package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.OmniSplitViewModel

@Composable
fun SettingsScreen(
    viewModel: OmniSplitViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val scanPairIp by viewModel.scanPairIp.collectAsState()
    val isArabic by viewModel.isArabic.collectAsState()
    val signingConfig by viewModel.signingConfig.collectAsState()
    var storageGuardEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (isArabic) "الإعدادات والتخصيص" else "Settings & Configuration",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isArabic)
                "تخصيص محرك التوقيع، فحص PairIP، ومحاذاة ZipAlign لتفادي خطأ الحزمة غير الصالحة"
            else
                "Configure APK Signing schemes, ZipAlign 4-byte engine, and PairIP removal",
            color = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Preference Card 0: MT Manager Style APK Signing & ZipAlign Engine
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(NeonCyan.copy(alpha = 0.5f), DarkBorder)),
                width = 1.dp
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isArabic) "محرك التوقيع والمحاذاة (MT Manager Style)" else "APK Signing & ZipAlign Engine",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isArabic)
                                "محاذاة 4 بايت / 4KB مع خطط التوقيع v1, v2, v3 لمنع مشكلة الحزمة غير الصالحة"
                            else
                                "4-byte & 4KB ZipAlign with Schemes v1-v4 for 100% Android install success",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }

                HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)

                SettingsSwitchRow(
                    icon = Icons.Default.Speed,
                    title = if (isArabic) "التوقيع التلقائي الذكي (مستحسن)" else "Automatic Signing (Recommended)",
                    subtitle = if (isArabic)
                        "تطبيق تلقائي لخطط v1 (JAR) و v2 و v3 مع محاذاة ZipAlign كاملة ومفتاح AOSP TestKey"
                    else
                        "Auto-detects and applies v1 (JAR), v2, and v3 schemes with embedded TestKey",
                    checked = signingConfig.isAutoSigning,
                    onCheckedChange = { viewModel.setAutoSigning(it) }
                )

                if (!signingConfig.isAutoSigning) {
                    HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)

                    Text(
                        text = if (isArabic) "مخططات التوقيع اليدوية (Manual Schemes):" else "Manual Scheme Selection:",
                        color = NeonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SigningSchemeCheckbox(
                            title = "Scheme V1 (JAR Signature)",
                            subtitle = if (isArabic) "توقيع قديم متوافق مع Android 5.0+" else "Legacy JAR signature for Android 5.0+",
                            checked = signingConfig.v1Enabled,
                            onCheckedChange = { viewModel.setSchemeV1(it) }
                        )

                        SigningSchemeCheckbox(
                            title = "Scheme V2 (Full APK Signature)",
                            subtitle = if (isArabic) "إلزامي لنظام Android 11+ ويمنع خطأ الحزمة غير الصالحة" else "Full APK signature (Mandatory on Android 11+)",
                            checked = signingConfig.v2Enabled,
                            onCheckedChange = { viewModel.setSchemeV2(it) }
                        )

                        SigningSchemeCheckbox(
                            title = "Scheme V3 (Key Rotation Scheme)",
                            subtitle = if (isArabic) "دعم تدوير المفاتيح لنظام Android 9.0+" else "Key rotation & SDK version targeting (Android 9.0+)",
                            checked = signingConfig.v3Enabled,
                            onCheckedChange = { viewModel.setSchemeV3(it) }
                        )

                        SigningSchemeCheckbox(
                            title = "Scheme V4 (Incremental .idsig)",
                            subtitle = if (isArabic) "توليد ملف .idsig للتثبيت التدريجي لنظام Android 11+" else "Generates .idsig file for incremental installs (Android 11+)",
                            checked = signingConfig.v4Enabled,
                            onCheckedChange = { viewModel.setSchemeV4(it) }
                        )
                    }
                }

                HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)

                // Keystore Selection
                SettingsSwitchRow(
                    icon = Icons.Default.Info,
                    title = if (isArabic) "استخدام مفتاح مخصص (Custom Keystore)" else "Use Custom Keystore (.jks / .p12)",
                    subtitle = if (isArabic)
                        "استيراد مفتاح خاص بك بدلاً من مفتاح AOSP TestKey الافتراضي"
                    else
                        "Specify custom .jks or .keystore file instead of standard AOSP TestKey",
                    checked = signingConfig.useCustomKeystore,
                    onCheckedChange = { viewModel.setCustomKeystore(it) }
                )

                if (signingConfig.useCustomKeystore) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBackground, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isArabic) "بيانات المفتاح المخصص:" else "Custom Keystore Credentials:",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        androidx.compose.material3.OutlinedTextField(
                            value = signingConfig.customKeystorePath ?: "",
                            onValueChange = { viewModel.setCustomKeystore(true, it, signingConfig.keystorePassword, signingConfig.keyAlias, signingConfig.keyPassword) },
                            label = { Text(if (isArabic) "مسار ملف Keystore (/sdcard/...)" else "Keystore Path (/sdcard/...)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        androidx.compose.material3.OutlinedTextField(
                            value = signingConfig.keystorePassword,
                            onValueChange = { viewModel.setCustomKeystore(true, signingConfig.customKeystorePath, it, signingConfig.keyAlias, signingConfig.keyPassword) },
                            label = { Text(if (isArabic) "كلمة مرور Keystore" else "Keystore Password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        androidx.compose.material3.OutlinedTextField(
                            value = signingConfig.keyAlias,
                            onValueChange = { viewModel.setCustomKeystore(true, signingConfig.customKeystorePath, signingConfig.keystorePassword, it, signingConfig.keyPassword) },
                            label = { Text(if (isArabic) "اسم المعرّف (Key Alias)" else "Key Alias") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        androidx.compose.material3.OutlinedTextField(
                            value = signingConfig.keyPassword,
                            onValueChange = { viewModel.setCustomKeystore(true, signingConfig.customKeystorePath, signingConfig.keystorePassword, signingConfig.keyAlias, it) },
                            label = { Text(if (isArabic) "كلمة مرور المفتاح (Key Password)" else "Key Password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NeonCyan.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic)
                                "✓ مفتاح AOSP TestKey الرسمي المدمج (RSA 2048 / SHA-256) نشط ومتوافق مع جميع أجهزة أندرويد."
                            else
                                "✓ Official embedded AOSP TestKey (RSA 2048 / SHA-256) is active and ready.",
                            color = NeonCyan,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }

        // Preference Card 1: Language & Diagnostics
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder)),
                width = 1.dp
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsSwitchRow(
                    icon = Icons.Default.Language,
                    title = if (isArabic) "واجهة اللغة العربية" else "Arabic Interface (العربية)",
                    subtitle = if (isArabic) "التبديل بين العربية والإنجليزية" else "Switch language between English and Arabic",
                    checked = isArabic,
                    onCheckedChange = { viewModel.toggleArabic() }
                )

                HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)

                SettingsSwitchRow(
                    icon = Icons.Default.Security,
                    title = if (isArabic) "فحص وحذف كود PairIP تلقائياً" else "PairIP Auto-Scan & Remove",
                    subtitle = if (isArabic)
                        "تدقيق وحذف كود PairIP تلقائياً فور اختيار الأرشيف"
                    else
                        "Automatically audit and clean AndroidManifest.xml when loading archives",
                    checked = scanPairIp,
                    onCheckedChange = { viewModel.togglePairIpScan(it) }
                )

                HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)

                SettingsSwitchRow(
                    icon = Icons.Default.SdStorage,
                    title = if (isArabic) "حماية مساحة التخزين" else "Storage Safety Guard",
                    subtitle = if (isArabic)
                        "التحقق من توفر مساحة كافية على الذاكرة قبل بدء التحويل"
                    else
                        "Verify sufficient free disk space prior to extracting large archives",
                    checked = storageGuardEnabled,
                    onCheckedChange = { storageGuardEnabled = it }
                )
            }
        }

        // Card 2: Performance & Engine Specs
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder)),
                width = 1.dp
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isArabic) "مواصفات محرك التحويل والاستخراج" else "Engine Specifications",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val specsText = if (isArabic) {
                    "• مسار الحفظ الافتراضي: مجلد Downloads/APK Website\n" +
                        "• صيغة الإخراج: ملف APK حقيقي ومباشر جاهز للتثبيت\n" +
                        "• حجم مخزن البث المؤقت: 64 كيلوبايت (معالجة تدفق مباشر لمنع نفاذ الذاكرة)\n" +
                        "• دعم الملفات الضخمة: حتى 4 جيجابايت مع حسابات 64 بت\n" +
                        "• الحماية من ثغرات الأرشيف: فحص مسار المسار القانوني (Zip Slip Protection)\n" +
                        "• العمل بدون إنترنت: 100% محلي وبدون صلاحيات شبكة"
                } else {
                    "• Output Directory: Dedicated Downloads/APK Website folder\n" +
                        "• Output Format: Direct standalone .apk file ready to install\n" +
                        "• Streaming Buffer: 64 KB chunk size (zero memory blow-up)\n" +
                        "• Max File Support: Up to 4 GB with 64-bit offsets\n" +
                        "• Zip Slip Protection: Canonical path validation\n" +
                        "• Connectivity: 100% Offline (Zero internet permissions required)"
                }

                Text(
                    text = specsText,
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp
                )
            }
        }

        // Card 3: About & Compliance
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder)),
                width = 1.dp
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isArabic) "حول Omni Split X" else "About Omni Split X",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val aboutDesc = if (isArabic) {
                    "Omni Split X هو تطبيق احترافي متقدم لتحويل وتفكيك حزم XAPK و APKS و ZIP واستخراجها كملفات APK كاملة ومستقلة مباشرة في مجلد Downloads/APK Website، مع فحص وفلترة أكواد PairIP ومستكشف داخلي للمانفيست."
                } else {
                    "Omni Split X is a high-performance Anti-Split Toolkit designed to convert and extract XAPK, APKS, and ZIP archives directly into standalone APK files inside Downloads/APK Website with PairIP manifest inspection and MT-style explorer."
                }

                Text(
                    text = aboutDesc,
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "الإصدار 1.0.0 (Build 42) • معمارية Jetpack Compose الحديثة",
                    color = NeonCyan.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NeonCyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = DarkBorder
            )
        )
    }
}

@Composable
fun SigningSchemeCheckbox(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.CheckboxDefaults.colors(
                checkedColor = NeonCyan,
                checkmarkColor = DarkBackground,
                uncheckedColor = TextSecondary
            )
        )
    }
}

