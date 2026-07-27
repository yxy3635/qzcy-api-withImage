<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import Pagination from '@/components/Pagination.vue'
import AnimatedNumber from '@/components/relay/AnimatedNumber.vue'
import { adminApi } from '@/api/adminApi'
import type { AdminUserUsage } from '@/types'

const users = ref<AdminUserUsage[]>([])
const keyword = ref('')
const current = ref(1)
const pages = ref(1)
const total = ref(0)
const loading = ref(false)
const error = ref('')

const todayRevenue = computed(() => users.value.reduce((sum, user) => sum + Number(user.todayCost || 0), 0))
const todayRequests = computed(() => users.value.reduce((sum, user) => sum + Number(user.todayRequests || 0), 0))
const totalRecharge = computed(() => users.value.reduce((sum, user) => sum + Number(user.totalRecharge || 0), 0))

async function load(page = 1) {
  loading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.userUsage(page, 20, keyword.value.trim())
    users.value = data.data.records
    current.value = data.data.current
    pages.value = data.data.pages
    total.value = data.data.total
  } catch {
    error.value = '用户用量数据加载失败，请稍后重试或刷新页面'
  } finally {
    loading.value = false
  }
}

function money(value?: number, digits = 4) { return `¥ ${Number(value || 0).toFixed(digits)}` }
function compact(value?: number) {
  const amount = Number(value || 0)
  if (amount >= 1_000_000_000) return `${(amount / 1_000_000_000).toFixed(2)}B`
  if (amount >= 1_000_000) return `${(amount / 1_000_000).toFixed(2)}M`
  if (amount >= 1_000) return `${(amount / 1_000).toFixed(2)}K`
  return Math.round(amount).toLocaleString()
}
function date(value?: string) { return value ? value.replace('T', ' ').slice(0, 10) : '-' }

onMounted(load)
</script>

<template>
  <AppLayout admin wide>
    <section class="usage-page">
      <header class="usage-header">
        <div>
          <p class="eyebrow">User Intelligence</p>
          <h1>用户调用与充值</h1>
          <p>仅展示今日有调用的用户，追踪 API 消费、Token 累计和已完成充值。</p>
        </div>
        <form class="usage-search" @submit.prevent="load(1)">
          <label>
            <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5" /><path d="m16 16 4 4" /></svg>
            <input v-model="keyword" placeholder="用户名或邮箱" />
          </label>
          <button type="submit" :disabled="loading">查询</button>
        </form>
      </header>

      <div class="usage-metrics">
        <article><span>今日活跃用户</span><strong><AnimatedNumber :value="total" :format="compact" /></strong></article>
        <article><span>本页今日调用</span><strong><AnimatedNumber :value="todayRequests" :format="compact" /></strong></article>
        <article><span>本页今日消费</span><strong><AnimatedNumber :value="todayRevenue" :format="(value) => money(value, 2)" /></strong></article>
        <article><span>本页累计充值</span><strong><AnimatedNumber :value="totalRecharge" :format="(value) => money(value, 2)" /></strong></article>
      </div>

      <p v-if="error" class="usage-error">{{ error }}</p>

      <section class="usage-table-panel">
        <div class="table-caption"><div><h2>用量明细</h2><p>按今日消费降序排列，仅保留今日至少调用过一次的用户。</p></div><span v-if="loading">加载中</span></div>
        <div class="usage-table-wrap">
          <table>
            <thead><tr><th>用户</th><th>状态</th><th>今日调用</th><th>今日金额</th><th>昨日调用</th><th>昨日金额</th><th>累计 Token</th><th>累计消费</th><th>累计充值</th><th>当前余额</th><th>注册日期</th></tr></thead>
            <tbody>
              <tr v-for="user in users" :key="user.id">
                <td><div class="person"><b>{{ user.username.slice(0, 1).toUpperCase() }}</b><div><strong>{{ user.username }}</strong><small>{{ user.email || `ID ${user.id}` }}</small></div></div></td>
                <td><span class="state" :class="user.banned ? 'banned' : 'active'">{{ user.banned ? '已封禁' : user.role === 'ADMIN' ? '管理员' : '正常' }}</span></td>
                <td>{{ compact(user.todayRequests) }}</td><td class="money">{{ money(user.todayCost) }}</td><td>{{ compact(user.yesterdayRequests) }}</td><td>{{ money(user.yesterdayCost) }}</td><td>{{ compact(user.totalTokens) }}</td><td>{{ money(user.totalCost) }}</td><td class="recharge">{{ money(user.totalRecharge) }}</td><td>{{ money(user.balance) }}</td><td>{{ date(user.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="!loading && !users.length" class="usage-empty">没有匹配的用户记录</div>
        </div>
        <div class="pagination-row"><span>第 {{ current }} 页，共 {{ Math.max(pages, 1) }} 页</span><Pagination :current="current" :pages="pages" @change="load" /></div>
      </section>
    </section>
  </AppLayout>
</template>

<style scoped>
.usage-page { --glass: rgba(255,255,255,.50); --glass-strong: rgba(255,255,255,.70); --glass-line: rgba(255,255,255,.72); color: #172033; }.usage-header, .table-caption, .pagination-row { display: flex; justify-content: space-between; align-items: flex-end; gap: 1rem; }.eyebrow { color: #0284c7; font-size: .69rem; font-weight: 800; letter-spacing: .14em; }.usage-header h1 { margin: .42rem 0 0; color: #0f172a; font-size: 2rem; font-weight: 850; letter-spacing: 0; }.usage-header p:not(.eyebrow) { margin: .6rem 0 0; color: #52657a; font-size: .9rem; font-weight: 600; }.usage-search { display: flex; gap: .5rem; }.usage-search label { position: relative; }.usage-search svg { position: absolute; top: 50%; left: .75rem; width: 1rem; height: 1rem; color: #64748b; fill: none; stroke: currentColor; stroke-width: 2; transform: translateY(-50%); }.usage-search input { width: 15rem; height: 2.6rem; border: 1px solid var(--glass-line); border-radius: .5rem; background: rgba(255,255,255,.64); padding: 0 .75rem 0 2.25rem; color: #172033; font-size: .8rem; font-weight: 650; outline: none; box-shadow: 0 10px 28px rgba(39,84,107,.09), inset 0 1px 0 rgba(255,255,255,.78); backdrop-filter: blur(15px) saturate(135%); transition: .2s ease; }.usage-search input:focus { border-color: rgba(56,189,248,.7); background: rgba(255,255,255,.82); box-shadow: 0 0 0 3px rgba(186,230,253,.6), 0 10px 28px rgba(39,84,107,.09); }.usage-search button { height: 2.6rem; border: 1px solid rgba(15,23,42,.10); border-radius: .5rem; background: rgba(15,23,42,.92); color: #fff; padding: 0 .95rem; font-size: .78rem; font-weight: 800; box-shadow: 0 9px 20px rgba(15,23,42,.18); backdrop-filter: blur(12px); transition: .2s ease; }.usage-search button:hover { background: rgba(2,132,199,.94); transform: translateY(-1px); }.usage-search button:disabled { opacity: .65; }.usage-metrics { display: grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap: .75rem; margin-top: 1.6rem; }.usage-metrics article { padding: 1rem 1.1rem; border: 1px solid var(--glass-line); border-radius: .5rem; background: linear-gradient(135deg, rgba(255,255,255,.73), rgba(255,255,255,.37)); box-shadow: 0 14px 30px rgba(30,73,94,.10), inset 0 1px 0 rgba(255,255,255,.84); backdrop-filter: blur(18px) saturate(145%); transition: transform .2s ease, box-shadow .2s ease; }.usage-metrics article:hover { transform: translateY(-2px); box-shadow: 0 18px 36px rgba(30,73,94,.14), inset 0 1px 0 rgba(255,255,255,.88); }.usage-metrics span { color: #65778a; font-size: .72rem; font-weight: 750; }.usage-metrics strong { display: block; margin-top: .48rem; color: #172033; font-size: 1.35rem; font-weight: 850; letter-spacing: 0; }.usage-metrics article:nth-child(2) strong { color: #0369a1; }.usage-metrics article:nth-child(3) strong { color: #047857; }.usage-metrics article:nth-child(4) strong { color: #7c3aed; }.usage-error { margin-top: 1rem; border: 1px solid rgba(251,113,133,.24); border-radius: .5rem; background: rgba(255,241,242,.72); color: #be123c; padding: .75rem 1rem; font-size: .82rem; font-weight: 700; backdrop-filter: blur(12px); }.usage-table-panel { overflow: hidden; margin-top: 1rem; border: 1px solid var(--glass-line); border-radius: .5rem; background: var(--glass); box-shadow: 0 18px 44px rgba(30,73,94,.12), inset 0 1px 0 rgba(255,255,255,.82); backdrop-filter: blur(20px) saturate(140%); }.table-caption { padding: 1.05rem 1.25rem; border-bottom: 1px solid rgba(255,255,255,.64); background: rgba(255,255,255,.16); }.table-caption h2 { margin: 0; font-size: 1.03rem; font-weight: 800; }.table-caption p { margin: .28rem 0 0; color: #617388; font-size: .74rem; font-weight: 600; }.table-caption > span { border-radius: 99px; background: rgba(224,242,254,.74); color: #0369a1; padding: .3rem .55rem; font-size: .68rem; font-weight: 750; }.usage-table-wrap { overflow-x: auto; }.usage-table-wrap table { width: 100%; min-width: 1320px; border-collapse: collapse; }.usage-table-wrap th { padding: .72rem 1rem; background: rgba(248,250,252,.54); color: #607286; text-align: left; font-size: .65rem; font-weight: 800; letter-spacing: .05em; white-space: nowrap; }.usage-table-wrap td { padding: .84rem 1rem; border-top: 1px solid rgba(255,255,255,.58); color: #405269; font-size: .75rem; font-weight: 650; white-space: nowrap; }.usage-table-wrap tbody tr { transition: background .18s ease; }.usage-table-wrap tbody tr:hover { background: rgba(255,255,255,.38); }.person { display: flex; align-items: center; gap: .65rem; }.person > b { display: grid; width: 1.9rem; height: 1.9rem; place-items: center; border: 1px solid rgba(255,255,255,.65); border-radius: .45rem; background: rgba(224,242,254,.7); color: #0369a1; font-size: .78rem; box-shadow: inset 0 1px 0 rgba(255,255,255,.8); }.person strong, .person small { display: block; }.person strong { color: #1e293b; font-weight: 780; }.person small { margin-top: .16rem; color: #718196; font-size: .66rem; font-weight: 600; }.state { display: inline-block; border-radius: 99px; padding: .28rem .45rem; font-size: .65rem; font-weight: 800; }.active { background: rgba(209,250,229,.72); color: #047857; }.banned { background: rgba(255,228,230,.74); color: #be123c; }.money { color: #047857 !important; font-weight: 780 !important; }.recharge { color: #7c3aed !important; font-weight: 780 !important; }.usage-empty { padding: 2.5rem; color: #718196; text-align: center; font-size: .8rem; font-weight: 700; }.pagination-row { align-items: center; padding: .9rem 1.25rem; border-top: 1px solid rgba(255,255,255,.58); color: #718196; font-size: .72rem; font-weight: 650; background: rgba(255,255,255,.17); }
@media (max-width: 1050px) { .usage-metrics { grid-template-columns: repeat(2, minmax(0,1fr)); } } @media (max-width: 640px) { .usage-header { align-items: stretch; flex-direction: column; }.usage-header h1 { font-size: 1.7rem; }.usage-search, .usage-search label, .usage-search input { width: 100%; }.usage-search button { flex: 0 0 auto; }.usage-metrics { grid-template-columns: 1fr; }.table-caption { align-items: flex-start; flex-direction: column; }.pagination-row { align-items: flex-start; flex-direction: column; } }
</style>
