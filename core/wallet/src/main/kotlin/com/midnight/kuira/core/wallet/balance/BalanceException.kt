package com.midnight.kuira.core.wallet.balance

/**
 * Exception thrown when balance calculation detects an invalid state.
 */
sealed class BalanceException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when balance would go negative (double-spend or invalid event).
 *
 * **Why This Matters:**
 * - Negative balance indicates double-spend attack or corrupted event stream
 * - MUST NOT silently set to zero (masks critical errors)
 * - Wallet MUST stop processing and alert user
 *
 * **Recovery:**
 * - Clear event cache
 * - Re-sync from blockchain
 * - If persists, report to indexer operator (potential attack)
 */
class BalanceUnderflowException(message: String) : BalanceException(message)

/**
 * Exception thrown when balance arithmetic overflows (extremely large balances).
 *
 * **Why This Matters:**
 * - Integer overflow can wrap to negative values
 * - Attacker could manipulate balance calculations
 *
 * **Recovery:**
 * - Use BigInteger everywhere (already done)
 * - This is a safety net for unexpected edge cases
 */
class BalanceOverflowException(message: String) : BalanceException(message)
