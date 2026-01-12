// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.crypto.bip39

import org.bitcoinj.crypto.MnemonicCode
import java.security.SecureRandom

/**
 * Implementation of [MnemonicService] using BitcoinJ library.
 *
 * This implementation uses the BitcoinJ library which implements
 * standard BIP-39 (same algorithm as @scure/bip39 used by midnight-wallet SDK).
 *
 * **Compatibility:**
 * - Compatible with any standard BIP-39 implementation
 * - Same algorithm as midnight-wallet SDK (@scure/bip39)
 * - Should produce identical outputs for same inputs
 * - Battle-tested in Bitcoin wallets since 2013
 *
 * **Security:**
 * - Uses cryptographically secure random number generation
 * - Implements standard PBKDF2-HMAC-SHA512 with 2048 iterations
 * - Based on official BIP-39 specification
 */
class BitcoinJMnemonicService : MnemonicService {

    override fun generateMnemonic(wordCount: Int): String {
        require(wordCount in VALID_WORD_COUNTS) {
            "Invalid word count: $wordCount. Must be one of: ${VALID_WORD_COUNTS.joinToString()}"
        }

        // Convert word count to entropy length in bytes
        // Formula: entropy_bits = (word_count * 11) - (word_count / 3)
        // Simplified: entropy_bytes = (word_count * 4) / 3
        val entropyLength = when (wordCount) {
            12 -> 16   // 128 bits = 16 bytes
            15 -> 20   // 160 bits = 20 bytes
            18 -> 24   // 192 bits = 24 bytes
            21 -> 28   // 224 bits = 28 bytes
            24 -> 32   // 256 bits = 32 bytes
            else -> throw IllegalArgumentException("Invalid word count: $wordCount")
        }

        // Generate random entropy
        val entropy = ByteArray(entropyLength)
        SecureRandom().nextBytes(entropy)

        // Convert entropy to mnemonic using BitcoinJ
        val mnemonic = MnemonicCode.INSTANCE.toMnemonic(entropy)
        return mnemonic.joinToString(" ")
    }

    override fun mnemonicToSeed(mnemonic: String, passphrase: String): ByteArray {
        require(validateMnemonic(mnemonic)) {
            "Invalid mnemonic phrase"
        }

        // Convert string to List<String>
        val words = mnemonic.trim().split("\\s+".toRegex())

        // Convert mnemonic to seed using BIP-39 standard
        // PBKDF2-HMAC-SHA512 with 2048 iterations
        // BitcoinJ's toSeed is a static method
        return MnemonicCode.toSeed(words, passphrase)
    }

    override fun validateMnemonic(mnemonic: String): Boolean {
        return try {
            val words = mnemonic.trim().split("\\s+".toRegex())

            // Check word count
            if (words.size !in VALID_WORD_COUNTS) {
                return false
            }

            // Validate using BitcoinJ
            // check() throws MnemonicException if invalid
            MnemonicCode.INSTANCE.check(words)
            true
        } catch (e: org.bitcoinj.crypto.MnemonicException) {
            false
        } catch (e: Exception) {
            // Catch any other validation errors
            false
        }
    }

    companion object {
        /**
         * Valid BIP-39 mnemonic word counts.
         * Each corresponds to specific entropy:
         * - 12 words = 128 bits (16 bytes)
         * - 15 words = 160 bits (20 bytes)
         * - 18 words = 192 bits (24 bytes)
         * - 21 words = 224 bits (28 bytes)
         * - 24 words = 256 bits (32 bytes)
         */
        private val VALID_WORD_COUNTS = setOf(12, 15, 18, 21, 24)
    }
}
