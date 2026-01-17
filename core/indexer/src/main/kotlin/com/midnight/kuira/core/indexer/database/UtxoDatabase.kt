package com.midnight.kuira.core.indexer.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for UTXO storage.
 *
 * Stores unshielded UTXOs for local balance calculation.
 * Eventually will also store shielded UTXOs (future).
 */
@Database(
    entities = [UnshieldedUtxoEntity::class],
    version = 2,
    exportSchema = false
)
abstract class UtxoDatabase : RoomDatabase() {
    /**
     * DAO for unshielded UTXO operations.
     */
    abstract fun unshieldedUtxoDao(): UnshieldedUtxoDao

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
