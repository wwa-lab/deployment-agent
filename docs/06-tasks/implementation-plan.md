# MVP Implementation Plan: AUTO Execution, Team Book Auth, Role Alignment

**Last updated**: 2026-03-19
**Branch**: `develop-leo`
**Commit**: `b3eea3d`
**Test status**: 167 tests passing (`mvn test`), frontend compiles clean (`npx vue-tsc --noEmit`)

---

## Context

The deployment agent completed its MANUAL workflow (Phase 1-5, 147 tests passing). Four RESOLVE blockers were previously blocking progress. The user clarified that:

1. **AUTO execution** is just outbound API calls to Jenkins/Ansible + storing the external job URL. No callbacks, no log ingestion, no Vault.
2. **Authentication** should be a real login flow (employee ID + password) validated against the company team book. Not placeholder headers.
3. **Role alignment** is needed: frontend used AUDIT_MGMT but backend uses separate AUDIT and MANAGEMENT.
4. **Audit** already works but must be tied to authenticated identity post-login.

---

## Blocker Resolution

| Former Blocker | Status | Resolution |
|---|---|---|
| RESOLVE-Q2 (callback auth) | **Deferred** | No callbacks in MVP; fire-and-forget submission |
| RESOLVE-Q3 (secret store) | **Eliminated** | Jenkins/Ansible credentials stored in DA_CONFIGURATION_ITEM via Config page |
| RESOLVE-Q4 (log storage) | **Eliminated** | Full logs stay in Jenkins/Ansible; DA stores external job URL only |
| RESOLVE-Q5 (WWA auth) | **Replaced** | Team Book session login replaces WWA header auth |

---

## Implementation Phases

### Phase A: AUTO Execution Backend — COMPLETE

| Step | Description | Status |
|---|---|---|
| A1 | Extend ConfigKey enum (`jenkins_user`, `jenkins_api_token`, `ansible_user`, `ansible_api_token`) | Done |
| A2 | Add non-blank validators for new config keys in ConfigurationService | Done |
| A3 | Add `auto_submit` to AuditActionType | Done |
| A4 | Add 6 external execution columns to TaskExecutionHistory entity | Done |
| A5 | Update TaskExecutionHistoryDto with new fields | Done |
| A6 | Create AutoExecutionAdapter interface + JenkinsExecutionAdapter + AnsibleExecutionAdapter | Done |
| A7 | Create AutoExecutionService (guards, submission, failure handling, audit) | Done |
| A8 | Add `POST /tasks/{id}/submit-auto` endpoint (TL or DEVOPS_ADMIN) | Done |
| A9 | Register RestTemplate bean with 10s connect / 30s read timeouts | Done |
| A10 | AutoExecutionServiceTest (6 tests: success, metadata, failure, MANUAL rejection, wrong state, not found) | Done |

**Key files**:
- `domain/execution/AutoExecutionService.java` — core service
- `domain/execution/JenkinsExecutionAdapter.java` — Jenkins REST API integration
- `domain/execution/AnsibleExecutionAdapter.java` — AWX/Tower REST API integration
- `config/RestClientConfig.java` — RestTemplate with timeouts
- `resources/db/migration/V2__add_external_execution_columns.sql` — Oracle DDL

**Integration notes (addressed 2026-03-19)**:
- Ansible `extra_vars` uses Jackson ObjectMapper for safe JSON serialization (not string concatenation)
- Jenkins parameter mapping supports both Map (named params) and String (freeform) via `MultiValueMap`
- Ansible external job URL points to UI path (`/#/jobs/playbook/{id}`), not API path
- Jenkins external job URL is the queue item URL (redirects to build once started)

---

### Phase B: Team Book Authentication — COMPLETE

| Step | Description | Status |
|---|---|---|
| B1 | TeamBookAuthenticationProvider interface + TeamBookEmployee record + StubTeamBookAuthenticationProvider | Done |
| B2 | AuthService (delegates to provider, returns UserContext) | Done |
| B3 | LoginRequestDto + AuthResponseDto | Done |
| B4 | AuthController (POST /auth/login, GET /auth/me, POST /auth/logout) | Done |
| B5 | SessionAuthFilter (reads UserContext from HttpSession) | Done |
| B6 | Update SecurityConfig (IF_REQUIRED sessions, login permitAll, filter order) | Done |
| B7 | Update HeaderAuthFilter (skip if authenticated, gate with `app.auth.header-fallback-enabled`) | Done |
| B8 | Properties: `header-fallback-enabled=false` (prod), `=true` (test) | Done |
| B9 | Frontend LoginView.vue | Done |
| B10 | Update frontend router (auth guard, /login route) | Done |
| B11 | Update axios client (withCredentials, remove header injection, 401 redirect) | Done |
| B12 | Update user store (login/logout/initSession, isAuthenticated, displayName) | Done |
| B13 | Update WorkspaceLayout (remove role-switcher, add logout, show identity) | Done |
| B14 | AuthServiceTest (8 tests) + AuthControllerTest (6 tests) | Done |

**Key files**:
- `domain/auth/TeamBookAuthenticationProvider.java` — interface (swap for prod)
- `domain/auth/StubTeamBookAuthenticationProvider.java` — dev/test stub (5 hardcoded users)
- `web/controller/AuthController.java` — login/me/logout endpoints
- `web/security/SessionAuthFilter.java` — session-based auth filter
- `frontend/src/views/LoginView.vue` — login page

**Stub users (dev/test)**:
| Employee ID | Role | Display Name |
|---|---|---|
| emp-001 | DEVELOPER | Alice Park (Developer) |
| emp-002 | TL | Bob Kim (Tech Lead) |
| emp-003 | DEVOPS_ADMIN | Carol Lee (DevOps Admin) |
| emp-004 | AUDIT | David Cho (Auditor) |
| emp-005 | MANAGEMENT | Eve Yoon (Management) |

---

### Phase C: Role Alignment — COMPLETE

| Step | Description | Status |
|---|---|---|
| C1 | Fix frontend UserRole type to `DEVELOPER \| TL \| DEVOPS_ADMIN \| AUDIT \| MANAGEMENT` | Done |
| C2 | Replace `isAuditMgmt` with `isAudit`, `isManagement`, `canViewAudit` in user store | Done |
| C3 | Update router guard to handle array of allowed roles | Done |
| C4 | Update WorkspaceLayout (`canViewAudit`) and AuditLogView access check | Done |

---

### Phase D: Frontend AUTO Execution Support — COMPLETE

| Step | Description | Status |
|---|---|---|
| D1 | Add external execution fields to TaskResult TypeScript interface | Done |
| D2 | Add `submitAutoExecution(taskId)` API call | Done |
| D3 | Add "Submit Auto" button with `canSubmitAuto()` guard (AUTO + Ready_For_Execution + TL/DEVOPS_ADMIN) | Done |
| D4 | Show external job URL in View Result modal (clickable link, system type badge, submission status) | Done |

---

## Verification Status

| Check | Result | Date |
|---|---|---|
| `mvn test` | 167 tests, 0 failures | 2026-03-19 |
| `npx vue-tsc --noEmit` | Clean, no errors | 2026-03-19 |
| Existing 147 tests unchanged | All pass (header fallback enabled in test profile) | 2026-03-19 |
| New AutoExecutionServiceTest | 6 tests pass | 2026-03-19 |
| New AuthServiceTest | 8 tests pass | 2026-03-19 |
| New AuthControllerTest | 6 tests pass | 2026-03-19 |

---

## UAT Readiness Summary

| Area | Status | Blocker |
|---|---|---|
| MANUAL workflow (upload, execute, review, decide, progress) | **Ready now** | None |
| AUTO execution (submit, state transitions, failure handling, audit) | **Ready now** | None — tested with mocked adapters |
| AUTO against real Jenkins | **Ready once credentials provided** | Enter jenkins_url/user/api_token on Config page |
| AUTO against real Ansible | **Ready once credentials provided** | Enter ansible_url/user/api_token on Config page |
| Login/session auth flow | **Ready now** (stub users) | None |
| Login against real Team Book | **Ready once API contract provided** | Need RealTeamBookAuthenticationProvider |
| Role alignment (AUDIT/MANAGEMENT split) | **Ready now** | None |
| Audit with authenticated identity | **Ready now** | Audit writes session user's employeeId |
| Oracle DDL | **Ready now** | V2 migration script provided |

---

## Pending External Dependencies

### Team Book API Contract

Required to build `RealTeamBookAuthenticationProvider` (`@Profile("prod")`):

| # | Item | Purpose |
|---|---|---|
| 1 | API endpoint URL | Base URL for authentication calls |
| 2 | Request format | HTTP method, content type, body structure |
| 3 | Response format (success) | Fields for employee ID, display name, role/attribute |
| 4 | Response format (failure) | Status codes for invalid credentials vs server error |
| 5 | Role mapping rules | Whether Team Book returns role directly or needs mapping |
| 6 | Service credentials | API key or service account for the calling application |
| 7 | Network accessibility | VPC, proxy, firewall requirements |

### Jenkins / Ansible Credentials

Entered at runtime via the Config admin page (DevOps Admin role). No code changes needed.

| Config Key | Purpose |
|---|---|
| `jenkins_url` | Jenkins base URL (e.g. `https://jenkins.internal:8080`) |
| `jenkins_user` | Jenkins service account username |
| `jenkins_api_token` | Jenkins API token |
| `ansible_url` | AWX/Tower base URL (e.g. `https://awx.internal`) |
| `ansible_user` | AWX service account username (unused in current Bearer auth flow) |
| `ansible_api_token` | AWX OAuth2 token |

---

## Items Deferred from MVP

| Item | Reason | Revisit When |
|---|---|---|
| Execution callbacks (T9.x) | MVP uses fire-and-forget; no result ingestion from external systems | When real-time status sync is needed |
| Full log ingestion | Logs stay in Jenkins/Ansible; DA stores URL only | When compliance requires centralized log storage |
| Real Team Book provider | Pending API contract | When Team Book team provides contract |
| Session timeout/refresh | Default Spring session timeout (30 min) used | When UAT feedback indicates need |
