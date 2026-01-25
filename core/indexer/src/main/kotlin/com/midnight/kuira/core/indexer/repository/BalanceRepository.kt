package com.midnight.kuira.core.indexer.repository

import com.midnight.kuira.core.indexer.model.TokenBalance
import com.midnight.kuira.core.indexer.model.TokenTypeMapper
import com.midnight.kuira.core.indexer.utxo.UtxoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for aggregating and exposing token balances to the UI layer.
 *
 * **Responsibilities:**
 * - Transform UTXO manager data to TokenBalance models
 * - Expose Flow<List<TokenBalance>> for UI consumption
 * - Calculate token balances from available UTXOs
 *
 * **Data Flow:**
 * ```
 * Database → UtxoManager → BalanceRepository → ViewModel → UI
 *    (UTXOs)   (Sum by type)  (Add metadata)   (Transform)  (Display)
 * ```
 *
 * **Example Usage:**
 * ```kotlin
 * @Inject lateinit var repository: BalanceRepository
 *
 * repository.observeBalances(address)
 *     .collect { balances ->
 *         balances.forEach { balance ->
 *             println("${balance.tokenType}: ${balance.balance} (${balance.utxoCount} UTXOs)")
 *         }
 *     }
 * ```
 */
@Singleton
class BalanceRepository @Inject constructor(
    private val utxoManager: UtxoManager,
    private val indexerClient: com.midnight.kuira.core.indexer.api.IndexerClient
) {

    /**
     * Observe available token balances for a specific address.
     *
     * Emits a new list whenever:
     * - New transactions are received (UTXOs created/spent)
     * - Balance calculations change
     * - UTXO states change (pending → available → spent)
     *
     * Only includes AVAILABLE UTXOs (excludes PENDING and SPENT).
     *
     * Matches Midnight SDK: `getAvailableBalances()`
     *
     * @param address The unshielded address to track
     * @return Flow of token balances (DUST, TNIGHT, etc.) sorted by balance (largest first)
     */
    fun observeBalances(address: String): Flow<List<TokenBalance>> {
        return combine(
            utxoManager.observeBalance(address),
            utxoManager.observeUtxoCounts(address)
        ) { balanceMap, utxoCounts ->
            // Convert Map<String, BigInteger> to List<TokenBalance> with actual UTXO counts
            balanceMap.entries.map { entry ->
                TokenBalance(
                    tokenType = TokenTypeMapper.toDisplaySymbol(entry.key),
                    balance = entry.value,
                    utxoCount = utxoCounts[entry.key] ?: 0
                )
            }
            .sortedByDescending { it.balance } // Show largest balances first
        }
    }

    /**
     * Observe pending token balances for a specific address.
     *
     * Returns balances for UTXOs locked in pending transactions.
     * These are UTXOs in PENDING state (transaction submitted but not yet confirmed).
     *
     * Matches Midnight SDK: `getPendingBalances()`
     *
     * @param address The unshielded address to track
     * @return Flow of pending token balances sorted by balance (largest first)
     */
    fun observePendingBalances(address: String): Flow<List<TokenBalance>> {
        return utxoManager.observePendingBalance(address)
            .map { balanceMap ->
                balanceMap.entries.map { entry ->
                    TokenBalance(
                        tokenType = TokenTypeMapper.toDisplaySymbol(entry.key),
                        balance = entry.value,
                        utxoCount = 0  // Pending UTXO counts less important for UI
                    )
                }
                .sortedByDescending { it.balance }
            }
    }

    /**
     * Observe balance for a specific token type.
     *
     * @param address The unshielded address to track
     * @param tokenType The token type (e.g., "DUST", "TNIGHT")
     * @return Flow of token balance (or null if no UTXOs for this token)
     */
    fun observeTokenBalance(address: String, tokenType: String): Flow<TokenBalance?> {
        return observeBalances(address)
            .map { balances ->
                balances.find { it.tokenType == tokenType }
            }
    }

    /**
     * Observe total balance across all token types (available + pending).
     *
     * **Warning:** This sums different token types together - only useful for UI "total value"
     * where tokens are assumed to have equal value or for displaying aggregate statistics.
     * Do not use for financial calculations.
     *
     * Uses BigInteger to prevent overflow with large balances.
     *
     * Matches Midnight SDK: `getTotalBalances()` (sum of available + pending)
     *
     * @param address The unshielded address to track
     * @return Flow of total balance amount across all tokens as BigInteger
     */
    fun observeTotalBalance(address: String): Flow<BigInteger> {
        return combine(
            observeBalances(address),
            observePendingBalances(address)
        ) { availableBalances, pendingBalances ->
            // Sum available balances
            val availableTotal = availableBalances.fold(BigInteger.ZERO) { acc, balance ->
                acc.add(balance.balance)
            }

            // Sum pending balances
            val pendingTotal = pendingBalances.fold(BigInteger.ZERO) { acc, balance ->
                acc.add(balance.balance)
            }

            // Total = available + pending (matches Midnight SDK)
            availableTotal.add(pendingTotal)
        }
    }

    /**
     * Reset the WebSocket connection to the indexer.
     *
     * Use this when switching addresses to clean up old subscriptions
     * and prevent subscription buildup.
     */
    suspend fun resetConnection() {
        indexerClient.resetConnection()
    }
}
