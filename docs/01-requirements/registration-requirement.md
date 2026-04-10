# WWA Agent/Tool Registry & Contribution Board Requirement

## 1. Background

As the WWA platform grows beyond the initial workspaces, the team needs a simple way to answer two product questions:

1. Who owns each agent or tool?
2. How can the platform make team contribution more visible?

Today, ownership information is not presented in one clear place, and contribution visibility is weak. This makes it harder for users to find the right owner and harder for the team to recognize who is building and supporting platform capabilities.

The purpose of this requirement is to define:

1. **Agent/Tool Registry** — a lightweight CRUD capability for maintaining a visible directory of platform agents and tools with clear ownership information.
2. **Contribution Board** — a read-only page that groups registered entries by owner and shows simple activity signals to improve visibility and team recognition.

---

## 2. Product Positioning

### 2.1 Registry Positioning

The Agent/Tool Registry is a **platform directory and governance capability**.

It is not a runtime provisioning system. Registering an entry means:

- the capability exists and should be visible in the platform directory
- the owner and support contact are known
- users can discover who is responsible for it

It does **not** mean:

- a new backend controller is created
- a new frontend route is created
- a new workspace becomes usable without implementation work

### 2.2 Contribution Board Positioning

The Contribution Board is a **visibility and recognition page**.

Its purpose is to help the team see:

- who owns which agents/tools
- how many platform capabilities each owner is responsible for
- simple activity volume associated with those capabilities

This is not intended to be a strict performance scoreboard. Metrics are supporting context, not the only measure of contribution.

### 2.3 Relationship to Existing Architecture

The registry manages **metadata and ownership only**.

The following remain unchanged:

- Existing per-agent controllers (`/api/deployment-agent/*`, `/api/testing-agent/*`)
- Existing per-agent frontend views and stores
- Existing route declarations in the frontend router
- Existing request/task data model and data isolation

The registry may optionally store a link to an existing workspace or tool page, but the registry itself does not create runtime functionality.

---

## 3. MVP Objective

### 3.1 Registry MVP

Provide a simple platform page where DEVOPS_ADMIN users can:

- Register a new agent or tool entry
- Assign and maintain a clear owner for each entry
- Optionally record a BAU support contact
- Update metadata later as ownership or descriptions change
- Mark an entry inactive when it is no longer current

Provide a read-only registry view where all authenticated users can:

- See all registered agents/tools
- Quickly identify the owner of each entry
- Open a linked workspace or tool page when a link is available

### 3.2 Contribution Board MVP

Provide a read-only page where all authenticated users can:

- See entries grouped by owner
- See how many agents/tools each owner is responsible for
- See lightweight activity signals for agent entries using existing platform data

---

## 4. MVP Scope

### 4.1 In Scope

#### A. Registry Entry Data Model

One new table `DA_CAPABILITY_REGISTRATION` with the following attributes:

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String UUID | Primary key |
| `registry_key` | String (unique) | Stable identifier, e.g. `deployment-agent` |
| `name` | String | Display name |
| `entry_type` | Enum | `AGENT`, `TOOL` |
| `description` | String | Short summary shown in the registry |
| `owner_employee_id` | String | Required owner employee ID |
| `owner_display_name` | String | Required owner display name |
| `support_contact` | String | Optional BAU support contact |
| `status` | Enum | `ACTIVE`, `INACTIVE` |
| `link` | String | Optional existing workspace route or tool URL |
| `note` | String | Optional admin note |
| `created_by` | String | User who created the entry |
| `created_at` | Timestamp | Creation timestamp |
| `updated_by` | String | User who last updated the entry |
| `updated_at` | Timestamp | Last update timestamp |
| `version` | Long | Optimistic locking version |

#### B. Registry CRUD API

Platform-level REST endpoints at `/api/platform/registry`:

| Method | Path | Role Gate | Description |
|--------|------|-----------|-------------|
| GET | `/api/platform/registry` | All authenticated | List registry entries |
| GET | `/api/platform/registry/{registryKey}` | All authenticated | Get a single entry |
| POST | `/api/platform/registry` | DEVOPS_ADMIN | Create a new entry |
| PATCH | `/api/platform/registry/{registryKey}` | DEVOPS_ADMIN | Update metadata |
| POST | `/api/platform/registry/{registryKey}/deactivate` | DEVOPS_ADMIN | Mark an entry inactive |
| POST | `/api/platform/registry/{registryKey}/activate` | DEVOPS_ADMIN | Reactivate an entry |

#### C. Registry Page

A new platform page at `/wwa/registry`:

- Table showing name, type, owner, support contact, status, optional link, last updated
- "Register Entry" button for DEVOPS_ADMIN only
- Per-row "Edit" action for DEVOPS_ADMIN only
- Per-row "Activate/Deactivate" action for DEVOPS_ADMIN only
- Read-only mode for non-admin users
- Accessible from the home page "Shared Controls" section and sidebar flyout

#### D. Initial Population

The feature launch must include the current known agent workspaces as registry entries:

- `deployment-agent`
- `testing-agent`

The exact implementation mechanism is flexible:

- migration-backed initial data
- idempotent bootstrap on startup

The requirement is only that the initial entries exist when the feature is first used.

#### E. Contribution Board Page

A new platform page at `/wwa/contribution-board`:

- Summary statistics at the top: total registered entries, total active entries, total owners
- Per-owner cards showing:
  - Owner name and employee ID
  - Entries owned
  - Count of owned entries
  - Total request count across owned **agent** entries
  - Total task count across owned **agent** entries
- A separate "Unassigned" section only if legacy/backfilled entries are missing owner data
- Read-only for all authenticated users

#### F. Contribution Board API

Platform-level REST endpoint at `/api/platform/contributions`:

| Method | Path | Role Gate | Description |
|--------|------|-----------|-------------|
| GET | `/api/platform/contributions` | All authenticated | Get contribution board data |

The endpoint aggregates:

- Registry entries grouped by owner
- Request counts per registry key where the entry type is `AGENT`
- Task counts per registry key where the entry type is `AGENT`

Rules:

- `TOOL` entries may show zero activity in MVP
- Release-flow-level ranking is out of scope for MVP

#### G. Audit Logging

Registry mutations (create, update, activate, deactivate) must be recorded in the shared audit log.

### 4.2 Out of Scope

- Dynamic creation of backend controllers, frontend routes, stores, or navigation entries from registry data
- Replacing the existing static workspace routing model
- Automatically making a newly registered agent workspace executable or reachable
- Allowed-stage management or runtime execution configuration
- Real-time contribution updates, time-series analytics, or trend charts
- Gamification features such as badges, scores, streaks, or rewards
- Agent-level access control rules beyond the existing platform role model

---

## 5. Data Model

### 5.1 New Entity

One new entity: `CapabilityRegistration` (table `DA_CAPABILITY_REGISTRATION`).

### 5.2 Relationship to Existing Entities

The registry is a **metadata-only** entity.

It does not require foreign keys to Release Flow, Request, or Task.

Contribution metrics join by key:

- `Request.agent` maps to `CapabilityRegistration.registryKey` when `entry_type = AGENT`
- Task counts are derived from tasks belonging to those requests

### 5.3 No Changes to Existing Runtime Model

The existing runtime entity hierarchy (Release Flow, Request, Task, TaskExecutionHistory) is unchanged.

The registry adds an ownership layer; it does not change workflow execution semantics.

---

## 6. Access Model

### 6.1 Registry Access

- **Read**: All authenticated users
- **Write**: DEVOPS_ADMIN only

### 6.2 Contribution Board Access

- **Read**: All authenticated users
- **Write**: No write operations

---

## 7. MVP Deliverables

1. `DA_CAPABILITY_REGISTRATION` database table with Oracle DDL migration
2. `CapabilityRegistration` JPA entity with repository and service
3. Registry REST API at `/api/platform/registry`
4. Initial population of current known agents
5. Registry page at `/wwa/registry`
6. Contribution Board API at `/api/platform/contributions`
7. Contribution Board page at `/wwa/contribution-board`
8. Shared-navigation entry points to the Registry and Contribution Board pages
9. Audit log entries for registry mutations
10. Backend and controller tests for the new endpoints

---

## 8. MVP Success Criteria

- DEVOPS_ADMIN can create a registry entry with owner information
- DEVOPS_ADMIN can update owner and metadata later
- Users can open the Registry page and immediately see who owns each agent/tool
- Users can open the Contribution Board and see entries grouped by owner
- Contribution Board shows simple request/task activity for agent entries
- Existing Deployment Agent and Testing Agent are present in the registry at launch
- Existing runtime routes and functionality remain unaffected
- All existing tests pass (`mvn test`)
- Frontend builds without errors (`cd frontend && npm run build`)

---

## 9. Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Owner data is incomplete during initial rollout | HIGH | Require owner for new entries and surface legacy gaps in an "Unassigned" section |
| Product drifts into runtime onboarding scope | HIGH | Keep route/store/controller creation explicitly out of scope |
| Contribution board is interpreted as a hard performance ranking | MEDIUM | Frame metrics as visibility signals, not full performance evaluation |
| Tool entries have no measurable runtime activity in MVP | LOW | Show zero/no-data activity and keep ownership visibility as the primary value |

---

## 10. One-Line Summary

**Agent/Tool Registry + Contribution Board = clear ownership visibility plus lightweight team contribution transparency, without turning the registry into a runtime provisioning system.**
