package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ExportBitrate
import com.example.data.ExportFormat
import com.example.data.PlaybackUiState
import com.example.data.ProcessingProgress
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.WarmAmber
import java.util.Locale

fun formatTimeMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = safe / 1000L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    val tenths = (safe % 1000L) / 100L
    return String.format(Locale.US, "%02d:%02d.%d", mins, secs, tenths)
}

@Composable
fun WaveformCanvas(
    peaks: List<Float>,
    progressRatio: Float,
    zoomLevel: Float,
    scrollOffsetRatio: Float,
    selectionStartRatio: Float?,
    selectionEndRatio: Float?,
    loopEnabled: Boolean,
    loopARatio: Float,
    loopBRatio: Float,
    onSeekRatio: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaceBg = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val borderColor = MaterialTheme.colorScheme.outline

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(surfaceBg.copy(alpha = 0.9f), Color(0xFF0A0F19))
                    )
                )
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .pointerInput(zoomLevel, scrollOffsetRatio) {
                    detectTapGestures { offset ->
                        val localFrac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val visibleSpan = 1f / zoomLevel.coerceAtLeast(1f)
                        val globalRatio = (scrollOffsetRatio + localFrac * visibleSpan).coerceIn(0f, 1f)
                        onSeekRatio(globalRatio)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp)) {
                val w = size.width
                val h = size.height
                val centerY = h / 2f
                val visibleSpan = 1f / zoomLevel.coerceAtLeast(1f)
                val viewStart = scrollOffsetRatio.coerceIn(0f, (1f - visibleSpan).coerceAtLeast(0f))
                val viewEnd = viewStart + visibleSpan

                fun ratioToX(r: Float): Float = ((r - viewStart) / visibleSpan) * w

                // Center baseline
                drawLine(
                    color = borderColor.copy(alpha = 0.6f),
                    start = Offset(0f, centerY),
                    end = Offset(w, centerY),
                    strokeWidth = 1.dp.toPx()
                )

                // A-B Loop Highlight Zone
                if (loopEnabled) {
                    val ax = ratioToX(loopARatio).coerceIn(0f, w)
                    val bx = ratioToX(loopBRatio).coerceIn(0f, w)
                    if (bx > ax) {
                        drawRect(
                            color = WarmAmber.copy(alpha = 0.16f),
                            topLeft = Offset(ax, 0f),
                            size = Size(bx - ax, h)
                        )
                        drawLine(WarmAmber, Offset(ax, 0f), Offset(ax, h), strokeWidth = 2.dp.toPx())
                        drawLine(WarmAmber, Offset(bx, 0f), Offset(bx, h), strokeWidth = 2.dp.toPx())
                    }
                }

                // Selection Range Highlight
                if (selectionStartRatio != null && selectionEndRatio != null) {
                    val sx = ratioToX(selectionStartRatio).coerceIn(0f, w)
                    val ex = ratioToX(selectionEndRatio).coerceIn(0f, w)
                    if (ex > sx) {
                        drawRect(
                            color = tertiaryColor.copy(alpha = 0.15f),
                            topLeft = Offset(sx, 0f),
                            size = Size(ex - sx, h)
                        )
                        drawLine(tertiaryColor, Offset(sx, 0f), Offset(sx, h), strokeWidth = 2.dp.toPx())
                        drawLine(tertiaryColor, Offset(ex, 0f), Offset(ex, h), strokeWidth = 2.dp.toPx())
                    }
                }

                // Draw Waveform Bars
                if (peaks.isNotEmpty()) {
                    val startIdx = (viewStart * peaks.size).toInt().coerceIn(0, peaks.size - 1)
                    val endIdx = (viewEnd * peaks.size).toInt().coerceIn(startIdx + 1, peaks.size)
                    val visibleCount = (endIdx - startIdx).coerceAtLeast(1)
                    val slotW = w / visibleCount
                    val barW = (slotW * 0.62f).coerceAtLeast(2f)

                    for (i in startIdx until endIdx) {
                        val barProgress = i.toFloat() / peaks.size
                        val x = (i - startIdx) * slotW + (slotW - barW) / 2f
                        val amp = (peaks[i] * (h * 0.44f)).coerceAtLeast(3.dp.toPx())
                        val isPlayed = barProgress <= progressRatio
                        val barColor = when {
                            isPlayed -> primaryColor
                            else -> Color(0xFF4B5B7C)
                        }
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, centerY - amp),
                            size = Size(barW, amp * 2f),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }
                }

                // Playhead Line
                if (progressRatio in viewStart..viewEnd) {
                    val px = ratioToX(progressRatio)
                    drawLine(
                        color = ElectricCyan,
                        start = Offset(px, 0f),
                        end = Offset(px, h),
                        strokeWidth = 2.5.dp.toPx()
                    )
                    drawCircle(
                        color = ElectricCyan,
                        radius = 5.dp.toPx(),
                        center = Offset(px, 6.dp.toPx())
                    )
                }
            }

            if (peaks.isEmpty()) {
                Text(
                    text = "טען שיר מהספרייה להצגת גל הקול",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun TransportConsoleBar(
    playback: PlaybackUiState,
    hasAudio: Boolean,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Float) -> Unit,
    onOpenExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AudioFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = playback.activeTrackTitle,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = "${formatTimeMs(playback.positionMs)} / ${formatTimeMs(playback.durationMs)}",
                                style = StudioMonoStyle,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onStop,
                        enabled = hasAudio,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("transport_stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = stringResource(R.string.cd_stop)
                        )
                    }

                    FilledIconButton(
                        onClick = onTogglePlay,
                        enabled = hasAudio,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("transport_play_button")
                    ) {
                        Icon(
                            imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.cd_play_pause),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenExport,
                        enabled = hasAudio,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("transport_export_button"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = stringResource(R.string.cd_export_audio),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("ייצוא", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (hasAudio) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Slider(
                        value = playback.progressRatio,
                        onValueChange = onSeek,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("transport_seek_slider")
                    )
                }
            }
        }
    }
}

@Composable
fun ProcessingOverlayModal(progress: ProcessingProgress) {
    AnimatedVisibility(
        visible = progress.isActive,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "מעבד מקומית באופליין",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = progress.titleHe,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    LinearProgressIndicator(
                        progress = { progress.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = "${(progress.progress * 100).toInt()}%",
                            style = StudioMonoStyle,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    defaultTitle: String,
    onDismiss: () -> Unit,
    onSaveInternalOnly: (String, ExportFormat, ExportBitrate) -> Unit,
    onPickFolderAndSave: (String, ExportFormat, ExportBitrate) -> Unit
) {
    var fileName by remember(defaultTitle) {
        mutableStateOf("${defaultTitle.ifBlank { "playback" }}_studio")
    }
    var selectedFormat by remember { mutableStateOf(ExportFormat.WAV) }
    var selectedBitrate by remember { mutableStateOf(ExportBitrate.K320) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "ייצוא פלייבק ומיקס סופי",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "כל העיבוד, הסולם, המהירות, האקולייזר והאפקטים מרונדרים מקומית באיכות מלאה.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("שם הקובץ לייצוא") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_filename_input")
            )

            Text("בחר פורמט קובץ", style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { fmt ->
                    val selected = fmt == selectedFormat
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { selectedFormat = fmt }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(fmt.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                fmt.extension.uppercase(),
                                style = StudioMonoStyle,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (selectedFormat != ExportFormat.WAV) {
                Text("איכות קידוד (Bitrate)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportBitrate.entries.forEach { br ->
                        FilterChip(
                            selected = br == selectedBitrate,
                            onClick = { selectedBitrate = br },
                            label = {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text("${br.kbps} kbps")
                                }
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onSaveInternalOnly(fileName, selectedFormat, selectedBitrate) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("export_internal_button")
                ) {
                    Text("שמור בספרייה")
                }
                Button(
                    onClick = { onPickFolderAndSave(fileName, selectedFormat, selectedBitrate) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1.3f)
                        .minimumInteractiveComponentSize()
                        .testTag("export_folder_button")
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("בחר תיקייה ושמור")
                }
            }
        }
    }
}
