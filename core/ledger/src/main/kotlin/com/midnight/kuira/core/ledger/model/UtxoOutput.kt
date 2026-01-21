// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.ledger.model

import java.math.BigInteger

/**
 * Represents a UTXO being created (output) in an unshielded transaction.
 *
 * **Source:** Based on midnight-ledger `UtxoOutput` structure
 * **File:** `midnight-ledger/ledger/src/structure.rs`
 *
 * **Purpose:**
 * - Specifies a new UTXO to be created
 * - Defines recipient address, amount, and token type
 * - Used for both recipient outputs and change outputs
 *
 * **Midnight SDK Equivalent:**
 * ```typescript
 * interface UtxoOutput {
 *   value: bigint;           // Amount to create
 *   owner: UnshieldedAddress; // Recipient address
 *   tokenType: TokenType;    // Token being created
 * }
 * ```
 *
 * **Usage in Transaction:**
 * ```kotlin
 * // Recipient output
 * val recipientOutput = UtxoOutput(
 *     value = BigInteger("100000000"),  // 100.0 NIGHT
 *     owner = "mn_addr_undeployed1...",  // Recipient address
 *     tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
 * )
 *
 * // Change output
 * val changeOutput = UtxoOutput(
 *     value = BigInteger("50000000"),   // 50.0 NIGHT (change)
 *     owner = "mn_addr_undeployed1...", // Sender's address
 *     tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
 * )
 * ```
 *
 * @property value Amount to create (in smallest units)
 * @property owner Recipient's unshielded address (Bech32m format)
 * @property tokenType Token type identifier (hex string, 64 chars)
 */
data class UtxoOutput(
    val value: BigInteger,
    val owner: String,
    val tokenType: String
) {
    init {
        require(value > BigInteger.ZERO) { "Value must be positive, got: $value" }
        require(owner.isNotBlank()) { "Owner address cannot be blank" }
        require(tokenType.isNotBlank()) { "Token type cannot be blank" }
        require(tokenType.length == 64) { "Token type must be 64 hex characters, got: ${tokenType.length}" }
    }

    companion object {
        /**
         * Native NIGHT token type (all zeros).
         *
         * Matches `UtxoSpend.NATIVE_TOKEN_TYPE`.
         */
        const val NATIVE_TOKEN_TYPE = "0000000000000000000000000000000000000000000000000000000000000000"
    }
}
