<script setup lang="ts">
import { ref } from 'vue'

withDefaults(defineProps<{
  id: string
  label: string
  placeholder?: string
  autocomplete?: string
  minlength?: number
  invalid?: boolean
  describedby?: string
}>(), { placeholder: '输入密码', autocomplete: 'new-password' })

const model = defineModel<string>({ default: '' })
const visible = ref(false)
const capsLock = ref(false)
function checkCapsLock(event: KeyboardEvent) {
  capsLock.value = event.getModifierState('CapsLock')
}
</script>

<template>
  <div class="auth-field password-field">
    <div class="field-label">
      <label :for="id">{{ label }}</label>
      <slot name="action" />
    </div>
    <div class="password-control" :class="{ 'is-visible': visible }">
      <input
        :id="id"
        v-model="model"
        :type="visible ? 'text' : 'password'"
        :autocomplete="autocomplete"
        :placeholder="placeholder"
        :minlength="minlength"
        :aria-invalid="invalid || undefined"
        :aria-describedby="describedby"
        required
        @keydown="checkCapsLock"
        @keyup="checkCapsLock"
        @blur="capsLock = false"
      />
      <button type="button" :aria-label="`${visible ? '隐藏' : '显示'}${label}`" :aria-pressed="visible" :aria-controls="id" @click="visible = !visible">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
          <circle cx="12" cy="12" r="2.5" />
          <path class="eye-slash" :class="{ visible }" d="m4 4 16 16" />
        </svg>
      </button>
    </div>
    <Transition name="caps-hint"><small v-if="capsLock" class="caps-hint" role="status">大写锁定已开启</small></Transition>
  </div>
</template>

<style scoped>
.password-field { min-width: 0; }
.field-label { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin-bottom: var(--auth-label-gap, 9px); }
label { color: var(--auth-ink); font-size: 13px; font-weight: 550; }
.password-control { position: relative; }
input { width: 100%; min-width: 0; height: var(--auth-input-height, 52px); padding: 0 48px 0 15px; border: 1px solid var(--auth-border); border-radius: 10px; background: var(--auth-control-bg, #fbfcfe); color: var(--auth-ink); font-size: 14px; transition: border-color .2s, box-shadow .2s, background .2s; }
input::placeholder { color: #8995a7; }
input:hover:not(:disabled) { border-color: #b9c7dc; }
input:focus { outline: none; border-color: var(--auth-accent); background: #ffffffa6; box-shadow: 0 0 0 4px #2563eb0c; }
input[aria-invalid="true"] { border-color: #d16969; }
input:disabled { opacity: .6; }
button { position: absolute; right: 4px; top: 4px; width: 44px; height: calc(var(--auth-input-height, 52px) - 8px); display: grid; place-items: center; border-radius: 7px; color: #7d8ba0; transition: color .2s, background .2s, transform .2s; }
button:hover:not(:disabled), .is-visible button { background: #edf3ff; color: var(--auth-accent); }
button:active:not(:disabled) { transform: scale(.92); }
button:focus-visible { outline: 2px solid var(--auth-accent); outline-offset: 2px; }
button:disabled { cursor: not-allowed; opacity: .5; }
svg { width: 18px; height: 18px; fill: none; stroke: currentColor; stroke-width: 1.5; stroke-linecap: round; }
.eye-slash { stroke-dasharray: 24; stroke-dashoffset: 24; transition: stroke-dashoffset .25s; }
.eye-slash.visible { stroke-dashoffset: 0; }
.caps-hint { display: block; margin-top: 7px; color: #a66c18; font-size: 11px; }
.caps-hint-enter-active, .caps-hint-leave-active { transition: opacity .2s, transform .2s; }
.caps-hint-enter-from, .caps-hint-leave-to { opacity: 0; transform: translateY(-4px); }
@media (max-width: 760px) { input { font-size: 16px; } }
@media (prefers-reduced-motion: reduce) { *, *::after { transition-duration: .01ms !important; } }
</style>
