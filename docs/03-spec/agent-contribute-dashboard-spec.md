# Feature Specification: Agent Contribute Dashboard

> **Source stories:** ACD-01 through ACD-05
> **Spec status:** Backfilled / Implemented
> **Last updated:** 2026-05-26

---

## 1. Overview

### 1.1 Feature Summary

Agent Contribute Dashboard is a WWA shared-control page that maps the seven Qilianshan SDLC stages to implementation status, ownership, and agent contribution coverage.

### 1.2 Business Objective

The dashboard makes ownership and co-build contribution visible across agent teams, helps new users understand the SDLC agent landscape, and gives management a factual contribution view without introducing opaque scoring.

### 1.3 In-Scope Outcome

At delivery, WWA users can open a dashboard that shows seven SDLC stages, role ownership, contribution items, Confluence guideline/feedback links, and persisted admin-maintained implementation status.

### 1.4 Out of Scope

- Agent registration.
- Workflow execution.
- Performance scoring.
- Automatic Confluence integration.
- New persistent entity model for dashboard baseline data.

---

## 2. Actors

| Actor | Role |
|---|---|
| WWA Viewer | Reads dashboard coverage and links. |
| DEVOPS_ADMIN | Updates stage implementation status. |
| Agent Owner | Accountable owner for a stage or agent contribution area. |
| Sub-agent Owner | Owner of a contribution item within a stage. |
| Process Owner | Owner of process correctness and stage gate alignment. |
| Technical Leader | Owner of technical direction for the contribution item. |
| Co-Build Partner | Supporting contributor or team role. |

---

## 3. Functional Scope

### 3.1 Capability Domains

1. **Stage Coverage Map**: Shows the seven-stage SDLC flow and status at a glance.
2. **Coverage Matrix**: Lets users filter stages by implementation status and select a stage.
3. **Stage Detail Panel**: Shows selected stage description, Confluence links, status, accountability, and contribution items.
4. **Admin Status Control**: Lets `DEVOPS_ADMIN` persist status overrides.
5. **Static Baseline Data**: Provides dashboard seed content from frontend JSON.

### 3.2 Lifecycle Stages

The dashboard models these stages in order:

1. Planning
2. Estimation
3. Discovery
4. Build
5. Testing
6. Deployment
7. Maintenance

### 3.3 Status Model

| Value | Label | Meaning |
|---|---|---|
| `implemented` | Implemented | Available in the current platform baseline. |
| `in-progress` | In Progress | Currently being built out. |
| `backlog` | Backlog | Planned and tracked but not started. |
| `not-implemented` | Not Implemented | Target-state stage not yet implemented. |

Default baseline:

| Stage | Default Status |
|---|---|
| Planning | Implemented |
| Estimation | Implemented |
| Discovery | Not Implemented |
| Build | Implemented |
| Testing | In Progress |
| Deployment | Implemented |
| Maintenance | Not Implemented |

---

## 4. Functional Requirements

### 4.1 Stage Coverage

- **ACD-FR-01**: The dashboard shall display exactly seven Qilianshan SDLC stages.
- **ACD-FR-02**: The dashboard shall display a summary count for Implemented, In Progress, Backlog, Not Implemented, and Contribution Items.
- **ACD-FR-03**: The dashboard shall display a horizontal SDLC Coverage Map with stage name, status, owner, and item count.

### 4.2 Stage Selection and Detail

- **ACD-FR-04**: Selecting a stage shall update the right-side detail panel.
- **ACD-FR-05**: The detail panel shall display stage description and implementation status.
- **ACD-FR-06**: The detail panel shall display Agent Owner, Process Owners, Technical Leaders, Co-Build Partners, and I-E-O-V Gate.
- **ACD-FR-07**: The detail panel shall display each contribution item with Sub-agent Owner, Process Owner, Technical Leader, Co-Build Partners, and contribution description.

### 4.3 Filtering

- **ACD-FR-08**: The coverage matrix shall support status filtering for All, Implemented, In Progress, Backlog, and Not Implemented.
- **ACD-FR-09**: If the selected stage is filtered out, the dashboard shall select the first visible stage.

### 4.4 Confluence Links

- **ACD-FR-10**: Each stage shall include at least two resource links: Guideline and Feedback.
- **ACD-FR-11**: Resource links shall open in a new browser tab with `noopener noreferrer`.
- **ACD-FR-12**: Link labels, descriptions, and URLs shall be data-driven from dashboard configuration.

### 4.5 Admin Status Updates

- **ACD-FR-13**: The system shall expose a read API for persisted stage status overrides.
- **ACD-FR-14**: The system shall expose an update API for stage status overrides.
- **ACD-FR-15**: The update API shall require `DEVOPS_ADMIN`.
- **ACD-FR-16**: The backend shall reject unknown stage keys and unsupported status values.
- **ACD-FR-17**: The frontend shall merge persisted status overrides with static baseline data at render time.

### 4.6 Content Rules

- **ACD-FR-18**: The dashboard shall not present scores, ranking, or contributor leaderboards.
- **ACD-FR-19**: The dashboard UI shall use English-only copy.
- **ACD-FR-20**: The dashboard shall not use nested mountain terminology.

---

## 5. Non-Functional Requirements

- **Security:** Only `DEVOPS_ADMIN` may persist status changes.
- **Validation:** Backend must validate all status keys and values before saving.
- **Maintainability:** Static content must be in a structured JSON file so role and link updates do not require template changes.
- **Accessibility:** Stage rows and controls should remain keyboard accessible.
- **Performance:** Static dashboard rendering should not require additional backend calls beyond status override loading.
- **Auditability:** Status overrides are persisted through the platform configuration domain.

---

## 6. Data / Configuration Requirements

### 6.1 Stage Baseline

| Field | Description |
|---|---|
| `key` | Stable stage identifier. |
| `name` | Display name. |
| `focus` | Short stage focus. |
| `implementationStatus` | Default status value. |
| `implementationLabel` | Default display label. |
| `implementationNote` | Default status explanation. |
| `description` | Stage description. |
| `gate` | Stage gate guidance. |
| `resourceLinks` | Guideline and feedback link collection. |
| `workstreams` | Contribution items. |
| `agentOwner` | Stage-level agent owner. |

### 6.2 Contribution Item

| Field | Description |
|---|---|
| `name` | Contribution item name. |
| `agentName` | Agent that covers the item. |
| `contribution` | Plain-language contribution description. |
| `subAgentOwner` | Item owner. |
| `processOwner` | Process owner. |
| `technicalLeader` | Technical leader. |
| `coBuild` | Co-build partner list. |

### 6.3 Persisted Status Override

Status overrides are stored under platform configuration key `agent_contribution_dashboard_statuses`.

Logical payload:

```json
{
  "planning": "implemented",
  "testing": "in-progress"
}
```

---

## 7. API Requirements

| Method | Path | Purpose | Access |
|---|---|---|---|
| GET | `/api/platform/agent-contribute-dashboard/statuses` | Read persisted status overrides. | Authenticated WWA user |
| PUT | `/api/platform/agent-contribute-dashboard/statuses` | Update stage status overrides. | `DEVOPS_ADMIN` |

Error rules:

- Unknown stage key: validation error.
- Unsupported status value: validation error.
- Non-admin update: forbidden.

---

## 8. Testing Requirements

- Frontend test validates dashboard data shape, seven stages, status defaults, links, and forbidden terminology.
- Backend controller test validates read/update API, admin authorization, accepted status values, unknown stage key rejection, and rejected invalid payloads.
- Frontend build must pass.
- Backend targeted test must pass for the dashboard controller.

---

## 9. Risks / Ambiguities

- Placeholder Confluence links must be replaced before production release.
- Status update UX currently lives in the dashboard detail panel; product may later prefer a separate admin page.
- Management use must remain descriptive; adding metrics later would require explicit scoring rationale and a separate SDD change.

---

## 10. Requirement Traceability

| ACD-REQ | Covered By (User Story) | Covered By (FR) |
|---|---|---|
| ACD-REQ-01 | ACD-01 | ACD-FR-01 |
| ACD-REQ-02 | ACD-01 | ACD-FR-01, ACD-FR-02, ACD-FR-03 |
| ACD-REQ-03 | ACD-01, ACD-02 | ACD-FR-01, ACD-FR-02, ACD-FR-03, ACD-FR-08, ACD-FR-09 |
| ACD-REQ-04 | ACD-03 | ACD-FR-15 |
| ACD-REQ-05 | ACD-03 | ACD-FR-13, ACD-FR-14, ACD-FR-16, ACD-FR-17 |
| ACD-REQ-06 | ACD-02 | ACD-FR-06, ACD-FR-07 |
| ACD-REQ-07 | ACD-02 | ACD-FR-04, ACD-FR-05, ACD-FR-06, ACD-FR-07 |
| ACD-REQ-08 | ACD-04 | ACD-FR-10, ACD-FR-11, ACD-FR-12 |
| ACD-REQ-09 | ACD-05 | ACD-FR-19 |
| ACD-REQ-10 | ACD-05 | ACD-FR-18, ACD-FR-20 |
