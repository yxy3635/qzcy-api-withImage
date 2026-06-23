<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import RequestLoader from '@/components/RequestLoader.vue'
import { adminApi } from '@/api/adminApi'
import { useToast } from '@/composables/useToast'
import type { Announcement } from '@/types'

const toast = useToast()
const announcements = ref<Announcement[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const editingId = ref<number | null>(null)
const form = reactive({
  title: '',
  content: '',
  enabled: true,
  pinned: false,
  sortOrder: 10
})

function resetForm() {
  editingId.value = null
  form.title = ''
  form.content = ''
  form.enabled = true
  form.pinned = false
  form.sortOrder = 10
}

function editItem(item: Announcement) {
  editingId.value = item.id
  form.title = item.title || ''
  form.content = item.content || ''
  form.enabled = Boolean(item.enabled)
  form.pinned = Boolean(item.pinned)
  form.sortOrder = Number(item.sortOrder || 10)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.announcements()
    announcements.value = data.data
  } catch (err) {
    error.value = err instanceof Error ? err.message : '公告加载失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  error.value = ''
  if (!form.title.trim() || !form.content.trim()) {
    error.value = '标题和内容不能为空'
    toast.warning(error.value)
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title,
      content: form.content,
      enabled: form.enabled,
      pinned: form.pinned,
      sortOrder: form.sortOrder
    }
    if (editingId.value) {
      await adminApi.updateAnnouncement(editingId.value, payload)
      toast.success('公告已更新')
    } else {
      await adminApi.createAnnouncement(payload)
      toast.success('公告已发布')
    }
    resetForm()
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '公告保存失败'
    toast.error(error.value)
  } finally {
    saving.value = false
  }
}

async function removeItem(id: number) {
  if (!window.confirm('确定删除这条公告吗？')) return
  await adminApi.deleteAnnouncement(id)
  if (editingId.value === id) resetForm()
  await load()
  toast.success('公告已删除')
}

onMounted(load)
</script>

<template>
  <AppLayout admin>
    <div class="page-enter">
      <div class="flex flex-wrap items-end justify-between gap-5">
        <div>
          <p class="text-sm font-black tracking-[0.22em] text-sky-600">公告发布</p>
          <h1 class="mt-2 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">站内公告管理</h1>
          <p class="mt-3 text-sm font-medium text-slate-500">发布后会同步显示在生图用户仪表盘和中转站仪表盘。</p>
        </div>
        <button class="h-12 rounded-2xl border border-slate-200 bg-white px-5 text-sm font-black text-slate-700 transition hover:border-sky-200 hover:bg-sky-50" @click="load">
          刷新列表
        </button>
      </div>

      <p v-if="error" class="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{{ error }}</p>

      <div class="mt-6 grid gap-6 xl:grid-cols-[420px_1fr]">
        <section class="rounded-[28px] border border-white/80 bg-white/86 p-5 shadow-[0_24px_80px_rgba(15,23,42,0.08)]">
          <div class="flex items-center justify-between gap-3">
            <h2 class="text-2xl font-black">{{ editingId ? '编辑公告' : '发布公告' }}</h2>
            <button v-if="editingId" class="text-xs font-black text-slate-500 hover:text-sky-600" @click="resetForm">新建公告</button>
          </div>
          <div class="mt-5 space-y-4">
            <label class="block">
              <span class="text-xs font-black text-slate-500">公告标题</span>
              <input v-model="form.title" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" placeholder="输入公告标题" />
            </label>
            <label class="block">
              <span class="text-xs font-black text-slate-500">公告内容</span>
              <textarea v-model="form.content" class="mt-2 min-h-[220px] w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 py-3 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" placeholder="输入公告正文" />
            </label>
            <div class="grid gap-3 sm:grid-cols-3">
              <label class="flex items-center justify-between rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-black text-slate-700">
                启用
                <input v-model="form.enabled" class="h-5 w-5 accent-sky-500" type="checkbox" />
              </label>
              <label class="flex items-center justify-between rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-black text-slate-700">
                置顶
                <input v-model="form.pinned" class="h-5 w-5 accent-sky-500" type="checkbox" />
              </label>
              <label class="block rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
                <span class="text-xs font-black text-slate-500">排序</span>
                <input v-model.number="form.sortOrder" class="mt-2 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-black outline-none focus:border-sky-300" type="number" />
              </label>
            </div>
            <button class="h-12 w-full rounded-2xl bg-slate-950 text-sm font-black text-white transition hover:bg-sky-600 disabled:opacity-60" :disabled="saving" @click="save">
              {{ saving ? '保存中' : editingId ? '更新公告' : '发布公告' }}
            </button>
          </div>
        </section>

        <section class="rounded-[28px] border border-white/80 bg-white/86 p-5 shadow-[0_24px_80px_rgba(15,23,42,0.08)]">
          <h2 class="text-2xl font-black">公告列表</h2>
          <RequestLoader v-if="loading" class="py-10" label="正在加载公告" :cell-size="18" />
          <div v-else class="mt-5 space-y-3">
            <article v-for="item in announcements" :key="item.id" class="rounded-2xl border border-slate-100 bg-slate-50 p-4">
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div class="flex flex-wrap items-center gap-2">
                    <h3 class="text-lg font-black text-slate-950">{{ item.title }}</h3>
                    <span v-if="item.pinned" class="rounded-full bg-amber-50 px-2.5 py-1 text-xs font-black text-amber-700">置顶</span>
                    <span class="rounded-full px-2.5 py-1 text-xs font-black" :class="item.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-200 text-slate-600'">{{ item.enabled ? '已启用' : '已停用' }}</span>
                  </div>
                  <p class="mt-2 line-clamp-3 text-sm font-semibold leading-6 text-slate-600">{{ item.content }}</p>
                  <p class="mt-2 text-xs font-semibold text-slate-400">发布时间 {{ item.publishedAt || item.createdAt || '-' }}</p>
                </div>
                <div class="flex gap-3 text-xs font-black text-slate-500">
                  <button class="hover:text-sky-600" @click="editItem(item)">编辑</button>
                  <button class="hover:text-red-600" @click="removeItem(item.id)">删除</button>
                </div>
              </div>
            </article>
            <div v-if="!announcements.length" class="rounded-2xl border border-dashed border-slate-200 p-10 text-center text-sm font-black text-slate-500">暂无公告</div>
          </div>
        </section>
      </div>
    </div>
  </AppLayout>
</template>
