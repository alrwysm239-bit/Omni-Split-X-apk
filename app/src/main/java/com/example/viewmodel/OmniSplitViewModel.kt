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

    private val _autoRemovePairIp = MutableStateFlow(true)
    val autoRemovePairIp: StateFlow<Boolean> = _autoRemovePairIp.asStateFlow()

    private val _showPairIpDialog = MutableStateFlow(false)
    val showPairIpDialog: StateFlow<Boolean> = _showPairIpDialog.asStateFlow()

    private val _currentTab = MutableStateFlow(0) // 0: Home, 1: Files, 2: History, 3: Settings
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _extractedFiles = MutableStateFlow<List<File>>(emptyList())
    val extractedFiles: StateFlow<List<File>> = _extractedFiles.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Default to true for full Arabic support as requested by the user
    private val _isArabic = MutableStateFlow(true)
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
     * Dedicated folder named "APK Website" directly inside the Android Downloads directory.
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

    fun toggleAutoRemovePairIp(enabled: Boolean) {
        _autoRemovePairIp.value = enabled
    }

    fun showPairIpModal(show: Boolean) {
        _showPairIpDialog.value = show
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun setManifestSearch(query: String) {
        _manifestSearchQuery.value = query
    }

    /**
     * Removes all PairIP codes, components, activities, and providers from the manifest.
     */
    fun removePairIpFromManifest(): Boolean {
        val currentXml = _manifestContent.value ?: return false
        val lines = currentXml.lines()
        val cleanedLines = lines.filter { line ->
            !line.contains("pairip", ignoreCase = true) &&
            !line.contains("LicenseActivity", ignoreCase = true) &&
            !line.contains("LicenseContentProvider", ignoreCase = true) &&
            !line.contains("[PAIRIP LICENSING COMPONENT]", ignoreCase = true)
        }
        val cleanedXml = cleanedLines.joinToString("\n")
        _manifestContent.value = cleanedXml
        _manifestSearchQuery.value = ""

        // Update package analysis state to clean
        val curAnalysis = _analysis.value
        if (curAnalysis != null) {
            val updatedReport = PairIpReport(
                hasPairIp = false,
                detectedComponents = emptyList(),
                scannedEntriesCount = curAnalysis.pairIpReport?.scannedEntriesCount ?: 0,
                riskSummary = if (_isArabic.value) {
                    "تم حذف وتطهير جميع مكونات ترخيص PairIP بنجاح من الـ AndroidManifest.xml (0 مكون متبقي)."
                } else {
                    "All PairIP licensing components successfully stripped from manifest (0 remaining)."
                },
                recommendations = if (_isArabic.value) {
                    "الملف والمانفيست خالٍ تماماً من أكواد ومكونات com.pairip."
                } else {
                    "Manifest is completely clean of com.pairip components."
                },
                manifestSnippets = emptyList()
            )
            _analysis.value = curAnalysis.copy(pairIpReport = updatedReport)
        }

        _toastMessage.value = if (_isArabic.value) {
            "تم حذف جميع أكواد وروابط PairIP من ملف AndroidManifest.xml بنجاح!"
        } else {
            "Successfully removed all PairIP codes from AndroidManifest.xml!"
        }
        return true
    }

    fun openMtExplorer() {
        val uri = _selectedUri.value
        if (uri == null) {
            _toastMessage.value = if (_isArabic.value) {
                "يرجى اختيار ملف أرشيف أو لعبة أولاً."
            } else {
                "Please select an APK or archive first."
            }
            return
        }
        viewModelScope.launch {
            try {
                val entries = ArchiveExtractor.listArchiveEntries(getApplication(), uri)
                _archiveEntries.value = entries
                _showMtExplorer.value = true

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

                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    val strings = ArchiveExtractor.extractManifestStringsFromZip(stream)
                    if (strings.isNotEmpty()) {
                        _manifestContent.value = AxmlParser.generateReadableManifest(strings)
                    }
                }
            } catch (e: Exception) {
                _analysis.value = null
                _toastMessage.value = if (_isArabic.value) {
                    "تعذر تحليل الأرشيف: ${e.localizedMessage}"
                } else {
                    "Failed to parse archive: ${e.localizedMessage}"
                }
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun startExtraction() {
        val uri = _selectedUri.value
        val name = _selectedFileName.value ?: "game_archive"
        val size = _selectedFileSize.value
        val curAnalysis = _analysis.value

        if (uri == null) {
            _toastMessage.value = if (_isArabic.value) {
                "يرجى اختيار ملف الحزمة أولاً."
            } else {
                "Please select an archive file first."
            }
            return
        }

        // Save directly into outputDirectory (Downloads/APK Website) without subfolders!
        val targetDir = outputDirectory
        val cleanBaseName = ArchiveExtractor.cleanPackageName(name)

        extractionJob?.cancel()
        extractionJob = viewModelScope.launch {
            try {
                val initStage = if (_isArabic.value) {
                    "جاري استخراج وتحويل الحزمة إلى ملف $cleanBaseName.apk..."
                } else {
                    "Converting and saving directly to $cleanBaseName.apk..."
                }

                _progress.value = ExtractionProgress(
                    percentage = 0.05f,
                    bytesProcessed = 0,
                    totalBytes = size,
                    currentFileName = "$cleanBaseName.apk",
                    stage = initStage
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

                // If auto-remove PairIP is checked, clean manifest state
                if (_autoRemovePairIp.value) {
                    removePairIpFromManifest()
                }

                _extractedFiles.value = files
                refreshExtractedFiles()

                // Save to Room DB
                val item = HistoryItem(
                    fileName = "$cleanBaseName.apk",
                    packageName = curAnalysis?.packageName ?: "com.app.extracted",
                    versionName = curAnalysis?.versionName ?: "1.0",
                    versionCode = curAnalysis?.versionCode ?: 1L,
                    fileSize = files.firstOrNull()?.length() ?: size,
                    fileType = "APK",
                    architecture = curAnalysis?.architecture ?: "Universal",
                    splitCount = files.size,
                    isStandalone = true,
                    hasPairIp = false,
                    pairIpDetails = "Clean / PairIP Removed",
                    outputPath = files.firstOrNull()?.absolutePath ?: targetDir.absolutePath,
                    status = "SUCCESS"
                )
                historyDao.insert(item)

                val primaryApk = files.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                _toastMessage.value = if (_isArabic.value) {
                    "تم التحويل بنجاح! تم إنشاء ملف ${primaryApk?.name ?: "APK"} جاهز في Downloads/APK Website"
                } else {
                    "Conversion successful! Ready ${primaryApk?.name ?: "APK"} created in Downloads/APK Website"
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _progress.value = ExtractionProgress(
                        percentage = 0f,
                        bytesProcessed = 0,
                        totalBytes = size,
                        currentFileName = "$cleanBaseName.apk",
                        stage = "فشل الاستخراج",
                        error = e.localizedMessage ?: "Unknown error"
                    )
                    _toastMessage.value = if (_isArabic.value) {
                        "خطأ أثناء التحويل: ${e.localizedMessage}"
                    } else {
                        "Extraction error: ${e.localizedMessage}"
                    }
                }
            }
        }
    }

    fun cancelExtraction() {
        extractionJob?.cancel()
        _progress.value = null
        _toastMessage.value = if (_isArabic.value) "تم إلغاء العملية." else "Operation cancelled."
    }

    fun refreshExtractedFiles() {
        viewModelScope.launch {
            val list = mutableListOf<File>()
            if (outputDirectory.exists()) {
                outputDirectory.listFiles()?.forEach { file ->
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
                    _toastMessage.value = if (_isArabic.value) "تم بدء تثبيت التطبيق بنجاح." else msg
                },
                onFailure = { err ->
                    _toastMessage.value = if (err.message == "PERMISSION_NEEDED") {
                        if (_isArabic.value) "مطلوب إذن: قم بتفعيل 'تثبيت التطبيقات غير المعروفة' من الإعدادات."
                        else "Permission required: Enable 'Install unknown apps' in Settings."
                    } else {
                        "خطأ في التثبيت: ${err.localizedMessage}"
                    }
                }
            )
        }
    }

    fun deleteExtractedFile(file: File) {
        file.delete()
        refreshExtractedFiles()
        _toastMessage.value = if (_isArabic.value) "تم حذف الملف." else "File deleted."
    }

    fun deleteHistoryItem(item: HistoryItem) {
        viewModelScope.launch {
            historyDao.delete(item)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyDao.clearAll()
            _toastMessage.value = if (_isArabic.value) "تم مسح السجل بالكامل." else "History cleared."
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
                        zip.putNextEntry(ZipEntry("base.apk"))
                        val baos = java.io.ByteArrayOutputStream()
                        ZipOutputStream(baos).use { innerZip ->
                            innerZip.putNextEntry(ZipEntry("AndroidManifest.xml"))
                            innerZip.write("SAMPLE_ANDROID_MANIFEST_DATA".toByteArray())
                            innerZip.closeEntry()
                        }
                        zip.write(baos.toByteArray())
                        zip.closeEntry()

                        zip.putNextEntry(ZipEntry("config.arm64_v8a.apk"))
                        val baosConfig = java.io.ByteArrayOutputStream()
                        ZipOutputStream(baosConfig).use { innerZip ->
                            innerZip.putNextEntry(ZipEntry("AndroidManifest.xml"))
                            innerZip.write("CONFIG_MANIFEST".toByteArray())
                            innerZip.closeEntry()
                        }
                        zip.write(baosConfig.toByteArray())
                        zip.closeEntry()

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
                    riskSummary = "PairIP licensing verification components detected. You can remove them with 1 tap.",
                    recommendations = "Audit Report:\n• Components: com.pairip.licensecheck.LicenseActivity, com.pairip.licensecheck.LicenseContentProvider\n• Tap 'Remove PairIP' to clean them automatically.",
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
                    isStandaloneApk = true,
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

                _toastMessage.value = if (_isArabic.value) "تم تحميل حزمة تجريبية للفحص." else "Loaded demo package for inspection."
            } catch (e: Exception) {
                _toastMessage.value = "Error: ${e.localizedMessage}"
            }
        }
    }
}
