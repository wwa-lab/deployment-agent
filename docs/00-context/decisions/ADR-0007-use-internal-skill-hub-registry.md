# ADR-0007: Use An Internal Metadata Registry For Skill Hub

## Status

Accepted

## Date

2026-05-31

## Context

Skill Hub needs to help WWA users discover, classify, and maintain skills. The platform already supports authenticated sessions, guest read-only preview, platform navigation, audit logging, and database-backed shared capabilities. Skill definitions may also exist in local global skill directories, project-local files, or future Git repositories, but those sources have different permissions, machine-local paths, and synchronization semantics.

## Decision

Implement Skill Hub v1 as an internal persisted metadata registry owned by the WWA Platform. Authenticated non-guest users may create and edit registry entries. Guest viewers may browse only. Version management is limited to editable metadata fields: current version and version notes.

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| Scan local skill directories | Local paths vary by developer machine and would expose environment-specific state. |
| Synchronize with Git | Useful later, but requires credential, conflict, and release governance not needed for v1. |
| Store full skill file content | This turns Skill Hub into a content editor and raises ownership/version rollback questions. |
| Restrict edits to DEVOPS_ADMIN | The requested collaboration model allows all authenticated users to maintain metadata. |

## Consequences

### Positive

- Skill Hub is deterministic across environments.
- The feature fits existing platform CRUD, audit, and guest read-only patterns.
- The data model is simple enough for immediate browsing and governance.

### Negative

- Registry entries can drift from actual `SKILL.md` files until sync is introduced.
- Version history is not preserved beyond the current metadata.
- Categories are free-text in v1 and may need normalization later.

## Review Triggers

Revisit when Skill Hub needs filesystem scanning, marketplace installation, Git synchronization, version rollback, or controlled category administration.
