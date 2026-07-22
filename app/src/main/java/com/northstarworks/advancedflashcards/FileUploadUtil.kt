package com.northstarworks.advancedflashcards

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64

/**
 * Utilities for turning a picked content Uri into the payload shape the
 * server's /api/ai/generate_deck endpoint expects (base64 data URL + name +
 * MIME type). Used by image and document scanning.
 */
object FileUploadUtil {

    data class LoadedFile(
        val name: String,
        val mimeType: String,
        /** data:<mime>;base64,<...>  ready to POST to the server */
        val dataUrl: String,
        val sizeBytes: Int
    )

    /** Read a Uri fully into memory and encode as a base64 data URL. */
    fun loadAsDataUrl(context: Context, uri: Uri, maxBytes: Int = 12 * 1024 * 1024): LoadedFile {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: guessMimeFromName(queryName(context, uri))
        val name = queryName(context, uri)

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Unable to read the selected file")
        if (bytes.isEmpty()) throw IllegalArgumentException("Selected file is empty")
        if (bytes.size > maxBytes) {
            throw IllegalArgumentException("File is too large (max ${maxBytes / (1024 * 1024)} MB)")
        }

        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return LoadedFile(
            name = name,
            mimeType = mime,
            dataUrl = "data:$mime;base64,$b64",
            sizeBytes = bytes.size
        )
    }

    fun queryName(context: Context, uri: Uri): String {
        var result = "file"
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) {
                    val n = c.getString(idx)
                    if (!n.isNullOrBlank()) result = n
                }
            }
        }
        if (result == "file") {
            uri.lastPathSegment?.substringAfterLast('/')?.let { if (it.isNotBlank()) result = it }
        }
        return result
    }

    private fun guessMimeFromName(name: String): String {
        val n = name.lowercase()
        return when {
            n.endsWith(".png") -> "image/png"
            n.endsWith(".jpg") || n.endsWith(".jpeg") -> "image/jpeg"
            n.endsWith(".webp") -> "image/webp"
            n.endsWith(".gif") -> "image/gif"
            n.endsWith(".pdf") -> "application/pdf"
            n.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            n.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            n.endsWith(".csv") -> "text/csv"
            n.endsWith(".tsv") -> "text/tab-separated-values"
            n.endsWith(".txt") || n.endsWith(".md") -> "text/plain"
            n.endsWith(".json") -> "application/json"
            else -> "application/octet-stream"
        }
    }
}
