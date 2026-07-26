<script setup lang="ts">
import { computed } from 'vue'
import type { RelayUsageLog } from '@/types'

const props = defineProps<{
  logs: RelayUsageLog[]
  max?: number
}>()

const emit = defineEmits<{ (e: 'view-all'): void }>()

const PULSE_COUNT = 20

const list = computed(() => props.logs.slice(0, props.max || 6))

const maxDuration = computed(() => Math.max(1, ...list.value.map((log) => Number(log.durationMs || 0))))

const pulseRows = computed(() => props.logs.slice(0, PULSE_COUNT))

const pulse = computed(() => {
  const rows = pulseRows.value
  const maxMs = Math.max(1, ...rows.map((log) => Number(log.durationMs || 0)))
  return rows.slice().reverse().map((log) => ({
    id: log.id,
    failed: isFailed(log),
    height: 6 + Math.round((Number(log.durationMs || 0) / maxMs) * 16),
    title: `${log.model || '-'} · ${(Number(log.durationMs || 0) / 1000).toFixed(2)}s · ${isFailed(log) ? '失败' : '成功'}`
  }))
})

const successRate = computed(() => {
  const rows = pulseRows.value
  if (!rows.length) return 0
  return (rows.filter((log) => !isFailed(log)).length / rows.length) * 100
})

const averageSeconds = computed(() => {
  const rows = pulseRows.value
  if (!rows.length) return '0.00'
  const total = rows.reduce((sum, log) => sum + Number(log.durationMs || 0), 0)
  return (total / rows.length / 1000).toFixed(2)
})

function isFailed(log: RelayUsageLog) {
  return log.status === 'failed' || Number(log.statusCode || 0) >= 400
}

function durationPercent(log: RelayUsageLog) {
  return Math.max(4, Math.min(100, (Number(log.durationMs || 0) / maxDuration.value) * 100))
}

function compact(value: number) {
  if (value >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(2)}B`
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(2)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(2)}K`
  return String(value || 0)
}

function money(value: number) {
  return `$${Number(value || 0).toFixed(6)}`
}

function seconds(log: RelayUsageLog) {
  return `${(Number(log.durationMs || 0) / 1000).toFixed(2)}s`
}

function relativeTime(value?: string) {
  if (!value) return '-'
  const time = new Date(value.replace(' ', 'T')).getTime()
  if (!Number.isFinite(time)) return value.replace('T', ' ').slice(5, 16)
  const diff = Date.now() - time
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return `${Math.floor(diff / 86_400_000)} 天前`
}
</script>

<template>
  <div class="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
    <div class="flex items-center justify-between gap-3">
      <div class="flex items-center gap-2.5">
        <h2 class="text-lg font-black text-slate-950">最近调用</h2>
        <span class="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-[10px] font-black text-emerald-700">
          <i class="relative h-1.5 w-1.5 rounded-full bg-emerald-500"><i class="absolute inset-0 animate-ping rounded-full bg-emerald-500"></i></i>
          实时
        </span>
      </div>
      <button
        type="button"
        class="group inline-flex items-center gap-1 text-xs font-black text-slate-500 transition hover:text-emerald-700"
        @click="emit('view-all')"
      >
        查看全部
        <svg viewBox="0 0 24 24" class="h-3.5 w-3.5 transition-transform duration-200 group-hover:translate-x-0.5" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 6 6 6-6 6" /></svg>
      </button>
    </div>

    <div v-if="pulse.length" class="mt-4 flex items-center justify-between gap-4 rounded-xl border border-slate-100 bg-slate-50/70 px-3.5 py-2.5">
      <div class="min-w-0">
        <p class="text-[10px] font-black uppercase tracking-[0.14em] text-slate-400">近 {{ pulse.length }} 次</p>
        <p class="mt-0.5 text-xs font-black text-slate-700">
          成功率
          <span :class="successRate >= 90 ? 'text-emerald-600' : successRate >= 60 ? 'text-amber-600' : 'text-rose-600'">{{ successRate.toFixed(0) }}%</span>
          <span class="text-slate-300"> · </span>
          平均 {{ averageSeconds }}s
        </p>
      </div>
      <div class="flex h-[26px] shrink-0 items-end gap-[3px]" aria-label="最近调用状态脉冲">
        <i
          v-for="(bar, index) in pulse"
          :key="bar.id"
          class="pulse-bar"
          :class="[bar.failed ? 'bg-rose-400' : 'bg-emerald-400', index === pulse.length - 1 ? 'pulse-latest' : '']"
          :style="{ height: `${bar.height}px`, '--pd': `${index * 28}ms`, '--pulse-glow': bar.failed ? 'rgba(244,63,94,.45)' : 'rgba(16,185,129,.45)' }"
          :title="bar.title"
        ></i>
      </div>
    </div>

    <TransitionGroup v-if="list.length" tag="div" name="rc" appear class="relative mt-3">
      <article
        v-for="(item, index) in list"
        :key="item.id"
        class="rc-row group relative mt-2 overflow-hidden rounded-xl border border-slate-100 bg-white transition-all duration-200 first:mt-0 hover:-translate-y-0.5 hover:border-slate-200 hover:shadow-lg hover:shadow-slate-200/50"
        :style="{ '--d': `${index * 55}ms` }"
      >
        <div class="grid grid-cols-[1fr_auto] items-center gap-3 px-3.5 py-3">
          <div class="min-w-0">
            <div class="flex min-w-0 items-center gap-2">
              <p class="truncate text-sm font-black text-slate-950" :title="item.model">{{ item.model || '-' }}</p>
              <span
                v-if="isFailed(item)"
                class="shrink-0 rounded-full bg-rose-50 px-2 py-0.5 text-[10px] font-black text-rose-600"
              >失败{{ item.statusCode ? ` ${item.statusCode}` : '' }}</span>
              <span v-else class="h-1.5 w-1.5 shrink-0 rounded-full bg-emerald-500" title="成功"></span>
            </div>
            <div class="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[10px] font-bold text-slate-400">
              <span class="inline-flex min-w-0 items-center gap-1">
                <svg viewBox="0 0 24 24" class="h-3 w-3 shrink-0" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 7a4 4 0 1 1-2.4 3.67L4 19.27V21h1.73l1-1H9v-2h2v-2h2l2.33-2.33A4 4 0 0 1 15 7z" /></svg>
                <span class="max-w-32 truncate">{{ item.tokenName || '未命名密钥' }}</span>
              </span>
              <span class="inline-flex items-center gap-1 tabular-nums" title="输入 Token">
                <svg viewBox="0 0 24 24" class="h-3 w-3 shrink-0" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 19V5m-6 6 6-6 6 6" /></svg>
                {{ compact(item.promptTokens) }}
              </span>
              <span class="inline-flex items-center gap-1 tabular-nums" title="输出 Token">
                <svg viewBox="0 0 24 24" class="h-3 w-3 shrink-0" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 5v14m6-6-6 6-6-6" /></svg>
                {{ compact(item.completionTokens) }}
              </span>
              <span class="inline-flex items-center gap-1 tabular-nums" title="耗时">
                <svg viewBox="0 0 24 24" class="h-3 w-3 shrink-0" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M12 8v4l3 2M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z" /></svg>
                {{ seconds(item) }}
              </span>
            </div>
            <p v-if="isFailed(item) && item.message" class="mt-1.5 truncate text-[10px] font-bold text-rose-500" :title="item.message">{{ item.message }}</p>
          </div>

          <div class="shrink-0 text-right">
            <p class="font-mono text-sm font-black tabular-nums text-slate-900">{{ money(item.cost) }}</p>
            <p class="mt-0.5 text-[10px] font-bold text-slate-400">{{ relativeTime(item.createdAt) }}</p>
          </div>
        </div>

        <span
          class="rc-duration absolute bottom-0 left-0 h-[2px] rounded-full opacity-50 transition-all duration-500 group-hover:opacity-90"
          :class="isFailed(item) ? 'bg-gradient-to-r from-rose-300 to-rose-400' : 'bg-gradient-to-r from-emerald-300 to-cyan-400'"
          :style="{ width: `${durationPercent(item)}%` }"
          aria-hidden="true"
        ></span>
      </article>
    </TransitionGroup>

    <div v-else class="mt-4 grid h-48 place-items-center rounded-xl border border-dashed border-slate-200">
      <div class="text-center">
        <div class="mx-auto grid h-11 w-11 place-items-center rounded-2xl bg-slate-100 text-slate-400">
          <svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 12h4l2.5-6 3 12 2.5-6h4" /></svg>
        </div>
        <p class="mt-3 text-sm font-black text-slate-500">暂无调用日志</p>
        <p class="mt-1 text-xs font-semibold text-slate-400">配置密钥并发起请求后即可看到实时记录</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pulse-bar {
  display: block;
  width: 4px;
  border-radius: 2px;
  transform-origin: bottom;
  transition: height 400ms cubic-bezier(.16, 1, .3, 1);
  animation: pulseBarIn 420ms cubic-bezier(.16, 1, .3, 1) both;
  animation-delay: var(--pd, 0ms);
}

.pulse-bar:hover {
  filter: brightness(1.12);
}

.pulse-latest {
  animation: pulseBarIn 420ms cubic-bezier(.16, 1, .3, 1) both, pulseLatest 1.8s ease-in-out infinite;
  animation-delay: var(--pd, 0ms), calc(var(--pd, 0ms) + 420ms);
}

.rc-enter-active,
.rc-appear-active {
  transition: opacity 420ms ease, transform 420ms cubic-bezier(.16, 1, .3, 1);
  transition-delay: var(--d, 0ms);
}

.rc-enter-from,
.rc-appear-from {
  opacity: 0;
  transform: translateY(10px);
}

.rc-leave-active {
  position: absolute;
  opacity: 0;
  transition: opacity 150ms ease;
}

.rc-move {
  transition: transform 420ms cubic-bezier(.16, 1, .3, 1);
}

@keyframes pulseBarIn {
  from { transform: scaleY(0); opacity: 0; }
  to { transform: scaleY(1); opacity: 1; }
}

@keyframes pulseLatest {
  0%, 100% { box-shadow: 0 0 0 0 var(--pulse-glow, rgba(16, 185, 129, .45)); }
  50% { box-shadow: 0 0 8px 2px var(--pulse-glow, rgba(16, 185, 129, .45)); }
}

@media (prefers-reduced-motion: reduce) {
  .rc-enter-active,
  .rc-appear-active,
  .rc-leave-active,
  .rc-move,
  .rc-duration {
    transition: none !important;
  }

  .pulse-bar,
  .pulse-latest {
    animation: none !important;
  }

  .animate-ping {
    animation: none !important;
  }
}
</style>
