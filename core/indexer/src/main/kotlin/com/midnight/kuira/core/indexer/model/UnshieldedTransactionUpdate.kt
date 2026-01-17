package com.midnight.kuira.core.indexer.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Update from unshielded transactions subscription.
 *
 * Union type representing either:
 * - An actual transaction with UTXO changes
 * - A progress update indicating sync status
 *
 * Matches Midnight SDK's `WalletSyncUpdateSchema`.
 */
@Serializable
sealed class UnshieldedTransactionUpdate {
    /**
     * Actual transaction update with UTXO changes.
     *
     * Contains transaction details plus lists of created and spent UTXOs.
     */
    @Serializable
    @SerialName("UnshieldedTransaction")
    data class Transaction(
        val type: String = "UnshieldedTransaction",
        val transaction: UnshieldedTransaction,
        val createdUtxos: List<Utxo>,
        val spentUtxos: List<Utxo>
    ) : UnshieldedTransactionUpdate() {
        /**
         * Derive transaction status from transaction type and result.
         *
         * System transactions are always SUCCESS.
         * Regular transactions use the transactionResult status.
         */
        fun status(): TransactionStatus {
            return when (transaction.type) {
                TransactionType.SystemTransaction -> TransactionStatus.SUCCESS
                TransactionType.RegularTransaction -> transaction.transactionResult?.status
                    ?: TransactionStatus.SUCCESS
            }
        }
    }

    /**
     * Progress update indicating sync status.
     *
     * Tells us the highest transaction ID available, so we can track
     * how far behind we are in syncing.
     */
    @Serializable
    @SerialName("UnshieldedTransactionsProgress")
    data class Progress(
        val type: String = "UnshieldedTransactionsProgress",
        val highestTransactionId: Int
    ) : UnshieldedTransactionUpdate() {
        init {
            require(highestTransactionId >= 0) { "Highest transaction ID must be non-negative" }
        }
    }
}
