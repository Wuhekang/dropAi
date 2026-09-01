import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearAuthSession, getAuthToken } from '../utils/authStorage'

const request = axios.create({
  baseURL: '/api',
  timeout: 120000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
    Accept: 'application/json;charset=UTF-8'
  }
})

const recentMessages = new Map()

function parsePointShortage(message = '') {
  const numbers = String(message).match(/\d+/g)?.map(Number) || []
  if (numbers.length >= 3) {
    return {
      currentPoints: numbers[0],
      requiredPoints: numbers[1],
      missingPoints: numbers[2]
    }
  }
  if (numbers.length >= 2) {
    const requiredPoints = numbers[0]
    const currentPoints = numbers[1]
    return {
      currentPoints,
      requiredPoints,
      missingPoints: Math.max(0, requiredPoints - currentPoints)
    }
  }
  return { currentPoints: 0, requiredPoints: 0, missingPoints: 0 }
}

function emitPointShortage(result, message) {
  const parsed = parsePointShortage(message)
  const data = result?.data || {}
  window.dispatchEvent(new CustomEvent('dropai:points-not-enough', {
    detail: {
      ...parsed,
      currentPoints: data.currentPoints ?? data.current_points ?? parsed.currentPoints,
      requiredPoints: data.requiredPoints ?? data.required_points ?? parsed.requiredPoints,
      missingPoints: data.missingPoints ?? data.missing_points ?? parsed.missingPoints,
      message,
      data
    }
  }))
}

function showApiError(message) {
  const now = Date.now()
  const lastShownAt = recentMessages.get(message) || 0
  if (now - lastShownAt < 3000) return
  recentMessages.set(message, now)
  ElMessage.error(message)
}

function logApiError(error) {
  const config = error.config || {}
  console.error('[Dokiai Academic API Error]', {
    url: `${config.baseURL || ''}${config.url || ''}`,
    method: config.method,
    status: error.response?.status,
    responseData: error.response?.data
  })
}

function rejectApiError(message, code, responseData) {
  const apiError = new Error(message)
  apiError.code = code
  apiError.responseData = responseData
  return Promise.reject(apiError)
}

request.interceptors.request.use((config) => {
  const token = getAuthToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob' || response.data instanceof Blob) {
      return response.data
    }
    const result = response.data
    if (result && result.code !== 200) {
      const message = result.message || '请求失败'
      if (result.code === 'PAY_REQUIRED' || result.code === 'POINTS_NOT_ENOUGH') {
        emitPointShortage(result, message)
        return rejectApiError(message, result.code, result)
      }
      showApiError(message)
      return rejectApiError(message, result.code, result)
    }
    return result.data
  },
  async (error) => {
    logApiError(error)
    if (error.response?.status === 401 && !error.config?.skipAuthRedirect) {
      clearAuthSession()
      if (window.location.pathname !== '/login') {
        const returnTo = `${window.location.pathname}${window.location.search}${window.location.hash}`
        window.location.href = `/login?redirect=${encodeURIComponent(returnTo)}`
      }
    }
    const responseData = error.response?.data
    const blobMessage = responseData instanceof Blob && responseData.type?.includes('json')
      ? await responseData.text().then(text => { try { return JSON.parse(text)?.message } catch { return '' } })
      : ''
    const serverMessage = blobMessage || responseData?.message
    let message = serverMessage || error.message || '网络请求异常'
    if (error.response?.status === 429 || String(message).includes('429')) {
      message = '大模型接口请求频率受限，请稍后重试或更换可用 API Key。'
    } else if (error.code === 'ECONNABORTED') {
      message = '处理时间超过 120 秒，请稍后查看任务进度或缩短文本。'
    } else if (!error.response) {
      message = '无法连接后端服务，请确认服务已启动。'
    }
    showApiError(message)
    return rejectApiError(message, error.response?.data?.code, error.response?.data)
  }
)

export function login(data) {
  return request.post('/auth/login', data)
}

export function register(data) {
  return request.post('/auth/register', data)
}

export async function logout() {
  try {
    return await request.post('/auth/logout')
  } finally {
    clearAuthSession()
  }
}

export function changeMyPassword(data) {
  return request.put('/account/password', data)
}

export function getPointAccount() {
  return request.get('/points/me')
}

export function getPointTransactions() {
  return request.get('/points/transactions')
}

export function getFeaturePricing() {
  return request.get('/points/pricing')
}

export function updateFeaturePricing(featureCode, data) {
  return request.put(`/points/pricing/${featureCode}`, data)
}

export function getRechargePlans() {
  return request.get('/recharge/plans')
}

export function getRechargePricing() {
  return request.get('/recharge/pricing')
}

export function createRechargeOrder(data) {
  return request.post('/recharge/create', data)
}

export function getRechargeOrders() {
  return request.get('/recharge/orders')
}

export function getRechargeOrder(orderNo) {
  return request.get(`/recharge/orders/${encodeURIComponent(orderNo)}`)
}

export function reconcileRechargeOrder(orderNo, reason) {
  return request.post(`/recharge/admin/orders/${encodeURIComponent(orderNo)}/reconcile`, { reason })
}

export function getAdminUsers(params) {
  return request.get('/admin/users', { params })
}

export function getAdminUserDetail(userId) {
  return request.get(`/admin/users/${userId}`)
}

export function getAdminRechargeOrders() {
  return request.get('/admin/users/orders')
}

export function adjustAdminUserPoints(userId, data) {
  return request.post(`/admin/users/${userId}/points-adjust`, data)
}
export function resetAdminUserPassword(userId, password){return request.put(`/admin/users/${userId}/password`,{password})}
export function updateAdminUserSchool(userId,schoolId){return request.put(`/admin/users/${userId}/school`,{schoolId})}
export function getAdminFinancialSummary(month){return request.get('/admin/users/financial-summary',{params:{month}})}
export function getSchools(){return request.get('/admin/schools')}
export function createSchool(data){return request.post('/admin/schools',data)}
export function updateSchool(id,data){return request.put(`/admin/schools/${id}`,data)}
export function deleteSchool(id){return request.delete(`/admin/schools/${id}`)}
export function setSchoolEnabled(id,enabled){return request.put(`/admin/schools/${id}/enabled`,{enabled})}
export function createSchoolViewer(id,data){return request.post(`/admin/schools/${id}/viewers`,data)}
export function updateSchoolViewer(id,data){return request.put(`/admin/school-viewers/${id}`,data)}
export function getSchoolViewerStatistics(range='30d'){return request.get('/school-viewer/statistics',{params:{range}})}
export function getSchoolStudents(){return request.get('/school-viewer/students')}
export function giftSchoolStudentPoints(id,points){return request.post(`/school-viewer/students/${id}/gift`,{points})}
export function deleteSchoolStudent(id,currentPassword){return request.delete(`/school-viewer/students/${id}`,{data:{currentPassword}})}
export function updateSchoolStudentRechargePrice(studentRechargePricePer10){return request.put('/school-viewer/recharge-price',{studentRechargePricePer10})}

export function mockPayRechargeOrder(orderNo) {
  return request.post(`/recharge/orders/${orderNo}/mock-pay`)
}

export function getLatestNotice() {
  return request.get('/notices/latest')
}

export function markNoticeRead(noticeId) {
  return request.post(`/notices/${noticeId}/read`)
}

export function getAdminNotices() {
  return request.get('/notices/admin')
}

export function publishNotice(data) {
  return request.post('/notices/admin', data)
}

export function updateNotice(id, data) {
  return request.put(`/notices/admin/${id}`, data)
}

export function getAdminNoticeLatest() {
  return request.get('/admin/notice/latest')
}

export function saveAdminNotice(data) {
  const payload = {
    ...data,
    is_popup: data?.isPopup
  }
  return request.post('/admin/notice/save', payload)
}

export function publishAdminNotice(id) {
  return request.post(`/admin/notice/publish/${id}`)
}

export function submitRewrite(data) {
  return request.post('/rewrite/submit', data)
}

export function analyzeText(data) {
  return request.post('/rewrite/analyze', data)
}

export function getAiStatus() {
  return request.get('/rewrite/ai/status', { timeout: 180000 })
}

export function getRewriteList() {
  return request.get('/rewrite/list')
}

export function getRewriteDetail(id) {
  return request.get(`/rewrite/${id}`)
}

export function deleteRewrite(id) {
  return request.delete(`/rewrite/${id}`)
}

export function uploadDocument(file, mode, platform = 'GENERAL', requestId = '') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('mode', mode)
  formData.append('platform', platform)
  if (requestId) formData.append('requestId', requestId)
  return request.post('/document/rewrite/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export function getDocumentJob(jobId, includeParagraphs = false) {
  return request.get(`/document/rewrite/job/${jobId}`, {
    params: { includeParagraphs }
  })
}

export function getDocumentJobs() {
  return request.get('/document/rewrite/jobs')
}

export function precheckDocument(file, mode = 'humanize') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('mode', mode)
  return request.post('/document/precheck', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export function extractDocumentText(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/document/extract', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export function downloadDocument(jobId) {
  return request.get(`/document/rewrite/download/${jobId}`, {
    responseType: 'blob',
    timeout: 120000
  })
}

export function getMyDocuments(params = { pageNum: 1, pageSize: 10 }) {
  return request.get('/documents', { params })
}

export function downloadMyDocument(jobId) {
  return request.get(`/documents/${jobId}/download`, {
    responseType: 'blob',
    timeout: 120000
  })
}

export function generateEngineeringDocument(data) {
  return request.post('/engineering-writing/generate', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000
  })
}

export function analyzeEngineeringDesign(data) {
  return request.post('/engineering-writing/analyze', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000
  })
}

export function getEngineeringAiStatus() {
  return request.get('/engineering-writing/ai/status', { timeout: 240000 })
}

export function analyzeMechanicalAssistant({ projectName, description, files }) {
  const form = new FormData()
  form.append('projectName', projectName || '')
  form.append('description', description || '')
  ;(files || []).forEach(file => form.append('files', file))
  return request.post('/mechanical-assistant/analyze', form, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 300000 })
}

export function downloadMechanicalAssistantReport(projectId) {
  return request.get(`/mechanical-assistant/projects/${projectId}/report.docx`, { responseType: 'blob', timeout: 120000 })
}

export function downloadArtifact(downloadUrl) {
  if (!downloadUrl) return Promise.reject(new Error('文件下载地址不存在'))
  const url = downloadUrl.startsWith('/api/') ? downloadUrl.substring(4) : downloadUrl
  return request.get(url, {
    responseType: 'blob',
    timeout: 120000
  })
}

export function createComputerGenerationJob(data) {
  return request.post('/computer-generator/create', data, { timeout: 120000 })
}

export function uploadComputerGenerationFiles(jobId, files = []) {
  const formData = new FormData()
  formData.append('jobId', jobId)
  files.forEach(file => formData.append('files', file.raw || file))
  return request.post('/computer-generator/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000
  })
}

export function analyzeComputerGenerationFiles(files = []) {
  const formData = new FormData()
  files.forEach(file => formData.append('files', file.raw || file))
  return request.post('/computer-generator/analyze', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 240000
  })
}

export function startComputerGeneration(jobId, config = null) {
  return request.post(`/computer-generator/start/${jobId}`, config, { timeout: 300000 })
}

export function getComputerGenerationStatus(jobId) {
  return request.get(`/computer-generator/status/${jobId}`)
}

export function getComputerGenerationResult(jobId) {
  return request.get(`/computer-generator/result/${jobId}`)
}

export function getComputerGenerationHistory() {
  return request.get('/computer-generator/history')
}

export function deleteComputerGenerationJob(jobId) {
  return request.delete(`/computer-generator/${jobId}`)
}

export function downloadComputerGenerationZip(jobId) {
  return request.get(`/computer-generator/download/${jobId}`, {
    responseType: 'blob',
    timeout: 120000
  })
}

export function getWritingReferenceSearchStatus() {
  return request.get('/writing/reference-search/status')
}

export function searchLiterature(data) {
  return request.post('/literature/search', data, { timeout: 30000 })
}

export function getWritingReferenceProviders() {
  return request.get('/writing/reference-search/providers', { skipAuthRedirect: true })
}

export function getWritingV2Templates() {
  return request.get('/writing-v2/templates')
}

export function createWritingV2Project(data) {
  return request.post('/writing-v2/projects', data, { timeout: 120000 })
}

export function getWritingV2Project(id) {
  return request.get(`/writing-v2/projects/${id}`)
}

export function saveWritingV2Settings(id, data) {
  return request.post(`/writing-v2/projects/${id}/settings`, data)
}

export function saveWritingV2CaseMaterials(id, content) {
  return request.post(`/writing-v2/projects/${id}/case-materials`, { content })
}

export function uploadWritingV2Images(id, files = []) {
  const formData = new FormData()
  files.forEach(file => formData.append('files', file.raw || file))
  return request.post(`/writing-v2/projects/${id}/image-materials`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000
  })
}

export function designWritingV2Outline(id, data) {
  return request.post(`/writing-v2/projects/${id}/outline`, data, { timeout: 180000 })
}

export function saveWritingV2ChapterResources(id, chapterId, data) {
  return request.post(`/writing-v2/projects/${id}/chapters/${chapterId}/resources`, data)
}

export function saveWritingV2References(id, references) {
  return request.post(`/writing-v2/projects/${id}/references`, { references })
}

export function generateWritingV2Content(id) {
  return request.post(`/writing-v2/projects/${id}/content`, {}, { timeout: 600000 })
}

export function exportWritingV2Docx(id) {
  return request.post(`/writing-v2/projects/${id}/document/docx`, {}, { timeout: 300000 })
}

export function createWritingProject(data) {
  return request.post('/writing/projects', data, { timeout: 120000 })
}

export function getWritingProject(id) {
  return request.get(`/writing/projects/${id}`)
}

export function updateWritingProject(id, data) {
  return request.put(`/writing/projects/${id}`, data)
}

export function generateWritingOutline(id) {
  return request.post(`/writing/projects/${id}/outline/generate`, {}, { timeout: 120000 })
}

export function confirmWritingOutline(id) {
  return request.post(`/writing/projects/${id}/outline/confirm`)
}

export function addWritingChapter(id, data) {
  return request.post(`/writing/projects/${id}/chapters`, data)
}

export function updateWritingChapter(id, chapterId, data) {
  return request.put(`/writing/projects/${id}/chapters/${chapterId}`, data)
}

export function deleteWritingChapter(id, chapterId) {
  return request.delete(`/writing/projects/${id}/chapters/${chapterId}`)
}

export function reorderWritingChapters(id, chapterIds) {
  return request.put(`/writing/projects/${id}/chapters/reorder`, chapterIds)
}

export function addWritingSection(id, chapterId, data) {
  return request.post(`/writing/projects/${id}/chapters/${chapterId}/sections`, data)
}

export function updateWritingSection(id, sectionId, data) {
  return request.put(`/writing/projects/${id}/sections/${sectionId}`, data)
}

export function deleteWritingSection(id, sectionId) {
  return request.delete(`/writing/projects/${id}/sections/${sectionId}`)
}

export function reorderWritingSections(id, sectionIds) {
  return request.put(`/writing/projects/${id}/sections/reorder`, sectionIds)
}

export function addWritingChart(id, chapterId, data) {
  return request.post(`/writing/projects/${id}/chapters/${chapterId}/charts`, data)
}

export function updateWritingChart(id, chartId, data) {
  return request.put(`/writing/projects/${id}/charts/${chartId}`, data)
}

export function deleteWritingChart(id, chartId) {
  return request.delete(`/writing/projects/${id}/charts/${chartId}`)
}

export function addWritingChartSeries(id, chartId, data) {
  return request.post(`/writing/projects/${id}/charts/${chartId}/series`, data)
}

export function updateWritingChartSeries(id, seriesId, data) {
  return request.put(`/writing/projects/${id}/chart-series/${seriesId}`, data)
}

export function deleteWritingChartSeries(id, seriesId) {
  return request.delete(`/writing/projects/${id}/chart-series/${seriesId}`)
}

export function addWritingTable(id, chapterId, data) {
  return request.post(`/writing/projects/${id}/chapters/${chapterId}/tables`, data)
}

export function updateWritingTable(id, tableId, data) {
  return request.put(`/writing/projects/${id}/tables/${tableId}`, data)
}

export function deleteWritingTable(id, tableId) {
  return request.delete(`/writing/projects/${id}/tables/${tableId}`)
}

export function searchWritingReferences(id) {
  return request.post(`/writing/projects/${id}/references/search`, {}, { timeout: 180000 })
}

export function startAiReferenceSearch(id, data = {}) {
  return request.post(`/writing/projects/${id}/references/ai-search`, data, { timeout: 900000 })
}

export function saveWritingReferenceLibrary(id) {
  return request.post(`/writing/projects/${id}/references/library/save`)
}

export function addWritingManualReference(id, data) {
  return request.post(`/writing/projects/${id}/references/manual`, data, { timeout: 120000 })
}

export function getWritingV2Flow(id) {
  return request.get(`/writing/projects/${id}/flow`)
}

export function uploadWritingMaterials(id, files = []) {
  const formData = new FormData()
  files.forEach(file => formData.append('files', file.raw || file))
  return request.post(`/writing/projects/${id}/materials`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000
  })
}

export function updateWritingMaterial(id, materialId, data) {
  return request.put(`/writing/projects/${id}/materials/${materialId}`, data)
}

export function deleteWritingMaterial(id, materialId) {
  return request.delete(`/writing/projects/${id}/materials/${materialId}`)
}

export function getWritingMaterialContent(id, materialId) {
  return request.get(`/writing/projects/${id}/materials/${materialId}/content`, { responseType: 'blob' })
}

export function generateWritingV2Outline(id, data) {
  return request.post(`/writing/projects/${id}/v2-outline/generate`, data, { timeout: 180000 })
}

export function replaceWritingV2Outline(id, file) {
  const formData = new FormData()
  formData.append('file', file?.raw || file)
  return request.post(`/writing/projects/${id}/v2-outline/replace`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }, timeout: 180000
  })
}

export function analyzeWritingMaterials(id) {
  return request.post(`/writing/projects/${id}/materials/analyze`, {}, { timeout: 180000 })
}

export function searchWritingWebImages(id) {
  return request.post(`/writing/projects/${id}/materials/web-search`, {}, { timeout: 180000 })
}

export function updateWritingSectionMediaConfig(id, sectionId, data) {
  return request.put(`/writing/projects/${id}/sections/${sectionId}/media-config`, data)
}

export function searchWritingChineseReferences(id, data = {}) {
  return request.post(`/writing/projects/${id}/references/search/chinese`, data, { timeout: 180000 })
}

export function generateWritingReferenceSearchPlan(id, data = {}) {
  return request.post(`/writing/projects/${id}/references/search-plan`, data, { timeout: 60000 })
}

export function searchWritingEnglishReferences(id, data = {}) {
  return request.post(`/writing/projects/${id}/references/search/english`, data, { timeout: 180000 })
}

export function importWritingReferences(id, files = []) {
  const formData = new FormData()
  files.forEach(file => formData.append('files', file.raw || file))
  return request.post(`/writing/projects/${id}/references/import`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000
  })
}

export function deduplicateWritingReferences(id) {
  return request.post(`/writing/projects/${id}/references/deduplicate`)
}

export function assignWritingReferencesToChapters(id) {
  return request.post(`/writing/projects/${id}/references/assign-to-chapters`)
}

export function verifyWritingReferences(id) {
  return request.post(`/writing/projects/${id}/references/verify`)
}

export function completeWritingReferenceMetadata(id, referenceId) {
  return request.post(`/writing/projects/${id}/references/${referenceId}/complete-metadata`)
}

export function startWritingGeneration(id) {
  return request.post(`/writing/projects/${id}/generate`, {}, { timeout: 120000 })
}

export function getWritingProgress(id) {
  return request.get(`/writing/projects/${id}/progress`)
}

export function getWritingPreview(id) {
  return request.get(`/writing/projects/${id}/preview`)
}

export function getWritingFiles(id) {
  return request.get(`/writing/projects/${id}/files`)
}

export function createWordFormatJob({ template, source, instructions = '', useDoubao = false }, onUploadProgress) {
  const form = new FormData()
  form.append('template', template?.raw || template)
  form.append('source', source?.raw || source)
  if (String(instructions || '').trim()) form.append('instructions', String(instructions).trim())
  form.append('useDoubao', String(Boolean(useDoubao)))
  return request.post('/word-format/jobs', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000,
    onUploadProgress
  })
}

export function getWordFormatJob(id) {
  return request.get(`/word-format/jobs/${id}`)
}

export function downloadWordFormatResult(id) {
  return request.get(`/word-format/jobs/${id}/download`, {
    responseType: 'blob',
    timeout: 300000
  })
}

export function createPptProject(data) { return request.post('/ppt/projects', data) }
export function listPptProjects() { return request.get('/ppt/projects') }
export function getPptProject(id) { return request.get(`/ppt/projects/${id}`) }
export function uploadPptSource(id, file) { const form = new FormData(); form.append('file', file?.raw || file); return request.post(`/ppt/projects/${id}/upload`, form, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 180000 }) }
export function analyzePptProject(id) { return request.post(`/ppt/projects/${id}/analyze`, {}, { timeout: 300000 }) }
export function generatePptOutline(id) { return request.post(`/ppt/projects/${id}/outline`, {}, { timeout: 300000 }) }
export function savePptOutline(id, items) { return request.put(`/ppt/projects/${id}/outline`, items) }
export function planPptSlides(id) { return request.post(`/ppt/projects/${id}/plan`, {}, { timeout: 300000 }) }
export function savePptPlan(id, pages) { return request.put(`/ppt/projects/${id}/plan`, pages) }
export function updatePptSlide(id, slideId, data) { return request.put(`/ppt/projects/${id}/slides/${slideId}`, data) }
export function regeneratePptSlide(id, slideId) { return request.post(`/ppt/projects/${id}/slides/${slideId}/regenerate`, {}, { timeout: 180000 }) }
export function generatePptFile(id) { return request.post(`/ppt/projects/${id}/generate`, {}, { timeout: 600000 }) }
export function getPptProgress(id) { return request.get(`/ppt/projects/${id}/progress`) }
export function downloadPptFile(id) { return request.get(`/ppt/projects/${id}/download`, { responseType: 'blob', timeout: 300000 }) }
export function listPptTemplates() { return request.get('/ppt/templates') }
export function validateDiagram(dsl) { return request.post('/diagram/validate', { dsl }) }
export function renderDiagram(projectId, dsl, signal) { return request.post('/diagram/render', { projectId, dsl }, { timeout: 30000, signal }) }
export function getDiagramHealth() { return request.get('/diagram/health') }
export async function streamDiagramAssistant(data, { signal, onEvent } = {}) {
  const token = getAuthToken()
  const response = await fetch('/api/diagram/assistant/stream', {
    method: 'POST', signal,
    headers: { 'Content-Type': 'application/json;charset=UTF-8', Accept: 'text/event-stream', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(data)
  })
  if (response.status === 401) {
    clearAuthSession()
    if (window.location.pathname !== '/login') window.location.href = '/login'
    throw Object.assign(new Error('请先登录'), { code: 'UNAUTHORIZED' })
  }
  if (!response.ok || !response.body) throw Object.assign(new Error(`绘图助手连接失败（HTTP ${response.status}）`), { code: 'STREAM_CONNECT_FAILED' })
  const reader = response.body.getReader(), decoder = new TextDecoder('utf-8'); let buffer = '', doneResult = null
  const dispatch = (block) => {
    let event = 'message', dataText = ''
    for (const line of block.split('\n')) { if (line.startsWith('event:')) event = line.slice(6).trim(); else if (line.startsWith('data:')) dataText += line.slice(5).trim() }
    if (!dataText) return
    const payload = JSON.parse(dataText); onEvent?.(event, payload)
    if (event === 'done') doneResult = payload.data || payload
    if (event === 'error') { const detail = payload.data || payload; throw Object.assign(new Error(detail.message || payload.message || '生成失败，原图已恢复。'), { code: detail.code || 'GENERATION_FAILED', retryable: Boolean(detail.retryable) }) }
  }
  while (true) {
    const part = await reader.read(); buffer += decoder.decode(part.value || new Uint8Array(), { stream: !part.done }).replace(/\r\n/g, '\n')
    let cut; while ((cut = buffer.indexOf('\n\n')) >= 0) { const block = buffer.slice(0, cut); buffer = buffer.slice(cut + 2); if (block.trim()) dispatch(block) }
    if (part.done) break
  }
  if (buffer.trim()) dispatch(buffer)
  if (!doneResult) throw Object.assign(new Error('生成流结束但没有最终结果，原图已恢复。'), { code: 'STREAM_INCOMPLETE' })
  return doneResult
}
export function saveDiagramProject(data) { return request.post('/diagram/projects', data) }
export function listDiagramProjects() { return request.get('/diagram/projects') }
export function exportDiagram(previewId, format) { return request.get(`/diagram/previews/${previewId}/download/${format}`, { responseType: 'blob', timeout: 120000 }) }
export function createDiagramDownloadTicket(previewId, format, fileName) { return request.post(`/diagram/previews/${previewId}/download/${format}/ticket`, { fileName }) }
export function getDiagramPreview(previewId) { return request.get(`/diagram/previews/${previewId}`) }
export function uploadPptTemplateZip(file) { const form = new FormData(); form.append('file', file?.raw || file); return request.post('/ppt/templates/upload', form, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 600000 }) }
export function recommendPptTemplate(id) { return request.get(`/ppt/projects/${id}/template/recommend`) }
export function selectPptTemplate(id, data) { return request.put(`/ppt/projects/${id}/template`, data) }
