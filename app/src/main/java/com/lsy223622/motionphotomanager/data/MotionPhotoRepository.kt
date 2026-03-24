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
    private val tag = "MotionPhotoRepository"
    private val motionPhotoNamePatterns = arrayOf("MVIMG_%", "PXL_%", "%_MP.jpg")
    private val videoLengthRegex = Regex("Item:Mime=\\\"video/mp4\\\"(?s).*?Item:Length=\\\"(\\d+)\\\"")

    private fun buildStillDisplayName(originalName: String): String {
        val base = when {
            originalName.startsWith("MVIMG_", ignoreCase = true) -> {
                "IMG_" + originalName.removePrefix("MVIMG_")
            }
            else -> originalName
        }
        return "STILL_$base"
    }

    private fun buildVideoDisplayName(originalName: String): String {
        return "VIDEO_${extractBaseName(originalName)}.mp4"
    }

    private fun buildSplitPhotoDisplayName(originalName: String): String {
        return "STILL_${extractBaseName(originalName)}.jpg"
    }

    private fun extractBaseName(originalName: String): String {
        val extensionIndex = originalName.lastIndexOf('.')
        val nameWithoutExtension = if (extensionIndex > 0) {
            originalName.substring(0, extensionIndex)
        } else {
            originalName
        }
        return if (nameWithoutExtension.endsWith("_MP", ignoreCase = true)) {
            nameWithoutExtension.dropLast(3)
        } else {
            nameWithoutExtension
        }
    }

    suspend fun fetchMotionPhotos(): List<MotionPhoto> = withContext(Dispatchers.IO) {
        val motionPhotos = mutableListOf<MotionPhoto>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
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
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val size = cursor.getLong(sizeColumn)
                val dateTaken = cursor.getLong(dateTakenColumn)
                val dateModified = cursor.getLong(dateModifiedColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
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
                            videoOffset = videoOffset,
                            width = width,
                            height = height
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
            Log.e(tag, "Error preparing preview for ${photo.uri}", e)
            null
        }
    }

    suspend fun processPhoto(
        photo: MotionPhoto,
        mode: MotionPhotoProcessingMode
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val resolvedVideoOffset = if (photo.videoOffset > 0L) photo.videoOffset else findVideoOffset(photo.uri)
            if (resolvedVideoOffset <= 0L) {
                Log.w(tag, "Skip non-motion or unsupported file: ${photo.uri}")
                return@withContext false
            }

            val totalLength = if (photo.size > 0L) {
                photo.size
            } else {
                contentResolver.openAssetFileDescriptor(photo.uri, "r")?.use { afd ->
                    afd.length
                } ?: -1L
            }
            if (totalLength <= 0L) {
                Log.w(tag, "Unable to determine file length for ${photo.uri}")
                return@withContext false
            }

            val sourceRelativePath = querySourceRelativePath(photo.uri)
            val originalUri = MediaStore.setRequireOriginal(photo.uri)

            return@withContext when (mode) {
                MotionPhotoProcessingMode.PHOTO_ONLY -> exportPhotoOnly(
                    photo = photo,
                    originalUri = originalUri,
                    resolvedVideoOffset = resolvedVideoOffset,
                    totalLength = totalLength,
                    relativePath = sourceRelativePath
                )
                MotionPhotoProcessingMode.VIDEO_ONLY -> exportVideoOnly(
                    photo = photo,
                    originalUri = originalUri,
                    resolvedVideoOffset = resolvedVideoOffset,
                    totalLength = totalLength,
                    relativePath = sourceRelativePath
                )
                MotionPhotoProcessingMode.SPLIT_BOTH -> exportSplitParts(
                    photo = photo,
                    originalUri = originalUri,
                    resolvedVideoOffset = resolvedVideoOffset,
                    totalLength = totalLength,
                    relativePath = sourceRelativePath
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error processing photo ${photo.uri}", e)
            false
        }
    }

    private fun exportPhotoOnly(
        photo: MotionPhoto,
        originalUri: Uri,
        resolvedVideoOffset: Long,
        totalLength: Long,
        relativePath: String?
    ): Boolean {
        val targetUri = createPendingImage(
            displayName = buildStillDisplayName(photo.name),
            photo = photo,
            relativePath = relativePath
        ) ?: return false

        val success = writeStaticImage(
            sourceUri = originalUri,
            targetUri = targetUri,
            videoOffset = resolvedVideoOffset,
            totalLength = totalLength
        )

        return if (success) {
            if (publishPendingImage(targetUri)) {
                true
            } else {
                context.contentResolver.delete(targetUri, null, null)
                false
            }
        } else {
            context.contentResolver.delete(targetUri, null, null)
            false
        }
    }

    private fun exportVideoOnly(
        photo: MotionPhoto,
        originalUri: Uri,
        resolvedVideoOffset: Long,
        totalLength: Long,
        relativePath: String?
    ): Boolean {
        val targetUri = createPendingVideo(
            displayName = buildVideoDisplayName(photo.name),
            photo = photo,
            relativePath = relativePath
        ) ?: return false

        val success = writeVideo(
            sourceUri = originalUri,
            targetUri = targetUri,
            videoOffset = resolvedVideoOffset,
            totalLength = totalLength
        )

        return if (success) {
            if (publishPendingVideo(targetUri)) {
                true
            } else {
                context.contentResolver.delete(targetUri, null, null)
                false
            }
        } else {
            context.contentResolver.delete(targetUri, null, null)
            false
        }
    }

    private fun exportSplitParts(
        photo: MotionPhoto,
        originalUri: Uri,
        resolvedVideoOffset: Long,
        totalLength: Long,
        relativePath: String?
    ): Boolean {
        val imageUri = createPendingImage(
            displayName = buildSplitPhotoDisplayName(photo.name),
            photo = photo,
            relativePath = relativePath
        ) ?: return false
        val videoUri = createPendingVideo(
            displayName = buildVideoDisplayName(photo.name),
            photo = photo,
            relativePath = relativePath
        ) ?: run {
            context.contentResolver.delete(imageUri, null, null)
            return false
        }

        val imageSuccess = writeStaticImage(
            sourceUri = originalUri,
            targetUri = imageUri,
            videoOffset = resolvedVideoOffset,
            totalLength = totalLength
        )
        val videoSuccess = writeVideo(
            sourceUri = originalUri,
            targetUri = videoUri,
            videoOffset = resolvedVideoOffset,
            totalLength = totalLength
        )

        return if (imageSuccess && videoSuccess) {
            val imagePublished = publishPendingImage(imageUri)
            val videoPublished = publishPendingVideo(videoUri)
            if (imagePublished && videoPublished) {
                true
            } else {
                context.contentResolver.delete(imageUri, null, null)
                context.contentResolver.delete(videoUri, null, null)
                false
            }
        } else {
            context.contentResolver.delete(imageUri, null, null)
            context.contentResolver.delete(videoUri, null, null)
            false
        }
    }

    private fun createPendingImage(
        displayName: String,
        photo: MotionPhoto,
        relativePath: String?
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, photo.dateTaken)
            put(MediaStore.Images.Media.DATE_ADDED, photo.dateTaken / 1000)
            put(MediaStore.Images.Media.DATE_MODIFIED, photo.dateModified)
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath ?: (Environment.DIRECTORY_DCIM + "/Camera"))
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun createPendingVideo(
        displayName: String,
        photo: MotionPhoto,
        relativePath: String?
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_TAKEN, photo.dateTaken)
            put(MediaStore.Video.Media.DATE_ADDED, photo.dateTaken / 1000)
            put(MediaStore.Video.Media.DATE_MODIFIED, photo.dateModified)
            put(MediaStore.Video.Media.RELATIVE_PATH, relativePath ?: (Environment.DIRECTORY_DCIM + "/Camera"))
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        return context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun writeStaticImage(
        sourceUri: Uri,
        targetUri: Uri,
        videoOffset: Long,
        totalLength: Long
    ): Boolean {
        val contentResolver = context.contentResolver
        return contentResolver.openInputStream(sourceUri)?.use { input ->
            contentResolver.openOutputStream(targetUri)?.use { output ->
                MotionPhotoProcessor.extractStaticImage(input, output, videoOffset, totalLength)
            }
        } ?: false
    }

    private fun writeVideo(
        sourceUri: Uri,
        targetUri: Uri,
        videoOffset: Long,
        totalLength: Long
    ): Boolean {
        val contentResolver = context.contentResolver
        return contentResolver.openInputStream(sourceUri)?.use { input ->
            contentResolver.openOutputStream(targetUri)?.use { output ->
                MotionPhotoProcessor.extractVideo(input, output, videoOffset, totalLength)
            }
        } ?: false
    }

    private fun publishPendingImage(uri: Uri): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        return context.contentResolver.update(uri, values, null, null) > 0
    }

    private fun publishPendingVideo(uri: Uri): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.IS_PENDING, 0)
        }
        return context.contentResolver.update(uri, values, null, null) > 0
    }

    private fun findVideoOffset(uri: Uri): Long {
        try {
            val metadataText = readMetadataText(uri)
            if (metadataText.isNotEmpty()) {
                extractMicroVideoOffset(metadataText)?.let { return it }
                extractVideoItemLength(metadataText)?.let { return it }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error finding video offset for $uri", e)
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

    private fun querySourceRelativePath(uri: Uri): String? {
        val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val index = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            if (index == -1) return null
            return cursor.getString(index)
        }
        return null
    }
}
