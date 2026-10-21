package com.example.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AndroidManifest Cleaner & Refactor Engine.
 *
 * Provides automated sanitization for Android binary XML (AXML) manifests
 * during Split APK merging:
 * 1. Removes all split attributes: android:requiredSplitTypes, android:splitTypes,
 *    android:isSplitRequired, android:isolatedSplits, android:isFeatureSplit,
 *    android:configForSplit, and android:splitName.
 * 2. Neutralizes Google Play Store split distribution metadata (<meta-data>)
 *    such as com.android.vending.splits and distribution stamps.
 * 3. Inspects <application> and resets split-dependent or conflicting application
 *    classes to the Android standard: android.app.Application.
 * 4. Preserves binary chunk alignment and offsets across UTF-8 and UTF-16LE pools.
 */
object ManifestCleaner {

    private const val RES_XML_TYPE = 0x00080003
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x00080180

    // Standard Android framework attribute resource IDs for Split APK attributes:
    private val TARGET_SPLIT_RES_IDS = intArrayOf(
        0x0101064d, // requiredSplitTypes
        0x0101064e, // splitTypes
        0x01010591, // isSplitRequired
        0x01010565, // isolatedSplits
        0x0101055b, // isFeatureSplit
        0x010104cc, // configForSplit
        0x01010526, // splitName
        0x010104ea  // extractNativeLibs (ensure extraction is permitted)
    )

    /**
     * Configuration options for the Manifest Cleaner.
     */
    data class CleanOptions(
        val removeSplitAttributes: Boolean = true,
        val removeDistributionMetadata: Boolean = true,
        val resetApplicationClass: Boolean = true,
        val sanitizeComponents: Boolean = true
    )

    /**
     * Cleans and refactors a binary AndroidManifest.xml byte array.
     *
     * @param manifestBytes Raw bytes of AndroidManifest.xml from the base APK.
     * @param options Cleaning configuration.
     * @return Cleaned byte array safe for monolithic standalone installation.
     */
    fun cleanManifest(
        manifestBytes: ByteArray,
        options: CleanOptions = CleanOptions()
    ): ByteArray {
        if (manifestBytes.size < 16) return manifestBytes
        val result = manifestBytes.copyOf()

        // 1. Wipe split-related resource IDs in the ResTable_map chunk
        if (options.removeSplitAttributes) {
            wipeSplitResourceIds(result)
        }

        // 2. Prepare precise length-preserving string replacements
        val replacements = mutableListOf<Pair<String, String>>()

        if (options.removeSplitAttributes) {
            // Split attribute declarations and composite values
            replacements.add("requiredSplitTypes" to "__req_SplitTypes__")
            replacements.add("isSplitRequired"    to "isSplitDisabled")
            replacements.add("isFeatureSplit"     to "__isFeatSplit_")
            replacements.add("configForSplit"     to "__cfgForSplit_")
            replacements.add("isolatedSplits"     to "__isolated_spt")
            replacements.add("splitTypes"         to "__sptTypes")
            replacements.add("splitName"          to "__sptName")
            replacements.add("base__abi,base__density" to "base_uni,base_density_")
            replacements.add("base__density"      to "base_density_")
            replacements.add("base__abi"          to "base_uni_")
            replacements.add("split"              to "__spt")
        }

        if (options.removeDistributionMetadata) {
            // Play Store split requirements and vending metadata
            replacements.add("com.android.vending.splits.required" to "com.android.vending.splits.merged__")
            replacements.add("com.android.vending.derived.apk.id"  to "com.android.disabled.apk.id_______")
            replacements.add("com.android.vending.splits"          to "com.android.vending.merged")
            replacements.add("com.android.stamp.source"            to "com.android.stmp.source_")
            replacements.add("com.android.stamp.type"              to "com.android.stmp.type_")
        }

        if (options.resetApplicationClass) {
            // Reset known split-dependent Application classes to android.app.Application
            val splitCompat = "com.google.android.play.core.splitcompat.SplitCompatApplication"
            val missingSplits = "com.google.android.play.core.missingsplits.MissingSplitsDetectingApplication"
            val pairIpApp = "com.pairip.application.Application"

            replacements.add(splitCompat to "android.app.Application".padEnd(splitCompat.length, '_'))
            replacements.add(missingSplits to "android.app.Application".padEnd(missingSplits.length, '_'))
            replacements.add(pairIpApp to "android.app.Application".padEnd(pairIpApp.length, '_'))
        }

        if (options.sanitizeComponents) {
            // Neutralize licensing / verification activities and providers
            val pairIpActivity = "com.pairip.licensecheck.LicenseActivity"
            val pairIpProvider = "com.pairip.licensecheck.LicenseContentProvider"
            val pairIpCheck = "com.pairip.licensecheck"
            val pairIpBase = "com.pairip"

            replacements.add(pairIpActivity to "android.app.Activity".padEnd(pairIpActivity.length, '_'))
            replacements.add(pairIpProvider to "com.disabled.provider".padEnd(pairIpProvider.length, '_'))
            replacements.add(pairIpCheck to "com.disabled.check".padEnd(pairIpCheck.length, '_'))
            replacements.add(pairIpBase to "com.disip".padEnd(pairIpBase.length, '_'))
            replacements.add("pairip" to "disrip")
            replacements.add("psirip" to "disrip")
        }

        // 3. Execute in-place string replacements for UTF-8 and UTF-16LE pools
        for ((target, replacement) in replacements) {
            // UTF-8 sequence replacement
            val targetUtf8 = target.toByteArray(Charsets.UTF_8)
            val replaceUtf8 = replacement.toByteArray(Charsets.UTF_8)
            replaceSequence(result, targetUtf8, replaceUtf8)

            // UTF-16LE sequence replacement
            val targetUtf16 = target.toByteArray(Charsets.UTF_16LE)
            val replaceUtf16 = replacement.toByteArray(Charsets.UTF_16LE)
            replaceSequence(result, targetUtf16, replaceUtf16)
        }

        return result
    }

    /**
     * Wipes split-related attribute resource IDs from the AXML Resource Map chunk,
     * causing Android's PackageParser to ignore these attributes completely.
     */
    private fun wipeSplitResourceIds(bytes: ByteArray) {
        if (bytes.size < 16) return
        try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val headerType = buffer.int
            val fileSize = buffer.int
            if (headerType != RES_XML_TYPE && headerType != 0x00080000) return

            while (buffer.hasRemaining() && buffer.remaining() >= 8) {
                val chunkType = buffer.int
                val chunkSize = buffer.int
                if (chunkSize <= 8) break

                if (chunkType == RES_XML_RESOURCE_MAP_TYPE) {
                    val count = (chunkSize - 8) / 4
                    for (i in 0 until count) {
                        if (buffer.remaining() >= 4) {
                            val pos = buffer.position()
                            val resId = buffer.int
                            if (resId in TARGET_SPLIT_RES_IDS) {
                                buffer.putInt(pos, 0x00000000)
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
        } catch (_: Exception) {}
    }

    /**
     * Performs exact-length byte array search and replace to avoid altering
     * string pool offsets, chunk sizes, or binary structures.
     */
    private fun replaceSequence(bytes: ByteArray, target: ByteArray, replacement: ByteArray) {
        if (target.isEmpty() || target.size != replacement.size || bytes.size < target.size) return
        var i = 0
        while (i <= bytes.size - target.size) {
            var match = true
            for (j in target.indices) {
                if (bytes[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                System.arraycopy(replacement, 0, bytes, i, replacement.size)
                i += target.size
            } else {
                i++
            }
        }
    }
}
