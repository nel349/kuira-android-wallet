// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.ledger.model

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class UtxoOutputTest {

    @Test
    fun `given valid inputs when creating UtxoOutput then succeeds`() {
        // Given
        val value = BigInteger("1000000")
        val owner = "mn_addr_testnet1abc"
        val tokenType = UtxoOutput.NATIVE_TOKEN_TYPE

        // When
        val utxoOutput = UtxoOutput(
            value = value,
            owner = owner,
            tokenType = tokenType
        )

        // Then
        assertEquals(value, utxoOutput.value)
        assertEquals(owner, utxoOutput.owner)
        assertEquals(tokenType, utxoOutput.tokenType)
    }

    @Test
    fun `given zero value when creating UtxoOutput then throws`() {
        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            UtxoOutput(
                value = BigInteger.ZERO,
                owner = "mn_addr_testnet1abc",
                tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
            )
        }
    }

    @Test
    fun `given negative value when creating UtxoOutput then throws`() {
        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            UtxoOutput(
                value = BigInteger("-1"),
                owner = "mn_addr_testnet1abc",
                tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
            )
        }
    }

    @Test
    fun `given blank owner when creating UtxoOutput then throws`() {
        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            UtxoOutput(
                value = BigInteger.ONE,
                owner = "",
                tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
            )
        }
    }

    @Test
    fun `given invalid tokenType length when creating UtxoOutput then throws`() {
        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            UtxoOutput(
                value = BigInteger.ONE,
                owner = "mn_addr_testnet1abc",
                tokenType = "0000"  // Too short (needs 64 chars)
            )
        }
    }

    @Test
    fun `given blank tokenType when creating UtxoOutput then throws`() {
        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            UtxoOutput(
                value = BigInteger.ONE,
                owner = "mn_addr_testnet1abc",
                tokenType = ""
            )
        }
    }

    @Test
    fun `given NATIVE_TOKEN_TYPE when creating UtxoOutput then uses all zeros`() {
        // Given
        val utxoOutput = UtxoOutput(
            value = BigInteger.ONE,
            owner = "mn_addr_testnet1abc",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )

        // Then
        assertEquals(64, utxoOutput.tokenType.length)
        assertTrue(utxoOutput.tokenType.all { it == '0' })
    }

    @Test
    fun `given custom token type when creating UtxoOutput then accepts 64 char hex`() {
        // Given
        val customTokenType = "a".repeat(64)

        // When
        val utxoOutput = UtxoOutput(
            value = BigInteger.ONE,
            owner = "mn_addr_testnet1abc",
            tokenType = customTokenType
        )

        // Then
        assertEquals(customTokenType, utxoOutput.tokenType)
    }

    @Test
    fun `given two UtxoOutputs with same values when comparing then equal`() {
        // Given
        val output1 = UtxoOutput(
            value = BigInteger("1000000"),
            owner = "mn_addr_testnet1abc",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )
        val output2 = UtxoOutput(
            value = BigInteger("1000000"),
            owner = "mn_addr_testnet1abc",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )

        // Then
        assertEquals(output1, output2)
        assertEquals(output1.hashCode(), output2.hashCode())
    }
}
