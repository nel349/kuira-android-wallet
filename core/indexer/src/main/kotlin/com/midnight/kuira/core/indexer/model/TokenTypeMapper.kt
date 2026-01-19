package com.midnight.kuira.core.indexer.model

/**
 * Maps token identifiers from indexer API to human-readable token symbols.
 *
 * **Midnight Token Standards:**
 * - NIGHT: Native token (represented as 64 zeros in hex)
 * - DUST: Testnet token (also 64 zeros in undeployed network)
 * - Custom tokens: 32-byte hex strings (future support)
 *
 * **Why this is needed:**
 * - Indexer returns token as 64-char hex string (e.g., "000...000")
 * - BalanceFormatter expects symbol strings ("NIGHT", "DUST")
 * - UI displays formatted amounts like "1,500.0 NIGHT" not "1,500.0 000...000"
 */
object TokenTypeMapper {

    /**
     * Native NIGHT token identifier (64 zeros).
     */
    private const val NATIVE_TOKEN_HEX = "0000000000000000000000000000000000000000000000000000000000000000"

    /**
     * Convert indexer token hex to display symbol.
     *
     * **Mapping:**
     * - `000...000` (64 zeros) → "NIGHT" (mainnet/testnet/undeployed)
     * - Other hex → Shortened hex (first 8 chars) for custom tokens
     *
     * @param tokenHex 64-character hex string from indexer
     * @return Human-readable token symbol ("NIGHT", "DUST", or shortened hex)
     */
    fun toDisplaySymbol(tokenHex: String): String {
        return when (tokenHex) {
            NATIVE_TOKEN_HEX -> "NIGHT"
            else -> {
                // Custom token: Show first 8 chars for now
                // Future: Look up custom token registry for proper symbol
                if (tokenHex.length >= 8) {
                    tokenHex.substring(0, 8).uppercase()
                } else {
                    tokenHex.uppercase()
                }
            }
        }
    }

    /**
     * Convert display symbol back to indexer token hex (for queries).
     *
     * **Reverse mapping:**
     * - "NIGHT" → `000...000`
     * - "DUST" → `000...000` (same as NIGHT in undeployed)
     * - Other → Assume already hex format
     */
    fun toIndexerHex(displaySymbol: String): String {
        return when (displaySymbol.uppercase()) {
            "NIGHT", "DUST" -> NATIVE_TOKEN_HEX
            else -> displaySymbol.lowercase()
        }
    }
}
