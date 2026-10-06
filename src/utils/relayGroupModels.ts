import type { RelayGroup, RelayPublicChannel } from '@/types'

export function groupCallableModels(code: string, groups: RelayGroup[], channels: RelayPublicChannel[]) {
  const normalized = code.trim().toLowerCase()
  const group = groups.find(item => item.code.toLowerCase() === normalized && item.enabled)
  if (!group) return []
  const allowed = new Set(group.modelIds || [])
  const unrestricted = normalized === 'default' || allowed.size === 0
  const names = new Map<string, { name: string; channels: string[] }>()
  for (const channel of channels) {
    if (!channel.enabled || channel.status === 'failed') continue
    if (channel.groupNames?.trim() && !channel.groupNames.split(',').some(value => value.trim().toLowerCase() === normalized)) continue
    for (const model of channel.models || []) {
      if (!model.enabled || (!unrestricted && !allowed.has(model.modelId))) continue
      const name = model.displayName || model.model
      const entry = names.get(name) || { name, channels: [] }
      if (!entry.channels.includes(channel.name)) entry.channels.push(channel.name)
      names.set(name, entry)
    }
  }
  return [...names.values()].sort((a, b) => a.name.localeCompare(b.name))
}
