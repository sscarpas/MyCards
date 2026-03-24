package com.example.mycards

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.example.mycards.databinding.FragmentCameraBinding
import java.io.File

/**
 * Full-screen in-app camera using CameraX.
 *
 * Nav-args:
 *   target     – "FRONT" or "BACK" (CardImagePickerHelper.PickerTarget name)
 *   outputPath – absolute path of the pre-created temp file to write the JPEG into
 *
 * On capture success: posts FragmentResult "camera_capture" with keys "uri" and "target",
 * then pops the back stack. The calling fragment is responsible for all file management.
 *
 * [Preview] and [ImageCapture] are bound together in a [UseCaseGroup] with the
 * [androidx.camera.view.PreviewView]'s ViewPort. This ensures the saved JPEG contains
 * exactly the pixels visible in the live preview — required for
 * [CardImagePickerHelper.cropToFrameRegion] to crop accurately to the card frame area.
 */
class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null

    // ── Permission ────────────────────────────────────────────────────────────

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) binding.previewView.doOnLayout { startCamera() }
        else {
            Toast.makeText(
                requireContext(),
                getString(R.string.camera_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
            findNavController().popBackStack()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
        binding.btnShutter.setOnClickListener { capturePhoto() }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            // doOnLayout defers startCamera() until PreviewView is measured, so that
            // PreviewView.viewPort is non-null when UseCaseGroup is constructed.
            binding.previewView.doOnLayout { startCamera() }
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── CameraX ───────────────────────────────────────────────────────────────

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()

                // Bind with ViewPort so the ImageCapture output is cropped to match
                // exactly what is visible in the PreviewView. Without this the saved JPEG
                // contains the full sensor output (wider/taller than the preview), making
                // the frame-fraction crop in cropToFrameRegion inaccurate.
                val viewPort = binding.previewView.viewPort
                if (viewPort != null) {
                    val useCaseGroup = UseCaseGroup.Builder()
                        .addUseCase(preview)
                        .addUseCase(imageCapture!!)
                        .setViewPort(viewPort)
                        .build()
                    cameraProvider.bindToLifecycle(
                        viewLifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        useCaseGroup
                    )
                } else {
                    // Fallback: should not occur after doOnLayout, but kept for safety.
                    cameraProvider.bindToLifecycle(
                        viewLifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture!!
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.camera_capture_failed),
                    Toast.LENGTH_SHORT
                ).show()
                findNavController().popBackStack()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun capturePhoto() {
        val capture    = imageCapture ?: return
        val outputPath = arguments?.getString("outputPath") ?: return
        val target     = arguments?.getString("target")     ?: return

        val outputOptions = ImageCapture.OutputFileOptions.Builder(File(outputPath)).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = Uri.fromFile(File(outputPath)).toString()
                    setFragmentResult(
                        "camera_capture",
                        bundleOf("uri" to uri, "target" to target)
                    )
                    findNavController().popBackStack()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.camera_capture_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}
