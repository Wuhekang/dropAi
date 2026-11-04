<template>
  <aside class="studio-rail">
    <button class="studio-brand" type="button" @click="router.push('/dashboard')"><span>D</span><strong>Dokiai</strong><small>学术</small></button>
    <div class="project-label"><small>文档流程</small><b>{{ projectName || '智能文档创作中心' }}</b></div>
    <nav aria-label="项目流程">
      <button v-for="item in steps" :key="item.no" :class="{ active: item.no === current, done: item.no < current }" type="button" @click="router.push(item.path)"><i>{{ item.no < current ? '✓' : item.no }}</i><span><b>{{ item.name }}</b><small>{{ item.en }}</small></span></button>
    </nav>
    <button class="back" type="button" @click="router.push('/dashboard')">返回工作台</button>
  </aside>
  <aside class="ai-assistant">
    <div class="ai-head"><span>助</span><div><b>Dokiai 助手</b><small>{{ busy ? '正在协助处理' : '可以开始' }}</small></div></div>
    <div class="ai-card"><small>当前阶段</small><strong>{{ currentStep.name }}</strong><p>{{ description || currentStep.tip }}</p></div>
    <div v-if="assistantItems.length" class="ai-facts"><div v-for="item in assistantItems" :key="item.label"><small>{{ item.label }}</small><b>{{ item.value }}</b></div></div>
    <div class="ai-checks"><div><i>✓</i><span><b>只需输入题目</b><small>大纲与图表位置自动生成</small></span></div><div><i>✓</i><span><b>只确认一次大纲</b><small>其余步骤自动进行</small></span></div><div :class="{ pending: busy }"><i>{{ busy ? '···' : '✓' }}</i><span><b>{{ busy ? '正在处理' : '可以开始' }}</b><small>{{ busy ? '请稍候' : '等待输入论文题目' }}</small></span></div></div>
    <div class="ai-note"><b>写作说明</b><p>纯文字稿不联网检索文献，图表与表格按已确认的大纲生成。</p></div>
  </aside>
</template>
<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
const props = defineProps({ current: { type: Number, default: 1 }, projectName: { type: String, default: '' }, description: { type: String, default: '' }, busy: { type: Boolean, default: false }, assistantItems: { type: Array, default: () => [] } })
const router = useRouter()
const steps = [{ no: 1, name: '输入题目', en: '只需题目', path: '/writing', tip: '只需输入论文题目，其他内容由系统自动准备。' }, { no: 2, name: '确认大纲', en: '确认一次', path: '/writing-generator/outline', tip: '一次确认题目、标题及图表位置。' }, { no: 3, name: '生成全文', en: '自动成稿', path: '/writing-generator/generate', tip: '按二级标题生成正文并自动检查。' }, { no: 4, name: '下载文档', en: '导出 Word', path: '/writing-generator/export', tip: '查看并下载完整的 Word 文档。' }]
const currentStep = computed(() => steps.find(x => x.no === props.current) || steps[0])
</script>
<style scoped>
.studio-rail,
.ai-assistant {
  position: fixed;
  top: 0;
  bottom: 0;
  z-index: 20;
  background: #fff;
  color: #1c2430;
}

.studio-rail {
  left: 0;
  display: flex;
  flex-direction: column;
  width: 240px;
  padding: 24px 18px;
  border-right: 1px solid #d9dee7;
}

.studio-brand {
  display: grid;
  grid-template-columns: 36px 1fr;
  grid-template-rows: 18px 16px;
  column-gap: 10px;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.studio-brand > span,
.ai-head > span {
  display: grid;
  place-items: center;
  border-radius: 4px;
  color: #fff;
  background: #1e3a5f;
  font-family: "Songti SC", "STSong", "SimSun", serif;
}

.studio-brand > span {
  grid-row: 1 / 3;
  font-size: 18px;
}

.studio-brand strong {
  color: #1c2430;
  font-size: 16px;
}

.studio-brand small,
.project-label small,
.studio-rail nav small {
  color: #5c6b7a;
  font-size: 12px;
  letter-spacing: 0;
}

.project-label {
  display: grid;
  gap: 6px;
  margin: 32px 2px 16px;
}

.project-label b {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
}

.studio-rail nav {
  display: grid;
  gap: 4px;
}

.studio-rail nav button {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
  border: 0;
  border-radius: 4px;
  color: #3d4a5c;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.studio-rail nav button:hover,
.studio-rail nav button.active {
  color: #1e3a5f;
  background: #f4f6f8;
}

.studio-rail nav button > i {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid #d9dee7;
  border-radius: 4px;
  background: #fff;
  font-style: normal;
  font-size: 12px;
}

.studio-rail nav button.active > i,
.studio-rail nav button.done > i {
  border-color: #1e3a5f;
  color: #fff;
  background: #1e3a5f;
}

.studio-rail nav span {
  display: grid;
  gap: 2px;
}

.studio-rail nav b {
  font-size: 13px;
  font-weight: 600;
}

.back {
  margin-top: auto;
  padding: 10px 2px;
  border: 0;
  color: #1e3a5f;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.back:hover {
  text-decoration: underline;
}

.ai-assistant {
  right: 0;
  width: 260px;
  padding: 24px 20px;
  border-left: 1px solid #d9dee7;
}

.ai-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-head > span {
  width: 36px;
  height: 36px;
  font-size: 14px;
}

.ai-head div,
.ai-card,
.ai-checks span,
.ai-facts > div {
  display: grid;
}

.ai-head small {
  margin-top: 2px;
  color: #5c6b7a;
  font-size: 12px;
}

.ai-card,
.ai-note {
  margin-top: 22px;
  padding: 14px;
  border: 1px solid #e6ebf1;
  border-radius: 4px;
  background: #f7f8fa;
}

.ai-card {
  gap: 6px;
}

.ai-card small,
.ai-facts small {
  color: #5c6b7a;
}

.ai-card strong {
  font-size: 16px;
}

.ai-card p,
.ai-note p {
  margin: 0;
  color: #3d4a5c;
  font-size: 13px;
  line-height: 1.65;
}

.ai-facts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 14px;
}

.ai-facts > div {
  gap: 4px;
  padding: 10px;
  border: 1px solid #e6ebf1;
  border-radius: 4px;
}

.ai-facts b {
  color: #1e3a5f;
  font-size: 13px;
}

.ai-checks {
  display: grid;
  gap: 14px;
  margin: 22px 2px;
}

.ai-checks > div {
  display: flex;
  gap: 10px;
}

.ai-checks i {
  display: grid;
  flex: 0 0 20px;
  height: 20px;
  place-items: center;
  border-radius: 50%;
  color: #1e3a5f;
  background: #e7eef6;
  font-style: normal;
  font-size: 11px;
}

.ai-checks .pending i {
  color: #7a5b24;
  background: #f6f0e4;
}

.ai-checks b {
  font-size: 13px;
}

.ai-checks small {
  color: #5c6b7a;
  font-size: 12px;
}

.ai-note {
  position: absolute;
  right: 20px;
  bottom: 24px;
  left: 20px;
  background: #fff;
}

.ai-note p {
  margin-top: 6px;
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
    grid-template-columns: repeat(4, 1fr);
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
    width: 125px;
    margin-bottom: 10px;
  }
}
</style>
