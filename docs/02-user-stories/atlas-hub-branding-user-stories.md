# Atlas Hub Branding User Stories

**Date:** 2026-06-29
**Status:** Proposed / Documents-first
**Source requirement:** [atlas-hub-branding-requirement.md](../01-requirements/atlas-hub-branding-requirement.md)

## Story AHB-US-01: Reviewer Sees The Hub Brand First

**As a** competition reviewer,
**I want** the browser title and first UI screen to say Atlas Engineering Delivery Hub,
**so that** I understand the repository is the Hub framework, not only a Deployment Agent app.

Acceptance criteria:

1. Browser title uses Atlas Engineering Delivery Hub.
2. Login page hero uses Atlas Engineering Delivery Hub.
3. Home page headline uses Atlas Engineering Delivery Hub.

## Story AHB-US-02: Agent Naming Stays Clear

**As a** release contributor,
**I want** Deployment Agent to remain the deployment workspace name,
**so that** existing routes, docs, and mental model still match the M6 agent.

Acceptance criteria:

1. Agent cards and routes still show Deployment Agent.
2. Deployment Agent is described as inside Atlas Engineering Delivery Hub.
3. M6 Deployment is treated as a lifecycle stage label, not a replacement product name.

## Story AHB-US-03: Existing Links Keep Working

**As a** maintainer,
**I want** visible branding to change without route/API churn,
**so that** the competition package improves clarity without destabilizing the app.

Acceptance criteria:

1. `/wwa/deployment-agent` remains the Deployment Agent route.
2. `/api/deployment-agent/*` remains unchanged.
3. Technical identifiers are documented as compatibility identifiers.

## Story AHB-US-04: Docs Explain The Naming Boundary

**As a** future contributor,
**I want** docs to explain visible brand versus technical identifiers,
**so that** I do not start a risky technical rename by accident.

Acceptance criteria:

1. README and architecture docs mention Atlas Engineering Delivery Hub as the visible brand.
2. Changelog records the branding update.
3. SDD traceability links the requirement to implementation tasks.

## Story AHB-US-05: Branding Change Is Verified

**As a** maintainer,
**I want** frontend and docs checks to pass,
**so that** the branding change is safe to present.

Acceptance criteria:

1. Frontend build passes.
2. Markdown links pass.
3. Whitespace diff check passes.
