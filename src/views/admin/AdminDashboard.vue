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
