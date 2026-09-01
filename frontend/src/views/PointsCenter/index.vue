<template>
  <main class="points-page">
    <nav class="top-nav"><button @click="router.push('/dashboard')">← 工作台</button><div><i>D</i><span><b>Dokiai Academic</b><small>POINTS CENTER</small></span></div><button @click="router.push('/account')">账户中心 →</button></nav>
    <header><span>DOKIAI POINTS CENTER</span><h1>积分中心</h1><p>管理智能服务积分、充值订单与消费记录。</p></header>
    <section v-if="paymentMessage" class="panel payment-result">{{ paymentMessage }}</section>
    <section class="balance-card"><div><small>CURRENT BALANCE</small><strong>{{ account.points ?? '--' }}</strong><span>当前可用积分</span></div><div class="balance-meta"><article><span>累计消费</span><b>{{ account.usedPoints ?? '--' }}</b></article><article><span>累计获得</span><b>{{ account.totalPoints ?? '--' }}</b></article></div><button class="primary" @click="scrollRecharge">充值积分</button></section>

    <div class="points-grid">
      <section id="recharge" class="panel recharge-panel">
        <header>
          <div><small>RECHARGE</small><h2>充值积分</h2><p>{{ pricingDescription }}</p></div>
          <em>{{ finalPoints }} 积分</em>
        </header>
        <div class="amounts">
          <button v-for="item in quickAmounts" :key="item" :class="{ active: !customSelected && selectedAmount === item }" @click="selectAmount(item)"><b>¥{{ formatAmount(item) }}</b><span>{{ pointsFor(item) }} 积分</span></button>
          <label :class="{ active: customSelected }"><span>自定义</span><div><b>¥</b><input v-model="customAmount" :disabled="!pricingReady" :min="minimumAmount" :max="maximumAmount" :step="amountStep" :inputmode="schoolPricing ? 'decimal' : 'numeric'" type="number" @input="customSelected = true"></div><small>{{ amountHint }}</small></label>
        </div>
        <footer>
          <div><label><input v-model="payType" type="radio" value="alipay">支付宝</label><label><input v-model="payType" type="radio" value="wechat">微信支付</label></div>
          <button class="primary" :disabled="creating || !finalAmount" @click="createOrder">{{ creating ? '正在创建…' : `立即充值 ¥${finalAmount ? formatAmount(finalAmount) : '--'}` }}</button>
        </footer>
      </section>
      <aside class="panel guide"><small>POINTS GUIDE</small><h2>积分如何使用</h2><ul><li><i>✦</i><span><b>智能文档创作</b><small>根据实际生成规则扣除积分</small></span></li><li><i>Aa</i><span><b>学术优化</b><small>按服务规则计算消耗</small></span></li><li><i>✓</i><span><b>到账记录</b><small>支付成功后自动更新余额</small></span></li></ul></aside>
    </div>

    <div class="history-grid">
      <section class="panel"><header><small>USAGE HISTORY</small><h2>消费记录</h2></header><div v-if="consumption.length" class="list"><article v-for="(item, index) in consumption" :key="item.id || index"><span><b>{{ item.featureName || item.description || item.remark || '智能服务' }}</b><small>{{ formatTime(item.createdAt || item.created_at) }}</small></span><strong>−{{ Math.abs(Number(item.points || item.amount || 0)) }}</strong></article></div><div v-else class="empty">暂无消费记录</div></section>
      <section class="panel"><header><small>RECHARGE ORDERS</small><h2>充值订单</h2></header><div v-if="orders.length" class="list"><article v-for="order in orders.slice(0, 8)" :key="order.orderNo"><span><b>{{ order.orderNo }}</b><small>{{ formatTime(order.createdAt || order.created_at) }} · {{ statusText(order.status) }}</small></span><strong>¥{{ order.amount }} / {{ order.points }}积分</strong></article></div><div v-else class="empty">暂无充值订单</div></section>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  createRechargeOrder,
  getPointAccount,
  getPointTransactions,
  getRechargeOrder,
  getRechargeOrders,
  getRechargePricing
} from '../../api/rewrite'

const defaultPricing = { pricePer10: 2, minAmount: 1, maxAmount: 1000, schoolPricing: false, schoolId: null, schoolName: '' }
const router = useRouter()
const route = useRoute()
const account = ref({})
const transactions = ref([])
const orders = ref([])
const pricing = ref({ ...defaultPricing })
const pricingReady = ref(false)
const selectedAmount = ref(null)
const customAmount = ref('')
const customSelected = ref(false)
const payType = ref('alipay')
const creating = ref(false)
const paymentMessage = ref('')

const schoolPricing = computed(() => Boolean(pricing.value.schoolPricing))
const pricePer10 = computed(() => positiveNumber(pricing.value.pricePer10, defaultPricing.pricePer10))
const minimumAmount = computed(() => positiveNumber(pricing.value.minAmount, schoolPricing.value ? pricePer10.value : defaultPricing.minAmount))
const maximumAmount = computed(() => Math.max(minimumAmount.value, positiveNumber(pricing.value.maxAmount, defaultPricing.maxAmount)))
const amountStep = computed(() => schoolPricing.value ? 0.01 : 1)
const quickAmounts = computed(() => {
  if (!pricingReady.value) return []
  const candidates = schoolPricing.value ? [pricePer10.value, pricePer10.value * 10, pricePer10.value * 100] : [10, 20, 100]
  const values = [...new Set(candidates.map(normalizeMoney).filter(value => value >= minimumAmount.value && value <= maximumAmount.value))]
  return values.length ? values : [normalizeMoney(minimumAmount.value)]
})
const normalizedCustomAmount = computed(() => validateAmount(customAmount.value))
const finalAmount = computed(() => pricingReady.value ? (customSelected.value ? normalizedCustomAmount.value : validateAmount(selectedAmount.value)) : null)
const finalPoints = computed(() => pointsFor(finalAmount.value))
const consumption = computed(() => transactions.value.filter(item => Number(item.points ?? item.amount) < 0).slice(0, 8))
const pricingDescription = computed(() => {
  if (!pricingReady.value) return '正在读取当前账号充值费率…'
  const owner = schoolPricing.value && pricing.value.schoolName ? `${pricing.value.schoolName}统一价：` : ''
  return `${owner}${formatAmount(pricePer10.value)} 元 = 10 积分，支持 ${amountHint.value}。`
})
const amountHint = computed(() => pricingReady.value ? `${formatAmount(minimumAmount.value)}–${formatAmount(maximumAmount.value)} 元${schoolPricing.value ? '，最多两位小数' : '整数'}` : '充值范围读取中')

function positiveNumber(value, fallback) { const parsed = Number(value); return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback }
function normalizeMoney(value) { return Math.round(Number(value) * 100) / 100 }
function normalizePricing(data) {
  const price = Number(data?.pricePer10)
  const minimum = Number(data?.minAmount)
  const maximum = Number(data?.maxAmount)
  if (!Number.isFinite(price) || price <= 0 || !Number.isFinite(minimum) || minimum <= 0 || !Number.isFinite(maximum) || maximum < minimum || typeof data?.schoolPricing !== 'boolean') {
    throw new Error('充值费率配置异常，请联系管理员')
  }
  return { ...defaultPricing, ...data, pricePer10: normalizeMoney(price), minAmount: normalizeMoney(minimum), maxAmount: normalizeMoney(maximum) }
}
function validateAmount(raw) {
  if (raw === null || raw === undefined || String(raw).trim() === '') return null
  const value = Number(raw)
  if (!Number.isFinite(value) || value < minimumAmount.value || value > maximumAmount.value) return null
  if (schoolPricing.value) return Math.abs(value * 100 - Math.round(value * 100)) < 1e-7 ? normalizeMoney(value) : null
  return Number.isInteger(value) ? value : null
}
function pointsFor(amount) { return amount ? Math.floor((Number(amount) * 10) / pricePer10.value + 1e-9) : 0 }
function formatAmount(value) { return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: schoolPricing.value ? 2 : 0, maximumFractionDigits: 2 }) }
function selectAmount(value) { selectedAmount.value = value; customSelected.value = false; customAmount.value = '' }
function scrollRecharge() { document.getElementById('recharge')?.scrollIntoView({ behavior: 'smooth' }) }
function formatTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '--' }
function statusText(value) { return ({ pending: '待支付', paid: '支付成功', failed: '支付失败', refunded: '已退款' })[value] || value || '--' }

async function load() {
  pricingReady.value = false
  try {
    const pricingRequest = getRechargePricing().then(data => {
      pricing.value = normalizePricing(data)
      pricingReady.value = true
      return pricing.value
    })
    const [accountData, transactionData, orderData] = await Promise.all([
      getPointAccount(),
      getPointTransactions().catch(() => []),
      getRechargeOrders(),
      pricingRequest
    ])
    account.value = accountData || {}
    transactions.value = transactionData?.list || transactionData || []
    orders.value = orderData || []
    if (!validateAmount(selectedAmount.value)) selectAmount(quickAmounts.value[0])
  } catch (error) {
    ElMessage.warning(error?.responseData?.message || error.message || '积分数据暂时无法读取')
  }
}
async function createOrder() {
  if (!pricingReady.value) return ElMessage.warning('充值费率尚未读取成功，请刷新后重试')
  const amount = finalAmount.value
  if (!amount) return ElMessage.warning(`充值金额须为${amountHint.value}`)
  creating.value = true
  try {
    const order = await createRechargeOrder({ amount, payMethod: payType.value })
    if (!order?.paymentUrl) return ElMessage.error('支付订单创建失败，请稍后重试')
    window.location.href = order.paymentUrl
  } catch (error) {
    ElMessage.error(error?.responseData?.message || error?.response?.data?.message || '支付服务暂时不可用')
  } finally {
    creating.value = false
  }
}
async function pollReturnedOrder() {
  const orderNo = String(route.query.out_trade_no || route.query.order_no || '').trim()
  if (!orderNo) return
  paymentMessage.value = '支付已提交，正在确认到账…'
  for (let index = 0; index < 10; index += 1) {
    try {
      const order = await getRechargeOrder(orderNo)
      if (['paid', 'approved'].includes(String(order?.status).toLowerCase())) { paymentMessage.value = '支付成功，积分已到账'; await load(); return }
      if (['failed', 'rejected'].includes(String(order?.status).toLowerCase())) { paymentMessage.value = '支付失败'; return }
    } catch {}
    await new Promise(resolve => setTimeout(resolve, 2000))
  }
  paymentMessage.value = '订单正在处理中，请稍后刷新查看'
}
onMounted(async () => { await load(); await pollReturnedOrder() })
</script>

<style scoped>
.points-page{min-height:100vh;padding:20px max(32px,calc((100% - 1450px)/2)) 55px;background:linear-gradient(45deg,#fbd7ea,#f7edf5 38%,#edf1f8 65%,#dcebff);color:#252936}.top-nav{display:flex;align-items:center;justify-content:space-between}.top-nav>div{display:flex;align-items:center;gap:9px}.top-nav i{display:grid;place-items:center;width:38px;height:38px;border-radius:11px;background:linear-gradient(145deg,#4198ff,#7658ef 60%,#df66b7);color:#fff;font-style:normal;font-weight:900}.top-nav span{display:grid}.top-nav small{color:#969cad;font-size:8px}.top-nav button{border:0;background:none;color:#6757d3}.points-page>header{padding:38px 0 22px;text-align:center}.points-page>header span,.panel small,.balance-card small{color:#6e4fff;font-size:9px;font-weight:800;letter-spacing:.15em}.points-page>header h1{margin:8px 0 5px;font-size:42px}.points-page>header p,.panel p{margin:0;color:#777e90}.balance-card,.panel{border:1px solid #e7e2f2;border-radius:21px;background:#ffffffdf;box-shadow:0 18px 50px #3d356812}.balance-card{display:grid;grid-template-columns:1fr auto auto;align-items:center;gap:40px;padding:27px 32px;background:linear-gradient(135deg,#fff,#f6f2ff 58%,#fff3f9)}.balance-card>div:first-child{display:grid}.balance-card strong{font-size:42px}.balance-card>div:first-child span{color:#898f9f}.balance-meta{display:flex;gap:10px}.balance-meta article{display:grid;min-width:120px;padding:13px;border-radius:12px;background:#fff}.balance-meta span{color:#9197a6;font-size:9px}.balance-meta b{font-size:20px}.primary{padding:12px 19px;border:0;border-radius:10px;background:linear-gradient(135deg,#6e4fff,#ff55b0);color:#fff;font-weight:700}.points-grid{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:14px;margin-top:14px}.panel{padding:22px}.panel header{display:flex;justify-content:space-between;align-items:end;gap:18px}.panel h2{margin:5px 0}.recharge-panel header em{flex:none;padding:7px 11px;border-radius:99px;background:#f0ebff;color:#6e4fff;font-style:normal}.amounts{display:grid;grid-template-columns:repeat(4,1fr);gap:9px;margin:18px 0}.amounts button,.amounts label{display:grid;gap:5px;padding:16px;border:1px solid #e7e3ef;border-radius:13px;background:#fff}.amounts button b{font-size:22px}.amounts span,.amounts small{color:#9198a7;font-size:9px}.amounts .active{border-color:#8f7aea;background:#f7f3ff;box-shadow:0 0 0 2px #e8e0ff}.amounts label div{display:flex;gap:5px}.amounts input{min-width:0;width:100%;border:0;outline:0;font-size:19px}.recharge-panel footer{display:flex;justify-content:space-between;align-items:center;padding-top:14px;border-top:1px solid #efecf5}.recharge-panel footer>div{display:flex;gap:14px}.guide ul{display:grid;gap:14px;margin:18px 0 0;padding:0;list-style:none}.guide li{display:flex;gap:10px}.guide li i{display:grid;place-items:center;width:34px;height:34px;border-radius:10px;background:#eee9ff;color:#6e4fff;font-style:normal}.guide li span{display:grid}.guide li small{color:#9298a7;letter-spacing:0}.history-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:14px}.list{margin-top:14px}.list article{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:11px;border-bottom:1px solid #f0edf5}.list span{display:grid}.list small{color:#969cab;letter-spacing:0}.list strong{color:#6e4fff}.empty{display:grid;place-items:center;min-height:130px;color:#989ead}.payment-result{margin:0 0 14px;color:#17604c;background:#eefaf5}@media(max-width:900px){.balance-card,.points-grid,.history-grid{grid-template-columns:1fr}.amounts{grid-template-columns:1fr 1fr}}@media(max-width:600px){.points-page{padding-inline:12px}.balance-meta,.recharge-panel footer{align-items:stretch;flex-direction:column}.amounts{grid-template-columns:1fr}.panel header{align-items:flex-start;flex-direction:column}}
</style>
