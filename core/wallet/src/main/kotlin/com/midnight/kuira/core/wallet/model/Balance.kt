package com.midnight.kuira.core.wallet.model

import java.math.BigInteger

/**
 * Wallet balance across all address types.
 *
 * Midnight wallet has 3 address types:
 * 1. **Shielded** - Private ZK balance (mn_shield_addr_...)
 * 2. **Unshielded** - Transparent balance (mn_addr_...)
 * 3. **Dust** - Small amounts for fees (mn_dust_...)
 *
 * @property shielded Shielded (private) balance in dust (1 MIDNIGHT = 1,000,000 dust)
 * @property unshielded Unshielded (transparent) balance in dust
 * @property dust Dust balance for transaction fees
 * @property tokenType Token type (default: "MIDNIGHT")
 */
data class Balance(
    val shielded: BigInteger = BigInteger.ZERO,
    val unshielded: BigInteger = BigInteger.ZERO,
    val dust: BigInteger = BigInteger.ZERO,
    val tokenType: String = "MIDNIGHT"
) {
    /**
     * Total balance across all address types.
     */
    val total: BigInteger
        get() = shielded + unshielded + dust

    /**
     * Convert dust to MIDNIGHT (divide by 1,000,000).
     *
     * @return Balance in MIDNIGHT (decimal representation)
     */
    fun toMidnight(): MidnightBalance = MidnightBalance(
        shielded = shielded.toMidnight(),
        unshielded = unshielded.toMidnight(),
        dust = dust.toMidnight(),
        tokenType = tokenType
    )

    companion object {
        /**
         * 1 MIDNIGHT = 1,000,000 dust.
         */
        const val DUST_PER_MIDNIGHT = 1_000_000L

        /**
         * Create balance from MIDNIGHT amounts (will convert to dust).
         */
        fun fromMidnight(
            shielded: Double = 0.0,
            unshielded: Double = 0.0,
            dust: Double = 0.0,
            tokenType: String = "MIDNIGHT"
        ): Balance {
            return Balance(
                shielded = (shielded * DUST_PER_MIDNIGHT).toLong().toBigInteger(),
                unshielded = (unshielded * DUST_PER_MIDNIGHT).toLong().toBigInteger(),
                dust = (dust * DUST_PER_MIDNIGHT).toLong().toBigInteger(),
                tokenType = tokenType
            )
        }
    }
}

/**
 * Balance in MIDNIGHT (decimal representation).
 */
data class MidnightBalance(
    val shielded: Double,
    val unshielded: Double,
    val dust: Double,
    val tokenType: String = "MIDNIGHT"
) {
    val total: Double get() = shielded + unshielded + dust
}

/**
 * Convert dust (BigInteger) to MIDNIGHT (Double).
 */
fun BigInteger.toMidnight(): Double {
    return this.toDouble() / Balance.DUST_PER_MIDNIGHT
}
