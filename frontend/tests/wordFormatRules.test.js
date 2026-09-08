import assert from 'node:assert/strict'
import test from 'node:test'
import { validateRuleIndents } from '../src/utils/wordFormatRules.js'

const groups = rule => [{ key: 'body', items: [{ label: '正文', rule }] }]

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
