package com.example.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.ui.components.ExtractionProgressCard
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.InstalledAppInfo
import com.example.viewmodel.OmniSplitViewModel
import java.util.Locale

@Composable
fun InstalledAppsScreen(
    viewModel: OmniSplitViewModel,
    modifier: Modifier = Modifier
) {
    val isArabic by viewModel.isArabic.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isLoading by viewModel.isLoadingInstalledApps.collectAsState()
    val progress by viewModel.progress.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    // 0: All, 1: User, 2: System, 3: Split Only
    var filterType by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (installedApps.isEmpty()) {
            viewModel.loadInstalledApps()
        }
    }

    val filteredApps = remember(installedApps, searchQuery, filterType) {
        installedApps.filter { app ->
            val matchesFilter = when (filterType) {
                1 -> !app.isSystemApp
                2 -> app.isSystemApp
                3 -> app.isSplit
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Top Bar: Title & Refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isArabic) "تطبيقات الجهاز والنظام" else "Device & System Apps",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isArabic) "تحويل ودمج تطبيقات النظام والمستخدم إلى APK" else "Merge & convert device apps to standalone APK",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = { viewModel.loadInstalledApps() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkSurface)
                    .testTag("refresh_installed_apps")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = NeonCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = if (isArabic) "بحث باسم التطبيق أو الحزمة..." else "Search app name or package...",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = NeonCyan)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_apps_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            item {
                AppFilterChip(
                    selected = filterType == 0,
                    label = if (isArabic) "الكل (${installedApps.size})" else "All (${installedApps.size})",
                    onClick = { filterType = 0 }
                )
            }
            item {
                AppFilterChip(
                    selected = filterType == 1,
                    label = if (isArabic) "المستخدم" else "User Apps",
                    onClick = { filterType = 1 }
                )
            }
            item {
                AppFilterChip(
                    selected = filterType == 2,
                    label = if (isArabic) "النظام" else "System Apps",
                    onClick = { filterType = 2 }
                )
            }
            item {
                AppFilterChip(
                    selected = filterType == 3,
                    label = if (isArabic) "مقسمة Split" else "Split Apps",
                    onClick = { filterType = 3 }
                )
            }
        }

        // Active conversion progress banner
        AnimatedVisibility(visible = progress != null) {
            progress?.let { p ->
                Spacer(modifier = Modifier.height(8.dp))
                ExtractionProgressCard(
                    progress = p,
                    onCancel = { viewModel.cancelExtraction() }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Apps List or Loading State
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NeonCyan, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isArabic) "جارٍ فحص تطبيقات الجهاز والنظام..." else "Scanning installed apps on device...",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isArabic) "لم يتم العثور على أي تطبيقات مطابقة." else "No matching applications found.",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { appInfo ->
                    InstalledAppCard(
                        appInfo = appInfo,
                        isArabic = isArabic,
                        isProcessing = progress != null,
                        onConvert = {
                            viewModel.convertInstalledApp(appInfo)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = DarkSurface,
            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
            labelColor = TextSecondary,
            selectedLabelColor = NeonCyan
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = DarkBorder,
            selectedBorderColor = NeonCyan
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
fun InstalledAppCard(
    appInfo: InstalledAppInfo,
    isArabic: Boolean,
    isProcessing: Boolean,
    onConvert: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager

    val appIcon = remember(appInfo.packageName) {
        try {
            val d = pm.getApplicationIcon(appInfo.packageName)
            d.toBitmap(96, 96).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    val sizeMb = String.format(Locale.US, "%.1f MB", appInfo.totalSizeBytes / (1024.0 * 1024.0))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, if (appInfo.isSplit) NeonCyan.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Icon
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = appInfo.appName,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = appInfo.appName,
                            tint = NeonCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and Package Name
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appInfo.appName,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = appInfo.packageName,
                        color = TextMuted,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "v${appInfo.versionName} • $sizeMb",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Badges & Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // System vs User badge
                    if (appInfo.isSystemApp) {
                        BadgeLabel(
                            text = if (isArabic) "نظام" else "SYSTEM",
                            textColor = NeonAmber,
                            borderColor = NeonAmber
                        )
                    } else {
                        BadgeLabel(
                            text = if (isArabic) "مستخدم" else "USER",
                            textColor = NeonGreen,
                            borderColor = NeonGreen
                        )
                    }

                    // Split badge
                    if (appInfo.isSplit) {
                        BadgeLabel(
                            text = if (isArabic) "مقسم (${appInfo.splitCount} أجزاء)" else "SPLIT (${appInfo.splitCount})",
                            textColor = NeonCyan,
                            borderColor = NeonCyan
                        )
                    } else {
                        BadgeLabel(
                            text = if (isArabic) "APK مفرد" else "SINGLE APK",
                            textColor = TextMuted,
                            borderColor = DarkBorder
                        )
                    }
                }

                Button(
                    onClick = onConvert,
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black,
                        disabledContainerColor = DarkSurface,
                        disabledContentColor = TextMuted
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("convert_app_${appInfo.packageName}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Transform,
                        contentDescription = "Convert",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isArabic) "تحويل لـ APK" else "Merge to APK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BadgeLabel(
    text: String,
    textColor: Color,
    borderColor: Color
) {
    Box(
        modifier = Modifier
            .border(1.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .background(borderColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
