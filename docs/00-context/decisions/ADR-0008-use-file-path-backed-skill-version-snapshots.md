# ADR-0008: Use Project File-Backed Skill Version History

## Status

Accepted

## Context

Skill Hub v1 stored registry metadata only. The first V2 iteration referenced existing Markdown files and copied snapshots into the database, but users expect Skill Hub to create and own the actual skill file in the project.

## Decision

Skill Hub creates one Markdown file per skill under the configured project storage directory. That file contains the skill metadata and all version history blocks. The database remains a query/index layer for catalog metadata, current version pointers, hashes, audit context, and API responses.

## Consequences

- Skill content is visible and reviewable as normal project files.
- Creating a skill creates a file; creating a version appends to the same file.
- Skill Hub can still display and audit version content through indexed database rows.
- Optional path overrides must be validated to stay inside the repository and point to Markdown files.
- Automatic directory scanning, rollback, Git sync, and marketplace installation remain future work.

## Review Triggers

- Skill Hub needs to install skills into agent runtimes.
- Skills move to external repositories or object storage.
- Users need rollback or diff tooling across versions.
