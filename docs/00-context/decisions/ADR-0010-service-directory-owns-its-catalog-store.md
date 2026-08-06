# ADR-0010: Service Directory owns its own catalog store, separate from Configuration Management

## Status

Accepted

**Product rename note (2026-07-25):** The Hub page is now called **Resource Center** (formerly Service Directory). This ADR's **store-boundary decision is unchanged**. Slice artifact filenames and the physical table `DA_SERVICE_DIRECTORY_CATALOG` may retain the historical `service-directory` / `SERVICE_DIRECTORY` identifiers; user-facing name, route, and API path follow the Resource Center SDD amendment.

## Date

2026-07-25

## Context

The `service-directory` slice (product name: **Resource Center**) adds a Platform-shared Hub page that renders an administrator-maintained
catalog of destinations — documentation, tools, in-Hub workspaces, and source repositories — organised as
`directory scope → group → link`. The catalog must be editable by `DEVOPS_ADMIN` without a code release,
so it has to live in the database rather than in application code.

The repository already has three plausible homes for "admin-editable data", and choosing wrongly is
expensive to undo later:

| Existing home | What it is for today |
|---|---|
| `ConfigurationComponent` / `ConfigurationItem` | Runtime integration configuration — Jenkins, Ansible, and callback settings that the execution engine reads to actually run deployments. Scoped by application and SNOW group. |
| `ScopeDirectoryEntry` | Curated application / SNOW group / agent choices offered in the upload flow. Constrains what a user may submit. |
| A new dedicated table | Nothing yet. |

Navigation content shares one superficial trait with Configuration Management — an administrator edits
it — but nothing else. Configuration Management data is read by the execution path, is scoped by
application and SNOW group, is access-controlled by `AccessGrant`, and a bad value there breaks
deployments. Catalog content is read only by a browser rendering a page, is global rather than scoped,
is visible to every role, and a bad value there produces a broken hyperlink.

The word "scope" is also already overloaded in this codebase: `UserContext.scopes` (authorisation) and
`ScopeDirectoryEntry` (upload choices) both exist, and this slice introduces a third, unrelated meaning
(a directory section such as SDLC / Common / External). Without an explicit boundary decision, a future
change is likely to wire these together by name similarity alone.

A secondary question is the storage shape inside that dedicated home: one versioned JSON document row,
or three normalised tables (`SD_SCOPE`, `SD_GROUP`, `SD_LINK`).

## Decision

1. **Service Directory owns a dedicated persistence unit.** The catalog is stored in a new table,
   `DA_SERVICE_DIRECTORY_CATALOG`, owned by a new domain module `domain/servicedirectory/`.
2. **The catalog is never stored in `ConfigurationComponent`, `ConfigurationItem`, or
   `ScopeDirectoryEntry`,** and no `ConfigKey` constant is introduced for it. A contract test asserts
   that catalog mutations add no Configuration Management rows.
3. **Storage shape: a single versioned JSON document in a single row.** The catalog is always read whole,
   rendered whole, and versioned whole, so the document shape matches the access pattern. It uses the
   repository's existing character-large-object JSON conversion approach, already proven on both Oracle
   and H2.
4. **Concurrency is document-level optimistic locking.** A stale write returns `409 Conflict` and the
   client reloads; there is no merge and no last-write-wins.
5. **The API is Platform-shared, not agent-scoped.** Endpoints live under
   `/api/platform/service-directory` with no `agent` parameter, because the catalog is one global list
   rather than per-agent data.
6. **Mutations get their own audit action types** — `service_directory_update` and
   `service_directory_delete` — rather than reusing the Configuration Management action types. Reusing
   them would make a filtered audit review mix navigation edits with deployment-configuration edits.
7. **Naming discipline:** the domain type is `DirectoryScope`, never bare `Scope`, to keep it distinct
   from authorisation scopes and from `ScopeDirectoryEntry`.

## Alternatives Considered

| Alternative | Why not |
|---|---|
| Store the catalog as Configuration Management items | Puts page content in the store the execution path reads. It would inherit application/SNOW-group scoping and `AccessGrant` gating that the catalog does not want, force navigation content through a schema designed for integration settings, and blur which store is safe to change casually. |
| Extend `ScopeDirectoryEntry` | Different purpose entirely: that entity constrains what a user may submit in the upload flow. The name overlap is the only similarity, and following it would entrench the "scope" ambiguity. |
| Frontend-only static JSON (like `agentContributionDashboard.json`) | Fails the core requirement: administrators must change the catalog without a code release. |
| Three normalised tables inside the dedicated module | The only advantage this slice actually needs is row-level concurrency, and the write profile (a small admin group, edits measured in "per week") makes an explicit reload-and-retry acceptable. Everything else it buys — partial reads, per-entity SQL, independent ordering — is unused, while it costs three tables, two foreign keys, cascade rules, and a larger greenfield-schema delta. |
| Whole-catalog `PUT` instead of granular endpoints | Would make audit detail unanswerable ("something changed") and turn every concurrent edit into a lost update. |

## Consequences

### Positive

- Navigation content cannot break a deployment, and deployment configuration cannot be reshaped by a
  navigation edit. The blast radius of each store is clear.
- One table, one module, one migration — the boundary is easy to audit and easy to reverse.
- Hierarchical deletes are atomic document edits, with no cascade constraints to maintain.
- Audit reviewers can filter navigation changes separately from integration-configuration changes.

### Negative

- No per-entity SQL querying or reporting over the catalog. Nothing in the slice needs it today; adding
  it later means normalising.
- Two administrators editing simultaneously will see a `409` and have to reload. This is a real
  usability cost, accepted deliberately for the expected write volume.
- The catalog payload has no database-enforced schema; correctness depends entirely on the validator in
  the domain layer. That validator is therefore a first-class, fully tested unit rather than a helper.

### Neutral / Operational

- Adds Flyway migration `V20__add_service_directory_catalog.sql` and requires regenerating
  `docs/sql/ORACLE_CURRENT_SCHEMA.sql`.
- Adds two `AuditActionType` constants; no migration needed, since the column is stored as a string.
- Seeding is lazy application-side provisioning from a packaged resource, not SQL inserts, so the seed
  passes the same validation as an administrator edit.

## Review Triggers

Revisit this ADR if any of the following becomes true:

1. A requirement appears for partial catalog reads, per-link SQL reporting, or link-level history.
2. Concurrent admin edits become frequent enough that `409` responses are a recurring complaint.
3. The catalog grows past roughly a few hundred links, where shipping the whole document per page load
   stops being cheap.
4. A requirement appears to scope catalog visibility per application or SNOW group — at which point the
   authorisation model, not just the storage shape, needs rethinking.

## Related Documents

- `docs/01-requirements/service-directory-requirement.md` (SD-REQ-10)
- `docs/03-spec/service-directory-spec.md` (SD-FR-63, SD-FR-64; SD-OQ-04)
- `docs/04-architecture/service-directory-architecture.md`
- `docs/04-architecture/service-directory-data-model.md` §2 (storage-shape comparison)
- `docs/05-design/service-directory-design.md` (M1, M7)
- `docs/06-tasks/service-directory-tasks.md` (SD-T00, SD-T03, SD-T13, SD-T40)
- ADR-0001 (context engineering and ADRs)
