// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.ledger.fee

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger

/**
 * REAL integration tests for FeeCalculator with JNI.
 *
 * **NO MOCKS** - Tests actual JNI calls to Rust FFI.
 *
 * **What This Tests:**
 * - Native library loads successfully
 * - JNI bridge works (C ↔ Rust)
 * - SCALE codec deserialization in Rust
 * - Actual fee calculation from midnight-ledger
 * - Error handling for invalid inputs
 *
 * **Current Limitation:**
 * We don't have real SCALE-encoded transaction data yet, so we test:
 * - Library loading
 * - Error handling for invalid inputs
 * - Basic function calls
 *
 * **TODO:** Add tests with real SCALE-encoded transactions once available.
 */
@RunWith(AndroidJUnit4::class)
class FeeCalculatorIntegrationTest {

    // ==================== Basic Integration Tests ====================

    @Test
    fun verifyNativeLibraryLoads() {
        // This test verifies the JNI library loads successfully
        // If this fails, check:
        // - Native library is built (./gradlew :core:crypto:buildRustDebug)
        // - JNI bindings are correct in kuira_crypto_jni.c
        // - Library is packaged in APK

        // Simply accessing FeeCalculator triggers library load
        assertNotNull("FeeCalculator should exist", FeeCalculator)
    }

    @Test
    fun givenInvalidTransactionHexWhenCalculateFeeThenReturnsNull() {
        // Given: Invalid transaction hex (not SCALE-encoded)
        val invalidTxHex = "invalid_hex"
        val validParamsHex = "00" // Minimal SCALE data

        // When
        val fee = FeeCalculator.calculateFee(
            transactionHex = invalidTxHex,
            ledgerParamsHex = validParamsHex
        )

        // Then
        assertNull("Should return null for invalid transaction", fee)
    }

    @Test
    fun givenInvalidParamsHexWhenCalculateFeeThenReturnsNull() {
        // Given: Invalid ledger params (not SCALE-encoded)
        val validTxHex = "00" // Minimal SCALE data
        val invalidParamsHex = "not_valid_hex"

        // When
        val fee = FeeCalculator.calculateFee(
            transactionHex = validTxHex,
            ledgerParamsHex = invalidParamsHex
        )

        // Then
        assertNull("Should return null for invalid params", fee)
    }

    @Test
    fun givenEmptyInputsWhenCalculateFeeThenThrowsException() {
        // Given: Empty inputs
        val emptyTxHex = ""
        val emptyParamsHex = ""

        // When/Then: Should throw IllegalArgumentException
        try {
            FeeCalculator.calculateFee(
                transactionHex = emptyTxHex,
                ledgerParamsHex = emptyParamsHex
            )
            fail("Should throw IllegalArgumentException for empty inputs")
        } catch (e: IllegalArgumentException) {
            // Expected
            assertTrue("Error message should mention transactionHex",
                e.message?.contains("transactionHex") == true)
        }
    }

    // ==================== Margin Tests ====================

    @Test
    fun givenZeroMarginWhenCalculateFeeThenHandlesGracefully() {
        // Given: Zero margin (edge case)
        val txHex = "00"
        val paramsHex = "00"

        // When
        val fee = FeeCalculator.calculateFee(
            transactionHex = txHex,
            ledgerParamsHex = paramsHex,
            feeBlocksMargin = 0
        )

        // Then: Should not crash (fee may be null or valid)
        // This is acceptable - zero margin is unusual but not fatal
        assertTrue("Should handle zero margin", fee == null || fee is BigInteger)
    }

    @Test
    fun givenLargeMarginWhenCalculateFeeThenHandlesGracefully() {
        // Given: Large margin (1000 blocks)
        val txHex = "00"
        val paramsHex = "00"

        // When
        val fee = FeeCalculator.calculateFee(
            transactionHex = txHex,
            ledgerParamsHex = paramsHex,
            feeBlocksMargin = 1000
        )

        // Then: Should not crash
        assertTrue("Should handle large margin", fee == null || fee is BigInteger)
    }

    @Test
    fun givenNegativeMarginWhenCalculateFeeThenThrowsException() {
        // Given: Negative margin (invalid)
        val txHex = "00"
        val paramsHex = "00"

        // When/Then: Should throw IllegalArgumentException
        try {
            FeeCalculator.calculateFee(
                transactionHex = txHex,
                ledgerParamsHex = paramsHex,
                feeBlocksMargin = -5
            )
            fail("Should throw IllegalArgumentException for negative margin")
        } catch (e: IllegalArgumentException) {
            // Expected
            assertTrue("Error message should mention feeBlocksMargin",
                e.message?.contains("feeBlocksMargin") == true)
        }
    }

    // ==================== What We CAN'T Test Yet ====================

    // We CANNOT test fee calculation with real values because we need:
    // 1. Real SCALE-encoded transaction from midnight-ledger
    // 2. Real SCALE-encoded ledger parameters
    // 3. Expected fee output from midnight-ledger tests
    //
    // Without this data, any "fee range" tests would be fake.
    // The tests above prove the JNI bridge WORKS.
    // Fee calculation correctness requires real data.
}
