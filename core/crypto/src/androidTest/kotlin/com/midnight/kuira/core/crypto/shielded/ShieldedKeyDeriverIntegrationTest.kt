// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.crypto.shielded

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android instrumentation tests for [ShieldedKeyDeriver] with actual native library.
 *
 * **Requirements:**
 * - Native library `libkuira_crypto_ffi.so` must be bundled in APK
 * - Library must be built for the target device architecture (arm64-v8a, armeabi-v7a, x86, x86_64)
 * - Rust FFI compiled against Midnight Ledger v6.1.0-alpha.5
 *
 * **Test Vectors:**
 * These use the standard BIP-39 test mnemonic:
 * "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
 *
 * Derived at path: `m/44'/2400'/0'/3/0` (Zswap role, index 0)
 *
 * Expected outputs match Midnight SDK (`@midnight-ntwrk/ledger-v6` v6.1.0-alpha.6)
 */
@RunWith(AndroidJUnit4::class)
class ShieldedKeyDeriverIntegrationTest {

    @Before
    fun setUp() {
        // Skip tests if native library not loaded
        assumeTrue(
            "Native library not loaded - skipping integration tests. " +
                    "Error: ${ShieldedKeyDeriver.getLoadError()}",
            ShieldedKeyDeriver.isLibraryLoaded()
        )
    }

    @Test
    fun testNativeLibraryLoaded() {
        assertTrue(
            "Native library should be loaded on Android",
            ShieldedKeyDeriver.isLibraryLoaded()
        )
        assertNull(
            "No load error should exist",
            ShieldedKeyDeriver.getLoadError()
        )
    }

    @Test
    fun testDeriveKeysWithTestVector() {
        // Test vector: shielded seed derived from "abandon abandon ... art" at m/44'/2400'/0'/3/0
        val shieldedSeed = hexToBytes("b7637860b12f892ee07c67ad441c7935e37ac2153cefa39ae79083284f6d9180")

        val keys = ShieldedKeyDeriver.deriveKeys(shieldedSeed)

        assertNotNull("Keys should be derived successfully", keys)
        keys!!

        // Expected values from Midnight SDK v6.1.0-alpha.6
        assertEquals(
            "Coin public key should match Midnight SDK output",
            "274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a",
            keys.coinPublicKey
        )
        assertEquals(
            "Encryption public key should match Midnight SDK output",
            "f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b",
            keys.encryptionPublicKey
        )
    }

    @Test
    fun testDeriveKeysMultipleTimes() {
        // Derive the same seed multiple times - should produce identical results
        val seed = hexToBytes("b7637860b12f892ee07c67ad441c7935e37ac2153cefa39ae79083284f6d9180")

        val keys1 = ShieldedKeyDeriver.deriveKeys(seed)
        val keys2 = ShieldedKeyDeriver.deriveKeys(seed)
        val keys3 = ShieldedKeyDeriver.deriveKeys(seed)

        assertNotNull(keys1)
        assertNotNull(keys2)
        assertNotNull(keys3)

        // All should be identical (deterministic)
        assertEquals(keys1, keys2)
        assertEquals(keys2, keys3)
    }

    @Test
    fun testDeriveKeysWithDifferentSeeds() {
        // Different seeds should produce different keys
        val seed1 = hexToBytes("b7637860b12f892ee07c67ad441c7935e37ac2153cefa39ae79083284f6d9180")
        val seed2 = hexToBytes("b7637860b12f892ee07c67ad441c7935e37ac2153cefa39ae79083284f6d9181") // Last byte different

        val keys1 = ShieldedKeyDeriver.deriveKeys(seed1)
        val keys2 = ShieldedKeyDeriver.deriveKeys(seed2)

        assertNotNull(keys1)
        assertNotNull(keys2)

        // Keys should be completely different
        assertNotEquals(keys1!!.coinPublicKey, keys2!!.coinPublicKey)
        assertNotEquals(keys1.encryptionPublicKey, keys2.encryptionPublicKey)
    }

    @Test
    fun testDeriveKeysDoesNotModifySeed() {
        val originalSeed = hexToBytes("b7637860b12f892ee07c67ad441c7935e37ac2153cefa39ae79083284f6d9180")
        val seedCopy = originalSeed.copyOf()

        ShieldedKeyDeriver.deriveKeys(originalSeed)

        // Seed should not be modified
        assertArrayEquals(
            "Seed should not be modified by deriveKeys()",
            seedCopy,
            originalSeed
        )
    }

    @Test
    fun testDeriveKeysWithAllZeroSeed() {
        // Edge case: all-zero seed
        val zeroSeed = ByteArray(32) { 0 }

        val keys = ShieldedKeyDeriver.deriveKeys(zeroSeed)

        assertNotNull("Should derive keys even for all-zero seed", keys)
        // Keys should be valid hex strings
        assertTrue(keys!!.coinPublicKey.matches(Regex("^[0-9a-f]{64}$")))
        assertTrue(keys.encryptionPublicKey.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun testDeriveKeysWithAllFFSeed() {
        // Edge case: all-FF seed
        val ffSeed = ByteArray(32) { 0xFF.toByte() }

        val keys = ShieldedKeyDeriver.deriveKeys(ffSeed)

        assertNotNull("Should derive keys even for all-FF seed", keys)
        assertTrue(keys!!.coinPublicKey.matches(Regex("^[0-9a-f]{64}$")))
        assertTrue(keys.encryptionPublicKey.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun testMemoryWipingAfterDerivation() {
        val seed = hexToBytes("b7637860b12f892ee07c67ad441c7935e37ac2153cefa39ae79083284f6d9180")

        val keys = MemoryUtils.useAndWipe(seed) { seedBytes ->
            ShieldedKeyDeriver.deriveKeys(seedBytes)
        }

        // Verify keys were derived
        assertNotNull(keys)
        assertEquals(
            "274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a",
            keys!!.coinPublicKey
        )

        // Verify seed was wiped
        assertArrayEquals(
            "Seed should be wiped after use",
            ByteArray(32) { 0 },
            seed
        )
    }

    @Test
    fun testConcurrentDerivations() {
        // Test thread safety - derive keys concurrently
        val seed = hexToBytes("b7637860b12f892ee07c67ad441c7935e37ac2153cefa39ae79083284f6d9180")
        val expectedCoinPk = "274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a"

        val threads = List(10) { threadIndex ->
            Thread {
                repeat(5) {
                    val keys = ShieldedKeyDeriver.deriveKeys(seed)
                    assertNotNull("Thread $threadIndex: Keys should not be null", keys)
                    assertEquals(
                        "Thread $threadIndex: Coin PK should be deterministic",
                        expectedCoinPk,
                        keys!!.coinPublicKey
                    )
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join(5000) } // Wait max 5 seconds

        assertTrue("All threads should complete", threads.all { !it.isAlive })
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDeriveKeysWithWrongSeedSize() {
        val wrongSizeSeed = ByteArray(16) { it.toByte() } // Should be 32

        ShieldedKeyDeriver.deriveKeys(wrongSizeSeed)
    }

    // Helper function to convert hex string to bytes
    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
