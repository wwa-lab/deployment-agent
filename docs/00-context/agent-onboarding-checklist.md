# Agent Onboarding Checklist for WWA

**Version:** 1.0
**Date:** 2026-03-26
**Authority:** `docs/00-context/multi-agent-integration-standard.md`

An agent is **not ready for WWA integration** until every item below is resolved.
Work through this checklist before raising a shell integration request.

---

## 1. Agent Positioning

- [ ] Agent name is confirmed and consistent with `FinBlock → WWA → Agent Workspace` hierarchy
- [ ] Short description is written (one sentence, user-facing)
- [ ] Target users are defined (roles, teams, or personas)
- [ ] Core problem and primary business scenarios are documented
- [ ] Core domain objects are listed
- [ ] Key workflows are described end-to-end
- [ ] Dependent external systems are identified

**Output:** Complete the intake information in `docs/00-context/multi-agent-integration-standard.md` section 8.

---

## 2. Shell Registration

- [ ] Agent metadata is added to `frontend/src/config/agentRegistry.ts` as an `AgentDescriptor` entry
  - `key`: stable, kebab-case, matches route segment (e.g. `testing-agent`)
  - `name`: display name (e.g. `Testing Agent`)
  - `description`: one-line user-facing description
  - `route`: absolute path to the agent entry view (e.g. `/wwa/testing-agent`)
  - `icon`: emoji or ASCII icon character
  - `enabled`: set to `false` initially; flip to `true` when ready for users
  - `category`: one of `deployment`, `testing`, `platform`, `other`
- [ ] Agent route is registered in `frontend/src/router/index.ts` under the `/wwa` parent
  - Include `section`, `sectionTitle`, and optionally `workspaceLabel` in `meta`
- [ ] The agent view (`/wwa/<agent-key>`) renders without shell changes
- [ ] Agent card appears on WWA Home page (`/wwa/home`) via the registry

---

## 3. Navigation and UX

- [ ] Users can navigate from WWA Home into the agent
- [ ] Users can return from the agent to WWA Home via the shell breadcrumb or sidebar
- [ ] Users can return from WWA Home to FinBlock via the standard FinBlock link in the topbar
- [ ] Shell topbar breadcrumb shows `WWA > <Agent Name>` when inside the agent
- [ ] The agent does not rebuild its own login page (auth is handled by WWA)
- [ ] The agent does not add a new FinBlock return link (the shell already provides one)

---

## 4. Access and Permissions

- [ ] Platform vs agent permission boundary is documented
  - Which permission controls entry visibility to this agent on WWA Home?
  - Which permissions are agent-internal (actions within the agent workspace)?
- [ ] Agent-level roles are defined and documented
- [ ] Agent permissions are added to the permission taxonomy in `docs/00-context/wwa-permission-taxonomy.md`
- [ ] If the agent uses WWA Access Management, confirm that `AccessGrant` `assignedRoles` covers the agent's roles
- [ ] Deny-by-default: users with no relevant grant should not see the agent card on WWA Home

---

## 5. Platform Audit

- [ ] Agent populates the minimum platform audit fields on every audit log entry:
  - `agentName`: the agent's stable key (e.g. `testing-agent`)
  - `sourceSystem`: the system that triggered the action (e.g. `wwa-api`)
  - `targetType`: the affected domain object type (e.g. `TestRun`)
  - `targetId`: the affected domain object ID
- [ ] Agent audit events are classifiable as "Platform Events" or "Agent Activity" using the taxonomy in `docs/00-context/wwa-audit-taxonomy.md`
- [ ] Agent does not duplicate platform audit events (e.g. login, access grant changes are owned by WWA)

---

## 6. Configuration

- [ ] Configuration ownership is declared: is any configuration platform-shared or all agent-private?
- [ ] If agent-private: agent manages its own configuration page or section, separate from WWA Configuration Management
- [ ] If platform-shared (rare): coordinate with WWA platform team before introducing a new `ConfigKey`
- [ ] Jenkins, Ansible, and other agent-specific execution settings remain agent-private (see `docs/00-context/wwa-product-positioning.md`)

---

## 7. Operational Readiness

- [ ] Operational owner for the agent workspace is identified
- [ ] The agent can be deployed independently without requiring a WWA shell deployment
- [ ] The agent exposes a health endpoint or equivalent for operational monitoring
- [ ] Any runtime dependencies (external systems, credentials) are documented

---

## 8. Validation

- [ ] Agent entry flow tested: `FinBlock link → WWA Home → Agent card → Agent workspace`
- [ ] Return flow tested: `Agent workspace → WWA Home → FinBlock link`
- [ ] Existing Deployment Agent workflows are unaffected (run `mvn test` — all 167+ tests pass)
- [ ] Frontend typecheck passes: `cd frontend && npx vue-tsc --noEmit`
- [ ] No structural changes to `WorkspaceLayout.vue` were required (if changes were needed, treat as shell defects)

---

## Deployment Agent Reference (completed checklist)

The following shows the completed state for Deployment Agent as the reference implementation.

| Item | Status |
|------|--------|
| Agent name confirmed | ✓ Deployment Agent |
| Shell registration | ✓ `agentRegistry.ts`, key `deployment-agent` |
| Route registered | ✓ `/wwa/deployment-agent` in `router/index.ts` |
| WWA Home card | ✓ Rendered via agentRegistry |
| Permission boundary | ✓ `platform.*` (entry) + `release.*` / `task.*` (agent-private) |
| Audit fields | ✓ `agentName: "deployment-agent"`, `sourceSystem: "wwa-api"` |
| Config ownership | ✓ All agent-private (Jenkins, Ansible) |
| Operational owner | TBD — assign before production |
