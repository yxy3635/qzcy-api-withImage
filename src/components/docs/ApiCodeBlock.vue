<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'

const props = withDefaults(defineProps<{
  code: string
  language?: string
  label?: string
}>(), {
  language: 'text',
  label: ''
})

const copied = ref(false)
let copiedTimer: ReturnType<typeof setTimeout> | undefined

async function copyCode() {
  try {
    await navigator.clipboard.writeText(props.code)
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = props.code
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    textarea.remove()
  }

  copied.value = true
  if (copiedTimer) window.clearTimeout(copiedTimer)
  copiedTimer = window.setTimeout(() => {
    copied.value = false
  }, 1600)
}

onBeforeUnmount(() => {
  if (copiedTimer) window.clearTimeout(copiedTimer)
})
</script>

<template>
  <figure class="api-code-block">
    <figcaption class="api-code-toolbar">
      <span>{{ label || language }}</span>
      <button type="button" :aria-label="copied ? '代码已复制' : '复制代码'" @click="copyCode">
        {{ copied ? '已复制' : '复制' }}
      </button>
    </figcaption>
    <pre><code :class="`language-${language}`">{{ code }}</code></pre>
  </figure>
</template>

<style scoped>
.api-code-block {
  margin: 18px 0 0;
  overflow: hidden;
  border: 1px solid #1e293b;
  border-radius: 8px;
  background: #0b1220;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.12);
}

.api-code-toolbar {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
  padding: 0 14px 0 16px;
  background: #111b2e;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0;
  text-transform: uppercase;
}

.api-code-toolbar button {
  min-width: 54px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  padding: 5px 8px;
  color: #cbd5e1;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: none;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.api-code-toolbar button:hover,
.api-code-toolbar button:focus-visible {
  background: rgba(34, 211, 238, 0.12);
  color: #67e8f9;
  outline: none;
}

pre {
  max-height: 520px;
  margin: 0;
  overflow: auto;
  padding: 20px;
  color: #dbeafe;
  font-family: "Cascadia Code", "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 12.5px;
  line-height: 1.75;
  tab-size: 2;
}

code {
  font-family: inherit;
  white-space: pre;
}

@media (max-width: 640px) {
  pre {
    padding: 16px;
    font-size: 11.5px;
  }
}
</style>
