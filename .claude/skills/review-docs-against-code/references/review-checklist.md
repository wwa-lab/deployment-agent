# Review Checklist

Use this checklist to choose the smallest set of repo artifacts needed to verify a document.

## Evidence Priority

1. Running behavior, tests, and contract tests
2. Build and runtime manifests
3. Source files implementing the behavior
4. Secondary docs and comments

If items at different levels disagree, trust the higher-priority evidence unless the user says the
repo is intentionally ahead of implementation.

## High-Risk Review Areas

### Setup and developer workflow

Questions:

- Do install, run, test, lint, and build commands exist exactly as written?
- Does the documented package manager match the repo?
- Are prerequisites and working-directory assumptions still true?

Check:

- `package.json`
- workspace manifests
- `pom.xml`
- `Makefile`
- CI config
- Dockerfiles

Common drift patterns:

- `npm` vs `pnpm` vs `yarn`
- old script names after refactors
- commands that only work from a subdirectory
- outdated local ports

### Paths, modules, and naming

Questions:

- Do referenced files, directories, classes, components, and packages still exist?
- Have module names drifted after a rename or reorganization?

Check:

- `rg --files`
- import paths
- package names
- router and controller names

Common drift patterns:

- renamed Vue views or components
- stale Java package names
- moved docs or SQL files
- examples that still reference deleted folders

### API surface and contracts

Questions:

- Do documented routes, methods, auth headers, payload fields, and status names match code?
- Are request and response examples still valid?

Check:

- controllers or routers
- DTO and contract classes
- frontend API client modules
- contract tests

Common drift patterns:

- path prefixes changed
- renamed DTO fields
- auth requirements missing from docs
- response examples omit newly required fields

### Configuration and runtime behavior

Questions:

- Are env vars, ports, profiles, config keys, and startup assumptions correct?
- Do docs mention feature flags or secrets that no longer exist?

Check:

- `application*.properties`
- `*.env.example`
- frontend build config
- config modules

Common drift patterns:

- old profile names
- renamed env vars
- docs claiming a default port that changed
- examples that omit required local settings

### States, roles, and workflow behavior

Questions:

- Do docs use the same enum values, statuses, permissions, and transitions as the code?
- Does the documented happy path ignore important edge cases or failure states?

Check:

- enums
- state machines
- permission resolvers
- audit and review flow code

Common drift patterns:

- removed status values still shown in docs
- incomplete transition tables
- outdated role names
- retry or approval behavior described but not implemented

### Architecture and boundaries

Questions:

- Do docs describe the current module boundaries and ownership rules?
- Are responsibilities placed in the right layer according to code?

Check:

- current folder structure
- handler/controller layers
- domain services
- repository or persistence boundaries
- integration adapters

Common drift patterns:

- architecture docs lag behind refactors
- persistence logic moved but docs still describe old layer
- adapter names changed without doc updates

### Examples and snippets

Questions:

- Are curl commands, SQL snippets, imports, and code examples still runnable or at least accurate?
- If a snippet is illustrative only, does the doc make that clear?

Check:

- snippet paths and identifiers
- route strings
- sample JSON fields
- shell command assumptions

Common drift patterns:

- missing headers in curl examples
- examples referencing deleted migrations
- copy-pasted JSON that no longer matches DTOs

## Suggested Search Patterns

Use targeted searches based on the doc claim:

- Setup commands: `rg -n "pnpm|npm|yarn|mvn|make|docker|vite|spring-boot" README.md docs tasks`
- API paths: `rg -n "/api/|@RequestMapping|@GetMapping|@PostMapping|createRouter" src frontend tests`
- Config keys and ports: `rg -n "PORT|PROFILE|application-|server.port|VITE_" src frontend docs`
- State and roles: `rg -n "enum|Status|Role|Permission|StateMachine|transition" src docs`
- File or module names: `rg --files | rg "name-or-path-fragment"`

## Reporting Guidance

- Every important finding should name both the doc claim and the code evidence
- When you cannot confirm a claim, say `Unable to verify` instead of guessing
- If a doc is mostly correct but one section is stale, keep the finding scoped to that section
- Prioritize findings that would mislead a human into doing the wrong thing
