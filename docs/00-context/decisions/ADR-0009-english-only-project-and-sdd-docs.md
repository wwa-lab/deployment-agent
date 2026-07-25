# ADR-0009: English-only project rules and SDD documents

## Status

Accepted

## Date

2026-07-25

## Context

ADR-0007 adopted Atlas-style project rules including mandatory English + Simplified Chinese SDD companions. Maintaining dual-language copies for every slice increased cost without enough value for this repository’s current operating model.

The user decided project rules and SDD artifacts should remain English-only.

## Decision

1. Project rules, SDD documents, active agent handoff, bootstrap, and related checklists are **English-only**.
2. Do **not** require `.zh-CN.md` companions for new or updated SDD / governance docs.
3. Pre-existing Chinese product docs outside this governance set (for example historical `README.zh-CN.md` or open-collaboration submissions) may remain; they are not a template for SDD going forward.
4. The bilingual requirement in ADR-0007 is **superseded** by this ADR. Other ADR-0007 decisions remain in force: `PROJECT_RULES.md`, `DEVELOPMENT_STANDARDS.md`, `wwa-sdd-generate-all`, skill-chain evidence, and Atlas-as-reference (not domain copy).
5. ADR-0008 handoff remains, but as a single English file: `docs/00-context/AGENT_HANDOFF.md`.

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| Keep bilingual SDD | User rejected the maintenance cost |
| Optional Chinese only when asked | Easy to drift; prefer one default language |

## Consequences

### Positive

- Lower doc maintenance cost
- Matches historical WWA English-default convention

### Negative

- Chinese-speaking reviewers rely on English SDD or ad hoc translation

### Neutral / Operational

- Remove generated `.zh-CN.md` companions created under ADR-0007 for Service Directory and governance
- Update bootstrap, skills, gates, and agent instructions accordingly

## Review Triggers

Revisit if the team later requires bilingual compliance or multi-region reviewer access.

## Related Documents

- ADR-0007 (partially superseded)
- ADR-0008
- `PROJECT_RULES.md`
- `docs/SDD-BOOTSTRAP.md`
