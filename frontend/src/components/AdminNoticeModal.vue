<template>
  <el-dialog
    v-model="visible"
    title="系统公告管理"
    width="980px"
    class="admin-notice-modal"
    :close-on-click-modal="false"
    @open="loadLatest"
  >
    <div class="notice-editor">
      <section class="editor-panel">
        <el-input v-model="form.title" placeholder="公告标题" />
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="18"
          placeholder="# DropAI 系统公告"
        />
      </section>

      <section class="preview-panel">
        <div class="preview-head">
          <span>实时预览</span>
          <el-tag :type="form.status === 'active' ? 'success' : 'info'">
            {{ form.status === 'active' ? '发布' : '草稿' }}
          </el-tag>
        </div>
        <div class="markdown-preview" v-html="previewHtml"></div>
      </section>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button :loading="saving" @click="saveDraft">保存草稿</el-button>
      <el-button type="primary" :loading="publishing" @click="publishNotice">发布公告</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import { getAdminNoticeLatest, publishAdminNotice, saveAdminNotice } from '../api/rewrite'

const visible = defineModel({ type: Boolean, default: false })

const saving = ref(false)
const publishing = ref(false)
const form = ref(defaultForm())

function defaultForm() {
  return {
    id: null,
    title: 'DropAI 系统更新公告',
    content: '# DropAI 系统更新公告\n\n- 积分充值功能已上线\n- 所有生成任务都会进行积分校验\n\n---\n\n请确保账户积分充足后再使用生成功能。',
    status: 'draft',
    isPopup: false
  }
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

const previewHtml = computed(() => renderMarkdown(form.value.content))

async function loadLatest() {
  try {
    const latest = await getAdminNoticeLatest()
    if (latest?.id) {
      form.value = {
        id: latest.id,
        title: latest.title,
        content: latest.content,
        status: latest.status || 'draft',
        isPopup: Boolean(latest.isPopup)
      }
    }
  } catch (error) {
    console.warn('[DropAI Notice Admin] load latest failed', error)
  }
}

async function saveDraft() {
  saving.value = true
  try {
    const saved = await saveAdminNotice({
      ...form.value,
      status: 'draft',
      isPopup: false
    })
    form.value.id = saved.id
    form.value.status = 'draft'
    form.value.isPopup = false
    ElMessage.success('草稿已保存')
  } finally {
    saving.value = false
  }
}

async function publishNotice() {
  publishing.value = true
  try {
    const saved = await saveAdminNotice({
      ...form.value,
      status: 'active',
      isPopup: true
    })
    const published = await publishAdminNotice(saved.id)
    form.value = {
      id: published.id,
      title: published.title,
      content: published.content,
      status: published.status,
      isPopup: Boolean(published.isPopup)
    }
    ElMessage.success('公告已发布')
    visible.value = false
  } finally {
    publishing.value = false
  }
}
</script>

<style scoped>
.notice-editor {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(320px, 1fr);
  gap: 16px;
}

.editor-panel,
.preview-panel {
  display: grid;
  gap: 12px;
}

.preview-panel {
  min-height: 440px;
  padding: 14px;
  border: 1px solid #e4e9f1;
  border-radius: 8px;
  background: #f8fafc;
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #64748b;
  font-size: 13px;
}

.markdown-preview {
  max-height: 420px;
  overflow: auto;
  color: #1f2937;
  line-height: 1.7;
}

.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3) {
  margin: 0 0 12px;
  color: #111827;
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

.markdown-preview :deep(pre) {
  overflow: auto;
  padding: 12px;
  border-radius: 8px;
  background: #0f172a;
  color: #e5e7eb;
}

@media (max-width: 820px) {
  .notice-editor {
    grid-template-columns: 1fr;
  }
}
</style>
