package com.example.mycards

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.mycards.CardImagePickerHelper.PickerTarget
import com.example.mycards.databinding.FragmentAddCardBinding
import kotlinx.coroutines.launch
import java.io.File

class AddCardFragment : Fragment() {

    private var _binding: FragmentAddCardBinding? = null
    private val binding get() = _binding!!

    @VisibleForTesting internal var frontImageUri: String? = null
    private var backImageUri: String? = null

    // TD-002: track whether Finish was tapped so onDestroyView knows
    // whether to delete confirmed-but-unsaved image files.
    private var saved = false

    // Instantiated as a property initialiser — required by registerForActivityResult
    // (must be called before onStart).
    private val imagePicker = CardImagePickerHelper(
        fragment = this,
        onImageConfirmed = { target, permanentUri ->
            when (target) {
                PickerTarget.FRONT -> {
                    frontImageUri = permanentUri
                    binding.imageFrontPreview.setImageURI(Uri.parse(permanentUri))
                }
                PickerTarget.BACK -> {
                    backImageUri = permanentUri
                    binding.imageBackPreview.setImageURI(Uri.parse(permanentUri))
                }
            }
            setImageState(target, hasImage = true)
            updateFinishButton()
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddCardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Receive the captured image URI back from CameraFragment.
        setFragmentResultListener("camera_capture") { _, bundle ->
            val uriString  = bundle.getString("uri")    ?: return@setFragmentResultListener
            val targetName = bundle.getString("target") ?: return@setFragmentResultListener
            imagePicker.onCameraResult(
                Uri.parse(uriString),
                PickerTarget.valueOf(targetName)
            )
        }

        // ── Keep the Finish button above the system navigation bar ───────────
        // Read the baseline padding (16 dp set in XML) before any inset is applied,
        // then add the actual navigation-bar height on top of it.
        val footerPadBottom = binding.footerContainer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.footerContainer) { v, insets ->
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updatePadding(bottom = footerPadBottom + navBarBottom)
            insets
        }

        binding.editCardName.addTextChangedListener { updateFinishButton() }

        // ── Full picker buttons ──────────────────────────────────────────────
        binding.btnFrontCamera.setOnClickListener  { imagePicker.handleCameraClick(requireContext(), PickerTarget.FRONT) }
        binding.btnFrontGallery.setOnClickListener { imagePicker.launchGallery(PickerTarget.FRONT) }
        binding.btnBackCamera.setOnClickListener   { imagePicker.handleCameraClick(requireContext(), PickerTarget.BACK) }
        binding.btnBackGallery.setOnClickListener  { imagePicker.launchGallery(PickerTarget.BACK) }

        // ── Change strip buttons (open gallery/camera directly) ──────────────
        binding.btnChangeFrontGallery.setOnClickListener { imagePicker.launchGallery(PickerTarget.FRONT) }
        binding.btnChangeFrontCamera.setOnClickListener  { imagePicker.handleCameraClick(requireContext(), PickerTarget.FRONT) }
        binding.btnChangeBackGallery.setOnClickListener  { imagePicker.launchGallery(PickerTarget.BACK) }
        binding.btnChangeBackCamera.setOnClickListener   { imagePicker.handleCameraClick(requireContext(), PickerTarget.BACK) }

        // ── Remove (✕) buttons ───────────────────────────────────────────────
        binding.btnRemoveFront.setOnClickListener {
            deleteFileUri(frontImageUri)
            frontImageUri = null
            setImageState(PickerTarget.FRONT, hasImage = false)
            updateFinishButton()
        }
        binding.btnRemoveBack.setOnClickListener {
            deleteFileUri(backImageUri)
            backImageUri = null
            setImageState(PickerTarget.BACK, hasImage = false)
        }

        binding.btnFinish.setOnClickListener {
            val name  = binding.editCardName.text?.toString()?.trim() ?: return@setOnClickListener
            val front = frontImageUri ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                if (CardRepository.nameExists(name)) {
                    binding.editCardName.error = getString(R.string.error_card_name_exists)
                    return@launch
                }
                CardRepository.addCard(
                    LoyaltyCard(name = name, frontImageUri = front, backImageUri = backImageUri)
                )
                saved = true
                findNavController().popBackStack()
            }
        }

        // ── Restore UI state when returning from CameraFragment ──────────────
        // onViewCreated is called again after returning because Navigation's
        // FragmentNavigator destroys the view on forward navigation. Re-apply
        // whatever image state was already confirmed before going to the camera.
        frontImageUri?.let { uri ->
            binding.imageFrontPreview.setImageURI(Uri.parse(uri))
            setImageState(PickerTarget.FRONT, hasImage = true)
        }
        backImageUri?.let { uri ->
            binding.imageBackPreview.setImageURI(Uri.parse(uri))
            setImageState(PickerTarget.BACK, hasImage = true)
        }
        updateFinishButton()
    }

    override fun onDestroyView() {
        // TD-002: cleanup moved to onDestroy so that forward navigation to
        // CameraFragment (which also triggers onDestroyView) does NOT delete
        // already-confirmed image files.
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        // TD-002: delete confirmed-but-unsaved images only when the fragment
        // instance is truly removed (user backed out without saving).
        // Guard against configuration changes (e.g. rotation) which also call
        // onDestroy but should not discard the in-progress images.
        if (!saved && activity?.isChangingConfigurations != true) {
            deleteFileUri(frontImageUri)
            deleteFileUri(backImageUri)
        }
        super.onDestroy()
    }

    /**
     * Switches the image section UI between the full picker (hasImage = false)
     * and the preview + change strip (hasImage = true).
     */
    @VisibleForTesting internal fun setImageState(target: PickerTarget, hasImage: Boolean) {
        when (target) {
            PickerTarget.FRONT -> {
                binding.frontPickerContainer.visibility  = if (hasImage) View.GONE    else View.VISIBLE
                binding.frontPreviewContainer.visibility = if (hasImage) View.VISIBLE else View.GONE
                binding.frontChangeStrip.visibility      = if (hasImage) View.VISIBLE else View.GONE
            }
            PickerTarget.BACK -> {
                binding.backPickerContainer.visibility   = if (hasImage) View.GONE    else View.VISIBLE
                binding.backPreviewContainer.visibility  = if (hasImage) View.VISIBLE else View.GONE
                binding.backChangeStrip.visibility       = if (hasImage) View.VISIBLE else View.GONE
            }
        }
    }

    @VisibleForTesting internal fun updateFinishButton() {
        binding.btnFinish.isEnabled =
            binding.editCardName.text?.isNotBlank() == true && frontImageUri != null
    }

    private fun deleteFileUri(uriString: String?) {
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") uri.path?.let { File(it).delete() }
        }
    }
}
