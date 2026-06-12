<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import Pagination from '@/components/Pagination.vue'
import { adminApi } from '@/api/adminApi'
import type { Role, UserInfo } from '@/types'

interface UserDraft {
  email: string
  role: Role
  balance: number
  password: string
}

const users = ref<UserInfo[]>([])
const drafts = reactive<Record<number, UserDraft>>({})
const keyword = ref('')
const current = ref(1)
const pages = ref(1)
const message = ref('')
const error = ref('')
const savingId = ref<number | null>(null)

function setDraft(user: UserInfo) {
  drafts[user.id] = {
    email: user.email || '',
    role: user.role,
    balance: Number(user.balance || 0),
    password: ''
  }
}

function draftOf(user: UserInfo): UserDraft {
  if (!drafts[user.id]) setDraft(user)
  return drafts[user.id] as UserDraft
}

async function load(page = 1) {
  error.value = ''
  const { data } = await adminApi.users(page, 10, keyword.value)
  users.value = data.data.records
  users.value.forEach(setDraft)
  current.value = data.data.current
  pages.value = data.data.pages
}

async function saveUser(user: UserInfo) {
  const draft = draftOf(user)
  error.value = ''
  message.value = ''
  if (draft.balance < 0) {
    error.value = '余额不能小于0'
    return
  }
  if (draft.password && draft.password.length < 6) {
    error.value = '新密码至少6位'
    return
  }
  savingId.value = user.id
  try {
    await adminApi.updateUser(user.id, {
      email: draft.email.trim(),
      role: draft.role,
      balance: draft.balance,
      password: draft.password.trim() || undefined
    })
    draft.password = ''
    message.value = `用户 ${user.username} 已更新`
    await load(current.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
  } finally {
    savingId.value = null
  }
}

async function remove(user: UserInfo) {
  if (!confirm(`确认删除用户 ${user.username}？`)) return
  error.value = ''
  message.value = ''
  await adminApi.deleteUser(user.id)
  message.value = '用户已删除'
  await load(current.value)
}

function formatDate(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<template>
  <AppLayout admin>
    <div class="page-enter">
      <div class="flex flex-wrap items-end justify-between gap-5">
        <div>
          <p class="text-sm font-black tracking-[0.22em] text-sky-600">用户管理</p>
          <h1 class="mt-2 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">账号、资料与余额</h1>
          <p class="mt-3 text-sm font-medium text-slate-500">维护用户邮箱、权限、余额，并为用户重置登录密码。</p>
        </div>
        <div class="grid w-full gap-3 sm:flex sm:w-auto">
          <label class="relative min-w-0 flex-1 sm:w-80">
            <svg class="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m21 21-4.35-4.35M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" />
            </svg>
            <input
              v-model="keyword"
              class="h-14 w-full rounded-2xl border border-slate-200 bg-white/90 pl-12 pr-4 text-sm font-semibold text-slate-800 shadow-[0_16px_50px_rgba(15,23,42,0.06)] outline-none transition focus:border-sky-300 focus:ring-4 focus:ring-sky-100"
              placeholder="搜索用户名"
              @keyup.enter="load(1)"
            />
          </label>
          <button class="h-14 rounded-2xl bg-slate-950 px-7 text-sm font-black text-white shadow-[0_18px_45px_rgba(15,23,42,0.18)] transition hover:-translate-y-0.5 hover:bg-sky-600" @click="load(1)">搜索</button>
        </div>
      </div>

      <p v-if="message" class="mt-5 rounded-2xl bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-700">{{ message }}</p>
      <p v-if="error" class="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{{ error }}</p>

      <section class="mt-7 overflow-hidden rounded-[28px] border border-white/80 bg-white/78 shadow-[0_28px_90px_rgba(15,23,42,0.08)] backdrop-blur-2xl">
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-4 py-5 sm:px-6">
          <div>
            <p class="text-lg font-black text-slate-950">用户资产列表</p>
            <p class="mt-1 text-xs font-semibold text-slate-500">当前页 {{ users.length }} 位用户，可直接编辑后保存。</p>
          </div>
          <div class="rounded-full border border-sky-100 bg-sky-50 px-4 py-2 text-xs font-black text-sky-700">
            第 {{ current }} 页 / 共 {{ Math.max(pages, 1) }} 页
          </div>
        </div>

        <div class="hidden grid-cols-[1.2fr_1.7fr_1.05fr_1.05fr_1.45fr_1fr_1.15fr] gap-4 bg-slate-50/80 px-6 py-4 text-xs font-black tracking-[0.18em] text-slate-400 xl:grid">
          <span>用户</span>
          <span>邮箱</span>
          <span>角色</span>
          <span>余额</span>
          <span>新密码</span>
          <span>注册时间</span>
          <span class="text-right">操作</span>
        </div>

        <div class="space-y-3 bg-gradient-to-b from-white to-sky-50/50 p-3 sm:p-4">
          <article
            v-for="user in users"
            :key="user.id"
            class="group grid gap-4 rounded-3xl border border-slate-100 bg-white p-4 shadow-[0_16px_45px_rgba(15,23,42,0.055)] transition duration-300 hover:-translate-y-0.5 hover:border-sky-200 hover:shadow-[0_24px_70px_rgba(14,165,233,0.13)] xl:grid-cols-[1.2fr_1.7fr_1.05fr_1.05fr_1.45fr_1fr_1.15fr] xl:items-center"
          >
            <div class="flex items-center gap-3">
              <div class="grid h-12 w-12 place-items-center rounded-2xl bg-gradient-to-br from-slate-950 to-sky-600 text-base font-black text-white shadow-[0_14px_35px_rgba(15,23,42,0.18)]">
                {{ user.username.slice(0, 1).toUpperCase() }}
              </div>
              <div class="min-w-0">
                <p class="truncate text-base font-black text-slate-950">{{ user.username }}</p>
                <p class="mt-1 text-xs font-semibold text-slate-400">ID {{ user.id }}</p>
              </div>
            </div>

            <label class="block">
              <span class="mb-2 block text-xs font-black text-slate-400 xl:hidden">邮箱</span>
              <input v-model="draftOf(user).email" class="h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/70 px-4 text-sm font-semibold text-slate-800 outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" type="email" placeholder="用户邮箱" />
            </label>

            <label class="block">
              <span class="mb-2 block text-xs font-black text-slate-400 xl:hidden">角色</span>
              <div class="relative">
                <select v-model="draftOf(user).role" class="h-12 w-full appearance-none rounded-2xl border border-slate-200 bg-slate-50/70 px-4 pr-9 text-sm font-black text-slate-800 outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100">
                  <option value="USER">普通用户</option>
                  <option value="ADMIN">管理员</option>
                </select>
                <span class="pointer-events-none absolute right-4 top-1/2 h-2 w-2 -translate-y-1/2 rotate-45 border-b-2 border-r-2 border-slate-400" />
              </div>
            </label>

            <label class="block">
              <span class="mb-2 block text-xs font-black text-slate-400 xl:hidden">余额</span>
              <div class="relative">
                <span class="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-sm font-black text-slate-400">￥</span>
                <input v-model.number="draftOf(user).balance" class="h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/70 pl-9 pr-4 text-sm font-black text-slate-900 outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" min="0" step="0.01" type="number" />
              </div>
            </label>

            <label class="block">
              <span class="mb-2 block text-xs font-black text-slate-400 xl:hidden">新密码</span>
              <input v-model="draftOf(user).password" class="h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/70 px-4 text-sm font-semibold text-slate-800 outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" type="password" placeholder="留空则不修改" />
            </label>

            <div>
              <span class="mb-2 block text-xs font-black text-slate-400 xl:hidden">注册时间</span>
              <p class="text-sm font-bold leading-relaxed text-slate-600">{{ formatDate(user.createdAt) }}</p>
            </div>

            <div class="flex flex-wrap items-center justify-end gap-2">
              <button
                class="inline-flex h-11 items-center gap-2 rounded-2xl bg-slate-950 px-4 text-xs font-black text-white shadow-[0_12px_32px_rgba(15,23,42,0.16)] transition hover:-translate-y-0.5 hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="savingId === user.id"
                @click="saveUser(user)"
              >
                <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                </svg>
                {{ savingId === user.id ? '保存中' : '保存' }}
              </button>
              <button class="grid h-11 w-11 place-items-center rounded-2xl border border-red-100 bg-red-50 text-red-600 transition hover:-translate-y-0.5 hover:border-red-200 hover:bg-red-100" title="删除用户" @click="remove(user)">
                <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 7h12M9 7V5h6v2m-7 3v8m4-8v8m4-8v8M8 7l1 13h6l1-13" />
                </svg>
              </button>
            </div>
          </article>

          <div v-if="!users.length" class="rounded-3xl border border-dashed border-slate-200 bg-white/70 px-6 py-14 text-center">
            <p class="text-lg font-black text-slate-800">暂无用户数据</p>
            <p class="mt-2 text-sm font-semibold text-slate-500">请调整搜索条件后重试。</p>
          </div>
        </div>

        <div class="border-t border-slate-100 bg-white px-4 py-5 sm:px-6">
          <Pagination :current="current" :pages="pages" @change="load" />
        </div>
      </section>
    </div>
  </AppLayout>
</template>
