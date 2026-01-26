// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.ledger.fee

import com.midnight.kuira.core.indexer.database.DustTokenEntity
import java.math.BigInteger
import javax.inject.Inject

/**
 * Selects dust coins for fee payment using smallest-first strategy.
 *
 * **Strategy: Smallest-First**
 * - Sorts coins by value (ascending)
 * - Selects smallest coins until sum >= required fee
 * - Minimizes UTXO fragmentation
 * - Matches midnight-ledger's coin selection strategy
 *
 * **Benefits:**
 * - Keeps large UTXOs intact for future large transactions
 * - Reduces dust fragmentation over time
 * - Better privacy (smaller outputs less traceable)
 *
 * **Example:**
 * ```
 * Available: [1 Dust, 3 Dust, 10 Dust, 50 Dust]
 * Required: 12 Dust
 * Selected: [1 Dust, 3 Dust, 10 Dust] = 14 Dust
 * Change: 2 Dust (returned as new UTXO)
 * ```
 *
 * @see `/midnight-wallet/packages/dust-wallet/src/CoinSelection.ts` (TypeScript SDK reference)
 */
class DustCoinSelector @Inject constructor() {

    /**
     * Result of coin selection.
     *
     * @property selectedCoins Coins selected to cover fee
     * @property totalValue Total value of selected coins (in Specks)
     * @property change Change amount to be returned (totalValue - requiredFee)
     */
    data class CoinSelectionResult(
        val selectedCoins: List<DustTokenEntity>,
        val totalValue: BigInteger,
        val change: BigInteger
    ) {
        /** Returns true if selection was successful (has coins). */
        fun isSuccess(): Boolean = selectedCoins.isNotEmpty()

        /** Returns true if there will be change. */
        fun hasChange(): Boolean = change > BigInteger.ZERO
    }

    /**
     * Selects coins to cover required fee.
     *
     * **Algorithm:**
     * 1. Sort available coins by value (ascending)
     * 2. Select coins until sum >= requiredFee
     * 3. Calculate change = sum - requiredFee
     *
     * **Edge Cases:**
     * - If insufficient dust: returns empty selection
     * - If exact match: no change
     * - If requiredFee == 0: returns empty selection
     *
     * @param availableCoins List of available dust tokens (must be sorted smallest-first)
     * @param requiredFee Fee amount needed (in Specks)
     * @param currentTimeMillis Current time for calculating actual coin values
     * @return CoinSelectionResult with selected coins and change
     */
    fun selectCoins(
        availableCoins: List<DustTokenEntity>,
        requiredFee: BigInteger,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): CoinSelectionResult {
        // Edge case: no fee required
        if (requiredFee <= BigInteger.ZERO) {
            return CoinSelectionResult(
                selectedCoins = emptyList(),
                totalValue = BigInteger.ZERO,
                change = BigInteger.ZERO
            )
        }

        // Edge case: no coins available
        if (availableCoins.isEmpty()) {
            return CoinSelectionResult(
                selectedCoins = emptyList(),
                totalValue = BigInteger.ZERO,
                change = BigInteger.ZERO
            )
        }

        // Smallest-first selection
        val selected = mutableListOf<DustTokenEntity>()
        var totalValue = BigInteger.ZERO

        for (coin in availableCoins) {
            // Calculate current value of this coin (time-based generation)
            val coinValue = calculateCurrentValue(coin, currentTimeMillis)

            selected.add(coin)
            totalValue += coinValue

            // Check if we have enough
            if (totalValue >= requiredFee) {
                val change = totalValue - requiredFee
                return CoinSelectionResult(
                    selectedCoins = selected,
                    totalValue = totalValue,
                    change = change
                )
            }
        }

        // Insufficient dust
        return CoinSelectionResult(
            selectedCoins = emptyList(),
            totalValue = BigInteger.ZERO,
            change = BigInteger.ZERO
        )
    }

    /**
     * Calculate current value of a dust token (time-based generation).
     *
     * **Formula:**
     * ```
     * currentValue = min(
     *     initialValue + (timePassed * generationRate),
     *     dustCapacity
     * )
     * ```
     *
     * **Note:**
     * This is a simplified calculation. For exact values, use DustBalanceCalculator
     * or DustLocalState.getBalance().
     *
     * @param coin Dust token
     * @param currentTimeMillis Current time
     * @return Current value in Specks
     */
    private fun calculateCurrentValue(
        coin: DustTokenEntity,
        currentTimeMillis: Long
    ): BigInteger {
        val initialValue = BigInteger(coin.initialValue)
        val capacity = BigInteger(coin.dustCapacitySpecks)
        val ratePerSecond = BigInteger(coin.generationRatePerSecond)

        // Calculate time passed since creation (in seconds)
        val timePassedMillis = currentTimeMillis - coin.creationTimeMillis
        val timePassedSeconds = timePassedMillis / 1000

        // Calculate generated dust
        val generated = ratePerSecond * BigInteger.valueOf(timePassedSeconds)

        // Current value = initial + generated (capped at capacity)
        val currentValue = initialValue + generated

        return if (currentValue > capacity) capacity else currentValue
    }

    /**
     * Validates coin selection result.
     *
     * **Checks:**
     * - Selected coins sum >= required fee
     * - Change calculation is correct
     * - All selected coins are AVAILABLE state
     *
     * @param result Selection result
     * @param requiredFee Required fee amount
     * @return true if valid, false otherwise
     */
    fun validateSelection(
        result: CoinSelectionResult,
        requiredFee: BigInteger
    ): Boolean {
        // Empty selection is valid only if requiredFee is zero
        if (result.selectedCoins.isEmpty()) {
            return requiredFee <= BigInteger.ZERO
        }

        // Check total value >= required fee
        if (result.totalValue < requiredFee) {
            return false
        }

        // Check change calculation
        val expectedChange = result.totalValue - requiredFee
        if (result.change != expectedChange) {
            return false
        }

        // Check all coins are AVAILABLE
        return result.selectedCoins.all { it.state == com.midnight.kuira.core.indexer.database.UtxoState.AVAILABLE }
    }
}
