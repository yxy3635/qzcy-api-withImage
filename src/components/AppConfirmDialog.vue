<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  open: boolean
  title: string
  description: string
  confirmLabel?: string
  cancelLabel?: string
  subject?: string
  tone?: 'danger' | 'warning' | 'success'
  loading?: boolean
}>(), {
  confirmLabel: '确认',
  cancelLabel: '取消',
  subject: '',
  tone: 'warning',
  loading: false
})

const emit = defineEmits<{
  cancel: []
  confirm: []
}>()

const dialogRef = ref<HTMLElement | null>(null)

const toneClasses = {
  danger: {
    icon: 'bg-rose-50 text-rose-600 ring-rose-100',
    button: 'bg-rose-600 shadow-rose-200 hover:bg-rose-700 focus-visible:ring-rose-500'
  },
  warning: {
    icon: 'bg-amber-50 text-amber-600 ring-amber-100',
    button: 'bg-amber-500 shadow-amber-200 hover:bg-amber-600 focus-visible:ring-amber-500'
  },
  success: {
    icon: 'bg-emerald-50 text-emerald-600 ring-emerald-100',
    button: 'bg-emerald-600 shadow-emerald-200 hover:bg-emerald-700 focus-visible:ring-emerald-500'
  }
} as const

function cancel() {
  if (!props.loading) emit('cancel')
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) cancel()
}

watch(() => props.open, async (open) => {
  if (!open) return
  await nextTick()
  dialogRef.value?.focus()
})

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <Teleport to="body">
    <Transition name="app-confirm">
      <div v-if="open" class="fixed inset-0 z-[120] grid place-items-center overflow-y-auto bg-slate-950/45 p-4 backdrop-blur-[5px]" @click.self="cancel">
        <section
          ref="dialogRef"
          class="app-confirm-dialog w-full max-w-md overflow-hidden rounded-[26px] border border-white/80 bg-white shadow-[0_32px_110px_rgba(15,23,42,0.30)] outline-none"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="app-confirm-title"
          tabindex="-1"
        >
          <div class="p-6 sm:p-7">
            <div class="flex items-start gap-4">
              <div class="grid h-12 w-12 shrink-0 place-items-center rounded-2xl ring-1" :class="toneClasses[tone].icon">
                <svg v-if="tone === 'danger'" viewBox="0 0 24 24" class="h-6 w-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3m-8 0 1 14h8l1-14M10 11v6m4-6v6" /></svg>
                <svg v-else-if="tone === 'success'" viewBox="0 0 24 24" class="h-6 w-6" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5" /></svg>
                <svg v-else viewBox="0 0 24 24" class="h-6 w-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3 2.7 20h18.6L12 3zm0 6v5m0 3h.01" /></svg>
              </div>
              <div class="min-w-0 flex-1">
                <h2 id="app-confirm-title" class="text-xl font-black tracking-tight text-slate-950">{{ title }}</h2>
                <p class="mt-2 text-sm font-semibold leading-6 text-slate-500">{{ description }}</p>
              </div>
            </div>

            <div v-if="subject" class="mt-5 flex items-center gap-3 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
              <span class="grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-white text-emerald-600 shadow-sm">
                <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 7a4 4 0 1 1-2.4 3.67L4 19.27V21h1.73l1-1H9v-2h2v-2h2l2.33-2.33A4 4 0 0 1 15 7z" /></svg>
              </span>
              <p class="min-w-0 truncate font-mono text-xs font-black text-slate-700">{{ subject }}</p>
            </div>
          </div>

          <div class="flex flex-col-reverse gap-2 border-t border-slate-100 bg-slate-50/80 px-6 py-4 sm:flex-row sm:justify-end">
            <button type="button" class="h-11 rounded-xl border border-slate-200 bg-white px-5 text-sm font-black text-slate-600 transition hover:border-slate-300 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-400 disabled:cursor-not-allowed disabled:opacity-50" :disabled="loading" @click="cancel">{{ cancelLabel }}</button>
            <button type="button" class="inline-flex h-11 items-center justify-center gap-2 rounded-xl px-5 text-sm font-black text-white shadow-lg transition hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0" :class="toneClasses[tone].button" :disabled="loading" @click="emit('confirm')">
              <svg v-if="loading" viewBox="0 0 24 24" class="h-4 w-4 animate-spin" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M20 12a8 8 0 1 1-3-6.25" /></svg>
              {{ loading ? '处理中…' : confirmLabel }}
            </button>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.app-confirm-enter-active,
.app-confirm-leave-active {
  transition: opacity 0.22s ease;
}

.app-confirm-enter-active .app-confirm-dialog {
  animation: confirmDialogIn 0.34s cubic-bezier(0.2, 0.85, 0.28, 1.16) both;
}

.app-confirm-leave-active .app-confirm-dialog {
  animation: confirmDialogOut 0.18s ease both;
}

.app-confirm-enter-from,
.app-confirm-leave-to {
  opacity: 0;
}

@keyframes confirmDialogIn {
  from { opacity: 0; transform: translateY(18px) scale(0.94); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

@keyframes confirmDialogOut {
  to { opacity: 0; transform: translateY(10px) scale(0.97); }
}

@media (prefers-reduced-motion: reduce) {
  .app-confirm-enter-active .app-confirm-dialog,
  .app-confirm-leave-active .app-confirm-dialog {
    animation-duration: 0.01ms;
  }
}
</style>
