package com.midnight.kuira.core.indexer.model

import kotlinx.serialization.Serializable

/**
 * Network synchronization state.
 *
 * **Input Validation:**
 * - `currentBlock` must be non-negative
 * - `maxBlock` must be non-negative
 * - `currentBlock` must be <= `maxBlock`
 * - `syncProgress` must be in range [0.0, 1.0]
 *
 * @property currentBlock Current local block height
 * @property maxBlock Maximum known block height from network
 * @property syncProgress Sync progress (0.0 to 1.0)
 * @property isFullySynced Whether wallet is fully synced
 * @throws IllegalArgumentException if validation fails
 */
@Serializable
data class NetworkState(
    val currentBlock: Long,
    val maxBlock: Long,
    val syncProgress: Float,
    val isFullySynced: Boolean
) {
    init {
        // Validate block heights
        require(currentBlock >= 0) { "Current block must be non-negative, got: $currentBlock" }
        require(maxBlock >= 0) { "Max block must be non-negative, got: $maxBlock" }
        require(currentBlock <= maxBlock) {
            "Current block ($currentBlock) cannot exceed max block ($maxBlock)"
        }

        // Validate sync progress
        require(syncProgress in 0f..1f) {
            "Sync progress must be in [0.0, 1.0], got: $syncProgress"
        }
    }
    companion object {
        /**
         * Create network state from current and max block heights.
         */
        fun fromBlockHeights(current: Long, max: Long): NetworkState {
            val progress = if (max > 0) (current.toFloat() / max.toFloat()) else 0f
            return NetworkState(
                currentBlock = current,
                maxBlock = max,
                syncProgress = progress.coerceIn(0f, 1f),
                isFullySynced = current >= max
            )
        }
    }
}
