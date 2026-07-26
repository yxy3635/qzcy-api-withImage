<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  value: number
  format?: (value: number) => string
  duration?: number
}>(), {
  duration: 700
})

const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
const shown = ref(Number(props.value) || 0)
let raf = 0

function render(value: number) {
  const fmt = props.format
  return fmt ? fmt(value) : Math.round(value).toLocaleString()
}

watch(() => props.value, (next) => {
  const target = Number(next) || 0
  window.cancelAnimationFrame(raf)
  if (reduceMotion || Math.abs(target - shown.value) < 1e-9) {
    shown.value = target
    return
  }
  const from = shown.value
  const start = performance.now()
  const tick = (now: number) => {
    const t = Math.min(1, (now - start) / props.duration)
    const eased = 1 - Math.pow(1 - t, 3)
    shown.value = from + (target - from) * eased
    if (t < 1) raf = window.requestAnimationFrame(tick)
    else shown.value = target
  }
  raf = window.requestAnimationFrame(tick)
}, { immediate: true })

onBeforeUnmount(() => window.cancelAnimationFrame(raf))
</script>

<template>
  <span class="tabular-nums">{{ render(shown) }}</span>
</template>
