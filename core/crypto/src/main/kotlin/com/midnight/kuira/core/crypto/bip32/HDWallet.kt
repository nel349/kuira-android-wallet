// This file is part of Kuira Wallet.
// Copyright (C) 2025 Kuira Wallet
// SPDX-License-Identifier: Apache-2.0

package com.midnight.kuira.core.crypto.bip32

import org.bitcoinj.crypto.DeterministicHierarchy
import org.bitcoinj.crypto.DeterministicKey
import org.bitcoinj.crypto.HDKeyDerivation
import java.util.Arrays
import javax.annotation.concurrent.NotThreadSafe

/**
 * Hierarchical Deterministic (HD) Wallet for Midnight blockchain.
 *
 * Implements BIP-32 key derivation following Midnight's specific derivation path:
 * ```
 * m / 44' / 2400' / account' / role / index
 * ```
 *
 * **Features:**
 * - BIP-32 compliant key derivation using BitcoinJ
 * - Midnight-specific derivation paths (coin type 2400)
 * - Support for multiple accounts and roles
 * - Secure key management with hierarchical memory wiping
 *
 * **Usage:**
 * ```kotlin
 * // Create HD wallet from BIP-39 seed
 * val seed = BIP39.mnemonicToSeed(mnemonic)
 * try {
 *     val wallet = HDWallet.fromSeed(seed)
 *     try {
 *         // Derive key at path: m/44'/2400'/0'/0/0
 *         val key = wallet
 *             .selectAccount(0)
 *             .selectRole(MidnightKeyRole.NIGHT_EXTERNAL)
 *             .deriveKeyAt(0)
 *
 *         // Use key...
 *     } finally {
 *         // Always clear wallet when done
 *         wallet.clear()
 *     }
 * } finally {
 *     // Always wipe seed from memory
 *     Arrays.fill(seed, 0.toByte())
 * }
 * ```
 *
 * **Security:**
 * - ALWAYS call [clear] when done to wipe keys from memory
 * - ALWAYS wipe the seed array after calling [fromSeed]
 * - Use try-finally blocks to ensure cleanup even on exceptions
 * - This class tracks ALL derived keys and clears them hierarchically
 *
 * **Thread Safety:**
 * - This class is NOT thread-safe
 * - Do not share instances across threads
 * - Create separate instances for concurrent use
 *
 * **References:**
 * - BIP-32: https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki
 * - BIP-44: https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki
 * - Midnight SDK: https://github.com/midnightntwrk/midnight-wallet
 */
@NotThreadSafe
class HDWallet private constructor(
    private val hierarchy: DeterministicHierarchy,
    private val rootKey: DeterministicKey
) {
    /**
     * List of all account wallets created from this HD wallet.
     * Tracked for hierarchical cleanup.
     */
    private val accounts = mutableListOf<HDWalletAccount>()

    /**
     * Flag to track if this wallet has been cleared.
     * Prevents use-after-clear bugs.
     */
    private var isCleared = false

    /**
     * Selects a specific account in the HD wallet hierarchy.
     *
     * Derives to path: `m/44'/2400'/account'`
     *
     * **BIP-44 Account Semantics:**
     * - Account 0: Default account for most users
     * - Account 1+: Additional accounts for organization/privacy
     *
     * @param account Account index (typically 0)
     * @return [HDWalletAccount] for further derivation
     * @throws IllegalStateException if wallet has been cleared
     * @throws IllegalArgumentException if account is negative
     */
    fun selectAccount(account: Int): HDWalletAccount {
        check(!isCleared) { "Cannot use HDWallet after clear() has been called" }
        require(account >= 0) { "Account must be non-negative, got: $account" }

        // Derive: m/44'/2400'/account'
        val path = listOf(
            MIDNIGHT_PURPOSE,
            MIDNIGHT_COIN_TYPE,
            childNumber(account, hardened = true)
        )

        val accountKey = hierarchy.get(path, true, true)
        val accountWallet = HDWalletAccount(hierarchy, accountKey, account, this)

        // Track for cleanup
        accounts.add(accountWallet)

        return accountWallet
    }

    /**
     * Clears all keys from memory using hierarchical cleanup.
     *
     * **CRITICAL SECURITY:** This method performs the following:
     * 1. Clears all derived account wallets (and their role wallets recursively)
     * 2. Attempts to wipe the root key from memory
     * 3. Marks this wallet as cleared to prevent use-after-clear
     *
     * **IMPORTANT:**
     * - Always call this method when done using the wallet
     * - After calling [clear], this wallet instance cannot be used again
     * - This method is idempotent - safe to call multiple times
     * - Cleanup is best-effort - some keys may remain in BitcoinJ internals
     *
     * **Limitations:**
     * BitcoinJ's `DeterministicKey` uses immutable `BigInteger` for private keys,
     * which cannot be reliably wiped from memory. This method does its best to
     * minimize key exposure, but hardware wallets are recommended for maximum security.
     */
    fun clear() {
        if (isCleared) return // Idempotent

        try {
            // Clear all account wallets (hierarchical cleanup)
            accounts.forEach { account ->
                try {
                    account.clear()
                } catch (e: Exception) {
                    // Continue cleanup even if one fails
                    // In production, consider logging this
                }
            }
            accounts.clear()

            // Note: BitcoinJ stores private keys as BigInteger which is immutable
            // and does not provide a clearPrivateKey() method. Complete wiping
            // of the root key is not possible with BitcoinJ's current API.
            // For maximum security, consider using hardware wallets.
        } finally {
            isCleared = true
        }
    }

    /**
     * Returns true if this wallet has been cleared and can no longer be used.
     */
    fun isCleared(): Boolean = isCleared

    companion object {
        /**
         * BIP-44 purpose: 44' (hardened)
         */
        private val MIDNIGHT_PURPOSE = childNumber(44, hardened = true)

        /**
         * Midnight coin type: 2400' (hardened)
         */
        private val MIDNIGHT_COIN_TYPE = childNumber(2400, hardened = true)

        /**
         * Creates an HD wallet from a BIP-39 seed.
         *
         * **CRITICAL SECURITY WARNING:**
         * This function does NOT clear the provided seed array. The caller MUST
         * wipe the seed from memory after calling this function:
         *
         * ```kotlin
         * val seed = BIP39.mnemonicToSeed(mnemonic)
         * try {
         *     val wallet = HDWallet.fromSeed(seed)
         *     // Use wallet...
         *     wallet.clear()
         * } finally {
         *     // CRITICAL: Wipe seed from memory
         *     Arrays.fill(seed, 0.toByte())
         * }
         * ```
         *
         * The seed is the master secret that can derive ALL keys in this wallet.
         * Failing to wipe it is a critical security vulnerability.
         *
         * **Compatibility:**
         * - BIP-39 mnemonicToSeed() produces 64-byte seeds (most common)
         * - Midnight SDK supports seeds from 16 to 64 bytes
         * - This implementation accepts 16-64 bytes for full compatibility
         *
         * @param seed Seed bytes (16-64 bytes). BIP-39 mnemonicToSeed() produces 64 bytes.
         * @param clearSeed If true, automatically clears the seed after use (default: false for backward compatibility)
         * @return [HDWallet] instance
         * @throws IllegalArgumentException if seed is not between 16 and 64 bytes
         */
        @JvmOverloads
        fun fromSeed(seed: ByteArray, clearSeed: Boolean = false): HDWallet {
            require(seed.size in 16..64) {
                "Seed must be between 16 and 64 bytes (compatible with Midnight SDK), got ${seed.size} bytes"
            }

            return try {
                // Create master key from seed
                val masterKey = HDKeyDerivation.createMasterPrivateKey(seed)

                // Create deterministic hierarchy
                val hierarchy = DeterministicHierarchy(masterKey)

                HDWallet(hierarchy, masterKey)
            } finally {
                if (clearSeed) {
                    Arrays.fill(seed, 0.toByte())
                }
            }
        }

        /**
         * Creates a child number for BIP-32 derivation.
         *
         * @param index Child index
         * @param hardened Whether to use hardened derivation (')
         * @return [org.bitcoinj.crypto.ChildNumber]
         */
        private fun childNumber(index: Int, hardened: Boolean): org.bitcoinj.crypto.ChildNumber {
            return if (hardened) {
                org.bitcoinj.crypto.ChildNumber(index, true)
            } else {
                org.bitcoinj.crypto.ChildNumber(index, false)
            }
        }
    }
}

/**
 * Represents a specific account in the HD wallet hierarchy.
 *
 * Path: `m/44'/2400'/account'`
 *
 * **Thread Safety:** Not thread-safe. Do not share across threads.
 */
@NotThreadSafe
class HDWalletAccount internal constructor(
    private val hierarchy: DeterministicHierarchy,
    private val accountKey: DeterministicKey,
    val accountIndex: Int,
    private val parentWallet: HDWallet
) {
    /**
     * List of all role wallets created from this account.
     * Tracked for hierarchical cleanup.
     */
    private val roles = mutableListOf<HDWalletRole>()

    /**
     * Flag to track if this account has been cleared.
     */
    private var isCleared = false

    /**
     * Selects a specific role within this account.
     *
     * Derives to path: `m/44'/2400'/account'/role`
     *
     * @param role The [MidnightKeyRole] to select
     * @return [HDWalletRole] for further derivation
     * @throws IllegalStateException if account has been cleared
     */
    fun selectRole(role: MidnightKeyRole): HDWalletRole {
        check(!isCleared) { "Cannot use HDWalletAccount after clear() has been called" }
        check(!parentWallet.isCleared()) { "Cannot use HDWalletAccount after parent wallet has been cleared" }

        // Derive: m/44'/2400'/account'/role (NOT hardened)
        val roleKey = HDKeyDerivation.deriveChildKey(
            accountKey,
            org.bitcoinj.crypto.ChildNumber(role.index, false)
        ) ?: throw IllegalStateException("Failed to derive key for role ${role.name}")

        val roleWallet = HDWalletRole(hierarchy, roleKey, accountIndex, role, this)

        // Track for cleanup
        roles.add(roleWallet)

        return roleWallet
    }

    /**
     * Clears all role wallets created from this account.
     *
     * This is called automatically by the parent HDWallet.clear().
     * You typically don't need to call this directly.
     */
    internal fun clear() {
        if (isCleared) return // Idempotent

        try {
            // Clear all role wallets
            roles.forEach { role ->
                try {
                    role.clear()
                } catch (e: Exception) {
                    // Continue cleanup
                }
            }
            roles.clear()

            // Note: BitcoinJ's DeterministicKey does not provide clearPrivateKey()
            // Account key wiping is not possible with current BitcoinJ API
        } finally {
            isCleared = true
        }
    }
}

/**
 * Represents a specific role within an account in the HD wallet hierarchy.
 *
 * Path: `m/44'/2400'/account'/role`
 *
 * **Thread Safety:** Not thread-safe. Do not share across threads.
 */
@NotThreadSafe
class HDWalletRole internal constructor(
    private val hierarchy: DeterministicHierarchy,
    private val roleKey: DeterministicKey,
    val accountIndex: Int,
    val role: MidnightKeyRole,
    private val parentAccount: HDWalletAccount
) {
    /**
     * List of all derived keys created from this role.
     * Tracked for hierarchical cleanup.
     */
    private val derivedKeys = mutableListOf<DerivedKey>()

    /**
     * Flag to track if this role has been cleared.
     */
    private var isCleared = false

    /**
     * Derives a key at a specific index within this role.
     *
     * Final path: `m/44'/2400'/account'/role/index`
     *
     * @param index Key index (typically 0 for first address)
     * @return [DerivedKey] containing the private key
     * @throws IllegalStateException if role has been cleared
     * @throws IllegalArgumentException if index is negative
     */
    fun deriveKeyAt(index: Int): DerivedKey {
        check(!isCleared) { "Cannot use HDWalletRole after clear() has been called" }
        require(index >= 0) { "Index must be non-negative, got: $index" }

        // Derive: m/44'/2400'/account'/role/index (NOT hardened)
        val key = HDKeyDerivation.deriveChildKey(
            roleKey,
            org.bitcoinj.crypto.ChildNumber(index, false)
        ) ?: throw IllegalStateException("Failed to derive key at index $index")

        // Ensure key has private key material (not a watch-only key)
        val privateKeyBytes = key.privKeyBytes
            ?: throw IllegalStateException(
                "Derived key at path m/44'/2400'/$accountIndex'/${role.index}/$index " +
                        "does not have private key material. This should never happen for keys " +
                        "derived from a seed."
            )

        require(privateKeyBytes.size == 32) {
            "Private key must be 32 bytes, got ${privateKeyBytes.size} bytes"
        }

        val derivedKey = DerivedKey(
            privateKeyBytes = privateKeyBytes,
            publicKeyBytes = key.pubKey,
            chainCode = key.chainCode,
            path = "m/44'/2400'/$accountIndex'/${role.index}/$index",
            index = index
        )

        // Track for cleanup
        derivedKeys.add(derivedKey)

        return derivedKey
    }

    /**
     * Clears all derived keys created from this role.
     *
     * This is called automatically by the parent HDWalletAccount.clear().
     * You typically don't need to call this directly.
     */
    internal fun clear() {
        if (isCleared) return // Idempotent

        try {
            // Clear all derived keys
            derivedKeys.forEach { key ->
                try {
                    key.clear()
                } catch (e: Exception) {
                    // Continue cleanup
                }
            }
            derivedKeys.clear()

            // Note: BitcoinJ's DeterministicKey does not provide clearPrivateKey()
            // Role key wiping is not possible with current BitcoinJ API
        } finally {
            isCleared = true
        }
    }
}

/**
 * Result of key derivation containing the derived cryptographic material.
 *
 * **IMPORTANT:** This is a regular class (not data class) because:
 * 1. It contains mutable state that gets wiped via [clear]
 * 2. It should not be copied (copies would share mutable arrays)
 * 3. Hash code changes after clearing (breaks collections)
 *
 * **Security:**
 * - Call [clear] when done to wipe all keys from memory
 * - Private, public, and chain code are ALL wiped
 * - After clearing, this instance should not be used again
 *
 * @property privateKeyBytes 32-byte private key
 * @property publicKeyBytes 33-byte compressed public key (with prefix)
 * @property chainCode 32-byte chain code for further derivation
 * @property path Human-readable derivation path
 * @property index The address index within the role
 */
class DerivedKey internal constructor(
    val privateKeyBytes: ByteArray,
    val publicKeyBytes: ByteArray,
    val chainCode: ByteArray,
    val path: String,
    val index: Int
) {
    /**
     * Flag to track if this key has been cleared.
     */
    private var isCleared = false

    /**
     * Clears ALL key material from memory by filling with zeros.
     *
     * **CRITICAL:** This wipes:
     * - Private key (most sensitive)
     * - Public key (for privacy)
     * - Chain code (can derive child public keys)
     *
     * After calling [clear], this instance should not be used again.
     * This method is idempotent - safe to call multiple times.
     */
    fun clear() {
        if (isCleared) return // Idempotent

        try {
            Arrays.fill(privateKeyBytes, 0.toByte())
            Arrays.fill(publicKeyBytes, 0.toByte())
            Arrays.fill(chainCode, 0.toByte())
        } finally {
            isCleared = true
        }
    }

    /**
     * Returns true if this key has been cleared and can no longer be used.
     */
    fun isCleared(): Boolean = isCleared

    /**
     * Gets the private key as a hex string.
     *
     * **DANGER:** This method exists for testing and debugging ONLY.
     * - NEVER call this in production code
     * - NEVER log the output
     * - NEVER transmit over network
     * - Exposing private keys can lead to complete loss of funds
     *
     * @throws IllegalStateException if key has been cleared
     */
    @Deprecated(
        message = "Only use for testing and debugging. Never call in production code.",
        level = DeprecationLevel.WARNING
    )
    fun privateKeyHexDebugOnly(): String {
        check(!isCleared) { "Cannot access cleared key" }
        return privateKeyBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Gets the public key as a hex string.
     *
     * @throws IllegalStateException if key has been cleared
     */
    fun publicKeyHex(): String {
        check(!isCleared) { "Cannot access cleared key" }
        return publicKeyBytes.joinToString("") { "%02x".format(it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DerivedKey

        if (!privateKeyBytes.contentEquals(other.privateKeyBytes)) return false
        if (!publicKeyBytes.contentEquals(other.publicKeyBytes)) return false
        if (!chainCode.contentEquals(other.chainCode)) return false
        if (path != other.path) return false
        if (index != other.index) return false

        return true
    }

    override fun hashCode(): Int {
        var result = privateKeyBytes.contentHashCode()
        result = 31 * result + publicKeyBytes.contentHashCode()
        result = 31 * result + chainCode.contentHashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + index
        return result
    }

    override fun toString(): String {
        return "DerivedKey(path=$path, index=$index, cleared=$isCleared)"
    }
}
