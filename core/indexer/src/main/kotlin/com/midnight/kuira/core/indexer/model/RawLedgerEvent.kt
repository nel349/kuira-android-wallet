package com.midnight.kuira.core.indexer.model

import kotlinx.serialization.Serializable

/**
 * Raw ledger event from Midnight indexer GraphQL API.
 *
 * Contains hex-encoded event data that needs to be deserialized
 * with ledger 7.0.0 WASM (Phase 4B).
 *
 * **Input Validation:**
 * - `id` must be non-negative
 * - `rawHex` must be non-empty and valid hex string
 * - `maxId` must be non-negative and >= id
 * - `blockHeight` must be non-negative if provided
 * - `timestamp` must be positive if provided
 *
 * @property id Event ID (sequential)
 * @property rawHex Raw event data as hex string
 * @property maxId Maximum event ID currently available
 * @property blockHeight Block height where this event occurred
 * @property timestamp Block timestamp (Unix epoch milliseconds)
 * @throws IllegalArgumentException if validation fails
 */
@Serializable
data class RawLedgerEvent(
    val id: Long,
    val rawHex: String,
    val maxId: Long,
    val blockHeight: Long? = null,
    val timestamp: Long? = null
) {
    init {
        // Validate ID
        require(id >= 0) { "Event ID must be non-negative, got: $id" }

        // Validate rawHex
        require(rawHex.isNotBlank()) { "Event raw hex cannot be blank" }
        require(rawHex.matches(Regex("^[0-9a-fA-F]+$"))) {
            "Event raw hex must be valid hex string, got: ${rawHex.take(20)}..."
        }

        // Validate maxId
        require(maxId >= 0) { "MaxId must be non-negative, got: $maxId" }
        require(maxId >= id) { "MaxId ($maxId) must be >= event ID ($id)" }

        // Validate optional fields
        blockHeight?.let {
            require(it >= 0) { "Block height must be non-negative, got: $it" }
        }
        timestamp?.let {
            require(it > 0) { "Timestamp must be positive, got: $it" }
        }
    }
}
