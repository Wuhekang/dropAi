import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 120000
})

request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob' || response.data instanceof Blob) {
      return response.data
    }
    const result = response.data
    if (result && result.code !== 200) {
      ElMessage.error(result.message || '请求失败')
      return Promise.reject(new Error(result.message || '请求失败'))
    }
    return result.data
  },
  (error) => {
    if (error.code === 'ECONNABORTED') {
      ElMessage.error('文本较长，处理时间超过120秒。请缩短文本或稍后重试')
    } else if (!error.response) {
      ElMessage.error('无法连接后端服务，请确认 Spring Boot 已在 8080 端口启动')
    } else {
      ElMessage.error(error.message || '网络请求异常')
    }
    return Promise.reject(error)
  }
)

export function submitRewrite(data) {
  return request.post('/rewrite/submit', data)
}

export function analyzeText(data) {
  return request.post('/rewrite/analyze', data)
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

export function uploadDocument(file, mode) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('mode', mode)
  return request.post('/document/rewrite/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    timeout: 120000
  })
}

export function getDocumentJob(jobId) {
  return request.get(`/document/rewrite/job/${jobId}`)
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
