package com.example

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
}
