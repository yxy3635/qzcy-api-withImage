<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import ImagePreviewModal from '@/components/ImagePreviewModal.vue'
import RequestLoader from '@/components/RequestLoader.vue'
import { imageApi } from '@/api/imageApi'
import { useAuthStore } from '@/store/authStore'
import { useToast } from '@/composables/useToast'
import type { ImageGenerationConfig, ImageRecord } from '@/types'

const auth = useAuthStore()
const router = useRouter()
const toast = useToast()
const prompt = ref('')
const loading = ref(false)
const submitLoading = ref(false)
const error = ref('')
const result = ref<ImageRecord | null>(null)
const previewRecord = ref<ImageRecord | null>(null)
const errorRecord = ref<ImageRecord | null>(null)
const reusingRecordId = ref<number | null>(null)
const editingRecordId = ref<number | null>(null)
const recent = ref<ImageRecord[]>([])
const configs = ref<ImageGenerationConfig[]>([])
const selectedQuality = ref('1k')
const galleryFilter = ref('all')
const specOpen = ref(false)
const filterOpen = ref(false)
const formatOpen = ref(false)
const sizeModalOpen = ref(false)
const mobilePanelOpen = ref(false)
const imageFormat = ref('PNG')
const elapsedSeconds = ref(0)
const estimatedSeconds = ref(45)
const fileInput = ref<HTMLInputElement | null>(null)
const uploadedImages = ref<UploadedImage[]>([])
const sizeMode = ref<'auto' | 'ratio' | 'custom'>('auto')
const selectedRatio = ref('1:1')
const customRatioActive = ref(false)
const customRatio = ref('16:9')
const customWidth = ref(1280)
const customHeight = ref(720)
let timer: number | undefined
let activePollId = 0
const POLL_INTERVAL_MS = 2000
const MAX_UPLOAD_IMAGES = 4

interface UploadedImage {
  name: string
  url: string
  dataUrl: string
}

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
const sizeScales = [
  { value: '1k', label: '1K', longSide: 1280, square: 1024 },
  { value: '2k', label: '2K', longSide: 2560, square: 2048 },
  { value: '4k', label: '4K', longSide: 3840, square: 4096 }
] as const
const ratioOptions = ['1:1', '3:2', '2:3', '16:9', '9:16', '4:3', '3:4', '21:9']

const selectedConfig = computed(() => configs.value.find((item) => item.code === selectedQuality.value))
const selectedFilterLabel = computed(() => filterOptions.find((item) => item.value === galleryFilter.value)?.label || '全部')
const requestLoading = computed(() => submitLoading.value || reusingRecordId.value !== null || editingRecordId.value !== null)
const selectedSizeScale = computed(() => {
  const code = (selectedConfig.value?.code || selectedQuality.value).toLowerCase()
  if (code.includes('4k')) return sizeScales[2]
  if (code.includes('2k')) return sizeScales[1]
  return sizeScales[0]
})
const selectedImageSize = computed(() => {
  if (sizeMode.value === 'auto') return 'auto'
  if (sizeMode.value === 'custom') return `${customWidth.value}x${customHeight.value}`
  return sizeFromRatio(customRatioActive.value ? customRatio.value : selectedRatio.value)
})
const sizeButtonLabel = computed(() => (sizeMode.value === 'auto' ? '自动' : selectedImageSize.value))
const galleryRecords = computed(() => {
  const list = result.value ? [result.value, ...recent.value.filter((item) => item.id !== result.value?.id)] : recent.value
  const filtered = galleryFilter.value === 'all' ? list : list.filter((item) => item.status === galleryFilter.value)
  return filtered.slice(0, 12)
})

function roundToStep(value: number, step = 8) {
  return Math.max(step, Math.round(value / step) * step)
}

function sizeFromRatio(ratio: string) {
  const [ratioWidth, ratioHeight] = ratio.split(':').map(Number)
  if (!ratioWidth || !ratioHeight) return '1024x1024'
  const scale = selectedSizeScale.value
  if (ratioWidth === ratioHeight) {
    return `${scale.square}x${scale.square}`
  }
  if (ratioWidth > ratioHeight) {
    return `${scale.longSide}x${roundToStep((scale.longSide * ratioHeight) / ratioWidth)}`
  }
  return `${roundToStep((scale.longSide * ratioWidth) / ratioHeight)}x${scale.longSide}`
}

function ratioIconStyle(ratio: string) {
  const [ratioWidth, ratioHeight] = ratio.split(':').map(Number)
  if (!ratioWidth || !ratioHeight) return { width: '24px', height: '24px' }
  if (ratioWidth >= ratioHeight) {
    return { width: '28px', height: `${Math.max(12, Math.round((28 * ratioHeight) / ratioWidth))}px` }
  }
  return { width: `${Math.max(12, Math.round((28 * ratioWidth) / ratioHeight))}px`, height: '28px' }
}

function clampDimension(value: number) {
  return Math.min(8192, Math.max(256, roundToStep(Number(value) || 256)))
}

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
  if (loading.value || submitLoading.value) return
  error.value = ''
  if (!prompt.value.trim()) {
    error.value = '请输入提示词'
    toast.warning(error.value)
    return
  }
  submitLoading.value = true
  await loadEstimate()
  elapsedSeconds.value = 0
  window.clearInterval(timer)
  timer = window.setInterval(() => {
    elapsedSeconds.value += 1
  }, 1000)
  loading.value = true
  const pollId = ++activePollId
  const submittedPrompt = prompt.value
  prompt.value = ''
  try {
    const referenceImages = uploadedImages.value.map((item) => item.dataUrl)
    const { data } = await imageApi.generate(submittedPrompt, selectedQuality.value, selectedImageSize.value, referenceImages)
    submitLoading.value = false
    if (referenceImages.length) {
      clearUploads()
    }
    result.value = data.data
    recent.value = [data.data, ...recent.value.filter((item) => item.id !== data.data.id)]
    const completed = await waitForGeneration(data.data.id, pollId)
    result.value = completed
    recent.value = [completed, ...recent.value.filter((item) => item.id !== completed.id)]
    if (completed.status === 'success') {
      previewRecord.value = completed
      toast.success('图像生成完成')
    } else if (completed.status === 'failed') {
      error.value = imageErrorText(completed)
      toast.error(error.value)
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '生成失败'
    toast.error(error.value)
  } finally {
    submitLoading.value = false
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
    toast.success('图片记录已删除')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除失败'
    toast.error(error.value)
  }
}

async function reusePrompt(record: ImageRecord) {
  reusingRecordId.value = record.id
  prompt.value = record.prompt
  await sleep(180)
  if (reusingRecordId.value === record.id) {
    reusingRecordId.value = null
  }
}

function usePreset(text: string) {
  prompt.value = text
}

function imageErrorText(record?: ImageRecord | null) {
  const message = extractErrorMessage(record?.errorMessage)
  const details = [
    record?.errorStatusCode ? `状态码 ${record.errorStatusCode}` : '',
    record?.errorType ? `类型 ${record.errorType}` : ''
  ].filter(Boolean).join('，')
  if (message && details) return `图像生成失败：${message}（${details}）`
  if (message) return `图像生成失败：${message}`
  if (details) return `图像生成失败（${details}）`
  return '图像生成失败，请稍后重试或更换服务商线路'
}

function extractErrorMessage(value?: string) {
  if (!value) return ''
  const text = String(value).trim()
  try {
    const parsed = JSON.parse(text)
    return parsed?.error?.message || parsed?.message || text
  } catch {
    return text
  }
}

function selectSpec(code: string) {
  selectedQuality.value = code
  specOpen.value = false
}

function openUpload() {
  fileInput.value?.click()
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  await addImageFiles(files, true)
  input.value = ''
}

async function handlePaste(event: ClipboardEvent) {
  const files = Array.from(event.clipboardData?.files || []).filter((file) => file.type.startsWith('image/'))
  if (!files.length) return
  event.preventDefault()
  await addImageFiles(files)
}

async function addImageFiles(files: File[], replace = false) {
  const imageFiles = files.filter((file) => file.type.startsWith('image/'))
  if (!imageFiles.length) return
  if (replace) {
    clearUploads()
  }
  const slots = MAX_UPLOAD_IMAGES - uploadedImages.value.length
  if (slots <= 0) {
    toast.warning(`最多上传 ${MAX_UPLOAD_IMAGES} 张参考图`)
    return
  }
  const nextImages = await Promise.all(
    imageFiles.slice(0, slots).map(async (file) => ({
      name: file.name || `pasted-${Date.now()}.png`,
      url: URL.createObjectURL(file),
      dataUrl: await fileToDataUrl(file)
    }))
  )
  uploadedImages.value = [...uploadedImages.value, ...nextImages]
  if (imageFiles.length > slots) {
    toast.warning(`最多上传 ${MAX_UPLOAD_IMAGES} 张参考图`)
  }
}

async function addRecordToUploads(record: ImageRecord) {
  if (!record.generatedImageUrl || record.status !== 'success') return
  if (uploadedImages.value.length >= MAX_UPLOAD_IMAGES) {
    toast.warning(`最多上传 ${MAX_UPLOAD_IMAGES} 张参考图`)
    return
  }
  editingRecordId.value = record.id
  try {
    const response = await fetch(record.generatedImageUrl)
    if (!response.ok) throw new Error('图片读取失败')
    const blob = await response.blob()
    const file = new File([blob], `record-${record.id}.png`, { type: blob.type || 'image/png' })
    await addImageFiles([file])
    toast.success('已添加到参考图')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '图片添加失败')
  } finally {
    if (editingRecordId.value === record.id) {
      editingRecordId.value = null
    }
  }
}

function fileToDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('参考图读取失败'))
    reader.readAsDataURL(file)
  })
}

function clearUploads() {
  uploadedImages.value.forEach((item) => URL.revokeObjectURL(item.url))
  uploadedImages.value = []
}

function selectFilter(value: string) {
  galleryFilter.value = value
  filterOpen.value = false
}

function selectFormat(value: string) {
  imageFormat.value = value
  formatOpen.value = false
}

function openSizeModal() {
  sizeModalOpen.value = true
}

function selectRatio(value: string) {
  selectedRatio.value = value
  customRatioActive.value = false
  sizeMode.value = 'ratio'
}

function applyCustomRatio() {
  const [ratioWidth, ratioHeight] = customRatio.value.split(':').map(Number)
  if (!ratioWidth || !ratioHeight) return
  sizeMode.value = 'ratio'
  customRatio.value = `${ratioWidth}:${ratioHeight}`
  customRatioActive.value = true
}

function applyCustomSize() {
  customWidth.value = clampDimension(customWidth.value)
  customHeight.value = clampDimension(customHeight.value)
  sizeMode.value = 'custom'
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
  clearUploads()
})
</script>

<template>
  <div class="min-h-screen bg-[#fbfbfc] pb-32 text-slate-950 md:pb-56" @paste="handlePaste">
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
            ￥{{ Number(auth.userInfo?.balance || 0).toFixed(6) }}
          </span>
          <button class="rounded-2xl border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 transition hover:bg-slate-50 sm:px-4 sm:text-sm" @click="logout">退出</button>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-7xl px-3 py-4 sm:px-4 md:px-8 md:py-7">
      <section class="fixed inset-x-3 bottom-3 z-40 mx-auto rounded-[24px] border border-slate-200 bg-white/94 p-3 shadow-[0_18px_60px_rgba(15,23,42,0.16)] backdrop-blur-2xl md:inset-x-0 md:bottom-6 md:w-[min(960px,calc(100%-32px))] md:overflow-visible md:rounded-[28px] md:p-4">
        <div v-if="uploadedImages.length" class="mb-3 flex items-center gap-3 overflow-x-auto pb-1">
          <div v-for="(image, index) in uploadedImages" :key="image.url" class="relative h-16 w-16 shrink-0 overflow-hidden rounded-2xl border border-slate-200 bg-slate-100 shadow-sm">
            <img :src="image.url" :alt="image.name" class="h-full w-full object-cover" />
            <span class="absolute bottom-1 left-1 grid h-5 min-w-5 place-items-center rounded-full bg-slate-950/75 px-1 text-[10px] font-black text-white">{{ index + 1 }}</span>
          </div>
          <button class="grid h-16 w-16 shrink-0 place-items-center rounded-2xl border border-dashed border-slate-200 bg-slate-50 text-slate-400 transition hover:border-red-200 hover:bg-red-50 hover:text-red-500" type="button" title="清空上传图片" @click="clearUploads">
            <span class="text-center text-xs font-black">
              <svg class="mx-auto mb-1 h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 7h12M9 7V5h6v2m-7 3v8m4-8v8m4-8v8M8 7l1 13h6l1-13" />
              </svg>
              清空
            </span>
          </button>
        </div>
        <div class="flex items-center gap-3">
          <input
            v-model="prompt"
            class="h-12 min-w-0 flex-1 rounded-2xl border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-sky-300 focus:ring-4 focus:ring-sky-100"
            placeholder="描述你想生成的图像..."
            @keyup.enter="generate"
          />
          <input ref="fileInput" class="hidden" type="file" accept="image/*" multiple @change="handleUpload" />
          <button
            class="grid h-12 w-12 place-items-center rounded-2xl border border-slate-200 bg-slate-100 text-slate-500 transition hover:bg-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="loading"
            title="上传参考图"
            type="button"
            @click="openUpload"
          >
            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m21 8.5-9.6 9.6a5 5 0 0 1-7.1-7.1l9.2-9.2a3.5 3.5 0 1 1 5 5l-9.2 9.2a2 2 0 0 1-2.8-2.8l8.5-8.5" /></svg>
          </button>
          <button class="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-blue-600 text-white shadow-[0_14px_34px_rgba(37,99,235,0.26)] transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60" :disabled="loading || submitLoading || configs.length === 0" @click="generate">
            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 12h14m0 0-5-5m5 5-5 5" /></svg>
          </button>
        </div>
        <div class="mt-2 flex items-center justify-between gap-2 md:hidden">
          <button class="rounded-xl bg-slate-100 px-3 py-1.5 text-xs font-black text-slate-600" type="button" @click="mobilePanelOpen = !mobilePanelOpen">
            {{ mobilePanelOpen ? '收起参数' : '参数' }}
          </button>
          <div class="min-w-0 truncate text-xs font-bold text-slate-500">
            {{ selectedConfig?.name || '选择规格' }} / {{ sizeButtonLabel }} / {{ imageFormat }} / ￥{{ Number(selectedConfig?.price || 0).toFixed(2) }}
          </div>
        </div>
        <div class="mt-3 grid gap-2 text-xs font-bold text-slate-500 md:grid md:grid-cols-[2fr_0.9fr_0.8fr_0.8fr_1fr] md:gap-3" :class="mobilePanelOpen ? 'grid' : 'hidden'">
          <div class="relative">
            <span class="block">生成规格</span>
            <button
              class="mt-1 flex h-10 w-full items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white px-3 text-left font-black text-slate-700 outline-none transition duration-300 hover:border-sky-200 hover:bg-sky-50/40 focus:border-sky-300 focus:ring-4 focus:ring-sky-100"
              type="button"
              @click="specOpen = !specOpen"
            >
              <span class="min-w-0 truncate">
                {{ selectedConfig?.name || '选择规格' }}
                <span class="font-semibold text-slate-400">/ {{ selectedConfig?.quality || '-' }} / ￥{{ Number(selectedConfig?.price || 0).toFixed(2) }}</span>
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
                    <span class="mt-1 block truncate text-xs font-semibold text-slate-400">{{ config.model }} / {{ config.quality }}</span>
                  </span>
                  <span class="shrink-0 rounded-lg bg-slate-100 px-2 py-1 text-xs font-black">￥{{ Number(config.price).toFixed(2) }}</span>
                </button>
              </div>
            </Transition>
          </div>
          <div>
            <span class="block">图像尺寸</span>
            <button
              class="mt-1 flex h-10 w-full items-center justify-between gap-2 rounded-xl border border-slate-200 bg-white px-3 font-black text-slate-700 outline-none transition duration-300 hover:border-sky-200 hover:bg-sky-50/40 focus:border-sky-300 focus:ring-4 focus:ring-sky-100"
              type="button"
              @click="openSizeModal"
            >
              <span class="truncate">{{ sizeButtonLabel }}</span>
              <svg class="h-4 w-4 shrink-0 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7h16M4 12h16M4 17h16" />
              </svg>
            </button>
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
              <span>{{ selectedImageSize }} / 约 {{ estimatedSeconds }}s</span>
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
              <button v-else-if="record.status === 'failed'" class="inline-flex w-fit rounded-lg bg-red-50 px-3 py-1.5 text-xs font-black text-red-600 transition hover:bg-red-100" type="button" @click="errorRecord = record">
                失败原因
              </button>
              <span v-else class="inline-flex rounded-lg bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-500">默认配置</span>
              <div class="mt-4 flex items-center justify-end gap-3 text-slate-400">
                <button class="transition hover:text-sky-600" title="复用提示词" @click="reusePrompt(record)">
                  <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7h11a5 5 0 0 1 0 10H9m0 0 3-3m-3 3 3 3" /></svg>
                </button>
                <button class="transition hover:text-sky-600" title="预览" @click="previewRecord = record">
                  <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" /></svg>
                </button>
                <button v-if="record.status === 'success' && record.generatedImageUrl" class="transition hover:text-emerald-600" title="编辑图片" @click="addRecordToUploads(record)">
                  <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m16.9 3.6 3.5 3.5M4 20h4.2L19.4 8.8a2.5 2.5 0 0 0-3.5-3.5L4.7 16.5 4 20Z" /></svg>
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

    <Transition name="zoom-fade">
      <div v-if="requestLoading" class="fixed inset-0 z-[70] grid place-items-center bg-white/45 backdrop-blur-[2px]">
        <RequestLoader label="" :cell-size="20" />
      </div>
    </Transition>

    <div v-if="errorRecord" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4 py-6 backdrop-blur-sm" @click.self="errorRecord = null">
      <section class="w-full max-w-lg rounded-[24px] bg-white p-5 shadow-[0_24px_80px_rgba(15,23,42,0.22)]">
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="text-xs font-black uppercase tracking-[0.18em] text-red-500">Generation Failed</p>
            <h2 class="mt-2 text-xl font-black text-slate-950">失败原因</h2>
          </div>
          <button class="grid h-9 w-9 place-items-center rounded-xl text-slate-400 transition hover:bg-slate-100 hover:text-slate-700" type="button" @click="errorRecord = null">
            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 6l12 12M18 6 6 18" />
            </svg>
          </button>
        </div>
        <p class="mt-4 max-h-[52vh] overflow-y-auto whitespace-pre-wrap rounded-2xl bg-red-50 p-4 text-sm font-semibold leading-7 text-red-600">{{ imageErrorText(errorRecord) }}</p>
        <button class="mt-5 h-11 w-full rounded-2xl bg-slate-950 text-sm font-black text-white transition hover:bg-sky-600" type="button" @click="errorRecord = null">关闭</button>
      </section>
    </div>

    <div v-if="sizeModalOpen" class="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/35 px-4 py-4 backdrop-blur-sm sm:py-6" @click.self="sizeModalOpen = false">
      <section class="flex max-h-[calc(100vh-32px)] w-full max-w-xl flex-col overflow-hidden rounded-[28px] bg-white text-slate-950 shadow-[0_28px_90px_rgba(15,23,42,0.24)] sm:max-h-[calc(100vh-48px)]">
        <div class="flex shrink-0 items-start justify-between gap-4 px-5 pb-4 pt-5 sm:px-7 sm:pt-7">
          <div>
            <h2 class="text-xl font-black tracking-tight">设置图像尺寸</h2>
            <p class="mt-1 text-sm font-semibold text-slate-400">当前规格：{{ selectedConfig?.name || selectedSizeScale.label }}</p>
          </div>
          <button class="grid h-9 w-9 shrink-0 place-items-center rounded-full text-slate-400 transition hover:bg-slate-100 hover:text-slate-700" type="button" @click="sizeModalOpen = false">
            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 6l12 12M18 6 6 18" />
            </svg>
          </button>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto px-5 pb-4 sm:px-7">
          <div class="grid grid-cols-3 rounded-2xl bg-slate-100 p-1 text-sm font-black text-slate-500">
            <button class="h-11 rounded-xl transition" :class="sizeMode === 'auto' ? 'bg-white text-slate-950 shadow-sm' : 'hover:text-slate-800'" type="button" @click="sizeMode = 'auto'">自动</button>
            <button class="h-11 rounded-xl transition" :class="sizeMode === 'ratio' ? 'bg-white text-slate-950 shadow-sm' : 'hover:text-slate-800'" type="button" @click="sizeMode = 'ratio'">按比例</button>
            <button class="h-11 rounded-xl transition" :class="sizeMode === 'custom' ? 'bg-white text-slate-950 shadow-sm' : 'hover:text-slate-800'" type="button" @click="sizeMode = 'custom'">自定义宽高</button>
          </div>

          <div v-if="sizeMode === 'auto'" class="mt-5 rounded-2xl border border-slate-200 bg-slate-50 p-5">
            <span class="text-sm font-black text-slate-400">自动尺寸</span>
            <p class="mt-2 text-sm font-semibold leading-6 text-slate-500">将由当前模型或服务商根据提示词自动决定输出尺寸。</p>
          </div>

          <div v-if="sizeMode === 'ratio'" class="mt-5">
            <span class="text-sm font-black text-slate-400">图像比例</span>
            <div class="mt-2 grid grid-cols-4 gap-2">
              <button
                v-for="ratio in ratioOptions"
                :key="ratio"
                class="grid h-20 place-items-center rounded-2xl border text-sm font-black transition"
                :class="!customRatioActive && selectedRatio === ratio ? 'border-blue-500 bg-blue-50 text-blue-600' : 'border-slate-200 text-slate-600 hover:border-sky-200 hover:bg-sky-50'"
                type="button"
                @click="selectRatio(ratio)"
              >
                <span class="grid h-8 place-items-center">
                  <span class="block rounded border border-current" :style="ratioIconStyle(ratio)"></span>
                </span>
                <span>{{ ratio }}</span>
              </button>
            </div>

            <div class="mt-4">
              <button
                class="h-12 w-full rounded-2xl border text-sm font-black transition"
                :class="customRatioActive ? 'border-blue-500 bg-blue-50 text-blue-600 hover:bg-blue-100' : 'border-slate-200 bg-white text-slate-600 hover:border-sky-200 hover:bg-sky-50 hover:text-sky-700'"
                type="button"
                @click="applyCustomRatio"
              >
                自定义比例
              </button>
              <label class="mt-4 block">
                <span class="text-sm font-black text-slate-400">输入自定义比例</span>
                <input
                  v-model="customRatio"
                  class="mt-2 h-12 w-full rounded-2xl border border-slate-200 px-4 text-sm font-semibold text-slate-700 outline-none transition focus:border-sky-300 focus:ring-4 focus:ring-sky-100"
                  placeholder="16:9"
                  @blur="applyCustomRatio"
                />
              </label>
            </div>
          </div>

          <div v-if="sizeMode === 'custom'" class="mt-5 grid gap-3 sm:grid-cols-2">
            <label>
              <span class="text-sm font-black text-slate-400">宽度</span>
              <input
                v-model.number="customWidth"
                class="mt-2 h-12 w-full rounded-2xl border border-slate-200 px-4 text-sm font-semibold text-slate-700 outline-none transition focus:border-sky-300 focus:ring-4 focus:ring-sky-100"
                min="256"
                max="8192"
                step="8"
                type="number"
                @blur="applyCustomSize"
              />
            </label>
            <label>
              <span class="text-sm font-black text-slate-400">高度</span>
              <input
                v-model.number="customHeight"
                class="mt-2 h-12 w-full rounded-2xl border border-slate-200 px-4 text-sm font-semibold text-slate-700 outline-none transition focus:border-sky-300 focus:ring-4 focus:ring-sky-100"
                min="256"
                max="8192"
                step="8"
                type="number"
                @blur="applyCustomSize"
              />
            </label>
          </div>

          <div class="mt-5 rounded-2xl bg-slate-50 p-5">
            <span class="text-sm font-black text-slate-400">将使用</span>
            <p class="mt-2 text-2xl font-black tracking-tight">{{ selectedImageSize }}</p>
          </div>
        </div>

        <div class="grid shrink-0 grid-cols-2 gap-3 border-t border-slate-100 bg-white px-5 py-4 sm:px-7">
          <button class="h-12 rounded-2xl bg-slate-100 text-sm font-black text-slate-600 transition hover:bg-slate-200" type="button" @click="sizeModalOpen = false">取消</button>
          <button class="h-12 rounded-2xl bg-blue-600 text-sm font-black text-white shadow-[0_14px_34px_rgba(37,99,235,0.24)] transition hover:bg-blue-700" type="button" @click="sizeModalOpen = false">确定</button>
        </div>
      </section>
    </div>

    <ImagePreviewModal :record="previewRecord || undefined" :src="previewRecord?.generatedImageUrl" @close="previewRecord = null" @delete="remove" />
  </div>
</template>
