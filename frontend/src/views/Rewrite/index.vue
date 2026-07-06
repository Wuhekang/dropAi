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

    <section class="hero-workbench panel">
      <div class="hero-copy">
        <span class="eyebrow">AI 写作优化</span>
        <h1>学术文本 AI 优化、降重与风格增强</h1>
        <p>粘贴论文段落或上传 Word 文档，DropAI 会按所选模式生成更自然、更学术的表达。</p>
        <div class="model-chip">
          <span class="status-dot"></span>
          {{ aiStatus.testStatus === 'success' ? '模型已连接' : checkingAiStatus ? '正在检测模型' : '模型待连接' }}
        </div>
      </div>

      <div class="input-workspace">
        <textarea
          v-model="originalText"
          class="main-input"
          maxlength="10000"
          placeholder="粘贴需要优化的论文、报告或任务书段落..."
        ></textarea>
        <div class="input-meta">
          <span>{{ originalText.length }} 字</span>
          <button class="ghost-button" type="button" @click="loadDemoText">载入示例</button>
        </div>
        <button class="primary-button start-button" type="button" :disabled="!canSubmitText" @click="handleSubmit">
          {{ submitting ? processText : '开始优化' }}
        </button>
      </div>
    </section>

    <section class="mode-row">
      <button
        v-for="mode in textModes"
        :key="mode.value"
        class="product-card mode-card"
        :class="{ active: rewriteMode === mode.value }"
        type="button"
        @click="rewriteMode = mode.value"
      >
        <span>{{ mode.kicker }}</span>
        <strong>{{ mode.label }}</strong>
        <p>{{ mode.description }}</p>
      </button>
    </section>

    <section class="output-layout">
      <article class="output-panel panel">
        <div class="section-head">
          <div>
            <span class="status-pill"><span class="status-dot"></span>输出展示</span>
            <h2>优化结果</h2>
          </div>
          <button class="ghost-button" type="button" :disabled="!rewrittenText" @click="copyResult">复制结果</button>
        </div>

        <div v-if="submitting" class="generation-progress">
          <div class="progress-title">
            <strong>{{ processText }}</strong>
            <span>{{ progress }}%</span>
          </div>
          <div class="loading-line"><span :style="{ width: `${progress}%` }"></span></div>
          <div class="process-list">
            <span v-for="(step, index) in processSteps" :key="step" :class="{ done: index <= processStep }">{{ step }}</span>
          </div>
        </div>

        <div v-else-if="rewrittenText" class="compare-grid">
          <section class="compare-card original">
            <span>原文</span>
            <p>{{ originalSnapshot }}</p>
          </section>
          <section class="compare-card rewritten">
            <span>优化后</span>
            <p>{{ displayedResult }}</p>
          </section>
        </div>

        <div v-else class="empty-output">
          <strong>结果会在这里渐进出现</strong>
          <p>开始优化后，系统会先解析文本，再生成改写结果，并保留原文对比。</p>
        </div>
      </article>

      <aside class="document-panel panel">
        <div class="section-head compact">
          <div>
            <span class="status-pill">Word 文档</span>
            <h2>整篇处理</h2>
          </div>
        </div>

        <div class="doc-upload">
          <el-upload action="" :auto-upload="false" :show-file-list="false" accept=".docx" :on-change="handleDocumentSelected">
            <button class="ghost-button" type="button">上传 DOCX</button>
          </el-upload>
          <span>{{ documentJob.fileName || '支持整篇论文降重 / 降 AI' }}</span>
        </div>

        <div class="doc-state">
          <strong>{{ documentStatusText }}</strong>
          <p>{{ documentJob.message || '上传文档后会先检测字数和积分，再开始处理。' }}</p>
          <div class="loading-line"><span :style="{ width: `${documentProgress}%` }"></span></div>
        </div>

        <button class="primary-button doc-button" type="button" :disabled="!canConfirmDocument" @click="submitDocument">
          {{ documentUploading || documentPrechecking ? '处理中...' : documentActionText }}
        </button>
        <button class="ghost-button doc-button" type="button" :disabled="documentJob.status !== 'SUCCESS'" @click="downloadOptimizedDocument">
          下载优化文档
        </button>
      </aside>
    </section>

    <section class="history-panel panel">
      <div class="section-head">
        <div>
          <span class="eyebrow">历史记录</span>
          <h2>最近处理</h2>
        </div>
        <div class="history-tabs">
          <button :class="{ active: historyTab === 'text' }" type="button" @click="historyTab = 'text'">文本记录</button>
          <button :class="{ active: historyTab === 'document' }" type="button" @click="historyTab = 'document'">文档记录</button>
        </div>
      </div>

      <div v-if="historyLoading" class="history-empty">正在加载记录...</div>
      <div v-else-if="pagedHistory.length" class="record-grid">
        <article v-for="item in pagedHistory" :key="recordKey(item)" class="record-card product-card">
          <div class="record-top">
            <strong>{{ recordTitle(item) }}</strong>
            <span :class="['record-status', statusClass(item.status)]">{{ recordStatus(item) }}</span>
          </div>
          <p>{{ recordSummary(item) }}</p>
          <div class="record-meta">
            <span>模式：{{ recordMode(item) }}</span>
            <span>进度：{{ recordProgress(item) }}%</span>
            <span>{{ formatTime(item.createdAt || item.createTime || item.updatedAt) }}</span>
          </div>
          <div class="record-actions">
            <button class="ghost-button" type="button" @click="openRecord(item)">查看</button>
            <button class="ghost-button" type="button" :disabled="!canDownloadRecord(item)" @click="downloadRecord(item)">下载</button>
            <button class="ghost-button" type="button" @click="rerunRecord(item)">重新处理</button>
          </div>
        </article>
      </div>
      <div v-else class="history-empty">暂无记录。</div>

      <div class="pagination" v-if="historyTotalPages > 1">
        <button class="ghost-button" type="button" :disabled="historyPage <= 1" @click="historyPage -= 1">上一页</button>
        <span>第 {{ historyPage }} / {{ historyTotalPages }} 页</span>
        <button class="ghost-button" type="button" :disabled="historyPage >= historyTotalPages" @click="historyPage += 1">下一页</button>
      </div>
    </section>

    <el-dialog v-model="detailVisible" title="记录详情" width="760px">
      <div v-if="detail" class="detail-view">
        <section>
          <span>原文</span>
          <p>{{ detail.originalText || detail.fileName || '--' }}</p>
        </section>
        <section>
          <span>优化结果</span>
          <p>{{ detail.rewrittenText || detail.message || '--' }}</p>
        </section>
      </div>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  downloadDocument,
  getAiStatus,
  getDocumentJob,
  getDocumentJobs,
  getRewriteDetail,
  getRewriteList,
  precheckDocument,
  submitRewrite,
  uploadDocument,
  logout
} from '../../api/rewrite'

const router = useRouter()
const originalText = ref('')
const originalSnapshot = ref('')
const rewrittenText = ref('')
const displayedResult = ref('')
const rewriteMode = ref('humanize')
const targetPlatform = ref('GENERAL')
const submitting = ref(false)
const processStep = ref(0)
const historyLoading = ref(false)
const history = ref([])
const documentJobs = ref([])
const historyTab = ref('text')
const historyPage = ref(1)
const detailVisible = ref(false)
const detail = ref(null)
const checkingAiStatus = ref(false)
const selectedDocument = ref(null)
const documentPrechecking = ref(false)
const documentUploading = ref(false)
const documentPrecheckSeq = ref(0)
const documentPollTimer = ref(null)
const aiStatus = reactive({ provider: '', model: '', endpoint: '', testStatus: '', testMessage: '' })
const documentPrecheck = reactive({ ready: false, requestId: '', charCount: 0, costPoints: 0, currentPoints: 0, canProcess: false })
const documentJob = reactive({
  jobId: '',
  fileName: '',
  status: '',
  totalParagraphs: 0,
  processedParagraphs: 0,
  modeName: '',
  message: '',
  downloadUrl: ''
})

const pageSize = 10
const processSteps = ['解析文本', '识别语义', '生成改写', '润色表达', '保存记录']
const textModes = [
  { value: 'rewrite', label: '智能降重', kicker: '01', description: '降低重复表达，保留原意和专业术语。' },
  { value: 'polish', label: '精准降ai', kicker: '02', description: '降低 AI 痕迹，让表达更自然可信。' },
  { value: 'double', label: '双降', kicker: '03', description: '同时降低重复率与 AI 痕迹，适合终稿前处理。' }
]

const canSubmitText = computed(() => Boolean(originalText.value.trim()) && !submitting.value)
const processText = computed(() => processSteps[Math.min(processStep.value, processSteps.length - 1)])
const progress = computed(() => submitting.value ? Math.min(96, (processStep.value + 1) * 19) : rewrittenText.value ? 100 : 0)
const activeHistory = computed(() => historyTab.value === 'text' ? history.value : documentJobs.value)
const historyTotalPages = computed(() => Math.max(1, Math.ceil(activeHistory.value.length / pageSize)))
const pagedHistory = computed(() => {
  const start = (historyPage.value - 1) * pageSize
  return activeHistory.value.slice(start, start + pageSize)
})
const documentProgress = computed(() => jobProgress(documentJob))
const canConfirmDocument = computed(() =>
  selectedDocument.value &&
  documentPrecheck.ready &&
  documentPrecheck.canProcess &&
  !documentPrechecking.value &&
  !documentUploading.value &&
  !['PENDING', 'RUNNING'].includes(documentJob.status)
)
const documentActionText = computed(() => {
  if (!selectedDocument.value) return '等待上传'
  if (documentPrechecking.value) return '检测字数中...'
  if (documentPrecheck.ready && !documentPrecheck.canProcess) return `积分不足，需 ${documentPrecheck.costPoints}`
  if (documentPrecheck.ready) return `开始处理（${documentPrecheck.costPoints} 积分）`
  return '准备处理'
})
const documentStatusText = computed(() => {
  if (documentJob.status === 'SUCCESS') return '处理完成'
  if (documentJob.status === 'FAILED') return '处理失败'
  if (['PENDING', 'RUNNING'].includes(documentJob.status)) return '正在处理'
  if (documentPrechecking.value) return '正在预检'
  if (documentPrecheck.ready) return '预检完成'
  return '等待文档'
})

watch(historyTab, () => {
  historyPage.value = 1
})

async function handleSubmit() {
  if (!originalText.value.trim()) {
    ElMessage.warning('请先输入需要优化的文本。')
    return
  }
  submitting.value = true
  rewrittenText.value = ''
  displayedResult.value = ''
  originalSnapshot.value = originalText.value
  processStep.value = 0
  try {
    const ticker = startProgress()
    const result = await submitRewrite({
      originalText: originalText.value,
      rewriteType: modeLabel(rewriteMode.value),
      platform: targetPlatform.value
    })
    clearInterval(ticker)
    processStep.value = 4
    rewrittenText.value = result.rewrittenText || ''
    await revealResult(rewrittenText.value)
    ElMessage.success('优化完成。')
    await loadHistory()
  } catch (error) {
    ElMessage.error(error.message || '优化失败。')
  } finally {
    submitting.value = false
  }
}

function startProgress() {
  return window.setInterval(() => {
    processStep.value = Math.min(3, processStep.value + 1)
  }, 520)
}

async function revealResult(text) {
  displayedResult.value = ''
  const chunks = String(text || '').match(/.{1,18}/g) || []
  for (const chunk of chunks.slice(0, 80)) {
    displayedResult.value += chunk
    await wait(18)
  }
  displayedResult.value = text
}

function modeLabel(value) {
  return textModes.find(item => item.value === value)?.label || '学术润色'
}

function loadDemoText() {
  originalText.value = '随着人工智能技术的发展，学术写作辅助工具在论文撰写过程中发挥着越来越重要的作用。然而，现有研究仍需要在表达自然度、逻辑连贯性和专业术语一致性方面进一步提升。'
}

async function copyResult() {
  if (!rewrittenText.value) return
  await navigator.clipboard.writeText(rewrittenText.value)
  ElMessage.success('已复制。')
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

async function handleDocumentSelected(uploadFile) {
  selectedDocument.value = uploadFile.raw
  Object.assign(documentJob, { fileName: uploadFile.name, status: '', message: '正在检测字数和积分...' })
  await runDocumentPrecheck()
}

async function runDocumentPrecheck() {
  if (!selectedDocument.value) return
  resetDocumentPrecheck()
  documentPrechecking.value = true
  const seq = documentPrecheckSeq.value + 1
  documentPrecheckSeq.value = seq
  documentPrecheck.requestId = createRequestId()
  try {
    const result = await precheckDocument(selectedDocument.value, rewriteMode.value === 'polish' ? 'humanize' : rewriteMode.value)
    if (seq !== documentPrecheckSeq.value) return
    Object.assign(documentPrecheck, {
      ready: true,
      charCount: result.charCount || 0,
      costPoints: result.costPoints || 0,
      currentPoints: result.currentPoints || 0,
      canProcess: !!result.canProcess
    })
    documentJob.message = documentPrecheck.canProcess
      ? `预检完成，共 ${formatNumber(documentPrecheck.charCount)} 字。`
      : `当前积分不足，预计需要 ${documentPrecheck.costPoints} 积分。`
  } catch (error) {
    documentJob.status = 'FAILED'
    documentJob.message = error.message || '文档预检失败。'
  } finally {
    if (seq === documentPrecheckSeq.value) documentPrechecking.value = false
  }
}

async function submitDocument() {
  if (!canConfirmDocument.value) return
  documentUploading.value = true
  try {
    const mode = rewriteMode.value === 'polish' ? 'humanize' : rewriteMode.value
    const job = await uploadDocument(selectedDocument.value, mode, targetPlatform.value, documentPrecheck.requestId)
    setDocumentJob(job)
    upsertDocumentJob(job)
    ElMessage.success('文档任务已提交。')
    startDocumentPolling(job.jobId)
    historyTab.value = 'document'
  } catch (error) {
    ElMessage.error(error.message || '文档提交失败。')
  } finally {
    documentUploading.value = false
  }
}

async function startDocumentPolling(jobId) {
  stopDocumentPolling()
  await syncDocumentJob(jobId)
  if (['SUCCESS', 'FAILED'].includes(documentJob.status)) return
  documentPollTimer.value = window.setInterval(async () => {
    try {
      await syncDocumentJob(jobId)
      if (['SUCCESS', 'FAILED'].includes(documentJob.status)) {
        stopDocumentPolling()
        if (documentJob.status === 'SUCCESS') ElMessage.success('文档处理完成。')
      }
    } catch {
      stopDocumentPolling()
    }
  }, 1200)
}

function stopDocumentPolling() {
  if (documentPollTimer.value) window.clearInterval(documentPollTimer.value)
  documentPollTimer.value = null
}

async function syncDocumentJob(jobId) {
  const job = await getDocumentJob(jobId)
  setDocumentJob(job)
  upsertDocumentJob(job)
}

function setDocumentJob(job = {}) {
  Object.assign(documentJob, job)
}

function upsertDocumentJob(job) {
  const index = documentJobs.value.findIndex(item => item.jobId === job.jobId)
  if (index >= 0) documentJobs.value.splice(index, 1, job)
  else documentJobs.value.unshift(job)
}

async function downloadOptimizedDocument() {
  await downloadDocumentJob(documentJob)
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

async function loadHistory() {
  historyLoading.value = true
  try {
    history.value = await getRewriteList() || []
    documentJobs.value = await getDocumentJobs() || []
  } finally {
    historyLoading.value = false
  }
}

async function openRecord(item) {
  if (historyTab.value === 'document') {
    setDocumentJob(await getDocumentJob(item.jobId, true))
    detail.value = { originalText: documentJob.fileName, rewrittenText: documentJob.message }
  } else {
    detail.value = await getRewriteDetail(item.id)
  }
  detailVisible.value = true
}

async function downloadRecord(item) {
  if (historyTab.value === 'document') await downloadDocumentJob(item)
  else if (item.rewrittenText) {
    const blob = new Blob([item.rewrittenText], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `DropAI-优化结果-${item.id || Date.now()}.txt`
    link.click()
    URL.revokeObjectURL(url)
  }
}

function rerunRecord(item) {
  if (historyTab.value === 'document') {
    ElMessage.info('请重新上传文档以再次处理。')
    return
  }
  originalText.value = item.originalText || ''
  rewriteMode.value = modeValue(item.rewriteType)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function canDownloadRecord(item) {
  if (historyTab.value === 'document') return item.status === 'SUCCESS'
  return Boolean(item.rewrittenText)
}

function recordKey(item) {
  return historyTab.value === 'document' ? item.jobId : item.id
}

function recordTitle(item) {
  if (historyTab.value === 'document') return item.fileName || '文档任务'
  return item.rewriteType || '文本优化'
}

function recordSummary(item) {
  if (historyTab.value === 'document') return item.message || '文档处理任务'
  return item.originalText || item.rewrittenText || '文本优化记录'
}

function recordMode(item) {
  if (historyTab.value === 'document') return item.modeName || modeLabel(rewriteMode.value)
  return item.rewriteType || '学术润色'
}

function recordProgress(item) {
  return historyTab.value === 'document' ? jobProgress(item) : 100
}

function recordStatus(item) {
  if (historyTab.value === 'text') return 'SUCCESS'
  return item.status || 'PENDING'
}

function statusClass(status = '') {
  return String(status).toLowerCase()
}

function jobProgress(job = {}) {
  const total = job.totalParagraphs || 0
  const done = job.processedParagraphs || 0
  if (job.status === 'SUCCESS') return 100
  if (!total) return ['PENDING', 'RUNNING'].includes(job.status) ? 12 : 0
  return Math.min(99, Math.round((done / total) * 100))
}

function modeValue(label = '') {
  const found = textModes.find(item => label.includes(item.label))
  return found?.value || 'humanize'
}

function resetDocumentPrecheck() {
  Object.assign(documentPrecheck, { ready: false, requestId: '', charCount: 0, costPoints: 0, currentPoints: 0, canProcess: false })
}

function createRequestId() {
  return window.crypto?.randomUUID ? window.crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString()
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '--'
}

function wait(ms) {
  return new Promise(resolve => window.setTimeout(resolve, ms))
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
  width: min(1180px, calc(100% - 40px));
}

.brand {
  border: 0;
  background: transparent;
  cursor: pointer;
}

.hero-workbench {
  display: grid;
  grid-template-columns: minmax(260px, 0.82fr) minmax(0, 1.18fr);
  gap: 28px;
  padding: 26px;
}

.hero-copy h1 {
  max-width: 620px;
  margin: 0 0 16px;
  overflow-wrap: anywhere;
  font-size: clamp(34px, 5vw, 58px);
  line-height: 1.08;
}

.hero-copy p {
  max-width: 560px;
  margin: 0;
  color: var(--muted);
  font-size: 17px;
  line-height: 1.75;
}

.model-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 24px;
  padding: 8px 11px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--muted);
  background: rgba(255, 255, 255, 0.055);
  font-size: 13px;
}

.input-workspace {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.main-input {
  width: 100%;
  min-height: 260px;
  padding: 18px;
  resize: vertical;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  color: var(--text);
  background: rgba(255, 255, 255, 0.055);
  outline: none;
  line-height: 1.7;
}

.input-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--muted);
}

.start-button {
  min-height: 50px;
  font-size: 16px;
}

.mode-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin: 16px 0;
}

.mode-card {
  padding: 18px;
  text-align: left;
  cursor: pointer;
}

.mode-card.active {
  border-color: rgba(0, 210, 255, 0.5);
  background: linear-gradient(180deg, rgba(108, 92, 231, 0.18), rgba(255, 255, 255, 0.065));
}

.mode-card span {
  color: var(--cyan);
  font-size: 12px;
}

.mode-card strong {
  display: block;
  margin: 10px 0 8px;
  font-size: 21px;
}

.mode-card p {
  margin: 0;
  color: var(--muted);
  line-height: 1.65;
}

.output-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.42fr) minmax(280px, 0.58fr);
  gap: 16px;
  margin-bottom: 16px;
}

.output-panel,
.document-panel,
.history-panel {
  padding: 18px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-head h2 {
  margin: 10px 0 0;
  font-size: 28px;
}

.section-head.compact h2 {
  font-size: 22px;
}

.generation-progress {
  display: grid;
  gap: 18px;
  min-height: 360px;
  align-content: center;
}

.progress-title {
  display: flex;
  justify-content: space-between;
  color: var(--text);
  font-size: 18px;
}

.process-list {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.process-list span {
  color: var(--muted-2);
  font-size: 12px;
}

.process-list .done {
  color: var(--cyan);
}

.compare-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 14px;
  min-height: 420px;
}

.compare-card {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.045);
}

.compare-card span {
  color: var(--cyan);
  font-size: 12px;
  font-weight: 720;
}

.compare-card p {
  margin: 14px 0 0;
  white-space: pre-wrap;
  color: var(--muted);
  line-height: 1.85;
}

.compare-card.rewritten p {
  color: var(--text);
  font-size: 16px;
}

.empty-output,
.history-empty {
  display: grid;
  place-items: center;
  min-height: 280px;
  color: var(--muted);
  text-align: center;
}

.empty-output strong {
  color: var(--text);
  font-size: 22px;
}

.empty-output p {
  max-width: 460px;
  line-height: 1.7;
}

.doc-upload,
.doc-state {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.045);
}

.doc-upload span,
.doc-state p {
  margin: 0;
  color: var(--muted);
  line-height: 1.6;
}

.doc-button {
  width: 100%;
  margin-top: 12px;
}

.history-tabs {
  display: flex;
  gap: 8px;
}

.history-tabs button {
  padding: 9px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  color: var(--muted);
  background: rgba(255, 255, 255, 0.045);
  cursor: pointer;
}

.history-tabs .active {
  color: var(--text);
  border-color: rgba(0, 210, 255, 0.4);
  background: rgba(0, 210, 255, 0.1);
}

.record-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.record-card {
  display: grid;
  gap: 12px;
  padding: 16px;
}

.record-top,
.record-meta,
.record-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.record-top strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-status {
  flex: 0 0 auto;
  color: var(--success);
  font-size: 12px;
}

.record-status.failed {
  color: var(--danger);
}

.record-status.running,
.record-status.pending {
  color: var(--cyan);
}

.record-card p {
  display: -webkit-box;
  min-height: 50px;
  margin: 0;
  overflow: hidden;
  color: var(--muted);
  line-height: 1.7;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.record-meta {
  justify-content: flex-start;
  flex-wrap: wrap;
  color: var(--muted-2);
  font-size: 12px;
}

.record-actions {
  justify-content: flex-start;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 18px;
  color: var(--muted);
}

.detail-view {
  display: grid;
  gap: 14px;
}

.detail-view section {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.045);
}

.detail-view span {
  color: var(--cyan);
  font-size: 12px;
}

.detail-view p {
  margin: 10px 0 0;
  white-space: pre-wrap;
  line-height: 1.75;
}

@media (max-width: 980px) {
  .hero-workbench,
  .output-layout,
  .compare-grid,
  .record-grid {
    grid-template-columns: 1fr;
  }

  .mode-row {
    grid-template-columns: 1fr;
  }

  .process-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .rewrite-product {
    width: min(100% - 28px, 1180px);
  }

  .section-head,
  .record-top,
  .record-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .history-tabs {
    width: 100%;
  }

  .history-tabs button {
    flex: 1;
  }
}
</style>
