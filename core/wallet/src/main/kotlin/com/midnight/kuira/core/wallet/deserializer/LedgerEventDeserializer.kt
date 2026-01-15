package com.midnight.kuira.core.wallet.deserializer

import com.midnight.kuira.core.wallet.model.LedgerEvent

/**
 * Deserializes raw hex ledger events into domain objects.
 *
 * **Phase 4A:** Mock implementation with test data
 * **Phase 4B:** Real implementation using ledger 7.0.0 via JNI/FFI
 *
 * **Implementation Strategy (Phase 4B):**
 * ```
 * Kotlin → JNI → Rust (ledger 7.0.0 source) → ARM64/x86_64 native libs
 * ```
 *
 * Same approach as Phase 1B (shielded key derivation).
 */
interface LedgerEventDeserializer {

    /**
     * Deserialize raw hex event data into structured LedgerEvent.
     *
     * **Phase 4A:** Returns mock/test data
     * **Phase 4B:** Actual deserialization with ledger 7.0.0
     *
     * @param rawHex Raw event data as hex string
     * @return Deserialized ledger event
     * @throws DeserializationException if rawHex is invalid or unsupported format
     */
    suspend fun deserialize(rawHex: String): LedgerEvent
}

/**
 * Exception thrown when event deserialization fails.
 */
class DeserializationException(message: String, cause: Throwable? = null) : Exception(message, cause)
