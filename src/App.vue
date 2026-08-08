<template>
  <div
    class="app-ui-root"
    :class="{
      'portal-ui': isPortalPage,
      'legacy-ui': canSwitchUi && useLegacyUi,
      'glass-ui': canSwitchUi && !useLegacyUi
    }"
    :style="appStyle"
  >
    <div v-if="canSwitchUi && !useLegacyUi" class="app-glass-background" aria-hidden="true" />
    <label v-if="showLegacyUiToggle" class="legacy-ui-toggle">
      <span>使用旧版UI</span>
      <input v-model="useLegacyUi" type="checkbox" />
    </label>
    <RouterView />
    <AppToastHost />
    <BannedModal />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/authStore'
import { useToast } from '@/composables/useToast'
import AppToastHost from '@/components/AppToastHost.vue'
import BannedModal from '@/components/BannedModal.vue'
import backgroundImage from '@/assets/images/background.png'

const legacyUiKey = 'imageCreater_use_legacy_ui'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToast()

let handlingUnauthorized = false

function onUnauthorized() {
  if (handlingUnauthorized) return
  const hadSession = auth.isAuthenticated || Boolean(auth.userInfo)
  auth.logout()
  if (!hadSession) return
  handlingUnauthorized = true
  toast.warning('登录状态已过期，请重新登录')
  const target = route.meta.requiresAuth
    ? { path: '/login', query: { redirect: route.fullPath } }
    : '/login'
  router.push(target).finally(() => {
    window.setTimeout(() => {
      handlingUnauthorized = false
    }, 800)
  })
}

onMounted(() => {
  window.addEventListener('imageCreater:unauthorized', onUnauthorized)
})

onBeforeUnmount(() => {
  window.removeEventListener('imageCreater:unauthorized', onUnauthorized)
})
const useLegacyUi = ref(window.localStorage.getItem(legacyUiKey) === 'true')
const isPortalPage = computed(() => route.path === '/')
const isGeneratePage = computed(() => route.path === '/create' || route.path === '/user/generate')
const isRelayConsolePage = computed(() => route.path === '/admin/relay')
const canSwitchUi = computed(() => !isPortalPage.value)
const showLegacyUiToggle = computed(() => canSwitchUi.value && !isGeneratePage.value && !isRelayConsolePage.value)

const appStyle = computed(() =>
  canSwitchUi.value && !useLegacyUi.value ? { '--app-background-image': `url("${backgroundImage}")` } : undefined
)

watch(useLegacyUi, (value) => {
  window.localStorage.setItem(legacyUiKey, String(value))
})
</script>
