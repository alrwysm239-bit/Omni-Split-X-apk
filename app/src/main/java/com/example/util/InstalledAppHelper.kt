package com.example.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.example.data.model.SigningConfig
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Data model representing an installed app (user app or system app).
 */
data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val isSplit: Boolean,
    val splitCount: Int,
    val baseApkPath: String,
    val splitSourceDirs: List<String>,
    val totalSizeBytes: Long,
    val firstInstallTime: Long
)

/**
 * Utility for querying, extracting, and converting installed device and system apps
 * (both split and non-split) into standalone, universal, installable APKs.
 */
object InstalledAppHelper {

    private const val BUFFER_SIZE = 64 * 1024

    /**
     * Retrieves all installed applications on the device.
     */
    fun getInstalledApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val allApps: List<ApplicationInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val result = mutableListOf<InstalledAppInfo>()

        for (app in allApps) {
            val baseApk = app.sourceDir ?: continue
            val baseFile = File(baseApk)
            if (!baseFile.exists() || !baseFile.canRead()) continue

            val appName = try {
                pm.getApplicationLabel(app).toString()
            } catch (_: Exception) {
                app.packageName
            }

            val pkgInfo: PackageInfo? = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(app.packageName, 0)
                }
            } catch (_: Exception) {
                null
            }

            val versionName = pkgInfo?.versionName ?: "1.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo?.longVersionCode ?: 1L
            } else {
                @Suppress("DEPRECATION")
                (pkgInfo?.versionCode ?: 1).toLong()
            }

            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            val splits = app.splitSourceDirs?.filter {
                val f = File(it)
                f.exists() && f.canRead()
            } ?: emptyList()

            var totalSize = baseFile.length()
            splits.forEach { totalSize += File(it).length() }

            val isSplit = splits.isNotEmpty()

            result.add(
                InstalledAppInfo(
                    appName = appName,
                    packageName = app.packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    isSystemApp = isSystem,
                    isSplit = isSplit,
                    splitCount = splits.size,
                    baseApkPath = baseApk,
                    splitSourceDirs = splits,
                    totalSizeBytes = totalSize,
                    firstInstallTime = pkgInfo?.firstInstallTime ?: 0L
                )
            )
        }

        // Sort: User apps first, then by app name
        return result.sortedWith(
            compareBy<InstalledAppInfo> { it.isSystemApp }
                .thenBy { it.appName.lowercase() }
        )
    }

    /**
     * Converts an installed application (merging all split APKs and base.apk,
     * sanitizing AndroidManifest.xml, removing PairIP/Psirip, ZipAligning, and signing).
     */
    fun convertInstalledApp(
        context: Context,
        appInfo: InstalledAppInfo,
        destinationDir: File,
        signingConfig: SigningConfig,
        onProgress: (ExtractionProgress) -> Unit
    ): File {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val cleanName = cleanFileName(appInfo.appName.ifBlank { appInfo.packageName })
        val targetApk = File(destinationDir, "${cleanName}_universal.apk")
        val tempApk = File(context.cacheDir, "temp_installed_${System.currentTimeMillis()}.apk")

        try {
            onProgress(
                ExtractionProgress(
                    percentage = 0.1f,
                    bytesProcessed = 0,
                    totalBytes = appInfo.totalSizeBytes,
                    currentFileName = appInfo.appName,
                    stage = "Reading base APK and splits..."
                )
            )

            val writtenEntries = mutableSetOf<String>()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempApk), BUFFER_SIZE)).use { outZip ->
                // 1. Process Base APK
                val baseApkFile = File(appInfo.baseApkPath)
                ZipFile(baseApkFile).use { baseZip ->
                    val baseEntries = baseZip.entries()
                    while (baseEntries.hasMoreElements()) {
                        val entry = baseEntries.nextElement()
                        val name = entry.name

                        // Skip old signature blocks
                        if (name.startsWith("META-INF/") && (
                                name.endsWith(".SF") ||
                                name.endsWith(".RSA") ||
                                name.endsWith(".DSA") ||
                                name.endsWith(".EC") ||
                                name.equals("META-INF/MANIFEST.MF", ignoreCase = true)
                            )) {
                            continue
                        }

                        // Remove PairIP / Psirip entries
                        if (name.contains("pairip", ignoreCase = true) || name.contains("psirip", ignoreCase = true)) {
                            continue
                        }

                        if (writtenEntries.add(name)) {
                            val isManifest = name.equals("AndroidManifest.xml", ignoreCase = true)
                            val newEntry = ZipEntry(name)
                            outZip.putNextEntry(newEntry)
                            if (isManifest) {
                                val manifestBytes = baseZip.getInputStream(entry).use { it.readBytes() }
                                val sanitizedBytes = AxmlParser.sanitizeSplitManifest(manifestBytes)
                                outZip.write(sanitizedBytes)
                            } else {
                                baseZip.getInputStream(entry).use { it.copyTo(outZip, BUFFER_SIZE) }
                            }
                            outZip.closeEntry()
                        }
                    }
                }

                onProgress(
                    ExtractionProgress(
                        percentage = 0.5f,
                        bytesProcessed = appInfo.totalSizeBytes / 2,
                        totalBytes = appInfo.totalSizeBytes,
                        currentFileName = appInfo.appName,
                        stage = "Merging ${appInfo.splitCount} split APKs and native libraries..."
                    )
                )

                // 2. Determine max dex index from base APK
                val maxBaseDex = writtenEntries.mapNotNull { entryName ->
                    when {
                        entryName.equals("classes.dex", ignoreCase = true) -> 1
                        entryName.matches(Regex("classes(\\d+)\\.dex", RegexOption.IGNORE_CASE)) -> {
                            Regex("classes(\\d+)\\.dex", RegexOption.IGNORE_CASE)
                                .find(entryName)?.groupValues?.get(1)?.toIntOrNull()
                        }
                        else -> null
                    }
                }.maxOrNull() ?: 1
                var nextDexIndex = maxBaseDex + 1

                // 3. Process Split APKs
                for (splitPath in appInfo.splitSourceDirs) {
                    val splitFile = File(splitPath)
                    if (!splitFile.exists() || !splitFile.canRead()) continue

                    ZipFile(splitFile).use { splitZip ->
                        val splitEntries = splitZip.entries()
                        while (splitEntries.hasMoreElements()) {
                            val entry = splitEntries.nextElement()
                            var name = entry.name

                            if (name.equals("AndroidManifest.xml", ignoreCase = true)) continue
                            if (name.startsWith("META-INF/")) continue
                            if (name.contains("pairip", ignoreCase = true) || name.contains("psirip", ignoreCase = true)) continue

                            if (name.matches(Regex("classes\\d*\\.dex", RegexOption.IGNORE_CASE))) {
                                name = "classes$nextDexIndex.dex"
                                nextDexIndex++
                            }

                            if (writtenEntries.add(name)) {
                                outZip.putNextEntry(ZipEntry(name))
                                splitZip.getInputStream(entry).use { it.copyTo(outZip, BUFFER_SIZE) }
                                outZip.closeEntry()
                            }
                        }
                    }
                }
            }

            onProgress(
                ExtractionProgress(
                    percentage = 0.8f,
                    bytesProcessed = (appInfo.totalSizeBytes * 0.8).toLong(),
                    totalBytes = appInfo.totalSizeBytes,
                    currentFileName = targetApk.name,
                    stage = "Applying ZipAlign (4-byte & 4KB) and signing APK..."
                )
            )

            // Move temp to target and sign
            if (targetApk.exists()) targetApk.delete()
            tempApk.renameTo(targetApk)

            val signedApk = ApkSignerHelper.signAndAlignApk(targetApk, signingConfig)

            onProgress(
                ExtractionProgress(
                    percentage = 1.0f,
                    bytesProcessed = appInfo.totalSizeBytes,
                    totalBytes = appInfo.totalSizeBytes,
                    currentFileName = signedApk.name,
                    stage = "Successfully converted and signed!"
                )
            )

            return signedApk
        } finally {
            try { tempApk.delete() } catch (_: Exception) {}
        }
    }

    private fun cleanFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            .trim('_')
            .ifBlank { "app" }
    }
}
