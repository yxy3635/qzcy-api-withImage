<script setup lang="ts">
import { ref } from 'vue'
import type { ImageRecord } from '@/types'

defineProps<{ src?: string; record?: ImageRecord }>()
const emit = defineEmits<{ close: []; delete: [record: ImageRecord] }>()
const zoomed = ref(false)

function downloadImage(src: string) {
  const link = document.createElement('a')
  link.href = src
  link.download = `imageCreater-${Date.now()}.png`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}
</script>

<template>
  <div v-if="src" class="fixed inset-0 z-50 grid place-items-center bg-slate-950/78 p-2 backdrop-blur-sm sm:p-4" @click.self="emit('close')">
    <div class="grid max-h-[94vh] w-full max-w-6xl overflow-y-auto rounded-[22px] border border-white/20 bg-white p-3 shadow-[0_30px_110px_rgba(2,6,23,0.45)] sm:rounded-[28px] sm:p-4 md:grid-cols-[1.12fr_0.88fr] md:overflow-hidden">
      <div class="relative min-h-0 overflow-auto rounded-3xl bg-slate-100 text-left">
        <img :src="src" alt="预览图像" class="mx-auto max-h-[84vh] w-full cursor-zoom-in object-contain transition duration-500 hover:scale-[1.015]" title="点击放大" @click="zoomed = true" />
        <button class="absolute right-3 top-3 grid h-9 w-9 place-items-center rounded-xl bg-slate-950/55 text-white transition hover:bg-slate-950/75" title="下载图片" @click="downloadImage(src)">
          <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v12m0 0 4-4m-4 4-4-4M5 21h14" />
          </svg>
        </button>
      </div>
      <aside class="flex min-h-0 flex-col p-3 sm:p-5">
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="text-xs font-black text-slate-400">输入内容</p>
            <p class="mt-3 text-base font-bold leading-7 text-slate-700">{{ record?.prompt || '图像预览' }}</p>
          </div>
          <button class="grid h-9 w-9 shrink-0 place-items-center rounded-full text-slate-400 transition hover:bg-slate-100 hover:text-slate-700" @click="emit('close')">
            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 6l12 12M18 6 6 18" /></svg>
          </button>
        </div>

        <div class="mt-6">
          <p class="text-xs font-black text-slate-400">参数配置</p>
          <div class="mt-4 grid grid-cols-2 gap-3">
            <div class="rounded-2xl bg-slate-50 p-4">
              <p class="text-xs font-black text-slate-400">状态</p>
              <p class="mt-2 text-sm font-black text-slate-800">{{ record?.status || '-' }}</p>
            </div>
            <div class="rounded-2xl bg-slate-50 p-4">
              <p class="text-xs font-black text-slate-400">费用</p>
              <p class="mt-2 text-sm font-black text-slate-800">￥{{ Number(record?.cost || 0).toFixed(2) }}</p>
            </div>
            <div class="rounded-2xl bg-slate-50 p-4">
              <p class="text-xs font-black text-slate-400">格式</p>
              <p class="mt-2 text-sm font-black text-slate-800">png</p>
            </div>
            <div class="rounded-2xl bg-slate-50 p-4">
              <p class="text-xs font-black text-slate-400">数量</p>
              <p class="mt-2 text-sm font-black text-slate-800">1</p>
            </div>
          </div>
          <p class="mt-5 text-xs font-semibold text-slate-400">创建于 {{ record?.createdAt || '-' }}</p>
        </div>

        <div class="mt-5 flex flex-wrap items-center gap-3 border-t border-slate-100 pt-5 md:mt-auto">
          <button class="flex-1 rounded-2xl bg-blue-50 px-4 py-3 text-sm font-black text-blue-600 transition hover:bg-blue-100 sm:flex-none" @click="downloadImage(src)">下载图片</button>
          <button v-if="record" class="flex-1 rounded-2xl bg-red-50 px-4 py-3 text-sm font-black text-red-600 transition hover:bg-red-100 sm:flex-none" @click="emit('delete', record)">删除任务</button>
          <button class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm font-black text-slate-600 transition hover:bg-slate-50 sm:ml-auto sm:w-auto" @click="emit('close')">关闭</button>
        </div>
      </aside>
    </div>

    <Transition name="zoom-fade">
      <div v-if="zoomed" class="fixed inset-0 z-[60] grid place-items-center bg-slate-950/92 p-5 backdrop-blur-md" @click.self="zoomed = false">
        <img :src="src" alt="放大预览图像" class="max-h-[94vh] max-w-[96vw] rounded-2xl object-contain shadow-[0_30px_120px_rgba(0,0,0,0.55)]" @click="zoomed = false" />
        <div class="absolute right-5 top-5 flex gap-2">
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
</template>
