package com.midnight.kuira.core.indexer.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.midnight.kuira.core.indexer.model.Utxo

/**
 * Room entity for unshielded UTXO storage.
 *
 * Stores UTXOs (Unspent Transaction Outputs) in local database for balance calculation.
 *
 * Primary key is composite of intentHash + outputIndex (unique identifier).
 * Indexed on owner (address) for fast balance queries.
 */
@Entity(
    tableName = "unshielded_utxos",
    indices = [
        Index(value = ["owner"]),
        Index(value = ["token_type"]),
        Index(value = ["state"])
    ]
)
data class UnshieldedUtxoEntity(
    /**
     * Unique identifier: "intentHash:outputIndex"
     */
    @PrimaryKey
    val id: String,

    /**
     * Transaction hash that created this UTXO.
     */
    @ColumnInfo(name = "intent_hash")
    val intentHash: String,

    /**
     * Output index in the transaction.
     */
    @ColumnInfo(name = "output_index")
    val outputIndex: Int,

    /**
     * Address that owns this UTXO.
     */
    @ColumnInfo(name = "owner")
    val owner: String,

    /**
     * Token type (e.g., "DUST").
     */
    @ColumnInfo(name = "token_type")
    val tokenType: String,

    /**
     * Value in smallest unit (stored as string BigInteger).
     */
    @ColumnInfo(name = "value")
    val value: String,

    /**
     * Creation time (Unix timestamp in seconds).
     */
    @ColumnInfo(name = "ctime")
    val ctime: Long,

    /**
     * Whether this UTXO is registered for dust generation.
     */
    @ColumnInfo(name = "registered_for_dust_generation")
    val registeredForDustGeneration: Boolean,

    /**
     * UTXO state (AVAILABLE, PENDING, or SPENT).
     *
     * - AVAILABLE: Can be used in new transactions, included in balance
     * - PENDING: Locked for pending transaction, excluded from balance
     * - SPENT: Confirmed spent, excluded from balance
     *
     * State transitions:
     * - New UTXO → AVAILABLE
     * - Transaction created → PENDING
     * - Transaction SUCCESS → SPENT
     * - Transaction FAILURE → AVAILABLE (unlocked)
     */
    @ColumnInfo(name = "state")
    val state: UtxoState = UtxoState.AVAILABLE
) {
    companion object {
        /**
         * Convert domain model to entity.
         */
        fun fromUtxo(utxo: Utxo, state: UtxoState = UtxoState.AVAILABLE): UnshieldedUtxoEntity {
            return UnshieldedUtxoEntity(
                id = utxo.identifier(),
                intentHash = utxo.intentHash,
                outputIndex = utxo.outputIndex,
                owner = utxo.owner,
                tokenType = utxo.tokenType,
                value = utxo.value,
                ctime = utxo.ctime,
                registeredForDustGeneration = utxo.registeredForDustGeneration,
                state = state
            )
        }
    }

    /**
     * Convert entity to domain model.
     */
    fun toUtxo(): Utxo {
        return Utxo(
            value = value,
            owner = owner,
            tokenType = tokenType,
            intentHash = intentHash,
            outputIndex = outputIndex,
            ctime = ctime,
            registeredForDustGeneration = registeredForDustGeneration
        )
    }
}
