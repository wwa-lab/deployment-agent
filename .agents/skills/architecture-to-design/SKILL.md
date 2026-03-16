---
name: architecture-to-design
description: Converts a high-level system architecture document (architecture.md) into a detailed design document (design.md) suitable for engineering implementation, API definition, data modeling, UI design alignment, and downstream task breakdown. Use this skill whenever the user provides an architecture.md or similar architecture document and asks for a design doc, detailed design, solution design, module design, or implementation design. Also trigger when the user says "convert architecture to design", "create design from architecture", "write a design document", "I need a design doc before we start coding", or "break down this architecture for engineers". If the user uploads or pastes an architecture document and wants to move toward implementation, this skill should always be used — even if they don't use the exact words "design document".
---

# Architecture to Design Skill

## Purpose

Transform a high-level architecture document into a detailed, implementation-friendly design document. The output bridges the gap between architecture decisions and engineering execution — detailed enough for task breakdown and coding, but not so low-level that it becomes code or pseudocode.

---

## Workflow

### Step 1: Read and Analyze the Architecture

- Read the provided `architecture.md` (or equivalent source document)
- Identify:
  - System name and purpose
  - Major modules and components
  - Workflows and execution flows
  - Interfaces and integration points
  - State models and data flows
  - Architectural assumptions and constraints
  - Gaps, ambiguities, or unresolved decisions in the architecture

If the architecture document is missing, ask the user to provide it before proceeding.

### Step 2: Plan the Design Scope

Before writing, mentally map:
- Which modules need detailed design?
- Which workflows are most complex or implementation-sensitive?
- Where are the integration boundaries?
- What is explicitly in scope vs. out of scope?
- What must be inferred vs. what is explicitly stated?

### Step 3: Generate the Design Document

Produce a `design.md` using the **Output Structure** defined below.

**Key generation rules:**
- Derive design from the source architecture — do not invent major capabilities not supported by it
- If a design element is inferred rather than explicitly stated, label it: `[Assumption]`
- Surface architecture gaps rather than silently filling them with invented detail
- Keep language concise, professional, and engineering-friendly
- Do not write code, SQL DDL, or exact payload schemas unless explicitly requested
- Make state transitions, validation rules, and error handling explicit where they affect implementation
- For DevOps/workflow platforms, always explicitly design: configuration handling, template management, execution state, monitoring, audit history, and integration behavior
- - Do not invent specific frameworks, databases, messaging systems, frontend libraries, or infrastructure platforms unless they are explicitly supported by the source architecture; if inferred, label them as `[Assumption]`
- Keep the design at a logical and module level; do not turn it into class design, database DDL, or endpoint-by-endpoint payload specification unless explicitly requested

### Step 4: Deliver the Output

- Write the output as `docs/design.md` by default
- If the user explicitly requests a different path, follow the user's requested location
- If generating inline is more appropriate, produce the full structured document in the response

---

## Output Structure
Use this structure for `design.md`. Keep the full structure whenever practical. If a section is not applicable, write `None identified` instead of omitting it, unless the user explicitly asks for a shorter design document.

## Design Diagram (Mermaid)
- High-level module interaction diagram
- Key workflow or sequence diagram when useful

```markdown
# Detailed Design: [System Name]

## Overview
- Design summary (1–3 sentences)
- Design objective
- Relationship to source architecture

## Source Architecture
- System name
- Architecture summary
- Relevant assumptions carried forward

## Design Assumptions
- [Assumption] ...
- [Assumption] ...

## Design Scope
- In-scope modules
- Out-of-scope details
- Design boundaries

## Module Design
For each major module:
- Module name
- Responsibilities
- Key interactions with other modules
- Internal design concerns (state management, concurrency, caching, etc.)

## API / Interface Design
For each main API or interface:
- Purpose
- Key inputs / outputs at a logical level (not exact schemas)
- Validation expectations
- Error behavior

## Data Design
- Logical entities and key attributes
- Relationships between entities
- Configuration objects
- Status / state models with allowed transitions

## UI / User Flow Design
(Omit if no UI component)
- User actions and triggers
- Major screens or views
- State transitions visible to the user
- Feedback and status display expectations

## Workflow / Execution Design
- Step-by-step system flow
- Execution rules and ordering constraints
- Dependency handling
- Retry / skip / resume behavior
- Failure handling and escalation paths
- Explicitly describe the primary stateful entities, their valid states, and the rules for state transitions where they affect execution, validation, monitoring, or recovery behavior.

## Integration Design
For each external system:
- Integration purpose
- Interaction pattern (sync/async, pull/push, event-driven)
- Credential / secret access patterns
- Failure and retry behavior
- Logging and observability at the integration boundary

## Security / Audit / Reliability Design
- Access control assumptions
- Secrets handling approach
- Audit record design (what is logged, when, where)
- Resilience expectations (retries, timeouts, circuit breakers)
- Observability expectations (metrics, logs, alerts)

## Validation and Error Handling
- Input validation rules
- Workflow-level validation
- Integration failure handling
- User-facing error messaging expectations

## Testing Considerations
- Key test areas
- Critical test scenarios
- Integration-sensitive areas
- State transition coverage
- Keep testing guidance at the design level: focus on what must be validated, not full test case specifications.

## Risks / Design Tradeoffs
- Design risks
- Notable tradeoffs made
- Areas requiring confirmation before implementation

## Open Questions
- Unresolved design questions
- Dependencies on product or architecture decisions not yet made
```

---

## Behavior Guidelines

| Situation | Behavior |
|---|---|
| Architecture element is explicit | Design it directly |
| Element is implied but not stated | Design it, label as `[Assumption]` |
| Element is missing or ambiguous | Surface in **Open Questions**, do not invent |
| User asks for code or schemas | Clarify scope; produce only if explicitly requested |
| Architecture covers a DevOps/automation platform | Always include integration design, execution state, config handling, and audit design |
| Architecture covers a UI-facing system | Always include UI/user flow design |
| Multiple possible design choices exist | Note the tradeoff in **Risks / Design Tradeoffs** |

---

## Reference

For large or complex architectures, see `references/design-patterns.md` for common module design patterns and integration design templates.
