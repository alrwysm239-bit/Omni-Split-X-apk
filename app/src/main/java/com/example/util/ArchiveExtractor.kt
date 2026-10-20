package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.model.ApkSplitInfo
import com.example.data.model.PackageAnalysis
import com.example.data.model.PairIpReport
import com.example.data.model.SplitType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

data class ExtractionProgress(
    val percentage: Float,
    val bytesProcessed: Long,
    val totalBytes: Long,
    val currentFileName: String,
    val stage: String,
    val isComplete: Boolean = false,
    val error: String? = null
)

object ArchiveExtractor {

    private const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer

    /**
     * Inspects an archive without full extraction by streaming through its entries.
     */
    suspend fun analyzeArchive(
        context: Context,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        scanPairIp: Boolean = true
    ): PackageAnalysis = withContext(Dispatchers.IO) {
        val splits = mutableListOf<ApkSplitInfo>()
        val obbFiles = mutableListOf<String>()
        var baseApkFound = false
        var isStandalone = false
        var detectedArch = mutableSetOf<String>()
        var manifestStrings = listOf<String>()

        val extension = fileName.substringAfterLast('.', "").lowercase()
        val format = when (extension) {
            "xapk" -> "XAPK"
            "apks" -> "APKS"
            "apk" -> "APK"
            "zip" -> "ZIP"
            else -> "ARCHIVE"
        }

        if (extension == "apk") {
            // Single APK
            splits.add(
                ApkSplitInfo(
                    name = fileName,
                    splitType = SplitType.STANDALONE,
                    size = fileSize
                )
            )
            isStandalone = true
            baseApkFound = true

            // Read AndroidManifest.xml from single APK
            context.contentResolver.openInputStream(uri)?.use { stream ->
                manifestStrings = extractManifestStringsFromZip(stream)
            }
        } else {
            // Archive (XAPK, APKS, ZIP)
            context.contentResolver.openInputStream(uri)?.use { rawStream ->
                ZipInputStream(BufferedInputStream(rawStream, BUFFER_SIZE)).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        coroutineContext.ensureActive()
                        val entryName = entry.name
                        val entrySize = entry.size

                        if (entryName.endsWith(".apk", ignoreCase = true)) {
                            val splitType = classifySplit(entryName)
                            val arch = extractArchFromSplitName(entryName)
                            if (arch != null) detectedArch.add(arch)

                            if (splitType == SplitType.BASE) {
                                baseApkFound = true
                            }

                            splits.add(
                                ApkSplitInfo(
                                    name = entryName,
                                    splitType = splitType,
                                    size = if (entrySize > 0) entrySize else 0L,
                                    architecture = arch,
                                    density = extractDensityFromSplitName(entryName),
                                    language = extractLangFromSplitName(entryName)
                                )
                            )

                            // Read manifest from base.apk or first apk
                            if (manifestStrings.isEmpty() && (splitType == SplitType.BASE || splits.size == 1)) {
                                val manifestBytes = extractManifestBytesFromApkEntry(zip)
                                if (manifestBytes != null) {
                                    manifestStrings = AxmlParser.extractStrings(manifestBytes.inputStream())
                                }
                            }
                        } else if (entryName.contains(".obb", ignoreCase = true)) {
                            obbFiles.add(entryName)
                        } else if (entryName.equals("manifest.json", ignoreCase = true)) {
                            // XAPK manifest
                        }

                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            // Determine standalone capability
            isStandalone = splits.any { it.splitType == SplitType.STANDALONE } ||
                (splits.size == 1 && baseApkFound)
        }

        val pairIpReport = if (scanPairIp && manifestStrings.isNotEmpty()) {
            AxmlParser.auditPairIp(manifestStrings)
        } else null

        val (pkgName, vName, vCode) = AxmlParser.extractMetadata(manifestStrings)
        val archDisplay = if (detectedArch.isNotEmpty()) {
            detectedArch.joinToString(" / ")
        } else {
            "Universal / Multi-Arch"
        }

        PackageAnalysis(
            fileName = fileName,
            fileSize = fileSize,
            format = format,
            packageName = pkgName,
            versionName = vName,
            versionCode = vCode,
            minSdk = 24,
            targetSdk = 35,
            architecture = archDisplay,
            isStandaloneApk = isStandalone,
            baseApkFound = baseApkFound,
            splits = splits,
            obbFiles = obbFiles,
            pairIpReport = pairIpReport,
            manifestRawXmlSummary = if (manifestStrings.isNotEmpty()) {
                "Found ${manifestStrings.size} manifest string entries"
            } else null
        )
    }

    /**
     * Extracts an archive with streaming I/O, progress updates, and Zip Slip prevention.
     */
    suspend fun extractArchive(
        context: Context,
        sourceUri: Uri,
        totalBytes: Long,
        destinationDir: File,
        onProgress: (ExtractionProgress) -> Unit
    ): List<File> = withContext(Dispatchers.IO) {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        // Verify available disk space
        val freeSpace = destinationDir.usableSpace
        val requiredSpace = (totalBytes * 1.5).toLong()
        if (requiredSpace > 0 && freeSpace < requiredSpace) {
            throw IllegalStateException(
                "Insufficient storage: Free ${freeSpace / (1024 * 1024)} MB, Required ${requiredSpace / (1024 * 1024)} MB"
            )
        }

        val extractedFiles = mutableListOf<File>()
        var bytesProcessed = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        val destCanonicalPath = destinationDir.canonicalPath

        context.contentResolver.openInputStream(sourceUri)?.use { rawStream ->
            ZipInputStream(BufferedInputStream(rawStream, BUFFER_SIZE)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    coroutineContext.ensureActive()
                    val entryName = entry.name

                    // ZIP Slip Protection
                    val targetFile = File(destinationDir, entryName)
                    val targetCanonicalPath = targetFile.canonicalPath
                    if (!targetCanonicalPath.startsWith(destCanonicalPath + File.separator) &&
                        targetCanonicalPath != destCanonicalPath
                    ) {
                        throw SecurityException("Zip Slip exploit attempt detected: $entryName")
                    }

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()

                        // Only extract relevant files: APK, OBB, JSON manifest
                        if (entryName.endsWith(".apk", ignoreCase = true) ||
                            entryName.endsWith(".obb", ignoreCase = true) ||
                            entryName.endsWith(".json", ignoreCase = true)
                        ) {
                            BufferedOutputStream(FileOutputStream(targetFile), BUFFER_SIZE).use { out ->
                                var len: Int
                                while (zip.read(buffer).also { len = it } != -1) {
                                    coroutineContext.ensureActive()
                                    out.write(buffer, 0, len)
                                    bytesProcessed += len
                                    if (totalBytes > 0) {
                                        val pct = (bytesProcessed.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                        onProgress(
                                            ExtractionProgress(
                                                percentage = pct,
                                                bytesProcessed = bytesProcessed,
                                                totalBytes = totalBytes,
                                                currentFileName = targetFile.name,
                                                stage = "Extracting ${targetFile.name}..."
                                            )
                                        )
                                    }
                                }
                            }
                            extractedFiles.add(targetFile)
                        } else {
                            // Skip non-essential files while keeping streaming counter
                            var len: Int
                            while (zip.read(buffer).also { len = it } != -1) {
                                bytesProcessed += len
                            }
                        }
                    }

                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("Could not open input stream from file.")

        onProgress(
            ExtractionProgress(
                percentage = 1.0f,
                bytesProcessed = bytesProcessed,
                totalBytes = totalBytes,
                currentFileName = "Completed",
                stage = "Extraction complete!",
                isComplete = true
            )
        )

        extractedFiles
    }

    private fun extractManifestStringsFromZip(stream: InputStream): List<String> {
        return try {
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.equals("AndroidManifest.xml", ignoreCase = true)) {
                        val bytes = zip.readBytes()
                        return AxmlParser.extractStrings(bytes.inputStream())
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractManifestBytesFromApkEntry(zip: ZipInputStream): ByteArray? {
        return try {
            val apkBytes = zip.readBytes()
            ZipInputStream(apkBytes.inputStream()).use { innerZip ->
                var innerEntry = innerZip.nextEntry
                while (innerEntry != null) {
                    if (innerEntry.name.equals("AndroidManifest.xml", ignoreCase = true)) {
                        return innerZip.readBytes()
                    }
                    innerZip.closeEntry()
                    innerEntry = innerZip.nextEntry
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun classifySplit(name: String): SplitType {
        val lower = name.lowercase()
        return when {
            lower.contains("base.apk") || lower.contains("base-master.apk") -> SplitType.BASE
            lower.contains("standalone") -> SplitType.STANDALONE
            lower.contains("arm64") || lower.contains("armeabi") || lower.contains("x86") -> SplitType.CONFIG_ARCH
            lower.contains("hdpi") || lower.contains("mdpi") || lower.contains("ldpi") -> SplitType.CONFIG_DENSITY
            lower.contains("config.en") || lower.contains("config.ar") || lower.contains("lang") -> SplitType.CONFIG_LANGUAGE
            lower.contains("feature") -> SplitType.FEATURE
            else -> SplitType.BASE
        }
    }

    private fun extractArchFromSplitName(name: String): String? {
        val lower = name.lowercase()
        return when {
            lower.contains("arm64_v8a") || lower.contains("arm64-v8a") -> "arm64-v8a"
            lower.contains("armeabi_v7a") || lower.contains("armeabi-v7a") -> "armeabi-v7a"
            lower.contains("x86_64") -> "x86_64"
            lower.contains("x86") -> "x86"
            else -> null
        }
    }

    private fun extractDensityFromSplitName(name: String): String? {
        val lower = name.lowercase()
        return when {
            lower.contains("xxxhdpi") -> "xxxhdpi"
            lower.contains("xxhdpi") -> "xxhdpi"
            lower.contains("xhdpi") -> "xhdpi"
            lower.contains("hdpi") -> "hdpi"
            lower.contains("mdpi") -> "mdpi"
            lower.contains("ldpi") -> "ldpi"
            else -> null
        }
    }

    private fun extractLangFromSplitName(name: String): String? {
        val regex = Regex("config\\.(en|ar|es|fr|de|ru|zh|pt|ja|ko)")
        val match = regex.find(name.lowercase())
        return match?.groupValues?.getOrNull(1)
    }
}
