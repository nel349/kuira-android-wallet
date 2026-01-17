# Kuira Wallet - Implementation Plan

**Project:** Midnight Wallet for Android
**Estimate:** 85-125 hours across 7 phases
**Status:** Phase 1 ✅ Complete | Phase 4A-Full ✅ Complete | Phase 4A-Lite 🔄 Next

See **PROGRESS.md** for current status and hours invested.

## Implementation Strategy (REVISED - Jan 2026)

**Critical Insight:** Phase 4 was over-engineered. We built a full wallet sync engine when we only needed light wallet queries to view balances.

**New Structure:**
1. ✅ **Phase 1 Complete**: Crypto/keys working (41h)
2. ✅ **Phase 4A-Full Complete**: Full sync engine (optional/advanced) (21h)
3. 🔄 **Phase 4A-Lite Next**: Light wallet queries (2-3h)
4. 🔄 **Phase 4A-UI Next**: Balance display (5-8h)
5. ⏭️ **Phase 3**: Shielded transactions (20-25h)
6. 📋 **Phase 4B**: Real-time sync with WASM (optional/future) (25-35h)

**Why This Order?**
1. **Phase 1 first**: Must have keys before anything else ✅
2. **Light wallet before full wallet**: Mobile apps should be simple and fast
3. **Phase 3 before Phase 2**: Shielded transactions are core Midnight feature (privacy-first)
4. **Phase 4B optional**: Full sync engine is "nice to have" for privacy mode, not essential

---

## Phase Structure (CURRENT ORDER)

| Phase | Goal | Estimate | Actual | Status |
|-------|------|----------|--------|--------|
| **Phase 1: Crypto Foundation** | Key derivation & addresses | 30-35h | 41h | ✅ Complete |
| **Phase 4A-Full: Full Sync Engine** | Event cache, reorg, balance calc | 8-11h | 21h | ✅ Complete (Optional) |
| **Phase 4A-Lite: Light Wallet Queries** | Query indexer for balances | 2-3h | 0h | 🔄 In Progress |
| **Phase 4A-UI: Balance Display** | Show balances in UI | 5-8h | 0h | ⏸️ Next |
| **Phase 3: Shielded Transactions** | Private ZK transactions | 20-25h | 0h | ⏸️ Not Started |
| **Phase 2: Unshielded Transactions** | Send/receive transparent tokens | 15-20h | 0h | ⏸️ Not Started |
| **Phase 4B: Real-time Sync (WASM)** | WebSocket + event deserialization | 25-35h | 0h | 📋 Future/Optional |
| **Phase 5: DApp Connector** | Contract interaction | 15-20h | 0h | ⏸️ Not Started |
| **Phase 6: UI & Polish** | Production-ready app | 15-20h | 0h | ⏸️ Not Started |

**Progress:** 62h / ~120h estimated (52% complete)

---

## Phase 1: Crypto Foundation ✅ COMPLETE (41h)

**Goal:** Derive keys and addresses compatible with Lace wallet
**Status:** ✅ Both sub-phases complete, 90 unit tests + 24 Android tests passing

### 1A: Unshielded Crypto ✅ COMPLETE (30h actual / 20-25h estimate)

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

### 1B: Shielded Keys ✅ COMPLETE (11h actual / 10-15h estimate)

**Why JNI?**
- Shielded keys use JubJub curve (ZK-friendly, complex crypto)
- Reimplementing in Kotlin = high risk for wallet
- Bridge to Rust FFI = use Midnight's battle-tested code (98% confidence)

**Deliverables:**
- ✅ Derive shielded coin public key (32 bytes)
- ✅ Derive shielded encryption public key (32 bytes)
- ✅ JNI bridge: Kotlin → C → Rust FFI → midnight-zswap v6.1.0-alpha.5
- ✅ Cross-compile for ARM64, ARM32, x86_64, x86
- ✅ 24 Android integration tests passing
- ✅ Keys match Midnight SDK v6.1.0-alpha.6

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

## Phase 4A-Full: Full Sync Engine ✅ COMPLETE (21h actual / 8-11h estimate)

**Goal:** Full wallet sync infrastructure (event caching, reorg detection, balance calculation)
**Status:** ✅ Complete - 118 tests passing
**Note:** Built as optional/advanced feature (over-engineered for mobile)

**What We Built:**
This is a **full wallet sync engine** suitable for:
- Privacy mode (don't reveal addresses to indexer)
- Offline transaction building (local UTXO set)
- Desktop applications
- Advanced users

**Deliverables:**
- ✅ GraphQL HTTP client (Ktor)
- ✅ Event caching with LRU eviction
- ✅ Blockchain reorg detection (shallow + deep)
- ✅ Balance calculator from events
- ✅ Thread-safe storage with Mutex
- ✅ Retry policy with exponential backoff
- ✅ Comprehensive error handling
- ✅ Input validation on all models
- ✅ 118 tests passing (100% pass rate)

**Why This is Optional:**
For mobile wallet balance viewing, we don't need to:
- Sync all blockchain events
- Store thousands of events locally
- Calculate balances from events
- Handle blockchain reorgs

We just need to query the indexer: "What's the balance for this address?"

**Decision:** Keep this code as "advanced feature" for future privacy mode or desktop app.

---

## Phase 4A-Lite: Light Wallet Queries (2-3h)

**Goal:** Simple balance queries for mobile wallet
**Status:** 🔄 Next task

**Why This is Different:**
This is what **99% of mobile wallets do** (MetaMask, Trust Wallet, etc.):
- Query indexer for balance
- Cache result locally
- Show to user
- Refresh when online

**Deliverables:**
- [ ] `getUnshieldedBalance(address)` - Query indexer directly
- [ ] `getShieldedBalance(coinPublicKey)` - Query shielded balance
- [ ] `getUtxos(address)` - Get UTXOs (for transaction building later)
- [ ] `getTransactionHistory(address, page)` - Get transaction list
- [ ] Balance caching layer (Room database)
- [ ] Auto-refresh when online (every 30s)
- [ ] Works offline (shows cached balance with "Last updated" timestamp)

**Implementation:**
```kotlin
interface IndexerClient {
    // LIGHT WALLET QUERIES (Phase 4A-Lite) - NEW
    suspend fun getUnshieldedBalance(address: String): Map<String, BigInteger>
    suspend fun getShieldedBalance(coinPublicKey: String): Map<String, BigInteger>
    suspend fun getUtxos(address: String): List<Utxo>
    suspend fun getTransactionHistory(address: String, page: Int = 0): List<Transaction>

    // FULL WALLET (Phase 4A-Full) - Already implemented
    suspend fun getNetworkState(): NetworkState
    suspend fun getEventsInRange(fromId: Long, toId: Long): List<RawLedgerEvent>
    suspend fun isHealthy(): Boolean
}
```

**GraphQL Queries:**
```graphql
query GetUnshieldedBalance($address: String!) {
  unshieldedBalance(address: $address) {
    tokenType
    amount
  }
}

query GetShieldedBalance($coinPublicKey: String!) {
  shieldedBalance(coinPublicKey: $coinPublicKey) {
    tokenType
    amount
  }
}
```

**Files:**
```
core/indexer/src/main/kotlin/.../api/
└── IndexerClientImpl.kt          # Add light wallet queries here

core/indexer/src/main/kotlin/.../cache/
├── BalanceCache.kt               # Cache balances locally
└── BalanceCacheManager.kt        # Auto-refresh logic
```

---

## Phase 4A-UI: Balance Display (5-8h)

**Goal:** Show balances to user
**Status:** ⏸️ After Phase 4A-Lite completes

**Deliverables:**
- [ ] Balance screen (Jetpack Compose)
- [ ] Display unshielded address & balance
- [ ] Display shielded address & balance
- [ ] Display dust balance
- [ ] Pull-to-refresh gesture
- [ ] "Last updated X min ago" timestamp
- [ ] Loading states (skeleton screens)
- [ ] Error handling UI (offline, network errors)
- [ ] Copy address button

**UI Mockup:**
```
┌─────────────────────────────────┐
│  Kuira Wallet                   │
├─────────────────────────────────┤
│                                 │
│  Unshielded Balance             │
│  1,234.56 DUST                  │
│                                 │
│  mn_addr_testnet1...            │
│  [Copy]                         │
│                                 │
├─────────────────────────────────┤
│                                 │
│  Shielded Balance               │
│  567.89 DUST                    │
│                                 │
│  mn_shield-cpk_testnet1...      │
│  [Copy]                         │
│                                 │
├─────────────────────────────────┤
│  Last updated 2 min ago         │
└─────────────────────────────────┘
```

**Files:**
```
feature/wallet/
├── balance/
│   ├── BalanceScreen.kt          # Composable UI
│   ├── BalanceViewModel.kt       # State management
│   └── BalanceUiState.kt         # UI state model
└── navigation/
    └── WalletNavigation.kt       # Navigation setup
```

---

## Phase 3: Shielded Transactions (20-25h)

**Goal:** Private ZK transactions with zswap
**Status:** ⏸️ After balance viewing works

**Why After Phase 4A-UI?**
- Need balance viewing to test transaction correctness
- Core Midnight feature (privacy-first)
- Phase 1 shielded keys already working ✅ (JNI/Rust FFI)
- More complex than unshielded, do while crypto knowledge is fresh

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
- ⏳ Phase 4A-UI (balance viewing for testing)

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

## Phase 4B: Real-Time Sync with WASM (25-35h)

**Goal:** WebSocket subscriptions + event deserialization
**Status:** 📋 FUTURE/OPTIONAL - Not needed for mobile wallet

**Why This is Optional:**
This phase builds on Phase 4A-Full (full sync engine) to add:
- Real-time event streaming (WebSocket)
- Typed event parsing (WASM deserialization)
- Better for desktop applications

**For mobile wallet**, we don't need this because:
- Light wallet queries (Phase 4A-Lite) are faster
- Less battery drain (no WebSocket connection)
- Less storage (don't cache all events)
- Simpler architecture

**Use Cases (Future):**
1. **Privacy Mode** - Don't query indexer constantly
2. **Desktop App** - Full sync engine with WebSocket
3. **Offline Transaction Building** - Need local UTXO set
4. **Advanced Users** - Want full node-like experience

**Deliverables (If Implemented Later):**
- [ ] WebSocket client with `graphql-ws` protocol
- [ ] Connection lifecycle management (init, ack, subscribe)
- [ ] Auto-reconnection with exponential backoff
- [ ] Keepalive ping/pong
- [ ] WASM integration for event deserialization
- [ ] Typed event models (Transfer, Shield, Unshield)
- [ ] TLS certificate pinning
- [ ] 30+ tests

**See:** `docs/phase4b-implementation-plan.md` for detailed plan (if needed later)

---

## Phase 2: Unshielded Transactions (15-20h)

**Goal:** Send/receive transparent tokens (no privacy)
**Status:** ⏸️ After Phase 3

**Why After Phase 3?**
- Simpler than shielded transactions
- Not core Midnight feature (users want privacy)
- Phase 3 already implements transaction infrastructure we can reuse
- Can test with light wallet balance queries

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
- ⏳ Phase 3 (transaction infrastructure to reuse)
- ✅ Phase 4A-Lite (balance viewing for testing)

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
