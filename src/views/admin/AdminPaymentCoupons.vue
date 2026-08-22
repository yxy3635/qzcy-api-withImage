<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'
import AppConfirmDialog from '@/components/AppConfirmDialog.vue'
import Pagination from '@/components/Pagination.vue'
import RequestLoader from '@/components/RequestLoader.vue'
import { adminApi } from '@/api/adminApi'
import { useToast } from '@/composables/useToast'
import type { AdminRechargeCoupon, PageResult } from '@/types'

const toast = useToast()

interface CouponDraft {
  code: string
  discountPercent: number
  maxUsesPerUser: number
  maxDiscountAmount: number
  enabled: boolean
}

const coupons = ref<PageResult<AdminRechargeCoupon> | null>(null)
const keyword = ref('')
const pageSize = ref(20)
const loading = ref(false)
const saving = ref(false)
const randomLoading = ref(false)
const error = ref('')
const editingId = ref<number | null>(null)
const editingCoupon = ref<AdminRechargeCoupon | null>(null)
const deleteTarget = ref<AdminRechargeCoupon | null>(null)
const deleting = ref(false)
const form = reactive<CouponDraft>({
  code: '',
  discountPercent: 80,
  maxUsesPerUser: 0,
  maxDiscountAmount: 0,
  enabled: true
})

function resetForm() {
  editingId.value = null
  editingCoupon.value = null
  form.code = ''
  form.discountPercent = 80
  form.maxUsesPerUser = 0
  form.maxDiscountAmount = 0
  form.enabled = true
}

function editCoupon(coupon: AdminRechargeCoupon) {
  editingId.value = coupon.id
  editingCoupon.value = coupon
  form.code = coupon.code
  form.discountPercent = Number(coupon.discountPercent || 0)
  form.maxUsesPerUser = Number(coupon.maxUsesPerUser || 0)
  form.maxDiscountAmount = Number(coupon.maxDiscountAmount || 0)
  form.enabled = Boolean(coupon.enabled)
}

function formatDate(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

function formatPercent(value: number | undefined) {
  return Number(value || 0).toFixed(2).replace(/\.00$/, '')
}

function previewPayable() {
  return Math.max(0, 100 - previewDiscountValue()).toFixed(2)
}

function previewDiscount() {
  return previewDiscountValue().toFixed(2)
}

function previewDiscountValue() {
  const percent = Number(form.discountPercent || 0)
  const discount = Math.max(0, 100 - percent)
  const cap = Number(form.maxDiscountAmount || 0)
  return cap > 0 ? Math.min(discount, cap) : discount
}

async function load(page = 1) {
  loading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.paymentCoupons(page, pageSize.value, keyword.value.trim())
    coupons.value = data.data
  } catch (err) {
    error.value = err instanceof Error ? err.message : '优惠码列表加载失败'
  } finally {
    loading.value = false
  }
}

async function search() {
  await load(1)
}

async function generateCode() {
  randomLoading.value = true
  try {
    const { data } = await adminApi.randomPaymentCouponCode()
    form.code = data.data
    toast.success('已生成新的随机优惠码')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '随机优惠码生成失败')
  } finally {
    randomLoading.value = false
  }
}

async function save() {
  error.value = ''
  const code = form.code.trim()
  const discountPercent = Number(form.discountPercent)
  const maxUsesPerUser = Number(form.maxUsesPerUser)
  const maxDiscountAmount = Number(form.maxDiscountAmount)
  if (!code) {
    error.value = '优惠码不能为空'
    toast.warning(error.value)
    return
  }
  if (!Number.isFinite(discountPercent) || discountPercent <= 0 || discountPercent > 100) {
    error.value = '实付比例必须大于 0 且不超过 100'
    toast.warning(error.value)
    return
  }
  if (!Number.isInteger(maxUsesPerUser) || maxUsesPerUser < 0) {
    error.value = '每用户最多使用次数必须是大于等于 0 的整数'
    toast.warning(error.value)
    return
  }
  if (!Number.isFinite(maxDiscountAmount) || maxDiscountAmount < 0) {
    error.value = '优惠上限必须是大于等于 0 的金额'
    toast.warning(error.value)
    return
  }

  saving.value = true
  try {
    const payload = { code, discountPercent, maxUsesPerUser, maxDiscountAmount, enabled: form.enabled }
    if (editingId.value) {
      await adminApi.updatePaymentCoupon(editingId.value, payload)
      toast.success('优惠码已更新')
    } else {
      await adminApi.createPaymentCoupon(payload)
      toast.success('优惠码已创建')
    }
    resetForm()
    await load(1)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '优惠码保存失败'
    toast.error(error.value)
  } finally {
    saving.value = false
  }
}

function requestDelete(coupon: AdminRechargeCoupon) {
  deleteTarget.value = coupon
}

function closeDelete() {
  if (!deleting.value) deleteTarget.value = null
}

async function confirmDelete() {
  const coupon = deleteTarget.value
  if (!coupon) return
  deleting.value = true
  try {
    await adminApi.deletePaymentCoupon(coupon.id)
    toast.success('优惠码已删除')
    deleteTarget.value = null
    const current = coupons.value?.current || 1
    await load(current)
    if (coupons.value && coupons.value.records.length === 0 && current > 1) {
      await load(current - 1)
    }
    if (editingId.value === coupon.id) resetForm()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '优惠码删除失败'
    toast.error(error.value)
  } finally {
    deleting.value = false
  }
}

onMounted(load)
</script>

<template>
  <AppLayout admin>
    <div class="page-enter">
      <div class="flex flex-wrap items-end justify-between gap-5">
        <div>
          <p class="text-sm font-black tracking-[0.22em] text-sky-600">支付管理 / 优惠码</p>
          <h1 class="mt-2 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">充值优惠码</h1>
          <p class="mt-3 text-sm font-medium text-slate-500">创建充值优惠码，设置实付比例、优惠上限和每用户使用次数。</p>
        </div>
        <div class="flex w-full flex-col gap-3 sm:w-auto sm:flex-row">
          <RouterLink class="inline-flex h-12 items-center justify-center rounded-2xl border border-slate-200 bg-white px-5 text-sm font-black text-slate-700 shadow-sm transition hover:border-sky-200 hover:bg-sky-50" to="/admin/payment">返回支付设置</RouterLink>
          <button class="h-12 rounded-2xl border border-slate-200 bg-white px-5 text-sm font-black text-slate-700 shadow-sm transition hover:border-sky-200 hover:bg-sky-50" type="button" @click="load()">刷新列表</button>
        </div>
      </div>

      <p v-if="error" class="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{{ error }}</p>

      <div class="mt-6 grid gap-6 xl:grid-cols-[390px_minmax(0,1fr)]">
        <section class="rounded-[28px] border border-white/80 bg-white/86 p-5 shadow-[0_24px_80px_rgba(15,23,42,0.08)] backdrop-blur-2xl sm:p-6">
          <div class="flex items-start justify-between gap-3">
            <div>
              <p class="text-xs font-black uppercase tracking-[0.18em] text-sky-600">{{ editingId ? '编辑规则' : '新建规则' }}</p>
              <h2 class="mt-2 text-2xl font-black text-slate-950">{{ editingId ? '编辑优惠码' : '创建优惠码' }}</h2>
            </div>
            <button v-if="editingId" class="text-xs font-black text-slate-500 transition hover:text-sky-600" type="button" @click="resetForm">新建</button>
          </div>

          <div class="mt-5 space-y-4">
            <label class="block">
              <span class="text-xs font-black text-slate-500">优惠码</span>
              <div class="mt-2 flex gap-2">
                <input v-model="form.code" class="h-12 min-w-0 flex-1 rounded-2xl border border-slate-200 bg-slate-50/80 px-4 font-mono text-sm font-black outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400" :disabled="Boolean(editingId && (editingCoupon?.usedCount || 0) > 0)" maxlength="255" placeholder="手动填写任意格式" />
                <button class="h-12 shrink-0 rounded-2xl border border-sky-200 bg-sky-50 px-3 text-xs font-black text-sky-700 transition hover:border-sky-300 hover:bg-sky-100 disabled:cursor-not-allowed disabled:opacity-60" type="button" :disabled="randomLoading || Boolean(editingId && (editingCoupon?.usedCount || 0) > 0)" @click="generateCode">{{ randomLoading ? '生成中' : '随机生成' }}</button>
              </div>
              <p class="mt-2 text-xs font-semibold leading-5 text-slate-500">随机码为 8 位数字与大小写字母组合；手动编码不限制格式，最多 255 个字符。</p>
              <p v-if="editingCoupon && editingCoupon.usedCount > 0" class="mt-2 text-xs font-bold text-amber-600">该优惠码已有使用记录，编码不可修改，但仍可调整折扣、次数和启用状态。</p>
            </label>

            <label class="block">
              <span class="text-xs font-black text-slate-500">折扣比例（实付百分比）</span>
              <div class="relative mt-2">
                <input v-model.number="form.discountPercent" class="h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 pr-10 text-sm font-black outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" max="100" min="0.01" step="0.01" type="number" />
                <span class="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-sm font-black text-slate-400">%</span>
              </div>
              <p class="mt-2 text-xs font-semibold leading-5 text-slate-500">例如设置 80%，用户充值 ￥100 时实际支付 ￥80，即优惠 ￥20。</p>
            </label>

            <label class="block">
              <span class="text-xs font-black text-slate-500">每个用户最多使用次数</span>
              <div class="relative mt-2">
                <input v-model.number="form.maxUsesPerUser" class="h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 pr-16 text-sm font-black outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" min="0" step="1" type="number" />
                <span class="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-xs font-black text-slate-400">0 = 不限</span>
              </div>
            </label>

            <label class="block">
              <span class="text-xs font-black text-slate-500">优惠上限（元）</span>
              <div class="relative mt-2">
                <input v-model.number="form.maxDiscountAmount" class="h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 pr-16 text-sm font-black outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" min="0" step="0.01" type="number" />
                <span class="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-xs font-black text-slate-400">0 = 不限</span>
              </div>
              <p class="mt-2 text-xs font-semibold leading-5 text-slate-500">只限制实际优惠金额。例如 85% 实付、上限 ￥2，充值 ￥100 最多优惠 ￥2。</p>
            </label>

            <label class="flex cursor-pointer items-center justify-between rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
              <span>
                <span class="block text-sm font-black text-slate-800">立即启用</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">停用后用户端不会接受该优惠码。</span>
              </span>
              <input v-model="form.enabled" class="h-5 w-5 accent-sky-500" type="checkbox" />
            </label>

            <div class="rounded-2xl border border-sky-100 bg-sky-50/70 p-4">
              <div class="flex items-center justify-between gap-3 text-xs font-black text-sky-700">
                <span>示例试算</span>
                <span>{{ formatPercent(form.discountPercent) }}% 实付</span>
              </div>
              <div class="mt-3 flex items-end justify-between gap-3">
                <span class="text-sm font-semibold text-slate-600">充值 ￥100.00</span>
                <span class="text-xl font-black text-sky-700">￥{{ previewPayable() }}</span>
              </div>
              <p class="mt-2 text-xs font-bold text-emerald-600">用户优惠 ￥{{ previewDiscount() }}</p>
            </div>

            <button class="h-12 w-full rounded-2xl bg-slate-950 text-sm font-black text-white shadow-[0_18px_45px_rgba(15,23,42,0.16)] transition hover:-translate-y-0.5 hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-60" type="button" :disabled="saving" @click="save">{{ saving ? '保存中…' : editingId ? '保存优惠码' : '创建优惠码' }}</button>
          </div>
        </section>

        <section class="min-w-0 overflow-hidden rounded-[28px] border border-white/80 bg-white/86 shadow-[0_24px_80px_rgba(15,23,42,0.08)] backdrop-blur-2xl">
          <header class="flex flex-wrap items-end justify-between gap-4 border-b border-slate-100 p-5 sm:p-6">
            <div>
              <p class="text-xs font-black uppercase tracking-[0.18em] text-slate-400">规则列表</p>
              <h2 class="mt-2 text-2xl font-black text-slate-950">已创建的优惠码</h2>
            </div>
            <span class="rounded-full bg-slate-100 px-3 py-1.5 text-xs font-black text-slate-600">共 {{ coupons?.total || 0 }} 条</span>
          </header>

          <div class="border-b border-slate-100 bg-slate-50/70 p-5 sm:p-6">
            <div class="flex flex-col gap-3 sm:flex-row">
              <input v-model="keyword" class="h-11 min-w-0 flex-1 rounded-2xl border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-sky-400 focus:ring-4 focus:ring-sky-100" placeholder="搜索优惠码" @keyup.enter="search" />
              <select v-model.number="pageSize" class="h-11 rounded-2xl border border-slate-200 bg-white px-3 text-sm font-black text-slate-700 outline-none focus:border-sky-400 focus:ring-4 focus:ring-sky-100" aria-label="每页条数" @change="search">
                <option :value="20">每页 20 条</option>
                <option :value="50">每页 50 条</option>
                <option :value="100">每页 100 条</option>
              </select>
              <button class="h-11 rounded-2xl bg-sky-500 px-5 text-sm font-black text-white shadow-[0_10px_24px_rgba(14,165,233,0.2)] transition hover:bg-sky-600" type="button" @click="search">查询</button>
            </div>
          </div>

          <RequestLoader v-if="loading" class="p-12" label="正在加载优惠码" :cell-size="16" />
          <div v-else-if="!coupons?.records.length" class="p-12 text-center text-sm font-semibold text-slate-500">暂无优惠码</div>
          <div v-else class="overflow-x-auto">
            <table class="w-full min-w-[960px] text-left text-sm">
              <thead class="bg-slate-50 text-xs font-black uppercase tracking-[0.12em] text-slate-500">
                <tr>
                  <th class="px-5 py-4">优惠码</th>
                  <th class="px-5 py-4">折扣</th>
                  <th class="px-5 py-4">优惠上限</th>
                  <th class="px-5 py-4">使用次数</th>
                  <th class="px-5 py-4">状态</th>
                  <th class="px-5 py-4">创建时间</th>
                  <th class="px-5 py-4 text-right">操作</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr v-for="coupon in coupons.records" :key="coupon.id" class="transition hover:bg-sky-50/60">
                  <td class="px-5 py-4"><p class="max-w-[220px] truncate font-mono font-black text-slate-900" :title="coupon.code">{{ coupon.code }}</p><p class="mt-1 text-xs font-semibold text-slate-400">ID #{{ coupon.id }}</p></td>
                  <td class="px-5 py-4"><p class="font-black text-sky-700">{{ formatPercent(coupon.discountPercent) }}% 实付</p><p class="mt-1 text-xs font-semibold text-emerald-600">优惠 {{ (100 - Number(coupon.discountPercent || 0)).toFixed(2).replace(/\.00$/, '') }}%</p></td>
                  <td class="px-5 py-4"><p class="font-black text-slate-900">{{ Number(coupon.maxDiscountAmount || 0) > 0 ? `￥${Number(coupon.maxDiscountAmount).toFixed(2)}` : '不限' }}</p><p class="mt-1 text-xs font-semibold text-slate-400">按实际优惠金额封顶</p></td>
                  <td class="px-5 py-4"><p class="font-black text-slate-900">{{ coupon.usedCount }} 次</p><p class="mt-1 text-xs font-semibold text-slate-400">{{ coupon.maxUsesPerUser > 0 ? `每用户最多 ${coupon.maxUsesPerUser} 次` : '每用户不限次数' }}</p></td>
                  <td class="px-5 py-4"><span class="rounded-full px-3 py-1 text-xs font-black" :class="coupon.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-200 text-slate-600'">{{ coupon.enabled ? '已启用' : '已停用' }}</span></td>
                  <td class="px-5 py-4 text-xs font-semibold text-slate-500">{{ formatDate(coupon.createdAt) }}</td>
                  <td class="px-5 py-4"><div class="flex justify-end gap-3 text-xs font-black"><button class="text-sky-600 transition hover:text-sky-800" type="button" @click="editCoupon(coupon)">编辑</button><button class="text-rose-600 transition hover:text-rose-800" type="button" title="删除优惠码" @click="requestDelete(coupon)">删除</button></div></td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="coupons && coupons.pages > 1" class="border-t border-slate-100 p-4 sm:p-5"><Pagination :current="coupons.current" :pages="coupons.pages" @change="load" /></div>
        </section>
      </div>
    </div>

    <AppConfirmDialog
      :open="Boolean(deleteTarget)"
      title="删除这个优惠码？"
      description="管理员可以删除已使用过的优惠码，删除后无法恢复。"
      confirm-label="确认删除"
      tone="danger"
      :subject="deleteTarget?.code"
      :loading="deleting"
      @cancel="closeDelete"
      @confirm="confirmDelete"
    />
  </AppLayout>
</template>
