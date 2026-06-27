# Synthetic Adoption Sample: Atlas Engineering Delivery Hub

**Sample status:** Synthetic, safe to share
**Team:** Example Payments Modernization Team
**Project:** Example Account Notification Upgrade

This sample shows how a team might adopt Atlas Engineering Delivery Hub without using customer data or internal production details.

## 1. Project Context

The Example Payments Modernization Team wants to deliver a notification upgrade that affects one application, one test environment, and one production rollout window.

Synthetic scope:

- Application: `ExamplePay`
- Owning group: `Example-SNOW-Group`
- Business owner: `Example Product Owner`
- Technical lead: `Example Tech Lead`
- Release owner: `Example Release Owner`

## 2. Seven Mountains Map

| Mountain | Owner | Example evidence |
|---|---|---|
| Planning | Product Owner | Objective, scope, approval note, out-of-scope list. |
| Estimation | Delivery Manager | Timeline, resource plan, risk baseline. |
| Discovery | Business Analyst | Requirement notes, design outline, acceptance criteria. |
| Build | Build Engineering Team | Code change, unit evidence, build artifact. |
| Testing | QA Lead | UAT test run, defect disposition, acceptance result. |
| Deployment | Release Owner | SIT/UAT/PROD rollout tasks, approval, rollback note. |
| Maintenance | Operations Owner | Monitoring note, incident route, improvement backlog. |

## 3. I-E-O-V Gate Template

| Gate element | Example content |
|---|---|
| Input | Approved scope, owner list, risk baseline, required documents. |
| Execute | Agent workspace tasks, human actions, automation jobs, review comments. |
| Output | Updated docs, build artifacts, validation evidence, release-flow records. |
| Validate | Reviewer decision, test result, audit record, go/no-go note. |

## 4. Sub-Capability Plug-In Example

Atlas Phoenix Lens can be used as a Discovery-stage sub-capability:

- **Input:** raw business notes, draft requirements, known constraints.
- **Execute:** analyze ambiguity, map requirement gaps, suggest story/spec structure.
- **Output:** requirement candidate set, open questions, draft traceability.
- **Validate:** business analyst and technical lead review before Build starts.

This keeps Atlas Phoenix Lens inside the larger Atlas Engineering Delivery Hub framework instead of making it a competing parent process.

## 5. Adoption Checklist

- [ ] Map team workflow to the seven mountains.
- [ ] Define I-E-O-V for every stage gate.
- [ ] Identify current workspaces to use: Build, Testing, Deployment, or docs-only templates.
- [ ] Create or update SDD artifacts before non-trivial changes.
- [ ] Use synthetic sample data in shared docs.
- [ ] Run link and whitespace checks before submitting package updates.

## 6. Minimal Team Package

An adopting team can start with:

1. A copied stage map.
2. One I-E-O-V table per stage.
3. One owner matrix.
4. One validation checklist.
5. Links to current WWA workspaces or future sub-capabilities.

