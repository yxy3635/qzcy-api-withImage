import http from './http'
import type { ApiResponse, RelayToken, RelayUserOverview } from '@/types'

export const relayApi = {
  overview() {
    return http.get<ApiResponse<RelayUserOverview>>('/relay/overview')
  },
  createToken(payload: { name: string; groups?: string; allowedModels?: string; quota?: number; rpmLimit?: number; tpmLimit?: number; ipWhitelist?: string; enabled?: boolean }) {
    return http.post<ApiResponse<RelayToken>>('/relay/tokens', payload)
  },
  updateToken(id: number, payload: { name?: string; groups?: string; allowedModels?: string; quota?: number; rpmLimit?: number; tpmLimit?: number; ipWhitelist?: string; enabled?: boolean }) {
    return http.put<ApiResponse<RelayToken>>(`/relay/tokens/${id}`, payload)
  },
  deleteToken(id: number) {
    return http.delete<ApiResponse<void>>(`/relay/tokens/${id}`)
  },
  syncChannelStatus() {
    return http.post<ApiResponse<void>>('/relay/channels/status/sync')
  }
}
