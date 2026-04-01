# System Architecture: Testing Agent

**Date:** 2026-03-31
**Status:** Draft
**Source:** testing-agent-spec.md (primary), architecture.md (baseline)

---

## Platform Context

Testing Agent is the **second workspace** under the **WWA Agent Workspace Hub**. The operating model remains:

```
FinBlock  →  WWA Agent Workspace Hub (`WWA`)  →  Agent Workspaces
                                                   ├── Deployment Agent (first workspace)
                                                   └── Testing Agent (second workspace)
```

- **WWA Agent Workspace Hub** continues to own authentication, top-level navigation, platform access management, and platform-level audit.
- **Testing Agent** reuses the same domain model, services, and shared capabilities as Deployment Agent.
- **Data isolation** is achieved at the controller layer via the existing `Request.agent` column.

---

## Overview

Testing Agent mirrors the Deployment Agent workspace within the WWA Agent Workspace Hub. It provides the same human-in-the-loop controlled execution workflow for testing activities. The architecture adds a **thin controller delegation layer** on the backend and **parameterized frontend components** — no changes to domain services, repositories, entities, or shared capabilities are required.

**Architectural approach:** Agent-parameterized controllers + frontend route reuse. This approach minimizes changes to existing code and keeps Deployment Agent completely untouched.

**Key architectural decision:** Testing Agent does NOT introduce a new service layer, repository layer, or entity layer. The only new backend code is controller classes that delegate to existing services with `agent = "testing-agent"` injected as a parameter.

---

## Technology Stack

No changes. Same stack as Deployment Agent:

| Layer | Technology |
|-------|-----------|
| Frontend | Vue 3 (Composition API) · Vite 5 · Pinia · Vue Router 4 · Axios |
| Backend | Java 21 · Spring Boot 3.2.0 · Spring MVC · Spring Data JPA · Spring Security |
| Database | Oracle (production) · H2 in-memory (tests) |
| Build | Maven 3 (backend) · npm (frontend) |
| Auth | Session-based login via authentication-provider abstraction |

---

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│  Users                                                               │
│  Developer · Tech Lead · DevOps Admin · Audit / Management           │
└──────────────────────┬───────────────────────────────────────────────┘
                       │ HTTPS
                       ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Web App (Vue 3 SPA)                                                 │
│                                                                      │
│  ┌─────────────────────┐    ┌─────────────────────┐                  │
│  │  Deployment Agent   │    │  Testing Agent       │  ← NEW          │
│  │  Views + Store      │    │  Views + Store       │                  │
│  │  /wwa/deployment-   │    │  /wwa/testing-       │                  │
│  │  agent              │    │  agent               │                  │
│  └────────┬────────────┘    └────────┬─────────────┘                  │
│           │                          │                                │
│  ┌────────▼──────────────────────────▼─────────────┐                  │
│  │  Shared Components                               │                  │
│  │  AgentSummaryView · AgentDetailView              │                  │
│  │  UploadDialog · RecordResultDialog · etc.        │                  │
│  └──────────────────────────────────────────────────┘                  │
└──────────────────────┬───────────────────────────────────────────────┘
                       │ REST / JSON + Session Cookie
                       ▼
┌──────────────────────────────────────────────────────────────────────┐
│  API Service (Spring Boot 3)                                         │
│                                                                      │
│  ┌──────────────────────────┐    ┌──────────────────────────┐        │
│  │  /api/deployment-agent/  │    │  /api/testing-agent/      │ ← NEW │
│  │  ReleaseFlowController   │    │  TA_ReleaseFlowController │        │
│  │  UploadController        │    │  TA_UploadController      │        │
│  │  TaskController          │    │  TA_TaskController         │        │
│  │  DecisionController      │    │                            │        │
│  └────────────┬─────────────┘    └────────────┬──────────────┘        │
│               │                                │                      │
│               └────────────┬───────────────────┘                      │
│                            ▼                                          │
│  ┌──────────────────────────────────────────────────────────────┐     │
│  │  Shared Domain Services (UNCHANGED)                          │     │
│  │                                                              │     │
│  │  ReleaseFlowService · TaskService · ImportService            │     │
│  │  DecisionEngine · RecordResultService · AutoExecutionService │     │
│  │  TaskStateMachine · ReleaseFlowAggregation                   │     │
│  │  AuditLoggerService · ConfigurationService · AuthService     │     │
│  └──────────────────────────────────────────────────────────────┘     │
│                            │                                          │
│  ┌──────────────────────────────────────────────────────────────┐     │
│  │  Shared Persistence (UNCHANGED)                              │     │
│  │  Spring Data JPA · All existing repositories                 │     │
│  └──────────────────────────────────────────────────────────────┘     │
└──────────────┬──────────────────────┬────────────────────────────────┘
               │                      │ REST (fire-and-forget)
               ▼                      ▼
┌──────────────────────┐  ┌───────────────────────┐  ┌────────────────┐
│  Oracle DB           │  │  Jenkins              │  │  Auth Provider │
│  (UNCHANGED)         │  │  + Ansible Tower      │  │  (UNCHANGED)   │
│                      │  │  (UNCHANGED)          │  │                │
│  Same 7 entities     │  │                       │  │                │
│  No new tables       │  │                       │  │                │
└──────────────────────┘  └───────────────────────┘  └────────────────┘
```

---

## Architecture Decisions

### AD-1: Thin Controller Delegation (Not Service Duplication)

**Decision:** Testing Agent controllers delegate to existing domain services. No new service classes are created.

**Rationale:**
- Domain logic (import, task state machine, decisions, progression) is agent-agnostic
- The `agent` column is already supported as a filter parameter in `ReleaseFlowService`
- Duplicating services would create maintenance burden and divergence risk
- The controller layer is the natural boundary for agent-specific request mapping

**Consequences:**
- Testing Agent controllers are thin (~30–50 lines each)
- Any domain logic fix applies to both agents automatically
- Agent-specific business rules (if ever needed) would require service-layer changes later

### AD-2: No New Database Schema

**Decision:** No new tables, columns, or migrations are required for Testing Agent.

**Rationale:**
- The `Request.agent` column (VARCHAR 255, added in `V6__add_request_agent_column.sql`) already exists
- All entities, relationships, and indexes are reusable
- Agent-scoped filtering is a query-time concern, not a schema concern

**Consequences:**
- Zero migration risk
- No Oracle DDL review needed
- Cross-agent Release Flows are inherently supported

### AD-3: Separate API Prefix (Not Shared Prefix with Agent Parameter)

**Decision:** Testing Agent uses `/api/testing-agent/` as its API prefix, separate from `/api/deployment-agent/`.

**Alternatives considered:**
- Shared `/api/wwa/` prefix with `agent` query parameter — rejected because it blurs the namespace boundary and makes API documentation harder to navigate
- Shared `/api/agent/{agentId}/` prefix — rejected as over-engineering for two agents

**Rationale:**
- Clear namespace separation aligns with the multi-agent integration standard
- Each agent can evolve its API independently if needed
- API documentation and testing are simpler with separate prefixes
- Security auditing and access logging are clearer per-prefix

### AD-4: Separate Frontend Store Instance

**Decision:** Testing Agent uses a dedicated Pinia store (`useTestingAgentReleaseFlowStore`) separate from the deployment agent store.

**Rationale:**
- Prevents state collision if a user navigates between agents in the same session
- Each store manages its own loading state, pagination, and selected release flow
- Store factory pattern eliminates code duplication while maintaining instance isolation

### AD-5: Shared Access Model (Not Agent-Specific Grants)

**Decision:** Access grants are shared across agents. An active grant allows access to all agent workspaces.

**Rationale:**
- Phase 1 does not require agent-level access control
- Adding an `agent` dimension to `AccessScope` would complicate the grant model prematurely
- The `AccessScope` model uses `(application, snowGroup)` — agent is a runtime filter, not an authorization boundary
- Can be revisited when a genuine need for agent-specific access arises

### AD-6: Agent Identity Constants

**Decision:** Define agent identity as constants rather than string literals scattered across the codebase.

**Backend:**
```java
public final class AgentId {
    public static final String DEPLOYMENT_AGENT = "deployment-agent";
    public static final String TESTING_AGENT = "testing-agent";
    private AgentId() {}
}
```

**Frontend:**
```typescript
export const AGENT_ID = {
  DEPLOYMENT: 'deployment-agent',
  TESTING: 'testing-agent',
} as const
```

**Rationale:**
- Prevents typos and inconsistencies
- Single source of truth for agent identity strings
- Easy to search for all usages

---

## Component Architecture

### Backend — New Components

```
src/main/java/com/wwa/deploymentagent/
├── contracts/
│   └── AgentId.java                          ← NEW (constants)
└── web/controller/
    ├── TestingAgentReleaseFlowController.java ← NEW (thin wrapper)
    ├── TestingAgentUploadController.java      ← NEW (thin wrapper)
    └── TestingAgentTaskController.java        ← NEW (thin wrapper)
```

### Backend — Unchanged Components

All of the following remain untouched:

- **Domain services:** `ReleaseFlowService`, `TaskService`, `ImportService`, `DecisionEngine`, `RecordResultService`, `AutoExecutionService`, `ReleaseFlowProgressionService`, `TaskStateMachine`, `ReleaseFlowAggregation`
- **Repositories:** All Spring Data JPA repositories
- **Entities:** `ReleaseFlow`, `Request`, `Task`, `TaskExecutionHistory`, `AuditLogEntry`, `ConfigurationItem`, `AccessGrant`
- **Security:** `SessionAuthFilter`, `HeaderAuthFilter`, Spring Security configuration
- **Shared controllers:** `AuthController`, `ConfigurationController`, `AuditLogController`, `AccessGrantController`

### Frontend — New Components

```
frontend/src/
├── api/
│   ├── agentApiFactory.ts                    ← NEW (shared factory)
│   └── testingAgentClient.ts                 ← NEW (axios instance)
├── stores/
│   ├── agentReleaseFlowFactory.ts            ← NEW (shared factory)
│   └── testingAgentReleaseFlow.ts            ← NEW (store instance)
├── views/
│   ├── TestingAgentSummaryView.vue           ← NEW (thin wrapper)
│   └── TestingAgentDetailView.vue            ← NEW (thin wrapper)
├── config/
│   └── agentRegistry.ts                      ← MODIFIED (add entry)
└── router/
    └── index.ts                              ← MODIFIED (add routes)
```

### Frontend — Shared Component Extraction (Recommended)

```
frontend/src/components/
├── AgentSummaryView.vue                      ← NEW (extracted shared)
└── AgentDetailView.vue                       ← NEW (extracted shared)
```

After extraction, both `ReleaseFlowSummaryView.vue` and `TestingAgentSummaryView.vue` become thin wrappers (~20 lines) that pass agent-specific props to the shared component.

---

## Data Architecture

### No Changes

Testing Agent uses the same 7 entities with the same schema. No new tables, columns, or migrations.

### Agent Column Usage

The `Request.agent` column (VARCHAR 255) serves as the data isolation mechanism:

| Value | Meaning |
|---|---|
| `"deployment-agent"` | Request created through Deployment Agent |
| `"testing-agent"` | Request created through Testing Agent |
| `null` | Legacy data (pre-agent-column); visible only in Deployment Agent |

### Query Patterns

**Testing Agent list (new):**
```sql
SELECT rf.* FROM da_release_flow rf
WHERE EXISTS (
  SELECT 1 FROM da_request r
  WHERE r.release_flow_id = rf.id
  AND r.agent = 'testing-agent'
)
```

**Deployment Agent list (existing, unchanged):**
```sql
SELECT rf.* FROM da_release_flow rf
-- No agent filter enforced; shows all including legacy null-agent data
```

**Stage summary aggregation (per agent):**
```sql
SELECT ... FROM da_task t
JOIN da_request r ON t.request_id = r.id
WHERE r.release_flow_id = :flowId
AND r.agent = :agentId
```

---

## Integration Architecture

### No Changes

Testing Agent reuses the same integration architecture as Deployment Agent:

- **Jenkins** — same fire-and-forget REST POST pattern
- **Ansible Tower** — same REST POST pattern
- **Authentication Provider** — same session-based login
- **Access Grant Resolution** — same deny-by-default lookup

---

## API Boundaries

### New Endpoints (Testing Agent)

| Method | Endpoint | Purpose | Auth | Delegates To |
|--------|----------|---------|------|-------------|
| GET | /api/testing-agent/release-flows | List flows (agent-scoped) | Any authenticated within scoped visibility | ReleaseFlowService |
| GET | /api/testing-agent/release-flows/{id} | Flow detail with tasks | Any authenticated within scoped visibility | ReleaseFlowService |
| POST | /api/testing-agent/upload | Excel import (agent-tagged) | DEVELOPER, TL, DEVOPS_ADMIN | ImportService |
| GET | /api/testing-agent/upload/template | Download template | Any authenticated | Static resource |
| PUT | /api/testing-agent/tasks/{id}/input | Edit task input | Task owner or DEVOPS_ADMIN | TaskService |
| GET | /api/testing-agent/tasks/{id}/executions | Execution history | Any authenticated | TaskExecutionHistoryService |
| POST | /api/testing-agent/tasks/{id}/start-manual | Start MANUAL task | Task owner or DEVOPS_ADMIN | TaskService |
| POST | /api/testing-agent/tasks/{id}/record-result | Record MANUAL result | Task owner or DEVOPS_ADMIN | RecordResultService |
| POST | /api/testing-agent/tasks/{id}/submit-auto | Submit AUTO task | Task owner or DEVOPS_ADMIN | AutoExecutionService |
| POST | /api/testing-agent/tasks/{id}/decision | Apply decision | Task owner or DEVOPS_ADMIN | DecisionEngine |

### Existing Endpoints (Unchanged)

All `/api/deployment-agent/` endpoints remain unchanged.

All shared endpoints (`/auth/*`, `/access-grants/*`, `/config/*`, `/audit-logs/*`) remain unchanged.

---

## Security Architecture

### No Changes

Testing Agent uses the same security architecture:

- **Session management:** Same `IF_REQUIRED` session policy
- **Filter chain:** Same `SessionAuthFilter → HeaderAuthFilter → Spring Security`
- **Access control:** Same deny-by-default Access Grants
- **Scope grants:** Same `Application + SNOW Group` visibility model
- **RBAC / permissions:** Same effective permission enforcement
- **Optimistic locking:** Same `@Version` on entities
- **Audit isolation:** Same `REQUIRES_NEW` propagation for audit writes

Testing Agent controllers apply the same `@PreAuthorize` annotations and permission checks as Deployment Agent controllers.

---

## Routing Architecture

### Frontend Routes

| Route | Component | Meta |
|---|---|---|
| `/wwa/testing-agent` | `TestingAgentSummaryView` | `{ section: 'testing-agent', sectionTitle: 'Testing Agent' }` |
| `/wwa/testing-agent/release-flows/:id` | `TestingAgentDetailView` | `{ section: 'testing-agent', sectionTitle: 'Testing Agent' }` |

### Agent Registry

Testing Agent is added to `agentRegistry.ts`:

```typescript
{
  key: 'testing-agent',
  name: 'Testing Agent',
  description: 'Controlled, human-in-the-loop testing workflow across SIT, UAT, and PROD stages.',
  route: '/wwa/testing-agent',
  icon: '🧪',
  enabled: true,
  category: 'testing',
}
```

The agent registry drives:
- WWA Home page agent cards
- Sidebar flyout navigation entries
- No shell code changes needed

---

## Constraints and Assumptions

| # | Constraint | Source |
|---|-----------|--------|
| C1 | Testing Agent reuses the same data model — no new entities or tables | AD-2 |
| C2 | Testing Agent controllers are thin wrappers — no domain logic duplication | AD-1 |
| C3 | Agent identity is stored as `Request.agent = "testing-agent"` | Spec TFR-09 |
| C4 | Legacy data (null agent) is NOT visible in Testing Agent | Spec TFR-16 |
| C5 | Access grants are shared across agents | AD-5 |
| C6 | All existing Deployment Agent behavior must remain unchanged | Spec §14.2 |
| C7 | Agent identity strings are defined as constants | AD-6 |

---

## Impact Analysis

### Files to Create

| File | Size (est.) | Purpose |
|---|---|---|
| `AgentId.java` | ~10 lines | Agent identity constants |
| `TestingAgentReleaseFlowController.java` | ~50 lines | Thin controller wrapper |
| `TestingAgentUploadController.java` | ~40 lines | Thin controller wrapper |
| `TestingAgentTaskController.java` | ~80 lines | Thin controller wrapper |
| `testingAgentClient.ts` | ~15 lines | Axios instance |
| `agentApiFactory.ts` | ~60 lines | Shared API factory |
| `agentReleaseFlowFactory.ts` | ~40 lines | Shared store factory |
| `testingAgentReleaseFlow.ts` | ~10 lines | Store instance |
| `TestingAgentSummaryView.vue` | ~20 lines | Thin view wrapper |
| `TestingAgentDetailView.vue` | ~20 lines | Thin view wrapper |
| `AgentSummaryView.vue` | ~400 lines | Extracted shared component |
| `AgentDetailView.vue` | ~400 lines | Extracted shared component |

### Files to Modify

| File | Change |
|---|---|
| `agentRegistry.ts` | Add testing-agent entry (~8 lines) |
| `router/index.ts` | Add 2 routes (~10 lines) |
| `ReleaseFlowSummaryView.vue` | Refactor to thin wrapper around `AgentSummaryView` |
| `ReleaseFlowDetailView.vue` | Refactor to thin wrapper around `AgentDetailView` |

### Files Unchanged

All domain services, repositories, entities, security filters, shared controllers, database schema, and configuration.

---

## Pending External Dependencies

No new external dependencies. All existing dependencies from Deployment Agent apply unchanged:

1. **Team Book adapter contract** — still pending (not Testing Agent specific)
2. **Jenkins/Ansible credentials** — same runtime config
3. **Enterprise directory enrichment** — same pending consideration
