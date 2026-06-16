import http from './http'
import type { Announcement, ApiResponse } from '@/types'

export const noticeApi = {
  list() {
    return http.get<ApiResponse<Announcement[]>>('/announcements')
  }
}
