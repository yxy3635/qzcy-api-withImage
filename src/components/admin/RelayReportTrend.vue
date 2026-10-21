<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ReportMetric, ReportTrendPoint } from '@/types/relayReport'

const props = defineProps<{ rows: ReportTrendPoint[]; metric: ReportMetric; hourly: boolean }>()
const active = ref<number | null>(null)
const pinned = ref<number | null>(null)
const width = 900
const height = 242
const left = 60
const top = 20
const plotWidth = 810
const plotHeight = 180
const maximum = computed(() => {
  const peak = Math.max(0, ...props.rows.map(row => Number(row.metrics[props.metric])))
  return peak > 0 ? peak * 1.1 : 1
})
const points = computed(() => props.rows.map((row, i) => ({ ...row,
  x: left + i / Math.max(1, props.rows.length - 1) * plotWidth,
  y: top + plotHeight - Number(row.metrics[props.metric]) / maximum.value * plotHeight
})))
const path = computed(() => points.value.map((point, i) => `${i ? 'L' : 'M'} ${point.x} ${point.y}`).join(' '))
const selected = computed(() => points.value[active.value ?? pinned.value ?? Math.max(0, points.value.length - 1)])
const total = computed(() => props.rows.reduce((sum, row) => sum + Number(row.metrics[props.metric]), 0))
const peak = computed(() => props.rows.reduce((best, row) => Number(row.metrics[props.metric]) > Number(best?.metrics[props.metric] ?? -1) ? row : best, props.rows[0]))
watch([() => props.rows, () => props.metric], () => { active.value = null; pinned.value = null })
function format(value: number) {
  if (props.metric === 'cost') return `¥${Number(value).toFixed(6)}`
  return Number(value).toLocaleString('zh-CN')
}
function short(value: number) {
  if (value >= 1e6) return `${(value / 1e6).toFixed(1)}M`
  if (value >= 1e3) return `${(value / 1e3).toFixed(1)}K`
  return props.metric === 'cost' ? value.toFixed(2) : Math.round(value).toString()
}
function label(bucket: string) { return props.hourly ? bucket.slice(11, 16) : bucket.slice(5) }
function pin(index: number) { pinned.value = pinned.value === index ? null : index }
</script>

<template>
  <div class="report-trend">
    <div class="trend-summary">
      <div><span>{{ hourly ? '今日截至快照' : '近 30 日合计' }}</span><strong>{{ format(total) }}</strong></div>
      <div><span>峰值 · {{ peak ? label(peak.bucket) : '—' }}</span><strong>{{ format(Number(peak?.metrics[metric] || 0)) }}</strong></div>
      <p>悬停查看 · 点击锁定 · 可用键盘选择时段</p>
    </div>
    <div class="chart-scroll">
      <svg :viewBox="`0 0 ${width} ${height}`" role="group" :aria-label="hourly ? '今日小时趋势' : '近三十日趋势'" @pointerleave="active = null" @keydown.esc="pinned = null">
        <g v-for="ratio in [0, 0.5, 1]" :key="ratio">
          <line :x1="left" :x2="left + plotWidth" :y1="top + plotHeight * ratio" :y2="top + plotHeight * ratio" class="grid-line" />
          <text :x="left - 12" :y="top + plotHeight * ratio + 4" text-anchor="end" class="axis">{{ short(maximum * (1 - ratio)) }}</text>
        </g>
        <path v-if="points.length" :d="`${path} L ${points[points.length - 1]!.x} ${top + plotHeight} L ${left} ${top + plotHeight} Z`" class="area" />
        <path :d="path" class="trend-line" />
        <template v-for="(point, index) in points" :key="point.bucket">
          <text v-if="index % (hourly ? 4 : 5) === 0 || index === points.length - 1" :x="point.x" :y="top + plotHeight + 27" text-anchor="middle" class="axis">{{ label(point.bucket) }}</text>
          <rect :x="point.x - plotWidth / Math.max(1, points.length - 1) / 2" :y="top" :width="plotWidth / Math.max(1, points.length - 1)" :height="plotHeight" class="hit-target" tabindex="0" role="button"
            :aria-label="`${point.bucket}：${format(Number(point.metrics[metric]))}，点击锁定`" :aria-pressed="pinned === index"
            @pointerenter="active = index" @focus="active = index" @blur="active = null" @click="pin(index)" @keydown.enter.prevent="pin(index)" @keydown.space.prevent="pin(index)" />
        </template>
        <g v-if="selected" class="selection" pointer-events="none">
          <line :x1="selected.x" :x2="selected.x" :y1="top" :y2="top + plotHeight" />
          <circle :cx="selected.x" :cy="selected.y" r="5" />
        </g>
      </svg>
    </div>
    <div v-if="selected" class="point-detail" aria-live="polite">
      <strong>{{ selected.bucket }}</strong>
      <span>{{ selected.metrics.requests.toLocaleString() }} 次调用</span>
      <span>¥{{ Number(selected.metrics.cost).toFixed(6) }}</span>
      <span>{{ selected.metrics.tokens.toLocaleString() }} Token</span>
      <span>{{ selected.metrics.errors.toLocaleString() }} 次失败</span>
      <button v-if="pinned !== null" type="button" @click="pinned = null">取消锁定</button>
    </div>
  </div>
</template>

<style scoped>
.trend-summary { display:flex; align-items:center; gap:2rem; padding:1rem 0; }.trend-summary span { display:block; color:#64748b; font-size:.72rem; }.trend-summary strong { display:block; margin-top:.3rem; font-size:1.22rem; color:#0f172a; font-variant-numeric:tabular-nums; }.trend-summary p { margin-left:auto; color:#64748b; font-size:.7rem; }.chart-scroll { overflow-x:auto; }svg { width:100%; min-width:480px; }.grid-line { stroke:#e2e8f0; stroke-dasharray:4 5; }.axis { fill:#64748b; font-size:11px; }.trend-line { fill:none; stroke:#0d9488; stroke-width:2.5; stroke-linejoin:round; }.area { fill:#0d9488; opacity:.07; }.hit-target { fill:transparent; cursor:crosshair; }.hit-target:focus-visible { outline:none; fill:#0d94881a; stroke:#0d9488; stroke-width:1; }.selection line { stroke:#0d9488; stroke-dasharray:4 4; opacity:.55; }.selection circle { fill:#fff; stroke:#0d9488; stroke-width:3; }.point-detail { display:flex; flex-wrap:wrap; gap:.8rem 1.4rem; padding:.8rem 1rem; border-radius:6px; background:#f0fdfa; color:#0f766e; font-size:.75rem; font-variant-numeric:tabular-nums; }.point-detail button { margin-left:auto; text-decoration:underline; }.point-detail button:focus-visible { outline:2px solid #0d9488; outline-offset:3px; }@media(max-width:640px) { .trend-summary { flex-wrap:wrap; gap:.8rem 1.5rem; }.trend-summary p { width:100%; margin:0; }.point-detail { gap:.5rem 1rem; } }
</style>
