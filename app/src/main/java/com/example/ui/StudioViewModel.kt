package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioAnalyzer
import com.example.audio.AudioDecoder
import com.example.audio.AudioEngineController
import com.example.audio.AudioExporter
import com.example.audio.StemSeparatorEngine
import com.example.data.AdvancedSettings
import com.example.data.AudioAnalysisResult
import com.example.data.AudioLatencyMode
import com.example.data.EqAndFxState
import com.example.data.EqPreset
import com.example.data.ExportBitrate
import com.example.data.ExportFormat
import com.example.data.LibraryFilter
import com.example.data.MixerState
import com.example.data.PitchTempoState
import com.example.data.PlaybackUiState
import com.example.data.ProcessingProgress
import com.example.data.ProcessingQuality
import com.example.data.RehearsalState
import com.example.data.StemPreset
import com.example.data.StudioDatabase
import com.example.data.StudioSettingsStore
import com.example.data.StudioTab
import com.example.data.TrackEntity
import com.example.data.TrackRepository
import com.example.data.VisualizerStyle
import com.example.data.WaveformEditState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TrackRepository(StudioDatabase.getInstance(application).trackDao())
    private val settingsStore = StudioSettingsStore(application)
    private val engine = AudioEngineController()

    private val studioDir = File(application.filesDir, "studio_audio").apply { mkdirs() }
    private val exportDir = File(application.filesDir, "studio_exports").apply { mkdirs() }

    private val _advancedSettings = MutableStateFlow(settingsStore.load())
    val advancedSettings: StateFlow<AdvancedSettings> = _advancedSettings.asStateFlow()

    private val _selectedTab = MutableStateFlow(StudioTab.LIBRARY)
    val selectedTab: StateFlow<StudioTab> = _selectedTab.asStateFlow()

    private val _darkTheme = MutableStateFlow(_advancedSettings.value.darkTheme)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _libraryFilter = MutableStateFlow(LibraryFilter.ALL)
    val libraryFilter: StateFlow<LibraryFilter> = _libraryFilter.asStateFlow()

    val filteredTracks: StateFlow<List<TrackEntity>> = combine(
        repository.allTracks,
        _searchQuery,
        _libraryFilter
    ) { tracks, query, filter ->
        tracks.filter { track ->
            val matchesQuery = query.isBlank() ||
                track.title.contains(query, ignoreCase = true) ||
                track.format.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.IMPORTED -> track.category == "IMPORTED"
                LibraryFilter.PLAYBACKS -> track.category == "PLAYBACK"
                LibraryFilter.RECORDINGS -> track.category == "RECORDING"
            }
            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTracks: StateFlow<List<TrackEntity>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeTrackTitle = MutableStateFlow("טרם נטען קובץ שמע")
    private val _activeTrackFormat = MutableStateFlow("")
    private val _activeTrackId = MutableStateFlow<Long?>(null)

    private val playbackProgressFlow = combine(
        engine.isPlaying,
        engine.positionMs,
        engine.durationMs
    ) { isPlaying, positionMs, durationMs ->
        Triple(isPlaying, positionMs, durationMs)
    }

    private val activeMetaFlow = combine(
        _activeTrackTitle,
        _activeTrackFormat,
        _activeTrackId
    ) { title, format, id ->
        Triple(title, format, id)
    }

    val playbackState: StateFlow<PlaybackUiState> = combine(
        playbackProgressFlow,
        activeMetaFlow
    ) { (isPlaying, positionMs, durationMs), (title, format, id) ->
        PlaybackUiState(
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            activeTrackTitle = title,
            activeTrackFormat = format,
            activeTrackId = id
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackUiState())

    private val _mixerState = MutableStateFlow(MixerState())
    val mixerState: StateFlow<MixerState> = _mixerState.asStateFlow()

    private val _pitchTempoState = MutableStateFlow(PitchTempoState())
    val pitchTempoState: StateFlow<PitchTempoState> = _pitchTempoState.asStateFlow()

    private val _eqFxState = MutableStateFlow(EqAndFxState())
    val eqFxState: StateFlow<EqAndFxState> = _eqFxState.asStateFlow()

    private val _rehearsalState = MutableStateFlow(RehearsalState())
    val rehearsalState: StateFlow<RehearsalState> = combine(_rehearsalState, engine.micLevel) { reh, mic ->
        reh.copy(micLevel = mic)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RehearsalState())

    val liveWaveform: StateFlow<FloatArray> = engine.liveWaveform
    val stereoPeaks: StateFlow<Pair<Float, Float>> = engine.stereoPeaks

    private val _analysisResult = MutableStateFlow(AudioAnalysisResult())
    val analysisResult: StateFlow<AudioAnalysisResult> = _analysisResult.asStateFlow()

    private val _waveformState = MutableStateFlow(WaveformEditState())
    val waveformState: StateFlow<WaveformEditState> = _waveformState.asStateFlow()

    private val _processing = MutableStateFlow(ProcessingProgress())
    val processing: StateFlow<ProcessingProgress> = _processing.asStateFlow()

    private val _showExportSheet = MutableStateFlow(false)
    val showExportSheet: StateFlow<Boolean> = _showExportSheet.asStateFlow()

    private val _showSettingsSheet = MutableStateFlow(false)
    val showSettingsSheet: StateFlow<Boolean> = _showSettingsSheet.asStateFlow()

    private val _cacheSizeMb = MutableStateFlow(0f)
    val cacheSizeMb: StateFlow<Float> = _cacheSizeMb.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val tapTimestamps = ArrayDeque<Long>()

    init {
        engine.advancedSettings = _advancedSettings.value
        _mixerState.update { it.copy(qualityMode = _advancedSettings.value.defaultQuality) }
        refreshCacheSize()
        viewModelScope.launch {
            engine.playbackError.collect { message ->
                if (!message.isNullOrBlank()) {
                    _snackbarMessages.emit("שגיאה בהשמעה: $message")
                }
            }
        }

    fun selectTab(tab: StudioTab) {
        _selectedTab.value = tab
    }

    fun toggleDarkTheme() {
        val next = !_darkTheme.value
        _darkTheme.value = next
        updateAdvancedSettings { it.copy(darkTheme = next) }
    }

    fun setShowSettingsSheet(show: Boolean) {
        if (show) refreshCacheSize()
        _showSettingsSheet.value = show
    }

    fun updateLatencyMode(mode: AudioLatencyMode) = updateAdvancedSettings { it.copy(latencyMode = mode) }
    fun updateDefaultQuality(q: ProcessingQuality) {
        updateAdvancedSettings { it.copy(defaultQuality = q) }
        _mixerState.update { it.copy(qualityMode = q) }
    }
    fun updateVisualizerStyle(style: VisualizerStyle) = updateAdvancedSettings { it.copy(visualizerStyle = style) }
    fun updateKeepScreenOn(enabled: Boolean) = updateAdvancedSettings { it.copy(keepScreenOn = enabled) }
    fun updateOverdubLatencyComp(ms: Int) = updateAdvancedSettings { it.copy(overdubLatencyCompMs = ms.coerceIn(-120, 120)) }
    fun updateAutoNormalizeRecording(enabled: Boolean) = updateAdvancedSettings { it.copy(autoNormalizeRecording = enabled) }
    fun updateLimiterCeiling(db: Float) = updateAdvancedSettings { it.copy(limiterCeilingDb = db.coerceIn(-3.0f, -0.1f)) }

    private fun updateAdvancedSettings(transform: (AdvancedSettings) -> AdvancedSettings) {
        val updated = transform(_advancedSettings.value)
        _advancedSettings.value = updated
        _darkTheme.value = updated.darkTheme
        engine.advancedSettings = updated
        settingsStore.save(updated)
    }

    private fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = (studioDir.listFiles()?.sumOf { it.length() } ?: 0L) +
                (exportDir.listFiles()?.sumOf { it.length() } ?: 0L)
            _cacheSizeMb.value = bytes / (1024f * 1024f)
        }
    }

    fun clearOrphanedCacheFiles() {
        viewModelScope.launch {
            val activePaths = allTracks.value.map { it.filePath }.toSet()
            var removed = 0
            withContext(Dispatchers.IO) {
                listOf(studioDir, exportDir).forEach { dir ->
                    dir.listFiles()?.forEach { file ->
                        if (file.absolutePath !in activePaths && file.delete()) {
                            removed++
                        }
                    }
                }
            }
            refreshCacheSize()
            _snackbarMessages.emit(
                if (removed > 0) "נוקו $removed קבצי מטמון זמניים" else "אין קבצי מטמון מיותרים לניקוי"
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateLibraryFilter(filter: LibraryFilter) {
        _libraryFilter.value = filter
    }

    fun setShowExportSheet(show: Boolean) {
        _showExportSheet.value = show
    }

    fun hasLoadedAudio(): Boolean = engine.currentBuffer != null

    // --- File Import & Library Management ---

    fun importAudioUri(uri: Uri) {
        viewModelScope.launch {
            _processing.value = ProcessingProgress(true, "מפענח ומייבא קובץ שמע...", 0.15f)
            try {
                val appCtx = getApplication<Application>()
                val rawName = AudioDecoder.getDisplayName(appCtx, uri)
                val ext = rawName.substringAfterLast('.', "AUDIO").uppercase()
                val title = rawName.substringBeforeLast('.').ifBlank { "רצועה חדשה" }
                val internalWav = File(studioDir, "track_${UUID.randomUUID()}.wav")

                _processing.value = ProcessingProgress(true, "ממיר לפורמט אולפן (44.1kHz)...", 0.45f)
                val buffer = AudioDecoder.decodeUriToFileAndBuffer(appCtx, uri, internalWav)

                val entity = TrackEntity(
                    title = title,
                    filePath = internalWav.absolutePath,
                    format = ext,
                    durationMs = buffer.durationMs,
                    sampleRate = buffer.sampleRate,
                    category = "IMPORTED",
                    stemsSeparated = false
                )
                val newId = repository.insertTrack(entity)
                activateTrack(entity.copy(id = newId), buffer)
                _selectedTab.value = StudioTab.MIXER
                _snackbarMessages.emit("הקובץ \"$title\" יובא בהצלחה")
            } catch (e: Exception) {
                _snackbarMessages.emit("שגיאה בייבוא הקובץ: ${e.message ?: "פורמט לא נתמך"}")
            } finally {
                _processing.value = ProcessingProgress(false)
            }
        }
    }

    fun createBuiltInPracticeLoop() {
        viewModelScope.launch {
            _processing.value = ProcessingProgress(true, "יוצר לופ אימון מובנה באופליין...", 0.4f)
            try {
                val buffer = withContext(Dispatchers.Default) {
                    AudioDecoder.generatePracticeLoop(bpm = 110)
                }
                val file = File(studioDir, "practice_${UUID.randomUUID()}.wav")
                AudioDecoder.writeWavFile(buffer, file)
                val entity = TrackEntity(
                    title = "לופ אימון (Am - F - C - G)",
                    filePath = file.absolutePath,
                    format = "WAV",
                    durationMs = buffer.durationMs,
                    sampleRate = buffer.sampleRate,
                    category = "IMPORTED",
                    stemsSeparated = false
                )
                val newId = repository.insertTrack(entity)
                activateTrack(entity.copy(id = newId), buffer)
                _selectedTab.value = StudioTab.MIXER
                _snackbarMessages.emit("לופ אימון מוכן! לחץ על 'הפרד ערוצים' כדי לנסות יצירת פלייבק")
            } catch (e: Exception) {
                _snackbarMessages.emit("שגיאה ביצירת לופ: ${e.message}")
            } finally {
                _processing.value = ProcessingProgress(false)
            }
        }
    }

    fun loadTrackFromLibrary(track: TrackEntity) {
        viewModelScope.launch {
            _processing.value = ProcessingProgress(true, "טוען רצועה מהמכשיר...", 0.3f)
            try {
                val buffer = AudioDecoder.loadWavFile(File(track.filePath))
                activateTrack(track, buffer)
                _selectedTab.value = StudioTab.MIXER
            } catch (e: Exception) {
                _snackbarMessages.emit("לא ניתן לטעון את הרצועה: ${e.message}")
            } finally {
                _processing.value = ProcessingProgress(false)
            }
        }
    }

    fun renameTrack(track: TrackEntity, newTitle: String) {
        val cleaned = newTitle.trim()
        if (cleaned.isBlank()) return
        viewModelScope.launch {
            repository.renameTrack(track.id, cleaned)
            if (_activeTrackId.value == track.id) {
                _activeTrackTitle.value = cleaned
            }
            _snackbarMessages.emit("שם הרצועה עודכן ל-\"$cleaned\"")
        }
    }

    fun deleteTrack(track: TrackEntity) {
        viewModelScope.launch {
            if (_activeTrackId.value == track.id) {
                engine.stopPlayback()
            }
            withContext(Dispatchers.IO) {
                runCatching { File(track.filePath).delete() }
            }
            repository.deleteTrack(track.id)
            refreshCacheSize()
            _snackbarMessages.emit("הרצועה \"${track.title}\" נמחקה")
        }
    }

    private fun activateTrack(track: TrackEntity, buffer: com.example.data.AudioBuffer) {
        engine.loadBuffer(buffer, stems = null, clearHistory = true)
        _activeTrackId.value = track.id
        _activeTrackTitle.value = track.title
        _activeTrackFormat.value = track.format
        _mixerState.value = MixerState(qualityMode = _advancedSettings.value.defaultQuality)
        _pitchTempoState.value = PitchTempoState()
        _rehearsalState.update { it.copy(hasOverdubTrack = false, isRecordingOverdub = false, loopEnabled = false) }
        _analysisResult.value = AudioAnalysisResult()
        syncEngineStates()
        refreshWaveformState()
        refreshCacheSize()
        analyzeCurrentTrack()
    }

    fun analyzeCurrentTrack() {
        val buf = engine.currentBuffer ?: return
        viewModelScope.launch {
            val result = AudioAnalyzer.analyze(buf)
            _analysisResult.value = result
        }
    }

    fun syncMetronomeToDetectedBpm() {
        val bpm = _analysisResult.value.detectedBpm
        if (bpm in 40..240) {
            updateBpm(bpm)
            viewModelScope.launch { _snackbarMessages.emit("המטרונום סונכרן לקצב השיר ($bpm BPM)") }
        }
    }

    // --- Transport Controls ---

    fun togglePlayPause() {
        if (!hasLoadedAudio()) {
            viewModelScope.launch { _snackbarMessages.emit("בחר או ייבא קובץ שמע תחילה") }
            return
        }
        engine.togglePlayPause()
    }

    fun playWithCountInIfEnabled() {
        if (!hasLoadedAudio()) return
        if (engine.isPlaying.value) {
            engine.pausePlayback()
        } else {
            engine.startPlayback(withOptionalCountIn = true)
        }
    }

    fun stopPlayback() {
        engine.stopPlayback()
    }

    fun seekToRatio(ratio: Float) {
        engine.seekToRatio(ratio)
    }

    // --- 4-Stem Separation & Mixer ---

    fun separateStemsAndCreatePlayback(presetAfter: StemPreset = StemPreset.FULL_PLAYBACK) {
        val buf = engine.currentBuffer ?: run {
            viewModelScope.launch { _snackbarMessages.emit("יש לטעון שיר לפני הפרדת הערוצים") }
            return
        }
        viewModelScope.launch {
            val quality = _mixerState.value.qualityMode
            _processing.value = ProcessingProgress(true, "מפריד ערוצים מקומית (${quality.titleHe})...", 0.05f)
            try {
                val stems = StemSeparatorEngine.separateStems(buf, quality) { p ->
                    _processing.value = ProcessingProgress(true, "מפריד שירה, כלים, בס ותופים...", p)
                }
                engine.setSeparatedStems(stems)
                _mixerState.update { it.copy(stemsSeparated = true) }
                applyStemPreset(presetAfter)
                _activeTrackId.value?.let { id ->
                    repository.updateTrackMetadata(id, separated = true, durationMs = buf.durationMs)
                }
                _snackbarMessages.emit("הפרדת 4 הערוצים הושלמה בהצלחה באופליין!")
            } catch (e: Exception) {
                _snackbarMessages.emit("שגיאה בהפרדת ערוצים: ${e.message}")
            } finally {
                _processing.value = ProcessingProgress(false)
            }
        }
    }

    fun applyStemPreset(preset: StemPreset) {
        _mixerState.update { m ->
            val updated = when (preset) {
                StemPreset.FULL_PLAYBACK -> m.copy(
                    vocalsGain = 0f, vocalsMute = true, vocalsSolo = false,
                    accompGain = 1.05f, accompMute = false, accompSolo = false,
                    bassGain = 1.05f, bassMute = false, bassSolo = false,
                    drumsGain = 1.05f, drumsMute = false, drumsSolo = false,
                    activePreset = preset
                )
                StemPreset.RHYTHM_PLAYBACK -> m.copy(
                    vocalsGain = 0f, vocalsMute = true, vocalsSolo = false,
                    accompGain = 0f, accompMute = true, accompSolo = false,
                    bassGain = 1.15f, bassMute = false, bassSolo = false,
                    drumsGain = 1.15f, drumsMute = false, drumsSolo = false,
                    activePreset = preset
                )
                StemPreset.HARMONIC_PLAYBACK -> m.copy(
                    vocalsGain = 0f, vocalsMute = true, vocalsSolo = false,
                    accompGain = 1.15f, accompMute = false, accompSolo = false,
                    bassGain = 0.85f, bassMute = false, bassSolo = false,
                    drumsGain = 0f, drumsMute = true, drumsSolo = false,
                    activePreset = preset
                )
                StemPreset.VOCALS_ONLY -> m.copy(
                    vocalsGain = 1.15f, vocalsMute = false, vocalsSolo = true,
                    accompGain = 0f, accompMute = false, accompSolo = false,
                    bassGain = 0f, bassMute = false, bassSolo = false,
                    drumsGain = 0f, drumsMute = false, drumsSolo = false,
                    activePreset = preset
                )
                StemPreset.CUSTOM -> m.copy(activePreset = preset)
            }
            updated
        }
        syncEngineStates()
    }

    fun updateStemGain(stemIndex: Int, gain: Float) {
        _mixerState.update { m ->
            val g = gain.coerceIn(0f, 1.5f)
            when (stemIndex) {
                0 -> m.copy(vocalsGain = g, activePreset = StemPreset.CUSTOM)
                1 -> m.copy(accompGain = g, activePreset = StemPreset.CUSTOM)
                2 -> m.copy(bassGain = g, activePreset = StemPreset.CUSTOM)
                else -> m.copy(drumsGain = g, activePreset = StemPreset.CUSTOM)
            }
        }
        syncEngineStates()
    }

    fun toggleStemMute(stemIndex: Int) {
        _mixerState.update { m ->
            when (stemIndex) {
                0 -> m.copy(vocalsMute = !m.vocalsMute, activePreset = StemPreset.CUSTOM)
                1 -> m.copy(accompMute = !m.accompMute, activePreset = StemPreset.CUSTOM)
                2 -> m.copy(bassMute = !m.bassMute, activePreset = StemPreset.CUSTOM)
                else -> m.copy(drumsMute = !m.drumsMute, activePreset = StemPreset.CUSTOM)
            }
        }
        syncEngineStates()
    }

    fun toggleStemSolo(stemIndex: Int) {
        _mixerState.update { m ->
            when (stemIndex) {
                0 -> m.copy(vocalsSolo = !m.vocalsSolo, activePreset = StemPreset.CUSTOM)
                1 -> m.copy(accompSolo = !m.accompSolo, activePreset = StemPreset.CUSTOM)
                2 -> m.copy(bassSolo = !m.bassSolo, activePreset = StemPreset.CUSTOM)
                else -> m.copy(drumsSolo = !m.drumsSolo, activePreset = StemPreset.CUSTOM)
            }
        }
        syncEngineStates()
    }

    fun toggleVocalTailCleanup(enabled: Boolean) {
        _mixerState.update { it.copy(vocalTailCleanup = enabled) }
        syncEngineStates()
    }

    fun setProcessingQuality(quality: ProcessingQuality) {
        _mixerState.update { it.copy(qualityMode = quality) }
    }

    // --- Real-time Pitch & Tempo ---

    fun updatePitchSemitones(semitones: Int) {
        _pitchTempoState.update { it.copy(semitones = semitones.coerceIn(-12, 12)) }
        syncEngineStates()
    }

    fun updatePitchFineCents(cents: Int) {
        _pitchTempoState.update { it.copy(fineCents = cents.coerceIn(-50, 50)) }
        syncEngineStates()
    }

    fun updateTempoRatio(ratio: Float) {
        _pitchTempoState.update { it.copy(tempoRatio = ratio.coerceIn(0.50f, 1.50f)) }
        syncEngineStates()
    }

    fun resetPitchTempo() {
        _pitchTempoState.value = PitchTempoState()
        syncEngineStates()
    }

    // --- EQ & Effects ---

    fun updateEqBand(bassDb: Float? = null, midDb: Float? = null, trebleDb: Float? = null) {
        _eqFxState.update { fx ->
            fx.copy(
                bassDb = (bassDb ?: fx.bassDb).coerceIn(-12f, 12f),
                midDb = (midDb ?: fx.midDb).coerceIn(-12f, 12f),
                trebleDb = (trebleDb ?: fx.trebleDb).coerceIn(-12f, 12f)
            )
        }
        syncEngineStates()
    }

    fun applyEqPreset(preset: EqPreset) {
        _eqFxState.update {
            it.copy(
                bassDb = preset.bassDb,
                midDb = preset.midDb,
                trebleDb = preset.trebleDb,
                activeEqPreset = preset
            )
        }
        syncEngineStates()
    }

    fun toggleAutoNormalize(enabled: Boolean) {
        _eqFxState.update { it.copy(autoNormalizeLimiter = enabled) }
        syncEngineStates()
    }

    fun updateReverbMix(mix: Float) {
        _eqFxState.update { it.copy(reverbMix = mix.coerceIn(0f, 0.6f)) }
        syncEngineStates()
    }

    fun updateDelayMix(mix: Float) {
        _eqFxState.update { it.copy(delayMix = mix.coerceIn(0f, 0.5f)) }
        syncEngineStates()
    }

    fun updateStereoWidth(width: Float) {
        _eqFxState.update { it.copy(stereoWidth = width.coerceIn(0f, 1.6f)) }
        syncEngineStates()
    }

    fun toggleHighPass80Hz(enabled: Boolean) {
        _eqFxState.update { it.copy(highPass80Hz = enabled) }
        syncEngineStates()
    }

    // --- Waveform Editor ---

    fun updateZoom(zoom: Float) {
        _waveformState.update {
            val z = zoom.coerceIn(1f, 12f)
            val maxScroll = (1f - 1f / z).coerceAtLeast(0f)
            it.copy(zoomLevel = z, scrollOffsetRatio = it.scrollOffsetRatio.coerceIn(0f, maxScroll))
        }
    }

    fun updateScrollOffset(offsetRatio: Float) {
        _waveformState.update {
            val maxScroll = (1f - 1f / it.zoomLevel).coerceAtLeast(0f)
            it.copy(scrollOffsetRatio = offsetRatio.coerceIn(0f, maxScroll))
        }
    }

    fun updateSelection(startRatio: Float, endRatio: Float) {
        val s = startRatio.coerceIn(0f, 0.98f)
        val e = endRatio.coerceIn(s + 0.02f, 1f)
        _waveformState.update { it.copy(selectionStartRatio = s, selectionEndRatio = e) }
    }

    fun trimToSelection() {
        val wf = _waveformState.value
        if (engine.trimSelection(wf.selectionStartRatio, wf.selectionEndRatio)) {
            _waveformState.update { it.copy(selectionStartRatio = 0.05f, selectionEndRatio = 0.95f) }
            refreshWaveformState()
            viewModelScope.launch { _snackbarMessages.emit("האודיו נחתך לטווח שנבחר") }
        } else {
            viewModelScope.launch { _snackbarMessages.emit("הטווח שנבחר קצר מדי לחיתוך") }
        }
    }

    fun cutOutSelection() {
        val wf = _waveformState.value
        if (engine.cutSelection(wf.selectionStartRatio, wf.selectionEndRatio)) {
            _waveformState.update { it.copy(selectionStartRatio = 0.1f, selectionEndRatio = 0.9f) }
            refreshWaveformState()
            viewModelScope.launch { _snackbarMessages.emit("הקטע הוסר והרצועה חוברה מחדש") }
        } else {
            viewModelScope.launch { _snackbarMessages.emit("יש לבחור קטע פנימי קצר מהרצועה כולה") }
        }
    }

    fun updateFadeDuration(seconds: Float) {
        _waveformState.update { it.copy(fadeDurationSec = seconds.coerceIn(0.5f, 8.0f)) }
    }

    fun applyFadeIn() {
        val dur = _waveformState.value.fadeDurationSec
        if (engine.applyFadeIn(dur)) {
            refreshWaveformState()
            viewModelScope.launch { _snackbarMessages.emit("נוספה כניסה הדרגתית (${dur}s Fade In)") }
        }
    }

    fun applyFadeOut() {
        val dur = _waveformState.value.fadeDurationSec
        if (engine.applyFadeOut(dur)) {
            refreshWaveformState()
            viewModelScope.launch { _snackbarMessages.emit("נוספה דעיכת סיום (${dur}s Fade Out)") }
        }
    }

    fun autoTrimSilence() {
        if (engine.autoTrimSilence()) {
            _waveformState.update { it.copy(selectionStartRatio = 0.05f, selectionEndRatio = 0.95f) }
            refreshWaveformState()
            viewModelScope.launch { _snackbarMessages.emit("השקט מהתחלה ומהסוף הוסר אוטומטית") }
        } else {
            viewModelScope.launch { _snackbarMessages.emit("לא נמצא שקט עודף לחיתוך") }
        }
    }

    fun mergeWithTrack(track: TrackEntity) {
        viewModelScope.launch {
            _processing.value = ProcessingProgress(true, "ממזג רצועות לרצף חלק...", 0.4f)
            try {
                val otherBuf = AudioDecoder.loadWavFile(File(track.filePath))
                if (engine.mergeAppendBuffer(otherBuf)) {
                    _mixerState.update { it.copy(stemsSeparated = false) }
                    syncEngineStates()
                    refreshWaveformState()
                    _snackbarMessages.emit("הרצועה \"${track.title}\" חוברה בהצלחה")
                }
            } catch (e: Exception) {
                _snackbarMessages.emit("שגיאה בחיבור הרצועות: ${e.message}")
            } finally {
                _processing.value = ProcessingProgress(false)
            }
        }
    }

    fun undoWaveformEdit() {
        if (engine.undo()) {
            _mixerState.update { it.copy(stemsSeparated = engine.currentStems != null) }
            syncEngineStates()
            refreshWaveformState()
            viewModelScope.launch { _snackbarMessages.emit("הפעולה בוטלה (Undo)") }
        }
    }

    fun redoWaveformEdit() {
        if (engine.redo()) {
            _mixerState.update { it.copy(stemsSeparated = engine.currentStems != null) }
            syncEngineStates()
            refreshWaveformState()
            viewModelScope.launch { _snackbarMessages.emit("הפעולה שוחזרה (Redo)") }
        }
    }

    private fun refreshWaveformState() {
        val peaks = engine.computeWaveformPeaks(180)
        _waveformState.update {
            it.copy(
                waveformPeaks = peaks,
                canUndo = engine.canUndo,
                canRedo = engine.canRedo
            )
        }
    }

    // --- Rehearsal: A-B Loop, Metronome, Overdub ---

    fun setLoopAFromCurrent() {
        val ratio = playbackState.value.progressRatio
        _rehearsalState.update { r ->
            val b = if (r.loopBRatio <= ratio + 0.02f) (ratio + 0.15f).coerceAtMost(1f) else r.loopBRatio
            r.copy(loopARatio = ratio, loopBRatio = b)
        }
        syncEngineStates()
    }

    fun setLoopBFromCurrent() {
        val ratio = playbackState.value.progressRatio
        _rehearsalState.update { r ->
            val a = if (r.loopARatio >= ratio - 0.02f) (ratio - 0.15f).coerceAtLeast(0f) else r.loopARatio
            r.copy(loopARatio = a, loopBRatio = ratio)
        }
        syncEngineStates()
    }

    fun toggleLoopEnabled() {
        _rehearsalState.update { it.copy(loopEnabled = !it.loopEnabled) }
        syncEngineStates()
    }

    fun clearLoop() {
        _rehearsalState.update { it.copy(loopEnabled = false, loopARatio = 0.2f, loopBRatio = 0.6f) }
        syncEngineStates()
    }

    fun toggleMetronome(enabled: Boolean) {
        _rehearsalState.update { it.copy(metronomeEnabled = enabled) }
        syncEngineStates()
    }

    fun updateBpm(bpm: Int) {
        _rehearsalState.update { it.copy(bpm = bpm.coerceIn(40, 240)) }
        syncEngineStates()
    }

    fun tapTempo() {
        val now = System.currentTimeMillis()
        if (tapTimestamps.isNotEmpty() && now - tapTimestamps.last() > 2500L) {
            tapTimestamps.clear()
        }
        tapTimestamps.addLast(now)
        if (tapTimestamps.size > 5) tapTimestamps.removeFirst()
        if (tapTimestamps.size >= 2) {
            var sum = 0L
            for (i in 1 until tapTimestamps.size) {
                sum += tapTimestamps[i] - tapTimestamps[i - 1]
            }
            val avgMs = sum / (tapTimestamps.size - 1)
            if (avgMs > 200) {
                updateBpm((60_000L / avgMs).toInt())
            }
        }
    }

    fun toggleCountIn(enabled: Boolean) {
        _rehearsalState.update { it.copy(countInEnabled = enabled) }
        syncEngineStates()
    }

    fun updateMetronomeVolume(volume: Float) {
        _rehearsalState.update { it.copy(metronomeVolume = volume.coerceIn(0f, 1f)) }
        syncEngineStates()
    }

    fun toggleOverdubRecording() {
        val currentlyRecording = _rehearsalState.value.isRecordingOverdub
        if (currentlyRecording) {
            engine.stopOverdubRecording()
            _rehearsalState.update { it.copy(isRecordingOverdub = false) }
        } else {
            _rehearsalState.update { it.copy(isRecordingOverdub = true) }
            engine.startOverdubRecording { recordedBuffer ->
                viewModelScope.launch {
                    _rehearsalState.update { it.copy(hasOverdubTrack = true, isRecordingOverdub = false) }
                    // Also save recording in library if standalone (no backing track)
                    if (engine.currentBuffer == null) {
                        val file = File(studioDir, "rec_${UUID.randomUUID()}.wav")
                        AudioDecoder.writeWavFile(recordedBuffer, file)
                        val entity = TrackEntity(
                            title = "הקלטת אולפן ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                            filePath = file.absolutePath,
                            format = "WAV",
                            durationMs = recordedBuffer.durationMs,
                            sampleRate = recordedBuffer.sampleRate,
                            category = "RECORDING"
                        )
                        val id = repository.insertTrack(entity)
                        activateTrack(entity.copy(id = id), recordedBuffer)
                    } else {
                        _snackbarMessages.emit("הקלטת ה-Overdub שולבה בהצלחה עם הפלייבק!")
                    }
                }
            }
        }
    }

    fun updateOverdubGain(gain: Float) {
        _rehearsalState.update { it.copy(overdubGain = gain.coerceIn(0f, 1.8f)) }
        syncEngineStates()
    }

    fun clearOverdubTrack() {
        engine.clearOverdub()
        _rehearsalState.update { it.copy(hasOverdubTrack = false) }
        viewModelScope.launch { _snackbarMessages.emit("ערוץ ההקלטה נוקה") }
    }

    // --- Export ---

    fun exportProject(
        fileName: String,
        format: ExportFormat,
        bitrate: ExportBitrate,
        externalUri: Uri?
    ) {
        val buf = engine.currentBuffer ?: run {
            viewModelScope.launch { _snackbarMessages.emit("אין רצועה טעונה לייצוא") }
            return
        }
        viewModelScope.launch {
            _showExportSheet.value = false
            _processing.value = ProcessingProgress(true, "מייצא ${format.extension.uppercase()} באופליין...", 0.05f)
            try {
                val safeName = fileName.trim().ifBlank { "playback_export" }
                val archiveFile = File(exportDir, "${safeName}_${System.currentTimeMillis()}.${format.extension}")
                val rendered = AudioExporter.renderAndExport(
                    context = getApplication<Application>(),
                    sourceBuffer = buf,
                    stems = engine.currentStems,
                    overdubBuffer = engine.overdubBuffer,
                    overdubGain = _rehearsalState.value.overdubGain,
                    mixer = _mixerState.value,
                    pitchTempo = _pitchTempoState.value,
                    fx = _eqFxState.value,
                    format = format,
                    bitrate = bitrate,
                    internalArchiveFile = archiveFile,
                    externalUri = externalUri,
                    limiterCeilingDb = _advancedSettings.value.limiterCeilingDb
                ) { p ->
                    _processing.value = ProcessingProgress(true, "מעבד ומקודד קובץ סופי...", p)
                }

                // Store archive entry in Room (as WAV mirror or direct file if WAV/M4A)
                val entity = TrackEntity(
                    title = "$safeName (${format.extension.uppercase()})",
                    filePath = archiveFile.absolutePath,
                    format = format.extension.uppercase(),
                    durationMs = rendered.durationMs,
                    sampleRate = rendered.sampleRate,
                    category = "PLAYBACK",
                    stemsSeparated = false
                )
                repository.insertTrack(entity)
                _snackbarMessages.emit(
                    if (externalUri != null) "הקובץ \"$safeName.${format.extension}\" נשמר בתיקייה שבחרת!"
                    else "הייצוא הושלם ונשמר בספריית הפלייבקים!"
                )
            } catch (e: Exception) {
                _snackbarMessages.emit("שגיאה בייצוא הקובץ: ${e.message}")
            } finally {
                _processing.value = ProcessingProgress(false)
            }
        }
    }

    private fun syncEngineStates() {
        engine.mixerState = _mixerState.value
        engine.pitchTempoState = _pitchTempoState.value
        engine.eqFxState = _eqFxState.value
        engine.rehearsalState = _rehearsalState.value
    }

    override fun onCleared() {
        super.onCleared()
        engine.stopOverdubRecording()
        engine.stopPlayback()
    }
}
