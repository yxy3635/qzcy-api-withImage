<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/authStore'
import { authApi } from '@/api/authApi'

const auth = useAuthStore()
const router = useRouter()
const username = ref('')
const email = ref('')
const code = ref('')
const password = ref('')
const confirmPassword = ref('')
const error = ref('')
const message = ref('')
const loading = ref(false)
const sending = ref(false)

const isVisible = ref(false)

onMounted(() => {
  // Trigger animation after mount
  setTimeout(() => {
    isVisible.value = true
  }, 100)
})

function isEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

async function sendCode() {
  error.value = ''
  message.value = ''
  if (!isEmail(email.value)) {
    error.value = '请输入有效邮箱'
    return
  }
  sending.value = true
  try {
    const { data } = await authApi.sendEmailCode(email.value, 'register')
    const devCode = data.data && typeof data.data.devCode === 'string' ? data.data.devCode : ''
    message.value = devCode ? `验证码已发送，开发验证码：${devCode}` : '验证码已发送，请查收邮箱'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '验证码发送失败'
  } finally {
    sending.value = false
  }
}

async function submit() {
  error.value = ''
  message.value = ''
  if (!/^[A-Za-z0-9]{3,20}$/.test(username.value)) error.value = '用户名只能包含英文/数字，长度3-20位'
  else if (!isEmail(email.value)) error.value = '请输入有效邮箱'
  else if (!code.value.trim()) error.value = '请输入邮箱验证码'
  else if (password.value.length < 6) error.value = '密码至少6位'
  else if (password.value !== confirmPassword.value) error.value = '两次密码不一致'
  if (error.value) return
  loading.value = true
  try {
    await auth.register(username.value, email.value, password.value, code.value)
    router.push('/login')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="relative flex min-h-screen items-center justify-center overflow-x-hidden bg-[#F8FAFC] px-3 py-8 font-sans selection:bg-blue-500 selection:text-white sm:px-0">
    <!-- Abstract Background Elements -->
    <div class="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none z-0">
      <div class="absolute -bottom-[20%] -left-[10%] w-[60%] h-[60%] rounded-full bg-cyan-400/10 blur-[120px]"></div>
      <div class="absolute top-[20%] -right-[10%] w-[50%] h-[50%] rounded-full bg-blue-500/10 blur-[120px]"></div>
      <div class="absolute inset-0 bg-[linear-gradient(to_right,#80808008_1px,transparent_1px),linear-gradient(to_bottom,#80808008_1px,transparent_1px)] bg-[size:24px_24px]"></div>
    </div>

    <!-- Main Container -->
    <div 
      class="relative z-10 w-full max-w-md px-0 transition-all duration-1000 sm:px-6"
      :class="isVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-12'"
    >
      <!-- Logo & Header -->
      <div class="mb-6 text-center sm:mb-10">
        <h1 class="text-3xl font-black tracking-tight text-slate-900 mb-2 cursor-pointer hover:opacity-80 transition-opacity" @click="router.push('/')">imageCreater</h1>
        <p class="text-slate-500 font-medium">注册以开始</p>
      </div>

      <!-- Glassmorphism Form Panel -->
      <form 
        class="rounded-3xl border border-white/60 bg-white/80 p-5 shadow-[0_20px_60px_-15px_rgba(0,0,0,0.05)] backdrop-blur-xl sm:p-8"
        @submit.prevent="submit"
      >
        <div class="space-y-5">
          <!-- Username Input -->
          <div class="space-y-2">
            <label class="block text-xs font-bold text-slate-600 uppercase tracking-wider">用户名 (英文/数字)</label>
            <div class="relative">
              <input 
                v-model="username" 
                class="w-full bg-slate-50/50 border border-slate-200 text-slate-900 text-sm rounded-xl px-4 py-3.5 focus:outline-none focus:ring-2 focus:ring-cyan-500/20 focus:border-cyan-500 transition-all placeholder-slate-400"
                placeholder="长度3-20位" 
                required 
              />
            </div>
          </div>

          <!-- Email Input -->
          <div class="space-y-2">
            <label class="block text-xs font-bold text-slate-600 uppercase tracking-wider">邮箱</label>
            <div class="relative">
              <input 
                v-model="email" 
                class="w-full bg-slate-50/50 border border-slate-200 text-slate-900 text-sm rounded-xl px-4 py-3.5 focus:outline-none focus:ring-2 focus:ring-cyan-500/20 focus:border-cyan-500 transition-all placeholder-slate-400"
                type="email"
                placeholder="用于验证与找回密码" 
                required 
              />
            </div>
          </div>

          <!-- Email Code Input -->
          <div class="space-y-2">
            <label class="block text-xs font-bold text-slate-600 uppercase tracking-wider">邮箱验证码</label>
            <div class="grid gap-3 sm:flex">
              <input 
                v-model="code" 
                class="min-w-0 flex-1 bg-slate-50/50 border border-slate-200 text-slate-900 text-sm rounded-xl px-4 py-3.5 focus:outline-none focus:ring-2 focus:ring-cyan-500/20 focus:border-cyan-500 transition-all placeholder-slate-400"
                placeholder="输入验证码" 
                required 
              />
              <button
                class="shrink-0 rounded-xl border border-slate-200 bg-white px-4 text-sm font-bold text-slate-700 transition hover:border-cyan-300 hover:text-cyan-700 disabled:cursor-not-allowed disabled:opacity-60"
                type="button"
                :disabled="sending"
                @click="sendCode"
              >
                {{ sending ? '发送中' : '获取验证码' }}
              </button>
            </div>
          </div>

          <!-- Password Input -->
          <div class="space-y-2">
            <label class="block text-xs font-bold text-slate-600 uppercase tracking-wider">密码</label>
            <div class="relative">
              <input 
                v-model="password" 
                class="w-full bg-slate-50/50 border border-slate-200 text-slate-900 text-sm rounded-xl px-4 py-3.5 focus:outline-none focus:ring-2 focus:ring-cyan-500/20 focus:border-cyan-500 transition-all placeholder-slate-400"
                type="password" 
                placeholder="至少6位" 
                required 
              />
            </div>
          </div>

          <!-- Confirm Password Input -->
          <div class="space-y-2">
            <label class="block text-xs font-bold text-slate-600 uppercase tracking-wider">确认密码</label>
            <div class="relative">
              <input 
                v-model="confirmPassword" 
                class="w-full bg-slate-50/50 border border-slate-200 text-slate-900 text-sm rounded-xl px-4 py-3.5 focus:outline-none focus:ring-2 focus:ring-cyan-500/20 focus:border-cyan-500 transition-all placeholder-slate-400"
                type="password" 
                placeholder="再次输入密码" 
                required 
              />
            </div>
          </div>
        </div>

        <!-- Error Message -->
        <div v-if="error" class="mt-5 p-3 rounded-lg bg-red-50 border border-red-100 flex items-center gap-2 text-sm text-red-600 font-medium animate-pulse">
          <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          {{ error }}
        </div>

        <div v-if="message" class="mt-5 rounded-lg border border-cyan-100 bg-cyan-50 p-3 text-sm font-medium text-cyan-700">
          {{ message }}
        </div>

        <!-- Submit Button -->
        <button 
          class="group relative w-full flex items-center justify-center gap-2 bg-slate-900 text-white text-sm font-semibold px-6 py-4 rounded-xl mt-8 overflow-hidden transition-transform hover:scale-[1.02] shadow-md hover:shadow-xl disabled:opacity-70 disabled:hover:scale-100 disabled:cursor-not-allowed"
          :disabled="loading"
        >
          <div class="absolute inset-0 bg-gradient-to-r from-cyan-500 to-blue-600 opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>
          <span class="relative z-10">{{ loading ? '正在配置环境...' : '完成注册' }}</span>
          <svg v-if="!loading" class="relative z-10 w-4 h-4 transition-transform group-hover:translate-x-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3" />
          </svg>
          <svg v-else class="relative z-10 w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
        </button>

        <!-- Footer Link -->
        <div class="mt-8 text-center text-sm font-medium text-slate-500">
          已有账号？
          <RouterLink class="text-blue-600 hover:text-blue-700 transition-colors font-bold ml-1" to="/login">
            返回登录
          </RouterLink>
        </div>
      </form>
    </div>
  </div>
</template>
