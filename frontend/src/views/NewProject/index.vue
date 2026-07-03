<template>
  <main class="workspace">
    <header class="hero">
      <div>
        <el-button text type="primary" @click="router.push('/dashboard')">返回工作台</el-button>
        <span class="eyebrow">GRADUATION DESIGN PACKAGE</span>
        <h1>完整毕业设计成果包</h1>
        <p>先读取任务书和开题报告，识别真实设计目标；毕业设计版会自动参考同类机械结构补全参数、总装图和关键零件图。</p>
      </div>
      <el-tag size="large" type="warning">方案级成果，需工程校核</el-tag>
    </header>

    <el-steps :active="activeStep" finish-status="success" class="steps" align-center>
      <el-step v-for="step in steps" :key="step" :title="step" />
    </el-steps>

    <section class="grid">
      <div class="step-one">
        <el-card shadow="never">
          <template #header><strong>上传毕业设计资料</strong></template>
          <div class="upload-slots">
            <div v-for="slot in uploadSlots" :key="slot.key" class="upload-slot">
              <div class="slot-main">
                <strong>{{ slot.label }}</strong>
                <span>{{ slot.hint }}</span>
              </div>
              <el-upload
                action=""
                :auto-upload="false"
                :limit="1"
                :show-file-list="false"
                :accept="slot.accept"
                :on-change="(file) => selectFile(slot.key, file)"
                :on-remove="() => removeFile(slot.key)"
              >
                <el-button size="small">选择文件</el-button>
              </el-upload>
              <div class="file-status" :class="fileState(slot.key).status">
                <template v-if="fileState(slot.key).name">
                  <b>{{ fileState(slot.key).name }}</b>
                  <span>{{ typeText(fileState(slot.key).type || slot.type) }} · {{ parseText(fileState(slot.key)) }}</span>
                </template>
                <span v-else>未上传</span>
              </div>
            </div>
          </div>
          <el-alert v-if="parseMessage" class="inline-alert" :type="analysisStatus === 'failed' ? 'warning' : 'info'" :title="parseMessage" :closable="false" />
          <el-button class="full" type="primary" :loading="analyzing" :disabled="!uploadedFiles.length" @click="analyze">
            第一步：自动识别设计目标
          </el-button>
        </el-card>

        <el-card shadow="never">
          <template #header>
            <div class="panel-head">
              <strong>AI识别设计目标</strong>
              <el-tag :type="analysisStatus === 'success' ? 'success' : analysisStatus === 'failed' ? 'danger' : 'info'">
                {{ analysisStatusText }}
              </el-tag>
            </div>
          </template>
          <el-empty v-if="analysisStatus === 'pending'" description="上传任务书后自动识别" />
          <el-alert v-if="analysisStatus === 'failed'" type="warning" title="未能从任务书中识别，请手动填写或重新上传资料。" :closable="false" />
          <el-form label-position="top" class="recognition-form">
            <el-form-item label="毕业设计题目">
              <el-input v-model="project.projectTitle" placeholder="上传任务书后自动识别" />
            </el-form-item>
            <el-form-item label="设备名称">
              <el-input v-model="project.equipmentName" placeholder="上传任务书后自动识别" />
            </el-form-item>
            <el-form-item label="设计类型">
              <el-select v-model="project.designType" filterable allow-create default-first-option placeholder="上传任务书后自动识别">
                <el-option v-for="item in designTypeOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="设计深度">
              <el-radio-group v-model="project.designDepth">
                <el-radio-button label="graduation">毕业设计版</el-radio-button>
                <el-radio-button label="engineering">工程版</el-radio-button>
              </el-radio-group>
              <p class="field-tip" v-if="project.designDepth === 'graduation'">毕业设计版允许任务书不完整，系统会自动补全总装图、三视图和五张关键零件图，补全内容可在下一步修改确认。</p>
              <p class="field-tip" v-else>工程版严格依据任务书和开题报告，缺少关键参数、结构或图纸规划时需要补充完整后生成。</p>
            </el-form-item>
            <el-form-item label="项目类别">
              <el-input v-model="project.projectCategory" placeholder="例如：机械类毕业设计" />
            </el-form-item>
            <el-form-item label="主要功能">
              <el-input v-model="functionsText" type="textarea" :rows="3" placeholder="例如：含尘气流沉降、颗粒物收集、设备检修维护" />
            </el-form-item>
            <el-form-item label="关键结构">
              <el-input v-model="structuresText" type="textarea" :rows="3" placeholder="例如：壳体、进风口、排灰斗、检修门、支撑架" />
            </el-form-item>
            <el-form-item label="已识别参数">
              <div class="recognized-params" v-if="parameters.length">
                <el-tag v-for="row in parameters.slice(0, 10)" :key="row.id" type="info">
                  {{ row.name }}={{ row.value }}{{ row.unit }}
                </el-tag>
              </div>
              <span v-else class="muted">上传任务书后自动识别</span>
            </el-form-item>
          </el-form>
          <el-button class="full" type="primary" :disabled="!canConfirmTarget" @click="confirmTarget">
            第二步：确认后生成参数表
          </el-button>
        </el-card>
      </div>

      <el-card shadow="never">
        <template #header>
          <div class="panel-head"><strong>参数确认与微调</strong><el-tag>{{ parameters.length }} 项参数</el-tag></div>
        </template>
        <el-alert type="info" title="参数来自识别结果、工程推导和系统建议。可直接修改、添加或删除，重新生成时论文、计算书和CAD会同步更新。" :closable="false" />
        <el-alert
          v-if="hasSuggestedParameters"
          class="inline-alert"
          type="warning"
          title="任务书部分参数未明确，系统已生成方案级建议值，可在下一步修改确认。"
          :closable="false"
        />
        <el-empty v-if="!targetConfirmed" description="请先完成设计目标识别与确认" />
        <template v-else>
          <div class="parameter-table">
            <div class="parameter-header"><span>参数名称</span><span>数值</span><span>单位</span><span>来源</span><span>依据说明</span><span>操作</span></div>
            <div v-for="(row, index) in parameters" :key="row.id" class="parameter-row" :class="{ suggested: row.category === 'suggested' }">
              <el-input v-model="row.name" placeholder="参数名称" />
              <el-input v-model="row.value" placeholder="数值" />
              <el-input v-model="row.unit" placeholder="单位" />
              <el-select v-model="row.category">
                <el-option label="资料明确" value="explicit" />
                <el-option label="工程推导" value="derived" />
                <el-option label="系统建议" value="suggested" />
              </el-select>
              <el-input v-model="row.note" placeholder="来源或推导依据" />
              <el-button type="danger" text @click="removeParameter(index)">删除</el-button>
            </div>
          </div>
          <el-button class="full" @click="addParameter">添加参数</el-button>
          <el-button class="full" type="primary" size="large" :loading="generating" @click="generate">
            重新计算并生成全部成果
          </el-button>
        </template>
      </el-card>
    </section>

    <el-card class="panel" shadow="never">
      <template #header>
        <div class="panel-head"><strong>成果生成状态</strong><el-tag :type="statusType(packageStatus)">{{ statusText(packageStatus) }}</el-tag></div>
      </template>
      <el-alert v-if="packageMessage" :type="packageStatus === 'success' ? 'success' : 'warning'" :title="packageMessage" :closable="false" />
      <el-empty v-if="!artifacts.length" description="确认参数后生成完整成果包" />
      <template v-else>
        <div class="metrics">
          <div><span>成功文件</span><strong>{{ successCount }}</strong></div>
          <div><span>失败文件</span><strong>{{ failedCount }}</strong></div>
          <div><span>CAD文件</span><strong>{{ groups.cad.length }}</strong></div>
          <div><span>生成完整度</span><strong>{{ completionText }}</strong></div>
        </div>
        <section v-if="hasDesignModel || previews.scheme || previews.cad" class="preview-stage">
          <div class="preview-card model-card">
            <div><strong>参数化3D方案模型</strong><span>随总长、总宽、总高和设备类型变化</span></div>
            <ModelViewer3D :project="modelProject" />
          </div>
          <div v-if="previews.scheme" class="preview-card">
            <div><strong>设备结构示意图</strong><span>{{ project.designType }} · {{ project.equipmentName }}</span></div>
            <img :src="previews.scheme" alt="设备结构示意图" />
          </div>
          <div v-if="previews.cad" class="preview-card drawing-preview-card">
            <div><strong>CAD 章节图纸预览</strong><span>{{ selectedDrawingLabel }}</span></div>
            <div class="drawing-switch">
              <button v-for="option in drawingOptions" :key="option.key" type="button" :class="{ active: selectedDrawing === option.key }" @click="selectDrawing(option.key)">{{ option.label }}</button>
            </div>
            <img :src="previews.cad" alt="CAD 章节图纸预览" />
          </div>
        </section>
        <el-tabs>
          <el-tab-pane label="设计计算预览">
            <el-table :data="project.calculations">
              <el-table-column prop="name" label="校核项目" /><el-table-column prop="formula" label="公式" />
              <el-table-column prop="substitution" label="代入" /><el-table-column label="结果"><template #default="{row}">{{ row.result }} {{ row.unit }}</template></el-table-column>
              <el-table-column prop="conclusion" label="结论" min-width="180" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="设计参数表">
            <el-table :data="parameters">
              <el-table-column prop="name" label="参数" />
              <el-table-column prop="value" label="数值" />
              <el-table-column prop="unit" label="单位" />
              <el-table-column label="来源类型">
                <template #default="{row}">
                  <el-tag :type="row.category === 'suggested' ? 'warning' : row.category === 'derived' ? 'success' : 'info'">
                    {{ categoryText(row.category) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="note" label="依据" min-width="220" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="材料与BOM">
            <div class="list-panel">
              <div>
                <h3>材料汇总表</h3>
                <el-tag v-for="item in project.materials || []" :key="item" class="list-tag">{{ item }}</el-tag>
              </div>
              <div>
                <h3>标准件</h3>
                <el-tag v-for="item in project.standardParts || []" :key="item" class="list-tag" type="success">{{ item }}</el-tag>
              </div>
            </div>
            <el-table :data="project.bom || []">
              <el-table-column prop="sequence" label="序号" width="80" />
              <el-table-column prop="name" label="名称" />
              <el-table-column prop="material" label="材料" />
              <el-table-column prop="quantity" label="数量" />
              <el-table-column prop="remark" label="备注" min-width="180" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="技术说明">
            <div class="list-panel">
              <div>
                <h3>工程细节</h3>
                <el-tag v-for="item in project.detailFeatures || []" :key="item" class="list-tag">{{ item }}</el-tag>
              </div>
              <div>
                <h3>技术要求</h3>
                <p v-for="item in project.technicalRequirements || []" :key="item" class="note-line">{{ item }}</p>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="论文插图清单">
            <div class="list-panel">
              <div>
                <h3>图纸视图</h3>
                <el-tag v-for="item in project.drawingViews || []" :key="item" class="list-tag" type="warning">{{ item }}</el-tag>
              </div>
              <div>
                <h3>标注清单</h3>
                <el-tag v-for="item in project.annotationList || []" :key="item" class="list-tag" type="info">{{ item }}</el-tag>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="CAD图纸与预览">
            <el-alert type="warning" title="当前CAD为方案级图纸，未经过完整工程校核，不可直接用于加工。" :closable="false" />
            <div class="tab-preview-card" v-if="previews.cad">
              <div class="tab-preview-head">
                <strong>CAD总装图预览</strong>
                <span>{{ selectedDrawingLabel }}</span>
              </div>
              <div class="drawing-switch" v-if="drawingOptions.length > 1">
                <button v-for="option in drawingOptions" :key="option.key" type="button" :class="{ active: selectedDrawing === option.key }" @click="selectDrawing(option.key)">{{ option.label }}</button>
              </div>
              <img :src="previews.cad" alt="CAD总装图预览" />
            </div>
            <el-empty v-else description="CAD预览图未返回，请检查 cad_preview.png / cad_preview.svg 是否生成成功" />
            <artifact-list :items="groups.cad" @download="download" />
          </el-tab-pane>
          <el-tab-pane label="论文与计算书"><artifact-list :items="groups.document" @download="download" /></el-tab-pane>
          <el-tab-pane label="成果包与参数"><artifact-list :items="groups.package" @download="download" /></el-tab-pane>
        </el-tabs>
      </template>
    </el-card>
  </main>
</template>

<script setup>
import { computed, defineComponent, h, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElMessage, ElTag } from 'element-plus'
import { analyzeDesignPackage, downloadArtifact, generateDesignPackage } from '../../api/rewrite'
import ModelViewer3D from '../../components/ModelViewer3D.vue'

const ArtifactList = defineComponent({
  props: { items: Array }, emits: ['download'],
  setup(props, { emit }) {
    return () => h('div', { class: 'artifact-grid' }, props.items.map(item => h('div', { class: 'artifact' }, [
      h('div', [h('strong', item.name || item.fileName), h('span', item.status === 'success' ? `${item.type?.toUpperCase()} · ${formatSize(item.size)}` : `失败原因：${item.failureReason || '未返回失败原因'}`)]),
      h('div', { class: 'artifact-action' }, [
        h(ElTag, { type: statusType(item.status), size: 'small' }, () => statusText(item.status)),
        h(ElButton, { type: item.status === 'success' ? 'primary' : 'danger', disabled: item.status !== 'success' || !item.downloadUrl || !item.size, onClick: () => emit('download', item) }, () => item.status === 'success' ? '下载' : '不可下载')
      ])
    ])))
  }
})

const router = useRouter()
const steps = ['上传资料', 'AI识别', '参数确认', '设计计算', '3D模型', '总装图', '零件图', '论文预览', '成果包']
const uploadSlots = [
  { key: 'taskBook', label: '任务书', type: 'TASK_BOOK', hint: '必传，用于识别题目、设备类型和基本要求', accept: '.docx,.pdf,.txt,.md' },
  { key: 'proposal', label: '开题报告', type: 'PROPOSAL', hint: '可选，用于补充设计背景和研究内容', accept: '.docx,.pdf,.txt,.md' }
]
const designTypeOptions = ['机械结构设计', '机械传动设计', '环保设备结构设计', '输送设备设计', '自动化设备设计', 'PLC控制设计', '机电一体化设计']
const files = reactive({})
const analyzing = ref(false), generating = ref(false), targetConfirmed = ref(false)
const analysisStatus = ref('pending'), parseMessage = ref('')
const artifacts = ref([]), parameters = ref([])
const functionsText = ref(''), structuresText = ref('')
const previews = reactive({ scheme: '', cad: '' })
const selectedDrawing = ref('cad_preview_png')
const baseDrawingOptions = [
  { key: 'cad_preview_png', label: 'CAD总装三视图', file: 'cad_preview.png' },
  { key: 'cad_preview_svg', label: 'CAD总装SVG', file: 'cad_preview.svg' },
  { key: 'overall_structure', label: '总体结构图', file: 'overall_structure.png' },
  { key: 'track_mechanism', label: '履带机构图', file: 'track_mechanism.png' },
  { key: 'cleaning_mechanism', label: '清扫机构图', file: 'cleaning_mechanism.png' },
  { key: 'frame_structure', label: '机架结构图', file: 'frame_structure.png' },
  { key: 'drive_mechanism', label: '驱动机构图', file: 'drive_mechanism.png' },
  { key: 'conveyor_belt', label: '输送带机构图', file: 'conveyor_belt.png' },
  { key: 'roller_mechanism', label: '滚筒机构图', file: 'roller_mechanism.png' },
  { key: 'shell_structure', label: '壳体结构图', file: 'shell_structure.png' },
  { key: 'inlet_outlet', label: '进出口接口图', file: 'inlet_outlet.png' },
  { key: 'ash_hopper', label: '排灰斗结构图', file: 'ash_hopper.png' },
  { key: 'access_door', label: '检修门结构图', file: 'access_door.png' },
  { key: 'support_frame', label: '支撑架结构图', file: 'support_frame.png' },
  { key: 'base_structure', label: '底座结构图', file: 'base_structure.png' },
  { key: 'upper_arm', label: '大臂结构图', file: 'upper_arm.png' },
  { key: 'forearm', label: '小臂结构图', file: 'forearm.png' },
  { key: 'gripper', label: '夹爪结构图', file: 'gripper.png' },
  { key: 'joint_drive', label: '关节驱动结构图', file: 'joint_drive.png' }
]
const packageStatus = ref('pending'), packageMessage = ref('等待生成')
const project = reactive({ projectId: '', projectTitle: '', equipmentName: '', designType: '', designDepth: 'graduation', projectCategory: '', mainFunctions: [], mainStructures: [], explicitParameters: [], derivedParameters: [], suggestedParameters: [], verificationItems: [], calculations: [], bom: [], technicalRequirements: [], materials: [], standardParts: [], detailFeatures: [], drawingViews: [], annotationList: [], structureTree: null, resolvedParts: [], assemblyTree: null, assemblyConstraints: [], components: [], partCount: 0, featureCount: 0, detailScore: 0, enhancementNotes: [] })

const uploadedFiles = computed(() => Object.values(files).filter(file => file?.raw))
const successCount = computed(() => artifacts.value.filter(x => x.status === 'success').length)
const failedCount = computed(() => artifacts.value.filter(x => x.status === 'failed').length)
const completionText = computed(() => artifacts.value.length ? `${Math.round(successCount.value / Math.max(1, artifacts.value.length) * 100)}%` : '--')
const activeStep = computed(() => packageStatus.value === 'success' ? steps.length : artifacts.value.length ? 7 : targetConfirmed.value ? 3 : analysisStatus.value === 'success' ? 2 : uploadedFiles.value.length ? 1 : 0)
const analysisStatusText = computed(() => ({ pending: '待识别', running: '正在解析资料', success: '已识别设计目标', failed: '识别失败' })[analysisStatus.value] || analysisStatus.value)
const canConfirmTarget = computed(() => Boolean(project.projectTitle?.trim() && project.equipmentName?.trim() && project.designType?.trim() && parameters.value.length))
const hasSuggestedParameters = computed(() => parameters.value.some(row => row.category === 'suggested'))
const groups = computed(() => ({
  cad: artifacts.value.filter(x => /\.dxf$/i.test(x.fileName) || /^cad_preview\.(svg|png)$/i.test(x.fileName) || (/^(?!preview\.)[a-z0-9_]+\.(svg|png)$/i.test(x.fileName) && x.fileName !== 'preview.png' && x.fileName !== 'preview.svg')),
  document: artifacts.value.filter(x => /\.(docx|pdf)$/i.test(x.fileName)),
  package: artifacts.value.filter(x => /\.(zip|json)$/i.test(x.fileName))
}))
const drawingOptions = computed(() => {
  const available = baseDrawingOptions.filter(option => artifacts.value.some(file => file.fileName === option.file && file.status === 'success'))
  return available
})
const selectedDrawingLabel = computed(() => drawingOptions.value.find(item => item.key === selectedDrawing.value)?.label || 'CAD图纸')
const hasDesignModel = computed(() => Boolean(
  targetConfirmed.value ||
  artifacts.value.length ||
  project.projectTitle ||
  project.equipmentName ||
  project.structureTree ||
  project.components?.length ||
  project.resolvedParts?.length
))
const modelProject = computed(() => ({
  projectTitle: project.projectTitle,
  equipmentName: project.equipmentName,
  designType: project.designType,
  totalLength: findParameter('总长', 4200),
  totalWidth: findParameter('总宽', 1600),
  totalHeight: findParameter('总高', 1800),
  structureTree: project.structureTree,
  resolvedParts: project.resolvedParts || [],
  assemblyTree: project.assemblyTree,
  components: project.components || [],
  assemblyConstraints: project.assemblyConstraints || []
}))

function selectFile(key, file) {
  resetProjectSession()
  files[key] = { raw: file.raw, name: file.name, type: uploadSlots.find(item => item.key === key)?.type, status: 'pending', textReadable: false, failureReason: '' }
  analysisStatus.value = 'pending'
  targetConfirmed.value = false
  parseMessage.value = '资料已选择，点击“第一步：自动识别设计目标”后开始解析。'
}
function removeFile(key) { delete files[key]; resetProjectSession(false) }
function fileState(key) { return files[key] || {} }
function typeText(type) {
  return ({ TASK_BOOK: '任务书', PROPOSAL: '开题报告', THESIS_TEMPLATE: '论文模板', REFERENCE: '参考文献', IMAGE_REFERENCE: '图片参考图', CAD_REFERENCE: 'CAD参考图', DOCUMENT: '文档资料' })[type] || '资料'
}
function parseText(file) {
  if (file.status === 'running') return '正在解析资料'
  if (file.status === 'success') return file.textReadable ? '成功读取文字' : (file.failureReason || '已接收参考资料')
  if (file.status === 'failed') return file.failureReason || '解析失败'
  return '待解析'
}
function flattenParameters(source) {
  let id = Date.now()
  return [
    ...(source.explicitParameters || []).map(row => toRow(row, 'explicit', id++)),
    ...(source.derivedParameters || []).map(row => toRow(row, 'derived', id++)),
    ...(source.suggestedParameters || []).map(row => toRow(row, 'suggested', id++))
  ]
}
function toRow(row, category, id) { return { id, name: row.name, value: row.value, unit: row.unit || '', category, note: row.source || row.basis || '' } }
function syncProjectParameters() {
  project.mainFunctions = splitList(functionsText.value)
  project.mainStructures = splitList(structuresText.value)
  const map = category => parameters.value.filter(row => row.category === category && row.name?.trim()).map(row => ({
    name: row.name.trim(), value: normalizeValue(row.value), unit: row.unit || '',
    ...(category === 'explicit' ? { source: row.note || '用户确认' } : { basis: row.note || '用户确认' })
  }))
  project.explicitParameters = map('explicit'); project.derivedParameters = map('derived'); project.suggestedParameters = map('suggested')
}
function normalizeValue(value) { return value !== '' && Number.isFinite(Number(value)) ? Number(value) : value }
function findParameter(name, fallback) {
  const row = parameters.value.find(item => item.name === name)
  return Number.isFinite(Number(row?.value)) ? Number(row.value) : fallback
}
function addParameter() { parameters.value.push({ id: Date.now(), name: '', value: '', unit: '', category: 'suggested', note: '用户新增参数' }) }
function removeParameter(index) { parameters.value.splice(index, 1) }
function splitList(value) { return String(value || '').split(/[、,，\n]/).map(item => item.trim()).filter(Boolean) }
async function analyze() {
  if (!files.taskBook?.raw) return ElMessage.warning('机械设计模块必须上传任务书，开题报告为可选。')
  clearGeneratedState()
  analyzing.value = true
  analysisStatus.value = 'running'
  parseMessage.value = '正在解析资料'
  Object.keys(files).forEach(key => { files[key].status = 'running'; files[key].failureReason = ''; files[key].textReadable = false })
  try {
    const form = new FormData()
    if (project.projectTitle?.trim()) form.append('title', project.projectTitle.trim())
    form.append('designDepth', project.designDepth || 'graduation')
    uploadedFiles.value.forEach(file => { form.append('files', file.raw); form.append('types', file.type) })
    const result = await analyzeDesignPackage(form)
    applyAnalysisResult(result)
    analysisStatus.value = result.status || 'success'
    parseMessage.value = result.message || (analysisStatus.value === 'success' ? '已识别设计目标' : '未能从任务书中识别，请手动填写或重新上传资料。')
    analysisStatus.value === 'success' ? ElMessage.success(parseMessage.value) : ElMessage.warning(parseMessage.value)
  } catch (error) {
    analysisStatus.value = 'failed'
    parseMessage.value = friendlyError(error)
    ElMessage.error(parseMessage.value)
  } finally { analyzing.value = false }
}
function resetProjectSession(clearTitle = true) {
  const depth = project.designDepth || 'graduation'
  Object.assign(project, { projectId: '', projectTitle: clearTitle ? '' : project.projectTitle, equipmentName: '', designType: '', designDepth: depth, projectCategory: '', mainFunctions: [], mainStructures: [], explicitParameters: [], derivedParameters: [], suggestedParameters: [], verificationItems: [], calculations: [], bom: [], technicalRequirements: [], materials: [], standardParts: [], detailFeatures: [], drawingViews: [], annotationList: [], structureTree: null, resolvedParts: [], assemblyTree: null, assemblyConstraints: [], components: [], partCount: 0, featureCount: 0, detailScore: 0, enhancementNotes: [] })
  functionsText.value = ''
  structuresText.value = ''
  parameters.value = []
  targetConfirmed.value = false
  clearGeneratedState()
}
function clearGeneratedState() {
  artifacts.value = []
  packageStatus.value = 'pending'
  packageMessage.value = '等待生成'
  for (const key of ['scheme', 'cad']) {
    if (previews[key]) URL.revokeObjectURL(previews[key])
    previews[key] = ''
  }
}
function applyAnalysisResult(result) {
  ;(result.documents || []).forEach(doc => {
    const key = Object.keys(files).find(item => files[item]?.name === doc.fileName)
    if (key) Object.assign(files[key], doc)
  })
  const analyzedProject = result.project || {}
  Object.assign(project, analyzedProject)
  project.projectTitle = result.title || analyzedProject.projectTitle || project.projectTitle || ''
  project.equipmentName = result.equipmentName || analyzedProject.equipmentName || project.equipmentName || ''
  project.designType = result.designType || analyzedProject.designType || project.designType || ''
  project.projectCategory = result.projectCategory || analyzedProject.projectCategory || project.projectCategory || '机械类毕业设计'
  functionsText.value = (result.mainFunctions || analyzedProject.mainFunctions || []).join('、')
  structuresText.value = (result.mainStructures || analyzedProject.mainStructures || []).join('、')
  parameters.value = flattenParameters(analyzedProject)
  targetConfirmed.value = false
}
function confirmTarget() {
  if (!canConfirmTarget.value) return ElMessage.warning('请先上传任务书并识别出题目、设备名称、设计类型和参数。')
  syncProjectParameters()
  targetConfirmed.value = true
  ElMessage.success('设计目标已确认，参数表已生成，可继续微调。')
}
async function generate() {
  if (!targetConfirmed.value) return ElMessage.warning('请先确认设计目标，不能直接生成默认通用图纸。')
  if (!parameters.value.length) return ElMessage.warning('请先识别资料或添加设计参数。')
  syncProjectParameters(); generating.value = true; packageStatus.value = 'running'; packageMessage.value = '正在生成并校验各项成果文件'
  try {
    const result = await generateDesignPackage(project); Object.assign(project, result.project); parameters.value = flattenParameters(result.project); artifacts.value = result.artifacts || []
    functionsText.value = (project.mainFunctions || []).join('、')
    structuresText.value = (project.mainStructures || []).join('、')
    await loadPreviews()
    packageStatus.value = result.status || 'failed'; packageMessage.value = result.message || '生成流程已结束'
    packageStatus.value === 'success' ? ElMessage.success(packageMessage.value) : ElMessage.warning(packageMessage.value)
  } catch (error) { packageStatus.value = 'failed'; packageMessage.value = friendlyError(error); ElMessage.error(packageMessage.value) } finally { generating.value = false }
}
async function loadPreviews() {
  for (const key of ['scheme', 'cad']) {
    if (previews[key]) URL.revokeObjectURL(previews[key])
    previews[key] = ''
  }
  const scheme = artifacts.value.find(file => file.fileName === 'preview.png' && file.status === 'success' && file.downloadUrl)
  if (scheme) {
    try { previews.scheme = URL.createObjectURL(await downloadArtifact(scheme.downloadUrl)) } catch (_) { /* artifact card keeps the failure visible */ }
  }
  await loadDrawingPreview()
}
async function loadDrawingPreview() {
  if (previews.cad) URL.revokeObjectURL(previews.cad)
  previews.cad = ''
  const options = drawingOptions.value
  if (!options.some(item => item.key === selectedDrawing.value)) selectedDrawing.value = options[0]?.key || 'cad_preview_png'
  const option = options.find(item => item.key === selectedDrawing.value) || options[0]
  if (!option) return
  const item = artifacts.value.find(file => file.fileName === option.file && file.status === 'success' && file.downloadUrl)
  if (!item) return
  try { previews.cad = URL.createObjectURL(await downloadArtifact(item.downloadUrl)) } catch (_) { /* artifact card keeps the failure visible */ }
}
function selectDrawing(key) {
  selectedDrawing.value = key
  if (artifacts.value.length) loadDrawingPreview()
}
async function download(item) {
  if (item.status !== 'success' || !item.downloadUrl || !item.size) return ElMessage.error(item.failureReason || '文件未生成成功，无法下载')
  try {
    const blob = await downloadArtifact(item.downloadUrl); if (!blob?.size) throw new Error('下载文件为空')
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = item.name || item.fileName; a.click(); URL.revokeObjectURL(url)
  } catch (error) { ElMessage.error(friendlyError(error)) }
}
function friendlyError(error) {
  const message = error?.message || '请求失败'
  if (message.includes('401') || message.includes('登录')) return '登录状态已失效，请重新登录后再生成。'
  if (message.includes('429') || message.includes('请求受限')) return '大模型接口请求频率受限，请稍后重试或更换可用API Key。'
  if (message.toLowerCase().includes('timeout') || message.includes('超时')) return '请求处理超时，请稍后重试。'
  return message === 'Request failed with status code 500' ? '成果生成失败，请刷新页面后重试。' : message
}
function formatSize(size) { return size >= 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(2)} MB` : `${Math.max(1, Math.round(size / 1024))} KB` }
function statusType(status) { return status === 'success' ? 'success' : status === 'failed' ? 'danger' : status === 'running' ? 'warning' : 'info' }
function statusText(status) { return ({ pending:'等待中', running:'生成中', success:'成功', failed:'失败', partial_success:'部分成功' })[status] || status }
function categoryText(category) { return ({ explicit: '资料明确', derived: '工程推导', suggested: '系统建议' })[category] || category }
</script>

<style scoped>
.workspace{max-width:1500px;margin:auto;padding:30px 24px 70px}.hero,.panel-head{display:flex;justify-content:space-between;align-items:flex-start;gap:24px}.hero h1{font-size:36px;margin:10px 0}.hero p{color:#64748b}.eyebrow{display:block;margin-top:18px;color:#2563eb;font-weight:800;font-size:12px;letter-spacing:.16em}.steps{margin:34px 0}.grid{display:grid;grid-template-columns:.9fr 1.1fr;gap:20px}.step-one{display:grid;gap:20px}.grid .el-card,.panel{border-radius:18px}.full{width:100%;margin:14px 0 0}.panel{margin-top:20px}.upload-slots{display:grid;gap:12px}.upload-slot{display:grid;grid-template-columns:1fr auto;gap:10px;align-items:center;padding:14px;border:1px solid #e4e9f1;border-radius:14px;background:#f8fafc}.slot-main span{display:block;color:#64748b;font-size:12px;margin-top:4px}.file-status{grid-column:1/-1;color:#64748b;font-size:12px}.file-status b{display:block;color:#0f172a;margin-bottom:3px}.file-status.success span{color:#059669}.file-status.failed span{color:#dc2626}.file-status.running span{color:#d97706}.inline-alert{margin-top:14px}.recognition-form{margin-top:8px}.field-tip{margin:8px 0 0;color:#64748b;font-size:12px;line-height:1.6}.recognized-params{display:flex;flex-wrap:wrap;gap:8px}.muted{color:#94a3b8}.parameter-table{margin-top:16px;overflow:auto}.parameter-header,.parameter-row{display:grid;grid-template-columns:1fr .75fr .5fr .8fr 1.45fr 60px;gap:8px;align-items:center;min-width:850px}.parameter-header{padding:8px 0;color:#64748b;font-size:13px;font-weight:700}.parameter-row{padding:8px 0;border-top:1px solid #edf1f6}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin:20px 0}.metrics div{padding:18px;border-radius:14px;background:#f4f7fb}.metrics span{display:block;color:#64748b}.metrics strong{font-size:28px}.preview-stage{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin:20px 0}.preview-card{border:1px solid #e4e9f1;border-radius:16px;padding:14px;background:#f8fafc}.preview-card div{display:flex;justify-content:space-between;gap:12px;margin-bottom:10px}.preview-card span{color:#64748b;font-size:12px}.preview-card img{display:block;width:100%;height:360px;object-fit:contain;background:white;border-radius:10px}.model-card{grid-column:1/-1}.model-card :deep(.model-viewer){height:430px;min-height:430px}.list-panel{display:grid;grid-template-columns:repeat(2,1fr);gap:18px;margin:10px 0 18px}.list-panel>div{padding:16px;border:1px solid #e4e9f1;border-radius:14px;background:#f8fafc}.list-panel h3{margin:0 0 12px}.list-tag{margin:0 8px 8px 0}.note-line{margin:0 0 8px;color:#334155;line-height:1.7}.artifact-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px;margin-top:14px}.artifact{display:flex;justify-content:space-between;align-items:center;padding:16px;border:1px solid #e4e9f1;border-radius:12px}.artifact span{display:block;color:#64748b;font-size:12px;margin-top:5px}.artifact-action{display:flex;align-items:center;gap:8px}@media(max-width:1050px){.grid,.preview-stage,.list-panel{grid-template-columns:1fr}.metrics{grid-template-columns:repeat(2,1fr)}}@media(max-width:700px){.steps{display:none}.artifact-grid,.metrics{grid-template-columns:1fr}.hero{display:block}.upload-slot{grid-template-columns:1fr}}
.parameter-row.suggested{background:#fffbeb}
.drawing-switch{display:flex;flex-wrap:wrap;gap:8px;margin:0 0 10px}.drawing-switch button{border:1px solid #cbd5e1;background:#fff;color:#334155;border-radius:6px;padding:7px 10px;font-size:12px;cursor:pointer}.drawing-switch button.active{background:#2563eb;border-color:#2563eb;color:#fff}.drawing-preview-card img{height:390px}
.tab-preview-card{margin:14px 0 18px;padding:14px;border:1px solid #e4e9f1;border-radius:14px;background:#f8fafc}.tab-preview-head{display:flex;justify-content:space-between;gap:12px;margin-bottom:10px}.tab-preview-head span{color:#64748b;font-size:12px}.tab-preview-card img{display:block;width:100%;height:460px;object-fit:contain;background:white;border-radius:10px;border:1px solid #edf1f6}
</style>
