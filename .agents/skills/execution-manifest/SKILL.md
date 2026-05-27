---
name: execution-manifest
description: Use this skill when preparing work for a coding agent, remote agent, automation, CI workflow, or tool handoff. Creates a pinned execution manifest so agents consume explicit context instead of guessing latest sources, branches, documents, constraints, outputs, and verification commands.
---

# Execution Manifest

Use this skill to package a task for deterministic agent execution.

## Principle

Agents consume manifests, not guesses. Resolve the latest intended inputs first, pin their versions, then execute against the pinned manifest.

## Default Path

```text
docs/00-context/execution-manifests/{manifest-id}.yaml
```

Use another path if the project already has a manifest convention.

## Workflow

1. Identify the execution goal.
2. Resolve source references:
   - repo URL or local path
   - branch/ref
   - commit SHA when available
   - relevant document paths
   - ticket/source references
3. Pin inputs:
   - approved docs
   - ADRs
   - constraints
   - expected outputs
4. Define permissions and boundaries:
   - allowed writes
   - forbidden files
   - external actions needing approval
5. Define verification:
   - build/test commands
   - quality gates
   - expected evidence
6. Write the manifest and point the agent/tool at it.

## Manifest Template

Use `references/execution-manifest-template.yaml`.

## Good Manifest Rules

- Prefer exact refs over "latest".
- Include every document the agent is expected to honor.
- Include output expectations, not just input context.
- Include rollback or stop conditions for risky work.
- Treat missing pins as a risk and call them out.
