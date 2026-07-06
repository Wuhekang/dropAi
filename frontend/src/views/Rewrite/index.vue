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
      <span class="eyebrow">AI 写作优化</span>
      <h1>学术文本 AI 优化</h1>
      <p>整篇论文上传为 DOCX 处理，短文本片段直接在下方快速优化，两条流程互不干扰。</p>
    </section>

    <section class="document-panel panel">
      <div class="document-upload">
        <div class="section-title-row upload-head">
          <div>
            <span class="mini-label">文档处理</span>
            <h2>文档上传与处理</h2>
          </div>
          <div class="mode-tabs">
            <button
              v-for="mode in modes"
              :key="mode.value"
              type="button"
              :class="{ active: docMode === mode.value }"
              :disabled="docBusy"
              @click="docMode = mode.value"
            >
              {{ mode.label }}
            </button>
          </div>
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
          <strong>{{ selectedDocument?.name || '拖拽 DOCX 到这里' }}</strong>
          <p>{{ selectedDocument ? '已完成文档检测，确认后开始后台处理并生成优化后的 Word。' : '支持拖拽或点击上传，上传后只检测字符数量和预计积分。' }}</p>
          <button class="ghost-button" type="button" :disabled="docBusy" @click.stop="fileInput?.click()">选择 DOCX 文件</button>
          <input ref="fileInput" class="hidden-input" type="file" accept=".docx" @change="handleFileInput" />
        </div>

        <dl class="file-summary">
          <div>
            <dt>文件名</dt>
            <dd>{{ selectedDocument?.name || '-' }}</dd>
          </div>
          <div>
            <dt>文件大小</dt>
            <dd>{{ selectedDocument ? formatFileSize(selectedDocument.size) : '-' }}</dd>
          </div>
          <div>
            <dt>字符数量</dt>
            <dd>{{ formatNumber(documentPrecheck.charCount) }} 字</dd>
          </div>
          <div>
            <dt>预计消耗</dt>
            <dd>{{ documentCostText }}</dd>
          </div>
        </dl>
      </div>

      <aside class="document-process">
        <div class="section-title-row">
          <div>
            <span class="mini-label">处理信息</span>
            <h2>{{ docStatusText }}</h2>
          </div>
          <strong class="progress-number">{{ docProgress }}%</strong>
        </div>

        <dl class="info-list">
          <div>
            <dt>当前模式</dt>
            <dd>{{ activeDocMode.label }}</dd>
          </div>
          <div>
            <dt>处理状态</dt>
            <dd>{{ docStatusText }}</dd>
          </div>
          <div>
            <dt>当前积分</dt>
            <dd>{{ documentPrecheck.ready ? `${documentPrecheck.currentPoints} 积分` : '-' }}</dd>
          </div>
          <div>
            <dt>下载状态</dt>
            <dd>{{ documentJob.status === 'SUCCESS' ? '文档已生成' : '等待生成' }}</dd>
          </div>
        </dl>

        <div class="loading-line progress-line"><span :style="{ width: `${docProgress}%` }"></span></div>

        <button class="primary-button start-button" type="button" :disabled="docActionDisabled" @click="handleDocumentAction">
          {{ docActionText }}
        </button>
        <button v-if="selectedDocument" class="plain-button" type="button" :disabled="docBusy" @click="clearDocument">重新选择文档</button>
      </aside>
    </section>

    <section ref="textSection" class="text-panel panel">
      <div class="section-title-row text-head">
        <div>
          <span class="mini-label">文本优化</span>
          <h2>输入内容 / 优化结果</h2>
        </div>
        <div class="text-actions">
          <div class="mode-tabs">
            <button
              v-for="mode in modes"
              :key="mode.value"
              type="button"
              :class="{ active: textMode === mode.value }"
              :disabled="textSubmitting"
              @click="textMode = mode.value"
            >
              {{ mode.label }}
            </button>
          </div>
          <button class="primary-button text-submit" type="button" :disabled="!canSubmitText" @click="submitText">
            {{ textSubmitting ? '正在优化...' : `开始文本优化（${textCostText}）` }}
          </button>
        </div>
      </div>

      <div class="compare-grid">
        <article class="compare-card input-card">
          <span>输入内容</span>
          <textarea
            v-model="originalText"
            class="text-input"
            :disabled="textSubmitting"
            placeholder="在这里粘贴论文段落、报告内容或需要优化的一段文字..."
            @input="clearTextResult"
          ></textarea>
          <small>{{ formatNumber(inputCharCount) }} 字</small>
        </article>
        <article class="compare-card optimized">
          <div class="result-card-head">
            <span>优化结果</span>
            <div>
              <button class="ghost-button" type="button" :class="{ active: diffMode }" :disabled="!rewrittenText" @click="diffMode = !diffMode">对比模式</button>
              <button class="ghost-button" type="button" :disabled="!rewrittenText" @click="copyResult">复制结果</button>
            </div>
          </div>
          <div v-if="textSubmitting" class="inline-loading">
            <div class="spinner"></div>
            <strong>正在优化文本...</strong>
          </div>
          <p v-else-if="!diffMode">{{ rewrittenText || '点击开始文本优化后，这里显示 AI 返回的最终结果。' }}</p>
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
          <span>模式</span>
          <span>时间</span>
          <span>操作</span>
        </div>
        <div v-for="item in pagedDocuments" :key="item.jobId" class="table-row">
          <strong>{{ item.fileName || '未命名文档' }}</strong>
          <span>{{ documentModeLabel(item) }}</span>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  downloadDocument,
  getAiStatus,
  getDocumentJob,
  getDocumentJobs,
  getFeaturePricing,
  logout,
  precheckDocument,
  submitRewrite,
  uploadDocument
} from '../../api/rewrite'

const router = useRouter()

const modes = [
  { value: 'rewrite', label: '智能降重', apiMode: 'rewrite', featureCode: 'DOCUMENT_REWRITE' },
  { value: 'humanize', label: '精准降AI', apiMode: 'humanize', featureCode: 'DOCUMENT_HUMANIZE' },
  { value: 'double', label: '双降', apiMode: 'double', featureCode: 'DOCUMENT_DOUBLE' }
]

const docMode = ref('rewrite')
const selectedDocument = ref(null)
const fileInput = ref(null)
const dragging = ref(false)
const documentPrechecking = ref(false)
const documentUploading = ref(false)
const docProgress = ref(0)
const docStatusText = ref('等待上传')
const documentPollTimer = ref(null)
const documentJobs = ref([])
const documentPrecheck = reactive({ ready: false, requestId: '', charCount: 0, costPoints: 0, currentPoints: 0, canProcess: false })
const documentJob = reactive({ jobId: '', fileName: '', status: '', message: '', downloadUrl: '', modeName: '', charCount: 0 })

const textMode = ref('rewrite')
const originalText = ref('')
const originalSnapshot = ref('')
const rewrittenText = ref('')
const textSubmitting = ref(false)
const diffMode = ref(false)
const textSection = ref(null)

const historyLoading = ref(false)
const historyPage = ref(1)
const pricing = ref([])
const aiStatus = reactive({ provider: '', model: '', endpoint: '', testStatus: '', testMessage: '' })

const pageSize = 10

const activeDocMode = computed(() => modes.find(item => item.value === docMode.value) || modes[0])
const activeTextMode = computed(() => modes.find(item => item.value === textMode.value) || modes[0])
const docBusy = computed(() => documentPrechecking.value || documentUploading.value)
const documentCostText = computed(() => documentPrecheck.costPoints > 0 ? `${documentPrecheck.costPoints} 积分` : '免费')
const docActionText = computed(() => {
  if (documentJob.status === 'SUCCESS') return '下载优化文档'
  if (documentUploading.value) return '正在优化...'
  if (documentPrechecking.value) return '正在检测...'
  if (!selectedDocument.value) return '选择 DOCX 文件'
  return `开始优化（${documentCostText.value}）`
})
const docActionDisabled = computed(() => documentPrechecking.value || documentUploading.value)

const inputCharCount = computed(() => originalText.value.length)
const estimatedTextCost = computed(() => calculateTextCost(inputCharCount.value, activeTextMode.value.featureCode))
const textCostText = computed(() => estimatedTextCost.value > 0 ? `${estimatedTextCost.value} 积分` : '免费')
const canSubmitText = computed(() => Boolean(originalText.value.trim()) && !textSubmitting.value)
const diffHtml = computed(() => buildDiffHtml(originalSnapshot.value, rewrittenText.value))

const rewriteDocumentJobs = computed(() => documentJobs.value.filter(isRewriteDocument))
const historyTotalPages = computed(() => Math.max(1, Math.ceil(rewriteDocumentJobs.value.length / pageSize)))
const pagedDocuments = computed(() => {
  const start = (historyPage.value - 1) * pageSize
  return rewriteDocumentJobs.value.slice(start, start + pageSize)
})

watch(docMode, async () => {
  if (selectedDocument.value && documentJob.status !== 'SUCCESS') await runDocumentPrecheck(false)
})

function clearTextResult() {
  rewrittenText.value = ''
  originalSnapshot.value = ''
  diffMode.value = false
}

async function submitText() {
  if (!originalText.value.trim()) {
    ElMessage.warning('请先输入需要优化的文本。')
    return
  }

  textSubmitting.value = true
  rewrittenText.value = ''
  originalSnapshot.value = ''
  diffMode.value = false
  try {
    const result = await submitRewrite({
      originalText: originalText.value,
      rewriteType: activeTextMode.value.label,
      platform: 'GENERAL'
    })
    originalSnapshot.value = originalText.value
    rewrittenText.value = result.rewrittenText || ''
    ElMessage.success('文本优化完成。')
  } catch (error) {
    ElMessage.error(error.message || '文本优化失败。')
  } finally {
    textSubmitting.value = false
  }
}

async function handleDocumentAction() {
  if (documentJob.status === 'SUCCESS') {
    await downloadDocumentJob(documentJob)
    return
  }
  if (!selectedDocument.value) {
    fileInput.value?.click()
    return
  }
  if (!documentPrecheck.ready) {
    ElMessage.warning('文档还未完成检测，请稍后。')
    return
  }
  if (!documentPrecheck.canProcess) {
    try {
      await ElMessageBox.confirm(
        `当前积分不足，需要 ${documentPrecheck.costPoints} 积分，当前余额 ${documentPrecheck.currentPoints} 积分。是否充值？`,
        '当前积分不足',
        { confirmButtonText: '去充值', cancelButtonText: '取消', type: 'warning' }
      )
      router.push({ path: '/recharge', query: { redirect: '/rewrite' } })
    } catch {
      // User cancelled the recharge prompt.
    }
    return
  }
  await submitDocument()
}

async function submitDocument() {
  if (!selectedDocument.value || !documentPrecheck.ready) return
  documentUploading.value = true
  resetDocumentJob()
  setDocProgress(10, '正在提交文档')
  try {
    const job = await uploadDocument(selectedDocument.value, activeDocMode.value.apiMode, 'GENERAL', documentPrecheck.requestId)
    setDocumentJob(job)
    upsertDocumentJob(job)
    setDocProgress(jobProgress(job), '文档处理中')
    startDocumentPolling(job.jobId)
    ElMessage.success('文档任务已提交。')
  } catch (error) {
    docStatusText.value = '提交失败'
    ElMessage.error(error.message || '文档提交失败。')
  } finally {
    documentUploading.value = false
  }
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
  resetDocumentJob()
  resetDocumentPrecheck()
  documentPrecheck.requestId = createRequestId()
  setDocProgress(0, '正在检测文档')
  await runDocumentPrecheck(true)
}

async function runDocumentPrecheck(showMessage = true) {
  if (!selectedDocument.value) return
  documentPrechecking.value = true
  resetDocumentPrecheck(true)
  try {
    const result = await precheckDocument(selectedDocument.value, activeDocMode.value.apiMode)
    Object.assign(documentPrecheck, {
      ready: true,
      charCount: result.charCount || 0,
      costPoints: result.costPoints || 0,
      currentPoints: result.currentPoints || 0,
      canProcess: !!result.canProcess
    })
    setDocProgress(0, documentPrecheck.canProcess ? '等待开始' : '积分不足')
    if (showMessage && !documentPrecheck.canProcess) {
      ElMessage.warning(`积分不足，需要 ${documentPrecheck.costPoints} 积分，当前 ${documentPrecheck.currentPoints} 积分。`)
    }
  } catch (error) {
    clearDocument()
    ElMessage.error(error.message || '文档检测失败。')
  } finally {
    documentPrechecking.value = false
  }
}

function clearDocument() {
  selectedDocument.value = null
  resetDocumentJob()
  resetDocumentPrecheck(false)
  setDocProgress(0, '等待上传')
}

function resetDocumentPrecheck(keepRequestId = true) {
  const requestId = keepRequestId ? documentPrecheck.requestId : ''
  Object.assign(documentPrecheck, { ready: false, requestId, charCount: 0, costPoints: 0, currentPoints: 0, canProcess: false })
}

function resetDocumentJob() {
  Object.assign(documentJob, { jobId: '', fileName: '', status: '', message: '', downloadUrl: '', modeName: '', charCount: 0 })
}

async function startDocumentPolling(jobId) {
  stopDocumentPolling()
  await syncDocumentJob(jobId)
  if (['SUCCESS', 'FAILED'].includes(documentJob.status)) return
  documentPollTimer.value = window.setInterval(async () => {
    try {
      await syncDocumentJob(jobId)
      if (['SUCCESS', 'FAILED'].includes(documentJob.status)) stopDocumentPolling()
    } catch {
      stopDocumentPolling()
    }
  }, 1400)
}

async function syncDocumentJob(jobId) {
  const job = await getDocumentJob(jobId)
  setDocumentJob(job)
  upsertDocumentJob(job)
  setDocProgress(jobProgress(job), job.status === 'SUCCESS' ? '已完成' : job.status === 'FAILED' ? '处理失败' : '文档处理中')
  if (job.status === 'SUCCESS') {
    ElMessage.success('文档处理完成，可以下载优化文档。')
    await loadHistory()
  }
}

function stopDocumentPolling() {
  if (documentPollTimer.value) window.clearInterval(documentPollTimer.value)
  documentPollTimer.value = null
}

function setDocumentJob(job = {}) {
  Object.assign(documentJob, job)
}

function upsertDocumentJob(job = {}) {
  if (!job.jobId) return
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
  try {
    Object.assign(aiStatus, await getAiStatus())
  } catch (error) {
    aiStatus.testStatus = 'failed'
    aiStatus.testMessage = error.message || '模型连接失败'
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

async function loadPricing() {
  try {
    pricing.value = await getFeaturePricing() || []
  } catch {
    pricing.value = []
  }
}

function setDocProgress(value, text) {
  docProgress.value = Math.max(0, Math.min(100, value))
  docStatusText.value = text
}

function calculateTextCost(charCount, featureCode) {
  if (!charCount) return 0
  const item = pricing.value.find(value => value.featureCode === featureCode)
  const unitCost = Number(item?.costPoints ?? (featureCode === 'DOCUMENT_DOUBLE' ? 20 : 10))
  return Math.ceil(charCount / 1000) * Math.max(0, unitCost)
}

function modeLabelFromMode(mode = '') {
  return modes.find(item => item.apiMode === mode || item.value === mode)?.label || '智能降重'
}

function documentModeLabel(item = {}) {
  return item.modeName || modeLabelFromMode(item.mode)
}

function isRewriteDocument(item = {}) {
  return (item.sourceFeature || 'REWRITE') === 'REWRITE' &&
    String(item.fileName || '').toLowerCase().endsWith('.docx')
}

function jobProgress(job = {}) {
  if (job.status === 'SUCCESS' || job.status === 'FAILED') return 100
  const total = job.totalParagraphs || 0
  const done = job.processedParagraphs || 0
  if (!total) return ['PENDING', 'RUNNING'].includes(job.status) ? 12 : 0
  return Math.min(99, Math.round((done / total) * 100))
}

function buildDiffHtml(original, optimized) {
  const safe = escapeHtml(optimized || '点击开始文本优化后，这里显示 AI 返回的最终结果。')
  if (!original || !rewrittenText.value) return safe
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
  loadPricing()
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
  font-size: clamp(36px, 4.8vw, 56px);
  line-height: 1.05;
}

.hero-strip p {
  max-width: 760px;
  margin: 0;
  color: var(--muted);
  font-size: 17px;
  line-height: 1.7;
}

.document-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(360px, 0.88fr);
  gap: 28px;
  margin-bottom: 16px;
  padding: 24px;
  border-radius: 16px;
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

.upload-head,
.text-head {
  align-items: center;
}

.mini-label {
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

.document-upload,
.document-process {
  min-width: 0;
}

.doc-drop {
  display: grid;
  place-items: center;
  min-height: 292px;
  padding: 26px;
  border-radius: 16px;
  text-align: center;
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

.doc-drop strong {
  margin-top: 14px;
  color: var(--text);
  font-size: 18px;
}

.doc-drop p {
  max-width: 430px;
  margin: 4px 0 12px;
  color: var(--muted);
  line-height: 1.7;
}

.doc-icon {
  display: grid;
  place-items: center;
  width: 62px;
  height: 62px;
  border-radius: 16px;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  background: linear-gradient(135deg, var(--cyan), var(--primary));
  box-shadow: 0 0 35px rgba(0, 210, 255, 0.25);
}

.hidden-input {
  display: none;
}

.file-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 14px 0 0;
}

.file-summary div,
.info-list div {
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.035);
}

.file-summary div {
  min-width: 0;
  padding: 12px;
}

.file-summary dt,
.info-list dt {
  color: var(--muted);
  font-size: 13px;
}

.file-summary dd,
.info-list dd {
  min-width: 0;
  margin: 6px 0 0;
  overflow: hidden;
  color: var(--text);
  font-weight: 720;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-number {
  display: block;
  color: var(--text);
  font-size: 22px;
}

.info-list {
  display: grid;
  gap: 12px;
  margin: 0 0 22px;
}

.info-list div {
  padding: 13px 14px;
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

.plain-button {
  width: 100%;
  margin-top: 12px;
  border: 0;
  color: var(--muted);
  background: transparent;
  cursor: pointer;
}

.plain-button:hover {
  color: var(--text);
}

.text-panel,
.history-panel {
  padding: 22px;
  border-radius: 16px;
}

.text-panel {
  margin-bottom: 16px;
}

.text-actions,
.result-card-head,
.result-card-head div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.text-submit {
  min-height: 42px;
  padding: 0 18px;
  border-radius: 12px;
}

.compare-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
}

.compare-card {
  min-height: 340px;
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

.text-input {
  width: 100%;
  min-height: 254px;
  margin-top: 12px;
  padding: 0;
  resize: vertical;
  border: 0;
  color: var(--text);
  background: transparent;
  outline: none;
  line-height: 1.85;
}

.input-card small {
  color: var(--muted);
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

.result-card-head {
  justify-content: space-between;
}

.result-card-head .active {
  color: var(--text);
  border-color: rgba(0, 210, 255, 0.45);
  background: rgba(0, 210, 255, 0.1);
}

.inline-loading,
.history-empty {
  display: grid;
  place-items: center;
  min-height: 260px;
  color: var(--muted);
  text-align: center;
}

.inline-loading strong {
  margin-top: 12px;
  color: var(--text);
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
  grid-template-columns: minmax(220px, 1fr) 150px 160px 90px;
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
  .document-panel,
  .compare-grid {
    grid-template-columns: 1fr;
  }

  .file-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .table-row {
    grid-template-columns: minmax(180px, 1fr) 120px 130px 78px;
  }
}

@media (max-width: 760px) {
  .rewrite-product {
    width: min(100% - 28px, 1280px);
  }

  .section-title-row,
  .upload-head,
  .text-head,
  .text-actions,
  .result-card-head,
  .result-card-head div {
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

  .file-summary,
  .table-row {
    grid-template-columns: 1fr;
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
    gap: 8px;
    min-height: auto;
    padding: 14px;
    border: 1px solid var(--line);
    border-radius: 14px;
    background: rgba(255, 255, 255, 0.045);
  }
}
</style>
