<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

export interface ModelDistributionRow {
  name: string
  requests: number
  tokens: number
  cost: number
  status?: string
}

const props = defineProps<{
  rows: ModelDistributionRow[]
}>()

type Metric = 'requests' | 'tokens' | 'cost'

const METRICS: { value: Metric; label: string }[] = [
  { value: 'requests', label: '请求' },
  { value: 'tokens', label: 'Token' },
  { value: 'cost', label: '消费' }
]

const PALETTE = ['#10b981', '#2563eb', '#06b6d4', '#f59e0b', '#8b5cf6', '#f43f5e', '#0ea5e9', '#84cc16']
const OTHER_COLOR = '#94a3b8'

const metric = ref<Metric>('requests')
const activeIndex = ref<number | null>(null)
const revealed = ref(false)

onMounted(() => {
  window.requestAnimationFrame(() => {
    window.requestAnimationFrame(() => {
      revealed.value = true
    })
  })
})

const items = computed(() => {
  const ranked = props.rows
    .map((row) => ({ ...row, value: Number(row[metric.value] || 0) }))
    .filter((row) => row.value > 0)
    .sort((a, b) => b.value - a.value)

  const top = ranked.slice(0, 7)
  const rest = ranked.slice(7)
  if (rest.length) {
    top.push({
      name: `其他 ${rest.length} 个模型`,
      requests: rest.reduce((sum, row) => sum + row.requests, 0),
      tokens: rest.reduce((sum, row) => sum + row.tokens, 0),
      cost: rest.reduce((sum, row) => sum + row.cost, 0),
      status: '',
      value: rest.reduce((sum, row) => sum + row.value, 0)
    })
  }

  const total = top.reduce((sum, row) => sum + row.value, 0) || 1
  let cursor = 0
  return top.map((row, index) => {
    const share = row.value / total
    const start = cursor
    cursor += share * 100
    return {
      ...row,
      share,
      start,
      length: share * 100,
      color: index < 7 && index < PALETTE.length && !row.name.startsWith('其他') ? PALETTE[index] : OTHER_COLOR
    }
  })
})

const total = computed(() => items.value.reduce((sum, row) => sum + row.value, 0))

const activeItem = computed(() => activeIndex.value === null ? null : items.value[activeIndex.value] || null)

function compact(value: number) {
  if (value >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(2)}B`
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(2)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(2)}K`
  return String(Math.round(value))
}

function formatValue(value: number) {
  if (metric.value === 'cost') return `$${value.toFixed(4)}`
  if (metric.value === 'requests') return `${compact(value)} 次`
  return compact(value)
}

const metricLabel = computed(() => METRICS.find((item) => item.value === metric.value)?.label || '')

function segmentDash(item: { length: number }) {
  if (!revealed.value) return '0 100'
  const gap = items.value.length > 1 ? 1 : 0
  const visible = Math.max(0.4, item.length - gap)
  return `${visible} ${100 - visible}`
}

function segmentOffset(item: { start: number }) {
  return 25 - item.start
}
</script>

<template>
  <div class="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-lg font-black text-slate-950">模型分布</h2>
        <p class="mt-1 text-xs font-bold text-slate-500">按真实调用日志聚合</p>
      </div>
      <div class="inline-flex rounded-xl bg-slate-100 p-1" role="tablist" aria-label="切换模型分布指标">
        <button
          v-for="item in METRICS"
          :key="item.value"
          type="button"
          role="tab"
          :aria-selected="metric === item.value"
          class="h-8 rounded-lg px-3 text-xs font-black transition-all duration-200"
          :class="metric === item.value ? 'bg-white text-slate-950 shadow-sm' : 'text-slate-500 hover:text-slate-800'"
          @click="metric = item.value"
        >{{ item.label }}</button>
      </div>
    </div>

    <div v-if="items.length" class="mt-5">
      <div class="flex flex-col items-center gap-6 sm:flex-row sm:items-start">
        <div class="relative h-44 w-44 shrink-0" @mouseleave="activeIndex = null">
          <svg viewBox="0 0 42 42" class="h-full w-full -rotate-0">
            <circle cx="21" cy="21" r="15.915" fill="none" stroke="#f1f5f9" stroke-width="5.5" />
            <circle
              v-for="(item, index) in items"
              :key="item.name"
              cx="21"
              cy="21"
              r="15.915"
              fill="none"
              class="donut-seg cursor-pointer"
              :class="{ 'is-dim': activeIndex !== null && activeIndex !== index }"
              :stroke="item.color"
              :stroke-width="activeIndex === index ? 7 : 5.5"
              :stroke-dasharray="segmentDash(item)"
              :stroke-dashoffset="segmentOffset(item)"
              @mouseenter="activeIndex = index"
            />
          </svg>
          <div class="pointer-events-none absolute inset-0 grid place-items-center">
            <Transition name="donut-center" mode="out-in">
              <div :key="activeIndex === null ? `total-${metric}` : `item-${activeIndex}-${metric}`" class="max-w-[104px] text-center">
                <template v-if="activeItem">
                  <p class="truncate text-[11px] font-black text-slate-500" :title="activeItem.name">{{ activeItem.name }}</p>
                  <p class="mt-0.5 text-xl font-black tabular-nums text-slate-950">{{ (activeItem.share * 100).toFixed(1) }}%</p>
                  <p class="mt-0.5 text-[11px] font-bold tabular-nums text-slate-500">{{ formatValue(activeItem.value) }}</p>
                </template>
                <template v-else>
                  <p class="text-[11px] font-black uppercase tracking-[0.14em] text-slate-400">总{{ metricLabel }}</p>
                  <p class="mt-0.5 text-xl font-black tabular-nums text-slate-950">{{ formatValue(total) }}</p>
                  <p class="mt-0.5 text-[11px] font-bold text-slate-400">{{ items.length }} 个模型</p>
                </template>
              </div>
            </Transition>
          </div>
        </div>

        <div class="min-w-0 flex-1 space-y-2.5 self-stretch" @mouseleave="activeIndex = null">
          <button
            v-for="(item, index) in items"
            :key="item.name"
            type="button"
            class="dist-row block w-full rounded-xl border p-2.5 text-left transition-all duration-200"
            :class="activeIndex === index
              ? 'border-slate-200 bg-slate-50 shadow-sm'
              : 'border-transparent hover:border-slate-100 hover:bg-slate-50/70'"
            :style="{ '--d': `${index * 55}ms` }"
            @mouseenter="activeIndex = index"
            @focus="activeIndex = index"
          >
            <div class="flex items-center justify-between gap-3 text-xs font-black">
              <span class="flex min-w-0 items-center gap-2 text-slate-900">
                <i class="h-2.5 w-2.5 shrink-0 rounded-full" :style="{ backgroundColor: item.color }"></i>
                <span class="truncate" :title="item.name">{{ item.name }}</span>
              </span>
              <span class="shrink-0 tabular-nums text-slate-600">{{ formatValue(item.value) }}</span>
            </div>
            <div class="mt-2 flex items-center gap-2.5">
              <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-100">
                <div
                  class="dist-bar h-full rounded-full"
                  :style="{
                    width: revealed ? `${Math.max(2, item.share * 100)}%` : '0%',
                    background: `linear-gradient(90deg, ${item.color}, ${item.color}99)`
                  }"
                ></div>
              </div>
              <span class="w-11 shrink-0 text-right text-[10px] font-black tabular-nums text-slate-400">{{ (item.share * 100).toFixed(1) }}%</span>
            </div>
          </button>
        </div>
      </div>
    </div>

    <div v-else class="mt-5 grid h-52 place-items-center rounded-xl border border-dashed border-slate-200">
      <div class="text-center">
        <div class="mx-auto grid h-11 w-11 place-items-center rounded-2xl bg-slate-100 text-slate-400">
          <svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3a9 9 0 1 0 9 9h-9V3z" /><path d="M15 3.5A9 9 0 0 1 20.5 9H15V3.5z" /></svg>
        </div>
        <p class="mt-3 text-sm font-black text-slate-500">暂无模型调用数据</p>
        <p class="mt-1 text-xs font-semibold text-slate-400">发起第一次 API 调用后这里会展示分布</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.donut-seg {
  transition:
    stroke-dasharray 700ms cubic-bezier(.16, 1, .3, 1),
    stroke-dashoffset 700ms cubic-bezier(.16, 1, .3, 1),
    stroke-width 220ms ease,
    opacity 220ms ease;
}

.donut-seg.is-dim {
  opacity: 0.32;
}

.dist-row {
  animation: distRowIn 420ms cubic-bezier(.16, 1, .3, 1) both;
  animation-delay: var(--d, 0ms);
}

.dist-bar {
  transition: width 750ms cubic-bezier(.16, 1, .3, 1);
  transition-delay: var(--d, 0ms);
}

.donut-center-enter-active,
.donut-center-leave-active {
  transition: opacity 140ms ease, transform 180ms cubic-bezier(.16, 1, .3, 1);
}

.donut-center-enter-from {
  opacity: 0;
  transform: translateY(4px) scale(.96);
}

.donut-center-leave-to {
  opacity: 0;
  transform: translateY(-3px) scale(.97);
}

@keyframes distRowIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (prefers-reduced-motion: reduce) {
  .donut-seg,
  .dist-bar,
  .donut-center-enter-active,
  .donut-center-leave-active {
    transition: none !important;
  }

  .dist-row {
    animation: none !important;
  }
}
</style>
