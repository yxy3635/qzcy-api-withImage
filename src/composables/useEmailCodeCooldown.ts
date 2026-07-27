import { onBeforeUnmount, ref } from 'vue'

export function useEmailCodeCooldown(defaultSeconds = 60) {
  const secondsLeft = ref(0)
  let timer: ReturnType<typeof window.setInterval> | undefined

  function clearTimer() {
    if (timer !== undefined) {
      window.clearInterval(timer)
      timer = undefined
    }
  }

  function start(seconds = defaultSeconds) {
    clearTimer()
    secondsLeft.value = seconds
    timer = window.setInterval(() => {
      secondsLeft.value = Math.max(0, secondsLeft.value - 1)
      if (secondsLeft.value === 0) {
        clearTimer()
      }
    }, 1_000)
  }

  onBeforeUnmount(clearTimer)

  return { secondsLeft, start }
}
