<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const visible = ref(false)
const message = ref('账号已被封禁，无法使用网站功能')

function show(event: Event) {
  const detail = (event as CustomEvent<{ message?: string }>).detail
  message.value = detail?.message || '账号已被封禁，无法使用网站功能'
  visible.value = true
}

function close() {
  visible.value = false
}

onMounted(() => window.addEventListener('imageCreater:banned', show))
onBeforeUnmount(() => window.removeEventListener('imageCreater:banned', show))
</script>

<template>
  <Teleport to="body">
    <Transition name="ban-modal">
      <div v-if="visible" class="fixed inset-0 z-[10000] grid place-items-center bg-slate-950/55 p-4 backdrop-blur-md" @click.self="close">
        <section class="ban-dialog w-full max-w-md overflow-hidden rounded-lg bg-white shadow-2xl">
          <div class="relative px-7 pb-6 pt-7 text-center">
            <div class="mx-auto grid h-16 w-16 place-items-center rounded-full bg-red-50 text-red-600 ring-8 ring-red-50/50">
              <svg class="h-8 w-8 ban-lock" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15.5v1.5m-5-8V7a5 5 0 0 1 10 0v2m-12 0h14v10H5V9Z" />
              </svg>
            </div>
            <h2 class="mt-5 text-2xl font-black text-slate-950">用户被封禁</h2>
            <p class="mx-auto mt-3 max-w-sm text-sm font-semibold leading-6 text-slate-600">{{ message }}</p>
            <button class="mt-6 h-11 rounded-lg bg-slate-950 px-6 text-sm font-black text-white transition hover:bg-red-600" @click="close">
              我知道了
            </button>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
