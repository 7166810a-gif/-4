package com.example.data

enum class StudioTab(val titleHe: String) {
    LIBRARY("ספרייה"),
    MIXER("מיקסר"),
    WAVEFORM("גל קול"),
    EFFECTS("אפקטים"),
    REHEARSAL("חזרות")
}

enum class StemPreset(val titleHe: String, val subtitleHe: String) {
    FULL_PLAYBACK("פלייבק מלא", "ללא שירה"),
    RHYTHM_PLAYBACK("פלייבק קצב", "תופים + בס בלבד"),
    HARMONIC_PLAYBACK("פלייבק הרמוני", "כלים ללא תופים ושירה"),
    VOCALS_ONLY("בידוד שירה", "אקפלה"),
    CUSTOM("מיקס מותאם", "שליטה ידנית")
}

enum class ProcessingQuality(val titleHe: String, val fftSize: Int, val hopSize: Int) {
    FAST("מצב מהיר", 2048, 512),
    MAX_QUALITY("איכות מקסימלית", 4096, 512)
}

enum class EqPreset(
    val titleHe: String,
    val bassDb: Float,
    val midDb: Float,
    val trebleDb: Float
) {
    BALANCED("מאוזן", 0f, 0f, 0f),
    BASS_BOOST("חיזוק באס", 5.5f, -0.5f, 1.0f),
    VOCAL_CLARITY("בהירות קולית", -2.0f, 3.5f, 4.0f),
    ACOUSTIC("אקוסטי", 2.0f, 1.5f, 2.5f),
    LIVE_STAGE("במה חיה", 3.5f, -1.0f, 3.5f)
}

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    WAV("WAV (ללא דחיסה - איכות אולפן)", "wav", "audio/wav"),
    M4A("M4A (AAC איכותי)", "m4a", "audio/mp4"),
    AAC("AAC (זרם דחוס מהיר)", "aac", "audio/aac")
}

enum class ExportBitrate(val kbps: Int) {
    K128(128),
    K192(192),
    K320(320)
}

enum class LibraryFilter(val titleHe: String) {
    ALL("הכל"),
    IMPORTED("שירים שיובאו"),
    PLAYBACKS("פלייבקים שיוצאו"),
    RECORDINGS("הקלטות")
}

class AudioBuffer(
    val sampleRate: Int = 44100,
    val left: FloatArray,
    val right: FloatArray
) {
    val frameCount: Int get() = minOf(left.size, right.size)
    val durationMs: Long get() = ((frameCount.toDouble() / sampleRate) * 1000.0).toLong()

    fun copy(): AudioBuffer = AudioBuffer(sampleRate, left.copyOf(), right.copyOf())
}

class StemSet(
    val sampleRate: Int = 44100,
    val vocalsLeft: FloatArray,
    val vocalsRight: FloatArray,
    val accompLeft: FloatArray,
    val accompRight: FloatArray,
    val bassLeft: FloatArray,
    val bassRight: FloatArray,
    val drumsLeft: FloatArray,
    val drumsRight: FloatArray
) {
    val frameCount: Int get() = vocalsLeft.size
}

data class MixerState(
    val stemsSeparated: Boolean = false,
    val vocalsGain: Float = 1f,
    val accompGain: Float = 1f,
    val bassGain: Float = 1f,
    val drumsGain: Float = 1f,
    val vocalsMute: Boolean = false,
    val accompMute: Boolean = false,
    val bassMute: Boolean = false,
    val drumsMute: Boolean = false,
    val vocalsSolo: Boolean = false,
    val accompSolo: Boolean = false,
    val bassSolo: Boolean = false,
    val drumsSolo: Boolean = false,
    val activePreset: StemPreset = StemPreset.CUSTOM,
    val vocalTailCleanup: Boolean = true,
    val qualityMode: ProcessingQuality = ProcessingQuality.MAX_QUALITY
)

data class PitchTempoState(
    val semitones: Int = 0,          // -12 .. +12
    val fineCents: Int = 0,          // -50 .. +50
    val tempoRatio: Float = 1.0f     // 0.50 .. 1.50
) {
    val totalSemitones: Float get() = semitones + (fineCents / 100f)
    val pitchFactor: Float get() = Math.pow(2.0, totalSemitones / 12.0).toFloat()
}

data class EqAndFxState(
    val bassDb: Float = 0f,
    val midDb: Float = 0f,
    val trebleDb: Float = 0f,
    val activeEqPreset: EqPreset = EqPreset.BALANCED,
    val autoNormalizeLimiter: Boolean = true,
    val reverbMix: Float = 0f,      // 0f .. 0.6f
    val delayMix: Float = 0f        // 0f .. 0.5f
)

data class RehearsalState(
    val loopEnabled: Boolean = false,
    val loopARatio: Float = 0.2f,
    val loopBRatio: Float = 0.6f,
    val metronomeEnabled: Boolean = false,
    val bpm: Int = 110,
    val countInEnabled: Boolean = false,
    val metronomeVolume: Float = 0.7f,
    val isRecordingOverdub: Boolean = false,
    val hasOverdubTrack: Boolean = false,
    val overdubGain: Float = 1.0f,
    val micLevel: Float = 0f
)

data class WaveformEditState(
    val zoomLevel: Float = 1f,
    val scrollOffsetRatio: Float = 0f,
    val selectionStartRatio: Float = 0.1f,
    val selectionEndRatio: Float = 0.9f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val waveformPeaks: List<Float> = emptyList()
)

data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val activeTrackTitle: String = "טרם נטען קובץ שמע",
    val activeTrackFormat: String = "",
    val activeTrackId: Long? = null
) {
    val progressRatio: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

data class ProcessingProgress(
    val isActive: Boolean = false,
    val titleHe: String = "",
    val progress: Float = 0f
)
