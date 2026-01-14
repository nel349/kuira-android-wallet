# Midnight Wallet Android - Refined Implementation Plan

**Project Type:** Month 2+ | 80-120 hours | Weeks 5-16
**Status:** ✅ Phase 1 Complete | Ready for Phase 2
**Progress Tracker:** See `PROGRESS.md` for detailed status

---

## Executive Summary

**Scope:** Full-featured Midnight wallet with ZK privacy and DApp support for Android.

**Technical Approach:** Pure Kotlin/JNI implementation - **NO WASM** (avoiding failed WAMR/React Native approaches).

**Critical Finding:** Midnight-libraries are TypeScript/WASM-based with **ZERO native mobile support**. We must port/reimplement core functionality in Kotlin.

**Timeline:** 80-120 hours across 8-12 weeks (extended from original 30-40 hour estimate).

**Why Extended:** Full wallet + DApp connector + porting crypto from TypeScript/Rust + building Substrate RPC client from scratch.

---

## Investigation Results

### Midnight Libraries Analysis
- **Tech Stack:** TypeScript/JavaScript with Rust core compiled to WASM (5-10MB binaries)
- **Mobile Support:** None - no Kotlin/Java bindings, no Android documentation
- **Complete SDK Available:**
  - HD wallets (BIP-32/BIP-44 derivation path: `m/44'/2400'/account'/role/index`)
  - Address derivation (SHA-256 hash → Bech32m format)
  - Transaction signing (Schnorr over secp256k1, BIP-340)
  - Shielded transactions (zswap)
  - Contract interaction

### Failed WASM Integration (MidnightWasmTest Project)
- **WAMR:** 80% complete on iOS, blocked on externref function calling
- **Polygen:** Can't handle externref (heavily used by Midnight WASM)
- **WebView:** Too complex (74KB JS glue injection, performance issues)
- **Critical Blocker:** externref types required for passing SecretKeys between JS and WASM

### Why Pure Kotlin Approach

✅ **Advantages:**
- Clean architecture (no WASM runtime complexity)
- Better performance (native code)
- Full control over implementation
- Mobile optimization (memory, battery)
- Smaller app size (vs 10MB+ WASM bundles)

❌ **Trade-offs:**
- Must reimplement/port core crypto
- Can't use official Midnight SDK directly
- More initial development time
- Must sync with protocol updates

---

## Address Derivation Algorithm (Lace Compatibility) ✅

**CRITICAL:** This exact algorithm ensures wallet compatibility with Lace (official Midnight wallet).

### Complete Derivation Chain:

```
1. Mnemonic (24 words)
   ↓
2. BIP-39 → Seed (512 bits)
   ↓
3. BIP-32 HD Derivation → Private Key
   Path: m/44'/2400'/account'/role/index
   - COIN_TYPE = 2400 (Midnight's coin type)
   - Roles: Night (0,1), Dust (2), Zswap (3), Metadata (4)
   ↓
4. Schnorr/secp256k1 (BIP-340) → Public Key (32 bytes)
   - NOT Ed25519 (common misconception)
   - x-only public key format
   ↓
5. SHA-256(publicKey) → Address (32 bytes)
   - Source: midnight-ledger/base-crypto/src/hash.rs::persistent_hash
   - Simple SHA-256 digest, no additional processing
   ↓
6. Bech32m Encoding → Human-readable Address
   - Prefix: "mn_addr_{network}" or "mn_addr" (mainnet)
   - Example: mn_addr_testnet1qxy...
```

### Implementation Sources:

| Step | Source File | Library/Method |
|------|-------------|----------------|
| BIP-39 | `midnight-wallet/packages/hd/src/MnemonicUtils.ts` | `@scure/bip39` |
| BIP-32 | `midnight-wallet/packages/hd/src/HDWallet.ts:123` | `@scure/bip32` HDKey.derive() |
| Schnorr | `midnight-ledger/base-crypto/src/signatures.rs:17` | `k256::schnorr` (BIP-340) |
| Hash | `midnight-ledger/base-crypto/src/hash.rs:92` | `Sha256::digest()` |
| Bech32m | `midnight-wallet/packages/address-format/src/index.ts:58` | `@scure/base` bech32m.encode() |

### Validation Strategy:

1. **Test Vectors:** Use BIP-32/BIP-340 published test vectors
2. **Cross-Check:** Generate same mnemonic in Lace and our wallet → addresses MUST match
3. **Unit Tests:** Test each step independently with known outputs

**Compatibility Guarantee:** If same mnemonic → same addresses between Lace and Android wallet.

---

## New Findings from Latest midnight-libraries Update (Jan 9, 2026)

### 1. Official DApp Connector API (`midnight-dapp-connector-api`)

**Critical Discovery:** Midnight now has an official DApp Connector API specification!

**API Structure:**
```javascript
window.midnight['com.wallet.id'] = {
  rdns: 'com.wallet.id',
  name: 'Wallet Name',
  icon: 'data:image/...',
  apiVersion: '1.0.0',
  connect: async (networkId) => ConnectedAPI
}
```

**18 Required Methods:**
- getShieldedBalances, getUnshieldedBalances, getDustBalance
- getShieldedAddresses, getUnshieldedAddress, getDustAddress
- getTxHistory (paginated)
- makeTransfer, makeIntent
- balanceUnsealedTransaction, balanceSealedTransaction
- signData, submitTransaction
- getProvingProvider, getConfiguration
- getConnectionStatus, hintUsage

**Why This Matters:**
- We now have exact API specification to implement
- Must be WebView-based (JavaScript bridge injection)
- 18 methods is more than originally planned (increased Phase 5 timeline)
- Permission system required (hintUsage)
- Configuration sharing required (wallet endpoints)

### 2. ZK Parameter Data Provider (`data_provider.rs`)

**New System:** Remote fetching of cryptographic parameters

**Parameters Hosted on AWS S3:**
- Public parameters (bls_midnight_2p0, 2p1, 2p2, 2p3)
- Prover keys
- Verifier keys
- ZKIR representations

**Local Cache:**
- Location: `$MIDNIGHT_PP` / `$XDG_CACHE_HOME/midnight/zk-params` / `$HOME/.cache/midnight/zk-params`
- SHA-256 verification on fetch
- On-demand or synchronous fetch modes

**Why This Matters:**
- Don't bundle ZK parameters in APK (would be ~100MB+)
- Fetch on-demand from Midnight's S3
- Need cache management strategy (Android cache dir)
- First-run experience (download parameters)

### 3. Cost Model (`cost_model.rs`)

**New Abstraction:** Time/cost measurement in picoseconds

**CostDuration Type:**
- Measured in picoseconds (u64)
- Used for fee calculation
- Arithmetic operations (Add, Sub, Mul, Div)
- Human-readable formatting (ps, ns, μs, ms, s)

**Why This Matters:**
- Fee estimation now has formal model
- Must pass CostModel to proof server
- Display costs to user in human-readable format

### 4. WalletFacade Unified Interface (`facade/src/index.ts`)

**New Abstraction:** Single entry point for wallet operations

**WalletFacade Combines:**
- ShieldedWallet (zswap operations)
- UnshieldedWallet (UTXO operations)
- DustWallet (fee payments)

**Key Methods:**
- `state()` - Observable combining all three wallet states
- `balanceTransaction()` - Unified balancing across shielded/unshielded/dust
- `transferTransaction()` - Unified transfers (auto-routing)
- `submitTransaction()` - Single submission point
- `signTransaction()` - Unified signing
- `calculateTransactionFee()` - Unified fee estimation

**Why This Matters:**
- Simplifies wallet implementation (one facade instead of three separate wallets)
- Handles cross-wallet coordination (e.g., shielded → dust fee payment)
- Provides unified sync state (`isSynced` checks all three)
- ProvingRecipe pattern orchestrates proving flow
- Reference implementation for our Kotlin architecture

---

## Configuration Decisions

Based on user requirements:

| Requirement | Decision |
|-------------|----------|
| **Proof Server** | User-hosted (Docker), provide endpoint URL in settings |
| **Networks** | Testnet + Preview support with configurable endpoints |
| **Crypto Libraries** | Zcash's `kotlin-bip39` + bitcoinj-core for Schnorr/secp256k1 (BIP-340) |
| **Timeline** | Whatever it takes to do it right (8-12 weeks realistic) |
| **Settings Panel** | Required for network/endpoint configuration with testnet/preview defaults |

---

## Phased Implementation

### Phase 1: Foundation ✅ COMPLETE
**Timeline:** Weeks 5-6 (20-25 hours) | **Actual:** ~25 hours
**Goal:** Kotlin crypto primitives and key management
**Completed:** January 12, 2026 | **Verification:** ✅ Compatible with Midnight SDK

#### Tasks:
1. **Multi-Module Setup**
   - Create convention plugins (build-logic/)
   - Set up version catalog (libs.versions.toml)
   - Configure all modules (app, feature/*, core/*)
   - Set up Hilt modules

2. **BIP-39 Seed Phrase**
   - **Library:** `cash.z.ecc.android:kotlin-bip39` (Zcash)
   - Generate 24-word mnemonics
   - Checksum validation
   - Seed derivation

3. **BIP-32 HD Key Derivation**
   - **Library:** Included in kotlin-bip39
   - Derivation path: `m/44'/2400'/account'/role/index`
   - Roles: Night (0,1), Dust (2), Zswap (3), Metadata (4)

4. **Schnorr secp256k1 Key Pairs (BIP-340)**
   - **Library:** `bitcoinj-core` or custom implementation
   - **Algorithm:** Schnorr signatures over secp256k1 (NOT Ed25519)
   - Generate from HD seed
   - 32-byte public key (BIP-340 x-only format)

5. **Address Derivation**
   - **Algorithm:** `address = SHA-256(schnorrPublicKey32bytes)`
   - **Source:** `/midnight-ledger/base-crypto/src/hash.rs` (persistent_hash)
   - **Result:** 32-byte address (hex or bytes)

6. **Bech32m Address Formatting**
   - **Port from:** `@midnight-ntwrk/wallet-sdk-address-format`
   - **Prefix:** `mn_addr_{network}` or `mn_addr` (mainnet)
   - **Example:** `mn_addr_testnet1abc...`
   - Encode 32-byte address to Bech32m string
   - Checksum validation

7. **Secure Storage**
   - Android Keystore (hardware-backed)
   - EncryptedSharedPreferences (seed backup)
   - Room + SQLCipher (wallet metadata)

#### Deliverables:
- [ ] `:core:crypto` module complete
- [ ] `:core:domain` entities defined
- [ ] Unit tests (100% crypto coverage with test vectors)
- [ ] Can create/restore wallet, derive addresses

#### Files to Create:
```
core/crypto/
  ├── BIP39SeedGenerator.kt
  ├── HDKeyDerivation.kt
  ├── SchnorrKeyPair.kt (BIP-340 secp256k1)
  ├── AddressDerivation.kt (SHA-256 hash)
  ├── Bech32mFormatter.kt (mn_addr encoding)
  └── SecureKeyStore.kt

core/domain/
  ├── entities/Wallet.kt
  ├── entities/Address.kt
  └── repository/WalletRepository.kt
```

**Blockers:** None

---

### Phase 2: Unshielded Transactions ⏳ NOT STARTED
**Timeline:** Week 7 (15-20 hours)
**Goal:** Send/receive unshielded tokens (no privacy yet)

#### Core Concepts:

**Transaction Structure:**
- Midnight uses "Intents" with numbered segments
- Segment 0 = Guaranteed (must succeed)
- Segment 1+ = Fallible (may fail without invalidating transaction)
- Each intent has TTL (expiration time)
- Offers contain: inputs (UTXOs) + outputs (recipients/change) + signatures

**UTXO State Machine:**
- **Available** → can spend
- **Pending** → selected for transaction, awaiting confirmation
- **Spent** → confirmed on-chain
- Rollback handling: Retracted blocks revert Spent → Available

**Signing & Binding Flow:**
1. Build transaction with unsigned intents
2. Extract signature data from each segment (unique payload per segment)
3. Sign each payload with Schnorr (BIP-340)
4. Add signatures to offers (order matters - must match inputs)
5. Bind transaction (final immutability step, cannot bind until fully signed)

**Balancing Algorithm:**
1. Calculate imbalances: sum(outputs) - sum(inputs) per token type
2. Select coins using largest-first strategy
3. Add change outputs for excess: change = inputs - outputs - fees
4. Mark selected coins as Pending to prevent double-spend
5. Add offers to Segment 0 (guaranteed section)

#### Tasks:
1. **Substrate RPC Client via Polkadot.js**
   - WebSocket-based: `api.tx.midnight.sendMnTransaction(hexTx).send(callback)`
   - Track states: Ready/Future/Broadcast (mempool) → InBlock → Finalized
   - Handle Retracted (block rollback - tx back to mempool)
   - Genesis fetching: `api.rpc.chain.getBlock(genesisHash)`, filter `midnight.sendMnTransaction`
   - Reconnection with exponential backoff

2. **SCALE Codec**
   - Binary serialization for Substrate transactions
   - Must exactly match Rust ledger format
   - Critical for node submission

3. **Intent-Based Transaction Builder**
   - Create Transaction with Intent (Segment 0)
   - Build Offer: selected inputs + recipient outputs + change outputs
   - Set TTL for intent expiration

4. **Multi-Segment Signing**
   - Extract signature data: `intent.signatureData(segment)`
   - Sign with Schnorr key
   - Add to offer: `offer.addSignatures([signature])`
   - Multiple segments need separate signatures

5. **Transaction Binding**
   - Final step after all signatures added
   - `transaction.bind()` makes immutable
   - Cannot bind partial transactions

6. **UTXO State Tracking**
   - Local database: track Available/Pending/Spent per coin
   - Coin selection: filter Available, select largest-first
   - State transitions on: build tx, confirm, fail, rollback
   - Sync with chain state via indexer

7. **Balance Queries**
   - Query via RPC: `state_getStorage()` for balance
   - Parse genesis transactions for initial state

#### Deliverables:
- [ ] `:core:network` module (Substrate RPC)
- [ ] `:core:ledger` module (transaction logic)
- [ ] Can send/receive unshielded tokens
- [ ] Integration tests with testnet

#### Files to Create:
```
core/network/
  ├── SubstrateRpcClient.kt
  ├── PolkadotApi.kt
  └── dto/BlockchainResponses.kt

core/ledger/
  ├── UnshieldedTransaction.kt
  ├── TransactionBuilder.kt
  ├── TransactionSigner.kt
  └── ScaleCodec.kt
```

**Blockers:** Need testnet endpoint from user

---

### Phase 3: Shielded Transactions ⏳ NOT STARTED
**Timeline:** Weeks 8-9 (20-25 hours)
**Goal:** Private transactions with ZK proofs

#### Core Concepts:

**Proving Recipe Pattern (determines how to handle transaction):**
| Type | When | Action |
|------|------|--------|
| `TRANSACTION_TO_PROVE` | New shielded tx, needs proving | Serialize → POST /prove → Deserialize |
| `BALANCE_TRANSACTION_TO_PROVE` | Imbalanced + needs proving | Balance first, THEN prove |
| `NOTHING_TO_PROVE` | Already balanced | Skip proving, just sign & submit |

**Zswap Secret Keys (different key set):**
- Derive from HD seed with role = 3 (Zswap)
- Signing keys use role = 0/1 (Night)
- Zswap keys for: note encryption, nullifier derivation, shielded operations

**Guaranteed vs Fallible Sections:**
- Both sections may need balancing independently
- Guaranteed (Segment 0): Must succeed, covers required outputs
- Fallible (Segment 1+): Allowed to fail, for swaps/conditional operations
- Each section gets separate coin selection + change outputs

**Proof Server Binary Protocol:**
- Content-Type: `application/octet-stream` (no JSON)
- Request: Binary serialized unproven transaction (SCALE codec)
- Response: Binary serialized proven transaction
- Must EXACTLY match Rust ledger serialization format
- Proving takes 10-60 seconds (compute-intensive)

#### Shielded Transaction Flow:

```
1. Create transaction with shielded recipient outputs
2. Calculate imbalances (inputs vs outputs) for both sections
3. IF imbalanced:
   a. Select shielded coins from Available set
   b. Calculate change: inputs - outputs - fees
   c. Add change outputs (back to self)
   d. Mark coins as Pending
4. Determine proving recipe type:
   - If balanced → NOTHING_TO_PROVE
   - If needs balancing → BALANCE_TRANSACTION_TO_PROVE
   - If unproven → TRANSACTION_TO_PROVE
5. IF needs proving:
   a. Serialize transaction to binary (SCALE codec)
   b. POST to /prove endpoint
   c. Wait for proven transaction (retry 3x on 502-504)
   d. Deserialize binary response
6. Sign proven transaction (each segment)
7. Bind transaction
8. Submit to node via RPC
```

#### Tasks:
1. **Proof Server HTTP Client**
   - Binary protocol, not JSON
   - POST /prove: unproven tx → proven tx
   - POST /check: validate before proving (fail fast)
   - Retry strategy: 3 attempts, exponential backoff (2s, 4s, 8s)
   - Handle 502/503/504 (server overload)
   - CostModel parameter required

2. **Proving Recipe Determination**
   - Check transaction imbalances
   - Decide: TRANSACTION_TO_PROVE, BALANCE_TRANSACTION_TO_PROVE, or NOTHING_TO_PROVE
   - Route to appropriate handler

3. **Zswap Key Derivation**
   - From HD seed: `m/44'/2400'/account'/3/index` (role=3)
   - Store securely (separate from signing keys)
   - Use for shielded operations

4. **Shielded Balancing**
   - Balance guaranteed section (Segment 0)
   - Balance fallible section (Segment 1+) if exists
   - Separate coin selection per section
   - Create change outputs for each section

5. **Binary Serialization**
   - Serialize unproven transaction to SCALE format
   - Must match Rust ledger format EXACTLY
   - Deserialize proven transaction from binary response

6. **Integration with Unshielded Flow**
   - Reuse signing + binding from Phase 2
   - Reuse UTXO state management
   - Add shielded coin tracking

#### Deliverables:
- [ ] `:core:prover-client` module
- [ ] Shielded transaction support
- [ ] Can send private transactions

#### Files to Create:
```
core/prover-client/
  ├── ProofServerClient.kt
  ├── dto/ProofRequest.kt
  └── dto/ProofResponse.kt

core/ledger/
  ├── ShieldedTransaction.kt
  ├── NoteConstructor.kt
  └── NullifierDerivation.kt
```

**Blockers:** Need proof server URL from user

---

### Phase 4: Indexer Integration ⏳ NOT STARTED
**Timeline:** Weeks 9-10 (15-20 hours)
**Goal:** Fast state sync (vs slow RPC polling)

#### Core Concepts:

**Why Indexer vs Node RPC:**
- **Indexer:** Fast queries indexed by address, pre-computed aggregations, transaction history
- **Node RPC:** Slower, requires parsing blocks sequentially, good for transaction submission only
- Indexer provides near-instant balance queries vs seconds/minutes via RPC

**GraphQL Protocol (Standard):**
- HTTP for one-time queries (balance, transaction history)
- WebSocket for real-time subscriptions (new transactions, balance updates)
- Use existing Kotlin libraries (Apollo Kotlin)
- Code generation from GraphQL schema for type safety

**Typical Queries:**
- Get balance by address (shielded + unshielded)
- Get transaction history by address (paginated)
- Get UTXO set by address
- Get transaction details by hash

**Real-time Subscriptions:**
- Subscribe to new transactions for specific address
- Subscribe to balance changes
- Long-lived WebSocket connection with 100 retry attempts
- Handle reconnection automatically

#### Tasks:
1. **GraphQL HTTP Client (One-time Queries)**
   - Endpoint: `POST /graphql`
   - Request: GraphQL query document + variables (JSON)
   - Response: JSON result
   - Use Apollo Kotlin for code generation
   - Retry: 3 attempts on 502-504 (server errors)

2. **GraphQL WebSocket Client (Real-time Subscriptions)**
   - Protocol: `graphql-ws` (standard WebSocket subprotocol)
   - Endpoint: `ws://indexer-url/graphql`
   - Subscribe to address-specific events
   - Retry: 100 attempts (connection drops are common)
   - Handle connection lifecycle (connect, subscribe, reconnect)

3. **Query Definitions**
   - Balance by address
   - Transaction history (paginated, filtered by address)
   - UTXO set (available coins for spending)
   - Transaction details (inputs, outputs, status)

4. **Local State Caching**
   - Room database to cache query results
   - Reduce network calls for frequent operations
   - Sync strategy:
     - Initial load from indexer on app start
     - Subscribe to updates via WebSocket
     - Periodic refresh (every 30s) for reliability
   - Handle stale data (timestamp-based invalidation)

5. **Subscription Event Handling**
   - New transaction received → update pending UTXO state
   - Transaction confirmed → move UTXO from Pending to Spent
   - Balance changed → refresh balance display
   - Update Room database with new data

6. **Integration with UTXO State Machine**
   - Indexer provides authoritative chain state
   - Local state (Available/Pending/Spent) syncs with indexer
   - Handle discrepancies: indexer wins over local state
   - Rollback detection: compare block hashes

#### Deliverables:
- [ ] `:core:indexer` module
- [ ] Faster balance updates
- [ ] Transaction history

#### Files to Create:
```
core/indexer/
  ├── IndexerClient.kt
  ├── StateProvider.kt
  └── dto/IndexerResponses.kt

core/database/
  ├── entities/TransactionEntity.kt
  └── dao/TransactionDao.kt
```

**Blockers:** Need indexer endpoint from user

---

### Phase 5: DApp Connector ⏳ NOT STARTED
**Timeline:** Weeks 10-11 (20-25 hours - increased for full API)
**Goal:** Implement Midnight's official DApp Connector API

#### Core Concepts:

**Official API:** `window.midnight[walletId]`
- DApp accesses via: `window.midnight['com.example.wallet'].connect('testnet')`
- Returns ConnectedAPI with 18 methods
- All methods async (Promise-based)
- Compatible with CAIP-372 draft standard

**Critical Methods to Implement:**
1. Balance queries (shielded, unshielded, dust)
2. Address getters (all 3 types)
3. Transaction creation (makeTransfer, makeIntent)
4. Transaction balancing (unsealed, sealed)
5. Transaction submission
6. Data signing
7. Proving provider delegation
8. Configuration sharing (endpoints)
9. Permission management (hintUsage)

**Transaction States:**
- Per-segment execution status: Success | Failure
- Overall: finalized, confirmed, pending, discarded
- Fallible segments allowed to fail

#### Tasks:
1. **WebView JavaScript Bridge**
   - Inject `window.midnight['com.yourwallet.midnight']`
   - InitialAPI: {rdns, name, icon, apiVersion, connect()}
   - connect() returns ConnectedAPI (18 methods)
   - Handle async Promise communication

2. **Implement 18 ConnectedAPI Methods**
   - Reuse Phase 2/3/4 logic for most operations
   - Add permission checking layer
   - Add user approval dialogs

3. **Permission System**
   - hintUsage(methods[]) → show permission dialog
   - Store per-DApp permissions (origin-based)
   - Gate all methods by permissions

4. **Configuration Sharing**
   - Share wallet's node/indexer/prover URIs
   - DApp should use same services (privacy/performance)

5. **Proving Provider Delegation**
   - DApp provides KeyMaterialProvider
   - Wallet proves using its proof server
   - Return ProvingProvider interface

#### Deliverables:
- [ ] `:feature:dapp-connector` module
- [ ] WebView with JavaScript bridge
- [ ] 18 API methods implemented
- [ ] Permission management system
- [ ] Per-DApp permission storage

#### Files to Create:
```
feature/dapp-connector/
  ├── DAppConnectorActivity.kt
  ├── DAppRequest.kt
  ├── ApprovalDialog.kt
  └── ContractSigner.kt

core/contracts/
  ├── ContractCallBuilder.kt
  └── ContractABI.kt
```

**Blockers:** None

---

### Phase 6: UI & Polish ⏳ NOT STARTED
**Timeline:** Weeks 11-12 (10-15 hours)
**Goal:** Complete Compose UI

#### Screens:
1. **Onboarding**
   - Create wallet → seed phrase → verify backup
   - Restore from seed
   - PIN/biometric setup

2. **Wallet Home**
   - Balance (shielded + unshielded)
   - Transaction history (LazyColumn)
   - Send/Receive buttons

3. **Send Transaction**
   - Recipient address input
   - Amount input
   - Shielded/unshielded toggle
   - Fee estimation
   - Confirmation screen

4. **Receive**
   - Address display (text + QR)
   - Copy/share

5. **Settings** ⭐ IMPORTANT
   - **Network Configuration Panel:**
     - Testnet (default endpoints)
     - Preview (default endpoints)
     - Custom (user-provided endpoints)
   - **Endpoints to configure:**
     - Node RPC (WebSocket)
     - Indexer API (HTTP)
     - Proof Server (HTTP)
   - Backup seed phrase (re-auth required)
   - Security settings
   - About/version

#### Deliverables:
- [ ] All feature modules with Compose UI
- [ ] Navigation (Compose Navigation)
- [ ] Material3 design system
- [ ] Settings panel for endpoint configuration

**Blockers:** None

---

## Multi-Module Architecture

```
midnight-wallet/
├── app/                           # Navigation, DI setup
├── feature/
│   ├── onboarding/                # Create/restore wallet
│   ├── wallet/                    # Home (balance, history)
│   ├── send/                      # Send transaction
│   ├── receive/                   # Receive + QR code
│   ├── settings/                  # Settings + network config ⭐
│   └── dapp-connector/            # DApp integration
├── core/
│   ├── crypto/                    # BIP-39, HD keys, Schnorr/secp256k1 (PORT)
│   ├── ledger/                    # Transactions (PORT)
│   ├── network/                   # Substrate RPC (NEW)
│   ├── indexer/                   # Indexer client (NEW)
│   ├── prover-client/             # Proof server API (NEW)
│   ├── database/                  # Room + SQLCipher
│   ├── datastore/                 # EncryptedSharedPreferences
│   ├── domain/                    # Use cases, repos
│   ├── ui/                        # Design system
│   ├── common/                    # Extensions, utils
│   └── testing/                   # Test utilities
└── build-logic/                   # Convention plugins
```

---

## Porting Strategy

### From Midnight Libraries (What to Port)

| Source | Destination | Strategy |
|--------|-------------|----------|
| `@scure/bip39` (TS) | `:core:crypto` | Use Zcash `kotlin-bip39` |
| `@scure/bip32` (TS) | `:core:crypto` | Included in `kotlin-bip39` |
| Schnorr/secp256k1 (BIP-340) | `:core:crypto` | Use `bitcoinj-core` or custom impl |
| SHA-256 (address hash) | `AddressDerivation.kt` | Built-in Java SHA-256 |
| `@midnight-ntwrk/wallet-sdk-address-format` (TS) | `Bech32mFormatter.kt` | Port Bech32m logic |
| Transaction structure (Intents, Segments) | `:core:ledger` | Port structure, signing, binding logic |
| UTXO state management | `:core:domain` | Port available/pending/spent tracking |
| Coin selection algorithm | `:core:ledger` | Port balancing + coin selection |
| `@midnight-ntwrk/ledger` (Rust) | `:core:ledger` | Reverse-engineer + SCALE codec |
| `@midnight-ntwrk/zswap` (Rust) | `:core:ledger` | Port client-side only (proving recipe) |
| `@polkadot/api` (TS) | `:core:network` | Port essential RPC: sendMnTransaction, getBlock |
| GraphQL indexer | `:core:indexer` | Use Apollo Kotlin (standard GraphQL) |

### What NOT to Port (Remote Services)

1. **ZK Proof Generation** - User's proof server (too compute-intensive)
2. **Ledger Runtime** - Stays on-chain
3. **Node Infrastructure** - Use existing Midnight nodes

---

## External Dependencies

### User Must Provide:

| Service | Type | Purpose | Example |
|---------|------|---------|---------|
| **Node RPC** | WebSocket | Blockchain queries | `wss://rpc.testnet.midnight.network` |
| **Indexer** | HTTP | Fast state sync | `https://indexer.testnet.midnight.network` |
| **Proof Server** | HTTP | ZK proof generation | `https://prover.mydomain.com` |

### Network Defaults (in Settings):

**Testnet:**
- Node: `wss://rpc.testnet.midnight.network`
- Indexer: `https://indexer.testnet.midnight.network`
- Proof Server: (user provides)

**Preview:**
- Node: `wss://rpc.preview.midnight.network`
- Indexer: `https://indexer.preview.midnight.network`
- Proof Server: (user provides)

**Custom:**
- All endpoints user-configurable

---

## Risk Assessment

### 🔴 High Risk
1. **Intent/Segment Structure** - Complex multi-segment signing, binding order matters
   - *Mitigation:* Study TypeScript implementation carefully, unit test each step
2. **Transaction Format Mismatch** - Kotlin doesn't match Midnight's binary format
   - *Mitigation:* Extensive testnet validation, compare with TypeScript SDK outputs
3. **SCALE Codec Bugs** - Incorrect serialization breaks proof server
   - *Mitigation:* Test vectors, validate against Rust, binary comparison
4. **Proof Server Binary Protocol** - Must exactly match ledger serialization
   - *Mitigation:* Mock server first, document API contract, retry strategy
5. **UTXO State Synchronization** - Rollbacks, reorgs, concurrent transactions
   - *Mitigation:* Careful state machine, handle retracted blocks, pessimistic locking

### 🟡 Medium Risk
1. **Polkadot.js RPC** - Missing/incorrect methods
   - *Mitigation:* Start minimal, expand as needed
2. **Indexer Changes** - API updates break client
   - *Mitigation:* Version calls, graceful errors

### 🟢 Low Risk
1. **Crypto** - Battle-tested libraries (bitcoinj-core for Schnorr, Zcash for BIP-39/32)
2. **Compose UI** - Already experienced from Weather App
3. **Address Compatibility** - Simple SHA-256 hash, validated against Lace wallet

---

## Testing Strategy

### Phase 1: Crypto (Unit Tests)
- BIP-39: Standard word lists
- BIP-32: Published test vectors (from BIP-32 spec)
- Schnorr/secp256k1: BIP-340 test vectors
- Address derivation: Compare with Lace wallet outputs
- **Goal:** 100% crypto confidence, Lace compatibility

### Phase 2-3: Integration
- Mock blockchain (WireMock)
- Mock proof server
- **Goal:** Fast feedback without network

### Phase 4-6: End-to-End
- Midnight testnet
- Real transactions
- Real proof generation
- **Goal:** Production readiness

---

## Success Criteria

### Phase 1 (Foundation):
- [ ] Create wallet from seed
- [ ] Restore from seed
- [ ] Derive addresses (all roles)
- [ ] Secure key storage

### Phase 2 (Unshielded):
- [ ] Send unshielded tx
- [ ] Receive unshielded tx
- [ ] View balance

### Phase 3 (Shielded):
- [ ] Send shielded tx (via proof server)
- [ ] View shielded balance

### Phase 4 (Indexer):
- [ ] Fast state sync
- [ ] Transaction history

### Phase 5 (DApp):
- [ ] DApp request signature
- [ ] User approve/reject
- [ ] Sign contract calls

### Phase 6 (Polish):
- [ ] Complete Compose UI
- [ ] Network config panel
- [ ] 80%+ test coverage
- [ ] Published on GitHub
- [ ] Demo video

---

## Timeline

### Realistic Estimate: 80-120 hours

**Breakdown:**
- Phase 1: 20-25 hours
- Phase 2: 15-20 hours
- Phase 3: 20-25 hours
- Phase 4: 15-20 hours
- Phase 5: 15-20 hours
- Phase 6: 10-15 hours
- Buffer: +20 hours

**Schedule Options:**
- **Fast:** 8 weeks @ 10-15 hrs/week
- **Balanced:** 12 weeks @ 7-10 hrs/week

**Recommendation:** 12-week timeline for learning curve and debugging buffer.

---

## What We Need from User

### Now (Before Phase 2):
- [ ] Testnet endpoints (node, indexer, proof server)
- [ ] Preview endpoints (node, indexer, proof server)
- [ ] Test tokens (how to acquire)
- [ ] Proof server authentication details (if required)

### Later (Phase 3+):
- [ ] Example transactions to study
- [ ] Any Midnight-specific documentation
- [ ] Contact for technical questions (if available)

---

## Interview Story

> "I built a zero-knowledge cryptocurrency wallet for Midnight Network on Android. The main challenge was that Midnight's SDK is TypeScript/WASM-based with no mobile support - I had to port the entire crypto stack to Kotlin.
>
> I started by analyzing their failed WASM integration attempt which got stuck on externref handling. Instead, I took a pure Kotlin approach, using Zcash's kotlin-bip39 for HD wallet derivation and implementing Schnorr signatures over secp256k1 (BIP-340) for transaction signing. Address derivation was straightforward - just SHA-256 hash of the public key, then Bech32m encoding - but I validated it against Lace wallet to ensure compatibility.
>
> The trickiest part was transaction serialization - I had to reverse-engineer Midnight's Rust ledger (compiled to WASM) and reimplement their SCALE codec in Kotlin. I validated against test vectors and compared behavior with the TypeScript SDK to ensure compatibility.
>
> For zero-knowledge proofs, mobile devices can't generate them (too compute-intensive), so I architected it with a remote proof server. The app sends transaction inputs via HTTP, the server generates the proof, then the app combines it and broadcasts to the blockchain.
>
> The architecture uses Clean Architecture with 11 modules: 6 feature modules (onboarding, wallet, send, receive, settings, dapp-connector) and 5 core modules (crypto, ledger, network, indexer, prover-client). This separation let me test crypto in isolation with known test vectors before integrating with the blockchain.
>
> I achieved 80%+ test coverage: unit tests for all crypto operations, integration tests with mock blockchain, and end-to-end tests on Midnight's testnet. The wallet supports shielded and unshielded transactions, HD key derivation with Midnight-specific roles, and a DApp connector for mobile dapp integration."

**Follow-ups:**
- "How did you handle the SCALE codec?" → Studied Rust implementation, ported encode/decode logic with unit tests
- "Why not use WASM?" → Externref limitations, bundle size, performance - native Kotlin is cleaner
- "Security considerations?" → Android Keystore (hardware-backed), EncryptedSharedPreferences, SQLCipher, ProGuard obfuscation

---

## Progress Tracking

Track phase completion in this file:
- ⏳ NOT STARTED
- 🏗️ IN PROGRESS
- ✅ COMPLETE
- ❌ BLOCKED

**Current Phase:** Phase 1 (Foundation) - ⏳ NOT STARTED
**Next Milestone:** Multi-module setup + BIP-39 implementation
**Blockers:** None - ready to start

---

**Last Updated:** 2026-01-09 (Code review findings embedded into phases)
**Project Start Date:** TBD (after Week 4 completion)
**Expected Completion:** 8-12 weeks from start
