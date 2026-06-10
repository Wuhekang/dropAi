<template>
  <main class="project-page">
    <header class="project-header">
      <div>
        <el-button text type="primary" @click="router.push('/dashboard')">← 返回 Dashboard</el-button>
        <span class="eyebrow">MECHANICAL GRADUATION DESIGN</span>
        <h1>机械毕业设计文档生成</h1>
        <p>根据任务书、开题报告、学校模板、真实参考文献、CAD 与结构图片，生成对应毕业设计文件。</p>
      </div>
      <el-tag type="success" size="large">独立新功能</el-tag>
    </header>

    <section class="workflow">
      <el-card class="form-card" shadow="never">
        <template #header><strong>1. 定义生成任务</strong></template>
        <el-form label-position="top">
          <el-form-item label="毕业设计题目">
            <el-input v-model="title" placeholder="例如：健身器材机械结构设计" />
          </el-form-item>
          <el-form-item label="需要生成的文件">
            <div class="type-grid">
              <button v-for="type in outputTypes" :key="type.value" type="button" :class="{ active: outputType === type.value }" @click="outputType = type.value">
                <strong>{{ type.label }}</strong><span>{{ type.description }}</span>
              </button>
            </div>
          </el-form-item>
          <el-form-item label="补充要求">
            <el-input v-model="requirements" type="textarea" :rows="5" placeholder="填写学校格式、重点计算内容、章节要求等。没有则留空。" />
          </el-form-item>
        </el-form>
      </el-card>

      <el-card class="upload-card" shadow="never">
        <template #header><strong>2. 上传设计资料</strong></template>
        <el-upload drag multiple action="" :auto-upload="false" :file-list="fileList" :on-change="onFileChange" :on-remove="onFileRemove"
          accept=".docx,.txt,.md,.dxf,.dwg,.png,.jpg,.jpeg,.webp,.bmp">
          <div class="upload-copy"><strong>拖入任务书、开题报告、模板、文献、CAD 或图片</strong><span>支持 DOCX、TXT、DXF、DWG 和常见图片格式</span></div>
        </el-upload>
        <div v-if="cadFiles.length" class="cad-panel">
          <div><strong>CAD 文件已识别</strong><span>当前版本会将 CAD 文件名和类型纳入生成依据。</span></div>
          <el-tag v-for="file in cadFiles" :key="file.uid" type="info">{{ file.name }}</el-tag>
          <el-alert type="info" :closable="false" title="CAD 在线查看" description="当前支持 ASCII DXF 基础预览；DWG、复杂图层和标注后续接入专业转换服务。" />
          <div v-if="cadPreview.length" class="cad-preview">
            <svg :viewBox="cadViewBox" preserveAspectRatio="xMidYMid meet">
              <template v-for="(shape, index) in cadPreview" :key="index">
                <line v-if="shape.type === 'LINE'" :x1="shape.x1" :y1="-shape.y1" :x2="shape.x2" :y2="-shape.y2" />
                <circle v-else :cx="shape.cx" :cy="-shape.cy" :r="shape.r" />
              </template>
            </svg>
            <span>已绘制 {{ cadPreview.length }} 个直线或圆实体</span>
          </div>
        </div>
        <el-button class="generate-button" type="primary" size="large" :loading="generating" :disabled="!title || !fileList.length" @click="generate">
          调用真实模型并生成 Word
        </el-button>
      </el-card>
    </section>

    <el-card v-if="result.jobId" class="result-card" shadow="never">
      <template #header><strong>生成结果</strong></template>
      <div class="result-row">
        <div><h3>{{ result.fileName }}</h3><p>{{ result.message }}</p></div>
        <el-button type="success" @click="downloadResult">下载 Word</el-button>
      </div>
    </el-card>
  </main>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { downloadMyDocument, generateEngineeringDocument } from '../../api/rewrite'

const router = useRouter()
const title = ref('')
const requirements = ref('')
const outputType = ref('PROPOSAL')
const fileList = ref([])
const generating = ref(false)
const result = reactive({})
const cadPreview = ref([])
const cadViewBox = ref('0 0 100 100')
const outputTypes = [
  { value: 'TASK_BOOK', label: '任务书', description: '根据题目、模板与文献生成任务书' },
  { value: 'PROPOSAL', label: '开题报告', description: '生成研究现状、方案与进度安排' },
  { value: 'MIDTERM', label: '中期检查', description: '根据任务书与开题内容生成检查材料' },
  { value: 'THESIS_DRAFT', label: '论文初稿', description: '生成机械毕业设计论文结构化初稿' }
]
const cadFiles = computed(() => fileList.value.filter((file) => /\.(dxf|dwg)$/i.test(file.name)))
function onFileChange(file, files) {
  fileList.value = files
  if (/\.dxf$/i.test(file.name) && file.raw) previewDxf(file.raw)
}
function onFileRemove(file, files) { fileList.value = files }
async function generate() {
  generating.value = true
  try {
    const data = new FormData()
    data.append('title', title.value)
    data.append('outputType', outputType.value)
    data.append('requirements', requirements.value)
    fileList.value.forEach((file) => data.append('files', file.raw))
    Object.assign(result, await generateEngineeringDocument(data))
  } finally { generating.value = false }
}
async function downloadResult() {
  const blob = await downloadMyDocument(result.jobId)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = result.fileName
  link.click()
  URL.revokeObjectURL(url)
}
function previewDxf(file) {
  const reader = new FileReader()
  reader.onload = () => {
    const pairs = String(reader.result || '').split(/\r?\n/).map((value) => value.trim())
    const shapes = []
    for (let i = 0; i < pairs.length - 1; i += 2) {
      if (pairs[i] !== '0' || !['LINE', 'CIRCLE'].includes(pairs[i + 1])) continue
      const type = pairs[i + 1]
      const values = {}
      for (i += 2; i < pairs.length - 1 && pairs[i] !== '0'; i += 2) values[pairs[i]] = Number(pairs[i + 1])
      i -= 2
      if (type === 'LINE' && [values['10'], values['20'], values['11'], values['21']].every(Number.isFinite)) shapes.push({ type, x1: values['10'], y1: values['20'], x2: values['11'], y2: values['21'] })
      if (type === 'CIRCLE' && [values['10'], values['20'], values['40']].every(Number.isFinite)) shapes.push({ type, cx: values['10'], cy: values['20'], r: values['40'] })
      if (shapes.length >= 2000) break
    }
    cadPreview.value = shapes
    const points = shapes.flatMap((shape) => shape.type === 'LINE' ? [[shape.x1, -shape.y1], [shape.x2, -shape.y2]] : [[shape.cx - shape.r, -shape.cy - shape.r], [shape.cx + shape.r, -shape.cy + shape.r]])
    if (!points.length) return
    const xs = points.map((point) => point[0]); const ys = points.map((point) => point[1])
    const minX = Math.min(...xs); const minY = Math.min(...ys)
    const width = Math.max(1, Math.max(...xs) - minX); const height = Math.max(1, Math.max(...ys) - minY)
    cadViewBox.value = `${minX - width * .05} ${minY - height * .05} ${width * 1.1} ${height * 1.1}`
  }
  reader.readAsText(file)
}
</script>

<style scoped>
.project-page{max-width:1280px;margin:auto;padding:32px 24px 70px}.project-header{display:flex;justify-content:space-between;gap:30px;margin-bottom:30px}.project-header h1{font-size:34px;margin:12px 0 8px}.project-header p{color:#64748b;margin:0}.eyebrow{display:block;margin-top:20px;font-size:12px;color:#7c3aed;font-weight:800;letter-spacing:.16em}.workflow{display:grid;grid-template-columns:1fr 1fr;gap:22px}.form-card,.upload-card,.result-card{border-radius:18px}.type-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px;width:100%}.type-grid button{padding:16px;border:1px solid #dbe3ef;border-radius:12px;background:#fff;text-align:left;cursor:pointer}.type-grid button.active{border-color:#7c3aed;background:#faf5ff}.type-grid strong,.type-grid span{display:block}.type-grid span{margin-top:5px;color:#64748b;font-size:12px;line-height:1.5}.upload-copy{display:grid;gap:8px}.upload-copy span,.cad-panel span{color:#64748b}.cad-panel{display:grid;gap:12px;margin-top:18px;padding:16px;border-radius:12px;background:#f8fafc}.cad-preview{display:grid;gap:8px}.cad-preview svg{width:100%;height:260px;background:#fff;border:1px solid #dbe3ef;border-radius:8px}.cad-preview line,.cad-preview circle{fill:none;stroke:#111827;stroke-width:1;vector-effect:non-scaling-stroke}.generate-button{width:100%;margin-top:20px}.result-card{margin-top:22px}.result-row{display:flex;align-items:center;justify-content:space-between;gap:20px}.result-row h3{margin:0 0 7px}.result-row p{margin:0;color:#64748b}@media(max-width:850px){.workflow{grid-template-columns:1fr}.project-header{display:block}.type-grid{grid-template-columns:1fr}}
</style>
