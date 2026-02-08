package com.echo.domain.repository

import kotlinx.coroutines.flow.Flow

interface RecordingRepository {
    val isListening: Flow<Boolean>
    suspend fun enableListening()
    suspend fun disableListening()

    suspend fun startRecording(prependedMemorySeconds: Float = 0f)
    suspend fun stopRecording()

    suspend fun dumpRecording(memorySeconds: Float, newFileName: String? = null): Result<Unit>
}