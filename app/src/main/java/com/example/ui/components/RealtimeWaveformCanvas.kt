package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.audio.AudioEngineController
import com.example.data.VisualizerStyle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldDrum
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.WarmAmber
import kotlin.math.abs

/**
 * High-performance real-time audio waveform visualizer Composable.
 *
 * Optimization techniques:
 * 1. State read deferral: [samplesProvider] is invoked strictly inside the Draw phase
 *    (`onDrawBehind`), completely skipping Compose Recomposition and Layout on 40-60fps updates.
 * 2. Zero per-frame heap allocations: [Path], [Stroke], and [Brush] objects are allocated once
 *    inside [drawWithCache] when size/colors change, and [smoothedSamples] reuses a fixed array.
 */
@Composable
fun RealtimeWaveformCanvas(
    samplesProvider: () -> FloatArray,
    isActive: Boolean,
    style: VisualizerStyle = VisualizerStyle.HYBRID,
    labelHe: String = "תצוגת גל קול בזמן אמת",
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    // Pre-allocated smoothing buffer reused across every draw pass
    val smoothedSamples = remember { FloatArray(AudioEngineController.LIVE_WAVEFORM_SIZE) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0A0F19))
            .border(1.dp, gridColor, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = labelHe,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                color = if (isActive) EmeraldDrum.copy(alpha = 0.16f) else gridColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(50.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isActive) EmeraldDrum else MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = if (isActive) "LIVE" else "IDLE",
                            style = StudioMonoStyle,
                            color = if (isActive) EmeraldDrum else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .semantics { contentDescription = labelHe }
                    .testTag("realtime_waveform_canvas")
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val centerY = h / 2f
                        val quarterY = h * 0.25f
                        val threeQuarterY = h * 0.75f

                        // Pre-allocate Paths & Brushes once per layout size change
                        val oscPath = Path()
                        val fillPath = Path()
                        val strokeStyle = Stroke(
                            width = 2.2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                        val waveBrush = Brush.horizontalGradient(
                            colors = listOf(ElectricCyan, StudioViolet, WarmAmber)
                        )
                        val barsBrush = Brush.verticalGradient(
                            colors = listOf(ElectricCyan, StudioViolet, ElectricCyan)
                        )
                        val areaBrush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.28f),
                                tertiaryColor.copy(alpha = 0.08f),
                                secondaryColor.copy(alpha = 0.28f)
                            )
                        )
                        val barColorActive = primaryColor.copy(alpha = 0.36f)
                        val barColorIdle = gridColor.copy(alpha = 0.45f)
                        val gridStrokePx = 1.dp.toPx()

                        onDrawBehind {
                            // 1. Static Studio Grid
                            drawLine(gridColor.copy(alpha = 0.55f), Offset(0f, centerY), Offset(w, centerY), gridStrokePx)
                            drawLine(gridColor.copy(alpha = 0.22f), Offset(0f, quarterY), Offset(w, quarterY), gridStrokePx)
                            drawLine(gridColor.copy(alpha = 0.22f), Offset(0f, threeQuarterY), Offset(w, threeQuarterY), gridStrokePx)

                            // 2. Read live audio samples ONLY in Draw phase
                            val raw = samplesProvider()
                            val count = minOf(raw.size, smoothedSamples.size)
                            if (count < 2) return@onDrawBehind

                            val dx = w / (count - 1).toFloat()
                            val barWidth = (dx * 0.56f).coerceAtLeast(1.8.dp.toPx())
                            val maxAmp = h * 0.44f

                            // 3. Smooth samples in-place (attack fast, release smooth)
                            for (i in 0 until count) {
                                val target = raw[i]
                                val prev = smoothedSamples[i]
                                smoothedSamples[i] = if (abs(target) > abs(prev)) {
                                    prev + (target - prev) * 0.65f
                                } else {
                                    prev * 0.78f + target * 0.22f
                                }
                            }

                            val drawBars = style != VisualizerStyle.WAVE
                            val drawWave = style != VisualizerStyle.BARS

                            if (drawWave) {
                                oscPath.reset()
                                fillPath.reset()
                                val firstY = centerY - smoothedSamples[0] * maxAmp
                                oscPath.moveTo(0f, firstY)
                                fillPath.moveTo(0f, centerY)
                                fillPath.lineTo(0f, firstY)
                            }

                            for (i in 0 until count) {
                                val x = i * dx
                                val sample = smoothedSamples[i]
                                val y = centerY - sample * maxAmp
                                val mag = (abs(sample) * maxAmp).coerceAtLeast(2.dp.toPx())

                                if (drawBars) {
                                    if (style == VisualizerStyle.BARS) {
                                        drawLine(
                                            brush = barsBrush,
                                            alpha = if (isActive) 0.92f else 0.32f,
                                            start = Offset(x, centerY - mag),
                                            end = Offset(x, centerY + mag),
                                            strokeWidth = barWidth,
                                            cap = StrokeCap.Round
                                        )
                                    } else {
                                        drawLine(
                                            color = if (isActive) barColorActive else barColorIdle,
                                            start = Offset(x, centerY - mag),
                                            end = Offset(x, centerY + mag),
                                            strokeWidth = barWidth * 0.85f,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }

                                if (drawWave && i > 0) {
                                    val prevX = (i - 1) * dx
                                    val prevY = centerY - smoothedSamples[i - 1] * maxAmp
                                    val midX = (prevX + x) * 0.5f
                                    oscPath.cubicTo(midX, prevY, midX, y, x, y)
                                    fillPath.cubicTo(midX, prevY, midX, y, x, y)
                                }
                            }

                            if (drawWave) {
                                fillPath.lineTo(w, centerY)
                                fillPath.close()
                                drawPath(path = fillPath, brush = areaBrush)
                                drawPath(path = oscPath, brush = waveBrush, style = strokeStyle)
                            }
                        }
                    }
            )
        }
        Spacer(Modifier.height(0.dp))
    }
}
