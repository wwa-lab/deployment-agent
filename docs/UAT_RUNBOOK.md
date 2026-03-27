# Release Agent UAT Runbook

> **Audience:** Delivery lead, DevOps engineer, backend engineer, frontend engineer, UAT coordinator  
> **Purpose:** Bring Release Agent into an internal/UAT environment in a controlled way and verify the product in the order `Database -> Backend -> Frontend -> Integrated UAT`

> **Naming note:** the workspace display name is now `Release Agent`, but the current implementation still uses technical identifiers such as `/api/deployment-agent` and `/wwa/deployment-agent`.

---

## 1. Recommended UAT Mode

For the current codebase, the most practical internal UAT setup is:

- **Database:** Oracle
- **Backend profile:** default profile (do **not** use `local`)
- **Authentication:** current stub Team Book provider
- **Frontend:** built SPA served behind a reverse proxy with `/api` forwarded to the backend

### Why this mode is recommended

- [`application.properties`](/Users/leo/wwa-lab/deployment-agent/src/main/resources/application.properties) is already wired for Oracle.
- [`StubTeamBookAuthenticationProvider.java`](/Users/leo/wwa-lab/deployment-agent/src/main/java/com/wwa/deploymentagent/domain/auth/StubTeamBookAuthenticationProvider.java) is active for `default`, `dev`, `test`, and `local`.
- [`LocalSecurityConfig.java`](/Users/leo/wwa-lab/deployment-agent/src/main/java/com/wwa/deploymentagent/config/LocalSecurityConfig.java) permits all requests, so `local` is good for developer debugging but not for permission-sensitive UAT.
- A real Team Book provider is **not** implemented yet, so internal UAT should currently assume stub-based login unless that integration is built separately.

---

## 2. Known Preconditions And Blockers

### Required before UAT can start

1. **Oracle schema must exist before backend startup.**
   - Because [`application.properties`](/Users/leo/wwa-lab/deployment-agent/src/main/resources/application.properties) uses `spring.jpa.hibernate.ddl-auto=validate`, the app will validate schema on startup and fail if tables are missing.
   - The repo now includes a full current-state Oracle schema script for greenfield UAT setup at [ORACLE_CURRENT_SCHEMA.sql](/Users/leo/wwa-lab/deployment-agent/docs/sql/ORACLE_CURRENT_SCHEMA.sql).

2. **A UAT static hosting / reverse proxy plan must exist for the frontend.**
   - The SPA uses relative API calls via [`frontend/src/api/client.ts`](/Users/leo/wwa-lab/deployment-agent/frontend/src/api/client.ts) with `baseURL: '/api/deployment-agent'`.
   - The simplest UAT deployment is same-origin hosting with `/api` proxied to the backend.

3. **Stub login must be accepted for this UAT cycle, or a real Team Book provider must be built first.**

### Current non-blocking limitations

- Template Management is still lighter-weight than Release / Audit / Access in backend enforcement.
- Configuration Management is not yet a fully implemented per-scope override model.
- Execution Target Catalog is not implemented yet.

---

## 3. Environment Inputs

Prepare these values before deployment:

### Oracle / backend

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- optional: `APP_AUTH_BOOTSTRAP_ADMIN_IDS`

### Frontend / routing

- public UAT frontend URL
- backend base URL or reverse proxy target
- session cookie routing policy for same-origin access

### UAT users

If using the current stub provider, these built-in users are available and accept **any non-empty password**:

- `emp-001` — Developer
- `emp-002` — TL
- `emp-003` — DevOps Admin
- `emp-004` — Audit
- `emp-005` — Management

Reference: [README.md](/Users/leo/wwa-lab/deployment-agent/README.md), [StubTeamBookAuthenticationProvider.java](/Users/leo/wwa-lab/deployment-agent/src/main/java/com/wwa/deploymentagent/domain/auth/StubTeamBookAuthenticationProvider.java)

---

## 4. Database Bring-Up

### 4.1 Create Oracle schema/user

Create the Oracle schema/user that will own Release Agent tables.

Minimum expectation:

- dedicated schema/user
- DDL permission to create or alter required tables and indexes
- application runtime permission to read/write the schema

### 4.2 Apply the baseline schema

For a fresh UAT Oracle schema, use:

- [ORACLE_CURRENT_SCHEMA.sql](/Users/leo/wwa-lab/deployment-agent/docs/sql/ORACLE_CURRENT_SCHEMA.sql)

This script already contains the current end-state columns from the historical incremental SQL files under [`src/main/resources/db/migration`](/Users/leo/wwa-lab/deployment-agent/src/main/resources/db/migration).

### 4.2.1 Run the schema from IntelliJ IDEA

Recommended steps:

1. Create an Oracle data source in IntelliJ IDEA / Database tool window
2. Open [ORACLE_CURRENT_SCHEMA.sql](/Users/leo/wwa-lab/deployment-agent/docs/sql/ORACLE_CURRENT_SCHEMA.sql)
3. Select the target Oracle schema
4. Execute the whole script once on the empty schema
5. Refresh the schema tree and confirm the tables and indexes were created

### 4.2.2 Important note

- If you use [ORACLE_CURRENT_SCHEMA.sql](/Users/leo/wwa-lab/deployment-agent/docs/sql/ORACLE_CURRENT_SCHEMA.sql) on a **fresh** database, do **not** run `V2-V9` again on top of it.
- The `V2-V9` files remain useful as historical incremental scripts or for upgrading an older baseline.

### 4.3 Validate database readiness

Before starting the backend, confirm:

- all required tables exist
- `DA_ACCESS_GRANT` exists with `assigned_roles` and `scope_grants`
- `DA_REQUEST` includes `snow_group`, `application`, `agent`, `owner`
- `DA_AUDIT_LOG_ENTRY` includes `application`, `snow_group`, `agent`

Reference schema: [data-model.md](/Users/leo/wwa-lab/deployment-agent/docs/04-architecture/data-model.md)

---

## 5. Backend Bring-Up

### 5.1 Backend prerequisites

- Java 21
- Maven
- Oracle connectivity from the UAT host

### 5.2 Backend configuration

The backend reads Oracle config from [`application.properties`](/Users/leo/wwa-lab/deployment-agent/src/main/resources/application.properties):

```bash
export DB_URL='jdbc:oracle:thin:@//<host>:<port>/<service>'
export DB_USERNAME='da_user'
export DB_PASSWORD='******'
```

Optional bootstrap admin IDs:

```bash
export APP_AUTH_BOOTSTRAP_ADMIN_IDS='emp-003'
```

Why this matters:

- [`AccessGrantBootstrapRunner.java`](/Users/leo/wwa-lab/deployment-agent/src/main/java/com/wwa/deploymentagent/domain/auth/AccessGrantBootstrapRunner.java) auto-seeds grants for known stub users.
- It can also explicitly seed `DEVOPS_ADMIN` grants from `app.auth.bootstrap-admin-ids`.

### 5.3 Start the backend

Recommended UAT startup:

```bash
cd /Users/leo/wwa-lab/deployment-agent
mvn spring-boot:run
```

Do **not** pass the `local` profile for UAT.

### 5.4 Backend smoke validation

After startup:

1. Confirm the app binds to `:8080`
2. Confirm startup does **not** fail on schema validation
3. Call:

```bash
curl -i http://<uat-backend-host>:8080/api/deployment-agent/auth/me
```

Expected result before login:

- `401 Unauthorized`

### 5.5 Login smoke test

Use stub credentials:

```bash
curl -i -c cookies.txt -H 'Content-Type: application/json' \
  -d '{"employeeId":"emp-003","password":"anything"}' \
  http://<uat-backend-host>:8080/api/deployment-agent/auth/login
```

Expected result:

- `200 OK`
- session cookie returned
- payload includes:
  - `role`
  - `roles`
  - `permissions`
  - `scopes`

Reference: [AuthController.java](/Users/leo/wwa-lab/deployment-agent/src/main/java/com/wwa/deploymentagent/web/controller/AuthController.java), [AuthResponseDto.java](/Users/leo/wwa-lab/deployment-agent/src/main/java/com/wwa/deploymentagent/contracts/dto/AuthResponseDto.java)

### 5.6 Backend functional smoke

With the session cookie, validate at least:

- `GET /api/deployment-agent/auth/me`
- `GET /api/deployment-agent/release-flows`
- `GET /api/deployment-agent/audit-logs`
- `GET /api/deployment-agent/access-grants` as `emp-003`

---

## 6. Frontend Bring-Up

### 6.1 Frontend prerequisites

- Node.js
- npm

### 6.2 Build the SPA

```bash
cd /Users/leo/wwa-lab/deployment-agent/frontend
npm install
npm run build
```

Build output:

- `frontend/dist`

### 6.3 Deploy the SPA

The frontend should be hosted so that browser requests to:

- `/api/deployment-agent/...`

are proxied to the backend.

This is important because [`frontend/src/api/client.ts`](/Users/leo/wwa-lab/deployment-agent/frontend/src/api/client.ts) uses:

- `baseURL: '/api/deployment-agent'`
- `withCredentials: true`

Recommended UAT setup:

- serve frontend static assets from a web server
- reverse proxy `/api` to `http://<uat-backend-host>:8080/api`
- keep frontend and API under the same origin if possible

### 6.4 Frontend smoke validation

Verify:

- login page loads
- after login, the app stays authenticated using session cookie
- main menus load without API/CORS errors

---

## 7. Integrated Bring-Up Sequence

Run the UAT environment in this order:

1. Prepare Oracle schema
2. Start backend against Oracle
3. Validate login and API smoke
4. Build and deploy frontend
5. Validate browser login and session
6. Execute integrated UAT scenarios

Do **not** start with the frontend first.  
If backend schema validation fails, the frontend will only surface secondary errors.

---

## 8. Recommended UAT Scenario Order

### Scenario A — Authentication and access

Use:

- `emp-003` for DevOps Admin
- `emp-001` for Developer
- `emp-004` for Audit

Validate:

- login
- logout
- `auth/me`
- Access Management visibility
- scoped visibility behavior

### Scenario B — Upload to Release Flow

Validate:

- upload Excel
- `Application / SNOW Group / Agent` captured correctly
- Release Flow created or updated correctly
- Rundown Owner assigned correctly

### Scenario C — Rundown controls

Validate:

- `Start Deployment`
- `Mark as Failed`
- owner/admin-only restriction

### Scenario D — Task workflow

Validate:

- `Run`
- `Rerun`
- `Review Decision`
- dependency hints
- task activity / execution history

### Scenario E — Audit and access governance

Validate:

- audit records appear
- audit scope filters work
- access grant changes are logged

### Scenario F — Rundown lifecycle

Validate:

- `Archive Rundown`
- `Restore Rundown`
- `Delete Permanently` for archived rundown only

---

## 9. UAT Exit Checklist

UAT should not be marked ready unless all of the following are true:

- Oracle schema is stable and repeatable
- backend starts cleanly against Oracle without schema validation failure
- frontend is deployed and can call backend via `/api/deployment-agent`
- login/session works through the chosen UAT auth mode
- upload, summary, detail, task flow, audit, and access management all work
- owner/admin restriction on rundown controls is validated
- at least one archive/restore path is verified
- known limitations are documented to stakeholders

---

## 10. Known Limitations To Disclose During UAT

- Current UAT auth is expected to use the stub Team Book provider unless a real provider is separately implemented.
- The repository does not currently provide a complete baseline Oracle schema script alongside the incremental migration files.
- Template Management and Configuration Management are not yet as fully backend-enforced as Release / Audit / Access.
- Execution Target Catalog is not implemented yet.

---

## 11. Recommended Follow-Up Documents

If this UAT runbook will be reused, add or prepare these documents next:

1. **Oracle schema validation checklist for DBAs**
2. **Environment variable sheet for UAT**
3. **Reverse proxy / static hosting config example**
4. **UAT test case sheet**
5. **Rollback guide**

---

## 12. Quick Command Summary

### Backend

```bash
cd /Users/leo/wwa-lab/deployment-agent
export DB_URL='jdbc:oracle:thin:@//<host>:<port>/<service>'
export DB_USERNAME='da_user'
export DB_PASSWORD='******'
export APP_AUTH_BOOTSTRAP_ADMIN_IDS='emp-003'
mvn spring-boot:run
```

### Frontend

```bash
cd /Users/leo/wwa-lab/deployment-agent/frontend
npm install
npm run build
```

### Smoke login

```bash
curl -i -c cookies.txt -H 'Content-Type: application/json' \
  -d '{"employeeId":"emp-003","password":"anything"}' \
  http://<uat-backend-host>:8080/api/deployment-agent/auth/login
```
