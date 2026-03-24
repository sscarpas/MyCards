package com.example.mycards

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.mycards.databinding.FragmentSettingsBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // -------------------------------------------------------------------------
    // Activity result launchers
    // -------------------------------------------------------------------------

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) performExport(uri) }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) performImport(uri) }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Keep the Import button above the system navigation bar on all devices.
        val rootPadBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updatePadding(bottom = rootPadBottom + navBarBottom)
            insets
        }

        binding.btnExport.setOnClickListener {
            val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            exportLauncher.launch("mycards_backup_$dateStr.zip")
        }

        binding.btnImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/zip", "*/*"))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    private fun performExport(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val cards = CardRepository.getAllCardsList()
                CardBackupManager.exportToZip(requireContext(), uri, cards)
                showSnackbar(getString(R.string.export_success, cards.size))
            } catch (_: Exception) {
                showSnackbar(getString(R.string.export_failed))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Import
    // -------------------------------------------------------------------------

    private fun performImport(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Persist read permission so we can open the file below
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* not persistable – fine */ }

            try {
                val result = CardBackupManager.importFromZip(requireContext(), uri)

                // Insert new (non-conflicting) cards immediately
                for (card in result.newCards) {
                    CardRepository.addCard(card)
                }

                // Resolve each conflict individually via a dialog
                var overwriteCount = 0
                for ((existing, incoming) in result.conflictCards) {
                    if (showConflictDialog(existing.name)) {
                        CardRepository.updateCard(
                            incoming.copy(id = existing.id),
                            existing.frontImageUri,
                            existing.backImageUri
                        )
                        overwriteCount++
                    }
                }

                val totalImported = result.newCards.size + overwriteCount
                showSnackbar(getString(R.string.import_success, totalImported))
            } catch (_: Exception) {
                showSnackbar(getString(R.string.import_failed))
            }
        }
    }

    /**
     * Shows a per-card conflict dialog and suspends until the user makes a choice.
     * Returns `true` if the user chose Overwrite, `false` if they chose Skip or dismissed.
     */
    private suspend fun showConflictDialog(cardName: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.import_conflict_title, cardName))
                .setMessage(R.string.import_conflict_message)
                .setPositiveButton(R.string.overwrite) { _, _ -> continuation.resume(true) }
                .setNegativeButton(R.string.skip) { _, _ -> continuation.resume(false) }
                .setOnCancelListener { continuation.resume(false) }
                .setCancelable(true)
                .show()
        }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}


