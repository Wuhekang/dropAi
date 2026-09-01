<template>
  <main class="page">
    <header>
      <div class="brand"><i>D</i><span><b>Dokiai Academic</b><small>SCHOOL ACCOUNT</small></span></div>
      <div class="balance"><small>学校可赠送积分</small><strong>{{ data.balance || 0 }}</strong></div>
      <button @click="signOut">退出</button>
    </header>

    <section class="welcome">
      <div>
        <small>SCHOOL WORKSPACE</small>
        <h1>{{ data.schoolName || '学校账户' }}</h1>
        <p>学校编号 {{ data.schoolCode || '--' }} · 学校积分只能赠送给本校绑定用户，不能用于平台功能消费。</p>
      </div>
      <nav><button v-for="item in tabs" :key="item.key" :class="{ active: tab === item.key }" @click="changeTab(item.key)">{{ item.label }}</button><button class="pricing-setting-button" @click="openStudentPrice">⚙ 下级账号充值价设置</button></nav>
    </section>

    <section v-if="tab === 'students'" class="panel">
      <header>
        <div><small>BOUND USERS</small><h2>本校用户</h2><p>仅展示通过本校渠道绑定的普通用户。</p></div>
        <label class="search">⌕ <input v-model.trim="keyword" placeholder="搜索手机号"></label>
      </header>
      <div class="student-table">
        <header><span>用户</span><span>当前积分</span><span>注册时间</span><span>操作</span></header>
        <article v-for="student in filteredStudents" :key="student.id">
          <b>{{ student.phone }}</b>
          <strong>{{ student.points || 0 }}</strong>
          <time>{{ formatTime(student.createdAt) }}</time>
          <div class="student-actions">
            <button @click="openGift(student)">赠送积分</button>
            <button class="delete-button" @click="removeStudent(student)">删除测试账号</button>
          </div>
        </article>
        <div v-if="!filteredStudents.length" class="empty">暂无本校用户</div>
      </div>
    </section>

    <section v-else-if="tab === 'recharge'" class="panel recharge">
      <header>
        <div><small>SCHOOL RECHARGE</small><h2>学校账户充值</h2><p>当前价格：{{ money(rechargePrice) }} 元兑换 10 积分；不足 1 积分的尾数向下取整。</p></div>
        <em>{{ previewPoints }} 积分</em>
      </header>
      <label>充值金额（元）<div><span>¥</span><input v-model="amount" type="number" :min="rechargePrice" max="100000" step="0.01" placeholder="请输入金额"></div></label>
      <div class="pay">
        <label><input v-model="payMethod" type="radio" value="alipay">支付宝</label>
        <label><input v-model="payMethod" type="radio" value="wechat">微信支付</label>
        <button :disabled="creating || !validAmount" @click="recharge">{{ creating ? '正在创建…' : `立即充值 ¥${validAmount || '--'}` }}</button>
      </div>
    </section>

    <template v-else>
      <div class="metrics">
        <article><span>学校账户余额</span><strong>{{ data.balance || 0 }}</strong><small>仅用于赠送</small></article>
        <article><span>学校账户累计充值</span><strong>¥{{ money(data.totalRechargeAmount) }}</strong><small>学校自身实际支付净额</small></article>
        <article><span>学校赠送学生积分量</span><strong>{{ data.studentGiftPoints || 0 }}</strong><small>学校转交给本校用户的积分总量</small></article>
        <article><span>本校用户数</span><strong>{{ students.length }}</strong><small>当前绑定普通用户</small></article>
      </div>
      <nav class="ranges"><button v-for="r in ranges" :key="r.key" :class="{ active: range === r.key }" @click="range = r.key; loadStats()">{{ r.label }}</button></nav>
      <div class="charts"><section><h2>转交学生积分量趋势</h2><div ref="rechargeEl" class="chart"></div></section><section><h2>新增注册人数趋势</h2><div ref="registerEl" class="chart"></div></section></div>
    </template>

    <div v-if="giftStudent" class="mask" @click.self="giftStudent = null">
      <section class="modal">
        <header><div><small>POINTS GIFT</small><h2>赠送积分</h2></div><button @click="giftStudent = null">×</button></header>
        <p>赠送给 {{ giftStudent.phone }}，赠送后不可由学校收回。</p>
        <label>积分数量<input v-model.number="giftPoints" type="number" min="1" step="1"></label>
        <footer><button @click="giftStudent = null">取消</button><button class="primary" :disabled="gifting || !giftPoints" @click="submitGift">{{ gifting ? '赠送中…' : '确认赠送' }}</button></footer>
      </section>
    </div>

    <div v-if="studentPriceOpen" class="mask" @click.self="studentPriceOpen = false">
      <section class="modal price-modal">
        <header><div><small>STUDENT RECHARGE PRICING</small><h2>下级账号充值价设置</h2></div><button @click="studentPriceOpen = false">×</button></header>
        <p class="price-note">学校账户自身充值价为 {{ money(rechargePrice) }} 元 / 10 积分。这里仅设置本校下级普通用户的统一充值价，不影响学校账户自身充值。</p>
        <label>下级用户每 10 积分价格（元）<input v-model="studentPriceValue" type="number" :min="minimumStudentRechargePrice" max="1000" step="0.01" inputmode="decimal"><small>最低 {{ money(minimumStudentRechargePrice) }} 元，最高 1000 元，最多保留两位小数。</small></label>
        <footer><button :disabled="studentPriceSaving" @click="studentPriceOpen = false">取消</button><button class="primary" :disabled="studentPriceSaving || !validStudentPrice" @click="saveStudentPrice">{{ studentPriceSaving ? '保存中…' : '保存统一充值价' }}</button></footer>
      </section>
    </div>

    <div class="school-account-tools">
      <section v-if="accountMenuOpen" class="school-account-menu">
        <button @click="openPasswordDialog">修改密码</button>
        <button class="danger" @click="signOut">退出登录</button>
      </section>
      <button class="school-account-card" type="button" :aria-expanded="accountMenuOpen" @click="accountMenuOpen = !accountMenuOpen">
        <i>S</i><span><b>{{ maskedUsername }}</b><small>学校账户</small></span><em>{{ accountMenuOpen ? '⌄' : '⌃' }}</em>
      </button>
    </div>

    <change-password-dialog v-model="passwordVisible" />
  </main>
</template>

<script setup>
import * as echarts from 'echarts'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ChangePasswordDialog from '../../components/ChangePasswordDialog.vue'
import { clearAuthSession } from '../../utils/authStorage'
import {
  createRechargeOrder,
  deleteSchoolStudent,
  getSchoolStudents,
  getSchoolViewerStatistics,
  giftSchoolStudentPoints,
  logout,
  updateSchoolStudentRechargePrice
} from '../../api/rewrite'

const router = useRouter()
const tab = ref('students')
const tabs = [{ key: 'students', label: '本校用户' }, { key: 'recharge', label: '学校充值' }, { key: 'statistics', label: '数据统计' }]
const range = ref('30d')
const ranges = [{ key: '7d', label: '最近7天' }, { key: '30d', label: '最近30天' }, { key: 'monthly', label: '近一年' }]
const data = ref({})
const students = ref([])
const keyword = ref('')
const amount = ref('')
const payMethod = ref('alipay')
const creating = ref(false)
const giftStudent = ref(null)
const giftPoints = ref(null)
const gifting = ref(false)
const studentPriceOpen = ref(false)
const studentPriceSaving = ref(false)
const studentPriceValue = ref('')
const rechargeEl = ref()
const registerEl = ref()
const accountMenuOpen = ref(false)
const passwordVisible = ref(false)
const username = sessionStorage.getItem('dropai_username') || localStorage.getItem('dropai_username') || '学校账户'
let rechargeChart
let registerChart

const maskedUsername = computed(() => {
  if (/^\d{11}$/.test(username)) return `${username.slice(0, 3)}****${username.slice(-4)}`
  return username
})
const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 16) : '--'
const date = item => String(item.day || '').slice(0, 10)
const rechargePrice = computed(() => Number(data.value.rechargePricePer10 || 0.3))
const minimumStudentRechargePrice = computed(() => {
  const configuredMinimum = Number(data.value.minimumStudentRechargePricePer10)
  const ownPrice = Number(data.value.rechargePricePer10)
  return Math.max(0.3, Number.isFinite(configuredMinimum) ? configuredMinimum : 0, Number.isFinite(ownPrice) ? ownPrice : 0)
})
const currentStudentRechargePrice = computed(() => {
  const configured = Number(data.value.studentRechargePricePer10)
  return Number.isFinite(configured) && configured >= minimumStudentRechargePrice.value ? configured : minimumStudentRechargePrice.value
})
const validStudentPrice = computed(() => {
  if (String(studentPriceValue.value ?? '').trim() === '') return null
  const value = Number(studentPriceValue.value)
  return Number.isFinite(value) && value >= minimumStudentRechargePrice.value && value <= 1000 && Math.abs(value * 100 - Math.round(value * 100)) < 1e-7 ? Math.round(value * 100) / 100 : null
})
const validAmount = computed(() => {
  const value = Number(amount.value)
  return Number.isFinite(value) && value >= rechargePrice.value && value <= 100000 && Math.abs(value * 100 - Math.round(value * 100)) < 1e-7 ? Math.round(value * 100) / 100 : null
})
const previewPoints = computed(() => validAmount.value ? Math.floor((validAmount.value * 10) / rechargePrice.value + 1e-9) : 0)
const filteredStudents = computed(() => students.value.filter(item => String(item.phone || '').includes(keyword.value)))

async function loadStats() {
  data.value = await getSchoolViewerStatistics(range.value) || {}
  if (tab.value === 'statistics') { await nextTick(); draw() }
}
async function load() {
  const [stats, list] = await Promise.all([getSchoolViewerStatistics(range.value), getSchoolStudents()])
  data.value = stats || {}
  students.value = list || []
}
async function changeTab(value) {
  tab.value = value
  if (value === 'statistics') { await nextTick(); draw() }
}
function draw() {
  rechargeChart?.dispose()
  registerChart?.dispose()
  rechargeChart = echarts.init(rechargeEl.value)
  registerChart = echarts.init(registerEl.value)
  const base = { tooltip: { trigger: 'axis' }, grid: { left: 48, right: 20, top: 25, bottom: 35 }, xAxis: { type: 'category' }, yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eeeaf6' } } } }
  rechargeChart.setOption({ ...base, xAxis: { ...base.xAxis, data: (data.value.giftTrend || []).map(date) }, series: [{ type: 'line', smooth: true, lineStyle: { color: '#7056df', width: 3 }, data: (data.value.giftTrend || []).map(item => Number(item.value || 0)) }] })
  registerChart.setOption({ ...base, xAxis: { ...base.xAxis, data: (data.value.registrationTrend || []).map(date) }, series: [{ type: 'bar', itemStyle: { color: '#d765ae' }, data: (data.value.registrationTrend || []).map(item => Number(item.value || 0)) }] })
}
async function recharge() {
  if (!validAmount.value) return
  creating.value = true
  try {
    const order = await createRechargeOrder({ amount: validAmount.value, payMethod: payMethod.value })
    if (!order?.paymentUrl) throw new Error('支付订单创建失败')
    window.location.href = order.paymentUrl
  } catch (error) {
    ElMessage.error(error?.responseData?.message || error.message || '支付服务暂时不可用')
  } finally {
    creating.value = false
  }
}
function openGift(student) { giftStudent.value = student; giftPoints.value = null }
async function submitGift() {
  const points = Number(giftPoints.value)
  if (!Number.isInteger(points) || points <= 0) return ElMessage.warning('请输入正整数积分')
  await ElMessageBox.confirm(`确认向 ${giftStudent.value.phone} 赠送 ${points} 积分？赠送后不可收回。`, '确认赠送', { type: 'warning', confirmButtonText: '确认赠送', cancelButtonText: '取消' })
  gifting.value = true
  try {
    await giftSchoolStudentPoints(giftStudent.value.id, points)
    ElMessage.success('积分赠送成功')
    giftStudent.value = null
    await load()
  } finally {
    gifting.value = false
  }
}
async function removeStudent(student) {
  let currentPassword = ''
  try {
    const result = await ElMessageBox.prompt(
      `此功能仅用于删除学校掌握当前登录密码的自有测试账号，且账号不能有任何充值订单。请输入账号 ${student.phone} 的当前登录密码以确认从系统移除；账号将停用并保留审计记录。`,
      '删除测试账号',
      {
        type: 'warning',
        confirmButtonText: '验证密码并移除',
        cancelButtonText: '取消',
        confirmButtonClass: 'danger-confirm-button',
        inputType: 'password',
        inputPlaceholder: '请输入该测试账号的当前登录密码',
        inputValidator: value => (String(value || '').length >= 6 && String(value || '').length <= 72) || '请输入 6–72 位当前登录密码'
      }
    )
    currentPassword = String(result.value || '')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    throw error
  }
  try {
    await deleteSchoolStudent(student.id, currentPassword)
    ElMessage.success('测试账号已从系统移除并保留审计记录')
    await load()
  } finally {
    currentPassword = ''
  }
}
function openStudentPrice() {
  studentPriceValue.value = currentStudentRechargePrice.value.toFixed(2)
  studentPriceOpen.value = true
}
async function saveStudentPrice() {
  if (!validStudentPrice.value) return ElMessage.warning(`下级账号充值价须为 ${money(minimumStudentRechargePrice.value)}–1000 元，且最多保留两位小数`)
  studentPriceSaving.value = true
  try {
    await updateSchoolStudentRechargePrice(validStudentPrice.value)
    studentPriceOpen.value = false
    ElMessage.success('下级账号统一充值价已更新')
    await loadStats()
  } finally {
    studentPriceSaving.value = false
  }
}
function openPasswordDialog() { accountMenuOpen.value = false; passwordVisible.value = true }
function resize() { rechargeChart?.resize(); registerChart?.resize() }
async function signOut() {
  try { await logout() } catch {}
  clearAuthSession()
  router.replace('/login')
}
onMounted(() => { load(); window.addEventListener('resize', resize) })
onBeforeUnmount(() => { window.removeEventListener('resize', resize); rechargeChart?.dispose(); registerChart?.dispose() })
</script>

<style scoped>
.page{min-height:100vh;padding:24px max(28px,calc((100% - 1450px)/2)) 120px;background:linear-gradient(45deg,#fbd7ea,#f7edf5 38%,#edf1f8 65%,#dcebff);color:#292b39}.page>header,.brand,.welcome,.panel>header,.pay,.modal>header,.modal footer{display:flex;align-items:center;gap:16px}.brand i{display:grid;place-items:center;width:42px;height:42px;border-radius:12px;background:linear-gradient(145deg,#4198ff,#7658ef 60%,#df66b7);color:#fff;font-style:normal;font-weight:900}.brand span,.balance{display:grid}.brand small,.panel small,.welcome small{color:#6e4fff;font-size:8px;font-weight:800;letter-spacing:.14em}.balance{margin-left:auto;padding:10px 18px;border-radius:14px;background:#fff}.balance strong{color:#654ed1;font-size:24px}.page>header>button{border:0;background:none;color:#6855d1}.welcome,.panel,.charts section,.metrics article{margin-top:20px;padding:24px;border:1px solid #e6e1ef;border-radius:21px;background:#ffffffdf;box-shadow:0 18px 50px #3e356b12}.welcome{justify-content:space-between}.welcome h1{margin:7px 0}.welcome p,.panel p{color:#7a8191}.welcome nav,.ranges{display:flex;gap:7px}.welcome nav{flex-wrap:wrap;justify-content:flex-end}.welcome button,.ranges button,.student-table button,.pay button,.modal button{padding:9px 13px;border:1px solid #ddd7e9;border-radius:9px;background:#fff}.welcome button.active,.ranges button.active{border-color:#8f78e7;background:#f0ebff;color:#654ed1}.welcome .pricing-setting-button{border-color:#8f78e7;background:#fff;color:#654ed1;font-weight:700}.welcome .pricing-setting-button:hover,.welcome .pricing-setting-button:focus-visible{background:#f0ebff;outline:3px solid #d9cffb;outline-offset:2px}.panel>header{justify-content:space-between}.search{padding:9px 12px;border:1px solid #e2ddea;border-radius:10px}.search input{border:0;outline:0}.student-table{margin-top:18px}.student-table>header,.student-table>article{display:grid;grid-template-columns:1fr .7fr 1fr 1.2fr;align-items:center;gap:10px;padding:12px}.student-table>header{color:#9298a7;font-size:9px}.student-table>article{border-top:1px solid #efedf4}.student-actions{display:flex;flex-wrap:wrap;gap:7px}.student-table button{justify-self:start;color:#654ed1}.student-table .delete-button{border-color:#f0c5cf;background:#fff8fa;color:#c94463}.empty{display:grid;place-items:center;min-height:140px;color:#9298a7}.recharge{max-width:760px;margin-inline:auto}.recharge header em{margin-left:auto;padding:8px 12px;border-radius:99px;background:#eee9ff;color:#654ed1;font-style:normal}.recharge>label{display:grid;gap:8px;margin:25px 0}.recharge>label div{display:flex;align-items:center;padding:14px;border:1px solid #ddd7e9;border-radius:12px}.recharge input{width:100%;border:0;outline:0;font-size:24px}.pay{justify-content:flex-end}.pay button,.primary{border:0!important;background:linear-gradient(135deg,#6e4fff,#ff55b0)!important;color:#fff!important}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.metrics article{display:grid;gap:5px}.metrics strong{font-size:27px}.metrics span,.metrics small{color:#8d94a4}.ranges{margin-top:18px}.charts{display:grid;grid-template-columns:1fr 1fr;gap:16px}.chart{height:350px}.mask{position:fixed;inset:0;z-index:20;display:grid;place-items:center;background:#29234245}.modal{width:min(470px,calc(100% - 28px));padding:24px;border-radius:20px;background:#fff}.modal header{justify-content:space-between}.modal header button{border:0;font-size:22px}.modal>label{display:grid;gap:7px}.modal>label input{padding:11px;border:1px solid #ddd7e9;border-radius:9px}.modal>label small{color:#858c9d;line-height:1.6}.price-note{padding:11px;border-radius:10px;background:#f6f2ff;color:#666e80;line-height:1.65}.modal footer{justify-content:flex-end;margin-top:18px}.school-account-tools{position:fixed;left:22px;bottom:20px;z-index:12;width:292px}.school-account-card{display:grid;grid-template-columns:44px 1fr auto;align-items:center;gap:12px;width:100%;padding:12px 15px;border:2px solid #28242c;border-radius:17px;background:#ffffffed;box-shadow:0 14px 38px #3830471f;text-align:left}.school-account-card>i{display:grid;place-items:center;width:44px;height:44px;border-radius:12px;background:linear-gradient(145deg,#6e4fff,#8b66ec);color:#fff;font-size:20px;font-style:normal;font-weight:800}.school-account-card span{display:grid;min-width:0}.school-account-card b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:18px}.school-account-card small{color:#8d94a4}.school-account-card em{color:#777e90;font-style:normal}.school-account-menu{display:grid;gap:5px;margin-bottom:8px;padding:9px;border:1px solid #e2ddea;border-radius:14px;background:#fff;box-shadow:0 18px 48px #332c4d29}.school-account-menu button{padding:10px 12px;border:0;border-radius:9px;background:#fff;color:#545c70;text-align:left}.school-account-menu button:hover,.school-account-menu button:focus-visible{background:#f4f0ff;color:#654fd1}.school-account-menu .danger{color:#c94463}@media(max-width:850px){.welcome{align-items:flex-start;flex-direction:column}.metrics,.charts{grid-template-columns:1fr}.student-table{overflow:auto}.student-table>header,.student-table>article{min-width:760px}.school-account-tools{left:14px;bottom:14px;width:min(292px,calc(100% - 28px))}}
</style>
