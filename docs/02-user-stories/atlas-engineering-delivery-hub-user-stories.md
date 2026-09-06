# Atlas Engineering Delivery Hub User Stories

## Current revision — 2026-09-07, platform clarification
This revision supersedes earlier language-default and independent-entry claims. Previous stories below are historical context.

- **AEDH-US-09 (REQ-10,15):** As a reviewer, understand the Hub's independently useful problem, users, inputs/outputs and metrics; see Deployment identified as an implemented module. Acceptance: no claim of independent competing projects based on module naming, no invented information about other projects.
- **AEDH-US-10 (REQ-11,12):** As an adopter, read Chinese or English and trace the same capability claims to evidence. Acceptance: two complete current READMEs, reciprocal links, legacy Chinese compatibility link, explicit implemented/example/measured/planned distinctions.
- **AEDH-US-11 (REQ-13):** As a maintainer, preserve original evidence and register new versions. Acceptance: case source, version, inputs, outputs, verification, human involvement and result are recorded with checksums.
- **AEDH-US-12 (REQ-14,16):** As a presenter, show an offline deck and readable diagrams. Acceptance: red/white/black Chinese visuals, SVG/PNG assets, keyboard navigation, notes, reduced motion, viewport checks and a verification report.

Dependencies: inspect current code before writing claims; finish the evidence registry before finalizing presentation results. No authorized field case or measured return is assumed.

**AEDH-US-13 (REQ-17):** As a presenter, first introduce the Agentic SDLC platform and its IBM iSeries practice, then demonstrate Deployment Agent using atomization, automation and intelligence. Acceptance: the deck and both READMEs preserve platform breadth, distinguish framework technology from supported delivery languages, explain prerequisites between the three steps, and label runtime intelligence/UTL integration evidence accurately.

## Historical packaging baseline (superseded where inconsistent)

**Date:** 2026-06-27
**Status:** Proposed / Documents-first
**Source requirement:** [atlas-engineering-delivery-hub-requirement.md](../01-requirements/atlas-engineering-delivery-hub-requirement.md)

## Story AEDH-US-01: Understand The Framework Entry

**As a** competition reviewer,
**I want** the root README to identify Atlas Engineering Delivery Hub as a Framework entry,
**so that** I can quickly understand the project category, purpose, and reusable value.

Acceptance criteria:

1. The README starts with the Atlas Engineering Delivery Hub project name.
2. The category is stated as Framework.
3. The summary distinguishes the parent framework from individual sub-capabilities.
4. The current implementation baseline remains discoverable from the README.

## Story AEDH-US-02: See The Seven Mountains SDLC

**As an** adopting team,
**I want** the package to explain the seven SDLC mountains,
**so that** I can map my delivery work into a visible end-to-end lifecycle.

Acceptance criteria:

1. The stages are listed in order: Planning, Estimation, Discovery, Build, Testing, Deployment, Maintenance.
2. Each stage includes its delivery purpose.
3. Current implementation status is clear without overstating future capability.

## Story AEDH-US-03: Understand Seven Gates And I-E-O-V

**As a** process owner,
**I want** the docs to explain the I-E-O-V model,
**so that** every stage can define required inputs, execution controls, outputs, and validation evidence.

Acceptance criteria:

1. I-E-O-V is defined as Input, Execute, Output, Validate.
2. Seven Gates are described as lifecycle governance checkpoints.
3. The docs connect gates to evidence, auditability, and human-in-the-loop control.

## Story AEDH-US-04: Submit The Package For Open Collaboration

**As a** framework sponsor,
**I want** submission and pitch docs,
**so that** the project can be reviewed consistently in the internal competition.

Acceptance criteria:

1. English and Chinese submission docs exist.
2. A pitch document exists.
3. The docs index links all package artifacts.
4. The package states why it is AI-friendly and reusable.

## Story AEDH-US-05: Contribute Safely

**As a** contributor,
**I want** clear contribution rules,
**so that** I can add docs, modules, samples, and validation evidence without leaking sensitive data or breaking framework conventions.

Acceptance criteria:

1. `CONTRIBUTING.md` lists welcome contribution types.
2. It covers documentation, framework module or plugin rules, validation, secrets, redaction, PR checklist, and commit style.
3. It points contributors to SDD expectations for non-trivial changes.

## Story AEDH-US-06: Try A Synthetic Adoption Sample

**As an** adopting team,
**I want** a small synthetic sample,
**so that** I can see how a team would configure stages, gates, roles, evidence, and sub-capabilities.

Acceptance criteria:

1. The sample contains no customer or confidential data.
2. It shows the lifecycle, gates, contribution roles, and evidence expectations.
3. It includes Atlas Phoenix Lens only as a Discovery-stage sub-capability example.

## Story AEDH-US-07: Validate Discoverability

**As an** engineering lead,
**I want** links and whitespace validated,
**so that** the package is reviewable and avoids obvious documentation defects.

Acceptance criteria:

1. `git diff --check` passes.
2. A Markdown relative-link existence check passes or known external links are excluded.
3. The final response reports validation commands and results.

## Story AEDH-US-08: Distinguish The Two Competition Entries

**As a** competition reviewer,
**I want** the repository to clearly separate the parent Framework entry from the Deployment function entry,
**so that** I can evaluate Atlas Engineering Delivery Hub as a team framework without losing the independent Deployment project.

Acceptance criteria:

1. The root README identifies Atlas Engineering Delivery Hub as the primary Framework entry.
2. The docs explain that Deployment is one function inside the Hub, not the whole Hub.
3. Deployment has its own function-level submission links.
4. The Deployment materials mention the IBM iSeries one-click release UTL design direction without exposing internal or sensitive details.
