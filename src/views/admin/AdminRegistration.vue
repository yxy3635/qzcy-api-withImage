<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import { adminApi } from '@/api/adminApi'
import { useToast } from '@/composables/useToast'
import type { RegistrationConfig } from '@/types'

const toast = useToast()
const draft = ref<RegistrationConfig>({ registrationClosed: false, invitationOnly: false })
const loading = ref(true)
const loaded = ref(false)
const saving = ref(false)
const error = ref('')
const status = computed(() => draft.value.registrationClosed ? '关闭注册' : draft.value.invitationOnly ? '仅邀请码注册' : '开放注册')

async function loadConfig() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.registrationConfig()
    draft.value = data.data
    loaded.value = true
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载注册设置失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!loaded.value || saving.value) return
  saving.value = true
  error.value = ''
  try {
    const { data } = await adminApi.updateRegistrationConfig({ ...draft.value })
    draft.value = data.data
    toast.success('注册设置已保存')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存注册设置失败'
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<template>
  <AppLayout admin>
    <div class="mx-auto w-full max-w-3xl space-y-6">
      <header>
        <p class="text-xs font-black tracking-[0.18em] text-sky-600">账号管理</p>
        <h1 class="mt-2 text-3xl font-black tracking-tight text-slate-950">注册设置</h1>
        <p class="mt-3 text-sm font-medium text-slate-500">控制新用户注册方式，保存后立即生效。</p>
      </header>

      <div v-if="error" role="alert" class="rounded-2xl border border-red-100 bg-red-50 p-4 text-sm text-red-600">
        {{ error }}
        <button v-if="!loaded" type="button" class="ml-3 font-bold underline" :disabled="loading" @click="loadConfig">重新加载</button>
      </div>
      <p v-if="loading" role="status" class="text-sm text-slate-500">正在加载注册设置…</p>
      <form v-else-if="loaded" class="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8" @submit.prevent="save">
        <fieldset :disabled="saving" class="space-y-5">
          <legend class="mb-5 text-lg font-black text-slate-900">注册方式</legend>
          <label class="flex cursor-pointer items-start gap-4 rounded-2xl border border-slate-200 p-5">
            <input v-model="draft.invitationOnly" type="checkbox" class="mt-1 h-5 w-5 shrink-0 accent-sky-600" />
            <span>
              <span class="block font-bold text-slate-900">仅邀请码邀请注册</span>
              <span class="mt-2 block text-sm leading-6 text-slate-500">开启后，新用户必须填写有效的 6 位邀请码。使用现有用户在「邀请返利」中生成的邀请码。</span>
            </span>
          </label>
          <label class="flex cursor-pointer items-start gap-4 rounded-2xl border border-slate-200 p-5">
            <input v-model="draft.registrationClosed" type="checkbox" class="mt-1 h-5 w-5 shrink-0 accent-sky-600" />
            <span>
              <span class="block font-bold text-slate-900">关闭注册</span>
              <span class="mt-2 block text-sm leading-6 text-slate-500">开启后暂停所有新用户注册及注册验证码发送，有邀请码也无法注册。已有用户仍可登录和找回密码。</span>
            </span>
          </label>
          <div role="status" class="rounded-2xl bg-sky-50 p-4 text-sm leading-6 text-sky-800">
            保存后生效：<strong>{{ status }}</strong>
            <p v-if="draft.registrationClosed && draft.invitationOnly" class="mt-1">关闭注册优先；重新开放后，仍需邀请码注册。</p>
          </div>
          <button type="submit" class="rounded-xl bg-slate-900 px-6 py-3 text-sm font-bold text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-60">{{ saving ? '保存中…' : '保存设置' }}</button>
        </fieldset>
      </form>
    </div>
  </AppLayout>
</template>
