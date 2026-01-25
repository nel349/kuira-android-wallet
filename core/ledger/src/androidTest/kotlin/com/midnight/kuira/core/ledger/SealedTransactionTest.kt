package com.midnight.kuira.core.ledger

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midnight.kuira.core.ledger.api.FfiTransactionSerializer
import com.midnight.kuira.core.ledger.model.Intent
import com.midnight.kuira.core.ledger.model.UnshieldedOffer
import com.midnight.kuira.core.ledger.model.UtxoOutput
import com.midnight.kuira.core.ledger.model.UtxoSpend
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test to verify sealed transaction tag.
 * Runs on actual Android device/emulator to generate FFI logs.
 */
@RunWith(AndroidJUnit4::class)
class SealedTransactionTest {

    @Test
    fun testSealedTransactionTagFormat() {
        println("\n═══════════════════════════════════════════════════════════════")
        println("  [INSTRUMENTED] Testing Sealed Transaction Tag")
        println("═══════════════════════════════════════════════════════════════\n")

        // Create serializer (this loads the FFI library)
        val serializer = FfiTransactionSerializer()

        // Build test transaction
        val inputs = listOf(
            UtxoSpend(
                value = java.math.BigInteger("5000000"),
                ownerPublicKey = "7de754a427c2723bd9e04f7e7876b70bed051aaa439966aaff1596a2c3309fe0",
                tokenType = "0000000000000000000000000000000000000000000000000000000000000000",
                intentHash = "00e28d3099efda8b36d6277c61f4ce062d52102898b1314c16bd28c9d905b59c",
                outputNo = 0,
                owner = "mn_addr_test"  // Required field
            )
        )

        val outputs = listOf(
            UtxoOutput(
                value = java.math.BigInteger("1000000"),
                owner = "mn1ezy8gk3rkx2jtwnf7qw8qgz03fl2y730fvhfeaj4w4m296v4cvkssz74tx",
                tokenType = "0000000000000000000000000000000000000000000000000000000000000000"
            ),
            UtxoOutput(
                value = java.math.BigInteger("4000000"),
                owner = "mn1pz9g2hx5q7cxj0fjr27gqcf36zqt3ulzuhmf06z0lu8vdxlq62ulqwufac",
                tokenType = "0000000000000000000000000000000000000000000000000000000000000000"
            )
        )

        val signatures = listOf(
            hexToBytes("fd5dbd86cdbf787453b6d88c350dd4fdf20d1096dc8cb7a64f5056dc2498ffeb90c12dcf98b08fc5a8567f6e1a4cd5c4297de435e87819301ebde75f95e170bc")
        )

        val offer = UnshieldedOffer(inputs, outputs, signatures)
        val ttl = 1769298539531L

        val intent = Intent(
            guaranteedUnshieldedOffer = offer,
            fallibleUnshieldedOffer = null,
            ttl = ttl
        )

        // First, get signing message to generate binding_commitment
        println("📦 Getting signing message (generates binding_commitment)...")
        val signingMessage = serializer.getSigningMessageForInput(inputs, outputs, 0, ttl)
        assertNotNull("Signing message should be generated", signingMessage)
        println("✅ Signing message generated")

        // Now serialize (this will use the stored binding_commitment)
        println("📦 Serializing transaction...")
        val txHex = serializer.serialize(intent)
        assertNotNull("Transaction serialization should succeed", txHex)
        println("✅ Transaction serialized: ${txHex.length / 2} bytes")

        // Decode and verify tag
        val bytes = txHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val tagEndIndex = bytes.indexOfFirst { it == ':'.code.toByte() }
        require(tagEndIndex > 0) { "No tag delimiter ':' found" }

        val tagBytes = bytes.sliceArray(0..tagEndIndex)
        val tag = String(tagBytes, Charsets.UTF_8)

        println("\n📋 Transaction Tag:")
        println("   $tag\n")

        // Verify components
        assertTrue("Tag should contain 'proof-preimage'", tag.contains("proof-preimage"))

        val isSealed = tag.contains("pedersen-schnorr[v1]") || tag.contains("embedded-fr[v1]")
        val isPedersen = tag.contains("pedersen[v1]") && !tag.contains("pedersen-schnorr")

        when {
            isSealed -> {
                println("✅ SUCCESS! Transaction is SEALED")
                println("   Binding type: ${if (tag.contains("pedersen-schnorr")) "pedersen-schnorr[v1]" else "embedded-fr[v1]"}")
            }
            isPedersen -> {
                println("❌ FAIL! Transaction is NOT SEALED")
                println("   Still using pedersen[v1] instead of pedersen-schnorr[v1]")
                fail("Transaction was not sealed - binding type is incorrect")
            }
            else -> {
                println("⚠️  WARNING! Unexpected binding type in tag")
                println("   Tag: $tag")
            }
        }

        println("\n═══════════════════════════════════════════════════════════════")
        println("  Check logcat for detailed FFI output:")
        println("  adb logcat -d | grep -A 10 'FULL TRANSACTION TAG'")
        println("═══════════════════════════════════════════════════════════════\n")
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
