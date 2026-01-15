package com.midnight.kuira.core.indexer.storage

/**
 * Room entity for caching raw ledger events.
 *
 * **Purpose:** Cache raw hex events from indexer locally so we can:
 * 1. Replay events without re-syncing when ledger 7.0.0 deserializer is ready (Phase 4B)
 * 2. Support offline mode
 * 3. Reduce network traffic
 *
 * **Note:** We store raw hex because we can't deserialize yet (ledger 7.0.0 not available).
 * When Phase 4B is complete, we'll deserialize cached events to calculate balances.
 *
 * @property id Event ID from indexer (primary key, sequential)
 * @property rawHex Raw event data as hex string (cannot deserialize yet)
 * @property maxId Maximum event ID at time of fetch
 * @property blockHeight Block height where event occurred (nullable)
 * @property timestamp Event timestamp in milliseconds (nullable)
 * @property cachedAt When this event was cached locally (milliseconds)
 */
// @Entity(tableName = "ledger_events")  // Uncomment when Room is configured
data class EventEntity(
    // @PrimaryKey
    val id: Long,
    // @ColumnInfo(name = "raw_hex")
    val rawHex: String,
    // @ColumnInfo(name = "max_id")
    val maxId: Long,
    // @ColumnInfo(name = "block_height")
    val blockHeight: Long? = null,
    // @ColumnInfo(name = "timestamp")
    val timestamp: Long? = null,
    // @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis()
)
