# Resource Center User Stories

**Slice:** `service-directory`
**Date:** 2026-07-25
**Status:** Regenerated via `req-to-user-story`; **Amended 2026-07-25** — product renamed to **Resource Center**; per-link `iconKey` whitelist (SD-REQ-15)
**Product name:** Resource Center (slice id `service-directory`)
**Source requirements:** `docs/01-requirements/service-directory-requirement.md` (SD-REQ-01 … SD-REQ-15)
**Granularity:** Capability Story Mode — eight capability-domain stories covering navigation, browsing, SDLC wayfinding, shared tooling, personal shortcuts, administration, audit, and boundary enforcement.

> ID note: this regeneration uses `SD-US-nn`. The earlier draft used `SD-0n`; the mapping is
> positional (`SD-01` → `SD-US-01`, …) and is recorded in
> `docs/00-context/service-directory-traceability.md`.

---

# User Story SD-US-01

**Title:** Reach Resource Center from Hub navigation

**Story:**
As an authenticated WWA user,
I want to open Resource Center from the Platform flyout and from the Home page,
so that I can reach shared tools and docs without leaving the Hub shell or hunting for a bookmark.

## Acceptance Criteria

1. **Given** I am authenticated and viewing any `/wwa/*` page
   **When** I open the Platform section of the navigation flyout
   **Then** I see a **Resource Center** entry alongside the other Platform capabilities, with no permission lock icon.

2. **Given** I am on the Home page
   **When** I look at the **Shared Controls** section
   **Then** I see a Resource Center card that links to the same destination.

3. **Given** I activate either entry
   **When** the page loads
   **Then** the browser URL is `/wwa/resource-center`, the page renders inside the existing Hub shell (sidebar, flyout, topbar intact), and the topbar section title reads "Resource Center".

4. **Given** I am not authenticated
   **When** I request `/wwa/resource-center` directly
   **Then** the existing router guard sends me to `/login` first, and returns me to the Hub after login.

## Notes / Assumptions

- Navigation is driven by the single existing registry `platformCapabilities` in `frontend/src/config/agentRegistry.ts:75-108`; both the flyout and the Home Shared Controls grid read from it, so one entry serves both surfaces.
- The entry is registered without an `accessPermission` value so it stays unlocked for every role, including `GUEST`.

## Dependencies

- `frontend/src/router/index.ts` route table and its `beforeEach` auth guard (lines 154-174).
- `frontend/src/views/WorkspaceLayout.vue` flyout rendering of `platformCapabilities` (lines 230-247).
- `frontend/src/views/WwaHomeView.vue` Shared Controls grid (lines 118-131).

## Out of Scope

- Reordering or restyling the existing Platform navigation entries.
- Adding a sidebar primary nav item (Resource Center is a Platform capability, not a workspace).

## Open Questions

- None.

---

# User Story SD-US-02

**Title:** Browse and filter the destination catalog

**Story:**
As a delivery team member,
I want to narrow the catalog by scope, link kind, and free-text search,
so that I can find the right destination in a few seconds instead of scanning every section.

## Acceptance Criteria

1. **Given** the catalog has loaded
   **When** I look at the scope filter
   **Then** I see an **All** chip plus one chip per enabled scope in `sortOrder` order — at minimum SDLC, Common, and External.

2. **Given** the **All** scope is selected
   **When** I select a specific scope chip
   **Then** only groups belonging to that scope remain visible, and the chip is shown as active.

3. **Given** any scope selection
   **When** I select a kind chip (`Docs / Confluence`, `Tools`, `WWA workspaces`, `GitHub / source`)
   **Then** only links of that kind remain, and groups left with zero matching links are hidden.

4. **Given** any scope and kind selection
   **When** I type text into search
   **Then** results are filtered case-insensitively against the link title, description, kind label, and kind, plus the parent group's name, description and owning agent, and the scope label.

5. **Given** a filter combination that matches nothing
   **When** the results update
   **Then** I see an empty-state message that names the active filters and offers a single action to clear them.

6. **Given** I am a keyboard user
   **When** I press `/` while focus is not in a text field and no dialog is open
   **Then** focus moves to the search input.

7. **Given** a link has a whitelisted `iconKey` (for example `github` or `arcad`)
   **When** I view its card
   **Then** the card icon area shows the matching local icon instead of the two-letter badge.

8. **Given** a link has no `iconKey`, or an unknown key that failed validation was never stored
   **When** I view its card
   **Then** the card keeps the existing kind-coloured letter badge (title initials, or `GH` for `repo`).

## Notes / Assumptions

- Scope and kind filters are single-select (exclusive), matching the prototype (`wwa-service-directory.html:1461-1466`, `1502`).
- **Correction from the earlier draft:** the draft claimed search matches the link URL. The accepted prototype does not search URLs (`wwa-service-directory.html:1443-1450`, `1590`), and searching raw URLs produces noisy matches. This story keeps URLs out of the search haystack; the requirement and spec were corrected to match.
- Filtering is entirely client-side over the single catalog payload — no server round trip per keystroke.
- **Amended 2026-07-25:** per-link icons use optional `iconKey` + frontend whitelist mapping (SD-REQ-15); no remote image URLs.

## Dependencies

- SD-US-01 (page must exist and load the catalog).

## Out of Scope

- Saved or shareable filter state in the URL query string.
- Multi-select scope or kind filters.
- Server-side search or pagination.
- Custom icon upload or arbitrary icon image URLs.

## Open Questions

- None.

---

# User Story SD-US-03

**Title:** Navigate SDLC stages and open each agent's source

**Story:**
As a new joiner or developer,
I want each SDLC stage presented in lifecycle order with its guideline docs, tools, workspace, and GitHub source,
so that I can learn how delivery flows and open the code of a published agent to start contributing.

## Acceptance Criteria

1. **Given** the SDLC scope is enabled and the active scope is **All** or **SDLC**
   **When** the page renders
   **Then** I see a stage strip listing the seven stages in order — Planning, Estimation, Discovery, Build, Testing, Deployment, Maintenance — each showing its order number and owning agent name.

2. **Given** the stage strip is visible
   **When** I select a stage
   **Then** the catalog narrows to that stage's group only, non-SDLC scopes are hidden for the duration of the stage focus, and the page scrolls to that group.

3. **Given** a stage is already selected
   **When** I select the same stage again
   **Then** the stage focus clears and all groups in the active scope return.

4. **Given** I select a scope other than SDLC
   **When** the view updates
   **Then** any stage focus is cleared and the stage strip is visually de-emphasised.

5. **Given** a stage group contains links of several kinds
   **When** I inspect it
   **Then** links are grouped under fixed sub-headings in this order: Docs / Confluence, Tools, WWA workspaces, GitHub / source (for newcomers).

6. **Given** a stage has a published tool with a `repo` link
   **When** I open that link
   **Then** the repository opens in a new browser tab with `noopener` protection, and the current Hub page stays loaded.

7. **Given** a `workspace` link such as the Deployment Agent workspace
   **When** I open it
   **Then** navigation happens inside the Hub via the client router — not in a new tab.

## Notes / Assumptions

- The seven stage keys align with the Agent Contribute Dashboard stage set already shipped in `frontend/src/config/agentContributionDashboard.json`.
- A stage group carries its own order number and agent name; the strip is rendered from group data, not from a hard-coded stage list (SD-REQ-02).
- Sub-heading order is a presentation rule taken from the prototype (`wwa-service-directory.html:1541-1544`); it is not driven by `sortOrder`.

## Dependencies

- SD-US-02 (filter chrome and catalog rendering).
- Seed content from SD-US-04 for stage links to exist on first load.

## Out of Scope

- Showing live agent status per stage — that remains the Agent Contribute Dashboard's job.
- Deep-linking to a specific stage via URL.

## Open Questions

- None.

---

# User Story SD-US-04

**Title:** Find shared platform and engineering systems in one place

**Story:**
As a developer,
I want Common and External destinations — including ARCAD, GitHub Enterprise, and in-Hub platform pages — listed in the directory,
so that I can reach core engineering and corporate systems without asking a colleague for the link.

## Acceptance Criteria

1. **Given** the seeded catalog on a fresh environment
   **When** I view the Common scope
   **Then** I see a **Platform** group with `workspace` links to in-Hub pages (at minimum Agent Contribute Dashboard and Configuration Management) and an **Engineering tools** group containing ARCAD and GitHub Enterprise as `tool` links.

2. **Given** the External scope
   **When** I view it
   **Then** I see enterprise destinations that open in a new tab.

3. **Given** a seeded link whose production URL has not been supplied yet
   **When** I view its card
   **Then** it is shown with a "URL pending" indicator and activating it does not navigate anywhere.

4. **Given** an administrator has replaced a pending URL with a real one
   **When** I reload the page
   **Then** the card becomes a normal, navigable link.

## Notes / Assumptions

- Pending seed URLs use the reserved `.invalid` top-level domain (RFC 2606) so they can never resolve to a real host; the UI treats a host ending in `.invalid` as pending. Hosts that merely contain the word "invalid" (for example `invalid-tool.acme.com`) are ordinary links.
- Real ARCAD and GitHub Enterprise URLs are collected under task SD-T02 before release (SD-OQ-02).
- Contributor and process guides live under SDLC stage `docs` links rather than being duplicated under Engineering tools.

## Dependencies

- Seed catalog implementation (SD-REQ-06).
- SD-OQ-02 resolution before the slice can be called release-ready.

## Out of Scope

- Health checks or uptime indicators for external systems.
- Per-environment (SIT / UAT / PROD) variants of the same tool link.

## Open Questions

- SD-OQ-02: production ARCAD and GitHub Enterprise URLs.

---

# User Story SD-US-05

**Title:** Re-open frequently used destinations quickly

**Story:**
As a frequent Hub user,
I want the links I recently opened kept in a compact strip at the top of the page,
so that my day-to-day destinations are one click away without filtering or searching.

## Acceptance Criteria

1. **Given** I open a link from the catalog
   **When** the navigation happens
   **Then** that link is recorded as my most recent entry for this browser.

2. **Given** I have recent entries
   **When** I return to the page
   **Then** I see up to 8 recent chips, most recent first, each showing the link title.

3. **Given** I re-open a link that is already in the list
   **When** the list updates
   **Then** it moves to the front without creating a duplicate, and the list still holds at most 8 entries.

4. **Given** I have never opened a link in this browser
   **When** the page loads
   **Then** the Recently used region shows a short empty message and no fabricated entries.

5. **Given** I use the clear action
   **When** it completes
   **Then** the strip empties and the stored list is removed from this browser only.

6. **Given** an administrator deleted a link that was in my recent list
   **When** I reload the page
   **Then** the stale entry is silently dropped rather than rendering a broken chip.

7. **Given** I sign in from a different browser or machine
   **When** I open the page
   **Then** my Recently used list may be empty, because MVP storage is per-browser.

## Notes / Assumptions

- Recently used is stored in browser `localStorage` under a versioned key. This is the **first** `localStorage` usage in this frontend — a grep of `frontend/` found none — so this story establishes the key-naming convention `wwa.resourceCenter.recent.v1`.
- Recently used is a UI convenience, not an audited event (SD-NFR-04).
- The prototype seeds three fake recent ids on first load; production must not (see requirement §8).

## Dependencies

- SD-US-02 / SD-US-03 for link opening behavior.

## Out of Scope

- Server-side sync of Recently used across devices.
- Usage analytics, popularity ranking, or team-wide "most used" lists.
- Pinned favourites.

## Open Questions

- None.

---

# User Story SD-US-06

**Title:** Maintain the catalog as DEVOPS_ADMIN

**Story:**
As a `DEVOPS_ADMIN`,
I want to add, edit, and delete scopes, groups, and links from the page itself,
so that the directory stays accurate without waiting for a code release.

## Acceptance Criteria

1. **Given** I hold the `DEVOPS_ADMIN` role
   **When** I open Resource Center
   **Then** I see a manage toggle; enabling it reveals Add scope / Add group / Add link actions plus per-link Edit and Delete controls.

2. **Given** manage mode is on
   **When** I view a group that currently has no links of a given kind
   **Then** the group is still shown with an inline "add" affordance for that kind, so I can fill empty slots.

3. **Given** I submit a new scope with a key that already exists
   **When** validation runs
   **Then** the save is rejected with a field-level message and nothing is persisted.

4. **Given** I submit a link with an empty title or an unsafe / malformed URL
   **When** validation runs
   **Then** the save is rejected with a field-level message naming the offending field.

5. **Given** I add a scope, a group inside it, and a link inside that group
   **When** another user reloads the page
   **Then** they see the new structure — no deploy required.

6. **Given** I delete a scope that still contains groups and links
   **When** I confirm the deletion
   **Then** the scope and all its descendants are removed together, and the confirmation told me how many groups and links would be removed.

7. **Given** I try to delete one of the seeded system scopes (`sdlc`, `common`, `external`)
   **When** the request is evaluated
   **Then** it is rejected with an explanatory error; disabling the scope or changing its displayed title remains allowed, while its key stays fixed.

8. **Given** I do **not** hold `DEVOPS_ADMIN`
   **When** I open the page
   **Then** no manage affordance is shown, and a direct API mutation request is rejected with HTTP 403.

9. **Given** another administrator saved a change after my page loaded
   **When** I save an edit or a deletion
   **Then** I receive a conflict error telling me to reload, and my stale write is not applied.

10. **Given** another administrator saved a change after my page loaded
    **When** I *add* a new scope, group, or link
    **Then** it is accepted, because adding cannot overwrite anyone else's work — the conflict error is reserved for edits and deletions that could.

11. **Given** I create or edit a link
    **When** I open the link form
    **Then** I can optionally pick an `iconKey` from a fixed whitelist dropdown (or leave it empty for the letter badge).

12. **Given** I submit a link with an `iconKey` that is not on the whitelist
    **When** validation runs
    **Then** the save is rejected with a field-level message on `iconKey` and nothing is persisted.

## Notes / Assumptions

- The role check is imperative and server-side (`user.hasRole("DEVOPS_ADMIN")` → `ForbiddenAppException`), matching the existing pattern in `ConfigurationController.java:39-48`. `@PreAuthorize` is not used anywhere in this codebase.
- The prototype's topbar role `<select>` is a demo device only; production reads roles from the real session (requirement §8).
- Conflict detection needs the client to send back the catalog version it last read on every edit and deletion. Storage-level optimistic locking alone would not satisfy AC 9: it only catches writes that overlap in flight, whereas the case in AC 9 is two saves minutes apart, which storage-level locking accepts silently. The existing `GlobalExceptionHandler` maps both to HTTP 409. See spec SD-FR-44, SD-FR-67, and SD-FR-68.
- **Amended 2026-07-25:** link icons are optional whitelist keys only (SD-REQ-15); no upload or remote icon URL field.

## Dependencies

- Server-side persistence and write API (SD-REQ-08).
- Existing auth chain and `UserContext`.

## Out of Scope

- Draft / publish workflow, approval gates, or scheduled publication.
- Bulk import or CSV upload of links.
- Reordering by drag and drop (order is edited as a numeric field in MVP).
- Role delegation — no new role or permission is introduced by this slice.
- Custom icon upload or arbitrary icon image URLs.

## Open Questions

- None.

---

# User Story SD-US-07

**Title:** Review who changed the catalog

**Story:**
As an auditor or platform owner,
I want every catalog mutation recorded in the existing Audit Log,
so that I can see who added, changed, or removed a destination and when.

## Acceptance Criteria

1. **Given** a `DEVOPS_ADMIN` successfully creates, updates, or deletes a scope, group, or link
   **When** the mutation commits
   **Then** exactly one audit entry is written, attributed to that operator, with `actor_kind = HUMAN`.

2. **Given** an audit entry for this slice
   **When** I inspect it in the Audit Log page
   **Then** I can tell which entity type was affected (scope / group / link), which entity it was, and which operation was performed.

3. **Given** a user only browses, filters, or updates Recently used
   **When** no catalog mutation occurs
   **Then** no audit entry is created.

4. **Given** a mutation is rejected for validation, authorization, or conflict reasons
   **When** the request fails
   **Then** no audit entry is written for the failed attempt.

5. **Given** the audit subsystem fails while a valid catalog mutation is committing
   **When** the request completes
   **Then** the catalog change is still applied and the audit failure is logged server-side rather than surfaced as a user error.

## Notes / Assumptions

- Audit uses the existing `AuditLoggerService`. `AuditActionType` is a closed enum stored as a string (`contracts/enums/AuditActionType.java:4-24`), so this slice adds dedicated constants rather than overloading `config_update` / `config_delete`, which currently mean Configuration Management changes.
- Identifying details travel in the audit entry's `context_payload` map, which the audit DTO already exposes. The `target_type` / `target_id` columns exist on `AuditLogEntry` but no code writes them and the DTO does not expose them, so writing them would be invisible to auditors today.
- The existing `AuditLoggerService` write path is already `REQUIRES_NEW`, which satisfies criterion 5 without new infrastructure.

## Dependencies

- SD-US-06 (mutations must exist to be audited).
- Existing Audit Log page and API.

## Out of Scope

- A dedicated Resource Center change-history UI (the shared Audit Log page is the surface).
- Field-level before/after diffs for every attribute.
- Audit retention or export changes.

## Open Questions

- None.

---

# User Story SD-US-08

**Title:** Keep the catalog out of Configuration Management

**Story:**
As a product owner,
I want Resource Center content stored in its own place with its own admin surface,
so that navigation content never mixes with Jenkins / Ansible runtime configuration and cannot be corrupted by config edits.

## Acceptance Criteria

1. **Given** the implemented slice
   **When** I inspect where the catalog is stored
   **Then** it lives in its own dedicated table, and no rows are added to the Configuration Management stores `DA_CONFIGURATION_COMPONENT`, `DA_CONFIGURATION_ITEM`, or `DA_SCOPE_DIRECTORY`.

2. **Given** the Configuration Management page
   **When** an admin opens its Component, Scope Directory, and Configuration tabs
   **Then** Resource Center entities do not appear there and its behavior is unchanged by this slice.

3. **Given** an admin needs to change a directory link
   **When** they perform the change
   **Then** they use the Resource Center page and its API, not the Config Admin editors.

4. **Given** the API surface
   **When** the slice is reviewed
   **Then** Resource Center endpoints are Platform-shared (`/api/platform/...`) and carry no agent parameter, because the catalog is cross-agent.

## Notes / Assumptions

- The boundary is a durable architectural decision, so it is captured in proposed `ADR-0010` rather than only in this story.
- The existing `ConfigKey` enum backs `ConfigurationItem` rows; adding a Resource Center key there would violate this story and must be avoided. Note that the Agent Contribute Dashboard *does* store its stage-status overrides as a `ConfigurationItem` — that precedent must not be copied for the catalog.
- "Scope" is an overloaded word in this repository: `ScopeDirectoryEntry` / `AccessScope` mean application + SNOW-group access scoping, while a Resource Center **scope** is a catalog category. The names must not be conflated in code or docs.

## Dependencies

- Acceptance of `ADR-0010`.

## Out of Scope

- Refactoring or migrating existing Configuration Management data.
- Changing the Agent Contribute Dashboard's existing status storage.

## Open Questions

- SD-OQ-04: whether `ADR-0010` must be accepted before implementation starts.

---

## Story Coverage Summary

| Story | Capability domain | Requirements covered |
|---|---|---|
| SD-US-01 | Navigation and shell placement | SD-REQ-01 |
| SD-US-02 | Catalog browsing and filtering | SD-REQ-02, SD-REQ-05 |
| SD-US-03 | SDLC wayfinding and link kinds | SD-REQ-03, SD-REQ-04 |
| SD-US-04 | Shared and external tooling, seed content | SD-REQ-06 |
| SD-US-05 | Personal shortcuts | SD-REQ-09 |
| SD-US-06 | Administration | SD-REQ-07, SD-REQ-08, SD-REQ-11, SD-REQ-12 |
| SD-US-07 | Audit | SD-REQ-08 |
| SD-US-08 | Data ownership boundary | SD-REQ-10, SD-REQ-14 |
| SD-US-01, SD-US-06 | Guest read-only posture | SD-REQ-13 |
