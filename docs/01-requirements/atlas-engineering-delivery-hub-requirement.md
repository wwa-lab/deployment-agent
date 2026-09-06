# Atlas Engineering Delivery Hub Requirement

## Current revision — 2026-09-07, platform clarification
**Status:** Authorized documentation revision; implementation below is a historical packaging baseline.
**Source:** User request to inspect this repository, clarify independent value, use Situation → Solution → Result, and deliver local changes only.

This revision supersedes earlier English-default, full-lifecycle readiness, automatic commit, and two-independent-entry requirements on this page. Keep the project name. Position it as a concrete Agentic SDLC platform practice, with IBM iSeries as the current user-confirmed implementation setting and Deployment Agent as the presentation deep dive. The platform method is not restricted to one delivery language. Framework remains the existing category context, not proof that all lifecycle stages operate.

- **AEDH-REQ-10:** Describe users, work situation, current handling costs (unmeasured), three concrete mechanisms, inputs, outputs, and boundaries from repository evidence.
- **AEDH-REQ-11:** Make simplified Chinese the default README; provide a complete English README and a compatible old Chinese entry.
- **AEDH-REQ-12:** Distinguish implemented behavior, synthetic examples, executable test results, authorized field evidence, unmeasured benefit, and plans.
- **AEDH-REQ-13:** Preserve original samples, historical outputs and diagrams byte-for-byte; register provenance and checksums; put new runs in new locations.
- **AEDH-REQ-14:** Supply red/white/black editable value and capability SVGs with PNG exports and a Chinese offline HTML deck with notes and keyboard controls.
- **AEDH-REQ-15:** Use tool-neutral Agent Skills language publicly, retain real integration identifiers, leave personal application fields empty, and make no unverified claims about other entries.
- **AEDH-REQ-16:** Check links, language navigation, assets, deck behavior and selected implementation claims; record tested and untested scope. Do not commit, push, merge, change runtime or migrate directories.

Acceptance: reviewers can trace each central claim to the case index or code, identify limits of simulated tests, and read the deck offline. Real customer evidence and measured savings are missing inputs, not blockers for this documentation revision.

**AEDH-REQ-17 (user clarification):** Present the platform first, then Deployment Agent through atomization → automation → intelligence. BAU → SOP → atomic tasks is the foundation; reuse existing execution tools, then evolve toward evidence-informed orchestration and assisted decisions. Mark intelligence as an evolution direction and distinguish language-neutral task/adapter contracts from verified portability. The supplied screenshot is narrative context, not a benchmark or runtime trace.

## Historical packaging baseline (superseded where inconsistent)

**Date:** 2026-06-27
**Status:** Proposed / Documents-first
**Slice key:** `atlas-engineering-delivery-hub`
**Owner:** Atlas Engineering Delivery Hub direction

## 1. Background

This repository currently documents and implements the WWA Agent Workspace Hub, a Spring Boot and Vue platform for controlled, human-in-the-loop delivery workflows. For the internal open collaboration competition, the repository must be packaged as the parent framework entry named **Atlas Engineering Delivery Hub**.

The framework narrative must sit above individual agent workspaces. It should present the repository as an end-to-end SDLC delivery framework organized around the Seven Mountains SDLC and the Seven Gates Flow / I-E-O-V model. Existing implementation details should remain available as reference documentation, but the root README should become concise, review-friendly, and competition oriented.

## 2. Product Objectives

1. Position Atlas Engineering Delivery Hub as a reusable framework, not a single deployment tool.
2. Explain how the framework provides visibility, process control, quality validation, traceability, and always-on delivery operations.
3. Make the Seven Mountains SDLC understandable to adopters.
4. Show how Seven Gates and I-E-O-V provide repeatable governance at each stage.
5. Provide bilingual entry documentation for English and Chinese readers.
6. Make contribution, adoption, submission, pitch, sample, and visual assets discoverable.
7. Preserve existing implementation documentation rather than deleting detailed operational knowledge.
8. Distinguish the two competition projects: the Hub as the parent Framework entry, and Deployment as an independent Tool / Function entry inside the Hub.

## 3. Scope

### 3.1 In Scope

- Rewrite the root README as the English framework entry.
- Add `README.zh-CN.md` as the Chinese entry.
- Add open-collaboration submission, pitch, and docs index materials.
- Add a contribution guide covering docs, framework modules, validation, safety, PR checklist, and commit style.
- Add Mermaid source plus rendered SVG diagrams under `docs/assets/`.
- Add a synthetic adoption sample under `docs/samples/`.
- Preserve the existing detailed README content in a reference doc.
- Link the package from README and docs index.
- Run lightweight validation and commit the package.

### 3.2 Out of Scope

- Runtime feature changes.
- Database schema changes.
- API or UI behavior changes.
- Real customer, internal confidential, credential, or screenshot material.
- Treating Atlas Phoenix Lens as the primary project.
- Replacing the existing WWA technical identifiers, route slugs, or package names.

## 4. Users and Roles

| Role | Need |
|---|---|
| Competition reviewer | Understand why this repository qualifies as a Framework entry. |
| Adopting team | Learn how to use the framework to structure SDLC delivery governance. |
| Contributor | Understand contribution rules, validation expectations, and safety boundaries. |
| Product or process owner | See how lifecycle visibility, gates, and accountability are represented. |
| Engineering lead | Understand the architecture, extension model, and current implementation scope. |

## 5. Functional Requirements

- **AEDH-REQ-01:** The package shall identify the project as Atlas Engineering Delivery Hub and the competition category as Framework.
- **AEDH-REQ-02:** The package shall describe the Seven Mountains SDLC stages: Planning, Estimation, Discovery, Build, Testing, Deployment, and Maintenance.
- **AEDH-REQ-03:** The package shall describe Seven Gates Flow and I-E-O-V as the stage-level governance model.
- **AEDH-REQ-04:** The package shall include competition submission, Chinese submission, pitch, docs index, and README links.
- **AEDH-REQ-05:** The package shall include contribution guidance for documentation, framework modules or plugins, validation, redaction, PR checklist, and conventional commits.
- **AEDH-REQ-06:** The package shall include a synthetic adoption sample that does not expose customer or sensitive data.
- **AEDH-REQ-07:** The package shall include visual assets that communicate the framework lifecycle and where Discovery capabilities such as Atlas Phoenix Lens fit.
- **AEDH-REQ-08:** The package shall preserve detailed existing implementation documentation in a docs reference file.
- **AEDH-REQ-09:** The package shall state that Deployment is one function inside the Hub and may be submitted separately as the M6 Deployment Tool / Function entry, including the IBM iSeries one-click release UTL design direction.

## 6. Non-Functional Requirements

- **Safety:** Do not include secrets, credentials, customer data, or unredacted internal screenshots.
- **Reviewability:** Keep the root README concise enough for competition reviewers.
- **Traceability:** Link package artifacts back to this SDD slice.
- **Maintainability:** Prefer Mermaid source plus deterministic SVG output for diagrams.
- **Localization:** English remains the default README; Chinese is provided as an optional companion.
- **Validation:** Run `git diff --check` and a Markdown relative-link existence check before commit.

## 7. Acceptance Criteria

1. `README.md` presents Atlas Engineering Delivery Hub as the Framework entry.
2. `README.zh-CN.md` provides the Chinese companion entry.
3. The required open-collaboration docs exist and are linked.
4. `CONTRIBUTING.md` exists and includes safety, contribution, validation, PR, and commit guidance.
5. `docs/assets/` contains Mermaid source and rendered SVG diagrams.
6. `docs/samples/` contains a synthetic adoption sample.
7. Existing operational README detail is preserved in a docs reference file.
8. Deployment has separate function-level submission links so reviewers can evaluate it without confusing it for the entire Hub.
9. Validation commands run and results are recorded in the final response.
10. The related changes are committed with a conventional commit message.

## 8. Open Questions

Open questions are tracked in [atlas-engineering-delivery-hub-tasks.md](../06-tasks/atlas-engineering-delivery-hub-tasks.md).
