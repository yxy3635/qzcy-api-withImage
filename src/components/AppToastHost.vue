<script setup lang="ts">
import { computed } from 'vue'
import { useToast, type ToastType } from '@/composables/useToast'

const toast = useToast()

const toneClass: Record<ToastType, string> = {
  success: 'border-emerald-100 bg-emerald-50 text-emerald-700 ring-emerald-100',
  error: 'border-rose-100 bg-rose-50 text-rose-700 ring-rose-100',
  warning: 'border-amber-100 bg-amber-50 text-amber-700 ring-amber-100',
  info: 'border-sky-100 bg-sky-50 text-sky-700 ring-sky-100'
}

const accentClass: Record<ToastType, string> = {
  success: 'from-emerald-400 to-teal-500',
  error: 'from-rose-400 to-red-500',
  warning: 'from-amber-400 to-orange-500',
  info: 'from-sky-400 to-blue-500'
}

const iconPath: Record<ToastType, string> = {
  success: 'M5 13l4 4L19 7',
  error: 'M6 6l12 12M18 6 6 18',
  warning: 'M12 9v4m0 4h.01M10.3 4.8 2.6 18.2A2 2 0 0 0 4.3 21h15.4a2 2 0 0 0 1.7-2.8L13.7 4.8a2 2 0 0 0-3.4 0Z',
  info: 'M12 8h.01M11 12h1v5h1M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z'
}

const visibleToasts = computed(() => toast.toasts.value.slice(0, 4))
</script>

<template>
  <Teleport to="body">
    <div class="pointer-events-none fixed inset-x-0 top-4 z-[100] flex flex-col items-center gap-3 px-3 sm:inset-x-auto sm:right-5 sm:top-5 sm:items-end sm:px-0">
      <TransitionGroup name="toast-stack">
        <article
          v-for="item in visibleToasts"
          :key="item.id"
          class="pointer-events-auto relative flex w-full max-w-[calc(100vw-24px)] items-start gap-3 overflow-hidden rounded-2xl border border-white/70 bg-white/92 p-3 pr-10 text-slate-800 shadow-[0_22px_70px_rgba(15,23,42,0.16)] ring-1 ring-slate-900/5 backdrop-blur-2xl sm:w-[380px]"
          role="status"
        >
          <div class="absolute inset-y-0 left-0 w-1 bg-gradient-to-b" :class="accentClass[item.type]"></div>
          <div class="grid h-10 w-10 shrink-0 place-items-center rounded-xl border ring-4" :class="toneClass[item.type]">
            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2" :d="iconPath[item.type]" />
            </svg>
          </div>
          <div class="min-w-0 flex-1 pt-0.5">
            <p class="break-words text-sm font-black leading-5 text-slate-950">{{ item.title }}</p>
            <p v-if="item.message" class="mt-1 break-words text-xs font-semibold leading-5 text-slate-500">{{ item.message }}</p>
          </div>
          <button
            class="absolute right-2 top-2 grid h-8 w-8 place-items-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
            type="button"
            aria-label="关闭提示"
            @click="toast.dismiss(item.id)"
          >
            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 6l12 12M18 6 6 18" />
            </svg>
          </button>
        </article>
      </TransitionGroup>
    </div>
  </Teleport>
</template>
