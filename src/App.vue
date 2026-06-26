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
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppToastHost from '@/components/AppToastHost.vue'
import BannedModal from '@/components/BannedModal.vue'
import backgroundImage from '@/assets/images/background.png'

const legacyUiKey = 'imageCreater_use_legacy_ui'

const route = useRoute()
const useLegacyUi = ref(window.localStorage.getItem(legacyUiKey) === 'true')
const isPortalPage = computed(() => route.path === '/')
const isGeneratePage = computed(() => route.path === '/create' || route.path === '/user/generate')
const canSwitchUi = computed(() => !isPortalPage.value)
const showLegacyUiToggle = computed(() => canSwitchUi.value && !isGeneratePage.value)

const appStyle = computed(() =>
  canSwitchUi.value && !useLegacyUi.value ? { '--app-background-image': `url("${backgroundImage}")` } : undefined
)

watch(useLegacyUi, (value) => {
  window.localStorage.setItem(legacyUiKey, String(value))
})
</script>
