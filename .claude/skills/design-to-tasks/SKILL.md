---
name: design-to-tasks
description: Converts a detailed design document (design.md) into a structured implementation task breakdown (tasks.md) suitable for engineering execution, Jira decomposition, sprint planning, and handoff to coding agents. Use this skill whenever the user provides or references a design document and asks for implementation tasks, engineering breakdown, Jira tasks, sprint planning, delivery planning, or a work breakdown structure. Also trigger when the user says things like "convert this design into tasks", "break this down for engineering", "create an implementation plan from this design", "generate tasks from design", or "I want to hand this off to developers". Even if the user just uploads a design.md or pastes a design and says "what do I do next?" — use this skill.
---

# design-to-tasks

Converts a detailed design document into a structured, engineering-ready implementation task breakdown.

## Purpose

Transform a design document (`design.md`) into a `tasks.md` file that:
- Is suitable for Jira story/task creation
- Can be handed directly to engineering teams or coding agents
- Captures workstreams, dependencies, risks, and open questions
- Distinguishes MVP tasks from optional/future work

---

## Workflow

### Step 1: Ingest and Analyze the Design

Read the provided design document carefully. Extract and identify:

- **System name and scope** — what is being built
- **Modules and components** — major building blocks
- **APIs and interfaces** — endpoints, contracts, adapter boundaries
- **Data design** — entities, schemas, state models, persistence strategy
- **Workflows and state machines** — execution flows, transitions, retry/failure behavior
- **Validation rules** — input constraints, business rules
- **Integrations** — external systems (Jenkins, Ansible, secrets managers, notification systems, etc.)
- **Security concerns** — access control, secret handling, audit requirements
- **Testing considerations** — mentioned test types or coverage expectations
- **Operational requirements** — observability, metrics, logging, reliability

> If the design document is ambiguous or missing information in a key area, note it explicitly in the **Open Questions** and **Risks / Blockers** sections rather than silently inventing detail.
> Do not invent implementation work that is not supported by the design. If a task is reasonably implied but not explicit, include it only when necessary and mark it `[ASSUMPTION]`.

---

### Step 2: Identify Delivery Domains

Map the design elements to the applicable delivery domains. Not every design will require all domains — only include domains that are relevant:

| Domain | Covers |
|---|---|
| Backend / API | Service logic, endpoints, validation, orchestration interfaces |
| Frontend / UI | Screens, forms, state/status display, user feedback |
| Workflow / Orchestration | Execution logic, state transitions, retry/skip/resume, failure handling |
| Configuration / Administration | Env config, templates, parameters, admin workflows, permissions |
| Persistence / Data | Entities, repositories, migrations, audit history |
| Integrations | Jenkins, Ansible, secrets/credential access, monitoring, external interfaces |
| Security / Reliability / Observability | Access control, secret masking, audit logging, resilience, metrics/tracing |
| Testing | Unit, integration, workflow/e2e, adapter/contract, state transition, failure scenarios |

---

### Step 3: Define Tasks

For each domain, define tasks at an **implementable level**:
- Granular enough for a developer to start work
- Not so granular they become pseudocode or line-by-line instructions
- Suitable for a Jira task or sub-task

Label each task with:
- **Priority**: `Must` (MVP), `Should` (important but not blocking), `Could` (optional/future)
- **Owner type**: `backend`, `frontend`, `platform`, `QA`, `devops`, `security`
- **Inferred flag**: If a task is inferred (not explicitly in the design), label it `[ASSUMPTION]`

---

### Step 4: Map Dependencies and Sequencing

- Identify which tasks must complete before others can start
- Identify tasks that can run in parallel
- Surface the critical path

---

### Step 5: Surface Risks and Open Questions

Do not invent answers to ambiguous design areas. Instead:
- Flag unresolved design questions as **Open Questions**
- Flag missing interfaces, unclear ownership, or external dependencies as **Risks / Blockers**

---

## Output Format

Produce a `tasks.md` file with the following structure:

```markdown
# Implementation Task Breakdown

## Overview
- Implementation summary (1–3 sentences)
- Delivery objective
- Planning assumptions (list key assumptions made)

## Source Design
- System name
- Design scope summary (2–4 sentences)

## Workstreams
- Major implementation streams
- Recommended sequencing
- Opportunities for parallel work

## Task Breakdown by Domain

### [Domain Name]
- Brief list of capability areas covered by tasks in this domain
- List the major tasks for each domain in summary form before expanding them in Task Details.

### [Repeat per domain...]

## Task Details

### TASK-001: [Task Title]
- **Objective**: What this task achieves
- **Scope**: What is included (and optionally what is excluded)
- **Dependencies**: TASK-IDs that must complete first, or "None"
- **Owner type**: backend / frontend / platform / QA / devops / security
- **Priority**: Must / Should / Could
- **Notes**: Assumptions, inferred items [ASSUMPTION], open sub-questions

### [Repeat per task...]

## Dependency Plan
- Critical path: TASK-001 → TASK-003 → TASK-007 → ...
- Prerequisite clusters (grouped by blocking relationship)
- Parallel workstreams (tasks that can run simultaneously)

## Risks / Blockers
- [Risk description, impact, and what would resolve it]

## Open Questions
- [Question that must be answered before or during execution]
```

---

## Key Rules

1. **Derive tasks from the design.** Do not invent major features not supported by the source document.
2. **Flag inferences.** If a task is implied but not explicit, mark it `[ASSUMPTION]`.
3. **Surface gaps, don't paper over them.** Missing interfaces, unclear ownership, and ambiguous behavior belong in Risks and Open Questions.
4. **Keep tasks implementation-friendly.** No pseudocode, no line-by-line steps.
5. **Include non-feature work.** Migrations, audit logging, observability, test coverage, and error handling are first-class tasks.
6. **Use professional, concise engineering language.**
7. **Distinguish MVP from optional.** Use `Must / Should / Could` consistently.
8. **Group by domain and capability, not just UI actions.**
9. Ensure the overall task set is delivery-complete: implementation, validation, integration, observability, and testing should collectively support a shippable outcome.

---

## Task ID Scheme

Use sequential IDs: `TASK-001`, `TASK-002`, etc.  
Optionally prefix by domain for large breakdowns: `BE-001`, `FE-001`, `INT-001`, `TST-001`, etc.

---

## Output File

Write the result to `docs/06-tasks/tasks.md` by default.
If the user explicitly requests a different location, follow the user's requested path.
