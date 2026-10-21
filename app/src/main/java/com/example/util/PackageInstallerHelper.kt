package com.example.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

object PackageInstallerHelper {

    /**
     * Installs split APKs atomically using the native Android PackageInstaller Session API.
     */
    suspend fun installSplitApks(
        context: Context,
        apkFiles: List<File>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apkFiles.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No APK files provided for installation."))
            }

            // Check if user has permission to install unknown apps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    return@withContext Result.failure(
                        SecurityException("PERMISSION_NEEDED")
                    )
                }
            }

            // If there is only 1 standalone APK, use standard installation intent
            if (apkFiles.size == 1) {
                return@withContext installSingleApk(context, apkFiles.first())
            }

            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.use { activeSession ->
                val buffer = ByteArray(64 * 1024)
                for (file in apkFiles) {
                    if (!file.name.endsWith(".apk", ignoreCase = true)) continue
                    val nameInSession = file.name
                    activeSession.openWrite(nameInSession, 0, file.length()).use { output ->
                        FileInputStream(file).use { input ->
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                }

                val intent = Intent(context, context.javaClass).apply {
                    action = "com.example.omnisplitx.INSTALL_COMPLETE"
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getActivity(context, sessionId, intent, flags)

                activeSession.commit(pendingIntent.intentSender)
            }

            Result.success("Installation session initiated successfully.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Installs a single standalone APK via Intent.
     */
    fun installSingleApk(context: Context, apkFile: File): Result<String> {
        return try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            Result.success("Standalone installer opened.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks if a package with [packageName] is currently installed on the device.
     */
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Prompts the user to uninstall an existing conflicting package to avoid "App not installed" errors.
     */
    fun promptUninstallPackage(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Shares an APK file or extracted package.
     */
    fun shareApk(context: Context, file: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share APK"))
    }
}
