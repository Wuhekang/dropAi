<template>
  <main class="word-studio">
    <nav class="studio-nav">
      <button class="brand" type="button" @click="router.push('/dashboard')">
        <i>D</i>
        <span><b>Dokiai Academic</b><small>WORD FORMAT STUDIO</small></span>
      </button>
      <div>
        <span>Template-driven</span>
        <button type="button" @click="router.push('/dashboard')">返回工作台</button>
      </div>
    </nav>

    <header class="hero">
      <div class="eyebrow"><i></i>DOKIAI WORD FORMAT WORKFLOW</div>
      <h1>上传学校模板，自动得到<br /><em>规范格式的论文 DOCX</em></h1>
      <p>识别模板中的标题、正文、图名、表名与表格规则，在保留论文内容的前提下一键完成格式整理。</p>
      <div class="hero-actions">
        <button class="primary" type="button" @click="scrollToWorkspace">开始修改格式　→</button>
        <button class="secondary" type="button" @click="showFlow = !showFlow">查看处理流程　⌄</button>
      </div>
      <div class="support">
        <span>模板支持</span><b>DOC</b><b>DOCX</b><b>DOTX</b><span class="separator">论文原稿</span><b>DOCX</b>
      </div>
      <i class="orb one"></i>
      <i class="orb two"></i>
    </header>

    <section v-if="screen === 'upload'" ref="workspaceSection" class="upload-workspace">
      <div class="upload-grid">
        <article class="glass upload-card">
          <header>
            <div><small>STEP 01 · SCHOOL TEMPLATE</small><h2>上传学校格式模板</h2></div>
            <span class="chip">DOC / DOCX / DOTX</span>
          </header>
          <label
            class="drop-zone"
            :class="{ selected: templateFile }"
            tabindex="0"
            @dragover.prevent
            @drop.prevent="handleDrop('template', $event)"
            @keydown.enter.prevent="openPicker('template')"
            @keydown.space.prevent="openPicker('template')"
          >
            <input ref="templateInput" type="file" accept=".doc,.docx,.dotx" @change="handleSelection('template', $event)" />
            <span class="upload-icon template-icon">T</span>
            <template v-if="!templateFile">
              <strong>拖入学校模板，或点击选择</strong>
              <p>最大 30 MB · DOC/DOTX 需 Windows Word 环境</p>
              <div><b>标题格式</b><b>图表格式</b><b>页面设置</b></div>
            </template>
            <template v-else>
              <span class="file-type">{{ fileExtension(templateFile) }}</span>
              <strong>{{ templateFile.name }}</strong>
              <p>{{ formatBytes(templateFile.size) }} · 模板已就绪</p>
              <button type="button" @click.prevent.stop="clearFile('template')">重新选择</button>
            </template>
          </label>
          <p class="card-note"><i>✓</i> 系统将从模板中提取版式，不会修改模板文件。</p>
        </article>

        <article class="glass upload-card">
          <header>
            <div><small>STEP 02 · SOURCE DOCUMENT</small><h2>上传论文原稿</h2></div>
            <span class="chip source-chip">DOCX ONLY</span>
          </header>
          <label
            class="drop-zone"
            :class="{ selected: sourceFile }"
            tabindex="0"
            @dragover.prevent
            @drop.prevent="handleDrop('source', $event)"
            @keydown.enter.prevent="openPicker('source')"
            @keydown.space.prevent="openPicker('source')"
          >
            <input ref="sourceInput" type="file" accept=".docx" @change="handleSelection('source', $event)" />
            <span class="upload-icon source-icon">W</span>
            <template v-if="!sourceFile">
              <strong>拖入论文原稿，或点击选择</strong>
              <p>论文原稿须为 DOCX · 最大 100 MB</p>
              <div><b>保留正文</b><b>保留图片</b><b>另存结果</b></div>
            </template>
            <template v-else>
              <span class="file-type">DOCX</span>
              <strong>{{ sourceFile.name }}</strong>
              <p>{{ formatBytes(sourceFile.size) }} · 原稿已就绪</p>
              <button type="button" @click.prevent.stop="clearFile('source')">重新选择</button>
            </template>
          </label>
          <p class="card-note"><i>✓</i> 结果将生成新文件，论文原稿不会被覆盖。</p>
        </article>
      </div>

      <article class="glass options-card">
        <header>
          <div><small>STEP 03 · FORMAT OPTIONS</small><h2>补充格式要求</h2></div>
          <span class="ready-state" :class="{ ready: canStart }">{{ canStart ? '文件已齐全' : '等待两个文件' }}</span>
        </header>
        <div class="option-grid">
          <label class="instruction-field">
            <span>自然语言补充要求 <small>选填 · {{ instructions.length }}/1000</small></span>
            <textarea
              v-model="instructions"
              maxlength="1000"
              placeholder="例如：一级标题保持居中，所有图名放在图片下方；未填写时完全以学校模板为准。"
            ></textarea>
          </label>
          <div class="doubao-option">
            <div class="doubao-heading">
              <i>D</i>
              <span><b>Doki 理解补充要求</b><small>适合较复杂的自然语言规则</small></span>
              <el-switch v-model="useDoubao" :disabled="!instructions.trim()" />
            </div>
            <ul>
              <li><i>✓</i> 未开启时仍可按模板自动修改</li>
              <li><i>✓</i> 开启后由服务端解析补充要求</li>
              <li><i>✓</i> 论文与模板始终作为独立文件处理</li>
            </ul>
          </div>
        </div>
        <button class="generate" type="button" :disabled="!canStart" @click="startFormatting">
          <span>✦</span>开始智能修改格式<b>→</b>
        </button>
        <p class="safe-note">上传后将创建独立任务；离开页面后可通过任务链接恢复进度。</p>
      </article>
    </section>

    <section v-else ref="workspaceSection" class="workflow-center">
      <article class="glass task-card" :class="{ failed: screen === 'error', complete: screen === 'done' }">
        <div class="task-icon">
          <span v-if="screen === 'done'">✓</span>
          <span v-else-if="screen === 'error'">!</span>
          <i v-else></i>
        </div>
        <div>
          <small>{{ taskEyebrow }}</small>
          <h2>{{ taskTitle }}</h2>
          <p>{{ taskDescription }}</p>
        </div>
        <strong>{{ displayProgress }}<small>%</small></strong>
        <div class="master-progress"><i :style="{ width: `${displayProgress}%` }"></i></div>
        <footer>
          <span>当前步骤：<b>{{ activeStepLabel }}</b></span>
          <span>任务状态：<b>{{ statusLabel }}</b></span>
          <span>任务 ID：<b>{{ shortId }}</b></span>
        </footer>
      </article>

      <article class="glass flow-card">
        <header>
          <div><small>LIVE WORKFLOW</small><h2>论文格式处理流程</h2></div>
          <span>{{ completedStepCount }}/6 已完成</span>
        </header>
        <div class="steps">
          <div v-for="item in workflowSteps" :key="item.no" :class="item.state">
            <span><i v-if="item.state === 'done'">✓</i><b v-else>{{ item.no }}</b></span>
            <div><small>STEP {{ item.no }}</small><strong>{{ item.title }}</strong><p>{{ item.description }}</p></div>
            <em>{{ stateLabel(item.state) }}</em>
          </div>
        </div>
      </article>

      <div class="detail-grid">
        <article class="glass file-summary">
          <header><div><small>INPUT FILES</small><h2>本次处理文件</h2></div><span class="success-dot">文件已接收</span></header>
          <dl>
            <div><dt>T</dt><dd><small>学校模板</small><strong>{{ currentTemplateName }}</strong></dd></div>
            <div><dt>W</dt><dd><small>论文原稿</small><strong>{{ currentSourceName }}</strong></dd></div>
          </dl>
        </article>
        <article class="glass server-status">
          <header><div><small>SERVER STATUS</small><h2>实时任务信息</h2></div><span>{{ formattedUpdatedAt }}</span></header>
          <div class="status-message"><i>i</i><p>{{ taskDescription }}</p></div>
          <dl><div><dt>服务端阶段</dt><dd>{{ job.stage || '等待服务端更新' }}</dd></div><div><dt>创建时间</dt><dd>{{ formattedCreatedAt }}</dd></div></dl>
        </article>
      </div>

      <article v-if="screen === 'done'" class="glass result-card">
        <div class="result-mark">✓</div>
        <div class="result-copy">
          <small>FORMATTED DOCUMENT READY</small><h2>论文格式修改完成</h2><p>{{ resultMessage }}</p>
          <div v-if="resultChangedCount !== null || resultWarnings.length || resultTemplateNotes.length" class="result-notices">
            <b v-if="resultChangedCount !== null">已完成 {{ resultChangedCount }} 项格式调整</b>
            <ul v-if="resultWarnings.length"><li v-for="warning in resultWarnings" :key="warning">{{ warning }}</li></ul>
            <details v-if="resultTemplateNotes.length">
              <summary>查看模板识别说明（{{ resultTemplateNotes.length }}）</summary>
              <ul><li v-for="note in resultTemplateNotes" :key="note">{{ note }}</li></ul>
            </details>
          </div>
        </div>
        <div class="result-stats">
          <span><b>100%</b>处理完成</span>
          <span><b>DOCX</b>输出格式</span>
          <span><b>{{ formattedUpdatedAt }}</b>完成时间</span>
        </div>
        <div class="result-actions">
          <button class="primary" type="button" :disabled="downloading" @click="downloadResult">{{ downloading ? '正在下载…' : '下载格式化论文 ↓' }}</button>
          <button class="text-button" type="button" @click="reset">处理另一篇论文</button>
        </div>
      </article>

      <article v-if="screen === 'error'" class="glass error-card">
        <span>!</span>
        <div><small>FORMAT TASK EXCEPTION</small><h2>格式处理任务未完成</h2><p>{{ errorMessage || taskDescription }}</p></div>
        <button v-if="recoverableJobId" class="secondary" type="button" @click="reloadJob">再次查询任务</button>
        <button class="primary" type="button" @click="reset">重新上传</button>
      </article>
    </section>

    <section v-if="showFlow && screen === 'upload'" class="flow-preview glass">
      <small>HOW IT WORKS</small><h2>从学校模板到规范论文，六步自动完成</h2>
      <div><span v-for="item in idleWorkflow" :key="item.no"><b>{{ item.no }}</b><strong>{{ item.title }}</strong><small>{{ item.description }}</small></span></div>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { createWordFormatJob, downloadWordFormatResult, getWordFormatJob } from '../../api/rewrite'

const MAX_TEMPLATE_SIZE = 30 * 1024 * 1024
const MAX_SOURCE_SIZE = 100 * 1024 * 1024
const POLL_INTERVAL = 1400
const templateExtensions = new Set(['doc', 'docx', 'dotx'])
const sourceExtensions = new Set(['docx'])
const terminalStatuses = new Set(['SUCCESS', 'FAILED'])

const router = useRouter()
const route = useRoute()
const workspaceSection = ref(null)
const templateInput = ref(null)
const sourceInput = ref(null)
const templateFile = ref(null)
const sourceFile = ref(null)
const instructions = ref('')
const useDoubao = ref(false)
const showFlow = ref(false)
const screen = ref('upload')
const uploadProgress = ref(0)
const job = ref({})
const errorMessage = ref('')
const downloading = ref(false)

let pollTimer = null
let pollGeneration = 0
let pollFailures = 0

const stepData = [
  { no: '01', key: 'upload', title: '文件上传', description: '安全接收模板与论文原稿' },
  { no: '02', key: 'template', title: '模板提取', description: '读取页面、标题、正文与图表规则' },
  { no: '03', key: 'analyze', title: '结构识别', description: '识别论文正文、标题、图表与参考文献' },
  { no: '04', key: 'format', title: '格式套用', description: '按模板规则修改论文格式' },
  { no: '05', key: 'validate', title: '完整校验', description: '检查内容、图片、表格与版式完整性' },
  { no: '06', key: 'output', title: '成果输出', description: '生成可下载的规范 DOCX' }
]

const canStart = computed(() => Boolean(templateFile.value && sourceFile.value))
const normalizedStatus = computed(() => String(job.value.status || '').toUpperCase())
const displayProgress = computed(() => {
  if (screen.value === 'submitting') return clampProgress(uploadProgress.value)
  if (normalizedStatus.value === 'SUCCESS') return 100
  return clampProgress(job.value.progress)
})
const activeStepIndex = computed(() => inferStepIndex(job.value, screen.value, displayProgress.value))
const idleWorkflow = computed(() => stepData.map(item => ({ ...item, state: 'waiting' })))
const workflowSteps = computed(() => stepData.map((item, index) => {
  let state = 'waiting'
  if (normalizedStatus.value === 'SUCCESS' || index < activeStepIndex.value) state = 'done'
  else if (normalizedStatus.value === 'FAILED' && index === activeStepIndex.value) state = 'error'
  else if (index === activeStepIndex.value) state = 'active'
  return { ...item, state }
}))
const completedStepCount = computed(() => workflowSteps.value.filter(item => item.state === 'done').length)
const activeStepLabel = computed(() => stepData[Math.min(activeStepIndex.value, stepData.length - 1)]?.title || '文件上传')
const shortId = computed(() => String(job.value.id || 'PREPARING').slice(0, 8).toUpperCase())
const statusLabel = computed(() => ({
  QUEUED: '排队中',
  RUNNING: '处理中',
  SUCCESS: '已完成',
  FAILED: '处理失败'
})[normalizedStatus.value] || (screen.value === 'submitting' ? '上传中' : '准备中'))
const taskEyebrow = computed(() => screen.value === 'done' ? 'FORMAT COMPLETE' : screen.value === 'error' ? 'TASK INTERRUPTED' : screen.value === 'submitting' ? 'FILES ARE UPLOADING' : 'SERVER IS PROCESSING')
const taskTitle = computed(() => screen.value === 'done' ? '规范格式论文已准备好' : screen.value === 'error' ? '任务需要处理' : screen.value === 'submitting' ? '正在上传模板与论文' : `正在进行${activeStepLabel.value}`)
const taskDescription = computed(() => job.value.message || (screen.value === 'submitting' ? '正在将两个文件完整上传至格式处理服务。' : stepData[activeStepIndex.value]?.description || '等待服务端任务状态更新。'))
const currentTemplateName = computed(() => job.value.templateName || templateFile.value?.name || '学校格式模板')
const currentSourceName = computed(() => job.value.sourceName || sourceFile.value?.name || '论文原稿.docx')
const formattedCreatedAt = computed(() => formatDateTime(job.value.createdAt))
const formattedUpdatedAt = computed(() => formatDateTime(job.value.updatedAt || job.value.createdAt))
const recoverableJobId = computed(() => job.value.id || route.query.jobId || '')
const resultMessage = computed(() => {
  const result = parseResult(job.value.result)
  return result.message || result.summary || job.value.message || `已生成 ${job.value.outputName || defaultOutputName()}`
})
const resultWarnings = computed(() => {
  const result = parseResult(job.value.result)
  const values = Array.isArray(job.value.warnings) ? job.value.warnings : result.warnings
  return Array.isArray(values) ? values.filter(Boolean).slice(0, 20) : []
})
const resultTemplateNotes = computed(() => {
  const result = parseResult(job.value.result)
  const values = Array.isArray(job.value.templateNotes) ? job.value.templateNotes : result.templateNotes
  return Array.isArray(values) ? values.filter(Boolean).slice(0, 20) : []
})
const resultChangedCount = computed(() => {
  const result = parseResult(job.value.result)
  const value = job.value.changedCount ?? result.changedCount
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? Math.round(number) : null
})

function clampProgress(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return 0
  return Math.max(0, Math.min(100, Math.round(number)))
}

function inferStepIndex(currentJob, currentScreen, progress) {
  if (currentScreen === 'submitting') return 0
  if (String(currentJob.status || '').toUpperCase() === 'SUCCESS') return 5
  const stage = String(currentJob.stage || '').toUpperCase().replace(/[-\s]+/g, '_')
  const exactStages = {
    QUEUED: 0,
    STARTING: 0,
    VALIDATING: 0,
    EXTRACTING_TEMPLATE: 1,
    APPLYING_RULES: 1,
    ANALYZING_SOURCE: 2,
    PROCESSING: 3,
    INTEGRITY_CHECK: 4,
    COMPLETED: 5,
    FAILED: Math.min(4, Math.max(0, Math.floor(progress / 20)))
  }
  if (Object.prototype.hasOwnProperty.call(exactStages, stage)) return exactStages[stage]
  const mappings = [
    [0, ['QUEUE', 'PENDING', 'UPLOAD', 'RECEIVE', 'CREATED']],
    [1, ['TEMPLATE', 'EXTRACT', 'RULE', 'CONVERT']],
    [2, ['ANALY', 'CLASSIF', 'STRUCTURE', 'SOURCE', 'SCAN']],
    [3, ['FORMAT', 'APPLY', 'PROCESS', 'MODIFY', 'REWRITE']],
    [4, ['VERIFY', 'CHECK', 'QA', 'INTEGRITY']],
    [5, ['OUTPUT', 'EXPORT', 'SAVE', 'PACKAGE', 'COMPLETE', 'SUCCESS']]
  ]
  const matched = mappings.find(([, tokens]) => tokens.some(token => stage.includes(token)))
  if (matched) return matched[0]
  if (progress >= 95) return 5
  if (progress >= 75) return 4
  if (progress >= 45) return 3
  if (progress >= 25) return 2
  if (progress >= 10) return 1
  return 0
}

function extensionOf(file) {
  const name = String(file?.name || '')
  return name.includes('.') ? name.split('.').pop().toLowerCase() : ''
}

function fileExtension(file) {
  return extensionOf(file).toUpperCase() || 'FILE'
}

function validateFile(kind, file) {
  if (!file) return false
  if (!file.size) {
    ElMessage.error('不能上传空文件。')
    return false
  }
  const maxSize = kind === 'template' ? MAX_TEMPLATE_SIZE : MAX_SOURCE_SIZE
  if (file.size > maxSize) {
    ElMessage.error(kind === 'template' ? '学校模板不能超过 30 MB。' : '论文原稿不能超过 100 MB。')
    return false
  }
  const extension = extensionOf(file)
  const allowed = kind === 'template' ? templateExtensions : sourceExtensions
  if (!allowed.has(extension)) {
    ElMessage.error(kind === 'template' ? '模板仅支持 .doc、.docx 或 .dotx。' : '论文原稿仅支持 .docx。')
    return false
  }
  return true
}

function setFile(kind, file) {
  if (!validateFile(kind, file)) {
    clearNativeInput(kind)
    return
  }
  if (kind === 'template') templateFile.value = file
  else sourceFile.value = file
}

function handleSelection(kind, event) {
  setFile(kind, event.target.files?.[0] || null)
}

function handleDrop(kind, event) {
  setFile(kind, event.dataTransfer?.files?.[0] || null)
}

function openPicker(kind) {
  const input = kind === 'template' ? templateInput.value : sourceInput.value
  input?.click()
}

function clearNativeInput(kind) {
  const input = kind === 'template' ? templateInput.value : sourceInput.value
  if (input) input.value = ''
}

function clearFile(kind) {
  if (kind === 'template') templateFile.value = null
  else sourceFile.value = null
  clearNativeInput(kind)
}

function scrollToWorkspace() {
  workspaceSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function formatBytes(bytes) {
  const value = Number(bytes || 0)
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(2)} MB`
  return `${Math.max(0.1, value / 1024).toFixed(1)} KB`
}

function formatDateTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 19)
  return date.toLocaleString('zh-CN', { hour12: false }).replaceAll('/', '-')
}

function stateLabel(value) {
  return ({ done: '完成', active: '处理中', waiting: '等待', error: '异常' })[value] || value
}

function parseResult(value) {
  if (value && typeof value === 'object') return value
  if (typeof value !== 'string' || !value.trim()) return {}
  try { return JSON.parse(value) } catch { return { summary: value } }
}

function defaultOutputName() {
  const raw = String(job.value.sourceName || sourceFile.value?.name || '论文原稿.docx')
  return `${raw.replace(/\.docx$/i, '')}_格式修改完成.docx`
}

function safeDownloadName() {
  const raw = String(job.value.outputName || defaultOutputName()).split(/[\\/]/).pop() || defaultOutputName()
  return raw.toLowerCase().endsWith('.docx') ? raw : `${raw}.docx`
}

function isTerminal(status = normalizedStatus.value) {
  return terminalStatuses.has(String(status || '').toUpperCase())
}

function applyJob(nextJob) {
  if (!nextJob || typeof nextJob !== 'object') throw new Error('服务端未返回有效任务信息。')
  job.value = { ...job.value, ...nextJob }
  const status = String(job.value.status || '').toUpperCase()
  if (status === 'SUCCESS') {
    screen.value = 'done'
    stopPolling()
  } else if (status === 'FAILED') {
    errorMessage.value = job.value.message || '服务端未能完成本次格式处理。'
    screen.value = 'error'
    stopPolling()
  } else {
    screen.value = 'working'
  }
}

async function updateJobQuery(id) {
  if (!id || String(route.query.jobId || '') === String(id)) return
  await router.replace({ query: { ...route.query, jobId: String(id) } })
}

function stopPolling() {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = null
  pollGeneration += 1
}

function schedulePoll(id, generation) {
  if (generation !== pollGeneration || isTerminal()) return
  pollTimer = setTimeout(() => pollJob(id, generation), POLL_INTERVAL)
}

async function pollJob(id, generation) {
  if (generation !== pollGeneration) return
  try {
    const latest = await getWordFormatJob(id)
    if (generation !== pollGeneration) return
    pollFailures = 0
    applyJob(latest)
    if (!isTerminal()) schedulePoll(id, generation)
  } catch (error) {
    if (generation !== pollGeneration) return
    pollFailures += 1
    if (pollFailures >= 4) {
      errorMessage.value = error.message || '暂时无法读取任务状态，请稍后重试。'
      screen.value = 'error'
      stopPolling()
      return
    }
    job.value = { ...job.value, message: `任务仍在服务端处理，正在重新连接（${pollFailures}/4）…` }
    schedulePoll(id, generation)
  }
}

function startPolling(id, immediate = false) {
  stopPolling()
  pollFailures = 0
  const generation = pollGeneration
  if (immediate) pollJob(id, generation)
  else schedulePoll(id, generation)
}

async function startFormatting() {
  if (!canStart.value) {
    ElMessage.warning('请先选择学校模板和论文原稿。')
    return
  }
  if (!validateFile('template', templateFile.value) || !validateFile('source', sourceFile.value)) return
  stopPolling()
  screen.value = 'submitting'
  uploadProgress.value = 0
  errorMessage.value = ''
  job.value = {
    status: 'QUEUED',
    progress: 0,
    stage: 'UPLOAD',
    message: '正在上传学校模板与论文原稿。',
    templateName: templateFile.value.name,
    sourceName: sourceFile.value.name
  }
  scrollToWorkspace()
  try {
    const created = await createWordFormatJob({
      template: templateFile.value,
      source: sourceFile.value,
      instructions: instructions.value,
      useDoubao: Boolean(instructions.value.trim()) && useDoubao.value
    }, event => {
      if (event.total) uploadProgress.value = clampProgress((event.loaded / event.total) * 100)
    })
    if (!created?.id) throw new Error('任务创建成功，但服务端未返回任务 ID。')
    applyJob(created)
    await updateJobQuery(created.id)
    if (!isTerminal(created.status)) startPolling(created.id)
  } catch (error) {
    errorMessage.value = error?.responseData?.message || error.message || '任务创建失败，请检查文件后重试。'
    screen.value = 'error'
  }
}

async function restoreJob(id) {
  if (!id) return
  job.value = { id: String(id), status: 'QUEUED', progress: 0, stage: 'QUEUE', message: '正在恢复任务状态…' }
  errorMessage.value = ''
  screen.value = 'working'
  startPolling(String(id), true)
}

function reloadJob() {
  const id = String(recoverableJobId.value || '')
  if (!id) return reset()
  restoreJob(id)
}

async function downloadResult() {
  if (!job.value.id || downloading.value) return
  downloading.value = true
  try {
    const blob = await downloadWordFormatResult(job.value.id)
    if (!(blob instanceof Blob) || !blob.size) throw new Error('服务端返回的结果文件为空。')
    if (blob.type?.includes('json')) {
      const payload = await blob.text()
      let message = '下载失败。'
      try { message = JSON.parse(payload)?.message || message } catch { message = payload || message }
      throw new Error(message)
    }
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = safeDownloadName()
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    ElMessage.success('格式化论文已开始下载。')
  } catch (error) {
    ElMessage.error(error?.responseData?.message || error.message || '下载失败，请稍后重试。')
  } finally {
    downloading.value = false
  }
}

async function reset() {
  stopPolling()
  templateFile.value = null
  sourceFile.value = null
  instructions.value = ''
  useDoubao.value = false
  uploadProgress.value = 0
  job.value = {}
  errorMessage.value = ''
  screen.value = 'upload'
  clearNativeInput('template')
  clearNativeInput('source')
  const query = { ...route.query }
  delete query.jobId
  await router.replace({ query })
}

onMounted(() => {
  const id = Array.isArray(route.query.jobId) ? route.query.jobId[0] : route.query.jobId
  if (id) restoreJob(id)
})

onBeforeUnmount(stopPolling)
</script>

<style scoped>
.word-studio {
  --violet: #7159ef;
  --pink: #df61ae;
  --blue: #4d92ed;
  --green: #2a9a79;
  --ink: #20243a;
  --muted: #747b91;
  min-height: 100vh;
  padding: 20px 28px 72px;
  color: var(--ink);
  background:
    radial-gradient(circle at 8% 12%, #eee9ff 0, transparent 28%),
    radial-gradient(circle at 90% 18%, #e7f4ff 0, transparent 30%),
    linear-gradient(145deg, #fbf9ff, #f1f8ff 55%, #fff7fc);
  font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif;
}

button { cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: .48; }

.studio-nav {
  position: relative;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(1360px, 100%);
  margin: auto;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  border: 0;
  background: none;
  text-align: left;
}

.brand > i {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 13px;
  color: #fff;
  background: linear-gradient(145deg, #4d92ed, var(--violet) 58%, var(--pink));
  box-shadow: 0 12px 26px #7159ef38;
  font-size: 20px;
  font-style: normal;
  font-weight: 900;
}

.brand span { display: grid; }
.brand b { font-size: 15px; }
.brand small { color: #979eb0; font-size: 8px; letter-spacing: .14em; }
.studio-nav > div { display: flex; align-items: center; gap: 12px; }
.studio-nav > div > span { padding: 7px 11px; border: 1px solid #dcd7f7; border-radius: 99px; color: #6652d2; background: #ffffff99; font-size: 10px; }
.studio-nav > div > button { border: 0; color: #5f6578; background: none; }

.hero {
  position: relative;
  overflow: hidden;
  width: min(1360px, 100%);
  margin: 20px auto 26px;
  padding: 66px 7%;
  border: 1px solid #ffffffcf;
  border-radius: 32px;
  background: linear-gradient(125deg, #ffffffdf, #f5f1ffcf 48%, #eef8ffdf);
  box-shadow: 0 34px 90px #51438a17;
  backdrop-filter: blur(24px);
}

.eyebrow,
.glass header small,
.task-card > div > small,
.result-card > div > small,
.error-card div > small,
.flow-preview > small {
  color: #6f58dd;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: .16em;
}

.eyebrow i { display: inline-block; width: 6px; height: 6px; margin-right: 8px; border-radius: 50%; background: var(--pink); box-shadow: 0 0 0 5px #df61ae1a; }
.hero h1 { position: relative; z-index: 1; margin: 18px 0 16px; font-size: clamp(40px, 5vw, 66px); line-height: 1.06; letter-spacing: -.045em; }
.hero h1 em { color: transparent; background: linear-gradient(100deg, var(--violet), #9b64e5, var(--pink)); background-clip: text; font-style: normal; }
.hero > p { max-width: 650px; color: var(--muted); font-size: 17px; line-height: 1.8; }
.hero-actions { display: flex; gap: 12px; margin: 28px 0 22px; }
.primary,
.secondary,
.generate { border-radius: 14px; font-weight: 700; }
.primary,
.generate { border: 0; color: #fff; background: linear-gradient(110deg, var(--violet), #9962e6 60%, var(--pink)); box-shadow: 0 16px 34px #7159ef32; }
.primary,
.secondary { padding: 13px 20px; }
.secondary { border: 1px solid #dcd5ef; color: #554b8d; background: #ffffffbf; }
.support { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; color: #949aad; font-size: 11px; }
.support b,
.file-type { padding: 5px 8px; border-radius: 6px; color: #7064a8; background: #fff; font-size: 9px; }
.support .separator { margin-left: 10px; }
.orb { position: absolute; border-radius: 50%; opacity: .58; filter: blur(3px); }
.orb.one { right: 7%; top: 17%; width: 220px; height: 220px; background: radial-gradient(circle at 30% 30%, #fff, #d6ccff 45%, #7dbbf4); }
.orb.two { right: 23%; bottom: -76px; width: 150px; height: 150px; background: linear-gradient(145deg, #ffd9ef, #baa4ff); }

.glass { border: 1px solid #ffffffdc; border-radius: 24px; background: #ffffffc2; box-shadow: 0 22px 65px #41386f13; backdrop-filter: blur(24px); }
.upload-workspace,
.workflow-center,
.flow-preview { width: min(1240px, 100%); margin: auto; scroll-margin-top: 20px; }
.upload-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.upload-card,
.options-card,
.flow-card,
.file-summary,
.server-status { padding: 26px; }
.glass > header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.glass h2 { margin: 7px 0; font-size: 23px; }
.chip,
.ready-state,
.success-dot { padding: 6px 9px; border-radius: 99px; color: #6555cb; background: #eeeaff; font-size: 9px; font-weight: 800; }
.source-chip { color: #2e7c6a; background: #e6f6f0; }

.drop-zone {
  display: grid;
  place-items: center;
  min-height: 274px;
  margin-top: 18px;
  padding: 25px;
  border: 1.5px dashed #bdb0ee;
  border-radius: 20px;
  outline: none;
  background: linear-gradient(145deg, #faf8ff, #f3f9ff);
  text-align: center;
  transition: .25s;
}

.drop-zone:hover,
.drop-zone:focus-visible,
.drop-zone.selected { border-color: var(--violet); box-shadow: inset 0 0 0 4px #7159ef0b; transform: translateY(-1px); }
.drop-zone input { display: none; }
.upload-icon { display: grid; place-items: center; width: 66px; height: 66px; margin-bottom: 14px; border-radius: 20px; color: #fff; box-shadow: 0 18px 36px #7159ef2d; font-size: 24px; font-weight: 900; }
.template-icon { background: linear-gradient(145deg, #8876f5, var(--violet), var(--pink)); }
.source-icon { background: linear-gradient(145deg, #55a8ef, #3979d9, #755fec); }
.drop-zone strong { max-width: 430px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.drop-zone p { margin: 7px; color: #969daf; font-size: 12px; }
.drop-zone > div { display: flex; flex-wrap: wrap; justify-content: center; gap: 6px; margin-top: 11px; }
.drop-zone > div b { padding: 5px 8px; border-radius: 6px; color: #7064a8; background: #fff; font-size: 9px; }
.drop-zone > button { margin-top: 10px; border: 0; color: var(--violet); background: none; }
.file-type { margin-bottom: 10px; }
.card-note { display: flex; align-items: center; gap: 8px; margin: 16px 2px 0; color: #838a9e; font-size: 10px; }
.card-note i { display: grid; place-items: center; width: 19px; height: 19px; border-radius: 50%; color: #298d6e; background: #e2f4ed; font-style: normal; }

.options-card { margin-top: 18px; }
.ready-state { color: #8c839d; background: #f1eff4; }
.ready-state.ready { color: #258266; background: #e2f4ed; }
.option-grid { display: grid; grid-template-columns: 1.2fr .8fr; gap: 18px; margin-top: 18px; }
.instruction-field { display: grid; gap: 8px; }
.instruction-field > span { color: #6f768a; font-size: 11px; }
.instruction-field > span small { float: right; color: #9b91b1; }
.instruction-field textarea { min-height: 142px; padding: 14px; resize: vertical; border: 1px solid #e0daef; border-radius: 14px; outline: none; color: var(--ink); background: #ffffffbf; line-height: 1.65; }
.instruction-field textarea:focus { border-color: #9887e7; box-shadow: 0 0 0 4px #7159ef0c; }
.doubao-option { padding: 17px; border-radius: 16px; background: linear-gradient(135deg, #f1edff, #f4f9ff); }
.doubao-heading { display: flex; align-items: center; gap: 11px; }
.doubao-heading > i { display: grid; place-items: center; flex: 0 0 38px; height: 38px; border-radius: 12px; color: #fff; background: linear-gradient(145deg, var(--violet), var(--pink)); font-style: normal; font-weight: 800; }
.doubao-heading > span { display: grid; flex: 1; }
.doubao-heading small { margin-top: 3px; color: #8c92a3; font-size: 9px; }
.doubao-option ul { display: grid; gap: 10px; margin: 17px 0 0; padding: 0; list-style: none; color: #747b8e; font-size: 10px; }
.doubao-option li { display: flex; align-items: center; gap: 8px; }
.doubao-option li i { color: #258266; font-style: normal; }
.generate { display: flex; align-items: center; justify-content: center; gap: 10px; width: 100%; margin-top: 20px; padding: 14px; }
.generate b { margin-left: auto; }
.safe-note { margin: 10px 0 0; color: #a0a6b4; font-size: 9px; text-align: center; }

.workflow-center { display: grid; gap: 18px; }
.task-card { display: grid; grid-template-columns: auto 1fr auto; gap: 18px; align-items: center; padding: 25px 28px; }
.task-icon { display: grid; place-items: center; width: 56px; height: 56px; border-radius: 17px; color: #fff; background: linear-gradient(145deg, var(--violet), var(--pink)); }
.task-icon i { width: 23px; height: 23px; border: 3px solid #ffffff50; border-top-color: #fff; border-radius: 50%; animation: spin 1s linear infinite; }
.task-icon span { font-size: 24px; font-weight: 900; }
.task-card.failed .task-icon { background: #d85278; }
.task-card.complete .task-icon { background: linear-gradient(145deg, #39ae83, #6b62e9); }
.task-card h2 { margin: 6px 0; }
.task-card p { margin: 0; color: var(--muted); font-size: 12px; }
.task-card > strong { font-size: 38px; }
.task-card > strong small { font-size: 15px; }
.master-progress { grid-column: 1 / -1; height: 8px; overflow: hidden; border-radius: 99px; background: #ebe7f5; }
.master-progress i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--violet), var(--pink)); transition: width .45s; }
.task-card footer { grid-column: 1 / -1; display: flex; flex-wrap: wrap; gap: 28px; color: #8d94a6; font-size: 10px; }
.task-card footer b { color: #494f66; }
.flow-card > header > span { color: #7b6acb; font-size: 11px; }
.steps { display: grid; grid-template-columns: repeat(6, 1fr); gap: 8px; margin-top: 22px; }
.steps > div { position: relative; display: grid; grid-template-columns: auto 1fr; gap: 9px; min-height: 132px; padding: 15px; border: 1px solid #ebe7f4; border-radius: 16px; background: #fff; }
.steps > div:not(:last-child)::after { position: absolute; z-index: 2; right: -11px; top: 35px; width: 14px; height: 2px; background: #ddd7ef; content: ""; }
.steps > div > span { display: grid; place-items: center; width: 29px; height: 29px; border-radius: 9px; color: #8a829f; background: #f0eef5; }
.steps .done > span,
.steps .active > span { color: #fff; background: linear-gradient(145deg, var(--violet), var(--pink)); }
.steps .error > span { color: #fff; background: #d85278; }
.steps .active { border-color: #a997f3; box-shadow: 0 10px 25px #7159ef18; }
.steps > div > div { display: grid; align-content: start; }
.steps > div small { color: #aaa5b7; font-size: 7px; }
.steps > div strong { margin: 4px 0; font-size: 12px; }
.steps > div p { grid-column: 1 / -1; margin: 8px 0 0; color: #969cad; font-size: 9px; line-height: 1.5; }
.steps > div em { grid-column: 1 / -1; align-self: end; color: #9b93aa; font-size: 8px; font-style: normal; }
.steps .done em { color: #37a57d; }
.steps .active em { color: var(--violet); }
.steps .error em { color: #c84268; }

.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.file-summary dl { display: grid; gap: 10px; margin: 16px 0 0; }
.file-summary dl > div { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 11px; padding: 12px; border-radius: 13px; background: #f7f5fc; }
.file-summary dt { display: grid; place-items: center; width: 37px; height: 42px; border-radius: 10px; color: #fff; background: linear-gradient(145deg, var(--violet), var(--blue)); font-weight: 900; }
.file-summary dd { display: grid; min-width: 0; margin: 0; }
.file-summary dd small { color: #969cad; font-size: 8px; }
.file-summary dd strong { overflow: hidden; margin-top: 4px; text-overflow: ellipsis; white-space: nowrap; font-size: 11px; }
.server-status header > span { color: #9aa0ae; font-size: 9px; }
.status-message { display: grid; grid-template-columns: auto 1fr; align-items: center; gap: 10px; min-height: 73px; margin-top: 16px; padding: 14px; border-radius: 13px; background: linear-gradient(135deg, #f1edff, #f3f9ff); }
.status-message i { display: grid; place-items: center; width: 29px; height: 29px; border-radius: 9px; color: #fff; background: var(--violet); font-style: normal; }
.status-message p { margin: 0; color: #70778c; font-size: 10px; line-height: 1.6; }
.server-status dl { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin: 10px 0 0; }
.server-status dl div { padding: 10px; border-radius: 10px; background: #faf9fc; }
.server-status dt { color: #9a9fac; font-size: 8px; }
.server-status dd { overflow: hidden; margin: 4px 0 0; color: #555c70; text-overflow: ellipsis; white-space: nowrap; font-size: 10px; }

.result-card { display: grid; grid-template-columns: auto 1fr auto auto; gap: 20px; align-items: center; padding: 28px; }
.result-mark { display: grid; place-items: center; width: 58px; height: 58px; border-radius: 18px; color: #fff; background: linear-gradient(145deg, #42b98d, #6c5fea); font-size: 25px; }
.result-card h2 { margin: 5px 0; }
.result-card p { margin: 0; color: var(--muted); font-size: 11px; }
.result-notices { display: grid; gap: 7px; margin-top: 10px; color: #677087; font-size: 10px; }
.result-notices > b { color: #3e806c; }
.result-notices ul { display: grid; gap: 4px; margin: 0; padding-left: 16px; }
.result-notices summary { cursor: pointer; color: #695ac4; }
.result-notices details ul { margin-top: 6px; }
.result-stats { display: flex; gap: 8px; }
.result-stats span { display: grid; min-width: 92px; padding: 10px; border-radius: 11px; color: #9197a8; background: #f5f3fb; font-size: 8px; }
.result-stats b { overflow: hidden; color: #32374c; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.result-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 7px; }
.result-actions button { padding: 10px 13px; }
.text-button { width: 100%; border: 0; color: #7160c9; background: none; }
.error-card { display: grid; grid-template-columns: auto 1fr auto auto; gap: 16px; align-items: center; padding: 25px; border-color: #ffd3df; }
.error-card > span { display: grid; place-items: center; width: 50px; height: 50px; border-radius: 15px; color: #fff; background: #df557a; font-size: 24px; }
.error-card h2 { margin: 6px 0; }
.error-card p { margin: 0; color: #a45a70; }

.flow-preview { margin-top: 18px; padding: 30px; text-align: center; }
.flow-preview > div { display: grid; grid-template-columns: repeat(6, 1fr); gap: 8px; margin-top: 20px; }
.flow-preview span { display: grid; gap: 7px; padding: 15px; border-radius: 14px; background: #f7f5fc; }
.flow-preview span > b { color: var(--violet); font-size: 19px; }
.flow-preview span small { color: #969cad; font-size: 8px; }

@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 1100px) {
  .upload-grid,
  .option-grid,
  .detail-grid { grid-template-columns: 1fr; }
  .steps,
  .flow-preview > div { grid-template-columns: repeat(3, 1fr); }
  .result-card { grid-template-columns: auto 1fr; }
  .result-stats,
  .result-actions { grid-column: 1 / -1; }
  .orb { opacity: .28; }
}

@media (max-width: 720px) {
  .word-studio { padding: 14px 12px 48px; }
  .studio-nav > div > span { display: none; }
  .hero { padding: 44px 24px; }
  .hero h1 { font-size: 37px; }
  .hero-actions { align-items: stretch; flex-direction: column; }
  .upload-card,
  .options-card,
  .flow-card,
  .file-summary,
  .server-status { padding: 20px; }
  .glass > header { align-items: flex-start; flex-direction: column; }
  .drop-zone { min-height: 240px; }
  .task-card { grid-template-columns: auto 1fr; }
  .task-card > strong { grid-column: 1 / -1; }
  .task-card footer { flex-direction: column; gap: 6px; }
  .steps,
  .flow-preview > div { grid-template-columns: 1fr 1fr; }
  .result-card,
  .error-card { grid-template-columns: 1fr; }
  .result-stats { display: grid; grid-template-columns: repeat(3, 1fr); }
  .error-card button { width: 100%; }
}

@media (max-width: 460px) {
  .steps,
  .flow-preview > div,
  .result-stats,
  .server-status dl { grid-template-columns: 1fr; }
  .support .separator { width: 100%; margin: 4px 0 0; }
}
</style>
