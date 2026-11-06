import assert from 'node:assert/strict'
import test from 'node:test'
import { buildEditableRules, validateRuleIndents } from '../src/utils/wordFormatRules.js'

const groups = rule => [{ key: 'body', items: [{ label: '正文', rule }] }]

test('missing recognition still provides all twelve editable rules', () => {
  const rules = buildEditableRules()
  const all = Object.values(rules).flatMap(group => Object.values(group))
  assert.equal(all.length, 12)
  assert.ok(all.every(rule => rule.enabled && rule.latinFont === 'Times New Roman'))
  assert.equal(rules.body.normal.fontSizePt, 12)
  assert.equal(rules.headings.level1.fontSizePt, 16)
  assert.equal(rules.captions.figure.fontSizePt, 10.5)
})

test('complete submission merges defaults then recognized values then user edits', () => {
  const server = { body: { normal: { fontSizePt: 14, chineseFont: '仿宋', bold: true, spaceBefore: { unit: 'pt', value: 6 } } } }
  const edits = { body: { normal: { fontSizePt: 12, bold: false, leftIndentCm: 0, spaceBefore: { value: 0 } } } }
  const rules = buildEditableRules(server, edits)
  assert.equal(rules.body.normal.chineseFont, '仿宋')
  assert.equal(rules.body.normal.fontSizePt, 12)
  assert.equal(rules.body.normal.bold, false)
  assert.deepEqual(rules.body.normal.spaceBefore, { unit: 'pt', value: 0 })
  assert.equal(rules.toc.level3.fontSizePt, 14)
  assert.equal(server.body.normal.spaceBefore.value, 6)
})

test('null or omitted recognition fields retain independent defaults', () => {
  const rules = buildEditableRules({ headings: { level2: null }, captions: { figure: { chineseFont: '', fontSizePt: null, enabled: false } } })
  assert.equal(rules.headings.level2.fontSizePt, 15)
  assert.equal(rules.captions.figure.fontSizePt, 10.5)
  assert.equal(rules.captions.figure.enabled, true)
  rules.toc.level1.spaceBefore.value = 7
  assert.equal(rules.toc.level2.spaceBefore.value, 0)
  assert.equal(buildEditableRules().toc.level1.spaceBefore.value, 0)
})

test('left and right indents accept both boundaries and ordinary values', () => {
  for (const value of [0, 0.5, 2]) {
    assert.doesNotThrow(() => validateRuleIndents(groups({ leftIndentCm: value, rightIndentCm: value })))
  }
})

test('all supplied indents reject unsafe and non-numeric values', () => {
  for (const field of ['leftIndentCm', 'rightIndentCm']) {
    for (const value of [-0.001, 2.001, 5, 100, NaN, Infinity, -Infinity, '', '1', 'NaN', 'Infinity', null, undefined, true, false]) {
      assert.throws(() => validateRuleIndents(groups({ [field]: value })), /0–2 厘米/)
    }
  }
})

test('legacy rules with omitted optional fields are allowed', () => {
  assert.doesNotThrow(() => validateRuleIndents(groups({ fontSizePt: 12 })))
})

test('validation includes later groups, both fields, and disabled table rules', () => {
  assert.throws(() => validateRuleIndents([
    ...groups({ leftIndentCm: 0 }),
    { key: 'details', items: [{ key: 'table', label: '表格文字', rule: { rightIndentCm: 3 } }] }
  ]), /表格文字的右缩进/)
})
