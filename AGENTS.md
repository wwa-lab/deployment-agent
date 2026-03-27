# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

# Project Contract

## Build And Test

- Backend test: `mvn test`
- Backend local run: `mvn spring-boot:run -Dspring-boot.run.profiles=local`
- Frontend install/dev: `cd frontend && npm install && npm run dev`
- Frontend build/typecheck: `cd frontend && npm run build`

## Architecture Boundaries

- REST controllers live in `src/main/java/com/wwa/deploymentagent/web/controller/`
- Domain logic lives in `src/main/java/com/wwa/deploymentagent/domain/`
- Do not put persistence logic in controllers
- Shared types live in `src/main/java/com/wwa/deploymentagent/contracts/`
- Security filters live in `src/main/java/com/wwa/deploymentagent/web/security/`
- Spring configuration lives in `src/main/java/com/wwa/deploymentagent/config/`
- Frontend source lives in `frontend/src/`

## Coding Conventions

- Prefer pure functions in domain layer
- Do not introduce new global state without explicit justification
- Reuse existing error types from `src/main/java/com/wwa/deploymentagent/errors/`

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
- API changes: update controller/contract tests under `src/test/java/com/wwa/deploymentagent/web/`
- UI changes: `cd frontend && npm run build` and capture before/after screenshots
- Oracle schema changes: provide DDL in `src/main/resources/db/migration/`

## Compact Instructions

Preserve:

1. Architecture decisions (NEVER summarize)
2. Modified files and key changes
3. Current verification status (pass/fail commands)
4. Open risks, TODOs, rollback notes
