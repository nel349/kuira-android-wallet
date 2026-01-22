package com.midnight.kuira.core.ledger.signer

import java.security.SecureRandom

/**
 * Transaction signer using Schnorr BIP-340 signatures via JNI to Rust midnight-ledger.
 *
 * This class bridges Kotlin → JNI → Rust FFI → midnight-ledger for cryptographic signing.
 *
 * Architecture:
 * ```
 * Kotlin: TransactionSigner.signData(privateKey, data)
 *   ↓
 * JNI (C): kuira_crypto_jni.c - Extract bytes, call Rust
 *   ↓
 * Rust FFI: transaction_ffi.rs - create_signing_key, sign_data
 *   ↓
 * midnight-ledger: Schnorr BIP-340 signing over secp256k1
 * ```
 *
 * **Security Notes:**
 * - Private keys are copied to native memory and zeroized immediately after use
 * - Transaction data is zeroized after signing (prevents memory dump recovery)
 * - SigningKey pointers must be freed to prevent memory leaks
 * - Uses OS-level RNG for signature nonces (secure by default)
 *
 * **Thread Safety:**
 * - Each signing operation creates an independent SigningKey
 * - SigningKey pointers are NOT shared across threads
 * - Safe to call signData() from multiple threads concurrently with different keys
 * - DO NOT manually share SigningKey pointers between threads (use-after-free risk)
 * - The useSigningKey() helper ensures automatic cleanup and prevents pointer misuse
 *
 * **Memory Management:**
 * - Native library loads once on first use
 * - Each signData() call creates a new SigningKey, uses it, and frees it automatically
 * - Manual management with nativeCreateSigningKey() requires calling nativeFreeSigningKey()
 * - Use `useSigningKey()` for automatic cleanup (recommended pattern)
 *
 * @see midnight_base_crypto::signatures::SigningKey (Rust implementation)
 * @see com.midnight.kuira.core.crypto.bip32.HDWallet (provides private keys)
 */
object TransactionSigner {

    private const val LIBRARY_NAME = "kuira_crypto_ffi"

    /**
     * Load native library on first use.
     * System.loadLibrary searches lib/<arch>/libkuira_crypto_ffi.so in APK.
     */
    init {
        try {
            System.loadLibrary(LIBRARY_NAME)
        } catch (e: UnsatisfiedLinkError) {
            throw IllegalStateException(
                "Failed to load native library '$LIBRARY_NAME'. " +
                        "Ensure the library is built and bundled in the APK.",
                e
            )
        }
    }

    // ============================================================================
    // Public API
    // ============================================================================

    /**
     * Signs data with Schnorr BIP-340 signature.
     *
     * This is the main entry point for transaction signing.
     *
     * **Process:**
     * 1. Create SigningKey from private key (32 bytes)
     * 2. Sign data using Schnorr BIP-340
     * 3. Free SigningKey memory
     * 4. Return 64-byte signature
     *
     * **Usage Example:**
     * ```kotlin
     * val privateKey: ByteArray = hdWallet.selectAccount(0)
     *     .selectRole(MidnightKeyRole.NIGHT_EXTERNAL)
     *     .deriveKeysAt(0)
     *     .privateKey
     *
     * val transactionData = intent.serialize() // Midnight Intent bytes
     * val signature = TransactionSigner.signData(privateKey, transactionData)
     *
     * // signature is 64 bytes: [R (32 bytes) || s (32 bytes)]
     * ```
     *
     * **Signature Format:**
     * - 64 bytes total
     * - First 32 bytes: R (public nonce commitment)
     * - Last 32 bytes: s (signature scalar)
     * - Conforms to BIP-340 standard
     *
     * @param privateKey 32-byte secp256k1 private key (from BIP-32 derivation)
     * @param data Data to sign (transaction intent, arbitrary bytes)
     * @return 64-byte Schnorr signature, or null on error
     * @throws IllegalArgumentException if privateKey is not 32 bytes
     */
    fun signData(privateKey: ByteArray, data: ByteArray): ByteArray? {
        require(privateKey.size == 32) { "Private key must be 32 bytes, got ${privateKey.size}" }
        require(data.isNotEmpty()) { "Data to sign cannot be empty" }

        return useSigningKey(privateKey) { signingKeyPtr ->
            nativeSignData(signingKeyPtr, data)
        }
    }

    /**
     * Derives the verifying key (public key) from a private key.
     *
     * **BIP-340 Public Key Format:**
     * - 32 bytes (x-only public key)
     * - Y-coordinate is implicit (even)
     * - NOT the same as uncompressed secp256k1 public key (33 or 65 bytes)
     *
     * **Usage Example:**
     * ```kotlin
     * val privateKey: ByteArray = ... // 32 bytes from BIP-32
     * val publicKey = TransactionSigner.getPublicKey(privateKey)
     *
     * // publicKey is 32 bytes (BIP-340 x-only format)
     * val address = AddressFormatter.encodeAddress(publicKey)
     * ```
     *
     * @param privateKey 32-byte secp256k1 private key
     * @return 32-byte BIP-340 public key (x-only), or null on error
     * @throws IllegalArgumentException if privateKey is not 32 bytes
     */
    fun getPublicKey(privateKey: ByteArray): ByteArray? {
        require(privateKey.size == 32) { "Private key must be 32 bytes, got ${privateKey.size}" }

        return useSigningKey(privateKey) { signingKeyPtr ->
            nativeGetVerifyingKey(signingKeyPtr)
        }
    }

    // ============================================================================
    // Memory Management Helpers
    // ============================================================================

    /**
     * Creates a SigningKey, executes a block, and automatically frees the key.
     *
     * **This is the recommended pattern for all signing operations.**
     *
     * Ensures SigningKey memory is freed even if block throws an exception.
     *
     * **Example:**
     * ```kotlin
     * val signature = useSigningKey(privateKey) { signingKeyPtr ->
     *     nativeSignData(signingKeyPtr, data)
     * }
     * // SigningKey is automatically freed here
     * ```
     *
     * @param privateKey 32-byte private key
     * @param block Function to execute with SigningKey pointer
     * @return Result from block, or null if SigningKey creation fails
     */
    private inline fun <T> useSigningKey(privateKey: ByteArray, block: (Long) -> T?): T? {
        val signingKeyPtr = nativeCreateSigningKey(privateKey)
        if (signingKeyPtr == 0L) {
            return null
        }

        return try {
            block(signingKeyPtr)
        } finally {
            nativeFreeSigningKey(signingKeyPtr)
        }
    }

    // ============================================================================
    // Native Methods (JNI) - Private to prevent name mangling
    // ============================================================================

    /**
     * JNI: Creates a SigningKey from 32-byte private key.
     *
     * **IMPORTANT:** This is private external to prevent Kotlin name mangling.
     * Use the internal wrapper for testing.
     *
     * @param privateKey 32-byte secp256k1 private key
     * @return Pointer to SigningKey (as Long), or 0 on error
     */
    private external fun nativeCreateSigningKey(privateKey: ByteArray): Long

    /**
     * JNI: Frees a SigningKey.
     *
     * @param signingKeyPtr Pointer to SigningKey (from nativeCreateSigningKey)
     */
    private external fun nativeFreeSigningKey(signingKeyPtr: Long)

    /**
     * JNI: Signs data with Schnorr BIP-340.
     *
     * @param signingKeyPtr Pointer to SigningKey
     * @param data Data to sign (arbitrary bytes)
     * @return 64-byte signature, or null on error
     */
    private external fun nativeSignData(signingKeyPtr: Long, data: ByteArray): ByteArray?

    /**
     * JNI: Gets the verifying key (public key) from a SigningKey.
     *
     * @param signingKeyPtr Pointer to SigningKey
     * @return 32-byte public key, or null on error
     */
    private external fun nativeGetVerifyingKey(signingKeyPtr: Long): ByteArray?

    // ============================================================================
    // Internal Wrappers for Testing - Delegate to private external functions
    // ============================================================================

    /**
     * Creates a SigningKey from 32-byte private key.
     *
     * **Lifetime:** Caller MUST free with internalFreeSigningKey() when done.
     *
     * **Visibility:** Internal for testing memory safety. Production code should use signData()/getPublicKey().
     *
     * @param privateKey 32-byte secp256k1 private key
     * @return Pointer to SigningKey (as Long), or 0 on error
     */
    internal fun internalCreateSigningKey(privateKey: ByteArray): Long =
        nativeCreateSigningKey(privateKey)

    /**
     * Frees a SigningKey created by internalCreateSigningKey.
     *
     * **Visibility:** Internal for testing memory safety (double-free detection).
     *
     * @param signingKeyPtr Pointer to SigningKey (from internalCreateSigningKey)
     */
    internal fun internalFreeSigningKey(signingKeyPtr: Long) =
        nativeFreeSigningKey(signingKeyPtr)

    /**
     * Signs data with Schnorr BIP-340.
     *
     * **Signature Format:** 64 bytes (R || s)
     *
     * **Visibility:** Internal for testing memory safety (use-after-free detection).
     *
     * @param signingKeyPtr Pointer to SigningKey
     * @param data Data to sign (arbitrary bytes)
     * @return 64-byte signature, or null on error
     */
    internal fun internalSignData(signingKeyPtr: Long, data: ByteArray): ByteArray? =
        nativeSignData(signingKeyPtr, data)

    /**
     * Gets the verifying key (public key) from a SigningKey.
     *
     * **Public Key Format:** 32 bytes (BIP-340 x-only)
     *
     * **Visibility:** Internal for testing.
     *
     * @param signingKeyPtr Pointer to SigningKey
     * @return 32-byte public key, or null on error
     */
    internal fun internalGetVerifyingKey(signingKeyPtr: Long): ByteArray? =
        nativeGetVerifyingKey(signingKeyPtr)
}
