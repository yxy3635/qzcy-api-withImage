<script setup lang="ts">
import { computed, ref } from 'vue'
import RelayDrawer from './RelayDrawer.vue'
import type { RelayModel } from '@/types'
import type { GroupDraft } from '@/types/relayAdmin'

const props = withDefaults(defineProps<{
  isNew: boolean
  draft: GroupDraft
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

const modelSearch = ref('')
const modelTypeFilter = ref('all')

const modelTypes = computed(() => Array.from(new Set(props.models.map((model) => model.modelType).filter(Boolean))))

function modelOptionLabel(model: RelayModel) {
  const duplicate = props.models.some((item) => item.id !== model.id && item.model === model.model)
  if (!duplicate) return model.model
  const displayName = (model.displayName || '').trim()
  return displayName && displayName !== model.model ? `${model.model} · ${displayName} · #${model.id}` : `${model.model} · #${model.id}`
}

const filteredModels = computed(() => {
  const keyword = modelSearch.value.trim().toLowerCase()
  return props.models.filter((model) => {
    const matchesKeyword = !keyword || modelOptionLabel(model).toLowerCase().includes(keyword)
    const matchesType = modelTypeFilter.value === 'all' || model.modelType === modelTypeFilter.value
    return matchesKeyword && matchesType
  })
})

function isSelected(model: RelayModel) {
  return props.draft.modelIds.includes(model.id)
}

function toggleModel(model: RelayModel) {
  props.draft.modelIds = isSelected(model)
    ? props.draft.modelIds.filter((id) => id !== model.id)
    : [...props.draft.modelIds, model.id]
}

function setAllFiltered(enabled: boolean) {
  const filteredIds = new Set(filteredModels.value.map((model) => model.id))
  props.draft.modelIds = enabled
    ? Array.from(new Set([...props.draft.modelIds, ...filteredIds]))
    : props.draft.modelIds.filter((id) => !filteredIds.has(id))
}
</script>

<template>
  <RelayDrawer
    eyebrow="Group policy"
    :title="isNew ? '新增分组' : '编辑分组'"
    :subtitle="`${draft.code || '新分组'} · ${draft.name || ''}`"
    @close="emit('cancel')"
  >
    <div class="space-y-5">
      <section class="grid gap-3 sm:grid-cols-2">
        <label class="block">
          <span class="text-xs font-black text-slate-600">代码</span>
          <input v-model="draft.code" class="input mt-1 h-10 rounded-lg text-sm" placeholder="vip" :disabled="!isNew && draft.code === 'default'" />
          <span class="mt-1 block text-[11px] font-semibold text-slate-400">{{ draft.code === 'default' ? '默认分组代码不可修改。' : '令牌通过代码绑定分组。' }}</span>
        </label>
        <label class="block">
          <span class="text-xs font-black text-slate-600">名称</span>
          <input v-model="draft.name" class="input mt-1 h-10 rounded-lg text-sm" placeholder="VIP 分组" />
        </label>
        <label class="block">
          <span class="text-xs font-black text-slate-600">分组倍率</span>
          <input v-model.number="draft.ratio" class="input mt-1 h-10 rounded-lg text-sm" type="number" step="0.0001" />
          <span class="mt-1 block text-[11px] font-semibold text-slate-400">乘在模型价格之上，作为该分组用户的最终扣费。</span>
        </label>
        <label class="flex items-end gap-2 pb-1 text-sm font-black text-slate-700">
          <input v-model="draft.enabled" class="h-4 w-4 accent-sky-600" type="checkbox" />
          启用分组
        </label>
      </section>

      <section>
        <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
          <h3 class="text-xs font-black uppercase tracking-[0.14em] text-slate-400">模型范围（已选 {{ draft.modelIds.length }}/{{ models.length }}）</h3>
          <div class="flex gap-1.5">
            <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-[11px] font-black text-slate-600 transition hover:border-sky-200 hover:text-sky-700" type="button" @click="setAllFiltered(true)">全选当前</button>
            <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-[11px] font-black text-slate-600 transition hover:border-red-200 hover:text-red-600" type="button" @click="setAllFiltered(false)">移除当前</button>
            <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-[11px] font-black text-slate-600 transition hover:border-sky-200 hover:text-sky-700" type="button" @click="draft.modelIds = models.map((model) => model.id)">全选</button>
            <button class="rounded-md border border-slate-200 bg-white px-2 py-1 text-[11px] font-black text-slate-600 transition hover:border-red-200 hover:text-red-600" type="button" @click="draft.modelIds = []">清空</button>
          </div>
        </div>
        <div class="mb-2 grid gap-2 sm:grid-cols-[1fr_120px]">
          <input v-model="modelSearch" class="input h-9 rounded-lg text-sm" placeholder="搜索模型…" />
          <select v-model="modelTypeFilter" class="input h-9 rounded-lg text-sm">
            <option value="all">全部类型</option>
            <option v-for="type in modelTypes" :key="type" :value="type">{{ type }}</option>
          </select>
        </div>
        <div class="flex max-h-80 flex-wrap gap-2 overflow-y-auto rounded-xl border border-slate-100 bg-slate-50 p-2">
          <button
            v-for="model in filteredModels"
            :key="model.id"
            class="rounded-md px-2 py-1 text-xs font-black transition"
            :class="isSelected(model) ? 'bg-sky-600 text-white' : 'bg-white text-slate-600 ring-1 ring-slate-200 hover:text-sky-700'"
            type="button"
            @click="toggleModel(model)"
          >{{ modelOptionLabel(model) }}</button>
          <p v-if="!filteredModels.length" class="w-full py-6 text-center text-xs font-black text-slate-400">没有匹配的模型</p>
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
          {{ saving ? '保存中…' : (isNew ? '创建分组' : '保存分组') }}
        </button>
      </div>
    </template>
  </RelayDrawer>
</template>
