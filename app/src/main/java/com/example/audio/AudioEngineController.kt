package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import com.example.data.AdvancedSettings
import com.example.data.AudioBuffer
import com.example.data.EqAndFxState
import com.example.data.MixerState
import com.example.data.PitchTempoState
import com.example.data.RehearsalState
import com.example.data.StemSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class AudioEngineController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile var currentBuffer: AudioBuffer? = null
        private set
    @Volatile var currentStems: StemSet? = null
        private set
    @Volatile var overdubBuffer: AudioBuffer? = null
        private set

    @Volatile var mixerState: MixerState = MixerState()
    @Volatile var pitchTempoState: PitchTempoState = PitchTempoState()
    @Volatile var eqFxState: EqAndFxState = EqAndFxState()
    @Volatile var rehearsalState: RehearsalState = RehearsalState()
    @Volatile var advancedSettings: AdvancedSettings = AdvancedSettings()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _stereoPeaks = MutableStateFlow(0f to 0f)
    val stereoPeaks: StateFlow<Pair<Float, Float>> = _stereoPeaks.asStateFlow()

    companion object {
        const val LIVE_WAVEFORM_SIZE = 72
    }

    private val liveBufA = FloatArray(LIVE_WAVEFORM_SIZE)
    private val liveBufB = FloatArray(LIVE_WAVEFORM_SIZE)
    private val liveZeroBuf = FloatArray(LIVE_WAVEFORM_SIZE)
    private val _liveWaveform = MutableStateFlow(liveZeroBuf)
    val liveWaveform: StateFlow<FloatArray> = _liveWaveform.asStateFlow()

    @Volatile private var playFrameCursor = 0
    private var playbackJob: Job? = null
    private var recordingJob: Job? = null

    private data class EditSnapshot(
        val buffer: AudioBuffer,
        val stems: StemSet?
    )

    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun loadBuffer(buffer: AudioBuffer, stems: StemSet? = null, clearHistory: Boolean = true) {
        stopPlayback()
        if (clearHistory) {
            undoStack.clear()
            redoStack.clear()
            overdubBuffer = null
        }
        currentBuffer = buffer
        currentStems = stems
        playFrameCursor = 0
        _positionMs.value = 0L
        _durationMs.value = buffer.durationMs
    }

    fun setSeparatedStems(stems: StemSet) {
        currentStems = stems
    }

    fun seekToRatio(ratio: Float) {
        val buf = currentBuffer ?: return
        val target = (ratio.coerceIn(0f, 1f) * buf.frameCount).toInt().coerceIn(0, max(0, buf.frameCount - 1))
        playFrameCursor = target
        _positionMs.value = ((target.toDouble() / buf.sampleRate) * 1000.0).toLong()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback(withOptionalCountIn: Boolean = false) {
        val buf = currentBuffer ?: return
        if (_isPlaying.value) return
        if (playFrameCursor >= buf.frameCount - 64) {
            playFrameCursor = 0
        }
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = scope.launch {
            runPlaybackLoop(withOptionalCountIn && rehearsalState.metronomeEnabled && rehearsalState.countInEnabled)
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        _liveWaveform.value = liveZeroBuf
        _stereoPeaks.value = 0f to 0f
    }

    fun stopPlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        playFrameCursor = 0
        _positionMs.value = 0L
        _liveWaveform.value = liveZeroBuf
        _stereoPeaks.value = 0f to 0f
    }

    private fun publishLiveWaveform(left: FloatArray, right: FloatArray, frames: Int) {
        if (frames <= 0) return
        val target = if (_liveWaveform.value === liveBufA) liveBufB else liveBufA
        val step = max(1, frames / LIVE_WAVEFORM_SIZE)
        var peakL = 0f
        var peakR = 0f
        for (i in 0 until LIVE_WAVEFORM_SIZE) {
            val idx = (i * step).coerceAtMost(frames - 1)
            val l = left[idx]
            val r = right[idx]
            if (abs(l) > peakL) peakL = abs(l)
            if (abs(r) > peakR) peakR = abs(r)
            target[i] = (0.5f * (l + r)).coerceIn(-1f, 1f)
        }
        _stereoPeaks.value = peakL.coerceIn(0f, 1f) to peakR.coerceIn(0f, 1f)
        _liveWaveform.value = target
    }

    private fun publishLiveMonoWaveform(mono: FloatArray, frames: Int) {
        if (frames <= 0) return
        val target = if (_liveWaveform.value === liveBufA) liveBufB else liveBufA
        val step = max(1, frames / LIVE_WAVEFORM_SIZE)
        for (i in 0 until LIVE_WAVEFORM_SIZE) {
            val idx = (i * step).coerceAtMost(frames - 1)
            target[i] = mono[idx].coerceIn(-1f, 1f)
        }
        _liveWaveform.value = target
    }

    private suspend fun runPlaybackLoop(doCountIn: Boolean) {
        val sr = AudioDecoder.TARGET_SAMPLE_RATE
        val minBufBytes = AudioTrack.getMinBufferSize(
            sr,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)

        val mult = advancedSettings.latencyMode.bufferMultiplier.coerceIn(1, 4)
        val audioTrackBuilder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBufBytes * mult)
            .setTransferMode(AudioTrack.MODE_STREAM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioTrackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        val audioTrack = audioTrackBuilder.build()

        val sonic = SonicTimePitchProcessor(sr)
        val fxProcessor = AudioEffectsProcessor(sr)

        val chunkFrames = 1024
        val mixL = FloatArray(chunkFrames)
        val mixR = FloatArray(chunkFrames)
        val outL = FloatArray(chunkFrames * 3)
        val outR = FloatArray(chunkFrames * 3)
        val pcmShorts = ShortArray(chunkFrames * 6)

        var metronomePhaseSample = 0L
        var countInRemainingSamples = if (doCountIn) ((60.0 / rehearsalState.bpm.coerceIn(40, 240)) * 4 * sr).toLong() else 0L

        audioTrack.play()
        try {
            while (scope.isActive && _isPlaying.value) {
                val buf = currentBuffer ?: break
                val totalFrames = buf.frameCount
                if (totalFrames <= 0) break

                // Optional 4-beat count-in before track starts advancing
                if (countInRemainingSamples > 0) {
                    val step = min(chunkFrames.toLong(), countInRemainingSamples).toInt()
                    mixL.fill(0f, 0, step)
                    mixR.fill(0f, 0, step)
                    mixMetronomeClick(mixL, mixR, step, sr, rehearsalState.bpm, rehearsalState.metronomeVolume.coerceAtLeast(0.55f), metronomePhaseSample)
                    metronomePhaseSample += step
                    countInRemainingSamples -= step
                    writeShorts(audioTrack, mixL, mixR, step, pcmShorts)
                    continue
                }

                // Check A-B Loop boundaries
                val reh = rehearsalState
                val loopStartFrame = if (reh.loopEnabled) (reh.loopARatio * totalFrames).toInt().coerceIn(0, totalFrames - 1) else 0
                val loopEndFrame = if (reh.loopEnabled) (reh.loopBRatio * totalFrames).toInt().coerceIn(loopStartFrame + 1024, totalFrames) else totalFrames

                if (reh.loopEnabled && (playFrameCursor < loopStartFrame || playFrameCursor >= loopEndFrame)) {
                    playFrameCursor = loopStartFrame
                    sonic.reset()
                }

                if (!reh.loopEnabled && playFrameCursor >= totalFrames) {
                    break
                }

                val limitFrame = if (reh.loopEnabled) loopEndFrame else totalFrames
                val framesToRead = min(chunkFrames, limitFrame - playFrameCursor).coerceAtLeast(0)
                if (framesToRead == 0) {
                    if (reh.loopEnabled) {
                        playFrameCursor = loopStartFrame
                        continue
                    } else break
                }

                // 1. Mix 4 Stems + Vocal Tail Cleanup + Overdub
                val m = mixerState
                val stems = currentStems
                val anySolo = m.vocalsSolo || m.accompSolo || m.bassSolo || m.drumsSolo
                val vGain = if (m.vocalsMute || (anySolo && !m.vocalsSolo)) 0f else m.vocalsGain
                val aGain = if (m.accompMute || (anySolo && !m.accompSolo)) 0f else m.accompGain
                val bGain = if (m.bassMute || (anySolo && !m.bassSolo)) 0f else m.bassGain
                val dGain = if (m.drumsMute || (anySolo && !m.drumsSolo)) 0f else m.drumsGain

                val od = overdubBuffer
                val odGain = reh.overdubGain

                for (i in 0 until framesToRead) {
                    val idx = playFrameCursor + i
                    var l: Float
                    var r: Float
                    if (stems != null && m.stemsSeparated && idx < stems.frameCount) {
                        val vl = stems.vocalsLeft[idx]
                        val vr = stems.vocalsRight[idx]
                        var al = stems.accompLeft[idx]
                        var ar = stems.accompRight[idx]
                        if (m.vocalTailCleanup) {
                            val cleaned = StemSeparatorEngine.applyVocalTailCleanup(al, ar, vl, vr, vGain)
                            al = cleaned.first
                            ar = cleaned.second
                        }
                        l = vl * vGain + al * aGain + stems.bassLeft[idx] * bGain + stems.drumsLeft[idx] * dGain
                        r = vr * vGain + ar * aGain + stems.bassRight[idx] * bGain + stems.drumsRight[idx] * dGain
                    } else {
                        l = buf.left[idx]
                        r = buf.right[idx]
                    }

                    if (od != null && idx < od.frameCount) {
                        l += od.left[idx] * odGain
                        r += od.right[idx] * odGain
                    }
                    mixL[i] = l
                    mixR[i] = r
                }

                playFrameCursor += framesToRead
                _positionMs.value = ((playFrameCursor.toDouble() / sr) * 1000.0).toLong()

                // 2. Real-time Pitch & Tempo
                val pt = pitchTempoState
                sonic.tempo = pt.tempoRatio
                sonic.pitch = pt.pitchFactor
                sonic.writeStereo(mixL, mixR, framesToRead)

                var produced = sonic.readStereo(outL, outR, outL.size)
                while (produced > 0) {
                    // 3. Real-time EQ + Stereo Width + Reverb + Delay + Auto-Normalize Limiter
                    fxProcessor.processBlock(outL, outR, produced, eqFxState, advancedSettings.limiterCeilingDb)

                    // 4. Real-time Metronome overlay (pitch-independent steady BPM click)
                    if (reh.metronomeEnabled) {
                        mixMetronomeClick(outL, outR, produced, sr, reh.bpm, reh.metronomeVolume, metronomePhaseSample)
                        metronomePhaseSample += produced
                    }

                    writeShorts(audioTrack, outL, outR, produced, pcmShorts)
                    publishLiveWaveform(outL, outR, produced)
                    produced = sonic.readStereo(outL, outR, outL.size)
                }

                if (reh.loopEnabled && playFrameCursor >= loopEndFrame) {
                    playFrameCursor = loopStartFrame
                } else if (!reh.loopEnabled && playFrameCursor >= totalFrames) {
                    sonic.flush()
                    var flushed = sonic.readStereo(outL, outR, outL.size)
                    while (flushed > 0) {
                        fxProcessor.processBlock(outL, outR, flushed, eqFxState, advancedSettings.limiterCeilingDb)
                        writeShorts(audioTrack, outL, outR, flushed, pcmShorts)
                        flushed = sonic.readStereo(outL, outR, outL.size)
                    }
                    break
                }
            }
        } finally {
            try {
                audioTrack.pause()
                audioTrack.flush()
                audioTrack.release()
            } catch (_: Exception) {
            }
            if (_isPlaying.value && playFrameCursor >= (currentBuffer?.frameCount ?: 0)) {
                playFrameCursor = 0
                _positionMs.value = 0L
            }
            _isPlaying.value = false
        }
    }

    private fun mixMetronomeClick(
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        sr: Int,
        bpm: Int,
        volume: Float,
        startSampleOffset: Long
    ) {
        val safeBpm = bpm.coerceIn(40, 240)
        val samplesPerBeat = ((60.0 / safeBpm) * sr).toLong().coerceAtLeast(1L)
        val clickLen = (sr * 0.032).toInt()
        for (i in 0 until frames) {
            val globalSample = startSampleOffset + i
            val posInBeat = (globalSample % samplesPerBeat).toInt()
            if (posInBeat < clickLen) {
                val beatNum = (globalSample / samplesPerBeat) % 4L
                val freq = if (beatNum == 0L) 1480.0 else 980.0
                val t = posInBeat.toDouble() / sr
                val click = (sin(2.0 * PI * freq * t) * exp(-t * 95.0) * volume * 0.65).toFloat()
                left[i] = (left[i] + click).coerceIn(-1f, 1f)
                right[i] = (right[i] + click).coerceIn(-1f, 1f)
            }
        }
    }

    private fun writeShorts(
        track: AudioTrack,
        l: FloatArray,
        r: FloatArray,
        frames: Int,
        shortScratch: ShortArray
    ) {
        var sIdx = 0
        for (i in 0 until frames) {
            shortScratch[sIdx++] = (l[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            shortScratch[sIdx++] = (r[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        track.write(shortScratch, 0, sIdx)
    }

    // --- Overdub / Standalone Microphone Recording ---

    @SuppressLint("MissingPermission")
    fun startOverdubRecording(onFinished: (AudioBuffer) -> Unit) {
        val sr = AudioDecoder.TARGET_SAMPLE_RATE
        val minBytes = AudioRecord.getMinBufferSize(
            sr,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sr,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBytes * 2
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return
        }

        val startFrame = playFrameCursor
        // Start playback alongside mic if a backing track is loaded!
        if (currentBuffer != null && !_isPlaying.value) {
            startPlayback(withOptionalCountIn = false)
        }

        recordingJob?.cancel()
        recordingJob = scope.launch(Dispatchers.IO) {
            val recordedChunks = ArrayList<FloatArray>()
            val shortBuf = ShortArray(1024)
            recorder.startRecording()
            try {
                while (isActive) {
                    val read = recorder.read(shortBuf, 0, shortBuf.size)
                    if (read > 0) {
                        val floats = FloatArray(read)
                        var sumSq = 0f
                        for (i in 0 until read) {
                            val v = shortBuf[i] / 32768f
                            floats[i] = v
                            sumSq += v * v
                        }
                        _micLevel.value = (sqrt(sumSq / read) * 3.2f).coerceIn(0f, 1f)
                        if (!_isPlaying.value) {
                            publishLiveMonoWaveform(floats, read)
                        }
                        recordedChunks.add(floats)
                    }
                }
            } finally {
                try {
                    recorder.stop()
                } catch (_: Exception) {
                }
                recorder.release()
                _micLevel.value = 0f

                val totalRecorded = recordedChunks.sumOf { it.size }
                if (totalRecorded > 0) {
                    val adv = advancedSettings
                    // Latency compensation shifts recorded samples backward/forward by configured ms
                    val compFrames = (adv.overdubLatencyCompMs * sr) / 1000
                    val backingFrames = currentBuffer?.frameCount ?: 0
                    val rawAlignedStart = (startFrame - compFrames).coerceAtLeast(0)
                    val totalOut = max(backingFrames, rawAlignedStart + totalRecorded)
                    val odL = FloatArray(totalOut)
                    val odR = FloatArray(totalOut)

                    var peak = 0f
                    if (adv.autoNormalizeRecording) {
                        for (c in recordedChunks) {
                            for (s in c) {
                                val a = abs(s)
                                if (a > peak) peak = a
                            }
                        }
                    }
                    val normGain = if (adv.autoNormalizeRecording && peak in 0.01f..0.75f) {
                        (0.85f / peak).coerceAtMost(3.5f)
                    } else 1f

                    var writeIdx = rawAlignedStart
                    for (c in recordedChunks) {
                        for (s in c) {
                            if (writeIdx < totalOut) {
                                val v = (s * normGain).coerceIn(-0.98f, 0.98f)
                                odL[writeIdx] = v
                                odR[writeIdx] = v
                                writeIdx++
                            }
                        }
                    }
                    val odBuf = AudioBuffer(sr, odL, odR)
                    overdubBuffer = odBuf
                    onFinished(odBuf)
                }
            }
        }
    }

    fun stopOverdubRecording() {
        recordingJob?.cancel()
        recordingJob = null
        pausePlayback()
    }

    fun clearOverdub() {
        overdubBuffer = null
    }

    // --- Waveform Editor Operations with Full Undo / Redo ---

    private fun pushUndoSnapshot() {
        val buf = currentBuffer ?: return
        undoStack.addLast(EditSnapshot(buf.copy(), currentStems))
        if (undoStack.size > 15) undoStack.removeFirst()
        redoStack.clear()
    }

    fun trimSelection(startRatio: Float, endRatio: Float): Boolean {
        val buf = currentBuffer ?: return false
        val start = (startRatio.coerceIn(0f, 1f) * buf.frameCount).toInt()
        val end = (endRatio.coerceIn(0f, 1f) * buf.frameCount).toInt()
        if (end - start < 4410) return false
        pushUndoSnapshot()
        val newLen = end - start
        val newL = buf.left.copyOfRange(start, end)
        val newR = buf.right.copyOfRange(start, end)
        val newStems = currentStems?.let { s ->
            if (end <= s.frameCount) {
                StemSet(
                    s.sampleRate,
                    s.vocalsLeft.copyOfRange(start, end), s.vocalsRight.copyOfRange(start, end),
                    s.accompLeft.copyOfRange(start, end), s.accompRight.copyOfRange(start, end),
                    s.bassLeft.copyOfRange(start, end), s.bassRight.copyOfRange(start, end),
                    s.drumsLeft.copyOfRange(start, end), s.drumsRight.copyOfRange(start, end)
                )
            } else null
        }
        applyEditedBuffer(AudioBuffer(buf.sampleRate, newL, newR), newStems)
        return true
    }

    fun cutSelection(startRatio: Float, endRatio: Float): Boolean {
        val buf = currentBuffer ?: return false
        val start = (startRatio.coerceIn(0f, 1f) * buf.frameCount).toInt()
        val end = (endRatio.coerceIn(0f, 1f) * buf.frameCount).toInt()
        val removed = end - start
        val remaining = buf.frameCount - removed
        if (removed < 2000 || remaining < 4410) return false
        pushUndoSnapshot()

        fun cutPair(arr: FloatArray): FloatArray {
            val out = FloatArray(arr.size - removed)
            System.arraycopy(arr, 0, out, 0, start)
            System.arraycopy(arr, end, out, start, arr.size - end)
            // 10ms micro-crossfade at stitch point to prevent clicks
            val fade = min(220, min(start, arr.size - end))
            for (k in 0 until fade) {
                val w = k.toFloat() / fade
                val idx = start - fade / 2 + k
                val srcA = start - fade + k
                val srcB = end + k
                if (idx in out.indices && srcA in arr.indices && srcB in arr.indices) {
                    out[idx] = arr[srcA] * (1f - w) + arr[srcB] * w
                }
            }
            return out
        }

        val newL = cutPair(buf.left)
        val newR = cutPair(buf.right)
        val newStems = currentStems?.let { s ->
            if (end <= s.frameCount) {
                StemSet(
                    s.sampleRate,
                    cutPair(s.vocalsLeft), cutPair(s.vocalsRight),
                    cutPair(s.accompLeft), cutPair(s.accompRight),
                    cutPair(s.bassLeft), cutPair(s.bassRight),
                    cutPair(s.drumsLeft), cutPair(s.drumsRight)
                )
            } else null
        }
        applyEditedBuffer(AudioBuffer(buf.sampleRate, newL, newR), newStems)
        return true
    }

    fun applyFadeIn(fadeDurationSec: Float = 2.0f): Boolean {
        val buf = currentBuffer ?: return false
        pushUndoSnapshot()
        val copy = buf.copy()
        val fadeSamples = min(copy.frameCount / 2, (fadeDurationSec * copy.sampleRate).toInt())
        for (i in 0 until fadeSamples) {
            val gain = (0.5 * (1.0 - cos(PI * i / fadeSamples))).toFloat()
            copy.left[i] *= gain
            copy.right[i] *= gain
        }
        val newStems = currentStems?.let { s ->
            StemSet(
                s.sampleRate,
                fadeInCopy(s.vocalsLeft, fadeSamples), fadeInCopy(s.vocalsRight, fadeSamples),
                fadeInCopy(s.accompLeft, fadeSamples), fadeInCopy(s.accompRight, fadeSamples),
                fadeInCopy(s.bassLeft, fadeSamples), fadeInCopy(s.bassRight, fadeSamples),
                fadeInCopy(s.drumsLeft, fadeSamples), fadeInCopy(s.drumsRight, fadeSamples)
            )
        }
        applyEditedBuffer(copy, newStems)
        return true
    }

    fun applyFadeOut(fadeDurationSec: Float = 2.5f): Boolean {
        val buf = currentBuffer ?: return false
        pushUndoSnapshot()
        val copy = buf.copy()
        val fadeSamples = min(copy.frameCount / 2, (fadeDurationSec * copy.sampleRate).toInt())
        val start = copy.frameCount - fadeSamples
        for (i in 0 until fadeSamples) {
            val gain = (0.5 * (1.0 + cos(PI * i / fadeSamples))).toFloat()
            copy.left[start + i] *= gain
            copy.right[start + i] *= gain
        }
        val newStems = currentStems?.let { s ->
            StemSet(
                s.sampleRate,
                fadeOutCopy(s.vocalsLeft, fadeSamples), fadeOutCopy(s.vocalsRight, fadeSamples),
                fadeOutCopy(s.accompLeft, fadeSamples), fadeOutCopy(s.accompRight, fadeSamples),
                fadeOutCopy(s.bassLeft, fadeSamples), fadeOutCopy(s.bassRight, fadeSamples),
                fadeOutCopy(s.drumsLeft, fadeSamples), fadeOutCopy(s.drumsRight, fadeSamples)
            )
        }
        applyEditedBuffer(copy, newStems)
        return true
    }

    fun mergeAppendBuffer(other: AudioBuffer): Boolean {
        val buf = currentBuffer ?: return false
        pushUndoSnapshot()
        val n1 = buf.frameCount
        val n2 = other.frameCount
        val crossfade = min(4410, min(n1, n2) / 4) // 100ms smooth crossfade join
        val total = n1 + n2 - crossfade
        val outL = FloatArray(total)
        val outR = FloatArray(total)

        System.arraycopy(buf.left, 0, outL, 0, n1 - crossfade)
        System.arraycopy(buf.right, 0, outR, 0, n1 - crossfade)
        for (i in 0 until crossfade) {
            val w = i.toFloat() / crossfade
            outL[n1 - crossfade + i] = buf.left[n1 - crossfade + i] * (1f - w) + other.left[i] * w
            outR[n1 - crossfade + i] = buf.right[n1 - crossfade + i] * (1f - w) + other.right[i] * w
        }
        System.arraycopy(other.left, crossfade, outL, n1, n2 - crossfade)
        System.arraycopy(other.right, crossfade, outR, n1, n2 - crossfade)

        // Merged track invalidates old stem cache until re-separated
        applyEditedBuffer(AudioBuffer(buf.sampleRate, outL, outR), null)
        return true
    }

    fun autoTrimSilence(thresholdDb: Float = -44f): Boolean {
        val buf = currentBuffer ?: return false
        val threshold = Math.pow(10.0, thresholdDb / 20.0).toFloat()
        val n = buf.frameCount
        var start = 0
        while (start < n - 1 && abs(buf.left[start]) < threshold && abs(buf.right[start]) < threshold) {
            start++
        }
        var end = n - 1
        while (end > start + 4410 && abs(buf.left[end]) < threshold && abs(buf.right[end]) < threshold) {
            end--
        }
        start = (start - 440).coerceAtLeast(0)
        end = (end + 880).coerceAtMost(n)
        if (end - start < 4410 || (start == 0 && end == n)) return false
        return trimSelection(start.toFloat() / n, end.toFloat() / n)
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val current = currentBuffer ?: return false
        redoStack.addLast(EditSnapshot(current.copy(), currentStems))
        val prev = undoStack.removeLast()
        applyEditedBuffer(prev.buffer, prev.stems)
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val current = currentBuffer ?: return false
        undoStack.addLast(EditSnapshot(current.copy(), currentStems))
        val next = redoStack.removeLast()
        applyEditedBuffer(next.buffer, next.stems)
        return true
    }

    private fun applyEditedBuffer(buffer: AudioBuffer, stems: StemSet?) {
        stopPlayback()
        currentBuffer = buffer
        currentStems = stems
        _durationMs.value = buffer.durationMs
        _positionMs.value = 0L
        playFrameCursor = 0
    }

    private fun fadeInCopy(src: FloatArray, fadeSamples: Int): FloatArray {
        val out = src.copyOf()
        val lim = min(fadeSamples, out.size)
        for (i in 0 until lim) {
            val gain = (0.5 * (1.0 - cos(PI * i / lim))).toFloat()
            out[i] *= gain
        }
        return out
    }

    private fun fadeOutCopy(src: FloatArray, fadeSamples: Int): FloatArray {
        val out = src.copyOf()
        val lim = min(fadeSamples, out.size)
        val start = out.size - lim
        for (i in 0 until lim) {
            val gain = (0.5 * (1.0 + cos(PI * i / lim))).toFloat()
            out[start + i] *= gain
        }
        return out
    }

    fun computeWaveformPeaks(barsCount: Int = 180): List<Float> {
        val buf = currentBuffer ?: return emptyList()
        val n = buf.frameCount
        if (n <= 0) return emptyList()
        val block = max(1, n / barsCount)
        val peaks = ArrayList<Float>(barsCount)
        var i = 0
        while (i < n && peaks.size < barsCount) {
            var peak = 0f
            val end = min(n, i + block)
            val step = max(1, (end - i) / 64)
            var j = i
            while (j < end) {
                val m = max(abs(buf.left[j]), abs(buf.right[j]))
                if (m > peak) peak = m
                j += step
            }
            peaks.add(peak.coerceIn(0.04f, 1f))
            i += block
        }
        return peaks
    }
}
