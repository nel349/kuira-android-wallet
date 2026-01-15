package com.midnight.kuira.core.indexer.api

import com.midnight.kuira.core.indexer.model.BlockInfo
import com.midnight.kuira.core.indexer.model.NetworkState
import com.midnight.kuira.core.indexer.model.RawLedgerEvent
import kotlinx.coroutines.flow.Flow

/**
 * Client for Midnight indexer GraphQL API (v3).
 *
 * Provides access to ledger events, blocks, and network state.
 *
 * **Phase 4A Implementation:**
 * - Fetches raw hex events from `zswapLedgerEvents` subscription
 * - Caches events locally
 * - Cannot deserialize yet (blocked by ledger 7.0.0)
 *
 * **GraphQL Endpoint:**
 * - HTTP: `http://localhost:8088/api/v3/graphql`
 * - WebSocket: `ws://localhost:8088/api/v3/graphql/ws`
 */
interface IndexerClient {

    /**
     * Subscribe to zswap ledger events.
     *
     * **GraphQL Subscription:**
     * ```graphql
     * subscription {
     *   zswapLedgerEvents {
     *     id
     *     raw
     *     maxId
     *   }
     * }
     * ```
     *
     * @param fromId Start from this event ID (inclusive). Use null to start from latest.
     * @return Flow of raw ledger events
     */
    fun subscribeToZswapEvents(fromId: Long? = null): Flow<RawLedgerEvent>

    /**
     * Subscribe to new blocks.
     *
     * **GraphQL Subscription:**
     * ```graphql
     * subscription {
     *   blocks {
     *     height
     *     hash
     *     timestamp
     *   }
     * }
     * ```
     *
     * @return Flow of block metadata
     */
    fun subscribeToBlocks(): Flow<BlockInfo>

    /**
     * Get current network synchronization state.
     *
     * **GraphQL Query:**
     * ```graphql
     * query {
     *   networkState {
     *     currentBlock
     *     maxBlock
     *   }
     * }
     * ```
     *
     * @return Current network state
     */
    suspend fun getNetworkState(): NetworkState

    /**
     * Get historical events in range.
     *
     * **GraphQL Query:**
     * ```graphql
     * query {
     *   zswapLedgerEvents(fromId: $from, toId: $to) {
     *     id
     *     raw
     *     maxId
     *   }
     * }
     * ```
     *
     * @param fromId Start event ID (inclusive)
     * @param toId End event ID (inclusive)
     * @return List of events in range
     */
    suspend fun getEventsInRange(fromId: Long, toId: Long): List<RawLedgerEvent>

    /**
     * Check if indexer is healthy and reachable.
     *
     * @return true if indexer is responding, false otherwise
     */
    suspend fun isHealthy(): Boolean

    /**
     * Close the client and release resources.
     */
    fun close()
}
