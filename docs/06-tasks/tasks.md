# Implementation Task Breakdown

## Overview

This document breaks the current Deployment Agent design into implementation-ready tasks for the next delivery phase. It covers two tracks: finishing the access-governance alignment already introduced into the product, and delivering the new `Development` workspace for deterministic AS400-oriented code-spec generation.

**Delivery objective**
- Preserve the current release-orchestration workflow baseline
- Complete authorization alignment across login, APIs, routes, and UI actions
- Maintain deny-by-default product entry plus scoped visibility through Access Grants
- Deliver the `Development` workspace under `/wwa/development` with deterministic spec generation, draft persistence, and export

**Planning assumptions**
- Current workflow capabilities such as upload/import, Release Flow monitoring, rundown archive lifecycle, task actions, configuration management, template-based rundown creation, and audit viewing already exist as the implementation baseline.
- Access Management, scoped visibility, multi-scope runtime capture, and rundown-owner controls are already implemented in substantial part, but full enforcement and verification are still incomplete.
- The `Development` workspace is a spec-generation workbench, not a source-code editor or runtime execution surface.
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
  - all `Development` workspace tasks in this document

---

## Source Design

**System name:** Deployment Agent

**Design scope summary**
- The design preserves the existing Deployment Agent MVP for release orchestration and extends it with local Access Grants, deny-by-default entry, scoped visibility, Access Management, and consistent permission-based enforcement across backend and frontend behavior.
- The design also adds a new `Development` workspace page under the existing WWA shell. This page is a deterministic, form-driven AS400 code-spec workbench with draft persistence, preview, and export support.
- Existing archive / restore / purge behavior remains separate from Access Management and must continue to work under the authorization model. The new `Development` workspace must reuse the current authentication/session and scoped-access patterns rather than introducing a parallel security model.

---

## Workstreams

### Major Implementation Streams

1. Authorization foundation and data model hardening
2. Access Management backend/API enforcement and audit coverage
3. Frontend session, route, and action permission alignment
4. Development workspace backend domain, APIs, and persistence
5. Development workspace UI, preview, and export flows
6. Verification, rollout, and operational hardening

### Recommended Sequencing

1. Finish authorization foundation, bootstrap rules, and API enforcement alignment
2. Stabilize frontend auth contract, route guards, and permission behavior
3. Add Development Spec persistence model and deterministic generation service
4. Expose Development Spec APIs and audit behavior
5. Build `/wwa/development` workbench UI and export flows
6. Complete contract, integration, and E2E validation across both tracks
7. Execute rollout and seed first admin access safely

### Parallel Work Opportunities

- Development workspace backend can proceed in parallel with remaining frontend authorization alignment once auth/session contracts are stable.
- Audit and observability tasks can proceed in parallel with API work.
- Team Book production adapter work can run alongside Development workspace UI.
- Verification streams can prepare fixtures and test plans before endpoint completion.

---

## Task Breakdown by Domain

### Persistence / Data
- Access Grant entity, migration, repository, optimistic-update behavior
- Audit action expansion for access-governance events
- Initial admin bootstrap / seed strategy
- Development Spec persistence model and generated-artifact storage

### Backend / API
- Login/session authorization resolution
- Access Management endpoints
- Effective-permission enforcement across existing APIs
- Team Book production adapter alignment
- Development Spec CRUD, generation, and export APIs

### Frontend / UI
- User store upgrade for roles/permissions/access state
- Route guards and access-denied UX
- Access Management page
- Permission alignment for existing navigation and action surfaces
- Development workbench page, form, preview, and export UX

### Workflow / Generation
- Deterministic Development Spec generation rules
- Regeneration behavior and source-vs-generated content boundaries
- Export shaping for Markdown and JSON artifacts

### Security / Reliability / Observability
- Audit coverage for grant lifecycle and Development Spec actions
- Structured logging / visibility for access denial, admin changes, and generation/export failures
- Safe rollout under deny-by-default rules

### Testing
- Contract tests for auth, access-grant, and Development Spec APIs
- Backend state and audit tests
- Frontend integration and E2E coverage for both authorization and Development workbench flows

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
- **Objective**: Lock down the API contract for the auth/session and grant-management behavior.
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

### TASK-015: Add Frontend Integration and E2E Coverage for Authorization Flows
- **Objective**: Validate end-user behavior under the access model.
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

### TASK-017: Plan and Execute Authorization Rollout
- **Objective**: Ship the authorization model safely.
- **Scope**: Define rollout sequence, seed initial admins, validate migration behavior in non-prod, confirm support playbook for locked-out users, and coordinate cutover steps with stakeholders.
- **Dependencies**: TASK-004, TASK-013, TASK-014, TASK-015
- **Owner type**: devops
- **Priority**: Must
- **Notes**: Include rollback considerations if deny-by-default is enabled before grants are seeded correctly.

### TASK-018: Evaluate Enterprise Directory Search for Access Management
- **Objective**: Decide whether Access Management should search only existing grants or also enterprise users without grants.
- **Scope**: Confirm product decision, identify integration impact, and implement only if required by the agreed scope.
- **Dependencies**: TASK-005
- **Owner type**: platform
- **Priority**: Should
- **Notes**: This task may remain deferred if the current scope limits search to existing grants.

### TASK-019: Design Follow-up for AUTO Completion Ingestion
- **Objective**: Close the current execution gap after authorization work is stable.
- **Scope**: Evaluate callback, polling, or explicit manual completion for AUTO tasks; define the next-step design and backlog entry.
- **Dependencies**: None
- **Owner type**: backend
- **Priority**: Could
- **Notes**: Not part of the critical path for this phase. Detailed follow-up artifacts now live in `docs/05-design/multi-tool-execution-design.md` and `docs/06-tasks/multi-tool-execution-tasks.md`.

### TASK-020: Add Development Spec Persistence Model
- **Objective**: Introduce the data model for Development Spec drafts and generated artifacts.
- **Scope**: Add migration(s), persistence entity/model, repository access, status enum, optimistic-update support, and storage for structured input plus generated output payloads.
- **Dependencies**: None
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Include `title`, `module_name`, `program_type`, `code_style`, structured source sections, `status`, `created_by`, `created_at`, `updated_by`, `updated_at`. [ASSUMPTION] Generated Markdown may be stored or produced on demand, but the source model must remain canonical.

### TASK-021: Define Development Spec Domain Model and Validation Catalogs
- **Objective**: Establish the normalized source model and validation rules for AS400-oriented spec authoring.
- **Scope**: Define supported `programType`, `codeStyle`, export formats, section structures, validation rules, and normalization behavior for partially complete drafts.
- **Dependencies**: TASK-020
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Must explicitly support `FREE_FORMAT`, `FIXED_FORMAT`, and `BOTH`, and preserve open questions instead of silently dropping them.

### TASK-022: Build Deterministic Development Spec Generation Service
- **Objective**: Generate a stable, implementation-facing code-spec document from structured inputs.
- **Scope**: Implement rule/template-driven generation that maps normalized source input into sectioned output covering overview, program objective, I/O, processing flow, data/file usage, structure, error handling, free-format guidance, fixed-format guidance, test scenarios, and open questions.
- **Dependencies**: TASK-021
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Generation must be deterministic for the same input payload and must not depend on LLM availability.

### TASK-023: Build Development Spec CRUD and Generation APIs
- **Objective**: Expose backend interfaces for Development Spec authoring and generation.
- **Scope**: Implement `GET /development-specs`, `POST /development-specs`, `GET /development-specs/{id}`, `PUT /development-specs/{id}`, and `POST /development-specs/{id}/generate` with validation, authorization, optimistic locking behavior, and list/detail retrieval.
- **Dependencies**: TASK-020, TASK-021, TASK-022
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Visibility and mutation must follow the existing scoped authorization pattern already used by Deployment Agent.

### TASK-024: Build Development Spec Export API
- **Objective**: Allow generated Development Specs to be exported for downstream use.
- **Scope**: Implement `GET /development-specs/{id}/export` for at least Markdown and JSON outputs, with clear content shaping, error handling, and authorization checks.
- **Dependencies**: TASK-022, TASK-023
- **Owner type**: backend
- **Priority**: Must
- **Notes**: JSON export should preserve the normalized source model as well as generated sections where applicable.

### TASK-025: Add Development Spec Audit Coverage
- **Objective**: Make Development Spec activity traceable in the existing audit model.
- **Scope**: Add audit action types and write audit events for spec create, update, generate/regenerate, and export actions where persistence/audit is enabled.
- **Dependencies**: TASK-023, TASK-024
- **Owner type**: backend
- **Priority**: Should
- **Notes**: Reuse the current audit surface rather than creating a separate history subsystem.

### TASK-026: Add `/wwa/development` Route and Navigation Entry
- **Objective**: Expose the Development workspace inside the existing WWA shell.
- **Scope**: Add frontend route, navigation/menu entry, page-level metadata, and permission gating consistent with the current auth/session model.
- **Dependencies**: TASK-009, TASK-010, TASK-023
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: The page must behave as a separate workspace function without changing existing WWA layout behavior.

### TASK-027: Build Development Workbench Form UI
- **Objective**: Provide structured authoring inputs for Development Specs.
- **Scope**: Implement `DevelopmentWorkbenchView` and form components grouped by basic info, business requirement, data/file design, program structure, error/audit expectations, and delivery notes/open questions.
- **Dependencies**: TASK-021, TASK-023, TASK-026
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Validation feedback should appear near the relevant section, not only in a global summary.

### TASK-028: Build Development Spec Preview and Regeneration UX
- **Objective**: Show users the generated code-spec output clearly and safely.
- **Scope**: Implement preview pane, generate/regenerate actions, loading/error states, and clear source-vs-generated content boundaries.
- **Dependencies**: TASK-022, TASK-023, TASK-027
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: If manual section editing is deferred, the UI should make source-input-only editing explicit. [ASSUMPTION] Phase 1 can omit manual editing of generated sections if the preview/generation loop remains clear.

### TASK-029: Build Draft Save/Load and Detail View Behavior
- **Objective**: Support iterative Development Spec authoring.
- **Scope**: Implement create/load/update flows, draft persistence, detail retrieval, optimistic-update conflict handling in UI, and user feedback for incomplete but valid drafts.
- **Dependencies**: TASK-023, TASK-027
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Save/generate operations should preserve user-entered draft content on error.

### TASK-030: Build Development Spec Export UX
- **Objective**: Let users export generated Development Specs in supported formats.
- **Scope**: Implement export action surfaces, format selection, success/failure handling, and alignment with backend export behavior.
- **Dependencies**: TASK-024, TASK-028, TASK-029
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Markdown should be optimized for human review and JSON for downstream machine-assisted processing.

### TASK-031: Align Development Workspace Authorization and Visibility Rules
- **Objective**: Ensure the Development workspace follows the same security model as the rest of the product.
- **Scope**: Apply session, route, record-visibility, mutation, and scope-based access rules to Development Spec views and APIs; ensure no parallel authorization model is introduced.
- **Dependencies**: TASK-007, TASK-023, TASK-026
- **Owner type**: backend
- **Priority**: Must
- **Notes**: [ASSUMPTION] Unless a narrower rule is approved later, Development Spec visibility follows the existing scoped-access approach.

### TASK-032: Add Contract Tests for Development Spec APIs
- **Objective**: Lock down the API contract for Development Spec authoring, generation, and export.
- **Scope**: Add contract tests for CRUD, generate, export, validation, authorization failures, and optimistic-update conflicts where applicable.
- **Dependencies**: TASK-023, TASK-024
- **Owner type**: QA
- **Priority**: Must
- **Notes**: Include validation around supported `programType`, `codeStyle`, and export formats.

### TASK-033: Add Backend Tests for Deterministic Generation and Audit
- **Objective**: Validate the backend behavior of the Development workspace.
- **Scope**: Add unit/integration tests for normalized source handling, deterministic output generation, partial-input tolerance, export shaping, and audit emission where enabled.
- **Dependencies**: TASK-022, TASK-024, TASK-025
- **Owner type**: QA
- **Priority**: Must
- **Notes**: Include scenarios for `FREE_FORMAT`, `FIXED_FORMAT`, and `BOTH`.

### TASK-034: Add Frontend Integration and E2E Coverage for Development Workbench
- **Objective**: Validate end-user behavior for Development Spec authoring and export.
- **Scope**: Cover route access, page load, form validation, generation preview, draft save/load, export actions, and error preservation in integration or E2E tests.
- **Dependencies**: TASK-026, TASK-027, TASK-028, TASK-029, TASK-030
- **Owner type**: QA
- **Priority**: Must
- **Notes**: Include partially complete drafts and all supported code-style modes.

### TASK-035: Add Observability for Development Spec Failures
- **Objective**: Make Development workspace operational failures diagnosable.
- **Scope**: Add structured logging and diagnostics for generation failures, export failures, validation hotspots, and optimistic-update conflicts.
- **Dependencies**: TASK-023, TASK-024, TASK-025
- **Owner type**: security
- **Priority**: Should
- **Notes**: Logging should capture failure context without leaking sensitive content from large draft payloads.

### TASK-036: Decide Phase 1 Manual-Edit vs Source-Only Editing Boundary
- **Objective**: Lock the Development workspace UX boundary before implementation drifts into editor-like behavior.
- **Scope**: Confirm whether Phase 1 supports only source-input editing or also manual editing of generated sections, and align API/UI scope accordingly.
- **Dependencies**: TASK-022
- **Owner type**: platform
- **Priority**: Should
- **Notes**: The design allows manual section editing as an optional refinement, but it is not clearly required for Phase 1.

### TASK-037: Evaluate Future Linking Between Development Specs and Release Flows
- **Objective**: Capture the next-step design boundary between code-spec authoring and deployment orchestration.
- **Scope**: Evaluate whether Development Specs should later link to release flows, tasks, or generated engineering artifacts, and define the follow-up backlog entry without expanding current scope.
- **Dependencies**: None
- **Owner type**: platform
- **Priority**: Could
- **Notes**: This is future-facing design work, not part of the first delivery path.

---

## Dependency Plan

### Critical Path

`TASK-001 -> TASK-002 -> TASK-003 -> TASK-004 -> TASK-005 -> TASK-007 -> TASK-009 -> TASK-010 -> TASK-017`

`TASK-020 -> TASK-021 -> TASK-022 -> TASK-023 -> TASK-026 -> TASK-027 -> TASK-028 -> TASK-029 -> TASK-030 -> TASK-032/TASK-033/TASK-034`

### Prerequisite Clusters

- **Authorization foundation**
  - TASK-001, TASK-002, TASK-003, TASK-004
- **Admin management backend**
  - TASK-005, TASK-006, TASK-007
- **Frontend authorization alignment**
  - TASK-009, TASK-010, TASK-011, TASK-012
- **Development Spec backend foundation**
  - TASK-020, TASK-021, TASK-022, TASK-023, TASK-024, TASK-025
- **Development workbench frontend**
  - TASK-026, TASK-027, TASK-028, TASK-029, TASK-030, TASK-031
- **Verification and rollout**
  - TASK-013, TASK-014, TASK-015, TASK-017, TASK-032, TASK-033, TASK-034

### Parallel Workstreams

- TASK-006 can run alongside TASK-007 once TASK-005 is stable.
- TASK-008 can run in parallel with TASK-009/TASK-010 after TASK-003.
- TASK-020 through TASK-025 can run largely in parallel with remaining authorization frontend alignment after auth/session contract stability.
- TASK-035 can run alongside TASK-032/TASK-033/TASK-034.
- TASK-018, TASK-019, TASK-036, and TASK-037 are outside the main delivery path.

---

## Risks / Blockers

- The auth/session contract is now fixed to compatibility `role` plus `roles[]`, `permissions[]`, and `scopes[]`; the remaining risk is incomplete enforcement across older workflow surfaces.
- Deny-by-default requires a safe first-admin bootstrap path; without it, rollout can lock everyone out of the product.
- Team Book production contract details may block completion of the real provider adapter.
- Existing workflow authorization logic may contain scattered assumptions that are easy to miss during migration to effective permissions.
- The Development workspace can drift into code-editor scope unless the source-input vs generated-output boundary is kept explicit.
- Scoped visibility rules for Development Specs are not deeply elaborated in the design; implementation must reuse the existing access model unless product direction changes.
- If manual editing of generated sections is included too early, regeneration semantics can become confusing and create avoidable data-loss risk.

---

## Open Questions

1. What is the approved production bootstrap mechanism for the first `DEVOPS_ADMIN` under deny-by-default rules?
2. Should a later phase expand Access Management beyond the current provider-backed directory search and manual display-name fallback into broader enterprise sync?
3. Are grant updates required to capture a mandatory admin note for governance purposes?
4. Should a later phase extend Access Management beyond the current product-entry grant plus `Application + SNOW Group` scope model into agent- or environment-scoped authorization?
5. Should Phase 1 of the Development workspace support source-input-only editing, or also manual editing of generated sections?
6. Should later phases add reusable Development Spec templates for common AS400 program types?
7. Should generated Development Specs later be linkable to release flows, task records, or starter code artifacts?
