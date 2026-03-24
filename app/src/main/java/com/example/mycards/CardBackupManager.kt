package com.example.mycards

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Holds the result of a [CardBackupManager.importFromZip] call.
 *
 * @property newCards      Cards that have no name collision with existing ones and can be
 *                         inserted directly.
 * @property conflictCards Pairs of (existing card, incoming card) where the name already
 *                         exists in the database.  The caller must decide per-pair whether
 *                         to skip or overwrite.
 */
data class ImportResult(
    val newCards: List<LoyaltyCard>,
    val conflictCards: List<Pair<LoyaltyCard, LoyaltyCard>>
)

object CardBackupManager {

    private const val MANIFEST_FILENAME = "manifest.json"
    private const val MANIFEST_VERSION = 1

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    /**
     * Serialises [cards] together with their image files into a ZIP archive written
     * to [uri] (obtained via ACTION_CREATE_DOCUMENT).
     *
     * ZIP structure:
     * ```
     * manifest.json
     * front_<id>.jpg    (only for cards with a URI-backed front image)
     * back_<id>.jpg     (only for cards with a URI-backed back image)
     * ```
     */
    suspend fun exportToZip(context: Context, uri: Uri, cards: List<LoyaltyCard>) =
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zip ->
                    val cardsArray = JSONArray()

                    for (card in cards) {
                        val cardObj = JSONObject()
                        cardObj.put("name", card.name)

                        val frontEntry = addImageEntry(context, zip, card.frontImageUri, "front_${card.id}")
                        cardObj.put("frontImageFile", frontEntry ?: JSONObject.NULL)

                        val backEntry = addImageEntry(context, zip, card.backImageUri, "back_${card.id}")
                        cardObj.put("backImageFile", backEntry ?: JSONObject.NULL)

                        cardsArray.put(cardObj)
                    }

                    val manifest = JSONObject().apply {
                        put("version", MANIFEST_VERSION)
                        put("cards", cardsArray)
                    }

                    zip.putNextEntry(ZipEntry(MANIFEST_FILENAME))
                    zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }

    /**
     * Copies the image referenced by [uriString] into the ZIP as [baseName].jpg.
     * Returns the ZIP entry name on success, or null if the URI is absent / unreadable.
     * Resource-backed images (no URI) are silently skipped.
     */
    private fun addImageEntry(
        context: Context,
        zip: ZipOutputStream,
        uriString: String?,
        baseName: String
    ): String? {
        if (uriString == null) return null
        val uri = Uri.parse(uriString)
        val inputStream = try {
            if (uri.scheme == "file") File(uri.path!!).inputStream()
            else context.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            return null
        } ?: return null

        val entryName = "$baseName.jpg"
        zip.putNextEntry(ZipEntry(entryName))
        inputStream.use { it.copyTo(zip) }
        zip.closeEntry()
        return entryName
    }

    // -------------------------------------------------------------------------
    // Import
    // -------------------------------------------------------------------------

    /**
     * Reads a ZIP archive from [uri] (obtained via ACTION_OPEN_DOCUMENT), extracts
     * images to the app's internal storage and returns an [ImportResult] that separates
     * new cards from those whose name already exists in the database.
     */
    suspend fun importFromZip(context: Context, uri: Uri): ImportResult =
        withContext(Dispatchers.IO) {
            // 1 – Read entire ZIP into memory (manifest text + raw image bytes)
            val imageData = mutableMapOf<String, ByteArray>()
            var manifestJson: String? = null

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val bytes = zip.readBytes()
                        if (entry.name == MANIFEST_FILENAME) {
                            manifestJson = bytes.toString(Charsets.UTF_8)
                        } else {
                            imageData[entry.name] = bytes
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            if (manifestJson == null) return@withContext ImportResult(emptyList(), emptyList())

            // 2 – Parse manifest
            val cardsArray = JSONObject(manifestJson!!).getJSONArray("cards")
            val imagesDir = File(context.filesDir, "images").also { it.mkdirs() }

            val newCards = mutableListOf<LoyaltyCard>()
            val conflictCards = mutableListOf<Pair<LoyaltyCard, LoyaltyCard>>()

            for (i in 0 until cardsArray.length()) {
                val cardObj = cardsArray.getJSONObject(i)
                val name = cardObj.getString("name")

                val frontFile = cardObj.optString("frontImageFile").ifEmpty { null }
                val backFile  = cardObj.optString("backImageFile").ifEmpty { null }

                // Save extracted image bytes to the app's private storage
                val frontUri = frontFile?.let { saveImage(imagesDir, imageData[it]) }
                val backUri  = backFile?.let { saveImage(imagesDir, imageData[it]) }

                val incoming = LoyaltyCard(name = name, frontImageUri = frontUri, backImageUri = backUri)

                val existing = CardRepository.getByName(name)
                if (existing != null) {
                    conflictCards.add(Pair(existing, incoming))
                } else {
                    newCards.add(incoming)
                }
            }

            ImportResult(newCards, conflictCards)
        }

    /** Writes [data] to a new UUID-named file inside [imagesDir] and returns its file:// URI. */
    private fun saveImage(imagesDir: File, data: ByteArray?): String? {
        if (data == null) return null
        val dest = File(imagesDir, "${UUID.randomUUID()}.jpg")
        dest.writeBytes(data)
        return Uri.fromFile(dest).toString()
    }
}


