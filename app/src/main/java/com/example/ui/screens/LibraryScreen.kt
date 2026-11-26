package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LibraryFilter
import com.example.data.TrackEntity
import com.example.ui.components.formatTimeMs
import com.example.ui.theme.EmeraldDrum
import com.example.ui.theme.StudioMonoStyle

@Composable
fun LibraryScreen(
    tracks: List<TrackEntity>,
    activeTrackId: Long?,
    searchQuery: String,
    activeFilter: LibraryFilter,
    onSearchChange: (String) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onImportAudioClick: () -> Unit,
    onCreatePracticeLoopClick: () -> Unit,
    onGoToRecordClick: () -> Unit,
    onSelectTrack: (TrackEntity) -> Unit,
    onRenameTrack: (TrackEntity, String) -> Unit,
    onDuplicateTrack: (TrackEntity) -> Unit,
    onShareTrack: (TrackEntity) -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var trackToRename by remember { mutableStateOf<TrackEntity?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var trackForDetails by remember { mutableStateOf<TrackEntity?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Studio Hero Banner Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_hero),
                    contentDescription = stringResource(R.string.cd_hero_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xEE090C12),
                                    Color(0xBB090C12),
                                    Color(0x55090C12)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = EmeraldDrum.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.OfflineBolt,
                                    contentDescription = null,
                                    tint = EmeraldDrum,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "100% אופליין • פרטיות מלאה",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldDrum
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "סטודיו אודיו מקצועי",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Text(
                            text = "הפרדת ערוצים, שינוי סולם ומהירות, עורך גל קול וייצוא פלייבקים ישירות על המכשיר.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD0DCF0),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onImportAudioClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("import_audio_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ייבוא קובץ שמע (MP3, WAV, M4A, AAC, FLAC, OGG)",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCreatePracticeLoopClick,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("create_practice_loop_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("מחולל לופ אימון", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = onGoToRecordClick,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("quick_record_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("הקלטה חדשה", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Search & Filter Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("חיפוש מהיר לפי שם שיר או פורמט...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LibraryFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = filter == activeFilter,
                            onClick = { onFilterChange(filter) },
                            label = { Text(filter.titleHe) },
                            modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Empty State or Tracks List
        if (tracks.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "הספרייה המקומית ריקה",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "ייבאו שיר מתיקיות המכשיר או צרו לופ אימון מובנה כדי להתחיל בהפרדת ערוצים ויצירת פלייבק.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onCreatePracticeLoopClick) {
                            Icon(Icons.Rounded.AddCircleOutline, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("נסה לופ אימון עכשיו")
                        }
                    }
                }
            }
        } else {
            items(tracks, key = { it.id }) { track ->
                val isActive = track.id == activeTrackId
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelectTrack(track) }
                        .testTag("library_track_${track.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AudioFile,
                                    contentDescription = null,
                                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            text = "${track.format} • ${formatTimeMs(track.durationMs)}",
                                            style = StudioMonoStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (track.stemsSeparated) {
                                        Surface(
                                            color = EmeraldDrum.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                "4 ערוצים",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldDrum,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { trackForDetails = track },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("info_track_${track.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = "פרטי רצועה",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    renameDraft = track.title
                                    trackToRename = track
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("rename_track_${track.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "שנה שם רצועה",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onDeleteTrack(track) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("delete_track_${track.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "מחק רצועה",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    trackToRename?.let { target ->
        AlertDialog(
            onDismissRequest = { trackToRename = null },
            title = { Text("שינוי שם רצועה") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("שם הרצועה") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_track_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameTrack(target, renameDraft)
                        trackToRename = null
                    },
                    enabled = renameDraft.isNotBlank()
                ) {
                    Text("שמור")
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToRename = null }) { Text("ביטול") }
            }
        )
    }

    trackForDetails?.let { info ->
        val categoryLabel = when (info.category) {
            "PLAYBACK" -> "פלייבק שיוצא"
            "RECORDING" -> "הקלטה מקומית"
            else -> "שיר שיובא"
        }
        AlertDialog(
            onDismissRequest = { trackForDetails = null },
            title = { Text(info.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("קטגוריה: $categoryLabel", style = MaterialTheme.typography.bodyMedium)
                    Text("פורמט מקור: ${info.format}", style = MaterialTheme.typography.bodyMedium)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            "אורך: ${formatTimeMs(info.durationMs)} • ${info.sampleRate} Hz",
                            style = StudioMonoStyle,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        if (info.stemsSeparated) "ערוצים: מופרד ל-4 ערוצים (Stems)" else "ערוצים: רצועת סטריאו מאוחדת",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDuplicateTrack(info)
                                trackForDetails = null
                            },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("duplicate_track_button")
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("שכפל")
                        }
                        Button(
                            onClick = {
                                onShareTrack(info)
                                trackForDetails = null
                            },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("share_track_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("שתף קובץ")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { trackForDetails = null }) { Text("סגור") }
            }
        )
    }
}
