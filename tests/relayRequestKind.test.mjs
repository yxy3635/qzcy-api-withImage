import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const source = await readFile(new URL('../src/utils/relayRequestKind.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText
const { isCompactionRequest } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)

test('recorded compaction on ordinary protocols receives a badge', () => {
  for (const endpoint of ['/v1/messages', '/v1/chat/completions', '/v1/responses']) {
    assert.equal(isCompactionRequest(endpoint, true), true)
    assert.equal(isCompactionRequest(endpoint, false), false)
    assert.equal(isCompactionRequest(endpoint, null), false)
  }
})

test('legacy compact endpoints keep badges when no classification was saved', () => {
  for (const endpoint of ['/responses/compact', '/api/v1/responses/compact/', '/v1/v1/responses/compact?x=1']) {
    assert.equal(isCompactionRequest(endpoint), true)
  }
  assert.equal(isCompactionRequest('/v1/responses/compact-other'), false)
  assert.equal(isCompactionRequest(null), false)
})
