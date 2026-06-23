<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import RequestLoader from '@/components/RequestLoader.vue'
import { adminApi } from '@/api/adminApi'
import { useToast } from '@/composables/useToast'
import type { ImageGenerationConfig } from '@/types'

const toast = useToast()
interface ConfigDraft {
  name: string
  model: string
  apiKey: string
  apiBaseUrl: string
  endpointPath: string
  size: string
  quality: string
  price: number
  enabled: boolean
  sortOrder: number
}

const configs = ref<ImageGenerationConfig[]>([])
const drafts = reactive<Record<number, ConfigDraft>>({})
const loading = ref(false)
const savingId = ref<number | null>(null)
const error = ref('')

function setDraft(config: ImageGenerationConfig) {
  drafts[config.id] = {
    name: config.name,
    model: config.model,
    apiKey: '',
    apiBaseUrl: config.apiBaseUrl,
    endpointPath: config.endpointPath,
    size: config.size,
    quality: config.quality,
    price: Number(config.price || 0),
    enabled: config.enabled,
    sortOrder: Number(config.sortOrder || 0)
  }
}

function draftOf(config: ImageGenerationConfig): ConfigDraft {
  if (!drafts[config.id]) setDraft(config)
  return drafts[config.id] as ConfigDraft
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.imageConfigs()
    configs.value = data.data
    configs.value.forEach(setDraft)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '配置加载失败'
  } finally {
    loading.value = false
  }
}

async function save(config: ImageGenerationConfig) {
  const draft = draftOf(config)
  error.value = ''
  if (draft.price < 0) {
    error.value = '价格不能小于0'
    toast.warning(error.value)
    return
  }
  savingId.value = config.id
  try {
    await adminApi.updateImageConfig(config.id, {
      name: draft.name,
      model: draft.model,
      apiBaseUrl: draft.apiBaseUrl,
      endpointPath: '/v1/images/generations',
      apiKey: draft.apiKey.trim() || undefined,
      size: draft.size,
      quality: draft.quality,
      price: draft.price,
      enabled: draft.enabled,
      sortOrder: draft.sortOrder
    })
    draft.apiKey = ''
    toast.success(`${draft.name} 已保存`)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
    toast.error(error.value)
  } finally {
    savingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <AppLayout admin>
    <div class="page-enter">
      <div class="flex flex-wrap items-end justify-between gap-5">
        <div>
          <p class="text-sm font-black tracking-[0.22em] text-sky-600">生图定价</p>
          <h1 class="mt-2 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">OpenAI 模型与价格</h1>
          <p class="mt-3 text-sm font-medium text-slate-500">配置每个图像规格的 API Key、模型参数和用户扣费价格。</p>
        </div>
        <button class="h-12 w-full rounded-2xl border border-slate-200 bg-white px-5 text-sm font-black text-slate-700 shadow-sm transition hover:border-sky-200 hover:bg-sky-50 sm:w-auto" @click="load">
          刷新配置
        </button>
      </div>

      <p v-if="error" class="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{{ error }}</p>

      <div v-if="loading" class="mt-8 rounded-3xl border border-slate-100 bg-white p-10 shadow-sm">
        <RequestLoader label="正在读取配置" :cell-size="18" />
      </div>

      <div v-else class="mt-6 grid gap-5 sm:mt-8 xl:grid-cols-3">
        <section
          v-for="config in configs"
          :key="config.id"
          class="rounded-[24px] border border-white/80 bg-white/86 p-4 shadow-[0_24px_80px_rgba(15,23,42,0.08)] backdrop-blur-2xl transition duration-300 hover:-translate-y-1 hover:shadow-[0_28px_90px_rgba(14,165,233,0.14)] sm:rounded-[28px] sm:p-5"
        >
          <div class="flex items-start justify-between gap-3">
            <div>
              <p class="text-xs font-black uppercase tracking-[0.2em] text-sky-600">{{ config.code }}</p>
              <h2 class="mt-2 text-2xl font-black text-slate-950">{{ config.name }}</h2>
              <p class="mt-2 text-xs font-semibold text-slate-500">当前 Key：{{ config.apiKeyMasked || '未配置' }}</p>
            </div>
            <label class="relative inline-flex cursor-pointer items-center">
              <input v-model="draftOf(config).enabled" class="peer sr-only" type="checkbox" />
              <span class="h-7 w-12 rounded-full bg-slate-200 transition peer-checked:bg-sky-500" />
              <span class="absolute left-1 h-5 w-5 rounded-full bg-white shadow transition peer-checked:translate-x-5" />
            </label>
          </div>

          <div class="mt-6 space-y-4">
            <label class="block">
              <span class="text-xs font-black text-slate-500">显示名称</span>
              <input v-model="draftOf(config).name" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" />
            </label>

            <label class="block">
              <span class="text-xs font-black text-slate-500">OpenAI API Key</span>
              <input v-model="draftOf(config).apiKey" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" type="password" placeholder="留空则不修改" />
            </label>

            <label class="block">
              <span class="text-xs font-black text-slate-500">服务商 API 地址</span>
              <input v-model="draftOf(config).apiBaseUrl" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" placeholder="https://api.openai.com" />
            </label>

            <label class="block">
              <span class="text-xs font-black text-slate-500">模型</span>
              <input v-model="draftOf(config).model" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" placeholder="dall-e-3" />
            </label>

            <div class="grid gap-3 sm:grid-cols-2">
              <label class="block">
                <span class="text-xs font-black text-slate-500">尺寸</span>
                <input v-model="draftOf(config).size" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" placeholder="1024x1024" />
              </label>
            </div>

            <div class="grid gap-3 sm:grid-cols-3">
              <label class="block sm:col-span-1">
                <span class="text-xs font-black text-slate-500">质量</span>
                <input v-model="draftOf(config).quality" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" placeholder="standard" />
              </label>
              <label class="block sm:col-span-1">
                <span class="text-xs font-black text-slate-500">价格</span>
                <div class="relative mt-2">
                  <span class="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-sm font-black text-slate-400">￥</span>
                  <input v-model.number="draftOf(config).price" class="h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 pl-9 pr-4 text-sm font-black outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" min="0" step="0.01" type="number" />
                </div>
              </label>
              <label class="block sm:col-span-1">
                <span class="text-xs font-black text-slate-500">排序</span>
                <input v-model.number="draftOf(config).sortOrder" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" type="number" />
              </label>
            </div>

            <button
              class="mt-2 h-12 w-full rounded-2xl bg-slate-950 text-sm font-black text-white shadow-[0_18px_45px_rgba(15,23,42,0.16)] transition hover:-translate-y-0.5 hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="savingId === config.id"
              @click="save(config)"
            >
              {{ savingId === config.id ? '保存中' : '保存配置' }}
            </button>
          </div>
        </section>
      </div>
    </div>
  </AppLayout>
</template>
