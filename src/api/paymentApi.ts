import http from './http'
import type { ApiResponse, PageResult, PaymentConfig, PaymentRecord } from '@/types'

export const paymentApi = {
  recharge(amount: number, type: string) {
    return http.post<ApiResponse<Record<string, unknown>>>('/payment/recharge', { amount, type })
  },
  notify(params: Record<string, string>) {
    return http.post<string>('/payment/notify', params)
  },
  config() {
    return http.get<ApiResponse<PaymentConfig>>('/payment/config')
  },
  history(page = 1, size = 10) {
    return http.get<ApiResponse<PageResult<PaymentRecord>>>('/payment/history', { params: { page, size } })
  }
}
