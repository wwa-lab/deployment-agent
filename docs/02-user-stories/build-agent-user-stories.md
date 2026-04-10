# Build Agent User Stories

## Overview

This document defines the MVP user stories for Build Agent under the WWA Agent Workspace Hub.
Build Agent is the third agent workspace, mirroring the Testing Agent workflow but scoped to build activities. It reuses the same domain model (Release Flow, Request, Task), shared platform capabilities, and human-in-the-loop control pattern.

The main MVP objective is:
**Provide a dedicated build workspace with full data isolation from Deployment Agent and Testing Agent, while reusing all existing domain logic and shared capabilities.**

---

## Data Model Hierarchy

Same as Deployment Agent and Testing Agent:

- A Release Flow contains one or more Requests.
- Each Request contains one or more Tasks.
- Data isolation is achieved via `Request.agent = "build-agent"`.

---

## Relationship to Existing Agent User Stories

Build Agent reuses most capabilities already defined in Deployment Agent and Testing Agent user stories. The table below maps which existing stories apply as-is vs which need Build Agent-specific versions.

| Existing Story | Build Agent Treatment |
|---|---|
| US-1 / TA-1: Access workspace navigation | **New story needed** (BA-1) — Build Agent entry in nav |
| US-2 / TA-2: Upload Excel | **New story needed** (BA-2) — agent tagging on upload |
| US-3: Create/update Release Flow | **Reused as-is** — same service, agent tag inherited from request |
| US-4 / TA-3: View Release Flow summary | **New story needed** (BA-3) — filtered by agent |
| US-5: View Release Flow details | **Reused as-is** — same detail view structure |
| US-6: View task details | **Reused as-is** — same task display |
| US-7: Edit task input | **Reused as-is** — same edit behavior |
| US-8: Execute decisions | **Reused as-is** — same decision control |
| US-9 / TA-4: Audit logging | **New story needed** (BA-4) — agent name in audit entries |
| US-10: Configuration | **Reused as-is** — shared capability |
| US-11: Audit log view | **Reused as-is** — shared capability |
| US-12–13: Template management | **Reused as-is** — shared capability |
| US-14: Navigation | **Extended** (BA-1) — Build Agent added to flyout |
| US-15–20: Task activity, gates, permissions | **Reused as-is** — same behavior |
| US-21–25 / TA-5: Access management | **New story needed** (BA-5) — cross-agent visibility |
| TA-6: Detail view and task management | **New story needed** (BA-6) — Build Agent detail page and task management |

---

# User Stories

---

## User Story BA-1

**Title**
Access Build Agent workspace within WWA navigation

**Story**
As a Developer, TL, DevOps Admin, or Audit/Management user,
I want to access the Build Agent workspace from the WWA Agent Workspace Hub menu and home page,
so that I can use a dedicated workspace for build-related activities separately from deployment and testing activities.

**Acceptance Criteria**

1. Given the user is logged into the system,
   When the user views the WWA Home page,
   Then a Build Agent card is displayed alongside the Deployment Agent and Testing Agent cards.

2. Given the user clicks the Build Agent card on the WWA Home page,
   When the navigation completes,
   Then the Build Agent workspace is displayed at `/wwa/build-agent`.

3. Given the user clicks WWA in the sidebar navigation,
   When the flyout opens,
   Then Build Agent appears as a level-2 entry alongside Deployment Agent and Testing Agent.

4. Given the user selects Build Agent from the flyout,
   When the navigation completes,
   Then the Build Agent workspace is displayed.

5. Given the user is in the Build Agent workspace,
   When the user views the page header,
   Then the page title shows "Build Agent" (not "Deployment Agent" or "Testing Agent").

6. Given the user is in the Build Agent workspace,
   When the user views the left-side navigation,
   Then the shared menu entries (Template Management, Configuration Management, Audit Log, Access Management) remain visible and navigable.

**Notes / Assumptions**

- Build Agent is registered in the agent registry (`agentRegistry.ts`) with `enabled: true`.
- No changes to the WWA shell components are needed — the registry drives home page cards and flyout entries.

**Dependencies**

- Agent registry is implemented and drives the home page and flyout navigation.
- Authentication and session management are available.

**Out of Scope**

- Build Agent-specific branding or color theme.
- Agent-specific landing page content beyond title and description.

---

## User Story BA-2

**Title**
Upload build request via Excel file with agent tagging

**Story**
As a Developer,
I want to upload a build request through the Build Agent workspace,
so that the created request is automatically tagged as a build-agent request and isolated from deployment and testing data.

**Acceptance Criteria**

1. Given the Developer is in the Build Agent workspace,
   When the Developer clicks the "Upload Excel" action,
   Then an upload dialog is displayed with the same capabilities as Deployment Agent and Testing Agent (Download Template, View Sample, Upload, Stage selector).

2. Given the Developer selects a valid Excel file,
   When the Developer confirms upload,
   Then the system creates a Request with `agent = "build-agent"` and `stage = "DEV"`.

3. Given the Developer views the Stage selector in the Build Agent upload dialog,
   When the selector is rendered,
   Then only `DEV` is available (non-selectable disabled input, matching the Testing Agent UAT-only pattern).

4. Given the upload is processed successfully,
   When the import completes,
   Then the system displays a success message and the new release flow appears in the Build Agent summary (not in the Deployment Agent or Testing Agent summary).

5. Given the Developer uploads through the Deployment Agent or Testing Agent workspace instead,
   When the import completes,
   Then the created request has the corresponding agent value and does NOT appear in the Build Agent summary.

6. Given the Developer uploads an invalid or malformed Excel file,
   When validation fails,
   Then the system rejects the upload and displays validation errors (same behavior as Deployment Agent and Testing Agent).

7. Given the upload creates a release flow for a project that already has requests under other agents,
   When the Build Agent summary is loaded,
   Then the release flow is visible in Build Agent showing only build-agent requests.

**Notes / Assumptions**

- The same fixed Excel template **content** is used across all agents on Day 1 — all three agents invoke the shared `uploadTemplateService.generateTemplate()` backend generator. The downloaded file **name** matches each agent's existing Content-Disposition convention: Deployment Agent uses the platform default, Testing Agent returns `testing-request-template.xlsx` (existing behavior in `TestingAgentUploadController`), and Build Agent returns `build-request-template.xlsx`. Unifying the file names across all three agents is a separate, out-of-scope concern that would require changing Testing Agent's user-visible download name.
- The only differences are the `agent` value set on the created Request and the enforced `stage = "DEV"`.
- The upload API endpoint is `/api/build-agent/upload` (not `/api/deployment-agent/upload` or `/api/testing-agent/upload`).

**Dependencies**

- Backend `BuildAgentUploadController` that delegates to `ImportService` with `agent = "build-agent"` and forces stage to `DEV`.
- Frontend API client configured for `/api/build-agent`.
- `UploadDialog` accepts `:allowed-stages` prop set to `['DEV']`.

**Out of Scope**

- Build-specific Excel template fields.
- Build-specific validation rules beyond what existing agents already validate.
- Multi-stage build pipelines (compile → package → publish as separate stages).

---

## User Story BA-3

**Title**
View Build Agent release flow summary with agent-scoped data isolation

**Story**
As a Developer, TL, or DevOps Admin,
I want to see only build-related release flows in the Build Agent summary,
so that build, testing, and deployment activities are clearly separated.

**Acceptance Criteria**

1. Given the user is in the Build Agent workspace,
   When the Build Flow Summary loads,
   Then the system displays only release flows that contain at least one request with `agent = "build-agent"`.

2. Given the user is in the Deployment Agent workspace,
   When the Deployment Flow Summary loads,
   Then Deployment Agent continues its existing "global view" behavior: all release flows are visible regardless of agent. Build-only flows appear as rows with empty `SIT` / `UAT` / `PROD` columns because Deployment Agent does not render the `devStatus` / `devPresent` fields.

3. Given the user is in the Testing Agent workspace,
   When the Testing Flow Summary loads,
   Then Testing Agent continues its existing behavior: only flows with at least one testing-agent request are shown. Build-only flows are NOT shown in Testing Agent.

4. Given two Build Agent uploads share the same DEV release identifier (e.g. both `DEV-1234`),
   When the user views the Build Agent summary,
   Then the two persisted flows are stitched into a single summary row via `ReleaseFlowFamilyKey` within-agent grouping.

5. Given a Build Agent DEV-1234 flow exists and a Deployment Agent SIT-1234 flow exists for the same underlying release,
   When the user views the Build Agent summary,
   Then they appear as separate summary rows — the Build Agent summary does NOT cross-agent-stitch with Deployment Agent flows. If the user wants to see the full DEV→SIT→UAT→PROD family chain, they switch to Deployment Agent.

6. Given legacy data exists (requests without an `agent` value),
   When the Build Agent summary loads,
   Then legacy data is NOT shown in Build Agent (legacy data remains visible only in Deployment Agent).

7. Given the user applies filters (Project, Release ID, Stage, Status),
   When the filter takes effect,
   Then the filtered results remain scoped to build-agent data only.

8. Given the Build Agent summary displays a Stage filter,
   When the filter is rendered,
   Then it is a disabled input showing `DEV` (matching the Testing Agent UAT-only pattern), not a dropdown.

**Notes / Assumptions**

- The backend API at `/api/build-agent/release-flows` defaults the `agent` filter to `"build-agent"`.
- Stage status aggregation per agent is computed server-side and already supported by `ReleaseFlowService`.
- The `stages` constant in the Build Agent summary view is `['DEV']`.

**Dependencies**

- Backend `BuildAgentReleaseFlowController` with agent-scoped list endpoint.
- `ReleaseFlowService` already supports agent filtering.
- Frontend `useBuildAgentReleaseFlowStore` with separate state from the deployment and testing agent stores.

**Out of Scope**

- Cross-agent unified view showing build, testing, and deployment data together.
- Agent-switching within the summary view.

---

## User Story BA-4

**Title**
Record Build Agent actions in shared audit log

**Story**
As an Audit team member or management user,
I want Build Agent actions to be recorded in the shared audit log with a clear agent identifier,
so that I can distinguish build-related operations from deployment and testing operations in audit records.

**Acceptance Criteria**

1. Given a user performs a key action in the Build Agent workspace (upload, edit, view result, approve, reject, rerun, skip),
   When the action is processed successfully,
   Then the system creates an audit log entry with `agentName = "build-agent"`.

2. Given a user performs the same action type in the Deployment Agent or Testing Agent workspace,
   When the action is processed successfully,
   Then the audit log entry has the corresponding `agentName` value derived dynamically from the request scope.

3. Given an Audit user views the shared Audit Log page,
   When the user reviews audit records,
   Then the user can identify which agent workspace each action was performed in.

4. Given an Audit user filters audit records,
   When the user filters by agent name,
   Then only records matching the selected agent are displayed.

5. Given `AuditLoggerService` previously wrote all audit entries with hardcoded `agentName = "deployment-agent"` regardless of actual workspace,
   When the shared service is updated to derive `agentName` from `scope.agent()`,
   Then new Testing Agent audit entries begin producing `agentName = "testing-agent"` and new Build Agent audit entries produce `agentName = "build-agent"`. Historical rows are not backfilled.

**Notes / Assumptions**

- Audit Log is a shared WWA capability — all agents write to the same audit store.
- The `agentName` field aligns with the minimum common audit fields defined in the multi-agent integration standard.
- This story includes a forward-only shared-service fix: `AuditLoggerService.log` is updated to read `agentName` from `scope.agent()` rather than a hardcoded literal. This simultaneously corrects a pre-existing defect in Testing Agent.

**Dependencies**

- Audit log entity already supports an `agentName` field.
- Shared `AuditLoggerService` is updated to derive `agentName` dynamically (see spec BFR-34a).
- Build Agent controllers invoke the existing `AuditLoggerService.log` with the same signature as Deployment and Testing Agent — no new parameters.

**Out of Scope**

- Agent-specific audit log pages.
- Agent-specific audit retention policies.

---

## User Story BA-5

**Title**
Access Build Agent with existing access grants

**Story**
As a platform owner,
I want Build Agent access to be governed by the same access grant model as Deployment Agent and Testing Agent,
so that users with existing access grants can use all three agent workspaces without separate authorization.

**Acceptance Criteria**

1. Given an employee has an active access grant with scope grants for an application,
   When the employee navigates to the Build Agent workspace,
   Then the employee can access Build Agent with the same roles, permissions, and scope grants as Deployment Agent and Testing Agent.

2. Given an employee has no access grant,
   When the employee attempts to enter Build Agent,
   Then the system denies entry with the same "Access not granted" behavior as the other agents.

3. Given an employee has a suspended access grant,
   When the employee attempts to enter Build Agent,
   Then the system denies entry with the same "Access suspended" behavior as the other agents.

4. Given a DevOps Admin manages access grants in the Access Management page,
   When the admin grants or modifies access,
   Then the change applies to Deployment Agent, Testing Agent, and Build Agent (access is not agent-specific).

**Notes / Assumptions**

- Phase 1 does not introduce agent-specific access grants. A user with access to an application has access across all agent workspaces.
- The `AccessScope` model does not include an `agent` dimension — scoping is by `(application, snowGroup)`.
- Future phases may introduce agent-level access control if needed.

**Dependencies**

- Existing access management system (User Stories 21–25 from Deployment Agent).
- Build Agent controllers use the same `SessionAuthFilter` and permission checks.

**Out of Scope**

- Agent-specific role definitions.
- Agent-specific scope grants.
- Per-agent access management UI.

---

## User Story BA-6

**Title**
View Build Agent release flow details and manage tasks

**Story**
As a signed-in user with Release Flow visibility,
I want to view release flow details, manage tasks, and make decisions within the Build Agent workspace,
so that I can execute the full build workflow without switching to another agent workspace.

**Acceptance Criteria**

1. Given the user selects a release flow from the Build Agent summary,
   When the detail page loads at `/wwa/build-agent/release-flows/:id`,
   Then the page displays release flow details, stage tabs (showing only `DEV`), rundown information, and task table scoped to build-agent requests.

2. Given the user views the task table in the Build Agent detail page,
   When tasks are displayed,
   Then all task actions (Edit Input, Activity, View Result, Start Manual, Submit Auto, Record Result, Rerun, Review Decision) are available with the same state-based behavior as Deployment Agent and Testing Agent.

3. Given the user makes a decision (Approve, Reject, Skip) on a task,
   When the decision is confirmed,
   Then the workflow progresses according to the same rules as the other agents and an audit entry is recorded with `agentName = "build-agent"`.

4. Given the user clicks "View Result" on a task,
   When the result dialog opens,
   Then the execution history and result content are displayed (same as the other agents).

5. Given the user edits task input or records a manual result,
   When the save completes,
   Then the change is persisted via the Build Agent task API prefix (`/api/build-agent/tasks/{id}/input`, `/api/build-agent/tasks/{id}/record-result`) and an audit entry is recorded with `agentName = "build-agent"`.

6. Given the page title and breadcrumb are displayed,
   When the user views the header,
   Then the label shows "Build Agent" (not "Deployment Agent" or "Testing Agent").

7. Given a user in the Build Agent workspace attempts to operate on a task whose parent request belongs to another agent (e.g. by crafting a URL with a deployment-agent task ID),
   When the request reaches the Build Agent task controller,
   Then the system returns HTTP 404 (not 403) and the underlying task is not modified; an audit entry for the rejected attempt is NOT required.

8. Given a Build Agent release flow completes its last task in the `DEV` stage,
   When the progression service evaluates the flow,
   Then the flow transitions to `Completed` and does NOT auto-advance into the `SIT` stage.

**Notes / Assumptions**

- The detail page structure, layout, and interaction patterns are identical to Testing Agent, except only the `DEV` stage tab is shown.
- The only differences are the page title, API prefix, Pinia store instance, and the fact that only the `DEV` stage tab is shown.
- Task-level permission checks (owner or DEVOPS_ADMIN) apply identically, AND an additional controller-layer guard enforces `request.agent == "build-agent"` on every task mutation.
- Terminal-stage behavior for DEV is implemented by `Stage.DEV.next() == null`; the existing `ReleaseFlowProgressionService` already handles `next() == null` as "flow terminal" so no progression service changes are required.

**Dependencies**

- Backend `BuildAgentTaskController` + `BuildAgentDecisionController` delegating to existing `TaskService`, `DecisionEngine`, `RecordResultService`, `AutoExecutionService`, `TaskExecutionHistoryService`.
- Shared `AgentBoundaryGuard` helper that loads a task, resolves its parent request, and asserts `request.agent == expectedAgent` (throws to produce HTTP 404).
- Frontend `BuildAgentDetailView` using `useBuildAgentReleaseFlowStore`.
- `Stage` enum extension (`DEV` value + explicit `next()` switch).

**Out of Scope**

- Build-specific task types or execution adapters.
- Build-specific result formats.
- Multi-stage build pipelines within a single release flow.
- Back-patching Testing Agent to enforce the same agent boundary (tracked as a separate follow-up risk R-08).

---

## Summary

These user stories define the Build Agent MVP under the WWA Agent Workspace Hub.

### New capabilities (Build Agent-specific)
1. **BA-1**: Build Agent workspace navigation and home page card
2. **BA-2**: Upload with `agent = "build-agent"` tagging and `DEV`-only stage
3. **BA-3**: Agent-scoped data isolation in summary view, `DEV`-only stage filter
4. **BA-4**: Audit log entries with `agentName = "build-agent"`
5. **BA-5**: Cross-agent access grant model extended to Build Agent
6. **BA-6**: Build Agent detail page and task management

### Reused capabilities (from Deployment Agent and Testing Agent, no changes needed)
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
**Same Deployment/Testing Agent workflow, separate namespace, full data isolation via the agent column, restricted to the `DEV` stage (Build Agent covers the development phase of the SDLC: DEV → SIT → UAT → PROD).**
