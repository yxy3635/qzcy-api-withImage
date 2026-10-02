<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/authStore'
import { authApi } from '@/api/authApi'
import { useToast } from '@/composables/useToast'
import { useEmailCodeCooldown } from '@/composables/useEmailCodeCooldown'
import RequestLoader from '@/components/RequestLoader.vue'
import AuthPasswordInput from '@/components/auth/AuthPasswordInput.vue'
import AuthFlowBackground from '@/components/auth/AuthFlowBackground.vue'
import { usePreferredReducedMotion } from '@vueuse/core'
import type { RegistrationConfig } from '@/types'

type Mode = 'login' | 'register' | 'reset'
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToast()
const isAuthPath = (path: string) => ['/login', '/register', '/forgot-password'].includes(path)
const mode = computed<Mode>(() => route.path === '/register' ? 'register' : route.path === '/forgot-password' ? 'reset' : 'login')
const formSteps = reactive({ register: 0, reset: 0 })
const formStep = computed(() => mode.value === 'login' ? 0 : formSteps[mode.value])
const flowKey = computed(() => `${mode.value}-${formStep.value}`)
const copy = computed(() => ({
  login: { eyebrow: 'WELCOME BACK', title: '欢迎回来。', description: '登录你的账户，继续上一次的灵感。', action: '进入创作空间', number: '01' },
  register: { eyebrow: 'YOUR NEXT CHAPTER', title: '从这里，开始创作。', description: '创建账户，探索图像创作与 AI 中转服务。', action: '创建我的账户', number: '02' },
  reset: { eyebrow: 'LET’S GET YOU BACK', title: '找回密码。', description: '验证绑定邮箱，为你的账户设置新密码。', action: '重置密码', number: '03' }
}[mode.value]))
const login = reactive({ account: '', password: '' })
const register = reactive({ username: '', email: '', code: '', password: '', confirm: '', invite: '' })
const reset = reactive({ email: '', code: '', password: '', confirm: '' })
const inviteOpen = ref(false)
const error = ref('')
const invalidField = ref('')
const success = ref('')
const loading = ref(false)
const sending = ref(false)
const busy = computed(() => loading.value || sending.value)
const registrationConfig = ref<RegistrationConfig | null>(null)
const configLoading = ref(false)
const configError = ref('')
const canRegister = computed(() => !configLoading.value && registrationConfig.value !== null && !registrationConfig.value.registrationClosed)
const { secondsLeft: registerCooldown, start: startRegisterCooldown } = useEmailCodeCooldown()
const { secondsLeft: resetCooldown, start: startResetCooldown } = useEmailCodeCooldown()
const cooldown = computed(() => mode.value === 'register' ? registerCooldown.value : resetCooldown.value)
const page = ref<HTMLElement | null>(null)
const viewportArea = ref<HTMLElement | null>(null)
const fitShell = ref<HTMLElement | null>(null)
const backgroundPaused = ref(false)
const reducedMotion = usePreferredReducedMotion()
const stage = ref<HTMLElement | null>(null)
const flowDirection = ref(1)
const leaving = ref(false)
const newPassword = computed(() => mode.value === 'register' ? register.password : reset.password)
const confirmation = computed(() => mode.value === 'register' ? register.confirm : reset.confirm)
const passwordScore = computed(() => {
  const value = newPassword.value
  if (!value || value.length < 6) return 0
  return 1 + Number(value.length >= 10) + Number(/[A-Za-z]/.test(value) && /[^A-Za-z]/.test(value))
})
const passwordHint = computed(() => !newPassword.value ? '至少 6 位，建议组合字母、数字或符号' : passwordScore.value === 0 ? '再输入一些字符，密码至少需要 6 位' : passwordScore.value === 1 ? '可以使用，增加长度会更安全' : passwordScore.value === 2 ? '安全性较好' : '安全性很好')
const passwordsMatch = computed(() => Boolean(confirmation.value) && confirmation.value === newPassword.value)
const isEmail = (value: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())
let resizeObserver: ResizeObserver | undefined
let fitObserver: ResizeObserver | undefined
let activeContent: HTMLElement | undefined
let exitAnimations: Animation[] = []
let removeAfterEach: (() => void) | undefined
const prefersReducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

function safeRedirectPath() {
  const value = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  return value.startsWith('/') && !value.startsWith('//') && !['/login', '/register', '/forgot-password'].includes(value.split(/[?#]/)[0]!) ? value : ''
}
function switchMode(target: Mode) {
  if (busy.value || leaving.value || target === mode.value) return
  success.value = ''
  void router.push({ path: target === 'register' ? '/register' : target === 'reset' ? '/forgot-password' : '/login', query: route.query })
}
function changeStep(target: number) {
  if (busy.value || mode.value === 'login' || target === formStep.value) return
  if (target === 1) { advanceStep(); return }
  flowDirection.value = -1
  error.value = ''; invalidField.value = ''
  formSteps[mode.value] = 0
}
function validateDetails(target: 'register' | 'reset') {
  if (target === 'register') {
    if (!/^[A-Za-z0-9]{3,20}$/.test(register.username.trim())) { fail('用户名为 3–20 位英文或数字', 'register-name'); return false }
    if (!isEmail(register.email)) { fail('请输入有效邮箱', 'register-email'); return false }
    if (!register.code.trim()) { fail('请输入邮箱验证码', 'register-code'); return false }
  } else {
    if (!isEmail(reset.email)) { fail('请输入有效邮箱', 'reset-email'); return false }
    if (!reset.code.trim()) { fail('请输入邮箱验证码', 'reset-code'); return false }
  }
  return true
}
function advanceStep() {
  if (mode.value === 'login' || busy.value || (mode.value === 'register' && !canRegister.value)) return
  error.value = ''; invalidField.value = ''
  if (!validateDetails(mode.value)) return
  flowDirection.value = 1
  formSteps[mode.value] = 1
}
function readInvitation() {
  const raw = route.query.inviteCode || route.query.invite || route.query.ref
  if (typeof raw === 'string') { register.invite = raw.toUpperCase().slice(0, 6); inviteOpen.value = Boolean(register.invite) }
}
async function loadRegistrationConfig() {
  if (configLoading.value) return
  configLoading.value = true
  configError.value = ''
  try {
    const { data } = await authApi.registrationConfig()
    registrationConfig.value = data.data
    if (data.data.invitationOnly) inviteOpen.value = true
  } catch (err) {
    registrationConfig.value = null
    configError.value = err instanceof Error ? err.message : '加载注册设置失败'
  } finally { configLoading.value = false }
}
watch(mode, (target, previous) => {
  const order: Mode[] = ['login', 'register', 'reset']
  flowDirection.value = order.indexOf(target) > order.indexOf(previous) ? 1 : -1
  error.value = ''; success.value = ''; invalidField.value = ''
  restorePage()
  if (target === 'register') { readInvitation(); void loadRegistrationConfig() }
})
onMounted(() => {
  readInvitation()
  if (mode.value === 'register') void loadRegistrationConfig()
  resizeObserver = new ResizeObserver(() => updateStageHeight())
  fitObserver = new ResizeObserver(fitToViewport)
  if (viewportArea.value) fitObserver.observe(viewportArea.value)
  if (fitShell.value) fitObserver.observe(fitShell.value)
  const content = stage.value?.querySelector<HTMLElement>('.auth-flow-content')
  if (content) observeContent(content)
  removeAfterEach = router.afterEach((to, _from, failure) => {
    if (failure || isAuthPath(to.path)) restorePage()
  })
})
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  fitObserver?.disconnect()
  removeAfterEach?.()
  restorePage()
})
function fitToViewport() {
  if (!fitShell.value || !viewportArea.value) return
  // Height breakpoints keep controls readable; scaling only handles the remainder
  // in short desktop windows and expanded validation/invitation states.
  const scale = window.innerWidth > 760
    ? Math.min(1, Math.max(1, viewportArea.value.clientHeight - 2) / Math.max(1, fitShell.value.offsetHeight))
    : 1
  fitShell.value.style.setProperty('--fit-scale', String(scale))
}
function updateStageHeight() {
  if (stage.value && activeContent?.isConnected) stage.value.style.height = `${activeContent.offsetHeight}px`
}
function observeContent(el: Element) {
  const content = el as HTMLElement
  if (content.dataset.flowKey !== flowKey.value) return
  resizeObserver?.disconnect()
  activeContent = content
  resizeObserver?.observe(content)
  updateStageHeight()
}
function prepareLeave(el: Element) {
  el.setAttribute('inert', '')
  el.setAttribute('aria-hidden', 'true')
}
function prepareEnter(el: Element) {
  el.removeAttribute('inert')
  el.removeAttribute('aria-hidden')
}
function finishTransition(el: Element) {
  const content = el as HTMLElement
  if (content.dataset.flowKey !== flowKey.value) return
  observeContent(content)
  if (!content.contains(document.activeElement)) {
    content.querySelector<HTMLInputElement>('input:not(:disabled)')?.focus({ preventScroll: true })
    if (window.innerWidth <= 760 && page.value && page.value.getBoundingClientRect().top < -24) {
      window.scrollTo({ top: 0, behavior: prefersReducedMotion() ? 'instant' : 'smooth' })
    }
  }
}
function restorePage() {
  exitAnimations.forEach(animation => animation.cancel())
  exitAnimations = []
  leaving.value = false
}
onBeforeRouteLeave(async to => {
  if (isAuthPath(to.path) || !page.value || prefersReducedMotion()) return
  restorePage()
  leaving.value = true
  const parts = page.value.querySelectorAll<HTMLElement>('[data-page-reveal]')
  exitAnimations = Array.from(parts).map((part, index) => {
    const current = window.getComputedStyle(part)
    return part.animate([
      { opacity: current.opacity, transform: current.transform },
      { opacity: 0, transform: `translateY(${index === 1 ? -14 : 14}px)` }
    ], { duration: 240, delay: index === 2 ? 0 : 45, easing: 'cubic-bezier(.4,0,1,1)', fill: 'forwards' })
  })
  await Promise.all(exitAnimations.map(animation => animation.finished.catch(() => undefined)))
})
function clearFieldError(event: Event) {
  const input = event.target as HTMLInputElement
  if (!invalidField.value || input.id === invalidField.value) { error.value = ''; invalidField.value = '' }
}
function fail(message: string, field: string) {
  if (mode.value !== 'login') {
    const targetStep = /password|confirm|invite/.test(field) ? 1 : 0
    flowDirection.value = targetStep > formStep.value ? 1 : -1
    formSteps[mode.value] = targetStep
  }
  error.value = message
  invalidField.value = field
  void nextTick(() => stage.value?.querySelector<HTMLElement>(`.auth-flow-content:not([inert]) #${field}`)?.focus({ preventScroll: true }))
}

async function sendCode() {
  if (busy.value || cooldown.value > 0 || (mode.value === 'register' && !canRegister.value)) return
  const targetMode = mode.value
  const email = (targetMode === 'register' ? register.email : reset.email).trim()
  error.value = ''
  if (!isEmail(email)) { fail('请输入有效邮箱', `${targetMode}-email`); return }
  sending.value = true
  try {
    const { data } = await authApi.sendEmailCode(email, targetMode === 'register' ? 'register' : 'forgot_password')
    if (targetMode === 'register') startRegisterCooldown()
    else startResetCooldown()
    const devCode = typeof data.data?.devCode === 'string' ? data.data.devCode : ''
    toast.success(devCode ? `验证码已发送，开发验证码：${devCode}` : '验证码已发送，请查收邮箱')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '验证码发送失败'
    if (targetMode === 'register') await loadRegistrationConfig()
  } finally { sending.value = false }
}
async function submit() {
  if (busy.value || (mode.value === 'register' && !canRegister.value)) return
  if (mode.value !== 'login' && formStep.value === 0) { advanceStep(); return }
  error.value = ''; success.value = ''; invalidField.value = ''
  const targetMode = mode.value
  if (targetMode === 'login') {
    if (!login.account.trim()) fail('请输入用户名或邮箱', 'login-account')
    else if (!login.password) fail('请输入密码', 'login-password')
  } else if (targetMode === 'register') {
    if (!/^[A-Za-z0-9]{3,20}$/.test(register.username.trim())) fail('用户名为 3–20 位英文或数字', 'register-name')
    else if (!isEmail(register.email)) fail('请输入有效邮箱', 'register-email')
    else if (!register.code.trim()) fail('请输入邮箱验证码', 'register-code')
    else if (registrationConfig.value?.invitationOnly && !register.invite.trim()) fail('当前仅限邀请注册，请填写邀请码', 'register-invite')
    else if (register.invite.trim() && !/^[A-Za-z0-9]{6}$/.test(register.invite.trim())) { inviteOpen.value = true; fail('邀请码为 6 位英文或数字', 'register-invite') }
    else if (register.password.length < 6) fail('密码至少 6 位', 'register-password')
    else if (register.password !== register.confirm) fail('两次密码不一致', 'register-confirm')
  } else {
    if (!isEmail(reset.email)) fail('请输入有效邮箱', 'reset-email')
    else if (!reset.code.trim()) fail('请输入邮箱验证码', 'reset-code')
    else if (reset.password.length < 6) fail('新密码至少 6 位', 'reset-password')
    else if (reset.password !== reset.confirm) fail('两次密码不一致', 'reset-confirm')
  }
  if (error.value) return
  loading.value = true
  try {
    if (targetMode === 'login') {
      const user = await auth.login(login.account.trim(), login.password)
      await router.push(safeRedirectPath() || (user.role === 'ADMIN' ? '/admin/dashboard' : '/create'))
    } else {
      if (targetMode === 'register') {
        await auth.register(register.username.trim(), register.email.trim(), register.password, register.code.trim(), register.invite.trim())
        login.account = register.username.trim()
        register.password = ''; register.confirm = ''; register.code = ''
        formSteps.register = 0
      } else {
        await authApi.resetPassword(reset.email.trim(), reset.code.trim(), reset.password)
        login.account = reset.email.trim()
        reset.password = ''; reset.confirm = ''; reset.code = ''
        formSteps.reset = 0
      }
      login.password = ''
      await router.push({ path: '/login', query: route.query })
      await nextTick()
      success.value = targetMode === 'register' ? '账户已创建，请登录开始创作。' : '密码已重置，请使用新密码登录。'
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '操作失败，请重试'
    if (targetMode === 'register') await loadRegistrationConfig()
  } finally { loading.value = false }
}
</script>

<template>
  <main ref="page" class="auth-page" :class="{ 'is-leaving': leaving }" :inert="leaving || undefined">
    <AuthFlowBackground :paused="backgroundPaused || reducedMotion === 'reduce'" />
    <header class="auth-header" data-page-reveal>
      <RouterLink to="/" class="auth-brand" aria-label="imageCreater 首页">imageCreater</RouterLink>
      <nav aria-label="快捷导航">
        <RouterLink to="/docs?type=relay">API 文档 <span aria-hidden="true">↗</span></RouterLink>
        <RouterLink to="/">返回首页 <span aria-hidden="true">↗</span></RouterLink>
      </nav>
    </header>

    <div ref="viewportArea" class="auth-viewport">
    <div ref="fitShell" class="auth-shell">
      <aside class="auth-story" data-page-reveal>
        <p class="story-eyebrow"><span aria-hidden="true"></span> A SPACE FOR YOUR IDEAS</p>
        <h1>你的下一次<span class="story-emphasis">灵感</span>，<br />从这里开始。</h1>
        <p class="story-description">用文字描绘想象，用 AI 连接可能。<br />留一点空间，给还未发生的精彩。</p>

        <div class="story-motion-label"><span>IMAGINATION, IN MOTION</span><span aria-hidden="true">∞</span></div>

        <div class="story-links">
          <RouterLink to="/docs?type=image"><span>图像创作 <i aria-hidden="true">↗</i></span><small>让灵感成为画面</small></RouterLink>
          <RouterLink to="/docs?type=relay"><span>AI 中转 <i aria-hidden="true">↗</i></span><small>连接你的 AI 工作流</small></RouterLink>
        </div>
      </aside>

      <section class="auth-panel" aria-label="账户访问" data-page-reveal>
        <nav class="auth-switcher" aria-label="切换账户操作">
          <span class="switcher-indicator" :style="{ transform: `translateX(${mode === 'register' ? 100 : mode === 'reset' ? 200 : 0}%)` }" aria-hidden="true"></span>
          <button v-for="item in [{ value: 'login', label: '登录' }, { value: 'register', label: '注册' }, { value: 'reset', label: '找回密码' }]" :key="item.value" type="button" :class="{ active: mode === item.value }" :aria-current="mode === item.value ? 'page' : undefined" :disabled="busy" @click="switchMode(item.value as Mode)">{{ item.label }}</button>
        </nav>

        <div ref="stage" class="auth-stage" :style="{ '--flow-direction': flowDirection }">
          <Transition name="auth-flow" @before-enter="prepareEnter" @before-leave="prepareLeave" @enter="observeContent" @after-enter="finishTransition">
            <div :key="flowKey" class="auth-flow-content" :data-mode="mode" :data-flow-key="flowKey">
              <div class="auth-intro form-reveal">
                <div class="intro-meta"><p>{{ copy.eyebrow }}</p><span>{{ copy.number }} <i>/ 03</i></span></div>
                <h2>{{ copy.title }}</h2>
                <p class="intro-description">{{ copy.description }}</p>
                <nav v-if="mode !== 'login'" class="form-steps" aria-label="表单步骤">
                  <button type="button" :class="{ current: formStep === 0 }" :aria-current="formStep === 0 ? 'step' : undefined" :disabled="busy" @click="changeStep(0)"><span>1</span>{{ mode === 'register' ? '账户信息' : '邮箱信息' }}</button>
                  <i aria-hidden="true"></i>
                  <button type="button" :class="{ current: formStep === 1 }" :aria-current="formStep === 1 ? 'step' : undefined" :disabled="busy || (mode === 'register' && !canRegister)" @click="changeStep(1)"><span>2</span>设置密码</button>
                </nav>
              </div>

              <form class="auth-form" :aria-busy="busy" @submit.prevent="submit" @input="clearFieldError">
                <Transition name="auth-expand">
                  <div v-if="mode === 'register' && (configLoading || configError || registrationConfig?.registrationClosed || registrationConfig?.invitationOnly)" class="expand-region">
                    <div class="expand-inner">
                      <div class="registration-state" aria-live="polite">
                        <p v-if="configLoading"><span class="inline-spinner" aria-hidden="true"></span>正在读取注册设置…</p>
                        <p v-else-if="configError" class="status-error">{{ configError }} <button type="button" @click="loadRegistrationConfig">重新加载</button></p>
                        <p v-else-if="registrationConfig?.registrationClosed">当前暂未开放注册，你仍可登录已有账户。</p>
                        <p v-else-if="registrationConfig?.invitationOnly">当前为邀请注册，请填写有效邀请码。</p>
                      </div>
                    </div>
                  </div>
                </Transition>

                <fieldset :disabled="busy || (mode === 'register' && !canRegister)">
                  <template v-if="mode === 'login'">
                    <div class="auth-field form-reveal" style="--field-index: 1">
                      <label for="login-account">用户名或邮箱</label>
                      <input id="login-account" v-model="login.account" autocomplete="username" placeholder="输入用户名或绑定邮箱" :aria-invalid="invalidField === 'login-account' || undefined" required />
                    </div>
                    <AuthPasswordInput id="login-password" v-model="login.password" class="form-reveal" style="--field-index: 2" label="密码" autocomplete="current-password" placeholder="输入你的密码" :invalid="invalidField === 'login-password'">
                      <template #action><button type="button" class="text-action" @click="switchMode('reset')">忘记密码？</button></template>
                    </AuthPasswordInput>
                  </template>

                  <template v-else-if="mode === 'register'">
                    <template v-if="formStep === 0">
                    <div class="auth-field form-reveal" style="--field-index: 1">
                      <label for="register-name">用户名</label>
                      <input id="register-name" v-model="register.username" autocomplete="username" placeholder="3–20 位英文或数字" minlength="3" maxlength="20" :aria-invalid="invalidField === 'register-name' || undefined" required />
                    </div>
                    <div class="auth-field form-reveal" style="--field-index: 2">
                      <label for="register-email">邮箱</label>
                      <input id="register-email" v-model="register.email" type="email" autocomplete="email" placeholder="输入常用邮箱" :aria-invalid="invalidField === 'register-email' || undefined" required />
                    </div>
                    <div class="auth-field form-reveal" style="--field-index: 3">
                      <label for="register-code">邮箱验证码</label>
                      <div class="code-input">
                        <input id="register-code" v-model="register.code" autocomplete="one-time-code" inputmode="numeric" placeholder="输入验证码" :aria-invalid="invalidField === 'register-code' || undefined" required />
                        <button type="button" :disabled="cooldown > 0" @click="sendCode"><span v-if="sending" class="inline-spinner" aria-hidden="true"></span>{{ sending ? '发送中' : cooldown > 0 ? `${cooldown}s 后重试` : '获取验证码' }}</button>
                      </div>
                    </div>
                    </template>
                    <template v-else>
                    <div class="new-password-group form-reveal" style="--field-index: 4">
                      <div class="auth-field-pair">
                        <AuthPasswordInput id="register-password" v-model="register.password" label="设置密码" placeholder="至少 6 位" :minlength="6" :invalid="invalidField === 'register-password'" describedby="register-password-hint" />
                        <AuthPasswordInput id="register-confirm" v-model="register.confirm" label="确认密码" placeholder="再次输入密码" :minlength="6" :invalid="invalidField === 'register-confirm'" describedby="register-password-hint" />
                      </div>
                      <div id="register-password-hint" class="password-hint" aria-live="polite">
                        <span class="strength-meter" :data-score="passwordScore" aria-hidden="true"><i v-for="number in 3" :key="number" :class="{ filled: passwordScore >= number }"></i></span>
                        <span v-if="confirmation" :class="passwordsMatch ? 'hint-match' : 'hint-mismatch'">{{ passwordsMatch ? '两次密码一致' : '两次密码尚不一致' }}</span>
                        <span v-else>{{ passwordHint }}</span>
                      </div>
                    </div>
                    <div class="invite-field form-reveal" style="--field-index: 5">
                      <button v-if="!registrationConfig?.invitationOnly" type="button" class="invite-toggle" :aria-expanded="inviteOpen" aria-controls="invite-region" @click="inviteOpen = !inviteOpen">
                        <span>我有邀请码 <small>选填</small></span><span class="invite-plus" :class="{ expanded: inviteOpen }" aria-hidden="true">+</span>
                      </button>
                      <Transition name="auth-expand">
                        <div v-if="inviteOpen || registrationConfig?.invitationOnly" id="invite-region" class="expand-region">
                          <div class="expand-inner"><div class="auth-field invite-input"><label for="register-invite">邀请码 <small v-if="!registrationConfig?.invitationOnly">（选填）</small></label><input id="register-invite" v-model="register.invite" maxlength="6" placeholder="6 位英文或数字" :required="registrationConfig?.invitationOnly" :aria-invalid="invalidField === 'register-invite' || undefined" @input="register.invite = register.invite.toUpperCase()" /></div></div>
                        </div>
                      </Transition>
                    </div>
                    </template>
                  </template>

                  <template v-else>
                    <template v-if="formStep === 0">
                    <div class="auth-field form-reveal" style="--field-index: 1">
                      <label for="reset-email">绑定邮箱</label>
                      <input id="reset-email" v-model="reset.email" type="email" autocomplete="email" placeholder="注册时绑定的邮箱" :aria-invalid="invalidField === 'reset-email' || undefined" required />
                    </div>
                    <div class="auth-field form-reveal" style="--field-index: 2">
                      <label for="reset-code">邮箱验证码</label>
                      <div class="code-input"><input id="reset-code" v-model="reset.code" autocomplete="one-time-code" inputmode="numeric" placeholder="输入验证码" :aria-invalid="invalidField === 'reset-code' || undefined" required /><button type="button" :disabled="cooldown > 0" @click="sendCode"><span v-if="sending" class="inline-spinner" aria-hidden="true"></span>{{ sending ? '发送中' : cooldown > 0 ? `${cooldown}s 后重试` : '获取验证码' }}</button></div>
                    </div>
                    </template>
                    <div v-else class="new-password-group form-reveal" style="--field-index: 3">
                      <div class="auth-field-pair">
                        <AuthPasswordInput id="reset-password" v-model="reset.password" label="新密码" placeholder="至少 6 位" :minlength="6" :invalid="invalidField === 'reset-password'" describedby="reset-password-hint" />
                        <AuthPasswordInput id="reset-confirm" v-model="reset.confirm" label="确认新密码" placeholder="再次输入密码" :minlength="6" :invalid="invalidField === 'reset-confirm'" describedby="reset-password-hint" />
                      </div>
                      <div id="reset-password-hint" class="password-hint" aria-live="polite">
                        <span class="strength-meter" :data-score="passwordScore" aria-hidden="true"><i v-for="number in 3" :key="number" :class="{ filled: passwordScore >= number }"></i></span>
                        <span v-if="confirmation" :class="passwordsMatch ? 'hint-match' : 'hint-mismatch'">{{ passwordsMatch ? '两次密码一致' : '两次密码尚不一致' }}</span><span v-else>{{ passwordHint }}</span>
                      </div>
                    </div>
                  </template>
                </fieldset>

                <Transition name="auth-expand">
                  <div v-if="error || success" class="expand-region"><div class="expand-inner"><p class="auth-feedback" :class="error ? 'feedback-error' : 'feedback-success'" :role="error ? 'alert' : 'status'">{{ error || success }}</p></div></div>
                </Transition>
                <div class="submit-row form-reveal" style="--field-index: 6">
                  <button v-if="mode !== 'login' && formStep === 1" type="button" class="step-back" :disabled="busy" @click="changeStep(0)">上一步</button>
                  <button class="auth-submit" type="submit" :disabled="busy || (mode === 'register' && !canRegister)">
                    <span>{{ loading ? '正在处理…' : mode !== 'login' && formStep === 0 ? '继续，设置密码' : copy.action }}</span><RequestLoader v-if="loading" label="" :cell-size="4" /><svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M4 12h16m-6-6 6 6-6 6" /></svg>
                  </button>
                </div>
                <p class="auth-form-footer form-reveal" style="--field-index: 7">
                  <template v-if="mode === 'login'">还没有账户？<button type="button" :disabled="busy" @click="switchMode('register')">立即注册 <span aria-hidden="true">↗</span></button></template>
                  <template v-else-if="mode === 'register'">已有账户？<button type="button" :disabled="busy" @click="switchMode('login')">返回登录 <span aria-hidden="true">↗</span></button></template>
                  <template v-else>想起密码了？<button type="button" :disabled="busy" @click="switchMode('login')">返回登录 <span aria-hidden="true">↗</span></button></template>
                </p>
              </form>
            </div>
          </Transition>
        </div>
      </section>
    </div>
    </div>
    <footer class="auth-page-footer" data-page-reveal>
      <span>imageCreater · 为想象留出空间</span>
      <button v-if="reducedMotion !== 'reduce'" type="button" class="motion-toggle" :aria-pressed="backgroundPaused" @click="backgroundPaused = !backgroundPaused"><span aria-hidden="true">{{ backgroundPaused ? '▷' : 'Ⅱ' }}</span>{{ backgroundPaused ? '继续背景动画' : '暂停背景动画' }}</button>
      <span v-else>IMAGINATION, IN MOTION</span>
    </footer>
  </main>
</template>

<style scoped>
.auth-page {
  --auth-ink: #172c47;
  --auth-accent: #2563eb;
  --auth-muted: #627995;
  --auth-border: #ffffffbd;
  --auth-ease: cubic-bezier(.22, 1, .36, 1);
  --auth-input-height: 48px;
  --auth-control-bg: #ffffff59;
  --auth-label-gap: 7px;
  --field-gap: 17px;
  --intro-gap: 22px;
  position: relative;
  isolation: isolate;
  height: 100vh;
  height: 100dvh;
  min-height: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 18px;
  padding: 24px clamp(24px, 5vw, 88px) 18px;
  overflow: hidden;
  color: var(--auth-ink);
  font-family: 'Inter', 'Segoe UI', 'Microsoft YaHei', sans-serif;
}
.auth-header, .auth-page-footer { width: 100%; max-width: 1180px; margin: 0 auto; display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.auth-header { min-height: 44px; animation: page-part-in .65s var(--auth-ease) both; }
.auth-brand { font-size: 24px; font-weight: 750; letter-spacing: -1.1px; transition: color .2s; }.auth-brand:hover { color: var(--auth-accent); }
.auth-header nav { display: flex; align-items: center; gap: 24px; padding: 0 20px; border: 1px solid #ffffffb3; border-radius: 99px; background: #ffffff38; box-shadow: inset 0 1px 0 #fff9, 0 8px 24px #43698b08; backdrop-filter: blur(14px) saturate(1.4); -webkit-backdrop-filter: blur(14px) saturate(1.4); }
.auth-header nav a { display: inline-flex; align-items: center; gap: 8px; min-height: 40px; color: #56708f; font-size: 12px; transition: color .2s; }.auth-header nav a span { transition: transform .25s; }.auth-header nav a:hover { color: var(--auth-accent); }.auth-header nav a:hover span { transform: translate(2px, -2px); }
.auth-viewport { display: flex; align-items: center; justify-content: center; min-height: 0; min-width: 0; }
.auth-shell { --fit-scale: 1; position: relative; flex-shrink: 0; width: 100%; max-width: 1120px; display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 456px); align-items: center; gap: 48px; padding: 22px; border: 1px solid #ffffffb8; border-radius: 32px; background: linear-gradient(125deg, #ffffff50, #ffffff20 58%, #ffffff40); box-shadow: inset 0 1px 0 #ffffffed, inset 0 -1px 0 #ffffff66, 0 25px 70px #476c9c12; backdrop-filter: blur(5px) saturate(1.2); -webkit-backdrop-filter: blur(5px) saturate(1.2); transform: scale(var(--fit-scale)); transform-origin: center; }
.auth-shell::before { content: ''; position: absolute; inset: 0; pointer-events: none; border-radius: inherit; background: linear-gradient(115deg, #ffffff50, transparent 26%, transparent 75%, #ffffff30); }
.auth-story { position: relative; min-width: 0; padding: 30px 12px 30px 24px; animation: page-part-in .85s var(--auth-ease) .08s both; }
.story-eyebrow { display: flex; align-items: center; gap: 10px; margin: 0 0 24px; font-size: 9px; font-weight: 600; letter-spacing: .17em; color: #567591; }.story-eyebrow > span { width: 6px; height: 6px; background: var(--auth-accent); border-radius: 50%; box-shadow: 0 0 0 5px #2563eb0c; }
.auth-story h1 { margin: 0; font-size: clamp(34px, 3.15vw, 47px); font-weight: 600; line-height: 1.5; letter-spacing: -.045em; white-space: nowrap; }.story-emphasis { position: relative; color: var(--auth-accent); }.story-emphasis::after { content: ''; position: absolute; height: 2px; background: #7aacf1; left: 2px; right: 0; bottom: -5px; transform: scaleX(0); transform-origin: left; animation: underline-in .8s var(--auth-ease) .55s forwards; }
.story-description { font-size: 13px; line-height: 2; color: #5d7692; margin: 24px 0 0; }
.story-motion-label { display: flex; align-items: center; gap: 20px; margin-top: 60px; color: #688ba8; font-size: 9px; letter-spacing: .14em; }.story-motion-label > span:last-child { font-size: 28px; font-weight: 300; line-height: 1; }
.story-links { display: flex; gap: 34px; margin-top: 28px; }.story-links a { display: grid; gap: 7px; padding: 5px 0; }.story-links a > span { display: flex; align-items: center; gap: 25px; font-size: 12px; font-weight: 600; transition: color .2s; }.story-links i { font-style: normal; color: #5983ad; transition: transform .25s; }.story-links small { color: #6f859e; font-size: 10px; }.story-links a:hover > span { color: var(--auth-accent); }.story-links a:hover i { transform: translate(3px, -3px); }
.auth-panel { position: relative; min-width: 0; padding: 28px; border: 1px solid #ffffffbf; border-radius: 24px; background: linear-gradient(150deg, #ffffff75, #ffffff45); box-shadow: inset 0 1px 0 #fff, inset 0 -1px 0 #fff8, 0 12px 40px #587ba511; backdrop-filter: blur(18px) saturate(1.45); -webkit-backdrop-filter: blur(18px) saturate(1.45); animation: page-part-in .85s var(--auth-ease) .18s both; }
.auth-switcher { position: relative; display: grid; grid-template-columns: repeat(3, 1fr); padding: 4px; border: 1px solid #fff9; background: #dbe8f44d; border-radius: 12px; margin-bottom: 24px; box-shadow: inset 0 1px 5px #7995b20b; }.auth-switcher button { position: relative; z-index: 1; min-height: 36px; color: #7186a0; font-size: 12px; font-weight: 500; border-radius: 8px; transition: color .25s; }.auth-switcher button.active { color: var(--auth-accent); }.auth-switcher button:hover:not(:disabled) { color: var(--auth-ink); }.switcher-indicator { position: absolute; top: 4px; bottom: 4px; left: 4px; width: calc((100% - 8px) / 3); border: 1px solid #ffffffed; border-radius: 8px; background: #ffffffb3; box-shadow: 0 3px 10px #6785ac12, inset 0 1px 0 #fff; transition: transform .5s var(--auth-ease); }
.auth-stage { position: relative; transition: height .5s var(--auth-ease); }
.auth-flow-content { display: flow-root; width: 100%; }
.auth-intro { margin-bottom: 32px; }
.intro-meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 15px; }.intro-meta p { margin: 0; color: #7b89a0; font-size: 9px; font-weight: 600; letter-spacing: .15em; }.intro-meta > span { color: #5d7fb5; font-size: 10px; font-variant-numeric: tabular-nums; letter-spacing: .08em; }.intro-meta i { font-style: normal; color: #b2becf; }
.auth-intro h2 { margin: 0 0 12px; font-size: 34px; font-weight: 600; line-height: 1.4; letter-spacing: -.04em; }
.auth-flow-content[data-mode="register"] .auth-intro h2 { font-size: 30px; }
.intro-description { margin: 0; color: var(--auth-muted); font-size: 13px; line-height: 1.8; }
.form-steps { display: flex; align-items: center; gap: 18px; margin-top: 24px; }
.form-steps button { display: inline-flex; align-items: center; gap: 8px; min-height: 32px; font-size: 11px; color: #94a0b2; transition: color .25s; }.form-steps button > span { display: grid; place-items: center; width: 22px; height: 22px; border: 1px solid #e1e7f0; border-radius: 50%; font-size: 10px; font-variant-numeric: tabular-nums; transition: background .25s, color .25s, border-color .25s; }.form-steps button.current { color: #436aab; }.form-steps button.current > span { color: #fff; background: #4d7cc8; border-color: #4d7cc8; }.form-steps i { width: 32px; height: 1px; background: #e1e7f0; }.form-steps button:hover:not(:disabled) { color: var(--auth-accent); }
.submit-row { display: flex; align-items: center; gap: 12px; margin-top: 30px; }.submit-row .auth-submit { margin-top: 0; }.step-back { min-height: 53px; padding: 0 15px; flex-shrink: 0; border: 1px solid var(--auth-border); border-radius: 10px; color: #7788a0; font-size: 12px; transition: background .2s, border-color .2s; }.step-back:hover:not(:disabled) { background: #f5f8fc; border-color: #b8cae4; }
.auth-form fieldset { display: flex; flex-direction: column; gap: 22px; border: 0; min-width: 0; padding: 0; margin: 0; }
.auth-field { min-width: 0; }.auth-field > label { display: block; margin-bottom: 9px; font-size: 13px; font-weight: 550; }.auth-field small { color: var(--auth-muted); font-weight: 400; }
.auth-field input { min-width: 0; width: 100%; height: 52px; border: 1px solid var(--auth-border); border-radius: 10px; background: #fbfcfe; padding: 0 15px; color: var(--auth-ink); font-size: 14px; transition: border-color .2s, box-shadow .2s, background .2s; }
.auth-field input::placeholder { color: #8995a7; }.auth-field input:hover:not(:disabled) { border-color: #b9c7dc; }.auth-field input:focus { outline: none; border-color: var(--auth-accent); background: #fff; box-shadow: 0 0 0 4px #2563eb0c; }.auth-field input[aria-invalid="true"] { border-color: #d16969; }
.auth-field-pair { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }.text-action { color: #6380ad; font-size: 12px; transition: color .2s; }.text-action:hover { color: var(--auth-accent); }
.code-input { display: flex; gap: 10px; }.code-input input { flex: 1; width: 0; }.code-input button { display: flex; align-items: center; justify-content: center; gap: 6px; width: 120px; min-height: 52px; flex-shrink: 0; border: 1px solid #d5e2f7; border-radius: 10px; background: #f4f8ff; color: #3c6ebc; font-size: 12px; font-weight: 500; transition: background .2s, border-color .2s, transform .2s; }.code-input button:hover:not(:disabled) { background: #eaf2ff; border-color: #b7cff5; }.code-input button:active:not(:disabled) { transform: scale(.97); }
.password-hint { display: flex; align-items: center; gap: 9px; min-height: 18px; margin-top: 10px; font-size: 10px; line-height: 1.6; color: #8895a9; }.strength-meter { display: flex; flex-shrink: 0; gap: 3px; }.strength-meter i { width: 14px; height: 3px; border-radius: 2px; background: #e6ebf2; transition: background .3s; }.strength-meter .filled { background: #87a9e8; }.strength-meter[data-score="3"] .filled { background: #459b85; }.hint-match { color: #388772; }.hint-mismatch { color: #b27151; }
.invite-toggle { display: flex; justify-content: space-between; align-items: center; min-height: 28px; width: 100%; color: #70829d; font-size: 12px; text-align: left; }.invite-toggle small { margin-left: 8px; padding: 2px 5px; font-size: 9px; color: #93a0b3; background: #f2f5f9; border-radius: 4px; }.invite-plus { font-size: 20px; color: #7693bd; font-weight: 300; transition: transform .35s var(--auth-ease); }.invite-plus.expanded { transform: rotate(135deg); }.invite-input { padding-top: 15px; }
.expand-region { display: grid; grid-template-rows: 1fr; }.expand-inner { min-height: 0; overflow: hidden; }.auth-expand-enter-active, .auth-expand-leave-active { transition: grid-template-rows .35s var(--auth-ease), opacity .25s; }.auth-expand-enter-from, .auth-expand-leave-to { grid-template-rows: 0fr; opacity: 0; }
.registration-state { padding: 12px 14px; margin: 0 0 20px; background: #f3f7fd; border: 1px solid #e1eaf7; border-radius: 10px; color: #657b9c; font-size: 12px; line-height: 1.8; }.registration-state p { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }.registration-state button { color: var(--auth-accent); text-decoration: underline; text-underline-offset: 3px; }.status-error { color: #ac5d5d; }
.inline-spinner { display: inline-block; flex-shrink: 0; width: 12px; height: 12px; border: 1.5px solid currentColor; border-right-color: transparent; border-radius: 50%; animation: spinner-turn .8s linear infinite; }
.auth-feedback { margin: 18px 0 0; padding: 12px 14px; border-radius: 9px; font-size: 12px; line-height: 1.7; }.feedback-error { background: #fff4f2; color: #b05b52; }.feedback-success { background: #edf8f3; color: #2f8668; }
.auth-submit { position: relative; display: flex; align-items: center; justify-content: space-between; width: 100%; min-height: 53px; margin-top: 30px; padding: 0 19px; background: #203652; border: 1px solid #203652; border-radius: 10px; color: #fff; font-size: 14px; font-weight: 500; box-shadow: 0 4px 10px #2036520a; transition: background .25s, border-color .25s, transform .25s var(--auth-ease), box-shadow .25s; }.auth-submit svg { width: 21px; height: 21px; fill: none; stroke: currentColor; stroke-width: 1.4; stroke-linecap: round; stroke-linejoin: round; transition: transform .3s var(--auth-ease); }.auth-submit:hover:not(:disabled) { background: var(--auth-accent); border-color: var(--auth-accent); transform: translateY(-2px); box-shadow: 0 8px 22px #2563eb20; }.auth-submit:hover:not(:disabled) svg { transform: translateX(4px); }.auth-submit:active:not(:disabled) { transform: translateY(0) scale(.992); box-shadow: none; }
.auth-form-footer { margin: 23px 0 0; display: flex; justify-content: center; align-items: center; gap: 8px; color: #8a96a8; font-size: 12px; }.auth-form-footer button { color: #506f9c; font-weight: 500; min-height: 28px; }.auth-form-footer button span { display: inline-block; margin-left: 3px; transition: transform .25s; }.auth-form-footer button:hover { color: var(--auth-accent); }.auth-form-footer button:hover span { transform: translate(2px, -2px); }
.auth-panel-note { display: flex; align-items: center; justify-content: center; gap: 8px; margin-top: 32px; padding-top: 22px; border-top: 1px solid #edf0f5; color: #98a3b4; font-size: 10px; }.auth-panel-note > span { width: 4px; height: 4px; border-radius: 50%; background: #8ca9d2; }
.auth-page button:disabled { cursor: not-allowed; opacity: .5; }.auth-page fieldset:disabled input { opacity: .6; }.auth-page button:focus-visible, .auth-page a:focus-visible { outline: 2px solid var(--auth-accent); outline-offset: 4px; }
.auth-flow-enter-active { transition: opacity .4s ease, transform .7s var(--auth-ease); }.auth-flow-leave-active { position: absolute; left: 0; top: 0; pointer-events: none; transition: opacity .2s ease, transform .32s var(--auth-ease); }.auth-flow-enter-from { opacity: 0; transform: translateX(calc(var(--flow-direction) * 20px)); }.auth-flow-leave-to { opacity: 0; transform: translateX(calc(var(--flow-direction) * -18px)); }
.auth-flow-enter-active .form-reveal { animation: field-in .5s var(--auth-ease) calc(var(--field-index, 0) * 28ms) backwards; }
.auth-flow-enter-active .submit-row { animation-delay: .16s; }.auth-flow-enter-active .auth-form-footer { animation-delay: .2s; }
/* Shared controls inherit their density from the available window height. */
.auth-intro { margin-bottom: var(--intro-gap); }
.intro-meta { margin-bottom: 10px; }.intro-meta p { color: #68819d; }.intro-meta i { color: #93a9bd; }
.auth-intro h2 { font-size: 30px; margin-bottom: 8px; }.auth-flow-content[data-mode="register"] .auth-intro h2 { font-size: 27px; }.intro-description { font-size: 12px; }
.auth-form fieldset { gap: var(--field-gap); }.auth-field > label { margin-bottom: var(--auth-label-gap); }
.auth-field input { height: var(--auth-input-height); background: var(--auth-control-bg); box-shadow: inset 0 1px 3px #37567b06, 0 1px 0 #ffffff70; }.auth-field input:focus { background: #ffffffa6; }
.form-steps { margin-top: 16px; gap: 14px; }.form-steps button { min-height: 28px; }.form-steps button > span { background: #ffffff55; border-color: #ffffffad; }.form-steps button.current > span { background: #4d7cc8; }.form-steps i { background: #a6bdd74d; }
.code-input button { min-height: var(--auth-input-height); background: #e7f2ff80; border-color: #ffffffad; }
.submit-row { margin-top: 22px; }.auth-submit, .step-back { min-height: var(--auth-input-height); }.auth-submit { background: linear-gradient(115deg, #234d7de8, #306091df); border-color: #e1eeff3d; box-shadow: inset 0 1px 0 #ffffff40, 0 6px 16px #2c557c18; }.step-back { background: #ffffff3d; border-color: #ffffffb3; }
.auth-form-footer { margin-top: 14px; color: #6f849c; }.auth-form-footer button { color: #456991; }
.registration-state { background: #edf5fc85; border-color: #ffffffa6; padding: 9px 12px; margin-bottom: 14px; font-size: 11px; }.auth-feedback { margin-top: 12px; padding: 9px 12px; }.feedback-error { background: #fff1ecbf; }.feedback-success { background: #eaf8f0c9; }
.invite-toggle small { background: #ffffff55; color: #7890a7; }.invite-input { padding-top: 10px; }.password-hint { color: #71859c; }.strength-meter i { background: #99b3ce40; }
.auth-page-footer { min-height: 28px; color: #69839d; font-size: 10px; animation: page-part-in .7s var(--auth-ease) .3s both; }.auth-page-footer > span:last-child { font-size: 8px; letter-spacing: .15em; }
.motion-toggle { display: inline-flex; align-items: center; gap: 8px; min-height: 30px; padding: 0 12px; border: 1px solid #ffffff9c; border-radius: 99px; background: #ffffff38; backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); font-size: 10px; color: #577796; transition: background .2s; }.motion-toggle:hover { background: #ffffff8c; }.motion-toggle > span { min-width: 11px; font-size: 12px; }
@keyframes page-part-in { from { opacity: 0; transform: translateY(18px); } to { opacity: 1; transform: translateY(0); } }
@keyframes field-in { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
@keyframes underline-in { to { transform: scaleX(1); } }
@keyframes spinner-turn { to { transform: rotate(360deg); } }
@media (max-width: 1100px) and (min-width: 761px) {
  .auth-page { padding-left: 28px; padding-right: 28px; }.auth-shell { grid-template-columns: minmax(0, 1fr) minmax(0, 400px); gap: 20px; padding: 18px; }.auth-story { padding-left: 10px; }.auth-story h1 { font-size: 34px; }.auth-panel { padding: 24px; }.story-links { gap: 25px; }.story-links a > span { gap: 16px; }
}
@media (max-width: 900px) and (min-width: 761px) {
  .auth-page { padding-left: 18px; padding-right: 18px; }.auth-shell { grid-template-columns: minmax(0, 1fr) minmax(0, 360px); gap: 14px; padding: 14px; }.auth-panel { padding: 20px; }.auth-story h1 { font-size: 28px; }.story-eyebrow { font-size: 8px; letter-spacing: .09em; }.story-description { font-size: 12px; }.story-links { gap: 18px; }.story-links a > span { gap: 10px; }
}
@media (min-width: 761px) and (max-height: 800px) {
  .auth-page { --auth-input-height: 44px; --field-gap: 13px; --intro-gap: 16px; gap: 12px; padding-top: 16px; padding-bottom: 12px; }
  .auth-shell { padding: 16px; border-radius: 26px; }.auth-panel { padding: 22px 26px; border-radius: 20px; }.auth-switcher { margin-bottom: 18px; }.auth-intro h2 { font-size: 27px; }.auth-flow-content[data-mode="register"] .auth-intro h2 { font-size: 25px; }
  .intro-meta { margin-bottom: 8px; }.form-steps { margin-top: 12px; }.submit-row { margin-top: 18px; }.auth-form-footer { margin-top: 10px; }.story-motion-label { margin-top: 38px; }.story-links { margin-top: 22px; }.auth-story { padding-top: 20px; padding-bottom: 20px; }
}
@media (min-width: 761px) and (max-height: 700px) {
  .auth-page { --auth-input-height: 40px; --field-gap: 10px; --intro-gap: 12px; --auth-label-gap: 5px; padding-top: 10px; padding-bottom: 8px; gap: 8px; }
  .auth-header { min-height: 34px; }.auth-header nav a { min-height: 34px; }.auth-brand { font-size: 22px; }.auth-shell { padding: 12px; }.auth-panel { padding: 16px 24px; }.auth-switcher { margin-bottom: 14px; }.auth-switcher button { min-height: 32px; }.intro-meta { display: none; }.auth-intro h2 { font-size: 25px; margin-bottom: 6px; }.auth-flow-content[data-mode="register"] .auth-intro h2 { font-size: 24px; }.intro-description { font-size: 11px; }.form-steps { margin-top: 8px; }.submit-row { margin-top: 14px; }.auth-form-footer { margin-top: 8px; font-size: 11px; }.registration-state { margin-bottom: 10px; padding: 7px 10px; }.password-hint { margin-top: 7px; }.story-motion-label { margin-top: 28px; }.story-eyebrow { margin-bottom: 18px; }.story-description { margin-top: 16px; }.auth-page-footer { min-height: 26px; }.motion-toggle { min-height: 26px; }
}
@media (max-width: 760px) {
  .auth-page { height: auto; min-height: 100svh; overflow: clip; padding: 20px 18px 16px; gap: 22px; grid-template-rows: auto 1fr auto; }
  .auth-header, .auth-page-footer { max-width: 460px; }.auth-brand { font-size: 21px; }.auth-header nav { padding: 0 12px; }.auth-header nav a { min-height: 34px; font-size: 10px; }.auth-header nav a:last-child { display: none; }
  .auth-viewport { padding: 8px 0; }.auth-shell { max-width: 460px; display: block; padding: 8px; border-radius: 26px; }.auth-story { display: none; }.auth-panel { padding: 26px 22px; border-radius: 20px; }.auth-switcher { margin-bottom: 26px; }.auth-field input { font-size: 16px; }.auth-intro h2 { font-size: 29px; }.auth-flow-content[data-mode="register"] .auth-intro h2 { font-size: 25px; }.auth-field-pair { grid-template-columns: 1fr; gap: var(--field-gap); }.auth-page-footer { font-size: 8px; gap: 8px; }.motion-toggle { font-size: 9px; padding: 0 10px; }.code-input button { width: 104px; font-size: 11px; }
}
@media (max-width: 360px) { .auth-page { padding-left: 12px; padding-right: 12px; }.auth-panel { padding: 22px 16px; }.auth-brand { font-size: 19px; }.auth-flow-content[data-mode="register"] .auth-intro h2 { font-size: 23px; }.intro-description { font-size: 11px; }.intro-meta p { font-size: 8px; }.form-steps { gap: 9px; }.form-steps i { width: 20px; }.code-input { gap: 7px; }.code-input button { width: 96px; }.auth-field input { padding-left: 12px; } }
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) { .auth-shell { background: #edf4fbe6; }.auth-panel { background: #ffffffd9; } }
@media (prefers-reduced-motion: reduce) { .auth-page *, .auth-page *::before, .auth-page *::after { animation: none !important; transition-duration: .01ms !important; }.story-emphasis::after { transform: scaleX(1); }.auth-flow-enter-from, .auth-flow-leave-to { transform: none; } }
</style>
