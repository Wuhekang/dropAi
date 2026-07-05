<template>
  <div class="recharge-page">
    <header class="page-header">
      <div>
        <h1>积分充值</h1>
        <p>选择套餐后跳转易支付收银台，支付成功后积分自动到账。</p>
      </div>
      <div class="balance">
        <span>当前积分</span>
        <strong>{{ account?.points ?? '--' }}</strong>
      </div>
    </header>

    <section class="plans">
      <button
        v-for="plan in plans"
        :key="plan.planId || plan.amount"
        class="plan-card"
        :class="{ active: selected?.planId === plan.planId }"
        type="button"
        @click="selected = plan"
      >
        <span v-if="plan.recommended" class="badge">推荐</span>
        <strong>{{ plan.amount }} 元</strong>
        <span>{{ plan.points }} 积分</span>
      </button>
    </section>

    <section class="pay-panel">
      <div class="section-head">
        <div>
          <h2>在线支付</h2>
          <p>订单创建后将进入易支付页面，可使用支付宝或微信完成付款。</p>
        </div>
        <el-select v-model="payType" class="pay-type">
          <el-option label="支付宝" value="alipay" />
          <el-option label="微信支付" value="wxpay" />
          <el-option label="QQ钱包" value="qqpay" />
        </el-select>
      </div>

      <el-button type="primary" size="large" :loading="creating" :disabled="!selected" @click="createAndRedirect">
        立即充值 {{ selected?.points || 0 }} 积分
      </el-button>
      <p class="hint">支付回调会自动校验签名和订单金额，重复回调不会重复加积分。</p>
    </section>

    <section class="orders" v-if="orders.length">
      <div class="section-head compact">
        <h2>最近订单</h2>
        <el-button :loading="loading" @click="loadData">刷新</el-button>
      </div>
      <div v-for="order in orders" :key="order.orderNo" class="order-row">
        <span>{{ order.orderNo }}</span>
        <span>{{ order.amount }} 元 / {{ order.points }} 积分</span>
        <el-tag :type="statusType(order.status)">{{ statusText(order.status) }}</el-tag>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import {
  createRechargeOrder,
  getPointAccount,
  getRechargeOrders,
  getRechargePlans
} from '../../api/rewrite'

const account = ref(null)
const plans = ref([])
const orders = ref([])
const selected = ref(null)
const payType = ref('alipay')
const loading = ref(false)
const creating = ref(false)

async function loadData() {
  loading.value = true
  try {
    const [accountData, planData, orderData] = await Promise.all([
      getPointAccount(),
      getRechargePlans(),
      getRechargeOrders()
    ])
    account.value = accountData
    plans.value = planData || []
    orders.value = orderData || []
    selected.value = selected.value || plans.value.find(item => item.recommended) || plans.value[0] || null
  } finally {
    loading.value = false
  }
}

async function createAndRedirect() {
  if (!selected.value) return
  creating.value = true
  try {
    const order = await createRechargeOrder({
      planId: selected.value.planId,
      amount: selected.value.amount,
      payMethod: payType.value
    })
    if (!order?.paymentUrl) {
      ElMessage.error('支付链接生成失败，请稍后重试')
      return
    }
    window.location.href = order.paymentUrl
  } finally {
    creating.value = false
  }
}

function statusText(status) {
  const map = {
    pending: '待支付',
    waiting_review: '待审核',
    approved: '已到账',
    paid: '已到账',
    rejected: '已驳回'
  }
  return map[status] || status
}

function statusType(status) {
  if (status === 'approved' || status === 'paid') return 'success'
  if (status === 'rejected') return 'danger'
  if (status === 'waiting_review') return 'warning'
  return 'info'
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

.page-header,
.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h1,
.pay-panel h2,
.orders h2 {
  margin: 0;
}

.page-header p,
.section-head p,
.hint {
  margin: 8px 0 0;
  color: #6b7280;
}

.balance,
.pay-panel,
.orders {
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background: #fff;
}

.balance {
  display: grid;
  gap: 4px;
  min-width: 140px;
  padding: 16px;
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
  margin-bottom: 24px;
}

.pay-type {
  width: 140px;
}

.compact {
  align-items: center;
}

.order-row {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(160px, auto) auto;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid #edf1f7;
}

@media (max-width: 760px) {
  .recharge-page {
    padding: 20px;
  }

  .page-header,
  .section-head {
    display: grid;
  }

  .order-row {
    grid-template-columns: 1fr;
  }
}
</style>
