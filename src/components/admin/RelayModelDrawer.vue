<script setup lang="ts">
import { ref } from 'vue'
import RelayDrawer from './RelayDrawer.vue'
import type { ModelDraft } from '@/types/relayAdmin'

const props = withDefaults(defineProps<{
  isNew: boolean
  draft: ModelDraft
  saving?: boolean
  deleting?: boolean
  canDelete?: boolean
}>(), {
  saving: false,
  deleting: false,
  canDelete: true
})

const emit = defineEmits<{ save: []; cancel: []; delete: [] }>()

const advancedBillingOpen = ref(false)

const modelTypeOptions = ['chat', 'image', 'embedding', 'audio', 'code']
</script>

<template>
  <RelayDrawer
    eyebrow="Model configuration"
    :title="isNew ? '新增模型' : '编辑模型'"
    :subtitle="draft.displayName || draft.model || '新模型'"
    @close="emit('cancel')"
  >
    <div class="space-y-5">
      <!-- 基本信息 -->
      <section class="space-y-3">
        <h3 class="text-xs font-black uppercase tracking-[0.14em] text-slate-400">基本信息</h3>
        <label class="block">
          <span class="text-xs font-black text-slate-600">模型 ID（发送给上游）</span>
          <input v-model="draft.model" class="input mt-1 h-10 rounded-lg text-sm" placeholder="gpt-4o" />
        </label>
        <label class="block">
          <span class="text-xs font-black text-slate-600">显示名称（对外模型名）</span>
          <input v-model="draft.displayName" class="input mt-1 h-10 rounded-lg text-sm" placeholder="GPT-4o" />
        </label>
        <div class="grid gap-3 sm:grid-cols-2">
          <label class="block">
            <span class="text-xs font-black text-slate-600">类型</span>
            <input v-model="draft.modelType" class="input mt-1 h-10 rounded-lg text-sm" list="relay-model-type-options" placeholder="chat" />
            <datalist id="relay-model-type-options">
              <option v-for="type in modelTypeOptions" :key="type" :value="type" />
            </datalist>
          </label>
          <label class="block">
            <span class="text-xs font-black text-slate-600">排序</span>
            <input v-model.number="draft.sortOrder" class="input mt-1 h-10 rounded-lg text-sm" type="number" />
          </label>
          <label class="block">
            <span class="text-xs font-black text-slate-600">状态</span>
            <input v-model="draft.status" class="input mt-1 h-10 rounded-lg text-sm" placeholder="available" />
          </label>
          <label class="flex items-end gap-2 pb-1 text-sm font-black text-slate-700">
            <input v-model="draft.enabled" class="h-4 w-4 accent-sky-600" type="checkbox" />
            启用模型
          </label>
        </div>
      </section>

      <!-- 价格 -->
      <section>
        <h3 class="text-xs font-black uppercase tracking-[0.14em] text-slate-400">价格（每 1M Token）</h3>
        <div class="mt-3 grid gap-3 sm:grid-cols-2">
          <label class="block">
            <span class="text-xs font-black text-slate-600">输入</span>
            <input v-model.number="draft.inputPrice" class="input mt-1 h-10 rounded-lg text-sm" type="number" step="0.0001" />
          </label>
          <label class="block">
            <span class="text-xs font-black text-slate-600">输出</span>
            <input v-model.number="draft.outputPrice" class="input mt-1 h-10 rounded-lg text-sm" type="number" step="0.0001" />
          </label>
          <label class="block">
            <span class="text-xs font-black text-slate-600">缓存读入</span>
            <input v-model.number="draft.cachedInputPrice" class="input mt-1 h-10 rounded-lg text-sm" type="number" step="0.0001" />
          </label>
          <label class="block">
            <span class="text-xs font-black text-slate-600">缓存创建</span>
            <input v-model.number="draft.cacheCreationPrice" class="input mt-1 h-10 rounded-lg text-sm" type="number" step="0.0001" />
          </label>
          <label class="block">
            <span class="text-xs font-black text-slate-600">每请求价格</span>
            <input v-model.number="draft.requestPrice" class="input mt-1 h-10 rounded-lg text-sm" type="number" step="0.0001" />
          </label>
          <label class="flex items-start gap-2 self-end rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-xs font-black text-slate-700">
            <input v-model="draft.fixedRequestBilling" class="mt-0.5 h-4 w-4 accent-sky-600" type="checkbox" />
            <span>
              <span class="block">一次性扣费</span>
              <span class="mt-0.5 block font-semibold text-slate-400">开启后每请求价格为最终扣费</span>
            </span>
          </label>
        </div>
      </section>

      <!-- 高级计费（默认折叠） -->
      <section>
        <button class="flex w-full items-center justify-between rounded-lg bg-slate-50 px-3 py-2 text-xs font-black uppercase tracking-[0.14em] text-slate-500 transition hover:bg-slate-100" type="button" @click="advancedBillingOpen = !advancedBillingOpen">
          长上下文计费{{ draft.longContextThreshold > 0 ? ' · 已开启' : '' }}
          <span>{{ advancedBillingOpen ? '收起' : '展开' }}</span>
        </button>
        <div v-if="advancedBillingOpen" class="mt-3 space-y-3 rounded-xl border border-slate-100 bg-slate-50 p-3">
          <p class="text-[11px] font-semibold text-slate-400">输入 Token 达到阈值后切换长上下文计费；0 表示关闭。</p>
          <div class="grid gap-3 sm:grid-cols-2">
            <label class="block">
              <span class="text-xs font-black text-slate-600">长上下文起算 Token</span>
              <input v-model.number="draft.longContextThreshold" class="input mt-1 h-10 rounded-lg text-sm" type="number" min="0" step="1" placeholder="例如 272000；0 关闭" />
            </label>
            <label class="block">
              <span class="text-xs font-black text-slate-600">计费方式</span>
              <select v-model="draft.longContextBillingMode" class="input mt-1 h-10 rounded-lg text-sm">
                <option value="price">单独设置价格</option>
                <option value="multiplier">按普通价格倍数</option>
              </select>
            </label>
          </div>
          <label v-if="draft.longContextBillingMode === 'multiplier'" class="block">
            <span class="text-xs font-black text-slate-600">长上下文价格倍数</span>
            <input v-model.number="draft.longContextMultiplier" class="input mt-1 h-10 rounded-lg text-sm" type="number" min="0.000001" step="0.1" placeholder="例如 2，表示普通价格 × 2" />
          </label>
          <template v-else>
            <div class="grid gap-3 sm:grid-cols-2">
              <label class="block">
                <span class="text-xs font-black text-slate-600">长上下文输入 / 1M</span>
                <input v-model.number="draft.longContextInputPrice" class="input mt-1 h-10 rounded-lg text-sm" type="number" min="0" step="0.0001" placeholder="留空沿用普通输入价" />
              </label>
              <label class="block">
                <span class="text-xs font-black text-slate-600">长上下文输出 / 1M</span>
                <input v-model.number="draft.longContextOutputPrice" class="input mt-1 h-10 rounded-lg text-sm" type="number" min="0" step="0.0001" placeholder="留空沿用普通输出价" />
              </label>
              <label class="block">
                <span class="text-xs font-black text-slate-600">长上下文缓存读 / 1M</span>
                <input v-model.number="draft.longContextCachedInputPrice" class="input mt-1 h-10 rounded-lg text-sm" type="number" min="0" step="0.0001" placeholder="留空沿用普通缓存读价" />
              </label>
              <label class="block">
                <span class="text-xs font-black text-slate-600">长上下文缓存写 / 1M</span>
                <input v-model.number="draft.longContextCacheCreationPrice" class="input mt-1 h-10 rounded-lg text-sm" type="number" min="0" step="0.0001" placeholder="留空沿用普通缓存写价" />
              </label>
            </div>
          </template>
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
          {{ saving ? '保存中…' : (isNew ? '创建模型' : '保存模型') }}
        </button>
      </div>
    </template>
  </RelayDrawer>
</template>
