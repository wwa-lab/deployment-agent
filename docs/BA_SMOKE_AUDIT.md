# BA-T27 — Smoke Scenario Audit

**Date:** 2026-04-11
**Branch:** `build-agent-leo`
**Status:** 9 of 13 auto-covered; 4 require manual E2E verification

`BuildDataIsolationTest` (see `src/test/java/com/wwa/deploymentagent/agents/build/web/BuildDataIsolationTest.java`) covers 9 of the 13 §9 smoke scenarios as integration tests. The remaining 4 require a running backend + frontend for manual UI click-through.

## Status matrix

| ID | Design §9 # | Scenario | Status | Evidence |
|---|---|---|---|---|
| SM-01 | 1 | Upload via Build Agent → Build Agent summary | ✅ auto | `BuildDataIsolationTest.sm01_buildUpload_visibleInBuildSummary` |
| SM-02 | 2 | Same upload → Deployment Agent summary (not visible) | ✅ auto | `BuildDataIsolationTest.sm02_buildUpload_notVisibleInDeployment` |
| SM-03 | 3 | Same upload → Testing Agent summary (not visible) | ✅ auto | `BuildDataIsolationTest.sm03_buildUpload_notVisibleInTesting` |
| SM-04 | 4 | Upload `DEV-1234` twice → single row, upsert | ⚠ manual | Covered indirectly by `ImportServiceTest.importFile_reUpload_upsertsTasks`; UI re-verification pending |
| SM-05 | 5 | Build Agent `DEV-1234` + Deployment Agent `SIT-1234` → two separate rows, no stitching | ✅ auto | `BuildDataIsolationTest.sm05_buildAndDeployment_neverStitch` |
| SM-06 | 6 | `GET /api/build-agent/tasks/{deployment-task-id}` → 404 | ✅ auto | `BuildDataIsolationTest.sm06_crossAgentTaskGet_returns404` |
| SM-07 | 7 | `POST /api/build-agent/tasks/{testing-task-id}/decision` → 404, task unmodified | ✅ auto | `BuildDataIsolationTest.sm07_crossAgentDecisionPost_returns404` |
| SM-08 | 8 | Approve all tasks in a Build Agent DEV flow → `Completed`, no auto-advance | ⚠ manual | Covered indirectly by `BuildStagePipelineTest.next_isEmptyForDev` + `ReleaseFlowProgressionServiceTest`; UI re-verification pending |
| SM-09 | 9 | `GET /api/build-agent/release-flows/{id}?linked=abc,def` → ignores `linked` | ✅ auto | `BuildDataIsolationTest.sm09_buildGetById_ignoresLinkedParam` |
| SM-10 | 10 | Audit trail after Build Agent action → `agentName = "build-agent"` | ⚠ manual | `AuditLoggerServiceTest` covers the dynamic agentName for upload events; end-to-end verification via Build Agent upload + audit-log view pending |
| SM-11 | 11 | `POST /api/platform/auth/login` then call any agent endpoint → same session | ⚠ manual | `AuthControllerTest` covers session login; cross-prefix session cookie not auto-tested |
| SM-12 | 12 | `POST /api/deployment-agent/auth/login` (legacy route) → 401/404 | ✅ auto | `BuildDataIsolationTest.sm12_legacyAuthLoginRoute_unavailable` (expects 401 under current SecurityConfig) |
| SM-13 | 13 | Testing Agent cross-agent task probe → 404 (closes v2 R-08) | ✅ auto | `BuildDataIsolationTest.sm13_testingAgentCrossAgentProbe_returns404` |

## Manual verification steps (for SM-04, SM-08, SM-10, SM-11)

Start backend and frontend locally:

```bash
# Terminal 1 — backend
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Terminal 2 — frontend
cd frontend && npm install && npm run dev
```

Log in at `http://localhost:5173/login` with e.g. `emp-003` / any password (DEVOPS_ADMIN).

### SM-04 — Build Agent idempotent re-upload

1. Navigate to **Build Agent** (`/wwa/build-agent`).
2. Click **+ Upload**, pick an XLSX with `releaseId = DEV-1234`.
3. Upload once, record the release flow row id.
4. Upload the same XLSX again.
5. **Expected:** summary still shows exactly one row with `releaseId = DEV-1234`; task list on the detail view reflects the second upload's content (upsert).

### SM-08 — Approve all Build Agent tasks → Completed without advance

1. Upload a minimal Build Agent rundown with 1 task.
2. Start the task (MANUAL) → `Record Result` → `Approve` via the decision dialog.
3. **Expected:** flow status moves to `Completed`; `currentStage` remains `DEV`; no request is created for any other stage.

### SM-10 — Audit trail agentName

1. Perform a Build Agent upload (as in SM-04).
2. Open **Audit Log** (`/wwa/audit-log`) and filter on the just-uploaded release id or time window.
3. **Expected:** the upload entry shows `agentName = "build-agent"` (column or JSON payload).

### SM-11 — Cross-prefix session sharing

1. Log in (this hits `POST /api/platform/auth/login`).
2. Use browser devtools **Network** tab; confirm a `JSESSIONID` cookie is set with `Path=/`.
3. Navigate between Deployment Agent, Testing Agent, and Build Agent summaries. Each hits a different `/api/<agent>/*` prefix.
4. **Expected:** no re-login prompted; every request carries the same `JSESSIONID`; all respond 200.

## Automation gap follow-up

SM-04 and SM-08 are strong candidates for `BuildAgentControllerTest` or `BuildWorkflowTest` integration tests. SM-10 can be covered by an end-to-end `BuildAuditTrailTest` that uploads and queries the audit log. SM-11 is harder to automate without a real browser and is best left as manual or moved to a Playwright smoke pack.
