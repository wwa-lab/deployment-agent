# Data Model: Service Directory

> **Slice:** `service-directory`
> **Status:** Regenerated via `architecture-to-design` — awaiting user acceptance
> **Last updated:** 2026-07-25
> **Source spec:** `docs/03-spec/service-directory-spec.md`
> **Companions:** `service-directory-architecture.md`, `service-directory-data-flow.md`
> **Decision record:** `docs/00-context/decisions/ADR-0010-service-directory-owns-its-catalog-store.md` (Proposed)

---

## 1. Overview

Service Directory persists exactly one thing: the directory catalog — the administrator-maintained tree
of directory scopes, groups, and links that the Hub renders as a destination catalog. It is stored as a
**single versioned JSON document in a single row** of a new dedicated table, because the catalog is
always read whole, rendered whole, and versioned whole.

Nothing else in this slice is persisted server-side. In particular, the personal Recently used list is
browser-only by design (SD-FR-31, SD-NFR-09).

---

## 2. Persistence Decision (closes SD-T00)

**Decision: Option A — single JSON document row.** `[DEFAULT — revisit if the concurrency or query
assumptions below stop holding]`

| Criterion | Option A — one JSON document row | Option B — normalised `SD_SCOPE` / `SD_GROUP` / `SD_LINK` |
|---|---|---|
| Fits the access pattern | Yes. Every read is "give me the whole catalog"; there is no partial-read use case in the spec (SD-FR-06) | Over-serves it: three joins to rebuild a tree the client always wants whole |
| Hierarchical delete | Trivially atomic — removing a subtree is a document edit (SD-FR-41, SD-FR-42) | Needs cascade constraints or explicit multi-table deletes |
| Ordering | Array position plus `sortOrder`, both inside one document | Per-table `sortOrder` columns, three places to keep consistent |
| Concurrency | Document-level optimistic version: an explicit 409 for the loser (SD-FR-44) | Row-level, so unrelated edits never collide |
| Migration surface | One table, one forward migration | Three tables, two foreign keys, indexes, and a larger greenfield-schema delta |
| Oracle / H2 parity | Uses the repository's existing character-large-object JSON conversion approach, already proven on both engines (verified in `util/JsonAttributeConverter.java:18-26`, applied for example at `domain/task/Task.java:132-134` and `domain/audit/AuditLogEntry.java:144-146`) | Also portable, but three times the DDL to keep in sync with `docs/sql/ORACLE_CURRENT_SCHEMA.sql` |
| Per-entity SQL querying / reporting | Not available — no requirement asks for it | Available |
| Audit granularity | Unaffected: mutations are already per-entity at the API layer, so audit detail comes from the request, not from the storage shape | Unaffected |

**Why Option A wins here:** the only advantage Option B offers that this slice actually needs is
row-level concurrency, and the spec's write profile (a small administrator group, edits measured in
"per week") makes an explicit reload-and-retry acceptable (SD-R-03). Everything else Option B buys —
partial reads, per-entity SQL, independent ordering — is unused. Option A also keeps the boundary in
`ADR-0010` easy to audit: one table, owned by one module.

**What would reverse this decision:** administrator write concurrency becoming routine, catalog size
exceeding the MVP ceiling in SD-NFR-07, or a new requirement for per-link SQL reporting. Because the
domain module is the only owner of catalog persistence, that migration stays contained.

---

## 3. Entity Relationship Diagram

```
┌────────────────────────────────────────────────────────────────┐
│  DA_SERVICE_DIRECTORY_CATALOG            (new table, 1 row)    │
│  ────────────────────────────────────────────────────────────  │
│  id            PK                                              │
│  payload       CLOB  ← the whole catalog document (JSON)        │
│  version       optimistic lock                                 │
│  updated_by / updated_at                                       │
└──────────────────────────┬─────────────────────────────────────┘
                           │ payload contains (1 : N, ordered)
                           ▼
              ┌───────────────────────────┐
              │  DirectoryScope           │   embedded, not a table
              │  key · title · layout ·   │
              │  system · enabled · order  │
              └────────────┬──────────────┘
                           │ 1 : N (ordered)
                           ▼
              ┌───────────────────────────┐
              │  DirectoryGroup           │   embedded, not a table
              │  key · title · type ·     │
              │  stageKey · stageOrder ·  │
              │  agentName · enabled ·    │
              │  order                    │
              └────────────┬──────────────┘
                           │ 1 : N (ordered)
                           ▼
              ┌───────────────────────────┐
              │  DirectoryLink            │   embedded, not a table
              │  id · title · description │
              │  url · kind · kindLabel · │
              │  enabled · order          │
              └───────────────────────────┘

Relationships to existing tables:

  DA_SERVICE_DIRECTORY_CATALOG ──(no FK, no row)──► DA_CONFIGURATION_COMPONENT   ✗ never
  DA_SERVICE_DIRECTORY_CATALOG ──(no FK, no row)──► DA_CONFIGURATION_ITEM        ✗ never
  DA_SERVICE_DIRECTORY_CATALOG ──(no FK, no row)──► DA_SCOPE_DIRECTORY           ✗ never
  Catalog mutation ──────────────(emits)─────────►  DA_AUDIT_LOG_ENTRY           ✓ one per mutation
```

The three "never" edges are the boundary this slice exists to protect (SD-FR-63, `ADR-0010`). Note the
name collision hazard: the existing `DA_SCOPE_DIRECTORY` table stores **access** scoping (application /
SNOW group / agent) for uploads — it is unrelated to a directory *scope* here, and must not be reused.

---

## 4. Entity Definitions

### 4.1 `DA_SERVICE_DIRECTORY_CATALOG` (the only new table)

Table naming follows the repository's existing `DA_`-prefixed convention (for example
`DA_CONFIGURATION_COMPONENT`, `DA_SCOPE_DIRECTORY`, `DA_AUDIT_LOG_ENTRY`).

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | String(36) | No | Primary key. String UUID assigned before insert, matching the repository's entity convention (see `domain/releaseflow/ReleaseFlow.java:40-42, 98-103`) |
| `payload` | CLOB | No | The catalog document: an ordered list of directory scopes with nested groups and links. Stored through the existing JSON attribute-conversion approach so Oracle and H2 behave identically |
| `version` | Long | No | Optimistic lock, default 0. The mechanism behind SD-FR-44's conflict response; the platform's existing exception handling already maps an optimistic-lock failure to HTTP 409 |
| `updated_by` | String(64) | Yes | Identifier of the last operator. Null only for the seeded row, which has no human author |
| `updated_at` | Timestamp | No | Last modification instant, maintained by the persistence layer's update-timestamp mechanism |
| `created_at` | Timestamp | No | Row creation instant (seed installation), maintained by the creation-timestamp mechanism |

**Keys and constraints**

- Primary key: `id`.
- **Singleton invariant:** the table holds at most one row. Enforced in the domain module (read-or-seed, never insert twice) rather than by a database check, because a portable single-row constraint across Oracle and H2 costs more than it protects. The module is the only writer, which is what makes the invariant defensible.
- Foreign keys: none. This table intentionally references nothing and is referenced by nothing.

**Indexes**

- None beyond the primary key. A single-row table has no query pattern worth indexing.

### 4.2 `DirectoryScope` (embedded in `payload`)

| Field | Type | Required | Description |
|---|---|---|---|
| `key` | String | Yes | Stable identifier and filter chip value. Unique across the catalog. Pattern `^[a-z0-9][a-z0-9_-]{1,31}$` after trim and lower-case (SD-FR-49). **Immutable after create** for every scope, system or not (SD-FR-43) — behavior and audit history reference it |
| `title` | String(120) | Yes | Display label — for example "SDLC", "Common", "External" |
| `description` | String(240) | No | Short explanatory text shown under the section heading |
| `layout` | Enum | Yes | `stage-strip` (renders an ordered stage rail; used by `sdlc`) or `buckets` (plain grouped sections) — SD-FR-11. At most one scope in the catalog may be `stage-strip` (SD-FR-70), because stage focus resolves to "the stage-strip scope" |
| `system` | Boolean | Yes | True for the three seeded scopes. A `system` scope cannot be deleted; its `title`, `description`, `sortOrder`, and `enabled` flag remain editable, but its `key` is immutable (SD-FR-43) |
| `enabled` | Boolean | Yes | Readers receive enabled scopes only (SD-FR-08) |
| `sortOrder` | Integer 0–9999 | Yes | Ascending; ties break by `key` ascending (SD-FR-10) |
| `groups` | Ordered list | Yes | Zero or more groups. An empty scope is legal and shows an add affordance in manage mode |

### 4.3 `DirectoryGroup` (embedded in a scope)

| Field | Type | Required | Description |
|---|---|---|---|
| `key` | String | Yes | Unique **within its scope**. Same pattern as a scope key (SD-FR-40, SD-FR-49). Immutable after create. When `type = stage`, must equal `stageKey` (SD-FR-51) |
| `title` | String(120) | Yes | Section heading — for example "Deployment", "Common · Engineering tools" |
| `description` | String(240) | No | Short explanatory text |
| `type` | Enum | Yes | `stage` (an SDLC lifecycle stage) or `bucket` (a plain grouping) — SD-FR-12 |
| `stageKey` | Enum | Only when `type = stage` | One of `planning`, `estimation`, `discovery`, `build`, `testing`, `deployment`, `maintenance` (SD-FR-51). Must equal the group's `key`, so a stage has one identity; the client's stage filter compares against `key` and would otherwise have two candidate fields |
| `stageOrder` | Integer 1–99 | Only when `type = stage` | The number shown in the stage rail. Kept separate from `sortOrder` because the prototype conflated the two |
| `agentName` | String(120) | No | Owning agent display text shown in the stage rail — for example "Deployment Agent". Display text only; **not** a reference to the backend `AgentId` contract, and not used for any agent isolation decision |
| `enabled` | Boolean | Yes | Readers receive enabled groups only |
| `sortOrder` | Integer 0–9999 | Yes | Ascending within the scope; ties break by `key` |
| `links` | Ordered list | Yes | Zero or more links. An empty group is legal and shows per-kind add affordances in manage mode (SD-FR-39) |

### 4.4 `DirectoryLink` (embedded in a group)

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | String(36) | Yes | Stable String UUID assigned by the server on create; immutable afterwards. This is the value the browser stores in Recently used, which is why it must never be reassigned |
| `title` | String(120) | Yes | Card title |
| `description` | String(240) | No | Card subtitle |
| `url` | String(1024) | Yes | Destination. Validated per kind: for `workspace`, an in-Hub path matching `^/wwa/[A-Za-z0-9._~\-/]*$` — no query string, fragment, or percent-encoding; for `docs`, `tool`, `repo`, an `http`/`https` scheme. Script-like schemes, protocol-relative URLs, and blanks are rejected (SD-FR-47, SD-FR-48) |
| `kind` | Enum | Yes | `docs`, `tool`, `workspace`, `repo` (SD-FR-46) |
| `kindLabel` | String(24) | No | Short pill caption — for example "Guideline", "CI", "GitHub". Defaults to the kind's display name when omitted |
| `enabled` | Boolean | Yes | Readers receive enabled links only |
| `sortOrder` | Integer 0–9999 | Yes | Ascending within the group; ties break by `title`, then by `id` (SD-FR-10). Links have no `key`, so the scope/group tie-break rule does not apply here; `id` is the final discriminator that makes the order stable across reloads when two links share a title |

**Fields deliberately not stored** (each would create a way for stored data to contradict behavior):

| Not stored | Reason |
|---|---|
| `openInNewTab` | Derived from `kind` (SD-FR-26). Storing it allows a `workspace` link flagged for a new tab — a state with no correct behavior |
| `tone`, `mark` (icon colour and two-letter badge) | Pure presentation, derived deterministically in the frontend from kind and title, exactly as the prototype derives them on create |
| `tags`, `audience`, `environments`, `source` | Speculative in the earlier draft; no requirement consumes them, and unused fields still need validation, UI, and migration |
| `agentOwner` (team name) | Team ownership belongs to the Agent Contribute Dashboard, not the link catalog |
| `updatedAt` / `updatedBy` per link | Per-entity attribution is already captured in the audit trail (SD-FR-55); duplicating it in the document creates a second, un-reviewed history that can drift |

---

## 5. State Models

The catalog is reference data, not a workflow entity. There is exactly one state dimension —
availability — plus structural existence.

### 5.1 Availability (applies to scope, group, and link alike)

```
              administrator update (enabled = false)
        ┌──────────────────────────────────────────────┐
        │                                              ▼
   ┌─────────┐                                   ┌──────────┐
   │ ENABLED │                                   │ DISABLED │
   │ visible │                                   │ hidden   │
   │ to all  │                                   │ from     │
   │ readers │◄──────────────────────────────────┤ readers  │
   └────┬────┘   administrator update             └────┬─────┘
        │        (enabled = true)                      │
        │                                              │
        │  administrator delete                        │  administrator delete
        └──────────────────┬───────────────────────────┘
                           ▼
                     ┌──────────┐
                     │ REMOVED  │  hard delete; for a scope or group this
                     │ (absent) │  applies atomically to all descendants
                     └──────────┘
```

| Transition | Trigger | Notes |
|---|---|---|
| `absent → ENABLED` | Administrator create, or one-time seed provisioning | Seed installs only into an empty store (SD-FR-61) |
| `ENABLED → DISABLED` | Administrator update | Disabling a system scope is allowed but the manage UI warns (SD-R-08) |
| `DISABLED → ENABLED` | Administrator update | — |
| `ENABLED/DISABLED → absent` | Administrator delete | Cascades to descendants (SD-FR-41, SD-FR-42). Blocked for `system` scopes (SD-FR-43) |

There is no soft-delete, no draft, no approval, and no archived state. Readers only ever observe
`ENABLED`.

### 5.2 Catalog version (concurrency, not lifecycle)

```
version n ──[successful mutation]──► version n+1
    ▲                                    │
    └──── a mutation computed against ────┘
          version n is rejected with 409
          once the stored version is > n
```

The version is not a business state and is never shown as one; it exists solely to reject stale writes.

---

## 6. Configuration Entities

This slice adds **no** configuration keys, components, or scope-directory entries. That is a
requirement, not an omission (SD-FR-63).

| Existing store | Owner | Why Service Directory must not use it |
|---|---|---|
| `DA_CONFIGURATION_ITEM` (keyed by the `ConfigKey` enum) | Configuration Management | Holds runtime key-value settings. Adding a catalog key would place navigation content behind the config admin editors and mix two change-control audiences. Note the Agent Contribute Dashboard *does* store its stage-status overrides here — that precedent must not be copied for the catalog |
| `DA_CONFIGURATION_COMPONENT` | Configuration Management | Holds integration endpoints and credentials for Jenkins / Ansible / callback components. The catalog has no credential concept and must not sit next to secrets |
| `DA_SCOPE_DIRECTORY` | Configuration Management (upload scoping) | Holds curated application / SNOW group / agent choices for access scoping. Its "scope" is an access concept; a directory scope is a catalog category. Same word, different model |

The only packaged configuration this slice introduces is the **seed catalog**, which lives in the
application package rather than the database, so it can never be mistaken for Configuration Management
data.

---

## 7. Audit Entities

This slice writes to the existing audit store; it defines no new audit table.

### 7.1 What is written

| Audit field | Value for this slice |
|---|---|
| Action type | A dedicated Service Directory action: an update action for create and update, a delete action for deletion (SD-FR-54) |
| Actor kind | `HUMAN` — the audit write path already sets this for every entry (verified at `domain/audit/AuditLoggerService.java:125`) |
| Operator identity and role | Taken from the server-side user context, never from the request body |
| Context payload | The identifying detail: entity type, entity id or key, entity title, operation, and — for cascade deletes — counts of removed groups and links (SD-FR-55, SD-FR-56) |
| Release flow / request / task identifiers | Not applicable; this capability has no release-flow context |

### 7.2 Why the identifying detail goes in the context payload

`AuditLogEntry` has generic `target_type` / `target_id` columns (verified at
`domain/audit/AuditLogEntry.java:120-127`), which look like the natural home for this. They are not
used, for two verified reasons: no code currently writes them, and the audit read model
(`AuditLogEntryDto`) does not expose them, so an auditor could never see what was written. The context
payload **is** exposed and is already used this way by the existing scope-directory flow, which marks
its entries with a context key rather than a dedicated action. Populating the target columns properly —
including exposing them through the audit API and UI — is a worthwhile audit-capability improvement and
is explicitly out of this slice's scope.

### 7.3 Action vocabulary change

`AuditActionType` is a closed enum persisted as a string (verified at
`contracts/enums/AuditActionType.java:4-24`), so adding constants requires **no database migration**.

Reusing `config_update` / `config_delete` was rejected: those values already mean Configuration
Management changes, and overloading them would make the two capabilities indistinguishable in the Audit
Log — directly contradicting SD-US-07's acceptance criterion 2.

Cross-cutting check performed: `DEVELOPMENT_STANDARDS.md` requires a matching TypeScript union update
whenever a backend enum in `contracts/enums/` changes. Verified that the frontend audit model types
this field as a plain `string` (`frontend/src/types/index.ts:250`), so there is **no** union to update
and no silent drift risk for this particular enum.

### 7.4 Immutability

Audit entries are append-only in the existing capability; this slice neither updates nor deletes them.

---

## 8. Seed Data Inventory

Installed once into an empty store (SD-FR-60), validated by the same rules as administrator input.

| Scope | `layout` | `system` | Groups |
|---|---|---|---|
| `sdlc` | `stage-strip` | Yes | Seven stage groups: Planning, Estimation, Discovery, Build, Testing, Deployment, Maintenance — each with `stageKey`, `stageOrder` 1–7, and an owning agent name |
| `common` | `buckets` | Yes | **Platform** (in-Hub `workspace` links) and **Engineering tools** (ARCAD, GitHub Enterprise) |
| `external` | `buckets` | Yes | **Enterprise** (corporate systems opened in a new tab) |

Seed content rules:

1. **No example custom scope.** The prototype's `security` scope demonstrates extensibility and must not ship as data (requirement §8).
2. **Workspace links point only at routes that exist**, so the seed cannot produce a dead in-app navigation.
3. **Unknown URLs use the reserved `.invalid` suffix** (RFC 2606), which renders as "URL pending" and is not activatable (SD-FR-27, SD-FR-62). This applies to ARCAD and GitHub Enterprise until SD-T02 supplies real URLs, and to stage guideline / feedback links whose Confluence targets are not yet confirmed.
4. **No fabricated Recently used entries.** The prototype seeds three; production starts empty (SD-FR-32).
5. **No real internal hostnames are guessed.** A placeholder is preferable to a wrong or sensitive host.

---

## 9. Client-Side Storage (not persisted server-side)

| Key | Shape | Cap | Notes |
|---|---|---|---|
| `wwa.serviceDirectory.recent.v1` | Ordered list of `{ linkId, openedAt }`, most recent first | 8 entries | Resolved against the loaded catalog on every page load; unresolvable ids are dropped (SD-FR-34). Bump the `v` suffix on an incompatible shape change |

Two grounded notes:

- This is the **first** browser-storage usage in this frontend — a grep of `frontend/` for `localStorage` and `sessionStorage` found none — so this key establishes the naming convention (`wwa.<feature>.<purpose>.<version>`) for later slices.
- Only the link id and a timestamp are stored. Titles and URLs are resolved from the catalog rather than copied, so a renamed or re-pointed link shows its current state instead of a stale snapshot.

---

## 10. Field Mapping

The complete prototype-to-domain field mapping, including every renamed and every deliberately dropped
field, is maintained in one place: `service-directory-data-flow.md` §6.1. The mutation-input
normalisation rules are in §6.2 of the same document, and the mutation-to-audit mapping is in §6.3.

---

## 11. Migration And Schema Sync

| Item | Requirement |
|---|---|
| Forward migration | One new Flyway script creating `DA_SERVICE_DIRECTORY_CATALOG`. The next free version is **V20** — verified that the migration chain currently ends at `V19__add_scope_directory_agent.sql` |
| Greenfield Oracle schema | `docs/sql/ORACLE_CURRENT_SCHEMA.sql` must be regenerated to include the new table. This is mandatory because local and test profiles use in-memory H2 auto-DDL and never exercise that document, so staleness stays invisible until an Oracle deployment |
| Seed data | Installed by application code into an empty store, **not** by a migration script. Keeping seed content out of SQL means the same validation rules apply to seeded and administrator-entered data, and content changes do not require a new migration |
| Rollback | Dropping the table removes all catalog content. There is no data to preserve elsewhere, so rollback is a clean drop plus removal of the seeded content |
| Data volume | One row. Payload size at the MVP ceiling (20 scopes / 100 groups / 600 links) is on the order of tens to low hundreds of kilobytes of JSON, well within CLOB handling and single-request response norms |
