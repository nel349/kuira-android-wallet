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
    private val dustActionsBuilder: DustActionsBuilder? = null,
    private val dustRepository: com.midnight.kuira.core.indexer.repository.DustRepository? = null
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

        // Validate DustRepository is available
        checkNotNull(dustRepository) {
            "DustRepository required for dust fee payment. " +
            "Initialize TransactionSubmitter with DustRepository."
        }

        // Step 1: Serialize base transaction (without dust)
        val baseSerializedHex = try {
            serializer.serialize(signedIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Serialization failed", e)
            return SubmissionResult.Failed(
                txHash = null,
                reason = "Serialization failed: ${e.message}"
            )
        }

        Log.d(TAG, "Base transaction serialized: ${baseSerializedHex.length} hex chars")
        Log.d(TAG, "Ledger params: ${ledgerParamsHex.length} hex chars")

        // Step 2: Prove and seal base transaction for fee calculation
        Log.d(TAG, "Proving base transaction for fee calculation...")
        val baseProvenHex = try {
            proofServerClient.proveTransaction(baseSerializedHex)
        } catch (e: ProofServerException) {
            Log.e(TAG, "Failed to prove base transaction", e)
            return SubmissionResult.Failed(
                txHash = null,
                reason = "Proof generation failed: ${e.message}"
            )
        }

        Log.d(TAG, "Sealing base transaction for fee calculation...")
        val ffiSerializer = serializer as? com.midnight.kuira.core.ledger.api.FfiTransactionSerializer
            ?: throw IllegalStateException("FFI serializer required")

        val baseSealedHex = ffiSerializer.sealProvenTransaction(baseProvenHex)
            ?: throw IllegalStateException("Sealing failed")

        Log.d(TAG, "Base transaction sealed: ${baseSealedHex.length} hex chars")

        // Step 3: Calculate fee on sealed transaction
        Log.d(TAG, "Calculating transaction fee...")
        val dustActions = try {
            dustActionsBuilder.buildDustActions(
                transactionHex = baseSealedHex,  // Use SEALED transaction
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
            Log.e(TAG, "Insufficient dust balance (required: ${dustActions?.totalFee} Specks)")
            return SubmissionResult.Failed(
                txHash = null,
                reason = "Insufficient dust balance to cover fee (required: ${dustActions?.totalFee} Specks)"
            )
        }

        Log.d(TAG, "Dust actions built: ${dustActions.utxoIndices.size} UTXOs, fee=${dustActions.totalFee} Specks")

        // Step 3: Load DustLocalState
        val dustState = dustRepository.loadState(fromAddress)
        if (dustState == null) {
            Log.e(TAG, "Failed to load dust state for $fromAddress")
            return SubmissionResult.Failed(
                txHash = null,
                reason = "Failed to load dust state"
            )
        }

        try {
            // Step 4: Build JSON for selected dust UTXOs
            val dustUtxosJson = buildDustUtxosJson(dustActions.utxoIndices, dustActions.totalFee)
            Log.d(TAG, "Dust UTXOs JSON: $dustUtxosJson")

            // Step 5: Extract transaction components from signed intent
            val offer = signedIntent.guaranteedUnshieldedOffer!!
            val inputs = offer.inputs
            val outputs = offer.outputs
            val signatures = offer.signatures

            // Step 6: Serialize WITH dust (calls state.spend() internally in Rust FFI)
            Log.d(TAG, "Serializing transaction with dust fees...")
            val ffiSerializer = serializer as? com.midnight.kuira.core.ledger.api.FfiTransactionSerializer
                ?: throw IllegalStateException("FFI serializer required for dust fee payment")

            // IMPORTANT: Do NOT call getSigningMessageForInput() again here!
            // The binding_randomness from the original signing (when user signed the transaction)
            // is already stored in the FFI layer. Calling getSigningMessageForInput() would
            // generate a NEW random binding_randomness, which would cause signature verification
            // to fail because the signature was created for the ORIGINAL binding_randomness.
            //
            // The stored binding_randomness will be automatically used by serializeWithDust().

            val unprovenTxHex = ffiSerializer.serializeWithDust(
                inputs = inputs,
                outputs = outputs,
                signatures = signatures,
                dustState = dustState,
                seed = seed,
                dustUtxosJson = dustUtxosJson,
                ttl = signedIntent.ttl
            )

            Log.d(TAG, "Transaction with dust serialized (unproven): ${unprovenTxHex.length} hex chars")

            // Step 7: Save updated dust state
            dustRepository.saveState(fromAddress, dustState)
            Log.d(TAG, "Dust state saved")

            // Step 8: Prove transaction
            Log.d(TAG, "🔐 Proving transaction via proof server...")
            val provenTxHex = try {
                proofServerClient.proveTransaction(unprovenTxHex)
            } catch (e: ProofServerException) {
                Log.e(TAG, "❌ Proof server error", e)
                // Rollback dust actions on proving failure
                dustActionsBuilder.rollbackDustActions(dustActions)
                return SubmissionResult.Failed(
                    txHash = null,
                    reason = "Proof generation failed: ${e.message}"
                )
            }

            Log.d(TAG, "✅ Transaction proven: ${provenTxHex.length} hex chars")

            // Step 9: Seal proven transaction
            Log.d(TAG, "🔐 Sealing proven transaction...")
            val sealedTxHex = ffiSerializer.sealProvenTransaction(provenTxHex)
                ?: throw IllegalStateException("Sealing returned null")

            Log.d(TAG, "✅ Transaction sealed: ${sealedTxHex.length} hex chars")

            // Step 10: Submit to node
            val txHash = try {
                nodeRpcClient.submitTransaction(sealedTxHex)
            } catch (e: TransactionRejected) {
                // Rollback dust actions on node rejection
                dustActionsBuilder.rollbackDustActions(dustActions)
                return SubmissionResult.Failed(
                    txHash = e.txHash,
                    reason = "Transaction rejected by node: ${e.reason}"
                )
            } catch (e: NodeRpcException) {
                // Rollback dust actions on RPC error
                dustActionsBuilder.rollbackDustActions(dustActions)
                return SubmissionResult.Failed(
                    txHash = null,
                    reason = "Node RPC error: ${e.message}"
                )
            }

            Log.d(TAG, "✅ Transaction submitted: $txHash")

            // Step 11: Wait for confirmation
            val result = try {
                withTimeout(timeoutMs) {
                    waitForConfirmation(fromAddress, txHash)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // Timeout - transaction may still be pending
                // Don't rollback dust actions yet (might still confirm)
                SubmissionResult.Pending(
                    txHash = txHash,
                    reason = "Confirmation timeout after ${timeoutMs}ms (transaction may still be processing)"
                )
            }

            // Step 12: Update dust coin state based on final result
            when (result) {
                is SubmissionResult.Success -> {
                    // Transaction confirmed: mark coins as SPENT
                    try {
                        dustActionsBuilder.confirmDustActions(dustActions)
                        Log.d(TAG, "✅ Dust coins marked as SPENT")
                    } catch (e: Exception) {
                        // Log but don't fail - transaction already succeeded
                        Log.e(TAG, "Failed to confirm dust actions", e)
                    }
                }
                is SubmissionResult.Failed -> {
                    // Transaction failed: rollback coins to AVAILABLE
                    try {
                        dustActionsBuilder.rollbackDustActions(dustActions)
                        Log.d(TAG, "⚠️  Dust coins rolled back to AVAILABLE")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to rollback dust actions", e)
                    }
                }
                is SubmissionResult.Pending -> {
                    // Timeout: leave coins as PENDING for manual resolution
                    Log.w(TAG, "⏳ Transaction pending - dust coins left in PENDING state")
                }
            }

            return result

        } finally {
            // Always close dust state
            dustState.close()
        }
    }

    /**
     * Builds JSON array of dust UTXO selections for Rust FFI.
     *
     * Format: [{"utxo_index": 0, "v_fee": "1000000"}, ...]
     *
     * @param utxoIndices Indices of UTXOs to spend
     * @param totalFee Total fee to be paid (only first UTXO pays the fee)
     * @return JSON string for Rust FFI
     */
    private fun buildDustUtxosJson(
        utxoIndices: List<Int>,
        totalFee: java.math.BigInteger
    ): String {
        val utxos = utxoIndices.mapIndexed { orderIndex, utxoIndex ->
            // Only first UTXO pays the full fee, others pay 0
            val vFee = if (orderIndex == 0) totalFee.toString() else "0"
            """{"utxo_index": $utxoIndex, "v_fee": "$vFee"}"""
        }
        return "[${utxos.joinToString(",")}]"
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
