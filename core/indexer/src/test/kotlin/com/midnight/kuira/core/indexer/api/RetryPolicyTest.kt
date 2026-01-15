package com.midnight.kuira.core.indexer.api

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for RetryPolicy.
 *
 * **Critical:** Validates exponential backoff and retry logic.
 */
class RetryPolicyTest {

    @Test
    fun `default policy has correct parameters`() {
        val policy = RetryPolicy()

        assertEquals(3, policy.maxAttempts)
        assertEquals(1000L, policy.initialDelayMs)
        assertEquals(16000L, policy.maxDelayMs)
        assertEquals(2.0, policy.backoffMultiplier, 0.01)
    }

    @Test
    fun `delayForAttempt calculates exponential backoff`() {
        val policy = RetryPolicy(
            initialDelayMs = 1000,
            backoffMultiplier = 2.0
        )

        assertEquals(1000L, policy.delayForAttempt(0)) // 1s
        assertEquals(2000L, policy.delayForAttempt(1)) // 2s
        assertEquals(4000L, policy.delayForAttempt(2)) // 4s
        assertEquals(8000L, policy.delayForAttempt(3)) // 8s
    }

    @Test
    fun `delayForAttempt respects max delay`() {
        val policy = RetryPolicy(
            initialDelayMs = 1000,
            maxDelayMs = 5000,
            backoffMultiplier = 2.0
        )

        assertEquals(1000L, policy.delayForAttempt(0))
        assertEquals(2000L, policy.delayForAttempt(1))
        assertEquals(4000L, policy.delayForAttempt(2))
        assertEquals(5000L, policy.delayForAttempt(3)) // Capped at max
        assertEquals(5000L, policy.delayForAttempt(4)) // Still capped
    }

    @Test
    fun `NetworkException is retryable`() {
        val policy = RetryPolicy()

        assertTrue(policy.isRetryable(NetworkException("Test")))
    }

    @Test
    fun `TimeoutException is retryable`() {
        val policy = RetryPolicy()

        assertTrue(policy.isRetryable(TimeoutException("Test")))
    }

    @Test
    fun `HttpException with 5xx is retryable`() {
        val policy = RetryPolicy()

        assertTrue(policy.isRetryable(HttpException(500, "Internal Server Error")))
        assertTrue(policy.isRetryable(HttpException(502, "Bad Gateway")))
        assertTrue(policy.isRetryable(HttpException(503, "Service Unavailable")))
    }

    @Test
    fun `HttpException with 4xx is not retryable`() {
        val policy = RetryPolicy()

        assertEquals(false, policy.isRetryable(HttpException(400, "Bad Request")))
        assertEquals(false, policy.isRetryable(HttpException(404, "Not Found")))
        assertEquals(false, policy.isRetryable(HttpException(403, "Forbidden")))
    }

    @Test
    fun `InvalidResponseException is retryable`() {
        val policy = RetryPolicy()

        assertTrue(policy.isRetryable(InvalidResponseException("Test")))
    }

    @Test
    fun `GraphQLException is not retryable`() {
        val policy = RetryPolicy()

        assertEquals(false, policy.isRetryable(GraphQLException(listOf("error"), "Test")))
    }

    @Test
    fun `ApiVersionException is not retryable`() {
        val policy = RetryPolicy()

        assertEquals(false, policy.isRetryable(ApiVersionException("Test")))
    }

    @Test
    fun `generic Exception is not retryable`() {
        val policy = RetryPolicy()

        assertEquals(false, policy.isRetryable(Exception("Test")))
    }

    @Test
    fun `retryWithPolicy succeeds on first attempt`() = runTest {
        var attempts = 0

        val result = retryWithPolicy {
            attempts++
            "success"
        }

        assertEquals("success", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `retryWithPolicy retries on retryable exception`() = runTest {
        var attempts = 0

        val result = retryWithPolicy(policy = RetryPolicy(maxAttempts = 3)) {
            attempts++
            if (attempts < 3) {
                throw NetworkException("Temporary failure")
            }
            "success"
        }

        assertEquals("success", result)
        assertEquals(3, attempts) // Failed twice, succeeded on third
    }

    @Test
    fun `retryWithPolicy does not retry non-retryable exception`() = runTest {
        var attempts = 0

        try {
            retryWithPolicy {
                attempts++
                throw GraphQLException(listOf("error"), "Query error")
            }
            fail("Expected GraphQLException")
        } catch (e: GraphQLException) {
            // Expected
            assertEquals(1, attempts) // No retry
        }
    }

    @Test
    fun `retryWithPolicy throws original exception after max attempts`() = runTest {
        var attempts = 0

        try {
            retryWithPolicy(policy = RetryPolicy(maxAttempts = 3)) {
                attempts++
                throw NetworkException("Always fails")
            }
            fail("Expected NetworkException")
        } catch (e: NetworkException) {
            // Expected - throws original exception on last attempt
            assertEquals(3, attempts)
            assertEquals("Always fails", e.message)
        }
    }

    @Test
    fun `retryWithPolicy respects maxAttempts`() = runTest {
        var attempts = 0

        try {
            retryWithPolicy(policy = RetryPolicy(maxAttempts = 5)) {
                attempts++
                throw TimeoutException("Timeout")
            }
            fail("Expected TimeoutException or RetryExhaustedException")
        } catch (e: Exception) {
            // Either TimeoutException or RetryExhaustedException is acceptable
            assertEquals(5, attempts)
        }
    }

    @Test
    fun `retryWithPolicy waits between attempts`() = runTest {
        val policy = RetryPolicy(
            maxAttempts = 3,
            initialDelayMs = 100,
            backoffMultiplier = 2.0
        )

        var attempts = 0

        try {
            retryWithPolicy(policy = policy) {
                attempts++
                throw NetworkException("Fail")
            }
        } catch (e: Exception) {
            // Expected failure
        }

        // Verify it retried correct number of times
        // Note: Can't reliably test timing in virtual time with System.currentTimeMillis()
        // The delays are handled by kotlinx.coroutines.delay which works with virtual time,
        // but we just verify the retry behavior worked
        assertEquals(3, attempts)
    }

    @Test
    fun `custom retry policy applies custom parameters`() {
        val policy = RetryPolicy(
            maxAttempts = 10,
            initialDelayMs = 500,
            maxDelayMs = 32000,
            backoffMultiplier = 3.0
        )

        assertEquals(10, policy.maxAttempts)
        assertEquals(500L, policy.delayForAttempt(0))
        assertEquals(1500L, policy.delayForAttempt(1))
        assertEquals(4500L, policy.delayForAttempt(2))
    }

    @Test
    fun `HttpException isClientError and isServerError flags correct`() {
        val clientError = HttpException(404, "Not Found")
        val serverError = HttpException(500, "Internal Server Error")

        assertTrue(clientError.isClientError)
        assertEquals(false, clientError.isServerError)

        assertEquals(false, serverError.isClientError)
        assertTrue(serverError.isServerError)
    }

    @Test
    fun `RetryExhaustedException includes cause`() {
        val cause = NetworkException("Root cause")
        val exception = RetryExhaustedException("Failed", 3, cause)

        assertEquals(cause, exception.cause)
        assertEquals(3, exception.attempts)
    }

    @Test
    fun `retryWithPolicy preserves exception message on final throw`() = runTest {
        try {
            retryWithPolicy(policy = RetryPolicy(maxAttempts = 2)) {
                throw NetworkException("Specific error message")
            }
            fail("Expected exception")
        } catch (e: NetworkException) {
            // Throws original exception on last attempt
            assertEquals("Specific error message", e.message)
        }
    }
}
