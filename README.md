# Deployment Agent

Deployment Agent is the first active workspace inside the **WWA Agent Workspace Hub**. It is a controlled, human-in-the-loop release orchestration product for creating, running, reviewing, and auditing deployment rundowns across SIT, UAT, and PROD.

This README reflects the current repository code and the current design baseline in [`docs/05-design/design.md`](docs/05-design/design.md).

## Current Baseline

- Product/workspace name: **Deployment Agent**
- Platform shell name: **WWA Agent Workspace Hub** (`WWA`)
- Current technical identifiers remain unchanged:
  - repository / artifact: `deployment-agent`
  - frontend route: `/wwa/deployment-agent`
  - API base path: `/api/deployment-agent`
  - Java package: `com.wwa.deploymentagent`
- Current implementation status: MVP release orchestration plus scoped access-governance foundations, template-based rundown creation, rundown archive lifecycle, scoped configuration management, and WWA shell integration

## What Is Implemented Today

- Session-based login through a configurable authentication-provider abstraction (`TeamBookAuthenticationProvider` in code)
- Deny-by-default product access via local `AccessGrant` records
- Effective auth/session payload with compatibility `role`, plus `roles[]`, `permissions[]`, and `scopes[]`
- Scoped visibility and delegated administration based on `Application + SNOW Group`
- Release-flow summary and detail pages inside the WWA shell
- Stitched rollout views that group related SIT / UAT / PROD uploads together
- Attempt-aware rundown history with `latest` and `history` views for repeated stage uploads
- Excel upload flow using the fixed `AMH_HCC_task` worksheet plus downloadable template
- Template-based rundown creation through `POST /api/deployment-agent/release-flows/from-template`
- Task lifecycle actions for edit input, start MANUAL execution, submit AUTO execution, record results, and apply review decisions
- Execution history per task attempt, including reruns and external job links
- Rundown-level controls for editing scope metadata, starting deployment, marking failed, archiving, restoring, and purging
- Configuration Management with scoped component overrides:
  - `Platform Default`
  - `Application Default`
  - `SNOW Group Default`
  - `Agent Override`
- Encrypted storage for sensitive configuration values
- Audit Log with `application`, `snowGroup`, and `agent` trace fields
- WWA Access Management for listing, creating, updating, suspending, reactivating, and directory-searching access grants

## Important Current Boundaries

- Real Team Book integration is still future work. Current environments use the provider abstraction, and local/test runs use `StubTeamBookAuthenticationProvider`.
- Product authorization is owned by Deployment Agent. Enterprise identity is authenticated first, then resolved through local Access Grants.
- Primary authorization scope is currently `Application + SNOW Group`. `Agent` is runtime execution context, not the primary auth boundary.
- Template Management is only partially backed by the backend today:
  - creating a rundown from a template is real backend behavior
  - template authoring and storage in the current UI are still frontend-local draft data
  - the template upload tab is not backed by a dedicated template-import API yet
- AUTO execution callback ingestion is not the main model. A polling monitor exists in code, but `execution.monitor.enabled=false` by default.
- The default backend profile expects an Oracle schema that already exists. Use the `local` profile for the fastest local setup.

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
  config/           Spring configuration
  contracts/        DTOs, enums, request/response contracts, user context
  domain/           Business logic and persistence-facing services
    audit/
    auth/
    configuration/
    decision/
    execution/
    fileimport/
    releaseflow/
    task/
  errors/           Shared application exceptions
  util/             Shared converters and helpers
  web/
    controller/     Current HTTP entry points
    exception/      Global exception handling
    security/       Session/header auth filters and Spring Security glue

src/main/resources/
  application.properties
  application-local.properties
  db/migration/     SQL change history kept with the repo

frontend/
  src/
    api/            Axios clients and endpoint wrappers
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

The default profile expects Oracle and validates the schema on startup.

```bash
export DB_URL='jdbc:oracle:thin:@localhost:1521/XEPDB1'
export DB_USERNAME='da_user'
export DB_PASSWORD='changeme'
mvn spring-boot:run
```

Before using the default profile:

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

- `/wwa/home`
- `/wwa/deployment-agent`
- `/wwa/template-management`
- `/wwa/configuration-management`
- `/wwa/audit-log`
- `/wwa/access-management`

Main backend API groups:

- `/api/deployment-agent/auth`
- `/api/deployment-agent/access-grants`
- `/api/deployment-agent/upload`
- `/api/deployment-agent/release-flows`
- `/api/deployment-agent/tasks`
- `/api/deployment-agent/config`
- `/api/deployment-agent/audit-logs`

## Source Documents

- Product requirements: [`docs/03-spec/spec.md`](docs/03-spec/spec.md)
- Architecture baseline: [`docs/04-architecture/architecture.md`](docs/04-architecture/architecture.md)
- Detailed design baseline: [`docs/05-design/design.md`](docs/05-design/design.md)
- Remaining implementation work: [`docs/06-tasks/tasks.md`](docs/06-tasks/tasks.md)
- UAT setup/runbook: [`docs/UAT_RUNBOOK.md`](docs/UAT_RUNBOOK.md)
