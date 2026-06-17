<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import Pagination from '@/components/Pagination.vue'
import { adminApi } from '@/api/adminApi'
import { useToast } from '@/composables/useToast'
import type { PageResult, PaymentConfig, ReferralRebate, ReferralWithdrawRequest } from '@/types'

const toast = useToast()
const config = ref<PaymentConfig | null>(null)
const draft = reactive({
  referralRebateRate: 0
})
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const status = ref('')
const rebatePage = ref(1)
const rebates = ref<PageResult<ReferralRebate> | null>(null)
const withdrawPage = ref(1)
const withdrawStatus = ref('')
const withdraws = ref<PageResult<ReferralWithdrawRequest> | null>(null)
const actingId = ref<number | null>(null)
const reasons = reactive<Record<number, string>>({})

function reasonFor(id: number) {
  return reasons[id] || ''
}

function setReason(id: number, value: string) {
  reasons[id] = value
}

function setDraft(next: PaymentConfig) {
  draft.referralRebateRate = Number(next.referralRebateRate || 0)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.paymentConfig()
    config.value = data.data
    setDraft(data.data)
    await Promise.all([loadRebates(), loadWithdraws()])
  } catch (err) {
    error.value = err instanceof Error ? err.message : '邀请返利配置加载失败'
    toast.error(error.value)
  } finally {
    loading.value = false
  }
}

async function loadRebates() {
  const { data } = await adminApi.referralRebates(rebatePage.value, 10, status.value)
  rebates.value = data.data
}

async function loadWithdraws() {
  const { data } = await adminApi.referralWithdraws(withdrawPage.value, 10, withdrawStatus.value)
  withdraws.value = data.data
}

async function act(id: number, action: 'approve' | 'reject' | 'paid' | 'failed') {
  actingId.value = id
  try {
    if (action === 'approve') await adminApi.approveReferralRebate(id)
    if (action === 'reject') await adminApi.rejectReferralRebate(id, reasonFor(id))
    delete reasons[id]
    toast.success('操作已完成')
    await Promise.all([load(), loadRebates()])
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '操作失败')
  } finally {
    actingId.value = null
  }
}

async function actWithdraw(id: number, action: 'paid' | 'failed') {
  actingId.value = id
  try {
    if (action === 'paid') await adminApi.referralAccountWithdrawSuccess(id)
    if (action === 'failed') await adminApi.referralAccountWithdrawFailed(id, reasonFor(id))
    delete reasons[id]
    toast.success('操作已完成')
    await Promise.all([load(), loadWithdraws()])
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '操作失败')
  } finally {
    actingId.value = null
  }
}

function changePage(next: number) {
  rebatePage.value = next
  loadRebates()
}

function changeWithdrawPage(next: number) {
  withdrawPage.value = next
  loadWithdraws()
}

function changeStatus(next: string) {
  status.value = next
  rebatePage.value = 1
  loadRebates()
}

function changeWithdrawStatus(next: string) {
  withdrawStatus.value = next
  withdrawPage.value = 1
  loadWithdraws()
}

function statusText(value: string) {
  if (value === 'pending_review') return '待审核'
  if (value === 'approved') return '审核通过'
  if (value === 'rejected') return '审核拒绝'
  if (value === 'transferred') return '已转主钱包'
  if (value === 'withdraw_requested') return '申请提现'
  if (value === 'withdraw_paid') return '提现成功'
  if (value === 'withdraw_failed') return '提现失败'
  return value || '-'
}

function withdrawStatusText(value: string) {
  if (value === 'pending') return '待处理'
  if (value === 'paid') return '提现成功'
  if (value === 'failed') return '提现失败'
  return value || '-'
}

function channelText(channel: string) {
  if (channel === 'wechat') return '微信'
  if (channel === 'alipay') return '支付宝'
  return channel || '-'
}

async function save() {
  error.value = ''
  if (draft.referralRebateRate < 0 || draft.referralRebateRate > 100) {
    error.value = '返利比例必须在 0-100 之间'
    toast.warning(error.value)
    return
  }
  saving.value = true
  try {
    const { data } = await adminApi.updatePaymentConfig({
      referralRebateRate: draft.referralRebateRate
    })
    config.value = data.data
    setDraft(data.data)
    toast.success('邀请返利配置已保存')
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
          <p class="text-sm font-black tracking-[0.22em] text-sky-600">邀请返利</p>
          <h1 class="mt-2 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">邀请返利设置</h1>
          <p class="mt-3 text-sm font-medium text-slate-500">设置返利比例，审核邀请充值返利，并处理用户提现申请。</p>
        </div>
        <button class="h-12 w-full rounded-2xl border border-slate-200 bg-white px-5 text-sm font-black text-slate-700 shadow-sm transition hover:border-sky-200 hover:bg-sky-50 sm:w-auto" @click="load">
          刷新配置
        </button>
      </div>

      <p v-if="error" class="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{{ error }}</p>

      <section class="mt-6 grid gap-5 lg:grid-cols-[1fr_320px]">
        <div class="rounded-[28px] border border-white/80 bg-white/86 p-5 shadow-[0_24px_80px_rgba(15,23,42,0.08)] backdrop-blur-2xl sm:p-6">
          <div v-if="loading" class="p-10 text-center text-sm font-bold text-slate-500">正在读取配置</div>
          <div v-else class="space-y-5">
            <label class="block">
              <span class="text-xs font-black text-slate-500">返利比例</span>
              <div class="relative mt-2">
                <input v-model.number="draft.referralRebateRate" class="h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 pr-10 text-sm font-black outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" max="100" min="0" step="0.01" type="number" />
                <span class="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-sm font-black text-slate-400">%</span>
              </div>
              <p class="mt-2 text-xs font-semibold text-slate-500">例如设置 10%，被邀请人完成充值 ￥100 后，邀请人获得 ￥10 余额返利。</p>
            </label>

            <button
              class="h-12 w-full rounded-2xl bg-slate-950 text-sm font-black text-white shadow-[0_18px_45px_rgba(15,23,42,0.16)] transition hover:-translate-y-0.5 hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto sm:px-8"
              :disabled="saving"
              @click="save"
            >
              {{ saving ? '保存中' : '保存返利设置' }}
            </button>
          </div>
        </div>

        <aside class="rounded-[28px] border border-slate-200 bg-slate-50 p-5">
          <p class="text-sm font-black text-slate-500">当前规则</p>
          <div class="mt-4 space-y-3 text-sm font-semibold text-slate-600">
            <div class="flex justify-between gap-3">
              <span>返利比例</span>
              <span class="font-black text-slate-950">{{ Number(config?.referralRebateRate || 0).toFixed(2) }}%</span>
            </div>
            <div class="flex justify-between gap-3">
              <span>入账方式</span>
              <span class="font-black text-emerald-600">自动入余额</span>
            </div>
            <div class="flex justify-between gap-3">
              <span>触发时机</span>
              <span class="font-black text-slate-950">充值成功</span>
            </div>
          </div>
          <p class="mt-5 text-xs font-semibold leading-6 text-slate-500">比例为 0 时不会生成返利。返利需管理员审核，通过后进入用户返利账户。</p>
        </aside>
      </section>

      <section class="mt-6 overflow-hidden rounded-[28px] border border-white/80 bg-white/86 shadow-[0_24px_80px_rgba(15,23,42,0.08)] backdrop-blur-2xl">
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 p-5">
          <div>
            <h2 class="text-xl font-black">返利审核与提现</h2>
            <p class="mt-1 text-sm font-semibold text-slate-500">充值成功后生成待审核返利，通过后进入用户返利账户。</p>
          </div>
          <select v-model="status" class="h-11 rounded-2xl border border-slate-200 bg-white px-4 text-sm font-black outline-none" @change="changeStatus(status)">
            <option value="">全部状态</option>
            <option value="pending_review">待审核</option>
            <option value="approved">审核通过</option>
            <option value="rejected">审核拒绝</option>
          </select>
        </div>

        <div v-if="!rebates?.records.length" class="p-10 text-center text-sm font-bold text-slate-500">暂无返利记录</div>
        <div v-else class="overflow-x-auto">
          <table class="w-full min-w-[1120px] text-left text-sm">
            <thead class="bg-slate-50 text-xs uppercase tracking-[0.16em] text-slate-500">
              <tr>
                <th class="px-5 py-4">邀请人</th>
                <th class="px-5 py-4">被邀请人</th>
                <th class="px-5 py-4">充值金额</th>
                <th class="px-5 py-4">返利比例</th>
                <th class="px-5 py-4">返利金额</th>
                <th class="px-5 py-4">状态</th>
                <th class="px-5 py-4">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr v-for="item in rebates.records" :key="item.id" class="hover:bg-slate-50/80">
                <td class="px-5 py-4 font-black text-slate-900">{{ item.inviterUsername }}</td>
                <td class="px-5 py-4 font-black text-slate-900">{{ item.inviteeUsername }}</td>
                <td class="px-5 py-4 font-semibold text-slate-700">￥{{ Number(item.rechargeAmount || 0).toFixed(2) }}</td>
                <td class="px-5 py-4 font-semibold text-slate-700">{{ Number(item.rebateRate || 0).toFixed(2) }}%</td>
                <td class="px-5 py-4 font-black text-emerald-600">￥{{ Number(item.rebateAmount || 0).toFixed(2) }}</td>
                <td class="px-5 py-4">
                  <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-600">{{ statusText(item.status) }}</span>
                  <p v-if="item.rejectReason" class="mt-2 text-xs font-semibold text-rose-600">{{ item.rejectReason }}</p>
                  <p v-if="item.withdrawFailReason" class="mt-2 text-xs font-semibold text-rose-600">{{ item.withdrawFailReason }}</p>
                </td>
                <td class="px-5 py-4">
                  <div class="flex min-w-[240px] flex-col gap-2">
                    <template v-if="item.status === 'pending_review'">
                      <button class="rounded-xl bg-emerald-600 px-3 py-2 text-xs font-black text-white disabled:opacity-60" :disabled="actingId === item.id" @click="act(item.id, 'approve')">审核通过</button>
                      <input :value="reasonFor(item.id)" class="rounded-xl border border-slate-200 px-3 py-2 text-xs font-semibold outline-none" placeholder="失败/拒绝原因" @input="setReason(item.id, ($event.target as HTMLInputElement).value)" />
                      <button class="rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-black text-rose-700 disabled:opacity-60" :disabled="actingId === item.id" @click="act(item.id, 'reject')">审核拒绝</button>
                    </template>
                    <span v-else class="text-xs font-semibold text-slate-400">无需操作</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="rebates && rebates.pages > 1" class="border-t border-slate-100 p-4">
          <Pagination :current="rebates.current" :pages="rebates.pages" @change="changePage" />
        </div>
      </section>

      <section class="mt-6 overflow-hidden rounded-[28px] border border-white/80 bg-white/86 shadow-[0_24px_80px_rgba(15,23,42,0.08)] backdrop-blur-2xl">
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 p-5">
          <div>
            <h2 class="text-xl font-black">返利账户提现申请</h2>
            <p class="mt-1 text-sm font-semibold text-slate-500">用户从返利账户发起的自定义金额提现申请。</p>
          </div>
          <select v-model="withdrawStatus" class="h-11 rounded-2xl border border-slate-200 bg-white px-4 text-sm font-black outline-none" @change="changeWithdrawStatus(withdrawStatus)">
            <option value="">全部状态</option>
            <option value="pending">待处理</option>
            <option value="paid">提现成功</option>
            <option value="failed">提现失败</option>
          </select>
        </div>

        <div v-if="!withdraws?.records.length" class="p-10 text-center text-sm font-bold text-slate-500">暂无提现申请</div>
        <div v-else class="overflow-x-auto">
          <table class="w-full min-w-[980px] text-left text-sm">
            <thead class="bg-slate-50 text-xs uppercase tracking-[0.16em] text-slate-500">
              <tr>
                <th class="px-5 py-4">用户</th>
                <th class="px-5 py-4">提现金额</th>
                <th class="px-5 py-4">收款方式</th>
                <th class="px-5 py-4">收款二维码</th>
                <th class="px-5 py-4">状态</th>
                <th class="px-5 py-4">申请时间</th>
                <th class="px-5 py-4">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr v-for="item in withdraws.records" :key="item.id" class="hover:bg-slate-50/80">
                <td class="px-5 py-4 font-black text-slate-900">{{ item.username }}</td>
                <td class="px-5 py-4 font-black text-emerald-600">￥{{ Number(item.amount || 0).toFixed(2) }}</td>
                <td class="px-5 py-4 font-semibold text-slate-700">{{ channelText(item.channel) }}</td>
                <td class="px-5 py-4">
                  <a class="font-black text-sky-600 underline" :href="item.qrCodeUrl" target="_blank">查看二维码</a>
                </td>
                <td class="px-5 py-4">
                  <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-600">{{ withdrawStatusText(item.status) }}</span>
                  <p v-if="item.failReason" class="mt-2 text-xs font-semibold text-rose-600">{{ item.failReason }}</p>
                </td>
                <td class="px-5 py-4 font-semibold text-slate-500">{{ item.createdAt ? new Date(item.createdAt).toLocaleString() : '-' }}</td>
                <td class="px-5 py-4">
                  <div v-if="item.status === 'pending'" class="flex min-w-[240px] flex-col gap-2">
                    <button class="rounded-xl bg-emerald-600 px-3 py-2 text-xs font-black text-white disabled:opacity-60" :disabled="actingId === item.id" @click="actWithdraw(item.id, 'paid')">提现成功</button>
                    <input :value="reasonFor(item.id)" class="rounded-xl border border-slate-200 px-3 py-2 text-xs font-semibold outline-none" placeholder="失败原因" @input="setReason(item.id, ($event.target as HTMLInputElement).value)" />
                    <button class="rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-black text-rose-700 disabled:opacity-60" :disabled="actingId === item.id" @click="actWithdraw(item.id, 'failed')">提现失败</button>
                  </div>
                  <span v-else class="text-xs font-semibold text-slate-400">无需操作</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="withdraws && withdraws.pages > 1" class="border-t border-slate-100 p-4">
          <Pagination :current="withdraws.current" :pages="withdraws.pages" @change="changeWithdrawPage" />
        </div>
      </section>
    </div>
  </AppLayout>
</template>
