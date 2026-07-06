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

    <section class="workbench-hero">
      <div class="hero-title-block">
        <span class="eyebrow">Writing Assistant</span>
        <h1>AI 学术写作助手</h1>
        <p>输入文本或上传 Word 文档，系统会按你的目标完成改写、润色和表达优化。</p>
      </div>

      <section class="primary-workspace panel">
        <div class="workspace-top">
          <div>
            <span class="status-pill"><span class="status-dot"></span>{{ modelStateText }}</span>
            <h2>开始一次优化</h2>
          </div>
          <span class="word-count">{{ originalText.length }} 字</span>
        </div>

        <textarea
          v-model="originalText"
          class="main-input"
          maxlength="10000"
          placeholder="粘贴论文、报告或开题材料中的段落..."
        ></textarea>

        <div class="upload-strip">
          <div>
            <strong>{{ documentJob.fileName || '上传 Word 文档' }}</strong>
            <span>{{ documentHint }}</span>
          </div>
          <el-upload action="" :auto-upload="false" :show-file-list="false" accept=".docx" :on-change="handleDocumentSelected">
            <button class="ghost-button" type="button">选择 DOCX</button>
          </el-upload>
        </div>

        <div class="mode-picker">
          <button
            v-for="mode in textModes"
            :key="mode.value"
            :class="{ active: rewriteMode === mode.value }"
            type="button"
            @click="rewriteMode = mode.value"
          >
            <strong>{{ mode.label }}</strong>
            <span>{{ mode.description }}</span>
          </button>
        </div>

        <div class="primary-actions">
          <button class="ghost-button" type="button" @click="loadDemoText">载入示例</button>
          <button class="primary-button start-button" type="button" :disabled="!canStart" @click="startPrimaryAction">
            {{ primaryButtonText }}
          </button>
        </div>
      </section>
    </section>

    <section v-if="isBusy || progress > 0" class="process-panel panel">
      <div class="process-head">
        <div>
          <span class="eyebrow">处理过程</span>
          <h2>{{ processTitle }}</h2>
        </div>
        <strong>{{ progress }}%</strong>
      </div>
      <div class="loading-line"><span :style="{ width: `${progress}%` }"></span></div>
      <div class="process-list">
        <span v-for="(step, index) in processSteps" :key="step" :class="{ done: index <= processStep }">{{ step }}</span>
      </div>
    </section>

    <section class="result-section panel" :class="{ active: hasResult || submitting }">
      <div class="section-head">
        <div>
          <span class="eyebrow">输出结果</span>
          <h2>原文与优化对比</h2>
        </div>
        <button class="ghost-button" type="button" :disabled="!rewrittenText" @click="copyResult">复制结果</button>
      </div>

      <div v-if="submitting" class="result-placeholder">
        <strong>正在生成优化结果</strong>
        <p>结果会按步骤渐进加载，不会一次性堆出整段内容。</p>
      </div>

      <div v-else-if="hasResult" class="compare-grid">
        <article class="compare-card">
          <span>原文</span>
          <p>{{ originalSnapshot }}</p>
        </article>
        <article class="compare-card rewritten">
          <span>优化后</span>
          <p>{{ displayedResult }}</p>
        </article>
      </div>

      <div v-else class="result-placeholder muted-state">
        <strong>完成输入后，这里会显示结果</strong>
        <p>先在上方输入文本或上传文档，然后点击“开始优化”。</p>
      </div>
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
          <span>原文 / 文件</span>
          <p>{{ detail.originalText || detail.fileName || '--' }}</p>
        </section>
        <section>
          <span>优化结果 / 状态</span>
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
const rewriteMode = ref('standard')
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
const processSteps = ['解析', '重写', '优化', '输出']
const textModes = [
  { value: 'standard', label: '标准优化（推荐）', apiMode: '学术润色', description: '提升表达自然度与论文语气。' },
  { value: 'academic', label: '学术增强', apiMode: '降低AI写作痕迹', description: '降低 AI 痕迹，增强人工写作感。' },
  { value: 'deep', label: '深度重写', apiMode: '智能降重', description: '更大幅度重写，适合重复率偏高内容。' }
]

const selectedMode = computed(() => textModes.find(item => item.value === rewriteMode.value) || textModes[0])
const hasTextInput = computed(() => Boolean(originalText.value.trim()))
const hasDocumentInput = computed(() => Boolean(selectedDocument.value))
const isDocumentReady = computed(() => documentPrecheck.ready && documentPrecheck.canProcess)
const canStart = computed(() => !isBusy.value && (hasTextInput.value || isDocumentReady.value))
const isBusy = computed(() => submitting.value || documentUploading.value || documentPrechecking.value || ['PENDING', 'RUNNING'].includes(documentJob.status))
const modelStateText = computed(() => aiStatus.testStatus === 'success' ? '模型已连接' : checkingAiStatus.value ? '正在检测模型' : '模型待连接')
const processTitle = computed(() => documentUploading.value || ['PENDING', 'RUNNING'].includes(documentJob.status) ? '文档处理中' : '文本优化中')
const primaryButtonText = computed(() => {
  if (submitting.value || documentUploading.value) return '正在优化...'
  if (documentPrechecking.value) return '正在检测文档...'
  if (!hasTextInput.value && hasDocumentInput.value && !documentPrecheck.ready) return '等待文档检测'
  if (!hasTextInput.value && hasDocumentInput.value && documentPrecheck.ready && !documentPrecheck.canProcess) return `积分不足，需 ${documentPrecheck.costPoints}`
  return '开始优化'
})
const processText = computed(() => processSteps[Math.min(processStep.value, processSteps.length - 1)])
const progress = computed(() => {
  if (submitting.value) return Math.min(96, (processStep.value + 1) * 24)
  if (documentPrechecking.value) return 18
  if (['PENDING', 'RUNNING'].includes(documentJob.status)) return documentProgress.value
  if (documentJob.status === 'SUCCESS') return 100
  if (rewrittenText.value) return 100
  return 0
})
const hasResult = computed(() => Boolean(rewrittenText.value))
const activeHistory = computed(() => historyTab.value === 'text' ? history.value : documentJobs.value)
const historyTotalPages = computed(() => Math.max(1, Math.ceil(activeHistory.value.length / pageSize)))
const pagedHistory = computed(() => {
  const start = (historyPage.value - 1) * pageSize
  return activeHistory.value.slice(start, start + pageSize)
})
const documentProgress = computed(() => jobProgress(documentJob))
const documentHint = computed(() => {
  if (documentPrechecking.value) return '正在检测字数和积分...'
  if (documentPrecheck.ready && documentPrecheck.canProcess) return `预检完成，预计消耗 ${documentPrecheck.costPoints} 积分`
  if (documentPrecheck.ready && !documentPrecheck.canProcess) return `积分不足，预计需要 ${documentPrecheck.costPoints} 积分`
  return '可选：上传整篇论文，优先处理粘贴文本'
})

watch(historyTab, () => {
  historyPage.value = 1
})

async function startPrimaryAction() {
  if (hasTextInput.value) {
    await handleSubmit()
    return
  }
  if (isDocumentReady.value) await submitDocument()
}

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
      rewriteType: selectedMode.value.apiMode,
      platform: targetPlatform.value
    })
    clearInterval(ticker)
    processStep.value = 3
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
    processStep.value = Math.min(2, processStep.value + 1)
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
    const result = await precheckDocument(selectedDocument.value, documentModeForApi())
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
  if (!isDocumentReady.value) return
  documentUploading.value = true
  processStep.value = 0
  try {
    const job = await uploadDocument(selectedDocument.value, documentModeForApi(), targetPlatform.value, documentPrecheck.requestId)
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

function documentModeForApi() {
  if (rewriteMode.value === 'deep') return 'rewrite'
  if (rewriteMode.value === 'academic') return 'humanize'
  return 'double'
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
  if (historyTab.value === 'document') return item.modeName || selectedMode.value.label
  return item.rewriteType || '标准优化'
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
  if (label.includes('降重')) return 'deep'
  if (label.includes('AI')) return 'academic'
  return 'standard'
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
  width: min(1120px, calc(100% - 40px));
}

.brand {
  border: 0;
  background: transparent;
  cursor: pointer;
}

.workbench-hero {
  display: grid;
  justify-items: center;
  gap: 24px;
  padding: 18px 0 12px;
}

.hero-title-block {
  max-width: 720px;
  text-align: center;
}

.hero-title-block h1 {
  margin: 0 0 12px;
  font-size: clamp(34px, 5vw, 56px);
  line-height: 1.08;
}

.hero-title-block p {
  margin: 0 auto;
  max-width: 620px;
  color: var(--muted);
  font-size: 17px;
  line-height: 1.75;
}

.primary-workspace {
  width: min(880px, 100%);
  padding: 22px;
}

.workspace-top,
.section-head,
.process-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.workspace-top h2,
.section-head h2,
.process-head h2 {
  margin: 12px 0 0;
  font-size: 28px;
}

.word-count {
  color: var(--muted);
  font-size: 13px;
}

.main-input {
  width: 100%;
  min-height: 230px;
  margin-top: 18px;
  padding: 18px;
  resize: vertical;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  color: var(--text);
  background: rgba(255, 255, 255, 0.055);
  outline: none;
  line-height: 1.75;
}

.upload-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 12px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.045);
}

.upload-strip strong,
.upload-strip span {
  display: block;
}

.upload-strip span {
  margin-top: 4px;
  color: var(--muted);
  font-size: 13px;
}

.mode-picker {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.mode-picker button {
  min-height: 96px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  color: var(--muted);
  background: rgba(255, 255, 255, 0.04);
  text-align: left;
  cursor: pointer;
  transition: var(--ease);
}

.mode-picker button.active {
  color: var(--text);
  border-color: rgba(0, 210, 255, 0.48);
  background: linear-gradient(180deg, rgba(108, 92, 231, 0.16), rgba(255, 255, 255, 0.055));
}

.mode-picker strong,
.mode-picker span {
  display: block;
}

.mode-picker span {
  margin-top: 8px;
  line-height: 1.55;
  font-size: 13px;
}

.primary-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
}

.start-button {
  min-width: 180px;
  min-height: 50px;
  font-size: 16px;
}

.process-panel,
.result-section,
.history-panel {
  margin-top: 16px;
  padding: 18px;
}

.process-head strong {
  color: var(--cyan);
  font-size: 32px;
}

.process-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.process-list span {
  color: var(--muted-2);
  font-size: 13px;
}

.process-list .done {
  color: var(--cyan);
}

.result-section {
  opacity: 0.78;
}

.result-section.active {
  opacity: 1;
}

.compare-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
}

.compare-card {
  min-height: 280px;
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
}

.result-placeholder,
.history-empty {
  display: grid;
  place-items: center;
  min-height: 220px;
  color: var(--muted);
  text-align: center;
}

.result-placeholder strong {
  color: var(--text);
  font-size: 20px;
}

.result-placeholder p {
  max-width: 420px;
  line-height: 1.7;
}

.muted-state {
  min-height: 180px;
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

@media (max-width: 900px) {
  .mode-picker,
  .compare-grid,
  .record-grid {
    grid-template-columns: 1fr;
  }

  .upload-strip,
  .workspace-top,
  .section-head,
  .process-head,
  .primary-actions,
  .record-top,
  .record-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .start-button,
  .history-tabs {
    width: 100%;
  }

  .history-tabs button {
    flex: 1;
  }
}
</style>
