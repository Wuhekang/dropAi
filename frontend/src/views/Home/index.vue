<template>
  <main class="landing-page">
    <div class="ambient ambient-one"></div>
    <div class="ambient ambient-two"></div>
    <div class="particle-layer" aria-hidden="true">
      <span v-for="dot in 18" :key="dot"></span>
    </div>

    <header class="site-header" :class="{ scrolled: navScrolled }">
      <nav class="nav-shell" aria-label="DokiAI Academic">
        <BrandLogo as="button" class="brand home-brand" size="lg" @click="scrollToSection('top')" />

        <div class="nav-links">
          <button
            v-for="item in navItems"
            :key="item.id"
            :class="{ active: item.id === 'top' }"
            type="button"
            @click="scrollToSection(item.id)"
          >
            {{ item.label }}
          </button>
        </div>

        <button class="nav-cta" type="button" @click="navigate('/dashboard')">
          进入工作台
          <ArrowRight class="button-icon" />
        </button>
      </nav>
    </header>

    <section id="top" class="hero-section">
      <div class="orbit-line orbit-one"></div>
      <div class="orbit-line orbit-two"></div>

      <div class="hero-inner">
        <div class="hero-copy">
          <p class="eyebrow">
            <span></span>
            AI 驱动的学术创作平台
          </p>

          <h1>
            让复杂学术创作<br />
            变成一条<br />
            <span>智能工作流</span>
          </h1>

          <p class="hero-description">
            DokiAI 帮助用户完成论文写作、<br />
            设计方案、图纸生成与工程创作。
          </p>

          <div class="hero-actions">
            <button class="primary-button" type="button" @click="navigate('/dashboard')">
              进入工作台
              <ArrowRight class="button-icon" />
            </button>
            <button class="secondary-button" type="button" @click="scrollToSection('features')">
              <VideoPlay class="button-icon" />
              查看功能介绍
            </button>
          </div>

          <div class="value-tags" aria-label="核心能力标签">
            <article v-for="item in valueTags" :key="item.title">
              <span class="mini-icon">
                <component :is="item.icon" />
              </span>
              <div>
                <strong>{{ item.title }}</strong>
                <small>{{ item.copy }}</small>
              </div>
            </article>
          </div>
        </div>

        <aside class="workflow-preview" aria-label="DokiAI AI Workflow">
          <div class="preview-title">
            <strong>DokiAI AI Workflow</strong>
            <span>从需求到成果，一站式智能创作流程</span>
          </div>

          <div class="preview-body">
            <div class="analysis-card">
              <h3>文献与资料分析</h3>
              <div class="document-visual">
                <div class="doc-page">
                  <i></i>
                  <i></i>
                  <i></i>
                  <div class="chart-line">
                    <span></span>
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                  <div class="pie"></div>
                </div>
                <b class="pdf-badge">PDF</b>
                <b class="clock-badge">↗</b>
              </div>
              <div class="analysis-tags">
                <span v-for="tag in analysisTags" :key="tag">{{ tag }}</span>
              </div>
            </div>

            <div class="workflow-steps">
              <article v-for="step in workflowSteps" :key="step.no" class="workflow-step">
                <span class="step-connect"></span>
                <div class="step-symbol">
                  <component :is="step.icon" />
                </div>
                <div>
                  <h3><b>{{ step.no }}</b> {{ step.title }}</h3>
                  <p>{{ step.copy }}</p>
                  <div class="step-tags">
                    <span v-for="tag in step.tags" :key="tag">{{ tag }}</span>
                  </div>
                </div>
              </article>
            </div>
          </div>
        </aside>
      </div>
    </section>

    <section id="features" class="section creation-section">
      <div class="section-heading">
        <h2>选择你的创作方向 <i>✦</i></h2>
        <p>从想法到成果，DokiAI支持多种学术与工程创作场景</p>
      </div>

      <div class="creation-grid">
        <article v-for="card in creationCards" :key="card.title" class="creation-card">
          <div class="creation-icon" :class="card.tone">
            <component :is="card.icon" />
          </div>
          <h3>{{ card.title }}</h3>
          <p>{{ card.copy }}</p>
          <button type="button" @click="navigate(card.route)" aria-label="进入创作方向">
            <ArrowRight />
          </button>
        </article>
      </div>
    </section>

    <section id="workflow" class="section insight-section">
      <article class="process-panel glass-panel">
        <div class="panel-heading">
          <h2>智能工作流程</h2>
          <p>四步完成从想法到成果的闭环</p>
        </div>

        <div class="process-line">
          <article v-for="step in processSteps" :key="step.title" class="process-node">
            <span>{{ step.no }}</span>
            <h3>{{ step.title }}</h3>
            <p>{{ step.copy }}</p>
          </article>
        </div>

        <button class="text-link" type="button" @click="scrollToSection('workflow')">
          了解完整流程
          <ArrowRight />
        </button>
      </article>

      <article id="deliverables" class="deliverable-panel glass-panel">
        <div class="panel-heading">
          <h2>交付的成果不只是文字，更是完整的解决方案</h2>
          <p>多种格式导出，满足不同场景需求</p>
        </div>

        <div class="file-grid">
          <article v-for="item in deliverables" :key="item.title">
            <b :class="item.tone">
              <component :is="item.icon" />
            </b>
            <strong>{{ item.title }}</strong>
            <small>{{ item.copy }}</small>
          </article>
        </div>

        <button class="text-link" type="button" @click="scrollToSection('deliverables')">
          查看成果示例
          <ArrowRight />
        </button>
      </article>
    </section>

    <section id="scenarios" class="section scenario-strip">
      <span v-for="item in scenarios" :key="item">{{ item }}</span>
    </section>

    <section id="pricing" class="section cta-section">
      <div class="cta-copy">
        <h2>准备好开始你的项目了吗？</h2>
        <p>加入 DokiAI，体验更高效、更智能的学术与工程创作方式。</p>
      </div>

      <div class="cta-actions">
        <button class="cta-primary" type="button" @click="navigate('/dashboard')">
          进入工作台
          <ArrowRight class="button-icon" />
        </button>
        <button class="cta-secondary" type="button" @click="navigate('/login')">
          <Calendar class="button-icon" />
          预约演示
        </button>
      </div>

      <div class="cta-orbit" aria-hidden="true">
        <span>D</span>
      </div>
    </section>

    <footer id="help" class="site-footer">
      <article v-for="item in footerStats" :key="item.label">
        <component :is="item.icon" />
        <strong>{{ item.value }}</strong>
        <span>{{ item.label }}</span>
      </article>
    </footer>
  </main>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BrandLogo from '../../components/BrandLogo.vue'
import {
  ArrowRight,
  Calendar,
  Check,
  Cpu,
  DataAnalysis,
  Document,
  Files,
  FolderOpened,
  MagicStick,
  Picture,
  Reading,
  Search,
  Share,
  UploadFilled,
  VideoPlay
} from '@element-plus/icons-vue'

const router = useRouter()
const navScrolled = ref(false)

const navItems = [
  { id: 'top', label: '首页' },
  { id: 'features', label: '功能介绍' },
  { id: 'workflow', label: '工作流程' },
  { id: 'scenarios', label: '应用场景' },
  { id: 'deliverables', label: '成果展示' },
  { id: 'pricing', label: '价格与权益' },
  { id: 'help', label: '帮助中心' }
]

const valueTags = [
  { title: '多学科覆盖', copy: '理工 / 经管 / 艺术', icon: Share },
  { title: '智能驱动', copy: 'AI理解 · 生成 · 优化', icon: Cpu },
  { title: '成果完整', copy: '从思路到交付', icon: Check }
]

const analysisTags = ['研究趋势', '核心观点', '方法对比', '数据结论']

const workflowSteps = [
  { no: '01', title: '需求录入', copy: '明确项目目标', tags: ['明确目标', '任务要求'], icon: UploadFilled },
  { no: '02', title: '资料上传', copy: '上传文献、图片、数据', tags: ['文献资料', '图片数据'], icon: FolderOpened },
  { no: '03', title: '智能生成', copy: '生成内容与方案', tags: ['内容生成', '方案设计'], icon: MagicStick },
  { no: '04', title: '成果输出', copy: '论文 / 图纸 / PPT', tags: ['多格式导出', '一键成稿'], icon: Files }
]

const creationCards = [
  { title: '论文创作', copy: '从开题规划到论文交付', icon: Reading, tone: 'violet', route: '/writing-generator' },
  { title: '文献检索', copy: '快速定位高价值资料', icon: Search, tone: 'blue', route: '/writing-generator' },
  { title: '文献分析', copy: 'AI理解文献内容', icon: DataAnalysis, tone: 'green', route: '/writing-generator' },
  { title: '学术优化', copy: '提升论文质量表达', icon: MagicStick, tone: 'gold', route: '/rewrite' },
  { title: '机械设计', copy: '图片理解、结构方案与工程报告', icon: Cpu, tone: 'blue', route: '/mechanical-design' },
  { title: '图纸智能处理', copy: '识别图纸并分析机械结构', icon: Picture, tone: 'cyan', route: '/mechanical-design' },
  { title: '流程图生成', copy: '生成 UML、ER、流程图', icon: Share, tone: 'violet', route: '/computer-generator' },
  { title: 'PPT与答辩', copy: '生成展示材料', icon: VideoPlay, tone: 'coral', route: '/ppt-generator' }
]

const processSteps = [
  { no: '01', title: '需求输入', copy: '上传题目 / 任务书，AI 理解任务目标' },
  { no: '02', title: '资料分析', copy: '检索与分析文献，提炼关键信息' },
  { no: '03', title: '智能生成', copy: 'AI 生成内容 / 图纸 / 方案等成果' },
  { no: '04', title: '成果输出', copy: '导出论文、图纸、PPT 等多格式成果' }
]

const deliverables = [
  { title: 'DOCX', copy: '论文文档', icon: Document, tone: 'blue' },
  { title: 'PPT', copy: '演示文稿', icon: VideoPlay, tone: 'orange' },
  { title: 'DWG / CAD', copy: '工程图纸', icon: Share, tone: 'cyan' },
  { title: 'PNG / JPG', copy: '图片文件', icon: Picture, tone: 'violet' },
  { title: 'PDF', copy: '报告文件', icon: Files, tone: 'coral' },
  { title: 'XLSX', copy: '数据表格', icon: DataAnalysis, tone: 'green' }
]

const scenarios = ['毕业论文', '课程设计', '工程说明书', '机械制图', '系统流程图', '答辩汇报', '文献综述', '方案展示']

const footerStats = [
  { value: '10,000+', label: '用户信赖选择', icon: Share },
  { value: '500,000+', label: '任务已完成', icon: Document },
  { value: '98.5%', label: '用户满意度', icon: Check },
  { value: '7×24h', label: '智能服务支持', icon: Calendar }
]

function scrollToSection(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function navigate(path) {
  router.push(path)
}

function updateNavState() {
  navScrolled.value = window.scrollY > 16
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
  background: #f8f7ff;
  color: #0f1736;
}

:global(*) {
  box-sizing: border-box;
}

.landing-page {
  position: relative;
  min-height: 100vh;
  overflow-x: hidden;
  color: #101733;
  background:
    radial-gradient(circle at 78% 8%, rgba(109, 93, 251, 0.22), transparent 28%),
    radial-gradient(circle at 16% 34%, rgba(74, 144, 255, 0.15), transparent 34%),
    radial-gradient(circle at 86% 86%, rgba(56, 217, 197, 0.13), transparent 30%),
    linear-gradient(rgba(109, 93, 251, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(74, 144, 255, 0.04) 1px, transparent 1px),
    linear-gradient(180deg, #ffffff 0%, #f8f7ff 52%, #eef2ff 100%);
  background-size: auto, auto, auto, 52px 52px, 52px 52px, auto;
  font-family:
    Inter,
    "PingFang SC",
    "Microsoft YaHei",
    "Helvetica Neue",
    Arial,
    sans-serif;
}

.landing-page::before,
.landing-page::after {
  position: absolute;
  pointer-events: none;
  content: "";
}

.landing-page::before {
  top: 72px;
  left: -10%;
  z-index: 0;
  width: 120%;
  height: 760px;
  background:
    conic-gradient(from 210deg at 52% 52%, transparent 0 18%, rgba(109, 93, 251, 0.1) 28%, transparent 39% 58%, rgba(56, 217, 197, 0.1) 68%, transparent 78%),
    radial-gradient(circle at 58% 44%, rgba(255, 255, 255, 0.82), transparent 36%);
  filter: blur(8px);
  opacity: 0.52;
  transform-origin: center;
  animation: energySweep 18s ease-in-out infinite;
  mask-image: linear-gradient(180deg, #000 0%, rgba(0, 0, 0, 0.78) 62%, transparent 100%);
}

.landing-page::after {
  inset: 86px 0 0;
  z-index: 0;
  background-image:
    linear-gradient(116deg, transparent 0 49%, rgba(109, 93, 251, 0.1) 49.15%, transparent 49.5%),
    linear-gradient(28deg, transparent 0 54%, rgba(56, 217, 197, 0.08) 54.15%, transparent 54.45%),
    radial-gradient(circle, rgba(109, 93, 251, 0.18) 1px, transparent 1.5px);
  background-size: 480px 220px, 560px 260px, 88px 88px;
  opacity: 0.22;
  animation: dataDrift 22s linear infinite;
  mask-image: linear-gradient(180deg, transparent 0%, #000 12%, rgba(0, 0, 0, 0.68) 74%, transparent 100%);
}

button {
  border: 0;
  font: inherit;
  cursor: pointer;
}

.ambient,
.particle-layer,
.orbit-line {
  pointer-events: none;
}

.ambient {
  position: absolute;
  z-index: 0;
  border-radius: 999px;
  filter: blur(22px);
  opacity: 0.82;
  animation: drift 10s ease-in-out infinite;
}

.ambient-one {
  top: 104px;
  left: 55%;
  width: 460px;
  height: 430px;
  background: radial-gradient(circle, rgba(109, 93, 251, 0.18), transparent 68%);
}

.ambient-two {
  top: 398px;
  left: -130px;
  width: 520px;
  height: 520px;
  background: radial-gradient(circle, rgba(56, 217, 197, 0.15), transparent 70%);
  animation-delay: -4s;
}

.particle-layer {
  position: absolute;
  inset: 82px 0 auto;
  z-index: 0;
  height: 860px;
  overflow: hidden;
  mask-image: linear-gradient(180deg, transparent 0%, #000 16%, rgba(0, 0, 0, 0.82) 78%, transparent 100%);
}

.particle-layer::before,
.particle-layer::after {
  position: absolute;
  pointer-events: none;
  content: "";
}

.particle-layer::before {
  inset: 140px 8% auto;
  height: 390px;
  background:
    linear-gradient(28deg, transparent 0 31%, rgba(109, 93, 251, 0.15) 31.2%, transparent 31.5%),
    linear-gradient(148deg, transparent 0 54%, rgba(74, 144, 255, 0.13) 54.15%, transparent 54.45%),
    linear-gradient(8deg, transparent 0 63%, rgba(56, 217, 197, 0.12) 63.15%, transparent 63.45%);
  opacity: 0.38;
}

.particle-layer::after {
  left: 58%;
  top: 95px;
  width: 260px;
  height: 260px;
  border: 1px solid rgba(109, 93, 251, 0.12);
  border-radius: 50%;
  box-shadow:
    0 0 0 52px rgba(109, 93, 251, 0.035),
    0 0 0 116px rgba(74, 144, 255, 0.025);
  transform: rotate(-18deg) scaleX(1.75);
  animation: orbitPulseWide 8s ease-in-out infinite;
}

.particle-layer span {
  position: absolute;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: rgba(109, 93, 251, 0.35);
  box-shadow: 0 0 16px rgba(109, 93, 251, 0.6);
  animation: particleFloat 8s ease-in-out infinite;
}

.particle-layer span:nth-child(1) { left: 5%; top: 64%; animation-delay: -1s; }
.particle-layer span:nth-child(2) { left: 11%; top: 78%; animation-delay: -3s; }
.particle-layer span:nth-child(3) { left: 19%; top: 52%; animation-delay: -5s; }
.particle-layer span:nth-child(4) { left: 29%; top: 68%; animation-delay: -2s; }
.particle-layer span:nth-child(5) { left: 37%; top: 44%; animation-delay: -6s; }
.particle-layer span:nth-child(6) { left: 48%; top: 70%; animation-delay: -4s; }
.particle-layer span:nth-child(7) { left: 57%; top: 20%; animation-delay: -7s; }
.particle-layer span:nth-child(8) { left: 68%; top: 16%; animation-delay: -2s; }
.particle-layer span:nth-child(9) { left: 77%; top: 35%; animation-delay: -8s; }
.particle-layer span:nth-child(10) { left: 91%; top: 8%; animation-delay: -3s; }
.particle-layer span:nth-child(11) { left: 86%; top: 62%; animation-delay: -1s; }
.particle-layer span:nth-child(12) { left: 96%; top: 74%; animation-delay: -6s; }
.particle-layer span:nth-child(13) { left: 3%; top: 25%; animation-delay: -5s; }
.particle-layer span:nth-child(14) { left: 24%; top: 18%; animation-delay: -4s; }
.particle-layer span:nth-child(15) { left: 42%; top: 28%; animation-delay: -2s; }
.particle-layer span:nth-child(16) { left: 61%; top: 82%; animation-delay: -3s; }
.particle-layer span:nth-child(17) { left: 74%; top: 72%; animation-delay: -5s; }
.particle-layer span:nth-child(18) { left: 98%; top: 46%; animation-delay: -7s; }

.site-header {
  position: sticky;
  top: 0;
  z-index: 30;
  border-bottom: 1px solid rgba(255, 255, 255, 0.5);
  background: rgba(255, 255, 255, 0.7);
  box-shadow: 0 14px 40px rgba(63, 53, 124, 0.06);
  backdrop-filter: blur(22px) saturate(130%);
  transition: background 0.3s ease, box-shadow 0.3s ease, border-color 0.3s ease;
}

.site-header.scrolled {
  border-color: rgba(109, 93, 251, 0.12);
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 18px 46px rgba(63, 53, 124, 0.1);
}

.nav-shell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(1580px, calc(100% - clamp(40px, 5vw, 96px)));
  height: 72px;
  margin: 0 auto;
  gap: 24px;
}

.brand {
  flex: 0 0 auto;
}

.home-brand {
  padding: 0;
}

.nav-links {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex: 1;
}

.nav-links button {
  min-height: 42px;
  padding: 0 16px;
  border-radius: 12px;
  color: #151e3f;
  background: transparent;
  font-size: 14px;
  font-weight: 700;
  transition: 0.22s ease;
}

.nav-links button:hover,
.nav-links button.active {
  color: #5b4cf0;
  background: rgba(109, 93, 251, 0.1);
}

.nav-cta,
.primary-button,
.cta-primary {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 48px;
  overflow: hidden;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(135deg, #6d5dfb, #4a90ff);
  box-shadow: 0 14px 32px rgba(91, 76, 240, 0.32);
  font-weight: 800;
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

.nav-cta::after,
.primary-button::after,
.cta-primary::after {
  position: absolute;
  inset: 0;
  background: linear-gradient(110deg, transparent 0%, rgba(255, 255, 255, 0.36) 45%, transparent 72%);
  content: "";
  transform: translateX(-120%);
  transition: transform 0.7s ease;
}

.nav-cta {
  flex: 0 0 auto;
  padding: 0 22px;
}

.nav-cta:hover,
.primary-button:hover,
.secondary-button:hover,
.cta-primary:hover,
.cta-secondary:hover {
  transform: translateY(-3px);
}

.nav-cta:hover,
.primary-button:hover,
.cta-primary:hover {
  box-shadow: 0 18px 44px rgba(91, 76, 240, 0.42), 0 0 24px rgba(109, 93, 251, 0.24);
}

.nav-cta:hover::after,
.primary-button:hover::after,
.cta-primary:hover::after {
  transform: translateX(120%);
}

.secondary-button:hover,
.cta-secondary:hover {
  box-shadow: 0 16px 34px rgba(38, 32, 87, 0.12), 0 0 22px rgba(109, 93, 251, 0.14);
}

.button-icon {
  width: 18px;
  height: 18px;
}

.hero-section {
  position: relative;
  z-index: 1;
  padding: 36px 0 38px;
}

.orbit-line {
  position: absolute;
  left: 50%;
  bottom: -86px;
  width: 1500px;
  height: 360px;
  border: 1px solid rgba(109, 93, 251, 0.12);
  border-radius: 50%;
  transform: translateX(-50%) rotate(-5deg);
}

.orbit-two {
  bottom: -128px;
  border-color: rgba(56, 217, 197, 0.1);
  transform: translateX(-50%) rotate(9deg);
}

.hero-inner {
  display: grid;
  grid-template-columns: minmax(520px, 0.94fr) minmax(600px, 1.06fr);
  align-items: center;
  width: min(1580px, calc(100% - clamp(40px, 5vw, 96px)));
  margin: 0 auto;
  gap: clamp(48px, 5vw, 88px);
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 28px;
  padding: 10px 16px;
  border: 1px solid rgba(109, 93, 251, 0.14);
  border-radius: 999px;
  color: #5b4cf0;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: 0 12px 34px rgba(109, 93, 251, 0.08);
  font-size: 14px;
  font-weight: 800;
}

.eyebrow span {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #6d5dfb;
  box-shadow: 0 0 14px rgba(109, 93, 251, 0.8);
}

.hero-copy h1 {
  margin: 0;
  color: #101733;
  font-size: clamp(48px, 5.25vw, 66px);
  line-height: 1.1;
  letter-spacing: 0;
}

.hero-copy h1 span {
  background: linear-gradient(110deg, #6d5dfb 12%, #8b5cf6 46%, #21c6f3 88%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-description {
  width: min(650px, 100%);
  margin: 24px 0 0;
  color: #4e5a78;
  font-size: 17px;
  line-height: 1.85;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 28px;
}

.primary-button {
  min-width: 178px;
  padding: 0 26px;
  font-size: 16px;
}

.secondary-button,
.cta-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  min-height: 48px;
  border: 1px solid rgba(109, 93, 251, 0.16);
  border-radius: 12px;
  color: #101733;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 12px 28px rgba(38, 32, 87, 0.08);
  font-weight: 800;
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

.secondary-button {
  min-width: 178px;
  padding: 0 22px;
}

.value-tags {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 30px;
}

.value-tags article {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: center;
  gap: 11px;
  min-height: 66px;
  padding: 11px 14px;
  border: 1px solid rgba(109, 93, 251, 0.12);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.64);
  box-shadow: 0 12px 28px rgba(42, 34, 93, 0.08);
  backdrop-filter: blur(12px);
}

.mini-icon {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 12px;
  color: #5b4cf0;
  background: #f0edff;
}

.mini-icon svg {
  width: 21px;
  height: 21px;
}

.value-tags strong,
.value-tags small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.value-tags strong {
  font-size: 13px;
}

.value-tags small {
  margin-top: 3px;
  color: #6c7895;
  font-size: 11px;
}

.workflow-preview {
  position: relative;
  min-height: 505px;
  padding: 22px;
  overflow: hidden;
  border: 1px solid rgba(109, 93, 251, 0.16);
  border-radius: 22px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.78), rgba(255, 255, 255, 0.54)),
    radial-gradient(circle at 86% 4%, rgba(109, 93, 251, 0.18), transparent 32%);
  box-shadow: 0 26px 70px rgba(58, 47, 124, 0.14);
  backdrop-filter: blur(18px);
  animation: previewFloat 6s ease-in-out infinite;
}

.workflow-preview::before,
.workflow-preview::after {
  position: absolute;
  pointer-events: none;
  content: "";
}

.workflow-preview::before {
  inset: 1px;
  border-radius: 21px;
  background:
    radial-gradient(circle at 12% 22%, rgba(255, 255, 255, 0.85), transparent 26%),
    linear-gradient(135deg, rgba(109, 93, 251, 0.1), transparent 38%, rgba(56, 217, 197, 0.08));
  opacity: 0.72;
}

.workflow-preview::after {
  top: -24%;
  left: -54%;
  width: 42%;
  height: 150%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.46), transparent);
  transform: rotate(18deg);
  animation: glassSweep 8s ease-in-out infinite;
}

.preview-title {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 6px;
  padding: 2px 6px 18px;
}

.preview-title strong {
  font-size: 20px;
}

.preview-title span {
  color: #68748d;
  font-size: 13px;
}

.preview-body {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 0.92fr 1.08fr;
  gap: 20px;
}

.analysis-card,
.workflow-step {
  border: 1px solid rgba(109, 93, 251, 0.13);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.74), rgba(255, 255, 255, 0.58));
  box-shadow: 0 16px 38px rgba(64, 53, 127, 0.08);
  backdrop-filter: blur(12px);
}

.analysis-card {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: 420px;
  padding: 18px;
}

.analysis-card h3 {
  margin: 0 0 16px;
  color: #5b4cf0;
  font-size: 15px;
}

.document-visual {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 228px;
  border-radius: 18px;
  background:
    radial-gradient(circle at 75% 25%, rgba(109, 93, 251, 0.18), transparent 35%),
    linear-gradient(180deg, #f8f7ff, #fff);
  overflow: hidden;
}

.document-visual::before,
.document-visual::after {
  position: absolute;
  pointer-events: none;
  content: "";
}

.document-visual::before {
  inset: 0;
  background:
    linear-gradient(rgba(109, 93, 251, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(74, 144, 255, 0.05) 1px, transparent 1px);
  background-size: 26px 26px;
  opacity: 0.5;
}

.document-visual::after {
  right: 18px;
  bottom: 28px;
  width: 88px;
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, transparent, #38d9c5, transparent);
  box-shadow: 0 0 18px rgba(56, 217, 197, 0.52);
  animation: scanLine 3.8s ease-in-out infinite;
}

.doc-page {
  position: relative;
  z-index: 1;
  width: 155px;
  height: 198px;
  padding: 30px 22px;
  border: 1px solid rgba(109, 93, 251, 0.13);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 18px 36px rgba(109, 93, 251, 0.18);
}

.doc-page i {
  display: block;
  height: 7px;
  margin-bottom: 10px;
  border-radius: 999px;
  background: #e7e5ff;
}

.doc-page i:nth-child(1) { width: 86%; }
.doc-page i:nth-child(2) { width: 66%; }
.doc-page i:nth-child(3) { width: 78%; }

.chart-line {
  position: absolute;
  left: 20px;
  right: 20px;
  bottom: 30px;
  display: flex;
  align-items: flex-end;
  gap: 7px;
  height: 52px;
  border-bottom: 2px solid #dcd8ff;
}

.chart-line span {
  width: 16px;
  border-radius: 999px 999px 2px 2px;
  background: linear-gradient(180deg, #6d5dfb, #38d9c5);
}

.chart-line span:nth-child(1) { height: 18px; }
.chart-line span:nth-child(2) { height: 28px; }
.chart-line span:nth-child(3) { height: 38px; }
.chart-line span:nth-child(4) { height: 48px; }

.pie {
  position: absolute;
  right: -17px;
  bottom: -17px;
  width: 58px;
  height: 58px;
  border: 8px solid #ebe8ff;
  border-top-color: #6d5dfb;
  border-right-color: #4a90ff;
  border-radius: 50%;
  background: #fff;
}

.pdf-badge,
.clock-badge {
  position: absolute;
  z-index: 2;
  display: grid;
  place-items: center;
  border-radius: 11px;
  color: #fff;
  font-size: 10px;
  font-style: normal;
  box-shadow: 0 12px 24px rgba(91, 76, 240, 0.18);
}

.pdf-badge {
  left: 18px;
  bottom: 28px;
  width: 38px;
  height: 34px;
  background: #ff4d6d;
}

.clock-badge {
  top: 28px;
  right: 34px;
  width: 34px;
  height: 34px;
  background: linear-gradient(135deg, #6d5dfb, #38d9c5);
}

.analysis-tags {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
  margin-top: 16px;
}

.analysis-tags span,
.step-tags span {
  border-radius: 999px;
  color: #5b4cf0;
  background: #f0edff;
  font-size: 12px;
  font-weight: 700;
  text-align: center;
}

.analysis-tags span {
  padding: 8px 9px;
}

.workflow-steps {
  position: relative;
  display: grid;
  gap: 14px;
}

.workflow-steps::before {
  position: absolute;
  top: 20px;
  bottom: 20px;
  left: 31px;
  width: 2px;
  background: linear-gradient(180deg, rgba(109, 93, 251, 0.08), #6d5dfb, rgba(56, 217, 197, 0.12));
  content: "";
}

.workflow-steps::after {
  position: absolute;
  top: 20px;
  left: 30px;
  width: 4px;
  height: 48px;
  border-radius: 999px;
  background: linear-gradient(180deg, transparent, #ffffff, #38d9c5, transparent);
  box-shadow: 0 0 18px rgba(109, 93, 251, 0.65);
  content: "";
  animation: stepGlow 4.8s ease-in-out infinite;
}

.workflow-step {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 13px;
  align-items: center;
  min-height: 92px;
  padding: 15px 17px;
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease;
}

.workflow-step:hover {
  border-color: rgba(109, 93, 251, 0.28);
  box-shadow: 0 18px 46px rgba(64, 53, 127, 0.12);
  transform: translateY(-3px);
}

.step-connect {
  position: absolute;
  left: -11px;
  top: 50%;
  width: 11px;
  height: 2px;
  background: #a99dff;
}

.step-symbol {
  position: relative;
  z-index: 1;
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border-radius: 13px;
  color: #5b4cf0;
  background: #f0edff;
}

.step-symbol svg {
  width: 23px;
  height: 23px;
}

.workflow-step h3 {
  margin: 0;
  font-size: 16px;
}

.workflow-step h3 b {
  color: #5b4cf0;
}

.workflow-step p {
  margin: 5px 0 9px;
  color: #657089;
  font-size: 13px;
}

.step-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.step-tags span {
  padding: 6px 10px;
}

.section {
  position: relative;
  z-index: 1;
  width: min(1580px, calc(100% - clamp(40px, 5vw, 96px)));
  margin: 0 auto;
}

.creation-section {
  padding: 18px 0 14px;
}

.section-heading {
  margin-bottom: 18px;
  text-align: center;
}

.section-heading h2 {
  margin: 0;
  color: #111936;
  font-size: 28px;
}

.section-heading h2 i {
  color: #6d5dfb;
  font-style: normal;
}

.section-heading p {
  margin: 10px 0 0;
  color: #5d6884;
}

.creation-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: clamp(14px, 1.25vw, 22px);
}

.creation-card {
  position: relative;
  display: grid;
  justify-items: center;
  min-height: 196px;
  padding: 22px clamp(18px, 1.5vw, 28px);
  overflow: hidden;
  border: 1px solid rgba(109, 93, 251, 0.13);
  border-radius: 18px;
  background:
    radial-gradient(circle at 50% 0%, rgba(109, 93, 251, 0.09), transparent 48%),
    rgba(255, 255, 255, 0.7);
  box-shadow: 0 16px 45px rgba(63, 53, 124, 0.09);
  text-align: center;
  backdrop-filter: blur(14px);
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease;
}

.creation-card::before {
  position: absolute;
  top: -42px;
  right: -36px;
  width: 112px;
  height: 112px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.58);
  filter: blur(6px);
  content: "";
}

.creation-card:hover,
.glass-panel:hover {
  border-color: rgba(109, 93, 251, 0.26);
  transform: translateY(-5px);
  box-shadow: 0 24px 60px rgba(63, 53, 124, 0.15), 0 0 24px rgba(109, 93, 251, 0.08);
}

.creation-icon {
  position: relative;
  z-index: 1;
  display: grid;
  width: 70px;
  height: 70px;
  place-items: center;
  margin-bottom: 12px;
  border-radius: 21px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9), 0 16px 30px rgba(74, 144, 255, 0.16);
  animation: iconFloat 4.8s ease-in-out infinite;
}

.creation-card:nth-child(2n) .creation-icon {
  animation-delay: -2s;
}

.creation-icon svg {
  width: 36px;
  height: 36px;
}

.creation-card h3 {
  position: relative;
  z-index: 1;
  margin: 0;
  font-size: 18px;
}

.creation-card p {
  position: relative;
  z-index: 1;
  min-height: 34px;
  margin: 8px 0 12px;
  color: #56617c;
  font-size: 13px;
  line-height: 1.55;
}

.creation-card button {
  position: relative;
  z-index: 1;
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid rgba(109, 93, 251, 0.18);
  border-radius: 50%;
  color: #5b4cf0;
  background: #fff;
  box-shadow: 0 9px 20px rgba(109, 93, 251, 0.12);
}

.creation-card button svg {
  width: 16px;
  height: 16px;
}

.insight-section {
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 16px;
  padding: 16px 0 0;
}

.glass-panel {
  min-height: 238px;
  border: 1px solid rgba(109, 93, 251, 0.13);
  border-radius: 18px;
  background:
    radial-gradient(circle at 88% 12%, rgba(109, 93, 251, 0.08), transparent 34%),
    rgba(255, 255, 255, 0.7);
  box-shadow: 0 16px 45px rgba(63, 53, 124, 0.09);
  backdrop-filter: blur(14px);
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease;
}

.process-panel,
.deliverable-panel {
  padding: 22px;
}

.panel-heading h2 {
  margin: 0;
  color: #111936;
  font-size: 23px;
  line-height: 1.35;
}

.panel-heading p {
  margin: 8px 0 0;
  color: #5d6884;
  font-size: 14px;
}

.process-line {
  position: relative;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin: 26px 0 20px;
}

.process-line::before {
  position: absolute;
  top: 21px;
  left: 20px;
  right: 20px;
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, #c9c3ff, #5b4cf0, #38d9c5, #d7d3ff);
  content: "";
}

.process-line::after {
  position: absolute;
  top: 19px;
  left: 0;
  width: 38px;
  height: 7px;
  border-radius: 999px;
  background: #fff;
  box-shadow: 0 0 18px #6d5dfb;
  content: "";
  animation: flowLight 4s linear infinite;
}

.process-node {
  position: relative;
  z-index: 1;
  display: grid;
  justify-items: center;
  gap: 8px;
  text-align: center;
}

.process-node span {
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  border: 7px solid #f2efff;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, #5b4cf0, #7d4dff);
  box-shadow: 0 12px 24px rgba(91, 76, 240, 0.2);
  font-size: 12px;
  font-weight: 900;
}

.process-node h3 {
  margin: 0;
  font-size: 15px;
}

.process-node p {
  margin: 0;
  color: #66718b;
  font-size: 12px;
  line-height: 1.55;
}

.text-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0;
  color: #5b4cf0;
  background: transparent;
  font-weight: 800;
}

.text-link svg {
  width: 17px;
  height: 17px;
}

.file-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin: 20px 0 18px;
}

.file-grid article {
  position: relative;
  display: grid;
  justify-items: center;
  gap: 6px;
  min-height: 106px;
  padding: 12px 7px 11px;
  overflow: hidden;
  border: 1px solid rgba(109, 93, 251, 0.1);
  border-radius: 15px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.82), rgba(255, 255, 255, 0.56)),
    radial-gradient(circle at 50% 0%, rgba(109, 93, 251, 0.08), transparent 55%);
  box-shadow: 0 12px 26px rgba(63, 53, 124, 0.07);
  text-align: center;
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease;
}

.file-grid article::before {
  position: absolute;
  top: -34px;
  width: 74px;
  height: 74px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.74);
  content: "";
}

.file-grid article:hover {
  border-color: rgba(109, 93, 251, 0.24);
  box-shadow: 0 18px 34px rgba(63, 53, 124, 0.12);
  transform: translateY(-4px);
}

.file-grid b {
  position: relative;
  z-index: 1;
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border-radius: 13px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.92), 0 12px 20px rgba(74, 144, 255, 0.12);
}

.file-grid b svg {
  width: 25px;
  height: 25px;
}

.file-grid strong {
  position: relative;
  z-index: 1;
  font-size: 13px;
}

.file-grid small {
  position: relative;
  z-index: 1;
  color: #68748b;
  font-size: 11px;
}

.scenario-strip {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  padding: 18px 0;
}

.scenario-strip span {
  padding: 10px 15px;
  border: 1px solid rgba(109, 93, 251, 0.12);
  border-radius: 999px;
  color: #52607d;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: 0 10px 24px rgba(63, 53, 124, 0.06);
  font-size: 13px;
  font-weight: 700;
}

.cta-section {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 250px;
  gap: clamp(36px, 4vw, 72px);
  align-items: center;
  min-height: 120px;
  margin-top: 0;
  padding: 30px clamp(34px, 4vw, 68px);
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 18px;
  color: #fff;
  background:
    radial-gradient(circle at 86% 50%, rgba(109, 93, 251, 0.78), transparent 29%),
    radial-gradient(circle at 18% 18%, rgba(56, 217, 197, 0.32), transparent 28%),
    radial-gradient(circle at 42% 112%, rgba(74, 144, 255, 0.34), transparent 34%),
    linear-gradient(135deg, #0a31b6 0%, #151a84 42%, #651bdf 100%);
  box-shadow: 0 22px 54px rgba(24, 25, 108, 0.22);
}

.cta-section::before {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(circle, rgba(255, 255, 255, 0.7) 1px, transparent 1.5px),
    linear-gradient(110deg, rgba(255, 255, 255, 0.12), transparent 35%);
  background-size: 44px 44px, 100% 100%;
  opacity: 0.45;
  content: "";
  animation: ctaStars 12s linear infinite;
}

.cta-section::after {
  position: absolute;
  right: 120px;
  bottom: 24px;
  width: 340px;
  height: 90px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 50%;
  box-shadow:
    0 0 34px rgba(74, 144, 255, 0.32),
    inset 0 0 24px rgba(255, 255, 255, 0.08);
  content: "";
  transform: rotate(-10deg);
  animation: ctaOrbitPulse 7s ease-in-out infinite;
}

.cta-copy,
.cta-actions,
.cta-orbit {
  position: relative;
  z-index: 1;
}

.cta-copy h2 {
  margin: 0;
  font-size: 28px;
}

.cta-copy p {
  margin: 10px 0 0;
  color: rgba(255, 255, 255, 0.82);
}

.cta-actions {
  display: flex;
  gap: 14px;
}

.cta-primary,
.cta-secondary {
  min-width: 170px;
  padding: 0 24px;
}

.cta-secondary {
  border-color: rgba(255, 255, 255, 0.3);
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
  box-shadow: none;
}

.cta-orbit {
  display: grid;
  place-items: center;
  min-height: 106px;
}

.cta-orbit::before,
.cta-orbit::after {
  position: absolute;
  border: 1px solid rgba(255, 255, 255, 0.34);
  border-radius: 50%;
  content: "";
}

.cta-orbit::before {
  width: 162px;
  height: 62px;
  transform: rotate(-12deg);
}

.cta-orbit::after {
  width: 132px;
  height: 52px;
  transform: rotate(20deg);
}

.cta-orbit span {
  position: relative;
  display: grid;
  width: 68px;
  height: 68px;
  place-items: center;
  border-radius: 19px;
  color: #fff;
  background: linear-gradient(145deg, #4a90ff, #6d5dfb 60%, #9d5cff);
  box-shadow: 0 0 42px rgba(255, 255, 255, 0.44);
  font-size: 28px;
  font-weight: 900;
}

.cta-orbit span::after {
  position: absolute;
  top: -9px;
  right: 4px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #38d9c5;
  box-shadow: 0 0 18px rgba(56, 217, 197, 0.9);
  content: "";
  animation: particleFloat 3.8s ease-in-out infinite;
}

.site-footer {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  width: min(1580px, calc(100% - clamp(40px, 5vw, 96px)));
  margin: 14px auto 28px;
  border: 1px solid rgba(109, 93, 251, 0.12);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.68);
  box-shadow: 0 16px 45px rgba(63, 53, 124, 0.08);
  backdrop-filter: blur(14px);
}

.site-footer article {
  display: grid;
  grid-template-columns: 42px auto;
  gap: 2px 12px;
  align-items: center;
  padding: 20px 28px;
  border-right: 1px solid rgba(109, 93, 251, 0.09);
}

.site-footer article:last-child {
  border-right: 0;
}

.site-footer svg {
  grid-row: 1 / 3;
  width: 28px;
  height: 28px;
  color: #5b4cf0;
}

.site-footer strong {
  font-size: 20px;
}

.site-footer span {
  color: #66718b;
  font-size: 12px;
}

.violet {
  color: #6d5dfb;
  background: linear-gradient(145deg, #f3efff, #fff);
}

.blue {
  color: #4a90ff;
  background: linear-gradient(145deg, #eaf3ff, #fff);
}

.green,
.cyan {
  color: #0ea898;
  background: linear-gradient(145deg, #e8fbf8, #fff);
}

.gold {
  color: #f59e0b;
  background: linear-gradient(145deg, #fff4dc, #fff);
}

.coral,
.orange {
  color: #f05b5b;
  background: linear-gradient(145deg, #fff0ed, #fff);
}

@keyframes drift {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }

  50% {
    transform: translate3d(26px, -22px, 0);
  }
}

@keyframes particleFloat {
  0%,
  100% {
    transform: translateY(0);
    opacity: 0.18;
  }

  50% {
    transform: translateY(-28px);
    opacity: 0.9;
  }
}

@keyframes previewFloat {
  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-8px);
  }
}

@keyframes iconFloat {
  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-6px);
  }
}

@keyframes flowLight {
  0% {
    left: 0;
  }

  100% {
    left: calc(100% - 38px);
  }
}

@keyframes ctaStars {
  0% {
    background-position: 0 0, 0 0;
  }

  100% {
    background-position: 90px -44px, 0 0;
  }
}

@keyframes energySweep {
  0%,
  100% {
    transform: translate3d(-18px, 0, 0) rotate(0deg) scale(1);
  }

  50% {
    transform: translate3d(22px, -18px, 0) rotate(4deg) scale(1.04);
  }
}

@keyframes dataDrift {
  0% {
    background-position: 0 0, 0 0, 0 0;
  }

  100% {
    background-position: 480px -220px, -560px 260px, 88px 88px;
  }
}

@keyframes orbitPulseWide {
  0%,
  100% {
    opacity: 0.72;
    transform: rotate(-18deg) scaleX(1.75) scale(1);
  }

  50% {
    opacity: 1;
    transform: rotate(-18deg) scaleX(1.75) scale(1.04);
  }
}

@keyframes ctaOrbitPulse {
  0%,
  100% {
    opacity: 0.72;
    transform: rotate(-10deg) scale(1);
  }

  50% {
    opacity: 1;
    transform: rotate(-10deg) scale(1.04);
  }
}

@keyframes glassSweep {
  0%,
  48% {
    left: -54%;
    opacity: 0;
  }

  58% {
    opacity: 0.82;
  }

  76%,
  100% {
    left: 118%;
    opacity: 0;
  }
}

@keyframes scanLine {
  0%,
  100% {
    transform: translateX(-18px);
    opacity: 0.28;
  }

  50% {
    transform: translateX(12px);
    opacity: 0.92;
  }
}

@keyframes stepGlow {
  0%,
  100% {
    top: 20px;
    opacity: 0.18;
  }

  45% {
    opacity: 0.95;
  }

  80% {
    top: calc(100% - 68px);
    opacity: 0.18;
  }
}

@media (max-width: 1180px) {
  .nav-shell,
  .hero-inner,
  .section,
  .site-footer {
    width: min(100% - 36px, 1040px);
  }

  .nav-links {
    gap: 2px;
  }

  .nav-links button {
    padding: 0 10px;
    font-size: 13px;
  }

  .hero-inner {
    grid-template-columns: 1fr;
  }

  .workflow-preview {
    width: min(760px, 100%);
    margin: 0 auto;
  }

  .creation-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .insight-section {
    grid-template-columns: 1fr;
  }

  .cta-section {
    grid-template-columns: 1fr;
  }

  .cta-actions {
    flex-wrap: wrap;
  }

  .cta-orbit {
    display: none;
  }
}

@media (max-width: 760px) {
  .site-header {
    position: static;
  }

  .nav-shell {
    flex-wrap: wrap;
    height: auto;
    padding: 14px 0;
  }

  .nav-links {
    order: 3;
    justify-content: flex-start;
    width: 100%;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .nav-links button {
    flex: 0 0 auto;
  }

  .hero-copy h1 {
    font-size: 44px;
  }

  .hero-description {
    font-size: 16px;
  }

  .value-tags,
  .preview-body,
  .creation-grid,
  .process-line,
  .file-grid,
  .site-footer {
    grid-template-columns: 1fr;
  }

  .workflow-preview {
    min-height: auto;
    padding: 18px;
  }

  .analysis-card {
    min-height: 360px;
  }

  .workflow-steps::before,
  .process-line::before,
  .process-line::after {
    display: none;
  }

  .process-line {
    gap: 18px;
  }

  .cta-section {
    padding: 24px;
  }

  .cta-actions,
  .hero-actions {
    display: grid;
    grid-template-columns: 1fr;
  }

  .primary-button,
  .secondary-button,
  .cta-primary,
  .cta-secondary {
    width: 100%;
  }

  .site-footer article {
    border-right: 0;
    border-bottom: 1px solid rgba(109, 93, 251, 0.09);
  }

  .site-footer article:last-child {
    border-bottom: 0;
  }
}
</style>
