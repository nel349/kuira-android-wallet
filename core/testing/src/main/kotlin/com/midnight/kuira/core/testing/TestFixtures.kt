package com.midnight.kuira.core.testing

/**
 * Test fixtures and utilities for Kuira Wallet tests.
 *
 * Following BIP-39 and BIP-32 test vectors for crypto testing.
 */
object TestFixtures {
    /**
     * Standard BIP-39 test mnemonic (24 words)
     * From BIP-39 spec: https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki
     */
    const val TEST_MNEMONIC_24_WORDS =
        "abandon abandon abandon abandon abandon abandon abandon abandon " +
        "abandon abandon abandon abandon abandon abandon abandon abandon " +
        "abandon abandon abandon abandon abandon abandon abandon art"

    /**
     * Expected seed for TEST_MNEMONIC_24_WORDS with empty passphrase
     */
    const val EXPECTED_SEED_HEX =
        "bda85446c68413707090a52022edd26a1c9462295029f2e60cd7c4f2bbd3097170af7a4d73245cafa9c3cca8d561a7c3de6f5d4a10be8ed2a5e608d68f92fcc8"

    /**
     * Placeholder test address format (Bech32m for Midnight)
     * Format: mn_addr_[network][checksum]...
     */
    const val TEST_ADDRESS = "mn_addr_testnet1qpzry9x8gf2tvdw0s3jn54khce6mua7l"
}
