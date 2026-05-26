# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

# Project Contract

## Build And Test

- Backend test: `mvn test`
- Backend local run: `mvn spring-boot:run -Dspring-boot.run.profiles=local`
- Frontend install/dev: `cd frontend && npm install && npm run dev`
- Frontend build/typecheck: `cd frontend && npm run build`

## SDD Workflow Gate

This repository should be operated in strict Spec Driven Development mode for non-trivial or user-facing changes.

- Before implementation, create or update the relevant SDD artifacts under `docs/01-requirements`, `docs/02-user-stories`, `docs/03-spec`, `docs/04-architecture`, `docs/05-design`, and `docs/06-tasks`.
- If a change has already been implemented without SDD artifacts, backfill the full SDD chain immediately and mark the documents as `Backfilled` rather than pretending they preceded the code.
- Treat SDD documents as the primary source of change intent and scope. Code, tests, and changelog entries must trace back to the SDD artifacts.
- For small bug fixes, copy edits, or metadata-only cleanup, update the nearest existing SDD artifact only when behavior or scope changes.
- Do not add a new user-facing feature as code-only work.

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
