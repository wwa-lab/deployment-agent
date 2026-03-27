# WWA Agent Workspace Hub Integration Standard (Link-to-WWA Mode) v0.1

**Date:** 2026-03-26
**Status:** Draft
**Owner:** WWA Agent Workspace Hub direction

---

## 1. Purpose

This standard defines how future agents should integrate with FinBlock and WWA.

In this document, `WWA` refers to the **WWA Agent Workspace Hub**, the shared DevOps hub above individual agent workspaces.

This version adopts the `Link-to-WWA` model:

- `FinBlock` does not host agent platform capabilities.
- `FinBlock` provides one stable entry link to `WWA`.
- Users authenticate in `WWA`, navigate within `WWA`, and then enter a specific agent workspace.
- Each agent remains an independent microservice and is presented consistently under the `WWA Agent Workspace Hub`.

The goal is to avoid redesigning authentication, navigation, permissions, and audit behavior every time a new agent is added.

---

## 2. Product Positioning

- `FinBlock`: business system and upstream entry point
- `WWA Agent Workspace Hub` (`WWA`): unified DevOps hub for agent workspaces
- `Agent Workspace`: independent microservice workspace for a specific agent domain
- `Release Agent`: the first mature workspace under `WWA`
- Future agents such as `Testing Agent`: follow the same integration standard

This model should be understood as:

`FinBlock -> WWA -> Agent Workspace`

not:

`FinBlock -> many unrelated external tools`

---

## 3. Core Design Decisions

- FinBlock will not pass rich business context in the initial phase.
- FinBlock will not embed agent pages inside its own UI.
- WWA is the single platform entry for all agents.
- Users should first perceive `WWA` as a platform, then choose a specific agent.
- Agent teams may deploy and scale independently, but user experience should still feel like one platform.

---

## 4. User Journey

1. A user clicks the `WWA` entry in FinBlock.
2. The browser opens the WWA entry page.
3. The user completes authentication in WWA.
4. WWA shows the list of available agents based on access rights.
5. The user selects an agent such as `Release Agent` or `Testing Agent`.
6. The user performs work inside the selected agent workspace.
7. The user can switch to other agents inside WWA or return to FinBlock.

---

## 5. FinBlock Responsibilities

FinBlock is intentionally lightweight in this model.

FinBlock must:

- provide one stable `WWA` entry link
- use a consistent entry label such as `WWA` or `Agent Workspace`
- point that link to the WWA home page, not to one specific agent page

FinBlock does not need to:

- pass release, project, environment, or other rich context in phase one
- embed agent UIs inside FinBlock
- manage separate entries for each agent
- own agent-specific navigation, permissions, configuration, or audit behavior

---

## 6. WWA Agent Workspace Hub Responsibilities

WWA is the unified shell and must provide the shared platform experience.

WWA must provide:

- unified authentication
- top-level platform navigation
- an agent directory or WWA home page
- agent switching
- access management
- platform-level audit log
- a clear path back to FinBlock
- a shared visual shell and interaction baseline

WWA should not absorb:

- each agent's full domain workflow
- each agent's domain data model
- each agent's detailed execution UI

---

## 7. Agent Workspace Responsibilities

Each agent microservice owns its own domain behavior.

Each agent is responsible for:

- domain workflows
- domain data model
- external integrations
- domain-level configuration
- domain execution, review, and results handling
- detailed in-agent activity logging

Each agent should not rebuild:

- login pages
- the overall Agent Workspace Hub shell
- platform entry permissions
- the platform-level audit entry point
- a separate return-to-FinBlock pattern

---

## 8. Minimum Agent Intake Information

Before a new agent is accepted into WWA, the proposing team must define:

- agent name
- short description
- target users
- core problem being solved
- primary business scenarios
- core domain objects
- key workflows
- dependent external systems
- platform-level access requirement
- agent-level role model
- need for platform audit visibility
- need for shared inbox or task surfacing
- need for shared configuration entry

---

## 9. Access Model

Access is split into two layers.

Platform-level access decides:

- whether the user can enter WWA
- whether the user can see a given agent
- whether the user can access shared capabilities such as Access Management or platform audit

Agent-level access decides:

- what the user can do inside an agent workspace
- for example upload, review, execute, configure, approve, or administer

Principles:

- platform access is owned by WWA
- agent permissions are owned by each agent
- permission changes must be auditable
- permission naming should be consistent where possible

---

## 10. Navigation Standard

WWA navigation must ensure that users can always tell:

- they are inside `WWA`
- which agent they are currently using
- how to return to the WWA home page
- how to switch to another agent
- how to return to FinBlock

Recommended hierarchy:

- level 1: `WWA`
- level 2: `Release Agent`, `Testing Agent`, other future agents
- shared areas: `Access Management`, `Audit Log`, other future shared capabilities

Agent access must flow through the WWA shell rather than through scattered hardcoded URLs.

---

## 11. WWA Home Page Standard

WWA must have a dedicated home page.

WWA must not simply redirect all users into Release Agent by default, because that would make future agents feel secondary and would blur the distinction between the platform and its first workspace.

The WWA home page should include:

- platform summary
- current user identity
- available agent cards
- recent agent visits
- inbox or pending work entry
- platform notices or status guidance

---

## 12. Audit Standard

All agents must support a minimum shared audit shape for platform visibility.

Minimum common audit fields:

- `operatorId`
- `agentName`
- `actionType`
- `targetType`
- `targetId`
- `timestamp`
- `result`
- `sourceSystem`
- `contextPayload`

Recommended split:

- `WWA Platform Audit`
  - login, agent entry, permission changes, platform settings
- `Agent Activity Log`
  - domain-specific detailed actions inside each agent

---

## 13. Configuration Standard

Configuration should be split into two classes.

Platform shared configuration:

- authentication-related setup
- platform navigation or registry settings
- shared inbox or notification settings
- agent registration metadata

Agent private configuration:

- Jenkins, Ansible, testing platform, or other external integration settings
- domain execution settings
- agent-specific feature toggles

Principles:

- do not force all configuration into the platform layer
- keep platform configuration limited to true shared concerns
- keep domain configuration close to the owning agent

---

## 14. Template Standard

Template capability should not be over-unified too early.

Rules:

- if a template model serves only one agent, it should stay inside that agent
- if multiple agents share a management frame but not the same business fields, use a unified entry with agent-specific models
- do not create one platform template object prematurely just for consistency

Current guidance:

- `Template Management` should remain Release-Agent-centric for now
- after the second agent is onboarded, reassess whether template management should become a shared WWA entry point

---

## 15. Agent Readiness Checklist

An agent is not ready for WWA integration until the following are clear:

- agent positioning is defined
- target users are defined
- business boundaries are defined
- platform versus agent permission boundaries are defined
- the agent can participate in the unified login flow
- the agent is reachable from WWA navigation
- the agent can be entered from the WWA home page
- the user can return from the agent to WWA
- the user can return from WWA to FinBlock
- the agent emits the minimum platform audit fields
- configuration ownership is defined
- operational ownership is defined

---

## 16. Roles and Ownership

- `FinBlock team`
  - owns the WWA entry point
  - does not own agent-internal workflows

- `WWA Agent Workspace Hub team`
  - owns shell navigation
  - owns unified authentication
  - owns platform access management
  - owns platform-level audit visibility

- `Agent team`
  - owns business workflows
  - owns agent UI and domain rules
  - owns agent integrations
  - owns agent-private configuration
  - owns domain-level permissions and activity details

---

## 17. Recommended Delivery Sequence

1. Formally position the current system as `WWA + Release Agent`.
2. Create a dedicated WWA home page instead of defaulting directly into Release Agent.
3. Stabilize unified login, shell navigation, platform access, and platform audit.
4. Onboard a second agent using the same model.
5. Use the second onboarding to validate and refine this standard.
6. Expand later into shared inbox, agent registry, and cross-agent status aggregation.

---

## 18. Explicit Tradeoffs In This Version

This standard intentionally accepts the following tradeoffs in the initial phase:

- FinBlock does not pass rich business context into WWA
- users may need to choose a target agent after entering WWA
- users may need to navigate to the relevant object within an agent instead of landing directly on it
- FinBlock remains simple and does not evolve into the agent platform itself

This is a deliberate sequencing choice. The first priority is to establish a stable multi-agent platform model before investing in deeper context handoff and richer upstream integration.

---

## 19. Summary

The operating model for this standard is:

- `FinBlock` provides entry
- `WWA` provides the Agent Workspace Hub
- each `Agent` provides a specialized workspace

The integration target is a unified platform experience built on independently evolving agent services, not a growing collection of unrelated external tools.
