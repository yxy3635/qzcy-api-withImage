<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'
import AnimatedNumber from '@/components/relay/AnimatedNumber.vue'
import AdminMiniTrendChart from '@/components/admin/AdminMiniTrendChart.vue'
import { adminApi } from '@/api/adminApi'
import type { AdminStats } from '@/types'

const stats = ref<AdminStats | null>(null)
const loading = ref(false)
const error = ref('')

const totalRelayTokens = computed(() =>
  (stats.value?.relayChannelProfits || []).reduce((sum, item) => sum + Number(item.totalTokens || 0), 0)
)

const primaryCards = computed(() => [
  { label: '总用户', value: Number(stats.value?.totalUsers || 0), detail: '平台注册账户', accent: 'sky', icon: 'users', money: false },
  { label: '总生成', value: Number(stats.value?.totalImages || 0), detail: `今日 ${compact(stats.value?.todayImages)}`, accent: 'violet', icon: 'image', money: false },
  { label: '累计充值', value: Number(stats.value?.totalRevenue || 0), detail: '已完成支付订单', accent: 'emerald', icon: 'wallet', money: true },
  { label: '累计 Token', value: totalRelayTokens.value, detail: '中转调用累计', accent: 'amber', icon: 'sparkles', money: false }
])

const relayCards = computed(() => [
  { label: '今日调用', value: Number(stats.value?.todayRelayRequests || 0), previous: `昨日 ${compact(stats.value?.yesterdayRelayRequests)}`, accent: 'sky', icon: 'activity', money: false },
  { label: '今日 Tokens', value: Number(stats.value?.todayRelayTokens || 0), previous: `昨日 ${compact(stats.value?.yesterdayRelayTokens)}`, accent: 'violet', icon: 'tokens', money: false },
  { label: '今日调用金额', value: Number(stats.value?.todayRelayCost || 0), previous: `昨日 ${yuan(stats.value?.yesterdayRelayCost)}`, accent: 'emerald', icon: 'arrow-up', money: true },
  { label: '今日上游成本', value: Number(stats.value?.todayRelayUpstreamCost || 0), previous: `昨日 ${yuan(stats.value?.yesterdayRelayUpstreamCost)}`, accent: 'rose', icon: 'arrow-down', money: true },
  { label: '今日利润', value: Math.abs(Number(stats.value?.todayRelayProfit || 0)), previous: `昨日 ${profitLabel(stats.value?.yesterdayRelayProfit)}`, accent: Number(stats.value?.todayRelayProfit || 0) >= 0 ? 'teal' : 'red', icon: 'trend', money: true, negative: Number(stats.value?.todayRelayProfit || 0) < 0 }
])

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.dashboard()
    stats.value = data.data
  } catch (err) {
    error.value = err instanceof Error ? err.message : '仪表盘数据加载失败'
  } finally {
    loading.value = false
  }
}

function yuan(value?: number, digits = 4) {
  return `¥ ${Number(value || 0).toFixed(digits)}`
}

function compact(value?: number) {
  const amount = Number(value || 0)
  if (amount >= 1_000_000_000) return `${(amount / 1_000_000_000).toFixed(2)}B`
  if (amount >= 1_000_000) return `${(amount / 1_000_000).toFixed(2)}M`
  if (amount >= 1_000) return `${(amount / 1_000).toFixed(2)}K`
  return Math.round(amount).toLocaleString()
}

function profitLabel(value?: number) {
  const amount = Number(value || 0)
  return `${amount >= 0 ? '盈利' : '亏损'} ${yuan(Math.abs(amount))}`
}

onMounted(load)
</script>

<template>
  <AppLayout admin wide>
    <div class="dashboard-shell">
      <section class="dashboard-heading">
        <div>
          <p class="eyebrow">运营总览</p>
          <h1>管理员仪表盘</h1>
          <p class="dashboard-description">掌握账户增长、充值收入，以及中转业务每一天的真实盈亏。</p>
        </div>
        <div class="heading-actions">
          <RouterLink class="usage-link" to="/admin/user-usage">
            用户用量
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6" /></svg>
          </RouterLink>
          <button class="refresh-button" type="button" title="刷新数据" :disabled="loading" @click="load">
            <svg :class="{ 'is-spinning': loading }" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 11a8.1 8.1 0 0 0-15.5-2.6L3 10m1-6v4h4m-4 5a8.1 8.1 0 0 0 15.5 2.6L21 14m-1 6v-4h-4" /></svg>
          </button>
        </div>
      </section>

      <p v-if="error" class="dashboard-error">{{ error }}</p>

      <section class="overview-grid" aria-label="平台概览">
        <article v-for="(card, index) in primaryCards" :key="card.label" class="overview-card" :class="`accent-${card.accent}`" :style="{ '--delay': `${index * 65}ms` }">
          <div class="metric-icon" aria-hidden="true">
            <svg v-if="card.icon === 'users'" viewBox="0 0 24 24"><path d="M16 20v-1.5a4.5 4.5 0 0 0-4.5-4.5h-4A4.5 4.5 0 0 0 3 18.5V20m13-14a3.5 3.5 0 1 1 0 7m5 7v-1.5a4.5 4.5 0 0 0-3-4.24M12 6a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0Z" /></svg>
            <svg v-else-if="card.icon === 'image'" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2" /><circle cx="8" cy="9" r="1.5" /><path d="m21 15-4.5-4.5L7 20" /></svg>
            <svg v-else-if="card.icon === 'wallet'" viewBox="0 0 24 24"><path d="M4 7.5A2.5 2.5 0 0 1 6.5 5H19v14H6.5A2.5 2.5 0 0 1 4 16.5v-9Z" /><path d="M4 8h13v5H4m11-2.5h2" /></svg>
            <svg v-else viewBox="0 0 24 24"><path d="m12 3 1.5 5.5L19 10l-5.5 1.5L12 17l-1.5-5.5L5 10l5.5-1.5L12 3Zm6 13 .8 2.2L21 19l-2.2.8L18 22l-.8-2.2L15 19l2.2-.8L18 16Z" /></svg>
          </div>
          <p>{{ card.label }}</p>
          <strong><AnimatedNumber :value="card.value" :format="card.money ? (value) => yuan(value, 2) : compact" /></strong>
          <small>{{ card.detail }}</small>
        </article>
      </section>

      <section class="relay-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">API Relay</p>
            <h2>今日经营情况</h2>
          </div>
          <div class="profit-summary" :class="Number(stats?.relayProfit || 0) >= 0 ? 'positive' : 'negative'">
            <span>累计{{ Number(stats?.relayProfit || 0) >= 0 ? '盈利' : '亏损' }}</span>
            <strong>{{ yuan(Math.abs(Number(stats?.relayProfit || 0))) }}</strong>
          </div>
        </div>
        <div class="relay-grid">
          <article v-for="(card, index) in relayCards" :key="card.label" class="relay-card" :class="`accent-${card.accent}`" :style="{ '--delay': `${index * 65 + 180}ms` }">
            <div class="relay-card-top">
              <span>{{ card.label }}</span>
              <svg v-if="card.icon === 'activity'" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 12h4l2-6 4 12 2-6h6" /></svg>
              <svg v-else-if="card.icon === 'tokens'" viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 7 4-7 4-7-4 7-4Zm-7 9 7 4 7-4m-14 5 7 4 7-4" /></svg>
              <svg v-else-if="card.icon === 'arrow-up'" viewBox="0 0 24 24" aria-hidden="true"><path d="m7 17 10-10m-8 0h8v8" /></svg>
              <svg v-else-if="card.icon === 'arrow-down'" viewBox="0 0 24 24" aria-hidden="true"><path d="m7 7 10 10m0-8v8H9" /></svg>
              <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="m4 16 5-5 4 3 7-8M15 6h5v5" /></svg>
            </div>
            <strong :class="{ 'text-negative': card.negative }"><span v-if="card.negative">-</span><AnimatedNumber :value="card.value" :format="card.money ? yuan : compact" /></strong>
            <small>{{ card.previous }}</small>
          </article>
        </div>
      </section>

      <section class="data-panel channel-panel">
        <div class="table-heading">
          <div>
            <h2>渠道日度盈亏</h2>
            <p>收入来自本站计费；上游成本根据渠道倍率与模型价格计算。</p>
          </div>
          <span>按今日利润排序</span>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>渠道</th><th>今日调用 / Token</th><th>今日收入</th><th>今日上游成本</th><th>今日利润</th><th>昨日收入 / 利润</th><th>累计利润</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in stats?.relayChannelProfits || []" :key="item.channelId || item.channelName">
                <td><strong>{{ item.channelName || 'Unknown' }}</strong><small>累计 {{ compact(item.requests) }} 次</small></td>
                <td><strong>{{ compact(item.todayRequests) }}</strong><small>{{ compact(item.todayTokens) }} Tokens</small></td>
                <td>{{ yuan(item.todaySiteCost) }}</td>
                <td>{{ yuan(item.todayUpstreamCost) }}</td>
                <td><span class="profit-pill" :class="Number(item.todayProfit || 0) >= 0 ? 'positive' : 'negative'">{{ profitLabel(item.todayProfit) }}</span></td>
                <td><strong>{{ yuan(item.yesterdaySiteCost) }}</strong><small>{{ profitLabel(item.yesterdayProfit) }}</small></td>
                <td :class="Number(item.profit || 0) >= 0 ? 'text-positive' : 'text-negative'">{{ profitLabel(item.profit) }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="!(stats?.relayChannelProfits || []).length" class="empty-table">暂无中转调用记录</div>
        </div>
      </section>

      <section class="trend-board" aria-labelledby="trend-board-title">
        <div class="trend-board-heading">
          <div>
            <p class="eyebrow">Growth Pulse</p>
            <h2 id="trend-board-title">增长与产出趋势</h2>
            <p>最近 7 个自然日的账户增长与图像生成变化。</p>
          </div>
          <div class="trend-window" aria-label="统计周期为最近 7 个自然日">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3v3m12-3v3M4 9h16M5 5h14a1 1 0 0 1 1 1v14H4V6a1 1 0 0 1 1-1Z" /></svg>
            <div><strong>近 7 日</strong><small>截至今日</small></div>
          </div>
        </div>

        <div class="trend-grid">
          <AdminMiniTrendChart
            :rows="stats?.recentRegistrations || []"
            title="注册趋势"
            description="每日新增账户"
            label="新增用户"
            unit="人"
            icon="users"
            color="#0284c7"
            fill-color="#7dd3fc"
            :loading="loading"
          />
          <AdminMiniTrendChart
            :rows="stats?.generationTrend || []"
            title="生成趋势"
            description="每日图像生成量"
            label="生成量"
            unit="张"
            icon="image"
            color="#7c3aed"
            fill-color="#c4b5fd"
            :loading="loading"
          />
        </div>
      </section>
    </div>
  </AppLayout>
</template>

<style scoped>
.dashboard-shell { --glass: rgba(255,255,255,.48); --glass-strong: rgba(255,255,255,.70); --glass-line: rgba(255,255,255,.72); color: #172033; }
.dashboard-heading, .section-heading, .table-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 1rem; }
.eyebrow { margin: 0; color: #0284c7; font-size: .69rem; font-weight: 800; letter-spacing: .14em; text-transform: uppercase; }
h1, h2, p { margin: 0; } h1 { margin-top: .42rem; font-size: 2rem; font-weight: 850; letter-spacing: 0; color: #0f172a; } h2 { font-size: 1.06rem; font-weight: 800; color: #172033; }
.dashboard-description { margin-top: .6rem; color: #52657a; font-size: .9rem; font-weight: 600; }
.heading-actions { display: flex; align-items: center; gap: .55rem; }.usage-link { display: inline-flex; align-items: center; gap: .38rem; border: 1px solid var(--glass-line); background: rgba(255,255,255,.58); color: #0f4c70; border-radius: .5rem; padding: .63rem .8rem; font-size: .78rem; font-weight: 750; box-shadow: 0 10px 24px rgba(30,73,94,.08), inset 0 1px 0 rgba(255,255,255,.82); backdrop-filter: blur(14px) saturate(135%); transition: .2s ease; }.usage-link:hover { border-color: rgba(125,211,252,.8); background: rgba(240,249,255,.74); transform: translateY(-1px); }.usage-link svg, .refresh-button svg { width: 1rem; height: 1rem; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }.refresh-button { display: grid; width: 2.35rem; height: 2.35rem; place-items: center; border: 1px solid var(--glass-line); border-radius: .5rem; background: rgba(255,255,255,.58); color: #475569; box-shadow: 0 10px 24px rgba(30,73,94,.08), inset 0 1px 0 rgba(255,255,255,.82); backdrop-filter: blur(14px) saturate(135%); transition: .2s ease; }.refresh-button:hover { color: #0284c7; border-color: rgba(125,211,252,.8); background: rgba(240,249,255,.74); }.refresh-button:disabled { opacity: .6; cursor: wait; }.is-spinning { animation: spin .85s linear infinite; }
.dashboard-error { margin-top: 1rem; padding: .75rem 1rem; border-radius: .5rem; background: #fff1f2; color: #be123c; font-size: .84rem; font-weight: 650; }
.overview-grid { display: grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap: .75rem; margin-top: 1.7rem; }.overview-card, .relay-card { position: relative; overflow: hidden; border: 1px solid var(--glass-line); border-radius: .5rem; background: linear-gradient(135deg, rgba(255,255,255,.74), rgba(255,255,255,.35)); box-shadow: 0 15px 34px rgba(30,73,94,.11), inset 0 1px 0 rgba(255,255,255,.86); backdrop-filter: blur(19px) saturate(145%); animation: rise .42s both; animation-delay: var(--delay); transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease; }.overview-card:hover, .relay-card:hover { transform: translateY(-3px); border-color: rgba(186,230,253,.9); box-shadow: 0 20px 40px rgba(30,73,94,.16), inset 0 1px 0 rgba(255,255,255,.9); }.overview-card { min-height: 10.5rem; padding: 1.15rem; }.overview-card > p { margin-top: 1.4rem; color: #62758a; font-size: .78rem; font-weight: 750; }.overview-card strong { display: block; margin-top: .4rem; font-size: 1.75rem; font-weight: 850; letter-spacing: 0; color: #162133; }.overview-card small, .relay-card small { display: block; margin-top: .5rem; color: #738398; font-size: .72rem; font-weight: 650; }.metric-icon { display: grid; width: 2.35rem; height: 2.35rem; place-items: center; border: 1px solid rgba(255,255,255,.68); border-radius: .5rem; box-shadow: inset 0 1px 0 rgba(255,255,255,.82); }.metric-icon svg { width: 1.2rem; height: 1.2rem; fill: none; stroke: currentColor; stroke-width: 1.85; stroke-linecap: round; stroke-linejoin: round; }.accent-sky .metric-icon { background: rgba(224,242,254,.72); color: #0369a1; }.accent-violet .metric-icon { background: rgba(237,233,254,.72); color: #6d28d9; }.accent-emerald .metric-icon, .accent-teal .metric-icon { background: rgba(209,250,229,.72); color: #047857; }.accent-amber .metric-icon { background: rgba(254,243,199,.72); color: #b45309; }.accent-rose .metric-icon, .accent-red .metric-icon { background: rgba(255,228,230,.72); color: #be123c; }
.relay-section { margin-top: 1.5rem; padding: 1.35rem; border: 1px solid var(--glass-line); border-radius: .5rem; background: rgba(248,251,253,.43); box-shadow: 0 18px 42px rgba(30,73,94,.11), inset 0 1px 0 rgba(255,255,255,.82); backdrop-filter: blur(22px) saturate(145%); }.profit-summary { display: flex; align-items: baseline; gap: .55rem; padding: .42rem .62rem; border: 1px solid rgba(255,255,255,.54); border-radius: .45rem; font-size: .75rem; font-weight: 700; box-shadow: inset 0 1px 0 rgba(255,255,255,.62); }.profit-summary strong { font-size: .92rem; }.positive { background: rgba(236,253,245,.72); color: #047857; }.negative { background: rgba(255,241,242,.74); color: #be123c; }.relay-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: .7rem; margin-top: 1.1rem; }.relay-card { min-height: 8.55rem; padding: 1rem; }.relay-card-top { display: flex; align-items: center; justify-content: space-between; color: #62758a; font-size: .76rem; font-weight: 750; }.relay-card-top svg { width: 1.1rem; height: 1.1rem; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }.relay-card strong { display: block; margin-top: 1.15rem; color: #182235; font-size: 1.35rem; font-weight: 850; letter-spacing: 0; }
.data-panel { overflow: hidden; border: 1px solid var(--glass-line); border-radius: .5rem; background: var(--glass); box-shadow: 0 18px 44px rgba(30,73,94,.12), inset 0 1px 0 rgba(255,255,255,.82); backdrop-filter: blur(21px) saturate(140%); }.channel-panel { margin-top: 1.5rem; }.table-heading { align-items: center; padding: 1.15rem 1.25rem; border-bottom: 1px solid rgba(255,255,255,.6); background: rgba(255,255,255,.15); }.table-heading p { margin-top: .28rem; color: #617388; font-size: .76rem; font-weight: 600; }.table-heading > span { border-radius: 99px; background: rgba(224,242,254,.72); color: #0369a1; padding: .36rem .55rem; font-size: .68rem; font-weight: 750; }.table-wrap { overflow-x: auto; } table { width: 100%; min-width: 950px; border-collapse: collapse; } th { padding: .75rem 1.25rem; background: rgba(248,250,252,.52); color: #607286; text-align: left; font-size: .66rem; font-weight: 800; letter-spacing: .05em; white-space: nowrap; } td { padding: .9rem 1.25rem; border-top: 1px solid rgba(255,255,255,.58); color: #405269; font-size: .78rem; font-weight: 650; white-space: nowrap; } tbody tr { transition: background .18s ease; } tbody tr:hover { background: rgba(255,255,255,.35); } td strong { display: block; color: #1e293b; font-weight: 780; } td small { display: block; margin-top: .2rem; color: #718196; font-size: .68rem; font-weight: 600; }.profit-pill { display: inline-block; border: 1px solid rgba(255,255,255,.54); border-radius: 99px; padding: .34rem .52rem; font-size: .68rem; font-weight: 800; box-shadow: inset 0 1px 0 rgba(255,255,255,.58); }.text-positive { color: #047857; }.text-negative { color: #be123c !important; }.empty-table { padding: 2.5rem; text-align: center; color: #718196; font-size: .82rem; font-weight: 700; }
.trend-board { margin-top: 1.35rem; }.trend-board-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 1rem; padding: 0 .1rem; }.trend-board-heading h2 { margin-top: .38rem; font-size: 1.16rem; }.trend-board-heading > div > p:last-child { margin-top: .35rem; color: #617388; font-size: .76rem; font-weight: 650; }.trend-window { display: flex; flex: 0 0 auto; align-items: center; gap: .58rem; border: 1px solid rgba(255,255,255,.72); border-radius: .5rem; background: rgba(255,255,255,.52); padding: .55rem .7rem; color: #0369a1; box-shadow: 0 10px 26px rgba(30,73,94,.09), inset 0 1px 0 rgba(255,255,255,.84); backdrop-filter: blur(14px) saturate(135%); }.trend-window svg { width: 1.05rem; height: 1.05rem; fill: none; stroke: currentColor; stroke-width: 1.9; stroke-linecap: round; stroke-linejoin: round; }.trend-window strong, .trend-window small { display: block; }.trend-window strong { font-size: .72rem; font-weight: 850; }.trend-window small { margin-top: .08rem; color: #718196; font-size: .58rem; font-weight: 650; }.trend-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: .85rem; margin-top: .8rem; }
@keyframes rise { from { opacity: 0; transform: translateY(9px); } to { opacity: 1; transform: translateY(0); } } @keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 1280px) { .relay-grid { grid-template-columns: repeat(3, minmax(0,1fr)); } } @media (max-width: 1100px) { .overview-grid, .relay-grid { grid-template-columns: repeat(2, minmax(0,1fr)); } } @media (max-width: 760px) { .trend-grid { grid-template-columns: 1fr; } } @media (max-width: 640px) { .dashboard-heading, .section-heading, .table-heading, .trend-board-heading { align-items: flex-start; flex-direction: column; }.overview-grid, .relay-grid { grid-template-columns: 1fr; } h1 { font-size: 1.7rem; }.overview-card { min-height: 9rem; }.relay-section { padding: 1rem; }.table-heading { padding: 1rem; }.trend-window { align-self: stretch; } }
@media (prefers-reduced-motion: reduce) { .overview-card, .relay-card, .is-spinning { animation: none !important; }.overview-card:hover, .relay-card:hover, .usage-link:hover { transform: none; } }
</style>
