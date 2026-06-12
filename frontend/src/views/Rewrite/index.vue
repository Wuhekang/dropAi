<template>
  <main class="rewrite-page">
    <header class="topbar">
      <div>
        <h1>学术写作优化平台</h1>
        <p>面向论文段落的润色、改写与表达风险辅助分析</p>
      </div>
      <div class="top-tags">
        <el-button text type="primary" @click="router.push('/dashboard')">返回 Dashboard</el-button>
        <el-tag type="success" effect="dark">MVP</el-tag>
        <el-tag :type="aiStatus.testStatus === 'success' ? 'success' : 'danger'">
          {{ aiStatus.testStatus === 'success' ? '模型已连接' : '模型未连接' }}
        </el-tag>
        <el-tag type="primary">{{ modelLabel }}</el-tag>
        <el-tag type="info">{{ username }}</el-tag>
        <el-button text type="danger" @click="signOut">退出</el-button>
      </div>
    </header>

    <el-alert
      class="model-status-alert"
      :type="aiStatus.testStatus === 'success' ? 'success' : 'error'"
      :closable="false"
      show-icon
    >
      <template #title>
        {{ aiStatus.provider || '豆包 Ark' }} / {{ aiStatus.model || '未读取模型配置' }}
      </template>
      <div class="model-status-detail">
        <span>{{ aiStatus.testMessage || '正在校验真实模型连接状态...' }}</span>
        <span v-if="aiStatus.endpoint">Endpoint：{{ aiStatus.endpoint }}</span>
        <el-button text type="primary" :loading="checkingAiStatus" @click="loadAiStatus">重新校验</el-button>
      </div>
    </el-alert>

    <el-card class="panel document-panel" shadow="never">
      <template #header>
        <div class="panel-title">
          <span>文档降重 / 降 AI</span>
          <el-tag :type="documentJob.status === 'SUCCESS' ? 'success' : 'info'">
            {{ documentJob.modeName || 'DOCX' }}
          </el-tag>
        </div>
      </template>

      <div class="mode-grid">
        <button
          v-for="mode in documentModes"
          :key="mode.value"
          class="mode-card"
          :class="{ active: documentMode === mode.value }"
          type="button"
          @click="documentMode = mode.value"
        >
          <strong>{{ mode.label }}</strong>
          <span>{{ mode.description }}</span>
        </button>
      </div>

      <div class="platform-strip">
        <span class="platform-label">检测口径</span>
        <el-segmented
          v-model="targetPlatform"
          :options="platformOptions"
        />
      </div>

      <div class="document-upload">
        <el-upload
          drag
          action=""
          :auto-upload="false"
          :show-file-list="false"
          accept=".docx"
          :on-change="handleDocumentSelected"
        >
          <div class="upload-text">
            <strong>上传 Word 文档</strong>
            <span>默认处理目录后的每一段正文，自动跳过标题、目录和保护段落</span>
          </div>
        </el-upload>

        <div class="document-status">
          <span>文件：{{ documentJob.fileName || '未选择' }}</span>
          <span>模式：{{ currentDocumentMode.label }}</span>
          <span>口径：{{ documentJob.platformName || currentPlatform.label }}</span>
          <span>进度：{{ documentProgress }}%（{{ documentJob.processedParagraphs || 0 }}/{{ documentJob.totalParagraphs || 0 }} 段）</span>
          <span>{{ documentJob.message || '支持 .docx，处理完成后可下载结果文档' }}</span>
          <el-progress :percentage="documentProgress" :status="progressStatus" />
          <div class="controls">
            <el-button
              type="primary"
              :loading="documentUploading"
              :disabled="!selectedDocument || isDocumentRunning || aiStatus.testStatus !== 'success'"
              @click="submitDocument"
            >
              开始整篇处理
            </el-button>
            <el-button
              type="success"
              :disabled="documentJob.status !== 'SUCCESS'"
              @click="downloadOptimizedDocument"
            >
              下载优化文档
            </el-button>
          </div>
        </div>
      </div>

      <el-table
        class="document-job-table"
        :data="documentJobs"
        border
        empty-text="暂无文档任务"
      >
        <el-table-column prop="fileName" label="文件" min-width="220" show-overflow-tooltip />
        <el-table-column prop="modeName" label="模式" width="110" />
        <el-table-column prop="platformName" label="口径" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="jobTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="170">
          <template #default="{ row }">
            {{ jobProgress(row) }}%（{{ row.processedParagraphs || 0 }}/{{ row.totalParagraphs || 0 }}）
          </template>
        </el-table-column>
        <el-table-column prop="message" label="信息" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button text type="primary" @click="openDocumentJob(row)">查看</el-button>
            <el-button text type="success" :disabled="row.status !== 'SUCCESS'" @click="downloadJob(row)">
              下载
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="documentDetailVisible" title="文档任务详情" size="720px">
      <div class="document-detail">
        <div class="document-detail-head">
          <strong>{{ documentJob.fileName || '文档任务' }}</strong>
          <el-tag :type="jobTagType(documentJob.status)">{{ documentJob.status || '--' }}</el-tag>
        </div>
        <div class="document-detail-meta">
          <span>模式：{{ documentJob.modeName || currentDocumentMode.label }}</span>
          <span>口径：{{ documentJob.platformName || currentPlatform.label }}</span>
          <span>进度：{{ documentProgress }}%（{{ documentJob.processedParagraphs || 0 }}/{{ documentJob.totalParagraphs || 0 }}）</span>
          <span>{{ documentJob.message }}</span>
        </div>

        <div class="paragraph-list">
          <section
            v-for="paragraph in documentJob.paragraphs"
            :key="paragraph.index"
            class="paragraph-item"
          >
            <div class="paragraph-title">
              <span>第 {{ paragraph.index + 1 }} 段</span>
              <el-tag :type="paragraphTagType(paragraph.status)">
                {{ paragraph.status }}
              </el-tag>
            </div>
            <p class="paragraph-original">{{ paragraph.originalText }}</p>
            <p v-if="paragraph.rewrittenText" class="paragraph-rewritten">
              {{ paragraph.rewrittenText }}
            </p>
            <span class="paragraph-message">{{ paragraph.message }}</span>
          </section>
        </div>
      </div>
    </el-drawer>

    <section class="workspace">
      <el-card class="panel input-panel" shadow="never">
        <template #header>
          <div class="panel-title">
            <span>原文输入</span>
            <el-tag type="info">{{ originalText.length }} 字</el-tag>
          </div>
        </template>

        <el-input
          v-model="originalText"
          type="textarea"
          :autosize="{ minRows: 15, maxRows: 22 }"
          maxlength="10000"
          show-word-limit
          placeholder="粘贴需要优化的论文段落"
        />

        <div class="controls">
          <el-select v-model="targetPlatform" class="platform-select" placeholder="检测口径">
            <el-option
              v-for="item in platformOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-select v-model="rewriteType" class="type-select" placeholder="选择优化类型">
            <el-option
              v-for="item in rewriteTypes"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="aiStatus.testStatus !== 'success'"
            @click="handleSubmit"
          >
            开始优化
          </el-button>
          <el-button :loading="analyzing" @click="handleAnalyze">
            风险分析
          </el-button>
          <el-button @click="loadDemoText">
            加载演示文本
          </el-button>
        </div>

        <div class="protection-hint">
          <strong>结构保护已启用</strong>
          <span>表格、代码块、URL 与参考文献会在模型调用前锁定，完成后原样恢复。</span>
        </div>
      </el-card>

      <el-card class="panel result-panel" shadow="never">
        <template #header>
          <div class="panel-title">
            <span>优化结果</span>
            <el-tag v-if="currentResult?.aiLevel" :type="levelTagType(currentResult.aiLevel)">
              {{ currentResult.aiLevel }}
            </el-tag>
          </div>
        </template>

        <el-input
          v-model="rewrittenText"
          type="textarea"
          :autosize="{ minRows: 15, maxRows: 22 }"
          readonly
          placeholder="优化后的文本会显示在这里"
        />

        <div class="controls result-actions">
          <el-button :disabled="!rewrittenText" @click="copyResult">复制结果</el-button>
          <el-button type="success" :disabled="!currentResult" @click="confirmSaved">保存记录</el-button>
        </div>
      </el-card>
    </section>

    <section class="analysis-grid">
      <el-card class="panel" shadow="never">
        <template #header>
          <div class="panel-title">
            <span>AI痕迹风险分析</span>
            <el-tag v-if="analysis.level" :type="levelTagType(analysis.level)">
              {{ analysis.level }}
            </el-tag>
          </div>
        </template>

        <div class="score-row">
          <div class="score">{{ analysis.score ?? '--' }}</div>
          <div class="score-meta">
            <strong>风险评分</strong>
            <span>根据句式规整、逻辑词密度、语态特征、词汇分布和论证深度进行自查预估</span>
          </div>
        </div>

        <div class="suggestions">
          <el-tag
            v-for="suggestion in analysis.suggestions"
            :key="suggestion"
            type="warning"
          >
            {{ suggestion }}
          </el-tag>
        </div>
      </el-card>

      <el-card class="panel" shadow="never">
        <template #header>
          <div class="panel-title">
            <span>当前记录</span>
            <el-button text type="primary" @click="loadHistory">刷新</el-button>
          </div>
        </template>

        <div class="current-record">
          <span>ID：{{ currentResult?.id || '--' }}</span>
          <span>类型：{{ currentResult?.rewriteType || rewriteType }}</span>
          <span>提供商：{{ currentResult?.aiProvider || '提交后显示' }}</span>
          <span>模型：{{ currentResult?.aiModel || aiStatus.model || '提交后显示' }}</span>
          <span>创建时间：{{ formatTime(currentResult?.createdAt) }}</span>
        </div>
      </el-card>
    </section>

    <section class="workflow-grid">
      <el-card class="panel" shadow="never">
        <template #header>
          <div class="panel-title">
            <span>受控工作流</span>
            <el-tag type="primary">{{ workflowSteps.length }} 步</el-tag>
          </div>
        </template>

        <el-timeline>
          <el-timeline-item
            v-for="step in workflowSteps"
            :key="step.nodeType"
            :timestamp="step.nodeType"
            placement="top"
          >
            <strong>{{ step.nodeName }}</strong>
            <p>{{ step.summary }}</p>
          </el-timeline-item>
        </el-timeline>
      </el-card>

      <el-card class="panel" shadow="never">
        <template #header>
          <div class="panel-title">
            <span>质量检查</span>
            <el-tag :type="qualityPassed ? 'success' : 'warning'">
              {{ qualityPassed ? '通过' : '待优化' }}
            </el-tag>
          </div>
        </template>

        <div class="quality-list">
          <span :class="{ passed: qualityCheck.meaningPreserved }">原意保持</span>
          <span :class="{ passed: qualityCheck.academicTone }">论文语气</span>
          <span :class="{ passed: qualityCheck.templateReduced }">模板词减少</span>
          <span :class="{ passed: qualityCheck.fluent }">语句通顺</span>
        </div>

        <div class="suggestions" v-if="qualityCheck.issues?.length">
          <el-tag v-for="issue in qualityCheck.issues" :key="issue" type="danger">
            {{ issue }}
          </el-tag>
        </div>
      </el-card>
    </section>

    <el-card class="panel history-panel" shadow="never">
      <template #header>
        <div class="panel-title">
          <span>历史记录</span>
          <el-tag>{{ history.length }} 条</el-tag>
        </div>
      </template>

      <el-table :data="history" v-loading="historyLoading" border height="360">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="rewriteType" label="优化类型" width="140" />
        <el-table-column label="AI评分" width="100">
          <template #default="{ row }">
            <el-tag :type="levelTagType(row.aiLevel)">{{ row.aiScore }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="originalText" label="原文" min-width="220" show-overflow-tooltip />
        <el-table-column prop="rewrittenText" label="优化结果" min-width="240" show-overflow-tooltip />
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="viewDetail(row.id)">详情</el-button>
            <el-button text type="danger" @click="removeRecord(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" title="记录详情" width="720px">
      <div v-if="detail" class="detail">
        <h3>原文</h3>
        <p>{{ detail.originalText }}</p>
        <h3>优化结果</h3>
        <p>{{ detail.rewrittenText }}</p>
        <h3>建议</h3>
        <div class="suggestions">
          <el-tag v-for="item in detail.suggestions" :key="item" type="warning">{{ item }}</el-tag>
        </div>
        <h3>工作流</h3>
        <div class="detail-steps">
          <p v-for="step in detail.workflowSteps" :key="step.nodeType">
            {{ step.nodeName }}：{{ step.summary }}
          </p>
        </div>
      </div>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  analyzeText,
  deleteRewrite,
  downloadDocument,
  getAiStatus,
  getDocumentJob,
  getDocumentJobs,
  getRewriteDetail,
  getRewriteList,
  submitRewrite,
  uploadDocument,
  logout
} from '../../api/rewrite'

const router = useRouter()
const username = sessionStorage.getItem('dropai_username') || '当前账号'
async function signOut() {
  try { await logout() } finally {
    sessionStorage.removeItem('dropai_token')
    sessionStorage.removeItem('dropai_username')
    router.replace('/login')
  }
}

const rewriteTypes = [
  '学术化润色',
  '降重复改写',
  '降低AI写作痕迹',
  '扩写',
  '缩写',
  '语句通顺优化'
]

const originalText = ref('')
const rewrittenText = ref('')
const rewriteType = ref(rewriteTypes[0])
const submitting = ref(false)
const analyzing = ref(false)
const historyLoading = ref(false)
const history = ref([])
const currentResult = ref(null)
const checkingAiStatus = ref(false)
const aiStatus = reactive({
  provider: '豆包 Ark',
  model: '',
  endpoint: '',
  apiKeyConfigured: false,
  testStatus: '',
  testMessage: ''
})
const detailVisible = ref(false)
const detail = ref(null)
const selectedDocument = ref(null)
const documentMode = ref('FULL_AI_REDUCE')
const targetPlatform = ref('GENERAL')
const documentUploading = ref(false)
const documentPollTimer = ref(null)
const documentJobs = ref([])
const documentDetailVisible = ref(false)
const documentJob = reactive({
  jobId: '',
  fileName: '',
  status: '',
  totalParagraphs: 0,
  processedParagraphs: 0,
  rewrittenParagraphs: 0,
  modeName: '',
  platform: 'GENERAL',
  platformName: '通用',
  message: '',
  downloadUrl: '',
  paragraphs: []
})
const analysis = reactive({
  score: null,
  level: '',
  suggestions: []
})
const documentModes = [
  {
    value: 'FULL_AI_REDUCE',
    label: '全文降AI',
    description: '目录后所有正文段落统一优化，跳过标题'
  },
  {
    value: 'DUPLICATE_REDUCE',
    label: '智能降重',
    description: '调整语序与句式，降低重复表达风险'
  },
  {
    value: 'DOUBLE_REDUCE',
    label: '双降',
    description: '一次性同时控制重复表达与AI痕迹'
  },
  {
    value: 'PRECISE_AI_REDUCE',
    label: '精准降AI',
    description: '只处理含明显AI痕迹信号的正文段落'
  }
]
const platformOptions = [
  { value: 'GENERAL', label: '通用' },
  { value: 'CNKI', label: '知网' },
  { value: 'WEIPU', label: '维普' },
  { value: 'WANFANG', label: '万方' },
  { value: 'GEZIDA', label: '格子达' }
]
const workflowSteps = ref([])
const qualityCheck = reactive({
  meaningPreserved: false,
  academicTone: false,
  templateReduced: false,
  fluent: false,
  issues: []
})
const qualityPassed = computed(() =>
  qualityCheck.meaningPreserved &&
  qualityCheck.academicTone &&
  qualityCheck.templateReduced &&
  qualityCheck.fluent
)
const modelLabel = computed(() => {
  if (currentResult.value?.aiProvider) {
    return `${currentResult.value.aiProvider} / ${currentResult.value.aiModel || aiStatus.model}`
  }
  return aiStatus.model || '模型待校验'
})

function loadDemoText() {
  originalText.value = `随着信息技术的快速发展，传统管理方式已经难以满足当前需求。因此，本文基于 Spring Boot 构建系统，通过 MyBatis-Plus 实现数据访问，从而有效提升管理效率。

| 接口 | 方法 | 说明 |
|---|---|---|
| /api/rewrite/submit | POST | 提交优化任务 |

相关配置见 \`application.yml\`，项目文档地址为 https://example.com/docs。

[1] 张三. 学术写作表达研究[J]. 写作研究, 2025(2): 10-15.`
  rewriteType.value = '降低AI写作痕迹'
  targetPlatform.value = 'GENERAL'
  ElMessage.success('演示文本已加载，可直接点击开始优化')
}
const isDocumentRunning = computed(() => ['PENDING', 'RUNNING'].includes(documentJob.status))
const documentProgress = computed(() => {
  return jobProgress(documentJob)
})
const progressStatus = computed(() => {
  if (documentJob.status === 'SUCCESS') return 'success'
  if (documentJob.status === 'FAILED') return 'exception'
  return ''
})
const currentDocumentMode = computed(() =>
  documentModes.find((item) => item.value === documentMode.value) || documentModes[0]
)
const currentPlatform = computed(() =>
  platformOptions.find((item) => item.value === targetPlatform.value) || platformOptions[0]
)

async function handleSubmit() {
  if (!originalText.value.trim()) {
    ElMessage.warning('请输入原文内容')
    return
  }
  if (aiStatus.testStatus !== 'success') {
    ElMessage.error('真实模型尚未连接，已阻止提交，不会生成模拟结果')
    return
  }
  submitting.value = true
  try {
    const result = await submitRewrite({
      originalText: originalText.value,
      rewriteType: rewriteType.value,
      platform: targetPlatform.value
    })
    currentResult.value = result
    rewrittenText.value = result.rewrittenText || ''
    setAnalysis(result)
    setWorkflow(result)
    ElMessage.success('优化完成，记录已保存')
    await loadHistory()
  } finally {
    submitting.value = false
  }
}

async function loadAiStatus() {
  checkingAiStatus.value = true
  try {
    Object.assign(aiStatus, await getAiStatus())
  } catch (error) {
    aiStatus.testStatus = 'failed'
    aiStatus.testMessage = error.message || '真实模型连接校验失败'
  } finally {
    checkingAiStatus.value = false
  }
}

async function handleAnalyze() {
  if (!originalText.value.trim()) {
    ElMessage.warning('请输入原文内容')
    return
  }
  analyzing.value = true
  try {
    const result = await analyzeText({
      originalText: originalText.value,
      rewriteType: rewriteType.value
    })
    setAnalysis(result)
    ElMessage.success('分析完成')
  } finally {
    analyzing.value = false
  }
}

function setAnalysis(result) {
  analysis.score = result.aiScore ?? result.score ?? null
  analysis.level = result.aiLevel ?? result.level ?? ''
  analysis.suggestions = result.suggestions || []
}

function setWorkflow(result) {
  workflowSteps.value = result.workflowSteps || []
  const check = result.qualityCheck || {}
  qualityCheck.meaningPreserved = Boolean(check.meaningPreserved)
  qualityCheck.academicTone = Boolean(check.academicTone)
  qualityCheck.templateReduced = Boolean(check.templateReduced)
  qualityCheck.fluent = Boolean(check.fluent)
  qualityCheck.issues = check.issues || []
}

function handleDocumentSelected(uploadFile) {
  selectedDocument.value = uploadFile.raw
  documentJob.fileName = uploadFile.name
  documentJob.status = ''
  documentJob.message = '已选择文件，点击开始整篇处理'
}

async function submitDocument() {
  if (!selectedDocument.value) {
    ElMessage.warning('请先选择 .docx 文件')
    return
  }
  if (aiStatus.testStatus !== 'success') {
    ElMessage.error('真实模型尚未连接，已阻止文档任务，不会生成模拟结果')
    return
  }
  documentUploading.value = true
  try {
    const job = await uploadDocument(selectedDocument.value, documentMode.value, targetPlatform.value)
    setDocumentJob(job)
    rememberDocumentJob(job)
    ElMessage.success('文档任务已提交，正在后台处理')
    await startDocumentPolling(job.jobId)
  } finally {
    documentUploading.value = false
  }
}

async function startDocumentPolling(jobId) {
  if (documentPollTimer.value) {
    clearInterval(documentPollTimer.value)
  }
  await syncDocumentJob(jobId)
  if (['SUCCESS', 'FAILED'].includes(documentJob.status)) {
    if (documentJob.status === 'FAILED') {
      ElMessage.error(documentJob.message || '文档处理失败')
    }
    if (documentJob.status === 'SUCCESS') {
      ElMessage.success(documentJob.message || '文档处理完成')
    }
    return
  }
  documentPollTimer.value = setInterval(async () => {
    try {
      const job = await getDocumentJob(jobId)
      setDocumentJob(job)
      upsertDocumentJob(job)
      rememberDocumentJob(job)
      if (['SUCCESS', 'FAILED'].includes(job.status)) {
        clearInterval(documentPollTimer.value)
        documentPollTimer.value = null
        if (job.status === 'FAILED') {
          ElMessage.error(job.message || '文档处理失败')
        }
        if (job.status === 'SUCCESS') {
          ElMessage.success(job.message || '文档处理完成')
        }
      }
    } catch (error) {
      clearInterval(documentPollTimer.value)
      documentPollTimer.value = null
      documentJob.status = 'FAILED'
      documentJob.message = error.message || '查询文档任务失败'
    }
  }, 800)
}

async function syncDocumentJob(jobId) {
  try {
    const job = await getDocumentJob(jobId)
    setDocumentJob(job)
    upsertDocumentJob(job)
    rememberDocumentJob(job)
  } catch (error) {
    documentJob.status = 'FAILED'
    documentJob.message = error.message || '查询文档任务失败'
  }
}

function setDocumentJob(job) {
  const next = job || {}
  const previousParagraphs = Array.isArray(documentJob.paragraphs) ? documentJob.paragraphs : []
  const keepParagraphs =
    documentDetailVisible.value &&
    next.jobId === documentJob.jobId &&
    previousParagraphs.length > 0 &&
    (!Array.isArray(next.paragraphs) || next.paragraphs.length === 0)
  Object.assign(documentJob, next)
  documentJob.paragraphs = keepParagraphs ? previousParagraphs : (Array.isArray(next.paragraphs) ? next.paragraphs : [])
}

async function downloadOptimizedDocument() {
  await downloadJob(documentJob)
}

async function downloadJob(job) {
  const blob = await downloadDocument(job.jobId)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${job.fileName.replace(/\.docx$/i, '')}-AI痕迹优化.docx`
  link.click()
  URL.revokeObjectURL(url)
}

function selectDocumentJob(job) {
  setDocumentJob(job)
  if (['PENDING', 'RUNNING'].includes(job.status)) {
    startDocumentPolling(job.jobId)
  }
}

async function openDocumentJob(job) {
  selectDocumentJob(job)
  documentDetailVisible.value = true
  try {
    const detailJob = await getDocumentJob(job.jobId, true)
    setDocumentJob(detailJob)
  } catch (error) {
    ElMessage.error(error.message || '加载文档详情失败')
  }
}

function upsertDocumentJob(job) {
  const index = documentJobs.value.findIndex((item) => item.jobId === job.jobId)
  if (index >= 0) {
    documentJobs.value.splice(index, 1, job)
  } else {
    documentJobs.value.unshift(job)
  }
}

function rememberDocumentJob(job) {
  upsertDocumentJob(job)
  const stored = documentJobs.value.slice(0, 10).map((item) => item.jobId)
  localStorage.setItem('documentRewriteJobIds', JSON.stringify(stored))
}

async function restoreDocumentJobs() {
  try {
    const serverJobs = await getDocumentJobs()
    documentJobs.value = serverJobs || []
  } catch {
    const jobIds = JSON.parse(localStorage.getItem('documentRewriteJobIds') || '[]')
    const jobs = await Promise.allSettled(jobIds.map((jobId) => getDocumentJob(jobId)))
    documentJobs.value = jobs
      .filter((item) => item.status === 'fulfilled')
      .map((item) => item.value)
  }
  const runningJob = documentJobs.value.find((job) => ['PENDING', 'RUNNING'].includes(job.status))
  if (runningJob) {
    setDocumentJob(runningJob)
    startDocumentPolling(runningJob.jobId)
  } else if (documentJobs.value[0]) {
    setDocumentJob(documentJobs.value[0])
  }
}

function jobProgress(job) {
  const paragraphs = Array.isArray(job.paragraphs) ? job.paragraphs : []
  const paragraphTotal = paragraphs.length
  const paragraphDone = paragraphs.filter((item) => ['SUCCESS', 'FAILED'].includes(item.status)).length
  const total = job.totalParagraphs || paragraphTotal
  const done = Math.max(job.processedParagraphs || 0, paragraphDone)
  if (!total) {
    return job.status === 'SUCCESS' ? 100 : 0
  }
  return Math.min(100, Math.round((done / total) * 100))
}

function jobTagType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

function paragraphTagType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

async function copyResult() {
  if (!rewrittenText.value) {
    return
  }
  await navigator.clipboard.writeText(rewrittenText.value)
  ElMessage.success('已复制')
}

function confirmSaved() {
  if (!currentResult.value) {
    return
  }
  ElMessage.success(`记录 #${currentResult.value.id} 已保存`)
}

async function loadHistory() {
  historyLoading.value = true
  try {
    history.value = await getRewriteList()
  } finally {
    historyLoading.value = false
  }
}

async function viewDetail(id) {
  detail.value = await getRewriteDetail(id)
  detailVisible.value = true
}

async function removeRecord(id) {
  await ElMessageBox.confirm('确认删除这条历史记录吗？', '删除确认', {
    type: 'warning'
  })
  await deleteRewrite(id)
  ElMessage.success('删除成功')
  await loadHistory()
  if (currentResult.value?.id === id) {
    currentResult.value = null
  }
}

function formatTime(value) {
  if (!value) {
    return '--'
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

function levelTagType(level) {
  if (level === '较高') {
    return 'danger'
  }
  if (level === '中等') {
    return 'warning'
  }
  return 'success'
}

onMounted(() => {
  loadAiStatus()
  loadHistory()
  restoreDocumentJobs()
})

onBeforeUnmount(() => {
  if (documentPollTimer.value) {
    clearInterval(documentPollTimer.value)
  }
})
</script>

<style scoped>
.rewrite-page {
  width: min(1440px, 100%);
  min-height: 100vh;
  margin: 0 auto;
  padding: 24px;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.top-tags {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.topbar h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0;
}

.topbar p {
  margin: 8px 0 0;
  color: #64748b;
}

.model-status-alert {
  margin-bottom: 16px;
}

.model-status-detail {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
}

.document-panel {
  margin-bottom: 16px;
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.mode-card {
  display: grid;
  gap: 8px;
  min-height: 92px;
  padding: 14px;
  text-align: left;
  color: #475569;
  background: #f8fafc;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  cursor: pointer;
}

.mode-card strong {
  color: #1f2937;
  font-size: 15px;
}

.mode-card span {
  line-height: 1.5;
}

.mode-card.active {
  color: #1d4ed8;
  background: #eff6ff;
  border-color: #2563eb;
}

.mode-card.active strong {
  color: #1d4ed8;
}

.platform-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  margin-bottom: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.platform-label {
  color: #475569;
  font-weight: 600;
  white-space: nowrap;
}

.document-upload {
  display: grid;
  grid-template-columns: minmax(280px, 420px) minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
}

.upload-text {
  display: grid;
  gap: 8px;
  color: #475569;
}

.upload-text strong {
  color: #1f2937;
}

.document-status {
  display: grid;
  align-content: center;
  gap: 10px;
  color: #475569;
}

.document-job-table {
  margin-top: 16px;
}

.document-detail {
  display: grid;
  gap: 14px;
}

.document-detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.document-detail-meta {
  display: grid;
  gap: 8px;
  color: #64748b;
  padding-bottom: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.paragraph-list {
  display: grid;
  gap: 12px;
}

.paragraph-item {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background: #ffffff;
}

.paragraph-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-weight: 600;
}

.paragraph-original,
.paragraph-rewritten {
  margin: 0;
  line-height: 1.8;
  white-space: pre-wrap;
}

.paragraph-original {
  color: #475569;
}

.paragraph-rewritten {
  padding: 10px;
  color: #1f2937;
  background: #f8fafc;
  border-radius: 8px;
}

.paragraph-message {
  color: #64748b;
  font-size: 13px;
}

.panel {
  border: 1px solid #dbe3ef;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 32px;
  font-weight: 600;
}

.controls {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.protection-hint {
  display: grid;
  gap: 4px;
  margin-top: 14px;
  padding: 10px 12px;
  color: #475569;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
}

.protection-hint strong {
  color: #166534;
}

.type-select {
  width: 190px;
}

.platform-select {
  width: 140px;
}

.result-actions {
  justify-content: flex-end;
}

.analysis-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.score-row {
  display: flex;
  align-items: center;
  gap: 18px;
}

.score {
  display: grid;
  place-items: center;
  width: 84px;
  height: 84px;
  border-radius: 8px;
  color: #ffffff;
  background: #2563eb;
  font-size: 30px;
  font-weight: 700;
}

.score-meta {
  display: grid;
  gap: 8px;
  color: #64748b;
}

.score-meta strong {
  color: #1f2937;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.current-record {
  display: grid;
  gap: 10px;
  color: #475569;
}

.history-panel {
  margin-top: 16px;
}

.workflow-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(320px, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.workflow-grid :deep(.el-timeline) {
  padding-left: 2px;
}

.workflow-grid p {
  margin: 6px 0 0;
  color: #64748b;
  line-height: 1.7;
}

.quality-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.quality-list span {
  padding: 10px 12px;
  border-radius: 8px;
  color: #64748b;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}

.quality-list .passed {
  color: #166534;
  background: #ecfdf5;
  border-color: #bbf7d0;
}

.detail h3 {
  margin: 16px 0 8px;
  font-size: 15px;
}

.detail p {
  margin: 0;
  padding: 12px;
  white-space: pre-wrap;
  line-height: 1.8;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.detail-steps {
  display: grid;
  gap: 8px;
}

@media (max-width: 900px) {
  .rewrite-page {
    padding: 16px;
  }

  .workspace,
  .analysis-grid,
  .workflow-grid,
  .document-upload {
    grid-template-columns: 1fr;
  }

  .mode-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .topbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .type-select,
  .platform-select {
    width: 100%;
  }

  .platform-strip {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
