<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import { adminApi } from '@/api/adminApi'
import type { AdminStats } from '@/types'

const stats = ref<AdminStats | null>(null)
onMounted(async () => {
  const { data } = await adminApi.dashboard()
  stats.value = data.data
})

function money(value?: number) {
  return Number(value || 0).toFixed(4)
}
</script>

<template>
  <AppLayout admin>
    <h1 class="text-2xl font-black">管理员仪表盘</h1>
    <div class="mt-6 grid gap-3 sm:grid-cols-2 md:grid-cols-4">
      <div class="panel p-4 sm:p-5"><p class="text-sm text-slate-500">总用户</p><p class="mt-2 text-2xl font-black sm:text-3xl">{{ stats?.totalUsers || 0 }}</p></div>
      <div class="panel p-4 sm:p-5"><p class="text-sm text-slate-500">总生成</p><p class="mt-2 text-2xl font-black sm:text-3xl">{{ stats?.totalImages || 0 }}</p></div>
      <div class="panel p-4 sm:p-5"><p class="text-sm text-slate-500">今日生成</p><p class="mt-2 text-2xl font-black sm:text-3xl">{{ stats?.todayImages || 0 }}</p></div>
      <div class="panel p-4 sm:p-5"><p class="text-sm text-slate-500">总收入</p><p class="mt-2 text-2xl font-black sm:text-3xl">￥{{ Number(stats?.totalRevenue || 0).toFixed(2) }}</p></div>
    </div>
    <div class="mt-3 grid gap-3 md:grid-cols-3">
      <div class="panel p-4 sm:p-5">
        <p class="text-sm text-slate-500">本站调用成本</p>
        <p class="mt-2 text-2xl font-black sm:text-3xl">￥{{ money(stats?.relaySiteCost) }}</p>
      </div>
      <div class="panel p-4 sm:p-5">
        <p class="text-sm text-slate-500">渠道产生成本</p>
        <p class="mt-2 text-2xl font-black sm:text-3xl">￥{{ money(stats?.relayUpstreamCost) }}</p>
      </div>
      <div class="panel p-4 sm:p-5">
        <p class="text-sm text-slate-500">中转盈利</p>
        <p class="mt-2 text-2xl font-black sm:text-3xl" :class="Number(stats?.relayProfit || 0) >= 0 ? 'text-emerald-600' : 'text-red-600'">
          {{ Number(stats?.relayProfit || 0) >= 0 ? '盈利' : '亏损' }} ￥{{ money(Math.abs(Number(stats?.relayProfit || 0))) }}
        </p>
      </div>
    </div>
    <section class="panel mt-6 overflow-hidden">
      <div class="border-b border-slate-200 p-5">
        <h2 class="font-bold">渠道盈亏</h2>
        <p class="mt-2 text-sm font-semibold text-slate-500">格式：渠道产生成本 / 本站调用成本；盈利 = 本站调用成本 - 渠道产生成本。</p>
      </div>
      <div class="overflow-x-auto">
        <table class="min-w-[760px] w-full text-left text-sm">
          <thead class="bg-slate-50 text-xs font-black uppercase text-slate-500">
            <tr>
              <th class="px-4 py-3">渠道</th>
              <th class="px-4 py-3">调用</th>
              <th class="px-4 py-3">Token</th>
              <th class="px-4 py-3">渠道产生成本 / 本站调用成本</th>
              <th class="px-4 py-3">结果</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 bg-white">
            <tr v-for="item in stats?.relayChannelProfits || []" :key="item.channelId || item.channelName">
              <td class="px-4 py-3 font-black text-slate-900">{{ item.channelName || 'Unknown' }}</td>
              <td class="px-4 py-3 font-semibold text-slate-600">{{ item.requests || 0 }}</td>
              <td class="px-4 py-3 font-semibold text-slate-600">{{ item.totalTokens || 0 }}</td>
              <td class="px-4 py-3 font-black text-slate-800">￥{{ money(item.upstreamCost) }} / ￥{{ money(item.siteCost) }}</td>
              <td class="px-4 py-3">
                <span class="rounded-lg px-2 py-1 text-xs font-black" :class="Number(item.profit || 0) >= 0 ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-600'">
                  {{ Number(item.profit || 0) >= 0 ? '盈利' : '亏损' }} ￥{{ money(Math.abs(Number(item.profit || 0))) }}
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
        <h2 class="font-bold">近7天注册趋势</h2>
        <div class="mt-4 space-y-3">
          <div v-for="item in stats?.recentRegistrations || []" :key="item.date" class="grid grid-cols-[76px_1fr_32px] items-center gap-2 text-xs sm:grid-cols-[100px_1fr_40px] sm:gap-3 sm:text-sm">
            <span>{{ item.date }}</span><div class="h-3 rounded bg-blue-100"><div class="h-3 rounded bg-ocean" :style="{ width: `${Math.min(100, Number(item.count) * 12)}%` }" /></div><span>{{ item.count }}</span>
          </div>
        </div>
      </section>
      <section class="panel p-5">
        <h2 class="font-bold">近7天生成趋势</h2>
        <div class="mt-4 space-y-3">
          <div v-for="item in stats?.generationTrend || []" :key="item.date" class="grid grid-cols-[76px_1fr_32px] items-center gap-2 text-xs sm:grid-cols-[100px_1fr_40px] sm:gap-3 sm:text-sm">
            <span>{{ item.date }}</span><div class="h-3 rounded bg-violet-100"><div class="h-3 rounded bg-iris" :style="{ width: `${Math.min(100, Number(item.count) * 12)}%` }" /></div><span>{{ item.count }}</span>
          </div>
        </div>
      </section>
    </div>
  </AppLayout>
</template>
