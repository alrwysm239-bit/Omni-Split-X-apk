package com.example.util

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Robust 4-byte and 4096-byte ZipAligner for Android APKs.
 * Aligns uncompressed (STORED) entries and native libraries (.so)
 * to ensure maximum compatibility with Android's PackageParser and mmap.
 */
object ZipAligner {

    private const val DEFAULT_ALIGNMENT = 4
    private const val PAGE_ALIGNMENT = 4096 // 4KB page alignment for .so libraries

    /**
     * Performs 4-byte and 4096-byte alignment on [inputApk], writing the result to [outputApk].
     * Strips existing META-INF signatures to prepare for clean re-signing.
     *
     * @return true if alignment succeeded and [outputApk] was created.
     */
    fun align(inputApk: File, outputApk: File): Boolean {
        if (!inputApk.exists() || inputApk.length() == 0L) return false

        try {
            val countingOut = CountingOutputStream(BufferedOutputStream(FileOutputStream(outputApk), 64 * 1024))
            val zipOut = ZipOutputStream(countingOut)

            ZipFile(inputApk).use { zipIn ->
                val entries = zipIn.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    // Omit previous signature blocks before alignment and signing
                    if (name.startsWith("META-INF/") && (
                            name.endsWith(".SF") ||
                            name.endsWith(".RSA") ||
                            name.endsWith(".DSA") ||
                            name.endsWith(".EC") ||
                            name.equals("META-INF/MANIFEST.MF", ignoreCase = true)
                        )) {
                        continue
                    }

                    val newEntry = ZipEntry(name).apply {
                        comment = entry.comment
                        time = entry.time
                    }

                    val isStored = entry.method == ZipEntry.STORED
                    val isNativeLib = name.endsWith(".so", ignoreCase = true)

                    if (isStored) {
                        newEntry.method = ZipEntry.STORED
                        newEntry.size = entry.size
                        newEntry.compressedSize = entry.compressedSize
                        newEntry.crc = entry.crc

                        val alignment = if (isNativeLib) PAGE_ALIGNMENT else DEFAULT_ALIGNMENT
                        val headerSize = 30 + name.toByteArray(Charsets.UTF_8).size
                        val dataOffsetWithoutExtra = countingOut.bytesWritten + headerSize
                        val padding = ((alignment - (dataOffsetWithoutExtra % alignment)) % alignment).toInt()
                        if (padding > 0) {
                            newEntry.extra = ByteArray(padding)
                        }
                    } else {
                        newEntry.method = ZipEntry.DEFLATED
                    }

                    zipOut.putNextEntry(newEntry)
                    zipIn.getInputStream(entry).use { inStream ->
                        inStream.copyTo(zipOut, 64 * 1024)
                    }
                    zipOut.closeEntry()
                }
            }
            zipOut.finish()
            zipOut.flush()
            zipOut.close()
            return outputApk.exists() && outputApk.length() > 0L
        } catch (e: Exception) {
            e.printStackTrace()
            try { outputApk.delete() } catch (_: Exception) {}
            return false
        }
    }

    /**
     * OutputStream wrapper that tracks exact bytes written for zip offset calculation.
     */
    private class CountingOutputStream(private val wrapped: OutputStream) : OutputStream() {
        var bytesWritten: Long = 0L
            private set

        override fun write(b: Int) {
            wrapped.write(b)
            bytesWritten++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            wrapped.write(b, off, len)
            bytesWritten += len
        }

        override fun flush() = wrapped.flush()
        override fun close() = wrapped.close()
    }
}
