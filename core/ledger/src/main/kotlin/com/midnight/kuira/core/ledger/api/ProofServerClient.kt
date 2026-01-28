package com.midnight.kuira.core.ledger.api

/**
 * Client for Midnight Proof Server API.
 *
 * Converts unproven transactions into proven transactions by communicating
 * with the Midnight proof server, which computes zero-knowledge proofs and
 * seals binding commitments.
 *
 * **Transaction Evolution:**
 * ```
 * INPUT:  Transaction<Signature, ProofPreimageMarker, Pedersen>
 *         Tag: midnight:transaction[v6](signature[v1],proof-preimage,pedersen[v1])
 *
 * OUTPUT: Transaction<Signature, Proof, PureGeneratorPedersen>
 *         Tag: midnight:transaction[v6](signature[v1],proof,pedersen-schnorr[v1])
 * ```
 *
 * **Proof Server Endpoints:**
 * - `/prove-tx` (Legacy): Prove complete transaction (binary POST)
 * - `/prove`: Prove individual preimages (binary POST)
 * - `/check`: Validate proofs without generating (binary POST)
 * - `/health`: Health check (GET)
 *
 * **Protocol:**
 * - Request: Binary SCALE-encoded unproven transaction
 * - Response: Binary SCALE-encoded proven transaction
 * - Content-Type: application/octet-stream
 * - Timeout: 300 seconds (5 minutes) - proof generation is slow
 *
 * **Error Handling:**
 * - Client errors (400-499): Don't retry
 * - Server errors (502-504): Retry with exponential backoff
 * - Timeout: Show clear error to user
 *
 * @see ProofServerClientImpl for implementation details
 */
interface ProofServerClient {

    /**
     * Prove an unproven transaction.
     *
     * **Process:**
     * 1. Deserialize binary unproven transaction
     * 2. Compute zero-knowledge proofs for all proof obligations
     * 3. Seal Pedersen commitments to PureGeneratorPedersen (Schnorr form)
     * 4. Serialize proven transaction
     *
     * **Input:** Unproven transaction (serialized as SCALE binary)
     * - Type: `Transaction<Signature, ProofPreimageMarker, Pedersen>`
     * - Tag: `midnight:transaction[v6](signature[v1],proof-preimage,pedersen[v1])`
     *
     * **Output:** Proven transaction (serialized as SCALE binary)
     * - Type: `Transaction<Signature, Proof, PureGeneratorPedersen>`
     * - Tag: `midnight:transaction[v6](signature[v1],proof,pedersen-schnorr[v1])`
     *
     * **Performance:**
     * - Simple unshielded transfer: 2-10 seconds (typically fast)
     * - Complex shielded transaction: 30 seconds - 5 minutes (can be slow)
     * - Server overload: May timeout or return 502-504
     *
     * @param unprovenTxHex Hex-encoded unproven transaction (without "0x" prefix)
     * @return Hex-encoded proven transaction (without "0x" prefix)
     * @throws ProofServerNetworkException if network connectivity fails
     * @throws ProofServerHttpException if HTTP request fails
     * @throws ProofServerTimeoutException if request times out (> 5 minutes)
     * @throws ProofServerError if proof server returns error
     * @throws ProofComputationException if proof generation fails
     * @throws ProofServerInvalidResponseException if response is malformed
     */
    suspend fun proveTransaction(unprovenTxHex: String): String

    /**
     * Check if proof server is healthy and reachable.
     *
     * Performs a lightweight GET request to /health endpoint.
     *
     * @return true if proof server is responding, false otherwise
     */
    suspend fun isHealthy(): Boolean

    /**
     * Close the client and release resources.
     */
    fun close()
}
