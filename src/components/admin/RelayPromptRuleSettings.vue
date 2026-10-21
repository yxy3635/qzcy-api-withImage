<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '@/api/adminApi'
import { useToast } from '@/composables/useToast'
import type { RelayPromptRuleConfig, RelayPromptRuleHits, RelayPromptRuleStatus } from '@/types'
import Pagination from '@/components/Pagination.vue'

const toast = useToast()
const current = ref<RelayPromptRuleStatus | null>(null)
const draft = ref<RelayPromptRuleConfig>({ enabled: true, content: '', autoBanThreshold: 3 })
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const hits = ref<RelayPromptRuleHits | null>(null)
const hitsLoading = ref(false)
const hitsError = ref('')
let hitLoadVersion = 0
const hitPage = ref(1)
const hitKeyword = ref('')
const maxLength = computed(() => current.value?.maxContentLength ?? 16000)
const valid = computed(() => draft.value.content.length <= maxLength.value
  && (!draft.value.enabled || draft.value.content.trim().length > 0)
  && Number.isInteger(draft.value.autoBanThreshold) && draft.value.autoBanThreshold >= 1 && draft.value.autoBanThreshold <= 1000)
const dirty = computed(() => current.value !== null && (
  current.value.enabled !== draft.value.enabled || current.value.content !== draft.value.content
  || current.value.autoBanThreshold !== draft.value.autoBanThreshold
))

function apply(status: RelayPromptRuleStatus) {
  current.value = status
  draft.value = { enabled: status.enabled, content: status.content, autoBanThreshold: status.autoBanThreshold }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    apply((await adminApi.relayPromptRuleConfig()).data.data)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载提示词规则失败'
  } finally {
    loading.value = false
  }
  await loadHits()
}

async function loadHits() {
  const version = ++hitLoadVersion
  hitsLoading.value = true
  hitsError.value = ''
  try {
    const result = (await adminApi.relayPromptRuleHits(hitPage.value, 8, hitKeyword.value.trim())).data.data
    if (version === hitLoadVersion) hits.value = result
  } catch (err) {
    if (version === hitLoadVersion) hitsError.value = err instanceof Error ? err.message : '加载触发记录失败'
  } finally {
    if (version === hitLoadVersion) hitsLoading.value = false
  }
}

function searchHits() {
  hitPage.value = 1
  void loadHits()
}

function formatTime(value?: string) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
}

function endpointLabel(value: string) {
  return value.replace('/v1/', '').replaceAll('/', ' · ')
}

async function save() {
  if (!current.value || !dirty.value || !valid.value || saving.value) return
  saving.value = true
  error.value = ''
  try {
    apply((await adminApi.updateRelayPromptRuleConfig({ ...draft.value })).data.data)
    toast.success(current.value?.enabled ? '提示词规则已保存并启用' : '提示词规则已关闭，内容已保留')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存提示词规则失败'
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
        <h2 class="text-xl font-black text-slate-950">提示词规则</h2>
        <p class="mt-1 text-sm leading-6 text-slate-500">统一管理发往模型的附加指令，保存后对后续请求立即生效。</p>
      </div>
      <button type="button" :disabled="loading || saving || dirty" class="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-bold text-slate-600 hover:bg-slate-50 focus-visible:outline-sky-600 disabled:cursor-not-allowed disabled:opacity-40" @click="load">{{ loading ? '加载中…' : '重新加载' }}</button>
    </header>
    <p v-if="error" role="alert" class="rounded-xl border border-red-100 bg-red-50 p-4 text-sm text-red-600">{{ error }}</p>
    <p v-if="loading" role="status" class="p-4 text-sm text-slate-500">正在读取提示词规则…</p>
    <form v-else-if="current" class="panel overflow-hidden" @submit.prevent="save">
      <fieldset :disabled="saving" class="min-w-0">
        <legend class="sr-only">全局提示词转发规则设置</legend>
        <div class="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 p-5 sm:px-6">
          <div>
            <div class="flex flex-wrap items-center gap-3">
              <h3 id="prompt-rule-enabled-label" class="text-sm font-black text-slate-900">启用全局规则</h3>
              <span class="rounded-md px-2 py-1 text-xs font-bold" :class="current.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'">{{ current.enabled ? '当前已启用' : '当前已关闭' }}</span>
            </div>
            <p id="prompt-rule-enabled-help" class="mt-2 text-xs leading-5 text-slate-500">应用到所有渠道与分组；关闭后保留设置，停止附加规则及检测后续请求。</p>
          </div>
          <button type="button" role="switch" :aria-checked="draft.enabled" aria-labelledby="prompt-rule-enabled-label" aria-describedby="prompt-rule-enabled-help" class="relative h-7 w-12 shrink-0 rounded-full transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-sky-600 disabled:opacity-40" :class="draft.enabled ? 'bg-sky-600 hover:bg-sky-700' : 'bg-slate-300 hover:bg-slate-400'" @click="draft.enabled = !draft.enabled">
            <span aria-hidden="true" class="absolute left-1 top-1 h-5 w-5 rounded-full bg-white shadow-sm transition-transform motion-reduce:transition-none" :class="draft.enabled ? 'translate-x-5' : 'translate-x-0'" />
          </button>
        </div>
        <div class="space-y-4 p-5 sm:p-6">
          <div class="flex flex-col items-start justify-between gap-4 rounded-xl border border-amber-100 bg-amber-50/50 p-4 sm:flex-row sm:items-center">
            <div class="min-w-0 flex-1">
              <label for="relay-prompt-auto-ban-threshold" class="text-sm font-bold text-slate-900">自动封禁阈值</label>
              <p id="relay-prompt-auto-ban-help" class="mt-1 text-xs leading-6 text-slate-600">同一用户累计命中达到此次数时自动封禁，默认 3 次，可设为 1–1000 次。调低阈值后在下一次命中时判断；历史次数保留。</p>
              <p class="mt-1 text-xs leading-6 text-slate-500">命中后返回 HTTP 400 并提示账号已被标记观察；达到阈值后，该账号的登录与中转访问将被限制。误判可在用户管理中解除封禁。</p>
            <p class="mt-1 text-xs leading-6 text-slate-500">为确保流式命中也能返回 HTTP 400，Responses 与多候选回复会先检测完整输出，再返回内容。</p>
            </div>
            <div class="flex shrink-0 items-center gap-2"><input id="relay-prompt-auto-ban-threshold" v-model.number="draft.autoBanThreshold" type="number" min="1" max="1000" step="1" required aria-describedby="relay-prompt-auto-ban-help" class="h-11 w-24 rounded-lg border border-slate-200 bg-white px-3 text-sm font-bold tabular-nums outline-none focus:border-sky-400 focus:ring-4 focus:ring-sky-100" /><span class="text-sm text-slate-500">次</span></div>
          </div>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <label for="relay-prompt-rule-content" class="text-sm font-bold text-slate-900">规则内容</label>
            <button type="button" :disabled="draft.content === current.defaultContent" class="text-xs font-bold text-sky-700 underline-offset-4 hover:underline focus-visible:outline-sky-600 disabled:text-slate-400 disabled:no-underline" @click="draft.content = current.defaultContent">填入默认规则</button>
          </div>
          <textarea id="relay-prompt-rule-content" v-model="draft.content" rows="15" :maxlength="maxLength" :required="draft.enabled" :aria-invalid="!valid" aria-describedby="relay-prompt-rule-help relay-prompt-rule-validation" spellcheck="false" class="block min-h-72 w-full resize-y rounded-xl border border-slate-200 bg-slate-50/60 p-4 text-sm leading-7 text-slate-800 outline-none focus:border-sky-400 focus:bg-white focus:ring-4 focus:ring-sky-100" />
          <div class="flex flex-wrap items-start justify-between gap-2 text-xs text-slate-500">
            <p id="relay-prompt-rule-help">开关、编辑及填入默认规则均需点击「保存设置」后生效。</p>
            <span class="tabular-nums">{{ draft.content.length.toLocaleString() }} / {{ maxLength.toLocaleString() }} 字符</span>
          </div>
          <p id="relay-prompt-rule-validation" :role="!valid ? 'alert' : undefined" class="text-xs font-bold text-red-600">{{ !valid ? '请将封禁阈值设为 1–1000 的整数；启用时请填写规则内容，且不要超过字符上限。' : '' }}</p>
          <div class="border-l-2 border-sky-300 bg-sky-50/70 px-4 py-3 text-xs leading-6 text-slate-600">
            <p>适用于对话、Responses（含上下文压缩）、Messages（含 Token 计数）、文本补全及绘图请求，也覆盖后台渠道测试和站内绘图。</p>
            <p class="mt-1">向量、内容审核、音频接口没有通用指令字段，保持原输入。规则通过提示词传递，实际遵循效果取决于上游模型。</p>
          </div>
          <footer class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
            <p role="status" class="text-xs font-semibold" :class="dirty ? 'text-amber-700' : 'text-slate-500'">{{ dirty ? '有未保存的修改' : '已保存设置' }}</p>
            <div class="flex items-center gap-3">
              <button type="button" :disabled="!dirty || saving" class="rounded-lg px-3 py-3 text-sm font-bold text-slate-600 hover:bg-slate-50 focus-visible:outline-sky-600 disabled:opacity-40" @click="apply(current)">撤销修改</button>
              <button type="submit" :disabled="!valid || !dirty || saving" class="rounded-lg bg-slate-950 px-5 py-3 text-sm font-black text-white hover:bg-slate-800 focus-visible:outline-sky-600 disabled:cursor-not-allowed disabled:opacity-40">{{ saving ? '保存中…' : '保存设置' }}</button>
            </div>
          </footer>
        </div>
      </fieldset>
    </form>
    <section class="panel overflow-hidden">
      <header class="flex flex-wrap items-end justify-between gap-4 border-b border-slate-100 p-5 sm:px-6">
        <div>
          <div class="flex flex-wrap items-center gap-3"><h2 class="text-lg font-black text-slate-950">规则拒答命中</h2><span class="rounded-md bg-amber-50 px-2 py-1 text-xs font-bold text-amber-700">{{ hits?.userCount || 0 }} 个用户</span></div>
          <p class="mt-1 text-xs leading-5 text-slate-500">累计 {{ hits?.hitCount || 0 }} 次。仅匹配模型完整回复「抱歉，该请求违反平台使用规范」，不因“越狱”等关键词标记用户。</p>
        </div>
        <div class="flex w-full gap-2 sm:w-auto"><input v-model="hitKeyword" type="search" aria-label="搜索触发用户" maxlength="100" placeholder="搜索用户名或用户 ID" class="h-10 min-w-0 flex-1 rounded-lg border border-slate-200 px-3 text-sm outline-none focus:border-sky-400 focus:ring-4 focus:ring-sky-100 sm:w-56" @keyup.enter="searchHits" /><button type="button" :disabled="hitsLoading" class="rounded-lg border border-slate-200 px-3 text-sm font-bold text-slate-600 hover:bg-slate-50 focus-visible:outline-sky-600 disabled:opacity-40" @click="searchHits">搜索</button><button type="button" :disabled="hitsLoading" class="rounded-lg border border-slate-200 px-3 text-sm font-bold text-slate-600 hover:bg-slate-50 focus-visible:outline-sky-600 disabled:opacity-40" @click="loadHits">刷新记录</button></div>
      </header>
      <p v-if="hitsError" role="alert" class="m-5 rounded-lg bg-red-50 p-3 text-sm text-red-600">{{ hitsError }}</p>
      <div v-if="hitsLoading" role="status" class="p-6 text-sm text-slate-500">正在读取触发记录…</div>
      <div v-else-if="!hits?.records.length" class="p-8 text-center text-sm text-slate-500">暂无规则拒答命中记录</div>
      <div v-else class="overflow-x-auto"><table class="min-w-[760px] w-full text-left text-sm"><thead class="bg-slate-50 text-xs font-bold text-slate-500"><tr><th class="px-5 py-3">用户</th><th class="px-5 py-3">账号状态 / 次数</th><th class="px-5 py-3">最近请求</th><th class="px-5 py-3">接口 / 模型</th><th class="px-5 py-3">请求摘要（已脱敏）</th></tr></thead><tbody class="divide-y divide-slate-100"><tr v-for="record in hits.records" :key="record.userId" class="align-top"><td class="px-5 py-4"><strong class="block text-slate-900">{{ record.username || '已删除用户' }}</strong><span class="mt-1 block font-mono text-xs text-slate-400">#{{ record.userId }}</span></td><td class="px-5 py-4"><div class="flex items-center gap-2"><span class="whitespace-nowrap rounded-md px-2 py-1 text-xs font-bold" :class="record.banned ? 'bg-red-50 text-red-700' : 'bg-amber-50 text-amber-700'">{{ record.banned ? '已封禁' : '观察中' }}</span><span class="whitespace-nowrap text-xs font-black text-slate-700">{{ record.hitCount }} 次</span></div><span class="mt-2 block text-xs text-slate-400">首次 {{ formatTime(record.firstHitAt) }}</span></td><td class="whitespace-nowrap px-5 py-4 text-xs text-slate-600">{{ formatTime(record.lastHitAt) }}</td><td class="px-5 py-4 text-xs text-slate-600"><span class="block font-mono">{{ endpointLabel(record.endpoint) }}</span><span class="mt-1 block text-slate-400">{{ record.model || '—' }}</span></td><td class="max-w-[280px] px-5 py-4 text-xs leading-5 text-slate-500">{{ record.requestSummary || '无可展示文本' }}</td></tr></tbody></table></div>
      <footer v-if="hits && hits.pages > 1" class="border-t border-slate-100 p-4"><Pagination :current="hits.current" :pages="hits.pages" @change="(page) => { hitPage = page; void loadHits() }" /></footer>
    </section>
  </section>
</template>
