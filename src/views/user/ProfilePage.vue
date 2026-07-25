<script setup lang="ts">
import { ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import RequestLoader from '@/components/RequestLoader.vue'
import { userApi } from '@/api/userApi'
import { useAuthStore } from '@/store/authStore'
import { useToast } from '@/composables/useToast'

const auth = useAuthStore()
const toast = useToast()
const email = ref(auth.userInfo?.email || '')
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const error = ref('')
const actionLoading = ref(false)

async function saveProfile() {
  error.value = ''
  actionLoading.value = true
  try {
    const { data } = await userApi.updateProfile(email.value)
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
  if (newPassword.value.length < 6) {
    error.value = '新密码至少6位'
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
    toast.success('密码已修改')
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
          <button class="w-full rounded-full bg-sky-500 px-5 py-3 text-sm font-black text-white transition hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto" :disabled="actionLoading" @click="saveProfile">保存资料</button>
        </div>
      </section>

      <section class="soft-card p-4 sm:p-6">
        <h2 class="text-2xl font-black">修改密码</h2>
        <div class="mt-5 space-y-4">
          <input v-model="oldPassword" class="input rounded-2xl" type="password" placeholder="原密码" />
          <input v-model="newPassword" class="input rounded-2xl" type="password" placeholder="新密码，至少6位" />
          <input v-model="confirmPassword" class="input rounded-2xl" type="password" placeholder="确认新密码" />
          <button class="w-full rounded-full bg-slate-950 px-5 py-3 text-sm font-black text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto" :disabled="actionLoading" @click="changePassword">确认修改</button>
        </div>
      </section>
    </div>
  </AppLayout>
</template>
