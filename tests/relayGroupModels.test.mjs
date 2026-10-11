import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'
const source = await readFile(new URL('../src/utils/relayGroupModels.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText
const { groupCallableModels } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
const model = (modelId, name, enabled = true) => ({ modelId, model: name, displayName: name, enabled })
const channel = (name, overrides = {}) => ({ name, groupNames: 'pro, default', enabled: true, status: 'available', models: [model(1, 'one'), model(2, 'two'), model(3, 'off', false)], ...overrides })
test('filters group permissions and channel availability, deduplicates models across channels', () => {
  const groups = [{ code: 'pro', enabled: true, modelIds: [1] }]
  assert.deepEqual(groupCallableModels('pro', groups, [channel('a'), channel('b'), channel('failed', { status: 'failed' }), channel('other', { groupNames: 'vip' }), channel('off', { enabled: false })]), [{ name: 'one', channels: ['a', 'b'] }])
  assert.deepEqual(groupCallableModels('unknown', groups, [channel('a')]), [])
})
test('default and unconfigured groups preserve dispatch fallback; disabled groups are empty', () => {
  for (const group of [{ code: 'default', modelIds: [1] }, { code: 'pro', modelIds: [] }]) {
    assert.equal(groupCallableModels(group.code, [{ ...group, enabled: true }], [channel('a')]).length, 2)
  }
  assert.deepEqual(groupCallableModels('pro', [{ code: 'pro', enabled: false, modelIds: [] }], [channel('a')]), [])
})
