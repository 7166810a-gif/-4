package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.AudioBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object AudioDecoder {
    const val TARGET_SAMPLE_RATE = 44100

    fun getDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "audio_track"
    }

    suspend fun decodeUriToFileAndBuffer(
        context: Context,
        uri: Uri,
        destWavFile: File
    ): AudioBuffer = withContext(Dispatchers.IO) {
        val rawBuffer = decodeWithMediaExtractor(context, uri)
        val normalized = if (rawBuffer.sampleRate == TARGET_SAMPLE_RATE) {
            rawBuffer
        } else {
            resampleStereo(rawBuffer, TARGET_SAMPLE_RATE)
        }
        writeWavFile(normalized, destWavFile)
        normalized
    }

    suspend fun loadWavFile(file: File): AudioBuffer = withContext(Dispatchers.IO) {
        if (!file.exists()) error("קובץ השמע לא נמצא במכשיר")
        readPcmWavFile(file) ?: decodeFileWithMediaExtractor(file)
    }

    suspend fun writeWavFile(buffer: AudioBuffer, outFile: File) = withContext(Dispatchers.IO) {
        outFile.parentFile?.mkdirs()
        val frames = buffer.frameCount
        val dataSize = frames * 4 // stereo 16-bit
        DataOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { out ->
            // RIFF header
            out.writeBytes("RIFF")
            writeIntLE(out, 36 + dataSize)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            writeIntLE(out, 16)
            writeShortLE(out, 1) // PCM
            writeShortLE(out, 2) // Stereo
            writeIntLE(out, buffer.sampleRate)
            writeIntLE(out, buffer.sampleRate * 4)
            writeShortLE(out, 4) // block align
            writeShortLE(out, 16) // bits per sample
            out.writeBytes("data")
            writeIntLE(out, dataSize)

            val chunk = ByteArray(4096)
            var idx = 0
            var bytePos = 0
            while (idx < frames) {
                val l = (buffer.left[idx].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                val r = (buffer.right[idx].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                chunk[bytePos++] = (l.toInt() and 0xFF).toByte()
                chunk[bytePos++] = ((l.toInt() shr 8) and 0xFF).toByte()
                chunk[bytePos++] = (r.toInt() and 0xFF).toByte()
                chunk[bytePos++] = ((r.toInt() shr 8) and 0xFF).toByte()
                if (bytePos == chunk.size) {
                    out.write(chunk, 0, bytePos)
                    bytePos = 0
                }
                idx++
            }
            if (bytePos > 0) out.write(chunk, 0, bytePos)
        }
    }

    private fun readPcmWavFile(file: File): AudioBuffer? {
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { inp ->
                val riff = ByteArray(4)
                inp.readFully(riff)
                if (String(riff) != "RIFF") return null
                readIntLE(inp)
                val wave = ByteArray(4)
                inp.readFully(wave)
                if (String(wave) != "WAVE") return null

                var channels = 2
                var sampleRate = 44100
                var bitsPerSample = 16
                var audioFormat = 1
                var dataBytes: ByteArray? = null

                while (inp.available() >= 8) {
                    val chunkIdBytes = ByteArray(4)
                    if (inp.read(chunkIdBytes) < 4) break
                    val chunkId = String(chunkIdBytes)
                    val chunkSize = readIntLE(inp).coerceAtLeast(0)
                    when (chunkId) {
                        "fmt " -> {
                            val fmtData = ByteArray(chunkSize)
                            inp.readFully(fmtData)
                            audioFormat = (fmtData[0].toInt() and 0xFF) or ((fmtData[1].toInt() and 0xFF) shl 8)
                            channels = (fmtData[2].toInt() and 0xFF) or ((fmtData[3].toInt() and 0xFF) shl 8)
                            sampleRate = (fmtData[4].toInt() and 0xFF) or
                                ((fmtData[5].toInt() and 0xFF) shl 8) or
                                ((fmtData[6].toInt() and 0xFF) shl 16) or
                                ((fmtData[7].toInt() and 0xFF) shl 24)
                            bitsPerSample = (fmtData[14].toInt() and 0xFF) or ((fmtData[15].toInt() and 0xFF) shl 8)
                        }
                        "data" -> {
                            if (audioFormat != 1 || bitsPerSample != 16) return null
                            val bytes = ByteArray(chunkSize)
                            inp.readFully(bytes)
                            dataBytes = bytes
                            break
                        }
                        else -> {
                            var skip = chunkSize.toLong()
                            if (skip % 2L == 1L) skip++
                            inp.skipBytes(skip.toInt())
                        }
                    }
                }
                val pcm = dataBytes ?: return null
                val bytesPerFrame = channels * 2
                val frames = pcm.size / bytesPerFrame
                val left = FloatArray(frames)
                val right = FloatArray(frames)
                var pos = 0
                for (i in 0 until frames) {
                    val sL = ((pcm[pos].toInt() and 0xFF) or (pcm[pos + 1].toInt() shl 8)).toShort()
                    val fL = sL / 32768f
                    left[i] = fL
                    if (channels >= 2) {
                        val sR = ((pcm[pos + 2].toInt() and 0xFF) or (pcm[pos + 3].toInt() shl 8)).toShort()
                        right[i] = sR / 32768f
                    } else {
                        right[i] = fL
                    }
                    pos += bytesPerFrame
                }
                val buf = AudioBuffer(sampleRate, left, right)
                if (sampleRate == TARGET_SAMPLE_RATE) buf else resampleStereo(buf, TARGET_SAMPLE_RATE)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeWithMediaExtractor(context: Context, uri: Uri): AudioBuffer {
        val extractor = MediaExtractor()
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            extractor.setDataSource(pfd.fileDescriptor)
        } ?: error("לא ניתן לפתוח את קובץ השמע")
        return extractAndDecode(extractor)
    }

    private fun decodeFileWithMediaExtractor(file: File): AudioBuffer {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        return extractAndDecode(extractor)
    }

    private fun extractAndDecode(extractor: MediaExtractor): AudioBuffer {
        var audioTrackIdx = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIdx = i
                format = f
                break
            }
        }
        if (audioTrackIdx < 0 || format == null) {
            extractor.release()
            error("הקובץ אינו מכיל ערוץ שמע תקין")
        }

        extractor.selectTrack(audioTrackIdx)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmChunks = ArrayList<ShortArray>()
        var totalShorts = 0
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        try {
            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val shorts = ShortArray(shortBuf.remaining())
                            shortBuf.get(shorts)
                            pcmChunks.add(shorts)
                            totalShorts += shorts.size
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        val ch = channelCount.coerceAtLeast(1)
        val frames = totalShorts / ch
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        var frameIdx = 0
        for (chunk in pcmChunks) {
            var i = 0
            while (i + ch <= chunk.size && frameIdx < frames) {
                val l = chunk[i] / 32768f
                val r = if (ch >= 2) chunk[i + 1] / 32768f else l
                left[frameIdx] = l
                right[frameIdx] = r
                frameIdx++
                i += ch
            }
        }
        return AudioBuffer(sampleRate, left, right)
    }

    fun resampleStereo(input: AudioBuffer, targetRate: Int): AudioBuffer {
        if (input.sampleRate == targetRate || input.frameCount == 0) return input
        val ratio = input.sampleRate.toDouble() / targetRate.toDouble()
        val outFrames = ((input.frameCount / ratio).toInt()).coerceAtLeast(1)
        val outL = FloatArray(outFrames)
        val outR = FloatArray(outFrames)
        val maxIdx = input.frameCount - 1
        for (i in 0 until outFrames) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, maxIdx)
            val idx1 = (idx0 + 1).coerceIn(0, maxIdx)
            val frac = (srcPos - idx0).toFloat()
            outL[i] = input.left[idx0] + frac * (input.left[idx1] - input.left[idx0])
            outR[i] = input.right[idx0] + frac * (input.right[idx1] - input.right[idx0])
        }
        return AudioBuffer(targetRate, outL, outR)
    }

    /**
     * Generates a rich 16-second 110-BPM stereo studio practice loop containing:
     * - Punchy kick & snare & stereo hi-hats (Drums)
     * - Deep warm sub-bassline (Bass)
     * - Wide stereo electric piano chords (Accompaniment)
     * - Center-panned expressive vocal-register lead melody (Vocals)
     * Ideal for testing 4-stem separation, pitch/tempo shifting, EQ, waveform editing, and export offline.
     */
    fun generatePracticeLoop(bpm: Int = 110): AudioBuffer {
        val sr = TARGET_SAMPLE_RATE
        val durationSec = 16.0
        val totalFrames = (sr * durationSec).toInt()
        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)

        val beatDur = 60.0 / bpm
        val barDur = beatDur * 4.0

        // Chord progression: Am7 - Fmaj7 - Cmaj7 - G6
        val chordFreqs = arrayOf(
            doubleArrayOf(220.0, 261.63, 329.63, 392.0),
            doubleArrayOf(174.61, 220.0, 261.63, 329.63),
            doubleArrayOf(130.81, 196.0, 246.94, 329.63),
            doubleArrayOf(196.0, 246.94, 293.66, 329.63)
        )
        val bassNotes = doubleArrayOf(110.0, 87.31, 65.41, 98.0)
        // Center-panned vocal-register melody notes (Hz) per 8th note
        val melodyNotes = doubleArrayOf(
            440.0, 523.25, 493.88, 440.0, 392.0, 440.0, 523.25, 0.0,
            349.23, 440.0, 523.25, 493.88, 440.0, 392.0, 329.63, 0.0,
            523.25, 587.33, 659.25, 587.33, 523.25, 493.88, 440.0, 392.0,
            392.0, 440.0, 493.88, 587.33, 523.25, 493.88, 440.0, 0.0
        )

        var seed = 1337L
        fun nextNoise(): Float {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 33).toFloat() / Int.MAX_VALUE.toFloat()) * 2f - 1f
        }

        for (i in 0 until totalFrames) {
            val t = i.toDouble() / sr
            val barIdx = ((t / barDur).toInt()) % chordFreqs.size
            val timeInBeat = t % beatDur
            val beatIdx = ((t / beatDur).toInt()) % 4
            val eighthDur = beatDur * 0.5
            val timeInEighth = t % eighthDur
            val melodyIdx = ((t / eighthDur).toInt()) % melodyNotes.size

            // 1. Drums (Kick on 0,2; Snare on 1,3; Hats on 8ths)
            var kick = 0.0
            if (beatIdx == 0 || beatIdx == 2) {
                if (timeInBeat < 0.25) {
                    val pitchEnv = 52.0 + 110.0 * exp(-timeInBeat * 32.0)
                    kick = sin(2.0 * PI * pitchEnv * timeInBeat) * exp(-timeInBeat * 14.0) * 0.52
                }
            }
            var snare = 0.0
            if (beatIdx == 1 || beatIdx == 3) {
                if (timeInBeat < 0.18) {
                    val body = sin(2.0 * PI * 195.0 * timeInBeat) * exp(-timeInBeat * 22.0)
                    val snap = nextNoise() * exp(-timeInBeat * 26.0)
                    snare = (body * 0.35 + snap * 0.45) * 0.42
                }
            }
            var hatL = 0.0
            var hatR = 0.0
            if (timeInEighth < 0.07) {
                val n = nextNoise() * exp(-timeInEighth * 65.0) * 0.16
                hatL = n * 0.75
                hatR = n * 1.15
            }

            // 2. Bass (sub + 2nd harmonic)
            val bassF = bassNotes[barIdx]
            val bassEnv = exp(-timeInBeat * 2.5) * 0.7 + 0.3
            val bass = (sin(2.0 * PI * bassF * t) * 0.75 + sin(2.0 * PI * bassF * 2.0 * t) * 0.25) * bassEnv * 0.28

            // 3. Stereo Accompaniment Chords (wide L/R detuned Rhodes)
            var chordL = 0.0
            var chordR = 0.0
            val chord = chordFreqs[barIdx]
            val chordEnv = exp(-(t % barDur) * 0.7) * 0.65 + 0.35
            for (cIdx in chord.indices) {
                val f = chord[cIdx]
                val sL = sin(2.0 * PI * (f * 0.997) * t) + 0.2 * sin(2.0 * PI * f * 2.0 * t)
                val sR = sin(2.0 * PI * (f * 1.003) * t) + 0.2 * sin(2.0 * PI * f * 2.0 * t)
                if (cIdx % 2 == 0) {
                    chordL += sL * 0.9; chordR += sR * 0.45
                } else {
                    chordL += sL * 0.45; chordR += sR * 0.9
                }
            }
            chordL *= chordEnv * 0.075
            chordR *= chordEnv * 0.075

            // 4. Center-panned Lead / Vocal-register Synth
            val melF = melodyNotes[melodyIdx]
            var vocal = 0.0
            if (melF > 0.0) {
                val vibrato = 1.0 + 0.004 * sin(2.0 * PI * 5.2 * t)
                val mf = melF * vibrato
                val attack = (timeInEighth / 0.02).coerceAtMost(1.0)
                val decay = exp(-timeInEighth * 2.2)
                vocal = (sin(2.0 * PI * mf * t) * 0.65 +
                    sin(2.0 * PI * mf * 2.0 * t) * 0.25 +
                    sin(2.0 * PI * mf * 3.0 * t) * 0.10) * attack * decay * 0.26
            }

            left[i] = (kick + snare + hatL + bass + chordL + vocal).toFloat().coerceIn(-0.98f, 0.98f)
            right[i] = (kick + snare + hatR + bass + chordR + vocal).toFloat().coerceIn(-0.98f, 0.98f)
        }
        return AudioBuffer(sr, left, right)
    }

    private fun writeIntLE(out: DataOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }

    private fun writeShortLE(out: DataOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private fun readIntLE(inp: DataInputStream): Int {
        val b0 = inp.readUnsignedByte()
        val b1 = inp.readUnsignedByte()
        val b2 = inp.readUnsignedByte()
        val b3 = inp.readUnsignedByte()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
