package com.midnight.kuira.core.indexer.repository

import com.midnight.kuira.core.indexer.model.TokenBalance
import com.midnight.kuira.core.indexer.utxo.UtxoManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigInteger

/**
 * Unit tests for BalanceRepository.
 *
 * **Test Coverage:**
 * - Transform UtxoManager balance map to TokenBalance list
 * - Sort balances by largest first
 * - Observe single token balance
 * - Observe total balance across all tokens
 * - Handle empty balances
 * - Handle multiple token types
 */
class BalanceRepositoryTest {

    private lateinit var utxoManager: UtxoManager
    private lateinit var repository: BalanceRepository

    private val testAddress = "mn_addr_testnet1test123"

    @Before
    fun setup() {
        utxoManager = mock()
        repository = BalanceRepository(utxoManager)
    }

    // ==================== Transform Balances ====================

    @Test
    fun `observeBalances returns empty list when no balances`() = runTest {
        // Given: No balances
        whenever(utxoManager.observeBalance(testAddress))
            .thenReturn(flowOf(emptyMap()))
        whenever(utxoManager.observeUtxoCounts(testAddress))
            .thenReturn(flowOf(emptyMap()))

        // When: Observe balances
        val result = repository.observeBalances(testAddress).first()

        // Then: Empty list
        assertEquals(0, result.size)
    }

    @Test
    fun `observeBalances transforms balance map to TokenBalance list`() = runTest {
        // Given: Balance map from UtxoManager
        val balanceMap = mapOf(
            "TNIGHT" to BigInteger.valueOf(1_000_000),
            "DUST" to BigInteger.valueOf(2_000_000)
        )
        val utxoCounts = mapOf(
            "TNIGHT" to 2,
            "DUST" to 3
        )
        whenever(utxoManager.observeBalance(testAddress))
            .thenReturn(flowOf(balanceMap))
        whenever(utxoManager.observeUtxoCounts(testAddress))
            .thenReturn(flowOf(utxoCounts))

        // When: Observe balances
        val result = repository.observeBalances(testAddress).first()

        // Then: Transformed to TokenBalance list
        assertEquals(2, result.size)

        // Find by token type
        val tnight = result.find { it.tokenType == "TNIGHT" }
        val dust = result.find { it.tokenType == "DUST" }

        assertEquals(BigInteger.valueOf(1_000_000), tnight?.balance)
        assertEquals(2, tnight?.utxoCount)
        assertEquals(BigInteger.valueOf(2_000_000), dust?.balance)
        assertEquals(3, dust?.utxoCount)
    }

    @Test
    fun `observeBalances sorts by largest balance first`() = runTest {
        // Given: Multiple token types with different amounts
        val balanceMap = mapOf(
            "DUST" to BigInteger.valueOf(500_000),    // Smaller
            "TNIGHT" to BigInteger.valueOf(5_000_000) // Larger
        )
        val utxoCounts = mapOf(
            "DUST" to 1,
            "TNIGHT" to 5
        )
        whenever(utxoManager.observeBalance(testAddress))
            .thenReturn(flowOf(balanceMap))
        whenever(utxoManager.observeUtxoCounts(testAddress))
            .thenReturn(flowOf(utxoCounts))

        // When: Observe balances
        val result = repository.observeBalances(testAddress).first()

        // Then: Sorted by largest first
        assertEquals(2, result.size)
        assertEquals("TNIGHT", result[0].tokenType) // Largest first
        assertEquals(BigInteger.valueOf(5_000_000), result[0].balance)
        assertEquals("DUST", result[1].tokenType)
        assertEquals(BigInteger.valueOf(500_000), result[1].balance)
    }

    @Test
    fun `observeBalances handles single token type`() = runTest {
        // Given: Only TNIGHT balance
        val balanceMap = mapOf(
            "TNIGHT" to BigInteger.valueOf(1_234_567_890)
        )
        val utxoCounts = mapOf(
            "TNIGHT" to 10
        )
        whenever(utxoManager.observeBalance(testAddress))
            .thenReturn(flowOf(balanceMap))
        whenever(utxoManager.observeUtxoCounts(testAddress))
            .thenReturn(flowOf(utxoCounts))

        // When: Observe balances
        val result = repository.observeBalances(testAddress).first()

        // Then: Single TokenBalance
        assertEquals(1, result.size)
        assertEquals("TNIGHT", result[0].tokenType)
        assertEquals(BigInteger.valueOf(1_234_567_890), result[0].balance)
    }

    // ==================== Observe Single Token ====================

    @Test
    fun `observeTokenBalance returns specific token`() = runTest {
        // Given: Multiple token types
        val balanceMap = mapOf(
            "TNIGHT" to BigInteger.valueOf(1_000_000),
            "DUST" to BigInteger.valueOf(2_000_000)
        )
        val utxoCounts = mapOf(
            "TNIGHT" to 2,
            "DUST" to 3
        )
        whenever(utxoManager.observeBalance(testAddress))
            .thenReturn(flowOf(balanceMap))
        whenever(utxoManager.observeUtxoCounts(testAddress))
            .thenReturn(flowOf(utxoCounts))

        // When: Observe DUST only
        val result = repository.observeTokenBalance(testAddress, "DUST").first()

        // Then: Returns DUST balance
        assertEquals("DUST", result?.tokenType)
        assertEquals(BigInteger.valueOf(2_000_000), result?.balance)
    }

    @Test
    fun `observeTokenBalance returns null when token not found`() = runTest {
        // Given: Only TNIGHT balance
        val balanceMap = mapOf(
            "TNIGHT" to BigInteger.valueOf(1_000_000)
        )
        val utxoCounts = mapOf(
            "TNIGHT" to 1
        )
        whenever(utxoManager.observeBalance(testAddress))
            .thenReturn(flowOf(balanceMap))
        whenever(utxoManager.observeUtxoCounts(testAddress))
            .thenReturn(flowOf(utxoCounts))

        // When: Observe DUST (not present)
        val result = repository.observeTokenBalance(testAddress, "DUST").first()

        // Then: Returns null
        assertNull(result)
    }

    // ==================== Observe Total Balance ====================

    @Test
    fun `observeTotalBalance sums across all token types`() = runTest {
        // Given: Available balances
        val availableBalanceMap = mapOf(
            "TNIGHT" to BigInteger.valueOf(1_000_000),
            "DUST" to BigInteger.valueOf(2_000_000)
        )
        val utxoCounts = mapOf("TNIGHT" to 1, "DUST" to 2)

        // And: Pending balances
        val pendingBalanceMap = mapOf(
            "TNIGHT" to BigInteger.valueOf(500_000)
        )

        whenever(utxoManager.observeBalance(testAddress))
            .thenReturn(flowOf(availableBalanceMap))
        whenever(utxoManager.observeUtxoCounts(testAddress))
            .thenReturn(flowOf(utxoCounts))
        whenever(utxoManager.observePendingBalance(testAddress))
            .thenReturn(flowOf(pendingBalanceMap))

        // When: Observe total
        val result = repository.observeTotalBalance(testAddress).first()

        // Then: Sum of available + pending (3M + 500K = 3.5M)
        assertEquals(BigInteger.valueOf(3_500_000), result)
    }

    @Test
    fun `observeTotalBalance returns zero when no balances`() = runTest {
        // Given: No balances
        whenever(utxoManager.observeBalance(testAddress))
            .thenReturn(flowOf(emptyMap()))
        whenever(utxoManager.observeUtxoCounts(testAddress))
            .thenReturn(flowOf(emptyMap()))
        whenever(utxoManager.observePendingBalance(testAddress))
            .thenReturn(flowOf(emptyMap()))

        // When: Observe total
        val result = repository.observeTotalBalance(testAddress).first()

        // Then: Zero
        assertEquals(BigInteger.ZERO, result)
    }

    @Test
    fun `observeTotalBalance handles single token`() = runTest {
        // Given: Single token available
        val availableBalanceMap = mapOf(
            "TNIGHT" to BigInteger.valueOf(1_000_000)
        )
        val utxoCounts = mapOf("TNIGHT" to 1)

        whenever(utxoManager.observeBalance(testAddress))
            .thenReturn(flowOf(availableBalanceMap))
        whenever(utxoManager.observeUtxoCounts(testAddress))
            .thenReturn(flowOf(utxoCounts))
        whenever(utxoManager.observePendingBalance(testAddress))
            .thenReturn(flowOf(emptyMap()))

        // When: Observe total
        val result = repository.observeTotalBalance(testAddress).first()

        // Then: Returns single balance
        assertEquals(BigInteger.valueOf(1_000_000), result)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `observeBalances handles large amounts correctly`() = runTest {
        // Given: Large balance amounts
        val balanceMap = mapOf(
            "TNIGHT" to BigInteger.valueOf(Long.MAX_VALUE / 2)
        )
        val utxoCounts = mapOf("TNIGHT" to 1)
        whenever(utxoManager.observeBalance(testAddress))
            .thenReturn(flowOf(balanceMap))
        whenever(utxoManager.observeUtxoCounts(testAddress))
            .thenReturn(flowOf(utxoCounts))

        // When: Observe balances
        val result = repository.observeBalances(testAddress).first()

        // Then: Preserves large amount
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE / 2), result[0].balance)
    }

    @Test
    fun `observeBalances handles zero balances`() = runTest {
        // Given: Zero balance for a token
        val balanceMap = mapOf(
            "TNIGHT" to BigInteger.ZERO,
            "DUST" to BigInteger.valueOf(1_000_000)
        )
        val utxoCounts = mapOf("TNIGHT" to 0, "DUST" to 2)
        whenever(utxoManager.observeBalance(testAddress))
            .thenReturn(flowOf(balanceMap))
        whenever(utxoManager.observeUtxoCounts(testAddress))
            .thenReturn(flowOf(utxoCounts))

        // When: Observe balances
        val result = repository.observeBalances(testAddress).first()

        // Then: Both tokens present, sorted by balance
        assertEquals(2, result.size)
        assertEquals("DUST", result[0].tokenType) // Non-zero first
        assertEquals("TNIGHT", result[1].tokenType) // Zero last
    }

    @Test
    fun `observeTotalBalance handles very large sums`() = runTest {
        // Given: Multiple large available balances
        val availableBalanceMap = mapOf(
            "TNIGHT" to BigInteger.valueOf(Long.MAX_VALUE / 3),
            "DUST" to BigInteger.valueOf(Long.MAX_VALUE / 3)
        )
        val utxoCounts = mapOf("TNIGHT" to 1, "DUST" to 1)

        whenever(utxoManager.observeBalance(testAddress))
            .thenReturn(flowOf(availableBalanceMap))
        whenever(utxoManager.observeUtxoCounts(testAddress))
            .thenReturn(flowOf(utxoCounts))
        whenever(utxoManager.observePendingBalance(testAddress))
            .thenReturn(flowOf(emptyMap()))

        // When: Observe total
        val result = repository.observeTotalBalance(testAddress).first()

        // Then: Sum is correct (BigInteger handles overflow)
        val expected = BigInteger.valueOf(Long.MAX_VALUE / 3).multiply(BigInteger.TWO)
        assertEquals(expected, result)
    }
}
