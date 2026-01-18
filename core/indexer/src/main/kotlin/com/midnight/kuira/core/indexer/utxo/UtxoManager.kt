package com.midnight.kuira.core.indexer.utxo

import com.midnight.kuira.core.indexer.database.UnshieldedUtxoDao
import com.midnight.kuira.core.indexer.database.UnshieldedUtxoEntity
import com.midnight.kuira.core.indexer.database.UtxoState
import com.midnight.kuira.core.indexer.model.TransactionStatus
import com.midnight.kuira.core.indexer.model.UnshieldedTransactionUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger

/**
 * Manages UTXO state from transaction updates.
 *
 * Handles:
 * - Processing transaction updates (created/spent UTXOs)
 * - Three-state UTXO lifecycle (AVAILABLE → PENDING → SPENT)
 * - Transaction failure handling (unlock UTXOs)
 * - Calculating balances from available UTXOs only
 *
 * **Transaction Status Handling:**
 * - SUCCESS/PARTIAL_SUCCESS: Create UTXOs (AVAILABLE), mark spent as SPENT
 * - FAILURE: Don't create UTXOs, unlock spent UTXOs (PENDING → AVAILABLE)
 *
 * Thread-safe: All operations are suspend functions using Room's built-in thread safety.
 */
class UtxoManager(
    private val utxoDao: UnshieldedUtxoDao
) {
    /**
     * Process transaction update from subscription.
     *
     * Handles both transaction updates (with UTXO changes) and progress updates.
     *
     * For transaction updates (based on status):
     * - SUCCESS/PARTIAL_SUCCESS:
     *   1. Insert created UTXOs (state = AVAILABLE)
     *   2. Mark spent UTXOs as SPENT (permanent)
     * - FAILURE:
     *   1. Don't insert created UTXOs (never created on-chain)
     *   2. Unlock spent UTXOs (PENDING → AVAILABLE) so they can be reused
     *
     * For progress updates:
     * - Just return (nothing to do with UTXOs)
     *
     * @param update Update from subscription
     * @return ProcessingResult indicating what was done
     */
    suspend fun processUpdate(update: UnshieldedTransactionUpdate): ProcessingResult {
        return when (update) {
            is UnshieldedTransactionUpdate.Transaction -> {
                processTransaction(update)
            }
            is UnshieldedTransactionUpdate.Progress -> {
                ProcessingResult.ProgressUpdate(update.highestTransactionId)
            }
        }
    }

    private suspend fun processTransaction(
        update: UnshieldedTransactionUpdate.Transaction
    ): ProcessingResult {
        val createdCount = update.createdUtxos.size
        val spentCount = update.spentUtxos.size
        val status = update.status()

        // Handle based on transaction status
        when (status) {
            TransactionStatus.SUCCESS, TransactionStatus.PARTIAL_SUCCESS -> {
                // Insert created UTXOs as AVAILABLE
                if (createdCount > 0) {
                    val entities = update.createdUtxos.map { utxo ->
                        UnshieldedUtxoEntity.fromUtxo(utxo, state = UtxoState.AVAILABLE)
                    }
                    utxoDao.insertUtxos(entities)
                }

                // Mark spent UTXOs as SPENT (permanent)
                if (spentCount > 0) {
                    val utxoIds = update.spentUtxos.map { it.identifier() }
                    utxoDao.markAsSpent(utxoIds)
                }
            }

            TransactionStatus.FAILURE -> {
                // Don't create new UTXOs for failed transactions
                // (they were never actually created on-chain)

                // If there were spent UTXOs, unlock them (PENDING → AVAILABLE)
                // This allows them to be used in future transactions
                if (spentCount > 0) {
                    val utxoIds = update.spentUtxos.map { it.identifier() }
                    utxoDao.markAsAvailable(utxoIds)
                }
            }
        }

        return ProcessingResult.TransactionProcessed(
            transactionId = update.transaction.id,
            transactionHash = update.transaction.hash,
            createdCount = createdCount,
            spentCount = spentCount,
            status = status
        )
    }

    /**
     * Calculate balance for an address.
     *
     * Sums all AVAILABLE UTXOs grouped by token type.
     * Excludes PENDING (locked) and SPENT UTXOs.
     *
     * @param address Unshielded address
     * @return Map of tokenType → balance (as BigInteger)
     */
    suspend fun calculateBalance(address: String): Map<String, BigInteger> {
        val unspentUtxos = utxoDao.getUnspentUtxos(address)

        return unspentUtxos
            .groupBy { it.tokenType }
            .mapValues { (_, utxos) ->
                utxos.fold(BigInteger.ZERO) { acc, utxo ->
                    acc + BigInteger(utxo.value)
                }
            }
    }

    /**
     * Observe balance changes for an address (available UTXOs only).
     *
     * Returns Flow that emits new balance map whenever AVAILABLE UTXOs change.
     * Only includes AVAILABLE UTXOs, excludes PENDING and SPENT.
     *
     * Matches Midnight SDK: `getAvailableBalances()`
     *
     * @param address Unshielded address
     * @return Flow of balance maps (tokenType → balance)
     */
    fun observeBalance(address: String): Flow<Map<String, BigInteger>> {
        return utxoDao.observeUnspentUtxos(address).map { utxos ->
            utxos
                .groupBy { it.tokenType }
                .mapValues { (_, utxoList) ->
                    utxoList.fold(BigInteger.ZERO) { acc, utxo ->
                        acc + BigInteger(utxo.value)
                    }
                }
        }
    }

    /**
     * Observe pending balance for an address (locked in pending transactions).
     *
     * Returns Flow that emits balance map for UTXOs in PENDING state.
     * These are UTXOs locked for pending transactions.
     *
     * Matches Midnight SDK: `getPendingBalances()`
     *
     * @param address Unshielded address
     * @return Flow of pending balance maps (tokenType → balance)
     */
    fun observePendingBalance(address: String): Flow<Map<String, BigInteger>> {
        return utxoDao.observePendingUtxos(address).map { utxos ->
            utxos
                .groupBy { it.tokenType }
                .mapValues { (_, utxoList) ->
                    utxoList.fold(BigInteger.ZERO) { acc, utxo ->
                        acc + BigInteger(utxo.value)
                    }
                }
        }
    }

    /**
     * Observe UTXO counts per token type (available UTXOs only).
     *
     * Returns Flow that emits UTXO counts grouped by token type.
     * Used for displaying "X UTXOs" in balance UI.
     *
     * Matches Midnight SDK: `.length` on `getAvailableCoins()` result
     *
     * @param address Unshielded address
     * @return Flow of UTXO count maps (tokenType → count)
     */
    fun observeUtxoCounts(address: String): Flow<Map<String, Int>> {
        return utxoDao.observeUnspentUtxos(address).map { utxos ->
            utxos
                .groupBy { it.tokenType }
                .mapValues { (_, utxoList) -> utxoList.size }
        }
    }

    /**
     * Get detailed UTXO list for an address.
     *
     * Returns only AVAILABLE UTXOs (not PENDING or SPENT).
     * Useful for debugging or transaction building.
     *
     * @param address Unshielded address
     * @return List of available UTXOs
     */
    suspend fun getUnspentUtxos(address: String): List<UnshieldedUtxoEntity> {
        return utxoDao.getUnspentUtxos(address)
    }

    /**
     * Delete all UTXOs for an address.
     *
     * Used when handling deep reorgs or wallet reset.
     */
    suspend fun clearUtxos(address: String) {
        utxoDao.deleteUtxosForAddress(address)
    }

    /**
     * Result of processing a transaction update.
     */
    sealed class ProcessingResult {
        data class TransactionProcessed(
            val transactionId: Int,
            val transactionHash: String,
            val createdCount: Int,
            val spentCount: Int,
            val status: com.midnight.kuira.core.indexer.model.TransactionStatus
        ) : ProcessingResult()

        data class ProgressUpdate(
            val highestTransactionId: Int
        ) : ProcessingResult()
    }
}
