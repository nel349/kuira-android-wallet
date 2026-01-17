package com.midnight.kuira.core.indexer.model

import kotlinx.serialization.Serializable

/**
 * Status of a transaction on the Midnight blockchain.
 *
 * Matches Midnight SDK's transaction status enum.
 */
@Serializable
enum class TransactionStatus {
    /**
     * All segments succeeded.
     */
    SUCCESS,

    /**
     * All segments failed.
     */
    FAILURE,

    /**
     * Some segments succeeded, some failed.
     */
    PARTIAL_SUCCESS
}
