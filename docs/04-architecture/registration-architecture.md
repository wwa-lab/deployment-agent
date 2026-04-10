# System Architecture: Agent/Tool Registry & Contribution Board

**Date:** 2026-04-10
**Status:** Draft
**Source:** registration-spec.md (primary), architecture.md (baseline)

---

## Platform Context

The Agent/Tool Registry and Contribution Board are **platform-level shared capabilities**, positioned alongside existing platform capabilities (Configuration Management, Access Management, Audit Log, Template Management).

```
WWA Agent Workspace Hub
├── Agent Workspaces (runtime)
│   ├── Deployment Agent  /api/deployment-agent/*
│   └── Testing Agent     /api/testing-agent/*
│
├── Shared Capabilities (existing)
│   ├── Configuration Management
│   ├── Access Management
│   ├── Audit Log
│   └── Template Management
│
└── Shared Capabilities (NEW)
    ├── Agent/Tool Registry         /api/platform/registry
    └── Contribution Board          /api/platform/contributions
```

- **WWA Agent Workspace Hub** continues to own authentication, navigation, and platform-level controls.
- **Registry** adds a metadata layer for ownership tracking. It does not modify the runtime agent architecture.
- **Contribution Board** derives activity data from existing `Request` and `Task` tables via read-only aggregate queries.

---

## Overview

- **Architecture Summary**: The feature adds a new `registration` domain to the existing backend with a new platform-level API prefix (`/api/platform/`), a single JPA entity for registry entries, and two frontend pages. It integrates with the existing audit log system and reads from existing request/task tables for contribution metrics. No existing domain services, entities, or repositories are modified.
- **Design Objective**: Provide clear ownership visibility for platform capabilities with minimal architectural footprint — one new entity, one new domain package, two new controllers, two new frontend views.
- **Architectural Style**: Layered service architecture, consistent with the existing project. The new domain follows the same patterns as `auth/` (AccessGrant) and `configuration/` (ConfigurationItem).

---

## Source Specification

- **Feature Name**: Agent/Tool Registry & Contribution Board
- **Scope Summary**: A CRUD registry for platform agents and tools with mandatory ownership tracking, plus a read-only contribution board aggregating activity metrics by owner. The registry is metadata-only and does not create runtime workspace infrastructure.

---

## Architectural Drivers

### Key Functional Drivers

- Registry CRUD with role-gated write access (DEVOPS_ADMIN only)
- Mandatory ownership tracking on all new entries
- Contribution metrics derived from existing runtime data (request/task counts per agent)
- Audit trail for all registry mutations
- Initial population of existing agents at feature launch

### Key Non-Functional Drivers

- Must work across all three Spring profiles (default/Oracle, local/H2, test/H2)
- Optimistic locking for concurrent edit safety
- Audit logging must not abort business operations on audit failure
- New `/api/platform/` path prefix must be permitted by Spring Security

### Constraints and Assumptions

- The registry is a metadata directory — it does not create backend controllers, frontend routes, or Pinia stores for registered capabilities
- The existing static `agentRegistry.ts` array and `AgentId.java` constants remain unchanged
- `[ASSUMPTION]` Contribution board aggregate queries perform adequately because the registry and request data sets are small (tens of agents, not thousands)
- `[ASSUMPTION]` The new `/api/platform/` prefix requires an explicit update to Spring Security configuration since no existing controller uses this prefix

---

## Technology Stack

No changes. Same stack as the existing platform:

| Layer | Technology |
|-------|-----------|
| Frontend | Vue 3 (Composition API) · Vite 5 · Pinia · Vue Router 4 · Axios |
| Backend | Java 21 · Spring Boot 3.2.0 · Spring MVC · Spring Data JPA · Spring Security |
| Database | Oracle (production) · H2 in-memory (local, test) |
| Build | Maven 3 (backend) · npm (frontend) |
| Auth | Session-based login via SessionAuthFilter |

---

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│  Users                                                            │
│  DevOps Admin (write) · All Authenticated Users (read)            │
└──────────────────────┬────────────────────────────────────────────┘
                       │ HTTPS
                       ▼
┌──────────────────────────────────────────────────────────────────┐
│  Web App (Vue 3 SPA)                                              │
│                                                                   │
│  ┌──────────────────────────────┐  ┌────────────────────────────┐ │
│  │  Agent Workspace Views       │  │  Platform Capability Views  │ │
│  │  (existing, UNCHANGED)       │  │  (existing + NEW)           │ │
│  │  ReleaseFlowSummaryView      │  │  ConfigAdminView            │ │
│  │  TestingAgentSummaryView     │  │  AccessManagementView       │ │
│  │  ...                         │  │  AuditLogView               │ │
│  │                              │  │  RegistryView        ← NEW  │ │
│  │                              │  │  ContributionBoardView← NEW │ │
│  └──────────────────────────────┘  └─────────────┬──────────────┘ │
│                                                   │               │
│  ┌────────────────────────────────────────────────▼──────────────┐ │
│  │  API Clients                                                  │ │
│  │  client.ts (/api/deployment-agent)  UNCHANGED                 │ │
│  │  testingAgentClient.ts              UNCHANGED                 │ │
│  │  platformClient.ts (/api/platform)  ← NEW                    │ │
│  └───────────────────────────────────────────────────────────────┘ │
└──────────────────────┬────────────────────────────────────────────┘
                       │ REST / JSON + Session Cookie
                       ▼
┌──────────────────────────────────────────────────────────────────┐
│  API Service (Spring Boot 3)                                      │
│                                                                   │
│  ┌─────────────────────────┐  ┌───────────────────────────────┐   │
│  │  Agent Controllers      │  │  Platform Controllers          │  │
│  │  (existing, UNCHANGED)  │  │  (existing + NEW)              │  │
│  │  /api/deployment-agent/ │  │  ConfigurationController       │  │
│  │  /api/testing-agent/    │  │  AccessGrantController         │  │
│  │                         │  │  AuditLogController            │  │
│  │                         │  │  RegistryController     ← NEW  │  │
│  │                         │  │  ContributionController ← NEW  │  │
│  └────────────┬────────────┘  └──────────────┬────────────────┘   │
│               │                               │                   │
│               └───────────┬───────────────────┘                   │
│                           ▼                                       │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │  Domain Services                                           │   │
│  │                                                            │   │
│  │  Existing (UNCHANGED):                                     │   │
│  │    ReleaseFlowService · TaskService · ImportService         │   │
│  │    AuditLoggerService · ConfigurationService · AuthService  │   │
│  │                                                            │   │
│  │  New:                                                      │   │
│  │    RegistryService            ← NEW (CRUD + validation)    │   │
│  │    ContributionBoardService   ← NEW (read-only aggregation)│   │
│  └────────────────────────────────────────────────────────────┘   │
│                           │                                       │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │  Persistence                                               │   │
│  │                                                            │   │
│  │  Existing Repositories (UNCHANGED):                        │   │
│  │    ReleaseFlowRepository · RequestRepository               │   │
│  │    TaskRepository · AuditLogRepository                     │   │
│  │                                                            │   │
│  │  New:                                                      │   │
│  │    CapabilityRegistrationRepository  ← NEW                 │   │
│  └──────────────┬─────────────────────────────────────────────┘   │
└─────────────────┼─────────────────────────────────────────────────┘
                  │ JDBC
                  ▼
┌──────────────────────────────────────────────────────────────────┐
│  Database                                                         │
│  Oracle (prod) / H2 (local, test)                                 │
│                                                                   │
│  Existing tables (UNCHANGED):                                     │
│    DA_RELEASE_FLOW · DA_REQUEST · DA_TASK · DA_AUDIT_LOG          │
│                                                                   │
│  New table:                                                       │
│    DA_CAPABILITY_REGISTRATION  ← NEW                              │
└──────────────────────────────────────────────────────────────────┘
```

### Layer Summary

The feature follows the existing four-layer architecture:

- **Presentation Layer** — Two new Vue 3 views (`RegistryView`, `ContributionBoardView`), a new Axios client (`platformClient.ts`), two new Pinia stores, and two new entries in `platformCapabilities`. Existing views and stores are untouched.
- **API Layer** — Two new Spring MVC controllers under `/api/platform/`. `RegistryController` handles CRUD. `ContributionController` handles the read-only aggregation endpoint. Both use the existing `SessionAuthFilter` for authentication and check roles inline for authorization.
- **Domain Layer** — Two new services: `RegistryService` (CRUD + validation + audit) and `ContributionBoardService` (read-only aggregation from registry + request/task data). Both follow existing service patterns (transactional, constructor-injected).
- **Persistence Layer** — One new JPA entity (`CapabilityRegistration`), one new Spring Data JPA repository (`CapabilityRegistrationRepository`). Existing repositories are used read-only for contribution metrics.

---

## Component Breakdown

### Frontend Components (NEW)

| Component | Responsibility |
|-----------|---------------|
| `platformClient.ts` | Axios instance with `baseURL: '/api/platform'`, same 401 interceptor pattern as existing clients |
| `api/registry.ts` | API functions for registry CRUD (list, get, create, update, activate, deactivate) |
| `api/contributionBoard.ts` | API function for contribution board data |
| `stores/registry.ts` | Pinia store: registrations list, loading state, CRUD actions |
| `stores/contributionBoard.ts` | Pinia store: contribution entries, loading state, fetch action |
| `types/registry.ts` | TypeScript interfaces for registry and contribution data |
| `views/RegistryView.vue` | Registry admin/browse page with table, register/edit dialog, activate/deactivate actions |
| `views/ContributionBoardView.vue` | Contribution board page with summary stats and per-owner cards |

### Frontend Components (MODIFIED)

| Component | Change |
|-----------|--------|
| `config/agentRegistry.ts` | Add two entries to `platformCapabilities` array (Registry, Contribution Board) |
| `router/index.ts` | Add routes for `/wwa/registry` and `/wwa/contribution-board` |

### Backend Components (NEW)

| Component | Package | Responsibility |
|-----------|---------|---------------|
| `CapabilityRegistration` | `domain/registration/` | JPA entity for `DA_CAPABILITY_REGISTRATION` table |
| `CapabilityRegistrationRepository` | `domain/registration/` | Spring Data JPA repository with custom finders |
| `RegistryService` | `domain/registration/` | CRUD operations, validation, audit logging |
| `ContributionBoardService` | `domain/registration/` | Read-only aggregation: group by owner, count requests/tasks |
| `RegistryBootstrapRunner` | `domain/registration/` | `ApplicationRunner` that seeds initial entries (idempotent) |
| `CapabilityRegistrationDto` | `contracts/dto/` | Immutable DTO record with `from()` factory, nested `CreateRequest` and `UpdateRequest` |
| `ContributionBoardEntryDto` | `contracts/dto/` | Immutable DTO record for contribution board response |
| `EntryType` | `contracts/enums/` | Enum: `AGENT`, `TOOL` |
| `RegistryStatus` | `contracts/enums/` | Enum: `ACTIVE`, `INACTIVE` |
| `RegistryController` | `web/controller/` | REST controller at `/api/platform/registry` |
| `ContributionController` | `web/controller/` | REST controller at `/api/platform/contributions` |
| Oracle DDL migration | `resources/db/migration/` | `CREATE TABLE DA_CAPABILITY_REGISTRATION` |

### Backend Components (MODIFIED)

| Component | Change |
|-----------|--------|
| `AuditActionType` enum | Add: `registry_create`, `registry_update`, `registry_activate`, `registry_deactivate` |
| `AuditLoggerService` | Add overload or context-map support for platform-level events that sets `agentName = "platform"` instead of the hardcoded `"deployment-agent"` default |
| Spring Security config | Permit `/api/platform/**` for authenticated users |

### Components NOT Modified

- All existing agent controllers (`ReleaseFlowController`, `TestingAgentReleaseFlowController`, etc.)
- All existing domain services (`ReleaseFlowService`, `TaskService`, etc.)
- All existing entities (`ReleaseFlow`, `Request`, `Task`, etc.)
- All existing frontend views, stores, and API clients for agent workspaces
- `AgentId.java` constants
- `agentRegistry.ts` static agent array (only `platformCapabilities` is extended)

---

## Data Architecture

### Conceptual Entities

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| CapabilityRegistration | A platform agent or tool with ownership metadata | registry_key (unique), name, entry_type (AGENT/TOOL), description, owner_employee_id, owner_display_name, support_contact, status (ACTIVE/INACTIVE), link, note, created_by, created_at, updated_by, updated_at, version |

### State Model

```
        ┌─────────────┐
        │   (new)      │
        └──────┬───────┘
               │ Create
               ▼
        ┌─────────────┐
   ┌───►│   ACTIVE     │◄───┐
   │    └──────┬───────┘    │
   │           │ Deactivate │ Activate
   │           ▼            │
   │    ┌─────────────┐    │
   │    │  INACTIVE    │────┘
   │    └─────────────┘
   │
   │ Same-state transitions are rejected
   │ with a conflict error
   └──────────────────────
```

### Persistence Responsibilities

| Data | Persisted by | Storage |
|------|-------------|---------|
| Registry entries | `RegistryService` via `CapabilityRegistrationRepository` | `DA_CAPABILITY_REGISTRATION` table |
| Audit trail | `AuditLoggerService` (existing) | `DA_AUDIT_LOG` table (existing) |
| Request/task counts | Not persisted by this feature — read-only from existing `DA_REQUEST` and `DA_TASK` tables | Existing tables |

### Contribution Metrics Join Path

The contribution board does not introduce new tables or materialized views. Activity metrics are computed at query time:

```
DA_CAPABILITY_REGISTRATION (registry_key, entry_type = 'AGENT')
         │
         │ registry_key = Request.agent
         ▼
DA_REQUEST (agent column)
         │
         │ request_id FK
         ▼
DA_TASK (belongs to request)
```

---

## Integration Architecture

### Audit Log System (Existing)

- **Interaction Pattern**: `RegistryService` calls `AuditLoggerService.log()` after each successful mutation
- **Data exchanged**: Action type (registry_create/update/activate/deactivate), acting user ID, registry key, timestamp
- **Transaction isolation**: Audit uses `Propagation.REQUIRES_NEW` — audit failure does not abort the registry operation

### Existing Request/Task Data (Read-Only)

- **Interaction Pattern**: `ContributionBoardService` queries `RequestRepository` and `TaskRepository` with aggregate count queries
- **Data exchanged**: Count of requests per `Request.agent` value; count of tasks per request
- **Responsibility boundary**: The contribution feature only reads from these tables. It never writes to or modifies runtime data.

### Authentication System (Existing)

- **Interaction Pattern**: `SessionAuthFilter` populates `UserContext` from HTTP session. Controllers check `userContext.hasRole("DEVOPS_ADMIN")` for write operations.
- **No changes required to the auth system itself.** The new `/api/platform/` prefix must be added to the Spring Security filter chain's permitted paths.

---

## API / Interface Boundaries

### Inbound Interfaces

| Interface | Consumer | Purpose |
|-----------|----------|---------|
| `GET /api/platform/registry` | Frontend RegistryView | List all registry entries |
| `GET /api/platform/registry/{registryKey}` | Frontend RegistryView | Get single entry |
| `POST /api/platform/registry` | Frontend RegistryView (DEVOPS_ADMIN) | Create new entry |
| `PATCH /api/platform/registry/{registryKey}` | Frontend RegistryView (DEVOPS_ADMIN) | Update entry metadata |
| `POST /api/platform/registry/{registryKey}/activate` | Frontend RegistryView (DEVOPS_ADMIN) | Reactivate entry |
| `POST /api/platform/registry/{registryKey}/deactivate` | Frontend RegistryView (DEVOPS_ADMIN) | Deactivate entry |
| `GET /api/platform/contributions` | Frontend ContributionBoardView | Get contribution data |

### Internal Module Boundaries

- `RegistryController` → `RegistryService` → `CapabilityRegistrationRepository` + `AuditLoggerService`
- `ContributionController` → `ContributionBoardService` → `CapabilityRegistrationRepository` + `RequestRepository` + `TaskRepository`
- No cross-dependency between `RegistryService` and `ContributionBoardService`

### Event / Polling / Callback Patterns

None. All operations are synchronous request-response.

---

## Deployment / Environment Considerations

- **Supported Environments**: `default` (Oracle), `local` (H2), `test` (H2) — same as existing platform
- **Schema Migration**: Oracle DDL migration file for the new table. H2 in `local` profile uses `ddl-auto: update`; `test` profile uses `ddl-auto: validate` with `schema.sql`
- **Bootstrap Runner**: `RegistryBootstrapRunner` implements `ApplicationRunner`, runs on `local` and `default` profiles only. Excluded from `test` profile via `@Profile({"default", "local"})`.
- **Vite Proxy**: The existing Vite dev server proxy for `/api` should already forward `/api/platform/` to `:8080`. `[ASSUMPTION]` — Verify during implementation.

---

## Security / Reliability / Observability

### Access Control

- Write operations (create, update, activate, deactivate) require `DEVOPS_ADMIN` role. Controllers check `userContext.hasRole("DEVOPS_ADMIN")` and throw `ForbiddenAppException` on failure.
- Read operations require authentication only. All platform roles have access.
- The `/api/platform/**` path prefix must be added to Spring Security's authenticated path matchers.

### Concurrency Safety

- Optimistic locking via `@Version Long version` on `CapabilityRegistration`. Concurrent writes produce `OptimisticLockConflictException`, handled by `GlobalExceptionHandler` returning HTTP 409.

### Auditability

- All registry mutations are logged via `AuditLoggerService` with `Propagation.REQUIRES_NEW`.
- New audit action types: `registry_create`, `registry_update`, `registry_activate`, `registry_deactivate`.
- Registry audit entries must use `agentName = "platform"` (not `"deployment-agent"`). The current `AuditLoggerService.log()` hardcodes `agentName = "deployment-agent"` at line 61. The `RegistryService` must explicitly set `agentName`, `targetType = "CapabilityRegistration"`, and `targetId` to the registry key. This requires either a new `AuditLoggerService` overload or passing these fields via the context map.
- Audit entries visible in the existing shared Audit Log page.

### Monitoring / Logging

- Standard application logging (SLF4J) for errors and warnings. No custom metrics or dashboards for MVP.

---

## Risks / Tradeoffs

| # | Risk / Tradeoff | Notes |
|---|-----------------|-------|
| 1 | New `/api/platform/` prefix requires Spring Security update | High impact if missed — all platform endpoints return 401/403. Verify early. |
| 2 | `AuditLoggerService.log()` hardcodes `agentName = "deployment-agent"` | Registry events must use `agentName = "platform"`. Requires a new overload or explicit context-map override. Without this, platform events are mislabeled in audit records. |
| 3 | Contribution board aggregate queries may become slow as request/task data grows | Acceptable for MVP (small data set). If needed, add indexed views or caching later. |
| 4 | Owner employee ID is free-text — no validation against Team Book | Acceptable for MVP. Could add optional validation if Team Book API becomes available. |
| 5 | Seeded entries may launch with placeholder owner if team does not confirm real owners | Spec requires owner info on seeded entries. If not confirmed, placeholder surfaces in "Unassigned" section of the contribution board, creating visibility pressure. |

---

## Open Questions

1. Does the existing Vite proxy configuration forward `/api/platform/` to the backend, or is an explicit proxy rule needed?
2. Should the Oracle DDL migration include seed data (INSERT statements), or should initial population rely solely on the `ApplicationRunner`?
3. Should the `AuditLoggerService` fix for `agentName` be a new overload (e.g., `logPlatformEvent(...)`) or a context-map key that overrides the hardcoded default? The overload approach is cleaner but has a larger surface area change.
