<template>
  <div class="recharge-page">
    <header class="page-header">
      <div>
        <h1>积分充值</h1>
        <p>选择套餐后完成支付，积分到账后可继续生成任务。</p>
      </div>
      <div class="balance">
        <span>当前积分</span>
        <strong>{{ account?.points ?? '--' }}</strong>
      </div>
    </header>

    <section class="plans">
      <button
        v-for="plan in plans"
        :key="plan.amount"
        class="plan-card"
        :class="{ active: selected?.amount === plan.amount }"
        @click="selected = plan"
      >
        <span v-if="plan.recommended" class="badge">推荐</span>
        <strong>{{ plan.amount }} 元</strong>
        <span>{{ plan.points }} 积分</span>
      </button>
    </section>

    <section class="pay-panel">
      <h2>支付方式</h2>
      <el-radio-group v-model="payMethod">
        <el-radio-button label="alipay_mock">支付宝</el-radio-button>
        <el-radio-button label="wechat_reserved">微信支付</el-radio-button>
      </el-radio-group>
      <el-button type="primary" :loading="paying" :disabled="!selected" @click="createAndPay">
        确认充值 {{ selected?.points || 0 }} 积分
      </el-button>
      <p class="hint">当前为模拟支付接口，支付成功后会立即写入积分。</p>
    </section>

    <section class="orders" v-if="orders.length">
      <h2>最近订单</h2>
      <div v-for="order in orders" :key="order.orderNo" class="order-row">
        <span>{{ order.orderNo }}</span>
        <span>{{ order.amount }} 元 / {{ order.points }} 积分</span>
        <el-tag :type="order.status === 'paid' ? 'success' : 'warning'">{{ order.status }}</el-tag>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createRechargeOrder,
  getPointAccount,
  getRechargeOrders,
  getRechargePlans,
  mockPayRechargeOrder
} from '../../api/rewrite'

const route = useRoute()
const router = useRouter()

const account = ref(null)
const plans = ref([])
const orders = ref([])
const selected = ref(null)
const payMethod = ref('alipay_mock')
const paying = ref(false)

async function loadData() {
  const [accountData, planData, orderData] = await Promise.all([
    getPointAccount(),
    getRechargePlans(),
    getRechargeOrders()
  ])
  account.value = accountData
  plans.value = planData || []
  orders.value = orderData || []
  selected.value = plans.value.find(item => item.recommended) || plans.value[0] || null
}

async function createAndPay() {
  if (!selected.value) return
  paying.value = true
  try {
    const order = await createRechargeOrder({
      amount: selected.value.amount,
      payMethod: payMethod.value
    })
    await mockPayRechargeOrder(order.orderNo)
    ElMessage.success('充值成功，积分已到账')
    await loadData()
    const redirect = route.query.redirect
    if (redirect && redirect !== '/recharge') {
      router.replace(String(redirect))
    }
  } finally {
    paying.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.recharge-page {
  min-height: 100vh;
  padding: 32px;
  background: #f5f7fb;
  color: #111827;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.page-header h1 {
  margin: 0;
  font-size: 28px;
}

.page-header p {
  margin: 8px 0 0;
  color: #6b7280;
}

.balance {
  display: grid;
  gap: 4px;
  min-width: 140px;
  padding: 16px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background: #fff;
}

.balance span {
  color: #6b7280;
  font-size: 13px;
}

.balance strong {
  color: #2563eb;
  font-size: 28px;
}

.plans {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.plan-card {
  position: relative;
  display: grid;
  gap: 10px;
  min-height: 128px;
  padding: 20px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background: #fff;
  color: #111827;
  text-align: left;
  cursor: pointer;
}

.plan-card.active {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.plan-card strong {
  font-size: 26px;
}

.badge {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #eaf2ff;
  color: #2563eb;
  font-size: 12px;
}

.pay-panel,
.orders {
  display: grid;
  gap: 16px;
  padding: 20px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background: #fff;
  margin-bottom: 24px;
}

.pay-panel h2,
.orders h2 {
  margin: 0;
  font-size: 18px;
}

.pay-panel .el-button {
  width: fit-content;
}

.hint {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
}

.order-row {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(160px, auto) auto;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid #edf1f7;
}
</style>
