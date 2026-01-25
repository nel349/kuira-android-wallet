// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.crypto.dust

import java.math.BigInteger

/**
 * Represents a Dust UTXO (Unspent Transaction Output) in the wallet.
 *
 * **What is a Dust Token?**
 * Dust is Midnight's fee payment mechanism. When you register a Night UTXO for
 * dust generation, it creates a Dust token that accumulates value over time.
 * This accumulated dust is used to pay transaction fees.
 *
 * **Dust Generation:**
 * ```
 * Time →
 * │
 * ├─ Creation (ctime): initialValue Specks
 * │
 * ├─ Generation Phase: Value increases linearly
 * │  Rate: backing Night value × generation_decay_rate
 * │  Cap: backing Night value × night_dust_ratio
 * │
 * ├─ Full Phase: Value stays at cap
 * │
 * ├─ Decay Phase: Value decreases linearly (if backing Night is destroyed)
 * │
 * └─ Empty: Value reaches zero
 * ```
 *
 * **Midnight SDK Mapping:**
 * This corresponds to `DustWalletUtxoState` which contains:
 * - `utxo: QualifiedDustOutput` - The actual dust output
 * - `pending_until: Option<Timestamp>` - If pending, when it becomes available
 *
 * **Usage:**
 * ```kotlin
 * val token = DustToken(
 *     initialValue = BigInteger("1000000"), // 1 Dust = 1,000,000 Specks
 *     owner = dustPublicKey,
 *     nonce = randomNonce,
 *     sequenceNumber = 0,
 *     creationTime = System.currentTimeMillis(),
 *     backingNight = nightNonce,
 *     merkleTreeIndex = 0,
 *     pendingUntil = null // Available immediately
 * )
 *
 * // Calculate current value
 * val currentValue = token.calculateValue(
 *     now = System.currentTimeMillis(),
 *     params = dustParams
 * )
 * ```
 *
 * @property initialValue Initial dust value in Specks when created
 * @property owner Dust public key that owns this token
 * @property nonce Unique nonce for this dust output
 * @property sequenceNumber Sequence number for ordering
 * @property creationTime Unix timestamp in milliseconds when created
 * @property backingNight The Night UTXO that backs this dust generation
 * @property merkleTreeIndex Index in the Merkle tree
 * @property pendingUntil If set, this token is pending until this time (milliseconds)
 */
data class DustToken(
    val initialValue: BigInteger,
    val owner: String, // Hex-encoded dust public key
    val nonce: String, // Hex-encoded field element
    val sequenceNumber: Int,
    val creationTime: Long, // Unix milliseconds
    val backingNight: String, // Hex-encoded initial nonce of backing Night
    val merkleTreeIndex: Long,
    val pendingUntil: Long? = null // Unix milliseconds, null if not pending
) {
    /**
     * Calculates the current value of this dust token at a specific time.
     *
     * **Algorithm:**
     * 1. **Generation Phase** (creation → full capacity):
     *    - Value increases linearly: `rate × time_elapsed + initial_value`
     *    - Rate: `backing_night_value × generation_decay_rate`
     *    - Cap: `backing_night_value × night_dust_ratio`
     *
     * 2. **Full Phase** (full capacity → backing night destroyed):
     *    - Value stays constant at cap
     *
     * 3. **Decay Phase** (backing night destroyed → empty):
     *    - Value decreases linearly: `cap - (rate × time_since_destroyed)`
     *    - Falls to zero
     *
     * 4. **Empty Phase**: Value stays at zero
     *
     * **Note:** This is a placeholder. Full implementation requires DustGenerationInfo
     * and will be completed in Phase 2D-3.
     *
     * @param now Current time in milliseconds
     * @param params Dust generation parameters
     * @return Current dust value in Specks
     */
    fun calculateValue(now: Long, params: DustParameters): BigInteger {
        // TODO: Implement full dust generation algorithm in Phase 2D-3
        // This requires:
        // - DustGenerationInfo from Merkle tree
        // - Backing Night value and destruction time
        // - Proper time phase calculations

        // For now, return initial value as placeholder
        return initialValue
    }

    /**
     * Checks if this dust token is currently pending (not yet spendable).
     *
     * @param now Current time in milliseconds
     * @return true if pending, false if available
     */
    fun isPending(now: Long): Boolean {
        return pendingUntil?.let { it > now } ?: false
    }

    /**
     * Checks if this dust token is available for spending.
     *
     * @param now Current time in milliseconds
     * @return true if available, false if pending
     */
    fun isAvailable(now: Long): Boolean = !isPending(now)

    /**
     * Calculates the nullifier for this dust token.
     *
     * **Nullifier:**
     * Used to prevent double-spending. When a dust token is spent, its nullifier
     * is published on-chain to mark it as spent.
     *
     * **Formula:**
     * ```
     * nullifier = Hash(owner_secret_key, nonce, ...)
     * ```
     *
     * **Note:** This requires the secret key and will be implemented in Phase 2D-4.
     *
     * @return Nullifier as hex string (placeholder)
     */
    fun calculateNullifier(): String {
        // TODO: Implement in Phase 2D-4
        // Requires:
        // - Dust secret key (NOT stored in this class for security)
        // - Proper nullifier derivation algorithm

        throw NotImplementedError("Nullifier calculation requires secret key (Phase 2D-4)")
    }

    /**
     * Calculates the commitment for this dust token.
     *
     * **Commitment:**
     * Public commitment to the dust output that hides the owner but allows
     * validation on-chain.
     *
     * **Formula:**
     * ```
     * commitment = Hash(initial_value, owner, nonce, ctime)
     * ```
     *
     * **Note:** This will be implemented in Phase 2D-4.
     *
     * @return Commitment as hex string (placeholder)
     */
    fun calculateCommitment(): String {
        // TODO: Implement in Phase 2D-4
        // Uses transient hash over DustPreProjection

        throw NotImplementedError("Commitment calculation (Phase 2D-4)")
    }

    companion object {
        /**
         * Conversion: 1 Dust = 1 quadrillion Specks (10^15)
         *
         * **From midnight-ledger:**
         * ```rust
         * pub const SPECKS_PER_DUST: u128 = 1_000_000_000_000_000;
         * ```
         */
        const val SPECKS_PER_DUST = 1_000_000_000_000_000L

        /**
         * Converts Dust to Specks.
         *
         * @param dust Amount in Dust
         * @return Amount in Specks
         */
        fun dustToSpecks(dust: Long): BigInteger {
            return BigInteger.valueOf(dust).multiply(BigInteger.valueOf(SPECKS_PER_DUST))
        }

        /**
         * Converts Specks to Dust (rounded down).
         *
         * @param specks Amount in Specks
         * @return Amount in Dust (rounded down)
         */
        fun specksToDust(specks: BigInteger): Long {
            return specks.divide(BigInteger.valueOf(SPECKS_PER_DUST)).toLong()
        }
    }
}
