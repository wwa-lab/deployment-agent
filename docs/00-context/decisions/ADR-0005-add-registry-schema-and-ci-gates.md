# ADR-0005: Add Registry, Execution Manifest Schema, And CI Gates For Enforceable Workflow Checks

## Status

Accepted

## Date

2026-05-27

## Context

The Agentic SDLC workflow now has global skills, an orchestrator, SDD profile, discipline profile, doctor script, and cross-tool routing. The next gap is enforceability. Without a registry, global skill versions and sync targets can drift. Without a manifest schema, agent handoff manifests remain informal templates. Without CI, the workflow is mostly an AI behavior constraint rather than a repeatable programmatic gate.

## Decision

Add three enforceability assets:

- `docs/00-context/agentic-sdlc-registry.md` to track global skill versions, canonical sources, installed targets, and supporting assets.
- `docs/00-context/execution-manifest.schema.json` to define the machine-checkable execution manifest contract.
- `.github/workflows/agentic-sdlc.yml` to run Agentic SDLC doctor, secret scan, backend tests, and frontend build.

Extend `scripts/agentic-sdlc-doctor.sh` so it checks registry coverage, schema validity, manifest template shape, ADR index coverage, OpenCode routing, global skill sync, and CI workflow coverage.

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| Keep registry details in prose only | Versions, sources, and sync targets are easy to lose without a dedicated registry. |
| Rely on YAML examples without a schema | Agents and humans can create incomplete manifests without noticing. |
| Keep CI gates as documentation only | Critical gates need programmatic enforcement, not only AI behavior guidance. |
| Add all possible gates immediately | Starting with doctor, secrets, backend tests, and frontend build gives useful coverage without overfitting. |

## Consequences

### Positive

- Global skill state becomes inspectable and versioned.
- Execution manifests have a stable contract.
- CI can catch missing workflow assets, secret leaks, backend regressions, and frontend build failures.
- Doctor output becomes a stronger signal for workflow health.

### Negative

- The workflow adds another CI job and external secret-scan action.
- Registry and schema must be maintained when global assets change.

### Neutral / Operational

- Run `scripts/agentic-sdlc-doctor.sh` locally before pushing workflow changes.
- Update the registry when skill contracts change.
- Future coverage, dependency, and manifest instance validation can build on this foundation.

## Review Triggers

Revisit this decision when:

- Execution manifests become common enough to require full YAML schema validation in CI.
- Coverage or dependency scanning becomes mandatory.
- Global skills are packaged externally and versioned outside this repository.
- CI runtime or action dependencies become too costly for routine changes.

## Related Documents

- [Agentic SDLC registry](../agentic-sdlc-registry.md)
- [Execution manifest schema](../execution-manifest.schema.json)
- [Agent discipline profile](../agent-discipline-profile.md)
- [ADR-0004](ADR-0004-add-agentic-sdlc-orchestrator-and-discipline-profile.md)
