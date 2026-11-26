package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.MixerState
import com.example.data.PitchTempoState
import com.example.data.ProcessingQuality
import com.example.data.StemPreset
import com.example.ui.components.RealtimeWaveformCanvas
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldDrum
import com.example.ui.theme.PeakRed
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.WarmAmber
import kotlin.math.roundToInt

@Composable
fun MixerScreen(
    hasAudio: Boolean,
    isPlaying: Boolean,
    liveSamplesProvider: () -> FloatArray,
    mixer: MixerState,
    pitchTempo: PitchTempoState,
    onSeparateClick: () -> Unit,
    onQualityChange: (ProcessingQuality) -> Unit,
    onPresetClick: (StemPreset) -> Unit,
    onVocalTailToggle: (Boolean) -> Unit,
    onGainChange: (Int, Float) -> Unit,
    onToggleMute: (Int) -> Unit,
    onToggleSolo: (Int) -> Unit,
    onPitchSemitonesChange: (Int) -> Unit,
    onPitchFineCentsChange: (Int) -> Unit,
    onTempoChange: (Float) -> Unit,
    onResetPitchTempo: () -> Unit,
    onGoToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                    Text("בחר קובץ שמע כדי להפעיל את המיקסר", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "ייבא שיר מהמכשיר או צור לופ אימון בספרייה ליצירת פלייבק והפרדת 4 ערוצים.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onGoToLibrary) {
                        Text("עבור לספרייה")
                    }
                }
            }
            return
        }

        // 0. Real-Time Live Audio Waveform Visualizer
        RealtimeWaveformCanvas(
            samplesProvider = liveSamplesProvider,
            isActive = isPlaying,
            labelHe = "אוסילוסקופ וגל קול חי (Real-Time)"
        )

        // 1. One-Click Offline Stem Separation Card
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text("הפרדת ערוצים חכמה (4-Stem)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (mixer.stemsSeparated) "הערוצים מופרדים ומוכנים לשליטה חיה"
                            else "לחיצה אחת להפרדת שירה, כלים, בס ותופים מקומית",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (mixer.stemsSeparated) EmeraldDrum else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Processing Quality Selector
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ProcessingQuality.entries.forEachIndexed { idx, q ->
                        SegmentedButton(
                            selected = mixer.qualityMode == q,
                            onClick = { onQualityChange(q) },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = ProcessingQuality.entries.size)
                        ) {
                            Text(q.titleHe)
                        }
                    }
                }

                Button(
                    onClick = onSeparateClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("separate_stems_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(vertical = 13.dp)
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (mixer.stemsSeparated) "הפרד מחדש באיכות הנבחרת" else "צור פלייבק והפרד ערוצים בלחיצה",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ניקוי זנבות שירה ודהוד", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "הפחתת שאריות קול והדהוד לאחר הסרת השירה",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = mixer.vocalTailCleanup,
                        onCheckedChange = onVocalTailToggle,
                        modifier = Modifier.testTag("vocal_tail_cleanup_switch")
                    )
                }
            }
        }

        // 2. Ready-made Presets
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("מצבי עבודה מוכנים מראש", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    StemPreset.FULL_PLAYBACK,
                    StemPreset.RHYTHM_PLAYBACK,
                    StemPreset.HARMONIC_PLAYBACK,
                    StemPreset.VOCALS_ONLY
                ).forEach { preset ->
                    FilterChip(
                        selected = mixer.activePreset == preset,
                        onClick = { onPresetClick(preset) },
                        enabled = mixer.stemsSeparated,
                        label = {
                            Column {
                                Text(preset.titleHe, fontWeight = FontWeight.SemiBold)
                                Text(preset.subtitleHe, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        modifier = Modifier.testTag("stem_preset_${preset.name.lowercase()}")
                    )
                }
            }
        }

        // 3. 4-Stem Mixer Channel Strips
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
                Text("מערבל ערוצים (4-Stem Mixer)", style = MaterialTheme.typography.titleMedium)

                StemChannelRow(
                    title = "שירה (Vocals)",
                    icon = Icons.Rounded.Mic,
                    accent = ElectricCyan,
                    gain = mixer.vocalsGain,
                    muted = mixer.vocalsMute,
                    solo = mixer.vocalsSolo,
                    enabled = mixer.stemsSeparated,
                    onGainChange = { onGainChange(0, it) },
                    onToggleMute = { onToggleMute(0) },
                    onToggleSolo = { onToggleSolo(0) },
                    tagPrefix = "stem_vocals"
                )
                StemChannelRow(
                    title = "ליווי והרמוניה (Instruments)",
                    icon = Icons.Rounded.Piano,
                    accent = StudioViolet,
                    gain = mixer.accompGain,
                    muted = mixer.accompMute,
                    solo = mixer.accompSolo,
                    enabled = mixer.stemsSeparated,
                    onGainChange = { onGainChange(1, it) },
                    onToggleMute = { onToggleMute(1) },
                    onToggleSolo = { onToggleSolo(1) },
                    tagPrefix = "stem_accomp"
                )
                StemChannelRow(
                    title = "בס (Bass)",
                    icon = Icons.Rounded.MusicNote,
                    accent = WarmAmber,
                    gain = mixer.bassGain,
                    muted = mixer.bassMute,
                    solo = mixer.bassSolo,
                    enabled = mixer.stemsSeparated,
                    onGainChange = { onGainChange(2, it) },
                    onToggleMute = { onToggleMute(2) },
                    onToggleSolo = { onToggleSolo(2) },
                    tagPrefix = "stem_bass"
                )
                StemChannelRow(
                    title = "תופים וקצב (Drums)",
                    icon = Icons.Rounded.GraphicEq,
                    accent = EmeraldDrum,
                    gain = mixer.drumsGain,
                    muted = mixer.drumsMute,
                    solo = mixer.drumsSolo,
                    enabled = mixer.stemsSeparated,
                    onGainChange = { onGainChange(3, it) },
                    onToggleMute = { onToggleMute(3) },
                    onToggleSolo = { onToggleSolo(3) },
                    tagPrefix = "stem_drums"
                )
            }
        }

        // 4. Real-Time Pitch & Tempo Card
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
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("סולם (Pitch) ומהירות (Tempo) בזמן אמת", style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(
                        onClick = onResetPitchTempo,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reset_pitch_tempo_button")
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = "אפס סולם ומהירות")
                    }
                }

                // Pitch Semitones -12 .. +12
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("שינוי סולם (חצאי טונים)", style = MaterialTheme.typography.titleSmall)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        val sign = if (pitchTempo.semitones > 0) "+" else ""
                        Text(
                            text = "$sign${pitchTempo.semitones} st",
                            style = StudioMonoStyle,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onPitchSemitonesChange(pitchTempo.semitones - 1) },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("pitch_minus_button")
                    ) {
                        Text("-1")
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Slider(
                            value = pitchTempo.semitones.toFloat(),
                            onValueChange = { onPitchSemitonesChange(it.roundToInt()) },
                            valueRange = -12f..12f,
                            steps = 23,
                            modifier = Modifier.weight(1f).testTag("pitch_semitones_slider")
                        )
                    }
                    FilledTonalButton(
                        onClick = { onPitchSemitonesChange(pitchTempo.semitones + 1) },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("pitch_plus_button")
                    ) {
                        Text("+1")
                    }
                }

                // Fine-Tune Cents -50 .. +50
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("כוונון עדין (Fine-Tune)", style = MaterialTheme.typography.bodyMedium)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        val sign = if (pitchTempo.fineCents > 0) "+" else ""
                        Text(
                            text = "$sign${pitchTempo.fineCents} cents",
                            style = StudioMonoStyle,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Slider(
                        value = pitchTempo.fineCents.toFloat(),
                        onValueChange = { onPitchFineCentsChange(it.roundToInt()) },
                        valueRange = -50f..50f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("pitch_fine_slider")
                    )
                }

                // Tempo 50% .. 150%
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(18.dp), tint = EmeraldDrum)
                        Text("מהירות נגינה (ללא שינוי טון)", style = MaterialTheme.typography.titleSmall)
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = "${(pitchTempo.tempoRatio * 100).roundToInt()}%",
                            style = StudioMonoStyle,
                            color = EmeraldDrum
                        )
                    }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Slider(
                        value = pitchTempo.tempoRatio,
                        onValueChange = onTempoChange,
                        valueRange = 0.50f..1.50f,
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldDrum,
                            activeTrackColor = EmeraldDrum
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("tempo_slider")
                    )
                }
            }
        }
    }
}

@Composable
private fun StemChannelRow(
    title: String,
    icon: ImageVector,
    accent: Color,
    gain: Float,
    muted: Boolean,
    solo: Boolean,
    enabled: Boolean,
    onGainChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    tagPrefix: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmall)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = "${(gain * 100).roundToInt()}%",
                        style = StudioMonoStyle,
                        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else accent
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (muted) PeakRed else MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onToggleMute,
                    enabled = enabled,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("${tagPrefix}_mute")
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "M",
                            style = StudioMonoStyle,
                            color = if (muted) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (solo) WarmAmber else MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onToggleSolo,
                    enabled = enabled,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("${tagPrefix}_solo")
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "S",
                            style = StudioMonoStyle,
                            color = if (solo) Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Slider(
                value = gain,
                onValueChange = onGainChange,
                enabled = enabled && !muted,
                valueRange = 0f..1.5f,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .testTag("${tagPrefix}_gain")
            )
        }
    }
}
