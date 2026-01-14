// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.crypto.shielded

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [MemoryUtils] secure memory wiping.
 */
class MemoryUtilsTest {

    @Test
    fun `given byte array when calling wipe then all bytes are zeroed`() {
        val data = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9A.toByte(), 0xBC.toByte())

        MemoryUtils.wipe(data)

        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0, 0), data)
    }

    @Test
    fun `given empty byte array when calling wipe then succeeds`() {
        val data = byteArrayOf()

        MemoryUtils.wipe(data)

        assertEquals(0, data.size)
    }

    @Test
    fun `given already zeroed array when calling wipe then remains zeroed`() {
        val data = byteArrayOf(0, 0, 0, 0)

        MemoryUtils.wipe(data)

        assertArrayEquals(byteArrayOf(0, 0, 0, 0), data)
    }

    @Test
    fun `given multiple arrays when calling wipeAll then all are zeroed`() {
        val data1 = byteArrayOf(0x11, 0x22, 0x33)
        val data2 = byteArrayOf(0x44, 0x55, 0x66)
        val data3 = byteArrayOf(0x77.toByte(), 0x88.toByte(), 0x99.toByte())

        MemoryUtils.wipeAll(data1, data2, data3)

        assertArrayEquals(byteArrayOf(0, 0, 0), data1)
        assertArrayEquals(byteArrayOf(0, 0, 0), data2)
        assertArrayEquals(byteArrayOf(0, 0, 0), data3)
    }

    @Test
    fun `given null arrays when calling wipeAll then ignores nulls`() {
        val data1 = byteArrayOf(0x11, 0x22)
        val data2: ByteArray? = null
        val data3 = byteArrayOf(0x33, 0x44)

        // Should not throw
        MemoryUtils.wipeAll(data1, data2, data3)

        assertArrayEquals(byteArrayOf(0, 0), data1)
        assertArrayEquals(byteArrayOf(0, 0), data3)
    }

    @Test
    fun `given byte array when using useAndWipe then wipes after block`() {
        val data = byteArrayOf(0x12, 0x34, 0x56, 0x78)

        val result = MemoryUtils.useAndWipe(data) { bytes ->
            // Verify data is still available inside block
            assertEquals(0x12.toByte(), bytes[0])
            assertEquals(0x34.toByte(), bytes[1])
            "test result"
        }

        // Verify result was returned
        assertEquals("test result", result)

        // Verify data was wiped after block
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), data)
    }

    @Test
    fun `given byte array when useAndWipe throws then still wipes data`() {
        val data = byteArrayOf(0x12, 0x34, 0x56, 0x78)

        try {
            MemoryUtils.useAndWipe(data) {
                throw IllegalStateException("Test exception")
            }
            fail("Should have thrown exception")
        } catch (e: IllegalStateException) {
            assertEquals("Test exception", e.message)
        }

        // Verify data was wiped despite exception
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), data)
    }

    @Test
    fun `given two byte arrays when using useAndWipeAll then wipes both after block`() {
        val data1 = byteArrayOf(0x11, 0x22)
        val data2 = byteArrayOf(0x33, 0x44)

        val result = MemoryUtils.useAndWipeAll(data1, data2) { d1, d2 ->
            // Verify both arrays available inside block
            assertEquals(0x11.toByte(), d1[0])
            assertEquals(0x33.toByte(), d2[0])
            "test result"
        }

        assertEquals("test result", result)

        // Verify both arrays were wiped
        assertArrayEquals(byteArrayOf(0, 0), data1)
        assertArrayEquals(byteArrayOf(0, 0), data2)
    }

    @Test
    fun `given two byte arrays when useAndWipeAll throws then still wipes both`() {
        val data1 = byteArrayOf(0x11, 0x22)
        val data2 = byteArrayOf(0x33, 0x44)

        try {
            MemoryUtils.useAndWipeAll(data1, data2) { _, _ ->
                throw IllegalStateException("Test exception")
            }
            fail("Should have thrown exception")
        } catch (e: IllegalStateException) {
            assertEquals("Test exception", e.message)
        }

        // Verify both arrays were wiped despite exception
        assertArrayEquals(byteArrayOf(0, 0), data1)
        assertArrayEquals(byteArrayOf(0, 0), data2)
    }

    @Test
    fun `given 32-byte seed when wiping then all bytes zeroed`() {
        // Simulate a real seed
        val seed = ByteArray(32) { it.toByte() } // 0x00, 0x01, 0x02, ... 0x1F

        MemoryUtils.wipe(seed)

        assertArrayEquals(ByteArray(32) { 0 }, seed)
    }

    @Test
    fun `given wiped array when calling wipe again then is idempotent`() {
        val data = byteArrayOf(0x12, 0x34, 0x56, 0x78)

        MemoryUtils.wipe(data)
        MemoryUtils.wipe(data) // Second call
        MemoryUtils.wipe(data) // Third call

        assertArrayEquals(byteArrayOf(0, 0, 0, 0), data)
    }
}
