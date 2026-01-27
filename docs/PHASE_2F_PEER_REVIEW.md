# Phase 2F MVP - Peer Review Report
**Date:** January 26, 2026
**Reviewer:** Claude (following CLAUDE.md guidelines)
**Scope:** SendViewModel, SendScreen, AddressValidator, Navigation, DI Setup

---

## Executive Summary

**Overall Assessment:** ⚠️ **NEEDS CRITICAL FIXES BEFORE PRODUCTION**

The Phase 2F MVP implementation is **functionally correct** but has **3 critical security vulnerabilities** related to memory management that violate `SECURITY_GUIDELINES.md`. These must be fixed immediately.

**Severity Breakdown:**
- 🔴 **3 Critical** (Memory leaks exposing cryptographic secrets)
- 🟡 **4 High** (Input validation, code quality)
- 🟢 **5 Medium** (Best practices, improvements)

---

## 🔴 CRITICAL Issues (MUST FIX IMMEDIATELY)

### 1. **Seed Bytes Not Wiped (Memory Leak)**
**File:** `feature/send/src/main/kotlin/com/midnight/kuira/feature/send/SendViewModel.kt`
**Lines:** 181, 242
**Severity:** 🔴 **CRITICAL** - Private key material in memory

**Issue:**
```kotlin
// Line 181 - First instance
val seed = BIP39.mnemonicToSeed(seedPhrase)
// ... seed is used but NEVER wiped!

// Line 242 - Second instance (seed derived AGAIN!)
val seed = BIP39.mnemonicToSeed(seedPhrase)
// ... seed is used but NEVER wiped!
```

**Violated Guideline:**
`SECURITY_GUIDELINES.md:58-64` - "Always wipe seed bytes after key derivation"

**Risk:**
- 64-byte seed contains master secret for ALL keys
- Remains in heap until garbage collected (non-deterministic)
- Memory dumps can expose seed → complete wallet compromise

**Fix Required:**
```kotlin
// CORRECT Implementation
val seed = BIP39.mnemonicToSeed(seedPhrase)
try {
    val result = transactionSubmitter.submitWithFees(
        signedIntent = signedIntent,
        ledgerParamsHex = ledgerParamsHex,
        fromAddress = fromAddress,
        seed = seed
    )
    // ... handle result
} finally {
    java.util.Arrays.fill(seed, 0.toByte())  // CRITICAL: Wipe seed
}
```

---

### 2. **HDWallet Not Cleared (Memory Leak)**
**File:** `feature/send/src/main/kotlin/com/midnight/kuira/feature/send/SendViewModel.kt`
**Lines:** 243-249
**Severity:** 🔴 **CRITICAL** - Private key material in memory

**Issue:**
```kotlin
val hdWallet = HDWallet.fromSeed(seed)
val derivedKey = hdWallet
    .selectAccount(0)
    .selectRole(MidnightKeyRole.NIGHT_EXTERNAL)
    .deriveKeyAt(0)
// ... hdWallet.clear() is NEVER called!
```

**Violated Guideline:**
`SECURITY_GUIDELINES.md:132-138` - "ALWAYS wipe keys after use"

**Risk:**
- HDWallet contains BIP-32 master key and all derived child keys
- Remains in memory indefinitely
- Memory dumps expose entire key hierarchy

**Fix Required:**
```kotlin
// CORRECT Implementation
val seed = BIP39.mnemonicToSeed(seedPhrase)
val hdWallet = HDWallet.fromSeed(seed)
try {
    val derivedKey = hdWallet
        .selectAccount(0)
        .selectRole(MidnightKeyRole.NIGHT_EXTERNAL)
        .deriveKeyAt(0)

    val privateKey = derivedKey.privateKeyBytes

    // ... use privateKey for signing
} finally {
    hdWallet.clear()  // CRITICAL: Wipe all keys
    java.util.Arrays.fill(seed, 0.toByte())  // CRITICAL: Wipe seed
}
```

---

### 3. **DerivedKey Not Cleared (Memory Leak)**
**File:** `feature/send/src/main/kotlin/com/midnight/kuira/feature/send/SendViewModel.kt`
**Lines:** 244-274
**Severity:** 🔴 **CRITICAL** - Private key material in memory

**Issue:**
```kotlin
val derivedKey = hdWallet
    .selectAccount(0)
    .selectRole(MidnightKeyRole.NIGHT_EXTERNAL)
    .deriveKeyAt(0)

val privateKey = derivedKey.privateKeyBytes

// ... only privateKey.fill(0) is called (line 274)
// ... but derivedKey.clear() is NEVER called!
```

**Violated Guideline:**
`SECURITY_GUIDELINES.md:58-64` - "Always wipe keys after use"

**Risk:**
- DerivedKey contains private key, public key, and chain code
- Wiping only `privateKey` leaves public key + chain code in memory
- Chain code can derive child public keys (privacy leak)

**Fix Required:**
```kotlin
// CORRECT Implementation
try {
    val derivedKey = hdWallet
        .selectAccount(0)
        .selectRole(MidnightKeyRole.NIGHT_EXTERNAL)
        .deriveKeyAt(0)

    val privateKey = derivedKey.privateKeyBytes

    // ... use privateKey for signing

    privateKey.fill(0)  // Wipe private key
} finally {
    derivedKey.clear()  // CRITICAL: Wipe all key material
    hdWallet.clear()    // CRITICAL: Wipe HD wallet
}
```

---

## 🟡 HIGH Priority Issues (Should Fix Before Release)

### 4. **Missing Amount Validation**
**File:** `feature/send/src/main/kotlin/com/midnight/kuira/feature/send/SendViewModel.kt`
**Line:** 134-140
**Severity:** 🟡 **HIGH** - Can submit invalid transactions

**Issue:**
No validation that `amount > 0` before building transaction.

**Violated Guideline:**
`SECURITY_GUIDELINES.md:69-95` - "Validate all user input"

**Fix Required:**
```kotlin
fun sendTransaction(..., amount: BigInteger, ...) {
    viewModelScope.launch {
        try {
            // CRITICAL: Validate amount
            if (amount <= BigInteger.ZERO) {
                _state.value = SendUiState.Error("Amount must be greater than zero")
                return@launch
            }

            // ... continue with transaction
        }
    }
}
```

---

### 5. **Dead Code: Unused transactionBuilder**
**File:** `feature/send/src/main/kotlin/com/midnight/kuira/feature/send/SendViewModel.kt`
**Line:** 77
**Severity:** 🟡 **HIGH** - Code quality

**Issue:**
```kotlin
// Transaction builder (created when needed)
private var transactionBuilder: UnshieldedTransactionBuilder? = null
```
This field is declared but never used. A new `UnshieldedTransactionBuilder` is created inline at line 153.

**Violated Guideline:**
Code should be clean and maintainable (general principle)

**Fix Required:**
```kotlin
// REMOVE this line entirely (line 77)
// OR use it instead of creating inline:
private fun getOrCreateBuilder(): UnshieldedTransactionBuilder {
    return transactionBuilder ?: UnshieldedTransactionBuilder(utxoManager).also {
        transactionBuilder = it
    }
}
```

---

### 6. **Serializer Should Be Injected**
**File:** `feature/send/src/main/kotlin/com/midnight/kuira/feature/send/SendViewModel.kt`
**Line:** 80
**Severity:** 🟡 **HIGH** - Dependency Injection best practice

**Issue:**
```kotlin
// Serializer for getting signing messages
private val serializer = FfiTransactionSerializer()
```
Serializer is created directly instead of being injected via Hilt.

**Violated Guideline:**
Best practice: Dependencies should be injected (testability, consistency)

**Fix Required:**
```kotlin
@HiltViewModel
class SendViewModel @Inject constructor(
    private val balanceRepository: BalanceRepository,
    private val utxoManager: UtxoManager,
    private val transactionSubmitter: TransactionSubmitter,
    private val serializer: TransactionSerializer  // ✅ Inject it
) : ViewModel() {
    // Remove line 80
}
```

---

### 7. **Missing Address Validation in loadBalance**
**File:** `feature/send/src/main/kotlin/com/midnight/kuira/feature/send/SendViewModel.kt`
**Line:** 91-110
**Severity:** 🟡 **HIGH** - Input validation

**Issue:**
`loadBalance(address)` doesn't validate that `address` is not blank before querying.

**Violated Guideline:**
`SECURITY_GUIDELINES.md:69-95` - "Validate all user input"

**Fix Required:**
```kotlin
fun loadBalance(address: String) {
    viewModelScope.launch {
        try {
            // CRITICAL: Validate address
            if (address.isBlank()) {
                _state.value = SendUiState.Error("Address cannot be empty")
                return@launch
            }

            // Optional: Validate address format
            val validation = AddressValidator.validate(address)
            if (validation is AddressValidator.ValidationResult.Invalid) {
                _state.value = SendUiState.Error(validation.reason)
                return@launch
            }

            // ... continue loading balance
        }
    }
}
```

---

## 🟢 MEDIUM Priority Issues (Improvements)

### 8. **Seed Phrase Exposed in UI**
**File:** `feature/send/src/main/kotlin/com/midnight/kuira/feature/send/SendScreen.kt`
**Lines:** 305-335
**Severity:** 🟢 **MEDIUM** - Acknowledged MVP limitation

**Issue:**
Seed phrase is displayed in plain text input field.

**Status:** ✅ **Acknowledged in documentation** (line 44-45 of SendViewModel)

**Future Fix (Phase 3+):**
- Store seed in Android Keystore (encrypted)
- Use BiometricPrompt for authentication
- Never expose seed in UI

---

### 9. **Error Messages Could Be More Specific**
**File:** `feature/send/src/main/kotlin/com/midnight/kuira/feature/send/SendViewModel.kt`
**Lines:** 104-108, 211-216
**Severity:** 🟢 **MEDIUM** - User experience

**Issue:**
Generic catch-all error handling loses specific error context.

**Improvement:**
```kotlin
} catch (e: Exception) {
    _state.value = when (e) {
        is IllegalArgumentException -> SendUiState.Error(
            message = "Invalid input: ${e.message}",
            throwable = e
        )
        is IllegalStateException -> SendUiState.Error(
            message = "Transaction error: ${e.message}",
            throwable = e
        )
        else -> SendUiState.Error(
            message = "Unexpected error: ${e.message}",
            throwable = e
        )
    }
}
```

---

### 10. **AddressValidator: Hardcoded Network Prefixes**
**File:** `feature/send/src/main/kotlin/com/midnight/kuira/feature/send/AddressValidator.kt`
**Lines:** 47-51
**Severity:** 🟢 **MEDIUM** - Maintainability

**Issue:**
Network prefixes are hardcoded. Should be configurable for different environments.

**Improvement:**
```kotlin
object AddressValidator {
    // Make configurable via BuildConfig or Settings
    private val validPrefixes: Set<String>
        get() = if (BuildConfig.DEBUG) {
            setOf("mn_addr_preview", "mn_addr_testnet", "mn_addr")
        } else {
            setOf("mn_addr")  // Production: mainnet only
        }
}
```

---

### 11. **SendScreen: Amount Input Accepts Non-Numeric**
**File:** `feature/send/src/main/kotlin/com/midnight/kuira/feature/send/SendScreen.kt`
**Lines:** 140-157
**Severity:** 🟢 **MEDIUM** - User experience

**Issue:**
Amount parsing uses try-catch instead of filtering input.

**Improvement:**
```kotlin
OutlinedTextField(
    value = amountInput,
    onValueChange = { newValue ->
        // Only allow digits
        if (newValue.all { it.isDigit() }) {
            amountInput = newValue
        }
    },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
)
```

---

### 12. **Missing Unit Tests**
**Files:** All SendViewModel, AddressValidator
**Severity:** 🟢 **MEDIUM** - Test coverage

**Issue:**
No unit tests for SendViewModel or AddressValidator.

**Required Tests:**
```kotlin
// AddressValidatorTest.kt
@Test
fun `valid preview address passes validation`()

@Test
fun `invalid prefix returns error`()

@Test
fun `blank address returns error`()

// SendViewModelTest.kt
@Test
fun `sendTransaction with invalid address shows error`()

@Test
fun `sendTransaction with zero amount shows error`()

@Test
fun `successful transaction updates state to Success`()
```

---

## ✅ What's GOOD (Follows Best Practices)

### Architecture
✅ Clean MVVM separation (ViewModel ↔ UI)
✅ Sealed classes for exhaustive state handling
✅ StateFlow for reactive state management
✅ Proper Hilt dependency injection setup

### Code Quality
✅ Comprehensive KDoc documentation
✅ Meaningful variable names
✅ Clear separation of concerns
✅ Stateless Composables (easy to test/preview)

### Security (Partial)
✅ Address validation before sending
✅ Bech32m checksum verification
✅ Private key wiped after signing (line 274) ⚠️ (but incomplete)

### Navigation
✅ Type-safe navigation with sealed class
✅ Proper NavHost setup
✅ Optional callback pattern for BalanceScreen

---

## Recommended Fixes Priority

### IMMEDIATE (Before ANY Testing):
1. ✅ Fix seed byte wiping (add try-finally blocks)
2. ✅ Fix HDWallet clearing (call `.clear()` in finally)
3. ✅ Fix DerivedKey clearing (call `.clear()` in finally)

### BEFORE COMMIT:
4. Add amount > 0 validation
5. Remove dead `transactionBuilder` field
6. Add address validation in `loadBalance()`

### BEFORE PRODUCTION:
7. Inject TransactionSerializer via Hilt
8. Add unit tests for SendViewModel
9. Add unit tests for AddressValidator
10. Improve error messages (more specific)

---

## Security Checklist (From SECURITY_GUIDELINES.md)

- [❌] No sensitive data in logs → ⚠️ **Needs audit** (check for debug prints)
- [❌] All keys wiped after use → 🔴 **FAILED** (3 critical issues)
- [✅] Input validation on all external data → ⚠️ **Partial** (missing amount validation)
- [N/A] Android Keystore used for key storage → MVP limitation (acknowledged)
- [N/A] EncryptedSharedPreferences → MVP limitation (acknowledged)
- [N/A] BiometricPrompt → MVP limitation (acknowledged)
- [✅] Address validation before transactions → ✅ **PASSED**
- [✅] No hardcoded keys/seeds/passwords → ✅ **PASSED**

**Overall Security Score:** 🔴 **3/8 CRITICAL FAILS**

---

## Code Review Summary

**Functionality:** ✅ Implementation is logically correct
**Architecture:** ✅ Follows Android best practices
**Security:** 🔴 **CRITICAL MEMORY LEAKS - MUST FIX**
**Code Quality:** 🟡 Some cleanup needed (dead code, injections)
**Testing:** 🟢 No tests yet (acceptable for MVP, add before production)

**Recommendation:** 🔴 **DO NOT MERGE** until critical security issues are fixed.

---

## Next Steps

1. **Fix critical memory leaks** (SendViewModel.kt lines 181, 242-249, 274)
2. **Add input validation** (amount > 0, address not blank)
3. **Remove dead code** (transactionBuilder field)
4. **Add unit tests** (SendViewModel, AddressValidator)
5. **Manual testing** with real blockchain

---

**Reviewed By:** Claude
**Guidelines Referenced:**
- `SECURITY_GUIDELINES.md`
- `KOTLIN_GUIDELINES.md`
- `ARCHITECTURE_GUIDELINES.md`
- `COMPOSE_GUIDELINES.md`
