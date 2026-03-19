# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Project Contract

## Build And Test

- Backend test: `mvn test` (requires internet on first run for Maven Central)
- Frontend dev: `cd frontend && npm install && npm run dev` (Vite on :5173, proxies /api to :8080)
- Frontend typecheck: `cd frontend && npx vue-tsc --noEmit`

## Architecture Boundaries

- REST controllers live in `src/main/java/.../web/controller/`
- Domain logic lives in `src/main/java/.../domain/`
- Do not put persistence logic in controllers
- Shared types (DTOs, enums, UserContext) live in `src/main/java/.../contracts/`
- Security filters live in `src/main/java/.../web/security/`
- Spring configuration lives in `src/main/java/.../config/`
- Custom exceptions live in `src/main/java/.../errors/`
- Frontend source lives in `frontend/src/`

## Technology Stack

- **Backend**: Java 21 / Spring Boot 3.2.4 / Spring Data JPA / Maven / Lombok
- **Frontend**: Vue 3 (Composition API) / Vite 5 / Pinia / Vue Router 4 / Axios
- **Database**: Oracle (production) / H2 in-memory (tests)
- **Auth**: Session-based login (Team Book provider) + header fallback for tests

## Coding Conventions

- Prefer pure functions in domain layer (e.g. TaskStateMachine, ReleaseFlowAggregation)
- Do not introduce new global state without explicit justification
- Reuse existing error types from `src/main/java/.../errors/`
- DTOs are Java records with static `from()` factory methods
- Entity IDs are String UUIDs generated via `@PrePersist`
- Enum constant names match DB string values (no mapping needed)
- JSON columns use `@Convert(converter = JsonAttributeConverter.class)` + `columnDefinition = "CLOB"`
- Audit logging uses `Propagation.REQUIRES_NEW` — failures must not abort business operations

## Safety Rails

## NEVER

- Modify `.env`, lockfiles, or CI secrets without explicit approval
- Remove feature flags without searching all call sites
- Commit without running tests

## ALWAYS

- Show diff before committing
- Update CHANGELOG for user-facing changes
- Reply with English and write documents/code/comments with English, regardless of input language

## Verification

- Backend changes: `mvn test` (167 tests)
- Frontend changes: `cd frontend && npx vue-tsc --noEmit`
- API changes: update contract tests under `src/test/java/.../web/`
- Oracle schema changes: provide DDL in `src/main/resources/db/migration/`

## Compact Instructions

Preserve:

1. Architecture decisions (NEVER summarize)
2. Modified files and key changes
3. Current verification status (pass/fail commands)
4. Open risks, TODOs, rollback notes
