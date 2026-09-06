# Atlas Engineering Delivery Hub Packaging Design

## Visual exploration — ocean / teal, 2026-09-07

Third exploration: the user found the overall style insufficiently technological. [Immersive technology preview v3](../prototypes/atlas-immersive-tech-preview-v3.html) replaces the composition with a full dark stage, oversized typography, an animated projected task network, a spatial platform map and open three-step diagrams. It remains a four-page concept using the same evidence boundaries. Animation must have a pause control, respect reduced motion and suspend when the page is hidden. It uses local canvas/SVG and inline code for offline use; no product runtime or completed-integration claims are introduced.

Follow-up: the user requested stronger technology cues and visual impact. Add a second four-slide concept at [technology preview v2](../prototypes/atlas-ocean-tech-preview-v2.html), using midnight navy, luminous cyan/teal accents, layered task geometry, a restrained grid and brief flow animations. Retain the first preview for comparison. These are visual metaphors, not live system status; factual claims and maturity labels remain unchanged. Keep the existing navigation and reduced-motion behavior and validate the revised layout independently.

The user accepted a four-slide visual preview: cover, platform overview, three-step progression and evidence. This prototype uses deep navy, teal and warm white; it explores appearance without introducing a runtime contract. The current 18-slide presentation and evidence assets remain versioned references. Preview: [ocean / teal HTML](../prototypes/atlas-ocean-teal-preview.html). Reuse the current pitch's claims and distinguish existing implementation from planned intelligence. Validate offline navigation, notes and viewport fit before stakeholder review. Full-deck adoption is a subsequent style decision.

## Current revision — 2026-09-07, platform clarification
This revision supersedes conflicting information design below. Runtime design is unchanged.

- Entry: README.md (Chinese), README.en.md (full English), README.zh-CN.md (compatible link).
- Narrative: docs/atlas-engineering-delivery-hub-pitch.md is the detailed Chinese narrative; submissions are concise form-ready summaries. Sequence: Agentic SDLC platform → current IBM iSeries practice → Deployment Agent → atomization / automation / intelligence → evidence and reuse boundaries. Both existing indexes remain.
- Evidence: docs/samples/README.md owns the evidence ledger and measurement plan. docs/samples/case-template.md defines source, consent, revision, input/output, raw evidence, method, human intervention, checksums and limitations.
- Historical bytes: existing samples and assets retain their original content. A JSON manifest records source commit and SHA-256; hash equality checks reject silent edits. New test captures live in unique local output directories; only reviewed shareable summaries are included in the package.
- Visuals: new value and capability SVGs retain text/groups/arrows and are canonical editable sources; PNGs are browser-rendered derivatives. Old graphics remain historical references.
- Versioning: prior SVG/PNG, HTML and verification bytes remain intact. Use atlas-delivery-value-v2.svg/png, atlas-delivery-workflow-v2.svg/png and the presentation-v2.html file for the current story.
- Deck: docs/atlas-engineering-delivery-hub-presentation-v2.html contains inline CSS/JS and Chinese content/notes. Arrow/Page/Space, Home/End, touch, wheel, on-screen buttons, N for notes and Escape are supported. Every slide fits its viewport; motion respects user preference.
- Compatibility: keep actual Maven/npm commands, Java package names, routes and tool routing directories. No fictional universal CLI.
- Edge cases: unknown external status stays pending; failed/rejected execution can rerun; skip is permitted only in current valid states and is not success evidence. Broken links, clipping, personal fields or unsupported claims fail the package review.

## Historical packaging baseline (superseded where inconsistent)

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

- README links to the framework docs index, contribution guide, framework submission, framework pitch, sample, diagrams, preserved baseline, and Deployment function entry.
- Docs index links to every framework package artifact and the separate Deployment function package.
- Submission and pitch link back to README and docs index.
- SDD docs link to traceability and source documents.

## 7.1 Two-Project Link Design

The link model must keep the two competition entries distinct:

| Link surface | Framework target | Deployment target |
|---|---|---|
| Root README | Primary entry and framework summary. | Function table row and Deployment docs section. |
| Docs index | `atlas-engineering-delivery-hub-index.md` | `atlas-engineering-delivery-hub-deployment-index.md` |
| Submission | `open-collaboration-submission.md` | `open-collaboration-submission-deployment.md` |
| Chinese submission | `open-collaboration-submission.zh-CN.md` | `open-collaboration-submission-deployment.zh-CN.md` |
| Pitch | `atlas-engineering-delivery-hub-pitch.md` | `atlas-engineering-delivery-hub-deployment-pitch.md` |

Deployment copy may mention IBM iSeries one-click release UTL as design direction, but it must stay sanitized and avoid real environment names, credentials, customer details, or production-only runbooks.

## 8. Validation Design

Required lightweight checks:

- `git diff --check`
- Markdown relative-link existence check
- Render Mermaid diagrams and confirm SVG files exist
- Existing tests only if cheap and relevant; this slice is documentation-only, so heavy backend/frontend suites are not required.
