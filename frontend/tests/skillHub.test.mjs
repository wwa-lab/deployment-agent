import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'

const root = new URL('../', import.meta.url)

async function readProjectFile(path) {
  return readFile(new URL(path, root), 'utf8')
}

test('skill hub is wired as a platform capability route', async () => {
  const [routerSource, registrySource] = await Promise.all([
    readProjectFile('src/router/index.ts'),
    readProjectFile('src/config/agentRegistry.ts'),
  ])

  assert.match(routerSource, /path: 'skill-hub'/)
  assert.match(routerSource, /name: 'wwa-skill-hub'/)
  assert.match(routerSource, /SkillHubView\.vue/)
  assert.match(registrySource, /key: 'skill-hub'/)
  assert.match(registrySource, /label: 'Skill Hub'/)
  assert.match(registrySource, /to: '\/wwa\/skill-hub'/)
})

test('skill hub api exposes list, detail, create, update, and version calls', async () => {
  const apiSource = await readProjectFile('src/api/skillHub.ts')

  assert.match(apiSource, /export type SkillStatus = 'ACTIVE' \| 'DRAFT' \| 'DEPRECATED' \| 'ARCHIVED'/)
  assert.match(apiSource, /listSkillHubSkills/)
  assert.match(apiSource, /getSkillHubSkill/)
  assert.match(apiSource, /createSkillHubSkill/)
  assert.match(apiSource, /updateSkillHubSkill/)
  assert.match(apiSource, /createSkillHubVersion/)
  assert.match(apiSource, /getSkillHubVersion/)
  assert.match(apiSource, /sourcePath/)
  assert.match(apiSource, /contentSnapshot/)
  assert.match(apiSource, /\/skill-hub\/skills/)
  assert.match(apiSource, /\/versions/)
  assert.match(apiSource, /platformClient\.post/)
  assert.match(apiSource, /platformClient\.put/)
})

test('skill hub view includes marketplace cards, filters, detail, and version dialogs', async () => {
  const viewSource = await readProjectFile('src/views/SkillHubView.vue')

  assert.match(viewSource, /skill-card-grid/)
  assert.match(viewSource, /skill-detail-dialog/)
  assert.match(viewSource, /isDetailDialogOpen/)
  assert.match(viewSource, /closeDetailDialog/)
  assert.match(viewSource, /No skills match the current filters/)
  assert.match(viewSource, /Search/)
  assert.match(viewSource, /Category/)
  assert.match(viewSource, /Status/)
  assert.match(viewSource, /Source Path/)
  assert.match(viewSource, /Current Version/)
  assert.match(viewSource, /Current Content Snapshot/)
  assert.match(viewSource, /selectedVersion/)
  assert.match(viewSource, /selectVersion/)
  assert.match(viewSource, /getSkillHubVersion/)
  assert.match(viewSource, /Version History/)
  assert.match(viewSource, /Version Notes/)
  assert.match(viewSource, /New Skill/)
  assert.match(viewSource, /Edit Skill/)
  assert.match(viewSource, /Save Skill/)
  assert.match(viewSource, /Create Version/)
  assert.match(viewSource, /createSkillHubSkill/)
  assert.match(viewSource, /updateSkillHubSkill/)
  assert.match(viewSource, /createSkillHubVersion/)
})

test('skill hub view gates mutation controls for guest users', async () => {
  const viewSource = await readProjectFile('src/views/SkillHubView.vue')

  assert.match(viewSource, /canMutate/)
  assert.match(viewSource, /userStore\.isAuthenticated && !userStore\.isGuest/)
  assert.match(viewSource, /Read-only preview/)
  assert.match(viewSource, /:disabled="!canMutate"/)
})
