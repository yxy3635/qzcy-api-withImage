export type Role = 'USER' | 'ADMIN'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface UserInfo {
  id: number
  username: string
  email?: string
  role: Role
  balance: number
  createdAt?: string
  updatedAt?: string
}

export interface ImageRecord {
  id: number
  userId: number
  prompt: string
  generatedImageUrl?: string
  status: 'pending' | 'success' | 'failed'
  cost: number
  createdAt: string
}

export interface ImageGenerationConfig {
  id: number
  code: string
  name: string
  model: string
  apiKeyMasked: string
  apiBaseUrl: string
  endpointPath: string
  size: string
  quality: string
  price: number
  enabled: boolean
  sortOrder: number
}

export interface ImageEstimate {
  averageDurationMs: number
  sampleCount: number
}

export interface MailConfig {
  id: number
  host: string
  port: number
  username: string
  fromAddress: string
  sslEnabled: boolean
  starttlsEnabled: boolean
  enabled: boolean
  devReturnCode: boolean
  passwordConfigured: boolean
}

export interface AdminImageRecord extends ImageRecord {
  username?: string
}

export interface PaymentRecord {
  id: number
  userId: number
  amount: number
  type: string
  status: string
  createdAt: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface AdminStats {
  totalUsers: number
  totalImages: number
  todayImages: number
  totalRevenue: number
  recentRegistrations: Array<{ date: string; count: number }>
  generationTrend: Array<{ date: string; count: number }>
}
