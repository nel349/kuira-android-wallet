package com.midnight.kuira.core.ledger.api

/**
 * Client for Midnight node JSON-RPC API.
 *
 * Submits transactions to the Midnight blockchain via the node's RPC interface.
 *
 * **JSON-RPC 2.0 Protocol:**
 * - Endpoint: HTTP POST to node RPC (default: http://localhost:9944)
 * - Method: `author_submitExtrinsic` (standard Substrate RPC)
 * - Params: Hex-encoded serialized transaction (SCALE codec)
 * - Response: Transaction hash (32 bytes hex)
 *
 * **Transaction Lifecycle:**
 * ```
 * 1. Submit via RPC → Get transaction hash
 * 2. Track status via IndexerClient subscription
 * 3. Wait for finalization (typically 6-12 seconds)
 * ```
 *
 * **Error Handling:**
 * - Network errors: Retry with exponential backoff
 * - Server errors (5xx): Retry with backoff
 * - Client errors (4xx): Don't retry
 * - Transaction rejected: Don't retry (fix transaction)
 *
 * @see NodeRpcClientImpl for implementation details
 */
interface NodeRpcClient {

    /**
     * Submit a serialized transaction to the Midnight node.
     *
     * **Process:**
     * 1. Send JSON-RPC request: `author_submitExtrinsic`
     * 2. Node validates transaction (signature, format, UTXOs)
     * 3. Node adds to mempool if valid
     * 4. Returns transaction hash (32 bytes hex)
     *
     * **JSON-RPC Request Format:**
     * ```json
     * {
     *   "id": 1,
     *   "jsonrpc": "2.0",
     *   "method": "author_submitExtrinsic",
     *   "params": ["0x<hex_serialized_transaction>"]
     * }
     * ```
     *
     * **JSON-RPC Response Format:**
     * ```json
     * {
     *   "jsonrpc": "2.0",
     *   "id": 1,
     *   "result": "0x<transaction_hash>"
     * }
     * ```
     *
     * **Error Response Format:**
     * ```json
     * {
     *   "jsonrpc": "2.0",
     *   "id": 1,
     *   "error": {
     *     "code": 1010,
     *     "message": "Invalid Transaction",
     *     "data": "..."
     *   }
     * }
     * ```
     *
     * @param serializedTxHex Hex-encoded serialized transaction (without "0x" prefix)
     * @return Transaction hash (32 bytes hex, without "0x" prefix)
     * @throws NodeNetworkException if network connectivity fails
     * @throws NodeHttpException if HTTP request fails
     * @throws NodeTimeoutException if request times out
     * @throws NodeRpcError if node returns JSON-RPC error
     * @throws TransactionRejected if transaction is invalid
     * @throws NodeInvalidResponseException if response is malformed
     */
    suspend fun submitTransaction(serializedTxHex: String): String

    /**
     * Check if node is healthy and reachable.
     *
     * Performs a lightweight RPC call to verify connectivity.
     *
     * @return true if node is responding, false otherwise
     */
    suspend fun isHealthy(): Boolean

    /**
     * Close the client and release resources.
     */
    fun close()
}
