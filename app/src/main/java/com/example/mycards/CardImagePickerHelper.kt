package com.example.mycards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Encapsulates all image-picking logic shared by [AddCardFragment] and [EditCardFragment]:
 * in-app CameraX navigation, gallery launchers, temp-file lifecycle, and the
 * photo-confirmation [android.app.AlertDialog].
 *
 * Camera permission is handled inside [CameraFragment] (self-contained).
 *
 * IMPORTANT: must be instantiated as a **Fragment property initialiser** (not inside any
 * lifecycle callback) so that [registerForActivityResult] is called before [Fragment.onStart],
 * as required by the Activity Result API.
 *
 * @param onImageConfirmed Called on the main thread after the user confirms a photo and it has
 *                         been copied to permanent app storage. Receives the target slot and
 *                         the permanent file:// URI string.
 * @param onPickCancelled  Called when the user dismisses the confirmation dialog. The camera
 *                         temp file has already been deleted before this is invoked.
 */
class CardImagePickerHelper(
    private val fragment: Fragment,
    private val onImageConfirmed: (target: PickerTarget, permanentUri: String) -> Unit,
    private val onPickCancelled:  (target: PickerTarget) -> Unit = {}
) {
    enum class PickerTarget { FRONT, BACK }

    private var currentFrontTempFile: File? = null
    private var currentBackTempFile:  File? = null

    // ── Gallery launchers ────────────────────────────────────────────────────
    private val frontGalleryLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val ctx = fragment.requireContext()
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val (croppedUri, cropFile) = cropCenterToCardRatio(ctx, it)
                if (cropFile != null) currentFrontTempFile = cropFile
                showConfirmationDialog(
                    fragment.viewLifecycleOwner.lifecycleScope, ctx, croppedUri, PickerTarget.FRONT
                )
            }
        }
    }

    private val backGalleryLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val ctx = fragment.requireContext()
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val (croppedUri, cropFile) = cropCenterToCardRatio(ctx, it)
                if (cropFile != null) currentBackTempFile = cropFile
                showConfirmationDialog(
                    fragment.viewLifecycleOwner.lifecycleScope, ctx, croppedUri, PickerTarget.BACK
                )
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates a temp file, stores it for later cleanup, then navigates to [CameraFragment].
     * Permission checking is delegated to [CameraFragment] itself.
     */
    fun handleCameraClick(context: Context, target: PickerTarget) {
        val tempFile = File.createTempFile("card_pick_", ".jpg", context.externalCacheDir)
        when (target) {
            PickerTarget.FRONT -> currentFrontTempFile = tempFile
            PickerTarget.BACK  -> currentBackTempFile  = tempFile
        }
        fragment.findNavController().navigate(
            R.id.CameraFragment,
            bundleOf("target" to target.name, "outputPath" to tempFile.absolutePath)
        )
    }

    /** Launches the system gallery picker for [target]. */
    fun launchGallery(target: PickerTarget) = when (target) {
        PickerTarget.FRONT -> frontGalleryLauncher.launch("image/*")
        PickerTarget.BACK  -> backGalleryLauncher.launch("image/*")
    }

    /**
     * Called by the hosting fragment when it receives the "camera_capture" FragmentResult
     * from [CameraFragment]. Crops to landscape if needed, then shows the confirmation dialog.
     */
    fun onCameraResult(uri: Uri, target: PickerTarget) {
        val ctx = fragment.requireContext()
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val (croppedUri, cropFile) = cropToFrameRegion(ctx, uri)
            if (cropFile != null) {
                // A new cropped file was written; replace the original temp file reference.
                deleteTempFile(target)
                when (target) {
                    PickerTarget.FRONT -> currentFrontTempFile = cropFile
                    PickerTarget.BACK  -> currentBackTempFile  = cropFile
                }
            }
            showConfirmationDialog(
                fragment.viewLifecycleOwner.lifecycleScope, ctx, croppedUri, target
            )
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /**
     * [scope] is accepted as a method parameter rather than stored as a field, ensuring it is
     * always the caller's current view-lifecycle scope — never an outdated reference.
     */
    private fun showConfirmationDialog(
        scope: CoroutineScope, context: Context, uri: Uri, target: PickerTarget
    ) {
        val preview = ImageView(context).apply {
            setImageURI(uri)
            scaleType    = ImageView.ScaleType.FIT_CENTER
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 600)
        }
        android.app.AlertDialog.Builder(context)
            .setTitle(R.string.confirm_photo_title)
            .setView(preview)
            .setPositiveButton(R.string.use_this_photo) { _, _ ->
                scope.launch {
                    val permanentPath = ImageStorage.copyToAppStorage(context, uri)
                    val permanentUri  = Uri.fromFile(File(permanentPath)).toString()
                    deleteTempFile(target)
                    onImageConfirmed(target, permanentUri)
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                deleteTempFile(target)
                onPickCancelled(target)
            }
            .show()
    }

    private fun deleteTempFile(target: PickerTarget) {
        when (target) {
            PickerTarget.FRONT -> { currentFrontTempFile?.delete(); currentFrontTempFile = null }
            PickerTarget.BACK  -> { currentBackTempFile?.delete();  currentBackTempFile  = null }
        }
    }

    /**
     * Crops the captured camera JPEG to exactly the region shown inside [CardFrameOverlayView].
     *
     * Requires [CameraFragment] to have bound [ImageCapture] inside a [UseCaseGroup] with the
     * [PreviewView]'s ViewPort, so the saved JPEG already contains only the pixels visible in
     * the live preview. Given that guarantee, the card frame maps directly to
     * [FRAME_FRACTION_PORTRAIT] (portrait) or [FRAME_FRACTION_LANDSCAPE] (landscape) of the
     * image width, centred on both axes — the same fractions used by [CardFrameOverlayView].
     *
     * EXIF orientation is applied before cropping; the output is always visually upright.
     *
     * @return Pair of the cropped-file URI and the [File] created, or the original URI + null
     *         if decoding fails.
     */
    private suspend fun cropToFrameRegion(
        context: Context, uri: Uri
    ): Pair<Uri, File?> = withContext(Dispatchers.IO) {

        // Phase 1: read EXIF orientation.
        val exifDegrees = context.contentResolver.openInputStream(uri)?.use { stream ->
            val o = ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
            when (o) {
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_TRANSVERSE   -> 90
                ExifInterface.ORIENTATION_ROTATE_180,
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
                ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSPOSE    -> 270
                else -> 0
            }
        } ?: 0

        // Phase 2: load full bitmap.
        val rawBitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return@withContext uri to null

        // Phase 3: apply EXIF rotation so the bitmap is visually upright.
        val bitmap = if (exifDegrees != 0) {
            val matrix = Matrix().apply { postRotate(exifDegrees.toFloat()) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                .also { if (it !== rawBitmap) rawBitmap.recycle() }
        } else {
            rawBitmap
        }

        val bw = bitmap.width
        val bh = bitmap.height

        // Phase 4: choose the frame fraction that matches the orientation used at capture time.
        //   Portrait image (bh > bw)  → phone was held portrait  → 85 % frame fraction.
        //   Landscape image (bw >= bh) → phone was held landscape → 60 % frame fraction.
        val fraction = if (bh > bw) FRAME_FRACTION_PORTRAIT else FRAME_FRACTION_LANDSCAPE

        // Phase 5: crop to the frame region, centred in both axes.
        val cropWidth  = (bw * fraction).toInt()
        val cropHeight = (cropWidth / CARD_RATIO).toInt().coerceAtMost(bh)
        val startX     = (bw - cropWidth)  / 2
        val startY     = (bh - cropHeight) / 2

        val cropped = Bitmap.createBitmap(bitmap, startX, startY, cropWidth, cropHeight)
        bitmap.recycle()

        // Output is already correctly oriented — no EXIF tag required.
        val cropFile = File.createTempFile("card_crop_", ".jpg", context.externalCacheDir)
        FileOutputStream(cropFile).use { cropped.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        cropped.recycle()

        Uri.fromFile(cropFile) to cropFile
    }

    /**
     * Center-crops [uri] to the ISO/IEC 7810 ID-1 card aspect ratio (1.586 : 1, landscape).
     * Used for gallery picks where no ViewPort / frame-fraction contract applies.
     *
     * Always applied — regardless of whether the source image is portrait or landscape.
     * This ensures that the output always matches the area indicated by [CardFrameOverlayView]
     * on the camera screen.
     *
     * Runs on [Dispatchers.IO] — safe to call from a coroutine on the main thread.
     *
     * Crop rules (center-crop):
     *  - If image is wider than CARD_RATIO  → fix height, trim left/right sides
     *  - If image is taller than CARD_RATIO → fix width,  trim top/bottom
     *  - If image is already exactly CARD_RATIO → returned as-is (no file written)
     *
     * @return Pair of the URI to use (original or cropped) and the crop [File] if one was
     *         created (null when no cropping was necessary). The caller is responsible for
     *         tracking and deleting the crop file.
     */
    private suspend fun cropCenterToCardRatio(
        context: Context, uri: Uri
    ): Pair<Uri, File?> = withContext(Dispatchers.IO) {
        // Phase 1: read EXIF orientation so we know the visual (displayed) dimensions.
        // BitmapFactory.decodeStream ignores EXIF — without this step, a portrait photo stored
        // as landscape-with-rotate-90 tag would be mis-measured and cropped on the wrong axis.
        val exifDegrees = context.contentResolver.openInputStream(uri)?.use { stream ->
            val orientation = ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_TRANSVERSE   -> 90
                ExifInterface.ORIENTATION_ROTATE_180,
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
                ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSPOSE    -> 270
                else -> 0
            }
        } ?: 0

        // Phase 2: read raw pixel dimensions without loading pixels.
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        val rawW = opts.outWidth
        val rawH = opts.outHeight
        if (rawW <= 0 || rawH <= 0) return@withContext uri to null

        // Visual dimensions account for EXIF rotation (90°/270° swap width ↔ height).
        val visualW = if (exifDegrees == 90 || exifDegrees == 270) rawH else rawW
        val visualH = if (exifDegrees == 90 || exifDegrees == 270) rawW else rawH
        val currentRatio = visualW.toFloat() / visualH.toFloat()

        // Already the correct ratio — skip expensive bitmap work.
        if (kotlin.math.abs(currentRatio - CARD_RATIO) < 0.01f) return@withContext uri to null

        // Phase 3: load the full bitmap and apply EXIF rotation so it is visually upright.
        val rawBitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return@withContext uri to null

        val bitmap = if (exifDegrees != 0) {
            val matrix = Matrix().apply { postRotate(exifDegrees.toFloat()) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                .also { if (it !== rawBitmap) rawBitmap.recycle() }
        } else {
            rawBitmap
        }

        // Phase 4: center-crop to CARD_RATIO on the now-upright bitmap.
        val bw = bitmap.width   // == visualW after rotation
        val bh = bitmap.height  // == visualH after rotation
        val cropped = if (currentRatio > CARD_RATIO) {
            // Wider than card ratio → trim sides, keep full height.
            val cropWidth = (bh * CARD_RATIO).toInt().coerceAtMost(bw)
            val startX    = (bw - cropWidth) / 2
            Bitmap.createBitmap(bitmap, startX, 0, cropWidth, bh)
        } else {
            // Taller than card ratio (portrait or nearly square) → trim top/bottom.
            val cropHeight = (bw / CARD_RATIO).toInt().coerceAtMost(bh)
            val startY     = (bh - cropHeight) / 2
            Bitmap.createBitmap(bitmap, 0, startY, bw, cropHeight)
        }
        bitmap.recycle()

        // Output is already correctly oriented — no EXIF rotation tag required.
        val cropFile = File.createTempFile("card_crop_", ".jpg", context.externalCacheDir)
        FileOutputStream(cropFile).use { cropped.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        cropped.recycle()

        Uri.fromFile(cropFile) to cropFile
    }

    companion object {
        /** ISO/IEC 7810 ID-1 card aspect ratio (85.6 mm ÷ 54 mm). */
        private const val CARD_RATIO = 1.586f
        private const val FRAME_FRACTION_PORTRAIT = 0.85f
        private const val FRAME_FRACTION_LANDSCAPE = 0.60f
    }
}
