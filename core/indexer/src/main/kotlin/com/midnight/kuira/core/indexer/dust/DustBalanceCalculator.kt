package com.midnight.kuira.core.indexer.dust

import com.midnight.kuira.core.indexer.database.DustTokenEntity
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

/**
 * Domain logic for calculating time-based dust balance.
 *
 * **Separation of Concerns:**
 * - DustTokenEntity: Dumb data container (Data layer)
 * - DustBalanceCalculator: Business logic (Domain layer)
 * - DustRepository: Orchestration (Data layer)
 *
 * **Why separate class?**
 * Follows Clean Architecture - keep business logic out of entities.
 * Makes logic reusable and testable in isolation.
 *
 * **Formula (from midnight-ledger spec):**
 * - Phase 1 (Generation): value grows from initialValue to capacity
 *   - elapsed = (currentTimeMillis - creationTimeMillis) / 1000 (to seconds)
 *   - generated = elapsed * generationRatePerSecond
 *   - current = initialValue + generated
 *   - capped = min(current, dustCapacitySpecks)
 *
 * **Usage:**
 * ```kotlin
 * val calculator = DustBalanceCalculator()
 * val currentValue = calculator.calculateCurrentValue(
 *     token = dustToken,
 *     currentTimeMillis = System.currentTimeMillis()
 * )
 * ```
 */
class DustBalanceCalculator @Inject constructor() {

    /**
     * Calculate current dust value for a token at a specific time.
     *
     * @param token Dust token entity
     * @param currentTimeMillis Current time in milliseconds
     * @return Current dust value in Specks (BigInteger)
     */
    fun calculateCurrentValue(
        token: DustTokenEntity,
        currentTimeMillis: Long
    ): BigInteger {
        // Parse values (handle parse errors gracefully)
        val initial = token.initialValue.toBigIntegerOrNull() ?: return BigInteger.ZERO
        val capacity = token.dustCapacitySpecks.toBigIntegerOrNull() ?: return initial
        val rate = token.generationRatePerSecond.toBigDecimalOrNull() ?: return initial

        // Calculate elapsed time in seconds
        val elapsedMillis = currentTimeMillis - token.creationTimeMillis
        if (elapsedMillis < 0) {
            // Time went backwards? Return initial value
            return initial
        }
        val elapsedSeconds = elapsedMillis / 1000

        // Calculate generated dust
        val generated = rate.multiply(elapsedSeconds.toBigDecimal()).toBigInteger()

        // Current value = initial + generated (capped at capacity)
        val current = initial + generated
        return if (current > capacity) capacity else current
    }

    /**
     * Calculate total balance from multiple tokens.
     *
     * @param tokens List of dust tokens
     * @param currentTimeMillis Current time in milliseconds
     * @return Total balance in Specks (BigInteger)
     */
    fun calculateTotalBalance(
        tokens: List<DustTokenEntity>,
        currentTimeMillis: Long
    ): BigInteger {
        return tokens.fold(BigInteger.ZERO) { acc, token ->
            acc + calculateCurrentValue(token, currentTimeMillis)
        }
    }

    /**
     * Check if token has reached maximum capacity.
     *
     * @param token Dust token entity
     * @param currentTimeMillis Current time in milliseconds
     * @return true if at capacity, false otherwise
     */
    fun isAtCapacity(
        token: DustTokenEntity,
        currentTimeMillis: Long
    ): Boolean {
        val current = calculateCurrentValue(token, currentTimeMillis)
        val capacity = token.dustCapacitySpecks.toBigIntegerOrNull() ?: return false
        return current >= capacity
    }

    /**
     * Calculate time remaining until token reaches capacity.
     *
     * @param token Dust token entity
     * @param currentTimeMillis Current time in milliseconds
     * @return Milliseconds until capacity, or 0 if already at capacity
     */
    fun calculateTimeToCapacity(
        token: DustTokenEntity,
        currentTimeMillis: Long
    ): Long {
        if (isAtCapacity(token, currentTimeMillis)) {
            return 0
        }

        val current = calculateCurrentValue(token, currentTimeMillis)
        val capacity = token.dustCapacitySpecks.toBigIntegerOrNull() ?: return 0
        val rate = token.generationRatePerSecond.toBigDecimalOrNull() ?: return 0

        if (rate <= BigDecimal.ZERO) {
            return Long.MAX_VALUE // Never reaches capacity
        }

        // Remaining = capacity - current
        val remaining = capacity - current

        // Time = remaining / rate (seconds)
        val secondsToCapacity = remaining.toBigDecimal().divide(
            rate,
            0,
            java.math.RoundingMode.CEILING
        ).toLong()

        return secondsToCapacity * 1000 // Convert to milliseconds
    }
}
