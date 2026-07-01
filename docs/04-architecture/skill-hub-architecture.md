# Architecture: Skill Hub

## Summary

Skill Hub is implemented as a platform shared capability under `/api/platform/skill-hub` and `/wwa/skill-hub`. It owns a persisted metadata registry and does not integrate with local skill directories or external repositories in v1.

## Backend Architecture

- `SkillHubSkill` is the persistent aggregate for skill metadata.
- `SkillHubService` owns validation, search/filter behavior, mutation, and audit context creation.
- `SkillHubController` exposes platform APIs and maps domain objects to DTOs.
- Audit events use existing `AuditLoggerService` and platform audit context.
- Guest write protection is delegated to the existing `GuestReadOnlyFilter`.

## Frontend Architecture

- `SkillHubView.vue` is a platform capability view rendered inside `WorkspaceLayout`.
- `skillHub.ts` wraps the platform API.
- The view keeps search/filter state locally and requests filtered pages from the backend.
- Create/edit forms submit the same metadata shape used by the API.

## Decision Links

- [ADR-0007](../00-context/decisions/ADR-0007-use-internal-skill-hub-registry.md)
