# Agent Contribute Dashboard User Stories

**Date:** 2026-05-26
**Status:** Backfilled / Implemented

---

## Overview

Agent Contribute Dashboard gives WWA users a concise view of how agents map to the seven Qilianshan SDLC stages, who owns each contribution area, what is already implemented, and where collaboration guidance lives.

The feature is a dashboard only. It does not register agents, create workflow tasks, or calculate performance scores.

---

## Story ACD-01: View SDLC Agent Coverage

**As a** WWA team member,
**I want** to see the seven Qilianshan SDLC stages in one dashboard,
**so that** I can understand where agents currently contribute to the delivery lifecycle.

### Acceptance Criteria

1. Given I open Agent Contribute Dashboard, when the page loads, then I see Planning, Estimation, Discovery, Build, Testing, Deployment, and Maintenance.
2. Given the dashboard loads, when I view the summary area, then I can distinguish Implemented, In Progress, Backlog, and Not Implemented stage counts.
3. Given I view the SDLC coverage map, when I scan the stage cards, then each card shows stage name, implementation status, agent owner, and contribution item count.

---

## Story ACD-02: Understand Ownership and Contribution

**As a** new joiner or management viewer,
**I want** to see ownership and contribution details for a selected stage,
**so that** I can understand who is accountable and who co-builds each contribution item.

### Acceptance Criteria

1. Given I select a stage, when the detail panel opens, then I see stage description, implementation status, accountability, and contribution coverage.
2. Given I view accountability, when I inspect the detail panel, then I see Agent Owner, Process Owners, Technical Leaders, Co-Build Partners, and I-E-O-V Gate.
3. Given I view contribution coverage, when I inspect each item, then I see Sub-agent Owner, Process Owner, Technical Leader, Co-Build Partners, and contribution description.

---

## Story ACD-03: Maintain Stage Status as Admin

**As a** DEVOPS_ADMIN,
**I want** to update the implementation status for each SDLC stage,
**so that** the dashboard reflects the current platform baseline.

### Acceptance Criteria

1. Given I am a DEVOPS_ADMIN, when I select a stage, then I can choose a status from Implemented, In Progress, Backlog, and Not Implemented.
2. Given I change a stage status, when I save it, then the status is persisted through the platform API.
3. Given I am not a DEVOPS_ADMIN, when I view the dashboard, then I can read status but cannot save status updates.

---

## Story ACD-04: Open Guidelines and Feedback

**As a** WWA user,
**I want** clickable Confluence links inside the selected stage panel,
**so that** I can read stage guidance or write feedback without searching separately.

### Acceptance Criteria

1. Given I select a stage, when I view the detail panel, then I see Confluence links for Guideline and Feedback.
2. Given I click a Confluence link, when the browser opens the link, then the target opens in a new tab and does not replace the dashboard.
3. Given a stage has configured links, when the dashboard renders, then each link shows a short label and description.

---

## Story ACD-05: Keep Dashboard Concise and Non-Scoring

**As a** product owner,
**I want** the dashboard to remain concise and avoid unclear scores,
**so that** it supports accountability without creating misleading performance judgments.

### Acceptance Criteria

1. Given I view the dashboard, when I scan the page, then I do not see scores, ranking, or contributor leaderboards.
2. Given I view the dashboard, when I inspect UI text, then UI copy is English-only.
3. Given the dashboard displays SDLC stage structure, when I inspect terminology, then it does not use nested mountain terminology.
