// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.crypto.shielded

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [ShieldedKeyDeriver] JNI wrapper.
 *
 * **Note:** These tests run WITHOUT the native library loaded (JVM unit tests).
 * For integration tests WITH the native library, see:
 * - `ShieldedKeyDeriverIntegrationTest` (Android instrumentation tests)
 * - `MidnightKeyDerivationTest` (HD wallet integration)
 */
class ShieldedKeyDeriverTest {

    @Test
    fun `given 32-byte seed when deriving without native library then returns null`() {
        val seed = ByteArray(32) { it.toByte() }

        val result = ShieldedKeyDeriver.deriveKeys(seed)

        // Should return null because native library is not loaded in unit tests
        assertNull(result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given seed with wrong size when deriving then throws`() {
        val seed = ByteArray(16) { it.toByte() } // Wrong size

        ShieldedKeyDeriver.deriveKeys(seed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given empty seed when deriving then throws`() {
        val seed = ByteArray(0)

        ShieldedKeyDeriver.deriveKeys(seed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given 64-byte seed when deriving then throws`() {
        val seed = ByteArray(64) { it.toByte() } // BIP-39 seed size, not shielded seed

        ShieldedKeyDeriver.deriveKeys(seed)
    }

    @Test
    fun `when checking library loaded status then returns false in unit tests`() {
        // Native library should not be loaded in JVM unit tests
        assertFalse(ShieldedKeyDeriver.isLibraryLoaded())
    }

    @Test
    fun `when getting load error then returns non-null error message`() {
        // Should have an error message explaining why it's not loaded
        val error = ShieldedKeyDeriver.getLoadError()

        assertNotNull(error)
        assertTrue(error!!.contains("kuira_crypto_ffi"))
    }

    @Test
    fun `given seed when deriving then does not modify seed array`() {
        val originalSeed = byteArrayOf(
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
            0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
            0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
        )
        val seedCopy = originalSeed.copyOf()

        ShieldedKeyDeriver.deriveKeys(originalSeed)

        // Seed should NOT be modified by deriveKeys()
        assertArrayEquals(seedCopy, originalSeed)
    }
}
