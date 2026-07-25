# WorkBuddy · Kimi-K3 — Implement Service Directory (all tasks)

Paste the fenced block into WorkBuddy with model **Kimi-K3**.

User has accepted the regenerated SDD and wants one continuous implementation pass covering the task list.

```text
You are implementing slice `service-directory` in the WWA Agent Hub repo.

## Authority (read in this order — do not invent behavior)
1. `docs/00-context/AGENT_HANDOFF.md`
2. `PROJECT_RULES.md`
3. `DEVELOPMENT_STANDARDS.md`
4. Behavior SoT: `docs/03-spec/service-directory-spec.md`
5. Design: `docs/05-design/service-directory-design.md`
6. API contract: `docs/05-design/contracts/service-directory-API_IMPLEMENTATION_GUIDE.md`
7. Executable checklist: `docs/06-tasks/service-directory-tasks.md`  ← implement EVERY task in order
8. Data model / data flow / architecture under `docs/04-architecture/service-directory-*`
9. Prototype UX reference (visual/interaction only): `docs/prototypes/wwa-service-directory.html`
10. Traceability: `docs/00-context/service-directory-traceability.md`

English-only docs (ADR-0009). Do not create `.zh-CN.md` files.

## User acceptance (treat as given for this run)
- Regenerated Service Directory SDD set is **accepted**.
- SD-T00: Option A — single versioned JSON row in `DA_SERVICE_DIRECTORY_CATALOG` (per data model + ADR-0010).
- SD-T01: Guests may **read**; guest writes remain blocked by existing `GuestReadOnlyFilter`.
- SD-T02: Keep seed `.invalid` / "URL pending" placeholders for ARCAD / GitHub Enterprise until real URLs exist.
- SD-T03: Flip `ADR-0010` from `Proposed` → `Accepted` and update the ADR index.
- SD-T70: **Skip unless trivial and clearly required for compile** — do NOT rewrite stale `CLAUDE.md` / `AGENTS.md` package paths in this pass unless a compile/runtime failure forces a minimal fix. Prefer leaving SD-T70 for a follow-up.

## Mission
Implement the full Service Directory feature end-to-end in one continuous run:
Platform page `/wwa/service-directory`, Platform REST API, Flyway V20 + entity store, seed, DEVOPS_ADMIN manage + audit, frontend filters / Recently used / Manage UI.

Follow workstream order from tasks:
W0 → W1 → W2 → W3 → (W4 ∥ W5) → W6 → W7 → W8

Complete every SD-T* task that is in scope for implementation (skip people-only waiting on external URL collection beyond placeholders). Mark tasks done in your final report.

## Hard constraints
- Java base package is `com.wwa.agenthub` (NOT `com.wwa.deploymentagent`).
- Catalog MUST NOT use ConfigurationComponent / ConfigurationItem / ScopeDirectoryEntry.
- Platform-shared only — no agent workspace under `frontend/src/agents/`.
- Server enforces `DEVOPS_ADMIN` on mutations; never trust client role.
- Audit mutations with new action types from the SDD (`service_directory_update` / `service_directory_delete` — follow the accepted API guide / design, not old draft guesses).
- `actor_kind = HUMAN` for admin mutations; do not audit Recently used / browse.
- Optimistic concurrency via catalog `version` as specified.
- Shared components stay agent-agnostic; one `platformCapabilities` entry for flyout + Home.
- Respect HITL safety rails in `CLAUDE.md`.
- Do not expand scope beyond the accepted SDD.
- Do not commit unless I explicitly ask.
- Do not modify `.env`, lockfiles, or CI secrets.

## Verification (must run before claiming done)
- Backend: `mvn test` (and focused controller/contract tests from the API guide / SD-T* test tasks).
- Frontend: `cd frontend && npm run build`
- If Flyway V20 added: regenerate `docs/sql/ORACLE_CURRENT_SCHEMA.sql` per project rules.
- Update CHANGELOG for user-facing page (W8).
- Update `docs/00-context/AGENT_HANDOFF.md` last: status, done tasks, remaining risks, verification evidence.
- Update `docs/00-context/service-directory-traceability.md` with implementation status.

## Working style
- Prefer small, coherent commits only if I later ask to commit; until then keep a clean working tree of complete feature slices.
- When a task conflicts with code reality, stop and report the mismatch — do not silently change the spec.
- If blocked, record blocker in AGENT_HANDOFF and continue with unblocked tasks only when safe.

## First actions
1. Read the authority docs above.
2. Run `git status` and locate existing Platform / audit / Flyway patterns to mirror.
3. Execute W0 ratifications (ADR-0010 Accepted + index).
4. Implement W1…W8 against the task list and API guide.
5. Run verification commands; fix failures.
6. Write a completion report: tasks done / skipped, files changed, commands run + pass/fail, residual risks, AGENT_HANDOFF updated.

Start now.
```
