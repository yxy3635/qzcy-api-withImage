<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '@/api/adminApi'
import AppLayout from '@/components/AppLayout.vue'
import ImagePreviewModal from '@/components/ImagePreviewModal.vue'
import Pagination from '@/components/Pagination.vue'
import RequestLoader from '@/components/RequestLoader.vue'
import type { AdminImageRecord, AdminRelayUsageLog } from '@/types'

type LogTab = 'relay' | 'image'

const activeTab = ref<LogTab>('relay')
const imageRecords = ref<AdminImageRecord[]>([])
const imageCurrent = ref(1)
const imagePages = ref(1)
const imageTotal = ref(0)
const imageKeyword = ref('')
const imageStatus = ref('')
const imageLoading = ref(false)
const relayRecords = ref<AdminRelayUsageLog[]>([])
const relayCurrent = ref(1)
const relayPages = ref(1)
const relayTotal = ref(0)
const relayKeyword = ref('')
const relayStatus = ref('')
const relayLoading = ref(false)
const relayStatusOpen = ref(false)
const imageStatusOpen = ref(false)
const preview = ref('')
const error = ref('')

const relayStatusOptions = [
  { value: '', label: 'All status' },
  { value: 'success', label: 'success' },
  { value: 'failed', label: 'failed' },
  { value: 'error', label: 'Error reports' }
]

const imageStatusOptions = [
  { value: '', label: 'All status' },
  { value: 'pending', label: 'pending' },
  { value: 'success', label: 'success' },
  { value: 'failed', label: 'failed' }
]

const relayErrorCount = computed(() => relayRecords.value.filter((item) => isRelayError(item)).length)
const relayStatusLabel = computed(() => relayStatusOptions.find((item) => item.value === relayStatus.value)?.label || 'All status')
const imageStatusLabel = computed(() => imageStatusOptions.find((item) => item.value === imageStatus.value)?.label || 'All status')

async function loadImages(page = 1) {
  imageLoading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.imageRecords(page, 10, imageKeyword.value, imageStatus.value)
    imageRecords.value = data.data.records
    imageCurrent.value = data.data.current
    imagePages.value = data.data.pages
    imageTotal.value = data.data.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load image logs'
  } finally {
    imageLoading.value = false
  }
}

async function loadRelay(page = 1) {
  relayLoading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.relayUsageRecords(page, 12, relayKeyword.value, relayStatus.value)
    relayRecords.value = data.data.records
    relayCurrent.value = data.data.current
    relayPages.value = data.data.pages
    relayTotal.value = data.data.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load API logs'
  } finally {
    relayLoading.value = false
  }
}

function switchTab(tab: LogTab) {
  activeTab.value = tab
  relayStatusOpen.value = false
  imageStatusOpen.value = false
  if (tab === 'image' && imageRecords.value.length === 0) loadImages()
  if (tab === 'relay' && relayRecords.value.length === 0) loadRelay()
}

function showRelayErrors() {
  relayStatus.value = 'error'
  relayStatusOpen.value = false
  loadRelay(1)
}

function selectRelayStatus(value: string) {
  relayStatus.value = value
  relayStatusOpen.value = false
  loadRelay(1)
}

function selectImageStatus(value: string) {
  imageStatus.value = value
  imageStatusOpen.value = false
  loadImages(1)
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

function formatDate(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function cacheHitRate(log: Pick<AdminRelayUsageLog, 'promptTokens' | 'cachedTokens'>) {
  const inputTokens = Number(log.promptTokens || 0)
  if (inputTokens <= 0) return 0
  return Math.min(100, Math.max(0, (Number(log.cachedTokens || 0) / inputTokens) * 100))
}

function cacheHitLabel(log: Pick<AdminRelayUsageLog, 'promptTokens' | 'cachedTokens'>) {
  return `${cacheHitRate(log).toFixed(1)}%`
}

function cacheHitTitle(log: Pick<AdminRelayUsageLog, 'promptTokens' | 'cachedTokens' | 'cacheCreationTokens'>) {
  return `Cache hit ${cacheHitLabel(log)}, read ${compact(log.cachedTokens || 0)}, write ${compact(log.cacheCreationTokens || 0)}`
}

function isRelayError(log: AdminRelayUsageLog) {
  return log.status === 'failed' || Number(log.statusCode || 0) >= 400
}

function statusClass(status?: string, statusCode?: number) {
  if (status === 'success' && Number(statusCode || 0) < 400) return 'border-emerald-200 bg-emerald-50 text-emerald-700'
  if (status === 'failed' || Number(statusCode || 0) >= 400) return 'border-rose-200 bg-rose-50 text-rose-700'
  return 'border-slate-200 bg-slate-50 text-slate-600'
}

onMounted(() => loadRelay())
</script>

<template>
  <AppLayout admin>
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="text-sm font-bold uppercase tracking-[0.22em] text-sky-600">日志中心</p>
        <h1 class="mt-2 text-2xl font-black sm:text-3xl">使用记录与错误报告</h1>
        <p class="mt-2 text-sm text-slate-500">查看用户生图记录、中转 API 调用、Token 用量、缓存命中和失败请求。</p>
      </div>
      <div class="grid w-full grid-cols-2 overflow-hidden rounded-2xl border border-slate-200 bg-white p-1 shadow-sm sm:w-auto">
        <button class="rounded-xl px-4 py-2 text-sm font-black transition" :class="activeTab === 'relay' ? 'bg-slate-950 text-white' : 'text-slate-500 hover:bg-slate-50'" @click="switchTab('relay')">API 使用记录</button>
        <button class="rounded-xl px-4 py-2 text-sm font-black transition" :class="activeTab === 'image' ? 'bg-slate-950 text-white' : 'text-slate-500 hover:bg-slate-50'" @click="switchTab('image')">生图日志</button>
      </div>
    </div>

    <p v-if="error" class="mt-4 rounded-xl bg-red-50 px-3 py-2 text-sm font-semibold text-red-600">{{ error }}</p>

    <template v-if="activeTab === 'relay'">
      <section class="mt-6 rounded-2xl border border-slate-200 bg-white p-4 shadow-[0_18px_60px_rgba(21,32,51,0.07)]">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div class="grid flex-1 gap-3 md:grid-cols-[1fr_180px_100px]">
            <input v-model="relayKeyword" class="input rounded-2xl" placeholder="搜索用户、密钥、模型、渠道、端点、错误信息" @keyup.enter="loadRelay(1)" />
            <div class="relative">
              <button class="flex h-12 w-full items-center justify-between rounded-2xl border border-slate-200 bg-white/90 px-4 text-left text-sm font-black text-slate-700 shadow-sm outline-none transition hover:border-sky-200 hover:bg-sky-50/70 focus:border-sky-300 focus:ring-4 focus:ring-sky-100" type="button" @click="relayStatusOpen = !relayStatusOpen">
                <span>{{ relayStatusLabel }}</span>
                <svg class="h-4 w-4 text-slate-400 transition" :class="relayStatusOpen ? 'rotate-180' : ''" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 11.17l3.71-3.94a.75.75 0 1 1 1.08 1.04l-4.25 4.5a.75.75 0 0 1-1.08 0l-4.25-4.5a.75.75 0 0 1 .02-1.06Z" clip-rule="evenodd" /></svg>
              </button>
              <div v-if="relayStatusOpen" class="absolute left-0 right-0 top-[calc(100%+8px)] z-30 overflow-hidden rounded-2xl border border-slate-200 bg-white p-1 shadow-[0_18px_45px_rgba(15,23,42,0.16)]">
                <button v-for="option in relayStatusOptions" :key="option.value || 'all'" class="flex h-10 w-full items-center justify-between rounded-xl px-3 text-left text-sm font-black transition" :class="relayStatus === option.value ? 'bg-slate-950 text-white' : 'text-slate-600 hover:bg-slate-50'" type="button" @click="selectRelayStatus(option.value)">
                  <span>{{ option.label }}</span>
                  <span v-if="relayStatus === option.value" class="h-2 w-2 rounded-full bg-emerald-400"></span>
                </button>
              </div>
            </div>
            <button class="btn-primary rounded-2xl" @click="loadRelay(1)">查询</button>
          </div>
          <button class="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-black text-rose-700 transition hover:bg-rose-100" @click="showRelayErrors">
            错误报告 <span class="ml-1 rounded-full bg-white px-2 py-0.5 text-xs">{{ relayErrorCount }}</span>
          </button>
          <div class="rounded-2xl border border-slate-100 bg-slate-50 px-4 py-3">
            <p class="text-xs font-semibold text-slate-500">API 记录总数</p>
            <p class="mt-1 text-xl font-black text-slate-950">{{ relayTotal }}</p>
          </div>
        </div>
      </section>

      <section class="relative mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-[0_18px_60px_rgba(21,32,51,0.07)]">
        <div class="overflow-x-auto">
          <table class="w-full min-w-[1320px] table-fixed text-left text-sm">
            <colgroup>
              <col class="w-[130px]" /><col class="w-[230px]" /><col class="w-[220px]" /><col class="w-[220px]" /><col class="w-[150px]" /><col class="w-[230px]" /><col class="w-[80px]" /><col class="w-[170px]" /><col />
            </colgroup>
            <thead class="bg-slate-50 text-xs font-black uppercase tracking-[0.12em] text-slate-500">
              <tr><th class="px-5 py-4">用户</th><th>API 密钥 / 模型</th><th>端点 / 类型</th><th>Token</th><th>费用</th><th>状态</th><th>耗时</th><th>时间</th><th>User-Agent</th></tr>
            </thead>
            <tbody class="divide-y divide-slate-100 font-semibold">
              <tr v-if="!relayLoading && relayRecords.length === 0"><td colspan="9" class="p-8 text-sm text-slate-500">暂无 API 使用记录</td></tr>
              <tr v-for="log in relayRecords" :key="log.id" class="align-top transition duration-200 hover:bg-sky-50/60" :class="relayLoading ? 'opacity-55' : 'opacity-100'">
                <td class="px-5 py-4"><p class="font-black text-slate-900">{{ log.username || '未知用户' }}</p><p class="mt-1 text-xs text-slate-500">ID: {{ log.userId }}</p></td>
                <td class="py-4"><p class="max-w-[180px] truncate font-black text-slate-900">{{ log.tokenName || '-' }}</p><p class="mt-1 max-w-[240px] truncate font-mono text-xs text-slate-500">{{ log.model || '-' }}</p><p class="mt-1 text-xs text-slate-400">{{ log.channelName || '-' }}</p></td>
                <td class="py-4"><p class="max-w-[260px] truncate font-mono text-xs text-slate-600">{{ log.endpoint || '-' }}</p><span class="mt-2 inline-flex rounded bg-blue-50 px-2 py-1 text-xs font-black text-blue-600">{{ log.modelType || '-' }}</span></td>
                <td class="py-4"><div class="w-48" :title="cacheHitTitle(log)"><p><span class="text-emerald-600">↓ {{ compact(log.promptTokens || 0) }}</span> <span class="text-violet-600">↑ {{ compact(log.completionTokens || 0) }}</span></p><div class="mt-2 flex items-center justify-between text-[11px] font-black"><span class="text-cyan-600">缓存命中</span><span class="text-slate-600">{{ cacheHitLabel(log) }}</span></div><div class="mt-1 h-2 overflow-hidden rounded-full bg-slate-100"><div class="h-full rounded-full bg-gradient-to-r from-cyan-400 via-emerald-400 to-teal-500 shadow-[0_0_14px_rgba(20,184,166,0.35)] transition-all duration-700 ease-out" :class="(log.cachedTokens || 0) > 0 ? 'animate-pulse' : ''" :style="{ width: `${cacheHitRate(log)}%` }"></div></div><p class="mt-1 text-[11px] font-bold text-slate-400">读 {{ compact(log.cachedTokens || 0) }} · 写 {{ compact(log.cacheCreationTokens || 0) }} · 总 {{ compact(log.totalTokens || 0) }}</p></div></td>
                <td class="py-4"><p class="font-black text-emerald-600">{{ money(log.cost) }}</p><p class="mt-1 text-xs text-slate-500">组 {{ Number(log.groupRatio || 1).toFixed(3) }}x / 渠道 {{ Number(log.channelRatio || 1).toFixed(3) }}x</p></td>
                <td class="py-4"><span class="inline-flex rounded-full border px-3 py-1 text-xs font-black" :class="statusClass(log.status, log.statusCode)">HTTP {{ log.statusCode || 0 }} · {{ log.status || '-' }}</span><p v-if="log.message" class="mt-2 max-w-[260px] whitespace-pre-wrap break-words text-xs leading-5 text-rose-600">{{ log.message }}</p></td>
                <td class="py-4">{{ ((log.durationMs || 0) / 1000).toFixed(2) }}s</td>
                <td class="py-4 text-slate-500">{{ formatDate(log.createdAt) }}</td>
                <td class="py-4"><p class="max-w-[280px] truncate text-xs text-slate-500">{{ log.userAgent || '-' }}</p></td>
              </tr>
            </tbody>
          </table>
        </div>
        <Transition enter-active-class="transition duration-200 ease-out" enter-from-class="opacity-0" enter-to-class="opacity-100" leave-active-class="transition duration-150 ease-in" leave-from-class="opacity-100" leave-to-class="opacity-0">
          <div v-if="relayLoading" class="absolute inset-0 z-20 grid place-items-center bg-white/55 backdrop-blur-[2px]">
            <div class="rounded-3xl border border-white/80 bg-white/90 px-8 py-6 shadow-[0_20px_60px_rgba(15,23,42,0.14)]"><RequestLoader label="正在加载 API 使用记录" :cell-size="16" /></div>
          </div>
        </Transition>
        <div class="border-t border-slate-100 bg-white/70 p-4"><Pagination :current="relayCurrent" :pages="relayPages" @change="loadRelay" /></div>
      </section>
    </template>

    <template v-else>
      <section class="mt-6 rounded-2xl border border-slate-200 bg-white p-4 shadow-[0_18px_60px_rgba(21,32,51,0.07)]">
        <div class="grid gap-3 md:grid-cols-[1fr_180px_100px]">
          <input v-model="imageKeyword" class="input rounded-2xl" placeholder="搜索用户名或提示词" @keyup.enter="loadImages(1)" />
          <div class="relative">
            <button class="flex h-12 w-full items-center justify-between rounded-2xl border border-slate-200 bg-white/90 px-4 text-left text-sm font-black text-slate-700 shadow-sm outline-none transition hover:border-sky-200 hover:bg-sky-50/70 focus:border-sky-300 focus:ring-4 focus:ring-sky-100" type="button" @click="imageStatusOpen = !imageStatusOpen">
              <span>{{ imageStatusLabel }}</span>
              <svg class="h-4 w-4 text-slate-400 transition" :class="imageStatusOpen ? 'rotate-180' : ''" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 11.17l3.71-3.94a.75.75 0 1 1 1.08 1.04l-4.25 4.5a.75.75 0 0 1-1.08 0l-4.25-4.5a.75.75 0 0 1 .02-1.06Z" clip-rule="evenodd" /></svg>
            </button>
            <div v-if="imageStatusOpen" class="absolute left-0 right-0 top-[calc(100%+8px)] z-30 overflow-hidden rounded-2xl border border-slate-200 bg-white p-1 shadow-[0_18px_45px_rgba(15,23,42,0.16)]">
              <button v-for="option in imageStatusOptions" :key="option.value || 'all'" class="flex h-10 w-full items-center justify-between rounded-xl px-3 text-left text-sm font-black transition" :class="imageStatus === option.value ? 'bg-slate-950 text-white' : 'text-slate-600 hover:bg-slate-50'" type="button" @click="selectImageStatus(option.value)"><span>{{ option.label }}</span><span v-if="imageStatus === option.value" class="h-2 w-2 rounded-full bg-emerald-400"></span></button>
            </div>
          </div>
          <button class="btn-primary rounded-2xl" @click="loadImages(1)">查询</button>
        </div>
      </section>
      <section class="relative mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-[0_18px_60px_rgba(21,32,51,0.07)]">
        <div class="hidden grid-cols-[120px_1fr_110px_110px_180px_120px] gap-4 border-b border-slate-100 bg-slate-50 px-5 py-4 text-xs font-bold uppercase tracking-[0.14em] text-slate-500 xl:grid"><span>用户</span><span>提示词</span><span>状态</span><span>成本</span><span>时间</span><span>结果</span></div>
        <div class="divide-y divide-slate-100">
          <div v-for="record in imageRecords" :key="record.id" class="grid gap-3 px-4 py-4 text-sm transition duration-200 hover:bg-sky-50/70 sm:gap-4 sm:px-5 xl:grid-cols-[120px_1fr_110px_110px_180px_120px] xl:items-center" :class="imageLoading ? 'opacity-55' : 'opacity-100'">
            <div><p class="font-black text-slate-900">{{ record.username || '未知用户' }}</p><p class="mt-1 text-xs text-slate-500">ID: {{ record.userId }}</p></div>
            <p class="line-clamp-2 leading-6 text-slate-700">{{ record.prompt }}</p>
            <span class="w-fit rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-bold text-slate-600">{{ record.status }}</span>
            <span class="font-semibold text-slate-700">${{ Number(record.cost || 0).toFixed(2) }}</span>
            <span class="text-slate-500">{{ formatDate(record.createdAt) }}</span>
            <button v-if="record.generatedImageUrl" class="group w-fit overflow-hidden rounded-xl border border-slate-200 bg-white p-1 shadow-sm" @click="preview = record.generatedImageUrl || ''"><img :src="record.generatedImageUrl" class="h-16 w-24 rounded-lg object-cover transition duration-300 group-hover:scale-105" alt="生成结果" /></button>
            <span v-else class="text-xs text-slate-400">暂无图像</span>
          </div>
          <div v-if="!imageLoading && imageRecords.length === 0" class="p-8 text-sm text-slate-500">暂无生图记录</div>
        </div>
        <Transition enter-active-class="transition duration-200 ease-out" enter-from-class="opacity-0" enter-to-class="opacity-100" leave-active-class="transition duration-150 ease-in" leave-from-class="opacity-100" leave-to-class="opacity-0">
          <div v-if="imageLoading" class="absolute inset-0 z-20 grid place-items-center bg-white/55 backdrop-blur-[2px]">
            <div class="rounded-3xl border border-white/80 bg-white/90 px-8 py-6 shadow-[0_20px_60px_rgba(15,23,42,0.14)]"><RequestLoader label="正在加载生图记录" :cell-size="16" /></div>
          </div>
        </Transition>
        <div class="border-t border-slate-100 bg-white/70 p-4"><Pagination :current="imageCurrent" :pages="imagePages" @change="loadImages" /></div>
      </section>
    </template>

    <ImagePreviewModal :src="preview" @close="preview = ''" />
  </AppLayout>
</template>
