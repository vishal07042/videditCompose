package com.videdit.android.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import com.videdit.android.model.VideoFile
import java.io.File

class VideoFileLoader(private val context: Context) {

    fun load(uri: Uri): VideoFile {
        val localFile = copyToCache(uri)
        val duration = readDurationSeconds(localFile)
        val name = readDisplayName(uri) ?: localFile.name

        return VideoFile(
            uriString = uri.toString(),
            displayName = name,
            localPath = localFile.absolutePath,
            durationSec = duration,
        )
    }

    private fun copyToCache(uri: Uri): File {
        val extension = context.contentResolver.getType(uri)
            ?.substringAfterLast('/', "mp4")
            ?: "mp4"
        val target = File(context.cacheDir, "source_${System.currentTimeMillis()}.$extension")

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected video." }
            target.outputStream().use { output -> input.copyTo(output) }
        }

        return target
    }

    private fun readDurationSeconds(file: File): Double {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            durationMs / 1000.0
        } finally {
            retriever.release()
        }
    }

    private fun readDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0) return cursor.getString(column)
            }
        }
        return null
    }
}
