package com.example.mycards

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.mycards.CardImagePickerHelper.PickerTarget
import com.example.mycards.databinding.FragmentEditCardBinding
import kotlinx.coroutines.launch
import java.io.File

class EditCardFragment : Fragment() {

    private var _binding: FragmentEditCardBinding? = null
    private val binding get() = _binding!!

    private var originalFrontUri: String? = null
    private var originalBackUri:  String? = null
    private var currentFrontUri:  String? = null
    private var currentBackUri:   String? = null
    private var isFrontImageSet   = false
    private var saved             = false
    // Prevents the DB load block from overwriting already-confirmed images
    // when onViewCreated is re-called after returning from CameraFragment.
    private var dataLoaded        = false

    // Instantiated as a property initialiser — required by registerForActivityResult
    // (must be called before onStart).
    private val imagePicker = CardImagePickerHelper(
        fragment = this,
        onImageConfirmed = { target, permanentUri ->
            when (target) {
                PickerTarget.FRONT -> viewLifecycleOwner.lifecycleScope.launch {
                    val old = currentFrontUri
                    if (old != null && old != originalFrontUri) ImageStorage.deleteFromAppStorage(old)
                    currentFrontUri = permanentUri
                    isFrontImageSet = true
                    showPreview(PickerTarget.FRONT, Uri.parse(permanentUri))
                    updateSaveButton()
                }
                PickerTarget.BACK -> viewLifecycleOwner.lifecycleScope.launch {
                    val old = currentBackUri
                    if (old != null && old != originalBackUri) ImageStorage.deleteFromAppStorage(old)
                    currentBackUri = permanentUri
                    showPreview(PickerTarget.BACK, Uri.parse(permanentUri))
                    updateSaveButton()
                }
            }
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditCardBinding.inflate(inflater, container, false)
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

        // ── Keep the Save button above the system navigation bar ─────────────
        val footerPadBottom = binding.footerContainer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.footerContainer) { v, insets ->
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updatePadding(bottom = footerPadBottom + navBarBottom)
            insets
        }

        val cardId = arguments?.getInt("cardId") ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val card = CardRepository.getById(cardId) ?: return@launch

            // Only initialise mutable state from the DB on the very first load.
            // On subsequent calls (e.g. returning from CameraFragment) the
            // already-confirmed currentFrontUri / currentBackUri must be kept.
            if (!dataLoaded) {
                dataLoaded       = true
                originalFrontUri = card.frontImageUri
                originalBackUri  = card.backImageUri
                currentFrontUri  = card.frontImageUri
                currentBackUri   = card.backImageUri
                binding.editCardName.setText(card.name)
            }

            // Restore UI from current values — works on first load AND on
            // return from CameraFragment.
            when {
                currentFrontUri != null -> {
                    showPreview(PickerTarget.FRONT, Uri.parse(currentFrontUri!!))
                    isFrontImageSet = true
                }
                card.frontImageRes != 0 -> {
                    binding.imageFrontPreview.setImageResource(card.frontImageRes)
                    binding.frontPreviewContainer.visibility = View.VISIBLE
                    binding.frontPickerContainer.visibility  = View.GONE
                    isFrontImageSet = true
                }
                else -> {
                    binding.frontPickerContainer.visibility  = View.VISIBLE
                    binding.frontPreviewContainer.visibility = View.GONE
                }
            }
            when {
                currentBackUri != null ->
                    showPreview(PickerTarget.BACK, Uri.parse(currentBackUri!!))
                card.backImageRes != null && card.backImageRes != 0 -> {
                    binding.imageBackPreview.setImageResource(card.backImageRes)
                    binding.backPreviewContainer.visibility = View.VISIBLE
                    binding.backPickerContainer.visibility  = View.GONE
                }
                else -> {
                    binding.backPickerContainer.visibility  = View.VISIBLE
                    binding.backPreviewContainer.visibility = View.GONE
                }
            }
            updateSaveButton()
        }

        binding.editCardName.addTextChangedListener { updateSaveButton() }

        binding.btnFrontCamera.setOnClickListener  { imagePicker.handleCameraClick(requireContext(), PickerTarget.FRONT) }
        binding.btnFrontGallery.setOnClickListener { imagePicker.launchGallery(PickerTarget.FRONT) }
        binding.btnBackCamera.setOnClickListener   { imagePicker.handleCameraClick(requireContext(), PickerTarget.BACK) }
        binding.btnBackGallery.setOnClickListener  { imagePicker.launchGallery(PickerTarget.BACK) }

        // ── Change strip buttons (open gallery/camera directly) ──────────────
        binding.btnChangeFrontGallery.setOnClickListener { imagePicker.launchGallery(PickerTarget.FRONT) }
        binding.btnChangeFrontCamera.setOnClickListener  { imagePicker.handleCameraClick(requireContext(), PickerTarget.FRONT) }
        binding.btnChangeBackGallery.setOnClickListener  { imagePicker.launchGallery(PickerTarget.BACK) }
        binding.btnChangeBackCamera.setOnClickListener   { imagePicker.handleCameraClick(requireContext(), PickerTarget.BACK) }

        binding.btnRemoveFront.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val old = currentFrontUri
                if (old != null && old != originalFrontUri) ImageStorage.deleteFromAppStorage(old)
                currentFrontUri = null
                isFrontImageSet = false
                binding.frontPreviewContainer.visibility = View.GONE
                binding.frontChangeStrip.visibility      = View.GONE
                binding.frontPickerContainer.visibility  = View.VISIBLE
                updateSaveButton()
            }
        }
        binding.btnRemoveBack.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val old = currentBackUri
                if (old != null && old != originalBackUri) ImageStorage.deleteFromAppStorage(old)
                currentBackUri = null
                binding.backPreviewContainer.visibility = View.GONE
                binding.backChangeStrip.visibility      = View.GONE
                binding.backPickerContainer.visibility  = View.VISIBLE
                updateSaveButton()
            }
        }

        binding.btnSave.setOnClickListener {
            val name = binding.editCardName.text?.toString()?.trim() ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                val cardId2  = arguments?.getInt("cardId") ?: return@launch
                if (CardRepository.nameExistsExcluding(name, cardId2)) {
                    binding.editCardName.error = getString(R.string.error_card_name_exists)
                    return@launch
                }
                val original = CardRepository.getById(cardId2) ?: return@launch
                CardRepository.updateCard(
                    updated          = original.copy(name = name, frontImageUri = currentFrontUri, backImageUri = currentBackUri),
                    originalFrontUri = originalFrontUri,
                    originalBackUri  = originalBackUri
                )
                saved = true
                findNavController().popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        // Cleanup moved to onDestroy — onDestroyView also fires on forward
        // navigation to CameraFragment and must not delete in-progress images.
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        // Delete newly confirmed (but unsaved) images only when the fragment
        // instance is truly being removed. Guard against configuration changes
        // (rotation) which call onDestroy without the user having backed out.
        if (!saved && activity?.isChangingConfigurations != true) {
            fun deleteIfUnsaved(uriString: String?, originalUri: String?) {
                if (uriString != null && uriString != originalUri) {
                    val uri = Uri.parse(uriString)
                    if (uri.scheme == "file") uri.path?.let { File(it).delete() }
                }
            }
            deleteIfUnsaved(currentFrontUri, originalFrontUri)
            deleteIfUnsaved(currentBackUri,  originalBackUri)
        }
        super.onDestroy()
    }

    private fun showPreview(target: PickerTarget, uri: Uri) {
        when (target) {
            PickerTarget.FRONT -> {
                binding.imageFrontPreview.setImageURI(uri)
                binding.frontPreviewContainer.visibility = View.VISIBLE
                binding.frontChangeStrip.visibility      = View.VISIBLE
                binding.frontPickerContainer.visibility  = View.GONE
            }
            PickerTarget.BACK -> {
                binding.imageBackPreview.setImageURI(uri)
                binding.backPreviewContainer.visibility = View.VISIBLE
                binding.backChangeStrip.visibility      = View.VISIBLE
                binding.backPickerContainer.visibility  = View.GONE
            }
        }
    }

    private fun updateSaveButton() {
        binding.btnSave.isEnabled =
            binding.editCardName.text?.isNotBlank() == true && isFrontImageSet
    }
}
