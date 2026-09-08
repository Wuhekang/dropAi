// Keep in sync with DocumentRules / the server fallback. Missing recognition
// never removes a form card or silently disables a formatting operation.
const baseRule = () => ({
  enabled: true, chineseFont: '宋体', latinFont: 'Times New Roman', fontSizeName: '小四',
  fontSizePt: 12, bold: false, alignment: 'justify', leftIndentCm: 0, rightIndentCm: 0,
  lineSpacingMode: '1.5', fixedLineSpacingPt: 20, minimumLineSpacingPt: 12,
  multipleLineSpacing: 1.25, firstLineIndentChars: 2,
  spaceBefore: { unit: 'line', value: 0 }, spaceAfter: { unit: 'line', value: 0 }
})

export function buildEditableRules(...layers) {
  const heading = (name, pt) => ({ ...baseRule(), chineseFont: '黑体', fontSizeName: name,
    fontSizePt: pt, bold: true, alignment: 'left', lineSpacingMode: 'single', firstLineIndentChars: 0 })
  const caption = () => ({ ...baseRule(), fontSizeName: '五号', fontSizePt: 10.5,
    alignment: 'center', lineSpacingMode: 'single', firstLineIndentChars: 0 })
  const result = {
    body: { normal: baseRule() },
    headings: { level1: heading('三号', 16), level2: heading('小三', 15), level3: heading('四号', 14) },
    toc: { title: heading('三号', 16), level1: heading('三号', 16), level2: heading('小三', 15), level3: heading('四号', 14) },
    captions: { figure: caption(), table: caption() },
    details: { table: { ...baseRule(), alignment: 'center', lineSpacingMode: 'single', firstLineIndentChars: 0 }, reference: baseRule() }
  }
  for (const layer of layers) {
    for (const [groupKey, group] of Object.entries(result)) {
      for (const [ruleKey, rule] of Object.entries(group)) {
        const supplied = layer?.[groupKey]?.[ruleKey]
        if (!supplied || typeof supplied !== 'object' || Array.isArray(supplied)) continue
        for (const field of Object.keys(rule)) {
          const value = supplied[field]
          if (value === undefined || value === null || value === '') continue
          if (field === 'spaceBefore' || field === 'spaceAfter') {
            for (const part of ['unit', 'value']) {
              if (value[part] !== undefined && value[part] !== null && value[part] !== '') rule[field][part] = value[part]
            }
          } else rule[field] = value
        }
        rule.enabled = true
      }
    }
  }
  return result
}

export function validateRuleIndents(groups) {
  for (const group of groups) {
    for (const item of group.items) {
      for (const [field, label] of [['leftIndentCm', '左缩进'], ['rightIndentCm', '右缩进']]) {
        // Older analysis results may omit these optional fields entirely.
        if (!Object.prototype.hasOwnProperty.call(item.rule, field)) continue
        const value = item.rule[field]
        if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 2) {
          throw new Error(`${item.label}的${label}必须是 0–2 厘米之间的有限数值。`)
        }
      }
    }
  }
}
