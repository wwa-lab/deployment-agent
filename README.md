# Atlas Engineering Delivery Hub

**Category:** Framework

Atlas Engineering Delivery Hub is an end-to-end SDLC delivery framework that organizes planning, discovery, build, testing, deployment, and maintenance work through the Seven Mountains SDLC and Seven Gates Flow.

This repository is the framework entry for the internal open collaboration competition. The current codebase is the WWA Agent Workspace Hub implementation baseline: a Spring Boot and Vue platform with shared governance services plus agent workspaces for build, testing, and deployment operations.

![Atlas Engineering Delivery Hub lifecycle](docs/assets/atlas-framework-lifecycle.svg)

## Framework Positioning

Atlas Engineering Delivery Hub is not a single release tool. It is a reusable delivery framework for teams that need:

- lifecycle visibility across the whole SDLC;
- process control through explicit stage gates and human-in-the-loop decisions;
- quality validation through traceable evidence and review checkpoints;
- delivery traceability through audit logs, access scope, task history, and SDD artifacts;
- always-on delivery operations that can plug in human teams, AI agents, automation, and external systems.

The framework is implemented today through the WWA Agent Workspace Hub and its agent-module pattern. Future stage capabilities can plug into the same lifecycle and gate model without replacing the parent framework.

## Seven Mountains SDLC

The framework models the SDLC as seven mountains. Each mountain has a clear delivery responsibility and a gate before downstream work continues.

| Mountain | Purpose | Current scope |
|---|---|---|
| Planning | Define project intent, approvals, scope, and boundaries. | Represented in the SDLC coverage model. |
| Estimation | Baseline schedule, cost, resource, and risk assumptions. | Represented in the SDLC coverage model. |
| Discovery | Turn business intent into requirements and executable design. | Target stage; Atlas Phoenix Lens can plug in here as one example capability. |
| Build | Produce code changes, local verification, and build artifacts. | Implemented through Build Agent. |
| Testing | Validate behavior, quality, defects, and acceptance evidence. | In progress through Testing Agent. |
| Deployment | Coordinate SIT, UAT, PROD rollout and launch acceptance. | Implemented through Deployment Agent. |
| Maintenance | Route production feedback, incidents, and improvements back into the SDLC. | Target stage. |

![Seven Mountains SDLC](docs/assets/seven-mountains-sdlc.svg)

## Seven Gates Flow And I-E-O-V

Every mountain is governed by a repeatable gate model:

| Gate element | Meaning |
|---|---|
| Input | Required artifacts, scope, owners, constraints, and preconditions. |
| Execute | Controlled human, agent, automation, or tool work. |
| Output | Durable artifacts, decisions, run records, and traceable results. |
| Validate | Review checks, approvals, test evidence, and audit records. |

The existing implementation expresses this through task status, execution history, decision gates, audit records, scoped access, and SDD traceability.

![Seven Gates I-E-O-V flow](docs/assets/seven-gates-ieov.svg)

## Framework Capabilities

- **Lifecycle map:** Seven-stage SDLC coverage and ownership visibility through the Agent Contribute Dashboard.
- **Platform core:** Shared authentication, access governance, audit log, configuration management, uploads, task progression, and release-flow services.
- **Agent modules:** Stage-specific workspaces for Build, Testing, Deployment, and future capabilities.
- **Human-in-the-loop control:** Explicit task execution, result recording, review, rerun, skip, and approval decisions.
- **Traceability:** SDD documents, audit logs, task history, release-flow state, ownership data, and contribution metadata.
- **AI-friendly workflow surface:** Structured docs, stable stage contracts, explicit gates, and sample adoption templates make the framework easy for AI agents and humans to share.

## Current Scope

Implemented today:

- WWA Agent Workspace Hub platform shell.
- Build Agent for DEV-stage build workflows.
- Deployment Agent for SIT / UAT / PROD rollout orchestration.
- Testing Agent baseline for UAT testing workflows.
- Agent Contribute Dashboard for Seven Mountains SDLC visibility.
- Platform access management, audit, configuration, template download, upload, and task lifecycle services.

Not implemented as runtime capabilities yet:

- Discovery-stage production capability.
- Maintenance-stage production capability.
- A full external notification dispatcher.
- Real customer-specific diagrams or screenshots in this open-collaboration package.

Detailed current implementation notes are preserved in [WWA Agent Workspace Hub Current Baseline](docs/wwa-agent-workspace-hub-current-baseline.md).

## Architecture Overview

The current architecture has four reusable layers:

1. **Lifecycle model:** Seven Mountains SDLC and Seven Gates / I-E-O-V.
2. **Governance model:** human-in-the-loop decisions, access scopes, audit, validation evidence, and SDD traceability.
3. **Platform Core:** shared Spring Boot services and Vue platform surfaces under `/api/platform/*` and WWA shared controls.
4. **Agent Modules:** independent workspaces such as Build Agent, Testing Agent, Deployment Agent, and future stage capabilities.

For implementation details, see [System Architecture: Deployment Agent + Platform Core](docs/04-architecture/architecture.md) and [Atlas Engineering Delivery Hub Packaging Architecture](docs/04-architecture/atlas-engineering-delivery-hub-architecture.md).

## Demo Walkthrough

A reviewer can understand the framework through this synthetic scenario:

1. A team frames a project in Planning and Estimation, recording owners, scope, and risk boundaries.
2. A Discovery capability such as Atlas Phoenix Lens can plug in to convert raw business input into requirements and design evidence.
3. Build Agent turns the approved design into traceable DEV tasks and artifacts.
4. Testing Agent tracks validation evidence and defect feedback.
5. Deployment Agent coordinates SIT, UAT, and PROD rollout with task-level review decisions.
6. Maintenance routes production learning back into the lifecycle as future work.

To run the current local application baseline:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

```bash
cd frontend
npm install
npm run dev
```

Then open `http://localhost:5173/wwa/agent-contribute-dashboard` to view the Seven Mountains coverage dashboard.

## Relationship To Sub-Capabilities

Atlas Phoenix Lens is a Discovery-stage sub-capability example. It can help with requirement and discovery intelligence, but it is not the parent framework. Atlas Engineering Delivery Hub is the larger delivery framework that defines the lifecycle, gates, platform governance, adoption path, and contribution model that sub-capabilities plug into.

## Roadmap

- Replace placeholder internal guideline links with approved collaboration pages.
- Expand Discovery and Maintenance as first-class runtime capabilities through separate SDD slices.
- Add more framework templates for team onboarding, gate evidence, and agent module contribution.
- Add richer validation automation for docs, diagrams, SDD traceability, and sample packages.
- Add redacted screenshots or promotional imagery only after explicit review and approval.

## Documentation

- [Framework documentation index](docs/atlas-engineering-delivery-hub-index.md)
- [Open collaboration submission](docs/open-collaboration-submission.md)
- [Chinese submission](docs/open-collaboration-submission.zh-CN.md)
- [Framework pitch](docs/atlas-engineering-delivery-hub-pitch.md)
- [Contribution guide](CONTRIBUTING.md)
- [Synthetic adoption sample](docs/samples/atlas-framework-adoption-sample.md)
- [SDD traceability](docs/00-context/atlas-engineering-delivery-hub-traceability.md)
- [Current implementation baseline](docs/wwa-agent-workspace-hub-current-baseline.md)

## Verification

Recommended checks for this package:

```bash
git diff --check
node scripts/check-markdown-links.mjs
```

Backend and frontend validation remain available for runtime changes:

```bash
mvn test
cd frontend && npm run build
```
