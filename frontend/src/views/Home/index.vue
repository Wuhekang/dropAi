<template>
  <main class="landing-page">
    <header class="site-header" :class="{ scrolled: navScrolled }">
      <nav class="nav-shell" aria-label="Dokiai Academic">
        <BrandLogo
          as="button"
          class="brand home-brand"
          size="md"
          title="Dokiai"
          subtitle="Academic"
          @click="scrollToSection('top')"
        />

        <div class="nav-links">
          <button
            v-for="item in navItems"
            :key="item.id"
            :class="{ active: item.id === activeSection }"
            type="button"
            @click="scrollToSection(item.id)"
          >
            {{ item.label }}
          </button>
        </div>

        <button class="nav-cta" type="button" @click="navigate('/dashboard')">进入工作台</button>
      </nav>
    </header>

    <section id="top" class="hero-section">
      <div class="hero-inner">
        <p class="eyebrow">学术工作台</p>
        <h1>论文、文献与工程文档<br />放在同一张工作台上</h1>
        <p class="hero-description">
          Dokiai 用于论文写作与学术优化，也处理文献、学校格式、机械说明、图纸和答辩材料。
        </p>
        <div class="hero-actions">
          <button class="secondary-button" type="button" @click="scrollToSection('features')">查看功能</button>
        </div>
        <ul class="value-list">
          <li v-for="item in valueTags" :key="item.title">
            <strong>{{ item.title }}</strong>
            <span>{{ item.copy }}</span>
          </li>
        </ul>
      </div>
    </section>

    <section id="features" class="section">
      <div class="section-heading">
        <h2>工作内容</h2>
        <p>常用写作与工程任务放在前面，其余工具按条目进入。</p>
      </div>

      <div class="primary-grid">
        <button
          v-for="card in primaryCards"
          :key="card.title"
          class="primary-card"
          type="button"
          @click="navigate(card.route)"
        >
          <span class="card-index">{{ card.index }}</span>
          <strong>{{ card.title }}</strong>
          <span>{{ card.copy }}</span>
          <em>进入</em>
        </button>
      </div>

      <div class="secondary-grid">
        <button
          v-for="card in secondaryCards"
          :key="card.title"
          type="button"
          @click="navigate(card.route)"
        >
          <strong>{{ card.title }}</strong>
          <span>{{ card.copy }}</span>
        </button>
      </div>
    </section>

    <section id="workflow" class="section">
      <div class="section-heading">
        <h2>工作流程</h2>
        <p>从题目或任务书开始，到文献、成稿和导出。</p>
      </div>
      <ol class="process-list">
        <li v-for="step in processSteps" :key="step.no">
          <span>{{ step.no }}</span>
          <div>
            <h3>{{ step.title }}</h3>
            <p>{{ step.copy }}</p>
          </div>
        </li>
      </ol>
    </section>

    <section id="deliverables" class="section">
      <div class="section-heading">
        <h2>可导出的文件</h2>
        <p>成稿后按任务下载对应格式。</p>
      </div>
      <ul class="file-list">
        <li v-for="item in deliverables" :key="item.title">
          <strong>{{ item.title }}</strong>
          <span>{{ item.copy }}</span>
        </li>
      </ul>
    </section>

    <section id="scenarios" class="section scenario-line">
      <h2>适用场景</h2>
      <p>
        <span v-for="(item, index) in scenarios" :key="item">
          {{ item }}<template v-if="index < scenarios.length - 1"> · </template>
        </span>
      </p>
    </section>

    <section class="section close-section">
      <div>
        <h2>预约一次演示</h2>
        <p>演示从登录页开始。已有账号可使用页首的「进入工作台」。</p>
      </div>
      <button class="primary-button" type="button" @click="navigate('/login')">预约演示</button>
    </section>

    <footer class="colophon">
      <div>
        <strong>Dokiai Academic</strong>
        <p>学术写作、文献整理与工程文档工作台。</p>
      </div>
      <nav aria-label="页内目录">
        <button v-for="item in navItems" :key="item.id" type="button" @click="scrollToSection(item.id)">
          {{ item.label }}
        </button>
      </nav>
    </footer>
  </main>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BrandLogo from '../../components/BrandLogo.vue'

const router = useRouter()
const navScrolled = ref(false)
const activeSection = ref('')
const sectionIds = ['features', 'workflow', 'deliverables', 'scenarios']

const navItems = [
  { id: 'features', label: '功能' },
  { id: 'workflow', label: '流程' },
  { id: 'deliverables', label: '成果' },
  { id: 'scenarios', label: '场景' }
]

const valueTags = [
  { title: '写作', copy: '理工、经管与艺术类论文' },
  { title: '文献', copy: '检索、引用格式与摘要整理' },
  { title: '工程', copy: '机械说明、图纸与答辩材料' }
]

const primaryCards = [
  { index: '01', title: '论文创作', copy: '从项目设置到正文，并导出 DOCX。', route: '/writing' },
  { index: '02', title: '学术优化', copy: '整理论文表达，处理重复与模板化写法。', route: '/rewrite' },
  { index: '03', title: '机械说明稿', copy: '完整正文与工程配图。300 积分 / 篇。', route: '/mechanical-design' }
]

const secondaryCards = [
  { title: '文献检索', copy: '按研究题目查找文献。', route: '/literature' },
  { title: '文献分析', copy: '整理引用格式与摘要信息。', route: '/literature' },
  { title: '论文格式智能修改', copy: '按学校模板整理标题、图表与正文。', route: '/word-formatter' },
  { title: '图纸智能处理', copy: '识别图纸并分析机械结构。', route: '/mechanical-design' },
  { title: '流程图生成', copy: '生成 UML、ER 与流程图。', route: '/drawing' },
  { title: 'PPT与答辩', copy: '生成展示材料。300 积分 / 篇。', route: '/ppt-generator' }
]

const processSteps = [
  { no: '01', title: '需求输入', copy: '上传题目或任务书，说明这次要完成的文档。' },
  { no: '02', title: '资料分析', copy: '检索并整理文献，抽出可用的题录与要点。' },
  { no: '03', title: '智能生成', copy: '按已确认的结构生成正文、图纸或展示材料。' },
  { no: '04', title: '成果输出', copy: '导出论文、图纸、PPT 或其他任务对应的文件。' }
]

const deliverables = [
  { title: 'DOCX', copy: '论文文档' },
  { title: 'PPT', copy: '演示文稿' },
  { title: 'DWG / CAD', copy: '工程图纸' },
  { title: 'PNG / JPG', copy: '图片文件' },
  { title: 'PDF', copy: '报告文件' },
  { title: 'XLSX', copy: '数据表格' }
]

const scenarios = ['毕业论文', '课程设计', '工程说明书', '机械制图', '系统流程图', '答辩汇报', '文献综述', '方案展示']

function scrollToSection(id) {
  activeSection.value = sectionIds.includes(id) ? id : ''
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function navigate(path) {
  router.push(path)
}

function updateNavState() {
  navScrolled.value = window.scrollY > 8
  const marker = window.scrollY + 96
  let current = ''
  for (const id of sectionIds) {
    const el = document.getElementById(id)
    if (el && el.offsetTop <= marker) current = id
  }
  activeSection.value = current
}

onMounted(() => {
  updateNavState()
  window.addEventListener('scroll', updateNavState, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', updateNavState)
})
</script>

<style scoped>
:global(html) {
  scroll-behavior: smooth;
}

:global(body) {
  margin: 0;
  background: #f4f6f8;
  color: #1c2430;
}

.landing-page {
  --ink: #1c2430;
  --muted: #5c6b7a;
  --line: #d9dee7;
  --navy: #1e3a5f;
  --paper: #f4f6f8;
  min-height: 100vh;
  color: var(--ink);
  background: var(--paper);
  font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Noto Sans SC", sans-serif;
}

.site-header {
  position: sticky;
  top: 0;
  z-index: 30;
  border-bottom: 1px solid transparent;
  background: rgba(244, 246, 248, 0.92);
}

.site-header.scrolled {
  border-bottom-color: var(--line);
  background: rgba(255, 255, 255, 0.96);
}

.nav-shell,
.hero-inner,
.section,
.colophon {
  width: min(1080px, calc(100% - 48px));
  margin: 0 auto;
}

.nav-shell {
  display: flex;
  align-items: center;
  gap: 28px;
  min-height: 68px;
}

.home-brand {
  padding: 0;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.nav-links button,
.colophon button,
.secondary-button,
.nav-cta,
.primary-button,
.primary-card,
.secondary-grid button {
  font: inherit;
  cursor: pointer;
}

.nav-links button,
.colophon button {
  padding: 8px 12px;
  border: 0;
  border-radius: 4px;
  color: var(--muted);
  background: transparent;
}

.nav-links button.active,
.nav-links button:hover,
.colophon button:hover {
  color: var(--navy);
}

.nav-links button.active {
  box-shadow: inset 0 -2px 0 var(--navy);
}

.nav-cta,
.secondary-button {
  min-height: 36px;
  padding: 0 14px;
  border: 1px solid #c5ceda;
  border-radius: 4px;
  color: var(--navy);
  background: #fff;
}

.nav-cta {
  flex: 0 0 auto;
}

.nav-cta:hover,
.secondary-button:hover,
.primary-button:hover,
.primary-card:hover,
.secondary-grid button:hover {
  border-color: var(--navy);
}

.hero-section,
.section {
  scroll-margin-top: 84px;
}

.hero-section {
  padding: 72px 0 36px;
}

.hero-inner {
  margin: 0 auto;
}

.eyebrow {
  margin: 0 0 18px;
  color: var(--navy);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.hero-inner h1,
.section-heading h2,
.scenario-line h2,
.close-section h2,
.process-list h3,
.primary-card strong {
  font-family: "Songti SC", "STSong", "Noto Serif SC", "Source Han Serif SC", "SimSun", serif;
  font-weight: 600;
}

.hero-inner h1 {
  max-width: 720px;
  margin: 0;
  font-size: clamp(36px, 4.4vw, 52px);
  line-height: 1.28;
  letter-spacing: 0;
}

.hero-description,
.section-heading p,
.close-section p,
.scenario-line p,
.colophon p {
  color: var(--muted);
  font-size: 16px;
  line-height: 1.75;
}

.hero-description {
  max-width: 640px;
  margin: 18px 0 0;
}

.hero-actions,
.close-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hero-actions {
  margin-top: 28px;
}

.primary-button {
  min-height: 42px;
  padding: 0 18px;
  border: 1px solid var(--navy);
  border-radius: 4px;
  color: #fff;
  background: var(--navy);
}

.primary-button:hover {
  background: #17314f;
}

.value-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin: 48px 0 0;
  padding: 22px 0 0;
  border-top: 1px solid var(--line);
  list-style: none;
}

.value-list li {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.value-list strong {
  color: var(--navy);
  font-size: 14px;
}

.value-list span,
.primary-card span,
.secondary-grid span,
.file-list span,
.process-list p {
  color: var(--muted);
  line-height: 1.65;
}

.section {
  padding: 56px 0 8px;
}

.section-heading {
  max-width: 640px;
  margin-bottom: 28px;
}

.section-heading h2,
.scenario-line h2,
.close-section h2 {
  margin: 0;
  font-size: 32px;
  line-height: 1.35;
}

.section-heading p {
  margin: 10px 0 0;
}

.primary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.primary-card {
  display: grid;
  align-content: start;
  gap: 10px;
  min-height: 210px;
  padding: 22px 22px 18px;
  border: 1px solid var(--line);
  border-radius: 6px;
  color: inherit;
  background: #fff;
  text-align: left;
}

.card-index {
  color: var(--navy);
  font-family: "Songti SC", "STSong", "SimSun", serif;
  font-size: 14px;
}

.primary-card strong {
  font-size: 24px;
  line-height: 1.35;
}

.primary-card em {
  align-self: end;
  margin-top: 12px;
  color: var(--navy);
  font-style: normal;
  font-size: 14px;
}

.secondary-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin-top: 8px;
  border-top: 1px solid var(--line);
}

.secondary-grid button {
  display: grid;
  grid-template-columns: minmax(140px, 180px) minmax(0, 1fr);
  gap: 16px;
  align-items: baseline;
  padding: 16px 12px;
  border: 0;
  border-bottom: 1px solid var(--line);
  color: inherit;
  background: transparent;
  text-align: left;
}

.secondary-grid strong {
  font-size: 15px;
  font-weight: 650;
}

.secondary-grid button:nth-child(odd) {
  padding-left: 0;
}

.process-list,
.file-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.process-list {
  display: grid;
  border-top: 1px solid var(--line);
}

.process-list li {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 8px;
  padding: 18px 0;
  border-bottom: 1px solid var(--line);
}

.process-list span {
  color: var(--navy);
  font-family: "Songti SC", "STSong", "SimSun", serif;
  font-size: 18px;
}

.process-list h3 {
  margin: 0 0 4px;
  font-size: 18px;
}

.process-list p,
.file-list span {
  margin: 0;
  font-size: 15px;
}

.file-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-top: 1px solid var(--line);
}

.file-list li {
  display: grid;
  gap: 4px;
  padding: 16px 16px 16px 0;
  border-bottom: 1px solid var(--line);
}

.file-list strong {
  font-size: 15px;
  letter-spacing: 0.04em;
}

.scenario-line {
  display: grid;
  gap: 12px;
  padding-top: 64px;
}

.scenario-line p {
  margin: 0;
  max-width: 760px;
  font-size: 18px;
  line-height: 1.8;
}

.close-section {
  justify-content: space-between;
  margin-top: 28px;
  padding: 28px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.close-section p {
  margin: 8px 0 0;
}

.colophon {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 36px 0 12px;
}

.colophon strong {
  font-size: 16px;
}

.colophon p {
  margin: 6px 0 0;
  font-size: 14px;
}

.colophon nav {
  display: flex;
  align-items: flex-start;
  gap: 4px;
}

@media (max-width: 900px) {
  .nav-shell,
  .hero-inner,
  .section,
  .colophon {
    width: min(100% - 32px, 1080px);
  }

  .nav-shell {
    flex-wrap: wrap;
    gap: 8px 16px;
    padding: 12px 0;
  }

  .nav-links {
    order: 3;
    width: 100%;
    margin-left: 0;
    overflow-x: auto;
  }

  .nav-cta {
    margin-left: auto;
  }

  .hero-section {
    padding-top: 40px;
  }

  .value-list,
  .primary-grid,
  .secondary-grid,
  .file-list {
    grid-template-columns: 1fr;
  }

  .secondary-grid button:nth-child(odd) {
    padding-left: 12px;
  }

  .close-section,
  .colophon,
  .hero-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .primary-button,
  .secondary-button {
    width: 100%;
  }
}
</style>
