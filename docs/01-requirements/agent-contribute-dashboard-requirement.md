# Agent Contribute Dashboard Requirement

**Date:** 2026-05-26
**Status:** Backfilled / Implemented
**Owner:** WWA Agent Workspace Hub direction

---

## 1. Background

WWA is moving toward a multi-agent operating model where each agent contributes to a defined part of the internal SDLC. The team needs a dashboard that makes this contribution model visible across the seven Qilianshan SDLC stages.

This requirement captures the Agent Contribute Dashboard MVP. The feature was initially implemented through iterative product feedback and is now documented as a strict SDD artifact baseline so future changes can proceed documents-first.

The dashboard is not an agent registration workflow. It is a visibility and accountability dashboard that describes existing or planned agent contribution coverage.

---

## 2. Product Objectives

The dashboard must help WWA teams:

1. Identify the owner for each agent and stage contribution area.
2. Encourage agent co-build culture across teams.
3. Help new joiners understand where each agent fits in the SDLC.
4. Give management a lightweight factual basis for ownership and contribution review.
5. Show current implementation status without introducing opaque scores.

---

## 3. Scope

### 3.1 In Scope

- Display the seven Qilianshan SDLC stages: Planning, Estimation, Discovery, Build, Testing, Deployment, and Maintenance.
- Display the current implementation status for each stage: Implemented, In Progress, Backlog, or Not Implemented.
- Default Discovery and Maintenance to Not Implemented.
- Default Testing to In Progress.
- Default Planning, Estimation, Build, and Deployment to Implemented.
- Allow `DEVOPS_ADMIN` users to update stage implementation status through persisted platform configuration.
- Show each contribution item with Agent Owner, Sub-agent Owner, Process Owner, Technical Leader, Co-Build partners, and contribution description.
- Provide stage-level Confluence links for Guideline and Feedback.
- Use English-only UI copy.
- Keep the page concise and dashboard-like.

### 3.2 Out of Scope

- Agent registration or onboarding submission workflow.
- Score, rating, leaderboard, or quantified performance scoring.
- Per-person performance calculation.
- Automatic Confluence page creation.
- Workflow execution, task assignment, or approval routing.
- New database tables for dashboard baseline data.
- Nested mountain terminology or representation.

---

## 4. Users and Roles

| Role | Need |
|---|---|
| Developer / Team Member | Understand who owns each SDLC stage and which agents contribute. |
| New Joiner | Learn the Qilianshan SDLC agent coverage model quickly. |
| Agent Owner | See and explain accountable contribution areas. |
| Sub-agent Owner | See owned contribution items inside a stage. |
| Process Owner | Confirm process ownership and stage gates. |
| Technical Leader | Confirm technical accountability for the contribution item. |
| Co-Build Partner | Understand where collaboration is expected. |
| DEVOPS_ADMIN | Maintain stage implementation status. |
| Management Viewer | Review ownership and contribution coverage without opaque scores. |

---

## 5. Functional Requirements

- **ACD-REQ-01**: The system shall expose Agent Contribute Dashboard under WWA shared controls.
- **ACD-REQ-02**: The system shall display all seven Qilianshan SDLC stages in one dashboard view.
- **ACD-REQ-03**: The system shall distinguish each stage by implementation status using Implemented, In Progress, Backlog, and Not Implemented.
- **ACD-REQ-04**: The system shall allow only `DEVOPS_ADMIN` users to update stage implementation status.
- **ACD-REQ-05**: The system shall persist admin status changes through platform configuration rather than frontend-only state.
- **ACD-REQ-06**: The system shall show each contribution item with Agent Owner, Sub-agent Owner, Process Owner, Technical Leader, Co-Build partners, and contribution description.
- **ACD-REQ-07**: The system shall show selected stage details, including description, status, accountability, contribution coverage, and stage gate.
- **ACD-REQ-08**: The system shall show clickable stage-level Confluence links for guideline reading and feedback submission.
- **ACD-REQ-09**: The system shall use English-only UI copy for this dashboard.
- **ACD-REQ-10**: The system shall not display contribution scores, ratings, or unexplained quantitative judgments.

---

## 6. Non-Functional Requirements

- **Security:** Status update API must require `DEVOPS_ADMIN`; non-admin users must receive a forbidden response.
- **Validation:** Stage keys and status values must be validated server-side.
- **Maintainability:** Static dashboard baseline data should remain configuration-driven in frontend JSON.
- **Usability:** The dashboard should prioritize scanability over dense narrative explanation.
- **Compatibility:** The feature must run inside the existing WWA shell and follow current frontend routing conventions.
- **Traceability:** Future expansion must update SDD artifacts before implementation.

---

## 7. Acceptance Criteria

1. A user can open `/wwa/agent-contribute-dashboard` from the WWA shared controls.
2. The dashboard displays exactly seven SDLC stages.
3. Discovery and Maintenance are shown as Not Implemented by default.
4. Testing is shown as In Progress by default.
5. Admin users can update a selected stage status and see the persisted result.
6. Non-admin users cannot update stage status.
7. The UI displays the internal role model for each contribution item.
8. The UI includes Confluence Guideline and Feedback links for each selected stage.
9. The UI does not include scores, contributor leaderboards, Chinese copy, or nested mountain terminology.
10. Frontend build and dashboard tests pass.

---

## 8. Open Questions

Open questions are tracked as pending tasks in `docs/06-tasks/agent-contribute-dashboard-tasks.md`. Do not maintain a separate list here.
