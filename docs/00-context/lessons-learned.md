# Lessons Learned

Reusable acceptance mismatches and prevention updates for WWA Agent Hub.

## Format

For each lesson:

1. **Symptom** — what was wrong
2. **Root cause**
3. **Prevention artifact updated** — rule, SDD, checklist, test, or standard
4. **Date**

---

## Entries

### LL-2026-07-25 — Atlas domain rules must not be copied verbatim into WWA

- **Symptom:** Risk of importing WeKnora/parser/PostgreSQL-only constraints into a multi-agent delivery hub.
- **Root cause:** Porting “project rules” without an adaptation filter.
- **Prevention artifact updated:** `PROJECT_RULES.md` (Control Tower/Atlas as reference only), `ADR-0007`.
- **Date:** 2026-07-25

### LL-2026-07-25b — Do not keep bilingual SDD when the team wants English-only

- **Symptom:** Dual-language SDD/governance companions added, then immediately rejected as too costly.
- **Root cause:** Adopted Atlas bilingual mandate without confirming WWA preference.
- **Prevention artifact updated:** `ADR-0009`, `PROJECT_RULES.md` Language section, English-only bootstrap/skills.
- **Date:** 2026-07-25

### LL-2026-07-25c — Governance files document the wrong Java base package

- **Symptom:** `CLAUDE.md` (9 occurrences) and `AGENTS.md` (7) state the base package as `com.wwa.deploymentagent` in their Architecture Boundaries sections. (`PROJECT_RULES.md` was checked and is clean.) The code uses `com.wwa.agenthub`. An agent following the governance files would generate classes into a package that does not exist, and the first Service Directory SDD draft inherited the wrong package.
- **Root cause:** The repository was renamed from a single-agent Deployment Agent to the multi-agent Agent Hub; the code moved but the governance files' boundary sections did not. Nothing verifies package claims, so the drift stayed invisible.
- **Prevention artifact updated:** Corrected throughout the regenerated `service-directory` SDD set and recorded as task **SD-T70** in `docs/06-tasks/service-directory-tasks.md`. Fixing the two governance files needs explicit user approval (`SD-OQ-05`) because they are agent-contract documents.
- **Date:** 2026-07-25

### LL-2026-07-25d — SDD documents must be grounded, not inherited

- **Symptom:** The first `service-directory` draft asserted eight things about the codebase that were not true — searchable URLs, reusable Configuration Management audit action types, a stored `openInNewTab` flag, usable `AuditLogEntry.target_type` / `target_id` columns, two separate navigation registrations, and the wrong base package among them.
- **Root cause:** The draft was hand-written without running the skill chain's grounding pass, so upstream assumptions propagated downstream unverified (phantom inheritance).
- **Prevention artifact updated:** The regenerated set carries `file:line` anchors for every claim about existing code, plus an explicit "Corrections applied during the grounding pass" table in the spec and in `docs/00-context/service-directory-traceability.md`. Grounding evidence is now expected in the skill-chain report per `docs/00-context/checklists/sdd-generation-gate.md`.
- **Date:** 2026-07-25

### LL-2026-07-25e — `architecture-review` is referenced but does not exist

- **Symptom:** The session brief required running an `architecture-review` skill. No skill by that name exists in `.agents/skills/`, `.claude/skills/`, `~/.codex/skills/`, or `~/.agents/skills/`.
- **Root cause:** The name is close enough to real skills (`review-doc-quality`, `review-code-against-design`, `spec-to-architecture`) to look installed without being so.
- **Prevention artifact updated:** The gap is recorded here and in the traceability document. The review was performed instead against the quality checklist inside `spec-to-architecture` plus the SDD profile's architecture gate. Either install a real `architecture-review` skill or stop referencing it in briefs and checklists.
- **Date:** 2026-07-25

### LL-2026-07-25f — "Optimistic locking" in a design does not by itself prevent lost updates

- **Symptom:** The regenerated `service-directory` SDD stated a requirement ("a stale write is rejected with 409") and a design ("JPA `@Version` rejects a stale write, so no bespoke conflict handling is written") that read as consistent but could not satisfy the requirement. Its acceptance criterion — one administrator saving minutes after another — would have shipped as a silent last-write-wins overwrite, and the contract test as written ("two mutations against the same loaded version") would have passed anyway.
- **Root cause:** `@Version` only detects transactions that overlap *in flight*. Every mutation loaded the row inside its own transaction, so sequential saves never conflict, no matter how stale the client's page. The design named a real mechanism, which made the gap invisible to review; nothing in the repo had a client-echoed version to compare against, so there was no precedent to notice was missing.
- **Prevention artifact updated:** Spec `SD-FR-44`, `SD-FR-67`–`SD-FR-69`; the design's M3 and M6 sections; the API guide's Concurrency section; contract checklist row 12; and the two-session manual walkthrough steps. The generalisable rules: (1) when a requirement is about a *user-visible* window like "an open form went stale", check that the mechanism's window actually covers it; (2) a concurrency test must include a case where the conflict must **not** fire — here a create, which is exempt — otherwise the test passes against an over- or under-scoped implementation.
- **Date:** 2026-07-25
