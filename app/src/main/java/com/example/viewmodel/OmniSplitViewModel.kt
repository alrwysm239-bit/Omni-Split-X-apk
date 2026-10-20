package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.HistoryItem
import com.example.data.model.PackageAnalysis
import com.example.data.model.PairIpComponent
import com.example.data.model.PairIpReport
import com.example.data.model.SplitType
import com.example.util.ArchiveExtractor
import com.example.util.ExtractionProgress
import com.example.util.PackageInstallerHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OmniSplitViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val historyDao = db.historyDao()

    val historyList: StateFlow<List<HistoryItem>> = historyDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _selectedFileName = MutableStateFlow<String?>(null)
    val selectedFileName: StateFlow<String?> = _selectedFileName.asStateFlow()

    private val _selectedFileSize = MutableStateFlow<Long>(0L)
    val selectedFileSize: StateFlow<Long> = _selectedFileSize.asStateFlow()

    private val _analysis = MutableStateFlow<PackageAnalysis?>(null)
    val analysis: StateFlow<PackageAnalysis?> = _analysis.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _progress = MutableStateFlow<ExtractionProgress?>(null)
    val progress: StateFlow<ExtractionProgress?> = _progress.asStateFlow()

    private val _scanPairIp = MutableStateFlow(true)
    val scanPairIp: StateFlow<Boolean> = _scanPairIp.asStateFlow()

    private val _showPairIpDialog = MutableStateFlow(false)
    val showPairIpDialog: StateFlow<Boolean> = _showPairIpDialog.asStateFlow()

    private val _currentTab = MutableStateFlow(0) // 0: Home, 1: Files, 2: History, 3: Settings
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _extractedFiles = MutableStateFlow<List<File>>(emptyList())
    val extractedFiles: StateFlow<List<File>> = _extractedFiles.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _isArabic = MutableStateFlow(false)
    val isArabic: StateFlow<Boolean> = _isArabic.asStateFlow()

    private var extractionJob: Job? = null

    val outputDirectory: File by lazy {
        val base = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(application.filesDir, "OmniSplitX")
        val dir = File(base, "Extracted")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    init {
        refreshExtractedFiles()
    }

    fun setTab(index: Int) {
        _currentTab.value = index
    }

    fun toggleArabic() {
        _isArabic.value = !_isArabic.value
    }

    fun togglePairIpScan(enabled: Boolean) {
        _scanPairIp.value = enabled
        // Re-evaluate current analysis if already loaded
        val current = _analysis.value
        if (current != null && _selectedUri.value != null) {
            onFileSelected(_selectedUri.value!!, current.fileName, current.fileSize)
        }
    }

    fun showPairIpModal(show: Boolean) {
        _showPairIpDialog.value = show
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun onFileSelected(uri: Uri, name: String, size: Long) {
        _selectedUri.value = uri
        _selectedFileName.value = name
        _selectedFileSize.value = size
        _isAnalyzing.value = true
        _progress.value = null

        viewModelScope.launch {
            try {
                val result = ArchiveExtractor.analyzeArchive(
                    context = getApplication(),
                    uri = uri,
                    fileName = name,
                    fileSize = size,
                    scanPairIp = _scanPairIp.value
                )
                _analysis.value = result
            } catch (e: Exception) {
                // Fallback analysis if format parsing failed
                _analysis.value = PackageAnalysis(
                    fileName = name,
                    fileSize = size,
                    format = name.substringAfterLast('.', "APK").uppercase(),
                    packageName = "com.package.inspected",
                    versionName = "1.0.0",
                    versionCode = 1,
                    minSdk = 24,
                    targetSdk = 35,
                    architecture = "arm64-v8a / armeabi-v7a",
                    isStandaloneApk = name.endsWith(".apk", ignoreCase = true),
                    baseApkFound = true,
                    splits = emptyList(),
                    obbFiles = emptyList(),
                    pairIpReport = PairIpReport(
                        hasPairIp = false,
                        detectedComponents = emptyList(),
                        scannedEntriesCount = 0,
                        riskSummary = "Could not parse archive manifest.",
                        recommendations = "Verify archive integrity.",
                        manifestSnippets = emptyList()
                    )
                )
                _toastMessage.value = "Analysis error: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun startExtraction() {
        val uri = _selectedUri.value
        val name = _selectedFileName.value ?: "archive"
        val size = _selectedFileSize.value
        val curAnalysis = _analysis.value

        if (uri == null) {
            _toastMessage.value = "Please select an archive file first."
            return
        }

        val targetDir = File(outputDirectory, name.substringBeforeLast('.'))
        if (!targetDir.exists()) targetDir.mkdirs()

        extractionJob?.cancel()
        extractionJob = viewModelScope.launch {
            try {
                _progress.value = ExtractionProgress(
                    percentage = 0.05f,
                    bytesProcessed = 0,
                    totalBytes = size,
                    currentFileName = name,
                    stage = "Preparing extraction target..."
                )

                val files = ArchiveExtractor.extractArchive(
                    context = getApplication(),
                    sourceUri = uri,
                    totalBytes = size,
                    destinationDir = targetDir,
                    onProgress = { p ->
                        _progress.value = p
                    }
                )

                _extractedFiles.value = files
                refreshExtractedFiles()

                // Save to Room DB
                val item = HistoryItem(
                    fileName = name,
                    packageName = curAnalysis?.packageName ?: "com.app.extracted",
                    versionName = curAnalysis?.versionName ?: "1.0",
                    versionCode = curAnalysis?.versionCode ?: 1L,
                    fileSize = size,
                    fileType = curAnalysis?.format ?: "XAPK",
                    architecture = curAnalysis?.architecture ?: "Universal",
                    splitCount = files.count { it.name.endsWith(".apk") },
                    isStandalone = curAnalysis?.isStandaloneApk ?: false,
                    hasPairIp = curAnalysis?.pairIpReport?.hasPairIp ?: false,
                    pairIpDetails = curAnalysis?.pairIpReport?.detectedComponents?.joinToString { it.componentName } ?: "Clean",
                    outputPath = targetDir.absolutePath,
                    status = "SUCCESS"
                )
                historyDao.insert(item)

                _toastMessage.value = "Extraction complete! ${files.size} files ready."
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _progress.value = ExtractionProgress(
                        percentage = 0f,
                        bytesProcessed = 0,
                        totalBytes = size,
                        currentFileName = name,
                        stage = "Extraction Failed",
                        error = e.localizedMessage ?: "Unknown error"
                    )
                    _toastMessage.value = "Error: ${e.localizedMessage}"
                }
            }
        }
    }

    fun cancelExtraction() {
        extractionJob?.cancel()
        _progress.value = null
        _toastMessage.value = "Operation cancelled."
    }

    fun refreshExtractedFiles() {
        viewModelScope.launch {
            val list = mutableListOf<File>()
            outputDirectory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    file.listFiles()?.forEach { child ->
                        if (child.name.endsWith(".apk", ignoreCase = true)) {
                            list.add(child)
                        }
                    }
                } else if (file.name.endsWith(".apk", ignoreCase = true)) {
                    list.add(file)
                }
            }
            _extractedFiles.value = list.sortedByDescending { it.lastModified() }
        }
    }

    fun installExtractedApks(files: List<File>) {
        viewModelScope.launch {
            val result = PackageInstallerHelper.installSplitApks(getApplication(), files)
            result.fold(
                onSuccess = { msg ->
                    _toastMessage.value = msg
                },
                onFailure = { err ->
                    _toastMessage.value = if (err.message == "PERMISSION_NEEDED") {
                        "Permission required: Enable 'Install unknown apps' in Settings."
                    } else {
                        "Install error: ${err.localizedMessage}"
                    }
                }
            )
        }
    }

    fun deleteExtractedFile(file: File) {
        file.delete()
        refreshExtractedFiles()
        _toastMessage.value = "File deleted."
    }

    fun deleteHistoryItem(item: HistoryItem) {
        viewModelScope.launch {
            historyDao.delete(item)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyDao.clearAll()
            _toastMessage.value = "History cleared."
        }
    }

    /**
     * Loads a built-in mock/demo package so the user can test the app immediately in the emulator.
     */
    fun loadSamplePackage() {
        viewModelScope.launch {
            try {
                val sampleFile = File(getApplication<Application>().cacheDir, "sample_game_bundle.xapk")
                if (!sampleFile.exists()) {
                    ZipOutputStream(FileOutputStream(sampleFile)).use { zip ->
                        // Add base.apk
                        zip.putNextEntry(ZipEntry("base.apk"))
                        zip.write("PK_DEMO_BASE_APK_BYTES".toByteArray())
                        zip.closeEntry()

                        // Add config.arm64_v8a.apk
                        zip.putNextEntry(ZipEntry("config.arm64_v8a.apk"))
                        zip.write("PK_DEMO_CONFIG_ARM64_BYTES".toByteArray())
                        zip.closeEntry()

                        // Add config.xxhdpi.apk
                        zip.putNextEntry(ZipEntry("config.xxhdpi.apk"))
                        zip.write("PK_DEMO_CONFIG_XXHDPI_BYTES".toByteArray())
                        zip.closeEntry()

                        // Add manifest.json
                        zip.putNextEntry(ZipEntry("manifest.json"))
                        zip.write("{\"package_name\":\"com.cyberpunk.game.split\",\"version_name\":\"2.4.1\",\"version_code\":42}".toByteArray())
                        zip.closeEntry()
                    }
                }

                val pairIpReport = PairIpReport(
                    hasPairIp = true,
                    detectedComponents = listOf(
                        PairIpComponent(
                            componentName = "com.pairip.licensecheck.LicenseActivity",
                            type = "Activity",
                            details = "PairIP licensing integrity verification activity."
                        ),
                        PairIpComponent(
                            componentName = "com.pairip.licensecheck.LicenseContentProvider",
                            type = "ContentProvider",
                            details = "PairIP runtime license initialization provider."
                        )
                    ),
                    scannedEntriesCount = 4,
                    riskSummary = "PairIP licensing verification components detected. These components protect application distribution through Google Play.",
                    recommendations = "Audit Report:\n• Components: com.pairip.licensecheck.LicenseActivity, com.pairip.licensecheck.LicenseContentProvider\n• Status: Licensed Google Play build. Bypassing or removing DRM code is not performed per policy.",
                    manifestSnippets = listOf(
                        "com.pairip.licensecheck.LicenseActivity",
                        "com.pairip.licensecheck.LicenseContentProvider"
                    )
                )

                _selectedFileName.value = "sample_game_bundle.xapk"
                _selectedFileSize.value = 184549200L // 176 MB
                _selectedUri.value = Uri.fromFile(sampleFile)

                _analysis.value = PackageAnalysis(
                    fileName = "sample_game_bundle.xapk",
                    fileSize = 184549200L,
                    format = "XAPK",
                    packageName = "com.cyberpunk.game.split",
                    versionName = "v2.4.1 (Build 42)",
                    versionCode = 42,
                    minSdk = 26,
                    targetSdk = 35,
                    architecture = "arm64-v8a / armeabi-v7a",
                    isStandaloneApk = false,
                    baseApkFound = true,
                    splits = listOf(
                        com.example.data.model.ApkSplitInfo("base.apk", SplitType.BASE, 84500000L),
                        com.example.data.model.ApkSplitInfo("config.arm64_v8a.apk", SplitType.CONFIG_ARCH, 52100000L, architecture = "arm64-v8a"),
                        com.example.data.model.ApkSplitInfo("config.xxhdpi.apk", SplitType.CONFIG_DENSITY, 47949200L, density = "xxhdpi")
                    ),
                    obbFiles = emptyList(),
                    pairIpReport = pairIpReport,
                    manifestRawXmlSummary = "Found 34 binary XML string entries"
                )

                _toastMessage.value = "Loaded demo package for inspection."
            } catch (e: Exception) {
                _toastMessage.value = "Error loading demo: ${e.localizedMessage}"
            }
        }
    }
}
