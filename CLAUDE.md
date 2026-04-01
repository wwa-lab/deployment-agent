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
- Backend local run: `mvn spring-boot:run -Dspring-boot.run.profiles=local`
- Frontend dev: `cd frontend && npm install && npm run dev` (Vite on `:5173`, proxies `/api` to `:8080`)
- Frontend build/typecheck: `cd frontend && npm run build`

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
- Audit logging should not break core business flows

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

- [ ] Agent view passes its own `uploadFn`, `downloadTemplateFn`, `onUploadSuccess` to `UploadDialog`
- [ ] Agent view passes `:allowed-stages` matching the agent's scope
- [ ] `stages` constant in the summary view matches allowed stages
- [ ] Page subtitle and WWA Today text do not mention stages outside the agent's scope
- [ ] Stage filter is a disabled input (not a dropdown) when only one stage is allowed
- [ ] `agentRegistry.ts` description is accurate
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
