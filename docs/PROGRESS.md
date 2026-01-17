# Kuira Wallet - Progress Tracker

**Last Updated:** January 15, 2026
**Current Phase:** Phase 4A-Lite (Light Wallet Queries) + UI
**Hours Invested:** 62h / ~120h estimated
**Completion:** ~52%

---

## Phase Overview

| Phase | Status | Est. | Actual | % |
|-------|--------|------|--------|---|
| **Phase 1: Crypto Foundation** | ✅ **Complete** | 30-35h | 41h | 100% |
| ↳ 1A: Unshielded Crypto | ✅ Complete | 20-25h | 30h | 100% |
| ↳ 1B: Shielded Keys (JNI FFI) | ✅ Complete | 10-15h | 11h | 100% |
| **Phase 4A-Full: Full Sync Engine** | ✅ **Complete (Optional)** | 8-11h | 21h | 100% |
| **Phase 4A-Lite: Light Wallet Queries** | 🔄 **In Progress** | 2-3h | 0h | 0% |
| **Phase 4A-UI: Balance Display** | ⏸️ Next | 5-8h | 0h | 0% |
| **Phase 3: Shielded Transactions** | ⏸️ Not Started | 20-25h | 0h | 0% |
| **Phase 2: Unshielded Transactions** | ⏸️ Not Started | 15-20h | 0h | 0% |
| **Phase 4B: Real-time Sync (WASM)** | 📋 Future/Optional | 25-35h | 0h | 0% |
| **Phase 5: DApp Connector** | ⏸️ Not Started | 15-20h | 0h | 0% |
| **Phase 6: UI & Polish** | ⏸️ Not Started | 15-20h | 0h | 0% |

**Next Milestone:** Working balance viewer (7-11h remaining)

---

## Phase 4A-Full: Full Sync Engine ✅ COMPLETE (OPTIONAL)

**Duration:** 21 hours (January 2026)
**Goal:** Full wallet sync infrastructure (event caching, reorg detection, balance calculation)
**Status:** ✅ Complete - Marked as optional/advanced feature
**Note:** Built more than originally planned - full sync engine instead of light wallet queries

### Completed Deliverables

#### Indexer Client (HTTP Queries)
- ✅ GraphQL HTTP client using Ktor
- ✅ `getNetworkState()` - Current blockchain sync status
- ✅ `getEventsInRange()` - Historical event fetching
- ✅ `isHealthy()` - Health check endpoint
- ✅ Retry policy with exponential backoff
- ✅ Comprehensive error handling hierarchy
- ✅ TLS/HTTPS configuration (certificate pinning ready)
- **Tests:** 21 passing (RetryPolicyTest)

**Files:**
```
core/indexer/src/main/kotlin/.../api/
├── IndexerClient.kt              # Interface
├── IndexerClientImpl.kt          # Ktor implementation
├── IndexerExceptions.kt          # Error hierarchy
└── RetryPolicy.kt                # Exponential backoff
```

#### Event Storage & Caching
- ✅ In-memory event cache with LRU eviction
- ✅ Bounded cache (DOS protection, max 10,000 events)
- ✅ Thread-safe with Mutex (not ConcurrentHashMap)
- ✅ getEventRange(), getLatestEventId(), getOldestEventId()
- ✅ Access time tracking for LRU
- **Tests:** 20 passing (InMemoryEventCacheTest)

**Files:**
```
core/indexer/src/main/kotlin/.../storage/
├── EventCache.kt                 # Interface
└── InMemoryEventCache.kt         # LRU implementation
```

#### Blockchain Reorg Detection
- ✅ Full reorg detection implementation
- ✅ Shallow reorg handling (< finality threshold)
- ✅ Deep reorg handling (> finality threshold)
- ✅ Common ancestor finding
- ✅ Block history with configurable depth
- ✅ Flow-based reorg notifications
- **Tests:** 16 passing (ReorgDetectorImplTest)

**Files:**
```
core/indexer/src/main/kotlin/.../reorg/
├── ReorgDetector.kt              # Interface
├── ReorgDetectorImpl.kt          # Implementation
├── ReorgEvent.kt                 # Sealed class (Shallow/Deep)
└── ReorgConfig.kt                # Configuration
```

#### Balance Calculation
- ✅ Balance calculator from events
- ✅ Underflow detection (prevents double-spend)
- ✅ Three balance types: shielded, unshielded, dust
- ✅ BigInteger for financial calculations
- ✅ Event ordering validation
- **Tests:** 17 passing (BalanceCalculatorTest)

**Files:**
```
core/wallet/src/main/kotlin/.../balance/
└── BalanceCalculator.kt          # Event-based balance calculation
```

#### Data Model Validation
- ✅ Input validation on all models
- ✅ RawLedgerEvent validation (hex format, IDs)
- ✅ BlockInfo validation (hash format, timestamps)
- ✅ NetworkState validation (block heights)
- **Tests:** 44 passing (26 indexer + 18 wallet validation tests)

**Files:**
```
core/indexer/src/main/kotlin/.../model/
├── RawLedgerEvent.kt             # Validated event model
├── BlockInfo.kt                  # Validated block model
└── NetworkState.kt               # Validated network state
```

### Test Summary

**Total Tests:** 118 passing (100% pass rate)
- InMemoryEventCacheTest: 20 tests ✅
- ReorgDetectorImplTest: 16 tests ✅
- RetryPolicyTest: 21 tests ✅
- ModelValidationTest: 26 tests ✅
- BalanceCalculatorTest: 17 tests ✅
- LedgerEventValidationTest: 18 tests ✅

**Coverage:**
- Thread safety (concurrent operations)
- Edge cases (reorgs, balance underflows)
- Security (input validation, bounded cache)
- Network resilience (retry logic)

### Why This is "Optional"

This is a **full wallet sync engine** designed for:
- Privacy mode (don't query indexer constantly)
- Offline transaction building (local UTXO set)
- Desktop applications
- Advanced users

For **mobile balance viewing**, we only need light wallet queries (Phase 4A-Lite).

**Decision:** Keep this code as "advanced feature" for future use. Build light wallet on top.

---

## Phase 4A-Lite: Light Wallet Queries 🔄 IN PROGRESS

**Estimate:** 2-3h
**Goal:** Simple balance queries for mobile wallet
**Status:** Next task

### Planned Deliverables

- [ ] `getUnshieldedBalance(address)` - Query indexer for balance
- [ ] `getShieldedBalance(coinPublicKey)` - Query shielded balance
- [ ] `getUtxos(address)` - Get UTXOs for transaction building
- [ ] `getTransactionHistory(address)` - Get transaction list
- [ ] Balance caching (Room database)
- [ ] Auto-refresh when online

**Why This is Different from Phase 4A-Full:**
- Query indexer directly (don't sync all events)
- Cache only balances (not all events)
- Fast and simple (mobile-optimized)
- Works offline (shows cached balance)

---

## Phase 4A-UI: Balance Display ⏸️ NEXT

**Estimate:** 5-8h
**Goal:** Show balances to user
**Status:** After Phase 4A-Lite completes

### Planned Deliverables

- [ ] Balance screen (Compose UI)
- [ ] Display unshielded address & balance
- [ ] Display shielded address & balance
- [ ] Pull-to-refresh
- [ ] "Last updated" timestamp
- [ ] Loading states
- [ ] Error handling UI

---

## Phase 1A: Unshielded Crypto ✅ COMPLETE

**Duration:** ~30 hours (Dec-Jan 2026)
**Goal:** BIP-39/32 key derivation + unshielded addresses
**Status:** ✅ All deliverables complete, 74 tests passing

### Completed Deliverables

#### BIP-39 Mnemonic Generation
- ✅ Generate 12/15/18/21/24 word mnemonics
- ✅ Seed derivation (PBKDF2-HMAC-SHA512, 2048 iterations)
- ✅ Checksum validation
- ✅ Passphrase support (max 256 chars)
- ✅ Entropy wiping (security)
- **Verification:** ✅ Seeds match Midnight SDK (`@scure/bip39`)
- **Tests:** 52 passing (generation, validation, security, compatibility)
- **Library:** BitcoinJ (`org.bitcoinj:bitcoinj-core:0.16.3`)

**Files:**
```
core/crypto/src/main/kotlin/.../bip39/
├── BIP39.kt
├── MnemonicService.kt
└── BitcoinJMnemonicService.kt
```

#### BIP-32 HD Key Derivation
- ✅ Derivation path: `m/44'/2400'/account'/role/index`
- ✅ Midnight roles: NightExternal(0), NightInternal(1), Dust(2), Zswap(3), Metadata(4)
- ✅ Hierarchical memory cleanup (wallet → account → role → keys)
- ✅ Use-after-clear protection
- **Verification:** ✅ Private keys match Midnight SDK (`@scure/bip32`)
- **Tests:** 12 passing
- **Library:** BitcoinJ (DeterministicHierarchy)

**Files:**
```
core/crypto/src/main/kotlin/.../bip32/
├── HDWallet.kt
├── MidnightKeyRole.kt
└── DerivedKey.kt
```

#### Unshielded Address Generation
- ✅ Algorithm: `address = SHA-256(publicKey)` → Bech32m encoding
- ✅ Prefix: `mn_addr_testnet1...` (testnet), `mn_addr1...` (mainnet)
- ✅ BIP-340 x-only public key format (32 bytes)
- **Verification:** ✅ Addresses match Lace wallet
- **Tests:** 10 passing
- **Library:** Custom Bech32m implementation

**Files:**
```
core/crypto/src/main/kotlin/.../address/
└── Bech32m.kt
```

### Compatibility Verification

**Test:** Generated wallet with mnemonic "abandon abandon ... art"
- ✅ Seed matches Midnight SDK
- ✅ Private keys match at all roles (0-4)
- ✅ Addresses match Lace wallet
- ✅ Can restore wallet in Lace from Kuira mnemonic

---

## Phase 1B: Shielded Keys ✅ COMPLETE

**Duration:** 11h / 10-15h estimated
**Goal:** Derive shielded public keys via JNI to Rust
**Status:** ✅ Both steps complete, 24/24 tests passing

### Step 1: Kotlin FFI Wrapper ✅ COMPLETE (3h)

**Completed:**
- ✅ `ShieldedKeys.kt` - Data class for coin_pk + enc_pk
- ✅ `MemoryUtils.kt` - Secure memory wiping utilities
- ✅ `ShieldedKeyDeriver.kt` - JNI wrapper (loads libkuira_crypto_ffi.so)
- ✅ 28 unit tests passing (run on JVM without native library)
- ✅ 16 Android tests written (skipped until Step 2 completes)
- ✅ Code review complete (1 doc bug fixed, implementation clean)

**Test Results:**
```bash
$ ./gradlew :core:crypto:testDebugUnitTest --tests "*.shielded.*"
MemoryUtilsTest: 11/11 passed ✅
ShieldedKeysTest: 10/10 passed ✅
ShieldedKeyDeriverTest: 7/7 passed ✅
Total: 28/28 passed ✅
```

**Files Created:**
```
core/crypto/src/main/kotlin/.../shielded/
├── ShieldedKeys.kt              # Coin + encryption public keys
├── MemoryUtils.kt               # Wipe utilities (try-finally safe)
└── ShieldedKeyDeriver.kt        # JNI entry point

core/crypto/src/test/kotlin/.../shielded/
├── ShieldedKeysTest.kt          # 10 unit tests
├── MemoryUtilsTest.kt           # 11 unit tests
└── ShieldedKeyDeriverTest.kt    # 7 unit tests

core/crypto/src/androidTest/kotlin/.../shielded/
├── ShieldedKeyDeriverIntegrationTest.kt    # 10 tests (pending Step 2)
└── HDWalletShieldedIntegrationTest.kt      # 6 tests (pending Step 2)
```

**Rust FFI (from POC):**
```
rust/kuira-crypto-ffi/
├── Cargo.toml                   # Dependencies: midnight-zswap v6.1.0-alpha.5
└── src/lib.rs                   # derive_shielded_keys(), free_shielded_keys()
```

**API Example:**
```kotlin
val seed = derivedKey.privateKeyBytes  // 32 bytes from BIP-32 at m/44'/2400'/0'/3/0

MemoryUtils.useAndWipe(seed) { seedBytes ->
    val keys = ShieldedKeyDeriver.deriveKeys(seedBytes)
    // keys.coinPublicKey: "274c79e9..." (64 hex chars)
    // keys.encryptionPublicKey: "f3ae706b..." (64 hex chars)
}
```

### Step 2: JNI C Glue + Android Build ✅ COMPLETE (8h)

**Completed:**

1. **JNI C Code** (1h)
   - [x] Wrote `kuira_crypto_jni.c` (119 lines)
   - [x] Bridge Java bytearrays ↔ C pointers (`GetByteArrayRegion`)
   - [x] Call Rust FFI: `derive_shielded_keys()`
   - [x] Format result: `"coinPk|encPk"`
   - [x] Free native memory (`free_shielded_keys()`)
   - [x] Added `JNI_OnLoad` version checking

2. **Android NDK Setup** (2h)
   - [x] Installed Rust Android targets (all 4 architectures)
   - [x] Created `CMakeLists.txt` with ABI mapping
   - [x] Updated `build.gradle.kts` with `externalNativeBuild` config
   - [x] Created `build-android.sh` script (auto-detects NDK)

3. **Cross-Compile** (3h)
   - [x] Built for ARM64 (aarch64-linux-android) - 9.3 MB → 463 KB
   - [x] Built for ARM32 (armv7-linux-androideabi) - 7.5 MB → 458 KB
   - [x] Built for x86_64 (x86_64-linux-android) - 9.5 MB → 534 KB
   - [x] Built for x86 (i686-linux-android) - 6.7 MB → 601 KB

4. **Bundle in APK** (1h)
   - [x] CMake compiles JNI C + links Rust static libs
   - [x] Gradle automatically strips symbols (75% size reduction)
   - [x] Libraries bundled at `lib/<arch>/libkuira_crypto_ffi.so`

5. **Testing** (1h)
   - [x] Run 24 Android integration tests (16 shielded + 8 BIP-39)
   - [x] **Result: 24/24 passed (0 failures, 0 errors, 0 skipped)** ✅
   - [x] Test vector matches Midnight SDK v6.1.0-alpha.6 ✅
   - [x] Tested on Android emulator (Pixel 9a, API 16)
   - [x] Performance validated (< 2ms per derivation)

**Test Results:**
```bash
$ ./gradlew :core:crypto:connectedAndroidTest

# 24/24 tests passed ✅
# - 8 BIP-39 Android tests ✅
# - 6 HDWalletShieldedIntegrationTest ✅
# - 10 ShieldedKeyDeriverIntegrationTest ✅

# Key validations:
✅ Native library loads successfully
✅ Test vector: coinPk = 274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a
✅ Test vector: encPk = f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b
✅ Deterministic derivation (same seed → same keys)
✅ Thread safety (10 threads × 5 derivations)
✅ Memory safety (seed not modified, wiped correctly)
```

**Files Created:**
```
rust/kuira-crypto-ffi/
├── jni/kuira_crypto_jni.c          # JNI bridge (119 lines)
├── CMakeLists.txt                   # NDK build config
└── build-android.sh                 # Cross-compilation script

core/crypto/build.gradle.kts         # Updated with externalNativeBuild

# Output libraries (automatically bundled in APK):
core/crypto/build/intermediates/stripped_native_libs/.../lib/
├── arm64-v8a/libkuira_crypto_ffi.so      (463 KB)
├── armeabi-v7a/libkuira_crypto_ffi.so    (458 KB)
├── x86/libkuira_crypto_ffi.so            (601 KB)
└── x86_64/libkuira_crypto_ffi.so         (534 KB)
```

### Test Vector (For Validation)

**Mnemonic:** `abandon abandon ... art` (24 words)
**Path:** `m/44'/2400'/0'/3/0`
**Expected Output:**
```
Shielded Seed: b7637860b12f892ee07c67ad441c7935e37ac2153cefa39ae79083284f6d9180
Coin Public Key: 274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a
Encryption Public Key: f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b
```
Source: Midnight SDK `@midnight-ntwrk/ledger-v6` v6.1.0-alpha.6

### Critical Findings

**Version Compatibility Issue:**
- ⚠️ **CRITICAL:** Must use midnight-zswap v6.1.0-alpha.5
- Wallet SDK uses v6.1.0-alpha.6, but source repo was on v7.0.0-alpha.1
- v7.0 changed key derivation algorithm → completely different keys
- **Fix:** Checkout commit `163d533` (v6.1.0-alpha.5) in midnight-ledger
- **Verification:** Test passed after version fix ✅

**JNI Approach Decision:**
- **Why JNI?** JubJub curve is complex, wallet correctness > convenience
- **Confidence:** 98% (using battle-tested Rust) vs 85% (pure Kotlin rewrite)
- **Trade-off:** +2 MB APK size, but eliminates crypto implementation risk

---

## Phase 2: Unshielded Transactions ⏸️ NOT STARTED

**Estimate:** 15-20h
**Goal:** Send/receive transparent tokens

**Waiting for:** Phase 1 completion

---

## Phase 3: Shielded Transactions ⏸️ NOT STARTED

**Estimate:** 20-25h
**Goal:** Private ZK transactions

**Waiting for:** Phase 1B + Phase 2

---

## Phase 4: Indexer Integration ⏸️ NOT STARTED

**Estimate:** 10-15h
**Goal:** Fast wallet sync

**Waiting for:** Phase 2, 3

---

## Phase 5: DApp Connector ⏸️ NOT STARTED

**Estimate:** 15-20h
**Goal:** Smart contract interaction

**Waiting for:** Phase 2, 4

---

## Phase 6: UI & Polish ⏸️ NOT STARTED

**Estimate:** 15-20h
**Goal:** Production-ready app

**Waiting for:** All phases

---

## Key Metrics

**Test Coverage:**
- Unit tests: 90 passing (BIP-39: 52, BIP-32: 12, Bech32m: 10, Shielded: 28, Debug: 2)
- Android tests: 16 written (pending Phase 1B completion)
- **Total:** 90 unit + 16 integration = 106 tests

**Code:**
- Production: ~1,200 LOC (Kotlin)
- Tests: ~2,500 LOC (Kotlin)
- Rust FFI: ~200 LOC

**Performance:**
- BIP-39 seed derivation: ~500ms (PBKDF2 is intentionally slow)
- BIP-32 key derivation: < 5ms per key
- Shielded key derivation: < 2ms (estimated, will verify in Step 2)

---

## Blockers & Risks

**Current Blockers:** None

**Risks:**
- 🔴 **High:** JNI memory leaks (will test with LeakCanary in Step 2)
- 🟡 **Medium:** Cross-compilation failures (will test on multiple architectures)
- 🟢 **Low:** Performance (Rust FFI is fast, verified in POC)

---

## Next Steps

1. Write JNI C glue code (1-2h)
2. Set up NDK build system (2-3h)
3. Cross-compile for Android (1-2h)
4. Run integration tests (1-2h)
5. Update docs (1h)

**Estimated to Phase 1 complete:** 7-11 hours
