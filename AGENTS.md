# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Required Reading

Before non-trivial changes, read:

- `docs/00-context/AGENT_HANDOFF.md` — **first in every new session**
- `PROJECT_RULES.md`
- `DEVELOPMENT_STANDARDS.md`
- `docs/00-context/sdd-profile.md`
- `docs/SDD-BOOTSTRAP.md` when generating or updating slice SDD
- Relevant slice docs and ADRs

## Agent Handoff

- New session: read `AGENT_HANDOFF` before continuing work; do not rely on chat memory.
- Ending a session with progress: update `AGENT_HANDOFF.md` last (status, done, next, blockers, verification).
- Optional archives: `docs/00-context/handoffs/`. See ADR-0008.
- For remote/async agents, also pin an execution manifest under `docs/00-context/execution-manifests/`.
- Project rules and SDD docs are English-only (ADR-0009).

# Project Contract

## Build And Test

- Backend test: `mvn test`
- Backend local run: `mvn spring-boot:run -Dspring-boot.run.profiles=local`
- Frontend install/dev: `cd frontend && npm install && npm run dev`
- Frontend build/typecheck: `cd frontend && npm run build`

## SDD Workflow Gate

This repository should be operated in strict Spec Driven Development mode for non-trivial or user-facing changes.

- The active project profile is `docs/00-context/sdd-profile.md`.
- Before implementation, create or update the relevant SDD artifacts under `docs/01-requirements`, `docs/02-user-stories`, `docs/03-spec`, `docs/04-architecture`, `docs/05-design`, and `docs/06-tasks`.
- For full SDD generation, use `wwa-sdd-generate-all` (see `docs/SDD-BOOTSTRAP.md`) and report skill-chain evidence per `docs/00-context/checklists/sdd-generation-gate.md`.
- SDD and project-rule documents are English-only (ADR-0009).
- If a change has already been implemented without SDD artifacts, backfill the full SDD chain immediately and mark the documents as `Backfilled` rather than pretending they preceded the code.
- Treat SDD documents as the primary source of change intent and scope. Code, tests, and changelog entries must trace back to the SDD artifacts.
- For small bug fixes, copy edits, or metadata-only cleanup, update the nearest existing SDD artifact only when behavior or scope changes.
- Do not add a new user-facing feature as code-only work.

## Context Engineering And ADRs

- Use `docs/00-context/` as the durable project context layer for background, terminology, boundaries, onboarding knowledge, and cross-agent working rules.
- Use `docs/00-context/decisions/` for Architecture Decision Records (ADRs).
- Before changing architecture, platform boundaries, security posture, data ownership, integrations, or shared agent conventions, read the relevant context documents and ADRs.
- Capture significant new or reversed decisions as ADRs instead of leaving rationale only in chat, PRs, or implementation notes.
- Keep SDD artifacts as the source of feature scope; use ADRs for the "why" behind architecture and cross-cutting choices.
- For reusable ADR/context workflow guidance, use `.agents/skills/context-engineering-adr/`.

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
