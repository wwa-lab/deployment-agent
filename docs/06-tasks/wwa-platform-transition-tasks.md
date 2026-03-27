# WWA Agent Workspace Hub Transition Task Breakdown

## Overview

This document breaks the current `Link-to-WWA` direction into implementation-ready tasks.

The goal is not to rebuild Deployment Agent from scratch. The goal is to reposition the current workspace as the first agent under the broader `WWA Agent Workspace Hub`, while keeping FinBlock intentionally lightweight and preserving the existing Deployment Agent workflow baseline.

In this document, `WWA` refers to the `WWA Agent Workspace Hub`, the shared DevOps hub above individual workspaces.

**Delivery objective**
- Keep `FinBlock` limited to a single entry link to `WWA`
- Establish `WWA` as the unified Agent Workspace Hub for authentication, navigation, access governance, and shared platform capabilities
- Reposition `Deployment Agent` as the first `WWA` workspace instead of the implicit default product shell
- Prepare the codebase and information architecture so a second and third agent can be added without redesigning the platform model

**Planning assumptions**
- FinBlock will not pass rich business context in the initial phase.
- WWA will own authentication, top-level navigation, platform access, and platform-level audit.
- Each agent will remain an independent microservice or independently deployable workspace.
- Deployment Agent domain workflows should remain stable during the initial platform transition.
- Template and configuration capabilities should not be over-generalized too early.

### Current Workspace Alignment

- **Already present in the current workspace**
  - `/wwa/...` route namespace and shared shell layout
  - visible shared-capability pages for `Configuration Management`, `Audit Log`, and `Access Management`
  - a working Deployment Agent flow inside the shell
  - deny-by-default access patterns and a working access-management baseline
- **Partially aligned**
  - `WWA` exists structurally, but the product still defaults directly into `Deployment Agent`
  - shared pages exist, but some wording and ownership still center on Deployment Agent
  - navigation is already grouped, but only one real agent workspace is active
- **Still missing**
  - a dedicated `WWA Home / Agent Directory`
  - an explicit agent registry model
  - clear separation between platform-level and agent-level responsibilities
  - a validated onboarding path for a second agent

---

## Source Direction

**Operating model**
- `FinBlock` provides entry
- `WWA` provides the Agent Workspace Hub
- each `Agent` provides a specialized workspace

**Target state summary**
- Users click one `WWA` link in FinBlock
- Users authenticate in WWA
- Users land on a neutral WWA home page
- Users select an accessible agent such as `Deployment Agent` or `Testing Agent`
- Shared capabilities such as platform access management and platform audit remain visible in WWA
- Agent-specific workflows stay inside the owning agent workspace

---

## Workstreams

### Major Implementation Streams

1. Platform positioning and information architecture
2. WWA shell routing and navigation
3. Platform-level access and audit model
4. Shared-capability boundary clarification
5. Deployment Agent workspace normalization
6. Multi-agent onboarding readiness

### Recommended Sequencing

1. Lock product positioning and naming
2. Add `WWA Home / Agent Directory`
3. Stop defaulting directly into Deployment Agent
4. Clarify platform-level versus agent-level permissions and audit
5. Normalize shared-capability ownership
6. Reframe Deployment Agent as the first workspace
7. Onboard a second agent as the validation milestone

### Parallel Work Opportunities

- WWA home-page UX can proceed in parallel with platform audit model design
- shared-capability wording cleanup can proceed in parallel with route changes
- agent registry design can begin before the second agent is ready to implement
- documentation and acceptance-checklist work can run alongside shell implementation

---

## Task Breakdown by Domain

### Product / Information Architecture
- Define final naming and platform hierarchy
- Create WWA home-page content and agent-directory model
- Clarify platform/shared versus agent/private capability boundaries

### Frontend / Shell
- Add neutral WWA landing page
- Update route defaults and navigation behavior
- Add agent registry-driven navigation and entry cards
- Add platform-level breadcrumbs and return-to-FinBlock affordances

### Backend / Platform
- Define platform access model versus agent access model
- Expand platform-level audit semantics
- Provide registry or configuration support for agent discovery if needed

### Governance / Documentation
- Update architecture and design docs to reflect the new operating model
- Define onboarding checklist for new agents
- Define rollout and acceptance criteria for the second agent pilot

### Verification
- Validate WWA home flow
- validate that Deployment Agent workflows still work after shell changes
- validate that platform shared pages still behave correctly
- validate that a second agent can be introduced without redesigning the shell

---

## Task Details

### WWA-001: Confirm WWA Product Positioning and Naming
- **Objective**: Lock the product model before UI and platform refactoring begins.
- **Scope**: Confirm that `FinBlock -> WWA -> Agent Workspace` is the approved product structure; confirm naming for `WWA`, `Agent Workspace`, `Deployment Agent`, and future agent categories.
- **Dependencies**: None
- **Owner type**: product
- **Priority**: Must
- **Notes**: This decision should be treated as the foundation for all later route, permission, and documentation work.

### WWA-002: Add Dedicated WWA Home / Agent Directory
- **Objective**: Make WWA feel like a platform, not just a redirector into Deployment Agent.
- **Scope**: Design and build a neutral WWA home page that shows platform identity, available agents, shared capabilities, and future extensibility points.
- **Dependencies**: WWA-001
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: WWA home should not assume Deployment Agent is the only destination.

### WWA-003: Change Default Routing to Land in WWA Home
- **Objective**: Remove the current assumption that the default entry is Deployment Agent.
- **Scope**: Update route defaults, redirects, and entry behavior so `/` and the WWA entry land on the WWA home page rather than `/wwa/deployment-agent`.
- **Dependencies**: WWA-002
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: This is the most visible structural change in the transition.

### WWA-004: Introduce an Agent Registry Model
- **Objective**: Create a standard way to register and display current and future agents.
- **Scope**: Define a model for agent metadata such as name, route, description, visibility rules, iconography, and platform category; use it to drive WWA home and shell navigation.
- **Dependencies**: WWA-001
- **Owner type**: platform
- **Priority**: Must
- **Notes**: This should become the standard onboarding contract for future agents.

### WWA-005: Refactor Shell Navigation Around Platform-First Semantics
- **Objective**: Make the shell clearly read as `WWA Agent Workspace Hub first, agent second`.
- **Scope**: Update topbar, sidebar, breadcrumbs, active titles, and menu groupings so users can always tell when they are in WWA, when they are in a shared capability, and when they are in a specific agent.
- **Dependencies**: WWA-002, WWA-004
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Avoid language that implies Deployment Agent owns the whole shell.

### WWA-006: Add a Clear Return-to-FinBlock Pattern
- **Objective**: Ensure users can reliably return to the upstream business system.
- **Scope**: Add a clear and consistent return path from WWA back to FinBlock, including placement, wording, and expected behavior from both the WWA home page and agent workspaces.
- **Dependencies**: WWA-002
- **Owner type**: frontend
- **Priority**: Should
- **Notes**: In the current phase, this can be a simple platform affordance rather than a deep-linked context return.

### WWA-007: Define Platform-Level Access Versus Agent-Level Access
- **Objective**: Prevent access governance from staying implicitly Release-Agent-centric.
- **Scope**: Define which permissions control WWA entry, which permissions control visibility of shared capabilities, and which permissions remain agent-specific.
- **Dependencies**: WWA-001
- **Owner type**: backend
- **Priority**: Must
- **Notes**: This task is about authorization boundaries, not only UI wording.

### WWA-008: Reframe Access Management as a Platform Capability
- **Objective**: Evolve Access Management from “who can enter Deployment Agent” into “who can enter WWA and which platform/agent capabilities they can see”.
- **Scope**: Update the conceptual model, UX wording, data assumptions, and future API direction for Access Management so it supports a multi-agent platform.
- **Dependencies**: WWA-007
- **Owner type**: platform
- **Priority**: Must
- **Notes**: The first implementation may remain partially backward-compatible while the model changes.

### WWA-009: Define Platform Audit Model
- **Objective**: Establish a platform-level audit view that can span multiple agents.
- **Scope**: Define common audit fields, platform-level audit events, and the relationship between platform audit and agent-specific activity history.
- **Dependencies**: WWA-001
- **Owner type**: backend
- **Priority**: Must
- **Notes**: This task should avoid forcing all domain activity into one generic event model.

### WWA-010: Split Platform Audit from Agent Activity
- **Objective**: Clarify what belongs in shared audit and what stays in agent-local history.
- **Scope**: Adjust UX, wording, retrieval rules, and traceability expectations so users understand the difference between platform-wide audit and detailed in-agent execution history.
- **Dependencies**: WWA-009
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: Keep agent-local detail views where they are strongest; do not flatten everything into one grid.

### WWA-011: Classify Configuration Into Platform-Shared and Agent-Private
- **Objective**: Prevent configuration management from becoming a catch-all page with unclear ownership.
- **Scope**: Classify current configuration items, define which ones truly belong to the WWA Agent Workspace Hub, and which ones should remain inside Deployment Agent or other future agents.
- **Dependencies**: WWA-001
- **Owner type**: platform
- **Priority**: Must
- **Notes**: Jenkins and Ansible settings are likely still Release-Agent-private in the short term.

### WWA-012: Keep Template Management Release-Agent-Scoped for Now
- **Objective**: Avoid premature over-generalization of templates before the second agent exists.
- **Scope**: Confirm that current template workflows remain Release-Agent-owned until a second agent proves that a shared template framework is needed.
- **Dependencies**: WWA-001
- **Owner type**: product
- **Priority**: Must
- **Notes**: This task is an explicit non-generalization decision and should be documented clearly.

### WWA-013: Normalize Deployment Agent as the First Workspace
- **Objective**: Reposition Deployment Agent from implicit shell owner to explicit first workspace under WWA.
- **Scope**: Update navigation labels, page framing, entry points, and workspace cues while preserving existing Deployment Agent behavior, routes, and domain logic wherever possible.
- **Dependencies**: WWA-003, WWA-005
- **Owner type**: frontend
- **Priority**: Must
- **Notes**: This task should preserve the existing deployment workflow baseline.

### WWA-014: Update Supporting Documentation for the New Operating Model
- **Objective**: Keep architecture, design, and implementation planning aligned with the new direction.
- **Scope**: Update relevant documents so they describe WWA as the Agent Workspace Hub and Deployment Agent as its first workspace under the `Link-to-WWA` model.
- **Dependencies**: WWA-001
- **Owner type**: architecture
- **Priority**: Must
- **Notes**: This task should include cross-document consistency checks.

### WWA-015: Define New-Agent Onboarding Checklist
- **Objective**: Prevent future agents from integrating in ad hoc ways.
- **Scope**: Create a lightweight but enforceable onboarding checklist covering naming, visibility, routing, permissions, audit requirements, shared-capability usage, and operational ownership.
- **Dependencies**: WWA-004, WWA-007, WWA-009
- **Owner type**: platform
- **Priority**: Must
- **Notes**: This task operationalizes the multi-agent standard.

### WWA-016: Add Acceptance Tests for WWA Entry and Shell Behavior
- **Objective**: Protect the platform transition from regressions.
- **Scope**: Add coverage for login flow into WWA, WWA home routing, shell navigation, shared-capability access visibility, and Deployment Agent reachability from the WWA home page.
- **Dependencies**: WWA-002, WWA-003, WWA-005, WWA-013
- **Owner type**: QA
- **Priority**: Must
- **Notes**: This should complement existing Deployment Agent workflow tests rather than replace them.

### WWA-017: Validate Deployment Agent Regression Baseline Under the New Shell
- **Objective**: Ensure the platform transition does not break the existing first workspace.
- **Scope**: Re-run core Deployment Agent flows under the new entry and shell model, including login, release summary, release detail, task actions, configuration visibility, and audit visibility.
- **Dependencies**: WWA-013, WWA-016
- **Owner type**: QA
- **Priority**: Must
- **Notes**: This is the minimum safety check before introducing a second agent.

### WWA-018: Prepare the Second Agent Pilot
- **Objective**: Use one real second workspace to validate that WWA is genuinely multi-agent-ready.
- **Scope**: Choose the pilot agent, define its metadata, determine initial access rules, confirm whether it needs shared audit visibility, and prepare its shell entry under the agent registry.
- **Dependencies**: WWA-004, WWA-015
- **Owner type**: product
- **Priority**: Should
- **Notes**: `Testing Agent` is the obvious candidate if it is the next likely entrant.

### WWA-019: Onboard the Second Agent Through the Standardized Path
- **Objective**: Prove that the platform model works for more than one agent.
- **Scope**: Register the second agent, expose it through WWA home and navigation, enforce access visibility, and validate that it does not require a new shell design.
- **Dependencies**: WWA-018
- **Owner type**: platform
- **Priority**: Should
- **Notes**: This is the milestone that turns the model from theory into a repeatable pattern.

### WWA-020: Review and Refine the Platform Model After the Second Agent
- **Objective**: Close the loop after the first real multi-agent onboarding.
- **Scope**: Review onboarding friction, shared-capability boundaries, audit usefulness, permission clarity, and shell extensibility; refine the integration standard based on evidence.
- **Dependencies**: WWA-019
- **Owner type**: architecture
- **Priority**: Should
- **Notes**: The platform should be refined after the second agent, not over-designed before it.

---

## Recommended Delivery Phases

### Phase 1: Establish WWA as a Real Platform Shell
- WWA-001
- WWA-002
- WWA-003
- WWA-004
- WWA-005
- WWA-006
- WWA-013
- WWA-014

### Phase 2: Clarify Shared Governance Boundaries
- WWA-007
- WWA-008
- WWA-009
- WWA-010
- WWA-011
- WWA-012
- WWA-015

### Phase 3: Validate the Multi-Agent Model
- WWA-016
- WWA-017
- WWA-018
- WWA-019
- WWA-020

---

## Success Criteria

- Users enter `WWA` from FinBlock through one stable link.
- Users land on a real WWA home page rather than directly inside Deployment Agent.
- Deployment Agent still works as the first workspace without major workflow regression.
- Shared capabilities read as platform-owned rather than Release-Agent-owned where appropriate.
- A second agent can be added without redesigning the shell or product model.

---

## Risks and Guardrails

- **Risk:** WWA remains visually or conceptually synonymous with Deployment Agent.
  - **Guardrail:** add a neutral WWA home page and platform-first navigation language.
- **Risk:** shared capabilities are generalized too early.
  - **Guardrail:** keep templates and agent-private configuration close to Deployment Agent until a second agent proves reuse.
- **Risk:** access governance stays single-agent-shaped.
  - **Guardrail:** define platform access and agent access separately before the second agent arrives.
- **Risk:** the second agent still requires a custom shell path.
  - **Guardrail:** require agent-registry-driven onboarding and validate it with a pilot.
