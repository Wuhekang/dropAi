<template>
  <main class="project-page">
    <header class="project-header">
      <div>
        <el-button text type="primary" @click="router.push('/dashboard')">← 返回 Dashboard</el-button>
        <span class="eyebrow">PARAMETRIC MECHANICAL DESIGN</span>
        <h1>设计生成</h1>
        <p>上传任务书和设计资料，AI 自动提取并推导设计参数，再生成 CAD 方案图、截图与设计说明。</p>
      </div>
      <el-tag type="warning" size="large">方案级图纸 · 需工程校核</el-tag>
    </header>

    <section class="workflow">
      <el-card class="form-card" shadow="never">
        <template #header><strong>1. 上传任务书并分析参数</strong></template>
        <el-form label-position="top">
          <el-form-item label="设计题目">
            <el-input v-model="title" placeholder="可选，模型也会根据任务书识别设计方向" />
          </el-form-item>
          <el-upload drag multiple action="" :auto-upload="false" :file-list="fileList" :on-change="onFileChange" :on-remove="onFileRemove"
            accept=".docx,.txt,.md,.dxf,.dwg,.png,.jpg,.jpeg,.webp,.bmp">
            <div class="upload-copy"><strong>拖入任务书、开题报告、参考图和现有 CAD</strong><span>模型会区分任务书明确参数、推导参数和工程建议值</span></div>
          </el-upload>
          <el-button class="generate-button" type="primary" size="large" :loading="analyzing" :disabled="!fileList.length" @click="analyze">
            分析任务书并生成设计参数
          </el-button>
        </el-form>
      </el-card>

      <el-card class="upload-card" shadow="never">
        <template #header><strong>2. 确认模型生成的参数</strong></template>
        <el-empty v-if="!analysisReady" description="上传任务书后，参数将由模型自动生成" />
        <template v-else>
          <el-alert type="success" :closable="false" :title="analysis.designType || '设计参数分析完成'" :description="analysis.summary" />
          <div class="parameter-grid">
            <el-form-item v-for="field in parameterFields" :key="field.key" :label="`${field.label} ${field.unit}`">
              <el-input-number v-model="parameters[field.key]" :min="field.min" :max="field.max" :step="field.step || 1" />
              <el-tag class="source-tag" size="small" :type="statusType(parameterMeta[field.key]?.status)">{{ statusName(parameterMeta[field.key]?.status) }}</el-tag>
            </el-form-item>
          </div>
          <el-form-item label="补充约束或人工修正说明">
            <el-input v-model="requirements" type="textarea" :rows="3" placeholder="参数已由模型生成；这里只填写需要纠正或补充的内容。" />
          </el-form-item>
        </template>
        <el-form-item class="output-select" label="设计说明交付物">
          <el-select v-model="outputType">
            <el-option v-for="type in outputTypes" :key="type.value" :label="type.label" :value="type.value" />
          </el-select>
        </el-form-item>
        <el-button class="generate-button" type="primary" size="large" :loading="generating" :disabled="!analysisReady" @click="generate">
          使用确认后的参数生成 CAD 与说明文档
        </el-button>
      </el-card>
    </section>

    <el-card v-if="designReady" class="result-card" shadow="never">
      <template #header><strong>3. 参数化 CAD 方案图</strong></template>
      <el-alert type="warning" :closable="false" title="这是参数驱动的方案级总装图，可编辑但不可未经校核直接加工。" />
      <div class="design-result">
        <div>
          <h3>关键设计参数</h3>
          <el-table :data="parameterRows" size="small">
            <el-table-column prop="name" label="参数" />
            <el-table-column prop="value" label="数值" width="110" />
            <el-table-column prop="unit" label="单位" width="80" />
            <el-table-column prop="basis" label="参数来源与依据" min-width="220" />
          </el-table>
        </div>
        <div class="cad-preview">
          <h3>总装侧视方案图</h3>
          <svg ref="generatedSvg" :viewBox="cadViewBox" preserveAspectRatio="xMidYMid meet" xmlns="http://www.w3.org/2000/svg">
            <rect x="0" y="0" width="100%" height="100%" fill="white" />
            <g>
              <template v-for="(shape, index) in generatedShapes" :key="index">
                <line v-if="shape.type === 'LINE'" :x1="shape.x1" :y1="-shape.y1" :x2="shape.x2" :y2="-shape.y2" />
                <circle v-else :cx="shape.cx" :cy="-shape.cy" :r="shape.r" />
              </template>
            </g>
          </svg>
          <div class="actions">
            <el-button type="primary" @click="downloadDxf">下载可编辑 DXF</el-button>
            <el-button type="success" @click="downloadCadScreenshot">下载 CAD 截图 PNG</el-button>
          </div>
        </div>
      </div>
      <div v-if="result.jobId" class="document-result">
        <div><h3>{{ result.fileName }}</h3><p>{{ result.message }}</p></div>
        <el-button type="success" @click="downloadResult">下载设计说明 Word</el-button>
      </div>
    </el-card>
  </main>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { analyzeEngineeringDesign, downloadMyDocument, generateEngineeringDocument } from '../../api/rewrite'

const router = useRouter()
const title = ref('')
const requirements = ref('')
const outputType = ref('DESIGN_PACKAGE')
const fileList = ref([])
const analyzing = ref(false)
const generating = ref(false)
const analysisReady = ref(false)
const designReady = ref(false)
const result = reactive({})
const analysis = reactive({})
const parameterMeta = reactive({})
const generatedSvg = ref()
const generatedShapes = ref([])
const cadViewBox = ref('0 -600 1800 700')
const parameters = reactive({ length: 1600, width: 900, height: 850, wheelbase: 1100, wheelDiameter: 260, load: 350, speed: 1.2, safetyFactor: 1.8 })
const parameterFields = [
  { key: 'length', label: '总长', unit: 'mm', min: 300, max: 10000 },
  { key: 'width', label: '总宽', unit: 'mm', min: 200, max: 5000 },
  { key: 'height', label: '总高', unit: 'mm', min: 200, max: 5000 },
  { key: 'wheelbase', label: '轴距', unit: 'mm', min: 200, max: 8000 },
  { key: 'wheelDiameter', label: '轮径', unit: 'mm', min: 50, max: 2000 },
  { key: 'load', label: '设计载荷', unit: 'kg', min: 1, max: 100000 },
  { key: 'speed', label: '目标速度', unit: 'm/s', min: .1, max: 100, step: .1 },
  { key: 'safetyFactor', label: '安全系数', unit: '', min: 1, max: 10, step: .1 }
]
const outputTypes = [
  { value: 'DESIGN_PACKAGE', label: '设计方案包' },
  { value: 'TASK_BOOK', label: '任务书' },
  { value: 'PROPOSAL', label: '开题报告' },
  { value: 'MIDTERM', label: '中期检查' },
  { value: 'THESIS_DRAFT', label: '论文初稿' }
]
const parameterRows = computed(() => [
  { name: '总体尺寸', value: `${parameters.length} × ${parameters.width} × ${parameters.height}`, unit: 'mm', basis: `${parameterMeta.length?.source || ''}；${parameterMeta.length?.basis || ''}` },
  { name: '轴距', value: parameters.wheelbase, unit: 'mm', basis: `${parameterMeta.wheelbase?.source || ''}；${parameterMeta.wheelbase?.basis || ''}` },
  { name: '轮径', value: parameters.wheelDiameter, unit: 'mm', basis: `${parameterMeta.wheelDiameter?.source || ''}；${parameterMeta.wheelDiameter?.basis || ''}` },
  { name: '设计载荷', value: parameters.load, unit: 'kg', basis: `${parameterMeta.load?.source || ''}；${parameterMeta.load?.basis || ''}` },
  { name: '目标速度', value: parameters.speed, unit: 'm/s', basis: `${parameterMeta.speed?.source || ''}；${parameterMeta.speed?.basis || ''}` },
  { name: '设计载荷力', value: Math.round(parameters.load * 9.81 * parameters.safetyFactor), unit: 'N', basis: '质量 × 重力加速度 × 安全系数' }
])
function onFileChange(file, files) { fileList.value = files; analysisReady.value = false }
function onFileRemove(file, files) { fileList.value = files; analysisReady.value = false }
async function analyze() {
  analyzing.value = true
  try {
    const data = new FormData()
    data.append('title', title.value)
    fileList.value.forEach((file) => data.append('files', file.raw))
    const response = await analyzeEngineeringDesign(data)
    Object.assign(analysis, response)
    Object.entries(response.parameters || {}).forEach(([key, parameter]) => {
      if (key in parameters) parameters[key] = Number(parameter.value)
      parameterMeta[key] = parameter
    })
    if (!title.value) title.value = response.designType || '机械结构设计'
    analysisReady.value = true
  } finally { analyzing.value = false }
}
function buildRequirements() {
  return `${requirements.value}\n模型从任务书分析并经用户确认的设计参数：总长 ${parameters.length} mm，总宽 ${parameters.width} mm，总高 ${parameters.height} mm，轴距 ${parameters.wheelbase} mm，轮径 ${parameters.wheelDiameter} mm，设计载荷 ${parameters.load} kg，目标速度 ${parameters.speed} m/s，安全系数 ${parameters.safetyFactor}。请继续区分任务书明确值、推导值、工程建议值与待校核项。`
}
async function generate() {
  generating.value = true
  buildCad()
  designReady.value = true
  try {
    const data = new FormData()
    data.append('title', title.value)
    data.append('outputType', outputType.value)
    data.append('requirements', buildRequirements())
    fileList.value.forEach((file) => data.append('files', file.raw))
    Object.assign(result, await generateEngineeringDocument(data))
  } finally { generating.value = false }
}
function buildCad() {
  const L = parameters.length
  const H = parameters.height
  const wb = Math.min(parameters.wheelbase, L * .82)
  const r = parameters.wheelDiameter / 2
  const y = r + 40
  const left = (L - wb) / 2
  const right = left + wb
  const shapes = []
  const line = (x1, y1, x2, y2) => shapes.push({ type: 'LINE', x1, y1, x2, y2 })
  const circle = (cx, cy, radius) => shapes.push({ type: 'CIRCLE', cx, cy, r: radius })
  line(0, 0, L, 0); line(L, 0, L, H * .55); line(L, H * .55, L * .82, H * .82)
  line(L * .82, H * .82, L * .28, H * .82); line(L * .28, H * .82, L * .12, H * .58); line(L * .12, H * .58, 0, H * .55); line(0, H * .55, 0, 0)
  line(left - r, y - r - 25, right + r, y - r - 25); line(right + r, y - r - 25, right + r, y + r + 25)
  line(right + r, y + r + 25, left - r, y + r + 25); line(left - r, y + r + 25, left - r, y - r - 25)
  circle(left, y, r); circle(right, y, r)
  const supportCount = 5
  for (let i = 1; i <= supportCount; i++) circle(left + (wb * i) / (supportCount + 1), y - r * .18, r * .48)
  line(L * .42, H * .82, L * .42, H); line(L * .42, H, L * .53, H); line(L * .53, H, L * .53, H * .82)
  generatedShapes.value = shapes
  cadViewBox.value = `${-L * .06} ${-H * 1.08} ${L * 1.12} ${H * 1.18}`
}
function dxfPair(code, value) { return `${code}\n${value}\n` }
function downloadDxf() {
  let dxf = '0\nSECTION\n2\nHEADER\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n'
  generatedShapes.value.forEach((shape) => {
    if (shape.type === 'LINE') dxf += dxfPair(0, 'LINE') + dxfPair(8, 'DESIGN_OUTLINE') + dxfPair(10, shape.x1) + dxfPair(20, shape.y1) + dxfPair(11, shape.x2) + dxfPair(21, shape.y2)
    else dxf += dxfPair(0, 'CIRCLE') + dxfPair(8, 'DESIGN_OUTLINE') + dxfPair(10, shape.cx) + dxfPair(20, shape.cy) + dxfPair(40, shape.r)
  })
  dxf += '0\nENDSEC\n0\nEOF\n'
  downloadBlob(new Blob([dxf], { type: 'application/dxf' }), `${title.value}-总装方案图.dxf`)
}
function downloadCadScreenshot() {
  const source = new XMLSerializer().serializeToString(generatedSvg.value)
  const image = new Image()
  const url = URL.createObjectURL(new Blob([source], { type: 'image/svg+xml;charset=utf-8' }))
  image.onload = () => {
    const canvas = document.createElement('canvas'); canvas.width = 1800; canvas.height = 1000
    const ctx = canvas.getContext('2d'); ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, canvas.width, canvas.height); ctx.drawImage(image, 0, 0, canvas.width, canvas.height)
    canvas.toBlob((blob) => downloadBlob(blob, `${title.value}-CAD方案图截图.png`), 'image/png')
    URL.revokeObjectURL(url)
  }
  image.src = url
}
async function downloadResult() {
  downloadBlob(await downloadMyDocument(result.jobId), result.fileName)
}
function downloadBlob(blob, name) {
  const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = name; link.click(); URL.revokeObjectURL(url)
}
function statusType(status) { return status === 'EXPLICIT' ? 'success' : status === 'INFERRED' ? 'warning' : 'info' }
function statusName(status) { return ({ EXPLICIT: '任务书明确', INFERRED: '资料推导', RECOMMENDED: '工程建议' })[status] || '工程建议' }
</script>

<style scoped>
.project-page{max-width:1380px;margin:auto;padding:32px 24px 70px}.project-header{display:flex;justify-content:space-between;gap:30px;margin-bottom:30px}.project-header h1{font-size:36px;margin:12px 0 8px}.project-header p{color:#64748b;margin:0}.eyebrow{display:block;margin-top:20px;font-size:12px;color:#7c3aed;font-weight:800;letter-spacing:.16em}.workflow{display:grid;grid-template-columns:.9fr 1.1fr;gap:22px}.form-card,.upload-card,.result-card{border-radius:18px}.parameter-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:0 12px;margin-top:18px}.parameter-grid :deep(.el-input-number){width:100%}.source-tag{margin-top:7px}.upload-copy{display:grid;gap:8px}.upload-copy span{color:#64748b}.output-select{margin-top:22px}.generate-button{width:100%;margin-top:16px}.result-card{margin-top:22px}.design-result{display:grid;grid-template-columns:.9fr 1.1fr;gap:24px;margin-top:18px}.cad-preview svg{width:100%;height:440px;background:#fff;border:1px solid #dbe3ef;border-radius:10px}.cad-preview line,.cad-preview circle{fill:none;stroke:#111827;stroke-width:2;vector-effect:non-scaling-stroke}.actions,.document-result{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:14px}.document-result{padding-top:20px;border-top:1px solid #e5e7eb}.document-result h3,.document-result p{margin:0 0 6px}.document-result p{color:#64748b}@media(max-width:1100px){.parameter-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:950px){.workflow,.design-result{grid-template-columns:1fr}}@media(max-width:600px){.parameter-grid{grid-template-columns:1fr}.project-header{display:block}}
</style>
