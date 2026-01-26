// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.ledger.fee

import com.midnight.kuira.core.indexer.database.DustTokenEntity
import com.midnight.kuira.core.indexer.repository.DustRepository
import java.math.BigInteger
import javax.inject.Inject

/**
 * Builds DustActions for transaction fee payment.
 *
 * **High-Level Flow:**
 * ```
 * 1. Calculate transaction fee (FeeCalculator)
 * 2. Select dust coins to cover fee (DustCoinSelector)
 * 3. Create DustSpend for each selected coin (DustSpendCreator)
 * 4. Mark coins as PENDING (DustRepository)
 * 5. Return DustActions (ready to add to Intent)
 * ```
 *
 * **DustActions Structure:**
 * - `spends`: List of DustSpend actions (one per selected coin)
 * - `registrations`: List of DustRegistration actions (empty for fee payment)
 *
 * **Integration with TransactionSubmitter:**
 * ```kotlin
 * val dustActions = dustActionsBuilder.buildDustActions(
 *     transactionHex = serializedTx,
 *     ledgerParamsHex = paramsHex,
 *     address = userAddress,
 *     seed = userSeed
 * )
 *
 * // Add dustActions to Intent
 * val intentWithFees = intent.copy(dustActions = dustActions)
 * ```
 *
 * **Rollback on Failure:**
 * If transaction submission fails, call `rollbackDustActions()` to unlock coins.
 *
 * @see `/midnight-wallet/packages/dust-wallet/src/Transacting.ts:addFeePayment` (TypeScript SDK reference)
 */
class DustActionsBuilder @Inject constructor(
    private val dustRepository: DustRepository,
    private val feeCalculator: FeeCalculator,
    private val coinSelector: DustCoinSelector,
    private val dustSpendCreator: DustSpendCreator
) {

    companion object {
        private const val TAG = "DustActionsBuilder"
    }

    /**
     * Result of building dust actions.
     *
     * @property spends List of DustSpend actions
     * @property selectedCoins Coins that were selected (for rollback)
     * @property totalFee Total fee paid in Specks
     * @property change Change amount in Specks
     */
    data class DustActions(
        val spends: List<DustSpendCreator.DustSpend>,
        val selectedCoins: List<DustTokenEntity>,
        val totalFee: BigInteger,
        val change: BigInteger
    ) {
        /** Returns true if actions were successfully created. */
        fun isSuccess(): Boolean = spends.isNotEmpty()

        /** Returns nullifiers of selected coins (for state management). */
        fun getNullifiers(): List<String> = selectedCoins.map { it.nullifier }
    }

    /**
     * Builds dust actions for transaction fee payment.
     *
     * **Steps:**
     * 1. Calculate transaction fee using midnight-ledger
     * 2. Get available dust coins from repository
     * 3. Select coins to cover fee (smallest-first strategy)
     * 4. Load DustLocalState from repository
     * 5. Create DustSpend for each selected coin
     * 6. Mark coins as PENDING in database
     * 7. Return DustActions
     *
     * **Important:**
     * - Caller must handle rollback on transaction failure
     * - Caller must mark coins as SPENT on transaction success
     * - DustLocalState must be saved after creating spends
     *
     * @param transactionHex SCALE-serialized transaction (hex)
     * @param ledgerParamsHex SCALE-serialized ledger parameters (hex)
     * @param address Wallet address
     * @param seed 32-byte seed for deriving DustSecretKey
     * @param feeBlocksMargin Safety margin in blocks (default: 5)
     * @return DustActions, or null on error
     */
    suspend fun buildDustActions(
        transactionHex: String,
        ledgerParamsHex: String,
        address: String,
        seed: ByteArray,
        feeBlocksMargin: Int = 5
    ): DustActions? {
        android.util.Log.d(TAG, "Building dust actions for transaction")

        // Step 1: Calculate transaction fee
        val fee = feeCalculator.calculateFee(
            transactionHex = transactionHex,
            ledgerParamsHex = ledgerParamsHex,
            feeBlocksMargin = feeBlocksMargin
        )

        if (fee == null) {
            android.util.Log.e(TAG, "Failed to calculate transaction fee")
            return null
        }

        android.util.Log.d(TAG, "Transaction fee: $fee Specks")

        // Step 2: Get available dust coins
        val availableCoins = dustRepository.getAvailableTokensSorted(address)

        if (availableCoins.isEmpty()) {
            android.util.Log.e(TAG, "No dust coins available")
            return null
        }

        android.util.Log.d(TAG, "Available coins: ${availableCoins.size}")

        // Step 3: Select coins to cover fee
        val currentTime = System.currentTimeMillis()
        val selection = coinSelector.selectCoins(
            availableCoins = availableCoins,
            requiredFee = fee,
            currentTimeMillis = currentTime
        )

        if (!selection.isSuccess()) {
            android.util.Log.e(TAG, "Insufficient dust to cover fee (required: $fee Specks)")
            return null
        }

        android.util.Log.d(TAG, "Selected ${selection.selectedCoins.size} coins (total: ${selection.totalValue} Specks, change: ${selection.change} Specks)")

        // Step 4: Load DustLocalState
        val state = dustRepository.loadState(address)
        if (state == null) {
            android.util.Log.e(TAG, "Failed to load dust state for $address")
            return null
        }

        try {
            // Get state pointer for FFI calls
            val statePtr = state.getStatePointer()

            // Step 5: Create DustSpend for each selected coin
            val spends = mutableListOf<DustSpendCreator.DustSpend>()

            for ((index, coin) in selection.selectedCoins.withIndex()) {
                android.util.Log.d(TAG, "Creating spend for UTXO $index (nullifier: ${coin.nullifier})")

                val spend = dustSpendCreator.createDustSpend(
                    statePtr = statePtr,
                    seed = seed,
                    utxoIndex = index,
                    vFee = if (index == 0) fee else BigInteger.ZERO, // Only first spend pays fee
                    currentTimeMs = currentTime
                )

                if (spend == null) {
                    android.util.Log.e(TAG, "Failed to create dust spend for UTXO $index")
                    return null
                }

                spends.add(spend)
            }

            android.util.Log.d(TAG, "Created ${spends.size} dust spends")

            // Step 6: Save updated state
            dustRepository.saveState(address, state)

            // Step 7: Mark coins as PENDING
            val nullifiers = selection.selectedCoins.map { it.nullifier }
            dustRepository.markTokensAsPending(nullifiers)

            android.util.Log.d(TAG, "Marked ${nullifiers.size} coins as PENDING")

            // Return DustActions with real spends
            return DustActions(
                spends = spends,
                selectedCoins = selection.selectedCoins,
                totalFee = fee,
                change = selection.change
            )

        } finally {
            // Always close state
            state.close()
        }
    }

    /**
     * Rolls back dust actions (unlocks coins after transaction failure).
     *
     * **When to call:**
     * - Transaction submission failed
     * - Transaction was rejected by blockchain
     * - User cancelled transaction
     *
     * **Effect:**
     * - Marks coins as AVAILABLE (PENDING → AVAILABLE)
     * - Coins can be used in future transactions
     *
     * @param actions DustActions to roll back
     */
    suspend fun rollbackDustActions(actions: DustActions) {
        android.util.Log.d(TAG, "Rolling back dust actions (${actions.selectedCoins.size} coins)")

        val nullifiers = actions.getNullifiers()
        dustRepository.markTokensAsAvailable(nullifiers)

        android.util.Log.d(TAG, "Rolled back ${nullifiers.size} coins to AVAILABLE")
    }

    /**
     * Confirms dust actions (marks coins as spent after transaction success).
     *
     * **When to call:**
     * - Transaction successfully confirmed on blockchain
     *
     * **Effect:**
     * - Marks coins as SPENT (PENDING → SPENT)
     * - Coins removed from available balance
     *
     * @param actions DustActions to confirm
     */
    suspend fun confirmDustActions(actions: DustActions) {
        android.util.Log.d(TAG, "Confirming dust actions (${actions.selectedCoins.size} coins)")

        val nullifiers = actions.getNullifiers()
        dustRepository.markTokensAsSpent(nullifiers)

        android.util.Log.d(TAG, "Confirmed ${nullifiers.size} coins as SPENT")
    }

    /**
     * Validates dust actions before submission.
     *
     * **Checks:**
     * - Spends match selected coins
     * - Total fee is correct
     * - All coins are in PENDING state
     *
     * @param actions DustActions to validate
     * @return true if valid, false otherwise
     */
    fun validateDustActions(actions: DustActions): Boolean {
        // Check spends exist
        if (!actions.isSuccess()) {
            android.util.Log.e(TAG, "Validation failed: no spends")
            return false
        }

        // Check total fee
        if (actions.totalFee <= BigInteger.ZERO) {
            android.util.Log.e(TAG, "Validation failed: invalid fee")
            return false
        }

        // Check selected coins
        if (actions.selectedCoins.isEmpty()) {
            android.util.Log.e(TAG, "Validation failed: no coins selected")
            return false
        }

        android.util.Log.d(TAG, "Dust actions validated successfully")
        return true
    }
}
