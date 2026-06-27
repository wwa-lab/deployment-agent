# Atlas Engineering Delivery Hub User Stories

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

