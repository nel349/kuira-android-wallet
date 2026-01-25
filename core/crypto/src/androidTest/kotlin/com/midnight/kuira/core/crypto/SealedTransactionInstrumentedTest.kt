package com.midnight.kuira.core.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test to verify sealed transaction tag on actual Android device.
 * This will generate FFI logs that we can inspect via logcat.
 */
@RunWith(AndroidJUnit4::class)
class SealedTransactionInstrumentedTest {

    init {
        // Load native library
        System.loadLibrary("kuira_crypto_ffi")
    }

    @Test
    fun testSealedTransactionTag() {
        println("\n═══════════════════════════════════════════════════════════════")
        println("  [INSTRUMENTED] Testing Sealed Transaction Tag")
        println("═══════════════════════════════════════════════════════════════\n")

        // Test data (known working case)
        val inputsJson = """
            [{
                "value": "5000000",
                "owner": "7de754a427c2723bd9e04f7e7876b70bed051aaa439966aaff1596a2c3309fe0",
                "type": "0000000000000000000000000000000000000000000000000000000000000000",
                "intent_hash": "00e28d3099efda8b36d6277c61f4ce062d52102898b1314c16bd28c9d905b59c",
                "output_no": 0
            }]
        """.trimIndent()

        val outputsJson = """
            [{
                "value": "1000000",
                "owner": "cc88e4d1c76326a4ceea7e37108189af419e2af4c59d7295dae7745dd195a363",
                "type": "0000000000000000000000000000000000000000000000000000000000000000"
            },
            {
                "value": "4000000",
                "owner": "0f0a855cd404f70c87921a9f50163a808b729fe14be61ff0df50cd27780d753e",
                "type": "0000000000000000000000000000000000000000000000000000000000000000"
            }]
        """.trimIndent()

        val signaturesJson = """
            ["fd5dbd86cdbf787453b6d88c350dd4fdf20d1096dc8cb7a64f5056dc2498ffeb90c12dcf98b08fc5a8567f6e1a4cd5c4297de435e87819301ebde75f95e170bc"]
        """.trimIndent()

        val ttl = 1769298539531L
        val bindingCommitment = "734e903096da963e1c293acc3c3f8bd0d104e83176f0ddd0e3edffd1444377fc0b"

        println("📦 Calling FFI serialization...")

        // Call FFI - this will generate logs
        val txHex = nativeSerializeTransaction(
            inputsJson,
            outputsJson,
            signaturesJson,
            ttl,
            bindingCommitment
        )

        assertNotNull("Transaction serialization should succeed", txHex)
        println("✅ Transaction serialized: ${txHex!!.length / 2} bytes")

        // Decode and verify tag
        val bytes = txHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val tagEndIndex = bytes.indexOfFirst { it == ':'.code.toByte() }
        require(tagEndIndex > 0) { "No tag delimiter ':' found" }

        val tagBytes = bytes.sliceArray(0..tagEndIndex)
        val tag = String(tagBytes, Charsets.UTF_8)

        println("📋 Transaction Tag: $tag")

        // Verify components
        assertTrue("Tag should contain 'proof-preimage'", tag.contains("proof-preimage"))

        val isSealed = tag.contains("pedersen-schnorr[v1]") || tag.contains("embedded-fr[v1]")
        val isPedersen = tag.contains("pedersen[v1]") && !tag.contains("pedersen-schnorr")

        when {
            isSealed -> println("✅ SUCCESS! Transaction is SEALED (PureGeneratorPedersen)")
            isPedersen -> {
                println("❌ FAIL! Transaction is NOT SEALED (still using Pedersen)")
                fail("Transaction was not sealed - binding type is pedersen[v1] instead of pedersen-schnorr[v1]")
            }
            else -> {
                println("⚠️  WARNING! Unexpected binding type in tag")
            }
        }

        println("\n═══════════════════════════════════════════════════════════════")
        println("  Check logcat for detailed FFI output:")
        println("  adb logcat -d | grep -A 10 'FULL TRANSACTION TAG'")
        println("═══════════════════════════════════════════════════════════════\n")
    }

    private external fun nativeSerializeTransaction(
        inputsJson: String,
        outputsJson: String,
        signaturesJson: String,
        ttl: Long,
        bindingCommitmentHex: String
    ): String?
}
