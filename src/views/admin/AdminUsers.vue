<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import Pagination from '@/components/Pagination.vue'
import RequestLoader from '@/components/RequestLoader.vue'
import { adminApi } from '@/api/adminApi'
import { useToast } from '@/composables/useToast'
import type { RelayUserOverview, Role, UserInfo } from '@/types'

const toast = useToast()
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
const total = ref(0)
const error = ref('')
const savingId = ref<number | null>(null)
const apiLoadingUserId = ref<number | null>(null)
const editingUser = ref<UserInfo | null>(null)
const selectedUser = ref<UserInfo | null>(null)
const selectedOverview = ref<RelayUserOverview | null>(null)

const activeUsers = computed(() => users.value.filter((user) => user.role === 'USER').length)
const adminUsers = computed(() => users.value.filter((user) => user.role === 'ADMIN').length)
const pageBalance = computed(() => users.value.reduce((sum, user) => sum + Number(user.balance || 0), 0))
const tokens = computed(() => selectedOverview.value?.tokens || [])
const logs = computed(() => selectedOverview.value?.logs || [])
const models = computed(() => selectedOverview.value?.models || [])
const modelUsage = computed(() => selectedOverview.value?.modelUsage || [])

const apiCards = computed(() => {
  const overview = selectedOverview.value
  return [
    { label: '今日消费', value: money(overview?.todayCost), sub: `累计 ${money(overview?.totalCost)}`, tone: 'emerald' },
    { label: '今日请求', value: compact(overview?.todayRequests), sub: `累计 ${compact(overview?.totalRequests)}`, tone: 'blue' },
    { label: '今日 Token', value: compact(overview?.todayTotalTokens), sub: `输入 ${compact(overview?.todayPromptTokens)} / 输出 ${compact(overview?.todayCompletionTokens)}`, tone: 'amber' },
    { label: 'RPM / TPM', value: `${compact(overview?.currentRpm)} / ${compact(overview?.currentTpm)}`, sub: '最近 1 分钟', tone: 'violet' }
  ]
})

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

function openEditUser(user: UserInfo) {
  setDraft(user)
  editingUser.value = user
}

function closeEditUser() {
  editingUser.value = null
}

async function load(page = 1) {
  error.value = ''
  const { data } = await adminApi.users(page, 10, keyword.value)
  users.value = data.data.records
  users.value.forEach(setDraft)
  current.value = data.data.current
  pages.value = data.data.pages
  total.value = data.data.total
}

async function saveUser(user: UserInfo) {
  const draft = draftOf(user)
  error.value = ''
  if (draft.balance < 0) {
    error.value = '余额不能小于 0'
    toast.warning(error.value)
    return
  }
  if (draft.password && draft.password.length < 6) {
    error.value = '新密码至少 6 位'
    toast.warning(error.value)
    return
  }
  savingId.value = user.id
  try {
    await adminApi.updateUser(user.id, {
      email: draft.email.trim(),
      role: draft.role,
      balance: draft.balance,
      password: draft.password || undefined
    })
    draft.password = ''
    toast.success(`用户 ${user.username} 已更新`)
    editingUser.value = null
    await load(current.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
    toast.error(error.value)
  } finally {
    savingId.value = null
  }
}

async function remove(user: UserInfo) {
  if (!confirm(`确认删除用户 ${user.username}？`)) return
  error.value = ''
  try {
    await adminApi.deleteUser(user.id)
    toast.success('用户已删除')
    await load(current.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除失败'
    toast.error(error.value)
  }
}

async function openApiUsage(user: UserInfo) {
  selectedUser.value = user
  selectedOverview.value = null
  apiLoadingUserId.value = user.id
  error.value = ''
  try {
    const { data } = await adminApi.userRelayOverview(user.id)
    selectedOverview.value = data.data
  } catch (err) {
    error.value = err instanceof Error ? err.message : '查询 API 使用失败'
    toast.error(error.value)
    selectedUser.value = null
  } finally {
    apiLoadingUserId.value = null
  }
}

function closeApiUsage() {
  selectedUser.value = null
  selectedOverview.value = null
}

function formatDate(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function compact(value?: number) {
  const amount = Number(value || 0)
  if (amount >= 1_000_000_000) return `${(amount / 1_000_000_000).toFixed(2)}B`
  if (amount >= 1_000_000) return `${(amount / 1_000_000).toFixed(2)}M`
  if (amount >= 1_000) return `${(amount / 1_000).toFixed(2)}K`
  return String(amount)
}

function money(value?: number) {
  return `$${Number(value || 0).toFixed(6)}`
}

function roleBadge(role: Role) {
  return role === 'ADMIN'
    ? 'bg-violet-50 text-violet-700 ring-violet-100'
    : 'bg-emerald-50 text-emerald-700 ring-emerald-100'
}

function cardTone(tone: string) {
  const tones: Record<string, string> = {
    emerald: 'border-emerald-100 text-emerald-700',
    blue: 'border-blue-100 text-blue-700',
    amber: 'border-amber-100 text-amber-700',
    violet: 'border-violet-100 text-violet-700'
  }
  return tones[tone] || tones.blue
}

onMounted(load)
</script>

<template>
  <AppLayout admin>
    <div class="page-enter">
      <div class="flex flex-wrap items-end justify-between gap-5">
        <div>
          <p class="text-sm font-black tracking-[0.22em] text-sky-600">用户管理</p>
          <h1 class="mt-2 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">账户、资料与 API 使用</h1>
          <p class="mt-3 text-sm font-medium text-slate-500">集中维护用户资料、余额、权限，并快速查看每个用户的中转 API 实时数据。</p>
        </div>
        <div class="grid w-full gap-3 sm:flex sm:w-auto">
          <label class="relative min-w-0 flex-1 sm:w-80">
            <svg class="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m21 21-4.35-4.35M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" />
            </svg>
            <input
              v-model="keyword"
              class="h-12 w-full rounded-lg border border-slate-200 bg-white pl-12 pr-4 text-sm font-semibold text-slate-800 outline-none transition focus:border-sky-300 focus:ring-4 focus:ring-sky-100"
              placeholder="搜索用户名"
              @keyup.enter="load(1)"
            />
          </label>
          <button class="h-12 rounded-lg bg-slate-950 px-6 text-sm font-black text-white transition hover:bg-sky-600" @click="load(1)">搜索</button>
        </div>
      </div>

      <div class="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <div class="rounded-lg border border-slate-100 bg-white p-4 shadow-sm">
          <p class="text-xs font-black text-slate-400">总用户</p>
          <p class="mt-2 text-2xl font-black text-slate-950">{{ total }}</p>
        </div>
        <div class="rounded-lg border border-slate-100 bg-white p-4 shadow-sm">
          <p class="text-xs font-black text-slate-400">当前页普通用户</p>
          <p class="mt-2 text-2xl font-black text-emerald-600">{{ activeUsers }}</p>
        </div>
        <div class="rounded-lg border border-slate-100 bg-white p-4 shadow-sm">
          <p class="text-xs font-black text-slate-400">当前页管理员</p>
          <p class="mt-2 text-2xl font-black text-violet-600">{{ adminUsers }}</p>
        </div>
        <div class="rounded-lg border border-slate-100 bg-white p-4 shadow-sm">
          <p class="text-xs font-black text-slate-400">当前页余额</p>
          <p class="mt-2 text-2xl font-black text-sky-600">${{ pageBalance.toFixed(6) }}</p>
        </div>
      </div>

      <p v-if="error" class="mt-5 rounded-lg bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{{ error }}</p>

      <section class="mt-7 overflow-hidden rounded-lg border border-slate-100 bg-white shadow-sm">
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-4 py-5 sm:px-6">
          <div>
            <p class="text-lg font-black text-slate-950">用户列表</p>
            <p class="mt-1 text-xs font-semibold text-slate-500">当前页 {{ users.length }} 位用户，列表仅展示基础信息。</p>
          </div>
          <div class="rounded-full border border-sky-100 bg-sky-50 px-4 py-2 text-xs font-black text-sky-700">
            第 {{ current }} 页 / 共 {{ Math.max(pages, 1) }} 页
          </div>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full min-w-[1120px] text-left text-sm">
            <thead class="bg-slate-50 text-xs font-black text-slate-500">
              <tr>
                <th class="px-5 py-4">用户</th>
                <th class="px-5 py-4">邮箱</th>
                <th class="px-5 py-4">角色</th>
                <th class="px-5 py-4">余额</th>
                <th class="px-5 py-4">邀请码</th>
                <th class="px-5 py-4">邀请人 ID</th>
                <th class="px-5 py-4">注册时间</th>
                <th class="px-5 py-4 text-right">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr v-for="user in users" :key="user.id" class="align-middle transition hover:bg-sky-50/40">
                <td class="px-5 py-4">
                  <div class="flex items-center gap-3">
                    <div class="grid h-10 w-10 place-items-center rounded-lg bg-slate-950 text-sm font-black text-white">
                      {{ user.username.slice(0, 1).toUpperCase() }}
                    </div>
                    <div class="min-w-0">
                      <p class="truncate font-black text-slate-950">{{ user.username }}</p>
                      <p class="mt-1 text-xs font-semibold text-slate-400">ID {{ user.id }}</p>
                    </div>
                  </div>
                </td>
                <td class="px-5 py-4 font-semibold text-slate-600">{{ user.email || '-' }}</td>
                <td class="px-5 py-4">
                  <span class="inline-flex rounded-full px-2 py-1 text-xs font-black ring-1" :class="roleBadge(user.role)">{{ user.role }}</span>
                </td>
                <td class="px-5 py-4 font-black text-slate-900">${{ Number(user.balance || 0).toFixed(6) }}</td>
                <td class="px-5 py-4 font-semibold text-slate-600">{{ user.invitationCode || '-' }}</td>
                <td class="px-5 py-4 font-semibold text-slate-600">{{ user.inviterId || '-' }}</td>
                <td class="px-5 py-4 font-semibold text-slate-500">{{ formatDate(user.createdAt) }}</td>
                <td class="px-5 py-4">
                  <div class="flex items-center justify-end gap-2">
                    <button
                      class="h-10 rounded-lg border border-slate-200 bg-white px-3 text-xs font-black text-slate-700 transition hover:border-sky-200 hover:bg-sky-50 hover:text-sky-700"
                      @click="openEditUser(user)"
                    >
                      编辑
                    </button>
                    <button
                      class="h-10 rounded-lg border border-sky-100 bg-sky-50 px-3 text-xs font-black text-sky-700 transition hover:border-sky-200 hover:bg-sky-100 disabled:opacity-60"
                      :disabled="apiLoadingUserId === user.id"
                      @click="openApiUsage(user)"
                    >
                      {{ apiLoadingUserId === user.id ? '查询中' : '查询 API 使用' }}
                    </button>
                    <button class="grid h-10 w-10 place-items-center rounded-lg border border-red-100 bg-red-50 text-red-600 transition hover:bg-red-100" title="删除用户" @click="remove(user)">
                      <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 7h12M9 7V5h6v2m-7 3v8m4-8v8m4-8v8M8 7l1 13h6l1-13" />
                      </svg>
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="!users.length" class="border-t border-slate-100 px-6 py-14 text-center">
          <p class="text-lg font-black text-slate-800">暂无用户数据</p>
          <p class="mt-2 text-sm font-semibold text-slate-500">请调整搜索条件后重试。</p>
        </div>

        <div class="border-t border-slate-100 bg-white px-4 py-5 sm:px-6">
          <Pagination :current="current" :pages="pages" @change="load" />
        </div>
      </section>

      <Teleport to="body">
        <div v-if="editingUser" class="fixed inset-0 z-[9998] grid place-items-center bg-slate-950/40 p-4 backdrop-blur-sm" @click.self="closeEditUser">
          <section class="w-full max-w-2xl rounded-lg bg-white shadow-2xl">
            <div class="flex items-start justify-between gap-4 border-b border-slate-100 px-6 py-5">
              <div class="min-w-0">
                <p class="text-xs font-black uppercase tracking-[0.18em] text-sky-600">Edit User</p>
                <h2 class="mt-1 truncate text-2xl font-black text-slate-950">编辑用户信息</h2>
                <p class="mt-1 text-sm font-semibold text-slate-500">{{ editingUser.username }} · ID {{ editingUser.id }}</p>
              </div>
              <button class="grid h-10 w-10 shrink-0 place-items-center rounded-lg border border-slate-200 bg-white text-xl font-black text-slate-400 transition hover:border-red-100 hover:bg-red-50 hover:text-red-600" @click="closeEditUser">×</button>
            </div>

            <div class="space-y-5 px-6 py-5">
              <div class="grid gap-4 sm:grid-cols-2">
                <label class="block">
                  <span class="text-xs font-black text-slate-500">用户名</span>
                  <input :value="editingUser.username" class="mt-2 h-11 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm font-semibold text-slate-500 outline-none" readonly />
                </label>
                <label class="block">
                  <span class="text-xs font-black text-slate-500">用户 ID</span>
                  <input :value="editingUser.id" class="mt-2 h-11 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm font-semibold text-slate-500 outline-none" readonly />
                </label>
              </div>

              <div class="grid gap-4 sm:grid-cols-2">
                <label class="block">
                  <span class="text-xs font-black text-slate-500">邮箱</span>
                  <input v-model="draftOf(editingUser).email" class="mt-2 h-11 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-800 outline-none transition focus:border-sky-300 focus:ring-4 focus:ring-sky-100" type="email" placeholder="用户邮箱" />
                </label>
                <label class="block">
                  <span class="text-xs font-black text-slate-500">角色</span>
                  <select v-model="draftOf(editingUser).role" class="mt-2 h-11 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm font-black text-slate-800 outline-none transition focus:border-sky-300 focus:ring-4 focus:ring-sky-100">
                    <option value="USER">普通用户</option>
                    <option value="ADMIN">管理员</option>
                  </select>
                </label>
              </div>

              <div class="grid gap-4 sm:grid-cols-2">
                <label class="block">
                  <span class="text-xs font-black text-slate-500">余额</span>
                  <input v-model.number="draftOf(editingUser).balance" class="mt-2 h-11 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm font-black text-slate-800 outline-none transition focus:border-sky-300 focus:ring-4 focus:ring-sky-100" min="0" step="0.000001" type="number" />
                </label>
                <label class="block">
                  <span class="text-xs font-black text-slate-500">新密码</span>
                  <input v-model="draftOf(editingUser).password" class="mt-2 h-11 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-800 outline-none transition focus:border-sky-300 focus:ring-4 focus:ring-sky-100" type="password" placeholder="留空不修改" />
                </label>
              </div>

              <div class="grid gap-4 sm:grid-cols-2">
                <label class="block">
                  <span class="text-xs font-black text-slate-500">邀请码</span>
                  <input :value="editingUser.invitationCode || '-'" class="mt-2 h-11 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm font-semibold text-slate-500 outline-none" readonly />
                </label>
                <label class="block">
                  <span class="text-xs font-black text-slate-500">邀请人 ID</span>
                  <input :value="editingUser.inviterId || '-'" class="mt-2 h-11 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm font-semibold text-slate-500 outline-none" readonly />
                </label>
              </div>
            </div>

            <div class="flex items-center justify-end gap-3 border-t border-slate-100 px-6 py-5">
              <button class="h-11 rounded-lg border border-slate-200 bg-white px-5 text-sm font-black text-slate-600 transition hover:bg-slate-50" @click="closeEditUser">取消</button>
              <button class="h-11 rounded-lg bg-slate-950 px-5 text-sm font-black text-white transition hover:bg-sky-600 disabled:opacity-60" :disabled="savingId === editingUser.id" @click="saveUser(editingUser)">
                {{ savingId === editingUser.id ? '保存中' : '保存修改' }}
              </button>
            </div>
          </section>
        </div>
      </Teleport>

      <Teleport to="body">
        <div v-if="selectedUser" class="fixed inset-0 z-[9999] h-screen w-screen bg-slate-950/35 backdrop-blur-sm" @click.self="closeApiUsage">
        <section class="flex h-screen w-screen flex-col bg-slate-50 shadow-2xl">
          <div class="sticky top-0 z-10 border-b border-slate-200 bg-white px-6 py-5">
            <div class="flex flex-wrap items-start justify-between gap-4">
              <div class="min-w-0">
                <p class="text-xs font-black uppercase tracking-[0.18em] text-sky-600">API Usage</p>
                <h2 class="mt-1 truncate text-2xl font-black text-slate-950">{{ selectedUser.username }} 的 API 使用</h2>
                <div class="mt-2 flex flex-wrap items-center gap-2 text-xs font-black text-slate-500">
                  <span class="rounded-full bg-emerald-50 px-3 py-1 text-emerald-700">余额 ${{ Number(selectedOverview?.balance || selectedUser.balance || 0).toFixed(6) }}</span>
                  <span class="rounded-full bg-slate-100 px-3 py-1">ID {{ selectedUser.id }}</span>
                  <span class="rounded-full bg-indigo-50 px-3 py-1 text-indigo-700">邀请码 {{ selectedUser.invitationCode || '-' }}</span>
                  <span v-if="selectedUser.inviterId" class="rounded-full bg-amber-50 px-3 py-1 text-amber-700">邀请人 ID {{ selectedUser.inviterId }}</span>
                  <span class="rounded-full bg-sky-50 px-3 py-1 text-sky-700">{{ tokens.length }} 个密钥</span>
                </div>
              </div>
              <button class="grid h-10 w-10 place-items-center rounded-lg border border-slate-200 bg-white text-xl font-black text-slate-400 transition hover:border-red-100 hover:bg-red-50 hover:text-red-600" @click="closeApiUsage">×</button>
            </div>
          </div>

          <div v-if="!selectedOverview" class="grid flex-1 place-items-center p-8">
            <RequestLoader label="正在加载真实 API 使用数据" :cell-size="18" />
          </div>

          <div v-else class="flex-1 space-y-5 overflow-y-auto p-5 lg:p-6">
            <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <article v-for="card in apiCards" :key="card.label" class="rounded-lg border bg-white p-4 shadow-sm" :class="cardTone(card.tone)">
                <p class="text-xs font-black opacity-80">{{ card.label }}</p>
                <p class="mt-2 text-2xl font-black">{{ card.value }}</p>
                <p class="mt-1 text-xs font-bold opacity-75">{{ card.sub }}</p>
              </article>
            </div>

            <div class="grid gap-5 xl:grid-cols-[0.9fr_1.1fr]">
              <section class="rounded-lg border border-slate-100 bg-white p-5 shadow-sm">
                <div class="flex items-center justify-between gap-3">
                  <h3 class="text-lg font-black text-slate-950">模型分布</h3>
                  <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-600">{{ models.length }} 个可用模型</span>
                </div>
                <div class="mt-4 grid max-h-[360px] gap-3 overflow-y-auto pr-1 md:grid-cols-2 xl:grid-cols-1">
                  <article v-for="model in models" :key="model.id" class="rounded-lg border border-slate-100 p-3 transition hover:border-sky-100 hover:bg-sky-50/40">
                    <div class="flex items-center justify-between gap-3">
                      <div class="min-w-0">
                        <p class="truncate font-black text-slate-950">{{ model.displayName || model.model }}</p>
                        <p class="mt-1 text-xs font-semibold text-slate-500">{{ model.model }}</p>
                      </div>
                      <span class="rounded-full px-2 py-1 text-xs font-black" :class="model.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'">{{ model.status }}</span>
                    </div>
                  </article>
                  <div v-if="!models.length" class="rounded-lg border border-dashed border-slate-200 p-8 text-center text-sm font-black text-slate-400">暂无可用模型</div>
                </div>
              </section>

              <section class="rounded-lg border border-slate-100 bg-white p-5 shadow-sm">
                <div class="flex items-center justify-between gap-3">
                  <h3 class="text-lg font-black text-slate-950">调用排行</h3>
                  <span class="text-xs font-black text-slate-400">按模型聚合</span>
                </div>
                <div class="mt-4 max-h-[360px] space-y-3 overflow-y-auto pr-1">
                  <article v-for="usage in modelUsage" :key="usage.model" class="rounded-lg border border-slate-100 p-3 transition hover:border-sky-100 hover:bg-sky-50/40">
                    <div class="flex items-center justify-between gap-4">
                      <p class="font-black text-slate-950">{{ usage.model }}</p>
                      <p class="text-sm font-black text-sky-600">{{ usage.requests }} 次</p>
                    </div>
                    <div class="mt-3 h-2 overflow-hidden rounded-full bg-slate-100">
                      <div class="h-full rounded-full bg-sky-500" :style="{ width: `${Math.min(100, Number(usage.totalTokens || 0) / Math.max(1, Number(selectedOverview?.totalTokens || 1)) * 100)}%` }"></div>
                    </div>
                    <p class="mt-2 text-xs font-semibold text-slate-500">{{ compact(usage.totalTokens) }} tokens · {{ money(usage.cost) }}</p>
                  </article>
                  <div v-if="!modelUsage.length" class="rounded-lg border border-dashed border-slate-200 p-8 text-center text-sm font-black text-slate-400">暂无调用数据</div>
                </div>
              </section>
            </div>

            <div class="grid gap-5 xl:grid-cols-[0.85fr_1.15fr]">
              <section class="rounded-lg border border-slate-100 bg-white p-5 shadow-sm">
                <h3 class="text-lg font-black text-slate-950">API 密钥</h3>
                <div class="mt-4 max-h-[340px] space-y-3 overflow-y-auto pr-1">
                  <article v-for="token in tokens" :key="token.id" class="rounded-lg border border-slate-100 p-3 transition hover:border-sky-100 hover:bg-sky-50/40">
                    <div class="flex items-center justify-between gap-3">
                      <div>
                        <p class="font-black text-slate-950">{{ token.name }}</p>
                        <p class="mt-1 text-xs font-semibold text-slate-500">{{ token.tokenPreview }} · {{ token.groups }}</p>
                      </div>
                      <span class="rounded-full px-2 py-1 text-xs font-black" :class="token.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'">{{ token.enabled ? '启用' : '停用' }}</span>
                    </div>
                    <p class="mt-3 text-xs font-semibold text-slate-500">{{ compact(token.requestCount) }} 次 · {{ compact(token.tokenCount) }} tokens · 今日 {{ money(token.todayCost) }}</p>
                  </article>
                  <div v-if="!tokens.length" class="rounded-lg border border-dashed border-slate-200 p-8 text-center text-sm font-black text-slate-400">暂无 API 密钥</div>
                </div>
              </section>

              <section class="rounded-lg border border-slate-100 bg-white p-5 shadow-sm">
                <h3 class="text-lg font-black text-slate-950">最近调用</h3>
                <div class="mt-4 overflow-x-auto">
                  <table class="w-full min-w-[780px] text-left text-sm">
                    <thead class="text-xs font-black text-slate-400">
                      <tr><th class="py-3">密钥</th><th>模型</th><th>Token</th><th>费用</th><th>耗时</th><th>时间</th></tr>
                    </thead>
                    <tbody class="divide-y divide-slate-100 font-semibold">
                      <tr v-for="log in logs.slice(0, 8)" :key="log.id">
                        <td class="py-3">{{ log.tokenName || '-' }}</td>
                        <td>{{ log.model }}</td>
                        <td>{{ compact(log.totalTokens) }}</td>
                        <td class="font-black text-emerald-600">{{ money(log.cost) }}</td>
                        <td>{{ ((log.durationMs || 0) / 1000).toFixed(2) }}s</td>
                        <td>{{ formatDate(log.createdAt) }}</td>
                      </tr>
                    </tbody>
                  </table>
                  <div v-if="!logs.length" class="rounded-lg border border-dashed border-slate-200 p-8 text-center text-sm font-black text-slate-400">暂无调用记录</div>
                </div>
              </section>
            </div>
          </div>
        </section>
        </div>
      </Teleport>
    </div>
  </AppLayout>
</template>
