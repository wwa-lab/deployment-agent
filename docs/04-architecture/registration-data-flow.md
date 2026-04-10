# Data Flow: Agent/Tool Registry & Contribution Board

**Date:** 2026-04-10
**Status:** Draft
**Source:** registration-spec.md, registration-architecture.md

---

## Data Objects

| Object | Lifecycle | Owner |
|--------|-----------|-------|
| `CapabilityRegistration` | Created by DEVOPS_ADMIN → Active → optionally Deactivated → optionally Reactivated | `RegistryService` |
| `AuditLogEntry` (for registry) | Created on each successful registry mutation, immutable thereafter | `AuditLoggerService` |
| Contribution board response | Computed on each request, not persisted | `ContributionBoardService` |

---

## Flow 1: Registry Entry Creation

```
┌──────────┐     ┌──────────────┐     ┌─────────────────┐     ┌──────────────┐
│  Browser  │────►│  Registry    │────►│  RegistryService│────►│  Capability  │
│  (Admin)  │     │  Controller  │     │                 │     │  Registration│
│           │     │  POST /api/  │     │  validate()     │     │  Repository  │
│           │     │  platform/   │     │  save()         │     │  .save()     │
│           │     │  registry    │     │                 │     │              │
└──────────┘     └──────────────┘     └────────┬────────┘     └──────────────┘
                                               │
                                               │ on success
                                               ▼
                                      ┌─────────────────┐     ┌──────────────┐
                                      │  AuditLogger    │────►│  DA_AUDIT_LOG│
                                      │  Service        │     │  (table)     │
                                      │  REQUIRES_NEW   │     │              │
                                      └─────────────────┘     └──────────────┘
```

**Field mapping: Create request → Entity**

| Request Field | Entity Field | Rule |
|---------------|-------------|------|
| registryKey | registry_key | Required. Validated: lowercase + hyphens, 3-100 chars, unique |
| name | name | Required. Max 255 |
| entryType | entry_type | Required. Must be `AGENT` or `TOOL` |
| description | description | Optional. Max 1000 |
| ownerEmployeeId | owner_employee_id | Required |
| ownerDisplayName | owner_display_name | Required |
| supportContact | support_contact | Optional. Max 500 |
| link | link | Optional. Max 500. Both internal routes and external URLs accepted |
| note | note | Optional. Max 1000 |
| (system) | status | Set to `ACTIVE` |
| (system) | created_by | Current user ID from `UserContext` |
| (system) | created_at | Current timestamp |
| (system) | updated_by | Current user ID from `UserContext` |
| (system) | updated_at | Current timestamp |
| (system) | version | Initialized to 0 by JPA |

---

## Flow 2: Registry Entry Update

```
┌──────────┐     ┌──────────────┐     ┌─────────────────┐     ┌──────────────┐
│  Browser  │────►│  Registry    │────►│  RegistryService│────►│  Capability  │
│  (Admin)  │     │  Controller  │     │                 │     │  Registration│
│           │     │  PATCH /api/ │     │  findByKey()    │     │  Repository  │
│           │     │  platform/   │     │  applyUpdates() │     │  .save()     │
│           │     │  registry/   │     │  save()         │     │              │
│           │     │  {key}       │     │                 │     │              │
└──────────┘     └──────────────┘     └────────┬────────┘     └──────────────┘
                                               │
                                   ┌───────────┴───────────┐
                                   │ Optimistic lock check  │
                                   │ @Version comparison    │
                                   └───────────┬───────────┘
                                               │
                              ┌────────────────┼────────────────┐
                              │ version match  │                │ version mismatch
                              ▼                │                ▼
                     ┌────────────────┐        │       ┌────────────────┐
                     │  Save + Audit  │        │       │  409 Conflict  │
                     └────────────────┘        │       └────────────────┘
                                               │
                                               ▼
                                      ┌─────────────────┐
                                      │  AuditLogger    │
                                      │  registry_update│
                                      └─────────────────┘
```

**Editable fields on update:**

| Field | Editable | Note |
|-------|----------|------|
| registry_key | No | Immutable after creation |
| name | Yes | |
| entry_type | No | Immutable after creation — determines contribution metric semantics |
| description | Yes | |
| owner_employee_id | Yes | |
| owner_display_name | Yes | |
| support_contact | Yes | |
| link | Yes | |
| note | Yes | |
| status | No | Changed via activate/deactivate endpoints only |

---

## Flow 3: Activate / Deactivate

```
┌──────────┐     ┌──────────────┐     ┌─────────────────┐
│  Browser  │────►│  Registry    │────►│  RegistryService│
│  (Admin)  │     │  Controller  │     │                 │
│           │     │  POST /api/  │     │  findByKey()    │
│           │     │  platform/   │     │  checkState()   │──── Same state? → 409
│           │     │  registry/   │     │  toggleStatus() │
│           │     │  {key}/      │     │  save()         │
│           │     │  activate    │     │  audit()        │
│           │     │  or          │     │                 │
│           │     │  deactivate  │     │                 │
└──────────┘     └──────────────┘     └─────────────────┘
```

**State transitions:**

| Current Status | Action | New Status | Result |
|----------------|--------|-----------|--------|
| ACTIVE | deactivate | INACTIVE | Success + audit |
| INACTIVE | activate | ACTIVE | Success + audit |
| ACTIVE | activate | - | 409 Conflict |
| INACTIVE | deactivate | - | 409 Conflict |

---

## Flow 4: Registry Browsing (Read)

```
┌──────────┐     ┌──────────────┐     ┌─────────────────┐     ┌──────────────┐
│  Browser  │────►│  Registry    │────►│  RegistryService│────►│  Capability  │
│  (Any     │     │  Controller  │     │                 │     │  Registration│
│   user)   │     │  GET /api/   │     │  listAll()      │     │  Repository  │
│           │     │  platform/   │     │                 │     │  .findAll    │
│           │     │  registry    │     │                 │     │  OrderByName │
└──────────┘     └──────────────┘     └─────────────────┘     └──────────────┘
                        │
                        │ Returns List<CapabilityRegistrationDto>
                        ▼
                 ┌──────────────┐
                 │  RegistryView│
                 │  renders     │
                 │  table       │
                 └──────────────┘
```

**Response mapping: Entity → DTO**

| Entity Field | DTO Field | Note |
|-------------|-----------|------|
| id | id | |
| registry_key | registryKey | |
| name | name | |
| entry_type | entryType | "AGENT" or "TOOL" |
| description | description | |
| owner_employee_id | ownerEmployeeId | |
| owner_display_name | ownerDisplayName | |
| support_contact | supportContact | |
| status | status | "ACTIVE" or "INACTIVE" |
| link | link | null if not set |
| note | note | null if not set |
| created_by | createdBy | |
| created_at | createdAt | ISO-8601 timestamp |
| updated_by | updatedBy | |
| updated_at | updatedAt | ISO-8601 timestamp |
| version | version | Used by frontend for optimistic lock on update |

---

## Flow 5: Contribution Board

```
┌──────────┐     ┌──────────────┐     ┌─────────────────────────────────────┐
│  Browser  │────►│  Contribution│────►│  ContributionBoardService           │
│  (Any     │     │  Controller  │     │                                     │
│   user)   │     │  GET /api/   │     │  1. Load all registry entries       │
│           │     │  platform/   │     │     CapabilityRegistrationRepository│
│           │     │  contributions     │                                     │
│           │     │              │     │  2. Group by owner_employee_id      │
│           │     │              │     │                                     │
│           │     │              │     │  3. For AGENT entries:              │
│           │     │              │     │     Count requests per agent key    │
│           │     │              │     │     RequestRepository               │
│           │     │              │     │                                     │
│           │     │              │     │  4. For AGENT entries:              │
│           │     │              │     │     Count tasks per agent key       │
│           │     │              │     │     TaskRepository (via request)    │
│           │     │              │     │                                     │
│           │     │              │     │  5. Assemble response               │
└──────────┘     └──────────────┘     └─────────────────────────────────────┘
```

**Aggregation data path:**

```
DA_CAPABILITY_REGISTRATION
    │
    │  SELECT registry_key, owner_employee_id, owner_display_name, ...
    │  GROUP BY owner_employee_id
    │
    ▼
Per-owner group
    │
    │  For each AGENT entry in the group:
    │
    │  SELECT COUNT(*) FROM DA_REQUEST
    │  WHERE agent = :registryKey
    │  ──────────────────────────────► request_count
    │
    │  SELECT COUNT(*) FROM DA_TASK t
    │  JOIN DA_REQUEST r ON t.request_id = r.id
    │  WHERE r.agent = :registryKey
    │  ──────────────────────────────► task_count
    │
    ▼
ContributionBoardEntryDto
    │
    │  Assembled per-owner with:
    │    - ownerDisplayName, ownerEmployeeId
    │    - entries[] (name, type, status per entry)
    │    - entryCount
    │    - totalRequests (sum across AGENT entries)
    │    - totalTasks (sum across AGENT entries)
    │
    ▼
Response: List<ContributionBoardEntryDto>
    + summary (totalEntries, totalActive, totalOwners)
```

**Response structure:**

| Field | Source | Note |
|-------|--------|------|
| summary.totalEntries | COUNT(*) from registry | All entries regardless of status |
| summary.totalActive | COUNT(*) WHERE status = ACTIVE | |
| summary.totalOwners | COUNT(DISTINCT owner_employee_id) WHERE owner_employee_id IS NOT NULL | |
| entries[].ownerDisplayName | registry.owner_display_name | Grouped |
| entries[].ownerEmployeeId | registry.owner_employee_id | Grouped |
| entries[].ownedEntries[] | registry entries for this owner | Each with name, type, status |
| entries[].entryCount | COUNT of entries for this owner | |
| entries[].totalRequests | SUM of request counts for AGENT entries | 0 for owners with only TOOL entries |
| entries[].totalTasks | SUM of task counts for AGENT entries | 0 for owners with only TOOL entries |

Entries with null/blank `owner_employee_id` are grouped under a separate "Unassigned" entry with `ownerDisplayName = "Unassigned"`.

---

## Flow 6: Initial Population (Bootstrap)

```
┌──────────────────┐     ┌─────────────────┐     ┌──────────────┐
│  Application     │────►│  RegistryBootstrap    │────►│  Capability  │
│  Startup         │     │  Runner          │     │  Registration│
│  (Spring Boot)   │     │                  │     │  Repository  │
│                  │     │  @Profile(       │     │              │
│                  │     │  "default","local│     │  existsByKey?│
│                  │     │  ")              │     │  → skip      │
│                  │     │                  │     │  → save      │
└──────────────────┘     └─────────────────┘     └──────────────┘
```

**Seed data:**

| registry_key | name | entry_type | status | owner |
|-------------|------|-----------|--------|-------|
| deployment-agent | Deployment Agent | AGENT | ACTIVE | TBD (placeholder) |
| testing-agent | Testing Agent | AGENT | ACTIVE | TBD (placeholder) |

- Runner checks `existsByRegistryKey()` before each insert
- Idempotent: skips if entry already exists
- Does NOT run under `test` profile

---

## End-to-End Data Path Summary

| User Action | Frontend | API | Service | Repository | Table |
|-------------|----------|-----|---------|------------|-------|
| Register entry | RegistryView → platformClient POST | RegistryController | RegistryService.register() | CapabilityRegistrationRepo.save() | DA_CAPABILITY_REGISTRATION |
| View registry | RegistryView → platformClient GET | RegistryController | RegistryService.listAll() | CapabilityRegistrationRepo.findAllByOrderByNameAsc() | DA_CAPABILITY_REGISTRATION |
| Update entry | RegistryView → platformClient PATCH | RegistryController | RegistryService.update() | CapabilityRegistrationRepo.save() | DA_CAPABILITY_REGISTRATION |
| Activate/Deactivate | RegistryView → platformClient POST | RegistryController | RegistryService.activate/deactivate() | CapabilityRegistrationRepo.save() | DA_CAPABILITY_REGISTRATION |
| View contributions | ContributionBoardView → platformClient GET | ContributionController | ContributionBoardService.getContributions() | CapabilityRegistrationRepo + RequestRepo + TaskRepo | DA_CAPABILITY_REGISTRATION + DA_REQUEST + DA_TASK |
| Audit (automatic) | — | — | AuditLoggerService.log() | AuditLogRepository.save() | DA_AUDIT_LOG |
