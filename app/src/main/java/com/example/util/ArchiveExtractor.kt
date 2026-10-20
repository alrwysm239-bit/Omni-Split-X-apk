package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.model.ApkSplitInfo
import com.example.data.model.PackageAnalysis
import com.example.data.model.PairIpReport
import com.example.data.model.SplitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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

data class ArchiveEntryItem(
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val isApk: Boolean = path.endsWith(".apk", ignoreCase = true),
    val isManifest: Boolean = path.endsWith("AndroidManifest.xml", ignoreCase = true),
    val isDex: Boolean = path.endsWith(".dex", ignoreCase = true),
    val isResource: Boolean = path.startsWith("res/") || path.endsWith(".arsc")
)

object ArchiveExtractor {

    private const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer

    /**
     * Cleans a filename to get a friendly base name for output APK without double extensions.
     */
    fun cleanPackageName(fileName: String): String {
        var name = fileName
        val extensions = listOf(".xapk", ".apks", ".zip", ".apk")
        var changed = true
        while (changed) {
            changed = false
            for (ext in extensions) {
                if (name.endsWith(ext, ignoreCase = true)) {
                    name = name.substring(0, name.length - ext.length)
                    changed = true
                }
            }
        }
        return name.ifBlank { "app_package" }
    }

    /**
     * Copies content URI to a temporary cache file so ZipFile can perform robust random-access.
     */
    private suspend fun cacheUriToTempFile(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "omni_cache_${System.currentTimeMillis()}.tmp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output, BUFFER_SIZE)
            }
        } ?: throw IllegalStateException("Could not open input stream from URI.")
        tempFile
    }

    /**
     * Inspects an archive using ZipFile random access.
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
        val detectedArch = mutableSetOf<String>()
        var manifestStrings = listOf<String>()
        var jsonPackageName: String? = null
        var jsonVersionName: String? = null
        var jsonVersionCode: Long? = null

        val extension = fileName.substringAfterLast('.', "").lowercase()
        val format = when (extension) {
            "xapk" -> "XAPK"
            "apks" -> "APKS"
            "apk" -> "APK"
            "zip" -> "ZIP"
            else -> "ARCHIVE"
        }

        val tempFile = cacheUriToTempFile(context, uri)
        try {
            if (extension == "apk") {
                splits.add(
                    ApkSplitInfo(
                        name = fileName,
                        splitType = SplitType.STANDALONE,
                        size = fileSize
                    )
                )
                isStandalone = true
                baseApkFound = true

                ZipFile(tempFile).use { zip ->
                    val entry = zip.getEntry("AndroidManifest.xml")
                    if (entry != null) {
                        zip.getInputStream(entry).use { stream ->
                            manifestStrings = AxmlParser.extractStrings(stream)
                        }
                    } else {
                        // Check inside apk if needed
                        zip.getInputStream(zip.entries().nextElement()).use { apkStream ->
                            manifestStrings = extractManifestStringsFromZip(apkStream)
                        }
                    }
                }
            } else {
                ZipFile(tempFile).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        coroutineContext.ensureActive()
                        val entry = entries.nextElement()
                        val entryName = entry.name
                        val entrySize = entry.size

                        if (entryName.endsWith(".apk", ignoreCase = true)) {
                            val splitType = classifySplit(entryName)
                            val arch = extractArchFromSplitName(entryName)
                            if (arch != null) detectedArch.add(arch)

                            if (splitType == SplitType.BASE || entryName.endsWith("base.apk", ignoreCase = true)) {
                                baseApkFound = true
                            }

                            splits.add(
                                ApkSplitInfo(
                                    name = entryName.substringAfterLast('/'),
                                    splitType = splitType,
                                    size = if (entrySize > 0) entrySize else 0L,
                                    architecture = arch,
                                    density = extractDensityFromSplitName(entryName),
                                    language = extractLangFromSplitName(entryName)
                                )
                            )

                            // Read manifest from base.apk or first apk
                            if (manifestStrings.isEmpty() && (splitType == SplitType.BASE || entryName.contains("base", ignoreCase = true) || splits.size == 1)) {
                                zip.getInputStream(entry).use { apkStream ->
                                    manifestStrings = extractManifestStringsFromZip(apkStream)
                                }
                            }
                        } else if (entryName.contains(".obb", ignoreCase = true)) {
                            obbFiles.add(entryName.substringAfterLast('/'))
                        } else if (entryName.equals("manifest.json", ignoreCase = true)) {
                            try {
                                zip.getInputStream(entry).use { jsonStream ->
                                    val jsonStr = jsonStream.bufferedReader().use { it.readText() }
                                    val obj = JSONObject(jsonStr)
                                    if (obj.has("package_name")) jsonPackageName = obj.optString("package_name")
                                    if (obj.has("version_name")) jsonVersionName = obj.optString("version_name")
                                    if (obj.has("version_code")) jsonVersionCode = obj.optLong("version_code")
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                isStandalone = splits.any { it.splitType == SplitType.STANDALONE } ||
                    (splits.size == 1 && baseApkFound)
            }
        } finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }

        val pairIpReport = if (scanPairIp && manifestStrings.isNotEmpty()) {
            AxmlParser.auditPairIp(manifestStrings)
        } else null

        val (extractedPkg, extractedVName, extractedVCode) = AxmlParser.extractMetadata(manifestStrings)
        val finalPkg = jsonPackageName ?: extractedPkg
        val finalVName = jsonVersionName ?: extractedVName
        val finalVCode = jsonVersionCode ?: extractedVCode

        val archDisplay = if (detectedArch.isNotEmpty()) {
            detectedArch.joinToString(" / ")
        } else {
            "Universal / Multi-Arch"
        }

        PackageAnalysis(
            fileName = fileName,
            fileSize = fileSize,
            format = format,
            packageName = finalPkg,
            versionName = finalVName,
            versionCode = finalVCode,
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
     * Extracts and converts XAPK, APKS, ZIP, or APK into a single universal standalone APK file
     * inside destinationDir (Downloads/APK Website), merging all split APKs and stripping PairIP components.
     */
    suspend fun extractArchive(
        context: Context,
        sourceUri: Uri,
        fileName: String,
        totalBytes: Long,
        destinationDir: File,
        onProgress: (ExtractionProgress) -> Unit
    ): List<File> = withContext(Dispatchers.IO) {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val freeSpace = destinationDir.usableSpace
        val requiredSpace = if (totalBytes > 0) (totalBytes * 1.5).toLong() else 50 * 1024 * 1024L
        if (requiredSpace > 0 && freeSpace < requiredSpace) {
            throw IllegalStateException(
                "Insufficient storage: Free ${freeSpace / (1024 * 1024)} MB, Required ${requiredSpace / (1024 * 1024)} MB"
            )
        }

        val extractedFiles = mutableListOf<File>()
        val cleanBaseName = cleanPackageName(fileName)
        val tempFile = cacheUriToTempFile(context, sourceUri)

        try {
            val isSingleApk = fileName.endsWith(".apk", ignoreCase = true)
            if (isSingleApk) {
                val targetApk = File(destinationDir, "$cleanBaseName.apk")
                onProgress(
                    ExtractionProgress(
                        percentage = 0.1f,
                        bytesProcessed = 0,
                        totalBytes = totalBytes,
                        currentFileName = targetApk.name,
                        stage = "Saving APK to Downloads/APK Website..."
                    )
                )

                FileInputStream(tempFile).use { input ->
                    BufferedOutputStream(FileOutputStream(targetApk), BUFFER_SIZE).use { output ->
                        input.copyTo(output, BUFFER_SIZE)
                    }
                }
                if (targetApk.exists() && targetApk.length() > 0) {
                    val signed = ApkSignerUtil.signAndAlignApk(targetApk)
                    extractedFiles.add(signed)
                }
            } else {
                val apkEntries = mutableListOf<ZipEntry>()
                val obbEntries = mutableMapOf<String, ZipEntry>()

                ZipFile(tempFile).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val entryName = entry.name
                        if (entryName.endsWith(".apk", ignoreCase = true)) {
                            apkEntries.add(entry)
                        } else if (entryName.contains(".obb", ignoreCase = true)) {
                            obbEntries[entryName.substringAfterLast('/')] = entry
                        }
                    }
                }

                if (apkEntries.isEmpty()) {
                    throw IllegalStateException("No valid APK files found inside archive.")
                }

                if (apkEntries.size == 1) {
                    val targetApk = File(destinationDir, "$cleanBaseName.apk")
                    ZipFile(tempFile).use { zip ->
                        zip.getInputStream(apkEntries.first()).use { input ->
                            BufferedOutputStream(FileOutputStream(targetApk), BUFFER_SIZE).use { output ->
                                input.copyTo(output, BUFFER_SIZE)
                            }
                        }
                    }
                    if (targetApk.exists() && targetApk.length() > 0) {
                        val signed = ApkSignerUtil.signAndAlignApk(targetApk)
                        extractedFiles.add(signed)
                    }
                } else {
                    onProgress(
                        ExtractionProgress(
                            percentage = 0.2f,
                            bytesProcessed = 0,
                            totalBytes = totalBytes,
                            currentFileName = "Merging split APKs...",
                            stage = "Merging ${apkEntries.size} split APKs into universal APK & stripping PairIP..."
                        )
                    )

                    val baseEntry = apkEntries.firstOrNull {
                        it.name.contains("base", ignoreCase = true) || it.name.contains("master", ignoreCase = true)
                    } ?: apkEntries.maxByOrNull { it.size } ?: apkEntries.first()

                    val targetApk = File(destinationDir, "$cleanBaseName.apk")

                    ZipOutputStream(BufferedOutputStream(FileOutputStream(targetApk), BUFFER_SIZE)).use { outZip ->
                        val writtenEntries = mutableSetOf<String>()

                        // 1. Process Base APK
                        val baseTemp = File(context.cacheDir, "base_split_${System.currentTimeMillis()}.tmp")
                        try {
                            ZipFile(tempFile).use { zip ->
                                zip.getInputStream(baseEntry).use { input ->
                                    FileOutputStream(baseTemp).use { output ->
                                        input.copyTo(output, BUFFER_SIZE)
                                    }
                                }
                            }

                            ZipFile(baseTemp).use { baseZip ->
                                val baseEntries = baseZip.entries()
                                while (baseEntries.hasMoreElements()) {
                                    val entry = baseEntries.nextElement()
                                    val name = entry.name
                                    if (name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"))) {
                                        continue
                                    }
                                    if (name.contains("pairip", ignoreCase = true)) continue

                                    if (writtenEntries.add(name)) {
                                        outZip.putNextEntry(ZipEntry(name))
                                        baseZip.getInputStream(entry).use { it.copyTo(outZip, BUFFER_SIZE) }
                                        outZip.closeEntry()
                                    }
                                }
                            }
                        } finally {
                            try { baseTemp.delete() } catch (_: Exception) {}
                        }

                        // 2. Process Split APKs
                        var dexIndex = 2
                        for (splitEntry in apkEntries) {
                            if (splitEntry == baseEntry) continue
                            val splitTemp = File(context.cacheDir, "split_${System.currentTimeMillis()}.tmp")
                            try {
                                ZipFile(tempFile).use { zip ->
                                    zip.getInputStream(splitEntry).use { input ->
                                        FileOutputStream(splitTemp).use { output ->
                                            input.copyTo(output, BUFFER_SIZE)
                                        }
                                    }
                                }

                                ZipFile(splitTemp).use { splitZip ->
                                    val splitEntries = splitZip.entries()
                                    while (splitEntries.hasMoreElements()) {
                                        val entry = splitEntries.nextElement()
                                        var name = entry.name
                                        if (name.equals("AndroidManifest.xml", ignoreCase = true)) continue
                                        if (name.startsWith("META-INF/")) continue
                                        if (name.contains("pairip", ignoreCase = true)) continue

                                        if (name.matches(Regex("classes\\d*\\.dex", RegexOption.IGNORE_CASE))) {
                                            name = "classes$dexIndex.dex"
                                            dexIndex++
                                        }

                                        if (writtenEntries.add(name)) {
                                            outZip.putNextEntry(ZipEntry(name))
                                            splitZip.getInputStream(entry).use { it.copyTo(outZip, BUFFER_SIZE) }
                                            outZip.closeEntry()
                                        }
                                    }
                                }
                            } finally {
                                try { splitTemp.delete() } catch (_: Exception) {}
                            }
                        }
                    }

                    if (targetApk.exists() && targetApk.length() > 0) {
                        val signed = ApkSignerUtil.signAndAlignApk(targetApk)
                        extractedFiles.add(signed)
                    }
                }

                // Extract OBB files
                for ((obbName, obbEntry) in obbEntries) {
                    val targetObb = File(destinationDir, obbName)
                    ZipFile(tempFile).use { zip ->
                        zip.getInputStream(obbEntry).use { input ->
                            BufferedOutputStream(FileOutputStream(targetObb), BUFFER_SIZE).use { output ->
                                input.copyTo(output, BUFFER_SIZE)
                            }
                        }
                    }
                    if (targetObb.exists() && targetObb.length() > 0) {
                        extractedFiles.add(targetObb)
                    }
                }
            }
        } finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }

        if (extractedFiles.isEmpty()) {
            throw IllegalStateException("Extraction failed: no valid APK files generated.")
        }

        onProgress(
            ExtractionProgress(
                percentage = 1.0f,
                bytesProcessed = totalBytes,
                totalBytes = totalBytes,
                currentFileName = "$cleanBaseName.apk",
                stage = "Conversion complete! Universal APK ready in Downloads/APK Website",
                isComplete = true
            )
        )

        extractedFiles
    }

    /**
     * Lists internal entries of an APK or archive for the MT Manager-style Explorer.
     */
    suspend fun listArchiveEntries(
        context: Context,
        uri: Uri
    ): List<ArchiveEntryItem> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<ArchiveEntryItem>()
        val tempFile = cacheUriToTempFile(context, uri)
        try {
            ZipFile(tempFile).use { zip ->
                val zipEntries = zip.entries()
                var count = 0
                while (zipEntries.hasMoreElements() && count < 400) {
                    val entry = zipEntries.nextElement()
                    entries.add(
                        ArchiveEntryItem(
                            path = entry.name,
                            size = if (entry.size > 0) entry.size else 0L,
                            isDirectory = entry.isDirectory
                        )
                    )
                    count++
                }
            }
        } finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }
        entries.sortedWith(compareBy({ !it.isDirectory }, { it.path }))
    }

    /**
     * Reads AndroidManifest.xml strings from a zip stream.
     */
    fun extractManifestStringsFromZip(stream: InputStream): List<String> {
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
