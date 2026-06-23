<script setup lang="ts">
defineProps<{ balance: number | string }>()
</script>

<template>
  <div class="glass-panel p-5">
    <div class="flex items-start justify-between">
      <div>
        <p class="text-sm font-semibold text-slate-500">当前余额</p>
        <p class="mt-2 text-3xl font-black text-ink">￥{{ Number(balance || 0).toFixed(6) }}</p>
      </div>
      <div class="h-10 w-10 rounded-lg border border-slate-200 bg-slate-50" />
    </div>
  </div>
</template>
