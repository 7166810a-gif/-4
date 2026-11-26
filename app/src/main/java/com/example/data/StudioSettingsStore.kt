package com.example.data

import android.content.Context

class StudioSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("offline_studio_prefs", Context.MODE_PRIVATE)

    fun load(): AdvancedSettings {
        val latencyName = prefs.getString("latency_mode", AudioLatencyMode.BALANCED.name)
        val qualityName = prefs.getString("default_quality", ProcessingQuality.MAX_QUALITY.name)
        val vizName = prefs.getString("viz_style", VisualizerStyle.HYBRID.name)
        return AdvancedSettings(
            darkTheme = prefs.getBoolean("dark_theme", true),
            keepScreenOn = prefs.getBoolean("keep_screen_on", true),
            latencyMode = AudioLatencyMode.entries.firstOrNull { it.name == latencyName } ?: AudioLatencyMode.BALANCED,
            defaultQuality = ProcessingQuality.entries.firstOrNull { it.name == qualityName } ?: ProcessingQuality.MAX_QUALITY,
            visualizerStyle = VisualizerStyle.entries.firstOrNull { it.name == vizName } ?: VisualizerStyle.HYBRID,
            overdubLatencyCompMs = prefs.getInt("overdub_latency_comp_ms", 35),
            autoNormalizeRecording = prefs.getBoolean("auto_norm_rec", true),
            limiterCeilingDb = prefs.getFloat("limiter_ceiling_db", -0.5f)
        )
    }

    fun save(settings: AdvancedSettings) {
        prefs.edit()
            .putBoolean("dark_theme", settings.darkTheme)
            .putBoolean("keep_screen_on", settings.keepScreenOn)
            .putString("latency_mode", settings.latencyMode.name)
            .putString("default_quality", settings.defaultQuality.name)
            .putString("viz_style", settings.visualizerStyle.name)
            .putInt("overdub_latency_comp_ms", settings.overdubLatencyCompMs)
            .putBoolean("auto_norm_rec", settings.autoNormalizeRecording)
            .putFloat("limiter_ceiling_db", settings.limiterCeilingDb)
            .apply()
    }
}
