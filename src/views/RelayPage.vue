<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/authStore'
import { noticeApi } from '@/api/noticeApi'
import { relayApi } from '@/api/relayApi'
import { paymentApi } from '@/api/paymentApi'
import { userApi } from '@/api/userApi'
import { useToast } from '@/composables/useToast'
import { useSidebarPreference } from '@/composables/useSidebarPreference'
import RequestLoader from '@/components/RequestLoader.vue'
import AppConfirmDialog from '@/components/AppConfirmDialog.vue'
import Pagination from '@/components/Pagination.vue'
import AnimatedNumber from '@/components/relay/AnimatedNumber.vue'
import RelayTrendChart from '@/components/relay/RelayTrendChart.vue'
import RelayModelDistribution from '@/components/relay/RelayModelDistribution.vue'
import RelayRecentCalls from '@/components/relay/RelayRecentCalls.vue'
import type { Announcement, ErrorRequestLog, PaymentRecord, RelayModel, RelayModelRecentCall, RelayPublicChannelModel, RelayToken, RelayUsageLog, RelayUserOverview } from '@/types'

const router = useRouter()
const auth = useAuthStore()
const toast = useToast()
const { sidebarCollapsed, toggleSidebar } = useSidebarPreference()

const overview = ref<RelayUserOverview | null>(null)
const activeMenu = ref('dashboard')
const loadedSections = ref<Set<string>>(new Set())
const loadingSections = ref<Set<string>>(new Set())
const loading = computed(() => loadingSections.value.size > 0)
const activeMenuLoading = computed(() => loadingSections.value.has(activeMenu.value))
const announcementsLoading = ref(false)
const billingExtrasLoaded = ref(false)
let logFilterTimer: number | undefined
const copied = ref('')
const creatingKey = ref(false)
const showKeyDialog = ref(false)
const tokenSecretActionId = ref<number | null>(null)
const syncingStatus = ref(false)
const checkingChannelId = ref<number | null>(null)
const checkedChannelIds = ref<Set<number>>(new Set())
const showErrorLogsDialog = ref(false)
const showIntegrationGuide = ref(false)
const mobileMenuOpen = ref(false)
const menuDirection = ref<'forward' | 'backward'>('forward')
const menuSwitching = ref(false)
const menuProgressKey = ref(0)
let menuSwitchTimer: number | undefined
const keySearch = ref('')
const keyGroupFilter = ref('')
const keyStatusFilter = ref('')
const channelSearch = ref('')
const logSearch = ref('')
const logStatusFilter = ref('all')
const logSort = ref<'latest' | 'slowest' | 'cost'>('latest')
const logPage = ref(1)
const logPageSize = ref(20)
const expandedLogIds = ref<Set<number>>(new Set())
const keyActionDialog = ref<{ action: 'toggle' | 'delete'; token: RelayToken } | null>(null)
const keyActionLoading = ref(false)
const ccSwitchImportDialog = ref<RelayToken | null>(null)
const ccSwitchImportLoading = ref(false)
const activePricingTooltip = ref<{
  model: RelayPublicChannelModel
  detail: RelayModel
  rule: string
  x: number
  y: number
} | null>(null)
const rechargeAmount = ref(10)
const rechargePreset = ref<number | 'custom'>(10)
const rechargeType = ref('alipay')
const rechargeLoading = ref(false)
const rechargeError = ref('')
const paymentRecords = ref<PaymentRecord[]>([])
const announcements = ref<Announcement[]>([])
const showAnnouncementsDialog = ref(false)
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
  expiresAt: '',
  quotaEnabled: false,
  speedLimitEnabled: false,
  expiresEnabled: false
})
const keyFormError = ref('')
const profileEmail = ref(auth.userInfo?.email || '')
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const profileError = ref('')
const profileSaving = ref<'profile' | 'password' | ''>('')
const pwdVisible = reactive({ current: false, next: false, confirm: false })

const passwordStrength = computed(() => {
  const value = newPassword.value
  if (!value) return { score: 0, label: '', color: '#e2e8f0' }
  let points = 0
  if (value.length >= 6) points += 1
  if (value.length >= 10) points += 1
  if (/[a-z]/.test(value) && /[A-Z]/.test(value)) points += 1
  if (/\d/.test(value)) points += 1
  if (/[^A-Za-z0-9]/.test(value)) points += 1
  if (points <= 2) return { score: 34, label: '强度：弱', color: '#f43f5e' }
  if (points <= 3) return { score: 67, label: '强度：中', color: '#f59e0b' }
  return { score: 100, label: '强度：强', color: '#10b981' }
})

const confirmMismatch = computed(() => Boolean(confirmPassword.value) && confirmPassword.value !== newPassword.value)

const apiBase = computed(() => `${window.location.origin}/api/v1`)
const siteOrigin = computed(() => window.location.origin)
const balance = computed(() => Number(overview.value?.balance || auth.userInfo?.balance || 0))
const tokens = computed(() => overview.value?.tokens || [])
const channels = computed(() => overview.value?.channels || [])
const availableChannels = computed(() => channels.value.filter((item) => item.status === 'available').length)
const logs = computed(() => overview.value?.logs || [])
// Search, status, and ordering are applied by the paged API query.
const filteredLogs = computed(() => logs.value)
const logTotal = computed(() => Number(overview.value?.logsTotal || 0))
const logPages = computed(() => Math.max(1, Number(overview.value?.logsPages || (logTotal.value ? Math.ceil(logTotal.value / logPageSize.value) : 1))))
const successfulLogCount = computed(() => logs.value.filter((log) => log.status !== 'failed' && Number(log.statusCode || 0) < 400).length)
const logSuccessRate = computed(() => logs.value.length ? (successfulLogCount.value / logs.value.length) * 100 : 0)
const logTotalCost = computed(() => logs.value.reduce((sum, log) => sum + Number(log.cost || 0), 0))
const logAverageDuration = computed(() => logs.value.length
  ? logs.value.reduce((sum, log) => sum + Number(log.durationMs || 0), 0) / logs.value.length
  : 0)
const errorLogs = computed<ErrorRequestLog[]>(() => overview.value?.errorLogs || logs.value
  .filter((item) => item.status === 'failed' || Number(item.statusCode || 0) >= 400)
  .map((item) => ({
    id: item.id,
    source: 'relay',
    tokenName: item.tokenName,
    channelName: item.channelName,
    groupNames: item.groupNames,
    endpoint: item.endpoint,
    requestUrl: '',
    model: item.model,
    modelType: item.modelType,
    statusCode: item.statusCode,
    durationMs: item.durationMs,
    userAgent: item.userAgent,
    status: item.status,
    errorType: '',
    message: item.message,
    prompt: '',
    createdAt: item.createdAt
  })))
const models = computed(() => overview.value?.models || [])
const modelsById = computed(() => new Map(models.value.map((model) => [model.id, model])))
const groups = computed(() => overview.value?.groups || [{ id: 0, code: 'default', name: '默认分组', ratio: 1, enabled: true }])
const trend = computed(() => overview.value?.trend || [])
const modelUsage = computed(() => overview.value?.modelUsage || [])
const modelRecentCalls = computed(() => overview.value?.modelRecentCalls || [])
const channelRows = computed(() => {
  const keyword = channelSearch.value.trim().toLowerCase()
  return channels.value
    .filter((channel) => {
      const modelText = (channel.models || []).map((item) => `${publicModelName(item)} ${item.model}`).join(' ').toLowerCase()
      return !keyword
        || channel.name.toLowerCase().includes(keyword)
        || channel.groupNames.toLowerCase().includes(keyword)
        || String(channel.remark || '').toLowerCase().includes(keyword)
        || modelText.includes(keyword)
    })
    .map((channel) => ({
      ...channel,
      groups: csvValues(channel.groupNames || 'default'),
      enabledModels: (channel.models || []).filter((item) => item.enabled)
    }))
})
const failedChannels = computed(() => channels.value.filter((item) => item.status === 'failed').length)
const channelModelCount = computed(() => new Set(channels.value.flatMap((item) => (item.models || []).filter((model) => model.enabled).map((model) => publicModelName(model)))).size)
const activeTokens = computed(() => tokens.value.filter((item) => item.enabled))
const keyTodayCost = computed(() => tokens.value.reduce((sum, item) => sum + Number(item.todayCost || 0), 0))
const keyUsedQuota = computed(() => tokens.value.reduce((sum, item) => sum + Number(item.usedQuota || 0), 0))
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

const comingSoonPanel = computed(() => activeMenu.value === 'orders'
  ? {
      title: '我的订单',
      subtitle: '查看订单与支付记录',
      description: '订单查询功能正在准备中，开放后完整记录会显示在这里。',
      icon: 'file',
      columns: ['订单号', '订单类型', '金额', '状态', '创建时间']
    }
  : {
      title: '我的订阅',
      subtitle: '管理当前套餐与订阅周期',
      description: '订阅管理功能正在准备中，开放后套餐信息会显示在这里。',
      icon: 'card',
      columns: ['套餐名称', '计费周期', '生效时间', '状态', '操作']
    })

const menuTransitionName = computed(() => menuDirection.value === 'backward' ? 'relay-view-back' : 'relay-view-forward')

const menuIconPaths: Record<string, string> = {
  grid: 'M4 4h6v6H4V4zm10 0h6v6h-6V4zM4 14h6v6H4v-6zm10 0h6v6h-6v-6z',
  key: 'M15 7a4 4 0 1 1-2.4 3.67L4 19.27V21h1.73l1-1H9v-2h2v-2h2l2.33-2.33A4 4 0 0 1 15 7z',
  chart: 'M4 19V9m5 10V5m5 14v-7m5 7V3',
  layers: 'm12 3 9 5-9 5-9-5 9-5zm-9 10 9 5 9-5M3 18l9 5 9-5',
  card: 'M3 6h18v12H3V6zm0 4h18M7 15h4',
  coin: 'M12 3c5 0 9 2 9 4.5S17 12 12 12 3 10 3 7.5 7 3 12 3zm-9 4.5V16c0 2.5 4 4.5 9 4.5s9-2 9-4.5V7.5M3 12c0 2.5 4 4.5 9 4.5s9-2 9-4.5',
  file: 'M6 3h8l4 4v14H6V3zm8 0v5h5M9 13h6m-6 4h6',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm-7 9a7 7 0 0 1 14 0',
  gift: 'M4 10h16v11H4V10zm-1-4h18v4H3V6zm9 0v15m0-15H8.5A2.5 2.5 0 1 1 11 3.5V6zm1 0h2.5A2.5 2.5 0 1 0 13 3.5V6z'
}

const metricCards = computed(() => [
  { label: '总请求数', value: compact(totalRequests.value), sub: '当前账号累计', tone: 'blue' },
  { label: '总 Token', value: compact(totalTokens.value), sub: `输入 ${compact(promptTotal.value)} / 输出 ${compact(completionTotal.value)}`, tone: 'amber' },
  { label: '总消费', value: `$${totalCost.value.toFixed(4)}`, sub: `余额 $${balance.value.toFixed(6)}`, tone: 'emerald' },
  { label: '平均耗时', value: `${(avgDuration.value / 1000).toFixed(2)}s`, sub: '每次请求平均', tone: 'violet' }
])

const modelRows = computed(() => {
  const usageByModel = new Map(modelUsage.value.flatMap((item) => {
    const model = models.value.find((row) => row.model === item.model || publicModelName(row) === item.model)
    return model ? [[model.model, item], [publicModelName(model), item]] : [[item.model, item]]
  }))
  const callsByModel = new Map<string, RelayModelRecentCall[]>()
  modelRecentCalls.value.forEach((call) => {
    const rows = callsByModel.get(call.model) || []
    rows.push(call)
    callsByModel.set(call.model, rows)
  })
  return models.value.map((model) => {
    const usage = usageByModel.get(publicModelName(model)) || usageByModel.get(model.model)
    const recentCalls = callsByModel.get(publicModelName(model)) || callsByModel.get(model.model) || []
    const latest = recentCalls.at(-1)
    return {
      ...model,
      requests: Number(usage?.requests || 0),
      tokens: Number(usage?.totalTokens || 0),
      cost: Number(usage?.cost || 0),
      recentCalls,
      lastStatus: latest?.status || (model.enabled ? model.status || 'unknown' : 'disabled')
    }
  })
})

const dashboardCards = computed(() => [
  { label: '今日消费', raw: displayTodayCost.value, kind: 'money', sub: `累计消费 ${money(totalCost.value)}`, tone: 'emerald', icon: 'M12 6v12m6-6H6' },
  { label: '今日请求', raw: displayTodayRequests.value, kind: 'compact', sub: `总请求 ${compact(totalRequests.value)}`, tone: 'blue', icon: 'M4 7h16M4 12h16M4 17h10' },
  { label: '今日输入 Token', raw: displayTodayPromptTokens.value, kind: 'compact', sub: `输出 ${compact(displayTodayCompletionTokens.value)}`, tone: 'amber', icon: 'M7 8l-4 4 4 4M17 8l4 4-4 4M14 4l-4 16' },
  { label: '今日总 Token', raw: displayTodayTotalTokens.value, kind: 'compact', sub: `累计 ${compact(totalTokens.value)}`, tone: 'violet', icon: 'M5 7h14M5 12h14M5 17h14' },
  { label: 'RPM', raw: currentRpm.value, kind: 'compact', sub: '最近 1 分钟请求数', tone: 'rose', icon: 'M4 13a8 8 0 1 1 16 0M12 13l4-4' },
  { label: 'TPM', raw: currentTpm.value, kind: 'compact', sub: '最近 1 分钟 Token', tone: 'cyan', icon: 'M13 2L4 14h7l-1 8 9-12h-7l1-8z' },
  { label: '缓存 Token', raw: cachedTotal.value + cacheCreateTotal.value, kind: 'compact', sub: `读 ${compact(cachedTotal.value)} / 写 ${compact(cacheCreateTotal.value)}`, tone: 'indigo', icon: 'M4 7c0-2 4-4 8-4s8 2 8 4-4 4-8 4-8-2-8-4zm0 0v10c0 2 4 4 8 4s8-2 8-4V7' },
  { label: '平均响应', raw: avgDuration.value, kind: 'seconds', sub: '按历史调用平均', tone: 'slate', icon: 'M12 8v4l3 3M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z' }
])

function cardFormat(kind: string) {
  if (kind === 'money') return money
  if (kind === 'seconds') return (value: number) => `${(value / 1000).toFixed(2)}s`
  return (value: number) => compact(Math.round(value))
}

function toneGlow(tone: string) {
  const map: Record<string, string> = {
    emerald: '#10b981',
    blue: '#3b82f6',
    amber: '#f59e0b',
    violet: '#8b5cf6',
    rose: '#f43f5e',
    cyan: '#06b6d4',
    indigo: '#6366f1',
    slate: '#64748b'
  }
  return map[tone] || map.slate
}

const distributionRows = computed(() => modelRows.value.map((row) => ({
  name: publicModelName(row),
  requests: row.requests,
  tokens: row.tokens,
  cost: row.cost,
  status: row.lastStatus
})))

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


function updateLoadedSections(section: string) {
  const next = new Set(loadedSections.value)
  next.add(section)
  loadedSections.value = next
}

function updateLoadingSection(section: string, active: boolean) {
  const next = new Set(loadingSections.value)
  if (active) next.add(section)
  else next.delete(section)
  loadingSections.value = next
}

function mergeOverview(partial: RelayUserOverview) {
  const next = { ...(overview.value || {}) } as Record<string, unknown>
  Object.entries(partial as unknown as Record<string, unknown>).forEach(([key, value]) => {
    if (value !== null && value !== undefined) next[key] = value
  })
  overview.value = next as unknown as RelayUserOverview
  if (!keyForm.group && groups.value.length) keyForm.group = groups.value[0]?.code || 'default'
}

async function loadSection(section: string, force = false) {
  if (!auth.isAuthenticated || section === 'referral') return
  if (!force && loadedSections.value.has(section)) {
    if (section === 'billing') await loadBillingExtras(false)
    if (section === 'dashboard') await loadAnnouncements(false)
    return
  }
  if (loadingSections.value.has(section)) return

  updateLoadingSection(section, true)
  try {
    const { data } = await relayApi.overview({
      section,
      page: section === 'logs' ? logPage.value : 1,
      size: section === 'logs' ? logPageSize.value : 20,
      keyword: section === 'logs' ? logSearch.value.trim() || undefined : undefined,
      status: section === 'logs' ? logStatusFilter.value : undefined,
      sort: section === 'logs' ? logSort.value : undefined
    })
    mergeOverview(data.data)
    if (section === 'dashboard') await loadAnnouncements(force)
    if (section === 'billing') await loadBillingExtras(force)
    updateLoadedSections(section)
    return true
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '加载中转数据失败')
    return false
  } finally {
    updateLoadingSection(section, false)
  }
}

async function load() {
  if (!auth.isAuthenticated) return
  try {
    await auth.refreshUser()
    profileEmail.value = auth.userInfo?.email || ''
    await loadSection(activeMenu.value, true)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '刷新账户信息失败')
  }
}

async function loadBillingExtras(force = false) {
  if (!force && billingExtrasLoaded.value) return
  const [config, history] = await Promise.all([paymentApi.config(), paymentApi.history(1, 8)])
  const configData = config.data.data
  paymentOptions.value = paymentOptions.value.map((item) => ({
    ...item,
    enabled:
      item.value === 'alipay'
        ? Boolean(configData.alipayEnabled)
        : item.value === 'wxpay'
          ? Boolean(configData.wxpayEnabled)
          : Boolean(configData.qqpayEnabled)
  }))
  if (!enabledPaymentOptions.value.some((item) => item.value === rechargeType.value)) {
    rechargeType.value = enabledPaymentOptions.value[0]?.value || 'alipay'
  }
  paymentRecords.value = history.data.data.records
  billingExtrasLoaded.value = true
}

async function loadAnnouncements(force = false) {
  if (!force && loadedSections.value.has('announcements')) return
  if (announcementsLoading.value) return
  announcementsLoading.value = true
  try {
    const { data } = await noticeApi.list()
    announcements.value = data.data
    updateLoadedSections('announcements')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '加载公告失败')
  } finally {
    announcementsLoading.value = false
  }
}

function openAnnouncements() {
  selectedAnnouncement.value = null
  showAnnouncementsDialog.value = true
  void loadAnnouncements()
}

function openAnnouncement(item: Announcement) {
  showAnnouncementsDialog.value = false
  selectedAnnouncement.value = item
}

function backToAnnouncementList() {
  selectedAnnouncement.value = null
  showAnnouncementsDialog.value = true
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

function publicModelName(model: Pick<RelayModel | RelayPublicChannelModel, 'model' | 'displayName'>) {
  return model.displayName || model.model
}

function ruleLabel(rule?: string) {
  return String(rule || '').toLowerCase() === 'anthropic' ? 'ANTHROPIC' : 'OPENAI'
}

function ruleBadgeClass(rule?: string) {
  return String(rule || '').toLowerCase() === 'anthropic'
    ? 'bg-orange-50 text-orange-700 ring-orange-100'
    : 'bg-emerald-50 text-emerald-700 ring-emerald-100'
}

function channelModelDetail(binding: RelayPublicChannelModel): RelayModel {
  const exact = modelsById.value.get(binding.modelId)
  return {
    id: binding.modelId,
    model: binding.model,
    displayName: binding.displayName,
    modelType: binding.modelType,
    inputPrice: Number(binding.inputPrice ?? exact?.inputPrice ?? 0),
    outputPrice: Number(binding.outputPrice ?? exact?.outputPrice ?? 0),
    cachedInputPrice: Number(binding.cachedInputPrice ?? exact?.cachedInputPrice ?? 0),
    cacheCreationPrice: Number(binding.cacheCreationPrice ?? exact?.cacheCreationPrice ?? 0),
    requestPrice: Number(binding.requestPrice ?? exact?.requestPrice ?? 0),
    fixedRequestBilling: Boolean(binding.fixedRequestBilling ?? exact?.fixedRequestBilling),
    status: exact?.status || '',
    enabled: binding.enabled,
    sortOrder: exact?.sortOrder || 0
  }
}

function modelHasConfiguredPricing(model?: Pick<RelayModel, 'inputPrice' | 'outputPrice' | 'cachedInputPrice' | 'cacheCreationPrice' | 'requestPrice'>) {
  if (!model) return false
  return Number(model.inputPrice || 0) > 0
    || Number(model.outputPrice || 0) > 0
    || Number(model.cachedInputPrice || 0) > 0
    || Number(model.cacheCreationPrice || 0) > 0
    || Number(model.requestPrice || 0) > 0
}

function priceValue(value?: number) {
  return `$${Number(value || 0).toFixed(6)}`
}

function fixedBilling(model: RelayModel) {
  return Boolean(model.fixedRequestBilling)
}

function groupDetail(code: string) {
  return groups.value.find((group) => group.code === code)
}

function groupRatioLabel(code: string) {
  const ratio = Number(groupDetail(code)?.ratio || 1)
  return `${ratio.toFixed(3)}x`
}

function showPricingTooltip(event: MouseEvent | FocusEvent, model: RelayPublicChannelModel, rule: string) {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const width = 288
  const estimatedHeight = 270
  const margin = 16
  let x = rect.left
  let y = rect.bottom + 10
  if (x + width > window.innerWidth - margin) x = window.innerWidth - width - margin
  if (x < margin) x = margin
  if (y + estimatedHeight > window.innerHeight - margin) {
    y = Math.max(margin, rect.top - estimatedHeight - 10)
  }
  activePricingTooltip.value = {
    model,
    detail: channelModelDetail(model),
    rule,
    x,
    y
  }
}

function hidePricingTooltip() {
  activePricingTooltip.value = null
}

function cacheHitRate(log: Pick<RelayUsageLog, 'promptTokens' | 'cachedTokens'>) {
  const inputTokens = Number(log.promptTokens || 0)
  if (inputTokens <= 0) return 0
  return Math.min(100, Math.max(0, (Number(log.cachedTokens || 0) / inputTokens) * 100))
}

function cacheHitLabel(log: Pick<RelayUsageLog, 'promptTokens' | 'cachedTokens'>) {
  return `${cacheHitRate(log).toFixed(1)}%`
}

function cacheHitTitle(log: Pick<RelayUsageLog, 'promptTokens' | 'cachedTokens' | 'cacheCreationTokens'>) {
  return `缓存命中率 ${cacheHitLabel(log)}，缓存读 ${compact(log.cachedTokens || 0)}，缓存写 ${compact(log.cacheCreationTokens || 0)}`
}

function logFailed(log: Pick<RelayUsageLog, 'status' | 'statusCode'>) {
  return log.status === 'failed' || Number(log.statusCode || 0) >= 400
}

function logDate(value?: string) {
  if (!value) return '-'
  const normalized = value.replace('T', ' ')
  return normalized.slice(5, 16)
}

function logTime(value?: string) {
  if (!value) return '-'
  const normalized = value.replace('T', ' ')
  return normalized.slice(11, 19)
}

function toggleLogDetails(id: number) {
  const next = new Set(expandedLogIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedLogIds.value = next
}

function exportUsageCsv() {
  if (!logs.value.length) {
    toast.warning('暂无使用记录可导出')
    return
  }
  const headers = [
    'API 密钥',
    '渠道',
    '分组',
    '模型',
    '请求端点',
    '类型',
    '输入 Token',
    '输出 Token',
    '缓存读 Token',
    '缓存写 Token',
    '总 Token',
    '消费金额',
    '状态码',
    '状态',
    '耗时秒',
    '时间',
    'User-Agent',
    '错误信息'
  ]
  const rows = logs.value.map((log) => [
    log.tokenName || '',
    log.channelName || '',
    log.groupNames || '',
    log.model || '',
    log.endpoint || '',
    log.modelType || '',
    log.promptTokens || 0,
    log.completionTokens || 0,
    log.cachedTokens || 0,
    log.cacheCreationTokens || 0,
    log.totalTokens || 0,
    Number(log.cost || 0).toFixed(6),
    log.statusCode || '',
    log.status || '',
    ((log.durationMs || 0) / 1000).toFixed(2),
    formatCsvDate(log.createdAt),
    log.userAgent || '',
    log.message || ''
  ])
  const csv = [headers, ...rows].map((row) => row.map(csvCell).join(',')).join('\r\n')
  downloadTextFile(`usage-records-${localDateKey()}.csv`, `\uFEFF${csv}`, 'text/csv;charset=utf-8')
  toast.success(`已导出 ${logs.value.length} 条使用记录`)
}

function csvCell(value: unknown) {
  const text = String(value ?? '')
  return `"${text.replace(/"/g, '""')}"`
}

function formatCsvDate(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

function downloadTextFile(fileName: string, content: string, type: string) {
  const blob = new Blob([content], { type })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function csvValues(value: string) {
  return String(value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function yuan(value: number) {
  return `￥${Number(value || 0).toFixed(6)}`
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
  if (value === 'image_refund') return '生图失败退款'
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
    await load()
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
  keyFormError.value = ''
  keyForm.name = ''
  keyForm.group = groups.value[0]?.code || 'default'
  keyForm.ipLimitEnabled = false
  keyForm.ipWhitelist = ''
  keyForm.quota = 0
  keyForm.rpmLimit = 0
  keyForm.tpmLimit = 0
  keyForm.expiresAt = ''
  keyForm.quotaEnabled = false
  keyForm.speedLimitEnabled = false
  keyForm.expiresEnabled = false
  showKeyDialog.value = true
}

function localDateTimeValue(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function minimumExpiryValue() {
  return localDateTimeValue(new Date(Date.now() + 5 * 60 * 1000))
}

function applyExpiryPreset(days: number) {
  keyForm.expiresAt = localDateTimeValue(new Date(Date.now() + days * 24 * 60 * 60 * 1000))
}

function toggleExpiryLimit() {
  keyForm.expiresEnabled = !keyForm.expiresEnabled
  keyFormError.value = ''
  if (keyForm.expiresEnabled && !keyForm.expiresAt) applyExpiryPreset(30)
}

function normalizeIpWhitelist(value: string) {
  return [...new Set(value.split(/[\s,，;；]+/).map((item) => item.trim()).filter(Boolean))].join(',')
}

function validIpAddress(value: string) {
  if (value.includes(':')) return /^[0-9a-fA-F:]+$/.test(value)
  const parts = value.split('.')
  return parts.length === 4 && parts.every((part) => /^\d{1,3}$/.test(part) && Number(part) >= 0 && Number(part) <= 255)
}

function validateKeyForm() {
  if (keyForm.name.trim().length > 80) return '密钥名称不能超过 80 个字符'
  if (!keyForm.group) return '请选择一个密钥分组'

  if (keyForm.ipLimitEnabled) {
    const normalized = normalizeIpWhitelist(keyForm.ipWhitelist)
    if (!normalized) return '开启 IP 限制后，请至少填写一个允许访问的 IP 地址'
    const invalid = normalized.split(',').find((item) => !validIpAddress(item))
    if (invalid) return `IP 地址格式不正确：${invalid}`
    keyForm.ipWhitelist = normalized
  }

  if (keyForm.quotaEnabled && (!Number.isFinite(Number(keyForm.quota)) || Number(keyForm.quota) <= 0)) {
    return '开启额度限制后，额度必须大于 0 USD'
  }

  if (keyForm.speedLimitEnabled) {
    const rpm = Number(keyForm.rpmLimit)
    const tpm = Number(keyForm.tpmLimit)
    if (!Number.isInteger(rpm) || rpm < 0 || !Number.isInteger(tpm) || tpm < 0) return 'RPM 和 TPM 必须是大于等于 0 的整数'
    if (rpm === 0 && tpm === 0) return '开启速率限制后，RPM 或 TPM 至少填写一项'
  }

  if (keyForm.expiresEnabled) {
    if (!keyForm.expiresAt) return '开启有效期后，请选择密钥失效时间'
    const expires = new Date(keyForm.expiresAt).getTime()
    if (!Number.isFinite(expires) || expires <= Date.now() + 60_000) return '密钥失效时间必须晚于当前时间'
  }

  return ''
}

async function createKey() {
  keyFormError.value = validateKeyForm()
  if (keyFormError.value) {
    toast.warning(keyFormError.value)
    return
  }
  creatingKey.value = true
  try {
    await relayApi.createToken({
      name: keyForm.name || '我的 API 密钥',
      groups: keyForm.group,
      quota: keyForm.quotaEnabled ? keyForm.quota : 0,
      rpmLimit: keyForm.speedLimitEnabled ? keyForm.rpmLimit : 0,
      tpmLimit: keyForm.speedLimitEnabled ? keyForm.tpmLimit : 0,
      ipWhitelist: keyForm.ipLimitEnabled ? keyForm.ipWhitelist : '',
      expiresAt: keyForm.expiresEnabled ? keyForm.expiresAt : undefined
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

async function copyToken(token: RelayToken) {
  tokenSecretActionId.value = token.id
  try {
    const { data } = await relayApi.revealToken(token.id)
    await copyText(data.data, `token-${token.id}`)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '读取密钥失败')
  } finally {
    tokenSecretActionId.value = null
  }
}

function ccSwitchTargetForToken(token: RelayToken) {
  const tokenGroups = new Set(csvValues(token.groups).map((group) => group.toLowerCase()))
  const matchingChannels = channels.value.filter((channel) => csvValues(channel.groupNames)
    .some((group) => tokenGroups.has(group.toLowerCase())))
  const hasOpenAiChannel = matchingChannels.some((channel) => String(channel.channelRule || '').toLowerCase() !== 'anthropic')

  // A token may be shared by both protocols. Prefer Codex/OpenAI unless every matching channel is Anthropic.
  return hasOpenAiChannel || !matchingChannels.length
    ? { app: 'codex', label: 'Codex（OpenAI）' }
    : { app: 'claude', label: 'Claude' }
}

async function importToCcSwitch(token: RelayToken) {
  tokenSecretActionId.value = token.id
  try {
    const { data } = await relayApi.revealToken(token.id)
    const target = ccSwitchTargetForToken(token)
    const params = new URLSearchParams({
      resource: 'provider',
      app: target.app,
      name: `${token.name} · imageCreater API`,
      endpoint: apiBase.value,
      apiKey: data.data,
      homepage: siteOrigin.value,
      notes: `由 imageCreater API 中转站安全导入 · ${target.label}`
    })
    const link = document.createElement('a')
    link.href = `ccswitch://v1/import?${params.toString()}`
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    link.remove()
    toast.success(`已唤起 CCSwitch，将导入到 ${target.label}`)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '唤起 CCSwitch 失败')
  } finally {
    tokenSecretActionId.value = null
  }
}

function requestCcSwitchImport(token: RelayToken) {
  ccSwitchImportDialog.value = token
}

function closeCcSwitchImport() {
  if (!ccSwitchImportLoading.value) ccSwitchImportDialog.value = null
}

async function confirmCcSwitchImport() {
  const token = ccSwitchImportDialog.value
  if (!token) return
  ccSwitchImportLoading.value = true
  try {
    await importToCcSwitch(token)
    ccSwitchImportDialog.value = null
  } finally {
    ccSwitchImportLoading.value = false
  }
}

function groupOf(code: string) {
  return groups.value.find((item) => item.code === code)
}

function tokenQuotaPercent(token: RelayToken) {
  const quota = Number(token.quota || 0)
  if (quota <= 0) return 0
  return Math.min(100, Math.max(0, (Number(token.usedQuota || 0) / quota) * 100))
}

function openKeyAction(token: RelayToken, action: 'toggle' | 'delete') {
  keyActionDialog.value = { token, action }
}

function closeKeyAction() {
  if (!keyActionLoading.value) keyActionDialog.value = null
}

async function confirmKeyAction() {
  const dialog = keyActionDialog.value
  if (!dialog) return
  keyActionLoading.value = true
  try {
    if (dialog.action === 'delete') {
      await relayApi.deleteToken(dialog.token.id)
      toast.success(`密钥「${dialog.token.name}」已删除`)
    } else {
      await relayApi.updateToken(dialog.token.id, { enabled: !dialog.token.enabled })
      toast.success(`密钥「${dialog.token.name}」已${dialog.token.enabled ? '禁用' : '启用'}`)
    }
    await load()
    keyActionDialog.value = null
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '密钥操作失败')
  } finally {
    keyActionLoading.value = false
  }
}

function channelAnimationDelay() {
  return new Promise((resolve) => window.setTimeout(resolve, 520))
}

async function checkChannelStatus(channel: { id: number; status: string }) {
  checkingChannelId.value = channel.id
  try {
    const [{ data }] = await Promise.all([relayApi.syncChannelStatusOne(channel.id), channelAnimationDelay()])
    const nextStatus = data.data || 'unknown'
    channel.status = nextStatus
    const sourceChannel = channels.value.find((item) => item.id === channel.id)
    if (sourceChannel && sourceChannel !== channel) sourceChannel.status = nextStatus
    return channel.status === 'available'
  } catch {
    channel.status = 'failed'
    const sourceChannel = channels.value.find((item) => item.id === channel.id)
    if (sourceChannel && sourceChannel !== channel) sourceChannel.status = 'failed'
    return false
  } finally {
    const completed = new Set(checkedChannelIds.value)
    completed.add(channel.id)
    checkedChannelIds.value = completed
    checkingChannelId.value = null
  }
}

async function syncStatus() {
  if (syncingStatus.value || !channels.value.length) return
  syncingStatus.value = true
  checkedChannelIds.value = new Set()
  let available = 0
  try {
    for (const channel of channels.value) {
      if (await checkChannelStatus(channel)) available += 1
    }
    toast.success(`渠道检测完成：${available} 个可用，${channels.value.length - available} 个异常`)
  } finally {
    syncingStatus.value = false
    window.setTimeout(() => { checkedChannelIds.value = new Set() }, 1800)
  }
}

async function syncSingleChannel(channel: { id: number; name: string; status: string }) {
  if (syncingStatus.value) return
  syncingStatus.value = true
  checkedChannelIds.value = new Set()
  try {
    const available = await checkChannelStatus(channel)
    if (available) toast.success(`${channel.name}：渠道可用`)
    else toast.warning(`${channel.name}：渠道异常`)
  } finally {
    syncingStatus.value = false
    window.setTimeout(() => { checkedChannelIds.value = new Set() }, 1800)
  }
}

async function saveRelayProfile() {
  profileError.value = ''
  const email = profileEmail.value.trim()
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    profileError.value = '请输入正确的邮箱地址'
    toast.warning(profileError.value)
    return
  }
  profileSaving.value = 'profile'
  try {
    const { data } = await userApi.updateProfile(email)
    auth.userInfo = data.data
    auth.persist()
    profileEmail.value = data.data.email || ''
    toast.success('账号资料已保存')
  } catch (err) {
    profileError.value = err instanceof Error ? err.message : '资料保存失败'
    toast.error(profileError.value)
  } finally {
    profileSaving.value = ''
  }
}

async function changeRelayPassword() {
  profileError.value = ''
  if (!oldPassword.value) {
    profileError.value = '请输入当前密码'
  } else if (newPassword.value.length < 6) {
    profileError.value = '新密码至少需要 6 位'
  } else if (newPassword.value !== confirmPassword.value) {
    profileError.value = '两次输入的新密码不一致'
  }
  if (profileError.value) {
    toast.warning(profileError.value)
    return
  }
  profileSaving.value = 'password'
  try {
    await userApi.changePassword(oldPassword.value, newPassword.value)
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    toast.success('登录密码已更新')
  } catch (err) {
    profileError.value = err instanceof Error ? err.message : '密码修改失败'
    toast.error(profileError.value)
  } finally {
    profileSaving.value = ''
  }
}

function statusText(status: string) {
  if (status === 'available' || status === 'success') return '可用'
  if (status === 'failed') return '异常'
  if (status === 'disabled') return '已停用'
  if (status === 'unknown') return '待调用'
  return '未知'
}

function recentCallSucceeded(call: RelayModelRecentCall) {
  return call.status === 'success' && Number(call.statusCode || 0) >= 200 && Number(call.statusCode || 0) < 300
}

function recentCallBarClass(call: RelayModelRecentCall) {
  if (recentCallSucceeded(call)) return 'bg-emerald-400 shadow-[0_2px_5px_rgba(16,185,129,0.28)]'
  if (call.status === 'failed' || Number(call.statusCode || 0) >= 400) return 'bg-rose-400 shadow-[0_2px_5px_rgba(244,63,94,0.22)]'
  return 'bg-amber-400'
}

function recentCallBarHeight(call: RelayModelRecentCall, index: number) {
  const durationFactor = Math.min(10, Math.round(Number(call.durationMs || 0) / 1_000))
  return 6 + ((index * 3 + durationFactor) % 11)
}

function recentCallTitle(call: RelayModelRecentCall) {
  const label = recentCallSucceeded(call) ? '调用成功' : call.status === 'failed' ? '调用失败' : '调用状态未知'
  const duration = Number(call.durationMs || 0) ? ` · ${(Number(call.durationMs) / 1_000).toFixed(2)}s` : ''
  const time = call.createdAt ? ` · ${new Date(call.createdAt).toLocaleString()}` : ''
  return `${label}${duration}${time}`
}

function modelStateBadgeClass(model: { lastStatus: string }) {
  if (model.lastStatus === 'success' || model.lastStatus === 'available') return 'bg-emerald-50 text-emerald-700'
  if (model.lastStatus === 'failed') return 'bg-rose-50 text-rose-700'
  if (model.lastStatus === 'disabled') return 'bg-slate-100 text-slate-500'
  return 'bg-amber-50 text-amber-700'
}

function selectMenu(item: { id: string; route?: string }) {
  mobileMenuOpen.value = false
  if (item.route) {
    router.push(item.route)
    return
  }

  if (item.id === activeMenu.value) return

  const currentIndex = menus.findIndex((menu) => menu.id === activeMenu.value)
  const nextIndex = menus.findIndex((menu) => menu.id === item.id)
  menuDirection.value = nextIndex < currentIndex ? 'backward' : 'forward'
  menuProgressKey.value += 1
  menuSwitching.value = true
  if (menuSwitchTimer !== undefined) window.clearTimeout(menuSwitchTimer)
  menuSwitchTimer = window.setTimeout(() => {
    menuSwitching.value = false
    menuSwitchTimer = undefined
  }, 460)
  activeMenu.value = item.id
  void loadSection(item.id)

  if (window.scrollY > 0) {
    window.scrollTo({ top: 0, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
  }
}

async function changeLogPage(page: number) {
  const nextPage = Math.max(1, Math.min(page, logPages.value))
  if (nextPage === logPage.value && loadedSections.value.has('logs')) return
  logPage.value = nextPage
  expandedLogIds.value = new Set()
  await loadSection('logs', true)
}

watch([logSearch, logStatusFilter, logSort], () => {
  if (activeMenu.value !== 'logs') return
  logPage.value = 1
  if (logFilterTimer !== undefined) window.clearTimeout(logFilterTimer)
  logFilterTimer = window.setTimeout(() => {
    logFilterTimer = undefined
    void loadSection('logs', true)
  }, 260)
})

onBeforeUnmount(() => {
  if (menuSwitchTimer !== undefined) window.clearTimeout(menuSwitchTimer)
  if (logFilterTimer !== undefined) window.clearTimeout(logFilterTimer)
})

onMounted(async () => {
  await load()
})
</script>

<template>
  <div class="relay-page min-h-screen bg-white text-slate-950">
    <div
      v-if="mobileMenuOpen"
      class="fixed inset-0 z-30 bg-slate-950/30 backdrop-blur-[2px] md:hidden"
      aria-hidden="true"
      @click="mobileMenuOpen = false"
    ></div>

    <aside
      class="fixed inset-y-0 left-0 z-40 flex w-[248px] flex-col border-r border-slate-200/80 bg-white shadow-[12px_0_40px_rgba(15,23,42,0.04)] transition-[width,transform] duration-300 ease-[cubic-bezier(.2,.8,.2,1)] md:translate-x-0"
      :class="[
        mobileMenuOpen ? 'translate-x-0' : '-translate-x-full',
        sidebarCollapsed ? 'md:w-[72px]' : 'md:w-[248px]'
      ]"
    >
      <div class="relative flex h-[72px] shrink-0 items-center border-b border-slate-100 px-5" :class="sidebarCollapsed ? 'md:justify-center md:px-0' : ''">
        <button class="flex min-w-0 items-center gap-3 text-left" @click="router.push('/')">
          <img class="h-10 w-10 shrink-0 rounded-xl" src="/favicon.ico" alt="logo" />
          <span
            class="overflow-hidden whitespace-nowrap text-lg font-black tracking-tight transition-[width,opacity] duration-200"
            :class="sidebarCollapsed ? 'md:w-0 md:opacity-0' : 'w-[160px] opacity-100'"
          >imageCreater API</span>
        </button>
        <button
          type="button"
          class="absolute -right-3 top-1/2 hidden h-7 w-7 -translate-y-1/2 place-items-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition hover:border-emerald-300 hover:text-emerald-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 md:grid"
          :aria-label="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
          :title="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
          @click="toggleSidebar"
        >
          <svg viewBox="0 0 24 24" class="h-4 w-4 transition-transform duration-300" :class="sidebarCollapsed ? 'rotate-180' : ''" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m15 18-6-6 6-6" />
          </svg>
        </button>
        <button type="button" class="ml-auto grid h-9 w-9 place-items-center rounded-xl text-slate-500 hover:bg-slate-100 md:hidden" aria-label="关闭导航" @click="mobileMenuOpen = false">
          <svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
        </button>
      </div>
      <nav class="app-sidebar-nav flex-1 space-y-1 overflow-y-auto px-3 py-4" :class="sidebarCollapsed ? 'md:flex md:flex-col md:items-center md:px-0' : ''">
        <button
          v-for="item in menus"
          :key="item.id"
          class="group flex h-11 w-full items-center gap-3 rounded-xl px-3 text-left text-sm font-bold transition duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500"
          :class="[
            activeMenu === item.id ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-100' : 'text-slate-500 hover:bg-slate-50 hover:text-slate-950',
            sidebarCollapsed ? 'md:h-11 md:w-11 md:flex-none md:justify-center md:px-0' : ''
          ]"
          :title="sidebarCollapsed ? item.label : undefined"
          @click="selectMenu(item)"
        >
          <svg viewBox="0 0 24 24" class="h-[19px] w-[19px] shrink-0" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path :d="menuIconPaths[item.icon]" />
          </svg>
          <span class="overflow-hidden whitespace-nowrap transition-[width,opacity] duration-200" :class="sidebarCollapsed ? 'md:w-0 md:opacity-0' : 'w-auto opacity-100'">{{ item.label }}</span>
        </button>
      </nav>
    </aside>

    <div class="transition-[padding] duration-300 ease-[cubic-bezier(.2,.8,.2,1)]" :class="sidebarCollapsed ? 'md:pl-[72px]' : 'md:pl-[248px]'">
      <header class="sticky top-0 z-20 flex min-h-[72px] items-center justify-between gap-3 border-b border-slate-200/80 bg-white/92 px-3 backdrop-blur-xl sm:px-5 md:px-7">
        <div class="flex min-w-0 items-center gap-3">
          <button type="button" class="grid h-10 w-10 shrink-0 place-items-center rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-50 md:hidden" aria-label="打开导航" @click="mobileMenuOpen = true">
            <svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" /></svg>
          </button>
          <div class="min-w-0">
          <Transition name="relay-title" mode="out-in">
            <h1 :key="activeMenu" class="text-2xl font-black">{{ menus.find((item) => item.id === activeMenu)?.label }}</h1>
          </Transition>
            <p class="mt-0.5 hidden text-xs font-semibold text-slate-500 lg:block">独立 API 中转站 · 账号、余额与原项目互通</p>
          </div>
        </div>
        <div class="flex shrink-0 items-center gap-2 sm:gap-3">
          <span class="hidden rounded-full bg-emerald-50 px-3 py-2 text-sm font-black text-emerald-700 sm:inline-flex">${{ balance.toFixed(6) }}</span>
          <button
            type="button"
            class="relative inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-2.5 text-slate-600 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-2 sm:px-3"
            aria-label="打开公告中心"
            title="公告中心"
            @click="openAnnouncements"
          >
            <svg viewBox="0 0 24 24" class="h-[19px] w-[19px] shrink-0" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
            </svg>
            <span class="hidden text-sm font-black sm:inline">公告</span>
            <span class="hidden min-w-5 rounded-full bg-emerald-100 px-1.5 py-0.5 text-center text-[10px] font-black leading-4 text-emerald-700 sm:inline-block">{{ announcements.length > 99 ? '99+' : announcements.length }}</span>
            <span v-if="announcements.length" class="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-rose-500 ring-2 ring-white sm:hidden" aria-hidden="true"></span>
          </button>
          <button class="hidden rounded-full border border-slate-200 px-3 py-2 text-sm font-black text-slate-700 transition hover:bg-slate-50 disabled:opacity-60 sm:inline-flex" :disabled="loading" @click="load">{{ loading ? '刷新中' : '刷新' }}</button>
          <button class="grid h-10 w-10 place-items-center rounded-full bg-gradient-to-br from-pink-100 to-sky-100 text-sm font-black">{{ auth.userInfo?.username?.slice(0, 1).toUpperCase() || 'U' }}</button>
        </div>
        <div v-if="menuSwitching" :key="menuProgressKey" class="relay-menu-progress" aria-hidden="true">
          <span></span>
        </div>
      </header>

      <main
        class="relay-shell"
        :class="activeMenu === 'logs' || activeMenu === 'keys' || activeMenu === 'channels' || activeMenu === 'subscription' || activeMenu === 'orders' || activeMenu === 'profile'
          ? 'h-[calc(100dvh-72px)] overflow-hidden p-3 sm:p-4 md:p-5'
          : 'min-h-[calc(100vh-72px)] px-4 py-8 md:px-8'"
      >
        <div v-if="!auth.isAuthenticated" class="mx-auto max-w-xl rounded-2xl border border-amber-100 bg-amber-50 p-6 text-center">
          <p class="text-lg font-black text-amber-800">登录后查看中转站</p>
          <button class="mt-4 rounded-xl bg-slate-950 px-5 py-3 text-sm font-black text-white" @click="router.push('/login')">登录账号</button>
        </div>

        <Transition :name="menuTransitionName" mode="out-in">
          <div v-if="auth.isAuthenticated" :key="activeMenu" class="relay-view-frame h-full min-h-0">
          <section v-if="activeMenu === 'dashboard'" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <article
              v-for="(card, index) in dashboardCards"
              :key="card.label"
              class="dash-card group relative overflow-hidden rounded-2xl border border-slate-100 bg-white p-5 shadow-sm transition-all duration-300 hover:-translate-y-1 hover:border-slate-200 hover:shadow-xl hover:shadow-slate-200/60"
              :style="{ '--d': `${index * 45}ms`, '--glow': toneGlow(card.tone) }"
            >
              <span class="dash-card-glow" aria-hidden="true"></span>
              <div class="relative flex items-start justify-between gap-4">
                <div class="min-w-0">
                  <p class="text-sm font-bold text-slate-500">{{ card.label }}</p>
                  <p class="mt-2 truncate text-3xl font-black tracking-normal" :class="toneTextClass(card.tone)">
                    <AnimatedNumber :value="card.raw" :format="cardFormat(card.kind)" />
                  </p>
                </div>
                <span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl ring-1 transition-transform duration-300 group-hover:-rotate-3 group-hover:scale-110" :class="toneIconClass(card.tone)">
                  <svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path :d="card.icon" />
                  </svg>
                </span>
              </div>
              <p class="relative mt-3 text-xs font-semibold text-slate-500">{{ card.sub }}</p>
            </article>
          </section>

          <section v-if="activeMenu === 'dashboard'" class="dash-section mt-6 rounded-2xl border border-slate-100 bg-white p-5 shadow-sm" style="--d: 90ms">
            <div class="flex items-center justify-between gap-3">
              <div>
                <p class="text-sm font-bold uppercase tracking-[0.18em] text-emerald-600">公告</p>
                <h2 class="mt-2 text-xl font-black text-slate-950">最新通知</h2>
              </div>
              <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-500">{{ announcements.length }} 条</span>
            </div>
            <div class="mt-5 grid gap-3">
              <div v-if="announcementsLoading" class="grid min-h-24 place-items-center rounded-2xl border border-dashed border-slate-200 bg-slate-50/70">
                <RequestLoader label="正在读取公告" :cell-size="10" />
              </div>
              <button
                v-for="(item, index) in announcements"
                :key="item.id"
                v-else
                class="relay-list-item rounded-2xl border border-slate-100 bg-slate-50 p-4 text-left transition hover:border-emerald-200 hover:bg-emerald-50/60"
                :style="{ '--i': index }"
                @click="openAnnouncement(item)"
              >
                <div class="flex items-center justify-between gap-3">
                  <p class="text-base font-black text-slate-950">{{ item.title }}</p>
                  <span class="shrink-0 text-xs font-semibold text-slate-400">{{ (item.publishedAt || item.createdAt || '').slice(0, 16).replace('T', ' ') }}</span>
                </div>
                <p class="mt-2 line-clamp-2 text-sm font-semibold leading-6 text-slate-600">{{ item.content }}</p>
              </button>
              <div v-if="!announcementsLoading && !announcements.length" class="rounded-2xl border border-dashed border-slate-200 p-8 text-center text-sm font-black text-slate-500">暂无公告</div>
            </div>
          </section>

          <section v-if="activeMenu === 'dashboard'" class="dash-section mt-6 grid gap-6 xl:grid-cols-[0.92fr_1.08fr]" style="--d: 140ms">
            <RelayModelDistribution :rows="distributionRows" />
            <RelayTrendChart :rows="chartRows" :loading="activeMenuLoading" @refresh="load" />
          </section>

          <section v-if="activeMenu === 'dashboard'" class="dash-section mt-6 grid gap-6 xl:grid-cols-[1.08fr_0.92fr]" style="--d: 220ms">
            <RelayRecentCalls :logs="logs" @view-all="selectMenu({ id: 'logs' })" />

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

          <section v-if="activeMenu === 'keys'" class="flex h-full min-h-0 flex-col overflow-hidden rounded-[22px] border border-slate-200/80 bg-white shadow-[0_14px_45px_rgba(15,23,42,0.07)]">
            <div class="shrink-0 border-b border-slate-100 px-4 py-4 sm:px-5">
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div class="flex items-center gap-2">
                    <h2 class="text-lg font-black tracking-tight text-slate-950">密钥管理</h2>
                    <span class="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-black text-slate-500">{{ filteredTokens.length }} / {{ tokens.length }}</span>
                  </div>
                  <p class="mt-1 text-xs font-semibold text-slate-500">集中查看用量、限制与最近活动，危险操作均需二次确认</p>
                </div>
                <div class="flex items-center gap-2">
                  <button type="button" class="inline-flex h-9 items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 text-xs font-black text-slate-600 transition hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-700" @click="showIntegrationGuide = true">
                    <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 4h14v16H5V4zm3 4h8m-8 4h8m-8 4h5" /></svg>
                    接入指南
                  </button>
                  <button type="button" class="grid h-9 w-9 place-items-center rounded-xl border border-slate-200 text-slate-600 transition hover:bg-slate-50 disabled:opacity-50" :disabled="activeMenuLoading" aria-label="刷新密钥" title="刷新密钥" @click="load">
                    <svg viewBox="0 0 24 24" class="h-4 w-4" :class="activeMenuLoading ? 'animate-spin' : ''" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6v5h-5M4 18v-5h5M18.5 9A7 7 0 0 0 6 6.5L4 11m16 2-2 4.5A7 7 0 0 1 5.5 15" /></svg>
                  </button>
                  <button type="button" class="inline-flex h-9 items-center gap-2 rounded-xl bg-emerald-600 px-3.5 text-xs font-black text-white shadow-lg shadow-emerald-100 transition hover:-translate-y-0.5 hover:bg-emerald-700" @click="openKeyDialog">
                    <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg>
                    创建密钥
                  </button>
                </div>
              </div>

              <div role="note" class="mt-2 flex items-center gap-2 rounded-lg border border-amber-200/80 bg-amber-50/85 px-2.5 py-2 text-[11px] text-amber-950 shadow-sm shadow-amber-100/60 sm:px-3">
                <span class="grid h-6 w-6 shrink-0 place-items-center rounded-md bg-amber-500 text-white shadow-sm shadow-amber-200" aria-hidden="true">
                  <svg viewBox="0 0 24 24" class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m12 3 9 17H3L12 3Z" /><path d="M12 9v4m0 3h.01" /></svg>
                </span>
                <p class="min-w-0 leading-4">
                  <strong class="font-black text-amber-900">余额扣款保护</strong>
                  <span class="ml-2 font-semibold text-amber-800/80">余额不足以扣款时会自动禁用当前密钥；补充余额后请手动启用。</span>
                </p>
              </div>

              <div class="mt-4 grid grid-cols-2 gap-2 lg:grid-cols-4">
                <article class="rounded-xl border border-slate-100 bg-slate-50/80 px-3 py-2.5"><p class="text-[10px] font-black uppercase tracking-[0.12em] text-slate-400">密钥总数</p><div class="mt-1 flex items-end justify-between gap-2"><strong class="text-xl font-black text-slate-950">{{ tokens.length }}</strong><span class="text-[11px] font-bold text-slate-400">当前账号</span></div></article>
                <article class="rounded-xl border border-emerald-100 bg-emerald-50/70 px-3 py-2.5"><p class="text-[10px] font-black uppercase tracking-[0.12em] text-emerald-600/70">活跃密钥</p><div class="mt-1 flex items-end justify-between gap-2"><strong class="text-xl font-black text-emerald-700">{{ activeTokens.length }}</strong><span class="text-[11px] font-bold text-emerald-600">可正常调用</span></div></article>
                <article class="rounded-xl border border-cyan-100 bg-cyan-50/60 px-3 py-2.5"><p class="text-[10px] font-black uppercase tracking-[0.12em] text-cyan-600/70">今日消费</p><div class="mt-1 flex items-end justify-between gap-2"><strong class="text-xl font-black text-cyan-700">{{ money(keyTodayCost) }}</strong><span class="text-[11px] font-bold text-cyan-600">全部密钥</span></div></article>
                <article class="rounded-xl border border-amber-100 bg-amber-50/60 px-3 py-2.5"><p class="text-[10px] font-black uppercase tracking-[0.12em] text-amber-600/70">累计用量</p><div class="mt-1 flex items-end justify-between gap-2"><strong class="text-xl font-black text-amber-700">{{ money(keyUsedQuota) }}</strong><span class="text-[11px] font-bold text-amber-600">USD</span></div></article>
              </div>

              <div class="mt-3 flex flex-col gap-2 lg:flex-row lg:items-center">
                <label class="relative min-w-0 flex-1">
                  <span class="sr-only">搜索 API 密钥</span>
                  <svg viewBox="0 0 24 24" class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></svg>
                  <input v-model="keySearch" class="h-10 w-full rounded-xl border border-slate-200 bg-white pl-9 pr-9 text-sm font-semibold text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-emerald-400 focus:ring-2 focus:ring-emerald-100" placeholder="搜索名称或密钥片段..." />
                  <button v-if="keySearch" type="button" class="absolute right-2 top-1/2 grid h-6 w-6 -translate-y-1/2 place-items-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="清除搜索" @click="keySearch = ''">×</button>
                </label>
                <div class="flex items-center gap-2 overflow-x-auto pb-0.5 lg:overflow-visible lg:pb-0">
                  <select v-model="keyGroupFilter" class="h-10 shrink-0 rounded-xl border border-slate-200 bg-white px-3 text-xs font-black text-slate-600 outline-none focus:border-emerald-400 focus:ring-2 focus:ring-emerald-100"><option value="">全部分组</option><option v-for="group in groups" :key="group.code" :value="group.code">{{ group.name }}</option></select>
                  <div class="inline-flex shrink-0 rounded-xl bg-slate-100 p-1" aria-label="按状态筛选密钥">
                    <button v-for="option in [{ value: '', label: '全部' }, { value: 'active', label: '活跃' }, { value: 'disabled', label: '禁用' }]" :key="option.value" type="button" class="h-8 rounded-lg px-3 text-xs font-black transition" :class="keyStatusFilter === option.value ? 'bg-white text-slate-950 shadow-sm' : 'text-slate-500 hover:text-slate-800'" @click="keyStatusFilter = option.value">{{ option.label }}</button>
                  </div>
                  <button type="button" class="inline-flex h-10 shrink-0 items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 font-mono text-[11px] font-black text-slate-500 transition hover:border-emerald-300 hover:text-emerald-700" @click="copyText(apiBase, 'api-base')">
                    <span class="max-w-48 truncate">{{ apiBase }}</span>
                    <span class="font-sans text-emerald-600">{{ copied === 'api-base' ? '已复制' : '复制' }}</span>
                  </button>
                </div>
              </div>
            </div>

            <div class="hidden shrink-0 grid-cols-[minmax(220px,1.45fr)_minmax(170px,.95fr)_minmax(155px,.8fr)_minmax(155px,.85fr)_auto] items-center gap-4 border-b border-slate-100 bg-slate-50/80 px-5 py-2.5 text-[10px] font-black uppercase tracking-[0.12em] text-slate-400 xl:grid"><span>密钥 / 分组</span><span>用量</span><span>限制 / 有效期</span><span>状态 / 活动</span><span class="text-right">操作</span></div>

            <div class="relative min-h-0 flex-1 overflow-y-auto overscroll-contain" tabindex="0">
              <div v-if="activeMenuLoading" class="relay-list-refresh-bar" aria-hidden="true"><span></span></div>
              <div v-if="activeMenuLoading && !tokens.length" class="grid h-full min-h-52 place-items-center"><RequestLoader label="正在读取 API 密钥" :cell-size="13" /></div>
              <div v-else-if="filteredTokens.length" class="divide-y divide-slate-100">
                <article v-for="(item, index) in filteredTokens" :key="item.id" class="relay-list-item grid gap-4 px-4 py-4 transition hover:bg-slate-50/80 sm:px-5 xl:grid-cols-[minmax(220px,1.45fr)_minmax(170px,.95fr)_minmax(155px,.8fr)_minmax(155px,.85fr)_auto] xl:items-center" :style="{ '--i': index }">
                  <div class="min-w-0">
                    <div class="flex min-w-0 items-center gap-2"><span class="h-2 w-2 shrink-0 rounded-full" :class="item.enabled ? 'bg-emerald-500 shadow-[0_0_0_4px_rgba(16,185,129,0.10)]' : 'bg-slate-300'"></span><p class="truncate text-sm font-black text-slate-950" :title="item.name">{{ item.name }}</p></div>
                    <div class="mt-2 flex min-w-0 flex-wrap items-center gap-2 pl-4"><code class="rounded-lg bg-slate-100 px-2 py-1 text-[11px] font-black text-teal-700">{{ item.tokenPreview }}</code><span class="rounded-lg bg-emerald-50 px-2 py-1 text-[10px] font-black text-emerald-700">{{ groupOf(item.groups)?.name || item.groups }} · {{ Number(groupOf(item.groups)?.ratio || 1).toFixed(3) }}x</span></div>
                    <p class="mt-2 pl-4 text-[10px] font-semibold text-slate-400">创建于 {{ item.createdAt ? item.createdAt.replace('T', ' ').slice(0, 16) : '-' }}</p>
                  </div>

                  <div>
                    <div class="flex items-center justify-between gap-3 text-xs"><span class="font-semibold text-slate-400">今日</span><strong class="font-black text-cyan-700">{{ money(Number(item.todayCost || 0)) }}</strong></div>
                    <div class="mt-1 flex items-center justify-between gap-3 text-xs"><span class="font-semibold text-slate-400">累计</span><strong class="font-black text-slate-700">{{ money(Number(item.usedQuota || 0)) }}</strong></div>
                    <div v-if="Number(item.quota || 0) > 0" class="mt-2"><div class="h-1.5 overflow-hidden rounded-full bg-slate-100"><div class="h-full rounded-full bg-gradient-to-r from-cyan-400 to-emerald-500" :style="{ width: `${tokenQuotaPercent(item)}%` }"></div></div><p class="mt-1 text-[10px] font-bold text-slate-400">额度 {{ tokenQuotaPercent(item).toFixed(1) }}%</p></div>
                    <p v-else class="mt-2 text-[10px] font-bold text-slate-400">不限额度 · {{ item.requestCount || 0 }} 次请求</p>
                  </div>

                  <div class="grid grid-cols-2 gap-2 text-xs xl:block">
                    <div><p class="font-semibold text-slate-400">速率限制</p><p class="mt-1 font-black text-slate-700">{{ item.rpmLimit || 0 }} RPM / {{ item.tpmLimit || 0 }} TPM</p></div>
                    <div class="xl:mt-2"><p class="font-semibold text-slate-400">有效期</p><p class="mt-1 font-black text-slate-700">{{ item.expiresAt ? item.expiresAt.replace('T', ' ').slice(0, 16) : '永久有效' }}</p></div>
                  </div>

                  <div class="flex items-center justify-between gap-3 xl:block">
                    <span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-black" :class="item.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'"><span class="h-1.5 w-1.5 rounded-full" :class="item.enabled ? 'bg-emerald-500' : 'bg-slate-400'"></span>{{ item.enabled ? '运行中' : '已禁用' }}</span>
                    <div class="text-right xl:mt-2 xl:text-left"><p class="text-[10px] font-semibold text-slate-400">上次使用</p><p class="mt-1 text-[11px] font-black text-slate-600">{{ item.lastUsedAt ? item.lastUsedAt.replace('T', ' ').slice(5, 16) : '尚未使用' }}</p></div>
                  </div>

                  <div class="grid grid-cols-2 gap-2 xl:flex xl:flex-wrap xl:justify-end">
                    <button type="button" class="inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-emerald-600 px-3 text-xs font-black text-white shadow-sm shadow-emerald-100 transition hover:-translate-y-0.5 hover:bg-emerald-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60" :disabled="tokenSecretActionId === item.id" @click="copyToken(item)"><svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="8" y="8" width="11" height="11" rx="2" /><path d="M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h3" /></svg>{{ tokenSecretActionId === item.id ? '读取中' : copied === `token-${item.id}` ? '已复制' : '复制密钥' }}</button>
                    <button type="button" class="group inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-blue-200 bg-gradient-to-br from-blue-50 to-indigo-50 px-3 text-xs font-black text-blue-700 shadow-sm shadow-blue-100 transition hover:-translate-y-0.5 hover:border-blue-300 hover:from-blue-100 hover:to-indigo-100 hover:shadow-blue-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60" :disabled="tokenSecretActionId === item.id" title="按密钥分组自动导入 Codex（OpenAI）或 Claude" @click="requestCcSwitchImport(item)"><svg viewBox="0 0 24 24" class="h-[18px] w-[18px] transition-transform duration-200 group-hover:translate-x-0.5" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3.5" y="4.5" width="12" height="12" rx="2.2" /><path d="M7 19.5h5M9.5 16.5v3M13 10.5h7m-2.5-2.5 2.5 2.5-2.5 2.5" /></svg><span class="hidden 2xl:inline">导入 CCSwitch</span><span class="2xl:hidden">导入</span></button>
                    <button type="button" class="inline-flex h-10 items-center justify-center gap-2 rounded-xl border px-3 text-xs font-black transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2" :class="item.enabled ? 'border-amber-200 bg-amber-50 text-amber-700 hover:bg-amber-100 focus-visible:ring-amber-500' : 'border-emerald-200 bg-emerald-50 text-emerald-700 hover:bg-emerald-100 focus-visible:ring-emerald-500'" @click="openKeyAction(item, 'toggle')"><svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M12 2v10M6.3 5.8a8 8 0 1 0 11.4 0" /></svg>{{ item.enabled ? '禁用' : '启用' }}</button>
                    <button type="button" class="grid h-10 w-[42px] place-items-center rounded-xl border border-rose-200 bg-rose-50 text-rose-600 transition hover:bg-rose-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-2" aria-label="删除密钥" title="删除密钥" @click="openKeyAction(item, 'delete')"><svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3m-8 0 1 14h8l1-14M10 11v6m4-6v6" /></svg></button>
                  </div>
                </article>
              </div>
              <div v-else class="grid h-full min-h-52 place-items-center px-6 text-center"><div><div class="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-slate-100 text-slate-400"><svg viewBox="0 0 24 24" class="h-6 w-6" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M15 7a4 4 0 1 1-2.4 3.67L4 19.27V21h1.73l1-1H9v-2h2v-2h2l2.33-2.33A4 4 0 0 1 15 7z" /></svg></div><p class="mt-3 text-sm font-black text-slate-700">{{ tokens.length ? '没有匹配的 API 密钥' : '还没有 API 密钥' }}</p><button v-if="tokens.length" type="button" class="mt-2 text-xs font-black text-emerald-700" @click="keySearch = ''; keyGroupFilter = ''; keyStatusFilter = ''">清除筛选条件</button><button v-else type="button" class="mt-3 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-black text-white" @click="openKeyDialog">创建第一个密钥</button></div></div>
            </div>
          </section>

          <section v-if="activeMenu === 'logs'" class="flex h-full min-h-0 flex-col overflow-hidden rounded-[22px] border border-slate-200/80 bg-white shadow-[0_14px_45px_rgba(15,23,42,0.07)]">
            <div class="shrink-0 border-b border-slate-100 px-4 py-4 sm:px-5">
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div class="flex items-center gap-2">
                    <h2 class="text-lg font-black tracking-tight text-slate-950">调用明细</h2>
                    <span class="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-black text-slate-500">{{ logTotal }} 条记录</span>
                  </div>
                  <p class="mt-1 text-xs font-semibold text-slate-500">聚合关键信息，点击任意记录查看完整请求详情</p>
                </div>
                <div class="flex items-center gap-2">
                  <button
                    type="button"
                    class="inline-flex h-9 items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 px-3 text-xs font-black text-rose-700 transition hover:bg-rose-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-400"
                    @click="showErrorLogsDialog = true"
                  >
                    <span class="h-2 w-2 rounded-full bg-rose-500"></span>
                    <span class="hidden sm:inline">错误日志</span>
                    <span class="rounded-full bg-white px-1.5 py-0.5 text-[10px]">{{ errorLogs.length }}</span>
                  </button>
                  <button type="button" class="grid h-9 w-9 place-items-center rounded-xl border border-slate-200 text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50" :disabled="activeMenuLoading" aria-label="刷新使用记录" title="刷新使用记录" @click="load">
                    <svg viewBox="0 0 24 24" class="h-4 w-4" :class="activeMenuLoading ? 'animate-spin' : ''" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6v5h-5M4 18v-5h5M18.5 9A7 7 0 0 0 6 6.5L4 11m16 2-2 4.5A7 7 0 0 1 5.5 15" /></svg>
                  </button>
                  <button type="button" class="inline-flex h-9 items-center gap-2 rounded-xl bg-slate-950 px-3 text-xs font-black text-white transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-40" :disabled="!logs.length" @click="exportUsageCsv">
                    <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3v12m0 0 4-4m-4 4-4-4M5 19h14" /></svg>
                    <span class="hidden sm:inline">导出 CSV</span>
                  </button>
                </div>
              </div>

              <div class="mt-4 grid grid-cols-2 gap-2 lg:grid-cols-4">
                <article class="rounded-xl border border-slate-100 bg-slate-50/80 px-3 py-2.5">
                  <p class="text-[10px] font-black uppercase tracking-[0.12em] text-slate-400">请求总数</p>
                  <div class="mt-1 flex items-end justify-between gap-2"><strong class="text-xl font-black text-slate-950">{{ logs.length }}</strong><span class="text-[11px] font-bold text-slate-400">最近记录</span></div>
                </article>
                <article class="rounded-xl border border-emerald-100 bg-emerald-50/70 px-3 py-2.5">
                  <p class="text-[10px] font-black uppercase tracking-[0.12em] text-emerald-600/70">成功率</p>
                  <div class="mt-1 flex items-end justify-between gap-2"><strong class="text-xl font-black text-emerald-700">{{ logSuccessRate.toFixed(1) }}%</strong><span class="text-[11px] font-bold text-emerald-600">{{ successfulLogCount }} 成功</span></div>
                </article>
                <article class="rounded-xl border border-cyan-100 bg-cyan-50/60 px-3 py-2.5">
                  <p class="text-[10px] font-black uppercase tracking-[0.12em] text-cyan-600/70">平均响应</p>
                  <div class="mt-1 flex items-end justify-between gap-2"><strong class="text-xl font-black text-cyan-700">{{ (logAverageDuration / 1000).toFixed(2) }}s</strong><span class="text-[11px] font-bold text-cyan-600">每次调用</span></div>
                </article>
                <article class="rounded-xl border border-amber-100 bg-amber-50/60 px-3 py-2.5">
                  <p class="text-[10px] font-black uppercase tracking-[0.12em] text-amber-600/70">记录消费</p>
                  <div class="mt-1 flex items-end justify-between gap-2"><strong class="text-xl font-black text-amber-700">{{ money(logTotalCost) }}</strong><span class="text-[11px] font-bold text-amber-600">USD</span></div>
                </article>
              </div>

              <div class="mt-3 flex flex-col gap-2 lg:flex-row lg:items-center">
                <label class="relative min-w-0 flex-1">
                  <span class="sr-only">搜索使用记录</span>
                  <svg viewBox="0 0 24 24" class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></svg>
                  <input v-model="logSearch" class="h-10 w-full rounded-xl border border-slate-200 bg-white pl-9 pr-9 text-sm font-semibold text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-emerald-400 focus:ring-2 focus:ring-emerald-100" placeholder="搜索密钥、模型、端点或渠道..." />
                  <button v-if="logSearch" type="button" class="absolute right-2 top-1/2 grid h-6 w-6 -translate-y-1/2 place-items-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="清除搜索" @click="logSearch = ''">×</button>
                </label>
                <div class="flex items-center gap-2 overflow-x-auto pb-0.5 lg:overflow-visible lg:pb-0">
                  <div class="inline-flex shrink-0 rounded-xl bg-slate-100 p-1" aria-label="按状态筛选">
                    <button v-for="option in [{ value: 'all', label: '全部' }, { value: 'success', label: '成功' }, { value: 'failed', label: '异常' }]" :key="option.value" type="button" class="h-8 rounded-lg px-3 text-xs font-black transition" :class="logStatusFilter === option.value ? 'bg-white text-slate-950 shadow-sm' : 'text-slate-500 hover:text-slate-800'" @click="logStatusFilter = option.value">{{ option.label }}</button>
                  </div>
                  <select v-model="logSort" class="h-10 shrink-0 rounded-xl border border-slate-200 bg-white px-3 text-xs font-black text-slate-600 outline-none transition focus:border-emerald-400 focus:ring-2 focus:ring-emerald-100" aria-label="使用记录排序">
                    <option value="latest">最新优先</option>
                    <option value="slowest">耗时最高</option>
                    <option value="cost">费用最高</option>
                  </select>
                </div>
              </div>
            </div>

            <div class="hidden shrink-0 grid-cols-[minmax(220px,1.55fr)_92px_minmax(190px,1.15fr)_115px_135px_32px] items-center gap-4 border-b border-slate-100 bg-slate-50/80 px-5 py-2.5 text-[10px] font-black uppercase tracking-[0.12em] text-slate-400 xl:grid">
              <span>请求</span><span>状态</span><span>Token / 缓存</span><span>费用</span><span>耗时 / 时间</span><span></span>
            </div>

            <div class="relative min-h-0 flex-1 overflow-y-auto overscroll-contain" tabindex="0">
              <div v-if="activeMenuLoading" class="relay-list-refresh-bar" aria-hidden="true"><span></span></div>
              <div v-if="activeMenuLoading && !logs.length" class="grid h-full min-h-52 place-items-center">
                <RequestLoader label="正在读取使用记录" :cell-size="13" />
              </div>
              <div v-else-if="filteredLogs.length" class="divide-y divide-slate-100">
                <article v-for="(log, index) in filteredLogs" :key="log.id" class="relay-list-item group bg-white transition hover:bg-slate-50/80" :style="{ '--i': index }">
                  <button type="button" class="relative grid w-full gap-3 px-4 py-3 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-emerald-500 sm:grid-cols-2 sm:px-5 xl:grid-cols-[minmax(220px,1.55fr)_92px_minmax(190px,1.15fr)_115px_135px_32px] xl:items-center xl:gap-4" :aria-expanded="expandedLogIds.has(log.id)" @click="toggleLogDetails(log.id)">
                    <div class="min-w-0 sm:col-span-2 xl:col-span-1">
                      <div class="flex min-w-0 items-center gap-2">
                        <span class="h-2 w-2 shrink-0 rounded-full" :class="logFailed(log) ? 'bg-rose-500 shadow-[0_0_0_4px_rgba(244,63,94,0.10)]' : 'bg-emerald-500 shadow-[0_0_0_4px_rgba(16,185,129,0.10)]'"></span>
                        <p class="truncate text-sm font-black text-slate-900" :title="log.model">{{ log.model || '-' }}</p>
                        <span class="shrink-0 rounded-md bg-blue-50 px-1.5 py-0.5 text-[10px] font-black uppercase text-blue-600">{{ log.modelType || '-' }}</span>
                        <span v-if="log.thinkingEffort" class="shrink-0 rounded-md bg-violet-50 px-1.5 py-0.5 text-[10px] font-black text-violet-700">思考 {{ log.thinkingEffort }}</span>
                      </div>
                      <p class="mt-1 truncate pl-4 font-mono text-[11px] font-semibold text-slate-400" :title="`${log.tokenName || '-'} · ${log.endpoint || '-'}`">{{ log.tokenName || '-' }} · {{ log.endpoint || '-' }}</p>
                    </div>

                    <div class="flex items-center gap-2 xl:block">
                      <span class="rounded-lg px-2 py-1 text-[11px] font-black" :class="logFailed(log) ? 'bg-rose-50 text-rose-700' : 'bg-emerald-50 text-emerald-700'">{{ logFailed(log) ? `HTTP ${log.statusCode || 500}` : '成功' }}</span>
                      <span class="text-[11px] font-semibold text-slate-400 xl:mt-1 xl:block">{{ log.channelName || '未命名渠道' }}</span>
                    </div>

                    <div class="min-w-0" :title="cacheHitTitle(log)">
                      <div class="flex items-center justify-between gap-3 text-xs font-black">
                        <span><i class="not-italic text-emerald-600">↓ {{ compact(log.promptTokens || 0) }}</i> <i class="ml-2 not-italic text-violet-600">↑ {{ compact(log.completionTokens || 0) }}</i></span>
                        <span class="text-slate-500">{{ compact(log.totalTokens || 0) }}</span>
                      </div>
                      <div class="mt-2 flex items-center gap-2">
                        <div class="h-1.5 min-w-0 flex-1 overflow-hidden rounded-full bg-slate-100"><div class="h-full rounded-full bg-gradient-to-r from-cyan-400 to-emerald-500 transition-[width] duration-500" :style="{ width: `${cacheHitRate(log)}%` }"></div></div>
                        <span class="w-10 text-right text-[10px] font-black text-cyan-600">{{ cacheHitLabel(log) }}</span>
                      </div>
                    </div>

                    <div>
                      <p class="text-sm font-black text-emerald-700">{{ money(log.cost) }}</p>
                      <p class="mt-1 text-[10px] font-bold text-slate-400">用户消费</p>
                    </div>

                    <div class="flex items-center justify-between gap-3 xl:block">
                      <p class="text-sm font-black" :class="Number(log.durationMs || 0) > 30000 ? 'text-amber-600' : 'text-slate-800'">{{ ((log.durationMs || 0) / 1000).toFixed(2) }}s</p>
                      <p class="text-[11px] font-semibold text-slate-400 xl:mt-1">{{ logDate(log.createdAt) }} <span class="text-slate-500">{{ logTime(log.createdAt) }}</span></p>
                    </div>

                    <span class="absolute right-4 top-4 grid h-7 w-7 place-items-center rounded-lg text-slate-400 transition group-hover:bg-white group-hover:text-slate-700 sm:static" :class="expandedLogIds.has(log.id) ? 'rotate-180 bg-slate-100 text-slate-700' : ''">
                      <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
                    </span>
                  </button>

                  <div v-if="expandedLogIds.has(log.id)" class="border-t border-dashed border-slate-200 bg-slate-50/70 px-4 py-4 sm:px-5">
                    <div class="grid gap-4 text-xs font-semibold text-slate-600 md:grid-cols-2 xl:grid-cols-4">
                      <div><p class="font-black text-slate-400">思考强度</p><p class="mt-1.5 text-slate-700">{{ log.thinkingEffort || '-' }}</p></div>
                      <div><p class="font-black text-slate-400">渠道 / 分组</p><p class="mt-1.5 break-all text-slate-700">{{ log.channelName || '-' }} · {{ log.groupNames || '-' }}</p></div>
                      <div><p class="font-black text-slate-400">请求端点</p><p class="mt-1.5 break-all font-mono text-slate-700">{{ log.endpoint || '-' }}</p></div>
                      <div><p class="font-black text-slate-400">缓存明细</p><p class="mt-1.5 text-slate-700">读取 {{ compact(log.cachedTokens || 0) }} · 写入 {{ compact(log.cacheCreationTokens || 0) }}</p></div>
                      <div><p class="font-black text-slate-400">完整时间</p><p class="mt-1.5 text-slate-700">{{ log.createdAt?.replace('T', ' ').slice(0, 19) || '-' }}</p></div>
                    </div>
                    <div class="mt-4">
                      <div class="min-w-0 rounded-xl border border-slate-200 bg-white p-3">
                        <p class="text-[10px] font-black uppercase tracking-[0.12em] text-slate-400">User-Agent</p>
                        <p class="mt-1.5 break-all font-mono text-[11px] font-semibold leading-5 text-slate-600">{{ log.userAgent || '-' }}</p>
                      </div>
                    </div>
                    <p v-if="log.message" class="mt-3 rounded-xl border border-rose-100 bg-rose-50 p-3 text-xs font-semibold leading-5 text-rose-700">{{ log.message }}</p>
                  </div>
                </article>
              </div>
              <div v-else class="grid h-full min-h-52 place-items-center px-6 text-center">
                <div>
                  <div class="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-slate-100 text-slate-400">
                    <svg viewBox="0 0 24 24" class="h-6 w-6" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 4h14v16H5V4zm3 4h8M8 12h8m-8 4h5" /></svg>
                  </div>
                  <p class="mt-3 text-sm font-black text-slate-700">{{ logs.length ? '没有匹配的使用记录' : '暂无使用记录' }}</p>
                  <button v-if="logs.length" type="button" class="mt-2 text-xs font-black text-emerald-700 hover:text-emerald-800" @click="logSearch = ''; logStatusFilter = 'all'">清除筛选条件</button>
                </div>
              </div>
            </div>
            <footer class="flex shrink-0 flex-wrap items-center justify-between gap-3 border-t border-slate-100 bg-slate-50/70 px-4 py-3 sm:px-5">
              <span class="text-xs font-semibold text-slate-500">第 {{ logPage }} 页，共 {{ logPages }} 页，{{ logTotal }} 条记录</span>
              <Pagination :current="logPage" :pages="logPages" @change="changeLogPage" />
            </footer>
          </section>

          <section v-if="activeMenu === 'channels'" class="flex h-full min-h-0 flex-col gap-3">
            <div class="shrink-0 overflow-hidden rounded-[22px] border border-slate-200/80 bg-white shadow-[0_18px_55px_rgba(15,23,42,0.06)]">
              <div class="flex flex-col gap-4 p-4 sm:p-5 xl:flex-row xl:items-center xl:justify-between">
                <div class="flex min-w-0 items-center gap-4">
                  <div class="channel-radar channel-radar-large shrink-0" :class="{ 'is-active': syncingStatus }" aria-hidden="true">
                    <span></span><i></i>
                  </div>
                  <div class="min-w-0">
                    <div class="flex flex-wrap items-center gap-2">
                      <h2 class="text-lg font-black tracking-tight text-slate-950">渠道运行矩阵</h2>
                      <span v-if="syncingStatus" class="rounded-full bg-cyan-50 px-2.5 py-1 text-[10px] font-black text-cyan-700" aria-live="polite">{{ checkedChannelIds.size }} / {{ channels.length }}</span>
                    </div>
                    <p class="mt-1 text-xs font-semibold text-slate-500">逐一检测服务节点状态；内部线路、地址和调度参数不会对用户展示。</p>
                  </div>
                </div>
                <div class="grid grid-cols-3 gap-2 sm:flex sm:items-center">
                  <div class="rounded-xl bg-emerald-50 px-3 py-2 text-center sm:min-w-20"><strong class="block text-base font-black text-emerald-700">{{ availableChannels }}</strong><span class="text-[10px] font-bold text-emerald-600/70">正常</span></div>
                  <div class="rounded-xl bg-rose-50 px-3 py-2 text-center sm:min-w-20"><strong class="block text-base font-black text-rose-700">{{ failedChannels }}</strong><span class="text-[10px] font-bold text-rose-600/70">异常</span></div>
                  <div class="rounded-xl bg-slate-100 px-3 py-2 text-center sm:min-w-20"><strong class="block text-base font-black text-slate-800">{{ channelModelCount }}</strong><span class="text-[10px] font-bold text-slate-500">模型</span></div>
                </div>
              </div>
              <div class="flex flex-col gap-3 border-t border-slate-100 bg-slate-50/70 p-3 sm:flex-row sm:items-center sm:justify-between sm:px-5">
                <label class="relative block min-w-0 flex-1 sm:max-w-md">
                  <svg viewBox="0 0 24 24" class="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
                  <input v-model="channelSearch" class="h-10 w-full rounded-xl border border-slate-200 bg-white pl-10 pr-4 text-sm font-semibold text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-emerald-400 focus:ring-2 focus:ring-emerald-100" placeholder="搜索节点、模型或分组" />
                </label>
                <button class="inline-flex h-10 shrink-0 items-center justify-center gap-2 rounded-xl bg-slate-950 px-4 text-xs font-black text-white shadow-sm transition hover:-translate-y-0.5 hover:bg-emerald-600 disabled:cursor-not-allowed disabled:translate-y-0 disabled:opacity-70" :disabled="syncingStatus || !channels.length" @click="syncStatus">
                  <svg v-if="!syncingStatus" viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><path d="M20 12a8 8 0 1 1-2.34-5.66M20 4v6h-6" /></svg>
                  <span v-else class="channel-radar" aria-hidden="true"><span></span><i></i></span>
                  {{ syncingStatus ? `正在检测 ${checkedChannelIds.size + (checkingChannelId ? 1 : 0)} / ${channels.length}` : '检测全部渠道' }}
                </button>
              </div>
              <div v-if="syncingStatus || checkedChannelIds.size" class="h-1 bg-slate-100" aria-hidden="true">
                <div class="h-full bg-gradient-to-r from-cyan-500 via-emerald-500 to-lime-400 transition-[width] duration-500 ease-out" :style="{ width: `${channels.length ? (checkedChannelIds.size / channels.length) * 100 : 0}%` }"></div>
              </div>
            </div>

            <div class="relative min-h-0 flex-1 overflow-y-auto overscroll-contain pr-0.5">
              <div v-if="activeMenuLoading" class="relay-list-refresh-bar" aria-hidden="true"><span></span></div>
              <div v-if="channelRows.length" class="grid gap-3 pb-1 xl:grid-cols-2 2xl:grid-cols-3">
                <article
                  v-for="(channel, index) in channelRows"
                  :key="channel.id"
                  class="relay-list-item channel-card group relative isolate overflow-hidden rounded-[20px] border bg-white p-4 shadow-[0_10px_35px_rgba(15,23,42,0.045)] transition duration-300"
                  :style="{ '--i': index }"
                  :class="[
                    channel.status === 'available' ? 'border-emerald-100 hover:border-emerald-200' : channel.status === 'failed' ? 'border-rose-100 hover:border-rose-200' : 'border-slate-200 hover:border-slate-300',
                    { 'is-checking': checkingChannelId === channel.id, 'channel-check-complete': checkedChannelIds.has(channel.id) && checkingChannelId !== channel.id }
                  ]"
                >
                  <div v-if="checkingChannelId === channel.id" class="channel-scan-line" aria-hidden="true"></div>
                  <div class="flex items-start justify-between gap-3">
                    <div class="flex min-w-0 items-start gap-3">
                      <div class="grid h-10 w-10 shrink-0 place-items-center rounded-xl font-black" :class="channel.status === 'available' ? 'bg-emerald-50 text-emerald-700' : channel.status === 'failed' ? 'bg-rose-50 text-rose-700' : 'bg-slate-100 text-slate-600'">{{ channel.name.slice(0, 1).toUpperCase() }}</div>
                      <div class="min-w-0">
                        <h3 class="truncate text-base font-black tracking-tight text-slate-950">{{ channel.name }}</h3>
                        <div class="mt-1 flex min-w-0 flex-wrap items-center gap-1.5">
                          <span class="rounded-md px-1.5 py-0.5 text-[9px] font-black ring-1" :class="ruleBadgeClass(channel.channelRule)">{{ ruleLabel(channel.channelRule) }}</span>
                          <span class="max-w-40 truncate text-[11px] font-semibold text-slate-400">兼容协议节点</span>
                        </div>
                      </div>
                    </div>
                    <span class="inline-flex shrink-0 items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-black" :class="checkingChannelId === channel.id ? 'bg-cyan-50 text-cyan-700' : channel.status === 'available' ? 'bg-emerald-50 text-emerald-700' : channel.status === 'failed' ? 'bg-rose-50 text-rose-700' : 'bg-slate-100 text-slate-600'">
                      <span v-if="checkingChannelId === channel.id" class="channel-radar" aria-hidden="true"><span></span><i></i></span>
                      <span v-else class="h-1.5 w-1.5 rounded-full" :class="channel.status === 'available' ? 'bg-emerald-500' : channel.status === 'failed' ? 'bg-rose-500' : 'bg-slate-400'"></span>
                      {{ checkingChannelId === channel.id ? '检测中' : statusText(channel.status) }}
                    </span>
                  </div>

                  <p
                    class="mt-3 min-h-10 line-clamp-2 text-xs font-semibold leading-5"
                    :class="channel.remark ? 'text-slate-600' : 'text-slate-400'"
                    :title="channel.remark || undefined"
                  >{{ channel.remark || '由系统自动选择并调度，节点内部配置不会公开。' }}</p>

                  <div class="mt-3 border-y border-slate-100 py-3">
                    <div class="min-w-0">
                      <p class="text-[9px] font-black uppercase tracking-[0.16em] text-slate-400">Access groups</p>
                      <div class="mt-2 flex flex-wrap gap-1.5">
                        <span v-for="group in channel.groups" :key="group" class="inline-flex items-center gap-1 rounded-lg bg-slate-100 px-2 py-1 text-[10px] font-black text-slate-600">{{ group }}<b class="text-emerald-700">{{ groupRatioLabel(group) }}</b></span>
                      </div>
                    </div>
                  </div>

                  <div class="mt-3">
                    <div class="flex items-center justify-between gap-3"><p class="text-[9px] font-black uppercase tracking-[0.16em] text-slate-400">Enabled models</p><span class="text-[10px] font-bold text-slate-400">{{ channel.enabledModels.length }} 个</span></div>
                    <div class="mt-2 flex max-h-[68px] flex-wrap gap-1.5 overflow-y-auto">
                      <button v-for="model in channel.enabledModels" :key="model.modelId" type="button" class="rounded-lg border border-emerald-100 bg-emerald-50/70 px-2 py-1 text-left text-[10px] font-black text-emerald-700 transition hover:border-emerald-300 hover:bg-emerald-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500" @mouseenter="showPricingTooltip($event, model, channel.channelRule)" @mouseleave="hidePricingTooltip" @focus="showPricingTooltip($event, model, channel.channelRule)" @blur="hidePricingTooltip">{{ publicModelName(model) }}</button>
                      <span v-if="!channel.enabledModels.length" class="text-[11px] font-semibold text-slate-400">暂无绑定模型</span>
                    </div>
                  </div>

                  <div class="mt-3 flex items-center justify-between gap-3">
                    <span class="text-[10px] font-bold text-slate-400">{{ channel.rpmLimit ? `${channel.rpmLimit} RPM` : '不限 RPM' }} · {{ channel.maxConcurrency ? `${channel.maxConcurrency} 并发` : '不限并发' }}</span>
                    <button type="button" class="inline-flex h-8 shrink-0 items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 text-[10px] font-black text-slate-700 transition hover:border-emerald-300 hover:bg-emerald-50 hover:text-emerald-700 disabled:cursor-not-allowed disabled:opacity-50" :disabled="syncingStatus" @click="syncSingleChannel(channel)">
                      <svg viewBox="0 0 24 24" class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><path d="M20 12a8 8 0 1 1-2.34-5.66M20 4v6h-6" /></svg>
                      单独检测
                    </button>
                  </div>
                </article>
              </div>
              <div v-else class="grid h-full min-h-64 place-items-center rounded-[20px] border border-dashed border-slate-200 bg-white/70 text-center">
                <div><div class="channel-radar channel-radar-large mx-auto" aria-hidden="true"><span></span><i></i></div><p class="mt-4 text-sm font-black text-slate-600">没有匹配的渠道</p><button type="button" class="mt-2 text-xs font-black text-emerald-700" @click="channelSearch = ''">清除搜索</button></div>
              </div>
            </div>
          </section>

          <section v-if="activeMenu === 'models'" class="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
            <div class="flex flex-wrap items-end justify-between gap-3"><div><h2 class="text-xl font-black">模型状态</h2><p class="mt-1 text-xs font-semibold text-slate-500">默认展示系统全部模型；状态条来自所有用户的最近调用。</p></div><span class="rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-black text-emerald-700"><span class="mr-1.5 inline-block h-1.5 w-1.5 rounded-full bg-emerald-500"></span>全站实时</span></div>
            <div class="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              <article v-for="(model, index) in modelRows" :key="model.id" class="relay-list-item rounded-xl border border-slate-100 bg-slate-50 p-4" :style="{ '--i': index }">
                <div class="flex items-start justify-between gap-3">
                  <div>
                    <p class="font-black">{{ model.displayName || model.model }}</p>
                    <p class="mt-1 font-mono text-xs font-bold text-slate-500">{{ model.model }}</p>
                  </div>
                  <span class="rounded-full px-3 py-1 text-xs font-black" :class="modelStateBadgeClass(model)">{{ statusText(model.lastStatus) }}</span>
                </div>
                <p class="mt-3 text-xs font-semibold text-slate-500">{{ model.modelType }} · 请求 {{ model.requests }} · Token {{ compact(model.tokens) }}</p>
                <div class="mt-4 border-t border-slate-200/80 pt-3"><div class="flex items-center justify-between gap-3"><span class="text-[10px] font-black uppercase tracking-[0.12em] text-slate-400">Recent calls</span><span class="text-[10px] font-bold text-slate-400">{{ model.recentCalls.length ? `最近 ${model.recentCalls.length}/20 次` : '暂无调用' }}</span></div><div class="mt-2 flex h-5 items-end gap-1" role="list" :aria-label="`${model.displayName || model.model} 最近调用状态`"><span v-if="!model.recentCalls.length" class="h-1.5 w-full rounded-full bg-slate-200"></span><span v-for="(call, index) in model.recentCalls" v-else :key="call.id" class="w-1.5 min-w-1.5 rounded-full transition-transform duration-200 hover:scale-y-125" :class="recentCallBarClass(call)" :style="{ height: `${recentCallBarHeight(call, index)}px` }" role="listitem" :title="recentCallTitle(call)"></span></div></div>
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
                <div v-for="(record, index) in paymentRecords" :key="record.id" class="relay-list-item grid gap-3 p-5 text-sm font-semibold text-slate-600 md:grid-cols-[120px_1fr_120px_180px] md:items-center" :style="{ '--i': index }">
                  <span class="font-black text-slate-950">{{ yuan(record.amount) }}</span>
                  <span>{{ paymentTypeText(record.type) }}</span>
                  <span class="w-fit rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-600">{{ paymentStatusText(record.status) }}</span>
                  <span class="text-slate-500">{{ record.createdAt }}</span>
                </div>
                <div v-if="!paymentRecords.length" class="p-10 text-center text-sm font-black text-slate-400">暂无充值记录</div>
              </div>
            </div>
          </section>

          <section
            v-if="activeMenu === 'subscription' || activeMenu === 'orders'"
            class="relay-pending-page h-full min-h-[420px] overflow-hidden rounded-[22px] border border-slate-200/80"
            :class="activeMenu === 'orders' ? 'is-orders' : 'is-subscription'"
            :aria-labelledby="`pending-page-${activeMenu}`"
          >
            <header class="pending-page-header">
              <div class="pending-page-identity">
                <span class="pending-page-icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <path :d="menuIconPaths[comingSoonPanel.icon]"></path>
                  </svg>
                </span>
                <div>
                  <h2 :id="`pending-page-${activeMenu}`">{{ comingSoonPanel.title }}</h2>
                  <p>{{ comingSoonPanel.subtitle }}</p>
                </div>
              </div>
              <span class="pending-page-badge">即将开放</span>
            </header>

            <div class="pending-table" role="table" :aria-label="`${comingSoonPanel.title}列表`">
              <div class="pending-table-head" role="row">
                <span v-for="column in comingSoonPanel.columns" :key="column" role="columnheader">{{ column }}</span>
              </div>
              <div class="pending-empty" role="row">
                <div role="cell">
                  <span class="pending-empty-icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                      <path :d="menuIconPaths[comingSoonPanel.icon]"></path>
                    </svg>
                  </span>
                  <h3>即将来临...</h3>
                  <p>{{ comingSoonPanel.description }}</p>
                  <div class="pending-dots" aria-hidden="true"><i></i><i></i><i></i></div>
                </div>
              </div>
            </div>

            <footer class="pending-page-footer">页面开放后，数据将自动显示在此处</footer>
          </section>

          <section v-if="activeMenu === 'profile'" class="relative h-full min-h-0 overflow-y-auto overscroll-contain">
            <Transition name="zoom-fade">
              <div v-if="profileSaving" class="absolute inset-0 z-20 grid place-items-center rounded-[24px] bg-white/65 backdrop-blur-[4px]">
                <RequestLoader :label="profileSaving === 'profile' ? '正在保存资料' : '正在更新密码'" :cell-size="11" />
              </div>
            </Transition>

            <div class="mx-auto flex max-w-4xl flex-col gap-4 pb-4">
              <header class="profile-hero dash-section relative isolate overflow-hidden rounded-[24px] p-5 text-white sm:p-7" style="--d: 0ms">
                <div class="profile-hero-grid" aria-hidden="true"></div>
                <span class="profile-hero-glow glow-a" aria-hidden="true"></span>
                <span class="profile-hero-glow glow-b" aria-hidden="true"></span>

                <div class="relative z-10 flex flex-col gap-5 sm:flex-row sm:items-center">
                  <div class="profile-avatar grid h-20 w-20 shrink-0 place-items-center rounded-[26px] text-3xl font-black">{{ auth.userInfo?.username?.slice(0, 1).toUpperCase() || 'U' }}</div>
                  <div class="min-w-0">
                    <div class="flex flex-wrap items-center gap-2">
                      <h2 class="break-all text-2xl font-black tracking-tight">{{ auth.userInfo?.username || 'User' }}</h2>
                      <span class="rounded-full border border-emerald-300/30 bg-emerald-400/10 px-2.5 py-1 text-[10px] font-black text-emerald-300">{{ auth.role === 'ADMIN' ? '管理员' : '普通用户' }}</span>
                      <span class="inline-flex items-center gap-1.5 rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[10px] font-black text-emerald-300"><i class="h-1.5 w-1.5 rounded-full bg-emerald-400 shadow-[0_0_10px_#34d399]"></i>状态正常</span>
                    </div>
                    <p class="mt-1.5 break-all text-xs font-semibold text-slate-300">{{ auth.userInfo?.email || '尚未绑定邮箱' }}</p>
                    <p class="mt-1 text-[10px] font-semibold text-slate-400">账号 ID {{ auth.userInfo?.id || '-' }}<span v-if="auth.userInfo?.createdAt"> · 加入于 {{ auth.userInfo.createdAt.slice(0, 10) }}</span></p>
                  </div>
                  <div class="shrink-0 sm:ml-auto sm:text-right">
                    <p class="text-[10px] font-black uppercase tracking-[0.18em] text-slate-400">账户余额</p>
                    <p class="mt-1 font-mono text-2xl font-black text-emerald-300"><AnimatedNumber :value="balance" :format="cardFormat('money')" /></p>
                  </div>
                </div>

                <div class="relative z-10 mt-6 grid grid-cols-2 gap-2.5 lg:grid-cols-4">
                  <div class="rounded-2xl border border-white/10 bg-white/[0.06] p-3 backdrop-blur-sm"><span class="text-[9px] font-black uppercase tracking-[0.15em] text-slate-400">今日消费</span><strong class="mt-1.5 block truncate font-mono text-sm font-black text-white"><AnimatedNumber :value="displayTodayCost" :format="cardFormat('money')" /></strong></div>
                  <div class="rounded-2xl border border-white/10 bg-white/[0.06] p-3 backdrop-blur-sm"><span class="text-[9px] font-black uppercase tracking-[0.15em] text-slate-400">总请求</span><strong class="mt-1.5 block truncate font-mono text-sm font-black text-white"><AnimatedNumber :value="totalRequests" :format="cardFormat('compact')" /></strong></div>
                  <div class="rounded-2xl border border-white/10 bg-white/[0.06] p-3 backdrop-blur-sm"><span class="text-[9px] font-black uppercase tracking-[0.15em] text-slate-400">API 密钥</span><strong class="mt-1.5 block truncate font-mono text-sm font-black text-white"><AnimatedNumber :value="tokens.length" /> 个</strong></div>
                  <div class="rounded-2xl border border-white/10 bg-white/[0.06] p-3 backdrop-blur-sm"><span class="text-[9px] font-black uppercase tracking-[0.15em] text-slate-400">可用渠道</span><strong class="mt-1.5 block truncate font-mono text-sm font-black text-white"><AnimatedNumber :value="availableChannels" /> 个</strong></div>
                </div>
              </header>

              <p v-if="profileError" class="dash-section flex items-center gap-2 rounded-2xl border border-rose-100 bg-rose-50 px-4 py-3 text-xs font-black text-rose-700" style="--d: 40ms">
                <svg viewBox="0 0 24 24" class="h-4 w-4 shrink-0" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><path d="M12 8v5m0 3.5v.5M12 2 2 20h20L12 2z" /></svg>
                {{ profileError }}
              </p>

              <article class="dash-section rounded-[22px] border border-slate-200/80 bg-white p-5 shadow-sm sm:p-6" style="--d: 90ms">
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div class="flex items-start gap-3">
                    <span class="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-emerald-50 text-emerald-700 ring-1 ring-emerald-100"><svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.9" aria-hidden="true"><path d="M4 6h16v12H4V6zm0 2 8 6 8-6" /></svg></span>
                    <div><h3 class="text-base font-black text-slate-950">联系邮箱</h3><p class="mt-0.5 text-xs font-semibold text-slate-500">用于账号通知、验证和密码找回。</p></div>
                  </div>
                  <span class="rounded-full px-2.5 py-1 text-[10px] font-black" :class="auth.userInfo?.email ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'">{{ auth.userInfo?.email ? '已绑定' : '未绑定' }}</span>
                </div>
                <div class="mt-5 grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
                  <label class="relative block">
                    <span class="sr-only">邮箱地址</span>
                    <svg viewBox="0 0 24 24" class="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" stroke="currentColor" stroke-width="1.9" aria-hidden="true"><path d="M4 6h16v12H4V6zm0 2 8 6 8-6" /></svg>
                    <input v-model="profileEmail" class="h-12 w-full rounded-xl border border-slate-200 bg-slate-50 pl-10 pr-3.5 text-sm font-semibold text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-emerald-400 focus:bg-white focus:ring-2 focus:ring-emerald-100" type="email" placeholder="name@example.com" @keyup.enter="saveRelayProfile" />
                  </label>
                  <button type="button" class="h-12 rounded-xl bg-emerald-600 px-6 text-xs font-black text-white shadow-sm shadow-emerald-100 transition hover:-translate-y-0.5 hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60" :disabled="Boolean(profileSaving)" @click="saveRelayProfile">保存邮箱</button>
                </div>
              </article>

              <article class="dash-section rounded-[22px] border border-slate-200/80 bg-white p-5 shadow-sm sm:p-6" style="--d: 160ms">
                <div class="flex items-start gap-3">
                  <span class="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-amber-50 text-amber-700 ring-1 ring-amber-100"><svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.9" aria-hidden="true"><path d="M7 10V8a5 5 0 0 1 10 0v2M5 10h14v11H5V10zm7 4v3" /></svg></span>
                  <div><h3 class="text-base font-black text-slate-950">登录安全</h3><p class="mt-0.5 text-xs font-semibold text-slate-500">更新密码后，后续登录立即使用新密码。</p></div>
                </div>

                <div class="mt-5 grid gap-4 md:grid-cols-3">
                  <label class="block">
                    <span class="text-[11px] font-black text-slate-600">当前密码</span>
                    <div class="relative mt-2">
                      <input v-model="oldPassword" :type="pwdVisible.current ? 'text' : 'password'" class="h-12 w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 pr-11 text-sm font-semibold outline-none transition focus:border-amber-400 focus:bg-white focus:ring-2 focus:ring-amber-100" autocomplete="current-password" placeholder="输入当前密码" />
                      <button type="button" class="absolute right-2 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700" :aria-label="pwdVisible.current ? '隐藏密码' : '显示密码'" @click="pwdVisible.current = !pwdVisible.current">
                        <svg v-if="pwdVisible.current" viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.5-7 10-7c2.1 0 3.9.7 5.4 1.7M22 12s-3.5 7-10 7c-2.1 0-3.9-.7-5.4-1.7M3 3l18 18" /><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" /></svg>
                        <svg v-else viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" /><circle cx="12" cy="12" r="3" /></svg>
                      </button>
                    </div>
                  </label>

                  <label class="block">
                    <span class="text-[11px] font-black text-slate-600">新密码</span>
                    <div class="relative mt-2">
                      <input v-model="newPassword" :type="pwdVisible.next ? 'text' : 'password'" class="h-12 w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 pr-11 text-sm font-semibold outline-none transition focus:border-amber-400 focus:bg-white focus:ring-2 focus:ring-amber-100" autocomplete="new-password" placeholder="至少 6 位" />
                      <button type="button" class="absolute right-2 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700" :aria-label="pwdVisible.next ? '隐藏密码' : '显示密码'" @click="pwdVisible.next = !pwdVisible.next">
                        <svg v-if="pwdVisible.next" viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.5-7 10-7c2.1 0 3.9.7 5.4 1.7M22 12s-3.5 7-10 7c-2.1 0-3.9-.7-5.4-1.7M3 3l18 18" /><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" /></svg>
                        <svg v-else viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" /><circle cx="12" cy="12" r="3" /></svg>
                      </button>
                    </div>
                    <div v-if="newPassword" class="mt-2 flex items-center gap-2">
                      <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-100">
                        <div class="h-full rounded-full transition-all duration-500" :style="{ width: `${passwordStrength.score}%`, backgroundColor: passwordStrength.color }"></div>
                      </div>
                      <span class="shrink-0 text-[10px] font-black" :style="{ color: passwordStrength.color }">{{ passwordStrength.label }}</span>
                    </div>
                  </label>

                  <label class="block">
                    <span class="text-[11px] font-black text-slate-600">确认新密码</span>
                    <div class="relative mt-2">
                      <input v-model="confirmPassword" :type="pwdVisible.confirm ? 'text' : 'password'" class="h-12 w-full rounded-xl border bg-slate-50 px-3.5 pr-11 text-sm font-semibold outline-none transition focus:bg-white focus:ring-2" :class="confirmMismatch ? 'border-rose-300 focus:border-rose-400 focus:ring-rose-100' : 'border-slate-200 focus:border-amber-400 focus:ring-amber-100'" autocomplete="new-password" placeholder="再次输入" @keyup.enter="changeRelayPassword" />
                      <button type="button" class="absolute right-2 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700" :aria-label="pwdVisible.confirm ? '隐藏密码' : '显示密码'" @click="pwdVisible.confirm = !pwdVisible.confirm">
                        <svg v-if="pwdVisible.confirm" viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.5-7 10-7c2.1 0 3.9.7 5.4 1.7M22 12s-3.5 7-10 7c-2.1 0-3.9-.7-5.4-1.7M3 3l18 18" /><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" /></svg>
                        <svg v-else viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" /><circle cx="12" cy="12" r="3" /></svg>
                      </button>
                    </div>
                    <p v-if="confirmMismatch" class="mt-2 text-[10px] font-black text-rose-600">两次输入的密码不一致</p>
                  </label>
                </div>

                <div class="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
                  <p class="text-[10px] font-semibold text-slate-400">建议使用包含大小写字母、数字和符号的独立密码。</p>
                  <button type="button" class="h-11 rounded-xl bg-slate-950 px-6 text-xs font-black text-white transition hover:-translate-y-0.5 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60" :disabled="Boolean(profileSaving)" @click="changeRelayPassword">更新密码</button>
                </div>
              </article>
            </div>
          </section>
          </div>
        </Transition>
      </main>
    </div>

    <div v-if="showKeyDialog" class="fixed inset-0 z-50 flex min-h-dvh items-center justify-center overflow-y-auto bg-slate-950/45 p-2 backdrop-blur-[5px] sm:p-4" @click.self="showKeyDialog = false">
      <div class="flex max-h-[calc(100dvh-1rem)] w-full max-w-3xl flex-col overflow-hidden rounded-[26px] border border-white/80 bg-white shadow-[0_32px_110px_rgba(15,23,42,0.30)] sm:max-h-[calc(100dvh-2rem)]">
        <div class="flex shrink-0 items-start justify-between border-b border-slate-100 px-5 py-4 sm:px-6">
          <div>
            <h2 class="text-xl font-black tracking-tight text-slate-950">创建 API 密钥</h2>
            <p class="mt-1 text-xs font-semibold text-slate-500">配置访问范围和安全限制，所有已启用的限制都会在 API 请求时由后端校验。</p>
          </div>
          <button type="button" aria-label="关闭创建密钥弹窗" class="mr-5 rounded-lg px-3 py-2 text-2xl leading-none text-slate-400 hover:bg-slate-50" @click="showKeyDialog = false">×</button>
        </div>
        <div class="min-h-0 flex-1 space-y-5 overflow-y-auto overscroll-contain px-4 py-5 sm:px-6">
          <label class="block">
            <span class="text-sm font-black text-slate-700">名称</span>
            <input v-model="keyForm.name" class="mt-2 h-12 w-full rounded-xl border border-slate-200 px-4 text-sm font-semibold outline-none focus:border-teal-300" placeholder="我的 API 密钥" />
          </label>

          <div>
            <span class="text-sm font-black text-slate-700">分组</span>
            <div class="mt-2 max-h-44 space-y-2 overflow-y-auto rounded-xl border border-slate-200 bg-slate-50 p-2" role="radiogroup" aria-label="选择密钥分组">
              <button
                v-for="group in groups"
                :key="group.code"
                type="button"
                role="radio"
                :aria-checked="keyForm.group === group.code"
                class="grid w-full grid-cols-[20px_minmax(0,1fr)_auto] items-center gap-3 rounded-lg border px-3 py-3 text-left transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-2"
                :class="keyForm.group === group.code ? 'border-emerald-500 bg-emerald-50 shadow-sm ring-2 ring-emerald-100' : 'border-transparent bg-white hover:border-slate-300 hover:bg-slate-50'"
                @click="keyForm.group = group.code"
              >
                <span class="grid h-5 w-5 place-items-center rounded-full border-2 transition" :class="keyForm.group === group.code ? 'border-emerald-600 bg-emerald-600' : 'border-slate-300 bg-white'" aria-hidden="true">
                  <span v-if="keyForm.group === group.code" class="h-2 w-2 rounded-full bg-white"></span>
                </span>
                <span class="min-w-0">
                  <span class="block truncate text-sm font-black" :class="keyForm.group === group.code ? 'text-emerald-800' : 'text-slate-800'">{{ group.name }}</span>
                  <span class="mt-0.5 block truncate text-xs font-semibold text-slate-500">{{ group.code }}</span>
                </span>
                <span class="whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-black" :class="keyForm.group === group.code ? 'bg-emerald-600 text-white' : 'bg-slate-100 text-slate-600'">{{ Number(group.ratio || 1).toFixed(3) }}x 倍率</span>
              </button>
            </div>
          </div>

          <p v-if="keyFormError" class="flex items-start gap-2 rounded-xl border border-rose-100 bg-rose-50 px-3 py-2.5 text-xs font-bold leading-5 text-rose-700"><svg viewBox="0 0 24 24" class="mt-0.5 h-4 w-4 shrink-0" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 3 2.7 20h18.6L12 3zm0 6v5m0 3h.01" /></svg>{{ keyFormError }}</p>

          <div>
            <div class="flex items-center justify-between gap-3"><div><h3 class="text-sm font-black text-slate-800">访问限制</h3><p class="mt-1 text-xs font-semibold text-slate-500">点击选项即可展开配置；未开启的项目不会限制密钥。</p></div><span class="rounded-full bg-emerald-50 px-2.5 py-1 text-[10px] font-black text-emerald-700">后端实时校验</span></div>

            <div class="mt-3 grid items-start gap-3 md:grid-cols-2">
              <article class="overflow-hidden rounded-2xl border transition" :class="keyForm.ipLimitEnabled ? 'border-emerald-300 bg-emerald-50/40 shadow-sm' : 'border-slate-200 bg-white'">
                <button type="button" class="flex w-full items-center justify-between gap-3 p-4 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-emerald-500" role="switch" :aria-checked="keyForm.ipLimitEnabled" :aria-expanded="keyForm.ipLimitEnabled" @click="keyForm.ipLimitEnabled = !keyForm.ipLimitEnabled; keyFormError = ''">
                  <span class="flex items-center gap-3"><span class="grid h-9 w-9 place-items-center rounded-xl" :class="keyForm.ipLimitEnabled ? 'bg-emerald-600 text-white' : 'bg-slate-100 text-slate-500'"><svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 3 4 6v5c0 5 3.4 8.4 8 10 4.6-1.6 8-5 8-10V6l-8-3zm-3 9 2 2 4-4" /></svg></span><span><strong class="block text-sm font-black text-slate-800">IP 白名单</strong><small class="mt-0.5 block text-[11px] font-semibold text-slate-500">仅允许指定公网 IP 调用</small></span></span>
                  <span class="relative h-6 w-11 shrink-0 rounded-full transition" :class="keyForm.ipLimitEnabled ? 'bg-emerald-600' : 'bg-slate-200'"><span class="absolute top-1 h-4 w-4 rounded-full bg-white shadow-sm transition-transform" :class="keyForm.ipLimitEnabled ? 'translate-x-6' : 'translate-x-1'"></span></span>
                </button>
                <Transition name="key-option-expand">
                  <div v-if="keyForm.ipLimitEnabled" class="key-option-body border-t border-emerald-100 px-4 pb-4 pt-3">
                    <label class="text-xs font-black text-slate-600">允许访问的 IP</label>
                    <textarea v-model="keyForm.ipWhitelist" class="mt-2 min-h-24 w-full resize-y rounded-xl border border-slate-200 bg-white px-3 py-2.5 font-mono text-xs font-semibold text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-emerald-400 focus:ring-2 focus:ring-emerald-100" placeholder="例如：&#10;203.0.113.10&#10;198.51.100.8"></textarea>
                    <p class="mt-2 text-[11px] font-semibold leading-5 text-slate-500">支持逗号、空格或换行分隔；当前为精确 IP 匹配，不支持 CIDR 网段。</p>
                  </div>
                </Transition>
              </article>

              <article class="overflow-hidden rounded-2xl border transition" :class="keyForm.quotaEnabled ? 'border-cyan-300 bg-cyan-50/40 shadow-sm' : 'border-slate-200 bg-white'">
                <button type="button" class="flex w-full items-center justify-between gap-3 p-4 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-cyan-500" role="switch" :aria-checked="keyForm.quotaEnabled" :aria-expanded="keyForm.quotaEnabled" @click="keyForm.quotaEnabled = !keyForm.quotaEnabled; keyFormError = ''">
                  <span class="flex items-center gap-3"><span class="grid h-9 w-9 place-items-center rounded-xl" :class="keyForm.quotaEnabled ? 'bg-cyan-600 text-white' : 'bg-slate-100 text-slate-500'"><svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 6v12m3-9.5c-.8-.5-1.8-.8-3-.8-1.7 0-3 1-3 2.3s1.3 2 3 2 3 1 3 2.3-1.3 2.3-3 2.3c-1.2 0-2.3-.3-3.2-.9" /></svg></span><span><strong class="block text-sm font-black text-slate-800">消费额度</strong><small class="mt-0.5 block text-[11px] font-semibold text-slate-500">限制密钥最多可消费金额</small></span></span>
                  <span class="relative h-6 w-11 shrink-0 rounded-full transition" :class="keyForm.quotaEnabled ? 'bg-cyan-600' : 'bg-slate-200'"><span class="absolute top-1 h-4 w-4 rounded-full bg-white shadow-sm transition-transform" :class="keyForm.quotaEnabled ? 'translate-x-6' : 'translate-x-1'"></span></span>
                </button>
                <Transition name="key-option-expand">
                  <div v-if="keyForm.quotaEnabled" class="key-option-body border-t border-cyan-100 px-4 pb-4 pt-3">
                    <label class="text-xs font-black text-slate-600">最大消费额度（USD）</label>
                    <div class="relative mt-2"><span class="absolute left-3 top-1/2 -translate-y-1/2 text-sm font-black text-cyan-700">$</span><input v-model.number="keyForm.quota" class="h-11 w-full rounded-xl border border-slate-200 bg-white pl-7 pr-3 text-sm font-black text-slate-800 outline-none focus:border-cyan-400 focus:ring-2 focus:ring-cyan-100" type="number" min="0.000001" step="0.000001" placeholder="10.000000" /></div>
                    <div class="mt-2 flex flex-wrap gap-1.5"><button v-for="amount in [1, 5, 10, 50]" :key="amount" type="button" class="rounded-lg border border-cyan-100 bg-white px-2.5 py-1 text-[10px] font-black text-cyan-700 hover:bg-cyan-50" @click="keyForm.quota = amount">${{ amount }}</button></div>
                    <p class="mt-2 text-[11px] font-semibold leading-5 text-slate-500">成功请求累计费用达到该额度后，后续调用返回 402。</p>
                  </div>
                </Transition>
              </article>

              <article class="overflow-hidden rounded-2xl border transition" :class="keyForm.speedLimitEnabled ? 'border-amber-300 bg-amber-50/40 shadow-sm' : 'border-slate-200 bg-white'">
                <button type="button" class="flex w-full items-center justify-between gap-3 p-4 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-amber-500" role="switch" :aria-checked="keyForm.speedLimitEnabled" :aria-expanded="keyForm.speedLimitEnabled" @click="keyForm.speedLimitEnabled = !keyForm.speedLimitEnabled; keyFormError = ''">
                  <span class="flex items-center gap-3"><span class="grid h-9 w-9 place-items-center rounded-xl" :class="keyForm.speedLimitEnabled ? 'bg-amber-500 text-white' : 'bg-slate-100 text-slate-500'"><svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M13 2 4 14h7l-1 8 9-12h-7l1-8z" /></svg></span><span><strong class="block text-sm font-black text-slate-800">速率限制</strong><small class="mt-0.5 block text-[11px] font-semibold text-slate-500">控制每分钟请求和 Token</small></span></span>
                  <span class="relative h-6 w-11 shrink-0 rounded-full transition" :class="keyForm.speedLimitEnabled ? 'bg-amber-500' : 'bg-slate-200'"><span class="absolute top-1 h-4 w-4 rounded-full bg-white shadow-sm transition-transform" :class="keyForm.speedLimitEnabled ? 'translate-x-6' : 'translate-x-1'"></span></span>
                </button>
                <Transition name="key-option-expand">
                  <div v-if="keyForm.speedLimitEnabled" class="key-option-body border-t border-amber-100 px-4 pb-4 pt-3">
                    <div class="grid grid-cols-2 gap-2">
                      <label><span class="text-xs font-black text-slate-600">RPM</span><span class="mt-1 block text-[10px] font-semibold text-slate-400">每分钟请求数</span><input v-model.number="keyForm.rpmLimit" class="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-black text-slate-800 outline-none focus:border-amber-400 focus:ring-2 focus:ring-amber-100" type="number" min="0" step="1" placeholder="60" /></label>
                      <label><span class="text-xs font-black text-slate-600">TPM</span><span class="mt-1 block text-[10px] font-semibold text-slate-400">每分钟 Token 数</span><input v-model.number="keyForm.tpmLimit" class="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-black text-slate-800 outline-none focus:border-amber-400 focus:ring-2 focus:ring-amber-100" type="number" min="0" step="1" placeholder="100000" /></label>
                    </div>
                    <div class="mt-2 flex flex-wrap gap-1.5"><button type="button" class="rounded-lg border border-amber-100 bg-white px-2.5 py-1 text-[10px] font-black text-amber-700 hover:bg-amber-50" @click="keyForm.rpmLimit = 60; keyForm.tpmLimit = 100000">常规 60 / 100K</button><button type="button" class="rounded-lg border border-amber-100 bg-white px-2.5 py-1 text-[10px] font-black text-amber-700 hover:bg-amber-50" @click="keyForm.rpmLimit = 120; keyForm.tpmLimit = 500000">高频 120 / 500K</button></div>
                    <p class="mt-2 text-[11px] font-semibold leading-5 text-slate-500">某项填写 0 表示该项不限制；超过限制时返回 429。</p>
                  </div>
                </Transition>
              </article>

              <article class="overflow-hidden rounded-2xl border transition" :class="keyForm.expiresEnabled ? 'border-violet-300 bg-violet-50/40 shadow-sm' : 'border-slate-200 bg-white'">
                <button type="button" class="flex w-full items-center justify-between gap-3 p-4 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-violet-500" role="switch" :aria-checked="keyForm.expiresEnabled" :aria-expanded="keyForm.expiresEnabled" @click="toggleExpiryLimit">
                  <span class="flex items-center gap-3"><span class="grid h-9 w-9 place-items-center rounded-xl" :class="keyForm.expiresEnabled ? 'bg-violet-600 text-white' : 'bg-slate-100 text-slate-500'"><svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></svg></span><span><strong class="block text-sm font-black text-slate-800">密钥有效期</strong><small class="mt-0.5 block text-[11px] font-semibold text-slate-500">到期后自动拒绝访问</small></span></span>
                  <span class="relative h-6 w-11 shrink-0 rounded-full transition" :class="keyForm.expiresEnabled ? 'bg-violet-600' : 'bg-slate-200'"><span class="absolute top-1 h-4 w-4 rounded-full bg-white shadow-sm transition-transform" :class="keyForm.expiresEnabled ? 'translate-x-6' : 'translate-x-1'"></span></span>
                </button>
                <Transition name="key-option-expand">
                  <div v-if="keyForm.expiresEnabled" class="key-option-body border-t border-violet-100 px-4 pb-4 pt-3">
                    <label class="text-xs font-black text-slate-600">失效时间</label>
                    <input v-model="keyForm.expiresAt" class="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-black text-slate-800 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" type="datetime-local" :min="minimumExpiryValue()" />
                    <div class="mt-2 flex flex-wrap gap-1.5"><button v-for="days in [7, 30, 90]" :key="days" type="button" class="rounded-lg border border-violet-100 bg-white px-2.5 py-1 text-[10px] font-black text-violet-700 hover:bg-violet-50" @click="applyExpiryPreset(days)">{{ days }} 天</button></div>
                    <p class="mt-2 text-[11px] font-semibold leading-5 text-slate-500">到期后所有请求返回 401；默认预设为 30 天。</p>
                  </div>
                </Transition>
              </article>
            </div>
          </div>
        </div>
        <div class="flex shrink-0 justify-end gap-3 border-t border-slate-200 bg-slate-50 px-4 py-3 sm:px-6 sm:py-4">
          <button class="h-11 rounded-xl border border-slate-200 bg-white px-5 text-sm font-black text-slate-700 transition hover:border-slate-300 hover:bg-slate-100 sm:h-12 sm:px-6" @click="showKeyDialog = false">取消</button>
          <button class="h-11 rounded-xl bg-teal-600 px-5 text-sm font-black text-white shadow-sm shadow-teal-100 transition hover:bg-teal-700 disabled:cursor-not-allowed disabled:opacity-60 sm:h-12 sm:px-6" :disabled="creatingKey" @click="createKey">{{ creatingKey ? '创建中' : '确定创建' }}</button>
        </div>
      </div>
    </div>

    <div v-if="showErrorLogsDialog" class="fixed inset-0 z-[54] grid place-items-center bg-slate-950/45 px-4 backdrop-blur-sm" @click.self="showErrorLogsDialog = false">
      <section class="flex max-h-[86vh] w-full max-w-6xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl">
        <div class="flex flex-wrap items-start justify-between gap-4 border-b border-slate-100 px-6 py-5">
          <div>
            <h2 class="text-xl font-black text-slate-950">错误请求日志</h2>
            <p class="mt-1 text-sm font-semibold text-slate-500">展示最近 {{ errorLogs.length }} 条失败请求记录。</p>
          </div>
          <div class="flex items-center gap-2">
            <button class="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-black text-slate-700 transition hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-700" @click="load">刷新</button>
            <button class="grid h-10 w-10 place-items-center rounded-xl text-xl font-black text-slate-400 transition hover:bg-slate-50 hover:text-slate-700" @click="showErrorLogsDialog = false">×</button>
          </div>
        </div>

        <div class="overflow-auto p-6">
          <div v-if="errorLogs.length" class="space-y-4">
            <article v-for="log in errorLogs" :key="log.id" class="rounded-xl border border-rose-100 bg-rose-50/40 p-4">
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div class="min-w-0">
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="rounded-lg bg-slate-950 px-2.5 py-1 text-xs font-black text-white">{{ log.source === 'image' ? '生图' : '中转' }}</span>
                    <span class="rounded-lg bg-rose-100 px-2.5 py-1 text-xs font-black text-rose-700">HTTP {{ log.statusCode || 0 }}</span>
                    <span class="rounded-lg bg-white px-2.5 py-1 text-xs font-black text-slate-600">{{ log.status || 'failed' }}</span>
                    <span v-if="log.errorType" class="rounded-lg bg-white px-2.5 py-1 text-xs font-black text-rose-600">{{ log.errorType }}</span>
                    <span class="rounded-lg bg-white px-2.5 py-1 text-xs font-black text-slate-600">{{ log.modelType || '-' }}</span>
                  </div>
                  <p class="mt-3 break-all text-sm font-black text-slate-950">{{ log.model || '-' }}</p>
                  <p class="mt-1 break-all text-xs font-semibold text-slate-500">{{ log.requestUrl || log.endpoint || '-' }}</p>
                </div>
                <div class="text-right text-xs font-semibold text-slate-500">
                  <p class="font-black text-slate-700">{{ log.createdAt?.replace('T', ' ').slice(0, 19) }}</p>
                  <p class="mt-1">{{ ((log.durationMs || 0) / 1000).toFixed(2) }}s</p>
                </div>
              </div>

              <div class="mt-4 grid gap-3 text-sm font-semibold text-slate-600 md:grid-cols-2">
                <p><span class="text-slate-400">API 密钥：</span>{{ log.tokenName || '-' }}</p>
                <p><span class="text-slate-400">渠道：</span>{{ log.channelName || '-' }}</p>
                <p><span class="text-slate-400">分组：</span>{{ log.groupNames || '-' }}</p>
              </div>

              <p v-if="log.prompt" class="mt-3 line-clamp-2 break-all text-xs font-semibold text-slate-500">提示词：{{ log.prompt }}</p>
              <pre class="mt-4 max-h-48 overflow-auto whitespace-pre-wrap break-words rounded-xl bg-white p-4 text-xs font-semibold leading-6 text-rose-700">{{ log.message || '无错误详情' }}</pre>
              <p class="mt-3 truncate text-xs font-semibold text-slate-400">{{ log.userAgent || '-' }}</p>
            </article>
          </div>
          <div v-else class="rounded-xl border border-dashed border-slate-200 p-10 text-center text-sm font-black text-slate-500">暂无错误请求日志</div>
        </div>
      </section>
    </div>

    <AppConfirmDialog
      :open="Boolean(keyActionDialog)"
      :title="keyActionDialog?.action === 'delete' ? '删除 API 密钥？' : keyActionDialog?.token.enabled ? '禁用 API 密钥？' : '启用 API 密钥？'"
      :description="keyActionDialog?.action === 'delete'
        ? '删除后该密钥将立即失效，并且无法恢复。使用它的客户端会停止访问 API。'
        : keyActionDialog?.token.enabled
          ? '禁用后该密钥会立即停止接受请求，你可以稍后重新启用。'
          : '启用后该密钥将恢复接受 API 请求，并继续按照当前分组计费。'"
      :confirm-label="keyActionDialog?.action === 'delete' ? '确认删除' : keyActionDialog?.token.enabled ? '确认禁用' : '确认启用'"
      :subject="keyActionDialog ? `${keyActionDialog.token.name} · ${keyActionDialog.token.tokenPreview}` : ''"
      :tone="keyActionDialog?.action === 'delete' ? 'danger' : keyActionDialog?.token.enabled ? 'warning' : 'success'"
      :loading="keyActionLoading"
      @cancel="closeKeyAction"
      @confirm="confirmKeyAction"
    />

    <AppConfirmDialog
      :open="Boolean(ccSwitchImportDialog)"
      title="导入到 CCSwitch？"
      :description="ccSwitchImportDialog ? `将为「${ccSwitchImportDialog.name}」创建 ${ccSwitchTargetForToken(ccSwitchImportDialog).label} 配置。点击后会打开 CCSwitch，应用内仍需完成一次导入确认。` : ''"
      confirm-label="打开 CCSwitch"
      cancel-label="暂不导入"
      :subject="ccSwitchImportDialog ? `${ccSwitchImportDialog.tokenPreview} → ${ccSwitchTargetForToken(ccSwitchImportDialog).label}` : ''"
      tone="success"
      :loading="ccSwitchImportLoading"
      @cancel="closeCcSwitchImport"
      @confirm="confirmCcSwitchImport"
    />

    <Teleport to="body">
      <div
        v-if="activePricingTooltip"
        class="pointer-events-none fixed z-[90] max-h-[calc(100dvh-24px)] w-72 overflow-y-auto rounded-xl border bg-white text-left opacity-100 shadow-2xl shadow-slate-300/70 ring-1 ring-slate-100"
        :class="modelHasConfiguredPricing(activePricingTooltip.detail) ? 'border-emerald-100' : 'border-amber-200'"
        :style="{ left: `${activePricingTooltip.x}px`, top: `${activePricingTooltip.y}px` }"
      >
        <div class="rounded-t-xl px-4 py-3" :class="modelHasConfiguredPricing(activePricingTooltip.detail) ? 'bg-emerald-50' : 'bg-amber-50'">
          <div class="flex items-center justify-between gap-3">
            <p class="truncate text-sm font-black" :class="modelHasConfiguredPricing(activePricingTooltip.detail) ? 'text-emerald-700' : 'text-amber-800'">{{ publicModelName(activePricingTooltip.model) }}</p>
            <span class="rounded-md px-2 py-1 text-[10px] font-black ring-1" :class="ruleBadgeClass(activePricingTooltip.rule)">{{ ruleLabel(activePricingTooltip.rule) }}</span>
          </div>
        </div>
        <div class="px-4 py-3 text-xs font-semibold text-slate-500">
          <div v-if="modelHasConfiguredPricing(activePricingTooltip.detail)" class="grid gap-2">
            <p class="flex justify-between gap-3">
              <span>计费模式</span>
              <span class="font-black text-slate-700">{{ fixedBilling(activePricingTooltip.detail) ? '按次' : '按量' }}</span>
            </p>
            <p v-if="fixedBilling(activePricingTooltip.detail) || Number(activePricingTooltip.detail.requestPrice || 0) > 0" class="flex justify-between gap-3">
              <span>每次请求</span>
              <span class="font-mono font-black text-slate-700">{{ priceValue(activePricingTooltip.detail.requestPrice) }} / 次</span>
            </p>
            <template v-else>
              <p class="flex justify-between gap-3"><span>输入 / 1M</span><span class="font-mono font-black text-slate-700">{{ priceValue(activePricingTooltip.detail.inputPrice) }}</span></p>
              <p class="flex justify-between gap-3"><span>输出 / 1M</span><span class="font-mono font-black text-slate-700">{{ priceValue(activePricingTooltip.detail.outputPrice) }}</span></p>
              <p class="flex justify-between gap-3"><span>缓存读 / 1M</span><span class="font-mono font-black text-slate-700">{{ priceValue(activePricingTooltip.detail.cachedInputPrice) }}</span></p>
              <p class="flex justify-between gap-3"><span>缓存写 / 1M</span><span class="font-mono font-black text-slate-700">{{ priceValue(activePricingTooltip.detail.cacheCreationPrice) }}</span></p>
            </template>
          </div>
          <div v-else class="rounded-xl border border-amber-100 bg-amber-50/70 p-3 text-amber-800">
            <div class="flex items-center gap-2 font-black"><svg viewBox="0 0 24 24" class="h-4 w-4 shrink-0" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 3 2.5 20h19L12 3zm0 6v5m0 3h.01" /></svg>价格尚未配置</div>
            <p class="mt-2 text-[11px] font-semibold leading-5 text-amber-700">后台还没有为该模型设置输入、输出或按次价格，因此暂时无法计算费用。</p>
          </div>
        </div>
      </div>

      <Transition name="zoom-fade">
        <div v-if="showIntegrationGuide" class="fixed inset-0 z-[100] grid place-items-center overflow-y-auto bg-slate-950/45 p-3 backdrop-blur-[5px] sm:p-5" @click.self="showIntegrationGuide = false">
          <section class="max-h-[calc(100dvh-24px)] w-full max-w-5xl overflow-y-auto rounded-[26px] border border-white/80 bg-white shadow-[0_32px_110px_rgba(15,23,42,0.30)] sm:max-h-[calc(100dvh-40px)]">
            <div class="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-slate-100 bg-white/95 px-5 py-4 backdrop-blur-xl sm:px-6">
              <div>
                <p class="text-[11px] font-black uppercase tracking-[0.18em] text-emerald-600">API Quick Start</p>
                <h2 class="mt-1 text-xl font-black tracking-tight text-slate-950 sm:text-2xl">三步接入 API 中转站</h2>
                <p class="mt-1 text-xs font-semibold text-slate-500">配置地址、复制密钥，然后在兼容 OpenAI 的客户端中选择模型。</p>
              </div>
              <button type="button" class="grid h-10 w-10 shrink-0 place-items-center rounded-xl text-xl text-slate-400 transition hover:bg-slate-100 hover:text-slate-700" aria-label="关闭接入指南" @click="showIntegrationGuide = false">×</button>
            </div>

            <div class="p-5 sm:p-6">
              <div class="grid gap-3 lg:grid-cols-3">
                <article class="rounded-2xl border border-emerald-100 bg-emerald-50/60 p-4"><span class="grid h-9 w-9 place-items-center rounded-xl bg-emerald-600 text-sm font-black text-white shadow-lg shadow-emerald-100">1</span><h3 class="mt-4 text-sm font-black text-slate-950">创建并复制密钥</h3><p class="mt-2 text-xs font-semibold leading-5 text-slate-500">返回密钥列表，点击绿色的“复制密钥”按钮获取完整 API Key。</p></article>
                <article class="rounded-2xl border border-blue-100 bg-blue-50/60 p-4"><span class="grid h-9 w-9 place-items-center rounded-xl bg-blue-600 text-sm font-black text-white shadow-lg shadow-blue-100">2</span><h3 class="mt-4 text-sm font-black text-slate-950">填写 API 地址</h3><button type="button" class="mt-2 flex w-full items-center justify-between gap-3 rounded-xl border border-blue-100 bg-white px-3 py-2 text-left font-mono text-[11px] font-black text-blue-700 transition hover:border-blue-300" @click="copyText(apiBase, 'guide-api-base')"><span class="min-w-0 truncate">{{ apiBase }}</span><span class="shrink-0 font-sans">{{ copied === 'guide-api-base' ? '已复制' : '复制' }}</span></button></article>
                <article class="rounded-2xl border border-amber-100 bg-amber-50/60 p-4"><span class="grid h-9 w-9 place-items-center rounded-xl bg-amber-500 text-sm font-black text-white shadow-lg shadow-amber-100">3</span><h3 class="mt-4 text-sm font-black text-slate-950">选择模型并调用</h3><p class="mt-2 text-xs font-semibold leading-5 text-slate-500">在客户端选择可用模型，调用费用会按密钥所属分组倍率从余额扣除。</p></article>
              </div>

              <div class="mt-5 rounded-2xl border border-slate-200 bg-slate-50/80 p-4 sm:p-5">
                <div class="flex flex-wrap items-center justify-between gap-3"><div><h3 class="text-sm font-black text-slate-950">客户端下载</h3><p class="mt-1 text-xs font-semibold text-slate-500">快速切换不同 API 服务或安装桌面版 Codex。</p></div><button type="button" class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-600 transition hover:border-emerald-300 hover:text-emerald-700" @click="copyText(siteOrigin, 'guide-site')">{{ copied === 'guide-site' ? '站点地址已复制' : '复制站点地址' }}</button></div>
                <div class="mt-4 grid gap-3 md:grid-cols-2">
                  <article class="flex items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white p-4"><div class="min-w-0"><p class="text-sm font-black text-slate-900">CC Switch</p><p class="mt-1 truncate font-mono text-[10px] font-semibold text-slate-400">{{ ccSwitchDownloadUrl }}</p></div><a class="shrink-0 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-black text-white transition hover:bg-emerald-700" :href="ccSwitchDownloadUrl" download>下载</a></article>
                  <article class="flex items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white p-4"><div class="min-w-0"><p class="text-sm font-black text-slate-900">Codex Installer</p><p class="mt-1 truncate font-mono text-[10px] font-semibold text-slate-400">{{ codexDownloadUrl }}</p></div><a class="shrink-0 rounded-xl bg-slate-950 px-4 py-2 text-xs font-black text-white transition hover:bg-emerald-700" :href="codexDownloadUrl" download>下载</a></article>
                </div>
              </div>
            </div>
          </section>
        </div>
      </Transition>

      <Transition name="zoom-fade">
        <div
          v-if="showAnnouncementsDialog"
          class="fixed inset-0 z-[80] grid min-h-dvh place-items-center overflow-y-auto bg-slate-950/45 p-3 backdrop-blur-[6px] sm:p-5"
          @click.self="showAnnouncementsDialog = false"
        >
          <section
            class="flex max-h-[calc(100dvh-24px)] w-full max-w-2xl flex-col overflow-hidden rounded-[24px] border border-white/80 bg-white/95 shadow-[0_32px_100px_rgba(15,23,42,0.28)] sm:max-h-[calc(100dvh-40px)] sm:rounded-[28px]"
            role="dialog"
            aria-modal="true"
            aria-labelledby="announcement-center-title"
          >
            <header class="flex shrink-0 items-center justify-between gap-4 border-b border-slate-100 bg-white/90 px-4 py-4 backdrop-blur-xl sm:px-6 sm:py-5">
              <div class="flex min-w-0 items-center gap-3">
                <span class="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-emerald-50 text-emerald-700 ring-1 ring-emerald-100">
                  <svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
                  </svg>
                </span>
                <div class="min-w-0">
                  <div class="flex items-center gap-2">
                    <h2 id="announcement-center-title" class="truncate text-lg font-black text-slate-950 sm:text-xl">公告中心</h2>
                    <span class="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-black text-slate-500">{{ announcements.length }} 条</span>
                  </div>
                  <p class="mt-0.5 text-xs font-semibold text-slate-500">查看平台发布的全部通知</p>
                </div>
              </div>
              <button
                type="button"
                class="grid h-10 w-10 shrink-0 place-items-center rounded-xl text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500"
                aria-label="关闭公告中心"
                @click="showAnnouncementsDialog = false"
              >
                <svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18" /></svg>
              </button>
            </header>

            <div class="min-h-0 overflow-y-auto p-3 sm:p-5">
              <div v-if="announcementsLoading" class="grid min-h-64 place-items-center rounded-2xl border border-dashed border-slate-200 bg-slate-50/70">
                <RequestLoader label="正在读取公告" :cell-size="11" />
              </div>
              <div v-else-if="announcements.length" class="grid gap-3">
                <button
                  v-for="(item, index) in announcements"
                  :key="item.id"
                  type="button"
                  class="relay-list-item group w-full rounded-2xl border border-slate-200/80 bg-white p-4 text-left shadow-sm transition duration-200 hover:-translate-y-0.5 hover:border-emerald-200 hover:bg-emerald-50/50 hover:shadow-md hover:shadow-emerald-100/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-2 sm:p-5"
                  :style="{ '--i': index }"
                  @click="openAnnouncement(item)"
                >
                  <div class="flex items-start gap-3 sm:gap-4">
                    <span class="mt-0.5 grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-slate-100 text-slate-500 transition group-hover:bg-emerald-100 group-hover:text-emerald-700">
                      <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z" /><path d="M8 9h8M8 13h8M8 17h5" /></svg>
                    </span>
                    <div class="min-w-0 flex-1">
                      <div class="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
                        <h3 class="break-words text-sm font-black leading-6 text-slate-900 transition group-hover:text-emerald-800 sm:text-base">{{ item.title }}</h3>
                        <time class="shrink-0 text-[11px] font-semibold text-slate-400">{{ (item.publishedAt || item.createdAt || '').slice(0, 16).replace('T', ' ') || '发布时间待定' }}</time>
                      </div>
                      <p class="mt-2 line-clamp-2 text-xs font-semibold leading-5 text-slate-500 sm:text-sm sm:leading-6">{{ item.content }}</p>
                    </div>
                    <svg viewBox="0 0 24 24" class="mt-2 h-4 w-4 shrink-0 text-slate-300 transition group-hover:translate-x-0.5 group-hover:text-emerald-600" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 18 6-6-6-6" /></svg>
                  </div>
                </button>
              </div>

              <div v-else class="grid min-h-64 place-items-center rounded-2xl border border-dashed border-slate-200 bg-slate-50/70 p-8 text-center">
                <div>
                  <span class="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-white text-slate-400 shadow-sm ring-1 ring-slate-100">
                    <svg viewBox="0 0 24 24" class="h-6 w-6" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></svg>
                  </span>
                  <p class="mt-4 text-sm font-black text-slate-700">暂无公告</p>
                  <p class="mt-1 text-xs font-semibold text-slate-400">新公告发布后会显示在这里</p>
                </div>
              </div>
            </div>
          </section>
        </div>
      </Transition>

      <Transition name="zoom-fade">
        <div
          v-if="selectedAnnouncement"
          class="fixed inset-0 z-[80] grid min-h-dvh place-items-center overflow-y-auto bg-slate-950/45 p-3 backdrop-blur-[6px] sm:p-5"
          @click.self="selectedAnnouncement = null"
        >
          <section
            class="flex max-h-[calc(100dvh-24px)] w-full max-w-2xl flex-col overflow-hidden rounded-[24px] border border-white/80 bg-white shadow-[0_32px_100px_rgba(15,23,42,0.28)] sm:max-h-[calc(100dvh-40px)] sm:rounded-[28px]"
            role="dialog"
            aria-modal="true"
            :aria-labelledby="`announcement-title-${selectedAnnouncement.id}`"
          >
            <header class="flex shrink-0 items-center justify-between gap-3 border-b border-slate-100 bg-white/90 px-4 py-3 backdrop-blur-xl sm:px-6 sm:py-4">
              <button
                type="button"
                class="inline-flex h-10 items-center gap-2 rounded-xl px-2.5 text-xs font-black text-slate-600 transition hover:bg-emerald-50 hover:text-emerald-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 sm:px-3"
                @click="backToAnnouncementList"
              >
                <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 18-6-6 6-6" /></svg>
                返回公告列表
              </button>
              <button
                type="button"
                class="grid h-10 w-10 shrink-0 place-items-center rounded-xl text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500"
                aria-label="关闭公告详情"
                @click="selectedAnnouncement = null"
              >
                <svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18" /></svg>
              </button>
            </header>

            <article class="min-h-0 overflow-y-auto px-5 py-6 sm:px-8 sm:py-8">
              <div class="flex items-center gap-2 text-[11px] font-black uppercase tracking-[0.16em] text-emerald-600">
                <span class="h-1.5 w-1.5 rounded-full bg-emerald-500"></span>
                平台公告
              </div>
              <h2 :id="`announcement-title-${selectedAnnouncement.id}`" class="mt-3 break-words text-xl font-black leading-tight text-slate-950 sm:text-2xl">{{ selectedAnnouncement.title }}</h2>
              <p class="mt-3 flex items-center gap-1.5 text-xs font-semibold text-slate-400">
                <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 3v3M18 3v3M4 9h16M5 5h14a1 1 0 0 1 1 1v14H4V6a1 1 0 0 1 1-1Z" /></svg>
                {{ (selectedAnnouncement.publishedAt || selectedAnnouncement.createdAt || '').replace('T', ' ') || '发布时间待定' }}
              </p>
              <div class="mt-6 border-t border-slate-100 pt-6 whitespace-pre-wrap break-words text-sm font-semibold leading-7 text-slate-700 sm:text-[15px] sm:leading-8">{{ selectedAnnouncement.content }}</div>
            </article>
          </section>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.dash-card,
.dash-section {
  animation: dashSectionIn 500ms cubic-bezier(.16, 1, .3, 1) both;
  animation-delay: var(--d, 0ms);
}

.dash-card-glow {
  position: absolute;
  right: -34px;
  top: -34px;
  width: 130px;
  height: 130px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--glow, #10b981) 0%, transparent 68%);
  opacity: 0;
  filter: blur(8px);
  transition: opacity 350ms ease;
  pointer-events: none;
}

.dash-card:hover .dash-card-glow {
  opacity: .18;
}

.relay-list-item {
  animation: relayListIn 420ms cubic-bezier(.16, 1, .3, 1) both;
  animation-delay: calc(var(--i, 0) * 38ms);
}

.relay-list-refresh-bar {
  position: absolute;
  z-index: 12;
  top: 0;
  right: 0;
  left: 0;
  height: 2px;
  overflow: hidden;
  pointer-events: none;
  background: rgba(16, 185, 129, .12);
}

.relay-list-refresh-bar > span {
  display: block;
  width: 42%;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, transparent, #10b981 35%, #06b6d4 65%, transparent);
  box-shadow: 0 0 12px rgba(16, 185, 129, .55);
  animation: relayListRefresh 1.05s ease-in-out infinite;
}

@keyframes relayListIn {
  from { opacity: 0; transform: translate3d(0, 10px, 0); }
  to { opacity: 1; transform: translate3d(0, 0, 0); }
}

@keyframes relayListRefresh {
  from { transform: translateX(-120%); }
  to { transform: translateX(340%); }
}

@keyframes dashSectionIn {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}

.relay-view-frame {
  width: 100%;
  transform-origin: 50% 16%;
  backface-visibility: hidden;
}

.relay-view-forward-enter-active,
.relay-view-back-enter-active {
  transition:
    opacity 260ms ease,
    transform 330ms cubic-bezier(.16, 1, .3, 1);
  will-change: opacity, transform;
}

.relay-view-forward-leave-active,
.relay-view-back-leave-active {
  transition:
    opacity 110ms ease,
    transform 130ms ease;
  will-change: opacity, transform;
}

.relay-view-forward-enter-from {
  opacity: 0;
  transform: translate3d(22px, 8px, 0) scale(.992);
}

.relay-view-forward-leave-to {
  opacity: 0;
  transform: translate3d(-10px, -2px, 0) scale(.996);
}

.relay-view-back-enter-from {
  opacity: 0;
  transform: translate3d(-22px, 8px, 0) scale(.992);
}

.relay-view-back-leave-to {
  opacity: 0;
  transform: translate3d(10px, -2px, 0) scale(.996);
}

.relay-title-enter-active,
.relay-title-leave-active {
  transition: opacity 140ms ease, transform 180ms cubic-bezier(.16, 1, .3, 1);
}

.relay-title-enter-from {
  opacity: 0;
  transform: translateY(7px);
}

.relay-title-leave-to {
  opacity: 0;
  transform: translateY(-5px);
}

.relay-menu-progress {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  overflow: hidden;
  pointer-events: none;
}

.relay-menu-progress > span {
  display: block;
  width: 100%;
  height: 100%;
  transform-origin: left center;
  background: linear-gradient(90deg, #10b981, #06b6d4 72%, rgba(6, 182, 212, 0));
  box-shadow: 0 0 12px rgba(16, 185, 129, .55);
  animation: relayMenuProgress 460ms cubic-bezier(.2, .8, .2, 1) both;
}

@keyframes relayMenuProgress {
  0% { opacity: 0; transform: scaleX(.04); }
  18% { opacity: 1; }
  82% { opacity: 1; transform: scaleX(.92); }
  100% { opacity: 0; transform: scaleX(1); }
}

.key-option-expand-enter-active,
.key-option-expand-leave-active {
  overflow: hidden;
  transition: max-height 260ms cubic-bezier(.2, .8, .2, 1), opacity 180ms ease, transform 220ms ease;
}

.key-option-expand-enter-from,
.key-option-expand-leave-to {
  max-height: 0;
  opacity: 0;
  transform: translateY(-6px);
}

.key-option-expand-enter-to,
.key-option-expand-leave-from {
  max-height: 360px;
  opacity: 1;
  transform: translateY(0);
}

.key-option-body {
  transform-origin: top;
}

.channel-radar {
  position: relative;
  display: inline-block;
  width: 18px;
  height: 18px;
  overflow: hidden;
  border: 1px solid currentColor;
  border-radius: 999px;
  color: #06b6d4;
  opacity: .9;
}

.channel-radar::before,
.channel-radar::after {
  position: absolute;
  inset: 50% auto auto 50%;
  content: '';
  background: currentColor;
  opacity: .35;
  transform: translate(-50%, -50%);
}

.channel-radar::before { width: 1px; height: 100%; }
.channel-radar::after { width: 100%; height: 1px; }

.channel-radar > span {
  position: absolute;
  inset: 4px;
  border: 1px solid currentColor;
  border-radius: inherit;
  opacity: .5;
}

.channel-radar > i {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 46%;
  height: 1px;
  background: currentColor;
  box-shadow: 0 0 7px currentColor;
  transform-origin: left center;
  animation: channelRadarSpin 850ms linear infinite;
}

.channel-radar-large {
  width: 42px;
  height: 42px;
  border-color: #dbeafe;
  color: #10b981;
  background: linear-gradient(145deg, #f0fdfa, #ecfeff);
}

.channel-radar-large > span { inset: 9px; }
.channel-radar-large:not(.is-active) > i { animation-duration: 4s; opacity: .55; }

.channel-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 45px rgba(15, 23, 42, .08);
}

.channel-card.is-checking {
  border-color: rgba(6, 182, 212, .55);
  box-shadow: 0 0 0 3px rgba(6, 182, 212, .09), 0 20px 50px rgba(8, 145, 178, .12);
}

.channel-card.is-checking::after {
  position: absolute;
  inset: 0;
  z-index: -1;
  content: '';
  background: linear-gradient(110deg, transparent 25%, rgba(207, 250, 254, .38) 48%, transparent 72%);
  animation: channelCardShimmer 1.15s ease-in-out infinite;
}

.channel-scan-line {
  position: absolute;
  z-index: 4;
  top: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background: linear-gradient(90deg, transparent, #06b6d4 20%, #34d399 80%, transparent);
  box-shadow: 0 0 14px rgba(6, 182, 212, .8);
  animation: channelScanLine 1.15s ease-in-out infinite;
}

.channel-check-complete {
  animation: channelCheckComplete 560ms cubic-bezier(.16, 1, .3, 1);
}

.relay-pending-page {
  --pending-accent: #059669;
  --pending-soft: #ecfdf5;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, .88);
  color: #0f172a;
  box-shadow: 0 14px 45px rgba(15, 23, 42, .06), inset 0 1px 0 rgba(255, 255, 255, .92);
  backdrop-filter: blur(20px) saturate(125%);
  -webkit-backdrop-filter: blur(20px) saturate(125%);
  animation: pendingPageIn 380ms cubic-bezier(.16, 1, .3, 1) both;
}

.relay-pending-page.is-orders {
  --pending-accent: #2563eb;
  --pending-soft: #eff6ff;
}

.pending-page-header {
  display: flex;
  min-height: 76px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid #e8edf3;
  padding: 14px 20px;
  background: rgba(255, 255, 255, .72);
}

.pending-page-identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.pending-page-icon,
.pending-empty-icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid #dbe3ec;
  border-radius: 9px;
  background: #fff;
  color: var(--pending-accent);
  box-shadow: 0 4px 14px rgba(15, 23, 42, .05);
}

.pending-page-icon { width: 42px; height: 42px; }
.pending-page-icon svg { width: 19px; height: 19px; }
.pending-page-identity h2 { margin: 0; color: #0f172a; font-size: 17px; font-weight: 900; letter-spacing: 0; }
.pending-page-identity p { margin: 4px 0 0; color: #7b8a9d; font-size: 10px; font-weight: 650; }

.pending-page-badge {
  border: 1px solid color-mix(in srgb, var(--pending-accent) 18%, #e2e8f0);
  border-radius: 999px;
  background: var(--pending-soft);
  padding: 6px 10px;
  color: var(--pending-accent);
  font-size: 10px;
  font-weight: 800;
  white-space: nowrap;
}

.pending-table {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
}

.pending-table-head {
  display: grid;
  min-height: 43px;
  flex: 0 0 auto;
  align-items: center;
  border-bottom: 1px solid #e8edf3;
  background: #f8fafc;
  padding: 0 20px;
  grid-template-columns: 1.35fr 1fr .85fr 1fr 1.15fr;
}

.pending-table-head span {
  color: #718096;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0;
}

.pending-empty {
  display: grid;
  min-height: 300px;
  flex: 1 1 auto;
  place-items: center;
  padding: 36px 20px;
  text-align: center;
}

.pending-empty > div { width: min(100%, 370px); }
.pending-empty-icon { width: 56px; height: 56px; margin: 0 auto; background: var(--pending-soft); }
.pending-empty-icon svg { width: 24px; height: 24px; }
.pending-empty h3 { margin: 18px 0 0; color: #263244; font-size: 20px; font-weight: 850; letter-spacing: 0; }
.pending-empty p { margin: 9px 0 0; color: #718096; font-size: 12px; font-weight: 600; line-height: 1.75; }

.pending-dots {
  display: flex;
  min-height: 8px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  margin-top: 18px;
}

.pending-dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--pending-accent);
  opacity: .28;
  animation: pendingDot 1.4s ease-in-out infinite;
}

.pending-dots i:nth-child(2) { animation-delay: 140ms; }
.pending-dots i:nth-child(3) { animation-delay: 280ms; }

.pending-page-footer {
  min-height: 42px;
  flex: 0 0 auto;
  border-top: 1px solid #e8edf3;
  background: rgba(248, 250, 252, .76);
  padding: 13px 20px;
  color: #8b98a9;
  font-size: 10px;
  font-weight: 650;
  text-align: center;
}

@keyframes pendingPageIn {
  from { opacity: 0; transform: translateY(7px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes pendingDot {
  0%, 70%, 100% { opacity: .22; transform: translateY(0); }
  35% { opacity: .82; transform: translateY(-2px); }
}

@media (max-width: 640px) {
  .pending-page-header { min-height: 68px; padding: 12px 14px; }
  .pending-page-icon { width: 38px; height: 38px; }
  .pending-page-identity p { max-width: 190px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .pending-table-head { display: none; }
  .pending-empty { min-height: 360px; padding: 40px 22px; }
  .pending-page-footer { padding-right: 14px; padding-left: 14px; }
}

.profile-hero {
  background: linear-gradient(150deg, #0b1220 0%, #0e2223 55%, #06251d 100%);
  box-shadow: 0 24px 65px rgba(15, 23, 42, .22), inset 0 1px 0 rgba(255, 255, 255, .07);
}

.profile-hero-grid {
  position: absolute;
  inset: 0;
  z-index: 0;
  background-image: linear-gradient(rgba(255, 255, 255, .05) 1px, transparent 1px), linear-gradient(90deg, rgba(255, 255, 255, .05) 1px, transparent 1px);
  background-size: 30px 30px;
  mask-image: linear-gradient(to bottom, #000, transparent 90%);
}

.profile-hero-glow {
  position: absolute;
  z-index: 0;
  width: 280px;
  height: 280px;
  border-radius: 50%;
  filter: blur(75px);
  opacity: .3;
  animation: horizonGlow 9s ease-in-out infinite alternate;
}

.profile-hero-glow.glow-a { left: -60px; top: -110px; background: #10b981; }
.profile-hero-glow.glow-b { right: -70px; bottom: -130px; background: #22d3ee; animation-delay: -4s; }

.profile-avatar {
  background: linear-gradient(135deg, rgba(16, 185, 129, .35), rgba(34, 211, 238, .25));
  border: 1px solid rgba(255, 255, 255, .18);
  box-shadow: 0 18px 50px rgba(16, 185, 129, .25), inset 0 1px 0 rgba(255, 255, 255, .25);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

@keyframes channelRadarSpin { to { transform: rotate(360deg); } }
@keyframes channelCardShimmer { from { transform: translateX(-90%); } to { transform: translateX(90%); } }
@keyframes channelScanLine { 0%, 100% { top: 0; opacity: .55; } 50% { top: calc(100% - 2px); opacity: 1; } }
@keyframes channelCheckComplete { 0% { transform: scale(.985); } 45% { transform: scale(1.012); } 100% { transform: scale(1); } }
@keyframes horizonGlow { to { transform: translate3d(8%, 5%, 0) scale(1.08); opacity: .25; } }

@media (prefers-reduced-motion: reduce) {
  .relay-view-forward-enter-active,
  .relay-view-forward-leave-active,
  .relay-view-back-enter-active,
  .relay-view-back-leave-active,
  .relay-title-enter-active,
  .relay-title-leave-active {
    transition: none !important;
  }

  .dash-card,
  .dash-section {
    animation: none !important;
  }

  .relay-list-item {
    animation: none !important;
  }

  .dash-card-glow {
    transition: none !important;
  }

  .profile-hero-glow {
    animation: none !important;
  }

  .relay-menu-progress > span {
    animation: none !important;
  }

  .relay-list-refresh-bar > span {
    animation: none !important;
  }

  .channel-radar > i,
  .channel-card.is-checking::after,
  .channel-scan-line,
  .channel-check-complete,
  .relay-pending-page,
  .pending-dots i {
    animation: none !important;
  }

  .channel-card:hover { transform: none; }
}
</style>
