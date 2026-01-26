// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.indexer.model

/**
 * Dust-related blockchain events from Midnight indexer.
 *
 * **Event Types:**
 * - `DustInitialUtxo`: New dust UTXO registered (Night → Dust registration)
 * - `DustSpendProcessed`: Dust spent for transaction fee
 * - `DustGenerationDtimeUpdate`: Dust generation time updated
 *
 * **Processing:**
 * Events are SCALE-encoded in `RawLedgerEvent.rawHex` and need to be:
 * 1. Filtered to dust events only
 * 2. Decoded from hex
 * 3. Replayed into DustLocalState
 *
 * **Reference:**
 * `/midnight-ledger/ledger/src/events.rs` - Event enum definition
 */
sealed class DustEvent {
    /**
     * Event ID from indexer (for tracking sync progress).
     */
    abstract val eventId: Long

    /**
     * Raw hex-encoded event data (SCALE codec).
     * This is the actual data that gets replayed into DustLocalState.
     */
    abstract val rawHex: String

    /**
     * Block height where event occurred.
     */
    abstract val blockHeight: Long?

    /**
     * Block timestamp (milliseconds).
     */
    abstract val timestamp: Long?

    /**
     * New dust UTXO registered (Night → Dust registration).
     *
     * **When this occurs:**
     * - User creates DustRegistration transaction
     * - Night UTXO is locked for dust generation
     * - Dust starts accumulating over time
     *
     * **State Update:**
     * - Adds new dust UTXO to DustLocalState
     * - Sets initial value (usually 0)
     * - Records Night backing info
     */
    data class DustInitialUtxo(
        override val eventId: Long,
        override val rawHex: String,
        override val blockHeight: Long?,
        override val timestamp: Long?
    ) : DustEvent()

    /**
     * Dust spent for transaction fee.
     *
     * **When this occurs:**
     * - User submits transaction with dust fee payment
     * - Dust UTXO nullifier published on chain
     * - Change output created (if any)
     *
     * **State Update:**
     * - Marks spent UTXO as consumed
     * - Adds change output (if v_fee < utxo.value)
     * - Updates local balance
     */
    data class DustSpendProcessed(
        override val eventId: Long,
        override val rawHex: String,
        override val blockHeight: Long?,
        override val timestamp: Long?
    ) : DustEvent()

    /**
     * Dust generation time updated.
     *
     * **When this occurs:**
     * - Blockchain updates dust generation parameters
     * - Affects future dust accumulation rate
     *
     * **State Update:**
     * - Updates generation dtime in DustLocalState
     * - Affects balance calculations going forward
     */
    data class DustGenerationDtimeUpdate(
        override val eventId: Long,
        override val rawHex: String,
        override val blockHeight: Long?,
        override val timestamp: Long?
    ) : DustEvent()

    companion object {
        /**
         * Checks if a raw ledger event is a dust event.
         *
         * **Detection Strategy:**
         * Currently uses simple heuristics. In production, should:
         * 1. Deserialize SCALE-encoded event
         * 2. Check event discriminant/tag
         * 3. Match against dust event types
         *
         * **Placeholder Implementation:**
         * Returns true for all events (filters in DustLocalState.replayEvents).
         * DustLocalState will ignore non-dust events during replay.
         *
         * @param rawEvent Raw ledger event from indexer
         * @return true if this is a dust event
         */
        fun isDustEvent(rawEvent: RawLedgerEvent): Boolean {
            // TODO: Implement proper SCALE decoding to check event type
            // For now, pass all events to DustLocalState which filters internally
            return true
        }

        /**
         * Converts RawLedgerEvent to DustEvent.
         *
         * **Note:**
         * Since we can't easily discriminate event types without full SCALE decoding,
         * we create a generic DustSpendProcessed event and let DustLocalState.replayEvents()
         * handle the actual type checking.
         *
         * In production, this should:
         * 1. Decode SCALE event discriminant
         * 2. Match to specific DustEvent subtype
         * 3. Return appropriate variant
         *
         * @param rawEvent Raw ledger event
         * @return DustEvent (currently always DustSpendProcessed as placeholder)
         */
        fun fromRawEvent(rawEvent: RawLedgerEvent): DustEvent {
            // TODO: Implement proper event type discrimination
            // For now, create a generic event that passes raw data through
            return DustSpendProcessed(
                eventId = rawEvent.id,
                rawHex = rawEvent.rawHex,
                blockHeight = rawEvent.blockHeight,
                timestamp = rawEvent.timestamp
            )
        }
    }
}
