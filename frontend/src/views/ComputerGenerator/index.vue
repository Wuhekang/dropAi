<template>
  <main class="computer-page">
    <header class="topbar">
      <div>
        <el-button text type="primary" @click="router.push('/dashboard')">返回工作台</el-button>
        <span class="eyebrow">COMPUTER PROJECT GENERATOR</span>
        <h1>计算机程序包生成</h1>
        <p>根据任务书、开题报告或简短题目，生成前端、后端、SQL、论文、ZIP 成果包和静态网页预览。</p>
      </div>
      <el-tag size="large" type="success">MVP 规则补全生成</el-tag>
    </header>

    <section class="layout">
      <el-card class="panel" shadow="never">
        <template #header><strong>输入区</strong></template>
        <el-form label-position="top">
          <el-form-item label="项目题目">
            <el-input v-model="form.title" placeholder="例如：学生宿舍管理系统" />
          </el-form-item>
          <div class="two-col">
            <el-form-item label="项目类型">
              <el-select v-model="form.projectType">
                <el-option v-for="item in projectTypes" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="技术栈">
              <el-select v-model="form.techStack">
                <el-option v-for="item in techStacks" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="上传任务书 / 开题报告">
            <el-upload
              drag
              action=""
              multiple
              :auto-upload="false"
              :accept="'.docx,.txt,.pdf,.md'"
              :file-list="files"
              :on-change="onFileChange"
              :on-remove="onFileRemove"
            >
              <div class="upload-copy">拖入 docx / txt / pdf 文件，或点击选择</div>
            </el-upload>
          </el-form-item>
          <el-form-item label="补充需求">
            <el-input v-model="form.inputText" type="textarea" :rows="5" placeholder="填写角色、模块、页面、数据统计、论文侧重点等要求" />
          </el-form-item>
        </el-form>
      </el-card>

      <el-card class="panel" shadow="never">
        <template #header>
          <div class="panel-head">
            <strong>生成配置</strong>
            <el-tag>预计 {{ estimatedCost }} 积分</el-tag>
          </div>
        </template>
        <div class="switch-grid">
          <el-checkbox v-model="form.generatePaper">生成论文</el-checkbox>
          <el-checkbox v-model="form.generateSql">生成 SQL 文件</el-checkbox>
          <el-checkbox v-model="form.generateFrontend">生成前端页面</el-checkbox>
          <el-checkbox v-model="form.generateBackend">生成后端接口</el-checkbox>
          <el-checkbox v-model="form.generateAdmin">生成管理员端</el-checkbox>
          <el-checkbox v-model="form.generateUser">生成普通用户端</el-checkbox>
          <el-checkbox v-model="form.generateTests">生成测试用例</el-checkbox>
          <el-checkbox v-model="form.generateReadme">生成运行说明</el-checkbox>
          <el-checkbox v-model="form.generateZip">生成 ZIP 成果包</el-checkbox>
          <el-checkbox v-model="form.enablePreview">开启网页预览</el-checkbox>
        </div>
        <el-alert class="inline-alert" type="info" :closable="false" :title="job ? `任务 ${job.id}：${statusText(job.status)}，${job.currentStage}` : '创建任务后会先校验积分，再开始生成。'" />
        <div class="actions">
          <el-button type="primary" size="large" :loading="generating" :disabled="!form.title.trim()" @click="generate">
            创建并开始生成
          </el-button>
          <el-button :disabled="!job" @click="loadResult">刷新结果</el-button>
        </div>
      </el-card>
    </section>

    <el-card class="panel progress-panel" shadow="never">
      <template #header><strong>进度展示</strong></template>
      <el-steps :active="activeStep" finish-status="success" align-center>
        <el-step v-for="(stage, index) in stages" :key="stage" :title="stage" :status="stepStatus(index)" />
      </el-steps>
      <el-progress :percentage="job?.progress || 0" :status="job?.status === 'FAILED' ? 'exception' : job?.status === 'SUCCESS' ? 'success' : undefined" />
      <el-alert v-if="job?.errorMessage" class="inline-alert" type="error" :closable="false" :title="job.errorMessage" />
    </el-card>

    <section class="result-layout">
      <el-card class="panel" shadow="never">
        <template #header>
          <div class="panel-head">
            <strong>成果展示</strong>
            <el-button type="success" :disabled="job?.status !== 'SUCCESS'" @click="downloadZip">下载完整 ZIP</el-button>
          </div>
        </template>
        <el-empty v-if="!job?.files?.length" description="生成完成后展示成果文件" />
        <div v-else class="file-grid">
          <div v-for="file in importantFiles" :key="file.fileName" class="file-card">
            <b>{{ file.fileName }}</b>
            <span>{{ file.fileType }} · {{ formatSize(file.fileSize) }}</span>
            <el-button text type="primary" @click="downloadFile(file)">下载</el-button>
          </div>
        </div>
        <el-tabs v-if="job?.status === 'SUCCESS'" class="result-tabs">
          <el-tab-pane label="项目结构树">
            <pre>{{ structureTree }}</pre>
          </el-tab-pane>
          <el-tab-pane label="数据库表预览">
            <el-table :data="tablePreview" size="small">
              <el-table-column prop="name" label="表名" />
              <el-table-column prop="comment" label="说明" />
              <el-table-column prop="fields" label="字段" min-width="220" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="接口列表">
            <p v-for="api in apiPreview" :key="api" class="line">{{ api }}</p>
          </el-tab-pane>
          <el-tab-pane label="论文目录">
            <p v-for="item in paperOutline" :key="item" class="line">{{ item }}</p>
          </el-tab-pane>
        </el-tabs>
      </el-card>

      <el-card class="panel preview-panel" shadow="never">
        <template #header>
          <div class="panel-head">
            <strong>网页预览</strong>
            <el-segmented v-model="previewPage" :options="previewPages" @change="switchPreview" />
          </div>
        </template>
        <iframe v-if="previewSrc" :src="previewSrc" sandbox="allow-same-origin allow-forms" title="生成项目预览" />
        <el-empty v-else description="生成完成后可预览登录页、仪表盘和业务页面" />
      </el-card>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createComputerGenerationJob,
  downloadArtifact,
  downloadComputerGenerationZip,
  getComputerGenerationResult,
  getComputerGenerationStatus,
  startComputerGeneration,
  uploadComputerGenerationFiles
} from '../../api/rewrite'

const router = useRouter()
const projectTypes = ['Java Web 项目', 'Python Web 项目', 'Java + Python 混合项目', '微信小程序后台项目', '数据分析项目']
const techStacks = ['Spring Boot + Vue + MySQL', 'Spring Boot + Thymeleaf + MySQL', 'Flask + Vue + MySQL', 'Django + MySQL', 'FastAPI + Vue + MySQL']
const stages = ['正在解析任务书', '正在识别项目类型', '正在生成数据库设计', '正在生成后端接口', '正在生成前端页面', '正在生成论文', '正在打包成果', '正在启动网页预览', '生成完成']
const previewPages = [
  { label: '登录', value: 'index.html' },
  { label: '仪表盘', value: 'dashboard.html' },
  { label: '业务', value: 'business.html' },
  { label: '统计', value: 'statistics.html' },
  { label: '用户', value: 'user.html' }
]
const form = reactive({
  title: '',
  projectType: projectTypes[0],
  techStack: techStacks[0],
  inputText: '',
  generatePaper: true,
  generateSql: true,
  generateFrontend: true,
  generateBackend: true,
  generateAdmin: true,
  generateUser: true,
  generateTests: true,
  generateReadme: true,
  generateZip: true,
  enablePreview: true
})
const files = ref([])
const job = ref(null)
const generating = ref(false)
const previewPage = ref('index.html')
const pollTimer = ref(null)

const estimatedCost = computed(() => {
  let cost = form.projectType.includes('混合') ? 120 : 80
  if (form.generatePaper) cost += 30
  if (form.enablePreview) cost += 20
  return cost
})
const activeStep = computed(() => Math.max(0, stages.findIndex(stage => stage === job.value?.currentStage)))
const importantFiles = computed(() => (job.value?.files || []).filter(file =>
  /(^frontend\/|^backend-|^backend\/|^sql\/schema\.sql|^paper\/|\.zip$|README\.md$)/.test(file.fileName)
))
const previewSrc = computed(() => {
  const base = job.value?.activePreviewUrl || job.value?.previewUrl
  if (!base) return ''
  return base.replace(/\/[^/]+$/, `/${previewPage.value}`)
})
const structureTree = computed(() => ['frontend/', 'backend-java 或 backend-python/', 'sql/schema.sql', 'paper/thesis.md', 'preview/index.html', 'README.md'].join('\n'))
const tablePreview = computed(() => {
  const title = form.title || job.value?.title || '业务系统'
  const domain = title.includes('宿舍') ? '宿舍' : title.includes('图书') ? '图书' : title.includes('商城') ? '商城' : '业务'
  return [
    { name: 'sys_user', comment: '系统用户', fields: 'id, username, password, role, phone, status' },
    { name: `${domain}_record`, comment: `${domain}核心记录`, fields: 'id, name, code, owner_id, status, remark' },
    { name: `${domain}_audit`, comment: `${domain}流程记录`, fields: 'id, record_id, action, operator_id, result' },
    { name: `${domain}_notice`, comment: `${domain}消息通知`, fields: 'id, title, content, receiver_id, read_flag' }
  ]
})
const apiPreview = computed(() => ['登录接口 /api/auth/login', '核心业务分页 /api/{module}/page', '新增业务记录 POST /api/{module}', '数据统计 GET /api/statistics', '文件上传 POST /api/files'])
const paperOutline = ['摘要', 'Abstract', '绪论', '相关技术介绍', '系统需求分析', '系统总体设计', '数据库设计', '系统详细设计', '系统实现', '系统测试', '结论', '参考文献', '致谢']

function onFileChange(file, list) { files.value = list }
function onFileRemove(file, list) { files.value = list }
async function generate() {
  try {
    await ElMessageBox.confirm(`预计消耗 ${estimatedCost.value} 积分，确认开始生成？`, '确认生成', { type: 'warning' })
  } catch { return }
  generating.value = true
  try {
    job.value = await createComputerGenerationJob({ ...form })
    if (files.value.length) job.value = await uploadComputerGenerationFiles(job.value.id, files.value)
    startPolling(job.value.id)
    job.value = await startComputerGeneration(job.value.id)
    await loadResult()
    ElMessage.success('计算机程序包生成完成')
  } catch (error) {
    ElMessage.error(error.message || '生成失败')
    if (job.value?.id) await loadResult()
  } finally {
    generating.value = false
    stopPolling()
  }
}
async function loadResult() {
  if (!job.value?.id) return
  job.value = await getComputerGenerationResult(job.value.id)
}
function startPolling(jobId) {
  stopPolling()
  pollTimer.value = window.setInterval(async () => {
    try { job.value = await getComputerGenerationStatus(jobId) } catch (_) { stopPolling() }
  }, 900)
}
function stopPolling() {
  if (pollTimer.value) window.clearInterval(pollTimer.value)
  pollTimer.value = null
}
async function downloadZip() {
  if (!job.value?.id) return
  const blob = await downloadComputerGenerationZip(job.value.id)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${job.value.title || 'computer-project'}.zip`
  link.click()
  URL.revokeObjectURL(url)
}
async function downloadFile(file) {
  if (!file?.downloadUrl) return
  const blob = await downloadArtifact(file.downloadUrl)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = file.fileName.split('/').pop()
  link.click()
  URL.revokeObjectURL(url)
}
function switchPreview(value) { previewPage.value = value }
function stepStatus(index) {
  if (job.value?.status === 'FAILED' && index === activeStep.value) return 'error'
  if (index < activeStep.value || job.value?.status === 'SUCCESS') return 'success'
  if (index === activeStep.value && job.value?.status === 'RUNNING') return 'process'
  return 'wait'
}
function statusText(status) {
  return ({ PENDING: '等待中', RUNNING: '进行中', SUCCESS: '已完成', FAILED: '失败' })[status] || status || '未创建'
}
function formatSize(size) { return size > 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(2)} MB` : `${Math.max(1, Math.round((size || 0) / 1024))} KB` }
onBeforeUnmount(stopPolling)
</script>

<style scoped>
.computer-page{min-height:100vh;padding:30px 24px 70px;background:linear-gradient(135deg,#f8fafc,#eef6ff 48%,#f5f3ff)}
.topbar{max-width:1500px;margin:0 auto 24px;display:flex;align-items:flex-start;justify-content:space-between;gap:24px}.eyebrow{display:block;margin-top:16px;color:#2563eb;font-size:12px;font-weight:800;letter-spacing:.16em}.topbar h1{margin:10px 0 8px;font-size:38px}.topbar p{margin:0;color:#64748b;line-height:1.7}
.layout,.result-layout{max-width:1500px;margin:0 auto;display:grid;grid-template-columns:1fr 1fr;gap:18px}.panel{border-radius:8px;border-color:#dbe5f4}.panel-head{display:flex;align-items:center;justify-content:space-between;gap:16px}.two-col{display:grid;grid-template-columns:1fr 1fr;gap:14px}.upload-copy{color:#64748b}.switch-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.inline-alert{margin-top:16px}.actions{display:flex;gap:12px;margin-top:18px}.progress-panel{max-width:1500px;margin:18px auto}.result-layout{grid-template-columns:.9fr 1.1fr}.file-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.file-card{padding:14px;border:1px solid #dbe5f4;border-radius:8px;background:#f8fafc}.file-card span{display:block;margin-top:6px;color:#64748b;font-size:12px}.result-tabs{margin-top:18px}pre{white-space:pre-wrap;margin:0;padding:16px;border-radius:8px;background:#0f172a;color:#e2e8f0}.line{margin:0 0 10px;color:#334155}.preview-panel iframe{width:100%;height:560px;border:1px solid #dbe5f4;border-radius:8px;background:white}
@media(max-width:1050px){.layout,.result-layout{grid-template-columns:1fr}.topbar{display:block}.preview-panel iframe{height:460px}}
@media(max-width:720px){.two-col,.switch-grid,.file-grid{grid-template-columns:1fr}.computer-page{padding:20px 12px 50px}.topbar h1{font-size:30px}.progress-panel :deep(.el-steps){display:none}}
</style>
