package com.midnight.kuira.core.indexer.sync

import android.util.Log
import com.midnight.kuira.core.indexer.api.IndexerClient
import com.midnight.kuira.core.indexer.model.UnshieldedTransactionUpdate
import com.midnight.kuira.core.indexer.utxo.UtxoManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.min
import kotlin.math.pow

/**
 * Manages subscription lifecycle for UTXO syncing.
 *
 * **This is the missing piece that connects everything:**
 * - Starts subscription via IndexerClient
 * - Collects updates from subscription Flow
 * - Passes updates to UtxoManager for processing
 * - Persists sync progress via SyncStateManager
 * - Handles reconnection with exponential backoff
 *
 * **Why this was missing:**
 * All the pieces existed (IndexerClient, UtxoManager, WebSocket client),
 * but nothing ever called `subscribeToUnshieldedTransactions()` and collected from it.
 * This class fixes that critical gap.
 *
 * **Usage:**
 * ```kotlin
 * // In ViewModel or background service
 * val subscriptionManager = SubscriptionManager(indexerClient, utxoManager, syncStateManager)
 *
 * // Start syncing (will resume from last processed transaction)
 * viewModelScope.launch {
 *     subscriptionManager.startSubscription(address)
 *         .collect { state ->
 *             when (state) {
 *                 is SyncState.Syncing -> updateUI("Syncing: ${state.processedCount} txs")
 *                 is SyncState.Synced -> updateUI("Synced up to block ${state.blockHeight}")
 *                 is SyncState.Error -> showError(state.message)
 *             }
 *         }
 * }
 * ```
 */
class SubscriptionManager(
    private val indexerClient: IndexerClient,
    private val utxoManager: UtxoManager,
    private val syncStateManager: SyncStateManager
) {
    companion object {
        private const val TAG = "SubscriptionManager"
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 32000L

        // Throttle DataStore writes to reduce battery drain and disk I/O
        // Only save progress if this many milliseconds have passed since last save
        private const val PROGRESS_SAVE_THROTTLE_MS = 5000L // 5 seconds

        // Auto-sync timeout: If no new transactions arrive for this duration,
        // assume we're synced (don't wait for server's slow Progress updates)
        private const val SYNC_TIMEOUT_MS = 5000L // 5 seconds
    }

    // Track last save time per address for throttling
    private val lastSaveTimestamps = mutableMapOf<String, Long>()

    /**
     * Start subscription for an address with automatic reconnection.
     *
     * **Flow:**
     * 1. Get last processed transaction ID from SyncStateManager
     * 2. Subscribe from that ID (null = start from beginning)
     * 3. Process each update via UtxoManager
     * 4. Save progress after each Progress update
     * 5. On error: retry with exponential backoff
     *
     * **Resumption:**
     * - First sync: fromTransactionId = null (replay all history)
     * - Subsequent syncs: fromTransactionId = last saved ID (skip already processed)
     *
     * **Reconnection:**
     * - Network errors: retry with exponential backoff (1s, 2s, 4s, 8s, 16s, 32s max)
     * - After max retries: emit error state and stop (caller must restart subscription)
     * - Retryable errors: IOException, connection failures, WebSocket errors
     * - Non-retryable errors: CancellationException (user cancelled)
     *
     * @param address Unshielded address to sync
     * @return Flow of sync states (Syncing, Synced, Error)
     */
    fun startSubscription(address: String): Flow<SyncState> {
        return createSubscriptionFlow(address)
            .retryWhen { cause, attempt ->
                // Retry all errors except cancellation (user-initiated stop)
                when {
                    cause is CancellationException -> {
                        // User cancelled - don't retry
                        Log.d(TAG, "Subscription cancelled by user")
                        false
                    }
                    attempt < MAX_RETRY_ATTEMPTS -> {
                        // Network error or connection failure - retry with exponential backoff
                        // Catches: IOException, WebSocket errors, connection timeouts, etc.
                        val delayMs = calculateRetryDelay(attempt)
                        Log.w(TAG, "Subscription error (${cause.javaClass.simpleName}), retrying in ${delayMs}ms (attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS)", cause)
                        delay(delayMs)
                        true
                    }
                    else -> {
                        // Max retries reached - stop retrying
                        Log.e(TAG, "Subscription failed after $attempt attempts: ${cause.message}", cause)
                        false
                    }
                }
            }
            .catch { error ->
                // Emit error state after retries exhausted
                Log.e(TAG, "Subscription failed permanently", error)
                emit(SyncState.Error(error.message ?: "Unknown error"))
            }
    }

    private fun createSubscriptionFlow(address: String): Flow<SyncState> = channelFlow {
        var processedCount = 0
        var latestTransactionId: Int? = null // Track latest for final save
        var syncTimeoutJob: Job? = null // Job for auto-sync timeout

        // Get resume point before starting subscription
        val lastId = syncStateManager.getLastProcessedTransactionId(address)
        Log.d(TAG, "Starting subscription for $address from transaction ID: $lastId")
        send(SyncState.Connecting)

        try {
            // Collect from subscription and map to sync states
            indexerClient.subscribeToUnshieldedTransactions(
                address = address,
                transactionId = lastId
            )
                .onCompletion { error ->
                    if (error == null) {
                        Log.d(TAG, "Subscription completed normally (processed $processedCount transactions)")
                    } else {
                        Log.e(TAG, "Subscription completed with error", error)
                    }
                    syncTimeoutJob?.cancel() // Clean up timeout job
                }
                .catch { error ->
                    Log.e(TAG, "Error in subscription", error)
                    syncTimeoutJob?.cancel() // Clean up timeout job
                    throw error // Re-throw for retryWhen to handle
                }
                .collect { update ->
                // Process update
                val result = utxoManager.processUpdate(update)

                when (result) {
                    is UtxoManager.ProcessingResult.TransactionProcessed -> {
                        processedCount++
                        latestTransactionId = result.transactionId
                        Log.d(TAG, "Processed tx ${result.transactionHash}: " +
                                "+${result.createdCount} -${result.spentCount} (${result.status})")

                        // Emit syncing state
                        send(SyncState.Syncing(processedCount, result.transactionId))

                        // Cancel previous timeout job (new transaction arrived)
                        syncTimeoutJob?.cancel()

                        // Start new timeout: if no transactions arrive for SYNC_TIMEOUT_MS,
                        // assume we're synced (don't wait for server's slow Progress updates)
                        syncTimeoutJob = launch {
                            delay(SYNC_TIMEOUT_MS)
                            Log.d(TAG, "No new transactions for ${SYNC_TIMEOUT_MS}ms, marking as synced")
                            send(SyncState.Synced(result.transactionId))
                        }
                    }
                    is UtxoManager.ProcessingResult.ProgressUpdate -> {
                        // Track latest transaction ID for final save
                        latestTransactionId = result.highestTransactionId

                        // Save sync progress (throttled to reduce battery drain)
                        val now = System.currentTimeMillis()
                        val lastSave = lastSaveTimestamps[address] ?: 0L
                        val shouldSave = (now - lastSave) >= PROGRESS_SAVE_THROTTLE_MS

                        if (shouldSave) {
                            syncStateManager.saveLastProcessedTransactionId(address, result.highestTransactionId)
                            lastSaveTimestamps[address] = now
                            Log.d(TAG, "Progress saved: highest transaction ID = ${result.highestTransactionId}")
                        } else {
                            Log.d(TAG, "Progress: highest transaction ID = ${result.highestTransactionId} (not saved - throttled)")
                        }

                        // Emit Synced state when Progress update received
                        // (This is backup in case timeout didn't fire)
                        send(SyncState.Synced(result.highestTransactionId))
                    }
                }
            }
        } finally {
            // Clean up timeout job
            syncTimeoutJob?.cancel()

            // Save final progress on completion/cancellation (if we have any)
            latestTransactionId?.let { txId ->
                syncStateManager.saveLastProcessedTransactionId(address, txId)
                lastSaveTimestamps.remove(address) // Clean up
                Log.d(TAG, "Final progress saved on completion: $txId")
            }
        }
    }

    /**
     * Calculate retry delay with exponential backoff.
     *
     * Formula: min(INITIAL_DELAY * 2^attempt, MAX_DELAY)
     * Example: 1s, 2s, 4s, 8s, 16s, 32s (max)
     */
    private fun calculateRetryDelay(attempt: Long): Long {
        val exponentialDelay = INITIAL_RETRY_DELAY_MS * 2.0.pow(attempt.toDouble()).toLong()
        return min(exponentialDelay, MAX_RETRY_DELAY_MS)
    }
}

/**
 * Sync state for UI/logging.
 */
sealed class SyncState {
    /**
     * Connecting to indexer.
     */
    object Connecting : SyncState()

    /**
     * Syncing transactions.
     *
     * @param processedCount Number of transactions processed so far
     * @param currentTransactionId Current transaction being processed
     */
    data class Syncing(
        val processedCount: Int,
        val currentTransactionId: Int
    ) : SyncState()

    /**
     * Synced up to highest available transaction.
     *
     * @param highestTransactionId Highest transaction ID available
     */
    data class Synced(
        val highestTransactionId: Int
    ) : SyncState()

    /**
     * Sync error (after retries exhausted).
     *
     * @param message Error message
     */
    data class Error(val message: String) : SyncState()
}
