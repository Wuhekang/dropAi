<template>
  <main class="workspace">
    <header class="hero">
      <div>
        <el-button text type="primary" @click="router.push('/dashboard')">返回工作台</el-button>
        <span class="eyebrow">GRADUATION DESIGN PACKAGE</span>
        <h1>完整毕业设计成果包</h1>
        <p>所有文件均按后端真实生成状态展示，失败文件不会提供下载。</p>
      </div>
      <el-tag size="large" type="warning">方案级成果，需工程校核</el-tag>
    </header>

    <el-steps :active="activeStep" finish-status="success" class="steps" align-center>
      <el-step v-for="step in steps" :key="step" :title="step" />
    </el-steps>

    <section class="grid">
      <el-card shadow="never">
        <template #header><strong>1. 上传资料与识别设计目标</strong></template>
        <el-form label-position="top">
          <el-form-item label="毕业设计题目"><el-input v-model="project.projectTitle" /></el-form-item>
          <el-form-item label="设备名称"><el-input v-model="project.equipmentName" /></el-form-item>
          <el-form-item label="设计类型"><el-input v-model="project.designType" /></el-form-item>
          <el-upload drag multiple action="" :auto-upload="false" :file-list="fileList"
            :on-change="(_, files) => fileList = files" :on-remove="(_, files) => fileList = files"
            accept=".docx,.txt,.md,.pdf,.dxf,.dwg,.png,.jpg,.jpeg,.webp,.bmp">
            <strong>拖入任务书、开题报告、模板、文献、图片或CAD参考图</strong>
            <p>上传内容用于识别设计目标与明确参数。</p>
          </el-upload>
          <el-button class="full" type="primary" :loading="analyzing" :disabled="!fileList.length" @click="analyze">识别资料与参数</el-button>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header><strong>2. 参数确认与工程推导</strong></template>
        <el-tabs v-model="parameterTab">
          <el-tab-pane label="明确参数" name="explicit"><parameter-editor v-model="project.explicitParameters" source-label="来源" /></el-tab-pane>
          <el-tab-pane label="推导参数" name="derived"><parameter-editor v-model="project.derivedParameters" source-label="推导依据" /></el-tab-pane>
          <el-tab-pane label="建议参数" name="suggested"><parameter-editor v-model="project.suggestedParameters" source-label="建议依据" /></el-tab-pane>
        </el-tabs>
        <el-button class="full" @click="addParameter">添加参数</el-button>
        <el-button class="full" type="primary" size="large" :loading="generating" @click="generate">重新计算并生成全部成果</el-button>
      </el-card>
    </section>

    <el-card class="panel" shadow="never">
      <template #header>
        <div class="panel-head"><strong>3-10. 成果生成状态</strong><el-tag :type="statusType(packageStatus)">{{ statusText(packageStatus) }}</el-tag></div>
      </template>
      <el-alert v-if="packageMessage" :type="packageStatus === 'success' ? 'success' : 'warning'" :title="packageMessage" :closable="false" />
      <el-empty v-if="!artifacts.length" description="确认参数后生成完整成果包" />
      <template v-else>
        <div class="metrics">
          <div><span>成功文件</span><strong>{{ successCount }}</strong></div>
          <div><span>失败文件</span><strong>{{ failedCount }}</strong></div>
          <div><span>CAD文件</span><strong>{{ groups.cad.length }}</strong></div>
          <div><span>全部任务</span><strong>{{ artifacts.length }}</strong></div>
        </div>
        <el-tabs>
          <el-tab-pane label="设计计算预览">
            <el-table :data="project.calculations">
              <el-table-column prop="name" label="校核项目" /><el-table-column prop="formula" label="公式" />
              <el-table-column prop="substitution" label="代入" /><el-table-column label="结果"><template #default="{row}">{{ row.result }} {{ row.unit }}</template></el-table-column>
              <el-table-column prop="conclusion" label="结论" min-width="180" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="CAD图纸与预览">
            <el-alert type="warning" title="当前CAD为方案级图纸，未经工程校核，不可直接用于加工。" :closable="false" />
            <artifact-list :items="groups.cad" @download="download" />
          </el-tab-pane>
          <el-tab-pane label="SolidWorks宏"><artifact-list :items="groups.macro" @download="download" /></el-tab-pane>
          <el-tab-pane label="论文与计算书"><artifact-list :items="groups.document" @download="download" /></el-tab-pane>
          <el-tab-pane label="成果包与参数"><artifact-list :items="groups.package" @download="download" /></el-tab-pane>
        </el-tabs>
      </template>
    </el-card>
  </main>
</template>

<script setup>
import { computed, defineComponent, h, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElInput, ElMessage, ElTag } from 'element-plus'
import { analyzeDesignPackage, downloadArtifact, generateDesignPackage } from '../../api/rewrite'

const ParameterEditor = defineComponent({
  props: { modelValue: Array, sourceLabel: String }, emits: ['update:modelValue'],
  setup(props, { emit }) {
    const update = (index, key, value) => { const rows = [...props.modelValue]; rows[index] = { ...rows[index], [key]: value }; emit('update:modelValue', rows) }
    const remove = (index) => emit('update:modelValue', props.modelValue.filter((_, i) => i !== index))
    return () => h('div', { class: 'parameter-list' }, props.modelValue.map((row, index) => h('div', { class: 'parameter-row' }, [
      h(ElInput, { modelValue: row.name, placeholder: '参数名', 'onUpdate:modelValue': v => update(index, 'name', v) }),
      h(ElInput, { modelValue: row.value, placeholder: '数值', 'onUpdate:modelValue': v => update(index, 'value', isNaN(Number(v)) ? v : Number(v)) }),
      h(ElInput, { modelValue: row.unit, placeholder: '单位', 'onUpdate:modelValue': v => update(index, 'unit', v) }),
      h(ElInput, { modelValue: row.source || row.basis, placeholder: props.sourceLabel, 'onUpdate:modelValue': v => update(index, row.source ? 'source' : 'basis', v) }),
      h(ElButton, { type: 'danger', text: true, onClick: () => remove(index) }, () => '删除')
    ])))
  }
})
const ArtifactList = defineComponent({
  props: { items: Array }, emits: ['download'],
  setup(props, { emit }) {
    return () => h('div', { class: 'artifact-grid' }, props.items.map(item => h('div', { class: 'artifact' }, [
      h('div', [
        h('strong', item.name || item.fileName),
        h('span', item.status === 'success' ? `${item.type?.toUpperCase()} · ${formatSize(item.size)}` : `失败原因：${item.failureReason || '未返回失败原因'}`)
      ]),
      h('div', { class: 'artifact-action' }, [
        h(ElTag, { type: statusType(item.status), size: 'small' }, () => statusText(item.status)),
        h(ElButton, {
          type: item.status === 'success' ? 'primary' : 'danger',
          disabled: item.status !== 'success' || !item.downloadUrl || !item.size,
          onClick: () => emit('download', item)
        }, () => item.status === 'success' ? '下载' : '不可下载')
      ])
    ])))
  }
})

const router = useRouter()
const steps = ['上传资料','AI识别','参数确认','设计计算','总体方案','CAD总装图','零件图','SW宏','论文预览','成果包']
const fileList = ref([]), analyzing = ref(false), generating = ref(false), parameterTab = ref('explicit'), artifacts = ref([])
const packageStatus = ref('pending'), packageMessage = ref('等待生成')
const project = reactive({
  projectTitle: '通用机械设备毕业设计', equipmentName: '机械设备', designType: '通用机械结构设计',
  explicitParameters: [], derivedParameters: [], suggestedParameters: [
    { name: '总长', value: 4200, unit: 'mm', basis: '方案阶段建议值' }, { name: '总宽', value: 1600, unit: 'mm', basis: '方案阶段建议值' },
    { name: '总高', value: 1800, unit: 'mm', basis: '方案阶段建议值' }, { name: '设计载荷', value: 1200, unit: 'kg', basis: '方案阶段建议值' },
    { name: '安全系数', value: 1.8, unit: '', basis: '方案阶段建议值' }
  ], verificationItems: [], calculations: []
})
const allParameters = computed(() => [...project.explicitParameters, ...project.derivedParameters, ...project.suggestedParameters])
const successCount = computed(() => artifacts.value.filter(x => x.status === 'success').length)
const failedCount = computed(() => artifacts.value.filter(x => x.status === 'failed').length)
const activeStep = computed(() => packageStatus.value === 'success' ? 10 : artifacts.value.length ? 8 : allParameters.value.length ? 3 : fileList.value.length ? 1 : 0)
const groups = computed(() => ({
  cad: artifacts.value.filter(x => /\.(dxf|svg|png)$/i.test(x.fileName)),
  macro: artifacts.value.filter(x => /\.(bas|txt)$/i.test(x.fileName)),
  document: artifacts.value.filter(x => /\.(docx|pdf)$/i.test(x.fileName)),
  package: artifacts.value.filter(x => /\.(zip|json)$/i.test(x.fileName))
}))
function addParameter() {
  const target = parameterTab.value === 'explicit' ? project.explicitParameters : parameterTab.value === 'derived' ? project.derivedParameters : project.suggestedParameters
  target.push({ name: '', value: '', unit: '', [parameterTab.value === 'explicit' ? 'source' : 'basis']: '' })
}
async function analyze() {
  analyzing.value = true
  try {
    const form = new FormData(); form.append('title', project.projectTitle); fileList.value.forEach(file => form.append('files', file.raw))
    Object.assign(project, await analyzeDesignPackage(form)); ElMessage.success('资料识别完成，请确认参数')
  } finally { analyzing.value = false }
}
async function generate() {
  generating.value = true; packageStatus.value = 'running'; packageMessage.value = '正在生成并校验各项成果文件'
  try {
    const result = await generateDesignPackage(project)
    Object.assign(project, result.project); artifacts.value = result.artifacts || []
    packageStatus.value = result.status || 'failed'; packageMessage.value = result.message || '生成流程已结束'
    packageStatus.value === 'success' ? ElMessage.success(packageMessage.value) : ElMessage.warning(packageMessage.value)
  } catch (error) {
    packageStatus.value = 'failed'; packageMessage.value = friendlyError(error); ElMessage.error(packageMessage.value)
  } finally { generating.value = false }
}
async function download(item) {
  if (item.status !== 'success' || !item.downloadUrl || !item.size) return ElMessage.error(item.failureReason || '文件未生成成功，无法下载')
  try {
    const blob = await downloadArtifact(item.downloadUrl)
    if (!blob?.size) throw new Error('下载文件为空')
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = item.name || item.fileName; a.click(); URL.revokeObjectURL(url)
  } catch (error) { ElMessage.error(friendlyError(error)) }
}
function friendlyError(error) {
  const message = error?.message || '请求失败'
  if (message.includes('429') || message.includes('请求受限')) return '大模型接口请求频率受限，请稍后重试或更换可用API Key。'
  if (message.toLowerCase().includes('timeout') || message.includes('超时')) return '请求处理超时，请稍后重试。'
  return message
}
function formatSize(size) { return size >= 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(2)} MB` : `${Math.max(1, Math.round(size / 1024))} KB` }
function statusType(status) { return status === 'success' ? 'success' : status === 'failed' ? 'danger' : status === 'running' ? 'warning' : 'info' }
function statusText(status) { return ({ pending:'等待中', running:'生成中', success:'成功', failed:'失败', partial_success:'部分成功' })[status] || status }
</script>

<style scoped>
.workspace{max-width:1450px;margin:auto;padding:30px 24px 70px}.hero,.panel-head{display:flex;justify-content:space-between;align-items:flex-start;gap:24px}.hero h1{font-size:36px;margin:10px 0}.hero p{color:#64748b}.eyebrow{display:block;margin-top:18px;color:#2563eb;font-weight:800;font-size:12px;letter-spacing:.16em}.steps{margin:34px 0}.grid{display:grid;grid-template-columns:.9fr 1.1fr;gap:20px}.grid .el-card,.panel{border-radius:18px}.full{width:100%;margin:14px 0 0}.panel{margin-top:20px}.parameter-list{display:grid;gap:9px}.parameter-row{display:grid;grid-template-columns:1fr .75fr .55fr 1.5fr auto;gap:8px}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin:20px 0}.metrics div{padding:18px;border-radius:14px;background:#f4f7fb}.metrics span{display:block;color:#64748b}.metrics strong{font-size:28px}.artifact-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px;margin-top:14px}.artifact{display:flex;justify-content:space-between;align-items:center;padding:16px;border:1px solid #e4e9f1;border-radius:12px}.artifact span{display:block;color:#64748b;font-size:12px;margin-top:5px}.artifact-action{display:flex;align-items:center;gap:8px}@media(max-width:1000px){.grid{grid-template-columns:1fr}.parameter-row{grid-template-columns:1fr 1fr}.metrics{grid-template-columns:repeat(2,1fr)}}@media(max-width:700px){.steps{display:none}.artifact-grid,.metrics{grid-template-columns:1fr}.hero{display:block}}
</style>
