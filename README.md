# WWA Agent Workspace Hub

This repository hosts the **WWA Agent Workspace Hub** — a multi-agent platform for controlled, human-in-the-loop operational workflows. It currently contains three active agent workspaces:

| Agent | Purpose | Stages |
|-------|---------|--------|
| **Build Agent** | DEV-stage build and packaging workflow with task-level execution and review controls | DEV |
| **Deployment Agent** | Release orchestration — creating, running, reviewing, and auditing deployment rundowns | SIT, UAT, PROD |
| **Testing Agent** | iSeries A/B testing — tracking and progressing test rundowns by program level | UAT |

This README reflects the current repository code and the current design baseline in [`docs/05-design/design.md`](docs/05-design/design.md).

## Current Baseline

- Platform shell name: **WWA Agent Workspace Hub** (`WWA`)
- Active agent workspaces: **Build Agent**, **Deployment Agent**, **Testing Agent**
- Current technical identifiers:
  - repository / artifact: `deployment-agent`
  - Java package: `com.wwa.deploymentagent`
  - Deployment Agent: route `/wwa/deployment-agent`, API `/api/deployment-agent`
  - Testing Agent: route `/wwa/testing-agent`, API `/api/testing-agent`
  - Build Agent: route `/wwa/build-agent`, API `/api/build-agent`
  - Shared platform capabilities: API `/api/platform`
- Current implementation status: MVP release orchestration, iSeries A/B testing workspace, scoped access-governance foundations, template-based rundown creation, rundown archive lifecycle, scoped configuration management, and WWA shell integration

## What Is Implemented Today

### Platform (shared across all agents)

- Session-based login through a configurable authentication-provider abstraction (`TeamBookAuthenticationProvider` in code)
- Deny-by-default product access via local `AccessGrant` records
- Effective auth/session payload with compatibility `role`, plus `roles[]`, `permissions[]`, and `scopes[]`
- Scoped visibility and delegated administration based on `Application + SNOW Group`
- Configuration Management with scoped component overrides: `Platform Default`, `Application Default`, `SNOW Group Default`, `Agent Override`
- Encrypted storage for sensitive configuration values
- Audit Log with `application`, `snowGroup`, and `agent` trace fields
- WWA Access Management for listing, creating, updating, suspending, reactivating, and directory-searching access grants
- Shared `UploadDialog` component with agent-injected API functions and per-agent stage restrictions

### Deployment Agent

- Release-flow summary and detail pages inside the WWA shell
- Stitched rollout views that group related SIT / UAT / PROD uploads together
- Attempt-aware rundown history with `latest` and `history` views for repeated stage uploads
- Excel upload flow using the fixed `AMH_HCC_task` worksheet plus downloadable template
- Template-based rundown creation through `POST /api/deployment-agent/release-flows/from-template`
- Task lifecycle actions for edit input, start MANUAL execution, submit AUTO execution, record results, and apply review decisions
- Execution history per task attempt, including reruns and external job links
- Rundown-level controls for editing scope metadata, starting deployment, marking failed, archiving, restoring, and purging

### Build Agent

- DEV-only build workspace with upload, summary, and detail pages inside the WWA shell
- Upload flow reusing the shared Excel template while forcing `agent = "build-agent"` and `stage = "DEV"` server-side
- Task lifecycle actions for edit input, start MANUAL execution, submit AUTO execution, record results, view activity, and apply review decisions
- Attempt-aware detail view that keeps repeated DEV uploads grouped under the same workflow summary while exposing request attempts in the detail page
- Shared audit/access/config/session platform services under `/api/platform/*`

### Testing Agent

- iSeries A/B testing workspace for tracking test rundowns by program level
- UAT-only stage (SIT and PROD are not applicable)
- Separate summary and detail views with testing-specific descriptions
- Isolated data path — backend controllers force `AgentId.TESTING_AGENT`, frontend uses dedicated API client and Pinia store
- Shared upload, template download, task lifecycle, archive/restore/purge, and rundown editing with Deployment Agent via common domain services

## Important Current Boundaries

- Real Team Book integration is still future work. Current environments use the provider abstraction, and local/test runs use `StubTeamBookAuthenticationProvider`.
- Product authorization is owned by Deployment Agent. Enterprise identity is authenticated first, then resolved through local Access Grants.
- Primary authorization scope is currently `Application + SNOW Group`. `Agent` is runtime execution context, not the primary auth boundary.
- Template Management is only partially backed by the backend today:
  - creating a rundown from a template is real backend behavior
  - template authoring and storage in the current UI are still frontend-local draft data
  - the template upload tab is not backed by a dedicated template-import API yet
- AUTO execution callback ingestion is not the main model. A polling monitor exists in code, but `execution.monitor.enabled=false` by default.
- The `test` backend profile expects an Oracle schema that already exists. Use the `local` profile for the fastest local setup.

## Technology Stack

- Frontend: Vue 3.4, Vue Router 4.3, Pinia 2.1, Axios, Vite 5
- Backend: Java 21, Spring Boot 3.2.0, Spring MVC, Spring Security, Spring Data JPA
- Database:
  - default profile: Oracle
  - `local` / `test`: H2
- Build tooling:
  - backend: Maven
  - frontend: npm

## Repository Layout

The current implementation is split between a Spring Boot backend and a Vue frontend:

```text
src/main/java/com/wwa/deploymentagent/
  agents/           Agent-specific controllers and domain helpers
    build/
    deployment/
    testing/
  config/           Spring configuration
  contracts/        DTOs, enums, request/response contracts, user context
  domain/           Shared business logic and persistence-facing services
    audit/
    auth/
    configuration/
    decision/
    execution/
    fileimport/
    releaseflow/
    task/
  errors/           Shared application exceptions
  platform/
    web/shared/     Platform capability controllers (`/api/platform/*`)
    web/security/   Session/header auth filters and Spring Security glue
  util/             Shared converters and helpers

src/main/resources/
  application.properties
  application-local.properties
  db/migration/     SQL change history kept with the repo

frontend/
  src/
    api/            Axios clients and endpoint wrappers
    agents/         Agent-specific entrypoints, API wrappers, and views
    components/     Dialogs and reusable UI pieces
    config/         WWA shell and agent registry config
    router/         Vue Router setup
    stores/         Pinia stores
    types/          Frontend TypeScript contracts
    views/          Page-level views

docs/
  03-spec/
  04-architecture/
  05-design/
  06-tasks/
  UAT_RUNBOOK.md
  sql/ORACLE_CURRENT_SCHEMA.sql
```

## Running Locally

This repository is not wired as a root `pnpm` workspace. Start the backend and frontend separately.

### 1. Backend (recommended local path)

Use the `local` profile to run against in-memory H2:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

What this does today:

- starts the backend on `http://localhost:8080`
- uses H2 instead of Oracle
- keeps the current session-login flow
- bootstraps local Access Grants for the known stub employees

### 2. Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend details:

- Vite dev server runs on `http://localhost:5173`
- `/api` is proxied to `http://localhost:8080`
- the frontend uses `withCredentials: true`, so session cookies are required

### 3. Oracle-backed backend run

The `test` profile expects Oracle and validates the schema on startup.

```bash
export DB_URL='jdbc:oracle:thin:@localhost:1521/XEPDB1'
export DB_USERNAME='da_user'
export DB_PASSWORD='changeme'
mvn spring-boot:run -Dspring-boot.run.profiles=test
```

Before using the `test` profile:

- create the schema from [`docs/sql/ORACLE_CURRENT_SCHEMA.sql`](docs/sql/ORACLE_CURRENT_SCHEMA.sql), or
- provision an equivalent Oracle schema out of band

## Local Login Accounts

In `local` / `test`, the configured stub authentication provider recognizes these known users and bootstrap grants are created for them automatically. Any non-empty password works.

| Employee ID | Display Name | Role |
| --- | --- | --- |
| `emp-001` | Alice Park (Developer) | `DEVELOPER` |
| `emp-002` | Bob Kim (Tech Lead) | `TL` |
| `emp-003` | Carol Lee (DevOps Admin) | `DEVOPS_ADMIN` |
| `emp-004` | David Cho (Auditor) | `AUDIT` |
| `emp-005` | Eve Yoon (Management) | `MANAGEMENT` |

The stub directory search also includes additional employees such as `emp-006`, `emp-007`, and `emp-008` for Access Management add-user flows.

## Verification Commands

Backend:

```bash
mvn test
```

Frontend:

```bash
cd frontend
npm run build
```

`npm run build` currently performs both `vue-tsc` and the Vite production build.

## Key UI / API Surfaces

WWA shell pages currently exposed by the frontend:

- `/wwa/home` — agent workspace selector
- `/wwa/build-agent` — Build Agent summary and detail
- `/wwa/deployment-agent` — Deployment Agent summary and detail
- `/wwa/testing-agent` — Testing Agent summary and detail
- `/wwa/template-management` — template authoring (platform)
- `/wwa/configuration-management` — scoped config management (platform)
- `/wwa/audit-log` — audit trail viewer (platform)
- `/wwa/access-management` — user access grants (platform)

Main backend API groups:

Platform:
- `/api/platform/auth`
- `/api/platform/access-grants`
- `/api/platform/config`
- `/api/platform/audit-logs`
- `/api/platform/upload/template`

Deployment Agent:
- `/api/deployment-agent/upload`
- `/api/deployment-agent/release-flows`
- `/api/deployment-agent/tasks`

Testing Agent:
- `/api/testing-agent/upload`
- `/api/testing-agent/release-flows`
- `/api/testing-agent/tasks`

Build Agent:
- `/api/build-agent/upload`
- `/api/build-agent/release-flows`
- `/api/build-agent/tasks`

## Source Documents

- Product requirements: [`docs/03-spec/spec.md`](docs/03-spec/spec.md)
- Architecture baseline: [`docs/04-architecture/architecture.md`](docs/04-architecture/architecture.md)
- Detailed design baseline: [`docs/05-design/design.md`](docs/05-design/design.md)
- Remaining implementation work: [`docs/06-tasks/tasks.md`](docs/06-tasks/tasks.md)
- UAT setup/runbook: [`docs/UAT_RUNBOOK.md`](docs/UAT_RUNBOOK.md)
