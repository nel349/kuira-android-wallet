# Phase 2-DUST: Dust Wallet Implementation

**Goal:** Enable transaction fee payment via Dust (REQUIRED for all Midnight transactions)

**Duration:** 30-40 hours estimated

**Status:** ✅ **FFI Layer Complete** | ⏳ **Integration Layer Pending**

---

## Overview

**Why Dust?** ALL Midnight transactions require dust tokens to pay fees. Without dust, transactions fail with "Invalid Transaction - Custom error: 1".

**Architecture:**
```
Phase 2E/2F: Integration Layer (Pending)
  ├─ Blockchain connection & indexer
  ├─ DustRegistration transaction builder (Kotlin)
  └─ Fee payment integration

Phase 2D: Dust FFI (✅ COMPLETE)
  ├─ State management (create, serialize, deserialize)
  ├─ Event replay (sync from blockchain)
  └─ Balance calculation (time-based generation)
```

---

## Current Status

### ✅ Completed Phases

**Phase 2D-1: Dust Key Derivation (3h)**
- Derive dust keys at BIP-44 path `m/44'/2400'/0'/2/0`
- Rust FFI: `derive_dust_public_key(seed)`
- Tests: 1 Rust test passing

**Phase 2D-2: Dust FFI Bridge (10h)**
- **Rust FFI (8 functions):**
  - `create_dust_local_state()` - Initialize state
  - `dust_wallet_balance()` - Get balance at time
  - `serialize_dust_state()` / `deserialize_dust_state()` - Persistence
  - `dust_replay_events()` - Sync from blockchain events
  - `dust_utxo_count()` / `dust_get_utxo_at()` - UTXO iteration
  - `free_dust_local_state()` / `free_byte_array()` - Memory cleanup

- **JNI C Bridge:** All 8 functions with proper signatures
- **Kotlin Wrapper:** `DustLocalState` class with high-level API
- **Tests:** 20 Rust tests + 23 Android instrumented tests (all passing)
- **Build:** All Android ABIs (arm64-v8a, armeabi-v7a, x86, x86_64)

**Phase 2D-3: Dust State Management (8h)**
- `DustRepository`, `DustDao`, `DustTokenEntity`
- Balance calculation with time-based generation
- Room database integration
- **Tests:** 47 unit tests (all passing)

**Phase 2D-4: State Persistence (2h)**
- Event replay infrastructure (SCALE codec deserialization)
- Complete save/load cycle: serialize → database → deserialize
- Dust accumulation verified: 1 NIGHT → 8.267B Specks/second
- **Tests:** 7 new tests (4 Rust + 3 Android)
- **Critical Bug Fixed:** `dtime` must be future time for accumulation

**Total Time:** ~23 hours

### ⏳ Pending Phases

**Phase 2D-5: Fee Calculation (4-5h)**
- Calculate transaction fees using midnight-ledger
- Coin selection strategy (smallest-first)
- **Deferred to:** Phase 2E (needs blockchain connection)

**Phase 2D-6: Fee Payment (4-5h)**
- Create `DustSpend` actions
- Add `dustActions` to `Intent`
- **Deferred to:** Phase 2E (needs transaction integration)

**Phase 2D-7: UTXO Registration (5-6h)**
- Build `DustRegistration` transactions
- Night UTXO self-spend logic
- **Deferred to:** Phase 2F (belongs in Kotlin integration layer, not FFI)

---

## Key Implementation Details

### Value Units (CRITICAL)
```
Specks: Smallest dust unit (balance, initial_value)
  - 1 Dust = 1,000,000 Specks
  - Used for dust token values

Stars: Night (native token) unit
  - 1 NIGHT = 1,000,000 Stars
  - Used for backing Night UTXO values

Two different value fields:
  - DustGenerationInfo.value = Stars (backing Night)
  - QualifiedDustOutput.initial_value = Specks (dust)
```

### Dust Generation Parameters
```
Rate: 8,267 Specks per Star per second
Capacity: 5 Dust per Night (5,000,000 Specks per Star)
Time to cap: ~1 week
```

### State Lifecycle
```kotlin
// Create state
val state = DustLocalState.create()

// Get balance (time-based calculation)
val balance = state.getBalance(System.currentTimeMillis())

// Replay blockchain events to sync
val newState = state.replayEvents(seed, eventsHex)

// Persist to database
val bytes = state.serialize()
database.save(bytes)

// Load from database
val loaded = DustLocalState.deserialize(bytes)

// Always close when done
state.close()
```

### Event Types
```rust
DustInitialUtxo {
    output: QualifiedDustOutput,
    generation: DustGenerationInfo,
    generation_index: u64,
    block_time: Timestamp,
}

DustSpendProcessed {
    commitment: DustCommitment,
    nullifier: DustNullifier,
    v_fee: u128,
    declared_time: Timestamp,
}

DustGenerationDtimeUpdate {
    update: TreeInsertionPath<DustGenerationInfo>,
    block_time: Timestamp,
}
```

---

## Next Steps

### Immediate: Phase 2E - Blockchain Integration
**Prerequisites:** Network connection, indexer client, transaction submission

**What to Build:**
1. **Network Layer:**
   - Connect to Midnight testnet node
   - Submit transactions via RPC
   - Receive transaction receipts

2. **Indexer Client:**
   - Subscribe to blockchain events
   - Filter dust-related events
   - Replay into DustLocalState

3. **Fee Integration:**
   - Calculate fees using midnight-ledger
   - Select dust coins to cover fee
   - Build `DustActions` with spends
   - Add to `Intent.dustActions`

### Phase 2F - DustRegistration Builder
**Prerequisites:** Phase 2E complete

**What to Build:**
1. **Transaction Builder (Kotlin):**
   - Select Night UTXOs to register
   - Build DustRegistration transaction
   - Night UTXO self-spend logic
   - Sign with Night key

2. **UI Flow:**
   - Registration screen (select UTXOs)
   - Show estimated generation (e.g., "5 NIGHT → 25 Dust in 7 days")
   - Submit and track confirmation

---

## Files

**Rust FFI:**
- `rust/kuira-crypto-ffi/src/dust_ffi.rs` (900+ lines, 20 tests)
- `rust/kuira-crypto-ffi/jni/kuira_crypto_jni.c` (JNI bridge)

**Kotlin:**
- `core/crypto/.../dust/DustLocalState.kt` (Kotlin wrapper, 560 lines)
- `core/indexer/.../repository/DustRepository.kt`
- `core/indexer/.../database/DustDao.kt`
- `core/indexer/.../database/DustTokenEntity.kt`
- `core/indexer/.../dust/DustBalanceCalculator.kt`

**Tests:**
- `core/crypto/.../DustLocalStateInstrumentedTest.kt` (23 tests)
- `core/indexer/.../DustBalanceCalculatorTest.kt` (16 tests)
- `core/indexer/.../DustDaoTest.kt` (18 tests)
- `core/indexer/.../DustRepositoryTest.kt` (13 tests)

---

## Reference

**TypeScript SDK:**
- `midnight-wallet/packages/dust-wallet/src/`
- `midnight-ledger/ledger-wasm/src/dust.rs`
- `midnight-ledger/ledger/src/dust.rs`

**Specification:**
- `midnight-ledger/spec/dust.md`

**Investigation:**
- `kuira-verification-test/ROOT_CAUSE_DUST_FEE_REQUIRED.md`

---

*Last Updated: January 25, 2026*
