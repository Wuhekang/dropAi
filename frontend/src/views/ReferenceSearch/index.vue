<template>
  <main class="topic-page">
    <DocumentStudioChrome :current="1" :project-name="form.title || '新建纯文字稿'" :busy="working"
      description="第一步只需输入论文题目。" />
    <section class="hero">
      <span>第一步 / 输入题目</span>
      <h1>输入论文题目</h1>
      <p>这里只填题目。系统自动准备大纲和图表位置，下一步再确认大纲。</p>
    </section>
    <section class="topic-card">
      <label for="paper-title">论文题目</label>
      <div class="topic-input">
        <el-input id="paper-title" v-model="form.title" size="large" maxlength="200" show-word-limit
          placeholder="例如：抖音电商短视频封面的视觉设计与点击率优化方案"
          :disabled="working" @keyup.enter="createOutline" />
        <button class="primary-action" type="button" :disabled="working || !form.title.trim()" @click="createOutline">
          {{ working ? '正在生成研究大纲…' : '生成大纲' }}
        </button>
      </div>
      <p class="input-note">无需选择论文类型或字数；本模块不联网检索文献。完整稿 300 积分 / 篇，生成成功后扣费。</p>
      <div v-if="working" class="progress-box">
        <div><span :style="{ width: `${progress}%` }"></span></div>
        <b>{{ statusText }}</b>
        <p>请稍候，完成后会直接进入大纲确认页。</p>
      </div>
    </section>
  </main>
</template>

<script setup>
import { onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import DocumentStudioChrome from '../../components/DocumentStudioChrome.vue'
import { createWritingProject, generateWritingOutline } from '../../api/rewrite'

const router = useRouter()
const form = ref({ title: '' })
const working = ref(false)
const progress = ref(0)
const statusText = ref('正在分析题目…')
let timer = null

function startProgress() {
  progress.value = 8
  const stages = [[25, '正在理解论文题目…'], [52, '正在生成章节大纲…'], [78, '正在安排图表位置…']]
  timer = setInterval(() => {
    const next = stages.find(([value]) => value > progress.value)
    if (next) { progress.value = next[0]; statusText.value = next[1] }
    else if (progress.value < 94) progress.value += 1
  }, 900)
}

async function createOutline() {
  const title = form.value.title.trim()
  if (!title) return ElMessage.warning('请输入论文题目')
  working.value = true
  startProgress()
  try {
    const project = await createWritingProject({
      title,
      documentType: '纯文字稿',
      targetWordCount: 8000,
      skipReferences: true,
      writingTone: '本科论文'
    })
    sessionStorage.setItem('dropai_writing_project_id', project.id)
    sessionStorage.setItem('writing_project_title', title)
    localStorage.setItem('dropai_writing_project_id', project.id)
    localStorage.setItem('writing_project_title', title)
    await generateWritingOutline(project.id)
    progress.value = 100
    statusText.value = '大纲已生成'
    router.push('/writing-generator/outline')
  } catch (error) {
    ElMessage.error(error?.responseData?.message || error?.message || '大纲生成失败')
  } finally {
    working.value = false
    clearInterval(timer)
  }
}

onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
*{box-sizing:border-box}.topic-page{min-height:100vh;padding:0 310px 70px 270px;background:radial-gradient(circle at 70% 0,#eee8ff 0,transparent 35%),linear-gradient(145deg,#fbf8ff,#edf5ff);color:#252936}.hero{max-width:1040px;margin:auto;padding:72px 20px 30px;text-align:center}.hero>span{color:#6e4fff;font-size:11px;font-weight:900;letter-spacing:.2em}.hero h1{margin:14px 0 12px;font-size:46px;letter-spacing:-.04em}.hero p{max-width:760px;margin:auto;color:#747989;font-size:16px;line-height:1.8}.topic-card{max-width:1040px;margin:auto;padding:34px;border:1px solid rgba(110,79,255,.13);border-radius:24px;background:rgba(255,255,255,.94);box-shadow:0 24px 70px rgba(61,53,104,.11)}.topic-card>label{display:block;margin-bottom:10px;font-weight:900}.topic-input{display:grid;grid-template-columns:1fr auto;gap:12px}.primary-action{min-width:150px;border:0;border-radius:12px;background:linear-gradient(135deg,#6e4fff,#ad58e9);color:#fff;font-weight:900;box-shadow:0 12px 28px rgba(110,79,255,.22)}button:disabled{opacity:.45}.rules{display:flex;flex-wrap:wrap;gap:8px;margin-top:17px}.rules span{padding:6px 10px;border-radius:99px;background:#f2efff;color:#644bc5;font-size:11px}.progress-box{margin-top:25px;padding:18px;border-radius:15px;background:#faf9ff;text-align:center}.progress-box>div{height:6px;overflow:hidden;border-radius:99px;background:#ebe8f4}.progress-box>div span{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,#6e4fff,#ff55b0);transition:width .5s}.progress-box b{display:block;margin-top:13px}.progress-box p{margin:5px 0 0;color:#9296a2;font-size:11px}.mode-note{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;max-width:1040px;margin:18px auto}.mode-note article{display:flex;gap:13px;padding:18px;border:1px solid rgba(110,79,255,.09);border-radius:16px;background:rgba(255,255,255,.76)}.mode-note article>b{display:grid;place-items:center;flex:0 0 34px;height:34px;border-radius:10px;background:#eee9ff;color:#6e4fff}.mode-note strong{font-size:13px}.mode-note p{margin:5px 0 0;color:#858997;font-size:11px;line-height:1.6}@media(max-width:1300px){.topic-page{padding-right:35px}}@media(max-width:900px){.topic-page{padding:0 14px 40px}.hero{padding-top:38px}.hero h1{font-size:34px}.topic-input,.mode-note{grid-template-columns:1fr}.primary-action{min-height:48px}}
.topic-page{background:#f5f6fa;color:#202330}
.hero{padding-top:84px;padding-bottom:32px}
.hero>span{color:#6143d6;font-size:13px;letter-spacing:.04em}
.hero h1{margin:16px 0 12px;font-size:48px;font-weight:800;letter-spacing:0}
.hero p{max-width:660px;color:#4e5565;font-size:16px;line-height:1.7}
.topic-card{max-width:880px;padding:36px;border:1px solid #e1dced;background:#fff;box-shadow:0 16px 44px rgba(37,35,67,.08)}
.topic-card>label{margin-bottom:14px;color:#252936;font-size:16px}
.topic-input{grid-template-columns:minmax(0,1fr) 148px}
.topic-input :deep(.el-input__wrapper){min-height:54px;border:1px solid #c9c4d6;border-radius:12px;box-shadow:none}
.topic-input :deep(.el-input__inner){color:#202330;font-size:16px}
.topic-input :deep(.el-input__inner::placeholder){color:#777d8c}
.primary-action{min-height:54px;font-size:15px;cursor:pointer}
.input-note{margin:15px 0 0;color:#5e6573;font-size:13px}
.progress-box{background:#f5f2ff}
.progress-box p{color:#59606e;font-size:12px}
@media(max-width:900px){.hero{padding-top:44px}.hero h1{font-size:36px}.topic-card{padding:22px}.topic-input{grid-template-columns:1fr}}
</style>
