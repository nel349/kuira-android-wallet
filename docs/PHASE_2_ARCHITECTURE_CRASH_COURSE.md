# Phase 2 Architecture Crash Course: Unshielded Transactions with DUST Fees

**Author:** Claude (for Norman)
**Date:** January 26, 2026
**Purpose:** Deep dive into Kuira's transaction architecture - how all components work together to send NIGHT tokens

---

## 🎯 The Big Picture: What Happens When You Send Tokens

```
User taps "Send 100 NIGHT to Bob"
    ↓
[Phase 2F: Send UI]
    ↓ User input validated
[Phase 2B: UTXO Manager] ← Selects which coins to spend
    ↓ Selected UTXOs
[Phase 2C: Transaction Builder] ← Constructs the transaction
    ↓ Unsigned Intent
[Phase 2D-FFI: Signing] ← Signs with Schnorr BIP-340
    ↓ Signed Intent
[Phase 2-DUST: Fee Payment] ← Adds dust fee payment
    ↓ Intent with dust actions
[Phase 2D-FFI: Serialization] ← Converts to SCALE bytes
    ↓ Hex transaction (2000+ bytes)
[Phase 2E: Submission] ← Submits to blockchain node
    ↓ Transaction hash
[Phase 2E: Confirmation] ← Tracks via GraphQL
    ↓
✅ Transaction finalized on blockchain
```

**Key Insight:** Each phase builds on the previous one. Data flows **downward** (user → blockchain) while state updates flow **upward** (blockchain → user).

---

## 📦 Component Overview: The 7 Layers

### Layer 1: Transaction Models (Phase 2A)
**What:** Data structures representing transactions
**Why:** Type-safe representation of Midnight's transaction format
**Key Files:**
- `Intent.kt` - Container for entire transaction + TTL
- `UnshieldedOffer.kt` - The actual transfer (inputs + outputs + signatures)
- `UtxoSpend.kt` - Reference to coins being spent (inputs)
- `UtxoOutput.kt` - New coins being created (outputs)

### Layer 2: UTXO Manager (Phase 2B)
**What:** Selects which coins to spend
**Why:** You don't spend individual bills - you spend UTXOs
**Key Files:**
- `UtxoManager.kt` - State machine for UTXO lifecycle
- `UnshieldedCoinSelector.kt` - "Smallest first" algorithm

### Layer 3: Transaction Builder (Phase 2C)
**What:** Assembles inputs/outputs into valid transaction
**Why:** Handles change calculation, balancing
**Key Files:**
- `UnshieldedTransactionBuilder.kt` - Constructs Intent from selected UTXOs

### Layer 4: Signing & Serialization (Phase 2D-FFI)
**What:** Cryptographic signing + SCALE encoding
**Why:** Blockchain requires Schnorr signatures + specific binary format
**Key Files:**
- `TransactionSigner.kt` - JNI wrapper for Rust signing
- `TransactionSerializer.kt` - JNI wrapper for SCALE encoding
- Rust FFI: `kuira_crypto_ffi/src/transaction.rs`

### Layer 5: DUST Fee Payment (Phase 2-DUST) ⭐ COMPLEX
**What:** Adds privacy-preserving fee payment to transaction
**Why:** Midnight doesn't have gas fees - uses DUST protocol instead
**Key Files:**
- `DustWalletManager.kt` - Manages dust state
- `DustActionsBuilder.kt` - Creates fee payment actions
- `FeeCalculator.kt` - Calculates fee from transaction size
- Rust FFI: `kuira_crypto_ffi/src/dust.rs`

### Layer 6: Submission (Phase 2E)
**What:** Sends transaction to blockchain node
**Why:** Need to communicate with node via JSON-RPC
**Key Files:**
- `NodeRpcClient.kt` - HTTP client for node RPC
- `TransactionSubmitter.kt` - Orchestrates submission + confirmation

### Layer 7: UI (Phase 2F)
**What:** User interface for sending
**Why:** Users need a form to enter recipient/amount
**Key Files:**
- `SendScreen.kt` - Compose UI
- `SendViewModel.kt` - State management

---

## 🔍 Deep Dive: The DUST Component (Most Complex Part)

### What is DUST?

**Problem:** Midnight blockchain needs transaction fees, but you can't charge fees in NIGHT tokens (would reveal amounts).

**Solution:** DUST protocol - a separate privacy-preserving token for fees.

**Analogy:** Like having a metro card for subway rides. You load it once (via Lace wallet), then use it for many transactions without revealing your main balance.

---

### DUST Architecture: 4 Components

```
┌─────────────────────────────────────────────────────────────┐
│                    DUST Component Architecture               │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. DustWalletManager                                        │
│     ├─ Manages DustLocalState (Merkle tree)                 │
│     ├─ Queries dust events from blockchain                  │
│     ├─ Replays events to build current state                │
│     └─ Provides dust balance                                │
│                                                              │
│  2. FeeCalculator                                            │
│     ├─ Measures transaction size (bytes)                    │
│     ├─ Calculates fee: size * SPECKS_PER_BYTE              │
│     └─ Returns fee in Specks (dust units)                   │
│                                                              │
│  3. DustCoinSelector                                         │
│     ├─ Selects which dust UTXOs to spend                    │
│     ├─ Uses "smallest first" for privacy                    │
│     └─ Handles multi-UTXO fees (if one isn't enough)       │
│                                                              │
│  4. DustActionsBuilder                                       │
│     ├─ Creates DustSpend actions                            │
│     ├─ Calls Rust FFI: state.spend()                       │
│     ├─ Generates zero-knowledge proofs                      │
│     └─ Returns DustActions for transaction                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 🧩 Component Interaction: DUST in Action

### Step-by-Step: Adding DUST to a Transaction

**Starting Point:** You have a signed transaction (Intent) ready to submit.

```kotlin
// Current state:
val signedIntent: Intent = /* ... */
val serializedTxHex: String = "4220 hex characters (2110 bytes)"
```

**Step 1: Calculate Fee** (FeeCalculator.kt)
```kotlin
// Input: Transaction hex (2110 bytes)
val fee = FeeCalculator.calculateFee(serializedTxHex)

// Output: 84,400 Specks (2110 bytes * 40 Specks/byte)
println("Fee required: $fee Specks")
```

**Step 2: Get Dust State** (DustWalletManager.kt)
```kotlin
// This contains your dust balance & Merkle tree
val dustState: DustLocalState = dustWalletManager.getState(address)

// Query balance
val balance = dustState.getBalance() // e.g., 2.9 trillion Specks
println("Dust balance: $balance Specks")
```

**Step 3: Select Dust Coins** (DustCoinSelector.kt)
```kotlin
// Input: Fee needed + available dust UTXOs
val selections = DustCoinSelector.selectCoins(
    fee = 84_400,
    availableCoins = dustState.getUtxos() // List of dust coins
)

// Output: Which dust UTXOs to spend
// Example: [{utxo_index: 0, v_fee: "84400"}]
println("Selected dust UTXO #0 with value 84,400 Specks")
```

**Step 4: Create Dust Actions** (DustActionsBuilder.kt)
```kotlin
// This is where the magic happens - calls Rust FFI
val dustActions = DustActionsBuilder.buildDustActions(
    transactionHex = serializedTxHex,
    ledgerParamsHex = ledgerParams, // Blockchain params
    address = senderAddress,
    seed = userSeed // 32-byte seed for dust keys
)

// Output: DustSpend with zero-knowledge proof
// {
//   old_nullifier: "hex...",
//   new_commitment: "hex...",
//   v_fee: "84400",
//   proof: "proof-preimage"
// }
```

**Step 5: Add to Transaction** (TransactionSerializer.kt)
```kotlin
// Serialize the ENTIRE transaction with dust actions
val finalTxHex = serializer.serializeWithDust(
    inputs = signedIntent.inputs,
    outputs = signedIntent.outputs,
    signatures = signedIntent.signatures,
    dustState = dustState, // Pass the state object
    seed = userSeed,
    dustUtxosJson = """[{"utxo_index": 0, "v_fee": "84400"}]""",
    ttl = signedIntent.ttl
)

// Output: Larger transaction (now ~4220 bytes with dust)
println("Transaction with dust: ${finalTxHex.length / 2} bytes")
```

---

## 🔬 Deep Dive: DustLocalState (The Merkle Tree)

### What is it?

**DustLocalState** is a Merkle tree that tracks your dust coins. It's created and managed by Rust FFI.

```
DustLocalState (Rust struct)
├─ Merkle Tree (tracks all dust operations)
│  ├─ Node 0: Dust received from registration
│  ├─ Node 1: Dust spent on transaction #1
│  ├─ Node 2: Dust spent on transaction #2
│  └─ ... (grows with each operation)
├─ Available UTXOs (coins you can spend)
│  ├─ UTXO 0: 2.9 trillion Specks (index=0)
│  ├─ UTXO 1: 100,000 Specks (index=1)
│  └─ ... (one per dust registration)
└─ Spent UTXOs (coins already used)
   └─ ... (updated after confirmed transactions)
```

### How is it built?

**The Global Dust Tree:** Midnight blockchain has ONE global dust Merkle tree shared by ALL users.

**Your Local State:** You replay YOUR dust events to build your local view of the tree.

```kotlin
// Step 1: Query YOUR dust events from blockchain
val eventsHex: String = indexerClient.queryDustEvents(
    address = "mn_addr_undeployed15jl...",
    maxBlocks = 70_000 // Scan entire blockchain
)

// Output: "6038...f1da" (hex-encoded array of dust events)
// Example: 72 events = 72 operations (registrations + spends)

// Step 2: Replay events into DustLocalState
val dustState: DustLocalState = DustLocalState.replay(
    eventsHex = eventsHex,
    ledgerParamsHex = ledgerParams
)

// Rust FFI does:
// for each event:
//   if event.type == "register":
//     merkle_tree.add_registration(event.amount)
//   if event.type == "spend":
//     merkle_tree.add_spend(event.nullifier, event.commitment)

// Step 3: Now dustState contains your current balance
val balance = dustState.getBalance() // 2.9 trillion Specks
```

---

## 🎨 Data Flow Diagram: Complete Transaction

```
┌─────────────────────────────────────────────────────────────────┐
│                         USER INPUT                               │
│  "Send 100 NIGHT to mn_addr_...abc"                             │
└────────────────────┬────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Phase 2B: UTXO Manager                        │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Available UTXOs:                                          │   │
│  │ - UTXO A: 500 NIGHT (intent_hash: abc...)                │   │
│  │ - UTXO B: 200 NIGHT (intent_hash: def...)                │   │
│  │ - UTXO C: 50 NIGHT  (intent_hash: ghi...)                │   │
│  └──────────────────────────────────────────────────────────┘   │
│                             ↓                                    │
│  Algorithm: Select smallest UTXOs that sum ≥ 100 NIGHT          │
│  Selected: UTXO C (50) + UTXO B (200) = 250 NIGHT              │
└────────────────────┬────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────┐
│                  Phase 2C: Transaction Builder                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Inputs (UTXOs to spend):                                  │   │
│  │ 1. UTXO C: 50 NIGHT                                       │   │
│  │ 2. UTXO B: 200 NIGHT                                      │   │
│  │ Total: 250 NIGHT                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Outputs (new UTXOs to create):                            │   │
│  │ 1. Bob receives:   100 NIGHT (to mn_addr_...abc)         │   │
│  │ 2. Change to you:  150 NIGHT (to mn_addr_...xyz)         │   │
│  │ Total: 250 NIGHT ✓ (balanced!)                           │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Result: UnshieldedOffer                                   │   │
│  │ {                                                          │   │
│  │   inputs: [UTXO C, UTXO B],                               │   │
│  │   outputs: [Bob: 100, You: 150],                          │   │
│  │   signatures: []  // Not signed yet                       │   │
│  │ }                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────┬────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────┐
│                  Phase 2D-FFI: Signing                           │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ For each input, generate signature:                       │   │
│  │                                                            │   │
│  │ Input 1 (UTXO C):                                         │   │
│  │ - Private key: Derive from seed at m/44'/2400'/0'/0/2    │   │
│  │ - Signing message: Hash of Intent                         │   │
│  │ - Signature: Schnorr BIP-340 (64 bytes)                  │   │
│  │                                                            │   │
│  │ Input 2 (UTXO B):                                         │   │
│  │ - Private key: Derive from seed at m/44'/2400'/0'/0/1    │   │
│  │ - Signing message: Same hash                              │   │
│  │ - Signature: Schnorr BIP-340 (64 bytes)                  │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Result: Signed Intent                                     │   │
│  │ {                                                          │   │
│  │   guaranteedUnshieldedOffer: {                            │   │
│  │     inputs: [UTXO C, UTXO B],                             │   │
│  │     outputs: [Bob: 100, You: 150],                        │   │
│  │     signatures: [sig1, sig2]  // ✓ NOW SIGNED            │   │
│  │   },                                                       │   │
│  │   ttl: 1737927600000  // 30 min from now                 │   │
│  │ }                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────┬────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────┐
│              Phase 2D-FFI: Initial Serialization                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Convert Intent → SCALE bytes (WITHOUT dust yet)           │   │
│  │                                                            │   │
│  │ Output: "4d4e..." (4220 hex chars = 2110 bytes)          │   │
│  │                                                            │   │
│  │ This is used to calculate fee ↓                          │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────┬────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────┐
│              Phase 2-DUST: Fee Payment (THE COMPLEX PART)        │
│                                                                   │
│  STEP 1: Calculate Fee                                          │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Transaction size: 2110 bytes                              │   │
│  │ Fee rate: 40 Specks/byte                                  │   │
│  │ Fee required: 2110 × 40 = 84,400 Specks                  │   │
│  └──────────────────────────────────────────────────────────┘   │
│                             ↓                                    │
│  STEP 2: Query Dust Events from Blockchain                      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ IndexerClient.queryDustEvents(address)                    │   │
│  │ → Scans 70,000 blocks via GraphQL                         │   │
│  │ → Finds 72 dust events for this address                   │   │
│  │ → Returns hex: "6038...f1da" (18,432 hex chars)          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                             ↓                                    │
│  STEP 3: Replay Events into DustLocalState                      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Rust FFI: DustLocalState.replay(eventsHex)                │   │
│  │                                                            │   │
│  │ Builds Merkle tree with 72 nodes:                         │   │
│  │ - Node 0: REGISTER 2.9T Specks                            │   │
│  │ - Node 1: SPEND 1000 Specks                               │   │
│  │ - ...                                                      │   │
│  │                                                            │   │
│  │ Result: Balance = 2.9 trillion Specks available           │   │
│  └──────────────────────────────────────────────────────────┘   │
│                             ↓                                    │
│  STEP 4: Select Dust Coins                                      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ DustCoinSelector.selectCoins(fee=84400)                   │   │
│  │                                                            │   │
│  │ Available dust UTXOs:                                      │   │
│  │ - UTXO 0: 2.9 trillion Specks                             │   │
│  │                                                            │   │
│  │ Selected: UTXO 0 (has enough to cover fee)                │   │
│  │                                                            │   │
│  │ Output: [{utxo_index: 0, v_fee: "84400"}]                │   │
│  └──────────────────────────────────────────────────────────┘   │
│                             ↓                                    │
│  STEP 5: Create DustSpend Action                                │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Rust FFI: state.spend(utxo_index=0, v_fee=84400)         │   │
│  │                                                            │   │
│  │ Generates:                                                 │   │
│  │ - old_nullifier: Hash of old dust coin                    │   │
│  │ - new_commitment: New dust coin (remaining balance)       │   │
│  │ - proof: Zero-knowledge proof (privacy!)                  │   │
│  │                                                            │   │
│  │ Result: DustSpend                                          │   │
│  │ {                                                          │   │
│  │   old_nullifier: "c8a3...",                               │   │
│  │   new_commitment: "7f2d...",                              │   │
│  │   v_fee: "84400",                                         │   │
│  │   proof: "proof-preimage..."                              │   │
│  │ }                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────┬────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────┐
│          Phase 2D-FFI: Final Serialization (with dust)           │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Now serialize the COMPLETE transaction:                   │   │
│  │                                                            │   │
│  │ Input:                                                     │   │
│  │ - Signed Intent (inputs, outputs, signatures)             │   │
│  │ - DustSpend action                                        │   │
│  │                                                            │   │
│  │ Rust FFI: serialize_transaction_with_dust()               │   │
│  │                                                            │   │
│  │ Output: "4d4e..." (8436 hex chars = 4218 bytes)          │   │
│  │         ↑ LARGER (now includes dust proof)                │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────┬────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────┐
│                  Phase 2E: Submission to Node                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ STEP 1: Wrap in Substrate Extrinsic                       │   │
│  │                                                            │   │
│  │ Midnight transaction must be wrapped:                     │   │
│  │ [compact_length][version=04][call=05][mystery=00]        │   │
│  │ [compact_tx_length][midnight_tx_hex]                     │   │
│  │                                                            │   │
│  │ Example:                                                   │   │
│  │ f908 04 05 00 e508 [4218 bytes of midnight tx]           │   │
│  │  ↑    ↑  ↑  ↑   ↑                                        │   │
│  │  │    │  │  │   └─ Compact(4218)                         │   │
│  │  │    │  │  └───── Mystery byte                          │   │
│  │  │    │  └──────── Call variant (Midnight::sendTx)       │   │
│  │  │    └─────────── Version 4 (unsigned)                  │   │
│  │  └──────────────── Compact(4224) = total length          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                             ↓                                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ STEP 2: Submit via JSON-RPC                               │   │
│  │                                                            │   │
│  │ POST to http://10.0.2.2:9944                              │   │
│  │ {                                                          │   │
│  │   "jsonrpc": "2.0",                                       │   │
│  │   "id": 1,                                                │   │
│  │   "method": "author_submitExtrinsic",                     │   │
│  │   "params": ["0xf908..."]                                 │   │
│  │ }                                                          │   │
│  │                                                            │   │
│  │ Node validates:                                            │   │
│  │ ✓ Signatures correct                                      │   │
│  │ ✓ UTXOs exist and unspent                                 │   │
│  │ ✓ Dust proof valid                                        │   │
│  │ ✓ Format correct                                          │   │
│  │                                                            │   │
│  │ Response:                                                  │   │
│  │ {                                                          │   │
│  │   "jsonrpc": "2.0",                                       │   │
│  │   "id": 1,                                                │   │
│  │   "result": "0xa3b2c1..."  // Transaction hash           │   │
│  │ }                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────┬────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────┐
│                Phase 2E: Confirmation Tracking                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Subscribe to IndexerClient:                                │   │
│  │                                                            │   │
│  │ indexerClient.subscribeToUnshieldedTransactions(address)  │   │
│  │   .collect { update ->                                    │   │
│  │     if (update.transaction.hash == txHash) {              │   │
│  │       // Transaction confirmed!                           │   │
│  │       updateUI(Status.Finalized)                          │   │
│  │     }                                                      │   │
│  │   }                                                        │   │
│  │                                                            │   │
│  │ Typical timeline:                                          │   │
│  │ - t=0s:  Transaction submitted                            │   │
│  │ - t=2s:  In mempool                                       │   │
│  │ - t=6s:  Included in block                                │   │
│  │ - t=12s: Finalized (6 block confirmations)               │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────┬────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────┐
│                        ✅ SUCCESS                                │
│                                                                   │
│  - Bob receives 100 NIGHT                                        │
│  - You have 150 NIGHT change                                     │
│  - Fee of 84,400 Specks deducted from dust balance              │
│  - Transaction visible on blockchain explorer                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📂 File Responsibilities: Who Does What?

### Core Models (`core/ledger/model/`)

| File | Purpose | Key Methods | Used By |
|------|---------|-------------|---------|
| `Intent.kt` | Transaction container with TTL | `validate()`, `addSegment()` | All phases |
| `UnshieldedOffer.kt` | The actual transfer | `validate()`, `isSigned()` | Builder, Signer |
| `UtxoSpend.kt` | Input UTXO reference | `fromIndexerUtxo()` | UTXO Manager |
| `UtxoOutput.kt` | Output specification | `validate()` | Builder |

### UTXO Management (`core/ledger/utxo/`)

| File | Purpose | Key Methods | Used By |
|------|---------|-------------|---------|
| `UtxoManager.kt` | State machine for UTXOs | `selectUtxos()`, `releaseUtxos()` | Transaction Builder |
| `UnshieldedCoinSelector.kt` | Selection algorithm | `selectCoins()` | UTXO Manager |
| `UtxoDatabase.kt` | Persistence (Room) | `insertUtxo()`, `markSpent()` | UTXO Manager |

### Transaction Building (`core/ledger/builder/`)

| File | Purpose | Key Methods | Used By |
|------|---------|-------------|---------|
| `UnshieldedTransactionBuilder.kt` | Constructs Intent | `buildTransaction()` | Send UI |

### Signing & Serialization (`core/ledger/signer/`, `core/ledger/api/`)

| File | Purpose | Key Methods | Used By |
|------|---------|-------------|---------|
| `TransactionSigner.kt` | JNI wrapper for signing | `signInput()`, `signData()` | Transaction Builder |
| `TransactionSerializer.kt` | JNI wrapper for SCALE | `serialize()`, `serializeWithDust()` | Transaction Submitter |

### DUST Components (`core/ledger/fee/`)

| File | Purpose | Key Methods | Used By |
|------|---------|-------------|---------|
| `DustWalletManager.kt` | Manages dust state | `getState()`, `updateState()` | Dust Actions Builder |
| `FeeCalculator.kt` | Fee calculation | `calculateFee()` | Dust Actions Builder |
| `DustCoinSelector.kt` | Dust UTXO selection | `selectCoins()` | Dust Actions Builder |
| `DustActionsBuilder.kt` | Creates DustSpend | `buildDustActions()` | Transaction Submitter |
| `DustSpendCreator.kt` | JNI wrapper | `createDustSpend()` | Dust Actions Builder |

### Submission (`core/ledger/api/`)

| File | Purpose | Key Methods | Used By |
|------|---------|-------------|---------|
| `NodeRpcClient.kt` | Interface for RPC | `submitTransaction()` | Transaction Submitter |
| `NodeRpcClientImpl.kt` | Ktor implementation | `submitTransaction()`, `wrapInExtrinsic()` | DI container |
| `TransactionSubmitter.kt` | Orchestrator | `submitAndWait()`, `submitWithFees()` | Send UI |
| `NodeRpcException.kt` | Error types | N/A (sealed class) | Transaction Submitter |

### Rust FFI (`core/crypto-ffi/src/`)

| File | Purpose | JNI Methods | Called From |
|------|---------|-------------|-------------|
| `transaction.rs` | Signing & serialization | `nativeSignInput()`, `nativeSerializeTransaction()` | TransactionSigner, TransactionSerializer |
| `dust.rs` | Dust operations | `nativeDustReplay()`, `nativeCreateDustSpend()` | DustWalletManager, DustSpendCreator |
| `bip32.rs` | Key derivation | `nativeDerivePrivateKey()` | HDWallet |

---

## 🔄 State Management: Where State Lives

### 1. UTXO State (Database)
**Location:** `UtxoDatabase.kt` (Room SQLite)
**Schema:**
```kotlin
@Entity(tableName = "unshielded_utxos")
data class UnshieldedUtxoEntity(
    @PrimaryKey val id: String,      // "intent_hash:output_no"
    val intentHash: String,
    val outputNo: Int,
    val value: String,                // BigInteger as String
    val owner: String,                // Bech32m address
    val tokenType: String,
    val state: UtxoState,             // AVAILABLE, PENDING, SPENT
    val createdAt: Long,
    val spentAt: Long?
)

enum class UtxoState {
    AVAILABLE,  // Can be spent
    PENDING,    // Locked for pending transaction
    SPENT       // Already consumed
}
```

**State Transitions:**
```
AVAILABLE → PENDING (selectUtxos)
PENDING → SPENT (onTransactionConfirmed)
PENDING → AVAILABLE (onTransactionFailed)
```

### 2. Dust State (In-Memory)
**Location:** `DustWalletManager.kt` (caches Rust pointer)
**Storage:** Rust heap (accessed via JNI pointer)

```kotlin
class DustWalletManager {
    // Cache: Address → DustLocalState pointer
    private val stateCache = mutableMapOf<String, DustLocalState>()

    fun getState(address: String): DustLocalState {
        return stateCache.getOrPut(address) {
            // Query events from blockchain
            val eventsHex = indexerClient.queryDustEvents(address)

            // Replay into Rust state
            DustLocalState.replay(eventsHex, ledgerParams)
        }
    }
}
```

**Why In-Memory?** Dust state is derived from blockchain events. We rebuild it on demand.

### 3. Transaction State (ViewModel)
**Location:** `SendViewModel.kt` (or equivalent)
**Lifecycle:** Lives while Send screen is active

```kotlin
sealed class SendUiState {
    object Idle : SendUiState()
    data class Building(val progress: Int) : SendUiState()
    data class Signing(val inputIndex: Int) : SendUiState()
    data class AddingFees(val feeAmount: Long) : SendUiState()
    data class Submitting(val txHash: String?) : SendUiState()
    data class Confirming(val txHash: String, val blockHeight: Long?) : SendUiState()
    data class Success(val txHash: String, val blockHeight: Long) : SendUiState()
    data class Error(val message: String) : SendUiState()
}
```

---

## ⚡ Performance Characteristics

### Hot Path (Transaction Creation)
**Goal:** < 500ms from "Send" tap to submission

| Phase | Time | Bottleneck |
|-------|------|------------|
| 2B: Select UTXOs | ~10ms | Database query |
| 2C: Build transaction | ~5ms | Pure computation |
| 2D: Sign (per input) | ~50ms | Rust FFI (Schnorr) |
| 2-DUST: Query events | **3-5 min** | **GraphQL scan** |
| 2-DUST: Replay | ~100ms | Rust Merkle tree |
| 2-DUST: Create spend | ~50ms | Zero-knowledge proof |
| 2D: Final serialize | ~30ms | Rust SCALE encoding |
| 2E: Submit | ~200ms | Network roundtrip |
| **Total (first tx)** | **3-5 min** | Dust event query |
| **Total (cached)** | **~500ms** | Good! |

### Optimization: Cache Dust Events
```kotlin
// First transaction: 3-5 minutes (scan 70k blocks)
val eventsHex = indexerClient.queryDustEvents(address)
File("dust_events_cache.hex").writeText(eventsHex)

// Subsequent transactions: < 1 second (read cache)
val eventsHex = File("dust_events_cache.hex").readText()
```

**Cache Invalidation:** When new dust events detected (e.g., another app spent dust).

---

## 🧪 Testing Strategy

### Unit Tests
**Scope:** Individual components in isolation
**Location:** `src/test/kotlin/`

```kotlin
// Example: Test UTXO selection algorithm
@Test
fun `selectCoins uses smallest-first algorithm`() {
    val coins = listOf(
        mockUtxo(value = 100),
        mockUtxo(value = 50),
        mockUtxo(value = 200)
    )

    val selected = coinSelector.selectCoins(
        targetAmount = 120,
        availableCoins = coins
    )

    // Should select: 50 + 100 = 150 (smallest first)
    assertEquals(2, selected.size)
    assertEquals(50, selected[0].value)
    assertEquals(100, selected[1].value)
}
```

### Integration Tests
**Scope:** Multiple components working together
**Location:** `src/androidTest/kotlin/`

```kotlin
// Example: End-to-end signing test
@Test
fun `sign and serialize transaction produces valid SCALE`() {
    // Build transaction
    val intent = buildTestIntent()

    // Sign
    val signedIntent = signer.signIntent(intent, privateKey)

    // Serialize
    val scaleHex = serializer.serialize(signedIntent)

    // Verify format
    assertTrue(scaleHex.matches(Regex("^[0-9a-f]+$")))
    assertTrue(scaleHex.length > 1000) // Reasonable size
}
```

### E2E Tests (Requires Real Node)
**Scope:** Full flow against real blockchain
**Location:** `src/androidTest/kotlin/com/midnight/kuira/core/ledger/e2e/`

```kotlin
@Test
fun `submit real transaction to testnet`() {
    // This test requires:
    // - Midnight node running at localhost:9944
    // - Test account with UTXOs and dust
    // - Network connectivity

    val result = submitter.submitAndWait(
        signedIntent = realIntent,
        fromAddress = testAddress
    )

    assertTrue(result is SubmissionResult.Success)
}
```

---

## 🎓 Key Learnings: What Makes This Complex?

### 1. **DUST is NOT a Simple Fee**
Unlike Ethereum gas (just subtract ETH), DUST requires:
- Merkle tree replay (global state)
- Zero-knowledge proofs (privacy)
- Separate UTXO selection
- Coordination with main transaction

**Analogy:** Gas is like paying cash. DUST is like swiping a metro card that uses cryptography to hide your balance.

### 2. **Two-Phase Serialization**
```
Serialize WITHOUT dust (to calculate size)
    ↓
Calculate fee from size
    ↓
Add dust actions
    ↓
Serialize WITH dust (final transaction)
```

**Why?** You can't know the fee until you know the size, but adding dust changes the size!

**Solution:** Midnight uses deterministic fee (doesn't include dust proof size in calculation).

### 3. **State is Distributed**
- UTXO state: Local database (Room)
- Dust state: Blockchain (query on demand)
- Wallet state: BIP-32 derivation (seed-based)

**Challenge:** Keeping all three in sync after transaction submission.

### 4. **JNI Complexity**
Rust FFI adds overhead:
- JSON serialization (Kotlin → JSON → Rust)
- Pointer management (lifetimes, memory leaks)
- Error propagation (Rust Result → Kotlin Exception)
- Testing difficulty (can't mock native methods)

**Benefit:** Reuse battle-tested midnight-ledger library (same as TypeScript SDK).

---

## 🚀 Next Steps: Phase 2F (Send UI)

Now that you understand the architecture, Phase 2F will:

1. **Create Compose UI** (`SendScreen.kt`)
   - Amount input field
   - Address input field
   - Fee preview
   - Send button

2. **State Management** (`SendViewModel.kt`)
   - Connect to all Phase 2 components
   - Handle loading states
   - Error handling
   - Success confirmation

3. **Address Validation** (`AddressValidator.kt`)
   - Decode Bech32m
   - Validate network prefix
   - Validate data length

**Estimated Time:** 3-4 hours

---

## 📚 Further Reading

**Midnight Documentation:**
- DUST Protocol: `midnight-libraries/docs/dust-protocol.md` (if exists)
- Transaction Format: `midnight-libraries/midnight-ledger/docs/`

**Internal Documentation:**
- Phase 1 Plan: `/docs/PHASE_1_PLAN.md` (Crypto primitives)
- Phase 2 Plan: `/docs/PHASE_2_PLAN.md` (This implementation)
- Midnight Libraries Mapping: `/docs/MIDNIGHT_LIBRARIES_MAPPING.md`

**Code References:**
- TypeScript SDK: `~/Development/midnight/midnight-libraries/midnight-wallet/`
- Our Implementation: `projects/kuira-android-wallet/core/ledger/`

---

## 🎯 Summary: The 7 Key Components

1. **Models** (Intent, UnshieldedOffer, UtxoSpend, UtxoOutput)
   → Define transaction structure

2. **UTXO Manager** (Coin selection, state tracking)
   → Choose which coins to spend

3. **Transaction Builder** (Balance, change calculation)
   → Construct valid transaction

4. **Signing** (Schnorr BIP-340 via Rust FFI)
   → Cryptographic authorization

5. **DUST** (Query, replay, fee calculation, spend creation)
   → Privacy-preserving fee payment

6. **Serialization** (SCALE encoding via Rust FFI)
   → Convert to blockchain format

7. **Submission** (RPC client, confirmation tracking)
   → Send to blockchain + verify

**The Flow:**
```
User Input → Select → Build → Sign → Add Fees → Serialize → Submit → Confirm
```

Each component is independent and testable, but they work together to create a complete transaction system.

---

**END OF CRASH COURSE**

Questions? Ask about:
- Specific component details
- DUST protocol deeper dive
- Testing strategies
- Performance optimization
- Phase 2F implementation
