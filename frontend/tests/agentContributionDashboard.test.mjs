import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'

const root = new URL('../', import.meta.url)

async function readProjectFile(path) {
  return readFile(new URL(path, root), 'utf8')
}

test('agent contribution dashboard models Qilianshan SDLC as seven stages', async () => {
  const source = await readProjectFile('src/config/agentContributionDashboard.json')
  const dashboard = JSON.parse(source)

  assert.equal(dashboard.stages.length, 7)

  for (const stage of dashboard.stages) {
    assert.ok(stage.key)
    assert.ok(stage.name)
    assert.ok(stage.focus)
    assert.ok(stage.implementationStatus)
    assert.ok(stage.implementationLabel)
    assert.ok(stage.implementationNote)
    assert.ok(stage.agentOwner)
    assert.ok(stage.resourceLinks.length >= 2)
    assert.ok(stage.workstreams.length >= 1)

    for (const resource of stage.resourceLinks) {
      assert.ok(resource.label)
      assert.ok(resource.description)
      assert.match(resource.href, /^https:\/\/confluence\.example\.com\//)
    }

    for (const workstream of stage.workstreams) {
      assert.ok(workstream.name)
      assert.ok(workstream.agentName)
      assert.ok(workstream.subAgentOwner)
      assert.ok(workstream.processOwner)
      assert.ok(workstream.technicalLeader)
      assert.ok(workstream.contribution)
      assert.ok(workstream.coBuild.length >= 1)
    }
  }
})

test('dashboard distinguishes current implementation status by stage', async () => {
  const source = await readProjectFile('src/config/agentContributionDashboard.json')
  const dashboard = JSON.parse(source)

  const statusByStage = Object.fromEntries(
    dashboard.stages.map((stage) => [stage.name, stage.implementationStatus]),
  )

  assert.equal(statusByStage.Discovery, 'not-implemented')
  assert.equal(statusByStage.Testing, 'in-progress')
  assert.equal(statusByStage.Maintenance, 'not-implemented')
  assert.equal(dashboard.stages.filter((stage) => stage.implementationStatus === 'implemented').length, 4)
  assert.equal(dashboard.stages.filter((stage) => stage.implementationStatus === 'in-progress').length, 1)
  assert.equal(
    dashboard.stages.filter((stage) => stage.implementationStatus === 'not-implemented').length,
    2,
  )
})

test('dashboard is wired as a read-only platform page, not an agent registration flow', async () => {
  const [routerSource, registrySource, viewSource] = await Promise.all([
    readProjectFile('src/router/index.ts'),
    readProjectFile('src/config/agentRegistry.ts'),
    readProjectFile('src/views/AgentContributionDashboardView.vue'),
  ])

  assert.match(routerSource, /agent-contribute-dashboard/)
  assert.match(registrySource, /Agent Contribute Dashboard/)
  assert.doesNotMatch(viewSource, /register|registration|create agent/i)
})

test('dashboard presents internal role attribution without opaque scores', async () => {
  const [viewSource, dataSource, apiSource] = await Promise.all([
    readProjectFile('src/views/AgentContributionDashboardView.vue'),
    readProjectFile('src/config/agentContributionDashboard.json'),
    readProjectFile('src/api/agentContributionDashboard.ts'),
  ])

  assert.match(viewSource, /Qilianshan SDLC/)
  assert.match(viewSource, /SDLC Coverage Map/)
  assert.match(viewSource, /SDLC Coverage Matrix/)
  assert.match(viewSource, /coverage-table/)
  assert.match(viewSource, /Filter by status/)
  assert.match(viewSource, /No stages match the selected status/)
  assert.match(viewSource, /Contribution Items/)
  assert.match(viewSource, /Implemented/)
  assert.match(viewSource, /In Progress/)
  assert.match(viewSource, /Backlog/)
  assert.match(viewSource, /Not Implemented/)
  assert.match(viewSource, /Implementation Status/)
  assert.match(viewSource, /Agent Owner/)
  assert.match(viewSource, /Sub-agent Owner/)
  assert.match(viewSource, /Process Owner/)
  assert.match(viewSource, /Technical Leader/)
  assert.match(viewSource, /Co-Build/)
  assert.match(viewSource, /Co-Build Partners/)
  assert.match(viewSource, /Covered by/)
  assert.match(viewSource, /Confluence Links/)
  assert.match(dataSource, /Guideline/)
  assert.match(dataSource, /Feedback/)
  assert.match(viewSource, /DEVOPS_ADMIN can edit stage status/)
  assert.match(viewSource, /Save Status/)
  assert.match(apiSource, /agent-contribute-dashboard\/statuses/)
  assert.doesNotMatch(viewSource, /Score/)
  assert.doesNotMatch(viewSource, /score-badge/)
  assert.doesNotMatch(viewSource, /Contributors/)
  assert.doesNotMatch(viewSource, /sub-?mountain|small mountain/i)
  assert.doesNotMatch(dataSource, /sub-?mountain|small mountain/i)
  assert.doesNotMatch(viewSource, /\p{Script=Han}/u)
  assert.doesNotMatch(dataSource, /\p{Script=Han}/u)
})
