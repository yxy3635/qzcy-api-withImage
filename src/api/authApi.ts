import http from './http'
import type { ApiResponse, UserInfo } from '@/types'

export interface LoginResponse {
  token: string
  user: UserInfo
}

export const authApi = {
  login(username: string, password: string) {
    return http.post<ApiResponse<LoginResponse>>('/auth/login', { username, password })
  },
  register(username: string, email: string, password: string, code: string, inviteCode = '') {
    return http.post<ApiResponse<UserInfo>>('/auth/register', { username, email, password, code, inviteCode })
  },
  sendEmailCode(email: string, scene: 'register' | 'forgot_password') {
    return http.post<ApiResponse<Record<string, unknown>>>('/auth/email-code', { email, scene })
  },
  resetPassword(email: string, code: string, newPassword: string) {
    return http.post<ApiResponse<void>>('/auth/forgot-password', { email, code, newPassword })
  },
  me() {
    return http.get<ApiResponse<UserInfo>>('/user/me')
  }
}
