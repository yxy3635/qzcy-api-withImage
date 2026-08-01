import http from './http'
import type { ApiResponse, RelayToken, RelayUserOverview } from '@/types'

export const relayApi = {
  overview(params: {
    section?: string
    page?: number
    size?: number
    keyword?: string
    status?: string
    sort?: string
  } = {}) {
    return http.get<ApiResponse<RelayUserOverview>>('/relay/overview', { params })
  },
  createToken(payload: { name: string; groups?: string; allowedModels?: string; quota?: number; rpmLimit?: number; tpmLimit?: number; ipWhitelist?: string; expiresAt?: string; enabled?: boolean }) {
    return http.post<ApiResponse<RelayToken>>('/relay/tokens', payload)
  },
  updateToken(id: number, payload: { name?: string; groups?: string; allowedModels?: string; quota?: number; rpmLimit?: number; tpmLimit?: number; ipWhitelist?: string; expiresAt?: string; enabled?: boolean }) {
    return http.put<ApiResponse<RelayToken>>(`/relay/tokens/${id}`, payload)
  },
  revealToken(id: number) {
    return http.get<ApiResponse<string>>(`/relay/tokens/${id}/secret`)
  },
  deleteToken(id: number) {
    return http.delete<ApiResponse<void>>(`/relay/tokens/${id}`)
  },
  syncChannelStatus() {
    return http.post<ApiResponse<void>>('/relay/channels/status/sync')
  },
  syncChannelStatusOne(id: number) {
    return http.post<ApiResponse<string>>(`/relay/channels/${id}/status/sync`)
  }
}
