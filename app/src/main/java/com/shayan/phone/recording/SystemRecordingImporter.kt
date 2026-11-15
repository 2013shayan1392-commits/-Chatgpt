package com.shayan.phone.recording

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ImportedRecording(
    val file: File,
    val displayName: String,
    val mimeType: String?,
    val durationMs: Long?
)

/**
 * Imports an audio file explicitly selected by the user through Android's
 * Storage Access Framework. The source is never deleted or modified.
 *
 * This is a file-import path, not live call capture.
 */
class SystemRecordingImporter(context: Context) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    fun importSelectedAudio(uri: Uri): ImportedRecording {
        val mimeType = resolver.getType(uri)
        val sourceName = queryDisplayName(uri)
        val safeName = sanitizeFileName(sourceName)
        val sourceExtension = safeName.substringAfterLast('.', "").lowercase()
        val recognizedAudioExtension = sourceExtension in setOf(
            "aac", "amr", "flac", "m4a", "mp3", "ogg", "opus", "wav", "3gp"
        )
        require(mimeType?.startsWith("audio/") == true || recognizedAudioExtension) {
            "The selected item is not identified as an audio file."
        }
        val extension = safeName.substringAfterLast('.', "")
            .takeIf { it.isNotBlank() }
            ?: extensionForMime(mimeType)
            ?: "audio"

        val directory = File(appContext.filesDir, "recordings/imported")
        check(directory.exists() || directory.mkdirs()) {
            "Could not create the private recordings directory."
        }

        val destination = File(directory, "${UUID.randomUUID()}.$extension")
        val temporary = File(directory, "${destination.name}.part")

        try {
            val input = resolver.openInputStream(uri)
                ?: error("Android could not open the selected audio file.")

            input.use { source ->
                FileOutputStream(temporary).use { output ->
                    source.copyTo(output)
                    output.fd.sync()
                }
            }

            check(temporary.length() > 0L) { "The selected audio file is empty." }
            check(temporary.renameTo(destination)) {
                "Could not finalize the imported audio file."
            }
        } catch (error: Exception) {
            temporary.delete()
            destination.delete()
            throw error
        }

        return ImportedRecording(
            file = destination,
            displayName = sourceName,
            mimeType = mimeType,
            durationMs = readDuration(destination)
        )
    }

    private fun queryDisplayName(uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
            }
        return "recording"
    }

    private fun sanitizeFileName(value: String): String {
        val cleaned = value
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', '_')
        return cleaned.ifBlank { "recording" }
    }

    private fun extensionForMime(mimeType: String?): String? = when (mimeType?.lowercase()) {
        "audio/mpeg" -> "mp3"
        "audio/mp4", "audio/m4a" -> "m4a"
        "audio/aac" -> "aac"
        "audio/ogg" -> "ogg"
        "audio/opus" -> "opus"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/flac" -> "flac"
        "audio/3gpp" -> "3gp"
        else -> null
    }

    private fun readDuration(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
