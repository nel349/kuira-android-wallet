// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.ledger.fee

import org.json.JSONObject
import java.math.BigInteger

/**
 * Creates DustSpend actions for transaction fee payment.
 *
 * **DustSpend:**
 * A DustSpend is a cryptographic proof that allows spending a dust UTXO to pay transaction fees.
 * It contains:
 * - `v_fee`: Fee amount in Specks
 * - `old_nullifier`: Nullifier of the UTXO being spent
 * - `new_commitment`: Commitment for the change output
 * - `proof`: Proof of validity (proof-preimage for unproven transactions)
 *
 * **Workflow:**
 * ```
 * DustLocalState → Select UTXO → Create DustSpend → Add to Intent → Submit Transaction
 * ```
 *
 * **Security:**
 * - Requires 32-byte seed to derive DustSecretKey
 * - Seed is zeroized after use in JNI layer
 * - DustSpend is serialized to JSON for transport
 *
 * @see `/midnight-ledger/ledger/src/dust.rs` (Rust implementation reference)
 */
object DustSpendCreator {

    init {
        System.loadLibrary("kuira_crypto_ffi")
    }

    /**
     * Result of DustSpend creation.
     *
     * @property vFee Fee amount in Specks
     * @property oldNullifier Hex-encoded nullifier of spent UTXO
     * @property newCommitment Hex-encoded commitment for change output
     * @property proof Proof type ("proof-preimage" for unproven transactions)
     */
    data class DustSpend(
        val vFee: BigInteger,
        val oldNullifier: String,
        val newCommitment: String,
        val proof: String
    ) {
        companion object {
            /**
             * Parse DustSpend from JSON string.
             *
             * @param json JSON string from Rust FFI
             * @return DustSpend object
             * @throws IllegalArgumentException if JSON is malformed
             */
            fun fromJson(json: String): DustSpend {
                val obj = JSONObject(json)
                return DustSpend(
                    vFee = BigInteger(obj.getString("v_fee")),
                    oldNullifier = obj.getString("old_nullifier"),
                    newCommitment = obj.getString("new_commitment"),
                    proof = obj.getString("proof")
                )
            }
        }

        /**
         * Serialize DustSpend to JSON string.
         *
         * @return JSON string
         */
        fun toJson(): String {
            return JSONObject().apply {
                put("v_fee", vFee.toString())
                put("old_nullifier", oldNullifier)
                put("new_commitment", newCommitment)
                put("proof", proof)
            }.toString()
        }
    }

    /**
     * Creates a DustSpend action for fee payment.
     *
     * **Parameters:**
     * - `statePtr`: Pointer to DustLocalState (from DustRepository.deserializeState())
     * - `seed`: 32-byte seed for deriving DustSecretKey
     * - `utxoIndex`: Index of UTXO to spend (0-based)
     * - `vFee`: Fee amount in Specks
     * - `currentTimeMs`: Current time for timestamp (System.currentTimeMillis())
     *
     * **Important:**
     * - UTXO must exist at the given index
     * - UTXO must have sufficient value to cover fee
     * - Change will be calculated as: utxo.value - vFee
     *
     * @param statePtr Pointer to deserialized DustLocalState
     * @param seed 32-byte seed array
     * @param utxoIndex Index of UTXO to spend
     * @param vFee Fee amount in Specks
     * @param currentTimeMs Current time in milliseconds
     * @return DustSpend object, or null on error
     *
     * @throws IllegalArgumentException if seed is not 32 bytes
     */
    fun createDustSpend(
        statePtr: Long,
        seed: ByteArray,
        utxoIndex: Int,
        vFee: BigInteger,
        currentTimeMs: Long = System.currentTimeMillis()
    ): DustSpend? {
        require(seed.size == 32) { "Seed must be exactly 32 bytes" }
        require(statePtr != 0L) { "State pointer must not be null" }
        require(utxoIndex >= 0) { "UTXO index must be non-negative" }
        require(vFee > BigInteger.ZERO) { "Fee must be positive" }

        val json = nativeCreateDustSpend(
            statePtr = statePtr,
            seed = seed,
            utxoIndex = utxoIndex,
            vFee = vFee.toString(),
            currentTimeMs = currentTimeMs
        ) ?: return null

        return try {
            DustSpend.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * JNI bridge to Rust FFI `create_dust_spend()`.
     *
     * @param statePtr DustLocalState pointer
     * @param seed 32-byte seed
     * @param utxoIndex UTXO index
     * @param vFee Fee as decimal string
     * @param currentTimeMs Current time in milliseconds
     * @return JSON string, or null on error
     */
    private external fun nativeCreateDustSpend(
        statePtr: Long,
        seed: ByteArray,
        utxoIndex: Int,
        vFee: String,
        currentTimeMs: Long
    ): String?
}
