export function isCompletedDocumentStatus(status) {
  return status === 'SUCCESS' || status === 'PARTIAL_SUCCESS'
}

export function isTerminalDocumentStatus(status) {
  return isCompletedDocumentStatus(status) || status === 'FAILED'
}

export function hasDocumentDownload(job = {}) {
  const url = job.downloadUrl || job.download_url
  return isCompletedDocumentStatus(job.status) && typeof url === 'string' && Boolean(url.trim())
}

export function canDownloadRewriteDocument(job = {}) {
  // Older successful rewrite records use the authenticated job download endpoint.
  // Partial results require the backend to explicitly confirm an output artifact.
  return Boolean(job.jobId) && (job.status === 'SUCCESS' || hasDocumentDownload(job))
}

export function rewriteJobProgress(job = {}) {
  if (isCompletedDocumentStatus(job.status)) return 100
  const total = job.totalParagraphs || 0
  const done = job.processedParagraphs || 0
  if (job.status === 'FAILED' && job.platform === 'DAYA') {
    return total ? Math.min(99, Math.round((done / total) * 100)) : 0
  }
  if (job.status === 'FAILED') return 100
  if (!total) return ['PENDING', 'RUNNING'].includes(job.status) ? 12 : 0
  return Math.min(99, Math.round((done / total) * 100))
}
