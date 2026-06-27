# Atlas Engineering Delivery Hub - Deployment Documentation Index

**Category:** Tool
**Stage:** M6 Deployment
**Status:** Open-collaboration package
**Last updated:** 2026-06-27

This index collects the M6 Deployment Tool packaging materials for reviewers and contributors.

## Entry Points

- [English README](../README.md)
- [Chinese README](../README.zh-CN.md)
- [Contribution guide](../CONTRIBUTING.md)
- [Open collaboration submission](open-collaboration-submission.md)
- [Chinese open collaboration submission](open-collaboration-submission.zh-CN.md)
- [Deployment pitch](atlas-engineering-delivery-hub-deployment-pitch.md)

## Visuals

- [M6 lifecycle positioning diagram](assets/atlas-deployment-lifecycle-positioning.svg)
- [M6 lifecycle positioning Mermaid source](assets/atlas-deployment-lifecycle-positioning.mmd)
- [Deployment tool workflow diagram](assets/atlas-deployment-tool-workflow.svg)
- [Deployment tool workflow Mermaid source](assets/atlas-deployment-tool-workflow.mmd)
- [Upstream/downstream relationship diagram](assets/atlas-deployment-upstream-downstream.svg)
- [Upstream/downstream Mermaid source](assets/atlas-deployment-upstream-downstream.mmd)

## Demo And Samples

- [Sanitized mini output package](samples/atlas-deployment-tool-mini-output/README.md)
- [Sample release input](samples/atlas-deployment-tool-mini-output/sample-release-input.json)
- [Sample task output](samples/atlas-deployment-tool-mini-output/sample-task-output.json)
- [Sample audit trail](samples/atlas-deployment-tool-mini-output/sample-audit-trail.json)
- [Sample rollback handoff checklist](samples/atlas-deployment-tool-mini-output/sample-rollback-checklist.md)

## SDD Traceability

- [M6 traceability index](00-context/atlas-engineering-delivery-hub-deployment-traceability.md)
- [Requirements](01-requirements/atlas-engineering-delivery-hub-deployment-requirement.md)
- [User stories](02-user-stories/atlas-engineering-delivery-hub-deployment-user-stories.md)
- [Specification](03-spec/atlas-engineering-delivery-hub-deployment-spec.md)
- [Architecture](04-architecture/atlas-engineering-delivery-hub-deployment-architecture.md)
- [Data flow](04-architecture/atlas-engineering-delivery-hub-deployment-data-flow.md)
- [Data model](04-architecture/atlas-engineering-delivery-hub-deployment-data-model.md)
- [Design](05-design/atlas-engineering-delivery-hub-deployment-design.md)
- [Tasks](06-tasks/atlas-engineering-delivery-hub-deployment-tasks.md)

## Runtime References

- [Current implementation baseline](wwa-agent-workspace-hub-current-baseline.md)
- [Deployment Agent requirement baseline](01-requirements/requirement.md)
- [Deployment Agent spec baseline](03-spec/spec.md)
- [Platform and Deployment architecture](04-architecture/architecture.md)
- [Detailed design baseline](05-design/design.md)
- [UAT runbook](UAT_RUNBOOK.md)
- [Oracle current schema](sql/ORACLE_CURRENT_SCHEMA.sql)

## Validation

Recommended package checks:

```bash
git diff --check
node scripts/check-markdown-links.mjs
```

Runtime checks for code changes:

```bash
mvn test
cd frontend && npm run build
```
