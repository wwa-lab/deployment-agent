# ADR-0007: Adopt Atlas-style project rules and bilingual SDD for WWA

## Status

Superseded in part by ADR-0009 (English-only docs). Remaining Accepted: Atlas-style `PROJECT_RULES` / `DEVELOPMENT_STANDARDS`, `wwa-sdd-generate-all`, and skill-chain evidence. The bilingual SDD mandate is withdrawn.

## Date

2026-07-25

## Context

WWA Agent Hub already uses Spec Driven Development via `docs/00-context/sdd-profile.md`, global Agentic SDLC skills, and rich `CLAUDE.md` / `AGENTS.md` contracts. Atlas Knowledge Hub matured a clearer operating model: root `PROJECT_RULES.md` / `DEVELOPMENT_STANDARDS.md`, bilingual SDD (English + Simplified Chinese), a one-entry orchestration skill (`atlas-sdd-generate-all`), and an explicit SDD generation gate that requires skill-chain evidence.

The team wants the same governance discipline in WWA without importing Atlas domain rules (knowledge wiki, WeKnora, parser/vector adapters, PostgreSQL-only assumptions).

## Decision

1. Add WWA-adapted root rules:
   - `PROJECT_RULES.md`
   - `DEVELOPMENT_STANDARDS.md`
2. Add bilingual SDD bootstrap:
   - `docs/SDD-BOOTSTRAP.md`
   - `docs/SDD-BOOTSTRAP.zh-CN.md`
3. Add project-local orchestration skill `wwa-sdd-generate-all` under `.agents/skills/` and `.claude/skills/`, chaining existing SDD skills and ending with `review-doc-quality`.
4. Require English + Simplified Chinese companions (`.zh-CN.md`) for every **new or materially updated** SDD artifact going forward. Historical single-language docs may migrate gradually; deferred translation must be recorded in slice traceability.
5. Keep WWA filename conventions (`{slice}-requirement.md`, `{slice}-user-stories.md`) and WWA stack/security/multi-agent boundaries as source of truth over Atlas-specific standards.
6. Update `AGENTS.md`, `CLAUDE.md`, `docs/00-context/sdd-profile.md`, and `docs/00-context/agentic-sdlc-registry.md` to point at these assets.

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| Copy Atlas files verbatim | Would import wrong domain constraints and stack assumptions |
| English-only SDD (status quo) | Rejected by product preference for bilingual slice docs |
| Full Option B (also FE/BE coding standard books) | Deferred; can follow later without blocking governance adoption |
| Replace `sdd-slice-bootstrap` entirely | Keep it for audit/skeleton; prefer `wwa-sdd-generate-all` for full bilingual generation |

## Consequences

### Positive

- Clearer SDD entry point and quality gate for agents
- Aligns WWA and Atlas governance patterns where portable
- Bilingual docs improve reviewer access across EN/ZH stakeholders

### Negative

- More files per slice (EN + ZH)
- Agents must not skip skill-chain evidence
- Historical slices temporarily mixed (mono vs bilingual)

### Neutral / Operational

- Code and default UI copy remain English unless a slice says otherwise
- Control Tower / Atlas remain references, not overrides

## Review Triggers

Revisit when:

- Bilingual maintenance cost becomes unacceptable
- Filename conventions are unified across WWA and Atlas
- Option B coding-standard documents are adopted

## Related Documents

- `PROJECT_RULES.md`
- `DEVELOPMENT_STANDARDS.md`
- `docs/SDD-BOOTSTRAP.md`
- `.agents/skills/wwa-sdd-generate-all/SKILL.md`
- `docs/00-context/checklists/sdd-generation-gate.md`
