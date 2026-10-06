<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { paymentApi } from '@/api/paymentApi'
import type { PaymentRecord } from '@/types'
import Pagination from '@/components/Pagination.vue'
import RequestLoader from '@/components/RequestLoader.vue'

const records = ref<PaymentRecord[]>([])
const current = ref(1)
const pages = ref(0)
const total = ref(0)
const loading = ref(false)
const error = ref('')

async function load(page = current.value) {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    const { data } = await paymentApi.completedRecharges(page, 10)
    records.value = data.data.records
    current.value = data.data.current
    pages.value = data.data.pages
    total.value = data.data.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : '充值订单加载失败'
  } finally {
    loading.value = false
  }
}

function money(value: number) {
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 6 })
}
function method(type: string) {
  return ({ alipay: '支付宝', wxpay: '微信支付', wechat: '微信支付', qqpay: 'QQ 钱包', third_party: '第三方支付' } as Record<string, string>)[type] || type
}
function time(value: string) { return value ? value.replace('T', ' ').replace(/\.\d+$/, '') : '—' }
defineExpose({ refresh: () => load() })
onMounted(() => load(1))
</script>

<template>
  <section class="orders-panel flex h-full min-h-0 flex-col overflow-hidden rounded-[22px] border border-slate-200/80 bg-white" aria-labelledby="orders-title" :aria-busy="loading">
    <header class="flex shrink-0 flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-5 py-4 sm:px-6">
      <div>
        <h2 id="orders-title" class="text-xl font-black text-slate-950">我的订单</h2>
        <p class="mt-1 text-xs font-semibold text-slate-500">成功充值记录 · 到账额度与实付金额</p>
      </div>
      <button class="btn-secondary" :disabled="loading" @click="load()">{{ loading ? '加载中…' : '刷新订单' }}</button>
    </header>
    <div class="min-h-0 flex-1 overflow-auto overscroll-contain">
      <RequestLoader v-if="loading" class="p-12" label="正在加载充值订单" :cell-size="15" />
      <div v-else-if="error" class="px-6 py-16 text-center" role="alert">
        <p class="text-sm text-rose-600">{{ error }}</p>
        <button class="btn-secondary mt-4" @click="load()">重新加载</button>
      </div>
      <template v-else-if="records.length">
        <table class="hidden w-full text-left text-sm md:table">
          <thead class="sticky top-0 bg-slate-50 text-xs font-bold text-slate-500">
            <tr><th class="px-6 py-4">订单号 / 支付方式</th><th class="px-4 py-4">到账额度</th><th class="px-4 py-4">实付金额</th><th class="px-4 py-4">状态</th><th class="px-6 py-4">订单时间</th></tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="record in records" :key="record.id" class="transition-colors hover:bg-sky-50/60">
              <td class="px-6 py-5"><p class="font-bold text-slate-800">#{{ record.id }}</p><p class="mt-1 text-xs text-slate-500">{{ method(record.type) }}</p></td>
              <td class="px-4 py-5 font-black tabular-nums text-emerald-700">+{{ money(record.rechargeAmount ?? record.amount) }}</td>
              <td class="px-4 py-5 font-bold tabular-nums text-slate-800">¥ {{ money(record.amount) }}</td>
              <td class="px-4 py-5"><span class="rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-700">充值成功</span></td>
              <td class="whitespace-nowrap px-6 py-5 text-xs tabular-nums text-slate-500">{{ time(record.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
        <div class="divide-y divide-slate-100 md:hidden">
          <article v-for="record in records" :key="record.id" class="p-5">
            <div class="flex items-center justify-between gap-3"><h3 class="font-bold">#{{ record.id }} · {{ method(record.type) }}</h3><span class="text-xs font-bold text-emerald-700">充值成功</span></div>
            <dl class="mt-4 grid grid-cols-2 gap-3 text-xs text-slate-500">
              <div><dt>到账额度</dt><dd class="mt-1 text-lg font-black text-emerald-700">+{{ money(record.rechargeAmount ?? record.amount) }}</dd></div>
              <div><dt>实付金额</dt><dd class="mt-1 text-lg font-bold text-slate-800">¥ {{ money(record.amount) }}</dd></div>
              <div class="col-span-2"><dt class="inline">订单时间：</dt><dd class="inline">{{ time(record.createdAt) }}</dd></div>
            </dl>
          </article>
        </div>
      </template>
      <div v-else class="grid min-h-64 place-content-center gap-2 p-8 text-center">
        <h3 class="text-lg font-black text-slate-800">暂无成功充值订单</h3>
        <p class="text-sm text-slate-500">完成充值后，到账额度和支付记录会显示在这里。</p>
      </div>
    </div>
    <footer class="shrink-0 space-y-3 border-t border-slate-100 bg-slate-50/60 px-5 py-4">
      <div class="flex flex-wrap items-center justify-between gap-3"><span class="text-xs font-semibold text-slate-500">共 {{ total }} 笔成功充值</span><Pagination v-if="!error && !loading && pages > 1" :current="current" :pages="pages" @change="load" /></div>
      <p class="text-[11px] leading-relaxed text-slate-400">订单时间为创建时间；到账额度按订单记录展示，优惠充值可能与实付金额不同。</p>
    </footer>
  </section>
</template>
