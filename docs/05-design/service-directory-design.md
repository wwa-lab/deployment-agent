# Detailed Design: Service Directory

> **Slice:** `service-directory`
> **Status:** Regenerated via `architecture-to-design` — awaiting user acceptance
> **Last updated:** 2026-07-25
> **Source spec (authoritative):** `docs/03-spec/service-directory-spec.md`
> **Source architecture:** `docs/04-architecture/service-directory-architecture.md`
> **Companions:** `docs/04-architecture/service-directory-data-model.md`, `docs/04-architecture/service-directory-data-flow.md`, `docs/05-design/contracts/service-directory-API_IMPLEMENTATION_GUIDE.md`
> **UX baseline:** `docs/prototypes/wwa-service-directory.html`

---

## Overview

Service Directory adds one Platform-shared capability to the existing Hub: a page at
`/wwa/service-directory` that renders an administrator-maintained catalog of destinations
(`directory scope → group → link`), plus the Platform API, domain module, and single-table persistence
behind it. Reads serve the whole catalog in one request; all filtering happens client-side;
`DEVOPS_ADMIN` mutations are validated, versioned, and audited.

This design is implementation-facing. Every reference to existing code below was grep-verified during
the grounding pass and carries a `file:line` anchor. Unverifiable claims are tagged.

> **Correction carried into this design — read before coding.** The Java base package in this
> repository is **`com.wwa.agenthub`**, not `com.wwa.deploymentagent`. `CLAUDE.md` and `AGENTS.md`
> still document the old package in their Architecture Boundaries sections; `PROJECT_RULES.md` does not.
> All paths in this document use the real package. See `docs/00-context/lessons-learned.md`
> (LL-2026-07-25c).

---

## Source Architecture

| Aspect | Position taken by the architecture |
|---|---|
| Placement | Platform-shared capability; not an agent workspace; no agent parameter anywhere |
| Read model | One request returns the whole catalog, projected by the caller's role |
| Write model | Granular per-entity mutations applied to one versioned document, each returning the updated catalog |
| Persistence | One row, one CLOB JSON document, optimistic version (`ADR-0010`, data model §2) |
| Audit | One entry per successful mutation, dedicated action types, identifying detail in the context payload |
| Integrations | None. The Hub never calls a linked system |
| Client state | Catalog in a Pinia store; Recently used in browser storage only |

---

## Design Assumptions

1. `[Assumption]` Catalog size stays within the spec ceiling (≤ 20 scopes, ≤ 100 groups, ≤ 600 links), which is what makes one payload plus client-side filtering correct.
2. `[Assumption]` Administrator write concurrency is low, so document-level version conflict is acceptable rather than requiring row-level locking.
3. `[Assumption]` Guest sessions may read the catalog (spec default, SD-OQ-01). If reversed, the change is confined to a route guard plus one read-side role check.
4. Verified: authorization in this codebase is imperative in the web layer (`user.hasRole(...)` → `ForbiddenAppException`), for example `ConfigurationController.java:39-48`. There is no `@PreAuthorize` anywhere in `src/main/java` — do not introduce it here.
5. Verified: the platform Axios client lives at `frontend/src/api/platformClient.ts:11-17` with `baseURL: '/api/platform'`, and platform pages live in `frontend/src/views/` — **not** under `frontend/src/platform/`, which holds only shared release-flow views and composables.
6. Verified: `frontend/src/config/agentRegistry.ts:75-108` (`platformCapabilities`) is the single registry feeding both the flyout (`WorkspaceLayout.vue:230-247`) and Home Shared Controls (`WwaHomeView.vue:118-131`).
7. Verified: there is no generic modal component; dialogs are standalone single-file components using the global `.modal-overlay` / `.modal` classes in `frontend/src/assets/main.css:202-260`. There is no toast component — `frontend/src/api/correlationId.ts:10` only notes one as future work.

---

## Design Scope

**In scope**

- Backend: one entity, one repository, one domain service (with validation and seeding), one Platform controller, DTOs, two new audit action constants, one Flyway migration, greenfield schema regeneration.
- Frontend: one route, one registry entry, one view, one API module, one Pinia store, one dialog component, one delete-confirmation component, one Recently used composable, one link-activation helper, catalog TypeScript types.
- Tests: controller/contract tests for read visibility, authorization, validation, cascade delete, system-scope protection, conflict handling, and audit side effects.

**Out of scope** (mirrors the spec's Out of Scope list — no module below implements any of these)

- Any write to Configuration Management stores or any new `ConfigKey`.
- Outbound calls to linked systems, health probes, or discovery/sync jobs.
- Server-side Recently used, favourites, or usage analytics.
- Drag-and-drop ordering, bulk import, draft/approval workflow.
- New roles, permissions, or scope types.
- Changes to the Agent Contribute Dashboard's existing status storage.

---

## Module Design

### M1 — Persistence: `ServiceDirectoryCatalogEntity`

**Location:** `src/main/java/com/wwa/agenthub/domain/servicedirectory/ServiceDirectoryCatalogEntity.java` (new)

| Aspect | Design |
|---|---|
| Table | `DA_SERVICE_DIRECTORY_CATALOG`, following the existing `DA_` prefix convention (`domain/configuration/ConfigurationComponent.java:21`, `domain/audit/AuditLogEntry.java:24`) |
| Id | `String` UUID assigned in `@PrePersist`, mirroring `ReleaseFlow.java:40-42` and `ReleaseFlow.java:98-103` |
| Payload | `List<DirectoryScope> scopes` stored in a `CLOB` column named `payload` |
| Version | `@Version Long version` on column `version`, as in `ReleaseFlow.java:94-96` |
| Timestamps | `@CreationTimestamp created_at`, `@UpdateTimestamp updated_at`, as in `ReleaseFlow.java:86-92` |
| Attribution | `String updatedBy` on column `updated_by`, nullable (null for the seeded row) |

**Converter decision.** The existing `JsonAttributeConverter` converts only `Map<String, Object>`
(`util/JsonAttributeConverter.java:26`), and `StringListJsonAttributeConverter` /
`AccessScopeListJsonAttributeConverter` are similarly type-specific
(`domain/auth/AccessGrant.java:41-47`). A typed list of scopes therefore needs a new converter that
follows the same shape:

- **New:** `src/main/java/com/wwa/agenthub/util/DirectoryScopeListJsonAttributeConverter.java`, `@Converter`, implementing `AttributeConverter<List<DirectoryScope>, String>`, applied as `@Convert(converter = DirectoryScopeListJsonAttributeConverter.class)` with `@Column(name = "payload", columnDefinition = "CLOB")` — the exact pattern documented at `util/JsonAttributeConverter.java:18-26`.
- On a deserialisation failure the converter throws rather than returning an empty list: a corrupt payload must surface as a 500, never as a silently empty catalog that an administrator might then "fix" by re-seeding over real content.

**Nested value types** (new records in `domain/servicedirectory/model/`, serialised inside the payload;
field lists are specified in the data model §4):

- `DirectoryScope(key, title, description, layout, system, enabled, sortOrder, groups)`
- `DirectoryGroup(key, title, description, type, stageKey, stageOrder, agentName, enabled, sortOrder, links)`
- `DirectoryLink(id, title, description, url, kind, kindLabel, enabled, sortOrder)`

These are domain value types, not JPA entities — there are no child tables.

**Enums** (new, in `src/main/java/com/wwa/agenthub/contracts/enums/`, matching the existing convention
that enum constant names equal their persisted string values):

| Enum | Constants |
|---|---|
| `DirectoryLinkKind` | `docs`, `tool`, `workspace`, `repo` |
| `DirectoryScopeLayout` | `stage_strip`, `buckets` — serialised as `stage-strip` / `buckets` in JSON and the API |
| `DirectoryGroupType` | `stage`, `bucket` |
| `SdlcStageKey` | `planning`, `estimation`, `discovery`, `build`, `testing`, `deployment`, `maintenance` |

**Cross-cutting sync obligation.** `DEVELOPMENT_STANDARDS.md:44` requires the matching TypeScript union
to change with any `contracts/enums/` change. All four enums above are consumed by the frontend, so
`frontend/src/types/index.ts` gains the corresponding unions in the same change (see F1 in UI design).
The two new `AuditActionType` constants are the exception — verified that
`frontend/src/types/index.ts:250` types `actionType` as plain `string`, so no union exists to update.

### M2 — Repository: `ServiceDirectoryCatalogRepository`

**Location:** `src/main/java/com/wwa/agenthub/domain/servicedirectory/ServiceDirectoryCatalogRepository.java` (new)

`extends JpaRepository<ServiceDirectoryCatalogEntity, String>` with one added method:

- `Optional<ServiceDirectoryCatalogEntity> findFirstByOrderByIdAsc()` — the singleton accessor. A deterministic ordering is used so that if a duplicate row ever appeared, every read would still pick the same one instead of alternating.

No other query methods. The singleton invariant is enforced by M3, not by the repository.

### M3 — Domain service: `ServiceDirectoryService`

**Location:** `src/main/java/com/wwa/agenthub/domain/servicedirectory/ServiceDirectoryService.java` (new)

Sole owner of catalog persistence and of every structural rule. The controller holds no business logic.

| Method | Responsibility |
|---|---|
| `ServiceDirectoryCatalogDto read(boolean includeDisabled)` | `@Transactional`. Load-or-seed, then project. Non-admin callers always pass `false` |
| `ServiceDirectoryCatalogDto read(boolean includeDisabled)` | *(above)* |
| `ServiceDirectoryCatalogDto createScope(DirectoryScopeUpsertRequest, UserContext)` | Validate, append, save, audit. **No version parameter** (SD-FR-67) |
| `ServiceDirectoryCatalogDto updateScope(String scopeKey, long expectedVersion, DirectoryScopeUpsertRequest, UserContext)` | Assert version, validate, replace in place, save, audit |
| `ServiceDirectoryCatalogDto deleteScope(String scopeKey, long expectedVersion, UserContext)` | Assert version; reject when `system`; cascade-remove groups and links; save; audit with descendant counts |
| `ServiceDirectoryCatalogDto createGroup(String scopeKey, DirectoryGroupUpsertRequest, UserContext)` | Validate (including scope existence and key uniqueness within scope), append, save, audit |
| `ServiceDirectoryCatalogDto updateGroup(String scopeKey, String groupKey, long expectedVersion, …)` | Assert version, then as above, in place |
| `ServiceDirectoryCatalogDto deleteGroup(String scopeKey, String groupKey, long expectedVersion, UserContext)` | Assert version; cascade-remove links; save; audit with link count |
| `ServiceDirectoryCatalogDto createLink(String scopeKey, String groupKey, DirectoryLinkUpsertRequest, UserContext)` | Validate, assign a UUID id, append, save, audit |
| `ServiceDirectoryCatalogDto updateLink(String linkId, long expectedVersion, DirectoryLinkUpsertRequest, UserContext)` | Assert version; locate by id across the document; support moving between groups via the request's target scope and group keys; save; audit including the move |
| `ServiceDirectoryCatalogDto deleteLink(String linkId, long expectedVersion, UserContext)` | Assert version; remove by id; save; audit |

**Committed decisions inside M3**

- **Addressing.** Scopes and groups are addressed by `key` (stable, human-meaningful, already unique at their level). Links are addressed by `id`, because a link has no key and because the browser's Recently used list stores exactly this id. Mixed addressing is deliberate, not an oversight.
- **Link lookup is a full document walk.** With ≤ 600 links, a linear search is simpler and cheaper than maintaining an index. No caching layer is introduced.
- **Mutations are read-modify-write in one transaction.** Load the singleton, mutate the in-memory tree, `save`.
- **Stale-page detection needs an explicit precondition, and this is the one design decision most likely to be implemented wrongly.** Every update and delete takes `expectedVersion`. The first thing the method does, before validation, is compare it to the loaded row's `version`; on mismatch it throws the existing `OptimisticLockConflictException("Service Directory catalog")` (`errors/OptimisticLockConflictException.java:4-9`), which is an `AppException` carrying 409 and code `OPTIMISTIC_LOCK_CONFLICT`, mapped by `GlobalExceptionHandler.java:24-28`.

  Why this cannot be skipped: JPA's `@Version` alone only detects two transactions overlapping *in flight*. Every mutation here loads the row at the start of its own transaction, so the sequence "admin A loads the page → admin B saves → admin A saves ten minutes later" produces no version conflict at all — A's request reads B's version and overwrites B's work silently. That is precisely the lost update SD-FR-44 forbids. The `@Version` column stays and still guards the same-instant race (SD-FR-68), reaching 409 through the *other* handler at `GlobalExceptionHandler.java:31-36`. Two layers, two windows, one status code.
- **Creates deliberately take no version** (SD-FR-67). A create appends to whatever the current document is and cannot overwrite anything, so a precondition there would reject harmless work. Implementers should resist "consistency" pressure to add one.
- **`sortOrder` default on create** is `max(sibling sortOrder) + 10`, so new entries land at the end and leave gaps for later manual reordering. Trace: empty list → 10; siblings 10, 20 → 30; siblings 9990 and above → clamp to 9999 and let the tie-break by key/title decide order.
- **Audit is emitted after the mutation is applied and before the method returns**, by calling the existing logger. Ordering is safe because the logger already writes in its own transaction (`domain/audit/AuditLoggerService.java:110-117`), so an audit failure cannot roll back the catalog change (SD-FR-58).
- **Seeding** is delegated to M5 and only ever invoked from `read` when the repository returns empty.

### M4 — Validation: `ServiceDirectoryValidator`

**Location:** `src/main/java/com/wwa/agenthub/domain/servicedirectory/ServiceDirectoryValidator.java` (new)

Pure functions, no repository dependency — so seeding and administrator input pass through identical
rules (spec §Validation Flow). Throws `ValidationAppException` (`errors/ValidationAppException.java`,
400 `VALIDATION_ERROR`) with the offending field name in the message. Reuses existing error types; no
new exception class is introduced.

| Rule | Implementation | Spec |
|---|---|---|
| Key normalisation and pattern | `trim().toLowerCase()`, then `^[a-z0-9][a-z0-9_-]{1,31}$` | SD-FR-49 |
| Scope key uniqueness | Across all scopes | SD-FR-40 |
| Group key uniqueness | Within the parent scope only | SD-FR-40 |
| Title required, ≤ 120; description ≤ 240 | Trim then length-check | SD-FR-50 |
| `sortOrder` range | 0 … 9999 inclusive | SD-FR-50 |
| `kind` membership | `DirectoryLinkKind` | SD-FR-46 |
| `stageKey` / `stageOrder` presence | Required together when `type = stage`; rejected when `type = bucket` | SD-FR-51 |
| Stage identity | When `type = stage`, require `key.equals(stageKey)` — a stage has one identifier, so the rail cannot disagree with the document | SD-FR-51 |
| One stage strip | Reject a create or update that would leave two scopes with `layout = stage-strip` | SD-FR-70 |
| URL shape | `workspace` → must match `^/wwa/[A-Za-z0-9._~\-/]*$` (no query string, no fragment, no percent-encoding); other kinds → scheme must be `http` or `https`, compared case-insensitively | SD-FR-47 |
| URL denial list | Reject blank, `javascript:`, `data:`, `vbscript:` (case-insensitive), and protocol-relative `//host` | SD-FR-48 |
| System scope deletion | Reject when `system` is true | SD-FR-43 |
| Key immutability | On any scope or group update, reject a submitted `key` that differs from the path key. Applies to every scope, not just system ones — a key is chosen once at create time | SD-FR-43 |

**URL rule trace** (Rule 3 of the grounding protocol — three cases including one where the rule must
not fire):

| Input | Kind | Outcome |
|---|---|---|
| `/wwa/audit-log` | `workspace` | Accepted |
| `/wwa/audit-log` | `tool` | Rejected — non-workspace kinds require `http`/`https` |
| `HTTPS://Github.example.com/org` | `repo` | Accepted — scheme compared case-insensitively |
| `//evil.example/x` | `tool` | Rejected — protocol-relative |
| `javascript:alert(1)` | any | Rejected |
| `/config` | `workspace` | Rejected — outside the `/wwa/` namespace |
| `/wwa/audit-log?tab=all` | `workspace` | Rejected — the pattern admits no query string |
| `https://arcad.example.invalid/` | `tool` | **Accepted for storage.** The `.invalid` suffix is a rendering concern (pending URL), not a validation failure — the rule must not fire here, otherwise the seed could not be stored |

That last row is the deliberate non-firing case: validation and pending-URL rendering are separate
concerns, decided in different layers.

### M5 — Seeding: `ServiceDirectorySeedLoader`

**Location:** `src/main/java/com/wwa/agenthub/domain/servicedirectory/ServiceDirectorySeedLoader.java` (new)
**Resource:** `src/main/resources/service-directory/seed-catalog.json` (new)

| Aspect | Design |
|---|---|
| Trigger | Called by `ServiceDirectoryService.read` only when the repository returns empty — lazy, not an `ApplicationRunner`, so an empty database in any profile self-heals on first page view without startup coupling |
| Idempotency | Guarded by the empty-store check inside the same transaction as the insert, so two concurrent first requests cannot both insert. If the unique-ish race still produced two rows, `findFirstByOrderByIdAsc` keeps reads deterministic (SD-FR-61) |
| Validation | Runs the seed through `ServiceDirectoryValidator`; an invalid seed fails loudly at first read rather than persisting bad content |
| Never overwrites | Only inserts into an empty store — including a catalog an administrator deliberately emptied |
| Attribution | `updatedBy` left null; the seed has no human author |
| Content | Data model §8: seven SDLC stage groups, Common (Platform + Engineering tools), External. No example `security` scope, no fabricated recents, unknown URLs use the reserved `.invalid` suffix |

**Why a classpath JSON resource rather than a Flyway insert:** content edits then need no migration, and
the seed passes through the same validator as administrator input. This mirrors the existing pattern of
holding structured static content as a packaged resource, as the Agent Contribute Dashboard does with
`frontend/src/config/agentContributionDashboard.json`, while keeping the catalog itself server-owned.

### M6 — Web layer: `ServiceDirectoryController`

**Location:** `src/main/java/com/wwa/agenthub/platform/web/shared/ServiceDirectoryController.java` (new)

Sits beside the existing platform controllers in that package (`AccessGrantController`,
`AuditLogController`, `AuthController`, `ConfigurationController`, `TemplateDownloadController`,
`AgentContributionDashboardController`). Follows their exact style: `@RestController`,
`@RequiredArgsConstructor`, `@RequestMapping("/api/platform/service-directory")`,
`@AuthenticationPrincipal UserContext user`, DTO records returned inside `ResponseEntity.ok(...)`.

| Concern | Design |
|---|---|
| Base path | `/api/platform/service-directory` |
| Read authorization | Any authenticated session, including `GUEST`. `includeDisabled=true` is honoured only for `DEVOPS_ADMIN`; for anyone else the parameter is ignored rather than rejected, so a stale bookmark cannot 403 a read |
| Write authorization | `private void validateAdmin(UserContext user, String action)` throwing `ForbiddenAppException(action)` — the same private-helper shape as `DeploymentReleaseFlowController.java:277-281` and the inline check at `ConfigurationController.java:39-48` |
| Agent parameter | None. Platform capability; `AgentBoundaryGuard` is not involved |
| Concurrency parameter | `@RequestParam long expectedVersion` on all six update and delete methods, absent from the three create methods. Declared as a primitive `long` with no default, so a missing parameter fails the request as 400 in Spring MVC before any handler code runs — the absence of a default is the mechanism that prevents an unversioned update from silently passing (SD-FR-44) |
| Error translation | None written. Domain exceptions map through the existing `GlobalExceptionHandler.java:24-28` (`AppException` → its status, covering both `ValidationAppException` 400 and `OptimisticLockConflictException` 409) and `:31-36` (a raw JPA lock failure → 409) |
| Bean validation | `@Valid` on request bodies for shape-level checks, with semantic rules in M4 — the mixed pattern already used at `ConfigurationController.java:39-42` |

Endpoint inventory, payloads, and status codes: `contracts/service-directory-API_IMPLEMENTATION_GUIDE.md`.

### M7 — Audit integration

Two constants are appended to `src/main/java/com/wwa/agenthub/contracts/enums/AuditActionType.java`
(currently `:4-24`):

| Constant | Used for |
|---|---|
| `service_directory_update` | Scope, group, and link create and update |
| `service_directory_delete` | Scope, group, and link delete (including cascades) |

No migration is needed — the column is `@Enumerated(EnumType.STRING)` (`AuditLogEntry.java:81-83`).

Emission uses the existing overload
`AuditLoggerService.log(UserContext user, AuditActionType actionType, Map<String, Object> context)`
(`AuditLoggerService.java:152-154`), which routes into the `REQUIRES_NEW` writer at
`AuditLoggerService.java:110-117` and sets `ActorKind.HUMAN` at `:125`.

Context map keys (data flow §6.3):

| Key | Value |
|---|---|
| `target_type` | `SERVICE_DIRECTORY` |
| `entity` | `scope` \| `group` \| `link` |
| `entity_key` | Scope or group key, or the link id |
| `entity_title` | Title at the time of the change |
| `operation` | `create` \| `update` \| `delete` |
| `removed_groups`, `removed_links` | Cascade deletes only |
| `from_scope_key`, `from_group_key`, `to_scope_key`, `to_group_key` | **Link update only, and only when the link actually moved.** Omitted entirely for an in-place edit |

A moved link is the one mutation where the interesting fact is not in the entity's own fields. Without
these four keys the audit trail for "someone moved the production runbook out of Deployment" is
indistinguishable from a title tweak, which defeats the purpose of auditing the change. The keys are
omitted rather than set to equal values when nothing moved, so the presence of `to_group_key` is itself
the signal that a move happened — a reader does not have to compare two values to find out.

The context map is used rather than the entity's `target_type` / `target_id` columns
(`AuditLogEntry.java:120-127`) because those columns are written by nothing and are not exposed by
`AuditLogEntryDto` (`AuditLogEntryDto.java:11-23`) — an auditor could not see them. `contextPayload`
**is** exposed there. Rationale and scope boundary: data model §7.2.

### F1 — Frontend types

**Location:** `frontend/src/types/index.ts` (existing; additions grouped with the other domain
interfaces, following the file's existing organisation of unions first)

Added unions and interfaces, kept in exact lockstep with M1's enums per `DEVELOPMENT_STANDARDS.md:44`:

```ts
export type DirectoryLinkKind = 'docs' | 'tool' | 'workspace' | 'repo'
export type DirectoryScopeLayout = 'stage-strip' | 'buckets'
export type DirectoryGroupType = 'stage' | 'bucket'
export type SdlcStageKey =
  | 'planning' | 'estimation' | 'discovery'
  | 'build' | 'testing' | 'deployment' | 'maintenance'
```

plus `DirectoryLink`, `DirectoryGroup`, `DirectoryScope`, and `ServiceDirectoryCatalog` interfaces
mirroring the data model's field lists.

### F2 — API module: `frontend/src/api/serviceDirectory.ts` (new)

Uses the shared `platformClient` (`frontend/src/api/platformClient.ts:11-17`), matching the function
style of `frontend/src/api/config.ts:106-118` and `frontend/src/api/accessGrants.ts:18-21`. One exported
function per endpoint; every mutation returns the updated catalog so the store can replace state
wholesale.

The client's existing 401 interceptor (`platformClient.ts:21-42`) already redirects to `/login`, and its
error normalisation already merges `message` and `details` — so this module adds no bespoke error
handling.

### F3 — Store: `frontend/src/stores/serviceDirectory.ts` (new)

Pinia store following the shape of `frontend/src/stores/config.ts:15-29`:

| State | Purpose |
|---|---|
| `catalog` | The loaded catalog, or null before first load |
| `loading`, `error` | Request status for the page's loading and error states |
| `saving`, `saveError` | Separate flags so a failed dialog save does not blank the page |

Behaviour decisions:

- `fetchCatalog(includeDisabled)` **preserves the previous `catalog` on failure** and sets `error`, which is what implements "keep the last good catalog" (spec §Error paths).
- Every mutation action assigns the response catalog directly to `catalog` — no client-side merge, so client and server cannot diverge.
- A 409 sets `saveError` to a reload-required message and triggers `fetchCatalog`, so the administrator sees current data immediately.

### F4 — Recently used composable

**Location:** `frontend/src/platform/composables/useRecentDirectoryLinks.ts` (new — that directory
exists and already holds `createAgentWorkspace.ts`)

| Aspect | Design |
|---|---|
| Storage key | `wwa.serviceDirectory.recent.v1` — this is the **first** browser-storage usage in the frontend (verified: no `localStorage` or `sessionStorage` occurrences exist), so it sets the convention `wwa.<feature>.<purpose>.<version>` |
| Stored shape | `Array<{ linkId: string; openedAt: string }>`, most recent first |
| Cap | 8, matching the prototype's `MAX_RECENT` (`wwa-service-directory.html:1170`) |
| `record(linkId)` | Unshift, de-duplicate by id, truncate to 8, persist |
| `resolved(catalog)` | Map ids to live links, dropping unresolvable ids (SD-FR-34). Titles and URLs are always read from the catalog, never from storage, so renames appear immediately |
| `clear()` | Remove the key and empty the list |
| Failure handling | Every read and write is wrapped; a `SecurityError`, quota error, or malformed JSON degrades to an empty list and never throws into rendering (spec §Error paths) |
| Not seeded | Unlike the prototype (`:1409-1411`), production never fabricates entries |

### F5 — View: `frontend/src/views/ServiceDirectoryView.vue` (new)

Placed in `views/` with the other platform pages (`ConfigAdminView.vue`, `AuditLogView.vue`,
`AccessManagementView.vue`, `TemplateManagementView.vue`) — verified as the established location.

Composition (single view with internal sections rather than six small components; the earlier draft's
six-component split was unnecessary indirection for one page):

| Section | Responsibility |
|---|---|
| Page header | Eyebrow / title / subtitle plus manage toggle, following `ConfigAdminView.vue:548-562` |
| Read-only or manage banner | `.helper-banner` pattern from `ConfigAdminView.vue:564-566` |
| Recently used strip | Chips from F4, with a clear action; hidden when there is nothing resolvable |
| Filter bar | Scope chips, kind chips, search input, using the global `.form-control` classes |
| SDLC stage rail | Rendered from `stage`-type groups of the `stage-strip` scope |
| Catalog body | Scope sections → group blocks → per-kind link grids |
| Dialogs | F6 and F7, mounted conditionally as in `ConfigAdminView.vue:1042-1062` |

Derived state is computed, not stored: `visibleScopes`, `visibleGroups`, `filteredLinks`, and the
per-kind partition. The filter algorithm is specified in the UI section below.

### F6 — Dialog: `frontend/src/components/ServiceDirectoryEntityDialog.vue` (new)

One dialog handling all three entity types, mirroring the prototype's single-modal approach
(`wwa-service-directory.html:1630-1691`) and the prop/emit contract of
`frontend/src/components/ScopeDirectoryDialog.vue:6-16`:

```ts
const props = defineProps<{
  entityType: 'scope' | 'group' | 'link'
  mode: 'create' | 'edit'
  scopes: DirectoryScope[]          // for the parent selector
  entity?: DirectoryScope | DirectoryGroup | DirectoryLink | null
  parentScopeKey?: string
  parentGroupKey?: string
  presetKind?: DirectoryLinkKind    // set when adding from an empty kind slot
  saving?: boolean
  error?: string
}>()
```

Emits `close` and `save`. Field visibility is driven by `entityType`. Client validation mirrors M4
exactly and is never the enforcement point.

### F7 — Delete confirmation: `frontend/src/components/ServiceDirectoryDeleteDialog.vue` (new)

Follows `DeleteTemplateDialog.vue` rather than the prototype's `window.confirm`
(`wwa-service-directory.html:1696`), and states the descendant impact — "This removes 2 groups and 9
links" — which SD-FR-41 requires.

### F8 — Link activation helper

**Location:** co-located with F5 (a module-scoped function in the view's script, not a separate file —
it has exactly one caller)

Single decision point for open behavior, derived from kind rather than a stored flag (SD-FR-26):

1. If the URL's host ends in `.invalid`, do nothing: the card is already rendered as pending and is not activatable, and no Recently used entry is written (SD-FR-27).
2. If `kind === 'workspace'`, `router.push(url)`.
3. Otherwise `window.open(url, '_blank', 'noopener,noreferrer')`.
4. On a successful 2 or 3, call `record(link.id)`.

**Pending-host rule trace:** `https://arcad.example.invalid/` → pending ✓ · `https://invalid-tool.acme.com/`
→ host does not end `.invalid`, normal link ✓ (must not fire) · `/wwa/build-agent` → no host, normal
workspace navigation ✓.

**Derived presentation** (replacing the prototype's stored `tone` and `mark`, data flow §6.1): icon
colour is a fixed map from `kind`, and the badge text is the first two characters of the title
upper-cased, except `repo`, which always shows `GH` — exactly the derivation the prototype performs at
`wwa-service-directory.html:1773-1777`. Trace: `repo` "Build Agent · source" → `GH` ✓ · `tool` "ARCAD"
→ `AR` ✓ · `docs` "X" (single character) → `X` ✓.

### F9 — Route and navigation registration

| File | Change |
|---|---|
| `frontend/src/router/index.ts` | New child route of `/wwa`, inserted after the `access-management` child (currently ending at `:148`): path `service-directory`, name `wwa-service-directory`, lazy component `../views/ServiceDirectoryView.vue`, meta `{ section: 'service-directory', sectionTitle: 'Service Directory' }`. The existing `beforeEach` guard at `:154-174` then covers authentication with no change |
| `frontend/src/config/agentRegistry.ts` | One `platformCapabilities` entry (`:75-108`): `{ key: 'service-directory', label: 'Service Directory', to: '/wwa/service-directory', icon: '🧭' }` — deliberately **no** `accessPermission`, so the entry stays unlocked for every role including `GUEST` (SD-FR-02). This single entry serves both the flyout and Home Shared Controls |

---

## API / Interface Design

Full contract: `docs/05-design/contracts/service-directory-API_IMPLEMENTATION_GUIDE.md`. Summary:

| Operation | Method | Path | Auth |
|---|---|---|---|
| Read catalog | GET | `/api/platform/service-directory` | Any authenticated session (incl. `GUEST`) |
| Create scope | POST | `/api/platform/service-directory/scopes` | `DEVOPS_ADMIN` |
| Update scope | PUT | `/api/platform/service-directory/scopes/{scopeKey}?expectedVersion=` | `DEVOPS_ADMIN` |
| Delete scope | DELETE | `/api/platform/service-directory/scopes/{scopeKey}?expectedVersion=` | `DEVOPS_ADMIN` |
| Create group | POST | `/api/platform/service-directory/scopes/{scopeKey}/groups` | `DEVOPS_ADMIN` |
| Update group | PUT | `/api/platform/service-directory/scopes/{scopeKey}/groups/{groupKey}?expectedVersion=` | `DEVOPS_ADMIN` |
| Delete group | DELETE | `/api/platform/service-directory/scopes/{scopeKey}/groups/{groupKey}?expectedVersion=` | `DEVOPS_ADMIN` |
| Create link | POST | `/api/platform/service-directory/scopes/{scopeKey}/groups/{groupKey}/links` | `DEVOPS_ADMIN` |
| Update link | PUT | `/api/platform/service-directory/links/{linkId}?expectedVersion=` | `DEVOPS_ADMIN` |
| Delete link | DELETE | `/api/platform/service-directory/links/{linkId}?expectedVersion=` | `DEVOPS_ADMIN` |

**Committed interface decisions**

- **Every mutation returns the full catalog** (SD-FR-45). One response shape, one store assignment, no client-side merge logic.
- **A whole-catalog `PUT` is deliberately not offered.** It would make audit detail guesswork ("something in the catalog changed") and would turn every concurrent edit into a lost update, because the client would send a whole stale tree.
- **Link update and delete are addressed by id at the top level**, not nested under scope and group, because a link can move between groups; nesting would make the path lie about the link's new parent. Moving is expressed by an optional `targetScopeKey` / `targetGroupKey` in the update body.
- **A required `expectedVersion` query parameter on every update and delete, and on no create.** This is the client echo of the last-read `version`, and it is what makes a stale page detectable at all — the stored row's `@Version` alone cannot see it, since each request loads the row inside its own transaction. Query parameter rather than a body field or `If-Match`, because DELETE has no body and the codebase emits no ETags; one mechanism for all six conditional writes. Full reasoning in the API guide's Concurrency section.
- **DTOs are Java records with static `from()` factories**, per `DEVELOPMENT_STANDARDS.md:34` and the existing `AgentContributionDashboardStatusDto.java:8-15` style.

---

## Data Design

Full field definitions, the ER view, the persistence-option comparison, the state model, the seed
inventory, and the migration plan live in `docs/04-architecture/service-directory-data-model.md`.
Design-level essentials:

- One new table, `DA_SERVICE_DIRECTORY_CATALOG`, holding exactly one row.
- One new Flyway script; the next free version is **V20** (verified: the chain ends at `V19__add_scope_directory_agent.sql`).
- `docs/sql/ORACLE_CURRENT_SCHEMA.sql` (326 lines, consolidating V2–V19) must be regenerated, because H2 auto-DDL in `local` and `test` never exercises it.
- No foreign keys in either direction; no row in any Configuration Management table.
- Availability is the only state dimension (`enabled ⇄ disabled`, plus hard delete). No soft-delete column.

---

## UI / User Flow Design

### Visual language

The page follows the **glass** styling used by `WwaHomeView.vue` (hero and cards with
`backdrop-filter: blur(22px)` at `:184-188`) rather than the denser table styling of
`ConfigAdminView.vue`, because the prototype is a card catalog, not a data grid. All colours, radii, and
shadows come from the existing tokens at `frontend/src/assets/main.css:7-28`; no new palette is
introduced (SD-NFR-10). Dialogs use the global `.modal-overlay` / `.modal` classes at
`main.css:202-260`.

### Copy

| Element | Text |
|---|---|
| Eyebrow | WWA-Atlas Hub Shared Capability |
| Title | Service Directory |
| Subtitle | Jump to SDLC tools, shared platforms, and external systems. |
| Manage toggle (admin) | Manage catalog / Managing… |
| Non-admin banner | Read-only view. Only **DEVOPS_ADMIN** can change the directory. |
| Empty catalog (reader) | The directory is empty. |
| Empty catalog (admin) | The directory is empty. Add a scope to get started. |
| No filter matches | No links match your filters. → **Clear filters** |
| Recently used empty | No recent links yet. Open any card to start your personal list. |
| Pending URL pill | URL pending |
| Delete confirmation | Delete "{title}"? This removes {n} groups and {m} links. |
| Conflict error | The directory changed in another session. Reloaded — please reapply your edit. |

### Filter algorithm (single specification, mirrored by tests)

```
0. the server already applied enabled-filtering and the full SD-FR-10 ordering at
   every level, so the client preserves the received order and never re-sorts
1. scopes    := catalog.scopes
2. if activeScope != 'all'  → keep only that scope
3. if activeStage != 'all'  → keep only the stage-strip scope (SD-FR-70 guarantees
                              there is at most one), and within it only the group
                              whose key == activeStage
                              (non-stage-strip scopes are dropped entirely)
                              — comparing against group.key is correct only because
                                SD-FR-51 forces key == stageKey for stage groups;
                                without that rule this line would have to pick one
                                of two fields and could disagree with the seed
4. for each surviving group, links :=
       links matching activeKind (or all when 'all')
       AND, when search text is non-empty, matching case-insensitively against:
         link.title, link.description, link.kindLabel, link.kind,
         group.title, group.description, group.agentName, scope.title
       — note: link.url is deliberately NOT searched
       — consequence worth knowing before writing tests: because kindLabel IS
         searched, the text "github" still matches every repo link through the
         label "GitHub / source". A test proving URLs are unsearched must use a
         fragment that appears only in a URL and in no display text —
         for example a path segment or a host label
5. drop groups with zero links, unless manage mode is on (then keep them
   so their add affordances remain reachable)
6. if nothing survives → render the empty state with Clear filters
```

Interaction rules from the prototype, preserved exactly:

| Rule | Prototype anchor |
|---|---|
| Scope and kind chips are single-select | `:1461-1466`, `:1502` |
| Selecting a non-SDLC scope clears the stage focus | `:1463` |
| Re-selecting the focused stage clears the focus | `:1485` |
| Selecting a stage also sets the active scope to SDLC and scrolls to the group | `:1486-1489` |
| The stage rail is de-emphasised when another scope is active | `:1475` |
| Kind sub-heading order: Docs / Confluence → Tools → WWA workspaces → GitHub / source | `:1541-1544` |
| In manage mode, empty groups and empty kind slots stay visible with add affordances | `:1534-1547`, `:1591` |
| `/` focuses search; `Escape` closes the dialog | `:1811-1817` |

The `/` shortcut must not fire while focus is in an input, textarea, or select, or while a dialog is
open. Trace: typing "/" in search → inserts the character ✓ · pressing "/" while browsing → focuses
search ✓ · pressing "/" with the dialog open → ignored ✓.

### Manage mode

Rendered only when `userStore.isDevOpsAdmin` (`frontend/src/stores/user.ts:25`) — the same gate
`ConfigAdminView.vue:80` uses. It is presentation only; M6 is the authority. The prototype's topbar role
`<select>` (`:1371-1376`) is not reproduced in any form.

Disabling a `system` scope surfaces an inline warning that the section will disappear for all users
(SD-R-08).

### States

| State | Rendering |
|---|---|
| Loading (first load) | `.loading-state` spinner, as `ConfigAdminView.vue:814` |
| Error (first load) | `.alert alert-error` with retry, as `ConfigAdminView.vue:602-604` |
| Error (refresh) | Same banner, previous catalog still rendered underneath |
| Empty catalog | Message, plus an add-scope call to action for administrators |
| No filter matches | Message with a single Clear filters action |
| Disabled entry (admin view) | Rendered dimmed with a "Disabled" pill |
| Pending URL | Card rendered with a "URL pending" pill, not activatable, excluded from Recently used |

Feedback uses inline `.alert` banners, not toasts — verified that no toast component or library exists
in this frontend.

---

## Workflow / Execution Design

### Page load

1. The route guard (`router/index.ts:154-174`) ensures a session.
2. The view calls `fetchCatalog(includeDisabled = userStore.isDevOpsAdmin)`.
3. The backend loads or seeds, projects by role, and returns one payload.
4. The view resolves Recently used ids against the payload and computes derived state.
5. No further requests occur while the user filters, searches, or focuses stages.

### Mutation

1. The administrator submits a dialog; the store sets `saving`.
2. The API module calls the endpoint. For an update or a delete it appends
   `?expectedVersion=<catalog.version currently in the store>`; a create sends none.
3. M6 checks the role, M3 asserts the version, M4 validates, M3 mutates and saves, M7 audits.
4. The response catalog replaces store state — including its new `version`, which the next
   mutation will echo — and the dialog closes.
5. On failure: 403 / 400 / 404 / 409 map to `saveError`; a 409 additionally re-fetches.

The store's `version` is therefore the single source of the precondition, and it is only ever written
from a server response. Nothing else may increment or guess it: a client-side `version + 1` after a
successful save would appear to work and would silently disable stale detection the moment a response
ordering differed from what the client assumed.

### Ordering guarantee

Authorization → validation → persistence → audit. No audit entry can describe an unpersisted change,
and no persisted change is lost to an audit failure.

### Failure handling

There is no retry, backoff, or compensation anywhere in this design, because every operation is a single
synchronous transaction with no external call. A failed request is simply reported.

---

## Integration Design

**No external integration exists in this slice.** The Hub stores and renders URLs; it never calls the
linked systems. Consequences, stated so no one designs them in later by accident: no credentials, no
timeouts, no retry policy, no circuit breaker, no outbound allow-list, and no availability coupling
between the Hub and any linked system.

Internal touch points only:

| Touch point | Direction | Contract |
|---|---|---|
| Auth chain → controller | Inbound | `UserContext` via `@AuthenticationPrincipal`; guest writes already blocked by `GuestReadOnlyFilter.java:29-63` |
| Service → audit | Outbound, in-process | `AuditLoggerService.log(user, actionType, context)` (`:152-154`) |
| Service → database | Outbound | JPA through M2 |
| Agent Contribute Dashboard | None in MVP | Overlapping SDLC guideline / feedback content is aligned by ownership, not code (SD-FR-66). Verified that its `resourceLinks` are frontend-only static JSON and its backend persists only stage statuses (`AgentContributionDashboardController.java:13-33`), so no shared source exists to integrate with today |

---

## Security / Audit / Reliability Design

### Access control

| Path | Enforcement |
|---|---|
| Read | Authenticated session required by the existing chain. `GUEST` allowed (spec default, SD-OQ-01) |
| `includeDisabled=true` | Honoured only for `DEVOPS_ADMIN`; silently ignored otherwise |
| All mutations | `validateAdmin` → `ForbiddenAppException` → 403 via `GlobalExceptionHandler.java:24-28` |
| Guest mutations | Blocked upstream by `GuestReadOnlyFilter` before the controller runs; the slice adds no exemption to its `LOGOUT_PATH` allowance |
| Client role claims | Never trusted; the role is read from the server-side `UserContext` only |

No new role, permission, or scope type is introduced. None of the human-in-the-loop decision classes in
`CLAUDE.md` are touched: this capability manages navigation links, not production data, access grants,
audit configuration, or task execution.

### Output safety

- URLs are validated on write (M4) and rendered through Vue's standard binding — the view constructs no raw HTML from catalog content, unlike the prototype's `innerHTML` rendering.
- External kinds open with `noopener,noreferrer`.
- Error responses use the existing `ErrorResponseDto(code, message, details)` envelope and must not echo internal paths or stack traces.

### Audit

Per M7: one entry per successful mutation, `ActorKind.HUMAN` (set at `AuditLoggerService.java:125`),
dedicated action types, identifying context map, one entry with descendant counts for a cascade delete.
No entry for reads, filtering, Recently used, or failed mutations.

### Reliability

- Single-transaction mutations: no partial state.
- Two-layer conflict detection → 409 in both cases; nothing merged, nothing lost. The `expectedVersion` precondition catches a stale page (SD-FR-44); the `@Version` column catches a same-instant overlap (SD-FR-68).
- Audit isolated in `REQUIRES_NEW`: cannot roll back a catalog change.
- Frontend keeps the last good catalog on a failed refresh.
- Browser-storage failures degrade Recently used to empty without affecting the catalog.

---

## Validation and Error Handling

| Condition | Where detected | Response | Audit written? |
|---|---|---|---|
| No session | Auth chain | 401 `UNAUTHORIZED` | No |
| Guest attempts any mutation | `GuestReadOnlyFilter` | 403 with the guest read-only message | No |
| Authenticated non-admin mutation | M6 `validateAdmin` | 403 `FORBIDDEN` | No |
| Malformed body / missing required field | Bean validation | 400 with field details | No |
| Key pattern, duplicate key, length, range, kind, stage-key violation | M4 | 400 `VALIDATION_ERROR`, message names the field | No |
| Submitted `key` differs from the path key on an update | M4 | 400 `VALIDATION_ERROR` — keys are immutable (SD-FR-43) | No |
| Second scope with `layout = stage-strip` | M4 | 400 `VALIDATION_ERROR` (SD-FR-70) | No |
| Unsafe or wrong-shaped URL | M4 | 400 `VALIDATION_ERROR` | No |
| Delete of a `system` scope | M4 | 400 `VALIDATION_ERROR` explaining that system scopes may be disabled but not deleted | No |
| Unknown scope key, group key, or link id | M3 | 404 `NOT_FOUND` | No |
| `expectedVersion` missing on an update or delete | Spring MVC parameter binding | 400 | No |
| `expectedVersion` present but superseded | M3 precondition → `OptimisticLockConflictException` | 409 `OPTIMISTIC_LOCK_CONFLICT` | No |
| Two mutations overlapping in flight | JPA `@Version` | 409 `OPTIMISTIC_LOCK_CONFLICT` | No |
| Corrupt stored payload | Converter | 500 (fails loudly rather than serving an empty catalog) | No |
| Audit write failure after a valid mutation | Audit capability | 2xx — the mutation stands; failure logged server-side | No (that is the failure) |

All exception types are existing ones from `src/main/java/com/wwa/agenthub/errors/`
(`ValidationAppException`, `ForbiddenAppException`, `NotFoundAppException`); no new exception class is
introduced, per `DEVELOPMENT_STANDARDS.md:33`.

---

## Testing Considerations

Backend tests follow the verified platform convention — `@SpringBootTest`, `@AutoConfigureMockMvc`,
`@ActiveProfiles("test")`, `@Transactional`, with authentication supplied as `X-User-Id` /
`X-User-Role` headers (`AccessGrantControllerTest.java:28-31`, `:49-51`, `:58-60`). No `@WithMockUser`
and no custom security-context builder — that pattern does not exist here. New tests belong in
`src/test/java/com/wwa/agenthub/web/`, where the other platform controller tests live.

| Area | Cases to cover |
|---|---|
| Read | Seeds on an empty store and returns the three system scopes; second read does not re-seed; reader payload excludes disabled entries; `includeDisabled=true` returns them for `DEVOPS_ADMIN` and is ignored for a `DEVELOPER`; guest read succeeds |
| Authorization | Every mutation returns 403 for `DEVELOPER`; each succeeds for `DEVOPS_ADMIN`; guest mutation is blocked |
| Validation | Duplicate scope key; duplicate group key within a scope but allowed across scopes; bad key pattern; over-length title; `sortOrder` out of range; each URL trace row in M4, including the `.invalid` row that must be accepted |
| Structure | Cascade delete removes descendants; `system` scope delete rejected while disable succeeds; system scope title edit succeeds while a key edit is rejected; link move between groups; `sortOrder` default lands the new entry last; a second `stage-strip` scope is rejected; a stage group with `key != stageKey` is rejected |
| Concurrency | Read the catalog to capture version *v*; apply one mutation so the stored version moves past *v*; then (a) an update sent with `expectedVersion = v` yields 409 and leaves the catalog byte-identical, (b) a delete sent with `expectedVersion = v` likewise, (c) an update sent with the current version succeeds, (d) a **create sent while holding stale state still succeeds**, since creates carry no version (SD-FR-67), and (e) an update sent with the parameter omitted yields 400. Case (d) is the one that proves the precondition is scoped rather than blanket — a suite that only asserts conflicts would pass even if creates were wrongly versioned |
| Audit | One entry per successful mutation with the expected action type and context keys; a link move additionally carries `from_group_key` / `to_group_key`, and an in-place link edit carries neither; none for a rejected mutation; none for a read |
| Boundary | After exercising every mutation, the Configuration Management tables contain no new rows and no new `ConfigKey` exists |
| Frontend | `cd frontend && npm run build` (`vue-tsc && vite build`) is the type gate. Note: there is no component-test framework here — verified that `frontend/package.json:6-11` defines `"test": "node --test tests/**/*.mjs"` with no vitest or Playwright — so filter, Recently used, and pending-URL behavior are verified by the documented manual walkthrough, and any pure helper extracted for testing would use that Node test runner |

Test expectations above intentionally mirror the rules in M4 and the filter algorithm one-for-one; if a
rule changes, both move together.

---

## Risks / Design Tradeoffs

| # | Tradeoff | Position taken |
|---|---|---|
| 1 | One document versus normalised tables | Document. The catalog is always read whole; hierarchical delete becomes trivial; one migration instead of three tables. Cost is document-level conflict (see 2). Recorded in `ADR-0010` |
| 2 | The `expectedVersion` precondition is document-scoped, so it rejects an update whose target nobody else touched | Accepted for MVP. The version guards the whole catalog because the whole catalog is one row, so an unrelated change does invalidate a held version. Writes are rare (a handful per quarter by a few admins), and the client re-fetches automatically so the admin re-applies one edit against fresh data. Per-entity versioning would mean per-entity rows — that is tradeoff 1, already decided the other way |
| 2b | Requiring the version on deletes means a destructive action can be blocked by an unrelated edit | Kept deliberately. The delete confirmation states "this removes N groups and M links"; if the catalog moved, those numbers may no longer be true, so the admin should re-read the impact before confirming. Here the strictness is the feature |
| 3 | Granular endpoints over a document store are slightly more code than one whole-catalog `PUT` | Worth it: precise audit detail and no lost updates. A whole-catalog `PUT` would make SD-US-07's "which entity changed?" unanswerable |
| 4 | A new typed JSON converter is needed because the existing one is `Map`-only | Small and follows the documented pattern exactly; the alternative — storing the tree as an untyped `Map` — would move field validation from the type system into runtime code |
| 5 | Link addressing by id while scopes and groups use keys | Deliberate: links have no key, can move between groups, and their ids are what the browser stores. Documented in the API guide so it is not read as an inconsistency |
| 6 | Lazy seed on first read rather than a startup runner | Keeps startup free of data work and lets any empty environment self-heal. The empty-store check and insert share one transaction, so a concurrent first request cannot double-insert |
| 7 | Single view file rather than six small components | One page with one filter algorithm; splitting it would spread that algorithm across props and emits. The earlier draft's six-component split was indirection without a second consumer |
| 8 | Presentation values (`tone`, `mark`, open behavior) derived rather than stored | Storage cannot then contradict behavior; and it matches how the prototype itself derives them |
| 9 | Identifying detail in the audit context map rather than the entity's target columns | Those columns are written by nothing and exposed by nothing, so using them would produce invisible audit records. Fixing them properly is an audit-capability change, not this slice |
| 10 | No component-test framework for the frontend | Type checking plus a documented manual walkthrough is the honest current ceiling; claiming automated UI coverage would be false |

---

## Open Questions

1. **SD-OQ-01 — Guest read access.** This design allows it and relies on the existing guest write block. Reversal before implementation costs one route guard plus one read-side role check; reversal after release is a visible behavior change.
2. **SD-OQ-02 — Production ARCAD and GitHub Enterprise URLs.** Until supplied, the seed carries reserved `.invalid` placeholders that render as "URL pending". The slice is not release-ready until they are replaced.
3. **SD-OQ-03 — Shared source with the Agent Contribute Dashboard.** Verified today that no shared source exists (its links are static frontend JSON), so MVP alignment is manual. If a shared source is wanted, it is a separate slice with its own ownership decision.
4. **SD-OQ-04 — ADR acceptance.** This design assumes `ADR-0010` is accepted before implementation, since the store boundary is the reason the module exists.
5. **Documentation drift found during grounding.** `CLAUDE.md` and `AGENTS.md` state the Java base package as `com.wwa.deploymentagent`, but the code uses `com.wwa.agenthub`. (`PROJECT_RULES.md` was checked and does not carry the stale package.) This design uses the real package. Correcting those governance files is a separate, user-approved edit — flagged here and recorded in `docs/00-context/lessons-learned.md`.
