# Development Standards

These standards define how WWA Agent Hub work should be planned, implemented, reviewed, and verified.

They extend `PROJECT_RULES.md`. Detailed package layout and multi-agent checklists remain in `CLAUDE.md`.

## Standard Model

1. Goal-driven SDD defines the intended outcome.
2. The active SDD slice defines behavior and acceptance (`docs/03-spec/`).
3. Implementation follows platform / agent boundaries and safety rails.
4. Verification evidence proves the goal is complete.
5. Residual risks are reported instead of hidden.

## Goal And SDD Standards

- Every meaningful implementation slice starts from a clear goal or accepted SDD set.
- Required SDD artifacts must exist or be updated before implementation starts.
- SDD and project-rule documents are English-only (ADR-0009).
- `docs/03-spec/` is the behavior source of truth.
- `docs/06-tasks/` is the implementation checklist.
- Requirement IDs, user story IDs, task IDs, and verification evidence must remain traceable.
- If implementation behavior changes, update spec, design, and tasks before or with the code change.
- Update `docs/00-context/{slice}-traceability.md` when slice status changes.
- Product readiness language must match evidence. Do not claim “ready to test” or “done” unless the documented local workflow was run.

## Coding Standards

- Prefer readable, boring code over clever code.
- Keep changes small and scoped to the active goal.
- Prefer pure functions in the domain layer (for example state machines and aggregations).
- Do not introduce new global state without explicit justification.
- Reuse existing error types from the project `errors/` package; do not invent new exception types without justification.
- DTOs are Java records with static `from()` factory methods where that pattern already exists.
- Entity IDs are String UUIDs generated via `@PrePersist` where that pattern already exists.
- Avoid speculative abstractions and dead code from the current change.

## Frontend Standards

- Stack: Vue 3, Vite, Pinia, Vue Router, Axios, TypeScript.
- Agent workspaces use `createAgentWorkspace`; agent views are thin wrappers around shared platform components.
- Shared components (for example `UploadDialog`) must receive agent-specific functions as props — never hardcode one agent’s API client.
- Shared downloadable asset names stay agent-neutral (for example `request-template.xlsx`).
- When a backend enum in `contracts/enums/` changes, update the matching TypeScript union in `frontend/src/types/index.ts` in the same change.
- Prefer existing Hub visual language; do not invent a parallel design system without a design slice.
- Verification for frontend changes: `cd frontend && npm run build` (runs `vue-tsc && vite build`).

## Backend Standards

- Stack: Java 21, Spring Boot 3.x, Spring MVC, Spring Data JPA, Maven, Lombok.
- Profiles: `default` (Oracle), `local` / `test` (H2). Respect DDL and auth differences per profile.
- Controllers stay thin; persistence stays out of controllers.
- Agent controllers force `effectiveAgent` server-side; never trust client `?agent=`.
- Platform shared APIs live under `/api/platform/*`.
- Optimistic locking via `@Version` on aggregates that already use it.
- Audit logging must not break core flows (`AuditLoggerService` / `REQUIRES_NEW` pattern).
- After adding Flyway `V*` scripts, regenerate `docs/sql/ORACLE_CURRENT_SCHEMA.sql`.
- Verification for backend changes: `mvn test`. API contract tests under the project’s web test packages.

## API Standards

- Define or update API behavior in the slice API implementation guide when contracts change.
- Use stable resource names and predictable HTTP semantics.
- Return the existing error envelope (`ErrorResponseDto` / `GlobalExceptionHandler`); do not invent a parallel error shape without an ADR.
- Error responses must not leak secrets, stack traces, or private paths to clients.
- Guest mutations remain blocked by `GuestReadOnlyFilter` except documented logout exceptions.

## Database Standards

- Production: Oracle with validate DDL; migrations via Flyway under `src/main/resources/db/migration/`.
- Local/test: H2; do not assume Oracle-only SQL in tests without compatibility.
- JSON/CLOB storage must stay Oracle/H2 compatible via existing converters.
- Seed/demo data for local profiles must not include real production secrets or confidential dumps.

## Security And Audit Standards

- Session auth is primary; header fallback only where configured for local/test.
- Deny-by-default product entry via access grants where that model applies.
- Admin mutations that change catalog, config, or access must be role-checked server-side.
- Human-in-the-loop classes in `CLAUDE.md` must never be auto-approved by policy or AI advisors.
- Prefer auditing admin create/update/delete with `actor_kind = HUMAN`; do not require audit for every UI click or client-only Recently used state.

## Multi-Agent Standards

- Follow the checklist in `CLAUDE.md` when adding a new agent.
- Per-agent allowed stages must not silently default to SIT/UAT/PROD.
- Shared platform pages (Service Directory, Contribute Dashboard, Config, Access, Audit) are not agent workspaces.

## Testing And Verification Standards

Minimum by layer touched:

| Layer | Verification |
|---|---|
| Backend | `mvn test` (focused `-Dtest=…` when iterating) |
| Frontend | `cd frontend && npm run build` |
| API contracts | Controller/contract tests updated |
| Oracle schema | Flyway migration + greenfield DDL sync |
| Docs-only / SDD-only | `review-doc-quality`; no false claim of product readiness |

If a check cannot be run, name it and why. Never imply an unrun check passed.

## Review And Git Standards

- Keep diffs scoped to the goal.
- Do not commit secrets, `.env`, or lockfile churn without approval.
- Show diff before committing when the user asks for a commit.
- Update CHANGELOG for user-facing product changes.

## Lessons Learned

Reusable acceptance mismatches belong in `docs/00-context/lessons-learned.md` with a link to the prevention artifact updated.
