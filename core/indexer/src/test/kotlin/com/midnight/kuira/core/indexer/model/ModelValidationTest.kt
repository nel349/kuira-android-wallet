package com.midnight.kuira.core.indexer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for input validation in model classes.
 *
 * **Critical:** Validates that malformed data from indexer is rejected.
 * Prevents crashes from malicious or corrupted indexer responses.
 */
class ModelValidationTest {

    @Test
    fun `RawLedgerEvent with valid data succeeds`() {
        val event = RawLedgerEvent(
            id = 123,
            rawHex = "deadbeef",
            maxId = 200
        )

        assertEquals(123L, event.id)
        assertEquals("deadbeef", event.rawHex)
        assertEquals(200L, event.maxId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `RawLedgerEvent with negative id throws exception`() {
        RawLedgerEvent(id = -1, rawHex = "deadbeef", maxId = 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `RawLedgerEvent with blank rawHex throws exception`() {
        RawLedgerEvent(id = 1, rawHex = "", maxId = 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `RawLedgerEvent with invalid hex characters throws exception`() {
        RawLedgerEvent(id = 1, rawHex = "notvalidhex!", maxId = 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `RawLedgerEvent with negative maxId throws exception`() {
        RawLedgerEvent(id = 1, rawHex = "deadbeef", maxId = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `RawLedgerEvent with maxId less than id throws exception`() {
        RawLedgerEvent(id = 100, rawHex = "deadbeef", maxId = 50)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `RawLedgerEvent with negative blockHeight throws exception`() {
        RawLedgerEvent(
            id = 1,
            rawHex = "deadbeef",
            maxId = 100,
            blockHeight = -1
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `RawLedgerEvent with zero timestamp throws exception`() {
        RawLedgerEvent(
            id = 1,
            rawHex = "deadbeef",
            maxId = 100,
            timestamp = 0
        )
    }

    @Test
    fun `RawLedgerEvent accepts uppercase hex`() {
        val event = RawLedgerEvent(id = 1, rawHex = "DEADBEEF", maxId = 100)
        assertEquals("DEADBEEF", event.rawHex)
    }

    @Test
    fun `RawLedgerEvent accepts mixed case hex`() {
        val event = RawLedgerEvent(id = 1, rawHex = "DeAdBeEf", maxId = 100)
        assertEquals("DeAdBeEf", event.rawHex)
    }

    @Test
    fun `BlockInfo with valid data succeeds`() {
        val block = BlockInfo(
            height = 100,
            hash = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
            timestamp = System.currentTimeMillis(),
            eventCount = 5
        )

        assertEquals(100L, block.height)
        assertEquals(5, block.eventCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `BlockInfo with negative height throws exception`() {
        BlockInfo(
            height = -1,
            hash = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `BlockInfo with blank hash throws exception`() {
        BlockInfo(
            height = 1,
            hash = "",
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `BlockInfo with non-hex hash throws exception`() {
        BlockInfo(
            height = 1,
            hash = "not-valid-hex!",
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `BlockInfo with short hash throws exception`() {
        BlockInfo(
            height = 1,
            hash = "abc123", // Too short
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `BlockInfo with zero timestamp throws exception`() {
        BlockInfo(
            height = 1,
            hash = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
            timestamp = 0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `BlockInfo with negative eventCount throws exception`() {
        BlockInfo(
            height = 1,
            hash = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
            timestamp = System.currentTimeMillis(),
            eventCount = -1
        )
    }

    @Test
    fun `NetworkState with valid data succeeds`() {
        val state = NetworkState.fromBlockHeights(100, 200)

        assertEquals(100L, state.currentBlock)
        assertEquals(200L, state.maxBlock)
        assertEquals(0.5f, state.syncProgress, 0.01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `NetworkState with negative currentBlock throws exception`() {
        NetworkState(
            currentBlock = -1,
            maxBlock = 100,
            syncProgress = 0.5f,
            isFullySynced = false
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `NetworkState with negative maxBlock throws exception`() {
        NetworkState(
            currentBlock = 50,
            maxBlock = -1,
            syncProgress = 0.5f,
            isFullySynced = false
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `NetworkState with currentBlock greater than maxBlock throws exception`() {
        NetworkState(
            currentBlock = 200,
            maxBlock = 100,
            syncProgress = 1.0f,
            isFullySynced = false
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `NetworkState with syncProgress less than 0 throws exception`() {
        NetworkState(
            currentBlock = 50,
            maxBlock = 100,
            syncProgress = -0.1f,
            isFullySynced = false
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `NetworkState with syncProgress greater than 1 throws exception`() {
        NetworkState(
            currentBlock = 50,
            maxBlock = 100,
            syncProgress = 1.5f,
            isFullySynced = false
        )
    }

    @Test
    fun `NetworkState fromBlockHeights calculates progress correctly`() {
        val state = NetworkState.fromBlockHeights(50, 100)

        assertEquals(0.5f, state.syncProgress, 0.01f)
        assertEquals(false, state.isFullySynced)
    }

    @Test
    fun `NetworkState fromBlockHeights marks fully synced when equal`() {
        val state = NetworkState.fromBlockHeights(100, 100)

        assertEquals(1.0f, state.syncProgress, 0.01f)
        assertEquals(true, state.isFullySynced)
    }

    @Test
    fun `NetworkState fromBlockHeights handles zero maxBlock`() {
        val state = NetworkState.fromBlockHeights(0, 0)

        assertEquals(0.0f, state.syncProgress, 0.01f)
        assertEquals(true, state.isFullySynced)
    }
}
