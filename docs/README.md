# Documentation Structure

**Rule:** ONE living document per feature. Update it, don't create new versions.

---

## Current Documentation

| Document | Status | Purpose |
|----------|--------|---------|
| **PLAN.md** | ✅ **CURRENT** | Master plan (6 phases, 80-120 hours) |
| **PROGRESS.md** | ✅ **CURRENT** | Current progress tracking |
| **SHIELDED_KEYS.md** | ✅ **CURRENT** | Shielded key derivation implementation |

**Read order:** PLAN.md (what we're building) → PROGRESS.md (where we are) → Feature docs (how it works)

---

## Archive

Old docs are in `archive/` - they're outdated. Don't read them.

---

## Convention

### ✅ DO:
- **Update existing docs** when status changes
- Keep docs < 200 lines (concise)
- Put "Last Updated: YYYY-MM-DD" at top
- Use status badges (✅ Complete, ⏳ In Progress, ❌ Blocked)

### ❌ DON'T:
- Create "Phase X" docs (update the main doc instead)
- Create "Review" docs (add findings to main doc)
- Create "Plan" docs (use GitHub issues or TODOs instead)
- Keep outdated docs (archive or delete)

---

## When to Create New Docs

**Only when starting a completely new feature.**

Example:
- ✅ `SHIELDED_KEYS.md` (new feature)
- ✅ `UNSHIELDED_ADDRESSES.md` (different feature)
- ❌ `SHIELDED_KEYS_PHASE_2.md` (update existing instead)
- ❌ `SHIELDED_KEYS_REVIEW.md` (update existing instead)
