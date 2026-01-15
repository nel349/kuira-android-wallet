// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.crypto.shielded

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midnight.kuira.core.crypto.address.Bech32m
import com.midnight.kuira.core.crypto.bip32.HDWallet
import com.midnight.kuira.core.crypto.bip32.MidnightKeyRole
import com.midnight.kuira.core.crypto.bip39.BIP39
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ⚠️ CRITICAL: LACE WALLET COMPATIBILITY TESTS ⚠️
 *
 * These tests verify that Kuira Wallet generates IDENTICAL addresses to Lace wallet
 * for the same mnemonic phrase.
 *
 * ## Why This Matters
 *
 * Lace is the most popular Midnight wallet. Users expect to be able to:
 * - Export their mnemonic from Lace → Import into Kuira → Get same addresses
 * - Export their mnemonic from Kuira → Import into Lace → Get same addresses
 *
 * ## The Non-Standard Behavior
 *
 * Lace uses ONLY the first 32 bytes of the BIP-39 seed (not the standard 64 bytes).
 * This was confirmed by the Lace team in GitHub issue #2133.
 *
 * Our implementation follows Lace's approach to ensure compatibility.
 *
 * ## Test Vectors Source
 *
 * These test vectors were generated using:
 * 1. The official Lace wallet (https://www.lace.io/)
 * 2. Midnight TypeScript SDK with 32-byte truncated seed
 * 3. Verification script: `generate-lace-addresses-all-networks.mjs` (see kuira-verification-test repo)
 *
 * ## References
 *
 * - **Full Documentation**: `docs/LACE_COMPATIBILITY.md`
 * - **Lace GitHub Issue**: https://github.com/input-output-hk/lace/issues/2133
 * - **Test Mnemonic**: "abandon abandon abandon ... art" (BIP-39 standard test mnemonic)
 *
 * @see com.midnight.kuira.core.crypto.bip39.BIP39.mnemonicToSeed
 */
@RunWith(AndroidJUnit4::class)
class LaceCompatibilityTest {

    @Before
    fun setUp() {
        assumeTrue(
            "Native library not loaded - skipping Lace compatibility tests",
            ShieldedKeyDeriver.isLibraryLoaded()
        )
    }

    /**
     * Verifies that using the standard BIP-39 test mnemonic produces addresses
     * that match Lace wallet EXACTLY.
     *
     * **Test Mnemonic**: "abandon abandon abandon ... art"
     * **Source**: BIP-39 specification (standard test vector)
     * **Verification**: Confirmed with actual Lace wallet
     */
    @Test
    fun verifyLaceCompatibility_PreviewNetwork() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        val bip39Seed = BIP39.mnemonicToSeed(mnemonic)

        // Verify seed is 32 bytes (Lace standard, not 64 bytes like standard BIP-39)
        assertEquals("Seed should be 32 bytes for Lace compatibility", 32, bip39Seed.size)

        MemoryUtils.useAndWipe(bip39Seed) { seed ->
            val hdWallet = HDWallet.fromSeed(seed)
            try {
                val shieldedKey = hdWallet
                    .selectAccount(0)
                    .selectRole(MidnightKeyRole.ZSWAP)
                    .deriveKeyAt(0)

                try {
                    val shieldedKeys = ShieldedKeyDeriver.deriveKeys(shieldedKey.privateKeyBytes)
                    assertNotNull(shieldedKeys)

                    val coinPkBytes = hexToBytes(shieldedKeys!!.coinPublicKey)
                    val encPkBytes = hexToBytes(shieldedKeys.encryptionPublicKey)
                    val fullAddressBytes = coinPkBytes + encPkBytes

                    val address = Bech32m.encode("mn_shield-addr_preview", fullAddressBytes)

                    // Expected address from Lace wallet
                    val laceAddress = "mn_shield-addr_preview1p8p0d7z86plp50awec642lh0t2q3fqvernhsdz05067fps9tjhm435xrcnpvd08mcd57q8qa3ya8myueya3yqldw5jj9wn9u0manz4cenlp3y"

                    assertEquals("Address MUST match Lace wallet", laceAddress, address)

                    println("✅ LACE COMPATIBILITY VERIFIED")
                    println("Lace Address:  $laceAddress")
                    println("Kuira Address: $address")
                    println("Match: ${address == laceAddress}")

                } finally {
                    shieldedKey.clear()
                }
            } finally {
                hdWallet.clear()
            }
        }
    }

    /**
     * Verifies compatibility across ALL major Midnight networks.
     *
     * This ensures that Kuira-generated wallets work seamlessly with Lace
     * regardless of which network the user is on.
     */
    @Test
    fun verifyLaceCompatibility_AllNetworks() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        val bip39Seed = BIP39.mnemonicToSeed(mnemonic)

        val expectedAddresses = mapOf(
            "test" to "mn_shield-addr_test1p8p0d7z86plp50awec642lh0t2q3fqvernhsdz05067fps9tjhm435xrcnpvd08mcd57q8qa3ya8myueya3yqldw5jj9wn9u0manz4cewjpgr",
            "dev" to "mn_shield-addr_dev1p8p0d7z86plp50awec642lh0t2q3fqvernhsdz05067fps9tjhm435xrcnpvd08mcd57q8qa3ya8myueya3yqldw5jj9wn9u0manz4cl6vd04",
            "preview" to "mn_shield-addr_preview1p8p0d7z86plp50awec642lh0t2q3fqvernhsdz05067fps9tjhm435xrcnpvd08mcd57q8qa3ya8myueya3yqldw5jj9wn9u0manz4cenlp3y",
            "undeployed" to "mn_shield-addr_undeployed1p8p0d7z86plp50awec642lh0t2q3fqvernhsdz05067fps9tjhm435xrcnpvd08mcd57q8qa3ya8myueya3yqldw5jj9wn9u0manz4cl6ysq7",
            "mainnet" to "mn_shield-addr1p8p0d7z86plp50awec642lh0t2q3fqvernhsdz05067fps9tjhm435xrcnpvd08mcd57q8qa3ya8myueya3yqldw5jj9wn9u0manz4ctsgm4w"
        )

        MemoryUtils.useAndWipe(bip39Seed) { seed ->
            val hdWallet = HDWallet.fromSeed(seed)
            try {
                val shieldedKey = hdWallet
                    .selectAccount(0)
                    .selectRole(MidnightKeyRole.ZSWAP)
                    .deriveKeyAt(0)

                try {
                    val shieldedKeys = ShieldedKeyDeriver.deriveKeys(shieldedKey.privateKeyBytes)
                    assertNotNull(shieldedKeys)

                    val coinPkBytes = hexToBytes(shieldedKeys!!.coinPublicKey)
                    val encPkBytes = hexToBytes(shieldedKeys.encryptionPublicKey)
                    val fullAddressBytes = coinPkBytes + encPkBytes

                    expectedAddresses.forEach { (network, expectedAddress) ->
                        val hrp = if (network == "mainnet") {
                            "mn_shield-addr"
                        } else {
                            "mn_shield-addr_$network"
                        }

                        val address = Bech32m.encode(hrp, fullAddressBytes)

                        assertEquals(
                            "Address for $network MUST match Lace",
                            expectedAddress,
                            address
                        )

                        println("✅ $network: MATCH")
                    }

                } finally {
                    shieldedKey.clear()
                }
            } finally {
                hdWallet.clear()
            }
        }

        println("")
        println("✅ ALL NETWORKS: LACE COMPATIBILITY VERIFIED")
    }

    /**
     * This test documents what happens if we use the FULL 64-byte BIP-39 seed
     * (standard behavior) instead of Lace's 32-byte truncated seed.
     *
     * **Result**: Completely different addresses (wallet incompatibility)
     *
     * This test exists to document WHY we truncate the seed and what happens
     * if someone tries to "fix" it back to standard BIP-39.
     */
    @Test
    fun documentNonCompatibility_Full64ByteSeed() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"

        // Simulate what would happen if we used full 64-byte seed
        val service = com.midnight.kuira.core.crypto.bip39.BitcoinJMnemonicService()
        val full64ByteSeed = service.mnemonicToSeed(mnemonic, "")  // Standard BIP-39: 64 bytes

        assertEquals("Standard BIP-39 produces 64 bytes", 64, full64ByteSeed.size)

        // Our actual implementation truncates to 32 bytes
        val our32ByteSeed = BIP39.mnemonicToSeed(mnemonic)
        assertEquals("Our implementation produces 32 bytes", 32, our32ByteSeed.size)

        // The first 32 bytes should match
        assertArrayEquals(
            "First 32 bytes should be identical",
            full64ByteSeed.copyOfRange(0, 32),
            our32ByteSeed
        )

        println("")
        println("⚠️  DOCUMENTATION: Why We Truncate Seeds")
        println("Full BIP-39 Seed: ${full64ByteSeed.size} bytes")
        println("Our Truncated Seed: ${our32ByteSeed.size} bytes")
        println("Reason: Lace wallet compatibility")
        println("See: docs/LACE_COMPATIBILITY.md")
    }

    // Helper function
    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
