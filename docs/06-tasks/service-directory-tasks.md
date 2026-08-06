# Implementation Task Breakdown: Resource Center

> **Slice:** `service-directory`
> **Status:** Regenerated via `design-to-tasks`; base slice implemented. **Amended 2026-07-25** — product renamed to **Resource Center**; W9 `iconKey`; **W10** code rename to match SDD. Do not start W9/W10 until amendments are accepted.
> **Last updated:** 2026-07-25
> **Product name:** Resource Center (formerly Service Directory)
> **Slice id / doc filenames:** `service-directory` (unchanged)
> **Source design:** `docs/05-design/service-directory-design.md`
> **Behavior source of truth:** `docs/03-spec/service-directory-spec.md`
> **API contract:** `docs/05-design/contracts/service-directory-API_IMPLEMENTATION_GUIDE.md`

---

## Overview

**Implementation summary:** Add one Platform-shared capability to the WWA Agent Hub: a
`/wwa/resource-center` page backed by a new single-table catalog store, a Platform REST API with
`DEVOPS_ADMIN`-only mutations, audited changes, and a seeded catalog covering the seven SDLC stages plus
Common and External destinations.

**Delivery objective:** Any authenticated user finds the right destination in seconds; a `DEVOPS_ADMIN`
keeps the catalog current without a code release; every change is auditable; no catalog data touches
Configuration Management.

**Planning assumptions:**

1. The persistence decision is closed — one JSON document row (SD-T00, see below).
2. Guest read access is a committed default awaiting ratification (SD-T01).
3. Production ARCAD / GitHub Enterprise URLs are still missing; the seed ships reserved `.invalid` placeholders (SD-T02).
4. The Java base package is `com.wwa.agenthub` — **not** `com.wwa.deploymentagent` as `CLAUDE.md` and `AGENTS.md` still claim.
5. The frontend has no component-test framework, so UI behavior is verified by type checking plus a documented manual walkthrough.
6. Next free Flyway version is **V20**.

---

## Source Design

**System name:** Resource Center (WWA Agent Hub Platform capability).

**Design scope summary:** Nine backend units (entity, typed JSON converter, repository, service,
validator, seed loader, controller, DTOs, audit constants), one migration plus greenfield schema
regeneration, and nine frontend units (types, API module, store, view, entity dialog, delete dialog,
Recently used composable, route, navigation registry entry). No external integration, no workflow
engine, no new role or permission.

---

## Workstreams

| Workstream | Contents | Can start when |
|---|---|---|
| **W0 · Decisions** | Close or ratify SD-T00 … SD-T03 | Immediately (SD-T00 and SD-T03 already prepared) |
| **W1 · Persistence** | Entity, converter, repository, migration, schema regeneration | W0 ratified |
| **W2 · Domain** | Validator, service, seed loader and seed resource | W1 entity shape exists |
| **W3 · API** | DTOs, controller, audit constants and emission | W2 service signatures exist |
| **W4 · Backend tests** | Controller/contract tests per the API guide checklist | W3 endpoints exist |
| **W5 · Frontend foundation** | Types, API module, store, route, registry entry | W3 contract frozen (types can start from the contract before the backend is done) |
| **W6 · Frontend page** | View, filters, stage rail, catalog body, Recently used composable, link activation | W5 |
| **W7 · Frontend admin** | Manage mode, entity dialog, delete dialog, error and conflict handling | W6 |
| **W8 · Hardening** | Content alignment, CHANGELOG, verification run, handoff and traceability updates | W4 and W7 |
| **W9 · Per-link icons** | Optional `iconKey` whitelist on links: enum + validation + DTO + seed keys + frontend assets/map + admin picker + contract tests (SD-FR-71) | Base slice implemented; prefer after W10 so paths are stable |
| **W10 · Product rename** | Align code with **Resource Center**: route, registry, API path, Java/TS symbols, seed resource path, audit action names; keep table `DA_SERVICE_DIRECTORY_CATALOG` | SDD rename amendment accepted |

**Recommended sequencing:** W0 → W1 → W2 → W3 → (W4 ∥ W5) → W6 → W7 → W8 → **W10** (rename) → **W9** (icons).

**Genuine parallelism:**

- W5 (frontend types and API module) can be written against the frozen API guide while W2/W3 are in progress.
- W4 (backend tests) runs alongside W5/W6 once the endpoints exist.
- SD-T02 (collecting URLs) is a people task that can run in the background from day one.
- W10 frontend (SD-T90) and backend (SD-T91) can proceed in parallel; SD-T92 closes the rename.

---

## Task Breakdown By Domain

### Decisions (W0)

Close the persistence choice, ratify the guest posture, collect the missing URLs, and land the store
boundary decision record.

### Persistence / Data (W1)

Catalog entity with optimistic version, a typed JSON CLOB converter, a singleton-aware repository, the
`V20` migration, and regeneration of the greenfield Oracle schema.

### Backend / Domain (W2)

Pure validator covering every structural and URL rule, the catalog service owning all mutations and
cascade behavior, and lazy one-time seed provisioning from a packaged resource.

### Backend / API (W3)

Request and response DTO records, the Platform controller with imperative role checks, and the two new
audit action constants with context-map emission.

### Testing (W4)

Contract tests for read projection, authorization, validation, structure, concurrency, audit side
effects, and the Configuration Management boundary.

### Frontend / Foundation (W5)

TypeScript unions kept in lockstep with backend enums, the platform API module, the Pinia store, the
route, and the single navigation registry entry that serves both flyout and Home.

### Frontend / Page (W6)

The view with its filter algorithm, stage rail, catalog body, Recently used strip, pending-URL handling,
and the link activation helper.

### Frontend / Admin (W7)

Manage mode gating, the shared entity dialog, the impact-stating delete dialog, and conflict recovery.

### Documentation / Hardening (W8)

Content alignment with the Agent Contribute Dashboard, CHANGELOG, full verification run, traceability
and handoff updates.

---

## Task Details

### SD-T00: Confirm catalog persistence approach

- **Objective**: Lock the storage shape before any code is written.
- **Scope**: Compare a single JSON document row against normalised `SD_SCOPE` / `SD_GROUP` / `SD_LINK` tables against the slice's actual access pattern, and record the outcome.
- **Status**: **Closed by this regeneration.** Decision: **one JSON document row** (`DA_SERVICE_DIRECTORY_CATALOG`), rationale and reversal triggers in `docs/04-architecture/service-directory-data-model.md` §2, consequences in `ADR-0010`.
- **Dependencies**: None.
- **Owner type**: platform
- **Priority**: Must
- **Notes**: Remaining action is user ratification during SDD acceptance, not further analysis. Reversal after implementation is contained because one module owns all catalog persistence.

### SD-T01: Ratify guest visibility

- **Objective**: Decide whether a `GUEST` session may read the Resource Center.
- **Scope**: Choose between (a) allow read — the spec's committed default, relying on the existing guest write block; or (b) redirect guests away from the route and reject guest reads server-side.
- **Status**: **Default committed, awaiting user decision** (spec SD-OQ-01, SD-FR-65).
- **Dependencies**: None.
- **Owner type**: platform
- **Priority**: Must
- **Notes**: If (b) is chosen, the change is a route guard in `frontend/src/router/index.ts` plus one role check on the read path — roughly an hour before implementation, versus a visible behavior change after release. Decide during acceptance.

### SD-T02: Collect production ARCAD and GitHub Enterprise URLs

- **Objective**: Replace seed placeholders with real destinations.
- **Scope**: Obtain the production URLs from platform operations, update the seed resource, and confirm the "URL pending" indicator disappears for those two links. Also review whether any seeded stage guideline / feedback Confluence URLs are known.
- **Status**: **Open** (spec SD-OQ-02).
- **Dependencies**: None to start; blocks release readiness, not development.
- **Owner type**: devops
- **Priority**: Must
- **Notes**: Until closed, the seed keeps reserved `.invalid` hosts, which render as non-navigable "URL pending" cards. Do not guess internal hostnames.

### SD-T03: Land the store boundary decision record

- **Objective**: Capture the "Resource Center owns its own store, separate from Configuration Management" decision durably rather than in chat.
- **Scope**: `docs/00-context/decisions/ADR-0010-service-directory-owns-its-catalog-store.md` plus an entry in the ADR index.
- **Status**: **Prepared by this regeneration as `Proposed`.** Remaining action: user accepts it and the status flips to `Accepted`.
- **Dependencies**: SD-T00 (the ADR records the chosen storage shape).
- **Owner type**: platform
- **Priority**: Must
- **Notes**: The SDD profile requires an ADR for data-ownership and platform-boundary decisions, which is exactly what this is.

### SD-T10: Create the catalog entity and typed JSON converter

- **Objective**: Persist the catalog document portably on Oracle and H2.
- **Scope**: `ResourceCenterCatalogEntity` in `domain/resourcecenter/` (String UUID id via `@PrePersist`, `@Version`, creation and update timestamps, `updated_by`, `payload` CLOB); the `DirectoryScope` / `DirectoryGroup` / `DirectoryLink` value records; `DirectoryScopeListJsonAttributeConverter` in `util/` following the pattern documented at `util/JsonAttributeConverter.java:18-26`. Excludes: any child table.
- **Dependencies**: SD-T00
- **Owner type**: backend
- **Priority**: Must
- **Notes**: The converter must throw on a deserialisation failure rather than returning an empty list — an empty catalog looks repairable and invites overwriting real content.

### SD-T11: Add the four catalog enums and the matching TypeScript unions

- **Objective**: Type the catalog vocabulary on both sides in one change.
- **Scope**: `DirectoryLinkKind`, `DirectoryScopeLayout`, `DirectoryGroupType`, `SdlcStageKey` in `contracts/enums/`, plus the matching unions and interfaces in `frontend/src/types/index.ts`. *(W9 adds `DirectoryLinkIconKey` via SD-T80 — do not fold that into a re-run of this closed task.)*
- **Dependencies**: None
- **Owner type**: backend
- **Priority**: Must
- **Notes**: `DEVELOPMENT_STANDARDS.md:44` makes the TypeScript half mandatory in the same change, because `vue-tsc` cannot catch drift in `.includes()` checks. Verified that the two new `AuditActionType` constants (SD-T31) are exempt: `frontend/src/types/index.ts:250` types `actionType` as plain `string`.

### SD-T12: Add the repository with a deterministic singleton accessor

- **Objective**: Read the one catalog row reliably.
- **Scope**: `ResourceCenterCatalogRepository extends JpaRepository<…, String>` with `findFirstByOrderByIdAsc()`. No other query methods.
- **Dependencies**: SD-T10
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Deterministic ordering means that if a duplicate row ever appeared, reads would still be stable rather than alternating.

### SD-T13: Add the `V20` migration

- **Objective**: Create `DA_SERVICE_DIRECTORY_CATALOG` in Oracle.
- **Scope**: `src/main/resources/db/migration/V20__add_service_directory_catalog.sql` with `id`, `payload` CLOB, `version`, `updated_by`, `updated_at`, `created_at`, and a primary key. No foreign keys, no extra indexes.
- **Dependencies**: SD-T10
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Verified that the chain currently ends at `V19__add_scope_directory_agent.sql`, so `V20` is free. No seed rows in SQL — seeding is application code (SD-T22).

### SD-T14: Regenerate the greenfield Oracle schema

- **Objective**: Keep the Oracle end-state document truthful.
- **Scope**: Add the new table to `docs/sql/ORACLE_CURRENT_SCHEMA.sql` (currently 326 lines, consolidating V2–V19) and update its header note.
- **Dependencies**: SD-T13
- **Owner type**: backend
- **Priority**: Must
- **Notes**: `DEVELOPMENT_STANDARDS.md:57` requires this after any `V*` script. Local and test profiles use H2 auto-DDL and never exercise this file, so staleness stays invisible until an Oracle deployment fails.

### SD-T20: Implement the validator

- **Objective**: One authoritative place for every structural and URL rule.
- **Scope**: `ResourceCenterValidator` in `domain/resourcecenter/` implementing every row of the design's validation table — key normalisation and pattern, scope-key global uniqueness, group-key uniqueness within scope, **key immutability on update**, title and description lengths, `sortOrder` range, `kind` membership, `stageKey` / `stageOrder` presence rules, **`key` equal to `stageKey` for stage groups (SD-FR-51)**, **at most one `stage-strip` scope (SD-FR-70)**, per-kind URL shape, and the scheme denial list. Throws `ValidationAppException` naming the offending field. Excludes persistence: the validator takes no repository.
- **Dependencies**: SD-T11
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Three rules are easy to get backwards. (1) A host ending in `.invalid` must **pass** — it is a rendering concern, not an error; rejecting it makes the seed unstorable. (2) The `workspace` URL pattern is exactly `^/wwa/[A-Za-z0-9._~\-/]*$`, so `/wwa/audit-log?tab=all` is **rejected**; do not loosen it to a `startsWith("/wwa/")` check. (3) Key immutability applies to every scope and group, not only `system` ones — reject a submitted key that differs from the path key rather than ignoring it, so a client cannot believe a rename succeeded.

### SD-T21: Implement the catalog service

- **Objective**: Own all catalog reads, mutations, and cascade behavior.
- **Scope**: `ResourceCenterService` with the ten methods listed in the design (read plus create/update/delete for scope, group, link), role-aware projection, `sortOrder` defaulting to highest sibling + 10 clamped to 9999, cascade removal for scope and group deletes, system-scope delete rejection, link addressing by id including moves, and the **`expectedVersion` precondition on the six update and delete methods**. Excludes: authorization (controller) and audit emission wiring (SD-T31).
- **Dependencies**: SD-T12, SD-T20
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Mutations are read-modify-write inside one transaction. **The concurrency design has two layers and skipping the first one silently loses data.** Each update and delete takes `long expectedVersion` and compares it to the loaded row's `version` *before* validating or mutating, throwing the existing `OptimisticLockConflictException` (`errors/OptimisticLockConflictException.java:4-9`, an `AppException` at 409) on mismatch. Relying on `@Version` alone does not work here: every request loads the row inside its own transaction, so "admin A loads → admin B saves → admin A saves later" produces no lock failure at all and overwrites B silently. `@Version` stays and still covers the in-flight race through `web/exception/GlobalExceptionHandler.java:31-36`. The three **create** methods take no version and must not gain one (SD-FR-67) — a create only appends and cannot overwrite anyone. Link lookup is a linear document walk — no index, no cache.

### SD-T22: Implement seed provisioning and the seed resource

- **Objective**: Make an empty environment useful on first page view.
- **Scope**: `ResourceCenterSeedLoader` plus `src/main/resources/resource-center/seed-catalog.json` containing the inventory in the data model §8 — seven SDLC stage groups, Common (Platform and Engineering tools), External. Seeding is lazy (triggered from read when the store is empty), validated by SD-T20, idempotent, and never overwrites an existing row.
- **Dependencies**: SD-T20, SD-T21
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Do not ship the prototype's example `security` scope, its fabricated recent entries, or `#` hrefs. Unknown URLs use reserved `.invalid` hosts until SD-T02 closes. The empty-store check and the insert must share one transaction so two concurrent first requests cannot double-insert.

### SD-T30: Implement DTOs and the Platform controller

- **Objective**: Expose the contract exactly as specified.
- **Scope**: Request records for scope, group, and link upserts and the `ResourceCenterCatalogDto` response (Java records with static `from()` factories), plus `ResourceCenterController` in `platform/web/shared/` implementing all ten endpoints from the API guide, with a private `validateAdmin` helper throwing `ForbiddenAppException`, `includeDisabled` honoured only for `DEVOPS_ADMIN`, and `@RequestParam long expectedVersion` on the six PUT and DELETE methods.
- **Dependencies**: SD-T21
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Use the imperative role-check style of `ConfigurationController.java:39-48` (same shape as the private helper at `DeploymentReleaseFlowController.java:277-281`); `@PreAuthorize` exists nowhere in this codebase. No agent parameter. `includeDisabled` from a non-admin is ignored, not rejected. Declare `expectedVersion` as a primitive `long` with **no `defaultValue`**, so a missing parameter is a 400 from Spring MVC before the handler body runs — a default would turn a client bug into a silent overwrite. The three POST creates take no such parameter.

### SD-T31: Add audit action types and emit audit entries

- **Objective**: Make every catalog change reviewable and distinguishable from Configuration Management changes.
- **Scope**: Append `resource_center_update` and `resource_center_delete` to `contracts/enums/AuditActionType.java`; call `AuditLoggerService.log(user, actionType, context)` once per successful mutation with the context keys in the design's M7 table, including removed-descendant counts for cascade deletes and, **for a link update that changed parent, all four of `from_scope_key`, `from_group_key`, `to_scope_key`, `to_group_key`** — omitted entirely for an in-place edit, so their presence is itself the signal that a move occurred.
- **Dependencies**: SD-T21
- **Owner type**: backend
- **Priority**: Must
- **Notes**: No migration needed — the column is `@Enumerated(STRING)` at `domain/audit/AuditLogEntry.java:81-83`. Do **not** write `target_type` / `target_id` on the entity: verified that nothing writes them and `AuditLogEntryDto` does not expose them, so auditors could not see them. Emit after the mutation is applied; the logger's `REQUIRES_NEW` write means an audit failure cannot roll back the change.

### SD-T40: Backend contract tests

- **Objective**: Prove every rule in the contract, not just the happy path.
- **Scope**: `ResourceCenterControllerTest` in `src/test/java/com/wwa/agenthub/web/` covering all rows of the API guide's contract test checklist (base 16; W9 adds row 17 via SD-T83): seed-once, read projection by role, guest read, 403 on every mutation for a non-admin, duplicate keys, key immutability, every URL rule row, stage-field rules including `key == stageKey`, the single-`stage-strip` rule, system-scope protection, cascade counts in audit, link move validation and id stability, the full stale-write matrix, audit presence and absence including the link-move keys, and the Configuration Management boundary assertion.
- **Dependencies**: SD-T30, SD-T31
- **Owner type**: QA
- **Priority**: Must
- **Notes**: Use the verified convention — `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@Transactional`, `X-User-Id` / `X-User-Role` headers (`AccessGrantControllerTest.java:28-31`, `:49-51`, `:58-60`). `@WithMockUser` is not used in this repository. Checklist row 12 is the one to write carefully: as well as asserting 409 for a stale update and a stale delete, assert that a **create** succeeds while holding a stale version and that an update with the parameter **omitted** returns 400. A suite that only checks the two conflict cases still passes against an implementation that wrongly versions creates or silently defaults the parameter — which is exactly the bug this rule exists to prevent. Verification: `mvn test -Dtest=ResourceCenterControllerTest`.

### SD-T50: Add the platform API module and store

- **Objective**: One typed client and one state holder for the page.
- **Scope**: `frontend/src/api/resourceCenter.ts` (one function per endpoint, using the shared `platformClient`; the six update and delete functions send `expectedVersion` as a query parameter, the three creates do not) and `frontend/src/stores/resourceCenter.ts` (catalog, loading, error, saving, saveError; every mutation assigns the response catalog wholesale so `version` always comes from the server; a failed refresh preserves the previous catalog; a 409 sets a reload message and re-fetches).
- **Dependencies**: SD-T11 (types); contract frozen — can begin before SD-T30 lands.
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Add no bespoke 401 or error handling — `frontend/src/api/platformClient.ts:21-42` already redirects on 401 and normalises error messages. The store's `version` must only ever be written from a server response. Never compute `version + 1` locally after a save: it would appear to work and would quietly disable stale detection.

### SD-T51: Register the route and the navigation entry

- **Objective**: Make the page reachable from both surfaces with one registration.
- **Scope**: A `resource-center` child route under `/wwa` in `frontend/src/router/index.ts` (after the `access-management` child, currently ending at `:148`) with `section` / `sectionTitle` meta; one `platformCapabilities` entry in `frontend/src/config/agentRegistry.ts:75-108` **without** `accessPermission`.
- **Dependencies**: None
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Verified that the single registry feeds both the flyout (`WorkspaceLayout.vue:230-247`) and Home Shared Controls (`WwaHomeView.vue:118-131`), so no second registration exists to add. Omitting `accessPermission` is deliberate — the entry must stay unlocked for every role.

### SD-T52: Implement the Recently used composable

- **Objective**: A durable per-browser shortcut list that cannot break the page.
- **Scope**: `frontend/src/platform/composables/useRecentResourceCenterLinks.ts` with `record`, `resolved`, and `clear`; key `wwa.resourceCenter.recent.v1`; shape `Array<{ linkId, openedAt }>`; cap 8; de-duplicate on re-open; drop ids that no longer resolve; wrap every storage access so a quota, security, or parse failure degrades to an empty list.
- **Dependencies**: SD-T11
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: This is the **first** browser-storage usage in this frontend (verified: no `localStorage` or `sessionStorage` occurrences exist), so it establishes the `wwa.<feature>.<purpose>.<version>` convention. Store only ids and timestamps — titles and URLs resolve from the catalog so renames show immediately. Never fabricate entries.

### SD-T53: Implement the Resource Center view

- **Objective**: Deliver the prototype's read experience against real data.
- **Scope**: `frontend/src/views/ResourceCenterView.vue` with the page header, banners, Recently used strip, filter bar, SDLC stage rail, and catalog body; the filter algorithm exactly as specified in the design (including URLs excluded from search); the fixed kind sub-heading order; the `/` shortcut with its guard conditions; the link activation helper with pending-`.invalid` suppression, workspace routing, and `noopener` new tabs; derived icon colour and badge text; and the loading, error, empty-catalog, and no-match states.
- **Dependencies**: SD-T50, SD-T51, SD-T52
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Glass styling like `WwaHomeView.vue`, existing tokens only, existing `.alert` banners rather than toasts (no toast component exists). Bind text through Vue rather than the prototype's `innerHTML`. Verification: `cd frontend && npm run build`.

### SD-T54: Implement manage mode and the admin dialogs

- **Objective**: Give `DEVOPS_ADMIN` full catalog maintenance in the page.
- **Scope**: Manage toggle gated on `userStore.isDevOpsAdmin`; per-kind add affordances on empty groups; `ResourceCenterEntityDialog.vue` handling scope, group, and link in create and edit modes with client validation mirroring SD-T20; `ResourceCenterDeleteDialog.vue` stating descendant impact; disabled-entity marking; the system-scope disable warning; and conflict recovery on 409.
- **Dependencies**: SD-T53
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Follow the dialog prop and emit contract of `frontend/src/components/ScopeDirectoryDialog.vue:6-16` and the conditional mount pattern of `ConfigAdminView.vue:1042-1062`. Do not reproduce the prototype's role `<select>` or its `window.confirm`. Client validation is convenience only — the server is authoritative.

### W10 · Product rename to Resource Center

### SD-T90: Rename Platform route, registry, and frontend modules

- **Objective**: User-facing Hub surfaces say **Resource Center** and use `/wwa/resource-center`.
- **Scope**: Router path/name/meta; `agentRegistry` capability `key: 'resource-center'`, label `Resource Center`, `to: '/wwa/resource-center'`; rename frontend modules from `ServiceDirectory*` / `serviceDirectory` to `ResourceCenter*` / `resourceCenter` (view, dialogs, api, store, composable, types only as needed); optional client redirect from `/wwa/service-directory` → `/wwa/resource-center` for bookmarks. Recently used key becomes `wwa.resourceCenter.recent.v1` (old key may be read once then dropped).
- **Dependencies**: SDD rename accepted
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Do not rename SDD artifact filenames in this task.

### SD-T91: Rename Platform API path and backend symbols

- **Objective**: API base path is `/api/platform/resource-center`; Java types match the design names.
- **Scope**: Controller mapping; package/folder `domain/resourcecenter` (or keep package and rename only public types — prefer move to match design); DTOs/service/validator/seed loader/controller/test class renames; seed resource path `classpath:resource-center/seed-catalog.json`; audit constants `resource_center_update` / `resource_center_delete`. **Do not** rename table `DA_SERVICE_DIRECTORY_CATALOG` or Flyway `V20` filename.
- **Dependencies**: SD-T90 can proceed in parallel once path contract is frozen; coordinate with SD-T92
- **Owner type**: backend
- **Priority**: Must
- **Notes**: If any environment already wrote `service_directory_*` audit rows, document that historical rows keep the old action string and new writes use the new constants (or migrate strings in a follow-up — MVP may leave history as-is).

### SD-T92: Verification after rename

- **Objective**: Prove no stale `service-directory` user-facing paths remain.
- **Scope**: Update controller tests for new base path; `mvn test`; `cd frontend && npm run build`; grep gate for `/wwa/service-directory` and `/api/platform/service-directory` in `frontend/` and `src/main` (allowlisted only in redirect / migration comments); CHANGELOG; traceability + handoff.
- **Dependencies**: SD-T90, SD-T91
- **Owner type**: QA
- **Priority**: Must

### W9 · Per-link icons (amendment — SD-REQ-15 / SD-FR-71)

### SD-T80: Add `DirectoryLinkIconKey` enum and wire `iconKey` through the link model

- **Objective**: Persist and validate optional per-link whitelist icon keys with no schema migration.
- **Scope**: New `DirectoryLinkIconKey` in `contracts/enums/` (`confluence`, `github`, `arcad`, `peoplesoft`, `learning`, `infosec`, `vendor`, `wwa`); optional `iconKey` on `DirectoryLink`, `DirectoryLinkDto`, `DirectoryLinkUpsertRequest`; matching TypeScript union + `DirectoryLink.iconKey` in `frontend/src/types/index.ts`; validator rule (blank → null; unknown → `ValidationAppException` on `iconKey`); service create/update mapping. Excludes: Flyway (JSON payload only), remote icon URLs, asset upload.
- **Dependencies**: Existing W1–W3 implementation
- **Owner type**: backend
- **Priority**: Must
- **Notes**: Enum constant names must equal persisted JSON strings. Extend the whitelist only by coordinated enum + TS union + asset map change.

### SD-T81: Seed known destinations with `iconKey` where brands are clear

- **Objective**: First-load catalog shows distinct icons for the main tools without admin setup.
- **Scope**: Update `seed-catalog.json` — set `iconKey` on clear brands (for example ARCAD → `arcad`, GitHub Enterprise / `repo` links → `github`, Confluence docs → `confluence`, in-Hub workspaces → `wwa`). Leave ambiguous titles without a key so they keep the letter badge. Re-validate through the existing seed loader path.
- **Dependencies**: SD-T80
- **Owner type**: backend
- **Priority**: Should
- **Notes**: Seeding only affects empty stores; existing environments keep admin-edited catalogs. Document in CHANGELOG that operators may set `iconKey` via manage mode.

### SD-T82: Frontend icon map, card rendering, and admin picker

- **Objective**: Cards show local whitelist icons; admins can choose them.
- **Scope**: Local assets under `frontend/src/assets/resource-center/icons/` (one file per key); shared resolve helper; update `ResourceCenterView.vue` card icon slot (icon when mapped, else existing letter badge); add Icon select to `ResourceCenterEntityDialog.vue` for link create/edit with empty = default badge; API/store payloads include `iconKey`.
- **Dependencies**: SD-T80
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Prefer simple SVG/monochrome marks that fit the existing Hub tokens. Do not load remote images. An unknown key from a newer backend must fall back to the letter badge without breaking the page.

### SD-T83: Contract tests and verification for `iconKey`

- **Objective**: Prove whitelist enforcement and happy-path echo.
- **Scope**: Extend `ResourceCenterControllerTest` with API guide checklist row 17; run `mvn test -Dtest=ResourceCenterControllerTest` and `cd frontend && npm run build`; update CHANGELOG Unreleased; update traceability + handoff.
- **Dependencies**: SD-T80, SD-T82
- **Owner type**: QA
- **Priority**: Must
- **Notes**: Cover accept known key, omit/blank, reject unknown, reject URL-shaped string.

### SD-T60: Align SDLC guideline and feedback content

- **Objective**: Avoid two surfaces telling users different things.
- **Scope**: Compare the seeded SDLC `docs` links against the Agent Contribute Dashboard's `resourceLinks` in `frontend/src/config/agentContributionDashboard.json`, reconcile the URLs, and record who owns each list.
- **Dependencies**: SD-T22
- **Owner type**: platform
- **Priority**: Should
- **Notes**: Manual alignment only for MVP (SD-FR-66). Verified that the dashboard's links are frontend-only static JSON and its backend persists just stage statuses, so there is no shared source to integrate with today. A shared source is a separate slice (spec SD-OQ-03).

### SD-T61: Update the CHANGELOG

- **Objective**: Record a user-facing addition.
- **Scope**: One entry describing the new Resource Center page and its admin capability.
- **Dependencies**: SD-T53, SD-T54
- **Owner type**: platform
- **Priority**: Must
- **Notes**: Required by `PROJECT_RULES.md` for user-facing product changes.

### SD-T62: Run the full verification set and record evidence

- **Objective**: Prove the slice works rather than asserting it.
- **Scope**: `mvn test`; `cd frontend && npm run build`; and the documented manual walkthrough below, run as both `emp-001` (`DEVELOPER`) and `emp-003` (`DEVOPS_ADMIN`), plus one guest session. Capture before/after screenshots for the UI change.
- **Dependencies**: SD-T40, SD-T54
- **Owner type**: QA
- **Priority**: Must
- **Notes**: `PROJECT_RULES.md` forbids calling the slice ready unless the manual workflow was actually run. Name any skipped check and why.

### SD-T63: Update traceability and the agent handoff

- **Objective**: Leave the next session a truthful resume point.
- **Scope**: Update `docs/00-context/service-directory-traceability.md` status and evidence, then update `docs/00-context/AGENT_HANDOFF.md` **last**.
- **Dependencies**: SD-T62
- **Owner type**: platform
- **Priority**: Must
- **Notes**: ADR-0008 requires the handoff to be the final edit of a session with meaningful progress.

### SD-T70: Correct the base-package drift in the governance files

- **Objective**: Stop future agents from generating code into a package that does not exist.
- **Scope**: `CLAUDE.md` (9 occurrences) and `AGENTS.md` (7) state the Java base package as `com.wwa.deploymentagent`; the code uses `com.wwa.agenthub`. Correct the Architecture Boundaries sections in both. `PROJECT_RULES.md` was checked and needs no change.
- **Dependencies**: None
- **Owner type**: platform
- **Priority**: Should
- **Notes**: `[ASSUMPTION]` — this task is inferred from a grounding-pass finding, not from the design. It is listed separately because it edits governance files, which needs explicit user approval; the finding is already recorded in `docs/00-context/lessons-learned.md` (LL-2026-07-25c).

---

## Complexity Indicators

Relative complexity, not calendar estimates. **S** = a focused single sitting, **M** = a substantial unit
with several interacting rules, **L** = the largest units in the slice, where most of the risk lives.

| Complexity | Tasks | Why |
|---|---|---|
| **S** | SD-T00, SD-T03, SD-T12, SD-T13, SD-T14, SD-T51, SD-T61, SD-T63, SD-T81 | Single-file or decision-only work with an unambiguous shape |
| **M** | SD-T01, SD-T02, SD-T10, SD-T11, SD-T22, SD-T30, SD-T31, SD-T50, SD-T52, SD-T60, SD-T62, SD-T70, SD-T80, SD-T82, SD-T83, SD-T90, SD-T91, SD-T92 | Several coordinated edits or one non-trivial rule set, but no branching design space |
| **L** | SD-T20, SD-T21, SD-T40, SD-T53, SD-T54 | The validator and service carry every structural rule; the view carries the whole filter algorithm; the admin surface carries three entity shapes in two modes; the test task covers 14 contract rows |

The five **L** tasks are where review attention pays off most. None of them requires a design decision
that is not already made in the design document — if an implementer finds one, that is a defect in the
design, not a judgement call to make in code.

---

## Dependency Plan

**Critical path:**

```
SD-T00 → SD-T10 → SD-T12 → SD-T21 → SD-T30 → SD-T40 → SD-T62 → SD-T63
                     ↑         ↑
                  SD-T20    SD-T22
```

with the frontend path joining before verification:

```
SD-T11 → SD-T50 → SD-T53 → SD-T54 → SD-T62
```

**Prerequisite clusters:**

| Cluster | Blocked by | Blocks |
|---|---|---|
| Decisions (SD-T00, SD-T01, SD-T03) | Nothing — user ratification only | All implementation |
| Entity and enums (SD-T10, SD-T11) | SD-T00 | Repository, validator, frontend types |
| Migration (SD-T13, SD-T14) | SD-T10 | Oracle deployment readiness (not local development) |
| Domain (SD-T20, SD-T21, SD-T22) | SD-T10, SD-T12 | API |
| API (SD-T30, SD-T31) | SD-T21 | Backend tests, live frontend integration |
| Frontend foundation (SD-T50, SD-T51, SD-T52) | SD-T11 plus the frozen contract | View |
| Frontend page and admin (SD-T53, SD-T54) | SD-T50 … SD-T52 | Verification |
| Hardening (SD-T60 … SD-T63) | Tests and admin UI | Acceptance |
| Icons amendment (SD-T80 … SD-T83) | Prefer after W10 | Manual UAT for icons |
| Rename (SD-T90 … SD-T92) | SDD rename accepted | Icons W9 / release naming |

**Parallel workstreams:**

- SD-T02 (URL collection) runs independently of everything from day one.
- SD-T11, SD-T50, SD-T51, SD-T52 can proceed against the frozen API guide while SD-T20 … SD-T31 are in progress.
- SD-T40 (backend tests) runs alongside SD-T53 (view).
- SD-T13 and SD-T14 can be done any time after SD-T10; they do not block local development because `local` and `test` use H2 auto-DDL.
- SD-T70 is independent of all implementation work.
- W10 (SD-T90 ∥ SD-T91 → SD-T92) aligns code with the Resource Center product name.
- W9 (SD-T80 → SD-T81 ∥ SD-T82 → SD-T83) runs after the base slice; prefer after W10 so API/route paths are stable.

**No cycles:** every dependency above points strictly forward along the critical path.

---

## Verification

```bash
# Backend — full suite
mvn test

# Backend — focused, while iterating
mvn test -Dtest=ResourceCenterControllerTest

# Frontend — type check and build
cd frontend && npm run build

# Local run for the manual walkthrough
mvn spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm run dev
```

### Manual walkthrough (required before claiming readiness)

As `emp-001` (`DEVELOPER`):

1. Open the flyout, confirm the Resource Center entry with no lock icon, and open it.
2. Confirm the Home Shared Controls card leads to the same page.
3. Confirm the seven SDLC stages appear in order with their agent names.
4. Filter by scope, then by kind, then search. To prove URLs are not searched, use a fragment that appears **only** inside a URL and in no display text (a path segment such as `/browse/` works; do not use a word like "github", which legitimately matches the "GitHub / source" kind label).
5. Select a stage, confirm other scopes disappear, then re-select it to clear the focus.
6. Open a `workspace` link (in-app), a `repo` link (new tab), and a pending `.invalid` link (blocked with "URL pending").
7. Confirm Recently used fills, caps at 8, de-duplicates on re-open, and clears.
8. Confirm no manage affordances are visible.

As `emp-003` (`DEVOPS_ADMIN`):

9. Enable manage mode; add a scope, a group inside it, and a link inside that group.
10. Reload as `emp-001` and confirm the new content is visible.
11. Edit the link, move it to another group, and confirm Recently used still resolves it.
12. Delete the custom scope and confirm the confirmation states the descendant counts.
13. Attempt to delete `sdlc` and confirm the rejection message, then retitle `sdlc` and confirm that succeeds.
14. Open Audit Log and confirm each mutation appears with an identifiable entity, and that the link move from step 11 shows its source and destination group.

Two-session concurrency check (needs two browser sessions signed in as `emp-003`, for example one normal
and one private window):

15. In both sessions, open the page so both hold the same catalog version.
16. In session A, edit a link and save. Confirm it succeeds.
17. In session B — still showing the pre-edit page — edit a *different* link and save. Confirm the conflict message appears, the page re-fetches, and session A's change is intact. This is the check that would have failed under a storage-only locking design, so do not skip it.
18. In session B, now reapply the edit against the refreshed page and confirm it succeeds.
19. Repeat steps 15–17 with a *delete* in session B instead of an edit, and confirm the same conflict behavior.
20. Repeat step 15, then in session B *add* a new link. Confirm it is **accepted** — creates are deliberately exempt, so a conflict here would be a bug.

As a guest session:

21. Confirm the catalog is readable (or redirected, if SD-T01 resolves to option b) and that no manage affordance appears.

---

## Definition Of Done

1. Every acceptance row in the spec's matrix (SD-US-01 … SD-US-08 plus the guest posture) is satisfied.
2. Prototype behaviors are reproduced without the mock role switch, the browser-side catalog, the fabricated recents, or the example `security` scope.
3. The catalog lives only in `DA_SERVICE_DIRECTORY_CATALOG`; the boundary assertion test proves no Configuration Management rows were added and no `ConfigKey` exists for it.
4. Every admin mutation is visible in Audit Log with an identifiable entity and `actor_kind = HUMAN`.
5. `mvn test` and `cd frontend && npm run build` both pass, and the manual walkthrough above was actually run with evidence recorded.
6. `docs/sql/ORACLE_CURRENT_SCHEMA.sql` includes the new table.
7. CHANGELOG updated; traceability updated; `docs/00-context/AGENT_HANDOFF.md` updated last.
8. SD-T01 and SD-T02 are closed, or their residual risk is explicitly accepted by the user in writing.

---

## Risks / Blockers

| # | Risk | Impact | What resolves it |
|---|---|---|---|
| 1 | SD-T02 unresolved at release: ARCAD and GitHub Enterprise ship as "URL pending" | Medium — the Common scope's headline value is missing | Platform operations supply the URLs; the seed resource is updated |
| 2 | SD-T01 reversed after implementation | Medium — a visible behavior change plus rework in the route guard and read path | Ratify during SDD acceptance, before W1 starts |
| 3 | The catalog version covers the whole document, so concurrent admin edits to unrelated entries collide | Low — an explicit 409 with automatic re-fetch, not data loss | Accepted for MVP; per-entity versions would need per-entity rows, which `ADR-0010` decided against. Normalising later is contained because one module owns persistence |
| 3b | An implementer "simplifies" the design by dropping `expectedVersion` and trusting `@Version` alone | High — silent lost updates that no test catches unless row 12 of the contract checklist is written in full | Mitigated by stating the reasoning in SD-T21 and by contract checklist rows for the exempt-create and missing-parameter cases |
| 4 | `CLAUDE.md` and `AGENTS.md` document the wrong Java base package | Medium — a future agent may generate code into a non-existent package | SD-T70 (needs user approval to edit those files) |
| 5 | No frontend component-test framework | Medium — filter, Recently used, and pending-URL logic have no automated coverage | Accepted: type checking plus the manual walkthrough. Do not claim automated UI coverage. Extracting pure helpers testable under the existing Node test runner is a possible follow-up |
| 6 | The word "scope" now means two different things in the codebase | Medium — a future change could wire the catalog to `ScopeDirectoryEntry` by mistake | Naming discipline (`DirectoryScope` versus `AccessScope` / `ScopeDirectoryEntry`) plus the boundary test in SD-T40 |
| 7 | Seed content drifts from the Agent Contribute Dashboard | Low to medium — two surfaces disagree | SD-T60 plus recorded content ownership |
| 8 | Existing `AuditLogEntry.target_type` / `target_id` columns stay unused and unexposed | Low — this slice works around them via the context payload | A separate audit-capability improvement, deliberately out of this slice |

---

## Open Questions

| # | Question | Blocking? | Owner |
|---|---|---|---|
| SD-OQ-01 | May guest sessions read the catalog, or should they be redirected? Default committed: read allowed. | Blocks W1 start (cheap now, expensive later) | Product / Security |
| SD-OQ-02 | Production ARCAD and GitHub Enterprise URLs. | Blocks release readiness, not development | Ops / Platform |
| SD-OQ-03 | Should SDLC guideline / feedback links have one shared source with the Agent Contribute Dashboard? Default: manual alignment for MVP. | Not blocking | Product |
| SD-OQ-04 | Must `ADR-0010` be accepted before implementation? Default: yes. | Blocks W1 start | Architecture |
| SD-OQ-05 | Is SD-T70 (correcting the base-package drift in `CLAUDE.md` and `AGENTS.md`) approved? | Not blocking this slice | User |

`SD-OQ-01` … `SD-OQ-04` are inherited from the requirement and spec. `SD-OQ-05` is raised here: it is
out-of-slice governance housekeeping surfaced by the grounding pass, not a Resource Center scope
question.
