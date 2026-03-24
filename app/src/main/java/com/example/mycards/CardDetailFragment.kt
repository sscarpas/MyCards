package com.example.mycards

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.mycards.databinding.FragmentCardDetailBinding
import kotlinx.coroutines.launch

class CardDetailFragment : Fragment() {

    private var _binding: FragmentCardDetailBinding? = null
    private val binding get() = _binding!!

    /** Holds the loaded card so the menu actions can reference it. */
    private var currentCard: LoyaltyCard? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCardDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ensure the last card image is fully scrollable above the system nav bar.
        val scrollPadBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updatePadding(bottom = scrollPadBottom + navBarBottom)
            insets
        }

        // Register menu provider
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_card_detail, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_edit -> {
                        val card = currentCard ?: return false
                        val bundle = bundleOf("cardId" to card.id)
                        findNavController().navigate(
                            R.id.action_CardDetailFragment_to_EditCardFragment, bundle
                        )
                        true
                    }
                    R.id.action_delete -> {
                        val card = currentCard ?: return false
                        showDeleteDialog(card)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val cardId = arguments?.getInt("cardId") ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val card = CardRepository.getById(cardId) ?: return@launch
            currentCard = card

            binding.textDetailName.text = card.name
            loadImage(binding.imageDetailFront, card.frontImageRes, card.frontImageUri)

            val hasBack = card.backImageRes != null || card.backImageUri != null
            if (hasBack) {
                binding.textBackLabel.visibility = View.VISIBLE
                binding.cardDetailBack.visibility = View.VISIBLE
                loadImage(binding.imageDetailBack, card.backImageRes ?: 0, card.backImageUri)
            }
        }
    }

    private fun showDeleteDialog(card: LoyaltyCard) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    CardRepository.deleteCard(card)
                    findNavController().popBackStack()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
