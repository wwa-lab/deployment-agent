# Atlas Engineering Delivery Hub Packaging Design

**Date:** 2026-06-27
**Status:** Proposed / Documents-first
**Source architecture:** [atlas-engineering-delivery-hub-architecture.md](../04-architecture/atlas-engineering-delivery-hub-architecture.md)

## 1. Overview

This design defines the content shape for the Atlas Engineering Delivery Hub open-collaboration package. The design favors short entry documents with links to deeper references so reviewers can scan quickly while adopters can still inspect implementation details.

## 2. README Design

The English README should contain:

1. Project title and category.
2. One-sentence summary.
3. Framework positioning.
4. Seven Mountains SDLC overview.
5. Seven Gates Flow / I-E-O-V model.
6. Framework capabilities.
7. Current scope.
8. Architecture and design overview.
9. Demo walkthrough.
10. Relationship to sub-capabilities such as Atlas Phoenix Lens.
11. Roadmap.
12. Links to contribution and submission materials.

The Chinese README mirrors the same structure with concise wording.

## 3. Submission And Pitch Design

Submission documents must answer:

- Why is this a Framework?
- What problem does it solve?
- What reusable assets does it provide?
- Why is it AI-friendly?
- How can teams adopt it?
- How does it support delivery governance?
- How do sub-capabilities plug in?

The pitch should be shorter than the submission and optimized for a reviewer or slide narrator.

## 4. Diagram Design

Create Mermaid source and SVG output for three diagrams:

| Diagram | Source | Rendered SVG | Purpose |
|---|---|---|---|
| Framework lifecycle | `docs/assets/atlas-framework-lifecycle.mmd` | `docs/assets/atlas-framework-lifecycle.svg` | Shows parent framework, Platform Core, agents, and lifecycle stages. |
| Seven Mountains SDLC | `docs/assets/seven-mountains-sdlc.mmd` | `docs/assets/seven-mountains-sdlc.svg` | Shows the seven SDLC stages in order. |
| Seven Gates I-E-O-V | `docs/assets/seven-gates-ieov.mmd` | `docs/assets/seven-gates-ieov.svg` | Shows Input, Execute, Output, Validate at every stage gate. |

The diagrams should use readable labels, avoid sensitive names, and keep Atlas Phoenix Lens as a Discovery-stage sub-capability example only.

## 5. Contribution Guide Design

The contribution guide should be direct and checklist-oriented. It should not introduce a second process that conflicts with the repository SDD contract. It should tell contributors to:

- update SDD artifacts for non-trivial or user-facing changes;
- add docs under the relevant docs folder;
- keep samples synthetic;
- validate links and tests relevant to the change;
- use conventional commits.

## 6. Adoption Sample Design

The sample should show a fictional team adopting the framework by defining:

- project context;
- lifecycle stage map;
- gate evidence map;
- framework module or sub-capability extension points;
- validation checklist.

The sample should be small enough to copy into a real team's planning docs after replacing synthetic names.

## 7. Link Design

Primary links:

- README links to docs index, contribution guide, submission, pitch, sample, diagrams, and preserved baseline.
- Docs index links to every package artifact.
- Submission and pitch link back to README and docs index.
- SDD docs link to traceability and source documents.

## 8. Validation Design

Required lightweight checks:

- `git diff --check`
- Markdown relative-link existence check
- Render Mermaid diagrams and confirm SVG files exist
- Existing tests only if cheap and relevant; this slice is documentation-only, so heavy backend/frontend suites are not required.

