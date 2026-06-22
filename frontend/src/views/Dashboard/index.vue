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

    <section class="points-panel">
      <el-card class="points-card" shadow="never">
        <template #header>
          <div class="section-head">
            <div>
              <h2>我的积分</h2>
              <p>所有生成能力都会消耗积分，生成成功后自动扣减并记录流水。</p>
            </div>
            <el-button :loading="pointsLoading" @click="loadPoints">刷新积分</el-button>
          </div>
        </template>
        <div class="points-summary">
          <div><span>当前积分</span><strong>{{ pointAccount.points ?? '--' }}</strong></div>
          <div><span>累计获得</span><strong>{{ pointAccount.totalPoints ?? '--' }}</strong></div>
          <div><span>累计消耗</span><strong>{{ pointAccount.usedPoints ?? '--' }}</strong></div>
        </div>
        <el-table :data="pointAccount.recentTransactions || []" size="small" empty-text="暂无积分流水">
          <el-table-column prop="featureName" label="功能" min-width="130" />
          <el-table-column prop="pointsChange" label="积分变化" width="95" />
          <el-table-column prop="balanceAfter" label="余额" width="80" />
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        </el-table>
      </el-card>
      <el-card class="points-card admin-card" shadow="never">
        <h2>积分管理</h2>
        <p>管理员可进入功能价格管理，修改各生成能力的积分消耗和启用状态。</p>
        <el-button type="primary" :disabled="role !== 'ADMIN'" @click="router.push('/points-admin')">进入积分管理</el-button>
        <small v-if="role !== 'ADMIN'">当前账号不是管理员，仅可查看个人积分。</small>
      </el-card>
    </section>

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
            <p>成果包用于整体交付，过程文档和检测报告可单独查看与下载。</p>
          </div>
          <el-button :loading="loading" @click="refreshDocuments">刷新</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="documents" empty-text="暂无生成文档">
        <el-table-column prop="fileName" label="文档名称" min-width="280" show-overflow-tooltip />
        <el-table-column prop="projectName" label="项目名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ fileTypeName(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生成时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.viewable" text type="primary" :disabled="row.status !== 'SUCCESS'" @click="viewUrl(row.downloadUrl)">查看</el-button>
            <el-button text type="success" :disabled="row.status !== 'SUCCESS'" @click="downloadUrl(row.downloadUrl, row.fileName)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="document-pagination">
        <el-button :disabled="page.pageNum <= 1 || loading" @click="goPage(page.pageNum - 1)">上一页</el-button>
        <span>第 {{ page.pageNum }} 页 / 共 {{ pageTotal }} 页</span>
        <el-button :disabled="page.pageNum >= pageTotal || loading" @click="goPage(page.pageNum + 1)">下一页</el-button>
      </div>
    </el-card>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import HomeHero3D from '../../components/HomeHero3D.vue'
import { downloadArtifact, getMyDocuments, getPointAccount, logout } from '../../api/rewrite'

const router = useRouter()
const username = sessionStorage.getItem('dropai_username') || '当前账号'
const role = sessionStorage.getItem('dropai_role') || 'USER'
const documents = ref([])
const page = ref({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const pointsLoading = ref(false)
const pointAccount = ref({ points: null, totalPoints: null, usedPoints: null, recentTransactions: [] })
const heroProject = {
  projectTitle: '重力沉降室详细设计展示',
  equipmentName: '重力沉降室',
  designType: '环保设备结构设计',
  totalLength: 4200,
  totalWidth: 2000,
  totalHeight: 3200
}

const pageTotal = computed(() => Math.max(1, Math.ceil((page.value.total || 0) / page.value.pageSize)))

async function loadDocuments() {
  loading.value = true
  try {
    const result = await getMyDocuments({ pageNum: page.value.pageNum, pageSize: page.value.pageSize })
    documents.value = result?.list || []
    page.value.total = result?.total || 0
    page.value.pageNum = result?.pageNum || page.value.pageNum
    page.value.pageSize = result?.pageSize || page.value.pageSize
  } finally { loading.value = false }
}
function refreshDocuments() {
  page.value.pageNum = 1
  loadDocuments()
}
function goPage(pageNum) {
  page.value.pageNum = Math.max(1, pageNum)
  loadDocuments()
}
async function loadPoints() {
  pointsLoading.value = true
  try { pointAccount.value = await getPointAccount() || pointAccount.value } finally { pointsLoading.value = false }
}
async function downloadUrl(downloadUrl, fileName) {
  if (!downloadUrl) return
  const blob = await downloadArtifact(downloadUrl)
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = fileName || 'download'
  link.click()
  URL.revokeObjectURL(objectUrl)
}
async function viewUrl(url, fileName) {
  if (!url) return
  const blob = await downloadArtifact(url)
  const viewBlob = blob.type === 'application/pdf' ? blob : new Blob([blob], { type: 'application/pdf' })
  const objectUrl = URL.createObjectURL(viewBlob)
  window.open(objectUrl, '_blank', 'noopener')
  setTimeout(() => URL.revokeObjectURL(objectUrl), 60000)
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
function formatTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '--' }
function fileTypeName(row) {
  if (row.packageUrl || row.fileType === 'zip') return '成果包'
  if (row.fileType === 'pdf') return '报告'
  if (row.fileType === 'docx') return '文档'
  return row.fileType || '文件'
}
onMounted(() => {
  loadDocuments()
  loadPoints()
})
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
.points-panel{display:grid;grid-template-columns:1.4fr .8fr;gap:18px;margin:24px 0}.points-card{border-radius:18px}.points-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-bottom:16px}.points-summary div{padding:14px;border-radius:16px;background:#f8fafc;border:1px solid #e2e8f0}.points-summary span{display:block;color:#64748b;font-size:13px}.points-summary strong{display:block;margin-top:6px;font-size:28px;color:#2563eb}.admin-card p{color:#64748b;line-height:1.7}.admin-card small{display:block;margin-top:12px;color:#94a3b8}
.feature-grid div{padding:18px;border-radius:18px;background:#f8fafc;border:1px solid #e2e8f0}
.feature-grid strong{display:block;margin-bottom:8px;color:#172033}
.document-center{border-radius:18px}.section-head h2{font-size:22px}
.document-pagination{display:flex;align-items:center;justify-content:center;gap:16px;margin-top:18px;color:#64748b}
@media(max-width:980px){.quick-actions,.feature-grid,.points-panel{grid-template-columns:1fr}.dashboard-header{align-items:center}h1{font-size:30px}}
@media(max-width:760px){.document-pagination{gap:10px}}
</style>
