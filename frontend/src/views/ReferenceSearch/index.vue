<template>
  <main class="reference-page">
    <DocumentStudioChrome :current="1" :project-name="result?.topic || form.title || '新建文档项目'" :busy="searching || saving" description="建立统一参考文献库，并检查中英文文献是否满足当前项目目标。" />
    <nav class="top-nav page-nav">
      <button class="brand brand-button" type="button" @click="router.push('/dashboard')">
        <span class="brand-mark">D</span><span>Dokiai Academic</span>
      </button>
      <div class="step-track" aria-label="纯文字稿生成步骤">
        <span class="step active"><b>1</b> 文献处理</span>
        <i></i>
        <span class="step"><b>2</b> 文档设计中心</span>
        <i></i>
        <span class="step"><b>3</b> 正文生成</span>
      </div>
      <button class="ghost-button" type="button" @click="router.push('/dashboard')">返回工作台</button>
    </nav>

    <section class="hero">
      <span class="eyebrow">STEP 1 · REFERENCE LIBRARY</span>
      <h1>AI 智能文献搜索</h1>
      <p>输入论文主题，AI 自动分析检索关键词，联网查找近五年真实文献，并整理为 GB/T 7714 格式。</p>
      <div class="automation-note">
        <span class="spark">✦</span>
        <div><strong>全程自动完成</strong><small>主题分析 · 关键词生成 · 联网搜索 · 去重核验 · 格式化</small></div>
      </div>
    </section>

    <section v-if="!hasResults" class="search-card panel">
      <div class="section-heading">
        <div><span>输入论文信息</span><h2>从一个清晰的研究主题开始</h2></div>
        <em>近五年文献</em>
      </div>

      <el-form label-position="top" class="search-form" @submit.prevent>
        <el-form-item label="文献来源">
          <el-radio-group v-model="sourceMode" :disabled="searching">
            <el-radio-button value="ai">AI智能检索</el-radio-button>
            <el-radio-button value="upload">上传已有文献</el-radio-button>
            <el-radio-button value="mixed">混合模式（推荐）</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="论文题目" required>
          <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="请输入完整的论文题目" size="large" :disabled="searching" />
        </el-form-item>
        <el-form-item label="研究方向（可选）">
          <el-input v-model="form.major" maxlength="100" placeholder="例如：职业教育、人工智能、企业管理" size="large" :disabled="searching" />
        </el-form-item>
        <el-form-item label="专业类型" required>
          <el-select v-model="form.documentMode" size="large" :disabled="searching">
            <el-option label="普通纯文字稿" value="general" />
            <el-option label="环境 / 景观设计" value="environment" />
            <el-option label="视觉传达" value="visual_communication" />
            <el-option label="室内设计" value="interior_design" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.documentMode === 'environment'" label="项目地址" required>
          <el-input v-model="form.projectLocation" maxlength="300" placeholder="例如：广州市白云区萧岗地铁站周边" size="large" :disabled="searching" />
        </el-form-item>
        <div v-if="sourceMode !== 'upload'" class="count-grid">
          <el-form-item label="中文参考文献数量">
            <el-input-number v-model="form.chineseReferenceCount" :min="0" :max="50" :step="1" controls-position="right" :disabled="searching" />
          </el-form-item>
          <el-form-item label="英文参考文献数量">
            <el-input-number v-model="form.englishReferenceCount" :min="0" :max="50" :step="1" controls-position="right" :disabled="searching" />
          </el-form-item>
        </div>
        <el-form-item v-if="sourceMode !== 'ai'" label="上传参考文献文件">
          <el-upload v-model:file-list="referenceFiles" drag multiple :auto-upload="false" accept=".txt,.doc,.docx,.pdf,.ris,.bib,.csv,.xlsx">
            <strong>拖放文件到这里，或点击选择</strong><p>支持 txt、doc、docx、pdf、ris、bib、csv、xlsx，可多选</p>
          </el-upload>
        </el-form-item>
      </el-form>

      <div :class="['provider-status', providerState]">
        <span></span>
        <p>{{ providerMessage }}</p>
        <button v-if="providerState === 'warning'" class="ghost-button" type="button" @click="loadProviders">重新检查</button>
      </div>

      <button class="primary-button search-button" type="button" :disabled="!canSearch" @click="startSearch">
        <span v-if="searching" class="spinner"></span>
        {{ searching ? currentActionText : startActionText }}
      </button>

      <div v-if="searching" class="search-progress">
        <div class="progress-line"><span :style="{ width: `${progress}%` }"></span></div>
        <strong>{{ currentStatus }}</strong>
        <p>系统正在自动处理，请保持页面开启。</p>
      </div>
    </section>

    <template v-else>
      <section class="result-summary panel">
        <div class="summary-title">
          <span class="success-mark">✓</span>
          <div><small>{{ quotaSatisfied ? '检索完成' : '搜索源已耗尽' }}</small><h2>{{ result.topic }}</h2><p>{{ result.searchMessage || (result.researchDirection ? `研究方向：${result.researchDirection}` : '') }}</p></div>
        </div>
        <div class="metrics">
          <article><strong>{{ result.chineseCount }}</strong><span>中文文献</span></article>
          <article><strong>{{ result.englishCount }}</strong><span>英文文献</span></article>
          <article><strong>{{ result.verifiedCount }}</strong><span>已验证</span></article>
          <article><strong>{{ result.yearFrom }}–{{ result.yearTo }}</strong><span>文献年份</span></article>
        </div>
      </section>

      <section class="library panel">
        <div class="library-head">
          <div><span>REFERENCE LIBRARY</span><h2>参考文献库</h2><p>AI 搜索与手动添加的文献统一保存，并转换为 GB/T 7714 格式。</p></div>
          <div class="library-tools"><b>共 {{ references.length }} 篇</b><button class="ghost-button" type="button" @click="manualDialog=true">＋ 手动添加文献</button></div>
        </div>

        <div v-if="sourceMode === 'upload'" :class="['quota-card', { complete: quotaSatisfied }]">
          <span>上传已有文献模式不限制中文或英文篇数</span><strong>{{ references.length > 0 ? `已解析 ${references.length} 篇，可以继续` : '请至少上传一篇文献' }}</strong>
        </div>
        <div v-else :class="['quota-card', { complete: quotaSatisfied }]">
          <span>目标：中文 {{ targetChinese }} 篇 / 英文 {{ targetEnglish }} 篇</span>
          <span>当前：中文 {{ currentChinese }} 篇 / 英文 {{ currentEnglish }} 篇</span>
          <strong>{{ quotaSatisfied ? '数量已满足，可以继续' : `缺少：中文 ${missingChinese} 篇 / 英文 ${missingEnglish} 篇` }}</strong>
        </div>

        <div v-if="references.length" class="reference-list">
          <article v-for="(item, index) in references" :key="item.id || index" class="reference-item">
            <span class="reference-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <div class="reference-content">
              <div class="reference-topline">
                <span :class="['language-tag', languageOf(item).toLowerCase()]">{{ languageOf(item) === 'ZH' ? '中文' : '英文' }}</span>
                <span class="verified-tag">{{ item.verification_status === 'MANUAL' ? '手动添加' : '✓ 已核验' }}</span>
                <span>{{ item.publication_year || '年份未知' }}</span>
              </div>
              <h3>{{ item.title }}</h3>
              <p class="metadata">{{ item.authors || '作者信息缺失' }} · {{ item.journal_or_publisher || item.source_platform || '来源信息缺失' }}</p>
              <a v-if="item.doi || item.url || item.source_url" :href="externalUrl(item)" target="_blank" rel="noopener noreferrer">
                {{ item.doi ? `DOI: ${item.doi}` : (item.url || item.source_url) }}
              </a>
              <div class="citation"><span>GB/T 7714</span><p>{{ item.formatted_text || fallbackCitation(item, index) }}</p></div>
            </div>
          </article>
        </div>
        <div v-else class="empty-state">未找到符合条件的文献，请稍后重新提交检索。</div>
      </section>

      <footer class="result-actions panel">
        <div><strong>{{ librarySaved ? '文献库已保存' : '检索结果已自动保存在当前项目中' }}</strong><span>项目 ID：{{ projectId }}</span></div>
        <div class="action-buttons">
          <button class="ghost-button save-button" type="button" :disabled="saving || librarySaved || !quotaSatisfied" @click="saveLibrary">
            {{ librarySaved ? '已保存文献库' : '保存文献库' }}
          </button>
          <button class="primary-button" type="button" :disabled="saving || !quotaSatisfied" @click="goToOutline">下一步：文档设计中心 →</button>
        </div>
      </footer>

      <el-dialog v-model="manualDialog" title="手动添加参考文献" width="min(620px,92vw)" :close-on-click-modal="false">
        <el-tabs v-model="manualMode">
          <el-tab-pane label="表单填写" name="form">
            <el-form label-position="top">
              <div class="manual-grid">
                <el-form-item label="文献语言" required><el-select v-model="manualForm.language"><el-option label="中文" value="ZH"/><el-option label="英文" value="EN"/></el-select></el-form-item>
                <el-form-item label="年份" required><el-input-number v-model="manualForm.year" :min="1900" :max="currentYear"/></el-form-item>
              </div>
              <el-form-item label="标题" required><el-input v-model="manualForm.title" maxlength="500"/></el-form-item>
              <el-form-item label="作者" required><el-input v-model="manualForm.authors" placeholder="多位作者请用逗号或分号分隔"/></el-form-item>
              <el-form-item label="来源（期刊/出版社）" required><el-input v-model="manualForm.source"/></el-form-item>
              <div class="manual-grid"><el-form-item label="DOI"><el-input v-model="manualForm.doi"/></el-form-item><el-form-item label="URL"><el-input v-model="manualForm.url"/></el-form-item></div>
            </el-form>
          </el-tab-pane>
          <el-tab-pane label="粘贴完整文本" name="paste">
            <el-input v-model="manualText" type="textarea" :rows="8" maxlength="3000" show-word-limit placeholder="粘贴一条完整参考文献，AI 将提取字段并生成 GB/T 7714 格式"/>
            <p class="parse-note">此方式会调用 AI 解析；系统不会补造原文中不存在的字段。</p>
          </el-tab-pane>
        </el-tabs>
        <template #footer><button class="ghost-button" type="button" @click="manualDialog=false">取消</button> <button class="primary-button" type="button" :disabled="manualSaving" @click="submitManualReference">{{ manualSaving ? '正在处理…' : (manualMode==='paste' ? 'AI解析并添加' : '添加到文献库') }}</button></template>
      </el-dialog>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import DocumentStudioChrome from '../../components/DocumentStudioChrome.vue'
import { addWritingManualReference, createWritingProject, generateWritingV2Outline, getWritingReferenceProviders, importWritingReferences, saveWritingReferenceLibrary, startAiReferenceSearch } from '../../api/rewrite'

const router = useRouter()
const currentYear = new Date().getFullYear()
const form = ref({ title: '', major: '', documentMode: 'general', projectLocation: '', chineseReferenceCount: 10, englishReferenceCount: 10 })
const sourceMode = ref('mixed')
const referenceFiles = ref([])
const projectId = ref('')
const result = ref(null)
const searching = ref(false)
const saving = ref(false)
const librarySaved = ref(false)
const manualDialog = ref(false)
const manualMode = ref('form')
const manualSaving = ref(false)
const manualText = ref('')
const manualForm = ref({ language: 'ZH', title: '', authors: '', year: currentYear, source: '', doi: '', url: '' })
const progress = ref(0)
const currentStatus = ref('AI 正在分析论文主题…')
const providerState = ref('checking')
const providerMessage = ref('正在检查联网文献服务…')
let progressTimer = null

const references = computed(() => result.value?.references || [])
const hasResults = computed(() => Boolean(result.value))
const canSearch = computed(() => !searching.value && form.value.title.trim()
  && form.value.documentMode && (form.value.documentMode !== 'environment' || form.value.projectLocation.trim())
  && (sourceMode.value === 'upload' || Number(form.value.chineseReferenceCount || 0) + Number(form.value.englishReferenceCount || 0) > 0)
  && (sourceMode.value === 'ai' || referenceFiles.value.length > 0))
const startActionText = computed(() => sourceMode.value === 'ai' ? '开始 AI 检索' : sourceMode.value === 'upload' ? '上传并解析文献' : '上传并智能补充')
const currentActionText = computed(() => sourceMode.value === 'upload' ? 'AI 正在解析文件…' : 'AI 正在处理文献…')
const quotaSatisfied = computed(() => sourceMode.value === 'upload' ? references.value.length > 0 : (Boolean(result.value?.quotaSatisfied)
  && Number(result.value?.chineseCount || 0) >= Number(result.value?.targetChineseCount || form.value.chineseReferenceCount)
  && Number(result.value?.englishCount || 0) >= Number(result.value?.targetEnglishCount || form.value.englishReferenceCount)))
const targetChinese = computed(() => Number(result.value?.targetChineseCount ?? form.value.chineseReferenceCount))
const targetEnglish = computed(() => Number(result.value?.targetEnglishCount ?? form.value.englishReferenceCount))
const currentChinese = computed(() => Number(result.value?.chineseCount || 0))
const currentEnglish = computed(() => Number(result.value?.englishCount || 0))
const missingChinese = computed(() => Math.max(0, targetChinese.value - currentChinese.value))
const missingEnglish = computed(() => Math.max(0, targetEnglish.value - currentEnglish.value))

const progressStages = [
  [12, 'AI 正在分析论文主题…'],
  [28, '正在自动生成检索关键词…'],
  [48, '正在联网搜索近五年文献…'],
  [68, '正在去重并核验文献信息…'],
  [84, '正在转换为 GB/T 7714 格式…']
]

function beginProgress() {
  progress.value = 8
  currentStatus.value = progressStages[0][1]
  progressTimer = setInterval(() => {
    const next = progressStages.find(([value]) => value > progress.value)
    if (next) {
      progress.value = next[0]
      currentStatus.value = next[1]
    } else if (progress.value < 94) {
      progress.value += 1
    }
  }, 900)
}

async function loadProviders() {
  providerState.value = 'checking'
  providerMessage.value = '正在检查联网文献服务…'
  try {
    const providers = await getWritingReferenceProviders()
    const available = Array.isArray(providers) ? providers.filter(item => item.available) : []
    if (available.length) {
      providerState.value = 'ready'
      providerMessage.value = `联网文献服务已就绪：${available.map(item => item.provider || item.name).join('、')}`
      return
    }
    providerState.value = 'warning'
    providerMessage.value = '当前暂无可用联网服务，提交检索时将再次检测。'
  } catch (error) {
    providerState.value = 'warning'
    providerMessage.value = '联网服务状态暂不可检测，提交时将自动检查'
  }
}

async function startSearch() {
  if (!form.value.title.trim()) return ElMessage.warning('请输入论文题目')
  searching.value = true
  beginProgress()
  try {
    const project = await createWritingProject({
      title: form.value.title.trim(),
      major: form.value.major.trim(),
      documentType: '纯文字稿',
      chineseReferenceCount: form.value.chineseReferenceCount,
      englishReferenceCount: form.value.englishReferenceCount,
      referenceCount: Number(form.value.chineseReferenceCount) + Number(form.value.englishReferenceCount),
      yearStart: currentYear - 4,
      yearEnd: currentYear,
      citationStyle: 'GB/T 7714',
      referenceMode: sourceMode.value.toUpperCase()
    })
    projectId.value = project.id
    currentStatus.value = '文献检索与文档设计正在并行执行…'
    const designTask = generateWritingV2Outline(projectId.value, {
      documentMode: form.value.documentMode,
      projectLocation: form.value.projectLocation.trim()
    })
    let uploaded = null
    if (sourceMode.value !== 'ai') {
      currentStatus.value = '正在上传并解析已有参考文献…'
      uploaded = await importWritingReferences(projectId.value, referenceFiles.value)
      result.value = {
        ...uploaded,
        topic: form.value.title.trim(),
        researchDirection: form.value.major.trim(),
        yearFrom: currentYear - 4,
        yearTo: currentYear,
        verifiedCount: (uploaded.references || []).filter(item => item.verification_status !== 'PENDING').length
      }
    }
    if (sourceMode.value === 'ai' || (sourceMode.value === 'mixed' && !uploaded?.quotaSatisfied)) {
      currentStatus.value = sourceMode.value === 'mixed' ? '上传文献数量不足，AI 正在补充缺少部分…' : 'AI 正在联网检索文献…'
      result.value = await startAiReferenceSearch(projectId.value, {
        chineseReferenceCount: form.value.chineseReferenceCount,
        englishReferenceCount: form.value.englishReferenceCount,
        yearFrom: currentYear - 4,
        yearTo: currentYear
      })
    }
    await designTask
    progress.value = 100
    sessionStorage.setItem('dropai_writing_project_id', projectId.value)
    ElMessage.success(`AI 检索完成，共找到 ${references.value.length} 篇文献`)
  } finally {
    searching.value = false
    clearInterval(progressTimer)
  }
}

async function saveLibrary() {
  if (!projectId.value) return
  saving.value = true
  try {
    await saveWritingReferenceLibrary(projectId.value)
    librarySaved.value = true
    sessionStorage.setItem('dropai_writing_project_id', projectId.value)
    ElMessage.success('文献库已保存')
  } finally {
    saving.value = false
  }
}

async function submitManualReference() {
  if (manualMode.value === 'paste' && !manualText.value.trim()) return ElMessage.warning('请粘贴完整参考文献文本')
  if (manualMode.value === 'form' && (!manualForm.value.title.trim() || !manualForm.value.authors.trim() || !manualForm.value.source.trim())) {
    return ElMessage.warning('请补全标题、作者、年份和来源')
  }
  manualSaving.value = true
  try {
    const payload = manualMode.value === 'paste' ? { rawText: manualText.value.trim() } : { ...manualForm.value }
    const updated = await addWritingManualReference(projectId.value, payload)
    result.value = { ...result.value, ...updated }
    librarySaved.value = false
    manualDialog.value = false
    manualText.value = ''
    manualForm.value = { language: 'ZH', title: '', authors: '', year: currentYear, source: '', doi: '', url: '' }
    ElMessage.success('参考文献已添加并转换为 GB/T 7714 格式')
  } finally {
    manualSaving.value = false
  }
}

async function goToOutline() {
  if (!librarySaved.value) await saveLibrary()
  router.push('/writing-generator/outline')
}

function languageOf(item) {
  if (item.language) return String(item.language).toUpperCase()
  return /[\u4e00-\u9fff]/.test(item.title || '') ? 'ZH' : 'EN'
}

function externalUrl(item) {
  if (item.doi) return `https://doi.org/${String(item.doi).replace(/^https?:\/\/(dx\.)?doi\.org\//i, '')}`
  return item.url || item.source_url
}

function fallbackCitation(item, index) {
  return `[${index + 1}] ${item.authors || ''}. ${item.title || ''}[J]. ${item.journal_or_publisher || ''}, ${item.publication_year || ''}.`
}

onMounted(loadProviders)
onUnmounted(() => clearInterval(progressTimer))
</script>

<style scoped>
.reference-page{width:min(1120px,calc(100% - 40px));margin:0 auto;padding:22px 0 60px}.page-nav{position:relative;top:auto}.brand-button{border:0;background:transparent;cursor:pointer}.step-track{display:flex;align-items:center;gap:12px;color:var(--muted-2);font-size:13px}.step{display:flex;align-items:center;gap:7px;white-space:nowrap}.step b{display:grid;place-items:center;width:25px;height:25px;border-radius:50%;background:#eef2ff;color:#8991a8}.step.active{color:var(--primary);font-weight:700}.step.active b{color:#fff;background:var(--primary-gradient)}.step-track i{width:34px;height:1px;background:#dde3f0}.hero{text-align:center;padding:54px 16px 36px}.eyebrow,.library-head span{font-size:12px;font-weight:800;letter-spacing:.16em;color:var(--primary)}.hero h1{margin:13px 0 14px;font-size:clamp(34px,5vw,54px);letter-spacing:-.045em}.hero>p{max-width:680px;margin:0 auto;color:var(--muted);font-size:17px;line-height:1.8}.automation-note{display:inline-flex;align-items:center;gap:12px;margin-top:24px;padding:11px 17px;border:1px solid rgba(108,99,255,.14);border-radius:999px;background:rgba(255,255,255,.68)}.spark{color:var(--primary);font-size:20px}.automation-note div{display:flex;align-items:center;gap:10px}.automation-note small{color:var(--muted)}.search-card{max-width:820px;margin:0 auto;padding:32px}.section-heading,.library-head,.result-actions,.summary-title,.reference-topline{display:flex;align-items:center}.section-heading,.library-head,.result-actions{justify-content:space-between;gap:20px}.section-heading span{color:var(--primary);font-size:13px;font-weight:700}.section-heading h2,.library-head h2{margin:5px 0 0}.section-heading em{padding:7px 12px;border-radius:999px;color:#178b65;background:#eafaf4;font-size:12px;font-style:normal}.search-form{margin-top:28px}.count-grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}.count-grid :deep(.el-input-number){width:100%}.provider-status{display:flex;align-items:center;gap:9px;margin:2px 0 14px;padding:10px 12px;border-radius:9px;background:#f7f8fc;color:var(--muted);font-size:13px}.provider-status span{width:8px;height:8px;border-radius:50%;background:#a7aec0}.provider-status p{flex:1;margin:0}.provider-status.ready{color:#147a59;background:#ecfaf5}.provider-status.ready span{background:#22c55e}.provider-status.warning{color:#9a6518;background:#fff8e8}.provider-status.warning span{background:#f59e0b}.provider-status.checking span{animation:pulse 1s ease-in-out infinite}.provider-status .ghost-button{padding:5px 8px}.search-button{width:100%;min-height:50px;margin-top:8px}.spinner{width:18px;height:18px;border:2px solid rgba(255,255,255,.4);border-top-color:#fff;border-radius:50%;animation:spin .8s linear infinite}.search-progress{margin-top:24px;text-align:center}.progress-line{height:6px;overflow:hidden;border-radius:99px;background:#edf0f7}.progress-line span{display:block;height:100%;border-radius:inherit;background:var(--primary-gradient);transition:width .6s ease}.search-progress strong{display:block;margin-top:16px}.search-progress p{margin:5px 0 0;color:var(--muted);font-size:13px}.result-summary{padding:28px 30px}.summary-title{gap:14px}.summary-title h2{margin:3px 0 4px;font-size:21px}.summary-title p,.summary-title small{margin:0;color:var(--muted)}.success-mark{display:grid;place-items:center;width:44px;height:44px;border-radius:14px;color:#fff;background:#22c55e;font-size:22px}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-top:24px}.metrics article{padding:18px;border-radius:12px;background:rgba(247,249,255,.9);text-align:center}.metrics strong{display:block;color:var(--primary);font-size:22px}.metrics span{color:var(--muted);font-size:12px}.library{margin-top:20px;padding:30px}.library-head p{margin:6px 0 0;color:var(--muted)}.library-head>b{padding:8px 13px;border-radius:999px;color:var(--primary);background:#f0efff;font-size:13px}.reference-list{margin-top:22px}.reference-item{display:grid;grid-template-columns:42px 1fr;gap:14px;padding:22px 0;border-top:1px solid var(--line-soft)}.reference-index{color:#a7aec0;font-weight:800}.reference-topline{gap:9px;color:var(--muted);font-size:12px}.language-tag,.verified-tag{padding:4px 8px;border-radius:6px}.language-tag{color:#4d55c7;background:#efefff}.language-tag.en{color:#a94c73;background:#fff0f6}.verified-tag{color:#13845e;background:#eafaf4}.reference-content h3{margin:10px 0 7px;font-size:17px;line-height:1.55}.metadata{margin:0;color:var(--muted);font-size:13px}.reference-content>a{display:inline-block;margin-top:8px;color:var(--primary);font-size:12px;word-break:break-all}.citation{display:grid;grid-template-columns:auto 1fr;gap:12px;margin-top:14px;padding:13px 15px;border-radius:9px;background:#f7f8fc}.citation span{color:var(--primary);font-size:11px;font-weight:800}.citation p{margin:0;color:#4a5568;font-size:13px;line-height:1.65}.empty-state{padding:48px;text-align:center;color:var(--muted)}.result-actions{position:sticky;bottom:18px;margin-top:20px;padding:18px 22px;background:rgba(255,255,255,.88);z-index:10}.result-actions>div:first-child{display:flex;flex-direction:column;gap:3px}.result-actions span{color:var(--muted);font-size:12px}.action-buttons{display:flex;gap:10px}.save-button{border-color:rgba(108,99,255,.2)}@keyframes spin{to{transform:rotate(360deg)}}@keyframes pulse{50%{opacity:.35}}@media(max-width:800px){.step-track{display:none}.count-grid,.metrics{grid-template-columns:1fr 1fr}.result-actions{align-items:stretch;flex-direction:column}.action-buttons>*{flex:1}}@media(max-width:560px){.reference-page{width:min(100% - 24px,1120px)}.search-card,.library,.result-summary{padding:22px}.metrics{grid-template-columns:1fr 1fr}.automation-note div{align-items:flex-start;flex-direction:column;gap:2px}.reference-item{grid-template-columns:1fr}.reference-index{display:none}.citation{grid-template-columns:1fr}.action-buttons{flex-direction:column}}
.library-tools{display:flex;align-items:center;gap:10px}.library-tools>b{padding:8px 13px;border-radius:999px;color:var(--primary);background:#f0efff;font-size:13px}.quota-card{display:flex;flex-wrap:wrap;gap:10px 22px;margin-top:20px;padding:14px 16px;border-radius:10px;color:#9a6518;background:#fff8e8;font-size:13px}.quota-card strong{margin-left:auto}.quota-card.complete{color:#147a59;background:#ecfaf5}.manual-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.manual-grid :deep(.el-select),.manual-grid :deep(.el-input-number){width:100%}.parse-note{color:var(--muted);font-size:13px}@media(max-width:560px){.library-tools,.quota-card{align-items:stretch;flex-direction:column}.quota-card strong{margin-left:0}.manual-grid{grid-template-columns:1fr}}
.reference-page{width:auto;margin:0;padding:22px 302px 60px 256px}.page-nav{display:none}.panel{border-radius:18px;box-shadow:0 12px 35px rgba(35,69,58,.05)}@media(max-width:1300px){.reference-page{padding-right:20px}}@media(max-width:900px){.reference-page{padding:0 12px 35px}.page-nav{display:none}}
.reference-page{--primary:#145b4d;--primary-gradient:linear-gradient(135deg,#6e4fff,#ff55b0);max-width:1920px;min-height:100vh;margin:0 auto;padding-left:280px;padding-right:320px;background:linear-gradient(45deg,#fbd7ea 0%,#f8eaf0 38%,#edf1f8 64%,#dcebff 100%);color:#173a33}.reference-page>.hero,.reference-page>.panel{max-width:1440px;margin-left:auto;margin-right:auto}.hero h1{background:linear-gradient(135deg,#6e4fff,#ff55b0);-webkit-background-clip:text;background-clip:text;color:transparent}.panel{border-color:rgba(20,91,77,.1);box-shadow:0 12px 35px rgba(20,91,77,.07)}.ghost-button,.reference-page :deep(.el-button:not(.el-button--primary)){border-color:#145b4d!important;color:#145b4d!important;background:transparent!important}.primary-button{background:linear-gradient(135deg,#6e4fff,#ff55b0)!important}@media(max-width:1300px){.reference-page{padding-right:40px}}
.reference-page{padding-left:260px;padding-right:300px;color:#252936}.reference-page>.hero,.reference-page>.panel{max-width:1500px}.eyebrow,.library-head span,.section-heading span{color:#6e4fff}.panel{border-color:rgba(110,79,255,.09);box-shadow:0 14px 42px rgba(61,53,104,.07)}.ghost-button,.reference-page :deep(.el-button:not(.el-button--primary)){border-color:#b9aaff!important;color:#6e4fff!important}.provider-status.ready,.quota-card.complete,.verified-tag{color:#145b4d;background:#eaf5f1}@media(max-width:1300px){.reference-page{padding-right:40px}}
</style>
