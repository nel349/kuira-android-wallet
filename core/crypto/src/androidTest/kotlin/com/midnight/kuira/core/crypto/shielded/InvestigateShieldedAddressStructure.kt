// Investigation test to understand shielded address structure
package com.midnight.kuira.core.crypto.shielded

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midnight.kuira.core.crypto.bip32.HDWallet
import com.midnight.kuira.core.crypto.bip32.MidnightKeyRole
import com.midnight.kuira.core.crypto.bip39.BIP39
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvestigateShieldedAddressStructure {

    @Before
    fun setUp() {
        assumeTrue(
            "Native library not loaded",
            ShieldedKeyDeriver.isLibraryLoaded()
        )
    }

    @Test
    fun investigateShieldedKeyLengths() {
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
                    require(shieldedKeys != null)

                    println("=== SHIELDED KEY INVESTIGATION ===")
                    println("Coin Public Key:")
                    println("  Hex: ${shieldedKeys.coinPublicKey}")
                    println("  Length: ${shieldedKeys.coinPublicKey.length} hex chars = ${shieldedKeys.coinPublicKey.length / 2} bytes")
                    println("")
                    println("Encryption Public Key:")
                    println("  Hex: ${shieldedKeys.encryptionPublicKey}")
                    println("  Length: ${shieldedKeys.encryptionPublicKey.length} hex chars = ${shieldedKeys.encryptionPublicKey.length / 2} bytes")
                    println("")

                    // Concatenate both to see full address length
                    val combined = shieldedKeys.coinPublicKey + shieldedKeys.encryptionPublicKey
                    println("Combined (coin + enc):")
                    println("  Hex: $combined")
                    println("  Length: ${combined.length} hex chars = ${combined.length / 2} bytes")
                    println("")

                    // Compare with Midnight SDK actual format (verified against TypeScript SDK)
                    println("Midnight SDK full address format:")
                    println("  Expected: 64 bytes (32 coin PK + 32 enc PK)")
                    println("  Our total: ${combined.length / 2} bytes")
                    println("")

                    if (combined.length / 2 == 64) {
                        println("✅ CORRECT: Format matches TypeScript SDK (32 + 32 = 64 bytes)")
                    } else {
                        println("⚠️  WARNING: Length mismatch! Expected 64 bytes, got ${combined.length / 2}")
                    }

                    println("==================================")

                } finally {
                    shieldedKey.clear()
                }
            } finally {
                hdWallet.clear()
            }
        }
    }
}
