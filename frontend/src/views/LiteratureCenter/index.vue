<template>
  <main class="literature-page">
    <header class="topbar"><button class="brand" @click="router.push('/dashboard')"><b>D</b><span>DokiAI<small>文字创作中心 V2</small></span></button><button class="back" @click="router.push('/dashboard')">返回工作台 ↗</button></header>
    <section class="hero"><div class="eyebrow">— LITERATURE INTELLIGENCE</div><h1>从一个题目，找到<br><em>值得引用的文献。</em></h1><p>无需拆分关键词。输入你的研究题目，设置中英文文献数量，Doki 将联网检索公开题录并整理为 GB/T 7714 格式。</p><div class="trust"><span>● 真实来源检索</span><span>● 中英文独立配额</span><span>● 自动生成引用</span></div></section>
    <section class="search-shell">
      <div class="panel-head"><div><span class="step">01</span><h2>开始检索</h2><p>只需填写以下三项</p></div><div class="price"><small>最高余额校验</small><strong>{{ maximumCostText }}</strong><span>积分</span><em>当前余额 {{ balanceText }}</em></div></div>
      <div class="form-body">
        <label class="title-field"><span>题目名称 <b>必填</b></span><input v-model="form.title" maxlength="200" placeholder="例如：数字经济背景下中小企业创新能力提升路径研究" :disabled="searching" @keyup.enter="submit"><small>{{ form.title.length }}/200</small></label>
        <div class="count-row"><label><span>中文文献数量</span><div class="number-input"><button @click="adjust('chineseCount',-1)">−</button><input v-model.number="form.chineseCount" type="number" min="0" max="20" step="1" :aria-invalid="Boolean(countState.chineseCount.error)"><button @click="adjust('chineseCount',1)">＋</button></div><small>优先检索中文期刊与公开题录</small></label><label><span>英文文献数量</span><div class="number-input"><button @click="adjust('englishCount',-1)">−</button><input v-model.number="form.englishCount" type="number" min="0" max="20" step="1" :aria-invalid="Boolean(countState.englishCount.error)"><button @click="adjust('englishCount',1)">＋</button></div><small>优先检索英文期刊与 DOI 页面</small></label></div>
        <button class="primary" :disabled="!canSubmit||searching" @click="submit"><span>{{ searching?'Doki 正在检索中':'让 Doki 开始搜索' }}</span><i>{{ searching?'···':'→' }}</i></button><p class="tip" :class="{warning:billingWarning}">{{ billingHint }}<button v-if="pricingError" type="button" @click="loadBilling">重新读取</button></p>
      </div>
      <div v-if="searching" class="progress"><i></i><div><strong>正在并行检索多个公开学术来源</strong><span>单个来源超时会自动切换，Doki 正在核验作者、年份、期刊与来源链接…</span></div></div>
    </section>
    <section v-if="result" class="summary"><div><span>SEARCH COMPLETE</span><h2>已凑满 {{ actualCount }} 篇文献</h2><p>中文目标 {{ result.chineseCount }} 篇 · 英文目标 {{ result.englishCount }} 篇 · 实际按 {{ result.unitCostPoints }} 积分/篇扣除 {{ result.costPoints }} 积分</p><p class="completion-note">相关性优先，不足时以同语言真实题录补齐；仅凑满目标数量才返回并扣费。</p></div><button @click="copyCitation">复制全部 GB/T 7714</button></section>
    <section v-if="items.length" class="results"><article v-for="item in items" :key="item.number"><header><b>[{{ item.number }}]</b><em>{{ item.language==='EN'?'ENGLISH':'中文' }} · {{ item.year||'年份未知' }}</em></header><h3>{{ item.title }}</h3><p class="meta">{{ item.authors||'作者未知' }} · {{ item.source||'来源未知' }}</p><p class="abstract">{{ item.abstractText||'暂无公开摘要。' }}</p><div class="citation"><span>GB/T 7714</span><p>{{ item.gbt7714 }}</p></div><footer><a v-if="item.url" :href="item.url" target="_blank">查看来源 ↗</a><button @click="copyOne(item)">复制引用</button></footer></article></section>
    <section v-if="result" class="summary next"><div><span>下一步</span><h2>将文献用于创作</h2><p>引用会自动带入文字创作中心，你仍可在生成前检查与调整。</p></div><button class="go" @click="goWriting">进入文字创作中心 →</button></section>
  </main>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getFeaturePricing, getPointAccount, searchLiterature } from '../../api/rewrite'

const router = useRouter()
const searching = ref(false)
const result = ref(null)
const form = ref({ title: '', chineseCount: 10, englishCount: 5 })
const literaturePricing = ref(null)
const pricingLoaded = ref(false)
const pricingError = ref('')
const accountPoints = ref(null)

const countState = computed(() => ({
  chineseCount: validateCount(form.value.chineseCount, '中文文献数量'),
  englishCount: validateCount(form.value.englishCount, '英文文献数量')
}))
const normalizedCounts = computed(() => {
  if (countState.value.chineseCount.error || countState.value.englishCount.error) return null
  return {
    chineseCount: countState.value.chineseCount.value,
    englishCount: countState.value.englishCount.value
  }
})
const totalCount = computed(() => normalizedCounts.value
  ? normalizedCounts.value.chineseCount + normalizedCounts.value.englishCount
  : null)
const countValidationMessage = computed(() => {
  const fieldError = countState.value.chineseCount.error || countState.value.englishCount.error
  if (fieldError) return fieldError
  if (totalCount.value < 15) return '中文和英文文献数量合计至少需要 15 篇'
  if (totalCount.value > 20) return '中文和英文文献数量合计不能超过 20 篇'
  return ''
})
const unitCostPoints = computed(() => literaturePricing.value?.costPoints ?? null)
const pricingEnabled = computed(() => Boolean(literaturePricing.value?.enabled))
const pricingReady = computed(() => pricingLoaded.value && !pricingError.value && unitCostPoints.value !== null && pricingEnabled.value)
const maximumCost = computed(() => pricingReady.value && normalizedCounts.value && !countValidationMessage.value
  ? totalCount.value * unitCostPoints.value
  : null)
const maximumCostText = computed(() => maximumCost.value === null ? '--' : maximumCost.value)
const balanceKnown = computed(() => accountPoints.value !== null)
const balanceText = computed(() => balanceKnown.value ? `${accountPoints.value} 积分` : '--')
const hasEnoughPoints = computed(() => !balanceKnown.value || maximumCost.value === null || accountPoints.value >= maximumCost.value)
const basicCanSearch = computed(() => Boolean(form.value.title.trim()) && Boolean(normalizedCounts.value) && !countValidationMessage.value)
const canSubmit = computed(() => basicCanSearch.value && pricingReady.value && hasEnoughPoints.value)
const billingWarning = computed(() => Boolean(countValidationMessage.value) || Boolean(pricingError.value) || (pricingLoaded.value && !pricingEnabled.value) || (pricingReady.value && !hasEnoughPoints.value))
const billingHint = computed(() => {
  if (countValidationMessage.value) return countValidationMessage.value
  if (!pricingLoaded.value) return '正在读取当前文献检索单价…'
  if (pricingError.value) return `${pricingError.value}，暂时无法发起检索。`
  if (!pricingEnabled.value) return '文献检索功能当前未启用。'
  if (!hasEnoughPoints.value) return `当前余额 ${accountPoints.value} 积分，最高需校验 ${maximumCost.value} 积分，还差 ${maximumCost.value - accountPoints.value} 积分。`
  const priceText = unitCostPoints.value === 0 ? '当前免费' : `当前单价 ${unitCostPoints.value} 积分/篇`
  return `每次合计检索 15–20 篇 · ${priceText} · 相关性优先，不足时以同语言真实题录补齐；仅凑满目标数量才返回并扣费`
})
const items = computed(() => result.value?.items || [])
const actualCount = computed(() => Number(result.value?.actualCount ?? items.value.length))

function normalizePricing(item) {
  if (!item) return null
  if (item.costPoints === null || item.costPoints === undefined || item.costPoints === '') return null
  const costPoints = Number(item.costPoints)
  if (!Number.isFinite(costPoints) || costPoints < 0) return null
  return {
    ...item,
    costPoints,
    enabled: item.enabled === true || item.enabled === 1 || item.enabled === '1' || item.enabled === 'true'
  }
}

function validateCount(value, label) {
  if (value === null || value === undefined || value === '' || (typeof value === 'string' && !value.trim())) {
    return { value: null, error: `请输入${label}（0–20 的整数）` }
  }
  const number = Number(value)
  if (!Number.isFinite(number) || !Number.isInteger(number)) {
    return { value: null, error: `${label}必须是 0–20 的整数` }
  }
  if (number < 0 || number > 20) {
    return { value: null, error: `${label}必须在 0–20 之间` }
  }
  return { value: number, error: '' }
}

async function loadPricing() {
  pricingLoaded.value = false
  pricingError.value = ''
  try {
    const pricing = await getFeaturePricing()
    const item = Array.isArray(pricing) ? pricing.find(value => value.featureCode === 'LITERATURE_SEARCH') : null
    literaturePricing.value = normalizePricing(item)
    if (!literaturePricing.value) pricingError.value = '文献检索价格未配置'
  } catch {
    literaturePricing.value = null
    pricingError.value = '文献检索价格读取失败'
  } finally {
    pricingLoaded.value = true
  }
}

async function refreshAccount() {
  try {
    const account = await getPointAccount()
    const rawPoints = account?.points
    const points = rawPoints === null || rawPoints === undefined || rawPoints === '' ? Number.NaN : Number(rawPoints)
    accountPoints.value = Number.isFinite(points) ? points : null
  } catch {
    accountPoints.value = null
  }
}

async function loadBilling() {
  await Promise.all([loadPricing(), refreshAccount()])
}

function adjust(key, delta) {
  const current = validateCount(form.value[key], '')
  const base = current.error ? 0 : current.value
  form.value[key] = Math.max(0, Math.min(20, base + delta))
}

async function submit() {
  if (!form.value.title.trim()) return ElMessage.warning('请输入题目名称')
  if (countValidationMessage.value) return ElMessage.warning(countValidationMessage.value)
  if (!pricingReady.value) return ElMessage.warning(pricingError.value || '文献检索功能当前不可用')
  if (!hasEnoughPoints.value) return ElMessage.warning(`积分不足，最高需要 ${maximumCost.value} 积分，当前余额 ${accountPoints.value} 积分`)
  const counts = normalizedCounts.value
  result.value = null
  searching.value = true
  try {
    const response = await searchLiterature({ title: form.value.title.trim(), ...counts })
    const responseItems = Array.isArray(response?.items) ? response.items : []
    const responseActualCount = Number(response?.actualCount ?? responseItems.length)
    const responseChineseCount = Number(response?.actualChineseCount)
    const responseEnglishCount = Number(response?.actualEnglishCount)
    const itemChineseCount = responseItems.filter(item => String(item?.language || '').toUpperCase() === 'ZH').length
    const itemEnglishCount = responseItems.filter(item => String(item?.language || '').toUpperCase() === 'EN').length
    const itemUrls = responseItems.map(item => String(item?.url || '').trim().toLowerCase())
    const itemDois = responseItems
      .map(item => String(item?.doi || '').trim().toLowerCase().replace(/^https?:\/\/(dx\.)?doi\.org\//, '').replace(/^doi\s*:\s*/, ''))
      .filter(Boolean)
    const itemTitleYears = responseItems.map(item => `${String(item?.title || '').trim().toLowerCase().replace(/[^\p{Script=Han}a-z0-9]+/gu, '')}|${String(item?.year || '').trim()}`)
    const targetCount = counts.chineseCount + counts.englishCount
    if (response?.partial
      || !Number.isInteger(responseActualCount)
      || responseActualCount !== targetCount
      || responseItems.length !== targetCount
      || responseChineseCount !== counts.chineseCount
      || responseEnglishCount !== counts.englishCount
      || itemChineseCount !== counts.chineseCount
      || itemEnglishCount !== counts.englishCount
      || itemUrls.some(value => !value)
      || new Set(itemDois).size !== itemDois.length
      || itemTitleYears.some(value => value.startsWith('|') || value.endsWith('|'))
      || new Set(itemTitleYears).size !== targetCount) {
      result.value = null
      await refreshAccount()
      ElMessage.error('本次检索未凑满目标数量，未展示不完整结果，请稍后重试')
      return
    }
    result.value = response
    const rawResponseUnitCost = response?.unitCostPoints
    const responseUnitCost = rawResponseUnitCost === null || rawResponseUnitCost === undefined || rawResponseUnitCost === ''
      ? Number.NaN
      : Number(rawResponseUnitCost)
    if (Number.isFinite(responseUnitCost) && responseUnitCost >= 0 && literaturePricing.value) {
      literaturePricing.value = { ...literaturePricing.value, costPoints: responseUnitCost }
    }
    await refreshAccount()
    ElMessage.success(`文献搜索完成：已凑满 ${items.value.length} 篇`)
  } finally {
    searching.value = false
  }
}

async function copyCitation() {
  await navigator.clipboard.writeText(result.value?.citationText || '')
  ElMessage.success('已复制全部引用')
}

async function copyOne(item) {
  await navigator.clipboard.writeText(item.gbt7714 || '')
  ElMessage.success('已复制该条引用')
}

function goWriting() {
  if (result.value?.citationText) sessionStorage.setItem('dropai_literature_citations', result.value.citationText)
  router.push('/writing')
}

onMounted(loadBilling)
</script>
<style scoped>
*{box-sizing:border-box}.literature-page{min-height:100vh;padding:0 5vw 80px;background:radial-gradient(circle at 88% 14%,#e1e9ff 0,transparent 28%),radial-gradient(circle at 5% 70%,#ffe1f2 0,transparent 24%),linear-gradient(135deg,#fff 0%,#f8f5ff 52%,#f1f5ff 100%);color:#202337;font-family:Inter,"PingFang SC",sans-serif}.topbar{display:flex;justify-content:space-between;max-width:1240px;margin:auto;padding:24px 0;border-bottom:1px solid #7460d91c}.brand{display:flex;align-items:center;gap:11px;border:0;background:none;text-align:left}.brand b{display:grid;place-items:center;width:38px;height:38px;border-radius:11px;background:linear-gradient(135deg,#4f8cff,#7356f1 55%,#ed65b5);color:#fff;font:800 20px Georgia;box-shadow:0 8px 22px #7456e53d}.brand span{font-size:18px;font-weight:900}.brand small{display:block;color:#8b89a0;font-size:9px;letter-spacing:.15em}.back{padding:11px 16px;border:1px solid #d7cff8;border-radius:99px;background:#ffffffa8;color:#674cda;font-weight:700}.hero{max-width:1240px;margin:auto;padding:76px 0 54px}.eyebrow{color:#7057dc;font-size:11px;font-weight:900;letter-spacing:.18em}.hero h1{margin:22px 0;font:500 clamp(48px,6.3vw,86px)/1.06 Georgia,"Songti SC",serif;letter-spacing:-.045em}.hero h1 em{background:linear-gradient(100deg,#4f83f1,#7652e9 48%,#e65eac);-webkit-background-clip:text;background-clip:text;color:transparent;font-style:normal}.hero>p{max-width:710px;color:#74768a;font-size:17px;line-height:1.9}.trust{display:flex;gap:28px;margin-top:28px;color:#686a82;font-size:13px}.search-shell,.summary{max-width:1240px;margin:0 auto 22px;border:1px solid #e1dcf3;border-radius:28px;background:#ffffffeb;box-shadow:0 28px 80px #4d3a9d12}.panel-head{display:flex;align-items:center;justify-content:space-between;padding:28px 34px;border-bottom:1px solid #ece8f6}.panel-head>div:first-child{display:grid;grid-template-columns:auto auto;align-items:center;gap:0 13px}.step{grid-row:1/3;display:grid;place-items:center;width:42px;height:42px;border-radius:50%;background:linear-gradient(135deg,#6d56ee,#e55fab);color:#fff;font-weight:900;box-shadow:0 9px 22px #7656e43b}.panel-head h2{margin:0;font:500 28px Georgia}.panel-head p{margin:4px 0;color:#9393a4;font-size:12px}.price{color:#7b7c90}.price strong{margin:0 6px;color:#6b50df;font-size:30px}.form-body{padding:34px}.form-body label{display:grid;gap:10px;color:#4b4d67;font-size:13px;font-weight:800}.title-field{position:relative}.title-field b{color:#df5ba7;font-size:10px}.title-field input{height:66px;padding:0 75px 0 20px;border:1px solid #d8d2e9;border-radius:15px;background:#fcfbff;font-size:16px;outline:none}.title-field input:focus{border-color:#8268ec;box-shadow:0 0 0 4px #7657ed14}.title-field small{position:absolute;right:18px;bottom:24px;color:#aaa7b6}.count-row{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-top:24px}.number-input{display:grid;grid-template-columns:48px 1fr 48px;height:56px;overflow:hidden;border:1px solid #ddd6ef;border-radius:14px}.number-input button{border:0;background:#f4f1ff;color:#6f53dc;font-size:20px}.number-input input{min-width:0;border:0;border-inline:1px solid #e4dff2;color:#5e47c5;font-size:20px;font-weight:900;text-align:center}.count-row small{color:#9896a6;font-weight:400}.primary{display:flex;align-items:center;justify-content:space-between;width:100%;height:62px;margin-top:30px;padding:0 10px 0 24px;border:0;border-radius:15px;background:linear-gradient(135deg,#4c8df7,#7355ed 55%,#dc58aa);color:#fff;font-weight:800;box-shadow:0 14px 34px #7255e33b}.primary i{display:grid;place-items:center;width:43px;height:43px;border-radius:12px;background:#ffffff2b;color:#fff;font-size:22px;font-style:normal}.primary:disabled{opacity:.45}.tip{color:#a09eac;font-size:11px;text-align:center}.progress{display:flex;align-items:center;gap:18px;margin:0 34px 32px;padding:18px;border-radius:15px;background:linear-gradient(135deg,#f0edff,#fff0f8)}.progress>i{width:13px;height:13px;border-radius:50%;background:#7759e9;box-shadow:0 0 0 8px #7657ed20;animation:pulse 1.2s infinite}.progress div{display:grid;gap:4px}.progress span{color:#888698;font-size:12px}.summary{display:flex;align-items:center;justify-content:space-between;padding:28px 32px}.summary span{color:#7759e7;font-size:10px;font-weight:900;letter-spacing:.16em}.summary h2{margin:7px 0 5px;font:500 27px Georgia}.summary p{margin:0;color:#848394}.summary button,.results button{padding:11px 15px;border:1px solid #d1c6f7;border-radius:10px;background:#fff;color:#684dd5;font-weight:700}.results{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px;max-width:1240px;margin:0 auto 22px}.results article{padding:24px;border:1px solid #e1dced;border-radius:22px;background:#fff;box-shadow:0 16px 45px #55459a0c}.results header,.results footer{display:flex;align-items:center;justify-content:space-between}.results header b{color:#7054dd;font:600 24px Georgia}.results em{color:#9997a6;font-size:10px;font-style:normal}.results h3{min-height:54px;margin:16px 0 10px;font-size:18px}.meta,.abstract{color:#777687;font-size:13px;line-height:1.7}.abstract{min-height:66px}.citation{padding:14px;border-radius:12px;background:linear-gradient(135deg,#f6f3ff,#fff5fa)}.citation span{color:#7457e3;font-size:9px;font-weight:900}.citation p{margin:6px 0 0;font-size:12px;line-height:1.65}.results footer{margin-top:14px}.results a{color:#5d78dc;font-size:12px;text-decoration:none}.summary .go{background:linear-gradient(135deg,#5a83f2,#7355e9,#d85cab);color:#fff}@keyframes pulse{50%{transform:scale(1.2)}}@media(max-width:760px){.literature-page{padding:0 20px 60px}.hero{padding:50px 0 38px}.hero h1{font-size:44px}.trust{flex-wrap:wrap;gap:10px}.panel-head,.form-body{padding:22px}.count-row,.results{grid-template-columns:1fr}.summary{align-items:flex-start;flex-direction:column;gap:20px}}
.price{display:grid;grid-template-columns:auto auto auto;align-items:baseline;justify-items:end}.price small,.price em{grid-column:1/-1}.price em{margin-top:3px;color:#9693a5;font-size:10px;font-style:normal}.number-input input[aria-invalid=true]{background:#fff6f8;color:#c05275}.tip.warning{color:#c05275}.tip button{margin-left:8px;padding:2px 7px;border:1px solid #d7cff8;border-radius:99px;background:#fff;color:#684dd5;font-size:10px}.summary .completion-note{margin-top:7px;color:#706a89;font-size:12px}
</style>
