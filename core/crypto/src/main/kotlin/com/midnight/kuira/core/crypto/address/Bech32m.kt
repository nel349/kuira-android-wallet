// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.crypto.address

/**
 * Bech32m encoding/decoding implementation based on BIP-350.
 *
 * **Differences from Bech32 (BIP-173):**
 * - Uses different checksum constant (0x2bc830a3 vs 0x1)
 * - Fixes mutation weakness in original Bech32
 * - Used by Midnight blockchain for address encoding
 *
 * **Format:**
 * `[human-readable part]1[data][checksum]`
 *
 * Example Midnight address:
 * ```
 * mn_addr_preview1qe8qj25qkva7ug6qf3rvl3y0a366ydt2nvq30rwk5ckznavfdansq8yfx3u
 * └─┬─┘ └──┬──┘ └──────────────────────┬────────────────────────┘
 *   │      │                           │
 *   │      │                           └── Data + Checksum (Bech32m encoded)
 *   │      └────────────────────────────── Network ID
 *   └───────────────────────────────────── Prefix
 * ```
 *
 * **Reference:**
 * - BIP-350: https://github.com/bitcoin/bips/blob/master/bip-0350.mediawiki
 * - @scure/base implementation: https://github.com/paulmillr/scure-base
 * - Midnight SDK: @midnight-ntwrk/wallet-sdk-address-format
 *
 * **Compatibility:** 100% compatible with Midnight SDK's Bech32m implementation
 */
object Bech32m {

    /** Character set for Bech32m encoding (33 characters: a-z, 0-9, excluding 1, b, i, o) */
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    /** Bech32m checksum constant (different from Bech32's 0x1) */
    private const val BECH32M_CONST = 0x2bc830a3

    /** Generator polynomials for checksum calculation */
    private val GEN = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    /**
     * Encodes data to Bech32m format.
     *
     * @param hrp Human-readable part (e.g., "mn_addr_preview")
     * @param data Data bytes to encode
     * @return Bech32m encoded string
     * @throws IllegalArgumentException if hrp or data is invalid
     */
    fun encode(hrp: String, data: ByteArray): String {
        require(hrp.isNotEmpty()) { "HRP must not be empty" }
        require(hrp.all { it.code in 33..126 }) { "HRP contains invalid characters" }
        require(hrp.lowercase() == hrp) { "HRP must be lowercase" }

        // Convert 8-bit data to 5-bit groups
        val fiveBitData = convertBits(data, 8, 5, true)

        // Create checksum
        val checksum = createChecksum(hrp, fiveBitData)

        // Combine data and checksum
        val combined = fiveBitData + checksum

        // Encode to Bech32m string
        return hrp + "1" + combined.map { CHARSET[it] }.joinToString("")
    }

    /**
     * Decodes a Bech32m string.
     *
     * @param bech32String Bech32m encoded string
     * @return Pair of (hrp, data bytes)
     * @throws IllegalArgumentException if string is invalid
     */
    fun decode(bech32String: String): Pair<String, ByteArray> {
        require(bech32String.isNotEmpty()) { "Bech32m string must not be empty" }
        require(bech32String.lowercase() == bech32String || bech32String.uppercase() == bech32String) {
            "Bech32m string must be all lowercase or all uppercase"
        }

        val lower = bech32String.lowercase()

        // Find separator
        val sepIndex = lower.lastIndexOf('1')
        require(sepIndex >= 1) { "Invalid Bech32m: missing separator" }
        require(sepIndex + 7 <= lower.length) { "Invalid Bech32m: checksum too short" }

        val hrp = lower.substring(0, sepIndex)
        val data = lower.substring(sepIndex + 1)

        // Decode data part
        val decoded = data.map { char ->
            val index = CHARSET.indexOf(char)
            require(index >= 0) { "Invalid character in Bech32m data: $char" }
            index
        }.toIntArray()

        // Verify checksum
        require(verifyChecksum(hrp, decoded)) { "Invalid Bech32m checksum" }

        // Remove checksum (last 6 characters)
        val dataWithoutChecksum = decoded.dropLast(6).toIntArray()

        // Convert 5-bit groups back to 8-bit bytes
        val bytes = convertBits(dataWithoutChecksum, 5, 8, false)

        return Pair(hrp, bytes)
    }

    /**
     * Converts between bit groups.
     *
     * @param data Input data
     * @param fromBits Bits per input group
     * @param toBits Bits per output group
     * @param pad Whether to pad output
     * @return Converted data
     */
    private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): IntArray {
        val result = mutableListOf<Int>()
        var acc = 0
        var bits = 0

        val maxv = (1 shl toBits) - 1
        val max_acc = (1 shl (fromBits + toBits - 1)) - 1

        for (byte in data) {
            val value = byte.toInt() and 0xFF
            acc = ((acc shl fromBits) or value) and max_acc
            bits += fromBits

            while (bits >= toBits) {
                bits -= toBits
                result.add((acc shr bits) and maxv)
            }
        }

        if (pad) {
            if (bits > 0) {
                result.add((acc shl (toBits - bits)) and maxv)
            }
        } else {
            require(bits < fromBits) { "Invalid padding in data" }
            require(((acc shl (toBits - bits)) and maxv) == 0) { "Non-zero padding bits" }
        }

        return result.toIntArray()
    }

    /**
     * Converts between bit groups (Int array version).
     */
    private fun convertBits(data: IntArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
        val result = mutableListOf<Byte>()
        var acc = 0
        var bits = 0

        val maxv = (1 shl toBits) - 1
        val max_acc = (1 shl (fromBits + toBits - 1)) - 1

        for (value in data) {
            acc = ((acc shl fromBits) or value) and max_acc
            bits += fromBits

            while (bits >= toBits) {
                bits -= toBits
                result.add(((acc shr bits) and maxv).toByte())
            }
        }

        if (pad) {
            if (bits > 0) {
                result.add(((acc shl (toBits - bits)) and maxv).toByte())
            }
        } else {
            require(bits < fromBits) { "Invalid padding in data" }
            require(((acc shl (toBits - bits)) and maxv) == 0) { "Non-zero padding bits" }
        }

        return result.toByteArray()
    }

    /**
     * Creates Bech32m checksum.
     */
    private fun createChecksum(hrp: String, data: IntArray): IntArray {
        val values = hrpExpand(hrp) + data + IntArray(6) { 0 }
        val polymod = polymod(values) xor BECH32M_CONST

        return IntArray(6) { i ->
            (polymod shr (5 * (5 - i))) and 31
        }
    }

    /**
     * Verifies Bech32m checksum.
     */
    private fun verifyChecksum(hrp: String, data: IntArray): Boolean {
        val values = hrpExpand(hrp) + data
        return polymod(values) == BECH32M_CONST
    }

    /**
     * Expands HRP for checksum calculation.
     */
    private fun hrpExpand(hrp: String): IntArray {
        val result = IntArray(hrp.length * 2 + 1)

        // High bits
        for (i in hrp.indices) {
            result[i] = hrp[i].code shr 5
        }

        // Separator
        result[hrp.length] = 0

        // Low bits
        for (i in hrp.indices) {
            result[hrp.length + 1 + i] = hrp[i].code and 31
        }

        return result
    }

    /**
     * Computes Bech32 polymod checksum.
     */
    private fun polymod(values: IntArray): Int {
        var chk = 1

        for (value in values) {
            val top = chk shr 25
            chk = ((chk and 0x1ffffff) shl 5) xor value

            for (i in 0..4) {
                if ((top shr i) and 1 == 1) {
                    chk = chk xor GEN[i]
                }
            }
        }

        return chk
    }
}
