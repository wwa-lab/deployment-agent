# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Working Rules

- When asked to review a document, only produce a review or quality report unless the user explicitly asks to rewrite or fix the document.
- At the start of a new session, read `docs/00-context/AGENT_HANDOFF.md` before product/SDD/implementation work. Before ending a session with meaningful progress, update that handoff last (ADR-0008).
- Read `PROJECT_RULES.md` and `DEVELOPMENT_STANDARDS.md` before non-trivial SDD or implementation work.
- Write staged SDLC artifacts to slice paths under `docs/01-requirements` … `docs/06-tasks` (see `docs/SDD-BOOTSTRAP.md`). Shared docs such as `docs/03-spec/spec.md` remain valid for cross-cutting scope.
- For full SDD generation, use `.claude/skills/wwa-sdd-generate-all/` and the mandatory skill chain; report skill-chain evidence.
- Project rules and SDD documents are English-only (ADR-0009). Do not create `.zh-CN.md` companions unless the user explicitly asks.
- Default code, comments, and runtime UI copy to English unless a product slice explicitly requires otherwise.
- For durable project memory, use the `context-engineering-adr` skill when a task changes architecture, cross-project conventions, AI-agent working context, platform boundaries, integrations, security posture, or data ownership.

# Project Contract

## Build And Test

- Backend test: `mvn test`
- Backend single test class: `mvn test -Dtest=ReleaseFlowControllerTest`
- Backend single test method: `mvn test -Dtest=ReleaseFlowControllerTest#methodName`
- Backend local run: `mvn spring-boot:run -Dspring-boot.run.profiles=local`
- Frontend dev: `cd frontend && npm install && npm run dev` (Vite on `:5173`, proxies `/api` to `:8080`)
- Frontend build/typecheck: `cd frontend && npm run build` (runs `vue-tsc && vite build`)

## Context Engineering And ADRs

- Use `docs/00-context/` as the durable project context layer for background, terminology, boundaries, onboarding knowledge, and cross-agent working rules.
- The active SDD profile is `docs/00-context/sdd-profile.md`.
- Use `docs/00-context/decisions/` for Architecture Decision Records (ADRs).
- Before changing architecture, platform boundaries, security posture, data ownership, integrations, or shared agent conventions, read the relevant context documents and ADRs.
- Capture significant new or reversed decisions as ADRs instead of leaving rationale only in chat, PRs, or implementation notes.
- Keep SDD artifacts as the source of feature scope; use ADRs for the "why" behind architecture and cross-cutting choices.
- For reusable ADR/context workflow guidance, use `.claude/skills/context-engineering-adr/`.

## Global Agentic SDLC Skills

- Treat `.agents/skills/` as the canonical project-local source for reusable SDD and Agentic SDLC workflows.
- Use `sdd-profile-manager` before applying SDD to a new project shape.
- Use `wwa-sdd-generate-all` for full slice SDD generation; use `sdd-slice-bootstrap` when auditing an existing skeleton.
- Use `execution-manifest` before handing work to a coding agent, remote agent, or automation.
- Use `freshness-gate` before approving, implementing, or releasing from potentially stale docs/code/tests.
- Use `cross-ide-skill-router` when keeping Codex, Claude Code, OpenCode, Gemini, or another tool aligned on the same workflow source.
- Use `agentic-sdlc-orchestrator` for propose/apply/verify/archive lifecycle work.
- Use `agentic-sdlc-doctor` after global skill sync or routing changes.
- Track global skill versions and supporting assets in `docs/00-context/agentic-sdlc-registry.md`.
- Validate execution manifests against `docs/00-context/execution-manifest.schema.json`.
- GitHub Copilot routing lives in `.github/copilot-instructions.md` and `.github/instructions/agentic-sdlc.instructions.md`; keep those files as thin bridges to this contract and `.agents/skills/`.

## Spring Profiles

| Profile | Database | DDL | Header Auth | Use |
|---------|----------|-----|-------------|-----|
| `default` | Oracle | validate | disabled | Production |
| `local` | H2 in-memory | update | enabled | Local dev |
| `test` | H2 in-memory | validate | enabled | Tests |

## Local Login Credentials

Any non-empty password works with the stub auth provider.

| Employee ID | Display Name | Role |
|-------------|-------------|------|
| `emp-001` | Alice Park | DEVELOPER |
| `emp-002` | Bob Kim | TL |
| `emp-003` | Carol Lee | DEVOPS_ADMIN |
| `emp-004` | David Cho | AUDIT |
| `emp-005` | Eve Yoon | MANAGEMENT |

A `GUEST` role also exists for anonymous read-only access (no employee login required). The guest session is created via `POST /api/platform/auth/guest` and enforced by `GuestReadOnlyFilter`.

## Architecture Overview

### Entity Hierarchy

```
ReleaseFlow (root aggregate, @Version optimistic lock)
 └─ Request (stage-specific, has optional `agent` field for multi-agent isolation)
     └─ Task (atomic execution unit, MANUAL or AUTO)
         └─ TaskExecutionHistory (immutable record of each attempt)

AccessGrant (deny-by-default product entry, scoped by application + snowGroup)
OutboxEvent (transactional outbox for domain events, PENDING → PUBLISHED)
ScopeDirectoryEntry (curated application / SNOW group / agent choices for upload)
ConfigurationComponent (scoped component metadata for Jenkins/Ansible/callback)
ConfigurationItem (raw key-value configuration)
AuditLogEntry (immutable audit trail)
```

### Multi-Agent Architecture

The system supports multiple agents (Deployment Agent, Testing Agent, Build Agent) sharing the same domain services but isolated at three layers:

**Backend isolation:**
- Separate controller routes: `/api/deployment-agent/*`, `/api/testing-agent/*`, `/api/build-agent/*`
- Agent-specific controllers live in `agents/<agent>/web/` (e.g. `agents/deployment/web/`, `agents/build/web/`)
- Platform-shared controllers live in `platform/web/shared/` (auth, audit, config, access grants, template download)
- `AgentBoundaryGuard` (in `platform/web/security/`) enforces that each controller only accesses data belonging to its agent
- Controllers force `effectiveAgent = AgentId.<AGENT>` server-side (client `?agent=` param is ignored)
- Shared domain services receive `agentId` as a parameter; queries filter by `request.agent`

**Frontend isolation:**
- `createAgentWorkspace` factory in `frontend/src/platform/composables/createAgentWorkspace.ts` produces per-agent Axios client, Pinia store, and API modules from a single configuration
- Each agent is defined in `frontend/src/agents/<agent>/index.ts` (e.g. `agents/deployment/index.ts`, `agents/build/index.ts`)
- `platformClient.ts` (baseURL `/api/platform`) handles shared endpoints (auth, audit, config, access grants)
- Shared view components in `frontend/src/platform/components/` (`ReleaseFlowSummaryView.vue`, `ReleaseFlowDetailView.vue`) contain all presentation logic; each agent wraps them with a thin `<script setup>` that injects its own store, API module, and copy text
- Agent-specific views live in `frontend/src/agents/<agent>/` as thin wrappers around the shared platform components
- Agent registry in `frontend/src/config/agentRegistry.ts` drives home page cards and nav

### Security Architecture

1. `SessionAuthFilter` reads `UserContext` from HTTP session
2. `HeaderAuthFilter` reads `X-User-Id` / `X-User-Role` headers (test/local only, controlled by `app.auth.header-fallback-enabled`)
3. `GuestReadOnlyFilter` blocks non-GET requests from `GUEST` sessions (only exception: `/api/platform/auth/logout`)
4. `UserContext` carries `roles[]`, `permissions[]`, `scopes[]` (application + snowGroup pairs)
5. DEVOPS_ADMIN with no scopes = global admin; with scopes = scoped admin

### Error Handling

Custom `AppException` hierarchy in `errors/` maps to HTTP status codes. `GlobalExceptionHandler` (@RestControllerAdvice) intercepts all exceptions and returns `ErrorResponseDto`. Reuse existing exception types; do not create new ones without justification.

### Frontend Routing

```
/login                                     → LoginView (public)
/wwa/home                                 → WwaHomeView (agent cards)
/wwa/deployment-agent                      → ReleaseFlowSummaryView
/wwa/deployment-agent/release-flows/:id    → ReleaseFlowDetailView
/wwa/testing-agent                         → TestingAgentSummaryView
/wwa/testing-agent/release-flows/:id       → TestingAgentDetailView
/wwa/build-agent                           → BuildAgentSummaryView
/wwa/build-agent/release-flows/:id         → BuildAgentDetailView
/wwa/template-management                   → TemplateManagementView
/wwa/configuration-management              → ConfigAdminView
/wwa/audit-log                             → AuditLogView
/wwa/access-management                     → AccessManagementView
```

## Architecture Boundaries

- Agent-specific controllers live in `src/main/java/com/wwa/deploymentagent/agents/<agent>/web/`
- Platform-shared controllers live in `src/main/java/com/wwa/deploymentagent/platform/web/shared/`
- Domain logic lives in `src/main/java/com/wwa/deploymentagent/domain/`
- Do not put persistence logic in controllers
- Shared types (DTOs, enums, `UserContext`) live in `src/main/java/com/wwa/deploymentagent/contracts/`
- Security filters live in `src/main/java/com/wwa/deploymentagent/web/security/` (SessionAuthFilter, HeaderAuthFilter, GuestReadOnlyFilter) and `src/main/java/com/wwa/deploymentagent/platform/web/security/` (AgentBoundaryGuard)
- Spring configuration lives in `src/main/java/com/wwa/deploymentagent/config/`
- Custom exceptions live in `src/main/java/com/wwa/deploymentagent/errors/`
- Frontend agent workspaces live in `frontend/src/agents/<agent>/` (thin wrappers: `index.ts`, `api.ts`, summary/detail views)
- Frontend shared platform view components live in `frontend/src/platform/components/` (all agent views compose from these)
- Frontend shared composables and factories live in `frontend/src/platform/composables/`
- Frontend source lives in `frontend/src/`

## Technology Stack

- Backend: Java 21 / Spring Boot 3.4 / Spring MVC / Spring Data JPA / Maven / Lombok
- Frontend: Vue 3 / Vite 5 / Pinia / Vue Router 4 / Axios
- Database: Oracle (default profile) / H2 in-memory (`local` and `test`)
- Auth: session-based login with a Team Book provider abstraction, plus optional header fallback where configured

## Coding Conventions

- Prefer pure functions in domain layer (for example `TaskStateMachine`, `ReleaseFlowAggregation`)
- Do not introduce new global state without explicit justification
- Reuse existing error types from `src/main/java/com/wwa/deploymentagent/errors/`
- DTOs are Java records with static `from()` factory methods
- Entity IDs are String UUIDs generated via `@PrePersist`
- Enum constant names match DB string values directly
- JSON columns use the shared converter helpers and Oracle/H2-compatible CLOB storage
- Audit logging should not break core business flows (`AuditLoggerService` uses `Propagation.REQUIRES_NEW`)
- Optimistic locking via `@Version Long version` on ReleaseFlow, Request, Task

## Multi-Agent Rules

### Shared Components Must Be Agent-Agnostic

`UploadDialog` is shared across all agents. It must NOT hardcode any agent-specific API client or store. Always inject `uploadFn`, `downloadTemplateFn`, and `onUploadSuccess` as props. Each agent view is responsible for passing its own API functions from the workspace created by `createAgentWorkspace`.

Violating this causes silent data routing bugs — uploads go to the wrong agent's backend, records are saved under the wrong `agentId`, and the list query (which filters by `agentId`) returns nothing.

### Shared File Names Must Not Be Agent-Specific

Downloadable assets shared across agents (e.g. the XLSX upload template) must use a neutral name such as `request-template.xlsx`, not `deployment-request-template.xlsx`. The backend `Content-Disposition` header and frontend `link.download` must match and stay neutral.

### Per-Agent Stage Restrictions

Each agent defines its own allowed stages. Do not default to `['SIT', 'UAT', 'PROD']` for new agents.

| Agent | Allowed Stages |
|-------|---------------|
| Deployment Agent | SIT, UAT, PROD |
| Testing Agent | UAT only |
| Build Agent | DEV only |

Enforce via `:allowed-stages` prop on `UploadDialog` and a `stages` constant in the summary view. Also update: page subtitle, WWA Today description, Stage filter (use a disabled input when only one stage is allowed), and `agentRegistry.ts` description.

### Checklist When Adding a New Agent

- [ ] Add `AgentId` constant in `contracts/AgentId.java`
- [ ] Create backend controllers under `agents/<agent-key>/web/` mapped to `/api/<agent-key>/` that force `effectiveAgent` server-side
- [ ] Create `frontend/src/agents/<agent-key>/index.ts` using `createAgentWorkspace` factory (produces Axios client, Pinia store, and API modules)
- [ ] Create summary and detail view as thin wrappers in `frontend/src/agents/<agent-key>/` that compose from `platform/components/ReleaseFlow{Summary,Detail}View.vue` (do NOT duplicate the shared view)
- [ ] Register in `agentRegistry.ts` with accurate description
- [ ] Add routes in `frontend/src/router/index.ts`
- [ ] Agent view passes its own `uploadFn`, `downloadTemplateFn`, `onUploadSuccess` to `UploadDialog`
- [ ] Agent view passes `:allowed-stages` matching the agent's scope
- [ ] `stages` constant in the summary view matches allowed stages
- [ ] Page subtitle and WWA Today text do not mention stages outside the agent's scope
- [ ] Stage filter is a disabled input (not a dropdown) when only one stage is allowed
- [ ] Backend controller forces `effectiveAgent = AgentId.<THIS_AGENT>` (never trusts client-supplied agent param)

## Safety Rails

## NEVER

- Modify `.env`, lockfiles, or CI secrets without explicit approval
- Remove feature flags without searching all call sites
- Commit without running tests

### Decisions that must always be synchronous human-in-the-loop

The following task / decision classes must **never** be auto-approved by policy,
**never** be auto-actioned by an AI advisor, and **never** default to "approve"
on SLA timeout. They must require a real, interactive human click every time,
and the audit trail must record `actor_kind = HUMAN`. This rule holds even
after the MVP foundation seams (`DecisionGate`, `ActorKind`, `RiskLevel`) are
later wired to real policy / AI implementations — any such implementation
must explicitly skip these classes and escalate instead of deciding.

- Production data **deletion**, **masking**, or cross-environment **migration**
- **Access grant** changes — especially granting or revoking `DEVOPS_ADMIN`
- Any action with **financial, regulatory, or external-audit** impact
- Cross-environment **data writeback** (e.g. UAT → PROD, PROD → any non-prod)
- Any action that **disables or bypasses audit logging itself**
- **First execution** of a new task type or template before a policy baseline
  exists — new task shapes must be human-gated until they have observed history
- Any task marked `RiskLevel.L3` in its template or entity

If a new feature would weaken any of these protections, stop and escalate to
the user before proceeding.

## ALWAYS

- Show diff before committing
- Update CHANGELOG for user-facing changes

## Verification

- Backend changes: `mvn test`
- Frontend changes: `cd frontend && npm run build`
- API changes: update controller/contract tests under `src/test/java/com/wwa/deploymentagent/web/`
- Oracle schema changes: provide DDL in `src/main/resources/db/migration/`

## Cross-Cutting Sync Rules

### Backend enum change → frontend type must follow

When adding, removing, or renaming a value in any `contracts/enums/*.java` enum, immediately update the matching TypeScript type union in `frontend/src/types/index.ts`. This is mandatory because TypeScript's `.includes()` does not enforce that the argument belongs to the union, so `vue-tsc` will not catch the drift.

### Flyway migration → regenerate Oracle greenfield DDL

After adding any `db/migration/V*` script, regenerate `docs/sql/ORACLE_CURRENT_SCHEMA.sql` to include the new end-state. The greenfield DDL is never exercised by local/test runs (H2 auto-DDL), so staleness is invisible until someone tries an Oracle deployment.

### Shared view refactor → all agents must follow

All agent views must be thin wrappers around `platform/components/ReleaseFlow{Summary,Detail}View.vue`. Do not duplicate the shared view into an agent-specific standalone file. If an agent needs behavior that the shared view doesn't support, extend the shared view's props — do not fork it.

## Compact Instructions

Preserve:

1. Architecture decisions (NEVER summarize)
2. Modified files and key changes
3. Current verification status (pass/fail commands)
4. Open risks, TODOs, rollback notes
