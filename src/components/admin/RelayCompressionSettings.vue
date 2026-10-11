<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '@/api/adminApi'
import { useToast } from '@/composables/useToast'
import type { RelayCompressionConfig, RelayCompressionStatus } from '@/types'

const toast = useToast()
const current = ref<RelayCompressionStatus | null>(null)
const draft = ref<RelayCompressionConfig>({ gzipEnabled: true, zstdEnabled: true, memoryBudgetMb: 128 })
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const valid = computed(() => Number.isInteger(draft.value.memoryBudgetMb) && draft.value.memoryBudgetMb >= 32 && draft.value.memoryBudgetMb <= 1024)
const dirty = computed(() => current.value !== null && (
  draft.value.gzipEnabled !== current.value.gzipEnabled || draft.value.zstdEnabled !== current.value.zstdEnabled || draft.value.memoryBudgetMb !== current.value.memoryBudgetMb
))
const slots = computed(() => valid.value ? Math.floor(draft.value.memoryBudgetMb / (current.value?.requestBudgetMb || 32)) : 0)

function apply(status: RelayCompressionStatus) {
  current.value = status
  draft.value = { gzipEnabled: status.gzipEnabled, zstdEnabled: status.zstdEnabled, memoryBudgetMb: status.memoryBudgetMb }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    apply((await adminApi.relayCompressionConfig()).data.data)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载压缩设置失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!current.value || !valid.value || saving.value) return
  saving.value = true
  error.value = ''
  try {
    apply((await adminApi.updateRelayCompressionConfig({ ...draft.value })).data.data)
    toast.success('请求压缩设置已保存并生效')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存压缩设置失败'
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="space-y-4">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-black text-slate-950">请求压缩</h2>
        <p class="mt-1 text-sm text-slate-500">支持 Chat Completions、Responses、Anthropic Messages，保存后立即生效。</p>
      </div>
      <button type="button" :disabled="loading || saving" class="rounded-lg border border-slate-200 px-4 py-2 text-sm font-bold text-slate-600 hover:bg-slate-50 focus-visible:outline-sky-600 disabled:opacity-50" @click="load">{{ loading ? '加载中…' : '重新加载' }}</button>
    </header>
    <p v-if="error" role="alert" class="rounded-xl border border-red-100 bg-red-50 p-4 text-sm text-red-600">{{ error }}</p>
    <p v-if="loading" role="status" class="p-4 text-sm text-slate-500">正在读取压缩设置…</p>
    <form v-else-if="current" class="panel p-5 sm:p-6" @submit.prevent="save">
      <fieldset :disabled="saving" class="space-y-5">
        <legend class="mb-4 text-sm font-black text-slate-900">允许的请求压缩格式</legend>
        <div class="grid gap-3 sm:grid-cols-2">
          <label class="flex cursor-pointer items-start gap-3 rounded-xl border border-slate-200 p-4 hover:border-sky-200">
            <input v-model="draft.gzipEnabled" type="checkbox" class="mt-1 h-5 w-5 shrink-0 accent-sky-600" />
            <span><strong class="block text-slate-900">gzip</strong><span class="mt-1 block text-xs leading-5 text-slate-500">接收 Content-Encoding: gzip 的 JSON 请求。</span></span>
          </label>
          <label class="flex cursor-pointer items-start gap-3 rounded-xl border border-slate-200 p-4 hover:border-sky-200">
            <input v-model="draft.zstdEnabled" type="checkbox" class="mt-1 h-5 w-5 shrink-0 accent-sky-600" />
            <span><strong class="block text-slate-900">zstd</strong><span class="mt-1 block text-xs leading-5 text-slate-500">接收 Content-Encoding: zstd 的 JSON 请求。</span></span>
          </label>
        </div>
        <div class="rounded-xl border border-slate-200 p-4">
          <label for="compression-budget" class="block text-sm font-bold text-slate-900">解压内存预算（MB）</label>
          <div class="mt-3 flex flex-wrap items-center gap-3">
            <input id="compression-budget" v-model.number="draft.memoryBudgetMb" type="number" required min="32" max="1024" step="1" aria-describedby="compression-budget-help" :aria-invalid="!valid" class="h-11 w-36 rounded-lg border border-slate-200 px-3 font-mono text-lg font-bold text-slate-900 outline-none focus:border-sky-400 focus:ring-4 focus:ring-sky-100" />
            <span class="text-sm text-slate-600">{{ slots }} 个并发解压名额 · 每个 {{ current.requestBudgetMb }} MB</span>
          </div>
          <p id="compression-budget-help" class="mt-3 text-xs leading-6 text-slate-500">默认 128 MB，可设置 32–1024 MB 的整数。按需使用，所有正在解压和解析的压缩请求共用；预算不足时返回 503。降低预算会等待已有名额释放。</p>
          <p v-if="!valid" class="mt-2 text-xs font-bold text-red-600">请输入 32–1024 之间的整数。</p>
        </div>
        <div class="rounded-xl bg-sky-50 p-4 text-sm leading-6 text-sky-900">
          <p>单次解压后最多 {{ current.maxBodyMb }} MiB，zstd 窗口最多 {{ current.zstdWindowMb }} MiB。解析完成后释放名额，流式回答不继续占用解压预算。</p>
          <p class="mt-2 text-xs text-sky-800">这是当前后端实例的解压资源预算，不会常驻预分配；应用本身、JSON 对象和上游转发仍需额外内存。未压缩请求不受这两个开关影响。</p>
        </div>
        <div class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
          <p role="status" class="text-xs font-semibold text-slate-500">{{ dirty ? '有未保存的修改' : '已保存设置' }} · 当前快照：{{ current.activeRequests }} 个请求 / {{ current.reservedMb }} MB 名额</p>
          <button type="submit" :disabled="!valid || !dirty || saving" class="rounded-lg bg-slate-950 px-5 py-3 text-sm font-black text-white hover:bg-slate-800 focus-visible:outline-sky-600 disabled:cursor-not-allowed disabled:opacity-40">{{ saving ? '保存中…' : '保存设置' }}</button>
        </div>
      </fieldset>
    </form>
  </section>
</template>
