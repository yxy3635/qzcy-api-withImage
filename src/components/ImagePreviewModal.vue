<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ImageRecord } from '@/types'

defineProps<{ src?: string; record?: ImageRecord }>()
const emit = defineEmits<{ close: []; delete: [record: ImageRecord] }>()
const zoomed = ref(false)
const zoomScale = ref(1)

watch(zoomed, (value) => {
  if (!value) {
    zoomScale.value = 1
  }
})

function downloadImage(src: string) {
  const link = document.createElement('a')
  link.href = src
  link.download = `imageCreater-${Date.now()}.png`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

function errorText(record?: ImageRecord) {
  if (!record || record.status !== 'failed') return ''
  const message = extractErrorMessage(record.errorMessage)
  const details = [
    record.errorStatusCode ? `状态码 ${record.errorStatusCode}` : '',
    record.errorType ? `类型 ${record.errorType}` : ''
  ].filter(Boolean).join('，')
  if (message && details) return `${message}（${details}）`
  return message || details || ''
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

function handleZoomWheel(event: WheelEvent) {
  event.preventDefault()
  const next = zoomScale.value + (event.deltaY < 0 ? 0.15 : -0.15)
  zoomScale.value = Math.min(5, Math.max(0.5, Number(next.toFixed(2))))
}
</script>

<template>
  <Teleport to="body">
    <div v-if="src" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/82 p-3 backdrop-blur-sm sm:p-4" @click.self="emit('close')">
      <div class="flex h-[calc(100dvh-24px)] w-full max-w-6xl flex-col overflow-hidden rounded-[22px] border border-white/20 bg-white shadow-[0_30px_110px_rgba(2,6,23,0.45)] sm:h-auto sm:max-h-[94vh] sm:rounded-[28px] md:grid md:grid-cols-[1.12fr_0.88fr]">
        <div class="relative flex min-h-0 flex-1 items-center justify-center bg-slate-100 p-2 sm:p-3 md:min-h-[520px]">
          <img :src="src" alt="预览图像" class="max-h-full max-w-full cursor-zoom-in rounded-2xl object-contain shadow-sm transition duration-500 hover:scale-[1.01]" title="点击放大" @click="zoomed = true" />
          <div class="absolute right-3 top-3 flex gap-2">
            <button class="grid h-10 w-10 place-items-center rounded-2xl bg-slate-950/55 text-white backdrop-blur transition hover:bg-slate-950/75" title="下载图片" @click="downloadImage(src)">
              <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v12m0 0 4-4m-4 4-4-4M5 21h14" />
              </svg>
            </button>
            <button class="grid h-10 w-10 place-items-center rounded-2xl bg-slate-950/55 text-white backdrop-blur transition hover:bg-slate-950/75 md:hidden" title="关闭预览" @click="emit('close')">
              <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 6l12 12M18 6 6 18" />
              </svg>
            </button>
          </div>
        </div>
        <aside class="flex max-h-[42dvh] min-h-0 flex-col overflow-y-auto border-t border-slate-100 p-4 sm:p-5 md:max-h-none md:border-l md:border-t-0">
          <div class="flex items-start justify-between gap-4">
            <div class="min-w-0">
              <p class="text-xs font-black text-slate-400">输入内容</p>
              <p class="mt-2 line-clamp-3 text-sm font-bold leading-6 text-slate-700 sm:text-base sm:leading-7 md:line-clamp-none">{{ record?.prompt || '图像预览' }}</p>
            </div>
            <button class="hidden h-9 w-9 shrink-0 place-items-center rounded-full text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 md:grid" @click="emit('close')">
              <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 6l12 12M18 6 6 18" /></svg>
            </button>
          </div>

          <div class="mt-4 sm:mt-6">
            <p class="text-xs font-black text-slate-400">参数配置</p>
            <div class="mt-3 grid grid-cols-2 gap-2 sm:mt-4 sm:gap-3">
              <div class="rounded-2xl bg-slate-50 p-3 sm:p-4">
                <p class="text-xs font-black text-slate-400">状态</p>
                <p class="mt-2 text-sm font-black text-slate-800">{{ record?.status || '-' }}</p>
              </div>
              <div class="rounded-2xl bg-slate-50 p-3 sm:p-4">
                <p class="text-xs font-black text-slate-400">费用</p>
                <p class="mt-2 text-sm font-black text-slate-800">￥{{ Number(record?.cost || 0).toFixed(2) }}</p>
              </div>
              <div class="rounded-2xl bg-slate-50 p-3 sm:p-4">
                <p class="text-xs font-black text-slate-400">格式</p>
                <p class="mt-2 text-sm font-black text-slate-800">png</p>
              </div>
              <div class="rounded-2xl bg-slate-50 p-3 sm:p-4">
                <p class="text-xs font-black text-slate-400">数量</p>
                <p class="mt-2 text-sm font-black text-slate-800">1</p>
              </div>
            </div>
            <div v-if="errorText(record)" class="mt-4 rounded-2xl bg-red-50 p-4">
              <p class="text-xs font-black text-red-400">失败原因</p>
              <p class="mt-2 whitespace-pre-wrap text-sm font-semibold leading-6 text-red-600">{{ errorText(record) }}</p>
            </div>
            <p class="mt-3 text-xs font-semibold text-slate-400 sm:mt-5">创建于 {{ record?.createdAt || '-' }}</p>
          </div>

          <div class="mt-4 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-4 sm:gap-3 md:mt-auto md:pt-5">
            <button class="flex-1 rounded-2xl bg-blue-50 px-4 py-3 text-sm font-black text-blue-600 transition hover:bg-blue-100 sm:flex-none" @click="downloadImage(src)">下载图片</button>
            <button v-if="record" class="flex-1 rounded-2xl bg-red-50 px-4 py-3 text-sm font-black text-red-600 transition hover:bg-red-100 sm:flex-none" @click="emit('delete', record)">删除任务</button>
            <button class="hidden rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm font-black text-slate-600 transition hover:bg-slate-50 sm:ml-auto md:block" @click="emit('close')">关闭</button>
          </div>
        </aside>
      </div>

      <Transition name="zoom-fade">
        <div v-if="zoomed" class="fixed inset-0 z-[60] flex items-center justify-center overflow-auto bg-slate-950/94 p-3 backdrop-blur-md sm:p-5" @click.self="zoomed = false" @wheel="handleZoomWheel">
          <img
            :src="src"
            alt="放大预览图像"
            class="max-h-[calc(100dvh-24px)] max-w-full rounded-2xl object-contain shadow-[0_30px_120px_rgba(0,0,0,0.55)] transition-transform duration-150 sm:max-h-[94vh] sm:max-w-[96vw]"
            :style="{ transform: `scale(${zoomScale})` }"
            @dblclick="zoomScale = 1"
          />
          <div class="absolute bottom-3 left-1/2 -translate-x-1/2 rounded-full bg-white/10 px-3 py-1.5 text-xs font-black text-white backdrop-blur sm:bottom-5">
            {{ Math.round(zoomScale * 100) }}%
          </div>
          <div class="absolute right-3 top-3 flex gap-2 sm:right-5 sm:top-5">
            <button class="grid h-11 w-11 place-items-center rounded-2xl bg-white/10 text-white backdrop-blur transition hover:bg-white/20" title="下载图片" @click.stop="downloadImage(src)">
              <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v12m0 0 4-4m-4 4-4-4M5 21h14" />
              </svg>
            </button>
            <button class="grid h-11 w-11 place-items-center rounded-2xl bg-white/10 text-white backdrop-blur transition hover:bg-white/20" title="关闭放大" @click.stop="zoomed = false">
              <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 6l12 12M18 6 6 18" />
              </svg>
            </button>
          </div>
        </div>
      </Transition>
    </div>
  </Teleport>
</template>
