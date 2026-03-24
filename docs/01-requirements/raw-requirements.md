# Raw Requirements

## Requirement Sources
Where are the requirements coming from?

- User interviews
  - Requirements come from ongoing discussions with the project sponsor and core users around the WWA operating model and the first agent workspace, Deployment Agent.

- Business goals
  - Establish WWA (Work With Agent) as a reusable platform layer, with Deployment Agent as the first implemented workspace.
  - Improve deployment execution visibility, control, and traceability through a structured human-in-the-loop workflow.
  - Enable later reuse of shared capabilities such as Template Management, Configuration Management, and Audit Log.

- Existing workflow issues
  - Deployment requests are currently initiated through Excel and external orchestration tools, but the workflow lacks a unified execution workspace.
  - Users cannot easily track release progress across SIT / UAT / PROD in one consolidated view.
  - Task results may be produced by systems such as Jenkins or Ansible, but review and go/no-go decisions are not standardized in one interface.
  - Some key runtime configuration values are integration-specific and should not be hardcoded.

- Competitive references
  - The requirement references a platform-style agent model where a shared shell supports multiple future agent workspaces, each with its own task logic but shared governance capabilities.

- Personal judgment
  - This MVP should prioritize operational clarity and controlled progression over autonomous decision-making.
  - The first version should focus on making deployment flow visible, reviewable, and auditable before expanding into more advanced automation.

## Problem Statements
What problems need to be solved?

- There is no unified workspace to onboard deployment requests, track release flows, inspect task results, and make controlled decisions before moving to the next step.

- Deployment progress across SIT / UAT / PROD is difficult to monitor consistently at the release-flow level rather than only at the individual request level.

- Execution outputs may exist, but there is no standardized human review mechanism that prevents premature progression after task execution.

- Operational traceability is insufficient: important actions such as upload, edit, approval, rejection, rerun, and skip need to be logged clearly.

- Shared capabilities needed by future agents are not yet abstracted at the platform level.

## User Needs
What do users actually need?

- Developers need to upload deployment requests and trigger a controlled release flow using the existing Excel template.

- TLs need to review execution and verification results, then make explicit human decisions before the release continues.

- DevOps Admins need to maintain key integration and runtime configurations from UI rather than code changes.

- Audit teams or management need to inspect key actions and process traces through auditable records.

- Users need a single Deployment Agent workspace inside WWA to manage deployment activities in a structured way.

- Users need a summary view that shows each release flow and its stage progress across SIT / UAT / PROD with simple status values such as Done, Running, and Pending. :contentReference[oaicite:4]{index=4}

- Users need task-level visibility, including task name, result summary, timing, status, and available actions. :contentReference[oaicite:6]{index=6}

- The MVP must ensure the core workflow can run through successfully from request to process to verification to decision.

## Initial Functional Ideas
What capabilities are currently being considered?

### Platform Navigation

- Build WWA as a two-level navigation structure: WWA as a first-level menu with a second-level flyout panel containing workspace entries.

- Include first-level placeholder applications alongside WWA so the sidebar reads like a broader platform shell.

- Shared capability entries under WWA flyout:
  - Deployment Agent
  - Template Management
  - Configuration Management
  - Audit Log
  - Deployment Agent

- Show shared-capability navigation for users without access, with page-level access guidance instead of hiding menu entries.

- Build the Deployment Agent main page with the following sections:
  - page introduction area
  - filter area
  - Deployment Flow Summary with SIT / UAT / PROD stage status columns
  - Selected Release Flow Details
  - Task Details with category column and execution mix (manual/auto counts and percentages)
  - Upload Excel entry and dialog
  - Selected Release Flow Details

- Support Excel-based request onboarding, including:
  - Upload Excel
  - Download Template (working Excel template download)
  - View Sample
  - upload success message
  - View Import Log entry

### Release Flow

- Represent the main business object as a Release Flow that groups multiple stage requests under the same journey.

- Show stage-level Rundown Information panel per stage tab, including editable fields for SNOW group, application, site, and estimated remaining time.

- Provide request-level actions: Refresh, Start Deployment, and Mark as Failed.

### Task Actions and Permissions

- Show task-level actions: Edit, View Result, and Decision dropdown (Approve / Reject / Rerun / Skip), with state-based disabling and tooltips for disabled actions.

- Restrict task actions (edit input, record results, submit auto execution, apply decisions) to the task owner or DEVOPS_ADMIN only.

- Move MANUAL task result submission into the Edit dialog.

- Wire View Result to execution history so external job links and stored output can be viewed from the task result modal.

### Critical Task Gate

- Add a first-class Critical (Y/N) task field, surfaced in the release detail table, included in Excel import and template flow.

- Use the Critical flag as a workflow gate: tasks marked critical must be reviewed before the next pending task is released.

### Task Activity

- Provide a task-level Activity dialog on the release-flow detail page to trace who did what, when it happened, and related input/output from audit and execution records.

### Decision Effects

- Define the decision effects in MVP:
  - Approve: continue the release flow normally
  - Reject: stop the current release flow
  - Rerun: rerun the current step
  - Skip: skip the current step and continue

### Template Management

- Build a full Template Management workspace for deployment templates with CRUD lifecycle (create, view, edit, clone, delete).

- Treat each template as a multi-task deployment blueprint, with task tables aligned to the current deployment task structure.

- Support local task authoring within templates: Add Task, Edit, Delete, dependency maintenance, and Critical (Y/N) flag.

- Support template creation via Manual Entry and Upload Excel tabs.

- Provide a More menu per template row with Clone, Edit, and Delete actions.

- Template row selection directly switches the Template Details workspace (no separate View Details button).

### Configuration Management

- Redesign Configuration Management into a component workspace for Jenkins, Ansible, and callback integrations, while preserving a Raw Configuration tab for key-level admin edits.

- Open Configuration Management for read-only access to all signed-in users, with edit actions restricted to DEVOPS_ADMIN.

- Rework the Configuration tab into a filterable admin table with application, owning-group, config-item, and value columns.

### Audit Log

- Record a basic audit log for key actions such as upload_excel, create_request, edit_task_input, view_result, approve_task, reject_task, rerun_task, and skip_task.

- Open Audit Log for read-only access to all signed-in users.

- Present audit log as an action-record view with User, Time, Type, and Detail columns, plus Staff Id search for faster tracing.

- Redact sensitive config-update values from audit responses.

## Initial Non-Functional Expectations
What are the expectations around performance, maintainability, reliability, security, etc.?

- Maintainability
  - WWA should be designed as a reusable platform layer, not as a one-off page. :contentReference[oaicite:12]{index=12}
  - Shared capabilities should be separated from Deployment-Agent-specific logic.

- Reliability
  - The system must not auto-progress after execution; human review is required before moving forward. :contentReference[oaicite:14]{index=14}
  - The MVP should primarily guarantee that the core workflow can complete end-to-end from request to process to verification to decision.

- Explainability
  - Users should be able to inspect result summaries and explicitly decide the next action.
  - The workflow should make the current review state and responsible owner visible.

- Security and control
  - Deployment Agent MVP is not intended to be fully autonomous. :contentReference[oaicite:16]{index=16}
  - Human-in-the-loop decision control is a core control mechanism, not an optional enhancement.

- Traceability
  - Key operator actions should be logged with who, what, input, output, and timestamp. :contentReference[oaicite:18]{index=18}

## Open Questions
What is still unclear?

- What exact fields are included in the Day-1 fixed Excel template, and which are mandatory for onboarding?

- Is TL review always performed at the task level, or can it also happen at the request or stage level?

- After Reject stops the current release, what exact status should be displayed for the Release Flow and current Request?

- After Rerun, should the system preserve the previous execution record and append a new run history?

- After Skip, should the next step become immediately available, or should additional confirmation still be required?

- What exact content should be shown in View Result: summary only, raw logs, parsed output, or all of them?

- How should audit logs be surfaced before a full query page is implemented?

## Assumptions
What assumptions are currently being made?

- WWA is a platform layer that will host multiple future agent workspaces, and Deployment Agent is the first one.

- The MVP is for FinBlock and is not intended to launch multiple agents simultaneously. :contentReference[oaicite:20]{index=20}

- The MVP is a controlled execution workspace, not a fully autonomous deployment agent. :contentReference[oaicite:22]{index=22}

- Primary actor assumptions for the current workflow are:
  - Developer uploads requests
  - TL performs review and decision
  - DevOps Admin maintains configuration
  - Audit team or management views audit records

- Release Flow is the top-level business entity, with Request and Task as lower-level entities. :contentReference[oaicite:24]{index=24}

- Stage values of interest are SIT, UAT, and PROD.

- Day 1 will use the existing fixed Excel template rather than a dynamic template-definition system. :contentReference[oaicite:26]{index=26}

## Notes
- The current prototype and requirement both support a human-in-the-loop task lifecycle where execution result review is followed by explicit decision control. :contentReference[oaicite:28]{index=28} :contentReference[oaicite:29]{index=29}

- The main MVP success target at this stage is not completeness of all surrounding capabilities, but successful completion of the core request → process → verification → decision flow.

- This raw requirements file is now suitable to be used as the direct input for req-to-user-story.
