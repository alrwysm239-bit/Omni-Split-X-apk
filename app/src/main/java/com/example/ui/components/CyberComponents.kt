package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.PairIpReport
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanLight
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.OmniGradient
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.ExtractionProgress

/**
 * Neon cyber styled top title header matching the user reference screenshot
 */
@Composable
fun AppCyberHeader(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ANTI-SPLIT TOOLKIT",
            color = NeonMagenta,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        val titleText = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp
                )
            ) {
                append("Omni Split ")
            }
            withStyle(
                SpanStyle(
                    color = NeonCyan,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp
                )
            ) {
                append("X")
            }
        }
        Text(
            text = titleText,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Dashed glowing neon box for selecting files
 */
@Composable
fun SelectFileDashedCard(
    modifier: Modifier = Modifier,
    fileName: String? = null,
    fileSizeStr: String? = null,
    onClick: () -> Unit
) {
    val stroke = Stroke(
        width = 3.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f),
        cap = StrokeCap.Round
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .drawBehind {
                drawRoundRect(
                    brush = OmniGradient,
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = stroke
                )
            }
            .clickable(onClick = onClick)
            .testTag("select_file_dashed_card")
            .padding(vertical = 20.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeonCyan.copy(alpha = 0.12f))
                    .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = "Upload",
                    tint = NeonCyan,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName ?: "Select XAPK / APKS / APK",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (fileName != null) "File loaded • Tap to change" else "Tap to browse files",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = fileSizeStr ?: "Maximum file size: Up to 4 GB",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Package metadata card matching the screenshot's key-value rows
 */
@Composable
fun PackageInfoCard(
    packageName: String,
    architecture: String,
    version: String,
    actualFileSize: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(DarkBorder, DarkBorder.copy(alpha = 0.5f))
            ),
            width = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PackageInfoRow(
                label = "Package Name",
                value = packageName,
                isPending = packageName.contains("Pending")
            )
            HorizontalDivider(color = DarkBorder.copy(alpha = 0.4f), thickness = 0.8.dp)
            PackageInfoRow(
                label = "Architecture",
                value = architecture
            )
            HorizontalDivider(color = DarkBorder.copy(alpha = 0.4f), thickness = 0.8.dp)
            PackageInfoRow(
                label = "Version",
                value = version
            )
            HorizontalDivider(color = DarkBorder.copy(alpha = 0.4f), thickness = 0.8.dp)
            PackageInfoRow(
                label = "Actual File Size",
                value = actualFileSize
            )
        }
    }
}

@Composable
fun PackageInfoRow(
    label: String,
    value: String,
    isPending: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = if (isPending) TextMuted else Color.White,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * PairIP Inspector section card with toggle and report button
 */
@Composable
fun PairIpInspectorCard(
    isScanningEnabled: Boolean,
    onToggleScan: (Boolean) -> Unit,
    onViewReport: () -> Unit,
    hasReport: Boolean,
    hasPairIp: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(NeonCyan.copy(alpha = 0.35f), DarkBorder)
            ),
            width = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonCyan.copy(alpha = 0.15f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Security",
                        tint = NeonCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "PAIRIP INSPECTOR",
                    color = NeonCyan,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Scan PairIP Components",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = isScanningEnabled,
                    onCheckedChange = onToggleScan,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkBorder
                    ),
                    modifier = Modifier.testTag("pairip_toggle_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onViewReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("view_pairip_report_button"),
                shape = RoundedCornerShape(10.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.horizontalGradient(
                        listOf(NeonCyan.copy(alpha = 0.7f), NeonMagenta.copy(alpha = 0.7f))
                    ),
                    width = 1.5.dp
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (hasReport) {
                        Icon(
                            imageVector = if (hasPairIp) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (hasPairIp) NeonAmber else NeonGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "VIEW PAIRIP REPORT",
                        color = NeonCyanLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

/**
 * Big glowing gradient primary action button
 */
@Composable
fun CyberPrimaryGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    testTag: String = "primary_gradient_button"
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = DarkSurfaceVariant
        ),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(
                    if (enabled) OmniGradient else Brush.linearGradient(listOf(DarkSurfaceVariant, DarkSurfaceVariant))
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    // Split layers icon
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/**
 * Secondary dark button with cyan border
 */
@Composable
fun CyberSecondaryButton(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "secondary_cyber_button"
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(
                listOf(NeonCyan.copy(alpha = 0.5f), DarkBorder)
            ),
            width = 1.2.dp
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = DarkSurface
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

/**
 * Output location row
 */
@Composable
fun OutputLocationCard(
    path: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(listOf(DarkBorder, DarkBorder)),
            width = 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Folder",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Output Location: $path",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Progress Banner
 */
@Composable
fun ExtractionProgressCard(
    progress: ExtractionProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = OmniGradient,
            width = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = progress.stage,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${(progress.percentage * 100).toInt()}%",
                    color = NeonCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress.percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = NeonCyan,
                trackColor = DarkBorder
            )

            if (!progress.isComplete && progress.error == null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(8.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.linearGradient(listOf(DarkBorder, DarkBorder)),
                            width = 1.dp
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
                    ) {
                        Text("CANCEL", color = NeonMagenta, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * High-fidelity PairIP Diagnostic Report Dialog
 */
@Composable
fun PairIpReportDialog(
    report: PairIpReport?,
    onDismiss: () -> Unit
) {
    if (report == null) return

    val clipboard = LocalClipboardManager.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(20.dp),
            color = DarkBackground,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.verticalGradient(
                    listOf(
                        if (report.hasPairIp) NeonAmber else NeonCyan,
                        DarkBorder
                    )
                ),
                width = 1.5.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (report.hasPairIp) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (report.hasPairIp) NeonAmber else NeonGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "PairIP Diagnostic Audit",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                HorizontalDivider(
                    color = DarkBorder,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (report.hasPairIp) NeonAmber.copy(alpha = 0.12f) else NeonGreen.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        if (report.hasPairIp) NeonAmber.copy(alpha = 0.4f) else NeonGreen.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                ),
                                width = 1.dp
                            )
                        ) {
                            Text(
                                text = report.riskSummary,
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (report.hasPairIp) {
                        item {
                            Text(
                                text = "Detected Manifest Components (${report.detectedComponents.size}):",
                                color = NeonCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(report.detectedComponents) { comp ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                shape = RoundedCornerShape(8.dp),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder)),
                                    width = 1.dp
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = comp.componentName,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "[${comp.type}] ${comp.details}",
                                        color = TextSecondary,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(10.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder)),
                                width = 1.dp
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Integrity & Compliance Notice",
                                        color = NeonCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "PairIP components are responsible for application licensing and play integrity verification. In accordance with system security standards, this tool conducts audits and diagnostics. It does not bypass or disable licensing mechanisms.",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val text = "PairIP Diagnostic Report\n" +
                                "Status: ${if (report.hasPairIp) "Detected" else "Clean"}\n" +
                                "Components:\n" + report.detectedComponents.joinToString("\n") { "• ${it.componentName} (${it.type})" } +
                                "\nSummary: ${report.riskSummary}"
                            clipboard.setText(AnnotatedString(text))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder)),
                            width = 1.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy", color = NeonCyan, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Text("Close", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
