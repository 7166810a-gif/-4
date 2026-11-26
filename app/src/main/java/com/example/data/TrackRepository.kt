package com.example.data

import kotlinx.coroutines.flow.Flow

class TrackRepository(private val trackDao: TrackDao) {
    val allTracks: Flow<List<TrackEntity>> = trackDao.getAllTracks()

    suspend fun insertTrack(track: TrackEntity): Long = trackDao.insertTrack(track)

    suspend fun updateTrackMetadata(id: Long, separated: Boolean, durationMs: Long) {
        trackDao.updateTrackMetadata(id, separated, durationMs)
    }

    suspend fun deleteTrack(id: Long) {
        trackDao.deleteTrackById(id)
    }
}
