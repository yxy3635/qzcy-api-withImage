import axios from 'axios'
import type { ApiResponse } from '@/types'
import { sessionText } from '@/utils/storage'

const http = axios.create({
  baseURL: '/api',
  timeout: 600000
})

const publicPaths = ['/auth/login', '/auth/register', '/auth/email-code', '/auth/forgot-password']

function notifyBanned(message?: string) {
  window.dispatchEvent(new CustomEvent('imageCreater:banned', {
    detail: { message: message || '账号已被封禁，无法使用网站功能，申诉：yxy3635@gmail.com' }
  }))
}

http.interceptors.request.use((config) => {
  const url = config.url || ''
  if (publicPaths.some((path) => url.startsWith(path))) {
    delete config.headers.Authorization
    return config
  }

  const sessionValue = sessionText()
  if (sessionValue) {
    config.headers.Authorization = `Bearer ${sessionValue}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body && body.code && body.code !== 200) {
      if (body.code === 423) {
        notifyBanned(body.message)
        window.localStorage.removeItem('imageCreater_token')
        window.localStorage.removeItem('imageCreater_user')
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return response
  },
  (error) => {
    if (error.response?.data?.code === 423) {
      notifyBanned(error.response?.data?.message)
    }
    if (error.response?.status === 401 || error.response?.status === 403) {
      window.localStorage.removeItem('imageCreater_token')
      window.localStorage.removeItem('imageCreater_user')
    }
    const message = error.response?.data?.message || error.message || '网络错误'
    return Promise.reject(new Error(message))
  }
)

export default http
