<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useEmailCodeCooldown } from '@/composables/useEmailCodeCooldown'
import AppLayout from '@/components/AppLayout.vue'
import RequestLoader from '@/components/RequestLoader.vue'
import { userApi } from '@/api/userApi'
import { useAuthStore } from '@/store/authStore'
import { useToast } from '@/composables/useToast'

const auth = useAuthStore()
const toast = useToast()
const router = useRouter()
const email = ref(auth.userInfo?.email || '')
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const error = ref('')
const actionLoading = ref(false)
const profilePassword = ref('')
const currentEmailCode = ref('')
const newEmailCode = ref('')
const emailChanged = computed(() => email.value.trim().toLowerCase() !== (auth.userInfo?.email || '').toLowerCase())
const { secondsLeft: currentCooldown, start: startCurrentCooldown } = useEmailCodeCooldown()
const { secondsLeft: newCooldown, start: startNewCooldown } = useEmailCodeCooldown()

async function sendEmailCode(target: 'current' | 'new') {
  error.value = ''
  actionLoading.value = true
  try {
    await userApi.sendEmailChangeCode(email.value, target === 'current' ? 'change_email_current' : 'change_email_new')
    if (target === 'current') startCurrentCooldown()
    else startNewCooldown()
    toast.success('验证码已发送，请检查对应邮箱')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '发送失败'
    toast.error(error.value)
  } finally {
    actionLoading.value = false
  }
}

async function requireLogin(message: string) {
  auth.logout()
  toast.success(message)
  await router.replace('/login')
}

async function saveProfile() {
  error.value = ''
  actionLoading.value = true
  try {
    const changed = emailChanged.value
    const { data } = await userApi.updateProfile(email.value, profilePassword.value, currentEmailCode.value, newEmailCode.value)
    if (changed) {
      await requireLogin('邮箱已修改，所有登录已失效，请重新登录')
      return
    }
    auth.userInfo = data.data
    auth.persist()
    toast.success('资料已更新')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
    toast.error(error.value)
  } finally {
    actionLoading.value = false
  }
}

async function changePassword() {
  error.value = ''
  if (newPassword.value.length < 10 || new TextEncoder().encode(newPassword.value).length > 72) {
    error.value = '新密码至少10位，且不能超过72字节'
    toast.warning(error.value)
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次密码不一致'
    toast.warning(error.value)
    return
  }
  actionLoading.value = true
  try {
    await userApi.changePassword(oldPassword.value, newPassword.value)
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    await requireLogin('密码已修改，所有登录已失效，请重新登录')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '修改失败'
    toast.error(error.value)
  } finally {
    actionLoading.value = false
  }
}
</script>

<template>
  <AppLayout>
    <Transition name="zoom-fade">
      <div v-if="actionLoading" class="fixed inset-0 z-[60] grid place-items-center bg-white/55 backdrop-blur-[3px]">
        <RequestLoader label="正在提交账号信息" :cell-size="15" />
      </div>
    </Transition>
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="text-sm font-bold uppercase tracking-[0.22em] text-sky-600">资料管理</p>
        <h1 class="mt-2 text-3xl font-black tracking-tight sm:text-4xl">账号资料</h1>
        <p class="mt-2 text-sm text-slate-500">维护邮箱和登录密码，用于找回账号与安全验证。</p>
      </div>
    </div>

    <p v-if="error" class="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{{ error }}</p>

    <div class="mt-6 grid gap-6 lg:grid-cols-2">
      <section class="soft-card p-4 sm:p-6">
        <h2 class="text-2xl font-black">基础资料</h2>
        <div class="mt-5 space-y-4">
          <div>
            <label class="text-sm font-semibold text-slate-600">用户名</label>
            <input class="input mt-2 rounded-2xl bg-slate-50" :value="auth.userInfo?.username" disabled />
          </div>
          <div>
            <label class="text-sm font-semibold text-slate-600">邮箱</label>
            <input v-model="email" class="input mt-2 rounded-2xl" placeholder="请输入邮箱" />
          </div>
          <div v-if="emailChanged" class="space-y-4 rounded-2xl bg-slate-50 p-4">
            <p class="text-sm text-slate-600">修改邮箱需要验证登录密码和新旧邮箱。完成后请重新登录。</p>
            <input v-model="profilePassword" class="input rounded-2xl" type="password" autocomplete="current-password" aria-label="修改邮箱时验证登录密码" placeholder="当前登录密码" />
            <div v-if="auth.userInfo?.email" class="space-y-2">
              <p class="break-all text-sm text-slate-600">原邮箱：{{ auth.userInfo.email }}</p>
              <div class="flex gap-2">
                <input v-model="currentEmailCode" class="input min-w-0 rounded-2xl" inputmode="numeric" autocomplete="one-time-code" aria-label="原邮箱验证码" placeholder="原邮箱验证码" maxlength="6" />
                <button type="button" class="shrink-0 rounded-2xl bg-white px-3 text-sm font-bold disabled:opacity-50" :disabled="actionLoading || currentCooldown > 0" @click="sendEmailCode('current')">{{ currentCooldown > 0 ? `${currentCooldown}秒后重试` : '发送到原邮箱' }}</button>
              </div>
            </div>
            <div class="flex gap-2">
              <input v-model="newEmailCode" class="input min-w-0 rounded-2xl" inputmode="numeric" autocomplete="one-time-code" aria-label="新邮箱验证码" placeholder="新邮箱验证码" maxlength="6" />
              <button type="button" class="shrink-0 rounded-2xl bg-white px-3 text-sm font-bold disabled:opacity-50" :disabled="actionLoading || newCooldown > 0" @click="sendEmailCode('new')">{{ newCooldown > 0 ? `${newCooldown}秒后重试` : '发送到新邮箱' }}</button>
            </div>
          </div>
          <button class="w-full rounded-full bg-sky-500 px-5 py-3 text-sm font-black text-white transition hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto" :disabled="actionLoading" @click="saveProfile">保存资料</button>
        </div>
      </section>

      <section class="soft-card p-4 sm:p-6">
        <h2 class="text-2xl font-black">修改密码</h2>
        <div class="mt-5 space-y-4">
          <input v-model="oldPassword" class="input rounded-2xl" type="password" placeholder="原密码" />
          <input v-model="newPassword" class="input rounded-2xl" type="password" autocomplete="new-password" placeholder="新密码，至少10位" />
          <input v-model="confirmPassword" class="input rounded-2xl" type="password" placeholder="确认新密码" />
          <button class="w-full rounded-full bg-slate-950 px-5 py-3 text-sm font-black text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto" :disabled="actionLoading" @click="changePassword">确认修改</button>
        </div>
      </section>
    </div>
  </AppLayout>
</template>
