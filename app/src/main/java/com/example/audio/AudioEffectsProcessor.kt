package com.example.audio

import com.example.data.EqAndFxState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Real-time Stereo Studio DSP Chain:
 * 1. Optional 80 Hz High-Pass Rumble Filter (for clean vocals & stage PA clarity)
 * 2. 3-Band Biquad EQ (Low Shelf @ 120 Hz, Mid Peak @ 1.2 kHz, High Shelf @ 6.5 kHz)
 * 3. Mid-Side Stereo Width (0% Mono .. 100% Natural .. 160% Wide Stage)
 * 4. Stereo Delay & Schroeder Reverb
 * 5. Auto-Normalize RMS Gain + Configurable Soft-Clip Brickwall Limiter Ceiling
 */
class AudioEffectsProcessor(private val sampleRate: Int = 44100) {
    private val hpfL = BiquadFilter().apply { setHighPass(80.0, sampleRate.toDouble(), 0.707) }
    private val hpfR = BiquadFilter().apply { copyFrom(hpfL) }
    private val lowL = BiquadFilter()
    private val lowR = BiquadFilter()
    private val midL = BiquadFilter()
    private val midR = BiquadFilter()
    private val highL = BiquadFilter()
    private val highR = BiquadFilter()

    // Delay line (280ms L, 370ms R)
    private val delaySizeL = (sampleRate * 0.28).toInt()
    private val delaySizeR = (sampleRate * 0.37).toInt()
    private val delayBufL = FloatArray(delaySizeL)
    private val delayBufR = FloatArray(delaySizeR)
    private var delayIdxL = 0
    private var delayIdxR = 0

    // Schroeder Reverb comb delays
    private val combDelays = intArrayOf(
        (sampleRate * 0.0297).toInt(),
        (sampleRate * 0.0371).toInt(),
        (sampleRate * 0.0411).toInt(),
        (sampleRate * 0.0437).toInt()
    )
    private val combL = Array(4) { FloatArray(combDelays[it]) }
    private val combR = Array(4) { FloatArray(combDelays[it] + 23) }
    private val combIdxL = IntArray(4)
    private val combIdxR = IntArray(4)
    private val combFb = floatArrayOf(0.805f, 0.827f, 0.783f, 0.764f)

    private var rmsEnvelope = 0.2f
    private var gateEnvelope = 1.0f
    private var lastBassDb = Float.NaN
    private var lastMidDb = Float.NaN
    private var lastTrebleDb = Float.NaN

    fun reset() {
        hpfL.reset(); hpfR.reset()
        lowL.reset(); lowR.reset()
        midL.reset(); midR.reset()
        highL.reset(); highR.reset()
        delayBufL.fill(0f); delayBufR.fill(0f)
        combL.forEach { it.fill(0f) }
        combR.forEach { it.fill(0f) }
        rmsEnvelope = 0.2f
        gateEnvelope = 1.0f
    }

    fun processBlock(
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        fx: EqAndFxState,
        limiterCeilingDb: Float = -0.5f,
        targetRms: Float = 0.24f,
        noiseGateEnabled: Boolean = false,
        noiseGateThresholdDb: Float = -50f
    ) {
        updateCoefficientsIfNeeded(fx)

        val reverbWet = fx.reverbMix.coerceIn(0f, 0.65f)
        val delayWet = fx.delayMix.coerceIn(0f, 0.55f)
        val width = fx.stereoWidth.coerceIn(0f, 1.6f)
        val applyEq = abs(fx.bassDb) > 0.05f || abs(fx.midDb) > 0.05f || abs(fx.trebleDb) > 0.05f
        val ceilingLinear = 10.0.pow(limiterCeilingDb.coerceIn(-3.0f, -0.05f) / 20.0).toFloat()
        val masterLinear = 10.0.pow(fx.masterGainDb.coerceIn(-6.0f, 4.0f) / 20.0).toFloat()
        val gateThreshLinear = 10.0.pow(noiseGateThresholdDb.coerceIn(-65f, -30f) / 20.0).toFloat()

        for (i in 0 until frames) {
            var l = left[i]
            var r = right[i]

            // 0. Optional Smooth Noise Gate
            if (noiseGateEnabled) {
                val level = maxOf(abs(l), abs(r))
                val targetGate = if (level >= gateThreshLinear) 1.0f else 0.08f
                val coef = if (targetGate > gateEnvelope) 0.06f else 0.003f
                gateEnvelope += (targetGate - gateEnvelope) * coef
                l *= gateEnvelope
                r *= gateEnvelope
            }

            // 1. Optional 80Hz High-Pass Rumble Filter
            if (fx.highPass80Hz) {
                l = hpfL.process(l)
                r = hpfR.process(r)
            }

            // 2. 3-Band EQ
            if (applyEq) {
                l = highL.process(midL.process(lowL.process(l)))
                r = highR.process(midR.process(lowR.process(r)))
            }

            // 3. Mid-Side Stereo Width
            if (abs(width - 1.0f) > 0.01f) {
                val mid = 0.5f * (l + r)
                val side = 0.5f * (l - r) * width
                l = mid + side
                r = mid - side
            }

            // 4. Stereo Delay
            if (delayWet > 0.005f) {
                val dL = delayBufL[delayIdxL]
                val dR = delayBufR[delayIdxR]
                delayBufL[delayIdxL] = l + dR * 0.34f
                delayBufR[delayIdxR] = r + dL * 0.34f
                delayIdxL = (delayIdxL + 1) % delaySizeL
                delayIdxR = (delayIdxR + 1) % delaySizeR
                l += dL * delayWet
                r += dR * delayWet
            }

            // 5. Stereo Schroeder Reverb
            if (reverbWet > 0.005f) {
                var revL = 0f
                var revR = 0f
                for (c in 0 until 4) {
                    val idxL = combIdxL[c]
                    val bufL = combL[c]
                    val outL = bufL[idxL]
                    bufL[idxL] = l + outL * combFb[c]
                    combIdxL[c] = (idxL + 1) % bufL.size
                    revL += outL

                    val idxR = combIdxR[c]
                    val bufR = combR[c]
                    val outR = bufR[idxR]
                    bufR[idxR] = r + outR * combFb[c]
                    combIdxR[c] = (idxR + 1) % bufR.size
                    revR += outR
                }
                l = l * (1f - reverbWet * 0.3f) + (revL * 0.25f) * reverbWet
                r = r * (1f - reverbWet * 0.3f) + (revR * 0.25f) * reverbWet
            }

            // 6. Master Output Gain + Auto-Normalize & Soft-Clip Brickwall Limiter
            l *= masterLinear
            r *= masterLinear
            if (fx.autoNormalizeLimiter) {
                val energy = 0.5f * (l * l + r * r)
                rmsEnvelope = rmsEnvelope * 0.9992f + energy * 0.0008f
                val rms = sqrt(rmsEnvelope.coerceAtLeast(1e-5f))
                val targetGain = (targetRms / rms).coerceIn(0.55f, 2.2f)
                l = softLimit(l * targetGain, ceilingLinear)
                r = softLimit(r * targetGain, ceilingLinear)
            } else {
                l = l.coerceIn(-1f, 1f)
                r = r.coerceIn(-1f, 1f)
            }

            left[i] = l
            right[i] = r
        }
    }

    private fun softLimit(x: Float, ceiling: Float): Float {
        val knee = ceiling * 0.85f
        return if (abs(x) <= knee) {
            x
        } else {
            val sign = if (x >= 0f) 1f else -1f
            val excess = abs(x) - knee
            val headroom = (ceiling - knee).coerceAtLeast(0.02f)
            sign * (knee + headroom * tanh(excess / headroom))
        }
    }

    private fun updateCoefficientsIfNeeded(fx: EqAndFxState) {
        if (fx.bassDb != lastBassDb) {
            lastBassDb = fx.bassDb
            lowL.setLowShelf(120.0, sampleRate.toDouble(), fx.bassDb.toDouble())
            lowR.copyFrom(lowL)
        }
        if (fx.midDb != lastMidDb) {
            lastMidDb = fx.midDb
            midL.setPeaking(1200.0, sampleRate.toDouble(), 0.9, fx.midDb.toDouble())
            midR.copyFrom(midL)
        }
        if (fx.trebleDb != lastTrebleDb) {
            lastTrebleDb = fx.trebleDb
            highL.setHighShelf(6500.0, sampleRate.toDouble(), fx.trebleDb.toDouble())
            highR.copyFrom(highL)
        }
    }

    private class BiquadFilter {
        private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
        private var a1 = 0f; private var a2 = 0f
        private var z1 = 0f; private var z2 = 0f

        fun reset() { z1 = 0f; z2 = 0f }

        fun copyFrom(other: BiquadFilter) {
            b0 = other.b0; b1 = other.b1; b2 = other.b2
            a1 = other.a1; a2 = other.a2
        }

        fun process(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }

        fun setHighPass(freq: Double, sr: Double, q: Double) {
            val w0 = 2.0 * PI * freq / sr
            val cosW = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            b0 = ((1.0 + cosW) / 2.0 / a0).toFloat()
            b1 = (-(1.0 + cosW) / a0).toFloat()
            b2 = ((1.0 + cosW) / 2.0 / a0).toFloat()
            a1 = ((-2.0 * cosW) / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
        }

        fun setLowShelf(freq: Double, sr: Double, gainDb: Double) {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freq / sr
            val cosW = cos(w0)
            val alpha = sin(w0) / 2.0 * sqrt(2.0)
            val sqrtA = sqrt(a)
            val a0 = (a + 1) + (a - 1) * cosW + 2 * sqrtA * alpha
            b0 = (a * ((a + 1) - (a - 1) * cosW + 2 * sqrtA * alpha) / a0).toFloat()
            b1 = (2 * a * ((a - 1) - (a + 1) * cosW) / a0).toFloat()
            b2 = (a * ((a + 1) - (a - 1) * cosW - 2 * sqrtA * alpha) / a0).toFloat()
            a1 = (-2 * ((a - 1) + (a + 1) * cosW) / a0).toFloat()
            a2 = (((a + 1) + (a - 1) * cosW - 2 * sqrtA * alpha) / a0).toFloat()
        }

        fun setHighShelf(freq: Double, sr: Double, gainDb: Double) {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freq / sr
            val cosW = cos(w0)
            val alpha = sin(w0) / 2.0 * sqrt(2.0)
            val sqrtA = sqrt(a)
            val a0 = (a + 1) - (a - 1) * cosW + 2 * sqrtA * alpha
            b0 = (a * ((a + 1) + (a - 1) * cosW + 2 * sqrtA * alpha) / a0).toFloat()
            b1 = (-2 * a * ((a - 1) + (a + 1) * cosW) / a0).toFloat()
            b2 = (a * ((a + 1) + (a - 1) * cosW - 2 * sqrtA * alpha) / a0).toFloat()
            a1 = (2 * ((a - 1) - (a + 1) * cosW) / a0).toFloat()
            a2 = (((a + 1) - (a - 1) * cosW - 2 * sqrtA * alpha) / a0).toFloat()
        }

        fun setPeaking(freq: Double, sr: Double, q: Double, gainDb: Double) {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freq / sr
            val cosW = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha / a
            b0 = ((1.0 + alpha * a) / a0).toFloat()
            b1 = ((-2.0 * cosW) / a0).toFloat()
            b2 = ((1.0 - alpha * a) / a0).toFloat()
            a1 = ((-2.0 * cosW) / a0).toFloat()
            a2 = ((1.0 - alpha / a) / a0).toFloat()
        }
    }
}
