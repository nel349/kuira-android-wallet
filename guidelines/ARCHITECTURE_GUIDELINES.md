# Architecture & Project Guidelines
**Version:** 1.0.0
**Last Updated:** January 10, 2026

**Purpose:** High-level architecture principles, project organization, and cross-cutting concerns.

**Related Guidelines:**
- For Kotlin-specific patterns → See `KOTLIN_GUIDELINES.md`
- For testing strategies → See `TESTING_GUIDELINES.md`
- For UI architecture → See `COMPOSE_GUIDELINES.md`
- For security architecture → See `SECURITY_GUIDELINES.md`

---

## Core Principles

### 1. Code for Humans First
- **Clarity over cleverness** - Simple, readable code beats "smart" code
- **Names matter** - Variables and functions should explain themselves
- **Comments explain WHY, not WHAT** - Code shows what; comments explain reasoning
- **No magic numbers** - Use named constants with explanations

### 2. No Assumptions
- **Never assume I understand** - Always explain the "why"
- **No "obviously" or "simply"** - If it needs explaining, explain it fully
- **Validate understanding** - Ask me to explain it back before proceeding
- **Question requirements** - If something seems off, raise it immediately

### 3. Test-First Mindset
- **Write tests BEFORE implementation** (TDD when possible)
- **Test the unhappy path** - Errors, edge cases, invalid input
- **Integration tests for flows** - Not just unit tests
- **Test names document behavior** - `given_when_then` format

---

## Clean Architecture Layers

```
┌─────────────────────────────────────┐
│  Presentation (UI/Compose)          │ ← Views, ViewModels
├─────────────────────────────────────┤
│  Domain (Business Logic)            │ ← Use Cases, Entities
├─────────────────────────────────────┤
│  Data (Implementation)              │ ← Repositories, Data Sources
└─────────────────────────────────────┘
```

**Rules:**
- **Domain NEVER depends on framework** - No Android imports in domain layer
- **Data implements domain contracts** - Interfaces defined in domain
- **Presentation depends on domain only** - Not on data layer directly
- **Use cases are single-purpose** - One public function per use case

**Why Clean Architecture:**
- **Testability** - Domain logic testable without Android framework
- **Flexibility** - Easy to swap implementations (mock repositories)
- **Maintainability** - Clear separation of concerns
- **Scalability** - Can grow without becoming tangled

---

## Module Structure

```
midnight-wallet/
├── app/                           # Main app, DI setup, navigation
├── feature/
│   ├── onboarding/         # Self-contained feature
│   │   ├── ui/            # Compose screens (internal)
│   │   ├── viewmodel/     # ViewModels (internal)
│   │   └── OnboardingModule.kt (public API)
│   └── wallet/            # Another feature
├── core/
│   ├── domain/            # Business entities, use cases
│   ├── crypto/            # Cryptography (BIP-39, Schnorr)
│   ├── ledger/            # Transaction building
│   ├── network/           # RPC, indexer clients
│   └── common/            # Shared utilities
```

**Rules:**
- **Features are independent** - Don't reference other features directly
- **Core modules are reusable** - Can be used in any feature
- **Internal by default** - Only expose public APIs explicitly
- **Test in isolation** - Each module has its own tests

**Dependency flow:**
```
app → feature → core/domain
               ↗          ↘
         core/data    core/crypto
```

---

## Dependency Injection (Hilt)

### Module Organization

```kotlin
// ✅ GOOD: Organized by feature/layer
@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun provideKeyGenerator(): KeyGenerator {
        return KeyGeneratorImpl()
    }
}

@Module
@InstallIn(ViewModelComponent::class)
object WalletModule {

    @Provides
    fun provideWalletUseCase(
        repository: WalletRepository
    ): GetBalanceUseCase {
        return GetBalanceUseCase(repository)
    }
}
```

### Interface Injection

```kotlin
// ✅ GOOD: Depend on abstractions
class WalletViewModel @Inject constructor(
    private val repository: WalletRepository  // Interface
) : ViewModel()

// ✅ GOOD: Provide implementation
@Binds
abstract fun bindWalletRepository(
    impl: WalletRepositoryImpl
): WalletRepository
```

**DI Principles:**
- Inject interfaces, not implementations
- Use `@Singleton` sparingly (only for true singletons)
- Organize modules by feature/layer
- Keep modules focused (one concern per module)

---

## Documentation Standards

### KDoc for Public APIs

```kotlin
/**
 * Derives a Midnight address from a BIP-39 mnemonic.
 *
 * This function follows the Midnight HD derivation path:
 * `m/44'/2400'/account'/role/index`
 *
 * @param mnemonic A valid 24-word BIP-39 mnemonic phrase
 * @param account The account index (typically 0 for first account)
 * @param role The key role (0=NightExternal, 2=Dust, 3=Zswap)
 * @param index The address index (0 for first address)
 * @return A Bech32m-encoded Midnight address (e.g., "mn_addr_testnet1...")
 * @throws IllegalArgumentException if mnemonic is invalid
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki">BIP-44</a>
 */
fun deriveAddress(
    mnemonic: String,
    account: Int = 0,
    role: Int = 0,
    index: Int = 0
): String
```

**When to write KDoc:**
- All public APIs
- Complex algorithms
- Security-critical functions
- Functions with non-obvious behavior

### Inline Comments for Complex Logic

```kotlin
// ✅ GOOD: Explain WHY, not WHAT
fun selectCoins(target: BigInteger): List<Utxo> {
    // Use largest-first strategy to minimize transaction size.
    // Fewer inputs = lower fees and better privacy.
    return availableUtxos
        .sortedByDescending { it.value }
        .takeWhile { ... }
}

// ❌ BAD: Comment explains WHAT (code already shows this)
// Sort UTXOs by value in descending order
return availableUtxos.sortedByDescending { it.value }
```

**Comment guidelines:**
- Explain **why**, not **what**
- Document assumptions
- Explain non-obvious algorithms
- Reference sources (BIP specs, RFC docs)

---

## Git Commit Standards

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code restructuring (no behavior change)
- `test`: Adding/updating tests
- `docs`: Documentation changes
- `chore`: Build/config changes

**Examples:**

```
feat(crypto): implement BIP-39 mnemonic generation

- Add 24-word mnemonic generation
- Include checksum validation
- Add test vectors from BIP-39 spec

Closes #12
```

```
fix(wallet): prevent double-spend in concurrent transactions

Added mutex around coin selection to ensure atomic UTXO marking.
Without this, two simultaneous transactions could select the same
UTXOs, causing the second one to fail.

Fixes #45
```

**Commit best practices:**
- One logical change per commit
- Write meaningful commit messages
- Reference issues/tickets
- Explain WHY, not WHAT
- Small commits > large commits

---

## Code Review Checklist

Before submitting any code, verify:

### Functionality
- [ ] Code does what it's supposed to do
- [ ] Edge cases are handled
- [ ] Error cases are handled
- [ ] No hardcoded values (use constants)

### Testing
- [ ] Unit tests written and passing
- [ ] Integration tests for flows
- [ ] Test coverage > 80%
- [ ] Tests are readable and maintainable

### Security
- [ ] No sensitive data in logs
- [ ] Memory wiped for keys/seeds
- [ ] Input validation present
- [ ] Using Android Keystore for secrets

### Architecture
- [ ] Follows Clean Architecture layers
- [ ] Dependencies point inward
- [ ] Single Responsibility Principle
- [ ] Interface Segregation Principle

### Code Quality
- [ ] No magic numbers
- [ ] Descriptive variable names
- [ ] Functions are small (<20 lines preferred)
- [ ] Proper error handling
- [ ] KDoc for public APIs

### Android Specific
- [ ] No memory leaks
- [ ] Lifecycle-aware
- [ ] Proper coroutine scopes
- [ ] UI state properly hoisted

---

## Performance Guidelines

### Avoid Premature Optimization

```kotlin
// ✅ GOOD: Clear, readable code first
fun calculateFee(tx: Transaction): BigInteger {
    return tx.inputs.sumOf { it.size } * FEE_PER_INPUT
}

// ❌ BAD: Premature optimization (sacrifices readability)
fun calculateFee(tx: Transaction): BigInteger {
    var total = 0L
    for (i in tx.inputs.indices) {
        total += tx.inputs[i].size.toLong()
    }
    return (total * FEE_PER_INPUT).toBigInteger()
}
```

### Profile Before Optimizing

- Use Android Profiler to find bottlenecks
- Don't optimize based on assumptions
- Benchmark before and after optimizations
- **Rule:** Make it work, make it right, make it fast (in that order)

**When to optimize:**
- After profiling shows bottleneck
- User-facing performance issues
- Battery drain
- Memory pressure

**When NOT to optimize:**
- Before profiling
- Based on assumptions
- At cost of readability
- "Just in case"

---

## When in Doubt

1. **Ask "Why?"** - If implementation reason isn't clear, ask
2. **Check the guidelines** - Does this follow our principles?
3. **Look at examples** - How did we solve similar problems?
4. **Simplify** - Can this be simpler while remaining correct?
5. **Test first** - Write test before implementation

---

## Enforcement

**Claude's Responsibility:**
- Reference these guidelines before writing code
- Explain which guideline is being followed
- Flag violations and suggest fixes
- Keep guidelines updated based on learnings

**Norman's Responsibility:**
- Challenge deviations from guidelines
- Suggest updates when patterns emerge
- Hold code reviews to standard
- Enforce in PRs

---

## Living Document

This document should evolve. When you discover:
- A better pattern
- A common mistake
- A Midnight-specific gotcha
- An Android best practice

**Update this document!**

Version history:
- 1.0.0 (Jan 10, 2026): Initial version (split from monolithic ENGINEERING_GUIDELINES.md)
