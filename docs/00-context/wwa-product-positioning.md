# WWA Agent Workspace Hub Positioning Decision Record

**Date:** 2026-03-26
**Status:** Approved
**Owner:** WWA Agent Workspace Hub direction
**Gates:** All route, permission, navigation, and documentation work in the platform transition

---

## Decision

The approved product hierarchy is:

```
FinBlock  →  WWA Agent Workspace Hub  →  Agent Workspace
```

This is not a temporary arrangement. It is the foundational operating model for all current and future work in this platform.

---

## Naming

| Term | Definition |
|------|------------|
| **FinBlock** | The upstream business system. Provides one stable entry link to WWA. Does not own agent logic, navigation, or permissions. |
| **WWA Agent Workspace Hub (`WWA`)** | The unified DevOps hub above all agent workspaces. Owns authentication, top-level navigation, platform access governance, platform-level audit, and shared platform capabilities. |
| **Agent Workspace** | An independently deployable microservice workspace for a specific agent domain. Presented consistently under the WWA Agent Workspace Hub. |
| **Release Agent** | The first mature workspace under WWA. Owns deployment workflow, release flow logic, and execution integrations (Jenkins, Ansible). |
| **Testing Agent** | The planned second workspace. Follows the same integration standard as Release Agent. |

---

## Naming Rationale

The workspace should now be called `Release Agent`, not `Deployment Agent`.

**Reason:** the current scope is broader than deployment execution alone. The workspace already owns release-flow lifecycle management, human review and progression control, execution coordination across tools, scoped visibility, and access-governance behaviors. `Release Agent` better matches that end-to-end operating boundary while still fitting the WWA `Agent Workspace` naming model.

**Transition rule:** this naming update is currently a documentation and product-language change. Existing technical identifiers remain stable until a separate migration is approved. That includes the repository name, package namespace, route slug `/wwa/deployment-agent`, and API prefix `/api/deployment-agent`.

The platform layer should now be described as `WWA Agent Workspace Hub`.

**Reason:** the current top layer is not just a visual shell. It is the shared DevOps hub for entry, navigation, access governance, audit visibility, and multi-workspace expansion. In day-to-day writing, `WWA` remains the short label for that hub.

---

## Structural Rules

1. `FinBlock` provides **one** stable entry link pointing to the WWA home page, not to a specific agent.
2. `WWA Agent Workspace Hub` is the platform entry point. Users authenticate in WWA, not inside an agent.
3. Users land on the **WWA Agent Workspace Hub home page** after login. They choose their destination from there.
4. `Release Agent` is **the first workspace**, not the implicit product shell or the default landing page.
5. Shared capabilities (Access Management, Audit Log) are **platform-owned**, not Release-Agent-owned.
6. Template Management stays **Release-Agent-scoped** until a second agent proves that a shared template framework is needed. See section below.

---

## Template Management Scope Decision

Template workflows remain Release-Agent-owned for the current phase.

**Reason:** No second agent currently requires shared templates. Over-extracting before reuse is proven creates unnecessary abstraction cost and complicates future agent onboarding.

**Review trigger:** After the second agent (Testing Agent) is onboarded and running. If Testing Agent requires template management, reassess at that point.

**Code implication:** The `/wwa/template-management` route and `TemplateManagementView.vue` remain in their current location. No migration to a platform-level namespace until the review trigger is met.

---

## Configuration Scope Decision

All current configuration keys (`jenkins_url`, `jenkins_user`, `jenkins_api_token`, `ansible_url`, `ansible_user`, `ansible_api_token`, `execution_callback_endpoint`) are **agent-private** to Release Agent.

No platform-shared configuration keys exist yet. Any new platform-level configuration (e.g., agent registry metadata, notification settings) should be introduced as a distinct category and not mixed with existing agent-private keys.

---

## Implications for Implementation

This document is the authority for any naming, routing, or permission question during the platform transition. When in doubt:

- The word "WWA" refers to the `WWA Agent Workspace Hub`, not to Release Agent.
- The term "Release Agent" refers to the first agent workspace, never to the platform as a whole.
- Shared pages belong to WWA. Agent-specific pages belong to the owning agent.
