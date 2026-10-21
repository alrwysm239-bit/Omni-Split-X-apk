package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppCyberHeader
import com.example.ui.components.CyberPrimaryGradientButton
import com.example.ui.components.CyberSecondaryButton
import com.example.ui.components.ExtractionProgressCard
import com.example.ui.components.MtManagerExplorerDialog
import com.example.ui.components.OutputLocationCard
import com.example.ui.components.PackageInfoCard
import com.example.ui.components.PairIpInspectorCard
import com.example.ui.components.PairIpReportDialog
import com.example.ui.components.SelectFileDashedCard
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.OmniSplitViewModel
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: OmniSplitViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val isArabic by viewModel.isArabic.collectAsState()
    val analysis by viewModel.analysis.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val scanPairIp by viewModel.scanPairIp.collectAsState()
    val autoRemovePairIp by viewModel.autoRemovePairIp.collectAsState()
    val autoCleanAndSign by viewModel.autoCleanAndSign.collectAsState()
    val conflictingPackageInstalled by viewModel.conflictingPackageInstalled.collectAsState()
    val showPairIpDialog by viewModel.showPairIpDialog.collectAsState()
    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val selectedFileSize by viewModel.selectedFileSize.collectAsState()

    val showMtExplorer by viewModel.showMtExplorer.collectAsState()
    val archiveEntries by viewModel.archiveEntries.collectAsState()
    val manifestContent by viewModel.manifestContent.collectAsState()
    val manifestSearchQuery by viewModel.manifestSearchQuery.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var name = "archive.xapk"
            var size = 0L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) name = cursor.getString(nameIndex)
                        if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                    }
                }
            } catch (_: Exception) {}
            viewModel.onFileSelected(uri, name, size)
        }
    }

    if (showPairIpDialog) {
        PairIpReportDialog(
            report = analysis?.pairIpReport,
            onDismiss = { viewModel.showPairIpModal(false) }
        )
    }

    if (showMtExplorer) {
        MtManagerExplorerDialog(
            entries = archiveEntries,
            manifestContent = manifestContent,
            searchQuery = manifestSearchQuery,
            isArabic = isArabic,
            onSearchChange = { viewModel.setManifestSearch(it) },
            onRemovePairIp = { viewModel.removePairIpFromManifest() },
            onDismiss = { viewModel.closeMtExplorer() }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Header
        AppCyberHeader()

        // Card 1: Dotted Dashed Neon Upload Card
        val fileSizeFormatted = if (selectedFileSize > 0) {
            String.format(Locale.US, "%.2f MB", selectedFileSize / (1024.0 * 1024.0))
        } else null

        SelectFileDashedCard(
            fileName = selectedFileName,
            fileSizeStr = fileSizeFormatted?.let {
                if (isArabic) "الحجم: $it / حتى 4 جيجابايت" else "Size: $it / Up to 4 GB"
            },
            onClick = {
                filePicker.launch(arrayOf("*/*"))
            }
        )

        // Quick actions: Installed Apps & Sample Demo
        if (analysis == null && !isAnalyzing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                OutlinedButton(
                    onClick = { viewModel.setTab(1) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isArabic) "تطبيقات الجهاز والنظام" else "Installed Apps",
                        color = NeonGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.loadSamplePackage() },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isArabic) "حزمة تجريبية" else "Load Demo",
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Analyzing indicator
        AnimatedVisibility(visible = isAnalyzing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                CircularProgressIndicator(
                    color = NeonCyan,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isArabic) "جاري فحص وتدفق هيكل الأرشيف..." else "Streaming and parsing archive structure...",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        // Card 2: Package Info Card
        val pkgNameDisplay = analysis?.packageName ?: if (isArabic) "com.example.app (في انتظار الاختيار...)" else "com.example.app (Pending...)"
        val archDisplay = analysis?.architecture ?: "arm64-v8a / armeabi-v7a"
        val versionDisplay = analysis?.versionName ?: "v1.0.0 (Build 42)"
        val actualSizeDisplay = if (selectedFileSize > 0) {
            String.format(Locale.US, "%.2f MB / Up to 4 GB", selectedFileSize / (1024.0 * 1024.0))
        } else {
            "0.00 MB / Up to 4 GB"
        }

        PackageInfoCard(
            packageName = pkgNameDisplay,
            architecture = archDisplay,
            version = versionDisplay,
            actualFileSize = actualSizeDisplay
        )

        // Conflict Alert Banner (to prevent "App not installed" error)
        if (conflictingPackageInstalled && analysis?.packageName != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = NeonAmber.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, NeonAmber)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "تحذير: تعارض التثبيت والتوقيع" else "Installation Conflict Warning",
                            color = NeonAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isArabic)
                            "الحزمة (${analysis?.packageName}) مثبتة مسبقاً على هذا الجهاز بتوقيع مختلف. نظام الأندرويد يرفض التثبيت برسالة 'التطبيق ليس مثبتاً' عند اختلاف التوقيع. يرجى إلغاء تثبيت النسخة الحالية لتثبيت النسخة المحولة مباشرة بدون مشاكل."
                        else
                            "Package (${analysis?.packageName}) is already installed on this device with a different signature. Android will reject the installation with 'App not installed' unless you uninstall the old version first.",
                        color = Color(0xFFF0E6D2),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.uninstallConflictingPackage() },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonAmber),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isArabic) "إلغاء تثبيت النسخة القديمة الآن" else "Uninstall Old Version Now",
                            color = DarkBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Toggle Switch Card: "التنظيف والتوقيع التلقائي" (Auto Clean & Sign)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, if (autoCleanAndSign) NeonCyan.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (autoCleanAndSign) NeonCyan.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = if (autoCleanAndSign) NeonCyan else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isArabic) "التنظيف والتوقيع التلقائي" else "Auto Clean & Sign",
                                color = if (autoCleanAndSign) NeonCyan else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (autoCleanAndSign) {
                                    if (isArabic) "مُفعل - جاهز للتثبيت على كافة إصدارات الأندرويد" else "Enabled - Ready for direct installation"
                                } else {
                                    if (isArabic) "مُعطل" else "Disabled"
                                },
                                color = if (autoCleanAndSign) NeonGreen else TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = autoCleanAndSign,
                        onCheckedChange = { viewModel.toggleAutoCleanAndSign(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(alpha = 0.35f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkBackground
                        )
                    )
                }

                if (autoCleanAndSign) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val bulletPoints = if (isArabic) listOf(
                            "إزالة سمات التجزئة من المانفيست (requiredSplitTypes, splitTypes, isSplitRequired)",
                            "إزالة وسوم com.android.vending.splits وطوابع التوزيع التجميعي",
                            "ترجيع كلاس Application إلى android.app.Application عند التعارض",
                            "تصفية مجلد assets ومطابقة وفحص ملفات lib/*.so لمعماريات المعالج",
                            "تنفيذ محاذاة 4-byte ZipAlign وتوقيع رسمي بمخططات V1, V2, V3"
                        ) else listOf(
                            "Clean split attributes in manifest (requiredSplitTypes, isSplitRequired)",
                            "Strip com.android.vending.splits metadata and distribution stamps",
                            "Reset custom Application class to android.app.Application on conflict",
                            "Filter temp assets and validate lib/*.so ELF binaries for all ABIs",
                            "Execute 4-byte/4KB ZipAlign and official V1, V2, V3 APK signature schemes"
                        )

                        for (bullet in bulletPoints) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "✓",
                                    color = NeonGreen,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text(
                                    text = bullet,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Card 3: PairIP Inspector Card
        PairIpInspectorCard(
            isScanningEnabled = scanPairIp,
            onToggleScan = { viewModel.togglePairIpScan(it) },
            onViewReport = {
                viewModel.showPairIpModal(true)
            },
            hasReport = analysis?.pairIpReport != null,
            hasPairIp = analysis?.pairIpReport?.hasPairIp ?: false
        )

        // Quick PairIP Strip Button if PairIP is detected
        if (analysis?.pairIpReport?.hasPairIp == true) {
            OutlinedButton(
                onClick = { viewModel.removePairIpFromManifest() },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = NeonAmber.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isArabic) "حذف كود PairIP تلقائياً من الـ Manifest الآن" else "Strip PairIP Codes from Manifest Now",
                    color = NeonAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
            }
        }

        // Progress Card (when extracting)
        progress?.let { p ->
            ExtractionProgressCard(
                progress = p,
                onCancel = { viewModel.cancelExtraction() }
            )
        }

        // Signing & ZipAlign Status Badge
        val signingConfig by viewModel.signingConfig.collectAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val signText = if (signingConfig.isAutoSigning) {
                    if (isArabic) "التوقيع التلقائي: v1, v2, v3 + محاذاة ZipAlign 4B/4KB"
                    else "Auto-Sign: v1, v2, v3 + 4B/4KB ZipAlign"
                } else {
                    val activeSchemes = mutableListOf<String>()
                    if (signingConfig.v1Enabled) activeSchemes.add("v1")
                    if (signingConfig.v2Enabled) activeSchemes.add("v2")
                    if (signingConfig.v3Enabled) activeSchemes.add("v3")
                    if (signingConfig.v4Enabled) activeSchemes.add("v4")
                    (if (isArabic) "التوقيع اليدوي: " else "Manual Sign: ") + activeSchemes.joinToString(", ")
                }
                Text(
                    text = signText,
                    color = NeonGreen,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = if (signingConfig.useCustomKeystore) (if (isArabic) "مفتاح مخصص" else "Custom Key") else (if (isArabic) "مفتاح TestKey" else "AOSP TestKey"),
                color = TextMuted,
                fontSize = 11.sp
            )
        }

        // Primary Button: EXTRACT TO DIRECT APK FILE
        CyberPrimaryGradientButton(
            text = if (isArabic) "تحويل واستخراج إلى ملف APK جاهز" else "CONVERT TO STANDALONE APK",
            onClick = { viewModel.startExtraction() },
            enabled = selectedFileName != null,
            isLoading = progress != null && !(progress?.isComplete ?: false)
        )

        // Secondary Button: APK EXPLORER (MT STYLE)
        CyberSecondaryButton(
            text = if (isArabic) "مستكشف الحزم ومحرر المانفيست (نمط MT Manager)" else "APK EXPLORER (MT STYLE)",
            icon = {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                viewModel.openMtExplorer()
            }
        )

        // Tertiary Button: PAIRIP INSPECTOR
        CyberSecondaryButton(
            text = if (isArabic) "تقرير تشخيص PAIRIP" else "PAIRIP INSPECTOR",
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                viewModel.showPairIpModal(true)
            }
        )

        // Output Location Row
        OutputLocationCard(
            path = if (isArabic) "مجلد الحفظ: /Downloads/APK Website" else "Output Location: /Downloads/APK Website"
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
