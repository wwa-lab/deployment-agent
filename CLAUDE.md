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
