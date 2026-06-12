import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 120000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('dropai_token')
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
      ElMessage.error(message)
      return Promise.reject(new Error(message))
    }
    return result.data
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('dropai_token')
      localStorage.removeItem('dropai_username')
      if (window.location.pathname !== '/login') window.location.href = '/login'
    }
    const serverMessage = error.response?.data?.message
    let message = serverMessage || error.message || '网络请求异常'
    if (error.code === 'ECONNABORTED') {
      message = '处理时间超过 120 秒，请稍后查看任务进度或缩短文本'
    } else if (!error.response) {
      message = '无法连接后端服务，请确认服务已启动'
    }
    ElMessage.error(message)
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

export function getMyDocuments() {
  return request.get('/documents')
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
