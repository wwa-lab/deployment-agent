import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'

const root = new URL('../', import.meta.url)

async function readProjectFile(path) {
  return readFile(new URL(path, root), 'utf8')
}

test('Atlas Execution Center is registered as a shared platform capability', async () => {
  const [routerSource, registrySource] = await Promise.all([
    readProjectFile('src/router/index.ts'),
    readProjectFile('src/config/agentRegistry.ts'),
  ])

  assert.match(routerSource, /atlas-execution-center/)
  assert.match(routerSource, /AtlasExecutionCenterView/)
  assert.match(registrySource, /Atlas Execution Center/)
  const platformRegistry = registrySource.slice(registrySource.indexOf('export const platformCapabilities'))
  assert.match(platformRegistry, /Atlas Execution Center/)
  assert.doesNotMatch(registrySource, /Atlas Execution Center[\s\S]{0,240}category: 'build'/)
})

test('integration API consumes safe v1 projections and only submits review decisions', async () => {
  const [apiSource, typeSource] = await Promise.all([
    readProjectFile('src/api/atlasIntegration.ts'),
    readProjectFile('src/platform/integration/types.ts'),
  ])

  assert.match(apiSource, /baseURL:\s*['"]\/api\/v1\/integration['"]/)
  assert.match(apiSource, /\/tasks/)
  assert.match(apiSource, /approved-input-artifacts/)
  assert.match(apiSource, /\/executions\/\$\{.*\}\/artifacts/)
  assert.match(apiSource, /\/review-decision/)
  assert.match(apiSource, /Idempotency-Key/)
  assert.match(apiSource, /\/telemetry\/capability-usage/)
  assert.doesNotMatch(apiSource, /\/submit|\/fail|\/cancel|progress-events/)

  assert.match(typeSource, /pendingSync:\s*boolean/)
  assert.match(typeSource, /failureReason\??:\s*IntegrationFailure/)
  assert.match(typeSource, /versionDistribution/)
  assert.match(typeSource, /agentModuleId:\s*string/)
  assert.match(typeSource, /projectContext:\s*IntegrationProjectContext/)
  assert.match(typeSource, /approvedInputArtifactIds:\s*string\[\]/)
  assert.match(typeSource, /attemptNumber:\s*number/)
  assert.match(typeSource, /user:\s*IntegrationActor/)
  assert.match(typeSource, /clientVersion:\s*string/)
  assert.match(typeSource, /digest:/)
  assert.match(typeSource, /content:/)
  assert.match(typeSource, /reviewDecisionId:\s*string/)
  assert.match(typeSource, /agentModuleId\?:\s*string/)
  assert.match(typeSource, /meta:\s*\{/)
  assert.doesNotMatch(typeSource, /page:\s*\{/)
  assert.doesNotMatch(typeSource, /repositoryUrl|resultLogs|inputSnapshot|rawLog|sourceCode|accessToken|apiToken/i)
})

test('visibility-aware polling pauses while the page is hidden and always cleans up', async () => {
  const source = await readProjectFile('src/platform/composables/useVisiblePolling.ts')

  assert.match(source, /document\.visibilityState\s*===\s*['"]visible['"]/)
  assert.match(source, /visibilitychange/)
  assert.match(source, /window\.setInterval/)
  assert.match(source, /window\.clearInterval/)
  assert.match(source, /onBeforeUnmount/)
})

test('store refreshes tasks, related execution evidence and usage without overlapping polls', async () => {
  const source = await readProjectFile('src/stores/atlasIntegration.ts')

  assert.match(source, /fetchTasks/)
  assert.match(source, /fetchTaskWorkspace/)
  assert.match(source, /listExecutions/)
  assert.match(source, /listArtifacts/)
  assert.match(source, /fetchCapabilityUsage/)
  assert.match(source, /submitReview/)
  assert.match(source, /refreshInFlight/)
  assert.match(source, /pendingSync/)
  assert.match(source, /failure/)
  assert.match(source, /taskDetail\.value\?\.taskId\s*===\s*selectedTaskId\.value/)
  assert.match(source, /nextTaskId\s*!==\s*selectedTaskId\.value\)\s*clearWorkspace\(\)/)
  assert.match(source, /isAuthorizationLoss\(error\)/)
  assert.match(source, /error\.status\s*===\s*401\s*\|\|\s*error\.status\s*===\s*403\s*\|\|\s*error\.status\s*===\s*404/)
  assert.match(source, /tasks\.value\s*=\s*\[\][\s\S]*selectedTaskId\.value\s*=\s*['"]/)
  assert.match(source, /capabilityUsage\.value\s*=\s*null/)
})

test('Execution Center renders required safe operational views and all telemetry filters', async () => {
  const source = await readProjectFile('src/views/AtlasExecutionCenterView.vue')

  for (const label of [
    'Execution History',
    'Artifacts',
    'Awaiting Review',
    'Capability Usage',
    'Failure reason',
    'Pending sync',
    'Team',
    'Project',
    'Agent',
    'From date',
    'To date',
    'Client type',
    'Skill version distribution',
    'Invocation count',
    'Success rate',
    'Failure rate',
    'Average duration',
    'Users',
  ]) {
    assert.match(source, new RegExp(label, 'i'))
  }

  for (const clientType of ['COPILOT', 'OPENCODE', 'KIRO', 'MANUAL', 'PIPELINE']) {
    assert.match(source, new RegExp(clientType))
  }

  assert.doesNotMatch(source, /authorization|bearer|access[_ -]?token|api[_ -]?token|repositoryUrl|resultLogs|inputSnapshot|raw logs?|full source/i)
})

test('Web renders structured failure state without client-controlled operational text', async () => {
  const viewSource = await readProjectFile('src/views/AtlasExecutionCenterView.vue')

  assert.match(viewSource, /execution\.failureReason\.code/)
  assert.match(viewSource, /execution\.failureReason\?\.retryable/)
  assert.match(viewSource, /Execution cancelled by an authorized operator/)
  assert.doesNotMatch(viewSource, /execution\.failureReason\.message/)
  assert.doesNotMatch(viewSource, /execution\.cancellationReason/)
  assert.doesNotMatch(viewSource, /safeOperationalText\(store\.review\.comment\)/)
  assert.doesNotMatch(viewSource, /safeOperationalText\(artifact\.name/)
  assert.doesNotMatch(viewSource, /\{\{\s*artifact\.kind\s*\}\}/)
  assert.doesNotMatch(viewSource, /client detail hidden/)
  assert.match(viewSource, /protected audit record/)
  assert.match(viewSource, /Artifact \{\{ artifact\.artifactId\.slice/)
})

test('workspace shell releases the fixed sidebar width on mobile viewports', async () => {
  const shellSource = await readProjectFile('src/views/WorkspaceLayout.vue')
  const mobileStyles = shellSource.slice(shellSource.lastIndexOf('@media (max-width: 768px)'))

  assert.match(mobileStyles, /\.workspace\s*\{[^}]*flex-direction:\s*column/s)
  assert.match(mobileStyles, /\.sidebar\s*\{[^}]*width:\s*100%/s)
  assert.match(mobileStyles, /\.main-area\s*\{[^}]*min-width:\s*0/s)
  assert.match(mobileStyles, /\.topbar\s*\{[^}]*flex-direction:\s*column/s)
})
