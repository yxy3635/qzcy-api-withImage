import http from './http'
import type { ApiResponse, ImageEstimate, ImageGenerationConfig, ImageRecord, PageResult } from '@/types'

export const imageApi = {
  generate(prompt: string, qualityCode: string, size?: string) {
    return http.post<ApiResponse<ImageRecord>>('/image/generate', { prompt, qualityCode, size })
  },
  detail(id: number) {
    return http.get<ApiResponse<ImageRecord>>(`/image/${id}`)
  },
  configs() {
    return http.get<ApiResponse<ImageGenerationConfig[]>>('/image/configs')
  },
  estimate() {
    return http.get<ApiResponse<ImageEstimate>>('/image/estimate')
  },
  history(page = 1, size = 10) {
    return http.get<ApiResponse<PageResult<ImageRecord>>>('/image/history', { params: { page, size } })
  },
  remove(id: number) {
    return http.delete<ApiResponse<void>>(`/image/${id}`)
  }
}
