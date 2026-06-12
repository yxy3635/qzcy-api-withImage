import http from './http'
import type { AdminImageRecord, AdminStats, ApiResponse, ImageGenerationConfig, MailConfig, PageResult, UserInfo } from '@/types'

export const adminApi = {
  dashboard() {
    return http.get<ApiResponse<AdminStats>>('/admin/dashboard')
  },
  users(page = 1, size = 10, keyword = '') {
    return http.get<ApiResponse<PageResult<UserInfo>>>('/admin/users', { params: { page, size, keyword } })
  },
  imageRecords(page = 1, size = 10, keyword = '', status = '') {
    return http.get<ApiResponse<PageResult<AdminImageRecord>>>('/admin/image-records', {
      params: { page, size, keyword, status }
    })
  },
  imageConfigs() {
    return http.get<ApiResponse<ImageGenerationConfig[]>>('/admin/image-configs')
  },
  updateImageConfig(id: number, payload: Partial<Omit<ImageGenerationConfig, 'id' | 'code' | 'apiKeyMasked'>> & { apiKey?: string }) {
    return http.put<ApiResponse<ImageGenerationConfig>>(`/admin/image-configs/${id}`, payload)
  },
  mailConfig() {
    return http.get<ApiResponse<MailConfig>>('/admin/mail-config')
  },
  updateMailConfig(payload: Partial<Omit<MailConfig, 'id' | 'passwordConfigured'>> & { password?: string }) {
    return http.put<ApiResponse<MailConfig>>('/admin/mail-config', payload)
  },
  updateRole(id: number, role: 'USER' | 'ADMIN') {
    return http.put<ApiResponse<void>>(`/admin/users/${id}/role`, { role })
  },
  updateUser(id: number, payload: Partial<Pick<UserInfo, 'email' | 'role' | 'balance'>> & { password?: string }) {
    return http.put<ApiResponse<UserInfo>>(`/admin/users/${id}`, payload)
  },
  deleteUser(id: number) {
    return http.delete<ApiResponse<void>>(`/admin/users/${id}`)
  }
}
