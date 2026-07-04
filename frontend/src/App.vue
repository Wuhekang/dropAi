<template>
  <router-view />

  <div v-if="showAdminNoticeEntry" class="admin-quick-entry">
    <el-tooltip content="系统公告管理" placement="bottom">
      <el-button type="warning" :icon="Bell" @click="adminNoticeVisible = true">
        公告
      </el-button>
    </el-tooltip>
  </div>

  <admin-notice-modal v-model="adminNoticeVisible" />

  <el-dialog
    v-model="rechargeVisible"
    title="积分不足，无法继续生成"
    width="420px"
    :close-on-click-modal="false"
  >
    <div class="recharge-summary">
      <div>当前积分：<strong>{{ shortage.currentPoints }}</strong></div>
      <div>所需积分：<strong>{{ shortage.requiredPoints }}</strong></div>
      <div>还差：<strong>{{ shortage.missingPoints }}</strong> 积分</div>
    </div>
    <template #footer>
      <el-button @click="rechargeVisible = false">取消</el-button>
      <el-button type="primary" @click="goRecharge">充值积分</el-button>
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
      <el-button type="primary" @click="ackNotice">我知道了</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { Bell } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminNoticeModal from './components/AdminNoticeModal.vue'
import { getLatestNotice, markNoticeRead } from './api/rewrite'

const router = useRouter()
const route = useRoute()

const adminNoticeVisible = ref(false)
const rechargeVisible = ref(false)
const shortage = ref({ currentPoints: 0, requiredPoints: 0, missingPoints: 0 })
const noticeVisible = ref(false)
const notice = ref(null)
const noticeLoading = ref(false)

const showAdminNoticeEntry = computed(() => {
  const token = sessionStorage.getItem('dropai_token')
  const role = sessionStorage.getItem('dropai_role')
  return Boolean(token) && route.path !== '/login' && role?.toLowerCase() === 'admin'
})

function handlePointShortage(event) {
  shortage.value = {
    currentPoints: event.detail?.currentPoints || 0,
    requiredPoints: event.detail?.requiredPoints || 0,
    missingPoints: event.detail?.missingPoints || 0
  }
  rechargeVisible.value = true
}

function goRecharge() {
  rechargeVisible.value = false
  router.push({
    path: '/recharge',
    query: { redirect: route.fullPath }
  })
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
  const lines = escapeHtml(markdown).split(/\r?\n/)
  let inList = false
  let inCode = false
  const html = []
  const closeList = () => {
    if (inList) {
      html.push('</ul>')
      inList = false
    }
  }
  const inline = (text) => text.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  for (const raw of lines) {
    const line = raw.trim()
    if (line.startsWith('```')) {
      closeList()
      html.push(inCode ? '</code></pre>' : '<pre><code>')
      inCode = !inCode
      continue
    }
    if (inCode) {
      html.push(`${raw}\n`)
      continue
    }
    if (!line) {
      closeList()
      continue
    }
    if (line === '---') {
      closeList()
      html.push('<hr>')
      continue
    }
    if (line.startsWith('### ')) {
      closeList()
      html.push(`<h3>${inline(line.slice(4))}</h3>`)
      continue
    }
    if (line.startsWith('## ')) {
      closeList()
      html.push(`<h2>${inline(line.slice(3))}</h2>`)
      continue
    }
    if (line.startsWith('# ')) {
      closeList()
      html.push(`<h1>${inline(line.slice(2))}</h1>`)
      continue
    }
    if (line.startsWith('- ')) {
      if (!inList) {
        html.push('<ul>')
        inList = true
      }
      html.push(`<li>${inline(line.slice(2))}</li>`)
      continue
    }
    closeList()
    html.push(`<p>${inline(line)}</p>`)
  }
  closeList()
  if (inCode) html.push('</code></pre>')
  return html.join('')
}

const noticeHtml = computed(() => renderMarkdown(notice.value?.content || ''))

async function loadNotice() {
  const token = sessionStorage.getItem('dropai_token')
  const role = sessionStorage.getItem('dropai_role')
  if (!token || route.path === '/login' || role?.toLowerCase() === 'admin' || noticeLoading.value) return
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

watch(
  () => route.fullPath,
  () => loadNotice(),
  { immediate: true }
)
</script>

<style scoped>
.admin-quick-entry {
  position: fixed;
  top: 18px;
  right: 132px;
  z-index: 2100;
}

.admin-quick-entry :deep(.el-button) {
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.12);
}

.recharge-summary {
  display: grid;
  gap: 12px;
  padding: 8px 0;
  color: #1f2937;
  font-size: 15px;
}

.recharge-summary strong {
  color: #2563eb;
}

.notice-content {
  max-height: 56vh;
  overflow: auto;
  color: #1f2937;
  line-height: 1.7;
}

.notice-content :deep(h1),
.notice-content :deep(h2),
.notice-content :deep(h3) {
  margin: 0 0 12px;
  color: #111827;
}

.notice-content :deep(p),
.notice-content :deep(ul) {
  margin: 0 0 12px;
}

.notice-content :deep(hr) {
  border: none;
  border-top: 1px solid #e5e7eb;
  margin: 16px 0;
}

.notice-content :deep(pre) {
  overflow: auto;
  padding: 12px;
  border-radius: 8px;
  background: #0f172a;
  color: #e5e7eb;
}

@media (max-width: 720px) {
  .admin-quick-entry {
    top: 12px;
    right: 12px;
  }
}
</style>
