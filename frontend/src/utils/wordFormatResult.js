const reportItems = values => (Array.isArray(values) ? values : [])
  .filter(item => item && typeof item.item === 'string' && item.item.trim())

const countOf = value => typeof value === 'number' && Number.isFinite(value) && value >= 0
  ? Math.round(value) : null

export function describeWordFormatResult(result) {
  const value = result && typeof result === 'object' ? result : {}
  const report = value.formatReport && typeof value.formatReport === 'object' && !Array.isArray(value.formatReport)
    ? value.formatReport : {}
  const pending = reportItems(report.notApplied).map(item => ({
    item: item.item,
    reason: typeof item.reason === 'string' ? item.reason : '',
    count: countOf(item.count),
    status: item.status
  }))
  // A template category absent from the source is not an attempted operation that failed.
  const partialSuccess = typeof value.partialSuccess === 'boolean' ? value.partialSuccess
    : (typeof report.skippedCount === 'number' && report.skippedCount > 0)
      || pending.some(item => item.status === 'skipped')
  const reviewCopyOnly = partialSuccess && value.changedCount === 0
  return {
    partialSuccess,
    reviewCopyOnly,
    title: reviewCopyOnly ? '已生成可下载核对副本，自动调整未完成'
      : partialSuccess ? '部分格式已处理，文档可下载' : '格式处理结果已生成，可下载',
    statusLabel: reviewCopyOnly ? '核对副本可下载 · 自动调整未完成' : partialSuccess ? '部分完成 · 可下载' : '已完成',
    hasReport: Object.keys(report).length > 0,
    applied: reportItems(report.applied).map(item => ({ item: item.item, count: countOf(item.count) ?? 0 })),
    pending
  }
}
