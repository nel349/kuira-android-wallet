// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.ledger.model

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class IntentTest {

    private fun createTestOffer(): UnshieldedOffer {
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
        return UnshieldedOffer(
            inputs = listOf(input),
            outputs = listOf(output)
        )
    }

    @Test
    fun `given guaranteed offer when creating Intent then succeeds`() {
        // Given
        val offer = createTestOffer()
        val ttl = System.currentTimeMillis() + 60000

        // When
        val intent = Intent(
            guaranteedUnshieldedOffer = offer,
            fallibleUnshieldedOffer = null,
            ttl = ttl
        )

        // Then
        assertNotNull(intent.guaranteedUnshieldedOffer)
        assertNull(intent.fallibleUnshieldedOffer)
        assertEquals(ttl, intent.ttl)
    }

    @Test
    fun `given fallible offer when creating Intent then succeeds`() {
        // Given
        val offer = createTestOffer()
        val ttl = System.currentTimeMillis() + 60000

        // When
        val intent = Intent(
            guaranteedUnshieldedOffer = null,
            fallibleUnshieldedOffer = offer,
            ttl = ttl
        )

        // Then
        assertNull(intent.guaranteedUnshieldedOffer)
        assertNotNull(intent.fallibleUnshieldedOffer)
    }

    @Test
    fun `given both offers when creating Intent then succeeds`() {
        // Given
        val guaranteedOffer = createTestOffer()
        val fallibleOffer = createTestOffer()
        val ttl = System.currentTimeMillis() + 60000

        // When
        val intent = Intent(
            guaranteedUnshieldedOffer = guaranteedOffer,
            fallibleUnshieldedOffer = fallibleOffer,
            ttl = ttl
        )

        // Then
        assertNotNull(intent.guaranteedUnshieldedOffer)
        assertNotNull(intent.fallibleUnshieldedOffer)
    }

    @Test
    fun `given no offers when creating Intent then throws`() {
        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            Intent(
                guaranteedUnshieldedOffer = null,
                fallibleUnshieldedOffer = null,
                ttl = System.currentTimeMillis() + 60000
            )
        }
    }

    @Test
    fun `given negative ttl when creating Intent then throws`() {
        // Given
        val offer = createTestOffer()

        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            Intent(
                guaranteedUnshieldedOffer = offer,
                fallibleUnshieldedOffer = null,
                ttl = -1
            )
        }
    }

    @Test
    fun `given zero ttl when creating Intent then throws`() {
        // Given
        val offer = createTestOffer()

        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            Intent(
                guaranteedUnshieldedOffer = offer,
                fallibleUnshieldedOffer = null,
                ttl = 0
            )
        }
    }

    @Test
    fun `given future ttl when checking if expired then returns false`() {
        // Given
        val offer = createTestOffer()
        val ttl = System.currentTimeMillis() + 60000
        val intent = Intent(
            guaranteedUnshieldedOffer = offer,
            fallibleUnshieldedOffer = null,
            ttl = ttl
        )

        // When/Then
        assertFalse(intent.isExpired())
    }

    @Test
    fun `given past ttl when checking if expired then returns true`() {
        // Given
        val offer = createTestOffer()
        val ttl = System.currentTimeMillis() - 60000
        val intent = Intent(
            guaranteedUnshieldedOffer = offer,
            fallibleUnshieldedOffer = null,
            ttl = ttl
        )

        // When/Then
        assertTrue(intent.isExpired())
    }

    @Test
    fun `given intent when calculating remaining time then returns correct value`() {
        // Given
        val offer = createTestOffer()
        val currentTime = System.currentTimeMillis()
        val ttl = currentTime + 60000
        val intent = Intent(
            guaranteedUnshieldedOffer = offer,
            fallibleUnshieldedOffer = null,
            ttl = ttl
        )

        // When
        val remaining = intent.remainingTime(currentTime)

        // Then
        assertEquals(60000L, remaining)
    }

    @Test
    fun `given expired intent when calculating remaining time then returns negative`() {
        // Given
        val offer = createTestOffer()
        val currentTime = System.currentTimeMillis()
        val ttl = currentTime - 60000
        val intent = Intent(
            guaranteedUnshieldedOffer = offer,
            fallibleUnshieldedOffer = null,
            ttl = ttl
        )

        // When
        val remaining = intent.remainingTime(currentTime)

        // Then
        assertEquals(-60000L, remaining)
    }

    @Test
    fun `given balanced offers when checking balance then returns true`() {
        // Given
        val offer = createTestOffer()
        val intent = Intent(
            guaranteedUnshieldedOffer = offer,
            fallibleUnshieldedOffer = null,
            ttl = System.currentTimeMillis() + 60000
        )

        // When/Then
        assertTrue(intent.isBalanced())
    }

    @Test
    fun `given unbalanced guaranteed offer when checking balance then returns false`() {
        // Given
        val unbalancedOffer = UnshieldedOffer(
            inputs = listOf(
                UtxoSpend(
                    intentHash = "0x123",
                    outputNo = 0,
                    value = BigInteger("100"),
                    owner = "mn_addr_testnet1abc",
                    ownerPublicKey = UtxoSpend.TEST_PUBLIC_KEY,
                    tokenType = UtxoSpend.NATIVE_TOKEN_TYPE
                )
            ),
            outputs = listOf(
                UtxoOutput(
                    value = BigInteger("200"),  // More than input!
                    owner = "mn_addr_testnet1xyz",
                    tokenType = UtxoOutput.NATIVE_TOKEN_TYPE
                )
            )
        )
        val intent = Intent(
            guaranteedUnshieldedOffer = unbalancedOffer,
            fallibleUnshieldedOffer = null,
            ttl = System.currentTimeMillis() + 60000
        )

        // When/Then
        assertFalse(intent.isBalanced())
    }

    @Test
    fun `given unsigned offers when checking if signed then returns false`() {
        // Given
        val offer = createTestOffer()
        val intent = Intent(
            guaranteedUnshieldedOffer = offer,
            fallibleUnshieldedOffer = null,
            ttl = System.currentTimeMillis() + 60000
        )

        // When/Then
        assertFalse(intent.isSigned())
    }

    @Test
    fun `given fully signed offers when checking if signed then returns true`() {
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
        val signedOffer = UnshieldedOffer(
            inputs = listOf(input),
            outputs = listOf(output),
            signatures = listOf(signature)
        )

        val intent = Intent(
            guaranteedUnshieldedOffer = signedOffer,
            fallibleUnshieldedOffer = null,
            ttl = System.currentTimeMillis() + 60000
        )

        // When/Then
        assertTrue(intent.isSigned())
    }

    @Test
    fun `given intent with both offers when counting inputs then sums both`() {
        // Given
        val offer1 = createTestOffer()  // 1 input
        val offer2 = createTestOffer()  // 1 input
        val intent = Intent(
            guaranteedUnshieldedOffer = offer1,
            fallibleUnshieldedOffer = offer2,
            ttl = System.currentTimeMillis() + 60000
        )

        // When
        val totalInputs = intent.totalInputCount()

        // Then
        assertEquals(2, totalInputs)
    }

    @Test
    fun `given intent with both offers when counting outputs then sums both`() {
        // Given
        val offer1 = createTestOffer()  // 1 output
        val offer2 = createTestOffer()  // 1 output
        val intent = Intent(
            guaranteedUnshieldedOffer = offer1,
            fallibleUnshieldedOffer = offer2,
            ttl = System.currentTimeMillis() + 60000
        )

        // When
        val totalOutputs = intent.totalOutputCount()

        // Then
        assertEquals(2, totalOutputs)
    }

    @Test
    fun `given offer when creating with default TTL then sets 5 minutes from now`() {
        // Given
        val offer = createTestOffer()
        val beforeCreate = System.currentTimeMillis()

        // When
        val intent = Intent.withDefaultTtl(guaranteedOffer = offer)
        val afterCreate = System.currentTimeMillis()

        // Then
        assertTrue(intent.ttl >= beforeCreate + Intent.DEFAULT_TTL_MILLIS)
        assertTrue(intent.ttl <= afterCreate + Intent.DEFAULT_TTL_MILLIS)
        assertEquals(5 * 60 * 1000L, Intent.DEFAULT_TTL_MILLIS)
    }

    @Test
    fun `given two intents with same values when comparing then equal`() {
        // Given
        val offer = createTestOffer()
        val ttl = System.currentTimeMillis() + 60000
        val intent1 = Intent(
            guaranteedUnshieldedOffer = offer,
            fallibleUnshieldedOffer = null,
            ttl = ttl
        )
        val intent2 = Intent(
            guaranteedUnshieldedOffer = offer,
            fallibleUnshieldedOffer = null,
            ttl = ttl
        )

        // When/Then
        assertEquals(intent1, intent2)
        assertEquals(intent1.hashCode(), intent2.hashCode())
    }
}
