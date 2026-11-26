package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.PlaybackUiState
import com.example.data.RehearsalState
import com.example.data.VisualizerStyle
import com.example.ui.components.RealtimeWaveformCanvas
import com.example.ui.components.formatTimeMs
import com.example.ui.theme.EmeraldDrum
import com.example.ui.theme.PeakRed
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.WarmAmber
import kotlin.math.roundToInt

@Composable
fun RehearsalScreen(
    hasAudio: Boolean,
    playback: PlaybackUiState,
    rehearsal: RehearsalState,
    visualizerStyle: VisualizerStyle,
    liveSamplesProvider: () -> FloatArray,
    onSetLoopA: () -> Unit,
    onSetLoopB: () -> Unit,
    onToggleLoop: () -> Unit,
    onClearLoop: () -> Unit,
    onToggleMetronome: (Boolean) -> Unit,
    onBpmChange: (Int) -> Unit,
    onTapTempo: () -> Unit,
    onToggleCountIn: (Boolean) -> Unit,
    onMetronomeVolumeChange: (Float) -> Unit,
    onPlayWithCountIn: () -> Unit,
    onToggleOverdubRecord: () -> Unit,
    onOverdubGainChange: (Float) -> Unit,
    onClearOverdub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var micPermissionDenied by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micPermissionDenied = false
            onToggleOverdubRecord()
        } else {
            micPermissionDenied = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. A-B Loop Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Repeat, contentDescription = null, tint = WarmAmber)
                        Text("לולאת חזרות (A-B Loop)", style = MaterialTheme.typography.titleMedium)
                    }
                    Switch(
                        checked = rehearsal.loopEnabled,
                        onCheckedChange = { onToggleLoop() },
                        enabled = hasAudio,
                        modifier = Modifier.testTag("ab_loop_switch")
                    )
                }

                val aMs = (rehearsal.loopARatio * playback.durationMs).toLong()
                val bMs = (rehearsal.loopBRatio * playback.durationMs).toLong()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onSetLoopA,
                        enabled = hasAudio,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("set_loop_a_button")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("סמן A")
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(formatTimeMs(aMs), style = StudioMonoStyle, color = WarmAmber)
                            }
                        }
                    }
                    FilledTonalButton(
                        onClick = onSetLoopB,
                        enabled = hasAudio,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("set_loop_b_button")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("סמן B")
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(formatTimeMs(bMs), style = StudioMonoStyle, color = WarmAmber)
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onClearLoop,
                        enabled = hasAudio,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_loop_button")
                    ) {
                        Text("אפס")
                    }
                }
            }
        }

        // 2. Built-in Metronome Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.AvTimer, contentDescription = null, tint = EmeraldDrum)
                        Text("מטרונום אולפני (BPM)", style = MaterialTheme.typography.titleMedium)
                    }
                    Switch(
                        checked = rehearsal.metronomeEnabled,
                        onCheckedChange = onToggleMetronome,
                        modifier = Modifier.testTag("metronome_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = { onBpmChange(rehearsal.bpm - 1) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) { Text("-") }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text("${rehearsal.bpm} BPM", style = StudioMonoStyle, color = EmeraldDrum)
                        }
                        FilledTonalButton(
                            onClick = { onBpmChange(rehearsal.bpm + 1) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) { Text("+") }
                    }

                    OutlinedButton(
                        onClick = onTapTempo,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("tap_tempo_button")
                    ) {
                        Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tap Tempo")
                    }
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Slider(
                        value = rehearsal.bpm.toFloat(),
                        onValueChange = { onBpmChange(it.roundToInt()) },
                        valueRange = 40f..240f,
                        colors = SliderDefaults.colors(thumbColor = EmeraldDrum, activeTrackColor = EmeraldDrum),
                        modifier = Modifier.fillMaxWidth().testTag("metronome_bpm_slider")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ספירה מקדימה (4 פעימות Count-In)", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = rehearsal.countInEnabled,
                        onCheckedChange = onToggleCountIn,
                        modifier = Modifier.testTag("count_in_switch")
                    )
                }

                if (rehearsal.metronomeEnabled && rehearsal.countInEnabled && hasAudio) {
                    FilledTonalButton(
                        onClick = onPlayWithCountIn,
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                    ) {
                        Text("נגן עם ספירה מקדימה (1-2-3-4)")
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("עוצמת מטרונום", style = MaterialTheme.typography.bodyMedium)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text("${(rehearsal.metronomeVolume * 100).roundToInt()}%", style = StudioMonoStyle)
                    }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Slider(
                        value = rehearsal.metronomeVolume,
                        onValueChange = onMetronomeVolumeChange,
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth().height(30.dp)
                    )
                }
            }
        }

        // 3. Direct Overdub Recording Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = null, tint = PeakRed)
                    Text("הקלטה על גבי הפלייבק (Overdub)", style = MaterialTheme.typography.titleMedium)
                }

                Text(
                    if (hasAudio) "הקליטו שירה או כלי נגינה ישירות מהמיקרופון תוך כדי השמעת הפלייבק, ובצעו מיקס מאוזן."
                    else "אפשר גם להקליט סקיצה קולית נקייה ישירות אל תוך הספרייה.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                RealtimeWaveformCanvas(
                    samplesProvider = liveSamplesProvider,
                    isActive = playback.isPlaying || rehearsal.isRecordingOverdub,
                    style = visualizerStyle,
                    labelHe = "גל קול חי (מיקרופון / מיקס)"
                )

                // Live Mic VU Meter
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A0F19))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(rehearsal.micLevel.coerceIn(0.02f, 1f))
                            .background(if (rehearsal.micLevel > 0.85f) PeakRed else EmeraldDrum)
                    )
                }

                Button(
                    onClick = {
                        if (rehearsal.isRecordingOverdub) {
                            onToggleOverdubRecord()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                onToggleOverdubRecord()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (rehearsal.isRecordingOverdub) PeakRed else MaterialTheme.colorScheme.secondary,
                        contentColor = if (rehearsal.isRecordingOverdub) Color.White else MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("overdub_record_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = if (rehearsal.isRecordingOverdub) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                        contentDescription = stringResource(R.string.cd_record_overdub)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (rehearsal.isRecordingOverdub) "עצור הקלטה ושלב במיקס" else "התחל הקלטת Overdub",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (micPermissionDenied) {
                    Text(
                        "נדרשת הרשאת מיקרופון כדי להקליט שירה או כלי נגינה. ניתן לאשר בלחיצה חוזרת או בהגדרות האפליקציה.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PeakRed
                    )
                }

                if (rehearsal.hasOverdubTrack) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("עוצמת ערוץ ההקלטה (Overdub Mix)", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text("${(rehearsal.overdubGain * 100).roundToInt()}%", style = StudioMonoStyle)
                            }
                            IconButton(
                                onClick = onClearOverdub,
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_overdub_button")
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "נקה הקלטה")
                            }
                        }
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Slider(
                            value = rehearsal.overdubGain,
                            onValueChange = onOverdubGainChange,
                            valueRange = 0f..1.8f,
                            modifier = Modifier.fillMaxWidth().testTag("overdub_gain_slider")
                        )
                    }
                }
            }
        }
    }
}
