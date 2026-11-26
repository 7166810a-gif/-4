package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.EqAndFxState
import com.example.data.EqPreset
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldDrum
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.WarmAmber
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EffectsScreen(
    fx: EqAndFxState,
    onBassChange: (Float) -> Unit,
    onMidChange: (Float) -> Unit,
    onTrebleChange: (Float) -> Unit,
    onSelectEqPreset: (EqPreset) -> Unit,
    onToggleAutoNormalize: (Boolean) -> Unit,
    onReverbChange: (Float) -> Unit,
    onDelayChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Visual EQ Curve + Presets + 3-Band Controls
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
                    Icon(Icons.Rounded.Equalizer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("אקולייזר רב-ערוצי (Studio EQ)", style = MaterialTheme.typography.titleMedium)
                }

                // Frequency Curve Canvas
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val gridColor = MaterialTheme.colorScheme.outline
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0A0F19))
                            .border(1.dp, gridColor, RoundedCornerShape(14.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            val w = size.width
                            val h = size.height
                            val midY = h / 2f

                            drawLine(gridColor.copy(alpha = 0.5f), Offset(0f, midY), Offset(w, midY), 1.dp.toPx())
                            drawLine(gridColor.copy(alpha = 0.25f), Offset(w * 0.25f, 0f), Offset(w * 0.25f, h), 1.dp.toPx())
                            drawLine(gridColor.copy(alpha = 0.25f), Offset(w * 0.50f, 0f), Offset(w * 0.50f, h), 1.dp.toPx())
                            drawLine(gridColor.copy(alpha = 0.25f), Offset(w * 0.75f, 0f), Offset(w * 0.75f, h), 1.dp.toPx())

                            val yBass = midY - (fx.bassDb / 12f) * (h * 0.40f)
                            val yMid = midY - (fx.midDb / 12f) * (h * 0.40f)
                            val yTreble = midY - (fx.trebleDb / 12f) * (h * 0.40f)

                            val path = Path().apply {
                                moveTo(0f, yBass)
                                cubicTo(w * 0.22f, yBass, w * 0.30f, yMid, w * 0.50f, yMid)
                                cubicTo(w * 0.70f, yMid, w * 0.80f, yTreble, w, yTreble)
                            }
                            drawPath(
                                path = path,
                                brush = Brush.horizontalGradient(listOf(WarmAmber, ElectricCyan, StudioViolet)),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            drawCircle(WarmAmber, 5.dp.toPx(), Offset(w * 0.14f, yBass))
                            drawCircle(ElectricCyan, 5.dp.toPx(), Offset(w * 0.50f, yMid))
                            drawCircle(StudioViolet, 5.dp.toPx(), Offset(w * 0.86f, yTreble))
                        }
                    }
                }

                // EQ Presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EqPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = fx.activeEqPreset == preset,
                            onClick = { onSelectEqPreset(preset) },
                            label = { Text(preset.titleHe) },
                            modifier = Modifier.testTag("eq_preset_${preset.name.lowercase()}")
                        )
                    }
                }

                EqBandSlider("תדרים נמוכים (Bass 120Hz)", fx.bassDb, WarmAmber, onBassChange, "eq_bass_slider")
                EqBandSlider("תדרים בינוניים (Mid 1.2kHz)", fx.midDb, ElectricCyan, onMidChange, "eq_mid_slider")
                EqBandSlider("תדרים גבוהים (Treble 6.5kHz)", fx.trebleDb, StudioViolet, onTrebleChange, "eq_treble_slider")
            }
        }

        // 2. Auto-Normalize & Limiter
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = EmeraldDrum, modifier = Modifier.size(28.dp))
                    Column {
                        Text("מאזן עוצמה ומגביל עיוותים", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Auto-Normalize & Brickwall Limiter למניעת קטיעות שמע ועוצמה אחידה",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = fx.autoNormalizeLimiter,
                    onCheckedChange = onToggleAutoNormalize,
                    modifier = Modifier.testTag("auto_normalize_switch")
                )
            }
        }

        // 3. Space & Depth Effects (Reverb & Delay)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.SurroundSound, contentDescription = null, tint = StudioViolet)
                    Text("אפקטים של חלל ועומק (Reverb & Delay)", style = MaterialTheme.typography.titleMedium)
                }

                SpaceEffectSlider("הדהוד אולם (Reverb)", fx.reverbMix / 0.6f, StudioViolet, { onReverbChange(it * 0.6f) }, "fx_reverb_slider")
                SpaceEffectSlider("דיליי סטריאופוני (Delay)", fx.delayMix / 0.5f, ElectricCyan, { onDelayChange(it * 0.5f) }, "fx_delay_slider")
            }
        }
    }
}

@Composable
private fun EqBandSlider(
    label: String,
    dbValue: Float,
    accent: Color,
    onChange: (Float) -> Unit,
    tag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                val formatted = String.format(Locale.US, "%+.1f dB", dbValue)
                Text(formatted, style = StudioMonoStyle, color = accent)
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Slider(
                value = dbValue,
                onValueChange = onChange,
                valueRange = -12f..12f,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                modifier = Modifier.fillMaxWidth().height(32.dp).testTag(tag)
            )
        }
    }
}

@Composable
private fun SpaceEffectSlider(
    label: String,
    normalized: Float,
    accent: Color,
    onChange: (Float) -> Unit,
    tag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text("${(normalized * 100).roundToInt()}%", style = StudioMonoStyle, color = accent)
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Slider(
                value = normalized.coerceIn(0f, 1f),
                onValueChange = onChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                modifier = Modifier.fillMaxWidth().height(32.dp).testTag(tag)
            )
        }
    }
}
