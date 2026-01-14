package com.midnight.kuira.core.crypto.integration

import com.midnight.kuira.core.crypto.bip32.HDWallet
import com.midnight.kuira.core.crypto.bip32.MidnightKeyRole
import com.midnight.kuira.core.crypto.bip39.BIP39
import org.junit.Test

/**
 * Verifies that Kuira derives the same keys as Midnight Wallet SDK
 * for ALL key types: Unshielded, Shielded, and Dust.
 *
 * Test vector: "abandon abandon abandon... art" (24-word mnemonic)
 */
class MidnightKeyDerivationTest {

    @Test
    fun `verify all three key types match Midnight SDK`() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon abandon art"

        // Step 1: Derive BIP-39 seed
        val seed = BIP39.mnemonicToSeed(mnemonic, passphrase = "")

        println("=== MIDNIGHT KEY DERIVATION TEST ===\n")
        println("Mnemonic: ${mnemonic.substring(0, 50)}...")
        println()

        // Step 2: Create HD wallet
        val wallet = HDWallet.fromSeed(seed)

        // Step 3: Derive UNSHIELDED key (role 0 = NIGHT_EXTERNAL)
        val unshieldedKey = wallet
            .selectAccount(0)
            .selectRole(MidnightKeyRole.NIGHT_EXTERNAL)
            .deriveKeyAt(0)

        val unshieldedSeed = unshieldedKey.privateKeyBytes.joinToString("") { "%02x".format(it) }

        println("1. UNSHIELDED seed at m/44'/2400'/0'/0/0:")
        println("   Expected: d319aebe08e7706091e56b1abe83f50ba6d3ceb4209dd0deca8ab22b264ff31c")
        println("   Actual:   $unshieldedSeed")
        println("   Match: ${unshieldedSeed == "d319aebe08e7706091e56b1abe83f50ba6d3ceb4209dd0deca8ab22b264ff31c"}")
        println()

        // Step 4: Derive SHIELDED key (role 3 = ZSWAP)
        val shieldedKey = wallet
            .selectAccount(0)
            .selectRole(MidnightKeyRole.ZSWAP)
            .deriveKeyAt(0)

        val shieldedSeed = shieldedKey.privateKeyBytes.joinToString("") { "%02x".format(it) }

        println("2. SHIELDED seed at m/44'/2400'/0'/3/0:")
        println("   Expected: 5212aab1ab7134133dae5820e87697a4327218ee908d73c234ea0a7b95d0b176")
        println("   Actual:   $shieldedSeed")
        println("   Match: ${shieldedSeed == "5212aab1ab7134133dae5820e87697a4327218ee908d73c234ea0a7b95d0b176"}")
        println()

        // Step 5: Derive DUST key (role 2 = DUST)
        val dustKey = wallet
            .selectAccount(0)
            .selectRole(MidnightKeyRole.DUST)
            .deriveKeyAt(0)

        val dustSeed = dustKey.privateKeyBytes.joinToString("") { "%02x".format(it) }

        println("3. DUST seed at m/44'/2400'/0'/2/0:")
        println("   Expected: eec5d0f4a0524db4860445e0b5e65e861f7e9179d09eaa2cc621cb0aba8140d9")
        println("   Actual:   $dustSeed")
        println("   Match: ${dustSeed == "eec5d0f4a0524db4860445e0b5e65e861f7e9179d09eaa2cc621cb0aba8140d9"}")
        println()

        // Verify all match (⚠️ LACE COMPATIBILITY: Updated for 32-byte seeds)
        val unshieldedMatches = unshieldedSeed == "d319aebe08e7706091e56b1abe83f50ba6d3ceb4209dd0deca8ab22b264ff31c"
        val shieldedMatches = shieldedSeed == "5212aab1ab7134133dae5820e87697a4327218ee908d73c234ea0a7b95d0b176"
        val dustMatches = dustSeed == "eec5d0f4a0524db4860445e0b5e65e861f7e9179d09eaa2cc621cb0aba8140d9"

        if (unshieldedMatches && shieldedMatches && dustMatches) {
            println("✅ SUCCESS: All three key types match Midnight SDK!")
            println()
            println("This verifies:")
            println("  ✅ Unshielded transactions (public)")
            println("  ✅ Shielded transactions (private with ZK proofs)")
            println("  ✅ Dust transactions (fee payments)")
            println()
            println("Kuira crypto module is FULLY compatible with Midnight!")
        } else {
            println("❌ FAILURE: One or more keys don't match!")
            throw AssertionError("Key derivation mismatch")
        }

        // Clean up
        unshieldedKey.clear()
        shieldedKey.clear()
        dustKey.clear()
        wallet.clear()
    }
}
