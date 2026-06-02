# Data Model: Skill Hub

## Entity: Skill

| Field | Type | Notes |
|---|---|---|
| `id` | String UUID | Stable identifier. |
| `name` | String | Required, max 160. |
| `description` | String | Required, max 2000. |
| `category` | String | Required, max 80. |
| `tags` | JSON string list | Optional, normalized and deduplicated. |
| `owner` | String | Required, max 160. |
| `status` | Enum | `ACTIVE`, `DRAFT`, `DEPRECATED`, `ARCHIVED`. |
| `currentVersion` | String | Required, max 40. |
| `versionNotes` | String | Optional, max 2000. |
| `contentSourceType` | String | `FILE_PATH` for v2. |
| `sourcePath` | String | System-generated repo-relative Markdown path under the Skill Hub storage directory. |
| `contentSha256` | String | SHA-256 of current version snapshot. |
| `currentVersionId` | String UUID | Points to the current snapshot row. |
| `lastIndexedAt` | Instant | Timestamp when the current snapshot was created. |
| `createdBy` | String | Authenticated user ID. |
| `createdAt` | Instant | Set by persistence layer. |
| `updatedBy` | String | Authenticated user ID. |
| `updatedAt` | Instant | Set by persistence layer. |
| `version` | Long | Optimistic locking column. |

## Entity: Skill Version

| Field | Type | Notes |
|---|---|---|
| `id` | String UUID | Stable version snapshot identifier. |
| `skillId` | String UUID | Owning skill. |
| `version` | String | User-facing version label. |
| `versionNotes` | String | Optional release notes. |
| `sourcePath` | String | Repo-relative Skill Hub file path containing the full version history. |
| `contentSnapshot` | CLOB | Immutable Markdown/plain text version body captured at version creation. |
| `contentSha256` | String | SHA-256 for the captured content. |
| `createdBy` | String | Authenticated user ID. |
| `createdAt` | Instant | Set by persistence layer. |

## Indexes

- Status/category lookup.
- Updated timestamp ordering.
- Name lookup for browsing.
- Skill version lookup by skill and created timestamp.
