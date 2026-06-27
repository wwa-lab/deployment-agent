# User Stories: Atlas Engineering Delivery Hub - Deployment

**Slice key:** `atlas-engineering-delivery-hub-deployment`
**Status:** Backfilled
**Lifecycle stage:** M6 Deployment

## Stories

### M6-US-01 - Reviewer Understands The Stage Positioning

As a competition reviewer, I want the repository entry point to clearly say this is the M6 Deployment Tool so that I do not confuse it with the whole Atlas Engineering Delivery Hub framework.

Acceptance criteria:

- README states Tool category and M6 Deployment stage near the top.
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
- Chinese README is natural Chinese and preserves M6 Deployment positioning.
- Chinese submission mirrors the Tool-category story.

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

- Detailed baseline docs remain linked from the new README and deployment index.
- Older umbrella framework files are retained only as context and not as the current Tool submission.

### M6-US-08 - Maintainer Can Trust The Package

As a maintainer, I want validation and a commit so that the repository is ready to present.

Acceptance criteria:

- `git diff --check` passes.
- Markdown relative links pass.
- Mermaid rendering is attempted when tooling is available.
- Cheap existing tests are run when discoverable.
- Commit message is `docs: package atlas engineering delivery hub deployment tool`.
