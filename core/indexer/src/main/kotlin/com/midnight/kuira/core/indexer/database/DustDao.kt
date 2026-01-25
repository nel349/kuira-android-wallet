package com.midnight.kuira.core.indexer.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for dust token operations.
 *
 * Provides methods to:
 * - Insert dust tokens (from DustLocalState sync)
 * - Update token state (AVAILABLE → PENDING → SPENT)
 * - Query available dust (for fee payment)
 * - Calculate current balance (time-based generation)
 * - Handle transaction failures (PENDING → AVAILABLE)
 *
 * **Relationship to DustLocalState:**
 * This table caches dust token data from the serialized DustLocalState.
 * Always sync this table when DustLocalState changes.
 */
@Dao
interface DustDao {
    // ========== Insert/Upsert Operations ==========

    /**
     * Insert dust tokens into database.
     *
     * Uses REPLACE strategy so re-inserting same token (by nullifier) updates it.
     * This is useful when syncing from DustLocalState after blockchain events.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokens(tokens: List<DustTokenEntity>)

    /**
     * Insert single dust token.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToken(token: DustTokenEntity)

    // ========== State Transitions ==========

    /**
     * Mark dust token as spent.
     *
     * State transition: PENDING → SPENT (on transaction SUCCESS).
     * Called when fee payment transaction is confirmed successful.
     *
     * @param nullifier Hex-encoded nullifier
     */
    @Query("UPDATE dust_tokens SET state = 'SPENT' WHERE nullifier = :nullifier")
    suspend fun markAsSpent(nullifier: String)

    /**
     * Mark multiple dust tokens as spent.
     *
     * More efficient than calling markAsSpent multiple times.
     * State transition: PENDING → SPENT (on transaction SUCCESS).
     *
     * @param nullifiers List of hex-encoded nullifiers
     */
    @Query("UPDATE dust_tokens SET state = 'SPENT' WHERE nullifier IN (:nullifiers)")
    suspend fun markAsSpent(nullifiers: List<String>)

    /**
     * Mark dust token as pending (locked for fee payment).
     *
     * State transition: AVAILABLE → PENDING (when creating transaction).
     * Prevents double-spending by locking token during transaction.
     *
     * @param nullifier Hex-encoded nullifier
     */
    @Query("UPDATE dust_tokens SET state = 'PENDING' WHERE nullifier = :nullifier")
    suspend fun markAsPending(nullifier: String)

    /**
     * Mark multiple dust tokens as pending.
     *
     * @param nullifiers List of hex-encoded nullifiers
     */
    @Query("UPDATE dust_tokens SET state = 'PENDING' WHERE nullifier IN (:nullifiers)")
    suspend fun markAsPending(nullifiers: List<String>)

    /**
     * Mark dust token as available (unlock).
     *
     * State transition: PENDING → AVAILABLE (on transaction FAILURE).
     * Unlocks token so it can be used in future fee payments.
     *
     * @param nullifier Hex-encoded nullifier
     */
    @Query("UPDATE dust_tokens SET state = 'AVAILABLE' WHERE nullifier = :nullifier")
    suspend fun markAsAvailable(nullifier: String)

    /**
     * Mark multiple dust tokens as available.
     *
     * @param nullifiers List of hex-encoded nullifiers
     */
    @Query("UPDATE dust_tokens SET state = 'AVAILABLE' WHERE nullifier IN (:nullifiers)")
    suspend fun markAsAvailable(nullifiers: List<String>)

    // ========== Query Operations ==========

    /**
     * Get all available dust tokens for an address.
     *
     * Used for fee calculation and coin selection. Only returns AVAILABLE tokens.
     * Excludes PENDING (locked) and SPENT tokens.
     *
     * **Note:** Values in database are static. Call calculateCurrentValue() on each
     * entity to get time-adjusted value.
     *
     * @param address Owner address
     * @return List of available dust tokens
     */
    @Query("SELECT * FROM dust_tokens WHERE address = :address AND state = 'AVAILABLE'")
    suspend fun getAvailableTokens(address: String): List<DustTokenEntity>

    /**
     * Get all available dust tokens for an address, sorted by value (smallest first).
     *
     * Used for smallest-first coin selection algorithm (matches midnight-ledger behavior).
     * Only returns AVAILABLE tokens. Excludes PENDING and SPENT.
     *
     * **Note:** Sorting by initial_value is approximate. For exact sorting, calculate
     * current value on each token and sort in Kotlin.
     *
     * @param address Owner address
     * @return List of available dust tokens sorted by initial value
     */
    @Query("""
        SELECT * FROM dust_tokens
        WHERE address = :address
        AND state = 'AVAILABLE'
        ORDER BY CAST(initial_value AS INTEGER) ASC
    """)
    suspend fun getAvailableTokensSorted(address: String): List<DustTokenEntity>

    /**
     * Observe all available dust tokens for an address.
     *
     * Returns Flow for reactive updates. Only returns AVAILABLE tokens.
     * Excludes PENDING (locked) and SPENT tokens.
     *
     * @param address Owner address
     * @return Flow of available dust tokens
     */
    @Query("SELECT * FROM dust_tokens WHERE address = :address AND state = 'AVAILABLE' ORDER BY creation_time_millis")
    fun observeAvailableTokens(address: String): Flow<List<DustTokenEntity>>

    /**
     * Observe all pending dust tokens for an address (locked in pending transactions).
     *
     * Returns Flow for reactive updates. Only returns PENDING tokens.
     * Excludes AVAILABLE and SPENT tokens.
     *
     * @param address Owner address
     * @return Flow of pending dust tokens
     */
    @Query("SELECT * FROM dust_tokens WHERE address = :address AND state = 'PENDING' ORDER BY creation_time_millis")
    fun observePendingTokens(address: String): Flow<List<DustTokenEntity>>

    /**
     * Get dust token by nullifier.
     *
     * Used to check if token already exists before inserting.
     *
     * @param nullifier Hex-encoded nullifier
     * @return Dust token, or null if not found
     */
    @Query("SELECT * FROM dust_tokens WHERE nullifier = :nullifier")
    suspend fun getTokenByNullifier(nullifier: String): DustTokenEntity?

    /**
     * Get all tokens backed by a specific Night UTXO.
     *
     * Used when Night UTXO is spent (triggers dust decay phase).
     *
     * @param nightUtxoId Night UTXO identifier (intentHash:outputIndex)
     * @return List of dust tokens linked to this Night UTXO
     */
    @Query("SELECT * FROM dust_tokens WHERE night_utxo_id = :nightUtxoId")
    suspend fun getTokensByNightUtxo(nightUtxoId: String): List<DustTokenEntity>

    // ========== Cleanup Operations ==========

    /**
     * Delete all dust tokens for an address.
     *
     * Used when handling deep chain reorgs or wallet reset.
     *
     * @param address Owner address
     */
    @Query("DELETE FROM dust_tokens WHERE address = :address")
    suspend fun deleteTokensForAddress(address: String)

    /**
     * Delete all dust tokens.
     *
     * Used for testing or complete wallet reset.
     */
    @Query("DELETE FROM dust_tokens")
    suspend fun deleteAll()

    // ========== Statistics ==========

    /**
     * Count total dust tokens (for debugging).
     *
     * @return Total number of dust tokens
     */
    @Query("SELECT COUNT(*) FROM dust_tokens")
    suspend fun count(): Int

    /**
     * Count available dust tokens for an address (for debugging).
     *
     * Only counts AVAILABLE tokens (not PENDING or SPENT).
     *
     * @param address Owner address
     * @return Number of available dust tokens
     */
    @Query("SELECT COUNT(*) FROM dust_tokens WHERE address = :address AND state = 'AVAILABLE'")
    suspend fun countAvailable(address: String): Int
}
