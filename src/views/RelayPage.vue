<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/authStore'
import { noticeApi } from '@/api/noticeApi'
import { relayApi } from '@/api/relayApi'
import { paymentApi } from '@/api/paymentApi'
import { useToast } from '@/composables/useToast'
import type { Announcement, PaymentRecord, RelayUserOverview } from '@/types'

const router = useRouter()
const auth = useAuthStore()
const toast = useToast()

const overview = ref<RelayUserOverview | null>(null)
const activeMenu = ref('dashboard')
const loading = ref(false)
const copied = ref('')
const creatingKey = ref(false)
const showKeyDialog = ref(false)
const syncingStatus = ref(false)
const keySearch = ref('')
const keyGroupFilter = ref('')
const keyStatusFilter = ref('')
const activeChartIndex = ref<number | null>(null)
const rechargeAmount = ref(10)
const rechargePreset = ref<number | 'custom'>(10)
const rechargeType = ref('alipay')
const rechargeLoading = ref(false)
const rechargeError = ref('')
const paymentRecords = ref<PaymentRecord[]>([])
const announcements = ref<Announcement[]>([])
const selectedAnnouncement = ref<Announcement | null>(null)
const paymentOptions = ref([
  { value: 'alipay', label: '支付宝', desc: '推荐使用支付宝扫码支付', enabled: true },
  { value: 'wxpay', label: '微信支付', desc: '使用微信完成余额充值', enabled: true },
  { value: 'qqpay', label: 'QQ钱包', desc: '使用 QQ 钱包支付', enabled: false }
])
const rechargePresets = [1, 5, 10, 100]
const ccSwitchDownloadUrl = 'https://image.qzcy3.top/CC-Switch-v3.16.3-Windows.msi'
const codexDownloadUrl = 'https://image.qzcy3.top/Codex%20Installer.exe'
const keyForm = reactive({
  name: '',
  group: 'default',
  ipLimitEnabled: false,
  ipWhitelist: '',
  quota: 0,
  rpmLimit: 0,
  tpmLimit: 0,
  quotaEnabled: false,
  speedLimitEnabled: false,
  expiresEnabled: false
})

const apiBase = computed(() => `${window.location.origin}/api/v1`)
const siteOrigin = computed(() => window.location.origin)
const balance = computed(() => Number(overview.value?.balance || auth.userInfo?.balance || 0))
const tokens = computed(() => overview.value?.tokens || [])
const channels = computed(() => overview.value?.channels || [])
const availableChannels = computed(() => channels.value.filter((item) => item.status === 'available').length)
const logs = computed(() => overview.value?.logs || [])
const models = computed(() => overview.value?.models || [])
const groups = computed(() => overview.value?.groups || [{ id: 0, code: 'default', name: '默认分组', ratio: 1, enabled: true }])
const trend = computed(() => overview.value?.trend || [])
const modelUsage = computed(() => overview.value?.modelUsage || [])
const activeTokens = computed(() => tokens.value.filter((item) => item.enabled))
const enabledPaymentOptions = computed(() => paymentOptions.value.filter((item) => item.enabled))
const filteredTokens = computed(() => tokens.value.filter((item) => {
  const keyword = keySearch.value.trim().toLowerCase()
  const matchesKeyword = !keyword || item.name.toLowerCase().includes(keyword) || item.tokenPreview.toLowerCase().includes(keyword)
  const matchesGroup = !keyGroupFilter.value || item.groups === keyGroupFilter.value
  const matchesStatus = !keyStatusFilter.value || (keyStatusFilter.value === 'active' ? item.enabled : !item.enabled)
  return matchesKeyword && matchesGroup && matchesStatus
}))
const totalRequests = computed(() => Number(overview.value?.totalRequests || 0))
const totalTokens = computed(() => Number(overview.value?.totalTokens || 0))
const totalCost = computed(() => Number(overview.value?.totalCost || 0))
const avgDuration = computed(() => Number(overview.value?.averageDurationMs || 0))
const promptTotal = computed(() => Number(overview.value?.totalPromptTokens || 0))
const completionTotal = computed(() => Number(overview.value?.totalCompletionTokens || 0))
const cachedTotal = computed(() => Number(overview.value?.totalCachedTokens || 0))
const cacheCreateTotal = computed(() => Number(overview.value?.totalCacheCreationTokens || 0))
const todayRequests = computed(() => Number(overview.value?.todayRequests || 0))
const todayPromptTokens = computed(() => Number(overview.value?.todayPromptTokens || 0))
const todayCompletionTokens = computed(() => Number(overview.value?.todayCompletionTokens || 0))
const todayTotalTokens = computed(() => Number(overview.value?.todayTotalTokens || 0))
const todayCost = computed(() => Number(overview.value?.todayCost || 0))
const currentRpm = computed(() => Number(overview.value?.currentRpm || 0))
const currentTpm = computed(() => Number(overview.value?.currentTpm || 0))
const chartWidth = 680
const chartHeight = 230
const todayPrefix = computed(() => new Date().toISOString().slice(0, 10))

type TrendField = 'promptTokens' | 'completionTokens' | 'cachedTokens' | 'cacheCreationTokens'
type ChartRow = {
  date: string
  requests: number
  promptTokens: number
  completionTokens: number
  cachedTokens: number
  cacheCreationTokens: number
  totalTokens: number
  cost: number
}

const menus = [
  { id: 'dashboard', label: '仪表盘', icon: 'grid' },
  { id: 'keys', label: 'API 密钥', icon: 'key' },
  { id: 'logs', label: '使用记录', icon: 'chart' },
  { id: 'channels', label: '可用渠道', icon: 'layers' },
  { id: 'models', label: '模型状态', icon: 'chart' },
  { id: 'subscription', label: '我的订阅', icon: 'card' },
  { id: 'billing', label: '充值/订阅', icon: 'coin' },
  { id: 'orders', label: '我的订单', icon: 'file' },
  { id: 'profile', label: '个人资料', icon: 'user' },
  { id: 'referral', label: '邀请返利', icon: 'gift', route: '/user/referral' }
]

const metricCards = computed(() => [
  { label: '总请求数', value: compact(totalRequests.value), sub: '当前账号累计', tone: 'blue' },
  { label: '总 Token', value: compact(totalTokens.value), sub: `输入 ${compact(promptTotal.value)} / 输出 ${compact(completionTotal.value)}`, tone: 'amber' },
  { label: '总消费', value: `$${totalCost.value.toFixed(4)}`, sub: `余额 $${balance.value.toFixed(2)}`, tone: 'emerald' },
  { label: '平均耗时', value: `${(avgDuration.value / 1000).toFixed(2)}s`, sub: '每次请求平均', tone: 'violet' }
])

const modelRows = computed(() => {
  const usageByModel = new Map(modelUsage.value.map((item) => [item.model, item]))
  return models.value.map((model) => {
    const usage = usageByModel.get(model.model)
    const latest = logs.value.find((item) => item.model === model.model)
    return {
      ...model,
      requests: Number(usage?.requests || 0),
      tokens: Number(usage?.totalTokens || 0),
      cost: Number(usage?.cost || 0),
      lastStatus: latest?.status || model.status || 'unknown'
    }
  })
})

const dashboardCards = computed(() => [
  { label: '今日消费', value: money(displayTodayCost.value), sub: `累计消费 ${money(totalCost.value)}`, tone: 'emerald', icon: 'M12 6v12m6-6H6' },
  { label: '今日请求', value: compact(displayTodayRequests.value), sub: `总请求 ${compact(totalRequests.value)}`, tone: 'blue', icon: 'M4 7h16M4 12h16M4 17h10' },
  { label: '今日输入 Token', value: compact(displayTodayPromptTokens.value), sub: `输出 ${compact(displayTodayCompletionTokens.value)}`, tone: 'amber', icon: 'M7 8l-4 4 4 4M17 8l4 4-4 4M14 4l-4 16' },
  { label: '今日总 Token', value: compact(displayTodayTotalTokens.value), sub: `累计 ${compact(totalTokens.value)}`, tone: 'violet', icon: 'M5 7h14M5 12h14M5 17h14' },
  { label: 'RPM', value: compact(currentRpm.value), sub: '最近 1 分钟请求数', tone: 'rose', icon: 'M4 13a8 8 0 1 1 16 0M12 13l4-4' },
  { label: 'TPM', value: compact(currentTpm.value), sub: '最近 1 分钟 Token', tone: 'cyan', icon: 'M13 2L4 14h7l-1 8 9-12h-7l1-8z' },
  { label: '缓存 Token', value: compact(cachedTotal.value + cacheCreateTotal.value), sub: `读 ${compact(cachedTotal.value)} / 写 ${compact(cacheCreateTotal.value)}`, tone: 'indigo', icon: 'M4 7c0-2 4-4 8-4s8 2 8 4-4 4-8 4-8-2-8-4zm0 0v10c0 2 4 4 8 4s8-2 8-4V7' },
  { label: '平均响应', value: `${(avgDuration.value / 1000).toFixed(2)}s`, sub: '按历史调用平均', tone: 'slate', icon: 'M12 8v4l3 3M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z' }
])

const chartRows = computed<ChartRow[]>(() => {
  const byDate = new Map(trend.value.map((item) => [item.date, item]))
  return Array.from({ length: 7 }, (_, index) => {
    const date = localDateKey(index - 6)
    const item = byDate.get(date)
    return {
      date,
      requests: Number(item?.requests || 0),
      promptTokens: Number(item?.promptTokens || 0),
      completionTokens: Number(item?.completionTokens || 0),
      cachedTokens: Number(item?.cachedTokens || 0),
      cacheCreationTokens: Number(item?.cacheCreationTokens || 0),
      totalTokens: Number(item?.totalTokens || 0),
      cost: Number(item?.cost || 0)
    }
  })
})

const todayTrend = computed(() => chartRows.value.find((item) => item.date === localDateKey()) || null)
const displayTodayRequests = computed(() => todayRequests.value || Number(todayTrend.value?.requests || 0))
const displayTodayPromptTokens = computed(() => todayPromptTokens.value || Number(todayTrend.value?.promptTokens || 0))
const displayTodayCompletionTokens = computed(() => todayCompletionTokens.value || Number(todayTrend.value?.completionTokens || 0))
const displayTodayTotalTokens = computed(() => todayTotalTokens.value || Number(todayTrend.value?.totalTokens || 0))
const displayTodayCost = computed(() => todayCost.value || Number(todayTrend.value?.cost || 0))

const maxTrendToken = computed(() => Math.max(
  1,
  ...chartRows.value.flatMap((item) => [
    item.promptTokens,
    item.completionTokens,
    item.cachedTokens,
    item.cacheCreationTokens
  ])
))

const recentLogs = computed(() => logs.value.slice(0, 5))
const activeChartRow = computed(() => {
  if (activeChartIndex.value === null) return null
  return chartRows.value[activeChartIndex.value] || null
})

async function load() {
  if (!auth.isAuthenticated) return
  loading.value = true
  try {
    await auth.refreshUser()
    const { data } = await relayApi.overview()
    overview.value = data.data
    if (!keyForm.group) keyForm.group = groups.value[0]?.code || 'default'
  } finally {
    loading.value = false
  }
}

async function loadPaymentConfig() {
  const { data } = await paymentApi.config()
  paymentOptions.value = paymentOptions.value.map((item) => ({
    ...item,
    enabled:
      item.value === 'alipay'
        ? Boolean(data.data.alipayEnabled)
        : item.value === 'wxpay'
          ? Boolean(data.data.wxpayEnabled)
          : Boolean(data.data.qqpayEnabled)
  }))
  if (!enabledPaymentOptions.value.some((item) => item.value === rechargeType.value)) {
    rechargeType.value = enabledPaymentOptions.value[0]?.value || 'alipay'
  }
}

async function loadPaymentHistory() {
  const { data } = await paymentApi.history(1, 8)
  paymentRecords.value = data.data.records
}

async function loadAnnouncements() {
  const { data } = await noticeApi.list()
  announcements.value = data.data
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

function yuan(value: number) {
  return `￥${Number(value || 0).toFixed(2)}`
}

function selectRechargePreset(value: number | 'custom') {
  rechargePreset.value = value
  if (typeof value === 'number') {
    rechargeAmount.value = value
  }
}

function paymentTypeText(value: string) {
  if (value === 'alipay') return '支付宝'
  if (value === 'wxpay') return '微信支付'
  if (value === 'qqpay') return 'QQ钱包'
  if (value === 'referral_rebate') return '邀请返利'
  if (value === 'balance') return '余额扣费'
  return value || '-'
}

function paymentStatusText(value: string) {
  if (value === 'completed') return '已完成'
  if (value === 'pending') return '待支付'
  if (value === 'failed') return '失败'
  return value || '-'
}

async function createRechargeOrder() {
  rechargeError.value = ''
  if (!Number.isFinite(Number(rechargeAmount.value)) || Number(rechargeAmount.value) <= 0) {
    rechargeError.value = '充值金额必须大于 0'
    toast.warning(rechargeError.value)
    return
  }
  rechargeLoading.value = true
  try {
    const { data } = await paymentApi.recharge(Number(rechargeAmount.value), rechargeType.value)
    const paymentUrl = data.data.paymentUrl ? String(data.data.paymentUrl) : ''
    if (paymentUrl) {
      window.location.href = paymentUrl
      return
    }
    toast.success(String(data.data.message || '支付订单已创建'))
    await Promise.all([auth.refreshUser(), loadPaymentHistory(), load()])
  } catch (err) {
    rechargeError.value = err instanceof Error ? err.message : '创建支付订单失败'
    toast.error(rechargeError.value)
  } finally {
    rechargeLoading.value = false
  }
}

function localDateKey(offsetDays = 0) {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function chartPoint(index: number, value: number) {
  const x = 24 + index * ((chartWidth - 48) / Math.max(1, chartRows.value.length - 1))
  const y = chartHeight - 28 - (Number(value || 0) / maxTrendToken.value) * (chartHeight - 64)
  return { x: Number(x.toFixed(2)), y: Number(y.toFixed(2)) }
}

function curvePath(field: TrendField) {
  const points = chartRows.value.map((item, index) => chartPoint(index, item[field]))
  if (!points.length) return ''
  return points.reduce((path, point, index) => {
    if (index === 0) return `M ${point.x} ${point.y}`
    const prev = points[index - 1] || point
    const distance = (point.x - prev.x) / 2
    return `${path} C ${prev.x + distance} ${prev.y}, ${point.x - distance} ${point.y}, ${point.x} ${point.y}`
  }, '')
}

function areaPath(field: TrendField) {
  if (!chartRows.value.length) return ''
  const baseline = chartHeight - 28
  const firstRow = chartRows.value[0] as ChartRow
  const lastRow = chartRows.value[chartRows.value.length - 1] as ChartRow
  const first = chartPoint(0, firstRow[field])
  const last = chartPoint(chartRows.value.length - 1, lastRow[field])
  return `${curvePath(field)} L ${last.x} ${baseline} L ${first.x} ${baseline} Z`
}

function toneIconClass(tone: string) {
  const classes: Record<string, string> = {
    emerald: 'bg-emerald-50 text-emerald-700 ring-emerald-100',
    blue: 'bg-blue-50 text-blue-700 ring-blue-100',
    amber: 'bg-amber-50 text-amber-700 ring-amber-100',
    violet: 'bg-violet-50 text-violet-700 ring-violet-100',
    rose: 'bg-rose-50 text-rose-700 ring-rose-100',
    cyan: 'bg-cyan-50 text-cyan-700 ring-cyan-100',
    indigo: 'bg-indigo-50 text-indigo-700 ring-indigo-100',
    slate: 'bg-slate-100 text-slate-700 ring-slate-200'
  }
  return classes[tone] || classes.slate
}

function toneTextClass(tone: string) {
  const classes: Record<string, string> = {
    emerald: 'text-emerald-600',
    blue: 'text-blue-600',
    amber: 'text-amber-600',
    violet: 'text-violet-600',
    rose: 'text-rose-600',
    cyan: 'text-cyan-600',
    indigo: 'text-indigo-600',
    slate: 'text-slate-700'
  }
  return classes[tone] || classes.slate
}

async function copyText(value: string, key: string) {
  await window.navigator.clipboard?.writeText(value)
  copied.value = key
  window.setTimeout(() => {
    copied.value = ''
  }, 1200)
}

function openKeyDialog() {
  keyForm.name = ''
  keyForm.group = groups.value[0]?.code || 'default'
  keyForm.ipLimitEnabled = false
  keyForm.ipWhitelist = ''
  keyForm.quota = 0
  keyForm.rpmLimit = 0
  keyForm.tpmLimit = 0
  keyForm.quotaEnabled = false
  keyForm.speedLimitEnabled = false
  keyForm.expiresEnabled = false
  showKeyDialog.value = true
}

async function createKey() {
  creatingKey.value = true
  try {
    await relayApi.createToken({
      name: keyForm.name || '我的 API 密钥',
      groups: keyForm.group,
      quota: keyForm.quotaEnabled ? keyForm.quota : 0,
      rpmLimit: keyForm.speedLimitEnabled ? keyForm.rpmLimit : 0,
      tpmLimit: keyForm.speedLimitEnabled ? keyForm.tpmLimit : 0,
      ipWhitelist: keyForm.ipLimitEnabled ? keyForm.ipWhitelist : ''
    })
    await load()
    showKeyDialog.value = false
    toast.success('密钥创建成功')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '密钥创建失败')
  } finally {
    creatingKey.value = false
  }
}

async function toggleKey(item: { id: number; enabled: boolean }) {
  await relayApi.updateToken(item.id, { enabled: !item.enabled })
  await load()
}

function groupOf(code: string) {
  return groups.value.find((item) => item.code === code)
}

async function deleteKey(id: number) {
  if (!window.confirm('确定删除这个 API 密钥吗？删除后无法恢复。')) return
  try {
    await relayApi.deleteToken(id)
    await load()
    toast.success('密钥已删除')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '删除密钥失败')
  }
}

async function syncStatus() {
  syncingStatus.value = true
  try {
    await relayApi.syncChannelStatus()
    await load()
    toast.success('渠道状态已同步')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '同步渠道状态失败')
  } finally {
    syncingStatus.value = false
  }
}

function statusText(status: string) {
  if (status === 'available' || status === 'success') return '可用'
  if (status === 'failed') return '异常'
  return '未知'
}

function selectMenu(item: { id: string; route?: string }) {
  if (item.route) {
    router.push(item.route)
    return
  }
  activeMenu.value = item.id
}

onMounted(async () => {
  await load()
  if (auth.isAuthenticated) {
    await Promise.all([loadPaymentConfig(), loadPaymentHistory(), loadAnnouncements()])
  }
})
</script>

<template>
  <div class="min-h-screen bg-white text-slate-950">
    <aside class="fixed inset-y-0 left-0 z-30 hidden w-72 border-r border-slate-100 bg-white md:flex md:flex-col">
      <button class="flex h-20 items-center gap-3 px-6 text-left" @click="router.push('/')">
        <img class="h-10 w-10 rounded-xl" src="/favicon.ico" alt="logo" />
        <span class="text-xl font-black">imageCreater API</span>
      </button>
      <nav class="flex-1 space-y-1 px-3">
        <button
          v-for="item in menus"
          :key="item.id"
          class="flex h-11 w-full items-center gap-3 rounded-lg px-4 text-left text-sm font-bold transition"
          :class="activeMenu === item.id ? 'bg-emerald-50 text-emerald-700' : 'text-slate-600 hover:bg-slate-50 hover:text-slate-950'"
          @click="selectMenu(item)"
        >
          <span class="h-2.5 w-2.5 rounded-full" :class="activeMenu === item.id ? 'bg-emerald-500' : 'bg-slate-300'"></span>
          {{ item.label }}
        </button>
      </nav>
    </aside>

    <div class="md:pl-72">
      <header class="sticky top-0 z-20 flex min-h-20 flex-wrap items-center justify-between gap-4 border-b border-slate-100 bg-white/90 px-4 backdrop-blur-xl md:px-8">
        <div>
          <h1 class="text-2xl font-black">{{ menus.find((item) => item.id === activeMenu)?.label }}</h1>
          <p class="mt-1 hidden text-sm font-semibold text-slate-500 md:block">独立 API 中转站，账号、余额与原项目互通。</p>
        </div>
        <div class="flex items-center gap-3">
          <button class="rounded-full bg-emerald-50 px-4 py-2 text-sm font-black text-emerald-700">${{ balance.toFixed(2) }}</button>
          <button class="rounded-full border border-slate-200 px-4 py-2 text-sm font-black text-slate-700" @click="load">{{ loading ? '刷新中' : '刷新' }}</button>
          <button class="grid h-10 w-10 place-items-center rounded-full bg-gradient-to-br from-pink-100 to-sky-100 text-sm font-black">{{ auth.userInfo?.username?.slice(0, 1).toUpperCase() || 'U' }}</button>
        </div>
      </header>

      <main class="min-h-[calc(100vh-80px)] bg-[linear-gradient(#e8edf3_1px,transparent_1px),linear-gradient(90deg,#e8edf3_1px,transparent_1px)] bg-[size:32px_32px] px-4 py-8 md:px-10">
        <div v-if="!auth.isAuthenticated" class="mx-auto max-w-xl rounded-2xl border border-amber-100 bg-amber-50 p-6 text-center">
          <p class="text-lg font-black text-amber-800">登录后查看中转站</p>
          <button class="mt-4 rounded-xl bg-slate-950 px-5 py-3 text-sm font-black text-white" @click="router.push('/login')">登录账号</button>
        </div>

        <template v-else>
          <section v-if="activeMenu === 'dashboard'" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <article
              v-for="card in dashboardCards"
              :key="card.label"
              class="group rounded-2xl border border-slate-100 bg-white p-5 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:border-slate-200 hover:shadow-xl hover:shadow-slate-200/60"
            >
              <div class="flex items-start justify-between gap-4">
                <div>
                  <p class="text-sm font-bold text-slate-500">{{ card.label }}</p>
                  <p class="mt-2 text-3xl font-black tracking-normal" :class="toneTextClass(card.tone)">{{ card.value }}</p>
                </div>
                <span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl ring-1 transition group-hover:scale-105" :class="toneIconClass(card.tone)">
                  <svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path :d="card.icon" />
                  </svg>
                </span>
              </div>
              <p class="mt-3 text-xs font-semibold text-slate-500">{{ card.sub }}</p>
            </article>
          </section>

          <section v-if="activeMenu === 'dashboard'" class="mt-6 rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
            <div class="flex items-center justify-between gap-3">
              <div>
                <p class="text-sm font-bold uppercase tracking-[0.18em] text-emerald-600">公告</p>
                <h2 class="mt-2 text-xl font-black text-slate-950">最新通知</h2>
              </div>
              <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-500">{{ announcements.length }} 条</span>
            </div>
            <div class="mt-5 grid gap-3">
              <button
                v-for="item in announcements"
                :key="item.id"
                class="rounded-2xl border border-slate-100 bg-slate-50 p-4 text-left transition hover:border-emerald-200 hover:bg-emerald-50/60"
                @click="selectedAnnouncement = item"
              >
                <div class="flex items-center justify-between gap-3">
                  <p class="text-base font-black text-slate-950">{{ item.title }}</p>
                  <span class="shrink-0 text-xs font-semibold text-slate-400">{{ (item.publishedAt || item.createdAt || '').slice(0, 16).replace('T', ' ') }}</span>
                </div>
                <p class="mt-2 line-clamp-2 text-sm font-semibold leading-6 text-slate-600">{{ item.content }}</p>
              </button>
              <div v-if="!announcements.length" class="rounded-2xl border border-dashed border-slate-200 p-8 text-center text-sm font-black text-slate-500">暂无公告</div>
            </div>
          </section>

          <section v-if="activeMenu === 'dashboard'" class="mt-6 grid gap-6 xl:grid-cols-[0.92fr_1.08fr]">
            <div class="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
              <div class="flex items-center justify-between gap-3">
                <div>
                  <h2 class="text-lg font-black">模型分布</h2>
                  <p class="mt-1 text-xs font-bold text-slate-500">按真实调用日志聚合</p>
                </div>
                <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-600">{{ modelUsage.length }} 个模型</span>
              </div>
              <div class="mt-5 space-y-4">
                <div v-for="row in modelRows.slice(0, 8)" :key="row.model" class="rounded-xl border border-slate-100 p-3 transition hover:border-emerald-100 hover:bg-emerald-50/40">
                  <div class="flex items-center justify-between gap-3 text-sm font-bold">
                    <span class="truncate text-slate-900">{{ row.model }}</span>
                    <span class="shrink-0 text-slate-500">{{ row.requests }} 次</span>
                  </div>
                  <div class="mt-2 flex items-center gap-3">
                    <div class="h-2 flex-1 rounded-full bg-slate-100">
                      <div class="h-2 rounded-full bg-gradient-to-r from-emerald-500 to-cyan-500" :style="{ width: `${Math.min(100, (row.tokens / Math.max(1, totalTokens)) * 100)}%` }"></div>
                    </div>
                    <span class="w-20 text-right text-xs font-black text-slate-600">{{ compact(row.tokens) }}</span>
                  </div>
                  <div class="mt-2 flex justify-between text-[11px] font-bold text-slate-400">
                    <span>{{ statusText(row.lastStatus) }}</span>
                    <span>{{ money(row.cost) }}</span>
                  </div>
                </div>
                <div v-if="!modelRows.length" class="grid h-40 place-items-center rounded-xl border border-dashed border-slate-200 text-sm font-black text-slate-400">暂无模型调用数据</div>
              </div>
            </div>

            <div class="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
              <div class="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h2 class="text-lg font-black">Token 使用曲线</h2>
                  <p class="mt-1 text-xs font-bold text-slate-500">近 7 天输入、输出与缓存 token</p>
                </div>
                <button class="rounded-xl border border-slate-200 px-3 py-2 text-xs font-black text-slate-600 transition hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-700" @click="load">
                  {{ loading ? '更新中' : '实时刷新' }}
                </button>
              </div>
              <div class="relative mt-5 overflow-hidden rounded-xl border border-slate-100 bg-[linear-gradient(180deg,#f8fafc_0%,#ffffff_100%)] p-3" @mouseleave="activeChartIndex = null">
                <svg class="h-72 w-full" :viewBox="`0 0 ${chartWidth} ${chartHeight}`" preserveAspectRatio="none" role="img" aria-label="近 7 天 token 使用曲线">
                  <defs>
                    <linearGradient id="promptArea" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stop-color="#2563eb" stop-opacity="0.18" />
                      <stop offset="100%" stop-color="#2563eb" stop-opacity="0" />
                    </linearGradient>
                  </defs>
                  <g stroke="#e2e8f0" stroke-width="1">
                    <line v-for="tick in [0, 1, 2, 3]" :key="tick" x1="20" :y1="36 + tick * 48" :x2="chartWidth - 20" :y2="36 + tick * 48" />
                  </g>
                  <path :d="areaPath('promptTokens')" fill="url(#promptArea)" />
                  <path :d="curvePath('promptTokens')" fill="none" stroke="#2563eb" stroke-width="3.5" stroke-linecap="round" />
                  <path :d="curvePath('completionTokens')" fill="none" stroke="#10b981" stroke-width="3.5" stroke-linecap="round" />
                  <path :d="curvePath('cachedTokens')" fill="none" stroke="#06b6d4" stroke-width="2.8" stroke-linecap="round" stroke-dasharray="8 7" />
                  <path :d="curvePath('cacheCreationTokens')" fill="none" stroke="#f59e0b" stroke-width="2.8" stroke-linecap="round" stroke-dasharray="4 7" />
                  <g v-for="(item, index) in chartRows" :key="item.date" @mouseenter="activeChartIndex = index" @mousemove="activeChartIndex = index">
                    <rect :x="Math.max(0, chartPoint(index, 0).x - 36)" y="20" width="72" :height="chartHeight - 36" fill="transparent" class="cursor-crosshair" />
                    <line :x1="chartPoint(index, 0).x" y1="28" :x2="chartPoint(index, 0).x" :y2="chartHeight - 24" :stroke="activeChartIndex === index ? '#94a3b8' : '#e2e8f0'" stroke-width="1" stroke-dasharray="3 8" />
                    <circle :cx="chartPoint(index, item.promptTokens).x" :cy="chartPoint(index, item.promptTokens).y" :r="activeChartIndex === index ? 6 : 4" fill="#2563eb" class="transition-all" />
                    <circle :cx="chartPoint(index, item.completionTokens).x" :cy="chartPoint(index, item.completionTokens).y" :r="activeChartIndex === index ? 5 : 3.5" fill="#10b981" class="transition-all" />
                    <circle :cx="chartPoint(index, item.cachedTokens).x" :cy="chartPoint(index, item.cachedTokens).y" :r="activeChartIndex === index ? 4.5 : 3" fill="#06b6d4" class="transition-all" />
                    <circle :cx="chartPoint(index, item.cacheCreationTokens).x" :cy="chartPoint(index, item.cacheCreationTokens).y" :r="activeChartIndex === index ? 4.5 : 3" fill="#f59e0b" class="transition-all" />
                    <text :x="chartPoint(index, 0).x" :y="chartHeight - 7" text-anchor="middle" class="fill-slate-400 text-[11px] font-bold">{{ item.date.slice(5) }}</text>
                  </g>
                </svg>
                <div
                  v-if="activeChartRow"
                  class="pointer-events-none absolute top-6 z-10 w-56 rounded-2xl border border-slate-100 bg-white/95 p-4 text-xs font-bold text-slate-600 shadow-2xl shadow-slate-200/80 backdrop-blur"
                  :style="{ left: `${Math.min(100, Math.max(0, ((chartPoint(activeChartIndex || 0, 0).x / chartWidth) * 100)))}%`, transform: (activeChartIndex || 0) > 4 ? 'translateX(-100%)' : 'translateX(0)' }"
                >
                  <div class="flex items-center justify-between gap-3">
                    <p class="text-sm font-black text-slate-950">{{ activeChartRow.date }}</p>
                    <p class="rounded-full bg-slate-100 px-2 py-1 text-[11px] text-slate-500">{{ activeChartRow.requests }} 次</p>
                  </div>
                  <div class="mt-3 space-y-2">
                    <p class="flex justify-between"><span class="text-blue-600">输入</span><span>{{ compact(activeChartRow.promptTokens) }}</span></p>
                    <p class="flex justify-between"><span class="text-emerald-600">输出</span><span>{{ compact(activeChartRow.completionTokens) }}</span></p>
                    <p class="flex justify-between"><span class="text-cyan-600">缓存读</span><span>{{ compact(activeChartRow.cachedTokens) }}</span></p>
                    <p class="flex justify-between"><span class="text-amber-600">缓存写</span><span>{{ compact(activeChartRow.cacheCreationTokens) }}</span></p>
                    <p class="flex justify-between border-t border-slate-100 pt-2 text-slate-950"><span>总 Token</span><span>{{ compact(activeChartRow.totalTokens) }}</span></p>
                    <p class="flex justify-between text-slate-950"><span>消费</span><span>{{ money(activeChartRow.cost) }}</span></p>
                  </div>
                </div>
              </div>
              <div class="mt-4 grid gap-3 text-xs font-bold text-slate-600 sm:grid-cols-4">
                <span class="flex items-center gap-2"><i class="h-2.5 w-2.5 rounded-full bg-blue-600"></i>输入 {{ compact(promptTotal) }}</span>
                <span class="flex items-center gap-2"><i class="h-2.5 w-2.5 rounded-full bg-emerald-500"></i>输出 {{ compact(completionTotal) }}</span>
                <span class="flex items-center gap-2"><i class="h-2.5 w-2.5 rounded-full bg-cyan-500"></i>缓存读 {{ compact(cachedTotal) }}</span>
                <span class="flex items-center gap-2"><i class="h-2.5 w-2.5 rounded-full bg-amber-500"></i>缓存写 {{ compact(cacheCreateTotal) }}</span>
              </div>
            </div>
          </section>

          <section v-if="activeMenu === 'dashboard'" class="mt-6 grid gap-6 xl:grid-cols-[1.08fr_0.92fr]">
            <div class="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
              <div class="flex items-center justify-between gap-3">
                <h2 class="text-lg font-black">最近调用</h2>
                <span class="text-xs font-black text-slate-400">实时日志</span>
              </div>
              <div class="mt-4 divide-y divide-slate-100">
                <div v-for="item in recentLogs" :key="item.id" class="grid gap-3 py-3 text-sm font-semibold text-slate-600 md:grid-cols-[1fr_auto_auto_auto] md:items-center">
                  <div class="min-w-0">
                    <p class="truncate font-black text-slate-900">{{ item.model }}</p>
                    <p class="mt-1 truncate text-xs text-slate-400">{{ item.tokenName }} · {{ item.createdAt }}</p>
                  </div>
                  <span class="text-xs font-black" :class="item.status === 'success' ? 'text-emerald-600' : 'text-rose-600'">{{ statusText(item.status) }}</span>
                  <span class="text-xs font-black text-slate-500">{{ compact(item.totalTokens) }} token</span>
                  <span class="text-xs font-black text-slate-900">{{ money(item.cost) }}</span>
                </div>
                <div v-if="!recentLogs.length" class="grid h-32 place-items-center text-sm font-black text-slate-400">暂无调用日志</div>
              </div>
            </div>

            <div class="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2 class="text-lg font-black">API 地址</h2>
                  <p class="mt-2 break-all rounded-xl bg-slate-50 px-3 py-2 font-mono text-sm font-semibold text-slate-600">{{ apiBase }}</p>
                </div>
                <button class="rounded-xl bg-slate-950 px-5 py-3 text-sm font-black text-white transition hover:bg-emerald-700" @click="copyText(apiBase, 'base')">{{ copied === 'base' ? '已复制' : '一键复制' }}</button>
              </div>
              <div class="mt-5 grid grid-cols-2 gap-3">
                <div class="rounded-xl bg-slate-50 p-3">
                  <p class="text-xs font-bold text-slate-400">可用渠道</p>
                  <p class="mt-1 text-2xl font-black text-slate-900">{{ availableChannels }}</p>
                </div>
                <div class="rounded-xl bg-slate-50 p-3">
                  <p class="text-xs font-bold text-slate-400">启用密钥</p>
                  <p class="mt-1 text-2xl font-black text-slate-900">{{ activeTokens.length }}</p>
                </div>
              </div>
            </div>
          </section>

          <section v-if="activeMenu === 'keys'" class="space-y-4">
            <div class="flex flex-wrap items-center justify-between gap-4">
              <div class="flex flex-wrap gap-3">
                <input v-model="keySearch" class="h-12 w-72 rounded-2xl border border-slate-200 bg-white px-4 text-sm font-semibold outline-none focus:border-teal-300" placeholder="搜索名称或 Key..." />
                <select v-model="keyGroupFilter" class="h-12 rounded-2xl border border-slate-200 bg-white px-4 text-sm font-bold text-slate-700 outline-none focus:border-teal-300">
                  <option value="">全部分组</option>
                  <option v-for="group in groups" :key="group.code" :value="group.code">{{ group.name }}</option>
                </select>
                <select v-model="keyStatusFilter" class="h-12 rounded-2xl border border-slate-200 bg-white px-4 text-sm font-bold text-slate-700 outline-none focus:border-teal-300">
                  <option value="">全部状态</option>
                  <option value="active">活跃</option>
                  <option value="disabled">禁用</option>
                </select>
              </div>
              <div class="flex gap-3">
                <button class="h-12 rounded-2xl border border-slate-200 bg-white px-4 text-sm font-black text-slate-700" @click="load">刷新</button>
                <button class="h-12 rounded-2xl bg-teal-600 px-5 text-sm font-black text-white shadow-sm shadow-teal-100" @click="openKeyDialog">+ 创建密钥</button>
              </div>
            </div>

            <div class="flex flex-wrap gap-2">
              <button class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-bold text-slate-600" @click="copyText(apiBase, 'api-base')">
                API 端点 <span class="text-emerald-600">默认</span> | {{ apiBase }} {{ copied === 'api-base' ? '已复制' : '' }}
              </button>
              <button class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-bold text-slate-600" @click="copyText(siteOrigin, 'site-url')">
                地址 | {{ siteOrigin }} {{ copied === 'site-url' ? '已复制' : '' }}
              </button>
            </div>

            <div class="grid gap-4 xl:grid-cols-[0.82fr_1.18fr]">
              <section class="rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm">
                <div class="flex flex-wrap items-start justify-between gap-4">
                  <p class="text-sm font-bold text-emerald-600">软件下载</p>
                </div>
                <div class="mt-4 grid gap-3">
                  <div class="rounded-xl bg-slate-50 p-3">
                    <div class="flex flex-wrap items-center justify-between gap-3">
                      <p class="text-sm font-black text-slate-700">CC Switch</p>
                      <a class="rounded-xl bg-emerald-600 px-4 py-2 text-xs font-black text-white transition hover:bg-emerald-700" :href="ccSwitchDownloadUrl" download>下载</a>
                    </div>
                    <button class="mt-2 break-all text-left font-mono text-xs font-bold text-slate-500 transition hover:text-emerald-700" @click="copyText(ccSwitchDownloadUrl, 'cc-switch-download')">
                      {{ ccSwitchDownloadUrl }} {{ copied === 'cc-switch-download' ? '已复制' : '' }}
                    </button>
                  </div>
                  <div class="rounded-xl bg-slate-50 p-3">
                    <div class="flex flex-wrap items-center justify-between gap-3">
                      <p class="text-sm font-black text-slate-700">Codex Installer</p>
                      <a class="rounded-xl bg-slate-950 px-4 py-2 text-xs font-black text-white transition hover:bg-emerald-700" :href="codexDownloadUrl" download>下载</a>
                    </div>
                    <button class="mt-2 break-all text-left font-mono text-xs font-bold text-slate-500 transition hover:text-emerald-700" @click="copyText(codexDownloadUrl, 'codex-download')">
                      {{ codexDownloadUrl }} {{ copied === 'codex-download' ? '已复制' : '' }}
                    </button>
                  </div>
                </div>
              </section>

              <section class="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
                <div class="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <p class="text-sm font-bold text-slate-500">使用教程</p>
                    <h2 class="mt-1 text-xl font-black text-slate-950">三步接入 API 中转站</h2>
                  </div>
                  <button class="rounded-xl border border-slate-200 px-3 py-2 text-xs font-black text-slate-600 transition hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-700" @click="copyText(apiBase, 'tutorial-api-base')">
                    {{ copied === 'tutorial-api-base' ? '已复制' : '复制 API 地址' }}
                  </button>
                </div>
                <div class="mt-4 grid gap-3 md:grid-cols-3">
                  <article class="rounded-xl bg-slate-50 p-4">
                    <span class="grid h-8 w-8 place-items-center rounded-full bg-emerald-100 text-sm font-black text-emerald-700">1</span>
                    <p class="mt-3 text-sm font-black text-slate-900">创建或复制密钥</p>
                    <p class="mt-1 text-xs font-semibold text-slate-500">在下方列表点击“复制密钥”，复制当前账号的完整 API Key。</p>
                  </article>
                  <article class="rounded-xl bg-slate-50 p-4">
                    <span class="grid h-8 w-8 place-items-center rounded-full bg-blue-100 text-sm font-black text-blue-700">2</span>
                    <p class="mt-3 text-sm font-black text-slate-900">填写 API 地址</p>
                    <p class="mt-1 break-all text-xs font-semibold text-slate-500">{{ apiBase }}</p>
                  </article>
                  <article class="rounded-xl bg-slate-50 p-4">
                    <span class="grid h-8 w-8 place-items-center rounded-full bg-amber-100 text-sm font-black text-amber-700">3</span>
                    <p class="mt-3 text-sm font-black text-slate-900">选择模型并使用</p>
                    <p class="mt-1 text-xs font-semibold text-slate-500">客户端或兼容 OpenAI 的工具里选择可用模型，余额会按实际用量扣费。</p>
                  </article>
                </div>
              </section>
            </div>

            <div class="overflow-x-auto rounded-2xl border border-slate-100 bg-white shadow-sm">
              <table class="w-full min-w-[1280px] text-left text-sm">
                <thead class="bg-slate-50 text-xs font-black text-slate-500">
                  <tr>
                    <th class="px-5 py-4">名称</th>
                    <th>API 密钥</th>
                    <th>分组</th>
                    <th>用量</th>
                    <th>速率限制</th>
                    <th>过期时间</th>
                    <th>状态</th>
                    <th>上次使用时间</th>
                    <th>创建时间</th>
                    <th class="text-right pr-5">操作</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-100">
                  <tr v-for="item in filteredTokens" :key="item.id" class="font-semibold text-slate-700">
                    <td class="px-5 py-5 text-slate-950 font-black">{{ item.name }}</td>
                    <td>
                      <div class="flex items-center gap-2">
                        <span class="rounded bg-slate-50 px-2 py-1 font-mono text-xs font-black text-teal-600">{{ item.tokenPreview }}</span>
                        <button class="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-black text-slate-500 transition hover:border-teal-200 hover:bg-teal-50 hover:text-teal-700" @click="copyText(item.plainToken || item.tokenPreview, `token-${item.id}`)">
                          {{ copied === `token-${item.id}` ? '已复制' : '复制密钥' }}
                        </button>
                      </div>
                    </td>
                    <td>
                      <span class="inline-flex items-center gap-2 rounded-lg bg-emerald-50 px-2.5 py-1 text-xs font-black text-emerald-700">
                        {{ groupOf(item.groups)?.name || item.groups }}
                        <b>{{ Number(groupOf(item.groups)?.ratio || 1).toFixed(3) }}x</b>
                      </span>
                    </td>
                    <td>
                      <p>今日：{{ money(Number(item.todayCost || 0)) }}</p>
                      <p class="text-slate-500">累计：{{ money(Number(item.usedQuota || 0)) }}</p>
                    </td>
                    <td>{{ item.rpmLimit || 0 }} RPM / {{ item.tpmLimit || 0 }} TPM</td>
                    <td>{{ item.expiresAt ? item.expiresAt.replace('T', ' ').slice(0, 16) : '永久有效' }}</td>
                    <td><span class="rounded-full px-3 py-1 text-xs font-black" :class="item.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'">{{ item.enabled ? '活跃' : '禁用' }}</span></td>
                    <td>{{ item.lastUsedAt ? item.lastUsedAt.replace('T', ' ').slice(0, 19) : '-' }}</td>
                    <td>{{ item.createdAt ? item.createdAt.replace('T', ' ').slice(0, 19) : '-' }}</td>
                    <td class="pr-5">
                      <div class="flex justify-end gap-3 text-xs font-black text-slate-500">
                        <button class="hover:text-teal-600" @click="copyText(item.plainToken || item.tokenPreview, `token-use-${item.id}`)">{{ copied === `token-use-${item.id}` ? '已复制' : '使用密钥' }}</button>
                        <button class="hover:text-amber-600" @click="toggleKey(item)">{{ item.enabled ? '禁用' : '启用' }}</button>
                        <button class="hover:text-red-600" @click="deleteKey(item.id)">删除</button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div v-if="!filteredTokens.length" class="p-10 text-center text-sm font-black text-slate-500">暂无密钥</div>
            </div>
          </section>

          <section v-if="activeMenu === 'logs'" class="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
            <div class="flex flex-wrap items-center justify-between gap-4">
              <h2 class="text-xl font-black">使用记录</h2>
              <div class="flex gap-2">
                <button class="rounded-xl border border-slate-200 px-4 py-2 text-sm font-black text-slate-700" @click="load">刷新</button>
                <button class="rounded-xl bg-emerald-600 px-4 py-2 text-sm font-black text-white">导出 CSV</button>
              </div>
            </div>
            <div class="mt-5 overflow-x-auto">
              <table class="w-full min-w-[1180px] text-left text-sm">
                <thead class="text-xs font-black text-slate-400">
                  <tr><th class="py-3">API 密钥</th><th>模型</th><th>端点</th><th>类型</th><th>Token</th><th>费用</th><th>耗时</th><th>时间</th><th>User-Agent</th></tr>
                </thead>
                <tbody class="divide-y divide-slate-100 font-semibold">
                  <tr v-for="log in logs" :key="log.id">
                    <td class="py-4">{{ log.tokenName || '-' }}</td>
                    <td>{{ log.model }}</td>
                    <td>{{ log.endpoint }}</td>
                    <td><span class="rounded bg-blue-50 px-2 py-1 text-xs font-black text-blue-600">{{ log.modelType }}</span></td>
                    <td>
                      <p><span class="text-emerald-600">↓ {{ compact(log.promptTokens || 0) }}</span> <span class="text-violet-600">↑ {{ compact(log.completionTokens || 0) }}</span></p>
                      <p class="text-xs text-cyan-600">缓存读 {{ compact(log.cachedTokens || 0) }} · 缓存建 {{ compact(log.cacheCreationTokens || 0) }}</p>
                    </td>
                    <td>
                      <p class="font-black text-emerald-600">{{ money(log.cost) }}</p>
                      <p class="text-xs text-slate-500">分组倍率 {{ Number(log.groupRatio || 1).toFixed(3) }}x</p>
                    </td>
                    <td>{{ ((log.durationMs || 0) / 1000).toFixed(2) }}s</td>
                    <td>{{ log.createdAt?.replace('T', ' ').slice(0, 19) }}</td>
                    <td class="max-w-[280px] truncate text-slate-500">{{ log.userAgent || '-' }}</td>
                  </tr>
                </tbody>
              </table>
              <div v-if="!logs.length" class="rounded-xl border border-dashed border-slate-200 p-10 text-center text-sm font-black text-slate-500">暂无使用记录</div>
            </div>
          </section>

          <section v-if="activeMenu === 'channels'" class="space-y-4">
            <div class="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
              <div>
                <h2 class="text-lg font-black text-slate-950">渠道状态</h2>
                <p class="mt-1 text-sm font-semibold text-slate-500">当前可用 {{ availableChannels }} / {{ channels.length }}</p>
              </div>
              <button
                class="inline-flex h-11 items-center gap-3 rounded-xl bg-slate-950 px-5 text-sm font-black text-white transition hover:bg-emerald-600 disabled:cursor-not-allowed disabled:opacity-80"
                :disabled="syncingStatus"
                @click="syncStatus"
              >
                <span
                  class="h-4 w-4 rounded-full border-2 border-white/40 border-t-white"
                  :class="syncingStatus ? 'animate-spin' : 'hidden'"
                ></span>
                {{ syncingStatus ? '正在检测渠道' : '检测可用状态' }}
              </button>
            </div>
            <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              <article
                v-for="channel in channels"
                :key="channel.id"
                class="rounded-2xl border bg-white p-5 shadow-sm transition"
                :class="channel.status === 'available' ? 'border-emerald-100' : 'border-slate-100'"
              >
                <div class="flex items-center justify-between gap-4">
                  <h2 class="min-w-0 truncate text-lg font-black text-slate-950">{{ channel.name }}</h2>
                  <span
                    class="inline-flex shrink-0 items-center gap-2 rounded-full px-3 py-1 text-xs font-black"
                    :class="channel.status === 'available' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'"
                  >
                    <span
                      class="h-2 w-2 rounded-full"
                      :class="channel.status === 'available' ? 'bg-emerald-500' : 'bg-amber-500'"
                    ></span>
                    {{ statusText(channel.status) }}
                  </span>
                </div>
                <div v-if="syncingStatus" class="mt-4 h-1.5 overflow-hidden rounded-full bg-slate-100">
                  <div class="h-full w-1/3 animate-[channelScan_1.1s_ease-in-out_infinite] rounded-full bg-emerald-500"></div>
                </div>
              </article>
            </div>
          </section>

          <section v-if="activeMenu === 'models'" class="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
            <h2 class="text-xl font-black">模型状态</h2>
            <div class="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              <article v-for="model in modelRows" :key="model.id" class="rounded-xl border border-slate-100 bg-slate-50 p-4">
                <div class="flex items-start justify-between gap-3">
                  <div>
                    <p class="font-black">{{ model.displayName || model.model }}</p>
                    <p class="mt-1 font-mono text-xs font-bold text-slate-500">{{ model.model }}</p>
                  </div>
                  <span class="rounded-full px-3 py-1 text-xs font-black" :class="model.lastStatus === 'success' || model.status === 'available' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'">{{ statusText(model.lastStatus) }}</span>
                </div>
                <p class="mt-3 text-xs font-semibold text-slate-500">{{ model.modelType }} · 请求 {{ model.requests }} · Token {{ compact(model.tokens) }}</p>
              </article>
            </div>
          </section>

          <section v-if="activeMenu === 'billing'" class="grid gap-6 xl:grid-cols-[380px_1fr]">
            <div class="space-y-5">
              <div class="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
                <p class="text-sm font-bold text-slate-500">当前余额</p>
                <p class="mt-2 text-4xl font-black text-emerald-600">{{ yuan(balance) }}</p>
                <p class="mt-2 text-xs font-semibold text-slate-500">充值余额与主站账户共享，可用于中转站 API 调用扣费。</p>
              </div>

              <div class="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
                <h2 class="text-xl font-black">余额充值</h2>
                <div class="mt-5">
                  <p class="text-sm font-black text-slate-700">充值档位</p>
                  <div class="mt-3 grid grid-cols-2 gap-3">
                    <button
                      v-for="preset in rechargePresets"
                      :key="preset"
                      class="h-14 rounded-2xl border text-lg font-black transition"
                      :class="rechargePreset === preset ? 'border-emerald-300 bg-emerald-50 text-emerald-700 shadow-sm' : 'border-slate-200 bg-white text-slate-700 hover:border-emerald-200 hover:bg-emerald-50/60'"
                      @click="selectRechargePreset(preset)"
                    >
                      ￥{{ preset }}
                    </button>
                    <button
                      class="h-14 rounded-2xl border text-sm font-black transition"
                      :class="rechargePreset === 'custom' ? 'border-emerald-300 bg-emerald-50 text-emerald-700 shadow-sm' : 'border-slate-200 bg-white text-slate-700 hover:border-emerald-200 hover:bg-emerald-50/60'"
                      @click="selectRechargePreset('custom')"
                    >
                      自定义
                    </button>
                  </div>
                  <input
                    v-if="rechargePreset === 'custom'"
                    v-model.number="rechargeAmount"
                    class="mt-3 h-12 w-full rounded-xl border border-slate-200 px-4 text-sm font-semibold outline-none focus:border-emerald-300"
                    min="0.01"
                    step="0.01"
                    type="number"
                    placeholder="输入充值金额"
                  />
                </div>

                <div class="mt-5">
                  <p class="text-sm font-black text-slate-700">支付方式</p>
                  <div class="mt-3 grid gap-2">
                    <button
                      v-for="option in enabledPaymentOptions"
                      :key="option.value"
                      class="flex items-center justify-between rounded-2xl border px-4 py-3 text-left transition"
                      :class="rechargeType === option.value ? 'border-emerald-300 bg-emerald-50 text-emerald-700' : 'border-slate-200 bg-white text-slate-700 hover:border-emerald-200 hover:bg-emerald-50/60'"
                      @click="rechargeType = option.value"
                    >
                      <span>
                        <span class="block text-sm font-black">{{ option.label }}</span>
                        <span class="mt-1 block text-xs font-semibold text-slate-500">{{ option.desc }}</span>
                      </span>
                      <span class="grid h-5 w-5 place-items-center rounded-full border" :class="rechargeType === option.value ? 'border-emerald-500 bg-emerald-500' : 'border-slate-300'">
                        <span v-if="rechargeType === option.value" class="h-2 w-2 rounded-full bg-white"></span>
                      </span>
                    </button>
                    <p v-if="!enabledPaymentOptions.length" class="rounded-xl bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-700">暂无可用支付方式，请联系管理员。</p>
                  </div>
                </div>

                <button class="mt-5 h-12 w-full rounded-2xl bg-emerald-600 text-sm font-black text-white shadow-sm shadow-emerald-100 transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60" :disabled="rechargeLoading || !enabledPaymentOptions.length" @click="createRechargeOrder">
                  {{ rechargeLoading ? '创建中' : `充值 ${yuan(rechargeAmount)}` }}
                </button>
                <p v-if="rechargeError" class="mt-3 rounded-xl bg-red-50 px-3 py-2 text-sm font-semibold text-red-600">{{ rechargeError }}</p>
              </div>
            </div>

            <div class="rounded-2xl border border-slate-100 bg-white shadow-sm">
              <div class="border-b border-slate-100 p-5">
                <h2 class="text-xl font-black">充值记录</h2>
                <p class="mt-1 text-sm font-semibold text-slate-500">沿用当前账户支付记录，支付回调完成后余额自动入账。</p>
              </div>
              <div class="divide-y divide-slate-100">
                <div v-for="record in paymentRecords" :key="record.id" class="grid gap-3 p-5 text-sm font-semibold text-slate-600 md:grid-cols-[120px_1fr_120px_180px] md:items-center">
                  <span class="font-black text-slate-950">{{ yuan(record.amount) }}</span>
                  <span>{{ paymentTypeText(record.type) }}</span>
                  <span class="w-fit rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-600">{{ paymentStatusText(record.status) }}</span>
                  <span class="text-slate-500">{{ record.createdAt }}</span>
                </div>
                <div v-if="!paymentRecords.length" class="p-10 text-center text-sm font-black text-slate-400">暂无充值记录</div>
              </div>
            </div>
          </section>

          <section v-if="activeMenu === 'subscription' || activeMenu === 'orders' || activeMenu === 'profile'" class="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
            <h2 class="text-xl font-black">{{ menus.find((item) => item.id === activeMenu)?.label }}</h2>
            <p class="mt-3 text-sm font-semibold text-slate-500">这是中转站独立页面。账号、邮箱和余额与原项目互通；当前余额 ${{ balance.toFixed(2) }}。</p>
            <div v-if="activeMenu === 'profile'" class="mt-5 grid gap-3 text-sm font-semibold text-slate-600">
              <p>用户名：{{ auth.userInfo?.username }}</p>
              <p>邮箱：{{ auth.userInfo?.email || '未绑定' }}</p>
              <p>角色：{{ auth.role }}</p>
            </div>
            <div v-else class="mt-5 rounded-xl border border-dashed border-slate-200 p-8 text-sm font-black text-slate-500">中转站订单、订阅和充值记录将在这里独立展示。</div>
          </section>
        </template>
      </main>
    </div>

    <div v-if="showKeyDialog" class="fixed inset-0 z-50 grid place-items-center bg-slate-950/45 px-4 backdrop-blur-sm">
      <div class="w-full max-w-2xl overflow-hidden rounded-2xl bg-white shadow-2xl">
        <div class="flex items-center justify-between">
          <h2 class="px-6 py-5 text-xl font-black">创建密钥</h2>
          <button class="mr-5 rounded-lg px-3 py-2 text-2xl leading-none text-slate-400 hover:bg-slate-50" @click="showKeyDialog = false">×</button>
        </div>
        <div class="space-y-5 border-t border-slate-100 px-6 py-5">
          <label class="block">
            <span class="text-sm font-black text-slate-700">名称</span>
            <input v-model="keyForm.name" class="mt-2 h-12 w-full rounded-xl border border-slate-200 px-4 text-sm font-semibold outline-none focus:border-teal-300" placeholder="我的 API 密钥" />
          </label>

          <div>
            <span class="text-sm font-black text-slate-700">分组</span>
            <div class="mt-2 max-h-56 overflow-y-auto rounded-2xl border border-teal-200 bg-white p-2 shadow-sm">
              <button
                v-for="group in groups"
                :key="group.code"
                class="flex w-full items-center justify-between rounded-xl px-3 py-3 text-left transition"
                :class="keyForm.group === group.code ? 'bg-slate-50' : 'hover:bg-slate-50'"
                @click="keyForm.group = group.code"
              >
                <span>
                  <span class="inline-flex rounded-md bg-emerald-50 px-2 py-1 text-sm font-black text-emerald-700">{{ group.name }}</span>
                  <span class="ml-2 text-xs font-semibold text-slate-500">{{ group.code }}</span>
                </span>
                <span class="rounded-full bg-emerald-50 px-3 py-1 text-sm font-black text-emerald-700">{{ Number(group.ratio || 1).toFixed(3) }}x 倍率</span>
              </button>
            </div>
          </div>

          <div class="space-y-4">
            <label class="flex items-center justify-between text-sm font-black text-slate-700">
              IP 限制
              <input v-model="keyForm.ipLimitEnabled" class="h-5 w-5 accent-teal-600" type="checkbox" />
            </label>
            <input v-if="keyForm.ipLimitEnabled" v-model="keyForm.ipWhitelist" class="h-12 w-full rounded-xl border border-slate-200 px-4 text-sm font-semibold outline-none focus:border-teal-300" placeholder="多个 IP 用英文逗号分隔" />

            <label class="flex items-center justify-between text-sm font-black text-slate-700">
              额度限制
              <input v-model="keyForm.quotaEnabled" class="h-5 w-5 accent-teal-600" type="checkbox" />
            </label>
            <div v-if="keyForm.quotaEnabled">
              <input v-model.number="keyForm.quota" class="h-12 w-full rounded-xl border border-slate-200 px-4 text-sm font-semibold outline-none focus:border-teal-300" type="number" step="0.0001" placeholder="$ 输入 USD 额度限制" />
              <p class="mt-2 text-xs font-semibold text-slate-500">设置此密钥可消费的最大金额，0 = 无限制。</p>
            </div>

            <label class="flex items-center justify-between text-sm font-black text-slate-700">
              速率限制
              <input v-model="keyForm.speedLimitEnabled" class="h-5 w-5 accent-teal-600" type="checkbox" />
            </label>
            <div v-if="keyForm.speedLimitEnabled" class="grid gap-3 sm:grid-cols-2">
              <input v-model.number="keyForm.rpmLimit" class="h-12 rounded-xl border border-slate-200 px-4 text-sm font-semibold outline-none focus:border-teal-300" type="number" placeholder="RPM" />
              <input v-model.number="keyForm.tpmLimit" class="h-12 rounded-xl border border-slate-200 px-4 text-sm font-semibold outline-none focus:border-teal-300" type="number" placeholder="TPM" />
            </div>

            <label class="flex items-center justify-between text-sm font-black text-slate-700">
              密钥有效期
              <input v-model="keyForm.expiresEnabled" class="h-5 w-5 accent-teal-600" type="checkbox" />
            </label>
          </div>
        </div>
        <div class="flex justify-end gap-3 border-t border-slate-100 bg-slate-50 px-6 py-4">
          <button class="h-12 rounded-xl border border-slate-200 bg-white px-6 text-sm font-black text-slate-700" @click="showKeyDialog = false">取消</button>
          <button class="h-12 rounded-xl bg-teal-600 px-6 text-sm font-black text-white shadow-sm shadow-teal-100 disabled:opacity-60" :disabled="creatingKey" @click="createKey">{{ creatingKey ? '创建中' : '创建' }}</button>
        </div>
      </div>
    </div>

    <div v-if="selectedAnnouncement" class="fixed inset-0 z-[55] grid place-items-center bg-slate-950/40 px-4 backdrop-blur-sm" @click.self="selectedAnnouncement = null">
      <section class="w-full max-w-2xl rounded-[28px] bg-white p-6 shadow-[0_28px_90px_rgba(15,23,42,0.24)]">
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2 class="text-2xl font-black text-slate-950">{{ selectedAnnouncement.title }}</h2>
            <p class="mt-2 text-xs font-semibold text-slate-400">{{ (selectedAnnouncement.publishedAt || selectedAnnouncement.createdAt || '').replace('T', ' ') }}</p>
          </div>
          <button class="rounded-xl px-3 py-2 text-xl font-black text-slate-400 hover:bg-slate-50" @click="selectedAnnouncement = null">×</button>
        </div>
        <div class="mt-5 whitespace-pre-wrap text-sm font-semibold leading-7 text-slate-700">{{ selectedAnnouncement.content }}</div>
      </section>
    </div>
  </div>
</template>
