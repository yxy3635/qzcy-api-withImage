<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'
import BalanceCard from '@/components/BalanceCard.vue'
import { imageApi } from '@/api/imageApi'
import { noticeApi } from '@/api/noticeApi'
import { userApi } from '@/api/userApi'
import { useAuthStore } from '@/store/authStore'
import type { Announcement, ImageRecord } from '@/types'

const auth = useAuthStore()
const balance = ref(0)
const records = ref<ImageRecord[]>([])
const announcements = ref<Announcement[]>([])
const selectedAnnouncement = ref<Announcement | null>(null)

onMounted(async () => {
  const [balanceRes, historyRes, announcementRes] = await Promise.all([userApi.balance(), imageApi.history(1, 6), noticeApi.list()])
  balance.value = Number(balanceRes.data.data)
  records.value = historyRes.data.data.records
  announcements.value = announcementRes.data.data
})
</script>

<template>
  <AppLayout>
    <section class="relative overflow-hidden rounded-[1.5rem] border border-white/80 bg-white/82 p-4 shadow-[0_30px_100px_rgba(14,165,233,0.12)] backdrop-blur-2xl sm:rounded-[2rem] sm:p-6 md:p-8">
      <div class="absolute right-8 top-8 h-36 w-36 rounded-full bg-sky-200/40 blur-3xl" />
      <div class="absolute bottom-0 right-1/4 h-28 w-28 rounded-full bg-teal-200/40 blur-3xl" />
      <div class="relative grid gap-8 lg:grid-cols-[1fr_320px]">
        <div>
          <p class="text-sm font-bold uppercase tracking-[0.24em] text-sky-600">资产概览</p>
          <h1 class="mt-4 text-3xl font-black tracking-tight sm:text-4xl md:text-6xl">欢迎回来，{{ auth.userInfo?.username }}</h1>
          <p class="mt-4 max-w-2xl text-sm leading-7 text-slate-600">
            这里汇总你的生成资产、余额状态和最近创作。进入前台创作空间即可继续生成新图像。
          </p>
          <div class="mt-7 flex flex-wrap gap-3">
            <RouterLink class="w-full rounded-full bg-sky-500 px-5 py-3 text-center text-sm font-black text-white shadow-[0_18px_50px_rgba(14,165,233,0.28)] transition hover:-translate-y-0.5 hover:bg-sky-600 sm:w-auto" to="/create">进入前台创作</RouterLink>
            <RouterLink class="w-full rounded-full border border-slate-200 bg-white px-5 py-3 text-center text-sm font-black text-slate-800 transition hover:border-sky-200 hover:bg-sky-50 sm:w-auto" to="/user/payment">管理余额</RouterLink>
            <RouterLink class="w-full rounded-full border border-emerald-200 bg-emerald-50 px-5 py-3 text-center text-sm font-black text-emerald-700 transition hover:-translate-y-0.5 hover:border-emerald-300 hover:bg-emerald-100 sm:w-auto" to="/relay">接入 API</RouterLink>
          </div>
        </div>
        <div class="float-soft rounded-3xl border border-slate-200 bg-gradient-to-br from-white to-sky-50 p-5 shadow-[0_18px_60px_rgba(14,165,233,0.12)]">
          <p class="text-sm font-bold text-slate-500">最近一次生成</p>
          <p class="mt-3 text-lg font-black text-slate-950">{{ records[0]?.createdAt || '暂无记录' }}</p>
          <p class="mt-4 line-clamp-4 text-sm leading-6 text-slate-600">{{ records[0]?.prompt || '进入创作空间生成第一张图像。' }}</p>
        </div>
      </div>
    </section>

    <div class="mt-6 grid gap-4 md:grid-cols-3">
      <div class="soft-card p-5">
        <p class="text-sm font-semibold text-slate-500">最近生成数</p>
          <p class="mt-2 text-3xl font-black sm:text-4xl">{{ records.length }}</p>
      </div>
      <BalanceCard :balance="balance" />
      <div class="soft-card p-5">
        <p class="text-sm font-semibold text-slate-500">成功记录</p>
        <p class="mt-2 text-3xl font-black sm:text-4xl">{{ records.filter((item) => item.status === 'success').length }}</p>
      </div>
    </div>

    <section class="mt-6 rounded-[1.5rem] border border-white/80 bg-white/82 p-5 shadow-[0_24px_80px_rgba(14,165,233,0.10)] backdrop-blur-2xl">
      <div class="flex items-center justify-between gap-3">
        <div>
          <p class="text-sm font-bold uppercase tracking-[0.22em] text-sky-600">公告</p>
          <h2 class="mt-2 text-2xl font-black text-slate-950">最新通知</h2>
        </div>
        <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-500">{{ announcements.length }} 条</span>
      </div>
      <div class="mt-5 grid gap-3">
        <button
          v-for="item in announcements"
          :key="item.id"
          class="rounded-2xl border border-slate-100 bg-slate-50 p-4 text-left transition hover:border-sky-200 hover:bg-sky-50/70"
          @click="selectedAnnouncement = item"
        >
          <div class="flex items-center justify-between gap-3">
            <p class="text-base font-black text-slate-950">{{ item.title }}</p>
            <span class="shrink-0 text-xs font-semibold text-slate-400">{{ (item.publishedAt || item.createdAt || '').slice(0, 16).replace('T', ' ') }}</span>
          </div>
          <p class="mt-2 line-clamp-2 text-sm font-semibold leading-6 text-slate-600">{{ item.content }}</p>
        </button>
        <div v-if="!announcements.length" class="rounded-2xl border border-dashed border-slate-200 p-8 text-sm font-black text-slate-500">暂无公告</div>
      </div>
    </section>

    <section class="mt-9">
      <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="text-2xl font-black">最近资产</h2>
          <p class="mt-1 text-sm text-slate-500">悬停卡片可查看轻微浮动反馈</p>
        </div>
        <RouterLink class="btn-secondary w-full rounded-full sm:w-auto" to="/user/history">查看全部</RouterLink>
      </div>
      <div class="grid gap-5 md:grid-cols-3">
        <article v-for="record in records" :key="record.id" class="soft-card group overflow-hidden">
          <div class="relative h-52 overflow-hidden bg-slate-100">
            <img v-if="record.generatedImageUrl" :src="record.generatedImageUrl" class="h-full w-full object-cover transition duration-500 group-hover:scale-105" alt="生成图像" />
            <div v-else class="grid h-full place-items-center text-sm text-slate-500">暂无图像</div>
            <div class="absolute inset-0 bg-gradient-to-t from-slate-950/42 to-transparent opacity-0 transition group-hover:opacity-100" />
          </div>
          <div class="p-4">
            <p class="line-clamp-2 text-sm leading-6 text-slate-700">{{ record.prompt }}</p>
            <div class="mt-3 flex items-center justify-between text-xs text-slate-500">
              <span class="rounded-full bg-sky-50 px-2 py-1 font-bold text-sky-700">{{ record.status }}</span>
              <span>{{ record.createdAt }}</span>
            </div>
          </div>
        </article>
        <div v-if="records.length === 0" class="rounded-2xl border border-dashed border-slate-300 bg-white/70 p-8 text-sm text-slate-500 md:col-span-3">
          暂无生成记录。进入前台创作空间开始生成。
        </div>
      </div>
    </section>

    <div v-if="selectedAnnouncement" class="fixed inset-0 z-50 grid place-items-center bg-slate-950/40 px-4 backdrop-blur-sm" @click.self="selectedAnnouncement = null">
      <section class="w-full max-w-2xl rounded-[28px] bg-white p-6 shadow-[0_28px_90px_rgba(15,23,42,0.24)]">
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2 class="text-2xl font-black text-slate-950">{{ selectedAnnouncement.title }}</h2>
            <p class="mt-2 text-xs font-semibold text-slate-400">{{ (selectedAnnouncement.publishedAt || selectedAnnouncement.createdAt || '').replace('T', ' ') }}</p>
          </div>
          <button class="rounded-xl px-3 py-2 text-xl font-black text-slate-400 hover:bg-slate-50" @click="selectedAnnouncement = null">×</button>
        </div>
        <div class="mt-5 whitespace-pre-wrap text-sm font-semibold leading-7 text-slate-700">{{ selectedAnnouncement.content }}</div>
      </section>
    </div>
  </AppLayout>
</template>
