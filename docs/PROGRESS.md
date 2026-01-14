# Kuira Wallet - Progress Tracker

**Last Updated:** January 13, 2026
**Current Phase:** Phase 1B (Shielded Keys)
**Hours Invested:** 33h / ~115h estimated
**Completion:** ~29%

---

## Phase Overview

| Phase | Status | Est. | Actual | % |
|-------|--------|------|--------|---|
| **Phase 1: Crypto Foundation** | ⏳ **90%** | 30-35h | 33h | 90% |
| ↳ 1A: Unshielded Crypto | ✅ Complete | 20-25h | 30h | 100% |
| ↳ 1B: Shielded Keys (JNI FFI) | ⏳ In Progress | 10-15h | 3h | 30% |
| **Phase 2: Unshielded Transactions** | ⏸️ Not Started | 15-20h | 0h | 0% |
| **Phase 3: Shielded Transactions** | ⏸️ Not Started | 20-25h | 0h | 0% |
| **Phase 4: Indexer Integration** | ⏸️ Not Started | 10-15h | 0h | 0% |
| **Phase 5: DApp Connector** | ⏸️ Not Started | 15-20h | 0h | 0% |
| **Phase 6: UI & Polish** | ⏸️ Not Started | 15-20h | 0h | 0% |

**Next Milestone:** Complete Phase 1B (7-11h remaining)

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

## Phase 1B: Shielded Keys ⏳ IN PROGRESS

**Duration:** 3h / ~13h estimated
**Goal:** Derive shielded public keys via JNI to Rust
**Status:** ⏳ Step 1 done (Kotlin wrapper), Step 2 next (JNI C glue)

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

### Step 2: JNI C Glue + Android Build ⏳ NEXT (7-11h)

**Remaining Work:**

1. **JNI C Code** (1-2h)
   - [ ] Write `kuira_crypto_jni.c` (~50-80 lines)
   - [ ] Bridge Java bytearrays ↔ C pointers
   - [ ] Call Rust FFI: `derive_shielded_keys()`
   - [ ] Format result: `"coinPk|encPk"`
   - [ ] Free native memory

2. **Android NDK Setup** (2-3h)
   - [ ] Install Rust Android targets
   - [ ] Create `Android.mk` or `CMakeLists.txt`
   - [ ] Update `build.gradle.kts` with NDK config
   - [ ] Create `build-android.sh` script

3. **Cross-Compile** (1-2h)
   - [ ] Build for ARM64 (aarch64-linux-android)
   - [ ] Build for ARM32 (armv7-linux-androideabi)
   - [ ] Build for x86_64 (x86_64-linux-android)
   - [ ] Build for x86 (i686-linux-android)

4. **Bundle in APK** (1h)
   - [ ] Copy `.so` files to `jniLibs/`
   - [ ] Automate with Gradle task
   - [ ] Verify files in APK

5. **Testing** (1-2h)
   - [ ] Run 16 Android integration tests
   - [ ] Verify test vector matches
   - [ ] Test on real ARM64 device
   - [ ] Performance test (< 2ms per derivation)

6. **Documentation** (1h)
   - [ ] Update `SHIELDED_KEYS.md` with Step 2 complete
   - [ ] Update this file with hours/status
   - [ ] Document build instructions

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
