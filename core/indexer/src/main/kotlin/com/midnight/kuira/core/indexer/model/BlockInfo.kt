package com.midnight.kuira.core.indexer.model

import kotlinx.serialization.Serializable

/**
 * Block metadata from Midnight blockchain.
 *
 * **Input Validation:**
 * - `height` must be non-negative
 * - `hash` must be non-empty hex string (64 characters for SHA-256)
 * - `timestamp` must be positive
 * - `eventCount` must be non-negative
 *
 * @property height Block height (sequential number)
 * @property hash Block hash (hex string)
 * @property timestamp Block timestamp (Unix epoch milliseconds)
 * @property eventCount Number of ledger events in this block
 * @throws IllegalArgumentException if validation fails
 */
@Serializable
data class BlockInfo(
    val height: Long,
    val hash: String,
    val timestamp: Long,
    val eventCount: Int = 0
) {
    init {
        // Validate height
        require(height >= 0) { "Block height must be non-negative, got: $height" }

        // Validate hash
        require(hash.isNotBlank()) { "Block hash cannot be blank" }
        require(hash.matches(Regex("^[0-9a-fA-F]+$"))) {
            "Block hash must be valid hex string, got: $hash"
        }
        // Typical block hash is 64 characters (SHA-256), but allow flexibility
        require(hash.length >= 32) {
            "Block hash too short (expected >= 32 chars), got: ${hash.length}"
        }

        // Validate timestamp
        require(timestamp > 0) { "Block timestamp must be positive, got: $timestamp" }

        // Validate event count
        require(eventCount >= 0) { "Event count must be non-negative, got: $eventCount" }
    }
}
