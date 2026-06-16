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

export interface PaymentConfig {
  id: number
  apiUrl: string
  merchantId: string
  registerGiftAmount: number
  enabled: boolean
  alipayEnabled: boolean
  wxpayEnabled: boolean
  qqpayEnabled: boolean
  merchantSecretConfigured: boolean
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

export interface Announcement {
  id: number
  title: string
  content: string
  enabled: boolean
  pinned: boolean
  sortOrder: number
  publishedAt?: string
  createdAt?: string
  updatedAt?: string
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
  relaySiteCost: number
  relayUpstreamCost: number
  relayProfit: number
  todayRelayRequests: number
  yesterdayRelayRequests: number
  todayRelayTokens: number
  yesterdayRelayTokens: number
  todayRelayCost: number
  yesterdayRelayCost: number
  todayRelayUpstreamCost: number
  yesterdayRelayUpstreamCost: number
  todayRelayProfit: number
  yesterdayRelayProfit: number
  relayChannelProfits: RelayChannelProfit[]
  recentRegistrations: Array<{ date: string; count: number }>
  generationTrend: Array<{ date: string; count: number }>
}

export interface RelayChannelProfit {
  channelId: number
  channelName: string
  requests: number
  totalTokens: number
  upstreamCost: number
  siteCost: number
  profit: number
}

export interface RelayChannel {
  id: number
  name: string
  provider: string
  apiBaseUrl: string
  apiKeyMasked: string
  groupNames: string
  status: string
  priority: number
  weight: number
  rpmLimit: number
  tpmLimit: number
  priceMultiplier: number
  enabled: boolean
}

export interface RelayToken {
  id: number
  userId: number
  username: string
  name: string
  tokenPreview: string
  plainToken?: string
  groups: string
  allowedModels: string
  quota: number
  usedQuota: number
  todayCost: number
  requestCount: number
  tokenCount: number
  rpmLimit: number
  tpmLimit: number
  ipWhitelist: string
  enabled: boolean
  expiresAt?: string
  lastUsedAt?: string
  createdAt?: string
}

export interface RelayStats {
  totalChannels: number
  activeChannels: number
  totalTokens: number
  activeTokens: number
  totalRequests: number
  totalTokensUsed: number
  totalCost: number
}

export interface RelayAdminOverview {
  stats: RelayStats
  channels: RelayChannel[]
  tokens: RelayToken[]
  models: RelayModel[]
  groups: RelayGroup[]
}

export interface RelayUserOverview {
  balance: number
  models: RelayModel[]
  tokens: RelayToken[]
  channels: RelayChannel[]
  logs: RelayUsageLog[]
  modelUsage: RelayModelUsage[]
  trend: RelayTrend[]
  groups: RelayGroup[]
  totalRequests: number
  totalTokens: number
  totalCost: number
  averageDurationMs: number
  totalPromptTokens: number
  totalCompletionTokens: number
  totalCachedTokens: number
  totalCacheCreationTokens: number
  todayRequests: number
  todayPromptTokens: number
  todayCompletionTokens: number
  todayTotalTokens: number
  todayCost: number
  currentRpm: number
  currentTpm: number
}

export interface RelayModel {
  id: number
  model: string
  displayName: string
  modelType: string
  inputPrice: number
  outputPrice: number
  cachedInputPrice: number
  cacheCreationPrice: number
  requestPrice: number
  fixedRequestBilling: boolean
  status: string
  enabled: boolean
  sortOrder: number
}

export interface RelayUpstreamModel {
  id: string
  ownedBy: string
  configured: boolean
}

export interface RelayUsageLog {
  id: number
  tokenName: string
  channelName: string
  groupNames: string
  endpoint: string
  model: string
  modelType: string
  promptTokens: number
  completionTokens: number
  cachedTokens: number
  cacheCreationTokens: number
  totalTokens: number
  inputCost: number
  outputCost: number
  cacheReadCost: number
  cacheCreationCost: number
  requestCost: number
  groupRatio: number
  channelRatio: number
  cost: number
  statusCode: number
  durationMs: number
  userAgent: string
  status: string
  createdAt: string
}

export interface RelayTrend {
  date: string
  requests: number
  promptTokens: number
  completionTokens: number
  cachedTokens: number
  cacheCreationTokens: number
  totalTokens: number
  cost: number
}

export interface RelayGroup {
  id: number
  code: string
  name: string
  ratio: number
  enabled: boolean
  modelIds: number[]
}

export interface RelayModelUsage {
  model: string
  requests: number
  totalTokens: number
  cost: number
}
