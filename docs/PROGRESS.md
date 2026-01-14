# Kuira Wallet - Project Progress Tracker

**Last Updated:** January 13, 2026
**Overall Status:** Phase 1 In Progress (Shielded Keys)
**Total Estimate:** 80-120 hours across 6 phases
**Time Invested:** ~33 hours (Phase 1)

---

## Quick Status Overview

| Phase | Status | Hours Estimate | Hours Actual | Progress |
|-------|--------|----------------|--------------|----------|
| **Phase 1: Crypto Module** | ⏳ IN PROGRESS | 30-35h | ~33h | 90% |
| ↳ Unshielded (BIP-39/32/Addresses) | ✅ COMPLETE | 20-25h | ~30h | 100% |
| ↳ Shielded Keys (JNI FFI) | ⏳ Phase 2A done | 10-15h | ~3h | 30% |
| **Phase 2: Unshielded Transactions** | ⏳ NOT STARTED | 15-20h | 0h | 0% |
| **Phase 3: Shielded Transactions** | ⏳ NOT STARTED | 20-25h | 0h | 0% |
| **Phase 4: Indexer Integration** | ⏳ NOT STARTED | 10-15h | 0h | 0% |
| **Phase 5: DApp Connector** | ⏳ NOT STARTED | 15-20h | 0h | 0% |
| **Phase 6: UI & Polish** | ⏳ NOT STARTED | 15-20h | 0h | 0% |

**Next Milestone:** Phase 1 - Complete Shielded Keys (Phase 2B: JNI + NDK, 7-11h remaining)

---

## Phase 1: Crypto Module ✅ COMPLETE

**Goal:** Kotlin crypto primitives and key management compatible with Midnight SDK
**Duration:** ~30 hours
**Status:** ✅ ALL KEY TYPES VERIFIED

### Phase 1 Deliverables

- ✅ BIP-39 mnemonic generation (12/15/18/21/24 words)
- ✅ BIP-39 seed derivation (matches `@scure/bip39`)
- ✅ BIP-32 HD key derivation (matches `@scure/bip32`)
- ✅ Midnight role enum (NightExternal, NightInternal, Dust, Zswap, Metadata)
- ✅ HD wallet can derive at ANY role (verified all 3: unshielded/shielded/dust)
- ✅ Unshielded address generation (SHA-256 + Bech32m)
- ✅ 74 tests passing

### NOT in Phase 1 (Future Work)

- ❌ Shielded address generation (needs ZK keys - Phase 3)
- ❌ Schnorr transaction signing (needs BIP-340 - Phase 2)
- ❌ Secure storage (Android Keystore - Phase 2)

### Completed Components

#### ✅ BIP-39 Mnemonic Generation
- **Library:** BitcoinJ (`org.bitcoinj:bitcoinj-core:0.16.3`)
- **Implementation:** `core/crypto/src/main/kotlin/com/midnight/kuira/core/crypto/bip39/`
  - `MnemonicService.kt` - Interface
  - `BitcoinJMnemonicService.kt` - Implementation
- **Features:**
  - 12/15/18/21/24 word mnemonic generation
  - Entropy wiping after generation (security)
  - Passphrase support (max 256 chars)
  - Seed derivation (PBKDF2-HMAC-SHA512, 2048 iterations)
- **Tests:** 52 tests passing
  - Basic: 22 tests (generation, validation, seed derivation)
  - Edge cases: 5 tests (invalid mnemonics, empty passphrases)
  - Security: 15 tests (entropy wiping, memory safety)
  - Midnight compatibility: 12 tests (test vectors)
- **Verification:** ✅ Produces IDENTICAL seeds to Midnight SDK (`@scure/bip39`)

#### ✅ BIP-32 HD Key Derivation
- **Library:** BitcoinJ (DeterministicHierarchy)
- **Implementation:** `core/crypto/src/main/kotlin/com/midnight/kuira/core/crypto/bip32/`
  - `HDWallet.kt` - Main wallet interface
  - `AccountWallet.kt` - Account-level operations
  - `RoleWallet.kt` - Role-level operations
  - `DerivedKey.kt` - Individual key wrapper
  - `MidnightKeyRole.kt` - Midnight role definitions
- **Features:**
  - Derivation path: `m/44'/2400'/account'/role/index`
  - Midnight roles: NightExternal(0), NightInternal(1), Dust(2), Zswap(3), Metadata(4)
  - Hierarchical memory cleanup (wallet → account → role → keys)
  - Use-after-clear protection with `isCleared` flags
  - Seed length validation (16-64 bytes)
- **Tests:** 12 tests passing
  - Path derivation with Midnight roles
  - Memory wiping and hierarchical cleanup
  - Use-after-clear protection
  - Multiple key derivation
- **Verification:** ✅ Produces IDENTICAL private keys to Midnight SDK (`@scure/bip32`)

#### ✅ BIP-340 Public Key Derivation
- **Implementation:** `DerivedKey.kt:28` - `publicKeyBytes` property
- **Features:**
  - Extracts compressed public key (33 bytes with 0x02/0x03 prefix)
  - Compatible with BIP-340 x-only format (strip prefix for 32-byte key)
- **Verification:** ✅ Produces IDENTICAL public keys to Midnight SDK

#### ✅ Address Generation (SHA-256 + Bech32m)
- **Algorithm:** `address = Bech32m.encode(SHA-256(publicKey))`
- **Source Reference:** `midnight-ledger/base-crypto/src/hash.rs::persistent_hash`
- **Implementation:** `core/crypto/src/main/kotlin/com/midnight/kuira/core/crypto/address/`
  - `Bech32m.kt` - Bech32m encoding/decoding (BIP-350 compliant)
- **Features:**
  - SHA-256 hash of BIP-340 x-only public key (32 bytes)
  - Bech32m encoding with HRP: `mn_addr_preview` or `mn_addr_mainnet`
  - 5-bit to 8-bit conversion for Bech32m
  - Checksum validation (constant: 0x2bc830a3)
- **Tests:** 8 tests passing
  - Encoding with different HRPs
  - Decoding and validation
  - Invalid input handling
- **Verification:** ✅ Produces IDENTICAL addresses to Midnight SDK
  - Test mnemonic: "abandon abandon abandon ... art"
  - Expected address: `mn_addr_preview19kxg8sxrsty37elmm6yd68tuy7prryjst2r48eapf2fdtd8z4gpq8xczf2`
  - Kuira output: `mn_addr_preview19kxg8sxrsty37elmm6yd68tuy7prryjst2r48eapf2fdtd8z4gpq8xczf2`
  - **Match: ✅ VERIFIED**

#### ✅ Integration Tests
- **Location:** `core/crypto/src/test/kotlin/com/midnight/kuira/core/crypto/integration/`
- **File:** `AddressGenerationTest.kt`
- **Tests:**
  1. Generate unshielded address from test mnemonic (prints for manual verification)
  2. Generate unshielded address from new random mnemonic
  3. Generate multiple addresses from same mnemonic (HD wallet)
- **Verification Script:** `/Users/norman/Development/midnight/MidnightWasmTest/verify-kuira-address.mjs`
  - Compares Kuira output with Midnight SDK
  - All components verified: Private Key ✅ | Public Key ✅ | Address ✅

### Test Coverage Summary

| Test Suite | Tests | Purpose |
|------------|-------|---------|
| BIP39Test | 22 | Basic BIP-39 functionality |
| BIP39EdgeCaseTest | 5 | Edge cases & error handling |
| BIP39SecurityTest | 15 | Security validations |
| BIP39MidnightCompatibilityTest | 12 | Midnight SDK compatibility |
| HDWalletTest | 12 | BIP-32 HD wallet functionality |
| Bech32mTest | 8 | Bech32m encoding/decoding |
| **TOTAL** | **74 tests** | **All passing ✅** |

**Total Test Code:** 1,466 lines
**Code Quality:** A+ (zero TODO/FIXME comments)

### What's NOT Done (Can be added as needed)

❌ **Schnorr Transaction Signing**
- We can derive public keys, but not sign transactions yet
- Needed for Phase 2 (transaction submission)
- Estimated: 6-8 hours

❌ **Secure Storage (Android Keystore)**
- Mnemonic encryption with hardware-backed keys
- Needed for production wallet
- Estimated: 3-4 hours

### Critical Discoveries

1. **Address Algorithm:** Found missing SHA-256 step by reading Rust source code
   - Initial attempt encoded public key directly with Bech32m ❌
   - Correct: SHA-256(public key) → then Bech32m encode ✅
   - Source: `midnight-ledger/base-crypto/src/hash.rs`

2. **Midnight SDK Compatibility:** 100% verified
   - Same mnemonic → same seed → same keys → same addresses
   - Cross-wallet import/export will work between Kuira and Lace

### Deliverables ✅

- ✅ `:core:crypto` module complete (5 packages, 74 tests)
- ✅ Can generate mnemonics and restore from mnemonic
- ✅ Can derive HD keys at Midnight paths
- ✅ Can generate addresses compatible with Midnight network
- ✅ Verified against official Midnight SDK

### Verification Results

**Test Mnemonic:** "abandon abandon abandon... art" (24 words)

**HD Key Derivation (BIP-32):**
| Path | Role | Private Key | Match |
|------|------|-------------|-------|
| m/44'/2400'/0'/0/0 | Unshielded | `af7a998947...` | ✅ |
| m/44'/2400'/0'/3/0 | Shielded | `b7637860b1...` | ✅ |
| m/44'/2400'/0'/2/0 | Dust | `eb8d1f8ec9...` | ✅ |

**Unshielded Address Generation:**
- Input: Private key `af7a998947...`
- Public key: `1a106adf78...`
- Address: `mn_addr_undeployed19kxg8sxrsty37elmm6yd68tuy7prryjst2r48eapf2fdtd8z4gpqauuvtx`
- ✅ Matches Wallet SDK
- ✅ Recognized by network
- ✅ Wallet syncs successfully

**Note:** We can derive seeds for all roles, but only UNSHIELDED addresses are implemented. Shielded/Dust address generation is Phase 3 work.

---

## Phase 2: Unshielded Transactions ⏳ NOT STARTED

**Goal:** Send/receive unshielded tokens (no privacy yet)
**Estimate:** 15-20 hours
**Status:** Ready to start

### Planned Components

#### 📋 Substrate RPC Client
- WebSocket connection to Midnight node
- Transaction submission via `api.tx.midnight.sendMnTransaction()`
- Block subscription and finality tracking
- Genesis fetching for initial state
- Reconnection with exponential backoff

#### 📋 SCALE Codec
- Binary serialization for Substrate transactions
- Must exactly match Rust ledger format
- Critical for node submission

#### 📋 Intent-Based Transaction Builder
- Create transactions with Intent (Segment 0 = guaranteed)
- Build offers: inputs + outputs + change
- TTL (time-to-live) for expiration

#### 📋 Schnorr Transaction Signing (BIP-340)
- Extract signature data per segment
- Sign with private key from HD wallet
- Add signatures to offers
- Bind transaction (final immutability)

#### 📋 UTXO State Tracking
- Local database: Available/Pending/Spent
- Coin selection: largest-first strategy
- State transitions on: build, confirm, fail, rollback
- Sync with chain state

#### 📋 Balance Queries
- Query via RPC: `state_getStorage()`
- Parse genesis transactions for initial state

### Blockers
- Need testnet endpoint URL from user

### Files to Create
```
core/network/
  ├── SubstrateRpcClient.kt
  ├── PolkadotApi.kt
  └── dto/BlockchainResponses.kt

core/ledger/
  ├── UnshieldedTransaction.kt
  ├── TransactionBuilder.kt
  ├── TransactionSigner.kt (needs BIP-340 Schnorr signing)
  └── ScaleCodec.kt

core/data/
  ├── UtxoDatabase.kt (Room)
  └── dao/UtxoDao.kt
```

---

## Phase 3: Shielded Transactions ⏳ NOT STARTED

**Goal:** Private transactions with ZK proofs
**Estimate:** 20-25 hours
**Status:** Blocked by Phase 2

### Planned Components

#### 📋 Proof Server HTTP Client
- Binary protocol (not JSON)
- POST /prove: unproven tx → proven tx
- POST /check: validate before proving
- Retry strategy: 3 attempts with exponential backoff
- Handle 502/503/504 (server overload)

#### 📋 Zswap Secret Keys
- Derive from HD seed with role = 3 (Zswap)
- Different from signing keys (role = 0/1)
- Used for: note encryption, nullifier derivation

#### 📋 Proving Recipe Determination
- `TRANSACTION_TO_PROVE`: New shielded tx
- `BALANCE_TRANSACTION_TO_PROVE`: Imbalanced tx
- `NOTHING_TO_PROVE`: Already balanced

#### 📋 Shielded Coin Selection
- Select from shielded UTXO set
- Calculate change outputs
- Handle both guaranteed and fallible sections

#### 📋 ZK Parameter Cache
- Fetch from Midnight S3 on-demand
- Local cache in Android cache dir
- SHA-256 verification
- First-run download experience

### Blockers
- Phase 2 must be complete
- Need proof server endpoint from user

---

## Phase 4: Indexer Integration ⏳ NOT STARTED

**Goal:** Fast balance/history queries without full node sync
**Estimate:** 10-15 hours
**Status:** Blocked by Phase 2-3

### Planned Components

#### 📋 Indexer HTTP Client
- REST API for balance queries
- Transaction history (paginated)
- Real-time updates via WebSocket

#### 📋 Balance Aggregation
- Combine unshielded + shielded + dust balances
- Cache for offline viewing
- Sync strategy

#### 📋 Transaction History
- Parse and display transaction details
- Filter by type (send/receive/shield/unshield)
- Export functionality

### Blockers
- Need indexer endpoint URL from user

---

## Phase 5: DApp Connector ⏳ NOT STARTED

**Goal:** Enable DApp integration via WebView bridge
**Estimate:** 15-20 hours
**Status:** Blocked by Phase 2-3

### Planned Components

#### 📋 DApp Connector API (18 methods)
- Balance queries (shielded, unshielded, dust)
- Address queries
- Transaction history (paginated)
- Make transfer/intent
- Balance transactions (sealed/unsealed)
- Sign data
- Submit transaction
- Proving provider
- Configuration sharing
- Connection status
- Permission system (hintUsage)

#### 📋 WebView Bridge
- JavaScript injection
- `window.midnight['com.kuira.wallet']` namespace
- Permission prompts
- Secure communication

#### 📋 DApp Browser
- Custom WebView with bridge
- DApp discovery
- Permission management UI

---

## Phase 6: UI & Polish ⏳ NOT STARTED

**Goal:** Production-ready wallet UI
**Estimate:** 15-20 hours
**Status:** Blocked by all previous phases

### Planned Components

#### 📋 Onboarding Flow
- Generate new wallet
- Restore from mnemonic
- Biometric setup
- Backup verification

#### 📋 Main Wallet Screen
- Balance display (total + per-token)
- Transaction history
- Send/receive buttons
- Network indicator

#### 📋 Send Flow
- Address input (scan QR, paste, contacts)
- Amount input with max button
- Fee estimation
- Privacy toggle (shielded/unshielded)
- Confirmation screen

#### 📋 Receive Flow
- Address display with QR code
- Copy button
- Share functionality

#### 📋 Settings
- Network configuration (testnet/preview)
- Node endpoint configuration
- Indexer endpoint configuration
- Proof server endpoint configuration
- Security settings
- About/version info

#### 📋 DApp Browser
- Integrated browser with connector
- Permission management

---

## Critical Success Metrics

### Phase 1 (ACHIEVED) ✅
- ✅ Generate 24-word mnemonic
- ✅ Mnemonic → Seed produces SAME output as Lace
- ✅ Seed → HD keys produces SAME output as Lace
- ✅ Private key → Public key produces SAME output as Lace
- ✅ Public key → Address produces SAME output as Lace
- ✅ 74 tests passing, zero TODO/FIXME
- ✅ Verification script confirms compatibility

### Phase 2 (PENDING)
- [ ] Send unshielded tokens on testnet
- [ ] Receive unshielded tokens on testnet
- [ ] Balance updates correctly
- [ ] UTXO tracking works
- [ ] Transaction history displays

### Phase 3 (PENDING)
- [ ] Send shielded tokens on testnet
- [ ] Receive shielded tokens on testnet
- [ ] ZK proofs generate successfully
- [ ] Proof server integration works
- [ ] Privacy guarantees verified

### Phase 4 (PENDING)
- [ ] Indexer queries return correct data
- [ ] Real-time balance updates
- [ ] Transaction history loads fast

### Phase 5 (PENDING)
- [ ] DApp can connect to wallet
- [ ] DApp can query balances
- [ ] DApp can request signatures
- [ ] Permission system works
- [ ] 18 connector methods implemented

### Phase 6 (PENDING)
- [ ] Onboarding flow complete
- [ ] Can send/receive via UI
- [ ] Settings panel functional
- [ ] DApp browser works
- [ ] Production-ready polish

---

## Risk Assessment

### Completed Risks ✅
- ✅ **BIP-39/32 Compatibility:** Verified with Midnight SDK test vectors
- ✅ **Address Format:** Verified SHA-256 + Bech32m matches Lace exactly
- ✅ **Library Selection:** BitcoinJ works perfectly for BIP-39/32

### Active Risks 🟡
- ⚠️ **Schnorr Signing:** Need to implement BIP-340 signing (not just public key derivation)
- ⚠️ **SCALE Codec:** Must match Rust serialization exactly
- ⚠️ **Proof Server Protocol:** Binary protocol, must match Midnight's implementation
- ⚠️ **Indexer Availability:** Need reliable indexer for production

### Future Risks 🔴
- 🔴 **Protocol Changes:** Midnight is in active development, may introduce breaking changes
- 🔴 **ZK Parameter Updates:** New parameters may require app updates
- 🔴 **DApp API Stability:** Connector API is new, may evolve

---

## Next Steps

### Immediate (This Week)
1. ✅ Update progress tracker (this document)
2. 📋 User to provide testnet endpoint URL
3. 📋 Start Phase 2: Implement Substrate RPC client
4. 📋 Implement Schnorr transaction signing (6-8h)

### Short Term (Next 2 Weeks)
1. 📋 Complete Phase 2: Unshielded transactions
2. 📋 Test send/receive on testnet
3. 📋 User to provide proof server endpoint
4. 📋 Start Phase 3: Shielded transactions

### Medium Term (Next 4-6 Weeks)
1. 📋 Complete Phase 3: Shielded transactions
2. 📋 Complete Phase 4: Indexer integration
3. 📋 Start Phase 5: DApp connector

### Long Term (Next 8-12 Weeks)
1. 📋 Complete Phase 5: DApp connector
2. 📋 Complete Phase 6: UI & Polish
3. 📋 Production release preparation

---

## Reference Documentation

- **Main Plan:** `docs/projects/midnightWallet.md` (6 phases, 80-120 hours)
- **Phase 1 Plan:** `.claude/plans/quiet-dancing-quasar.md` (Lace compatibility)
- **Verification Report:** `docs/projects/midnight-implementation-verification.md`
- **Test Summary:** `/tmp/review_summary.md`
- **Midnight Libraries:** `/Users/norman/Development/midnight/midnight-libraries/`
- **Verification Scripts:** `/Users/norman/Development/midnight/MidnightWasmTest/`

---

**Last Updated:** January 13, 2026
**Phase 1 Completed:** ✅ January 13, 2026
**Next Milestone:** Phase 2 - Unshielded Transactions (Schnorr signing + RPC)
