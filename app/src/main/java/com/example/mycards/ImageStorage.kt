package com.example.mycards

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageStorage {

    /**
     * Copies an image from [sourceUri] (file:// or content://) into the app's
     * permanent internal files directory and returns the absolute path of the copy.
     */
    suspend fun copyToAppStorage(context: Context, sourceUri: Uri): String =
        withContext(Dispatchers.IO) {
            val imagesDir = File(context.filesDir, "images").also { it.mkdirs() }
            val destFile = File(imagesDir, "${UUID.randomUUID()}.jpg")

            val inputStream = if (sourceUri.scheme == "file") {
                File(sourceUri.path!!).inputStream()
            } else {
                context.contentResolver.openInputStream(sourceUri)
            }

            inputStream?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            destFile.absolutePath
        }

    /**
     * Deletes a stored image file from disk.
     * Only acts on file:// URIs (files we own in filesDir/images/).
     * Content:// gallery URIs are skipped — we don't own those originals.
     */
    suspend fun deleteFromAppStorage(uriString: String?) = withContext(Dispatchers.IO) {
        if (uriString == null) return@withContext
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") {
            uri.path?.let { File(it).delete() }
        }
    }
}
