package com.midnight.kuira.core.indexer.model

import java.math.BigInteger

/**
 * Represents the balance for a single token type.
 *
 * Aggregated from multiple UTXOs of the same token type.
 *
 * **Example:**
 * ```kotlin
 * TokenBalance(
 *     tokenType = "DUST",
 *     balance = BigInteger("5000000"),
 *     utxoCount = 3
 * )
 * ```
 */
data class TokenBalance(
    /**
     * Token type (e.g., "DUST", "TNIGHT").
     */
    val tokenType: String,

    /**
     * Total balance in smallest unit (sum of all AVAILABLE UTXOs).
     */
    val balance: BigInteger,

    /**
     * Number of UTXOs contributing to this balance.
     */
    val utxoCount: Int
)
