# Documentation Structure

**Rule:** ONE living document per topic. Update it, don't create new versions.

---

## Sources of Truth

### 📋 Core Docs (Always Current)

| Document | Purpose |
|----------|---------|
| **PLAN.md** | Master plan (what we're building - 6 phases, 80-120h) |
| **PROGRESS.md** | Status tracker (where we are, hours invested) |

### 🔧 Component Docs (One Per Feature)

| Document | Status | Component |
|----------|--------|-----------|
| **SHIELDED_KEYS.md** | ⏳ Step 1 done | Shielded key derivation (JNI FFI to Rust) |

**As we build:** Create new component docs (e.g., `UNSHIELDED_TRANSACTIONS.md`, `INDEXER.md`)

---

## Reading Order

1. **PLAN.md** → Understand what we're building
2. **PROGRESS.md** → See where we are
3. **Component docs** → Deep dive on specific features

---

## Rules

### ✅ DO
- Update existing docs when status changes
- Keep component docs < 200 lines
- Add "Last Updated: YYYY-MM-DD" at top
- Create new component doc when starting new feature

### ❌ DON'T
- Create versioned docs (`SHIELDED_KEYS_V2.md` ❌)
- Create review docs (`CODE_REVIEW.md` ❌) - add findings to component doc
- Create phase docs (`PHASE_2B.md` ❌) - update component doc instead
- Keep outdated docs - archive or delete

### When to Create New Doc
**Only for new components:**
- ✅ `SHIELDED_KEYS.md` - New component
- ✅ `INDEXER.md` - New component
- ❌ `SHIELDED_KEYS_PHASE_2.md` - Update existing
- ❌ `SHIELDED_KEYS_REVIEW.md` - Update existing

---

## Archive

`archive/` contains outdated docs from early development. Ignore them.
