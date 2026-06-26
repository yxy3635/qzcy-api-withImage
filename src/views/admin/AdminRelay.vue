<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import { adminApi } from '@/api/adminApi'
import { useToast } from '@/composables/useToast'
import type { RelayAdminOverview, RelayChannel, RelayGroup, RelayModel, RelayUpstreamModel } from '@/types'

const toast = useToast()
type Tab = 'overview' | 'channels' | 'tokens' | 'models' | 'usage' | 'policy'

interface ChannelDraft {
  name: string
  provider: string
  channelRule: string
  apiBaseUrl: string
  keyValue: string
  groupNames: string
  remark: string
  priority: number
  weight: number
  rpmLimit: number
  tpmLimit: number
  maxConcurrency: number
  priceMultiplier: number
  enabled: boolean
  models: ChannelModelDraft[]
}

interface ChannelModelDraft {
  modelId: number
  upstreamModel: string
  enabled: boolean
}

interface ModelDraft {
  model: string
  displayName: string
  modelType: string
  inputPrice: number
  outputPrice: number
  cachedInputPrice: number
  cacheCreationPrice: number
  requestPrice: number
  fixedRequestBilling: boolean
  status: string
  enabled: boolean
  sortOrder: number
}

interface GroupDraft {
  code: string
  name: string
  ratio: number
  enabled: boolean
  modelIds: number[]
}

const overview = ref<RelayAdminOverview | null>(null)
const activeTab = ref<Tab>('overview')
const loading = ref(false)
const saving = ref<number | string | null>(null)
const error = ref('')
const channelDrafts = reactive<Record<number, ChannelDraft>>({})
const modelDrafts = reactive<Record<number, ModelDraft>>({})
const groupDrafts = reactive<Record<number, GroupDraft>>({})
const syncingChannelId = ref<number | null>(null)
const upstreamModels = ref<RelayUpstreamModel[]>([])
const selectedUpstreamIds = ref<string[]>([])
const modelSearch = ref('')
const modelTypeFilter = ref('all')
const modelStateFilter = ref('all')
const channelSearch = ref('')
const channelStateFilter = ref('all')
const editingModelId = ref<number | 'new' | null>(null)
const expandedChannels = ref<Array<number | 'new'>>(['new'])
const expandedGroups = ref<Array<number | 'new'>>([])
const expandedModelSync = ref(false)

const newChannel = reactive<ChannelDraft>({
  name: 'OpenAI Compatible',
  provider: '自定义供应商',
  channelRule: 'openai',
  apiBaseUrl: 'https://api.openai.com',
  keyValue: '',
  groupNames: 'default',
  remark: '',
  priority: 10,
  weight: 10,
  rpmLimit: 0,
  tpmLimit: 0,
  maxConcurrency: 0,
  priceMultiplier: 1,
  enabled: true,
  models: []
})

const newModel = reactive<ModelDraft>({
  model: 'gpt-4o',
  displayName: 'GPT-4o',
  modelType: 'chat',
  inputPrice: 2.5,
  outputPrice: 10,
  cachedInputPrice: 0,
  cacheCreationPrice: 0,
  requestPrice: 0,
  fixedRequestBilling: false,
  status: 'available',
  enabled: true,
  sortOrder: 10
})

const newGroup = reactive<GroupDraft>({
  code: 'vip',
  name: 'VIP 分组',
  ratio: 1,
  enabled: true,
  modelIds: []
})

const tabs: Array<{ id: Tab; label: string }> = [
  { id: 'overview', label: '总览' },
  { id: 'channels', label: '渠道' },
  { id: 'tokens', label: '令牌' },
  { id: 'models', label: '模型' },
  { id: 'usage', label: '用量' },
  { id: 'policy', label: '策略' }
]

const stats = computed(() => overview.value?.stats)
const channels = computed(() => overview.value?.channels || [])
const tokens = computed(() => overview.value?.tokens || [])
const models = computed(() => overview.value?.models || [])
const groups = computed(() => overview.value?.groups || [])
const modelTypes = computed(() => Array.from(new Set(models.value.map((item) => item.modelType).filter(Boolean))))
const filteredChannels = computed(() => {
  const keyword = channelSearch.value.trim().toLowerCase()
  return channels.value.filter((channel) => {
    const draft = channelDraftOf(channel)
    const matchesKeyword = !keyword
      || draft.name.toLowerCase().includes(keyword)
      || draft.provider.toLowerCase().includes(keyword)
      || draft.apiBaseUrl.toLowerCase().includes(keyword)
      || draft.groupNames.toLowerCase().includes(keyword)
    const matchesState = channelStateFilter.value === 'all'
      || (channelStateFilter.value === 'enabled' && draft.enabled)
      || (channelStateFilter.value === 'disabled' && !draft.enabled)
      || (channelStateFilter.value === 'failed' && channel.status === 'failed')
    return matchesKeyword && matchesState
  })
})
const filteredModels = computed(() => {
  const keyword = modelSearch.value.trim().toLowerCase()
  return models.value.filter((model) => {
    const draft = modelDraftOf(model)
    const matchesKeyword = !keyword
      || draft.model.toLowerCase().includes(keyword)
      || draft.displayName.toLowerCase().includes(keyword)
    const matchesType = modelTypeFilter.value === 'all' || draft.modelType === modelTypeFilter.value
    const matchesState = modelStateFilter.value === 'all'
      || (modelStateFilter.value === 'enabled' && draft.enabled)
      || (modelStateFilter.value === 'disabled' && !draft.enabled)
    return matchesKeyword && matchesType && matchesState
  })
})
const enabledModelsCount = computed(() => models.value.filter((model) => modelDraftOf(model).enabled).length)
const pricedModelsCount = computed(() => models.value.filter((model) => {
  const draft = modelDraftOf(model)
  return Number(draft.inputPrice || 0) > 0
    || Number(draft.outputPrice || 0) > 0
    || Number(draft.cachedInputPrice || 0) > 0
    || Number(draft.cacheCreationPrice || 0) > 0
    || Number(draft.requestPrice || 0) > 0
}).length)
const editingModel = computed(() => {
  if (typeof editingModelId.value !== 'number') return null
  return models.value.find((model) => model.id === editingModelId.value) || null
})

function compactToken(value?: number) {
  const amount = Number(value || 0)
  if (amount >= 1_000_000_000) return `${(amount / 1_000_000_000).toFixed(2)}B`
  if (amount >= 1_000_000) return `${(amount / 1_000_000).toFixed(2)}M`
  if (amount >= 1_000) return `${(amount / 1_000).toFixed(2)}K`
  return String(amount)
}

function setChannelDraft(channel: RelayChannel) {
  channelDrafts[channel.id] = {
    name: channel.name,
    provider: channel.provider,
    channelRule: channel.channelRule || inferChannelRule(channel),
    apiBaseUrl: channel.apiBaseUrl,
    keyValue: '',
    groupNames: channel.groupNames || 'default',
    remark: channel.remark || '',
    priority: Number(channel.priority || 0),
    weight: Number(channel.weight || 0),
    rpmLimit: Number(channel.rpmLimit || 0),
    tpmLimit: Number(channel.tpmLimit || 0),
    maxConcurrency: Number(channel.maxConcurrency || 0),
    priceMultiplier: Number(channel.priceMultiplier || 1),
    enabled: channel.enabled,
    models: models.value.map((model) => {
      const binding = (channel.models || []).find((item) => item.modelId === model.id)
      return {
        modelId: model.id,
        upstreamModel: binding?.upstreamModel || model.model,
        enabled: binding ? Boolean(binding.enabled) : false
      }
    })
  }
}

function setModelDraft(model: RelayModel) {
  modelDrafts[model.id] = {
    model: model.model,
    displayName: model.displayName,
    modelType: model.modelType,
    inputPrice: Number(model.inputPrice || 0),
    outputPrice: Number(model.outputPrice || 0),
    cachedInputPrice: Number(model.cachedInputPrice || 0),
    cacheCreationPrice: Number(model.cacheCreationPrice || 0),
    requestPrice: Number(model.requestPrice || 0),
    fixedRequestBilling: Boolean(model.fixedRequestBilling),
    status: model.status || 'available',
    enabled: model.enabled,
    sortOrder: Number(model.sortOrder || 0)
  }
}

function setGroupDraft(group: RelayGroup) {
  groupDrafts[group.id] = {
    code: group.code,
    name: group.name,
    ratio: Number(group.ratio || 1),
    enabled: group.enabled,
    modelIds: [...(group.modelIds || [])]
  }
}

function channelDraftOf(channel: RelayChannel) {
  if (!channelDrafts[channel.id]) setChannelDraft(channel)
  return channelDrafts[channel.id] as ChannelDraft
}

function modelDraftOf(model: RelayModel) {
  if (!modelDrafts[model.id]) setModelDraft(model)
  return modelDrafts[model.id] as ModelDraft
}

function groupDraftOf(group: RelayGroup) {
  if (!groupDrafts[group.id]) setGroupDraft(group)
  return groupDrafts[group.id] as GroupDraft
}

function channelPayload(draft: ChannelDraft) {
  const payload: Record<string, unknown> = {
    name: draft.name,
    provider: draft.provider,
    channelRule: draft.channelRule,
    apiBaseUrl: draft.apiBaseUrl,
    groupNames: draft.groupNames,
    remark: draft.remark,
    priority: draft.priority,
    weight: draft.weight,
    rpmLimit: draft.rpmLimit,
    tpmLimit: draft.tpmLimit,
    maxConcurrency: draft.maxConcurrency,
    priceMultiplier: draft.priceMultiplier,
    enabled: draft.enabled,
    models: draft.models
  }
  const keyValue = draft.keyValue.trim()
  if (keyValue) payload['api' + 'Key'] = keyValue
  return payload
}

function groupPayload(draft: GroupDraft) {
  return {
    code: draft.code,
    name: draft.name,
    ratio: draft.ratio,
    enabled: draft.enabled,
    modelIds: draft.modelIds
  }
}

function selectedGroupModelCount(draft: GroupDraft) {
  return draft.modelIds.length
}

function isGroupModelSelected(draft: GroupDraft, modelId: number) {
  return draft.modelIds.includes(modelId)
}

function toggleGroupModel(draft: GroupDraft, modelId: number) {
  if (draft.modelIds.includes(modelId)) {
    draft.modelIds = draft.modelIds.filter((id) => id !== modelId)
  } else {
    draft.modelIds = [...draft.modelIds, modelId]
  }
}

function selectAllGroupModels(draft: GroupDraft) {
  draft.modelIds = models.value.map((model) => model.id)
}

function clearGroupModels(draft: GroupDraft) {
  draft.modelIds = []
}

function modelOptionLabel(model: RelayModel) {
  const duplicate = models.value.some((item) => item.id !== model.id && item.model === model.model)
  if (!duplicate) return model.model
  const displayName = (model.displayName || '').trim()
  return displayName && displayName !== model.model
    ? `${model.model} · ${displayName} · #${model.id}`
    : `${model.model} · #${model.id}`
}

function channelModelDraft(draft: ChannelDraft, model: RelayModel) {
  let item = draft.models.find((modelDraft) => modelDraft.modelId === model.id)
  if (!item) {
    item = { modelId: model.id, upstreamModel: model.model, enabled: false }
    draft.models = [...draft.models, item]
  }
  return item
}

function enabledChannelModelCount(channel: RelayChannel) {
  return (channelDraftOf(channel).models || []).filter((item) => item.enabled).length
}

function enabledChannelModelDraftCount(draft: ChannelDraft) {
  return (draft.models || []).filter((item) => item.enabled).length
}

function isChannelExpanded(id: number | 'new') {
  return expandedChannels.value.includes(id)
}

function toggleChannel(id: number | 'new') {
  expandedChannels.value = isChannelExpanded(id)
    ? expandedChannels.value.filter((item) => item !== id)
    : [...expandedChannels.value, id]
}

function isGroupExpanded(id: number | 'new') {
  return expandedGroups.value.includes(id)
}

function toggleGroup(id: number | 'new') {
  expandedGroups.value = isGroupExpanded(id)
    ? expandedGroups.value.filter((item) => item !== id)
    : [...expandedGroups.value, id]
}

function selectAllChannelModels(draft: ChannelDraft) {
  draft.models = models.value.map((model) => ({
    modelId: model.id,
    upstreamModel: channelModelDraft(draft, model).upstreamModel || model.model,
    enabled: true
  }))
}

function clearChannelModels(draft: ChannelDraft) {
  draft.models = models.value.map((model) => ({
    modelId: model.id,
    upstreamModel: channelModelDraft(draft, model).upstreamModel || model.model,
    enabled: false
  }))
}

function enableChannelModelsByType(draft: ChannelDraft, type: string) {
  draft.models = models.value.map((model) => ({
    modelId: model.id,
    upstreamModel: channelModelDraft(draft, model).upstreamModel || model.model,
    enabled: type === 'all' || model.modelType === type
  }))
}

function providerBadgeClass() {
  return 'bg-slate-100 text-slate-700 ring-slate-200'
}

function ruleLabel(rule: string) {
  return rule === 'anthropic' ? 'Anthropic' : 'OpenAI'
}

function ruleBadgeClass(rule: string) {
  return rule === 'anthropic'
    ? 'bg-orange-50 text-orange-700 ring-orange-100'
    : 'bg-emerald-50 text-emerald-700 ring-emerald-100'
}

function inferChannelRule(channel: RelayChannel) {
  const provider = (channel.provider || '').toLowerCase()
  const baseUrl = (channel.apiBaseUrl || '').toLowerCase()
  if (provider.includes('anthropic') || provider.includes('claude') || baseUrl.includes('api.anthropic.com')) return 'anthropic'
  return 'openai'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.relayOverview()
    overview.value = data.data
    overview.value.channels.forEach(setChannelDraft)
    overview.value.models.forEach(setModelDraft)
    overview.value.groups.forEach(setGroupDraft)
    if (typeof editingModelId.value === 'number' && !overview.value.models.some((model) => model.id === editingModelId.value)) {
      editingModelId.value = null
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '中转站数据加载失败'
  } finally {
    loading.value = false
  }
}

async function createGroup() {
  saving.value = 'group-new'
  error.value = ''
  try {
    if (!newGroup.modelIds.length) selectAllGroupModels(newGroup)
    await adminApi.createRelayGroup(groupPayload(newGroup))
    toast.success(`${newGroup.code} 分组已创建`)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建分组失败'
    toast.error(error.value)
  } finally {
    saving.value = null
  }
}

async function saveGroup(group: RelayGroup) {
  const draft = groupDraftOf(group)
  saving.value = `group-${group.id}`
  error.value = ''
  try {
    await adminApi.updateRelayGroup(group.id, groupPayload(draft))
    toast.success(`${draft.code} 分组已保存`)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存分组失败'
    toast.error(error.value)
  } finally {
    saving.value = null
  }
}

async function deleteGroup(group: RelayGroup) {
  if (!window.confirm(`确定删除分组 ${group.code} 吗？已使用该分组的令牌需要重新配置。`)) return
  saving.value = `group-delete-${group.id}`
  error.value = ''
  try {
    await adminApi.deleteRelayGroup(group.id)
    toast.success(`${group.code} 分组已删除`)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除分组失败'
    toast.error(error.value)
  } finally {
    saving.value = null
  }
}

async function createChannel() {
  saving.value = 'channel-new'
  error.value = ''
  try {
    if (!newChannel.models.length) selectAllChannelModels(newChannel)
    await adminApi.createRelayChannel(channelPayload(newChannel))
    newChannel.keyValue = ''
    toast.success('渠道已创建')
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建失败'
    toast.error(error.value)
  } finally {
    saving.value = null
  }
}

async function saveChannel(channel: RelayChannel) {
  const draft = channelDraftOf(channel)
  saving.value = `channel-${channel.id}`
  error.value = ''
  try {
    await adminApi.updateRelayChannel(channel.id, channelPayload(draft))
    draft.keyValue = ''
    toast.success(`${draft.name} 已保存`)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
    toast.error(error.value)
  } finally {
    saving.value = null
  }
}

async function deleteChannel(channel: RelayChannel) {
  if (!window.confirm(`确定删除渠道 ${channel.name} 吗？该渠道的模型绑定也会一并删除。`)) return
  saving.value = `channel-delete-${channel.id}`
  error.value = ''
  try {
    await adminApi.deleteRelayChannel(channel.id)
    toast.success(`${channel.name} 已删除`)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除渠道失败'
    toast.error(error.value)
  } finally {
    saving.value = null
  }
}

async function createModel() {
  saving.value = 'model-new'
  error.value = ''
  try {
    await adminApi.createRelayModel(newModel)
    toast.success('模型已创建')
    editingModelId.value = null
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建失败'
    toast.error(error.value)
  } finally {
    saving.value = null
  }
}

async function saveModel(model: RelayModel) {
  const draft = modelDraftOf(model)
  saving.value = `model-${model.id}`
  error.value = ''
  try {
    await adminApi.updateRelayModel(model.id, draft)
    toast.success(`${draft.model} 已保存`)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
    toast.error(error.value)
  } finally {
    saving.value = null
  }
}

async function deleteModel(model: RelayModel) {
  if (!window.confirm(`确定删除模型 ${model.model} 吗？删除后用户将不能再使用这个模型。`)) return
  saving.value = `model-delete-${model.id}`
  error.value = ''
  try {
    await adminApi.deleteRelayModel(model.id)
    toast.success(`${model.model} 已删除`)
    if (editingModelId.value === model.id) editingModelId.value = null
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除失败'
    toast.error(error.value)
  } finally {
    saving.value = null
  }
}

async function syncModels(channel: RelayChannel) {
  syncingChannelId.value = channel.id
  error.value = ''
  try {
    const { data } = await adminApi.syncRelayModels(channel.id)
    upstreamModels.value = data.data
    selectedUpstreamIds.value = data.data.filter((item) => !item.configured).map((item) => item.id)
    const upstreamIds = data.data.map((item) => item.id).filter(Boolean)
    if (upstreamIds.length) {
      toast.success(`已从 ${channel.name} 查询到 ${upstreamIds.length} 个上游模型，可在下方选择导入到模型库`)
    } else {
      toast.info(`已查询 ${channel.name}，但上游没有返回模型`)
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '查询上游模型失败'
    toast.error(error.value)
  } finally {
    syncingChannelId.value = null
  }
}

async function syncChannelStatus() {
  saving.value = 'channel-status-sync'
  error.value = ''
  try {
    await adminApi.syncRelayChannelStatus()
    toast.success('渠道状态已同步')
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '同步渠道状态失败'
    toast.error(error.value)
  } finally {
    saving.value = null
  }
}

function inferModelType(modelId: string) {
  const lower = modelId.toLowerCase()
  if (lower.includes('embedding')) return 'embedding'
  if (lower.includes('image') || lower.includes('dall-e')) return 'image'
  if (lower.includes('whisper') || lower.includes('tts') || lower.includes('audio')) return 'audio'
  if (lower.includes('code')) return 'code'
  return 'chat'
}

function pickUpstreamModel(item: RelayUpstreamModel) {
  newModel.model = item.id
  newModel.displayName = item.id
  newModel.modelType = inferModelType(item.id)
  activeTab.value = 'models'
  editingModelId.value = 'new'
}

function editModel(model: RelayModel) {
  modelDraftOf(model)
  editingModelId.value = model.id
}

function newModelEditor() {
  editingModelId.value = 'new'
}

function modelTotalPrice(draft: ModelDraft) {
  return Number(draft.inputPrice || 0)
    + Number(draft.outputPrice || 0)
    + Number(draft.cachedInputPrice || 0)
    + Number(draft.cacheCreationPrice || 0)
    + Number(draft.requestPrice || 0)
}

async function enableSelectedUpstreamModels() {
  const ids = selectedUpstreamIds.value.filter((id) => id && !models.value.some((item) => item.model === id))
  if (!ids.length) return
  saving.value = 'model-import'
  error.value = ''
  try {
    for (const id of ids) {
      await adminApi.createRelayModel({
        model: id,
        displayName: id,
        modelType: inferModelType(id),
        inputPrice: 0,
        outputPrice: 0,
        cachedInputPrice: 0,
        cacheCreationPrice: 0,
        requestPrice: 0,
        fixedRequestBilling: false,
        status: 'available',
        enabled: true,
        sortOrder: 10
      })
    }
    toast.success(`已启用 ${ids.length} 个上游模型`)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '启用上游模型失败'
    toast.error(error.value)
  } finally {
    saving.value = null
  }
}

onMounted(load)
</script>

<template>
  <AppLayout admin>
    <div class="page-enter">
      <div class="flex flex-wrap items-end justify-between gap-5">
        <div>
          <p class="text-sm font-black tracking-[0.22em] text-sky-600">中转站设置</p>
          <h1 class="mt-2 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">通用 API 中转后台</h1>
          <p class="mt-3 text-sm font-medium text-slate-500">独立管理中转站渠道、模型、令牌、用量和策略；只共享用户账号与余额。</p>
        </div>
        <button class="h-12 rounded-2xl border border-slate-200 bg-white px-5 text-sm font-black text-slate-700 shadow-sm transition hover:border-sky-200 hover:bg-sky-50" @click="load">
          {{ loading ? '同步中' : '刷新' }}
        </button>
        <button class="h-12 rounded-2xl bg-slate-950 px-5 text-sm font-black text-white shadow-sm transition hover:bg-sky-600 disabled:opacity-60" :disabled="saving === 'channel-status-sync'" @click="syncChannelStatus">
          {{ saving === 'channel-status-sync' ? '检测中' : '检测渠道' }}
        </button>
      </div>

      <div class="mt-6 flex gap-2 overflow-x-auto rounded-2xl border border-slate-200 bg-white p-1">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          class="shrink-0 rounded-xl px-4 py-2 text-sm font-black transition"
          :class="activeTab === tab.id ? 'bg-slate-950 text-white' : 'text-slate-500 hover:bg-sky-50 hover:text-sky-700'"
          @click="activeTab = tab.id"
        >
          {{ tab.label }}
        </button>
      </div>

      <p v-if="error" class="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{{ error }}</p>

      <section v-if="activeTab === 'overview'" class="mt-6 grid gap-4 xl:grid-cols-4">
        <div class="panel p-5"><p class="text-sm font-bold text-slate-500">启用渠道</p><p class="mt-2 text-3xl font-black">{{ stats?.activeChannels || 0 }}/{{ stats?.totalChannels || 0 }}</p></div>
        <div class="panel p-5"><p class="text-sm font-bold text-slate-500">启用令牌</p><p class="mt-2 text-3xl font-black">{{ stats?.activeTokens || 0 }}/{{ stats?.totalTokens || 0 }}</p></div>
        <div class="panel p-5"><p class="text-sm font-bold text-slate-500">总请求数</p><p class="mt-2 text-3xl font-black">{{ stats?.totalRequests || 0 }}</p></div>
        <div class="panel p-5"><p class="text-sm font-bold text-slate-500">总 Token</p><p class="mt-2 text-3xl font-black text-sky-600">{{ compactToken(stats?.totalTokensUsed) }}</p></div>
      </section>

      <section v-if="activeTab === 'channels'" class="mt-6 space-y-3">
        <details class="panel overflow-hidden">
          <summary class="flex cursor-pointer list-none flex-wrap items-center justify-between gap-3 px-4 py-3 transition hover:bg-slate-50">
            <div>
              <h2 class="text-base font-black text-slate-950">新增渠道</h2>
              <p class="mt-1 text-xs font-semibold text-slate-500">{{ newChannel.name }} · {{ newChannel.provider }} · {{ ruleLabel(newChannel.channelRule) }} · {{ enabledChannelModelDraftCount(newChannel) }} 个模型</p>
            </div>
            <span class="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-600">展开设置</span>
          </summary>
          <div class="collapsible-body border-t border-slate-100 p-4">
          <div class="space-y-4">
            <label class="block">
              <span class="text-sm font-black text-slate-800">渠道名称</span>
              <span class="mt-1 block text-xs font-semibold text-slate-500">后台识别用的名称，例如 OpenAI 官方、Azure 主渠道、备用渠道。</span>
              <input v-model="newChannel.name" class="input mt-2 h-12 rounded-2xl" placeholder="渠道名称" />
            </label>
            <label class="block">
              <span class="text-sm font-black text-slate-800">供应商</span>
              <span class="mt-1 block text-xs font-semibold text-slate-500">自由填写，只用于管理展示和后续排查。</span>
              <input v-model="newChannel.provider" class="input mt-2 h-12 rounded-2xl" placeholder="自定义供应商名称" />
            </label>
            <label class="block">
              <span class="text-sm font-black text-slate-800">规则</span>
              <span class="mt-1 block text-xs font-semibold text-slate-500">决定上游鉴权和请求格式，目前支持 OpenAI 与 Anthropic。</span>
              <select v-model="newChannel.channelRule" class="input mt-2 h-12 rounded-2xl">
                <option value="openai">OpenAI 兼容</option>
                <option value="anthropic">Anthropic</option>
              </select>
            </label>
            <label class="block">
              <span class="text-sm font-black text-slate-800">Base URL</span>
              <span class="mt-1 block text-xs font-semibold text-slate-500">上游中转或模型服务地址，系统会按所选规则拼接标准路径。</span>
              <input v-model="newChannel.apiBaseUrl" class="input mt-2 h-12 rounded-2xl" placeholder="https://api.openai.com" />
            </label>
            <label class="block">
              <span class="text-sm font-black text-slate-800">上游 API Key</span>
              <span class="mt-1 block text-xs font-semibold text-slate-500">调用上游服务使用的密钥，只保存在后台，用户不会看到。</span>
              <input v-model="newChannel.keyValue" class="input mt-2 h-12 rounded-2xl" type="password" placeholder="上游 API Key" />
            </label>
            <label class="block">
              <span class="text-sm font-black text-slate-800">可用分组</span>
              <span class="mt-1 block text-xs font-semibold text-slate-500">逗号分隔，例如 default,vip；只有这些分组的令牌会路由到该渠道。</span>
              <input v-model="newChannel.groupNames" class="input mt-2 h-12 rounded-2xl" placeholder="default,vip" />
            </label>
            <label class="block">
              <span class="text-sm font-black text-slate-800">用户备注</span>
              <span class="mt-1 block text-xs font-semibold text-slate-500">展示给用户看的注意事项，不会影响实际转发。</span>
              <textarea v-model="newChannel.remark" class="input mt-2 min-h-24 rounded-2xl py-3" placeholder="例如：该渠道仅支持图片模型，请勿用于聊天请求。"></textarea>
            </label>
            <div v-if="models.length" class="rounded-2xl border border-slate-100 bg-slate-50 p-3">
              <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
                <span class="text-sm font-black text-slate-800">绑定模型</span>
                <div class="flex flex-wrap gap-2">
                  <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-black text-slate-600 transition hover:border-sky-200 hover:text-sky-700" type="button" @click="enableChannelModelsByType(newChannel, 'chat')">聊天</button>
                  <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-black text-slate-600 transition hover:border-sky-200 hover:text-sky-700" type="button" @click="selectAllChannelModels(newChannel)">全选</button>
                  <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-black text-slate-600 transition hover:border-red-200 hover:text-red-600" type="button" @click="clearChannelModels(newChannel)">清空</button>
                </div>
              </div>
              <div class="grid max-h-56 gap-2 overflow-y-auto">
                <label v-for="model in models" :key="model.id" class="grid gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700">
                  <span class="flex items-center gap-2">
                    <input v-model="channelModelDraft(newChannel, model).enabled" class="h-4 w-4 accent-sky-600" type="checkbox" />
                    <span>{{ modelOptionLabel(model) }}</span>
                  </span>
                  <input v-model="channelModelDraft(newChannel, model).upstreamModel" class="input h-9 rounded-md text-xs" placeholder="上游模型 ID" />
                </label>
              </div>
            </div>
            <div class="grid gap-3 sm:grid-cols-2">
              <label class="block">
                <span class="text-sm font-black text-slate-800">优先级</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">数字越小越先被选择。</span>
                <input v-model.number="newChannel.priority" class="input mt-2 h-12 rounded-2xl" type="number" placeholder="10" />
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">权重</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">同优先级下的分流权重。</span>
                <input v-model.number="newChannel.weight" class="input mt-2 h-12 rounded-2xl" type="number" placeholder="10" />
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">RPM 限制</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">每分钟请求数上限，0 表示不限制。</span>
                <input v-model.number="newChannel.rpmLimit" class="input mt-2 h-12 rounded-2xl" type="number" placeholder="0" />
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">TPM 限制</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">每分钟 Token 上限，0 表示不限制。</span>
                <input v-model.number="newChannel.tpmLimit" class="input mt-2 h-12 rounded-2xl" type="number" placeholder="0" />
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">流式并发上限</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">该渠道同时向上游发起的流式请求数，0 表示使用默认值（8）。</span>
                <input v-model.number="newChannel.maxConcurrency" class="input mt-2 h-12 rounded-2xl" type="number" min="0" placeholder="0" />
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">渠道成本倍率</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">用于标记渠道成本和日志分析，不参与用户扣费。</span>
                <input v-model.number="newChannel.priceMultiplier" class="input mt-2 h-12 rounded-2xl" type="number" step="0.0001" placeholder="1" />
              </label>
              <label class="flex min-h-[76px] items-center gap-3 rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm font-black text-slate-700">
                <input v-model="newChannel.enabled" class="h-5 w-5 accent-sky-600" type="checkbox" />
                创建后立即启用
              </label>
            </div>
            <button class="btn-primary h-12 w-full rounded-2xl" :disabled="saving === 'channel-new'" @click="createChannel">
              {{ saving === 'channel-new' ? '创建中' : '创建渠道' }}
            </button>
          </div>
          </div>
        </details>

        <div class="space-y-4">
          <div class="panel p-4">
            <div class="grid gap-3 lg:grid-cols-[minmax(220px,1fr)_160px_auto]">
              <input v-model="channelSearch" class="input h-10 rounded-lg" placeholder="搜索渠道、供应商、URL 或分组" />
              <select v-model="channelStateFilter" class="input h-10 rounded-lg">
                <option value="all">全部渠道</option>
                <option value="enabled">仅启用</option>
                <option value="disabled">仅停用</option>
                <option value="failed">仅异常</option>
              </select>
              <button class="h-10 rounded-lg bg-slate-950 px-4 text-xs font-black text-white transition hover:bg-sky-600 disabled:opacity-60" :disabled="saving === 'channel-status-sync'" @click="syncChannelStatus">
                {{ saving === 'channel-status-sync' ? '检测中' : '检测渠道' }}
              </button>
            </div>
          </div>
          <details v-for="channel in filteredChannels" :key="channel.id" class="panel overflow-hidden">
            <summary class="grid cursor-pointer list-none gap-3 px-4 py-3 transition hover:bg-slate-50 lg:grid-cols-[minmax(220px,1fr)_160px_180px_120px] lg:items-center">
              <div class="min-w-0">
                <div class="flex items-center gap-2">
                  <span class="inline-flex rounded-md px-2 py-1 text-xs font-black ring-1" :class="providerBadgeClass()">{{ channel.provider || '未标记供应商' }}</span>
                  <span class="inline-flex rounded-md px-2 py-1 text-xs font-black ring-1" :class="ruleBadgeClass(channelDraftOf(channel).channelRule)">{{ ruleLabel(channelDraftOf(channel).channelRule) }}</span>
                  <span class="rounded-md px-2 py-1 text-xs font-black" :class="channel.status === 'failed' ? 'bg-red-50 text-red-600' : 'bg-emerald-50 text-emerald-700'">{{ channel.status }}</span>
                </div>
                <h3 class="mt-2 truncate text-base font-black text-slate-950">{{ channel.name }}</h3>
                <p class="mt-1 truncate text-xs font-semibold text-slate-500">{{ channel.apiBaseUrl }}</p>
              </div>
              <p class="text-xs font-black text-slate-600">{{ channelDraftOf(channel).groupNames || 'default' }}</p>
              <p class="text-xs font-semibold text-slate-500">P{{ channelDraftOf(channel).priority }} / W{{ channelDraftOf(channel).weight }} · {{ enabledChannelModelCount(channel) }} 模型</p>
              <label class="flex items-center gap-2 text-sm font-black text-slate-600">
                <input v-model="channelDraftOf(channel).enabled" class="h-5 w-5 accent-sky-600" type="checkbox" />
                启用
              </label>
            </summary>
            <div class="collapsible-body border-t border-slate-100 p-4">
            <div class="grid gap-3 lg:grid-cols-2">
              <label class="block">
                <span class="text-sm font-black text-slate-800">渠道名称</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">自由填写，用于后台识别。</span>
                <input v-model="channelDraftOf(channel).name" class="input mt-2 h-12 rounded-2xl" />
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">供应商</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">自由填写，只用于管理展示和后续排查。</span>
                <input v-model="channelDraftOf(channel).provider" class="input mt-2 h-12 rounded-2xl" placeholder="自定义供应商名称" />
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">规则</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">决定上游鉴权和请求格式。</span>
                <select v-model="channelDraftOf(channel).channelRule" class="input mt-2 h-12 rounded-2xl">
                  <option value="openai">OpenAI 兼容</option>
                  <option value="anthropic">Anthropic</option>
                </select>
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">Base URL</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">当前渠道转发到的上游服务地址。</span>
                <input v-model="channelDraftOf(channel).apiBaseUrl" class="input mt-2 h-12 rounded-2xl" />
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">上游 API Key</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">留空表示继续使用当前密钥。</span>
                <input v-model="channelDraftOf(channel).keyValue" class="input mt-2 h-12 rounded-2xl" type="password" placeholder="留空则不修改 Key" />
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">可用分组</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">逗号分隔，例如 default,vip；不匹配的分组不会选中该渠道。</span>
                <input v-model="channelDraftOf(channel).groupNames" class="input mt-2 h-12 rounded-2xl" />
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">用户备注</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">展示给用户看的注意事项，不会影响实际转发。</span>
                <textarea v-model="channelDraftOf(channel).remark" class="input mt-2 min-h-24 rounded-2xl py-3" placeholder="例如：该渠道仅支持图片模型，请勿用于聊天请求。"></textarea>
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">优先级</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">数字越小越优先。</span>
                <input v-model.number="channelDraftOf(channel).priority" class="input mt-2 h-12 rounded-2xl" type="number" />
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">权重</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">同优先级渠道之间的分流比例。</span>
                <input v-model.number="channelDraftOf(channel).weight" class="input mt-2 h-12 rounded-2xl" type="number" />
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">RPM 限制</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">每分钟最大请求数，0 表示不限制。</span>
                <input v-model.number="channelDraftOf(channel).rpmLimit" class="input mt-2 h-12 rounded-2xl" type="number" />
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">TPM 限制</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">每分钟最大 Token 数，0 表示不限制。</span>
                <input v-model.number="channelDraftOf(channel).tpmLimit" class="input mt-2 h-12 rounded-2xl" type="number" />
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">流式并发上限</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">该渠道同时向上游发起的流式请求数，0 表示使用默认值（8）。</span>
                <input v-model.number="channelDraftOf(channel).maxConcurrency" class="input mt-2 h-12 rounded-2xl" type="number" min="0" placeholder="0" />
              </label>
              <label class="block">
                <span class="text-sm font-black text-slate-800">渠道成本倍率</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">用于记录渠道成本差异，不参与用户扣费。</span>
                <input v-model.number="channelDraftOf(channel).priceMultiplier" class="input mt-2 h-12 rounded-2xl" type="number" step="0.0001" />
              </label>
            </div>
            <div class="mt-4 rounded-2xl border border-slate-100 bg-slate-50 p-4">
              <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p class="text-sm font-black text-slate-800">渠道模型绑定</p>
                  <p class="mt-1 text-xs font-semibold text-slate-500">已启用 {{ enabledChannelModelCount(channel) }} / {{ models.length }}，上游模型 ID 可与对外模型不同。</p>
                </div>
                <div class="flex flex-wrap gap-2">
                  <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-black text-slate-600 transition hover:border-sky-200 hover:text-sky-700" type="button" @click="enableChannelModelsByType(channelDraftOf(channel), 'chat')">聊天</button>
                  <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-black text-slate-600 transition hover:border-sky-200 hover:text-sky-700" type="button" @click="enableChannelModelsByType(channelDraftOf(channel), 'image')">图片</button>
                  <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-black text-slate-600 transition hover:border-sky-200 hover:text-sky-700" type="button" @click="selectAllChannelModels(channelDraftOf(channel))">全选</button>
                  <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-black text-slate-600 transition hover:border-red-200 hover:text-red-600" type="button" @click="clearChannelModels(channelDraftOf(channel))">清空</button>
                </div>
              </div>
              <div class="grid max-h-72 gap-2 overflow-y-auto lg:grid-cols-2">
                <label v-for="model in models" :key="model.id" class="grid gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700">
                  <span class="flex min-w-0 items-center gap-2">
                    <input v-model="channelModelDraft(channelDraftOf(channel), model).enabled" class="h-4 w-4 accent-sky-600" type="checkbox" />
                    <span class="truncate">{{ modelOptionLabel(model) }}</span>
                  </span>
                  <input v-model="channelModelDraft(channelDraftOf(channel), model).upstreamModel" class="input h-9 rounded-md text-xs" placeholder="上游模型 ID" />
                </label>
              </div>
            </div>
            <div class="mt-4 flex flex-wrap justify-end gap-3">
              <button class="h-12 rounded-2xl border border-red-200 bg-red-50 px-5 text-sm font-black text-red-600 transition hover:bg-red-100 disabled:opacity-60" :disabled="saving === `channel-delete-${channel.id}`" @click="deleteChannel(channel)">
                {{ saving === `channel-delete-${channel.id}` ? '删除中' : '删除渠道' }}
              </button>
              <button class="h-12 rounded-2xl bg-slate-950 px-5 text-sm font-black text-white transition hover:bg-sky-600 disabled:opacity-60" :disabled="saving === `channel-${channel.id}`" @click="saveChannel(channel)">
                {{ saving === `channel-${channel.id}` ? '保存中' : '保存渠道' }}
              </button>
            </div>
            </div>
          </details>
          <div v-if="!filteredChannels.length" class="panel p-10 text-center text-sm font-black text-slate-500">没有匹配的渠道</div>
        </div>
      </section>

      <section v-if="activeTab === 'models'" class="mt-6 space-y-5">
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <div class="panel p-4">
            <p class="text-xs font-black text-slate-500">模型总数</p>
            <p class="mt-2 text-2xl font-black text-slate-950">{{ models.length }}</p>
          </div>
          <div class="panel p-4">
            <p class="text-xs font-black text-slate-500">已启用</p>
            <p class="mt-2 text-2xl font-black text-emerald-600">{{ enabledModelsCount }}</p>
          </div>
          <div class="panel p-4">
            <p class="text-xs font-black text-slate-500">已定价</p>
            <p class="mt-2 text-2xl font-black text-sky-600">{{ pricedModelsCount }}</p>
          </div>
          <div class="panel p-4">
            <p class="text-xs font-black text-slate-500">上游结果</p>
            <p class="mt-2 text-2xl font-black text-slate-950">{{ upstreamModels.length }}</p>
          </div>
        </div>

        <details class="panel overflow-hidden" :open="expandedModelSync">
          <summary class="flex cursor-pointer list-none flex-wrap items-center justify-between gap-3 px-4 py-3 transition hover:bg-slate-50" @click="expandedModelSync = !expandedModelSync">
            <div class="min-w-0">
              <h2 class="text-base font-black text-slate-950">上游同步</h2>
              <p class="mt-1 text-xs font-semibold text-slate-500">{{ channels.length }} 个渠道 · {{ upstreamModels.length }} 个上游结果 · 已选 {{ selectedUpstreamIds.length }}</p>
            </div>
            <button class="h-10 rounded-lg bg-slate-950 px-4 text-xs font-black text-white transition hover:bg-sky-600 disabled:opacity-60" :disabled="saving === 'model-import' || !selectedUpstreamIds.length" @click="enableSelectedUpstreamModels">
              {{ saving === 'model-import' ? '启用中' : `启用所选 ${selectedUpstreamIds.length}` }}
            </button>
          </summary>
          <div class="border-t border-slate-100 p-4">
            <p class="text-sm font-semibold text-slate-500">读取渠道 /v1/models 后，可批量写入模型配置。</p>
          </div>
          <div class="mt-4 flex gap-2 overflow-x-auto pb-1">
            <button
              v-for="channel in channels"
              :key="channel.id"
              class="min-w-[190px] rounded-lg border px-3 py-2 text-left text-sm font-black transition"
              :class="syncingChannelId === channel.id ? 'border-sky-300 bg-sky-50 text-sky-700' : 'border-slate-200 bg-white text-slate-700 hover:border-sky-200 hover:bg-sky-50'"
              :disabled="syncingChannelId === channel.id"
              @click="syncModels(channel)"
            >
              <span class="block truncate">{{ channel.name }}</span>
              <span class="mt-1 block truncate text-xs font-semibold text-slate-500">{{ syncingChannelId === channel.id ? '同步中' : channel.apiBaseUrl }}</span>
            </button>
          </div>
          <div v-if="upstreamModels.length" class="mt-4 flex gap-2 overflow-x-auto pb-1">
            <label v-for="item in upstreamModels" :key="item.id" class="flex min-w-[220px] items-center gap-3 rounded-lg border border-slate-200 bg-white px-3 py-2">
              <input v-model="selectedUpstreamIds" class="h-4 w-4 accent-sky-600" type="checkbox" :value="item.id" :disabled="item.configured" />
              <button class="min-w-0 flex-1 text-left" type="button" @click="pickUpstreamModel(item)">
                <span class="block truncate text-sm font-black text-slate-800">{{ item.id }}</span>
                <span class="mt-1 block text-xs font-semibold" :class="item.configured ? 'text-emerald-600' : 'text-slate-500'">{{ item.configured ? '已配置' : (item.ownedBy || '未标记供应商') }}</span>
              </button>
            </label>
          </div>
        </details>

        <div class="space-y-3">
          <div class="panel overflow-hidden">
            <div class="border-b border-slate-200 bg-white p-4">
              <div class="flex flex-wrap items-center justify-between gap-3">
                <h2 class="text-xl font-black text-slate-950">模型配置</h2>
                <button class="h-10 rounded-lg bg-slate-950 px-4 text-xs font-black text-white transition hover:bg-sky-600" @click="newModelEditor">
                  新增模型
                </button>
              </div>
              <div class="mt-4 grid gap-3 lg:grid-cols-[minmax(220px,1fr)_150px_150px]">
                <input v-model="modelSearch" class="input h-10 rounded-lg" placeholder="搜索模型 ID 或显示名称" />
                <select v-model="modelTypeFilter" class="input h-10 rounded-lg">
                  <option value="all">全部类型</option>
                  <option v-for="type in modelTypes" :key="type" :value="type">{{ type }}</option>
                </select>
                <select v-model="modelStateFilter" class="input h-10 rounded-lg">
                  <option value="all">全部状态</option>
                  <option value="enabled">仅启用</option>
                  <option value="disabled">仅停用</option>
                </select>
              </div>
            </div>

            <div class="overflow-x-auto">
              <table class="min-w-[980px] w-full text-left text-sm">
                <thead class="bg-slate-50 text-xs font-black uppercase text-slate-500">
                  <tr>
                    <th class="px-4 py-3">模型</th>
                    <th class="px-4 py-3">类型</th>
                    <th class="px-4 py-3">价格</th>
                    <th class="px-4 py-3">排序</th>
                    <th class="px-4 py-3">状态</th>
                    <th class="px-4 py-3 text-right">操作</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-100 bg-white">
                  <tr
                    v-for="model in filteredModels"
                    :key="model.id"
                    class="cursor-pointer transition hover:bg-sky-50/70"
                    :class="editingModelId === model.id ? 'bg-sky-50' : ''"
                    @click="editModel(model)"
                  >
                    <td class="px-4 py-3">
                      <p class="max-w-[320px] truncate font-black text-slate-950">{{ modelDraftOf(model).displayName || modelDraftOf(model).model }}</p>
                      <p class="mt-1 max-w-[320px] truncate font-mono text-xs font-semibold text-slate-500">{{ modelDraftOf(model).model }}</p>
                    </td>
                    <td class="px-4 py-3">
                      <span class="rounded-md bg-slate-100 px-2 py-1 text-xs font-black text-slate-700">{{ modelDraftOf(model).modelType }}</span>
                    </td>
                    <td class="px-4 py-3">
                      <p class="font-black text-slate-800">￥{{ modelTotalPrice(modelDraftOf(model)).toFixed(4) }}</p>
                      <p class="mt-1 text-xs font-semibold text-slate-500">in {{ modelDraftOf(model).inputPrice }} / out {{ modelDraftOf(model).outputPrice }}</p>
                    </td>
                    <td class="px-4 py-3 font-black text-slate-700">{{ modelDraftOf(model).sortOrder }}</td>
                    <td class="px-4 py-3">
                      <div class="flex flex-wrap items-center gap-2">
                        <span class="rounded-md px-2 py-1 text-xs font-black" :class="modelDraftOf(model).enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'">
                          {{ modelDraftOf(model).enabled ? '启用' : '停用' }}
                        </span>
                        <span class="rounded-md bg-white px-2 py-1 text-xs font-black text-slate-500 ring-1 ring-slate-200">{{ modelDraftOf(model).status }}</span>
                      </div>
                    </td>
                    <td class="px-4 py-3 text-right">
                      <button class="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 transition hover:border-sky-200 hover:text-sky-700" type="button" @click.stop="editModel(model)">
                        编辑
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-if="!filteredModels.length" class="border-t border-slate-100 p-10 text-center text-sm font-black text-slate-500">没有匹配的模型</div>
          </div>

          <details class="panel overflow-hidden" :open="editingModelId !== null">
            <summary class="flex cursor-pointer list-none flex-wrap items-center justify-between gap-3 px-4 py-3 transition hover:bg-slate-50">
              <div class="min-w-0">
                <h2 class="text-base font-black text-slate-950">
                  {{ editingModelId === 'new' ? '新增模型' : (editingModel ? (modelDraftOf(editingModel).displayName || modelDraftOf(editingModel).model) : '模型编辑') }}
                </h2>
                <p class="mt-1 truncate text-xs font-semibold text-slate-500">
                  {{ editingModelId === 'new' ? `${newModel.modelType} · ￥${modelTotalPrice(newModel).toFixed(4)}` : (editingModel ? `${modelDraftOf(editingModel).modelType} · ￥${modelTotalPrice(modelDraftOf(editingModel)).toFixed(4)}` : '从列表选择一个模型后编辑') }}
                </p>
              </div>
              <button class="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-600 transition hover:border-sky-200 hover:text-sky-700" type="button" @click.stop="newModelEditor">
                新增模型
              </button>
            </summary>
            <div class="border-t border-slate-100 p-4">
            <template v-if="editingModelId === 'new'">
              <div class="flex items-start justify-between gap-3">
                <div>
                  <p class="text-xs font-black uppercase tracking-[0.18em] text-sky-600">New</p>
                  <h2 class="mt-1 text-xl font-black text-slate-950">新增模型</h2>
                </div>
                <label class="flex items-center gap-2 text-sm font-black text-slate-600">
                  <input v-model="newModel.enabled" class="h-4 w-4 accent-sky-600" type="checkbox" />
                  启用
                </label>
              </div>
              <div class="mt-5 space-y-4">
                <label class="block">
                  <span class="text-xs font-black text-slate-700">模型 ID（发送给上游）</span>
                  <input v-model="newModel.model" class="input mt-2 h-10 rounded-lg" placeholder="gpt-4o" />
                </label>
                <label class="block">
                  <span class="text-xs font-black text-slate-700">显示名称（对外模型名）</span>
                  <input v-model="newModel.displayName" class="input mt-2 h-10 rounded-lg" placeholder="GPT-4o" />
                </label>
                <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">类型</span>
                    <input v-model="newModel.modelType" class="input mt-2 h-10 rounded-lg" placeholder="chat" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">状态</span>
                    <input v-model="newModel.status" class="input mt-2 h-10 rounded-lg" placeholder="available" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">输入价格</span>
                    <input v-model.number="newModel.inputPrice" class="input mt-2 h-10 rounded-lg" type="number" step="0.0001" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">输出价格</span>
                    <input v-model.number="newModel.outputPrice" class="input mt-2 h-10 rounded-lg" type="number" step="0.0001" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">缓存读入</span>
                    <input v-model.number="newModel.cachedInputPrice" class="input mt-2 h-10 rounded-lg" type="number" step="0.0001" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">缓存创建</span>
                    <input v-model.number="newModel.cacheCreationPrice" class="input mt-2 h-10 rounded-lg" type="number" step="0.0001" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">每请求价格</span>
                    <input v-model.number="newModel.requestPrice" class="input mt-2 h-10 rounded-lg" type="number" step="0.0001" />
                  </label>
                  <label class="flex min-h-[66px] items-center gap-3 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700">
                    <input v-model="newModel.fixedRequestBilling" class="h-4 w-4 accent-sky-600" type="checkbox" />
                    <span>
                      <span class="block">一次性扣费</span>
                      <span class="mt-1 block font-semibold text-slate-500">开启后每请求价格为最终扣费</span>
                    </span>
                  </label>
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">排序</span>
                    <input v-model.number="newModel.sortOrder" class="input mt-2 h-10 rounded-lg" type="number" />
                  </label>
                </div>
                <button class="btn-primary h-11 w-full rounded-lg" :disabled="saving === 'model-new'" @click="createModel">
                  {{ saving === 'model-new' ? '创建中' : '创建模型' }}
                </button>
              </div>
            </template>

            <template v-else-if="editingModel">
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="text-xs font-black uppercase tracking-[0.18em] text-sky-600">{{ modelDraftOf(editingModel).modelType }}</p>
                  <h2 class="mt-1 truncate text-xl font-black text-slate-950">{{ modelDraftOf(editingModel).displayName || modelDraftOf(editingModel).model }}</h2>
                  <p class="mt-1 truncate font-mono text-xs font-semibold text-slate-500">{{ modelDraftOf(editingModel).model }}</p>
                </div>
                <label class="flex items-center gap-2 text-sm font-black text-slate-600">
                  <input v-model="modelDraftOf(editingModel).enabled" class="h-4 w-4 accent-sky-600" type="checkbox" />
                  启用
                </label>
              </div>
              <div class="mt-5 space-y-4">
                <label class="block">
                  <span class="text-xs font-black text-slate-700">模型 ID（发送给上游）</span>
                  <input v-model="modelDraftOf(editingModel).model" class="input mt-2 h-10 rounded-lg" />
                </label>
                <label class="block">
                  <span class="text-xs font-black text-slate-700">显示名称（对外模型名）</span>
                  <input v-model="modelDraftOf(editingModel).displayName" class="input mt-2 h-10 rounded-lg" />
                </label>
                <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">类型</span>
                    <input v-model="modelDraftOf(editingModel).modelType" class="input mt-2 h-10 rounded-lg" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">状态</span>
                    <input v-model="modelDraftOf(editingModel).status" class="input mt-2 h-10 rounded-lg" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">输入价格</span>
                    <input v-model.number="modelDraftOf(editingModel).inputPrice" class="input mt-2 h-10 rounded-lg" type="number" step="0.0001" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">输出价格</span>
                    <input v-model.number="modelDraftOf(editingModel).outputPrice" class="input mt-2 h-10 rounded-lg" type="number" step="0.0001" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">缓存读入</span>
                    <input v-model.number="modelDraftOf(editingModel).cachedInputPrice" class="input mt-2 h-10 rounded-lg" type="number" step="0.0001" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">缓存创建</span>
                    <input v-model.number="modelDraftOf(editingModel).cacheCreationPrice" class="input mt-2 h-10 rounded-lg" type="number" step="0.0001" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">每请求价格</span>
                    <input v-model.number="modelDraftOf(editingModel).requestPrice" class="input mt-2 h-10 rounded-lg" type="number" step="0.0001" />
                  </label>
                  <label class="flex min-h-[66px] items-center gap-3 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700">
                    <input v-model="modelDraftOf(editingModel).fixedRequestBilling" class="h-4 w-4 accent-sky-600" type="checkbox" />
                    <span>
                      <span class="block">一次性扣费</span>
                      <span class="mt-1 block font-semibold text-slate-500">开启后每请求价格为最终扣费</span>
                    </span>
                  </label>
                  <label class="block">
                    <span class="text-xs font-black text-slate-700">排序</span>
                    <input v-model.number="modelDraftOf(editingModel).sortOrder" class="input mt-2 h-10 rounded-lg" type="number" />
                  </label>
                </div>
                <div class="grid gap-3 sm:grid-cols-2">
                  <button class="h-11 rounded-lg bg-slate-950 px-4 text-xs font-black text-white transition hover:bg-sky-600 disabled:opacity-60" :disabled="saving === `model-${editingModel.id}`" @click="saveModel(editingModel)">
                    {{ saving === `model-${editingModel.id}` ? '保存中' : '保存模型' }}
                  </button>
                  <button class="h-11 rounded-lg border border-red-200 bg-red-50 px-4 text-xs font-black text-red-600 transition hover:bg-red-100 disabled:opacity-60" :disabled="saving === `model-delete-${editingModel.id}`" @click="deleteModel(editingModel)">
                    {{ saving === `model-delete-${editingModel.id}` ? '删除中' : '删除模型' }}
                  </button>
                </div>
              </div>
            </template>

            <div v-else class="py-12 text-center">
              <p class="text-base font-black text-slate-700">选择模型开始编辑</p>
              <button class="mt-4 h-10 rounded-lg bg-slate-950 px-4 text-xs font-black text-white transition hover:bg-sky-600" @click="newModelEditor">
                新增模型
              </button>
            </div>
            </div>
          </details>
        </div>
      </section>

      <section v-if="activeTab === 'tokens'" class="mt-6 rounded-[28px] border border-white/80 bg-white/86 p-5 shadow-[0_24px_80px_rgba(15,23,42,0.08)]">
        <h2 class="text-2xl font-black">用户令牌</h2>
        <div class="mt-5 grid gap-3">
          <article v-for="token in tokens" :key="token.id" class="grid gap-3 rounded-2xl border border-slate-100 bg-slate-50 p-4 lg:grid-cols-[1fr_1fr_120px_120px_90px] lg:items-center">
            <div><p class="font-black">{{ token.name }}</p><p class="text-xs font-semibold text-slate-500">{{ token.username }} · {{ token.tokenPreview }}</p></div>
            <p class="text-xs font-semibold text-slate-500">{{ token.allowedModels || '全部模型' }}</p>
            <p class="text-sm font-black">{{ token.requestCount || 0 }} 次</p>
            <p class="text-sm font-black">{{ compactToken(token.tokenCount) }} tokens</p>
            <p class="text-xs font-black" :class="token.enabled ? 'text-emerald-600' : 'text-slate-400'">{{ token.enabled ? '启用' : '停用' }}</p>
          </article>
          <div v-if="!tokens.length" class="rounded-2xl border border-dashed border-slate-200 p-10 text-center text-sm font-black text-slate-500">暂无令牌</div>
        </div>
      </section>

      <section v-if="activeTab === 'usage'" class="mt-6 grid gap-4 sm:grid-cols-3">
        <div class="panel p-5"><p class="text-sm font-bold text-slate-500">请求数</p><p class="mt-2 text-3xl font-black">{{ stats?.totalRequests || 0 }}</p></div>
        <div class="panel p-5"><p class="text-sm font-bold text-slate-500">Token 用量</p><p class="mt-2 text-3xl font-black">{{ compactToken(stats?.totalTokensUsed) }}</p></div>
        <div class="panel p-5"><p class="text-sm font-bold text-slate-500">成本</p><p class="mt-2 text-3xl font-black text-sky-600">￥{{ Number(stats?.totalCost || 0).toFixed(4) }}</p></div>
      </section>

      <section v-if="activeTab === 'policy'" class="mt-6 space-y-3">
        <details class="panel overflow-hidden">
          <summary class="flex cursor-pointer list-none flex-wrap items-center justify-between gap-3 px-4 py-3 transition hover:bg-slate-50">
            <div>
              <h2 class="text-base font-black text-slate-950">新增分组</h2>
              <p class="mt-1 text-xs font-semibold text-slate-500">{{ newGroup.code }} · 倍率 {{ newGroup.ratio }} · {{ selectedGroupModelCount(newGroup) }} 个模型</p>
            </div>
            <span class="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-600">展开设置</span>
          </summary>
          <div class="border-t border-slate-100 p-4">
            <div class="grid gap-3 md:grid-cols-3">
              <label class="block">
                <span class="text-xs font-black text-slate-700">代码</span>
                <input v-model="newGroup.code" class="input mt-2 h-10 rounded-lg" placeholder="vip" />
              </label>
              <label class="block">
                <span class="text-xs font-black text-slate-700">名称</span>
                <input v-model="newGroup.name" class="input mt-2 h-10 rounded-lg" placeholder="VIP 分组" />
              </label>
              <label class="block">
                <span class="text-xs font-black text-slate-700">倍率</span>
                <input v-model.number="newGroup.ratio" class="input mt-2 h-10 rounded-lg" type="number" step="0.0001" />
              </label>
              <label class="flex items-center gap-2 text-sm font-black text-slate-600">
                <input v-model="newGroup.enabled" class="h-4 w-4 accent-sky-600" type="checkbox" />
                创建后启用
              </label>
            </div>
              <div>
                <div class="mb-2 mt-4 flex items-center justify-between gap-3">
                  <span class="text-xs font-black text-slate-700">模型范围 {{ selectedGroupModelCount(newGroup) }} / {{ models.length }}</span>
                  <div class="flex gap-2">
                    <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-black text-slate-600 transition hover:border-sky-200 hover:text-sky-700" type="button" @click="selectAllGroupModels(newGroup)">全选</button>
                    <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-black text-slate-600 transition hover:border-red-200 hover:text-red-600" type="button" @click="clearGroupModels(newGroup)">清空</button>
                  </div>
                </div>
                <div class="flex max-h-40 flex-wrap gap-2 overflow-y-auto rounded-lg border border-slate-100 bg-slate-50 p-2">
                  <button
                    v-for="model in models"
                    :key="model.id"
                    class="rounded-md px-2 py-1 text-xs font-black transition"
                    :class="isGroupModelSelected(newGroup, model.id) ? 'bg-sky-600 text-white' : 'bg-white text-slate-600 ring-1 ring-slate-200 hover:text-sky-700'"
                    type="button"
                    @click="toggleGroupModel(newGroup, model.id)"
                  >
                    {{ modelOptionLabel(model) }}
                  </button>
                </div>
              </div>
              <button class="btn-primary h-11 w-full rounded-lg" :disabled="saving === 'group-new'" @click="createGroup">
                {{ saving === 'group-new' ? '创建中' : '创建分组' }}
              </button>
          </div>
        </details>

        <details v-for="group in groups" :key="group.id" class="panel overflow-hidden">
          <summary class="grid cursor-pointer list-none gap-3 px-4 py-3 transition hover:bg-slate-50 md:grid-cols-[1fr_120px_120px_120px] md:items-center">
            <div class="min-w-0">
              <h3 class="truncate text-base font-black text-slate-950">{{ groupDraftOf(group).code }} · {{ groupDraftOf(group).name }}</h3>
              <p class="mt-1 text-xs font-semibold text-slate-500">{{ selectedGroupModelCount(groupDraftOf(group)) }} / {{ models.length }} 个模型</p>
            </div>
            <p class="text-xs font-black text-slate-600">倍率 {{ groupDraftOf(group).ratio }}</p>
            <span class="rounded-md px-2 py-1 text-center text-xs font-black" :class="groupDraftOf(group).enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'">{{ groupDraftOf(group).enabled ? '启用' : '停用' }}</span>
            <span class="text-right text-xs font-black text-slate-500">展开设置</span>
          </summary>
          <div class="border-t border-slate-100 p-4">
            <div class="grid gap-3 md:grid-cols-4">
              <label class="block">
                <span class="text-xs font-black text-slate-700">代码</span>
                <input v-model="groupDraftOf(group).code" class="input mt-2 h-10 rounded-lg" />
              </label>
              <label class="block">
                <span class="text-xs font-black text-slate-700">名称</span>
                <input v-model="groupDraftOf(group).name" class="input mt-2 h-10 rounded-lg" />
              </label>
              <label class="block">
                <span class="text-xs font-black text-slate-700">倍率</span>
                <input v-model.number="groupDraftOf(group).ratio" class="input mt-2 h-10 rounded-lg" type="number" step="0.0001" />
              </label>
              <label class="flex items-end gap-2 pb-2 text-sm font-black text-slate-600">
                <input v-model="groupDraftOf(group).enabled" class="h-4 w-4 accent-sky-600" type="checkbox" />
                启用
              </label>
            </div>
            <div class="mt-4">
              <div class="mb-2 flex items-center justify-between gap-3">
                <span class="text-xs font-black text-slate-500">模型范围 {{ selectedGroupModelCount(groupDraftOf(group)) }} / {{ models.length }}</span>
                <div class="flex gap-2">
                  <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-black text-slate-600 transition hover:border-sky-200 hover:text-sky-700" type="button" @click="selectAllGroupModels(groupDraftOf(group))">全选</button>
                  <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-black text-slate-600 transition hover:border-red-200 hover:text-red-600" type="button" @click="clearGroupModels(groupDraftOf(group))">清空</button>
                </div>
              </div>
              <div class="flex max-h-40 flex-wrap gap-2 overflow-y-auto rounded-lg border border-slate-100 bg-slate-50 p-2">
                <button
                  v-for="model in models"
                  :key="model.id"
                  class="rounded-md px-2 py-1 text-xs font-black transition"
                  :class="isGroupModelSelected(groupDraftOf(group), model.id) ? 'bg-sky-600 text-white' : 'bg-white text-slate-600 ring-1 ring-slate-200 hover:text-sky-700'"
                  type="button"
                  @click="toggleGroupModel(groupDraftOf(group), model.id)"
                >
                  {{ modelOptionLabel(model) }}
                </button>
              </div>
            </div>
            <div class="mt-4 flex justify-end gap-2">
              <button class="rounded-lg bg-slate-950 px-3 py-2 text-xs font-black text-white transition hover:bg-sky-600 disabled:opacity-60" :disabled="saving === `group-${group.id}`" @click="saveGroup(group)">
                {{ saving === `group-${group.id}` ? '保存中' : '保存' }}
              </button>
              <button class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs font-black text-red-600 transition hover:bg-red-100 disabled:opacity-60" :disabled="group.code === 'default' || saving === `group-delete-${group.id}`" @click="deleteGroup(group)">
                删除
              </button>
            </div>
          </div>
        </details>

          <div class="panel p-4">
            <h3 class="font-black text-slate-950">规则摘要</h3>
            <div class="mt-3 grid gap-2 text-sm font-semibold text-slate-600 md:grid-cols-3">
              <p>渠道只负责上游连接、成本倍率、优先级、权重和限流。</p>
              <p>渠道选择：先取最小优先级，再按权重随机分发。</p>
              <p>限制策略：令牌和渠道的 RPM/TPM 都会参与拦截；余额与 Key 额度按最终扣费校验。</p>
            </div>
          </div>
      </section>
    </div>
  </AppLayout>
</template>
