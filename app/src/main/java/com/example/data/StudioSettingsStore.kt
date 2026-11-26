package com.example.data

import android.content.Context
import androidx.core.content.edit

class StudioSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("offline_studio_prefs", Context.MODE_PRIVATE)

    fun load(): AdvancedSettings {
        val latencyName = prefs.getString("latency_mode", AudioLatencyMode.BALANCED.name)
        val qualityName = prefs.getString("default_quality", ProcessingQuality.MAX_QUALITY.name)
        val vizName = prefs.getString("viz_style", VisualizerStyle.HYBRID.name)
        val loudnessName = prefs.getString("loudness_target", LoudnessTarget.BALANCED_14.name)
        val toneName = prefs.getString("metronome_tone", MetronomeTone.WOODBLOCK.name)
        return AdvancedSettings(
            darkTheme = prefs.getBoolean("dark_theme", true),
            keepScreenOn = prefs.getBoolean("keep_screen_on", true),
            latencyMode = AudioLatencyMode.entries.firstOrNull { it.name == latencyName } ?: AudioLatencyMode.BALANCED,
            defaultQuality = ProcessingQuality.entries.firstOrNull { it.name == qualityName } ?: ProcessingQuality.MAX_QUALITY,
            visualizerStyle = VisualizerStyle.entries.firstOrNull { it.name == vizName } ?: VisualizerStyle.HYBRID,
            overdubLatencyCompMs = prefs.getInt("overdub_latency_comp_ms", 35),
            autoNormalizeRecording = prefs.getBoolean("auto_norm_rec", true),
            limiterCeilingDb = prefs.getFloat("limiter_ceiling_db", -0.5f),
            loudnessTarget = LoudnessTarget.entries.firstOrNull { it.name == loudnessName } ?: LoudnessTarget.BALANCED_14,
            noiseGateEnabled = prefs.getBoolean("noise_gate_enabled", false),
            noiseGateThresholdDb = prefs.getFloat("noise_gate_threshold_db", -50f),
            metronomeTone = MetronomeTone.entries.firstOrNull { it.name == toneName } ?: MetronomeTone.WOODBLOCK
        )
    }

    fun save(settings: AdvancedSettings) {
        prefs.edit {
            putBoolean("dark_theme", settings.darkTheme)
            putBoolean("keep_screen_on", settings.keepScreenOn)
            putString("latency_mode", settings.latencyMode.name)
            putString("default_quality", settings.defaultQuality.name)
            putString("viz_style", settings.visualizerStyle.name)
            putInt("overdub_latency_comp_ms", settings.overdubLatencyCompMs)
            putBoolean("auto_norm_rec", settings.autoNormalizeRecording)
            putFloat("limiter_ceiling_db", settings.limiterCeilingDb)
            putString("loudness_target", settings.loudnessTarget.name)
            putBoolean("noise_gate_enabled", settings.noiseGateEnabled)
            putFloat("noise_gate_threshold_db", settings.noiseGateThresholdDb)
            putString("metronome_tone", settings.metronomeTone.name)
        }
    }
}
