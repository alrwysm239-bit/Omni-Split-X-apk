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
}
