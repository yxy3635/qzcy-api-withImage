<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import Pagination from '@/components/Pagination.vue'
import RequestLoader from '@/components/RequestLoader.vue'
import { userApi } from '@/api/userApi'
import { useToast } from '@/composables/useToast'
import type { PageResult, ReferralInvitee, ReferralOverview, ReferralRebate, ReferralWithdrawRequest } from '@/types'

const toast = useToast()
const overview = ref<ReferralOverview | null>(null)
const invitees = ref<PageResult<ReferralInvitee> | null>(null)
const rebates = ref<PageResult<ReferralRebate> | null>(null)
const withdraws = ref<PageResult<ReferralWithdrawRequest> | null>(null)
const loading = ref(false)
const enabling = ref(false)
const error = ref('')
const page = ref(1)
const rebatePage = ref(1)
const withdrawPage = ref(1)
const actingId = ref<number | null>(null)
const uploadingId = ref<number | null>(null)
const withdrawModalOpen = ref(false)
const withdrawForm = reactive({
  amount: 0,
  channel: 'wechat',
  file: null as File | null,
  uploadedUrl: ''
})

function savedQrCode(channel = withdrawForm.channel) {
  return overview.value?.withdrawQrCodes?.find((item) => item.channel === channel)?.qrCodeUrl || ''
}

function openWithdrawModal() {
  withdrawForm.amount = Number(overview.value?.referralBalance || 0)
  withdrawForm.channel = 'wechat'
  withdrawForm.file = null
  withdrawForm.uploadedUrl = savedQrCode('wechat')
  withdrawModalOpen.value = true
}

function closeWithdrawModal() {
  withdrawModalOpen.value = false
}

function changeWithdrawChannel(channel: string) {
  withdrawForm.channel = channel
  withdrawForm.file = null
  withdrawForm.uploadedUrl = savedQrCode(channel)
}

function setWithdrawFile(event: Event) {
  const input = event.target as HTMLInputElement
  withdrawForm.file = input.files?.[0] || null
  withdrawForm.uploadedUrl = ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [overviewRes, inviteeRes] = await Promise.all([
      userApi.referral(),
      userApi.referralInvitees(page.value, 10)
    ])
    overview.value = overviewRes.data.data
    invitees.value = inviteeRes.data.data
    await Promise.all([loadRebates(), loadWithdraws()])
  } catch (err) {
    error.value = err instanceof Error ? err.message : '邀请返利数据加载失败'
    toast.error(error.value)
  } finally {
    loading.value = false
  }
}

async function loadRebates() {
  const { data } = await userApi.referralRebates(rebatePage.value, 10)
  rebates.value = data.data
}

async function loadWithdraws() {
  const { data } = await userApi.referralWithdraws(withdrawPage.value, 10)
  withdraws.value = data.data
}

async function enableReferral() {
  enabling.value = true
  error.value = ''
  try {
    const { data } = await userApi.enableReferral()
    overview.value = data.data
    await load()
    toast.success('邀请返利已开启')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '开启邀请返利失败'
    toast.error(error.value)
  } finally {
    enabling.value = false
  }
}

async function copy(text?: string) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    toast.success('已复制')
  } catch {
    toast.error('复制失败，请手动复制')
  }
}

function changePage(next: number) {
  page.value = next
  load()
}

async function transferToBalance(item: ReferralRebate) {
  actingId.value = item.id
  try {
    await userApi.transferReferralRebate(item.id)
    toast.success('已转入主钱包余额')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '操作失败')
  } finally {
    actingId.value = null
  }
}

async function requestWithdraw() {
  const balance = Number(overview.value?.referralBalance || 0)
  const amount = Number(withdrawForm.amount || 0)
  if (amount <= 0 || amount > balance) {
    toast.warning('提现金额必须大于 0 且不能超过当前返利账户余额')
    return
  }
  if (!withdrawForm.file && !withdrawForm.uploadedUrl) {
    toast.warning('请上传或选择已保存的收款二维码')
    return
  }
  uploadingId.value = 0
  try {
    if (!withdrawForm.uploadedUrl && withdrawForm.file) {
      const { data } = await userApi.uploadReferralWithdrawQr(withdrawForm.file, withdrawForm.channel)
      withdrawForm.uploadedUrl = data.data.url
    }
    await userApi.withdrawReferral(amount, withdrawForm.channel, withdrawForm.uploadedUrl)
    withdrawModalOpen.value = false
    toast.success('提现申请已提交')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '提交失败')
  } finally {
    uploadingId.value = null
  }
}

function rebateStatusText(status: string) {
  if (status === 'pending_review') return '正在审核'
  if (status === 'approved') return '审核通过'
  if (status === 'rejected') return '审核拒绝'
  if (status === 'transferred') return '已转入主钱包'
  if (status === 'withdraw_requested') return '提现审核中'
  if (status === 'withdraw_paid') return '提现成功'
  if (status === 'withdraw_failed') return '提现失败'
  return status || '-'
}

function changeRebatePage(next: number) {
  rebatePage.value = next
  loadRebates()
}

function changeWithdrawPage(next: number) {
  withdrawPage.value = next
  loadWithdraws()
}

function withdrawStatusText(status: string) {
  if (status === 'pending') return '提现审核中'
  if (status === 'paid') return '提现成功'
  if (status === 'failed') return '提现失败'
  return status || '-'
}

function channelText(channel: string) {
  if (channel === 'wechat') return '微信'
  if (channel === 'alipay') return '支付宝'
  return channel || '-'
}

onMounted(load)
</script>

<template>
  <AppLayout>
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="text-sm font-bold uppercase tracking-[0.22em] text-sky-600">邀请返利</p>
        <h1 class="mt-2 text-3xl font-black tracking-tight sm:text-4xl">邀请返利</h1>
        <p class="mt-2 text-sm text-slate-500">分享你的注册链接，被邀请用户完成充值后按后台设置比例返利到余额。</p>
      </div>
      <button class="h-12 w-full rounded-2xl border border-slate-200 bg-white px-5 text-sm font-black text-slate-700 shadow-sm transition hover:border-sky-200 hover:bg-sky-50 sm:w-auto" @click="load">
        刷新
      </button>
    </div>

    <p v-if="error" class="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{{ error }}</p>

    <div class="mt-6 grid gap-5 lg:grid-cols-[360px_1fr]">
      <section v-if="overview?.enabled" class="soft-card p-5 sm:p-6">
        <p class="text-sm font-black text-slate-500">我的邀请码</p>
        <div class="mt-4 flex items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
          <span class="text-3xl font-black tracking-[0.16em] text-slate-950">{{ overview?.invitationCode || '------' }}</span>
          <button class="rounded-xl bg-slate-950 px-4 py-2 text-sm font-black text-white transition hover:bg-sky-600" @click="copy(overview?.invitationCode)">
            复制
          </button>
        </div>

        <p class="mt-5 text-sm font-black text-slate-500">专属注册链接</p>
        <div class="mt-3 rounded-2xl border border-slate-200 bg-slate-50 p-3">
          <p class="break-all text-sm font-semibold text-slate-700">{{ overview?.invitationLink || '-' }}</p>
          <button class="mt-3 w-full rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-black text-slate-700 transition hover:border-sky-200 hover:bg-sky-50" @click="copy(overview?.invitationLink)">
            复制注册链接
          </button>
        </div>
      </section>
      <section v-else class="soft-card p-5 sm:p-6">
        <p class="text-sm font-black text-slate-500">邀请返利未开启</p>
        <h2 class="mt-3 text-2xl font-black text-slate-950">开启后生成专属邀请码</h2>
        <p class="mt-3 text-sm font-semibold leading-6 text-slate-500">生成邀请码之后无法变更，邀请成功将按照一定比例获得返利。</p>
        <button
          class="mt-5 h-12 w-full rounded-2xl bg-slate-950 px-5 text-sm font-black text-white shadow-[0_18px_45px_rgba(15,23,42,0.16)] transition hover:-translate-y-0.5 hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-60"
          :disabled="enabling"
          @click="enableReferral"
        >
          {{ enabling ? '正在开启' : '开启邀请返利' }}
        </button>
      </section>

      <section class="grid gap-4 sm:grid-cols-3">
        <div class="soft-card p-5">
          <p class="text-sm font-black text-slate-500">当前返利比例</p>
          <p class="mt-3 text-3xl font-black text-slate-950">{{ Number(overview?.rebateRate || 0).toFixed(2) }}%</p>
        </div>
        <div class="soft-card p-5">
          <p class="text-sm font-black text-slate-500">邀请用户数</p>
          <p class="mt-3 text-3xl font-black text-sky-600">{{ overview?.invitedUsers || 0 }}</p>
        </div>
        <div class="soft-card p-5">
          <p class="text-sm font-black text-slate-500">累计返利</p>
          <p class="mt-3 text-3xl font-black text-emerald-600">￥{{ Number(overview?.rebateTotal || 0).toFixed(6) }}</p>
        </div>
        <div class="soft-card p-5">
          <p class="text-sm font-black text-slate-500">返利账户</p>
          <p class="mt-3 text-3xl font-black text-emerald-600">￥{{ Number(overview?.referralBalance || 0).toFixed(6) }}</p>
          <button class="mt-4 h-10 w-full rounded-xl bg-slate-950 px-4 text-xs font-black text-white transition hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-50" :disabled="Number(overview?.referralBalance || 0) <= 0" @click="openWithdrawModal">
            提现余额
          </button>
        </div>
        <div class="soft-card p-5">
          <p class="text-sm font-black text-slate-500">正在审核</p>
          <p class="mt-3 text-3xl font-black text-amber-600">￥{{ Number(overview?.pendingReviewAmount || 0).toFixed(6) }}</p>
        </div>
        <div class="soft-card p-5">
          <p class="text-sm font-black text-slate-500">提现中</p>
          <p class="mt-3 text-3xl font-black text-sky-600">￥{{ Number(overview?.withdrawingAmount || 0).toFixed(6) }}</p>
        </div>
      </section>
    </div>

    <section class="soft-card mt-6 overflow-hidden">
      <div class="border-b border-slate-100 p-5">
        <h2 class="text-xl font-black">返利明细</h2>
        <p class="mt-1 text-sm font-semibold text-slate-500">审核通过后金额会进入返利账户，可转入主钱包；提现请使用上方返利账户按钮。</p>
      </div>
      <div v-if="!rebates?.records.length" class="p-10 text-center text-sm font-bold text-slate-500">暂无返利记录</div>
      <div v-else class="overflow-x-auto">
        <table class="w-full min-w-[920px] text-left text-sm">
          <thead class="bg-slate-50 text-xs uppercase tracking-[0.16em] text-slate-500">
            <tr>
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
              <td class="px-5 py-4 font-black text-slate-900">{{ item.inviteeUsername }}</td>
              <td class="px-5 py-4 font-semibold text-slate-700">￥{{ Number(item.rechargeAmount || 0).toFixed(6) }}</td>
              <td class="px-5 py-4 font-semibold text-slate-700">{{ Number(item.rebateRate || 0).toFixed(2) }}%</td>
              <td class="px-5 py-4 font-black text-emerald-600">￥{{ Number(item.rebateAmount || 0).toFixed(6) }}</td>
              <td class="px-5 py-4">
                <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-600">{{ rebateStatusText(item.status) }}</span>
                <p v-if="item.rejectReason" class="mt-2 text-xs font-semibold text-rose-600">{{ item.rejectReason }}</p>
                <p v-if="item.withdrawFailReason" class="mt-2 text-xs font-semibold text-rose-600">{{ item.withdrawFailReason }}</p>
              </td>
              <td class="px-5 py-4">
                <div v-if="item.status === 'approved'" class="flex min-w-[180px] flex-col gap-2">
                  <button class="rounded-xl bg-slate-950 px-3 py-2 text-xs font-black text-white disabled:opacity-60" :disabled="actingId === item.id" @click="transferToBalance(item)">转入主钱包</button>
                </div>
                <span v-else class="text-xs font-semibold text-slate-400">-</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="rebates && rebates.pages > 1" class="border-t border-slate-100 p-4">
        <Pagination :current="rebates.current" :pages="rebates.pages" @change="changeRebatePage" />
      </div>
    </section>

    <section class="soft-card mt-6 overflow-hidden">
      <div class="border-b border-slate-100 p-5">
        <h2 class="text-xl font-black">提现记录</h2>
        <p class="mt-1 text-sm font-semibold text-slate-500">返利账户提现申请会在这里保留记录。</p>
      </div>
      <div v-if="!withdraws?.records.length" class="p-10 text-center text-sm font-bold text-slate-500">暂无提现记录</div>
      <div v-else class="overflow-x-auto">
        <table class="w-full min-w-[760px] text-left text-sm">
          <thead class="bg-slate-50 text-xs uppercase tracking-[0.16em] text-slate-500">
            <tr>
              <th class="px-5 py-4">提现金额</th>
              <th class="px-5 py-4">收款方式</th>
              <th class="px-5 py-4">收款二维码</th>
              <th class="px-5 py-4">状态</th>
              <th class="px-5 py-4">申请时间</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="item in withdraws.records" :key="item.id" class="hover:bg-slate-50/80">
              <td class="px-5 py-4 font-black text-emerald-600">￥{{ Number(item.amount || 0).toFixed(6) }}</td>
              <td class="px-5 py-4 font-semibold text-slate-700">{{ channelText(item.channel) }}</td>
              <td class="px-5 py-4">
                <a class="font-black text-sky-600 underline" :href="item.qrCodeUrl" target="_blank">查看二维码</a>
              </td>
              <td class="px-5 py-4">
                <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-600">{{ withdrawStatusText(item.status) }}</span>
                <p v-if="item.failReason" class="mt-2 text-xs font-semibold text-rose-600">{{ item.failReason }}</p>
              </td>
              <td class="px-5 py-4 font-semibold text-slate-500">{{ item.createdAt ? new Date(item.createdAt).toLocaleString() : '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="withdraws && withdraws.pages > 1" class="border-t border-slate-100 p-4">
        <Pagination :current="withdraws.current" :pages="withdraws.pages" @change="changeWithdrawPage" />
      </div>
    </section>

    <section class="soft-card mt-6 overflow-hidden">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 p-5">
        <div>
          <h2 class="text-xl font-black">被邀请用户</h2>
          <p class="mt-1 text-sm font-semibold text-slate-500">累计充值量只统计已完成的第三方充值订单。</p>
        </div>
        <p class="rounded-full bg-sky-50 px-4 py-2 text-sm font-black text-sky-700">
          总充值 ￥{{ Number(overview?.inviteeRechargeTotal || 0).toFixed(6) }}
        </p>
      </div>

      <RequestLoader v-if="loading" class="p-10" label="正在读取邀请数据" :cell-size="18" />
      <div v-else-if="!invitees?.records.length" class="p-10 text-center text-sm font-bold text-slate-500">暂无邀请用户</div>
      <div v-else class="overflow-x-auto">
        <table class="w-full min-w-[640px] text-left text-sm">
          <thead class="bg-slate-50 text-xs uppercase tracking-[0.16em] text-slate-500">
            <tr>
              <th class="px-5 py-4">用户名</th>
              <th class="px-5 py-4">累计充值量</th>
              <th class="px-5 py-4">注册时间</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="item in invitees.records" :key="item.userId" class="hover:bg-slate-50/80">
              <td class="px-5 py-4 font-black text-slate-900">{{ item.username }}</td>
              <td class="px-5 py-4 font-black text-emerald-600">￥{{ Number(item.totalRecharge || 0).toFixed(6) }}</td>
              <td class="px-5 py-4 font-semibold text-slate-500">{{ item.registeredAt ? new Date(item.registeredAt).toLocaleString() : '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="invitees && invitees.pages > 1" class="border-t border-slate-100 p-4">
        <Pagination :current="invitees.current" :pages="invitees.pages" @change="changePage" />
      </div>
    </section>

    <div v-if="withdrawModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4 py-6 backdrop-blur-sm">
      <div class="w-full max-w-lg rounded-[28px] border border-white/80 bg-white p-5 shadow-[0_24px_80px_rgba(15,23,42,0.22)] sm:p-6">
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2 class="text-xl font-black text-slate-950">提现返利余额</h2>
            <p class="mt-1 text-sm font-semibold text-slate-500">可提现 ￥{{ Number(overview?.referralBalance || 0).toFixed(6) }}</p>
          </div>
          <button class="rounded-xl border border-slate-200 px-3 py-2 text-xs font-black text-slate-600 hover:bg-slate-50" @click="closeWithdrawModal">关闭</button>
        </div>

        <label class="mt-5 block">
          <span class="text-xs font-black text-slate-500">提现金额</span>
          <input v-model.number="withdrawForm.amount" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-black outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" min="0.000001" :max="Number(overview?.referralBalance || 0)" step="0.000001" type="number" />
        </label>

        <div class="mt-5">
          <p class="text-xs font-black text-slate-500">收款方式</p>
          <div class="mt-2 grid grid-cols-2 gap-2 rounded-2xl bg-slate-100 p-1">
            <button class="rounded-xl px-4 py-2 text-sm font-black transition" :class="withdrawForm.channel === 'wechat' ? 'bg-white text-emerald-600 shadow-sm' : 'text-slate-500'" @click="changeWithdrawChannel('wechat')">微信</button>
            <button class="rounded-xl px-4 py-2 text-sm font-black transition" :class="withdrawForm.channel === 'alipay' ? 'bg-white text-sky-600 shadow-sm' : 'text-slate-500'" @click="changeWithdrawChannel('alipay')">支付宝</button>
          </div>
        </div>

        <div class="mt-5 rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <p class="text-sm font-black text-slate-700">收款二维码</p>
            <a v-if="withdrawForm.uploadedUrl" class="text-xs font-black text-sky-600 underline" :href="withdrawForm.uploadedUrl" target="_blank">查看已保存二维码</a>
          </div>
          <div v-if="withdrawForm.uploadedUrl" class="mt-3 overflow-hidden rounded-2xl border border-slate-200 bg-white p-3">
            <img class="mx-auto h-40 w-40 object-contain" :src="withdrawForm.uploadedUrl" alt="收款二维码" />
          </div>
          <input class="mt-3 w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold outline-none file:mr-3 file:rounded-lg file:border-0 file:bg-slate-100 file:px-3 file:py-1.5 file:text-xs file:font-black file:text-slate-700 focus:border-sky-300" accept="image/png,image/jpeg,image/webp" type="file" @change="setWithdrawFile" />
          <p v-if="withdrawForm.file" class="mt-2 truncate text-xs font-semibold text-slate-500">将重新上传：{{ withdrawForm.file.name }}</p>
        </div>

        <button class="mt-5 h-12 w-full rounded-2xl bg-slate-950 text-sm font-black text-white shadow-[0_18px_45px_rgba(15,23,42,0.16)] transition hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-60" :disabled="uploadingId === 0" @click="requestWithdraw">
          {{ uploadingId === 0 ? '提交中' : '提交提现申请' }}
        </button>
      </div>
    </div>
  </AppLayout>
</template>
