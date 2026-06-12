<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import BalanceCard from '@/components/BalanceCard.vue'
import Pagination from '@/components/Pagination.vue'
import { paymentApi } from '@/api/paymentApi'
import { useAuthStore } from '@/store/authStore'
import type { PaymentRecord } from '@/types'

const auth = useAuthStore()
const amount = ref(10)
const type = ref('balance')
const message = ref('')
const records = ref<PaymentRecord[]>([])
const current = ref(1)
const pages = ref(1)

async function recharge() {
  const { data } = await paymentApi.recharge(amount.value, type.value)
  message.value = String(data.data.message || '操作完成')
  await auth.refreshUser()
  await loadHistory()
}

async function loadHistory(page = 1) {
  const { data } = await paymentApi.history(page, 10)
  records.value = data.data.records
  current.value = data.data.current
  pages.value = data.data.pages
}
onMounted(loadHistory)
</script>

<template>
  <AppLayout>
    <div class="grid gap-6 lg:grid-cols-[380px_1fr]">
      <div class="space-y-4">
        <div>
          <p class="text-sm font-bold uppercase tracking-[0.22em] text-sky-600">余额中心</p>
          <h1 class="mt-2 text-3xl font-black tracking-tight sm:text-4xl">充值与支付</h1>
          <p class="mt-2 text-sm text-slate-500">管理创作余额和模拟支付记录。</p>
        </div>
        <BalanceCard :balance="auth.userInfo?.balance || 0" />
        <section class="soft-card space-y-4 p-5">
          <div>
            <label class="text-sm font-semibold text-slate-600">充值金额</label>
            <input v-model.number="amount" class="input mt-2 rounded-2xl" type="number" min="1" step="0.01" />
          </div>
          <div>
            <label class="text-sm font-semibold text-slate-600">支付方式</label>
            <select v-model="type" class="input mt-2 rounded-2xl">
              <option value="balance">余额模拟充值</option>
              <option value="wechat">微信支付</option>
              <option value="alipay">支付宝支付</option>
            </select>
          </div>
          <button class="w-full rounded-full bg-sky-500 px-5 py-3 text-sm font-black text-white shadow-[0_18px_50px_rgba(14,165,233,0.24)] transition hover:-translate-y-0.5 hover:bg-sky-600" @click="recharge">提交充值</button>
          <p v-if="message" class="rounded-2xl bg-teal-50 px-3 py-2 text-sm font-semibold text-mint">{{ message }}</p>
        </section>
      </div>
      <section class="soft-card overflow-hidden">
        <div class="border-b border-slate-100 bg-white/70 p-5">
          <h2 class="text-2xl font-black">支付记录</h2>
          <p class="mt-1 text-sm text-slate-500">模拟充值会即时增加余额，第三方支付为预留占位。</p>
        </div>
        <div class="divide-y divide-slate-100">
          <div v-for="record in records" :key="record.id" class="interactive-row grid gap-2 p-4 text-sm md:grid-cols-[120px_1fr_120px_180px] md:p-5">
            <span class="font-black text-slate-950">￥{{ Number(record.amount).toFixed(2) }}</span>
            <span class="text-slate-600">{{ record.type }}</span>
            <span class="w-fit rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600">{{ record.status }}</span>
            <span class="text-slate-500">{{ record.createdAt }}</span>
          </div>
          <div v-if="records.length === 0" class="p-8 text-sm text-slate-500">暂无支付记录</div>
        </div>
        <div class="border-t border-slate-100 bg-white/70 p-4"><Pagination :current="current" :pages="pages" @change="loadHistory" /></div>
      </section>
    </div>
  </AppLayout>
</template>
