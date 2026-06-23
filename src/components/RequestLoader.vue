<script setup lang="ts">
withDefaults(defineProps<{
  label?: string
  cellSize?: number
}>(), {
  label: '加载中',
  cellSize: 14
})
</script>

<template>
  <div class="request-loader-wrap">
    <div
      class="request-loader"
      :style="{ '--cell-size': `${cellSize}px` }"
      aria-hidden="true"
    >
      <div class="request-loader-cell d-0"></div>
      <div class="request-loader-cell d-1"></div>
      <div class="request-loader-cell d-2"></div>
      <div class="request-loader-cell d-1"></div>
      <div class="request-loader-cell d-2"></div>
      <div class="request-loader-cell d-2"></div>
      <div class="request-loader-cell d-3"></div>
      <div class="request-loader-cell d-3"></div>
      <div class="request-loader-cell d-4"></div>
    </div>
    <p v-if="label" class="request-loader-label">{{ label }}</p>
  </div>
</template>

<style scoped>
.request-loader-wrap {
  display: grid;
  justify-items: center;
  gap: 14px;
}

.request-loader {
  --cell-size: 14px;
  --cell-spacing: 1px;
  --cells: 3;
  --total-size: calc(var(--cells) * (var(--cell-size) + 2 * var(--cell-spacing)));
  display: flex;
  flex-wrap: wrap;
  width: var(--total-size);
  height: var(--total-size);
}

.request-loader-cell {
  flex: 0 0 var(--cell-size);
  margin: var(--cell-spacing);
  background-color: transparent;
  box-sizing: border-box;
  border-radius: 4px;
  animation: 1.5s request-loader-ripple ease infinite;
}

.request-loader-cell.d-1 {
  animation-delay: 100ms;
}

.request-loader-cell.d-2 {
  animation-delay: 200ms;
}

.request-loader-cell.d-3 {
  animation-delay: 300ms;
}

.request-loader-cell.d-4 {
  animation-delay: 400ms;
}

.request-loader-cell:nth-child(1) {
  --cell-color: #00FF87;
}

.request-loader-cell:nth-child(2) {
  --cell-color: #0CFD95;
}

.request-loader-cell:nth-child(3) {
  --cell-color: #17FBA2;
}

.request-loader-cell:nth-child(4) {
  --cell-color: #23F9B2;
}

.request-loader-cell:nth-child(5) {
  --cell-color: #30F7C3;
}

.request-loader-cell:nth-child(6) {
  --cell-color: #3DF5D4;
}

.request-loader-cell:nth-child(7) {
  --cell-color: #45F4DE;
}

.request-loader-cell:nth-child(8) {
  --cell-color: #53F1F0;
}

.request-loader-cell:nth-child(9) {
  --cell-color: #60EFFF;
}

.request-loader-label {
  font-size: 13px;
  font-weight: 800;
  color: #475569;
}

@keyframes request-loader-ripple {
  0% {
    background-color: transparent;
  }

  30% {
    background-color: var(--cell-color);
  }

  60% {
    background-color: transparent;
  }

  100% {
    background-color: transparent;
  }
}
</style>
