package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.example.data.AudioBuffer
import com.example.data.EqAndFxState
import com.example.data.ExportBitrate
import com.example.data.ExportFormat
import com.example.data.MixerState
import com.example.data.PitchTempoState
import com.example.data.StemSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder
import kotlin.math.min

object AudioExporter {

    suspend fun renderAndExport(
        context: Context,
        sourceBuffer: AudioBuffer,
        stems: StemSet?,
        overdubBuffer: AudioBuffer?,
        overdubGain: Float,
        mixer: MixerState,
        pitchTempo: PitchTempoState,
        fx: EqAndFxState,
        format: ExportFormat,
        bitrate: ExportBitrate,
        internalArchiveFile: File,
        externalUri: Uri?,
        limiterCeilingDb: Float = -0.5f,
        onProgress: (Float) -> Unit
    ): AudioBuffer = withContext(Dispatchers.Default) {
        onProgress(0.05f)
        // 1. Mix Stems + Overdub
        val frames = sourceBuffer.frameCount
        val mixedL = FloatArray(frames)
        val mixedR = FloatArray(frames)

        val anySolo = mixer.vocalsSolo || mixer.accompSolo || mixer.bassSolo || mixer.drumsSolo
        val vGain = if (mixer.vocalsMute || (anySolo && !mixer.vocalsSolo)) 0f else mixer.vocalsGain
        val aGain = if (mixer.accompMute || (anySolo && !mixer.accompSolo)) 0f else mixer.accompGain
        val bGain = if (mixer.bassMute || (anySolo && !mixer.bassSolo)) 0f else mixer.bassGain
        val dGain = if (mixer.drumsMute || (anySolo && !mixer.drumsSolo)) 0f else mixer.drumsGain

        if (stems != null && mixer.stemsSeparated) {
            for (i in 0 until min(frames, stems.frameCount)) {
                val vl = stems.vocalsLeft[i]
                val vr = stems.vocalsRight[i]
                var al = stems.accompLeft[i]
                var ar = stems.accompRight[i]
                if (mixer.vocalTailCleanup) {
                    val cleaned = StemSeparatorEngine.applyVocalTailCleanup(al, ar, vl, vr, vGain)
                    al = cleaned.first
                    ar = cleaned.second
                }
                mixedL[i] = vl * vGain + al * aGain + stems.bassLeft[i] * bGain + stems.drumsLeft[i] * dGain
                mixedR[i] = vr * vGain + ar * aGain + stems.bassRight[i] * bGain + stems.drumsRight[i] * dGain
            }
        } else {
            System.arraycopy(sourceBuffer.left, 0, mixedL, 0, frames)
            System.arraycopy(sourceBuffer.right, 0, mixedR, 0, frames)
        }

        if (overdubBuffer != null) {
            val odFrames = min(frames, overdubBuffer.frameCount)
            for (i in 0 until odFrames) {
                mixedL[i] += overdubBuffer.left[i] * overdubGain
                mixedR[i] += overdubBuffer.right[i] * overdubGain
            }
        }

        onProgress(0.25f)

        // 2. Apply Pitch & Tempo
        val sonic = SonicTimePitchProcessor(sourceBuffer.sampleRate).apply {
            tempo = pitchTempo.tempoRatio
            pitch = pitchTempo.pitchFactor
        }
        val chunkFrames = 2048
        val outChunksL = ArrayList<FloatArray>()
        val outChunksR = ArrayList<FloatArray>()
        var totalOutFrames = 0
        val tmpL = FloatArray(chunkFrames)
        val tmpR = FloatArray(chunkFrames)
        val readL = FloatArray(chunkFrames * 2)
        val readR = FloatArray(chunkFrames * 2)

        var pos = 0
        while (pos < frames) {
            val len = min(chunkFrames, frames - pos)
            System.arraycopy(mixedL, pos, tmpL, 0, len)
            System.arraycopy(mixedR, pos, tmpR, 0, len)
            sonic.writeStereo(tmpL, tmpR, len)
            var got = sonic.readStereo(readL, readR, readL.size)
            while (got > 0) {
                outChunksL.add(readL.copyOf(got))
                outChunksR.add(readR.copyOf(got))
                totalOutFrames += got
                got = sonic.readStereo(readL, readR, readL.size)
            }
            pos += len
            if (pos % (chunkFrames * 8) == 0) {
                onProgress(0.25f + 0.30f * (pos.toFloat() / frames))
            }
        }
        sonic.flush()
        var rem = sonic.readStereo(readL, readR, readL.size)
        while (rem > 0) {
            outChunksL.add(readL.copyOf(rem))
            outChunksR.add(readR.copyOf(rem))
            totalOutFrames += rem
            rem = sonic.readStereo(readL, readR, readL.size)
        }

        val finalL = FloatArray(totalOutFrames)
        val finalR = FloatArray(totalOutFrames)
        var writePos = 0
        for (c in outChunksL.indices) {
            val cl = outChunksL[c]
            val cr = outChunksR[c]
            System.arraycopy(cl, 0, finalL, writePos, cl.size)
            System.arraycopy(cr, 0, finalR, writePos, cr.size)
            writePos += cl.size
        }

        onProgress(0.60f)

        // 3. Apply Studio EQ + Stereo Width + Reverb + Delay + Auto-Normalize Limiter
        val fxProc = AudioEffectsProcessor(sourceBuffer.sampleRate)
        fxProc.processBlock(finalL, finalR, totalOutFrames, fx, limiterCeilingDb)

        val renderedBuffer = AudioBuffer(sourceBuffer.sampleRate, finalL, finalR)
        onProgress(0.72f)

        // 4. Encode to chosen format into internal archive file
        withContext(Dispatchers.IO) {
            when (format) {
                ExportFormat.WAV -> AudioDecoder.writeWavFile(renderedBuffer, internalArchiveFile)
                ExportFormat.M4A -> encodeAacM4a(renderedBuffer, internalArchiveFile, bitrate.kbps * 1000, useM4aContainer = true) { p ->
                    onProgress(0.72f + 0.25f * p)
                }
                ExportFormat.AAC -> encodeAacM4a(renderedBuffer, internalArchiveFile, bitrate.kbps * 1000, useM4aContainer = false) { p ->
                    onProgress(0.72f + 0.25f * p)
                }
            }

            // Copy to user-picked SAF folder URI if requested
            if (externalUri != null) {
                context.contentResolver.openOutputStream(externalUri)?.use { out ->
                    internalArchiveFile.inputStream().use { inp ->
                        inp.copyTo(out)
                    }
                }
            }
        }
        onProgress(1.0f)
        renderedBuffer
    }

    private fun encodeAacM4a(
        buffer: AudioBuffer,
        outFile: File,
        bitRateBps: Int,
        useM4aContainer: Boolean,
        onEncodeProgress: (Float) -> Unit
    ) {
        outFile.parentFile?.mkdirs()
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val format = MediaFormat.createAudioFormat(mime, buffer.sampleRate, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRateBps)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = if (useM4aContainer) MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) else null
        val adtsOut = if (!useM4aContainer) BufferedOutputStream(FileOutputStream(outFile)) else null
        var muxerTrackIdx = -1
        var muxerStarted = false

        val frames = buffer.frameCount
        var frameIdx = 0
        val info = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        try {
            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val maxFrames = inBuf.remaining() / 4
                        val batch = min(maxFrames, frames - frameIdx)
                        if (batch <= 0) {
                            val pts = (frameIdx.toLong() * 1_000_000L) / buffer.sampleRate
                            codec.queueInputBuffer(inIdx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            val shortBuf = inBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            for (i in 0 until batch) {
                                val sL = (buffer.left[frameIdx + i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                                val sR = (buffer.right[frameIdx + i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                                shortBuf.put(sL)
                                shortBuf.put(sR)
                            }
                            val pts = (frameIdx.toLong() * 1_000_000L) / buffer.sampleRate
                            codec.queueInputBuffer(inIdx, 0, batch * 4, pts, 0)
                            frameIdx += batch
                            onEncodeProgress(frameIdx.toFloat() / frames.coerceAtLeast(1))
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxer != null && !muxerStarted) {
                            muxerTrackIdx = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            if (muxer != null && muxerStarted) {
                                muxer.writeSampleData(muxerTrackIdx, outBuf, info)
                            } else if (adtsOut != null) {
                                val data = ByteArray(info.size)
                                outBuf.get(data)
                                writeAdtsHeader(adtsOut, info.size + 7, buffer.sampleRate, 2)
                                adtsOut.write(data)
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true
                        }
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            if (muxer != null) {
                if (muxerStarted) muxer.stop()
                muxer.release()
            }
            adtsOut?.close()
        }
    }

    private fun writeAdtsHeader(out: BufferedOutputStream, packetLen: Int, sampleRate: Int, channels: Int) {
        val freqIdx = when (sampleRate) {
            48000 -> 3
            44100 -> 4
            32000 -> 5
            24000 -> 6
            22050 -> 7
            else -> 4
        }
        val profile = 2 // AAC LC
        val header = ByteArray(7)
        header[0] = 0xFF.toByte()
        header[1] = 0xF9.toByte()
        header[2] = (((profile - 1) shl 6) + (freqIdx shl 2) + (channels shr 2)).toByte()
        header[3] = (((channels and 3) shl 6) + (packetLen shr 11)).toByte()
        header[4] = ((packetLen and 0x7FF) shr 3).toByte()
        header[5] = (((packetLen and 7) shl 5) + 0x1F).toByte()
        header[6] = 0xFC.toByte()
        out.write(header)
    }
}
