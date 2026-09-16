import type { RelayPublicChannel, RelayToken } from '@/types'

const csv = (value: string) => value.split(',').map(item => item.trim()).filter(Boolean)

export function relayModelOptions(token: RelayToken, channels: RelayPublicChannel[]) {
  const groups = new Set(csv(token.groups).map(group => group.toLowerCase()))
  const allowed = new Set(csv(token.allowedModels || ''))
  const options = new Map<string, { value: string; label: string }>()
  for (const channel of channels) {
    if (!channel.enabled || !csv(channel.groupNames).some(group => groups.has(group.toLowerCase()))) continue
    for (const model of channel.models || []) {
      const publicName = model.displayName || model.model
      if (!model.enabled || !publicName) continue
      if (allowed.size && !allowed.has(model.model) && !allowed.has(model.displayName)) continue
      // The model editor defines displayName as the public API model name.
      options.set(publicName, { value: publicName, label: publicName })
    }
  }
  return [...options.values()].sort((a, b) => a.label.localeCompare(b.label))
}
