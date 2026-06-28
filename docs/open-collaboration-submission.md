# Open Collaboration Submission: Atlas Engineering Delivery Hub

## Summary

Atlas Engineering Delivery Hub is a Framework category entry for team delivery governance. It organizes engineering work through Seven Mountains SDLC and gives each stage a repeatable I-E-O-V operating model: Input, Execute, Output, Validate.

The current repository demonstrates the framework through the WWA Agent Workspace Hub implementation baseline: Spring Boot backend, Vue 3 frontend, shared workspace shell, agent/function workspaces, scoped access governance, configuration management, audit logs, SDD traceability, and human-in-the-loop workflow controls.

## Category

**Framework**

This project qualifies as a Framework because it is not only a single tool or deployment script. It defines a reusable way for teams to structure delivery stages, evidence, workflow execution, validation, and contribution across multiple SDLC functions.

## Two Competition Projects

| Project | Category | Relationship |
|---|---|---|
| Atlas Engineering Delivery Hub | Framework | The parent team framework and primary repository positioning. |
| Atlas Engineering Delivery Hub - Deployment | Tool / Function | One function inside the Hub, packaged as a separate entry for M6 Deployment and IBM iSeries one-click release UTL design direction. |

Deployment is intentionally presented as a function of the Hub. It is not the whole Hub.

## Problem Solved

Delivery work often becomes fragmented across planning notes, spreadsheets, build jobs, test reports, release runbooks, approvals, and production feedback. Teams lose clarity about:

- which input evidence is required before work starts;
- who owns each stage and decision;
- what was executed by humans, agents, or external tools;
- which output records are durable;
- how validation and approval happened;
- how later teams can trace the decision chain.

Atlas Engineering Delivery Hub gives teams a shared framework for those handoffs.

## Framework Model

Seven Mountains SDLC:

```text
M1 Planning -> M2 Estimation -> M3 Discovery -> M4 Build -> M5 Testing -> M6 Deployment -> M7 Maintenance
```

Every stage follows Seven Gates / I-E-O-V:

| Element | Meaning |
|---|---|
| Input | Required artifacts, scope, owners, constraints, and preconditions. |
| Execute | Controlled work by people, agents, automations, or external tools. |
| Output | Durable artifacts, decisions, run records, and traceable results. |
| Validate | Review checks, evidence gates, approvals, and audit records. |

## What The Repository Provides Today

- A working shared workspace shell for delivery functions.
- Agent/function workspace patterns for Build, Testing, and Deployment style workflows.
- Release Flow -> Request -> Task traceability.
- Human review gates for task decisions.
- Scoped access governance through local Access Grants.
- Audit logs with user, action, scope, agent, and correlation context.
- Configuration management for reusable execution targets.
- Template and Excel-based onboarding patterns.
- SDD documents that connect requirements, user stories, specs, architecture, design, and tasks.
- Framework docs, diagrams, contribution guidance, and synthetic samples for open collaboration.

## Deployment As An Independent Function

The Deployment function is the most concrete function currently packaged from this repository. It covers controlled SIT / UAT / PROD release operations with manual and AUTO task execution, Jenkins/Ansible adapters, review decisions, execution history, and auditability.

As a second competition project, Deployment can stand alone because it contains a complete function-level story and design direction for IBM iSeries one-click release UTL workflows. The Hub provides the team framework; Deployment demonstrates how one function becomes a reusable operating tool.

See [Deployment submission](open-collaboration-submission-deployment.md) for the function-level package.

## Reusable Value Across Teams

Teams can reuse:

- the lifecycle and gate vocabulary;
- the SDD document chain;
- the workflow shell and task progression pattern;
- the evidence and audit model;
- the contribution and validation rules;
- the function packaging pattern used by Deployment.

The framework is intentionally adaptable: different teams may plug in different Discovery, Build, Testing, Deployment, or Maintenance functions without rewriting the top-level operating model.

## Fit With The Open Collaboration Theme

The Hub is built for co-building:

- Each function can evolve independently while staying aligned to the same stage/gate model.
- Docs and SDD artifacts make scope clear for humans and AI agents.
- Synthetic samples allow safe sharing without exposing customer or production data.
- Contribution rules protect secrets, credentials, approvals, and audit posture.
- The Deployment function gives contributors a concrete reference implementation.

## Delivered Materials

- [English README](../README.md)
- [Chinese README](../README.zh-CN.md)
- [Framework documentation index](atlas-engineering-delivery-hub-index.md)
- [Framework pitch](atlas-engineering-delivery-hub-pitch.md)
- [Contribution guide](../CONTRIBUTING.md)
- [Framework lifecycle diagram](assets/atlas-framework-lifecycle.svg)
- [Seven Mountains SDLC diagram](assets/seven-mountains-sdlc.svg)
- [Seven Gates I-E-O-V diagram](assets/seven-gates-ieov.svg)
- [Synthetic framework adoption sample](samples/atlas-framework-adoption-sample.md)
- [Framework SDD traceability](00-context/atlas-engineering-delivery-hub-traceability.md)
- [Deployment function package](atlas-engineering-delivery-hub-deployment-index.md)

## Demo Story

1. Start from the Seven Mountains SDLC map.
2. Explain I-E-O-V gates as the common team operating contract.
3. Show the framework docs and SDD traceability chain.
4. Open the working WWA Agent Workspace Hub implementation.
5. Use Deployment as the concrete function example.
6. Upload or inspect a release rundown, task flow, decision gate, and audit trail.
7. Explain how another function can reuse the same framework structure.

## Contribution Opportunities

- Add new function-level packaging for Discovery, Build, Testing, or Maintenance.
- Improve framework adoption samples.
- Add sanitized stage gate templates and evidence examples.
- Strengthen Markdown, Mermaid, and SDD validation scripts.
- Expand the IBM iSeries one-click release UTL design under the Deployment function.
- Improve frontend clarity for operators, reviewers, and contributors.

## Safety Boundaries

- Secrets and credentials must stay out of docs, samples, screenshots, and committed workbooks.
- Real environment names and customer data must not be committed.
- Approval and rollback claims must match implemented behavior.
- New user-facing or non-trivial changes must update the relevant SDD artifacts.

## Links

- [README](../README.md)
- [Chinese README](../README.zh-CN.md)
- [Framework docs index](atlas-engineering-delivery-hub-index.md)
- [Framework pitch](atlas-engineering-delivery-hub-pitch.md)
- [Deployment docs index](atlas-engineering-delivery-hub-deployment-index.md)
- [Contribution guide](../CONTRIBUTING.md)
- [Current implementation baseline](wwa-agent-workspace-hub-current-baseline.md)
