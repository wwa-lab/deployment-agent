# Requirements: Skill Hub

## Status

Draft

## Last Updated

2026-05-31

## Summary

Skill Hub is a WWA Platform shared capability for displaying and managing skill metadata. It gives authenticated users one place to browse skills, classify them, and maintain lightweight version metadata without scanning local skill files or synchronizing with Git.

## Requirements

| ID | Requirement |
|---|---|
| SH-REQ-01 | Users can open Skill Hub from the WWA platform navigation. |
| SH-REQ-02 | Users can browse a persisted registry of skill metadata. |
| SH-REQ-03 | Users can search skills by name, description, owner, version, category, and tags. |
| SH-REQ-04 | Users can filter skills by category and lifecycle status. |
| SH-REQ-05 | Authenticated non-guest users can create a skill entry. |
| SH-REQ-06 | Authenticated non-guest users can edit skill metadata, category, current version, and version notes. |
| SH-REQ-07 | Guest users can browse Skill Hub but cannot create or update entries. |
| SH-REQ-08 | Create and update operations are audited. |
| SH-REQ-09 | Version management is metadata-only in v1; no rollback, file content storage, directory scanning, marketplace installation, or Git sync is included. |

## Acceptance Criteria

- Skill Hub appears as a platform capability and routes to `/wwa/skill-hub`.
- Empty, loading, success, validation error, and save error states are visible in the UI.
- Backend validates required fields and supported statuses.
- Skill list ordering is predictable, newest updated first.
- Create/update responses include creator/updater and timestamps.
