package com.midnight.kuira.core.indexer.reorg

import com.midnight.kuira.core.indexer.model.BlockInfo
import kotlinx.coroutines.flow.Flow

/**
 * Detects and handles blockchain reorganizations (reorgs).
 *
 * **What is a Reorg?**
 * When two competing blocks are mined at same height, blockchain temporarily forks.
 * Eventually one fork becomes canonical, invalidating events in the other fork.
 *
 * **Example:**
 * ```
 * Before Reorg:
 * ... → Block 100 → Block 101A → Block 102A
 *
 * After Reorg (longer chain found):
 * ... → Block 100 → Block 101B → Block 102B → Block 103B
 *
 * Events in 101A and 102A are now INVALID and must be discarded.
 * ```
 *
 * **Why This Matters:**
 * - Without reorg handling, wallet shows incorrect balance
 * - User may spend funds they don't actually have
 * - Double-spend attacks possible
 *
 * **Detection Strategy:**
 * 1. Track latest block hash we've seen
 * 2. When new block arrives, verify parent hash matches our latest
 * 3. If mismatch, reorg detected at that height
 * 4. Roll back to last common ancestor
 * 5. Re-sync from that point forward
 */
interface ReorgDetector {

    /**
     * Record a new block in the chain.
     *
     * **Returns:**
     * - `null` if no reorg detected (block extends current chain)
     * - `ReorgEvent` if reorg detected
     *
     * @param block New block from indexer
     * @param parentHash Hash of parent block (from block header)
     * @return ReorgEvent if reorg detected, null otherwise
     */
    suspend fun recordBlock(block: BlockInfo, parentHash: String): ReorgEvent?

    /**
     * Get current canonical chain tip.
     *
     * @return Latest block we trust as canonical
     */
    suspend fun getChainTip(): BlockInfo?

    /**
     * Observe reorg events as they occur.
     *
     * **Usage:**
     * ```kotlin
     * reorgDetector.observeReorgs().collect { reorg ->
     *     when (reorg) {
     *         is ShallowReorg -> {
     *             // Reorg within finality window, roll back events
     *             eventCache.deleteEventsAfter(reorg.commonAncestorHeight)
     *             syncManager.resyncFrom(reorg.commonAncestorHeight)
     *         }
     *         is DeepReorg -> {
     *             // Reorg past finality, critical failure
     *             alertUser("Blockchain reorganization detected!")
     *             clearAllData()
     *             resyncFromGenesis()
     *         }
     *     }
     * }
     * ```
     */
    fun observeReorgs(): Flow<ReorgEvent>

    /**
     * Reset detector state (for testing or recovery).
     */
    suspend fun reset()
}

/**
 * Blockchain reorganization event.
 */
sealed class ReorgEvent {
    /**
     * Height at which reorg occurred.
     */
    abstract val reorgHeight: Long

    /**
     * Height of last common ancestor block.
     */
    abstract val commonAncestorHeight: Long

    /**
     * Shallow reorg (within finality window).
     *
     * **Action:** Roll back events to common ancestor, re-sync forward.
     *
     * **Finality Window:**
     * - Bitcoin: ~6 blocks
     * - Ethereum: ~64 blocks (2 epochs)
     * - Midnight: TBD (check consensus docs)
     *
     * @property reorgHeight Height where chains diverged
     * @property commonAncestorHeight Last valid block height
     * @property oldBranch Blocks in invalidated branch
     * @property newBranch Blocks in canonical branch
     */
    data class ShallowReorg(
        override val reorgHeight: Long,
        override val commonAncestorHeight: Long,
        val oldBranch: List<BlockInfo>,
        val newBranch: List<BlockInfo>
    ) : ReorgEvent() {
        /**
         * Number of blocks rolled back.
         */
        val depth: Int get() = oldBranch.size
    }

    /**
     * Deep reorg (past finality threshold).
     *
     * **Action:** This should NEVER happen in a healthy blockchain.
     * Indicates major consensus failure or attack.
     *
     * **Recovery:**
     * 1. Alert user immediately
     * 2. Clear all cached data
     * 3. Re-sync from genesis
     * 4. Consider network may be under attack
     */
    data class DeepReorg(
        override val reorgHeight: Long,
        override val commonAncestorHeight: Long,
        val finalityThreshold: Long
    ) : ReorgEvent() {
        /**
         * Number of blocks past finality.
         */
        val depthBeyondFinality: Long get() = reorgHeight - finalityThreshold
    }
}

/**
 * Configuration for reorg detection.
 */
data class ReorgConfig(
    /**
     * Finality threshold in blocks.
     *
     * After this many confirmations, blocks are considered immutable.
     * Default: 64 blocks (conservative, similar to Ethereum)
     */
    val finalityThreshold: Long = 64,

    /**
     * How many blocks of history to keep for reorg detection.
     *
     * Must be >= finalityThreshold.
     * Default: 100 blocks
     */
    val historyDepth: Long = 100
) {
    init {
        require(finalityThreshold > 0) { "Finality threshold must be positive" }
        require(historyDepth >= finalityThreshold) {
            "History depth ($historyDepth) must be >= finality threshold ($finalityThreshold)"
        }
    }
}

/**
 * Exception thrown when reorg handling fails.
 */
class ReorgHandlingException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
