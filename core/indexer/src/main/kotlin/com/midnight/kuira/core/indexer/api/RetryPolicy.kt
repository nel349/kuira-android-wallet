package com.midnight.kuira.core.indexer.api

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

/**
 * Retry policy for indexer API calls with exponential backoff.
 *
 * **Strategy:**
 * - Network errors: Retry with exponential backoff
 * - Server errors (5xx): Retry with exponential backoff
 * - Client errors (4xx): Don't retry (request is invalid)
 * - Timeout: Retry with longer timeout
 *
 * **Exponential Backoff:**
 * - Attempt 1: Wait 1s
 * - Attempt 2: Wait 2s
 * - Attempt 3: Wait 4s
 * - Attempt 4: Wait 8s
 * - Attempt 5: Wait 16s (max)
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 16000L,
    val backoffMultiplier: Double = 2.0
) {
    /**
     * Calculate delay for given attempt number.
     *
     * @param attempt Attempt number (0-indexed)
     * @return Delay in milliseconds
     */
    fun delayForAttempt(attempt: Int): Long {
        val exponentialDelay = initialDelayMs * backoffMultiplier.pow(attempt).toLong()
        return min(exponentialDelay, maxDelayMs)
    }

    /**
     * Check if exception is retryable.
     *
     * @param exception Exception to check
     * @return True if should retry
     */
    fun isRetryable(exception: Throwable): Boolean {
        return when (exception) {
            is NetworkException -> true
            is TimeoutException -> true
            is HttpException -> exception.isServerError // Retry 5xx, not 4xx
            is InvalidResponseException -> true
            is GraphQLException -> false // Query errors not retryable
            is ApiVersionException -> false // Version mismatch not retryable
            else -> false
        }
    }
}

/**
 * Execute a suspending function with retry logic.
 *
 * @param policy Retry policy to use
 * @param block Suspending function to execute
 * @return Result of block execution
 * @throws RetryExhaustedException if max attempts exceeded
 */
suspend fun <T> retryWithPolicy(
    policy: RetryPolicy = RetryPolicy(),
    block: suspend () -> T
): T {
    var lastException: Throwable? = null

    repeat(policy.maxAttempts) { attempt ->
        try {
            return block()
        } catch (e: Throwable) {
            lastException = e

            // Don't retry if not retryable or last attempt
            if (!policy.isRetryable(e) || attempt == policy.maxAttempts - 1) {
                throw e
            }

            // Wait before retry
            val delayMs = policy.delayForAttempt(attempt)
            delay(delayMs)
        }
    }

    // Should never reach here, but throw if we do
    throw RetryExhaustedException(
        "Retry exhausted after ${policy.maxAttempts} attempts",
        policy.maxAttempts,
        lastException
    )
}
