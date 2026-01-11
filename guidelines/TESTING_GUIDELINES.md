# Testing Standards & Best Practices
**Version:** 1.0.0
**Last Updated:** January 10, 2026

**Purpose:** Testing requirements and patterns for Midnight Wallet.

**Related Guidelines:**
- For Kotlin patterns in tests → See `KOTLIN_GUIDELINES.md`
- For testing security features → See `SECURITY_GUIDELINES.md`
- For testing Compose UI → See `COMPOSE_GUIDELINES.md`

---

## Test Structure (Given-When-Then)

```kotlin
@Test
fun `given valid mnemonic when deriving address then returns correct address`() {
    // Given (Arrange)
    val mnemonic = "abandon abandon abandon ... art"
    val expectedAddress = "mn_addr_testnet1abc..."

    // When (Act)
    val address = walletService.deriveAddress(mnemonic, account = 0)

    // Then (Assert)
    assertEquals(expectedAddress, address)
}
```

**Structure every test:**
1. **Given** (Arrange) - Set up test data and preconditions
2. **When** (Act) - Execute the operation being tested
3. **Then** (Assert) - Verify the outcome

---

## Test Coverage Requirements

```kotlin
// For each public function, test:

// 1. Happy path
@Test fun `given valid input when processing then succeeds`()

// 2. Null/empty input
@Test fun `given null input when processing then throws exception`()

// 3. Invalid input
@Test fun `given invalid format when processing then returns error`()

// 4. Edge cases
@Test fun `given zero amount when sending then throws exception`()
@Test fun `given insufficient balance when sending then returns error`()

// 5. Concurrent access (if applicable)
@Test fun `given concurrent calls when processing then handles correctly`()
```

**Coverage targets:**
- Unit tests: >80% code coverage
- Integration tests: All critical user flows
- Edge cases: All error conditions

---

## Test Naming Convention

```kotlin
// ✅ GOOD: Descriptive test names
@Test
fun `given expired TTL when checking pending utxos then marks as available`()

@Test
fun `given insufficient balance when creating transaction then returns InsufficientFundsError`()

// ❌ BAD: Vague test names
@Test
fun testBalance()

@Test
fun test1()
```

**Naming format:** `` `given [precondition] when [action] then [outcome]` ``

**Benefits:**
- Test name documents expected behavior
- Failures are immediately understandable
- Tests serve as living documentation

---

## Use Test Fixtures

```kotlin
// Create reusable test data
object TestFixtures {
    const val TEST_MNEMONIC = "abandon abandon abandon ... art"
    const val TEST_ADDRESS = "mn_addr_testnet1abc..."

    fun createTestWallet(
        balance: BigInteger = 1000.toBigInteger()
    ) = Wallet(
        address = TEST_ADDRESS,
        balance = balance
    )

    fun createTestUtxo(
        value: BigInteger = 100.toBigInteger()
    ) = Utxo(
        value = value,
        owner = TEST_ADDRESS,
        // ...
    )
}
```

**Use fixtures for:**
- Known test vectors (BIP-39, BIP-32, Schnorr signatures)
- Common test data (addresses, mnemonics)
- Mock objects
- Test configuration

---

## Test Organization

### Unit Tests
Test individual components in isolation:

```kotlin
// core/crypto/src/test/kotlin/
@Test
fun `given valid BIP39 mnemonic when deriving seed then matches test vector`() {
    // Use official BIP-39 test vectors
    val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    val expectedSeed = "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04"

    val actualSeed = BIP39.mnemonicToSeed(mnemonic)

    assertEquals(expectedSeed, actualSeed.toHex())
}
```

### Integration Tests
Test component interactions:

```kotlin
// core/wallet/src/androidTest/kotlin/
@Test
fun `given synced wallet when sending transaction then updates balance`() = runTest {
    // Given
    val wallet = createTestWallet()
    wallet.sync()
    val initialBalance = wallet.getBalance()

    // When
    val result = wallet.sendTransaction(
        recipient = TEST_RECIPIENT,
        amount = 100.toBigInteger()
    )

    // Then
    assertTrue(result.isSuccess)
    wallet.sync()
    val finalBalance = wallet.getBalance()
    assertTrue(finalBalance < initialBalance)
}
```

### End-to-End Tests
Test complete user flows:

```kotlin
// app/src/androidTest/kotlin/
@Test
fun `user creates wallet sends transaction and views history`() {
    // Complete user journey test
    onView(withId(R.id.create_wallet)).perform(click())
    // ... full flow
}
```

---

## Test-Driven Development (TDD)

**When to use TDD:**
- Implementing well-defined algorithms (BIP-39, Schnorr signing)
- Critical security functions (key derivation, address validation)
- Complex business logic (coin selection, fee calculation)

**TDD cycle:**
1. **Red** - Write failing test
2. **Green** - Write minimal code to pass
3. **Refactor** - Improve code while keeping tests green

**Example:**

```kotlin
// 1. RED - Write test first
@Test
fun `given 24 word mnemonic when generating seed then produces 64 bytes`() {
    val mnemonic = TestFixtures.VALID_24_WORD_MNEMONIC
    val seed = BIP39.mnemonicToSeed(mnemonic)
    assertEquals(64, seed.size)
}

// 2. GREEN - Implement minimal solution
object BIP39 {
    fun mnemonicToSeed(mnemonic: String): ByteArray {
        // Minimal implementation to pass test
        return ByteArray(64)
    }
}

// 3. REFACTOR - Improve while tests stay green
object BIP39 {
    fun mnemonicToSeed(mnemonic: String, passphrase: String = ""): ByteArray {
        val salt = "mnemonic$passphrase"
        return PBKDF2.derive(
            password = mnemonic.toByteArray(),
            salt = salt.toByteArray(),
            iterations = 2048,
            keyLength = 64
        )
    }
}
```

---

## Testing Coroutines

```kotlin
// Use runTest for suspend functions
@Test
fun `given network error when fetching balance then returns error result`() = runTest {
    // Given
    val mockApi = mock<WalletApi> {
        onBlocking { getBalance() } doThrow IOException("Network error")
    }
    val repository = WalletRepositoryImpl(mockApi)

    // When
    val result = repository.fetchBalance()

    // Then
    assertTrue(result.isFailure)
}
```

---

## Testing Security

### Test Key Wiping

```kotlin
@Test
fun `given used HDWallet when cleared then memory is wiped`() {
    // Given
    val hdWallet = HDWallet.fromSeed(TEST_SEED)
    val keysBefore = hdWallet.selectAccount(0).deriveKeysAt(0)

    // When
    hdWallet.clear()

    // Then
    // Verify internal buffers are zeroed
    val memory = hdWallet.getInternalMemoryForTesting()
    assertTrue(memory.all { it == 0.toByte() })
}
```

### Test Validation

```kotlin
@Test
fun `given invalid address when sending then throws exception`() {
    val invalidAddress = "not_a_valid_address"

    assertThrows<IllegalArgumentException> {
        wallet.sendTransaction(invalidAddress, 100.toBigInteger())
    }
}
```

---

## Test Checklist

Before merging any code:

- [ ] All tests pass (`./gradlew test`)
- [ ] Test coverage >80% for new code
- [ ] Happy path tested
- [ ] Error cases tested
- [ ] Edge cases tested
- [ ] Tests use descriptive names
- [ ] Tests follow given-when-then structure
- [ ] No flaky tests (run 3 times to verify)
- [ ] Integration tests for critical flows
- [ ] Security-sensitive code has dedicated tests

---

## Common Pitfalls

### ❌ Don't test implementation details

```kotlin
// BAD - tests internal private method
@Test
fun `test private helper function`() {
    val result = wallet.privateHelperMethod()
    // ...
}

// GOOD - tests public behavior
@Test
fun `given valid input when deriving address then returns correct address`() {
    val address = wallet.deriveAddress(mnemonic, 0)
    assertEquals(expectedAddress, address)
}
```

### ❌ Don't use real external services in tests

```kotlin
// BAD - depends on external API
@Test
fun `test real blockchain API`() = runTest {
    val balance = api.getBalance()  // Real API call
    // ...
}

// GOOD - use mocks or fakes
@Test
fun `test with mock API`() = runTest {
    val mockApi = mock<WalletApi> {
        onBlocking { getBalance() } doReturn 1000.toBigInteger()
    }
    // ...
}
```

### ❌ Don't write fragile tests

```kotlin
// BAD - fails if order changes
@Test
fun `test transaction list`() {
    assertEquals("tx1", transactions[0].id)
    assertEquals("tx2", transactions[1].id)
}

// GOOD - tests behavior, not order
@Test
fun `given transactions when fetching list then contains all transactions`() {
    assertTrue(transactions.any { it.id == "tx1" })
    assertTrue(transactions.any { it.id == "tx2" })
}
```
