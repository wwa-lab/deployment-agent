# Atlas Engineering Delivery Hub Specification

## Current revision — 2026-09-07, platform clarification
**Status:** Authorized docs-only change, prior to document/asset implementation.
**Source stories:** AEDH-US-09–12. This section supersedes conflicting requirements below, including the two-entry split and English canonical language. Older content remains historical design context.

### Scope and acceptance
1. Retain Atlas Engineering Delivery Hub. Position it as a concrete Agentic SDLC platform practice. IBM iSeries is the current practice setting confirmed by the user; the platform method is not limited to one delivery language. Introduce the platform before the Deployment Agent deep dive.
2. Situation → Solution → Result shall lead public materials. Distinguish inferred pain from observed customer feedback (none verified here).
3. Organize the method as atomization → automation → intelligence: BAU tasks become SOPs and atomic task units; existing pipelines/scripts execute those units through adapters; accumulated structured context supports future intelligent orchestration and assisted decisions. These are capability-development steps, not three runtime task states. Structured intake, human review and traceability support the whole progression.
4. State exact limits: AUTO submission is not job completion; polling is disabled by default; manual result recording is not validation of external work; owner/admin decisions do not imply two-person separation.
5. Build and Testing are implemented workflow surfaces, not proof of code-generation or autonomous testing. iSeries one-click release, autonomous approval, automatic rollback and end-to-end lifecycle operations have no verified delivery evidence in this package.
6. README.md shall be simplified Chinese, README.en.md a complete equivalent, README.zh-CN.md a compatibility landing page. Both full READMEs link each other at the top.
7. Existing Deployment indexes/submissions/pitch shall remain reachable and describe a module-level view of the same implementation. Do not claim separate originality or count shared evidence/benefit twice. Do not infer the contents of other entries.
8. Existing samples and historical graphics shall remain unchanged. A case index and template classify synthetic, test execution, and authorized redacted field evidence. Record SHA-256 and source revision. New runs use unique versions.
9. Two new red/white/black SVGs with editable text and PNG exports explain value and the actual branch/review/output flow; distinguish implemented paths from planned/unverified extensions.
10. The HTML presentation shall be self-contained, Chinese-first, keyboard-operable, responsive, with notes and reduced-motion support. It shall work without Python or network access.
11. Public copy uses Agent Skills neutrally. Real CLI/API/file identifiers remain accurate; no directory migration or executor compatibility claim is introduced. No personal submission data.
12. Verification reports shall distinguish document/visual checks, focused automated tests, real external integration, manual business UAT and production readiness. Last three are not implied by the first two.
13. Generality shall be explained through task contracts, configuration and adapter boundaries, not claims of tested support for every language/platform. The reference image mentions Jenkins Pipeline, Ansible scripts and IBM iSeries Health Check UTL; treat UTL-specific wiring and outcomes as unverified until actual interface/run evidence is supplied. Do not describe the current IBM iSeries practice itself as merely hypothetical.

### Boundaries and risks
No Java/Vue runtime, API, schema, auth, executable adapter or integration is changed. No commit/push/merge. Historical baseline technical docs may be stale and must be labelled when linked. Failure paths in diagrams include import rejection, AUTO failure/unknown, reject/rerun and permitted skip. Business return is unmeasured; pilot metrics must define denominators, baseline, human effort and failure cases.

## Historical packaging baseline (superseded where inconsistent)

**Date:** 2026-06-27
**Status:** Proposed / Documents-first
**Source stories:** AEDH-US-01 through AEDH-US-07

## 1. Overview

### 1.1 Feature Summary

Package this repository as **Atlas Engineering Delivery Hub**, an internal open-collaboration Framework entry for end-to-end SDLC delivery governance. The package is a documentation and asset layer over the existing implementation; it does not alter runtime behavior.

### 1.2 Business Objective

The package must help reviewers and adopting teams understand the framework as a reusable operating model for lifecycle visibility, process control, quality validation, traceability, and always-on delivery operations.

### 1.3 In-Scope Outcome

At delivery, the repository has a concise English README, a Chinese README, open-collaboration submission materials, contribution guidance, visuals, a synthetic adoption sample, and SDD traceability for the packaging work.

## 2. Actors

| Actor | Role |
|---|---|
| Reviewer | Evaluates the Framework entry. |
| Adopter | Uses the package to understand how to adopt the framework. |
| Contributor | Extends docs, modules, templates, samples, or validation assets. |
| Framework maintainer | Maintains the parent framework narrative and artifact links. |
| Sub-capability owner | Plugs a capability such as Atlas Phoenix Lens into one lifecycle stage. |

## 3. Functional Scope

### 3.1 Framework Positioning

The README and submission docs must state:

- Project name: Atlas Engineering Delivery Hub.
- Category: Framework.
- Positioning: an end-to-end SDLC delivery framework organized around Seven Mountains SDLC and Seven Gates Flow.
- Current implementation: WWA Agent Workspace Hub and agent workspaces provide the operational baseline.

### 3.2 Seven Mountains SDLC

The package must use the following lifecycle order:

| Stage | Purpose | Current repository signal |
|---|---|---|
| Planning | Frame objectives, approval readiness, and scope boundaries. | Agent Contribute Dashboard baseline marks Planning implemented. |
| Estimation | Establish schedule, cost, resource, and risk baselines. | Agent Contribute Dashboard baseline marks Estimation implemented. |
| Discovery | Convert business intent into requirements and executable design. | Target-stage capability; Atlas Phoenix Lens may plug in here as an example. |
| Build | Turn approved design into code, local verification, and artifacts. | Build Agent workspace owns DEV-stage workflow. |
| Testing | Produce validation evidence before release. | Testing Agent workspace is in progress. |
| Deployment | Coordinate SIT, UAT, PROD rollout and launch acceptance. | Deployment Agent workspace is implemented. |
| Maintenance | Route production feedback, incidents, and improvements back into the SDLC. | Target-stage capability, not implemented in the current baseline. |

### 3.3 Seven Gates Flow / I-E-O-V

Each stage gate follows I-E-O-V:

| Element | Meaning |
|---|---|
| Input | Required intake artifacts, scope, owners, constraints, and preconditions. |
| Execute | Controlled work performed by humans, agents, automations, or external tools. |
| Output | Durable artifacts, decisions, run records, and traceable results. |
| Validate | Review checks, evidence gates, test results, approvals, and audit records. |

The package must connect I-E-O-V to the repository's human-in-the-loop task model, status visibility, audit log, scoped access, and current agent workspaces.

### 3.4 Open Collaboration Materials

The package must add:

- `docs/atlas-engineering-delivery-hub-index.md`
- `docs/open-collaboration-submission.md`
- `docs/open-collaboration-submission.zh-CN.md`
- `docs/atlas-engineering-delivery-hub-pitch.md`

These documents must explain why the project is a Framework, what reusable assets it provides, why it is AI-friendly, how teams can adopt it, how governance is supported, and how sub-capabilities plug into it.

### 3.5 Contribution Guidance

`CONTRIBUTING.md` must cover:

- Welcome contribution types.
- Documentation contribution rules.
- Framework module or plugin contribution rules.
- Validation and testing expectations.
- Data safety, secret handling, and redaction.
- PR checklist.
- Conventional commit style.

### 3.6 Adoption Sample

The package must add a synthetic sample under `docs/samples/` that shows:

- A fictional team adopting the framework.
- Stage and gate mapping.
- Roles and evidence expectations.
- Sub-capability plug-in points.
- No sensitive or customer data.

### 3.7 Visual Assets

The package must add Mermaid source and rendered SVG diagrams under `docs/assets/` for:

- Atlas Engineering Delivery Hub framework lifecycle.
- Seven Mountains SDLC.
- Seven Gates / I-E-O-V flow.
- Sub-capability fit, including Atlas Phoenix Lens as one Discovery example.

### 3.8 Two Competition Entry Split

The package must explain that this repository supports two related entries:

| Entry | Category | Scope |
|---|---|---|
| Atlas Engineering Delivery Hub | Framework | Parent team framework, Seven Mountains SDLC, shared gates, governance, evidence, contribution model. |
| Atlas Engineering Delivery Hub - Deployment | Tool / Function | One function inside the Hub, focused on M6 Deployment and the IBM iSeries one-click release UTL design direction. |

The root README and framework submission must lead with the Hub. Deployment-specific docs must remain available through a separate index and function-level submission so reviewers can evaluate it independently.

## 4. Non-Functional Requirements

- **No runtime impact:** No Java, Vue, API, database, or build behavior changes.
- **Safety:** No secrets, customer data, credentials, or screenshots.
- **Reviewability:** Root README remains concise and links to deeper docs.
- **Determinism:** Diagram source is committed next to rendered SVGs.
- **Localization:** English is canonical; Chinese README and submission docs are companion translations.
- **Validation:** Link check and whitespace check must run before commit.

## 5. Data, API, And Persistence

Not applicable. This packaging slice introduces documentation, diagrams, and samples only.

## 6. Risks

| Risk | Mitigation |
|---|---|
| Overstating unimplemented Discovery or Maintenance capabilities | Mark current scope and roadmap clearly. |
| Confusing Atlas Phoenix Lens with the parent framework | Mention it only as a Discovery-stage sub-capability example. |
| Losing detailed operational README content | Preserve the previous README body in a docs reference file. |
| Broken relative links | Run a Markdown relative-link existence check. |
| Confusing the Hub framework with the Deployment function | Keep separate framework and Deployment indexes, submissions, and pitch docs. |

## 7. Traceability

Detailed requirement-to-task mapping is maintained in [atlas-engineering-delivery-hub-traceability.md](../00-context/atlas-engineering-delivery-hub-traceability.md).
