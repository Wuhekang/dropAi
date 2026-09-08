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
