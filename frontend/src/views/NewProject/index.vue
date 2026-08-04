<template>
  <main class="workspace">
    <nav class="topbar">
      <button class="brand" @click="router.push('/')"><b>D</b> DropAI Mechanical</button>
      <button @click="router.push('/dashboard')">返回控制台</button>
    </nav>

    <header class="header">
      <div><span>FEATURE-BASED CAD WORKSPACE</span><h1>机械 CAD 设计</h1></div>
      <strong :class="statusClass">{{ statusText }}</strong>
    </header>

    <section class="shell">
      <aside class="input-pane">
        <h2>设计需求</h2>
        <textarea v-model="requirement" placeholder="描述用途、载荷、行程、安装空间和制造约束" />
        <label class="upload">导入任务书<input type="file" accept=".docx,.pdf,.txt,.md" @change="uploadRequirement" /></label>
        <button :disabled="busy || !requirement.trim()" @click="designOnly">生成设计方案</button>
        <button class="primary" :disabled="busy || !requirement.trim()" @click="execute">生成 PartDesign 成果</button>
        <div class="tools">
          <span>运行环境</span>
          <div v-for="(value, key) in tools" :key="key"><b>{{ toolName(key) }}</b><em :class="{ ok: value !== 'missing' }">{{ value === 'missing' ? '未配置' : '可用' }}</em></div>
        </div>
      </aside>

      <section class="main-pane">
        <div class="process">
          <div v-for="step in process" :key="step.key" :class="stageClass(step.key)"><i /><span>{{ step.label }}</span></div>
        </div>
        <div v-if="project.failureMessage" class="failure"><strong>{{ project.failureCode }}</strong><span>{{ project.failureMessage }}</span></div>

        <div class="model-row">
          <MechanicalBrepViewer :src="modelUrl" />
          <aside class="summary">
            <span>当前设计</span><h2>{{ project.productName || '等待设计' }}</h2>
            <p>{{ project.concept?.selectedConcept || '提交需求后生成机械方案与可执行 Feature Spec。' }}</p>
            <dl><template v-for="item in project.parameters || []" :key="item.name"><dt>{{ item.name }}</dt><dd>{{ item.value }} {{ item.unit }}</dd></template></dl>
          </aside>
        </div>

        <nav class="tabs"><button v-for="tab in tabs" :key="tab.key" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">{{ tab.label }}</button></nav>
        <section class="panel">
          <template v-if="activeTab === 'design'">
            <header><h2>设计方案</h2><span>产品定义、功能树与机械模块</span></header>
            <div class="architecture"><b>{{ project.designSpec?.architecture?.selectedConcept || '等待方案' }}</b><p>{{ project.designSpec?.architecture?.selectionReason }}</p></div>
            <article v-for="module in project.designSpec?.modules || []" :key="module.id" class="module">
              <b>{{ module.name }}</b><span>{{ module.function }}</span><small>{{ module.installation }}</small>
            </article>
          </template>
          <template v-else-if="activeTab === 'features'">
            <header><h2>Feature Tree</h2><span>来自 FeatureBasedCADSpec</span></header>
            <article v-for="part in project.parts || []" :key="part.partNumber" class="part">
              <div><b>{{ part.partNumber }}</b><strong>{{ part.name }}</strong><small>{{ part.material }} · {{ part.manufacturing }}</small></div>
              <ol><li v-for="feature in part.features" :key="feature.order"><i>{{ feature.order }}</i><b>{{ feature.type }}</b><span>{{ feature.intent }}</span></li></ol>
            </article>
          </template>
          <template v-else-if="activeTab === 'assembly'">
            <header><h2>Assembly Constraints</h2><span>完成状态以 CAD 求解回执为准</span></header>
            <article v-for="(constraint, index) in project.assembly?.constraints || []" :key="index" class="constraint">
              <b>{{ constraint.type }}</b><span>{{ constraint.componentA }} · {{ constraint.referenceA }}</span><i>→</i><span>{{ constraint.componentB }} · {{ constraint.referenceB }}</span>
            </article>
          </template>
          <template v-else-if="activeTab === 'analysis'">
            <header><h2>工程分析</h2><span>{{ project.analysisReport?.method || '等待分析' }}</span></header>
            <dl class="metrics"><dt>控制载荷</dt><dd>{{ number(project.analysisReport?.governingLoadN) }} N</dd><dt>估算应力</dt><dd>{{ number(project.analysisReport?.estimatedStressMpa) }} MPa</dd><dt>安全系数</dt><dd>{{ number(project.analysisReport?.safetyFactor) }}</dd></dl>
            <p>{{ project.analysisReport?.conclusion }}</p>
            <MechanicalArtifactPanel category="ANALYSIS" :artifacts="project.artifacts || []" @download="downloadFile" />
          </template>
          <MechanicalArtifactPanel v-else :category="categoryForTab" :artifacts="project.artifacts || []" @download="downloadFile" />
        </section>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MechanicalBrepViewer from '../../components/MechanicalBrepViewer.vue'
import MechanicalArtifactPanel from '../../components/MechanicalArtifactPanel.vue'
import { designMechanicalProject, downloadArtifact, executeMechanicalProject, extractMechanicalRequirement, getMechanicalTools } from '../../api/rewrite'

const router = useRouter()
const requirement = ref('设计一个可调丝杆夹具，用于工件定位和可靠夹紧。')
const busy = ref(false)
const activeTab = ref('design')
const tools = reactive({})
const project = reactive({ status: 'PENDING', stages: [], artifacts: [] })
const modelUrl = ref('')
const process = [
  ['PRODUCT_DEFINITION', '产品定义'], ['FUNCTIONAL_DECOMPOSITION', '功能树'], ['MECHANICAL_ARCHITECTURE', '机械架构'],
  ['PART_PLANNING', '零件规划'], ['ASSEMBLY_INTENT', '装配意图'], ['FEATURE_SPEC', 'Feature Spec'],
  ['FEATURE_EXECUTION', 'PartDesign'], ['VALIDATION', '真实性验证'], ['PACKAGE', '成果']
].map(([key, label]) => ({ key, label }))
const tabs = [
  { key: 'design', label: '设计方案' }, { key: 'features', label: 'Feature Tree' }, { key: 'assembly', label: '装配约束' },
  { key: 'drawing', label: '工程图' }, { key: 'analysis', label: '分析' },
  { key: 'document', label: '文档' }, { key: 'package', label: '成果包' }
]
const categoryForTab = computed(() => ({ drawing: 'DRAWING', analysis: 'ANALYSIS', document: 'DOCUMENT', package: 'PACKAGE' })[activeTab.value] || '')
const statusText = computed(() => project.status === 'COMPLETED' ? '真实性验证通过' : project.status === 'DESIGN_FAILED' ? '生成失败' : busy.value ? '正在执行' : '等待输入')
const statusClass = computed(() => ({ failed: project.status === 'DESIGN_FAILED', passed: project.status === 'COMPLETED' }))

async function designOnly() { await run(designMechanicalProject, 'Feature 设计方案已生成') }
async function execute() { await run(executeMechanicalProject, 'PartDesign 成果已生成'); if (project.status === 'COMPLETED') await loadModel() }
async function loadModel() {
  const file = (project.artifacts || []).find(item => item.name === 'Assembly.stl')
  if (!file) return
  const blob = await downloadArtifact(file.downloadUrl)
  if (modelUrl.value) URL.revokeObjectURL(modelUrl.value)
  modelUrl.value = URL.createObjectURL(blob)
}
async function downloadFile(file) {
  try {
    const blob = await downloadArtifact(file.downloadUrl)
    const url = URL.createObjectURL(blob), link = document.createElement('a')
    link.href = url; link.download = file.name; document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url)
  } catch (error) { ElMessage.error(error.message || '下载失败') }
}
async function uploadRequirement(event) {
  const file = event.target.files?.[0]
  if (!file) return
  busy.value = true
  try { const result = await extractMechanicalRequirement(file); requirement.value = result?.text || ''; ElMessage.success(`已读取：${result?.fileName || file.name}`) }
  catch (error) { ElMessage.error(error.message || '任务书解析失败') }
  finally { busy.value = false; event.target.value = '' }
}
async function run(action, message) {
  busy.value = true
  try {
    Object.assign(project, await action({ requirement: requirement.value.trim() }) || {})
    project.status === 'DESIGN_FAILED' ? ElMessage.error(project.failureMessage || 'CAD 生成失败') : ElMessage.success(message)
  } catch (error) { ElMessage.error(error.message || '请求失败') }
  finally { busy.value = false }
}
function stageClass(key) { const state = (project.stages || []).find(item => item.stage === key); return { passed: state?.status === 'PASSED', running: state?.status === 'RUNNING', failed: state?.status === 'FAILED' } }
function number(value) { return Number(value || 0).toFixed(2) }
function toolName(key) { return ({ OPENCASCADE_BREP: 'OpenCascade', FREECAD_STEP_EXPORT: 'FreeCAD PartDesign', BROWSER_STL_VIEWER: '3D 预览', ENGINEERING_DRAWING: '工程图', RULE_ANALYSIS: '规则分析', CALCULIX_FEA: 'CalculiX FEA' })[key] || key }
onMounted(async () => { try { Object.assign(tools, await getMechanicalTools()) } catch {} })
onBeforeUnmount(() => { if (modelUrl.value) URL.revokeObjectURL(modelUrl.value) })
</script>

<style scoped>
.workspace{min-height:100vh;padding:16px 24px 42px;color:#17211e;background:#f1f4f3}.topbar,.header{display:flex;align-items:center;justify-content:space-between;max-width:1540px;margin:auto}.topbar{height:52px}.topbar button{border:0;background:transparent;cursor:pointer}.brand{display:flex;align-items:center;gap:8px;font-size:16px}.brand b{display:grid;place-items:center;width:30px;height:30px;color:#fff;background:#176b57}.header{padding:24px 0 16px}.header span,.panel header span,.summary>span{font-size:11px;color:#687772}.header h1{margin:5px 0;font-size:30px}.header>strong{padding:7px 10px;border:1px solid #cbd5d1;background:#fff}.header .failed{color:#a43b32}.header .passed{color:#176b57}.shell{display:grid;grid-template-columns:300px minmax(0,1fr);gap:12px;max-width:1540px;margin:auto}.input-pane,.main-pane{border:1px solid #d7dfdc;background:#fff}.input-pane{align-self:start;padding:16px}.input-pane textarea{width:100%;min-height:210px;box-sizing:border-box;padding:10px;border:1px solid #c7d1cd;resize:vertical}.upload{display:block;margin-top:8px;padding:9px;border:1px dashed #aebbb6}.upload input{display:block;width:100%;margin-top:6px}.input-pane>button{width:100%;margin-top:8px;padding:10px;border:1px solid #adbbb6;background:#fff;cursor:pointer}.input-pane>.primary{color:#fff;border-color:#176b57;background:#176b57}.tools{display:grid;gap:7px;margin-top:18px;padding-top:14px;border-top:1px solid #e3e8e6}.tools>span{font-size:11px;color:#687772}.tools div{display:flex;justify-content:space-between;font-size:12px}.tools em{color:#a43b32;font-style:normal}.tools em.ok{color:#176b57}.main-pane{min-width:0;padding:12px}.process{display:grid;grid-template-columns:repeat(9,minmax(92px,1fr));gap:2px;overflow:auto}.process div{display:grid;gap:5px;padding:8px;color:#75817d;background:#f2f5f4;font-size:11px}.process i{width:20px;height:3px;background:#bfc9c5}.process .passed i{background:#23866e}.process .running i{background:#c38920}.process .failed i{background:#b64b42}.failure{display:flex;gap:12px;margin-top:10px;padding:11px;color:#8d2f28;border-left:4px solid #b64b42;background:#fff2f1}.model-row{display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:10px;margin-top:10px}.summary{padding:16px;border-left:1px solid #d7dfdc}.summary h2{font-size:20px}.summary p{color:#5e6c67;line-height:1.5}.summary dl,.metrics{display:grid;grid-template-columns:1fr auto;gap:8px;font-size:12px}.summary dt,.metrics dt{color:#687772}.summary dd,.metrics dd{margin:0;font-weight:600}.tabs{display:flex;gap:2px;margin-top:12px;border-bottom:1px solid #d7dfdc;overflow:auto}.tabs button{padding:10px 15px;border:0;background:transparent;white-space:nowrap;cursor:pointer}.tabs .active{color:#176b57;border-bottom:2px solid #176b57}.panel{min-height:190px;padding:16px}.panel header{display:flex;align-items:end;justify-content:space-between}.architecture{padding:12px 0;border-bottom:1px solid #d7dfdc}.architecture p{color:#687772}.module{display:grid;grid-template-columns:180px 1fr 1fr;gap:12px;padding:11px 0;border-bottom:1px solid #e2e7e5}.module small{color:#687772}.part{display:grid;grid-template-columns:220px minmax(0,1fr);gap:18px;padding:14px 0;border-bottom:1px solid #e2e7e5}.part>div{display:grid;align-content:start;gap:4px}.part small{color:#687772}.part ol{display:flex;gap:6px;margin:0;padding:0;overflow:auto;list-style:none}.part li{display:grid;grid-template-columns:22px auto;gap:2px 7px;min-width:130px;padding:8px;border-left:2px solid #23866e;background:#f4f7f6}.part li i{grid-row:1/3;display:grid;place-items:center;width:22px;height:22px;color:#fff;background:#23866e;font-size:11px;font-style:normal}.part li span{grid-column:2;color:#687772;font-size:11px}.constraint{display:grid;grid-template-columns:120px 1fr 24px 1fr;gap:10px;padding:11px 0;border-bottom:1px solid #e2e7e5}.constraint i{text-align:center;color:#176b57}.empty{padding:30px;text-align:center;color:#687772}@media(max-width:1050px){.shell{grid-template-columns:1fr}.model-row{grid-template-columns:1fr}.summary{border-top:1px solid #d7dfdc;border-left:0}.process{grid-template-columns:repeat(9,110px)}}@media(max-width:650px){.workspace{padding:10px}.part,.module{grid-template-columns:1fr}.constraint{grid-template-columns:90px 1fr}.constraint i{display:none}}
</style>
