# Shielded Address Generation - Complete Specification

## Executive Summary

**Confidence Level: 92%**

Shielded address generation is **90% simple hashing** (Blake2b-256) and **10% JubJub elliptic curve** operations.

After deep analysis of Midnight's Rust source code, we have **exact specifications** for implementing this in pure Kotlin.

---

## Complete Algorithm with Source References

### Input
- 32-byte shielded seed (from BIP-32 at `m/44'/2400'/0'/3/0`)

### Step 1: Derive Coin Secret Key (Blake2b hash)
```kotlin
val coinSecretKey = Blake2b256("midnight:csk" || shieldedSeed)
// Output: 32 bytes
```
**Source**: `midnight-ledger/zswap/src/keys.rs:59-66`

### Step 2: Derive Coin Public Key (Blake2b hash)
```kotlin
val coinPublicKey = Blake2b256("midnight:zswap-pk[v1]" || coinSecretKey)
// Output: 32 bytes (hex encoded for addresses)
```
**Source**: `midnight-ledger/coin-structure/src/coin.rs:161-166`

✅ **This is just hashing - NO elliptic curve needed!**

### Step 3: Derive Encryption Secret Key (JubJub scalar)
```kotlin
// Step 3a: Derive 64 bytes using iterative Blake2b
val rawBytes = deriveBytes(
    length = 64,
    domainSeparator = "midnight:esk".toByteArray(),
    seed = shieldedSeed
)

// Step 3b: Convert to JubJub scalar (reduce mod r)
val encryptionSecretKey = JubJubScalar.fromBytesWide(rawBytes)
```

**Iterative Derivation Algorithm**:
```kotlin
fun deriveBytes(length: Int, domainSeparator: ByteArray, seed: ByteArray): ByteArray {
    val result = ByteArray(length)
    val rounds = (length + 31) / 32  // ceiling division

    for (round in 0 until rounds) {
        // Inner hash: Blake2b(round_le_bytes || seed)
        val roundBytes = round.toLong().toLeBytes()  // u64 little-endian
        val innerHash = Blake2b256(roundBytes || seed)

        // Outer hash: Blake2b(domain_separator || inner_hash)
        val outerHash = Blake2b256(domainSeparator || innerHash)

        // Copy bytes to result
        val bytesToCopy = min(32, length - round * 32)
        outerHash.copyInto(result, destinationOffset = round * 32, endIndex = bytesToCopy)
    }
    return result
}
```
**Source**: `midnight-ledger/zswap/src/keys.rs:68-98`

### Step 4: Derive Encryption Public Key (JubJub point)
```kotlin
val encryptionPublicKey = JubJubAffine.GENERATOR * encryptionSecretKey
// Output: 32-byte compressed point
```
**Source**: `midnight-ledger/transient-crypto/src/encryption.rs:189-191`

⚠️ **This requires JubJub elliptic curve operations**

### Step 5: Encode Shielded Address
```kotlin
val addressData = coinPublicKey + encryptionPublicKey  // 64 bytes total
val shieldedAddress = Bech32m.encode(
    hrp = "mn_shield-addr",
    network = "undeployed",
    data = addressData
)
```

---

## JubJub Curve Exact Specifications

### Curve Type
**Twisted Edwards curve** over BLS12-381's scalar field (Fq)

**Equation**: `-u^2 + v^2 = 1 + d * u^2 * v^2`

**Parameters**:
- **d** = -(10240/10241) (Edwards D constant)
- **Scalar Field Modulus (Fr)**:
  - `0x0e7db4ea6533afa906673b0101343b00a6682093ccc81082d0970e5ed6f72cb7`
  - Four 64-bit limbs (little-endian): `[0xd0970e5ed6f72cb7, 0xa6682093ccc81082, 0x06673b0101343b00, 0x0e7db4ea6533afa9]`

**Source**: `midnight-zk/curves/src/jubjub/` (curve.rs, fr.rs)

### Generator Point
```
u = 0x62edcbb8bf3787c88b0f03ddd60a8187caf55d1b29bf81afe4b3d35df1a7adfe
v = 0x000000000000000b (decimal 11)
```
**Source**: `midnight-zk/curves/src/jubjub/curve.rs:1377-1393`

### Point Serialization (32 bytes)
Format: v-coordinate (32 bytes little-endian) with sign bit of u in bit 7 of byte 31

```kotlin
fun JubJubAffine.toBytes(): ByteArray {
    val bytes = v.toLittleEndianBytes()  // 32 bytes
    // Encode sign of u in bit 7 of byte 31
    if (u.isNegative()) {
        bytes[31] = (bytes[31].toInt() or 0x80).toByte()
    }
    return bytes
}
```
**Source**: `midnight-zk/curves/src/jubjub/curve.rs:444-453`

---

## Implementation Options Analysis

### Option A: Pure Kotlin Implementation ⭐ RECOMMENDED

**What we need to implement**:
1. ✅ Blake2b-256 hashing (use BouncyCastle - already added)
2. ✅ Iterative hash derivation (straightforward)
3. ⚠️ Fq field arithmetic (BLS12-381 scalar field, 256-bit operations)
4. ⚠️ Fr field arithmetic (JubJub scalar field, 252-bit operations)
5. ⚠️ JubJub point addition/doubling
6. ⚠️ Scalar multiplication
7. ⚠️ Point serialization/deserialization

**Pros**:
- No JNI complexity
- Pure Kotlin = easier testing & debugging
- Full control over implementation
- Android-optimized (no native library size)

**Cons**:
- Need to implement field arithmetic (3-4 hours)
- Need to implement curve operations (4-6 hours)
- Higher risk of subtle bugs

**Estimated Effort**: 12-16 hours total
- Blake2b + derivation: 2-3 hours ✅
- Field arithmetic (Fq, Fr): 3-4 hours
- Curve operations: 4-6 hours
- Testing + integration: 3-4 hours

**Confidence**: 85% we can implement correctly with test vectors

---

## Test Vectors (from "abandon abandon... art" mnemonic)

### Inputs
**Shielded Seed** (m/44'/2400'/0'/3/0):
```
b7637860b12f892ee07c67ad441c7935e37ac2153cefa39ae79083284f6d9180
```

### Expected Outputs

**Coin Public Key** (32 bytes, hex):
```
274c79e90fdf0e29468299ff624dc7092423041ba3976b76464feae3a07b994a
```

**Encryption Public Key** (32 bytes, hex):
```
f3ae706bf28c856a407690b468081a7f5a123e523501b69f4395abcd7e19032b
```

**Shielded Address**:
```
mn_shield-addr_undeployed1[bech32m checksum of 64-byte concatenation]
```

---

## Risk Assessment

### Low Risk (95%+ confidence)
- ✅ Blake2b hashing - BouncyCastle is battle-tested
- ✅ Iterative derivation - straightforward algorithm
- ✅ Coin public key - just hashing

### Medium Risk (85-90% confidence)
- ⚠️ Field arithmetic - standard algorithms, many references
- ⚠️ Point serialization - exact format specified in source

### Higher Risk (75-85% confidence)
- ⚠️ Scalar multiplication - must be constant-time & correct
- ⚠️ Point addition formulas - must use exact formulas from source
- ⚠️ Subtle bugs in field reduction

---

## Next Steps

1. ✅ **Research Midnight libraries** - COMPLETE
   - Found exact specifications
   - Have complete Rust source code

2. **Check for existing Kotlin/Java JubJub libraries** (1-2 hours)
   - Search for BLS12-381 implementations
   - Check ZCash libraries

3. **Create detailed implementation plan** (1 hour)
   - Choose implementation approach
   - Break down into sub-tasks

4. **Implement & test** (12-16 hours)
   - Blake2b layer
   - JubJub operations
   - Full integration

---

## Success Criteria

✅ **Phase 1 Complete When:**
- Generate coin public key matching SDK: `274c79e9...`
- Generate encryption public key matching SDK: `f3ae706b...`
- Generate shielded address that can be parsed by Lace wallet
- All intermediate test vectors match

---

## Confidence: 92%

**Why 92%?**
- ✅ Have complete Rust source code
- ✅ Have exact curve parameters
- ✅ Have serialization format
- ✅ Have test vectors to validate against
- ⚠️ Need to verify field arithmetic implementation
- ⚠️ Subtle bugs in elliptic curve code are possible

---

## References

**Midnight Rust Sources**:
- `midnight-ledger/zswap/src/keys.rs` - Key derivation
- `midnight-ledger/coin-structure/src/coin.rs` - Coin keys
- `midnight-ledger/transient-crypto/src/encryption.rs` - Encryption keys
- `midnight-zk/curves/src/jubjub/curve.rs` - JubJub implementation
- `midnight-zk/curves/src/jubjub/fr.rs` - Scalar field
