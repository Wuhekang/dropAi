<template>
  <main class="computer-page">
    <header class="hero">
      <div>
        <el-button text type="primary" @click="router.push('/dashboard')">返回工作台</el-button>
        <span class="eyebrow">COMPUTER PROJECT PACKAGE</span>
        <h1>计算机程序包生成</h1>
        <p>上传任务书和开题报告后，系统自动识别项目方案、规划目录，再按文件队列生成完整成果包。</p>
      </div>
      <el-tag size="large" type="success">万量矩阵智能识别</el-tag>
    </header>

    <section class="layout">
      <el-card class="panel upload-panel" shadow="never">
        <template #header><strong>文件上传区</strong></template>
        <div class="upload-slots">
          <div v-for="slot in uploadSlots" :key="slot.key" class="upload-slot">
            <div>
              <strong>{{ slot.label }}</strong>
              <p>{{ slot.hint }}</p>
            </div>
            <el-upload
              action=""
              :auto-upload="false"
              :limit="1"
              :show-file-list="false"
              accept=".docx,.pdf,.txt,.md"
              :on-change="file => setFile(slot.key, file)"
            >
              <el-button type="primary" plain>选择文件</el-button>
            </el-upload>
            <span class="file-name">{{ files[slot.key]?.name || '未上传' }}</span>
          </div>
        </div>
        <div class="main-actions">
          <el-button type="primary" size="large" :loading="analyzing" :disabled="!uploadedFiles.length" @click="analyze">
            智能识别
          </el-button>
          <el-button type="success" size="large" :loading="generating" :disabled="!plan || job?.status === 'SUCCESS'" @click="generate">
            确认目录并生成
          </el-button>
        </div>
        <el-alert
          class="inline-alert"
          type="info"
          :closable="false"
          :title="plan ? `预计消耗 ${plan.pointsCost} 积分，确认生成前会再次校验。` : '请先上传任务书或开题报告，点击智能识别后再确认生成。'"
        />
      </el-card>

      <el-card class="panel identify-panel" shadow="never">
        <template #header>
          <div class="panel-head">
            <strong>智能识别结果</strong>
            <el-button :disabled="!plan" @click="openTune">微调配置</el-button>
          </div>
        </template>
        <el-empty v-if="!plan" description="智能识别后展示项目方案初稿" />
        <div v-else class="result-list">
          <div v-for="item in resultItems" :key="item.key" class="result-row">
            <span>{{ item.label }}</span>
            <b>{{ item.value }}</b>
            <el-button text type="primary" @click="openTune">微调</el-button>
          </div>
          <div class="tree-box">
            <div class="tree-head">
              <span>项目目录树</span>
              <el-button text type="primary" @click="openTune">微调</el-button>
            </div>
            <pre>{{ plan.directoryTree }}</pre>
          </div>
          <div class="queue-box">
            <div class="tree-head">
              <span>文件生成队列</span>
              <small>{{ plan.fileQueue?.length || 0 }} 个文件</small>
            </div>
            <el-table :data="plan.fileQueue || []" size="small" max-height="280">
              <el-table-column prop="priority" label="#" width="56" />
              <el-table-column prop="path" label="文件路径" min-width="260" show-overflow-tooltip />
              <el-table-column prop="type" label="类型" width="90" />
              <el-table-column prop="description" label="职责" min-width="180" show-overflow-tooltip />
            </el-table>
          </div>
        </div>
      </el-card>
    </section>

    <el-card class="panel progress-panel" shadow="never">
      <template #header><strong>生成进度区</strong></template>
      <el-steps :active="activeStep" finish-status="success" align-center>
        <el-step v-for="(stage, index) in stages" :key="stage" :title="stage" :status="stepStatus(index)" />
      </el-steps>
      <div v-if="job?.currentFile" class="current-file">正在生成：{{ job.currentFile }}</div>
      <el-progress
        :percentage="job?.progress || 0"
        :status="job?.status === 'FAILED' ? 'exception' : job?.status === 'SUCCESS' ? 'success' : undefined"
      />
      <el-alert v-if="job?.errorMessage" class="inline-alert" type="error" :closable="false" :title="job.errorMessage" />
    </el-card>

    <section class="result-layout">
      <el-card class="panel" shadow="never">
        <template #header>
          <div class="panel-head">
            <strong>成果预览区</strong>
            <el-button type="success" :disabled="job?.status !== 'SUCCESS'" @click="downloadZip">下载成果包</el-button>
          </div>
        </template>
        <el-empty v-if="!zipFiles.length" description="生成完成后只展示毕业设计成果包.zip" />
        <div v-else class="file-grid">
          <div v-for="file in zipFiles" :key="file.fileName" class="file-card">
            <b>毕业设计成果包.zip</b>
            <span>{{ formatSize(file.fileSize) }}</span>
            <el-button text type="primary" @click="downloadFile(file)">下载</el-button>
          </div>
        </div>
      </el-card>

      <el-card class="panel preview-panel" shadow="never">
        <template #header>
          <div class="panel-head">
            <strong>网页预览</strong>
            <el-segmented v-model="previewPage" :options="previewPages" />
          </div>
        </template>
        <iframe v-if="previewSrc" :src="previewSrc" sandbox="allow-same-origin allow-forms" title="生成项目预览" />
        <el-empty v-else description="生成完成后自动展示登录页、仪表盘、业务页、统计页和用户管理页" />
      </el-card>
    </section>

    <el-dialog v-model="tuneVisible" title="微调配置" width="760px">
      <el-form v-if="draft" label-position="top" class="tune-form">
        <div class="two-col">
          <el-form-item label="项目题目"><el-input v-model="draft.title" /></el-form-item>
          <el-form-item label="技术栈">
            <el-select v-model="draft.techStack">
              <el-option v-for="item in techStacks" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="用户角色"><el-input v-model="rolesText" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="功能模块"><el-input v-model="modulesText" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="数据库表（每行：表名|说明|字段1,字段2）"><el-input v-model="tablesText" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="页面列表"><el-input v-model="pagesText" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="后端接口"><el-input v-model="apisText" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="论文大纲"><el-input v-model="outlineText" type="textarea" :rows="3" /></el-form-item>
        <div class="switches">
          <el-checkbox v-model="draft.generatePaper">生成论文</el-checkbox>
          <el-checkbox v-model="draft.generateTests">生成测试用例</el-checkbox>
          <el-checkbox v-model="draft.enablePreview">生成网页预览</el-checkbox>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="tuneVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTune">保存微调</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  analyzeComputerGenerationFiles,
  downloadArtifact,
  downloadComputerGenerationZip,
  getComputerGenerationResult,
  getComputerGenerationStatus,
  startComputerGeneration
} from '../../api/rewrite'

const router = useRouter()
const uploadSlots = [
  { key: 'taskBook', label: '上传任务书', hint: '用于识别项目题目、角色、模块和业务规则。' },
  { key: 'proposal', label: '上传开题报告', hint: '用于补充研究背景、技术路线和论文结构。' }
]
const techStacks = ['Spring Boot 3.x + Vue3 + Element Plus + MySQL 8', 'Spring Boot + Thymeleaf + MySQL', 'Flask + Vue + MySQL', 'Django + MySQL', 'FastAPI + Vue + MySQL']
const stages = ['项目识别', '目录生成', 'SQL生成', '后端生成', '前端生成', '论文生成', '预览构建', 'ZIP打包', '生成完成']
const previewPages = [
  { label: '登录页', value: 'index.html' },
  { label: '仪表盘', value: 'dashboard.html' },
  { label: '业务页', value: 'business.html' },
  { label: '统计页', value: 'statistics.html' },
  { label: '用户管理', value: 'user.html' }
]
const files = reactive({})
const job = ref(null)
const plan = ref(null)
const analyzing = ref(false)
const generating = ref(false)
const previewPage = ref('index.html')
const pollTimer = ref(null)
const tuneVisible = ref(false)
const draft = ref(null)
const rolesText = ref('')
const modulesText = ref('')
const tablesText = ref('')
const pagesText = ref('')
const apisText = ref('')
const outlineText = ref('')

const uploadedFiles = computed(() => Object.values(files).filter(Boolean))
const activeStep = computed(() => {
  const index = stages.findIndex(stage => stage === job.value?.currentStage)
  return index < 0 ? 0 : index
})
const zipFiles = computed(() => (job.value?.files || []).filter(file => /\.zip$/i.test(file.fileName)))
const previewSrc = computed(() => {
  const base = job.value?.activePreviewUrl || job.value?.previewUrl
  return base ? base.replace(/\/[^/]+$/, `/${previewPage.value}`) : ''
})
const resultItems = computed(() => plan.value ? [
  { key: 'title', label: '项目题目', value: plan.value.title },
  { key: 'projectType', label: '项目类型', value: plan.value.projectType },
  { key: 'techStack', label: '推荐技术栈', value: plan.value.techStack },
  { key: 'programmingLanguage', label: '编程语言', value: plan.value.programmingLanguage },
  { key: 'backendStack', label: '后端技术栈', value: plan.value.backendStack },
  { key: 'frontendStack', label: '前端技术栈', value: plan.value.frontendStack },
  { key: 'databaseType', label: '数据库类型', value: plan.value.databaseType },
  { key: 'needMiniprogram', label: '小程序', value: plan.value.needMiniprogram ? '需要' : '不需要' },
  { key: 'needDesktop', label: '桌面端', value: plan.value.needDesktop ? '需要' : '不需要' },
  { key: 'needDataAnalysis', label: '数据分析', value: plan.value.needDataAnalysis ? '需要' : '不需要' },
  { key: 'roles', label: '用户角色', value: (plan.value.roles || []).join('、') },
  { key: 'modules', label: '功能模块', value: (plan.value.modules || []).join('、') },
  { key: 'tables', label: '数据库表', value: (plan.value.tables || []).map(t => t.name).join('、') },
  { key: 'pages', label: '前端页面', value: (plan.value.pages || []).join('、') },
  { key: 'apis', label: '后端接口', value: `${(plan.value.apis || []).length} 个接口` },
  { key: 'paperOutline', label: '论文章节', value: (plan.value.paperOutline || []).join('、') },
  { key: 'pointsCost', label: '预计积分消耗', value: `${plan.value.pointsCost} 积分` }
] : [])

function setFile(key, file) {
  files[key] = file.raw ? { raw: file.raw, name: file.name } : file
}
async function analyze() {
  analyzing.value = true
  try {
    const result = await analyzeComputerGenerationFiles(uploadedFiles.value)
    job.value = result.job
    plan.value = result.plan
    ElMessage.success('智能识别完成，请确认目录后生成。')
  } catch (error) {
    ElMessage.error(error.message || '智能识别失败')
  } finally {
    analyzing.value = false
  }
}
async function generate() {
  if (!plan.value || !job.value?.id) return
  try {
    await ElMessageBox.confirm(`预计消耗 ${plan.value.pointsCost} 积分，确认按目录逐文件生成完整成果包？`, '确认生成', { type: 'warning' })
  } catch {
    return
  }
  generating.value = true
  try {
    job.value = await startComputerGeneration(job.value.id, plan.value)
    startPolling(job.value.id)
    ElMessage.success('生成任务已创建，系统正在逐文件生成。')
  } catch (error) {
    ElMessage.error(error.message || '生成失败')
    await loadResult()
    generating.value = false
  }
}
async function loadResult() {
  if (job.value?.id) job.value = await getComputerGenerationResult(job.value.id)
}
function openTune() {
  if (!plan.value) return
  draft.value = JSON.parse(JSON.stringify(plan.value))
  rolesText.value = (draft.value.roles || []).join('、')
  modulesText.value = (draft.value.modules || []).join('、')
  pagesText.value = (draft.value.pages || []).join('、')
  apisText.value = (draft.value.apis || []).join('\n')
  outlineText.value = (draft.value.paperOutline || []).join('、')
  tablesText.value = (draft.value.tables || []).map(t => `${t.name}|${t.comment || ''}|${(t.fields || []).join(',')}`).join('\n')
  tuneVisible.value = true
}
function saveTune() {
  draft.value.roles = splitList(rolesText.value)
  draft.value.modules = splitList(modulesText.value)
  draft.value.pages = splitList(pagesText.value)
  draft.value.apis = apisText.value.split('\n').map(x => x.trim()).filter(Boolean)
  draft.value.paperOutline = splitList(outlineText.value)
  draft.value.tables = tablesText.value.split('\n').map(line => {
    const [name, comment, fields] = line.split('|')
    return { name: (name || '').trim(), comment: (comment || '').trim(), fields: (fields || '').split(',').map(x => x.trim()).filter(Boolean) }
  }).filter(t => t.name)
  plan.value = draft.value
  tuneVisible.value = false
  ElMessage.success('微调配置已保存')
}
function splitList(value) {
  return String(value || '').split(/[、，,\n]/).map(x => x.trim()).filter(Boolean)
}
function startPolling(jobId) {
  stopPolling()
  pollTimer.value = window.setInterval(async () => {
    try {
      job.value = await getComputerGenerationStatus(jobId)
      if (['SUCCESS', 'FAILED'].includes(job.value?.status)) {
        stopPolling()
        generating.value = false
        if (job.value.status === 'SUCCESS') await loadResult()
      }
    } catch (_) {
      stopPolling()
      generating.value = false
    }
  }, 900)
}
function stopPolling() {
  if (pollTimer.value) window.clearInterval(pollTimer.value)
  pollTimer.value = null
}
async function downloadZip() {
  const blob = await downloadComputerGenerationZip(job.value.id)
  saveBlob(blob, '毕业设计成果包.zip')
}
async function downloadFile(file) {
  saveBlob(await downloadArtifact(file.downloadUrl), '毕业设计成果包.zip')
}
function saveBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}
function stepStatus(index) {
  if (job.value?.status === 'FAILED' && index === activeStep.value) return 'error'
  if (index < activeStep.value || job.value?.status === 'SUCCESS') return 'success'
  if (index === activeStep.value && job.value?.status === 'RUNNING') return 'process'
  return 'wait'
}
function formatSize(size) {
  return size > 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(2)} MB` : `${Math.max(1, Math.round((size || 0) / 1024))} KB`
}
onBeforeUnmount(stopPolling)
</script>

<style scoped>
.computer-page{min-height:100vh;padding:30px 24px 70px;background:#f6f8fb}.hero{max-width:1500px;margin:0 auto 24px;display:flex;align-items:flex-start;justify-content:space-between;gap:24px}.eyebrow{display:block;margin-top:16px;color:#2563eb;font-size:12px;font-weight:800;letter-spacing:.12em}.hero h1{margin:10px 0 8px;font-size:38px}.hero p{margin:0;color:#64748b;line-height:1.7}.layout,.result-layout{max-width:1500px;margin:0 auto;display:grid;grid-template-columns:.8fr 1.2fr;gap:18px}.panel{border-radius:8px;border-color:#dbe5f4}.panel-head{display:flex;align-items:center;justify-content:space-between;gap:16px}.upload-slots{display:grid;gap:14px}.upload-slot{display:grid;grid-template-columns:1fr auto;gap:10px;align-items:center;padding:16px;border:1px solid #dbe5f4;border-radius:8px;background:#fff}.upload-slot p{margin:5px 0 0;color:#64748b;font-size:13px}.file-name{grid-column:1/-1;color:#2563eb;font-size:13px}.main-actions{display:flex;gap:12px;margin-top:18px}.inline-alert{margin-top:16px}.result-list{display:grid;gap:10px}.result-row{display:grid;grid-template-columns:110px 1fr 60px;gap:12px;align-items:center;padding:12px;border:1px solid #e2e8f0;border-radius:8px;background:#fff}.result-row span,.tree-head span{color:#64748b}.result-row b{font-weight:600;color:#172033;line-height:1.6}.tree-box,.queue-box{padding:12px;border:1px solid #e2e8f0;border-radius:8px;background:#fff}.tree-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px}.tree-head small{color:#64748b}.tree-box pre{max-height:260px;overflow:auto;margin:0;padding:12px;border-radius:8px;background:#0f172a;color:#e2e8f0;font-size:12px;line-height:1.55}.progress-panel{max-width:1500px;margin:18px auto}.current-file{margin:16px 0 10px;padding:10px 12px;border-radius:8px;background:#eff6ff;color:#1d4ed8;font-size:13px}.result-layout{grid-template-columns:.9fr 1.1fr}.file-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.file-card{padding:14px;border:1px solid #dbe5f4;border-radius:8px;background:#fff}.file-card span{display:block;margin-top:6px;color:#64748b;font-size:12px}.preview-panel iframe{width:100%;height:560px;border:1px solid #dbe5f4;border-radius:8px;background:white}.two-col{display:grid;grid-template-columns:1fr 1fr;gap:14px}.switches{display:flex;gap:18px;flex-wrap:wrap}@media(max-width:1050px){.layout,.result-layout{grid-template-columns:1fr}.hero{display:block}.preview-panel iframe{height:460px}}@media(max-width:720px){.upload-slot,.two-col,.file-grid{grid-template-columns:1fr}.computer-page{padding:20px 12px 50px}.hero h1{font-size:30px}.progress-panel :deep(.el-steps){display:none}.result-row{grid-template-columns:1fr}}
</style>
