# Code Review Findings - Phase 4A-Lite INCORRECT IMPLEMENTATION

## CRITICAL ISSUES FOUND

### Issue 1: Invented Non-Existent APIs ❌

**What I Implemented:**
```kotlin
override suspend fun getUnshieldedBalance(address: String): Map<String, String>
override suspend fun getShieldedBalance(coinPublicKey: String): Map<String, String>
```

**Reality:** These queries DO NOT EXIST in Midnight Indexer GraphQL API!

### Issue 2: Wrong Default URL ❌

**My Implementation:**
```kotlin
baseUrl: String = "https://indexer.midnight.network/api/v3"
```

**Correct URL:**
- Testnet: `https://indexer.testnet-02.midnight.network/api/v3`
- Endpoint: `/graphql` (full path: `/api/v3/graphql`)
- WebSocket: `wss://indexer.testnet-02.midnight.network/api/v3/graphql/ws`

### Issue 3: Misunderstood Architecture ❌

**What I Thought:**
- Indexer has direct balance query APIs
- Light wallet just queries balances when needed

**Reality:**
- No direct balance queries exist
- Must subscribe to transactions via WebSocket
- Must build local UTXO set
- Must calculate balances locally from UTXOs

---

## How Midnight Actually Works

### Actual GraphQL Schema (Query Type)

```graphql
type Query {
  # Find a block
  block(offset: BlockOffset): Block

  # Find transactions by hash/identifier (NOT by address!)
  transactions(offset: TransactionOffset!): [Transaction!]!

  # Find contract action
  contractAction(address: HexEncoded!, offset: ContractActionOffset): ContractAction

  # Get DUST generation status
  dustGenerationStatus(cardanoStakeKeys: [HexEncoded!]!): [DustGenerationStatus!]!
}
```

**No `unshieldedBalance` or `shieldedBalance` queries!**

### Actual Subscription Type

```graphql
type Subscription {
  # Subscribe to blocks
  blocks(offset: BlockOffset): Block!

  # Subscribe to unshielded transactions for an address
  unshieldedTransactions(
    address: UnshieldedAddress!,
    transactionId: Int
  ): UnshieldedTransactionsEvent!

  # Subscribe to shielded transactions (requires session)
  shieldedTransactions(
    sessionId: HexEncoded!,
    index: Int
  ): ShieldedTransactionsEvent!

  # Subscribe to zswap events
  zswapLedgerEvents(id: Int): ZswapLedgerEvent!
}
```

### How Lace Wallet Gets Balances

**File:** `midnight-wallet/packages/unshielded-wallet/src/v1/CoinsAndBalances.ts`

```typescript
// Balances are calculated from local UTXO set
const calculateBalances = (utxos: readonly UtxoWithMeta[]): Balances =>
  utxos.reduce(
    (acc: Balances, { utxo }) => ({
      ...acc,
      [utxo.type]: acc[utxo.type] === undefined ? utxo.value : acc[utxo.type] + utxo.value,
    }),
    {},
  );

const getAvailableBalances = (state: CoreWallet): Balances => {
  const availableCoins = getAvailableCoins(state); // Get UTXOs
  return calculateBalances(availableCoins);
};
```

**Process:**
1. Subscribe to `unshieldedTransactions(address)`
2. Receive `UnshieldedTransactionsEvent` containing:
   - `createdUtxos: [UnshieldedUtxo!]!`
   - `spentUtxos: [UnshieldedUtxo!]!`
3. Update local UTXO set (add created, remove spent)
4. Calculate balance = sum of unspent UTXOs

---

## What This Means For Our Implementation

### Current Status: 237 Tests Passing ✅ BUT...

All tests are passing because they test:
- Mock HTTP responses with fake data
- Parsing logic for queries that don't exist
- Cache logic for data we can't actually fetch

**The implementation is USELESS - it will never work with real indexer!**

### Required Changes

We have THREE options:

#### Option 1: Full Wallet (WebSocket Subscriptions) - COMPLEX

**What:** Implement Phase 4A-Full + Phase 4B WebSocket subscriptions

**Implementation:**
1. WebSocket client with `graphql-ws` protocol
2. Subscribe to `unshieldedTransactions(address)`
3. Subscribe to `shieldedTransactions(sessionId)` (requires connect/disconnect mutations)
4. Build local UTXO database
5. Calculate balances from UTXOs
6. Real-time updates via subscriptions

**Pros:**
- Full-featured wallet
- Real-time balance updates
- Matches Lace architecture

**Cons:**
- Requires Phase 4B WebSocket implementation (15-20 hours)
- Requires local UTXO database (already have from Phase 4A-Full)
- Battery drain from maintaining WebSocket connection
- More complex for mobile

**Status:** Phase 4A-Full is complete (118 tests), but WebSocket subscriptions not implemented

#### Option 2: Hybrid Approach (Query + Local Calculation) - MODERATE

**What:** Query historical transactions once, calculate balances locally

**Implementation:**
1. Use `transactions` query to get transaction history (need to know transaction IDs)
2. Parse `unshieldedCreatedOutputs` and `unshieldedSpentOutputs`
3. Build local UTXO set
4. Calculate balances
5. Cache results

**Problem:**
- `transactions(offset: TransactionOffset!)` requires transaction hash or identifier
- We don't know which transactions are ours without subscribing first!
- Chicken-and-egg problem

**This won't work without subscriptions!**

#### Option 3: Server-Side API (Custom Endpoint) - SIMPLEST

**What:** Create custom backend service that maintains UTXO sets

**Implementation:**
1. Backend subscribes to indexer
2. Backend maintains UTXO database per address
3. Backend exposes REST API: `GET /balance/{address}`
4. Mobile app queries our API

**Pros:**
- Simple mobile implementation
- No WebSocket battery drain
- Fast balance queries
- Can add caching layer

**Cons:**
- Requires backend infrastructure
- Centralization (users must trust our server)
- Privacy concern (server knows your addresses)

---

## Recommendation

**For Mobile Light Wallet: Option 1 (Full Wallet)**

**Why:**
- Midnight's architecture requires WebSocket subscriptions
- No way around it without custom backend
- We already have most of Phase 4A-Full done (118 tests)
- Just need to add WebSocket client (Phase 4B)

**What to Do:**
1. ✅ Keep Phase 4A-Full (sync engine, UTXO tracking)
2. ❌ DELETE Phase 4A-Lite (fake balance queries)
3. 🔄 Implement Phase 4B (WebSocket subscriptions)
4. 🔄 Implement balance calculation from UTXOs
5. 🔄 Integration test with live testnet indexer

**Timeline:**
- Delete fake code: 1h
- Implement WebSocket client: 15-20h
- Integration test: 2-3h
- **Total:** 18-24h to working balance viewer

---

## Immediate Actions

1. **Delete fake implementation:**
   - Remove `getUnshieldedBalance()` and `getShieldedBalance()`
   - Remove `BalanceCacheManager` (was caching fake data)
   - Remove fake tests

2. **Fix configuration:**
   - Change default URL to testnet
   - Document correct endpoints

3. **Plan WebSocket implementation:**
   - Use Ktor WebSocket client
   - Implement `graphql-ws` protocol
   - Subscribe to `unshieldedTransactions`
   - Subscribe to `shieldedTransactions` (requires session)

4. **Integration test:**
   - Connect to live testnet indexer
   - Subscribe to known address
   - Verify transaction events
   - Calculate balance
   - Display in UI

---

## Lessons Learned

1. **Always verify API schema FIRST** before implementing
2. **Check official SDK implementation** to understand patterns
3. **Don't assume REST-like queries** exist in GraphQL
4. **Mobile wallets still need full sync** for Midnight blockchain
5. **"Light wallet" doesn't mean "simple queries"** - it means "no full node"

