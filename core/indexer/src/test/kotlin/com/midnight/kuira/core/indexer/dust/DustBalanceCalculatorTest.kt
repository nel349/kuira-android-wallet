package com.midnight.kuira.core.indexer.dust

import com.midnight.kuira.core.indexer.database.DustTokenEntity
import com.midnight.kuira.core.indexer.database.UtxoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

/**
 * Unit tests for DustBalanceCalculator.
 *
 * **Test Coverage:**
 * - Calculate current value at specific time
 * - Time-based dust generation
 * - Capacity capping (value doesn't exceed max)
 * - Total balance calculation from multiple tokens
 * - Capacity detection
 * - Time to capacity calculation
 * - Edge cases (zero rate, negative time, etc.)
 *
 * **Why Test Domain Logic:**
 * DustBalanceCalculator contains critical business logic for dust generation.
 * Incorrect calculations = wrong fees = failed transactions.
 * Must verify against Midnight SDK spec.
 */
class DustBalanceCalculatorTest {

    private lateinit var calculator: DustBalanceCalculator

    // Test constants matching Midnight SDK defaults
    private val testAddress = "mn_addr_testnet1test123"
    private val testNullifier = "0x1234567890abcdef"
    private val testNightUtxoId = "intent123:0"

    // Midnight default parameters (from midnight-ledger spec)
    private val generationRatePerStar = 8267L // Specks per Star per second
    private val nightDustRatio = 5_000_000L // 5 Dust per Night = 5,000,000 Specks per Star

    @Before
    fun setup() {
        calculator = DustBalanceCalculator()
    }

    // ==================== Basic Calculation Tests ====================

    @Test
    fun `calculateCurrentValue returns initial value at creation time`() {
        // Given: Token just created (elapsed time = 0)
        val creationTime = 1000000L
        val initialValue = "100000" // 100,000 Specks
        val token = createTestToken(
            initialValue = initialValue,
            creationTimeMillis = creationTime
        )

        // When: Calculate value at same time as creation
        val result = calculator.calculateCurrentValue(token, creationTime)

        // Then: Should return initial value (no time passed)
        assertEquals(BigInteger(initialValue), result)
    }

    @Test
    fun `calculateCurrentValue generates dust over time`() {
        // Given: Token with 1 Star Night backing, rate = 8,267 Specks/second
        val creationTime = 0L
        val nightValueStars = "1" // 1 Star
        val rate = generationRatePerStar.toString()
        val token = createTestToken(
            initialValue = "0",
            creationTimeMillis = creationTime,
            nightValueStars = nightValueStars,
            generationRatePerSecond = rate
        )

        // When: 10 seconds pass
        val currentTime = 10_000L // 10 seconds in millis
        val result = calculator.calculateCurrentValue(token, currentTime)

        // Then: Should generate 10 seconds * 8,267 = 82,670 Specks
        val expected = BigInteger.valueOf(10 * generationRatePerStar)
        assertEquals(expected, result)
    }

    @Test
    fun `calculateCurrentValue caps at maximum capacity`() {
        // Given: Token with small capacity, high rate
        val creationTime = 0L
        val capacity = "10000" // 10,000 Specks cap
        val rate = "1000" // 1,000 Specks/second
        val token = createTestToken(
            initialValue = "0",
            creationTimeMillis = creationTime,
            dustCapacitySpecks = capacity,
            generationRatePerSecond = rate
        )

        // When: Enough time to exceed capacity (100 seconds)
        val currentTime = 100_000L
        val result = calculator.calculateCurrentValue(token, currentTime)

        // Then: Should cap at 10,000 Specks
        assertEquals(BigInteger(capacity), result)
    }

    @Test
    fun `calculateCurrentValue handles negative time gracefully`() {
        // Given: Token created at time 1000
        val creationTime = 1000L
        val initialValue = "5000"
        val token = createTestToken(
            initialValue = initialValue,
            creationTimeMillis = creationTime
        )

        // When: Current time is BEFORE creation (time travel!)
        val currentTime = 500L
        val result = calculator.calculateCurrentValue(token, currentTime)

        // Then: Should return initial value (fallback)
        assertEquals(BigInteger(initialValue), result)
    }

    @Test
    fun `calculateCurrentValue handles zero generation rate`() {
        // Given: Token with zero generation rate (shouldn't happen, but handle gracefully)
        val token = createTestToken(
            initialValue = "1000",
            creationTimeMillis = 0L,
            generationRatePerSecond = "0"
        )

        // When: Time passes
        val result = calculator.calculateCurrentValue(token, 10_000L)

        // Then: Should return initial value (no generation)
        assertEquals(BigInteger("1000"), result)
    }

    // ==================== Total Balance Tests ====================

    @Test
    fun `calculateTotalBalance sums multiple tokens`() {
        // Given: 3 tokens with different values
        val currentTime = 10_000L
        val token1 = createTestToken(
            initialValue = "1000",
            creationTimeMillis = 0L,
            generationRatePerSecond = "100"
        )
        val token2 = createTestToken(
            initialValue = "2000",
            creationTimeMillis = 0L,
            generationRatePerSecond = "200"
        )
        val token3 = createTestToken(
            initialValue = "3000",
            creationTimeMillis = 0L,
            generationRatePerSecond = "300"
        )

        // When: Calculate total balance
        val result = calculator.calculateTotalBalance(
            tokens = listOf(token1, token2, token3),
            currentTimeMillis = currentTime
        )

        // Then: Sum of all values
        // Token1: 1000 + (10 * 100) = 2000
        // Token2: 2000 + (10 * 200) = 4000
        // Token3: 3000 + (10 * 300) = 6000
        // Total: 12000
        assertEquals(BigInteger("12000"), result)
    }

    @Test
    fun `calculateTotalBalance returns zero for empty list`() {
        // When: No tokens
        val result = calculator.calculateTotalBalance(emptyList(), 0L)

        // Then: Zero balance
        assertEquals(BigInteger.ZERO, result)
    }

    // ==================== Capacity Tests ====================

    @Test
    fun `isAtCapacity returns true when value reaches capacity`() {
        // Given: Token at capacity
        val token = createTestToken(
            initialValue = "10000",
            dustCapacitySpecks = "10000",
            creationTimeMillis = 0L
        )

        // When: Check capacity
        val result = calculator.isAtCapacity(token, 0L)

        // Then: At capacity
        assertTrue(result)
    }

    @Test
    fun `isAtCapacity returns false when value below capacity`() {
        // Given: Token below capacity
        val token = createTestToken(
            initialValue = "5000",
            dustCapacitySpecks = "10000",
            creationTimeMillis = 0L
        )

        // When: Check capacity
        val result = calculator.isAtCapacity(token, 0L)

        // Then: Not at capacity
        assertFalse(result)
    }

    @Test
    fun `isAtCapacity returns true when generated value reaches capacity`() {
        // Given: Token that reaches capacity through generation
        val token = createTestToken(
            initialValue = "0",
            dustCapacitySpecks = "1000",
            generationRatePerSecond = "100",
            creationTimeMillis = 0L
        )

        // When: Check after 10 seconds (generates 1000 Specks)
        val result = calculator.isAtCapacity(token, 10_000L)

        // Then: At capacity
        assertTrue(result)
    }

    // ==================== Time to Capacity Tests ====================

    @Test
    fun `calculateTimeToCapacity returns zero when already at capacity`() {
        // Given: Token at capacity
        val token = createTestToken(
            initialValue = "10000",
            dustCapacitySpecks = "10000",
            creationTimeMillis = 0L
        )

        // When: Calculate time to capacity
        val result = calculator.calculateTimeToCapacity(token, 0L)

        // Then: Already there
        assertEquals(0L, result)
    }

    @Test
    fun `calculateTimeToCapacity calculates correct time remaining`() {
        // Given: Token halfway to capacity, rate = 100/sec
        val token = createTestToken(
            initialValue = "5000",
            dustCapacitySpecks = "10000",
            generationRatePerSecond = "100",
            creationTimeMillis = 0L
        )

        // When: Calculate time to capacity
        val result = calculator.calculateTimeToCapacity(token, 0L)

        // Then: Need to generate 5000 more at 100/sec = 50 seconds = 50,000 millis
        assertEquals(50_000L, result)
    }

    @Test
    fun `calculateTimeToCapacity handles zero rate`() {
        // Given: Token with zero generation rate
        val token = createTestToken(
            initialValue = "5000",
            dustCapacitySpecks = "10000",
            generationRatePerSecond = "0",
            creationTimeMillis = 0L
        )

        // When: Calculate time to capacity
        val result = calculator.calculateTimeToCapacity(token, 0L)

        // Then: Never reaches capacity
        assertEquals(Long.MAX_VALUE, result)
    }

    // ==================== Real-World Midnight SDK Scenario ====================

    @Test
    fun `calculateCurrentValue matches Midnight SDK for 1 NIGHT token`() {
        // Given: 1 NIGHT (1,000,000 Stars) registered for dust
        // Midnight defaults: 8,267 Specks per Star per second
        // Capacity: 5 Dust per Night = 5,000,000 Specks per Star = 5,000,000,000,000 total Specks
        val nightValueStars = "1000000" // 1 NIGHT = 1,000,000 Stars
        val ratePerSecond = (nightValueStars.toLong() * generationRatePerStar).toString()
        val capacity = (nightValueStars.toLong() * nightDustRatio).toString()

        val token = createTestToken(
            initialValue = "0",
            creationTimeMillis = 0L,
            nightValueStars = nightValueStars,
            dustCapacitySpecks = capacity,
            generationRatePerSecond = ratePerSecond
        )

        // When: 1 second passes
        val result = calculator.calculateCurrentValue(token, 1000L)

        // Then: Should generate nightValueStars * 8,267 Specks
        val expected = nightValueStars.toLong() * generationRatePerStar
        assertEquals(BigInteger.valueOf(expected), result)
    }

    @Test
    fun `calculateTimeToCapacity matches Midnight SDK for 1 NIGHT token`() {
        // Given: 1 NIGHT token
        val nightValueStars = "1000000"
        val ratePerSecond = (nightValueStars.toLong() * generationRatePerStar).toString()
        val capacity = (nightValueStars.toLong() * nightDustRatio).toString()

        val token = createTestToken(
            initialValue = "0",
            creationTimeMillis = 0L,
            nightValueStars = nightValueStars,
            dustCapacitySpecks = capacity,
            generationRatePerSecond = ratePerSecond
        )

        // When: Calculate time to capacity
        val result = calculator.calculateTimeToCapacity(token, 0L)

        // Then: Should take capacity / rate seconds (with CEILING rounding)
        // Capacity = 5,000,000,000,000 Specks
        // Rate = 8,267,000,000 Specks/second
        // Time = 5,000,000,000,000 / 8,267,000,000 = 604.833... seconds
        // Rounded up (CEILING) = 605 seconds = 10 minutes 5 seconds
        val expectedSeconds = 605L // Ceiling division result
        val expectedMillis = expectedSeconds * 1000
        assertEquals(expectedMillis, result)
    }

    // ==================== Helper Methods ====================

    private fun createTestToken(
        nullifier: String = testNullifier,
        address: String = testAddress,
        initialValue: String = "0",
        creationTimeMillis: Long = 0L,
        nightUtxoId: String = testNightUtxoId,
        nightValueStars: String = "1",
        dustCapacitySpecks: String = "5000000", // 5 Dust default
        generationRatePerSecond: String = "8267", // Default rate
        state: UtxoState = UtxoState.AVAILABLE
    ): DustTokenEntity {
        return DustTokenEntity.create(
            nullifier = nullifier,
            address = address,
            initialValue = initialValue,
            creationTimeMillis = creationTimeMillis,
            nightUtxoId = nightUtxoId,
            nightValueStars = nightValueStars,
            dustCapacitySpecks = dustCapacitySpecks,
            generationRatePerSecond = generationRatePerSecond,
            state = state
        )
    }
}
