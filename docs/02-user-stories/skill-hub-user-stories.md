# User Stories: Skill Hub

## SH-01: Browse Skill Catalog

As a WWA user,  
I want to browse registered skills in one platform page,  
so that I can discover reusable capabilities without knowing where each skill is maintained.

### Acceptance Criteria

1. Given I am authenticated, when I open Skill Hub, then I can see the skill catalog and an empty state if no skills exist.
2. Given skills exist, when I search or filter by category/status, then the catalog updates to matching skills.
3. Given I select a skill, when the detail panel opens, then I can see category, tags, owner, status, version, notes, and audit metadata.

## SH-02: Maintain Skill Metadata

As an authenticated WWA user,  
I want to create and update skill metadata,  
so that the platform registry stays current as skills evolve.

### Acceptance Criteria

1. Given I am not a guest, when I create a skill with valid required fields, then the registry persists it and records my user ID.
2. Given I edit an existing skill, when I save valid metadata, then the skill version/category/status fields update.
3. Given invalid required fields, when I submit the form, then the system rejects the request with a validation error.

## SH-03: Protect Read-Only Guest Mode

As a guest viewer,  
I want to browse Skill Hub without changing data,  
so that preview mode remains safe.

### Acceptance Criteria

1. Given I am a guest, when I open Skill Hub, then mutation controls are disabled or hidden.
2. Given I am a guest, when a write request is attempted, then the platform rejects it as forbidden.

## SH-04: Audit Skill Registry Changes

As a platform operator,  
I want Skill Hub changes audited,  
so that metadata updates are attributable.

### Acceptance Criteria

1. Given a skill is created, when the operation succeeds, then an audit entry records the skill ID and name.
2. Given a skill is updated, when the operation succeeds, then an audit entry records old and new metadata.
