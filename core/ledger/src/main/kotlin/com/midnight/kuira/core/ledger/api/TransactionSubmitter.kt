package com.midnight.kuira.core.ledger.api

import com.midnight.kuira.core.indexer.api.IndexerClient
import com.midnight.kuira.core.indexer.model.UnshieldedTransactionUpdate
import com.midnight.kuira.core.ledger.model.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout

/**
 * Orchestrates transaction submission and confirmation for Midnight blockchain.
 *
 * **Process:**
 * 1. Serialize signed transaction (SCALE codec)
 * 2. Submit to node via RPC (`author_submitExtrinsic`)
 * 3. Track confirmation via Indexer GraphQL subscription
 * 4. Return when finalized (6-12 seconds typically)
 *
 * **Usage Example:**
 * ```kotlin
 * val submitter = TransactionSubmitter(nodeClient, indexerClient, serializer)
 *
 * val result = submitter.submitAndWait(
 *     signedIntent = intent,
 *     fromAddress = "mn_addr_sender..."
 * )
 *
 * when (result) {
 *     is SubmissionResult.Success -> {
 *         println("Transaction finalized: ${result.txHash}")
 *     }
 *     is SubmissionResult.Failed -> {
 *         println("Transaction failed: ${result.reason}")
 *     }
 * }
 * ```
 *
 * @property nodeRpcClient Client for submitting transactions to node
 * @property indexerClient Client for tracking transaction confirmation
 * @property serializer Serializes signed Intent to SCALE codec
 */
class TransactionSubmitter(
    private val nodeRpcClient: NodeRpcClient,
    private val indexerClient: IndexerClient,
    private val serializer: TransactionSerializer
) {

    /**
     * Submit a signed transaction and wait for finalization.
     *
     * **Steps:**
     * 1. Serialize transaction to SCALE codec
     * 2. Submit to node RPC
     * 3. Subscribe to indexer for confirmation
     * 4. Wait for transaction to appear (finalized)
     * 5. Return result
     *
     * **Timeout:**
     * - Default: 60 seconds
     * - Typical finalization: 6-12 seconds
     * - If timeout occurs, transaction may still be pending
     *
     * @param signedIntent Signed Intent with all signatures
     * @param fromAddress Sender's address (for tracking via indexer)
     * @param timeoutMs Maximum time to wait for finalization (default 60s)
     * @return SubmissionResult indicating success or failure
     * @throws NodeRpcException if submission to node fails
     * @throws kotlinx.coroutines.TimeoutCancellationException if confirmation times out
     */
    suspend fun submitAndWait(
        signedIntent: Intent,
        fromAddress: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): SubmissionResult {
        // Step 1: Serialize transaction
        val serializedHex = try {
            serializer.serialize(signedIntent)
        } catch (e: Exception) {
            return SubmissionResult.Failed(
                txHash = null,
                reason = "Serialization failed: ${e.message}"
            )
        }

        // Step 2: Submit to node
        val txHash = try {
            nodeRpcClient.submitTransaction(serializedHex)
        } catch (e: TransactionRejected) {
            return SubmissionResult.Failed(
                txHash = e.txHash,
                reason = "Transaction rejected by node: ${e.reason}"
            )
        } catch (e: NodeRpcException) {
            return SubmissionResult.Failed(
                txHash = null,
                reason = "Node RPC error: ${e.message}"
            )
        }

        // Step 3: Wait for confirmation via indexer subscription
        return try {
            withTimeout(timeoutMs) {
                waitForConfirmation(fromAddress, txHash)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            // Timeout - transaction may still be pending
            SubmissionResult.Pending(
                txHash = txHash,
                reason = "Confirmation timeout after ${timeoutMs}ms (transaction may still be processing)"
            )
        }
    }

    /**
     * Wait for transaction confirmation via indexer subscription.
     *
     * **Strategy:**
     * - Subscribe to unshielded transactions for sender address
     * - Filter for matching transaction hash
     * - Return when found
     *
     * @param address Sender's address
     * @param expectedTxHash Expected transaction hash
     * @return SubmissionResult.Success when found
     */
    private suspend fun waitForConfirmation(
        address: String,
        expectedTxHash: String
    ): SubmissionResult {
        // Subscribe to transactions for this address
        val txFlow: Flow<UnshieldedTransactionUpdate> = indexerClient
            .subscribeToUnshieldedTransactions(address)

        // Wait for our transaction to appear
        val update = txFlow
            .filter { it is UnshieldedTransactionUpdate.Transaction }
            .map { it as UnshieldedTransactionUpdate.Transaction }
            .firstOrNull { tx ->
                tx.transaction.hash.removePrefix("0x") == expectedTxHash
            }

        return if (update != null) {
            SubmissionResult.Success(
                txHash = expectedTxHash,
                blockHeight = update.transaction.id.toLong()  // Approximate block
            )
        } else {
            SubmissionResult.Failed(
                txHash = expectedTxHash,
                reason = "Transaction not found in indexer stream"
            )
        }
    }

    /**
     * Submit transaction without waiting for confirmation (fire-and-forget).
     *
     * **Use case:** When you don't need to wait for finalization
     *
     * @param signedIntent Signed Intent
     * @return Transaction hash on success
     * @throws NodeRpcException on submission failure
     */
    suspend fun submitOnly(signedIntent: Intent): String {
        val serializedHex = serializer.serialize(signedIntent)
        return nodeRpcClient.submitTransaction(serializedHex)
    }

    /**
     * Result of transaction submission.
     */
    sealed class SubmissionResult {
        /**
         * Transaction successfully submitted and finalized.
         *
         * @property txHash Transaction hash (32 bytes hex)
         * @property blockHeight Approximate block height where tx was included
         */
        data class Success(
            val txHash: String,
            val blockHeight: Long
        ) : SubmissionResult()

        /**
         * Transaction failed (rejected or error).
         *
         * @property txHash Transaction hash (null if never submitted)
         * @property reason Human-readable failure reason
         */
        data class Failed(
            val txHash: String?,
            val reason: String
        ) : SubmissionResult()

        /**
         * Transaction submitted but confirmation timed out.
         *
         * **Note:** Transaction may still be processing - check indexer manually.
         *
         * @property txHash Transaction hash
         * @property reason Timeout reason
         */
        data class Pending(
            val txHash: String,
            val reason: String
        ) : SubmissionResult()
    }

    companion object {
        /**
         * Default timeout for waiting for transaction finalization: 60 seconds.
         *
         * Typical finalization time is 6-12 seconds, so 60s provides margin.
         */
        const val DEFAULT_TIMEOUT_MS = 60_000L
    }
}
