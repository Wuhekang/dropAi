<template>
  <main class="workspace">
    <nav class="topbar"><button class="brand" @click="router.push('/')"><b>D</b> DropAI Mechanical</button><button @click="router.push('/dashboard')">返回控制台</button></nav>
    <header class="header"><div><span>MECHANICAL CAD WORKSPACE</span><h1>OpenCascade 参数化机械设计</h1></div><strong :class="statusClass">{{ statusText }}</strong></header>
    <section class="shell">
      <aside class="input-pane">
        <h2>项目输入</h2><p>输入功能和工况。任务书只用于需求理解，尺寸由工程规则重新生成。</p>
        <textarea v-model="requirement" placeholder="例如：设计一个可夹持 120 mm 工件、夹紧力约 3 kN 的自动夹具。" />
        <label class="upload">上传任务书<input type="file" accept=".docx,.pdf,.txt,.md" @change="uploadRequirement" /></label>
        <button :disabled="busy||!requirement.trim()" @click="designOnly">生成设计方案</button>
        <button class="primary" :disabled="busy||!requirement.trim()" @click="execute">生成真实 CAD 成果</button>
        <div class="tools"><span>工程工具</span><div v-for="(value,key) in tools" :key="key"><b>{{ toolName(key) }}</b><em :class="{ok:value!=='missing'}">{{ value==='missing'?'未配置':'可用' }}</em></div></div>
      </aside>
      <section class="main-pane">
        <div class="process"><div v-for="step in process" :key="step.key" :class="stageClass(step.key)"><i/><span>{{ step.label }}</span></div></div>
        <div v-if="project.failureMessage" class="failure"><strong>{{ project.failureCode }}</strong><span>{{ project.failureMessage }}</span></div>
        <div class="engineering-grid">
          <MechanicalBrepViewer :src="modelUrl" />
          <aside class="design-info"><span>设计信息</span><h2>{{ project.productName||'等待设计' }}</h2><p>{{ project.concept?.selectedConcept||'尚未生成结构方案' }}</p>
            <dl><template v-for="item in project.parameters||[]" :key="item.name"><dt>{{ item.name }}</dt><dd>{{ item.value }} {{ item.unit }}</dd></template></dl>
          </aside>
        </div>
        <nav class="tabs"><button v-for="tab in tabs" :key="tab.key" :class="{active:activeTab===tab.key}" @click="activeTab=tab.key">{{ tab.label }}</button></nav>
        <section class="panel">
          <template v-if="activeTab==='model'"><h2>参数化零件</h2><article v-for="part in project.parts||[]" :key="part.partNumber" class="part"><b>{{ part.partNumber }} · {{ part.name }}</b><span>{{ part.purpose }}</span><small>{{ part.material }} · {{ part.manufacturing }} · {{ part.features.map(f=>f.type).join(' → ') }}</small></article></template>
          <template v-else-if="activeTab==='exploded'"><h2>装配与爆炸结构</h2><article v-for="item in project.assembly?.components||[]" :key="item.partNumber" class="part"><b>{{ item.partNumber }} · {{ item.name }}</b><span>位置 {{ pose(item.position) }}</span></article></template>
          <MechanicalArtifactPanel v-else :category="categoryForTab" :artifacts="project.artifacts||[]" @download="downloadFile" />
        </section>
      </section>
    </section>
  </main>
</template>
<script setup>
import { computed,onBeforeUnmount,onMounted,reactive,ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MechanicalBrepViewer from '../../components/MechanicalBrepViewer.vue'
import MechanicalArtifactPanel from '../../components/MechanicalArtifactPanel.vue'
import { designMechanicalProject,downloadArtifact,executeMechanicalProject,extractMechanicalRequirement,getMechanicalTools } from '../../api/rewrite'

const router=useRouter(),requirement=ref('设计一个可调丝杆自动夹具，用于工件定位和可靠夹紧'),busy=ref(false),activeTab=ref('model'),tools=reactive({}),project=reactive({status:'PENDING',stages:[],artifacts:[]}),modelUrl=ref('')
const process=[['REQUIREMENT_UNDERSTANDING','需求'],['CONCEPT_DESIGN','方案'],['PARAMETER_GENERATION','参数'],['CAD_DSL','CAD DSL'],['BREP_GENERATION','BRep'],['ASSEMBLY','装配'],['STEP_EXPORT','STEP'],['DRAWING_GENERATION','图纸'],['VALIDATION','验证']].map(([key,label])=>({key,label}))
const tabs=[{key:'model',label:'模型'},{key:'exploded',label:'爆炸图'},{key:'drawing',label:'工程图'},{key:'analysis',label:'云图'},{key:'document',label:'文档'},{key:'package',label:'成果包'}]
const categoryForTab=computed(()=>({drawing:'DRAWING',analysis:'ANALYSIS',document:'DOCUMENT',package:'PACKAGE'})[activeTab.value]||'')
const statusText=computed(()=>project.status==='COMPLETED'?'工程验证完成':project.status==='DESIGN_FAILED'?'DESIGN_FAILED':busy.value?'生成中':'等待输入')
const statusClass=computed(()=>({failed:project.status==='DESIGN_FAILED',passed:project.status==='COMPLETED'}))
async function designOnly(){await run(designMechanicalProject,'机械方案与 CAD DSL 已生成')}
async function execute(){await run(executeMechanicalProject,'真实 BRep 机械成果已生成');if(project.status==='COMPLETED')await loadModel()}
async function loadModel(){const file=(project.artifacts||[]).find(x=>x.name==='Assembly.stl');if(!file)return;const blob=await downloadArtifact(file.downloadUrl);if(modelUrl.value)URL.revokeObjectURL(modelUrl.value);modelUrl.value=URL.createObjectURL(blob)}
async function downloadFile(file){try{const blob=await downloadArtifact(file.downloadUrl);const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=file.name;document.body.appendChild(a);a.click();a.remove();URL.revokeObjectURL(url)}catch(err){ElMessage.error(err.message||'下载失败')}}
async function uploadRequirement(e){const file=e.target.files?.[0];if(!file)return;busy.value=true;try{const r=await extractMechanicalRequirement(file);requirement.value=r?.text||'';ElMessage.success(`已读取：${r?.fileName||file.name}`)}catch(err){ElMessage.error(err.message||'任务书解析失败')}finally{busy.value=false;e.target.value=''}}
async function run(action,message){busy.value=true;try{Object.assign(project,await action({requirement:requirement.value.trim()})||{});project.status==='DESIGN_FAILED'?ElMessage.error(project.failureMessage||'CAD 生成失败'):ElMessage.success(message)}catch(err){ElMessage.error(err.message||'请求失败')}finally{busy.value=false}}
function stageClass(key){const s=(project.stages||[]).find(x=>x.stage===key);return{passed:s?.status==='PASSED',running:s?.status==='RUNNING',failed:s?.status==='FAILED'}}
function pose(v={}){return`${v.x||0}, ${v.y||0}, ${v.z||0}`}
function toolName(k){return({OPENCASCADE_BREP:'OpenCascade BRep',FREECAD_STEP_EXPORT:'FreeCAD STEP',BROWSER_STL_VIEWER:'浏览器 3D',ENGINEERING_DRAWING:'工程图',RULE_ANALYSIS:'规则分析',CALCULIX_FEA:'CalculiX FEA'})[k]||k}
onMounted(async()=>{try{Object.assign(tools,await getMechanicalTools())}catch{}});onBeforeUnmount(()=>{if(modelUrl.value)URL.revokeObjectURL(modelUrl.value)})
</script>
<style scoped>
.workspace{min-height:100vh;padding:16px 24px 42px;color:#182230;background:#f2f5f4}.topbar,.header{display:flex;align-items:center;justify-content:space-between;max-width:1540px;margin:auto}.topbar{height:52px}.topbar button{border:0;background:transparent;cursor:pointer}.brand{display:flex;align-items:center;gap:8px;font-size:16px}.brand b{display:grid;place-items:center;width:30px;height:30px;color:#fff;background:#176b57}.header{padding:24px 0 16px}.header span{font-size:11px;color:#687772}.header h1{margin:5px 0;font-size:30px}.header>strong{padding:7px 10px;border:1px solid #cbd5d1;background:#fff}.header .failed{color:#a43b32}.header .passed{color:#176b57}.shell{display:grid;grid-template-columns:300px minmax(0,1fr);gap:12px;max-width:1540px;margin:auto}.input-pane,.main-pane,.design-info,.panel{border:1px solid #d7dfdc;background:#fff}.input-pane{align-self:start;padding:16px}.input-pane p{color:#687772;line-height:1.55}.input-pane textarea{width:100%;min-height:190px;box-sizing:border-box;padding:10px;border:1px solid #c7d1cd;resize:vertical}.upload{display:block;margin-top:8px;padding:9px;border:1px dashed #aebbb6}.upload input{display:block;width:100%;margin-top:6px}.input-pane>button{width:100%;margin-top:8px;padding:10px;border:1px solid #adbbb6;background:#fff;cursor:pointer}.input-pane>.primary{color:#fff;border-color:#176b57;background:#176b57}.tools{display:grid;gap:7px;margin-top:18px;padding-top:14px;border-top:1px solid #e3e8e6}.tools>span{font-size:11px;color:#687772}.tools div{display:flex;justify-content:space-between;font-size:12px}.tools em{color:#a43b32;font-style:normal}.tools em.ok{color:#176b57}.main-pane{min-width:0;padding:12px}.process{display:grid;grid-template-columns:repeat(9,minmax(80px,1fr));gap:2px;overflow:auto}.process div{display:grid;gap:5px;padding:8px;color:#75817d;background:#f2f5f4;font-size:11px}.process i{width:20px;height:3px;background:#bfc9c5}.process .passed i{background:#23866e}.process .running i{background:#c38920}.process .failed i{background:#b64b42}.failure{display:flex;gap:12px;margin-top:10px;padding:11px;color:#8d2f28;border-left:4px solid #b64b42;background:#fff2f1}.engineering-grid{display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:10px;margin-top:10px}.design-info{padding:16px}.design-info>span{font-size:11px;color:#687772}.design-info h2{font-size:20px}.design-info p{color:#5e6c67;line-height:1.5}.design-info dl{display:grid;grid-template-columns:1fr auto;gap:8px;font-size:12px}.design-info dt{color:#687772}.design-info dd{margin:0;font-weight:600}.tabs{display:flex;gap:2px;margin-top:12px;border-bottom:1px solid #d7dfdc}.tabs button{padding:10px 15px;border:0;background:transparent;cursor:pointer}.tabs .active{color:#176b57;border-bottom:2px solid #176b57}.panel{min-height:170px;margin-top:10px;padding:16px}.part,.artifact{display:grid;grid-template-columns:180px 1fr auto;gap:12px;padding:10px;border-bottom:1px solid #e2e7e5}.part span,.part small{color:#687772}.empty{padding:30px;text-align:center;color:#687772}@media(max-width:1050px){.shell{grid-template-columns:1fr}.engineering-grid{grid-template-columns:1fr}.design-info{order:-1}.process{grid-template-columns:repeat(9,110px)}}
</style>
