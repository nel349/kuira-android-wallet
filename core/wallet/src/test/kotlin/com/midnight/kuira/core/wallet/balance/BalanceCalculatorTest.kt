package com.midnight.kuira.core.wallet.balance

import com.midnight.kuira.core.wallet.model.EventType
import com.midnight.kuira.core.wallet.model.LedgerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigInteger

/**
 * Tests for BalanceCalculator.
 *
 * **Critical:** These tests validate financial calculations.
 * Balance errors = user fund loss.
 */
class BalanceCalculatorTest {

    private val calculator = BalanceCalculator()
    private val testAddress = "mn_addr_test123"
    private val otherAddress = "mn_addr_other456"

    // Test helper to create events
    private fun createEvent(
        id: Long,
        type: EventType,
        amount: Long,
        sender: String? = null,
        receiver: String,
        blockHeight: Long = id
    ) = LedgerEvent(
        id = id,
        type = type,
        amount = BigInteger.valueOf(amount),
        tokenType = "MIDNIGHT",
        sender = sender,
        receiver = receiver,
        blockHeight = blockHeight,
        timestamp = System.currentTimeMillis()
    )

    @Test
    fun `coinbase event increases unshielded balance`() {
        val events = listOf(
            createEvent(
                id = 1,
                type = EventType.COINBASE,
                amount = 1_000_000,
                receiver = testAddress
            )
        )

        val balance = calculator.calculate(events, testAddress)

        assertEquals(BigInteger.valueOf(1_000_000), balance.unshielded)
        assertEquals(BigInteger.ZERO, balance.shielded)
        assertEquals(BigInteger.ZERO, balance.dust)
    }

    @Test
    fun `receiving unshielded transfer increases balance`() {
        val events = listOf(
            createEvent(
                id = 1,
                type = EventType.UNSHIELDED_TRANSFER,
                amount = 500_000,
                sender = otherAddress,
                receiver = testAddress
            )
        )

        val balance = calculator.calculate(events, testAddress)

        assertEquals(BigInteger.valueOf(500_000), balance.unshielded)
    }

    @Test
    fun `sending unshielded transfer decreases balance`() {
        val events = listOf(
            // First receive funds
            createEvent(
                id = 1,
                type = EventType.COINBASE,
                amount = 1_000_000,
                receiver = testAddress
            ),
            // Then send funds
            createEvent(
                id = 2,
                type = EventType.UNSHIELDED_TRANSFER,
                amount = 300_000,
                sender = testAddress,
                receiver = otherAddress
            )
        )

        val balance = calculator.calculate(events, testAddress)

        assertEquals(BigInteger.valueOf(700_000), balance.unshielded)
    }

    @Test
    fun `shield converts unshielded to shielded balance`() {
        val events = listOf(
            // Receive unshielded
            createEvent(
                id = 1,
                type = EventType.COINBASE,
                amount = 1_000_000,
                receiver = testAddress
            ),
            // Shield 600k
            createEvent(
                id = 2,
                type = EventType.SHIELD,
                amount = 600_000,
                sender = testAddress,
                receiver = testAddress
            )
        )

        val balance = calculator.calculate(events, testAddress)

        assertEquals(BigInteger.valueOf(400_000), balance.unshielded)
        assertEquals(BigInteger.valueOf(600_000), balance.shielded)
    }

    @Test
    fun `unshield converts shielded to unshielded balance`() {
        val events = listOf(
            // Receive and shield
            createEvent(id = 1, type = EventType.COINBASE, amount = 1_000_000, receiver = testAddress),
            createEvent(id = 2, type = EventType.SHIELD, amount = 1_000_000, sender = testAddress, receiver = testAddress),
            // Unshield 400k
            createEvent(
                id = 3,
                type = EventType.UNSHIELD,
                amount = 400_000,
                sender = testAddress,
                receiver = testAddress
            )
        )

        val balance = calculator.calculate(events, testAddress)

        assertEquals(BigInteger.valueOf(400_000), balance.unshielded)
        assertEquals(BigInteger.valueOf(600_000), balance.shielded)
    }

    @Test
    fun `shielded transfer between addresses`() {
        val events = listOf(
            // Setup: Get shielded balance
            createEvent(id = 1, type = EventType.COINBASE, amount = 1_000_000, receiver = testAddress),
            createEvent(id = 2, type = EventType.SHIELD, amount = 1_000_000, sender = testAddress, receiver = testAddress),
            // Send shielded
            createEvent(
                id = 3,
                type = EventType.SHIELDED_TRANSFER,
                amount = 300_000,
                sender = testAddress,
                receiver = otherAddress
            )
        )

        val balance = calculator.calculate(events, testAddress)

        assertEquals(BigInteger.valueOf(700_000), balance.shielded)
    }

    @Test
    fun `multiple events calculate correctly`() {
        val events = listOf(
            createEvent(id = 1, type = EventType.COINBASE, amount = 1_000_000, receiver = testAddress),
            createEvent(id = 2, type = EventType.UNSHIELDED_TRANSFER, amount = 200_000, sender = otherAddress, receiver = testAddress),
            createEvent(id = 3, type = EventType.SHIELD, amount = 500_000, sender = testAddress, receiver = testAddress),
            createEvent(id = 4, type = EventType.SHIELDED_TRANSFER, amount = 100_000, sender = testAddress, receiver = otherAddress),
            createEvent(id = 5, type = EventType.UNSHIELD, amount = 200_000, sender = testAddress, receiver = testAddress)
        )

        val balance = calculator.calculate(events, testAddress)

        // 1M coinbase + 200k transfer - 500k shield = 700k unshielded
        // 500k shield - 100k shielded transfer - 200k unshield = 200k shielded
        // 200k unshield added back = 900k unshielded
        assertEquals(BigInteger.valueOf(900_000), balance.unshielded)
        assertEquals(BigInteger.valueOf(200_000), balance.shielded)
    }

    @Test
    fun `events processed in chronological order by id`() {
        // Events out of order
        val events = listOf(
            createEvent(id = 3, type = EventType.UNSHIELDED_TRANSFER, amount = 100_000, sender = testAddress, receiver = otherAddress),
            createEvent(id = 1, type = EventType.COINBASE, amount = 1_000_000, receiver = testAddress),
            createEvent(id = 2, type = EventType.UNSHIELDED_TRANSFER, amount = 50_000, sender = testAddress, receiver = otherAddress)
        )

        val balance = calculator.calculate(events, testAddress)

        // Should process: 1M coinbase, -50k, -100k = 850k
        assertEquals(BigInteger.valueOf(850_000), balance.unshielded)
    }

    @Test
    fun `underflow throws BalanceUnderflowException`() {
        val events = listOf(
            // Try to spend without having funds
            createEvent(
                id = 1,
                type = EventType.UNSHIELDED_TRANSFER,
                amount = 1_000_000,
                sender = testAddress,
                receiver = otherAddress
            )
        )

        try {
            calculator.calculate(events, testAddress)
            fail("Expected BalanceUnderflowException")
        } catch (e: BalanceUnderflowException) {
            // Expected
            assertTrue(e.message!!.contains("Unshielded balance underflow"))
        }
    }

    @Test
    fun `shielded underflow throws exception`() {
        val events = listOf(
            // Try to send shielded funds without having any
            createEvent(
                id = 1,
                type = EventType.SHIELDED_TRANSFER,
                amount = 1_000_000,
                sender = testAddress,
                receiver = otherAddress
            )
        )

        try {
            calculator.calculate(events, testAddress)
            fail("Expected BalanceUnderflowException")
        } catch (e: BalanceUnderflowException) {
            // Expected
            assertTrue(e.message!!.contains("Shielded balance underflow"))
        }
    }

    @Test
    fun `shield more than unshielded balance throws exception`() {
        val events = listOf(
            createEvent(id = 1, type = EventType.COINBASE, amount = 500_000, receiver = testAddress),
            createEvent(
                id = 2,
                type = EventType.SHIELD,
                amount = 1_000_000, // Try to shield more than we have
                sender = testAddress,
                receiver = testAddress
            )
        )

        try {
            calculator.calculate(events, testAddress)
            fail("Expected BalanceUnderflowException")
        } catch (e: BalanceUnderflowException) {
            // Expected
            assertTrue(e.message!!.contains("Unshielded balance underflow"))
        }
    }

    @Test
    fun `empty event list returns zero balance`() {
        val balance = calculator.calculate(emptyList(), testAddress)

        assertEquals(BigInteger.ZERO, balance.unshielded)
        assertEquals(BigInteger.ZERO, balance.shielded)
        assertEquals(BigInteger.ZERO, balance.dust)
    }

    @Test
    fun `events for other address do not affect balance`() {
        val events = listOf(
            createEvent(id = 1, type = EventType.COINBASE, amount = 1_000_000, receiver = otherAddress),
            createEvent(id = 2, type = EventType.UNSHIELDED_TRANSFER, amount = 500_000, sender = otherAddress, receiver = "mn_addr_third")
        )

        val balance = calculator.calculate(events, testAddress)

        assertEquals(BigInteger.ZERO, balance.unshielded)
        assertEquals(BigInteger.ZERO, balance.shielded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank wallet address throws exception`() {
        calculator.calculate(emptyList(), "")
    }

    @Test
    fun `total balance sums all types`() {
        val events = listOf(
            createEvent(id = 1, type = EventType.COINBASE, amount = 1_000_000, receiver = testAddress),
            createEvent(id = 2, type = EventType.SHIELD, amount = 600_000, sender = testAddress, receiver = testAddress)
        )

        val balance = calculator.calculate(events, testAddress)

        // 400k unshielded + 600k shielded = 1M total
        assertEquals(BigInteger.valueOf(1_000_000), balance.total)
    }

    @Test
    fun `large amounts do not overflow`() {
        // Test with amounts near Long.MAX_VALUE
        val largeAmount = Long.MAX_VALUE / 2
        val events = listOf(
            createEvent(id = 1, type = EventType.COINBASE, amount = largeAmount, receiver = testAddress),
            createEvent(id = 2, type = EventType.COINBASE, amount = largeAmount, receiver = testAddress)
        )

        val balance = calculator.calculate(events, testAddress)

        // BigInteger should handle this without overflow
        assertEquals(BigInteger.valueOf(largeAmount).multiply(BigInteger.valueOf(2)), balance.unshielded)
    }

    @Test
    fun `receiving and sending to self nets zero change`() {
        val events = listOf(
            createEvent(id = 1, type = EventType.COINBASE, amount = 1_000_000, receiver = testAddress),
            createEvent(
                id = 2,
                type = EventType.UNSHIELDED_TRANSFER,
                amount = 500_000,
                sender = testAddress,
                receiver = testAddress // Send to self
            )
        )

        val balance = calculator.calculate(events, testAddress)

        // +500k received, -500k sent = net 0, plus 1M coinbase = 1M
        assertEquals(BigInteger.valueOf(1_000_000), balance.unshielded)
    }
}
