# Implementation Task Breakdown

## Overview

This document breaks the updated Deployment Agent design into implementation-ready tasks for the next delivery phase. The focus is no longer “build the MVP from zero”; it is to align the current MVP with the implemented Access Management, scoped-visibility, multi-scope runtime, and rundown-owner direction already present in the workspace, and make the remaining gaps explicit.

**Delivery objective**
- Preserve the current deployment workflow baseline
- Maintain deny-by-default product entry plus scoped visibility through Access Grants
- Support DevOps-admin-managed Access Management, including scope grants and scoped administration
- Align session contracts, route/API authorization, multi-scope runtime behavior, audit coverage, and operational rollout with the updated design

**Planning assumptions**
- Current workflow capabilities such as upload/import, Release Flow monitoring, rundown archive lifecycle, task actions, configuration management, and audit viewing already exist as the implementation baseline.
- Remaining work is now primarily permission hardening, frontend route enforcement, broader verification, and rollout safety around the already-implemented Access Management, release/audit scoped visibility, and rundown-owner controls.
- Callback-based AUTO completion ingestion is not part of the critical path for this phase.

### Current Workspace Alignment

- **Implemented in current workspace**
  - `TASK-001` Access Grant persistence model
  - `TASK-002` effective permission mapping
  - `TASK-003` deny-by-default login and session resolution
  - `TASK-005` Access Management APIs
  - `TASK-006` access-governance audit events
  - scoped visibility enforcement for Release Flow and Audit surfaces
  - multi-scope runtime capture and presentation across upload, release summary/detail, and audit (`Application / SNOW Group / Agent`)
  - rundown owner persistence plus owner/admin-only request-level controls
  - `TASK-009` frontend user store contract upgrade
  - `TASK-011` Access Management page
- **Partially implemented**
  - `TASK-004` bootstrap strategy: local/dev/test bootstrap exists; production rollout rules still need confirmation
  - `TASK-007` API authorization alignment: some existing endpoints still rely on legacy role checks
  - `TASK-010` route guards and denied-state UX: current UI uses menu visibility and page-level guidance more than hard route blocking
  - `TASK-012` existing frontend permission alignment: major surfaces are updated, but full convergence is not finished, especially for template/config scope isolation and hard route enforcement
  - `TASK-014` backend verification: focused auth/access tests exist, but full coverage is not complete
- **Still remaining**
  - `TASK-008`, `TASK-013`, `TASK-015`, `TASK-016`, `TASK-017`, `TASK-018`, `TASK-019`

---

## Source Design

**System name:** Deployment Agent

**Design scope summary**
- The updated design keeps Team Book as the enterprise identity source, adds local Access Grants for product entry plus `Application + SNOW Group` scoped visibility, and preserves the current workflow model for uploads, task execution, review decisions, and rundown lifecycle.
- Phase 1 introduces Access Management, deny-by-default entry, effective-permission-based UI/API enforcement, access-governance auditability, multi-scope runtime context, and rundown-owner controls.
- Existing archive / restore / purge behavior remains separate from Access Management and must continue to work under the new authorization model.

---

## Workstreams

### Major Implementation Streams

1. Authorization foundation and data model
2. Access Management backend APIs and audit coverage
3. Frontend session/route permission alignment
4. Access Management UI and admin workflows
5. Verification, rollout, and operational hardening

### Recommended Sequencing

1. Define Access Grant persistence, permission model, and initial bootstrap strategy
2. Implement backend authorization resolution in login/session flows
3. Add Access Management APIs and audit coverage
4. Upgrade frontend user store, route guards, and access-denied UX
5. Build Access Management UI
6. Tighten authorization across existing workflow/config/audit surfaces
7. Complete contract, integration, and E2E validation
8. Execute rollout and seed first admin access safely

### Parallel Work Opportunities

- UI work can begin once the auth/session contract is stable
- Audit and observability tasks can proceed in parallel with API work
- Team Book production adapter work can run alongside frontend admin UI
- Verification streams can prepare fixtures and test plans before endpoint completion

---

## Task Breakdown by Domain

### Persistence / Data
- Access Grant entity, migration, repository, optimistic-update behavior
- Audit action expansion for access-governance events
- Initial admin bootstrap / seed strategy

### Backend / API
- Login/session authorization resolution
- Access Management endpoints
- Effective-permission enforcement across existing APIs
- Team Book production adapter alignment

### Frontend / UI
- User store upgrade for roles/permissions/access state
- Route guards and access-denied UX
- Access Management page
- Permission alignment for existing navigation and action surfaces

### Security / Reliability / Observability
- Audit coverage for grant lifecycle
- Structured logging / visibility for access denial and admin changes
- Safe rollout under deny-by-default rules

### Testing
- Contract tests for auth and access-grant APIs
- Backend state and audit tests
- Frontend integration and E2E coverage

---

## Task Details

### TASK-001: Add Access Grant Persistence Model
- **Objective**: Introduce the Access Grant data model required for deny-by-default product authorization.
- **Scope**: Add migration(s), persistence entity/model, repository access, status enum, assigned-role representation, and optimistic-update support for grant lifecycle changes.
- **Dependencies**: None
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Include `employee_id`, `display_name_snapshot`, `grant_status`, `assigned_roles`, `scope_grants`, `note`, `last_login_at`, and audit-friendly timestamps.

### TASK-002: Define Effective Permission Mapping
- **Objective**: Convert assigned product roles into a stable effective-permission model that backend and frontend can both consume.
- **Scope**: Define the permission catalog, role-to-permission mapping, and the backend service that resolves effective permissions from one or more assigned roles.
- **Dependencies**: TASK-001
- **Owner type**: backend
- **Priority**: Must
- **Notes**: This task is the contract foundation for route guards, API authorization, and Access Management UX.

### TASK-003: Implement Deny-by-Default Login and Session Resolution
- **Objective**: Enforce product entry through Access Grant resolution after Team Book authentication succeeds.
- **Scope**: Update login/session flows so valid enterprise identity is not enough; resolve active/suspended/missing access state and return the agreed auth payload.
- **Dependencies**: TASK-001, TASK-002
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Includes `POST /auth/login`, `GET /auth/me`, and session restore behavior. Current contract returns a compatibility `role` plus `roles[]`, `permissions[]`, and `scopes[]`.

### TASK-004: Define Initial Admin Bootstrap Strategy
- **Objective**: Ensure the system can be operated safely once deny-by-default is enabled.
- **Scope**: Design and implement the initial-access bootstrap path for first admin(s), including local/dev seed behavior and production-safe initialization rules.
- **Dependencies**: TASK-001, TASK-003
- **Owner type**: platform
- **Priority**: Must
- **Notes**: [ASSUMPTION] This is required even though it is not a user-facing feature; otherwise the first `DEVOPS_ADMIN` cannot enter the product.

### TASK-005: Build Access Management APIs
- **Objective**: Expose admin-only CRUD-lite lifecycle APIs for Access Grants.
- **Scope**: Implement list, create, update, suspend, and reactivate endpoints with validation, pagination/search, lifecycle conflict handling, scope-grant maintenance, and scoped-admin restrictions.
- **Dependencies**: TASK-001, TASK-002, TASK-003
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Include clear access-state error semantics and preserve non-delete lifecycle behavior.

### TASK-006: Expand Audit Model for Access Governance
- **Objective**: Make Access Grant changes first-class audit events.
- **Scope**: Add audit action types, write access-governance events from Access Management flows, and ensure audit retrieval can expose these entries alongside existing workflow actions.
- **Dependencies**: TASK-005
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Cover create, update, suspend, reactivate, scope-grant changes, and related operator/context details.

### TASK-007: Align Existing API Authorization with Effective Permissions
- **Objective**: Remove fragmented role checks and align existing APIs with the new authorization model.
- **Scope**: Review upload, config, audit, task-action, rundown-lifecycle, release visibility, and related endpoints; centralize authorization evaluation so menu/route/API behavior can converge on the same permission-and-scope rules.
- **Dependencies**: TASK-002, TASK-003
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Preserve current archive/restore/purge separation from Access Management; keep request-level rundown controls limited to rundown owner or `DEVOPS_ADMIN`; do not add hidden superuser bypass behavior.

### TASK-008: Implement Production Team Book Adapter
- **Objective**: Replace the current stub-only assumption with a production-ready Team Book integration path.
- **Scope**: Implement the real provider adapter, configuration, failure handling, and environment wiring while preserving the stub provider for local/test use.
- **Dependencies**: TASK-003
- **Owner type**: backend
- **Priority**: Should
- **Notes**: Blocked on external Team Book contract details; local/testing should continue to work without the prod adapter.

### TASK-009: Upgrade Frontend User Store Contract
- **Objective**: Move frontend session handling from single-role assumptions to effective authorization context.
- **Scope**: Update user store/types so the frontend can consume access status, `roles[]`, `permissions[]`, and `scopes[]`, while remaining compatible with the agreed backend auth contract.
- **Dependencies**: TASK-003
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Include graceful handling for access-denied and suspended states.

### TASK-010: Add Route Guards and Access-Denied UX
- **Objective**: Enforce page-level authorization consistently in the frontend.
- **Scope**: Add route metadata, navigation guards, denied-state screens/messages, and menu visibility behavior aligned with effective permissions.
- **Dependencies**: TASK-009
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Must distinguish invalid login from `Access not granted` and `Access suspended`.

### TASK-011: Build Access Management Page
- **Objective**: Provide the admin UI for managing product access.
- **Scope**: Implement Access Management list/search, grant create/edit forms, suspend/reactivate actions, admin feedback states, and integration with the new Access Management APIs.
- **Dependencies**: TASK-005, TASK-009, TASK-010
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Include fields for employee ID, display name, status, roles, scope grants, last login, updated by, and updated at.

### TASK-012: Align Existing Frontend Actions with Effective Permissions
- **Objective**: Ensure existing workflow/config/audit/admin surfaces honor the same authorization contract as the backend.
- **Scope**: Recheck upload entry, configuration management, audit visibility, release visibility, rundown lifecycle actions, task action clusters, archived-view controls, and scope-driven filters under the new effective-permission-and-scope model.
- **Dependencies**: TASK-007, TASK-009, TASK-010
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Keep state-based disabled actions visible where that is the current UX pattern; do not regress archive/admin flows.

### TASK-013: Add Contract Tests for Auth and Access Management APIs
- **Objective**: Lock down the API contract for the new auth/session and grant-management behavior.
- **Scope**: Add or update contract tests for login, current-user context, access denial modes, and Access Management endpoints.
- **Dependencies**: TASK-003, TASK-005
- **Owner type**: QA
- **Priority**: Must
- **Notes**: Include both success and failure cases for missing/suspended grants.

### TASK-014: Add Backend Tests for Grant Lifecycle and Permission Enforcement
- **Objective**: Validate critical backend state and authorization behavior.
- **Scope**: Add unit/integration coverage for Access Grant lifecycle transitions, permission mapping, access-denied login behavior, audit emission, and authorization checks on existing APIs.
- **Dependencies**: TASK-005, TASK-006, TASK-007
- **Owner type**: QA
- **Priority**: Must
- **Notes**: Include optimistic-lock and invalid-transition cases where applicable.

### TASK-015: Add Frontend Integration and E2E Coverage
- **Objective**: Validate end-user behavior under the new access model.
- **Scope**: Cover login denial, route blocking, menu visibility, Access Management admin workflows, and existing-page permission behavior in integration or E2E tests.
- **Dependencies**: TASK-010, TASK-011, TASK-012
- **Owner type**: QA
- **Priority**: Must
- **Notes**: Include admin and non-admin personas.

### TASK-016: Add Access-Denial and Grant-Change Observability
- **Objective**: Make access-governance behavior operationally visible.
- **Scope**: Add structured logging and operational diagnostics for denied entry, suspended access, grant mutations, and admin failures.
- **Dependencies**: TASK-003, TASK-005, TASK-006
- **Owner type**: security
- **Priority**: Should
- **Notes**: This task should not expose sensitive values; it complements audit, not replaces it.

### TASK-017: Plan and Execute Phase 1 Rollout
- **Objective**: Ship the new authorization model safely.
- **Scope**: Define rollout sequence, seed initial admins, validate migration behavior in non-prod, confirm support playbook for locked-out users, and coordinate cutover steps with stakeholders.
- **Dependencies**: TASK-004, TASK-013, TASK-014, TASK-015
- **Owner type**: devops
- **Priority**: Must
- **Notes**: Include rollback considerations if deny-by-default is enabled before grants are seeded correctly.

### TASK-018: Evaluate Enterprise Directory Search for Access Management
- **Objective**: Decide whether Access Management should search only existing grants or also enterprise users without grants.
- **Scope**: Confirm product decision, identify integration impact, and implement only if required by the agreed Phase 1 scope.
- **Dependencies**: TASK-005
- **Owner type**: platform
- **Priority**: Should
- **Notes**: This task may remain deferred if Phase 1 limits search to existing grants.

### TASK-019: Design Follow-up for AUTO Completion Ingestion
- **Objective**: Close the current execution-gap after Phase 1 authorization work is stable.
- **Scope**: Evaluate callback, polling, or explicit manual completion for AUTO tasks; define the next-step design and backlog entry.
- **Dependencies**: None
- **Owner type**: backend
- **Priority**: Could
- **Notes**: Not part of the critical path for Access Management delivery. Detailed follow-up artifacts now live in `docs/05-design/multi-tool-execution-design.md` and `docs/06-tasks/multi-tool-execution-tasks.md`.

---

## Dependency Plan

### Critical Path

`TASK-001 -> TASK-002 -> TASK-003 -> TASK-004 -> TASK-005 -> TASK-009 -> TASK-010 -> TASK-011 -> TASK-012 -> TASK-013/TASK-014/TASK-015 -> TASK-017`

### Prerequisite Clusters

- **Authorization foundation**
  - TASK-001, TASK-002, TASK-003, TASK-004
- **Admin management backend**
  - TASK-005, TASK-006, TASK-007
- **Frontend alignment**
  - TASK-009, TASK-010, TASK-011, TASK-012
- **Verification and rollout**
  - TASK-013, TASK-014, TASK-015, TASK-017

### Parallel Workstreams

- TASK-006 can run alongside TASK-007 once TASK-005 is stable
- TASK-008 can run in parallel with TASK-009/TASK-010 after TASK-003
- TASK-016 can run alongside verification tasks
- TASK-018 and TASK-019 are independent from the core Phase 1 delivery path

---

## Risks / Blockers

- The auth/session contract is now fixed to compatibility `role` plus `roles[]`, `permissions[]`, and `scopes[]`; the remaining risk is incomplete enforcement across older workflow surfaces.
- Deny-by-default requires a safe first-admin bootstrap path; without it, rollout can lock everyone out of the product.
- Team Book production contract details may block completion of the real provider adapter.
- Access Management now supports provider-backed directory search plus manual display-name fallback; future enterprise sync or richer directory attributes would still materially change the backend/API/UI scope.
- Existing workflow authorization logic may contain scattered assumptions that are easy to miss during migration to effective permissions.

---

## Open Questions

1. What is the approved production bootstrap mechanism for the first `DEVOPS_ADMIN` under deny-by-default rules?
2. Should a later phase expand Access Management beyond the current provider-backed directory search and manual display-name fallback into broader enterprise user sync?
3. Are grant updates required to capture a mandatory admin note for governance purposes?
4. Should a later phase extend Access Management beyond the current product-entry grant plus `Application + SNOW Group` scope model into agent- or environment-scoped authorization?
