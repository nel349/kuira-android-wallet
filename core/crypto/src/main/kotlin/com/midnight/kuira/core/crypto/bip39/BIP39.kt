// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.crypto.bip39

/**
 * Convenience object for BIP-39 mnemonic operations.
 *
 * This is a facade that delegates to a [MnemonicService] implementation.
 * Default implementation uses BitcoinJ, but can be swapped if needed
 * for compatibility with different wallet implementations.
 *
 * **Reference:** https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki
 *
 * **Usage:**
 * ```kotlin
 * // Generate 24-word mnemonic
 * val mnemonic = BIP39.generateMnemonic()
 *
 * // Convert to seed
 * val seed = BIP39.mnemonicToSeed(mnemonic)
 *
 * // Validate mnemonic
 * val isValid = BIP39.validateMnemonic(mnemonic)
 * ```
 */
object BIP39 {

    /**
     * The underlying service implementation.
     * Can be swapped for testing or compatibility reasons.
     */
    @Volatile
    private var service: MnemonicService = BitcoinJMnemonicService()

    /**
     * Sets a custom [MnemonicService] implementation.
     * Useful for testing or supporting alternative wallet formats.
     *
     * @param customService The service implementation to use
     */
    fun setService(customService: MnemonicService) {
        service = customService
    }

    /**
     * Generates a random BIP-39 mnemonic phrase.
     *
     * @param wordCount Number of words (12, 15, 18, 21, or 24). Default: 24
     * @return Space-separated mnemonic phrase
     * @throws IllegalArgumentException if wordCount is invalid
     */
    fun generateMnemonic(wordCount: Int = 24): String {
        return service.generateMnemonic(wordCount)
    }

    /**
     * Derives a 64-byte seed from a mnemonic phrase and optional passphrase.
     *
     * Uses PBKDF2-HMAC-SHA512 with 2048 iterations as specified in BIP-39.
     *
     * @param mnemonic The mnemonic phrase (12-24 words)
     * @param passphrase Optional passphrase for additional security (default: empty string)
     * @return 64-byte seed as ByteArray
     * @throws IllegalArgumentException if mnemonic is invalid
     */
    fun mnemonicToSeed(mnemonic: String, passphrase: String = ""): ByteArray {
        return service.mnemonicToSeed(mnemonic, passphrase)
    }

    /**
     * Validates a mnemonic phrase.
     *
     * Checks:
     * - Word count is valid (12, 15, 18, 21, or 24)
     * - All words are in BIP-39 word list
     * - Checksum is correct
     *
     * @param mnemonic The mnemonic phrase to validate
     * @return true if valid, false otherwise
     */
    fun validateMnemonic(mnemonic: String): Boolean {
        return service.validateMnemonic(mnemonic)
    }
}
