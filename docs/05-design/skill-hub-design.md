# Design: Skill Hub

## Summary

Skill Hub follows the platform shared capability pattern already used by Access Management and Agent Contribute Dashboard: a Spring platform controller, service-owned validation, persisted domain entity, audit logging, Vue view, platform API client wrapper, and workspace navigation entry.

## Backend Design

- Create `SkillStatus` enum with `ACTIVE`, `DRAFT`, `DEPRECATED`, and `ARCHIVED`.
- Create `SkillHubSkill` entity mapped to `SKILL_HUB_SKILL`.
- Store `tags` as a JSON list using the existing `StringListJsonAttributeConverter`.
- Implement service-level normalization:
  - trim required strings
  - reject blank required fields
  - deduplicate non-blank tags
  - default missing status to `DRAFT`
- Implement in-memory search/filter after loading ordered rows, matching existing small platform registry patterns.
- Write `skill_hub_create` and `skill_hub_update` audit actions.

## Frontend Design

- Add `Skill Hub` to `platformCapabilities`.
- Add route `/wwa/skill-hub`.
- Render a work-focused management page with:
  - summary metrics
  - search/category/status filters
  - catalog table
  - detail panel
  - create/edit modal
- Hide mutation controls for guest users.

## Error Handling

- Backend validation errors return existing platform validation responses.
- Missing skill IDs return existing not-found responses.
- Frontend displays API error messages near the form or page toolbar.
