# Atlas Engineering Delivery Hub

**Category:** Framework
**Primary story:** Team delivery framework
**Companion function:** [Atlas Engineering Delivery Hub - Deployment](docs/atlas-engineering-delivery-hub-deployment-index.md)
**Chinese README:** [README.zh-CN.md](README.zh-CN.md)

Atlas Engineering Delivery Hub is a team framework for making software delivery visible, governed, traceable, and reusable across the full engineering lifecycle. It organizes delivery work around the Seven Mountains SDLC and a repeatable I-E-O-V gate model: Input, Execute, Output, Validate.

This repository is the current Atlas Engineering Delivery Hub implementation baseline that demonstrates the framework in working software. The visible product brand is **WWA-Atlas Engineering Delivery Hub** / **WWA-Atlas Hub**, preserving the WWA name that early adopters already recognize while connecting it to the Atlas framework. Some technical identifiers still use `WWA` or `deployment-agent` for compatibility. Deployment Agent is one implemented agent inside the Hub, not the whole story. For the open-collaboration competition, the repository supports two related entries:

| Entry | Category | What it shows | Where to start |
|---|---|---|---|
| Atlas Engineering Delivery Hub | Framework | A reusable team operating model for SDLC stages, shared workflow surfaces, human-in-the-loop governance, evidence, auditability, and contribution patterns. | This README, [framework docs index](docs/atlas-engineering-delivery-hub-index.md), [framework submission](docs/open-collaboration-submission.md) |
| Atlas Engineering Delivery Hub - Deployment | Tool / Function | Deployment Agent as the M6 Deployment capability: controlled SIT / UAT / PROD release operations, including the design direction for IBM iSeries one-click release UTL workflows. | [Deployment docs index](docs/atlas-engineering-delivery-hub-deployment-index.md), [Deployment submission](docs/open-collaboration-submission-deployment.md) |

![Atlas Engineering Delivery Hub framework lifecycle](docs/assets/atlas-framework-lifecycle.svg)

## Framework Positioning

The Hub is designed as a team framework rather than a single automation script or one deployment page. It gives teams a common way to describe each delivery stage, collect the right evidence, run controlled work, validate outcomes, and preserve decisions for later review.

The current implementation demonstrates this through a Spring Boot backend, Vue 3 frontend, shared platform shell, agent workspaces, access governance, audit logs, configuration management, Excel-based onboarding, and human review gates. Some lifecycle functions are implemented deeply, some are represented as framework direction, and each function can become its own project entry when it has enough working value.

## Seven Mountains SDLC

```text
M1 Planning -> M2 Estimation -> M3 Discovery -> M4 Build -> M5 Testing -> M6 Deployment -> M7 Maintenance
```

| Stage | Team framework purpose | Current repository signal |
|---|---|---|
| M1 Planning | Align goals, scope, stakeholders, and approval readiness. | Agent Contribute Dashboard and framework docs. |
| M2 Estimation | Capture effort, schedule, risk, and resource expectations. | Agent Contribute Dashboard and framework docs. |
| M3 Discovery | Convert business intent into requirements, specs, and design evidence. | Framework target stage; Atlas Phoenix Lens / Legacy Spec Factory can plug in as an upstream example. |
| M4 Build | Turn approved design into code, tasks, verification notes, and artifacts. | Build Agent workspace and shared task workflow baseline. |
| M5 Testing | Produce validation evidence before release. | Testing Agent workspace direction and shared workflow baseline. |
| M6 Deployment | Coordinate release execution, approvals, rollback posture, and auditability. | Implemented Deployment function in this repo. |
| M7 Maintenance | Feed incidents, production learning, and improvement work back into delivery. | Framework target stage and roadmap area. |

## Seven Gates: I-E-O-V

Each lifecycle stage follows the same operating shape:

| Gate element | Meaning |
|---|---|
| Input | Required intake artifacts, owners, scope, constraints, and preconditions. |
| Execute | Controlled work performed by humans, agents, automations, or external tools. |
| Output | Durable artifacts, decisions, run records, and traceable results. |
| Validate | Review checks, test evidence, approvals, audit records, and acceptance decisions. |

This lets teams reuse one governance language across planning, build, testing, deployment, and maintenance, while still allowing each function to have its own workflow and tooling.

![Seven Gates I-E-O-V](docs/assets/seven-gates-ieov.svg)

## Framework Capabilities

Implemented and documented capability areas include:

- Shared workspace shell for multiple delivery functions.
- Agent/function workspaces for Build Agent, Testing Agent, and Deployment Agent workflows.
- Human-in-the-loop task progression and review decisions.
- Release Flow -> Request -> Task traceability model.
- Scoped access governance with local Access Grants.
- Audit logs with user, action, scope, agent, and correlation context.
- Configuration management for reusable execution targets and team-owned settings.
- Template and Excel-based onboarding for repeatable workflow setup.
- SDD artifact chains that make requirements, stories, specs, architecture, design, and tasks explicit for human and AI collaborators.

## Deployment Function

Deployment Agent is the most concrete agent currently packaged from this repository. It sits at M6 Deployment and converts validated build/testing outputs into controlled SIT / UAT / PROD release work.

Deployment Agent includes:

- Excel-based deployment request onboarding.
- Stage-aware release flow tracking for `SIT`, `UAT`, and `PROD`.
- Manual task execution and AUTO submission paths.
- Jenkins and Ansible/AWX execution adapters.
- Human review decisions: approve, reject, rerun, skip.
- Execution history, external job/log links, and audit records.
- Access governance and release safety controls.

For the second competition project, this agent can be presented independently as **Atlas Engineering Delivery Hub - Deployment**. Its differentiator is the one-click release UTL direction for IBM iSeries: the framework provides the controlled release shell, while Deployment Agent captures the task model, evidence, review gates, and adapter design needed to package iSeries release activity into a repeatable one-click operating flow.

![Deployment tool workflow](docs/assets/atlas-deployment-tool-workflow.svg)

## Current Scope And Boundaries

This package does claim:

- A working implementation baseline for shared delivery workflows.
- A framework narrative that can host multiple SDLC functions.
- A fully documented Deployment function slice with SDD traceability and sample outputs.
- Bilingual reviewer entry points for the framework and Deployment function.

Compatibility note:

- Visible product brand: WWA-Atlas Engineering Delivery Hub / WWA-Atlas Hub.
- Agent name: Deployment Agent.
- Compatibility identifiers retained for now: `/wwa/*`, `/wwa/deployment-agent`, `/api/deployment-agent`, Maven `artifactId=agenthub`, and Java package `com.wwa.agenthub`.

This package does not claim:

- Full autonomous delivery approval.
- Complete production rollout for every Seven Mountains stage.
- Real enterprise Team Book production integration in this open package.
- One-click infrastructure rollback.
- Real credentials, customer data, kubeconfigs, internal screenshots, or production environment names.

## Quick Start

Backend:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Open:

```text
http://localhost:5173/wwa/deployment-agent
```

Local/test stub users are documented in [the preserved implementation baseline](docs/wwa-agent-workspace-hub-current-baseline.md). In local mode, any non-empty password works for the configured stub authentication provider.

## Documentation Map

Framework entry:

- [Framework documentation index](docs/atlas-engineering-delivery-hub-index.md)
- [Framework open collaboration submission](docs/open-collaboration-submission.md)
- [Chinese framework submission](docs/open-collaboration-submission.zh-CN.md)
- [Framework pitch](docs/atlas-engineering-delivery-hub-pitch.md)
- [Framework adoption sample](docs/samples/atlas-framework-adoption-sample.md)
- [Framework SDD traceability](docs/00-context/atlas-engineering-delivery-hub-traceability.md)

Deployment function entry:

- [Deployment documentation index](docs/atlas-engineering-delivery-hub-deployment-index.md)
- [Deployment open collaboration submission](docs/open-collaboration-submission-deployment.md)
- [Chinese Deployment submission](docs/open-collaboration-submission-deployment.zh-CN.md)
- [Deployment pitch](docs/atlas-engineering-delivery-hub-deployment-pitch.md)
- [Deployment sample package](docs/samples/atlas-deployment-tool-mini-output/README.md)
- [Deployment SDD traceability](docs/00-context/atlas-engineering-delivery-hub-deployment-traceability.md)

Runtime and contribution references:

- [Contribution guide](CONTRIBUTING.md)
- [Current implementation baseline](docs/wwa-agent-workspace-hub-current-baseline.md)
- [Platform and Deployment architecture](docs/04-architecture/architecture.md)
- [Detailed design baseline](docs/05-design/design.md)
- [SDD profile](docs/00-context/sdd-profile.md)

## Roadmap

- Clarify function-level project packaging for Discovery, Build, Testing, Deployment, and Maintenance.
- Expand the IBM iSeries one-click release UTL design under the Deployment function without exposing real environment details.
- Add clearer upstream evidence contracts between Build, Testing, and Deployment.
- Strengthen reusable templates for stage gates, task manifests, evidence capture, and review decisions.
- Continue improving SDD, Markdown, Mermaid, and documentation validation automation.

## Verification

Recommended documentation/package checks:

```bash
git diff --check
node scripts/check-markdown-links.mjs
```

Runtime checks for code changes:

```bash
mvn test
cd frontend && npm run build
```
