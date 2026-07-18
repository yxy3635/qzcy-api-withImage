<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'

const props = defineProps<{
  open: boolean
  title: string
  subtitle?: string
  eyebrow?: string
  width?: string
}>()

const emit = defineEmits<{
  close: []
}>()

let previousBodyOverflow = ''

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') emit('close')
}

function syncModalState(open: boolean) {
  if (open) {
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onKeydown)
    return
  }

  document.body.style.overflow = previousBodyOverflow
  window.removeEventListener('keydown', onKeydown)
}

watch(() => props.open, syncModalState, { immediate: true })
onBeforeUnmount(() => syncModalState(false))
</script>

<template>
  <Teleport to="body">
    <Transition name="relay-modal">
      <div
        v-if="open"
        class="relay-modal-backdrop"
        role="presentation"
        @mousedown.self="emit('close')"
      >
        <section
          class="relay-modal-dialog"
          :style="{ width: width || 'min(960px, calc(100vw - 32px))' }"
          role="dialog"
          aria-modal="true"
          :aria-label="title"
          @mousedown.stop
        >
          <header class="relay-modal-header">
            <div class="min-w-0">
              <p v-if="eyebrow" class="relay-modal-eyebrow">{{ eyebrow }}</p>
              <h2 class="relay-modal-title">{{ title }}</h2>
              <p v-if="subtitle" class="relay-modal-subtitle">{{ subtitle }}</p>
            </div>
            <button
              class="relay-modal-close"
              type="button"
              aria-label="关闭弹窗"
              title="关闭弹窗"
              @click="emit('close')"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path stroke-linecap="round" d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>
          </header>

          <div class="relay-modal-body">
            <slot />
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.relay-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  padding: 16px;
  background: rgba(15, 23, 42, 0.48);
  backdrop-filter: blur(5px);
}

.relay-modal-dialog {
  display: flex;
  max-height: calc(100vh - 32px);
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.96);
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 28px 90px rgba(15, 23, 42, 0.24);
}

.relay-modal-header {
  display: flex;
  flex: 0 0 auto;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid #e2e8f0;
  padding: 20px 24px;
}

.relay-modal-eyebrow {
  color: #0284c7;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.relay-modal-title {
  margin-top: 3px;
  color: #0f172a;
  font-size: 21px;
  font-weight: 900;
  line-height: 1.25;
}

.relay-modal-subtitle {
  max-width: 720px;
  margin-top: 5px;
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.relay-modal-close {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
  color: #64748b;
  transition: border-color 160ms ease, background-color 160ms ease, color 160ms ease;
}

.relay-modal-close:hover,
.relay-modal-close:focus-visible {
  border-color: #fecaca;
  background: #fef2f2;
  color: #dc2626;
  outline: none;
}

.relay-modal-close svg {
  width: 18px;
  height: 18px;
}

.relay-modal-body {
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.relay-modal-enter-active,
.relay-modal-leave-active {
  transition: opacity 180ms ease;
}

.relay-modal-enter-active .relay-modal-dialog,
.relay-modal-leave-active .relay-modal-dialog {
  transition: transform 180ms ease, opacity 180ms ease;
}

.relay-modal-enter-from,
.relay-modal-leave-to {
  opacity: 0;
}

.relay-modal-enter-from .relay-modal-dialog,
.relay-modal-leave-to .relay-modal-dialog {
  opacity: 0;
  transform: translateY(12px) scale(0.98);
}

@media (max-width: 640px) {
  .relay-modal-backdrop {
    padding: 10px;
  }

  .relay-modal-dialog {
    width: 100% !important;
    max-height: calc(100vh - 20px);
    border-radius: 14px;
  }

  .relay-modal-header {
    padding: 16px;
  }

  .relay-modal-title {
    font-size: 18px;
  }

  .relay-modal-subtitle {
    white-space: normal;
  }
}
</style>
