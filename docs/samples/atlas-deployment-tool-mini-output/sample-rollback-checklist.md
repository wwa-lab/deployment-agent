# Sample Rollback Handoff Checklist

**Status:** Synthetic template
**Scope:** Human handoff checklist for failed or rejected deployment tasks.

## Release Context

- Workflow identifier: `DEMO-RELEASE-001`
- Stage: `SIT`
- Application: `SAMPLE_APP`
- SNOW group: `SAMPLE_RELEASE_GROUP`
- Release owner: `sample.release.owner`

## Before Rollback

- [ ] Confirm the failed task and latest execution attempt.
- [ ] Review the task result summary and external log URL.
- [ ] Confirm whether the task was rejected, failed, or marked for rerun.
- [ ] Notify the release owner and affected task owner.
- [ ] Confirm upstream build/testing evidence remains valid or mark it stale.

## Rollback / Recovery

- [ ] Follow the team's approved rollback runbook outside this sample.
- [ ] Record the rollback or remediation result in the task result summary.
- [ ] Use `Rerun` only when the task should return to `Ready_For_Execution`; start the next execution attempt explicitly.
- [ ] Use `Reject` when the release should stop pending rework.
- [ ] Use `Archive` only for controlled cleanup of a rundown that should no longer appear in default views.

## After Rollback

- [ ] Verify the Release Flow status.
- [ ] Confirm audit entries exist for the failure, decision, and recovery action.
- [ ] Attach post-release learning to the M7 Maintenance feedback path.
- [ ] Create follow-up work in the appropriate upstream stage if the root cause belongs to Build, Testing, or Discovery.
