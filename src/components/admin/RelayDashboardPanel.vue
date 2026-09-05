<script setup lang="ts">
import { computed } from 'vue'
import RelayDashboardTrend from './RelayDashboardTrend.vue'
import type { RelayDashboard, RelayDashboardChannel, RelayDashboardError } from '@/types'

const props = withDefaults(defineProps<{
  data: RelayDashboard | null
  loading?: boolean
  testingChannelId?: number | null
}>(), {
  loading: false,
  testingChannelId: null
})

const emit = defineEmits<{
  refresh: []
  testChannel: [channelId: number]
  chatTest: [channelId: number]
  editChannel: [channelId: number]
}>()

const summary = computed(() => props.data?.summary)
const channels = computed(() => props.data?.channels || [])
const topModels = computed(() => props.data?.topModels || [])
const recentErrors = computed(() => props.data?.recentErrors || [])
const maxModelRequests = computed(() => Math.max(1, ...topModels.value.map((item) => Number(item.requests || 0))))

const healthMeta: Record<string, { label: string; dot: string; chip: string }> = {
  ok: { label: '正常', dot: 'bg-emerald-500', chip: 'bg-emerald-50 text-emerald-700 ring-emerald-200' },
  degraded: { label: '待探活', dot: 'bg-amber-400', chip: 'bg-amber-50 text-amber-700 ring-amber-200' },
  down: { label: '不可用', dot: 'bg-red-500', chip: 'bg-red-50 text-red-600 ring-red-200' },
  disabled: { label: '已停用', dot: 'bg-slate-300', chip: 'bg-slate-100 text-slate-500 ring-slate-200' }
}

const providerDotClass: Record<string, string> = {
  available: 'bg-emerald-500',
  failed: 'bg-red-500',
  unknown: 'bg-slate-300'
}

const strategyLabels: Record<string, string> = {
  weighted_random: '加权随机',
  smooth_rr: '平滑轮询',
  least_conn: '最小并发',
  priority: '严格优先级'
}

function strategyLabel(strategy?: string) {
  return strategyLabels[strategy || 'weighted_random'] || strategy || '加权随机'
}

const fallbackHealth = { label: '未知', dot: 'bg-amber-400', chip: 'bg-amber-50 text-amber-700 ring-amber-200' }

function healthOf(channel: RelayDashboardChannel) {
  return healthMeta[channel.health] ?? fallbackHealth
}

function providerStatusOf(status?: string) {
  if (status === 'available') return '可用'
  if (status === 'failed') return '失败'
  return '未探活'
}

function compactNumber(value?: number | null) {
  const amount = Number(value || 0)
  if (amount >= 1_000_000) return `${(amount / 1_000_000).toFixed(2)}M`
  if (amount >= 1_000) return `${(amount / 1_000).toFixed(1)}K`
  return amount.toLocaleString()
}

function formatCost(value?: number | null) {
  return `$${Number(value || 0).toFixed(2)}`
}

function formatMs(value?: number | null) {
  const ms = Number(value || 0)
  if (ms <= 0) return '—'
  if (ms >= 60_000) return `${(ms / 60_000).toFixed(1)}min`
  if (ms >= 1_000) return `${(ms / 1_000).toFixed(1)}s`
  return `${Math.round(ms)}ms`
}

function relativeTime(value?: string | null) {
  if (!value) return ''
  const time = new Date(value.replace(' ', 'T')).getTime()
  if (Number.isNaN(time)) return ''
  const diff = Date.now() - time
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return `${Math.floor(diff / 86_400_000)} 天前`
}

function errorRateClass(rate?: number) {
  const value = Number(rate || 0)
  if (value >= 5) return 'text-red-600'
  if (value > 0) return 'text-amber-600'
  return 'text-emerald-600'
}

function shortMessage(message?: string | null) {
  const text = String(message || '').trim()
  if (!text) return '—'
  return text.length > 90 ? `${text.slice(0, 90)}…` : text
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <p class="text-xs font-black uppercase tracking-[0.16em] text-sky-600">Relay Dashboard</p>
        <h2 class="mt-1 text-lg font-black text-slate-950">运行总览</h2>
      </div>
      <button
        class="h-10 rounded-xl bg-slate-950 px-4 text-xs font-black text-white transition hover:bg-sky-600 disabled:opacity-60"
        :disabled="loading"
        @click="emit('refresh')"
      >{{ loading ? '刷新中…' : '立即刷新' }}</button>
    </div>

    <p v-if="!data && loading" class="panel p-10 text-center text-sm font-black text-slate-500">仪表盘加载中…</p>
    <p v-else-if="!data" class="panel p-10 text-center text-sm font-black text-slate-500">暂无仪表盘数据</p>

    <template v-else>
      <!-- KPI 横幅 -->
      <section class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        <div class="panel p-4">
          <p class="text-xs font-bold text-slate-500">渠道可用</p>
          <p class="mt-1.5 text-2xl font-black tabular-nums" :class="(summary?.channelsAvailable || 0) < (summary?.channelsTotal || 0) ? 'text-amber-600' : 'text-slate-950'">
            {{ summary?.channelsAvailable || 0 }}<span class="text-sm text-slate-400">/{{ summary?.channelsTotal || 0 }}</span>
          </p>
        </div>
        <div class="panel p-4">
          <p class="text-xs font-bold text-slate-500">供应商可用</p>
          <p class="mt-1.5 text-2xl font-black tabular-nums" :class="(summary?.providersAvailable || 0) === 0 && (summary?.providersTotal || 0) > 0 ? 'text-red-600' : 'text-slate-950'">
            {{ summary?.providersAvailable || 0 }}<span class="text-sm text-slate-400">/{{ summary?.providersTotal || 0 }}</span>
          </p>
        </div>
        <div class="panel p-4">
          <p class="text-xs font-bold text-slate-500">今日请求 · 错误率</p>
          <p class="mt-1.5 text-2xl font-black tabular-nums text-slate-950">
            {{ compactNumber(summary?.todayRequests) }}
            <span class="ml-1 text-sm font-black" :class="errorRateClass(summary?.errorRate)">{{ Number(summary?.errorRate || 0).toFixed(1) }}%</span>
          </p>
          <p class="text-xs font-semibold text-slate-400">失败 {{ compactNumber(summary?.todayErrors) }}</p>
        </div>
        <div class="panel p-4">
          <p class="text-xs font-bold text-slate-500">今日 Token</p>
          <p class="mt-1.5 text-2xl font-black tabular-nums text-sky-600">{{ compactNumber(summary?.todayTokens) }}</p>
        </div>
        <div class="panel p-4">
          <p class="text-xs font-bold text-slate-500">今日消费</p>
          <p class="mt-1.5 text-2xl font-black tabular-nums text-slate-950">{{ formatCost(summary?.todayCost) }}</p>
        </div>
        <div class="panel p-4">
          <p class="text-xs font-bold text-slate-500">当前 RPM</p>
          <p class="mt-1.5 text-2xl font-black tabular-nums text-slate-950">{{ summary?.currentRpm || 0 }}</p>
          <p class="text-xs font-semibold text-slate-400">最近 1 分钟</p>
        </div>
      </section>

      <!-- 24h 趋势 -->
      <RelayDashboardTrend :points="data.trend || []" :loading="loading" />

      <!-- 渠道状态墙 -->
      <section>
        <div class="mb-2 flex items-center justify-between">
          <h3 class="text-sm font-black text-slate-800">渠道状态（{{ channels.length }}）</h3>
          <p class="text-xs font-semibold text-slate-500">指标为近 24 小时 · 每 30 秒自动刷新</p>
        </div>
        <div class="grid gap-3 lg:grid-cols-2 2xl:grid-cols-3">
          <article
            v-for="channel in channels" :key="channel.id"
            class="panel flex flex-col gap-2.5 p-4"
            :class="{ 'opacity-60': channel.health === 'disabled' }"
          >
            <div class="flex min-w-0 items-start justify-between gap-2">
              <div class="flex min-w-0 items-center gap-2">
                <span class="h-2.5 w-2.5 shrink-0 rounded-full" :class="healthOf(channel).dot"></span>
                <h4 class="truncate text-sm font-black text-slate-950">{{ channel.name }}</h4>
                <span class="shrink-0 rounded-md px-1.5 py-0.5 text-[10px] font-black ring-1" :class="healthOf(channel).chip">{{ healthOf(channel).label }}</span>
              </div>
              <span class="shrink-0 text-[10px] font-black text-slate-400">P{{ channel.priority }} · {{ strategyLabel(channel.scheduleStrategy) }}</span>
            </div>

            <div class="space-y-1">
              <div
                v-for="provider in channel.providers" :key="provider.id"
                class="flex min-w-0 items-center gap-2 rounded-lg bg-slate-50 px-2 py-1 text-xs"
              >
                <span class="h-1.5 w-1.5 shrink-0 rounded-full" :class="providerDotClass[provider.status] || 'bg-slate-300'"></span>
                <span class="truncate font-black text-slate-700" :class="{ 'line-through opacity-50': !provider.enabled }">{{ provider.name || '未命名供应商' }}</span>
                <span class="shrink-0 rounded bg-white px-1 py-0.5 text-[10px] font-black text-slate-500 ring-1 ring-slate-200">{{ provider.channelRule === 'anthropic' ? 'Anthropic' : 'OpenAI' }}</span>
                <span v-if="!provider.enabled" class="shrink-0 text-[10px] font-black text-slate-400">已停用</span>
                <span v-else-if="provider.circuitOpen" class="shrink-0 rounded bg-red-50 px-1 py-0.5 text-[10px] font-black text-red-600">熔断中</span>
                <span v-else class="ml-auto shrink-0 text-[10px] font-bold" :class="provider.status === 'failed' ? 'text-red-500' : 'text-slate-400'">{{ providerStatusOf(provider.status) }}</span>
              </div>
              <p v-if="!channel.providers?.length" class="rounded-lg bg-slate-50 px-2 py-1 text-xs font-bold text-slate-400">未配置供应商</p>
            </div>

            <div class="grid grid-cols-4 gap-1.5 text-center">
              <div class="rounded-lg bg-slate-50 py-1.5">
                <p class="text-[10px] font-bold text-slate-400">请求</p>
                <p class="text-xs font-black tabular-nums text-slate-800">{{ compactNumber(channel.requests24h) }}</p>
              </div>
              <div class="rounded-lg bg-slate-50 py-1.5">
                <p class="text-[10px] font-bold text-slate-400">失败</p>
                <p class="text-xs font-black tabular-nums" :class="channel.errors24h > 0 ? 'text-red-600' : 'text-slate-800'">{{ compactNumber(channel.errors24h) }}</p>
              </div>
              <div class="rounded-lg bg-slate-50 py-1.5">
                <p class="text-[10px] font-bold text-slate-400">均耗时</p>
                <p class="text-xs font-black tabular-nums text-slate-800">{{ formatMs(channel.avgDurationMs) }}</p>
              </div>
              <div class="rounded-lg bg-slate-50 py-1.5">
                <p class="text-[10px] font-bold text-slate-400">首字</p>
                <p class="text-xs font-black tabular-nums text-slate-800">{{ formatMs(channel.avgFirstTokenMs) }}</p>
              </div>
            </div>

            <div class="flex items-center justify-between text-[11px] font-bold text-slate-500">
              <span>Token {{ compactNumber(channel.tokens24h) }} · {{ formatCost(channel.cost24h) }}</span>
              <span v-if="channel.lastErrorAt" class="text-red-500">
                最近错误 {{ relativeTime(channel.lastErrorAt) }}<template v-if="channel.lastErrorCode"> · {{ channel.lastErrorCode }}</template>
              </span>
              <span v-else class="text-slate-400">24h 无错误</span>
            </div>

            <div class="flex gap-2 pt-0.5">
              <button
                class="h-8 flex-1 rounded-lg bg-sky-50 text-xs font-black text-sky-700 transition hover:bg-sky-100 disabled:opacity-60"
                :disabled="testingChannelId === channel.id"
                @click="emit('testChannel', channel.id)"
              >{{ testingChannelId === channel.id ? '检测中…' : '检测' }}</button>
              <button class="h-8 flex-1 rounded-lg bg-indigo-50 text-xs font-black text-indigo-700 transition hover:bg-indigo-100" @click="emit('chatTest', channel.id)">测试</button>
              <button class="h-8 flex-1 rounded-lg bg-slate-100 text-xs font-black text-slate-600 transition hover:bg-slate-200" @click="emit('editChannel', channel.id)">编辑</button>
            </div>
          </article>
        </div>
        <p v-if="!channels.length" class="panel p-8 text-center text-sm font-black text-slate-500">还没有渠道，去「渠道」页新增</p>
      </section>

      <!-- 模型热度 + 最近错误 -->
      <section class="grid gap-3 lg:grid-cols-2">
        <div class="panel p-4">
          <h3 class="text-sm font-black text-slate-800">模型热度（今日 Top {{ topModels.length }}）</h3>
          <div class="mt-3 space-y-2">
            <div v-for="item in topModels" :key="item.model" class="flex items-center gap-2">
              <span class="w-36 shrink-0 truncate text-xs font-black text-slate-700" :title="item.model">{{ item.model }}</span>
              <div class="h-4 min-w-0 flex-1 overflow-hidden rounded bg-slate-100">
                <div class="h-full rounded bg-sky-400/80" :style="{ width: `${Math.max(4, (Number(item.requests || 0) / maxModelRequests) * 100)}%` }"></div>
              </div>
              <span class="w-14 shrink-0 text-right text-xs font-black tabular-nums text-slate-700">{{ compactNumber(item.requests) }}</span>
              <span class="w-16 shrink-0 text-right text-[11px] font-bold tabular-nums text-slate-400">{{ formatCost(item.cost) }}</span>
            </div>
            <p v-if="!topModels.length" class="py-4 text-center text-xs font-bold text-slate-400">今日暂无模型调用</p>
          </div>
        </div>

        <div class="panel p-4">
          <h3 class="text-sm font-black text-slate-800">最近错误（{{ recentErrors.length }}）</h3>
          <div class="mt-3 max-h-72 space-y-1.5 overflow-y-auto pr-1">
            <div
              v-for="error in recentErrors" :key="error.id"
              class="flex items-start gap-2 rounded-lg bg-red-50/60 px-2.5 py-1.5"
            >
              <span class="mt-0.5 shrink-0 rounded bg-red-100 px-1.5 py-0.5 text-[10px] font-black text-red-600 tabular-nums">{{ error.statusCode || 'ERR' }}</span>
              <div class="min-w-0 flex-1">
                <p class="flex items-baseline gap-2 text-xs font-black text-slate-800">
                  <span class="truncate">{{ error.channelName || '未知渠道' }}</span>
                  <span class="shrink-0 text-[10px] font-bold text-slate-400">{{ error.model }}</span>
                </p>
                <p class="truncate text-[11px] font-semibold text-slate-500" :title="shortMessage(error.message)">{{ shortMessage(error.message) }}</p>
              </div>
              <span class="shrink-0 text-[10px] font-bold text-slate-400 tabular-nums">
                {{ relativeTime(error.createdAt) }}<template v-if="error.durationMs"> · {{ formatMs(error.durationMs) }}</template>
              </span>
            </div>
            <p v-if="!recentErrors.length" class="py-4 text-center text-xs font-bold text-emerald-600">最近没有失败请求</p>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>
