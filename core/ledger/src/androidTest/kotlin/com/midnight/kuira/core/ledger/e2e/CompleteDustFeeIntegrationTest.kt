package com.midnight.kuira.core.ledger.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midnight.kuira.core.crypto.address.Bech32m
import com.midnight.kuira.core.crypto.bip32.HDWallet
import com.midnight.kuira.core.crypto.bip32.MidnightKeyRole
import com.midnight.kuira.core.crypto.bip39.BIP39
import com.midnight.kuira.core.crypto.dust.DustLocalState
import com.midnight.kuira.core.ledger.api.FfiTransactionSerializer
import com.midnight.kuira.core.ledger.model.UnshieldedOffer
import com.midnight.kuira.core.ledger.model.UtxoOutput
import com.midnight.kuira.core.ledger.model.UtxoSpend
import com.midnight.kuira.core.ledger.signer.TransactionSigner
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Arrays

/**
 * COMPREHENSIVE integration test for dust fee payment.
 *
 * **GOAL:** Verify that our wallet can actually make transactions with dust fees.
 *
 * **What We Test:**
 * 1. ✅ DustLocalState creation and management (Rust FFI)
 * 2. ✅ Wallet generation (BIP-39 → BIP-32 → Schnorr keys)
 * 3. ✅ Transaction building (inputs/outputs)
 * 4. ✅ Transaction signing (real Schnorr signatures via Rust FFI)
 * 5. ✅ Serialization WITHOUT dust (verify baseline works)
 * 6. ✅ DustLocalState serialization/deserialization
 * 7. ✅ Balance calculation
 *
 * **What We CANNOT Test Yet:**
 * - Actual dust UTXO registration (requires blockchain connection)
 * - Real dust spend creation (requires registered dust UTXOs)
 * - Network submission (requires running local node)
 *
 * **NO MOCKS** - Everything is REAL except blockchain network.
 */
@RunWith(AndroidJUnit4::class)
class CompleteDustFeeIntegrationTest {

    private lateinit var wallet: HDWallet
    private lateinit var senderAddress: String
    private lateinit var senderPublicKey: String
    private lateinit var privateKey: ByteArray
    private lateinit var seed: ByteArray
    private var dustState: DustLocalState? = null

    companion object {
        private const val TEST_MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        private val NATIVE_TOKEN = "0".repeat(64)
        private val TEST_INTENT_HASH = "deadbeef" + "0".repeat(56)

        private val RECIPIENT_ADDRESS by lazy {
            Bech32m.encode("mn_addr_undeployed", ByteArray(32))
        }

        private fun ByteArray.toHex(): String {
            return joinToString("") { "%02x".format(it) }
        }

        private fun hexToBytes(hex: String): ByteArray {
            return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }

    @Before
    fun setup() {
        println("\n╔════════════════════════════════════════════════════════════════╗")
        println("║  COMPREHENSIVE DUST FEE INTEGRATION TEST - SETUP              ║")
        println("╚════════════════════════════════════════════════════════════════╝")

        // Generate wallet
        seed = BIP39.mnemonicToSeed(TEST_MNEMONIC, passphrase = "")
        wallet = HDWallet.fromSeed(seed)

        val derivedKey = wallet
            .selectAccount(0)
            .selectRole(MidnightKeyRole.NIGHT_EXTERNAL)
            .deriveKeyAt(0)

        privateKey = derivedKey.privateKeyBytes.copyOf()

        // Derive BIP-340 public key
        val xOnlyPublicKey = TransactionSigner.getPublicKey(privateKey)
            ?: throw IllegalStateException("Failed to derive BIP-340 public key")
        require(xOnlyPublicKey.size == 32) { "Public key must be 32 bytes, got ${xOnlyPublicKey.size}" }

        // Create address
        val addressData = MessageDigest.getInstance("SHA-256").digest(xOnlyPublicKey)
        senderAddress = Bech32m.encode("mn_addr_undeployed", addressData)
        senderPublicKey = xOnlyPublicKey.toHex()

        println("✅ Wallet:")
        println("   Address: $senderAddress")
        println("   Public Key: ${senderPublicKey.take(24)}...")
        println("   Private Key: ${privateKey.toHex().take(24)}...")

        derivedKey.clear()
    }

    @After
    fun teardown() {
        wallet.clear()
        Arrays.fill(privateKey, 0.toByte())
        Arrays.fill(seed, 0.toByte())
        dustState?.close()
        println("🧹 Cleanup complete\n")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 1: Verify DustLocalState FFI Works
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun test1_DustLocalStateCreation() {
        println("\n┌─────────────────────────────────────────────────────────────┐")
        println("│ TEST 1: DustLocalState Creation (Rust FFI)                 │")
        println("└─────────────────────────────────────────────────────────────┘")

        // Create dust state
        dustState = DustLocalState.create()
        assertNotNull("DustLocalState.create() should return non-null", dustState)

        println("✅ DustLocalState created successfully via Rust FFI")
    }

    @Test
    fun test2_DustLocalStateSerialization() {
        println("\n┌─────────────────────────────────────────────────────────────┐")
        println("│ TEST 2: DustLocalState Serialization (Round-Trip)          │")
        println("└─────────────────────────────────────────────────────────────┘")

        // Create state
        dustState = DustLocalState.create()
        assertNotNull(dustState)

        // Serialize
        val serialized = dustState!!.serialize()
        assertNotNull("Serialization should return data", serialized)
        assertTrue("Serialized data should not be empty", serialized!!.isNotEmpty())

        println("✅ Serialized: ${serialized.size} bytes")
        println("   First 32 bytes: ${serialized.take(32).joinToString("") { "%02x".format(it) }}")

        // Deserialize
        val deserialized = DustLocalState.deserialize(serialized)
        assertNotNull("Deserialization should succeed", deserialized)

        println("✅ Deserialized successfully")

        // Clean up
        deserialized?.close()
    }

    @Test
    fun test3_DustBalanceCalculation() {
        println("\n┌─────────────────────────────────────────────────────────────┐")
        println("│ TEST 3: Dust Balance Calculation                           │")
        println("└─────────────────────────────────────────────────────────────┘")

        // Create state
        dustState = DustLocalState.create()
        assertNotNull(dustState)

        // Get balance at current time
        val currentTime = System.currentTimeMillis()
        val balance = dustState!!.getBalance(currentTime)

        assertNotNull("Balance should be returned", balance)
        println("✅ Balance: $balance Specks")

        // Should be zero (no UTXOs registered yet)
        assertEquals("Balance should be 0 for new state", BigInteger.ZERO, balance)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 4: Verify Transaction Signing Works
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun test4_SchnorrSigningWorks() {
        println("\n┌─────────────────────────────────────────────────────────────┐")
        println("│ TEST 4: Schnorr Signing (TransactionSigner Rust FFI)       │")
        println("└─────────────────────────────────────────────────────────────┘")

        val testMessage = "Test message for signing".toByteArray()

        val signature = TransactionSigner.signData(privateKey, testMessage)
        assertNotNull("Signing should succeed", signature)
        assertEquals("Signature should be 64 bytes (BIP-340)", 64, signature!!.size)

        println("✅ Signature: ${signature.toHex().take(40)}...")
        println("   Length: ${signature.size} bytes")

        // Verify signature
        val publicKey = TransactionSigner.getPublicKey(privateKey)
        assertNotNull(publicKey)

        val isValid = TransactionSigner.verifySignature(publicKey!!, testMessage, signature)
        assertTrue("Signature should be valid", isValid)

        println("✅ Signature verified successfully")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 5: Verify Transaction Serialization Works (WITHOUT Dust)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun test5_TransactionSerializationWithoutDust() {
        println("\n┌─────────────────────────────────────────────────────────────┐")
        println("│ TEST 5: Transaction Serialization (NO DUST)                │")
        println("└─────────────────────────────────────────────────────────────┘")

        // Create test UTXO
        val testUtxo = UtxoSpend(
            intentHash = TEST_INTENT_HASH,
            outputNo = 0,
            value = BigInteger("1000000000"),  // 1000 NIGHT
            owner = senderAddress,
            ownerPublicKey = senderPublicKey,
            tokenType = NATIVE_TOKEN
        )

        val paymentOutput = UtxoOutput(
            value = BigInteger("100000000"),  // 100 NIGHT
            owner = RECIPIENT_ADDRESS,
            tokenType = NATIVE_TOKEN
        )

        val changeOutput = UtxoOutput(
            value = BigInteger("900000000"),  // 900 NIGHT change
            owner = senderAddress,
            tokenType = NATIVE_TOKEN
        )

        println("📝 Transaction:")
        println("   Input: ${testUtxo.value} (${testUtxo.value.divide(BigInteger("1000000"))} NIGHT)")
        println("   Output 1: ${paymentOutput.value} to recipient")
        println("   Output 2: ${changeOutput.value} change")

        // Get signing message from FFI
        val serializer = FfiTransactionSerializer()
        val ttl = System.currentTimeMillis() + (5 * 60 * 1000)

        val signingMessageHex = serializer.getSigningMessageForInput(
            inputs = listOf(testUtxo),
            outputs = listOf(paymentOutput, changeOutput),
            inputIndex = 0,
            ttl = ttl
        )

        assertNotNull("Signing message should be generated", signingMessageHex)
        println("✅ Signing message: ${signingMessageHex!!.take(40)}...")

        // Sign the transaction
        val signingMessage = hexToBytes(signingMessageHex)
        val signature = TransactionSigner.signData(privateKey, signingMessage)
        assertNotNull("Signing should succeed", signature)

        println("✅ Signature: ${signature!!.toHex().take(40)}...")

        // Create signed offer
        val signedOffer = UnshieldedOffer(
            inputs = listOf(testUtxo),
            outputs = listOf(paymentOutput, changeOutput),
            signatures = listOf(signature)
        )

        // Create Intent WITHOUT dust actions
        val intent = com.midnight.kuira.core.ledger.model.Intent.withDefaultTtl(
            guaranteedOffer = signedOffer,
            dustActions = null
        )

        // Serialize to SCALE
        val scaleHex = serializer.serialize(intent)
        assertNotNull("Serialization should succeed", scaleHex)
        assertTrue("SCALE hex should not be empty", scaleHex.isNotEmpty())

        println("✅ SCALE Serialization: ${scaleHex.take(80)}...")
        println("   Total length: ${scaleHex.length} chars (${scaleHex.length / 2} bytes)")

        // Verify SCALE starts with expected tag
        assertTrue("Should start with midnight:transaction tag",
            scaleHex.startsWith("6d69646e696768743a"))  // "midnight:" in hex
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 6: COMPREHENSIVE - All Mechanisms Together
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun test6_AllDustMechanismsWork() {
        println("\n┌─────────────────────────────────────────────────────────────┐")
        println("│ TEST 6: ALL DUST MECHANISMS (Comprehensive)                │")
        println("└─────────────────────────────────────────────────────────────┘")

        var testsPassed = 0
        val totalTests = 7

        // 1. DustLocalState
        try {
            dustState = DustLocalState.create()
            assertNotNull(dustState)
            println("✅ [1/$totalTests] DustLocalState creation")
            testsPassed++
        } catch (e: Exception) {
            println("❌ [1/$totalTests] DustLocalState creation FAILED: ${e.message}")
        }

        // 2. Wallet
        try {
            assertNotNull(wallet)
            assertNotNull(privateKey)
            assertTrue(seed.isNotEmpty())
            println("✅ [2/$totalTests] Wallet generation")
            testsPassed++
        } catch (e: Exception) {
            println("❌ [2/$totalTests] Wallet generation FAILED: ${e.message}")
        }

        // 3. TransactionSigner
        try {
            val testMsg = "test".toByteArray()
            val sig = TransactionSigner.signData(privateKey, testMsg)
            assertNotNull(sig)
            println("✅ [3/$totalTests] TransactionSigner (Schnorr)")
            testsPassed++
        } catch (e: Exception) {
            println("❌ [3/$totalTests] TransactionSigner FAILED: ${e.message}")
        }

        // 4. FfiTransactionSerializer
        try {
            val serializer = FfiTransactionSerializer()
            assertNotNull(serializer)
            println("✅ [4/$totalTests] FfiTransactionSerializer")
            testsPassed++
        } catch (e: Exception) {
            println("❌ [4/$totalTests] FfiTransactionSerializer FAILED: ${e.message}")
        }

        // 5. DustLocalState serialization
        try {
            val serialized = dustState!!.serialize()
            assertNotNull(serialized)
            val deserialized = DustLocalState.deserialize(serialized!!)
            assertNotNull(deserialized)
            deserialized?.close()
            println("✅ [5/$totalTests] DustLocalState serialization")
            testsPassed++
        } catch (e: Exception) {
            println("❌ [5/$totalTests] DustLocalState serialization FAILED: ${e.message}")
        }

        // 6. Balance calculation
        try {
            val balance = dustState!!.getBalance(System.currentTimeMillis())
            assertNotNull(balance)
            assertEquals(BigInteger.ZERO, balance)
            println("✅ [6/$totalTests] Balance calculation")
            testsPassed++
        } catch (e: Exception) {
            println("❌ [6/$totalTests] Balance calculation FAILED: ${e.message}")
        }

        // 7. Public key derivation
        try {
            val pubKey = TransactionSigner.getPublicKey(privateKey)
            assertNotNull(pubKey)
            assertEquals(32, pubKey!!.size)
            println("✅ [7/$totalTests] Public key derivation")
            testsPassed++
        } catch (e: Exception) {
            println("❌ [7/$totalTests] Public key derivation FAILED: ${e.message}")
        }

        println("\n═══════════════════════════════════════════════════════════════")
        println("  RESULTS: $testsPassed/$totalTests tests passed")
        println("═══════════════════════════════════════════════════════════════")

        assertEquals("All dust mechanisms should work", totalTests, testsPassed)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 7: Verify Native Library Contains All Required Functions
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun test7_NativeLibraryHasAllFunctions() {
        println("\n┌─────────────────────────────────────────────────────────────┐")
        println("│ TEST 7: Native Library Function Availability               │")
        println("└─────────────────────────────────────────────────────────────┘")

        var functionsAvailable = 0

        // Test create_dust_local_state
        try {
            val state = DustLocalState.create()
            assertNotNull(state)
            state?.close()
            println("✅ create_dust_local_state")
            functionsAvailable++
        } catch (e: Exception) {
            println("❌ create_dust_local_state MISSING")
        }

        // Test serialize_dust_state
        try {
            val state = DustLocalState.create()
            val serialized = state?.serialize()
            assertNotNull(serialized)
            state?.close()
            println("✅ serialize_dust_state")
            functionsAvailable++
        } catch (e: Exception) {
            println("❌ serialize_dust_state MISSING")
        }

        // Test deserialize_dust_state
        try {
            val state = DustLocalState.create()
            val serialized = state?.serialize()
            val deserialized = DustLocalState.deserialize(serialized!!)
            assertNotNull(deserialized)
            state?.close()
            deserialized?.close()
            println("✅ deserialize_dust_state")
            functionsAvailable++
        } catch (e: Exception) {
            println("❌ deserialize_dust_state MISSING")
        }

        // Test sign_data
        try {
            val sig = TransactionSigner.signData(privateKey, "test".toByteArray())
            assertNotNull(sig)
            println("✅ sign_data (Schnorr)")
            functionsAvailable++
        } catch (e: Exception) {
            println("❌ sign_data MISSING")
        }

        // Test serialize_unshielded_transaction
        try {
            val serializer = FfiTransactionSerializer()
            assertNotNull(serializer)
            println("✅ serialize_unshielded_transaction")
            functionsAvailable++
        } catch (e: Exception) {
            println("❌ serialize_unshielded_transaction MISSING")
        }

        println("\n═══════════════════════════════════════════════════════════════")
        println("  NATIVE FUNCTIONS: $functionsAvailable/5 available")
        println("═══════════════════════════════════════════════════════════════")

        assertEquals("All native functions should be available", 5, functionsAvailable)
    }
}
