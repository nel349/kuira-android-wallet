// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.crypto.bip39

import com.midnight.kuira.core.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Edge case tests for BIP-39 implementation.
 *
 * Tests handling of:
 * - Whitespace variations
 * - Case sensitivity
 * - All word counts (12, 15, 18, 21, 24)
 * - Passphrase encoding
 * - Empty strings
 */
class BIP39EdgeCaseTest {

    // ========== Whitespace Handling ==========

    @Test
    fun `given mnemonic with multiple spaces when validating then returns true`() {
        // Given - extra spaces between words
        val mnemonic = "abandon  abandon  abandon  abandon  abandon  abandon  " +
                "abandon  abandon  abandon  abandon  abandon  abandon  " +
                "abandon  abandon  abandon  abandon  abandon  abandon  " +
                "abandon  abandon  abandon  abandon  abandon  art"

        // When
        val isValid = BIP39.validateMnemonic(mnemonic)

        // Then - should handle multiple spaces gracefully
        assertTrue(isValid)
    }

    @Test
    fun `given mnemonic with tabs when validating then returns true`() {
        // Given - tabs between words
        val mnemonic = "abandon\tabandon\tabandon\tabandon\tabandon\tabandon\t" +
                "abandon\tabandon\tabandon\tabandon\tabandon\tabandon\t" +
                "abandon\tabandon\tabandon\tabandon\tabandon\tabandon\t" +
                "abandon\tabandon\tabandon\tabandon\tabandon\tart"

        // When
        val isValid = BIP39.validateMnemonic(mnemonic)

        // Then - tabs should be treated as whitespace
        assertTrue(isValid)
    }

    @Test
    fun `given mnemonic with newlines when validating then returns true`() {
        // Given - newlines between words
        val mnemonic = """
            abandon abandon abandon abandon abandon abandon
            abandon abandon abandon abandon abandon abandon
            abandon abandon abandon abandon abandon abandon
            abandon abandon abandon abandon abandon art
        """.trimIndent()

        // When
        val isValid = BIP39.validateMnemonic(mnemonic)

        // Then - newlines should be treated as whitespace
        assertTrue(isValid)
    }

    @Test
    fun `given mnemonic with leading and trailing whitespace when validating then returns true`() {
        // Given - leading and trailing whitespace
        val mnemonic = "   ${TestFixtures.TEST_MNEMONIC_24_WORDS}   "

        // When
        val isValid = BIP39.validateMnemonic(mnemonic)

        // Then - leading/trailing whitespace should be trimmed
        assertTrue(isValid)
    }

    @Test
    fun `given mnemonic with mixed whitespace when deriving seed then matches expected`() {
        // Given - mixed tabs, spaces, newlines
        val messyMnemonic = """
            abandon  abandon	abandon
            abandon abandon  abandon
            abandon	abandon abandon
            abandon abandon  abandon
            abandon abandon  abandon
            abandon abandon  abandon
            abandon abandon  abandon
            abandon abandon  art
        """.trimIndent()

        val cleanMnemonic = TestFixtures.TEST_MNEMONIC_24_WORDS

        // When
        val messySeed = BIP39.mnemonicToSeed(messyMnemonic, "TREZOR")
        val cleanSeed = BIP39.mnemonicToSeed(cleanMnemonic, "TREZOR")

        // Then - both should produce same seed
        assertEquals(cleanSeed.toHex(), messySeed.toHex())
    }

    // ========== Case Sensitivity ==========

    @Test
    fun `given uppercase mnemonic when validating then returns false`() {
        // Given - all uppercase (BIP-39 wordlist is lowercase only)
        val mnemonic = TestFixtures.TEST_MNEMONIC_24_WORDS.uppercase()

        // When
        val isValid = BIP39.validateMnemonic(mnemonic)

        // Then - BIP-39 wordlist is case-sensitive (lowercase only)
        assertFalse("Uppercase mnemonic should be invalid", isValid)
    }

    @Test
    fun `given mixed case mnemonic when validating then returns false`() {
        // Given - mixed case (BIP-39 wordlist is lowercase only)
        val mnemonic = "AbanDon aBaNdOn ABANDON abandon Abandon abandon " +
                "abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon ART"

        // When
        val isValid = BIP39.validateMnemonic(mnemonic)

        // Then - should reject mixed case (BIP-39 wordlist is lowercase only)
        assertFalse("Mixed case mnemonic should be invalid", isValid)
    }

    @Test
    fun `given lowercase mnemonic when validating then returns true`() {
        // Given - all lowercase (correct format)
        val mnemonic = TestFixtures.TEST_MNEMONIC_24_WORDS.lowercase()

        // When
        val isValid = BIP39.validateMnemonic(mnemonic)

        // Then - lowercase is the correct format
        assertTrue("Lowercase mnemonic should be valid", isValid)
    }

    // ========== All Word Counts ==========

    @Test
    fun `when generating 15-word mnemonic then produces valid phrase`() {
        // When
        val mnemonic = BIP39.generateMnemonic(wordCount = 15)

        // Then
        val words = mnemonic.split(" ")
        assertEquals(15, words.size)
        assertTrue(BIP39.validateMnemonic(mnemonic))
    }

    @Test
    fun `when generating 18-word mnemonic then produces valid phrase`() {
        // When
        val mnemonic = BIP39.generateMnemonic(wordCount = 18)

        // Then
        val words = mnemonic.split(" ")
        assertEquals(18, words.size)
        assertTrue(BIP39.validateMnemonic(mnemonic))
    }

    @Test
    fun `when generating 21-word mnemonic then produces valid phrase`() {
        // When
        val mnemonic = BIP39.generateMnemonic(wordCount = 21)

        // Then
        val words = mnemonic.split(" ")
        assertEquals(21, words.size)
        assertTrue(BIP39.validateMnemonic(mnemonic))
    }

    // ========== Passphrase Encoding ==========

    @Test
    fun `given passphrase with special characters when deriving seed then applies correctly`() {
        // Given
        val mnemonic = TestFixtures.TEST_MNEMONIC_24_WORDS
        val passphrase = "Test!@#$%^&*()_+-=[]{}|;:',.<>?"

        // When
        val seed1 = BIP39.mnemonicToSeed(mnemonic, passphrase)
        val seed2 = BIP39.mnemonicToSeed(mnemonic, passphrase)

        // Then - same passphrase should produce same seed
        assertEquals(seed1.toHex(), seed2.toHex())
    }

    @Test
    fun `given different passphrases when deriving seed then produces different seeds`() {
        // Given
        val mnemonic = TestFixtures.TEST_MNEMONIC_24_WORDS

        // When
        val seed1 = BIP39.mnemonicToSeed(mnemonic, "passphrase1")
        val seed2 = BIP39.mnemonicToSeed(mnemonic, "passphrase2")
        val seed3 = BIP39.mnemonicToSeed(mnemonic, "")

        // Then - different passphrases should produce different seeds
        assertNotEquals(seed1.toHex(), seed2.toHex())
        assertNotEquals(seed1.toHex(), seed3.toHex())
        assertNotEquals(seed2.toHex(), seed3.toHex())
    }

    @Test
    fun `given empty passphrase when deriving seed then produces deterministic output`() {
        // Given
        val mnemonic = TestFixtures.TEST_MNEMONIC_24_WORDS

        // When
        val seed1 = BIP39.mnemonicToSeed(mnemonic, "")
        val seed2 = BIP39.mnemonicToSeed(mnemonic) // Default is empty

        // Then - both should be identical
        assertEquals(seed1.toHex(), seed2.toHex())
    }

    @Test
    fun `given passphrase with unicode when deriving seed then applies correctly`() {
        // Given
        val mnemonic = TestFixtures.TEST_MNEMONIC_24_WORDS
        val passphrase = "🔐 Σ α β γ 你好"

        // When
        val seed1 = BIP39.mnemonicToSeed(mnemonic, passphrase)
        val seed2 = BIP39.mnemonicToSeed(mnemonic, passphrase)

        // Then - unicode passphrase should work deterministically
        assertEquals(seed1.toHex(), seed2.toHex())
    }

    // ========== Empty Strings & Edge Cases ==========

    @Test
    fun `given empty string when validating then returns false`() {
        // When
        val isValid = BIP39.validateMnemonic("")

        // Then
        assertFalse(isValid)
    }

    @Test
    fun `given only whitespace when validating then returns false`() {
        // When
        val isValid = BIP39.validateMnemonic("   \t  \n  ")

        // Then
        assertFalse(isValid)
    }

    @Test
    fun `given single word when validating then returns false`() {
        // When
        val isValid = BIP39.validateMnemonic("abandon")

        // Then
        assertFalse(isValid)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given invalid mnemonic when deriving seed then throws exception`() {
        // When/Then
        BIP39.mnemonicToSeed("invalid mnemonic with bad checksum")
    }

    // ========== Boundary Tests ==========

    @Test
    fun `given 11 words when validating then returns false`() {
        // Given - one word short of minimum
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"

        // When
        val isValid = BIP39.validateMnemonic(mnemonic)

        // Then
        assertFalse(isValid)
    }

    @Test
    fun `given 13 words when validating then returns false`() {
        // Given - between valid counts
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"

        // When
        val isValid = BIP39.validateMnemonic(mnemonic)

        // Then
        assertFalse(isValid)
    }

    @Test
    fun `given 25 words when validating then returns false`() {
        // Given - one word over maximum
        val mnemonic = TestFixtures.TEST_MNEMONIC_24_WORDS + " abandon"

        // When
        val isValid = BIP39.validateMnemonic(mnemonic)

        // Then
        assertFalse(isValid)
    }
}

/**
 * Extension function to convert ByteArray to hex string.
 */
private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
