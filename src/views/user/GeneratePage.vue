<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import ImagePreviewModal from '@/components/ImagePreviewModal.vue'
import { imageApi } from '@/api/imageApi'
import { useAuthStore } from '@/store/authStore'
import type { ImageGenerationConfig, ImageRecord } from '@/types'

const auth = useAuthStore()
const router = useRouter()
const prompt = ref('')
const loading = ref(false)
const error = ref('')
const result = ref<ImageRecord | null>(null)
const previewRecord = ref<ImageRecord | null>(null)
const recent = ref<ImageRecord[]>([])
const configs = ref<ImageGenerationConfig[]>([])
const selectedQuality = ref('1k')
const galleryFilter = ref('all')
const specOpen = ref(false)
const filterOpen = ref(false)
const formatOpen = ref(false)
const mobilePanelOpen = ref(false)
const imageFormat = ref('PNG')
const elapsedSeconds = ref(0)
const estimatedSeconds = ref(45)
let timer: number | undefined
let activePollId = 0
const POLL_INTERVAL_MS = 2000

const promptPresets = [
  '未来主义建筑，清晨薄雾，广角镜头，超写实',
  '高级香水广告，黑色大理石台面，电影级布光',
  '赛博朋克街区，雨夜霓虹，人物背影，细节丰富'
]

const filterOptions = [
  { value: 'all', label: '全部' },
  { value: 'success', label: '生成成功' },
  { value: 'failed', label: '生成失败' },
  { value: 'pending', label: '生成中' }
]

const formatOptions = ['PNG']

const selectedConfig = computed(() => configs.value.find((item) => item.code === selectedQuality.value))
const selectedFilterLabel = computed(() => filterOptions.find((item) => item.value === galleryFilter.value)?.label || '全部')
const galleryRecords = computed(() => {
  const list = result.value ? [result.value, ...recent.value.filter((item) => item.id !== result.value?.id)] : recent.value
  const filtered = galleryFilter.value === 'all' ? list : list.filter((item) => item.status === galleryFilter.value)
  return filtered.slice(0, 12)
})

async function loadRecent() {
  const { data } = await imageApi.history(1, 12)
  recent.value = data.data.records
}

async function loadConfigs() {
  const { data } = await imageApi.configs()
  configs.value = data.data
  if (configs.value.length && !configs.value.some((item) => item.code === selectedQuality.value)) {
    selectedQuality.value = configs.value[0]?.code || '1k'
  }
}

async function loadEstimate() {
  const { data } = await imageApi.estimate()
  estimatedSeconds.value = Math.max(1, Math.round(Number(data.data.averageDurationMs || 45000) / 1000))
}

async function generate() {
  error.value = ''
  if (!prompt.value.trim()) {
    error.value = '请输入提示词'
    return
  }
  await loadEstimate()
  elapsedSeconds.value = 0
  window.clearInterval(timer)
  timer = window.setInterval(() => {
    elapsedSeconds.value += 1
  }, 1000)
  loading.value = true
  const pollId = ++activePollId
  try {
    const { data } = await imageApi.generate(prompt.value, selectedQuality.value)
    result.value = data.data
    recent.value = [data.data, ...recent.value.filter((item) => item.id !== data.data.id)]
    const completed = await waitForGeneration(data.data.id, pollId)
    result.value = completed
    recent.value = [completed, ...recent.value.filter((item) => item.id !== completed.id)]
    if (completed.status === 'success') {
      previewRecord.value = completed
    } else if (completed.status === 'failed') {
      error.value = '图像生成失败，请稍后重试或更换服务商线路'
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '生成失败'
  } finally {
    window.clearInterval(timer)
    await Promise.allSettled([auth.refreshUser(), loadRecent(), loadEstimate()])
    loading.value = false
  }
}

function sleep(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

async function waitForGeneration(recordId: number, pollId: number) {
  while (pollId === activePollId) {
    await sleep(POLL_INTERVAL_MS)
    try {
      const { data } = await imageApi.detail(recordId)
      const record = data.data
      result.value = record
      recent.value = [record, ...recent.value.filter((item) => item.id !== record.id)]
      if (record.status === 'success' || record.status === 'failed') {
        return record
      }
    } catch {
      await loadRecent()
    }
  }
  throw new Error('生成轮询已取消')
}

async function remove(record: ImageRecord) {
  if (!confirm('确认删除这张图片记录？')) return
  error.value = ''
  try {
    await imageApi.remove(record.id)
    if (result.value?.id === record.id) result.value = null
    if (previewRecord.value?.id === record.id) previewRecord.value = null
    await loadRecent()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除失败'
  }
}

function usePreset(text: string) {
  prompt.value = text
}

function selectSpec(code: string) {
  selectedQuality.value = code
  specOpen.value = false
}

function selectFilter(value: string) {
  galleryFilter.value = value
  filterOpen.value = false
}

function selectFormat(value: string) {
  imageFormat.value = value
  formatOpen.value = false
}

function logout() {
  auth.logout()
  router.push('/login')
}

onMounted(() => {
  void Promise.all([auth.refreshUser(), loadRecent(), loadConfigs(), loadEstimate()])
})

onBeforeUnmount(() => {
  activePollId += 1
  window.clearInterval(timer)
})
</script>

<template>
  <div class="min-h-screen bg-[#fbfbfc] pb-32 text-slate-950 md:pb-56">
    <header class="sticky top-0 z-30 border-b border-slate-100 bg-white/86 backdrop-blur-2xl">
      <div class="mx-auto flex min-h-16 max-w-7xl flex-wrap items-center justify-between gap-3 px-3 py-3 sm:px-4 md:px-8">
        <div class="flex min-w-0 items-center gap-2 sm:gap-3">
          <RouterLink class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 transition hover:bg-slate-50 sm:text-sm" to="/user/dashboard">返回控制台</RouterLink>
          <h1 class="truncate text-base font-black tracking-tight sm:text-lg">AI 图像创作台</h1>
        </div>
        <div class="flex shrink-0 items-center gap-1.5 sm:gap-2">
          <RouterLink class="rounded-2xl bg-slate-950 px-3 py-2 text-xs font-black text-white sm:px-4 sm:text-sm" to="/user/history">画廊</RouterLink>
          <RouterLink class="rounded-2xl px-3 py-2 text-xs font-bold text-slate-500 transition hover:bg-slate-100 sm:px-4 sm:text-sm" to="/user/dashboard">资产</RouterLink>
          <span class="hidden rounded-2xl border border-sky-100 bg-sky-50 px-3 py-2 text-sm font-black text-sky-700 md:inline-flex">
            ￥{{ Number(auth.userInfo?.balance || 0).toFixed(2) }}
          </span>
          <button class="rounded-2xl border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 transition hover:bg-slate-50 sm:px-4 sm:text-sm" @click="logout">退出</button>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-7xl px-3 py-4 sm:px-4 md:px-8 md:py-7">
      <section class="fixed inset-x-3 bottom-3 z-40 mx-auto rounded-[24px] border border-slate-200 bg-white/94 p-3 shadow-[0_18px_60px_rgba(15,23,42,0.16)] backdrop-blur-2xl md:inset-x-0 md:bottom-6 md:w-[min(860px,calc(100%-32px))] md:overflow-visible md:rounded-[28px] md:p-4">
        <div class="flex items-center gap-3">
          <input
            v-model="prompt"
            class="h-12 min-w-0 flex-1 rounded-2xl border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-sky-300 focus:ring-4 focus:ring-sky-100"
            placeholder="描述你想生成的图像..."
            @keyup.enter="generate"
          />
          <button class="hidden h-12 w-12 place-items-center rounded-2xl border border-slate-200 bg-slate-100 text-slate-500 transition hover:bg-slate-200 sm:grid" title="附件">
            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m21 8.5-9.6 9.6a5 5 0 0 1-7.1-7.1l9.2-9.2a3.5 3.5 0 1 1 5 5l-9.2 9.2a2 2 0 0 1-2.8-2.8l8.5-8.5" /></svg>
          </button>
          <button class="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-blue-600 text-white shadow-[0_14px_34px_rgba(37,99,235,0.26)] transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60" :disabled="loading || configs.length === 0" @click="generate">
            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 12h14m0 0-5-5m5 5-5 5" /></svg>
          </button>
        </div>
        <div class="mt-2 flex items-center justify-between gap-2 md:hidden">
          <button class="rounded-xl bg-slate-100 px-3 py-1.5 text-xs font-black text-slate-600" type="button" @click="mobilePanelOpen = !mobilePanelOpen">
            {{ mobilePanelOpen ? '收起参数' : '参数' }}
          </button>
          <div class="min-w-0 truncate text-xs font-bold text-slate-500">
            {{ selectedConfig?.name || '选择规格' }} / {{ imageFormat }} / ￥{{ Number(selectedConfig?.price || 0).toFixed(2) }}
          </div>
        </div>
        <div class="mt-3 grid gap-2 text-xs font-bold text-slate-500 md:grid md:grid-cols-[2fr_0.8fr_0.8fr_1fr] md:gap-3" :class="mobilePanelOpen ? 'grid' : 'hidden'">
          <div class="relative">
            <span class="block">生成规格</span>
            <button
              class="mt-1 flex h-10 w-full items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white px-3 text-left font-black text-slate-700 outline-none transition duration-300 hover:border-sky-200 hover:bg-sky-50/40 focus:border-sky-300 focus:ring-4 focus:ring-sky-100"
              type="button"
              @click="specOpen = !specOpen"
            >
              <span class="min-w-0 truncate">
                {{ selectedConfig?.name || '选择规格' }}
                <span class="font-semibold text-slate-400">/ {{ selectedConfig?.size || '-' }} / ￥{{ Number(selectedConfig?.price || 0).toFixed(2) }}</span>
              </span>
              <svg class="h-4 w-4 shrink-0 text-slate-400 transition duration-300" :class="specOpen ? 'rotate-180' : ''" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m6 9 6 6 6-6" />
              </svg>
            </button>
            <Transition name="dropdown">
              <div v-if="specOpen" class="absolute bottom-full z-50 mb-2 w-full overflow-hidden rounded-2xl border border-slate-200 bg-white p-1 shadow-[0_22px_70px_rgba(15,23,42,0.14)]">
                <button
                  v-for="config in configs"
                  :key="config.code"
                  class="flex w-full items-center justify-between gap-3 rounded-xl px-3 py-3 text-left transition duration-200 hover:bg-sky-50"
                  :class="selectedQuality === config.code ? 'bg-sky-50 text-sky-700' : 'text-slate-700'"
                  type="button"
                  @click="selectSpec(config.code)"
                >
                  <span class="min-w-0">
                    <span class="block truncate text-sm font-black">{{ config.name }}</span>
                    <span class="mt-1 block truncate text-xs font-semibold text-slate-400">{{ config.size }} / {{ config.quality }}</span>
                  </span>
                  <span class="shrink-0 rounded-lg bg-slate-100 px-2 py-1 text-xs font-black">￥{{ Number(config.price).toFixed(2) }}</span>
                </button>
              </div>
            </Transition>
          </div>
          <div class="relative">
            <span class="block">格式</span>
            <button
              class="mt-1 flex h-10 w-full items-center justify-between gap-2 rounded-xl border border-slate-200 bg-white px-3 font-black text-slate-700 outline-none transition duration-300 hover:border-sky-200 hover:bg-sky-50/40 focus:border-sky-300 focus:ring-4 focus:ring-sky-100"
              type="button"
              @click="formatOpen = !formatOpen"
            >
              <span>{{ imageFormat }}</span>
              <svg class="h-4 w-4 text-slate-400 transition duration-300" :class="formatOpen ? 'rotate-180' : ''" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m6 9 6 6 6-6" />
              </svg>
            </button>
            <Transition name="dropdown">
              <div v-if="formatOpen" class="absolute bottom-full z-50 mb-2 w-full overflow-hidden rounded-2xl border border-slate-200 bg-white p-1 shadow-[0_22px_70px_rgba(15,23,42,0.14)]">
                <button
                  v-for="option in formatOptions"
                  :key="option"
                  class="w-full rounded-xl px-3 py-2.5 text-left text-sm font-black transition duration-200 hover:bg-sky-50"
                  :class="imageFormat === option ? 'bg-sky-50 text-sky-700' : 'text-slate-700'"
                  type="button"
                  @click="selectFormat(option)"
                >
                  {{ option }}
                </button>
              </div>
            </Transition>
          </div>
          <label>
            <span class="block">数量</span>
            <input class="mt-1 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 outline-none" value="1" disabled />
          </label>
          <div class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2">
            <span class="block">本次生成</span>
            <div class="mt-1 flex items-center justify-between gap-2 text-slate-700">
              <span>￥{{ Number(selectedConfig?.price || 0).toFixed(2) }}</span>
              <span>约 {{ estimatedSeconds }}s</span>
            </div>
          </div>
        </div>
        <div class="mt-3 gap-2 overflow-x-auto pb-1 md:flex md:flex-wrap md:overflow-visible md:pb-0" :class="mobilePanelOpen ? 'flex' : 'hidden'">
          <button v-for="preset in promptPresets" :key="preset" class="shrink-0 rounded-xl bg-slate-100 px-3 py-1.5 text-xs font-semibold text-slate-600 transition hover:bg-sky-50 hover:text-sky-700" @click="usePreset(preset)">
            {{ preset }}
          </button>
        </div>
      </section>
      <div class="mb-5 grid grid-cols-[44px_1fr] gap-3 sm:flex sm:items-center">
        <button class="grid h-11 w-11 place-items-center rounded-2xl border border-slate-200 bg-white text-slate-400 shadow-sm">
          <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11.5 4.5 13.8 9l5 .7-3.6 3.5.9 4.9-4.6-2.4-4.4 2.4.8-4.9L4.3 9.7l5-.7 2.2-4.5Z" />
          </svg>
        </button>
        <div class="relative min-w-0">
          <button
            class="flex h-11 w-full items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white px-4 text-sm font-black text-slate-700 shadow-sm outline-none transition duration-300 hover:border-sky-200 hover:bg-sky-50/40 focus:border-sky-300 focus:ring-4 focus:ring-sky-100 sm:min-w-36"
            type="button"
            @click="filterOpen = !filterOpen"
          >
            <span>{{ selectedFilterLabel }}</span>
            <svg class="h-4 w-4 text-slate-400 transition duration-300" :class="filterOpen ? 'rotate-180' : ''" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m6 9 6 6 6-6" />
            </svg>
          </button>
          <Transition name="dropdown">
            <div v-if="filterOpen" class="absolute left-0 top-full z-50 mt-2 w-40 overflow-hidden rounded-2xl border border-slate-200 bg-white p-1 shadow-[0_22px_70px_rgba(15,23,42,0.14)]">
              <button
                v-for="option in filterOptions"
                :key="option.value"
                class="w-full rounded-xl px-3 py-2.5 text-left text-sm font-black transition duration-200 hover:bg-sky-50"
                :class="galleryFilter === option.value ? 'bg-sky-50 text-sky-700' : 'text-slate-700'"
                type="button"
                @click="selectFilter(option.value)"
              >
                {{ option.label }}
              </button>
            </div>
          </Transition>
        </div>
        <div class="relative col-span-2 min-w-0 sm:col-span-1 sm:flex-1">
          <svg class="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m21 21-4.35-4.35M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" />
          </svg>
          <input class="h-11 w-full rounded-2xl border border-slate-200 bg-white pl-12 pr-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:ring-4 focus:ring-sky-100" placeholder="搜索提示词、参数..." />
        </div>
      </div>

      <section class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <article
          v-for="record in galleryRecords"
          :key="record.id"
          class="group grid min-h-[188px] overflow-hidden rounded-2xl border bg-white shadow-sm transition hover:-translate-y-0.5"
          :class="record.status === 'pending' ? 'grid-cols-1 sm:grid-cols-[142px_1fr] border-sky-100 shadow-[0_14px_42px_rgba(14,165,233,0.10)] hover:shadow-[0_20px_55px_rgba(14,165,233,0.12)]' : 'grid-cols-1 sm:grid-cols-[190px_1fr] border-slate-200 hover:shadow-[0_20px_60px_rgba(15,23,42,0.08)]'"
        >
          <button class="relative overflow-hidden bg-slate-100" @click="previewRecord = record">
            <img v-if="record.generatedImageUrl" :src="record.generatedImageUrl" class="h-48 w-full object-cover transition duration-500 group-hover:scale-105 sm:h-full sm:min-h-[188px]" alt="生成图像" />
            <div v-else-if="record.status === 'pending'" class="grid h-44 w-full place-items-center bg-gradient-to-br from-sky-50 to-fuchsia-50 sm:h-full sm:min-h-[188px]">
              <div class="loader loader-compact"></div>
            </div>
            <div v-else class="grid h-44 w-full place-items-center text-sm font-bold text-slate-400 sm:h-full sm:min-h-[188px]">暂无图像</div>
            <div class="absolute left-2 top-2 flex gap-1">
              <span class="rounded-md bg-slate-950/70 px-2 py-1 text-xs font-black text-white">{{ record.status }}</span>
            </div>
          </button>
          <div class="flex min-w-0 flex-col p-4">
            <p class="line-clamp-3 text-sm font-semibold leading-6 text-slate-700">{{ record.prompt }}</p>
            <div class="mt-auto">
              <div v-if="record.status === 'pending'" class="flex flex-wrap items-center gap-2 text-xs font-bold text-slate-500">
                <span>正在生成图像</span>
                <span class="h-1 w-1 rounded-full bg-slate-300"></span>
                <span class="text-base font-black leading-none text-slate-950">{{ elapsedSeconds }}s</span>
                <span>预计约 {{ estimatedSeconds }}s</span>
              </div>
              <span v-else class="inline-flex rounded-lg bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-500">默认配置</span>
              <div class="mt-4 flex items-center justify-end gap-3 text-slate-400">
                <button class="transition hover:text-sky-600" title="复用提示词" @click="prompt = record.prompt">
                  <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7h11a5 5 0 0 1 0 10H9m0 0 3-3m-3 3 3 3" /></svg>
                </button>
                <button class="transition hover:text-sky-600" title="预览" @click="previewRecord = record">
                  <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" /></svg>
                </button>
                <button class="transition hover:text-red-600" title="删除" @click="remove(record)">
                  <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 7h12M9 7V5h6v2m-7 3v8m4-8v8m4-8v8M8 7l1 13h6l1-13" /></svg>
                </button>
              </div>
            </div>
          </div>
        </article>

        <div v-if="galleryRecords.length === 0" class="rounded-2xl border border-dashed border-slate-200 bg-white p-10 text-center text-sm font-semibold text-slate-500 md:col-span-2 xl:col-span-3">
          暂无图像，输入提示词开始创作。
        </div>
      </section>

      <p v-if="error" class="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{{ error }}</p>
    </main>

    <ImagePreviewModal :record="previewRecord || undefined" :src="previewRecord?.generatedImageUrl" @close="previewRecord = null" @delete="remove" />
  </div>
</template>
