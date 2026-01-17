package com.midnight.kuira.core.indexer.model

import kotlinx.serialization.Serializable
import java.math.BigInteger

/**
 * Unshielded UTXO (Unspent Transaction Output).
 *
 * Represents an output from a transaction that can be spent.
 * Matches Midnight SDK's `WireUtxoSchema`.
 *
 * @property value Amount in smallest unit (like satoshis)
 * @property owner Address that owns this UTXO
 * @property tokenType Token type identifier (e.g., "DUST")
 * @property intentHash Transaction hash that created this UTXO
 * @property outputIndex Index of this output in the transaction
 * @property ctime Creation time (Unix timestamp in seconds)
 * @property registeredForDustGeneration Whether this UTXO is registered for dust generation
 */
@Serializable
data class Utxo(
    val value: String, // Serialized as string, convert to BigInteger
    val owner: String,
    val tokenType: String,
    val intentHash: String,
    val outputIndex: Int,
    val ctime: Long, // Unix timestamp in seconds
    val registeredForDustGeneration: Boolean
) {
    init {
        require(value.isNotBlank()) { "Value cannot be blank" }
        require(owner.isNotBlank()) { "Owner cannot be blank" }
        require(tokenType.isNotBlank()) { "TokenType cannot be blank" }
        require(intentHash.isNotBlank()) { "IntentHash cannot be blank" }
        require(outputIndex >= 0) { "OutputIndex must be non-negative" }
        require(ctime >= 0) { "Ctime must be non-negative" }

        // Validate value can be parsed as BigInteger
        try {
            BigInteger(value)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Value must be a valid BigInteger string: $value", e)
        }
    }

    /**
     * Convert value string to BigInteger.
     */
    fun valueBigInt(): BigInteger = BigInteger(value)

    /**
     * Unique identifier for this UTXO.
     * Format: "intentHash:outputIndex"
     */
    fun identifier(): String = "$intentHash:$outputIndex"
}
