<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import BalanceCard from '@/components/BalanceCard.vue'
import Pagination from '@/components/Pagination.vue'
import RequestLoader from '@/components/RequestLoader.vue'
import { paymentApi } from '@/api/paymentApi'
import { useAuthStore } from '@/store/authStore'
import { useToast } from '@/composables/useToast'
import { clearPendingRecharge, isBackForwardNavigation, pendingRechargeOrderId, rememberPendingRecharge } from '@/utils/pendingRecharge'
import type { PaymentRecord, RechargeCouponPreview } from '@/types'

const auth = useAuthStore()
const toast = useToast()
const amount = ref(10)
const amountPreset = ref<number | 'custom'>(10)
const type = ref('alipay')
const couponCode = ref('')
const couponPreview = ref<RechargeCouponPreview | null>(null)
const couponPreviewLoading = ref(false)
const checkoutModalOpen = ref(false)
const error = ref('')
const successModalOpen = ref(false)
const successAmount = ref('')
const successOrderId = ref('')
const returnedPaymentParams = ref<Record<string, string> | null>(null)
const showHistoryModal = ref(false)
const pendingOrderDialogOpen = ref(false)
const pendingOrder = ref<PaymentRecord | null>(null)
const pendingOrderChecking = ref(false)
const pendingOrderActionLoading = ref(false)
const records = ref<PaymentRecord[]>([])
const current = ref(1)
const pages = ref(1)
const initialLoading = ref(true)
const historyLoading = ref(false)
const rechargeLoading = ref(false)
const rechargePresets = [1, 5, 10, 100]
const paymentOptions = ref([
  { value: 'alipay', label: '支付宝', desc: '推荐使用支付宝扫码支付', enabled: true },
  { value: 'wxpay', label: '微信支付', desc: '使用微信完成余额充值', enabled: true },
  { value: 'qqpay', label: 'QQ钱包', desc: '使用 QQ 钱包支付', enabled: false }
])

const enabledPaymentOptions = () => paymentOptions.value.filter((item) => item.enabled)

let couponPreviewTimer: number | undefined
let couponPreviewRequestId = 0

type PaymentReturn = {
  status: 'success' | 'cancelled'
  orderId: number
}

function localCouponPreview(): RechargeCouponPreview {
  const originalAmount = Number(amount.value || 0)
  return {
    valid: true,
    code: '',
    discountPercent: 100,
    originalAmount,
    discountAmount: 0,
    payableAmount: originalAmount,
    message: ''
  }
}

couponPreview.value = localCouponPreview()

function formatMoney(value: number | undefined, digits = 2) {
  const normalized = Number(value || 0)
  return Number.isFinite(normalized) ? normalized.toFixed(digits) : '0.00'
}

function selectAmountPreset(value: number | 'custom') {
  amountPreset.value = value
  if (typeof value === 'number') {
    amount.value = value
  }
}

async function recharge() {
  error.value = ''
  rechargeLoading.value = true
  try {
    const { data } = await paymentApi.recharge(amount.value, type.value, couponCode.value.trim())
    const paymentUrl = data.data.paymentUrl ? String(data.data.paymentUrl) : ''
    if (paymentUrl) {
      rememberPendingRecharge(data.data.orderId)
      window.location.href = paymentUrl
      return
    }
    checkoutModalOpen.value = false
    toast.success(String(data.data.message || '操作完成'))
    await auth.refreshUser()
    await loadHistory()
  } catch (err) {
    if (err instanceof Error && err.message.includes('上一个充值订单')) {
      if (await checkPendingRecharge()) return
    }
    error.value = err instanceof Error ? err.message : '创建支付订单失败'
    toast.error(error.value)
  } finally {
    rechargeLoading.value = false
  }
}

async function checkPendingRecharge() {
  pendingOrderChecking.value = true
  try {
    const { data } = await paymentApi.pendingRecharge()
    if (data.data) {
      pendingOrder.value = data.data
      pendingOrderDialogOpen.value = true
      return true
    }
    return false
  } catch (err) {
    error.value = err instanceof Error ? err.message : '无法检查未支付订单'
    toast.error(error.value)
    return true
  } finally {
    pendingOrderChecking.value = false
  }
}

async function cancelPendingOrderAndContinue() {
  const order = pendingOrder.value
  if (!order) return
  pendingOrderActionLoading.value = true
  try {
    await paymentApi.cancelRecharge(order.id)
    clearPendingRecharge(order.id)
    pendingOrderDialogOpen.value = false
    pendingOrder.value = null
    await recharge()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '取消上一个订单失败'
    toast.error(error.value)
  } finally {
    pendingOrderActionLoading.value = false
  }
}

async function loadCouponPreview(showError = false) {
  const requestId = ++couponPreviewRequestId
  const originalAmount = Number(amount.value || 0)
  if (!Number.isFinite(originalAmount) || originalAmount <= 0) {
    couponPreview.value = null
    couponPreviewLoading.value = false
    return null
  }
  const code = couponCode.value.trim()
  if (!code) {
    const preview = localCouponPreview()
    couponPreview.value = preview
    couponPreviewLoading.value = false
    return preview
  }
  couponPreviewLoading.value = true
  try {
    const { data } = await paymentApi.previewCoupon(originalAmount, code)
    const preview = data.data
    if (requestId === couponPreviewRequestId) {
      couponPreview.value = preview
    }
    return preview
  } catch (err) {
    const preview: RechargeCouponPreview = {
      valid: false,
      code,
      originalAmount,
      discountAmount: 0,
      payableAmount: originalAmount,
      message: err instanceof Error ? err.message : '优惠码校验失败'
    }
    if (requestId === couponPreviewRequestId) {
      couponPreview.value = preview
      if (showError) error.value = preview.message || '优惠码校验失败'
    }
    return preview
  } finally {
    if (requestId === couponPreviewRequestId) {
      couponPreviewLoading.value = false
    }
  }
}

function scheduleCouponPreview() {
  if (couponPreviewTimer !== undefined) {
    window.clearTimeout(couponPreviewTimer)
  }
  if (!couponCode.value.trim()) {
    couponPreview.value = localCouponPreview()
    couponPreviewLoading.value = false
    return
  }
  couponPreviewTimer = window.setTimeout(() => {
    void loadCouponPreview()
  }, 350)
}

async function openCheckout() {
  error.value = ''
  const preview = await loadCouponPreview(true)
  if (!preview) {
    error.value = '请输入有效的充值金额'
    toast.warning(error.value)
    return
  }
  if (couponCode.value.trim() && !preview.valid) {
    error.value = preview.message || '优惠码不可用'
    toast.warning(error.value)
    return
  }
  checkoutModalOpen.value = true
}

async function confirmRecharge() {
  if (await checkPendingRecharge()) return
  await recharge()
}

async function loadHistory(page = 1) {
  historyLoading.value = true
  try {
    const { data } = await paymentApi.history(page, 10)
    records.value = data.data.records
    current.value = data.data.current
    pages.value = data.data.pages
  } finally {
    historyLoading.value = false
  }
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
  if (value === 'admin_gift') return '管理员赠送'
  if (value === 'balance') return '余额扣费'
  if (value === 'image_refund') return '生图失败退款'
  return value || '-'
}

function paymentStatusText(value: string) {
  if (value === 'completed') return '已完成'
  if (value === 'pending') return '待支付'
  if (value === 'cancelled') return '已取消'
  if (value === 'failed') return '失败'
  return value || '-'
}

function parseOrderId(value: string | null) {
  const normalized = Number(value || 0)
  return Number.isInteger(normalized) && normalized > 0 ? normalized : null
}

function handlePaymentReturn(): PaymentReturn | null {
  const params = new URLSearchParams(window.location.search)
  const status = params.get('trade_status')?.toUpperCase()
  const orderId = parseOrderId(params.get('out_trade_no'))
  if (!status || !orderId) return null
  returnedPaymentParams.value = Object.fromEntries(params.entries())
  successAmount.value = params.get('money') || ''
  successOrderId.value = String(orderId)
  window.history.replaceState({}, document.title, window.location.pathname)
  if (status === 'TRADE_SUCCESS') {
    clearPendingRecharge(orderId)
    successModalOpen.value = true
    return { status: 'success', orderId }
  }
  return { status: 'cancelled', orderId }
}

async function cancelPendingRecharge(orderId: number) {
  try {
    await paymentApi.cancelRecharge(orderId)
    clearPendingRecharge(orderId)
  } catch (err) {
    console.warn('pending recharge cancellation failed', err)
  }
}

async function releaseStoredPendingRecharge() {
  const orderId = pendingRechargeOrderId()
  if (orderId) await cancelPendingRecharge(orderId)
}

function openHistory() {
  showHistoryModal.value = true
  void loadHistory(current.value)
}

function handlePageShow(event: PageTransitionEvent) {
  if (!event.persisted) return
  void releaseStoredPendingRecharge()
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
  try {
    const returnedFromPayment = handlePaymentReturn()
    await Promise.all([loadPaymentConfig(), auth.refreshUser()])
    if (returnedFromPayment?.status === 'cancelled') {
      await cancelPendingRecharge(returnedFromPayment.orderId)
    } else if (!returnedFromPayment && isBackForwardNavigation()) {
      await releaseStoredPendingRecharge()
    }
    await loadHistory()
    if (returnedFromPayment?.status === 'success') {
      await confirmReturnedPayment()
      toast.success('支付成功，余额到账状态请以支付回调和余额刷新结果为准。')
    }
    window.addEventListener('pageshow', handlePageShow)
  } finally {
    initialLoading.value = false
  }
})

watch([amount, couponCode], scheduleCouponPreview)

onBeforeUnmount(() => {
  if (couponPreviewTimer !== undefined) {
    window.clearTimeout(couponPreviewTimer)
  }
  window.removeEventListener('pageshow', handlePageShow)
})
</script>

<template>
  <AppLayout>
    <Transition name="zoom-fade">
      <div v-if="initialLoading" class="fixed inset-0 z-[60] grid place-items-center bg-white/55 backdrop-blur-[3px]">
        <RequestLoader label="正在加载余额与支付信息" :cell-size="16" />
      </div>
    </Transition>
    <div class="mx-auto w-full max-w-2xl">
      <div class="space-y-4">
        <div>
          <p class="text-sm font-bold uppercase tracking-[0.22em] text-sky-600">余额中心</p>
          <h1 class="mt-2 text-3xl font-black tracking-tight sm:text-4xl">充值与支付</h1>
          <p class="mt-2 text-sm text-slate-500">管理创作余额和第三方支付记录。</p>
        </div>
        <BalanceCard :balance="auth.userInfo?.balance || 0" />
        <section class="soft-card space-y-4 p-5">
          <div class="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 pb-4">
            <div>
              <h2 class="text-xl font-black text-slate-950">余额充值</h2>
              <p class="mt-1 text-xs font-semibold text-slate-500">选择金额后确认支付，优惠码会实时计算。</p>
            </div>
            <button class="inline-flex h-10 items-center gap-2 rounded-xl border border-sky-200 bg-sky-50 px-3.5 text-xs font-black text-sky-700 transition hover:border-sky-300 hover:bg-sky-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-400" type="button" @click="openHistory">
              <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M5 6h14M5 12h14M5 18h9" /><path d="m17 16 2 2 4-4" /></svg>
              查看充值记录
            </button>
          </div>
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
            <div class="flex items-center justify-between gap-3">
              <label class="text-sm font-semibold text-slate-600" for="recharge-coupon-code">优惠码 <span class="text-xs font-medium text-slate-400">可选</span></label>
              <span v-if="couponPreviewLoading" class="text-xs font-bold text-sky-600">正在计算…</span>
            </div>
            <div class="relative mt-2">
              <input id="recharge-coupon-code" v-model="couponCode" class="h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 pr-20 text-sm font-black uppercase tracking-[0.08em] outline-none transition placeholder:font-semibold placeholder:normal-case placeholder:tracking-normal focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" placeholder="输入优惠码" autocomplete="off" @keyup.enter="loadCouponPreview(true)" />
              <button v-if="couponCode" class="absolute right-3 top-1/2 -translate-y-1/2 rounded-lg px-2 py-1 text-xs font-black text-slate-400 transition hover:bg-slate-100 hover:text-slate-700" type="button" @click="couponCode = ''">清除</button>
            </div>
            <div v-if="couponPreview" class="mt-3 rounded-2xl border px-4 py-3 text-sm" :class="couponPreview.valid ? 'border-sky-100 bg-sky-50/70' : 'border-rose-100 bg-rose-50/70'">
              <div class="flex items-center justify-between gap-3">
                <span class="font-semibold text-slate-600">充值到账</span>
                <span class="font-black text-slate-900">￥{{ formatMoney(couponPreview.originalAmount) }}</span>
              </div>
              <div class="mt-2 flex items-center justify-between gap-3">
                <span class="font-semibold text-slate-600">优惠金额</span>
                <span class="font-black" :class="couponPreview.valid && couponPreview.discountAmount > 0 ? 'text-emerald-600' : 'text-slate-500'">-￥{{ formatMoney(couponPreview.discountAmount) }}</span>
              </div>
              <div class="mt-2 flex items-center justify-between gap-3 border-t border-current/10 pt-2">
                <span class="font-black text-slate-700">实际支付</span>
                <span class="text-lg font-black" :class="couponPreview.valid ? 'text-sky-700' : 'text-slate-900'">￥{{ formatMoney(couponPreview.payableAmount) }}</span>
              </div>
              <p v-if="couponCode.trim() && couponPreview.message" class="mt-2 text-xs font-bold" :class="couponPreview.valid ? 'text-sky-700' : 'text-rose-600'">{{ couponPreview.message }}</p>
            </div>
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
          <button class="w-full rounded-full bg-sky-500 px-5 py-3 text-sm font-black text-white shadow-[0_18px_50px_rgba(14,165,233,0.24)] transition hover:-translate-y-0.5 hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-60" :disabled="enabledPaymentOptions().length === 0 || rechargeLoading || couponPreviewLoading" @click="openCheckout">{{ couponPreviewLoading ? '正在计算优惠…' : '确认金额并充值' }}</button>
          <p v-if="error" class="rounded-2xl bg-red-50 px-3 py-2 text-sm font-semibold text-red-600">{{ error }}</p>
        </section>
      </div>
    </div>

    <Teleport to="body">
      <Transition name="zoom-fade">
        <div v-if="checkoutModalOpen" class="fixed inset-0 z-[100] grid place-items-center bg-slate-950/45 px-4 backdrop-blur-sm" @click.self="!rechargeLoading && (checkoutModalOpen = false)">
          <section class="w-full max-w-md overflow-hidden rounded-[28px] border border-white/80 bg-white shadow-[0_28px_90px_rgba(15,23,42,0.24)]" role="dialog" aria-modal="true" aria-labelledby="checkout-title">
            <header class="border-b border-slate-100 bg-sky-50/75 px-6 py-5">
              <p class="text-xs font-black uppercase tracking-[0.2em] text-sky-600">支付确认</p>
              <h2 id="checkout-title" class="mt-2 text-2xl font-black tracking-tight text-slate-950">确认充值金额</h2>
              <p class="mt-1 text-sm font-semibold text-slate-500">确认后将跳转到第三方支付页面。</p>
            </header>
            <div class="space-y-3 px-6 py-5 text-sm font-semibold text-slate-600">
              <div class="flex justify-between gap-4"><span>充值到账</span><span class="font-black text-slate-950">￥{{ formatMoney(couponPreview?.originalAmount || amount) }}</span></div>
              <div class="flex justify-between gap-4"><span>优惠金额</span><span class="font-black text-emerald-600">-￥{{ formatMoney(couponPreview?.discountAmount) }}</span></div>
              <div class="flex justify-between gap-4"><span>支付方式</span><span class="font-black text-slate-950">{{ paymentTypeText(type) }}</span></div>
              <div class="mt-2 flex items-end justify-between gap-4 border-t border-slate-100 pt-4">
                <span class="font-black text-slate-800">要支付的价格</span>
                <span class="text-3xl font-black tracking-tight text-sky-700">￥{{ formatMoney(couponPreview?.payableAmount || amount) }}</span>
              </div>
              <p v-if="couponCode.trim()" class="rounded-2xl bg-slate-50 px-3 py-2 text-xs font-bold text-slate-500">已使用优惠码：{{ couponCode.trim() }}</p>
              <p v-if="error" class="rounded-2xl bg-rose-50 px-3 py-2 text-xs font-bold text-rose-600">{{ error }}</p>
            </div>
            <footer class="flex flex-col-reverse gap-2 border-t border-slate-100 bg-slate-50/80 px-6 py-4 sm:flex-row sm:justify-end">
              <button class="h-11 rounded-xl border border-slate-200 bg-white px-5 text-sm font-black text-slate-600 transition hover:border-slate-300 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50" type="button" :disabled="rechargeLoading" @click="checkoutModalOpen = false">返回修改</button>
              <button class="h-11 rounded-xl bg-sky-500 px-5 text-sm font-black text-white shadow-[0_12px_28px_rgba(14,165,233,0.22)] transition hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-60" type="button" :disabled="rechargeLoading || pendingOrderChecking" @click="confirmRecharge">{{ pendingOrderChecking ? '检查未支付订单…' : rechargeLoading ? '正在创建订单…' : '确认支付' }}</button>
            </footer>
          </section>
        </div>
      </Transition>

      <Transition name="zoom-fade">
        <div v-if="pendingOrderDialogOpen" class="fixed inset-0 z-[120] grid place-items-center bg-slate-950/50 px-4 backdrop-blur-sm" @click.self="!pendingOrderActionLoading && (pendingOrderDialogOpen = false)">
          <section class="w-full max-w-md overflow-hidden rounded-[26px] border border-white/80 bg-white shadow-[0_32px_100px_rgba(15,23,42,0.28)]" role="dialog" aria-modal="true" aria-labelledby="pending-order-title">
            <header class="border-b border-amber-100 bg-amber-50/80 px-5 py-5 sm:px-6">
              <p class="text-[11px] font-black uppercase tracking-[0.18em] text-amber-600">已有未支付订单</p>
              <h2 id="pending-order-title" class="mt-1.5 text-2xl font-black tracking-tight text-slate-950">上一个订单还没支付</h2>
              <p class="mt-1 text-sm font-semibold leading-6 text-slate-500">继续创建前需要先处理上一个订单，避免优惠码被重复占用。</p>
            </header>
            <div class="space-y-3 px-5 py-5 text-sm font-semibold text-slate-600 sm:px-6">
              <div class="flex justify-between gap-4"><span>上一个订单</span><span class="font-black text-slate-950">#{{ pendingOrder?.id || '-' }}</span></div>
              <div class="flex justify-between gap-4"><span>待支付金额</span><span class="font-black text-slate-950">￥{{ formatMoney(pendingOrder?.amount) }}</span></div>
              <div v-if="pendingOrder?.couponCode" class="flex justify-between gap-4"><span>优惠码</span><span class="font-mono font-black text-sky-700">{{ pendingOrder.couponCode }}</span></div>
            </div>
            <footer class="flex flex-col-reverse gap-2 border-t border-slate-100 bg-slate-50/80 px-5 py-4 sm:flex-row sm:justify-end sm:px-6">
              <button class="h-11 rounded-xl border border-slate-200 bg-white px-5 text-sm font-black text-slate-600 transition hover:border-slate-300 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50" type="button" :disabled="pendingOrderActionLoading" @click="pendingOrderDialogOpen = false">保留上一个订单</button>
              <button class="h-11 rounded-xl bg-amber-500 px-5 text-sm font-black text-white shadow-[0_12px_28px_rgba(245,158,11,0.22)] transition hover:bg-amber-600 disabled:cursor-not-allowed disabled:opacity-60" type="button" :disabled="pendingOrderActionLoading" @click="cancelPendingOrderAndContinue">{{ pendingOrderActionLoading ? '正在取消…' : '取消上一个并继续' }}</button>
            </footer>
          </section>
        </div>
      </Transition>

      <Transition name="zoom-fade">
        <div v-if="showHistoryModal" class="fixed inset-0 z-[90] bg-slate-950/50 p-0 backdrop-blur-sm sm:p-4" @click.self="showHistoryModal = false">
          <section class="flex h-full w-full flex-col overflow-hidden bg-white shadow-[0_32px_100px_rgba(15,23,42,0.28)] sm:max-h-[calc(100dvh-32px)] sm:rounded-[26px]" role="dialog" aria-modal="true" aria-labelledby="payment-history-title">
            <header class="flex shrink-0 items-center justify-between gap-4 border-b border-slate-100 bg-white/95 px-5 py-4 backdrop-blur-xl sm:px-7 sm:py-5">
              <div class="min-w-0">
                <p class="text-[11px] font-black uppercase tracking-[0.18em] text-sky-600">账户明细</p>
                <h2 id="payment-history-title" class="mt-1 text-xl font-black tracking-tight text-slate-950 sm:text-2xl">充值记录</h2>
                <p class="mt-1 text-xs font-semibold text-slate-500">第三方支付完成后，余额会通过支付回调入账。</p>
              </div>
              <button class="grid h-10 w-10 shrink-0 place-items-center rounded-xl text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-400" type="button" aria-label="关闭充值记录" @click="showHistoryModal = false">
                <svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18" /></svg>
              </button>
            </header>
            <div class="min-h-0 flex-1 overflow-y-auto">
              <RequestLoader v-if="historyLoading" class="p-12" label="正在加载充值记录" :cell-size="15" />
              <div v-else class="divide-y divide-slate-100">
                <div v-for="record in records" :key="record.id" class="grid gap-2 px-5 py-4 text-sm sm:px-7 md:grid-cols-[140px_160px_minmax(180px,1fr)_120px_190px] md:items-center">
                  <span class="font-black text-slate-950">￥{{ Number(record.amount).toFixed(6) }}</span>
                  <span class="text-slate-600">{{ paymentTypeText(record.type) }}</span>
                  <span class="min-w-0 truncate text-slate-500" :title="record.remark || ''">{{ record.remark || '-' }}</span>
                  <span class="w-fit rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600">{{ paymentStatusText(record.status) }}</span>
                  <span class="text-slate-500">{{ record.createdAt }}</span>
                </div>
                <div v-if="records.length === 0" class="p-12 text-center text-sm font-semibold text-slate-400">暂无充值记录</div>
              </div>
            </div>
            <footer v-if="!historyLoading" class="shrink-0 border-t border-slate-100 bg-white/95 px-5 py-4 sm:px-7"><Pagination :current="current" :pages="pages" @change="loadHistory" /></footer>
          </section>
        </div>
      </Transition>
    </Teleport>

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
