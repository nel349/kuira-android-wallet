# Shielded Address Generation - JNI Integration Plan

## Executive Summary

**Approach:** Use JNI to call Midnight's Rust `ZswapSecretKeys` implementation

**Confidence:** 98% (using battle-tested production code)

**Estimated Effort:** 8-12 hours

---

## Why JNI Over Pure Kotlin?

### Risk Comparison

| Aspect | Pure Kotlin | JNI to Rust |
|--------|-------------|-------------|
| Cryptographic correctness | 85% | 98% |
| Implementation bugs | High risk | Low risk |
| Wallet compatibility | Must verify | Guaranteed |
| Funds loss risk | Medium | Very low |
| Build complexity | Low | Medium |
| APK size impact | None | +2-5 MB |

**Decision:** For a WALLET, correctness > convenience. Use JNI.

---

## What We Need From Rust

### Minimal API Surface

```rust
// Input: 32-byte shielded seed from BIP-32
// Output: Two 32-byte hex-encoded public keys

pub struct ShieldedKeys {
    pub coin_public_key: String,      // 64 hex chars
    pub encryption_public_key: String, // 64 hex chars
}

// Single function we need to expose
pub extern "C" fn derive_shielded_keys(
    seed_ptr: *const u8,
    seed_len: usize,
) -> *mut ShieldedKeys;

pub extern "C" fn free_shielded_keys(ptr: *mut ShieldedKeys);
```

**That's it!** Two FFI functions.

### Source Code Location

**Existing Rust code** (already in Midnight repo):
- `midnight-ledger/ledger-wasm/src/zswap_keys.rs` (WASM wrapper - we'll adapt this)
- `midnight-ledger/zswap/src/keys.rs` (core implementation)
- `midnight-ledger/transient-crypto/` (encryption keys)
- `midnight-zk/curves/` (JubJub implementation)

**What we'll create:**
- New thin wrapper crate: `kuira-crypto-ffi/` (~50 lines of Rust)
- Bridges Midnight libraries → C FFI → JNI

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│  Kotlin (Android)                               │
│  ┌───────────────────────────────────────────┐  │
│  │ ShieldedKeyDerivation.kt                  │  │
│  │  - deriveShieldedKeys(seed: ByteArray)    │  │
│  │  - Returns: Pair<coinPubKey, encPubKey>   │  │
│  └───────────────────────────────────────────┘  │
│                    ▼ JNI call                    │
│  ┌───────────────────────────────────────────┐  │
│  │ Native JNI wrapper (C)                    │  │
│  │  - JNIEXPORT jobject JNICALL              │  │
│  │  - Java_...deriveShieldedKeysNative()     │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
                     ▼ FFI call
┌─────────────────────────────────────────────────┐
│  Rust (Native Library)                          │
│  ┌───────────────────────────────────────────┐  │
│  │ kuira-crypto-ffi/src/lib.rs               │  │
│  │  #[no_mangle]                              │  │
│  │  pub extern "C" fn derive_shielded_keys() │  │
│  └───────────────────────────────────────────┘  │
│                    ▼                             │
│  ┌───────────────────────────────────────────┐  │
│  │ Midnight Ledger Libraries                 │  │
│  │  - zswap::keys::SecretKeys                │  │
│  │  - transient_crypto::encryption           │  │
│  │  - midnight_curves::JubjubSubgroup        │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

---

## Implementation Plan

### Phase 1: Rust FFI Wrapper (3-4 hours)

**Goal:** Create C-compatible Rust library

**Steps:**

1. **Create new Rust crate** in Midnight repo (or separate)
   ```bash
   cd /Users/norman/Development/midnight/midnight-libraries
   cargo new --lib kuira-crypto-ffi
   ```

2. **Add dependencies** to `Cargo.toml`:
   ```toml
   [package]
   name = "kuira-crypto-ffi"
   version = "0.1.0"
   edition = "2021"

   [lib]
   crate-type = ["staticlib", "cdylib"]  # For Android

   [dependencies]
   zswap = { path = "../midnight-ledger/zswap" }

   [profile.release]
   opt-level = 3
   lto = true
   codegen-units = 1
   ```

3. **Implement FFI wrapper** (`src/lib.rs`):
   ```rust
   use std::ffi::CString;
   use std::os::raw::c_char;
   use zswap::keys::{SecretKeys, Seed};

   #[repr(C)]
   pub struct ShieldedKeys {
       coin_public_key: *mut c_char,
       encryption_public_key: *mut c_char,
   }

   #[no_mangle]
   pub extern "C" fn derive_shielded_keys(
       seed_ptr: *const u8,
       seed_len: usize,
   ) -> *mut ShieldedKeys {
       // Safety checks
       if seed_ptr.is_null() || seed_len != 32 {
           return std::ptr::null_mut();
       }

       // Convert to Rust slice
       let seed_slice = unsafe {
           std::slice::from_raw_parts(seed_ptr, seed_len)
       };

       // Convert to fixed-size array
       let mut seed_array = [0u8; 32];
       seed_array.copy_from_slice(seed_slice);
       let seed = Seed::from(seed_array);

       // Derive keys using Midnight's implementation
       let secret_keys = SecretKeys::from(seed);
       let coin_pk = secret_keys.coin_public_key();
       let enc_pk = secret_keys.enc_public_key();

       // Serialize to hex strings
       let coin_hex = hex::encode(coin_pk.to_bytes());
       let enc_hex = hex::encode(enc_pk.to_bytes());

       // Convert to C strings
       let coin_cstr = CString::new(coin_hex).unwrap();
       let enc_cstr = CString::new(enc_hex).unwrap();

       // Allocate result
       Box::into_raw(Box::new(ShieldedKeys {
           coin_public_key: coin_cstr.into_raw(),
           encryption_public_key: enc_cstr.into_raw(),
       }))
   }

   #[no_mangle]
   pub extern "C" fn free_shielded_keys(ptr: *mut ShieldedKeys) {
       if ptr.is_null() {
           return;
       }
       unsafe {
           let keys = Box::from_raw(ptr);
           if !keys.coin_public_key.is_null() {
               let _ = CString::from_raw(keys.coin_public_key);
           }
           if !keys.encryption_public_key.is_null() {
               let _ = CString::from_raw(keys.encryption_public_key);
           }
       }
   }
   ```

4. **Build for Android targets**:
   ```bash
   # Install Android NDK targets
   rustup target add aarch64-linux-android     # ARM64
   rustup target add armv7-linux-androideabi   # ARM32
   rustup target add x86_64-linux-android      # x86_64
   rustup target add i686-linux-android        # x86

   # Build for all targets
   cargo build --release --target aarch64-linux-android
   cargo build --release --target armv7-linux-androideabi
   cargo build --release --target x86_64-linux-android
   cargo build --release --target i686-linux-android
   ```

**Output:** Native libraries (`.so` files) for each Android architecture

### Phase 2: JNI Wrapper in Kotlin (2-3 hours)

**Goal:** Create Kotlin interface to Rust library

**Steps:**

1. **Create JNI wrapper class**:
   ```kotlin
   // core/crypto/src/main/kotlin/com/midnight/kuira/core/crypto/shielded/ShieldedKeyDerivation.kt

   package com.midnight.kuira.core.crypto.shielded

   import java.nio.ByteBuffer

   /**
    * JNI wrapper for Midnight's Rust shielded key derivation.
    */
   object ShieldedKeyDerivation {

       init {
           System.loadLibrary("kuira_crypto_ffi")
       }

       data class ShieldedKeys(
           val coinPublicKey: String,      // 64 hex chars
           val encryptionPublicKey: String // 64 hex chars
       )

       /**
        * Derives shielded public keys from a 32-byte seed.
        *
        * @param seed 32-byte shielded seed from BIP-32 at m/44'/2400'/0'/3/0
        * @return Pair of coin and encryption public keys (hex-encoded)
        * @throws IllegalArgumentException if seed is not 32 bytes
        */
       fun deriveShieldedKeys(seed: ByteArray): ShieldedKeys {
           require(seed.size == 32) { "Seed must be 32 bytes, got ${seed.size}" }

           val result = deriveShieldedKeysNative(seed)
           return ShieldedKeys(
               coinPublicKey = result.coinPublicKey,
               encryptionPublicKey = result.encryptionPublicKey
           )
       }

       /**
        * Native method implemented in Rust via JNI.
        */
       private external fun deriveShieldedKeysNative(seed: ByteArray): ShieldedKeys
   }
   ```

2. **Create JNI bridge (C code)**:
   ```c
   // jni/kuira_crypto_jni.c

   #include <jni.h>
   #include <string.h>
   #include "kuira_crypto_ffi.h"  // Rust FFI header

   JNIEXPORT jobject JNICALL
   Java_com_midnight_kuira_core_crypto_shielded_ShieldedKeyDerivation_deriveShieldedKeysNative(
       JNIEnv *env,
       jobject obj,
       jbyteArray seed
   ) {
       // Get seed bytes
       jsize seed_len = (*env)->GetArrayLength(env, seed);
       if (seed_len != 32) {
           // Throw exception
           jclass exc = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
           (*env)->ThrowNew(env, exc, "Seed must be 32 bytes");
           return NULL;
       }

       jbyte *seed_bytes = (*env)->GetByteArrayElements(env, seed, NULL);

       // Call Rust FFI
       ShieldedKeys *keys = derive_shielded_keys(
           (const uint8_t *)seed_bytes,
           (size_t)seed_len
       );

       (*env)->ReleaseByteArrayElements(env, seed, seed_bytes, JNI_ABORT);

       if (keys == NULL) {
           jclass exc = (*env)->FindClass(env, "java/lang/RuntimeException");
           (*env)->ThrowNew(env, exc, "Failed to derive shielded keys");
           return NULL;
       }

       // Convert C strings to Java strings
       jstring coin_pk = (*env)->NewStringUTF(env, keys->coin_public_key);
       jstring enc_pk = (*env)->NewStringUTF(env, keys->encryption_public_key);

       // Free Rust allocation
       free_shielded_keys(keys);

       // Create ShieldedKeys object
       jclass keys_class = (*env)->FindClass(
           env,
           "com/midnight/kuira/core/crypto/shielded/ShieldedKeyDerivation$ShieldedKeys"
       );
       jmethodID constructor = (*env)->GetMethodID(
           env,
           keys_class,
           "<init>",
           "(Ljava/lang/String;Ljava/lang/String;)V"
       );

       return (*env)->NewObject(env, keys_class, constructor, coin_pk, enc_pk);
   }
   ```

### Phase 3: Build System Integration (2-3 hours)

**Goal:** Automate Rust compilation in Gradle build

**Steps:**

1. **Add Gradle plugin for Rust**:
   ```kotlin
   // build.gradle.kts (project level)
   plugins {
       id("org.mozilla.rust-android-gradle.rust-android") version "0.9.3"
   }
   ```

2. **Configure Rust in crypto module**:
   ```kotlin
   // core/crypto/build.gradle.kts

   cargo {
       module = "../../midnight-libraries/kuira-crypto-ffi"
       libname = "kuira_crypto_ffi"
       targets = listOf("arm64", "arm", "x86", "x86_64")
       profile = "release"
   }
   ```

3. **Add native libraries to APK**:
   ```kotlin
   android {
       defaultConfig {
           ndk {
               abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
           }
       }
   }
   ```

### Phase 4: Testing (2-3 hours)

**Goal:** Verify correctness against Midnight SDK

**Steps:**

1. **Unit test in Kotlin**:
   ```kotlin
   @Test
   fun `derive shielded keys matches Midnight SDK`() {
       // Test vector from "abandon abandon... art"
       val shieldedSeed = "b7637860b12f892ee07c67ad441c7935e37ac2153cefa39ae79083284f6d9180"
           .hexToByteArray()

       val keys = ShieldedKeyDerivation.deriveShieldedKeys(shieldedSeed)

       // Expected from Midnight SDK
       assertEquals(
           "274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a",
           keys.coinPublicKey
       )
       assertEquals(
           "f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b",
           keys.encryptionPublicKey
       )
   }
   ```

2. **Cross-verify with JS test**:
   ```javascript
   // In kuira-verification-test project
   const kotlinCoinPk = "274c79e9..."; // From Android test output
   const kotlinEncPk = "f3ae706b...";

   const jsCoinPk = Buffer.from(zswapKeys.coinPublicKey, 'hex').toString('hex');
   const jsEncPk = Buffer.from(zswapKeys.encryptionPublicKey, 'hex').toString('hex');

   assert.equal(kotlinCoinPk, jsCoinPk);
   assert.equal(kotlinEncPk, jsEncPk);
   ```

3. **Integration test - full address generation**:
   ```kotlin
   @Test
   fun `generate shielded address end-to-end`() {
       val mnemonic = "abandon abandon abandon..."
       val seed = BIP39.mnemonicToSeed(mnemonic)
       val wallet = HDWallet.fromSeed(seed)
       val shieldedSeed = wallet.selectAccount(0).selectRole(ZSWAP).deriveKeyAt(0)

       val keys = ShieldedKeyDerivation.deriveShieldedKeys(shieldedSeed.privateKeyBytes)

       val addressData = keys.coinPublicKey.hexToByteArray() +
                        keys.encryptionPublicKey.hexToByteArray()
       val address = Bech32m.encode("mn_shield-addr", "undeployed", addressData)

       // Verify address can be imported into Lace wallet
       println("Shielded address: $address")
   }
   ```

---

## Risk Assessment

### Low Risk (98% confidence)
- ✅ Using production Rust code (same as Midnight SDK)
- ✅ No cryptographic reimplementation
- ✅ Guaranteed compatibility

### Medium Risk (manageable)
- ⚠️ JNI memory management (can cause crashes if wrong)
  - **Mitigation**: Careful pointer handling, thorough testing
- ⚠️ Build complexity (cross-compilation)
  - **Mitigation**: Use `rust-android-gradle` plugin
- ⚠️ APK size increase (+2-5 MB)
  - **Mitigation**: Acceptable for wallet security

### Critical Success Factors
1. ✅ Test on ALL Android architectures (arm64, arm, x86, x86_64)
2. ✅ Verify with test vectors before integration
3. ✅ Memory leak testing (Valgrind on emulator)

---

## Estimated Effort

| Phase | Task | Hours |
|-------|------|-------|
| 1 | Rust FFI wrapper | 3-4 |
| 2 | JNI Kotlin wrapper | 2-3 |
| 3 | Build system integration | 2-3 |
| 4 | Testing & verification | 2-3 |
| **Total** | | **9-13 hours** |

**Plus buffer:** 10-15 hours total (more conservative)

---

## Success Criteria

✅ **Complete when:**
- Rust library compiles for all Android targets
- JNI wrapper loads successfully
- Test vectors match Midnight SDK output exactly
- Integration test generates valid shielded address
- Memory leak testing passes
- Can import Kuira wallet into Lace

---

## Alternative: Use Existing Rust Crate

**Check if Midnight publishes**:
- `@midnight-ntwrk/ledger` as a Rust crate on crates.io?
- Could save Rust wrapper phase

**Action**: Research this before starting implementation

---

## Next Steps

1. **Research** (30 min):
   - Check if Midnight ledger is on crates.io
   - Check for existing JNI examples in Midnight repos

2. **Proof of Concept** (2 hours):
   - Create minimal Rust FFI wrapper
   - Test compilation for Android
   - Verify test vectors match

3. **Full Implementation** (8-10 hours):
   - Complete all phases above
   - Full testing suite

4. **Documentation** (1 hour):
   - Build instructions
   - Troubleshooting guide

**Total**: 11-14 hours for complete JNI integration

---

## Confidence: 98%

**Why 98%?**
- ✅ Using exact same code as Midnight production
- ✅ Zero cryptographic reimplementation risk
- ✅ Proven approach (many Android apps use JNI for crypto)
- ⚠️ 2% risk from JNI integration bugs (not crypto bugs)

**This is the safe choice for a wallet.**
