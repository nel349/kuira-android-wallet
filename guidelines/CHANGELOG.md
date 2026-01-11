# Guidelines Changelog

**Purpose:** Track updates to engineering guidelines as we learn during implementation.

---

## [1.0.1] - 2026-01-10

### Added
- Cross-references between guideline files
- ARCHITECTURE_GUIDELINES.md reference in CLAUDE.md
- "Related Guidelines" section to all guideline files

### Fixed
- LEARNING_STRATEGY.md no longer references deleted ENGINEERING_GUIDELINES.md
- CLAUDE.md now references all 6 guideline files

---

## [1.0.0] - 2026-01-10

### Added
- Initial split of monolithic ENGINEERING_GUIDELINES.md into topic-specific files:
  - `ARCHITECTURE_GUIDELINES.md` - Architecture patterns, Clean Architecture, module structure
  - `KOTLIN_GUIDELINES.md` - Kotlin-specific patterns, naming, null safety, coroutines
  - `SECURITY_GUIDELINES.md` - Security best practices, memory wiping, Android security
  - `TESTING_GUIDELINES.md` - Testing patterns, TDD, test organization
  - `COMPOSE_GUIDELINES.md` - Jetpack Compose patterns, state hoisting, side effects
  - `MIDNIGHT_GUIDELINES.md` - Midnight-specific patterns with confidence levels

### Changed
- MIDNIGHT_GUIDELINES.md marked as "LIVING DOCUMENT" with confidence levels:
  - ✅ Verified - Implemented and tested
  - 🔄 Hypothesis - Inferred from SDK, needs validation
  - ❓ Unknown - Needs investigation
- Added "Update After Implementation" checklists to MIDNIGHT_GUIDELINES.md

### Documentation
- CLAUDE.md updated to reference split guideline files
- LEARNING_STRATEGY.md simplified to focus on learning approach

---

## How to Update This Changelog

### During Implementation
When you discover a pattern that should be added or changed:

1. **Make the change** to the relevant guideline file
2. **Update CHANGELOG.md** with the change
3. **Update version number** if significant change

### Version Numbering
- **Major (X.0.0)** - Fundamental restructuring or major additions
- **Minor (1.X.0)** - New sections, significant pattern additions
- **Patch (1.0.X)** - Small fixes, clarifications, examples

### Entry Format
```markdown
## [Version] - YYYY-MM-DD

### Added
- New patterns, sections, or files

### Changed
- Modified existing patterns or recommendations

### Fixed
- Corrections to errors or outdated information

### Removed
- Deprecated patterns or sections

### Deprecated
- Patterns marked for future removal
```

---

## Future Updates

**After Phase 1 (Crypto Module):**
- [ ] Update MIDNIGHT_GUIDELINES confidence levels based on implementation
- [ ] Add real code examples to replace hypothetical ones
- [ ] Document BIP-39/BIP-32 quirks discovered
- [ ] Update SECURITY_GUIDELINES with memory wiping findings

**After Phase 2 (Unshielded Transactions):**
- [ ] Document actual TTL behavior
- [ ] Add UTXO state management patterns
- [ ] Update coin selection strategies

**After Phase 3 (Shielded Transactions):**
- [ ] Document proof server API details
- [ ] Add ZK proof generation patterns
- [ ] Update timing recommendations

**After Phase 4 (Indexer Integration):**
- [ ] Document sync behavior
- [ ] Add indexer patterns

**After Phase 5 (DApp Connector):**
- [ ] Document deep link patterns
- [ ] Add DApp security considerations

**After Phase 6 (UI/Polish):**
- [ ] Add real Compose examples from implementation
- [ ] Document accessibility findings
