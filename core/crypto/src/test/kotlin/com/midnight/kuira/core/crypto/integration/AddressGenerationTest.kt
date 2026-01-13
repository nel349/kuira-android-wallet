// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.crypto.integration

import com.midnight.kuira.core.crypto.address.Bech32m
import com.midnight.kuira.core.crypto.bip32.HDWallet
import com.midnight.kuira.core.crypto.bip32.MidnightKeyRole
import com.midnight.kuira.core.crypto.bip39.BIP39
import org.junit.After
import org.junit.Test
import java.security.MessageDigest
import java.util.Arrays

/**
 * Integration test for generating Midnight blockchain addresses.
 *
 * **Purpose:**
 * - Generate a complete unshielded address from mnemonic
 * - Print address for manual verification (send Night tokens to it)
 * - Verify the address format matches Midnight SDK
 *
 * **Midnight Unshielded Address Format:**
 * ```
 * Mnemonic (BIP-39)
 *   → Seed (64 bytes)
 *   → HD Wallet (BIP-32)
 *   → Private Key at m/44'/2400'/0'/0/0 (32 bytes)
 *   → Public Key - BIP-340 format (x-only, 32 bytes)
 *   → Bech32m encoding: mn_addr_[network]1...
 * ```
 *
 * **Reference:**
 * - Midnight SDK: @midnight-ntwrk/wallet-sdk-address-format
 * - BIP-340: Schnorr signatures (x-only public keys)
 */
class AddressGenerationTest {

    private val walletsToClean = mutableListOf<HDWallet>()

    @After
    fun cleanup() {
        walletsToClean.forEach { it.clear() }
        walletsToClean.clear()
    }

    /**
     * Generates an unshielded address from a test mnemonic.
     *
     * **IMPORTANT:** This prints the address so you can manually verify it
     * by sending Night tokens to it on the Midnight testnet.
     */
    @Test
    fun `generate unshielded address from test mnemonic`() {
        // Use standard BIP-39 test mnemonic
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon abandon art"

        println("\n" + "=".repeat(80))
        println("MIDNIGHT UNSHIELDED ADDRESS GENERATION TEST")
        println("=".repeat(80))
        println()
        println("Mnemonic (24 words):")
        println("  ${mnemonic.split(" ").take(12).joinToString(" ")}")
        println("  ${mnemonic.split(" ").drop(12).joinToString(" ")}")
        println()

        // Step 1: Generate seed from mnemonic
        val seed = BIP39.mnemonicToSeed(mnemonic, passphrase = "")
        try {
            val seedHex = seed.take(32).joinToString("") { "%02x".format(it) }
            println("Seed (first 32 bytes):")
            println("  $seedHex")
            println()

            // Step 2: Create HD wallet
            val wallet = HDWallet.fromSeed(seed)
            walletsToClean.add(wallet)

            // Step 3: Derive key at m/44'/2400'/0'/0/0 (NightExternal, index 0)
            val derivedKey = wallet
                .selectAccount(0)
                .selectRole(MidnightKeyRole.NIGHT_EXTERNAL)
                .deriveKeyAt(0)

            @Suppress("DEPRECATION")
            val privateKeyHex = derivedKey.privateKeyHexDebugOnly()
            println("Derived Key at m/44'/2400'/0'/0/0:")
            println("  Path: ${derivedKey.path}")
            println("  Private Key: $privateKeyHex")
            println()

            // Step 4: Get BIP-340 public key (x-only, 32 bytes)
            // BitcoinJ returns compressed public key (33 bytes with prefix)
            // Format: [prefix byte][x-coordinate (32 bytes)]
            // For BIP-340, we need only the x-coordinate (strip prefix)
            val compressedPublicKey = derivedKey.publicKeyBytes
            println("Compressed Public Key (33 bytes with prefix):")
            println("  ${compressedPublicKey.joinToString("") { "%02x".format(it) }}")
            println()

            require(compressedPublicKey.size == 33) {
                "Expected 33-byte compressed public key, got ${compressedPublicKey.size} bytes"
            }
            require(compressedPublicKey[0] == 0x02.toByte() || compressedPublicKey[0] == 0x03.toByte()) {
                "Invalid compressed public key prefix: ${compressedPublicKey[0]}"
            }

            // Extract x-only coordinate (32 bytes) for BIP-340
            val xOnlyPublicKey = compressedPublicKey.copyOfRange(1, 33)
            println("BIP-340 Public Key (x-only, 32 bytes):")
            println("  ${xOnlyPublicKey.joinToString("") { "%02x".format(it) }}")
            println()

            // Step 5: Hash public key with SHA-256 (Midnight's persistent_hash)
            val addressData = MessageDigest.getInstance("SHA-256").digest(xOnlyPublicKey)
            println("Address Data (SHA-256 of public key, 32 bytes):")
            println("  ${addressData.joinToString("") { "%02x".format(it) }}")
            println()

            // Step 6: Encode with Bech32m
            // Format: mn_addr_[network]1...
            val networks = listOf(
                "preview" to "Testnet/Preview Network",
                "mainnet" to "Mainnet (use empty string for HRP)"
            )

            networks.forEach { (network, description) ->
                val hrp = if (network == "mainnet") "mn_addr" else "mn_addr_$network"
                val address = Bech32m.encode(hrp, addressData)

                println("$description:")
                println("  $address")
                println()
            }

            println("=".repeat(80))
            println("✅ ADDRESS GENERATED SUCCESSFULLY")
            println("=".repeat(80))
            println()
            println("📋 NEXT STEPS:")
            println("1. Copy the preview network address above")
            println("2. Go to Midnight faucet or use your wallet")
            println("3. Send Night tokens to this address")
            println("4. Verify the tokens arrive correctly")
            println()
            println("⚠️  SECURITY NOTE:")
            println("This is a TEST mnemonic. NEVER use this for real funds!")
            println("Everyone knows this mnemonic. Use it for testing only.")
            println()

            // Clean up
            derivedKey.clear()

        } finally {
            // Wipe seed from memory
            Arrays.fill(seed, 0.toByte())
        }
    }

    /**
     * Generates an unshielded address from a NEW random mnemonic.
     *
     * **IMPORTANT:** This generates a REAL mnemonic that you should save securely.
     * You can use this address to receive actual Night tokens.
     */
    @Test
    fun `generate unshielded address from new random mnemonic`() {
        // Generate a new 24-word mnemonic
        val mnemonic = BIP39.generateMnemonic(wordCount = 24)

        println("\n" + "=".repeat(80))
        println("NEW WALLET - UNSHIELDED ADDRESS GENERATION")
        println("=".repeat(80))
        println()
        println("⚠️  WARNING: SAVE THIS MNEMONIC SECURELY!")
        println("This is a REAL wallet. Write down these 24 words:")
        println()
        println("Mnemonic (24 words):")
        val words = mnemonic.split(" ")
        words.chunked(6).forEachIndexed { chunk, wordList ->
            val startNum = chunk * 6 + 1
            println("  ${startNum}-${startNum + 5}: ${wordList.joinToString(" ")}")
        }
        println()
        println("⚠️  Write these words down on paper. Keep them safe!")
        println()

        // Generate seed and address
        val seed = BIP39.mnemonicToSeed(mnemonic, passphrase = "")
        try {
            val wallet = HDWallet.fromSeed(seed)
            walletsToClean.add(wallet)

            val derivedKey = wallet
                .selectAccount(0)
                .selectRole(MidnightKeyRole.NIGHT_EXTERNAL)
                .deriveKeyAt(0)

            // Get BIP-340 public key
            val compressedPublicKey = derivedKey.publicKeyBytes
            val xOnlyPublicKey = compressedPublicKey.copyOfRange(1, 33)

            // Hash public key with SHA-256 (Midnight's persistent_hash)
            val addressData = MessageDigest.getInstance("SHA-256").digest(xOnlyPublicKey)

            // Encode for preview network (testnet)
            val previewAddress = Bech32m.encode("mn_addr_preview", addressData)

            println("Your Unshielded Address (Preview Network):")
            println("  $previewAddress")
            println()

            println("=".repeat(80))
            println("✅ NEW WALLET CREATED")
            println("=".repeat(80))
            println()
            println("📋 WHAT TO DO NOW:")
            println("1. Write down the 24-word mnemonic above (on paper)")
            println("2. Test restoring the wallet from the mnemonic")
            println("3. Use this address to receive Night tokens on preview network")
            println("4. NEVER share your mnemonic with anyone")
            println()

            // Clean up
            derivedKey.clear()

        } finally {
            Arrays.fill(seed, 0.toByte())
        }
    }

    /**
     * Generates multiple addresses from the same mnemonic.
     *
     * Tests address derivation at different indices within the same role.
     */
    @Test
    fun `generate multiple addresses from same mnemonic`() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon abandon art"

        println("\n" + "=".repeat(80))
        println("MULTIPLE ADDRESS GENERATION FROM SAME MNEMONIC")
        println("=".repeat(80))
        println()

        val seed = BIP39.mnemonicToSeed(mnemonic, passphrase = "")
        try {
            val wallet = HDWallet.fromSeed(seed)
            walletsToClean.add(wallet)

            val role = wallet.selectAccount(0).selectRole(MidnightKeyRole.NIGHT_EXTERNAL)

            // Generate first 5 addresses
            println("First 5 Addresses (Preview Network):")
            println()
            (0 until 5).forEach { index ->
                val derivedKey = role.deriveKeyAt(index)
                val compressedPublicKey = derivedKey.publicKeyBytes
                val xOnlyPublicKey = compressedPublicKey.copyOfRange(1, 33)

                // Hash public key with SHA-256 (Midnight's persistent_hash)
                val addressData = MessageDigest.getInstance("SHA-256").digest(xOnlyPublicKey)
                val address = Bech32m.encode("mn_addr_preview", addressData)

                println("Address #$index:")
                println("  Path: ${derivedKey.path}")
                println("  Address: $address")
                println()

                derivedKey.clear()
            }

            println("=".repeat(80))
            println("✅ MULTIPLE ADDRESSES GENERATED")
            println("=".repeat(80))
            println()
            println("💡 TIP: You can generate unlimited addresses from one mnemonic")
            println("Each index creates a different address for better privacy.")
            println()

        } finally {
            Arrays.fill(seed, 0.toByte())
        }
    }
}
