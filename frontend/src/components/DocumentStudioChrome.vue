<template>
  <aside class="studio-rail">
    <button class="studio-brand" type="button" @click="router.push('/dashboard')">
      <span>D</span><strong>Dokiai</strong><small>学术</small>
    </button>
    <div class="project-label">
      <small>文档工作室</small>
      <b>{{ projectName || '智能文档创作中心' }}</b>
    </div>
    <nav aria-label="项目流程">
      <button
        v-for="item in steps"
        :key="item.no"
        :class="{ active: item.no === current, done: item.no < current }"
        type="button"
        @click="router.push(item.path)"
      >
        <i>{{ item.no < current ? '✓' : item.no }}</i>
        <span><b>{{ item.name }}</b><small>{{ item.en }}</small></span>
      </button>
    </nav>
    <button class="back" type="button" @click="router.push('/dashboard')">← 返回工作台</button>
  </aside>
  <aside class="ai-assistant">
    <div class="ai-head">
      <span>AI</span>
      <div>
        <b>Dokiai 助手</b>
        <small>{{ busy ? '正在协助处理' : '在线 · 已就绪' }}</small>
      </div>
      <i></i>
    </div>
    <div class="ai-card">
      <small>当前阶段</small>
      <strong>{{ currentStep.name }}</strong>
      <p>{{ description || currentStep.tip }}</p>
    </div>
    <div v-if="assistantItems.length" class="ai-facts">
      <div v-for="item in assistantItems" :key="item.label">
        <small>{{ item.label }}</small>
        <b>{{ item.value }}</b>
      </div>
    </div>
    <div class="ai-checks">
      <div><i>✓</i><span><b>只需输入题目</b><small>大纲与图表位置自动生成</small></span></div>
      <div><i>✓</i><span><b>只确认一次大纲</b><small>其余步骤自动进行</small></span></div>
      <div :class="{ pending: busy }">
        <i>{{ busy ? '···' : '✓' }}</i>
        <span>
          <b>{{ busy ? 'AI 正在处理' : 'AI 已就绪' }}</b>
          <small>{{ busy ? '请稍候' : '等待输入论文题目' }}</small>
        </span>
      </div>
    </div>
    <div class="ai-note">
      <b>创作原则</b>
      <p>纯文字稿不联网检索文献，图表与表格按已确认的大纲生成。</p>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  current: { type: Number, default: 1 },
  projectName: { type: String, default: '' },
  description: { type: String, default: '' },
  busy: { type: Boolean, default: false },
  assistantItems: { type: Array, default: () => [] }
})
const router = useRouter()
const steps = [
  { no: 1, name: '输入题目', en: '从题目开始', path: '/writing', tip: '只需输入论文题目，其他内容由系统自动准备。' },
  { no: 2, name: '确认大纲', en: '核对结构', path: '/writing-generator/outline', tip: '一次确认题目、标题及图表位置。' },
  { no: 3, name: '生成全文', en: '撰写正文', path: '/writing-generator/generate', tip: '按二级标题生成正文并自动检查。' },
  { no: 4, name: '下载文档', en: '导出 Word', path: '/writing-generator/export', tip: '查看并下载完整的 Word 文档。' }
]
const currentStep = computed(() => steps.find(x => x.no === props.current) || steps[0])
</script>

<style scoped>
.studio-rail,
.ai-assistant {
  position: fixed;
  top: 0;
  bottom: 0;
  z-index: 20;
  color: #2b2f3a;
  background: rgba(255, 255, 255, 0.94);
}

.studio-rail {
  left: 0;
  display: flex;
  flex-direction: column;
  width: 240px;
  padding: 22px 16px 18px;
  border-right: 1px solid rgba(108, 99, 255, 0.14);
}

.studio-brand {
  display: grid;
  grid-template-columns: 38px 1fr;
  grid-template-rows: 20px 16px;
  column-gap: 10px;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.studio-brand > span {
  grid-row: 1 / 3;
  display: grid;
  place-items: center;
  border-radius: 11px;
  color: #fff;
  background: linear-gradient(145deg, #4a90ff 0%, #6d5dfb 55%, #df66b7 100%);
  box-shadow: 0 10px 22px rgba(109, 93, 251, 0.28);
  font-size: 18px;
  font-weight: 800;
}

.studio-brand strong {
  color: #1c2438;
  font-size: 16px;
}

.studio-brand small,
.project-label small {
  color: #8b90a3;
  font-size: 11px;
  letter-spacing: 0.04em;
}

.project-label {
  display: grid;
  gap: 6px;
  margin: 28px 4px 14px;
}

.project-label b {
  color: #1c2438;
  font-size: 13px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.studio-rail nav {
  display: grid;
  gap: 4px;
}

.studio-rail nav button {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 10px;
  border: 0;
  border-radius: 12px;
  color: #4b5160;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.studio-rail nav button:hover {
  color: #5b4cf0;
  background: rgba(109, 93, 251, 0.06);
}

.studio-rail nav button.active {
  color: #5b4cf0;
  background: linear-gradient(135deg, rgba(108, 99, 255, 0.14), rgba(255, 126, 179, 0.12));
}

.studio-rail nav button > i {
  display: grid;
  place-items: center;
  width: 27px;
  height: 27px;
  border: 1px solid #e0dced;
  border-radius: 9px;
  background: #fff;
  color: #8e93a4;
  font-style: normal;
  font-size: 12px;
}

.studio-rail nav button.active > i {
  border-color: transparent;
  color: #fff;
  background: linear-gradient(145deg, #4a90ff, #6d5dfb 55%, #df66b7);
}

.studio-rail nav button.done {
  color: #6d5c99;
}

.studio-rail nav button.done > i {
  border-color: #e4dcff;
  background: #f3efff;
  color: #6d5dfb;
}

.studio-rail nav span {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.studio-rail nav b {
  font-size: 13px;
}

.studio-rail nav small {
  color: #8d93a6;
  font-size: 11px;
}

.back {
  margin-top: auto;
  padding: 10px 4px;
  border: 0;
  background: transparent;
  color: #6d5dfb;
  text-align: left;
  cursor: pointer;
}

.back:hover {
  text-decoration: underline;
}

.ai-assistant {
  right: 0;
  width: 260px;
  padding: 24px 18px 148px;
  overflow: auto;
  border-left: 1px solid rgba(108, 99, 255, 0.12);
}

.ai-head {
  display: flex;
  align-items: center;
  gap: 11px;
}

.ai-head > span {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(145deg, #4a90ff, #6d5dfb 55%, #df66b7);
  font-weight: 800;
}

.ai-head div {
  display: grid;
}

.ai-head small {
  margin-top: 3px;
  color: #7c8498;
  font-size: 12px;
}

.ai-head > i {
  width: 8px;
  height: 8px;
  margin-left: auto;
  border-radius: 50%;
  background: #6d5dfb;
  box-shadow: 0 0 0 4px rgba(109, 93, 251, 0.14);
}

.ai-card {
  display: grid;
  gap: 7px;
  margin-top: 22px;
  padding: 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(108, 99, 255, 0.1), rgba(255, 126, 179, 0.1));
}

.ai-card small {
  color: #6f7890;
}

.ai-card strong {
  font-size: 16px;
}

.ai-card p,
.ai-note p {
  margin: 0;
  font-size: 12px;
  line-height: 1.65;
}

.ai-card p {
  color: #5c6578;
}

.ai-facts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 14px;
}

.ai-facts > div {
  display: grid;
  gap: 4px;
  padding: 10px;
  border: 1px solid rgba(108, 99, 255, 0.12);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.78);
}

.ai-facts small {
  color: #8c90a0;
  font-size: 11px;
}

.ai-facts b {
  overflow-wrap: anywhere;
  color: #5044a5;
  font-size: 12px;
}

.ai-checks {
  display: grid;
  gap: 16px;
  margin: 22px 2px 0;
}

.ai-checks > div {
  display: flex;
  gap: 10px;
}

.ai-checks i {
  display: grid;
  flex: 0 0 22px;
  place-items: center;
  height: 22px;
  border-radius: 50%;
  color: #6d5dfb;
  background: #efeaff;
  font-style: normal;
  font-size: 11px;
}

.ai-checks .pending i {
  color: #a66b12;
  background: #fff0d6;
}

.ai-checks span {
  display: grid;
  gap: 3px;
}

.ai-checks b {
  font-size: 12px;
}

.ai-checks small {
  color: #87909f;
  font-size: 11px;
}

.ai-note {
  position: absolute;
  right: 18px;
  bottom: 18px;
  left: 18px;
  padding: 14px 16px;
  border-radius: 14px;
  color: #fff;
  background: linear-gradient(145deg, #3a2f86, #7a3d88);
}

.ai-note p {
  margin-top: 6px;
  color: rgba(255, 255, 255, 0.84);
}

@media (max-width: 1300px) {
  .ai-assistant {
    display: none;
  }
}

@media (max-width: 900px) {
  .studio-rail {
    position: relative;
    width: auto;
    height: auto;
    padding: 14px;
  }

  .studio-rail nav {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .studio-rail nav button {
    justify-content: center;
    padding: 8px;
  }

  .studio-rail nav span,
  .project-label,
  .back {
    display: none;
  }

  .studio-brand {
    width: 140px;
    margin-bottom: 10px;
  }
}
</style>
