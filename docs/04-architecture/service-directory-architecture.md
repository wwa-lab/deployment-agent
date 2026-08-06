# System Architecture: Resource Center

> **Slice:** `service-directory`
> **Status:** Regenerated via `spec-to-architecture`; **Amended 2026-07-25** — product renamed to **Resource Center** (formerly Service Directory); optional link `iconKey` stays inside the catalog document (SD-FR-71)
> **Product name:** Resource Center · **Slice id:** `service-directory`
> **Last updated:** 2026-07-25
> **Source spec:** `docs/03-spec/service-directory-spec.md`
> **Companion documents:** `service-directory-data-flow.md`, `service-directory-data-model.md`
> **Decision record:** `docs/00-context/decisions/ADR-0010-service-directory-owns-its-catalog-store.md` (Accepted)

Abstraction note: this document stays at component and boundary level. Concrete classes, endpoints,
payloads, and file locations belong to `docs/05-design/service-directory-design.md` and the API guide.

---

## Overview

- **Architecture Summary**: Resource Center adds one Platform-shared capability to the existing WWA Agent Hub modular monolith. A new catalog module owns a single versioned configuration document describing directory scopes, groups, and links; a Platform REST resource serves the whole document to the Hub frontend on page load and applies administrator mutations to it; the existing audit component records each mutation. All discovery behavior — filtering, stage focus, search — is client-side over that one payload, and the personal Recently used list never leaves the browser.
- **Design Objective**: Configuration-driven navigation content that an administrator can change without a code release, with a data-ownership boundary that keeps navigation content out of runtime integration configuration.
- **Architectural Style**: Layered modular monolith, consistent with the rest of the Hub — Vue presentation layer, Spring MVC Platform API layer, domain service layer, JPA persistence layer. Read-mostly reference data served as a single aggregate document. No new runtime process, queue, scheduler, or external integration.

---

## Source Specification

- **Feature / System Name**: Resource Center (WWA Agent Hub Platform capability)
- **Scope Summary**: A config-driven catalog page at `/wwa/resource-center` offering scope / kind / SDLC-stage / text discovery over administrator-maintained destinations, plus `DEVOPS_ADMIN` CRUD with audit. Excludes link health checks, external discovery or sync, server-side personal history, and any storage inside Configuration Management.

---

## Architectural Drivers

### Key Functional Drivers

- The catalog structure must be data, not code: adding a scope, group, or link is an administrator action with no deploy (SD-FR-07, SD-FR-38).
- One page load must render the entire catalog, because all discovery is client-side (SD-FR-06, SD-FR-14 … SD-FR-23).
- Two audiences read the same store differently: readers see enabled entries only, administrators must also see disabled entries to manage them (SD-FR-08, SD-FR-09).
- Structure is heterogeneous by design: the SDLC scope renders as an ordered stage rail with owning agent names, other scopes render as plain buckets (SD-FR-11, SD-FR-12).
- Deletion is hierarchical: removing a scope or group removes its descendants atomically (SD-FR-41, SD-FR-42).
- Three seeded system scopes must survive administrator error (SD-FR-43).
- An empty environment must become useful without manual data entry, and seeding must never overwrite administrator intent (SD-FR-60, SD-FR-61).

### Key Non-Functional Drivers

- **Deny-by-default writes**: mutations require `DEVOPS_ADMIN` verified from the server-side user context; a client-supplied role is never trusted (SD-FR-37).
- **Guest read-only posture**: guest sessions may read; every guest write is blocked upstream of this module by the existing platform-wide guest enforcement (SD-FR-65).
- **Audit on mutation only**: exactly one entry per successful mutation, attributed to a human actor, identifying the affected entity; reads and personal-shortcut activity are never audited (SD-FR-53 … SD-FR-59).
- **Audit must not endanger the primary change**: the existing audit component already writes in its own transaction, so an audit failure cannot roll back a catalog mutation (SD-FR-58).
- **No silent lost updates**: concurrent administrator writes are resolved by version checking, with the loser receiving an explicit conflict (SD-FR-44).
- **Data-ownership boundary**: the catalog must not live in Configuration Management stores, and no configuration key may be added for it (SD-FR-63).
- **Two-database portability**: the same storage approach must work on Oracle in production and H2 in local and test profiles (SD-NFR-08).
- **Outbound safety**: only validated URL shapes are stored, and externally targeted links open without granting the opened page access to the Hub window (SD-FR-47, SD-FR-48, SD-NFR-03).

### Constraints And Assumptions

- The Hub is a single Spring Boot application with a Vue single-page frontend; this slice introduces no new deployable unit.
- Platform-shared REST resources live under a single `/api/platform` prefix and carry no agent parameter; agent isolation mechanisms do not apply to this capability (SD-FR-64).
- Authorization in this codebase is **imperative**, performed inside the web layer against the request's user context; declarative method-level security annotations are not part of the established pattern. *(Verified during grounding: no declarative authorization annotations exist in production backend code.)*
- The audit action vocabulary is a closed, string-persisted enumeration; extending it is additive and requires no schema migration. Its existing configuration-related actions denote Configuration Management changes and are therefore unsuitable for this capability. *(Verified.)*
- The audit read model exposes a free-form context map to clients but does not expose the entity's generic target columns, so identifying detail must travel in the context map to be visible to auditors. *(Verified.)*
- JSON-shaped data is persisted as character large objects through the repository's existing attribute-conversion approach, keeping Oracle and H2 behavior identical; vendor-native JSON typing is not used anywhere in the codebase. *(Verified.)*
- The frontend has exactly one Platform capability registry that feeds both the navigation flyout and the Home Shared Controls grid, so one registration serves both surfaces. *(Verified.)*
- [ASSUMPTION] Catalog size stays within the MVP ceiling in the spec (≤ 20 scopes, ≤ 100 groups, ≤ 600 links), which is what makes a single-document read and client-side filtering appropriate.
- [ASSUMPTION] Administrator write concurrency is low (a handful of edits per week by a small group), which is what makes document-level version conflict acceptable.

---

## System Context

### Primary Actors

| Actor | Role |
|---|---|
| Authenticated Hub user | Reads the catalog, filters and searches, opens destinations, owns a browser-local Recently used list |
| DEVOPS_ADMIN | Additionally creates, updates, and deletes directory scopes, groups, and links |
| Guest viewer | Reads the catalog only; all writes blocked platform-wide |
| Auditor / platform owner | Reviews catalog mutations through the existing Audit Log capability |

### External Systems

| System | Integration Purpose |
|---|---|
| None | This capability performs **no outbound calls**. Linked systems (Confluence, Jira, Jenkins, ARCAD, GitHub Enterprise, ServiceNow, and similar) are navigation targets only — the Hub stores and renders their URLs and never contacts them. No health probing, metadata fetching, or credential exchange exists in this slice. |

### System Boundary

Inside the boundary: the catalog document and its persistence, catalog read and mutation services,
validation, seeding, the Platform REST resource, and the Resource Center page with its client-side
discovery logic and browser-local Recently used list.

Outside the boundary: authentication and session management, guest write enforcement, the audit store
and Audit Log capability, Configuration Management and its stores, the Agent Contribute Dashboard and
its own content, every agent workspace, and every system a link points to.

---

## High-Level Architecture

### Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Users                                                                   │
│  Authenticated user (DEVELOPER · TL · AUDIT · MANAGEMENT) · DEVOPS_ADMIN  │
│  · Guest viewer (read-only)                                              │
└───────────────────────────────┬──────────────────────────────────────────┘
                                │ HTTPS (session cookie)
                                ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Hub Web App (Vue SPA, existing shell)                                   │
│  ┌────────────────────────────────┐  ┌─────────────────────────────────┐  │
│  │ Resource Center Page         │  │ Browser-local store            │  │
│  │ catalog render · filters ·     │  │ Recently used (max 8) —        │  │
│  │ stage rail · search · manage   │  │ never sent to the server       │  │
│  └────────────────────────────────┘  └─────────────────────────────────┘  │
│  Platform navigation registry (flyout + Home Shared Controls entry)       │
└───────────────────────────────┬──────────────────────────────────────────┘
                                │ REST / JSON over /api/platform
                                ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Hub API (Spring MVC)                                                    │
│  Auth chain: session → header fallback (local/test) → guest write block   │
│  ├──────────────────────────────────────────────────────────────────────┤ │
│  │  Platform shared web layer                                          │ │
│  │  Resource Center resource — read (all sessions) ·                  │ │
│  │  mutate (DEVOPS_ADMIN, imperative role check)                        │ │
│  ├──────────────────────────────────────────────────────────────────────┤ │
│  │  Resource Center domain module                                     │ │
│  │  ┌────────────────┐ ┌────────────────┐ ┌────────────────────────┐    │ │
│  │  │ Catalog read + │ │ Validation +   │ │ Seed provisioning      │    │ │
│  │  │ visibility     │ │ mutation       │ │ (empty store only)     │    │ │
│  │  │ filtering      │ │ (hierarchical) │ │                        │    │ │
│  │  └────────────────┘ └────────────────┘ └────────────────────────┘    │ │
│  ├──────────────────────────────────────────────────────────────────────┤ │
│  │  Persistence (JPA · CLOB-backed JSON document · optimistic version)  │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
└───────────┬───────────────────────────────────┬──────────────────────────┘
            │ JDBC                              │ in-process, own transaction
            ▼                                   ▼
┌────────────────────────────┐      ┌────────────────────────────────────┐
│  Relational DB             │      │  Existing Audit capability         │
│  Oracle (prod) · H2 (local)│      │  writes one HUMAN-actor entry per   │
│  Resource Center catalog │      │  successful catalog mutation        │
│  table (new, isolated)     │      │                                    │
│                            │      │  Configuration Management stores    │
│                            │      │  remain untouched by this slice     │
└────────────────────────────┘      └────────────────────────────────────┘
```

### Layer Summary

The capability spans four layers plus one client-only store:

- **Presentation Layer** — the Resource Center page renders the catalog and owns all discovery behavior (scope filter, kind filter, stage focus, search, empty states, manage mode). It is the only place where filtering happens, because the server always returns the whole visible catalog.
- **Application / API Layer** — a Platform-shared REST resource, sitting behind the existing authentication chain. It resolves the caller's role, decides read visibility, delegates mutations, and translates domain failures into the platform's standard error envelope. It holds no persistence logic.
- **Domain Layer** — a new Resource Center module with three responsibilities: read with visibility filtering, validated hierarchical mutation, and one-time seed provisioning. Structural rules (uniqueness, cascade removal, system-scope protection, URL shape) live here so the API layer and any future caller inherit them.
- **Persistence Layer** — one table holding one catalog row: the serialised document plus an optimistic version and update-attribution columns. Reads load one row; writes read-modify-write that row inside a transaction.
- **Client-only store** — Recently used lives in browser storage, is resolved against the freshly loaded catalog, and is deliberately unreachable from the server (privacy and scope constraint SD-NFR-09).

---

## Component Breakdown

### Frontend Components

- **Resource Center page**: orchestrates load, error, and empty states; owns filter state (active scope, active kind, active stage focus, search text) and manage-mode state.
- **Filter region**: scope chips, kind chips, SDLC stage rail, and search input. Enforces the interaction rules — single-select chips, stage focus hiding non-SDLC scopes, stage toggle-off, focus reset when leaving the SDLC scope.
- **Catalog region**: renders visible scopes → groups → links, applies the fixed kind sub-heading order, and shows per-kind add affordances when manage mode is active.
- **Link activation helper**: single decision point for open behavior — in-app routing for workspace links, protected new tab for other kinds, suppression for pending URLs — and the only place that records a Recently used entry.
- **Recently used region**: renders resolvable recent entries as chips with a clear action; degrades to an empty message when storage is empty, unavailable, or corrupt.
- **Recently used store adapter**: reads and writes the versioned browser storage key, caps the list, de-duplicates, and drops entries that no longer resolve.
- **Manage dialogs**: create and edit forms for scope, group, and link, plus a delete confirmation that states the descendant impact. Client validation mirrors server rules but is never the enforcement point.
- **Catalog client state**: holds the loaded catalog, loading and error flags, and replaces the whole catalog from each mutation response so the view converges without a second read.
- **Platform navigation registration**: one entry in the existing Platform capability registry, deliberately registered without an access-permission gate so every role including guest sees it.

### Backend Services

- **Resource Center Platform resource**: the only inbound surface. Read is available to any authenticated session; mutation paths perform the imperative `DEVOPS_ADMIN` check and reject otherwise. Returns the updated catalog from every mutation.
- **Catalog read service**: loads the single catalog row, triggers seeding when the store is empty, and projects the document for the caller — enabled-only for readers, complete for administrators who ask for it.
- **Catalog mutation service**: applies one create, update, or delete to the document inside a transaction. Owns uniqueness checks, hierarchical removal, system-scope protection, and version-conflict propagation.
- **Catalog validation component**: pure structural and URL-shape validation, independent of persistence, so the same rules apply to seeding and to administrator input.
- **Seed provisioning component**: installs the packaged seed catalog exactly once, only into an empty store, and never over an existing document.

### Orchestration / Execution Engine

- None. This capability has no workflow engine, no job dispatch, no retry loop, and no scheduled execution. Every operation is a single synchronous request. This is a deliberate difference from the Hub's release-flow capabilities.

### Configuration / Administration Modules

- **Manage mode** (frontend): reveals administration affordances for `DEVOPS_ADMIN` only; a purely presentational gate on top of the authoritative server check.
- **Catalog administration API** (backend): the mutation half of the Platform resource — the sole write path for directory content.
- **Explicit non-participation in Configuration Management**: this capability registers no configuration key, adds no configuration component, and reuses no access-scope directory entity. Configuration Management remains the owner of runtime integration settings only; the two administration surfaces stay separate.

### Monitoring / Audit Modules

- **Audit emission**: after a successful mutation, the mutation service calls the existing audit component once with a dedicated Resource Center action, human actor kind, and a context map identifying entity type, identifier, key or title, operation, and — for cascade deletes — the removed-descendant counts.
- **Audit review**: the existing Audit Log capability is the review surface. No Resource Center-specific history UI is introduced.
- **Correlation**: requests inherit the platform's existing request-correlation mechanism; this slice adds no bespoke metrics or alerting.

### Integration Adapters

- None. The absence of an outbound adapter is an architectural property of this capability, not an omission: catalog links are rendered for the browser to follow, so nothing in the Hub needs to reach the linked systems.

---

## Data Architecture

### Conceptual Entities

| Entity | Description | Key Attributes |
|---|---|---|
| Catalog | The single versioned container for all directory content and the unit of concurrency control | version, updated-by, updated-at, ordered scopes |
| Directory scope | Top-level category and filter chip; also chooses its rendering layout | key, title, description, layout, system flag, enabled, sort order |
| Directory group | A section inside a scope — either an SDLC stage or a plain bucket | key, title, description, type, stage key, stage order, owning agent name, enabled, sort order |
| Directory link | A single destination | identifier, title, description, URL, kind, kind label, enabled, sort order |

Field-level definitions, the entity-relationship view, and the seed inventory live in
`service-directory-data-model.md`.

### Configuration Objects

- **Seed catalog** — a packaged, environment-independent definition of initial content, applied only to an empty store. It is application-packaged content, not a database-held configuration key, so it can never be confused with Configuration Management data.
- **Recently used storage key** — a versioned browser storage key whose version suffix is bumped on incompatible shape changes.
- **Recently used cap** — a fixed value of 8 for MVP.

### State / Status Models

The catalog is reference data, not a workflow entity. The only per-entity lifecycle state is
availability: `enabled ⇄ disabled` by administrator update, with deletion removing the entity and its
descendants outright. There is no soft-delete state, no approval state, and no execution state.
Readers only ever observe enabled entities.

The catalog row additionally carries a monotonically increasing version used solely for optimistic
concurrency: a mutation computed against version *n* only commits while the stored version is still
*n*, otherwise the caller is told to reload.

### Persistence Responsibilities

- The Resource Center domain module is the **only** owner of catalog persistence. No other module reads or writes the catalog table, and this module writes nothing else.
- The catalog is stored as one document in one row, because it is always read whole, rendered whole, and versioned whole. Rationale, alternatives, and consequences are recorded in `ADR-0010`.
- Audit records are owned by the existing audit capability; this module only emits.
- Recently used is owned by the browser and is intentionally not persisted server-side.
- Configuration Management stores are read-only-by-absence for this slice: the capability neither reads nor writes them.

---

## Integration Architecture

### External Systems

None. There is no external integration to describe: the capability stores and renders URLs and never
calls them. Consequences that follow from this and are therefore architecturally guaranteed: no
credential handling, no timeout or retry policy, no circuit breaking, no outbound allow-listing, and
no availability coupling between the Hub and any linked system.

### Internal Capability Boundaries

- **Authentication chain → Resource Center resource**: the chain establishes the session-derived user context and blocks guest writes before the resource is reached; the resource consumes the resulting context and never re-authenticates.
- **Resource Center resource → Resource Center domain module**: the resource passes the caller's identity and role decision plus a validated request; the module owns all structural rules.
- **Resource Center domain module → audit capability**: one-way, in-process, after-commit-intent emission in the audit capability's own transaction. Failure is contained on the audit side (SD-FR-58).
- **Resource Center domain module → Configuration Management**: deliberately **no** relationship. This is the boundary the slice exists to protect (`ADR-0010`).
- **Resource Center ↔ Agent Contribute Dashboard**: no runtime coupling in MVP. Overlapping SDLC guideline and feedback content is aligned by content ownership, not by code (SD-FR-66). A shared content source is a separate future slice.
- **Agent isolation mechanisms**: not applicable. This is a Platform capability with no agent parameter, so no agent-forcing or agent boundary guarding participates.

### Event / Polling / Callback Patterns

None. There is no event publication, no polling loop, no webhook, and no push channel. Other users
observe an administrator's change on their next page load; this eventual visibility is an accepted
property of reference data, stated in the spec's lifecycle (§Functional Scope, step 6).

---

## Workflow / Runtime Architecture

### Request Flow

1. The browser requests the Resource Center route; the frontend router guard ensures an authenticated session exists.
2. The page issues one catalog read.
3. The authentication chain resolves the session into a server-side user context and passes the request through.
4. The Platform resource inspects the caller's role to decide whether disabled entries may be included, then delegates to the read service.
5. The read service loads the single catalog row. If the store is empty, seed provisioning installs the packaged catalog once, and the newly stored document is served.
6. The projected document is returned in one response; the page renders it and resolves the browser-local Recently used entries against it.
7. All subsequent filtering, searching, stage focus, and sub-heading grouping happen in the browser with no further requests.

### Mutation Flow

1. A `DEVOPS_ADMIN` submits one create, update, or delete from manage mode.
2. The Platform resource verifies the role from the server-side user context and rejects a non-administrator with a forbidden response.
3. The mutation service loads the current catalog row, validates the request against structural and URL rules, and applies the change to the document — including hierarchical removal for a scope or group deletion.
4. The transaction commits only if the row's version is unchanged; otherwise the caller receives a conflict response and no change is applied.
5. On success, the audit capability records one entry in its own transaction with the human actor kind and an identifying context map.
6. The updated catalog is returned in the mutation response, and the client replaces its held catalog with it.

### State Transitions

The only durable state transitions in this capability are availability changes and structural
existence:

- `disabled → enabled` — administrator update sets an entity available; it becomes visible to readers.
- `enabled → disabled` — administrator update hides an entity from readers while keeping it manageable.
- `absent → present` — administrator create, or one-time seed provisioning into an empty store.
- `present → absent` — administrator delete; for a scope or group this transition applies atomically to all descendants.
- Catalog version `n → n+1` — accompanies every successful mutation and is the mechanism that rejects stale writes.

### Validation Flow

Validation happens at two points with a single authoritative source:

1. **Client-side, on submit** — immediate field feedback; a convenience only.
2. **Server-side, inside the domain module before the document is modified** — authoritative. Structural rules (key pattern and uniqueness, required title, length limits, sort-order range, stage-key membership, kind membership) and URL rules (in-Hub path for workspace links, `http`/`https` for all other kinds, rejection of script-like schemes, protocol-relative, and blank URLs) are enforced here, so seeding is validated by the same code path as administrator input.

A validation failure rejects the whole request, leaves the document untouched, and writes no audit
entry.

### Failure And Conflict Handling

- **Rejected requests** (unauthenticated, forbidden, invalid, not found, conflicting) leave the catalog byte-identical and produce no audit entry.
- **Version conflict** is surfaced explicitly rather than merged, because a silent merge of two administrators' document edits could resurrect deleted entries. The client's remedy is to reload and reapply — acceptable given the assumed low write concurrency (SD-R-03).
- **Audit failure after a valid mutation** is contained by the audit capability's separate transaction; the mutation stands and the failure is logged server-side.
- **Read failure on refresh** leaves the previously rendered catalog on screen with an error banner, so a transient backend problem does not blank a page users navigate by.
- **Browser storage failure** degrades Recently used to empty and never affects catalog rendering.
- There is no retry, backoff, or compensation mechanism, because there is no asynchronous or external work to retry.

---

## API / Interface Boundaries

### Major Inbound Interfaces

| Interface | Consumer | Purpose |
|---|---|---|
| Platform catalog read resource | Resource Center page | Fetch the whole catalog for rendering; visibility depends on the caller's role |
| Platform catalog administration resource | Resource Center manage mode | Create, update, and delete directory scopes, groups, and links; returns the updated catalog |

Both live under the shared `/api/platform` prefix and accept no agent parameter. Concrete paths,
payloads, and status codes are specified in
`docs/05-design/contracts/service-directory-API_IMPLEMENTATION_GUIDE.md`.

### Internal Module Boundaries

- The Platform resource depends on the Resource Center domain module; the module has no dependency on the web layer.
- The domain module depends on the audit capability's write interface only.
- No other Hub module depends on the Resource Center module, and the Resource Center module depends on no other domain module. This isolation is what makes the boundary in `ADR-0010` enforceable by inspection.

### Outbound Integrations

| Target | Protocol | Triggered by |
|---|---|---|
| None | — | — |

### Event / Polling / Callback Patterns

None, as stated under Integration Architecture.

---

## Deployment / Environment Considerations

- **Supported environments**: production on Oracle with validated schema, plus local and test profiles on in-memory H2 — the Hub's existing profile model. No new environment or infrastructure is introduced.
- **Runtime assumptions**: the capability ships inside the existing single application deployment; no separate service, worker, cache, or scheduler.
- **Schema management**: the new table arrives as the next forward-only migration in the existing chain, and the greenfield Oracle schema document must be regenerated to include it, because local and test profiles never exercise that document.
- **Configuration separation**: seed content is application-packaged and identical across environments; environment-specific reality (the real ARCAD and GitHub Enterprise URLs) is applied by administrators after deployment rather than baked into per-environment configuration files.
- **Secrets handling**: none required — the catalog holds no credentials. Unknown URLs remain reserved placeholders rather than guesses at internal hostnames.
- **Operational concerns**: catalog content is durable business-relevant configuration, so it participates in normal database backup and restore; audit records of catalog changes remain in the existing audit store.

---

## Security / Reliability / Observability

### Access Control

- Read: any authenticated session, including guest (SD-FR-65, pending ratification of SD-OQ-01).
- Write: `DEVOPS_ADMIN` only, checked imperatively in the web layer against the session-derived user context, consistent with the Hub's existing platform administration surfaces.
- Guest writes are blocked platform-wide before reaching this capability; the slice adds no exemption to that filter.
- Visibility of disabled entries is a role-dependent projection decided server-side, not a client-side filter, so a non-administrator never receives hidden content.
- No new role, permission, or scope type is introduced.

### Input And Output Protection

- Stored URLs are validated by shape and scheme on every write, including during seeding.
- Rendered link text and URLs are escaped by the frontend framework's standard binding; the page constructs no raw HTML from catalog content.
- Externally targeted links are opened without granting the opened document a handle back to the Hub window.
- Error responses use the platform's standard error envelope and must not leak internal paths or stack traces.

### Secret Protection

Not applicable by design — the capability stores and transmits no credentials or secrets. This is
enforced by the data model, which has no credential field, unlike Configuration Management's component
store.

### Auditability

- One entry per successful mutation, human actor kind, dedicated Resource Center action vocabulary distinct from Configuration Management actions.
- Identifying detail is carried in the audit context map, which is the part of the audit record the review surface actually exposes.
- Cascade deletes record one entry with descendant counts rather than a burst of per-descendant entries, keeping the audit trail readable.
- Reads, filtering, and Recently used are never audited, so the audit trail stays a record of change rather than of browsing.

### Resilience

- Every operation is a single synchronous transaction; there is no partially applied state to reconcile.
- Optimistic version checking prevents lost updates; the conflict is explicit.
- Audit emission is isolated in its own transaction and cannot roll back a catalog change.
- The frontend keeps the last good catalog on a failed refresh, and Recently used failures are contained.

### Monitoring / Logging

- Requests inherit the platform's existing correlation and logging behavior.
- Audit failures are logged server-side at error level rather than surfaced to the user.
- No new dashboards, metrics, or alerts are introduced by this slice; catalog changes are observable through the Audit Log capability.

---

## Risks / Tradeoffs

| # | Risk / Tradeoff | Notes |
|---|---|---|
| 1 | Single-document storage versus normalised tables | Chosen deliberately: the catalog is always read and rendered whole, which makes one document simpler, cheaper to migrate, and trivially consistent for hierarchical deletes. The cost is document-level concurrency (see risk 2) and the loss of per-row SQL querying. Recorded in `ADR-0010`. |
| 2 | Document-level optimistic locking can reject edits to unrelated links | Accepted for MVP under the low-write-concurrency assumption. An explicit conflict is strictly safer than a silent lost update. Normalising later is a contained change because the module owns all catalog persistence. |
| 3 | Client-side filtering assumes a bounded catalog | Valid within the stated MVP ceiling. Crossing it would force server-side filtering and pagination, which would also change the "one read serves the page" property. The ceiling is stated in the spec so growth is detected rather than discovered. |
| 4 | Extending the shared audit action vocabulary | Additive and migration-free because the action is persisted as a string. The alternative — reusing configuration actions — was rejected because it would make Configuration Management and Resource Center changes indistinguishable to auditors. |
| 5 | Identifying detail lives in the audit context map, not in the audit entity's generic target columns | Deliberate: those columns exist but are written by nothing and exposed by nothing, so using them would produce audit records that auditors cannot see. Populating them properly is a separate audit-capability improvement, not this slice's scope. |
| 6 | Overlapping content with the Agent Contribute Dashboard | Two surfaces can drift while alignment is manual. Accepted for MVP with an explicit owner responsibility; a shared source is a candidate follow-up slice (SD-OQ-03). |
| 7 | Term collision on the word "scope" | The codebase already uses "scope" for access scoping. Mitigated by consistently qualifying the new concept as a *directory scope* and by keeping the two models in separate modules with no shared types. |
| 8 | Browser-local Recently used cannot follow a user across devices | Accepted: it keeps personal browsing history out of the server entirely, which is the cheapest defensible privacy posture for MVP. |
| 9 | Eventual visibility of administrator changes | Other users see changes on their next load. Acceptable for reference data and cheaper than any live-refresh mechanism. |
| 10 | Guest read access is a committed default, not a ratified decision | Flagged in the spec as SD-OQ-01. Reversal before implementation is inexpensive; reversal after release would be a visible behavior change. |

---

## Open Questions

1. Should guest sessions be able to read the catalog at all, or should the route redirect them? The architecture currently assumes read access is allowed and relies on the existing platform-wide guest write block (SD-OQ-01).
2. What are the production ARCAD and GitHub Enterprise URLs? Until they are supplied, seeded placeholders render as pending rather than as working links (SD-OQ-02).
3. Should SDLC guideline and feedback content eventually have a single shared source with the Agent Contribute Dashboard, and if so, which surface owns it (SD-OQ-03)?
4. Must `ADR-0010` be accepted before implementation begins? The architecture assumes yes, since the store boundary is the reason this capability exists as a separate module (SD-OQ-04).
