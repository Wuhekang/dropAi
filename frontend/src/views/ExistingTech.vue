<template>
  <main class="existing-tech-page">
    <header class="hero">
      <div>
        <el-button text type="primary" @click="router.push('/dashboard')">返回工作台</el-button>
        <span class="eyebrow">EXISTING TECHNOLOGY</span>
        <h1>现有技术</h1>
        <p>基于成熟技术方案的文本优化与 AIGC 处理工具，支持论文优化、内容改写、摘要标题生成和文档处理。</p>
      </div>
      <div class="hero-badge">
        <strong>Mock 接口模式</strong>
        <span>已按异步任务流程预留真实 API 接入点</span>
      </div>
    </header>

    <section class="tech-shell">
      <aside class="left-panel">
        <el-card shadow="never" class="glass-card">
          <template #header><strong>功能选择</strong></template>
          <div class="mode-grid">
            <button
              v-for="item in features"
              :key="item.key"
              type="button"
              :class="{ active: selectedFeature === item.key }"
              @click="selectedFeature = item.key"
            >
              <span>{{ item.icon }}</span>
              <strong>{{ item.name }}</strong>
              <small>{{ item.desc }}</small>
            </button>
          </div>
          <el-alert v-if="!selectedFeature" class="tip" type="warning" title="请选择一个功能后再开始处理" :closable="false" />
        </el-card>

        <el-card shadow="never" class="glass-card">
          <template #header><strong>参数设置</strong></template>
          <el-form label-position="top">
            <el-form-item label="处理强度">
              <el-radio-group v-model="settings.strength">
                <el-radio-button label="轻度" />
                <el-radio-button label="标准" />
                <el-radio-button label="深度" />
              </el-radio-group>
            </el-form-item>
            <el-form-item label="输出风格">
              <el-select v-model="settings.outputStyle" class="full-select">
                <el-option label="学术" value="学术" />
                <el-option label="通顺" value="通顺" />
                <el-option label="自然" value="自然" />
                <el-option label="降 AI" value="降 AI" />
              </el-select>
            </el-form-item>
            <div class="switch-list">
              <el-switch v-model="settings.keepMeaning" active-text="保留原意" />
              <el-switch v-model="settings.keepTerms" active-text="保留专业术语" />
              <el-switch v-model="settings.segmented" active-text="分段处理" />
            </div>
          </el-form>
        </el-card>

        <el-card shadow="never" class="glass-card status-card">
          <template #header><strong>处理状态</strong></template>
          <el-tag :type="statusType(taskStatus)" size="large">{{ statusLabel }}</el-tag>
          <p>{{ statusMessage }}</p>
          <el-progress v-if="taskStatus === 'running'" :percentage="progress" striped striped-flow />
          <el-alert v-if="errorMessage" type="error" :title="errorMessage" :closable="false" />
        </el-card>
      </aside>

      <section class="right-panel">
        <el-card shadow="never" class="glass-card input-card">
          <template #header>
            <div class="card-head">
              <strong>内容输入</strong>
              <el-segmented v-model="inputMode" :options="['粘贴文本', '上传文档']" />
            </div>
          </template>

          <template v-if="inputMode === '粘贴文本'">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="13"
              resize="none"
              placeholder="请粘贴需要处理的论文段落、正文、摘要或标题素材..."
              maxlength="30000"
              show-word-limit
            />
          </template>

          <template v-else>
            <el-upload
              drag
              action=""
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleFileChange"
              accept=".doc,.docx,.pdf,.txt"
            >
              <div class="upload-inner">
                <strong>拖拽或点击上传文档</strong>
                <span>支持 .doc / .docx / .pdf / .txt，单文件不超过 20MB</span>
              </div>
            </el-upload>

            <div v-if="uploadedFile.name" class="file-row" :class="uploadedFile.status">
              <div>
                <strong>{{ uploadedFile.name }}</strong>
                <span>{{ formatSize(uploadedFile.size) }} · {{ fileStatusText }}</span>
              </div>
              <el-button text type="danger" @click="removeFile">删除</el-button>
            </div>
          </template>

          <div class="action-row">
            <el-button type="primary" size="large" :loading="processing" :disabled="processing" @click="startProcess">
              {{ processing ? '处理中，请稍候' : '开始处理' }}
            </el-button>
            <el-button size="large" :disabled="processing" @click="clearAll">清空内容</el-button>
            <el-button size="large" :disabled="!resultText" @click="copyResult">复制结果</el-button>
            <el-button size="large" :disabled="!resultText" @click="downloadResult">下载结果</el-button>
          </div>
        </el-card>

        <el-card shadow="never" class="glass-card result-card">
          <template #header>
            <div class="card-head">
              <strong>结果展示</strong>
              <el-tag :type="statusType(taskStatus)">{{ statusLabel }}</el-tag>
            </div>
          </template>
          <el-empty v-if="!resultText && taskStatus !== 'running'" description="处理完成后将在这里展示结果" />
          <div v-else-if="taskStatus === 'running'" class="loading-preview">
            <el-skeleton :rows="7" animated />
          </div>
          <pre v-else class="result-text">{{ resultText }}</pre>
        </el-card>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  downloadExistingTechResult,
  getExistingTechResult,
  getExistingTechTaskStatus,
  submitExistingTechTask,
  uploadExistingTechFile
} from '../api/existingTech'

const router = useRouter()
const features = [
  { key: 'reduce-ai', name: '论文降 AI', icon: 'AI', desc: '降低机器写作痕迹' },
  { key: 'reduce-dup', name: '论文降重', icon: 'DR', desc: '优化重复表达' },
  { key: 'polish', name: '文本润色', icon: 'PL', desc: '提升通顺度与规范性' },
  { key: 'rewrite', name: '内容改写', icon: 'RW', desc: '保持语义重构表述' },
  { key: 'abstract', name: '摘要生成', icon: 'AB', desc: '生成论文摘要草稿' },
  { key: 'title', name: '标题生成', icon: 'TL', desc: '生成规范标题方案' }
]
const selectedFeature = ref('reduce-ai')
const inputMode = ref('粘贴文本')
const inputText = ref('')
const resultText = ref('')
const taskId = ref('')
const taskStatus = ref('idle')
const statusMessage = ref('请选择功能并输入文本或上传文档。')
const errorMessage = ref('')
const progress = ref(0)
const processing = computed(() => taskStatus.value === 'running')
const settings = reactive({ strength: '标准', outputStyle: '学术', keepMeaning: true, keepTerms: true, segmented: true })
const uploadedFile = reactive({ fileId: '', name: '', size: 0, status: '', message: '', raw: null })

const selectedFeatureInfo = computed(() => features.find(item => item.key === selectedFeature.value))
const statusLabel = computed(() => ({ idle: '等待处理', running: '处理中', success: '处理成功', failed: '处理失败' })[taskStatus.value] || taskStatus.value)
const fileStatusText = computed(() => ({ success: '上传成功', failed: '上传失败', uploading: '上传中' })[uploadedFile.status] || uploadedFile.message || '待上传')

async function handleFileChange(file) {
  errorMessage.value = ''
  const raw = file.raw
  if (!raw) return
  const ext = raw.name.slice(raw.name.lastIndexOf('.')).toLowerCase()
  if (!['.doc', '.docx', '.pdf', '.txt'].includes(ext)) {
    errorMessage.value = '文件格式错误，仅支持 .doc / .docx / .pdf / .txt'
    ElMessage.error(errorMessage.value)
    return
  }
  if (raw.size > 20 * 1024 * 1024) {
    errorMessage.value = '文件过大，单个文件不能超过 20MB'
    ElMessage.error(errorMessage.value)
    return
  }
  Object.assign(uploadedFile, { raw, name: raw.name, size: raw.size, status: 'uploading', message: '上传中' })
  try {
    const result = await uploadExistingTechFile(raw)
    Object.assign(uploadedFile, { fileId: result.fileId, status: result.status || 'success', message: result.message || '上传成功' })
  } catch (error) {
    uploadedFile.status = 'failed'
    uploadedFile.message = friendlyError(error)
  }
}

function removeFile() {
  Object.assign(uploadedFile, { fileId: '', name: '', size: 0, status: '', message: '', raw: null })
}

async function startProcess() {
  errorMessage.value = ''
  resultText.value = ''
  if (!selectedFeature.value) return fail('请先选择一个处理功能。')
  if (!inputText.value.trim() && !uploadedFile.fileId) return fail('请粘贴文本或上传文档后再开始处理。')
  if (uploadedFile.status === 'failed') return fail(uploadedFile.message || '文件上传失败，请重新上传。')

  taskStatus.value = 'running'
  statusMessage.value = '处理中，请稍候'
  progress.value = 15
  try {
    const submit = await submitExistingTechTask({
      feature: selectedFeature.value,
      featureName: selectedFeatureInfo.value?.name,
      text: inputText.value,
      fileId: uploadedFile.fileId,
      fileName: uploadedFile.name,
      ...settings
    })
    taskId.value = submit.taskId
    await pollTask(submit.taskId)
  } catch (error) {
    taskStatus.value = 'failed'
    errorMessage.value = friendlyError(error)
    statusMessage.value = errorMessage.value
  }
}

async function pollTask(id) {
  for (let i = 0; i < 12; i++) {
    const status = await getExistingTechTaskStatus(id)
    taskStatus.value = status.status
    statusMessage.value = status.message || statusLabel.value
    progress.value = Math.min(92, 25 + i * 12)
    if (status.status === 'success') {
      const result = await getExistingTechResult(id)
      resultText.value = result.result || ''
      if (!resultText.value.trim()) throw new Error('结果为空，请重新提交任务。')
      progress.value = 100
      statusMessage.value = '处理成功，结果已生成。'
      return
    }
    if (status.status === 'failed') throw new Error(status.message || '处理失败')
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  throw new Error('任务处理超时，请稍后重试。')
}

function clearAll() {
  inputText.value = ''
  resultText.value = ''
  taskId.value = ''
  taskStatus.value = 'idle'
  statusMessage.value = '请选择功能并输入文本或上传文档。'
  errorMessage.value = ''
  progress.value = 0
  removeFile()
}

async function copyResult() {
  if (!resultText.value) return
  await navigator.clipboard.writeText(resultText.value)
  ElMessage.success('结果已复制')
}

async function downloadResult() {
  if (!resultText.value) return
  const blob = taskId.value ? await downloadExistingTechResult(taskId.value) : new Blob([resultText.value], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${selectedFeatureInfo.value?.name || '现有技术'}-处理结果.txt`
  link.click()
  URL.revokeObjectURL(url)
}

function fail(message) {
  errorMessage.value = message
  taskStatus.value = 'failed'
  statusMessage.value = message
  ElMessage.warning(message)
}
function friendlyError(error) { return error?.message || '网络错误，请稍后重试。' }
function formatSize(size) { return size >= 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(2)} MB` : `${Math.max(1, Math.round(size / 1024))} KB` }
function statusType(status) { return status === 'success' ? 'success' : status === 'failed' ? 'danger' : status === 'running' ? 'warning' : 'info' }
</script>

<style scoped>
.existing-tech-page{max-width:1480px;margin:0 auto;padding:34px 24px 72px;color:#172033}
.hero{display:flex;justify-content:space-between;gap:26px;align-items:flex-start;padding:30px;border-radius:28px;background:linear-gradient(135deg,#f8fbff,#eef4ff 45%,#f6f0ff);border:1px solid #e0e7ff;box-shadow:0 24px 80px rgba(37,99,235,.10)}
.eyebrow{display:block;margin-top:8px;color:#2563eb;font-weight:900;font-size:12px;letter-spacing:.18em}
.hero h1{margin:10px 0;font-size:42px}.hero p{max-width:760px;margin:0;color:#64748b;line-height:1.8}
.hero-badge{min-width:250px;padding:18px;border-radius:20px;background:rgba(255,255,255,.78);border:1px solid #dbeafe}
.hero-badge strong,.hero-badge span{display:block}.hero-badge span{margin-top:6px;color:#64748b;font-size:13px}
.tech-shell{display:grid;grid-template-columns:390px minmax(0,1fr);gap:22px;margin-top:24px}
.left-panel,.right-panel{display:grid;gap:18px;align-content:start}.glass-card{border-radius:22px;border:1px solid #e3eaf6;background:rgba(255,255,255,.92);box-shadow:0 18px 48px rgba(15,23,42,.07)}
.mode-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.mode-grid button{border:1px solid #e2e8f0;background:#f8fafc;border-radius:18px;padding:16px;text-align:left;cursor:pointer;transition:.2s}
.mode-grid button:hover{transform:translateY(-2px);box-shadow:0 14px 28px rgba(37,99,235,.12)}.mode-grid button.active{border-color:#2563eb;background:linear-gradient(135deg,#eff6ff,#ffffff);box-shadow:0 14px 34px rgba(37,99,235,.16)}
.mode-grid span{display:inline-flex;width:34px;height:34px;align-items:center;justify-content:center;border-radius:12px;background:#2563eb;color:white;font-size:12px;font-weight:900}
.mode-grid strong{display:block;margin-top:12px}.mode-grid small{display:block;margin-top:6px;color:#64748b;line-height:1.5}.tip{margin-top:12px}
.full-select{width:100%}.switch-list{display:grid;gap:14px}.status-card p{color:#64748b;line-height:1.6}
.card-head{display:flex;align-items:center;justify-content:space-between;gap:16px}.input-card :deep(.el-textarea__inner){border-radius:18px;font-size:15px;line-height:1.75}
.upload-inner{display:grid;gap:8px;color:#334155}.upload-inner span{color:#64748b}.file-row{display:flex;justify-content:space-between;align-items:center;margin-top:14px;padding:15px;border-radius:16px;background:#f8fafc;border:1px solid #e2e8f0}
.file-row strong,.file-row span{display:block}.file-row span{margin-top:5px;color:#64748b}.file-row.success{border-color:#bbf7d0}.file-row.failed{border-color:#fecaca}
.action-row{display:flex;flex-wrap:wrap;gap:12px;margin-top:18px}.result-card{min-height:420px}.result-text{white-space:pre-wrap;margin:0;padding:20px;border-radius:18px;background:#0f172a;color:#e5edff;line-height:1.9;min-height:330px;font-family:"Microsoft YaHei",system-ui,sans-serif}
.loading-preview{padding:20px;border-radius:18px;background:#f8fafc}
@media(max-width:1050px){.tech-shell{grid-template-columns:1fr}.hero{display:block}.hero-badge{margin-top:18px}.mode-grid{grid-template-columns:repeat(3,1fr)}}
@media(max-width:680px){.existing-tech-page{padding:20px 14px 54px}.hero{padding:22px}.hero h1{font-size:32px}.mode-grid{grid-template-columns:1fr}.card-head{display:grid}.action-row .el-button{width:100%;margin-left:0!important}}
</style>
