package com.midnight.kuira.core.ledger.api

import android.util.Log
import com.midnight.kuira.core.indexer.api.IndexerClient
import com.midnight.kuira.core.indexer.model.UnshieldedTransactionUpdate
import com.midnight.kuira.core.ledger.fee.DustActionsBuilder
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
 * **Phase 2E - Dust Fee Payment:**
 * Use `submitWithFees()` to automatically build and pay dust fees.
 *
 * **Usage Example:**
 * ```kotlin
 * val submitter = TransactionSubmitter(nodeClient, indexerClient, serializer, dustActionsBuilder)
 *
 * // With fee payment (Phase 2E):
 * val result = submitter.submitWithFees(
 *     signedIntent = intent,
 *     ledgerParamsHex = paramsHex,
 *     fromAddress = "mn_addr_sender...",
 *     seed = userSeed
 * )
 *
 * // Without fee payment (existing):
 * val result = submitter.submitAndWait(
 *     signedIntent = intent,
 *     fromAddress = "mn_addr_sender..."
 * )
 * ```
 *
 * @property nodeRpcClient Client for submitting transactions to node
 * @property proofServerClient Client for proving transactions (Phase 2)
 * @property indexerClient Client for tracking transaction confirmation
 * @property serializer Serializes signed Intent to SCALE codec
 * @property dustActionsBuilder Builder for dust fee payment actions (optional, Phase 2E)
 */
class TransactionSubmitter(
    private val nodeRpcClient: NodeRpcClient,
    private val proofServerClient: ProofServerClient,
    private val indexerClient: IndexerClient,
    private val serializer: TransactionSerializer,
    private val dustActionsBuilder: DustActionsBuilder? = null
) {

    /**
     * Submit a signed transaction and wait for finalization.
     *
     * **Steps:**
     * 1. Serialize transaction to SCALE codec (unproven)
     * 2. Prove transaction via proof server (Phase 2 - NEW)
     * 3. Seal proven transaction (transform binding commitment)
     * 4. Submit finalized transaction to node RPC
     * 5. Subscribe to indexer for confirmation
     * 6. Wait for transaction to appear (finalized)
     * 7. Return result
     *
     * **Timeout:**
     * - Default: 60 seconds (does NOT include proof server time)
     * - Proof server: ~2-10 seconds (handled separately with 5 min timeout)
     * - Typical finalization: 6-12 seconds
     * - If timeout occurs, transaction may still be pending
     *
     * @param signedIntent Signed Intent with all signatures
     * @param fromAddress Sender's address (for tracking via indexer)
     * @param timeoutMs Maximum time to wait for finalization (default 60s)
     * @return SubmissionResult indicating success or failure
     * @throws NodeRpcException if submission to node fails
     * @throws ProofServerException if proving fails
     * @throws kotlinx.coroutines.TimeoutCancellationException if confirmation times out
     */
    suspend fun submitAndWait(
        signedIntent: Intent,
        fromAddress: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): SubmissionResult {
        // Step 1: Serialize unproven transaction
        val unprovenTxHex = try {
            serializer.serialize(signedIntent)
        } catch (e: Exception) {
            return SubmissionResult.Failed(
                txHash = null,
                reason = "Serialization failed: ${e.message}"
            )
        }

        Log.d(TAG, "Transaction serialized (unproven): ${unprovenTxHex.length} hex chars")
        Log.d(TAG, "Tag should contain: proof-preimage, pedersen[v1]")

        // Step 2: Prove transaction via proof server (Phase 2 - NEW!)
        val provenTxHex = try {
            Log.d(TAG, "🔐 Proving transaction via proof server...")
            proofServerClient.proveTransaction(unprovenTxHex)
        } catch (e: ProofServerException) {
            Log.e(TAG, "❌ Proof server error", e)
            return SubmissionResult.Failed(
                txHash = null,
                reason = "Proof generation failed: ${e.message}"
            )
        }

        Log.d(TAG, "✅ Transaction proven: ${provenTxHex.length} hex chars")
        Log.d(TAG, "Tag contains: proof, embedded-fr[v1] (not yet sealed)")

        // Step 2.5: Seal the proven transaction (transform binding commitment)
        val finalizedTxHex = try {
            Log.d(TAG, "🔐 Sealing proven transaction...")
            serializer.sealProvenTransaction(provenTxHex)
                ?: throw IllegalStateException("Sealing returned null")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Sealing error", e)
            return SubmissionResult.Failed(
                txHash = null,
                reason = "Transaction sealing failed: ${e.message}"
            )
        }

        Log.d(TAG, "✅ Transaction finalized: ${finalizedTxHex.length} hex chars")
        Log.d(TAG, "Tag should now contain: proof, pedersen-schnorr[v1]")

        // Step 3: Submit finalized transaction to node
        val txHash = try {
            nodeRpcClient.submitTransaction(finalizedTxHex)
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
     * Submit transaction WITH automatic dust fee payment (Phase 2E).
     *
     * **Process:**
     * 1. Serialize transaction to calculate fee
     * 2. Build dust actions to cover fee (DustActionsBuilder)
     * 3. Add dust actions to intent
     * 4. Submit transaction with fees
     * 5. Wait for confirmation
     * 6. On success: mark dust coins as SPENT
     * 7. On failure: rollback dust coins to AVAILABLE
     *
     * **Requirements:**
     * - DustActionsBuilder must be provided in constructor
     * - User must have sufficient dust balance
     * - DustLocalState must be initialized for address
     *
     * @param signedIntent Signed Intent (without dust actions)
     * @param ledgerParamsHex SCALE-serialized ledger parameters (hex)
     * @param fromAddress Sender's address
     * @param seed 32-byte seed for deriving DustSecretKey
     * @param timeoutMs Maximum time to wait for finalization (default 60s)
     * @return SubmissionResult indicating success or failure
     * @throws IllegalStateException if DustActionsBuilder not provided
     * @throws NodeRpcException if submission to node fails
     */
    suspend fun submitWithFees(
        signedIntent: Intent,
        ledgerParamsHex: String,
        fromAddress: String,
        seed: ByteArray,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): SubmissionResult {
        // Validate DustActionsBuilder is available
        checkNotNull(dustActionsBuilder) {
            "DustActionsBuilder required for fee payment. " +
            "Initialize TransactionSubmitter with DustActionsBuilder."
        }

        // Step 1: Serialize transaction to calculate fee
        val serializedHex = try {
            serializer.serialize(signedIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Serialization failed", e)
            return SubmissionResult.Failed(
                txHash = null,
                reason = "Serialization failed: ${e.message}"
            )
        }

        Log.d(TAG, "Serialized transaction: ${serializedHex.length} hex chars")
        Log.d(TAG, "Ledger params: ${ledgerParamsHex.length} hex chars")
        Log.d(TAG, "Transaction hex prefix: ${serializedHex.take(100)}")

        // Step 2: Build dust actions for fee payment
        val dustActions = try {
            dustActionsBuilder.buildDustActions(
                transactionHex = serializedHex,
                ledgerParamsHex = ledgerParamsHex,
                address = fromAddress,
                seed = seed
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build dust actions", e)
            return SubmissionResult.Failed(
                txHash = null,
                reason = "Failed to build dust actions: ${e.message}"
            )
        }

        if (dustActions == null || !dustActions.isSuccess()) {
            return SubmissionResult.Failed(
                txHash = null,
                reason = "Insufficient dust balance to cover fee (required: ${dustActions?.totalFee} Specks)"
            )
        }

        // Step 3: Add dust actions to intent
        // TODO: Implement Intent.addDustActions() method
        // val intentWithFees = signedIntent.addDustActions(dustActions.spends)

        // Step 4: Submit transaction
        // TODO: Use intentWithFees instead of signedIntent
        val result = submitAndWait(
            signedIntent = signedIntent, // TODO: Replace with intentWithFees
            fromAddress = fromAddress,
            timeoutMs = timeoutMs
        )

        // Step 5: Update dust coin state based on result
        when (result) {
            is SubmissionResult.Success -> {
                // Transaction confirmed: mark coins as SPENT
                try {
                    dustActionsBuilder.confirmDustActions(dustActions)
                } catch (e: Exception) {
                    // Log but don't fail - transaction already succeeded
                    android.util.Log.e("TransactionSubmitter", "Failed to confirm dust actions", e)
                }
            }
            is SubmissionResult.Failed, is SubmissionResult.Pending -> {
                // Transaction failed or timeout: rollback coins to AVAILABLE
                try {
                    dustActionsBuilder.rollbackDustActions(dustActions)
                } catch (e: Exception) {
                    android.util.Log.e("TransactionSubmitter", "Failed to rollback dust actions", e)
                }
            }
        }

        return result
    }

    /**
     * Submit transaction without waiting for confirmation (fire-and-forget).
     *
     * **Use case:** When you don't need to wait for finalization
     *
     * **Steps:**
     * 1. Serialize unproven transaction
     * 2. Prove via proof server (Phase 2 - NEW)
     * 3. Seal proven transaction (transform binding commitment)
     * 4. Submit finalized transaction to node
     *
     * @param signedIntent Signed Intent
     * @return Transaction hash on success
     * @throws NodeRpcException on submission failure
     * @throws ProofServerException if proving fails
     */
    suspend fun submitOnly(signedIntent: Intent): String {
        // Step 1: Serialize unproven transaction
        val unprovenTxHex = serializer.serialize(signedIntent)

        // Step 2: Prove transaction
        val provenTxHex = proofServerClient.proveTransaction(unprovenTxHex)

        // Step 2.5: Seal the proven transaction
        val finalizedTxHex = serializer.sealProvenTransaction(provenTxHex)
            ?: throw IllegalStateException("Sealing returned null")

        // Step 3: Submit finalized transaction
        return nodeRpcClient.submitTransaction(finalizedTxHex)
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
        private const val TAG = "TransactionSubmitter"

        /**
         * Default timeout for waiting for transaction finalization: 60 seconds.
         *
         * Typical finalization time is 6-12 seconds, so 60s provides margin.
         */
        const val DEFAULT_TIMEOUT_MS = 60_000L
    }
}
