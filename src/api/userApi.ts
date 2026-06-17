import http from './http'
import type { ApiResponse, PageResult, ReferralInvitee, ReferralOverview, ReferralRebate, ReferralWithdrawRequest, UserInfo } from '@/types'

export const userApi = {
  balance() {
    return http.get<ApiResponse<number>>('/user/balance')
  },
  updateProfile(email: string) {
    return http.put<ApiResponse<UserInfo>>('/user/profile', { email })
  },
  changePassword(oldPassword: string, newPassword: string) {
    return http.post<ApiResponse<void>>('/user/password', { oldPassword, newPassword })
  },
  referral() {
    return http.get<ApiResponse<ReferralOverview>>('/user/referral')
  },
  enableReferral() {
    return http.post<ApiResponse<ReferralOverview>>('/user/referral/enable')
  },
  referralInvitees(page = 1, size = 10) {
    return http.get<ApiResponse<PageResult<ReferralInvitee>>>('/user/referral/invitees', { params: { page, size } })
  },
  referralRebates(page = 1, size = 10) {
    return http.get<ApiResponse<PageResult<ReferralRebate>>>('/user/referral/rebates', { params: { page, size } })
  },
  transferReferralRebate(id: number) {
    return http.post<ApiResponse<void>>(`/user/referral/rebates/${id}/transfer`)
  },
  withdrawReferralRebate(id: number, qrCodeUrl: string) {
    return http.post<ApiResponse<void>>(`/user/referral/rebates/${id}/withdraw`, { qrCodeUrl })
  },
  withdrawReferral(amount: number, channel: string, qrCodeUrl: string) {
    return http.post<ApiResponse<void>>('/user/referral/withdraw', { amount, channel, qrCodeUrl })
  },
  uploadReferralWithdrawQr(file: File, channel = 'wechat') {
    const form = new FormData()
    form.append('file', file)
    form.append('channel', channel)
    return http.post<ApiResponse<{ url: string }>>('/user/referral/withdraw-qr', form)
  },
  referralWithdraws(page = 1, size = 10) {
    return http.get<ApiResponse<PageResult<ReferralWithdrawRequest>>>('/user/referral/withdraws', { params: { page, size } })
  }
}
