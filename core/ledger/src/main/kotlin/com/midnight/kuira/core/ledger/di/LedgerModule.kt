package com.midnight.kuira.core.ledger.di

import com.midnight.kuira.core.indexer.api.IndexerClient
import com.midnight.kuira.core.ledger.api.FfiTransactionSerializer
import com.midnight.kuira.core.ledger.api.NodeRpcClient
import com.midnight.kuira.core.ledger.api.NodeRpcClientImpl
import com.midnight.kuira.core.ledger.api.ProofServerClient
import com.midnight.kuira.core.ledger.api.ProofServerClientImpl
import com.midnight.kuira.core.ledger.api.TransactionSerializer
import com.midnight.kuira.core.ledger.api.TransactionSubmitter
import com.midnight.kuira.core.ledger.fee.DustActionsBuilder
import com.midnight.kuira.core.ledger.fee.DustCoinSelector
import com.midnight.kuira.core.ledger.fee.DustSpendCreator
import com.midnight.kuira.core.ledger.fee.FeeCalculator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Ledger component dependencies.
 *
 * **Provided Dependencies:**
 * - NodeRpcClient: HTTP client for Midnight node JSON-RPC API
 * - ProofServerClient: HTTP client for Midnight proof server (Phase 2)
 * - TransactionSerializer: SCALE serialization using Rust FFI
 * - TransactionSubmitter: Transaction submission orchestrator
 *
 * **Note:**
 * DustActionsBuilder and its dependencies are auto-provided by Hilt
 * (@Inject constructors): DustRepository, DustCoinSelector, FeeCalculator, DustSpendCreator
 */
@Module
@InstallIn(SingletonComponent::class)
object LedgerModule {

    /**
     * Provide NodeRpcClient singleton.
     *
     * **Singleton Scope:** HTTP client is expensive to create, shared across app.
     *
     * **Configuration:**
     * - Development mode for local testing (allows HTTP to localhost)
     * - Production: Use HTTPS (TODO: Phase 4C)
     *
     * **Node URLs:**
     * - Android Emulator: `http://10.0.2.2:9944` (localhost:9944 on host machine)
     * - Physical Device: Use actual IP address
     */
    @Provides
    @Singleton
    fun provideNodeRpcClient(): NodeRpcClient {
        // TODO: Read from BuildConfig or Settings
        val nodeUrl = "http://10.0.2.2:9944" // Android emulator → host localhost:9944
        val developmentMode = true // Allow HTTP for local testing

        return NodeRpcClientImpl(
            nodeUrl = nodeUrl,
            developmentMode = developmentMode
        )
    }

    /**
     * Provide ProofServerClient singleton.
     *
     * **Singleton Scope:** HTTP client is expensive to create, shared across app.
     *
     * **Configuration:**
     * - Development mode for local testing (allows HTTP to localhost)
     * - Production: Use HTTPS (TODO: Phase 4C)
     *
     * **Proof Server URLs:**
     * - Android Emulator: `http://10.0.2.2:6300` (localhost:6300 on host machine)
     * - Physical Device: Use actual IP address
     * - TestNet/MainNet: TBD (Midnight will provide public proof servers)
     *
     * **Phase 2:** Required for transaction proving (convert unproven → proven)
     */
    @Provides
    @Singleton
    fun provideProofServerClient(): ProofServerClient {
        // TODO: Read from BuildConfig or Settings
        val proofServerUrl = "http://10.0.2.2:6300" // Android emulator → host localhost:6300
        val developmentMode = true // Allow HTTP for local testing

        return ProofServerClientImpl(
            proofServerUrl = proofServerUrl,
            developmentMode = developmentMode
        )
    }

    /**
     * Provide TransactionSerializer singleton.
     *
     * **Singleton Scope:** Stateless serializer, safe to share.
     *
     * **Implementation:** FfiTransactionSerializer uses Rust midnight-ledger
     * for SCALE encoding via JNI.
     */
    @Provides
    @Singleton
    fun provideTransactionSerializer(): TransactionSerializer {
        return FfiTransactionSerializer()
    }

    /**
     * Provide FeeCalculator object.
     *
     * **Note:** FeeCalculator is a Kotlin object, but we need to provide it
     * explicitly for Hilt dependency injection.
     */
    @Provides
    @Singleton
    fun provideFeeCalculator(): FeeCalculator = FeeCalculator

    /**
     * Provide DustSpendCreator object.
     *
     * **Note:** DustSpendCreator is a Kotlin object, but we need to provide it
     * explicitly for Hilt dependency injection.
     */
    @Provides
    @Singleton
    fun provideDustSpendCreator(): DustSpendCreator = DustSpendCreator

    /**
     * Provide DustCoinSelector.
     *
     * **Note:** DustCoinSelector has @Inject constructor with no parameters,
     * but we provide it explicitly for clarity.
     */
    @Provides
    @Singleton
    fun provideDustCoinSelector(): DustCoinSelector = DustCoinSelector()

    /**
     * Provide TransactionSubmitter singleton.
     *
     * **Singleton Scope:** Stateless orchestrator, safe to share.
     *
     * **Dependencies:**
     * - NodeRpcClient: Submits transaction to node
     * - ProofServerClient: Proves transactions (Phase 2)
     * - IndexerClient: Tracks transaction confirmation
     * - TransactionSerializer: Serializes to SCALE
     * - DustActionsBuilder: Builds dust fee payment (optional, Phase 2E)
     *
     * **Note:** DustActionsBuilder is auto-provided by Hilt via @Inject constructor
     */
    @Provides
    @Singleton
    fun provideTransactionSubmitter(
        nodeRpcClient: NodeRpcClient,
        proofServerClient: ProofServerClient,
        indexerClient: IndexerClient,
        serializer: TransactionSerializer,
        dustActionsBuilder: DustActionsBuilder
    ): TransactionSubmitter {
        return TransactionSubmitter(
            nodeRpcClient = nodeRpcClient,
            proofServerClient = proofServerClient,
            indexerClient = indexerClient,
            serializer = serializer,
            dustActionsBuilder = dustActionsBuilder
        )
    }
}
