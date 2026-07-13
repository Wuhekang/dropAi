<template>
  <main class="writing-page">
    <aside class="side panel">
      <button class="brand" type="button" @click="router.push('/dashboard')">
        <span class="brand-mark">D</span><span>DropAI</span>
      </button>
      <el-steps direction="vertical" :active="activeStep" finish-status="success">
        <el-step title="基础信息" />
        <el-step title="提纲编辑" />
        <el-step title="章节图表" />
        <el-step title="联网文献" />
        <el-step title="生成下载" />
      </el-steps>
    </aside>

    <section class="main">
      <header class="hero-panel panel">
        <div>
          <span class="eyebrow">Pure Writing</span>
          <h1>纯文字稿生成</h1>
          <p>摘要、多章节正文、图表、表格、真实联网参考文献和 DOCX 导出一次串起来。</p>
        </div>
        <div class="provider-box">
          <strong>联网状态</strong>
          <span>{{ providerText }}</span>
          <button class="ghost-button" type="button" @click="loadProviderStatus">刷新</button>
        </div>
      </header>

      <section class="panel block">
        <div class="block-head">
          <div>
            <h2>1. 基础信息</h2>
            <p>章节数量由下方动态章节列表自动计算。</p>
          </div>
          <button class="primary-button" type="button" :disabled="saving" @click="createOrUpdateProject">保存项目</button>
        </div>
        <el-form label-position="top" class="grid-form">
          <el-form-item label="文章题目">
            <el-input v-model="form.title" placeholder="人工智能时代职业院校学生就业能力提升路径研究" />
          </el-form-item>
          <el-form-item label="专业或研究方向">
            <el-input v-model="form.major" placeholder="职业教育 / 就业能力 / 人才培养" />
          </el-form-item>
          <el-form-item label="文稿类型">
            <el-select v-model="form.documentType">
              <el-option v-for="item in documentTypes" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标总字数">
            <el-input-number v-model="form.targetWordCount" :min="3000" :max="30000" :step="500" />
          </el-form-item>
          <el-form-item label="摘要字数">
            <el-input-number v-model="form.abstractWordCount" :min="150" :max="1200" :step="50" />
          </el-form-item>
          <el-form-item label="关键词数量">
            <el-input-number v-model="form.keywordCount" :min="3" :max="8" />
          </el-form-item>
          <el-form-item label="&#20013;&#25991;&#21442;&#32771;&#25991;&#29486;&#25968;&#37327;">
            <el-input-number v-model="form.chineseReferenceCount" :min="0" :max="50" />
          </el-form-item>
          <el-form-item label="&#33521;&#25991;&#21442;&#32771;&#25991;&#29486;&#25968;&#37327;">
            <el-input-number v-model="form.englishReferenceCount" :min="0" :max="50" />
          </el-form-item>
          <el-form-item label="&#21442;&#32771;&#25991;&#29486;&#24635;&#25968;">
            <el-input :model-value="referenceTotal" readonly />
          </el-form-item>
          <el-form-item label="年份范围">
            <div class="inline">
              <el-input-number v-model="form.yearStart" :min="1990" :max="2030" />
              <span>至</span>
              <el-input-number v-model="form.yearEnd" :min="1990" :max="2030" />
            </div>
          </el-form-item>
          <el-form-item label="写作要求">
            <el-input v-model="form.requirements" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="选项">
            <div class="checks">
              <el-checkbox v-model="form.generateEnglishAbstract">英文摘要</el-checkbox>
              <el-checkbox v-model="form.generateToc">目录</el-checkbox>
              <el-checkbox v-model="form.skipReferences">本文不使用参考文献</el-checkbox>
            </div>
          </el-form-item>
        </el-form>
      </section>

      <section class="panel block">
        <div class="block-head">
          <div>
            <h2>2. 动态章节与图表配置</h2>
            <p>添加、删除、上移、下移后会自动重新编号；每章可以单独选择是否需要图片或表格。</p>
          </div>
          <div class="actions">
            <button class="ghost-button" type="button" :disabled="!projectId" @click="generateOutline">生成/刷新提纲</button>
            <button class="primary-button" type="button" :disabled="!projectId" @click="addChapter">＋ 添加章节</button>
          </div>
        </div>

        <el-table :data="chapters" row-key="id" class="chapter-table">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="chapter-detail">
                <div class="detail-grid">
                  <section>
                    <div class="mini-head">
                      <strong>二级小节</strong>
                      <button class="ghost-button small" type="button" @click="addSection(row)">添加小节</button>
                    </div>
                    <div v-for="section in row.sections || []" :key="section.id" class="edit-row">
                      <el-input v-model="section.title" @change="saveSection(section)" />
                      <el-input-number v-model="section.target_word_count" :min="100" :step="50" @change="saveSection(section)" />
                      <button class="ghost-button small" type="button" @click="removeSection(section)">删除</button>
                    </div>
                  </section>

                  <section>
                    <div class="mini-head">
                      <strong>图配置</strong>
                      <button class="ghost-button small" type="button" @click="addChart(row)">添加图</button>
                    </div>
                    <article v-for="chart in row.charts || []" :key="chart.id" class="config-card">
                      <div class="config-line">
                        <span>图{{ chart.chart_no }}</span>
                        <el-input v-model="chart.title" @change="saveChart(chart)" />
                        <el-select v-model="chart.chart_type" @change="saveChart(chart)">
                          <el-option v-for="type in chartTypes" :key="type.value" :label="type.label" :value="type.value" />
                        </el-select>
                        <el-checkbox v-model="chart.use_secondary_axis" @change="saveChart(chart)">次轴</el-checkbox>
                        <button class="ghost-button small" type="button" @click="removeChart(chart)">删除</button>
                      </div>
                      <div class="series-table">
                        <div class="series-head">系列名称 | 图表类型 | 次坐标轴 | 单位 | 操作</div>
                        <div v-for="series in chart.series || []" :key="series.id" class="series-row">
                          <el-input v-model="series.series_name" @change="saveSeries(series)" />
                          <el-select v-model="series.chart_type" @change="saveSeries(series)">
                            <el-option v-for="type in chartTypes" :key="type.value" :label="type.label" :value="type.value" />
                          </el-select>
                          <el-checkbox v-model="series.use_secondary_axis" @change="saveSeries(series)" />
                          <el-input v-model="series.unit" @change="saveSeries(series)" />
                          <button class="ghost-button small" type="button" @click="removeSeries(series)">删</button>
                        </div>
                        <button class="ghost-button small" type="button" @click="addSeries(chart)">添加系列</button>
                      </div>
                    </article>
                  </section>

                  <section>
                    <div class="mini-head">
                      <strong>表配置</strong>
                      <button class="ghost-button small" type="button" @click="addTable(row)">添加表</button>
                    </div>
                    <div v-for="table in row.tables || []" :key="table.id" class="edit-row">
                      <span>表{{ table.table_no }}</span>
                      <el-input v-model="table.title" @change="saveTable(table)" />
                      <el-select v-model="table.table_type" @change="saveTable(table)">
                        <el-option label="指标统计表" value="INDICATOR_STAT" />
                        <el-option label="对比分析表" value="COMPARE" />
                        <el-option label="调查结果表" value="SURVEY" />
                        <el-option label="评价指标表" value="EVALUATION" />
                      </el-select>
                      <el-checkbox v-model="table.use_three_line_style" @change="saveTable(table)">三线表</el-checkbox>
                      <button class="ghost-button small" type="button" @click="removeTable(table)">删除</button>
                    </div>
                  </section>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="章节" min-width="220">
            <template #default="{ row }">
              <div class="chapter-name">
                <strong>第{{ row.chapter_no }}章</strong>
                <el-input v-model="row.title" @change="saveChapter(row)" />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="目标字数" width="130">
            <template #default="{ row }"><el-input-number v-model="row.target_word_count" :min="500" :step="100" @change="saveChapter(row)" /></template>
          </el-table-column>
          <el-table-column label="二级节数" width="120">
            <template #default="{ row }"><el-input-number v-model="row.section_count" :min="1" :max="8" @change="saveChapter(row)" /></template>
          </el-table-column>
          <el-table-column label="图片数量" width="120">
            <template #default="{ row }"><el-input-number v-model="row.image_count" :min="0" :max="5" @change="saveChapter(row)" /></template>
          </el-table-column>
          <el-table-column label="表格数量" width="120">
            <template #default="{ row }"><el-input-number v-model="row.table_count" :min="0" :max="5" @change="saveChapter(row)" /></template>
          </el-table-column>
          <el-table-column label="总图表" width="90">
            <template #default="{ row }">{{ Number(row.image_count || 0) + Number(row.table_count || 0) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="260">
            <template #default="{ $index, row }">
              <button class="ghost-button small" type="button" @click="moveChapter($index, -1)">上移</button>
              <button class="ghost-button small" type="button" @click="moveChapter($index, 1)">下移</button>
              <button class="ghost-button small" type="button" @click="copyPrevious($index)">复制上章</button>
              <button class="ghost-button small danger" type="button" @click="removeChapter(row)">删除</button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel block">
        <div class="block-head">
          <div>
            <h2>3. 联网检索参考文献</h2>
            <p>正式生成前默认必须检索真实文献；不会用普通模型虚构 DOI 或文献字段。</p>
          </div>
          <button class="primary-button" type="button" :disabled="!projectId || searching || form.skipReferences" @click="searchReferences">真实联网搜索</button>
        </div>
        <div class="stats">
          <span>Provider：{{ project.search_provider || providerText }}</span>
          <span>状态：{{ project.search_status || '未搜索' }}</span>
          <span>文献：{{ references.length }}</span>
          <span>已验证：{{ verifiedCount }}</span>
        </div>
        <el-table :data="references.slice(0, 12)" max-height="360">
          <el-table-column prop="title" label="标题" min-width="280" />
          <el-table-column prop="authors" label="作者" min-width="180" />
          <el-table-column prop="publication_year" label="年份" width="80" />
          <el-table-column prop="source_platform" label="来源" width="110" />
          <el-table-column prop="verification_status" label="验证" width="130" />
        </el-table>
      </section>

      <section class="panel block">
        <div class="block-head">
          <div>
            <h2>4. 生成进度与下载</h2>
            <p>{{ project.current_stage || '等待生成' }}</p>
          </div>
          <button class="primary-button" type="button" :disabled="!projectId || generating" @click="startGeneration">生成DOCX</button>
        </div>
        <el-progress :percentage="Number(project.progress || 0)" :status="project.status === 'FAILED' ? 'exception' : project.status === 'SUCCESS' ? 'success' : undefined" />
        <pre v-if="previewText" class="preview">{{ previewText }}</pre>
        <div class="files">
          <button v-for="file in files" :key="file.id || file.document_job_id" class="ghost-button" type="button" @click="downloadFile(file)">
            下载 {{ file.file_name || 'DOCX' }} · {{ formatSize(file.file_size) }}
          </button>
        </div>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  addWritingChapter,
  addWritingChart,
  addWritingChartSeries,
  addWritingSection,
  addWritingTable,
  createWritingProject,
  deleteWritingChapter,
  deleteWritingChart,
  deleteWritingChartSeries,
  deleteWritingSection,
  deleteWritingTable,
  downloadArtifact,
  generateWritingOutline,
  getWritingPreview,
  getWritingProgress,
  getWritingReferenceSearchStatus,
  reorderWritingChapters,
  searchWritingReferences,
  startWritingGeneration,
  updateWritingChapter,
  updateWritingChart,
  updateWritingChartSeries,
  updateWritingProject,
  updateWritingSection,
  updateWritingTable
} from '../../api/rewrite'

const router = useRouter()
const activeStep = ref(0)
const projectId = ref('')
const project = ref({})
const chapters = ref([])
const references = ref([])
const files = ref([])
const providerStatus = ref(null)
const saving = ref(false)
const searching = ref(false)
const generating = ref(false)
const previewText = ref('')
let timer = null
let chapterSaveTimer = null
let chapterSaveRunning = false
let pendingChapterSnapshot = null

const documentTypes = ['毕业论文初稿', '课程论文', '调研报告', '分析报告', '设计说明书', '实践报告', '自定义']
const chartTypes = [
  { label: '簇状柱形图', value: 'BAR' },
  { label: '堆积柱形图', value: 'STACKED_BAR' },
  { label: '条形图', value: 'HORIZONTAL_BAR' },
  { label: '折线图', value: 'LINE' },
  { label: '柱线组合图', value: 'COMBO' },
  { label: '双轴组合图', value: 'DUAL_COMBO' },
  { label: '饼图', value: 'PIE' },
  { label: '环形图', value: 'DONUT' },
  { label: '面积图', value: 'AREA' },
  { label: '散点图', value: 'SCATTER' },
  { label: '雷达图', value: 'RADAR' },
  { label: '漏斗图', value: 'FUNNEL' },
  { label: '直方图', value: 'HISTOGRAM' },
  { label: '箱线图', value: 'BOX' },
  { label: '趋势图', value: 'TREND' }
]

const form = ref({
  title: '人工智能时代职业院校学生就业能力提升路径研究',
  major: '职业教育',
  documentType: '毕业论文初稿',
  targetWordCount: 8000,
  abstractWordCount: 300,
  keywordCount: 4,
  referenceCount: 20,
  chineseReferenceCount: 14,
  englishReferenceCount: 6,
  yearStart: 2020,
  yearEnd: 2026,
  citationStyle: 'GB/T 7714',
  writingTone: '本科论文',
  generateEnglishAbstract: true,
  generateToc: true,
  skipReferences: false,
  requirements: ''
})

const providerText = computed(() => {
  const providers = providerStatus.value?.providers || []
  if (!providers.length) return '未检测'
  return providers.map(item => `${item.name}:${item.available ? '可用' : '不可用'}`).join(' / ')
})
const verifiedCount = computed(() => references.value.filter(item => ['VERIFIED', 'PARTIALLY_VERIFIED'].includes(item.verification_status)).length)
const referenceTotal = computed(() => Number(form.value.chineseReferenceCount || 0) + Number(form.value.englishReferenceCount || 0))

function syncState(data) {
  project.value = data || {}
  chapters.value = data?.chapters || []
  references.value = data?.references || []
  files.value = data?.files || []
  if (data?.id) projectId.value = data.id
}

async function createOrUpdateProject() {
  saving.value = true
  try {
    if (referenceTotal.value <= 0) {
      ElMessage.error('中文和英文参考文献数量不能同时为 0')
      return
    }
    const payload = { ...form.value, referenceCount: referenceTotal.value, chapters: chapters.value.length ? chapters.value : [] }
    const data = projectId.value ? await updateWritingProject(projectId.value, payload) : await createWritingProject(payload)
    syncState(data)
    activeStep.value = Math.max(activeStep.value, 1)
    ElMessage.success('项目已保存')
  } finally {
    saving.value = false
  }
}

async function generateOutline() {
  await flushChapterSave()
  syncState(await generateWritingOutline(projectId.value))
  activeStep.value = 2
}

async function addChapter() {
  syncState(await addWritingChapter(projectId.value, { title: '新增章节', sectionCount: 3, imageCount: 1, tableCount: 1 }))
}

async function saveChapter(row) {
  pendingChapterSnapshot = {
    id: row.id,
    title: row.title,
    targetWordCount: row.target_word_count,
    sectionCount: row.section_count,
    imageCount: row.image_count,
    tableCount: row.table_count,
    useReferences: row.use_references,
    defaultChartType: row.default_chart_type
  }
  clearTimeout(chapterSaveTimer)
  chapterSaveTimer = setTimeout(flushChapterSave, 800)
}

async function flushChapterSave() {
  clearTimeout(chapterSaveTimer)
  if (!pendingChapterSnapshot || chapterSaveRunning || !projectId.value) return
  chapterSaveRunning = true
  try {
    while (pendingChapterSnapshot) {
      const snapshot = pendingChapterSnapshot
      pendingChapterSnapshot = null
      syncState(await updateWritingChapter(projectId.value, snapshot.id, snapshot))
    }
  } finally {
    chapterSaveRunning = false
  }
}

async function removeChapter(row) {
  await ElMessageBox.confirm(`删除 ${row.title}？`, '确认删除')
  syncState(await deleteWritingChapter(projectId.value, row.id))
}

async function moveChapter(index, delta) {
  const target = index + delta
  if (target < 0 || target >= chapters.value.length) return
  const ids = chapters.value.map(item => item.id)
  const [moved] = ids.splice(index, 1)
  ids.splice(target, 0, moved)
  syncState(await reorderWritingChapters(projectId.value, ids))
}

async function copyPrevious(index) {
  if (index <= 0) return
  const prev = chapters.value[index - 1]
  const row = chapters.value[index]
  row.section_count = prev.section_count
  row.image_count = prev.image_count
  row.table_count = prev.table_count
  row.default_chart_type = prev.default_chart_type
  await saveChapter(row)
}

async function addSection(row) { syncState(await addWritingSection(projectId.value, row.id, { title: '新增小节' })) }
async function saveSection(section) { syncState(await updateWritingSection(projectId.value, section.id, { title: section.title, targetWordCount: section.target_word_count })) }
async function removeSection(section) { syncState(await deleteWritingSection(projectId.value, section.id)) }
async function addChart(row) { syncState(await addWritingChart(projectId.value, row.id, { title: '新增图表', chartType: row.default_chart_type || 'COMBO' })) }
async function saveChart(chart) { syncState(await updateWritingChart(projectId.value, chart.id, chart)) }
async function removeChart(chart) { syncState(await deleteWritingChart(projectId.value, chart.id)) }
async function addSeries(chart) { syncState(await addWritingChartSeries(projectId.value, chart.id, { seriesName: '新增系列', chartType: 'LINE' })) }
async function saveSeries(series) { syncState(await updateWritingChartSeries(projectId.value, series.id, series)) }
async function removeSeries(series) { syncState(await deleteWritingChartSeries(projectId.value, series.id)) }
async function addTable(row) { syncState(await addWritingTable(projectId.value, row.id, { title: '新增表格' })) }
async function saveTable(table) { syncState(await updateWritingTable(projectId.value, table.id, table)) }
async function removeTable(table) { syncState(await deleteWritingTable(projectId.value, table.id)) }

async function searchReferences() {
  await flushChapterSave()
  searching.value = true
  try {
    references.value = await searchWritingReferences(projectId.value) || []
    activeStep.value = 3
    ElMessage.success(`已检索 ${references.value.length} 条文献`)
  } finally {
    searching.value = false
  }
}

async function startGeneration() {
  await flushChapterSave()
  generating.value = true
  syncState(await startWritingGeneration(projectId.value))
  activeStep.value = 4
  startPolling()
}

function startPolling() {
  clearInterval(timer)
  timer = setInterval(async () => {
    const data = await getWritingProgress(projectId.value)
    syncState(data)
    if (['SUCCESS', 'FAILED'].includes(data.status)) {
      clearInterval(timer)
      generating.value = false
      previewText.value = await getWritingPreview(projectId.value)
    }
  }, 2500)
}

async function loadProviderStatus() {
  providerStatus.value = await getWritingReferenceSearchStatus()
}

async function downloadFile(file) {
  const blob = await downloadArtifact(file.download_url)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = file.file_name || 'writing.docx'
  a.click()
  URL.revokeObjectURL(url)
}

function formatSize(size) {
  const value = Number(size || 0)
  if (value > 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  if (value > 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${value} B`
}

onMounted(loadProviderStatus)
onUnmounted(() => {
  clearInterval(timer)
  clearTimeout(chapterSaveTimer)
})
</script>

<style scoped>
.writing-page {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 22px;
  width: min(1360px, calc(100% - 40px));
  margin: 0 auto;
  padding: 22px 0 60px;
}
.side {
  position: sticky;
  top: 22px;
  height: calc(100vh - 44px);
  padding: 18px;
}
.brand {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 24px;
  border: 0;
  background: transparent;
  cursor: pointer;
}
.main {
  display: grid;
  gap: 16px;
}
.hero-panel,
.block-head,
.actions,
.provider-box,
.inline,
.checks,
.mini-head,
.config-line,
.edit-row,
.series-row,
.stats,
.files {
  display: flex;
  gap: 12px;
  align-items: center;
}
.hero-panel,
.block-head {
  justify-content: space-between;
}
.hero-panel {
  padding: 24px;
}
.hero-panel h1 {
  margin: 6px 0 8px;
  font-size: clamp(34px, 4vw, 52px);
}
.provider-box {
  min-width: 260px;
  flex-direction: column;
  align-items: flex-start;
}
.block {
  padding: 20px;
}
.block h2 {
  margin: 0 0 6px;
  font-size: 24px;
}
.block p,
.provider-box span,
.stats span {
  margin: 0;
  color: var(--muted);
}
.grid-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 14px;
}
.grid-form :deep(.el-form-item:last-child),
.grid-form :deep(.el-form-item:nth-last-child(2)) {
  grid-column: span 3;
}
.chapter-name,
.series-table {
  display: grid;
  gap: 8px;
}
.chapter-table {
  width: 100%;
  overflow-x: auto;
}
.writing-page :deep(.el-input-number) {
  width: 136px;
  min-width: 136px;
  flex-shrink: 0;
}
.writing-page :deep(.el-input-number .el-input__wrapper) {
  overflow: visible;
}
.writing-page :deep(.el-input-number__increase),
.writing-page :deep(.el-input-number__decrease) {
  width: 32px;
  min-width: 32px;
  z-index: 2;
}
.writing-page :deep(.el-table__body-wrapper),
.writing-page :deep(.el-table__inner-wrapper) {
  overflow-x: auto;
}
.chapter-detail {
  padding: 16px;
  background: rgba(255,255,255,0.55);
}
.detail-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}
.config-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(108, 99, 255, 0.14);
  border-radius: 8px;
  background: rgba(255,255,255,0.72);
}
.config-line,
.edit-row,
.series-row {
  flex-wrap: wrap;
}
.series-head {
  color: var(--muted);
  font-size: 13px;
}
.small {
  padding: 7px 10px;
  font-size: 12px;
}
.danger {
  color: #d64c6f;
}
.preview {
  max-height: 340px;
  overflow: auto;
  padding: 14px;
  border-radius: 8px;
  background: rgba(255,255,255,0.7);
  white-space: pre-wrap;
  line-height: 1.65;
}
@media (max-width: 980px) {
  .writing-page,
  .grid-form {
    grid-template-columns: 1fr;
  }
  .side {
    position: static;
    height: auto;
  }
  .grid-form :deep(.el-form-item:last-child),
  .grid-form :deep(.el-form-item:nth-last-child(2)) {
    grid-column: span 1;
  }
}
</style>
