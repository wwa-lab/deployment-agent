# Service Directory Traceability

**Slice:** `service-directory`
**Date:** 2026-07-25
**Status:** Regenerated via the `wwa-sdd-generate-all` skill chain, then reviewed and repaired — SDD complete, **awaiting user acceptance**, not implemented
**Language:** English-only (ADR-0009)

This document is the single index for the slice: which documents exist, how every requirement traces
forward to code-level tasks, and what is still open.

---

## Slice Contract

| Field | Value |
|---|---|
| Slice | `service-directory` |
| Goal | One Platform page that answers "where do I go?" for SDLC tooling, shared platforms, and external systems, driven by admin-maintained configuration rather than code |
| Scope (in) | Route `/wwa/service-directory`; `directory scopes → groups → links` catalog; scope / kind / stage / text filters; browser-local Recently used; `DEVOPS_ADMIN` manage with audit; dedicated persistence + Platform REST API; seed catalog |
| Scope (out) | Storage inside Configuration Management entities; link health probes; auto-discovery; server-side Recently used; iframe embedding; per-user favourites; the prototype's mock role switch |
| Prototype (accepted UX baseline) | `docs/prototypes/wwa-service-directory.html` |
| Behavior source of truth | `docs/03-spec/service-directory-spec.md` |
| Execution source of truth | `docs/06-tasks/service-directory-tasks.md` |
| Decision record | `docs/00-context/decisions/ADR-0010-service-directory-owns-its-catalog-store.md` (Proposed) |
| Verification | `mvn test`, `cd frontend && npm run build`, documented manual walkthrough (tasks doc) |

---

## Document Map

| Stage | Path | Status |
|---|---|---|
| Requirements | `docs/01-requirements/service-directory-requirement.md` | Regenerated |
| User stories | `docs/02-user-stories/service-directory-user-stories.md` | Regenerated |
| Spec | `docs/03-spec/service-directory-spec.md` | Regenerated |
| Architecture | `docs/04-architecture/service-directory-architecture.md` | Regenerated |
| Data flow | `docs/04-architecture/service-directory-data-flow.md` | Regenerated (was previously deferred) |
| Data model | `docs/04-architecture/service-directory-data-model.md` | Regenerated |
| Design | `docs/05-design/service-directory-design.md` | Regenerated |
| API guide | `docs/05-design/contracts/service-directory-API_IMPLEMENTATION_GUIDE.md` | Regenerated (was previously deferred) |
| Tasks | `docs/06-tasks/service-directory-tasks.md` | Regenerated |
| Traceability | this document | Regenerated |
| Decision record | `docs/00-context/decisions/ADR-0010-…` | Proposed |

---

## Skill Chain Evidence

| Step | Skill file read | Produced |
|---|---|---|
| 0 | `.agents/skills/wwa-sdd-generate-all/SKILL.md` | Slice contract, chain order, grounding rules |
| 1 | `.claude/skills/req-to-user-story/SKILL.md` | Requirements + user stories |
| 2 | `.claude/skills/user-story-to-spec/SKILL.md` | Spec |
| 3 | `.claude/skills/spec-to-architecture/SKILL.md` | Architecture + data flow |
| 4 | `.claude/skills/architecture-to-design/SKILL.md` | Design + data model + API guide |
| 5 | `.claude/skills/design-to-tasks/SKILL.md` | Tasks |
| 6 | `.claude/skills/review-doc-quality/SKILL.md` | Quality review pass; one Critical and five Major findings fixed (below) |

Supporting: `docs/00-context/checklists/sdd-generation-gate.md`, `docs/SDD-BOOTSTRAP.md`,
`docs/00-context/sdd-profile.md`, `PROJECT_RULES.md`, `DEVELOPMENT_STANDARDS.md`.

### Review findings fixed after generation

The `review-doc-quality` pass found one Critical and five Major defects in the generated set. All were
repaired in place; the fixes changed behavior in the contract, not just wording, so the summary below is
the record of what the accepted design now says.

| Severity | Finding | Resolution |
|---|---|---|
| Critical | The stale-write requirement (SD-FR-44) was not implementable as designed. It relied on JPA `@Version` alone, which only detects in-flight overlaps — so the requirement's own scenario (two saves minutes apart) would have been a silent lost update, and the contract test as written would still have passed | Added a required `expectedVersion` query parameter on all six updates and deletes, with creates deliberately exempt (SD-FR-67), storage-level locking retained as a second layer (SD-FR-68), and no-merge semantics stated (SD-FR-69). Contract checklist row 12 rewritten as a five-case matrix including the must-not-fire create case. Lesson recorded as LL-2026-07-25f |
| Major | A link move was audited with no record of source or destination, making it indistinguishable from a title edit | Four context keys (`from_scope_key`, `from_group_key`, `to_scope_key`, `to_group_key`), written only on an actual move |
| Major | System scope keys were described as renamable while behavior depended on them | Keys are immutable after create for every scope and group (SD-FR-43); titles remain editable |
| Major | Link `sortOrder` tie-break said `key` in the data model and `title` then `id` in the API guide; links have no key | Aligned on `title` then `id` everywhere, with `id` as the stability discriminator |
| Major | The `workspace` URL rule was a loose `/wwa/` prefix in the spec and an exact pattern in the design | One definition: `^/wwa/[A-Za-z0-9._~\-/]*$`, no query string or fragment |
| Major | Stage focus compared against `group.key` while the model also carried `stageKey`, with no stated winner | `key` must equal `stageKey` for stage groups (SD-FR-51), and at most one scope may be `stage-strip` (SD-FR-70) |
| Minor | 422 listed as a possible status; two off-by-one code citations | 422 removed (it belongs to the import flow only); citations corrected to `GlobalExceptionHandler.java:24-28` and `DeploymentReleaseFlowController.java:277-281` |

**`architecture-review`:** no skill by that name exists in `.agents/skills/`, `.claude/skills/`,
`~/.codex/skills/`, or `~/.agents/skills/`. The review was performed instead against the quality
checklist inside `spec-to-architecture` plus the architecture gate in the SDD profile. Result recorded
in the completion report and in `docs/00-context/lessons-learned.md`.

---

## Requirement → Story → Spec → Task Trace

| Requirement | Stories | Spec FR | Design unit | Tasks |
|---|---|---|---|---|
| SD-REQ-01 — Route + flyout + Home entry | SD-US-01 | SD-FR-01 … 05 | F9 | SD-T51, SD-T53 |
| SD-REQ-02 — Config-driven rendering, no hard-coded catalog | SD-US-02 | SD-FR-06 … 10 | M1, M3, F3, F5 | SD-T10, SD-T21, SD-T50, SD-T53 |
| SD-REQ-03 — Four link kinds, per-kind open behavior | SD-US-03 | SD-FR-24 … 26 | F8 | SD-T11, SD-T53 |
| SD-REQ-04 — Seven SDLC stage groups with order + agent | SD-US-03 | SD-FR-11 … 13, 17 … 20 | M1, F5 | SD-T11, SD-T22, SD-T53 |
| SD-REQ-05 — Scope / kind / stage / text filtering | SD-US-02, SD-US-03 | SD-FR-14 … 16, 21 … 23 | F5 | SD-T53 |
| SD-REQ-06 — Seed catalog | SD-US-04 | SD-FR-27, 60 … 62 | M5 | SD-T22, SD-T02, SD-T60 |
| SD-REQ-07 — `DEVOPS_ADMIN`-only mutations, enforced server-side | SD-US-06 | SD-FR-36 … 40 | M6, F5, F6 | SD-T30, SD-T54 |
| SD-REQ-08 — Server-side persistence + audit per mutation | SD-US-06, SD-US-07 | SD-FR-41 … 44, 53 … 59 | M1, M3, M7 | SD-T10, SD-T13, SD-T21, SD-T31 |
| SD-REQ-09 — Recently used, browser-local, max 8, clearable | SD-US-05 | SD-FR-28 … 35 | F4 | SD-T52, SD-T53 |
| SD-REQ-10 — Dedicated store, never Configuration Management | SD-US-08 | SD-FR-63, 64 | M1, ADR-0010 | SD-T00, SD-T03, SD-T13, SD-T40 |
| SD-REQ-11 — Reject unsafe / malformed link targets | SD-US-06 | SD-FR-46 … 52 | M4 | SD-T20, SD-T40, SD-T54 |
| SD-REQ-12 — System scopes protected from deletion, keys fixed | SD-US-06 | SD-FR-43 | M3, M4 | SD-T20, SD-T21, SD-T40, SD-T54 |
| SD-REQ-13 — Guest read allowed, mutations blocked `[DEFAULT]` | SD-US-01 | SD-FR-65 | M6 | SD-T01, SD-T40 |
| SD-REQ-14 — Manual alignment with Agent Contribute Dashboard | SD-US-08 | SD-FR-66 | Integration design | SD-T60 |

Non-functional requirements are defined as `SD-NFR-01` … `SD-NFR-10` in
`docs/01-requirements/service-directory-requirement.md` §5 and restated with per-item verification in
`docs/03-spec/service-directory-spec.md` §"Non-Functional Requirements", which adds one spec-introduced
item, `SD-NFR-11` (observability). They are verified through SD-T40 (contract tests), SD-T53
(client-side filtering performance and accessibility), SD-T52 (storage resilience), and SD-T62 (full
verification run).

---

## Full FR Coverage

The table above traces by requirement. This one traces the other direction — every one of the spec's 66
functional requirements to the design unit that realises it and the task that delivers it — so no FR can
be silently dropped during implementation. Design unit ids (`M*` backend, `F*` frontend) are the section
ids in `docs/05-design/service-directory-design.md`.

| Spec FR | Topic | Design unit | Task |
|---|---|---|---|
| SD-FR-01 … 05 | Route, flyout entry, Home card, auth guard, single registry | F9 | SD-T51 |
| SD-FR-06 … 10 | Whole-catalog read, config-driven structure, role projection, deterministic order | M3, M6, F3 | SD-T21, SD-T30, SD-T50 |
| SD-FR-11 … 13 | Scope layout, group type, fixed kind sub-heading order | M1, F5 | SD-T11, SD-T53 |
| SD-FR-14 … 16 | Scope filter, kind filter, search field set (URLs excluded) | F5 | SD-T53 |
| SD-FR-17 … 20 | Stage rail visibility, stage focus, focus toggle-off, focus clearing | F5 | SD-T53 |
| SD-FR-21 … 23 | Empty-group hiding, no-match empty state, `/` shortcut | F5 | SD-T53 |
| SD-FR-24 … 26 | In-app workspace navigation, protected new tab, kind-derived open behavior | F8 | SD-T53 |
| SD-FR-27 | Pending `.invalid` links non-activatable | M5, F8 | SD-T22, SD-T53 |
| SD-FR-28 … 35 | Recently used: record, cap, de-duplicate, browser-local, empty default, clear, self-heal, never audited | F4 | SD-T52, SD-T53 |
| SD-FR-36 … 40 | Admin-only affordances, server-side role check, full CRUD, manage-mode empty groups, key uniqueness | M4, M6, F5, F6 | SD-T20, SD-T30, SD-T54 |
| SD-FR-41 … 43 | Scope cascade delete with counts, group cascade delete, system-scope protection, key immutability | M3, M4, F7 | SD-T20, SD-T21, SD-T54 |
| SD-FR-44, 45 | `expectedVersion` precondition on updates and deletes, mutation returns updated catalog and its new version | M3, M6, F3 | SD-T21, SD-T30, SD-T40, SD-T50 |
| SD-FR-46 … 52 | Kind membership, per-kind URL shape, scheme denial, key pattern, length and range limits, stage-key set and `key == stageKey`, server authority | M4, F6 | SD-T20, SD-T54 |
| SD-FR-53 … 59 | One entry per successful mutation, dedicated action types, entity identity in context, cascade summary, no entry on failure, audit-failure isolation, reads never audited | M7 | SD-T31 |
| SD-FR-60 … 62 | Seed once, idempotent, `.invalid` placeholders | M5 | SD-T22, SD-T02 |
| SD-FR-63, 64 | Dedicated store boundary, Platform-shared endpoints with no agent parameter | M1, M6, ADR-0010 | SD-T13, SD-T30, SD-T40 |
| SD-FR-65 | Guest read allowed, guest writes blocked | M6 | SD-T01, SD-T40 |
| SD-FR-66 | Manual content alignment with the Agent Contribute Dashboard | Integration design | SD-T60 |
| SD-FR-67 … 69 | Creates exempt from the version precondition, storage-level locking as the second layer, 409 carries no field detail and is never merged | M3, M6, F3 | SD-T21, SD-T30, SD-T40, SD-T50 |
| SD-FR-70 | At most one `stage-strip` scope | M4 | SD-T20, SD-T40 |

---

## Task → Verification Trace

| Task group | Verification |
|---|---|
| SD-T10 … SD-T14 (persistence, migration, schema) | `mvn test`; `docs/sql/ORACLE_CURRENT_SCHEMA.sql` contains the new table |
| SD-T20 … SD-T22 (validator, service, seed) | `mvn test -Dtest=ServiceDirectoryControllerTest` |
| SD-T30, SD-T31 (API, audit) | `mvn test`; Audit Log shows one entry per mutation |
| SD-T40 (contract tests) | 14-row checklist in the API guide, all asserted |
| SD-T50 … SD-T54 (frontend) | `cd frontend && npm run build`; manual walkthrough, before/after screenshots |
| SD-T60 … SD-T63 (hardening) | CHANGELOG entry; this document; `AGENT_HANDOFF.md` updated last |

---

## Corrections Applied During The Grounding Pass

The earlier draft asserted these incorrectly; the regenerated set fixes them.

| # | Draft claim | Reality (verified) |
|---|---|---|
| 1 | Java base package is `com.wwa.deploymentagent` | It is `com.wwa.agenthub`. `CLAUDE.md` (9 occurrences) and `AGENTS.md` (7) still state the old name; `PROJECT_RULES.md` does not — SD-T70, needs user approval to edit |
| 2 | Free-text search also matches link URLs | Search matches display text only (spec §7.3) |
| 3 | Audit can reuse `config_update` / `config_delete` | Rejected; the slice adds `service_directory_update` / `service_directory_delete` |
| 4 | `openInNewTab` is a stored field | Derived client-side from link kind |
| 5 | Guest visibility is "policy TBD" | Stated as a committed default (read allowed) with an explicit open question, so acceptance can ratify or reverse it |
| 6 | `AuditLogEntry.target_type` / `target_id` are usable for entity identity | Nothing writes them and the DTO does not expose them; identity goes in the audit context payload instead |
| 7 | Two navigation registrations needed (flyout + Home) | One `platformCapabilities` entry feeds both surfaces |
| 8 | Data flow and API guide could be deferred | Both are in scope for this slice and are now present |

---

## Governance Notes

- English-only SDD and project rules (ADR-0009); no `.zh-CN.md` companions.
- Full `wwa-sdd-generate-all` skill chain was re-run for this regeneration — unlike the original draft.
- `ADR-0010` records the catalog store boundary and is `Proposed` pending acceptance (spec SD-OQ-04).
- HITL safety rails in `CLAUDE.md` are untouched: catalog mutations are ordinary admin writes, always
  performed by an interactive human with `actor_kind = HUMAN`, never auto-approved.
- Lessons from this regeneration are recorded in `docs/00-context/lessons-learned.md`.

---

## Open Questions

| # | Question | Blocking | Owner |
|---|---|---|---|
| SD-OQ-01 | Guest read allowed, or redirect? Default: allowed. | Blocks implementation start (cheap now, visible change later) | Product / Security |
| SD-OQ-02 | Production ARCAD / GitHub Enterprise URLs. | Blocks release readiness, not development | Ops / Platform |
| SD-OQ-03 | Shared source for SDLC guideline links with the Agent Contribute Dashboard? Default: manual alignment. | No | Product |
| SD-OQ-04 | Must `ADR-0010` be accepted before implementation? Default: yes. | Blocks implementation start | Architecture |
| SD-OQ-05 | Approve SD-T70 (fix base-package drift in `CLAUDE.md` and `AGENTS.md`)? | No | User |

`SD-OQ-01` … `SD-OQ-04` originate in the requirement and are carried through the spec. `SD-OQ-05` is
raised by the tasks document: it is out-of-slice governance housekeeping surfaced by the grounding pass,
not a Service Directory scope question.

---

## Resume Point

- **Now:** user reviews and accepts the regenerated SDD set.
- **Then:** ratify SD-T00 … SD-T03 / SD-OQ-01 … SD-OQ-04, flip `ADR-0010` to `Accepted`, and start W1 (persistence) from `docs/06-tasks/service-directory-tasks.md`.
- **Active handoff:** `docs/00-context/AGENT_HANDOFF.md`
