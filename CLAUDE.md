# CLAUDE.md

- When asked to 'review' a document, ONLY produce a review/quality report. Do NOT modify or rewrite the original document unless explicitly asked to fix or update it.
- AddAfter implementing code changes, always run the full test suite and typecheck (`npm test`, `npm run typecheck` or equivalent) before reporting completion. Report exact pass/fail counts.
- This project uses TypeScript with Fastify (not Express). When generating HTTP handlers, routes, or middleware, use Fastify patterns and types. The test database uses sql.js (not better-sqlite3).
- When generating or updating documents (spec.md, design.md, architecture.md, user-stories.md, implementation-plan.md), always write them to the `docs/` directory and confirm the file path in your response.

- Review docs/design.md for completeness and consistency against docs/spec.md. Write your findings to docs/reviews/design-review.md. Do NOT modify design.md itself.

- Implement the next 5 tasks from docs/implementation-plan.md. After implementing, run npm test and npx tsc --noEmit. Fix any failures before proceeding. Then report what passed and what's next.

- Before we start, verify the environment: check Node/Java version, run npm install (or mvn dependency:resolve), confirm tests pass, and report any issues.

- Read the implementation plan in PLAN.md. Identify all tasks that have no dependencies on each other and can be implemented in parallel. Group them into independent batches. For each batch, spawn a sub-agent with these instructions: implement the assigned tasks, write tests, run all tests with `npm test`, and report results. After all agents complete, run the full test suite and typecheck to verify integration. Fix any conflicts between parallel changes.

- Execute the following pipeline autonomously, stopping only if a quality gate fails:

1. SPEC PHASE: Read requirements/ and generate spec.md. Self-review for completeness — every requirement must trace to a spec section.
2. ARCHITECTURE PHASE: Generate architecture.md from spec.md. Self-review for consistency with spec.
3. DESIGN PHASE: Generate design.md from architecture.md. Self-review against architecture.
4. TASK PHASE: Generate implementation tasks from design.md. Verify every design component maps to at least one task.
5. IMPLEMENT PHASE: Implement all tasks in dependency order. After each batch: run `npm test`, `npm run typecheck`, and `npm run lint`. Only proceed if all pass.
6. FIDELITY REVIEW: Compare final implementation against design.md and report any gaps.

At each gate, write a brief pass/fail status to PIPELINE_LOG.md before continuing.

- Implement all tasks listed in PLAN.md for the current phase. After implementing each task:
1. Run `npm run typecheck` — if errors, fix them immediately and re-run until clean.
2. Run `npm test` — if failures, read the error output, diagnose the root cause, fix the code, and re-run. Repeat up to 5 times per failing test.
3. Run `npm run lint` — auto-fix what you can, manually fix the rest.

If a dependency or environment issue blocks you (e.g., incompatible Node version, missing native module), proactively find an alternative library and swap it in rather than stopping. Document any such substitutions in DECISIONS.md. Do not ask me for help unless you've exhausted 5 fix attempts on the same error.


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


