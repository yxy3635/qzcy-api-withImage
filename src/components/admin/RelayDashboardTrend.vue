<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { useElementSize } from '@vueuse/core'
import type { RelayDashboardTrendPoint } from '@/types'

const props = withDefaults(defineProps<{
  points: RelayDashboardTrendPoint[]
  loading?: boolean
}>(), {
  loading: false
})

const gradientId = `relay-traffic-${useId().replace(/:/g, '')}`
const chartStage = ref<HTMLElement | null>(null)
const hoveredIndex = ref<number | null>(null)
const { width: measuredWidth } = useElementSize(chartStage, { width: 720, height: 220 })

const svgWidth = computed(() => Math.max(320, Math.round(measuredWidth.value || 720)))
const svgHeight = 240
const padding = { top: 18, right: 12, bottom: 30, left: 42 }
const plotWidth = computed(() => Math.max(1, svgWidth.value - padding.left - padding.right))
const plotHeight = svgHeight - padding.top - padding.bottom
const baselineY = svgHeight - padding.bottom

const rows = computed(() => (props.points || []).map((point) => ({
  hour: String(point.hour || ''),
  label: String(point.hour || '').slice(11, 16),
  requests: Math.max(0, Number(point.requests || 0)),
  errors: Math.min(Math.max(0, Number(point.errors || 0)), Math.max(0, Number(point.requests || 0))),
  totalTokens: Math.max(0, Number(point.totalTokens || 0)),
  cost: Number(point.cost || 0)
})))

const scaleMax = computed(() => {
  const max = Math.max(0, ...rows.value.map((row) => row.requests))
  if (max <= 0) return 4
  const magnitude = 10 ** Math.floor(Math.log10(max))
  const normalized = (max / magnitude) * 1.12
  const factors = [1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10]
  const factor = factors.find((candidate) => candidate >= normalized) || 10
  return Math.max(1, factor * magnitude)
})

const barLayout = computed(() => {
  const count = Math.max(1, rows.value.length)
  const slot = plotWidth.value / count
  const barWidth = Math.max(3, slot * 0.62)
  return rows.value.map((row, index) => {
    const x = padding.left + slot * index + (slot - barWidth) / 2
    const totalHeight = (row.requests / scaleMax.value) * plotHeight
    const errorHeight = (row.errors / scaleMax.value) * plotHeight
    const successHeight = Math.max(0, totalHeight - errorHeight)
    return {
      ...row,
      index,
      slot,
      x,
      width: barWidth,
      successY: baselineY - successHeight,
      successHeight,
      errorY: baselineY - totalHeight,
      errorHeight,
      centerX: x + barWidth / 2,
      topY: baselineY - totalHeight
    }
  })
})

const gridTicks = computed(() => [
  { ratio: 1, value: scaleMax.value },
  { ratio: 0.75, value: scaleMax.value * 0.75 },
  { ratio: 0.5, value: scaleMax.value / 2 },
  { ratio: 0.25, value: scaleMax.value * 0.25 },
  { ratio: 0, value: 0 }
])

const xLabelIndexes = computed(() => barLayout.value
  .map((bar) => bar.index)
  .filter((index) => index % (svgWidth.value < 500 ? 6 : 4) === 0 || index === barLayout.value.length - 1))

const totals = computed(() => rows.value.reduce((acc, row) => ({
  requests: acc.requests + row.requests,
  errors: acc.errors + row.errors,
  cost: acc.cost + row.cost
}), { requests: 0, errors: 0, cost: 0 }))

const activeBar = computed(() => hoveredIndex.value === null ? null : barLayout.value[hoveredIndex.value] || null)

function formatScale(value: number) {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`
  return Number.isInteger(value) ? String(value) : value.toFixed(1)
}

function formatCost(value: number) {
  return `$${Number(value || 0).toFixed(2)}`
}

function handlePointerMove(event: PointerEvent) {
  if (!barLayout.value.length) return
  const svg = event.currentTarget as SVGSVGElement
  const matrix = svg.getScreenCTM()
  if (!matrix) return
  const point = new DOMPoint(event.clientX, event.clientY).matrixTransform(matrix.inverse())
  const localX = point.x
  if (localX < padding.left || localX > svgWidth.value - padding.right || point.y < padding.top || point.y > baselineY) {
    hoveredIndex.value = null
    return
  }
  const slot = plotWidth.value / Math.max(1, rows.value.length)
  const index = Math.floor((localX - padding.left) / slot)
  hoveredIndex.value = Math.min(Math.max(0, index), rows.value.length - 1)
}
</script>

<template>
  <div class="panel traffic-panel p-4 sm:p-5">
    <div class="mb-2 flex flex-wrap items-center justify-between gap-3">
      <div>
        <p class="text-sm font-black text-slate-800">24 小时流量</p>
        <p class="mt-0.5 text-xs font-semibold text-slate-500">
          共 {{ totals.requests.toLocaleString() }} 次 · 失败 {{ totals.errors.toLocaleString() }} · 消费 {{ formatCost(totals.cost) }}
        </p>
      </div>
      <div class="flex items-center gap-3 text-xs font-black text-slate-500">
        <span class="inline-flex items-center gap-1.5"><span class="h-2 w-2 rounded-sm bg-sky-400"></span>成功</span>
        <span class="inline-flex items-center gap-1.5"><span class="h-2 w-2 rounded-sm bg-red-400"></span>失败</span>
      </div>
    </div>
    <div ref="chartStage" class="relative min-w-0 pt-16">
      <div class="pointer-events-none absolute inset-x-0 top-0 flex h-14 items-center justify-between gap-3 rounded-xl border border-sky-100/80 bg-sky-50/70 px-3 text-xs">
        <template v-if="activeBar">
          <div class="shrink-0"><p class="font-black text-slate-800">{{ activeBar.label }}</p><p class="text-[10px] text-slate-500">{{ activeBar.hour.slice(0, 10) }}</p></div>
          <div class="flex flex-wrap justify-end gap-x-4 gap-y-1 tabular-nums">
            <span class="font-bold text-sky-700">{{ activeBar.requests.toLocaleString() }} 次请求</span>
            <span class="font-bold text-rose-500">{{ activeBar.errors }} 次失败</span>
            <span class="hidden text-slate-500 sm:inline">{{ formatScale(activeBar.totalTokens) }} tokens</span>
            <span class="font-bold text-slate-700">{{ formatCost(activeBar.cost) }}</span>
          </div>
        </template>
        <template v-else><span class="font-semibold text-slate-500">每小时请求分布</span><span class="text-[10px] text-slate-400">悬停 / 轻触柱形查看详情</span></template>
      </div>
      <svg
        class="block w-full"
        :viewBox="`0 0 ${svgWidth} ${svgHeight}`"
        :style="{ height: `${svgHeight}px` }"
        preserveAspectRatio="none"
        tabindex="0"
        @keydown.right.prevent="hoveredIndex = Math.min((hoveredIndex ?? -1) + 1, rows.length - 1)"
        @keydown.left.prevent="hoveredIndex = Math.max((hoveredIndex ?? rows.length) - 1, 0)"
        @keydown.esc="hoveredIndex = null"
        @blur="hoveredIndex = null"
        role="img"
        aria-label="24 小时请求趋势图，使用左右方向键查看每小时详情"
        @pointermove="handlePointerMove"
        @pointerleave="hoveredIndex = null"
      >
        <defs>
          <linearGradient :id="gradientId" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#22d3ee" />
            <stop offset="100%" stop-color="#0284c7" />
          </linearGradient>
        </defs>
        <g v-if="activeBar" class="pointer-events-none" aria-hidden="true">
          <rect :x="activeBar.centerX - activeBar.slot / 2" :y="padding.top" :width="activeBar.slot" :height="plotHeight" rx="6" fill="#0ea5e9" opacity="0.07" />
          <line :x1="activeBar.centerX" :x2="activeBar.centerX" :y1="padding.top" :y2="baselineY" stroke="#0ea5e9" stroke-dasharray="3 4" opacity="0.35" />
        </g>
        <g aria-hidden="true">
          <g v-for="tick in gridTicks" :key="tick.ratio">
            <line
              :x1="padding.left" :x2="svgWidth - padding.right"
              :y1="padding.top + (1 - tick.ratio) * plotHeight"
              :y2="padding.top + (1 - tick.ratio) * plotHeight"
              stroke="rgba(100,116,139,.16)" stroke-dasharray="3 6"
            />
            <text x="2" :y="padding.top + (1 - tick.ratio) * plotHeight + 3" fill="#8795a5" font-size="9" font-weight="700">{{ formatScale(tick.value) }}</text>
          </g>
          <line :x1="padding.left" :x2="svgWidth - padding.right" :y1="baselineY" :y2="baselineY" stroke="rgba(100,116,139,.3)" />
        </g>
        <g>
          <g v-for="bar in barLayout" :key="bar.hour" class="traffic-bar" :style="{ transformOrigin: `${bar.centerX}px ${baselineY}px`, animationDelay: `${bar.index * 18}ms` }">
            <rect
              v-if="bar.successHeight > 0"
              :x="bar.x" :y="bar.successY" :width="bar.width" :height="Math.max(1.5, bar.successHeight)"
              rx="3" :fill="`url(#${gradientId})`" :opacity="hoveredIndex === null || hoveredIndex === bar.index ? 0.92 : 0.45"
            />
            <rect
              v-if="bar.errors > 0"
              :x="bar.x" :y="bar.errorY" :width="bar.width" :height="Math.max(1.5, bar.errorHeight)"
              rx="2" fill="#fb7185" :opacity="hoveredIndex === null || hoveredIndex === bar.index ? 0.95 : 0.5"
            />
          </g>
        </g>
        <g aria-hidden="true">
          <text
            v-for="index in xLabelIndexes" :key="`x-${index}`"
            :x="barLayout[index]?.centerX" :y="svgHeight - 8"
            text-anchor="middle" fill="#7b8a9b" font-size="9" font-weight="700"
          >{{ barLayout[index]?.label }}</text>
        </g>
      </svg>
      <p
        v-if="!rows.some((row) => row.requests > 0)"
        class="pointer-events-none absolute inset-0 flex items-center justify-center text-xs font-black text-slate-400"
      >{{ loading ? '加载中…' : '近 24 小时暂无请求' }}</p>
    </div>
  </div>
</template>

<style scoped>
.traffic-panel {
  background-image: linear-gradient(180deg, rgba(255,255,255,.35), rgba(240,249,255,.25));
}
.traffic-bar {
  animation: traffic-rise 650ms cubic-bezier(.22, 1, .36, 1) both;
  transform-box: view-box;
}
.traffic-bar rect {
  transition: opacity 180ms ease, y 450ms ease, height 450ms ease;
}
svg:focus-visible {
  outline: 2px solid #38bdf8;
  outline-offset: 3px;
  border-radius: 8px;
}
@keyframes traffic-rise {
  from { transform: scaleY(0); opacity: 0; }
  to { transform: scaleY(1); opacity: 1; }
}
@media (prefers-reduced-motion: reduce) {
  .traffic-bar { animation: none; }
  .traffic-bar rect { transition: none; }
}
</style>
