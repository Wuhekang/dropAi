<template>
  <main class="workspace">
    <nav class="topbar">
      <button class="brand" type="button" @click="router.push('/')"><b>D</b> DropAI Mechanical</button>
      <button type="button" @click="router.push('/dashboard')">返回控制台</button>
    </nav>

    <header class="header">
      <div><span>MECHANICAL WORKSPACE</span><h1>SolidWorks 自动化机械工程</h1></div>
      <strong :class="statusClass">{{ statusText }}</strong>
    </header>

    <section class="layout">
      <aside class="input-pane">
        <h2>项目输入</h2>
        <p>描述产品、场景和功能。任务书用于理解需求，不会把示例尺寸直接复制到 CAD。</p>
        <textarea v-model="requirement" placeholder="例如：设计一台油罐壁面检测机器人，需要稳定吸附、移动、携带传感器并便于维护。" />
        <label class="upload">上传任务书（DOCX / PDF / TXT）<input type="file" accept=".docx,.pdf,.txt,.md" @change="uploadRequirement" /></label>
        <button type="button" :disabled="busy || !requirement.trim()" @click="designOnly">生成工程方案</button>
        <button class="primary" type="button" :disabled="busy || !requirement.trim()" @click="execute">执行 SolidWorks</button>
        <div class="tools">
          <span>工具状态</span>
          <div v-for="(value, key) in tools" :key="key"><b>{{ toolName(key) }}</b><em :class="{ ok: value !== 'missing' }">{{ value === 'missing' ? '未配置' : '已注册' }}</em></div>
        </div>
      </aside>

      <section class="result-pane">
        <div class="process">
          <div v-for="step in process" :key="step.key" :class="stageClass(step.key)"><i /><span>{{ step.label }}</span></div>
        </div>

        <div v-if="project.failureMessage" class="failure"><strong>{{ project.failureCode }}</strong><span>{{ project.failureMessage }}</span></div>

        <div class="preview">
          <div v-if="validatedModel">
            <strong>FreeCAD STEP 预览已验证</strong>
            <p>真实模型来源：02_STEP/Assembly.STEP</p>
          </div>
          <div v-else>
            <strong>尚无可展示模型</strong>
            <p>只有 SolidWorks 生成且通过 FreeCAD 重开验证的 STEP 才会显示。</p>
          </div>
        </div>

        <nav class="tabs">
          <button v-for="tab in tabs" :key="tab.key" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">{{ tab.label }}</button>
        </nav>

        <section v-if="activeTab === 'design'" class="panel">
          <h2>{{ project.productName || '等待生成机械方案' }}</h2>
          <p>{{ project.concept?.selectedConcept || '机械总工程师尚未生成方案。' }}</p>
          <div class="cards"><article v-for="item in project.concept?.modules || []" :key="item"><b>{{ item }}</b><span>工程模块</span></article></div>
        </section>
        <section v-else-if="activeTab === 'assembly'" class="panel">
          <h2>装配架构</h2>
          <div class="table"><div class="head"><span>组件</span><span>父级</span><span>位置</span></div><div v-for="item in project.assembly?.components || []" :key="item.id"><b>{{ item.name }}</b><span>{{ item.parent }}</span><span>{{ pose(item.position) }}</span></div></div>
        </section>
        <section v-else-if="activeTab === 'parts'" class="panel">
          <h2>SolidWorks Feature Plan</h2>
          <article v-for="part in project.parts || []" :key="part.partNumber" class="row"><b>{{ part.partNumber }} · {{ part.name }}</b><span>{{ part.material }}</span><small>{{ part.featureTree.map(f => f.type).join(' → ') }}</small></article>
        </section>
        <section v-else class="panel">
          <h2>真实成果文件</h2>
          <div v-if="!(project.artifacts || []).length" class="empty">暂无已验证成果。内部 JSON、Agent 日志和调试文件不会在此显示。</div>
          <article v-for="file in project.artifacts || []" :key="file.name" class="artifact"><b>{{ file.name }}</b><span>{{ file.validated ? '已验证' : '未验证' }}</span><a v-if="file.downloadUrl" :href="file.downloadUrl">下载</a></article>
        </section>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { designMechanicalProject, executeMechanicalProject, extractMechanicalRequirement, getMechanicalTools } from '../../api/rewrite'

const router = useRouter()
const requirement = ref('')
const busy = ref(false)
const activeTab = ref('design')
const tools = reactive({})
const project = reactive({ status: 'PENDING', stages: [], artifacts: [] })
const process = [
  ['REQUIREMENT_UNDERSTANDING', '需求分析'], ['MECHANICAL_DESIGN', '机械设计'], ['PARAMETER_GENERATION', '参数生成'],
  ['ASSEMBLY_ARCHITECTURE', '装配架构'], ['PART_DESIGN', '零件设计'], ['SOLIDWORKS_SCRIPT', '脚本生成'],
  ['SOLIDWORKS_EXECUTION', 'SolidWorks 执行'], ['STEP_VALIDATION', 'STEP / FreeCAD 验证'], ['DRAWING_EXPORT', '工程图验证']
].map(([key, label]) => ({ key, label }))
const tabs = [{ key: 'design', label: '设计方案' }, { key: 'assembly', label: '装配树' }, { key: 'parts', label: '零件特征' }, { key: 'artifacts', label: '模型 / 图纸 / 文档' }]
const statusText = computed(() => project.status === 'COMPLETED' ? '工程验证完成' : project.status === 'DESIGN_FAILED' ? 'DESIGN_FAILED' : busy.value ? '工程执行中' : '等待输入')
const statusClass = computed(() => ({ failed: project.status === 'DESIGN_FAILED', passed: project.status === 'COMPLETED' }))
const validatedModel = computed(() => project.status === 'COMPLETED' && (project.artifacts || []).some(x => /Assembly\.STEP$/i.test(x.name) && x.validated))

async function designOnly() { await run(designMechanicalProject, '工程方案已生成，确认后可执行 SolidWorks。') }
async function execute() { await run(executeMechanicalProject, '真实机械成果已生成。') }
async function uploadRequirement(event) {
  const file = event.target.files?.[0]
  if (!file) return
  busy.value = true
  try {
    const result = await extractMechanicalRequirement(file)
    requirement.value = result?.text || ''
    ElMessage.success(`已读取任务书：${result?.fileName || file.name}`)
  } catch (error) { ElMessage.error(error.message || '任务书解析失败') }
  finally { busy.value = false; event.target.value = '' }
}
async function run(action, successMessage) {
  busy.value = true
  try {
    const result = await action({ requirement: requirement.value.trim() })
    Object.assign(project, result || {})
    if (project.status === 'DESIGN_FAILED') ElMessage.error(project.failureMessage || '机械工程执行失败')
    else ElMessage.success(successMessage)
  } catch (error) { ElMessage.error(error.message || '机械工程请求失败') }
  finally { busy.value = false }
}
function stageClass(key) { const state = (project.stages || []).find(x => x.stage === key); return { passed: state?.status === 'PASSED', running: state?.status === 'RUNNING', failed: state?.status === 'FAILED' } }
function pose(value = {}) { return `${value.x || 0}, ${value.y || 0}, ${value.z || 0}` }
function toolName(key) { return ({ SOLIDWORKS_API: 'SolidWorks API', FREECAD_STEP_PREVIEW: 'FreeCAD 预览', DWG_EXPORT: 'DWG 导出', STEP_EXPORT: 'STEP 导出' })[key] || key }
onMounted(async () => { try { Object.assign(tools, await getMechanicalTools()) } catch {} })
</script>

<style scoped>
.workspace{min-height:100vh;padding:18px 28px 44px;color:#182230;background:#f4f6f8}.topbar,.header{display:flex;align-items:center;justify-content:space-between;gap:18px;max-width:1500px;margin:auto}.topbar{height:54px}.topbar button{border:0;background:transparent;cursor:pointer}.brand{display:flex;align-items:center;gap:9px;font-size:16px}.brand b{display:grid;place-items:center;width:30px;height:30px;color:#fff;background:#176b57}.header{padding:28px 0 18px}.header span{color:#60706b;font-size:12px}.header h1{margin:6px 0 0;font-size:34px}.header>strong{padding:7px 10px;border:1px solid #cad3d0;background:#fff}.header>strong.failed{color:#a43b32;border-color:#e7b8b4}.header>strong.passed{color:#176b57;border-color:#91c8ba}.layout{display:grid;grid-template-columns:320px minmax(0,1fr);gap:14px;max-width:1500px;margin:auto}.input-pane,.result-pane,.panel,.preview{border:1px solid #dce2e0;background:#fff}.input-pane{align-self:start;padding:18px}.input-pane p,.preview p{color:#68736f;line-height:1.6}.input-pane textarea{width:100%;min-height:210px;box-sizing:border-box;padding:12px;border:1px solid #cbd5d1;resize:vertical}.input-pane>button{width:100%;margin-top:9px;padding:11px;border:1px solid #aebbb6;background:#fff;cursor:pointer}.input-pane>button.primary{color:#fff;border-color:#176b57;background:#176b57}.input-pane>button:disabled{opacity:.45}.tools{display:grid;gap:8px;margin-top:20px;padding-top:16px;border-top:1px solid #e4e9e7}.tools>span{font-size:12px;color:#68736f}.tools div{display:flex;justify-content:space-between;font-size:13px}.tools em{color:#a43b32;font-style:normal}.tools em.ok{color:#176b57}.result-pane{min-width:0;padding:14px}.process{display:grid;grid-template-columns:repeat(9,minmax(90px,1fr));gap:2px;overflow-x:auto}.process div{display:grid;gap:5px;padding:9px;color:#78827f;background:#f4f6f5;font-size:11px}.process i{width:22px;height:3px;background:#bdc7c3}.process .passed i{background:#23866e}.process .running i{background:#c38920}.process .failed i{background:#b64b42}.failure{display:flex;gap:14px;margin-top:12px;padding:12px;color:#8d2f28;border-left:4px solid #b64b42;background:#fff2f1}.preview{display:grid;place-items:center;min-height:400px;margin-top:14px;text-align:center;background:#eef2f1}.tabs{display:flex;gap:2px;margin-top:14px;border-bottom:1px solid #dce2e0}.tabs button{padding:11px 16px;border:0;color:#64706c;background:transparent;cursor:pointer}.tabs button.active{color:#176b57;border-bottom:2px solid #176b57}.panel{margin-top:12px;padding:18px}.cards{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.cards article,.row,.artifact{display:grid;gap:5px;padding:11px;border:1px solid #e0e5e3}.cards span,.row span,.row small{color:#6c7673;font-size:12px}.table{display:grid}.table>div{display:grid;grid-template-columns:1fr 1fr 1fr;gap:10px;padding:9px;border-bottom:1px solid #e5e9e8}.table .head{color:#69736f;background:#f4f6f5}.row,.artifact{margin-top:7px}.artifact{grid-template-columns:1fr auto auto}.empty{padding:30px;color:#6c7673;text-align:center;background:#f5f7f6}@media(max-width:900px){.layout{grid-template-columns:1fr}.process{grid-template-columns:repeat(9,120px)}.cards{grid-template-columns:1fr}.header{align-items:flex-start;flex-direction:column}}
.upload{display:block;margin-top:9px;padding:10px;border:1px dashed #aebbb6;color:#52615d;cursor:pointer}.upload input{display:block;width:100%;margin-top:7px}
</style>
