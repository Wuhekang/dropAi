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
      const message = result.message || '请求失败'
      ElMessage.error(message)
      return Promise.reject(new Error(message))
    }
    return result.data
  },
  (error) => {
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
