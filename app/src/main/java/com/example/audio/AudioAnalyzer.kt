package com.example.audio

import com.example.data.AudioAnalysisResult
import com.example.data.AudioBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 100% Offline Audio Analyzer:
 * - Estimates BPM via energy onset flux autocorrelation (60..180 BPM range)
 * - Estimates Musical Key via Krumhansl-Schmuckler 12-pitch-class chroma profile correlation
 */
object AudioAnalyzer {
    private val NOTE_NAMES = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

    // Krumhansl-Schmuckler key profiles
    private val MAJOR_PROFILE = floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
    private val MINOR_PROFILE = floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)

    suspend fun analyze(buffer: AudioBuffer): AudioAnalysisResult = withContext(Dispatchers.Default) {
        if (buffer.frameCount < buffer.sampleRate / 2) {
            return@withContext AudioAnalysisResult()
        }
        val sr = buffer.sampleRate
        val maxAnalyzeFrames = min(buffer.frameCount, sr * 35) // analyze up to first 35s for instant speed

        val bpm = estimateBpm(buffer, maxAnalyzeFrames, sr)
        val (rootIdx, isMinor) = estimateKey(buffer, maxAnalyzeFrames, sr)
        val label = if (rootIdx in 0..11) {
            "${NOTE_NAMES[rootIdx]}${if (isMinor) "m" else ""}"
        } else {
            "—"
        }
        AudioAnalysisResult(
            detectedBpm = bpm,
            rootPitchClass = rootIdx,
            isMinor = isMinor,
            detectedKeyLabel = label
        )
    }

    fun formatTransposedKey(analysis: AudioAnalysisResult, semitones: Int): String {
        if (analysis.rootPitchClass !in 0..11) return "—"
        val shifted = ((analysis.rootPitchClass + semitones) % 12 + 12) % 12
        return "${NOTE_NAMES[shifted]}${if (analysis.isMinor) "m" else ""}"
    }

    private fun estimateBpm(buffer: AudioBuffer, frames: Int, sr: Int): Int {
        val hop = 512
        val numFrames = frames / hop
        if (numFrames < 32) return 110

        val energies = FloatArray(numFrames)
        for (f in 0 until numFrames) {
            val start = f * hop
            var sum = 0f
            for (i in start until min(frames, start + hop)) {
                val m = 0.5f * (buffer.left[i] + buffer.right[i])
                sum += m * m
            }
            energies[f] = sqrt(sum / hop)
        }

        // Half-wave rectified onset difference
        val onset = FloatArray(numFrames)
        for (f in 1 until numFrames) {
            onset[f] = max(0f, energies[f] - energies[f - 1])
        }

        val frameRate = sr.toDouble() / hop
        val minLag = ((60.0 / 180.0) * frameRate).toInt().coerceAtLeast(1)
        val maxLag = ((60.0 / 65.0) * frameRate).toInt().coerceAtMost(numFrames / 2)
        if (maxLag <= minLag) return 110

        var bestLag = minLag
        var bestScore = -1f
        for (lag in minLag..maxLag) {
            var corr = 0f
            val limit = numFrames - lag
            for (i in 0 until limit) {
                corr += onset[i] * onset[i + lag]
            }
            val norm = corr / max(1, limit)
            if (norm > bestScore) {
                bestScore = norm
                bestLag = lag
            }
        }
        val rawBpm = (60.0 * frameRate / bestLag).roundToInt()
        return rawBpm.coerceIn(60, 180)
    }

    private fun estimateKey(buffer: AudioBuffer, frames: Int, sr: Int): Pair<Int, Boolean> {
        val fftSize = 4096
        val fft = FftProcessor(fftSize)
        val win = fft.hannWindow
        val bins = fftSize / 2 + 1
        val freqPerBin = sr.toDouble() / fftSize

        val chroma = FloatArray(12)
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)

        val step = max(fftSize, frames / 24)
        var pos = 0
        while (pos + fftSize <= frames) {
            for (i in 0 until fftSize) {
                re[i] = 0.5f * (buffer.left[pos + i] + buffer.right[pos + i]) * win[i]
                im[i] = 0f
            }
            fft.fft(re, im)
            for (k in 1 until bins) {
                val freq = k * freqPerBin
                if (freq in 65.0..1800.0) {
                    val mag = sqrt(re[k] * re[k] + im[k] * im[k])
                    val midi = 69.0 + 12.0 * (ln(freq / 440.0) / ln(2.0))
                    val pitchClass = ((midi.roundToInt() % 12) + 12) % 12
                    chroma[pitchClass] += mag
                }
            }
            pos += step
        }

        val maxVal = chroma.maxOrNull() ?: 0f
        if (maxVal <= 1e-6f) return -1 to false
        for (i in 0 until 12) chroma[i] /= maxVal

        var bestRoot = 0
        var bestIsMinor = false
        var bestCorr = Float.NEGATIVE_INFINITY

        for (root in 0 until 12) {
            var majCorr = 0f
            var minCorr = 0f
            for (i in 0 until 12) {
                val c = chroma[(root + i) % 12]
                majCorr += c * MAJOR_PROFILE[i]
                minCorr += c * MINOR_PROFILE[i]
            }
            if (majCorr > bestCorr) {
                bestCorr = majCorr
                bestRoot = root
                bestIsMinor = false
            }
            if (minCorr > bestCorr) {
                bestCorr = minCorr
                bestRoot = root
                bestIsMinor = true
            }
        }
        return bestRoot to bestIsMinor
    }
}
