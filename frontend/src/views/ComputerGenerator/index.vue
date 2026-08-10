<template>
  <main class="computer-page">
    <nav class="studio-nav">
      <button class="studio-brand" type="button" @click="router.push('/')">
        <b>D</b><span>Dokiai Academic<small>DOCUMENT KNOWLEDGE INTELLIGENCE AI</small></span>
      </button>
      <button class="back-link" type="button" @click="router.push('/dashboard')">返回工作台</button>
    </nav>
    <header class="hero">
      <div>
        <span class="eyebrow">AI ENGINEERING STUDIO</span>
        <h1>AI 工程生成器</h1>
        <p>从任务书与开题报告提取工程需求，自动规划技术架构、生成项目文件并交付 ZIP 成果包。</p>
      </div>
      <el-tag size="large">DOKIAI AI · READY</el-tag>
    </header>

    <section class="layout">
      <el-card class="panel upload-panel" shadow="never">
        <template #header><div><span class="section-kicker">01 · INPUT</span><strong>上传项目资料</strong></div></template>
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
            <div><span class="section-kicker">02 · AI ANALYSIS</span><strong>AI 分析工作区</strong></div>
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
      <aside class="engineering-assistant">
        <span class="section-kicker">DOKIAI COPILOT</span>
        <h3>工程 AI 助手</h3>
        <p>从资料中识别项目类型、技术栈与核心模块，并持续检查工程包完整性。</p>
        <div class="assistant-state"><i :class="{ ready: uploadedFiles.length }" /><span>{{ uploadedFiles.length ? `已接收 ${uploadedFiles.length} 份资料` : '等待项目资料' }}</span></div>
        <div class="assistant-metric"><span>项目类型</span><b>{{ plan?.projectType || '待识别' }}</b></div>
        <div class="assistant-metric"><span>技术栈</span><b>{{ plan?.techStack || '由 AI 推荐' }}</b></div>
        <div class="assistant-metric"><span>模块识别</span><b>{{ plan?.modules?.length || 0 }} 个</b></div>
        <div class="assistant-tip">上传资料后，AI 会先生成可调整的工程蓝图，不会直接开始生成。</div>
      </aside>
    </section>

    <el-card class="panel progress-panel" shadow="never">
      <template #header><div><span class="section-kicker">03 · GENERATION</span><strong>工程生成进度</strong></div></template>
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
            <div><span class="section-kicker">04 · DELIVERY</span><strong>成果下载</strong></div>
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
.computer-page{min-height:100vh;padding:30px 24px 70px;color:var(--text);background:radial-gradient(circle at 12% -8%,rgba(255,126,179,.22),transparent 32rem),radial-gradient(circle at 88% 2%,rgba(79,172,254,.2),transparent 30rem),linear-gradient(135deg,#fff,#fff5fa 48%,#f3f8ff)}
.hero{max-width:1500px;margin:0 auto 24px;display:flex;align-items:flex-start;justify-content:space-between;gap:24px;animation:page-in .55s ease both}
.hero h1{margin:10px 0 8px;font-size:clamp(34px,4vw,52px);line-height:1.08}.hero p{margin:0;color:var(--muted);line-height:1.7}
.layout,.result-layout{max-width:1500px;margin:0 auto;display:grid;grid-template-columns:.82fr 1.24fr .62fr;gap:18px}.panel-head{display:flex;align-items:center;justify-content:space-between;gap:16px}
.upload-slots{display:grid;gap:14px}.upload-slot{display:grid;grid-template-columns:1fr auto;gap:10px;align-items:center;padding:16px;border:1px solid rgba(108,99,255,.1);border-radius:8px;background:rgba(255,255,255,.58);backdrop-filter:blur(14px)}
.upload-slot p{margin:5px 0 0;color:var(--muted);font-size:13px}.file-name{grid-column:1/-1;color:var(--primary);font-size:13px}.main-actions{display:flex;gap:12px;margin-top:18px;flex-wrap:wrap}.inline-alert{margin-top:16px}
.result-list{display:grid;gap:10px}.result-row{display:grid;grid-template-columns:110px 1fr 60px;gap:12px;align-items:center;padding:12px;border:1px solid rgba(108,99,255,.1);border-radius:8px;background:rgba(255,255,255,.58)}
.result-row span,.tree-head span{color:var(--muted)}.result-row b{font-weight:600;color:var(--text);line-height:1.6}
.tree-box,.queue-box{padding:12px;border:1px solid rgba(108,99,255,.1);border-radius:8px;background:rgba(255,255,255,.58)}.tree-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px}.tree-head small{color:var(--muted)}
.tree-box pre{max-height:260px;overflow:auto;margin:0;padding:12px;border-radius:8px;background:rgba(31,41,55,.86);color:#f8fafc;font-size:12px;line-height:1.55}
.progress-panel{max-width:1500px;margin:18px auto}.current-file{margin:16px 0 10px;padding:10px 12px;border-radius:8px;background:rgba(79,172,254,.12);color:var(--primary);font-size:13px}.result-layout{grid-template-columns:.9fr 1.1fr}
.file-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.file-card{padding:14px;border:1px solid rgba(108,99,255,.1);border-radius:8px;background:rgba(255,255,255,.58)}.file-card span{display:block;margin-top:6px;color:var(--muted);font-size:12px}
.preview-panel iframe{width:100%;height:560px;border:1px solid rgba(108,99,255,.1);border-radius:8px;background:white}.two-col{display:grid;grid-template-columns:1fr 1fr;gap:14px}.switches{display:flex;gap:18px;flex-wrap:wrap}
@media(max-width:1050px){.layout,.result-layout{grid-template-columns:1fr}.hero{display:block}.preview-panel iframe{height:460px}}
@media(max-width:720px){.upload-slot,.two-col,.file-grid{grid-template-columns:1fr}.computer-page{padding:20px 12px 50px}.hero h1{font-size:30px}.progress-panel :deep(.el-steps){display:none}.result-row{grid-template-columns:1fr}}
.computer-page{padding:22px max(28px,calc((100% - 1500px)/2)) 70px!important;background:linear-gradient(45deg,#fbd7ea 0%,#f8edf5 38%,#eef1f8 65%,#dcebff 100%)!important}.hero{padding:20px 4px 8px}.hero h1{color:#252936;font-size:clamp(40px,5vw,64px)!important}.hero .el-tag{border:0!important;border-radius:99px!important;background:#eee9ff!important;color:#6e4fff!important}.panel{overflow:hidden;border:1px solid rgba(110,79,255,.1)!important;border-radius:22px!important;background:#ffffffdf!important;box-shadow:0 18px 50px rgba(61,53,104,.08)!important}.panel :deep(.el-card__header){padding:20px 22px;border-bottom:1px solid #f0edf5}.section-kicker{display:block;margin-bottom:5px;color:#6e4fff;font-size:9px;font-weight:800;letter-spacing:.15em}.upload-slot{border:1px dashed #cfc6ee!important;border-radius:15px!important;background:#faf8ff!important}.main-actions :deep(.el-button--primary),.main-actions :deep(.el-button--success){border:0;background:linear-gradient(135deg,#6e4fff,#ff55b0)}.result-row,.tree-box,.queue-box{border-radius:12px!important}.progress-panel{padding-block:4px}.file-card{border-radius:15px!important;background:#f8f5ff!important}.preview-panel iframe{border-radius:14px}
.studio-nav{max-width:1500px;height:54px;margin:auto;display:flex;align-items:center;justify-content:space-between}.studio-brand,.back-link{border:0;background:transparent;cursor:pointer}.studio-brand{display:flex;align-items:center;gap:10px;color:#29263a;text-align:left;font-weight:750}.studio-brand b{display:grid;place-items:center;width:36px;height:36px;border-radius:12px;color:#fff;background:linear-gradient(145deg,#4198ff,#7658ef 58%,#ff55b0);box-shadow:0 8px 20px rgba(110,79,255,.23)}.studio-brand span{display:grid}.studio-brand small{color:#9993aa;font-size:7px;letter-spacing:.1em}.back-link{color:#6e4fff;font-weight:650}.engineering-assistant{align-self:start;padding:22px;border:1px solid rgba(110,79,255,.1);border-radius:22px;background:rgba(255,255,255,.82);box-shadow:0 18px 50px rgba(61,53,104,.08);backdrop-filter:blur(18px)}.engineering-assistant h3{margin:8px 0;font-size:20px}.engineering-assistant>p{color:#777184;font-size:13px;line-height:1.65}.assistant-state{display:flex;align-items:center;gap:9px;margin:18px 0;padding:11px;border-radius:12px;background:#f5f1ff;color:#6e4fff;font-size:12px}.assistant-state i{width:8px;height:8px;border-radius:50%;background:#b9b4c5}.assistant-state i.ready{background:#42ad7d;box-shadow:0 0 0 5px rgba(66,173,125,.12)}.assistant-metric{display:grid;gap:5px;padding:12px 0;border-bottom:1px solid #eeeaf5}.assistant-metric span{color:#9993a6;font-size:11px}.assistant-metric b{font-size:13px;line-height:1.45}.assistant-tip{margin-top:16px;padding:13px;border-radius:13px;background:linear-gradient(135deg,#f2efff,#fff0f8);color:#696174;font-size:12px;line-height:1.6}
.upload-slot :deep(.el-button--primary),.panel-head :deep(.el-button--success){border:0!important;background:linear-gradient(135deg,#6e4fff,#ff55b0)!important;color:#fff!important}.panel-head :deep(.el-button:not(:disabled)){border-color:#bbaef0;color:#6e4fff}
@media(max-width:1250px){.layout{grid-template-columns:.8fr 1.2fr}.engineering-assistant{grid-column:1/-1}.assistant-metric{display:inline-grid;width:31%;margin-right:2%}}
</style>
