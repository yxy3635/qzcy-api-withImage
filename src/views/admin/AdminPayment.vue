<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'
import Pagination from '@/components/Pagination.vue'
import RequestLoader from '@/components/RequestLoader.vue'
import { adminApi } from '@/api/adminApi'
import { useToast } from '@/composables/useToast'
import type { AdminPaymentRecord, PaymentConfig } from '@/types'

const toast = useToast()
interface PaymentDraft {
  apiUrl: string
  merchantId: string
  merchantSecret: string
  registerGiftAmount: number
  referralRebateRate: number
  enabled: boolean
  alipayEnabled: boolean
  wxpayEnabled: boolean
  qqpayEnabled: boolean
}

const config = ref<PaymentConfig | null>(null)
const draft = reactive<PaymentDraft>({
  apiUrl: '',
  merchantId: '',
  merchantSecret: '',
  registerGiftAmount: 0,
  referralRebateRate: 0,
  enabled: false,
  alipayEnabled: true,
  wxpayEnabled: true,
  qqpayEnabled: false
})
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const recordsOpen = ref(false)
const paymentRecords = ref<AdminPaymentRecord[]>([])
const paymentKeyword = ref('')
const paymentStatus = ref('')
const paymentCurrent = ref(1)
const paymentPages = ref(1)
const paymentTotal = ref(0)
const paymentLoading = ref(false)
const paymentPageSize = ref(20)

const paymentStatusOptions = [
  { value: '', label: '全部状态' },
  { value: 'completed', label: '已完成' },
  { value: 'pending', label: '待支付' }
]

function setDraft(next: PaymentConfig) {
  draft.apiUrl = next.apiUrl || ''
  draft.merchantId = next.merchantId || ''
  draft.merchantSecret = ''
  draft.registerGiftAmount = Number(next.registerGiftAmount || 0)
  draft.referralRebateRate = Number(next.referralRebateRate || 0)
  draft.enabled = Boolean(next.enabled)
  draft.alipayEnabled = Boolean(next.alipayEnabled)
  draft.wxpayEnabled = Boolean(next.wxpayEnabled)
  draft.qqpayEnabled = Boolean(next.qqpayEnabled)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.paymentConfig()
    config.value = data.data
    setDraft(data.data)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '支付配置加载失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  error.value = ''
  if (draft.registerGiftAmount < 0) {
    error.value = '注册赠送金额不能小于0'
    toast.warning(error.value)
    return
  }
  if (draft.referralRebateRate < 0 || draft.referralRebateRate > 100) {
    error.value = '邀请返利比例必须在0-100之间'
    toast.warning(error.value)
    return
  }
  saving.value = true
  try {
    const { data } = await adminApi.updatePaymentConfig({
      apiUrl: draft.apiUrl,
      merchantId: draft.merchantId,
      merchantSecret: draft.merchantSecret || undefined,
      registerGiftAmount: draft.registerGiftAmount,
      referralRebateRate: draft.referralRebateRate,
      enabled: draft.enabled,
      alipayEnabled: draft.alipayEnabled,
      wxpayEnabled: draft.wxpayEnabled,
      qqpayEnabled: draft.qqpayEnabled
    })
    config.value = data.data
    setDraft(data.data)
    toast.success('支付配置已保存')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
    toast.error(error.value)
  } finally {
    saving.value = false
  }
}

async function loadPaymentRecords(page = 1) {
  paymentLoading.value = true
  try {
    const { data } = await adminApi.paymentRecords(page, paymentPageSize.value, paymentKeyword.value.trim(), paymentStatus.value)
    paymentRecords.value = data.data.records
    paymentCurrent.value = data.data.current
    paymentPages.value = data.data.pages
    paymentTotal.value = data.data.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载充值账单失败'
  } finally {
    paymentLoading.value = false
  }
}

function openPaymentRecords() {
  recordsOpen.value = true
  loadPaymentRecords(1)
}

function paymentTypeText(type: string) {
  if (type === 'alipay') return '支付宝'
  if (type === 'wxpay' || type === 'wechat') return '微信支付'
  if (type === 'qqpay') return 'QQ钱包'
  return type || '-'
}

function paymentStatusText(status: string) {
  if (status === 'completed') return '已完成'
  if (status === 'pending') return '待支付'
  return status || '-'
}

function paymentStatusClass(status: string) {
  return status === 'completed'
    ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
    : 'border-amber-200 bg-amber-50 text-amber-700'
}

function formatDate(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

onMounted(load)
</script>

<template>
  <AppLayout admin>
    <div class="page-enter">
      <div class="flex flex-wrap items-end justify-between gap-5">
        <div>
          <p class="text-sm font-black tracking-[0.22em] text-sky-600">支付管理</p>
          <h1 class="mt-2 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">第三方支付与注册赠送</h1>
          <p class="mt-3 text-sm font-medium text-slate-500">配置支付接口地址、商户信息，以及新用户注册后的赠送余额。</p>
        </div>
        <div class="flex w-full flex-col gap-3 sm:w-auto sm:flex-row">
          <RouterLink class="inline-flex h-12 items-center justify-center rounded-2xl border border-sky-200 bg-sky-50 px-5 text-sm font-black text-sky-700 shadow-sm transition hover:border-sky-300 hover:bg-sky-100" to="/admin/payment/coupons">
            优惠码管理
          </RouterLink>
          <button class="h-12 rounded-2xl bg-slate-950 px-5 text-sm font-black text-white shadow-[0_14px_32px_rgba(15,23,42,0.16)] transition hover:bg-sky-600" @click="openPaymentRecords">
            查询充值账单
          </button>
          <button class="h-12 rounded-2xl border border-slate-200 bg-white px-5 text-sm font-black text-slate-700 shadow-sm transition hover:border-sky-200 hover:bg-sky-50" @click="load">
            刷新配置
          </button>
        </div>
      </div>

      <p v-if="error" class="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{{ error }}</p>

      <section class="mt-6 rounded-[28px] border border-white/80 bg-white/86 p-5 shadow-[0_24px_80px_rgba(15,23,42,0.08)] backdrop-blur-2xl sm:mt-8 sm:p-6">
        <RequestLoader v-if="loading" class="p-10" label="正在读取配置" :cell-size="18" />
        <div v-else class="grid gap-5 lg:grid-cols-[1fr_320px]">
          <div class="space-y-4">
            <label class="block">
              <span class="text-xs font-black text-slate-500">接口地址</span>
              <input v-model="draft.apiUrl" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" placeholder="https://pay.example.com/api" />
            </label>

            <div class="grid gap-4 sm:grid-cols-2">
              <label class="block">
                <span class="text-xs font-black text-slate-500">商户ID</span>
                <input v-model="draft.merchantId" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" />
              </label>
              <label class="block">
                <span class="text-xs font-black text-slate-500">商户密钥</span>
                <input v-model="draft.merchantSecret" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" type="password" placeholder="留空则不修改" />
              </label>
            </div>

            <label class="block">
              <span class="text-xs font-black text-slate-500">新用户注册赠送余额</span>
              <div class="relative mt-2">
                <span class="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-sm font-black text-slate-400">￥</span>
                <input v-model.number="draft.registerGiftAmount" class="h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 pl-9 pr-4 text-sm font-black outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" min="0" step="0.000001" type="number" />
              </div>
            </label>

            <label class="block">
              <span class="text-xs font-black text-slate-500">邀请返利比例</span>
              <div class="relative mt-2">
                <input v-model.number="draft.referralRebateRate" class="h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 pr-10 text-sm font-black outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" max="100" min="0" step="0.01" type="number" />
                <span class="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-sm font-black text-slate-400">%</span>
              </div>
              <p class="mt-2 text-xs font-semibold text-slate-500">按被邀请人已完成充值金额的百分比返利到邀请人余额。</p>
            </label>

            <section class="rounded-3xl border border-slate-200 bg-white p-4">
              <div>
                <p class="text-sm font-black text-slate-800">支付方式管理</p>
                <p class="mt-1 text-xs font-semibold text-slate-500">开启后用户端才会展示对应支付方式。</p>
              </div>
              <div class="mt-4 grid gap-3 sm:grid-cols-3">
                <label class="flex cursor-pointer items-center justify-between rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
                  <span class="text-sm font-black text-slate-700">支付宝</span>
                  <input v-model="draft.alipayEnabled" class="h-5 w-5 accent-sky-500" type="checkbox" />
                </label>
                <label class="flex cursor-pointer items-center justify-between rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
                  <span class="text-sm font-black text-slate-700">微信</span>
                  <input v-model="draft.wxpayEnabled" class="h-5 w-5 accent-sky-500" type="checkbox" />
                </label>
                <label class="flex cursor-pointer items-center justify-between rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
                  <span class="text-sm font-black text-slate-700">QQ</span>
                  <input v-model="draft.qqpayEnabled" class="h-5 w-5 accent-sky-500" type="checkbox" />
                </label>
              </div>
            </section>

            <label class="flex cursor-pointer items-center justify-between rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
              <span>
                <span class="block text-sm font-black text-slate-800">启用第三方支付</span>
                <span class="mt-1 block text-xs font-semibold text-slate-500">启用后用户充值会创建第三方支付订单。</span>
              </span>
              <input v-model="draft.enabled" class="h-5 w-5 accent-sky-500" type="checkbox" />
            </label>

            <button
              class="h-12 w-full rounded-2xl bg-slate-950 text-sm font-black text-white shadow-[0_18px_45px_rgba(15,23,42,0.16)] transition hover:-translate-y-0.5 hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="saving"
              @click="save"
            >
              {{ saving ? '保存中' : '保存配置' }}
            </button>
          </div>

          <aside class="rounded-3xl border border-slate-200 bg-slate-50 p-5">
            <p class="text-sm font-black text-slate-500">当前状态</p>
            <div class="mt-4 space-y-3 text-sm font-semibold text-slate-600">
              <div class="flex justify-between gap-3"><span>支付状态</span><span class="font-black" :class="config?.enabled ? 'text-emerald-600' : 'text-slate-400'">{{ config?.enabled ? '已启用' : '未启用' }}</span></div>
              <div class="flex justify-between gap-3"><span>商户密钥</span><span class="font-black" :class="config?.merchantSecretConfigured ? 'text-emerald-600' : 'text-slate-400'">{{ config?.merchantSecretConfigured ? '已配置' : '未配置' }}</span></div>
              <div class="flex justify-between gap-3"><span>支付宝</span><span class="font-black" :class="config?.alipayEnabled ? 'text-emerald-600' : 'text-slate-400'">{{ config?.alipayEnabled ? '开启' : '关闭' }}</span></div>
              <div class="flex justify-between gap-3"><span>微信</span><span class="font-black" :class="config?.wxpayEnabled ? 'text-emerald-600' : 'text-slate-400'">{{ config?.wxpayEnabled ? '开启' : '关闭' }}</span></div>
              <div class="flex justify-between gap-3"><span>QQ</span><span class="font-black" :class="config?.qqpayEnabled ? 'text-emerald-600' : 'text-slate-400'">{{ config?.qqpayEnabled ? '开启' : '关闭' }}</span></div>
              <div class="flex justify-between gap-3"><span>注册赠送</span><span class="font-black text-slate-950">￥{{ Number(config?.registerGiftAmount || 0).toFixed(6) }}</span></div>
              <div class="flex justify-between gap-3"><span>邀请返利</span><span class="font-black text-slate-950">{{ Number(config?.referralRebateRate || 0).toFixed(2) }}%</span></div>
            </div>
            <p class="mt-5 text-xs font-semibold leading-6 text-slate-500">回调地址必须是公网可访问域名，localhost 只能本机访问，第三方支付平台无法回调。</p>
          </aside>
        </div>
      </section>
    </div>

    <Teleport to="body">
      <Transition name="zoom-fade">
        <div v-if="recordsOpen" class="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/30 p-3 backdrop-blur-[2px] sm:p-5" @click.self="recordsOpen = false">
          <section class="flex max-h-[calc(100vh-24px)] min-h-[420px] w-full max-w-[1320px] flex-col overflow-hidden rounded-[30px] border border-white/90 bg-white/90 shadow-[0_28px_90px_rgba(15,23,42,0.2)] backdrop-blur-2xl" role="dialog" aria-modal="true" aria-labelledby="payment-records-title">
            <header class="flex shrink-0 flex-wrap items-center justify-between gap-4 border-b border-slate-100 bg-white/75 px-5 py-5 text-slate-950 sm:px-7">
              <div class="min-w-0">
                <div class="flex items-center gap-3">
                  <span class="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-sky-50 text-sky-600">
                    <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M4 7.5A2.5 2.5 0 0 1 6.5 5h11A2.5 2.5 0 0 1 20 7.5v9a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 16.5v-9Z" /><path stroke-linecap="round" d="M4 9h16M8 14h3" /></svg>
                  </span>
                  <div class="min-w-0">
                    <p class="text-xs font-black tracking-[0.18em] text-sky-600">支付账单</p>
                    <h2 id="payment-records-title" class="mt-1 truncate text-xl font-black tracking-tight text-slate-950 sm:text-2xl">用户充值记录</h2>
                  </div>
                </div>
                <p class="mt-3 pl-[52px] text-sm font-semibold text-slate-500">仅显示第三方支付创建的充值订单，当前共 {{ paymentTotal }} 条。</p>
              </div>
              <div class="flex items-center gap-3">
                <span class="hidden rounded-full border border-sky-100 bg-sky-50 px-3 py-1.5 text-xs font-black text-sky-700 sm:inline-flex">充值流水</span>
                <button class="grid h-10 w-10 place-items-center rounded-xl border border-slate-200 bg-white/70 text-slate-400 transition hover:border-sky-200 hover:bg-sky-50 hover:text-sky-700" type="button" aria-label="关闭充值账单" @click="recordsOpen = false">
              <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path stroke-linecap="round" d="M6 6l12 12M18 6 6 18" /></svg>
                </button>
              </div>
            </header>

            <div class="shrink-0 border-b border-slate-100 bg-slate-50/65 px-5 py-4 sm:px-7">
              <div class="grid gap-3 md:grid-cols-[minmax(0,1fr)_150px_125px_88px]">
                <input v-model="paymentKeyword" class="h-11 rounded-2xl border border-slate-200 bg-white/85 px-4 text-sm font-semibold text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-sky-400 focus:bg-white focus:ring-4 focus:ring-sky-100" placeholder="搜索用户、邮箱、用户 ID 或订单号" @keyup.enter="loadPaymentRecords(1)" />
                <select v-model="paymentStatus" class="h-11 rounded-2xl border border-slate-200 bg-white/85 px-3 text-sm font-black text-slate-700 outline-none transition focus:border-sky-400 focus:bg-white focus:ring-4 focus:ring-sky-100" @change="loadPaymentRecords(1)">
                  <option v-for="option in paymentStatusOptions" :key="option.value || 'all'" :value="option.value">{{ option.label }}</option>
                </select>
                <select v-model.number="paymentPageSize" class="h-11 rounded-2xl border border-slate-200 bg-white/85 px-3 text-sm font-black text-slate-700 outline-none transition focus:border-sky-400 focus:bg-white focus:ring-4 focus:ring-sky-100" aria-label="每页条数" @change="loadPaymentRecords(1)">
                  <option :value="20">每页 20 条</option>
                  <option :value="50">每页 50 条</option>
                </select>
                <button class="h-11 rounded-2xl bg-sky-500 px-5 text-sm font-black text-white shadow-[0_10px_24px_rgba(14,165,233,0.2)] transition hover:bg-sky-600" type="button" @click="loadPaymentRecords(1)">查询</button>
              </div>
            </div>

            <div class="relative min-h-0 max-h-[calc(100vh-310px)] overflow-auto">
              <table class="w-full min-w-[940px] table-fixed text-left text-sm">
              <colgroup>
                <col class="w-[190px]" /><col class="w-[105px]" /><col class="w-[120px]" /><col class="w-[125px]" /><col class="w-[125px]" /><col class="w-[160px]" /><col />
              </colgroup>
              <thead class="sticky top-0 z-10 bg-slate-100 text-xs font-black uppercase tracking-[0.1em] text-slate-500">
                <tr>
                  <th class="px-5 py-3.5">用户</th><th>订单号</th><th>金额</th><th>支付方式</th><th>状态</th><th>创建时间</th><th class="pr-5">备注</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100 bg-white/80 font-semibold">
                <tr v-if="!paymentLoading && paymentRecords.length === 0"><td colspan="7" class="p-10 text-center text-sm text-slate-500">暂无符合条件的充值账单</td></tr>
                <tr v-for="record in paymentRecords" :key="record.id" class="h-[66px] transition hover:bg-sky-50/70">
                  <td class="px-5 py-3"><p class="truncate font-black text-slate-900">{{ record.username || '未知用户' }}</p><p class="mt-0.5 truncate text-xs text-slate-500">{{ record.email || `用户 ID: ${record.userId}` }}</p></td>
                  <td class="py-3 font-mono text-xs text-slate-600">#{{ record.id }}</td>
                  <td class="py-3 font-black text-emerald-600">￥{{ Number(record.amount || 0).toFixed(6) }}</td>
                  <td class="py-3 text-slate-700">{{ paymentTypeText(record.type) }}</td>
                  <td class="py-3"><span class="inline-flex rounded-full border px-3 py-1 text-xs font-black" :class="paymentStatusClass(record.status)">{{ paymentStatusText(record.status) }}</span></td>
                  <td class="py-3 text-slate-500">{{ formatDate(record.createdAt) }}</td>
                  <td class="py-3 pr-5"><p class="truncate text-slate-500" :title="record.remark || ''">{{ record.remark || '-' }}</p></td>
                </tr>
              </tbody>
              </table>
              <div v-if="paymentLoading" class="absolute inset-0 grid place-items-center bg-white/80">
                <div class="rounded-2xl border border-slate-200 bg-white px-8 py-6 shadow-lg"><RequestLoader label="正在加载充值账单" :cell-size="16" /></div>
              </div>
            </div>

            <footer class="flex shrink-0 items-center justify-between gap-4 border-t border-slate-100 bg-white/75 px-5 py-3.5 sm:px-7"><span class="hidden text-xs font-semibold text-slate-400 sm:block">共 {{ paymentTotal }} 条记录</span><Pagination :current="paymentCurrent" :pages="paymentPages" @change="loadPaymentRecords" /></footer>
          </section>
        </div>
      </Transition>
    </Teleport>
  </AppLayout>
</template>
