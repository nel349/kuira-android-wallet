# Documentation Structure

This directory contains all project documentation organized by purpose.

---

## Directory Structure

```
docs/
├── PLAN.md              # Master implementation plan (6 phases)
├── PROGRESS.md          # Current status and hours tracked
├── README.md            # This file
│
├── planning/            # Plans and implementation strategies
├── progress/            # Status reports and phase completions
├── learning/            # Educational docs and crash courses
├── research/            # Analysis, compatibility studies, investigations
├── archive/             # Outdated docs from early development
└── reviews/             # Code review results and verifications
```

---

## Core Documents (Always Current)

| Document | Purpose |
|----------|---------|
| **PLAN.md** | Master plan - what we're building (6 phases, 80-120h) |
| **PROGRESS.md** | Status tracker - where we are, hours invested |

**Start here** to understand the project.

---

## Category Descriptions

### 📋 `planning/`
Implementation plans and strategies for specific phases or features.
- Phase implementation plans
- Architecture decisions
- Feature design docs

### 📊 `progress/`
Status reports documenting what was completed when.
- Phase completion reports
- Progress snapshots
- Status updates

### 📚 `learning/`
Educational documentation and conceptual explanations.
- "How X Works" deep dives
- Crash courses on technologies
- Architecture overviews
- Tutorial-style guides

### 🔬 `research/`
Analysis and investigation results.
- Compatibility studies
- Code review findings
- Security analyses
- Library evaluations

### 📦 `archive/`
Outdated docs from early development. Historical reference only.

### ✅ `reviews/`
Code review results and verification reports.
- Midnight SDK compatibility checks
- Security reviews
- Test verification results

---

## Documentation Guidelines

### ✅ DO
- Update existing docs when information changes
- Add "Last Updated: YYYY-MM-DD" at top of docs
- Put docs in the appropriate category
- Write clear, searchable titles

### ❌ DON'T
- Create versioned docs (`PLAN_V2.md` ❌) - update the existing one
- Mix categories (planning doc in progress/ ❌)
- Keep outdated docs in main directories - move to archive/

---

## Finding Documentation

**Want to know what we're building?**
→ `PLAN.md`

**Want to know current status?**
→ `PROGRESS.md`

**Want to understand how something works?**
→ `learning/` directory

**Want to see what was completed?**
→ `progress/` directory

**Want implementation details for a phase?**
→ `planning/` directory

**Want analysis or research results?**
→ `research/` directory
