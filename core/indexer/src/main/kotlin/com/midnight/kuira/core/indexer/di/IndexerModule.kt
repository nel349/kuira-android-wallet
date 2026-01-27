package com.midnight.kuira.core.indexer.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.midnight.kuira.core.indexer.api.IndexerClient
import com.midnight.kuira.core.indexer.api.IndexerClientImpl
import com.midnight.kuira.core.indexer.database.UtxoDatabase
import com.midnight.kuira.core.indexer.repository.BalanceRepository
import com.midnight.kuira.core.indexer.sync.SubscriptionManager
import com.midnight.kuira.core.indexer.sync.SyncStateManager
import com.midnight.kuira.core.indexer.utxo.UtxoManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for dust state DataStore.
 * Distinguishes it from other DataStore instances.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DustStateDataStore

// Extension property for creating DataStore
private val Context.dustStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dust_state"
)

/**
 * Hilt module for Indexer component dependencies.
 *
 * **Provided Dependencies:**
 * - IndexerClient: WebSocket client for Midnight Indexer API
 * - UtxoDatabase: Room database for UTXO storage
 * - UtxoManager: UTXO processing and balance calculation
 * - BalanceRepository: Public API for balance queries
 * - SyncStateManager: Sync progress persistence
 * - SubscriptionManagerFactory: Factory for creating SubscriptionManager instances
 *
 * **Note on SubscriptionManager:**
 * SubscriptionManager is NOT a singleton because each instance should manage
 * a single address subscription. Use SubscriptionManagerFactory to create instances.
 */
@Module
@InstallIn(SingletonComponent::class)
object IndexerModule {

    /**
     * Provide IndexerClient singleton.
     *
     * **Singleton Scope:** Expensive to create (WebSocket connection), shared across app.
     *
     * **Configuration:**
     * - Development mode for local testing (allows HTTP to localhost)
     * - Production: Use HTTPS with certificate pinning (TODO: Phase 4C)
     */
    @Provides
    @Singleton
    fun provideIndexerClient(): IndexerClient {
        // TODO: Read from BuildConfig or Settings
        val baseUrl = "http://10.0.2.2:8088/api/v3" // Android emulator localhost
        val developmentMode = true // Allow HTTP for local testing

        return IndexerClientImpl(
            baseUrl = baseUrl,
            developmentMode = developmentMode
        )
    }

    /**
     * Provide UtxoDatabase singleton.
     *
     * **Singleton Scope:** Room database should be singleton to avoid multiple instances.
     *
     * **Database Name:** "utxo_database" (persistent storage)
     */
    @Provides
    @Singleton
    fun provideUtxoDatabase(
        @ApplicationContext context: Context
    ): UtxoDatabase {
        return Room.databaseBuilder(
            context,
            UtxoDatabase::class.java,
            "utxo_database"
        ).build()
    }

    /**
     * Provide UtxoManager singleton.
     *
     * **Singleton Scope:** Single instance manages all UTXOs for all addresses.
     */
    @Provides
    @Singleton
    fun provideUtxoManager(database: UtxoDatabase): UtxoManager {
        return UtxoManager(database.unshieldedUtxoDao())
    }

    /**
     * Provide DustDao from database.
     *
     * **Purpose:** For DustRepository to access dust token database.
     */
    @Provides
    @Singleton
    fun provideDustDao(database: UtxoDatabase) = database.dustDao()

    /**
     * Provide BalanceRepository singleton.
     *
     * **Singleton Scope:** Repository layer is stateless, safe to share.
     */
    @Provides
    @Singleton
    fun provideBalanceRepository(
        utxoManager: UtxoManager,
        indexerClient: IndexerClient
    ): BalanceRepository {
        return BalanceRepository(utxoManager, indexerClient)
    }

    /**
     * Provide SyncStateManager singleton.
     *
     * **Singleton Scope:** Manages sync state for ALL addresses, uses shared DataStore.
     */
    @Provides
    @Singleton
    fun provideSyncStateManager(
        @ApplicationContext context: Context
    ): SyncStateManager {
        return SyncStateManager(context)
    }

    /**
     * Provide DataStore for dust state persistence.
     *
     * **Singleton Scope:** Shared DataStore instance for all dust state operations.
     *
     * **Qualifier:** @DustStateDataStore distinguishes from other DataStore instances.
     */
    @Provides
    @Singleton
    @DustStateDataStore
    fun provideDustStateDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return context.dustStateDataStore
    }

    /**
     * Provide SubscriptionManagerFactory.
     *
     * **Why Factory:** SubscriptionManager should NOT be singleton because:
     * - Each instance manages subscription for ONE address
     * - Multiple addresses = multiple SubscriptionManager instances
     * - Lifecycle tied to ViewModel/screen scope, not app scope
     *
     * **Usage in ViewModel:**
     * ```kotlin
     * @HiltViewModel
     * class BalanceViewModel @Inject constructor(
     *     private val subscriptionManagerFactory: SubscriptionManagerFactory
     * ) : ViewModel() {
     *
     *     fun syncBalance(address: String) {
     *         viewModelScope.launch {
     *             subscriptionManagerFactory.create()
     *                 .startSubscription(address)
     *                 .collect { state -> ... }
     *         }
     *     }
     * }
     * ```
     */
    @Provides
    fun provideSubscriptionManagerFactory(
        indexerClient: IndexerClient,
        utxoManager: UtxoManager,
        syncStateManager: SyncStateManager
    ): SubscriptionManagerFactory {
        return SubscriptionManagerFactory(indexerClient, utxoManager, syncStateManager)
    }
}

/**
 * Factory for creating SubscriptionManager instances.
 *
 * Use this instead of directly injecting SubscriptionManager to avoid singleton scope issues.
 */
class SubscriptionManagerFactory(
    private val indexerClient: IndexerClient,
    private val utxoManager: UtxoManager,
    private val syncStateManager: SyncStateManager
) {
    /**
     * Create a new SubscriptionManager instance.
     *
     * Each instance should manage subscription for ONE address.
     * Collect the subscription flow in a ViewModel-scoped coroutine.
     */
    fun create(): SubscriptionManager {
        return SubscriptionManager(indexerClient, utxoManager, syncStateManager)
    }
}
