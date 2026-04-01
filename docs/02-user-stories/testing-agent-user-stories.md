# Testing Agent User Stories

## Overview

This document defines the MVP user stories for Testing Agent under the WWA Agent Workspace Hub.
Testing Agent is the second agent workspace, mirroring the Deployment Agent workflow but scoped to testing activities. It reuses the same domain model (Release Flow, Request, Task), shared platform capabilities, and human-in-the-loop control pattern.

The main MVP objective is:
**Provide a dedicated testing workspace with full data isolation from Deployment Agent, while reusing all existing domain logic and shared capabilities.**

---

## Data Model Hierarchy

Same as Deployment Agent:

- A Release Flow contains one or more Requests.
- Each Request contains one or more Tasks.
- Data isolation is achieved via `Request.agent = "testing-agent"`.

---

## Relationship to Deployment Agent User Stories

Testing Agent reuses many capabilities already defined in Deployment Agent user stories. The table below maps which Deployment Agent stories apply as-is vs which need Testing Agent-specific versions.

| Deployment Agent Story | Testing Agent Treatment |
|---|---|
| US-1: Access workspace navigation | **New story needed** (TA-1) — Testing Agent entry in nav |
| US-2: Upload Excel | **New story needed** (TA-2) — agent tagging on upload |
| US-3: Create/update Release Flow | **Reused as-is** — same service, agent tag inherited from request |
| US-4: View Release Flow summary | **New story needed** (TA-3) — filtered by agent |
| US-5: View Release Flow details | **Reused as-is** — same detail view structure |
| US-6: View task details | **Reused as-is** — same task display |
| US-7: Edit task input | **Reused as-is** — same edit behavior |
| US-8: Execute decisions | **Reused as-is** — same decision control |
| US-9: Audit logging | **New story needed** (TA-4) — agent name in audit entries |
| US-10: Configuration | **Reused as-is** — shared capability |
| US-11: Audit log view | **Reused as-is** — shared capability |
| US-12–13: Template management | **Reused as-is** — shared capability |
| US-14: Navigation | **Extended** (TA-1) — Testing Agent added to flyout |
| US-15–20: Task activity, gates, permissions | **Reused as-is** — same behavior |
| US-21–25: Access management | **New story needed** (TA-5) — cross-agent visibility |

---

# User Stories

---

## User Story TA-1

**Title**
Access Testing Agent workspace within WWA navigation

**Story**
As a Developer, TL, DevOps Admin, or Audit/Management user,
I want to access the Testing Agent workspace from the WWA Agent Workspace Hub menu and home page,
so that I can use a dedicated workspace for testing-related activities separately from deployment activities.

**Acceptance Criteria**

1. Given the user is logged into the system,
   When the user views the WWA Home page,
   Then a Testing Agent card is displayed alongside the Deployment Agent card.

2. Given the user clicks the Testing Agent card on the WWA Home page,
   When the navigation completes,
   Then the Testing Agent workspace is displayed at `/wwa/testing-agent`.

3. Given the user clicks WWA in the sidebar navigation,
   When the flyout opens,
   Then Testing Agent appears as a level-2 entry alongside Deployment Agent.

4. Given the user selects Testing Agent from the flyout,
   When the navigation completes,
   Then the Testing Agent workspace is displayed.

5. Given the user is in the Testing Agent workspace,
   When the user views the page header,
   Then the page title shows "Testing Agent" (not "Deployment Agent").

6. Given the user is in the Testing Agent workspace,
   When the user views the left-side navigation,
   Then the shared menu entries (Template Management, Configuration Management, Audit Log, Access Management) remain visible and navigable.

**Notes / Assumptions**

- Testing Agent is registered in the agent registry (`agentRegistry.ts`) with `enabled: true`.
- The agent registry already contains a placeholder entry for Testing Agent.
- No changes to the WWA shell components are needed — the registry drives home page cards and flyout entries.

**Dependencies**

- Agent registry is implemented and drives the home page and flyout navigation.
- Authentication and session management are available.

**Out of Scope**

- Testing Agent-specific branding or color theme.
- Agent-specific landing page content beyond title and description.

---

## User Story TA-2

**Title**
Upload testing request via Excel file with agent tagging

**Story**
As a Developer,
I want to upload a testing request through the Testing Agent workspace,
so that the created request is automatically tagged as a testing-agent request and isolated from deployment data.

**Acceptance Criteria**

1. Given the Developer is in the Testing Agent workspace,
   When the Developer clicks the "Upload Excel" action,
   Then an upload dialog is displayed with the same capabilities as Deployment Agent (Download Template, View Sample, Upload, Stage selector).

2. Given the Developer selects a valid Excel file and a target stage,
   When the Developer confirms upload,
   Then the system creates a Request with `agent = "testing-agent"`.

3. Given the upload is processed successfully,
   When the import completes,
   Then the system displays a success message and the new release flow appears in the Testing Agent summary (not in the Deployment Agent summary).

4. Given the Developer uploads through the Deployment Agent workspace instead,
   When the import completes,
   Then the created request has `agent = "deployment-agent"` and does NOT appear in the Testing Agent summary.

5. Given the Developer uploads an invalid or malformed Excel file,
   When validation fails,
   Then the system rejects the upload and displays validation errors (same behavior as Deployment Agent).

6. Given the upload creates a release flow for a project that already has a Deployment Agent release flow,
   When the Testing Agent summary is loaded,
   Then the release flow is visible in Testing Agent showing only testing-agent requests, and the Deployment Agent summary shows only deployment-agent requests within the same flow.

**Notes / Assumptions**

- The same fixed Excel template is used for both agents in Day 1.
- The only difference is the `agent` value set on the created Request.
- The upload API endpoint is `/api/testing-agent/upload` (not `/api/deployment-agent/upload`).

**Dependencies**

- Backend `TestingAgentUploadController` that delegates to `ImportService` with `agent = "testing-agent"`.
- Frontend API client configured for `/api/testing-agent`.

**Out of Scope**

- Testing-specific Excel template fields.
- Testing-specific validation rules beyond what Deployment Agent already validates.

---

## User Story TA-3

**Title**
View Testing Agent release flow summary with agent-scoped data isolation

**Story**
As a Developer, TL, or DevOps Admin,
I want to see only testing-related release flows in the Testing Agent summary,
so that testing and deployment activities are clearly separated.

**Acceptance Criteria**

1. Given the user is in the Testing Agent workspace,
   When the Testing Flow Summary loads,
   Then the system displays only release flows that contain at least one request with `agent = "testing-agent"`.

2. Given the user is in the Deployment Agent workspace,
   When the Deployment Flow Summary loads,
   Then release flows that contain ONLY testing-agent requests are NOT shown.

3. Given a release flow contains requests from both agents (same project uploaded through both),
   When the user views the Testing Agent summary,
   Then the release flow is visible but stage status is derived only from testing-agent requests.

4. Given a release flow contains requests from both agents,
   When the user views the Deployment Agent summary,
   Then the release flow is visible but stage status is derived only from deployment-agent requests.

5. Given legacy data exists (requests without an `agent` value),
   When the Testing Agent summary loads,
   Then legacy data is NOT shown in Testing Agent (legacy data remains visible only in Deployment Agent).

6. Given the user applies filters (Project, Release ID, Stage, Status),
   When the filter takes effect,
   Then the filtered results remain scoped to testing-agent data only.

**Notes / Assumptions**

- The backend API at `/api/testing-agent/release-flows` defaults the `agent` filter to `"testing-agent"`.
- The Deployment Agent API does not enforce strict agent filtering — it shows all data including legacy records without an agent value.
- Stage status aggregation per agent is computed server-side.

**Dependencies**

- Backend `TestingAgentReleaseFlowController` with agent-scoped list endpoint.
- `ReleaseFlowService` already supports agent filtering.
- Frontend `useTestingAgentReleaseFlowStore` with separate state from the deployment agent store.

**Out of Scope**

- Cross-agent unified view showing both deployment and testing data together.
- Agent-switching within the summary view.

---

## User Story TA-4

**Title**
Record Testing Agent actions in shared audit log

**Story**
As an Audit team member or management user,
I want Testing Agent actions to be recorded in the shared audit log with a clear agent identifier,
so that I can distinguish testing-related operations from deployment-related operations in audit records.

**Acceptance Criteria**

1. Given a user performs a key action in the Testing Agent workspace (upload, edit, view result, approve, reject, rerun, skip),
   When the action is processed successfully,
   Then the system creates an audit log entry with `agentName = "testing-agent"`.

2. Given a user performs the same action type in the Deployment Agent workspace,
   When the action is processed successfully,
   Then the audit log entry has `agentName = "deployment-agent"`.

3. Given an Audit user views the shared Audit Log page,
   When the user reviews audit records,
   Then the user can identify which agent workspace each action was performed in.

4. Given an Audit user filters audit records,
   When the user filters by agent name,
   Then only records matching the selected agent are displayed.

**Notes / Assumptions**

- Audit Log is a shared WWA capability — both agents write to the same audit store.
- The `agentName` field aligns with the minimum common audit fields defined in the multi-agent integration standard.

**Dependencies**

- Audit log entity supports an `agentName` field.
- Testing Agent controllers pass `agentName = "testing-agent"` to the audit logging service.

**Out of Scope**

- Agent-specific audit log pages.
- Agent-specific audit retention policies.

---

## User Story TA-5

**Title**
Access Testing Agent with existing access grants

**Story**
As a platform owner,
I want Testing Agent access to be governed by the same access grant model as Deployment Agent,
so that users with existing access grants can use both agent workspaces without separate authorization.

**Acceptance Criteria**

1. Given an employee has an active access grant with scope grants for an application,
   When the employee navigates to the Testing Agent workspace,
   Then the employee can access Testing Agent with the same roles, permissions, and scope grants as Deployment Agent.

2. Given an employee has no access grant,
   When the employee attempts to enter Testing Agent,
   Then the system denies entry with the same "Access not granted" behavior as Deployment Agent.

3. Given an employee has a suspended access grant,
   When the employee attempts to enter Testing Agent,
   Then the system denies entry with the same "Access suspended" behavior as Deployment Agent.

4. Given a DevOps Admin manages access grants in the Access Management page,
   When the admin grants or modifies access,
   Then the change applies to both Deployment Agent and Testing Agent (access is not agent-specific).

**Notes / Assumptions**

- Phase 1 does not introduce agent-specific access grants. A user with access to an application has access across all agent workspaces.
- The `AccessScope` model does not include an `agent` dimension — scoping is by `(application, snowGroup)`.
- Future phases may introduce agent-level access control if needed.

**Dependencies**

- Existing access management system (User Stories 21–25 from Deployment Agent).
- Testing Agent controllers use the same `SessionAuthFilter` and permission checks.

**Out of Scope**

- Agent-specific role definitions.
- Agent-specific scope grants.
- Per-agent access management UI.

---

## User Story TA-6

**Title**
View Testing Agent release flow details and manage tasks

**Story**
As a signed-in user with Release Flow visibility,
I want to view release flow details, manage tasks, and make decisions within the Testing Agent workspace,
so that I can execute the full testing workflow without switching to Deployment Agent.

**Acceptance Criteria**

1. Given the user selects a release flow from the Testing Agent summary,
   When the detail page loads at `/wwa/testing-agent/release-flows/:id`,
   Then the page displays release flow details, stage tabs, rundown information, and task table scoped to testing-agent requests.

2. Given the user views the task table in the Testing Agent detail page,
   When tasks are displayed,
   Then all task actions (Edit, Activity, View Result, Run, Record Result, Rerun, Review Decision) are available with the same state-based behavior as Deployment Agent.

3. Given the user makes a decision (Approve, Reject, Skip) on a task,
   When the decision is confirmed,
   Then the workflow progresses according to the same rules as Deployment Agent and an audit entry is recorded with `agentName = "testing-agent"`.

4. Given the user clicks "View Result" on a task,
   When the result dialog opens,
   Then the execution history and result content are displayed (same as Deployment Agent).

5. Given the user edits task input or records a manual result,
   When the save completes,
   Then the change is persisted via the `/api/testing-agent/tasks/` endpoint and an audit entry is recorded.

6. Given the page title and breadcrumb are displayed,
   When the user views the header,
   Then the label shows "Testing Agent" (not "Deployment Agent").

**Notes / Assumptions**

- The detail page structure, layout, and interaction patterns are identical to Deployment Agent.
- The only differences are the page title, API prefix, and Pinia store instance.
- Task-level permission checks (owner or DEVOPS_ADMIN) apply identically.

**Dependencies**

- Backend `TestingAgentTaskController` delegating to existing `TaskService`, `DecisionEngine`, `RecordResultService`.
- Frontend `TestingAgentDetailView` using `useTestingAgentReleaseFlowStore`.

**Out of Scope**

- Testing-specific task types or execution adapters.
- Testing-specific result formats.

---

## Summary

These user stories define the Testing Agent MVP under the WWA Agent Workspace Hub.

### New capabilities (Testing Agent-specific)
1. **TA-1**: Testing Agent workspace navigation and home page card
2. **TA-2**: Upload with `agent = "testing-agent"` tagging
3. **TA-3**: Agent-scoped data isolation in summary view
4. **TA-4**: Audit log entries with `agentName = "testing-agent"`
5. **TA-5**: Cross-agent access grant model
6. **TA-6**: Testing Agent detail page and task management

### Reused capabilities (from Deployment Agent, no changes needed)
- Release Flow creation/update logic (US-3)
- Task input editing (US-7)
- Decision control lifecycle (US-8)
- Configuration management (US-10)
- Audit log viewing (US-11)
- Template management (US-12, US-13)
- Task activity history (US-15)
- Rundown management (US-16)
- Critical task gate (US-17)
- Task action permissions (US-18)
- Execution mix display (US-19)
- Access management (US-21–US-25)

The main MVP objective remains:
**Same Deployment Agent workflow, separate namespace, full data isolation via the agent column.**
