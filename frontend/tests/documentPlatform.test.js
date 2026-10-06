import test from 'node:test'
import assert from 'node:assert/strict'
import { documentPlatforms, documentRoute, selectDocumentPlatform, selectDocumentMode } from '../src/utils/documentPlatform.js'

test('Gezida switch selects humanize pricing and native upload', () => {
  for (const mode of ['rewrite', 'humanize', 'double']) {
    const selected = selectDocumentPlatform(mode, 'GEZIDA')
    assert.deepEqual(selected, { mode: 'humanize', platform: 'GEZIDA' })
    assert.deepEqual(documentRoute(selected.mode, selected.platform), { ...selected, external: false })
  }
})
test('changing mode leaves specialist explicitly; hidden platform does not affect rewrite', () => {
  assert.deepEqual(selectDocumentMode('double', 'GEZIDA'), { mode: 'double', platform: 'GENERAL' })
  assert.deepEqual(documentRoute('rewrite', 'GEZIDA'), { mode: 'rewrite', platform: 'GENERAL', external: false })
  assert.deepEqual(documentRoute('rewrite', 'DAYA'), { mode: 'rewrite', platform: 'GENERAL', external: false })
})
test('only Daya uses external API and native generic route is unchanged', () => {
  for (const mode of ['humanize', 'double']) {
    assert.equal(documentRoute(mode, 'DAYA').external, true)
    assert.deepEqual(documentRoute(mode, 'GENERAL'), { mode, platform: 'GENERAL', external: false })
  }
  assert.deepEqual(documentPlatforms.map(p => p.value), ['GENERAL', 'DAYA', 'GEZIDA'])
})
