// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.crypto.shielded

import java.util.Arrays

/**
 * Utilities for secure memory wiping of cryptographic material.
 *
 * **Why Memory Wiping Matters:**
 * When working with sensitive data like seeds and private keys, it's critical to
 * minimize the time they remain in memory. JVM garbage collection is unpredictable,
 * so we explicitly zero out byte arrays after use.
 *
 * **Limitations:**
 * - This is "best effort" - JVM may create copies during GC
 * - String wiping is impossible (Strings are immutable)
 * - For maximum security, use hardware wallets
 * - Memory dumps can still expose keys before wiping
 *
 * **Best Practices:**
 * 1. Use ByteArray instead of String for sensitive data
 * 2. Wipe ByteArray as soon as you're done with it
 * 3. Use try-finally blocks to ensure wiping happens even on exceptions
 * 4. Never log sensitive data
 * 5. Never store seeds/keys long-term in memory
 *
 * **Example:**
 * ```kotlin
 * val seed = deriveSeed() // 32 bytes
 * try {
 *     val keys = deriveKeys(seed)
 *     // Use keys...
 * } finally {
 *     MemoryUtils.wipe(seed)  // CRITICAL: Always wipe
 * }
 * ```
 */
object MemoryUtils {
    /**
     * Wipes a byte array from memory by filling it with zeros.
     *
     * **IMPORTANT:**
     * - This is a destructive operation - the array is modified in-place
     * - After calling this, the array should not be used again
     * - This method is idempotent - safe to call multiple times
     * - Always use this in finally blocks to ensure cleanup
     *
     * **Implementation:**
     * Uses `Arrays.fill()` which is optimized by the JVM and less likely
     * to be optimized away by the compiler compared to manual loops.
     *
     * @param data Byte array to wipe (typically seeds, private keys, etc.)
     */
    fun wipe(data: ByteArray) {
        Arrays.fill(data, 0.toByte())
    }

    /**
     * Wipes multiple byte arrays from memory.
     *
     * **Use case:** When you have multiple sensitive arrays to wipe:
     * ```kotlin
     * try {
     *     val seed = ...
     *     val privateKey = ...
     *     // Use them...
     * } finally {
     *     MemoryUtils.wipeAll(seed, privateKey)
     * }
     * ```
     *
     * **Guarantee:**
     * - ALL arrays are wiped, even if one is null
     * - Continues wiping even if an exception occurs (should never happen)
     *
     * @param arrays Variable number of byte arrays to wipe
     */
    fun wipeAll(vararg arrays: ByteArray?) {
        arrays.forEach { array ->
            if (array != null) {
                try {
                    wipe(array)
                } catch (e: Exception) {
                    // Should never happen, but continue wiping others if it does
                    // In production, consider logging this
                }
            }
        }
    }

    /**
     * Executes a block with a byte array and automatically wipes it afterward.
     *
     * **Use case:** Ensures memory is wiped even if exceptions occur:
     * ```kotlin
     * val keys = MemoryUtils.useAndWipe(seed) { seedBytes ->
     *     deriveKeys(seedBytes)
     * }
     * // seed is wiped here, even if deriveKeys() throws
     * ```
     *
     * **Guarantee:**
     * - The data is ALWAYS wiped after use, even on exceptions
     * - Exceptions are re-thrown after wiping
     *
     * @param data Byte array to use and wipe
     * @param block Lambda that uses the byte array
     * @return Result of the block
     * @throws Any exception thrown by the block (after wiping data)
     */
    inline fun <T> useAndWipe(data: ByteArray, block: (ByteArray) -> T): T {
        return try {
            block(data)
        } finally {
            wipe(data)
        }
    }

    /**
     * Executes a block with multiple byte arrays and automatically wipes them afterward.
     *
     * **Use case:** When working with multiple sensitive arrays:
     * ```kotlin
     * val result = MemoryUtils.useAndWipeAll(seed, chainCode) { s, cc ->
     *     deriveChild(s, cc)
     * }
     * // Both seed and chainCode are wiped here
     * ```
     *
     * @param data1 First byte array to use and wipe
     * @param data2 Second byte array to use and wipe
     * @param block Lambda that uses both byte arrays
     * @return Result of the block
     * @throws Any exception thrown by the block (after wiping data)
     */
    inline fun <T> useAndWipeAll(
        data1: ByteArray,
        data2: ByteArray,
        block: (ByteArray, ByteArray) -> T
    ): T {
        return try {
            block(data1, data2)
        } finally {
            wipeAll(data1, data2)
        }
    }
}
