package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanLight
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.util.ArchiveEntryItem
import java.util.Locale

@Composable
fun MtManagerExplorerDialog(
    entries: List<ArchiveEntryItem>,
    manifestContent: String?,
    searchQuery: String,
    isArabic: Boolean = true,
    onSearchChange: (String) -> Unit,
    onRemovePairIp: () -> Boolean,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Manifest Editor, 1: Archive Entries
    var pairIpRemovedNotice by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val fullXml = manifestContent ?: "<!-- No AndroidManifest.xml parsed yet -->"
    val hasPairIpInXml = fullXml.contains("pairip", ignoreCase = true) ||
        fullXml.contains("LicenseActivity", ignoreCase = true) ||
        fullXml.contains("LicenseContentProvider", ignoreCase = true)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(16.dp),
            color = DarkBackground,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.verticalGradient(listOf(NeonCyan, DarkBorder)),
                width = 1.5.dp
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isArabic) "مستكشف الحزم ومحرر المانفيست" else "APK Explorer (MT Style)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp
                            )
                            Text(
                                text = if (isArabic) "فحص وبحث وحذف أكواد PairIP من الحزمة" else "Inspect, search, and clean package internals",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 10.dp))

                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 0) NeonCyan.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isArabic) "محرر AndroidManifest.xml" else "AndroidManifest.xml",
                            color = if (selectedTab == 0) NeonCyan else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 1) NeonCyan.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isArabic) "محتويات الأرشيف (${entries.size})" else "Archive Entries (${entries.size})",
                            color = if (selectedTab == 1) NeonCyan else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == 0) {
                    // Search in Manifest bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                if (isArabic) "بحث في المانفيست (مثال: pairip, activity)..." else "Search in manifest (e.g. pairip)...",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = NeonCyan, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = searchQuery.equals("pairip", ignoreCase = true),
                            onClick = { onSearchChange(if (searchQuery == "pairip") "" else "pairip") },
                            label = { Text("PairIP", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonAmber.copy(alpha = 0.25f),
                                selectedLabelColor = NeonAmber,
                                containerColor = DarkSurface,
                                labelColor = TextSecondary
                            )
                        )
                        FilterChip(
                            selected = searchQuery.equals("license", ignoreCase = true),
                            onClick = { onSearchChange(if (searchQuery == "license") "" else "license") },
                            label = { Text(if (isArabic) "الترخيص" else "License", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.25f),
                                selectedLabelColor = NeonCyan,
                                containerColor = DarkSurface,
                                labelColor = TextSecondary
                            )
                        )
                        FilterChip(
                            selected = searchQuery.equals("activity", ignoreCase = true),
                            onClick = { onSearchChange(if (searchQuery == "activity") "" else "activity") },
                            label = { Text(if (isArabic) "الأنشطة" else "Activities", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonMagenta.copy(alpha = 0.25f),
                                selectedLabelColor = NeonMagenta,
                                containerColor = DarkSurface,
                                labelColor = TextSecondary
                            )
                        )
                    }

                    // Success notice banner when PairIP is removed
                    AnimatedVisibility(visible = pairIpRemovedNotice || !hasPairIpInXml) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.15f)),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(NeonGreen.copy(alpha = 0.6f), Color.Transparent)),
                                width = 1.dp
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isArabic) "تم تطهير وحذف جميع أكواد PairIP بنجاح من المانفيست!" else "All PairIP codes stripped successfully from manifest!",
                                    color = NeonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Manifest code viewer with highlighting (FORCED LTR so XML does not mirror!)
                    val lines = fullXml.lines()
                    val filteredLines = if (searchQuery.isBlank()) {
                        lines
                    } else {
                        lines.filter { it.contains(searchQuery, ignoreCase = true) }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF07080C)),
                        shape = RoundedCornerShape(10.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(listOf(DarkBorder, DarkBorder)),
                            width = 1.dp
                        )
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                            ) {
                                items(filteredLines) { line ->
                                    val isPairIpLine = line.contains("pairip", ignoreCase = true) ||
                                        line.contains("LicenseActivity", ignoreCase = true) ||
                                        line.contains("LicenseContentProvider", ignoreCase = true)
                                    val matchesSearch = searchQuery.isNotBlank() && line.contains(searchQuery, ignoreCase = true)

                                    val textColor = when {
                                        isPairIpLine -> NeonAmber
                                        matchesSearch -> NeonCyan
                                        line.trim().startsWith("<!--") -> TextMuted
                                        line.trim().startsWith("<") -> Color(0xFFE2E8F0)
                                        else -> TextSecondary
                                    }

                                    Text(
                                        text = line,
                                        color = textColor,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isPairIpLine || matchesSearch) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(fullXml))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.linearGradient(listOf(DarkBorder, DarkBorder)),
                                width = 1.dp
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isArabic) "نسخ كود XML" else "Copy XML", color = TextSecondary, fontSize = 11.5.sp)
                        }

                        // REMOVE PAIRIP BUTTON - ACTUALLY STRIPS THE CODES!
                        Button(
                            onClick = {
                                val success = onRemovePairIp()
                                if (success) {
                                    pairIpRemovedNotice = true
                                }
                            },
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasPairIpInXml) NeonAmber.copy(alpha = 0.25f) else NeonGreen.copy(alpha = 0.25f)
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.linearGradient(
                                    listOf(
                                        if (hasPairIpInXml) NeonAmber else NeonGreen,
                                        if (hasPairIpInXml) NeonAmber else NeonGreen
                                    )
                                ),
                                width = 1.dp
                            )
                        ) {
                            Icon(
                                imageVector = if (hasPairIpInXml) Icons.Default.DeleteSweep else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (hasPairIpInXml) NeonAmber else NeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasPairIpInXml) {
                                    if (isArabic) "حذف كود PairIP" else "Remove PairIP"
                                } else {
                                    if (isArabic) "تم حذف PairIP ✓" else "PairIP Clean ✓"
                                },
                                color = if (hasPairIpInXml) NeonAmber else NeonGreen,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Archive entries tree
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(entries) { item ->
                            ArchiveEntryRow(
                                item = item,
                                onClick = {
                                    if (item.isManifest) {
                                        selectedTab = 0
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArchiveEntryRow(
    item: ArchiveEntryItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    if (item.isManifest) NeonCyan.copy(alpha = 0.4f) else DarkBorder,
                    DarkBorder
                )
            ),
            width = 0.8.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        item.isDirectory -> Icons.Default.Folder
                        item.isManifest -> Icons.Default.Description
                        else -> Icons.Default.Code
                    },
                    contentDescription = null,
                    tint = when {
                        item.isManifest -> NeonCyan
                        item.isDex -> NeonMagenta
                        item.isApk -> NeonGreen
                        else -> TextSecondary
                    },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = item.path,
                        color = if (item.isManifest) NeonCyanLight else Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (item.isManifest) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            if (item.size > 0) {
                Text(
                    text = String.format(Locale.US, "%.1f KB", item.size / 1024.0),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}
