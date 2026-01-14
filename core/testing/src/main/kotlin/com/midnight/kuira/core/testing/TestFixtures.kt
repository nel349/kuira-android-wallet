package com.midnight.kuira.core.testing

/**
 * Test fixtures and utilities for Kuira Wallet tests.
 *
 * Following BIP-39 and BIP-32 test vectors for crypto testing.
 */
object TestFixtures {
    /**
     * Standard BIP-39 test mnemonic (24 words)
     * From official Trezor test vectors: https://github.com/trezor/python-mnemonic/blob/master/vectors.json
     * Entropy: all zeros (0x0000...0000)
     */
    const val TEST_MNEMONIC_24_WORDS =
        "abandon abandon abandon abandon abandon abandon abandon abandon " +
        "abandon abandon abandon abandon abandon abandon abandon abandon " +
        "abandon abandon abandon abandon abandon abandon abandon art"

    /**
     * Expected seed for TEST_MNEMONIC_24_WORDS with passphrase "TREZOR"
     * (Official Trezor test vectors use "TREZOR" passphrase for all vectors)
     *
     * ⚠️ LACE COMPATIBILITY: This is TRUNCATED to 32 bytes (not 64 bytes)
     * Our implementation follows Lace wallet's approach of using only the first
     * 32 bytes of the BIP-39 seed for wallet interoperability.
     * See docs/LACE_COMPATIBILITY.md for full explanation.
     *
     * Full 64-byte seed would be: bda85446...bbd30971[70af7a4d...68f92fcc8]
     */
    const val EXPECTED_SEED_TREZOR_PASSPHRASE =
        "bda85446c68413707090a52022edd26a1c9462295029f2e60cd7c4f2bbd30971"

    /**
     * Expected seed for TEST_MNEMONIC_24_WORDS with empty passphrase ("")
     * (Most wallets use empty passphrase by default)
     *
     * ⚠️ LACE COMPATIBILITY: This is TRUNCATED to 32 bytes (not 64 bytes)
     * Our implementation follows Lace wallet's approach of using only the first
     * 32 bytes of the BIP-39 seed for wallet interoperability.
     * See docs/LACE_COMPATIBILITY.md for full explanation.
     *
     * Full 64-byte seed would be: 408b285c...c49acf70[5489c6fc...399480840]
     */
    const val EXPECTED_SEED_EMPTY_PASSPHRASE =
        "408b285c123836004f4b8842c89324c1f01382450c0d439af345ba7fc49acf70"

    /**
     * Placeholder test address format (Bech32m for Midnight)
     * Format: mn_addr_[network][checksum]...
     */
    const val TEST_ADDRESS = "mn_addr_testnet1qpzry9x8gf2tvdw0s3jn54khce6mua7l"
}
