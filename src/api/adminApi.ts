import http from './http'
import type { AdminImageRecord, AdminStats, ApiResponse, ImageGenerationConfig, MailConfig, PageResult, PaymentConfig, RelayAdminOverview, RelayChannel, RelayGroup, RelayModel, RelayUpstreamModel, UserInfo } from '@/types'

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
  relayOverview() {
    return http.get<ApiResponse<RelayAdminOverview>>('/admin/relay')
  },
  createRelayChannel(payload: Partial<Omit<RelayChannel, 'id' | 'apiKeyMasked' | 'status'>> & { apiKey?: string }) {
    return http.post<ApiResponse<RelayChannel>>('/admin/relay/channels', payload)
  },
  updateRelayChannel(id: number, payload: Partial<Omit<RelayChannel, 'id' | 'apiKeyMasked' | 'status'>> & { apiKey?: string }) {
    return http.put<ApiResponse<RelayChannel>>(`/admin/relay/channels/${id}`, payload)
  },
  createRelayGroup(payload: Partial<Omit<RelayGroup, 'id'>>) {
    return http.post<ApiResponse<RelayGroup>>('/admin/relay/groups', payload)
  },
  updateRelayGroup(id: number, payload: Partial<Omit<RelayGroup, 'id'>>) {
    return http.put<ApiResponse<RelayGroup>>(`/admin/relay/groups/${id}`, payload)
  },
  deleteRelayGroup(id: number) {
    return http.delete<ApiResponse<void>>(`/admin/relay/groups/${id}`)
  },
  createRelayModel(payload: Partial<Omit<RelayModel, 'id'>>) {
    return http.post<ApiResponse<RelayModel>>('/admin/relay/models', payload)
  },
  updateRelayModel(id: number, payload: Partial<Omit<RelayModel, 'id'>>) {
    return http.put<ApiResponse<RelayModel>>(`/admin/relay/models/${id}`, payload)
  },
  deleteRelayModel(id: number) {
    return http.delete<ApiResponse<void>>(`/admin/relay/models/${id}`)
  },
  syncRelayModels(channelId: number) {
    return http.post<ApiResponse<RelayUpstreamModel[]>>(`/admin/relay/channels/${channelId}/models/sync`)
  },
  syncRelayChannelStatus() {
    return http.post<ApiResponse<void>>('/admin/relay/channels/status/sync')
  },
  mailConfig() {
    return http.get<ApiResponse<MailConfig>>('/admin/mail-config')
  },
  updateMailConfig(payload: Partial<Omit<MailConfig, 'id' | 'passwordConfigured'>> & { password?: string }) {
    return http.put<ApiResponse<MailConfig>>('/admin/mail-config', payload)
  },
  paymentConfig() {
    return http.get<ApiResponse<PaymentConfig>>('/admin/payment-config')
  },
  updatePaymentConfig(payload: Partial<Omit<PaymentConfig, 'id' | 'merchantSecretConfigured'>> & { merchantSecret?: string }) {
    return http.put<ApiResponse<PaymentConfig>>('/admin/payment-config', payload)
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
