# Agent Handoff (Active)

> **Read this file first** in every new IDE / agent / chat session before doing product or SDD work.  
> **Update this file last** before ending a session that made meaningful progress.

Do not resume from chat memory. Durable resume state is: **this handoff** + linked SDD/traceability + optional
execution manifest + `git status`.

Language: English-only for project rules and SDD (ADR-0009).

---

## Status Snapshot

| Field | Value |
|---|---|
| Last updated | 2026-08-07 |
| Updated by | Codex |
| Active slice | `atlas-cli-platform-integration` |
| Phase | Implementation, review, verification, and archive complete |
| Overall status | `complete_verified` |
| Branch | `altas-cli` |
| Baseline commit | `abf3850dee78b13c597f7da2791dd06d201c1a66` |
| Adjacent Atlas CLI source | `fa95065ab193d919032fd6cb1349a6c1fabe30ad` |
| Commit/push status | User authorized commit and push on 2026-08-07; this session is closing the requested operation |

---

## Goal Completed

Implement the platform capabilities required by Atlas CLI while preserving the Platform Core / Agent Module
boundary. Execution, Artifact, Telemetry, idempotency, correlation, Event/Audit, and Review are platform-owned.
Atlas remains the sole Task and Execution state authority; clients may only request legal server-side transitions.

---

## Done (Do Not Redo)

- Added the Agent-neutral `/api/v1/integration` contract for Task discovery, fenced Execution lifecycle,
  keyset history, progress events, Artifact metadata/upload/download, approved inputs, exact-attempt Review, and
  capability/Skill usage aggregation.
- Reused and strengthened `Task`, `TaskExecutionHistory`, `TaskStateMachine`, audit, access, correlation,
  Artifact-adjacent, and Agent Module structures. Agent adapters carry fixed scope only; Platform Core owns the
  shared lifecycle behavior.
- Enforced one active monotonically numbered Execution attempt with Task locks, optimistic versions, active
  fences, legal state transitions, and server-derived actor/time/duration/count facts.
- Added persistent hashed-key idempotency with canonical fingerprints, in-progress conflict handling, stale
  reclaim, replay reauthorization, and atomic latest-attempt fencing.
- Added digest-only Bearer client authentication, exact credential-leak guards, invalid-credential response
  throttling by trusted remote address with valid-client bypass, per-request Access Grant revalidation, Guest
  isolation, ownership/delegation checks, and cross-scope not-found behavior.
- Added immutable Execution Event/Audit evidence with correlation IDs and safe error envelopes.
- Added bounded Artifact BLOB/reference handling with name/path/media/signature/digest validation, exact-client
  transfer admission, quotas, globally ordered locks, external malware/DLP scanning, source/raw-log/secret
  rejection, streaming JSON token/depth/byte budgets, renewal/expiry, legal hold, and bounded retention cleanup.
- Added exact-attempt human Review with stable all-Request-Task lock ordering and legal downstream progression.
- Added database aggregation for call count, success/failure rates, average duration, unique users, and Skill
  version distribution, filterable by team, project, Agent, UTC date, client, capability, and Skill.
- Added the Platform Execution Center with visible-page 10-second polling, Task and Awaiting Review views,
  Execution history, Artifact metadata/download, safe failure/pending-sync state, and usage dashboards.
- Fixed the workspace shell for a full-width 390 × 844 mobile layout and captured before/after desktop/mobile
  browser evidence under `docs/assets/screenshots/`.
- Added Oracle `V21__add_atlas_integration_platform.sql`, synchronized `ORACLE_CURRENT_SCHEMA.sql`, integration,
  migration, security, concurrency, and ArchUnit boundary tests.
- Completed and archived the full English SDD chain, ADR-0011, traceability, execution manifest, CHANGELOG,
  change review, and change archive.
- No LangGraph, local Skill runner, repository scanner, model runtime, or Build-Agent-specific Platform Core
  implementation was introduced.

---

## Sources Of Truth (Read In Order)

1. This file.
2. `PROJECT_RULES.md`, `DEVELOPMENT_STANDARDS.md`, and `docs/00-context/sdd-profile.md`.
3. `docs/00-context/atlas-cli-platform-integration-traceability.md`.
4. `docs/03-spec/atlas-cli-platform-integration-spec.md`.
5. `docs/00-context/decisions/ADR-0011-atlas-integration-is-platform-control-plane.md`.
6. `docs/05-design/atlas-cli-platform-integration-design.md` and its API implementation guide.
7. `docs/06-tasks/atlas-cli-platform-integration-tasks.md`.
8. `docs/00-context/execution-manifests/atlas-cli-platform-integration.yaml`.
9. `docs/00-context/changes/atlas-cli-platform-integration/review.md` and `archive.md`.

The adjacent Atlas CLI source documents remain pinned to commit
`fa95065ab193d919032fd6cb1349a6c1fabe30ad`; their three recorded blob hashes were rechecked at closure.

---

## Verification

| Check | Result |
|---|---|
| `mvn test` | 547 tests; 0 failures, 0 errors, 1 environment-gated Oracle skip |
| `mvn test -Dtest='*ArchitectureTest,*ArchTest,AgentModuleBoundaryTest'` | 15/15 passed |
| `cd frontend && npm test` | 11/11 passed |
| `cd frontend && npm run build` | Passed; `vue-tsc`, Vite, 225 modules transformed |
| Browser smoke | Desktop Operations/Usage and 390 × 844 mobile passed; second visible poll observed; no console error or sensitive sentinel rendered |
| Code review | Passed; no remaining P1/P2 finding |
| Security review | Passed; no remaining P1/P2 finding; 54 focused tests passed during review |
| Manifest | JSON Schema valid; pinned document hashes valid |
| Final static checks | `git diff --check`, high-confidence secret scan, forbidden-runtime scan, and Platform-to-Agent dependency scan passed |

`AtlasOracleMigrationIntegrationTest` is the single intentional skip. It requires an explicitly supplied real
Oracle V20 database and proves Flyway executes exactly V21. H2 migration-contract tests pass locally.

---

## Open Risks / Deployment Prerequisites

- Production must configure digest-only Integration client descriptors. There is no default production bearer
  credential.
- Production must provide the external fail-closed malware/DLP scanner bean for Artifact intake.
- Run the gated V20-to-V21 test against a disposable real Oracle environment before production rollout.
- Preserve unrelated/untracked `.codegraph/daemon.pid`; it is intentionally excluded from the commit.
- The older Resource Center slice still has its unrelated manual UAT/production-URL follow-up; this Atlas change
  does not modify that operational follow-up.

No implementation blocker remains.

---

## Next Actions

1. Review the working-tree diff and browser screenshots.
2. Run the real-Oracle migration test when a disposable V20 environment is available.
3. Configure production client digests and the external Artifact scanner before rollout.
4. Commit and push the verified Atlas integration change as requested; retain `.codegraph/daemon.pid` outside the commit.
