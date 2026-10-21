export type ReportDimension = 'model' | 'group' | 'channel' | 'provider'
export type ReportScope = 'today' | 'total'
export type ReportMetric = 'requests' | 'cost' | 'tokens'

export interface ReportMetrics {
  requests: number
  errors: number
  tokens: number
  inputTokens: number
  outputTokens: number
  cacheReadTokens: number
  cacheCreationTokens: number
  cacheCreation1hTokens: number
  cost: number
}
export interface ReportRow {
  key: string
  name: string
  detail: string
  legacy: boolean
  today: ReportMetrics
  total: ReportMetrics
}
export interface ReportTrendPoint { bucket: string; metrics: ReportMetrics }
export interface RelayReport {
  cacheSource: 'local' | 'redis' | 'database'
  snapshot: {
    date: string
    timezone: string
    generatedAt: string
    expiresAt: string
    today: ReportMetrics
    total: ReportMetrics
    dimensions: Record<ReportDimension, ReportRow[]>
    hourly: ReportTrendPoint[]
    daily: ReportTrendPoint[]
  }
}
