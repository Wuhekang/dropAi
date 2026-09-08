import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { computed, nextTick, reactive, ref, watch } from 'vue'
import {
  canDownloadRewriteDocument,
  hasDocumentDownload,
  isCompletedDocumentStatus,
  isTerminalDocumentStatus,
  rewriteJobProgress
} from '../src/utils/documentJobStatus.js'

test('partial results are terminal and complete processing, not ongoing retries', () => {
  assert.equal(isCompletedDocumentStatus('PARTIAL_SUCCESS'), true)
  assert.equal(isTerminalDocumentStatus('PARTIAL_SUCCESS'), true)
  assert.equal(rewriteJobProgress({ status: 'PARTIAL_SUCCESS', totalParagraphs: 182, processedParagraphs: 182 }), 100)
  assert.equal(isTerminalDocumentStatus('RUNNING'), false)
  assert.equal(isTerminalDocumentStatus('PENDING'), false)
})

test('partial document downloads require a confirmed artifact and rewrite job id', () => {
  const partial = { jobId: 'partial-job', status: 'PARTIAL_SUCCESS', downloadUrl: '/api/documents/partial-job/download' }
  assert.equal(canDownloadRewriteDocument(partial), true)
  assert.equal(hasDocumentDownload(partial), true)
  assert.equal(canDownloadRewriteDocument({ ...partial, downloadUrl: '' }), false)
  assert.equal(canDownloadRewriteDocument({ ...partial, downloadUrl: '   ' }), false)
  assert.equal(canDownloadRewriteDocument({ ...partial, jobId: '' }), false)
  assert.equal(hasDocumentDownload({ status: 'PARTIAL_SUCCESS', download_url: '/api/output.docx' }), true)
})

test('failed or ongoing jobs never become downloadable just because they have a URL', () => {
  for (const status of ['FAILED', 'RUNNING', 'PENDING', '']) {
    const job = { jobId: 'job', status, downloadUrl: '/api/output.docx' }
    assert.equal(canDownloadRewriteDocument(job), false)
    assert.equal(hasDocumentDownload(job), false)
  }
  assert.equal(isTerminalDocumentStatus('FAILED'), true)
  assert.equal(isCompletedDocumentStatus('FAILED'), false)
})

test('existing successful rewrite download behavior and failure progress stay unchanged', () => {
  assert.equal(canDownloadRewriteDocument({ jobId: 'legacy-success', status: 'SUCCESS' }), true)
  assert.equal(hasDocumentDownload({ jobId: 'legacy-success', status: 'SUCCESS' }), false)
  assert.equal(rewriteJobProgress({ status: 'SUCCESS' }), 100)
  assert.equal(rewriteJobProgress({ status: 'FAILED', platform: 'DAYA', totalParagraphs: 182, processedParagraphs: 182 }), 99)
  assert.equal(rewriteJobProgress({ status: 'RUNNING', totalParagraphs: 182, processedParagraphs: 91 }), 50)
})

// Exercise the actual SFC setup logic without adding a browser/test dependency.
// Vue reactivity is real; only lifecycle, network and browser effects are replaced.
function rewritePage(job) {
  const counts = { polls: 0, downloads: 0, uploads: 0, clicks: 0 }
  const notifications = []
  const filenames = []
  const source = readFileSync(new URL('../src/views/Rewrite/index.vue', import.meta.url), 'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .replace(/^import[\s\S]*?from\s+['"][^'"]+['"]\r?\n/gm, '')
  const noop = () => {}
  const context = vm.createContext({
    computed, nextTick, reactive, ref, watch,
    onMounted: noop, onBeforeUnmount: noop,
    useRouter: () => ({ push: noop, replace: noop }),
    ElMessage: Object.fromEntries(['success', 'warning', 'error'].map(type => [type, message => notifications.push({ type, message })])), ElMessageBox: {},
    canDownloadRewriteDocument, isCompletedDocumentStatus, isTerminalDocumentStatus,
    jobProgress: rewriteJobProgress,
    getDocumentJob: async () => job,
    getDocumentJobs: async () => [],
    downloadDocument: async () => { counts.downloads += 1; return new Blob(['valid-docx']) },
    uploadDocument: async () => { counts.uploads += 1; return job },
    uploadExternalDocument: async () => { counts.uploads += 1; return job },
    window: { setInterval: () => { counts.polls += 1; return 1 }, clearInterval: noop, setTimeout: noop },
    URL: { createObjectURL: () => 'blob:document', revokeObjectURL: noop },
    document: { createElement: () => ({ click() { counts.clicks += 1; filenames.push(this.download) } }) }
  })
  vm.runInContext(`${source}\nglobalThis.page = { startDocumentPolling, handleDocumentAction, downloadDocumentJob, setDocumentJob, documentJob, docProgress, docActionDisabled, docActionText, taskStateLabel, downloadStateText, partialCompletionNotice };`, context)
  return { page: context.page, counts, notifications, filenames }
}

test('actual partial-result action downloads the existing file, with no polling or resubmission', async () => {
  const job = { jobId: 'partial', status: 'PARTIAL_SUCCESS', platform: 'DAYA', downloadUrl: '/api/file', totalParagraphs: 182, processedParagraphs: 182 }
  const { page, counts } = rewritePage(job)
  await page.startDocumentPolling(job.jobId)
  assert.equal(page.docProgress.value, 100)
  assert.equal(page.taskStateLabel.value, '部分完成')
  assert.equal(page.docActionText.value, '下载部分完成文档')
  assert.equal(page.docActionDisabled.value, false)
  await page.handleDocumentAction()
  assert.deepEqual(counts, { polls: 0, downloads: 1, uploads: 0, clicks: 1 })
})

test('actual partial-result action without an artifact is disabled and never charges by resubmitting', async () => {
  const job = { jobId: 'partial-no-file', status: 'PARTIAL_SUCCESS', platform: 'DAYA', downloadUrl: '' }
  const { page, counts } = rewritePage(job)
  await page.startDocumentPolling(job.jobId)
  assert.equal(page.docActionDisabled.value, true)
  await page.handleDocumentAction()
  assert.deepEqual(counts, { polls: 0, downloads: 0, uploads: 0, clicks: 0 })
})

test('viewing a partial history item cannot reuse another document download URL', () => {
  const { page } = rewritePage({})
  page.setDocumentJob({ jobId: 'old-success', status: 'SUCCESS', downloadUrl: '/api/old-file' })
  page.setDocumentJob({ jobId: 'new-partial', status: 'PARTIAL_SUCCESS' })
  assert.equal(page.documentJob.downloadUrl, '')
  assert.equal(page.docActionDisabled.value, true)
})

test('actual failed-record download stays blocked while existing SUCCESS keeps downloading', async () => {
  for (const [status, expectedDownloads] of [['FAILED', 0], ['SUCCESS', 1]]) {
    const job = { jobId: status, status, platform: 'DAYA', downloadUrl: '/api/file' }
    const { page, counts } = rewritePage(job)
    await page.startDocumentPolling(job.jobId)
    await page.downloadDocumentJob(job)
    assert.equal(counts.polls, 0)
    assert.equal(counts.uploads, 0)
    assert.equal(counts.downloads, expectedDownloads)
  }
})

test('successful Daya protected results use normal completion and optimized filename, without partial warnings', async () => {
  const job = {
    jobId: 'daya-protected-success', platform: 'DAYA', status: 'SUCCESS', fileName: '测试稿.docx',
    downloadUrl: '/api/file', totalParagraphs: 182, processedParagraphs: 182,
    protectedParagraphs: 4, failedParagraphs: 0,
    message: '历史内部说明：表格说明受保护，[[DROP_AI_PROTECTED_4]]'
  }
  const { page, counts, notifications, filenames } = rewritePage(job)
  await page.startDocumentPolling(job.jobId)
  assert.equal(page.taskStateLabel.value, '已完成')
  assert.equal(page.docActionText.value, '下载优化文档')
  assert.equal(page.downloadStateText.value, '文档已生成')
  assert.equal(page.partialCompletionNotice.value, '')
  assert.equal(page.docActionDisabled.value, false)
  await page.handleDocumentAction()
  assert.deepEqual(filenames, ['测试稿_优化版.docx'])
  assert.equal(notifications.some(item => item.type === 'warning'), false)
  assert.equal(notifications.some(item => item.message.includes('DROP_AI_PROTECTED')), false)
  assert.deepEqual(counts, { polls: 0, downloads: 1, uploads: 0, clicks: 1 })
})

test('actual service partial results retain their status and generic notice without exposing internal errors', async () => {
  const job = {
    jobId: 'daya-service-partial', platform: 'DAYA', status: 'PARTIAL_SUCCESS', fileName: '测试稿.docx',
    downloadUrl: '/api/file', failedParagraphs: 1, message: '内部错误 [[DROP_AI_PROTECTED_4]] 表格说明'
  }
  const { page, notifications, filenames } = rewritePage(job)
  await page.startDocumentPolling(job.jobId)
  assert.equal(page.documentJob.status, 'PARTIAL_SUCCESS')
  assert.equal(page.taskStateLabel.value, '部分完成')
  assert.equal(page.partialCompletionNotice.value, '部分段落未完成，可以下载已有结果。')
  assert.equal(notifications.some(item => item.type === 'warning'), true)
  assert.equal(notifications.some(item => /DROP_AI_PROTECTED|表格说明/.test(item.message)), false)
  await page.handleDocumentAction()
  assert.deepEqual(filenames, ['测试稿_部分完成版.docx'])
})
