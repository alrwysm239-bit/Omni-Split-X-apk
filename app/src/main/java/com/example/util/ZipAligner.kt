package com.example.util

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Robust 4-byte and 4096-byte ZipAligner for Android APKs.
 *
 * Implements Android SDK-compliant zipalign:
 * 1. Aligns all uncompressed (STORED) entries to 4-byte boundaries.
 * 2. Aligns all shared native libraries (.so) to 4096-byte (4KB) page boundaries
 *    for efficient mmap execution on 64-bit and 32-bit Android runtimes.
 * 3. Enforces uncompressed STORED format on resources.arsc (which must never be compressed
 *    in modern Android APKs to avoid "App not installed" errors).
 * 4. Cleans out existing signature files from META-INF to prepare for pristine V1/V2/V3 signing.
 */
object ZipAligner {

    const val DEFAULT_ALIGNMENT = 4
    const val PAGE_ALIGNMENT = 4096 // 4KB page alignment for .so libraries

    /**
     * Performs 4-byte and 4096-byte alignment on [inputApk], writing the result to [outputApk].
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

                    val isArsc = name.equals("resources.arsc", ignoreCase = true)
                    val isNativeLib = name.endsWith(".so", ignoreCase = true)
                    val isStoredOriginally = entry.method == ZipEntry.STORED

                    // resources.arsc MUST be STORED (uncompressed) in production APKs
                    val shouldBeStored = isStoredOriginally || isArsc

                    val newEntry = ZipEntry(name).apply {
                        comment = entry.comment
                        time = entry.time
                    }

                    val entryBytes: ByteArray?
                    if (shouldBeStored) {
                        newEntry.method = ZipEntry.STORED

                        // Read bytes to compute exact CRC and size if converting or if originally stored
                        val baos = ByteArrayOutputStream()
                        zipIn.getInputStream(entry).use { it.copyTo(baos, 64 * 1024) }
                        entryBytes = baos.toByteArray()

                        val crcCalculator = CRC32()
                        crcCalculator.update(entryBytes)

                        newEntry.size = entryBytes.size.toLong()
                        newEntry.compressedSize = entryBytes.size.toLong()
                        newEntry.crc = crcCalculator.value

                        // Compute alignment padding
                        val alignment = if (isNativeLib) PAGE_ALIGNMENT else DEFAULT_ALIGNMENT
                        val fileNameBytes = name.toByteArray(Charsets.UTF_8)
                        val headerSize = 30 + fileNameBytes.size
                        val dataOffsetWithoutExtra = countingOut.bytesWritten + headerSize
                        val padding = ((alignment - (dataOffsetWithoutExtra % alignment)) % alignment).toInt()

                        if (padding > 0) {
                            newEntry.extra = ByteArray(padding)
                        }
                    } else {
                        newEntry.method = ZipEntry.DEFLATED
                        entryBytes = null
                    }

                    zipOut.putNextEntry(newEntry)
                    if (entryBytes != null) {
                        zipOut.write(entryBytes)
                    } else {
                        zipIn.getInputStream(entry).use { inStream ->
                            inStream.copyTo(zipOut, 64 * 1024)
                        }
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
