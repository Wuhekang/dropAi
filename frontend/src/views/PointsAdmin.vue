<template>
  <main class="points-admin-page">
    <header class="admin-header">
      <div>
        <span class="eyebrow">DROP AI POINTS CENTER</span>
        <h1>积分管理</h1>
        <p>管理各生成能力的积分消耗和启用状态。当前版本仅做积分账本，不接入微信支付。</p>
      </div>
      <el-button @click="router.push('/dashboard')">返回首页</el-button>
    </header>

    <el-card class="pricing-card" shadow="never">
      <template #header>
        <div class="section-head">
          <div>
            <h2>功能价格管理</h2>
            <p>修改后立即对后续生成请求生效。</p>
          </div>
          <el-button :loading="loading" @click="loadPricing">刷新</el-button>
        </div>
      </template>

      <el-table :data="pricing" empty-text="暂无功能价格配置">
        <el-table-column prop="featureCode" label="功能编码" min-width="170" />
        <el-table-column prop="featureName" label="功能名称" min-width="180" />
        <el-table-column label="消耗积分" width="180">
          <template #default="{ row }">
            <el-input-number v-model="row.costPoints" :min="0" :step="10" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="启用" width="110">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" :loading="savingCode === row.featureCode" @click="save(row)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="pricing-card" shadow="never">
      <template #header><h2>后续预留</h2></template>
      <div class="reserved-grid">
        <div><strong>充值接口</strong><p>预留积分充值入口，当前暂不开放支付。</p></div>
        <div><strong>订单接口</strong><p>后续可记录套餐、订单状态和发票信息。</p></div>
        <div><strong>支付接口</strong><p>预留微信支付/其他支付通道接入点。</p></div>
      </div>
    </el-card>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getFeaturePricing, updateFeaturePricing } from '../api/rewrite'

const router = useRouter()
const pricing = ref([])
const loading = ref(false)
const savingCode = ref('')

async function loadPricing() {
  loading.value = true
  try {
    pricing.value = await getFeaturePricing() || []
  } finally {
    loading.value = false
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

onMounted(loadPricing)
</script>

<style scoped>
.points-admin-page{max-width:1180px;margin:0 auto;padding:38px 24px 70px}
.admin-header,.section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:20px}
.eyebrow{color:#2563eb;font-size:12px;font-weight:800;letter-spacing:.15em}
h1{margin:8px 0 6px;font-size:36px}h2{margin:0 0 6px}.admin-header p,.section-head p{margin:0;color:#64748b;line-height:1.7}
.pricing-card{border-radius:20px;margin-top:22px}.reserved-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}
.reserved-grid div{padding:18px;border-radius:16px;background:#f8fafc;border:1px solid #e2e8f0}.reserved-grid p{color:#64748b;line-height:1.7}
@media(max-width:860px){.admin-header,.section-head{display:block}.reserved-grid{grid-template-columns:1fr}}
</style>
