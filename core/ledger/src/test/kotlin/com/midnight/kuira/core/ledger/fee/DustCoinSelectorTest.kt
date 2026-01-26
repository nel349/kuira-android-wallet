// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.ledger.fee

import com.midnight.kuira.core.indexer.database.DustTokenEntity
import com.midnight.kuira.core.indexer.database.UtxoState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

/**
 * Unit tests for DustCoinSelector.
 *
 * Tests smallest-first coin selection strategy with various scenarios:
 * - Exact match (no change)
 * - Insufficient dust
 * - Change calculation
 * - Empty coins list
 * - Zero fee
 * - Edge cases
 */
class DustCoinSelectorTest {

    private lateinit var coinSelector: DustCoinSelector

    @Before
    fun setup() {
        coinSelector = DustCoinSelector()
    }

    // ==================== Helper Functions ====================

    /**
     * Fixed timestamp for deterministic tests (no time-based dust accumulation).
     */
    private val FIXED_TIME_MILLIS = 1000000000000L // Jan 9, 2001

    /**
     * Creates a test dust token with specified value.
     */
    private fun createDustToken(
        nullifier: String,
        valueSpecks: Long,
        creationTimeMillis: Long = FIXED_TIME_MILLIS
    ): DustTokenEntity {
        return DustTokenEntity(
            nullifier = nullifier,
            address = "mn_addr_test1...",
            initialValue = valueSpecks.toString(),
            creationTimeMillis = creationTimeMillis,
            nightUtxoId = "test_utxo",
            nightValueStars = "1000000", // 1 NIGHT
            dustCapacitySpecks = "5000000", // 5 Dust capacity
            generationRatePerSecond = "8267", // Standard rate
            state = UtxoState.AVAILABLE
        )
    }

    // ==================== Basic Functionality Tests ====================

    @Test
    fun `given sufficient coins when select then success`() {
        // Given: coins = [1, 3, 10, 50] Specks, fee = 12 Specks
        val coins = listOf(
            createDustToken("null1", 1),
            createDustToken("null2", 3),
            createDustToken("null3", 10),
            createDustToken("null4", 50)
        )

        // When
        val result = coinSelector.selectCoins(
            availableCoins = coins,
            requiredFee = BigInteger.valueOf(12),
            currentTimeMillis = FIXED_TIME_MILLIS
        )

        // Then
        assertTrue("Should succeed", result.isSuccess())
        assertEquals("Should select 3 coins", 3, result.selectedCoins.size)
        assertTrue("Total >= fee", result.totalValue >= BigInteger.valueOf(12))
        assertTrue("Change should be positive", result.change > BigInteger.ZERO)
    }

    @Test
    fun `given exact match when select then no change`() {
        // Given: coins = [5, 10] Specks, fee = 15 Specks (exact)
        val coins = listOf(
            createDustToken("null1", 5),
            createDustToken("null2", 10)
        )

        // When
        val result = coinSelector.selectCoins(
            availableCoins = coins,
            requiredFee = BigInteger.valueOf(15),
            currentTimeMillis = FIXED_TIME_MILLIS
        )

        // Then
        assertTrue("Should succeed", result.isSuccess())
        assertEquals("Should select 2 coins", 2, result.selectedCoins.size)
        assertEquals("Total should equal fee", BigInteger.valueOf(15), result.totalValue)
        assertEquals("No change", BigInteger.ZERO, result.change)
    }

    @Test
    fun `given insufficient dust when select then failure`() {
        // Given: coins = [1, 2, 3] Specks, fee = 10 Specks (insufficient)
        val coins = listOf(
            createDustToken("null1", 1),
            createDustToken("null2", 2),
            createDustToken("null3", 3)
        )

        // When
        val result = coinSelector.selectCoins(
            availableCoins = coins,
            requiredFee = BigInteger.valueOf(10),
            currentTimeMillis = FIXED_TIME_MILLIS
        )

        // Then
        assertFalse("Should fail", result.isSuccess())
        assertTrue("Selected coins should be empty", result.selectedCoins.isEmpty())
        assertEquals("Total value should be zero", BigInteger.ZERO, result.totalValue)
    }

    @Test
    fun `given empty coins when select then failure`() {
        // Given: no coins available
        val coins = emptyList<DustTokenEntity>()

        // When
        val result = coinSelector.selectCoins(
            availableCoins = coins,
            requiredFee = BigInteger.valueOf(10),
            currentTimeMillis = FIXED_TIME_MILLIS
        )

        // Then
        assertFalse("Should fail", result.isSuccess())
        assertTrue("Selected coins should be empty", result.selectedCoins.isEmpty())
    }

    @Test
    fun `given zero fee when select then empty selection`() {
        // Given: coins available but fee = 0
        val coins = listOf(
            createDustToken("null1", 100)
        )

        // When
        val result = coinSelector.selectCoins(
            availableCoins = coins,
            requiredFee = BigInteger.ZERO,
            currentTimeMillis = FIXED_TIME_MILLIS
        )

        // Then
        assertTrue("Selected coins should be empty", result.selectedCoins.isEmpty())
        assertEquals("Total value should be zero", BigInteger.ZERO, result.totalValue)
    }

    // ==================== Smallest-First Strategy Tests ====================

    @Test
    fun `given unsorted coins when select then picks smallest first`() {
        // Given: coins in random order (already sorted smallest-first by repository)
        val coins = listOf(
            createDustToken("null1", 1),
            createDustToken("null2", 5),
            createDustToken("null3", 10),
            createDustToken("null4", 50)
        )

        // When: fee = 15 Specks
        val result = coinSelector.selectCoins(
            availableCoins = coins,
            requiredFee = BigInteger.valueOf(15),
            currentTimeMillis = FIXED_TIME_MILLIS
        )

        // Then: should select [1, 5, 10] = 16 Specks (smallest first until >= 15)
        assertTrue("Should succeed", result.isSuccess())
        assertEquals("Should select 3 coins", 3, result.selectedCoins.size)

        // Verify smallest coins were selected
        val selectedValues = result.selectedCoins.map { it.initialValue.toLong() }
        assertTrue("Should include 1", selectedValues.contains(1))
        assertTrue("Should include 5", selectedValues.contains(5))
        assertTrue("Should include 10", selectedValues.contains(10))
    }

    @Test
    fun `given large UTXO when fee small then prefer small UTXOs`() {
        // Given: mix of small and large UTXOs (sorted smallest-first)
        val coins = listOf(
            createDustToken("null1", 1),
            createDustToken("null2", 2),
            createDustToken("null3", 1000) // Large UTXO
        )

        // When: fee = 2 Specks
        val result = coinSelector.selectCoins(
            availableCoins = coins,
            requiredFee = BigInteger.valueOf(2),
            currentTimeMillis = FIXED_TIME_MILLIS
        )

        // Then: should select [1, 2] = 3 Specks, NOT the large UTXO
        assertTrue("Should succeed", result.isSuccess())
        assertEquals("Should select 2 small coins", 2, result.selectedCoins.size)

        val selectedValues = result.selectedCoins.map { it.initialValue.toLong() }
        assertFalse("Should NOT select large UTXO", selectedValues.contains(1000))
    }

    // ==================== Change Calculation Tests ====================

    @Test
    fun `given overpayment when select then correct change`() {
        // Given: coins = [10, 20] Specks, fee = 15 Specks
        val coins = listOf(
            createDustToken("null1", 10),
            createDustToken("null2", 20)
        )

        // When
        val result = coinSelector.selectCoins(
            availableCoins = coins,
            requiredFee = BigInteger.valueOf(15),
            currentTimeMillis = FIXED_TIME_MILLIS
        )

        // Then: total = 30, change = 30 - 15 = 15
        assertTrue("Should succeed", result.isSuccess())
        assertEquals("Total should be 30", BigInteger.valueOf(30), result.totalValue)
        assertEquals("Change should be 15", BigInteger.valueOf(15), result.change)
        assertTrue("Should have change", result.hasChange())
    }

    // ==================== Validation Tests ====================

    @Test
    fun `given valid selection when validate then true`() {
        // Given
        val coins = listOf(
            createDustToken("null1", 10),
            createDustToken("null2", 20)
        )
        val result = coinSelector.selectCoins(coins, BigInteger.valueOf(15))

        // When
        val isValid = coinSelector.validateSelection(result, BigInteger.valueOf(15))

        // Then
        assertTrue("Should be valid", isValid)
    }

    @Test
    fun `given insufficient selection when validate then false`() {
        // Given: manually create invalid result
        val result = DustCoinSelector.CoinSelectionResult(
            selectedCoins = emptyList(),
            totalValue = BigInteger.ZERO,
            change = BigInteger.ZERO
        )

        // When
        val isValid = coinSelector.validateSelection(result, BigInteger.valueOf(10))

        // Then
        assertFalse("Should be invalid", isValid)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `given single large coin when select then success`() {
        // Given: one coin that covers fee
        val coins = listOf(
            createDustToken("null1", 1000)
        )

        // When
        val result = coinSelector.selectCoins(
            availableCoins = coins,
            requiredFee = BigInteger.valueOf(100),
            currentTimeMillis = FIXED_TIME_MILLIS
        )

        // Then
        assertTrue("Should succeed", result.isSuccess())
        assertEquals("Should select 1 coin", 1, result.selectedCoins.size)
        assertEquals("Change should be 900", BigInteger.valueOf(900), result.change)
    }

    @Test
    fun `given negative fee when select then empty selection`() {
        // Given: invalid negative fee
        val coins = listOf(
            createDustToken("null1", 100)
        )

        // When
        val result = coinSelector.selectCoins(
            availableCoins = coins,
            requiredFee = BigInteger.valueOf(-10),
            currentTimeMillis = FIXED_TIME_MILLIS
        )

        // Then
        assertTrue("Selected coins should be empty", result.selectedCoins.isEmpty())
    }

    @Test
    fun `given very large fee when select with small coins then failure`() {
        // Given: fee larger than all coins combined
        val coins = listOf(
            createDustToken("null1", 1),
            createDustToken("null2", 2),
            createDustToken("null3", 3)
        )

        // When
        val result = coinSelector.selectCoins(
            availableCoins = coins,
            requiredFee = BigInteger("1000000000000000"), // 1 quadrillion Specks
            currentTimeMillis = FIXED_TIME_MILLIS
        )

        // Then
        assertFalse("Should fail", result.isSuccess())
    }
}
