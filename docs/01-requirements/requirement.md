# WWA Agent Workspace Hub / Release Agent Requirement

## 1. Background

The company is introducing a platform layer called **WWA Agent Workspace Hub**. `WWA` remains the short label used in navigation and route naming. The first agent workspace to be implemented under WWA is **Release Agent**.

The purpose of this requirement is to define the MVP scope for Release Agent in FinBlock.

This MVP is **not** intended to be a fully autonomous agent. Instead, it is designed as a **human-in-the-loop controlled execution workspace** for deployment activities.

The MVP should support:

- Excel-based request onboarding
- release flow tracking across SIT / UAT / PROD
- request-level and task-level visibility
- task result review
- human decision control
- basic audit logging
- basic configuration management

The design should also prepare WWA to become a reusable platform layer for future agents.

---

## 2. Product Positioning

### 2.1 WWA Agent Workspace Hub Positioning

WWA Agent Workspace Hub should not be treated as a single page. It should be designed as a reusable DevOps hub layer that can support multiple agent workspaces in the future.

### 2.2 Release Agent Positioning

Release Agent is the first workspace under WWA. It is responsible for deployment request onboarding, execution tracking, result review, and human decision control.

### 2.3 Future Reuse

Future agents should mainly add:

- their own main workspace
- their own task / result logic
- their own agent-specific configurations

The following capabilities should be shared and reusable across WWA:

- Template Management
- Configuration Management
- Audit Log
- operator identity and traceability
- human decision control pattern

---

## 3. MVP Objective

The objective of this MVP is to build a controlled deployment workspace that allows users to:

- upload a deployment request Excel file
- create or extend a release flow
- track deployment progress across SIT / UAT / PROD
- view task-level execution results
- review whether the output matches the expected result
- make a human decision before moving to the next step

The workflow should support API-based orchestration today and remain compatible with future AI enhancement.

---

## 4. MVP Scope

### 4.1 In Scope

#### A. Navigation

Add the following menu structure in FinBlock:

- WWA (level-1 menu)
  - Release Agent (level-2 menu)

WWA should also reserve shared capability entries for future use:

- Template Management
- Configuration Management
- Audit Log

#### B. Release Agent Main Page

The Release Agent page should include:

1. Page introduction area
2. Filter area
3. Deployment Flow Summary
4. Selected Release Flow Details
5. Task Details
6. Upload Excel entry and upload dialog

#### C. Page Introduction Area

Display:

- page title: Release Agent
- explanation of WWA
- note that the current phase uses API-based orchestration and human review

#### D. Filter Area

Fields:

- Project
- Release ID or business mapping key
- Stage
- Status

Buttons:

- Upload Excel
- Query
- Refresh

`Reset` is not included in MVP.

#### E. Excel Upload

Support:

- Stage selector (required input at upload time): SIT / UAT / PROD
- Upload Excel
- Download Template
- View Sample
- Upload success message
- View Import Log entry point

The user must select the target Stage before submitting the file. Stage is NOT read from the Excel file — it is an explicit upload-time input provided by the user.

Day 1 will use the existing fixed Excel template.

#### F. Deployment Flow Summary

The summary view should be based on **Release Flow**, not on a single request.

Each row should represent one release flow and display:

- Project
- Release ID
- SIT
- UAT
- PROD
- Overall Status

For SIT / UAT / PROD, only the following values should be displayed:

- Done
- Running
- Pending

#### G. Selected Release Flow Details

When a user selects one release flow, the page should display:

- Project
- Release ID
- Current Stage
- Current Request ID
- Review Status
- Review Owner

#### H. Task Details

For the currently selected request, the page should display:

- Task
- Result Summary
- Start Time
- End Time
- Status
- Actions

Suggested task status values:

- Pending
- Running
- Waiting Review
- Approved
- Rejected
- Failed
- Skipped

#### I. Task Actions

Each task should support:

- **Edit**: update task input
- **View Result**: review task execution output
- **Decision** dropdown:
  - Approve
  - Reject
  - Rerun
  - Skip

#### J. Human-in-the-Loop Control

Each task should follow this lifecycle:

1. Execute
2. Review Result
3. Human Decision

The system must not automatically move to the next step after execution. A human decision is required before progression.

#### K. Audit Log (Basic Version)

The MVP should record the following for key actions:

- who performed the action
- action type
- input
- output
- timestamp

Examples of actions to log:

- upload_excel
- create_request
- edit_task_input
- view_result
- approve_task
- reject_task
- rerun_task
- skip_task

#### L. Configuration Management (Basic Version)

The MVP should support UI-based maintenance of key configuration items such as:

- Jenkins URL
- Ansible URL
- other required integration endpoints

These values should not be hardcoded in the codebase.

---

### 4.2 Out of Scope

The following items are not included in this MVP:

- full visual Template Management page
- full Audit Log query page
- deep GitHub integration
- complex role and permission model
- multiple agents going live at the same time
- AI autonomous decision making
- advanced dashboard metrics
- automated approval workflow
- configuration version approval process

---

## 5. Data Model Principles

### 5.1 Release Flow as the Top-Level Entity

Project status should not be derived from a single request.

A **Release Flow** must be introduced to group multiple stage requests under the same project / release journey.

Recommended hierarchy:

- Release Flow
  - Request (SIT / UAT / PROD)
    - Tasks

### 5.2 Release Flow Identification

The system uses **Project ID** (from the Excel `Project ID` column) as the grouping key to link SIT / UAT / PROD uploads to the same Release Flow.

Release ID is **system-generated** after the first upload for a project, using the format:

```
{stage}-{normalized_project_name}-{seq}
```

Example: `sit-paymenthub-0001`

The Release ID remains fixed for the lifetime of the Release Flow. Subsequent stage uploads (UAT, PROD) attach to the same Release Flow identified by Project ID; the Release ID does not change.

Stage is **not stored in the Excel template**. It is selected by the user at upload time through the upload dialog.

---

## 6. Excel Template Strategy

### 6.1 Day 1 Strategy

Day 1 will use the existing fixed Excel template identified as **AMH_HCC_task** (steps table).

The page should support:

- Download Template
- View Sample
- Upload Excel

The backend will parse the file using the current fixed structure.

### 6.2 Known Template Structure and System Interpretation (AMH_HCC_task Sheet)

The template uses a steps table where **one row = one atomic execution step**. Multiple rows may share the same `Task ID` to form a logical task group.

**Stage and Release ID are NOT provided by the Excel file:**
- `Stage` (SIT / UAT / PROD) is selected by the user at upload time via the upload dialog
- `Release ID` is generated by the system after upload using the format `{stage}-{normalized_project_name}-{seq}` (e.g., `sit-paymenthub-0001`)
- The system groups uploads into Release Flows by `Project ID`; a new Release Flow is created if none exists for that project

**Fields the system treats as first-class** (used in execution, ordering, or result review):
- `Project ID` — identifies the project; used as the Release Flow grouping key
- `Step seq#` — execution ordering within a task group; controls which step runs next
- `Step` — name/description of this atomic step
- `Execution Type` — execution mode: `MANUAL` or `AUTO`
  - `MANUAL`: step requires human execution outside the system; script and parameters are displayed as reference
  - `AUTO`: system submits to execution pipeline; result received via callback
- `Script to be executed` and `Parameter (input)` — execution payload (for AUTO) or reference instructions (for MANUAL)
- `Parameter (Expected Output)` — expected result; displayed alongside actual output during verification
- `Task ID` — groups related steps for UI display and sequential ordering context

**Fields stored for UI display only** (do not control execution or workflow state):
- `Project Name`, `Task Name`, `Owner` — display labels
- `Planned Start date/time`, `Planned End date/time` — planning reference shown in task list; do not trigger or gate execution

**Fields stored as raw import metadata** (no workflow behavior in MVP):
- `Activity category`, `Common`, `Dependencies`, `Validation` — preserved in an opaque metadata blob; not processed by any business logic

**Fields explicitly excluded from import:**
- `Start date/time`, `End date/time` — runtime values generated by the system; not read from the spreadsheet
- `Status` — ignored; system always creates Tasks in `Pending` to preserve the human-in-the-loop control gate

### 6.3 Future Direction

A future **Template Management** capability should allow UI-based maintenance of:

- field definitions
- field order
- required flags
- field mapping
- template version

This is not required for the Day 1 MVP, but the overall design should reserve for it.

---

## 7. Audit Log Strategy

The MVP only requires a basic backend audit log.

For each key action, the system should store:

- operator
- action
- input snapshot
- output snapshot
- action time

A dedicated audit query page can be delivered later.

---

## 8. Configuration Management Strategy

Configuration should be treated as a shared WWA capability.

The MVP should support UI-based maintenance of integration endpoints and key runtime configurations.

At minimum, the system should support:

- Jenkins URL
- Ansible URL
- other required endpoint values

The configuration model should be flexible enough to be reused by future agents.

---

## 9. Shared Capabilities for Future Agents

WWA should evolve into a reusable platform layer.

The following should be treated as shared capabilities, not Release-Agent-only features:

- Template Management
- Configuration Management
- Audit Log
- operator traceability
- human decision control pattern

This allows future agents to add only their own main workspace and agent-specific logic while reusing the shared platform capabilities.

---

## 10. MVP Deliverables

The MVP should deliver at least the following:

1. WWA / Release Agent navigation
2. Release Agent main page
3. Excel upload capability using the existing fixed template
4. Deployment Flow Summary
5. Selected Release Flow Details
6. Task Details with Result Summary
7. Edit / View Result / Decision actions
8. basic audit logging
9. basic configuration management

---

## 11. MVP Success Criteria

The MVP can be considered successful if:

- users can upload the fixed Excel template
- the system can create or extend a release flow
- the page can clearly show SIT / UAT / PROD progress
- users can select one release flow and view current request details
- users can review task results and make decisions
- key actions are recorded in audit logs
- Jenkins / Ansible endpoint values can be maintained from UI

---

## 12. One-Line Summary

**WWA / Release Agent MVP = Excel Upload + Release Flow Tracking + Task Result Review + Human Decision Control + Basic Audit Log + Basic Configuration Management**
