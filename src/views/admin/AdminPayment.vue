<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import RequestLoader from '@/components/RequestLoader.vue'
import { adminApi } from '@/api/adminApi'
import { useToast } from '@/composables/useToast'
import type { PaymentConfig } from '@/types'

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
        <button class="h-12 w-full rounded-2xl border border-slate-200 bg-white px-5 text-sm font-black text-slate-700 shadow-sm transition hover:border-sky-200 hover:bg-sky-50 sm:w-auto" @click="load">
          刷新配置
        </button>
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
  </AppLayout>
</template>
