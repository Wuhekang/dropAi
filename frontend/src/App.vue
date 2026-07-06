<template>
  <router-view />

  <admin-notice-modal v-model="adminNoticeVisible" />

  <el-dialog
    v-model="rechargeVisible"
    title="积分不足"
    width="420px"
    :close-on-click-modal="false"
  >
    <div class="recharge-summary">
      <div>当前积分 <strong>{{ shortage.currentPoints }}</strong></div>
      <div>所需积分 <strong>{{ shortage.requiredPoints }}</strong></div>
      <div>还差积分 <strong>{{ shortage.missingPoints }}</strong></div>
    </div>
    <template #footer>
      <button class="ghost-button" type="button" @click="rechargeVisible = false">取消</button>
      <button class="primary-button" type="button" :disabled="rechargeLoading" @click="goRecharge">去充值</button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="noticeVisible"
    :title="notice?.title || '系统公告'"
    width="640px"
    class="notice-dialog"
    :close-on-click-modal="false"
  >
    <div class="notice-content" v-html="noticeHtml"></div>
    <template #footer>
      <button class="primary-button" type="button" @click="ackNotice">知道了</button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminNoticeModal from './components/AdminNoticeModal.vue'
import { createRechargeOrder, getLatestNotice, getRechargePlans, markNoticeRead } from './api/rewrite'

const router = useRouter()
const route = useRoute()

const adminNoticeVisible = ref(false)
const rechargeVisible = ref(false)
const shortage = ref({ currentPoints: 0, requiredPoints: 0, missingPoints: 0 })
const rechargeLoading = ref(false)
const noticeVisible = ref(false)
const notice = ref(null)
const noticeLoading = ref(false)

function handlePointShortage(event) {
  shortage.value = {
    currentPoints: event.detail?.currentPoints || 0,
    requiredPoints: event.detail?.requiredPoints || 0,
    missingPoints: event.detail?.missingPoints || 0
  }
  rechargeVisible.value = true
}

async function goRecharge() {
  rechargeLoading.value = true
  sessionStorage.setItem('dropai_pay_resume_path', route.fullPath)
  try {
    const plans = await getRechargePlans()
    const plan = selectRechargePlan(plans || [], shortage.value.missingPoints || shortage.value.requiredPoints)
    if (!plan) {
      router.push({ path: '/recharge', query: { redirect: route.fullPath } })
      return
    }
    const order = await createRechargeOrder({ planId: plan.planId, amount: plan.amount, payMethod: 'alipay' })
    if (!order?.paymentUrl) {
      ElMessage.error('支付链接生成失败。')
      return
    }
    window.location.href = order.paymentUrl
  } finally {
    rechargeLoading.value = false
    rechargeVisible.value = false
  }
}

function selectRechargePlan(plans, targetPoints) {
  const sorted = [...plans].sort((left, right) => (left.points || 0) - (right.points || 0))
  return sorted.find(plan => (plan.points || 0) >= targetPoints) || sorted[sorted.length - 1] || null
}

function escapeHtml(value = '') {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

function renderMarkdown(markdown = '') {
  return escapeHtml(markdown)
    .split(/\r?\n/)
    .map(line => line.trim() ? `<p>${line.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')}</p>` : '')
    .join('')
}

const noticeHtml = computed(() => renderMarkdown(notice.value?.content || ''))

async function loadNotice() {
  const token = sessionStorage.getItem('dropai_token')
  const role = sessionStorage.getItem('dropai_role')
  if (!token || ['/', '/login'].includes(route.path) || role?.toLowerCase() === 'admin' || noticeLoading.value) return
  noticeLoading.value = true
  try {
    const latest = await getLatestNotice()
    if (latest?.id) {
      notice.value = latest
      noticeVisible.value = true
    }
  } catch (error) {
    console.warn('[DropAI Notice] load failed', error)
  } finally {
    noticeLoading.value = false
  }
}

async function ackNotice() {
  if (notice.value?.id) {
    try {
      await markNoticeRead(notice.value.id)
    } catch (error) {
      console.warn('[DropAI Notice] mark read failed', error)
    }
  }
  noticeVisible.value = false
}

window.addEventListener('dropai:points-not-enough', handlePointShortage)
onBeforeUnmount(() => window.removeEventListener('dropai:points-not-enough', handlePointShortage))

watch(() => route.fullPath, () => loadNotice(), { immediate: true })
</script>

<style scoped>
.recharge-summary {
  display: grid;
  gap: 12px;
  color: var(--text);
}

.recharge-summary strong {
  color: var(--cyan);
}

.notice-content {
  max-height: 56vh;
  overflow: auto;
  color: var(--text);
  line-height: 1.7;
}

.notice-content :deep(p) {
  margin: 0 0 12px;
}
</style>
