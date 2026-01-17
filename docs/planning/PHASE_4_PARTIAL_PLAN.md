# Phase 4 Partial Implementation Plan

**Status:** ✅ APPROVED - This is the ACTUAL plan for Phase 4A

## What "PARTIAL" Means

**This IS the plan, not a "maybe":**
- We're implementing Phase 4 in TWO stages due to a blocker
- **Phase 4A (Partial)**: 85% of infrastructure we can build NOW (8-11h)
- **Phase 4B (Complete)**: 15% blocked by ledger deserialization (2-3h when unblocked)

**The blocker:**
- midnight-node 0.20.0-alpha.1 produces ledger 7.0.0 format events
- Deserialization requires ledger 7.0.0 WASM
- ledger 7.0.0-alpha.1 NOT published to npm yet
- Unknown timeline for release

**Why split the phase:**
- Don't waste time waiting for unblocked work
- Phase 3 benefits from Phase 4A infrastructure
- When ledger 7.0.0 releases, Phase 4B takes only 2-3h

## Goal
Implement Phase 4 (Indexer Integration) infrastructure **before** Phase 3, leaving only the ledger deserialization as a TODO when ledger 7.0.0 is published.

## Why This Order Makes Sense

**Testing Benefit**: When Phase 2 is implemented, balance viewing will be ready to test transactions immediately (once ledger 7.0.0 releases).

**What We Can Do Now**: 85% of Phase 4 infrastructure doesn't depend on ledger deserialization.

## Implementation Breakdown

### ✅ Part 1: GraphQL Client (3-4 hours)
**Not Blocked** - Can implement now

**Module**: `core:indexer`

**Files to Create**:
```
core/indexer/src/main/kotlin/com/midnight/kuira/core/indexer/
├── api/
│   ├── IndexerClient.kt          # GraphQL client interface
│   ├── IndexerClientImpl.kt      # Apollo/Ktor implementation
│   └── IndexerQueries.kt         # GraphQL queries/subscriptions
└── model/
    ├── RawLedgerEvent.kt         # Raw hex event from API
    ├── BlockInfo.kt              # Block metadata
    └── NetworkState.kt           # Chain sync state
```

**Implementation**:
```kotlin
interface IndexerClient {
    // Subscribe to ledger events
    fun subscribeToZswapEvents(): Flow<RawLedgerEvent>

    // Subscribe to blocks
    fun subscribeToBlocks(): Flow<BlockInfo>

    // Get network state
    suspend fun getNetworkState(): NetworkState

    // Health check
    suspend fun isHealthy(): Boolean
}

data class RawLedgerEvent(
    val id: Long,
    val rawHex: String,  // Raw hex - can't deserialize yet
    val maxId: Long
)
```

**Dependencies**:
```kotlin
implementation("com.apollographql.apollo3:apollo-runtime:3.8.2")
implementation("io.ktor:ktor-client-cio:2.3.7")
```

**Tests**: Mock GraphQL responses, verify subscriptions work

---

### ✅ Part 2: Event Storage (2-3 hours)
**Not Blocked** - Can implement now

**Module**: `core:indexer`

**Files to Create**:
```
core/indexer/src/main/kotlin/com/midnight/kuira/core/indexer/
└── storage/
    ├── EventCache.kt             # In-memory event cache
    ├── EventRepository.kt        # Room database for events
    └── SyncStateManager.kt       # Track sync progress
```

**Implementation**:
```kotlin
interface EventCache {
    // Store raw events (can't deserialize yet, but can cache)
    suspend fun storeRawEvent(event: RawLedgerEvent)

    // Get events by ID range
    suspend fun getEventRange(fromId: Long, toId: Long): List<RawLedgerEvent>

    // Get latest event ID
    suspend fun getLatestEventId(): Long?

    // Clear cache
    suspend fun clear()
}

interface SyncStateManager {
    // Track sync progress
    suspend fun updateSyncProgress(currentBlock: Long, maxBlock: Long)

    // Get sync percentage (0.0 to 1.0)
    fun getSyncProgress(): Flow<Float>

    // Is fully synced?
    fun isFullySynced(): Flow<Boolean>
}
```

**Why Store Undeserialized Events?**
- When ledger 7.0.0 releases, can deserialize cached events
- Don't need to re-sync from scratch
- Can implement cache invalidation logic now

---

### ✅ Part 3: Balance Calculation Logic (3-4 hours)
**Not Blocked** - Can implement with mock data

**Module**: `core:wallet`

**Files to Create**:
```
core/wallet/src/main/kotlin/com/midnight/kuira/core/wallet/
└── balance/
    ├── BalanceCalculator.kt      # Core balance logic
    ├── BalanceRepository.kt      # Balance state management
    └── TransactionHistory.kt     # Transaction tracking
```

**Implementation**:
```kotlin
// Define the interface now, even though we can't deserialize events yet
interface BalanceCalculator {
    // TODO: Requires LedgerEvent (ledger 7.0.0)
    // For now, accept raw hex and throw NotImplementedError
    suspend fun processEvent(rawHex: String): BalanceUpdate

    // This logic can be implemented now (uses domain types)
    suspend fun calculateBalance(
        events: List<LedgerEvent>,
        address: String
    ): Balance
}

data class Balance(
    val shielded: BigInteger,
    val unshielded: BigInteger,
    val dust: BigInteger
) {
    val total: BigInteger get() = shielded + unshielded + dust
}

// Can implement and test with mock LedgerEvent objects
data class LedgerEvent(
    val id: Long,
    val type: EventType,
    val amount: BigInteger,
    val sender: String?,
    val receiver: String?
)

enum class EventType {
    COINBASE,
    TRANSFER,
    SHIELD,
    UNSHIELD
}
```

**Testing Approach**:
```kotlin
@Test
fun `given mock ledger events when calculating balance then sums correctly`() {
    val events = listOf(
        LedgerEvent(1, EventType.COINBASE, 1000.toBigInteger(), null, "mn_addr_..."),
        LedgerEvent(2, EventType.TRANSFER, 300.toBigInteger(), "mn_addr_...", "other"),
        LedgerEvent(3, EventType.COINBASE, 500.toBigInteger(), null, "mn_addr_...")
    )

    val balance = calculator.calculateBalance(events, "mn_addr_...")

    assertEquals(1200.toBigInteger(), balance.unshielded) // 1000 + 500 - 300
}
```

**Why This Works**:
- Balance calculation logic is independent of deserialization
- Can define domain types now
- Can write comprehensive tests with mock data
- When deserialization works, just plug it in

---

### ⏸️ Part 4: Ledger Deserialization (BLOCKED)
**Cannot Implement** - Waiting for ledger 7.0.0

**What's Needed**:
```kotlin
// This interface is ready, but implementation is blocked
interface LedgerEventDeserializer {
    // Convert raw hex to LedgerEvent domain type
    // Requires: @midnight-ntwrk/ledger 7.0.0-alpha.1 WASM
    suspend fun deserialize(rawHex: String): LedgerEvent
}
```

**Current Implementation**:
```kotlin
class LedgerEventDeserializerImpl : LedgerEventDeserializer {
    override suspend fun deserialize(rawHex: String): LedgerEvent {
        // TODO: Implement when ledger 7.0.0 is published
        throw NotImplementedError(
            "Ledger event deserialization requires ledger 7.0.0-alpha.1 " +
            "(not yet published to npm). See: " +
            "https://github.com/midnightntwrk/midnight-ledger/tags/ledger-7.0.0-alpha.1"
        )
    }
}
```

**When ledger 7.0.0 releases**:
1. Add dependency to `package.json`
2. Build WASM module for Android (via JNI/FFI)
3. Implement deserialization
4. All other code is ready

---

## Testing Strategy

### Phase 4 Partial (Now)
- ✅ Test GraphQL subscriptions with real indexer
- ✅ Test event caching/storage
- ✅ Test balance calculation with mock events
- ⏸️ Cannot test end-to-end (blocked by deserialization)

### Phase 2 (Next)
- ✅ Test transaction building
- ✅ Test transaction signing
- ✅ Test transaction submission
- ⚠️ Manual verification (node logs, GraphQL transaction status)

### Phase 4 Complete (After ledger 7.0.0)
- ✅ Implement deserialization
- ✅ Test end-to-end balance viewing
- ✅ Use balance viewing to test Phase 2 retroactively

---

## Advantages of This Approach

**1. Infrastructure Ready**
- When ledger 7.0.0 releases, only need to add deserializer
- Don't need to design architecture later
- Can start using immediately

**2. Better Testing for Phase 2**
- Balance logic is ready (just needs deserializer)
- Can write integration tests with mocked deserialization
- When deserializer works, tests become real

**3. Educational Value**
- Understand balance calculation before implementing transactions
- Clearer mental model of wallet state
- Better transaction testing strategy

**4. Time Efficient**
- Implement non-blocked work now (8-11 hours)
- Don't waste time on blocked work
- Only 2-3 hours needed when ledger 7.0.0 releases

---

## Estimated Timeline

### Phase 4 Partial (8-11 hours)
- GraphQL client: 3-4 hours
- Event storage: 2-3 hours
- Balance calculation: 3-4 hours
- Integration tests: 2-3 hours (with mocked deserializer)

### Phase 2 (15-20 hours) - No changes

### Phase 4 Complete (2-3 hours) - When ledger 7.0.0 releases
- Implement deserializer: 2-3 hours
- Update tests to use real deserializer: 1 hour

**Total**: Same as original plan (Phase 4: 10-15h), just split into two stages

---

## Implementation Order (REVISED)

### Strategy: Interleaved Phase 4 + Phase 3

**Why This Makes Sense**:
- Phase 3 (shielded transactions) is the core Midnight feature
- Phase 4 (balance viewing) helps test Phase 3 immediately
- Phase 2 (unshielded) is simpler, less critical - can do later
- Can work on both phases in parallel

### Timeline

**Week 1: Phase 4 Basic Infrastructure (8-11h)**
1. GraphQL client (3-4h)
2. Event storage (2-3h)
3. Balance calculation logic (3-4h)
- Milestone: Can subscribe to events, calculate balances with mock data

**Week 2-3: Phase 3 Core (20-25h) + Phase 4 UI (3-4h)**
1. Shielded transaction building (6-8h)
2. Zero-knowledge proof integration (6-8h)
3. Transaction signing & submission (4-5h)
4. Phase 4: Balance viewing UI (3-4h)
- Milestone: Can send shielded transactions, see mock balances

**Week 4: Integration & Testing (5-7h)**
1. Test shielded transactions with manual verification
2. Integration tests with mocked deserializer
3. Document verification workflow
- Milestone: Phase 3 complete with degraded testing

**Future: Complete Phase 4 (2-3h)**
- When ledger 7.0.0 releases: Add deserializer
- Full balance viewing immediately works
- Retroactive testing of Phase 3 transactions

**Later: Phase 2 (15-20h)**
- Unshielded transactions (simpler)
- Can use completed Phase 4 for testing

---

## Why Phase 3 (Shielded) Before Phase 2 (Unshielded)?

**Technical Reasons**:
1. **Phase 1 Foundation**: Already have shielded key derivation via JNI/Rust FFI (114 tests passing)
2. **Core Feature**: Shielded transactions are the privacy innovation of Midnight blockchain
3. **More Complex**: Better to tackle while Phase 1 knowledge is fresh in context
4. **Proof Server**: Already running locally and tested

**User Experience**:
1. **Primary Use Case**: Users choose Midnight for privacy (shielded) not transparency (unshielded)
2. **Product Positioning**: "Privacy-first wallet" is stronger differentiator
3. **Competitive**: Lace wallet (official) emphasizes shielded operations
4. **Market Fit**: Privacy-conscious users are the target audience

**Testing & Development Benefits**:
1. Phase 4A balance viewing helps test Phase 3 (complex ZK transactions)
2. Phase 2 is simpler, doesn't need sophisticated testing infrastructure
3. Can validate Phase 3 correctness with balance viewing (even with mock data)
4. Phase 3 implements core transaction infrastructure that Phase 2 can reuse

---

## Parallel Work Strategy

**Can Work Simultaneously**:
- Phase 4 GraphQL client (Kotlin) ↔ Phase 3 proof integration (Rust FFI)
- Phase 4 balance calculation (Domain logic) ↔ Phase 3 transaction building (Domain logic)
- Different modules, no conflicts

**Sequential Work**:
- Phase 4 UI → Phase 3 testing (need UI to display balances)
- Phase 4 event storage → Phase 4 balance calculation (dependency)

**Suggested Interleaving**:
```
Day 1-2:   Phase 4 GraphQL client
Day 3-4:   Phase 3 proof server integration
Day 5-6:   Phase 4 event storage
Day 7-8:   Phase 3 transaction building
Day 9-10:  Phase 4 balance calculation
Day 11-12: Phase 3 transaction signing
Day 13-14: Phase 4 balance viewing UI
Day 15-16: Phase 3 integration & testing
```

---

## Recommendation ⭐

**Implement Phase 4 basic view + Phase 3 in parallel, Phase 2 later**

**Reasoning**:
1. ✅ Phase 3 is the core feature (shielded privacy)
2. ✅ Phase 4 provides testing infrastructure for Phase 3
3. ✅ Phase 1 shielded keys are ready (JNI/Rust FFI already working)
4. ✅ Can interleave work to avoid blocking
5. ✅ Phase 2 is simpler, can do later with completed Phase 4
6. ✅ Better time efficiency (85% of Phase 4 isn't blocked)

**Next Steps**:
1. Start Phase 4 GraphQL client (core:indexer module)
2. Start Phase 3 proof integration planning
3. Alternate between phases to maintain momentum
4. Build Phase 4 UI after Phase 3 transaction core is done
5. Test Phase 3 with manual verification + mock balances
6. Complete Phase 4 when ledger 7.0.0 releases
7. Implement Phase 2 last (with full Phase 4 support)
