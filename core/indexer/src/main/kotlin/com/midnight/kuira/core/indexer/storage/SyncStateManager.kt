package com.midnight.kuira.core.indexer.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Manages wallet synchronization state.
 *
 * **Purpose:**
 * - Track current sync progress (which event we're at)
 * - Provide reactive sync progress updates
 * - Determine when wallet is fully synced
 *
 * **Usage:**
 * ```kotlin
 * val syncManager = SyncStateManager()
 *
 * // Update progress as events are processed
 * syncManager.updateProgress(currentEventId = 100, maxEventId = 1000)
 *
 * // Observe sync progress in UI
 * syncManager.getSyncProgress().collect { progress ->
 *     println("Sync: ${(progress * 100).toInt()}%")
 * }
 * ```
 */
class SyncStateManager {

    private data class SyncState(
        val currentEventId: Long = 0,
        val maxEventId: Long = 0
    )

    private val _syncState = MutableStateFlow(SyncState())

    /**
     * Update sync progress.
     *
     * @param currentEventId Current processed event ID
     * @param maxEventId Maximum known event ID from network
     */
    fun updateProgress(currentEventId: Long, maxEventId: Long) {
        _syncState.value = SyncState(
            currentEventId = currentEventId,
            maxEventId = maxEventId
        )
    }

    /**
     * Get current sync progress as percentage (0.0 to 1.0).
     *
     * @return Flow of sync progress
     */
    fun getSyncProgress(): Flow<Float> = _syncState.asStateFlow().map { state ->
        if (state.maxEventId > 0) {
            (state.currentEventId.toFloat() / state.maxEventId.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    /**
     * Check if wallet is fully synced.
     *
     * @return Flow of sync status
     */
    fun isFullySynced(): Flow<Boolean> = _syncState.asStateFlow().map { state ->
        state.currentEventId >= state.maxEventId && state.maxEventId > 0
    }

    /**
     * Get current event ID.
     */
    fun getCurrentEventId(): Flow<Long> = _syncState.asStateFlow().map { it.currentEventId }

    /**
     * Get max event ID.
     */
    fun getMaxEventId(): Flow<Long> = _syncState.asStateFlow().map { it.maxEventId }

    /**
     * Reset sync state.
     */
    fun reset() {
        _syncState.value = SyncState()
    }
}
