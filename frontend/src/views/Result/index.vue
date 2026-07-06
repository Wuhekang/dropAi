<template>
  <main class="page-shell result-page">
    <nav class="top-nav">
      <button class="brand" type="button" @click="router.push('/')">
        <span class="brand-mark">D</span>
        <span>DropAI</span>
      </button>
      <div class="nav-links">
        <button type="button" @click="router.push('/new-project')">Builder</button>
        <button type="button" @click="router.push('/dashboard')">Dashboard</button>
        <button class="primary-button" type="button" :disabled="!zipFile" @click="download(zipFile)">Download</button>
      </div>
    </nav>

    <header class="result-head">
      <div>
        <span class="eyebrow">Result</span>
        <h1>{{ projectName }}</h1>
      </div>
      <button class="primary-button" type="button" :disabled="!zipFile" @click="download(zipFile)">Download ZIP</button>
    </header>

    <section class="viewer-panel panel">
      <ModelViewer3D :project="demoProject" />
    </section>

    <section class="downloads">
      <article class="product-card download-card">
        <span>CAD drawings</span>
        <strong>{{ cadFiles.length || '--' }}</strong>
        <button class="ghost-button" type="button" :disabled="!cadFiles[0]" @click="download(cadFiles[0])">Download CAD</button>
      </article>
      <article class="product-card download-card">
        <span>Thesis document</span>
        <strong>{{ paperFiles.length || '--' }}</strong>
        <button class="ghost-button" type="button" :disabled="!paperFiles[0]" @click="download(paperFiles[0])">Download Paper</button>
      </article>
      <article class="product-card download-card">
        <span>Complete package</span>
        <strong>{{ zipFile ? 'ZIP' : '--' }}</strong>
        <button class="ghost-button" type="button" :disabled="!zipFile" @click="download(zipFile)">Download ZIP</button>
      </article>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ModelViewer3D from '../../components/ModelViewer3D.vue'
import { downloadArtifact, getMyDocuments } from '../../api/rewrite'

const router = useRouter()
const route = useRoute()
const documents = ref([])
const projectName = computed(() => route.query.name || documents.value[0]?.projectName || 'DropAI Engineering Project')

const cadFiles = computed(() => documents.value.filter(file => /\.(dxf|svg|png)$/i.test(file.fileName || '') || file.fileType === 'cad'))
const paperFiles = computed(() => documents.value.filter(file => /\.(docx|pdf)$/i.test(file.fileName || '') || ['docx', 'pdf'].includes(file.fileType)))
const zipFile = computed(() => documents.value.find(file => /\.(zip)$/i.test(file.fileName || '') || file.fileType === 'zip' || file.packageUrl))

const demoProject = computed(() => ({
  projectTitle: projectName.value,
  equipmentName: 'Generated assembly',
  designType: 'Parametric system',
  totalLength: 4200,
  totalWidth: 1800,
  totalHeight: 2600
}))

async function loadDocuments() {
  const result = await getMyDocuments({ pageNum: 1, pageSize: 20 })
  documents.value = result?.list || []
}

async function download(file) {
  if (!file) return
  try {
    const blob = await downloadArtifact(file.downloadUrl || file.packageUrl)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.fileName || 'dropai-package.zip'
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(error.message || 'Download failed.')
  }
}

onMounted(loadDocuments)
</script>

<style scoped>
.brand {
  border: 0;
  background: transparent;
  cursor: pointer;
}

.result-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.result-head h1 {
  max-width: 820px;
  margin: 0;
  font-size: clamp(36px, 6vw, 64px);
  line-height: 1;
}

.viewer-panel {
  min-height: 620px;
  overflow: hidden;
}

.viewer-panel :deep(.model-viewer) {
  min-height: 620px;
  border-radius: var(--radius);
}

.downloads {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.download-card {
  display: grid;
  gap: 14px;
  padding: 18px;
}

.download-card span {
  color: var(--cyan);
  font-size: 12px;
  font-weight: 720;
}

.download-card strong {
  font-size: 34px;
}

@media (max-width: 860px) {
  .result-head,
  .downloads {
    align-items: flex-start;
    grid-template-columns: 1fr;
  }

  .result-head {
    flex-direction: column;
  }

  .viewer-panel,
  .viewer-panel :deep(.model-viewer) {
    min-height: 430px;
  }
}
</style>
