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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawWithCache
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
import com.example.data.AdvancedSettings
import com.example.data.AudioLatencyMode
import com.example.data.ExportBitrate
import com.example.data.ExportFormat
import com.example.data.LoudnessTarget
import com.example.data.MetronomeTone
import com.example.data.PlaybackUiState
import com.example.data.ProcessingProgress
import com.example.data.ProcessingQuality
import com.example.data.VisualizerStyle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldDrum
import com.example.ui.theme.PeakRed
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.WarmAmber
import java.util.Locale
import kotlin.math.roundToInt

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
fun StereoPeakMeterBar(
    peaksProvider: () -> Pair<Float, Float>,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val trackBg = MaterialTheme.colorScheme.surfaceVariant
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("L/R", style = StudioMonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0A0F19))
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val rowH = (h - 2.dp.toPx()) / 2f
                        val segments = 24
                        val gap = 2.dp.toPx()
                        val segW = ((w - gap * (segments - 1)) / segments).coerceAtLeast(1f)
                        val r = CornerRadius(2.dp.toPx(), 2.dp.toPx())

                        onDrawBehind {
                            val (leftPeak, rightPeak) = if (isActive) peaksProvider() else (0f to 0f)
                            for (i in 0 until segments) {
                                val thresh = (i + 1) / segments.toFloat()
                                val x = i * (segW + gap)
                                val activeColor = when {
                                    i >= segments - 2 -> PeakRed
                                    i >= segments - 6 -> WarmAmber
                                    else -> EmeraldDrum
                                }
                                drawRoundRect(
                                    color = if (leftPeak >= thresh - 0.02f) activeColor else trackBg.copy(alpha = 0.45f),
                                    topLeft = Offset(x, 0f),
                                    size = Size(segW, rowH),
                                    cornerRadius = r
                                )
                                drawRoundRect(
                                    color = if (rightPeak >= thresh - 0.02f) activeColor else trackBg.copy(alpha = 0.45f),
                                    topLeft = Offset(x, rowH + 2.dp.toPx()),
                                    size = Size(segW, rowH),
                                    cornerRadius = r
                                )
                            }
                        }
                    }
            )
        }
    }
}

@Composable
fun TransportConsoleBar(
    playback: PlaybackUiState,
    hasAudio: Boolean,
    stereoPeaksProvider: () -> Pair<Float, Float> = { 0f to 0f },
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    onSkipBack: () -> Unit = {},
    onSkipForward: () -> Unit = {},
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
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onSkipBack,
                        enabled = hasAudio,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("transport_skip_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Replay5,
                            contentDescription = "חזור 5 שניות"
                        )
                    }

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

                    IconButton(
                        onClick = onSkipForward,
                        enabled = hasAudio,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("transport_skip_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Forward5,
                            contentDescription = "קדם 5 שניות"
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenExport,
                        enabled = hasAudio,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("transport_export_button"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp)
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
                Spacer(Modifier.height(6.dp))
                StereoPeakMeterBar(
                    peaksProvider = stereoPeaksProvider,
                    isActive = playback.isPlaying
                )
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
    selectionDurationMs: Long,
    totalDurationMs: Long,
    onDismiss: () -> Unit,
    onSaveInternalOnly: (String, ExportFormat, ExportBitrate, Boolean) -> Unit,
    onPickFolderAndSave: (String, ExportFormat, ExportBitrate, Boolean) -> Unit
) {
    var fileName by remember(defaultTitle) {
        mutableStateOf("${defaultTitle.ifBlank { "playback" }}_studio")
    }
    var selectedFormat by remember { mutableStateOf(ExportFormat.WAV) }
    var selectedBitrate by remember { mutableStateOf(ExportBitrate.K320) }
    var exportSelectionOnly by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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

            if (totalDurationMs > 0L && selectionDurationMs in 1000L until totalDurationMs) {
                SettingSwitchRow(
                    title = "ייצא את טווח העריכה הנבחר בלבד",
                    subtitle = "אורך יעד: ${formatTimeMs(if (exportSelectionOnly) selectionDurationMs else totalDurationMs)}",
                    checked = exportSelectionOnly,
                    onCheckedChange = { exportSelectionOnly = it },
                    tag = "export_selection_only_switch"
                )
            }

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
                    onClick = { onSaveInternalOnly(fileName, selectedFormat, selectedBitrate, exportSelectionOnly) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("export_internal_button")
                ) {
                    Text("שמור בספרייה")
                }
                Button(
                    onClick = { onPickFolderAndSave(fileName, selectedFormat, selectedBitrate, exportSelectionOnly) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsBottomSheet(
    settings: AdvancedSettings,
    cacheSizeMb: Float,
    onDismiss: () -> Unit,
    onToggleDarkTheme: () -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onLatencyModeChange: (AudioLatencyMode) -> Unit,
    onDefaultQualityChange: (ProcessingQuality) -> Unit,
    onVisualizerStyleChange: (VisualizerStyle) -> Unit,
    onMetronomeToneChange: (MetronomeTone) -> Unit,
    onLoudnessTargetChange: (LoudnessTarget) -> Unit,
    onNoiseGateEnabledChange: (Boolean) -> Unit,
    onNoiseGateThresholdChange: (Float) -> Unit,
    onOverdubCompChange: (Int) -> Unit,
    onAutoNormalizeRecChange: (Boolean) -> Unit,
    onLimiterCeilingChange: (Float) -> Unit,
    onClearCache: () -> Unit,
    onResetSettings: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("הגדרות ואפשרויות מתקדמות", style = MaterialTheme.typography.titleLarge)
                }
                TextButton(onClick = onDismiss) { Text("סגור") }
            }

            // Display & Stage Behavior
            SettingSwitchRow(
                title = "מצב תצוגה כהה (Dark Mode)",
                subtitle = "נוח לחדרי חזרות חשוכים ובמה",
                checked = settings.darkTheme,
                onCheckedChange = { onToggleDarkTheme() },
                tag = "settings_dark_theme_switch"
            )
            SettingSwitchRow(
                title = "השאר מסך דולק בחזרות",
                subtitle = "מונע כיבוי מסך בזמן נגינה או שירה",
                checked = settings.keepScreenOn,
                onCheckedChange = onKeepScreenOnChange,
                tag = "settings_keep_screen_on_switch"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            // Audio Buffer Latency Mode
            Text("מצב חביון וזיכרון אודיו (Audio Latency)", style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AudioLatencyMode.entries.forEach { mode ->
                    val selected = settings.latencyMode == mode
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onLatencyModeChange(mode) }
                            .testTag("settings_latency_${mode.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(mode.titleHe, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text("x${mode.bufferMultiplier}", style = StudioMonoStyle, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // Default Separation Quality
            Text("איכות הפרדת ערוצים כברירת מחדל", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ProcessingQuality.entries.forEachIndexed { idx, q ->
                    SegmentedButton(
                        selected = settings.defaultQuality == q,
                        onClick = { onDefaultQualityChange(q) },
                        shape = SegmentedButtonDefaults.itemShape(idx, ProcessingQuality.entries.size)
                    ) {
                        Text(q.titleHe)
                    }
                }
            }

            // Live Visualizer Style
            Text("סגנון תצוגת גל הקול החי", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                VisualizerStyle.entries.forEachIndexed { idx, st ->
                    SegmentedButton(
                        selected = settings.visualizerStyle == st,
                        onClick = { onVisualizerStyleChange(st) },
                        shape = SegmentedButtonDefaults.itemShape(idx, VisualizerStyle.entries.size)
                    ) {
                        Text(st.titleHe, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Metronome Sound Tone
            Text("גוון צליל המטרונום", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                MetronomeTone.entries.forEachIndexed { idx, tone ->
                    SegmentedButton(
                        selected = settings.metronomeTone == tone,
                        onClick = { onMetronomeToneChange(tone) },
                        shape = SegmentedButtonDefaults.itemShape(idx, MetronomeTone.entries.size)
                    ) {
                        Text(tone.titleHe, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            // Auto-Normalize Loudness Target
            Text("יעד עוצמה אוטומטית (Loudness Target)", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                LoudnessTarget.entries.forEachIndexed { idx, target ->
                    SegmentedButton(
                        selected = settings.loudnessTarget == target,
                        onClick = { onLoudnessTargetChange(target) },
                        shape = SegmentedButtonDefaults.itemShape(idx, LoudnessTarget.entries.size)
                    ) {
                        Text(target.titleHe, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Noise Gate
            SettingSwitchRow(
                title = "שער רעשים (Noise Gate)",
                subtitle = "משתיק רעשי רקע וזמזום בקטעים שקטים",
                checked = settings.noiseGateEnabled,
                onCheckedChange = onNoiseGateEnabledChange,
                tag = "settings_noise_gate_switch"
            )
            if (settings.noiseGateEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("סף השתקה (Gate Threshold)", style = MaterialTheme.typography.bodyMedium)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text("${settings.noiseGateThresholdDb.roundToInt()} dB", style = StudioMonoStyle, color = ElectricCyan)
                        }
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Slider(
                            value = settings.noiseGateThresholdDb,
                            onValueChange = onNoiseGateThresholdChange,
                            valueRange = -65f..-30f,
                            modifier = Modifier.fillMaxWidth().testTag("settings_noise_gate_slider")
                        )
                    }
                }
            }

            // Overdub Latency Compensation
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("פיצוי השהיית הקלטת Overdub", style = MaterialTheme.typography.titleSmall)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        val sign = if (settings.overdubLatencyCompMs > 0) "+" else ""
                        Text("${sign}${settings.overdubLatencyCompMs} ms", style = StudioMonoStyle, color = MaterialTheme.colorScheme.primary)
                    }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Slider(
                        value = settings.overdubLatencyCompMs.toFloat(),
                        onValueChange = { onOverdubCompChange(it.roundToInt()) },
                        valueRange = -120f..120f,
                        modifier = Modifier.fillMaxWidth().testTag("settings_overdub_comp_slider")
                    )
                }
            }

            SettingSwitchRow(
                title = "נירמול עוצמה אוטומטי להקלטה",
                subtitle = "מאזן הקלטות מיקרופון שקטות מיידית בסיום ההקלטה",
                checked = settings.autoNormalizeRecording,
                onCheckedChange = onAutoNormalizeRecChange,
                tag = "settings_auto_norm_rec_switch"
            )

            // Master Limiter Ceiling
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("תקרת מגביל עיוותים (Limiter Ceiling)", style = MaterialTheme.typography.titleSmall)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            String.format(Locale.US, "%.1f dB", settings.limiterCeilingDb),
                            style = StudioMonoStyle,
                            color = WarmAmber
                        )
                    }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Slider(
                        value = settings.limiterCeilingDb,
                        onValueChange = onLimiterCeilingChange,
                        valueRange = -3.0f..-0.1f,
                        modifier = Modifier.fillMaxWidth().testTag("settings_limiter_ceiling_slider")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            // Local Cache Cleanup
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("אחסון וזיכרון מטמון מקומי", style = MaterialTheme.typography.titleSmall)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                String.format(Locale.US, "%.1f MB בשימוש", cacheSizeMb),
                                style = StudioMonoStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onClearCache,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_clear_cache_button")
                    ) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("נקה מטמון")
                    }
                }
            }

            OutlinedButton(
                onClick = onResetSettings,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_reset_all_button")
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("אפס את כל ההגדרות לברירת מחדל")
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
