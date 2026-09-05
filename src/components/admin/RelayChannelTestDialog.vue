<script setup lang="ts">
import { ref, watch } from 'vue'
import RelayModal from '@/components/RelayModal.vue'
import { adminApi } from '@/api/adminApi'
import { useToast } from '@/composables/useToast'
import type { RelayChannelTestResult } from '@/types'

interface TestModelOption {
  modelId: number
  model: string
  displayName: string
  upstreamModel: string
}

const props = withDefaults(defineProps<{
  open: boolean
  channelId: number | null
  channelName: string
  strategy?: string
  models: TestModelOption[]
}>(), {
  channelId: null,
  strategy: 'weighted_random'
})

const emit = defineEmits<{ close: []; tested: [] }>()

const toast = useToast()
const DEFAULT_PROMPT = '你好，请用一句话介绍你自己。'

const strategyLabels: Record<string, string> = {
  weighted_random: '加权随机',
  smooth_rr: '平滑加权轮询',
  least_conn: '最小并发',
  priority: '严格优先级'
}

const selectedModelId = ref<number | null>(null)
const prompt = ref(DEFAULT_PROMPT)
const loading = ref(false)
const result = ref<RelayChannelTestResult | null>(null)

watch(() => props.open, (open) => {
  if (!open) return
  result.value = null
  prompt.value = DEFAULT_PROMPT
  selectedModelId.value = props.models[0]?.modelId ?? null
})

watch(() => props.models, () => {
  if (selectedModelId.value == null || !props.models.some((item) => item.modelId === selectedModelId.value)) {
    selectedModelId.value = props.models[0]?.modelId ?? null
  }
})

async function submit() {
  if (props.channelId == null || selectedModelId.value == null) return
  loading.value = true
  try {
    const { data } = await adminApi.testRelayChannelChat(props.channelId, {
      modelId: selectedModelId.value,
      prompt: prompt.value
    })
    result.value = data.data
    if (result.value?.success) {
      toast.success('渠道测试成功')
      emit('tested')
    } else {
      toast.error(result.value?.error || '渠道测试失败')
    }
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '测试请求失败')
  } finally {
    loading.value = false
  }
}

function formatMs(value?: number | null) {
  const ms = Number(value || 0)
  if (ms <= 0) return '—'
  if (ms >= 1000) return `${(ms / 1000).toFixed(1)}s`
  return `${Math.round(ms)}ms`
}

function modelLabel(item: TestModelOption) {
  const name = item.displayName && item.displayName !== item.model ? `${item.displayName}（${item.model}）` : item.model
  return item.upstreamModel && item.upstreamModel !== item.model ? `${name} → ${item.upstreamModel}` : name
}
</script>

<template>
  <RelayModal
    :open="open"
    eyebrow="Channel test"
    :title="`测试渠道 · ${channelName}`"
    :subtitle="`将按「${strategyLabels[strategy] || strategy}」策略选择供应商，发起一次真实对话请求，失败自动切换下一个`"
    @close="emit('close')"
  >
    <div class="p-5">
      <div class="space-y-4">
        <label class="block">
          <span class="text-xs font-black text-slate-600">选择模型</span>
          <select v-model="selectedModelId" class="input mt-1 h-10 rounded-lg text-sm" :disabled="!models.length">
            <option v-for="item in models" :key="item.modelId" :value="item.modelId">{{ modelLabel(item) }}</option>
          </select>
          <span v-if="!models.length" class="mt-1 block text-[11px] font-black text-red-500">该渠道没有已启用的模型绑定，请先在渠道编辑中绑定。</span>
        </label>
        <label class="block">
          <span class="text-xs font-black text-slate-600">测试内容</span>
          <textarea v-model="prompt" class="input mt-1 min-h-20 rounded-lg py-2 text-sm" rows="3" :disabled="loading"></textarea>
        </label>
        <button
          class="btn-primary h-11 w-full rounded-lg"
          type="button"
          :disabled="loading || selectedModelId == null || !models.length"
          @click="submit"
        >{{ loading ? '请求中…（最长约 1 分钟）' : '发送测试' }}</button>

        <div v-if="result && result.success" class="rounded-xl border border-emerald-200 bg-emerald-50/60 p-3">
          <p class="text-sm font-black text-emerald-700">✅ 调用成功 · {{ formatMs(result.latencyMs) }}</p>
          <p class="mt-1 text-xs font-bold text-slate-600">
            供应商：{{ result.providerName }}
            <span class="ml-1 rounded bg-white px-1.5 py-0.5 text-[10px] font-black text-slate-500 ring-1 ring-slate-200">{{ result.rule === 'anthropic' ? 'Anthropic' : 'OpenAI' }} 规则</span>
          </p>
          <p class="mt-2 text-xs font-bold text-slate-500">实际调用模型</p>
          <p class="truncate font-mono text-base font-black text-sky-700">{{ result.upstreamModel || result.model }}</p>
          <p class="mt-2 whitespace-pre-wrap rounded-lg bg-white p-2.5 text-sm font-semibold text-slate-800 ring-1 ring-emerald-100">{{ result.content }}</p>
        </div>

        <div v-else-if="result" class="rounded-xl border border-red-200 bg-red-50/60 p-3">
          <p class="text-sm font-black text-red-600">❌ {{ result.error || '调用失败' }}</p>
          <div v-if="result.attempts?.length" class="mt-2 space-y-1.5">
            <p class="text-[11px] font-black uppercase tracking-wide text-slate-400">各供应商尝试明细</p>
            <div
              v-for="(attempt, index) in result.attempts"
              :key="index"
              class="rounded-lg bg-white px-2.5 py-1.5 text-xs"
            >
              <span class="font-black text-slate-700">{{ attempt.providerName }}</span>
              <span class="ml-2 font-semibold text-red-500">{{ attempt.error }}</span>
              <span class="ml-2 text-[10px] font-bold text-slate-400">{{ formatMs(attempt.latencyMs) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </RelayModal>
</template>
