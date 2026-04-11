# Claude Code SDLC Playbook

## Purpose

This playbook defines how to use Claude Code and the project skill set to move from raw requirements to implementation in a controlled SDLC workflow.

It is designed for:
- platform projects
- backend systems
- frontend features
- DevOps and workflow systems
- multi-stage implementation work

The goal is to make Claude Code work as a structured engineering assistant rather than a one-shot generator.

---

## SDLC Pipeline Overview

The standard pipeline is:

1. `req-to-user-story`
2. `user-story-to-spec`
3. `spec-to-architecture`
4. `architecture-to-design`
5. `design-to-tasks`
6. `tasks-to-code` (incremental brownfield work) or `tasks-to-implementation` (greenfield / migration / mode-detection workflow)

Quality gates:

7. `review-doc-quality`
8. `review-code-against-design`

Supporting review skill:

9. `review-docs-against-code`

---

## Standard Artifact Flow

The default artifact flow in this repository is:

- `docs/02-user-stories/user-stories.md`
- `docs/03-spec/spec.md`
- `docs/04-architecture/architecture.md`
- `docs/05-design/design.md`
- `docs/06-tasks/tasks.md`

Implementation is then applied directly in the repository.

---

## When to Use the Full Pipeline

Use the full pipeline when:
- the input starts as raw natural language requirements
- the feature is medium or large
- the work crosses backend, frontend, integrations, or workflow logic
- the implementation needs traceability and review
- multiple phases must be generated in order

Typical flow:

`requirement -> stories -> spec -> architecture -> design -> tasks -> code`

---

## When to Use Individual Skills

### `req-to-user-story`
Use when the input is raw business, product, platform, or DevOps requirements.

Output:
- Jira-ready user stories
- acceptance criteria
- assumptions
- dependencies
- open questions

### `user-story-to-spec`
Use when user stories already exist and need to be consolidated into an implementation-friendly specification.

Output:
- functional requirements
- non-functional requirements
- workflow
- integrations
- risks
- open questions

### `spec-to-architecture`
Use when the spec is stable enough to define high-level system structure.

Output:
- system context
- logical modules
- boundaries
- data/state concerns
- integration architecture
- risks and tradeoffs

### `architecture-to-design`
Use when architecture is approved and detailed implementation design is needed.

Output:
- module design
- API/interface design
- logical data design
- workflow and state handling
- validation and error handling
- integration behavior

### `design-to-tasks`
Use when design is ready to be converted into an implementation plan.

Output:
- workstreams
- implementation tasks
- dependencies
- priorities
- owner suggestions
- blockers

### `tasks-to-code`
Use for incremental brownfield work: the repository already has meaningful source, build/test commands, and established conventions to extend. This is the default implementation skill for this repo.

Output:
- code changes
- tests
- concise implementation summary
- validation results
- remaining blockers

### `tasks-to-implementation`
Use when the mode-detection workflow should govern the run: greenfield bootstrapping, migration/porting between stacks, or when you explicitly want the skill to classify the repo and select the appropriate workflow (greenfield / brownfield / migration) before coding.

Output:
- detected mode and signals
- code changes
- tests
- validation results (including parity checks for migration mode)
- remaining blockers

---

## Quality Gates

### `review-doc-quality`
Use before moving a document into the next phase.

Checks:
- completeness
- clarity
- consistency
- traceability
- phase discipline
- readiness for the next stage

Typical use:
- review stories before spec
- review spec before architecture
- review architecture before design
- review design before tasks
- review tasks before coding

### `review-code-against-design`
Use after implementation work has been generated.

Checks:
- design fidelity
- task coverage
- workflow/state alignment
- integration boundary correctness
- validation/error-handling alignment
- acceptable deviation vs real drift

Typical use:
- before merge
- before testing handoff
- before continuing implementation on top of generated code

### `review-docs-against-code`
Use when README files, runbooks, onboarding docs, AGENTS or CLAUDE instructions, or `docs/`
content must be checked against the current repository implementation.

Checks:
- build, run, test, and setup command drift
- file and path references
- API base paths and route examples
- config names and environment variables
- roles, states, workflow, and architecture claims

---

## Recommended Default Workflow

### Review Mode
Use when you want Claude Code to generate artifacts but not implement code yet.

Flow:
1. raw requirement -> `req-to-user-story`
2. review with `review-doc-quality`
3. stories -> `user-story-to-spec`
4. review with `review-doc-quality`
5. spec -> `spec-to-architecture`
6. review with `review-doc-quality`
7. architecture -> `architecture-to-design`
8. review with `review-doc-quality`
9. design -> `design-to-tasks`
10. review with `review-doc-quality`

### Execution Mode
Use when the design and tasks are already stable and implementation can begin.

Flow:
1. `design-to-tasks`
2. review tasks with `review-doc-quality`
3. `tasks-to-code`
4. `review-code-against-design`

---

## Rules of Engagement

### 1. Do not skip phases by default
Do not jump directly from requirement to code unless the user explicitly chooses to do so.

### 2. Prefer standard artifact locations
Use these default paths unless the user requests otherwise:
- `docs/02-user-stories/user-stories.md`
- `docs/03-spec/spec.md`
- `docs/04-architecture/architecture.md`
- `docs/05-design/design.md`
- `docs/06-tasks/tasks.md`

### 3. Surface ambiguity instead of silently inventing detail
If requirements, design intent, or implementation scope are unclear, capture:
- assumptions
- risks
- blockers
- open questions

### 4. Keep each document in its own phase scope
- stories should not become specs
- specs should not become designs
- architecture should not become implementation detail
- design should not become code
- tasks should not become pseudocode

### 5. Prefer incremental progress
For coding, prefer the highest-priority coherent subset instead of a broad unsafe pass.

### 6. Respect repository conventions
When implementing code:
- inspect the repo first
- follow existing patterns
- avoid introducing a new stack unnecessarily
- do not fabricate test results

---

## Recommended Commands / Prompts

### Start from raw requirement
`Use req-to-user-story first, then continue through user-story-to-spec, spec-to-architecture, architecture-to-design, and design-to-tasks in Review Mode.`

### Create stories only
`Use req-to-user-story on this requirement and produce Jira-ready stories.`

### Convert stories to spec
`Use user-story-to-spec to consolidate these stories into docs/03-spec/spec.md.`

### Generate architecture
`Use spec-to-architecture to convert docs/03-spec/spec.md into docs/04-architecture/architecture.md.`

### Generate design
`Use architecture-to-design to convert docs/04-architecture/architecture.md into docs/05-design/design.md.`

### Generate tasks
`Use design-to-tasks to convert docs/05-design/design.md into docs/06-tasks/tasks.md.`

### Implement code (brownfield, default)
`Use tasks-to-code to implement the highest-priority coherent subset in the current repository.`

### Implement code (greenfield / migration / mode-detection)
`Use tasks-to-implementation to implement tasks with explicit mode detection before coding.`

### Review a document
`Use review-doc-quality to review docs/05-design/design.md and assess whether it is ready for design-to-tasks.`

### Review code fidelity
`Use review-code-against-design to compare the current implementation against docs/05-design/design.md and docs/06-tasks/tasks.md.`

### Review repo docs against code
`Use review-docs-against-code to check README.md, AGENTS.md, or docs/UAT_RUNBOOK.md against the current repository code.`

---

## Recommended Project Types

This workflow is especially suitable for:
- release orchestration platforms
- deployment management systems
- DevOps automation platforms
- internal engineering tools
- workflow engines
- multi-module web systems
- systems with strong integration boundaries

---

## Practical Notes

- For small changes, the full pipeline may be unnecessary.
- For medium or large features, the full pipeline reduces drift and rework.
- For platform work, always keep configuration, state handling, validation, integration boundaries, and auditability explicit.
- If a generated artifact is weak, review and revise it before moving forward.
- If generated code partially aligns but is incomplete, fix the critical gaps before building further on top of it.

---

## Operating Principle

Claude Code should not behave like a one-step code generator.

It should behave like a structured engineering assistant that:
- helps shape requirements
- preserves traceability
- supports architecture and design thinking
- creates implementation-ready tasks
- generates code carefully
- validates outputs before the next phase
