import type { RelayDashboardChannel, RelayDashboardUsage } from '@/types'

const currency = new Intl.NumberFormat('en-US', {
  style: 'currency', currency: 'USD', minimumFractionDigits: 6, maximumFractionDigits: 6
})
export const formatUsageMoney = (amount: number) => currency.format(amount)

// 日志计费项为 6 位、倍率为 4 位；按十进制定点求和，避免浮点误差影响六位金额的舍入。
export function addUsageMoney(first: number, second: number): number {
  const units = (value: number) => BigInt(Number(value || 0).toFixed(10).replace('.', ''))
  return Number(units(first) + units(second)) / 10_000_000_000
}

export type ProviderUsage = RelayDashboardUsage & { name: string; archived?: boolean }
export interface ChannelUsage {
  key: string
  name: string
  archived?: boolean
  providers: ProviderUsage[]
  requests: number
  errors: number
  cost: number
  upstreamCost: number
}

export function groupTodayUsage(channels: RelayDashboardChannel[], usage: RelayDashboardUsage[]): ChannelUsage[] {
  const result = new Map<string, ChannelUsage>()
  for (const channel of channels) {
    result.set(String(channel.id), {
      key: String(channel.id), name: channel.name, providers: channel.providers.map(provider => ({
        channelId: channel.id, channelName: channel.name, providerId: provider.id, providerName: provider.name,
        name: provider.name || '未命名供应商', requests: 0, errors: 0, cost: 0, upstreamCost: 0
      })), requests: 0, errors: 0, cost: 0, upstreamCost: 0
    })
  }
  for (const row of usage) {
    const key = String(row.channelId ?? 'unassigned')
    let channel = result.get(key)
    if (!channel) {
      channel = { key, name: row.channelName || '未归属渠道', archived: row.channelId != null,
        providers: [], requests: 0, errors: 0, cost: 0, upstreamCost: 0 }
      result.set(key, channel)
    }
    let provider = channel.providers.find(item => item.providerId === row.providerId)
    if (!provider) {
      provider = { ...row, name: row.providerId == null ? '未归属 / 渠道直连' : row.providerName || `供应商 #${row.providerId}`,
        archived: row.providerId != null, requests: 0, errors: 0, cost: 0, upstreamCost: 0 }
      channel.providers.push(provider)
    }
    for (const metric of ['requests', 'errors', 'cost', 'upstreamCost'] as const) {
      const amount = Number(row[metric] || 0)
      if (metric === 'cost' || metric === 'upstreamCost') {
        provider[metric] = addUsageMoney(provider[metric], amount)
        channel[metric] = addUsageMoney(channel[metric], amount)
      } else {
        provider[metric] += amount
        channel[metric] += amount
      }
    }
  }
  return [...result.values()]
}
