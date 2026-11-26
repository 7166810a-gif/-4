package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AudioAnalysisResult
import com.example.data.ExportBitrate
import com.example.data.ExportFormat
import com.example.data.StudioTab
import com.example.data.VisualizerStyle
import com.example.ui.StudioViewModel
import com.example.ui.components.AdvancedSettingsBottomSheet
import com.example.ui.components.ExportBottomSheet
import com.example.ui.components.ProcessingOverlayModal
import com.example.ui.components.TransportConsoleBar
import com.example.ui.components.WaveformCanvas
import com.example.ui.theme.AudioStudioTheme
import com.example.ui.theme.EmeraldDrum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioRootScreen(viewModel: StudioViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val advancedSettings by viewModel.advancedSettings.collectAsStateWithLifecycle()
    val tracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val libraryFilter by viewModel.libraryFilter.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val mixer by viewModel.mixerState.collectAsStateWithLifecycle()
    val pitchTempo by viewModel.pitchTempoState.collectAsStateWithLifecycle()
    val eqFx by viewModel.eqFxState.collectAsStateWithLifecycle()
    val rehearsal by viewModel.rehearsalState.collectAsStateWithLifecycle()
    val waveform by viewModel.waveformState.collectAsStateWithLifecycle()
    val liveWaveformState = viewModel.liveWaveform.collectAsStateWithLifecycle()
    val stereoPeaksState = viewModel.stereoPeaks.collectAsStateWithLifecycle()
    val analysisResult by viewModel.analysisResult.collectAsStateWithLifecycle()
    val processing by viewModel.processing.collectAsStateWithLifecycle()
    val showExportSheet by viewModel.showExportSheet.collectAsStateWithLifecycle()
    val showSettingsSheet by viewModel.showSettingsSheet.collectAsStateWithLifecycle()
    val cacheSizeMb by viewModel.cacheSizeMb.collectAsStateWithLifecycle()

    val view = LocalView.current
    DisposableEffect(view, advancedSettings.keepScreenOn) {
        view.keepScreenOn = advancedSettings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Back handler returns to Library tab from secondary studio tabs
    BackHandler(enabled = selectedTab != StudioTab.LIBRARY) {
        viewModel.selectTab(StudioTab.LIBRARY)
    }

    // SAF Audio Import Picker (zero storage permission, supports MP3/WAV/M4A/AAC/FLAC/OGG)
    val importAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importAudioUri(it) }
    }

    // Pending export metadata for SAF CreateDocument
    var pendingExportName by remember { mutableStateOf("playback") }
    var pendingExportFormat by remember { mutableStateOf(ExportFormat.WAV) }
    var pendingExportBitrate by remember { mutableStateOf(ExportBitrate.K320) }
    var pendingExportSelectionOnly by remember { mutableStateOf(false) }

    val createExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            viewModel.exportProject(
                fileName = pendingExportName,
                format = pendingExportFormat,
                bitrate = pendingExportBitrate,
                externalUri = uri,
                exportSelectionOnly = pendingExportSelectionOnly
            )
        }
    }

    val hasAudio = playback.durationMs > 0L

    AudioStudioTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(modifier = Modifier.fillMaxSize()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWideScreen = maxWidth >= 600.dp

                    Scaffold(
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "סטודיו אודיו אופליין",
                                            style = MaterialTheme.typography.titleLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Surface(
                                            color = EmeraldDrum.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                "OFFLINE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldDrum,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                navigationIcon = {
                                    IconButton(
                                        onClick = {
                                            importAudioLauncher.launch(
                                                arrayOf(
                                                    "audio/*",
                                                    "application/ogg"
                                                )
                                            )
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_import_button")
                                    ) {
                                        Icon(
                                            Icons.Rounded.FileOpen,
                                            contentDescription = stringResource(R.string.cd_import_audio)
                                        )
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = { viewModel.toggleDarkTheme() },
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("theme_toggle_button")
                                    ) {
                                        Icon(
                                            imageVector = if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                            contentDescription = "החלף מצב תצוגה"
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.setShowSettingsSheet(true) },
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_settings_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Settings,
                                            contentDescription = "הגדרות מתקדמות"
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        },
                        bottomBar = {
                            if (!isWideScreen) {
                                Column {
                                    if (hasAudio) {
                                        TransportConsoleBar(
                                            playback = playback,
                                            hasAudio = true,
                                            stereoPeaksProvider = { stereoPeaksState.value },
                                            onTogglePlay = { viewModel.togglePlayPause() },
                                            onStop = { viewModel.stopPlayback() },
                                            onSkipBack = { viewModel.skipByMs(-5000L) },
                                            onSkipForward = { viewModel.skipByMs(5000L) },
                                            onSeek = { viewModel.seekToRatio(it) },
                                            onOpenExport = { viewModel.setShowExportSheet(true) }
                                        )
                                    }
                                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                        StudioTab.entries.forEach { tab ->
                                            val selected = tab == selectedTab
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = { viewModel.selectTab(tab) },
                                                icon = {
                                                    Icon(
                                                        imageVector = tabIcon(tab, selected),
                                                        contentDescription = tab.titleHe
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = tab.titleHe,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            if (isWideScreen) {
                                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                                    Spacer(Modifier.height(8.dp))
                                    StudioTab.entries.forEach { tab ->
                                        val selected = tab == selectedTab
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = { viewModel.selectTab(tab) },
                                            icon = {
                                                Icon(
                                                    imageVector = tabIcon(tab, selected),
                                                    contentDescription = tab.titleHe
                                                )
                                            },
                                            label = { Text(tab.titleHe) },
                                            modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            // Adaptive Main Workspace
                            if (isWideScreen && selectedTab != StudioTab.LIBRARY && hasAudio) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Left/Main Active Tab Module
                                    Box(
                                        modifier = Modifier
                                            .weight(1.25f)
                                            .fillMaxHeight()
                                    ) {
                                        ActiveTabContent(
                                            selectedTab = selectedTab,
                                            viewModel = viewModel,
                                            hasAudio = hasAudio,
                                            tracks = tracks,
                                            allTracks = allTracks,
                                            searchQuery = searchQuery,
                                            libraryFilter = libraryFilter,
                                            playback = playback,
                                            mixer = mixer,
                                            pitchTempo = pitchTempo,
                                            eqFx = eqFx,
                                            rehearsal = rehearsal,
                                            waveform = waveform,
                                            visualizerStyle = advancedSettings.visualizerStyle,
                                            analysisResult = analysisResult,
                                            liveSamplesProvider = { liveWaveformState.value },
                                            onLaunchImport = {
                                                importAudioLauncher.launch(arrayOf("audio/*", "application/ogg"))
                                            }
                                        )
                                    }

                                    // Persistent Side Waveform + Transport Deck in Landscape/Tablet
                                    Column(
                                        modifier = Modifier
                                            .weight(0.95f)
                                            .fillMaxHeight()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            "תצוגת גל קול וקונסולה חיה",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        WaveformCanvas(
                                            peaks = waveform.waveformPeaks,
                                            progressRatio = playback.progressRatio,
                                            zoomLevel = waveform.zoomLevel,
                                            scrollOffsetRatio = waveform.scrollOffsetRatio,
                                            selectionStartRatio = null,
                                            selectionEndRatio = null,
                                            loopEnabled = rehearsal.loopEnabled,
                                            loopARatio = rehearsal.loopARatio,
                                            loopBRatio = rehearsal.loopBRatio,
                                            onSeekRatio = { viewModel.seekToRatio(it) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                        )
                                        TransportConsoleBar(
                                            playback = playback,
                                            hasAudio = true,
                                            stereoPeaksProvider = { stereoPeaksState.value },
                                            onTogglePlay = { viewModel.togglePlayPause() },
                                            onStop = { viewModel.stopPlayback() },
                                            onSkipBack = { viewModel.skipByMs(-5000L) },
                                            onSkipForward = { viewModel.skipByMs(5000L) },
                                            onSeek = { viewModel.seekToRatio(it) },
                                            onOpenExport = { viewModel.setShowExportSheet(true) },
                                            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .widthIn(max = 760.dp)
                                            .fillMaxWidth()
                                    ) {
                                        ActiveTabContent(
                                            selectedTab = selectedTab,
                                            viewModel = viewModel,
                                            hasAudio = hasAudio,
                                            tracks = tracks,
                                            allTracks = allTracks,
                                            searchQuery = searchQuery,
                                            libraryFilter = libraryFilter,
                                            playback = playback,
                                            mixer = mixer,
                                            pitchTempo = pitchTempo,
                                            eqFx = eqFx,
                                            rehearsal = rehearsal,
                                            waveform = waveform,
                                            visualizerStyle = advancedSettings.visualizerStyle,
                                            analysisResult = analysisResult,
                                            liveSamplesProvider = { liveWaveformState.value },
                                            onLaunchImport = {
                                                importAudioLauncher.launch(arrayOf("audio/*", "application/ogg"))
                                            }
                                        )
                                    }
                                    if (isWideScreen && hasAudio) {
                                        TransportConsoleBar(
                                            playback = playback,
                                            hasAudio = true,
                                            stereoPeaksProvider = { stereoPeaksState.value },
                                            onTogglePlay = { viewModel.togglePlayPause() },
                                            onStop = { viewModel.stopPlayback() },
                                            onSkipBack = { viewModel.skipByMs(-5000L) },
                                            onSkipForward = { viewModel.skipByMs(5000L) },
                                            onSeek = { viewModel.seekToRatio(it) },
                                            onOpenExport = { viewModel.setShowExportSheet(true) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showExportSheet) {
                    val selectionDurationMs = ((waveform.selectionEndRatio - waveform.selectionStartRatio) * playback.durationMs)
                        .toLong()
                        .coerceAtLeast(0L)
                    ExportBottomSheet(
                        defaultTitle = playback.activeTrackTitle,
                        selectionDurationMs = selectionDurationMs,
                        totalDurationMs = playback.durationMs,
                        onDismiss = { viewModel.setShowExportSheet(false) },
                        onSaveInternalOnly = { name, fmt, br, selOnly ->
                            viewModel.exportProject(
                                fileName = name,
                                format = fmt,
                                bitrate = br,
                                externalUri = null,
                                exportSelectionOnly = selOnly
                            )
                        },
                        onPickFolderAndSave = { name, fmt, br, selOnly ->
                            pendingExportName = name
                            pendingExportFormat = fmt
                            pendingExportBitrate = br
                            pendingExportSelectionOnly = selOnly
                            val safeFile = "${name.trim().ifBlank { "playback" }}.${fmt.extension}"
                            createExportLauncher.launch(safeFile)
                        }
                    )
                }

                if (showSettingsSheet) {
                    AdvancedSettingsBottomSheet(
                        settings = advancedSettings,
                        cacheSizeMb = cacheSizeMb,
                        onDismiss = { viewModel.setShowSettingsSheet(false) },
                        onToggleDarkTheme = viewModel::toggleDarkTheme,
                        onKeepScreenOnChange = viewModel::updateKeepScreenOn,
                        onLatencyModeChange = viewModel::updateLatencyMode,
                        onDefaultQualityChange = viewModel::updateDefaultQuality,
                        onVisualizerStyleChange = viewModel::updateVisualizerStyle,
                        onMetronomeToneChange = viewModel::updateMetronomeTone,
                        onLoudnessTargetChange = viewModel::updateLoudnessTarget,
                        onNoiseGateEnabledChange = viewModel::updateNoiseGateEnabled,
                        onNoiseGateThresholdChange = viewModel::updateNoiseGateThreshold,
                        onOverdubCompChange = viewModel::updateOverdubLatencyComp,
                        onAutoNormalizeRecChange = viewModel::updateAutoNormalizeRecording,
                        onLimiterCeilingChange = viewModel::updateLimiterCeiling,
                        onClearCache = viewModel::clearOrphanedCacheFiles,
                        onResetSettings = viewModel::resetAllAdvancedSettings
                    )
                }

                ProcessingOverlayModal(progress = processing)
            }
        }
    }
}

@Composable
private fun ActiveTabContent(
    selectedTab: StudioTab,
    viewModel: StudioViewModel,
    hasAudio: Boolean,
    tracks: List<com.example.data.TrackEntity>,
    allTracks: List<com.example.data.TrackEntity>,
    searchQuery: String,
    libraryFilter: com.example.data.LibraryFilter,
    playback: com.example.data.PlaybackUiState,
    mixer: com.example.data.MixerState,
    pitchTempo: com.example.data.PitchTempoState,
    eqFx: com.example.data.EqAndFxState,
    rehearsal: com.example.data.RehearsalState,
    waveform: com.example.data.WaveformEditState,
    visualizerStyle: VisualizerStyle,
    analysisResult: AudioAnalysisResult,
    liveSamplesProvider: () -> FloatArray,
    onLaunchImport: () -> Unit
) {
    val context = LocalContext.current
    when (selectedTab) {
        StudioTab.LIBRARY -> LibraryScreen(
            tracks = tracks,
            activeTrackId = playback.activeTrackId,
            searchQuery = searchQuery,
            activeFilter = libraryFilter,
            onSearchChange = viewModel::updateSearchQuery,
            onFilterChange = viewModel::updateLibraryFilter,
            onImportAudioClick = onLaunchImport,
            onCreatePracticeLoopClick = viewModel::createBuiltInPracticeLoop,
            onGoToRecordClick = { viewModel.selectTab(StudioTab.REHEARSAL) },
            onSelectTrack = viewModel::loadTrackFromLibrary,
            onRenameTrack = viewModel::renameTrack,
            onDuplicateTrack = viewModel::duplicateTrack,
            onShareTrack = { track ->
                viewModel.getShareUriForTrack(track)?.let { uri ->
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "שתף קובץ שמע"))
                }
            },
            onDeleteTrack = viewModel::deleteTrack
        )
        StudioTab.MIXER -> MixerScreen(
            hasAudio = hasAudio,
            isPlaying = playback.isPlaying,
            visualizerStyle = visualizerStyle,
            analysisResult = analysisResult,
            liveSamplesProvider = liveSamplesProvider,
            mixer = mixer,
            pitchTempo = pitchTempo,
            onSeparateClick = { viewModel.separateStemsAndCreatePlayback() },
            onQualityChange = viewModel::setProcessingQuality,
            onPresetClick = viewModel::applyStemPreset,
            onVocalTailToggle = viewModel::toggleVocalTailCleanup,
            onGainChange = viewModel::updateStemGain,
            onToggleMute = viewModel::toggleStemMute,
            onToggleSolo = viewModel::toggleStemSolo,
            onPitchSemitonesChange = viewModel::updatePitchSemitones,
            onPitchFineCentsChange = viewModel::updatePitchFineCents,
            onTempoChange = viewModel::updateTempoRatio,
            onResetPitchTempo = viewModel::resetPitchTempo,
            onSyncMetronomeToBpm = viewModel::syncMetronomeToDetectedBpm,
            onReanalyzeTrack = viewModel::analyzeCurrentTrack,
            onGoToLibrary = { viewModel.selectTab(StudioTab.LIBRARY) }
        )
        StudioTab.WAVEFORM -> WaveformScreen(
            hasAudio = hasAudio,
            playback = playback,
            waveform = waveform,
            rehearsal = rehearsal,
            availableTracks = allTracks,
            onSeekRatio = viewModel::seekToRatio,
            onZoomChange = viewModel::updateZoom,
            onScrollChange = viewModel::updateScrollOffset,
            onSelectionChange = viewModel::updateSelection,
            onTrim = viewModel::trimToSelection,
            onCut = viewModel::cutOutSelection,
            onFadeDurationChange = viewModel::updateFadeDuration,
            onFadeIn = viewModel::applyFadeIn,
            onFadeOut = viewModel::applyFadeOut,
            onAutoTrimSilence = viewModel::autoTrimSilence,
            onMergeTrack = viewModel::mergeWithTrack,
            onUndo = viewModel::undoWaveformEdit,
            onRedo = viewModel::redoWaveformEdit,
            onGoToLibrary = { viewModel.selectTab(StudioTab.LIBRARY) }
        )
        StudioTab.EFFECTS -> EffectsScreen(
            fx = eqFx,
            onBassChange = { viewModel.updateEqBand(bassDb = it) },
            onMidChange = { viewModel.updateEqBand(midDb = it) },
            onTrebleChange = { viewModel.updateEqBand(trebleDb = it) },
            onSelectEqPreset = viewModel::applyEqPreset,
            onToggleAutoNormalize = viewModel::toggleAutoNormalize,
            onReverbChange = viewModel::updateReverbMix,
            onDelayChange = viewModel::updateDelayMix,
            onStereoWidthChange = viewModel::updateStereoWidth,
            onToggleHighPass80Hz = viewModel::toggleHighPass80Hz,
            onMasterGainChange = viewModel::updateMasterGain
        )
        StudioTab.REHEARSAL -> RehearsalScreen(
            hasAudio = hasAudio,
            playback = playback,
            rehearsal = rehearsal,
            visualizerStyle = visualizerStyle,
            liveSamplesProvider = liveSamplesProvider,
            onSetLoopA = viewModel::setLoopAFromCurrent,
            onSetLoopB = viewModel::setLoopBFromCurrent,
            onToggleLoop = viewModel::toggleLoopEnabled,
            onClearLoop = viewModel::clearLoop,
            onToggleMetronome = viewModel::toggleMetronome,
            onBpmChange = viewModel::updateBpm,
            onTapTempo = viewModel::tapTempo,
            onToggleCountIn = viewModel::toggleCountIn,
            onMetronomeVolumeChange = viewModel::updateMetronomeVolume,
            onMetronomeSignatureChange = viewModel::updateMetronomeSignature,
            onPlayWithCountIn = viewModel::playWithCountInIfEnabled,
            onToggleOverdubRecord = viewModel::toggleOverdubRecording,
            onOverdubGainChange = viewModel::updateOverdubGain,
            onClearOverdub = viewModel::clearOverdubTrack
        )
    }
}

private fun tabIcon(tab: StudioTab, selected: Boolean): ImageVector = when (tab) {
    StudioTab.LIBRARY -> if (selected) Icons.Rounded.LibraryMusic else Icons.Outlined.LibraryMusic
    StudioTab.MIXER -> if (selected) Icons.Rounded.Tune else Icons.Outlined.Tune
    StudioTab.WAVEFORM -> if (selected) Icons.Rounded.GraphicEq else Icons.Outlined.GraphicEq
    StudioTab.EFFECTS -> if (selected) Icons.Rounded.Equalizer else Icons.Outlined.Equalizer
    StudioTab.REHEARSAL -> if (selected) Icons.Rounded.Mic else Icons.Outlined.Mic
}
