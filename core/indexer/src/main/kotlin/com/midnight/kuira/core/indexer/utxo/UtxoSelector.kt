// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.indexer.utxo

import com.midnight.kuira.core.indexer.database.UnshieldedUtxoEntity
import java.math.BigInteger

/**
 * UTXO coin selection algorithm.
 *
 * **Strategy: Smallest-First (Privacy Optimization)**
 *
 * **Source:** Based on midnight-wallet coin selection
 * **File:** `midnight-wallet/packages/capabilities/src/balancer/Balancer.ts:143`
 *
 * **Why Smallest-First (Not Largest-First)?**
 * Privacy optimization - Using smallest coins first:
 * - Reduces UTXO fragmentation over time
 * - Makes transaction amounts less predictable
 * - Better long-term privacy vs largest-first
 *
 * **Algorithm:**
 * 1. Sort available UTXOs by value (ascending - smallest first)
 * 2. Accumulate UTXOs until sum >= required amount
 * 3. Return selected UTXOs
 *
 * **Multi-Token Support:**
 * Selection is performed per token type (each token selected independently).
 *
 * **Usage in Transaction Building:**
 * ```kotlin
 * val selector = UtxoSelector()
 *
 * // Select UTXOs for 100 NIGHT
 * val result = selector.selectUtxos(
 *     availableUtxos = availableNightUtxos,
 *     requiredAmount = BigInteger("100000000")  // 100.0 NIGHT
 * )
 *
 * when (result) {
 *     is SelectionResult.Success -> {
 *         // Use result.selectedUtxos for transaction inputs
 *         // Use result.totalSelected - requiredAmount for change output
 *     }
 *     is SelectionResult.InsufficientFunds -> {
 *         // Show error: "Need ${result.required} but only have ${result.available}"
 *     }
 * }
 * ```
 *
 * **Important:**
 * - UTXOs must be AVAILABLE (not PENDING or SPENT)
 * - UTXOs must be sorted by value before passing to selectUtxos()
 * - Selected UTXOs must be marked PENDING immediately after selection
 */
class UtxoSelector {

    /**
     * Select UTXOs using smallest-first algorithm.
     *
     * **Algorithm:**
     * 1. Start with empty selection and zero sum
     * 2. For each UTXO (in ascending value order):
     *    - Add UTXO to selection
     *    - Add UTXO value to sum
     *    - If sum >= required amount, stop and return success
     * 3. If reached end without sufficient funds, return failure
     *
     * **Example:**
     * ```
     * Required: 100
     * Available UTXOs: [10, 25, 50, 75, 100]
     *
     * Selection process:
     * - Add 10 → sum = 10 (< 100, continue)
     * - Add 25 → sum = 35 (< 100, continue)
     * - Add 50 → sum = 85 (< 100, continue)
     * - Add 75 → sum = 160 (>= 100, STOP!)
     *
     * Result: [10, 25, 50, 75], change = 60
     * ```
     *
     * @param availableUtxos Available UTXOs (must be sorted by value ASC)
     * @param requiredAmount Amount needed (in smallest units)
     * @return SelectionResult.Success with selected UTXOs, or InsufficientFunds
     */
    fun selectUtxos(
        availableUtxos: List<UnshieldedUtxoEntity>,
        requiredAmount: BigInteger
    ): SelectionResult {
        require(requiredAmount > BigInteger.ZERO) {
            "Required amount must be positive, got: $requiredAmount"
        }

        val selected = mutableListOf<UnshieldedUtxoEntity>()
        var totalSelected = BigInteger.ZERO

        // Smallest-first: Accumulate UTXOs until we have enough
        for (utxo in availableUtxos) {
            val utxoValue = BigInteger(utxo.value)

            selected.add(utxo)
            totalSelected += utxoValue

            // Check if we have enough
            if (totalSelected >= requiredAmount) {
                return SelectionResult.Success(
                    selectedUtxos = selected,
                    totalSelected = totalSelected,
                    change = totalSelected - requiredAmount
                )
            }
        }

        // Insufficient funds: totalSelected IS the total available (we accumulated all)
        return SelectionResult.InsufficientFunds(
            required = requiredAmount,
            available = totalSelected,
            shortfall = requiredAmount - totalSelected
        )
    }

    /**
     * Select UTXOs for multiple token types.
     *
     * Performs coin selection independently for each token type.
     * Useful for transactions that send multiple tokens.
     *
     * **Example:**
     * ```kotlin
     * val requirements = mapOf(
     *     "NIGHT_TOKEN" to BigInteger("100000000"),  // 100 NIGHT
     *     "DUST_TOKEN" to BigInteger("50000000")     // 50 DUST
     * )
     *
     * val allUtxos = utxoDao.getUnspentUtxos(address)
     * val result = selector.selectUtxosMultiToken(allUtxos, requirements)
     * ```
     *
     * @param availableUtxos All available UTXOs (will be filtered by token type)
     * @param requiredAmounts Map of tokenType → required amount
     * @return MultiTokenResult with selection per token type
     */
    fun selectUtxosMultiToken(
        availableUtxos: List<UnshieldedUtxoEntity>,
        requiredAmounts: Map<String, BigInteger>
    ): MultiTokenResult {
        val selections = mutableMapOf<String, SelectionResult>()

        for ((tokenType, requiredAmount) in requiredAmounts) {
            // Filter and sort UTXOs for this token type
            val tokenUtxos = availableUtxos
                .filter { it.tokenType == tokenType }
                .sortedBy { BigInteger(it.value) }  // Sort by value (smallest first)

            // Perform selection for this token
            val result = selectUtxos(tokenUtxos, requiredAmount)
            selections[tokenType] = result

            // If any token has insufficient funds, return early
            if (result is SelectionResult.InsufficientFunds) {
                return MultiTokenResult.PartialFailure(
                    selections = selections,
                    failedToken = tokenType,
                    required = requiredAmount,
                    available = result.available
                )
            }
        }

        // All tokens selected successfully
        return MultiTokenResult.Success(selections)
    }

    /**
     * Result of UTXO selection.
     */
    sealed class SelectionResult {
        /**
         * Selection successful - found enough UTXOs.
         *
         * @property selectedUtxos UTXOs selected for transaction inputs
         * @property totalSelected Total value of selected UTXOs
         * @property change Amount to send back as change (totalSelected - required)
         */
        data class Success(
            val selectedUtxos: List<UnshieldedUtxoEntity>,
            val totalSelected: BigInteger,
            val change: BigInteger
        ) : SelectionResult() {
            init {
                // Only validate what callers can break, not mathematical invariants
                require(selectedUtxos.isNotEmpty()) {
                    "Selected UTXOs cannot be empty for successful selection"
                }
                // totalSelected > 0 and change >= 0 are guaranteed by construction
            }
        }

        /**
         * Selection failed - insufficient funds.
         *
         * @property required Amount required
         * @property available Amount available
         * @property shortfall How much more is needed (required - available)
         */
        data class InsufficientFunds(
            val required: BigInteger,
            val available: BigInteger,
            val shortfall: BigInteger
        ) : SelectionResult()
        // No validation needed - mathematical invariants guaranteed by construction
    }

    /**
     * Result of multi-token UTXO selection.
     */
    sealed class MultiTokenResult {
        /**
         * All tokens selected successfully.
         *
         * @property selections Map of tokenType → SelectionResult.Success
         */
        data class Success(
            val selections: Map<String, SelectionResult>
        ) : MultiTokenResult() {
            init {
                require(selections.isNotEmpty()) {
                    "Selections cannot be empty"
                }
                // All selections are Success - guaranteed by construction
            }

            /**
             * Get all selected UTXOs across all token types.
             */
            fun allSelectedUtxos(): List<UnshieldedUtxoEntity> {
                return selections.values
                    .map { it as SelectionResult.Success }
                    .flatMap { it.selectedUtxos }
            }
        }

        /**
         * At least one token has insufficient funds.
         *
         * @property selections Map of tokenType → SelectionResult (partial results)
         * @property failedToken Token type that failed
         * @property required Required amount for failed token
         * @property available Available amount for failed token
         */
        data class PartialFailure(
            val selections: Map<String, SelectionResult>,
            val failedToken: String,
            val required: BigInteger,
            val available: BigInteger
        ) : MultiTokenResult()
    }
}
