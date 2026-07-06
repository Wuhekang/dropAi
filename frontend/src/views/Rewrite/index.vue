<template>
  <main class="page-shell rewrite-product">
    <nav class="top-nav">
      <button class="brand" type="button" @click="router.push('/')">
        <span class="brand-mark">D</span>
        <span>DropAI</span>
      </button>
      <div class="nav-links">
        <button type="button" @click="router.push('/dashboard')">控制台</button>
        <button type="button" @click="router.push('/new-project')">工程生成</button>
        <button class="ghost-button" type="button" @click="signOut">退出</button>
      </div>
    </nav>

    <section class="hero-strip">
      <div>
        <span class="eyebrow">AI 写作优化</span>
        <h1>学术文本 AI 优化</h1>
        <p>粘贴文本或上传 DOCX，DropAI 会按所选模式生成更自然、更适合学术场景的最终版本。</p>
      </div>
    </section>

    <section class="workspace-panel panel" :class="{ busy: isBusy }">
      <div class="input-column">
        <div class="section-title-row upload-head">
          <div>
            <span class="mini-label">文档上传与处理</span>
            <h2>上传 DOCX</h2>
          </div>
          <div class="mode-tabs">
            <button
              v-for="mode in textModes"
              :key="mode.value"
              type="button"
              :class="{ active: rewriteMode === mode.value }"
              :disabled="isBusy"
              @click="rewriteMode = mode.value"
            >
              {{ mode.label }}
            </button>
          </div>
          <span class="word-count">{{ formatNumber(inputCharCount) }} 字</span>
        </div>

        <div
          class="drop-zone doc-drop"
          :class="{ active: dragging, filled: !!selectedDocument }"
          @dragenter.prevent="dragging = true"
          @dragover.prevent="dragging = true"
          @dragleave.prevent="dragging = false"
          @drop.prevent="handleDrop"
        >
          <div class="doc-icon">DOCX</div>
          <div>
            <strong>{{ selectedDocument?.name || '拖拽 DOCX 到上方区域' }}</strong>
            <p>{{ selectedDocument ? `${formatFileSize(selectedDocument.size)} · 正文已在下方 Original 展示` : '上传后自动解析正文，原文内容会直接显示在下方结果区左侧' }}</p>
          </div>
          <button class="ghost-button" type="button" :disabled="isBusy" @click="fileInput?.click()">选择文件</button>
          <input ref="fileInput" class="hidden-input" type="file" accept=".docx" @change="handleFileInput" />
        </div>
      </div>

      <aside class="process-column">
        <div class="section-title-row">
          <div>
            <span class="mini-label">处理信息</span>
            <h2>{{ statusText }}</h2>
          </div>
          <strong class="progress-number">{{ progress }}%</strong>
        </div>

        <dl class="info-list">
          <div>
            <dt>文件名</dt>
            <dd>{{ selectedDocument?.name || '-' }}</dd>
          </div>
          <div>
            <dt>字数统计</dt>
            <dd>{{ formatNumber(effectiveCharCount) }} 字</dd>
          </div>
          <div>
            <dt>当前模式</dt>
            <dd>{{ activeMode.label }}</dd>
          </div>
          <div>
            <dt>处理状态</dt>
            <dd>{{ statusText }}</dd>
          </div>
        </dl>

        <div class="loading-line progress-line"><span :style="{ width: `${progress}%` }"></span></div>

        <button class="primary-button start-button" type="button" :disabled="!canStart" @click="handleSubmit">
          {{ isBusy ? statusText : '开始优化' }}
        </button>
      </aside>
    </section>

    <section ref="resultSection" class="result-panel panel">
      <div class="section-title-row result-head">
        <div>
          <span class="mini-label">输出结果</span>
          <h2>Original / Optimized</h2>
        </div>
        <div class="result-actions">
          <button class="ghost-button" type="button" :class="{ active: diffMode }" :disabled="!rewrittenText" @click="diffMode = !diffMode">
            对比模式
          </button>
          <button class="ghost-button" type="button" :disabled="!rewrittenText" @click="copyResult">复制结果</button>
        </div>
      </div>

      <div v-if="isBusy" class="result-loading">
        <div class="spinner"></div>
        <strong>{{ statusText }}</strong>
        <p>正在生成最终版本，完成后会一次性展示完整结果。</p>
      </div>

      <div v-else class="compare-grid">
        <article class="compare-card">
          <span>Original</span>
          <p>{{ originalSnapshot || originalText || '输入内容会显示在这里。' }}</p>
        </article>
        <article class="compare-card optimized">
          <span>Optimized</span>
          <p v-if="!diffMode">{{ rewrittenText || '优化完成后显示最终结果。' }}</p>
          <p v-else v-html="diffHtml"></p>
        </article>
      </div>
    </section>

    <section class="history-panel panel">
      <div class="section-title-row">
        <div>
          <span class="mini-label">文档记录</span>
          <h2>最近处理</h2>
        </div>
        <button class="ghost-button" type="button" :disabled="historyLoading" @click="loadHistory">刷新</button>
      </div>

      <div v-if="historyLoading" class="history-empty">正在加载文档记录...</div>
      <div v-else-if="pagedDocuments.length" class="document-table">
        <div class="table-row table-head">
          <span>文件名</span>
          <span>修改方式</span>
          <span>字数</span>
          <span>处理时间</span>
          <span>下载</span>
        </div>
        <div v-for="item in pagedDocuments" :key="item.jobId" class="table-row">
          <strong>{{ item.fileName || '未命名文档' }}</strong>
          <span>{{ documentModeLabel(item) }}</span>
          <span>{{ formatNumber(item.charCount) }}</span>
          <span>{{ formatTime(item.updatedAt || item.createdAt) }}</span>
          <button class="ghost-button" type="button" :disabled="item.status !== 'SUCCESS'" @click="downloadDocumentJob(item)">下载</button>
        </div>
      </div>
      <div v-else class="history-empty">暂无文档记录。</div>

      <div v-if="historyTotalPages > 1" class="pagination">
        <button class="ghost-button" type="button" :disabled="historyPage <= 1" @click="historyPage -= 1">上一页</button>
        <span>第 {{ historyPage }} / {{ historyTotalPages }} 页</span>
        <button class="ghost-button" type="button" :disabled="historyPage >= historyTotalPages" @click="historyPage += 1">下一页</button>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  downloadDocument,
  extractDocumentText,
  getAiStatus,
  getDocumentJob,
  getDocumentJobs,
  precheckDocument,
  submitRewrite,
  uploadDocument,
  logout
} from '../../api/rewrite'

const router = useRouter()
const originalText = ref('')
const originalSnapshot = ref('')
const rewrittenText = ref('')
const rewriteMode = ref('rewrite')
const targetPlatform = ref('GENERAL')
const submitting = ref(false)
const extracting = ref(false)
const documentUploading = ref(false)
const historyLoading = ref(false)
const dragging = ref(false)
const diffMode = ref(false)
const progress = ref(0)
const statusText = ref('等待输入')
const selectedDocument = ref(null)
const fileInput = ref(null)
const resultSection = ref(null)
const historyPage = ref(1)
const documentPollTimer = ref(null)
const documentJobs = ref([])
const checkingAiStatus = ref(false)
const aiStatus = reactive({ provider: '', model: '', endpoint: '', testStatus: '', testMessage: '' })
const documentPrecheck = reactive({ ready: false, requestId: '', charCount: 0, costPoints: 0, currentPoints: 0, canProcess: false })

const pageSize = 10
const textModes = [
  { value: 'rewrite', label: '智能降重', apiMode: 'rewrite', description: '降低重复表达' },
  { value: 'polish', label: '精准降AI', apiMode: 'humanize', description: '降低 AI 痕迹' },
  { value: 'double', label: '双降', apiMode: 'double', description: '降重 + 降 AI' }
]

const activeMode = computed(() => textModes.find(item => item.value === rewriteMode.value) || textModes[0])
const isBusy = computed(() => submitting.value || extracting.value || documentUploading.value)
const inputCharCount = computed(() => originalText.value.length)
const effectiveCharCount = computed(() => documentPrecheck.charCount || inputCharCount.value)
const canStart = computed(() => Boolean(selectedDocument.value && originalText.value.trim()) && !isBusy.value)
const rewriteDocumentJobs = computed(() => documentJobs.value.filter(isRewriteDocument))
const historyTotalPages = computed(() => Math.max(1, Math.ceil(rewriteDocumentJobs.value.length / pageSize)))
const pagedDocuments = computed(() => {
  const start = (historyPage.value - 1) * pageSize
  return rewriteDocumentJobs.value.slice(start, start + pageSize)
})
const diffHtml = computed(() => buildDiffHtml(originalSnapshot.value, rewrittenText.value))

watch(rewriteMode, async () => {
  if (selectedDocument.value) await runDocumentPrecheck(false)
})

async function handleSubmit() {
  if (!originalText.value.trim()) {
    ElMessage.warning('请先上传 DOCX。')
    return
  }

  submitting.value = true
  rewrittenText.value = ''
  originalSnapshot.value = originalText.value
  setProgress(8, '准备优化')

  const ticker = startProgress()
  try {
    setProgress(28, '正在优化')
    const result = await submitRewrite({
      originalText: originalText.value,
      rewriteType: activeMode.value.label,
      platform: targetPlatform.value
    })
    rewrittenText.value = result.rewrittenText || ''

    if (selectedDocument.value && documentPrecheck.ready && documentPrecheck.canProcess) {
      setProgress(78, '正在保存文档任务')
      await submitDocumentTask()
    }

    setProgress(100, '已完成')
    ElMessage.success('优化完成。')
    await loadHistory()
    await nextTick()
    resultSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (error) {
    statusText.value = '处理失败'
    ElMessage.error(error.message || '优化失败。')
  } finally {
    window.clearInterval(ticker)
    submitting.value = false
  }
}

function startProgress() {
  return window.setInterval(() => {
    if (progress.value < 92) progress.value += progress.value < 60 ? 7 : 3
  }, 420)
}

async function handleDrop(event) {
  dragging.value = false
  const file = Array.from(event.dataTransfer?.files || []).find(item => item.name.toLowerCase().endsWith('.docx'))
  if (!file) {
    ElMessage.warning('请上传 DOCX 文件。')
    return
  }
  await handleDocumentFile(file)
}

async function handleFileInput(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) await handleDocumentFile(file)
}

async function handleDocumentFile(file) {
  selectedDocument.value = file
  rewrittenText.value = ''
  documentPrecheck.requestId = createRequestId()
  setProgress(12, '正在解析')
  extracting.value = true
  try {
    const extracted = await extractDocumentText(file)
    if (!extracted?.readable || !extracted.text) {
      throw new Error(extracted?.message || '未读取到可用文本。')
    }
    originalText.value = extracted.text
    originalSnapshot.value = extracted.text
    await runDocumentPrecheck(false)
    setProgress(24, '解析完成')
    ElMessage.success('DOCX 已解析，原文已显示到下方。')
  } catch (error) {
    selectedDocument.value = null
    setProgress(0, '解析失败')
    ElMessage.error(error.message || '文档解析失败。')
  } finally {
    extracting.value = false
  }
}

async function runDocumentPrecheck(showMessage = true) {
  if (!selectedDocument.value) return
  Object.assign(documentPrecheck, { ready: false, charCount: 0, costPoints: 0, currentPoints: 0, canProcess: false })
  const result = await precheckDocument(selectedDocument.value, activeMode.value.apiMode)
  Object.assign(documentPrecheck, {
    ready: true,
    charCount: result.charCount || 0,
    costPoints: result.costPoints || 0,
    currentPoints: result.currentPoints || 0,
    canProcess: !!result.canProcess
  })
  if (showMessage && !documentPrecheck.canProcess) {
    ElMessage.warning(`积分不足，预计需要 ${documentPrecheck.costPoints} 积分。`)
  }
}

async function submitDocumentTask() {
  documentUploading.value = true
  try {
    const job = await uploadDocument(selectedDocument.value, activeMode.value.apiMode, targetPlatform.value, documentPrecheck.requestId)
    upsertDocumentJob(job)
    startDocumentPolling(job.jobId)
  } finally {
    documentUploading.value = false
  }
}

async function startDocumentPolling(jobId) {
  stopDocumentPolling()
  documentPollTimer.value = window.setInterval(async () => {
    try {
      const job = await getDocumentJob(jobId)
      upsertDocumentJob(job)
      if (['SUCCESS', 'FAILED'].includes(job.status)) stopDocumentPolling()
    } catch {
      stopDocumentPolling()
    }
  }, 1400)
}

function stopDocumentPolling() {
  if (documentPollTimer.value) window.clearInterval(documentPollTimer.value)
  documentPollTimer.value = null
}

function upsertDocumentJob(job = {}) {
  if (!job.jobId) return
  if (!job.sourceFeature) job.sourceFeature = 'REWRITE'
  if (!job.modeName) job.modeName = activeMode.value.label
  const index = documentJobs.value.findIndex(item => item.jobId === job.jobId)
  if (index >= 0) documentJobs.value.splice(index, 1, job)
  else documentJobs.value.unshift(job)
}

async function downloadDocumentJob(job) {
  if (!job?.jobId || job.status !== 'SUCCESS') return
  const blob = await downloadDocument(job.jobId)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${(job.fileName || 'DropAI文档').replace(/\.docx$/i, '')}-优化.docx`
  link.click()
  URL.revokeObjectURL(url)
}

async function copyResult() {
  if (!rewrittenText.value) return
  await navigator.clipboard.writeText(rewrittenText.value)
  ElMessage.success('已复制优化结果。')
}

async function loadAiStatus() {
  checkingAiStatus.value = true
  try {
    Object.assign(aiStatus, await getAiStatus())
  } catch (error) {
    aiStatus.testStatus = 'failed'
    aiStatus.testMessage = error.message || '模型连接失败'
  } finally {
    checkingAiStatus.value = false
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    documentJobs.value = await getDocumentJobs() || []
  } finally {
    historyLoading.value = false
  }
}

function setProgress(value, text) {
  progress.value = Math.max(0, Math.min(100, value))
  statusText.value = text
}

function modeLabelFromMode(mode = '') {
  return textModes.find(item => item.apiMode === mode || item.value === mode)?.label || '智能降重'
}

function documentModeLabel(item = {}) {
  return item.modeName || modeLabelFromMode(item.mode)
}

function isRewriteDocument(item = {}) {
  return (item.sourceFeature || 'REWRITE') === 'REWRITE' &&
    String(item.fileName || '').toLowerCase().endsWith('.docx')
}

function buildDiffHtml(original, optimized) {
  const safe = escapeHtml(optimized || '优化完成后显示最终结果。')
  if (!original || !optimized) return safe
  const originalWords = new Set(String(original).split(/\s+/).filter(Boolean))
  return String(optimized).split(/(\s+)/).map(part => {
    if (!part.trim()) return escapeHtml(part)
    return originalWords.has(part) ? escapeHtml(part) : `<mark>${escapeHtml(part)}</mark>`
  }).join('')
}

function escapeHtml(value = '') {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

function createRequestId() {
  return window.crypto?.randomUUID ? window.crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString()
}

function formatFileSize(size = 0) {
  if (size < 1024 * 1024) return `${Math.max(1, Math.round(size / 1024))} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '--'
}

async function signOut() {
  try {
    await logout()
  } finally {
    sessionStorage.removeItem('dropai_token')
    sessionStorage.removeItem('dropai_username')
    sessionStorage.removeItem('dropai_role')
    router.replace('/login')
  }
}

onMounted(() => {
  loadAiStatus()
  loadHistory()
})

onBeforeUnmount(stopDocumentPolling)
</script>

<style scoped>
.rewrite-product {
  width: min(1280px, calc(100% - 48px));
}

.brand {
  border: 0;
  background: transparent;
  cursor: pointer;
}

.hero-strip {
  display: block;
  margin-bottom: 20px;
}

.hero-strip h1 {
  margin: 0 0 10px;
  font-size: clamp(38px, 5vw, 58px);
  line-height: 1.05;
  letter-spacing: 0;
}

.hero-strip p {
  max-width: 720px;
  margin: 0;
  color: var(--muted);
  font-size: 17px;
  line-height: 1.7;
}

.progress-number {
  display: block;
  color: var(--text);
  font-size: 22px;
}

.workspace-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 0.9fr);
  gap: 28px;
  margin-bottom: 16px;
  padding: 24px;
  border-radius: 16px;
}

.workspace-panel.busy {
  border-color: rgba(0, 210, 255, 0.24);
}

.section-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
}

.section-title-row h2 {
  margin: 6px 0 0;
  font-size: 24px;
  line-height: 1.15;
}

.upload-head {
  align-items: center;
}

.mini-label,
.word-count {
  color: var(--cyan);
  font-size: 12px;
  font-weight: 760;
  letter-spacing: 0.08em;
}

.mode-tabs {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.045);
}

.mode-tabs button {
  min-height: 34px;
  padding: 0 13px;
  border: 1px solid transparent;
  border-radius: 9px;
  color: var(--muted);
  background: transparent;
  cursor: pointer;
  transition: color var(--ease), background var(--ease), border-color var(--ease), transform var(--ease);
}

.mode-tabs button:hover {
  transform: translateY(-1px);
  color: var(--text);
}

.mode-tabs button.active {
  color: #fff;
  border-color: rgba(255, 255, 255, 0.18);
  background: linear-gradient(135deg, rgba(108, 92, 231, 0.82), rgba(0, 210, 255, 0.28));
  box-shadow: 0 10px 28px rgba(108, 92, 231, 0.22);
}

.input-column,
.process-column {
  min-width: 0;
}

.doc-drop {
  grid-template-columns: 1fr;
  place-items: center;
  text-align: center;
  align-items: center;
  min-height: 250px;
  margin-top: 0;
  padding: 28px;
  border-radius: 16px;
  transition: transform var(--ease), border-color var(--ease), background var(--ease), box-shadow var(--ease);
}

.doc-drop.active,
.doc-drop:hover {
  transform: translateY(-2px);
  border-color: rgba(0, 210, 255, 0.7);
  background: rgba(0, 210, 255, 0.075);
  box-shadow: 0 18px 55px rgba(0, 210, 255, 0.12);
}

.doc-drop.filled {
  border-color: rgba(108, 92, 231, 0.62);
}

.doc-drop p {
  margin: 6px 0 0;
  color: var(--muted);
}

.doc-icon {
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  margin-bottom: 4px;
  border-radius: 14px;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  background: linear-gradient(135deg, var(--cyan), var(--primary));
  box-shadow: 0 0 35px rgba(0, 210, 255, 0.25);
}

.hidden-input {
  display: none;
}

.info-list {
  display: grid;
  gap: 12px;
  margin: 0 0 22px;
}

.info-list div {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  gap: 16px;
}

.info-list dt {
  color: var(--muted);
}

.info-list dd {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-line {
  height: 9px;
  margin-bottom: 18px;
}

.start-button {
  width: 100%;
  min-height: 52px;
  border-radius: 14px;
  font-size: 16px;
}

.result-panel,
.history-panel {
  padding: 22px;
  border-radius: 16px;
}

.result-panel {
  margin-bottom: 16px;
}

.result-head {
  align-items: center;
}

.result-actions {
  display: flex;
  gap: 10px;
}

.result-actions .active {
  color: var(--text);
  border-color: rgba(0, 210, 255, 0.45);
  background: rgba(0, 210, 255, 0.1);
}

.compare-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
}

.compare-card {
  min-height: 320px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.045);
}

.compare-card span {
  color: var(--cyan);
  font-size: 12px;
  font-weight: 800;
}

.compare-card p {
  margin: 14px 0 0;
  white-space: pre-wrap;
  color: var(--muted);
  line-height: 1.85;
}

.compare-card.optimized p {
  color: var(--text);
}

.compare-card :deep(mark) {
  padding: 1px 3px;
  border-radius: 5px;
  color: #fff;
  background: rgba(108, 92, 231, 0.45);
}

.result-loading,
.history-empty {
  display: grid;
  place-items: center;
  min-height: 260px;
  color: var(--muted);
  text-align: center;
}

.result-loading strong {
  margin-top: 12px;
  color: var(--text);
}

.result-loading p {
  max-width: 420px;
  line-height: 1.7;
}

.spinner {
  width: 34px;
  height: 34px;
  border: 2px solid rgba(255, 255, 255, 0.16);
  border-top-color: var(--cyan);
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}

.document-table {
  display: grid;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 16px;
}

.table-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.35fr) 150px 110px 160px 90px;
  gap: 14px;
  align-items: center;
  min-height: 58px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  color: var(--muted);
}

.table-row:last-child {
  border-bottom: 0;
}

.table-row strong {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-head {
  min-height: 52px;
  color: var(--muted-2);
  background: rgba(255, 255, 255, 0.045);
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 14px;
  margin-top: 18px;
  color: var(--muted);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1040px) {
  .workspace-panel,
  .compare-grid {
    grid-template-columns: 1fr;
  }

  .table-row {
    grid-template-columns: minmax(180px, 1fr) 120px 90px 130px 78px;
  }
}

@media (max-width: 760px) {
  .rewrite-product {
    width: min(100% - 28px, 1280px);
  }

  .doc-drop {
    grid-template-columns: 1fr;
  }

  .section-title-row,
  .upload-head,
  .result-head,
  .result-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .mode-tabs {
    width: 100%;
  }

  .mode-tabs button {
    flex: 1;
    padding: 0 8px;
  }

  .document-table {
    gap: 10px;
    border: 0;
    overflow: visible;
  }

  .table-head {
    display: none;
  }

  .table-row {
    grid-template-columns: 1fr;
    gap: 8px;
    min-height: auto;
    padding: 14px;
    border: 1px solid var(--line);
    border-radius: 14px;
    background: rgba(255, 255, 255, 0.045);
  }
}
</style>
