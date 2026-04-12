-- V13: Backfill legacy Request rows where agent IS NULL to 'deployment-agent'.
-- Resolves P-01 (build-agent-tasks.md §10): ensures all pre-agent-column rows
-- remain visible in the Deployment Agent workspace after v3 agent filtering.

UPDATE DA_REQUEST
   SET agent = 'deployment-agent'
 WHERE agent IS NULL;
