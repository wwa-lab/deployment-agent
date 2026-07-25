# Resource Center Requirement

**Slice:** `service-directory`
**Date:** 2026-07-25
**Status:** Regenerated via `wwa-sdd-generate-all`; **Amended 2026-07-25** — product renamed to **Resource Center** (formerly Service Directory); optional per-link `iconKey` whitelist (SD-REQ-15)
**Product name:** Resource Center  
**Slice id / artifact filenames:** `service-directory` (unchanged for continuity)
**Owner:** WWA-Atlas Hub (Platform)
**Language:** English-only (ADR-0009)
**Prototype (accepted UX baseline):** `docs/prototypes/wwa-service-directory.html`

> This document replaces the earlier hand-written draft. It was produced through the mandatory
> project-local SDD skill chain with a codebase grounding pass, so claims about existing WWA code
> are verified or explicitly tagged.

---

## 1. Slice Contract

| Field | Value |
|---|---|
| Goal | Give every Hub user one Platform page that answers "where do I go?" for SDLC tooling, shared platforms, and external systems — driven by admin-maintained configuration instead of code |
| Slice | `service-directory` (artifact id; product name is **Resource Center**, formerly Service Directory) |
| Scope (in) | New Platform page `/wwa/resource-center`; config-driven `scopes → groups → links` catalog; scope / kind / SDLC-stage / text filters; client-local Recently used; `DEVOPS_ADMIN` manage with audited mutations; new dedicated persistence + Platform REST API; seed catalog |
| Scope (out) | Any storage inside Configuration Management entities; link health probes; auto-discovery from GitHub/Confluence; server-side Recently used; iframe embedding; per-user favourites; the prototype's mock role switch; arbitrary icon URL upload or remote image hosting |
| Sources of truth | This requirement, `docs/03-spec/service-directory-spec.md` (behavior), `docs/06-tasks/service-directory-tasks.md` (execution), the prototype (UX), proposed `ADR-0010` (store boundary) |
| Acceptance | See §7 Success Criteria; per-story acceptance in `docs/02-user-stories/service-directory-user-stories.md`; consolidated matrix in the spec |
| Verification | `mvn test` (backend + new controller test), `cd frontend && npm run build`, documented manual walkthrough against the prototype |
| Constraints | Deny-by-default admin writes (`DEVOPS_ADMIN` enforced server-side); guest mutations blocked; audit on mutations only; no catalog data in Configuration Management; Platform-shared (not an agent workspace); HITL rails in `CLAUDE.md` untouched |

---

## 2. Background

WWA Agent Hub already owns several Platform-shared pages (Agent Contribute Dashboard, Template
Management, Configuration Management, Audit Log, Access Management — all registered in
`frontend/src/config/agentRegistry.ts:75-108`). What it does not own is a **destination catalog**.

Today a delivery engineer finds internal tools through tribal knowledge, personal bookmarks, or the
Agent Contribute Dashboard's per-stage `resourceLinks`, which are hard-coded in a static frontend
file (`frontend/src/config/agentContributionDashboard.json`) and cannot be edited by an admin at
runtime — the dashboard's backend API only persists stage **status** overrides
(`AgentContributionDashboardController.java:13-33`).

Consequences observed:

- New joiners cannot find the GitHub source of a published WWA agent, so they cannot start contributing.
- Shared engineering systems (ARCAD, GitHub Enterprise) have no canonical entry point in the Hub.
- Adding a new link category requires a frontend code change and a release.

Resource Center closes that gap with an admin-maintainable, audited catalog.

---

## 3. Product Objectives

1. One Hub page for SDLC, shared, and external destinations.
2. Configuration-driven structure (`scopes → groups → links`) so new categories need **no code deploy**.
3. Multiple links per SDLC stage — guideline docs, tools, in-Hub workspaces, and GitHub source.
4. `DEVOPS_ADMIN` maintains the catalog; every mutation is auditable.
5. A personal Recently used shortcut list for repeat access.
6. A store that is explicitly **separate** from Configuration Management (Jenkins/Ansible runtime config).

---

## 4. Users And Roles

Roles are the existing `UserRole` union (`frontend/src/types/index.ts:16`) backed by
`UserContext.roles` (`src/main/java/com/wwa/agenthub/contracts/UserContext.java:10-17`).

| Role | Need from this slice |
|---|---|
| `DEVELOPER` / `TL` | Browse, filter, open links; use Recently used |
| New joiner (any authenticated role) | Find ARCAD / GitHub Enterprise and per-agent `repo` links for onboarding |
| `DEVOPS_ADMIN` | Create / update / delete scopes, groups, links; audited |
| `AUDIT` / `MANAGEMENT` | Review catalog change history in the existing Audit Log page |
| `GUEST` | Read-only browse (see SD-REQ-13 and SD-OQ-01) |

---

## 5. Functional Requirements

| ID | Requirement |
|---|---|
| **SD-REQ-01** | Expose Resource Center at route `/wwa/resource-center` inside the existing Hub shell, reachable from both the Platform flyout and the Home "Shared Controls" section. |
| **SD-REQ-02** | Render the catalog from persisted configuration — `scopes`, `groups`, `links` — with no hard-coded scope, group, or link enumeration in application code. |
| **SD-REQ-03** | Support four link kinds: `docs`, `tool`, `workspace`, `repo`. `workspace` links navigate inside the Hub; the other kinds open in a new browser tab. |
| **SD-REQ-04** | Support many links per group, and model each of the seven SDLC stages as one group under the `sdlc` scope carrying its stage order and owning agent name. |
| **SD-REQ-05** | Provide filtering by scope, by link kind, by SDLC stage, and by free-text search across catalog display text. |
| **SD-REQ-06** | Ship a seed catalog covering the seven SDLC stages, Common (Platform + Engineering tools including ARCAD and GitHub Enterprise), and External, so the page is useful on first load. |
| **SD-REQ-07** | Allow only `DEVOPS_ADMIN` to create, update, or delete scopes, groups, and links; enforce this server-side and hide manage affordances for everyone else. |
| **SD-REQ-08** | Persist catalog changes server-side so they are visible to all users, and write an Audit Log entry with `actor_kind = HUMAN` for every successful mutation. |
| **SD-REQ-09** | Provide a Recently used list of the current browser user's most recently opened links, capped at 8 entries, clearable by the user. |
| **SD-REQ-10** | Store the catalog in its own dedicated persistence, never in `ConfigurationComponent`, `ConfigurationItem`, or `ScopeDirectoryEntry`. |
| **SD-REQ-11** | Reject unsafe or malformed link targets at the API boundary (no `javascript:`, `data:`, or `vbscript:` schemes; `workspace` links restricted to in-Hub paths). |
| **SD-REQ-12** | Preserve the three seeded system scopes (`sdlc`, `common`, `external`) against deletion, while still allowing them to be retitled, reordered, and disabled. Their keys stay fixed, since behavior and audit history reference them. |
| **SD-REQ-13** | Allow `GUEST` sessions to read the catalog while all mutations remain blocked. `[DEFAULT — revisit if wrong; see SD-OQ-01]` |
| **SD-REQ-14** | Keep SDLC guideline / feedback link content aligned with the Agent Contribute Dashboard by manual alignment for MVP; do not introduce a dual-write path without a defined sync rule. |
| **SD-REQ-15** | Allow each link an optional `iconKey` chosen from a fixed platform whitelist so catalog cards can show a distinct local icon per tool or destination. Omitting the key, or supplying an unknown key, must fall back to the existing kind-derived letter badge. Do not accept icon image URLs or uploaded assets in MVP. |

---

## 6. Non-Functional Requirements

| ID | Category | Requirement |
|---|---|---|
| **SD-NFR-01** | Security | Admin mutations require `DEVOPS_ADMIN` verified from the server-side `UserContext`; a client-supplied role is never trusted. |
| **SD-NFR-02** | Security | Guest write attempts stay blocked by the existing `GuestReadOnlyFilter` (`src/main/java/com/wwa/agenthub/web/security/GuestReadOnlyFilter.java:29-63`); the slice adds no logout-style exception. |
| **SD-NFR-03** | Security | Outbound link targets are validated on write, and `repo` / `tool` / `docs` links open with `rel="noopener"`. |
| **SD-NFR-04** | Auditability | Only mutations are audited. Browsing, filtering, and Recently used produce no audit entries. |
| **SD-NFR-05** | Reliability | Audit writes must not roll back or fail the catalog mutation (existing `REQUIRES_NEW` pattern, `AuditLoggerService.java:110-117`). |
| **SD-NFR-06** | Reliability | Concurrent admin writes must not silently lose data; a stale write is rejected with HTTP 409 rather than overwriting. |
| **SD-NFR-07** | Performance | The catalog is a single small read-mostly document; one GET must serve the whole page with no N+1 fan-out. Target catalog size for MVP: ≤ 20 scopes, ≤ 100 groups, ≤ 600 links. |
| **SD-NFR-08** | Environment | Must work on Oracle (`default` profile) and H2 (`local`, `test`) using the existing CLOB/JSON converter approach. |
| **SD-NFR-09** | Privacy | Recently used stays in the browser; no per-user browsing history is persisted server-side in MVP. |
| **SD-NFR-10** | UX consistency | Use the existing Hub design tokens in `frontend/src/assets/main.css:7-28`; do not introduce a third visual language. |

---

## 7. Success Criteria

1. Any authenticated user opens Resource Center from the flyout or Home and sees SDLC, Common, and External destinations.
2. A `DEVOPS_ADMIN` adds a new scope, group, and link and other users see them after reload — with no code deploy.
3. Publishing a WWA agent tool includes attaching a `repo` link under the owning SDLC stage, and a new joiner can find it.
4. Every admin mutation appears in the existing Audit Log page and identifies the affected catalog entity.
5. A non-admin sees no manage affordances, and direct API mutation attempts return HTTP 403.
6. Inspecting Configuration Management shows no Resource Center catalog data.
7. The delivered page matches the accepted prototype's structure and interaction model, minus the prototype-only artifacts listed in §8.
8. Links with a whitelisted `iconKey` render that local icon on the card; links without one keep the letter badge.

---

## 8. Explicitly Not Shipped From The Prototype

The prototype is a UX baseline, not an implementation reference. These parts must **not** ship:

| Prototype artifact | Reason | Production replacement |
|---|---|---|
| Topbar role `<select>` switch (`wwa-service-directory.html:1371-1376`) | Fake auth | Real session roles from the user store |
| `localStorage` catalog store (`DATA_KEY`, lines 1168, 1286-1301) | Client-owned data | Server persistence + REST API |
| Pre-seeded fake Recently used ids (lines 1409-1411) | Fabricated user history | Empty state until the user opens a link |
| Example `security` scope (line 1178) | Demo content | Admin-created after go-live if wanted |
| `#` placeholder hrefs | Dead links | Real URLs, or reserved placeholder handling per SD-T02 |
| `confirm()` + toast notifications | No such primitives exist in the app | Existing dialog + `.alert` banner patterns |

---

## 9. Dependencies

**Upstream (must exist — all verified present):**

- Session auth chain: `SessionAuthFilter` → `HeaderAuthFilter` → `GuestReadOnlyFilter` (`config/SecurityConfig.java:44-46`)
- `UserContext.hasRole(String)` (`contracts/UserContext.java:56-58`)
- `AuditLoggerService` (`domain/audit/AuditLoggerService.java`) and `AuditActionType` (`contracts/enums/AuditActionType.java:4-24`)
- `JsonAttributeConverter` for Oracle/H2 CLOB JSON (`util/JsonAttributeConverter.java:18-26`)
- Flyway migration chain, latest `V19__add_scope_directory_agent.sql`; next free version is `V20`
- Hub shell + registry: `views/WorkspaceLayout.vue`, `config/agentRegistry.ts`

**Downstream:**

- Audit Log page consumes the new action types.
- Agent Contribute Dashboard link content should stay aligned (SD-REQ-14).

---

## 10. Open Questions

| ID | Question | Default taken | Owner |
|---|---|---|---|
| **SD-OQ-01** | May a `GUEST` session read the Resource Center, or should guests be redirected away from the page? | Default: guests may read (`SD-REQ-13`). Guests already read other Platform pages and all writes are blocked by `GuestReadOnlyFilter`. | Product / Security |
| **SD-OQ-02** | Production URLs for ARCAD and GitHub Enterprise. | Seed ships reserved `.invalid` placeholders rendered as non-navigable "URL pending"; replaced under SD-T02 before release. | Ops / Platform |
| **SD-OQ-03** | Should SDLC guideline / feedback links become a shared source with the Agent Contribute Dashboard? | Default: manual alignment for MVP (`SD-REQ-14`); a shared source is a later slice. | Product |
| **SD-OQ-04** | Does the store boundary decision need a formal ADR before implementation? | Default: yes — `ADR-0010` is proposed with this set and needs acceptance. | Architecture |

---

## 11. Traceability

| Artifact | Path |
|---|---|
| User stories | `docs/02-user-stories/service-directory-user-stories.md` |
| Spec (behavior source of truth) | `docs/03-spec/service-directory-spec.md` |
| Architecture | `docs/04-architecture/service-directory-architecture.md` |
| Data flow | `docs/04-architecture/service-directory-data-flow.md` |
| Data model | `docs/04-architecture/service-directory-data-model.md` |
| Design | `docs/05-design/service-directory-design.md` |
| API guide | `docs/05-design/contracts/service-directory-API_IMPLEMENTATION_GUIDE.md` |
| Tasks | `docs/06-tasks/service-directory-tasks.md` |
| Traceability | `docs/00-context/service-directory-traceability.md` |
| Store boundary decision | `docs/00-context/decisions/ADR-0010-service-directory-owns-its-catalog-store.md` (Accepted) |
| Prototype | `docs/prototypes/wwa-service-directory.html` |
