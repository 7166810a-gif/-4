package com.example

import com.example.audio.AudioAnalyzer
import com.example.audio.AudioDecoder
import com.example.audio.AudioEffectsProcessor
import com.example.audio.AudioEngineController
import com.example.audio.SonicTimePitchProcessor
import com.example.audio.StemSeparatorEngine
import com.example.data.AudioAnalysisResult
import com.example.data.AudioBuffer
import com.example.data.EqAndFxState
import com.example.data.ProcessingQuality
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    @Test
    fun stemSeparation_producesFourNonSilentStems() = runTest {
        val loop = AudioDecoder.generatePracticeLoop(bpm = 110)
        // Use a 2-second slice for fast JVM test verification
        val sliceFrames = 44100 * 2
        val slice = AudioBuffer(
            44100,
            loop.left.copyOf(sliceFrames),
            loop.right.copyOf(sliceFrames)
        )

        val stems = StemSeparatorEngine.separateStems(slice, ProcessingQuality.FAST) {}
        assertEquals(sliceFrames, stems.frameCount)

        val vocEnergy = stems.vocalsLeft.sumOf { abs(it).toDouble() }
        val accEnergy = stems.accompLeft.sumOf { abs(it).toDouble() }
        val basEnergy = stems.bassLeft.sumOf { abs(it).toDouble() }
        val drmEnergy = stems.drumsLeft.sumOf { abs(it).toDouble() }

        assertTrue("Vocals stem should have energy", vocEnergy > 10.0)
        assertTrue("Accompaniment stem should have energy", accEnergy > 10.0)
        assertTrue("Bass stem should have energy", basEnergy > 10.0)
        assertTrue("Drums stem should have energy", drmEnergy > 10.0)
    }

    @Test
    fun sonicProcessor_scalesPitchAndTempoCleanly() {
        val sonic = SonicTimePitchProcessor(44100).apply {
            pitch = 1.25f
            tempo = 0.80f
        }
        val inL = FloatArray(4096) { kotlin.math.sin(2.0 * Math.PI * 440.0 * it / 44100.0).toFloat() * 0.5f }
        val inR = inL.copyOf()
        sonic.writeStereo(inL, inR, inL.size)
        sonic.flush()
        val outL = FloatArray(8192)
        val outR = FloatArray(8192)
        val produced = sonic.readStereo(outL, outR, outL.size)
        assertTrue("Sonic processor should output processed frames", produced > 3000)
    }

    @Test
    fun eqAndLimiter_clampsPeaksWithinSafeRange() {
        val fx = AudioEffectsProcessor(44100)
        val l = FloatArray(1024) { 1.4f }
        val r = FloatArray(1024) { -1.4f }
        fx.processBlock(
            l,
            r,
            1024,
            EqAndFxState(
                bassDb = 6f,
                autoNormalizeLimiter = true,
                reverbMix = 0.3f,
                delayMix = 0.2f,
                stereoWidth = 1.4f,
                highPass80Hz = true
            ),
            limiterCeilingDb = -1.0f
        )
        assertTrue(l.all { it in -1.0f..1.0f })
        assertTrue(r.all { it in -1.0f..1.0f })
    }

    @Test
    fun stereoWidthZero_collapsesChannelsToMono() {
        val fx = AudioEffectsProcessor(44100)
        val l = FloatArray(512) { 0.4f }
        val r = FloatArray(512) { -0.2f }
        fx.processBlock(
            l,
            r,
            512,
            EqAndFxState(autoNormalizeLimiter = false, stereoWidth = 0f, highPass80Hz = false)
        )
        for (i in l.indices) {
            assertEquals(l[i], r[i], 1e-4f)
        }
    }

    @Test
    fun waveformEditor_trimCutUndoRedoAndAutoTrimSilenceWork() {
        val controller = AudioEngineController()
        val buf = AudioBuffer(44100, FloatArray(44100) { 0.5f }, FloatArray(44100) { 0.5f })
        controller.loadBuffer(buf)
        assertTrue(controller.trimSelection(0.2f, 0.8f))
        val trimmedLen = controller.currentBuffer!!.frameCount
        assertTrue(trimmedLen < 44100)
        assertTrue(controller.undo())
        assertEquals(44100, controller.currentBuffer!!.frameCount)
        assertTrue(controller.redo())
        assertEquals(trimmedLen, controller.currentBuffer!!.frameCount)

        // Silence padding at edges should be trimmed automatically
        val paddedL = FloatArray(44100) { i -> if (i in 11025 until 33075) 0.4f else 0f }
        val paddedR = paddedL.copyOf()
        controller.loadBuffer(AudioBuffer(44100, paddedL, paddedR))
        assertTrue(controller.autoTrimSilence())
        assertTrue(controller.currentBuffer!!.frameCount < 30000)
        assertTrue(controller.currentBuffer!!.frameCount >= 22000)
    }

    @Test
    fun audioAnalyzer_detectsBpmAndTransposesKeyAccurately() = runTest {
        val loop = AudioDecoder.generatePracticeLoop(bpm = 110)
        val result = AudioAnalyzer.analyze(loop)
        assertTrue("Detected BPM should be in valid range", result.detectedBpm in 60..180)
        assertTrue("Detected key root should be valid", result.rootPitchClass in 0..11)

        val base = AudioAnalysisResult(detectedBpm = 110, rootPitchClass = 9, isMinor = true, detectedKeyLabel = "Am")
        assertEquals("Cm", AudioAnalyzer.formatTransposedKey(base, +3))
        assertEquals("F#m", AudioAnalyzer.formatTransposedKey(base, -3))
    }

    @Test
    fun audioEngine_skipByMsClampsWithinBufferBounds() {
        val controller = AudioEngineController()
        val buf = AudioBuffer(44100, FloatArray(44100 * 10), FloatArray(44100 * 10))
        controller.loadBuffer(buf)

        controller.skipByMs(5000L)
        assertEquals(5000L, controller.positionMs.value)

        controller.skipByMs(20_000L)
        assertTrue(controller.positionMs.value in 9990L..10_000L)

        controller.skipByMs(-30_000L)
        assertEquals(0L, controller.positionMs.value)
    }

    @Test
    fun effectsProcessor_noiseGateAndMasterGainAndLoudnessTargetWork() {
        // Noise gate attenuates low-level hum well below threshold
        val gateFx = AudioEffectsProcessor(44100)
        val quietL = FloatArray(2048) { 0.001f } // ~ -60 dBFS
        val quietR = FloatArray(2048) { 0.001f }
        gateFx.processBlock(
            quietL,
            quietR,
            2048,
            EqAndFxState(autoNormalizeLimiter = false, highPass80Hz = false),
            noiseGateEnabled = true,
            noiseGateThresholdDb = -35f
        )
        assertTrue("Noise gate should attenuate quiet tail", abs(quietL.last()) < 0.0002f)

        // Master gain scales signal linearly when limiter is off
        val gainFx = AudioEffectsProcessor(44100)
        val dryL = FloatArray(256) { 0.25f }
        val dryR = FloatArray(256) { 0.25f }
        gainFx.processBlock(
            dryL,
            dryR,
            256,
            EqAndFxState(autoNormalizeLimiter = false, highPass80Hz = false, masterGainDb = -6.0f)
        )
        assertEquals(0.125f, dryL.last(), 0.01f)

        // LoudnessTarget values are ordered from gentle to stage
        assertTrue(com.example.data.LoudnessTarget.GENTLE_18.targetRms < com.example.data.LoudnessTarget.BALANCED_14.targetRms)
        assertTrue(com.example.data.LoudnessTarget.BALANCED_14.targetRms < com.example.data.LoudnessTarget.STAGE_11.targetRms)
    }
}
