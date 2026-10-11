import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

// 使用项目已有 TypeScript 编译器，避免为纯聚合逻辑引入额外测试框架。
const source = await readFile(new URL('../src/utils/relayTodayUsage.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText
const { groupTodayUsage, formatUsageMoney } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
const row = (channelId, providerId, requests, cost, overrides = {}) => ({
  channelId, providerId, requests, cost, channelName: '历史名称', providerName: '同名供应商',
  errors: 1, upstreamCost: cost / 2, ...overrides
})

test('channel totals include all providers, legacy logs and deleted providers without changing input', () => {
  const channels = [{ id: 1, name: '当前渠道', providers: [{id: 11, name: '当前供应商'}, {id:12, name:'空闲供应商'}] }]
  const usage = [row(1, 11, 4, '0.010123'), row(1, null, 2, 0.02), row(1, 99, 3, 0.03)]
  const before = JSON.stringify({channels, usage})
  const [channel] = groupTodayUsage(channels, usage)
  assert.equal(channel.name, '当前渠道')
  assert.equal(channel.requests, 9)
  assert.equal(channel.errors, 3)
  assert.equal(channel.cost.toFixed(6), '0.060123')
  assert.equal(formatUsageMoney(channel.upstreamCost), '$0.030062')
  assert.equal(channel.providers.find(p => p.providerId === 11).name, '当前供应商')
  assert.equal(channel.providers.find(p => p.providerId === 12).requests, 0)
  assert.equal(channel.providers.find(p => p.providerId === null).requests, 2)
  assert.equal(channel.providers.find(p => p.providerId === 99).archived, true)
  assert.equal(JSON.stringify({channels, usage}), before)
})

test('same names in different channels remain separate and deleted channels retain daily usage', () => {
  const channels = [{id: 1, name: '同名渠道', providers: [{id:11,name:'同名供应商'}]}, {id:2,name:'同名渠道',providers:[]}]
  const grouped = groupTodayUsage(channels, [row(1, 11, 5, 0.01), row(2, 21, 7, 0.02), row(99, 91, 3, 0.03), row(null, null, 2, 0)])
  assert.deepEqual(grouped.map(c => c.requests), [5, 7, 3, 2])
  assert.equal(grouped.find(c => c.key === '99').archived, true)
  assert.equal(grouped.reduce((sum, c) => sum + c.requests, 0), 17)
  assert.equal(grouped.reduce((sum, c) => sum + c.cost, 0).toFixed(6), '0.060000')
})

test('unused channels and providers stay visible with zero amounts', () => {
  const [channel] = groupTodayUsage([{id:1,name:'无用量',providers:[{id:11,name:'供应商'}]}], [])
  assert.equal(channel.requests, 0)
  assert.equal(channel.cost, 0)
  assert.equal(channel.providers[0].requests, 0)
})

test('money retains sub-cent charges and rounds the displayed total to six decimals', () => {
  assert.equal(formatUsageMoney(0.000123), '$0.000123')
  assert.equal(formatUsageMoney(0.0300615), '$0.030062')
  assert.equal(formatUsageMoney(0), '$0.000000')
})
