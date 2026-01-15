package com.midnight.kuira.core.wallet.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

/**
 * Tests for LedgerEvent input validation.
 *
 * **Critical:** Ensures deserialized events meet validity requirements.
 */
class LedgerEventValidationTest {

    @Test
    fun `LedgerEvent with valid data succeeds`() {
        val event = LedgerEvent(
            id = 123,
            type = EventType.UNSHIELDED_TRANSFER,
            amount = BigInteger.valueOf(1_000_000),
            tokenType = "MIDNIGHT",
            sender = "mn_addr_sender",
            receiver = "mn_addr_receiver",
            blockHeight = 100,
            timestamp = System.currentTimeMillis()
        )

        assertEquals(123L, event.id)
        assertEquals(EventType.UNSHIELDED_TRANSFER, event.type)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `LedgerEvent with negative id throws exception`() {
        LedgerEvent(
            id = -1,
            type = EventType.COINBASE,
            amount = BigInteger.valueOf(1_000_000),
            receiver = "mn_addr_receiver",
            blockHeight = 1,
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `LedgerEvent with zero amount throws exception`() {
        LedgerEvent(
            id = 1,
            type = EventType.UNSHIELDED_TRANSFER,
            amount = BigInteger.ZERO,
            sender = "mn_addr_sender",
            receiver = "mn_addr_receiver",
            blockHeight = 1,
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `LedgerEvent with negative amount throws exception`() {
        LedgerEvent(
            id = 1,
            type = EventType.UNSHIELDED_TRANSFER,
            amount = BigInteger.valueOf(-1000),
            sender = "mn_addr_sender",
            receiver = "mn_addr_receiver",
            blockHeight = 1,
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `LedgerEvent with blank tokenType throws exception`() {
        LedgerEvent(
            id = 1,
            type = EventType.UNSHIELDED_TRANSFER,
            amount = BigInteger.valueOf(1_000_000),
            tokenType = "",
            sender = "mn_addr_sender",
            receiver = "mn_addr_receiver",
            blockHeight = 1,
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `COINBASE event with sender throws exception`() {
        LedgerEvent(
            id = 1,
            type = EventType.COINBASE,
            amount = BigInteger.valueOf(1_000_000),
            sender = "mn_addr_sender", // Should be null
            receiver = "mn_addr_receiver",
            blockHeight = 1,
            timestamp = System.currentTimeMillis()
        )
    }

    @Test
    fun `COINBASE event with null sender succeeds`() {
        val event = LedgerEvent(
            id = 1,
            type = EventType.COINBASE,
            amount = BigInteger.valueOf(1_000_000),
            sender = null,
            receiver = "mn_addr_receiver",
            blockHeight = 1,
            timestamp = System.currentTimeMillis()
        )

        assertEquals(EventType.COINBASE, event.type)
        assertEquals(null, event.sender)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `UNSHIELDED_TRANSFER with null sender throws exception`() {
        LedgerEvent(
            id = 1,
            type = EventType.UNSHIELDED_TRANSFER,
            amount = BigInteger.valueOf(1_000_000),
            sender = null, // Must have sender
            receiver = "mn_addr_receiver",
            blockHeight = 1,
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `UNSHIELDED_TRANSFER with blank sender throws exception`() {
        LedgerEvent(
            id = 1,
            type = EventType.UNSHIELDED_TRANSFER,
            amount = BigInteger.valueOf(1_000_000),
            sender = "",
            receiver = "mn_addr_receiver",
            blockHeight = 1,
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SHIELD event with null sender throws exception`() {
        LedgerEvent(
            id = 1,
            type = EventType.SHIELD,
            amount = BigInteger.valueOf(1_000_000),
            sender = null,
            receiver = "mn_addr_receiver",
            blockHeight = 1,
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `UNSHIELD event with null sender throws exception`() {
        LedgerEvent(
            id = 1,
            type = EventType.UNSHIELD,
            amount = BigInteger.valueOf(1_000_000),
            sender = null,
            receiver = "mn_addr_receiver",
            blockHeight = 1,
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SHIELDED_TRANSFER with null sender throws exception`() {
        LedgerEvent(
            id = 1,
            type = EventType.SHIELDED_TRANSFER,
            amount = BigInteger.valueOf(1_000_000),
            sender = null,
            receiver = "mn_addr_receiver",
            blockHeight = 1,
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `LedgerEvent with blank receiver throws exception`() {
        LedgerEvent(
            id = 1,
            type = EventType.COINBASE,
            amount = BigInteger.valueOf(1_000_000),
            receiver = "",
            blockHeight = 1,
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `LedgerEvent with negative blockHeight throws exception`() {
        LedgerEvent(
            id = 1,
            type = EventType.COINBASE,
            amount = BigInteger.valueOf(1_000_000),
            receiver = "mn_addr_receiver",
            blockHeight = -1,
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `LedgerEvent with zero timestamp throws exception`() {
        LedgerEvent(
            id = 1,
            type = EventType.COINBASE,
            amount = BigInteger.valueOf(1_000_000),
            receiver = "mn_addr_receiver",
            blockHeight = 1,
            timestamp = 0
        )
    }

    @Test
    fun `CONTRACT_DEPLOY allows null sender`() {
        val event = LedgerEvent(
            id = 1,
            type = EventType.CONTRACT_DEPLOY,
            amount = BigInteger.valueOf(1_000_000),
            sender = null,
            receiver = "mn_addr_receiver",
            blockHeight = 1,
            timestamp = System.currentTimeMillis()
        )

        assertEquals(EventType.CONTRACT_DEPLOY, event.type)
    }

    @Test
    fun `UNKNOWN type allows null sender`() {
        val event = LedgerEvent(
            id = 1,
            type = EventType.UNKNOWN,
            amount = BigInteger.valueOf(1_000_000),
            sender = null,
            receiver = "mn_addr_receiver",
            blockHeight = 1,
            timestamp = System.currentTimeMillis()
        )

        assertEquals(EventType.UNKNOWN, event.type)
    }

    @Test
    fun `large amounts accepted`() {
        val largeAmount = BigInteger("999999999999999999999999")

        val event = LedgerEvent(
            id = 1,
            type = EventType.COINBASE,
            amount = largeAmount,
            receiver = "mn_addr_receiver",
            blockHeight = 1,
            timestamp = System.currentTimeMillis()
        )

        assertEquals(largeAmount, event.amount)
    }
}
