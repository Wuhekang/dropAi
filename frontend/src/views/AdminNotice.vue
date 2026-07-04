<template>
  <div class="notice-admin">
    <header class="page-header">
      <div>
        <h1>公告管理</h1>
        <p>编辑并发布登录弹窗公告，非管理员用户每天最多看到一次。</p>
      </div>
      <el-button @click="$router.push('/dashboard')">返回首页</el-button>
    </header>

    <section class="editor-grid">
      <div class="editor-panel">
        <el-input v-model="form.title" placeholder="公告标题" />
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="16"
          placeholder="# DropAI 系统更新公告"
        />
        <div class="options">
          <el-switch v-model="form.isPopup" active-text="登录弹窗" inactive-text="仅存档" />
          <el-radio-group v-model="form.status">
            <el-radio-button label="active">启用</el-radio-button>
            <el-radio-button label="inactive">禁用</el-radio-button>
          </el-radio-group>
        </div>
        <el-button type="primary" :loading="saving" @click="saveNotice">发布公告</el-button>
      </div>

      <div class="preview-panel">
        <h2>{{ form.title || '公告预览' }}</h2>
        <div class="markdown-preview" v-html="previewHtml"></div>
      </div>
    </section>

    <section class="history">
      <h2>历史公告</h2>
      <el-table :data="notices" style="width: 100%">
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="弹窗" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isPopup ? 'success' : 'info'">{{ row.isPopup ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="160" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="loadToForm(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { getAdminNotices, publishNotice, updateNotice } from '../api/rewrite'

const notices = ref([])
const saving = ref(false)
const editingId = ref(null)
const form = ref({
  title: 'DropAI 系统更新公告',
  content: '# DropAI 系统更新公告\n\n- 积分充值功能已上线\n- 所有生成任务都会进行积分校验\n\n---\n\n请确保账户积分充足后再使用生成功能。',
  status: 'active',
  isPopup: true
})

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
  return html.join('')
}

const previewHtml = computed(() => renderMarkdown(form.value.content))

async function loadNotices() {
  notices.value = await getAdminNotices()
}

function loadToForm(row) {
  editingId.value = row.id
  form.value = {
    title: row.title,
    content: row.content,
    status: row.status,
    isPopup: Boolean(row.isPopup)
  }
}

async function saveNotice() {
  saving.value = true
  try {
    const payload = { ...form.value }
    if (editingId.value) {
      await updateNotice(editingId.value, payload)
    } else {
      await publishNotice(payload)
    }
    ElMessage.success('公告已保存')
    editingId.value = null
    await loadNotices()
  } finally {
    saving.value = false
  }
}

onMounted(loadNotices)
</script>

<style scoped>
.notice-admin {
  min-height: 100vh;
  padding: 28px;
  background: #f5f7fb;
  color: #111827;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0;
  font-size: 26px;
}

.page-header p {
  margin: 8px 0 0;
  color: #6b7280;
}

.editor-grid {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(320px, 1fr);
  gap: 18px;
  margin-bottom: 20px;
}

.editor-panel,
.preview-panel,
.history {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background: #fff;
}

.options {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
}

.preview-panel h2,
.history h2 {
  margin: 0;
  font-size: 18px;
}

.markdown-preview {
  min-height: 320px;
  max-height: 520px;
  overflow: auto;
  line-height: 1.7;
}

.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3) {
  margin: 0 0 12px;
}

.markdown-preview :deep(p),
.markdown-preview :deep(ul) {
  margin: 0 0 12px;
}

.markdown-preview :deep(hr) {
  border: none;
  border-top: 1px solid #e5e7eb;
  margin: 16px 0;
}

@media (max-width: 900px) {
  .editor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
