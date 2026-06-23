import http from './http'
import type { AdminImageRecord, AdminRelayUsageLog, AdminStats, Announcement, ApiResponse, ImageGenerationConfig, MailConfig, PageResult, PaymentConfig, ReferralRebate, ReferralWithdrawRequest, RelayAdminOverview, RelayChannel, RelayGroup, RelayModel, RelayUpstreamModel, RelayUserOverview, UserInfo } from '@/types'

export const adminApi = {
  dashboard() {
    return http.get<ApiResponse<AdminStats>>('/admin/dashboard')
  },
  announcements() {
    return http.get<ApiResponse<Announcement[]>>('/admin/announcements')
  },
  createAnnouncement(payload: Partial<Omit<Announcement, 'id' | 'createdAt' | 'updatedAt' | 'publishedAt'>>) {
    return http.post<ApiResponse<Announcement>>('/admin/announcements', payload)
  },
  updateAnnouncement(id: number, payload: Partial<Omit<Announcement, 'id' | 'createdAt' | 'updatedAt' | 'publishedAt'>>) {
    return http.put<ApiResponse<Announcement>>(`/admin/announcements/${id}`, payload)
  },
  deleteAnnouncement(id: number) {
    return http.delete<ApiResponse<void>>(`/admin/announcements/${id}`)
  },
  users(page = 1, size = 10, keyword = '') {
    return http.get<ApiResponse<PageResult<UserInfo>>>('/admin/users', { params: { page, size, keyword } })
  },
  userRelayOverview(id: number) {
    return http.get<ApiResponse<RelayUserOverview>>(`/admin/users/${id}/relay-overview`)
  },
  imageRecords(page = 1, size = 10, keyword = '', status = '') {
    return http.get<ApiResponse<PageResult<AdminImageRecord>>>('/admin/image-records', {
      params: { page, size, keyword, status }
    })
  },
  relayUsageRecords(page = 1, size = 10, keyword = '', status = '') {
    return http.get<ApiResponse<PageResult<AdminRelayUsageLog>>>('/admin/relay/usage-records', {
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
  referralRebates(page = 1, size = 10, status = '') {
    return http.get<ApiResponse<PageResult<ReferralRebate>>>('/admin/referral/rebates', { params: { page, size, status } })
  },
  approveReferralRebate(id: number) {
    return http.post<ApiResponse<void>>(`/admin/referral/rebates/${id}/approve`)
  },
  rejectReferralRebate(id: number, reason: string) {
    return http.post<ApiResponse<void>>(`/admin/referral/rebates/${id}/reject`, { reason })
  },
  referralWithdrawSuccess(id: number) {
    return http.post<ApiResponse<void>>(`/admin/referral/rebates/${id}/withdraw-success`)
  },
  referralWithdrawFailed(id: number, reason: string) {
    return http.post<ApiResponse<void>>(`/admin/referral/rebates/${id}/withdraw-failed`, { reason })
  },
  referralWithdraws(page = 1, size = 10, status = '') {
    return http.get<ApiResponse<PageResult<ReferralWithdrawRequest>>>('/admin/referral/withdraws', { params: { page, size, status } })
  },
  referralAccountWithdrawSuccess(id: number) {
    return http.post<ApiResponse<void>>(`/admin/referral/withdraws/${id}/success`)
  },
  referralAccountWithdrawFailed(id: number, reason: string) {
    return http.post<ApiResponse<void>>(`/admin/referral/withdraws/${id}/failed`, { reason })
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
