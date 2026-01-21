// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.ledger.model

import java.math.BigInteger

/**
 * Represents a UTXO being spent (input) in an unshielded transaction.
 *
 * **Source:** Based on midnight-ledger `UtxoSpend` structure
 * **File:** `midnight-ledger/ledger/src/structure.rs`
 *
 * **Purpose:**
 * - References an existing UTXO by intentHash and outputNo
 * - Specifies the owner address (for signature verification)
 * - Includes value and token type for balancing
 *
 * **Midnight SDK Equivalent:**
 * ```typescript
 * interface UtxoSpend {
 *   intentHash: string;      // Hash of the intent that created this UTXO
 *   outputNo: number;        // Output index in that intent
 *   value: bigint;           // Amount being spent
 *   owner: UnshieldedAddress; // Owner's address (for signing)
 *   tokenType: TokenType;    // Token being spent
 * }
 * ```
 *
 * **Usage in Transaction:**
 * ```kotlin
 * val input = UtxoSpend(
 *     intentHash = "0x123...",
 *     outputNo = 0,
 *     value = BigInteger("1000000"),  // 1.0 NIGHT
 *     owner = "mn_addr_undeployed1...",
 *     tokenType = "0000...000"  // NIGHT token
 * )
 * ```
 *
 * @property intentHash Hash of the intent that created this UTXO (hex string)
 * @property outputNo Output index in the creating intent (0-based)
 * @property value Amount being spent (in smallest units)
 * @property owner Owner's unshielded address (Bech32m format)
 * @property tokenType Token type identifier (hex string, 64 chars)
 */
data class UtxoSpend(
    val intentHash: String,
    val outputNo: Int,
    val value: BigInteger,
    val owner: String,
    val tokenType: String
) {
    init {
        require(intentHash.isNotBlank()) { "Intent hash cannot be blank" }
        require(outputNo >= 0) { "Output number must be non-negative, got: $outputNo" }
        require(value >= BigInteger.ZERO) { "Value must be non-negative, got: $value" }
        require(owner.isNotBlank()) { "Owner address cannot be blank" }
        require(tokenType.isNotBlank()) { "Token type cannot be blank" }
        require(tokenType.length == 64) { "Token type must be 64 hex characters, got: ${tokenType.length}" }
    }

    /**
     * Unique identifier for this UTXO.
     *
     * **Format:** `intentHash:outputNo`
     *
     * Used for database lookups and UTXO tracking.
     */
    fun identifier(): String = "$intentHash:$outputNo"

    companion object {
        /**
         * Native NIGHT token type (all zeros).
         */
        const val NATIVE_TOKEN_TYPE = "0000000000000000000000000000000000000000000000000000000000000000"
    }
}
