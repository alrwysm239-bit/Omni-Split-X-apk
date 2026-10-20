package com.example.util

import java.io.File

/**
 * Backwards compatibility delegate forwarding to [ApkSignerHelper].
 */
object ApkSignerUtil {

    /**
     * Performs ZipAlign and official multi-scheme APK signing (v1, v2, v3) on [apkFile].
     */
    fun signAndAlignApk(apkFile: File): File {
        return ApkSignerHelper.signAndAlignApk(apkFile)
    }
}
