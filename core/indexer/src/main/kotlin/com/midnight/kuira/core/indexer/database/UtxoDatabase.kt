package com.midnight.kuira.core.indexer.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for UTXO storage.
 *
 * Stores unshielded UTXOs and dust tokens for local balance calculation.
 * Eventually will also store shielded UTXOs (future).
 */
@Database(
    entities = [
        UnshieldedUtxoEntity::class,
        DustTokenEntity::class
    ],
    version = 6,  // Bumped to 6: Added index on intent_hash+output_index for spent UTXO lookup
    exportSchema = false
)
abstract class UtxoDatabase : RoomDatabase() {
    /**
     * DAO for unshielded UTXO operations.
     */
    abstract fun unshieldedUtxoDao(): UnshieldedUtxoDao

    /**
     * DAO for dust token operations.
     */
    abstract fun dustDao(): DustDao

    companion object {
        private const val DATABASE_NAME = "kuira-utxo-database"

        @Volatile
        private var INSTANCE: UtxoDatabase? = null

        /**
         * Get database instance (singleton).
         *
         * Thread-safe lazy initialization.
         */
        fun getInstance(context: Context): UtxoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): UtxoDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                UtxoDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration() // TODO: Add proper migrations for production
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onDestructiveMigration(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // CRITICAL: When database is wiped by destructive migration,
                        // we must also clear the sync state. Otherwise the subscription
                        // will resume from the last saved transaction ID instead of
                        // replaying all history to repopulate the UTXO table.
                        //
                        // This is done asynchronously via a coroutine since we can't
                        // call suspend functions directly from the callback.
                        android.util.Log.w("UtxoDatabase", "Destructive migration detected - sync state needs to be cleared!")
                        // Note: SyncStateManager clearing must be done by the caller
                        // who has access to the Context for DataStore.
                        // Set a flag that the app can check on startup.
                        context.getSharedPreferences("utxo_db_flags", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("needs_full_resync", true)
                            .apply()
                    }
                })
                .enableMultiInstanceInvalidation() // Allow multiple processes to share database
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // Enable WAL for better concurrency
                .build()
        }

        /**
         * Create in-memory database for testing.
         *
         * Data is lost when database is closed.
         */
        fun createInMemoryDatabase(context: Context): UtxoDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                UtxoDatabase::class.java
            )
                .allowMainThreadQueries() // Only for testing!
                .build()
        }
    }
}
