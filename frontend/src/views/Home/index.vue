<template>
  <main class="page-shell home-page">
    <nav class="top-nav">
      <button class="brand" type="button" @click="router.push('/')">
        <span class="brand-mark">D</span>
        <span>DropAI</span>
      </button>
      <div class="nav-links">
        <button type="button" @click="router.push('/new-project')">项目生成</button>
        <button type="button" @click="router.push('/dashboard')">控制台</button>
        <button type="button" @click="router.push('/rewrite')">论文优化</button>
        <button class="primary-button" type="button" @click="router.push('/new-project')">生成项目</button>
      </div>
    </nav>

    <section class="hero-section">
      <span class="eyebrow">AI 工程系统</span>
      <h1 class="hero-title">把任务书生成完整工程成果系统</h1>
      <p class="hero-copy">
        DropAI 将任务书转化为 3D 模型、CAD 图纸和论文文档一致联动的工程成果包。
      </p>
      <button class="primary-button hero-cta" type="button" @click="router.push('/new-project')">生成项目</button>
    </section>

    <section class="workspace-section panel">
      <div class="input-card">
        <span class="status-pill"><span class="status-dot"></span>输入工作区</span>
        <div class="drop-zone">
          <strong>上传任务书</strong>
          <span>{{ selectedFile?.name || '支持 DOCX、PDF、TXT、Markdown' }}</span>
          <el-upload action="" :auto-upload="false" :show-file-list="false" accept=".docx,.pdf,.txt,.md" :on-change="selectFile">
            <button class="ghost-button" type="button">选择文件</button>
          </el-upload>
        </div>
        <textarea v-model="taskText" class="text-input" placeholder="也可以直接粘贴任务书要求..."></textarea>
      </div>

      <div class="output-stage">
        <div class="stage-head">
          <span class="status-pill"><span class="status-dot"></span>输出预览</span>
          <span class="tiny">3D + CAD + 论文</span>
        </div>
        <div class="model-frame">
          <ModelViewer3D :project="demoProject" />
        </div>
        <div class="preview-row">
          <article class="product-card mini-preview">
            <span>CAD 预览</span>
            <strong>总装图纸</strong>
            <div class="cad-lines">
              <i></i><i></i><i></i><i></i>
            </div>
          </article>
          <article class="product-card mini-preview paper-preview">
            <span>论文预览</span>
            <strong>设计说明书</strong>
            <p>摘要、计算、图纸说明、结论同步生成。</p>
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

    <footer class="footer">DropAI 工程成果生成平台</footer>
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
  projectTitle: '参数化工程系统',
  equipmentName: '机械装配体',
  designType: '结构设计',
  totalLength: 4200,
  totalWidth: 1800,
  totalHeight: 2600
}

const features = [
  { kicker: '结构', title: '理解任务书的模型生成', copy: '识别设计目标和关键参数，生成清晰一致的 3D 结构。' },
  { kicker: '图纸', title: '围绕 CAD 交付组织成果', copy: '以同一套参数生成图纸预览和可下载文件。' },
  { kicker: '论文', title: '论文与计算同步输出', copy: '围绕计算、材料、BOM 和设计依据生成文档。' }
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
  padding: 28px 0 46px;
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
  font-size: 21px;
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
