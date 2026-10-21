package com.example.util

/**
 * Asset & Native Library Sanitizer.
 *
 * Ensures that during Split APK merging and repacking:
 * 1. Assets folder is thoroughly inspected and stripped of temporary folders,
 *    orphaned cache files, and system artifacts (e.g. .DS_Store, Thumbs.db, temp/).
 * 2. Native library (.so) files under lib/ are validated across all ABIs
 *    (arm64-v8a, armeabi-v7a, x86, x86_64). Corrupted, zero-byte, or non-ELF
 *    files are discarded to prevent "App not installed" and native crash issues.
 */
object PackageSanitizer {

    // Standard Android Architecture ABIs supported by Android OS
    val SUPPORTED_ABIS = setOf(
        "arm64-v8a",
        "armeabi-v7a",
        "x86",
        "x86_64",
        "armeabi"
    )

    // ELF magic header: 0x7F, 'E', 'L', 'F'
    private val ELF_MAGIC = byteArrayOf(0x7F.toByte(), 0x45.toByte(), 0x4C.toByte(), 0x46.toByte())

    /**
     * Sanitization report metrics.
     */
    data class SanitizationStats(
        var totalEntriesScanned: Int = 0,
        var assetsCleaned: Int = 0,
        var nativeLibsValidated: Int = 0,
        var nativeLibsDiscarded: Int = 0,
        val detectedAbis: MutableSet<String> = mutableSetOf()
    )

    /**
     * Determines whether an asset entry should be included or stripped.
     *
     * @param entryName The zip entry path.
     * @param entrySize Uncompressed size in bytes.
     * @return true if the asset is valid and should be preserved; false if it is temporary/junk.
     */
    fun shouldKeepAsset(entryName: String, entrySize: Long): Boolean {
        if (!entryName.startsWith("assets/")) return true

        val lower = entryName.lowercase()

        // Strip OS and temp metadata files
        if (lower.endsWith("/.ds_store") || lower == "assets/.ds_store") return false
        if (lower.endsWith("/thumbs.db") || lower == "assets/thumbs.db") return false
        if (lower.endsWith(".tmp") || lower.endsWith(".bak")) return false

        // Strip temporary and build cache folders
        if (lower.contains("assets/temp/") ||
            lower.contains("assets/.temp/") ||
            lower.contains("assets/.cache/") ||
            lower.contains("assets/debug/")
        ) {
            return false
        }

        // Strip empty placeholder files if size is 0 and name is .keep or .placeholder
        if (entrySize == 0L && (lower.endsWith(".keep") || lower.endsWith(".gitkeep") || lower.endsWith(".placeholder"))) {
            return false
        }

        return true
    }

    /**
     * Inspects and validates a native library (.so) file.
     *
     * @param entryName The entry path (e.g. "lib/arm64-v8a/libunity.so").
     * @param headerBytes Initial bytes of the file (at least 4 bytes for ELF magic).
     * @param totalSize Total byte size of the entry.
     * @return true if the library belongs to a valid ABI and has a valid ELF header.
     */
    fun isValidNativeLibrary(
        entryName: String,
        headerBytes: ByteArray,
        totalSize: Long
    ): Boolean {
        if (!entryName.startsWith("lib/")) return true
        if (!entryName.endsWith(".so", ignoreCase = true)) return true

        // Must have at least 4 bytes for ELF check
        if (totalSize < 4L || headerBytes.size < 4) {
            return false
        }

        // Validate standard ABI path segment: lib/<abi>/<filename>.so
        val parts = entryName.split('/')
        if (parts.size >= 2) {
            val abi = parts[1]
            if (abi !in SUPPORTED_ABIS) {
                return false
            }
        }

        // Validate ELF Magic: 0x7F 'E' 'L' 'F'
        return headerBytes[0] == ELF_MAGIC[0] &&
            headerBytes[1] == ELF_MAGIC[1] &&
            headerBytes[2] == ELF_MAGIC[2] &&
            headerBytes[3] == ELF_MAGIC[3]
    }

    /**
     * Extracts ABI string from entry path if applicable.
     */
    fun extractAbi(entryName: String): String? {
        if (!entryName.startsWith("lib/")) return null
        val parts = entryName.split('/')
        if (parts.size >= 2 && parts[1] in SUPPORTED_ABIS) {
            return parts[1]
        }
        return null
    }
}
