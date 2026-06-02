# Feature Specification: Skill Hub

> **Source stories:** SH-01 through SH-04  
> **Spec status:** Draft  
> **Last updated:** 2026-05-31

## Overview

Skill Hub is a WWA Platform shared capability that maintains an internal registry of skill metadata. It supports discovery, categorization, and metadata-only version management.

## Actors

| Actor | Role |
|---|---|
| WWA User | Browses, creates, and edits skill registry entries. |
| Guest Viewer | Browses Skill Hub in read-only preview mode. |
| Platform Operator | Reviews audit history for skill registry changes. |

## Functional Requirements

- **SH-FR-01**: The platform shall expose Skill Hub at `/wwa/skill-hub`.
- **SH-FR-02**: The catalog shall list persisted skills with name, description, category, tags, owner, status, current version, and update metadata.
- **SH-FR-03**: The catalog shall support search across name, description, owner, version, category, and tags.
- **SH-FR-04**: The catalog shall support category and status filters.
- **SH-FR-05**: Authenticated non-guest users shall be able to create skill entries.
- **SH-FR-06**: Authenticated non-guest users shall be able to update skill metadata, category, tags, owner, status, current version, and version notes.
- **SH-FR-07**: Guest users shall be blocked from write operations by the platform read-only guard.
- **SH-FR-08**: Create and update operations shall write audit entries.
- **SH-FR-09**: Skill Hub v1 shall not store skill file contents, scan local directories, synchronize Git repositories, install skills, maintain version snapshots, or roll back versions.

## Data Requirements

| Entity | Description | Key Attributes |
|---|---|---|
| Skill | Persisted skill registry entry. | ID, name, description, category, tags, owner, status, current version, version notes, created/updated metadata. |

Valid statuses:

- `ACTIVE`
- `DRAFT`
- `DEPRECATED`
- `ARCHIVED`

## API Requirements

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/platform/skill-hub/skills` | List skills with search, category, status, and pagination. |
| GET | `/api/platform/skill-hub/skills/{id}` | Read one skill. |
| POST | `/api/platform/skill-hub/skills` | Create a skill. |
| PUT | `/api/platform/skill-hub/skills/{id}` | Update skill metadata. |

## Out of Scope

- Skill file editing.
- Runtime skill installation.
- Filesystem scanning.
- Git synchronization.
- Version rollback and historical version snapshots.
