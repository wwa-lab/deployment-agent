# Competition submission: Atlas Engineering Delivery Hub

[简体中文](open-collaboration-submission.zh-CN.md) · [English README](../README.en.md) · [Full narrative](atlas-engineering-delivery-hub-pitch.md)

| Field | Content |
|---|---|
| Project | Atlas Engineering Delivery Hub |
| Existing category | Framework |
| Positioning | A concrete Agentic SDLC practice platform |
| Current practice setting | IBM iSeries |
| Applicant name | |
| Staff ID | |
| Contact | |

## Situation

Teams already have BAU activities, operational knowledge, Jenkins pipelines and Ansible scripts. They need clear boundaries to make these assets reusable and orchestratable. Current IBM iSeries operations/release practice connects deployment, health checking, result validation and human confirmation. Costs and business benefits have not yet been quantified.

## Solution

Atlas Engineering Delivery Hub organizes specification collaboration, multiple Agent workspaces, execution tools and human governance in one platform. Its method is not limited to a single delivery language. The presentation introduces the platform first, then develops Deployment Agent through three steps:

1. **Atomization:** BAU tasks → standardized SOPs → atomic tasks with inputs, actions, expected outputs and validation requirements.
2. **Automation:** reuse existing pipelines, scripts and checking tools while retaining manual work, results and approvals. Jenkins/AWX adapters exist; specific IBM iSeries Health Check UTL invocation and validation evidence must still be supplied.
3. **Intelligence:** progressively use specifications, atomic tasks and execution history for orchestration recommendations, exception explanation and assisted decisions. This remains an evolution direction.

Atomization defines the work, automation produces reusable execution evidence, and intelligence can then assist within explicit constraints. These are capability-development steps, with human review and audit throughout.

## Result

The project owner confirms current IBM iSeries practice. The repository contains multiple workspaces, atomic tasks, MANUAL/AUTO, human decisions, execution history and shared governance. Original synthetic examples and the previous revision's 84 passing selected tests supply bounded inspectable evidence; see the [case index](samples/README.md).

Actual iSeries run packages, UTL interfaces and health-check results, repeated delivery records and cross-platform cases remain to be added. AI Assist is a preview, not operational intelligent orchestration. No measured savings are claimed. Next measurements cover task-contract completeness, automated execution and human fallback, repeated outcomes and human effort.

## Generality and scope

Reusable elements are task contracts, workspaces, executor interfaces, configuration, human review and evidence mechanisms. Java/Vue is the platform implementation stack, not a restriction on the delivery languages it coordinates. Other platforms require SOP, executor and validation adaptation; arbitrary-platform compatibility is not established.

The platform serves delivery teams and platform maintainers. Deployment serves release coordinators, operators and reviewers, taking task lists, stage, scope and execution configuration and returning states, results, attempts and decisions. This is a platform introduction followed by a module deep dive; shared evidence is not counted twice. Other competition projects were not inspected.

AUTO submission is not completion and polling defaults to disabled. Owners/admins make current decisions; enforced two-person approval and automatic infrastructure rollback are not claimed.

[Platform value SVG](assets/atlas-delivery-value-v2.svg) · [Collaboration SVG](assets/atlas-delivery-workflow-v2.svg) · [Chinese offline deck v2](atlas-engineering-delivery-hub-presentation-v2.html) · [Contribution guide](../CONTRIBUTING.md)
