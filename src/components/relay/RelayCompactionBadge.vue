<script setup lang="ts">
import { isCompactionRequest } from '@/utils/relayRequestKind'

defineProps<{ endpoint?: string | null; contextCompaction?: boolean | null }>()
</script>

<template>
  <span
    v-if="isCompactionRequest(endpoint, contextCompaction)"
    class="inline-flex shrink-0 items-center rounded-md bg-amber-50 px-1.5 py-0.5 text-[10px] font-black text-amber-700"
    title="上下文压缩或总结请求会改变上下文，可能导致后续缓存命中下降，属于正常现象；费用以本条记录为准"
  >压缩</span>
</template>
