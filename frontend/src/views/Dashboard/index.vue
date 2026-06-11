<template>
  <main class="dashboard-page">
    <header class="dashboard-header">
      <div>
        <span class="eyebrow">DROP AI WORKSPACE</span>
        <h1>你好，{{ username }}</h1>
        <p>从一个工作台进入不同创作功能，所有生成文档都会汇总到这里。</p>
      </div>
      <el-button text type="danger" @click="signOut">退出登录</el-button>
    </header>

    <section class="product-grid">
      <button class="product-card rewrite-product" type="button" @click="router.push('/rewrite')">
        <span class="product-label">现有功能</span>
        <strong>降重与降 AI</strong>
        <p>处理论文段落和 Word 文档，降低重复表达与机械写作痕迹。</p>
        <span class="product-action">进入工作区 →</span>
      </button>
      <button class="product-card new-product" type="button" @click="router.push('/new-project')">
        <span class="product-label">机械设计工作区</span>
        <strong>设计生成</strong>
        <p>根据设计参数与上传资料，生成设计说明、参数表、CAD 方案图和论文插图截图。</p>
        <span class="product-action">进入设计生成 →</span>
      </button>
    </section>

    <el-card class="document-center" shadow="never">
      <template #header>
        <div class="section-head">
          <div>
            <h2>我的文档</h2>
            <p>当前及未来所有功能生成的文档都将在这里统一展示。</p>
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
            <el-button text type="success" :disabled="row.status !== 'SUCCESS'" @click="download(row)">
              下载
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { downloadMyDocument, getMyDocuments, logout } from '../../api/rewrite'

const router = useRouter()
const username = localStorage.getItem('dropai_username') || '微信用户'
const documents = ref([])
const loading = ref(false)

async function loadDocuments() {
  loading.value = true
  try { documents.value = await getMyDocuments() || [] } finally { loading.value = false }
}
async function download(row) {
  const blob = await downloadMyDocument(row.jobId)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = row.fileName?.replace(/\.docx$/i, '') + '-生成结果.docx'
  link.click()
  URL.revokeObjectURL(url)
}
async function signOut() {
  try { await logout() } finally {
    localStorage.removeItem('dropai_token')
    localStorage.removeItem('dropai_username')
    router.replace('/login')
  }
}
function statusType(status) { return status === 'SUCCESS' ? 'success' : status === 'FAILED' ? 'danger' : 'warning' }
function statusText(status) { return ({ SUCCESS: '已完成', FAILED: '失败', RUNNING: '处理中', PENDING: '等待中' })[status] || status }
function featureName(feature) { return feature === 'REWRITE' ? '降重与降 AI' : feature === 'DESIGN_GENERATION' || feature === 'ENGINEERING_WRITING' ? '设计生成' : feature || '设计生成' }
onMounted(loadDocuments)
</script>

<style scoped>
.dashboard-page { max-width: 1280px; margin: 0 auto; padding: 38px 24px 64px; }
.dashboard-header,.section-head { display:flex; align-items:flex-start; justify-content:space-between; gap:24px; }
.eyebrow { color:#2563eb; font-size:12px; font-weight:800; letter-spacing:.15em; }
h1 { margin:8px 0 6px; font-size:34px; } h2 { margin:0 0 6px; }
.dashboard-header p,.section-head p,.product-card p { margin:0; color:#64748b; line-height:1.7; }
.product-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:20px; margin:32px 0; }
.product-card { min-height:230px; padding:28px; border:1px solid #dbe5f4; border-radius:18px; text-align:left; cursor:pointer; transition:.2s; }
.product-card:hover { transform:translateY(-3px); box-shadow:0 16px 40px rgba(15,23,42,.1); }
.rewrite-product { background:linear-gradient(145deg,#eff6ff,#fff); }
.new-product { background:linear-gradient(145deg,#faf5ff,#fff); border-color:#eadcff; }
.product-card strong { display:block; margin:14px 0 10px; font-size:25px; color:#172033; }
.product-label { font-size:12px; font-weight:700; color:#64748b; }.product-action { display:block; margin-top:28px; color:#2563eb; font-weight:700; }
.document-center { border-radius:18px; }.section-head h2 { font-size:22px; }
@media(max-width:760px){.product-grid{grid-template-columns:1fr}.dashboard-header{align-items:center}h1{font-size:28px}}
</style>
