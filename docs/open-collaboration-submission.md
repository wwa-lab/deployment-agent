# Open Collaboration Submission: Atlas Engineering Delivery Hub - Deployment

## Summary

Atlas Engineering Delivery Hub - Deployment is a Tool category entry for the M6 Deployment stage of the Atlas Engineering Delivery Hub / Seven Mountains SDLC narrative. It helps teams convert validated build and testing outputs into controlled, traceable release operations across SIT, UAT, and PROD.

The current repository implements a Spring Boot + Vue release workspace with Excel onboarding, release-flow tracking, task-level manual/AUTO execution, human review decisions, access governance, audit logs, and configuration-driven Jenkins/Ansible execution adapters.

## Category

**Tool**

This project qualifies as a Tool because it provides a concrete release operations workspace and API surface for one lifecycle stage: M6 Deployment. The larger Atlas Engineering Delivery Hub is the umbrella story; this repository is the deployment-stage tool inside that story.

## Problem Solved

Release teams often receive build and testing evidence, but the last-mile release operation still becomes fragmented:

- deployment tasks are tracked in spreadsheets, chats, and external job consoles;
- manual steps and AUTO jobs are hard to review side by side;
- approvals are not consistently tied to the exact task result that was reviewed;
- rollback or rerun decisions lose context;
- environment-specific credentials and endpoints are sometimes copied into unsafe places;
- audit trails are assembled after the fact.

Atlas Engineering Delivery Hub - Deployment gives teams a controlled place to package, run, review, and trace release work without pretending the process is fully autonomous.

## What The Tool Does Today

- Imports deployment rundowns from a fixed Excel template.
- Requires upload-time stage selection for `SIT`, `UAT`, or `PROD`.
- Creates or updates Release Flow, Request, and Task records.
- Tracks repeated attempts and linked stage rollouts through workflow identifiers.
- Supports manual task execution and result recording.
- Supports AUTO task submission to Jenkins or Ansible/AWX through adapters.
- Preserves task execution history, external job links, and result summaries.
- Requires human review decisions before downstream progression.
- Provides rundown controls such as start, fail, archive, restore, and purge.
- Enforces deny-by-default access through local Access Grants.
- Records audit logs for workflow, access, and configuration activity.

## What Is Not Claimed

- It is not a fully autonomous deployment decision system.
- It is not the entire Atlas Engineering Delivery Hub framework.
- It does not include real production credentials, kubeconfigs, customer data, or internal screenshots.
- It does not currently provide one-click infrastructure rollback.
- Maintenance-stage incident routing and post-release automation are planned/TBD.

## Reusable Value Across Teams

The reusable pattern is not a single hardcoded deployment script. It is a release operations shell:

- **Input contract:** stage, release identifier, scope, task list, owners, expected outputs, and external execution metadata.
- **Execution contract:** manual or AUTO task execution with explicit owner/admin controls.
- **Output contract:** task results, external links, decisions, status, audit rows, and release-flow state.
- **Validation contract:** human review, status recomputation, access checks, and traceability.

Teams can reuse the workspace model even when their actual Jenkins jobs, Ansible templates, task names, or release evidence differ.

## Fit With The Open Collaboration Theme

The tool is built for co-building:

- Deployment adapters can be improved independently.
- Sample release templates can be added without exposing internal data.
- Docs and diagrams explain how teams can adopt the M6 stage contract.
- SDD artifacts make scope, decisions, and validation explicit for humans and AI agents.
- Safety rules protect secrets, approvals, audit history, and rollback posture.

## Relationship To Atlas Engineering Delivery Hub

Atlas Engineering Delivery Hub is the umbrella framework. Seven Mountains SDLC is the lifecycle model:

```text
M1 Planning -> M2 Estimation -> M3 Discovery -> M4 Build -> M5 Testing -> M6 Deployment -> M7 Maintenance
```

This repository is **M6 Deployment**.

Atlas Phoenix Lens / Legacy Spec Factory is useful context as an M3 Discovery capability. Build Agent and Testing capability provide upstream M4/M5 context. This project focuses on the M6 deployment tool and its controlled release workflow.

## Delivered Materials

- [English README](../README.md)
- [Chinese README](../README.zh-CN.md)
- [Deployment documentation index](atlas-engineering-delivery-hub-deployment-index.md)
- [Deployment pitch](atlas-engineering-delivery-hub-deployment-pitch.md)
- [Contribution guide](../CONTRIBUTING.md)
- [M6 lifecycle positioning diagram](assets/atlas-deployment-lifecycle-positioning.svg)
- [Deployment workflow diagram](assets/atlas-deployment-tool-workflow.svg)
- [Upstream/downstream relationship diagram](assets/atlas-deployment-upstream-downstream.svg)
- [Sanitized mini output sample](samples/atlas-deployment-tool-mini-output/README.md)
- [M6 SDD traceability](00-context/atlas-engineering-delivery-hub-deployment-traceability.md)

## Demo Story

1. Start from a validated candidate package and testing evidence.
2. Upload a sanitized deployment task workbook for `SIT`.
3. Show the created release flow and first runnable task.
4. Run a manual task or submit an AUTO task.
5. Record or inspect the result.
6. Approve, reject, rerun, or skip through the human review gate.
7. Reuse the workflow identifier for `UAT` and `PROD`.
8. Show audit and execution history as the traceable release record.
9. Explain how failed or rollback-needed work remains visible through state and history.

## Contribution Opportunities

- Add sanitized release template examples.
- Improve Jenkins and Ansible adapter tests.
- Add clearer upstream evidence handoff examples from Build and Testing.
- Expand docs for rollback handoff and post-release learning.
- Strengthen Markdown, Mermaid, and SDD validation scripts.
- Improve frontend clarity for release operators and reviewers.

## Safety Boundaries

- Human approval remains required for release progression.
- Secrets and credentials must stay out of docs, samples, screenshots, and committed workbooks.
- Real environment names and customer data must not be committed.
- Adapter contributions must use configuration and secret-management paths.
- Rollback claims must match implemented behavior.

## Links

- [README](../README.md)
- [Chinese README](../README.zh-CN.md)
- [Deployment docs index](atlas-engineering-delivery-hub-deployment-index.md)
- [Deployment pitch](atlas-engineering-delivery-hub-deployment-pitch.md)
- [Contribution guide](../CONTRIBUTING.md)
- [Current implementation baseline](wwa-agent-workspace-hub-current-baseline.md)
