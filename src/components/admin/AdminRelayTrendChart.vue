<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { useElementSize } from '@vueuse/core'
import type { AdminRelayDailyTrendPoint } from '@/types'

type MetricKey = 'requests' | 'tokens' | 'cost' | 'profit'
type ChartPoint = { date: string; value: number; x: number; y: number }

const props = withDefaults(defineProps<{
  rows: AdminRelayDailyTrendPoint[]
  days?: number
  loading?: boolean
}>(), {
  days: 30,
  loading: false
})

const metrics: Array<{ key: MetricKey; label: string; unit: string; money?: boolean; color: string; fill: string }> = [
  { key: 'requests', label: '调用量', unit: '次', color: '#0284c7', fill: '#7dd3fc' },
  { key: 'tokens', label: 'Token', unit: 'Token', color: '#7c3aed', fill: '#c4b5fd' },
  { key: 'cost', label: '调用收入', unit: '¥', money: true, color: '#047857', fill: '#6ee7b7' },
  { key: 'profit', label: '利润', unit: '¥', money: true, color: '#b45309', fill: '#fcd34d' }
]
const metric = ref<MetricKey>('requests')
const activeMetric = computed(() => metrics.find((item) => item.key === metric.value) ?? metrics[0]!)

const chartStage = ref<HTMLElement | null>(null)
const hoveredIndex = ref<number | null>(null)
const pinnedIndex = ref<number | null>(null)
const { width: measuredWidth } = useElementSize(chartStage, { width: 960, height: 260 })

const svgWidth = computed(() => Math.max(320, Math.round(measuredWidth.value || 960)))
const svgHeight = 260
const padding = { top: 18, right: 20, bottom: 38, left: 46 }
const plotWidth = computed(() => Math.max(1, svgWidth.value - padding.left - padding.right))
const plotHeight = svgHeight - padding.top - padding.bottom
const id = useId().replace(/:/g, '')
const gradientId = `relay-trend-gradient-${id}`
const glowId = `relay-trend-glow-${id}`

const sourceRows = computed(() => props.rows
  .map((row) => ({
    date: String(row.date || '').slice(0, 10),
    requests: Math.max(0, Number(row.requests || 0)),
    tokens: Math.max(0, Number(row.tokens || 0)),
    cost: Math.max(0, Number(row.cost || 0)),
    profit: Number(row.profit || 0)
  }))
  .filter((row) => /^\d{4}-\d{2}-\d{2}$/.test(row.date)))

const rows = computed(() => {
  if (!sourceRows.value.length) return []

  const merged = new Map<string, AdminRelayDailyTrendPoint>()
  sourceRows.value.forEach((row) => merged.set(row.date, row))

  const today = new Date()
  today.setHours(12, 0, 0, 0)

  return Array.from({ length: props.days }, (_, index) => {
    const date = new Date(today)
    date.setDate(today.getDate() - (props.days - 1 - index))
    const key = dateKey(date)
    return merged.get(key) || { date: key, requests: 0, tokens: 0, cost: 0, profit: 0 }
  })
})

const values = computed(() => rows.value.map((row) => Number(row[metric.value] ?? 0)))
const total = computed(() => values.value.reduce((sum, value) => sum + value, 0))
const average = computed(() => values.value.length ? total.value / values.value.length : 0)
const peakIndex = computed(() => values.value.reduce((peak, value, index) => (value > (values.value[peak] ?? 0) ? index : peak), 0))
const dailyDelta = computed(() => {
  const latest = values.value[values.value.length - 1] || 0
  const previous = values.value[values.value.length - 2] || 0
  return latest - previous
})

const scaleMax = computed(() => niceCeiling(Math.max(0, ...values.value)))
const scaleMin = computed(() => metric.value === 'profit' ? Math.min(0, niceFloor(Math.min(0, ...values.value))) : 0)
const zeroY = computed(() => padding.top + (1 - (0 - scaleMin.value) / Math.max(1e-9, scaleMax.value - scaleMin.value)) * plotHeight)
const points = computed<ChartPoint[]>(() => rows.value.map((row, index) => {
  const span = Math.max(1e-9, scaleMax.value - scaleMin.value)
  const x = padding.left + (index / Math.max(1, rows.value.length - 1)) * plotWidth.value
  const y = padding.top + (1 - ((values.value[index] ?? 0) - scaleMin.value) / span) * plotHeight
  return { date: row.date, value: values.value[index] ?? 0, x, y }
}))

const linePath = computed(() => points.value.reduce((path, point, index) => {
  if (index === 0) return `M ${point.x.toFixed(2)} ${point.y.toFixed(2)}`
  const previous = points.value[index - 1] || point
  const midX = (previous.x + point.x) / 2
  return `${path} C ${midX.toFixed(2)} ${previous.y.toFixed(2)}, ${midX.toFixed(2)} ${point.y.toFixed(2)}, ${point.x.toFixed(2)} ${point.y.toFixed(2)}`
}, ''))

const areaPath = computed(() => {
  const first = points.value[0]
  const last = points.value[points.value.length - 1]
  if (!first || !last) return ''
  return `${linePath.value} L ${last.x.toFixed(2)} ${zeroY.value.toFixed(2)} L ${first.x.toFixed(2)} ${zeroY.value.toFixed(2)} Z`
})

const activeIndex = computed(() => {
  if (!points.value.length) return null
  return hoveredIndex.value ?? pinnedIndex.value ?? points.value.length - 1
})
const activePoint = computed(() => activeIndex.value === null ? null : points.value[activeIndex.value] || null)
const isExploring = computed(() => hoveredIndex.value !== null || pinnedIndex.value !== null)
const displayPoint = computed(() => activePoint.value || points.value[points.value.length - 1] || null)
const axisLabels = computed(() => points.value.filter((_, index) => index % 5 === 0 || index === points.value.length - 1))
const chartSignature = computed(() => `${metric.value}:${rows.value.map((row) => row.date).join('|')}`)
const gridTicks = computed(() => [
  { value: scaleMax.value },
  { value: scaleMin.value + (scaleMax.value - scaleMin.value) / 2 },
  { value: scaleMin.value }
])

watch([() => props.rows, metric], () => {
  hoveredIndex.value = null
  pinnedIndex.value = null
})

function dateKey(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function niceCeiling(value: number) {
  if (value <= 0) return 1
  return niceBound(value, 1.08)
}

function niceFloor(value: number) {
  if (value >= 0) return 0
  return -niceBound(-value, 1.08)
}

function niceBound(value: number, targetRatio: number) {
  const magnitude = 10 ** Math.floor(Math.log10(value))
  const normalized = value / magnitude
  const target = normalized * targetRatio
  const factors = [1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10]
  const factor = factors.find((candidate) => candidate >= target) || 10
  return factor * magnitude
}

function nearestIndex(event: PointerEvent) {
  if (!points.value.length) return null
  const svg = event.currentTarget as SVGSVGElement
  const bounds = svg.getBoundingClientRect()
  const localX = ((event.clientX - bounds.left) / Math.max(1, bounds.width)) * svgWidth.value
  const ratio = Math.min(1, Math.max(0, (localX - padding.left) / plotWidth.value))
  return Math.round(ratio * Math.max(0, points.value.length - 1))
}

function handlePointerMove(event: PointerEvent) {
  if (event.pointerType === 'touch') return
  const index = nearestIndex(event)
  if (index !== null && hoveredIndex.value !== index) hoveredIndex.value = index
}

function handlePointerDown(event: PointerEvent) {
  const index = nearestIndex(event)
  if (index === null) return
  togglePinned(index)
}

function togglePinned(index: number) {
  pinnedIndex.value = pinnedIndex.value === index ? null : index
  hoveredIndex.value = index
}

function formatValue(value: number, money = activeMetric.value.money) {
  if (money) return `¥ ${value.toFixed(2)}`
  if (value >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(2)}B`
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(2)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`
  return Math.round(value).toLocaleString()
}

function formatScale(value: number) {
  if (Math.abs(value) >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (Math.abs(value) >= 1_000) return `${(value / 1_000).toFixed(0)}K`
  return Math.abs(value) < 10 && !Number.isInteger(value) ? value.toFixed(1) : String(Math.round(value))
}

function formatDate(value: string) {
  const [, month = '', day = ''] = value.split('-')
  return `${Number(month)}/${Number(day)}`
}

function formatLongDate(value?: string) {
  if (!value) return '暂无日期'
  const [, month = '', day = ''] = value.split('-')
  return `${Number(month)} 月 ${Number(day)} 日`
}
</script>

<template>
  <article
    class="relay-trend-card"
    :style="{ '--trend-color': activeMetric.color, '--trend-fill': activeMetric.fill }"
    :aria-busy="loading"
  >
    <header class="relay-trend-header">
      <div class="relay-trend-title">
        <span class="trend-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24"><path d="M3 12h4l2.5-6 4.5 12 2.5-6H21" /></svg>
        </span>
        <div>
          <h3>中转调用趋势</h3>
          <p>最近 {{ days }} 个自然日的调用量、收入与利润变化</p>
        </div>
      </div>
      <div class="metric-tabs" role="tablist" aria-label="切换趋势指标">
        <button
          v-for="item in metrics"
          :key="item.key"
          type="button"
          role="tab"
          :aria-selected="metric === item.key"
          :class="{ active: metric === item.key }"
          :style="{ '--tab-color': item.color }"
          @click="metric = item.key"
        >{{ item.label }}</button>
      </div>
    </header>

    <div v-if="rows.length" class="relay-trend-stats" aria-label="趋势摘要">
      <div><span>{{ days }} 日累计</span><strong>{{ formatValue(total) }}</strong></div>
      <div><span>日均</span><strong>{{ formatValue(average) }}</strong></div>
      <div><span>峰值</span><strong>{{ formatValue(values[peakIndex] || 0) }}</strong><small>{{ formatDate(rows[peakIndex]?.date || '') }}</small></div>
      <div class="delta-stat" :class="dailyDelta > 0 ? 'is-up' : dailyDelta < 0 ? 'is-down' : 'is-flat'">
        <span>较前一日</span>
        <strong>{{ dailyDelta > 0 ? '+' : '' }}{{ formatValue(dailyDelta) }}</strong>
      </div>
      <div v-if="displayPoint" class="current-stat">
        <span>{{ formatLongDate(displayPoint.date) }}</span>
        <strong>{{ formatValue(displayPoint.value) }}</strong>
      </div>
    </div>

    <div v-if="loading && !rows.length" class="trend-skeleton" aria-label="趋势数据加载中">
      <span v-for="index in 4" :key="index"></span>
      <i></i>
    </div>

    <div v-else-if="rows.length" ref="chartStage" class="chart-stage">
      <svg
        class="relay-trend-svg"
        :viewBox="`0 0 ${svgWidth} ${svgHeight}`"
        role="img"
        :aria-label="`${activeMetric.label}最近 ${days} 日趋势图`"
        @pointermove="handlePointerMove"
        @pointerleave="hoveredIndex = null"
        @pointerdown="handlePointerDown"
      >
        <defs>
          <linearGradient :id="gradientId" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" :stop-color="activeMetric.fill" stop-opacity=".34" />
            <stop offset="72%" :stop-color="activeMetric.fill" stop-opacity=".08" />
            <stop offset="100%" :stop-color="activeMetric.fill" stop-opacity="0" />
          </linearGradient>
          <filter :id="glowId" x="-20%" y="-30%" width="140%" height="160%">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
          </filter>
        </defs>

        <g class="grid-layer" aria-hidden="true">
          <g v-for="tick in gridTicks" :key="tick.value">
            <line :x1="padding.left" :x2="svgWidth - padding.right" :y1="padding.top + (1 - (tick.value - scaleMin) / Math.max(1e-9, scaleMax - scaleMin)) * plotHeight" :y2="padding.top + (1 - (tick.value - scaleMin) / Math.max(1e-9, scaleMax - scaleMin)) * plotHeight" />
            <text x="4" :y="padding.top + (1 - (tick.value - scaleMin) / Math.max(1e-9, scaleMax - scaleMin)) * plotHeight + 3">{{ formatScale(tick.value) }}</text>
          </g>
        </g>

        <g :key="chartSignature" class="series-layer">
          <path class="trend-area" :d="areaPath" :fill="`url(#${gradientId})`" />
          <path class="trend-glow" :d="linePath" :filter="`url(#${glowId})`" pathLength="1" />
          <path class="trend-line" :d="linePath" pathLength="1" />
          <g
            v-for="(point, index) in points"
            :key="point.date"
            class="trend-point"
            :class="{ active: activeIndex === index, pinned: pinnedIndex === index }"
            role="button"
            :tabindex="0"
            :aria-label="`${formatLongDate(point.date)}，${activeMetric.label} ${formatValue(point.value)}`"
            :aria-pressed="pinnedIndex === index"
            @focus="hoveredIndex = index"
            @blur="hoveredIndex = null"
            @keydown.enter.prevent="togglePinned(index)"
            @keydown.space.prevent="togglePinned(index)"
          >
            <circle :cx="point.x" :cy="point.y" r="8" class="point-hit" />
            <circle :cx="point.x" :cy="point.y" r="3.2" class="point-dot" />
          </g>
        </g>

        <g v-if="isExploring && activePoint" class="active-guide" aria-hidden="true">
          <line :x1="activePoint.x" :x2="activePoint.x" :y1="padding.top" :y2="svgHeight - padding.bottom" />
          <circle :cx="activePoint.x" :cy="activePoint.y" r="8" />
          <circle class="guide-core" :cx="activePoint.x" :cy="activePoint.y" r="4.2" />
        </g>

        <g class="axis-labels" aria-hidden="true">
          <text
            v-for="point in axisLabels"
            :key="`${point.date}-axis`"
            :x="point.x"
            :y="svgHeight - 10"
            text-anchor="middle"
          >{{ formatDate(point.date) }}</text>
        </g>
      </svg>

      <div
        v-if="isExploring && activePoint"
        class="chart-tooltip"
        :class="activeIndex !== null && activeIndex >= points.length - 3 ? 'align-right' : 'align-left'"
        :style="{ left: `${activePoint.x}px`, top: `${activePoint.y}px` }"
        aria-live="polite"
      >
        <span>{{ formatLongDate(activePoint.date) }}</span>
        <strong>{{ formatValue(activePoint.value) }}</strong>
      </div>
    </div>

    <div v-else class="trend-empty">
      <span aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M4 18 9 12l4 3 7-9M4 21h16" /></svg></span>
      <strong>最近 {{ days }} 日暂无调用数据</strong>
      <small>统计周期内没有中转调用记录</small>
    </div>
  </article>
</template>

<style scoped>
.relay-trend-card { overflow: hidden; border: 1px solid rgba(255,255,255,.76); border-radius: 8px; background: linear-gradient(145deg, rgba(255,255,255,.76), rgba(245,250,252,.48)); box-shadow: 0 20px 46px rgba(30,73,94,.13), inset 0 1px 0 rgba(255,255,255,.92); backdrop-filter: blur(24px) saturate(145%); transition: border-color .2s ease, box-shadow .2s ease; }.relay-trend-card:hover { border-color: color-mix(in srgb, var(--trend-color) 26%, white); box-shadow: 0 24px 52px rgba(30,73,94,.16), inset 0 1px 0 rgba(255,255,255,.94); }.relay-trend-header { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: .8rem; padding: 1rem 1.1rem .9rem; border-bottom: 1px solid rgba(255,255,255,.62); }.relay-trend-title { display: flex; align-items: center; gap: .72rem; }.trend-icon { display: grid; width: 2.45rem; height: 2.45rem; place-items: center; border: 1px solid color-mix(in srgb, var(--trend-color) 18%, white); border-radius: 8px; background: color-mix(in srgb, var(--trend-fill) 26%, rgba(255,255,255,.72)); color: var(--trend-color); box-shadow: inset 0 1px 0 rgba(255,255,255,.82); }.trend-icon svg { width: 1.18rem; height: 1.18rem; fill: none; stroke: currentColor; stroke-width: 1.9; stroke-linecap: round; stroke-linejoin: round; }.relay-trend-title h3 { margin: 0; color: #172033; font-size: .96rem; font-weight: 850; }.relay-trend-title p { margin: .24rem 0 0; color: #68798c; font-size: .7rem; font-weight: 650; }.metric-tabs { display: flex; gap: .25rem; padding: .22rem; border: 1px solid rgba(15,23,42,.10); border-radius: .55rem; background: rgba(241,245,249,.8); }.metric-tabs button { height: 1.9rem; border: 0; border-radius: .4rem; background: transparent; color: #64748b; padding: 0 .75rem; font-size: .7rem; font-weight: 800; cursor: pointer; transition: .18s ease; }.metric-tabs button:hover { color: var(--tab-color); }.metric-tabs button.active { background: rgba(255,255,255,.95); color: var(--tab-color); box-shadow: 0 3px 9px rgba(15,23,42,.14); }
.relay-trend-stats { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); margin: 0 1.1rem; border-bottom: 1px solid rgba(255,255,255,.58); }.relay-trend-stats > div { min-width: 0; padding: .72rem .72rem .7rem 0; }.relay-trend-stats > div + div { border-left: 1px solid rgba(148,163,184,.13); padding-left: .72rem; }.relay-trend-stats span, .relay-trend-stats small { display: block; overflow: hidden; color: #7a8999; font-size: .62rem; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }.relay-trend-stats strong { display: inline-block; margin-top: .18rem; color: #26364a; font-size: .88rem; font-weight: 850; font-variant-numeric: tabular-nums; }.relay-trend-stats small { display: inline; margin-left: .25rem; color: #91a0af; font-size: .58rem; }.delta-stat.is-up strong { color: #047857; }.delta-stat.is-down strong { color: #be123c; }.delta-stat.is-flat strong { color: #64748b; }.current-stat strong { color: var(--trend-color); }
.chart-stage { position: relative; min-width: 0; padding: .15rem .7rem .35rem; }.relay-trend-svg { display: block; width: 100%; height: 260px; overflow: visible; cursor: crosshair; touch-action: pan-y; }.grid-layer line { stroke: rgba(100,116,139,.14); stroke-dasharray: 3 6; vector-effect: non-scaling-stroke; }.grid-layer text { fill: #8795a5; font-size: 9px; font-weight: 700; }.trend-area { opacity: 0; transform-origin: center bottom; animation: area-enter .55s .12s cubic-bezier(.16,1,.3,1) forwards; }.trend-glow, .trend-line { fill: none; stroke: var(--trend-color); stroke-linecap: round; stroke-linejoin: round; stroke-dasharray: 1; stroke-dashoffset: 1; vector-effect: non-scaling-stroke; animation: line-draw .62s cubic-bezier(.16,1,.3,1) forwards; }.trend-glow { stroke-width: 7; opacity: .12; }.trend-line { stroke-width: 2.6; }
.trend-point { outline: none; cursor: pointer; }.point-hit { fill: transparent; }.point-dot { fill: #fff; stroke: var(--trend-color); stroke-width: 2; opacity: 0; transform-box: fill-box; transform-origin: center; animation: point-enter .28s cubic-bezier(.16,1,.3,1) forwards; vector-effect: non-scaling-stroke; }.trend-point.active .point-dot, .trend-point:focus-visible .point-dot { fill: var(--trend-color); }.active-guide { pointer-events: none; }.active-guide line { stroke: color-mix(in srgb, var(--trend-color) 48%, transparent); stroke-width: 1; stroke-dasharray: 3 5; vector-effect: non-scaling-stroke; }.active-guide > circle { fill: color-mix(in srgb, var(--trend-fill) 22%, transparent); stroke: color-mix(in srgb, var(--trend-color) 26%, transparent); }.active-guide .guide-core { fill: #fff; stroke: var(--trend-color); stroke-width: 2.5; vector-effect: non-scaling-stroke; }
.axis-labels text { fill: #7b8a9b; font-size: 9.5px; font-weight: 700; }
.chart-tooltip { position: absolute; z-index: 3; min-width: 106px; border: 1px solid rgba(255,255,255,.16); border-radius: 7px; background: rgba(15,23,42,.91); padding: .48rem .58rem; color: #fff; pointer-events: none; box-shadow: 0 12px 28px rgba(15,23,42,.24); backdrop-filter: blur(12px); transition: left .07s linear, top .07s linear; }.chart-tooltip.align-left { transform: translate(12px, calc(-100% - 10px)); }.chart-tooltip.align-right { transform: translate(calc(-100% - 12px), calc(-100% - 10px)); }.chart-tooltip span { display: block; color: #aebccb; font-size: .62rem; font-weight: 700; }.chart-tooltip strong { display: block; margin-top: .2rem; font-size: .9rem; font-weight: 850; font-variant-numeric: tabular-nums; }
.trend-empty, .trend-skeleton { min-height: 300px; }.trend-empty { display: grid; place-content: center; justify-items: center; color: #718196; text-align: center; }.trend-empty > span { display: grid; width: 2.8rem; height: 2.8rem; place-items: center; border: 1px solid rgba(255,255,255,.72); border-radius: 8px; background: rgba(255,255,255,.52); color: var(--trend-color); box-shadow: inset 0 1px 0 rgba(255,255,255,.82); }.trend-empty svg { width: 1.25rem; height: 1.25rem; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }.trend-empty strong { margin-top: .7rem; color: #52657a; font-size: .78rem; }.trend-empty small { margin-top: .28rem; color: #8a98a7; font-size: .66rem; font-weight: 650; }
.trend-skeleton { position: relative; display: flex; align-items: flex-end; gap: 3%; overflow: hidden; padding: 2.5rem 2rem 2.2rem; }.trend-skeleton span { flex: 1; height: 40%; border-radius: 5px 5px 0 0; background: rgba(148,163,184,.13); animation: skeleton-pulse 1s ease-in-out infinite alternate; }.trend-skeleton span:nth-child(2) { height: 62%; animation-delay: .1s; }.trend-skeleton span:nth-child(3) { height: 45%; animation-delay: .2s; }.trend-skeleton span:nth-child(4) { height: 74%; animation-delay: .3s; }.trend-skeleton i { position: absolute; inset: 50% 1.5rem auto; height: 2px; background: color-mix(in srgb, var(--trend-color) 38%, transparent); transform: rotate(-5deg); }
@keyframes line-draw { to { stroke-dashoffset: 0; } }
@keyframes area-enter { from { opacity: 0; transform: scaleY(.2); } to { opacity: 1; transform: scaleY(1); } }
@keyframes point-enter { from { opacity: 0; transform: scale(.3); } to { opacity: 1; transform: scale(1); } }
@keyframes skeleton-pulse { to { background: rgba(148,163,184,.25); } }
@media (max-width: 760px) { .relay-trend-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }.relay-trend-stats > div { border-left: 0 !important; padding-left: 0; }.relay-trend-stats > div:nth-child(even) { border-left: 1px solid rgba(148,163,184,.13) !important; padding-left: .72rem; }.relay-trend-svg { height: 220px; } }
@media (prefers-reduced-motion: reduce) { .trend-area, .trend-glow, .trend-line, .point-dot { animation: none; opacity: 1; stroke-dashoffset: 0; } }
</style>
