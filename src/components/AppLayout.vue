<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/authStore'
import { useSidebarPreference } from '@/composables/useSidebarPreference'

const props = defineProps<{ admin?: boolean; wide?: boolean }>()
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const { sidebarCollapsed, toggleSidebar } = useSidebarPreference()
const sidebarNav = ref<HTMLElement | null>(null)
const sidebarScrollKey = computed(() => props.admin ? 'imageCreater_admin_sidebar_scroll_top' : 'imageCreater_user_sidebar_scroll_top')

const nav = computed(() =>
  props.admin
    ? [
        { to: '/admin/dashboard', label: '仪表盘', desc: '运营总览', icon: 'dashboard' },
        { to: '/admin/announcements', label: '公告发布', desc: '站内通知', icon: 'bell' },
        { to: '/admin/users', label: '用户管理', desc: '账号权限', icon: 'users' },
        { to: '/admin/user-usage', label: '用户用量', desc: '消耗与充值', icon: 'chart' },
        { to: '/admin/pricing', label: '生图定价', desc: '模型价格', icon: 'tag' },
        { to: '/admin/relay', label: '中转站设置', desc: 'API分发配置', icon: 'relay' },
        { to: '/admin/payment', label: '支付管理', desc: '充值接入', icon: 'wallet' },
        { to: '/admin/referral', label: '邀请返利', desc: '返利比例设置', icon: 'share' },
        { to: '/admin/mail', label: '邮箱配置', desc: '发信SMTP', icon: 'mail' },
        { to: '/admin/logs', label: '系统日志', desc: '运行记录', icon: 'logs' }
      ]
    : [
        { to: '/user/dashboard', label: '资产概览', desc: '创作总览', icon: 'dashboard' },
        { to: '/user/history', label: '生成历史', desc: '图像资产', icon: 'image' },
        { to: '/user/payment', label: '余额支付', desc: '充值记录', icon: 'wallet' },
        { to: '/user/referral', label: '邀请返利', desc: '邀请码与返利', icon: 'share' },
        { to: '/user/profile', label: '资料管理', desc: '邮箱密码', icon: 'profile' }
      ]
)

const navIconPaths: Record<string, string> = {
  dashboard: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z',
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4',
  users: 'M16 20v-1a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v1m13-11a3 3 0 1 0 0-6m5 17v-1a4 4 0 0 0-3-3.87M12 6a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  chart: 'M4 19V5m0 14h16M8 16v-4m4 4V8m4 8v-6m4 6V6',
  tag: 'm20.6 13.4-7.2 7.2a2 2 0 0 1-2.8 0l-7.2-7.2A2 2 0 0 1 3 12V5a2 2 0 0 1 2-2h7a2 2 0 0 1 1.4.6l7.2 7.2a2 2 0 0 1 0 2.6ZM7.5 7.5h.01',
  relay: 'M6 3v4m0 10v4m12-18v4m0 10v4M6 7h12v10H6zM10 12h4',
  wallet: 'M4 7.5A2.5 2.5 0 0 1 6.5 5H19v14H6.5A2.5 2.5 0 0 1 4 16.5v-9ZM4 8h13v5H4m11-2.5h2',
  share: 'M18 8a3 3 0 1 0-2.82-4A3 3 0 0 0 18 8ZM6 15a3 3 0 1 0 2.82 4A3 3 0 0 0 6 15Zm12 5a3 3 0 1 0-2.82-4A3 3 0 0 0 18 20ZM8.6 16.5l6.8 3M15.4 5.5l-6.8 3',
  mail: 'M4 5h16v14H4zM4 7l8 6 8-6',
  logs: 'M6 3h9l3 3v15H6zM9 11h6M9 15h6M9 7h2',
  image: 'M3 4h18v16H3zM8 9h.01M3 17l5-5 4 4 3-3 6 6',
  profile: 'M20 21a8 8 0 0 0-16 0M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z'
}

function logout() {
  auth.logout()
  router.push('/login')
}

const title = 'imageCreater'
const userInitial = computed(() => auth.userInfo?.username?.slice(0, 1).toUpperCase() || 'U')

function saveSidebarScroll() {
  if (sidebarNav.value) {
    window.localStorage.setItem(sidebarScrollKey.value, String(sidebarNav.value.scrollTop))
  }
}

function restoreSidebarScroll() {
  nextTick(() => {
    const value = Number(window.localStorage.getItem(sidebarScrollKey.value) || 0)
    if (sidebarNav.value && Number.isFinite(value)) {
      sidebarNav.value.scrollTop = value
    }
  })
}

onMounted(restoreSidebarScroll)
watch(() => route.fullPath, restoreSidebarScroll)
</script>

<template>
  <div class="page-shell">
    <div class="fixed inset-0 -z-10 bg-[radial-gradient(circle_at_12%_10%,rgba(14,165,233,0.12),transparent_28%),radial-gradient(circle_at_88%_18%,rgba(45,212,191,0.11),transparent_30%),linear-gradient(180deg,#ffffff,#f6f9fd)]" />
    <aside
      class="fixed inset-y-0 left-0 z-30 hidden flex-col border-r border-white/80 bg-white/76 py-6 shadow-[20px_0_70px_rgba(21,32,51,0.06)] backdrop-blur-2xl transition-[width,padding] duration-300 ease-[cubic-bezier(.2,.8,.2,1)] md:flex"
      :class="sidebarCollapsed ? 'w-[72px] px-2' : 'w-60 px-5'"
    >
      <RouterLink to="/" class="flex h-8 shrink-0 items-center gap-2 overflow-hidden text-xl font-black tracking-tight text-ink" :class="sidebarCollapsed ? 'justify-center' : ''" :title="sidebarCollapsed ? title : undefined">
        <img class="h-8 w-8 shrink-0 rounded-lg" src="/favicon.ico" alt="imageCreater" />
        <span class="whitespace-nowrap transition-[width,opacity] duration-200" :class="sidebarCollapsed ? 'w-0 opacity-0' : 'w-auto opacity-100'"><span class="text-emerald-700">{{ title.slice(0, 5) }}</span>{{ title.slice(5) }}</span>
      </RouterLink>

      <section v-if="!admin || !sidebarCollapsed" class="mt-7 shrink-0 overflow-hidden rounded-2xl border border-slate-200 bg-gradient-to-br from-white to-sky-50 shadow-sm transition-[padding,height] duration-300" :class="sidebarCollapsed ? 'h-14 p-0' : 'p-4'">
        <div class="flex" :class="[sidebarCollapsed ? 'h-full justify-center' : '', admin ? '' : 'items-center gap-3']">
          <span v-if="!admin" class="grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-slate-950 text-xs font-black text-white">{{ userInitial }}</span>
          <div class="min-w-0 overflow-hidden transition-[width,opacity] duration-200" :class="sidebarCollapsed ? 'w-0 opacity-0' : 'w-auto opacity-100'">
            <p class="text-xs font-bold uppercase tracking-[0.18em] text-sky-600">{{ admin ? '管理员空间' : '创作者空间' }}</p>
            <p class="mt-2 truncate text-lg font-black">{{ auth.userInfo?.username }}</p>
            <p v-if="!admin" class="mt-1 text-sm font-semibold text-slate-500">￥{{ Number(auth.userInfo?.balance || 0).toFixed(6) }} 可用余额</p>
          </div>
        </div>
      </section>

      <button
        type="button"
        class="absolute -right-3 top-7 z-10 grid h-7 w-7 place-items-center rounded-full border border-sky-100 bg-white text-slate-500 shadow-[0_6px_16px_rgba(21,32,51,0.14)] transition hover:border-sky-300 hover:bg-sky-50 hover:text-sky-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500"
        :aria-label="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
        :title="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="toggleSidebar"
      >
        <svg viewBox="0 0 24 24" class="h-4 w-4 transition-transform duration-300" :class="sidebarCollapsed ? 'rotate-180' : ''" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 18-6-6 6-6" /></svg>
      </button>

      <nav ref="sidebarNav" class="app-sidebar-nav min-h-0 flex-1 space-y-2 overflow-y-auto overscroll-contain pb-24" :class="sidebarCollapsed ? 'mt-3 flex flex-col items-center px-0' : 'mt-6 pr-1'" @scroll.passive="saveSidebarScroll" @click.capture="saveSidebarScroll">
        <RouterLink
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="group flex items-center gap-3 border border-transparent transition duration-300 hover:border-sky-100 hover:bg-sky-50/80 hover:shadow-sm"
          :class="sidebarCollapsed ? 'h-11 w-11 min-h-0 justify-center rounded-xl px-0 py-0' : 'min-h-12 rounded-2xl px-3 py-2.5'"
          :title="sidebarCollapsed ? item.label : undefined"
          :active-class="sidebarCollapsed ? '!border-sky-200 !bg-gradient-to-br !from-sky-50 !to-cyan-100 !shadow-[0_8px_20px_rgba(14,165,233,0.16)]' : '!border-sky-200 !bg-white !shadow-[0_14px_40px_rgba(14,165,233,0.13)]'"
        >
          <svg viewBox="0 0 24 24" class="h-[19px] w-[19px] shrink-0 text-sky-700" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="navIconPaths[item.icon]" /></svg>
          <div class="min-w-0 overflow-hidden transition-[width,opacity] duration-200" :class="sidebarCollapsed ? 'w-0 opacity-0' : 'w-auto opacity-100'">
            <span class="block whitespace-nowrap text-sm font-black text-slate-800">{{ item.label }}</span>
            <p class="mt-0.5 whitespace-nowrap text-xs font-semibold text-slate-500">{{ item.desc }}</p>
          </div>
        </RouterLink>
      </nav>
    </aside>

    <div class="transition-[padding] duration-300 ease-[cubic-bezier(.2,.8,.2,1)]" :class="sidebarCollapsed ? 'md:pl-[72px]' : 'md:pl-60'">
      <header class="sticky top-0 z-20 border-b border-white/80 bg-white/76 backdrop-blur-2xl">
        <div class="flex min-h-16 flex-wrap items-center justify-between gap-3 px-4 py-3 md:h-16 md:flex-nowrap md:px-8 md:py-0">
          <div class="min-w-0"><p class="text-xs font-bold uppercase tracking-[0.18em] text-sky-600">{{ admin ? '管理员后台' : '用户后台' }}</p><p class="text-sm font-semibold text-slate-600">{{ route.meta.admin ? '运营与权限管理' : '创作资产与余额管理' }}</p></div>
          <div class="flex shrink-0 items-center gap-2"><RouterLink v-if="!admin" class="btn-primary rounded-full px-5" to="/create">开始创作</RouterLink><button class="btn-secondary rounded-full" @click="logout">退出</button></div>
        </div>
        <nav class="flex gap-2 overflow-x-auto border-t border-slate-100 px-4 py-2 md:hidden">
          <RouterLink v-for="item in nav" :key="item.to" :to="item.to" class="shrink-0 rounded-2xl border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-600" active-class="!border-sky-200 !bg-sky-50 !text-sky-700">{{ item.label }}</RouterLink>
        </nav>
      </header>
      <main class="mx-auto px-3 py-5 sm:px-4 md:px-8 md:py-8" :class="props.wide ? 'w-full max-w-[1920px]' : 'max-w-7xl'"><div class="page-enter"><slot /></div></main>
    </div>
  </div>
</template>

<style scoped>
@media (prefers-reduced-motion: reduce) {
  aside, aside *, .page-shell > div { transition-duration: 0ms !important; }
}
</style>
