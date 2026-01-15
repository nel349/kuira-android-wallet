# Kuira Wallet - Implementation Plan

**Project:** Midnight Wallet for Android
**Estimate:** 80-120 hours across 6 phases (split into 7 stages)
**Status:** Phase 1 ✅ Complete (41h) | Phase 4A ⏳ Next

See **PROGRESS.md** for current status and hours invested.

## Implementation Strategy (REVISED)

**Order Change Rationale:**
1. **Phase 4 split into 4A/4B**: Ledger deserialization is blocked (requires ledger 7.0.0), but 85% of infrastructure can be built now
2. **Phase 4A → Phase 3 → Phase 2**: Prioritize shielded transactions (core Midnight feature) with balance viewing infrastructure for testing
3. **Phase 2 moved later**: Simpler unshielded transactions benefit from completed Phase 4B

**Phase 4 "PARTIAL" Explained:**
- **Phase 4A (8-11h)**: Infrastructure we can build NOW (GraphQL client, event cache, balance calculation with mock data)
- **Phase 4B (2-3h)**: Add ledger deserializer LATER when ledger 7.0.0 is published to npm
- **Blocker**: midnight-node produces ledger 7.0.0 events, but deserialization WASM not published yet

---

## Phase Structure (REVISED ORDER)

| Phase | Goal | Estimate | Dependencies |
|-------|------|----------|--------------|
| **Phase 1: Crypto Foundation** ✅ | Key derivation & addresses | 30-35h | None |
| **Phase 4A: Indexer Integration (Partial)** ⏳ | Balance viewing infrastructure | 8-11h | Phase 1 |
| **Phase 3: Shielded Transactions** | Private ZK transactions | 20-25h | Phase 1, 4A |
| **Phase 4B: Indexer Integration (Complete)** | Add ledger deserializer | 2-3h | ledger 7.0.0 release |
| **Phase 2: Unshielded Transactions** | Send/receive transparent tokens | 15-20h | Phase 1 |
| **Phase 5: DApp Connector** | Contract interaction | 15-20h | Phase 3, 4B |
| **Phase 6: UI & Polish** | Production-ready app | 15-20h | All phases |

**Why This Order?**
1. **Phase 4A before Phase 3**: Build balance viewing infrastructure to help test shielded transactions
2. **Phase 3 before Phase 2**: Shielded transactions are the core Midnight feature (privacy-first)
3. **Phase 4B when unblocked**: Complete balance viewing when ledger 7.0.0 releases
4. **Phase 2 later**: Simpler unshielded transactions, benefits from completed Phase 4B

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

## Phase 4A: Indexer Integration - Infrastructure (8-11h)

**Goal:** Build balance viewing infrastructure (without deserialization)

**Why Now?**
- Phase 3 (shielded transactions) benefits from balance viewing for testing
- 85% of Phase 4 doesn't require ledger deserialization
- Can implement infrastructure now, add deserializer later

**Architecture:**
- GraphQL client to Midnight indexer API
- Event caching (store raw hex events)
- Balance calculation logic (with mock data for now)
- UI for displaying balances

**Deliverables:**
- [ ] GraphQL client (Apollo/Ktor)
- [ ] Subscribe to `zswapLedgerEvents`
- [ ] Event cache (Room database)
- [ ] Balance calculation logic
- [ ] Balance viewing UI
- [ ] Sync progress tracking

**Blocked:**
- ⏸️ Ledger event deserialization (requires ledger 7.0.0-alpha.1)

**Files:**
```
core/indexer/
├── api/
│   ├── IndexerClient.kt          # GraphQL client
│   ├── IndexerClientImpl.kt      # Implementation
│   └── IndexerQueries.kt         # Queries/subscriptions
├── storage/
│   ├── EventCache.kt             # Cache raw events
│   └── SyncStateManager.kt       # Track sync progress
└── model/
    ├── RawLedgerEvent.kt         # Raw hex event
    └── NetworkState.kt           # Chain state

core/wallet/
└── balance/
    ├── BalanceCalculator.kt      # Balance logic (mock data)
    ├── BalanceRepository.kt      # State management
    └── TransactionHistory.kt     # Transaction tracking
```

**Testing:**
- ✅ GraphQL subscriptions work
- ✅ Event caching works
- ✅ Balance calculation works with mock events
- ⏸️ End-to-end blocked by deserialization

**See:** `PHASE_4_PARTIAL_PLAN.md` for detailed implementation strategy

---

## Phase 3: Shielded Transactions (20-25h)

**Goal:** Private ZK transactions with zswap

**Why After Phase 4A?**
- Balance viewing infrastructure helps test transaction correctness
- Core Midnight feature (privacy-first)
- Phase 1 shielded keys are already working (JNI/Rust FFI)
- More complex than unshielded, better to do while Phase 1 is fresh

**Architecture:**
- Uses shielded keys from Phase 1B ✅
- Zero-knowledge proofs via proof server
- Separate UTXO set (shielded pool)
- Transaction submission to Midnight node

**Deliverables:**
- [ ] Shielded UTXO tracking
- [ ] ZK proof generation (via proof server)
- [ ] Shielded transaction builder
- [ ] Transaction signing & submission
- [ ] Convert: shielded ↔ unshielded

**Dependencies:**
- ✅ Phase 1B (shielded key derivation via JNI)
- ✅ Phase 4A (balance viewing infrastructure for testing)

**Testing:**
- Manual verification via node logs
- GraphQL transaction status queries
- Mock balance viewing (until Phase 4B complete)

**Files:**
```
core/ledger/
├── ShieldedTransactionBuilder.kt # ZK transaction builder
├── ProofServerClient.kt          # Proof generation
├── ShieldedUtxoManager.kt        # UTXO tracking
└── ShieldedSigner.kt             # Transaction signing

core/network/
├── SubstrateClient.kt            # Node RPC client
└── ScaleCodec.kt                 # Binary serialization
```

---

## Phase 4B: Indexer Integration - Complete (2-3h)

**Goal:** Add ledger event deserialization (when ledger 7.0.0 releases)

**Status:** ⏸️ Blocked waiting for Midnight to publish ledger 7.0.0-alpha.1 to npm

**Current Blocker:**
- midnight-node 0.20.0-alpha.1 produces ledger 7.0.0 format events
- Deserialization requires ledger 7.0.0 WASM
- ledger 7.0.0-alpha.1 exists as Git tag but NOT published to npm
- Available: ledger 4.0.0, ledger-v6 6.1.0-alpha.6 (too old)

**When Unblocked:**
- [ ] Add ledger 7.0.0 dependency
- [ ] Build WASM for Android (JNI/FFI)
- [ ] Implement `LedgerEventDeserializer.kt`
- [ ] Update tests to use real deserialization
- [ ] End-to-end balance viewing works

**Impact:**
- ✅ Phase 4A infrastructure is ready
- ✅ Can deserialize cached events immediately
- ✅ Full balance viewing works for all 3 address types
- ✅ Can retroactively test Phase 3 transactions

**Files:**
```
core/indexer/
└── deserializer/
    ├── LedgerEventDeserializer.kt   # Interface (already defined)
    └── LedgerEventDeserializerImpl.kt # Implementation (TODO)
```

---

## Phase 2: Unshielded Transactions (15-20h)

**Goal:** Send/receive transparent tokens (no privacy)

**Why Later?**
- Simpler than shielded transactions
- Not core Midnight feature (users want privacy)
- Benefits from completed Phase 4B (full balance viewing)
- Phase 3 already implements transaction infrastructure

**Architecture:**
- Substrate RPC client (reuse from Phase 3)
- UTXO state machine (Available → Pending → Spent)
- Intent-based transactions (Segment 0 = guaranteed)
- Schnorr signing (BIP-340 over secp256k1) from Phase 1

**Deliverables:**
- [ ] Unshielded UTXO tracking
- [ ] SCALE codec for transaction serialization
- [ ] UTXO selection (largest-first strategy)
- [ ] Multi-segment signing & binding
- [ ] Transaction submission & tracking
- [ ] Balance queries (reuse Phase 4B)

**Dependencies:**
- ✅ Phase 1 (unshielded keys)
- ✅ Phase 3 (transaction infrastructure)
- ✅ Phase 4B (balance viewing for testing)

**Files:**
```
core/ledger/
├── UnshieldedTransactionBuilder.kt # Intent-based tx
├── UnshieldedUtxoManager.kt        # State tracking
└── UnshieldedSigner.kt             # Schnorr BIP-340 (reuse Phase 1)
```

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
