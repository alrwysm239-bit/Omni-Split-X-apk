package com.example.util

import com.example.data.model.PairIpComponent
import com.example.data.model.PairIpReport
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AxmlParser {

    private const val RES_XML_TYPE = 0x00080003
    private const val RES_STRING_POOL_TYPE = 0x001C0001

    /**
     * Extracts all strings from Android's binary XML (AXML) format.
     */
    fun extractStrings(inputStream: InputStream): List<String> {
        val bytes = inputStream.readBytes()
        if (bytes.size < 8) return emptyList()

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val headerType = buffer.int
        if (headerType != RES_XML_TYPE && headerType != 0x00080000) {
            return scanPrintableStrings(bytes)
        }

        buffer.int // file size
        val strings = mutableListOf<String>()

        while (buffer.hasRemaining() && buffer.remaining() >= 8) {
            val chunkType = buffer.int
            val chunkSize = buffer.int

            if (chunkType == RES_STRING_POOL_TYPE) {
                val poolStart = buffer.position() - 8
                val stringCount = buffer.int
                buffer.int // styleCount
                val flags = buffer.int
                val stringsStart = buffer.int
                buffer.int // stylesStart

                val isUtf8 = (flags and (1 shl 8)) != 0

                val stringOffsets = IntArray(stringCount)
                for (i in 0 until stringCount) {
                    if (buffer.remaining() >= 4) {
                        stringOffsets[i] = buffer.int
                    }
                }

                val stringsActualBase = poolStart + stringsStart

                for (offset in stringOffsets) {
                    val strPos = stringsActualBase + offset
                    if (strPos in 0 until bytes.size) {
                        val parsed = if (isUtf8) {
                            parseUtf8String(bytes, strPos)
                        } else {
                            parseUtf16String(bytes, strPos)
                        }
                        if (parsed.isNotBlank()) {
                            strings.add(parsed)
                        }
                    }
                }
                break
            } else {
                val skip = chunkSize - 8
                if (skip > 0 && buffer.remaining() >= skip) {
                    buffer.position(buffer.position() + skip)
                } else {
                    break
                }
            }
        }

        if (strings.isEmpty()) {
            return scanPrintableStrings(bytes)
        }
        return strings
    }

    private fun parseUtf8String(bytes: ByteArray, pos: Int): String {
        var p = pos
        if (p >= bytes.size) return ""
        val len1 = bytes[p++].toInt() and 0xFF
        if (len1 and 0x80 != 0 && p < bytes.size) p++
        if (p >= bytes.size) return ""
        val u8len = bytes[p++].toInt() and 0xFF
        val actualLen = if (u8len and 0x80 != 0 && p < bytes.size) {
            ((u8len and 0x7F) shl 8) or (bytes[p++].toInt() and 0xFF)
        } else {
            u8len
        }

        val end = minOf(p + actualLen, bytes.size)
        return try {
            String(bytes, p, end - p, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseUtf16String(bytes: ByteArray, pos: Int): String {
        var p = pos
        if (p + 1 >= bytes.size) return ""
        var charCount = (bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() and 0xFF) shl 8)
        p += 2
        if (charCount and 0x8000 != 0 && p + 1 < bytes.size) {
            val high = (bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() and 0xFF) shl 8)
            charCount = ((charCount and 0x7FFF) shl 16) or high
            p += 2
        }

        val byteLen = charCount * 2
        val end = minOf(p + byteLen, bytes.size)
        return try {
            String(bytes, p, end - p, Charsets.UTF_16LE)
        } catch (_: Exception) {
            ""
        }
    }

    fun scanPrintableStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        for (b in bytes) {
            val c = b.toInt().toChar()
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "._-/:$,;=\" ") {
                sb.append(c)
            } else {
                if (sb.length >= 4) {
                    result.add(sb.toString().trim())
                }
                sb.setLength(0)
            }
        }
        if (sb.length >= 4) {
            result.add(sb.toString().trim())
        }
        return result
    }

    /**
     * Inspects extracted manifest strings for PairIP licensing components
     * and produces a compliant diagnostics audit report.
     */
    fun auditPairIp(manifestStrings: List<String>): PairIpReport {
        val detected = mutableListOf<PairIpComponent>()

        val pairIpActivity = "com.pairip.licensecheck.LicenseActivity"
        val pairIpProvider = "com.pairip.licensecheck.LicenseContentProvider"

        val hasActivity = manifestStrings.any { it.contains(pairIpActivity, ignoreCase = true) }
        val hasProvider = manifestStrings.any { it.contains(pairIpProvider, ignoreCase = true) }

        if (hasActivity) {
            detected.add(
                PairIpComponent(
                    componentName = pairIpActivity,
                    type = "Activity",
                    details = "PairIP runtime licensing check activity registered in manifest."
                )
            )
        }

        if (hasProvider) {
            detected.add(
                PairIpComponent(
                    componentName = pairIpProvider,
                    type = "ContentProvider",
                    details = "PairIP license initialization ContentProvider registered in manifest."
                )
            )
        }

        // Check for any other pairip references
        manifestStrings.filter {
            it.contains("pairip", ignoreCase = true) &&
                !it.equals(pairIpActivity, ignoreCase = true) &&
                !it.equals(pairIpProvider, ignoreCase = true)
        }.distinct().take(10).forEach { extra ->
            detected.add(
                PairIpComponent(
                    componentName = extra,
                    type = "Manifest String",
                    details = "Associated PairIP security / license reference found in string pool."
                )
            )
        }

        val hasPairIp = detected.isNotEmpty()
        val snippets = detected.map { it.componentName }

        val riskSummary = if (hasPairIp) {
            "PairIP licensing verification components detected (${detected.size} elements). " +
                "These components protect application distribution through Google Play licensing services."
        } else {
            "No PairIP licensing verification components found. Manifest is clean of com.pairip dependencies."
        }

        val recommendations = if (hasPairIp) {
            "Diagnostic Report:\n" +
                "• Components: ${detected.joinToString { it.componentName }}\n" +
                "• Explanation: These components verify license entitlement. Their presence indicates official Play Integrity/Licensing integration.\n" +
                "• Authorized Developer Build: To remove PairIP from your own application, remove the dependency in build.gradle and re-sign with your upload key."
        } else {
            "Standard APK package structure without external DRM license checks."
        }

        return PairIpReport(
            hasPairIp = hasPairIp,
            detectedComponents = detected,
            scannedEntriesCount = manifestStrings.size,
            riskSummary = riskSummary,
            recommendations = recommendations,
            manifestSnippets = snippets
        )
    }

    /**
     * Extracts package name, version, and architecture hints from manifest strings.
     */
    fun extractMetadata(strings: List<String>): Triple<String, String, Long> {
        var packageName = ""
        var versionName = ""
        var versionCode = 1L

        for (s in strings) {
            if (packageName.isEmpty() && s.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){2,}$"))) {
                if (!s.startsWith("android.") && !s.startsWith("com.google.") && !s.startsWith("androidx.")) {
                    packageName = s
                }
            }
            if (versionName.isEmpty() && s.matches(Regex("^v?[0-9]+\\.[0-9]+(\\.[0-9]+)?.*"))) {
                versionName = s
            }
        }

        if (packageName.isEmpty()) packageName = "com.example.app"
        if (versionName.isEmpty()) versionName = "1.0.0"

        return Triple(packageName, versionName, versionCode)
    }

    /**
     * Generates a reconstructed, readable AndroidManifest representation for the MT Manager-style viewer.
     */
    fun generateReadableManifest(strings: List<String>): String {
        val (pkg, verName, verCode) = extractMetadata(strings)
        val permissions = strings.filter { it.startsWith("android.permission.") }.distinct()
        val activities = strings.filter {
            (it.contains("Activity") || it.contains(".activity.")) &&
                !it.startsWith("android.") && !it.startsWith("androidx.")
        }.distinct()
        val providers = strings.filter {
            (it.contains("Provider") || it.contains(".provider.")) &&
                !it.startsWith("android.")
        }.distinct()
        val services = strings.filter {
            (it.contains("Service") || it.contains(".service.")) &&
                !it.startsWith("android.")
        }.distinct()

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
        sb.append("    package=\"$pkg\"\n")
        sb.append("    android:versionCode=\"$verCode\"\n")
        sb.append("    android:versionName=\"$verName\">\n\n")

        if (permissions.isNotEmpty()) {
            sb.append("    <!-- Permissions (${permissions.size}) -->\n")
            for (p in permissions) {
                sb.append("    <uses-permission android:name=\"$p\" />\n")
            }
            sb.append("\n")
        }

        sb.append("    <application\n")
        sb.append("        android:label=\"@string/app_name\"\n")
        sb.append("        android:allowBackup=\"true\">\n\n")

        if (activities.isNotEmpty()) {
            sb.append("        <!-- Activities (${activities.size}) -->\n")
            for (a in activities) {
                val isPairIp = a.contains("pairip", ignoreCase = true)
                if (isPairIp) {
                    sb.append("        <!-- [PAIRIP LICENSING COMPONENT] -->\n")
                }
                sb.append("        <activity android:name=\"$a\" android:exported=\"true\" />\n")
            }
            sb.append("\n")
        }

        if (providers.isNotEmpty()) {
            sb.append("        <!-- Content Providers (${providers.size}) -->\n")
            for (pr in providers) {
                val isPairIp = pr.contains("pairip", ignoreCase = true)
                if (isPairIp) {
                    sb.append("        <!-- [PAIRIP LICENSING COMPONENT] -->\n")
                }
                sb.append("        <provider android:name=\"$pr\" android:exported=\"false\" />\n")
            }
            sb.append("\n")
        }

        if (services.isNotEmpty()) {
            sb.append("        <!-- Services (${services.size}) -->\n")
            for (s in services) {
                sb.append("        <service android:name=\"$s\" />\n")
            }
            sb.append("\n")
        }

        sb.append("    </application>\n")
        sb.append("</manifest>\n")
        return sb.toString()
    }
}
