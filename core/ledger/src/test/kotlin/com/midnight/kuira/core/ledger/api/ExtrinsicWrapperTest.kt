package com.midnight.kuira.core.ledger.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtrinsicWrapperTest {

    /**
     * Test compact length encoding against known values from polkadot.js
     */
    @Test
    fun `SCALE compact encoding matches polkadot-js for 569 bytes`() {
        // From dump-extrinsic-format.ts output:
        // Midnight TX: 569 bytes
        // Wrapped extrinsic: f908040500e5086d69...
        //
        // Breaking down:  f9 08 04 05 00 e5 08 [data]
        // Expected compact(569) = e5 08

        val encoded = encodeCompactLength(569)
        val hex = encoded.joinToString("") { "%02x".format(it) }

        assertEquals("SCALE compact(569) should be 'e508'", "e508", hex)
    }

    @Test
    fun `SCALE compact encoding single-byte mode`() {
        // 0-63: single byte 0b00XXXXXX
        assertEquals("00", encodeCompactLength(0).toHex())
        assertEquals("04", encodeCompactLength(1).toHex())
        assertEquals("fc", encodeCompactLength(63).toHex())
    }

    @Test
    fun `SCALE compact encoding two-byte mode`() {
        // 64-16383: two bytes 0b01XXXXXX XXXXXXXX
        // 64 = 0x40, (64 << 2) | 1 = 257 = 0x0101
        assertEquals("0101", encodeCompactLength(64).toHex())

        // 16383 = 0x3FFF, (16383 << 2) | 1 = 65533 = 0xFFFD
        assertEquals("fdff", encodeCompactLength(16383).toHex())
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun encodeCompactLength(value: Int): ByteArray {
        return when {
            value < 64 -> {
                // Single-byte mode: 0b00XXXXXX
                byteArrayOf((value shl 2).toByte())
            }
            value < 16384 -> {
                // Two-byte mode: 0b01XXXXXX XXXXXXXX
                val encoded = (value shl 2) or 0x01
                byteArrayOf(
                    (encoded and 0xFF).toByte(),
                    ((encoded shr 8) and 0xFF).toByte()
                )
            }
            value < 1073741824 -> {
                // Four-byte mode: 0b10XXXXXX ...
                val encoded = (value shl 2) or 0x02
                byteArrayOf(
                    (encoded and 0xFF).toByte(),
                    ((encoded shr 8) and 0xFF).toByte(),
                    ((encoded shr 16) and 0xFF).toByte(),
                    ((encoded shr 24) and 0xFF).toByte()
                )
            }
            else -> {
                throw IllegalArgumentException("Value too large for SCALE compact encoding: $value")
            }
        }
    }
}
