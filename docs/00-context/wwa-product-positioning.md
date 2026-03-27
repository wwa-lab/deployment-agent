# WWA Product Positioning Decision Record

**Date:** 2026-03-26
**Status:** Approved
**Owner:** WWA platform direction
**Gates:** All route, permission, navigation, and documentation work in the platform transition

---

## Decision

The approved product hierarchy is:

```
FinBlock  →  WWA  →  Agent Workspace
```

This is not a temporary arrangement. It is the foundational operating model for all current and future work in this platform.

---

## Naming

| Term | Definition |
|------|------------|
| **FinBlock** | The upstream business system. Provides one stable entry link to WWA. Does not own agent logic, navigation, or permissions. |
| **WWA** | The unified agent platform shell. Owns authentication, top-level navigation, platform access governance, platform-level audit, and shared platform capabilities. |
| **Agent Workspace** | An independently deployable microservice workspace for a specific agent domain. Presented consistently under the WWA shell. |
| **Deployment Agent** | The first mature workspace under WWA. Owns deployment workflow, release flow logic, and execution integrations (Jenkins, Ansible). |
| **Testing Agent** | The planned second workspace. Follows the same integration standard as Deployment Agent. |

---

## Structural Rules

1. `FinBlock` provides **one** stable entry link pointing to the WWA home page, not to a specific agent.
2. `WWA` is the platform entry point. Users authenticate in WWA, not inside an agent.
3. Users land on the **WWA home page** after login. They choose their destination from there.
4. `Deployment Agent` is **the first workspace**, not the implicit product shell or the default landing page.
5. Shared capabilities (Access Management, Audit Log) are **platform-owned**, not Deployment-Agent-owned.
6. Template Management stays **Deployment-Agent-scoped** until a second agent proves that a shared template framework is needed. See section below.

---

## Template Management Scope Decision

Template workflows remain Deployment-Agent-owned for the current phase.

**Reason:** No second agent currently requires shared templates. Over-extracting before reuse is proven creates unnecessary abstraction cost and complicates future agent onboarding.

**Review trigger:** After the second agent (Testing Agent) is onboarded and running. If Testing Agent requires template management, reassess at that point.

**Code implication:** The `/wwa/template-management` route and `TemplateManagementView.vue` remain in their current location. No migration to a platform-level namespace until the review trigger is met.

---

## Configuration Scope Decision

All current configuration keys (`jenkins_url`, `jenkins_user`, `jenkins_api_token`, `ansible_url`, `ansible_user`, `ansible_api_token`, `execution_callback_endpoint`) are **agent-private** to Deployment Agent.

No platform-shared configuration keys exist yet. Any new platform-level configuration (e.g., agent registry metadata, notification settings) should be introduced as a distinct category and not mixed with existing agent-private keys.

---

## Implications for Implementation

This document is the authority for any naming, routing, or permission question during the platform transition. When in doubt:

- The word "WWA" refers to the platform shell, not to Deployment Agent.
- The term "Deployment Agent" refers to the first agent workspace, never to the platform as a whole.
- Shared pages belong to WWA. Agent-specific pages belong to the owning agent.
