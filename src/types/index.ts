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
  banned?: boolean
  balance: number
  invitationCode?: string
  inviterId?: number
  referralBalance?: number
  createdAt?: string
  updatedAt?: string
}

export interface ImageRecord {
  id: number
  userId: number
  prompt: string
  generatedImageUrl?: string
  status: 'pending' | 'success' | 'failed'
  generationModel?: string
  requestUrl?: string
  errorStatusCode?: number
  errorType?: string
  errorMessage?: string
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
  rechargeNoticeEnabled: boolean
  brandName: string
  brandLogoUrl: string
  siteUrl: string
  passwordConfigured: boolean
}

export interface PaymentConfig {
  id: number
  apiUrl: string
  merchantId: string
  registerGiftAmount: number
  referralRebateRate: number
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
  rechargeAmount?: number
  discountAmount?: number
  couponCode?: string
  type: string
  status: string
  remark?: string
  createdAt: string
}

export interface AdminPaymentRecord extends PaymentRecord {
  username?: string
  email?: string
}

export interface RechargeCouponPreview {
  valid: boolean
  couponId?: number
  code: string
  discountPercent?: number
  originalAmount: number
  discountAmount: number
  payableAmount: number
  remainingUses?: number
  message?: string
}

export interface AdminRechargeCoupon {
  id: number
  code: string
  discountPercent: number
  maxUsesPerUser: number
  maxDiscountAmount: number
  usedCount: number
  enabled: boolean
  createdAt?: string
  updatedAt?: string
}

export interface ReferralOverview {
  enabled: boolean
  invitationCode: string
  invitationLink: string
  rebateRate: number
  invitedUsers: number
  inviteeRechargeTotal: number
  rebateTotal: number
  referralBalance: number
  pendingReviewAmount: number
  approvedAmount: number
  withdrawingAmount: number
  withdrawQrCodes: ReferralWithdrawQrCode[]
}

export interface ReferralWithdrawQrCode {
  channel: string
  qrCodeUrl: string
}

export interface ReferralInvitee {
  userId: number
  username: string
  totalRecharge: number
  registeredAt: string
}

export interface ReferralRebate {
  id: number
  inviterId: number
  inviterUsername: string
  inviteeId: number
  inviteeUsername: string
  rechargeAmount: number
  rebateRate: number
  rebateAmount: number
  status: string
  rejectReason?: string
  withdrawQrCodeUrl?: string
  withdrawFailReason?: string
  reviewedAt?: string
  withdrawnAt?: string
  createdAt: string
}

export interface ReferralWithdrawRequest {
  id: number
  userId: number
  username: string
  amount: number
  channel: string
  qrCodeUrl: string
  status: string
  failReason?: string
  reviewedAt?: string
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
  todayRequests: number
  yesterdayRequests: number
  todayTokens: number
  yesterdayTokens: number
  todayUpstreamCost: number
  yesterdayUpstreamCost: number
  todaySiteCost: number
  yesterdaySiteCost: number
  todayProfit: number
  yesterdayProfit: number
}

export interface AdminUserUsage {
  id: number
  username: string
  email?: string
  role: Role
  banned?: boolean
  balance: number
  createdAt?: string
  todayRequests: number
  todayTokens: number
  yesterdayRequests: number
  todayCost: number
  yesterdayCost: number
  totalTokens: number
  totalCost: number
  totalRecharge: number
}

export interface AdminUserRanking {
  id: number
  username: string
  email?: string
  totalTokens?: number
  totalRecharge?: number
}

export interface AdminUserRankings {
  recharge: AdminUserRanking[]
  tokens: AdminUserRanking[]
}

export type RelayScheduleStrategy = 'weighted_random' | 'smooth_rr' | 'least_conn' | 'priority' | string

export interface RelayChannel {
  id: number
  name: string
  provider: string
  channelRule: 'openai' | 'anthropic' | string
  apiBaseUrl: string
  apiKeyMasked: string
  groupNames: string
  remark: string
  status: string
  priority: number
  weight: number
  rpmLimit: number
  tpmLimit: number
  maxConcurrency: number
  priceMultiplier: number
  enabled: boolean
  scheduleStrategy?: RelayScheduleStrategy
  providers?: RelayChannelProvider[]
  models: RelayChannelModel[]
}

export interface RelayChannelProvider {
  id: number
  channelId: number
  name: string
  apiBaseUrl: string
  apiKeyMasked: string
  channelRule: 'openai' | 'anthropic' | string
  priority: number
  weight: number
  status: string
  enabled: boolean
}

export interface RelayDashboardSummary {
  channelsTotal: number
  channelsAvailable: number
  providersTotal: number
  providersAvailable: number
  todayRequests: number
  todayErrors: number
  errorRate: number
  todayTokens: number
  todayCost: number
  currentRpm: number
}

export interface RelayDashboardTrendPoint {
  hour: string
  requests: number
  errors: number
  totalTokens: number
  cost: number
}

export interface RelayDashboardProvider {
  id: number
  name: string
  channelRule: string
  status: string
  enabled: boolean
  circuitOpen: boolean
}

export interface RelayDashboardChannel {
  id: number
  name: string
  status: string
  enabled: boolean
  priority: number
  weight: number
  scheduleStrategy?: string
  groupNames: string
  health: 'ok' | 'degraded' | 'down' | 'disabled' | string
  providers: RelayDashboardProvider[]
  requests24h: number
  errors24h: number
  avgDurationMs: number
  avgFirstTokenMs: number
  tokens24h: number
  cost24h: number
  lastErrorAt?: string | null
  lastErrorCode?: number | null
}

export interface RelayDashboardError {
  id: number
  channelName: string
  model: string
  statusCode?: number | null
  durationMs?: number | null
  message?: string | null
  createdAt: string
}

export interface RelayDashboard {
  summary: RelayDashboardSummary
  trend: RelayDashboardTrendPoint[]
  channels: RelayDashboardChannel[]
  recentErrors: RelayDashboardError[]
  topModels: RelayModelUsage[]
}

export interface RelayChannelModel {
  id: number
  channelId: number
  modelId: number
  model: string
  displayName: string
  modelType: string
  inputPrice?: number
  outputPrice?: number
  cachedInputPrice?: number
  cacheCreationPrice?: number
  requestPrice?: number
  fixedRequestBilling?: boolean
  longContextThreshold?: number
  longContextBillingMode?: 'price' | 'multiplier' | string
  longContextMultiplier?: number | null
  longContextInputPrice?: number | null
  longContextOutputPrice?: number | null
  longContextCachedInputPrice?: number | null
  longContextCacheCreationPrice?: number | null
  upstreamModel: string
  enabled: boolean
}

export interface RelayPublicChannel {
  id: number
  name: string
  channelRule: 'openai' | 'anthropic' | string
  groupNames: string
  remark?: string
  status: string
  rpmLimit: number
  maxConcurrency: number
  enabled: boolean
  models: RelayPublicChannelModel[]
}

export interface RelayPublicChannelModel {
  modelId: number
  model: string
  displayName: string
  modelType: string
  inputPrice?: number
  outputPrice?: number
  cachedInputPrice?: number
  cacheCreationPrice?: number
  requestPrice?: number
  fixedRequestBilling?: boolean
  longContextThreshold?: number
  longContextBillingMode?: 'price' | 'multiplier' | string
  longContextMultiplier?: number | null
  longContextInputPrice?: number | null
  longContextOutputPrice?: number | null
  longContextCachedInputPrice?: number | null
  longContextCacheCreationPrice?: number | null
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
  channels: RelayPublicChannel[]
  logs: RelayUsageLog[]
  errorLogs: ErrorRequestLog[]
  modelUsage: RelayModelUsage[]
  modelRecentCalls: RelayModelRecentCall[]
  trend: RelayTrend[]
  groups: RelayGroup[]
  logsTotal?: number
  logsCurrent?: number
  logsPages?: number
  logsSize?: number
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

export interface ErrorRequestLog {
  id: number
  source: 'relay' | 'image' | string
  tokenName: string
  channelName: string
  groupNames: string
  endpoint: string
  requestUrl: string
  model: string
  modelType: string
  statusCode: number
  durationMs: number
  userAgent: string
  status: string
  errorType: string
  message?: string
  prompt?: string
  createdAt: string
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
  longContextThreshold: number
  longContextBillingMode?: 'price' | 'multiplier' | string
  longContextMultiplier?: number | null
  longContextInputPrice?: number | null
  longContextOutputPrice?: number | null
  longContextCachedInputPrice?: number | null
  longContextCacheCreationPrice?: number | null
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
  thinkingEffort?: string
  promptTokens: number
  completionTokens: number
  cachedTokens: number
  cacheCreationTokens: number
  totalTokens: number
  cost: number
  statusCode: number
  durationMs: number
  firstTokenMs?: number | null
  userAgent: string
  status: string
  message?: string
  createdAt: string
}

export interface AdminRelayUsageLog extends RelayUsageLog {
  userId: number
  username?: string
  channelName: string
  inputCost: number
  outputCost: number
  cacheReadCost: number
  cacheCreationCost: number
  requestCost: number
  groupRatio: number
  channelRatio: number
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

export interface RelayModelRecentCall {
  id: number
  model: string
  status: string
  statusCode: number
  durationMs: number
  createdAt: string
}
