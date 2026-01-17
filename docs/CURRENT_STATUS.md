# Kuira Wallet - Current Status

**Last Updated:** January 15, 2026
**Hours Invested:** 62h / ~120h estimated
**Completion:** 52%

---

## ✅ What We've Completed

### Phase 1: Crypto Foundation (41h)
- ✅ BIP-39 mnemonic generation (24 words)
- ✅ BIP-32 HD key derivation at `m/44'/2400'/account'/role/index`
- ✅ Unshielded addresses (Bech32m encoding)
- ✅ Shielded keys via JNI/Rust FFI
- ✅ 90 unit tests + 24 Android tests
- ✅ **Compatible with Lace wallet** (same mnemonic → same addresses)

**Result:** We can generate addresses! 🎉

### Phase 4A-Full: Full Sync Engine (21h)
- ✅ GraphQL HTTP client (Ktor)
- ✅ Event caching with LRU eviction
- ✅ Blockchain reorg detection
- ✅ Balance calculator from events
- ✅ Thread-safe storage
- ✅ Retry policy with exponential backoff
- ✅ 118 tests passing (100% pass rate)

**Result:** Built optional/advanced "full wallet" infrastructure (over-engineered for mobile)

---

## 🔄 What's Next (7-11 hours to working wallet)

### Phase 4A-Lite: Light Wallet Queries (2-3h)

**Goal:** Query indexer for balances (simple mobile approach)

**Tasks:**
1. Add `getUnshieldedBalance(address)` to IndexerClient
2. Add `getShieldedBalance(coinPublicKey)` to IndexerClient
3. Add `getUtxos(address)` for transaction building later
4. Create balance caching layer (Room database)
5. Auto-refresh every 30 seconds when online

**Implementation:**
```kotlin
// Query indexer
suspend fun getUnshieldedBalance(address: String): Map<String, BigInteger> {
    val query = """
        query GetUnshieldedBalance(${'$'}address: String!) {
          unshieldedBalance(address: ${'$'}address) {
            tokenType
            amount
          }
        }
    """.trimIndent()

    // ... make GraphQL request, parse response
}
```

### Phase 4A-UI: Balance Display (5-8h)

**Goal:** Show balances to user

**Tasks:**
1. Create Balance screen (Jetpack Compose)
2. Display unshielded address & balance
3. Display shielded address & balance
4. Add pull-to-refresh gesture
5. Show "Last updated X min ago"
6. Add loading states and error handling
7. Add copy address button

**UI Mockup:**
```
┌─────────────────────────────────┐
│  Kuira Wallet                   │
├─────────────────────────────────┤
│  Unshielded Balance             │
│  1,234.56 DUST                  │
│  mn_addr_testnet1...  [Copy]    │
├─────────────────────────────────┤
│  Shielded Balance               │
│  567.89 DUST                    │
│  mn_shield-cpk_...    [Copy]    │
├─────────────────────────────────┤
│  Last updated 2 min ago         │
└─────────────────────────────────┘
```

---

## 🎯 After Balance Viewer Works

### Phase 3: Shielded Transactions (20-25h)

**Goal:** Send private ZK transactions

**Why this first?**
- Core Midnight feature (privacy-first blockchain)
- Shielded keys already working from Phase 1
- More complex than unshielded, do while crypto knowledge is fresh

**Deliverables:**
- Shielded UTXO tracking
- ZK proof generation (via proof server)
- Shielded transaction builder
- Transaction signing & submission
- Convert: shielded ↔ unshielded

### Phase 2: Unshielded Transactions (15-20h)

**Goal:** Send transparent tokens

**Why after Phase 3?**
- Simpler than shielded
- Can reuse transaction infrastructure from Phase 3

---

## 📋 Future/Optional Features

### Phase 4B: Real-Time Sync with WASM (25-35h)

**Status:** Optional - Not needed for mobile wallet

**What it adds:**
- WebSocket subscriptions for real-time updates
- WASM event deserialization (typed events)
- TLS certificate pinning

**Use cases:**
- Privacy mode (don't query indexer)
- Desktop app
- Advanced users wanting full node experience

**Decision:** Build this later if needed for privacy mode or desktop app.

---

## 🎉 Demo Milestone

**After Phase 4A-Lite + Phase 4A-UI (7-11 hours):**

We'll have a **working wallet** that can:
- ✅ Generate 24-word mnemonic
- ✅ Derive addresses (compatible with Lace)
- ✅ Show unshielded balance
- ✅ Show shielded balance
- ✅ Works offline (cached balances)
- ✅ Auto-refresh when online
- ❌ Can't send transactions yet (Phase 3)

This is enough for a **live demo** of the wallet!

---

## Key Decisions Made

1. **Light Wallet First:** Query indexer for balances (fast, simple)
2. **Full Wallet Optional:** Keep Phase 4A-Full for future privacy mode
3. **Phase 4B Deferred:** WebSocket/WASM not needed for mobile
4. **Shielded Before Unshielded:** Privacy is core Midnight feature
5. **Test-Driven:** 118 tests already passing, will add more

---

## Timeline to Demo

```
Week 1 (7-11h):
├─ Phase 4A-Lite (2-3h)
│  └─ Light wallet balance queries
└─ Phase 4A-UI (5-8h)
   └─ Balance display screen

🎉 DEMO: Working balance viewer!

Week 2-3 (20-25h):
└─ Phase 3: Shielded Transactions
   └─ Can send private ZK transactions

Week 4 (15-20h):
└─ Phase 2: Unshielded Transactions
   └─ Can send transparent tokens

🎉 DEMO: Full wallet with send/receive!
```

---

## Next Action

**Start Phase 4A-Lite** - Add light wallet balance queries to IndexerClient.

Ready to begin?
