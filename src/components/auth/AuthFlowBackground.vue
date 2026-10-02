<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{ paused: boolean }>()
const canvas = ref<HTMLCanvasElement | null>(null)
let context: CanvasRenderingContext2D | null = null
let observer: ResizeObserver | undefined
let frame = 0
let lastTime = 0
let elapsed = 0
let width = 1
let height = 1
let pointerX = 0
let pointerY = 0
let driftX = 0
let driftY = 0

function draw() {
  if (!context) return
  context.clearRect(0, 0, width, height)
  const time = elapsed * .00015
  const centerX = width * (.49 + Math.sin(time * .7) * .035) + driftX
  const centerY = height * (.53 + Math.cos(time * .8) * .055) + driftY
  const radiusX = Math.max(width * .43, height * .6)
  const radiusY = Math.max(height * .3, width * .14)
  context.save()
  context.translate(centerX, centerY)
  context.rotate(-.19 + Math.sin(time * .55) * .14)
  for (let line = 0; line < 34; line++) {
    const spread = line / 33
    context.beginPath()
    for (let step = 0; step <= 128; step++) {
      const angle = step / 128 * Math.PI * 2
      const x = radiusX * (.66 + spread * .4) * Math.cos(angle)
      const y = radiusY * (.65 + spread * .52) * Math.sin(angle)
        + radiusY * .57 * Math.sin(angle * 2 + spread * 1.05 + time)
      if (step === 0) context.moveTo(x, y)
      else context.lineTo(x, y)
    }
    context.closePath()
    context.strokeStyle = `rgba(${45 + Math.round(spread * 25)}, ${116 + Math.round(spread * 35)}, 220, ${.19 + spread * .2})`
    context.lineWidth = 1 + spread * .35
    context.stroke()
  }
  context.restore()
}

function tick(now: number) {
  // Cap drawing at 30 fps; the form stays independent of the animation loop.
  if (!lastTime || now - lastTime >= 1000 / 30) {
    elapsed += lastTime ? Math.min(now - lastTime, 80) : 0
    lastTime = now
    driftX += (pointerX - driftX) * .035
    driftY += (pointerY - driftY) * .035
    draw()
  }
  frame = requestAnimationFrame(tick)
}

function syncMotion() {
  cancelAnimationFrame(frame)
  lastTime = 0
  if (!props.paused && !document.hidden) frame = requestAnimationFrame(tick)
  else draw()
}

function resize() {
  if (!canvas.value || !context) return
  width = canvas.value.clientWidth
  height = canvas.value.clientHeight
  const ratio = Math.min(window.devicePixelRatio || 1, 1.5)
  canvas.value.width = Math.round(width * ratio)
  canvas.value.height = Math.round(height * ratio)
  context.setTransform(ratio, 0, 0, ratio, 0, 0)
  draw()
}

function move(event: PointerEvent) {
  if (event.pointerType !== 'mouse') return
  pointerX = (event.clientX / window.innerWidth - .5) * 42
  pointerY = (event.clientY / window.innerHeight - .5) * 30
}
function resetPointer() { pointerX = 0; pointerY = 0 }

watch(() => props.paused, syncMotion)
onMounted(() => {
  context = canvas.value?.getContext('2d') || null
  observer = new ResizeObserver(resize)
  if (canvas.value) observer.observe(canvas.value)
  window.addEventListener('pointermove', move, { passive: true })
  window.addEventListener('blur', resetPointer)
  document.addEventListener('visibilitychange', syncMotion)
  resize()
  syncMotion()
})
onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  observer?.disconnect()
  window.removeEventListener('pointermove', move)
  window.removeEventListener('blur', resetPointer)
  document.removeEventListener('visibilitychange', syncMotion)
})
</script>

<template>
  <div class="flow-background" aria-hidden="true">
    <div class="flow-light flow-light-blue"></div>
    <div class="flow-light flow-light-cyan"></div>
    <canvas ref="canvas"></canvas>
  </div>
</template>

<style scoped>
.flow-background { position: fixed; inset: 0; overflow: hidden; pointer-events: none; z-index: -1; background: #eaf2fa; }
.flow-background canvas { position: absolute; inset: 0; width: 100%; height: 100%; }
.flow-light { position: absolute; width: 85vw; height: 90vh; border-radius: 50%; filter: blur(70px); opacity: .8; }
.flow-light-blue { left: -20%; top: -25%; background: radial-gradient(ellipse, #bbd5f3, #dceafa 48%, transparent 72%); }
.flow-light-cyan { right: -25%; bottom: -40%; background: radial-gradient(ellipse, #b4e1e7, #e1eff5 48%, transparent 72%); }
</style>
