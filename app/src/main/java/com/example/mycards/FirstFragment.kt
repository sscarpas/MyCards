package com.example.mycards

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.mycards.databinding.FragmentFirstBinding
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var adapter: CardAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CardAdapter { card ->
            val bundle = bundleOf("cardId" to card.id)
            findNavController().navigate(R.id.action_FirstFragment_to_CardDetailFragment, bundle)
        }
        binding.recyclerCards.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerCards.adapter = adapter

        // Observe card list from Room via Flow — auto-updates on any DB change
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CardRepository.allCards.collect { cards ->
                    adapter?.updateList(cards)
                }
            }
        }

        binding.fabAddCard.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_AddCardFragment)
        }

        // Defensive fallback: ensure the FAB clears the system navigation bar
        // even if CoordinatorLayout does not dispatch the inset automatically.
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAddCard) { v, insets ->
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val basePx = resources.getDimensionPixelSize(R.dimen.fab_margin)
            (v.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin = basePx + navBarBottom
            v.requestLayout()
            insets
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}