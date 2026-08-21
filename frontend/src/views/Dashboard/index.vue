<template>
  <main class="workspace-shell">
    <aside class="workspace-sidebar">
      <button class="brand" type="button" @click="router.push('/')">
        <span>D</span><b>Dokiai Academic</b><small>Research Workspace</small>
      </button>

      <nav class="workspace-nav">
        <small>工作空间</small>
        <button class="active" type="button"><i>⌂</i>工作台</button>
        <button type="button" @click="router.push('/projects')"><i>□</i>我的项目</button>
        <button type="button" @click="router.push('/projects')"><i>📁</i>历史项目</button>
        <button type="button" @click="router.push('/points')"><i>✦</i>积分中心</button>
        <button type="button" @click="router.push('/account')"><i>◌</i>账户中心</button>
      </nav>

      <nav class="creation-nav">
        <small>创作工具</small>
        <button type="button" @click="router.push('/writing-generator')"><i>✦</i>文档创作</button>
        <button type="button" @click="router.push('/writing-generator')"><i>⌕</i>文献中心</button>
        <button type="button" @click="router.push('/rewrite')"><i>Aa</i>双降中心</button>
        <button type="button" @click="router.push('/ppt-generator')"><i>P</i>PPT生成</button>
        <button type="button" @click="router.push('/drawing')"><i>◇</i>智能绘图</button>
        <button type="button" @click="router.push('/mechanical-design')"><i>⚙</i>机械设计</button>
        <button type="button" @click="router.push('/computer-generator')"><i>&lt;/&gt;</i>AI 工程生成</button>
      </nav>

      <nav v-if="isAdmin" class="admin-nav">
        <small>管理工具</small>
        <button type="button" @click="router.push('/points-admin')"><i>⚒</i>管理控制台</button>
        <button type="button" @click="adminNoticeVisible = true"><i>i</i>系统公告</button>
      </nav>

      <div class="user-card-wrap">
        <button class="user-card" type="button" @click.stop="userMenuOpen = !userMenuOpen">
          <span>{{ username.slice(0, 1).toUpperCase() }}</span>
          <div>
            <strong>{{ username }}</strong>
            <small>{{ roleLabel }}</small>
          </div>
          <i :class="{ open: userMenuOpen }">⌃</i>
        </button>

        <section v-if="userMenuOpen" class="user-menu" @click.stop>
          <header>
            <strong>{{ username }}</strong>
            <small>{{ roleLabel }}</small>
          </header>
          <div class="menu-points">
            <span>积分余额</span>
            <b>{{ pointBalance }}</b>
          </div>
          <button type="button" @click="goAccount">个人中心</button>
          <button type="button" @click="goAccount">账号设置</button>
          <button type="button" @click="router.push('/points')">会员权益</button>
          <button type="button" @click="router.push('/points')">积分记录</button>
          <button class="danger" type="button" @click="signOut">退出登录</button>
        </section>
      </div>
    </aside>

    <section class="workspace-main">
      <header class="topbar">
        <div>
          <span>MY RESEARCH WORKSPACE</span>
          <h1>欢迎回来，{{ username }}</h1>
          <strong v-if="schoolName" class="school-name">{{ schoolName }}</strong>
          <p>继续正在进行的研究，或从一个新想法开始。</p>
        </div>
        <div class="top-actions">
          <button class="points-pill" type="button" @click="router.push('/points')">
            <small>积分余额</small>
            <strong>{{ pointBalance }}</strong>
            <em>+充值</em>
          </button>
        </div>
      </header>

      <section class="quick-start">
        <header>
          <span>START HERE</span>
          <h2>快速开始</h2>
          <p>选择一个方向，直接进入对应创作模块。</p>
        </header>
        <div class="start-grid">
          <button
            v-for="item in startCards"
            :key="item.title"
            class="start-card"
            type="button"
            @click="router.push(item.route)"
          >
            <b :class="item.tone">{{ item.icon }}</b>
            <span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.desc }}</small>
            </span>
            <i>→</i>
          </button>
        </div>
      </section>

      <section class="continue-panel">
        <header>
          <span>CONTINUE</span>
          <h2>继续工作</h2>
        </header>

        <article v-if="currentProject" class="continue-card">
          <div>
            <small>{{ projectType(currentProject) }}</small>
            <h3>{{ currentProject.projectName || currentProject.fileName || '未命名项目' }}</h3>
            <dl>
              <div>
                <dt>当前阶段</dt>
                <dd>{{ currentStep(currentProject) }}</dd>
              </div>
              <div>
                <dt>项目进度</dt>
                <dd>{{ projectProgress(currentProject) }}%</dd>
              </div>
            </dl>
          </div>
          <div class="task-status">
            <ul>
              <li v-for="step in taskSteps(currentProject)" :key="step.label" :class="step.state">
                <b>{{ step.mark }}</b>
                <span>{{ step.label }}</span>
              </li>
            </ul>
            <small v-if="isGenerating(currentProject)">预计剩余：约 60 秒</small>
          </div>
          <button type="button" @click="continueProject(currentProject)">继续 →</button>
        </article>

        <article v-else class="empty-work">
          <b>✦</b>
          <strong>还没有正在进行的创作</strong>
          <span>从论文、机械设计、PPT 或智能绘图开始，下一步会出现在这里。</span>
          <button type="button" @click="router.push('/writing-generator')">创建论文项目</button>
        </article>
      </section>

      <section id="my-projects" class="recent-panel">
        <header>
          <div>
            <span>RECENT PROJECTS</span>
            <h2>最近项目</h2>
          </div>
          <div>
            <button type="button" :disabled="loading" @click="refreshDocuments">刷新</button>
            <button type="button" @click="router.push('/projects')">查看全部 →</button>
          </div>
        </header>

        <div v-if="recentProjects.length" class="recent-list">
          <article v-for="project in recentProjects" :key="project.id || project.fileName">
            <b>{{ projectType(project).slice(0, 1) }}</b>
            <span>
              <strong>{{ project.projectName || project.fileName || '未命名项目' }}</strong>
              <small>{{ projectType(project) }} · {{ currentStep(project) }}</small>
            </span>
            <button type="button" @click="continueProject(project)">继续 →</button>
          </article>
        </div>

        <div v-else class="empty-recent">
          <span>暂无最近项目，创建后的项目会显示在这里。</span>
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
import { getMyDocuments, getPointAccount, logout } from '../../api/rewrite'

const router = useRouter()
const username = sessionStorage.getItem('dropai_username') || '当前用户'
const role = sessionStorage.getItem('dropai_role') || 'USER'
const schoolName = sessionStorage.getItem('dropai_school_name') || ''
const documents = ref([])
const loading = ref(false)
const pointsLoading = ref(false)
const adminNoticeVisible = ref(false)
const userMenuOpen = ref(false)
const pointAccount = ref({ points: null, totalPoints: null, usedPoints: null })

const startCards = [
  { title: '论文创作', desc: '开题、文献、大纲、正文与导出', icon: '文', tone: 'violet', route: '/writing-generator' },
  { title: '机械设计', desc: '方案、说明书、图纸与成果展示', icon: '⚙', tone: 'blue', route: '/mechanical-design' },
  { title: 'PPT生成', desc: '答辩与汇报展示材料', icon: 'P', tone: 'pink', route: '/ppt-generator' },
  { title: '智能绘图', desc: '流程图、UML、ER 图生成', icon: '◇', tone: 'green', route: '/drawing' }
]

const isAdmin = computed(() => String(role).toLowerCase() === 'admin')
const roleLabel = computed(() => (isAdmin.value ? '管理员' : '普通用户'))
const pointBalance = computed(() => pointAccount.value.points ?? '--')
const recentProjects = computed(() => documents.value.slice(0, 2))
const currentProject = computed(() => documents.value.find(x => !['SUCCESS', 'FAILED'].includes(x.status)) || documents.value[0] || null)

async function loadDocuments() {
  loading.value = true
  try {
    documents.value = (await getMyDocuments({ pageNum: 1, pageSize: 6 }))?.list || []
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

function goAccount() {
  userMenuOpen.value = false
  router.push('/account')
}

function continueProject(project) {
  const name = project.projectName || project.fileName || 'Dokiai 项目'
  router.push({ path: '/result', query: { name } })
}

async function signOut() {
  try {
    await logout()
  } finally {
    sessionStorage.removeItem('dropai_token')
    sessionStorage.removeItem('dropai_username')
    sessionStorage.removeItem('dropai_role')
    userMenuOpen.value = false
    router.replace('/login')
  }
}

function statusText(status) {
  return ({ SUCCESS: '已完成', FAILED: '失败', RUNNING: '生成中', GENERATING: '生成中', PENDING: '排队中', WAITING: '待继续' })[status] || status || '进行中'
}

function fileTypeName(record) {
  if (record.packageUrl || record.fileType === 'zip') return 'ZIP 成果包'
  if (record.fileType === 'pdf') return 'PDF'
  if (record.fileType === 'docx') return 'DOCX 文档'
  return record.fileType || '文件'
}

function projectType(project) {
  return project.documentType || project.projectType || fileTypeName(project) || '智能文档'
}

function projectProgress(project) {
  const value = Number(project.progress)
  if (Number.isFinite(value) && value > 0) return Math.min(100, value)
  return project.status === 'SUCCESS' ? 100 : project.status === 'FAILED' ? 0 : isGenerating(project) ? 65 : 20
}

function currentStep(project) {
  if (project.currentStep) return project.currentStep
  if (project.status === 'SUCCESS') return '成果已完成'
  if (isGenerating(project)) return '正文生成'
  if (project.status === 'FAILED') return '需要处理'
  return '项目准备'
}

function isGenerating(project) {
  return ['RUNNING', 'GENERATING', 'PENDING'].includes(String(project.status || '').toUpperCase())
}

function taskSteps(project) {
  if (!isGenerating(project)) {
    return [
      { label: '需求分析', state: 'done', mark: '✓' },
      { label: currentStep(project), state: project.status === 'SUCCESS' ? 'done' : 'active', mark: project.status === 'SUCCESS' ? '✓' : '●' },
      { label: '成果导出', state: project.status === 'SUCCESS' ? 'done' : 'waiting', mark: '待' }
    ]
  }
  return [
    { label: '需求分析', state: 'done', mark: '✓' },
    { label: '方案设计', state: 'active', mark: '●' },
    { label: '图纸生成', state: 'waiting', mark: '待' },
    { label: '三维模型', state: 'waiting', mark: '待' }
  ]
}

onMounted(() => {
  loadDocuments()
  loadPoints()
})
</script>

<style scoped>
.workspace-shell {
  --ink: #1b2437;
  --muted: #758096;
  display: grid;
  grid-template-columns: 245px minmax(0, 1fr);
  gap: 30px;
  width: min(1450px, calc(100% - 38px));
  margin: auto;
  padding: 20px 0 55px;
  color: var(--ink);
}

button { font: inherit; }

.workspace-sidebar {
  position: sticky;
  top: 20px;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 40px);
  padding: 18px 14px;
  border: 1px solid #e8e6f1;
  border-radius: 20px;
  background: #ffffffd9;
  box-shadow: 0 20px 60px #30395812;
  backdrop-filter: blur(20px);
}

.brand {
  display: grid;
  grid-template-columns: 40px 1fr;
  gap: 0 10px;
  align-items: center;
  padding: 0 5px 20px;
  border: 0;
  background: none;
  text-align: left;
}

.brand > span {
  grid-row: 1/3;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(145deg, #4198ff, #7658ef 60%, #df66b7);
  font-size: 20px;
  font-weight: 900;
}

.brand b { font-size: 15px; }
.brand small { color: #9aa2b3; font-size: 9px; }

.workspace-sidebar nav {
  display: grid;
  gap: 4px;
  padding: 13px 0;
  border-top: 1px solid #efedf5;
}

.workspace-sidebar nav > small {
  padding: 0 10px 8px;
  color: #a1a8b7;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: .14em;
  text-transform: uppercase;
}

.workspace-sidebar nav button {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px 10px;
  border: 0;
  border-radius: 10px;
  color: #667188;
  background: transparent;
  text-align: left;
}

.workspace-sidebar nav button:hover,
.workspace-sidebar nav .active {
  color: #5648ce;
  background: #f0edff;
}

.workspace-sidebar nav i {
  display: grid;
  place-items: center;
  width: 24px;
  font-style: normal;
  font-size: 12px;
}

.admin-nav {
  margin-top: 4px;
}

.user-card-wrap {
  position: relative;
  margin-top: auto;
}

.user-card {
  display: grid;
  grid-template-columns: 34px 1fr auto;
  gap: 9px;
  align-items: center;
  width: 100%;
  padding: 11px;
  border: 0;
  border-radius: 13px;
  background: #f6f4fb;
  text-align: left;
}

.user-card > span {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: #fff;
  background: #7764e5;
}

.user-card div { display: grid; }
.user-card small { color: #9099aa; font-size: 10px; }
.user-card i { color: #81899a; font-style: normal; transition: transform .2s ease; }
.user-card i.open { transform: rotate(180deg); }

.user-menu {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(100% + 10px);
  z-index: 5;
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px solid #e4e0f0;
  border-radius: 16px;
  background: #ffffffef;
  box-shadow: 0 18px 50px #2e2d5e24;
  backdrop-filter: blur(18px);
}

.user-menu header {
  display: grid;
  gap: 3px;
  padding: 4px 4px 10px;
  border-bottom: 1px solid #ece8f4;
}

.user-menu header small,
.menu-points span {
  color: #8d96a9;
  font-size: 11px;
}

.menu-points {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 6px 0;
  padding: 10px;
  border-radius: 12px;
  background: linear-gradient(135deg, #f4f0ff, #fff5fb);
}

.menu-points b {
  color: #6652dc;
  font-size: 20px;
}

.user-menu button {
  padding: 9px 10px;
  border: 0;
  border-radius: 10px;
  color: #49536a;
  background: transparent;
  text-align: left;
}

.user-menu button:hover {
  color: #5d4bd5;
  background: #f2efff;
}

.user-menu .danger {
  color: #d84d6a;
}

.workspace-main {
  position: relative;
  min-width: 0;
  isolation: isolate;
}

.workspace-main::before,
.workspace-main::after {
  content: '';
  position: absolute;
  pointer-events: none;
  z-index: -1;
}

.workspace-main::before {
  top: 10px;
  right: -22px;
  width: 520px;
  height: 280px;
  border-radius: 999px;
  background:
    radial-gradient(circle at 24% 42%, #7d66ff2e, transparent 38%),
    radial-gradient(circle at 72% 22%, #4a90ff24, transparent 35%),
    radial-gradient(circle at 68% 80%, #ff7bc321, transparent 42%);
  filter: blur(18px);
}

.workspace-main::after {
  top: 118px;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, #8d7cff42, transparent);
  box-shadow:
    0 100px 0 #ffffff42,
    0 236px 0 #ffffff38;
}

.topbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 25px;
  padding: 25px 5px 22px;
}

.topbar > div > span,
.quick-start header span,
.continue-panel header span,
.recent-panel header span {
  color: #6a59df;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: .15em;
}

.topbar h1 {
  margin: 9px 0 5px;
  font-size: 38px;
}

.topbar p {
  margin: 0;
  color: var(--muted);
}

.school-name {
  display: inline-block;
  margin-bottom: 6px;
  color: #6653d5;
}

.top-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  flex-shrink: 0;
}

.points-pill {
  display: grid;
  grid-template-columns: auto auto;
  gap: 2px 10px;
  align-items: center;
  min-width: 156px;
  padding: 12px 15px;
  border: 1px solid #d9d2ff;
  border-radius: 16px;
  color: #443c84;
  background:
    linear-gradient(135deg, #ffffffeb, #f7f3ffde),
    radial-gradient(circle at top right, #725bf026, transparent 45%);
  box-shadow: 0 18px 45px #5846c51a;
  text-align: left;
  cursor: pointer;
  transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;
}

.points-pill:hover {
  transform: translateY(-2px);
  border-color: #b8acff;
  box-shadow: 0 22px 55px #5846c529;
}

.points-pill small {
  color: #7a8397;
  font-size: 10px;
}

.points-pill strong {
  color: #634fff;
  font-size: 19px;
}

.points-pill em {
  grid-column: 2;
  color: #6d58de;
  font-size: 11px;
  font-style: normal;
}

.create-button,
.continue-card > button,
.empty-work button {
  padding: 12px 18px;
  border: 0;
  border-radius: 11px;
  color: #fff;
  background: linear-gradient(115deg, #6259eb, #a15ddb);
  box-shadow: 0 13px 28px #604ed62b;
}

.quick-start,
.continue-panel,
.recent-panel {
  margin-top: 18px;
  padding: 24px;
  border: 1px solid #e5e1f0;
  border-radius: 24px;
  background: #ffffffbd;
  box-shadow: 0 22px 60px #443d8a12;
}

.quick-start {
  position: relative;
  overflow: hidden;
  background:
    linear-gradient(135deg, #ffffffef, #fbf9ffee 72%, #f4efffee),
    radial-gradient(circle at 96% 0%, #7360ee18, transparent 42%);
}

.quick-start::after {
  content: '';
  position: absolute;
  right: 28px;
  top: 22px;
  width: 130px;
  height: 130px;
  border-radius: 42px;
  background:
    linear-gradient(135deg, #785fff17, transparent),
    repeating-linear-gradient(135deg, #6c5ce70f 0 1px, transparent 1px 11px);
  transform: rotate(12deg);
  pointer-events: none;
}

.quick-start header,
.continue-panel header,
.recent-panel header {
  margin-bottom: 16px;
}

.quick-start h2,
.continue-panel h2,
.recent-panel h2 {
  margin: 8px 0 6px;
  font-size: 27px;
}

.quick-start p {
  margin: 0;
  color: var(--muted);
}

.start-grid {
  position: relative;
  z-index: 1;
  display: grid !important;
  grid-template-columns: repeat(4, 1fr);
  gap: 13px;
}

.start-card {
  appearance: none;
  display: grid !important;
  grid-template-columns: 54px 1fr auto;
  gap: 13px;
  align-items: center;
  min-height: 112px;
  padding: 17px;
  border: 1px solid #e5e2ef;
  border-radius: 18px;
  background: linear-gradient(145deg, #fff, #fbfaff);
  text-align: left;
  color: var(--ink);
  cursor: pointer;
  box-shadow: inset 0 1px 0 #ffffff, 0 12px 30px #574d9d0d;
  transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;
}

.start-card:hover {
  transform: translateY(-4px);
  border-color: #cfc6ff;
  box-shadow: 0 18px 44px #4e40b519;
}

.start-card b {
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  border-radius: 15px;
  font-size: 20px;
}

.start-card .violet { color: #684fd9; background: #eee9ff; }
.start-card .blue { color: #2d78ce; background: #e6f2ff; }
.start-card .pink { color: #c34e8b; background: #ffe8f3; }
.start-card .green { color: #278b70; background: #e3f7f0; }
.start-card span { display: grid; gap: 5px; }
.start-card small { color: #8c96aa; font-size: 12px; line-height: 1.45; }
.start-card i { color: #9aa1b1; font-style: normal; }

.continue-card {
  position: relative;
  overflow: hidden;
  display: grid !important;
  grid-template-columns: minmax(0, 1fr) 310px auto;
  gap: 22px;
  align-items: center;
  padding: 20px;
  border: 1px solid #ded9ef;
  border-radius: 19px;
  background: linear-gradient(135deg, #fff, #f8f5ff 65%, #fff7fb);
}

.continue-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 92% 12%, #705bf320, transparent 28%),
    linear-gradient(90deg, transparent, #ffffff66, transparent);
  pointer-events: none;
}

.continue-card > * {
  position: relative;
  z-index: 1;
}

.continue-card small {
  color: #6a59df;
  font-weight: 700;
}

.continue-card h3 {
  margin: 9px 0 14px;
  font-size: 22px;
}

.continue-card dl {
  display: flex;
  gap: 28px;
  margin: 0;
}

.continue-card dl div {
  display: grid;
  gap: 4px;
}

.continue-card dt {
  color: #8993a8;
  font-size: 12px;
}

.continue-card dd {
  margin: 0;
  font-weight: 800;
}

.task-status {
  display: grid;
  gap: 8px;
  padding: 13px;
  border: 1px solid #e8e4f2;
  border-radius: 15px;
  background: #ffffffa8;
}

.task-status ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.task-status li {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8a93a5;
  font-size: 12px;
}

.task-status li b {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  font-size: 10px;
}

.task-status li.done b {
  color: #fff;
  background: #5ec09d;
}

.task-status li.active {
  color: #5f4ed6;
  font-weight: 800;
}

.task-status li.active b {
  color: #fff;
  background: #6f5ce6;
  box-shadow: 0 0 0 5px #6f5ce61c;
}

.task-status li.waiting b {
  color: #8c95aa;
  background: #f0eef6;
}

.task-status > small {
  color: #6f5ce6;
}

.empty-work {
  display: grid;
  place-items: center;
  gap: 8px;
  min-height: 190px;
  border: 1px dashed #dcd8ea;
  border-radius: 18px;
  color: var(--muted);
  background: #ffffff78;
}

.empty-work b {
  font-size: 30px;
}

.recent-panel > header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

.recent-panel > header button {
  border: 0;
  color: #6655d6;
  background: none;
}

.recent-list {
  display: grid;
  gap: 10px;
}

.recent-list article {
  display: grid !important;
  grid-template-columns: 46px 1fr auto;
  gap: 13px;
  align-items: center;
  padding: 14px;
  border: 1px solid #ebe8f2;
  border-radius: 15px;
  background: #ffffffc4;
}

.recent-list article > b {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(145deg, #5f8deb, #845fdc);
}

.recent-list article span {
  display: grid;
  gap: 4px;
}

.recent-list article small {
  color: #919bad;
}

.recent-list article button {
  border: 0;
  color: #6253ce;
  background: none;
}

.empty-recent {
  padding: 22px;
  border: 1px dashed #dcd8ea;
  border-radius: 15px;
  color: var(--muted);
  text-align: center;
}

@media (max-width: 1180px) {
  .workspace-shell { grid-template-columns: 1fr; }
  .workspace-sidebar { position: static; height: auto; }
  .workspace-sidebar nav { grid-template-columns: repeat(3, 1fr); }
  .workspace-sidebar nav > small { grid-column: 1 / -1; }
  .user-card-wrap { margin-top: 10px; }
  .user-menu { position: static; margin-top: 10px; }
  .start-grid { grid-template-columns: repeat(2, 1fr); }
  .continue-card { grid-template-columns: 1fr; }
}

@media (max-width: 720px) {
  .workspace-shell { width: min(100% - 24px, 1450px); }
  .topbar { align-items: flex-start; flex-direction: column; }
  .top-actions { width: 100%; flex-direction: column; align-items: stretch; }
  .points-pill { grid-template-columns: 1fr auto; }
  .workspace-sidebar nav,
  .start-grid { grid-template-columns: 1fr; }
  .recent-panel > header { align-items: flex-start; flex-direction: column; gap: 10px; }
  .recent-list article { grid-template-columns: 44px 1fr; }
  .recent-list article button { grid-column: 1 / -1; justify-self: start; }
}
</style>
