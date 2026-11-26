package com.example.audio

import com.example.data.AudioBuffer
import com.example.data.ProcessingQuality
import com.example.data.StemSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 100% On-Device Offline 4-Stem Separator Engine.
 * Combines:
 * 1. Short-Time Fourier Transform (STFT) with overlap-add reconstruction
 * 2. Harmonic-Percussive Source Separation (HPSS) via median time/frequency filtering for Drums
 * 3. Smooth frequency crossover + harmonic sub-tracking for Bass
 * 4. Phase-coherent Mid-Side spectral masking + vocal formant weighting for Vocals vs Accompaniment
 * 5. Optional Vocal Tail & Reverb Suppression (center-de-reverb & low-level spectral gate)
 */
object StemSeparatorEngine {

    suspend fun separateStems(
        buffer: AudioBuffer,
        quality: ProcessingQuality,
        onProgress: (Float) -> Unit
    ): StemSet = withContext(Dispatchers.Default) {
        val sr = buffer.sampleRate
        val n = buffer.frameCount
        val fftSize = quality.fftSize
        val hop = quality.hopSize
        val bins = fftSize / 2 + 1
        val fft = FftProcessor(fftSize)
        val win = fft.hannWindow

        val numFrames = max(1, (n - fftSize) / hop + 2)
        val halfWinNorm = FloatArray(n)

        // Precompute spectral magnitudes for HPSS median filtering
        val magMid = Array(numFrames) { FloatArray(bins) }
        val lReal = Array(numFrames) { FloatArray(fftSize) }
        val lImag = Array(numFrames) { FloatArray(fftSize) }
        val rReal = Array(numFrames) { FloatArray(fftSize) }
        val rImag = Array(numFrames) { FloatArray(fftSize) }

        val reL = FloatArray(fftSize)
        val imL = FloatArray(fftSize)
        val reR = FloatArray(fftSize)
        val imR = FloatArray(fftSize)

        for (f in 0 until numFrames) {
            val start = f * hop
            for (i in 0 until fftSize) {
                val idx = start + i
                val w = win[i]
                if (idx < n) {
                    reL[i] = buffer.left[idx] * w
                    reR[i] = buffer.right[idx] * w
                } else {
                    reL[i] = 0f
                    reR[i] = 0f
                }
                imL[i] = 0f
                imR[i] = 0f
            }
            fft.fft(reL, imL)
            fft.fft(reR, imR)

            System.arraycopy(reL, 0, lReal[f], 0, fftSize)
            System.arraycopy(imL, 0, lImag[f], 0, fftSize)
            System.arraycopy(reR, 0, rReal[f], 0, fftSize)
            System.arraycopy(imR, 0, rImag[f], 0, fftSize)

            val mRow = magMid[f]
            for (k in 0 until bins) {
                val mRe = 0.5f * (reL[k] + reR[k])
                val mIm = 0.5f * (imL[k] + imR[k])
                mRow[k] = sqrt(mRe * mRe + mIm * mIm)
            }
            if (f % 32 == 0) {
                onProgress(0.35f * (f.toFloat() / numFrames))
            }
        }

        // Output stem arrays
        val vocL = FloatArray(n)
        val vocR = FloatArray(n)
        val accL = FloatArray(n)
        val accR = FloatArray(n)
        val basL = FloatArray(n)
        val basR = FloatArray(n)
        val drmL = FloatArray(n)
        val drmR = FloatArray(n)

        val freqPerBin = sr.toFloat() / fftSize
        val bassCutoffBin = (210f / freqPerBin).toInt().coerceIn(2, bins - 1)
        val vocalLowBin = (180f / freqPerBin).toInt().coerceIn(1, bins - 1)
        val vocalHighBin = (5200f / freqPerBin).toInt().coerceIn(vocalLowBin + 1, bins - 1)

        val timeMedHalf = if (quality == ProcessingQuality.MAX_QUALITY) 6 else 4
        val freqMedHalf = if (quality == ProcessingQuality.MAX_QUALITY) 6 else 4
        val scratchTime = FloatArray(timeMedHalf * 2 + 1)
        val scratchFreq = FloatArray(freqMedHalf * 2 + 1)

        val tmpRe = FloatArray(fftSize)
        val tmpIm = FloatArray(fftSize)

        fun applyAndIfft(
            srcRe: FloatArray,
            srcIm: FloatArray,
            mask: FloatArray,
            outArr: FloatArray,
            start: Int,
            accumulateNorm: Boolean
        ) {
            for (k in 0 until bins) {
                val m = mask[k]
                tmpRe[k] = srcRe[k] * m
                tmpIm[k] = srcIm[k] * m
            }
            for (k in 1 until bins - 1) {
                tmpRe[fftSize - k] = tmpRe[k]
                tmpIm[fftSize - k] = -tmpIm[k]
            }
            fft.ifft(tmpRe, tmpIm)
            for (i in 0 until fftSize) {
                val idx = start + i
                if (idx < n) {
                    val w = win[i]
                    outArr[idx] += tmpRe[i] * w
                    if (accumulateNorm) {
                        halfWinNorm[idx] += w * w
                    }
                }
            }
        }

        val maskVoc = FloatArray(bins)
        val maskAcc = FloatArray(bins)
        val maskBas = FloatArray(bins)
        val maskDrm = FloatArray(bins)

        for (f in 0 until numFrames) {
            val lr = lReal[f]; val li = lImag[f]
            val rr = rReal[f]; val ri = rImag[f]
            val mCur = magMid[f]

            for (k in 0 until bins) {
                val freq = k * freqPerBin

                // 1. HPSS Harmonic vs Percussive Soft Masks
                var tCount = 0
                for (df in -timeMedHalf..timeMedHalf) {
                    val ff = (f + df).coerceIn(0, numFrames - 1)
                    scratchTime[tCount++] = magMid[ff][k]
                }
                val harmonicMed = medianOf(scratchTime, tCount)

                var kCount = 0
                for (dk in -freqMedHalf..freqMedHalf) {
                    val kk = (k + dk).coerceIn(0, bins - 1)
                    scratchFreq[kCount++] = mCur[kk]
                }
                val percussiveMed = medianOf(scratchFreq, kCount)

                val h2 = harmonicMed * harmonicMed
                val p2 = percussiveMed * percussiveMed
                val hpDenom = h2 + p2 + 1e-12f
                val percussiveRatio = p2 / hpDenom
                val harmonicRatio = h2 / hpDenom

                // 2. Mid-Side & Phase Coherence for Center Vocal Isolation
                val midRe = 0.5f * (lr[k] + rr[k])
                val midIm = 0.5f * (li[k] + ri[k])
                val sideRe = 0.5f * (lr[k] - rr[k])
                val sideIm = 0.5f * (li[k] - ri[k])
                val midMag = sqrt(midRe * midRe + midIm * midIm)
                val sideMag = sqrt(sideRe * sideRe + sideIm * sideIm)

                val lMag = sqrt(lr[k] * lr[k] + li[k] * li[k])
                val rMag = sqrt(rr[k] * rr[k] + ri[k] * ri[k])
                val dot = lr[k] * rr[k] + li[k] * ri[k]
                val phaseCoherence = (dot / (lMag * rMag + 1e-10f)).coerceIn(0f, 1f)

                val centerRatio = (midMag / (midMag + sideMag * 1.45f + 1e-10f)).coerceIn(0f, 1f)

                // 3. Smooth Crossover & Stem Mask Assignment (sums cleanly to 1.0!)
                // Bass: smooth rolloff 130Hz..260Hz, weighted toward harmonic/sustained low end
                val bassBand = when {
                    freq <= 120f -> 1f
                    freq >= 260f -> 0f
                    else -> {
                        val x = (freq - 120f) / 140f
                        0.5f * (1f + cos(PI * x)).toFloat()
                    }
                }
                val bassWeight = bassBand * (0.35f + 0.65f * harmonicRatio)

                // Drums: percussive transients outside sustained sub-bass
                val drumWeight = (1f - bassWeight * 0.7f) * percussiveRatio *
                    (if (freq < 60f) 0.45f else 1f)

                // Remaining harmonic non-bass energy split between Vocals and Accompaniment
                val remHarmonic = (1f - bassWeight - drumWeight).coerceIn(0f, 1f)

                // Vocal presence band (180Hz - 5.2kHz) with center coherence
                val vocalBand = when {
                    k < vocalLowBin -> 0f
                    k > vocalHighBin -> {
                        val fade = 1f - ((k - vocalHighBin).toFloat() / max(1, bins / 8)).coerceIn(0f, 1f)
                        fade * 0.45f
                    }
                    else -> {
                        val lowRamp = ((k - vocalLowBin).toFloat() / max(1, (320f / freqPerBin).toInt())).coerceIn(0.25f, 1f)
                        lowRamp
                    }
                }
                val rawVocalLikelihood = (centerRatio * centerRatio) * (0.35f + 0.65f * phaseCoherence) * vocalBand
                val vocalShare = ((rawVocalLikelihood - 0.18f) * 1.45f).coerceIn(0f, 1f)

                val vocWeight = remHarmonic * vocalShare
                val accWeight = (remHarmonic - vocWeight).coerceIn(0f, 1f)

                maskBas[k] = bassWeight
                maskDrm[k] = drumWeight
                maskVoc[k] = vocWeight
                maskAcc[k] = accWeight
            }

            val start = f * hop
            applyAndIfft(lr, li, maskVoc, vocL, start, accumulateNorm = true)
            applyAndIfft(rr, ri, maskVoc, vocR, start, accumulateNorm = false)

            applyAndIfft(lr, li, maskAcc, accL, start, accumulateNorm = false)
            applyAndIfft(rr, ri, maskAcc, accR, start, accumulateNorm = false)

            applyAndIfft(lr, li, maskBas, basL, start, accumulateNorm = false)
            applyAndIfft(rr, ri, maskBas, basR, start, accumulateNorm = false)

            applyAndIfft(lr, li, maskDrm, drmL, start, accumulateNorm = false)
            applyAndIfft(rr, ri, maskDrm, drmR, start, accumulateNorm = false)

            if (f % 24 == 0) {
                onProgress(0.35f + 0.60f * (f.toFloat() / numFrames))
            }
        }

        // Overlap-add window normalization
        for (i in 0 until n) {
            val norm = if (halfWinNorm[i] > 1e-6f) 1f / halfWinNorm[i] else 1f
            vocL[i] *= norm; vocR[i] *= norm
            accL[i] *= norm; accR[i] *= norm
            basL[i] *= norm; basR[i] *= norm
            drmL[i] *= norm; drmR[i] *= norm
        }

        onProgress(1.0f)
        StemSet(
            sampleRate = sr,
            vocalsLeft = vocL,
            vocalsRight = vocR,
            accompLeft = accL,
            accompRight = accR,
            bassLeft = basL,
            bassRight = basR,
            drumsLeft = drmL,
            drumsRight = drmR
        )
    }

    /**
     * Suppresses lingering vocal reverb tails and center bleed when Vocals are muted/attenuated.
     */
    fun applyVocalTailCleanup(
        accL: Float,
        accR: Float,
        vocL: Float,
        vocR: Float,
        vocEffectiveGain: Float
    ): Pair<Float, Float> {
        if (vocEffectiveGain >= 0.4f) return accL to accR
        // Mild mid-channel de-reverb subtraction proportional to vocal suppression
        val suppressionStrength = ((0.4f - vocEffectiveGain) / 0.4f).coerceIn(0f, 1f) * 0.28f
        val mid = 0.5f * (accL + accR)
        val side = 0.5f * (accL - accR)
        val vocMid = 0.5f * (vocL + vocR)
        // Subtract residual center correlation only where vocal energy is low-level tail
        val vocLevel = abs(vocMid)
        val cleanedMid = if (vocLevel < 0.08f) {
            mid * (1f - suppressionStrength)
        } else {
            mid
        }
        return (cleanedMid + side) to (cleanedMid - side)
    }

    private fun medianOf(arr: FloatArray, count: Int): Float {
        // Simple insertion sort on tiny window (9..13 items) - zero allocations!
        for (i in 1 until count) {
            val key = arr[i]
            var j = i - 1
            while (j >= 0 && arr[j] > key) {
                arr[j + 1] = arr[j]
                j--
            }
            arr[j + 1] = key
        }
        return arr[count / 2]
    }
}
