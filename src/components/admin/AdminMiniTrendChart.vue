<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { useElementSize } from '@vueuse/core'

type TrendPoint = { date: string; count: number }
type ChartPoint = TrendPoint & { x: number; y: number }

const props = withDefaults(defineProps<{
  rows: TrendPoint[]
  title: string
  description: string
  label: string
  unit?: string
  icon?: 'users' | 'image'
  color?: string
  fillColor?: string
  loading?: boolean
}>(), {
  unit: '次',
  icon: 'image',
  color: '#0ea5e9',
  fillColor: '#7dd3fc',
  loading: false
})

const chartStage = ref<HTMLElement | null>(null)
const hoveredIndex = ref<number | null>(null)
const pinnedIndex = ref<number | null>(null)
const { width: measuredWidth } = useElementSize(chartStage, { width: 640, height: 244 })

const svgWidth = computed(() => Math.max(300, Math.round(measuredWidth.value || 640)))
const svgHeight = 244
const padding = { top: 18, right: 18, bottom: 38, left: 38 }
const plotWidth = computed(() => Math.max(1, svgWidth.value - padding.left - padding.right))
const plotHeight = svgHeight - padding.top - padding.bottom
const baselineY = svgHeight - padding.bottom
const id = useId().replace(/:/g, '')
const gradientId = `admin-trend-gradient-${id}`
const glowId = `admin-trend-glow-${id}`

const sourceRows = computed(() => props.rows
  .map((row) => ({
    date: String(row.date || '').slice(0, 10),
    count: Math.max(0, Number(row.count || 0))
  }))
  .filter((row) => /^\d{4}-\d{2}-\d{2}$/.test(row.date)))

const rows = computed(() => {
  if (!sourceRows.value.length) return []

  const counts = new Map<string, number>()
  sourceRows.value.forEach((row) => counts.set(row.date, (counts.get(row.date) || 0) + row.count))

  const today = new Date()
  today.setHours(12, 0, 0, 0)

  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(today)
    date.setDate(today.getDate() - (6 - index))
    const key = dateKey(date)
    return { date: key, count: counts.get(key) || 0 }
  })
})

const total = computed(() => rows.value.reduce((sum, row) => sum + row.count, 0))
const average = computed(() => rows.value.length ? total.value / rows.value.length : 0)
const latestPoint = computed(() => rows.value[rows.value.length - 1] || null)
const previousPoint = computed(() => rows.value[rows.value.length - 2] || null)
const dailyDelta = computed(() => Number(latestPoint.value?.count || 0) - Number(previousPoint.value?.count || 0))
const peakPoint = computed(() => rows.value.reduce<TrendPoint | null>((peak, row) => {
  if (!peak || row.count > peak.count) return row
  return peak
}, null))

const scaleMax = computed(() => niceCeiling(Math.max(0, ...rows.value.map((row) => row.count))))
const points = computed<ChartPoint[]>(() => rows.value.map((row, index) => {
  const divisor = Math.max(1, rows.value.length - 1)
  const x = padding.left + (index / divisor) * plotWidth.value
  const y = padding.top + (1 - row.count / scaleMax.value) * plotHeight
  return { ...row, x, y }
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
  return `${linePath.value} L ${last.x.toFixed(2)} ${baselineY} L ${first.x.toFixed(2)} ${baselineY} Z`
})

const activeIndex = computed(() => {
  if (!points.value.length) return null
  return hoveredIndex.value ?? pinnedIndex.value ?? points.value.length - 1
})
const activePoint = computed(() => activeIndex.value === null ? null : points.value[activeIndex.value] || null)
const isExploring = computed(() => hoveredIndex.value !== null || pinnedIndex.value !== null)
const displayPoint = computed(() => activePoint.value || latestPoint.value)
const interactionBandWidth = computed(() => Math.max(30, plotWidth.value / Math.max(1, rows.value.length) * .72))
const chartSignature = computed(() => rows.value.map((row) => `${row.date}:${row.count}`).join('|'))
const gridTicks = computed(() => [
  { ratio: 1, value: scaleMax.value },
  { ratio: .5, value: scaleMax.value / 2 },
  { ratio: 0, value: 0 }
])

watch(() => props.rows, () => {
  hoveredIndex.value = null
  pinnedIndex.value = null
}, { deep: true })

function dateKey(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function niceCeiling(value: number) {
  if (value <= 0) return 1
  if (value < 10) return Math.ceil(value + 1)
  const magnitude = 10 ** Math.floor(Math.log10(value))
  const normalized = value / magnitude
  const target = normalized * 1.08
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
  if (index !== null) togglePinned(index)
}

function togglePinned(index: number) {
  pinnedIndex.value = pinnedIndex.value === index ? null : index
  hoveredIndex.value = index
}

function activate(index: number) {
  hoveredIndex.value = index
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

function formatScale(value: number) {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`
  return Number.isInteger(value) ? String(value) : value.toFixed(1)
}
</script>

<template>
  <article
    class="trend-card"
    :class="{ 'is-loading': loading && rows.length }"
    :style="{ '--trend-color': color, '--trend-fill': fillColor }"
    :aria-busy="loading"
  >
    <header class="trend-header">
      <div class="trend-identity">
        <span class="trend-icon" aria-hidden="true">
          <svg v-if="icon === 'users'" viewBox="0 0 24 24"><path d="M16 20v-1.5a4.5 4.5 0 0 0-4.5-4.5h-4A4.5 4.5 0 0 0 3 18.5V20m13-14a3.5 3.5 0 1 1 0 7m5 7v-1.5a4.5 4.5 0 0 0-3-4.24M12 6a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0Z" /></svg>
          <svg v-else viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2" /><circle cx="8" cy="9" r="1.5" /><path d="m21 15-4.5-4.5L7 20" /></svg>
        </span>
        <div>
          <h3>{{ title }}</h3>
          <p>{{ description }}</p>
        </div>
      </div>

      <div v-if="displayPoint" class="current-value" :key="`${displayPoint.date}-${displayPoint.count}`">
        <span>{{ formatLongDate(displayPoint.date) }}</span>
        <div><strong>{{ displayPoint.count.toLocaleString() }}</strong><small>{{ unit }}</small></div>
      </div>
    </header>

    <div v-if="rows.length" class="trend-stats" aria-label="趋势摘要">
      <div><span>7 日累计</span><strong>{{ total.toLocaleString() }}</strong></div>
      <div><span>日均</span><strong>{{ average.toFixed(1) }}</strong></div>
      <div><span>峰值</span><strong>{{ peakPoint?.count.toLocaleString() || '0' }}</strong><small>{{ formatDate(peakPoint?.date || '') }}</small></div>
      <div class="delta-stat" :class="dailyDelta > 0 ? 'is-up' : dailyDelta < 0 ? 'is-down' : 'is-flat'">
        <span>较前一日</span>
        <strong>{{ dailyDelta > 0 ? '+' : '' }}{{ dailyDelta.toLocaleString() }}</strong>
      </div>
    </div>

    <div v-if="loading && !rows.length" class="trend-skeleton" aria-label="趋势数据加载中">
      <span v-for="index in 4" :key="index"></span>
      <i></i>
    </div>

    <div v-else-if="rows.length" ref="chartStage" class="chart-stage">
      <svg
        class="trend-svg"
        :viewBox="`0 0 ${svgWidth} ${svgHeight}`"
        role="img"
        :aria-label="`${label}最近 7 日趋势图`"
        @pointermove="handlePointerMove"
        @pointerleave="hoveredIndex = null"
        @pointerdown="handlePointerDown"
      >
        <defs>
          <linearGradient :id="gradientId" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" :stop-color="fillColor" stop-opacity=".36" />
            <stop offset="72%" :stop-color="fillColor" stop-opacity=".09" />
            <stop offset="100%" :stop-color="fillColor" stop-opacity="0" />
          </linearGradient>
          <filter :id="glowId" x="-20%" y="-30%" width="140%" height="160%">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
          </filter>
        </defs>

        <g class="grid-layer" aria-hidden="true">
          <g v-for="tick in gridTicks" :key="tick.ratio">
            <line :x1="padding.left" :x2="svgWidth - padding.right" :y1="padding.top + (1 - tick.ratio) * plotHeight" :y2="padding.top + (1 - tick.ratio) * plotHeight" />
            <text x="2" :y="padding.top + (1 - tick.ratio) * plotHeight + 3">{{ formatScale(tick.value) }}</text>
          </g>
          <line v-for="point in points" :key="`${point.date}-grid`" class="vertical-grid" :x1="point.x" :x2="point.x" :y1="padding.top" :y2="baselineY" />
        </g>

        <rect
          v-if="isExploring && activePoint"
          class="active-band"
          :x="activePoint.x - interactionBandWidth / 2"
          :y="padding.top"
          :width="interactionBandWidth"
          :height="plotHeight"
          rx="7"
        />

        <g :key="chartSignature" class="series-layer">
          <path class="trend-area" :d="areaPath" :fill="`url(#${gradientId})`" />
          <path class="trend-glow" :d="linePath" :filter="`url(#${glowId})`" pathLength="1" />
          <path class="trend-line" :d="linePath" pathLength="1" />
          <g
            v-for="(point, index) in points"
            :key="point.date"
            class="trend-point"
            :class="{ active: activeIndex === index, pinned: pinnedIndex === index }"
            :style="{ '--point-delay': `${360 + index * 45}ms` }"
            role="button"
            :tabindex="0"
            :aria-label="`${formatLongDate(point.date)}，${label} ${point.count} ${unit}`"
            :aria-pressed="pinnedIndex === index"
            @focus="activate(index)"
            @blur="hoveredIndex = null"
            @keydown.enter.prevent="togglePinned(index)"
            @keydown.space.prevent="togglePinned(index)"
          >
            <circle :cx="point.x" :cy="point.y" r="13" class="point-hit" />
            <circle :cx="point.x" :cy="point.y" r="8" class="point-halo" />
            <circle :cx="point.x" :cy="point.y" r="4" class="point-dot" />
          </g>
        </g>

        <g v-if="isExploring && activePoint" class="active-guide" aria-hidden="true">
          <line :x1="activePoint.x" :x2="activePoint.x" :y1="padding.top" :y2="baselineY" />
          <circle :cx="activePoint.x" :cy="activePoint.y" r="10" />
          <circle class="guide-core" :cx="activePoint.x" :cy="activePoint.y" r="4.5" />
        </g>

        <g class="axis-labels" aria-hidden="true">
          <text
            v-for="(point, index) in points"
            :key="`${point.date}-axis`"
            :class="{ active: activeIndex === index && isExploring }"
            :x="point.x"
            :y="svgHeight - 10"
            text-anchor="middle"
          >{{ formatDate(point.date) }}</text>
        </g>
      </svg>

      <div
        v-if="isExploring && activePoint"
        class="chart-tooltip"
        :class="[
          activeIndex !== null && activeIndex >= points.length - 2 ? 'align-right' : 'align-left',
          activePoint.y < 76 ? 'place-below' : 'place-above'
        ]"
        :style="{ left: `${activePoint.x}px`, top: `${activePoint.y}px` }"
        aria-live="polite"
      >
        <span>{{ formatLongDate(activePoint.date) }}</span>
        <strong>{{ activePoint.count.toLocaleString() }} <small>{{ unit }}</small></strong>
      </div>
    </div>

    <div v-else class="trend-empty">
      <span aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M4 18 9 12l4 3 7-9M4 21h16" /></svg></span>
      <strong>最近 7 日暂无数据</strong>
      <small>统计周期内没有新增记录</small>
    </div>
  </article>
</template>

<style scoped>
.trend-card {
  position: relative;
  overflow: hidden;
  min-width: 0;
  border: 1px solid rgba(255, 255, 255, .76);
  border-radius: 8px;
  background: linear-gradient(145deg, rgba(255, 255, 255, .76), rgba(245, 250, 252, .48));
  box-shadow: 0 20px 46px rgba(30, 73, 94, .13), inset 0 1px 0 rgba(255, 255, 255, .92);
  backdrop-filter: blur(24px) saturate(145%);
  transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease;
}
.trend-card:hover { border-color: color-mix(in srgb, var(--trend-color) 26%, white); box-shadow: 0 24px 52px rgba(30, 73, 94, .16), inset 0 1px 0 rgba(255, 255, 255, .94); transform: translateY(-2px); }
.trend-card::before { position: absolute; inset: 0 auto 0 0; width: 3px; background: var(--trend-color); content: ''; opacity: .84; }
.trend-card.is-loading::after { position: absolute; top: 0; left: -35%; width: 35%; height: 2px; background: linear-gradient(90deg, transparent, var(--trend-color), transparent); content: ''; animation: loading-scan 1.1s ease-in-out infinite; }
.trend-header { display: flex; min-height: 84px; align-items: center; justify-content: space-between; gap: 1rem; padding: 1rem 1.1rem .9rem; border-bottom: 1px solid rgba(255, 255, 255, .62); }
.trend-identity { display: flex; min-width: 0; align-items: center; gap: .72rem; }
.trend-icon { display: grid; width: 2.45rem; height: 2.45rem; flex: 0 0 auto; place-items: center; border: 1px solid color-mix(in srgb, var(--trend-color) 18%, white); border-radius: 8px; background: color-mix(in srgb, var(--trend-fill) 26%, rgba(255,255,255,.72)); color: var(--trend-color); box-shadow: inset 0 1px 0 rgba(255,255,255,.82); }
.trend-icon svg { width: 1.18rem; height: 1.18rem; fill: none; stroke: currentColor; stroke-width: 1.9; stroke-linecap: round; stroke-linejoin: round; }
.trend-identity h3 { margin: 0; color: #172033; font-size: .96rem; font-weight: 850; letter-spacing: 0; }
.trend-identity p { margin: .24rem 0 0; color: #68798c; font-size: .7rem; font-weight: 650; }
.current-value { min-width: 7rem; text-align: right; animation: value-swap .18s ease both; }
.current-value > span { display: block; color: #7a8999; font-size: .64rem; font-weight: 700; }
.current-value > div { display: flex; justify-content: flex-end; align-items: baseline; gap: .28rem; margin-top: .16rem; }
.current-value strong { color: #172033; font-size: 1.55rem; font-weight: 900; line-height: 1; font-variant-numeric: tabular-nums; }
.current-value small { color: var(--trend-color); font-size: .66rem; font-style: normal; font-weight: 800; }
.trend-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 0 1.1rem; border-bottom: 1px solid rgba(255,255,255,.58); }
.trend-stats > div { min-width: 0; padding: .72rem .72rem .7rem 0; }
.trend-stats > div + div { border-left: 1px solid rgba(148, 163, 184, .13); padding-left: .72rem; }
.trend-stats span, .trend-stats small { display: block; overflow: hidden; color: #7a8999; font-size: .62rem; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.trend-stats strong { display: inline-block; margin-top: .18rem; color: #26364a; font-size: .88rem; font-weight: 850; font-variant-numeric: tabular-nums; }
.trend-stats small { display: inline; margin-left: .25rem; color: #91a0af; font-size: .58rem; }
.delta-stat.is-up strong { color: #047857; }.delta-stat.is-down strong { color: #be123c; }.delta-stat.is-flat strong { color: #64748b; }
.chart-stage { position: relative; min-width: 0; padding: .15rem .7rem .35rem; }
.trend-svg { display: block; width: 100%; height: 244px; overflow: visible; cursor: crosshair; touch-action: pan-y; }
.grid-layer line { stroke: rgba(100, 116, 139, .14); stroke-dasharray: 3 6; vector-effect: non-scaling-stroke; }
.grid-layer .vertical-grid { stroke: rgba(100, 116, 139, .07); stroke-dasharray: 2 8; }
.grid-layer text { fill: #8795a5; font-size: 9px; font-weight: 700; }
.active-band { fill: color-mix(in srgb, var(--trend-fill) 16%, transparent); pointer-events: none; }
.trend-area { opacity: 0; transform-origin: center bottom; animation: area-enter .55s .12s cubic-bezier(.16, 1, .3, 1) forwards; }
.trend-glow, .trend-line { fill: none; stroke: var(--trend-color); stroke-linecap: round; stroke-linejoin: round; stroke-dasharray: 1; stroke-dashoffset: 1; vector-effect: non-scaling-stroke; animation: line-draw .62s cubic-bezier(.16, 1, .3, 1) forwards; }
.trend-glow { stroke-width: 7; opacity: .12; }.trend-line { stroke-width: 2.8; }
.trend-point { outline: none; cursor: pointer; }
.point-hit { fill: transparent; pointer-events: none; }
.point-halo { fill: color-mix(in srgb, var(--trend-fill) 23%, transparent); opacity: 0; transition: opacity .1s ease, r .1s ease; }
.point-dot { fill: #fff; stroke: var(--trend-color); stroke-width: 2.2; opacity: 0; transform-box: fill-box; transform-origin: center; animation: point-enter .28s var(--point-delay) cubic-bezier(.16, 1, .3, 1) forwards; transition: fill .1s ease, r .1s ease; vector-effect: non-scaling-stroke; }
.trend-point.active .point-halo, .trend-point:focus-visible .point-halo { opacity: 1; }
.trend-point.active .point-dot, .trend-point:focus-visible .point-dot { r: 5.5; fill: var(--trend-color); }
.trend-point.pinned .point-halo { opacity: .8; r: 9; }
.trend-point:focus-visible .point-halo { stroke: #fff; stroke-width: 2; }
.active-guide { pointer-events: none; }.active-guide line { stroke: color-mix(in srgb, var(--trend-color) 48%, transparent); stroke-width: 1; stroke-dasharray: 3 5; vector-effect: non-scaling-stroke; }.active-guide > circle { fill: color-mix(in srgb, var(--trend-fill) 22%, transparent); stroke: color-mix(in srgb, var(--trend-color) 26%, transparent); }.active-guide .guide-core { fill: #fff; stroke: var(--trend-color); stroke-width: 2.5; vector-effect: non-scaling-stroke; }
.axis-labels text { fill: #7b8a9b; font-size: 9.5px; font-weight: 700; transition: fill .1s ease, font-weight .1s ease; }.axis-labels text.active { fill: var(--trend-color); font-weight: 900; }
.chart-tooltip { position: absolute; z-index: 3; min-width: 106px; border: 1px solid rgba(255,255,255,.16); border-radius: 7px; background: rgba(15, 23, 42, .91); padding: .48rem .58rem; color: #fff; pointer-events: none; box-shadow: 0 12px 28px rgba(15,23,42,.24); backdrop-filter: blur(12px); transition: left .07s linear, top .07s linear, opacity .1s ease; }
.chart-tooltip.align-left.place-above { transform: translate(12px, calc(-100% - 10px)); }.chart-tooltip.align-right.place-above { transform: translate(calc(-100% - 12px), calc(-100% - 10px)); }.chart-tooltip.align-left.place-below { transform: translate(12px, 12px); }.chart-tooltip.align-right.place-below { transform: translate(calc(-100% - 12px), 12px); }
.chart-tooltip span { display: block; color: #aebccb; font-size: .62rem; font-weight: 700; }.chart-tooltip strong { display: block; margin-top: .2rem; font-size: .9rem; font-weight: 850; font-variant-numeric: tabular-nums; }.chart-tooltip small { color: color-mix(in srgb, var(--trend-fill) 74%, white); font-size: .62rem; }
.trend-empty, .trend-skeleton { min-height: 286px; }
.trend-empty { display: grid; place-content: center; justify-items: center; color: #718196; text-align: center; }
.trend-empty > span { display: grid; width: 2.8rem; height: 2.8rem; place-items: center; border: 1px solid rgba(255,255,255,.72); border-radius: 8px; background: rgba(255,255,255,.52); color: var(--trend-color); box-shadow: inset 0 1px 0 rgba(255,255,255,.82); }
.trend-empty svg { width: 1.25rem; height: 1.25rem; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.trend-empty strong { margin-top: .7rem; color: #52657a; font-size: .78rem; }.trend-empty small { margin-top: .28rem; color: #8a98a7; font-size: .66rem; font-weight: 650; }
.trend-skeleton { position: relative; display: flex; align-items: flex-end; gap: 9%; overflow: hidden; padding: 2.5rem 2rem 2.2rem; }
.trend-skeleton span { flex: 1; height: 34%; border-radius: 5px 5px 0 0; background: rgba(148,163,184,.13); animation: skeleton-pulse 1s ease-in-out infinite alternate; }.trend-skeleton span:nth-child(2) { height: 58%; animation-delay: .1s; }.trend-skeleton span:nth-child(3) { height: 43%; animation-delay: .2s; }.trend-skeleton span:nth-child(4) { height: 72%; animation-delay: .3s; }.trend-skeleton i { position: absolute; inset: 50% 1.5rem auto; height: 2px; background: color-mix(in srgb, var(--trend-color) 38%, transparent); transform: rotate(-5deg); }
@keyframes line-draw { to { stroke-dashoffset: 0; } }
@keyframes area-enter { from { opacity: 0; transform: scaleY(.2); } to { opacity: 1; transform: scaleY(1); } }
@keyframes point-enter { from { opacity: 0; transform: scale(.3); } to { opacity: 1; transform: scale(1); } }
@keyframes value-swap { from { opacity: .45; transform: translateY(2px); } to { opacity: 1; transform: translateY(0); } }
@keyframes loading-scan { to { left: 100%; } }
@keyframes skeleton-pulse { to { background: rgba(148,163,184,.25); } }
@media (max-width: 560px) {
  .trend-header { align-items: flex-start; padding: .9rem; }
  .trend-identity { align-items: flex-start; }
  .current-value { min-width: 4.8rem; }
  .current-value strong { font-size: 1.25rem; }
  .trend-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 0 .9rem; }
  .trend-stats > div:nth-child(3) { border-left: 0; border-top: 1px solid rgba(148,163,184,.13); }
  .trend-stats > div:nth-child(4) { border-top: 1px solid rgba(148,163,184,.13); }
  .chart-stage { padding-inline: .25rem; }
  .trend-svg { height: 224px; }
}
@media (prefers-reduced-motion: reduce) {
  .trend-card, .trend-card:hover, .chart-tooltip, .point-dot, .point-halo { transition: none; transform: none; }
  .trend-area, .trend-glow, .trend-line, .point-dot, .trend-card.is-loading::after, .trend-skeleton span, .current-value { animation: none; opacity: 1; stroke-dashoffset: 0; }
}
</style>
