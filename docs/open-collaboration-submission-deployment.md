# Open Collaboration Submission: Atlas Engineering Delivery Hub - Deployment

## Summary

Atlas Engineering Delivery Hub - Deployment is a Tool / Function category entry for the M6 Deployment stage of the Atlas Engineering Delivery Hub. It turns validated build and testing outputs into controlled, traceable release operations across SIT, UAT, and PROD.

The current repository implements the function through a Spring Boot + Vue release workspace with Excel onboarding, release-flow tracking, task-level manual/AUTO execution, human review decisions, access governance, audit logs, and configuration-driven Jenkins/Ansible execution adapters.

## Category

**Tool / Function**

This project is separate from the parent Framework entry. Atlas Engineering Delivery Hub is the team framework; Deployment is one function inside it with enough implemented value and design depth to be presented independently.

## Problem Solved

Release teams often receive build and testing evidence, but the last-mile release operation remains fragmented:

- deployment tasks live in spreadsheets, chats, and external job consoles;
- manual steps and AUTO jobs are hard to review side by side;
- approvals are not consistently tied to the exact task result that was reviewed;
- rerun, reject, rollback, and handoff decisions lose context;
- credentials and environment endpoints can drift into unsafe places;
- audit trails are assembled after the fact.

Atlas Engineering Delivery Hub - Deployment gives release teams a controlled place to package, run, review, and trace release work without pretending the process is fully autonomous.

## Function Scope

- Import deployment rundowns from an Excel template.
- Require upload-time stage selection for `SIT`, `UAT`, or `PROD`.
- Create or update Release Flow, Request, and Task records.
- Track repeated attempts and linked stage rollouts through workflow identifiers.
- Support manual task execution and result recording.
- Support AUTO task submission to Jenkins or Ansible/AWX through adapters.
- Preserve task execution history, external job links, and result summaries.
- Require human review decisions before downstream progression.
- Provide rundown controls such as start, fail, archive, restore, and purge.
- Enforce deny-by-default access through local Access Grants.
- Record audit logs for workflow, access, and configuration activity.

## IBM iSeries One-Click Release UTL Direction

The Deployment function is also the place to express the IBM iSeries one-click release UTL design direction. The reusable idea is not a hardcoded script; it is a controlled release shell:

- **Input:** release identifier, stage, scope, iSeries release task list, owners, expected outputs, and external execution metadata.
- **Execute:** manual or AUTO execution steps with explicit owner/admin controls.
- **Output:** task results, external links, decisions, status, execution history, and release-flow state.
- **Validate:** human review, status recomputation, access checks, audit rows, and rollback or rerun handoff notes.

This keeps the iSeries one-click direction safe and reviewable: automation can execute or submit work, while humans still own approval, exception handling, and final release accountability.

## What Is Not Claimed

- It is not a fully autonomous deployment approval system.
- It is not the whole Atlas Engineering Delivery Hub framework.
- It does not include real production credentials, kubeconfigs, customer data, or internal screenshots.
- It does not currently provide one-click infrastructure rollback.
- Maintenance-stage incident routing and post-release automation are planned/TBD.

## Reusable Value Across Teams

The reusable pattern is a release operations shell:

- **Input contract:** stage, release identifier, scope, task list, owners, expected outputs, and external execution metadata.
- **Execution contract:** manual or AUTO task execution with explicit owner/admin controls.
- **Output contract:** task results, external links, decisions, status, audit rows, and release-flow state.
- **Validation contract:** human review, status recomputation, access checks, and traceability.

Teams can reuse the workspace model even when their Jenkins jobs, Ansible templates, iSeries commands, task names, or release evidence differ.

## Delivered Materials

- [Deployment documentation index](atlas-engineering-delivery-hub-deployment-index.md)
- [Deployment pitch](atlas-engineering-delivery-hub-deployment-pitch.md)
- [Framework README](../README.md)
- [Chinese README](../README.zh-CN.md)
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
9. Explain how IBM iSeries one-click release UTL work can fit the same task/evidence/review pattern.

## Contribution Opportunities

- Add sanitized release template examples.
- Expand IBM iSeries one-click release UTL design docs and samples.
- Improve Jenkins and Ansible adapter tests.
- Add clearer upstream evidence handoff examples from Build and Testing.
- Document rollback handoff and post-release learning patterns.
- Strengthen Markdown, Mermaid, and SDD validation scripts.

## Safety Boundaries

- Human approval remains required for release progression.
- Secrets and credentials must stay out of docs, samples, screenshots, and committed workbooks.
- Real environment names and customer data must not be committed.
- Adapter contributions must use configuration and secret-management paths.
- Rollback and one-click claims must match implemented or explicitly documented design behavior.

## Links

- [Framework README](../README.md)
- [Framework submission](open-collaboration-submission.md)
- [Deployment docs index](atlas-engineering-delivery-hub-deployment-index.md)
- [Deployment pitch](atlas-engineering-delivery-hub-deployment-pitch.md)
- [Contribution guide](../CONTRIBUTING.md)
- [Current implementation baseline](wwa-agent-workspace-hub-current-baseline.md)
