# Agent/Tool Registry & Contribution Board User Stories

## Overview

This document defines the MVP user stories for a lightweight Agent/Tool Registry and Contribution Board in the WWA platform.

The product goal is intentionally narrow:

- make ownership easy to see
- make team contribution more visible

This MVP is **not** a dynamic runtime onboarding system for new workspaces.

---

## Data Model Context

- One new entity: `CapabilityRegistration` (table `DA_CAPABILITY_REGISTRATION`)
- Registry entries represent platform agents or tools
- Existing workflow entities (Release Flow, Request, Task) remain unchanged
- Activity metrics reuse existing `Request.agent` and related task data for `AGENT` entries only

---

## Relationship to Existing Capabilities

| Existing Capability | Impact |
|---|---|
| Static agent routes and frontend router | Unchanged |
| Existing per-agent controllers and stores | Unchanged |
| WWA Home Page and sidebar flyout | Add entry points to Registry and Contribution Board only |
| Shared Audit Log | Extended with registry mutation entries |
| Existing request/task data model | Reused for lightweight contribution metrics |

---

# User Stories

---

## User Story REG-1

**Title**
Register an agent or tool with clear ownership

**Story**
As a DevOps Admin,
I want to register an agent or tool with owner information,
so that users can clearly see who is responsible for each platform capability.

**Acceptance Criteria**

1. Given the DevOps Admin navigates to the Registry page at `/wwa/registry`,
   When the page loads,
   Then a "Register Entry" button is visible in the page header.

2. Given the DevOps Admin clicks "Register Entry",
   When the form opens,
   Then the form contains fields for: registry key, name, entry type (`AGENT` or `TOOL`), description, owner employee ID, owner display name, optional support contact, optional link, and optional note.

3. Given the DevOps Admin fills in the required fields and submits,
   When the system processes the request,
   Then a new registry entry is created with status `ACTIVE` and appears in the registry list.

4. Given the DevOps Admin submits a registry key that already exists,
   When the system validates the request,
   Then the system rejects the creation with a conflict error.

5. Given the DevOps Admin submits the form without owner employee ID or owner display name,
   When the system validates the request,
   Then the system rejects the submission with a validation error because owner information is required for new entries.

6. Given a non-admin user navigates to the Registry page,
   When the page loads,
   Then the "Register Entry" button is not visible.

**Notes / Assumptions**

- A registry entry documents an existing capability; it does not create runtime functionality.
- Owner information is required for all newly created entries.
- Support contact is optional in MVP.
- The optional link can point to an existing workspace route or an existing tool URL.

**Dependencies**

- `DA_CAPABILITY_REGISTRATION` table and JPA entity
- REST API at `POST /api/platform/registry`
- DEVOPS_ADMIN role gate for create operations

**Out of Scope**

- Automatic creation of backend controllers, frontend routes, or stores
- Bulk registration/import

**Open Questions**

- Should link validation allow both internal routes and external URLs in MVP, or only internal links?

---

## User Story REG-2

**Title**
View the ownership registry

**Story**
As an authenticated user,
I want to view a simple registry of agents and tools with owner information,
so that I can quickly find who owns what on the WWA platform.

**Acceptance Criteria**

1. Given the user navigates to `/wwa/registry`,
   When the page loads,
   Then a table is displayed showing: name, type, owner, support contact, status, optional link, and last updated date.

2. Given the registry contains both active and inactive entries,
   When the user views the table,
   Then the status of each entry is clearly visible.

3. Given the user is not a DEVOPS_ADMIN,
   When the user views the registry,
   Then the page is read-only and no admin actions are shown.

4. Given an entry contains a link,
   When the user clicks the link,
   Then the system opens the linked workspace or tool page.

5. Given the Registry feature is available in the platform,
   When the user views the home page or sidebar flyout,
   Then the Registry page is visible as a shared platform capability.

6. Given the feature is first launched with initial platform data,
   When the user opens the Registry page,
   Then the registry includes the existing `deployment-agent` and `testing-agent` entries.

7. Given there are no registry entries,
   When the user opens the Registry page,
   Then the page shows a clear empty state message.

**Notes / Assumptions**

- The primary value of the page is visibility, not workflow execution.
- The page should stay lightweight and easy to scan.

**Dependencies**

- REST API at `GET /api/platform/registry`
- Frontend route at `/wwa/registry`
- Shared navigation entry for the Registry page

**Out of Scope**

- Complex filtering, export, or reporting
- Dynamic workspace generation from registry data

**Open Questions**

None.

---

## User Story REG-3

**Title**
Maintain ownership information as capabilities change

**Story**
As a DevOps Admin,
I want to update registry entries and deactivate outdated ones,
so that ownership and platform inventory stay current over time.

**Acceptance Criteria**

1. Given the DevOps Admin views the registry table,
   When the admin clicks "Edit" on a row,
   Then a pre-populated form opens with the current metadata.

2. Given the DevOps Admin updates owner information or other editable metadata,
   When the form is submitted successfully,
   Then the registry list refreshes and shows the updated values.

3. Given the DevOps Admin deactivates an active entry,
   When the action succeeds,
   Then the entry remains in the registry with status `INACTIVE`.

4. Given the DevOps Admin reactivates an inactive entry,
   When the action succeeds,
   Then the entry status changes back to `ACTIVE`.

5. Given a non-admin user views the registry,
   When the page renders,
   Then edit and activate/deactivate actions are not available.

**Notes / Assumptions**

- Deactivation is a lifecycle/status change, not a hard delete.
- Inactive entries remain visible for transparency and historical context.

**Dependencies**

- REST API at `PATCH /api/platform/registry/{registryKey}`
- REST API at `POST /api/platform/registry/{registryKey}/activate|deactivate`

**Out of Scope**

- Hard deletion
- Ownership approval workflow

**Open Questions**

- Should the UI show inactive entries by default, or only when a simple status filter is enabled?

---

## User Story REG-4

**Title**
View the contribution board by owner

**Story**
As an authenticated user,
I want to view a contribution board grouped by owner,
so that the team can see platform ownership and contribution visibility in one place.

**Acceptance Criteria**

1. Given the user navigates to `/wwa/contribution-board`,
   When the page loads,
   Then a summary section shows total registered entries, total active entries, and total distinct owners.

2. Given owners have registered entries,
   When the contribution board loads,
   Then the board displays a section or card for each owner showing:
   owner name, employee ID, entries owned, and total owned-entry count.

3. Given an owner has one or more `AGENT` entries,
   When the contribution board loads,
   Then the board also shows total request count and total task count across those owned agent entries.

4. Given an owner has `TOOL` entries without runtime activity data,
   When the board loads,
   Then the entries are still shown under that owner and their activity is displayed as zero or no-data in a clear way.

5. Given some legacy or backfilled entries are missing owner information,
   When the board loads,
   Then those entries appear under an "Unassigned" section until the data is fixed.

6. Given the Contribution Board feature is available,
   When the user views the home page or sidebar flyout,
   Then the Contribution Board appears as a shared platform capability.

**Notes / Assumptions**

- The board is primarily for visibility and recognition.
- Activity metrics are supporting signals, not a full performance ranking system.
- Request/task metrics are sufficient for MVP; release-flow ranking is intentionally excluded.

**Dependencies**

- REST API at `GET /api/platform/contributions`
- Existing request/task repositories for aggregate counts
- Frontend route at `/wwa/contribution-board`

**Out of Scope**

- Trend charts, monthly leaderboards, scores, badges, or rewards
- Per-user action attribution beyond ownership grouping

**Open Questions**

- Should the default owner ordering be alphabetical, or by owned-entry count first?

---

## User Story REG-5

**Title**
Record registry changes in the shared audit log

**Story**
As an Audit or management user,
I want registry changes to be recorded in the shared audit log,
so that platform ownership changes remain traceable.

**Acceptance Criteria**

1. Given a DevOps Admin creates a registry entry,
   When the operation succeeds,
   Then the system writes an audit log entry for the creation.

2. Given a DevOps Admin updates an existing registry entry,
   When the operation succeeds,
   Then the system writes an audit log entry for the update.

3. Given a DevOps Admin activates or deactivates an entry,
   When the operation succeeds,
   Then the system writes an audit log entry for the lifecycle change.

4. Given a registry mutation fails,
   When the system rejects the request,
   Then no success audit entry is written for that failed mutation.

5. Given an Audit user opens the Audit Log page,
   When the user reviews records,
   Then registry mutations are visible alongside other platform audit events.

**Notes / Assumptions**

- Registry mutations should follow the same shared audit pattern already used elsewhere in WWA.

**Dependencies**

- Existing `AuditLoggerService`
- Audit action types for registry create, update, activate, and deactivate

**Out of Scope**

- A dedicated registry-only audit page
- Detailed before/after diff rendering in MVP

**Open Questions**

None.

---

## Summary

These user stories define a deliberately lightweight MVP:

1. Register an agent/tool with clear ownership
2. Let everyone view the ownership registry
3. Keep ownership data current over time
4. Show contribution visibility by owner
5. Audit the registry changes

The product intent is:
**make ownership obvious and contribution visible, without expanding this feature into dynamic workspace provisioning.**
