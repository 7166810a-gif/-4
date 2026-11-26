package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "studio_tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val filePath: String,
    val format: String,
    val durationMs: Long,
    val sampleRate: Int,
    val category: String, // "IMPORTED", "PLAYBACK", "RECORDING"
    val stemsSeparated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
