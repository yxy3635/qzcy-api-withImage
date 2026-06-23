<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import BalanceCard from '@/components/BalanceCard.vue'
import Pagination from '@/components/Pagination.vue'
import { paymentApi } from '@/api/paymentApi'
import { useAuthStore } from '@/store/authStore'
import { useToast } from '@/composables/useToast'
import type { PaymentRecord } from '@/types'

const auth = useAuthStore()
const toast = useToast()
const amount = ref(10)
const amountPreset = ref<number | 'custom'>(10)
const type = ref('alipay')
const error = ref('')
const successModalOpen = ref(false)
const successAmount = ref('')
const successOrderId = ref('')
const returnedPaymentParams = ref<Record<string, string> | null>(null)
const records = ref<PaymentRecord[]>([])
const current = ref(1)
const pages = ref(1)
const rechargePresets = [1, 5, 10, 100]
const paymentOptions = ref([
  { value: 'alipay', label: '支付宝', desc: '推荐使用支付宝扫码支付', enabled: true },
  { value: 'wxpay', label: '微信支付', desc: '使用微信完成余额充值', enabled: true },
  { value: 'qqpay', label: 'QQ钱包', desc: '使用 QQ 钱包支付', enabled: false }
])

const enabledPaymentOptions = () => paymentOptions.value.filter((item) => item.enabled)

function selectAmountPreset(value: number | 'custom') {
  amountPreset.value = value
  if (typeof value === 'number') {
    amount.value = value
  }
}

async function recharge() {
  error.value = ''
  try {
    const { data } = await paymentApi.recharge(amount.value, type.value)
    const paymentUrl = data.data.paymentUrl ? String(data.data.paymentUrl) : ''
    if (paymentUrl) {
      window.location.href = paymentUrl
      return
    }
    toast.success(String(data.data.message || '操作完成'))
    await auth.refreshUser()
    await loadHistory()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建支付订单失败'
    toast.error(error.value)
  }
}

async function loadHistory(page = 1) {
  const { data } = await paymentApi.history(page, 10)
  records.value = data.data.records
  current.value = data.data.current
  pages.value = data.data.pages
}

async function loadPaymentConfig() {
  const { data } = await paymentApi.config()
  paymentOptions.value = paymentOptions.value.map((item) => ({
    ...item,
    enabled:
      item.value === 'alipay'
        ? Boolean(data.data.alipayEnabled)
        : item.value === 'wxpay'
          ? Boolean(data.data.wxpayEnabled)
          : Boolean(data.data.qqpayEnabled)
  }))
  if (!enabledPaymentOptions().some((item) => item.value === type.value)) {
    type.value = enabledPaymentOptions()[0]?.value || 'alipay'
  }
}

function paymentTypeText(value: string) {
  if (value === 'alipay') return '支付宝'
  if (value === 'wxpay') return '微信支付'
  if (value === 'qqpay') return 'QQ钱包'
  if (value === 'referral_rebate') return '邀请返利'
  if (value === 'balance') return '余额扣费'
  if (value === 'image_refund') return '生图失败退款'
  return value || '-'
}

function handlePaymentReturn() {
  const params = new URLSearchParams(window.location.search)
  if (params.get('trade_status') !== 'TRADE_SUCCESS') {
    return false
  }
  returnedPaymentParams.value = Object.fromEntries(params.entries())
  successAmount.value = params.get('money') || ''
  successOrderId.value = params.get('out_trade_no') || ''
  successModalOpen.value = true
  window.history.replaceState({}, document.title, window.location.pathname)
  return true
}

function sleep(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

function isSelectedOrderCompleted() {
  return records.value.some((record) => String(record.id) === successOrderId.value && record.status === 'completed')
}

async function confirmReturnedPayment() {
  const payload = returnedPaymentParams.value
  if (!payload || payload.trade_status !== 'TRADE_SUCCESS') {
    return
  }
  try {
    await paymentApi.notify(payload)
  } catch (err) {
    console.error('payment notify confirm failed', err)
  }
  for (let index = 0; index < 5; index += 1) {
    await Promise.all([auth.refreshUser(), loadHistory()])
    if (isSelectedOrderCompleted()) {
      break
    }
    await sleep(1000)
  }
}

onMounted(async () => {
  const returnedFromPayment = handlePaymentReturn()
  await Promise.all([loadPaymentConfig(), auth.refreshUser()])
  await loadHistory()
  if (returnedFromPayment) {
    await confirmReturnedPayment()
    toast.success('支付成功，余额到账状态请以支付回调和余额刷新结果为准。')
  }
})
</script>

<template>
  <AppLayout>
    <div class="grid gap-6 lg:grid-cols-[380px_1fr]">
      <div class="space-y-4">
        <div>
          <p class="text-sm font-bold uppercase tracking-[0.22em] text-sky-600">余额中心</p>
          <h1 class="mt-2 text-3xl font-black tracking-tight sm:text-4xl">充值与支付</h1>
          <p class="mt-2 text-sm text-slate-500">管理创作余额和第三方支付记录。</p>
        </div>
        <BalanceCard :balance="auth.userInfo?.balance || 0" />
        <section class="soft-card space-y-4 p-5">
          <div>
            <label class="text-sm font-semibold text-slate-600">充值金额</label>
            <div class="mt-2 grid grid-cols-2 gap-2">
              <button
                v-for="preset in rechargePresets"
                :key="preset"
                class="h-12 rounded-2xl border text-sm font-black transition"
                :class="amountPreset === preset ? 'border-sky-400 bg-sky-50 text-sky-700 shadow-sm' : 'border-slate-200 bg-white text-slate-700 hover:border-sky-200 hover:bg-sky-50/60'"
                type="button"
                @click="selectAmountPreset(preset)"
              >
                ￥{{ preset }}
              </button>
              <button
                class="h-12 rounded-2xl border text-sm font-black transition"
                :class="amountPreset === 'custom' ? 'border-sky-400 bg-sky-50 text-sky-700 shadow-sm' : 'border-slate-200 bg-white text-slate-700 hover:border-sky-200 hover:bg-sky-50/60'"
                type="button"
                @click="selectAmountPreset('custom')"
              >
                自定义
              </button>
            </div>
            <input v-if="amountPreset === 'custom'" v-model.number="amount" class="input mt-2 rounded-2xl" type="number" min="0.01" step="0.01" placeholder="输入充值金额" />
          </div>
          <div>
            <label class="text-sm font-semibold text-slate-600">支付方式</label>
            <div class="mt-2 grid gap-2">
              <button
                v-for="option in enabledPaymentOptions()"
                :key="option.value"
                class="flex items-center justify-between rounded-2xl border px-4 py-3 text-left transition"
                :class="type === option.value ? 'border-sky-400 bg-sky-50 text-sky-700 shadow-sm' : 'border-slate-200 bg-white text-slate-700 hover:border-sky-200 hover:bg-sky-50/60'"
                type="button"
                @click="type = option.value"
              >
                <span>
                  <span class="block text-sm font-black">{{ option.label }}</span>
                  <span class="mt-1 block text-xs font-semibold text-slate-500">{{ option.desc }}</span>
                </span>
                <span class="grid h-5 w-5 place-items-center rounded-full border" :class="type === option.value ? 'border-sky-500 bg-sky-500' : 'border-slate-300'">
                  <span v-if="type === option.value" class="h-2 w-2 rounded-full bg-white"></span>
                </span>
              </button>
              <p v-if="enabledPaymentOptions().length === 0" class="rounded-2xl bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-700">暂无可用支付方式，请联系管理员。</p>
            </div>
          </div>
          <button class="w-full rounded-full bg-sky-500 px-5 py-3 text-sm font-black text-white shadow-[0_18px_50px_rgba(14,165,233,0.24)] transition hover:-translate-y-0.5 hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-60" :disabled="enabledPaymentOptions().length === 0" @click="recharge">创建支付订单</button>
          <p v-if="error" class="rounded-2xl bg-red-50 px-3 py-2 text-sm font-semibold text-red-600">{{ error }}</p>
        </section>
      </div>
      <section class="soft-card overflow-hidden">
        <div class="border-b border-slate-100 bg-white/70 p-5">
          <h2 class="text-2xl font-black">支付记录</h2>
          <p class="mt-1 text-sm text-slate-500">第三方支付完成后余额会通过支付回调入账。</p>
        </div>
        <div class="divide-y divide-slate-100">
          <div v-for="record in records" :key="record.id" class="interactive-row grid gap-2 p-4 text-sm md:grid-cols-[120px_1fr_120px_180px] md:p-5">
            <span class="font-black text-slate-950">￥{{ Number(record.amount).toFixed(6) }}</span>
            <span class="text-slate-600">{{ paymentTypeText(record.type) }}</span>
            <span class="w-fit rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600">{{ record.status }}</span>
            <span class="text-slate-500">{{ record.createdAt }}</span>
          </div>
          <div v-if="records.length === 0" class="p-8 text-sm text-slate-500">暂无支付记录</div>
        </div>
        <div class="border-t border-slate-100 bg-white/70 p-4"><Pagination :current="current" :pages="pages" @change="loadHistory" /></div>
      </section>
    </div>

    <div v-if="successModalOpen" class="fixed inset-0 z-50 grid place-items-center bg-slate-950/40 px-4 backdrop-blur-sm">
      <section class="w-full max-w-md rounded-[28px] bg-white p-6 text-center shadow-[0_28px_90px_rgba(15,23,42,0.24)]">
        <div class="mx-auto grid h-14 w-14 place-items-center rounded-full bg-emerald-50 text-emerald-600">
          <svg class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <h2 class="mt-5 text-2xl font-black text-slate-950">支付成功</h2>
        <p class="mt-2 text-sm font-semibold text-slate-500">你的充值订单已完成支付。</p>
        <div class="mt-5 rounded-2xl bg-slate-50 p-4 text-left text-sm font-semibold text-slate-600">
          <div class="flex justify-between gap-3">
            <span>支付金额</span>
            <span class="font-black text-slate-950">￥{{ successAmount || '-' }}</span>
          </div>
          <div class="mt-3 flex justify-between gap-3">
            <span>订单号</span>
            <span class="font-black text-slate-950">{{ successOrderId || '-' }}</span>
          </div>
        </div>
        <button class="mt-6 h-12 w-full rounded-2xl bg-sky-500 text-sm font-black text-white shadow-[0_18px_50px_rgba(14,165,233,0.22)] transition hover:bg-sky-600" @click="successModalOpen = false">
          我知道了
        </button>
      </section>
    </div>
  </AppLayout>
</template>
