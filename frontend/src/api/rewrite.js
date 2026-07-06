import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 120000
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
  console.error('[DropAI API Error]', {
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
  const token = sessionStorage.getItem('dropai_token')
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
  (error) => {
    logApiError(error)
    if (error.response?.status === 401) {
      sessionStorage.removeItem('dropai_token')
      sessionStorage.removeItem('dropai_username')
      sessionStorage.removeItem('dropai_role')
      if (window.location.pathname !== '/login') window.location.href = '/login'
    }
    const serverMessage = error.response?.data?.message
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

export function logout() {
  return request.post('/auth/logout')
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

export function createRechargeOrder(data) {
  return request.post('/recharge/create', data)
}

export function getRechargeOrders() {
  return request.get('/recharge/orders')
}

export function confirmRechargePayment(data) {
  return request.post('/recharge/confirm', data)
}

export function getRechargeReviewOrders() {
  return request.get('/recharge/admin/orders')
}

export function auditRechargeOrder(data) {
  return request.post('/recharge/audit', data)
}

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

export function submitEngineeringWorkflow(data) {
  return request.post('/engineering-writing/workflows', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export function getEngineeringWorkflow(workflowId) {
  return request.get(`/engineering-writing/workflows/${workflowId}`)
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

export function downloadEngineeringDxf(params) {
  return request.get('/engineering-writing/cad/dxf', {
    params,
    responseType: 'blob',
    timeout: 120000
  })
}

export function generateDesignPackage(project) {
  return request.post('/design-packages/generate', project, { timeout: 300000 })
}

export function analyzeDesignPackage(data) {
  return request.post('/design-packages/analyze', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000
  })
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
