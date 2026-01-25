// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.crypto.dust

/**
 * Parameters for Dust generation and decay.
 *
 * **What are Dust Parameters?**
 * These control how Dust tokens generate and decay over time:
 * - **Night Dust Ratio**: Maximum dust a Night UTXO can generate (capacity)
 * - **Generation/Decay Rate**: How fast dust accumulates and decays
 *
 * **Dust Generation Formula:**
 * ```
 * max_capacity = night_value × night_dust_ratio
 * generation_rate = night_value × generation_decay_rate
 * decay_rate = night_value × generation_decay_rate (same as generation)
 * ```
 *
 * **Example:**
 * ```
 * Night value: 100 NIGHT
 * night_dust_ratio: 1000
 * generation_decay_rate: 10
 *
 * → Max capacity: 100 × 1000 = 100,000 Specks
 * → Generation rate: 100 × 10 = 1,000 Specks/second
 * → Time to full: 100,000 / 1,000 = 100 seconds
 * → Decay rate: 1,000 Specks/second (back to zero in 100 seconds)
 * ```
 *
 * **Midnight SDK Mapping:**
 * This corresponds to `DustParameters`:
 * ```rust
 * pub struct DustParameters {
 *     pub night_dust_ratio: u64,           // 64-bit unsigned
 *     pub generation_decay_rate: u32,      // 32-bit unsigned ⚠️ IMPORTANT
 *     pub dust_grace_period: Duration,
 * }
 * ```
 *
 * **Type Mapping:**
 * - night_dust_ratio (u64) → Long (Kotlin signed 64-bit, range sufficient)
 * - generation_decay_rate (u32) → Int (Kotlin signed 32-bit, range sufficient for positive values)
 *
 * **Network Defaults:**
 * - Testnet: Uses INITIAL_DUST_PARAMETERS
 * - Mainnet: TBD by governance
 *
 * **Usage:**
 * ```kotlin
 * val params = DustParameters.TESTNET_DEFAULTS
 *
 * // Calculate max capacity for a Night UTXO
 * val nightValue = BigInteger.valueOf(100_000_000) // 100 NIGHT
 * val maxDust = params.calculateMaxCapacity(nightValue)
 * println("Max dust: $maxDust Specks")
 *
 * // Calculate generation rate
 * val rate = params.calculateGenerationRate(nightValue)
 * println("Generation: $rate Specks/second")
 * ```
 *
 * @property nightDustRatio Ratio of Night value to maximum dust capacity (u64 → Long)
 * @property generationDecayRate Rate of dust generation and decay per second (u32 → Int)
 */
data class DustParameters(
    val nightDustRatio: Long,
    val generationDecayRate: Int
) {
    /**
     * Calculates the maximum dust capacity for a given Night value.
     *
     * **Formula:**
     * ```
     * max_capacity = night_value × night_dust_ratio
     * ```
     *
     * @param nightValue Value of the Night UTXO in Specks
     * @return Maximum dust capacity in Specks
     */
    fun calculateMaxCapacity(nightValue: java.math.BigInteger): java.math.BigInteger {
        return nightValue.multiply(java.math.BigInteger.valueOf(nightDustRatio))
    }

    /**
     * Calculates the generation rate for a given Night value.
     *
     * **Formula:**
     * ```
     * generation_rate = night_value × generation_decay_rate
     * ```
     *
     * @param nightValue Value of the Night UTXO in Specks
     * @return Dust generation rate in Specks per second
     */
    fun calculateGenerationRate(nightValue: java.math.BigInteger): java.math.BigInteger {
        return nightValue.multiply(java.math.BigInteger.valueOf(generationDecayRate.toLong()))
    }

    /**
     * Calculates the decay rate for a given Night value.
     *
     * **Note:** Decay rate equals generation rate in Midnight's design.
     *
     * @param nightValue Value of the Night UTXO in Specks
     * @return Dust decay rate in Specks per second
     */
    fun calculateDecayRate(nightValue: java.math.BigInteger): java.math.BigInteger {
        return calculateGenerationRate(nightValue)
    }

    /**
     * Calculates the time to reach full capacity for a given Night value.
     *
     * **Formula:**
     * ```
     * time_to_full = max_capacity / generation_rate
     * ```
     *
     * @param nightValue Value of the Night UTXO in Specks
     * @param initialDust Initial dust value (default 0)
     * @return Time to full capacity in seconds
     */
    fun calculateTimeToFull(
        nightValue: java.math.BigInteger,
        initialDust: java.math.BigInteger = java.math.BigInteger.ZERO
    ): Long {
        val capacity = calculateMaxCapacity(nightValue)
        val rate = calculateGenerationRate(nightValue)

        if (rate == java.math.BigInteger.ZERO) {
            return Long.MAX_VALUE // Never fills if rate is zero
        }

        val dustToGenerate = capacity.subtract(initialDust)
        if (dustToGenerate <= java.math.BigInteger.ZERO) {
            return 0 // Already full
        }

        return dustToGenerate.divide(rate).toLong()
    }

    /**
     * Calculates the time to decay to zero for a given Night value.
     *
     * **Formula:**
     * ```
     * time_to_empty = current_dust / decay_rate
     * ```
     *
     * @param nightValue Value of the Night UTXO in Specks
     * @param currentDust Current dust value (default: max capacity)
     * @return Time to decay to zero in seconds
     */
    fun calculateTimeToEmpty(
        nightValue: java.math.BigInteger,
        currentDust: java.math.BigInteger = calculateMaxCapacity(nightValue)
    ): Long {
        val rate = calculateDecayRate(nightValue)

        if (rate == java.math.BigInteger.ZERO) {
            return Long.MAX_VALUE // Never decays if rate is zero
        }

        if (currentDust <= java.math.BigInteger.ZERO) {
            return 0 // Already empty
        }

        return currentDust.divide(rate).toLong()
    }

    companion object {
        /**
         * Testnet default parameters (from INITIAL_DUST_PARAMETERS).
         *
         * **From midnight-ledger:**
         * ```rust
         * pub const INITIAL_DUST_PARAMETERS: DustParameters = DustParameters {
         *     night_dust_ratio: 5 * (SPECKS_PER_DUST / STARS_PER_NIGHT) as u64,
         *                     // = 5 * (1e15 / 1e6) = 5 * 1e9 = 5,000,000,000
         *                     // Meaning: 5 Dust per Night
         *     generation_decay_rate: 8_267,  // u32
         *                           // Generation time ≈ 1 week
         *     dust_grace_period: Duration::from_hours(3), // Not used in Phase 2D-2
         * };
         * ```
         *
         * **Constants:**
         * - SPECKS_PER_DUST = 1,000,000,000,000,000 (1 quadrillion, 10^15)
         * - STARS_PER_NIGHT = 1,000,000 (1 million, 10^6)
         *
         * **Type Verification:**
         * - nightDustRatio: u64 → Long ✅ (max u64 = 18 quintillion, max Long = 9 quintillion, sufficient)
         * - generationDecayRate: u32 → Int ✅ (max u32 = 4 billion, max Int = 2 billion, sufficient)
         */
        val TESTNET_DEFAULTS = DustParameters(
            nightDustRatio = 5_000_000_000L,  // u64: 5 billion (5 Dust per Night)
            generationDecayRate = 8_267        // u32: ~1 week generation time
        )

        /**
         * Mainnet parameters (TBD).
         *
         * **Note:** These will be determined by Midnight governance.
         * For now, use testnet defaults.
         */
        val MAINNET_DEFAULTS = TESTNET_DEFAULTS

        /**
         * Validates dust parameters are reasonable.
         *
         * @param params Parameters to validate
         * @return true if valid, false otherwise
         */
        fun isValid(params: DustParameters): Boolean {
            // Ratios and rates must be positive
            if (params.nightDustRatio <= 0 || params.generationDecayRate <= 0) {
                return false
            }

            // TODO: Add additional validation rules as needed
            // - Maximum reasonable ratio
            // - Maximum reasonable rate
            // - Consistency checks

            return true
        }
    }
}
