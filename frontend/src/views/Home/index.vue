<template>
  <main class="page-shell home-page">
    <nav class="top-nav">
      <button class="brand" type="button" @click="router.push('/')">
        <span class="brand-mark">D</span>
        <span>DropAI</span>
      </button>
      <div class="nav-links">
        <button type="button" @click="router.push('/new-project')">Builder</button>
        <button type="button" @click="router.push('/dashboard')">Dashboard</button>
        <button type="button" @click="router.push('/rewrite')">Writing</button>
        <button class="primary-button" type="button" @click="router.push('/new-project')">Generate Project</button>
      </div>
    </nav>

    <section class="hero-section">
      <span class="eyebrow">AI Engineering System</span>
      <h1 class="hero-title">Turn Task Documents into Complete Engineering Systems</h1>
      <p class="hero-copy">
        DropAI turns a task document into a coordinated 3D model, CAD drawings, and a thesis-ready engineering package.
      </p>
      <button class="primary-button hero-cta" type="button" @click="router.push('/new-project')">Generate Project</button>
    </section>

    <section class="workspace-section panel">
      <div class="input-card">
        <span class="status-pill"><span class="status-dot"></span>Input Workspace</span>
        <div class="drop-zone">
          <strong>Upload task document</strong>
          <span>{{ selectedFile?.name || 'DOCX, PDF, TXT or Markdown' }}</span>
          <el-upload action="" :auto-upload="false" :show-file-list="false" accept=".docx,.pdf,.txt,.md" :on-change="selectFile">
            <button class="ghost-button" type="button">Choose File</button>
          </el-upload>
        </div>
        <textarea v-model="taskText" class="text-input" placeholder="Paste the task requirements here..."></textarea>
      </div>

      <div class="output-stage">
        <div class="stage-head">
          <span class="status-pill"><span class="status-dot"></span>Output Showcase</span>
          <span class="tiny">3D + CAD + Paper</span>
        </div>
        <div class="model-frame">
          <ModelViewer3D :project="demoProject" />
        </div>
        <div class="preview-row">
          <article class="product-card mini-preview">
            <span>CAD Preview</span>
            <strong>Assembly drawing</strong>
            <div class="cad-lines">
              <i></i><i></i><i></i><i></i>
            </div>
          </article>
          <article class="product-card mini-preview paper-preview">
            <span>Paper Preview</span>
            <strong>Design thesis</strong>
            <p>Abstract, calculation, drawings, conclusion.</p>
          </article>
        </div>
      </div>
    </section>

    <section class="feature-section">
      <article v-for="feature in features" :key="feature.title" class="product-card feature-card">
        <span>{{ feature.kicker }}</span>
        <h3>{{ feature.title }}</h3>
        <p>{{ feature.copy }}</p>
      </article>
    </section>

    <footer class="footer">DropAI Engineering Studio</footer>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import ModelViewer3D from '../../components/ModelViewer3D.vue'

const router = useRouter()
const selectedFile = ref(null)
const taskText = ref('')

const demoProject = {
  projectTitle: 'Parametric engineering system',
  equipmentName: 'Mechanical assembly',
  designType: 'Structure design',
  totalLength: 4200,
  totalWidth: 1800,
  totalHeight: 2600
}

const features = [
  { kicker: 'Structure', title: 'Task-aware model generation', copy: 'Extracts engineering intent and turns it into a coherent 3D structure.' },
  { kicker: 'Drawing', title: 'CAD-first deliverables', copy: 'Creates drawing previews and downloadable artifacts around the same parameters.' },
  { kicker: 'Writing', title: 'Thesis system output', copy: 'Builds documents around calculations, materials, BOM, and design rationale.' }
]

function selectFile(file) {
  selectedFile.value = file
}
</script>

<style scoped>
.home-page {
  padding-bottom: 34px;
}

.brand {
  border: 0;
  background: transparent;
  cursor: pointer;
}

.hero-section {
  display: grid;
  justify-items: center;
  padding: 34px 0 52px;
  text-align: center;
}

.hero-copy {
  margin-bottom: 26px;
}

.hero-cta {
  min-width: 176px;
}

.workspace-section {
  display: grid;
  grid-template-columns: minmax(280px, 0.78fr) minmax(0, 1.22fr);
  gap: 18px;
  padding: 18px;
}

.input-card,
.output-stage {
  display: grid;
  align-content: start;
  gap: 14px;
}

.output-stage {
  min-width: 0;
}

.stage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.model-frame {
  min-height: 520px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.model-frame :deep(.model-viewer) {
  min-height: 520px;
  border-radius: var(--radius);
}

.preview-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.mini-preview {
  min-height: 150px;
  padding: 18px;
}

.mini-preview span,
.feature-card span {
  color: var(--cyan);
  font-size: 12px;
  font-weight: 720;
}

.mini-preview strong {
  display: block;
  margin-top: 8px;
  font-size: 22px;
}

.cad-lines {
  position: relative;
  height: 62px;
  margin-top: 18px;
  border: 1px solid rgba(0, 210, 255, 0.35);
  border-radius: var(--radius);
}

.cad-lines i {
  position: absolute;
  display: block;
  background: rgba(0, 210, 255, 0.48);
}

.cad-lines i:nth-child(1) { left: 18%; top: 14px; width: 56%; height: 1px; }
.cad-lines i:nth-child(2) { left: 28%; top: 38px; width: 44%; height: 1px; }
.cad-lines i:nth-child(3) { left: 24%; top: 12px; width: 1px; height: 38px; }
.cad-lines i:nth-child(4) { right: 24%; top: 12px; width: 1px; height: 38px; }

.paper-preview p {
  margin: 18px 0 0;
  color: var(--muted);
  line-height: 1.6;
}

.feature-section {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.feature-card {
  padding: 20px;
}

.feature-card h3 {
  margin: 10px 0 8px;
  font-size: 19px;
}

.feature-card p {
  margin: 0;
  color: var(--muted);
  line-height: 1.65;
}

.footer {
  padding: 30px 0 0;
  color: var(--muted-2);
  text-align: center;
}

@media (max-width: 960px) {
  .workspace-section,
  .feature-section {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .preview-row {
    grid-template-columns: 1fr;
  }

  .model-frame,
  .model-frame :deep(.model-viewer) {
    min-height: 390px;
  }
}
</style>
