package com.example.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Independent Pitch Shifting (-12..+12 semitones + cents) and Tempo Scaling (0.5x..1.5x)
 * using Overlap-Add WSOLA time-scaling + Cubic Hermite resampling.
 * Works both for real-time streaming playback and offline export rendering.
 */
class SonicTimePitchProcessor(private val sampleRate: Int = 44100) {
    private val maxPeriod = (sampleRate / 55).coerceAtLeast(128)   // ~800 samples (~18ms)
    private val minPeriod = (sampleRate / 400).coerceAtLeast(32)   // ~110 samples
    private val winSize = maxPeriod * 2                            // ~36ms overlap window

    private var inCap = maxPeriod * 8
    private var inL = FloatArray(inCap)
    private var inR = FloatArray(inCap)
    private var inCount = 0

    private var outCap = maxPeriod * 8
    private var outL = FloatArray(outCap)
    private var outR = FloatArray(outCap)
    private var outCount = 0

    private var pitchBufL = FloatArray(maxPeriod * 4)
    private var pitchBufR = FloatArray(maxPeriod * 4)
    private var pitchCount = 0

    var tempo: Float = 1.0f
    var pitch: Float = 1.0f

    fun reset() {
        inCount = 0
        outCount = 0
        pitchCount = 0
    }

    fun writeStereo(left: FloatArray, right: FloatArray, frames: Int) {
        if (frames <= 0) return
        ensureInCapacity(inCount + frames)
        System.arraycopy(left, 0, inL, inCount, frames)
        System.arraycopy(right, 0, inR, inCount, frames)
        inCount += frames
        processInternal()
    }

    fun readStereo(destL: FloatArray, destR: FloatArray, maxFrames: Int): Int {
        val toCopy = min(maxFrames, outCount)
        if (toCopy <= 0) return 0
        System.arraycopy(outL, 0, destL, 0, toCopy)
        System.arraycopy(outR, 0, destR, 0, toCopy)
        val rem = outCount - toCopy
        if (rem > 0) {
            System.arraycopy(outL, toCopy, outL, 0, rem)
            System.arraycopy(outR, toCopy, outR, 0, rem)
        }
        outCount = rem
        return toCopy
    }

    fun flush(): Int {
        // Pad remaining input with zeros to drain window
        val pad = maxPeriod * 2
        ensureInCapacity(inCount + pad)
        for (i in inCount until inCount + pad) {
            inL[i] = 0f
            inR[i] = 0f
        }
        inCount += pad
        processInternal()
        return outCount
    }

    private fun processInternal() {
        val t = tempo.coerceIn(0.45f, 1.60f)
        val p = pitch.coerceIn(0.45f, 2.10f)

        // Bypass cleanly when unity
        if (abs(t - 1.0f) < 0.002f && abs(p - 1.0f) < 0.002f) {
            appendOutput(inL, inR, inCount)
            inCount = 0
            return
        }

        // Combined speed factor for WSOLA time-scale before resampling for pitch
        // If pitch = p, resampling by p scales duration by 1/p, so WSOLA must stretch by speed = t / p
        val timeStretchSpeed = (t / p).coerceIn(0.25f, 3.5f)

        if (abs(timeStretchSpeed - 1.0f) < 0.003f) {
            appendPitchInput(inL, inR, inCount)
            inCount = 0
        } else {
            runWsola(timeStretchSpeed)
        }

        if (abs(p - 1.0f) < 0.002f) {
            appendOutput(pitchBufL, pitchBufR, pitchCount)
            pitchCount = 0
        } else {
            resamplePitch(p)
        }
    }

    private fun runWsola(speed: Float) {
        if (inCount < maxPeriod * 2) return
        val newSamplesNeeded = maxPeriod * 2
        var pos = 0
        while (pos + newSamplesNeeded <= inCount) {
            val period = findBestCorrelationPeriod(pos)
            if (speed > 1.0f) {
                // Compress time: skip forward
                val skip = max(1, ((period / (speed - 1.0f)).roundToInt()).coerceAtMost(period * 2))
                val copyLen = min(skip, inCount - pos - period)
                if (copyLen <= 0) break
                val segLen = min(period, copyLen)
                ensurePitchCapacity(pitchCount + copyLen)
                // Crossfade period overlap
                for (i in 0 until segLen) {
                    val w = i.toFloat() / segLen
                    pitchBufL[pitchCount + i] = inL[pos + i] * (1f - w) + inL[pos + period + i] * w
                    pitchBufR[pitchCount + i] = inR[pos + i] * (1f - w) + inR[pos + period + i] * w
                }
                if (copyLen > segLen) {
                    val extra = copyLen - segLen
                    System.arraycopy(inL, pos + period + segLen, pitchBufL, pitchCount + segLen, extra)
                    System.arraycopy(inR, pos + period + segLen, pitchBufR, pitchCount + segLen, extra)
                }
                pitchCount += copyLen
                pos += copyLen + period
            } else {
                // Expand time: repeat period cleanly
                val repeatLen = ((period * speed / (1.0f - speed)).roundToInt()).coerceIn(period, period * 4)
                ensurePitchCapacity(pitchCount + period + repeatLen)
                // Overlap-add period
                for (i in 0 until period) {
                    val w = i.toFloat() / period
                    pitchBufL[pitchCount + i] = inL[pos + period + i] * (1f - w) + inL[pos + i] * w
                    pitchBufR[pitchCount + i] = inR[pos + period + i] * (1f - w) + inR[pos + i] * w
                }
                pitchCount += period
                val direct = min(repeatLen, inCount - pos - period)
                if (direct > 0) {
                    System.arraycopy(inL, pos + period, pitchBufL, pitchCount, direct)
                    System.arraycopy(inR, pos + period, pitchBufR, pitchCount, direct)
                    pitchCount += direct
                }
                pos += period + direct
                if (direct == 0) break
            }
        }
        val rem = inCount - pos
        if (rem > 0 && pos > 0) {
            System.arraycopy(inL, pos, inL, 0, rem)
            System.arraycopy(inR, pos, inR, 0, rem)
        }
        inCount = rem
    }

    private fun findBestCorrelationPeriod(offset: Int): Int {
        var bestPeriod = minPeriod
        var bestScore = Float.NEGATIVE_INFINITY
        // Downsampled correlation (step 2) for ultra-fast CPU execution
        for (p in minPeriod..maxPeriod step 2) {
            var corr = 0f
            var energy = 1e-6f
            var i = 0
            while (i < minPeriod) {
                val a = inL[offset + i] + inR[offset + i]
                val b = inL[offset + p + i] + inR[offset + p + i]
                corr += a * b
                energy += a * a + b * b
                i += 2
            }
            val score = corr / energy
            if (score > bestScore) {
                bestScore = score
                bestPeriod = p
            }
        }
        return bestPeriod
    }

    private fun resamplePitch(pitchRatio: Float) {
        if (pitchCount < 4) return
        val outFrames = ((pitchCount - 3) / pitchRatio).toInt()
        if (outFrames <= 0) return
        ensureOutCapacity(outCount + outFrames)
        for (i in 0 until outFrames) {
            val srcPos = i * pitchRatio
            val idx = srcPos.toInt()
            val frac = srcPos - idx
            outL[outCount + i] = cubicHermite(
                pitchBufL[(idx - 1).coerceAtLeast(0)],
                pitchBufL[idx],
                pitchBufL[idx + 1],
                pitchBufL[(idx + 2).coerceAtMost(pitchCount - 1)],
                frac
            )
            outR[outCount + i] = cubicHermite(
                pitchBufR[(idx - 1).coerceAtLeast(0)],
                pitchBufR[idx],
                pitchBufR[idx + 1],
                pitchBufR[(idx + 2).coerceAtMost(pitchCount - 1)],
                frac
            )
        }
        outCount += outFrames
        val consumed = (outFrames * pitchRatio).toInt().coerceAtMost(pitchCount)
        val rem = pitchCount - consumed
        if (rem > 0) {
            System.arraycopy(pitchBufL, consumed, pitchBufL, 0, rem)
            System.arraycopy(pitchBufR, consumed, pitchBufR, 0, rem)
        }
        pitchCount = rem
    }

    private fun cubicHermite(ym1: Float, y0: Float, y1: Float, y2: Float, t: Float): Float {
        val c0 = y0
        val c1 = 0.5f * (y1 - ym1)
        val c2 = ym1 - 2.5f * y0 + 2f * y1 - 0.5f * y2
        val c3 = 0.5f * (y2 - ym1) + 1.5f * (y0 - y1)
        return ((c3 * t + c2) * t + c1) * t + c0
    }

    private fun appendPitchInput(l: FloatArray, r: FloatArray, len: Int) {
        if (len <= 0) return
        ensurePitchCapacity(pitchCount + len)
        System.arraycopy(l, 0, pitchBufL, pitchCount, len)
        System.arraycopy(r, 0, pitchBufR, pitchCount, len)
        pitchCount += len
    }

    private fun appendOutput(l: FloatArray, r: FloatArray, len: Int) {
        if (len <= 0) return
        ensureOutCapacity(outCount + len)
        System.arraycopy(l, 0, outL, outCount, len)
        System.arraycopy(r, 0, outR, outCount, len)
        outCount += len
    }

    private fun ensureInCapacity(needed: Int) {
        if (needed <= inCap) return
        inCap = max(needed, inCap * 2)
        inL = inL.copyOf(inCap)
        inR = inR.copyOf(inCap)
    }

    private fun ensurePitchCapacity(needed: Int) {
        if (needed <= pitchBufL.size) return
        val newCap = max(needed, pitchBufL.size * 2)
        pitchBufL = pitchBufL.copyOf(newCap)
        pitchBufR = pitchBufR.copyOf(newCap)
    }

    private fun ensureOutCapacity(needed: Int) {
        if (needed <= outCap) return
        outCap = max(needed, outCap * 2)
        outL = outL.copyOf(outCap)
        outR = outR.copyOf(outCap)
    }
}
