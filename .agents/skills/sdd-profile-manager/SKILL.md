---
name: sdd-profile-manager
description: Use this skill when a project needs to define, choose, migrate, or review a Spec Driven Development profile. Applies when SDD should not be hard-coded to one Java-oriented document chain, when a repo needs project-specific document stages, review gates, traceability rules, skill routing, or cross-tool SDD conventions for Codex, Claude Code, OpenCode, or similar IDE agents.
---

# SDD Profile Manager

Use this skill to choose or define the SDD profile for a project before running the document pipeline.

## Purpose

Different project types need different SDD chains. Do not force every repo into one Java/Spring document flow. A profile defines the document stages, paths, required gates, skill bindings, and traceability rules for a project.

## Profile File

Prefer:

```text
docs/00-context/sdd-profile.md
```

If the repo already has a profile location, reuse it.

## Workflow

1. Inspect repo instructions and docs:
   - `AGENTS.md`
   - `CLAUDE.md`
   - `OPENCODE.md`
   - `docs/00-context/`
   - existing `docs/01-*` through `docs/06-*`
2. Classify the project type:
   - `standard-java-sdd`
   - `frontend-spa-sdd`
   - `agentic-control-plane-sdd`
   - `docs-only-sdd`
   - `migration-sdd`
   - custom profile
3. Define or update the profile:
   - document stages
   - required artifacts
   - optional artifacts
   - default output paths
   - review gates before implementation
   - skill routing
   - traceability rules
4. If an existing SDD chain conflicts with the chosen profile, document the migration path instead of silently changing paths.
5. Link the profile from root agent instructions when it becomes a project rule.

## Minimum Profile Template

```markdown
# SDD Profile: [profile-name]

## Applies To

[Project types or conditions]

## Document Chain

| Order | Stage | Required | Default Path | Skill |
|---|---|---|---|---|
| 1 | Context | Yes | `docs/00-context/` | `context-engineering-adr` |
| 2 | Requirements | Yes | `docs/01-requirements/` | `req-to-user-story` |

## Gates

- [Gate before implementation]

## Traceability

- [How requirements, stories, specs, design, tasks, code, tests, and ADRs link]

## Tool Routing

- Codex: [rule]
- Claude Code: [rule]
- OpenCode: [rule]
```

## Default Profiles

### `standard-java-sdd`

Use for Spring/Java enterprise applications. Required stages:

1. Context
2. Requirements
3. User stories
4. Specification
5. Architecture
6. Data flow
7. Data model
8. Design
9. API implementation guide
10. Tasks

### `frontend-spa-sdd`

Use for UI-heavy apps. Required stages:

1. Context
2. Requirements
3. User stories
4. Specification
5. UX / interaction architecture
6. Design system or visual design
7. Component design
8. Tasks

### `docs-only-sdd`

Use for documentation, knowledge-base, article, or taxonomy projects. Required stages:

1. Context
2. Source inventory
3. Information architecture
4. Draft / rewrite plan
5. Review checklist

### `migration-sdd`

Use for modernization or migration projects. Required stages:

1. Context
2. Current-state inventory
3. Target-state spec
4. Gap analysis
5. Migration architecture
6. Cutover / rollback design
7. Tasks

## Quality Rules

- Profiles are durable project context, not per-task scratch notes.
- If the profile changes, record the reason in an ADR.
- Do not start implementation until the profile's required gates pass.
