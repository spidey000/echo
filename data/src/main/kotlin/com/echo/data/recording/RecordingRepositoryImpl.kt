package com.echo.data.recording

import com.echo.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Minimal stub implementation that will later delegate to the audio pipeline / Android service.
 */
import javax.inject.Inject

class RecordingRepositoryImpl @Inject constructor() : RecordingRepository {
    private val _isListening = MutableStateFlow(false)
    override val isListening: Flow<Boolean> = _isListening.asStateFlow()

    override suspend fun enableListening() { _isListening.value = true }
    override suspend fun disableListening() { _isListening.value = false }

    override suspend fun startRecording(prependedMemorySeconds: Float) { /* no-op stub */ }
    override suspend fun stopRecording() { /* no-op stub */ }

    override suspend fun dumpRecording(memorySeconds: Float, newFileName: String?): Result<Unit> = Result.success(Unit)
}