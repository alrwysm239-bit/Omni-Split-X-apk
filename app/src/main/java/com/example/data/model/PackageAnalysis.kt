package com.example.data.model

import java.io.File

data class ApkSplitInfo(
    val name: String,
    val splitType: SplitType,
    val size: Long,
    val architecture: String? = null,
    val density: String? = null,
    val language: String? = null,
    val file: File? = null
)

enum class SplitType {
    BASE,
    STANDALONE,
    CONFIG_ARCH,
    CONFIG_DENSITY,
    CONFIG_LANGUAGE,
    FEATURE,
    OBB,
    UNKNOWN
}

data class PairIpComponent(
    val componentName: String,
    val type: String, // Activity, ContentProvider, Service, etc.
    val details: String
)

data class PairIpReport(
    val hasPairIp: Boolean,
    val detectedComponents: List<PairIpComponent>,
    val scannedEntriesCount: Int,
    val riskSummary: String,
    val recommendations: String,
    val manifestSnippets: List<String>
)

data class PackageAnalysis(
    val fileName: String,
    val fileSize: Long,
    val format: String, // XAPK, APKS, APK, ZIP
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val architecture: String,
    val isStandaloneApk: Boolean,
    val baseApkFound: Boolean,
    val splits: List<ApkSplitInfo>,
    val obbFiles: List<String>,
    val pairIpReport: PairIpReport? = null,
    val manifestRawXmlSummary: String? = null
)
