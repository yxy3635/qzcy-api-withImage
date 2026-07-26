<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

export interface TrendChartRow {
  date: string
  requests: number
  promptTokens: number
  completionTokens: number
  cachedTokens: number
  cacheCreationTokens: number
  totalTokens: number
  cost: number
}

const props = defineProps<{
  rows: TrendChartRow[]
  loading?: boolean
}>()

const emit = defineEmits<{ (e: 'refresh'): void }>()

type SeriesKey = 'promptTokens' | 'completionTokens' | 'cachedTokens' | 'cacheCreationTokens'
type Channel = SeriesKey | 'requests' | 'cost'
type Mode = 'tokens' | 'requests' | 'cost'

const SERIES: { key: SeriesKey; label: string; color: string }[] = [
  { key: 'promptTokens', label: '输入', color: '#2563eb' },
  { key: 'completionTokens', label: '输出', color: '#10b981' },
  { key: 'cachedTokens', label: '缓存读', color: '#06b6d4' },
  { key: 'cacheCreationTokens', label: '缓存写', color: '#f59e0b' }
]

const CHANNELS: Channel[] = ['promptTokens', 'completionTokens', 'cachedTokens', 'cacheCreationTokens', 'requests', 'cost']

const MODES: { value: Mode; label: string }[] = [
  { value: 'tokens', label: 'Token' },
  { value: 'requests', label: '请求' },
  { value: 'cost', label: '消费' }
]

const mode = ref<Mode>('tokens')
const hiddenSeries = ref<Set<SeriesKey>>(new Set())
const activeIndex = ref<number | null>(null)
const plotEl = ref<HTMLElement | null>(null)

const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

const Y_TOP = 8
const Y_BOT = 96

function emptyMatrix(): Record<Channel, number[]> {
  return {
    promptTokens: [],
    completionTokens: [],
    cachedTokens: [],
    cacheCreationTokens: [],
    requests: [],
    cost: []
  }
}

const display = ref<Record<Channel, number[]>>(emptyMatrix())
const animMax = ref(1)

const targetMax = computed(() => {
  if (!props.rows.length) return 1
  if (mode.value === 'requests') return Math.max(1, ...props.rows.map((row) => Number(row.requests || 0)))
  if (mode.value === 'cost') return Math.max(1e-6, ...props.rows.map((row) => Number(row.cost || 0)))
  const visible = SERIES.filter((series) => !hiddenSeries.value.has(series.key))
  const pool = visible.length ? visible : SERIES
  return Math.max(1, ...props.rows.flatMap((row) => pool.map((series) => Number(row[series.key] || 0))))
})

let raf = 0
let animStart = 0
const DURATION = 620
let fromMatrix = emptyMatrix()
let toMatrix = emptyMatrix()
let fromMax = 1
let toMax = 1

function startTween() {
  toMatrix = emptyMatrix()
  for (const channel of CHANNELS) {
    toMatrix[channel] = props.rows.map((row) => Number(row[channel] || 0))
  }
  toMax = targetMax.value
  if (reduceMotion) {
    display.value = { ...toMatrix }
    animMax.value = toMax
    return
  }
  fromMatrix = emptyMatrix()
  for (const channel of CHANNELS) {
    fromMatrix[channel] = toMatrix[channel].map((_, index) => display.value[channel][index] ?? 0)
  }
  fromMax = animMax.value
  animStart = performance.now()
  window.cancelAnimationFrame(raf)
  raf = window.requestAnimationFrame(stepTween)
}

function stepTween(now: number) {
  const t = Math.min(1, (now - animStart) / DURATION)
  const eased = 1 - Math.pow(1 - t, 3)
  const next = emptyMatrix()
  for (const channel of CHANNELS) {
    next[channel] = toMatrix[channel].map((value, index) => {
      const from = fromMatrix[channel][index] ?? 0
      return from + (value - from) * eased
    })
  }
  display.value = next
  animMax.value = fromMax + (toMax - fromMax) * eased
  if (t < 1) raf = window.requestAnimationFrame(stepTween)
}

watch(() => props.rows, startTween, { immediate: true })
watch([mode, hiddenSeries], startTween)
onBeforeUnmount(() => window.cancelAnimationFrame(raf))

function pointX(index: number) {
  const count = Math.max(2, props.rows.length)
  return (index / (count - 1)) * 100
}

function pointY(value: number) {
  const max = Math.max(1e-9, animMax.value)
  const clamped = Math.max(0, Number(value || 0))
  return Y_BOT - (Math.min(1.02, clamped / max)) * (Y_BOT - Y_TOP)
}

function linePath(values: number[]) {
  if (!values.length) return ''
  const points = values.map((value, index) => ({ x: pointX(index), y: pointY(value) }))
  return points.reduce((path, point, index) => {
    if (index === 0) return `M ${point.x.toFixed(2)} ${point.y.toFixed(2)}`
    const prev = points[index - 1] || point
    const dx = (point.x - prev.x) / 2
    return `${path} C ${(prev.x + dx).toFixed(2)} ${prev.y.toFixed(2)}, ${(point.x - dx).toFixed(2)} ${point.y.toFixed(2)}, ${point.x.toFixed(2)} ${point.y.toFixed(2)}`
  }, '')
}

function areaPath(values: number[]) {
  const line = linePath(values)
  if (!line) return ''
  const lastX = pointX(values.length - 1).toFixed(2)
  const firstX = pointX(0).toFixed(2)
  return `${line} L ${lastX} ${Y_BOT} L ${firstX} ${Y_BOT} Z`
}

const tokenPaths = computed(() => SERIES.map((series) => ({
  ...series,
  hidden: hiddenSeries.value.has(series.key),
  line: linePath(display.value[series.key]),
  area: areaPath(display.value[series.key])
})))

const singleSeries = computed(() => mode.value === 'requests'
  ? { key: 'requests' as const, label: '请求数', color: '#8b5cf6' }
  : { key: 'cost' as const, label: '消费', color: '#f43f5e' })

const singlePaths = computed(() => ({
  line: linePath(display.value[singleSeries.value.key]),
  area: areaPath(display.value[singleSeries.value.key])
}))

const hasData = computed(() => props.rows.some((row) =>
  Number(row.totalTokens || 0) > 0 || Number(row.requests || 0) > 0 || Number(row.cost || 0) > 0))

const seriesTotals = computed(() => {
  const totals = new Map<SeriesKey, number>()
  for (const series of SERIES) {
    totals.set(series.key, props.rows.reduce((sum, row) => sum + Number(row[series.key] || 0), 0))
  }
  return totals
})

const singleTotal = computed(() => props.rows.reduce((sum, row) => sum + Number(row[singleSeries.value.key] || 0), 0))

const activeRow = computed(() => activeIndex.value === null ? null : props.rows[activeIndex.value] || null)

const activeDots = computed(() => {
  const index = activeIndex.value
  if (index === null) return []
  if (mode.value === 'tokens') {
    return SERIES
      .filter((series) => !hiddenSeries.value.has(series.key))
      .map((series) => ({
        color: series.color,
        left: pointX(index),
        top: pointY(display.value[series.key][index] ?? 0)
      }))
  }
  return [{
    color: singleSeries.value.color,
    left: pointX(index),
    top: pointY(display.value[singleSeries.value.key][index] ?? 0)
  }]
})

const gridLevels = [1, 2 / 3, 1 / 3, 0]

function compact(value: number) {
  if (value >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(2)}B`
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(2)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(2)}K`
  return value >= 100 ? String(Math.round(value)) : String(Math.round(value * 10) / 10)
}

function axisLabel(fraction: number) {
  const value = animMax.value * fraction
  if (mode.value === 'cost') return value >= 1 ? `$${value.toFixed(2)}` : `$${value.toFixed(4)}`
  return compact(value)
}

function money(value: number) {
  return `$${Number(value || 0).toFixed(6)}`
}

const todayKey = (() => {
  const date = new Date()
  const pad = (v: number) => String(v).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
})()

function toggleSeries(key: SeriesKey) {
  const next = new Set(hiddenSeries.value)
  if (next.has(key)) next.delete(key)
  else if (next.size < SERIES.length - 1) next.add(key)
  hiddenSeries.value = next
}

function onPlotMove(event: MouseEvent) {
  const el = plotEl.value
  if (!el || !props.rows.length) return
  const rect = el.getBoundingClientRect()
  if (rect.width <= 0) return
  const fraction = (event.clientX - rect.left) / rect.width
  const index = Math.round(fraction * (props.rows.length - 1))
  activeIndex.value = Math.min(props.rows.length - 1, Math.max(0, index))
}

function onPlotLeave() {
  activeIndex.value = null
}

const tooltipFlipped = computed(() => activeIndex.value !== null && activeIndex.value > (props.rows.length - 1) / 2)
</script>

<template>
  <div class="trend-card rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-lg font-black text-slate-950">使用趋势</h2>
        <p class="mt-1 text-xs font-bold text-slate-500">近 7 天调用走势 · 悬停查看单日明细</p>
      </div>
      <div class="flex items-center gap-2">
        <div class="inline-flex rounded-xl bg-slate-100 p-1" role="tablist" aria-label="切换趋势指标">
          <button
            v-for="item in MODES"
            :key="item.value"
            type="button"
            role="tab"
            :aria-selected="mode === item.value"
            class="h-8 rounded-lg px-3 text-xs font-black transition-all duration-200"
            :class="mode === item.value ? 'bg-white text-slate-950 shadow-sm' : 'text-slate-500 hover:text-slate-800'"
            @click="mode = item.value"
          >{{ item.label }}</button>
        </div>
        <button
          type="button"
          class="grid h-8 w-8 place-items-center rounded-xl border border-slate-200 text-slate-500 transition hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-700 disabled:opacity-50"
          :disabled="loading"
          aria-label="刷新趋势数据"
          title="刷新"
          @click="emit('refresh')"
        >
          <svg viewBox="0 0 24 24" class="h-4 w-4" :class="loading ? 'animate-spin' : ''" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6v5h-5M4 18v-5h5M18.5 9A7 7 0 0 0 6 6.5L4 11m16 2-2 4.5A7 7 0 0 1 5.5 15" /></svg>
        </button>
      </div>
    </div>

    <div class="mt-5 flex gap-3">
      <div class="flex w-10 shrink-0 flex-col justify-between pb-6 pt-1 text-right">
        <span v-for="level in gridLevels" :key="level" class="text-[10px] font-bold leading-none text-slate-400">{{ axisLabel(level) }}</span>
      </div>

      <div class="min-w-0 flex-1">
        <div
          ref="plotEl"
          class="trend-plot relative h-64 cursor-crosshair sm:h-72"
          @mousemove="onPlotMove"
          @mouseleave="onPlotLeave"
        >
          <div class="pointer-events-none absolute inset-0">
            <div
              v-for="level in gridLevels"
              :key="level"
              class="absolute inset-x-0 border-t border-dashed border-slate-200/90"
              :style="{ top: `${Y_TOP + (1 - level) * (Y_BOT - Y_TOP)}%` }"
            ></div>
          </div>

          <Transition name="trend-swap" mode="out-in">
            <svg :key="mode" class="absolute inset-0 h-full w-full overflow-visible" viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label="近 7 天使用趋势曲线">
              <defs>
                <linearGradient v-for="series in SERIES" :id="`trend-grad-${series.key}`" :key="series.key" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" :stop-color="series.color" stop-opacity="0.22" />
                  <stop offset="100%" :stop-color="series.color" stop-opacity="0" />
                </linearGradient>
                <linearGradient id="trend-grad-single" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" :stop-color="singleSeries.color" stop-opacity="0.26" />
                  <stop offset="100%" :stop-color="singleSeries.color" stop-opacity="0" />
                </linearGradient>
              </defs>

              <template v-if="mode === 'tokens'">
                <g v-for="series in tokenPaths" :key="series.key" class="trend-series" :class="{ 'is-hidden': series.hidden }">
                  <path :d="series.area" :fill="`url(#trend-grad-${series.key})`" />
                  <path
                    :d="series.line"
                    fill="none"
                    :stroke="series.color"
                    stroke-width="2.6"
                    stroke-linecap="round"
                    vector-effect="non-scaling-stroke"
                    :style="{ filter: `drop-shadow(0 3px 5px ${series.color}44)` }"
                  />
                </g>
              </template>
              <template v-else>
                <g class="trend-series">
                  <path :d="singlePaths.area" fill="url(#trend-grad-single)" />
                  <path
                    :d="singlePaths.line"
                    fill="none"
                    :stroke="singleSeries.color"
                    stroke-width="2.8"
                    stroke-linecap="round"
                    vector-effect="non-scaling-stroke"
                    :style="{ filter: `drop-shadow(0 3px 5px ${singleSeries.color}44)` }"
                  />
                </g>
              </template>
            </svg>
          </Transition>

          <div
            v-if="activeIndex !== null"
            class="trend-crosshair pointer-events-none absolute w-px bg-slate-300"
            :style="{ left: `${pointX(activeIndex)}%`, top: `${Y_TOP}%`, height: `${Y_BOT - Y_TOP}%` }"
          ></div>

          <span
            v-for="(dot, index) in activeDots"
            :key="index"
            class="trend-dot pointer-events-none absolute h-2.5 w-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full ring-4 ring-white"
            :style="{ left: `${dot.left}%`, top: `${dot.top}%`, backgroundColor: dot.color, boxShadow: `0 0 10px ${dot.color}88` }"
          ></span>

          <div
            v-if="activeRow"
            class="trend-tooltip pointer-events-none absolute top-2 z-10 w-52 rounded-2xl border border-slate-100 bg-white/95 p-3.5 text-xs font-bold text-slate-600 shadow-2xl shadow-slate-300/60 backdrop-blur"
            :style="{
              left: `${pointX(activeIndex || 0)}%`,
              transform: tooltipFlipped ? 'translateX(calc(-100% - 14px))' : 'translateX(14px)'
            }"
          >
            <div class="flex items-center justify-between gap-2">
              <p class="text-sm font-black text-slate-950">{{ activeRow.date.slice(5) }}</p>
              <span v-if="activeRow.date === todayKey" class="rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] font-black text-emerald-700">今天</span>
            </div>
            <div class="mt-2.5 space-y-1.5">
              <p v-for="series in SERIES" :key="series.key" class="flex items-center justify-between gap-2">
                <span class="flex items-center gap-1.5"><i class="h-2 w-2 rounded-full" :style="{ backgroundColor: series.color }"></i>{{ series.label }}</span>
                <span class="tabular-nums text-slate-800">{{ compact(Number(activeRow[series.key] || 0)) }}</span>
              </p>
              <p class="flex items-center justify-between gap-2 border-t border-slate-100 pt-1.5 text-slate-950">
                <span>请求</span><span class="tabular-nums">{{ activeRow.requests }} 次</span>
              </p>
              <p class="flex items-center justify-between gap-2 text-slate-950">
                <span>消费</span><span class="tabular-nums">{{ money(activeRow.cost) }}</span>
              </p>
            </div>
          </div>

          <div v-if="!hasData" class="pointer-events-none absolute inset-0 grid place-items-center">
            <p class="rounded-full bg-slate-50 px-4 py-2 text-xs font-black text-slate-400">近 7 天暂无调用数据</p>
          </div>
        </div>

        <div class="mt-1 flex justify-between">
          <span
            v-for="(row, index) in rows"
            :key="row.date"
            class="text-[10px] font-bold"
            :class="[
              row.date === todayKey ? 'text-emerald-600' : 'text-slate-400',
              activeIndex === index ? 'text-slate-900' : ''
            ]"
          >{{ row.date.slice(5) }}</span>
        </div>
      </div>
    </div>

    <div class="mt-4 flex flex-wrap items-center gap-2">
      <template v-if="mode === 'tokens'">
        <button
          v-for="series in SERIES"
          :key="series.key"
          type="button"
          class="trend-legend inline-flex items-center gap-2 rounded-xl border px-3 py-1.5 text-xs font-black transition-all duration-200"
          :class="hiddenSeries.has(series.key)
            ? 'border-slate-100 bg-slate-50 text-slate-400'
            : 'border-slate-200 bg-white text-slate-700 hover:-translate-y-0.5 hover:shadow-md hover:shadow-slate-200/70'"
          :aria-pressed="!hiddenSeries.has(series.key)"
          @click="toggleSeries(series.key)"
        >
          <i class="h-2.5 w-2.5 rounded-full transition" :style="{ backgroundColor: hiddenSeries.has(series.key) ? '#cbd5e1' : series.color }"></i>
          {{ series.label }}
          <span class="tabular-nums font-bold text-slate-400">{{ compact(seriesTotals.get(series.key) || 0) }}</span>
        </button>
      </template>
      <span v-else class="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-black text-slate-700">
        <i class="h-2.5 w-2.5 rounded-full" :style="{ backgroundColor: singleSeries.color }"></i>
        7 天{{ singleSeries.label }}合计
        <span class="tabular-nums text-slate-500">{{ mode === 'cost' ? money(singleTotal) : `${compact(singleTotal)} 次` }}</span>
      </span>
    </div>
  </div>
</template>

<style scoped>
.trend-series {
  transition: opacity 300ms ease;
}

.trend-series.is-hidden {
  opacity: 0;
}

.trend-crosshair {
  transition: left 130ms cubic-bezier(.2, .8, .2, 1);
}

.trend-dot {
  transition: left 130ms cubic-bezier(.2, .8, .2, 1), top 130ms cubic-bezier(.2, .8, .2, 1);
  animation: trendDotIn 220ms cubic-bezier(.16, 1, .3, 1);
}

.trend-tooltip {
  transition: left 130ms cubic-bezier(.2, .8, .2, 1);
  animation: trendTooltipIn 200ms cubic-bezier(.16, 1, .3, 1);
}

.trend-swap-enter-active,
.trend-swap-leave-active {
  transition: opacity 180ms ease, transform 220ms cubic-bezier(.16, 1, .3, 1);
}

.trend-swap-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.trend-swap-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@keyframes trendDotIn {
  from { scale: 0.4; opacity: 0; }
  to { scale: 1; opacity: 1; }
}

@keyframes trendTooltipIn {
  from { opacity: 0; translate: 0 5px; }
  to { opacity: 1; translate: 0 0; }
}

@media (prefers-reduced-motion: reduce) {
  .trend-series,
  .trend-crosshair,
  .trend-dot,
  .trend-tooltip,
  .trend-swap-enter-active,
  .trend-swap-leave-active {
    transition: none !important;
    animation: none !important;
  }
}
</style>
