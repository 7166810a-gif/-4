package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PlaybackUiState
import com.example.data.RehearsalState
import com.example.data.TrackEntity
import com.example.data.WaveformEditState
import com.example.ui.components.WaveformCanvas
import com.example.ui.components.formatTimeMs
import com.example.ui.theme.StudioMonoStyle

@Composable
fun WaveformScreen(
    hasAudio: Boolean,
    playback: PlaybackUiState,
    waveform: WaveformEditState,
    rehearsal: RehearsalState,
    availableTracks: List<TrackEntity>,
    onSeekRatio: (Float) -> Unit,
    onZoomChange: (Float) -> Unit,
    onScrollChange: (Float) -> Unit,
    onSelectionChange: (Float, Float) -> Unit,
    onTrim: () -> Unit,
    onCut: () -> Unit,
    onFadeDurationChange: (Float) -> Unit,
    onFadeIn: () -> Unit,
    onFadeOut: () -> Unit,
    onAutoTrimSilence: () -> Unit,
    onMergeTrack: (TrackEntity) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onGoToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMergePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!hasAudio) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("עורך גל קול מדויק", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "בחר רצועה מהספרייה כדי לחתוך הקדמות, להסיר קטעים, להוסיף Fade או למזג שירים.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onGoToLibrary) { Text("בחר רצועה מהספרייה") }
                }
            }
            return
        }

        // Top Undo/Redo & Zoom Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = onUndo,
                    enabled = waveform.canUndo,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("waveform_undo_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.cd_undo), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ביטול")
                }
                FilledTonalButton(
                    onClick = onRedo,
                    enabled = waveform.canRedo,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("waveform_redo_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.cd_redo), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("שחזור")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onZoomChange(waveform.zoomLevel - 1f) },
                    enabled = waveform.zoomLevel > 1f,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("zoom_out_button")
                ) {
                    Icon(Icons.Rounded.ZoomOut, contentDescription = "הקטן תצוגה")
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = "${waveform.zoomLevel.toInt()}x",
                        style = StudioMonoStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = { onZoomChange(waveform.zoomLevel + 1f) },
                    enabled = waveform.zoomLevel < 12f,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("zoom_in_button")
                ) {
                    Icon(Icons.Rounded.ZoomIn, contentDescription = "הגדל תצוגה")
                }
            }
        }

        // Waveform Canvas
        WaveformCanvas(
            peaks = waveform.waveformPeaks,
            progressRatio = playback.progressRatio,
            zoomLevel = waveform.zoomLevel,
            scrollOffsetRatio = waveform.scrollOffsetRatio,
            selectionStartRatio = waveform.selectionStartRatio,
            selectionEndRatio = waveform.selectionEndRatio,
            loopEnabled = rehearsal.loopEnabled,
            loopARatio = rehearsal.loopARatio,
            loopBRatio = rehearsal.loopBRatio,
            onSeekRatio = onSeekRatio,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .testTag("waveform_canvas")
        )

        // Pan Bar when zoomed in
        if (waveform.zoomLevel > 1.05f) {
            val maxScroll = (1f - 1f / waveform.zoomLevel).coerceAtLeast(0.01f)
            Column {
                Text("הזזת ציר הזמן", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Slider(
                        value = waveform.scrollOffsetRatio,
                        onValueChange = onScrollChange,
                        valueRange = 0f..maxScroll,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Selection Range Slider Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val startMs = (waveform.selectionStartRatio * playback.durationMs).toLong()
                val endMs = (waveform.selectionEndRatio * playback.durationMs).toLong()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("טווח עריכה נבחר", style = MaterialTheme.typography.titleSmall)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = "${formatTimeMs(startMs)} → ${formatTimeMs(endMs)}",
                            style = StudioMonoStyle,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    RangeSlider(
                        value = waveform.selectionStartRatio..waveform.selectionEndRatio,
                        onValueChange = { range -> onSelectionChange(range.start, range.endInclusive) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth().testTag("waveform_range_slider")
                    )
                }
            }
        }

        // Trim / Cut / Fade / Merge Action Grid
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("פעולות עריכה מקצועיות", style = MaterialTheme.typography.titleSmall)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTrim,
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("waveform_trim_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("השאר טווח (Trim)")
                }
                OutlinedButton(
                    onClick = onCut,
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("waveform_cut_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("מחק קטע (Cut)")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("משך דעיכה:", style = MaterialTheme.typography.labelLarge)
                listOf(1f, 2f, 4f, 6f).forEach { sec ->
                    FilterChip(
                        selected = kotlin.math.abs(waveform.fadeDurationSec - sec) < 0.2f,
                        onClick = { onFadeDurationChange(sec) },
                        label = {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text("${sec.toInt()}s")
                            }
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onFadeIn,
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("waveform_fadein_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Fade In (${waveform.fadeDurationSec.toInt()}s)")
                }
                OutlinedButton(
                    onClick = onFadeOut,
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("waveform_fadeout_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.TrendingDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Fade Out (${waveform.fadeDurationSec.toInt()}s)")
                }
            }

            OutlinedButton(
                onClick = onAutoTrimSilence,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("waveform_auto_trim_silence_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("הסר שקט מהתחלה ומהסוף אוטומטית")
            }

            FilledTonalButton(
                onClick = { showMergePicker = true },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("waveform_merge_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.MergeType, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("חיבור ומיזוג רצועה נוספת (Merge / Join)")
            }
        }
    }

    if (showMergePicker) {
        AlertDialog(
            onDismissRequest = { showMergePicker = false },
            title = { Text("בחר רצועה לחיבור בסוף השיר") },
            text = {
                if (availableTracks.isEmpty()) {
                    Text("אין רצועות נוספות בספרייה לחיבור.")
                } else {
                    LazyColumn(
                        modifier = Modifier.height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableTracks, key = { it.id }) { t ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showMergePicker = false
                                        onMergeTrack(t)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(t.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(formatTimeMs(t.durationMs), style = StudioMonoStyle)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMergePicker = false }) { Text("סגור") }
            }
        )
    }
}
