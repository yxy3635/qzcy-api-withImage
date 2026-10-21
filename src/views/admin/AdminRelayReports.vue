<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { adminApi } from '@/api/adminApi'
import RelayReportTrend from '@/components/admin/RelayReportTrend.vue'
import type { RelayReport, ReportDimension, ReportMetric, ReportRow, ReportScope } from '@/types/relayReport'

const report = ref<RelayReport | null>(null)
const loading = ref(false)
const error = ref('')
const dimension = ref<ReportDimension>('model')
const scope = ref<ReportScope>('today')
const metric = ref<ReportMetric>('requests')
const keyword = ref('')
const activeOnly = ref(false)
const topCount = ref(8)
const trendWindow = ref<'hourly' | 'daily'>('hourly')
const selectedKey = ref<string | null>(null)
const hoveredKey = ref<string | null>(null)
const page = ref(1)
const descending = ref(true)
const sortField = ref<'name' | 'today.requests' | 'today.cost' | 'today.tokens' | 'total.requests' | 'total.cost' | 'total.tokens'>('today.requests')
const dimensions: { key: ReportDimension; label: string }[] = [
  { key: 'model', label: '模型' }, { key: 'group', label: '分组' }, { key: 'channel', label: '渠道' }, { key: 'provider', label: '供应商' }
]
const metrics: { key: ReportMetric; label: string; unit: string }[] = [
  { key: 'requests', label: '调用次数', unit: '次' }, { key: 'cost', label: '调用金额', unit: '¥' }, { key: 'tokens', label: 'Token 数量', unit: 'Token' }
]
const palette = ['#0d9488', '#0284c7', '#6366f1', '#d97706', '#e11d48', '#475569', '#65a30d', '#9333ea', '#0891b2', '#c2410c', '#4f46e5', '#be185d']
const snapshot = computed(() => report.value?.snapshot)
const dimensionLabel = computed(() => dimensions.find(item => item.key === dimension.value)!.label)
const scopeLabel = computed(() => scope.value === 'today' ? '今日' : '历史累计')
const metricLabel = computed(() => metrics.find(item => item.key === metric.value)!.label)
const sourceRows = computed(() => snapshot.value?.dimensions[dimension.value] || [])
const filtered = computed(() => sourceRows.value.filter(row => {
  const search = keyword.value.trim().toLowerCase()
  return (!search || `${row.name} ${row.detail}`.toLowerCase().includes(search)) && (!activeOnly.value || row[scope.value].requests > 0)
}))
const ranked = computed(() => [...filtered.value].sort((a, b) => value(b) - value(a) || a.key.localeCompare(b.key)))
const chartRows = computed(() => ranked.value.filter(row => value(row) > 0).slice(0, topCount.value))
const filteredTotal = computed(() => filtered.value.reduce((sum, row) => sum + value(row), 0))
const restValue = computed(() => Math.max(0, filteredTotal.value - chartRows.value.reduce((sum, row) => sum + value(row), 0)))
const maxValue = computed(() => Math.max(1e-12, ...chartRows.value.map(value)))
const segments = computed(() => {
  let offset = 0
  const slices = [...chartRows.value.map((row, index) => ({ key: row.key, name: row.name, value: value(row), color: palette[index % palette.length]! })),
    ...(restValue.value > 0 ? [{ key: '__other__', name: '其余项目', value: restValue.value, color: '#cbd5e1' }] : [])]
  return slices.map(slice => {
    const percent = filteredTotal.value ? slice.value / filteredTotal.value * 100 : 0
    const result = { ...slice, percent, offset }
    offset += percent
    return result
  })
})
const selected = computed(() => sourceRows.value.find(row => row.key === selectedKey.value))
const hovered = computed(() => segments.value.find(row => row.key === hoveredKey.value))
const tableRows = computed(() => [...filtered.value].sort((a, b) => {
  if (sortField.value === 'name') return a.name.localeCompare(b.name, 'zh-CN') * (descending.value ? -1 : 1)
  const [range, field] = sortField.value.split('.') as [ReportScope, ReportMetric]
  return (Number(a[range][field]) - Number(b[range][field])) * (descending.value ? -1 : 1) || a.key.localeCompare(b.key)
}))
const pages = computed(() => Math.max(1, Math.ceil(tableRows.value.length / 20)))
const visibleRows = computed(() => tableRows.value.slice((page.value - 1) * 20, page.value * 20))
const trendRows = computed(() => (trendWindow.value === 'hourly' ? snapshot.value?.hourly : snapshot.value?.daily) || [])
watch([dimension, scope, metric, keyword, activeOnly], () => { page.value = 1; selectedKey.value = null; hoveredKey.value = null })
watch([scope, metric], () => { sortField.value = `${scope.value}.${metric.value}`; descending.value = true })

function value(row: ReportRow) { return Number(row[scope.value][metric.value]) }
function number(value: number | undefined) { return Number(value || 0).toLocaleString('zh-CN') }
function money(value: number | undefined) { return `¥${Number(value || 0).toFixed(6)}` }
function format(value: number | undefined) { return metric.value === 'cost' ? money(value) : number(value) }
function percent(value: number) { return filteredTotal.value > 0 ? `${(value / filteredTotal.value * 100).toFixed(1)}%` : '0%' }
function time(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { timeZone: snapshot.value?.timezone || 'Asia/Shanghai', hour12: false }) : '—'
}
function select(key: string) { if (key !== '__other__') selectedKey.value = selectedKey.value === key ? null : key }
function sort(field: typeof sortField.value) {
  descending.value = sortField.value === field ? !descending.value : field !== 'name'
  sortField.value = field
  page.value = 1
}
function order(field: typeof sortField.value) { return sortField.value === field ? descending.value ? ' ↓' : ' ↑' : '' }
function ariaSort(field: typeof sortField.value) { return sortField.value === field ? descending.value ? 'descending' : 'ascending' : 'none' }
async function load() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try { report.value = (await adminApi.relayReport()).data.data }
  catch (err) { error.value = err instanceof Error ? err.message : '报表加载失败' }
  finally { loading.value = false }
}
function exportCsv() {
  const rows: (string | number)[][] = [[dimensionLabel.value, '标识 / 归属', '今日调用', '今日金额(元)', '今日Token', '累计调用', '累计金额(元)', '累计Token', '统计日期', '快照时间'],
    ...tableRows.value.map(row => [row.name, row.detail, row.today.requests, Number(row.today.cost).toFixed(6), row.today.tokens,
      row.total.requests, Number(row.total.cost).toFixed(6), row.total.tokens, snapshot.value!.date, time(snapshot.value!.generatedAt)])]
  // Names are user-editable; keep spreadsheet formulas from executing on export.
  const csv = rows.map(row => row.map(cell => {
    let text = String(cell)
    if (typeof cell === 'string' && /^[\s]*[=+\-@]/.test(text)) text = `'${text}`
    return `"${text.replace(/"/g, '""')}"`
  }).join(',')).join('\r\n')
  const url = URL.createObjectURL(new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url; link.download = `中转报表-${dimensionLabel.value}-${snapshot.value!.date}.csv`; link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
onMounted(load)
</script>

<template>
  <div class="reports">
    <header class="report-header">
      <div><p class="eyebrow">RELAY ANALYTICS</p><h1>中转报表</h1><p class="description">从模型到供应商，查看每一次调用的用量与金额。</p></div>
      <div class="actions"><button :disabled="loading || !snapshot" @click="exportCsv">导出明细 CSV</button><button class="primary" :disabled="loading" @click="load">{{ loading ? '加载中…' : '重新加载' }}</button></div>
    </header>
    <p v-if="error" class="error" role="alert">{{ error }}<button :disabled="loading" @click="load">重试</button></p>
    <div v-if="!snapshot && loading" class="initial-state" role="status">正在生成报表快照，首次加载可能需要一些时间…</div>
    <template v-if="snapshot">
      <div class="snapshot-strip"><span class="status-dot" /><strong>{{ snapshot.date }} · {{ snapshot.timezone }}</strong><span>数据截至 {{ time(snapshot.generatedAt) }}</span><span>一小时缓存 · 到期 {{ time(snapshot.expiresAt) }}</span></div>
      <section class="summary-grid" aria-label="整体调用统计">
        <article v-for="item in metrics" :key="item.key" class="summary-card" :class="item.key">
          <p>今日{{ item.label }}</p><strong>{{ item.key === 'cost' ? money(snapshot.today.cost) : number(snapshot.today[item.key]) }}<small v-if="item.key !== 'cost'">{{ item.unit }}</small></strong>
          <div><span>历史总计</span><b>{{ item.key === 'cost' ? money(snapshot.total.cost) : number(snapshot.total[item.key]) }}</b></div>
        </article>
        <article class="summary-card reliability"><p>今日成功率</p><strong>{{ snapshot.today.requests ? ((1 - snapshot.today.errors / snapshot.today.requests) * 100).toFixed(1) + '%' : '—' }}</strong><div><span>今日失败 / 累计失败</span><b>{{ number(snapshot.today.errors) }} / {{ number(snapshot.total.errors) }}</b></div></article>
      </section>
      <section class="panel analysis">
        <div class="panel-heading"><div><h2>用量分布</h2><p>切换维度与指标；点击图表查看对应明细。</p></div><div class="segmented" aria-label="统计范围"><button v-for="s in (['today', 'total'] as const)" :key="s" :aria-pressed="scope === s" :class="{ active: scope === s }" @click="scope = s">{{ s === 'today' ? '今日' : '历史累计' }}</button></div></div>
        <div class="analysis-toolbar"><div class="dimension-tabs" aria-label="统计维度"><button v-for="item in dimensions" :key="item.key" :class="{ active: dimension === item.key }" :aria-pressed="dimension === item.key" @click="dimension = item.key">{{ item.label }}<small>{{ snapshot.dimensions[item.key].length }}</small></button></div><label class="select-control">统计指标<select v-model="metric" aria-label="统计指标"><option v-for="item in metrics" :key="item.key" :value="item.key">{{ item.label }}</option></select></label></div>
        <div class="filters"><label class="search-label"><span>搜索{{ dimensionLabel }}</span><input v-model="keyword" type="search" :placeholder="`搜索${dimensionLabel}名称或归属…`" /></label><label class="check"><input v-model="activeOnly" type="checkbox" />仅显示{{ scope === 'today' ? '今日' : '累计' }}有调用的项目</label><label class="select-control">图表数量<select v-model.number="topCount" aria-label="图表数量"><option :value="5">Top 5</option><option :value="8">Top 8</option><option :value="12">Top 12</option></select></label></div>
        <div v-if="chartRows.length" class="distribution-grid">
          <div class="ranking"><div class="chart-caption"><h3>{{ scopeLabel }}{{ metricLabel }}排名</h3><span>当前筛选合计 {{ format(filteredTotal) }}</span></div>
            <button v-for="(row, index) in chartRows" :key="row.key" class="ranking-row" :class="{ selected: selectedKey === row.key }" :aria-pressed="selectedKey === row.key" @click="select(row.key)">
              <span class="rank-number">{{ String(index + 1).padStart(2, '0') }}</span><div class="rank-content"><div><span class="rank-name">{{ row.name }}</span><strong>{{ format(value(row)) }}<small>{{ percent(value(row)) }}</small></strong></div><div class="bar-track"><span :style="{ width: `${value(row) / maxValue * 100}%`, background: palette[index % palette.length] }" /></div></div>
            </button>
          </div>
          <div class="share-chart"><h3>{{ dimensionLabel }}占比</h3><div class="donut-stage"><svg viewBox="0 0 220 220" role="group" :aria-label="`${scopeLabel}${dimensionLabel}${metricLabel}占比`"><circle cx="110" cy="110" r="82" fill="none" stroke="#e2e8f0" stroke-width="22" />
              <circle v-for="slice in segments" :key="slice.key" cx="110" cy="110" r="82" fill="none" :stroke="slice.color" :stroke-width="hoveredKey === slice.key || selectedKey === slice.key ? 27 : 22" pathLength="100" :stroke-dasharray="`${slice.percent} ${100 - slice.percent}`" :stroke-dashoffset="-slice.offset" transform="rotate(-90 110 110)" tabindex="0" role="button" :aria-label="`${slice.name} ${slice.percent.toFixed(1)}%，${format(slice.value)}`" :aria-pressed="selectedKey === slice.key" @pointerenter="hoveredKey = slice.key" @pointerleave="hoveredKey = null" @focus="hoveredKey = slice.key" @blur="hoveredKey = null" @click="select(slice.key)" @keydown.enter.prevent="select(slice.key)" @keydown.space.prevent="select(slice.key)" />
            </svg><div class="donut-center"><span>{{ hovered?.name || '筛选合计' }}</span><strong>{{ hovered ? hovered.percent.toFixed(1) + '%' : format(filteredTotal) }}</strong><small>{{ hovered ? format(hovered.value) : metricLabel }}</small></div></div>
            <div class="legend"><button v-for="slice in segments" :key="slice.key" :aria-pressed="selectedKey === slice.key" @mouseenter="hoveredKey = slice.key" @mouseleave="hoveredKey = null" @click="select(slice.key)"><i :style="{ background: slice.color }" /><span>{{ slice.name }}</span><strong>{{ slice.percent.toFixed(1) }}%</strong></button></div>
          </div>
        </div>
        <div v-else class="empty">{{ filtered.length ? '当前范围没有此指标的用量' : '没有符合筛选条件的项目' }}</div>
        <div v-if="selected" class="detail-panel"><div class="detail-heading"><div><h3>{{ selected.name }} <span v-if="selected.legacy" class="legacy">旧记录待确认</span></h3><p>{{ selected.detail }}</p></div><button @click="selectedKey = null" aria-label="关闭项目明细">关闭</button></div><div class="detail-grid"><div><span>{{ scopeLabel }}调用</span><strong>{{ number(selected[scope].requests) }} 次</strong></div><div><span>{{ scopeLabel }}金额</span><strong>{{ money(selected[scope].cost) }}</strong></div><div><span>输入 / 输出 Token</span><strong>{{ number(selected[scope].inputTokens) }} / {{ number(selected[scope].outputTokens) }}</strong></div><div><span>缓存读取 / 创建 Token</span><strong>{{ number(selected[scope].cacheReadTokens) }} / {{ number(selected[scope].cacheCreationTokens) }}</strong></div><div><span>其中 1h 缓存创建</span><strong>{{ number(selected[scope].cacheCreation1hTokens) }} Token</strong></div><div><span>失败调用</span><strong>{{ number(selected[scope].errors) }} 次</strong></div></div><p class="detail-note">输入 Token 已包含缓存读取与创建；缓存细分为输入的子集，不再重复累加。</p></div>
      </section>
      <section class="panel"><div class="panel-heading"><div><h2>调用趋势</h2><p>全站{{ metricLabel }}趋势，不受上方维度与搜索条件影响。</p></div><div class="segmented" aria-label="趋势周期"><button :class="{ active: trendWindow === 'hourly' }" :aria-pressed="trendWindow === 'hourly'" @click="trendWindow = 'hourly'">今日小时</button><button :class="{ active: trendWindow === 'daily' }" :aria-pressed="trendWindow === 'daily'" @click="trendWindow = 'daily'">近 30 日</button></div></div><RelayReportTrend :rows="trendRows" :metric="metric" :hourly="trendWindow === 'hourly'" /></section>
      <section class="panel table-panel"><div class="panel-heading"><div><h2>{{ dimensionLabel }}统计明细</h2><p>今日与历史总计并列展示 · 点击列名排序或名称查看细分。</p></div><span class="count-label">{{ filtered.length }} 个项目</span></div><div class="table-scroll"><table><thead><tr><th :aria-sort="ariaSort('name')"><button @click="sort('name')">{{ dimensionLabel }}{{ order('name') }}</button></th><th v-for="field in (['today.requests', 'today.cost', 'today.tokens', 'total.requests', 'total.cost', 'total.tokens'] as const)" :key="field" :aria-sort="ariaSort(field)"><button @click="sort(field)">{{ field.startsWith('today') ? '今日' : '累计' }}{{ field.endsWith('requests') ? '调用' : field.endsWith('cost') ? '金额' : 'Token' }}{{ order(field) }}</button></th></tr></thead><tbody><tr v-for="row in visibleRows" :key="row.key" :class="{ selected: selectedKey === row.key }"><td><button class="row-name" @click="select(row.key)">{{ row.name }}</button><span v-if="row.legacy" class="legacy">待确认</span><small>{{ row.detail }}</small></td><td>{{ number(row.today.requests) }}</td><td class="money-cell">{{ money(row.today.cost) }}</td><td>{{ number(row.today.tokens) }}</td><td>{{ number(row.total.requests) }}</td><td>{{ money(row.total.cost) }}</td><td>{{ number(row.total.tokens) }}</td></tr></tbody></table></div><div v-if="!visibleRows.length" class="empty">暂无明细</div><div class="pagination"><span>第 {{ page }} / {{ pages }} 页 · 每页 20 条</span><div><button :disabled="page <= 1" @click="page--">上一页</button><button :disabled="page >= pages" @click="page++">下一页</button></div></div>
        <div v-if="selected" class="table-selection">已选：{{ selected.name }} · {{ scopeLabel }} {{ number(selected[scope].requests) }} 次 / {{ money(selected[scope].cost) }} / {{ number(selected[scope].tokens) }} Token <button @click="selectedKey = null">取消选择</button></div>
      </section>
      <footer class="report-notes"><p>统计口径：调用次数包含成功与失败；金额为使用日志记录的实际扣费人民币金额；Token 使用日志总量（已包含缓存）。历史总计为当前保留日志截至快照的累计值。</p><p>分组按实际计费分组归属；旧日志的单分组可归属，多分组记录单列为「待确认」，不重复计入多个分组。已删除的渠道与供应商仍保留历史统计。重新加载会读取有效缓存，到期后才重新统计。</p></footer>
    </template>
  </div>
</template>

<style scoped>
.reports { --ink:#152c3b; --muted:#64748b; --teal:#0f766e; color:var(--ink); max-width:1600px; margin:0 auto; }h1,h2,h3,p { margin:0; }button,input,select { font:inherit; }button { cursor:pointer; transition:background .15s,border-color .15s; }button:disabled { opacity:.45; cursor:not-allowed; }button:focus-visible,input:focus-visible,select:focus-visible { outline:2px solid #0d9488; outline-offset:3px; }h1 { font-size:2rem; font-weight:800; margin:.35rem 0 .6rem; }h2 { font-size:1.1rem; font-weight:800; }h3 { font-size:.82rem; font-weight:750; }.eyebrow { color:var(--teal); font-size:.65rem; letter-spacing:.18em; font-weight:800; }.description { color:var(--muted); font-size:.86rem; }.report-header { display:flex; justify-content:space-between; align-items:center; gap:1rem; margin-bottom:1.4rem; }.actions { display:flex; gap:.6rem; flex-shrink:0; }.actions button,.pagination button,.detail-heading button { background:#fff; border:1px solid #cbd5e1; border-radius:6px; padding:.6rem .85rem; font-size:.77rem; font-weight:650; }.actions .primary { background:var(--teal); border-color:var(--teal); color:#fff; }.actions button:hover:not(:disabled),.pagination button:hover:not(:disabled) { border-color:#0d9488; }.actions .primary:hover:not(:disabled) { background:#115e59; }.snapshot-strip { display:flex; align-items:center; flex-wrap:wrap; gap:.5rem 1.2rem; font-size:.69rem; color:var(--muted); margin-bottom:1rem; padding:.75rem 1rem; border:1px solid #dbe8e7; border-radius:6px; background:#f5fbfa; }.snapshot-strip strong { color:#334155; }.status-dot { width:6px; height:6px; border-radius:50%; background:#0d9488; }.summary-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:.85rem; margin-bottom:1.2rem; }.summary-card { border:1px solid #dbe3e9; border-top:3px solid #0284c7; border-radius:7px; background:rgba(255,255,255,.85); padding:1.1rem; }.summary-card.cost { border-top-color:#0d9488; }.summary-card.tokens { border-top-color:#6366f1; }.summary-card.reliability { border-top-color:#d97706; }.summary-card p { font-size:.76rem; color:var(--muted); }.summary-card > strong { display:block; font-size:clamp(1.1rem,1.6vw,1.7rem); margin:.75rem 0 1.1rem; overflow-wrap:anywhere; font-weight:800; font-variant-numeric:tabular-nums; }.summary-card strong small { font-size:.65rem; margin-left:.35rem; color:var(--muted); font-weight:500; }.summary-card > div { border-top:1px solid #edf2f7; padding-top:.75rem; display:flex; flex-wrap:wrap; gap:.2rem .5rem; justify-content:space-between; font-size:.67rem; font-variant-numeric:tabular-nums; }.summary-card div span { color:var(--muted); }.summary-card b { font-weight:650; }.panel { background:rgba(255,255,255,.9); border:1px solid #dbe3e9; border-radius:8px; padding:1.25rem; margin-bottom:1.2rem; box-shadow:0 5px 20px #1e495e06; }.panel-heading { display:flex; align-items:center; justify-content:space-between; gap:1rem; margin-bottom:1.1rem; }.panel-heading p { margin-top:.3rem; color:var(--muted); font-size:.72rem; }.segmented { display:flex; gap:3px; border:1px solid #e2e8f0; padding:3px; background:#f8fafc; border-radius:6px; flex-shrink:0; }.segmented button { padding:.43rem .85rem; font-size:.74rem; color:var(--muted); border-radius:4px; white-space:nowrap; }.segmented button.active { background:#0f766e; color:white; }.analysis-toolbar { display:flex; justify-content:space-between; align-items:center; gap:1rem; border-bottom:1px solid #e2e8f0; }.dimension-tabs { display:flex; gap:.4rem; }.dimension-tabs button { border-bottom:2px solid transparent; padding:.8rem .85rem; font-size:.83rem; color:var(--muted); font-weight:700; white-space:nowrap; }.dimension-tabs button.active { border-color:#0d9488; color:#0f766e; }.dimension-tabs small { margin-left:.4rem; font-size:.6rem; color:#64748b; background:#f1f5f9; border-radius:4px; padding:.1rem .3rem; }.select-control { display:flex; align-items:center; gap:.5rem; font-size:.7rem; color:var(--muted); white-space:nowrap; }.select-control select { border:1px solid #dbe3e9; border-radius:5px; padding:.45rem .6rem; background:#fff; font-size:.74rem; color:#334155; }.filters { display:flex; flex-wrap:wrap; align-items:center; gap:.9rem 1.3rem; margin:1rem 0 1.4rem; }.search-label { flex:1; min-width:180px; max-width:360px; }.search-label > span { position:absolute; width:1px; height:1px; overflow:hidden; clip-path:inset(50%); }.search-label input { width:100%; border:1px solid #dbe3e9; border-radius:5px; background:#f8fafc; padding:.6rem .75rem; font-size:.76rem; }.check { display:flex; align-items:center; gap:.45rem; font-size:.72rem; color:var(--muted); }.check input { accent-color:#0d9488; }.distribution-grid { display:grid; grid-template-columns:minmax(0,1.6fr) minmax(260px,1fr); gap:2rem; }.chart-caption { display:flex; align-items:center; justify-content:space-between; gap:.8rem; margin-bottom:.7rem; }.chart-caption span { font-size:.66rem; color:var(--muted); }.ranking-row { display:flex; width:100%; text-align:left; align-items:center; gap:.65rem; padding:.6rem .65rem; margin-bottom:.3rem; border-radius:6px; border:1px solid transparent; }.ranking-row:hover,.ranking-row.selected { background:#f0fdfa; border-color:#ccfbf1; }.rank-number { color:#94a3b8; font-size:.65rem; width:1.25rem; font-variant-numeric:tabular-nums; }.rank-content { flex:1; min-width:0; }.rank-content > div:first-child { display:flex; justify-content:space-between; gap:.8rem; align-items:baseline; }.rank-name { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:.75rem; }.rank-content strong { font-size:.75rem; white-space:nowrap; font-weight:650; font-variant-numeric:tabular-nums; }.rank-content strong small { margin-left:.8rem; font-size:.65rem; color:var(--muted); font-weight:500; }.bar-track { height:6px; background:#f1f5f9; border-radius:3px; overflow:hidden; margin-top:.55rem; }.bar-track span { display:block; height:100%; border-radius:3px; transition:width .25s; }.share-chart { border-left:1px solid #edf2f7; padding-left:2rem; }.donut-stage { position:relative; width:230px; max-width:100%; margin:.2rem auto .6rem; }.donut-stage svg { width:100%; overflow:visible; }.donut-stage circle[role=button] { cursor:pointer; transition:stroke-width .15s; }.donut-stage circle:focus-visible { outline:none; stroke-width:28; filter:drop-shadow(0 0 2px #334155); }.donut-center { position:absolute; inset:0; display:flex; flex-direction:column; align-items:center; justify-content:center; pointer-events:none; gap:.4rem; text-align:center; padding:3.7rem; }.donut-center span { font-size:.69rem; color:var(--muted); max-width:135px; overflow:hidden; white-space:nowrap; text-overflow:ellipsis; }.donut-center strong { font-size:1.07rem; font-weight:800; font-variant-numeric:tabular-nums; overflow-wrap:anywhere; }.donut-center small { color:var(--muted); font-size:.61rem; }.legend { display:grid; grid-template-columns:1fr 1fr; gap:.3rem .8rem; }.legend button { display:flex; align-items:center; gap:.4rem; font-size:.64rem; text-align:left; padding:.35rem 0; min-width:0; }.legend i { width:7px; height:7px; border-radius:2px; flex-shrink:0; }.legend span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.legend strong { margin-left:auto; font-weight:600; white-space:nowrap; }.detail-panel { margin-top:1.3rem; padding:1.1rem; border:1px solid #bfe5df; background:#f6fcfa; border-radius:6px; }.detail-heading { display:flex; align-items:center; justify-content:space-between; gap:1rem; }.detail-heading p { margin-top:.3rem; font-size:.7rem; color:var(--muted); }.detail-heading button { padding:.3rem .6rem; }.detail-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:1rem; margin-top:1rem; }.detail-grid span { display:block; font-size:.66rem; color:var(--muted); }.detail-grid strong { display:block; margin-top:.3rem; font-size:.8rem; overflow-wrap:anywhere; font-variant-numeric:tabular-nums; }.detail-note { margin-top:1rem; font-size:.65rem; color:var(--muted); }.table-panel { padding:0; overflow:hidden; }.table-panel .panel-heading { padding:1.2rem 1.25rem; margin:0; }.count-label { font-size:.7rem; color:var(--muted); }.table-scroll { overflow-x:auto; }table { width:100%; min-width:850px; border-collapse:collapse; font-variant-numeric:tabular-nums; }th { text-align:right; font-weight:650; font-size:.7rem; background:#f8fafc; color:var(--muted); border-top:1px solid #e2e8f0; border-bottom:1px solid #e2e8f0; white-space:nowrap; }th button { padding:.8rem 1rem; width:100%; text-align:inherit; }th:first-child,td:first-child { text-align:left; }td { padding:.9rem 1rem; text-align:right; border-bottom:1px solid #f1f5f9; white-space:nowrap; font-size:.75rem; }td small { display:block; font-size:.63rem; color:var(--muted); margin-top:.25rem; max-width:260px; overflow:hidden; text-overflow:ellipsis; }tbody tr:hover,tbody tr.selected { background:#f0fdfa; }.row-name { font-weight:700; color:#0f766e; max-width:260px; overflow:hidden; text-overflow:ellipsis; vertical-align:middle; }.money-cell { color:#0f766e; }.legacy { display:inline-block; font-size:.57rem; font-weight:600; border-radius:3px; padding:.15rem .3rem; background:#fef3c7; color:#92400e; margin-left:.35rem; }.pagination { display:flex; align-items:center; justify-content:space-between; gap:1rem; padding:.85rem 1.25rem; font-size:.68rem; color:var(--muted); }.pagination > div { display:flex; gap:.4rem; }.pagination button { padding:.35rem .7rem; font-size:.68rem; }.table-selection { padding:.8rem 1.25rem; font-size:.71rem; background:#f0fdfa; color:#0f766e; }.table-selection button { margin-left:.6rem; text-decoration:underline; }.report-notes { font-size:.68rem; color:var(--muted); line-height:1.8; padding:0 .25rem 1rem; }.report-notes p + p { margin-top:.4rem; }.empty,.initial-state { padding:3rem 1rem; text-align:center; color:var(--muted); font-size:.83rem; }.error { padding:.85rem 1rem; margin-bottom:1rem; border-radius:6px; background:#fff1f2; color:#be123c; font-size:.8rem; }.error button { margin-left:1rem; text-decoration:underline; }
@media(max-width:1200px) { .summary-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.distribution-grid { gap:1rem; grid-template-columns:minmax(0,1.4fr) minmax(230px,1fr); }.share-chart { padding-left:1rem; }.rank-content strong small { margin-left:.4rem; } }@media(max-width:800px) { .distribution-grid { grid-template-columns:1fr; }.share-chart { border-left:0; border-top:1px solid #e2e8f0; padding:1rem 0 0; }.legend { grid-template-columns:repeat(3,minmax(0,1fr)); }.detail-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.report-header { align-items:flex-start; flex-direction:column; }.analysis-toolbar { flex-wrap:wrap; padding-bottom:.7rem; }.dimension-tabs { width:100%; overflow-x:auto; }.dimension-tabs button { flex:1; } }@media(max-width:520px) { h1 { font-size:1.7rem; }.summary-grid { gap:.6rem; }.summary-card { padding:.85rem; }.summary-card > strong { font-size:1.06rem; }.summary-card > div { flex-direction:column; }.panel { padding:1rem; }.table-panel { padding:0; }.panel-heading { align-items:flex-start; flex-direction:column; }.legend { grid-template-columns:repeat(2,minmax(0,1fr)); }.chart-caption { align-items:flex-start; flex-direction:column; }.rank-content strong small { display:none; }.detail-grid { grid-template-columns:1fr; }.filters .search-label { max-width:none; width:100%; }.dimension-tabs button { padding:.65rem .45rem; }.dimension-tabs small { margin-left:.25rem; }.snapshot-strip { gap:.4rem; }.snapshot-strip > span:not(.status-dot) { width:100%; }.actions button { padding:.5rem .65rem; } }@media(prefers-reduced-motion:reduce) { * { transition:none !important; } }
</style>
