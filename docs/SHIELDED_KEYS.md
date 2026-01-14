# Shielded Key Derivation Implementation

**Status:** ✅ Phase 1B Complete - Shielded key derivation fully operational
**Last Updated:** 2026-01-13

---

## Current Status

### ✅ Step 1 Complete: Kotlin FFI Wrapper (3 hours)
- **Production code:** 3 files, 350 LOC
- **Tests:** 44 tests (28 unit + 16 Android)
- **All unit tests passing:** ✅
- **Code review:** ✅ 1 doc bug fixed, implementation clean

### ✅ Step 2 Complete: JNI C Glue + Android Build (8 hours)
- [x] Write JNI C code (119 lines in kuira_crypto_jni.c)
- [x] Set up NDK cross-compilation (CMakeLists.txt + build.gradle.kts)
- [x] Build for ARM64, ARM32, x86_64, x86
- [x] Bundle `.so` files in APK (463KB - 601KB per arch, stripped)
- [x] Run 16 Android integration tests - **All 24 tests passed** ✅

---

## Implementation

### Files Created (Step 1)
```
core/crypto/src/main/kotlin/.../shielded/
├── ShieldedKeys.kt              # Data class (coin_pk, enc_pk)
├── MemoryUtils.kt               # Secure memory wiping utilities
└── ShieldedKeyDeriver.kt        # JNI wrapper (loads libkuira_crypto_ffi.so)

core/crypto/src/test/kotlin/.../shielded/
├── ShieldedKeysTest.kt          # 10 unit tests
├── MemoryUtilsTest.kt           # 11 unit tests
└── ShieldedKeyDeriverTest.kt    # 7 unit tests

core/crypto/src/androidTest/kotlin/.../shielded/
├── ShieldedKeyDeriverIntegrationTest.kt    # 10 Android tests (skipped until Step 2)
└── HDWalletShieldedIntegrationTest.kt      # 6 Android tests (skipped until Step 2)
```

### Rust FFI (Already Complete from POC)
```
rust/kuira-crypto-ffi/
├── Cargo.toml                   # Dependencies: midnight-zswap v6.1.0-alpha.5
├── src/lib.rs                   # FFI: derive_shielded_keys(), free_shielded_keys()
└── (Step 2 will add JNI glue)
```

---

## API Usage

```kotlin
import com.midnight.kuira.core.crypto.shielded.*
import com.midnight.kuira.core.crypto.bip32.*

// Full flow: Mnemonic → Shielded Keys
val mnemonic = "abandon abandon ... art"
val bip39Seed = BIP39.mnemonicToSeed(mnemonic)

MemoryUtils.useAndWipe(bip39Seed) { seed ->
    val hdWallet = HDWallet.fromSeed(seed)
    try {
        val derivedKey = hdWallet
            .selectAccount(0)
            .selectRole(MidnightKeyRole.ZSWAP)  // m/44'/2400'/0'/3/0
            .deriveKeyAt(0)

        try {
            val shieldedKeys = ShieldedKeyDeriver.deriveKeys(derivedKey.privateKeyBytes)
            // shieldedKeys.coinPublicKey: "274c79e9..." (64 hex chars)
            // shieldedKeys.encryptionPublicKey: "f3ae706b..." (64 hex chars)
        } finally {
            derivedKey.clear()
        }
    } finally {
        hdWallet.clear()
    }
}
```

---

## Key Decisions & Findings

### 1. Version Compatibility ⚠️ CRITICAL
**Finding:** Midnight changed key derivation algorithm between v6.1 → v7.0
**Decision:** Pin to `midnight-zswap v6.1.0-alpha.5` (matches wallet SDK v6.1.0-alpha.6)
**Impact:** Using wrong version produces completely different keys (wallet incompatibility)

### 2. JNI Approach (98% confidence)
**Decision:** Bridge to Rust FFI instead of reimplementing in Kotlin
**Reason:** JubJub curve is complex, wallet correctness > convenience
**Trade-off:** +2 MB APK size, but battle-tested crypto

### 3. Test Strategy
- **Unit tests (28):** Run on JVM without native library
- **Android tests (16):** Skip via `assumeTrue()` until Step 2 completes
- **Test vectors:** Match Midnight SDK output exactly

### 4. Code Review Findings
- ✅ 1 documentation bug fixed (error handling docs)
- ✅ 0 implementation bugs
- ✅ All 44 tests provide value (no redundancy)
- ✅ Security best practices followed

---

## Step 2 Implementation Summary

### ✅ JNI C Glue Code (Completed)
**File:** `rust/kuira-crypto-ffi/jni/kuira_crypto_jni.c` (119 lines)

- Extracts 32-byte seed from Java ByteArray using `GetByteArrayRegion()`
- Calls Rust FFI: `derive_shielded_keys(seed_ptr, 32)`
- Formats result as `"coinPk|encPk"` string for Kotlin parsing
- Properly frees native memory: `free_shielded_keys()`
- Includes `JNI_OnLoad` for version checking (JNI 1.6)

### ✅ Android NDK Setup (Completed)
**Files:** `CMakeLists.txt` + `core/crypto/build.gradle.kts`

- Installed Rust Android targets: `aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android`, `i686-linux-android`
- Created `CMakeLists.txt` with ABI → Rust target mapping
- Updated `build.gradle.kts` with `externalNativeBuild` configuration
- Created `build-android.sh` cross-compilation script (auto-detects NDK)

### ✅ Cross-Compilation (Completed)
Built `.a` static libraries for all architectures:
- `aarch64-linux-android`: 9.3 MB → 463 KB (stripped .so)
- `armv7-linux-androideabi`: 7.5 MB → 458 KB (stripped .so)
- `x86_64-linux-android`: 9.5 MB → 534 KB (stripped .so)
- `i686-linux-android`: 6.7 MB → 601 KB (stripped .so)

### ✅ APK Bundling (Completed)
Native libraries automatically bundled by Gradle build system:
- CMake compiles JNI C code + links Rust `.a` files → `.so` shared libraries
- Gradle strips debug symbols: 1.7-1.9 MB → 458-601 KB per architecture
- Libraries bundled in APK at: `lib/<arch>/libkuira_crypto_ffi.so`

### ✅ Testing (Completed)
```bash
./gradlew :core:crypto:connectedAndroidTest

# Results: 24/24 tests passed (0 failures, 0 errors, 0 skipped)
# - 8 BIP-39 Android tests ✅
# - 6 HDWallet shielded integration tests ✅
# - 10 ShieldedKeyDeriver integration tests ✅

# Key validations:
# ✅ Native library loads successfully on Android device
# ✅ Test vector matches Midnight SDK v6.1.0-alpha.6 exactly
# ✅ Deterministic derivation (same seed → same keys)
# ✅ Thread safety (10 concurrent threads × 5 derivations)
# ✅ Memory safety (seed not modified, proper wiping)
```

---

## Test Vector (For Validation)

**Mnemonic:** `abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art`

**Path:** `m/44'/2400'/0'/3/0`

**Expected Output:**
```
Shielded Seed: b7637860b12f892ee07c67ad441c7935e37ac2153cefa39ae79083284f6d9180
Coin Public Key: 274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a
Encryption Public Key: f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b
```

Source: Midnight SDK `@midnight-ntwrk/ledger-v6` v6.1.0-alpha.6

---

## Algorithm Reference

**Derivation Steps:**
1. BIP-39: Mnemonic → 64-byte seed
2. BIP-32: Derive at `m/44'/2400'/0'/3/0` → 32-byte shielded seed
3. Rust FFI:
   - Coin secret key: `Blake2b("midnight:csk" || seed)`
   - Coin public key: `Blake2b("midnight:zswap-pk[v1]" || coin_secret_key)`
   - Encryption keys: JubJub curve operations (twisted Edwards on BLS12-381)

**Performance:** < 2ms per derivation (< 1ms Rust + < 0.5ms Kotlin)

---

## Troubleshooting

### Native library not loading
```
Error: Failed to load native library 'kuira_crypto_ffi'
```
**Solution:** Step 2 not complete yet. Android tests will skip until `.so` files built and bundled.

### Test vector mismatch
```
Expected: 274c79e9...
Got: 9408aeff...
```
**Solution:** Wrong midnight-ledger version. Must use v6.1.0-alpha.5:
```bash
cd /path/to/midnight-ledger
git checkout 163d533  # v6.1.0-alpha.5
```

### Build fails for ARM64
**Solution:** Install Android targets:
```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
```

---

## Resources

- **Rust FFI source:** `rust/kuira-crypto-ffi/src/lib.rs`
- **Midnight ledger:** `~/Development/midnight/midnight-libraries/midnight-ledger/`
- **Midnight SDK:** `@midnight-ntwrk/ledger-v6` (TypeScript reference)
- **Test verification script:** `~/Development/midnight/kuira-verification-test/debug-coin-key.mjs`

---

## History

- **2026-01-13:** Step 2 complete - JNI + NDK build (8 hours, 24/24 tests passing)
- **2026-01-13:** Step 1 complete - Kotlin FFI wrapper (3 hours, 1 doc bug fixed)
- **2026-01-13:** POC validated - Rust FFI works with v6.1.0-alpha.5
- **2026-01-13:** Version mismatch discovered - v7.0 incompatible with v6.1

## Total Implementation Time

**Phase 1B (Shielded Keys):** 11 hours total
- Step 1 (Kotlin FFI wrapper): 3 hours
- Step 2 (JNI + NDK + Testing): 8 hours
