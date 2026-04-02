# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Working Rules

- When asked to review a document, only produce a review or quality report unless the user explicitly asks to rewrite or fix the document.
- Write staged SDLC artifacts to the current repository paths:
  - `docs/02-user-stories/user-stories.md`
  - `docs/03-spec/spec.md`
  - `docs/04-architecture/architecture.md`
  - `docs/05-design/design.md`
  - `docs/06-tasks/tasks.md`
- Default repository documents, code, and comments to English unless the user explicitly asks for another language.

# Project Contract

## Build And Test

- Backend test: `mvn test`
- Backend single test class: `mvn test -Dtest=ReleaseFlowControllerTest`
- Backend single test method: `mvn test -Dtest=ReleaseFlowControllerTest#methodName`
- Backend local run: `mvn spring-boot:run -Dspring-boot.run.profiles=local`
- Frontend dev: `cd frontend && npm install && npm run dev` (Vite on `:5173`, proxies `/api` to `:8080`)
- Frontend build/typecheck: `cd frontend && npm run build` (runs `vue-tsc && vite build`)

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

## Architecture Overview

### Entity Hierarchy

```
ReleaseFlow (root aggregate, @Version optimistic lock)
 └─ Request (stage-specific, has optional `agent` field for multi-agent isolation)
     └─ Task (atomic execution unit, MANUAL or AUTO)
         └─ TaskExecutionHistory (immutable record of each attempt)
```

### Multi-Agent Architecture

The system supports multiple agents (Deployment Agent, Testing Agent) sharing the same domain services but isolated at three layers:

**Backend isolation:**
- Separate controller routes: `/api/deployment-agent/*` vs `/api/testing-agent/*`
- Controllers force `effectiveAgent = AgentId.<AGENT>` server-side (client `?agent=` param is ignored)
- Shared domain services receive `agentId` as a parameter; queries filter by `request.agent`

**Frontend isolation:**
- Separate Axios clients: `api/client.ts` (baseURL `/api/deployment-agent`) vs `api/testingAgentClient.ts` (baseURL `/api/testing-agent`)
- Separate Pinia stores: `releaseFlow.ts` vs `testingAgentReleaseFlow.ts`
- Separate view components: `ReleaseFlowSummaryView` / `ReleaseFlowDetailView` vs `TestingAgentSummaryView` / `TestingAgentDetailView`
- Agent registry in `frontend/src/config/agentRegistry.ts` drives home page cards and nav

### Security Architecture

1. `SessionAuthFilter` reads `UserContext` from HTTP session
2. `HeaderAuthFilter` reads `X-User-Id` / `X-User-Role` headers (test/local only, controlled by `app.auth.header-fallback-enabled`)
3. `UserContext` carries `roles[]`, `permissions[]`, `scopes[]` (application + snowGroup pairs)
4. DEVOPS_ADMIN with no scopes = global admin; with scopes = scoped admin

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
/wwa/template-management                   → TemplateManagementView
/wwa/configuration-management              → ConfigAdminView
/wwa/audit-log                             → AuditLogView
/wwa/access-management                     → AccessManagementView
```

## Architecture Boundaries

- REST controllers live in `src/main/java/com/wwa/deploymentagent/web/controller/`
- Domain logic lives in `src/main/java/com/wwa/deploymentagent/domain/`
- Do not put persistence logic in controllers
- Shared types (DTOs, enums, `UserContext`) live in `src/main/java/com/wwa/deploymentagent/contracts/`
- Security filters live in `src/main/java/com/wwa/deploymentagent/web/security/`
- Spring configuration lives in `src/main/java/com/wwa/deploymentagent/config/`
- Custom exceptions live in `src/main/java/com/wwa/deploymentagent/errors/`
- Frontend source lives in `frontend/src/`

## Technology Stack

- Backend: Java 21 / Spring Boot 3.2.0 / Spring MVC / Spring Data JPA / Maven / Lombok
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

`UploadDialog` is shared across all agents. It must NOT hardcode any agent-specific API client or store. Always inject `uploadFn`, `downloadTemplateFn`, and `onUploadSuccess` as props. Each agent view is responsible for passing its own API functions.

- Deployment Agent view passes: `uploadFile` / `downloadTemplate` from `api/upload`, `store.fetchList` from `releaseFlow` store
- Testing Agent view passes: `uploadFile` / `downloadTemplate` from `api/testingAgentUpload`, `store.fetchList` from `testingAgentReleaseFlow` store

Violating this causes silent data routing bugs — uploads go to the wrong agent's backend, records are saved under the wrong `agentId`, and the list query (which filters by `agentId`) returns nothing.

### Shared File Names Must Not Be Agent-Specific

Downloadable assets shared across agents (e.g. the XLSX upload template) must use a neutral name such as `request-template.xlsx`, not `deployment-request-template.xlsx`. The backend `Content-Disposition` header and frontend `link.download` must match and stay neutral.

### Per-Agent Stage Restrictions

Each agent defines its own allowed stages. Do not default to `['SIT', 'UAT', 'PROD']` for new agents.

| Agent | Allowed Stages |
|-------|---------------|
| Deployment Agent | SIT, UAT, PROD |
| Testing Agent | UAT only |

Enforce via `:allowed-stages` prop on `UploadDialog` and a `stages` constant in the summary view. Also update: page subtitle, WWA Today description, Stage filter (use a disabled input when only one stage is allowed), and `agentRegistry.ts` description.

### Checklist When Adding a New Agent

- [ ] Add `AgentId` constant in `contracts/AgentId.java`
- [ ] Create backend controllers under `/api/<agent-key>/` that force `effectiveAgent` server-side
- [ ] Create frontend Axios client with `baseURL: '/api/<agent-key>'`
- [ ] Create frontend Pinia store (parallel to `releaseFlow.ts`)
- [ ] Create frontend API modules (parallel to `releaseFlows.ts`, `upload.ts`, `tasks.ts`)
- [ ] Create summary and detail view components
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

## ALWAYS

- Show diff before committing
- Update CHANGELOG for user-facing changes

## Verification

- Backend changes: `mvn test`
- Frontend changes: `cd frontend && npm run build`
- API changes: update controller/contract tests under `src/test/java/com/wwa/deploymentagent/web/`
- Oracle schema changes: provide DDL in `src/main/resources/db/migration/`

## Compact Instructions

Preserve:

1. Architecture decisions (NEVER summarize)
2. Modified files and key changes
3. Current verification status (pass/fail commands)
4. Open risks, TODOs, rollback notes
