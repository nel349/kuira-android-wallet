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
 * Android integration test for generating official Midnight shielded addresses.
 *
 * **Purpose:**
 * Demonstrates end-to-end flow:
 * 1. Generate mnemonic (BIP-39)
 * 2. Derive shielded seed (BIP-32 at m/44'/2400'/0'/3/0)
 * 3. Derive shielded keys (JNI → Rust FFI)
 * 4. Format as official Midnight addresses (Bech32m encoding)
 *
 * **Networks:**
 * - **Test:** Official Midnight testnet (`mn_shield-cpk_test1...`)
 * - **Dev:** Development network (`mn_shield-cpk_dev1...`)
 * - **Undeployed:** Undeployed network (`mn_shield-cpk_undeployed1...`)
 * - **Mainnet:** Production (no network suffix) (`mn_shield-cpk1...`)
 *
 * **Reference:**
 * - Midnight SDK: @midnight-ntwrk/wallet-sdk-address-format
 * - Test vectors: midnight-libraries/midnight-wallet/packages/address-format/test/addresses.json
 */
@RunWith(AndroidJUnit4::class)
class ShieldedAddressGenerationTest {

    @Before
    fun setUp() {
        // Skip tests if native library not loaded
        assumeTrue(
            "Native library not loaded - skipping shielded address generation tests",
            ShieldedKeyDeriver.isLibraryLoaded()
        )
    }

    @Test
    fun generateShieldedAddressForTestNetwork() {
        val networkId = "test"

        // Use standard test mnemonic for reproducibility
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        val bip39Seed = BIP39.mnemonicToSeed(mnemonic)

        MemoryUtils.useAndWipe(bip39Seed) { seed ->
            val hdWallet = HDWallet.fromSeed(seed)
            try {
                // Derive shielded keys at m/44'/2400'/0'/3/0
                val shieldedKey = hdWallet
                    .selectAccount(0)
                    .selectRole(MidnightKeyRole.ZSWAP)
                    .deriveKeyAt(0)

                try {
                    // Derive shielded keys via JNI
                    val shieldedKeys = ShieldedKeyDeriver.deriveKeys(shieldedKey.privateKeyBytes)
                    assertNotNull("Shielded keys should be derived", shieldedKeys)

                    // Convert hex to bytes for Bech32m encoding
                    val coinPkBytes = hexToBytes(shieldedKeys!!.coinPublicKey)
                    val encPkBytes = hexToBytes(shieldedKeys.encryptionPublicKey)

                    // Full shielded address = coin PK + encryption PK (32 + 32 = 64 bytes)
                    val fullAddressBytes = coinPkBytes + encPkBytes

                    // Encode as Bech32m address with network
                    val hrp = "mn_shield-addr_$networkId"
                    val address = Bech32m.encode(hrp, fullAddressBytes)

                    // Expected from Lace-compatible Midnight TypeScript SDK (32-byte seed)
                    val expectedAddress = "mn_shield-addr_test1p8p0d7z86plp50awec642lh0t2q3fqvernhsdz05067fps9tjhm435xrcnpvd08mcd57q8qa3ya8myueya3yqldw5jj9wn9u0manz4cewjpgr"

                    assertEquals("Address should match TypeScript SDK", expectedAddress, address)

                    // Print for manual verification
                    println("=== TEST NETWORK SHIELDED ADDRESS ===")
                    println("Network: $networkId")
                    println("Coin Public Key: ${shieldedKeys.coinPublicKey}")
                    println("Encryption Public Key: ${shieldedKeys.encryptionPublicKey}")
                    println("Address: $address")
                    println("Expected: $expectedAddress")
                    println("Match: ${address == expectedAddress}")
                    println("=====================================")

                    // Verify round-trip decode
                    val (decodedHrp, decodedData) = Bech32m.decode(address)
                    assertEquals("HRP should match", hrp, decodedHrp)
                    assertArrayEquals("Decoded data should match", fullAddressBytes, decodedData)

                } finally {
                    shieldedKey.clear()
                }
            } finally {
                hdWallet.clear()
            }
        }
    }

    @Test
    fun generateShieldedAddressForDevNetwork() {
        val networkId = "dev"

        // Use standard test mnemonic for reproducibility
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        val bip39Seed = BIP39.mnemonicToSeed(mnemonic)

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

                    val hrp = "mn_shield-addr_$networkId"
                    val address = Bech32m.encode(hrp, fullAddressBytes)

                    // Expected from Lace-compatible Midnight TypeScript SDK (32-byte seed)
                    val expectedCoinPk = "09c2f6f847d07e1a3faece35557eef5a811481991cef0689f47ebc90c0ab95f7"
                    val expectedEncPk = "58d0c3c4c2c6bcfbc369e01c1d893a7d93992762407daea4a4574cbc7efb3157"
                    val expectedAddress = "mn_shield-addr_dev1p8p0d7z86plp50awec642lh0t2q3fqvernhsdz05067fps9tjhm435xrcnpvd08mcd57q8qa3ya8myueya3yqldw5jj9wn9u0manz4cl6vd04"

                    assertEquals("Coin PK should match TypeScript SDK", expectedCoinPk, shieldedKeys.coinPublicKey)
                    assertEquals("Enc PK should match TypeScript SDK", expectedEncPk, shieldedKeys.encryptionPublicKey)
                    assertEquals("Address should match TypeScript SDK", expectedAddress, address)

                    println("=== DEV NETWORK SHIELDED ADDRESS ===")
                    println("Network: $networkId")
                    println("Coin Public Key: ${shieldedKeys.coinPublicKey}")
                    println("Encryption Public Key: ${shieldedKeys.encryptionPublicKey}")
                    println("Address: $address")
                    println("Expected: $expectedAddress")
                    println("Match: ${address == expectedAddress}")
                    println("====================================")

                } finally {
                    shieldedKey.clear()
                }
            } finally {
                hdWallet.clear()
            }
        }
    }

    @Test
    fun generateShieldedAddressForUndeployedNetwork() {
        val networkId = "undeployed"

        // Use standard test mnemonic
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        val bip39Seed = BIP39.mnemonicToSeed(mnemonic)

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

                    val hrp = "mn_shield-addr_$networkId"
                    val address = Bech32m.encode(hrp, fullAddressBytes)

                    // Expected from Lace-compatible Midnight TypeScript SDK (32-byte seed)
                    val expectedAddress = "mn_shield-addr_undeployed1p8p0d7z86plp50awec642lh0t2q3fqvernhsdz05067fps9tjhm435xrcnpvd08mcd57q8qa3ya8myueya3yqldw5jj9wn9u0manz4cl6ysq7"

                    assertEquals("Address should match TypeScript SDK", expectedAddress, address)

                    println("=== UNDEPLOYED NETWORK SHIELDED ADDRESS ===")
                    println("Network: $networkId")
                    println("Coin Public Key: ${shieldedKeys.coinPublicKey}")
                    println("Encryption Public Key: ${shieldedKeys.encryptionPublicKey}")
                    println("Address: $address")
                    println("Expected: $expectedAddress")
                    println("Match: ${address == expectedAddress}")
                    println("===========================================")

                } finally {
                    shieldedKey.clear()
                }
            } finally {
                hdWallet.clear()
            }
        }
    }

    @Test
    fun generateShieldedAddressForMainnet() {
        // Mainnet has NO network suffix - just "mn_shield-addr1..."

        // Use standard test mnemonic
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        val bip39Seed = BIP39.mnemonicToSeed(mnemonic)

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

                    val hrp = "mn_shield-addr"  // NO network suffix for mainnet
                    val address = Bech32m.encode(hrp, fullAddressBytes)

                    // Expected from Lace-compatible Midnight TypeScript SDK (32-byte seed)
                    val expectedAddress = "mn_shield-addr1p8p0d7z86plp50awec642lh0t2q3fqvernhsdz05067fps9tjhm435xrcnpvd08mcd57q8qa3ya8myueya3yqldw5jj9wn9u0manz4ctsgm4w"

                    assertEquals("Address should match TypeScript SDK", expectedAddress, address)

                    println("=== MAINNET SHIELDED ADDRESS ===")
                    println("Network: mainnet (no suffix)")
                    println("Coin Public Key: ${shieldedKeys.coinPublicKey}")
                    println("Encryption Public Key: ${shieldedKeys.encryptionPublicKey}")
                    println("Address: $address")
                    println("Expected: $expectedAddress")
                    println("Match: ${address == expectedAddress}")
                    println("================================")

                } finally {
                    shieldedKey.clear()
                }
            } finally {
                hdWallet.clear()
            }
        }
    }

    @Test
    fun verifyAllNetworksProduceDifferentAddresses() {
        // Same mnemonic and keys, but different network prefixes → different addresses

        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        val bip39Seed = BIP39.mnemonicToSeed(mnemonic)

        val addresses = mutableListOf<String>()
        val networks = listOf("test", "dev", "undeployed", null) // null = mainnet

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

                    networks.forEach { networkId ->
                        val hrp = if (networkId == null) {
                            "mn_shield-addr"  // Mainnet
                        } else {
                            "mn_shield-addr_$networkId"
                        }

                        val address = Bech32m.encode(hrp, fullAddressBytes)
                        addresses.add(address)

                        println("Network: ${networkId ?: "mainnet"} → $address")
                    }

                } finally {
                    shieldedKey.clear()
                }
            } finally {
                hdWallet.clear()
            }
        }

        // Verify all addresses are different (different checksums due to different HRPs)
        assertEquals("Should generate 4 addresses", 4, addresses.size)
        assertEquals("All addresses should be unique", addresses.size, addresses.toSet().size)
    }

    // Helper function to convert hex string to bytes
    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
