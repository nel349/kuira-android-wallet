# Kotlin Best Practices & Patterns
**Version:** 1.0.0
**Last Updated:** January 10, 2026

**Purpose:** Kotlin-specific coding standards for Midnight Wallet.

**Related Guidelines:**
- For testing Kotlin code → See `TESTING_GUIDELINES.md`
- For security patterns → See `SECURITY_GUIDELINES.md`

---

## Naming Conventions

```kotlin
// Classes: PascalCase
class WalletRepository

// Functions: camelCase (verb-first)
fun deriveAddress()
fun calculateFee()

// Properties: camelCase
val currentBalance
val isConnected

// Constants: SCREAMING_SNAKE_CASE
const val MAX_RETRY_ATTEMPTS = 3
const val DEFAULT_NETWORK_ID = "testnet"

// Private properties: _camelCase (only for mutable state)
private val _state = MutableStateFlow<WalletState>()
val state: StateFlow<WalletState> = _state

// Test functions: backticks with spaces
@Test
fun `should derive correct address from seed`()
```

---

## Immutability

```kotlin
// ✅ GOOD: Immutable by default
data class Wallet(
    val address: String,
    val balance: BigInteger
)

// ❌ BAD: Mutable data classes
data class Wallet(
    var address: String,
    var balance: BigInteger
)

// ✅ GOOD: Expose immutable, use private mutable
private val _balances = MutableStateFlow<Map<String, BigInteger>>(emptyMap())
val balances: StateFlow<Map<String, BigInteger>> = _balances.asStateFlow()
```

---

## Null Safety

```kotlin
// ✅ GOOD: Use nullable types explicitly
fun findUtxo(id: String): Utxo?

// ✅ GOOD: Use Result for operations that can fail
fun balanceTransaction(tx: Transaction): Result<Transaction>

// ❌ BAD: Return null for errors (use Result instead)
fun balanceTransaction(tx: Transaction): Transaction?

// ✅ GOOD: Safe calls with elvis operator
val address = wallet?.getAddress() ?: return

// ❌ BAD: Force unwrap (!! operator) - NEVER use in production code
val address = wallet!!.getAddress()  // DON'T DO THIS
```

---

## Sealed Classes for State

```kotlin
// ✅ GOOD: Exhaustive when expressions
sealed class WalletState {
    object Loading : WalletState()
    data class Synced(val balance: BigInteger) : WalletState()
    data class Error(val message: String) : WalletState()
}

when (state) {
    is WalletState.Loading -> showLoader()
    is WalletState.Synced -> showBalance(state.balance)
    is WalletState.Error -> showError(state.message)
    // Compiler ensures all cases handled
}
```

---

## Coroutines & Async Patterns

### Structured Concurrency

```kotlin
// ✅ GOOD: Use structured concurrency
class WalletViewModel : ViewModel() {
    fun loadBalance() {
        viewModelScope.launch {  // Cancelled when ViewModel cleared
            val balance = repository.getBalance()
            _state.value = balance
        }
    }
}

// ❌ BAD: Global scope (never gets cancelled)
fun loadBalance() {
    GlobalScope.launch {  // DON'T DO THIS
        val balance = repository.getBalance()
    }
}
```

### Error Handling in Coroutines

```kotlin
// ✅ GOOD: Catch exceptions in coroutines
suspend fun fetchBalance(): Result<BigInteger> = runCatching {
    val response = api.getBalance()
    response.toBigInteger()
}.onFailure { e ->
    log.error("Failed to fetch balance", e)
}

// ✅ GOOD: Show errors to user
viewModelScope.launch {
    fetchBalance()
        .onSuccess { _balance.value = it }
        .onFailure { _error.value = it.message }
}
```

### Prefer suspend over callbacks

```kotlin
// ✅ GOOD: Suspend function
suspend fun signTransaction(tx: Transaction): Transaction

// ❌ BAD: Callback hell
fun signTransaction(tx: Transaction, callback: (Transaction) -> Unit)
```

---

## Error Handling Patterns

### Use Result for Recoverable Errors

```kotlin
// ✅ GOOD: Use Result<T>
suspend fun fetchBalance(): Result<BigInteger> = runCatching {
    api.getBalance()
}

// Usage
fetchBalance()
    .onSuccess { balance -> updateUI(balance) }
    .onFailure { error -> showError(error.message) }
```

### Use Exceptions for Programming Errors

```kotlin
// ✅ GOOD: Throw for contract violations
fun setAmount(amount: BigInteger) {
    require(amount > BigInteger.ZERO) {
        "Amount must be positive, got: $amount"
    }
    this.amount = amount
}

// ✅ GOOD: Check state preconditions
fun sign(tx: Transaction) {
    check(isUnlocked) { "Wallet must be unlocked before signing" }
    // proceed with signing
}
```

### Specific Error Types

```kotlin
// ✅ GOOD: Specific, actionable errors
sealed class WalletError : Exception() {
    data class InsufficientFunds(
        val required: BigInteger,
        val available: BigInteger,
        val tokenType: String
    ) : WalletError() {
        override val message =
            "Insufficient $tokenType: need $required, have $available"
    }

    data class InvalidAddress(
        val address: String,
        val reason: String
    ) : WalletError() {
        override val message = "Invalid address '$address': $reason"
    }
}

// ❌ BAD: Generic error with string message
throw Exception("Something went wrong")
```

---

## Key Principles

1. **Immutability by default** - Use `val` unless mutation is truly needed
2. **Null safety** - Never use `!!` in production code
3. **Structured concurrency** - Always use proper coroutine scopes
4. **Result type for errors** - Use `Result<T>` for recoverable errors
5. **Sealed classes for state** - Exhaustive when expressions catch all cases
