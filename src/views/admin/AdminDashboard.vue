<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import { adminApi } from '@/api/adminApi'
import type { AdminStats } from '@/types'

const stats = ref<AdminStats | null>(null)

onMounted(async () => {
  const { data } = await adminApi.dashboard()
  stats.value = data.data
})

const totalRelayTokens = computed(() =>
  (stats.value?.relayChannelProfits || []).reduce((sum, item) => sum + Number(item.totalTokens || 0), 0)
)

const primaryCards = computed(() => [
  { label: '总用户', value: compact(stats.value?.totalUsers), sub: '平台注册账户', tone: 'slate' },
  { label: '总生成', value: compact(stats.value?.totalImages), sub: `今日 ${compact(stats.value?.todayImages)}`, tone: 'blue' },
  { label: '总收入', value: yuan(stats.value?.totalRevenue, 2), sub: '充值入账金额', tone: 'emerald' },
  { label: '累计 Token', value: compact(totalRelayTokens.value), sub: '中转调用累计', tone: 'violet' }
])

const relayCards = computed(() => [
  { label: '今日调用', value: compact(stats.value?.todayRelayRequests), sub: `昨日 ${compact(stats.value?.yesterdayRelayRequests)}`, tone: 'blue' },
  { label: '今日调用金额', value: yuan(stats.value?.todayRelayCost), sub: `昨日 ${yuan(stats.value?.yesterdayRelayCost)}`, tone: 'emerald' },
  { label: '今日 Tokens', value: compact(stats.value?.todayRelayTokens), sub: `昨日 ${compact(stats.value?.yesterdayRelayTokens)}`, tone: 'amber' },
  { label: '今日渠道成本', value: yuan(stats.value?.todayRelayUpstreamCost), sub: `昨日 ${yuan(stats.value?.yesterdayRelayUpstreamCost)}`, tone: 'rose' },
  { label: '今日利润', value: yuan(Math.abs(Number(stats.value?.todayRelayProfit || 0))), sub: `昨日 ${yuan(Math.abs(Number(stats.value?.yesterdayRelayProfit || 0)))}`, tone: Number(stats.value?.todayRelayProfit || 0) >= 0 ? 'emerald' : 'red' },
  { label: '累计调用金额', value: yuan(stats.value?.relaySiteCost), sub: `渠道成本 ${yuan(stats.value?.relayUpstreamCost)}`, tone: 'slate' }
])

function money(value?: number) {
  return Number(value || 0).toFixed(4)
}

function yuan(value?: number, digits = 4) {
  return `¥ ${Number(value || 0).toFixed(digits)}`
}

function compact(value?: number) {
  const amount = Number(value || 0)
  if (amount >= 1_000_000_000) return `${(amount / 1_000_000_000).toFixed(2)}B`
  if (amount >= 1_000_000) return `${(amount / 1_000_000).toFixed(2)}M`
  if (amount >= 1_000) return `${(amount / 1_000).toFixed(2)}K`
  return String(amount)
}

function cardClass(tone: string) {
  const tones: Record<string, string> = {
    slate: 'border-slate-100 text-slate-900',
    blue: 'border-blue-100 text-blue-700',
    emerald: 'border-emerald-100 text-emerald-700',
    violet: 'border-violet-100 text-violet-700',
    amber: 'border-amber-100 text-amber-700',
    rose: 'border-rose-100 text-rose-700',
    red: 'border-red-100 text-red-600'
  }
  return tones[tone] || tones.slate
}

function trendWidth(count: number) {
  const max = Math.max(
    1,
    ...(stats.value?.recentRegistrations || []).map((item) => Number(item.count || 0)),
    ...(stats.value?.generationTrend || []).map((item) => Number(item.count || 0))
  )
  return `${Math.max(4, Math.min(100, (Number(count || 0) / max) * 100))}%`
}
</script>

<template>
  <AppLayout admin>
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="text-sm font-black tracking-[0.22em] text-sky-600">运营总览</p>
        <h1 class="mt-2 text-3xl font-black tracking-tight text-slate-950">管理员仪表盘</h1>
        <p class="mt-2 text-sm font-semibold text-slate-500">账户、收入、中转调用、Token 与利润实时统计。</p>
      </div>
    </div>

    <div class="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <article v-for="card in primaryCards" :key="card.label" class="rounded-lg border bg-white p-5 shadow-sm" :class="cardClass(card.tone)">
        <p class="text-sm font-black text-slate-500">{{ card.label }}</p>
        <p class="mt-3 text-3xl font-black">{{ card.value }}</p>
        <p class="mt-2 text-xs font-bold text-slate-400">{{ card.sub }}</p>
      </article>
    </div>

    <section class="mt-4 rounded-lg border border-slate-100 bg-white p-5 shadow-sm">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="text-xl font-black text-slate-950">API 中转关键指标</h2>
          <p class="mt-1 text-sm font-semibold text-slate-500">今日 / 昨日调用金额、请求数、Token、成本和利润。</p>
        </div>
        <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-600">
          累计{{ Number(stats?.relayProfit || 0) >= 0 ? '盈利' : '亏损' }} {{ yuan(Math.abs(Number(stats?.relayProfit || 0))) }}
        </span>
      </div>
      <div class="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <article v-for="card in relayCards" :key="card.label" class="rounded-lg border bg-slate-50/40 p-4" :class="cardClass(card.tone)">
          <p class="text-sm font-black text-slate-500">{{ card.label }}</p>
          <p class="mt-2 text-2xl font-black">{{ card.value }}</p>
          <p class="mt-1 text-xs font-bold text-slate-400">{{ card.sub }}</p>
        </article>
      </div>
    </section>

    <section class="panel mt-6 overflow-hidden">
      <div class="border-b border-slate-200 p-5">
        <h2 class="font-bold">渠道盈亏</h2>
        <p class="mt-2 text-sm font-semibold text-slate-500">格式：渠道产生成本 / 本站调用金额；盈利 = 本站调用金额 - 渠道产生成本。</p>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full min-w-[760px] text-left text-sm">
          <thead class="bg-slate-50 text-xs font-black uppercase text-slate-500">
            <tr>
              <th class="px-4 py-3">渠道</th>
              <th class="px-4 py-3">调用</th>
              <th class="px-4 py-3">Token</th>
              <th class="px-4 py-3">渠道产生成本 / 本站调用金额</th>
              <th class="px-4 py-3">结果</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 bg-white">
            <tr v-for="item in stats?.relayChannelProfits || []" :key="item.channelId || item.channelName">
              <td class="px-4 py-3 font-black text-slate-900">{{ item.channelName || 'Unknown' }}</td>
              <td class="px-4 py-3 font-semibold text-slate-600">{{ item.requests || 0 }}</td>
              <td class="px-4 py-3 font-semibold text-slate-600">{{ compact(item.totalTokens) }}</td>
              <td class="px-4 py-3 font-black text-slate-800">{{ yuan(item.upstreamCost) }} / {{ yuan(item.siteCost) }}</td>
              <td class="px-4 py-3">
                <span class="rounded-lg px-2 py-1 text-xs font-black" :class="Number(item.profit || 0) >= 0 ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-600'">
                  {{ Number(item.profit || 0) >= 0 ? '盈利' : '亏损' }} {{ yuan(Math.abs(Number(item.profit || 0))) }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!(stats?.relayChannelProfits || []).length" class="p-8 text-center text-sm font-black text-slate-500">暂无中转调用记录</div>
    </section>

    <div class="mt-6 grid gap-6 lg:grid-cols-2">
      <section class="panel p-5">
        <h2 class="font-bold">近 7 天注册趋势</h2>
        <div class="mt-4 space-y-3">
          <div v-for="item in stats?.recentRegistrations || []" :key="item.date" class="grid grid-cols-[86px_1fr_42px] items-center gap-3 text-sm">
            <span class="font-semibold text-slate-600">{{ item.date }}</span>
            <div class="h-3 rounded bg-blue-100">
              <div class="h-3 rounded bg-ocean" :style="{ width: trendWidth(Number(item.count || 0)) }" />
            </div>
            <span class="font-black text-slate-600">{{ item.count }}</span>
          </div>
        </div>
      </section>
      <section class="panel p-5">
        <h2 class="font-bold">近 7 天生成趋势</h2>
        <div class="mt-4 space-y-3">
          <div v-for="item in stats?.generationTrend || []" :key="item.date" class="grid grid-cols-[86px_1fr_42px] items-center gap-3 text-sm">
            <span class="font-semibold text-slate-600">{{ item.date }}</span>
            <div class="h-3 rounded bg-violet-100">
              <div class="h-3 rounded bg-iris" :style="{ width: trendWidth(Number(item.count || 0)) }" />
            </div>
            <span class="font-black text-slate-600">{{ item.count }}</span>
          </div>
        </div>
      </section>
    </div>
  </AppLayout>
</template>
