# Kuira Wallet - Implementation Plan

**Project:** Midnight Wallet for Android
**Estimate:** 85-125 hours across 7 phases
**Status:** Phase 1 ✅ Complete | Phase 4A-Full ✅ Complete | Phase 4B 🔄 In Progress

See **PROGRESS.md** for current status and hours invested.

## Implementation Strategy (REVISED - Jan 2026)

**Critical Discovery:** Midnight's indexer does NOT provide simple balance query APIs. Light wallets must:
- Subscribe to transaction events via WebSocket
- Track UTXOs locally in database
- Calculate balances by summing unspent UTXOs

**Current Structure:**
1. ✅ **Phase 1 Complete**: Crypto/keys working (41h)
2. ✅ **Phase 4A-Full Complete**: GraphQL HTTP client + sync engine (21h)
3. 🔄 **Phase 4B In Progress**: WebSocket subscriptions + UTXO tracking (8h invested)
4. ⏭️ **Phase 4B-UI Next**: Balance display (5-8h)
5. ⏭️ **Phase 3**: Shielded transactions (20-25h)
6. ⏭️ **Phase 2**: Unshielded transactions (15-20h)

**Why This Order?**
1. **Phase 1 first**: Must have keys before anything else ✅
2. **Phase 4 before transactions**: Need balance viewing to test transactions
3. **Phase 3 before Phase 2**: Shielded transactions are core Midnight feature (privacy-first)
4. **No "lite" option**: Midnight architecture requires WebSocket + local UTXO tracking

---

## Phase Structure (CURRENT ORDER)

| Phase | Goal | Estimate | Actual | Status |
|-------|------|----------|--------|--------|
| **Phase 1: Crypto Foundation** | Key derivation & addresses | 30-35h | 41h | ✅ Complete |
| **Phase 4A-Full: Full Sync Engine** | Event cache, reorg, balance calc | 8-11h | 21h | ✅ Complete |
| **Phase 4B: WebSocket + UTXO Tracking** | Subscriptions, local UTXO database | 25-35h | 8h | 🔄 In Progress |
| ↳ 4B-1: WebSocket Client | GraphQL-WS protocol | ~8h | 8h | ✅ Complete |
| ↳ 4B-2: UTXO Database | Room database + subscriptions | ~10h | 0h | ⏸️ Next |
| ↳ 4B-3: Balance Calculator | Sum unspent UTXOs | ~3h | 0h | ⏸️ Pending |
| ↳ 4B-4: UI Integration | Display balances | ~5-8h | 0h | ⏸️ Pending |
| **Phase 3: Shielded Transactions** | Private ZK transactions | 20-25h | 0h | ⏸️ Not Started |
| **Phase 2: Unshielded Transactions** | Send/receive transparent tokens | 15-20h | 0h | ⏸️ Not Started |
| **Phase 5: DApp Connector** | Contract interaction | 15-20h | 0h | ⏸️ Not Started |
| **Phase 6: UI & Polish** | Production-ready app | 15-20h | 0h | ⏸️ Not Started |

**Progress:** 70h / ~120h estimated (58% complete)

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

## Phase 4B: WebSocket + UTXO Tracking (25-35h, 8h invested)

**Goal:** Real-time transaction subscriptions + local UTXO database for balance calculation
**Status:** 🔄 In Progress (WebSocket client complete, UTXO tracking next)

**Critical Discovery:**
Midnight's indexer does NOT provide simple balance query APIs like `getUnshieldedBalance(address)`. Light wallets must:
1. Subscribe to transaction events via WebSocket (GraphQL-WS protocol)
2. Track UTXOs locally in Room database
3. Calculate balances by summing unspent UTXOs

This is the ONLY way to view balances in a Midnight wallet.

### 4B-1: WebSocket Client ✅ COMPLETE (8h actual)

**Status:** ✅ WebSocket connection working, GraphQL-WS protocol implemented
**Test Results:** 87 tests total, 0 failures (4 integration tests marked @Ignore for manual execution)

**Deliverables:**
- ✅ GraphQL-WS protocol implementation (8 message types)
- ✅ Connection lifecycle (ConnectionInit → ConnectionAck)
- ✅ Subscribe/Next/Complete/Error handling
- ✅ Ping/Pong keepalive
- ✅ Thread-safe connection state (AtomicBoolean)
- ✅ Subscription management (concurrent map)
- ✅ Auto-increment operation IDs (AtomicInteger)
- ✅ Proper sub-protocol header (`Sec-WebSocket-Protocol: graphql-transport-ws`)
- ✅ JSON encoding with defaults (`encodeDefaults = true`)

**Key Implementation:**
```kotlin
// CRITICAL: Use block parameter for sub-protocol header
session = httpClient.webSocketSession(
    urlString = url,
    block = {
        header(HttpHeaders.SecWebSocketProtocol, "graphql-transport-ws")
    }
)

// CRITICAL: Always encode default values
private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true  // Required for type field
}
```

**Lesson Learned:**
> "Please investigate the documentation before making any assumptions!"

Examining Midnight's TypeScript `indexer-client` implementation revealed the exact GraphQL-WS protocol requirements, which led to the successful connection solution.

**Files:**
```
core/indexer/src/main/kotlin/.../websocket/
├── GraphQLWebSocketClient.kt     # WebSocket client
├── GraphQLWebSocketMessage.kt    # 8 message types
└── SubscriptionFlow.kt           # Flow-based subscriptions
```

**Documentation Created:**
- `docs/learning/WEBSOCKET_SOLUTION.md` - Complete troubleshooting guide
- `docs/learning/PHASE_4_STORY.md` - End-to-end architecture explanation
- `docs/learning/KTOR_WEBSOCKET_CRASH_COURSE.md` - Ktor/channels/atomics deep dive
- `docs/learning/WEBSOCKET_FRAMES_EXPLAINED.md` - WebSocket frames from first principles
- `docs/learning/CHANNEL_VS_FLOW.md` - Channel vs Flow explanation
- `docs/learning/INDEXER_MODULE_BIG_PICTURE.md` - Complete indexer architecture

### 4B-2: UTXO Database + Subscriptions ⏸️ NEXT (~10h)

**Goal:** Subscribe to transactions and track UTXOs locally

**Deliverables:**
- [ ] Add subscription methods to IndexerClient
  - `subscribeToUnshieldedTransactions(address: String): Flow<UnshieldedTransaction>`
  - `subscribeToShieldedTransactions(sessionId: String): Flow<ShieldedTransaction>`
- [ ] Create Room database for UTXO tracking
  - `UnshieldedUtxoEntity` (txHash, index, amount, tokenType, spent)
  - `ShieldedUtxoEntity` (commitment, amount, tokenType, spent)
  - `UnshieldedUtxoDao`, `ShieldedUtxoDao`
  - `UtxoDatabase` (Room database)
- [ ] Transaction model classes
  - `UnshieldedTransaction` (inputs, outputs, timestamp)
  - `ShieldedTransaction` (commitments, nullifiers)
  - `Utxo` (txHash, index, value, spendable)
- [ ] UTXO state management
  - Mark UTXOs as spent when consumed
  - Handle chain reorgs (mark reorged UTXOs as invalid)

**GraphQL Subscriptions:**
```graphql
subscription UnshieldedTransactions($address: String!) {
  unshieldedTransactions(address: $address) {
    txHash
    inputs { txHash, index, amount, tokenType }
    outputs { index, amount, tokenType, address }
    timestamp
  }
}

subscription ShieldedTransactions($sessionId: String!) {
  shieldedTransactions(sessionId: $sessionId) {
    commitments
    nullifiers
    timestamp
  }
}
```

**Files:**
```
core/indexer/src/main/kotlin/.../api/
└── IndexerClientImpl.kt          # Add subscription wrappers

core/indexer/src/main/kotlin/.../model/
├── UnshieldedTransaction.kt      # Transaction models
├── ShieldedTransaction.kt
└── Utxo.kt

core/indexer/src/main/kotlin/.../database/
├── UtxoDatabase.kt               # Room database
├── UnshieldedUtxoDao.kt          # CRUD operations
├── ShieldedUtxoDao.kt
├── UnshieldedUtxoEntity.kt       # Database entities
└── ShieldedUtxoEntity.kt
```

### 4B-3: Balance Calculator ⏸️ PENDING (~3h)

**Goal:** Calculate balances by summing unspent UTXOs

**Deliverables:**
- [ ] `BalanceCalculator.calculateUnshieldedBalance(address: String): Map<String, BigInteger>`
- [ ] `BalanceCalculator.calculateShieldedBalance(coinPubKey: String): Map<String, BigInteger>`
- [ ] Query unspent UTXOs from Room database
- [ ] Group by token type
- [ ] Sum amounts using BigInteger

**Implementation:**
```kotlin
class BalanceCalculator(private val utxoDao: UnshieldedUtxoDao) {
    suspend fun calculateBalance(address: String): Map<String, BigInteger> {
        val unspentUtxos = utxoDao.getUnspentUtxos(address)
        return unspentUtxos
            .groupBy { it.tokenType }
            .mapValues { (_, utxos) ->
                utxos.fold(BigInteger.ZERO) { acc, utxo ->
                    acc + utxo.amount.toBigInteger()
                }
            }
    }
}
```

### 4B-4: UI Integration ⏸️ PENDING (~5-8h)

**Goal:** Display balances to user

**Deliverables:**
- [ ] Balance screen (Jetpack Compose)
- [ ] Display unshielded address & balance
- [ ] Display shielded address & balance
- [ ] Pull-to-refresh gesture
- [ ] "Last updated X min ago" timestamp
- [ ] Loading states (skeleton screens)
- [ ] Error handling UI (offline, network errors)
- [ ] Copy address button

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
