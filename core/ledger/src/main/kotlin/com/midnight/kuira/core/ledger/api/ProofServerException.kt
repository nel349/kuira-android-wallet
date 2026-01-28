package com.midnight.kuira.core.ledger.api

/**
 * Base exception for all proof server errors.
 */
sealed class ProofServerException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Network connectivity error (connection refused, DNS failure, etc.).
 */
class ProofServerNetworkException(
    message: String,
    cause: Throwable? = null
) : ProofServerException(message, cause)

/**
 * HTTP error (non-2xx status code).
 */
class ProofServerHttpException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null
) : ProofServerException("HTTP $statusCode: $message", cause)

/**
 * Timeout error (proof generation took too long).
 */
class ProofServerTimeoutException(
    message: String,
    cause: Throwable? = null
) : ProofServerException(message, cause)

/**
 * Proof server returned an error response.
 */
class ProofServerError(
    val statusCode: Int,
    message: String,
    val responseBody: String? = null,
    cause: Throwable? = null
) : ProofServerException(
    "Proof server error ($statusCode): $message${responseBody?.let { "\nResponse: $it" } ?: ""}",
    cause
)

/**
 * Proof computation failed (proof server could not generate valid proof).
 */
class ProofComputationException(
    message: String,
    cause: Throwable? = null
) : ProofServerException(message, cause)

/**
 * Invalid or malformed response from proof server.
 */
class ProofServerInvalidResponseException(
    message: String,
    cause: Throwable? = null
) : ProofServerException(message, cause)
