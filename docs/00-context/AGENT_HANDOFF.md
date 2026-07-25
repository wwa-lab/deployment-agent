# Agent Handoff (Active)

> **Read this file first** in every new IDE / agent / chat session before doing product or SDD work.  
> **Update this file last** before ending a session that made meaningful progress.

Do not resume from chat memory. Durable resume state is: **this handoff** + linked SDD/traceability + optional execution manifest + `git status`.

Language: English-only for project rules and SDD (ADR-0009).

---

## Status Snapshot

| Field | Value |
|---|---|
| Last updated | 2026-07-25 |
| Updated by | Cursor agent (full `wwa-sdd-generate-all` regeneration, then a review-and-repair pass) |
| Active slice | `service-directory` |
| Phase | **SDD regeneration + review fixes complete — waiting for user acceptance** |
| Overall status | `blocked_on_user_acceptance` |
| Implementation started? | **No, and it must not start until the user accepts** |
| New-session prompt | `docs/00-context/handoffs/next-session-prompt.md` |
| Implement prompt (Composer 2.5) | `docs/00-context/handoffs/cursor-composer-25-implement-prompt.md` |
| Branch (if known) | check with `git status` / `git branch --show-current` |

---

## Goal (current)

Deliver **Service Directory** as a Platform catalog page (config-driven directory scopes → groups → links, filters, Recently used, `DEVOPS_ADMIN` manage + audit), with the catalog kept separate from Configuration Management.

**Immediate goal is not code.** It is user acceptance of the regenerated SDD set.

---

## Done (do not redo)

### Governance

- Added `PROJECT_RULES.md`, `DEVELOPMENT_STANDARDS.md`, `docs/SDD-BOOTSTRAP.md` (English-only)
- Added `wwa-sdd-generate-all` under `.agents/skills/` and `.claude/skills/`
- Added SDD generation gate checklist
- `ADR-0007` (project rules + skill chain); bilingual part superseded by `ADR-0009`
- `ADR-0008` active handoff; `ADR-0009` English-only docs
- **`ADR-0010` (new, `Proposed`)** — Service Directory owns its own catalog store, separate from Configuration Management; ADR index updated
- Updated `AGENTS.md`, `CLAUDE.md`, `sdd-profile.md`, registry, Copilot bridge
- Removed generated `.zh-CN.md` companions

### Service Directory SDD — regenerated via the full skill chain (this session)

All ten documents were **regenerated and overwritten**, not edited in place. The earlier hand-written draft is gone.

| Document | Path |
|---|---|
| Requirements | `docs/01-requirements/service-directory-requirement.md` |
| User stories | `docs/02-user-stories/service-directory-user-stories.md` |
| Spec | `docs/03-spec/service-directory-spec.md` |
| Architecture | `docs/04-architecture/service-directory-architecture.md` |
| Data flow (**new**) | `docs/04-architecture/service-directory-data-flow.md` |
| Data model | `docs/04-architecture/service-directory-data-model.md` |
| Design | `docs/05-design/service-directory-design.md` |
| API guide (**new**) | `docs/05-design/contracts/service-directory-API_IMPLEMENTATION_GUIDE.md` |
| Tasks | `docs/06-tasks/service-directory-tasks.md` |
| Traceability | `docs/00-context/service-directory-traceability.md` |

Scale: 14 requirements, 8 user stories, 70 functional + 11 non-functional requirements, 25 tasks, 1 read + 9 mutation endpoints, 16 contract-test rows. Every requirement and every FR traces to a task; verified mechanically, not by eye.

### Review-and-repair pass (same session, after generation)

A `review-doc-quality` pass over the finished set found **one Critical and five Major defects**. All are
fixed. The Critical one changed the contract, so it is worth knowing before reading the docs:

**The concurrency design did not work.** SD-FR-44 required a stale write to be rejected with 409, and the
design satisfied it with JPA `@Version` alone. But `@Version` only detects transactions that overlap *in
flight*, while each mutation loads the catalog row inside its own transaction — so the requirement's own
scenario (admin A loads a page, admin B saves, admin A saves ten minutes later) would have been applied
as a silent last-write-wins overwrite, and the contract test as originally written would still have
passed. The design now requires a client-echoed `expectedVersion` query parameter on all six updates and
deletes, keeps `@Version` as a second layer for the in-flight case, and **deliberately exempts the three
creates** because appending cannot overwrite anyone. Recorded as LL-2026-07-25f.

The five Majors: link moves now carry source and destination keys in the audit context; scope and group
keys are immutable after create (titles stay editable); link `sortOrder` ties break by `title` then `id`
everywhere; the `workspace` URL rule has one exact pattern with no query string; and stage groups must
have `key == stageKey` with at most one `stage-strip` scope in the catalog.

Full before/after table: `docs/00-context/service-directory-traceability.md` §"Review findings fixed
after generation".

### Decisions closed in the regenerated set

| # | Decision |
|---|---|
| SD-T00 | **Closed — Option A**: one versioned JSON document row in a new `DA_SERVICE_DIRECTORY_CATALOG` table. Rationale and reversal triggers in the data model §2 and `ADR-0010` |
| SD-T01 | **Default committed, needs the user's word**: guests may read; guest writes stay blocked by the existing filter. `SD-OQ-01` |
| SD-T02 | **Open**: production ARCAD / GitHub Enterprise URLs. Seed ships reserved `.invalid` hosts that render as non-navigable "URL pending". `SD-OQ-02` |
| SD-T03 | **Prepared**: `ADR-0010` written as `Proposed`; flips to `Accepted` on acceptance. `SD-OQ-04` |

### Grounding-pass corrections (the old draft was wrong about these)

1. Java base package is **`com.wwa.agenthub`**, not `com.wwa.deploymentagent`. `CLAUDE.md` (9 occurrences) and `AGENTS.md` (7) still carry the stale package; `PROJECT_RULES.md` is clean. Recorded as **SD-T70**, which needs approval (`SD-OQ-05`) because it edits agent-contract files.
2. Free-text search matches display text only — **not** link URLs.
3. Audit reuse of `config_update` / `config_delete` rejected; the slice adds `service_directory_update` / `service_directory_delete`.
4. `openInNewTab` is derived from link kind, not stored.
5. `AuditLogEntry.target_type` / `target_id` are declared but never written and not exposed by the DTO, so entity identity goes in the audit context payload instead.
6. One `platformCapabilities` entry feeds both the flyout and Home Shared Controls — there is no second registration to add.

Full list in `docs/00-context/service-directory-traceability.md` §"Corrections Applied During The Grounding Pass". Reusable lessons in `docs/00-context/lessons-learned.md` (LL-2026-07-25c … f).

---

## Next actions (do these)

1. **User reviews and accepts (or rejects) the regenerated and repaired SDD set.** Nothing else proceeds first. The concurrency change described above is the one part worth reading closely, since it altered the API contract (six endpoints gained a required query parameter).
2. On acceptance, ratify the four open questions — most importantly `SD-OQ-01` (guest read) and `SD-OQ-04` (ADR acceptance). Both are cheap to reverse now and expensive to reverse after implementation.
3. Flip `ADR-0010` from `Proposed` to `Accepted` and update the ADR index.
4. Start **W1 (persistence)** from `docs/06-tasks/service-directory-tasks.md`: SD-T10 → SD-T12 → SD-T13 → SD-T14. The task document has the full workstream and dependency plan.
5. Decide separately whether **SD-T70** (fixing `CLAUDE.md` / `AGENTS.md`) is approved.

**Do not start implementation before step 1.**

---

## Sources of truth (read in order)

1. This file — `docs/00-context/AGENT_HANDOFF.md`
2. `PROJECT_RULES.md` / `DEVELOPMENT_STANDARDS.md`
3. Slice index and full traceability: `docs/00-context/service-directory-traceability.md`
4. Behavior source of truth: `docs/03-spec/service-directory-spec.md`
5. Execution source of truth: `docs/06-tasks/service-directory-tasks.md`
6. Implementation detail: `docs/05-design/service-directory-design.md` and `docs/05-design/contracts/service-directory-API_IMPLEMENTATION_GUIDE.md`
7. Store boundary rationale: `docs/00-context/decisions/ADR-0010-service-directory-owns-its-catalog-store.md`
8. UX baseline: `docs/prototypes/wwa-service-directory.html`
9. Optional pinned manifest: `docs/00-context/execution-manifests/`

---

## Constraints

- English-only project rules and SDD (ADR-0009)
- Full SDD generation uses the `wwa-sdd-generate-all` skill chain, with reported evidence
- Service Directory catalog **not** stored in `ConfigurationComponent`, `ConfigurationItem`, or `ScopeDirectoryEntry`
- No mock role switch in production auth — the prototype's role `<select>` must not ship
- HITL safety rails in `CLAUDE.md` unchanged. Catalog mutations are ordinary interactive admin writes with `actor_kind = HUMAN`; nothing in this slice auto-approves anything
- Do not expand scope beyond the accepted SDD
- Use the real Java base package `com.wwa.agenthub`, whatever the governance files still say
- Do not commit unless the user explicitly asks

---

## Blockers / open questions

| # | Question | Blocking what | Owner |
|---|---|---|---|
| SD-OQ-01 | Guests read the catalog, or get redirected? Default committed: read allowed | Implementation start (cheap now, visible behavior change later) | Product / Security |
| SD-OQ-02 | Production ARCAD / GitHub Enterprise URLs | Release readiness, not development | Ops / Platform |
| SD-OQ-03 | Shared source for SDLC guideline links with the Agent Contribute Dashboard? Default: manual alignment for MVP | Nothing | Product |
| SD-OQ-04 | Must `ADR-0010` be accepted before implementation? Default: yes | Implementation start | Architecture |
| SD-OQ-05 | Approve SD-T70 (fix the stale package in `CLAUDE.md` and `AGENTS.md`)? | Nothing in this slice | User |

Resolved since the last handoff: persistence Option A/B (now SD-T00, closed) and whether to re-run the skill chain (done this session).

Known gap, not a blocker: **no `architecture-review` skill exists** in any skills directory. The architecture review was performed against the quality checklist inside `spec-to-architecture` plus the SDD profile's architecture gate. Either install the skill or stop referencing it (LL-2026-07-25e).

---

## Verification already run

Docs-only session. No product code changed, so no `mvn test` or frontend build is claimed.

What was actually verified, mechanically:

- All 105 `file:line` code citations in the set resolve to a real file with the range inside its bounds; the ones touching the concurrency rewrite were additionally read for content (`OptimisticLockConflictException.java:4-9` is a 409 `AppException`; `GlobalExceptionHandler.java:24-28` is the `AppException` handler; `:31-36` is the raw JPA lock handler; `:49-57` is the import handler, which is why 422 cannot occur here)
- Negative claims confirmed by search: no `localStorage` / `sessionStorage` anywhere in `frontend/src`; no `@PreAuthorize` in the codebase; **no existing endpoint accepts a client-supplied version**, so `expectedVersion` is a new convention rather than an existing one being followed; `V19` is the newest migration, so `V20` is free; `AuditLoggerService.log(UserContext, AuditActionType, Map)` exists with the stated signature
- All 70 FRs are defined exactly once in the spec with no numbering gaps, and every `SD-FR-nn` referenced anywhere in the set is defined — checked by script, not by eye
- All 70 FRs appear in the traceability coverage table — no orphans
- 126 of 127 `docs/...` cross-references resolved; the one failure (a bare `AGENT_HANDOFF.md` in the tasks doc) was fixed
- Targeted contradiction sweep for the six repaired rules returns zero surviving hits (no "no bespoke conflict code", no "renamed, reordered", no loose `/wwa/` prefix wording, no link tie-break by `key`, and the only two remaining mentions of 422 are the statements that this slice never returns it)

Blockers found and fixed across the two passes: the Critical concurrency defect and five Majors described above, plus undefined `SD-NFR-xx` ids in the spec, inconsistent `OQ-` vs `SD-OQ-` naming, a `sortOrder` default that could exceed its own range, a manual-test instruction that would have failed for the wrong reason, three unresolvable doc paths, a wrong FR id in the traceability matrix, two off-by-one code citations, and a false claim that `PROJECT_RULES.md` carried the stale package.

---

## Session close checklist (outgoing agent)

Before stopping, update this handoff:

- [x] Status snapshot date + updater
- [x] Done / Next / Blockers reflect reality
- [x] Sources of truth paths still valid
- [ ] Mention branch/commit if known — nothing committed this session by design
- [ ] Point to any new execution manifest — none created; the tasks document plus this handoff are sufficient for a local agent. Create one under `docs/00-context/execution-manifests/` before handing implementation to a remote or async agent

Optional: archive a frozen copy under `docs/00-context/handoffs/{slice}-{YYYYMMDD}.md` when a major phase completes.
