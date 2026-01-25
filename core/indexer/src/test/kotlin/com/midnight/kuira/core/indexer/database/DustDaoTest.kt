package com.midnight.kuira.core.indexer.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Instrumented tests for DustDao.
 *
 * **Test Coverage:**
 * - Insert/upsert operations
 * - State transitions (AVAILABLE → PENDING → SPENT)
 * - Query operations (get by state, sorted, etc.)
 * - Flow observations (reactive queries)
 * - Statistics (count)
 * - Cleanup operations
 *
 * **Why Robolectric:**
 * Room requires Android Context. Robolectric provides Android environment in JVM tests.
 * Faster than full instrumented tests (no emulator needed).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28]) // Android 9.0
class DustDaoTest {

    private lateinit var database: UtxoDatabase
    private lateinit var dustDao: DustDao

    // Test data
    private val testAddress = "mn_addr_testnet1test123"
    private val testNullifier1 = "0x1111111111111111"
    private val testNullifier2 = "0x2222222222222222"
    private val testNullifier3 = "0x3333333333333333"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            UtxoDatabase::class.java
        )
            .allowMainThreadQueries() // Only for testing
            .build()

        dustDao = database.dustDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Insert/Upsert Tests ====================

    @Test
    fun `insertToken inserts token successfully`() = runTest {
        // Given: A dust token
        val token = createTestToken(nullifier = testNullifier1)

        // When: Insert token
        dustDao.insertToken(token)

        // Then: Token is in database
        val retrieved = dustDao.getTokenByNullifier(testNullifier1)
        assertNotNull(retrieved)
        assertEquals(testNullifier1, retrieved?.nullifier)
        assertEquals(testAddress, retrieved?.address)
    }

    @Test
    fun `insertTokens inserts multiple tokens`() = runTest {
        // Given: Multiple tokens
        val tokens = listOf(
            createTestToken(nullifier = testNullifier1, initialValue = "1000"),
            createTestToken(nullifier = testNullifier2, initialValue = "2000"),
            createTestToken(nullifier = testNullifier3, initialValue = "3000")
        )

        // When: Insert all
        dustDao.insertTokens(tokens)

        // Then: All tokens in database
        val count = dustDao.count()
        assertEquals(3, count)
    }

    @Test
    fun `insertToken with REPLACE strategy updates existing token`() = runTest {
        // Given: Token with initial value
        val original = createTestToken(
            nullifier = testNullifier1,
            initialValue = "1000"
        )
        dustDao.insertToken(original)

        // When: Insert same nullifier with different value
        val updated = createTestToken(
            nullifier = testNullifier1,
            initialValue = "2000"
        )
        dustDao.insertToken(updated)

        // Then: Value is updated (not inserted as duplicate)
        val count = dustDao.count()
        assertEquals(1, count)

        val retrieved = dustDao.getTokenByNullifier(testNullifier1)
        assertEquals("2000", retrieved?.initialValue)
    }

    // ==================== State Transition Tests ====================

    @Test
    fun `markAsPending changes state to PENDING`() = runTest {
        // Given: AVAILABLE token
        val token = createTestToken(
            nullifier = testNullifier1,
            state = UtxoState.AVAILABLE
        )
        dustDao.insertToken(token)

        // When: Mark as pending
        dustDao.markAsPending(testNullifier1)

        // Then: State is PENDING
        val updated = dustDao.getTokenByNullifier(testNullifier1)
        assertEquals(UtxoState.PENDING, updated?.state)
    }

    @Test
    fun `markAsPending handles multiple tokens`() = runTest {
        // Given: Multiple AVAILABLE tokens
        dustDao.insertTokens(listOf(
            createTestToken(nullifier = testNullifier1, state = UtxoState.AVAILABLE),
            createTestToken(nullifier = testNullifier2, state = UtxoState.AVAILABLE),
            createTestToken(nullifier = testNullifier3, state = UtxoState.AVAILABLE)
        ))

        // When: Mark first two as pending
        dustDao.markAsPending(listOf(testNullifier1, testNullifier2))

        // Then: First two are PENDING, third is still AVAILABLE
        assertEquals(UtxoState.PENDING, dustDao.getTokenByNullifier(testNullifier1)?.state)
        assertEquals(UtxoState.PENDING, dustDao.getTokenByNullifier(testNullifier2)?.state)
        assertEquals(UtxoState.AVAILABLE, dustDao.getTokenByNullifier(testNullifier3)?.state)
    }

    @Test
    fun `markAsSpent changes state to SPENT`() = runTest {
        // Given: PENDING token
        val token = createTestToken(
            nullifier = testNullifier1,
            state = UtxoState.PENDING
        )
        dustDao.insertToken(token)

        // When: Mark as spent
        dustDao.markAsSpent(testNullifier1)

        // Then: State is SPENT
        val updated = dustDao.getTokenByNullifier(testNullifier1)
        assertEquals(UtxoState.SPENT, updated?.state)
    }

    @Test
    fun `markAsAvailable changes state to AVAILABLE`() = runTest {
        // Given: PENDING token (transaction failed)
        val token = createTestToken(
            nullifier = testNullifier1,
            state = UtxoState.PENDING
        )
        dustDao.insertToken(token)

        // When: Mark as available (unlock)
        dustDao.markAsAvailable(testNullifier1)

        // Then: State is AVAILABLE
        val updated = dustDao.getTokenByNullifier(testNullifier1)
        assertEquals(UtxoState.AVAILABLE, updated?.state)
    }

    // ==================== Query Operations ====================

    @Test
    fun `getAvailableTokens returns only AVAILABLE tokens`() = runTest {
        // Given: Tokens with different states
        dustDao.insertTokens(listOf(
            createTestToken(nullifier = testNullifier1, state = UtxoState.AVAILABLE),
            createTestToken(nullifier = testNullifier2, state = UtxoState.PENDING),
            createTestToken(nullifier = testNullifier3, state = UtxoState.SPENT)
        ))

        // When: Get available tokens
        val available = dustDao.getAvailableTokens(testAddress)

        // Then: Only AVAILABLE token returned
        assertEquals(1, available.size)
        assertEquals(testNullifier1, available[0].nullifier)
    }

    @Test
    fun `getAvailableTokensSorted returns tokens sorted by initial value`() = runTest {
        // Given: Tokens with different values
        dustDao.insertTokens(listOf(
            createTestToken(nullifier = testNullifier1, initialValue = "3000"),
            createTestToken(nullifier = testNullifier2, initialValue = "1000"),
            createTestToken(nullifier = testNullifier3, initialValue = "2000")
        ))

        // When: Get sorted
        val sorted = dustDao.getAvailableTokensSorted(testAddress)

        // Then: Sorted by value ascending
        assertEquals(3, sorted.size)
        assertEquals("1000", sorted[0].initialValue)
        assertEquals("2000", sorted[1].initialValue)
        assertEquals("3000", sorted[2].initialValue)
    }

    @Test
    fun `getTokenByNullifier returns correct token`() = runTest {
        // Given: Multiple tokens
        dustDao.insertTokens(listOf(
            createTestToken(nullifier = testNullifier1, initialValue = "1000"),
            createTestToken(nullifier = testNullifier2, initialValue = "2000")
        ))

        // When: Get by nullifier
        val token = dustDao.getTokenByNullifier(testNullifier2)

        // Then: Correct token returned
        assertNotNull(token)
        assertEquals(testNullifier2, token?.nullifier)
        assertEquals("2000", token?.initialValue)
    }

    @Test
    fun `getTokenByNullifier returns null for non-existent token`() = runTest {
        // When: Query non-existent nullifier
        val token = dustDao.getTokenByNullifier("0xnonexistent")

        // Then: Returns null
        assertNull(token)
    }

    @Test
    fun `getTokensByNightUtxo returns tokens for specific Night UTXO`() = runTest {
        // Given: Tokens backed by different Night UTXOs
        val nightUtxo1 = "intent1:0"
        val nightUtxo2 = "intent2:0"

        dustDao.insertTokens(listOf(
            createTestToken(nullifier = testNullifier1, nightUtxoId = nightUtxo1),
            createTestToken(nullifier = testNullifier2, nightUtxoId = nightUtxo1),
            createTestToken(nullifier = testNullifier3, nightUtxoId = nightUtxo2)
        ))

        // When: Get tokens for nightUtxo1
        val tokens = dustDao.getTokensByNightUtxo(nightUtxo1)

        // Then: Only tokens backed by nightUtxo1
        assertEquals(2, tokens.size)
        assertEquals(nightUtxo1, tokens[0].nightUtxoId)
        assertEquals(nightUtxo1, tokens[1].nightUtxoId)
    }

    // ==================== Flow Observation Tests ====================

    @Test
    fun `observeAvailableTokens emits current tokens`() = runTest {
        // Given: AVAILABLE tokens
        dustDao.insertTokens(listOf(
            createTestToken(nullifier = testNullifier1, state = UtxoState.AVAILABLE),
            createTestToken(nullifier = testNullifier2, state = UtxoState.AVAILABLE)
        ))

        // When: Observe
        val tokens = dustDao.observeAvailableTokens(testAddress).first()

        // Then: Current tokens emitted
        assertEquals(2, tokens.size)
    }

    @Test
    fun `observePendingTokens emits only PENDING tokens`() = runTest {
        // Given: Mix of states
        dustDao.insertTokens(listOf(
            createTestToken(nullifier = testNullifier1, state = UtxoState.AVAILABLE),
            createTestToken(nullifier = testNullifier2, state = UtxoState.PENDING),
            createTestToken(nullifier = testNullifier3, state = UtxoState.PENDING)
        ))

        // When: Observe pending
        val tokens = dustDao.observePendingTokens(testAddress).first()

        // Then: Only PENDING tokens
        assertEquals(2, tokens.size)
        tokens.forEach {
            assertEquals(UtxoState.PENDING, it.state)
        }
    }

    // ==================== Statistics Tests ====================

    @Test
    fun `count returns total number of tokens`() = runTest {
        // Given: Multiple tokens
        dustDao.insertTokens(listOf(
            createTestToken(nullifier = testNullifier1),
            createTestToken(nullifier = testNullifier2),
            createTestToken(nullifier = testNullifier3)
        ))

        // When: Count
        val count = dustDao.count()

        // Then: Correct count
        assertEquals(3, count)
    }

    @Test
    fun `countAvailable returns only AVAILABLE tokens`() = runTest {
        // Given: Tokens with different states
        dustDao.insertTokens(listOf(
            createTestToken(nullifier = testNullifier1, state = UtxoState.AVAILABLE),
            createTestToken(nullifier = testNullifier2, state = UtxoState.AVAILABLE),
            createTestToken(nullifier = testNullifier3, state = UtxoState.PENDING)
        ))

        // When: Count available
        val count = dustDao.countAvailable(testAddress)

        // Then: Only AVAILABLE counted
        assertEquals(2, count)
    }

    // ==================== Cleanup Tests ====================

    @Test
    fun `deleteTokensForAddress deletes tokens for specific address`() = runTest {
        // Given: Tokens for two addresses
        val address2 = "mn_addr_testnet1other456"
        dustDao.insertTokens(listOf(
            createTestToken(nullifier = testNullifier1, address = testAddress),
            createTestToken(nullifier = testNullifier2, address = testAddress),
            createTestToken(nullifier = testNullifier3, address = address2)
        ))

        // When: Delete for testAddress
        dustDao.deleteTokensForAddress(testAddress)

        // Then: Only testAddress tokens deleted
        assertEquals(1, dustDao.count())
        assertEquals(address2, dustDao.getTokenByNullifier(testNullifier3)?.address)
    }

    @Test
    fun `deleteAll removes all tokens`() = runTest {
        // Given: Multiple tokens
        dustDao.insertTokens(listOf(
            createTestToken(nullifier = testNullifier1),
            createTestToken(nullifier = testNullifier2),
            createTestToken(nullifier = testNullifier3)
        ))

        // When: Delete all
        dustDao.deleteAll()

        // Then: Database empty
        assertEquals(0, dustDao.count())
    }

    // ==================== Helper Methods ====================

    private fun createTestToken(
        nullifier: String = "0x1234567890abcdef",
        address: String = testAddress,
        initialValue: String = "1000000",
        creationTimeMillis: Long = 0L,
        nightUtxoId: String = "intentHash:0",
        nightValueStars: String = "1000000",
        dustCapacitySpecks: String = "5000000000000",
        generationRatePerSecond: String = "8267000000",
        state: UtxoState = UtxoState.AVAILABLE
    ): DustTokenEntity {
        return DustTokenEntity.create(
            nullifier = nullifier,
            address = address,
            initialValue = initialValue,
            creationTimeMillis = creationTimeMillis,
            nightUtxoId = nightUtxoId,
            nightValueStars = nightValueStars,
            dustCapacitySpecks = dustCapacitySpecks,
            generationRatePerSecond = generationRatePerSecond,
            state = state
        )
    }
}
