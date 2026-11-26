package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM studio_tracks ORDER BY createdAt DESC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Query("UPDATE studio_tracks SET stemsSeparated = :separated, durationMs = :durationMs WHERE id = :id")
    suspend fun updateTrackMetadata(id: Long, separated: Boolean, durationMs: Long)

    @Query("DELETE FROM studio_tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)
}
