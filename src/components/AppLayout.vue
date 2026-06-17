<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/authStore'

const props = defineProps<{ admin?: boolean }>()
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const nav = computed(() =>
  props.admin
    ? [
        { to: '/admin/dashboard', label: '仪表盘', desc: '运营总览' },
        { to: '/admin/announcements', label: '公告发布', desc: '站内通知' },
        { to: '/admin/users', label: '用户管理', desc: '账号权限' },
        { to: '/admin/pricing', label: '生图定价', desc: '模型价格' },
        { to: '/admin/relay', label: '中转站设置', desc: 'API分发配置' },
        { to: '/admin/payment', label: '支付管理', desc: '充值接入' },
        { to: '/admin/referral', label: '邀请返利', desc: '返利比例设置' },
        { to: '/admin/mail', label: '邮箱配置', desc: '发信SMTP' },
        { to: '/admin/logs', label: '系统日志', desc: '运行记录' }
      ]
    : [
        { to: '/user/dashboard', label: '资产概览', desc: '创作总览' },
        { to: '/user/history', label: '生成历史', desc: '图像资产' },
        { to: '/user/payment', label: '余额支付', desc: '充值记录' },
        { to: '/user/referral', label: '邀请返利', desc: '邀请码与返利' },
        { to: '/user/profile', label: '资料管理', desc: '邮箱密码' }
      ]
)

function logout() {
  auth.logout()
  router.push('/login')
}

const title = "imageCreater"
</script>

<template>
  <div class="page-shell">
    <div class="fixed inset-0 -z-10 bg-[radial-gradient(circle_at_12%_10%,rgba(14,165,233,0.12),transparent_28%),radial-gradient(circle_at_88%_18%,rgba(45,212,191,0.11),transparent_30%),linear-gradient(180deg,#ffffff,#f6f9fd)]" />
    <aside class="fixed inset-y-0 left-0 hidden w-72 border-r border-white/80 bg-white/76 px-5 py-6 shadow-[20px_0_70px_rgba(21,32,51,0.06)] backdrop-blur-2xl md:block">
      <RouterLink to="/" class="block text-xl font-black tracking-tight text-ink">
        <span style="color:green">{{ title.slice(0,5) }}</span>
        <span>{{ title.slice(5) }}</span>
      </RouterLink>
      <div class="mt-7 overflow-hidden rounded-2xl border border-slate-200 bg-gradient-to-br from-white to-sky-50 p-4 shadow-sm">
        <p class="text-xs font-bold uppercase tracking-[0.18em] text-sky-600">{{ admin ? '管理员空间' : '创作者空间' }}</p>
        <p class="mt-3 truncate text-lg font-black">{{ auth.userInfo?.username }}</p>
        <p v-if="!admin" class="mt-1 text-sm font-semibold text-slate-500">￥{{ Number(auth.userInfo?.balance || 0).toFixed(2) }} 可用余额</p>
      </div>
      <nav class="mt-6 space-y-2">
        <RouterLink
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="group block rounded-2xl border border-transparent px-4 py-3 transition duration-300 hover:border-sky-100 hover:bg-sky-50/80 hover:shadow-sm"
          active-class="!border-sky-200 !bg-white !shadow-[0_14px_40px_rgba(14,165,233,0.13)]"
        >
          <div class="flex items-center justify-between">
            <span class="text-sm font-black text-slate-800">{{ item.label }}</span>
            <span class="h-2 w-2 rounded-full bg-sky-400 opacity-0 transition group-hover:opacity-100" />
          </div>
          <p class="mt-1 text-xs font-semibold text-slate-500">{{ item.desc }}</p>
        </RouterLink>
      </nav>
    </aside>
    <div class="md:pl-72">
      <header class="sticky top-0 z-20 border-b border-white/80 bg-white/76 backdrop-blur-2xl">
        <div class="flex min-h-16 flex-wrap items-center justify-between gap-3 px-4 py-3 md:h-16 md:flex-nowrap md:px-8 md:py-0">
          <div class="min-w-0">
            <p class="text-xs font-bold uppercase tracking-[0.18em] text-sky-600">{{ admin ? '管理员后台' : '用户后台' }}</p>
            <p class="text-sm font-semibold text-slate-600">{{ route.meta.admin ? '运营与权限管理' : '创作资产与余额管理' }}</p>
          </div>
          <div class="flex shrink-0 items-center gap-2">
            <RouterLink v-if="!admin" class="btn-primary rounded-full px-5" to="/create">开始创作</RouterLink>
            <button class="btn-secondary rounded-full" @click="logout">退出</button>
          </div>
        </div>
        <nav class="flex gap-2 overflow-x-auto border-t border-slate-100 px-4 py-2 md:hidden">
          <RouterLink
            v-for="item in nav"
            :key="item.to"
            :to="item.to"
            class="shrink-0 rounded-2xl border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-600"
            active-class="!border-sky-200 !bg-sky-50 !text-sky-700"
          >
            {{ item.label }}
          </RouterLink>
        </nav>
      </header>
      <main class="mx-auto max-w-7xl px-3 py-5 sm:px-4 md:px-8 md:py-8">
        <div class="page-enter">
          <slot />
        </div>
      </main>
    </div>
  </div>
</template>
