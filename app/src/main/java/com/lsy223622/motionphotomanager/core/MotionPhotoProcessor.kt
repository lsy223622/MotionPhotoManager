package com.lsy223622.motionphotomanager.core

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.text.Charsets

object MotionPhotoProcessor {
    private const val tag = "MotionPhotoProcessor"

    /**
     * Extracts the static image from a motion photo.
     * 
     * @param inputStream The source motion photo stream.
     * @param outputStream The destination for the static image.
     * @param videoOffset The offset from the end of the file where the video starts.
     * @param totalLength The total length of the motion photo file.
     */
    fun extractStaticImage(
        inputStream: InputStream,
        outputStream: OutputStream,
        videoOffset: Long,
        totalLength: Long
    ): Boolean {
        try {
            val imageLength = totalLength - videoOffset
            if (imageLength <= 0) return false
            if (imageLength > Int.MAX_VALUE) return false

            val imageBuffer = ByteArrayOutputStream(imageLength.toInt())
            val buffer = ByteArray(8192)
            var bytesRemaining = imageLength

            while (bytesRemaining > 0) {
                val toRead =
                    if (bytesRemaining > buffer.size) buffer.size else bytesRemaining.toInt()
                val read = inputStream.read(buffer, 0, toRead)
                if (read == -1) return false
                imageBuffer.write(buffer, 0, read)
                bytesRemaining -= read
            }

            val sanitized = sanitizeXmpHeader(imageBuffer.toByteArray())
            outputStream.write(sanitized)
            return true
        } catch (e: Exception) {
            Log.e(tag, "Error extracting static image", e)
            return false
        }
    }

    private fun sanitizeXmpHeader(imageData: ByteArray): ByteArray {
        val scanLength = imageData.size
        if (scanLength <= 0) return imageData

        // ========== 1. 二进制外科手术：定点抹除小米私有 EXIF 标签 0x8897 ==========
        val searchLimit = minOf(scanLength, 256 * 1024) // 绝大多数情况 EXIF 都在前 256KB 内

        // 穷举该标签在 IFD 中可能出现的全部四种 12 字节合法形态
        val sequencesToZeroOut = listOf(
            // Big-Endian SHORT 格式: 88 97 00 03 00 00 00 01 00 01 00 00
            byteArrayOf(0x88.toByte(), 0x97.toByte(), 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00),
            // Big-Endian BYTE 格式: 88 97 00 01 00 00 00 01 01 00 00 00
            byteArrayOf(0x88.toByte(), 0x97.toByte(), 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x01, 0x00, 0x00, 0x00),
            // Little-Endian SHORT 格式: 97 88 03 00 01 00 00 00 01 00 00 00
            byteArrayOf(0x97.toByte(), 0x88.toByte(), 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00),
            // Little-Endian BYTE 格式: 97 88 01 00 01 00 00 00 01 00 00 00
            byteArrayOf(0x97.toByte(), 0x88.toByte(), 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00)
        )

        for (seq in sequencesToZeroOut) {
            var i = 0
            while (i <= searchLimit - seq.size) {
                var match = true
                for (j in seq.indices) {
                    if (imageData[i + j] != seq[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    // 找到后进行破坏：把标签 ID 改为 00 00 (废弃标签)，并把后面的参数值全归零
                    for (k in 0..11) {
                        imageData[i + k] = 0x00
                    }
                    Log.d(tag, "成功抹除小米私有 EXIF 动态标志 0x8897，位于偏移量: $i")
                }
                i++
            }
        }

        // ========== 2. 文本替换：应对隐藏在 JSON 和 XML 中的残留 ==========
        var header = String(imageData, 0, scanLength, Charsets.ISO_8859_1)

        // 抹除可能隐藏在 MakerNotes JSON 字符串中的标记 (用空格等长替换 true/1，保持字节偏移绝对不偏)
        val jsonRegexes = listOf(
            Regex("(\"isDynamic\"\\s*:\\s*)(true|1)"),
            Regex("(\"isMicroVideo\"\\s*:\\s*)(true|1)"),
            Regex("(\"microVideo\"\\s*:\\s*)(true|1)")
        )
        jsonRegexes.forEach { regex ->
            header = regex.replace(header) { match ->
                val prefix = match.groupValues[1]
                val value = match.groupValues[2]
                val replacement = if (value == "true") "null" else "0"
                prefix + replacement.padEnd(value.length, ' ')
            }
        }

        // 之前优化好的强大 XML 节点抹除逻辑 (必须保留)
        val attrRegex = Regex("[A-Za-z0-9_]+:(?:MotionPhoto|MicroVideo|IsDynamicPhoto)[A-Za-z0-9_]*\\s*=\\s*[\"'][^\"']*[\"']")
        header = attrRegex.replace(header) { " ".repeat(it.value.length) }

        val elementRegex = Regex("(?s)<[A-Za-z0-9_]+:(?:MotionPhoto|MicroVideo|IsDynamicPhoto)[^>]*>.*?</[A-Za-z0-9_]+:(?:MotionPhoto|MicroVideo|IsDynamicPhoto)[^>]*>")
        header = elementRegex.replace(header) { " ".repeat(it.value.length) }

        val selfClosingRegex = Regex("<[A-Za-z0-9_]+:(?:MotionPhoto|MicroVideo|IsDynamicPhoto)[^>]*/>")
        header = selfClosingRegex.replace(header) { " ".repeat(it.value.length) }

        val motionItemRegex = Regex("(?s)<rdf:li\\s+rdf:parseType=\\\"Resource\\\">(?:(?!</rdf:li>).)*Item:Semantic=\\\"MotionPhoto\\\"(?:(?!</rdf:li>).)*</rdf:li>")
        header = motionItemRegex.replace(header) { " ".repeat(it.value.length) }

        // 将文本转换回二进制
        val patchedHeader = header.toByteArray(Charsets.ISO_8859_1)
        if (patchedHeader.size == scanLength) {
            System.arraycopy(patchedHeader, 0, imageData, 0, scanLength)
        }

        return imageData
    }
}