// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.ledger.model

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class UnshieldedOfferTest {

    @Test
    fun `given valid inputs and outputs when creating UnshieldedOffer then succeeds`() {
        // Given
        val input = UtxoSpend(
            intentHash = "0x123",
            outputNo = 0,
            value = BigInteger("1000000"),
            owner = "mn_addr_testnet1abc",
            ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )
        val output = UtxoOutput(
            value = BigInteger("1000000"),
            owner = "mn_addr_testnet1xyz",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )

        // When
        val offer = UnshieldedOffer(
            inputs = listOf(input),
            outputs = listOf(output),
            signatures = emptyList()
        )

        // Then
        assertEquals(1, offer.inputs.size)
        assertEquals(1, offer.outputs.size)
        assertTrue(offer.signatures.isEmpty())
    }

    @Test
    fun `given empty inputs when creating UnshieldedOffer then throws`() {
        // Given
        val output = UtxoOutput(
            value = BigInteger.ONE,
            owner = "mn_addr_testnet1xyz",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )

        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            UnshieldedOffer(
                inputs = emptyList(),
                outputs = listOf(output)
            )
        }
    }

    @Test
    fun `given empty outputs when creating UnshieldedOffer then throws`() {
        // Given
        val input = UtxoSpend(
            intentHash = "0x123",
            outputNo = 0,
            value = BigInteger.ONE,
            owner = "mn_addr_testnet1abc",
            ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )

        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            UnshieldedOffer(
                inputs = listOf(input),
                outputs = emptyList()
            )
        }
    }

    @Test
    fun `given mismatched signature count when creating UnshieldedOffer then throws`() {
        // Given
        val input = UtxoSpend(
            intentHash = "0x123",
            outputNo = 0,
            value = BigInteger.ONE,
            owner = "mn_addr_testnet1abc",
            ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )
        val output = UtxoOutput(
            value = BigInteger.ONE,
            owner = "mn_addr_testnet1xyz",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )
        val signature = ByteArray(64) { 0xFF.toByte() }

        // When/Then (2 inputs but 1 signature)
        assertThrows(IllegalArgumentException::class.java) {
            UnshieldedOffer(
                inputs = listOf(input, input.copy(outputNo = 1)),
                outputs = listOf(output),
                signatures = listOf(signature)
            )
        }
    }

    @Test
    fun `given invalid signature size when creating UnshieldedOffer then throws`() {
        // Given
        val input = UtxoSpend(
            intentHash = "0x123",
            outputNo = 0,
            value = BigInteger.ONE,
            owner = "mn_addr_testnet1abc",
            ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )
        val output = UtxoOutput(
            value = BigInteger.ONE,
            owner = "mn_addr_testnet1xyz",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )
        val invalidSignature = ByteArray(32)  // Should be 64 bytes

        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            UnshieldedOffer(
                inputs = listOf(input),
                outputs = listOf(output),
                signatures = listOf(invalidSignature)
            )
        }
    }

    @Test
    fun `given balanced offer when checking balance then returns true`() {
        // Given
        val input = UtxoSpend(
            intentHash = "0x123",
            outputNo = 0,
            value = BigInteger("150"),
            owner = "mn_addr_testnet1abc",
            ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )
        val recipientOutput = UtxoOutput(
            value = BigInteger("100"),
            owner = "mn_addr_testnet1xyz",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )
        val changeOutput = UtxoOutput(
            value = BigInteger("50"),
            owner = "mn_addr_testnet1abc",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )

        val offer = UnshieldedOffer(
            inputs = listOf(input),
            outputs = listOf(recipientOutput, changeOutput)
        )

        // When/Then
        assertTrue(offer.isBalanced())
    }

    @Test
    fun `given unbalanced offer when checking balance then returns false`() {
        // Given
        val input = UtxoSpend(
            intentHash = "0x123",
            outputNo = 0,
            value = BigInteger("100"),
            owner = "mn_addr_testnet1abc",
            ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )
        val output = UtxoOutput(
            value = BigInteger("150"),  // More than input!
            owner = "mn_addr_testnet1xyz",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )

        val offer = UnshieldedOffer(
            inputs = listOf(input),
            outputs = listOf(output)
        )

        // When/Then
        assertFalse(offer.isBalanced())
    }

    @Test
    fun `given multi-token balanced offer when checking balance then returns true`() {
        // Given - Two token types, both balanced
        val nightInput = UtxoSpend(
            intentHash = "0x123",
            outputNo = 0,
            value = BigInteger("1000"),
            owner = "mn_addr_testnet1abc",
            ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )
        val dustInput = UtxoSpend(
            intentHash = "0x456",
            outputNo = 0,
            value = BigInteger("500"),
            owner = "mn_addr_testnet1abc",
            ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
            tokenType = "a".repeat(64)
        )
        val nightOutput = UtxoOutput(
            value = BigInteger("1000"),
            owner = "mn_addr_testnet1xyz",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )
        val dustOutput = UtxoOutput(
            value = BigInteger("500"),
            owner = "mn_addr_testnet1xyz",
            tokenType = "a".repeat(64)
        )

        val offer = UnshieldedOffer(
            inputs = listOf(nightInput, dustInput),
            outputs = listOf(nightOutput, dustOutput)
        )

        // When/Then
        assertTrue(offer.isBalanced())
    }

    @Test
    fun `given unsigned offer when checking if signed then returns false`() {
        // Given
        val input = UtxoSpend(
            intentHash = "0x123",
            outputNo = 0,
            value = BigInteger("100"),
            owner = "mn_addr_testnet1abc",
            ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )
        val output = UtxoOutput(
            value = BigInteger("100"),
            owner = "mn_addr_testnet1xyz",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )

        val offer = UnshieldedOffer(
            inputs = listOf(input),
            outputs = listOf(output),
            signatures = emptyList()
        )

        // When/Then
        assertFalse(offer.isSigned())
    }

    @Test
    fun `given fully signed offer when checking if signed then returns true`() {
        // Given
        val input = UtxoSpend(
            intentHash = "0x123",
            outputNo = 0,
            value = BigInteger("100"),
            owner = "mn_addr_testnet1abc",
            ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )
        val output = UtxoOutput(
            value = BigInteger("100"),
            owner = "mn_addr_testnet1xyz",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )
        val signature = ByteArray(64) { 0xFF.toByte() }

        val offer = UnshieldedOffer(
            inputs = listOf(input),
            outputs = listOf(output),
            signatures = listOf(signature)
        )

        // When/Then
        assertTrue(offer.isSigned())
    }

    @Test
    fun `given offer when calculating total input then sums correctly`() {
        // Given
        val input1 = UtxoSpend(
            intentHash = "0x123",
            outputNo = 0,
            value = BigInteger("100"),
            owner = "mn_addr_testnet1abc",
            ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )
        val input2 = UtxoSpend(
            intentHash = "0x456",
            outputNo = 0,
            value = BigInteger("200"),
            owner = "mn_addr_testnet1abc",
            ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )
        val output = UtxoOutput(
            value = BigInteger("300"),
            owner = "mn_addr_testnet1xyz",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )

        val offer = UnshieldedOffer(
            inputs = listOf(input1, input2),
            outputs = listOf(output)
        )

        // When
        val totalInput = offer.totalInput(UtxoSpend.NATIVE_TOKEN_TYPE)

        // Then
        assertEquals(BigInteger("300"), totalInput)
    }

    @Test
    fun `given offer when calculating total output then sums correctly`() {
        // Given
        val input = UtxoSpend(
            intentHash = "0x123",
            outputNo = 0,
            value = BigInteger("300"),
            owner = "mn_addr_testnet1abc",
            ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )
        val output1 = UtxoOutput(
            value = BigInteger("200"),
            owner = "mn_addr_testnet1xyz",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )
        val output2 = UtxoOutput(
            value = BigInteger("100"),
            owner = "mn_addr_testnet1abc",  // Change
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )

        val offer = UnshieldedOffer(
            inputs = listOf(input),
            outputs = listOf(output1, output2)
        )

        // When
        val totalOutput = offer.totalOutput(UtxoOutput.NATIVE_TOKEN_TYPE)

        // Then
        assertEquals(BigInteger("300"), totalOutput)
    }

    @Test
    fun `given two offers with same data when comparing then equal`() {
        // Given
        val input = UtxoSpend(
            intentHash = "0x123",
            outputNo = 0,
            value = BigInteger("100"),
            owner = "mn_addr_testnet1abc",
            ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
            tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
        )
        val output = UtxoOutput(
            value = BigInteger("100"),
            owner = "mn_addr_testnet1xyz",
            tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
        )
        val signature = ByteArray(64) { it.toByte() }

        val offer1 = UnshieldedOffer(
            inputs = listOf(input),
            outputs = listOf(output),
            signatures = listOf(signature)
        )
        val offer2 = UnshieldedOffer(
            inputs = listOf(input),
            outputs = listOf(output),
            signatures = listOf(signature.copyOf())
        )

        // When/Then
        assertEquals(offer1, offer2)
        assertEquals(offer1.hashCode(), offer2.hashCode())
    }
}
