# WWA Agent Workspace Hub / Testing Agent Requirement

## 1. Background

**Testing Agent** is the second agent workspace to be onboarded under the **WWA Agent Workspace Hub**. It follows the same integration standard established by Deployment Agent (see `docs/00-context/multi-agent-integration-standard.md`).

The purpose of this requirement is to define the MVP scope for Testing Agent.

Testing Agent reuses the same human-in-the-loop controlled execution workspace pattern as Deployment Agent, but is scoped to **testing activities** rather than deployment activities. It shares the same underlying platform capabilities (Template Management, Configuration Management, Audit Log, Access Management) and the same data model (Release Flow, Request, Task).

Data isolation between Deployment Agent and Testing Agent is achieved through the existing `agent` column on the Request entity. No new database tables are required.

---

## 2. Product Positioning

### 2.1 Testing Agent Positioning

Testing Agent is the second workspace under WWA. It is responsible for testing request onboarding, test execution tracking, result review, and human decision control.

Testing Agent follows the same workflow pattern as Deployment Agent:

- Excel-based request onboarding
- release flow tracking across SIT / UAT / PROD
- request-level and task-level visibility
- task result review
- human decision control
- basic audit logging

### 2.2 Relationship to Deployment Agent

Testing Agent and Deployment Agent are **peer workspaces** under the WWA Agent Workspace Hub. They:

- share the same platform shell, navigation, and authentication
- share the same domain data model (Release Flow, Request, Task)
- share the same backend services and repositories
- are isolated at the data level by the `agent` field on Request (`deployment-agent` vs `testing-agent`)
- each have their own API prefix (`/api/deployment-agent/` vs `/api/testing-agent/`)
- each have their own frontend routes (`/wwa/deployment-agent` vs `/wwa/testing-agent`)

### 2.3 Future Reuse Validation

Onboarding Testing Agent serves as the first validation of the multi-agent integration standard. It should confirm that:

- the agent registry pattern works for adding new agents
- shared capabilities (Audit Log, Configuration, Access Management) work across agents without modification
- data isolation via the `agent` column is sufficient
- the backend service layer is truly agent-agnostic

---

## 3. MVP Objective

The objective of the Testing Agent MVP is to provide a dedicated workspace where users can:

- upload a testing request Excel file
- create or extend a release flow scoped to Testing Agent
- track testing progress across SIT / UAT / PROD
- view task-level test execution results
- review whether the test output matches the expected result
- make a human decision before moving to the next step

The workflow mirrors Deployment Agent exactly, with the primary differentiation being:

- the agent identifier (`testing-agent`)
- the page title and description (Testing Agent, not Deployment Agent)
- the API prefix (`/api/testing-agent/`)
- the route prefix (`/wwa/testing-agent`)

---

## 4. MVP Scope

### 4.1 In Scope

#### A. Navigation

Add Testing Agent to the WWA navigation:

- WWA (level-1 menu)
  - Deployment Agent (level-2, existing)
  - **Testing Agent** (level-2, new)

Testing Agent must appear:

- as an agent card on the WWA Home page
- in the sidebar flyout navigation
- via the agent registry (`agentRegistry.ts`)

#### B. Testing Agent Main Page

The Testing Agent page should include:

1. Page introduction area (titled "Testing Agent")
2. Filter area
3. Testing Flow Summary (release flows scoped to `agent = "testing-agent"`)
4. Selected Release Flow Details
5. Task Details
6. Upload Excel entry and upload dialog

The page structure, layout, and interaction patterns are identical to the Deployment Agent main page.

#### C. Page Introduction Area

Display:

- page title: Testing Agent
- explanation of WWA
- note that the current phase uses API-based orchestration and human review for testing workflows

#### D. Filter Area

Same fields and buttons as Deployment Agent:

- Project, Release ID, Stage, Status
- Upload Excel, Query, Refresh

#### E. Excel Upload

Same capabilities as Deployment Agent:

- Stage selector (SIT / UAT / PROD)
- Upload Excel
- Download Template
- View Sample
- Upload success message
- View Import Log

When uploading through Testing Agent, the system must automatically set `agent = "testing-agent"` on the created Request.

#### F. Testing Flow Summary

Same structure as Deployment Flow Summary, but filtered to show only release flows containing requests where `agent = "testing-agent"`.

Each row displays:

- Project
- Release ID
- SIT / UAT / PROD stage status (Done, Running, Pending)
- Overall Status

#### G. Selected Release Flow Details

Same as Deployment Agent:

- Project, Release ID, Current Stage, Current Request ID, Review Status, Rundown Owner

#### H. Task Details

Same as Deployment Agent:

- Task, Result Summary, Start Time, End Time, Status, Actions
- Same task status values (Pending, Ready For Execution, Executing, Awaiting Review, Approved, Rejected, Failed, Skipped)

#### I. Task Actions

Same as Deployment Agent:

- Edit, Activity, View Result, Run, Record Result, Rerun, Review Decision (Approve / Reject / Skip)

#### J. Human-in-the-Loop Control

Same lifecycle as Deployment Agent:

1. Explicit Run
2. Record / Review Result
3. Human Decision

#### K. Audit Log

Testing Agent actions must be recorded in the shared Audit Log with `agentName = "testing-agent"`. The same action types apply:

- upload_excel, create_request, edit_task_input, view_result, approve_task, reject_task, rerun_task, skip_task

#### L. Configuration Management

Testing Agent may require its own agent-private configuration keys in the future (e.g., test platform URLs, test runner endpoints). For MVP, it reuses the shared configuration infrastructure. New configuration keys should be scoped with a `testing-agent` prefix if needed.

### 4.2 Out of Scope

The following items are not included in this MVP:

- testing-specific domain logic (e.g., test case management, test suite definitions, coverage tracking)
- testing-specific Excel template (reuses the existing fixed template)
- testing-specific task types or execution adapters beyond what Deployment Agent already supports
- testing-specific dashboards or metrics
- AI-driven test selection or prioritization
- integration with external testing platforms (e.g., Selenium, JMeter) beyond Jenkins/Ansible

---

## 5. Data Model

### 5.1 No New Entities

Testing Agent uses the same entity hierarchy as Deployment Agent:

- Release Flow
  - Request (`agent = "testing-agent"`)
    - Tasks

### 5.2 Data Isolation

- Requests created through Testing Agent have `agent = "testing-agent"`
- Requests created through Deployment Agent have `agent = "deployment-agent"` (or null for legacy data)
- The Testing Agent API layer filters all list operations to only return data where `agent = "testing-agent"`
- Legacy data (requests without an agent value) remains visible only in Deployment Agent
- A Release Flow may contain requests from both agents if the same project uploads through both; each agent sees only its own requests within the flow

---

## 6. Excel Template Strategy

### 6.1 Day 1 Strategy

Testing Agent reuses the same fixed Excel template as Deployment Agent (AMH_HCC_task steps table).

The only difference is that uploads through the Testing Agent UI automatically set `agent = "testing-agent"` on the created Request.

### 6.2 Future Direction

If testing workflows require a different template structure (e.g., test case fields, expected vs actual result columns), a testing-specific template can be introduced later. This would also trigger the Template Management shared-capability review per the product positioning decision record.

---

## 7. API Design

### 7.1 API Prefix

All Testing Agent API endpoints use the prefix `/api/testing-agent/`.

### 7.2 Endpoint Mapping

| Testing Agent Endpoint | Mirrors | Behavior Difference |
|---|---|---|
| `GET /api/testing-agent/release-flows` | `GET /api/deployment-agent/release-flows` | Defaults `agent` filter to `testing-agent` |
| `GET /api/testing-agent/release-flows/{id}` | `GET /api/deployment-agent/release-flows/{id}` | Validates flow contains testing-agent requests |
| `POST /api/testing-agent/upload` | `POST /api/deployment-agent/upload` | Forces `agent = "testing-agent"` on created requests |
| `GET /api/testing-agent/upload/template` | `GET /api/deployment-agent/upload/template` | Same template file |
| `PUT /api/testing-agent/tasks/{id}` | `PUT /api/deployment-agent/tasks/{id}` | Same behavior (task inherits agent from parent request) |
| `POST /api/testing-agent/tasks/{id}/decision` | `POST /api/deployment-agent/tasks/{id}/decision` | Same behavior |
| `POST /api/testing-agent/tasks/{id}/record-result` | `POST /api/deployment-agent/tasks/{id}/record-result` | Same behavior |
| `POST /api/testing-agent/tasks/{id}/execute` | `POST /api/deployment-agent/tasks/{id}/execute` | Same behavior |

### 7.3 Backend Implementation Strategy

Testing Agent controllers are **thin delegation wrappers** around the existing domain services. They:

- reuse `ReleaseFlowService`, `TaskService`, `ImportService`, `DecisionEngine`, `RecordResultService`, `AutoExecutionService`
- inject `agent = "testing-agent"` as a default parameter
- do not duplicate any domain logic

---

## 8. Frontend Architecture

### 8.1 Approach

The frontend follows the same agent-parameterization approach:

- A separate axios client for `/api/testing-agent`
- A separate Pinia store (`useTestingAgentReleaseFlowStore`)
- Separate views (`TestingAgentSummaryView`, `TestingAgentDetailView`) or shared parameterized components
- Routes under `/wwa/testing-agent`

### 8.2 Duplication Reduction (Recommended)

To avoid maintaining two near-identical view files, extract shared components:

- `AgentSummaryView.vue` — accepts agent name, store, and route prefix as props
- `AgentDetailView.vue` — accepts agent name, store, and API functions as props

Both Deployment Agent and Testing Agent views become thin wrappers around these shared components.

---

## 9. Access Model

### 9.1 Platform Access

Testing Agent must be visible on the WWA Home page and flyout navigation. Visibility is controlled by the agent registry (`enabled: true`).

### 9.2 Agent-Level Access

Testing Agent uses the same role model as Deployment Agent:

- DEVELOPER: upload, view, execute tasks
- TL: review results, make decisions
- DEVOPS_ADMIN: configure, manage
- AUDIT: view audit records
- MANAGEMENT: view summary

Access scopes are evaluated per `(application, snowGroup)` as with Deployment Agent. The `AccessScope` model does not include `agent` — a user with access to an application has access to it across both agents.

---

## 10. MVP Deliverables

1. Testing Agent entry in the agent registry (enabled)
2. Testing Agent card on the WWA Home page
3. Testing Agent in sidebar flyout navigation
4. Testing Agent main page at `/wwa/testing-agent`
5. Testing Agent detail page at `/wwa/testing-agent/release-flows/:id`
6. Backend API layer at `/api/testing-agent/` (release flows, upload, tasks, decisions)
7. Data isolation via `agent = "testing-agent"` on requests
8. Audit log entries with `agentName = "testing-agent"`

---

## 11. MVP Success Criteria

The Testing Agent MVP can be considered successful if:

- Testing Agent card appears on the WWA Home page
- users can navigate to `/wwa/testing-agent` and see a dedicated testing workspace
- users can upload the Excel template through Testing Agent and the created requests are tagged with `agent = "testing-agent"`
- the Testing Agent summary only shows release flows with testing-agent requests
- the Deployment Agent summary does NOT show testing-agent-only flows
- users can view task details, record results, and make decisions within Testing Agent
- all actions are recorded in the shared audit log with `agentName = "testing-agent"`
- all existing Deployment Agent functionality remains unaffected
- all existing tests pass (`mvn test`)
- frontend builds without errors (`cd frontend && npm run build`)

---

## 12. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| View duplication between agents leads to maintenance drift | MEDIUM | Extract shared components (Phase 4 in implementation) |
| Agent identity string hardcoded in multiple places | LOW | Define constants on both backend and frontend |
| Legacy data without agent value becomes invisible | LOW | Deployment Agent does not enforce agent filter strictly; legacy data remains visible there |
| Cross-agent release flows (same project uploads to both agents) | LOW | By design; each agent sees only its own requests within the flow |
| Template may not fit testing use cases | LOW | Out of scope for MVP; revisit if testing-specific template is needed |

---

## 13. One-Line Summary

**Testing Agent MVP = Same Deployment Agent Workflow + Separate Namespace + Data Isolation via Agent Column**
