<script setup lang="ts">
import { computed, nextTick, ref, useId, watch } from 'vue'

const props = defineProps<{
  modelValue: string
  options: { value: string; label: string }[]
  label: string
  disabled?: boolean
  loading?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const id = useId()
const open = ref(false)
const query = ref('')
const activeIndex = ref(0)
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const search = ref<HTMLInputElement | null>(null)
const selected = computed(() => props.options.find(option => option.value === props.modelValue))
const filtered = computed(() => props.options.filter(option => `${option.label} ${option.value}`.toLowerCase().includes(query.value.trim().toLowerCase())))
const choices = computed(() => [{ value: '', label: '使用 CCSwitch 默认' }, ...filtered.value])
watch(query, () => { activeIndex.value = 0 })
watch(() => props.disabled || props.loading, value => { if (value) open.value = false })

async function toggle() {
  open.value = !open.value
  if (!open.value) return
  query.value = ''
  activeIndex.value = Math.max(0, choices.value.findIndex(option => option.value === props.modelValue))
  await nextTick()
  search.value?.focus()
  scrollActive()
}
function close() {
  open.value = false
  trigger.value?.focus()
}
function choose(value: string) {
  emit('update:modelValue', value)
  close()
}
function scrollActive() {
  root.value?.querySelector(`#${CSS.escape(id)}-option-${activeIndex.value}`)?.scrollIntoView({ block: 'nearest' })
}
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    close()
  } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    activeIndex.value = (activeIndex.value + (event.key === 'ArrowDown' ? 1 : -1) + choices.value.length) % choices.value.length
    scrollActive()
  } else if (event.key === 'Enter') {
    event.preventDefault()
    const option = choices.value[activeIndex.value]
    if (option) choose(option.value)
  }
}
function onFocusout(event: FocusEvent) {
  if (!root.value?.contains(event.relatedTarget as Node | null)) open.value = false
}
</script>

<template>
  <div ref="root" class="model-select" @focusout="onFocusout">
    <label :id="`${id}-label`" class="model-label" :for="`${id}-trigger`">{{ label }}</label>
    <button :id="`${id}-trigger`" ref="trigger" type="button" class="model-trigger" :class="{ 'is-open': open }" :disabled="disabled || loading" :aria-expanded="open" :aria-controls="`${id}-list`" aria-haspopup="listbox" @click="toggle" @keydown.down.prevent="!open && toggle()">
      <span class="model-value" :title="selected?.label || modelValue">{{ loading ? '正在加载分组模型…' : selected?.label || modelValue || '使用 CCSwitch 默认' }}</span>
      <svg viewBox="0 0 20 20" class="model-chevron" :class="{ rotated: open }" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m5 7.5 5 5 5-5" stroke-linecap="round" stroke-linejoin="round" /></svg>
    </button>
    <div v-if="open" class="model-menu">
      <div class="model-search-wrap">
        <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><circle cx="8.5" cy="8.5" r="5.5" /><path d="m13 13 4 4" /></svg>
        <input ref="search" v-model="query" class="model-search" placeholder="搜索模型名称…" role="combobox" aria-autocomplete="list" :aria-label="`搜索${label}`" :aria-expanded="open" :aria-controls="`${id}-list`" :aria-activedescendant="`${id}-option-${activeIndex}`" @keydown="onKeydown" />
        <span class="model-count">{{ filtered.length }}</span>
      </div>
      <div :id="`${id}-list`" role="listbox" :aria-labelledby="`${id}-label`" class="model-options">
        <div v-for="(option, index) in choices" :id="`${id}-option-${index}`" :key="option.value" role="option" :aria-selected="modelValue === option.value" class="model-option" :class="{ selected: modelValue === option.value, active: activeIndex === index }" @mouseenter="activeIndex = index" @mousedown.prevent @click="choose(option.value)">
          <span class="model-option-text"><span>{{ option.label }}</span><small v-if="option.value && option.value !== option.label">{{ option.value }}</small></span>
          <svg v-if="modelValue === option.value" viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m4 10 4 4 8-8" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </div>
        <p v-if="!filtered.length" class="model-empty">{{ query ? '没有匹配的模型，试试其他关键词' : '当前分组暂无可选模型' }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.model-select { min-width: 0; }
.model-label { display: block; margin-bottom: 6px; font-size: 12px; font-weight: 700; color: #475569; }
.model-trigger { display: flex; width: 100%; align-items: center; justify-content: space-between; gap: 10px; min-height: 42px; padding: 10px 12px; border: 1px solid #e2e8f0; border-radius: 10px; background: #fff; text-align: left; color: #334155; transition: border-color 150ms, box-shadow 150ms; }
.model-trigger:hover { border-color: #6ee7b7; }
.model-trigger:focus-visible, .model-trigger.is-open { outline: none; border-color: #10b981; box-shadow: 0 0 0 3px #10b98116; }
.model-trigger:disabled { cursor: wait; opacity: .6; }
.model-value { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 600; }
.model-chevron { width: 16px; height: 16px; flex-shrink: 0; color: #94a3b8; transition: transform 150ms; }
.model-chevron.rotated { transform: rotate(180deg); }
.model-menu { margin-top: 6px; overflow: hidden; border: 1px solid #dbe7e2; border-radius: 12px; background: #fff; box-shadow: 0 8px 24px #0f172a0d; }
.model-search-wrap { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border-bottom: 1px solid #f1f5f9; color: #94a3b8; }
.model-search { width: 100%; min-width: 0; border: 0; outline: none; background: transparent; font-size: 12px; color: #334155; }
.model-count { border-radius: 5px; background: #f1f5f9; padding: 1px 6px; font-size: 10px; font-variant-numeric: tabular-nums; }
.model-options { max-height: 208px; overflow-y: auto; overscroll-behavior: contain; padding: 5px; }
.model-option { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 9px 10px; border-radius: 7px; cursor: pointer; font-size: 12px; font-weight: 600; color: #475569; }
.model-option.active { background: #f1f5f9; }
.model-option.selected { background: #ecfdf5; color: #047857; }
.model-option.active.selected { background: #d1fae5; }
.model-option-text { min-width: 0; overflow-wrap: anywhere; }
.model-option-text small { display: block; margin-top: 2px; font-size: 10px; font-weight: 400; color: #64748b; }
.model-option > svg { flex-shrink: 0; }
.model-empty { padding: 16px 10px; text-align: center; font-size: 12px; color: #94a3b8; }
</style>
