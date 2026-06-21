import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 120000
})

const recentMessages = new Map()

function showApiError(message) {
  const now = Date.now()
  const lastShownAt = recentMessages.get(message) || 0
  if (now - lastShownAt < 1800) {
    return
  }
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
      showApiError(message)
      return Promise.reject(new Error(message))
    }
    return result.data
  },
  (error) => {
    logApiError(error)
    if (error.response?.status === 401) {
      sessionStorage.removeItem('dropai_token')
      sessionStorage.removeItem('dropai_username')
      if (window.location.pathname !== '/login') window.location.href = '/login'
    }
    const serverMessage = error.response?.data?.message
    let message = serverMessage || error.message || '网络请求异常'
    if (error.response?.status === 429 || String(message).includes('429')) {
      message = '大模型接口请求频率受限，请稍后重试或更换可用API Key。'
    } else if (error.code === 'ECONNABORTED') {
      message = '处理时间超过 120 秒，请稍后查看任务进度或缩短文本'
    } else if (!error.response) {
      message = '无法连接后端服务，请确认服务已启动'
    }
    showApiError(message)
    return Promise.reject(new Error(message))
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

export function submitRewrite(data) {
  return request.post('/rewrite/submit', data)
}

export function analyzeText(data) {
  return request.post('/rewrite/analyze', data)
}

export function getAiStatus() {
  return request.get('/rewrite/ai/status', {
    timeout: 180000
  })
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

export function uploadDocument(file, mode, platform = 'GENERAL') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('mode', mode)
  formData.append('platform', platform)
  return request.post('/document/rewrite/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
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
  return request.post('/design-packages/generate', project, {
    timeout: 300000
  })
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
