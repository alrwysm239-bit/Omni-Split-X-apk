package com.example

import com.example.util.ArchiveExtractor
import com.example.util.AxmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testPairIpDetection() {
        val testManifestStrings = listOf(
            "com.example.game",
            "android.intent.action.MAIN",
            "com.pairip.licensecheck.LicenseActivity",
            "com.pairip.licensecheck.LicenseContentProvider",
            "v1.0.0"
        )

        val report = AxmlParser.auditPairIp(testManifestStrings)

        assertTrue(report.hasPairIp)
        assertEquals(2, report.detectedComponents.size)
        assertTrue(report.detectedComponents.any { it.componentName == "com.pairip.licensecheck.LicenseActivity" })
        assertTrue(report.detectedComponents.any { it.componentName == "com.pairip.licensecheck.LicenseContentProvider" })
    }

    @Test
    fun testCleanManifest() {
        val testManifestStrings = listOf(
            "com.example.calculator",
            "MainActivity",
            "android.permission.INTERNET",
            "v2.1.0"
        )

        val report = AxmlParser.auditPairIp(testManifestStrings)

        assertFalse(report.hasPairIp)
        assertTrue(report.detectedComponents.isEmpty())
    }

    @Test
    fun testMetadataExtraction() {
        val testManifestStrings = listOf(
            "manifest",
            "com.awesome.utility.app",
            "v3.4.1",
            "MainActivity"
        )

        val (pkg, version, code) = AxmlParser.extractMetadata(testManifestStrings)
        assertEquals("com.awesome.utility.app", pkg)
        assertEquals("v3.4.1", version)
        assertEquals(1L, code)
    }

    @Test
    fun testGenerateReadableManifest() {
        val testStrings = listOf(
            "com.example.app",
            "v1.2.3",
            "android.permission.INTERNET",
            "com.pairip.licensecheck.LicenseActivity",
            "com.example.app.MainActivity"
        )

        val xml = AxmlParser.generateReadableManifest(testStrings)
        assertTrue(xml.contains("package=\"com.example.app\""))
        assertTrue(xml.contains("android.permission.INTERNET"))
        assertTrue(xml.contains("com.pairip.licensecheck.LicenseActivity"))
        assertTrue(xml.contains("[PAIRIP LICENSING COMPONENT]"))
    }

    @Test
    fun testCleanPackageName() {
        assertEquals("Balkan Grill Simulator", ArchiveExtractor.cleanPackageName("Balkan Grill Simulator.xapk"))
        assertEquals("Balkan Grill Simulator", ArchiveExtractor.cleanPackageName("Balkan Grill Simulator.apks"))
        assertEquals("app", ArchiveExtractor.cleanPackageName("app.apk.APK"))
        assertEquals("my_game", ArchiveExtractor.cleanPackageName("my_game.zip"))
    }

    @Test
    fun testPairIpRemoval() {
        val originalXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest package="com.game">
                <application>
                    <activity android:name="com.game.MainActivity" />
                    <!-- [PAIRIP LICENSING COMPONENT] -->
                    <activity android:name="com.pairip.licensecheck.LicenseActivity" />
                    <provider android:name="com.pairip.licensecheck.LicenseContentProvider" />
                </application>
            </manifest>
        """.trimIndent()

        val cleanedLines = originalXml.lines().filter { line ->
            !line.contains("pairip", ignoreCase = true) &&
            !line.contains("LicenseActivity", ignoreCase = true) &&
            !line.contains("LicenseContentProvider", ignoreCase = true) &&
            !line.contains("[PAIRIP LICENSING COMPONENT]", ignoreCase = true)
        }
        val cleaned = cleanedLines.joinToString("\n")

        assertFalse(cleaned.contains("pairip"))
        assertFalse(cleaned.contains("LicenseActivity"))
        assertFalse(cleaned.contains("LicenseContentProvider"))
        assertTrue(cleaned.contains("com.game.MainActivity"))
    }

    @Test
    fun testBinaryManifestSanitization() {
        val sample = "android:requiredSplitTypes=\"base__abi\" and android:splitTypes and isSplitRequired and psirip and pairip"
            .toByteArray(Charsets.UTF_8)
        val sanitized = AxmlParser.sanitizeSplitManifest(sample)

        assertEquals(sample.size, sanitized.size)
        val result = String(sanitized, Charsets.UTF_8)
        println("SANITIZED_RESULT: $result")
        assertFalse("Contains requiredSplitTypes", result.contains("requiredSplitTypes"))
        assertFalse("Contains splitTypes", result.contains("splitTypes"))
        assertFalse("Contains isSplitRequired", result.contains("isSplitRequired"))
        assertFalse(result.contains("psirip"))
        assertFalse(result.contains("pairip"))
        assertTrue(result.contains("__req_SplitTypes__"))
        assertTrue(result.contains("__sptTypes"))
        assertTrue(result.contains("isSplitDisabled"))
        assertTrue(result.contains("disrip"))
    }

    @Test
    fun testUtf16ManifestSanitization() {
        val sampleStr = "<manifest isSplitRequired=\"true\" requiredSplitTypes=\"base__abi\" splitTypes=\"density\">"
        val sampleUtf16 = sampleStr.toByteArray(Charsets.UTF_16LE)
        val sanitizedUtf16 = AxmlParser.sanitizeSplitManifest(sampleUtf16)

        assertEquals(sampleUtf16.size, sanitizedUtf16.size)
        val resultStr = String(sanitizedUtf16, Charsets.UTF_16LE)
        assertFalse(resultStr.contains("isSplitRequired"))
        assertFalse(resultStr.contains("requiredSplitTypes"))
        assertFalse(resultStr.contains("splitTypes"))
        assertTrue(resultStr.contains("isSplitDisabled"))
        assertTrue(resultStr.contains("__req_SplitTypes__"))
        assertTrue(resultStr.contains("__sptTypes"))
    }

    @Test
    fun testPackageSanitizerAssets() {
        assertTrue(com.example.util.PackageSanitizer.shouldKeepAsset("assets/game_data.bin", 1024L))
        assertFalse(com.example.util.PackageSanitizer.shouldKeepAsset("assets/temp/temp.dat", 500L))
        assertFalse(com.example.util.PackageSanitizer.shouldKeepAsset("assets/.cache/cache.tmp", 200L))
        assertFalse(com.example.util.PackageSanitizer.shouldKeepAsset("assets/.DS_Store", 100L))
    }

    @Test
    fun testPackageSanitizerNativeLibs() {
        val validElfHeader = byteArrayOf(0x7F.toByte(), 0x45.toByte(), 0x4C.toByte(), 0x46.toByte())
        val corruptHeader = byteArrayOf(0x00, 0x00, 0x00, 0x00)

        assertTrue(com.example.util.PackageSanitizer.isValidNativeLibrary("lib/arm64-v8a/libtest.so", validElfHeader, 2048L))
        assertFalse(com.example.util.PackageSanitizer.isValidNativeLibrary("lib/arm64-v8a/libtest.so", corruptHeader, 2048L))
        assertFalse(com.example.util.PackageSanitizer.isValidNativeLibrary("lib/unknown_arch/libtest.so", validElfHeader, 2048L))
    }

    @Test
    fun testApplicationClassResetInManifestCleaner() {
        val sample = "com.google.android.play.core.splitcompat.SplitCompatApplication".toByteArray(Charsets.UTF_8)
        val cleaned = com.example.util.ManifestCleaner.cleanManifest(sample)
        val result = String(cleaned, Charsets.UTF_8)
        assertTrue(result.startsWith("android.app.Application"))
    }
}
