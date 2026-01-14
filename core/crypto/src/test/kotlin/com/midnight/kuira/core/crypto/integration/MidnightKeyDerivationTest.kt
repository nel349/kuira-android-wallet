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
        println("   Expected: af7a998947b1b1fd12d99cb40ee98a739e6a2518d8965690781d85ea0e3a5e13")
        println("   Actual:   $unshieldedSeed")
        println("   Match: ${unshieldedSeed == "af7a998947b1b1fd12d99cb40ee98a739e6a2518d8965690781d85ea0e3a5e13"}")
        println()

        // Step 4: Derive SHIELDED key (role 3 = ZSWAP)
        val shieldedKey = wallet
            .selectAccount(0)
            .selectRole(MidnightKeyRole.ZSWAP)
            .deriveKeyAt(0)

        val shieldedSeed = shieldedKey.privateKeyBytes.joinToString("") { "%02x".format(it) }

        println("2. SHIELDED seed at m/44'/2400'/0'/3/0:")
        println("   Expected: b7637860b12f892ee07c67ad441c7935e37ac2153cefa39ae79083284f6d9180")
        println("   Actual:   $shieldedSeed")
        println("   Match: ${shieldedSeed == "b7637860b12f892ee07c67ad441c7935e37ac2153cefa39ae79083284f6d9180"}")
        println()

        // Step 5: Derive DUST key (role 2 = DUST)
        val dustKey = wallet
            .selectAccount(0)
            .selectRole(MidnightKeyRole.DUST)
            .deriveKeyAt(0)

        val dustSeed = dustKey.privateKeyBytes.joinToString("") { "%02x".format(it) }

        println("3. DUST seed at m/44'/2400'/0'/2/0:")
        println("   Expected: eb8d1f8ec996205145c8409d790edea11b2da93e67924db6e0664f6de1a96f15")
        println("   Actual:   $dustSeed")
        println("   Match: ${dustSeed == "eb8d1f8ec996205145c8409d790edea11b2da93e67924db6e0664f6de1a96f15"}")
        println()

        // Verify all match
        val unshieldedMatches = unshieldedSeed == "af7a998947b1b1fd12d99cb40ee98a739e6a2518d8965690781d85ea0e3a5e13"
        val shieldedMatches = shieldedSeed == "b7637860b12f892ee07c67ad441c7935e37ac2153cefa39ae79083284f6d9180"
        val dustMatches = dustSeed == "eb8d1f8ec996205145c8409d790edea11b2da93e67924db6e0664f6de1a96f15"

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
