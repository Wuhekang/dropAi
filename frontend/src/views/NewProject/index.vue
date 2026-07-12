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
        <span class="eyebrow">Mechanical Design Engine</span>
        <h1>AI毕业设计工程生成系统</h1>
        <p>从任务书识别、参数设计、结构方案、三维模型、CAD工程图到论文和成果包，按同一套设计数据贯通生成。</p>
      </div>
      <span class="status-pill"><span class="status-dot"></span>{{ stageLabel }}</span>
    </header>

    <section class="builder-grid">
      <aside class="input-panel panel">
        <div class="panel-title">
          <h2>任务书输入</h2>
          <p>上传任务书，开题报告可粘贴到文本区作为补充。系统会先分析需求，再生成完整工程成果包。</p>
        </div>

        <div class="drop-zone">
          <strong>任务书文件</strong>
          <span>{{ files.taskBook?.name || '支持 DOCX、PDF、TXT、Markdown' }}</span>
          <el-upload action="" :auto-upload="false" :show-file-list="false" accept=".docx,.pdf,.txt,.md" :on-change="file => setFile('taskBook', file)">
            <button class="ghost-button" type="button">选择文件</button>
          </el-upload>
        </div>

        <textarea v-model="taskText" class="text-input" placeholder="也可以直接粘贴任务书正文或开题报告关键要求..."></textarea>

        <div class="depth-control">
          <button :class="{ active: project.designDepth === 'graduation' }" type="button" @click="project.designDepth = 'graduation'">毕业设计</button>
          <button :class="{ active: project.designDepth === 'engineering' }" type="button" @click="project.designDepth = 'engineering'">工程设计</button>
        </div>

        <button class="primary-button action" type="button" :disabled="!canAnalyze || analyzing || generating" @click="startFullGeneration">
          {{ analyzing ? '正在识别项目...' : generating ? '正在生成工程成果...' : targetConfirmed ? '重新生成完整成果' : '开始完整生成' }}
        </button>
        <button class="ghost-button action" type="button" :disabled="!targetConfirmed || generating" @click="generate">
          {{ generating ? '正在生成工程成果...' : '继续生成模型 / CAD / 论文 / ZIP' }}
        </button>
      </aside>

      <section class="output-panel">
        <div class="visual-stage panel">
          <div class="stage-overlay">
            <span class="status-pill"><span class="status-dot"></span>3D 装配模型</span>
            <strong>{{ project.projectTitle || '等待任务书输入' }}</strong>
            <small>{{ project.equipmentName || '参数与结构生成后，模型会同步更新。' }}</small>
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
          <div class="design-flow">
            <div v-for="step in processSteps" :key="step.index" :class="stepClass(step.index)">
              <i>{{ stepMark(step.index) }}</i>
              <span>{{ step.label }}</span>
            </div>
          </div>
        </div>
      </section>
    </section>

    <section class="analysis-grid">
      <article class="product-card result-card">
        <span>项目识别</span>
        <h3>{{ project.projectTitle || '待识别项目' }}</h3>
        <dl>
          <div><dt>设备类型</dt><dd>{{ project.equipmentType || project.equipmentName || '--' }}</dd></div>
          <div><dt>使用场景</dt><dd>{{ project.applicationScenario || '--' }}</dd></div>
          <div><dt>复杂度</dt><dd>{{ complexityLabel }}</dd></div>
          <div><dt>核心功能</dt><dd>{{ listText(project.mainFunctions) }}</dd></div>
        </dl>
      </article>

      <article class="product-card result-card">
        <span>参数自动设计</span>
        <h3>{{ allParameters.length || '--' }} 项设计参数</h3>
        <div class="parameter-list">
          <div v-for="item in allParameters.slice(0, 8)" :key="`${item.category}-${item.name}`">
            <b>{{ item.name }}</b>
            <strong>{{ formatParameter(item) }}</strong>
            <small>{{ item.note || item.source || item.basis || item.category }}</small>
          </div>
        </div>
      </article>

      <article class="product-card result-card">
        <span>零件参数</span>
        <h3>{{ keyComponents.length || '--' }} 个关键零件</h3>
        <div class="component-list">
          <div v-for="part in keyComponents.slice(0, 6)" :key="part.partId || part.name">
            <strong>{{ part.name }}</strong>
            <span>{{ part.material || '材料待校核' }} · {{ part.length || 0 }}×{{ part.width || 0 }}×{{ part.height || 0 }}</span>
          </div>
        </div>
      </article>
    </section>

    <section class="detail-grid">
      <article class="panel structure-panel">
        <div class="section-head">
          <div>
            <span class="eyebrow">Structure</span>
            <h2>结构方案</h2>
          </div>
          <span class="tiny">结构树与装配关系来自后端设计流水线</span>
        </div>
        <ul class="structure-tree">
          <StructureNode :node="project.structureTree" />
        </ul>
      </article>

      <article class="panel drawing-panel">
        <div class="section-head">
          <div>
            <span class="eyebrow">CAD</span>
            <h2>图纸预览</h2>
          </div>
          <span class="tiny">总装图 / 三视图 / 零件图</span>
        </div>
        <div v-if="drawingPreviewUrl" class="drawing-preview" @click="openDrawingPreview(activeDrawingCard)">
          <img :src="drawingPreviewUrl" alt="CAD图纸预览" />
        </div>
        <div v-else class="drawing-placeholder">生成完成后显示 CAD 预览图，DXF 图纸可在下方下载。</div>
        <div class="drawing-files">
          <button v-for="card in drawingCards" :key="card.baseName" type="button" class="drawing-card" :class="{ active: card.baseName === activeDrawingCard?.baseName }" @click="selectDrawing(card)">
            <img v-if="previewUrls[card.preview?.fileName]" :src="previewUrls[card.preview.fileName]" :alt="card.title" />
            <i v-else>{{ card.preview ? '加载预览' : '无预览' }}</i>
            <strong>{{ card.title }}</strong>
            <span>{{ card.original?.fileName || card.preview?.fileName }}</span>
            <small>{{ formatSize(card.original?.size || card.preview?.size) }}</small>
            <em>
              <span @click.stop="openDrawingPreview(card)">查看</span>
              <span v-if="card.original" @click.stop="downloadFile(card.original)">下载DXF</span>
            </em>
          </button>
        </div>
      </article>
    </section>

    <section class="detail-grid">
      <article class="panel bom-panel">
        <div class="section-head">
          <div>
            <span class="eyebrow">BOM</span>
            <h2>物料清单</h2>
          </div>
          <span class="tiny">{{ bomRows.length || 0 }} 项</span>
        </div>
        <div class="bom-table">
          <div class="bom-head"><span>编号</span><span>名称</span><span>数量</span><span>材料</span><span>备注</span></div>
          <div v-for="item in bomRows.slice(0, 10)" :key="`${item.sequence}-${item.name}`">
            <span>{{ item.sequence }}</span>
            <strong>{{ item.name }}</strong>
            <span>{{ item.quantity }}</span>
            <span>{{ item.material }}</span>
            <small>{{ item.remark || '--' }}</small>
          </div>
        </div>
      </article>

      <article class="panel artifact-panel">
        <div class="section-head">
          <div>
            <span class="eyebrow">Deliverables</span>
            <h2>成果下载</h2>
          </div>
          <button class="primary-button" type="button" :disabled="!zipArtifact" @click="downloadFile(zipArtifact)">下载ZIP</button>
        </div>
        <div class="artifact-grid">
          <article v-for="file in artifacts" :key="file.fileName" class="artifact-file">
            <span>{{ artifactType(file) }}</span>
            <strong>{{ file.fileName }}</strong>
            <small>{{ file.status === 'failed' ? file.failureReason : formatSize(file.size) }}</small>
            <button class="ghost-button" type="button" :disabled="file.status === 'failed'" @click="downloadFile(file)">下载</button>
          </article>
        </div>
      </article>
    </section>

    <div v-if="previewModal.open" class="preview-modal" @click.self="closeDrawingPreview">
      <section class="preview-dialog">
        <header>
          <div>
            <span class="eyebrow">Drawing Preview</span>
            <h2>{{ previewModal.title }}</h2>
          </div>
          <button type="button" @click="closeDrawingPreview">关闭</button>
        </header>
        <div class="preview-toolbar">
          <button type="button" @click="previewModal.zoom = Math.max(.4, previewModal.zoom - .2)">缩小</button>
          <button type="button" @click="previewModal.zoom = 1">100%</button>
          <button type="button" @click="previewModal.zoom = Math.min(3, previewModal.zoom + .2)">放大</button>
          <button v-if="previewModal.original" type="button" @click="downloadFile(previewModal.original)">下载DXF</button>
          <button v-if="previewModal.preview" type="button" @click="downloadFile(previewModal.preview)">下载预览图</button>
        </div>
        <div class="preview-canvas">
          <img v-if="previewModal.url" :src="previewModal.url" :style="{ transform: `scale(${previewModal.zoom})` }" alt="CAD图纸大图预览" />
          <p v-else>图纸预览加载失败，请下载原始文件查看。</p>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, defineComponent, h, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { analyzeDesignPackage, createDesignPackageJob, downloadArtifact, getDesignPackageJob } from '../../api/rewrite'
import ModelViewer3D from '../../components/ModelViewer3D.vue'

const StructureNode = defineComponent({
  name: 'StructureNode',
  props: { node: { type: Object, default: null } },
  setup(props) {
    const renderNode = node => {
      if (!node) return null
      const children = Array.isArray(node.children) ? node.children : []
      return h('li', [
        h('div', { class: 'tree-node' }, [
          h('strong', node.name || '未命名结构'),
          h('span', [node.type || 'structure', node.source ? ` 来源 ${node.source}` : ''])
        ]),
        children.length ? h('ul', children.map(renderNode)) : null
      ])
    }
    return () => renderNode(props.node)
  }
})

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
const activeJobId = ref(localStorage.getItem('dropai_design_package_job_id') || '')
const jobProgress = ref(0)
const jobStage = ref('')
const jobStatus = ref('')
const drawingPreviewUrl = ref('')
const previewUrls = reactive({})
const activeDrawingBase = ref('')
const previewModal = reactive({ open: false, title: '', url: '', zoom: 1, original: null, preview: null })
let jobTimer = null

const processSteps = [
  { index: 1, label: '文件解析' },
  { index: 2, label: '项目识别' },
  { index: 3, label: '机械方案' },
  { index: 4, label: '结构方案' },
  { index: 5, label: '零件与装配' },
  { index: 6, label: 'STEP与图纸' },
  { index: 7, label: '论文与图片' },
  { index: 8, label: '成果包完成' }
]

const project = reactive({
  projectTitle: '',
  equipmentName: '',
  equipmentType: '',
  applicationScenario: '',
  designType: '',
  projectCategory: '',
  workingPrinciple: '',
  designDepth: 'graduation',
  partCount: 0,
  featureCount: 0,
  detailScore: 0,
  mainFunctions: [],
  mainStructures: [],
  components: [],
  bom: [],
  explicitParameters: [],
  derivedParameters: [],
  suggestedParameters: [],
  technicalRequirements: [],
  calculations: [],
  structureTree: { name: '整机', type: 'root', source: 'system', children: [] },
  assemblyTree: null,
  assemblyConstraints: [],
  drawingPlan: null,
  resolvedParts: [],
  materials: [],
  standardParts: [],
  drawingViews: []
})

const canAnalyze = computed(() => Boolean(files.taskBook?.raw || taskText.value.trim()))
const stageLabel = computed(() => artifacts.value.length ? '成果包已完成' : targetConfirmed.value ? '设计方案已生成' : '等待输入')
const allParameters = computed(() => parameters.value.length ? parameters.value : flattenParameters(project))
const keyComponents = computed(() => (project.components || []).filter(item => item.keyPart).length ? (project.components || []).filter(item => item.keyPart) : (project.components || []))
const bomRows = computed(() => project.bom?.length ? project.bom : project.drawingPlan?.bomTable || [])
const drawingFiles = computed(() => artifacts.value.filter(file => /\.(dxf|svg|png)$/i.test(file.fileName || '')))
const drawingCards = computed(() => {
  const files = artifacts.value.filter(file => file.status !== 'failed')
  const byName = new Map(files.map(file => [file.fileName, file]))
  const bases = new Set()
  files.forEach(file => {
    const name = file.fileName || ''
    if (/^assembly\.(dxf|svg|png)$/i.test(name)) bases.add('assembly')
    const part = name.match(/^(part_\d{2})\.(dxf|svg|png)$/i)
    if (part) bases.add(part[1])
  })
  return Array.from(bases).sort((a, b) => a.localeCompare(b)).map(baseName => {
    const preview = byName.get(`${baseName}.svg`) || byName.get(`${baseName}.png`)
    return {
      baseName,
      title: drawingName(`${baseName}.dxf`),
      original: byName.get(`${baseName}.dxf`),
      preview
    }
  })
})
const activeDrawingCard = computed(() => drawingCards.value.find(card => card.baseName === activeDrawingBase.value) || drawingCards.value[0] || null)
const zipArtifact = computed(() => artifacts.value.find(file => /\.zip$/i.test(file.fileName || '')))
const packageSucceeded = computed(() => Boolean(zipArtifact.value) && artifacts.value.every(file => file.status !== 'failed'))
const progress = computed(() => activeJobId.value ? Math.max(0, Math.min(100, Number(jobProgress.value || 0))) : packageSucceeded.value ? 100 : Math.min(98, Math.round((currentStep.value / processSteps.length) * 100)))
const displayedStep = computed(() => activeJobId.value ? stepFromProgress(progress.value) : currentStep.value)
const progressTitle = computed(() => packageSucceeded.value ? '完整成果已生成' : targetConfirmed.value ? 'AI设计方案已生成' : generating.value ? '正在生成工程成果' : 'AI设计流程')
const complexityLabel = computed(() => {
  const score = Number(project.detailScore || 0) + Number(project.partCount || 0) * 2 + Number(project.featureCount || 0)
  if (score >= 90 || (project.components || []).length >= 12) return '复杂项目'
  if (score >= 45 || (project.components || []).length >= 6) return '中等项目'
  if (targetConfirmed.value) return '简单项目'
  return '--'
})
const modelProject = computed(() => ({
  ...project,
  totalLength: findParameter('总长', 4200),
  totalWidth: findParameter('总宽', 1800),
  totalHeight: findParameter('总高', 2600)
}))

function setFile(key, file) {
  files[key] = { raw: file.raw, name: file.name }
  resetGeneratedState('已选择任务书。')
}

function resetGeneratedState(message) {
  stopJobPolling()
  localStorage.removeItem('dropai_design_package_job_id')
  activeJobId.value = ''
  jobProgress.value = 0
  jobStage.value = ''
  jobStatus.value = ''
  targetConfirmed.value = false
  artifacts.value = []
  revokePreview()
  packageMessage.value = message
  currentStep.value = 0
}

function flattenParameters(source = {}) {
  let id = Date.now()
  return [
    ...(source.explicitParameters || []).map(row => toRow(row, '任务书参数', id++)),
    ...(source.derivedParameters || []).map(row => toRow(row, '计算参数', id++)),
    ...(source.suggestedParameters || []).map(row => toRow(row, '建议参数', id++))
  ]
}

function toRow(row, category, id) {
  return { id, name: row.name, value: row.value, unit: row.unit || '', category, note: row.source || row.basis || '' }
}

function findParameter(name, fallback) {
  const row = allParameters.value.find(item => item.name === name)
  return Number.isFinite(Number(row?.value)) ? Number(row.value) : fallback
}

function syncProjectParameters() {
  const map = category => parameters.value.filter(row => row.category === category && row.name?.trim()).map(row => ({
    name: row.name.trim(),
    value: Number.isFinite(Number(row.value)) ? Number(row.value) : row.value,
    unit: row.unit || '',
    source: row.note || 'DropAI'
  }))
  project.explicitParameters = map('任务书参数')
  project.derivedParameters = map('计算参数')
  project.suggestedParameters = map('建议参数')
}

async function startFullGeneration() {
  if (targetConfirmed.value) {
    await generate()
    return
  }
  await analyze(true)
}

async function analyze(autoGenerate = false) {
  analyzing.value = true
  currentStep.value = 1
  packageMessage.value = '正在识别项目名称、设备类型、使用场景和核心功能...'
  let shouldGenerate = false
  try {
    const form = new FormData()
    form.append('designDepth', project.designDepth)
    if (project.projectTitle?.trim()) form.append('title', project.projectTitle.trim())
    if (files.taskBook?.raw) {
      form.append('files', files.taskBook.raw)
      form.append('types', 'TASK_BOOK')
    } else if (taskText.value.trim()) {
      form.append('files', new File([taskText.value.trim()], '任务书文本.txt', { type: 'text/plain' }))
      form.append('types', 'TASK_BOOK')
    }
    const result = await analyzeDesignPackage(form)
    const analyzedProject = result.project || {}
    Object.assign(project, analyzedProject)
    project.projectTitle = result.title || analyzedProject.projectTitle || 'DropAI 工程项目'
    project.equipmentName = result.equipmentName || analyzedProject.equipmentName || project.equipmentName
    project.designType = result.designType || analyzedProject.designType || project.designType
    parameters.value = flattenParameters(analyzedProject)
    targetConfirmed.value = true
    currentStep.value = 4
    packageMessage.value = autoGenerate
      ? '设计方案已生成，正在进入模型、CAD、论文和成果包生成...'
      : (result.message || '项目识别、复杂度分析、参数设计和结构方案已完成。')
    shouldGenerate = autoGenerate
    if (!autoGenerate) ElMessage.success('工程设计方案已生成。')
  } catch (error) {
    packageMessage.value = error.message || '解析失败。'
    ElMessage.error(packageMessage.value)
  } finally {
    analyzing.value = false
    if (shouldGenerate) await generate()
  }
}

async function generate() {
  if (!targetConfirmed.value) return
  generating.value = true
  artifacts.value = []
  revokePreview()
  syncProjectParameters()
  try {
    currentStep.value = 5
    packageMessage.value = '正在创建后端工程生成任务...'
    const job = await createDesignPackageJob(project)
    applyJobState(job)
    startJobPolling(job.jobId)
  } catch (error) {
    packageMessage.value = error.message || '生成失败。'
    ElMessage.error(packageMessage.value)
    generating.value = false
  }
}

function applyJobState(job = {}) {
  if (!job.jobId) return
  activeJobId.value = job.jobId
  localStorage.setItem('dropai_design_package_job_id', job.jobId)
  jobStatus.value = job.status || ''
  jobStage.value = job.stage || ''
  jobProgress.value = Number(job.progress || 0)
  currentStep.value = stepFromProgress(jobProgress.value)
  packageMessage.value = [job.stage, job.errorCode, job.message].filter(Boolean).join(' | ') || '工程生成任务正在执行'
  const result = job.result
  if (result?.project) {
    Object.assign(project, result.project)
    parameters.value = flattenParameters(result.project)
  }
  if (Array.isArray(result?.artifacts)) artifacts.value = result.artifacts
}

function startJobPolling(jobId) {
  stopJobPolling()
  if (!jobId) return
  jobTimer = window.setInterval(() => pollJob(jobId), 2000)
  pollJob(jobId)
}

function stopJobPolling() {
  if (jobTimer) {
    window.clearInterval(jobTimer)
    jobTimer = null
  }
}

async function pollJob(jobId) {
  try {
    const job = await getDesignPackageJob(jobId)
    applyJobState(job)
    if (job.status === 'SUCCESS') {
      stopJobPolling()
      generating.value = false
      localStorage.removeItem('dropai_design_package_job_id')
      activeJobId.value = ''
      jobProgress.value = 100
      currentStep.value = 8
      await loadDrawingPreview()
      await loadDrawingCardPreviews()
      ElMessage.success('工程成果包已生成')
    } else if (['FAILED', 'CANCELLED'].includes(job.status)) {
      stopJobPolling()
      generating.value = false
      localStorage.removeItem('dropai_design_package_job_id')
      packageMessage.value = [job.stage, job.errorCode, job.message].filter(Boolean).join(' | ') || '工程生成失败'
      ElMessage.error(packageMessage.value)
    } else {
      generating.value = true
    }
  } catch (error) {
    packageMessage.value = error.message || '任务状态查询失败'
  }
}

function stepFromProgress(value) {
  const progress = Number(value || 0)
  if (progress >= 98) return 8
  if (progress >= 82) return 7
  if (progress >= 68) return 6
  if (progress >= 45) return 5
  if (progress >= 30) return 4
  if (progress >= 18) return 3
  if (progress >= 8) return 2
  return progress > 0 ? 1 : 0
}

async function loadDrawingPreview() {
  const preview = artifacts.value.find(file => /^assembly\.(svg|png)$/i.test(file.fileName || '') && file.downloadUrl)
    || artifacts.value.find(file => /cad_preview\.(png|svg)$/i.test(file.fileName || '') && file.downloadUrl)
  if (!preview) return
  try {
    const blob = await downloadArtifact(preview.downloadUrl)
    revokePreview()
    drawingPreviewUrl.value = URL.createObjectURL(blob)
    activeDrawingBase.value = preview.fileName?.replace(/\.(svg|png)$/i, '') || ''
  } catch (error) {
    console.warn('[DropAI Engineering] CAD preview load failed', error)
  }
}

async function loadDrawingCardPreviews() {
  for (const card of drawingCards.value) {
    if (!card.preview?.downloadUrl || previewUrls[card.preview.fileName]) continue
    try {
      const blob = await downloadArtifact(card.preview.downloadUrl)
      previewUrls[card.preview.fileName] = URL.createObjectURL(blob)
    } catch (error) {
      console.warn('[DropAI Engineering] drawing card preview load failed', card.preview.fileName, error)
    }
  }
}

async function selectDrawing(card) {
  if (!card) return
  activeDrawingBase.value = card.baseName
  if (!card.preview?.downloadUrl) return
  try {
    const blob = await downloadArtifact(card.preview.downloadUrl)
    revokePreview()
    drawingPreviewUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    ElMessage.error('图纸预览加载失败，可下载DXF查看。')
  }
}

async function openDrawingPreview(card) {
  if (!card) return
  await selectDrawing(card)
  previewModal.open = true
  previewModal.title = card.title
  previewModal.url = drawingPreviewUrl.value || (card.preview ? previewUrls[card.preview.fileName] : '')
  previewModal.zoom = 1
  previewModal.original = card.original || null
  previewModal.preview = card.preview || null
}

function closeDrawingPreview() {
  previewModal.open = false
  previewModal.title = ''
  previewModal.url = ''
  previewModal.zoom = 1
  previewModal.original = null
  previewModal.preview = null
}

async function downloadFile(file) {
  if (!file?.downloadUrl) return
  const blob = await downloadArtifact(file.downloadUrl)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = file.fileName || 'dropai-engineering-file'
  link.click()
  URL.revokeObjectURL(url)
}

function revokePreview() {
  if (drawingPreviewUrl.value) URL.revokeObjectURL(drawingPreviewUrl.value)
  drawingPreviewUrl.value = ''
}

function revokeAllDrawingPreviews() {
  revokePreview()
  Object.keys(previewUrls).forEach(key => {
    URL.revokeObjectURL(previewUrls[key])
    delete previewUrls[key]
  })
  closeDrawingPreview()
}

function stepClass(index) {
  return { done: packageSucceeded.value || index < displayedStep.value, active: index === displayedStep.value && !packageSucceeded.value }
}

function stepMark(index) {
  if (packageSucceeded.value || index < displayedStep.value) return '✓'
  if (index === displayedStep.value) return '●'
  return '○'
}

function listText(items) {
  return Array.isArray(items) && items.length ? items.slice(0, 4).join('、') : '--'
}

function formatParameter(item) {
  const value = item.value ?? '--'
  return `${value}${item.unit || ''}`
}

function drawingName(name = '') {
  if (/^assembly\./i.test(name)) return '总装图'
  if (/cad_preview/i.test(name)) return '三视图预览'
  const part = name.match(/part_(\d{2})/i)
  if (part) return `关键零件图 ${part[1]}`
  return '工程图'
}

function artifactType(file) {
  const name = file?.fileName || ''
  if (/\.zip$/i.test(name)) return 'ZIP成果包'
  if (/\.docx$/i.test(name)) return '论文'
  if (/\.dxf$/i.test(name)) return 'CAD图纸'
  if (/model_3d/i.test(name)) return '三维模型数据'
  if (/\.(png|svg)$/i.test(name)) return '图纸预览'
  return file?.type || '文件'
}

function formatSize(size = 0) {
  if (!size) return '--'
  return size > 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(2)} MB` : `${Math.max(1, Math.round(size / 1024))} KB`
}

onMounted(() => {
  if (activeJobId.value) {
    generating.value = true
    packageMessage.value = '正在恢复工程生成任务进度...'
    startJobPolling(activeJobId.value)
  }
})

onBeforeUnmount(() => {
  stopJobPolling()
  revokeAllDrawingPreviews()
})
</script>

<style scoped>
.brand{border:0;background:transparent;cursor:pointer}.builder-page{width:min(1380px,calc(100% - 40px))}
.builder-head{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;margin-bottom:22px}.builder-head h1{max-width:860px;margin:0 0 10px;overflow-wrap:anywhere;font-size:clamp(34px,4.8vw,56px);line-height:1.06}.builder-head p{max-width:760px;margin:0;color:var(--muted);line-height:1.7}
.builder-grid{display:grid;grid-template-columns:380px minmax(0,1fr);gap:18px}.input-panel{display:grid;align-content:start;gap:16px;padding:18px}.panel-title h2,.progress-head h2,.section-head h2{margin:0 0 8px;font-size:24px}.panel-title p,.progress-head p{margin:0;color:var(--muted);line-height:1.6}
.depth-control{display:grid;grid-template-columns:1fr 1fr;gap:8px}.depth-control button{min-height:40px;border:1px solid rgba(108,99,255,.12);border-radius:var(--radius);color:var(--muted);background:rgba(255,255,255,.58);cursor:pointer}.depth-control .active{color:#fff;border-color:rgba(255,255,255,.82);background:var(--primary-gradient)}.action{width:100%}
.output-panel{display:grid;gap:14px;min-width:0}.visual-stage{position:relative;min-height:540px;overflow:hidden}.visual-stage :deep(.model-viewer){min-height:540px;border-radius:var(--radius)}.stage-overlay{position:absolute;top:18px;left:18px;z-index:2;display:grid;gap:10px;max-width:min(460px,calc(100% - 36px));pointer-events:none}.stage-overlay strong{overflow-wrap:anywhere;font-size:clamp(20px,2.4vw,28px);line-height:1.18}.stage-overlay small{color:var(--muted)}
.progress-card{padding:18px}.progress-head{display:flex;justify-content:space-between;gap:18px;margin-bottom:16px}.progress-head strong{color:var(--primary);font-size:34px}.design-flow{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-top:16px}.design-flow div{display:flex;align-items:center;gap:8px;min-height:42px;padding:10px;border:1px solid rgba(108,99,255,.1);border-radius:8px;color:var(--muted);background:rgba(255,255,255,.45);font-size:13px}.design-flow i{display:grid;place-items:center;width:22px;height:22px;border-radius:999px;background:rgba(108,99,255,.1);font-style:normal}.design-flow .done{color:var(--text);background:rgba(255,255,255,.72)}.design-flow .done i,.design-flow .active i{color:#fff;background:var(--primary-gradient)}
.analysis-grid,.detail-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px;margin-top:18px}.detail-grid{grid-template-columns:minmax(0,.9fr) minmax(0,1.1fr)}.result-card,.structure-panel,.drawing-panel,.bom-panel,.artifact-panel{padding:18px}.result-card>span,.artifact-file>span{color:var(--primary);font-size:12px;font-weight:800}.result-card h3{margin:8px 0 14px;font-size:22px}.result-card dl{display:grid;gap:10px;margin:0}.result-card dl div{display:grid;grid-template-columns:80px 1fr;gap:12px}.result-card dt{color:var(--muted);font-size:13px}.result-card dd{margin:0;line-height:1.55}
.parameter-list,.component-list{display:grid;gap:10px}.parameter-list div,.component-list div{display:grid;gap:4px;padding:10px;border:1px solid rgba(108,99,255,.1);border-radius:8px;background:rgba(255,255,255,.52)}.parameter-list strong{color:var(--primary)}.parameter-list small,.component-list span{color:var(--muted);font-size:12px}
.section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:14px;margin-bottom:14px}.structure-tree,.structure-tree ul{display:grid;gap:8px;margin:0;padding-left:18px}.structure-tree{padding-left:0;list-style:none}.structure-tree :deep(li){list-style:none}.tree-node{display:grid;gap:4px;padding:10px;border:1px solid rgba(108,99,255,.1);border-radius:8px;background:rgba(255,255,255,.52)}.tree-node span{color:var(--muted);font-size:12px}
.drawing-preview{display:grid;place-items:center;min-height:280px;margin-bottom:12px;overflow:hidden;border:1px solid rgba(108,99,255,.1);border-radius:8px;background:rgba(255,255,255,.55);cursor:zoom-in}.drawing-preview img{max-width:100%;max-height:420px;object-fit:contain}.drawing-placeholder{display:grid;place-items:center;min-height:220px;margin-bottom:12px;border:1px dashed rgba(108,99,255,.18);border-radius:8px;color:var(--muted);background:rgba(255,255,255,.42)}.drawing-files{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.drawing-card{display:grid;gap:7px;min-width:0;padding:10px;border:1px solid rgba(108,99,255,.1);border-radius:8px;color:var(--text);background:rgba(255,255,255,.56);text-align:left;cursor:pointer;transition:transform .18s ease,border-color .18s ease,box-shadow .18s ease}.drawing-card:hover,.drawing-card.active{transform:translateY(-2px);border-color:rgba(255,126,179,.38);box-shadow:0 14px 34px rgba(108,99,255,.12)}.drawing-card img,.drawing-card i{display:grid;place-items:center;width:100%;height:112px;border-radius:7px;background:#fff;object-fit:contain;color:var(--muted);font-style:normal}.drawing-card strong,.drawing-card span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.drawing-card span,.drawing-card small{color:var(--muted);font-size:12px}.drawing-card em{display:flex;gap:8px;align-items:center;font-style:normal}.drawing-card em span{padding:5px 9px;border-radius:999px;color:#fff;background:var(--primary-gradient);font-size:12px;font-weight:700}
.preview-modal{position:fixed;inset:0;z-index:80;display:grid;place-items:center;padding:24px;background:rgba(15,23,42,.42);backdrop-filter:blur(16px)}.preview-dialog{display:grid;grid-template-rows:auto auto minmax(0,1fr);width:min(1120px,calc(100vw - 48px));height:min(820px,calc(100vh - 48px));overflow:hidden;border:1px solid rgba(255,255,255,.72);border-radius:18px;background:rgba(255,255,255,.82);box-shadow:0 30px 90px rgba(31,41,55,.24)}.preview-dialog header,.preview-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:16px 18px;border-bottom:1px solid rgba(108,99,255,.1)}.preview-dialog h2{margin:4px 0 0;font-size:22px}.preview-dialog button,.preview-toolbar button{border:0;border-radius:999px;padding:8px 12px;color:var(--text);background:rgba(255,255,255,.78);box-shadow:inset 0 0 0 1px rgba(108,99,255,.12);cursor:pointer}.preview-toolbar{justify-content:flex-start;flex-wrap:wrap}.preview-canvas{display:grid;place-items:center;min-height:0;overflow:auto;padding:20px;background:linear-gradient(135deg,rgba(243,248,255,.82),rgba(255,245,250,.86))}.preview-canvas img{max-width:100%;max-height:100%;transform-origin:center center;transition:transform .16s ease}.preview-canvas p{color:var(--muted)}
.bom-table{display:grid;gap:6px}.bom-table>div{display:grid;grid-template-columns:56px minmax(130px,1fr) 64px minmax(90px,1fr) minmax(120px,1.2fr);gap:10px;align-items:center;padding:10px;border:1px solid rgba(108,99,255,.1);border-radius:8px;background:rgba(255,255,255,.52);font-size:13px}.bom-table .bom-head{color:var(--muted);font-weight:700;background:rgba(255,255,255,.7)}.bom-table small{color:var(--muted)}
.artifact-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.artifact-file{display:grid;gap:8px;padding:12px;border:1px solid rgba(108,99,255,.1);border-radius:8px;background:rgba(255,255,255,.52)}.artifact-file strong{overflow-wrap:anywhere}.artifact-file small{color:var(--muted)}
@media(max-width:1100px){.builder-grid,.analysis-grid,.detail-grid{grid-template-columns:1fr}.design-flow,.drawing-files,.artifact-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.builder-head{align-items:flex-start;flex-direction:column}}
@media(max-width:680px){.builder-page{width:min(100% - 28px,1380px)}.design-flow,.drawing-files,.artifact-grid{grid-template-columns:1fr}.bom-table>div{grid-template-columns:1fr}.result-card dl div{grid-template-columns:1fr}}
</style>
