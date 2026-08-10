<template>
  <main class="export-page">
    <DocumentStudioChrome :current="5" :assistant-items="assistantItems" />

    <header class="page-heading">
      <span>STEP 5 · EXPORT STUDIO</span>
      <h1>AI成果交付中心</h1>
      <p>检查最终成果、管理文档版本，并交付排版完整的 DOCX 文件。</p>
    </header>

    <section class="project-card panel">
      <div>
        <small>PROJECT DELIVERY</small>
        <h2>{{ projectName }}</h2>
        <p>{{ professionalType }} · {{ statusLabel }}</p>
      </div>
      <div class="project-meta">
        <span>完成时间<strong>{{ completedTime }}</strong></span>
        <span>当前版本<strong>{{ currentVersion }}</strong></span>
        <span>交付状态<strong :class="statusClass">{{ statusLabel }}</strong></span>
      </div>
    </section>

    <div class="delivery-layout">
      <div class="delivery-main">
        <section class="document-card panel">
          <div class="doc-cover"><b>W</b><span>DOCX</span></div>
          <div class="document-info">
            <small>FINAL DOCUMENT</small>
            <h2>{{ documentName }}</h2>
            <p>正文、图片、图注、表格与参考文献统一汇总。</p>
            <div class="content-tags"><span v-for="item in contents" :key="item">✓ {{ item }}</span></div>
            <div class="document-stats">
              <span><b>{{ wordCountLabel }}</b>字数</span>
              <span><b>{{ pageCountLabel }}</b>{{ pageCount ? '预计页数' : '页数' }}</span>
              <span><b>{{ referenceCount }}</b>参考文献</span>
              <span><b>{{ imageCount }}</b>图片</span>
            </div>
          </div>
          <button class="primary-action" type="button" :disabled="!downloadUrl" @click="download">下载 DOCX</button>
        </section>

        <section class="quality-card panel">
          <div class="section-heading">
            <div><small>QUALITY REPORT</small><h2>质量检查报告</h2></div>
            <button class="outline-action" type="button" :disabled="repairing" @click="autoRepair">{{ repairing ? '正在自动修复…' : '自动修复' }}</button>
          </div>
          <p class="quality-note">检查结果用于辅助交付，不会因格式问题阻断下载；需要时可启动自动规范。</p>
          <div class="quality-list">
            <article v-for="item in qualityChecks" :key="item.label" :class="item.state">
              <i>{{ item.state === 'passed' ? '✓' : item.state === 'fixing' ? '●' : '—' }}</i>
              <div><strong>{{ item.label }}</strong><span>{{ item.message }}</span></div>
              <em>{{ item.state === 'passed' ? '已通过' : item.state === 'fixing' ? '规范中' : '待检查' }}</em>
            </article>
          </div>
        </section>

        <section class="action-card panel">
          <div><small>DELIVERY ACTIONS</small><h2>继续处理成果</h2><p>预览当前交付文件，或根据最新内容生成一个新版本。</p></div>
          <div><button class="outline-action" type="button" :disabled="!downloadUrl" @click="preview">在线预览</button><button class="outline-action" type="button" @click="regenerate">重新生成</button><button class="outline-action" type="button" @click="newVersion">生成新版本</button></div>
        </section>
      </div>

      <aside class="delivery-side">
        <section class="assistant-card panel">
          <small>AI DELIVERY ASSISTANT</small><h2>AI交付助手</h2>
          <div class="assistant-score"><b>{{ passedCount }}/5</b><span>检查项已完成</span></div>
          <ul><li v-for="item in suggestions" :key="item"><i>✦</i>{{ item }}</li></ul>
        </section>
        <section class="version-card panel">
          <div class="section-heading"><div><small>VERSION HISTORY</small><h2>版本记录</h2></div></div>
          <div v-if="versions.length" class="version-list"><article v-for="(item,index) in versions" :key="item.id || item.download_url || index"><i>V1.{{ versions.length - index - 1 }}</i><div><strong>{{ item.file_name || item.name || documentName }}</strong><span>{{ formatTime(item.created_at || item.updated_at) }}</span></div><em>{{ index === 0 ? '当前' : '历史' }}</em></article></div>
          <div v-else class="empty-version"><i>◇</i><strong>尚无交付版本</strong><span>正文生成完成后，版本将在这里自动记录。</span></div>
        </section>
      </aside>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import DocumentStudioChrome from '../../components/DocumentStudioChrome.vue'
import { downloadArtifact, getWritingProgress, getWritingV2Flow, startWritingGeneration } from '../../api/rewrite'

const router = useRouter()
const projectId = ref(sessionStorage.getItem('dropai_writing_project_id') || '')
const flow = ref({})
const progress = ref({})
const repairing = ref(false)
const contents = ['正文', '图片', '图注', '表格', '参考文献']

const projectName = computed(() => flow.value.project_name || flow.value.project_title || flow.value.title || flow.value.topic || sessionStorage.getItem('writing_project_title') || '未命名项目')
const professionalType = computed(() => flow.value.professional_type || ({environment:'环境设计',visual_communication:'视觉传达',interior_design:'室内设计',general:'普通论文'})[flow.value.document_mode] || '专业类型待确认')
const status = computed(() => String(progress.value.status || flow.value.status || 'WAITING').toUpperCase())
const completed = computed(() => ['SUCCESS', 'COMPLETED', 'DONE'].includes(status.value))
const statusLabel = computed(() => completed.value ? '交付完成' : ['GENERATING', 'RUNNING', 'PROCESSING'].includes(status.value) ? '生成中' : status.value === 'FAILED' ? '生成失败' : '等待生成')
const statusClass = computed(() => completed.value ? 'success' : status.value === 'FAILED' ? 'failed' : 'pending')
const versions = computed(() => progress.value.files || progress.value.artifacts || flow.value.files || [])
const latestFile = computed(() => versions.value[0] || {})
const downloadUrl = computed(() => latestFile.value.download_url || progress.value.download_url || flow.value.download_url || '')
const documentName = computed(() => latestFile.value.file_name || latestFile.value.name || `${projectName.value.replace(/[\\/:*?\"<>|]/g, '_')}.docx`)
const currentVersion = computed(() => versions.value.length ? `V1.${Math.max(0, versions.value.length - 1)}` : '待生成')
const completedTime = computed(() => formatTime(latestFile.value.created_at || progress.value.completed_at || progress.value.finished_at || flow.value.completed_at))
const chapters = computed(() => flow.value.chapters || progress.value.chapters || [])
const sections = computed(() => chapters.value.flatMap(item => item.sections || []))
const inferredWords = computed(() => sections.value.reduce((sum,item) => sum + String(item.content || '').replace(/\s/g,'').length, 0))
const wordCount = computed(() => Number(progress.value.word_count || flow.value.generated_word_count || inferredWords.value || 0))
const pageCount = computed(() => Number(progress.value.page_count || flow.value.page_count || (wordCount.value ? Math.ceil(wordCount.value / 800) : 0)))
const wordCountLabel = computed(() => wordCount.value ? wordCount.value.toLocaleString() : '待统计')
const pageCountLabel = computed(() => pageCount.value || '待统计')
const referenceCount = computed(() => Number(flow.value.reference_count || flow.value.references?.length || 0))
const imageCount = computed(() => Number(flow.value.image_count || flow.value.materials?.length || 0))
const qualityChecks = computed(() => ['章节完整', 'GB/T 7714', '图片引用', '图表编号', 'DOCX 排版'].map(label => ({ label, state: repairing.value ? 'fixing' : completed.value ? 'passed' : 'pending', message: repairing.value ? '正在自动规范交付内容' : completed.value ? '已完成生成阶段检查' : '文档生成后自动检查' })))
const passedCount = computed(() => qualityChecks.value.filter(item => item.state === 'passed').length)
const suggestions = computed(() => completed.value ? ['建议在线预览后再下载最终文件。', '生成新版本不会覆盖当前版本记录。'] : ['当前尚无可交付文件，请先完成 AI 正文生成。', '质量问题将在交付前自动规范，不阻断流程。'])
const assistantItems = computed(() => [{label:'交付状态',value:statusLabel.value},{label:'当前版本',value:currentVersion.value},{label:'文档字数',value:wordCountLabel.value},{label:'质量检查',value:`${passedCount.value}/5`}])

function formatTime(value){ if(!value) return '尚未完成'; const date = new Date(value); return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN',{hour12:false}) }
async function load(){ if(!projectId.value) return; try{ const [flowData,progressData] = await Promise.all([getWritingV2Flow(projectId.value),getWritingProgress(projectId.value)]); flow.value=flowData || {}; progress.value=progressData || {} }catch(error){ ElMessage.warning(error?.responseData?.message || error?.message || '项目交付信息暂时无法读取') } }
async function getArtifact(){ if(!downloadUrl.value) throw new Error('DOCX 文件尚未就绪'); return downloadArtifact(downloadUrl.value) }
async function download(){ try{ const blob=await getArtifact(); const url=URL.createObjectURL(blob); const a=document.createElement('a'); a.href=url; a.download=documentName.value; a.click(); URL.revokeObjectURL(url) }catch(error){ ElMessage.warning(error.message) } }
async function preview(){ try{ const blob=await getArtifact(); window.open(URL.createObjectURL(blob),'_blank') }catch(error){ ElMessage.warning(error.message) } }
async function runAgain(message){ if(!projectId.value) return ElMessage.warning('请先选择项目'); try{ await startWritingGeneration(projectId.value); ElMessage.success(message); router.push('/writing-generator/generate') }catch(error){ ElMessage.error(error?.responseData?.message || error?.message || '任务启动失败') } }
function regenerate(){ return runAgain('已重新启动生成任务') }
function newVersion(){ return runAgain('新版本生成任务已启动') }
async function autoRepair(){ repairing.value=true; try{ await runAgain('已启动自动规范与重新生成'); }finally{ repairing.value=false } }
onMounted(load)
</script>

<style scoped>
.export-page{min-height:100vh;padding:0 300px 56px 260px;background:linear-gradient(45deg,#fbd7ea 0%,#f8eaf0 38%,#edf1f8 64%,#dcebff 100%);color:#252936}.page-heading{max-width:1500px;margin:auto;padding:38px 0 22px;text-align:center}.page-heading span,.panel small{color:#6e4fff;font-size:9px;font-weight:800;letter-spacing:.15em}.page-heading h1{margin:8px 0 5px;font-size:40px}.page-heading p,.panel p{color:#747989}.panel{border:1px solid rgba(110,79,255,.1);border-radius:18px;background:rgba(255,255,255,.9);box-shadow:0 14px 42px rgba(61,53,104,.07)}.project-card{display:flex;align-items:center;justify-content:space-between;max-width:1456px;margin:0 auto 16px;padding:22px}.project-card h2,.section-heading h2,.assistant-card h2{margin:5px 0}.project-card p{margin:0}.project-meta{display:flex;gap:30px}.project-meta span{display:grid;gap:5px;color:#8a8e9a;font-size:10px}.project-meta strong{color:#353946;font-size:13px}.project-meta .success{color:#145b4d}.project-meta .failed{color:#d24666}.delivery-layout{display:grid;grid-template-columns:minmax(0,1fr) 290px;gap:16px;max-width:1500px;margin:auto}.delivery-main,.delivery-side{display:grid;align-content:start;gap:16px}.delivery-main>.panel,.delivery-side>.panel{padding:22px}.document-card{display:grid;grid-template-columns:76px 1fr auto;align-items:center;gap:20px}.doc-cover{display:grid;place-items:center;width:70px;height:88px;border-radius:13px;background:linear-gradient(145deg,#6e4fff,#9878ff);color:#fff}.doc-cover b{font-size:32px}.doc-cover span{font-size:8px;letter-spacing:.18em}.document-info h2{margin:5px 0}.document-info p{margin:0}.content-tags{display:flex;flex-wrap:wrap;gap:6px;margin:11px 0}.content-tags span{padding:5px 8px;border-radius:99px;background:#f1edff;color:#6e4fff;font-size:9px}.document-stats{display:flex;gap:20px}.document-stats span{display:grid;color:#9296a2;font-size:8px}.document-stats b{color:#454957;font-size:15px}.primary-action,.outline-action{padding:11px 16px;border-radius:10px;font-weight:700}.primary-action{border:0;background:linear-gradient(135deg,#6e4fff,#ff55b0);color:#fff;box-shadow:0 8px 22px rgba(110,79,255,.18)}button:disabled{cursor:not-allowed;opacity:.45}.outline-action{border:1px solid #b6a4f8;background:#fff;color:#6e4fff}.section-heading,.action-card{display:flex;align-items:center;justify-content:space-between}.quality-note{margin:9px 0 16px;padding:10px;border-radius:9px;background:#f8f5ff;font-size:10px}.quality-list{display:grid;grid-template-columns:1fr 1fr;gap:8px}.quality-list article{display:grid;grid-template-columns:28px 1fr auto;align-items:center;gap:9px;padding:11px;border:1px solid #efecf7;border-radius:10px}.quality-list i{display:grid;place-items:center;width:24px;height:24px;border-radius:50%;background:#f0edf8;color:#8f85b8;font-style:normal}.quality-list .passed i{background:#e7f3ef;color:#145b4d}.quality-list .fixing i{background:#eee9ff;color:#6e4fff}.quality-list div{display:grid}.quality-list span,.quality-list em{color:#9296a2;font-size:9px;font-style:normal}.action-card>div:last-child{display:flex;gap:8px}.assistant-score{display:grid;place-items:center;margin:15px 0;padding:18px;border-radius:13px;background:linear-gradient(135deg,#f2eeff,#fff1f8)}.assistant-score b{color:#6e4fff;font-size:30px}.assistant-score span{color:#858997;font-size:9px}.assistant-card ul{display:grid;gap:10px;margin:0;padding:0;list-style:none}.assistant-card li{display:flex;gap:8px;color:#676b78;font-size:10px;line-height:1.5}.assistant-card li i{color:#6e4fff;font-style:normal}.version-list{display:grid;gap:8px;margin-top:14px}.version-list article{display:grid;grid-template-columns:38px 1fr auto;align-items:center;gap:9px;padding:10px;border-radius:10px;background:#faf9ff}.version-list i{color:#6e4fff;font-size:10px;font-style:normal;font-weight:800}.version-list div{display:grid;min-width:0}.version-list strong{overflow:hidden;font-size:10px;text-overflow:ellipsis;white-space:nowrap}.version-list span,.version-list em{color:#9296a2;font-size:8px;font-style:normal}.empty-version{display:grid;place-items:center;gap:7px;padding:26px 8px;text-align:center}.empty-version i{color:#6e4fff;font-size:30px;font-style:normal}.empty-version span{color:#9296a2;font-size:9px;line-height:1.5}@media(max-width:1500px){.export-page{padding-right:40px}.delivery-layout{grid-template-columns:1fr}.delivery-side{grid-template-columns:1fr 1fr}}@media(max-width:900px){.export-page{padding:0 12px 35px}.project-card,.document-card,.action-card{display:grid;grid-template-columns:1fr}.project-meta,.document-stats,.action-card>div:last-child{flex-wrap:wrap}.quality-list,.delivery-side{grid-template-columns:1fr}}
</style>
