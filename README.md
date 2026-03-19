# Deployment Agent

Deployment Agent is a workflow-driven platform for managing deployment requests, template-based execution, and delivery process validation.

## Overview

This repository is the main engineering workspace for Deployment Agent.

It is designed to support structured delivery workflows, including:

- deployment request submission (Excel upload)
- template-driven parameter configuration
- manual and automated task execution (Jenkins/Ansible)
- execution tracking with external job URL linkage
- human-in-the-loop decision gates (approve/reject/rerun/skip)
- session-based authentication (Team Book login)
- audit and traceability tied to authenticated identity
- role-based access control (Developer, TL, DevOps Admin, Audit, Management)

## Technology Stack

- **Backend**: Java 21 / Spring Boot 3.2.4 / Spring Data JPA / Maven / Lombok
- **Frontend**: Vue 3 (Composition API) / Vite 5 / Pinia / Vue Router 4 / Axios
- **Database**: Oracle (production) / H2 in-memory (tests)
- **Auth**: Session-based login (Team Book provider interface) with header fallback for tests

## Repository Structure

```text
src/main/java/com/wwa/deploymentagent/
  config/           # Spring configuration (SecurityConfig, RestClientConfig)
  contracts/        # DTOs, enums, UserContext
  domain/           # Business logic
    audit/          # Audit logging
    auth/           # Team Book authentication
    configuration/  # System configuration
    decision/       # Decision engine + progression
    execution/      # AUTO execution adapters (Jenkins/Ansible)
    fileimport/     # Excel parsing + import
    releaseflow/    # Release flow lifecycle
    task/           # Task management
  errors/           # Custom exception hierarchy
  util/             # Utilities (JsonAttributeConverter)
  web/
    controller/     # REST controllers (7)
    exception/      # GlobalExceptionHandler
    security/       # SessionAuthFilter, HeaderAuthFilter

src/main/resources/
  application.properties
  db/migration/     # Oracle DDL scripts

src/test/java/      # JUnit 5 integration tests (167)
src/test/resources/ # H2 test configuration

frontend/           # Vue 3 SPA
  src/
    api/            # Axios API client + endpoint modules
    assets/         # Global CSS
    components/     # Dialog components
    router/         # Vue Router with auth guards
    stores/         # Pinia stores
    types/          # TypeScript type definitions
    views/          # Page-level components

docs/               # Product, architecture, and workflow documents
```

## Build & Run

### Backend
```bash
mvn test                    # Run 167 tests (requires internet on first run)
mvn spring-boot:run         # Start backend on :8080
```

### Frontend
```bash
cd frontend
npm install                 # Install dependencies
npm run dev                 # Start Vite dev server on :5173 (proxies /api → :8080)
npx vue-tsc --noEmit        # TypeScript type check
```

## Authentication

The application uses session-based login against the company Team Book.

- **Dev/Test**: `StubTeamBookAuthenticationProvider` accepts any password for 5 hardcoded users:
  - `emp-001` (Developer), `emp-002` (TL), `emp-003` (DevOps Admin), `emp-004` (Audit), `emp-005` (Management)
- **Production**: Requires `RealTeamBookAuthenticationProvider` implementation (pending Team Book API contract)
- **Test suite**: Uses header-based auth fallback (`app.auth.header-fallback-enabled=true`)

## Current Status

- MANUAL workflow: **UAT-ready** (upload, execute, review, decide, progress)
- AUTO execution: **UAT-ready with stub credentials** (submit to Jenkins/Ansible, failure handling, external job URL)
- Authentication: **UAT-ready with stub provider** (login, session, logout, role-based access)
- Audit attribution: **UAT-ready** (tied to authenticated session identity)

### Pending External Dependencies
1. Team Book API contract (endpoint URL, request/response format, role mapping)
2. Jenkins/Ansible credentials (entered via Config admin page at runtime)
