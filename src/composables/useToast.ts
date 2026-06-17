import { computed, reactive } from 'vue'

export type ToastType = 'success' | 'error' | 'warning' | 'info'

export type ToastItem = {
  id: number
  type: ToastType
  title: string
  message?: string
  duration: number
}

type ToastInput = string | {
  title: string
  message?: string
  duration?: number
}

const toasts = reactive<ToastItem[]>([])
let seed = 0

const defaultTitles: Record<ToastType, string> = {
  success: '操作成功',
  error: '操作失败',
  warning: '请注意',
  info: '提示'
}

function normalize(type: ToastType, input: ToastInput, duration?: number) {
  if (typeof input === 'string') {
    return {
      title: input || defaultTitles[type],
      duration: duration ?? 2600
    }
  }

  return {
    title: input.title || defaultTitles[type],
    message: input.message,
    duration: input.duration ?? duration ?? 3200
  }
}

function push(type: ToastType, input: ToastInput, duration?: number) {
  const toast = {
    id: ++seed,
    type,
    ...normalize(type, input, duration)
  }

  toasts.unshift(toast)
  window.setTimeout(() => dismiss(toast.id), toast.duration)
  return toast.id
}

function dismiss(id: number) {
  const index = toasts.findIndex((toast) => toast.id === id)
  if (index >= 0) {
    toasts.splice(index, 1)
  }
}

export function useToast() {
  return {
    toasts: computed(() => toasts),
    success: (input: ToastInput, duration?: number) => push('success', input, duration),
    error: (input: ToastInput, duration?: number) => push('error', input, duration),
    warning: (input: ToastInput, duration?: number) => push('warning', input, duration),
    info: (input: ToastInput, duration?: number) => push('info', input, duration),
    dismiss
  }
}
