// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.ledger.model

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class UtxoSpendTest {

    @Test
    fun `given valid inputs when creating UtxoSpend then succeeds`() {
        // Given
        val intentHash = "0x123abc"
        val outputNo = 0
        val value = BigInteger("1000000")
        val owner = "mn_addr_testnet1abc"
        val tokenType = UtxoSpend.NATIVE_TOKEN_TYPE

        // When
        val utxoSpend = UtxoSpend(
            intentHash = intentHash,
            outputNo = outputNo,
            value = value,
            owner = owner,
            tokenType = tokenType
        )

        // Then
        assertEquals(intentHash, utxoSpend.intentHash)
        assertEquals(outputNo, utxoSpend.outputNo)
        assertEquals(value, utxoSpend.value)
        assertEquals(owner, utxoSpend.owner)
        assertEquals(tokenType, utxoSpend.tokenType)
    }

    @Test
    fun `given blank intentHash when creating UtxoSpend then throws`() {
        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            UtxoSpend(
                intentHash = "",
                outputNo = 0,
                value = BigInteger.ONE,
                owner = "mn_addr_testnet1abc",
                tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
            )
        }
    }

    @Test
    fun `given negative outputNo when creating UtxoSpend then throws`() {
        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            UtxoSpend(
                intentHash = "0x123",
                outputNo = -1,
                value = BigInteger.ONE,
                owner = "mn_addr_testnet1abc",
                tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
            )
        }
    }

    @Test
    fun `given negative value when creating UtxoSpend then throws`() {
        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            UtxoSpend(
                intentHash = "0x123",
                outputNo = 0,
                value = BigInteger("-1"),
                owner = "mn_addr_testnet1abc",
                tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
            )
        }
    }

    @Test
    fun `given zero value when creating UtxoSpend then succeeds`() {
        // When
        val utxoSpend = UtxoSpend(
            intentHash = "0x123",
            outputNo = 0,
            value = BigInteger.ZERO,
            owner = "mn_addr_testnet1abc",
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )

        // Then
        assertEquals(BigInteger.ZERO, utxoSpend.value)
    }

    @Test
    fun `given blank owner when creating UtxoSpend then throws`() {
        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            UtxoSpend(
                intentHash = "0x123",
                outputNo = 0,
                value = BigInteger.ONE,
                owner = "",
                tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
            )
        }
    }

    @Test
    fun `given invalid tokenType length when creating UtxoSpend then throws`() {
        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            UtxoSpend(
                intentHash = "0x123",
                outputNo = 0,
                value = BigInteger.ONE,
                owner = "mn_addr_testnet1abc",
                tokenType = "0000"  // Too short (needs 64 chars)
            )
        }
    }

    @Test
    fun `given UtxoSpend when getting identifier then returns formatted string`() {
        // Given
        val utxoSpend = UtxoSpend(
            intentHash = "0x123abc",
            outputNo = 5,
            value = BigInteger.ONE,
            owner = "mn_addr_testnet1abc",
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )

        // When
        val identifier = utxoSpend.identifier()

        // Then
        assertEquals("0x123abc:5", identifier)
    }

    @Test
    fun `given NATIVE_TOKEN_TYPE when creating UtxoSpend then uses all zeros`() {
        // Given
        val utxoSpend = UtxoSpend(
            intentHash = "0x123",
            outputNo = 0,
            value = BigInteger.ONE,
            owner = "mn_addr_testnet1abc",
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )

        // Then
        assertEquals(64, utxoSpend.tokenType.length)
        assertTrue(utxoSpend.tokenType.all { it == '0' })
    }
}
