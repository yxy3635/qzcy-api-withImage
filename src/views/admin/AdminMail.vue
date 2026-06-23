<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import RequestLoader from '@/components/RequestLoader.vue'
import { adminApi } from '@/api/adminApi'
import { useToast } from '@/composables/useToast'

const toast = useToast()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const passwordConfigured = ref(false)

const form = reactive({
  host: '',
  port: 587,
  username: '',
  password: '',
  fromAddress: '',
  sslEnabled: false,
  starttlsEnabled: true,
  enabled: false,
  devReturnCode: true
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.mailConfig()
    Object.assign(form, {
      host: data.data.host || '',
      port: data.data.port || 587,
      username: data.data.username || '',
      password: '',
      fromAddress: data.data.fromAddress || '',
      sslEnabled: data.data.sslEnabled,
      starttlsEnabled: data.data.starttlsEnabled,
      enabled: data.data.enabled,
      devReturnCode: data.data.devReturnCode
    })
    passwordConfigured.value = data.data.passwordConfigured
  } catch (err) {
    error.value = err instanceof Error ? err.message : '邮箱配置加载失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    const { data } = await adminApi.updateMailConfig({
      host: form.host,
      port: Number(form.port),
      username: form.username,
      password: form.password || undefined,
      fromAddress: form.fromAddress,
      sslEnabled: form.sslEnabled,
      starttlsEnabled: form.starttlsEnabled,
      enabled: form.enabled,
      devReturnCode: form.devReturnCode
    })
    form.password = ''
    passwordConfigured.value = data.data.passwordConfigured
    toast.success('邮箱配置已保存')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
    toast.error(error.value)
  } finally {
    saving.value = false
  }
}

function usePreset(type: 'qq' | 'netease') {
  if (type === 'qq') {
    form.host = 'smtp.qq.com'
    form.port = 587
    form.sslEnabled = false
    form.starttlsEnabled = true
  } else {
    form.host = 'smtp.163.com'
    form.port = 465
    form.sslEnabled = true
    form.starttlsEnabled = false
  }
}

onMounted(load)
</script>

<template>
  <AppLayout admin>
    <div class="flex flex-wrap items-end justify-between gap-5">
      <div>
        <p class="text-sm font-black tracking-[0.22em] text-sky-600">邮件服务</p>
        <h1 class="mt-2 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">发信邮箱配置</h1>
        <p class="mt-3 text-sm font-medium text-slate-500">用于注册验证、忘记密码等邮件验证码发送。</p>
      </div>
      <button class="h-12 w-full rounded-2xl border border-slate-200 bg-white px-5 text-sm font-black text-slate-700 shadow-sm transition hover:border-sky-200 hover:bg-sky-50 sm:w-auto" @click="load">
        刷新配置
      </button>
    </div>

    <p v-if="error" class="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600">{{ error }}</p>

    <div v-if="loading" class="mt-8 rounded-3xl border border-slate-100 bg-white p-10 shadow-sm">
      <RequestLoader label="正在读取邮箱配置" :cell-size="18" />
    </div>

    <section v-else class="mt-6 grid gap-5 sm:mt-8 lg:grid-cols-[1.25fr_0.75fr]">
      <div class="rounded-[24px] border border-white/80 bg-white/90 p-4 shadow-[0_24px_80px_rgba(15,23,42,0.08)] backdrop-blur-2xl sm:rounded-[28px] sm:p-6">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 class="text-2xl font-black text-slate-950">SMTP 参数</h2>
            <p class="mt-1 text-sm font-semibold text-slate-500">授权码留空时不会覆盖已有密码。</p>
          </div>
          <div class="flex gap-2">
            <button class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2 text-xs font-black text-slate-600 transition hover:border-sky-200 hover:bg-sky-50" @click="usePreset('qq')">QQ邮箱</button>
            <button class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2 text-xs font-black text-slate-600 transition hover:border-sky-200 hover:bg-sky-50" @click="usePreset('netease')">163邮箱</button>
          </div>
        </div>

        <div class="mt-6 grid gap-4 md:grid-cols-2">
          <label class="block">
            <span class="text-xs font-black text-slate-500">SMTP服务器</span>
            <input v-model="form.host" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" placeholder="smtp.qq.com" />
          </label>
          <label class="block">
            <span class="text-xs font-black text-slate-500">端口</span>
            <input v-model.number="form.port" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" type="number" />
          </label>
          <label class="block">
            <span class="text-xs font-black text-slate-500">邮箱账号</span>
            <input v-model="form.username" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" placeholder="name@example.com" />
          </label>
          <label class="block">
            <span class="text-xs font-black text-slate-500">发件人地址</span>
            <input v-model="form.fromAddress" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" placeholder="默认使用邮箱账号" />
          </label>
          <label class="block md:col-span-2">
            <span class="text-xs font-black text-slate-500">邮箱授权码</span>
            <input v-model="form.password" class="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50/80 px-4 text-sm font-semibold outline-none transition focus:border-sky-300 focus:bg-white focus:ring-4 focus:ring-sky-100" type="password" :placeholder="passwordConfigured ? '已配置，留空不修改' : '请输入SMTP授权码'" />
          </label>
        </div>

        <button class="mt-6 h-12 w-full rounded-2xl bg-slate-950 text-sm font-black text-white shadow-[0_18px_45px_rgba(15,23,42,0.16)] transition hover:-translate-y-0.5 hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-60" :disabled="saving" @click="save">
          {{ saving ? '保存中' : '保存邮箱配置' }}
        </button>
      </div>

      <aside class="rounded-[24px] border border-white/80 bg-white/90 p-4 shadow-[0_24px_80px_rgba(15,23,42,0.08)] backdrop-blur-2xl sm:rounded-[28px] sm:p-6">
        <h2 class="text-2xl font-black text-slate-950">发送策略</h2>
        <div class="mt-6 space-y-4">
          <label class="flex items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <span>
              <span class="block text-sm font-black text-slate-800">启用邮件发送</span>
              <span class="mt-1 block text-xs font-semibold text-slate-500">关闭时验证码仅用于开发返回。</span>
            </span>
            <input v-model="form.enabled" class="h-5 w-5 accent-sky-500" type="checkbox" />
          </label>
          <label class="flex items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <span>
              <span class="block text-sm font-black text-slate-800">开发模式返回验证码</span>
              <span class="mt-1 block text-xs font-semibold text-slate-500">上线后建议关闭。</span>
            </span>
            <input v-model="form.devReturnCode" class="h-5 w-5 accent-sky-500" type="checkbox" />
          </label>
          <label class="flex items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <span>
              <span class="block text-sm font-black text-slate-800">SSL 加密</span>
              <span class="mt-1 block text-xs font-semibold text-slate-500">常见端口 465。</span>
            </span>
            <input v-model="form.sslEnabled" class="h-5 w-5 accent-sky-500" type="checkbox" />
          </label>
          <label class="flex items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <span>
              <span class="block text-sm font-black text-slate-800">STARTTLS</span>
              <span class="mt-1 block text-xs font-semibold text-slate-500">常见端口 587。</span>
            </span>
            <input v-model="form.starttlsEnabled" class="h-5 w-5 accent-sky-500" type="checkbox" />
          </label>
        </div>
      </aside>
    </section>
  </AppLayout>
</template>
