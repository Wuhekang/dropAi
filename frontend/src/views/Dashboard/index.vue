<template>
  <main class="dashboard-shell">
    <aside class="sidebar panel">
      <button class="brand" type="button" @click="router.push('/')">
        <span class="brand-mark">D</span>
        <span>DropAI</span>
      </button>
      <nav>
        <button class="active" type="button">总览</button>
        <button type="button" @click="router.push('/new-project')">项目生成</button>
        <button type="button" @click="router.push('/rewrite')">论文优化</button>
        <button type="button" @click="router.push('/recharge')">积分</button>
        <button v-if="role === 'ADMIN'" type="button" @click="adminNoticeVisible = true">公告</button>
      </nav>
      <button class="ghost-button signout" type="button" @click="signOut">退出登录</button>
    </aside>

    <section class="dashboard-main">
      <header class="dashboard-head">
        <div>
          <span class="eyebrow">控制台</span>
          <h1>欢迎回来，{{ username }}</h1>
          <p>最近项目、积分和下载记录，都收在一个清爽的工作区里。</p>
        </div>
        <button class="primary-button" type="button" @click="router.push('/new-project')">生成项目</button>
      </header>

      <section class="overview-grid">
        <article class="product-card credit-card">
          <span class="status-pill"><span class="status-dot"></span>我的积分</span>
          <strong>{{ pointAccount.points ?? '--' }}</strong>
          <p>累计 {{ pointAccount.totalPoints ?? '--' }} · 已用 {{ pointAccount.usedPoints ?? '--' }}</p>
          <button class="ghost-button" type="button" :disabled="pointsLoading" @click="loadPoints">刷新</button>
        </article>

        <article class="product-card recent-card">
          <div class="card-head">
            <span class="status-pill">最近项目</span>
            <button class="ghost-button" type="button" :disabled="loading" @click="refreshDocuments">刷新</button>
          </div>
          <div v-if="recentProjects.length" class="project-list">
            <button v-for="project in recentProjects" :key="project.id || project.fileName" type="button" @click="openResult(project)">
              <span>{{ project.projectName || project.fileName || '工程成果包' }}</span>
              <small>{{ formatTime(project.createTime) }}</small>
            </button>
          </div>
          <p v-else class="empty">暂无生成项目。</p>
        </article>
      </section>

      <section class="download-panel panel">
        <div class="card-head">
          <div>
            <h2>下载记录</h2>
            <p>只展示最近的交付物，避免工作区变得拥挤。</p>
          </div>
        </div>
        <div class="download-list">
          <div v-for="doc in documents.slice(0, 6)" :key="doc.id || doc.fileName" class="download-row">
            <div>
              <strong>{{ doc.fileName || '生成文件' }}</strong>
              <span>{{ fileTypeName(doc) }} · {{ statusText(doc.status) }}</span>
            </div>
            <button class="ghost-button" type="button" :disabled="doc.status !== 'SUCCESS'" @click="downloadUrl(doc.downloadUrl, doc.fileName)">下载</button>
          </div>
          <p v-if="!documents.length" class="empty">生成后的下载文件会显示在这里。</p>
        </div>
      </section>
    </section>

    <admin-notice-modal v-model="adminNoticeVisible" />
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AdminNoticeModal from '../../components/AdminNoticeModal.vue'
import { downloadArtifact, getMyDocuments, getPointAccount, logout } from '../../api/rewrite'

const router = useRouter()
const username = sessionStorage.getItem('dropai_username') || '当前用户'
const role = sessionStorage.getItem('dropai_role') || 'USER'
const documents = ref([])
const loading = ref(false)
const pointsLoading = ref(false)
const adminNoticeVisible = ref(false)
const pointAccount = ref({ points: null, totalPoints: null, usedPoints: null })

const recentProjects = computed(() => documents.value.slice(0, 4))

async function loadDocuments() {
  loading.value = true
  try {
    const result = await getMyDocuments({ pageNum: 1, pageSize: 8 })
    documents.value = result?.list || []
  } finally {
    loading.value = false
  }
}

function refreshDocuments() {
  loadDocuments()
}

async function loadPoints() {
  pointsLoading.value = true
  try {
    pointAccount.value = await getPointAccount() || pointAccount.value
  } finally {
    pointsLoading.value = false
  }
}

function openResult(project) {
  router.push({ path: '/result', query: { name: project.projectName || project.fileName || 'DropAI 项目' } })
}

async function downloadUrl(downloadUrl, fileName) {
  if (!downloadUrl) return
  const blob = await downloadArtifact(downloadUrl)
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = fileName || 'dropai-成果文件'
  link.click()
  URL.revokeObjectURL(objectUrl)
}

async function signOut() {
  try {
    await logout()
  } finally {
    sessionStorage.removeItem('dropai_token')
    sessionStorage.removeItem('dropai_username')
    sessionStorage.removeItem('dropai_role')
    router.replace('/login')
  }
}

function statusText(status) {
  return ({ SUCCESS: '已完成', FAILED: '失败', RUNNING: '处理中', PENDING: '排队中' })[status] || status || '已完成'
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '--'
}

function fileTypeName(row) {
  if (row.packageUrl || row.fileType === 'zip') return 'ZIP 成果包'
  if (row.fileType === 'pdf') return 'PDF'
  if (row.fileType === 'docx') return '文档'
  return row.fileType || '文件'
}

onMounted(() => {
  loadDocuments()
  loadPoints()
})
</script>

<style scoped>
.dashboard-shell {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 22px;
  width: min(1220px, calc(100% - 40px));
  margin: 0 auto;
  padding: 22px 0 44px;
  animation: page-in 0.5s ease both;
}

.brand {
  border: 0;
  background: transparent;
  cursor: pointer;
}

.sidebar {
  position: sticky;
  top: 22px;
  display: grid;
  align-content: start;
  gap: 24px;
  height: calc(100vh - 44px);
  padding: 18px;
}

.sidebar nav {
  display: grid;
  gap: 6px;
}

.sidebar nav button {
  width: 100%;
  padding: 11px 12px;
  border: 1px solid transparent;
  border-radius: var(--radius);
  color: var(--muted);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: var(--ease);
}

.sidebar nav button:hover,
.sidebar nav .active {
  color: var(--text);
  border-color: var(--line);
  background: rgba(255, 255, 255, 0.07);
}

.signout {
  align-self: end;
}

.dashboard-main {
  min-width: 0;
}

.dashboard-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin: 22px 0 22px;
}

.dashboard-head h1 {
  max-width: 760px;
  margin-bottom: 10px;
  overflow-wrap: anywhere;
  font-size: clamp(30px, 4vw, 46px);
  line-height: 1.12;
}

.dashboard-head p,
.download-panel p {
  margin: 0;
  color: var(--muted);
  line-height: 1.65;
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.62fr) minmax(0, 1.38fr);
  gap: 16px;
}

.credit-card,
.recent-card,
.download-panel {
  padding: 18px;
}

.credit-card {
  display: grid;
  gap: 14px;
}

.credit-card strong {
  max-width: 100%;
  overflow: hidden;
  font-size: clamp(42px, 6vw, 58px);
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.credit-card p {
  margin: 0;
  color: var(--muted);
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.project-list {
  display: grid;
  gap: 10px;
}

.project-list button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 15px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  color: var(--text);
  background: rgba(255, 255, 255, 0.045);
  cursor: pointer;
  transition: var(--ease);
}

.project-list button:hover {
  transform: translateY(-2px);
  background: rgba(255, 255, 255, 0.075);
}

.project-list button span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-list button small {
  flex: 0 0 auto;
}

.project-list small,
.download-row span,
.empty {
  color: var(--muted);
}

.download-panel {
  margin-top: 16px;
}

.download-panel h2 {
  margin: 0 0 8px;
  font-size: 28px;
}

.download-row strong,
.download-row span {
  display: block;
}

.download-row strong {
  overflow-wrap: anywhere;
}

.download-row span {
  margin-top: 5px;
  font-size: 13px;
}

@media (max-width: 920px) {
  .dashboard-shell,
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .sidebar {
    position: static;
    height: auto;
  }

  .dashboard-head {
    flex-direction: column;
  }
}
</style>
