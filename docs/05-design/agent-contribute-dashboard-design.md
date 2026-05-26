# Detailed Design: Agent Contribute Dashboard

**Date:** 2026-05-26
**Status:** Backfilled / Implemented
**Source:** `docs/04-architecture/agent-contribute-dashboard-architecture.md`

---

## 1. Overview

This design defines the implementation details for the Agent Contribute Dashboard MVP. The design keeps the page concise, separates static contribution baseline data from mutable stage status overrides, and aligns with the existing WWA platform shell.

---

## 2. Design Scope

### In Scope

- WWA route and registry entry.
- Dashboard view layout and interactions.
- Static stage/contribution JSON model.
- Platform API client.
- Backend controller and configuration service.
- Admin status update behavior.
- Dashboard-focused tests.

### Out of Scope

- Agent registration.
- Full admin configuration editor for all dashboard content.
- Confluence API integration.
- Performance score model.
- New database schema.

---

## 3. UI / User Flow Design

### 3.1 Page Structure

The page is organized into five major areas:

1. **Header**: Page identity, source note, and update metadata.
2. **Overview Card**: Short purpose statement.
3. **Status Summary**: Count of Implemented, In Progress, Backlog, Not Implemented, and Contribution Items.
4. **SDLC Coverage Map**: Horizontal seven-stage stage map.
5. **Coverage Layout**:
   - Left: filterable matrix.
   - Right: selected-stage detail panel.

### 3.2 Interaction Rules

- Clicking a stage in the coverage map selects that stage.
- Clicking a matrix row selects that stage.
- Status filter changes matrix rows and selects the first visible stage if needed.
- Selected stage detail panel updates without route change.
- Confluence links open in new tabs.
- Admin status save is disabled unless selected status differs from current status.

### 3.3 Visual Rules

- The dashboard must remain compact and scan-friendly.
- Cards use restrained borders and 8px radius.
- Status colors are consistent between map, matrix, and detail panel.
- English-only UI copy.
- No score or ranking UI.

---

## 4. Data Design

### 4.1 Static Dashboard Data

File:

`frontend/src/config/agentContributionDashboard.json`

The JSON contains:

- `summary`
- `stages[]`
- `resourceLinks[]`
- `workstreams[]`

### 4.2 Frontend Types

Logical types:

- `StageImplementationStatus`
- `StageResourceLink`
- `Workstream`
- `SdlcStage`
- `DashboardData`

### 4.3 Status Merge Rule

At render time:

1. Load static stage from JSON.
2. Load persisted override map from backend.
3. For each stage, use override value if present; otherwise use JSON default.
4. Derive label and note from frontend `STATUS_OPTIONS`.

---

## 5. API / Interface Design

### 5.1 Frontend API Client

File:

`frontend/src/api/agentContributionDashboard.ts`

Responsibilities:

- Define `StageImplementationStatus` union.
- Define status response contract.
- Provide `getAgentContributionDashboardStatuses`.
- Provide `updateAgentContributionDashboardStatuses`.

### 5.2 Backend Controller

File:

`src/main/java/com/wwa/agenthub/platform/web/shared/AgentContributionDashboardController.java`

Responsibilities:

- Route under `/api/platform/agent-contribute-dashboard`.
- Return status override payload.
- Enforce `DEVOPS_ADMIN` for update.
- Delegate validation/persistence to domain service.

### 5.3 Backend Config Service

File:

`src/main/java/com/wwa/agenthub/domain/configuration/AgentContributionDashboardConfigService.java`

Responsibilities:

- Maintain allowed stage key set.
- Maintain allowed status set.
- Validate incoming update payload.
- Serialize/deserialize status map.
- Delegate persistence to `ConfigurationService`.

---

## 6. Security / Audit / Reliability Design

### Access Control

- Read: authenticated WWA user.
- Update: `DEVOPS_ADMIN`.

### Validation

- Reject unknown stage keys.
- Reject unsupported status values.
- Ignore invalid stored values only when normalizing frontend-loaded responses.

### Reliability

- If status overrides fail to load, the dashboard can still render static baseline data and show an error message.
- Save errors remain visible in the selected-stage panel.

### Auditability

- Status changes go through the existing platform configuration path and config key.
- User-facing changelog records dashboard availability.

---

## 7. Testing Design

### Frontend Tests

File:

`frontend/tests/agentContributionDashboard.test.mjs`

Coverage:

- Seven-stage data model.
- Status defaults.
- Route/registry wiring.
- No agent registration language.
- Role labels present.
- Confluence links configured.
- No scores, contributor leaderboards, Chinese UI copy, or nested mountain terminology.

### Backend Tests

File:

`src/test/java/com/wwa/agenthub/web/AgentContributionDashboardControllerTest.java`

Coverage:

- Read statuses returns empty overrides by default.
- Admin status update persists and is readable by any authenticated user.
- Invalid status value (`done`, etc.) is rejected with 400.
- Unknown stage key is rejected with 400.
- Non-admin update is rejected with 403.

### Manual / Visual Verification

- Open `/wwa/agent-contribute-dashboard`.
- Verify the page loads inside WWA shell.
- Verify seven-stage map fits desktop viewport.
- Verify admin status control is visible for `DEVOPS_ADMIN`.
- Verify Confluence links render in selected-stage panel.

---

## 8. Risks / Tradeoffs

- Static JSON is simple but not admin-editable. This is acceptable for MVP because only status requires admin mutation.
- Placeholder Confluence links are easy to replace but not validated by the app.
- Status override persistence reuses platform configuration rather than introducing a typed table; this reduces schema work but relies on strong validation.

---

## 9. Open Questions

Open questions are tracked as pending tasks in `docs/06-tasks/agent-contribute-dashboard-tasks.md`. Do not maintain a separate list here.
