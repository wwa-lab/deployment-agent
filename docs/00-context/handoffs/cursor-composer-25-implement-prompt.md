# Cursor · Composer 2.5 — Implement Service Directory (all tasks)

Paste the fenced block into a **new Cursor Agent session** with model **Composer 2.5**.

```text
Implement slice `service-directory` end-to-end in one continuous run. Do not use chat memory — use durable docs only.

## Start (mandatory)
1. Read `docs/00-context/AGENT_HANDOFF.md`
2. Read `PROJECT_RULES.md` and `DEVELOPMENT_STANDARDS.md`
3. Treat these as authority (do not invent behavior):
   - Spec: `docs/03-spec/service-directory-spec.md`
   - Design: `docs/05-design/service-directory-design.md`
   - API: `docs/05-design/contracts/service-directory-API_IMPLEMENTATION_GUIDE.md`
   - Tasks (execute ALL): `docs/06-tasks/service-directory-tasks.md`
   - Architecture / data model / data flow: `docs/04-architecture/service-directory-*`
   - Traceability: `docs/00-context/service-directory-traceability.md`
   - UX reference only: `docs/prototypes/wwa-service-directory.html`

English-only docs (ADR-0009). No `.zh-CN.md` companions.

## User acceptance (treat as given)
- Regenerated Service Directory SDD is **accepted**.
- SD-T00: Option A — single versioned JSON document in `DA_SERVICE_DIRECTORY_CATALOG`.
- SD-T01: Guests may **read**; guest writes blocked by existing `GuestReadOnlyFilter`.
- SD-T02: Keep `.invalid` / "URL pending" placeholders for ARCAD / GitHub Enterprise.
- SD-T03: Flip `ADR-0010` from `Proposed` → `Accepted` and update ADR index.
- SD-T70: **Skip** fixing stale `CLAUDE.md` / `AGENTS.md` package paths unless compile fails and a minimal fix is required.

## Mission
Ship the full Platform capability:
- Route `/wwa/service-directory`
- Platform REST API per the API guide
- Flyway V20 + entity/converter/repository/service/seed
- DEVOPS_ADMIN manage + audit (`service_directory_update` / `service_directory_delete` as specified)
- Frontend: filters, stage rail, catalog, Recently used, Manage dialogs
- Tests, CHANGELOG, greenfield Oracle DDL sync if migration added
- Update handoff + traceability at the end

Workstream order from tasks:
**W0 → W1 → W2 → W3 → (W4 ∥ W5) → W6 → W7 → W8**

Complete every in-scope SD-T* item. People-only URL collection beyond placeholders can remain open.

## Hard constraints
- Java package base: `com.wwa.agenthub` (NOT `com.wwa.deploymentagent`)
- Catalog MUST NOT use ConfigurationComponent / ConfigurationItem / ScopeDirectoryEntry
- Platform-shared only — no `frontend/src/agents/` workspace
- Server enforces `DEVOPS_ADMIN` on mutations; never trust client role
- Audit admin mutations with `actor_kind = HUMAN`; do not audit browse / Recently used
- Optimistic concurrency via catalog `version`
- One `platformCapabilities` entry feeds flyout + Home Shared Controls
- Respect HITL safety rails in `CLAUDE.md`
- Do not expand scope beyond accepted SDD
- Do not commit unless I explicitly ask
- Do not modify `.env`, lockfiles, or CI secrets

## Verification (required before claiming done)
- `mvn test` (include new controller/contract tests)
- `cd frontend && npm run build`
- If V20 migration added: regenerate `docs/sql/ORACLE_CURRENT_SCHEMA.sql`
- Update CHANGELOG for the user-facing page
- Update `docs/00-context/AGENT_HANDOFF.md` last (status, done, verification, risks)
- Update `docs/00-context/service-directory-traceability.md` implementation status

## Working style
- Mirror existing Platform patterns (controllers, audit, Flyway, Pinia/workspace style)
- If code reality conflicts with SDD, stop and report — do not silently change the spec
- Prefer finishing a coherent vertical slice over leaving half-wired stubs
- Keep changes scoped; no drive-by refactors

## First actions
1. `git status` + locate existing Platform / audit / Flyway / frontend registry patterns
2. Ratify W0 (ADR-0010 Accepted + index)
3. Implement W1…W8 against the task list and API guide
4. Run verification; fix failures
5. Final report: tasks done/skipped, key files, command results, residual risks, handoff updated

Start now.
```
