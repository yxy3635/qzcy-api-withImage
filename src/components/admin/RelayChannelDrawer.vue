<script setup lang="ts">
import { computed, ref } from 'vue'
import RelayDrawer from './RelayDrawer.vue'
import type { RelayModel } from '@/types'
import type { ChannelDraft } from '@/types/relayAdmin'

const props = withDefaults(defineProps<{
  isNew: boolean
  draft: ChannelDraft
  models: RelayModel[]
  saving?: boolean
  deleting?: boolean
  canDelete?: boolean
}>(), {
  saving: false,
  deleting: false,
  canDelete: true
})

const emit = defineEmits<{ save: []; cancel: []; delete: [] }>()

const advancedOpen = ref(false)
const modelSearch = ref('')
const modelTypeFilter = ref('all')

const enabledModelCount = computed(() => props.draft.models.filter((item) => item.enabled).length)
const modelTypes = computed(() => Array.from(new Set(props.models.map((model) => model.modelType).filter(Boolean))))

const filteredModels = computed(() => {
  const keyword = modelSearch.value.trim().toLowerCase()
  return props.models.filter((model) => {
    const matchesKeyword = !keyword || modelOptionLabel(model).toLowerCase().includes(keyword)
    const matchesType = modelTypeFilter.value === 'all' || model.modelType === modelTypeFilter.value
    return matchesKeyword && matchesType
  })
})

function modelOptionLabel(model: RelayModel) {
  const duplicate = props.models.some((item) => item.id !== model.id && item.model === model.model)
  if (!duplicate) return model.model
  const displayName = (model.displayName || '').trim()
  return displayName && displayName !== model.model ? `${model.model} · ${displayName} · #${model.id}` : `${model.model} · #${model.id}`
}

function channelModelRow(model: RelayModel) {
  let row = props.draft.models.find((item) => item.modelId === model.id)
  if (!row) {
    row = { modelId: model.id, upstreamModel: '', enabled: false }
    props.draft.models = [...props.draft.models, row]
  }
  return row
}

function setAllModels(enabled: boolean, type?: string) {
  props.draft.models = props.models.map((model) => ({
    modelId: model.id,
    upstreamModel: channelModelRow(model).upstreamModel || '',
    enabled: enabled && (type === undefined || model.modelType === type)
  }))
}

function addProvider() {
  const last = props.draft.providers[props.draft.providers.length - 1]
  props.draft.providers.push({
    id: null,
    name: '',
    apiBaseUrl: '',
    keyValue: '',
    apiKeyMasked: '',
    channelRule: last?.channelRule || 'openai',
    priority: 10,
    weight: 10,
    status: 'unknown',
    enabled: true
  })
}

function removeProvider(index: number) {
  props.draft.providers.splice(index, 1)
}
</script>

<template>
  <RelayDrawer
    eyebrow="Channel setup"
    :title="isNew ? '新增渠道' : '编辑渠道'"
    :subtitle="draft.name || '未命名渠道'"
    @close="emit('cancel')"
  >
    <div class="space-y-5">
      <!-- 基本信息 -->
      <section class="space-y-3">
        <h3 class="text-xs font-black uppercase tracking-[0.14em] text-slate-400">基本信息</h3>
        <label class="block">
          <span class="text-xs font-black text-slate-600">渠道名称</span>
          <input v-model="draft.name" class="input mt-1 h-10 rounded-lg text-sm" placeholder="例如 OpenAI 官方主渠道" />
        </label>
        <div class="grid gap-3 sm:grid-cols-2">
          <label class="block">
            <span class="text-xs font-black text-slate-600">调度策略</span>
            <select v-model="draft.scheduleStrategy" class="input mt-1 h-10 rounded-lg text-sm">
              <option value="weighted_random">加权随机（默认）</option>
              <option value="smooth_rr">平滑加权轮询</option>
              <option value="least_conn">最小并发</option>
              <option value="priority">严格优先级</option>
            </select>
          </label>
          <label class="block">
            <span class="text-xs font-black text-slate-600">可用分组</span>
            <input v-model="draft.groupNames" class="input mt-1 h-10 rounded-lg text-sm" placeholder="default,vip" />
            <span class="mt-1 block text-[11px] font-semibold text-slate-400">逗号分隔，只有这些分组的令牌会路由到该渠道。</span>
          </label>
        </div>
        <label class="flex items-center gap-2 text-sm font-black text-slate-700">
          <input v-model="draft.enabled" class="h-4 w-4 accent-sky-600" type="checkbox" />
          启用渠道
        </label>
        <label class="block">
          <span class="text-xs font-black text-slate-600">用户备注</span>
          <textarea v-model="draft.remark" class="input mt-1 min-h-20 rounded-lg py-2 text-sm" placeholder="展示给用户的注意事项，不影响转发。"></textarea>
        </label>
      </section>

      <!-- 上游供应商 -->
      <section>
        <div class="mb-2 flex items-center justify-between gap-3">
          <h3 class="text-xs font-black uppercase tracking-[0.14em] text-slate-400">上游供应商（{{ draft.providers.length }}）</h3>
          <button class="rounded-md border border-sky-200 bg-white px-2.5 py-1 text-xs font-black text-sky-700 transition hover:bg-sky-50" type="button" @click="addProvider">添加供应商</button>
        </div>
        <div class="space-y-3">
          <div v-for="(provider, index) in draft.providers" :key="provider.id ?? `provider-${index}`" class="rounded-xl border border-slate-200 bg-slate-50 p-3">
            <div class="grid gap-2 sm:grid-cols-2">
              <label class="block">
                <span class="text-xs font-black text-slate-600">名称</span>
                <input v-model="provider.name" class="input mt-1 h-9 rounded-lg text-sm" placeholder="例如 OpenAI 官方" />
              </label>
              <label class="block">
                <span class="text-xs font-black text-slate-600">规则</span>
                <select v-model="provider.channelRule" class="input mt-1 h-9 rounded-lg text-sm">
                  <option value="openai">OpenAI 兼容</option>
                  <option value="anthropic">Anthropic</option>
                </select>
              </label>
              <label class="block sm:col-span-2">
                <span class="text-xs font-black text-slate-600">Base URL</span>
                <input v-model="provider.apiBaseUrl" class="input mt-1 h-9 rounded-lg text-sm" placeholder="https://api.openai.com" />
              </label>
              <label class="block sm:col-span-2">
                <span class="text-xs font-black text-slate-600">API Key</span>
                <input v-model="provider.keyValue" class="input mt-1 h-9 rounded-lg text-sm" type="password" :placeholder="provider.id != null && provider.apiKeyMasked ? `留空保持 ${provider.apiKeyMasked} 不变` : '上游 API Key'" />
              </label>
              <label class="block">
                <span class="text-xs font-black text-slate-600">优先级</span>
                <input v-model.number="provider.priority" class="input mt-1 h-9 rounded-lg text-sm" type="number" placeholder="10" />
              </label>
              <label class="block">
                <span class="text-xs font-black text-slate-600">权重</span>
                <input v-model.number="provider.weight" class="input mt-1 h-9 rounded-lg text-sm" type="number" placeholder="10" />
              </label>
            </div>
            <div class="mt-2 flex items-center justify-between">
              <label class="flex items-center gap-2 text-xs font-black text-slate-600">
                <input v-model="provider.enabled" class="h-4 w-4 accent-sky-600" type="checkbox" />
                启用
              </label>
              <button
                v-if="draft.providers.length > 1"
                class="rounded-md border border-red-200 bg-red-50 px-2 py-1 text-xs font-black text-red-600 transition hover:bg-red-100"
                type="button"
                @click="removeProvider(index)"
              >移除</button>
            </div>
          </div>
        </div>
        <p class="mt-2 text-[11px] font-semibold text-slate-400">一个渠道可配置多个供应商，按上方调度策略分流；请求失败自动切换下一个。</p>
      </section>

      <!-- 模型绑定 -->
      <section>
        <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
          <h3 class="text-xs font-black uppercase tracking-[0.14em] text-slate-400">模型绑定（已启用 {{ enabledModelCount }}/{{ models.length }}）</h3>
          <div class="flex flex-wrap gap-1.5">
            <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-[11px] font-black text-slate-600 transition hover:border-sky-200 hover:text-sky-700" type="button" @click="setAllModels(true, 'chat')">聊天</button>
            <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-[11px] font-black text-slate-600 transition hover:border-sky-200 hover:text-sky-700" type="button" @click="setAllModels(true, 'image')">图片</button>
            <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-[11px] font-black text-slate-600 transition hover:border-sky-200 hover:text-sky-700" type="button" @click="setAllModels(true)">全选</button>
            <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-[11px] font-black text-slate-600 transition hover:border-red-200 hover:text-red-600" type="button" @click="setAllModels(false)">清空</button>
          </div>
        </div>
        <div class="mb-2 grid gap-2 sm:grid-cols-[1fr_120px]">
          <input v-model="modelSearch" class="input h-9 rounded-lg text-sm" placeholder="搜索模型…" />
          <select v-model="modelTypeFilter" class="input h-9 rounded-lg text-sm">
            <option value="all">全部类型</option>
            <option v-for="type in modelTypes" :key="type" :value="type">{{ type }}</option>
          </select>
        </div>
        <div class="grid max-h-80 gap-2 overflow-y-auto rounded-xl border border-slate-100 bg-slate-50 p-2">
          <label v-for="model in filteredModels" :key="model.id" class="grid gap-1.5 rounded-lg border border-slate-200 bg-white px-2.5 py-2">
            <span class="flex items-center gap-2 text-xs font-black text-slate-700">
              <input v-model="channelModelRow(model).enabled" class="h-4 w-4 accent-sky-600" type="checkbox" />
              <span class="truncate">{{ modelOptionLabel(model) }}</span>
            </span>
            <input v-model="channelModelRow(model).upstreamModel" class="input h-8 rounded-md text-xs" placeholder="留空：原样转发模型 ID" />
          </label>
          <p v-if="!filteredModels.length" class="py-6 text-center text-xs font-black text-slate-400">没有匹配的模型</p>
        </div>
      </section>

      <!-- 高级设置 -->
      <section>
        <button class="flex w-full items-center justify-between rounded-lg bg-slate-50 px-3 py-2 text-xs font-black uppercase tracking-[0.14em] text-slate-500 transition hover:bg-slate-100" type="button" @click="advancedOpen = !advancedOpen">
          高级设置
          <span>{{ advancedOpen ? '收起' : '展开' }}</span>
        </button>
        <div v-if="advancedOpen" class="mt-3 grid gap-3 sm:grid-cols-2">
          <label class="block">
            <span class="text-xs font-black text-slate-600">优先级</span>
            <input v-model.number="draft.priority" class="input mt-1 h-10 rounded-lg text-sm" type="number" />
            <span class="mt-1 block text-[11px] font-semibold text-slate-400">数字越小越先被选择（跨渠道）。</span>
          </label>
          <label class="block">
            <span class="text-xs font-black text-slate-600">权重</span>
            <input v-model.number="draft.weight" class="input mt-1 h-10 rounded-lg text-sm" type="number" />
            <span class="mt-1 block text-[11px] font-semibold text-slate-400">同优先级渠道间的分流比例。</span>
          </label>
          <label class="block">
            <span class="text-xs font-black text-slate-600">RPM 限制</span>
            <input v-model.number="draft.rpmLimit" class="input mt-1 h-10 rounded-lg text-sm" type="number" placeholder="0" />
            <span class="mt-1 block text-[11px] font-semibold text-slate-400">每分钟请求数上限，0 不限。</span>
          </label>
          <label class="block">
            <span class="text-xs font-black text-slate-600">TPM 限制</span>
            <input v-model.number="draft.tpmLimit" class="input mt-1 h-10 rounded-lg text-sm" type="number" placeholder="0" />
            <span class="mt-1 block text-[11px] font-semibold text-slate-400">每分钟 Token 上限，0 不限。</span>
          </label>
          <label class="block">
            <span class="text-xs font-black text-slate-600">流式并发上限</span>
            <input v-model.number="draft.maxConcurrency" class="input mt-1 h-10 rounded-lg text-sm" type="number" min="0" placeholder="0" />
            <span class="mt-1 block text-[11px] font-semibold text-slate-400">同时向上游发起的流式请求数，0 不限。</span>
          </label>
          <label class="block">
            <span class="text-xs font-black text-slate-600">渠道成本倍率</span>
            <input v-model.number="draft.priceMultiplier" class="input mt-1 h-10 rounded-lg text-sm" type="number" step="0.0001" />
            <span class="mt-1 block text-[11px] font-semibold text-slate-400">仅用于成本统计，不参与用户扣费。</span>
          </label>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="flex items-center gap-2">
        <button
          v-if="canDelete"
          class="h-10 rounded-lg border border-red-200 bg-red-50 px-4 text-xs font-black text-red-600 transition hover:bg-red-100 disabled:opacity-60"
          :disabled="deleting"
          @click="emit('delete')"
        >{{ deleting ? '删除中' : '删除' }}</button>
        <div class="flex-1"></div>
        <button class="h-10 rounded-lg border border-slate-200 px-4 text-xs font-black text-slate-600 transition hover:bg-slate-50" type="button" @click="emit('cancel')">取消</button>
        <button class="h-10 rounded-lg bg-slate-950 px-5 text-xs font-black text-white transition hover:bg-sky-600 disabled:opacity-60" :disabled="saving" @click="emit('save')">
          {{ saving ? '保存中…' : (isNew ? '创建渠道' : '保存渠道') }}
        </button>
      </div>
    </template>
  </RelayDrawer>
</template>
