# Kuira Wallet - Implementation Plan

**Project:** Midnight Wallet for Android
**Estimate:** 80-120 hours across 6 phases
**Status:** Phase 1 in progress (90% complete)

See **PROGRESS.md** for current status and hours invested.

---

## Phase Structure

| Phase | Goal | Estimate | Dependencies |
|-------|------|----------|--------------|
| **Phase 1: Crypto Foundation** | Key derivation & addresses | 30-35h | None |
| **Phase 2: Unshielded Transactions** | Send/receive transparent tokens | 15-20h | Phase 1 |
| **Phase 3: Shielded Transactions** | Private ZK transactions | 20-25h | Phase 1, 2 |
| **Phase 4: Indexer Integration** | Sync wallet state | 10-15h | Phase 2, 3 |
| **Phase 5: DApp Connector** | Contract interaction | 15-20h | Phase 2, 4 |
| **Phase 6: UI & Polish** | Production-ready app | 15-20h | All phases |

---

## Phase 1: Crypto Foundation (30-35h)

**Goal:** Derive keys and addresses compatible with Lace wallet

### 1A: Unshielded Crypto ✅ COMPLETE (20-25h)

**Deliverables:**
- ✅ BIP-39 mnemonic generation (12/15/18/21/24 words)
- ✅ BIP-32 HD key derivation at `m/44'/2400'/account'/role/index`
- ✅ Midnight roles: NightExternal(0), NightInternal(1), Dust(2), Zswap(3), Metadata(4)
- ✅ Unshielded address: `SHA-256(publicKey)` → Bech32m encoding
- ✅ 74 tests passing

**Libraries:**
- BitcoinJ for BIP-39/32 (proven Android compatibility)
- Custom Bech32m implementation (ported from Midnight SDK)

**Files:**
```
core/crypto/
├── bip39/
│   ├── BIP39.kt                    # Interface
│   ├── MnemonicService.kt          # Implementation
│   └── BitcoinJMnemonicService.kt  # BitcoinJ wrapper
├── bip32/
│   ├── HDWallet.kt                 # HD key derivation
│   ├── MidnightKeyRole.kt          # Role enum
│   └── DerivedKey.kt               # Key wrapper
└── address/
    └── Bech32m.kt                  # Address encoding
```

### 1B: Shielded Keys ⏳ IN PROGRESS (10-15h)

**Current:** Step 1 complete (Kotlin FFI wrapper, 3h)
**Next:** Step 2 - JNI C glue + NDK build (7-11h)

**Why JNI?**
- Shielded keys use JubJub curve (ZK-friendly, complex crypto)
- Reimplementing in Kotlin = high risk for wallet
- Bridge to Rust FFI = use Midnight's battle-tested code (98% confidence)

**Deliverables:**
- ⏳ Derive shielded coin public key (32 bytes)
- ⏳ Derive shielded encryption public key (32 bytes)
- ⏳ JNI bridge: Kotlin → C → Rust FFI → midnight-zswap v6.1.0-alpha.5
- ⏳ Cross-compile for ARM64, ARM32, x86_64, x86
- ⏳ 44 tests (28 unit + 16 Android integration)

**Files:**
```
core/crypto/src/main/kotlin/.../shielded/
├── ShieldedKeys.kt              # Data class (coin_pk, enc_pk)
├── MemoryUtils.kt               # Secure memory wiping
└── ShieldedKeyDeriver.kt        # JNI wrapper

rust/kuira-crypto-ffi/
├── src/lib.rs                   # Rust FFI (derive_shielded_keys)
├── jni/kuira_crypto_jni.c       # JNI C glue (Step 2)
└── build-android.sh             # Cross-compile script (Step 2)
```

**Test Vector:** (For validation)
```
Mnemonic: "abandon abandon ... art" (24 words)
Path: m/44'/2400'/0'/3/0
Expected Coin PK: 274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a
```

**Critical:** Must use midnight-zswap v6.1.0-alpha.5 (matches Lace wallet SDK)

---

## Phase 2: Unshielded Transactions (15-20h)

**Goal:** Send/receive transparent tokens (no privacy)

**Architecture:**
- Substrate RPC client (WebSocket to Midnight node)
- UTXO state machine (Available → Pending → Spent)
- Intent-based transactions (Segment 0 = guaranteed)
- Schnorr signing (BIP-340 over secp256k1)

**Deliverables:**
- [ ] Substrate RPC client (Polkadot.js pattern)
- [ ] SCALE codec for transaction serialization
- [ ] UTXO selection (largest-first strategy)
- [ ] Multi-segment signing & binding
- [ ] Transaction submission & tracking
- [ ] Balance queries

**Files:**
```
core/network/
├── SubstrateClient.kt           # WebSocket RPC
└── ScaleCodec.kt                # Binary serialization

core/ledger/
├── TransactionBuilder.kt        # Intent-based tx
├── UtxoManager.kt               # State tracking
└── Signer.kt                    # Schnorr BIP-340
```

---

## Phase 3: Shielded Transactions (20-25h)

**Goal:** Private ZK transactions with zswap

**Architecture:**
- Uses shielded keys from Phase 1B
- Zero-knowledge proofs for privacy
- Separate UTXO set (shielded pool)

**Deliverables:**
- [ ] Shielded UTXO tracking
- [ ] ZK proof generation/verification
- [ ] Shielded transaction builder
- [ ] Convert: shielded ↔ unshielded

**Dependencies:**
- Requires Phase 1B (shielded key derivation)
- Requires Phase 2 (transaction infrastructure)

---

## Phase 4: Indexer Integration (10-15h)

**Goal:** Fast wallet sync without full node

**Architecture:**
- Connect to Midnight indexer API
- Subscribe to relevant events
- Detect incoming transactions
- Update local UTXO set

**Deliverables:**
- [ ] Indexer API client
- [ ] Event subscription
- [ ] Transaction detection
- [ ] Balance sync

---

## Phase 5: DApp Connector (15-20h)

**Goal:** Interact with Midnight smart contracts

**Architecture:**
- Deep link protocol
- Sign transaction requests
- Return results to DApp

**Deliverables:**
- [ ] Deep link handler
- [ ] Request approval UI
- [ ] Contract call signing
- [ ] Response protocol

---

## Phase 6: UI & Polish (15-20h)

**Goal:** Production-ready Android app

**Deliverables:**
- [ ] Material Design 3 UI
- [ ] Wallet creation/restore flow
- [ ] Send/receive screens
- [ ] Transaction history
- [ ] Settings & security
- [ ] App icon & branding

---

## Critical Compatibility Requirements

### Lace Wallet Compatibility ⚠️
**MUST** generate identical addresses/keys for same mnemonic:
- BIP-39: Use `@scure/bip39` algorithm (done via BitcoinJ)
- BIP-32: Exact path `m/44'/2400'/account'/role/index`
- Shielded: Use midnight-zswap v6.1.0-alpha.5 (NOT v7.0+)
- Addresses: SHA-256(publicKey) → Bech32m with "mn" prefix

**Test:** Generate wallet in Kuira → Import in Lace → Addresses match ✅

### Version Locking 🔒
**midnight-zswap:** MUST use v6.1.0-alpha.5
- Reason: v7.0 changed key derivation algorithm → incompatible keys
- Impact: Using wrong version = wallet can't be restored in Lace

---

## Architecture Decisions

### ✅ Pure Kotlin/JNI (No WASM)
**Reason:** Midnight WASM uses externref (unsupported on mobile)
**Trade-off:** More dev time, but cleaner architecture

### ✅ BitcoinJ for BIP-39/32
**Reason:** Battle-tested, Android-optimized, BIP-compliant
**Alternative:** Port @scure libraries (more work, same result)

### ✅ JNI for Shielded Keys
**Reason:** JubJub curve too complex, use Midnight's Rust code
**Trade-off:** +2 MB APK, but 98% confidence vs 85% pure Kotlin

### ✅ Direct Substrate RPC
**Reason:** No official Android SDK, build minimal client
**Trade-off:** Must handle reconnection, state sync manually

---

## Resources

- **Midnight SDK (TypeScript):** Reference for algorithms
- **midnight-ledger (Rust):** Core crypto implementation
- **Lace wallet:** Reference implementation for testing
- **Polkadot.js:** Substrate RPC patterns

---

## Risk Mitigation

**High Risk:**
- Version compatibility (test with Lace extensively)
- JNI memory leaks (use LeakCanary during testing)

**Medium Risk:**
- RPC client stability (implement reconnection, offline mode)
- UTXO state sync (test rollback scenarios)

**Low Risk:**
- UI/UX (iterate based on feedback)
