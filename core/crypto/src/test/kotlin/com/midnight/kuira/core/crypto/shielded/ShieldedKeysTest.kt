// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.crypto.shielded

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [ShieldedKeys] data class.
 */
class ShieldedKeysTest {

    @Test
    fun `given valid 64-char hex keys when creating ShieldedKeys then succeeds`() {
        val coinPk = "274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a"
        val encPk = "f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b"

        val keys = ShieldedKeys(
            coinPublicKey = coinPk,
            encryptionPublicKey = encPk
        )

        assertEquals(coinPk, keys.coinPublicKey)
        assertEquals(encPk, keys.encryptionPublicKey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given coin key with wrong length when creating ShieldedKeys then throws`() {
        ShieldedKeys(
            coinPublicKey = "274c79e9", // Too short
            encryptionPublicKey = "f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given encryption key with wrong length when creating ShieldedKeys then throws`() {
        ShieldedKeys(
            coinPublicKey = "274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a",
            encryptionPublicKey = "f3ae706b" // Too short
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given uppercase hex when creating ShieldedKeys then throws`() {
        ShieldedKeys(
            coinPublicKey = "274C79E90FDF0E29468299FF624DC7092423041BA3976B76464FEAE3A07B994A", // Uppercase
            encryptionPublicKey = "f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given non-hex characters when creating ShieldedKeys then throws`() {
        ShieldedKeys(
            coinPublicKey = "274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994g", // 'g' is invalid
            encryptionPublicKey = "f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b"
        )
    }

    @Test
    fun `given valid keys when calling coinPublicKeyBytes then returns correct bytes`() {
        val keys = ShieldedKeys(
            coinPublicKey = "274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a",
            encryptionPublicKey = "f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b"
        )

        val bytes = keys.coinPublicKeyBytes()

        assertEquals(32, bytes.size)
        assertEquals(0x27.toByte(), bytes[0])
        assertEquals(0x4c.toByte(), bytes[1])
        assertEquals(0x79.toByte(), bytes[2])
        assertEquals(0xe9.toByte(), bytes[3])
    }

    @Test
    fun `given valid keys when calling encryptionPublicKeyBytes then returns correct bytes`() {
        val keys = ShieldedKeys(
            coinPublicKey = "274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a",
            encryptionPublicKey = "f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b"
        )

        val bytes = keys.encryptionPublicKeyBytes()

        assertEquals(32, bytes.size)
        assertEquals(0xf3.toByte(), bytes[0])
        assertEquals(0xae.toByte(), bytes[1])
        assertEquals(0x70.toByte(), bytes[2])
        assertEquals(0x6b.toByte(), bytes[3])
    }

    @Test
    fun `given ShieldedKeys when calling toString then masks keys`() {
        val keys = ShieldedKeys(
            coinPublicKey = "274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a",
            encryptionPublicKey = "f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b"
        )

        val str = keys.toString()

        // Should show only first 8 characters
        assertTrue(str.contains("274c79e9"))
        assertTrue(str.contains("f3ae706b"))
        // Should not show full keys
        assertFalse(str.contains("274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a"))
    }

    @Test
    fun `given two identical ShieldedKeys when comparing then are equal`() {
        val keys1 = ShieldedKeys(
            coinPublicKey = "274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a",
            encryptionPublicKey = "f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b"
        )

        val keys2 = ShieldedKeys(
            coinPublicKey = "274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a",
            encryptionPublicKey = "f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b"
        )

        assertEquals(keys1, keys2)
        assertEquals(keys1.hashCode(), keys2.hashCode())
    }

    @Test
    fun `given two different ShieldedKeys when comparing then are not equal`() {
        val keys1 = ShieldedKeys(
            coinPublicKey = "274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a",
            encryptionPublicKey = "f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b"
        )

        val keys2 = ShieldedKeys(
            coinPublicKey = "274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994b", // Different last char
            encryptionPublicKey = "f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b"
        )

        assertNotEquals(keys1, keys2)
    }
}
