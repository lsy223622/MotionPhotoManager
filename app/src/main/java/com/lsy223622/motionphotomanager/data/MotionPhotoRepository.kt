package com.lsy223622.motionphotomanager.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.lsy223622.motionphotomanager.core.MotionPhotoProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

class MotionPhotoRepository(private val context: Context) {
    private val TAG = "MotionPhotoRepository"
    private val motionPhotoNamePatterns = arrayOf("MVIMG_%", "PXL_%", "%_MP.jpg")
    private val videoLengthRegex = Regex("Item:Mime=\\\"video/mp4\\\"(?s).*?Item:Length=\\\"(\\d+)\\\"")

    private fun buildSlimDisplayName(originalName: String): String {
        val base = when {
            originalName.startsWith("MVIMG_", ignoreCase = true) -> {
                "IMG_" + originalName.removePrefix("MVIMG_")
            }
            else -> originalName
        }
        return "SLIM_$base"
    }

    suspend fun fetchMotionPhotos(): List<MotionPhoto> = withContext(Dispatchers.IO) {
        val motionPhotos = mutableListOf<MotionPhoto>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED
        )

        val selection = buildString {
            append("${MediaStore.Images.Media.MIME_TYPE} = ?")
            append(" AND (")
            motionPhotoNamePatterns.forEachIndexed { index, _ ->
                if (index > 0) append(" OR ")
                append("${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?")
            }
            append(")")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                append(" AND ${MediaStore.MediaColumns.IS_TRASHED} = 0")
            }
        }
        val selectionArgs = arrayOf("image/jpeg", *motionPhotoNamePatterns)
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val size = cursor.getLong(sizeColumn)
                val dateTaken = cursor.getLong(dateTakenColumn)
                val dateModified = cursor.getLong(dateModifiedColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                val videoOffset = findVideoOffset(uri)
                if (videoOffset > 0L) {
                    motionPhotos.add(
                        MotionPhoto(
                            id = id,
                            uri = uri,
                            name = name,
                            size = size,
                            dateTaken = dateTaken,
                            dateModified = dateModified,
                            videoOffset = videoOffset
                        )
                    )
                }
            }
        }
        motionPhotos
    }

    suspend fun preparePreviewVideo(photo: MotionPhoto): String? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val resolvedVideoOffset = if (photo.videoOffset > 0L) photo.videoOffset else findVideoOffset(photo.uri)
            if (resolvedVideoOffset <= 0L) return@withContext null

            val totalLength = if (photo.size > 0L) {
                photo.size
            } else {
                contentResolver.openAssetFileDescriptor(photo.uri, "r")?.use { afd -> afd.length } ?: -1L
            }
            if (totalLength <= 0L) return@withContext null

            val imageLength = totalLength - resolvedVideoOffset
            if (imageLength <= 0L) return@withContext null

            val previewFile = File(context.cacheDir, "preview_${photo.id}.mp4")
            contentResolver.openInputStream(photo.uri)?.use { input ->
                if (!skipFully(input, imageLength)) return@withContext null
                previewFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            if (previewFile.length() > 0L) previewFile.absolutePath else null
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing preview for ${photo.uri}", e)
            null
        }
    }

    private fun findVideoOffset(uri: Uri): Long {
        try {
            val metadataText = readMetadataText(uri)
            if (metadataText.isNotEmpty()) {
                extractMicroVideoOffset(metadataText)?.let { return it }
                extractVideoItemLength(metadataText)?.let { return it }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding video offset for $uri", e)
        }
        return -1L
    }

    private fun readMetadataText(uri: Uri): String {
        val maxChunk = 1024 * 1024
        val resolver = context.contentResolver
        val head = resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(maxChunk)
            val read = input.read(buffer)
            if (read > 0) String(buffer, 0, read, StandardCharsets.ISO_8859_1) else ""
        } ?: ""

        val tail = resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            val total = afd.length
            if (total <= 0L) return@use ""
            val tailSize = minOf(maxChunk.toLong(), total).toInt()
            val input = afd.createInputStream()
            input.use {
                val toSkip = total - tailSize
                if (!skipFully(it, toSkip)) return@use ""
                val out = ByteArrayOutputStream(tailSize)
                val buffer = ByteArray(8192)
                var remaining = tailSize
                while (remaining > 0) {
                    val read = it.read(buffer, 0, minOf(buffer.size, remaining))
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    remaining -= read
                }
                String(out.toByteArray(), StandardCharsets.ISO_8859_1)
            }
        } ?: ""

        return if (tail.isNotEmpty()) "$head\n$tail" else head
    }

    private fun extractMicroVideoOffset(text: String): Long? {
        val pattern = "GCamera:MicroVideoOffset=\""
        val startIndex = text.indexOf(pattern)
        if (startIndex == -1) return null
        val valueStart = startIndex + pattern.length
        val valueEnd = text.indexOf("\"", valueStart)
        if (valueEnd == -1) return null
        return text.substring(valueStart, valueEnd).toLongOrNull()?.takeIf { it > 0L }
    }

    private fun extractVideoItemLength(text: String): Long? {
        val match = videoLengthRegex.find(text) ?: return null
        return match.groupValues.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }
    }

    private fun skipFully(input: java.io.InputStream, bytesToSkip: Long): Boolean {
        var remaining = bytesToSkip
        val buffer = ByteArray(8192)
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
                continue
            }
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) return false
            remaining -= read.toLong()
        }
        return true
    }

    suspend fun slimPhoto(photo: MotionPhoto): Boolean = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val resolvedVideoOffset = if (photo.videoOffset > 0) photo.videoOffset else findVideoOffset(photo.uri)
            if (resolvedVideoOffset <= 0L) {
                Log.w(TAG, "Skip non-motion or unsupported file: ${photo.uri}")
                return@withContext false
            }

            val totalLength = if (photo.size > 0) {
                photo.size
            } else {
                contentResolver.openAssetFileDescriptor(photo.uri, "r")?.use { afd ->
                    afd.length
                } ?: -1L
            }
            if (totalLength <= 0L) {
                Log.w(TAG, "Unable to determine file length for ${photo.uri}")
                return@withContext false
            }

            val sourceRelativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                querySourceRelativePath(photo.uri)
            } else {
                null
            }
            
            // 1. Prepare new file metadata
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, buildSlimDisplayName(photo.name))
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_TAKEN, photo.dateTaken)
                put(MediaStore.Images.Media.DATE_ADDED, photo.dateTaken / 1000) // Sync added time
                put(MediaStore.Images.Media.DATE_MODIFIED, photo.dateModified)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, sourceRelativePath ?: (Environment.DIRECTORY_DCIM + "/Camera"))
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val newUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false

            // 2. Perform extraction
            val originalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.setRequireOriginal(photo.uri) // 强行要求系统返回带经纬度的原始流
            } else {
                photo.uri
            }
            val success = contentResolver.openInputStream(originalUri)?.use { input ->
                contentResolver.openOutputStream(newUri)?.use { output ->
                    MotionPhotoProcessor.extractStaticImage(input, output, resolvedVideoOffset, totalLength)
                }
            } ?: false

            if (success) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(newUri, values, null, null)
                }
                // Trash original photo logic should be handled by the UI/ViewModel because it requires a PendingIntent on Android 11+
                return@withContext true
            } else {
                contentResolver.delete(newUri, null, null)
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error slimming photo ${photo.uri}", e)
            false
        }
    }

    private fun querySourceRelativePath(uri: Uri): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val index = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            if (index == -1) return null
            return cursor.getString(index)
        }
        return null
    }

    suspend fun ensureUrisTrashed(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || uris.isEmpty()) {
            return@withContext 0
        }

        var trashedCount = 0
        val resolver = context.contentResolver

        uris.forEach { uri ->
            try {
                if (isUriTrashed(uri)) {
                    trashedCount++
                    return@forEach
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_TRASHED, 1)
                }
                resolver.update(uri, values, null, null)

                if (isUriTrashed(uri)) {
                    trashedCount++
                } else {
                    Log.w(TAG, "Failed to mark as trashed: $uri")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error ensuring trashed state for $uri", e)
            }
        }

        trashedCount
    }

    private fun isUriTrashed(uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false

        val projection = arrayOf(MediaStore.MediaColumns.IS_TRASHED)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return false
            val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
            if (columnIndex == -1) return false
            return cursor.getInt(columnIndex) == 1
        }
        return false
    }
}
