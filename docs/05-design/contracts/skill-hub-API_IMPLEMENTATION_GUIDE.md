# API Implementation Guide: Skill Hub

## Base Path

`/api/platform/skill-hub`

## List Skills

`GET /skills`

Query parameters:

- `query` optional string
- `category` optional string
- `status` optional `ACTIVE | DRAFT | DEPRECATED | ARCHIVED`
- `page` optional integer, default `0`
- `size` optional integer, default `20`

Response: `PaginatedResponseDto<SkillHubSkillDto>`.

## Get Skill

`GET /skills/{id}`

Response: `SkillHubSkillDto`.

## Create Skill

`POST /skills`

Request:

```json
{
  "name": "Code Review",
  "description": "Reviews code changes against project standards.",
  "category": "Engineering",
  "tags": ["review", "quality"],
  "owner": "Platform Team",
  "status": "ACTIVE",
  "currentVersion": "1.0.0",
  "versionNotes": "Initial registry entry",
  "content": "Use this skill to review code changes against project standards."
}
```

Response: `SkillHubSkillDto`. The server creates a Markdown skill file under the configured Skill Hub storage directory, writes the initial version into that file, and returns the generated `sourcePath`.

## Update Skill

`PUT /skills/{id}`

Uses the same request shape as create. Updating metadata does not create a new version snapshot; version history changes only through Create Version.

## Create Version

`POST /skills/{id}/versions`

Request:

```json
{
  "version": "1.1.0",
  "versionNotes": "Refresh source-backed instructions.",
  "content": "Updated skill instructions for this version."
}
```

Response: `SkillHubSkillDto.VersionDetail`, including `contentSnapshot` and `contentSha256`. The server appends the version to the same generated skill file.

## Get Version

`GET /skills/{id}/versions/{versionId}`

Response: `SkillHubSkillDto.VersionDetail`.

## File Storage Rules

- Skill files are created by the backend, one file per skill.
- Each skill file contains metadata and all version history blocks.
- Default storage location is `${user.dir}/skills`; tests override this to `target/skill-hub-test-files`.
- Optional source path overrides are validated as repository-relative Markdown paths for maintenance/backfill only.
