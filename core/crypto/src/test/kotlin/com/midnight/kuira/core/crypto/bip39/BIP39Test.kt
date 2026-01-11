package com.midnight.kuira.core.crypto.bip39

import com.midnight.kuira.core.testing.TestFixtures
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test BIP-39 implementation with official test vectors.
 *
 * Test vectors from: https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki
 */
class BIP39Test {

    @Test
    fun `given valid 24-word mnemonic when deriving seed then matches test vector`() {
        // Given
        val mnemonic = TestFixtures.TEST_MNEMONIC_24_WORDS
        val expectedSeed = TestFixtures.EXPECTED_SEED_HEX

        // When
        val actualSeed = BIP39.mnemonicToSeed(mnemonic)

        // Then
        assertEquals(expectedSeed, actualSeed.toHex())
    }

    @Test
    fun `given valid mnemonic when validating then returns true`() {
        // Given
        val mnemonic = TestFixtures.TEST_MNEMONIC_24_WORDS

        // When
        val isValid = BIP39.validateMnemonic(mnemonic)

        // Then
        assertTrue(isValid)
    }

    @Test
    fun `given generated mnemonic when validating then returns true`() {
        // Given
        val mnemonic = BIP39.generateMnemonic()

        // When
        val isValid = BIP39.validateMnemonic(mnemonic)

        // Then
        assertTrue(isValid)
    }

    @Test
    fun `given 24-word mnemonic when generating seed then produces 64 bytes`() {
        // Given
        val mnemonic = TestFixtures.TEST_MNEMONIC_24_WORDS

        // When
        val seed = BIP39.mnemonicToSeed(mnemonic)

        // Then
        assertEquals(64, seed.size)
    }
}

/**
 * Extension function to convert ByteArray to hex string for comparison with test vectors.
 */
private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
