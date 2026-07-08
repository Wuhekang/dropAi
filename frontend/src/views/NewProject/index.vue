<template>
  <main class="page-shell builder-page">
    <nav class="top-nav">
      <button class="brand" type="button" @click="router.push('/')">
        <span class="brand-mark">D</span>
        <span>DropAI</span>
      </button>
      <div class="nav-links">
        <button type="button" @click="router.push('/dashboard')">控制台</button>
        <button type="button" @click="router.push('/computer-generator')">工程生成</button>
        <button type="button" @click="router.push('/result')">结果页</button>
      </div>
    </nav>

    <header class="builder-head">
      <div>
        <span class="eyebrow">项目生成</span>
        <h1>生成完整工程成果包</h1>
      </div>
      <span class="status-pill"><span class="status-dot"></span>{{ stageLabel }}</span>
    </header>

    <section class="builder-grid">
      <aside class="input-panel panel">
        <div class="panel-title">
          <h2>任务书输入</h2>
          <p>上传任务书或粘贴要求，右侧会按步骤渐进展示生成状态。</p>
        </div>

        <div class="drop-zone">
          <strong>任务书文件</strong>
          <span>{{ files.taskBook?.name || '支持 DOCX、PDF、TXT、Markdown' }}</span>
          <el-upload action="" :auto-upload="false" :show-file-list="false" accept=".docx,.pdf,.txt,.md" :on-change="file => setFile('taskBook', file)">
            <button class="ghost-button" type="button">选择文件</button>
          </el-upload>
        </div>

        <textarea v-model="taskText" class="text-input" placeholder="粘贴项目要求或任务书正文..."></textarea>

        <div class="depth-control">
          <button :class="{ active: project.designDepth === 'graduation' }" type="button" @click="project.designDepth = 'graduation'">毕业设计</button>
          <button :class="{ active: project.designDepth === 'engineering' }" type="button" @click="project.designDepth = 'engineering'">工程设计</button>
        </div>

        <button class="primary-button action" type="button" :disabled="!canAnalyze || analyzing" @click="analyze">
          {{ analyzing ? '正在解析任务...' : '解析任务书' }}
        </button>
        <button class="ghost-button action" type="button" :disabled="!targetConfirmed || generating" @click="generate">
          {{ generating ? '正在生成...' : '生成成果包' }}
        </button>
      </aside>

      <section class="output-panel">
        <div class="visual-stage panel">
          <div class="stage-overlay">
            <span class="status-pill"><span class="status-dot"></span>3D 预览</span>
            <strong>{{ project.projectTitle || '工程系统预览' }}</strong>
          </div>
          <ModelViewer3D :project="modelProject" />
        </div>

        <div class="progress-card panel">
          <div class="progress-head">
            <div>
              <h2>{{ progressTitle }}</h2>
              <p>{{ packageMessage }}</p>
            </div>
            <strong>{{ progress }}%</strong>
          </div>
          <div class="loading-line"><span :style="{ width: `${progress}%` }"></span></div>
          <div class="steps">
            <div v-for="step in processSteps" :key="step.label" :class="{ done: step.index <= currentStep }">
              <i></i>
              <span>{{ step.label }}</span>
            </div>
          </div>
        </div>

        <div class="artifact-strip">
          <article class="product-card artifact-card">
            <span>CAD</span>
            <strong>{{ groups.cad.length || '--' }}</strong>
            <small>图纸文件</small>
          </article>
          <article class="product-card artifact-card">
            <span>论文</span>
            <strong>{{ groups.document.length || '--' }}</strong>
            <small>文档文件</small>
          </article>
          <article class="product-card artifact-card">
            <span>ZIP</span>
            <strong>{{ groups.package.length || '--' }}</strong>
            <small>成果包</small>
          </article>
        </div>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { analyzeDesignPackage, generateDesignPackage } from '../../api/rewrite'
import ModelViewer3D from '../../components/ModelViewer3D.vue'

const router = useRouter()
const files = reactive({})
const taskText = ref('')
const analyzing = ref(false)
const generating = ref(false)
const targetConfirmed = ref(false)
const artifacts = ref([])
const parameters = ref([])
const packageMessage = ref('等待任务书输入。')
const currentStep = ref(0)

const processSteps = [
  { index: 1, label: '解析任务...' },
  { index: 2, label: '生成结构...' },
  { index: 3, label: '构建 CAD...' },
  { index: 4, label: '渲染 3D...' },
  { index: 5, label: '打包文件...' }
]

const project = reactive({
  projectTitle: '',
  equipmentName: '',
  designType: '',
  designDepth: 'graduation',
  mainFunctions: [],
  mainStructures: [],
  explicitParameters: [],
  derivedParameters: [],
  suggestedParameters: []
})

const canAnalyze = computed(() => Boolean(files.taskBook?.raw || taskText.value.trim()))
const stageLabel = computed(() => generating.value ? processSteps[Math.max(0, currentStep.value - 1)]?.label || '正在生成...' : targetConfirmed.value ? '可以生成' : '等待输入')
const progress = computed(() => {
  if (generating.value) return Math.min(98, currentStep.value * 19)
  if (artifacts.value.length) return 100
  if (targetConfirmed.value) return 42
  if (analyzing.value) return 18
  return 0
})
const progressTitle = computed(() => artifacts.value.length ? '成果包已生成' : generating.value ? '正在渐进生成' : targetConfirmed.value ? '解析完成' : '生成状态')
const groups = computed(() => ({
  cad: artifacts.value.filter(x => /\.(dxf|svg|png)$/i.test(x.fileName || '')),
  document: artifacts.value.filter(x => /\.(docx|pdf)$/i.test(x.fileName || '')),
  package: artifacts.value.filter(x => /\.(zip|json)$/i.test(x.fileName || ''))
}))
const modelProject = computed(() => ({
  ...project,
  totalLength: findParameter('总长', 4200),
  totalWidth: findParameter('总宽', 1800),
  totalHeight: findParameter('总高', 2600)
}))

function setFile(key, file) {
  files[key] = { raw: file.raw, name: file.name }
  targetConfirmed.value = false
  artifacts.value = []
  packageMessage.value = '已选择任务书。'
}

function flattenParameters(source = {}) {
  let id = Date.now()
  return [
    ...(source.explicitParameters || []).map(row => toRow(row, 'explicit', id++)),
    ...(source.derivedParameters || []).map(row => toRow(row, 'derived', id++)),
    ...(source.suggestedParameters || []).map(row => toRow(row, 'suggested', id++))
  ]
}

function toRow(row, category, id) {
  return { id, name: row.name, value: row.value, unit: row.unit || '', category, note: row.source || row.basis || '' }
}

function findParameter(name, fallback) {
  const row = parameters.value.find(item => item.name === name)
  return Number.isFinite(Number(row?.value)) ? Number(row.value) : fallback
}

function syncProjectParameters() {
  const map = category => parameters.value.filter(row => row.category === category && row.name?.trim()).map(row => ({
    name: row.name.trim(),
    value: Number.isFinite(Number(row.value)) ? Number(row.value) : row.value,
    unit: row.unit || '',
    source: row.note || 'DropAI'
  }))
  project.explicitParameters = map('explicit')
  project.derivedParameters = map('derived')
  project.suggestedParameters = map('suggested')
}

async function analyze() {
  analyzing.value = true
  currentStep.value = 1
  packageMessage.value = '正在解析任务...'
  try {
    const form = new FormData()
    form.append('designDepth', project.designDepth)
    if (project.projectTitle?.trim()) form.append('title', project.projectTitle.trim())
    if (files.taskBook?.raw) {
      form.append('files', files.taskBook.raw)
      form.append('types', 'TASK_BOOK')
    }
    if (taskText.value.trim()) {
      form.append('text', taskText.value.trim())
    }
    const result = await analyzeDesignPackage(form)
    const analyzedProject = result.project || {}
    Object.assign(project, analyzedProject)
    project.projectTitle = result.title || analyzedProject.projectTitle || 'DropAI 工程项目'
    project.equipmentName = result.equipmentName || analyzedProject.equipmentName || project.equipmentName
    project.designType = result.designType || analyzedProject.designType || project.designType
    parameters.value = flattenParameters(analyzedProject)
    targetConfirmed.value = true
    currentStep.value = 2
    packageMessage.value = result.message || '正在生成结构...'
    ElMessage.success('任务解析完成。')
  } catch (error) {
    packageMessage.value = error.message || '解析失败。'
    ElMessage.error(packageMessage.value)
  } finally {
    analyzing.value = false
  }
}

async function generate() {
  if (!targetConfirmed.value) return
  generating.value = true
  artifacts.value = []
  syncProjectParameters()
  try {
    for (let i = 2; i <= 4; i += 1) {
      currentStep.value = i
      packageMessage.value = processSteps[i - 1].label
      await wait(320)
    }
    const result = await generateDesignPackage(project)
    Object.assign(project, result.project || {})
    parameters.value = flattenParameters(result.project || project)
    artifacts.value = result.artifacts || []
    currentStep.value = 5
    packageMessage.value = result.message || '完整成果包已生成。'
    ElMessage.success('成果包已生成。')
    router.push({ path: '/result', query: { name: project.projectTitle || 'DropAI 项目' } })
  } catch (error) {
    packageMessage.value = error.message || '生成失败。'
    ElMessage.error(packageMessage.value)
  } finally {
    generating.value = false
  }
}

function wait(ms) {
  return new Promise(resolve => window.setTimeout(resolve, ms))
}
</script>

<style scoped>
.brand {
  border: 0;
  background: transparent;
  cursor: pointer;
}

.builder-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}

.builder-head h1 {
  max-width: 760px;
  margin: 0;
  overflow-wrap: anywhere;
  font-size: clamp(32px, 4.6vw, 50px);
  line-height: 1.1;
}

.builder-grid {
  display: grid;
  grid-template-columns: 40fr 60fr;
  gap: 18px;
}

.input-panel {
  display: grid;
  align-content: start;
  gap: 16px;
  padding: 18px;
}

.panel-title h2,
.progress-head h2 {
  margin: 0 0 8px;
  font-size: 24px;
}

.panel-title p,
.progress-head p {
  margin: 0;
  color: var(--muted);
  line-height: 1.6;
}

.depth-control {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.depth-control button {
  min-height: 40px;
  border: 1px solid rgba(108, 99, 255, 0.12);
  border-radius: var(--radius);
  color: var(--muted);
  background: rgba(255, 255, 255, 0.58);
  cursor: pointer;
}

.depth-control .active {
  color: #fff;
  border-color: rgba(255, 255, 255, 0.82);
  background: var(--primary-gradient);
}

.action {
  width: 100%;
}

.output-panel {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.visual-stage {
  position: relative;
  min-height: 540px;
  overflow: hidden;
}

.visual-stage :deep(.model-viewer) {
  min-height: 540px;
  border-radius: var(--radius);
}

.stage-overlay {
  position: absolute;
  top: 18px;
  left: 18px;
  z-index: 2;
  display: grid;
  gap: 10px;
  max-width: min(420px, calc(100% - 36px));
  pointer-events: none;
}

.stage-overlay strong {
  overflow-wrap: anywhere;
  font-size: clamp(20px, 2.4vw, 26px);
  line-height: 1.18;
}

.progress-card {
  padding: 18px;
}

.progress-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
}

.progress-head strong {
  color: var(--cyan);
  font-size: 34px;
}

.steps {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
}

.steps div {
  display: grid;
  gap: 8px;
  color: var(--muted-2);
  font-size: 12px;
}

.steps i {
  width: 100%;
  height: 3px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.1);
}

.steps .done {
  color: var(--text);
}

.steps .done i {
  background: linear-gradient(90deg, var(--primary), var(--cyan));
}

.artifact-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.artifact-card {
  padding: 18px;
}

.artifact-card span {
  color: var(--cyan);
  font-size: 12px;
}

.artifact-card strong {
  display: block;
  margin-top: 8px;
  font-size: 34px;
}

.artifact-card small {
  color: var(--muted);
}

@media (max-width: 980px) {
  .builder-grid,
  .artifact-strip {
    grid-template-columns: 1fr;
  }

  .builder-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
