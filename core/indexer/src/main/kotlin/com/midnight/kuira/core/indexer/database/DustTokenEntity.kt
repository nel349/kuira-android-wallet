package com.midnight.kuira.core.indexer.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for dust token (UTXO) storage.
 *
 * **Purpose:**
 * Caches dust token information from DustLocalState for fast balance queries
 * and prevents need to deserialize entire state for simple operations.
 *
 * **Relationship to DustLocalState:**
 * - Source of truth: Serialized DustLocalState (in EncryptedSharedPreferences)
 * - This table: Cache layer for performance
 * - Sync: Update this table when DustLocalState changes
 *
 * **Dust Token Lifecycle:**
 * 1. Night UTXO registered → Dust token created with value=0
 * 2. Dust generates over time (value increases up to cap)
 * 3. Dust spent for fees → Token marked PENDING
 * 4. Transaction confirmed → Token marked SPENT, new token created with change
 *
 * **Balance Calculation:**
 * Current balance = sum(all AVAILABLE dust tokens, calculated at current time)
 */
@Entity(
    tableName = "dust_tokens",
    indices = [
        Index(value = ["address"]),
        Index(value = ["state"]),
        Index(value = ["nullifier"], unique = true)
    ]
)
data class DustTokenEntity(
    /**
     * Unique identifier: hex-encoded nullifier (32 bytes).
     * The nullifier is the unique ID for this dust token in the commitment tree.
     */
    @PrimaryKey
    val nullifier: String,

    /**
     * Wallet address that owns this dust token.
     * Format: mn_addr_[network]1...
     */
    @ColumnInfo(name = "address")
    val address: String,

    /**
     * Initial value when token was created (in Specks).
     * Stored as string to support u128 values from Rust.
     */
    @ColumnInfo(name = "initial_value")
    val initialValue: String,

    /**
     * Creation time (Unix timestamp in milliseconds).
     * Used to calculate current value based on generation progress.
     */
    @ColumnInfo(name = "creation_time_millis")
    val creationTimeMillis: Long,

    /**
     * Backing Night UTXO identifier (intentHash:outputIndex).
     * Links this dust token to the Night UTXO that generates it.
     */
    @ColumnInfo(name = "night_utxo_id")
    val nightUtxoId: String,

    /**
     * Backing Night UTXO value (in Stars).
     * Determines dust generation rate and capacity.
     * Stored as string to support large values.
     */
    @ColumnInfo(name = "night_value_stars")
    val nightValueStars: String,

    /**
     * Maximum dust capacity for this token (in Specks).
     * Calculated as: nightValueStars * nightDustRatio
     * Default: 5 Dust per Night (5,000,000 Specks per Star)
     * Stored as string to support u128 values.
     */
    @ColumnInfo(name = "dust_capacity_specks")
    val dustCapacitySpecks: String,

    /**
     * Dust generation rate (Specks per second).
     * Calculated as: nightValueStars * generationDecayRate
     * Default: 8,267 Specks per Star per second
     * Stored as string to support precision.
     */
    @ColumnInfo(name = "generation_rate_per_second")
    val generationRatePerSecond: String,

    /**
     * Token state (AVAILABLE, PENDING, or SPENT).
     *
     * - AVAILABLE: Can be used for fees, included in balance
     * - PENDING: Locked for pending transaction, excluded from balance
     * - SPENT: Confirmed spent, excluded from balance
     *
     * State transitions:
     * - New token → AVAILABLE
     * - Fee payment created → PENDING
     * - Transaction SUCCESS → SPENT
     * - Transaction FAILURE → AVAILABLE (unlocked)
     */
    @ColumnInfo(name = "state")
    val state: UtxoState = UtxoState.AVAILABLE,

    /**
     * Last updated timestamp (Unix timestamp in milliseconds).
     * Used to track when this cache entry was last synced with DustLocalState.
     */
    @ColumnInfo(name = "last_updated_millis")
    val lastUpdatedMillis: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Create entity from DustLocalState UTXO data.
         *
         * @param nullifier Hex-encoded nullifier
         * @param address Owner address
         * @param initialValue Initial dust value (Specks)
         * @param creationTimeMillis Creation timestamp
         * @param nightUtxoId Backing Night UTXO ID
         * @param nightValueStars Backing Night value
         * @param dustCapacitySpecks Max dust capacity
         * @param generationRatePerSecond Generation rate
         * @param state Token state
         * @return DustTokenEntity
         */
        fun create(
            nullifier: String,
            address: String,
            initialValue: String,
            creationTimeMillis: Long,
            nightUtxoId: String,
            nightValueStars: String,
            dustCapacitySpecks: String,
            generationRatePerSecond: String,
            state: UtxoState = UtxoState.AVAILABLE
        ): DustTokenEntity {
            return DustTokenEntity(
                nullifier = nullifier,
                address = address,
                initialValue = initialValue,
                creationTimeMillis = creationTimeMillis,
                nightUtxoId = nightUtxoId,
                nightValueStars = nightValueStars,
                dustCapacitySpecks = dustCapacitySpecks,
                generationRatePerSecond = generationRatePerSecond,
                state = state,
                lastUpdatedMillis = System.currentTimeMillis()
            )
        }
    }
}
