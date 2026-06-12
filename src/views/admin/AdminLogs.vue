<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminApi } from '@/api/adminApi'
import AppLayout from '@/components/AppLayout.vue'
import ImagePreviewModal from '@/components/ImagePreviewModal.vue'
import Pagination from '@/components/Pagination.vue'
import type { AdminImageRecord } from '@/types'

const records = ref<AdminImageRecord[]>([])
const current = ref(1)
const pages = ref(1)
const total = ref(0)
const keyword = ref('')
const status = ref('')
const preview = ref('')
const loading = ref(false)
const error = ref('')

async function load(page = 1) {
  loading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.imageRecords(page, 10, keyword.value, status.value)
    records.value = data.data.records
    current.value = data.data.current
    pages.value = data.data.pages
    total.value = data.data.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  load(1)
}

onMounted(() => load())
</script>

<template>
  <AppLayout admin>
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="text-sm font-bold uppercase tracking-[0.22em] text-sky-600">生成日志</p>
        <h1 class="mt-2 text-2xl font-black sm:text-3xl">用户生图列表</h1>
        <p class="mt-2 text-sm text-slate-500">查看所有用户的生成结果、提示词、状态和生成时间。</p>
      </div>
      <div class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 shadow-sm sm:w-auto">
        <p class="text-xs font-semibold text-slate-500">记录总数</p>
        <p class="mt-1 text-2xl font-black">{{ total }}</p>
      </div>
    </div>

    <section class="mt-6 rounded-2xl border border-slate-200 bg-white p-4 shadow-[0_18px_60px_rgba(21,32,51,0.07)]">
      <div class="grid gap-3 md:grid-cols-[1fr_180px_100px]">
        <input v-model="keyword" class="input rounded-2xl" placeholder="搜索用户名或提示词" @keyup.enter="search" />
        <select v-model="status" class="input rounded-2xl" @change="search">
          <option value="">全部状态</option>
          <option value="pending">pending</option>
          <option value="success">success</option>
          <option value="failed">failed</option>
        </select>
        <button class="btn-primary rounded-2xl" @click="search">查询</button>
      </div>
      <p v-if="error" class="mt-3 rounded-xl bg-red-50 px-3 py-2 text-sm font-semibold text-red-600">{{ error }}</p>
    </section>

    <section class="mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-[0_18px_60px_rgba(21,32,51,0.07)]">
      <div class="hidden grid-cols-[120px_1fr_110px_110px_180px_120px] gap-4 border-b border-slate-100 bg-slate-50 px-5 py-4 text-xs font-bold uppercase tracking-[0.14em] text-slate-500 xl:grid">
        <span>用户</span>
        <span>提示词</span>
        <span>状态</span>
        <span>成本</span>
        <span>时间</span>
        <span>结果</span>
      </div>

      <div class="divide-y divide-slate-100">
        <div v-if="loading" class="p-8 text-sm text-slate-500">加载中...</div>
        <div
          v-for="record in records"
          v-else
          :key="record.id"
          class="grid gap-3 px-4 py-4 text-sm transition hover:bg-sky-50/70 sm:gap-4 sm:px-5 xl:grid-cols-[120px_1fr_110px_110px_180px_120px] xl:items-center"
        >
          <div>
            <p class="font-black text-slate-900">{{ record.username || '未知用户' }}</p>
            <p class="mt-1 text-xs text-slate-500">ID: {{ record.userId }}</p>
          </div>
          <p class="line-clamp-2 leading-6 text-slate-700">{{ record.prompt }}</p>
          <span class="w-fit rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-bold text-slate-600">{{ record.status }}</span>
          <span class="font-semibold text-slate-700">￥{{ Number(record.cost || 0).toFixed(2) }}</span>
          <span class="text-slate-500">{{ record.createdAt }}</span>
          <button v-if="record.generatedImageUrl" class="group w-fit overflow-hidden rounded-xl border border-slate-200 bg-white p-1 shadow-sm" @click="preview = record.generatedImageUrl || ''">
            <img :src="record.generatedImageUrl" class="h-16 w-24 rounded-lg object-cover transition duration-300 group-hover:scale-105" alt="生成结果" />
          </button>
          <span v-else class="text-xs text-slate-400">暂无图像</span>
        </div>
        <div v-if="!loading && records.length === 0" class="p-8 text-sm text-slate-500">暂无生图记录</div>
      </div>

      <div class="border-t border-slate-100 bg-white/70 p-4">
        <Pagination :current="current" :pages="pages" @change="load" />
      </div>
    </section>

    <ImagePreviewModal :src="preview" @close="preview = ''" />
  </AppLayout>
</template>
