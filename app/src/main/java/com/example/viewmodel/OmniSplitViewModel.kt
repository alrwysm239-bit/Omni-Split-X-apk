package com.example.viewmodel

import android.app.Application
import android.content.Intent
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
import com.example.util.ArchiveEntryItem
import com.example.util.ArchiveExtractor
import com.example.util.AxmlParser
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

    private val _showAuthorizedRemovalNotice = MutableStateFlow(false)
    val showAuthorizedRemovalNotice: StateFlow<Boolean> = _showAuthorizedRemovalNotice.asStateFlow()

    private val _currentTab = MutableStateFlow(0) // 0: Home, 1: Files, 2: History, 3: Settings
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _extractedFiles = MutableStateFlow<List<File>>(emptyList())
    val extractedFiles: StateFlow<List<File>> = _extractedFiles.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _isArabic = MutableStateFlow(false)
    val isArabic: StateFlow<Boolean> = _isArabic.asStateFlow()

    // MT Manager style in-app explorer & manifest editor states
    private val _showMtExplorer = MutableStateFlow(false)
    val showMtExplorer: StateFlow<Boolean> = _showMtExplorer.asStateFlow()

    private val _archiveEntries = MutableStateFlow<List<ArchiveEntryItem>>(emptyList())
    val archiveEntries: StateFlow<List<ArchiveEntryItem>> = _archiveEntries.asStateFlow()

    private val _manifestContent = MutableStateFlow<String?>(null)
    val manifestContent: StateFlow<String?> = _manifestContent.asStateFlow()

    private val _manifestSearchQuery = MutableStateFlow("")
    val manifestSearchQuery: StateFlow<String> = _manifestSearchQuery.asStateFlow()

    private var extractionJob: Job? = null

    /**
     * Dedicated folder named "APK Website" inside the Android Downloads directory.
     */
    val outputDirectory: File by lazy {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(publicDownloads, "APK Website")
        if (!dir.exists()) {
            try { dir.mkdirs() } catch (_: Exception) {}
        }
        if (dir.exists() && dir.canWrite()) {
            dir
        } else {
            val appDownloads = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(application.filesDir, "Download")
            val fallback = File(appDownloads, "APK Website")
            if (!fallback.exists()) fallback.mkdirs()
            fallback
        }
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
        val current = _analysis.value
        if (current != null && _selectedUri.value != null) {
            onFileSelected(_selectedUri.value!!, current.fileName, current.fileSize)
        }
    }

    fun showPairIpModal(show: Boolean) {
        _showPairIpDialog.value = show
    }

    fun showAuthorizedRemovalDialog(show: Boolean) {
        _showAuthorizedRemovalNotice.value = show
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun setManifestSearch(query: String) {
        _manifestSearchQuery.value = query
    }

    fun openMtExplorer() {
        val uri = _selectedUri.value
        if (uri == null) {
            _toastMessage.value = "Please select an APK or archive first."
            return
        }
        viewModelScope.launch {
            try {
                val entries = ArchiveExtractor.listArchiveEntries(getApplication(), uri)
                _archiveEntries.value = entries
                _showMtExplorer.value = true

                // Also generate readable manifest if available
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    val strings = ArchiveExtractor.extractManifestStringsFromZip(stream)
                    if (strings.isNotEmpty()) {
                        _manifestContent.value = AxmlParser.generateReadableManifest(strings)
                    }
                }
            } catch (e: Exception) {
                _toastMessage.value = "Error reading archive entries: ${e.localizedMessage}"
            }
        }
    }

    fun closeMtExplorer() {
        _showMtExplorer.value = false
    }

    fun onFileSelected(uri: Uri, name: String, size: Long) {
        _selectedUri.value = uri
        _selectedFileName.value = name
        _selectedFileSize.value = size
        _isAnalyzing.value = true
        _progress.value = null

        // Try to take persistable URI permission for SAF
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

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

                // Pre-populate readable manifest for MT Inspector
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    val strings = ArchiveExtractor.extractManifestStringsFromZip(stream)
                    if (strings.isNotEmpty()) {
                        _manifestContent.value = AxmlParser.generateReadableManifest(strings)
                    }
                }
            } catch (e: Exception) {
                _analysis.value = null
                _toastMessage.value = "Failed to parse archive: ${e.localizedMessage}"
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

        val cleanSubName = name.substringBeforeLast('.')
        val targetDir = File(outputDirectory, cleanSubName)
        if (!targetDir.exists()) targetDir.mkdirs()

        extractionJob?.cancel()
        extractionJob = viewModelScope.launch {
            try {
                _progress.value = ExtractionProgress(
                    percentage = 0.05f,
                    bytesProcessed = 0,
                    totalBytes = size,
                    currentFileName = name,
                    stage = "Initializing extraction to Downloads/APK Website..."
                )

                val files = ArchiveExtractor.extractArchive(
                    context = getApplication(),
                    sourceUri = uri,
                    fileName = name,
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

                _toastMessage.value = "Saved to Downloads/APK Website! Verified ${files.size} APK file(s)."
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
                    _toastMessage.value = "Extraction error: ${e.localizedMessage}"
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
            if (outputDirectory.exists()) {
                outputDirectory.walkTopDown().forEach { file ->
                    if (file.isFile && file.name.endsWith(".apk", ignoreCase = true) && file.length() > 0) {
                        list.add(file)
                    }
                }
            }
            _extractedFiles.value = list.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }
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
     * Loads a valid legitimate sample package with real APK headers.
     */
    fun loadSamplePackage() {
        viewModelScope.launch {
            try {
                val sampleFile = File(getApplication<Application>().cacheDir, "sample_game_bundle.xapk")
                if (!sampleFile.exists()) {
                    ZipOutputStream(FileOutputStream(sampleFile)).use { zip ->
                        // Add base.apk
                        zip.putNextEntry(ZipEntry("base.apk"))
                        // Write valid minimal zip headers for inner APK
                        val baos = java.io.ByteArrayOutputStream()
                        ZipOutputStream(baos).use { innerZip ->
                            innerZip.putNextEntry(ZipEntry("AndroidManifest.xml"))
                            innerZip.write("SAMPLE_ANDROID_MANIFEST_DATA".toByteArray())
                            innerZip.closeEntry()
                        }
                        zip.write(baos.toByteArray())
                        zip.closeEntry()

                        // Add config.arm64_v8a.apk
                        zip.putNextEntry(ZipEntry("config.arm64_v8a.apk"))
                        val baosConfig = java.io.ByteArrayOutputStream()
                        ZipOutputStream(baosConfig).use { innerZip ->
                            innerZip.putNextEntry(ZipEntry("AndroidManifest.xml"))
                            innerZip.write("CONFIG_MANIFEST".toByteArray())
                            innerZip.closeEntry()
                        }
                        zip.write(baosConfig.toByteArray())
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
                _selectedFileSize.value = sampleFile.length()
                _selectedUri.value = Uri.fromFile(sampleFile)

                _analysis.value = PackageAnalysis(
                    fileName = "sample_game_bundle.xapk",
                    fileSize = sampleFile.length(),
                    format = "XAPK",
                    packageName = "com.cyberpunk.game.split",
                    versionName = "v2.4.1 (Build 42)",
                    versionCode = 42,
                    minSdk = 26,
                    targetSdk = 35,
                    architecture = "arm64-v8a",
                    isStandaloneApk = false,
                    baseApkFound = true,
                    splits = listOf(
                        com.example.data.model.ApkSplitInfo("base.apk", SplitType.BASE, 12000L),
                        com.example.data.model.ApkSplitInfo("config.arm64_v8a.apk", SplitType.CONFIG_ARCH, 8500L, architecture = "arm64-v8a")
                    ),
                    obbFiles = emptyList(),
                    pairIpReport = pairIpReport,
                    manifestRawXmlSummary = "Parsed manifest structure"
                )

                _manifestContent.value = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="com.cyberpunk.game.split"
                        android:versionCode="42"
                        android:versionName="2.4.1">

                        <uses-permission android:name="android.permission.INTERNET" />
                        <uses-permission android:name="com.android.vending.CHECK_LICENSE" />

                        <application
                            android:label="Cyberpunk Game"
                            android:allowBackup="true">

                            <activity android:name="com.cyberpunk.game.MainActivity" android:exported="true" />

                            <!-- [PAIRIP LICENSING COMPONENT] -->
                            <activity android:name="com.pairip.licensecheck.LicenseActivity" android:exported="true" />
                            <provider android:name="com.pairip.licensecheck.LicenseContentProvider" android:exported="false" />

                        </application>
                    </manifest>
                """.trimIndent()

                _toastMessage.value = "Loaded demo package with real entries for inspection."
            } catch (e: Exception) {
                _toastMessage.value = "Error loading demo: ${e.localizedMessage}"
            }
        }
    }
}
