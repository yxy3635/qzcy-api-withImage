import http from './http'
import type { ApiResponse, PageResult, PaymentConfig, PaymentRecord, RechargeCouponPreview } from '@/types'

export const paymentApi = {
  recharge(amount: number, type: string, couponCode = '') {
    return http.post<ApiResponse<Record<string, unknown>>>('/payment/recharge', { amount, type, couponCode })
  },
  cancelRecharge(orderId: number) {
    return http.post<ApiResponse<void>>('/payment/cancel', { orderId })
  },
  pendingRecharge() {
    return http.get<ApiResponse<PaymentRecord | null>>('/payment/pending')
  },
  previewCoupon(amount: number, code: string) {
    return http.post<ApiResponse<RechargeCouponPreview>>('/payment/coupon-preview', { amount, code })
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
