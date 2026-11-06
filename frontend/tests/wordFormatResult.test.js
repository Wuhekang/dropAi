import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { describeWordFormatResult } from '../src/utils/wordFormatResult.js'

test('partial success shows a downloadable outcome and preserves both report lists', () => {
  const result = describeWordFormatResult({
    partialSuccess: true,
    formatReport: {
      applied: [{ item: '正文', count: 12 }],
      notApplied: [{ item: '复杂表格', reason: '该表格保留原格式', status: 'skipped', count: 2 }]
    }
  })
  assert.equal(result.partialSuccess, true)
  assert.equal(result.title, '部分格式已处理，文档可下载')
  assert.equal(result.statusLabel, '部分完成 · 可下载')
  assert.equal(result.hasReport, true)
  assert.deepEqual(result.applied, [{ item: '正文', count: 12 }])
  assert.deepEqual(result.pending, [{ item: '复杂表格', reason: '该表格保留原格式', status: 'skipped', count: 2 }])
})

test('missing source categories and template warnings do not imply partial failure', () => {
  for (const pending of [[], [{ item: '未定位图标题', reason: '原稿没有此项可忽略', status: 'not_found' }], [{ item: '请核对目录页码' }]]) {
    const result = describeWordFormatResult({ warnings: ['模板没有图标题要求'], formatReport: { notApplied: pending } })
    assert.equal(result.partialSuccess, false)
    assert.equal(result.statusLabel, '已完成')
  }
})

test('explicit partial marker takes precedence; legacy fallback only uses actual skip markers', () => {
  const formatReport = { notApplied: [{ item: '异常段落', status: 'skipped' }], skippedCount: 1 }
  assert.equal(describeWordFormatResult({ partialSuccess: false, formatReport }).partialSuccess, false)
  assert.equal(describeWordFormatResult({ formatReport }).partialSuccess, true)
  assert.equal(describeWordFormatResult({ formatReport: { skippedCount: 1 } }).partialSuccess, true)
  assert.equal(describeWordFormatResult({ partialSuccess: 'true' }).partialSuccess, false)
})

test('partial result with zero applied changes still reports the real outcome', () => {
  const result = describeWordFormatResult({ partialSuccess: true, changedCount: 0, formatReport: { applied: [], notApplied: [{ item: '正文', status: 'skipped', count: 1 }] } })
  assert.deepEqual(result.applied, [])
  assert.equal(result.partialSuccess, true)
  assert.equal(result.reviewCopyOnly, true)
  assert.equal(result.title, '已生成可下载核对副本，自动调整未完成')
  assert.equal(result.statusLabel, '核对副本可下载 · 自动调整未完成')
  assert.ok(!result.title.includes('部分格式已处理'))
})

test('missing or non-numeric changed counts do not misclassify legacy partial results as zero changes', () => {
  for (const changedCount of [undefined, null, '0', false, 12]) {
    const result = describeWordFormatResult({ partialSuccess: true, changedCount })
    assert.equal(result.reviewCopyOnly, false)
    assert.equal(result.title, '部分格式已处理，文档可下载')
  }
  assert.equal(describeWordFormatResult({ partialSuccess: true }).reviewCopyOnly, false)
  assert.equal(describeWordFormatResult({ partialSuccess: false, changedCount: 0 }).reviewCopyOnly, false)
})

test('optional counts remain optional and malformed report entries are filtered', () => {
  const result = describeWordFormatResult({ formatReport: {
    applied: [null, {}, { item: '  ' }, { item: '正文', count: -1 }],
    notApplied: [{ item: '域', reason: { secret: 'not rendered' } }, { item: '图表', count: Infinity }]
  } })
  assert.deepEqual(result.applied, [{ item: '正文', count: 0 }])
  assert.equal(result.pending[0].reason, '')
  assert.ok(result.pending.every(item => item.count === null))
  for (const input of [null, undefined, '', { formatReport: [] }]) {
    assert.equal(describeWordFormatResult(input).hasReport, false)
  }
})

test('download remains enabled for SUCCESS regardless of partial marker', () => {
  const page = readFileSync(new URL('../src/views/WordFormatter/index.vue', import.meta.url), 'utf8')
  assert.match(page, /:disabled="downloading" @click="downloadResult"/)
  assert.match(page, /if \(status === 'SUCCESS'\) \{\s*screen.value = 'done'/)
  assert.match(page, /<h2>\{\{ resultView.title \}\}<\/h2>/)
  assert.match(page, /未处理 \/ 需核对项目/)
})
