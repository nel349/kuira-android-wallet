package com.midnight.kuira.core.indexer.database

/**
 * UTXO state lifecycle matching Midnight SDK behavior.
 *
 * **Three-State Model:**
 * - AVAILABLE: UTXO is unspent and can be used in new transactions
 * - PENDING: UTXO is locked for a pending transaction (not yet confirmed)
 * - SPENT: UTXO has been spent in a confirmed transaction
 *
 * **State Transitions:**
 * - New UTXO: → AVAILABLE
 * - Create transaction: AVAILABLE → PENDING
 * - Transaction SUCCESS: PENDING → SPENT
 * - Transaction FAILURE: PENDING → AVAILABLE (unlock)
 * - Transaction PARTIAL_SUCCESS: PENDING → SPENT (if segment succeeded)
 *
 * **Balance Calculation:**
 * Only AVAILABLE UTXOs are included in balance calculations.
 * PENDING and SPENT UTXOs are excluded.
 *
 * **Security:**
 * The PENDING state prevents double-spending by locking UTXOs during transaction creation.
 */
enum class UtxoState {
    /**
     * UTXO is unspent and available for use in new transactions.
     * Included in balance calculations.
     */
    AVAILABLE,

    /**
     * UTXO is locked for a pending transaction (not yet confirmed).
     * Excluded from balance calculations to prevent double-spending.
     */
    PENDING,

    /**
     * UTXO has been spent in a confirmed transaction.
     * Excluded from balance calculations.
     */
    SPENT
}
