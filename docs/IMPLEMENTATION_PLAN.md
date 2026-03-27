# Deployment Agent — Implementation Plan

**Last Updated**: 2026-03-24
**Branch**: `develop-leo`
**Stack**: Java 21 / Spring Boot 3.2.4 / Spring Data JPA / Oracle + H2 (test) / Vue 3 / Vite / Pinia / Vue Router / Axios
**Reference Documents**:
- `docs/06-tasks/tasks.md` — implementation task source of truth
- `docs/05-design/design.md` — detailed design baseline
- `docs/05-design/contracts/API_IMPLEMENTATION_GUIDE.md` — logical API contract

---

## 1. Delivery Objective

This implementation plan reflects the **current baseline plus next-phase delivery work**, not the old “build MVP from scratch” framing.

The immediate objective is to:
- preserve the current deployment workflow baseline
- introduce **deny-by-default** product access plus scoped visibility using **Access Grants**
- add **Access Management** for `DEVOPS_ADMIN`, including scope grants and scoped administration
- align backend API authorization, frontend route/menu behavior, runtime scope handling, and auditability with the new permission-and-scope model

This plan supersedes the earlier “all phases complete” interpretation that no longer matches the updated product, spec, architecture, design, and task documents.

---

## 2. Current Baseline

The following capability areas are treated as the implemented baseline for planning:

- session-based Team Book login with local/dev stub support
- deny-by-default Access Grant resolution with compatibility `role` plus `roles[]` / `permissions[]` / `scopes[]` auth payloads
- non-production bootstrap grants for known stub users and configured admin IDs
- Excel upload and Release Flow / Request / Task import
- Release Flow summary/detail views with `Application / SNOW Group / Agent` scope context and rundown owner visibility
- stage-level rundown editing plus archive / restore / purge lifecycle
- request-level rundown control actions limited to rundown owner or `DEVOPS_ADMIN`
- MANUAL result recording and AUTO fire-and-forget submission
- task review decisions (`Approve`, `Reject`, `Rerun`, `Skip`)
- dependency visibility (`Blocked By` / `Blocks`) in relevant views
- configuration management
- audit log viewing with scope-aware filtering and visibility
- state-driven task action UX
- Access Management backend APIs and admin UI with scope grants

**Not treated as complete for planning**
- unified effective-permission enforcement across frontend and backend
- full backend-enforced scope isolation for every remaining surface, especially configuration and template management
- production Team Book adapter and rollout-safe admin bootstrap
- full contract/integration/E2E verification

---

## 3. Locked Design Rules

The following rules are already fixed by the current design baseline and should not be reopened unless upstream docs change:

1. Team Book authenticates enterprise identity; Deployment Agent authorizes product access.
2. Product authorization in Phase 1 is based on local **Access Grants** plus `Application + SNOW Group` scope grants.
3. Product entry becomes **deny-by-default** once Access Management is introduced.
4. Archive / restore / purge is a rundown lifecycle capability, not part of Access Management.
5. AUTO execution remains **fire-and-forget** in the current baseline; callback-based completion is deferred.
6. Existing workflow state transitions, rerun behavior, and execution-history preservation remain intact.
7. Dependency visibility remains informational in MVP and does not yet become an authoritative DAG execution engine.
8. Frontend menus/routes and backend APIs must converge on the same effective-permission-and-scope model.

---

## 4. Phase Status Summary

| Phase | Name | Status | Notes |
|-------|------|--------|-------|
| Baseline | Existing workflow baseline | Implemented | Current upload, workflow, archive lifecycle, config, and audit capabilities are the planning baseline |
| Phase 1A | Authorization foundation | Implemented | Access Grant persistence, permission mapping, login/session resolution, and scoped auth context are in the workspace; production bootstrap rules still need confirmation |
| Phase 1B | Access Management backend | Implemented | Access Grant APIs, scope grants, scoped visibility, and audit events are in place; deeper legacy authorization cleanup is still partial |
| Phase 1C | Frontend authorization alignment | Partial | user store, major menu/action alignment, and multi-scope runtime UX exist; hard route guards and broader denied-state UX are not finished |
| Phase 1D | Access Management UI | Implemented | admin list/search, create/edit, suspend/reactivate, and scope-grant editing are available in the workspace |
| Phase 1E | Verification and rollout | Partial | focused backend auth/access tests and frontend build pass; broader contract/E2E coverage and rollout readiness remain |
| Follow-up | Deferred / optional next work | Deferred | enterprise directory search scope, AUTO completion ingestion design |

---

## 5. Phase Plan

### Phase 1A — Authorization Foundation

**Goal**
- Establish the data and contract layer required for deny-by-default product entry and scoped visibility authorization.

**Primary tasks**
- `TASK-001` Add Access Grant persistence model
- `TASK-002` Define effective permission mapping
- `TASK-003` Implement deny-by-default login and session resolution
- `TASK-004` Define initial admin bootstrap strategy

**Exit criteria**
- Access Grant entity/model and migration exist
- backend can distinguish `authorized`, `not granted`, and `suspended`
- auth/session payload contract is agreed and implemented, including `scopes[]`
- first-admin bootstrap path is defined and testable

### Phase 1B — Access Management Backend

**Goal**
- Expose admin-safe APIs and auditability for grant lifecycle operations and scoped visibility enforcement.

**Primary tasks**
- `TASK-005` Build Access Management APIs
- `TASK-006` Expand audit model for access governance
- `TASK-007` Align existing API authorization with effective permissions
- `TASK-008` Implement production Team Book adapter (`Should`, external dependency)

**Exit criteria**
- Access Management endpoints exist, are role-gated, and can manage scope grants
- grant lifecycle writes produce audit records
- release and audit visibility enforce scope boundaries, and existing APIs no longer rely on fragmented legacy role assumptions
- production Team Book path is either implemented or explicitly blocked by external contract

### Phase 1C — Frontend Authorization Alignment

**Goal**
- Make frontend behavior consistent with the new product authorization and scoped-visibility contract.

**Primary tasks**
- `TASK-009` Upgrade frontend user store contract
- `TASK-010` Add route guards and access-denied UX
- `TASK-012` Align existing frontend actions with effective permissions

**Exit criteria**
- frontend can consume access-aware auth context, including `scopes[]`
- route access is blocked consistently
- denied-state messaging distinguishes auth failure vs missing/suspended access
- existing menus and pages reflect effective permissions and scope visibility without regressing current archive/admin flows

### Phase 1D — Access Management UI

**Goal**
- Deliver the admin-facing product access console.

**Primary tasks**
- `TASK-011` Build Access Management page

**Exit criteria**
- `DEVOPS_ADMIN` can list/search grants
- `DEVOPS_ADMIN` can create, edit, suspend, and reactivate grants
- page exposes the agreed operational fields:
  - employee ID
  - display name
  - status
  - roles
  - scope grants
  - last login
  - updated by / updated at

### Phase 1E — Verification and Rollout

**Goal**
- Ship the authorization model safely and verifiably.

**Primary tasks**
- `TASK-013` Add contract tests for auth and Access Management APIs
- `TASK-014` Add backend tests for grant lifecycle and permission enforcement
- `TASK-015` Add frontend integration and E2E coverage
- `TASK-016` Add access-denial and grant-change observability
- `TASK-017` Plan and execute Phase 1 rollout

**Exit criteria**
- contract, integration, and UI/E2E coverage exists for the new access model
- observability exists for access denial and admin changes
- rollout plan includes seeded admins, rollback guidance, and support handling for locked-out users

### Follow-up Work (Not on Critical Path)

**Tasks**
- `TASK-018` Evaluate enterprise directory search for Access Management
- `TASK-019` Design follow-up for AUTO completion ingestion

**Interpretation**
- Important, but intentionally not blocking the Phase 1 Access Management release path.

---

## 6. Critical Path

```text
TASK-001
 -> TASK-002
 -> TASK-003
 -> TASK-004
 -> TASK-005
 -> TASK-009
 -> TASK-010
 -> TASK-011
 -> TASK-012
 -> TASK-013 / TASK-014 / TASK-015
 -> TASK-017
```

### Parallelizable Streams

- `TASK-006` can run alongside `TASK-007` after the Access Management API model is stable
- `TASK-008` can proceed in parallel with frontend work once the auth/session contract is defined
- `TASK-016` can proceed during test implementation
- `TASK-018` and `TASK-019` remain outside the critical path

---

## 7. Key Dependencies

### Internal Dependencies

- `docs/06-tasks/tasks.md` remains the detailed task-level planning source
- auth/session contract is fixed; remaining frontend alignment depends on using `role`, `roles[]`, `permissions[]`, and `scopes[]` consistently
- bootstrap approach must be finalized before deny-by-default can be safely enabled

### External Dependencies

- Team Book production API contract
- decision on whether a later phase should expand Access Management beyond existing grants

---

## 8. Risks and Planning Watchpoints

| Risk | Impact | Mitigation |
|------|--------|------------|
| Legacy workflow surfaces still mix role checks with permission checks | Backend and frontend authorization can drift | Continue centralizing permission evaluation and recheck older endpoints |
| Some surfaces still expose multi-scope UX without equally strong backend isolation | Users may infer stronger scope guarantees than the backend currently enforces | Prioritize backend convergence for template/config scope rules before broad rollout |
| No safe first-admin bootstrap path | Product lockout on rollout | Treat bootstrap as a blocking deliverable, not a nice-to-have |
| Team Book production contract is delayed | Production auth path blocked | Keep stub/local path intact; isolate adapter work |
| Access Management search scope expands after the MVP | API/UI scope grows unexpectedly | Treat enterprise directory lookup as a separately scoped follow-up |
| Existing APIs contain scattered legacy role checks | Authorization inconsistency | Centralize permission evaluation in Phase 1B |

---

## 9. Verification Gates

The implementation should not be considered ready for rollout until the following gates are satisfied:

### Backend Gates

- Access Grant persistence and migration validated
- login/session behavior validated for:
  - valid access
  - no grant
  - suspended grant
- Access Management APIs validated for:
  - create
  - update
  - suspend
  - reactivate
- existing sensitive APIs rechecked under effective permissions
- release and audit scope filtering validated for scoped vs global admins
- rundown control actions validated for rundown owner vs admin vs non-owner

### Frontend Gates

- session restore supports the new auth payload
- route and menu restrictions behave consistently
- access-denied states render correctly
- Access Management page completes core admin lifecycle actions

### Audit / Observability Gates

- access-governance events appear in audit history
- denied-entry and admin-change diagnostics are available without exposing secrets

### Rollout Gates

- first-admin bootstrap is proven in non-prod
- rollback plan exists if deny-by-default is enabled before grants are seeded correctly
- support path exists for users who are authenticated but not granted product access

---

## 10. Implementation Readiness Summary

| Area | Status | Notes |
|------|--------|-------|
| Existing workflow baseline | Ready | Serves as the current implementation baseline |
| Phase 1 authorization foundation | Implemented | Access Grant model, permission mapping, login/session resolution, scoped auth context, and non-prod bootstrap are in place |
| Access Management APIs | Implemented | Admin grant lifecycle APIs, scope grants, and audit events are available |
| Frontend permission alignment | Partial | Auth payload and major UI surfaces are aligned; route hardening and full scope convergence remain |
| Access Management UI | Implemented | Admin grant management workspace with scope-grant editing is available |
| Scoped runtime / audit visibility | Partial | Release and audit surfaces enforce scoped visibility; template/config remain lighter-weight for now |
| Production Team Book adapter | Partially blocked | Requires external API contract |
| Rollout readiness | Not ready | Requires bootstrap, verification, and support plan |

---

## 11. Immediate Next Actions

1. Finish route-level permission hardening and access-denied UX (`TASK-010`, `TASK-012`).
2. Continue aligning legacy controller/service authorization with effective permissions and scope rules (`TASK-007`).
3. Expand verification beyond focused auth/access coverage (`TASK-013`, `TASK-015`).
4. Finalize production bootstrap and Team Book adapter rollout details (`TASK-004`, `TASK-008`).
5. Decide whether the next iteration should deepen backend scope isolation for Template / Configuration and introduce an execution-target catalog.

---

## 12. Planning Note

This document is intentionally a **sequencing and readiness plan**, not a historical completion ledger.
Detailed task ownership, dependencies, and execution-level granularity remain in `docs/06-tasks/tasks.md`.
