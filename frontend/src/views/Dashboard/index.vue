<template>
  <main class="dashboard-shell">
    <aside class="sidebar panel">
      <button class="brand" type="button" @click="router.push('/')">
        <span class="brand-mark">D</span>
        <span>DropAI</span>
      </button>
      <nav>
        <button class="active" type="button">Overview</button>
        <button type="button" @click="router.push('/new-project')">Project Builder</button>
        <button type="button" @click="router.push('/rewrite')">Writing</button>
        <button type="button" @click="router.push('/recharge')">Credits</button>
        <button v-if="role === 'ADMIN'" type="button" @click="adminNoticeVisible = true">Notice</button>
      </nav>
      <button class="ghost-button signout" type="button" @click="signOut">Sign out</button>
    </aside>

    <section class="dashboard-main">
      <header class="dashboard-head">
        <div>
          <span class="eyebrow">Dashboard</span>
          <h1>Welcome back, {{ username }}</h1>
          <p>Recent projects, credits, and downloads in one quiet workspace.</p>
        </div>
        <button class="primary-button" type="button" @click="router.push('/new-project')">Generate Project</button>
      </header>

      <section class="overview-grid">
        <article class="product-card credit-card">
          <span class="status-pill"><span class="status-dot"></span>Credits</span>
          <strong>{{ pointAccount.points ?? '--' }}</strong>
          <p>Total {{ pointAccount.totalPoints ?? '--' }} · Used {{ pointAccount.usedPoints ?? '--' }}</p>
          <button class="ghost-button" type="button" :disabled="pointsLoading" @click="loadPoints">Refresh</button>
        </article>

        <article class="product-card recent-card">
          <div class="card-head">
            <span class="status-pill">Recent Projects</span>
            <button class="ghost-button" type="button" :disabled="loading" @click="refreshDocuments">Refresh</button>
          </div>
          <div v-if="recentProjects.length" class="project-list">
            <button v-for="project in recentProjects" :key="project.id || project.fileName" type="button" @click="openResult(project)">
              <span>{{ project.projectName || project.fileName || 'Engineering package' }}</span>
              <small>{{ formatTime(project.createTime) }}</small>
            </button>
          </div>
          <p v-else class="empty">No generated projects yet.</p>
        </article>
      </section>

      <section class="download-panel panel">
        <div class="card-head">
          <div>
            <h2>Download Record</h2>
            <p>Only the latest deliverables are shown to keep the workspace focused.</p>
          </div>
        </div>
        <div class="download-list">
          <div v-for="doc in documents.slice(0, 6)" :key="doc.id || doc.fileName" class="download-row">
            <div>
              <strong>{{ doc.fileName || 'Generated artifact' }}</strong>
              <span>{{ fileTypeName(doc) }} · {{ statusText(doc.status) }}</span>
            </div>
            <button class="ghost-button" type="button" :disabled="doc.status !== 'SUCCESS'" @click="downloadUrl(doc.downloadUrl, doc.fileName)">Download</button>
          </div>
          <p v-if="!documents.length" class="empty">Generated downloads will appear here.</p>
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
const username = sessionStorage.getItem('dropai_username') || 'Engineer'
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
  router.push({ path: '/result', query: { name: project.projectName || project.fileName || 'DropAI Project' } })
}

async function downloadUrl(downloadUrl, fileName) {
  if (!downloadUrl) return
  const blob = await downloadArtifact(downloadUrl)
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = fileName || 'dropai-artifact'
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
  return ({ SUCCESS: 'Ready', FAILED: 'Failed', RUNNING: 'Processing', PENDING: 'Queued' })[status] || status || 'Ready'
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '--'
}

function fileTypeName(row) {
  if (row.packageUrl || row.fileType === 'zip') return 'ZIP package'
  if (row.fileType === 'pdf') return 'PDF'
  if (row.fileType === 'docx') return 'Document'
  return row.fileType || 'Artifact'
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
  margin-bottom: 10px;
  font-size: clamp(34px, 5vw, 56px);
  line-height: 1;
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
  font-size: 64px;
  line-height: 1;
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
