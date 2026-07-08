<template>
  <main class="page-shell home-page">
    <nav class="top-nav">
      <button class="brand" type="button" @click="router.push('/')">
        <span class="brand-mark">D</span>
        <span>DropAI</span>
      </button>
      <div class="nav-links">
        <button type="button" @click="router.push('/rewrite')">文档优化</button>
        <button type="button" @click="router.push('/new-project')">机械设计</button>
        <button type="button" @click="router.push('/computer-generator')">工程生成</button>
        <button type="button" @click="router.push('/dashboard')">工作台</button>
        <button class="primary-button" type="button" @click="router.push('/rewrite')">开始优化</button>
      </div>
    </nav>

    <section class="hero-section">
      <div class="hero-copy-block">
        <span class="eyebrow">AI Academic Workspace</span>
        <h1 class="hero-title">AI 学术写作助手</h1>
        <p class="hero-copy">智能优化论文表达，降低重复与AI痕迹。上传文档、选择优化方式、查看结果并下载，流程保持清晰可控。</p>
        <div class="hero-actions">
          <button class="primary-button" type="button" @click="router.push('/rewrite')">开始优化</button>
          <button class="ghost-button" type="button" @click="router.push('/computer-generator')">生成工程项目</button>
        </div>
      </div>

      <aside class="hero-orb panel">
        <div class="orb-head">
          <span class="status-pill"><span class="status-dot"></span>模型已连接</span>
          <span class="tiny">Live workflow</span>
        </div>
        <div class="orb-center">
          <strong>DropAI</strong>
          <span>Upload → Optimize → Download</span>
        </div>
        <div class="metric-grid">
          <div><b>DOCX</b><span>文档优化</span></div>
          <div><b>ZIP</b><span>成果包</span></div>
          <div><b>PDF</b><span>检测报告</span></div>
        </div>
      </aside>
    </section>

    <section class="mode-grid">
      <article v-for="mode in productModes" :key="mode.title" class="product-card mode-card" @click="router.push('/rewrite')">
        <span>{{ mode.kicker }}</span>
        <h3>{{ mode.title }}</h3>
        <p>{{ mode.copy }}</p>
      </article>
    </section>

    <section class="workspace-panel panel">
      <div class="workspace-input">
        <div class="section-head">
          <span class="eyebrow">Input</span>
          <h2>上传文档或输入文本</h2>
          <p>首页保留轻量入口，正式处理会进入文档优化页，继续使用已有上传、预检、积分和下载流程。</p>
        </div>
        <button class="drop-zone home-drop" type="button" @click="router.push('/rewrite')">
          <strong>上传 DOCX 文档</strong>
          <span>进入文档优化页后选择文件，系统会先检测字数与积分。</span>
        </button>
        <textarea class="text-input" readonly placeholder="粘贴论文段落，进入文档优化页后开始文本优化..." @focus="router.push('/rewrite')"></textarea>
      </div>

      <div class="workspace-output">
        <div class="section-head">
          <span class="eyebrow">Output</span>
          <h2>优化结果</h2>
          <p>结果会在处理完成后生成 Word 文档，可在历史记录或任务页下载。</p>
        </div>
        <div class="result-preview">
          <div class="preview-line long"></div>
          <div class="preview-line"></div>
          <div class="preview-line short"></div>
          <button class="primary-button" type="button" @click="router.push('/rewrite')">打开优化工作台</button>
        </div>
      </div>
    </section>

    <section class="module-grid">
      <article class="product-card module-card" @click="router.push('/new-project')">
        <span>Mechanical Design</span>
        <h3>机械设计模块</h3>
        <p>任务书上传、模型生成、CAD、3D展示和论文生成入口保持可用。</p>
      </article>
      <article class="product-card module-card" @click="router.push('/computer-generator')">
        <span>Computer Project</span>
        <h3>工程生成</h3>
        <p>上传任务书和开题报告，自动分析并生成项目代码、SQL 和文档。</p>
      </article>
      <article class="product-card module-card" @click="router.push('/dashboard')">
        <span>Workspace</span>
        <h3>我的文档</h3>
        <p>查看最近处理、成果包、积分余额和管理员入口。</p>
      </article>
    </section>

    <section class="history-section panel">
      <div class="history-head">
        <div>
          <span class="eyebrow">History</span>
          <h2>最近记录</h2>
        </div>
        <button class="ghost-button" type="button" :disabled="historyLoading" @click="loadHistory">刷新</button>
      </div>
      <div v-if="historyLoading" class="history-empty">正在加载记录...</div>
      <div v-else-if="pagedHistory.length" class="history-grid">
        <article v-for="doc in pagedHistory" :key="doc.id || doc.jobId || doc.fileName" class="history-card">
          <strong>{{ doc.fileName || doc.projectName || 'DropAI 文档' }}</strong>
          <span>{{ fileTypeName(doc) }} · {{ statusText(doc.status) }}</span>
          <small>{{ formatTime(doc.createTime || doc.createdAt || doc.updatedAt) }}</small>
          <button class="ghost-button" type="button" :disabled="doc.status !== 'SUCCESS'" @click="download(doc)">下载</button>
        </article>
      </div>
      <div v-else class="history-empty">登录后会显示最近生成和优化记录。</div>
      <div v-if="historyTotalPages > 1" class="pagination">
        <button class="ghost-button" type="button" :disabled="historyPage <= 1" @click="historyPage -= 1">上一页</button>
        <span>第 {{ historyPage }} / {{ historyTotalPages }} 页</span>
        <button class="ghost-button" type="button" :disabled="historyPage >= historyTotalPages" @click="historyPage += 1">下一页</button>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { downloadArtifact, getMyDocuments } from '../../api/rewrite'

const router = useRouter()
const historyLoading = ref(false)
const history = ref([])
const historyPage = ref(1)
const pageSize = 10

const productModes = [
  { kicker: 'Standard', title: '标准优化', copy: '适合常规论文表达整理，保持结构和事实稳定。' },
  { kicker: 'AI Trace', title: 'AI痕迹优化', copy: '减少模板化表达、机械连接和过度总结。' },
  { kicker: 'Deep', title: '深度优化', copy: '适合重复与AI痕迹都偏高的文档。' }
]

const historyTotalPages = computed(() => Math.max(1, Math.ceil(history.value.length / pageSize)))
const pagedHistory = computed(() => {
  const start = (historyPage.value - 1) * pageSize
  return history.value.slice(start, start + pageSize)
})

async function loadHistory() {
  if (!sessionStorage.getItem('dropai_token')) {
    history.value = []
    return
  }
  historyLoading.value = true
  try {
    const result = await getMyDocuments({ pageNum: 1, pageSize })
    history.value = result?.list || []
  } catch {
    history.value = []
  } finally {
    historyLoading.value = false
  }
}

async function download(doc) {
  const url = doc.downloadUrl || doc.packageUrl
  if (!url) return
  try {
    const blob = await downloadArtifact(url)
    const objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = doc.fileName || 'dropai-result'
    link.click()
    URL.revokeObjectURL(objectUrl)
  } catch (error) {
    ElMessage.error(error.message || '下载失败')
  }
}

function statusText(status) {
  return ({ SUCCESS: '已完成', FAILED: '失败', RUNNING: '处理中', PENDING: '排队中' })[status] || status || '已完成'
}

function fileTypeName(row) {
  if (row.packageUrl || row.fileType === 'zip') return '成果包'
  if (row.fileType === 'pdf') return '报告'
  if (row.fileType === 'docx') return '文档'
  return row.fileType || '文件'
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '--'
}

onMounted(loadHistory)
</script>

<style scoped>
.home-page {
  width: min(1240px, calc(100% - 40px));
}

.hero-section {
  display: grid;
  grid-template-columns: minmax(0, 1.06fr) minmax(360px, 0.94fr);
  gap: 24px;
  align-items: stretch;
  margin: 28px 0 18px;
}

.hero-copy-block {
  display: grid;
  align-content: center;
  min-height: 470px;
}

.hero-actions,
.history-head {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.hero-orb {
  position: relative;
  display: grid;
  align-content: space-between;
  min-height: 470px;
  padding: 24px;
  overflow: hidden;
}

.hero-orb::before {
  content: "";
  position: absolute;
  inset: 56px;
  border-radius: 999px;
  background: radial-gradient(circle, rgba(255,126,179,0.34), rgba(108,99,255,0.12) 48%, transparent 70%);
  filter: blur(8px);
}

.orb-head,
.metric-grid {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.orb-center {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  gap: 8px;
  min-height: 210px;
  text-align: center;
}

.orb-center strong {
  font-size: clamp(42px, 7vw, 74px);
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  color: transparent;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.metric-grid div {
  display: grid;
  gap: 4px;
  padding: 14px;
  border: 1px solid rgba(108,99,255,0.12);
  border-radius: 8px;
  background: rgba(255,255,255,0.56);
}

.metric-grid b {
  color: var(--primary);
}

.metric-grid span,
.mode-card p,
.module-card p,
.section-head p,
.history-card span,
.history-card small {
  color: var(--muted);
}

.mode-grid,
.module-grid,
.history-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.mode-card,
.module-card {
  padding: 20px;
  cursor: pointer;
}

.mode-card span,
.module-card span {
  color: var(--primary);
  font-size: 12px;
  font-weight: 800;
}

.mode-card h3,
.module-card h3 {
  margin: 10px 0 8px;
  font-size: 22px;
}

.workspace-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 18px;
  margin-top: 18px;
  padding: 20px;
}

.section-head h2,
.history-head h2 {
  margin: 0 0 8px;
  font-size: 28px;
}

.home-drop {
  width: 100%;
  text-align: left;
}

.workspace-input,
.workspace-output {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.result-preview {
  display: grid;
  align-content: center;
  gap: 16px;
  min-height: 312px;
  padding: 22px;
  border: 1px solid rgba(108,99,255,0.1);
  border-radius: 8px;
  background: rgba(255,255,255,0.56);
}

.preview-line {
  height: 12px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(108,99,255,0.18), rgba(255,126,179,0.24));
}

.preview-line.long {
  width: 92%;
}

.preview-line.short {
  width: 54%;
}

.history-section {
  margin-top: 18px;
  padding: 20px;
}

.history-head {
  justify-content: space-between;
  margin-bottom: 14px;
}

.history-card {
  display: grid;
  gap: 8px;
  padding: 16px;
  border: 1px solid rgba(108,99,255,0.1);
  border-radius: 8px;
  background: rgba(255,255,255,0.56);
}

.history-card strong {
  overflow-wrap: anywhere;
}

.history-empty {
  display: grid;
  place-items: center;
  min-height: 160px;
  color: var(--muted);
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
  color: var(--muted);
}

@media (max-width: 980px) {
  .hero-section,
  .workspace-panel,
  .mode-grid,
  .module-grid,
  .history-grid {
    grid-template-columns: 1fr;
  }

  .hero-copy-block,
  .hero-orb {
    min-height: auto;
  }
}
</style>
