# Requirement Document

## Overview

This document defines the MVP user stories for Release Agent under the WWA Agent Workspace Hub.
The MVP focuses on enabling a controlled, human-in-the-loop deployment workflow that allows users to upload requests, monitor release progress, inspect execution results, make explicit decisions, maintain key configuration, and review audit records.

The main MVP objective is:
**Ensure the core workflow can successfully run through request → process → verification → decision.**

---

## Data Model Hierarchy

A Release Flow contains one or more Requests.
Each Request contains one or more Tasks.
Task operations such as View Result, Edit, Approve, Reject, Rerun, and Skip occur at the Task level within the selected Request context.

---

# User Stories

---

## User Story 1

**Title**
Access Release Agent workspace within WWA Agent Workspace Hub navigation

**Story**
As a Developer, TL, DevOps Admin, or Audit/Management user,
I want to access the Release Agent workspace from the WWA Agent Workspace Hub menu,
so that I can use a unified workspace for deployment-related activities.

**Acceptance Criteria**

1. Given the user is logged into the system,
   When the user views the main navigation,
   Then the WWA level-1 menu is visible and contains Release Agent as a level-2 entry.

2. Given the user clicks the Release Agent entry,
   When the workspace page loads,
   Then the Release Agent workspace is displayed.

3. Given the user is in the Release Agent workspace,
   When the user views the left-side navigation,
   Then the shared menu entries Template Management, Configuration Management, and Audit Log are visible.

**Notes / Assumptions**

- WWA Agent Workspace Hub is a reusable platform layer for future agent workspaces.
- Release Agent is the first workspace implemented under WWA.

**Dependencies**

- Application navigation system supports multi-level menu structure.
- Authentication is already available.

**Out of Scope**

- Full implementation of Template Management, Configuration Management, or Audit Log pages.

**Open Questions**

- What is the exact routing path for Release Agent?
- Should breadcrumb navigation be shown in the workspace?

---

## User Story 2

**Title**
Upload deployment request via Excel file

**Story**
As a Developer,
I want to upload a deployment request using the fixed Excel template,
so that I can submit deployment input into the system.

**Acceptance Criteria**

1. Given the Developer is in the Release Agent workspace,
   When the Developer clicks the "Upload Excel" action,
   Then an upload dialog is displayed.

2. Given the upload dialog is displayed,
   When the Developer views the available actions,
   Then the dialog provides Download Template, View Sample, and Upload actions.

3. Given the Developer selects a valid Excel file,
   When the Developer confirms upload,
   Then the file is accepted and the system starts import processing.

4. Given the upload is processed successfully,
   When the import completes,
   Then the system displays a success message and provides access to the import log entry.

5. Given the Developer uploads an invalid or malformed Excel file,
   When validation fails,
   Then the system rejects the upload and displays validation errors.

**Notes / Assumptions**

- Day 1 uses a fixed Excel template.
- Dynamic template definition is not part of MVP.

**Dependencies**

- Excel parsing and validation capability.
- Fixed template is available to users.

**Out of Scope**

- Dynamic template management.
- Resume upload after network interruption.

**Open Questions**

- What are the exact mandatory fields in the fixed Excel template?
- What is the maximum supported file size?

---

## User Story 3

**Title**
Create or update Release Flow from imported deployment request

**Story**
As a Developer,
I want the imported deployment request to create or update Release Flow records,
so that deployment activities can be tracked in a structured release journey.

**Acceptance Criteria**

1. Given a valid Excel file is imported successfully,
   When the system processes the request data,
   Then one or more Release Flow records are created or updated in the system.

2. Given Release Flow records are created or updated,
   When the user views the Deployment Flow Summary,
   Then the corresponding Release Flow records are visible in the summary list.

3. Given the imported file contains multiple request groups,
   When the import processing completes,
   Then the system can create multiple Release Flow records as needed.

**Notes / Assumptions**

- Release Flow is the top-level business object.
- A single Excel file may produce multiple Release Flows.

**Dependencies**

- Release Flow data model.
- Import mapping rules from Excel to Release Flow / Request / Task.

**Out of Scope**

- Manual merge/split management for Release Flows.

**Open Questions**

- What is the exact grouping logic for creating a Release Flow?
- If Release ID is missing, what fallback rule should be used?

---

## User Story 4

**Title**
View Release Flow summary with stage progress

**Story**
As a Developer, TL, or DevOps Admin,
I want to see all Release Flows in a summary view with stage progress across SIT, UAT, and PROD,
so that I can monitor release status at a glance.

**Acceptance Criteria**

1. Given the Release Agent workspace is open,
   When the user views the Deployment Flow Summary section,
   Then the system displays a list of Release Flows.

2. Given a Release Flow row is displayed,
   When the user examines the row,
   Then the row shows the Release Flow identifier and stage statuses for SIT, UAT, and PROD.

3. Given stage statuses are displayed,
   When the user reads the values,
   Then each stage status is shown using supported summary values such as Done, Running, or Pending.

4. Given the user applies a supported filter,
   When the filter takes effect,
   Then the summary list updates to show only matching Release Flows.

**Notes / Assumptions**

- Release Flow summary is a top-level monitoring view.
- Summary status values are simplified for MVP.

**Dependencies**

- Depends on User Story 3: Release Flow records must exist before summary display.
- Release Flow data model with stage-level status aggregation.
- Filter controls and filtering logic.

**Out of Scope**

- Historical dashboards and trend analytics.
- Export/reporting features.

**Open Questions**

- Should historical/completed Release Flows be shown by default?
- What is the default sorting rule?

---

## User Story 5

**Title**
View selected Release Flow details

**Story**
As a TL or DevOps Admin,
I want to view the details of a selected Release Flow,
so that I can understand its current stage and review context before taking action.

**Acceptance Criteria**

1. Given the user selects a Release Flow from the summary list,
   When the selection is applied,
   Then the Selected Release Flow Details section is updated.

2. Given the Selected Release Flow Details section is displayed,
   When the user views the details,
   Then the section shows Project, Release ID, Current Stage, Current Request ID, Review Status, and Review Owner.

3. Given the selected Release Flow changes,
   When the user selects another Release Flow,
   Then the details section refreshes to reflect the newly selected Release Flow.

**Notes / Assumptions**

- This section provides review context for the current release journey.

**Dependencies**

- Release Flow detail fields are available in the backend model.

**Out of Scope**

- Editing Release Flow metadata in this section.

**Open Questions**

- Should Review Owner always be a single user, or can it be a group?
- How should empty or unassigned review fields be displayed?

---

## User Story 6

**Title**
View task-level details and execution results

**Story**
As a TL,
I want to view task-level details including status, result summary, and timestamps,
so that I can understand execution outcomes before making a decision.

**Acceptance Criteria**

1. Given a Release Flow is selected,
   When the user views the Task Details section,
   Then the system displays the tasks associated with the current Request or Release Flow context.

2. Given a task row is displayed,
   When the user examines the row,
   Then the row shows Task Name, Status, Result Summary, Start Time, End Time, and Available Actions.

3. Given a task has execution output available,
   When the user clicks "View Result",
   Then the system displays the task result content.
   Result content format (summary, raw logs, parsed output) will be finalized in design.
   For MVP, assume the system can display at least a result summary and raw logs.

4. Given a task supports operator actions,
   When the user opens the Available Actions menu,
   Then the supported actions are displayed for that task, including Edit, View Result, and Decision actions.
   When applicable, Decision actions include Approve, Reject, Rerun, and Skip.

**Notes / Assumptions**

- Result Summary is a short inline view of execution outcome.
- Full result content may be shown in a separate UI container.

**Dependencies**

- Task data model with execution result fields.
- Result retrieval/display capability.

**Out of Scope**

- Real-time streaming logs.
- Push notifications for task result updates.

**Open Questions**

- Is the task list scoped by Request, Stage, or full Release Flow?

---

## User Story 7

**Title**
Edit task input parameters before execution

**Story**
As a TL,
I want to edit task input parameters before execution,
so that I can correct or refine execution input without restarting the whole Release Flow.

**Acceptance Criteria**

1. Given a task is in an editable status,
   When the TL clicks the "Edit" action,
   Then the system displays editable task input parameters.

2. Given the edit view is displayed,
   When the TL updates one or more input values and saves,
   Then the system validates and persists the updated input.

3. Given the updated input is invalid,
   When the TL attempts to save,
   Then the system rejects the change and displays validation errors.

4. Given task input is updated successfully,
   When the task is later executed or rerun,
   Then the execution uses the latest saved input values.

5. Given a task input change is saved,
   When the save completes,
   Then an audit log entry is created for the edit action.

**Notes / Assumptions**

- Editable fields are limited to defined task input parameters.
- Editing after task execution starts is not supported in MVP.

**Dependencies**

- Task input schema and validation rules.
- Audit logging capability.

**Out of Scope**

- Editing task type or task identity.
- Free-form input outside defined schema.

**Open Questions**

- Which task statuses should be considered editable?
- Should only changed fields be logged in audit?

---

## User Story 8

**Title**
Execute task-level decisions to control Release Flow progression

**Story**
As a TL,
I want to make explicit task-level decisions after reviewing execution results,
so that the Release Flow progresses in a controlled and traceable way.

**Acceptance Criteria**

1. Given a task has completed execution and is waiting for review,
   When the TL opens the Decision action,
   Then the system displays the supported decision options: Approve, Reject, Rerun, and Skip.

2. Given the TL selects Approve,
   When the decision is confirmed,
   Then the current task is marked accordingly and the Release Flow continues to the next available step.

3. Given the TL selects Reject,
   When the decision is confirmed,
   Then the current Release Flow is stopped and no further steps are executed.

4. Given the TL selects Rerun,
   When the decision is confirmed,
   Then the system re-executes the current step.

5. Given the TL selects Skip,
   When the decision is confirmed,
   Then the current step is skipped and the Release Flow continues to the next available step.

6. Given any decision action is completed,
   When the action is processed successfully,
   Then an audit log entry is created for that decision.

**Notes / Assumptions**

- Human-in-the-loop decision control is mandatory in MVP.
- The system must not auto-progress after execution without decision.

**Dependencies**

- Task and Release Flow state transition rules.
- Audit logging capability.
- Execution integration for rerun.

**Out of Scope**

- Automatic decision-making based on result content.
- Parallel branch workflow execution.

**Open Questions**

- What exact statuses should be displayed after Reject?
- Should Reject require an extra confirmation step?
- Should Rerun preserve prior execution history?

---

## User Story 9

**Title**
Record operator actions for audit traceability

**Story**
As an Audit team member or management user,
I want key operator actions to be logged with traceable information,
so that I can review deployment-related operations for compliance and accountability.

**Acceptance Criteria**

1. Given a user performs a supported key action in the Release Agent workspace,
   When the action is processed successfully,
   Then the system creates and persists an audit log entry for that action.

2. Given a supported action is audit-relevant,
   When the action occurs,
   Then the audit log entry includes operator identity, action type, timestamp, and related context.

3. Given the system supports key actions such as upload, edit, view result, approve, reject, rerun, and skip,
   When those actions are performed,
   Then each action is logged consistently using the audit mechanism.

**Notes / Assumptions**

- MVP requires backend audit logging capability.
- Detailed audit query UI is not required in MVP.

**Dependencies**

- Audit log storage and schema.
- User identity is available from authentication context.

**Out of Scope**

- Full audit query/filter/reporting page.
- Audit retention policy design.

**Open Questions**

- Where should audit logs be stored?
- Who can access audit log data in MVP?

---

## User Story 10

**Title**
Maintain integration configuration in UI

**Story**
As a DevOps Admin,
I want to maintain key integration configuration values in the UI,
so that deployment execution can use managed configuration instead of hardcoded values.

**Acceptance Criteria**

1. Given the DevOps Admin enters Configuration Management,
   When the page is displayed,
   Then the system shows editable MVP configuration items, including Jenkins URL, Ansible URL, and one additional configuration item to be confirmed.

2. Given the DevOps Admin updates a configuration value,
   When the admin saves the change,
   Then the system validates the input and persists the updated configuration.

3. Given the configuration is saved successfully,
   When a related deployment task is executed,
   Then the task uses the latest saved configuration value.

4. Given the DevOps Admin enters an invalid configuration value,
   When validation fails,
   Then the system rejects the save and displays an error message.

**Notes / Assumptions**

- Configuration Management is a shared WWA capability.
- MVP focuses on key configuration required by deployment execution.

**Dependencies**

- Configuration storage mechanism.
- Deployment execution can read managed configuration values.

**Out of Scope**

- Advanced configuration versioning and rollback.
- Environment-specific override matrix.

**Open Questions**

- What is the additional Day 1 configuration item to be confirmed?
- Do configuration changes take effect immediately?

---

## User Story 11

**Title**
View audit logs for compliance review

**Story**
As an Audit team member or management user,
I want to view audit logs in a minimal read-only format,
so that I can review deployment-related operations for compliance and accountability during MVP.

**Acceptance Criteria**

1. Given the user enters the Audit Log area,
   When the page or section is displayed,
   Then the system shows a read-only list of recent audit log records.

2. Given an audit log record is displayed,
   When the user views the record,
   Then the record includes operator identity, action type, timestamp, and related context.

3. Given the user is viewing audit logs in MVP,
   When the user interacts with the list,
   Then the user can read the records but cannot edit or delete them.

**Notes / Assumptions**

- MVP only requires a minimal audit log viewing capability.
- Advanced filtering, export, and reporting remain future scope.

**Dependencies**

- Audit log records are already created and persisted by the audit logging mechanism.
- Read-only access control is available for Audit/Management users.

**Out of Scope**

- Advanced audit filtering and search.
- Exporting audit logs.
- Audit analytics dashboard.

**Open Questions**

- Should the MVP audit log view be a standalone page or a simple embedded list?
- How many recent records should be shown by default?

---

## User Story 12

**Title**
Manage deployment templates with full CRUD lifecycle

**Story**
As a TL or DevOps Admin,
I want to create, view, edit, clone, and delete deployment templates,
so that I can define reusable multi-task deployment blueprints for future release flows.

**Acceptance Criteria**

1. Given the user enters Template Management,
   When the page is displayed,
   Then the system shows a list of existing templates with name, description, stage, and task count.

2. Given the user clicks Create New Template,
   When the creation modal opens,
   Then the user can create a template via Manual Entry or Upload Excel tabs.

3. Given a template row is displayed,
   When the user opens the More menu,
   Then Clone, Edit, and Delete actions are available.

4. Given the user selects a template from the list,
   When the selection is applied,
   Then the Template Details panel shows the full task table with task definitions.

5. Given the user clones a template,
   When the clone completes,
   Then a new draft template is created with copied metadata and task definitions.

6. Given the user deletes a template,
   When the delete is confirmed,
   Then the template is removed from the list and the selection state resets.

**Notes / Assumptions**

- Templates are currently stored locally in frontend state only — backend API persistence is pending.
- Each template is treated as a multi-task deployment blueprint.
- Template selection switches the shared Template Details workspace directly (no separate View Details button).

**Dependencies**

- Template data model and backend API (pending).
- Excel template download/upload capability.

**Out of Scope**

- Template versioning or history.
- Template approval workflow.

---

## User Story 13

**Title**
Author and maintain tasks within a deployment template

**Story**
As a TL or DevOps Admin,
I want to add, edit, and delete tasks within a template, including dependency maintenance,
so that each template defines the exact task sequence and gate structure for a deployment.

**Acceptance Criteria**

1. Given a template is selected for editing,
   When the user clicks Add Task,
   Then a task authoring dialog is displayed with fields aligned to the deployment task structure.

2. Given a task exists in the template,
   When the user clicks Edit on the task row,
   Then the task dialog opens with pre-filled values for editing.

3. Given a task exists in the template,
   When the user clicks Delete on the task row,
   Then the task is removed from the template definition.

4. Given task authoring is active,
   When the user sets the Critical (Y/N) flag on a task,
   Then the flag is saved as part of the template task definition.

5. Given Manual Entry is selected during template creation,
   When the first task is created,
   Then the system automatically hands off from Manual Entry into the task-creation step.

**Notes / Assumptions**

- Task structure in templates mirrors the deployment task structure (name, execution type, category, critical flag, dependencies).
- Critical flag in templates defines review-blocking steps before a rundown is created.

**Dependencies**

- Template CRUD (User Story 12).

---

## User Story 14

**Title**
Navigate WWA Agent Workspace Hub with two-level menu and workspace flyout

**Story**
As any authenticated user,
I want to navigate WWA capabilities through a clear two-level navigation structure,
so that I can access Release Agent, Template Management, Configuration Management, and Audit Log from a unified Agent Workspace Hub.

**Acceptance Criteria**

1. Given the user is in the workspace,
   When the user views the left navigation,
   Then WWA appears as a first-level menu item with a second-level flyout panel.

2. Given the user clicks WWA in the sidebar,
   When the flyout opens,
   Then the second-level items include Release Agent, Template Management, Configuration Management, and Audit Log.

3. Given the user selects a workspace page from the flyout,
   When the navigation completes,
   Then the flyout closes and the selected page is displayed.

4. Given the user clicks outside the flyout,
   When the click is detected,
   Then the flyout closes.

5. Given the user does not have access to a shared capability,
   When the user views the sidebar,
   Then the menu entry is still visible but the page shows access guidance instead of hiding the entry.

6. Given first-level placeholder applications exist alongside WWA,
   When the user views the navigation,
   Then the left sidebar reads like a broader hub shell with only WWA opening a working second-level flyout.

**Notes / Assumptions**

- The flyout panel is not clipped by the sidebar scroll container.
- No redundant mini WWA heading appears in the flyout panel.

**Dependencies**

- Authentication and role management.

---

## User Story 15

**Title**
View task activity history for traceability

**Story**
As a TL or DevOps Admin,
I want to view task-level activity history showing who did what, when it happened, and the related input/output,
so that I can trace all actions taken on a task from audit and execution records.

**Acceptance Criteria**

1. Given a task is displayed in the release detail view,
   When the user clicks the Activity action,
   Then a task activity dialog opens.

2. Given the activity dialog is open,
   When the data loads,
   Then the dialog displays combined audit log entries and execution history records for that task.

3. Given audit logs or execution history fail to load,
   When the dialog renders,
   Then a warning is shown indicating partial data, and the available data is still displayed.

**Dependencies**

- Audit logging capability (User Story 9).
- Execution history records.

---

## User Story 16

**Title**
Manage stage-level rundown information for deployment requests

**Story**
As a TL or DevOps Admin,
I want to view and edit stage-level rundown fields such as SNOW group, application, site, and estimated remaining time,
so that the deployment context is captured per request for operational coordination.

**Acceptance Criteria**

1. Given a release flow detail page is open with stage tabs,
   When the user views a stage tab,
   Then a Rundown Information panel is displayed for that stage.

2. Given the Rundown Information panel is visible,
   When the user clicks Edit on the rundown,
   Then a dialog opens with editable fields for SNOW group, application, agent, site, estimated remaining time, and rundown owner when the current user is a DevOps Admin.

3. Given the user saves rundown changes,
   When the save completes,
   Then the updated values are persisted via the backend API.

4. Given a request is in a runnable state and the current user is the rundown owner or a DevOps Admin,
   When the user clicks Start Deployment, Refresh, or Mark as Failed,
   Then the corresponding request-level action is executed and the detail refreshes.

**Dependencies**

- Release Flow detail API with rundown fields.

---

## User Story 17

**Title**
Gate workflow progression on critical task review

**Story**
As a TL,
I want tasks marked as Critical to block the next pending task from being released until the critical task is reviewed,
so that review-blocking steps enforce governance before the workflow progresses.

**Acceptance Criteria**

1. Given a task has the Critical (Y/N) field set to Y,
   When the task is displayed in the release detail table,
   Then a Critical badge is visible on the task row.

2. Given a critical task is in Awaiting_Review status,
   When the system evaluates task progression,
   Then the next pending task is not released until the critical task receives a decision.

3. Given the critical task is approved or skipped,
   When the decision is recorded,
   Then the workflow gate is lifted and the next task becomes available.

4. Given the Excel import includes a Critical column,
   When the import completes,
   Then the critical flag is correctly mapped to the task entity.

**Notes / Assumptions**

- Critical flag is also supported in Template Management task authoring.
- The gate logic may be enforced on the backend.

**Dependencies**

- Decision control (User Story 8).
- Excel import mapping.

---

## User Story 18

**Title**
Control task action permissions by ownership and role

**Story**
As a TL or DevOps Admin,
I want task-level actions (edit input, record results, submit auto execution, apply decisions) to be restricted to the task owner or DEVOPS_ADMIN,
so that only authorized users can modify task execution state.

**Acceptance Criteria**

1. Given a task row is displayed,
   When the user views available actions,
   Then Edit, View Result, and Decision dropdown are always shown, with state-based disabling.

2. Given the current user is neither the task owner nor a DEVOPS_ADMIN,
   When the user hovers over a disabled action,
   Then a tooltip explains whether the action is blocked by role or task status.

3. Given a MANUAL task needs result submission,
   When the user opens the Edit dialog,
   Then the result can be recorded from within the Edit dialog (not a separate action).

**Dependencies**

- User authentication and role context.
- Task ownership assignment.

---

## User Story 19

**Title**
View execution mix and task category in release detail

**Story**
As a TL or DevOps Admin,
I want to see the execution mix (manual vs auto task counts and percentages) and task category in the release detail view,
so that I can understand the composition and automation coverage of each deployment.

**Acceptance Criteria**

1. Given a release flow detail page is open,
   When the user views the execution mix section,
   Then both task counts and manual/auto percentages are displayed.

2. Given a task row is displayed in the release detail table,
   When the user views the row,
   Then the task category (from imported Activity category) is shown.

**Dependencies**

- Excel import maps Activity category to task category.

---

## User Story 20

**Title**
View stage status on deployment summary table

**Story**
As a Developer, TL, or DevOps Admin,
I want to see SIT, UAT, and PROD stage statuses directly in the deployment summary table,
so that I can assess release progress at a glance without opening each flow detail.

**Acceptance Criteria**

1. Given the Release Agent summary page is open,
   When the user views the Release Flow table,
   Then SIT, UAT, and PROD status columns are visible for each row.

2. Given a stage has a status value,
   When the user reads the column,
   Then the status is displayed with a visual badge (e.g. Done, Running, Pending).

**Dependencies**

- Backend stage status aggregation.

---

## User Story 21

**Title**
Manage Release Agent access grants

**Story**
As a DevOps Admin,
I want to grant, suspend, and reactivate Release Agent access for employees,
so that product access can be managed without building a separate user account system.

**Acceptance Criteria**

1. Given an employee has a valid enterprise identity but does not yet have Release Agent access,
   When the DevOps Admin creates an access grant,
   Then the system stores the employee ID, display name snapshot, status, assigned roles, scope grants, and note.

2. Given an employee already has an active access grant,
   When the DevOps Admin suspends the employee,
   Then the system keeps the access record and changes the status to Suspended instead of physically deleting it.

3. Given an employee has a suspended access grant,
   When the DevOps Admin reactivates the employee,
   Then the employee regains product access and the authorization history remains preserved.

**Notes / Assumptions**

- Authentication continues to come from Team Book or enterprise SSO.
- Phase 1 manages product entry plus scoped visibility through `Application + SNOW Group` grants.
- `Agent` remains a runtime dimension and is not the primary authorization boundary.

**Dependencies**

- Enterprise identity source returns employee ID and display name.
- A local access grant data model and service are added to Release Agent.

**Out of Scope**

- Password management.
- User self-service access request and approval workflow.

**Open Questions**

- Should role changes require a mandatory admin note?
- Should access grants support future effective/expiry dates in a later phase?

---

## User Story 22

**Title**
Authorize product entry with deny-by-default access control

**Story**
As a platform owner,
I want Release Agent to allow only explicitly authorized employees into the product,
so that platform access is controlled and auditable.

**Acceptance Criteria**

1. Given an employee is successfully authenticated by Team Book but has no Release Agent access grant,
   When the employee logs in,
   Then the system denies entry and displays an "Access not granted" message.

2. Given an employee has a suspended Release Agent access grant,
   When the employee logs in,
   Then the system denies entry and displays an "Access suspended" message.

3. Given an employee has an active Release Agent access grant,
   When the employee logs in,
   Then the system returns the employee's effective roles, permissions, and applicable scope grants rather than relying on a single hardcoded role value.

**Notes / Assumptions**

- Phase 1 uses a deny-by-default model.
- The existing session-based login mechanism remains in place.

**Dependencies**

- Login and auth/session endpoints must be extended to resolve local access grants.
- Effective role and permission calculation logic must be introduced.

**Out of Scope**

- Multi-factor authentication.
- SSO federation setup.

**Open Questions**

- Should the "Access not granted" message include guidance to contact a DevOps Admin?
- Should suspended users be allowed to view any limited read-only information?

---

## User Story 23

**Title**
Use an Access Management console for authorization operations

**Story**
As a DevOps Admin,
I want a dedicated Access Management page to manage user access and roles,
so that I can operate authorization without changing code or stub data.

**Acceptance Criteria**

1. Given the DevOps Admin opens the Access Management page,
   When the page finishes loading,
   Then the system displays employee ID, name, status, roles, scope grants, last login time, updated by, and updated at.

2. Given the DevOps Admin searches for an employee,
   When an employee ID or name keyword is entered,
   Then matching employees are shown and the admin can grant access, edit roles, update scope grants, suspend access, or reactivate access.

3. Given a non-admin user attempts to access the Access Management page,
   When the user navigates from the menu or enters the URL directly,
   Then the frontend and backend both block access.

**Notes / Assumptions**

- Phase 1 needs only a list view and detail drawer/dialog.
- The page is a product authorization console, not a full HR directory.

**Dependencies**

- Access management API endpoints are available.
- Route-level role guards are implemented in the frontend.

**Out of Scope**

- Bulk import/export of access grants.
- Organizational hierarchy visualization.

**Open Questions**

- Should the page search only existing grants or also allow search of non-granted enterprise users?
- Should the UI show the reason for the most recent authorization change?

---

## User Story 24

**Title**
Enforce effective permissions consistently across UI and API

**Story**
As a product user,
I want menus, routes, and APIs to consistently reflect my effective permissions,
so that the product behaves predictably and securely.

**Acceptance Criteria**

1. Given a user only has Developer permissions,
   When the user enters Release Agent,
   Then the user sees only the menus and actions that the Developer permission set allows.

2. Given a user lacks the permission required for a page or API,
   When the user attempts to access it through navigation, direct URL entry, or an API call,
   Then the frontend blocks entry and the backend returns a permission error.

3. Given a user has multiple assigned roles,
   When the system resolves the user's effective permissions,
   Then page visibility and action availability are derived from the combined permission set.

4. Given a user has active scope grants,
   When the user loads scoped surfaces such as release flows or audit history,
   Then the system limits visible records to the `Application + SNOW Group` scopes assigned to that user unless the user is a global DevOps Admin.

**Notes / Assumptions**

- Phase 1 may continue to show a primary role in the UI, but execution must be permission-based.
- Existing hardcoded role checks will be progressively centralized behind a permission model.

**Dependencies**

- Unified permission model is defined for Release Agent.
- Frontend navigation and route guards are updated to use permissions.

**Out of Scope**

- Project-level permissions.
- Environment-level permissions.

**Open Questions**

- Should Template Management be limited to DevOps Admin only in Phase 1?
- Does the Management role need a separate read-only dashboard in a later phase?

---

## User Story 25

**Title**
Audit access grant changes

**Story**
As an Audit or DevOps Admin user,
I want access grant changes to be recorded and searchable,
so that authorization administration is traceable and reviewable.

**Acceptance Criteria**

1. Given a DevOps Admin grants access, edits roles, suspends access, or reactivates access,
   When the operation succeeds,
   Then the system writes an audit log entry containing the operator, target employee, action, and timestamp.

2. Given an Audit or DevOps Admin user opens the audit log,
   When the user filters by employee ID or action type,
   Then matching access-management records are visible in the result set.

3. Given a permission dispute or investigation occurs,
   When an authorized user reviews the audit history,
   Then the system clearly distinguishes grant, role edit, suspend, and reactivate events.

**Notes / Assumptions**

- Access governance is audit-first.
- Existing audit logging infrastructure can be extended with new action types.

**Dependencies**

- Access-related audit action types are added.
- Audit log access is tightened to authorized roles only.

**Out of Scope**

- Approval workflow audit.
- SIEM or external compliance tool integration.

**Open Questions**

- Should the audit payload include before/after role diffs?
- Should access-governance audit logs be exportable in a later phase?

---

## Summary

These user stories define the full capabilities for Release Agent under the WWA Agent Workspace Hub.
They cover the core deployment workflow, platform navigation, template management, task governance, and shared capabilities.

### Core workflow
1. Workspace Access
2. Request Upload
5. Selected Release Flow Details
6. Task Details and Results
7. Task Input Editing
8. Decision Control
9. Audit Logging
10. Configuration Management
11. Audit Log View

### Enhanced capabilities
12. Template Management CRUD
13. Template Task Authoring
15. Task Activity History
17. Critical Task Gate
18. Task Action Permissions
19. Execution Mix and Task Category
20. Stage Status on Summary
21. Access Grant Lifecycle
22. Deny-by-Default Product Entry
24. Effective Permission Enforcement
25. Access Grant Auditability

The main MVP objective remains:
**Ensure the core workflow can run through successfully from request → process → verification → decision.**
