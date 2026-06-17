<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/authStore'
import { authApi } from '@/api/authApi'
import { useToast } from '@/composables/useToast'

const auth = useAuthStore()
const router = useRouter()
const toast = useToast()
const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')
const resetOpen = ref(false)
const resetEmail = ref('')
const resetCode = ref('')
const resetPassword = ref('')
const resetConfirmPassword = ref('')
const sending = ref(false)
const resetting = ref(false)

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

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const user = await auth.login(username.value, password.value)
    router.push(user.role === 'ADMIN' ? '/admin/dashboard' : '/create')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败'
  } finally {
    loading.value = false
  }
}

async function sendResetCode() {
  error.value = ''
  if (!isEmail(resetEmail.value)) {
    error.value = '请输入有效邮箱'
    toast.warning(error.value)
    return
  }
  sending.value = true
  try {
    const { data } = await authApi.sendEmailCode(resetEmail.value, 'forgot_password')
    const devCode = data.data && typeof data.data.devCode === 'string' ? data.data.devCode : ''
    toast.success(devCode ? `验证码已发送，开发验证码：${devCode}` : '验证码已发送，请查收邮箱')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '验证码发送失败'
    toast.error(error.value)
  } finally {
    sending.value = false
  }
}

async function resetPwd() {
  error.value = ''
  if (!isEmail(resetEmail.value)) error.value = '请输入有效邮箱'
  else if (!resetCode.value.trim()) error.value = '请输入邮箱验证码'
  else if (resetPassword.value.length < 6) error.value = '新密码至少6位'
  else if (resetPassword.value !== resetConfirmPassword.value) error.value = '两次密码不一致'
  if (error.value) {
    toast.warning(error.value)
    return
  }
  resetting.value = true
  try {
    await authApi.resetPassword(resetEmail.value, resetCode.value, resetPassword.value)
    resetCode.value = ''
    resetPassword.value = ''
    resetConfirmPassword.value = ''
    toast.success('密码已重置，请使用新密码登录')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '密码重置失败'
    toast.error(error.value)
  } finally {
    resetting.value = false
  }
}

const title = 'imageCreater'
</script>

<template>
  <div class="relative flex min-h-screen items-center justify-center overflow-x-hidden bg-[#F8FAFC] px-3 py-8 font-sans selection:bg-blue-500 selection:text-white sm:px-0">
    <!-- Abstract Background Elements -->
    <div class="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none z-0">
      <div class="absolute -top-[20%] -left-[10%] w-[50%] h-[50%] rounded-full bg-blue-400/10 blur-[120px]"></div>
      <div class="absolute top-[60%] -right-[10%] w-[40%] h-[60%] rounded-full bg-indigo-500/10 blur-[120px]"></div>
      <div class="absolute inset-0 bg-[linear-gradient(to_right,#80808008_1px,transparent_1px),linear-gradient(to_bottom,#80808008_1px,transparent_1px)] bg-[size:24px_24px]"></div>
    </div>

    <!-- Main Container -->
    <div 
      class="relative z-10 w-full max-w-md px-0 transition-all duration-1000 sm:px-6"
      :class="isVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-12'"
    >
      <!-- Logo & Header -->
      <div class="mb-6 text-center sm:mb-10">
        <h1 class="text-3xl font-black tracking-tight text-slate-900 mb-2 cursor-pointer hover:opacity-80 transition-opacity" @click="router.push('/')">
			<span style="color:aqua">{{ title.slice(0 , 5) }}</span>
			<span>{{ title.slice(5) }}</span>
			</h1>
      </div>

      <!-- Glassmorphism Form Panel -->
      <form 
        class="rounded-3xl border border-white/60 bg-white/80 p-5 shadow-[0_20px_60px_-15px_rgba(0,0,0,0.05)] backdrop-blur-xl sm:p-8"
        @submit.prevent="submit"
      >
        <div class="space-y-5">
          <!-- Username Input -->
          <div class="space-y-2">
            <label class="block text-xs font-bold text-slate-600 uppercase tracking-wider">用户名</label>
            <div class="relative">
              <input 
                v-model="username" 
                class="w-full bg-slate-50/50 border border-slate-200 text-slate-900 text-sm rounded-xl px-4 py-3.5 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all placeholder-slate-400"
                placeholder="请输入用户名" 
                required 
              />
            </div>
          </div>

          <!-- Password Input -->
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <label class="block text-xs font-bold text-slate-600 uppercase tracking-wider">密码</label>
              <button class="text-xs font-bold text-blue-600 transition hover:text-blue-700" type="button" @click="resetOpen = !resetOpen">
                忘记密码
              </button>
            </div>
            <div class="relative">
              <input 
                v-model="password" 
                class="w-full bg-slate-50/50 border border-slate-200 text-slate-900 text-sm rounded-xl px-4 py-3.5 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all placeholder-slate-400"
                type="password" 
                placeholder="请输入密码" 
                required 
              />
            </div>
          </div>

          <div v-if="resetOpen" class="rounded-2xl border border-blue-100 bg-blue-50/60 p-4">
            <div class="space-y-3">
              <input
                v-model="resetEmail"
                class="w-full rounded-xl border border-blue-100 bg-white px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                type="email"
                placeholder="输入绑定邮箱"
              />
              <div class="grid gap-3 sm:grid-cols-[minmax(0,1fr)_132px]">
                <input
                  v-model="resetCode"
                  class="min-w-0 flex-1 rounded-xl border border-blue-100 bg-white px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                  placeholder="邮箱验证码"
                />
                <button
                  class="h-12 w-full rounded-xl border border-blue-200 bg-white px-3 text-sm font-bold text-blue-700 transition hover:bg-blue-100 disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto"
                  type="button"
                  :disabled="sending"
                  @click="sendResetCode"
                >
                  {{ sending ? '发送中' : '获取验证码' }}
                </button>
              </div>
              <input
                v-model="resetPassword"
                class="w-full rounded-xl border border-blue-100 bg-white px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                type="password"
                placeholder="新密码，至少6位"
              />
              <input
                v-model="resetConfirmPassword"
                class="w-full rounded-xl border border-blue-100 bg-white px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                type="password"
                placeholder="确认新密码"
              />
              <button
                class="w-full rounded-xl bg-blue-600 px-4 py-3 text-sm font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                type="button"
                :disabled="resetting"
                @click="resetPwd"
              >
                {{ resetting ? '正在重置' : '重置密码' }}
              </button>
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

        <!-- Submit Button -->
        <button 
          class="group relative w-full flex items-center justify-center gap-2 bg-slate-900 text-white text-sm font-semibold px-6 py-4 rounded-xl mt-8 overflow-hidden transition-transform hover:scale-[1.02] shadow-md hover:shadow-xl disabled:opacity-70 disabled:hover:scale-100 disabled:cursor-not-allowed"
          :disabled="loading"
        >
          <div class="absolute inset-0 bg-gradient-to-r from-blue-600 to-indigo-600 opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>
          <span class="relative z-10">{{ loading ? '正在验证身份...' : '登录账户' }}</span>
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
          还没有账号？
          <RouterLink class="text-blue-600 hover:text-blue-700 transition-colors font-bold ml-1" to="/register">
            立即创建账号
          </RouterLink>
        </div>
      </form>
    </div>
  </div>
</template>
