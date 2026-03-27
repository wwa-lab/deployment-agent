# Data Flow: Deployment Agent

> **Source**: architecture.md, spec.md, design.md
> **Last updated**: 2026-03-28

---

## Overview

This document describes how data flows through the Deployment Agent system, from external inputs (authentication, access-management actions, Excel upload, task actions) through processing layers to persistence and external systems.

---

## 1. Excel Import Data Flow

```mermaid
flowchart LR
    subgraph Input
        Excel[Excel File<br/>AMH_HCC_task sheet]
        Stage[Stage selector<br/>SIT / UAT / PROD]
        Scope[Upload scope<br/>Application / SNOW Group / Agent]
    end

    subgraph Parse["Parse Layer"]
        Parser[ExcelParserService]
        Validate[Schema Validation]
    end

    subgraph Transform["Transform Layer"]
        Import[ImportService]
        Map[Field Mapping]
    end

    subgraph Persist["Persistence"]
        RF[(ReleaseFlow)]
        Req[(Request)]
        Task[(Task)]
        Audit[(AuditLogEntry)]
    end

    Excel --> Parser
    Stage --> Import
    Scope --> Import
    Parser --> Validate
    Validate -->|Valid| Import
    Validate -->|Invalid| Error[422 Validation Error]
    Import --> Map
    Map --> RF
    Map --> Req
    Map --> Task
    Import --> Audit
```

### Field Mapping Summary

| Excel Field | Target Entity | Target Attribute | Classification |
|---|---|---|---|
| Project ID | ReleaseFlow | project_id | Core - grouping key |
| Project Name | ReleaseFlow | project_name | Display |
| Task ID | Task | task_group_id | Display grouping |
| Task Name | Task | task_group_name | Display |
| Step seq# | Task | step_seq | Core - ordering |
| Step | Task | task_name | Core - identity |
| Execution Type | Task | execution_type | Core - MANUAL/AUTO |
| Script to be executed | Task | input_parameters.script | Core - payload |
| Parameter (input) | Task | input_parameters.parameters | Core - payload |
| Parameter (Expected Output) | Task | expected_output | Core - verification |
| Owner | Task | owner | Display |
| Planned Start/End | Task | planned_start_time/end_time | Display only |
| Activity category, Common, Dependencies, Validation | Task | import_metadata (JSON) | Metadata blob |
| Status, Start/End date/time | -- | Not stored | Dropped |
| *(from UI)* Stage | Request | stage | Core |
| *(from UI)* Application | Request | application | Runtime scope |
| *(from UI)* SNOW Group | Request | snow_group | Runtime scope |
| *(from UI)* Agent | Request | agent | Runtime scope |
| *(system-derived)* Rundown owner | Request | owner | Derived from a single imported task owner, otherwise uploader |
| *(system-generated)* Release ID | ReleaseFlow | release_id | Core |

---

## 2. Task Execution Data Flow

### 2.1 MANUAL Execution

```mermaid
flowchart LR
    Operator["Task Owner / DevOps Admin"] -->|Run| StartAPI["POST /tasks/:id/start-manual"]
    StartAPI --> TS["TaskService.startManualExecution"]
    TS --> StartState["Task status -> Executing"]

    Operator -->|Record Result| ResultAPI["POST /tasks/:id/record-result"]
    ResultAPI --> RRS["RecordResultService"]
    RRS --> TEH[("TaskExecutionHistory\nresult_summary + result_logs")]
    RRS --> ReviewState["Task status -> Awaiting_Review"]
    RRS --> AL[("AuditLogEntry\naction: record_result")]
```

### 2.2 AUTO Execution

```mermaid
flowchart LR
    User[Task Owner / DevOps Admin] -->|Run| API[POST /tasks/:id/submit-auto]
    API --> AES[AutoExecutionService]
    AES --> SM[TaskStateMachine<br/>validates transition]
    AES --> TEH1[(TaskExecutionHistory<br/>created with attempt_number)]

    AES --> Adapter{Adapter Selection}
    Adapter -->|JENKINS| JA[JenkinsExecutionAdapter]
    Adapter -->|ANSIBLE| AA[AnsibleExecutionAdapter]

    JA -->|POST /job/:name/build| Jenkins[Jenkins Server]
    Jenkins -->|Location header<br/>→ queue ID → build number| JA
    JA --> TEH2[(TaskExecutionHistory<br/>external_execution_id<br/>external_job_url<br/>submission_status)]

    AA -->|POST /api/v2/job_templates/:id/launch/| Ansible[Ansible Tower]
    Ansible -->|JSON response<br/>→ job ID| AA
    AA --> TEH2

    AES --> AL[(AuditLogEntry<br/>action: auto_submit)]
```

### 2.3 External Execution Data Stored

| Field | Source | Example |
|---|---|---|
| external_system_type | Adapter selection | JENKINS, ANSIBLE |
| external_execution_id | Response from external system | Build #42, Job #789 |
| external_job_url | Constructed from response | `https://jenkins/job/deploy/42/console` |
| submitted_at | System clock | 2026-03-19T10:00:00Z |
| submission_status | Adapter result | SUBMITTED, FAILED |
| submission_message | Adapter result | "Build queued successfully" |

---

## 3. Decision Data Flow

```mermaid
flowchart TD
    Reviewer[Task Owner / DevOps Admin] -->|Approve / Reject / Rerun / Skip| API[POST /tasks/:id/decision]
    API --> DE[DecisionEngine]

    DE --> SM[TaskStateMachine<br/>validate transition]

    DE -->|Approve| Approve[Task → Approved]
    DE -->|Reject| Reject[Task → Rejected<br/>Request → Rejected<br/>ReleaseFlow → Rejected]
    DE -->|Skip| Skip[Task → Skipped]
    DE -->|Rerun| Rerun[Task → Ready_For_Execution<br/>new TaskExecutionHistory created]

    Approve --> Prog[ReleaseFlowProgressionService]
    Skip --> Prog

    Prog --> Agg[ReleaseFlowAggregation<br/>recompute Request & ReleaseFlow statuses]
    Prog --> Next{Next task exists?}
    Next -->|Yes| Promote[Next task → Ready_For_Execution]
    Next -->|No| Complete[Request/ReleaseFlow → Completed]

    DE --> AL[(AuditLogEntry<br/>action: approve/reject/rerun/skip)]
```

### Status Aggregation Rules

Statuses bubble up from Task → Request → ReleaseFlow using pure functions in `ReleaseFlowAggregation`:

| Level | Computed From | Rule |
|---|---|---|
| Request status | All child Task statuses | All terminal → Completed/Failed/Rejected; Any active → Running; All Pending → Pending |
| ReleaseFlow flow_status | All child Request statuses | Same aggregation logic |
| Stage summary | Tasks in that stage | Done (all terminal), Running (any active), Pending (all pending) |

---

## 4. Authentication and Authorization Data Flow

```mermaid
flowchart LR
    User["User Browser"] -->|POST /auth/login<br/>employeeId + password| Auth["AuthController"]
    Auth --> AS["AuthService"]
    AS --> Provider["Configured Authentication Provider"]
    Provider -->|Current: stub provider<br/>Future: Team Book adapter| Validate{"Valid credentials?"}
    Validate -->|Yes| AG[AccessGrant Resolution<br/>deny-by-default]
    Validate -->|No| Err[401 Unauthorized]

    AG -->|No active grant| Forbidden[403 Access not granted / suspended]
    AG -->|Active grant| Session[HttpSession<br/>stores user identity<br/>and authorization profile]

    Session --> SF[SessionAuthFilter<br/>reads UserContext from session<br/>populates SecurityContext]
    SF --> API[All /api/* requests<br/>authenticated with effective access]
```

### Auth Data Objects

| Object | Content | Lifecycle |
|---|---|---|
| LoginRequestDto | employeeId, password | Request-scoped |
| UserContext | employeeId, displayName, identity context | Stored in HttpSession |
| Authorization Profile `[Phase 1]` | access status, assigned roles, effective permissions, applicable scopes | Resolved during login / session restore |
| SecurityContext | Authentication with UserContext | Per-request from session |

### 4.1 Access Management Admin Flow

```mermaid
flowchart LR
    Admin[DevOps Admin] -->|GET/POST/PATCH /access-grants| AMC[Access Management API]
    AMC --> AGS[Access Grant Service]
    AGS --> Validate[Grant validation<br/>roles, scopes, status, note]
    AGS --> DB[(DA_ACCESS_GRANT)]
    AGS --> AL[(AuditLogEntry<br/>access governance action)]
```

### 4.2 Authorization Resolution Notes

- Authentication confirms login identity through the configured provider abstraction
- Authorization then resolves local product access and `Application + SNOW Group` visibility through Access Grants
- Users without an active Access Grant are blocked before entering the Deployment Agent workspace
- Frontend route visibility and backend endpoint access are expected to use the same effective permission set and scope evaluation in Phase 1

---

## 5. Configuration Data Flow

```mermaid
flowchart LR
    Admin[DevOps Admin] -->|GET/PUT /config| CC[ConfigurationController]
    CC --> CS[ConfigurationService]
    CS --> Validate[Validation rules<br/>per ConfigKey]
    CS --> DB[(ConfigurationItem table)]

    subgraph Consumers["Runtime Consumers"]
        JA[JenkinsExecutionAdapter<br/>reads jenkins_url, jenkins_user, jenkins_api_token]
        AA[AnsibleExecutionAdapter<br/>reads ansible_url, ansible_user, ansible_api_token]
    end

    DB -.->|Read at execution time| Consumers
    CS --> AL[(AuditLogEntry<br/>action: config_update)]
```

### Configuration Keys

| Key | Consumer | Purpose |
|---|---|---|
| jenkins_url | JenkinsExecutionAdapter | Jenkins server base URL |
| jenkins_user | JenkinsExecutionAdapter | Basic auth username |
| jenkins_api_token | JenkinsExecutionAdapter | Basic auth API token |
| ansible_url | AnsibleExecutionAdapter | Ansible Tower base URL |
| ansible_user | AnsibleExecutionAdapter | Display/audit only |
| ansible_api_token | AnsibleExecutionAdapter | Bearer token auth |

---

## 6. Audit Data Flow

```mermaid
flowchart TD
    Upload["Upload/Import"] --> ALS["AuditLoggerService\nREQUIRES_NEW propagation"]
    Edit["Task Edit"] --> ALS
    Result["Run / Record Result"] --> ALS
    Decision["Decision"] --> ALS
    AutoSub["Auto Submit"] --> ALS
    Config["Config Update"] --> ALS
    Access["Access Grant Create / Update / Suspend / Reactivate"] --> ALS

    ALS --> DB[(DA_AUDIT_LOG_ENTRY)]
    DB --> AuditAPI["GET /audit-logs\nsigned-in users, filtered by scope"]
    AuditAPI --> AuditView["Audit Log View\nread-only list"]
```

### Audit Entry Structure

| Field | Description |
|---|---|
| audit_log_id | System-generated UUID |
| operator_id | From authenticated UserContext |
| operator_role | From authenticated UserContext |
| action_type | upload, edit, view_result, approve, reject, rerun, skip, auto_submit, config_update, access-governance actions |
| timestamp | System clock at event time |
| release_flow_id | Nullable context reference |
| request_id | Nullable context reference |
| task_id | Nullable context reference |
| application | Nullable scope field for filtering and traceability |
| snow_group | Nullable scope field for filtering and traceability |
| agent | Nullable scope field for filtering and traceability |
| context_payload | JSON with action-specific details |

### Audit Isolation

`AuditLoggerService` uses `Propagation.REQUIRES_NEW` to ensure audit log writes succeed independently of the business transaction. If the business operation rolls back, the audit entry is still persisted.

---

## 7. Summary: End-to-End Data Path

```mermaid
flowchart TB
    Login[Workspace Login] --> Authz[Access Grant Resolution]
    Authz -->|Authorized| Excel[Excel Upload]
    Authz -->|Denied| Blocked[Access Denied]
    Excel --> Import[Import Pipeline]
    Import --> RF[ReleaseFlow + Request + Tasks]
    RF --> Execution{Execution Path}
    Execution -->|MANUAL| Manual[Owner/Admin records result]
    Execution -->|AUTO| Auto[Submit to Jenkins/Ansible]
    Manual --> Review[Awaiting Review]
    Auto --> Review
    Review --> Decision[Owner/Admin Decision]
    Decision -->|Approve/Skip| Progress[Progression Engine]
    Decision -->|Reject| Terminal[Flow Terminated]
    Decision -->|Rerun| Execution
    Progress --> Complete[Flow Completed]

    Authz -.-> Audit[(Audit Log)]
    Import -.-> Audit
    Manual -.-> Audit
    Auto -.-> Audit
    Decision -.-> Audit
```
