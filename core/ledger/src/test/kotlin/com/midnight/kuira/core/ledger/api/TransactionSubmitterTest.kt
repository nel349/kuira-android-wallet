package com.midnight.kuira.core.ledger.api

import com.midnight.kuira.core.indexer.api.IndexerClient
import com.midnight.kuira.core.indexer.model.TransactionType
import com.midnight.kuira.core.indexer.model.UnshieldedTransaction
import com.midnight.kuira.core.indexer.model.UnshieldedTransactionUpdate
import com.midnight.kuira.core.ledger.model.Intent
import com.midnight.kuira.core.ledger.model.UnshieldedOffer
import com.midnight.kuira.core.ledger.model.UtxoOutput
import com.midnight.kuira.core.ledger.model.UtxoSpend
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class TransactionSubmitterTest {

    @Test
    fun `submitAndWait returns Success when transaction is confirmed`() = runTest {
        // Given: Mock node client that returns transaction hash
        val nodeClient = mockk<NodeRpcClient>()
        val expectedTxHash = "a".repeat(64)  // 64 hex chars
        coEvery { nodeClient.submitTransaction(any()) } returns expectedTxHash

        // Given: Mock indexer client that confirms transaction
        val indexerClient = mockk<IndexerClient>()
        val confirmedTx = UnshieldedTransactionUpdate.Transaction(
            transaction = UnshieldedTransaction(
                id = 12345,
                hash = expectedTxHash,
                type = TransactionType.RegularTransaction,
                protocolVersion = 1,
                block = UnshieldedTransaction.BlockInfo(
                    timestamp = 1704067200000
                )
            ),
            createdUtxos = emptyList(),
            spentUtxos = emptyList()
        )
        every { indexerClient.subscribeToUnshieldedTransactions(any(), any()) } returns flowOf(confirmedTx)

        // Given: Transaction serializer
        val serializer = StubTransactionSerializer()

        // Given: Transaction submitter
        val submitter = TransactionSubmitter(nodeClient, indexerClient, serializer)

        // Given: Signed intent
        val signedIntent = createTestIntent()

        // When: Submit and wait
        val result = submitter.submitAndWait(
            signedIntent = signedIntent,
            fromAddress = "mn_addr_test"
        )

        // Then: Should succeed
        assertTrue(result is TransactionSubmitter.SubmissionResult.Success)
        val success = result as TransactionSubmitter.SubmissionResult.Success
        assertEquals(expectedTxHash, success.txHash)
        assertEquals(12345L, success.blockHeight)

        // Verify: Node client was called
        coVerify { nodeClient.submitTransaction(any()) }
    }

    @Test
    fun `submitAndWait returns Failed when node rejects transaction`() = runTest {
        // Given: Mock node client that rejects transaction
        val nodeClient = mockk<NodeRpcClient>()
        coEvery { nodeClient.submitTransaction(any()) } throws TransactionRejected(
            reason = "Invalid signature",
            txHash = null
        )

        // Given: Mock indexer (won't be called)
        val indexerClient = mockk<IndexerClient>(relaxed = true)

        // Given: Transaction serializer
        val serializer = StubTransactionSerializer()

        // Given: Transaction submitter
        val submitter = TransactionSubmitter(nodeClient, indexerClient, serializer)

        // Given: Signed intent
        val signedIntent = createTestIntent()

        // When: Submit and wait
        val result = submitter.submitAndWait(
            signedIntent = signedIntent,
            fromAddress = "mn_addr_test"
        )

        // Then: Should fail
        assertTrue(result is TransactionSubmitter.SubmissionResult.Failed)
        val failed = result as TransactionSubmitter.SubmissionResult.Failed
        assertEquals(null, failed.txHash)
        assertTrue(failed.reason.contains("rejected"))
    }

    @Test
    fun `submitOnly returns transaction hash without waiting`() = runTest {
        // Given: Mock node client
        val nodeClient = mockk<NodeRpcClient>()
        val expectedTxHash = "b".repeat(64)
        coEvery { nodeClient.submitTransaction(any()) } returns expectedTxHash

        // Given: Mock indexer (won't be called)
        val indexerClient = mockk<IndexerClient>(relaxed = true)

        // Given: Transaction serializer
        val serializer = StubTransactionSerializer()

        // Given: Transaction submitter
        val submitter = TransactionSubmitter(nodeClient, indexerClient, serializer)

        // Given: Signed intent
        val signedIntent = createTestIntent()

        // When: Submit only (no wait)
        val txHash = submitter.submitOnly(signedIntent)

        // Then: Should return hash immediately
        assertEquals(expectedTxHash, txHash)

        // Verify: Node client was called
        coVerify { nodeClient.submitTransaction(any()) }
    }

    @Test
    fun `serializer generates deterministic hex`() {
        // Given: Serializer
        val serializer = StubTransactionSerializer()

        // Given: Test intent
        val intent = createTestIntent()

        // When: Serialize twice
        val hex1 = serializer.serialize(intent)
        val hex2 = serializer.serialize(intent)

        // Then: Should be identical
        assertEquals(hex1, hex2)

        // Then: Should be valid hex
        assertTrue(hex1.matches(Regex("^[0-9a-f]+$")))
        assertTrue(hex1.length > 0)
        assertTrue(hex1.length % 2 == 0)  // Even length
    }

    // ==================== Test Helpers ====================

    private fun createTestIntent(): Intent {
        val input = UtxoSpend(
            intentHash = "1".repeat(64),
            outputNo = 0,
            value = BigInteger("100000000000"),  // 100 NIGHT
            owner = "2".repeat(64),
            ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
            tokenType = "0".repeat(64)
        )

        val output = UtxoOutput(
            value = BigInteger("100000000000"),
            owner = "3".repeat(64),
            tokenType = "0".repeat(64)
        )

        val signature = ByteArray(64) { 4 }  // Mock signature

        val offer = UnshieldedOffer(
            inputs = listOf(input),
            outputs = listOf(output),
            signatures = listOf(signature)
        )

        return Intent(
            guaranteedUnshieldedOffer = offer,
            fallibleUnshieldedOffer = null,
            ttl = 1704067200000  // 2024-01-01
        )
    }
}
