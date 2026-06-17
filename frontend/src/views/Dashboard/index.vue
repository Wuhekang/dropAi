<template>
  <main class="dashboard-page">
    <header class="dashboard-header">
      <div>
        <span class="eyebrow">DROP AI ENGINEERING STUDIO</span>
        <h1>你好，{{ username }}</h1>
        <p>从任务书到论文、计算书、CAD图纸和3D方案展示，统一参数驱动，面向本科机械毕业设计成果包。</p>
      </div>
      <el-button text type="danger" @click="signOut">退出登录</el-button>
    </header>

    <HomeHero3D :project="heroProject" status="首页演示模型" />

    <section class="quick-actions">
      <button class="primary-action" type="button" @click="router.push('/new-project')">
        <span>开始生成</span>
        <strong>毕业设计成果包</strong>
        <p>上传任务书、确认参数，生成设计说明书、CAD总装图、零件图和方案展示。</p>
      </button>
      <button class="secondary-action" type="button" @click="router.push('/rewrite')">
        <span>现有功能</span>
        <strong>论文降重与降AI</strong>
        <p>处理论文段落和Word文档，降低重复表达与机械写作痕迹。</p>
      </button>
    </section>

    <section class="feature-grid">
      <div><strong>工程图纸</strong><p>三视图、剖视图、轴测图、尺寸链和关键零件图。</p></div>
      <div><strong>参数联动</strong><p>论文、计算书、CAD尺寸与3D展示使用同一套设计参数。</p></div>
      <div><strong>成果导出</strong><p>DOCX、DXF、SVG、PNG、PDF和ZIP成果包统一下载。</p></div>
      <div><strong>答辩展示</strong><p>首页和方案页提供可旋转、可缩放的3D机械模型。</p></div>
    </section>

    <el-card class="document-center" shadow="never">
      <template #header>
        <div class="section-head">
          <div>
            <h2>我的文档</h2>
            <p>当前及未来所有功能生成的文档都会在这里统一展示。</p>
          </div>
          <el-button :loading="loading" @click="loadDocuments">刷新</el-button>
        </div>
      </template>
      <el-table :data="documents" empty-text="暂无生成文档">
        <el-table-column prop="fileName" label="文档名称" min-width="240" show-overflow-tooltip />
        <el-table-column label="来源功能" width="150">
          <template #default="{ row }">{{ featureName(row.sourceFeature) }}</template>
        </el-table-column>
        <el-table-column prop="platformName" label="处理口径" width="110" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="190" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button text type="success" :disabled="row.status !== 'SUCCESS'" @click="download(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import HomeHero3D from '../../components/HomeHero3D.vue'
import { downloadMyDocument, getMyDocuments, logout } from '../../api/rewrite'

const router = useRouter()
const username = sessionStorage.getItem('dropai_username') || '当前账号'
const documents = ref([])
const loading = ref(false)
const heroProject = {
  projectTitle: '参数化机械设备设计展示',
  equipmentName: '带式输送设备',
  designType: '带式输送设备',
  totalLength: 4200,
  totalWidth: 1600,
  totalHeight: 1800
}

async function loadDocuments() {
  loading.value = true
  try { documents.value = await getMyDocuments() || [] } finally { loading.value = false }
}
async function download(row) {
  const blob = await downloadMyDocument(row.jobId)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = row.sourceFeature === 'REWRITE'
    ? row.fileName?.replace(/\.docx$/i, '') + '-生成结果.docx'
    : row.fileName
  link.click()
  URL.revokeObjectURL(url)
}
async function signOut() {
  try { await logout() } finally {
    sessionStorage.removeItem('dropai_token')
    sessionStorage.removeItem('dropai_username')
    router.replace('/login')
  }
}
function statusType(status) { return status === 'SUCCESS' ? 'success' : status === 'FAILED' ? 'danger' : 'warning' }
function statusText(status) { return ({ SUCCESS: '已完成', FAILED: '失败', RUNNING: '处理中', PENDING: '等待中' })[status] || status }
function featureName(feature) { return feature === 'REWRITE' ? '降重与降 AI' : feature === 'DESIGN_GENERATION' || feature === 'ENGINEERING_WRITING' ? '设计生成' : feature || '设计生成' }
onMounted(loadDocuments)
</script>

<style scoped>
.dashboard-page{max-width:1440px;margin:0 auto;padding:38px 24px 70px}
.dashboard-header,.section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:24px}
.eyebrow{color:#2563eb;font-size:12px;font-weight:800;letter-spacing:.15em}
h1{margin:8px 0 6px;font-size:38px}h2{margin:0 0 6px}
.dashboard-header p,.section-head p,.quick-actions p,.feature-grid p{margin:0;color:#64748b;line-height:1.7}
.quick-actions{display:grid;grid-template-columns:1.1fr .9fr;gap:18px;margin:28px 0}
.quick-actions button{border:1px solid #dbe5f4;border-radius:22px;padding:26px;text-align:left;cursor:pointer;background:#fff;transition:.2s}
.quick-actions button:hover{transform:translateY(-2px);box-shadow:0 18px 40px rgba(15,23,42,.1)}
.primary-action{background:linear-gradient(145deg,#eff6ff,#fff)!important}.secondary-action{background:linear-gradient(145deg,#faf5ff,#fff)!important}
.quick-actions span{font-size:12px;font-weight:800;color:#2563eb}.quick-actions strong{display:block;margin:12px 0 8px;font-size:25px;color:#172033}
.feature-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin:22px 0 28px}
.feature-grid div{padding:18px;border-radius:18px;background:#f8fafc;border:1px solid #e2e8f0}
.feature-grid strong{display:block;margin-bottom:8px;color:#172033}
.document-center{border-radius:18px}.section-head h2{font-size:22px}
@media(max-width:980px){.quick-actions,.feature-grid{grid-template-columns:1fr}.dashboard-header{align-items:center}h1{font-size:30px}}
</style>
