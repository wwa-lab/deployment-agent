# Resource Center — API Implementation Guide

| Field | Value |
|---|---|
| **Slice** | `service-directory` |
| **Status** | Regenerated via `architecture-to-design`; **Amended 2026-07-25** — product renamed to **Resource Center**; optional link `iconKey` (SD-FR-71) |
| **Date** | 2026-07-25 |
| **Version** | 1.2 (target contract: Resource Center paths; code still on `service-directory` until W10) |
| **Base path** | `/api/platform/resource-center` (formerly `/api/platform/service-directory`) |
| **Product name** | Resource Center |
| **Backend stack** | Java 21 · Spring Boot 3.4 · Spring MVC · Spring Data JPA · Lombok |
| **Auth model** | Session-based (`UserContext` from the session), with `X-User-Id` / `X-User-Role` header fallback in the `local` and `test` profiles |
| **Source design** | `docs/05-design/service-directory-design.md` |
| **Behavior source of truth** | `docs/03-spec/service-directory-spec.md` |

---

## Overview

This capability exposes one read endpoint that returns the whole directory catalog, and nine
`DEVOPS_ADMIN`-only mutation endpoints that each apply a single change to it. Every mutation returns the
complete updated catalog, so a client never needs a follow-up read to converge.

Two contract properties are deliberate and worth reading before implementing:

1. **Scopes and groups are addressed by `key`; links are addressed by `id`.** Links have no key, they can move between groups, and their ids are what the browser's Recently used list stores — so a nested link path would misdescribe a moved link.
2. **There is no whole-catalog `PUT`.** It would make audit detail unanswerable ("something changed") and would turn every concurrent edit into a lost update.

---

## Authentication

### Auth chain

Requests pass through the existing filter chain, configured at
`src/main/java/com/wwa/agenthub/config/SecurityConfig.java:44-46`:

```
SessionAuthFilter  →  HeaderAuthFilter  →  GuestReadOnlyFilter
(session attribute)   (local/test only)     (blocks guest writes)
```

- `SessionAuthFilter` reads the `USER_CONTEXT` session attribute and populates the security context (`web/security/SessionAuthFilter.java:22-34`).
- `HeaderAuthFilter` builds a `UserContext` from `X-User-Id` / `X-User-Role` when not already authenticated — enabled in `local` and `test` only (`web/security/HeaderAuthFilter.java:57-70`).
- `GuestReadOnlyFilter` returns 403 for any non-`GET`/`HEAD`/`OPTIONS` request from a `GUEST` session, with `/api/platform/auth/logout` as the only exception (`web/security/GuestReadOnlyFilter.java:29-63`). **This slice adds no exception**, so guest mutation attempts never reach the controller.

Controllers receive the caller as `@AuthenticationPrincipal UserContext user`, matching every existing
platform controller.

### Authorization model

| Operation | Requirement | Enforcement |
|---|---|---|
| Read catalog | Any authenticated session, including `GUEST` | Presence of a `UserContext` |
| Read with `includeDisabled=true` | `DEVOPS_ADMIN` | Honoured only for admins; **silently ignored** for others (no 403), so a stale bookmark cannot break a read |
| Any mutation | `DEVOPS_ADMIN` | `user.hasRole("DEVOPS_ADMIN")` (`contracts/UserContext.java:56-58`); otherwise `ForbiddenAppException` |

Authorization is imperative, matching `ConfigurationController.java:39-48`. `@PreAuthorize` is not used
anywhere in this codebase and must not be introduced here.

No new role, permission, or scope is introduced. A client-supplied role is never trusted.

### Roles

| Role | Read | `includeDisabled` | Mutate |
|---|---|---|---|
| `DEVELOPER` | Yes | Ignored | No — 403 |
| `TL` | Yes | Ignored | No — 403 |
| `AUDIT` | Yes | Ignored | No — 403 |
| `MANAGEMENT` | Yes | Ignored | No — 403 |
| `DEVOPS_ADMIN` | Yes | Honoured | Yes |
| `GUEST` | Yes | Ignored | No — blocked by the filter before the controller |

### Local stub users

The stub auth provider accepts any non-empty password for the seeded employee ids documented in
`CLAUDE.md`; `emp-003` (Carol Lee) is the `DEVOPS_ADMIN` used for manual admin testing.

---

## Error Response Format

All errors use the existing envelope `ErrorResponseDto`
(`contracts/dto/ErrorResponseDto.java:4`), produced by `GlobalExceptionHandler`
(`web/exception/GlobalExceptionHandler.java`):

```json
{
  "code": "VALIDATION_ERROR",
  "message": "scope key already exists: sdlc",
  "details": null
}
```

| Status | `code` | Raised when |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Any structural or URL rule violation, including an attempt to delete a system scope |
| 400 | (bean validation) | Malformed body or missing required field — `details` carries per-field messages (`GlobalExceptionHandler.java:39-46`) |
| 401 | `UNAUTHORIZED` | No authenticated session |
| 403 | `FORBIDDEN` | Authenticated non-admin attempting a mutation |
| 403 | (guest message) | Guest attempting any non-read request — produced by the filter, not this controller |
| 404 | `NOT_FOUND` | Unknown scope key, group key, or link id |
| 409 | `OPTIMISTIC_LOCK_CONFLICT` | Either the `expectedVersion` sent on an update or delete no longer matches the stored version (thrown as `OptimisticLockConflictException`, mapped by `GlobalExceptionHandler.java:24-28`), or two writes overlapped in flight (raw JPA failure, mapped by `GlobalExceptionHandler.java:31-36`). Same status and code either way |
| 500 | — | Unexpected failure, including a corrupt stored payload |

This capability never returns **422** — that status is reserved for the spreadsheet import flow
(`GlobalExceptionHandler.java:49-57`), which the Resource Center does not use.

Error messages must name the offending field or entity and must never leak internal paths or stack
traces.

---

## API Endpoints Summary

### Catalog read

| Operation | Method | Endpoint | Auth |
|---|---|---|---|
| Read the catalog | GET | `/api/platform/resource-center` | Authenticated (incl. `GUEST`) |

### Directory scope administration

| Operation | Method | Endpoint | Auth |
|---|---|---|---|
| Create scope | POST | `/api/platform/resource-center/scopes` | `DEVOPS_ADMIN` |
| Update scope | PUT | `/api/platform/resource-center/scopes/{scopeKey}?expectedVersion=` | `DEVOPS_ADMIN` |
| Delete scope (cascades) | DELETE | `/api/platform/resource-center/scopes/{scopeKey}?expectedVersion=` | `DEVOPS_ADMIN` |

### Directory group administration

| Operation | Method | Endpoint | Auth |
|---|---|---|---|
| Create group | POST | `/api/platform/resource-center/scopes/{scopeKey}/groups` | `DEVOPS_ADMIN` |
| Update group | PUT | `/api/platform/resource-center/scopes/{scopeKey}/groups/{groupKey}?expectedVersion=` | `DEVOPS_ADMIN` |
| Delete group (cascades) | DELETE | `/api/platform/resource-center/scopes/{scopeKey}/groups/{groupKey}?expectedVersion=` | `DEVOPS_ADMIN` |

### Directory link administration

| Operation | Method | Endpoint | Auth |
|---|---|---|---|
| Create link | POST | `/api/platform/resource-center/scopes/{scopeKey}/groups/{groupKey}/links` | `DEVOPS_ADMIN` |
| Update link (may move it) | PUT | `/api/platform/resource-center/links/{linkId}?expectedVersion=` | `DEVOPS_ADMIN` |
| Delete link | DELETE | `/api/platform/resource-center/links/{linkId}?expectedVersion=` | `DEVOPS_ADMIN` |

**Every** endpoint in every table above responds with the same body shape: the full
`ResourceCenterCatalogDto`.

### The `expectedVersion` parameter

Every **PUT and DELETE** takes a required `expectedVersion` query parameter carrying the `version` the
client last received. The three **POST** creates take no such parameter. See
[Concurrency](#concurrency) for why the split is deliberate rather than an oversight, and
`docs/03-spec/service-directory-spec.md` SD-FR-44, SD-FR-67, and SD-FR-68 for the governing rules.

Declare it as a primitive `long` with no `defaultValue`, so Spring MVC rejects a missing parameter with
400 before the handler body runs. Giving it a default would turn a client bug into a silent
last-write-wins overwrite, which is exactly the failure this parameter exists to prevent.

It is a query parameter, not a body field or an `If-Match` header, because DELETE has no body and the
codebase emits no ETags today; one uniform mechanism across all six conditional writes is easier to
implement correctly than two.

---

## Shared Response Shape

```json
{
  "version": 7,
  "updatedBy": "emp-003",
  "updatedAt": "2026-07-25T02:14:09Z",
  "scopes": [
    {
      "key": "sdlc",
      "title": "SDLC",
      "description": "Seven Qilianshan delivery stages.",
      "layout": "stage-strip",
      "system": true,
      "enabled": true,
      "sortOrder": 10,
      "groups": [
        {
          "key": "deployment",
          "title": "Deployment",
          "description": "Human-in-the-loop deployment across SIT, UAT, and PROD.",
          "type": "stage",
          "stageKey": "deployment",
          "stageOrder": 6,
          "agentName": "Deployment Agent",
          "enabled": true,
          "sortOrder": 60,
          "links": [
            {
              "id": "8f1c0f8e-6c1a-4a1f-9d0b-2b7c5e1a44d1",
              "title": "Deployment Guideline",
              "description": "Confluence · stage guideline",
              "url": "https://confluence.example.invalid/wwa/deployment-guideline",
              "kind": "docs",
              "kindLabel": "Guideline",
              "iconKey": "confluence",
              "enabled": true,
              "sortOrder": 10
            },
            {
              "id": "b3a9d2e4-7f52-4c3a-9a11-0d8e6c2f7b93",
              "title": "Deployment Agent",
              "description": "Open the WWA Deployment workspace",
              "url": "/wwa/deployment-agent",
              "kind": "workspace",
              "kindLabel": "Workspace",
              "iconKey": "wwa",
              "enabled": true,
              "sortOrder": 30
            }
          ]
        }
      ]
    }
  ]
}
```

Field semantics, types, and length limits: `docs/04-architecture/service-directory-data-model.md` §4.

Notes:

- `version` is the concurrency token. Clients must retain it and echo it as `expectedVersion` on every update and delete (never on a create). It changes on every successful mutation, and the mutation response carries the new value, so a client that always stores the latest response never needs a second read.
- `updatedBy` is `null` for a freshly seeded catalog (the seed has no human author).
- Fallback presentation values (icon colour, two-letter badge, open-in-new-tab) are **not** in the payload; the client derives them from `kind` and `title` when `iconKey` is absent or unknown.
- Optional `iconKey` **is** in the payload when set. It is a whitelist key into local frontend assets, never an image URL (SD-FR-71).

---

## Endpoint Reference

### GET `/api/platform/resource-center`

**Purpose:** Return the whole catalog for rendering. Seeds the store on first access when it is empty.

**Query parameters**

| Name | Type | Required | Default | Description |
|---|---|---|---|---|
| `includeDisabled` | boolean | No | `false` | When `true` **and** the caller is `DEVOPS_ADMIN`, disabled scopes, groups, and links are included. Ignored for every other role |

**Response 200:** the shared catalog shape above.

Projection rules:

| Caller | Payload |
|---|---|
| Non-admin (any role incl. `GUEST`) | Enabled scopes only; within them enabled groups only; within them enabled links only. Disabled content is removed server-side, never merely hidden by the client |
| `DEVOPS_ADMIN`, `includeDisabled=false` | Same as above |
| `DEVOPS_ADMIN`, `includeDisabled=true` | Everything, with each entity's `enabled` flag intact so the UI can mark disabled rows |

**Ordering:** scopes, then groups, then links, each by `sortOrder` ascending. Ties break by `key`
ascending for scopes and groups; links have no key, so their ties break by `title` then `id`
ascending. Ordering is applied server-side so every client renders identically.

**Validation:** none — no request body.

**Error cases**

| Status | When |
|---|---|
| 401 | No authenticated session |
| 500 | Stored payload cannot be deserialised. This deliberately fails loudly rather than returning an empty catalog that an administrator might "repair" by overwriting real content |

**Side effects:** on an empty store only, the packaged seed catalog is validated and inserted once
(one row, `version` 0, `updatedBy` null). No audit entry is written for seeding — it is not a human
action. No audit entry is written for any read.

---

### POST `/api/platform/resource-center/scopes`

**Purpose:** Create a directory scope.

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `key` | string | Yes | Trimmed and lower-cased, then matched against `^[a-z0-9][a-z0-9_-]{1,31}$`; must be unique across scopes |
| `title` | string | Yes | ≤ 120 characters after trimming |
| `description` | string | No | ≤ 240 characters |
| `layout` | enum | Yes | `stage-strip` or `buckets` |
| `enabled` | boolean | No | Defaults to `true` |
| `sortOrder` | integer | No | 0–9999. When omitted, defaults to the highest sibling `sortOrder` + 10 (clamped to 9999) |

`system` is **not** accepted from clients: only the seed creates system scopes, so an administrator
cannot mint an undeletable scope.

```json
{
  "key": "security",
  "title": "Security",
  "description": "Security tooling and standards.",
  "layout": "buckets",
  "enabled": true
}
```

**Response 200:** updated catalog. The new scope has an empty `groups` array — an empty scope is legal
and shows an add affordance in manage mode.

**Error cases**

| Status | When |
|---|---|
| 400 | Key pattern violation, duplicate key, missing or over-long title, `sortOrder` out of range, unknown `layout`, or a second scope with `layout = stage-strip` (SD-FR-70) |
| 401 / 403 | No session / not `DEVOPS_ADMIN` |

There is no 409 here: creates carry no `expectedVersion` and cannot be stale (SD-FR-67).

**Side effects:** catalog row updated, `version` incremented, `updatedBy` / `updatedAt` set; one audit
entry with action `resource_center_update` and context `{ target_type: SERVICE_DIRECTORY, entity: scope, entity_key: security, entity_title: Security, operation: create }`.

---

### PUT `/api/platform/resource-center/scopes/{scopeKey}`

**Purpose:** Update a directory scope's presentation, ordering, or availability.

**Path parameters:** `scopeKey` — the key of the scope.

**Query parameters:** `expectedVersion` (long, **required**).

**Request body:** same fields as create, except that **`key` is immutable**. If `key` is present it must
equal `{scopeKey}`; a differing value is rejected with 400 rather than silently ignored, so a client
cannot believe a rename succeeded.

Keys are identifiers that behavior depends on — the stage rail is found by the `stage-strip` layout and
the SDLC scope's key, and audit history references keys — so a key is chosen once at create time. To
change what users see, change the `title`.

**Response 200:** updated catalog, carrying the incremented `version`.

**Error cases**

| Status | When |
|---|---|
| 400 | Any validation rule; a `key` differing from `{scopeKey}`; a second `stage-strip` scope; or a missing `expectedVersion` |
| 401 / 403 | No session / not `DEVOPS_ADMIN` |
| 404 | `scopeKey` does not exist |
| 409 | `expectedVersion` no longer matches the stored version |

**Side effects:** as create, with `operation: update`. Disabling a `system` scope is permitted (the UI
warns); deleting or re-keying one is not.

---

### DELETE `/api/platform/resource-center/scopes/{scopeKey}`

**Purpose:** Delete a scope together with all of its groups and links.

**Path parameters:** `scopeKey`.

**Query parameters:** `expectedVersion` (long, **required**). A cascade delete is quoted to the
administrator as "removes N groups and M links"; if the catalog has moved on, those counts may be wrong,
so a superseded version must block the delete rather than proceed against a different shape.

**Response 200:** updated catalog.

**Validation:** a scope with `system = true` (`sdlc`, `common`, `external`) cannot be deleted. The
rejection message states that system scopes may be disabled or retitled but not removed.

**Error cases**

| Status | When |
|---|---|
| 400 | `scopeKey` refers to a system scope, or `expectedVersion` is missing |
| 401 / 403 | No session / not `DEVOPS_ADMIN` |
| 404 | `scopeKey` does not exist |
| 409 | `expectedVersion` no longer matches the stored version |

**Side effects:** the scope and every descendant are removed in one transaction; `version` incremented;
**one** audit entry with action `resource_center_delete` and context
`{ …, entity: scope, operation: delete, removed_groups: 2, removed_links: 9 }` — a single entry with
counts rather than one entry per descendant.

---

### POST `/api/platform/resource-center/scopes/{scopeKey}/groups`

**Purpose:** Create a group inside a scope.

**Path parameters:** `scopeKey`.

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `key` | string | Yes | Same pattern as a scope key; unique **within this scope** only. When `type = stage`, must be identical to `stageKey` (SD-FR-51) |
| `title` | string | Yes | ≤ 120 characters |
| `description` | string | No | ≤ 240 characters |
| `type` | enum | Yes | `stage` or `bucket` |
| `stageKey` | enum | Required when `type = stage`, rejected otherwise | One of `planning`, `estimation`, `discovery`, `build`, `testing`, `deployment`, `maintenance`. Must equal `key`, so a stage has one identity and the stage rail cannot disagree with the document |
| `stageOrder` | integer | Required when `type = stage`, rejected otherwise | 1–99; the number shown in the stage rail |
| `agentName` | string | No | Owning agent display text, for example "Deployment Agent". Display only — not an `AgentId` reference and not used for any isolation decision |
| `enabled` | boolean | No | Defaults to `true` |
| `sortOrder` | integer | No | 0–9999; defaults to the end of the sibling list (highest sibling + 10, clamped to 9999) |

```json
{
  "key": "scanners",
  "title": "Security · Scanners",
  "description": "SAST and dependency scanning.",
  "type": "bucket",
  "enabled": true
}
```

**Response 200:** updated catalog; the new group has an empty `links` array.

**Error cases**

| Status | When |
|---|---|
| 400 | Key pattern or uniqueness violation within the scope; `key` not equal to `stageKey` for a `stage`; `stageKey` / `stageOrder` present for a `bucket` or missing for a `stage`; unknown `stageKey`; length or range violation |
| 401 / 403 | No session / not `DEVOPS_ADMIN` |
| 404 | `scopeKey` does not exist |

No 409: creates carry no `expectedVersion` (SD-FR-67).

**Side effects:** as for a scope create, with `entity: group`.

---

### PUT `/api/platform/resource-center/scopes/{scopeKey}/groups/{groupKey}`

**Purpose:** Update a group.

**Path parameters:** `scopeKey`, `groupKey`.

**Query parameters:** `expectedVersion` (long, **required**).

**Request body:** same fields as create, except that **`key` is immutable** — if present it must equal
`{groupKey}`. The reasoning matches scope keys, with an extra consequence for stages: since `key` must
equal `stageKey`, re-keying a stage group would change which stage it is.

**Moving a group between scopes is not supported** in MVP: a group's identity is scoped by its parent,
and no requirement asks for it. Recreate it in the target scope instead.

**Response 200:** updated catalog.

**Error cases:** as for group create, plus 404 when `groupKey` does not exist in that scope, 400 when
`key` differs from `{groupKey}` or `expectedVersion` is missing, and 409 when `expectedVersion` no longer
matches the stored version.

**Side effects:** as for group create, with `operation: update`.

---

### DELETE `/api/platform/resource-center/scopes/{scopeKey}/groups/{groupKey}`

**Purpose:** Delete a group together with its links.

**Query parameters:** `expectedVersion` (long, **required**).

**Response 200:** updated catalog.

**Error cases**

| Status | When |
|---|---|
| 400 | `expectedVersion` missing |
| 401 / 403 | No session / not `DEVOPS_ADMIN` |
| 404 | `scopeKey` or `groupKey` does not exist |
| 409 | `expectedVersion` no longer matches the stored version |

There is no system-group protection: only scopes carry the `system` flag.

**Side effects:** group and its links removed in one transaction; one audit entry with action
`resource_center_delete`, `entity: group`, and `removed_links`.

---

### POST `/api/platform/resource-center/scopes/{scopeKey}/groups/{groupKey}/links`

**Purpose:** Create a link inside a group.

**Path parameters:** `scopeKey`, `groupKey`.

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `title` | string | Yes | ≤ 120 characters |
| `description` | string | No | ≤ 240 characters |
| `url` | string | Yes | ≤ 1024 characters; validated per `kind` — see the URL rules below |
| `kind` | enum | Yes | `docs`, `tool`, `workspace`, `repo` |
| `kindLabel` | string | No | ≤ 24 characters; short pill caption. Defaults to the kind's display name |
| `iconKey` | enum | No | One of `confluence`, `github`, `arcad`, `peoplesoft`, `learning`, `infosec`, `vendor`, `wwa`. Omit, `null`, or blank for the letter-badge fallback. Unknown values → 400 on `iconKey` |
| `enabled` | boolean | No | Defaults to `true` |
| `sortOrder` | integer | No | 0–9999; defaults to the end of the sibling list (highest sibling + 10, clamped to 9999) |

`id` is **not** accepted: the server assigns a UUID. This matters because the browser's Recently used
list keys on that id, so it must never be client-chosen or reassigned.

```json
{
  "title": "Deployment Agent · source",
  "description": "Repo for newcomers to start contributing",
  "url": "https://github.example.com/wwa/deployment-agent",
  "kind": "repo",
  "kindLabel": "GitHub",
  "iconKey": "github"
}
```

**URL validation rules**

| Rule | Applies to | Behavior |
|---|---|---|
| Must be an in-Hub path matching `^/wwa/[A-Za-z0-9._~\-/]*$` | `kind = workspace` | Anything else, including an absolute URL, is rejected |
| Scheme must be `http` or `https`, compared case-insensitively | `docs`, `tool`, `repo` | A relative path is rejected |
| `javascript:`, `data:`, `vbscript:` (any case) | all kinds | Rejected |
| Protocol-relative (`//host/...`) | all kinds | Rejected |
| Blank or whitespace-only | all kinds | Rejected |
| Host ending in `.invalid` | all kinds | **Accepted for storage.** This is the reserved placeholder convention (RFC 2606); the client renders such links as "URL pending" and does not activate them. Validation must not reject them, or the seed could not be stored |

**Response 200:** updated catalog, including the new link with its server-assigned `id`.

**Error cases**

| Status | When |
|---|---|
| 400 | Missing title, unknown `kind`, unknown `iconKey`, any URL rule violation, length or range violation |
| 401 / 403 | No session / not `DEVOPS_ADMIN` |
| 404 | `scopeKey` or `groupKey` does not exist |

No 409: creates carry no `expectedVersion` (SD-FR-67).

**Side effects:** as above, with `entity: link` and `entity_key` set to the new link id.

---

### PUT `/api/platform/resource-center/links/{linkId}`

**Purpose:** Update a link and, optionally, move it to a different group.

**Path parameters:** `linkId` — the link's server-assigned id. The path is **not** nested under scope
and group, because a link may move; a nested path would misdescribe its new parent.

**Query parameters:** `expectedVersion` (long, **required**).

**Request body:** the create fields, plus:

| Field | Type | Required | Description |
|---|---|---|---|
| `targetScopeKey` | string | No | When supplied together with `targetGroupKey`, the link is moved to that group |
| `targetGroupKey` | string | No | Must be supplied together with `targetScopeKey` |

Supplying exactly one of the two target fields is a validation error, not a partial move.

```json
{
  "title": "Deployment Agent · source",
  "url": "https://github.example.com/wwa/deployment-agent",
  "kind": "repo",
  "kindLabel": "GitHub",
  "iconKey": "github",
  "enabled": true,
  "targetScopeKey": "sdlc",
  "targetGroupKey": "build"
}
```

**Response 200:** updated catalog. The link keeps its `id` across a move, so a user's Recently used
entry survives.

**Error cases**

| Status | When |
|---|---|
| 400 | Any create-level validation failure, only one of the two target fields supplied, or `expectedVersion` missing |
| 401 / 403 | No session / not `DEVOPS_ADMIN` |
| 404 | `linkId` not found, or the target scope/group does not exist |
| 409 | `expectedVersion` no longer matches the stored version |

**Side effects:** as above, with `operation: update`. When — and only when — the link actually changed
parent, the audit context additionally carries all four of `from_scope_key`, `from_group_key`,
`to_scope_key`, and `to_group_key`. An in-place edit carries none of them, so the presence of
`to_group_key` is itself the signal that a move occurred and a reader never has to compare two values to
find out. Without these keys, "someone moved the production runbook out of Deployment" would be
indistinguishable in the audit trail from a title correction.

---

### DELETE `/api/platform/resource-center/links/{linkId}`

**Purpose:** Delete a single link.

**Query parameters:** `expectedVersion` (long, **required**).

**Response 200:** updated catalog.

**Error cases**

| Status | When |
|---|---|
| 400 | `expectedVersion` missing |
| 401 / 403 | No session / not `DEVOPS_ADMIN` |
| 404 | `linkId` not found |
| 409 | `expectedVersion` no longer matches the stored version |

**Side effects:** link removed; one audit entry with action `resource_center_delete` and
`entity: link`.

**Client-side consequence, not a server one:** the deleted id may still sit in some users' browser
Recently used lists. Those entries are dropped on their next page load because ids are resolved against
the live catalog. The server does nothing about it and stores no per-user state.

---

## State Reference

The catalog is reference data, not a workflow entity. The only per-entity state is availability:

```
      admin update (enabled=false)
   ENABLED ──────────────────────► DISABLED
      ▲                                │
      └──── admin update (enabled=true)─┘
      │                                │
      │  admin delete                  │  admin delete
      └──────────────┬─────────────────┘
                     ▼
                  REMOVED  (hard delete; cascades for scope and group)
```

- Readers only ever receive `ENABLED` entities.
- `DEVOPS_ADMIN` with `includeDisabled=true` receives both, with the flag intact.
- System scopes (`sdlc`, `common`, `external`) can reach `DISABLED` but never `REMOVED`.

Catalog concurrency version:

```
GET  ──► version n  (client stores n)
                 │
                 ├── PUT/DELETE ?expectedVersion=n  and stored is still n
                 │        └──► applied, response carries version n+1
                 │
                 ├── PUT/DELETE ?expectedVersion=n  but stored has moved to n+1
                 │        └──► 409, nothing applied, client must re-read
                 │
                 └── POST (no expectedVersion) against stored n+1
                          └──► applied; a create cannot overwrite anything
```

---

## Concurrency

The catalog is one row with a JPA `@Version` column, and every mutation loads, mutates, and saves inside
one transaction. That alone is **not** sufficient, and the reason is the single most important thing to
understand before implementing this section.

**Why `@Version` alone does not protect anything here.** Each request opens its own transaction and loads
the row at the start of it. So this sequence produces no version conflict whatsoever:

1. Admin A opens the page and reads version 7.
2. Admin B edits a link; the row becomes version 8.
3. Admin A submits an edit ten minutes later. A's request loads version 8, applies A's payload, and saves
   version 9.

B's change is gone and nobody was told. JPA saw two sequential transactions, each internally consistent.
`@Version` only detects transactions that overlap *in flight* — a window of milliseconds — while the
window that actually matters here is however long a form stays open.

**Therefore two layers, both returning 409:**

| Layer | Detects | Mechanism | Reaches 409 via |
|---|---|---|---|
| `expectedVersion` precondition (SD-FR-44) | A stale page: the client's last-read version has been superseded | The service compares `expectedVersion` to the loaded row's `version` **before validating or mutating**, and throws `OptimisticLockConflictException` on mismatch | `GlobalExceptionHandler.java:24-28` (`AppException` → its own 409) |
| JPA `@Version` (SD-FR-68) | Two mutations overlapping in flight | Already present on the entity; no code to write | `GlobalExceptionHandler.java:31-36` |

Neither replaces the other, and both are required. Clients cannot tell them apart, which is fine — the
correct client response is identical.

**Creates are exempt on purpose** (SD-FR-67). A create appends to whatever the current document is and
cannot overwrite another administrator's work, so requiring a version there would reject harmless
concurrent work and train admins to treat 409 as noise.

**`expectedVersion` must have no default.** Declared as a primitive `long` `@RequestParam`, a missing
parameter is a 400 from Spring MVC before any handler code runs. A default — or an `Optional` treated as
"skip the check" — silently reinstates the lost update above.

**Expected client behavior on 409:** show "the directory changed in another session", re-fetch the
catalog, and ask the administrator to reapply the edit. Merging is intentionally never attempted, because
a merge could resurrect an entry another administrator just deleted.

**Accepted cost:** because the version covers the whole document, two administrators editing unrelated
links can collide (spec risk SD-R-03). Accepted for MVP — writes are rare, an explicit conflict beats a
silent lost update, and per-entity versions would require per-entity rows, which `ADR-0010` decided
against.

---

## Integration Dependencies

### External systems

**None.** This capability performs no outbound calls. Catalog links are navigation targets that the
browser follows; the Hub never contacts them. Therefore there are no credentials, no timeouts, no retry
policy, no circuit breakers, and no availability coupling to any linked system.

### Internal dependencies

| Dependency | Purpose | Contract |
|---|---|---|
| Auth filter chain | Session resolution, header fallback, guest write block | `config/SecurityConfig.java:44-46` |
| `UserContext` | Caller identity and roles | `contracts/UserContext.java:10-17`, `:56-58` |
| `AuditLoggerService` | One audit entry per successful mutation | `domain/audit/AuditLoggerService.java:152-154` → `:110-117`, `ActorKind.HUMAN` at `:125` |
| `AuditActionType` | Action vocabulary; two constants added by this slice | `contracts/enums/AuditActionType.java:4-24` (no migration — the column is `@Enumerated(STRING)` at `AuditLogEntry.java:81-83`) |
| `GlobalExceptionHandler` | Error envelope and status mapping | `web/exception/GlobalExceptionHandler.java:24-28`, `:31-36`, `:39-46` |
| Existing `errors/` exceptions | `ValidationAppException` (400), `ForbiddenAppException` (403), `NotFoundAppException` (404) | Reused; no new exception type |
| JSON CLOB conversion | Oracle/H2-portable document storage | Pattern at `util/JsonAttributeConverter.java:18-26`; this slice adds a typed converter for the scope list |
| Flyway | New table `DA_SERVICE_DIRECTORY_CATALOG` | Next free version **V20** (chain ends at `V19__add_scope_directory_agent.sql`) |

### Required configuration

None. This capability introduces no application property, no `ConfigKey`, and no environment-specific
setting. Its only packaged content is the seed catalog resource.

### Explicitly not integrated

| Store | Why |
|---|---|
| `DA_CONFIGURATION_ITEM` / `ConfigKey` | Runtime key-value settings; a catalog key there would put navigation content behind the config editors |
| `DA_CONFIGURATION_COMPONENT` | Holds integration endpoints and credentials; the catalog has no credential concept |
| `DA_SCOPE_DIRECTORY` | Access scoping (application / SNOW group / agent) — same word "scope", unrelated model |

Recorded as a decision in
`docs/00-context/decisions/ADR-0010-service-directory-owns-its-catalog-store.md`.

---

## Contract Test Checklist

Tests belong in `src/test/java/com/wwa/agenthub/web/` alongside the other platform controller tests, using
the verified convention `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` +
`@Transactional` with `X-User-Id` / `X-User-Role` headers (`AccessGrantControllerTest.java:28-31`,
`:49-51`, `:58-60`).

| # | Check |
|---|---|
| 1 | GET on an empty store seeds once and returns `sdlc`, `common`, `external`; a second GET does not re-seed |
| 2 | GET as `DEVELOPER` omits disabled entities; `includeDisabled=true` as `DEVELOPER` is ignored (200, still omitted) |
| 3 | GET as `DEVOPS_ADMIN` with `includeDisabled=true` returns disabled entities with the flag intact |
| 4 | GET as `GUEST` returns 200 |
| 5 | Each of the nine mutations returns 403 for `DEVELOPER` and succeeds for `DEVOPS_ADMIN` |
| 6 | Duplicate scope key → 400; duplicate group key within one scope → 400; the same group key in two different scopes → 200 |
| 7 | Every URL rule row is covered, including `https://x.example.invalid/` accepted and `/wwa/audit-log` rejected for `kind = tool` |
| 8 | `stageKey` / `stageOrder` required for `type = stage` and rejected for `type = bucket`; a `stage` group with `key != stageKey` → 400 |
| 9 | Deleting a system scope → 400; disabling it → 200; retitling it → 200; sending a different `key` for it → 400 |
| 10 | Cascade delete removes descendants and writes exactly one audit entry carrying the removed counts |
| 11 | Link update with only one of `targetScopeKey` / `targetGroupKey` → 400; with both → the link moves and keeps its `id` |
| 12 | **Stale-write matrix.** Read the catalog to capture version *v*, then apply one mutation so the stored version passes *v*. Assert all five: (a) `PUT …?expectedVersion=v` → 409 and the catalog is byte-identical; (b) `DELETE …?expectedVersion=v` → 409, likewise; (c) the same PUT with the current version → 200; (d) a **POST create sent while still holding *v*** → 200, because creates carry no version (SD-FR-67); (e) a PUT with `expectedVersion` omitted entirely → 400. Rows (d) and (e) are the ones that catch a wrong implementation: a suite asserting only (a) and (b) passes even if creates are wrongly versioned or the parameter silently defaults |
| 13 | A successful mutation writes exactly one audit entry with the expected action type and context keys; a rejected mutation writes none; a GET writes none |
| 14 | A link move writes `from_scope_key`, `from_group_key`, `to_scope_key`, and `to_group_key` in the audit context; an in-place link edit writes none of the four |
| 15 | Creating a second scope with `layout = stage-strip` → 400; creating a second `buckets` scope → 200 |
| 16 | After exercising every mutation, no rows were added to `DA_CONFIGURATION_COMPONENT`, `DA_CONFIGURATION_ITEM`, or `DA_SCOPE_DIRECTORY` |
| 17 | **`iconKey` whitelist (SD-FR-71).** Create/update link with `iconKey = github` → 200 and echoed in GET; omit `iconKey` → 200 with null/absent; blank `iconKey` → stored as absent; `iconKey = jenkins` (not in MVP set) → 400 naming `iconKey`; `iconKey = https://evil.example/x.png` → 400 (must not be accepted as a URL) |
