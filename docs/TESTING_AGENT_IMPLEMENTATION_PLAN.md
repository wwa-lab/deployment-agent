# Testing Agent — Implementation Plan

**Last Updated**: 2026-03-31
**Branch**: `Testing-Agent/Develop-leo`
**Stack**: Java 21 / Spring Boot 3.2.0 / Spring Data JPA / Oracle + H2 (test) / Vue 3 / Vite / Pinia / Vue Router / Axios
**Reference Documents**:
- `docs/06-tasks/testing-agent-tasks.md` — task source of truth
- `docs/05-design/testing-agent-design.md` — detailed design
- `docs/04-architecture/testing-agent-architecture.md` — architecture decisions
- `docs/03-spec/testing-agent-spec.md` — functional requirements

---

## 1. Delivery Objective

Add **Testing Agent** as the second workspace under WWA Agent Workspace Hub. Testing Agent mirrors the Deployment Agent workflow with:

- Separate API prefix (`/api/testing-agent/`)
- Separate frontend routes (`/wwa/testing-agent`)
- Data isolation via `Request.agent = "testing-agent"`
- Zero impact on existing Deployment Agent functionality

---

## 2. Approach: Duplicate First, Extract Later

This plan uses **Approach B** — duplicate views and stores for Testing Agent, then refactor shared components in a follow-up PR.

**Rationale:**
- Eliminates the HIGH risk of breaking Deployment Agent through shared component extraction
- Gets Testing Agent working faster with zero DA regression risk
- Defers refactoring to a separate PR with no functional changes

**Temporary duplication:** ~1000 lines across views, store, and API modules. Bounded and well-understood — cleanup is a follow-up task.

---

## 3. Current Baseline

The following are treated as the implemented baseline:

- All Deployment Agent functionality (upload, release flows, tasks, decisions, audit, config, access management)
- `Request.agent` column exists (`V6__add_request_agent_column.sql`)
- `ReleaseFlowService` supports `agent` as a filter parameter
- `ImportService.importFile()` accepts `agent` as a parameter
- Agent registry has a commented placeholder for Testing Agent
- `matchesContains` uses substring matching for agent filter (acceptable for MVP)
- Controllers use manual validation helpers (not `@PreAuthorize` annotations)
- Tests authenticate via `X-User-Id` / `X-User-Role` headers

---

## 4. Critical Findings from Code Analysis

| # | Finding | Location | Impact |
|---|---|---|---|
| 1 | `matchesContains` uses substring matching | `ReleaseFlowService.java:930` | LOW — no false matches with current agent values |
| 2 | No `@PreAuthorize` — manual validation helpers | All controllers | Must replicate helpers in new controllers |
| 3 | `ImportService.importFile()` already accepts `agent` | `ImportService.java:70` | No service changes needed |
| 4 | `UploadDialog.vue` hardcodes store/API imports | `UploadDialog.vue:3-4` | Eliminated by Approach B (duplicate, don't extract) |
| 5 | Tests use header-based auth | `ReleaseFlowControllerTest.java:60` | Follow same pattern |
| 6 | DA list does NOT enforce agent filter | `ReleaseFlowController.java:48` | Intentional — DA shows all data |

---

## 5. Implementation Phases

### Phase 1: Backend Foundation

**Goal:** All Testing Agent backend endpoints working and tested.

#### Step 1.1: Create Agent Identity Constants
- **Task:** TA-TASK-001
- **Create:** `src/main/java/com/wwa/deploymentagent/contracts/AgentId.java`
- **Content:**
  ```java
  public final class AgentId {
      public static final String DEPLOYMENT_AGENT = "deployment-agent";
      public static final String TESTING_AGENT = "testing-agent";
      private AgentId() {}
  }
  ```
- **Size:** XS (~10 lines)

#### Step 1.2: Create TestingAgentReleaseFlowController
- **Task:** TA-TASK-002
- **Create:** `src/main/java/com/wwa/deploymentagent/web/controller/TestingAgentReleaseFlowController.java`
- **Pattern:** Mirror `ReleaseFlowController.java` (349 lines)
  - `@RequestMapping("/api/testing-agent/release-flows")`
  - Inject `ReleaseFlowService`, `TemplateRundownCreationService` via constructor
  - `GET /` — delegate to `releaseFlowService.listStitchedSummaries()` with `agent = AgentId.TESTING_AGENT` (MUST override any client-supplied agent parameter)
  - `GET /{id}` — delegate to `releaseFlowService.getById()` and `findRequestsForFlow()`
  - Include all rundown endpoints: `createFromTemplate`, `updateRequestRundown`, `archiveRequestRundown`, `restoreRequestRundown`, `purgeRequestRundown`, `startRequestDeployment`, `markRequestFailed`
  - Copy validation helpers: `validateRundownEditor()`, `validateArchivedViewer()`, `validateViewMode()`, `validateAttemptView()`
- **Security:** Agent parameter override on list endpoint is a security boundary — prevents cross-agent data leakage
- **Size:** S-M (~200 lines)

#### Step 1.3: Create TestingAgentUploadController
- **Task:** TA-TASK-003
- **Create:** `src/main/java/com/wwa/deploymentagent/web/controller/TestingAgentUploadController.java`
- **Pattern:** Mirror `UploadController.java` (92 lines)
  - `@RequestMapping("/api/testing-agent/upload")`
  - Inject `ImportService`, `UploadTemplateService`
  - `POST /` — delegate to `importService.importFile(bytes, stage, userContext, releaseId, snowGroup, application, AgentId.TESTING_AGENT)` — override agent parameter
  - `GET /template` — delegate to `uploadTemplateService.generateTemplate()`
  - Copy `validateUploadRole()` helper
- **Size:** S (~60 lines)

#### Step 1.4: Create TestingAgentTaskController
- **Task:** TA-TASK-004
- **Create:** `src/main/java/com/wwa/deploymentagent/web/controller/TestingAgentTaskController.java`
- **Pattern:** Mirror `TaskController.java` (111 lines) + `DecisionController.java` (45 lines)
  - `@RequestMapping("/api/testing-agent/tasks")`
  - Inject `TaskService`, `TaskExecutionHistoryService`, `RecordResultService`, `AutoExecutionService`, `DecisionEngine`, `ReleaseFlowProgressionService`
  - All task endpoints: `updateInput`, `getExecutions`, `startManual`, `recordResult`, `submitAuto`, `decision`
  - No agent filtering needed — tasks accessed by ID, inherit agent from parent request
  - Pass `AgentId.TESTING_AGENT` to audit context
- **Size:** S (~100 lines)

#### Step 1.5: Write Backend Tests
- **Task:** TA-TASK-005
- **Create:**
  - `src/test/java/com/wwa/deploymentagent/web/TestingAgentReleaseFlowControllerTest.java`
  - `src/test/java/com/wwa/deploymentagent/web/TestingAgentUploadControllerTest.java`
  - `src/test/java/com/wwa/deploymentagent/web/TestingAgentTaskControllerTest.java`
  - `src/test/java/com/wwa/deploymentagent/web/TestingAgentDataIsolationTest.java`
- **Pattern:** Follow `ReleaseFlowControllerTest.java`
  - `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@Transactional`
  - Authenticate via `X-User-Id` and `X-User-Role` headers
  - Use `TestDataHelper` for seeding (may need `seedRequest(..., String agent)` overload)
  - Base URLs: `/api/testing-agent/release-flows`, `/api/testing-agent/upload`, `/api/testing-agent/tasks`
- **Key test scenarios:**
  1. Upload via `/api/testing-agent/upload` → verify `Request.agent = "testing-agent"` in DB
  2. List via `/api/testing-agent/release-flows` → only testing-agent flows returned
  3. List with `agent=deployment-agent` param → verify override to testing-agent (security)
  4. Null-agent legacy data → NOT visible in Testing Agent list
  5. Upload same project via both agents → each agent sees only its own requests
  6. Decision via Testing Agent → audit entry has `agentName = "testing-agent"`
- **Size:** M (~250 lines across 4 files)

#### Phase 1 Verification Gate
```bash
mvn test
# All existing 167 tests + new tests must pass
```

---

### Phase 2: Frontend Infrastructure

**Goal:** Testing Agent API client and store ready. No visible UI changes yet.

#### Step 2.1: Create Frontend Agent Constants
- **Task:** TA-TASK-001 (frontend part)
- **Create:** `frontend/src/config/agentId.ts`
- **Content:**
  ```typescript
  export const AGENT_ID = {
    DEPLOYMENT: 'deployment-agent',
    TESTING: 'testing-agent',
  } as const

  export type AgentIdValue = typeof AGENT_ID[keyof typeof AGENT_ID]
  ```
- **Size:** XS (~6 lines)

#### Step 2.2: Create Testing Agent API Client
- **Task:** TA-TASK-006
- **Create:** `frontend/src/api/testingAgentClient.ts`
- **Pattern:** Duplicate `client.ts` (31 lines) with `baseURL: '/api/testing-agent'`
  - Same `withCredentials: true`
  - Same 401 redirect interceptor
- **Size:** XS (~15 lines)

#### Step 2.3: Create Testing Agent API Modules
- **Task:** TA-TASK-006 (continued)
- **Create:**
  - `frontend/src/api/testingAgentReleaseFlows.ts` — duplicate `releaseFlows.ts` (99 lines), import from `testingAgentClient`
  - `frontend/src/api/testingAgentUpload.ts` — duplicate `upload.ts` (37 lines), import from `testingAgentClient`
  - `frontend/src/api/testingAgentTasks.ts` — duplicate `tasks.ts` (67 lines), import from `testingAgentClient`
- **Pattern:** Copy each API module, replace `import client from './client'` with `import client from './testingAgentClient'`
- **Size:** S (~200 lines total, all duplicated)

#### Step 2.4: Create Testing Agent Store
- **Task:** TA-TASK-007
- **Create:** `frontend/src/stores/testingAgentReleaseFlow.ts`
- **Pattern:** Duplicate `releaseFlow.ts` (128 lines)
  - Change store ID: `'testingAgentReleaseFlow'`
  - Change API imports: from `../api/testingAgentReleaseFlows` and `../api/testingAgentUpload`
- **Size:** S (~130 lines, duplicated)

#### Phase 2 Verification Gate
```bash
cd frontend && npm run build
# Must succeed with zero TypeScript errors
```

---

### Phase 3: Testing Agent Views & Routing

**Goal:** Testing Agent fully functional in the UI.

#### Step 3.1: Create TestingAgentSummaryView
- **Task:** TA-TASK-012
- **Create:** `frontend/src/views/TestingAgentSummaryView.vue`
- **Pattern:** Duplicate `ReleaseFlowSummaryView.vue` (496 lines)
  - Replace `useReleaseFlowStore` → `useTestingAgentReleaseFlowStore`
  - Replace API imports → Testing Agent API modules
  - Replace page title → `"Testing Agent"`
  - Replace page description → testing-specific text
  - Replace detail route path → `/wwa/testing-agent/release-flows/`
- **Size:** M (~496 lines, duplicated with modifications)

#### Step 3.2: Create TestingAgentDetailView
- **Task:** TA-TASK-013
- **Create:** `frontend/src/views/TestingAgentDetailView.vue`
- **Pattern:** Duplicate `ReleaseFlowDetailView.vue` (600+ lines)
  - Replace `useReleaseFlowStore` → `useTestingAgentReleaseFlowStore`
  - Replace API imports → Testing Agent API modules
  - Replace page title / breadcrumb → `"Testing Agent"`
  - Replace summary route path → `/wwa/testing-agent`
- **Size:** L (~600 lines, duplicated with modifications)

#### Step 3.3: Update Agent Registry
- **Task:** TA-TASK-008
- **Modify:** `frontend/src/config/agentRegistry.ts`
- **Action:** Replace commented placeholder (lines 42-50) with enabled entry:
  ```typescript
  {
    key: 'testing-agent',
    name: 'Testing Agent',
    description: 'Controlled, human-in-the-loop testing workflow across SIT, UAT, and PROD stages.',
    route: '/wwa/testing-agent',
    icon: '🧪',
    enabled: true,
    category: 'testing',
  }
  ```
- **Size:** XS (~8 lines changed)

#### Step 3.4: Add Testing Agent Routes
- **Task:** TA-TASK-009
- **Modify:** `frontend/src/router/index.ts`
- **Action:** Add two children under the `/wwa` parent route:
  ```typescript
  {
    path: 'testing-agent',
    name: 'wwa-testing-agent',
    component: () => import('../views/TestingAgentSummaryView.vue'),
    meta: { section: 'testing-agent', sectionTitle: 'Testing Agent' },
  },
  {
    path: 'testing-agent/release-flows/:id',
    name: 'wwa-testing-agent-detail',
    component: () => import('../views/TestingAgentDetailView.vue'),
    meta: { section: 'testing-agent', sectionTitle: 'Testing Agent' },
  },
  ```
- **Size:** XS (~14 lines)

#### Phase 3 Verification Gate
```bash
cd frontend && npm run build
cd frontend && npx vue-tsc --noEmit
# Both must succeed
```

---

### Phase 4: End-to-End Verification

#### Step 4.1: Backend Test Suite
- **Task:** TA-TASK-014 (partial)
```bash
mvn test
# All tests pass
```

#### Step 4.2: Frontend Build Verification
- **Task:** TA-TASK-014 (partial)
```bash
cd frontend && npm run build
cd frontend && npx vue-tsc --noEmit
# Both succeed
```

#### Step 4.3: Manual E2E Verification
- **Task:** TA-TASK-015
- **Start services:**
  ```bash
  mvn spring-boot:run -Dspring-boot.run.profiles=local
  cd frontend && npm run dev
  ```
- **Checklist:**
  1. [ ] Testing Agent card visible on WWA Home page
  2. [ ] Testing Agent in sidebar flyout
  3. [ ] `/wwa/testing-agent` loads Testing Agent summary with correct title
  4. [ ] Upload via Testing Agent creates request with `agent = "testing-agent"`
  5. [ ] Uploaded flow appears in Testing Agent summary
  6. [ ] Deployment Agent summary still shows all data (no regression)
  7. [ ] Navigate to Testing Agent detail — tasks visible
  8. [ ] Task actions work (edit, run, record result, decision)
  9. [ ] Audit log shows entries with `agentName = "testing-agent"`
  10. [ ] All existing Deployment Agent functionality unchanged

---

## 6. File Summary

### New Files (Backend — 4 files)

| File | Size | Purpose |
|---|---|---|
| `contracts/AgentId.java` | ~10 lines | Constants |
| `web/controller/TestingAgentReleaseFlowController.java` | ~200 lines | Release flow API |
| `web/controller/TestingAgentUploadController.java` | ~60 lines | Upload API |
| `web/controller/TestingAgentTaskController.java` | ~100 lines | Task API |

### New Files (Backend Tests — 4 files)

| File | Size | Purpose |
|---|---|---|
| `web/TestingAgentReleaseFlowControllerTest.java` | ~80 lines | List/detail tests |
| `web/TestingAgentUploadControllerTest.java` | ~60 lines | Upload agent-tagging tests |
| `web/TestingAgentTaskControllerTest.java` | ~50 lines | Task delegation tests |
| `web/TestingAgentDataIsolationTest.java` | ~60 lines | Cross-agent isolation tests |

### New Files (Frontend — 7 files)

| File | Size | Purpose |
|---|---|---|
| `config/agentId.ts` | ~6 lines | Constants |
| `api/testingAgentClient.ts` | ~15 lines | Axios instance |
| `api/testingAgentReleaseFlows.ts` | ~99 lines | API module (duplicated) |
| `api/testingAgentUpload.ts` | ~37 lines | API module (duplicated) |
| `api/testingAgentTasks.ts` | ~67 lines | API module (duplicated) |
| `stores/testingAgentReleaseFlow.ts` | ~130 lines | Store (duplicated) |
| `views/TestingAgentSummaryView.vue` | ~496 lines | Summary view (duplicated) |
| `views/TestingAgentDetailView.vue` | ~600 lines | Detail view (duplicated) |

### Modified Files (Frontend — 2 files)

| File | Change |
|---|---|
| `config/agentRegistry.ts` | Enable Testing Agent entry (~8 lines) |
| `router/index.ts` | Add 2 routes (~14 lines) |

---

## 7. Risks (Post-Mitigation)

| Risk | Severity | Status |
|---|---|---|
| ~~Shared component extraction breaks DA~~ | ~~HIGH~~ | **ELIMINATED** — using Approach B |
| ~~Dialog hardcoded imports~~ | ~~MEDIUM~~ | **ELIMINATED** — duplicated views |
| ~~Store factory misses behavior~~ | ~~MEDIUM~~ | **ELIMINATED** — duplicated store |
| `matchesContains` substring matching | LOW | Accepted for MVP |
| `TestDataHelper` may need agent overload | LOW | Add ~5 lines if needed |

---

## 8. Follow-Up Tasks (Separate PR)

After Testing Agent is working:

1. **Extract shared components** — `AgentSummaryView.vue`, `AgentDetailView.vue` from duplicated views
2. **Extract API factory** — `agentApiFactory.ts` to replace duplicated API modules
3. **Extract store factory** — `agentReleaseFlowFactory.ts` to replace duplicated stores
4. **Refactor dialog components** — accept API functions via props instead of hardcoded imports

These are pure refactoring tasks with no functional changes — lower risk when done independently.

---

## 9. Success Criteria

- [ ] `AgentId.java` and `agentId.ts` constants exist
- [ ] 3 Testing Agent controllers delegate correctly
- [ ] Upload via Testing Agent tags `Request.agent = "testing-agent"`
- [ ] Testing Agent list returns only testing-agent data
- [ ] Legacy null-agent data excluded from Testing Agent
- [ ] All backend tests pass: `mvn test`
- [ ] Frontend builds: `npm run build` + `vue-tsc --noEmit`
- [ ] Testing Agent card on WWA Home page
- [ ] Testing Agent in sidebar flyout
- [ ] Full workflow works through Testing Agent
- [ ] Deployment Agent unchanged (zero regression)
- [ ] Audit entries tagged with correct agent name

---

## 10. Parallel Execution Plan

```
Phase 1 (Backend)                    Phase 2 (Frontend Infra)
┌─────────────────────┐              ┌─────────────────────┐
│ 1.1 AgentId.java    │              │ 2.1 agentId.ts      │
│ 1.2 RF Controller   │    parallel  │ 2.2 API client      │
│ 1.3 Upload Ctrl     │◄───────────►│ 2.3 API modules     │
│ 1.4 Task Ctrl       │              │ 2.4 Store           │
│ 1.5 Tests           │              │                     │
└────────┬────────────┘              └────────┬────────────┘
         │                                    │
         └────────────┬───────────────────────┘
                      ▼
              Phase 3 (Views & Routing)
              ┌─────────────────────┐
              │ 3.1 Summary View    │
              │ 3.2 Detail View     │
              │ 3.3 Agent Registry  │
              │ 3.4 Routes          │
              └────────┬────────────┘
                       ▼
              Phase 4 (Verification)
              ┌─────────────────────┐
              │ 4.1 mvn test        │
              │ 4.2 npm run build   │
              │ 4.3 Manual E2E      │
              └─────────────────────┘
```
