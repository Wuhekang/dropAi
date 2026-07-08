<template>
  <main class="points-admin-page">
    <header class="admin-header">
      <div>
        <span class="eyebrow">DROP AI POINTS CENTER</span>
        <h1>积分管理</h1>
        <p>管理生成能力的积分消耗，并审核用户通过个人码提交的充值订单。</p>
      </div>
      <el-button @click="router.push('/dashboard')">返回首页</el-button>
    </header>

    <el-card class="admin-card" shadow="never">
      <template #header>
        <div class="section-head">
          <div>
            <h2>充值审核</h2>
            <p>审核通过后系统会增加用户积分，并写入积分流水。</p>
          </div>
          <el-button :loading="ordersLoading" @click="loadOrders">刷新</el-button>
        </div>
      </template>

      <el-table :data="reviewOrders" empty-text="暂无充值订单">
        <el-table-column prop="orderNo" label="订单号" min-width="190" />
        <el-table-column prop="amount" label="金额" width="90" />
        <el-table-column prop="points" label="积分" width="90" />
        <el-table-column prop="payAccountLast4" label="后四位" width="100" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="凭证" min-width="180">
          <template #default="{ row }">
            <span class="proof">{{ row.proofImage || '未填写' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              :disabled="row.status !== 'waiting_review'"
              :loading="auditingOrder === `${row.orderNo}:approved`"
              @click="audit(row, 'approved')"
            >
              通过
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :disabled="row.status !== 'waiting_review'"
              :loading="auditingOrder === `${row.orderNo}:rejected`"
              @click="audit(row, 'rejected')"
            >
              驳回
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="admin-card" shadow="never">
      <template #header>
        <div class="section-head">
          <div>
            <h2>功能价格管理</h2>
            <p>修改后立即对后续生成请求生效。</p>
          </div>
          <el-button :loading="loading" @click="loadPricing">刷新</el-button>
        </div>
      </template>

      <div v-if="pricing.length" class="pricing-grid">
        <article v-for="row in pricing" :key="row.featureCode" class="pricing-card">
          <div>
            <span>{{ row.featureCode }}</span>
            <strong>{{ displayFeatureName(row) }}</strong>
          </div>
          <el-input-number v-model="row.costPoints" :min="0" :step="10" controls-position="right" />
          <div class="pricing-actions">
            <el-switch v-model="row.enabled" />
            <el-button type="primary" :loading="savingCode === row.featureCode" @click="save(row)">保存</el-button>
          </div>
        </article>
      </div>
      <el-empty v-else description="暂无功能价格配置" />
    </el-card>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  auditRechargeOrder,
  getFeaturePricing,
  getRechargeReviewOrders,
  updateFeaturePricing
} from '../api/rewrite'

const router = useRouter()
const pricing = ref([])
const reviewOrders = ref([])
const loading = ref(false)
const ordersLoading = ref(false)
const savingCode = ref('')
const auditingOrder = ref('')

async function loadPricing() {
  loading.value = true
  try {
    pricing.value = await getFeaturePricing() || []
  } finally {
    loading.value = false
  }
}

async function loadOrders() {
  ordersLoading.value = true
  try {
    reviewOrders.value = await getRechargeReviewOrders() || []
  } finally {
    ordersLoading.value = false
  }
}

async function save(row) {
  savingCode.value = row.featureCode
  try {
    await updateFeaturePricing(row.featureCode, {
      costPoints: row.costPoints,
      enabled: row.enabled
    })
    ElMessage.success('功能价格已更新')
    await loadPricing()
  } finally {
    savingCode.value = ''
  }
}

async function audit(row, status) {
  auditingOrder.value = `${row.orderNo}:${status}`
  try {
    await auditRechargeOrder({ orderNo: row.orderNo, status })
    ElMessage.success(status === 'approved' ? '订单已通过，积分已到账' : '订单已驳回')
    await loadOrders()
  } finally {
    auditingOrder.value = ''
  }
}

function statusText(status) {
  const map = {
    pending: '待支付',
    waiting_review: '待审核',
    approved: '已通过',
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

function displayFeatureName(row) {
  const map = {
    DOCUMENT_REWRITE: '标准优化',
    DOCUMENT_HUMANIZE: 'AI痕迹优化',
    DOCUMENT_DOUBLE: '深度优化'
  }
  return map[row.featureCode] || row.featureName
}

onMounted(() => {
  loadOrders()
  loadPricing()
})
</script>

<style scoped>
.points-admin-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 38px 24px 70px;
  animation: page-in .55s ease both;
}

.admin-header,
.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.eyebrow {
  color: var(--primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .15em;
}

h1 {
  margin: 8px 0 6px;
  font-size: 36px;
}

h2 {
  margin: 0 0 6px;
}

.admin-header p,
.section-head p {
  margin: 0;
  color: var(--muted);
  line-height: 1.7;
}

.admin-card {
  border-radius: 8px;
  margin-top: 22px;
}

.pricing-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.pricing-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(108, 99, 255, 0.1);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
}

.pricing-card span {
  color: var(--primary);
  font-size: 12px;
  font-weight: 800;
}

.pricing-card strong {
  display: block;
  margin-top: 6px;
  font-size: 20px;
}

.pricing-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.proof {
  display: block;
  max-width: 260px;
  overflow: hidden;
  color: #64748b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 860px) {
  .admin-header,
  .section-head {
    display: block;
  }

  .pricing-grid {
    grid-template-columns: 1fr;
  }
}
</style>
