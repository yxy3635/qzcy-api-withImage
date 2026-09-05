<script setup lang="ts">
import { onBeforeUnmount } from 'vue'

defineProps<{
  eyebrow?: string
  title: string
  subtitle?: string
}>()

const emit = defineEmits<{ close: [] }>()

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') emit('close')
}

function startListening() {
  document.addEventListener('keydown', onKeydown)
  document.body.style.overflow = 'hidden'
}

function stopListening() {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
}

// 组件由父级 v-if 控制挂载：挂载即打开，卸载即关闭。
startListening()
onBeforeUnmount(stopListening)
</script>

<template>
  <Teleport to="body">
    <div class="fixed inset-0 z-[70] flex justify-end">
      <div class="relay-drawer-mask absolute inset-0 bg-slate-950/40 backdrop-blur-[2px]" @click="emit('close')"></div>
      <aside class="relay-drawer-panel relative flex h-full w-full max-w-[560px] flex-col bg-white shadow-2xl">
        <header class="flex items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
          <div class="min-w-0">
            <p v-if="eyebrow" class="text-[10px] font-black uppercase tracking-[0.18em] text-sky-600">{{ eyebrow }}</p>
            <h2 class="mt-0.5 truncate text-lg font-black text-slate-950">{{ title }}</h2>
            <p v-if="subtitle" class="mt-0.5 truncate text-xs font-semibold text-slate-500">{{ subtitle }}</p>
          </div>
          <button
            class="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
            type="button"
            aria-label="关闭"
            @click="emit('close')"
          >
            <svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12" /></svg>
          </button>
        </header>
        <div class="min-h-0 flex-1 overflow-y-auto px-5 py-4">
          <slot />
        </div>
        <footer v-if="$slots.footer" class="border-t border-slate-100 bg-white px-5 py-3">
          <slot name="footer" />
        </footer>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.relay-drawer-mask {
  animation: relay-drawer-fade 0.18s ease both;
}
.relay-drawer-panel {
  animation: relay-drawer-slide 0.24s cubic-bezier(0.16, 1, 0.3, 1) both;
}
@keyframes relay-drawer-fade {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes relay-drawer-slide {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}
@media (prefers-reduced-motion: reduce) {
  .relay-drawer-mask, .relay-drawer-panel { animation: none; }
}
</style>
