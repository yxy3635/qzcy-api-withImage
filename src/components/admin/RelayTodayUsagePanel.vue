<script setup lang="ts">
import { computed, ref } from 'vue'
import type { RelayDashboardChannel, RelayDashboardUsage } from '@/types'
import { groupTodayUsage, addUsageMoney, formatUsageMoney as money } from '@/utils/relayTodayUsage'

const props = defineProps<{
  channels: RelayDashboardChannel[]
  usage: RelayDashboardUsage[] | undefined
  loading?: boolean
}>()
const search = ref('')
const sort = ref('requests')
const collapsed = ref(new Set<string>())
const groups = computed(() => groupTodayUsage(props.channels, props.usage || []))
const totals = computed(() => groups.value.reduce((sum, channel) => ({
  requests: sum.requests + channel.requests, cost: addUsageMoney(sum.cost, channel.cost), upstreamCost: addUsageMoney(sum.upstreamCost, channel.upstreamCost)
}), { requests: 0, cost: 0, upstreamCost: 0 }))
const visibleGroups = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return groups.value.filter(channel => !keyword || [channel.name, ...channel.providers.map(p => p.name)].join(' ').toLowerCase().includes(keyword))
    .sort((a, b) => sort.value === 'cost' ? b.cost - a.cost : b.requests - a.requests)
})
function toggle(key: string) {
  const next = new Set(collapsed.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  collapsed.value = next
}
const number = (value: number) => value.toLocaleString('zh-CN')
</script>

<template>
  <section class="panel today-usage" :aria-busy="loading">
    <div class="usage-heading">
      <div><p class="usage-kicker">TODAY / CHANNELS &amp; PROVIDERS</p><h3>今日渠道与供应商用量</h3><p class="usage-note">今日 00:00 起 · 按服务端自然日统计 · 金额 USD · 每 30 秒刷新</p></div>
      <div class="usage-filters">
        <input v-model="search" type="search" aria-label="搜索渠道或供应商" placeholder="搜索渠道或供应商" />
        <select v-model="sort" aria-label="用量排序"><option value="requests">按调用次数</option><option value="cost">按扣费金额</option></select>
      </div>
    </div>
    <div class="usage-totals">
      <div><span>今日调用</span><strong>{{ usage === undefined ? '—' : number(totals.requests) }}<small> 次</small></strong></div>
      <div><span>用户扣费</span><strong>{{ usage === undefined ? '—' : money(totals.cost) }}</strong></div>
      <div><span>上游成本</span><strong>{{ usage === undefined ? '—' : money(totals.upstreamCost) }}</strong></div>
    </div>
    <p v-if="usage === undefined" class="usage-empty">{{ loading ? '正在加载今日统计…' : '今日统计暂不可用，请刷新或检查后端版本。' }}</p>
    <template v-else>
      <div class="usage-table-wrap">
        <table class="usage-table">
          <thead><tr><th scope="col">渠道 / 供应商</th><th scope="col">调用次数</th><th scope="col">失败次数</th><th scope="col">用户扣费</th><th scope="col">上游成本</th></tr></thead>
          <tbody v-for="channel in visibleGroups" :key="channel.key">
            <tr class="channel-row">
              <th scope="row"><button type="button" :aria-expanded="!collapsed.has(channel.key)" @click="toggle(channel.key)"><svg viewBox="0 0 20 20" :class="{ collapsed: collapsed.has(channel.key) }" aria-hidden="true"><path d="m6 8 4 4 4-4" /></svg><span>{{ channel.name }}<small>{{ channel.providers.length }} 个供应商{{ channel.archived ? ' · 渠道已移除' : '' }}</small></span></button></th>
              <td>{{ number(channel.requests) }}</td><td :class="{ 'usage-error': channel.errors > 0 }">{{ number(channel.errors) }}</td><td class="usage-charge">{{ money(channel.cost) }}</td><td>{{ money(channel.upstreamCost) }}</td>
            </tr>
            <template v-if="!collapsed.has(channel.key)">
              <tr v-for="provider in channel.providers" :key="provider.providerId ?? 'legacy'" class="provider-row">
                <th scope="row"><span class="provider-branch" aria-hidden="true">↳</span>{{ provider.name }}<small v-if="provider.archived">已移除</small></th>
                <td>{{ number(provider.requests) }}</td><td :class="{ 'usage-error': provider.errors > 0 }">{{ number(provider.errors) }}</td><td>{{ money(provider.cost) }}</td><td>{{ money(provider.upstreamCost) }}</td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
      <p v-if="!visibleGroups.length" class="usage-empty">{{ search ? '没有匹配的渠道或供应商' : '还没有渠道或今日调用记录' }}</p>
      <p class="usage-footnote">调用次数包含成功与失败的已记录请求；故障切换后按最终记录的供应商归属。旧日志未记录供应商时计入「未归属 / 渠道直连」，并包含在渠道合计中。上游成本按记录的计费项和渠道倍率计算。</p>
    </template>
  </section>
</template>

<style scoped>
.today-usage { padding: 24px; overflow: hidden; }
.usage-heading { display: flex; justify-content: space-between; align-items: center; gap: 20px; flex-wrap: wrap; }
.usage-kicker { color: #0284c7; font-size: 10px; letter-spacing: .14em; font-weight: 800; }
h3 { margin: 5px 0; font-size: 17px; font-weight: 800; color: #0f172a; }
.usage-note, .usage-footnote { color: #64748b; font-size: 11px; line-height: 1.8; }
.usage-filters { display: flex; gap: 8px; flex-wrap: wrap; }
.usage-filters input, .usage-filters select { min-height: 38px; max-width: 100%; border: 1px solid #dce5ef; background: #ffffffb3; border-radius: 8px; padding: 8px 12px; font-size: 12px; color: #475569; }
.usage-filters :focus-visible { outline: 2px solid #0ea5e9; outline-offset: 2px; }
.usage-totals { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin: 24px 0; }
.usage-totals div { border-left: 2px solid #bae6fd; padding-left: 14px; }
.usage-totals span { display: block; font-size: 11px; color: #64748b; margin-bottom: 5px; }
.usage-totals strong { color: #0f172a; font-size: clamp(15px, 1.6vw, 22px); font-weight: 800; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.usage-totals small { font-size: 11px; color: #64748b; }
.usage-table-wrap { overflow-x: auto; }
.usage-table { width: 100%; min-width: 650px; border-collapse: collapse; font-size: 12px; text-align: right; font-variant-numeric: tabular-nums; }
.usage-table th, .usage-table td { padding: 13px 14px; border-bottom: 1px solid #e2e8f099; white-space: nowrap; }
.usage-table th:first-child { text-align: left; white-space: normal; min-width: 230px; }
.usage-table thead { color: #64748b; font-size: 11px; background: #f1f5f980; }
.channel-row { background: #f0f9ff70; color: #0f172a; font-weight: 800; }
.channel-row button { display: flex; align-items: center; gap: 8px; text-align: left; border-radius: 4px; }
.channel-row button:focus-visible { outline: 2px solid #0ea5e9; outline-offset: 4px; }
.channel-row svg { width: 16px; height: 16px; fill: none; stroke: #0284c7; stroke-width: 1.8; transition: transform .2s; flex-shrink: 0; }
.channel-row svg.collapsed { transform: rotate(-90deg); }
.channel-row small { display: block; color: #64748b; font-size: 10px; font-weight: 500; margin-top: 3px; }
.provider-row { color: #64748b; }
.provider-row th { font-weight: 500; }
.provider-row small { margin-left: 8px; font-size: 10px; color: #94a3b8; }
.provider-branch { color: #94a3b8; padding-left: 22px; margin-right: 10px; }
.usage-table .usage-error { color: #dc2626; }.usage-charge { color: #0369a1; }
.usage-empty { padding: 25px; text-align: center; font-size: 13px; color: #64748b; }
.usage-footnote { margin-top: 15px; max-width: 1000px; }
@media (max-width: 640px) { .today-usage { padding: 16px; }.usage-totals { gap: 8px; }.usage-totals div { padding-left: 8px; }.usage-filters { width: 100%; }.usage-filters input { min-width: 0; flex: 1; width: 140px; } }
@media (prefers-reduced-motion: reduce) { .channel-row svg { transition: none; } }
</style>
