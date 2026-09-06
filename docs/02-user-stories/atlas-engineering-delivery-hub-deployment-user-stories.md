# User Stories: Atlas Engineering Delivery Hub - Deployment

> **Historical packaging baseline — 2026-09-07 notice.** Current presentation scope is governed by the [Hub specification, current revision](../03-spec/atlas-engineering-delivery-hub-spec.md). Deployment remains an implemented module with its existing name; the evidence does not establish a second independent competition solution. Earlier English-default, separate-entry and commit requirements below are superseded for this documentation revision. Original samples remain unchanged; runtime contracts are not modified.

**Slice key:** `atlas-engineering-delivery-hub-deployment`
**Status:** Backfilled
**Lifecycle stage:** M6 Deployment

## Stories

### M6-US-01 - Reviewer Understands The Stage Positioning

As a competition reviewer, I want the Deployment entry point to clearly say this is the M6 Deployment Tool / Function so that I do not confuse it with the whole Atlas Engineering Delivery Hub framework.

Acceptance criteria:

- Deployment index and submission state Tool / Function category and M6 Deployment stage near the top.
- Root README states the parent Hub framework first and links to Deployment as one function.
- Lifecycle order is shown as `M1 Planning -> ... -> M6 Deployment -> M7 Maintenance`.
- M6 Deployment is highlighted in a diagram.

### M6-US-02 - Reviewer Sees Accurate Current Capability

As a reviewer, I want current capabilities and limitations described accurately so that I can assess maturity without inflated claims.

Acceptance criteria:

- Implemented capabilities are separated from planned/TBD capabilities.
- Human-in-the-loop approval is described as current behavior.
- Autonomous approval, one-click rollback, and maintenance automation are not claimed as implemented.

### M6-US-03 - Bilingual Audience Can Read The Entry

As a bilingual reviewer or contributor, I want English and Chinese materials so that the project can be understood by a wider internal audience.

Acceptance criteria:

- English README links to Chinese README.
- Chinese README is natural Chinese and links Deployment as a function inside the Hub.
- Chinese Deployment submission mirrors the Tool / Function story.

### M6-US-04 - Reviewer Has Competition Materials

As a reviewer, I want a concise submission, pitch, and documentation index so that I can evaluate the entry quickly.

Acceptance criteria:

- Deployment index links README, submission docs, pitch, visuals, samples, and SDD chain.
- Submission explains problem, reusable value, delivered scope, contribution areas, and demo story.
- Pitch can support a short spoken walkthrough.

### M6-US-05 - Reviewer Can See The Workflow Visually

As a reviewer, I want diagrams for lifecycle position and workflow so that I can understand the tool without reading all code.

Acceptance criteria:

- Lifecycle positioning diagram highlights M6.
- Workflow diagram shows upload, validation, execution, review, progression, and audit.
- Upstream/downstream diagram shows M4/M5 evidence entering M6 and M7 feedback leaving M6.

### M6-US-06 - Contributor Can Reuse A Sanitized Sample

As a contributor, I want a safe mini output sample so that I can build examples without exposing internal data.

Acceptance criteria:

- Sample package contains representative input, task output, audit trail, and rollback checklist.
- Samples use synthetic names and `example.invalid` links.
- Samples contain no credentials, kubeconfigs, tokens, customer data, or real environment names.

### M6-US-07 - Maintainer Keeps Deeper Context

As a maintainer, I want existing technical docs preserved so that the packaging change does not erase useful implementation detail.

Acceptance criteria:

- Detailed baseline docs remain linked from the README and deployment index.
- Parent framework files remain the Hub submission, while Deployment files remain the function submission.

### M6-US-08 - Maintainer Can Trust The Package

As a maintainer, I want validation and a commit so that the repository is ready to present.

Acceptance criteria:

- `git diff --check` passes.
- Markdown relative links pass.
- Mermaid rendering is attempted when tooling is available.
- Cheap existing tests are run when discoverable.
- Commit message follows conventional commit style when a commit is requested.

### M6-US-09 - Reviewer Understands IBM iSeries One-Click Direction

As a reviewer, I want the Deployment function to explain the IBM iSeries one-click release UTL direction so that I can see why this function is a meaningful independent project.

Acceptance criteria:

- Deployment submission describes the iSeries one-click release UTL direction as a sanitized design focus.
- Materials explain the reusable task/evidence/review shell without claiming unsafe full autonomy.
- No real environment names, credentials, customer details, or production-only runbooks are included.
