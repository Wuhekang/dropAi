import axios from 'axios'

const USE_MOCK = true
const request = axios.create({ baseURL: '/api', timeout: 120000 })

function logApiError(error: any) {
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
    if (response.config.responseType === 'blob' || response.data instanceof Blob) return response.data
    const result = response.data
    if (result && result.code !== 200) return Promise.reject(new Error(result.message || '请求失败'))
    return result.data
  },
  (error) => {
    logApiError(error)
    return Promise.reject(error)
  }
)

const mockTasks = new Map<string, any>()
const wait = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

export async function uploadExistingTechFile(file: File) {
  if (USE_MOCK) {
    await wait(400)
    return {
      fileId: `mock-file-${Date.now()}`,
      fileName: file.name,
      size: file.size,
      status: 'success',
      message: '文件已接收，等待处理'
    }
  }
  const form = new FormData()
  form.append('file', file)
  return request.post('/existing-tech/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export async function submitExistingTechTask(params: any) {
  if (USE_MOCK) {
    const taskId = `mock-task-${Date.now()}`
    mockTasks.set(taskId, { status: 'running', params, createdAt: Date.now() })
    setTimeout(() => {
      mockTasks.set(taskId, {
        status: 'success',
        params,
        result: buildMockResult(params),
        updatedAt: Date.now()
      })
    }, 1500)
    return { taskId, status: 'running', message: '任务已提交' }
  }
  return request.post('/existing-tech/task', params)
}

export async function getExistingTechTaskStatus(taskId: string) {
  if (USE_MOCK) {
    await wait(300)
    const task = mockTasks.get(taskId)
    return { taskId, status: task?.status || 'failed', message: task ? statusText(task.status) : '任务不存在' }
  }
  return request.get(`/existing-tech/task/${taskId}`)
}

export async function getExistingTechResult(taskId: string) {
  if (USE_MOCK) {
    await wait(200)
    const task = mockTasks.get(taskId)
    return { taskId, status: task?.status || 'failed', result: task?.result || '' }
  }
  return request.get(`/existing-tech/result/${taskId}`)
}

export async function downloadExistingTechResult(taskId: string) {
  if (USE_MOCK) {
    const task = mockTasks.get(taskId)
    return new Blob([task?.result || ''], { type: 'text/plain;charset=utf-8' })
  }
  return request.get(`/existing-tech/download/${taskId}`, { responseType: 'blob' })
}

function buildMockResult(params: any) {
  const source = (params.text || '这是模拟处理结果。后续接入真实接口后，将由现有技术服务返回正式文本。').trim()
  const modeName = params.featureName || '文本处理'
  const style = params.outputStyle || '学术'
  const strength = params.strength || '标准'
  const lead = `【${modeName}｜${style}风格｜${strength}处理】\n`
  const body = source
    .split(/\n+/)
    .filter(Boolean)
    .map((paragraph: string, index: number) => {
      const cleaned = paragraph.replace(/\s+/g, ' ').trim()
      return `${index + 1}. ${cleaned}。本段已按所选参数完成表达优化，保留核心语义、专业术语和段落逻辑。`
    })
    .join('\n\n')
  return `${lead}${body}\n\n注：当前为 mock 结果，仅用于跑通提交流程、轮询状态、预览、复制和下载。`
}

function statusText(status: string) {
  return ({ running: '处理中，请稍候', success: '处理成功', failed: '处理失败' } as Record<string, string>)[status] || status
}
