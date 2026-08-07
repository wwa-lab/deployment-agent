import { onBeforeUnmount, onMounted } from 'vue'

export function useVisiblePolling(callback: () => Promise<unknown> | unknown, intervalMs = 10_000) {
  let intervalId: number | undefined
  let mounted = false

  function pageIsVisible(): boolean {
    return document.visibilityState === 'visible'
  }

  function stopTimer() {
    if (intervalId !== undefined) {
      window.clearInterval(intervalId)
      intervalId = undefined
    }
  }

  function runIfVisible() {
    if (mounted && pageIsVisible()) {
      void callback()
    }
  }

  function startTimer() {
    stopTimer()
    if (!mounted || !pageIsVisible()) return
    intervalId = window.setInterval(runIfVisible, intervalMs)
  }

  function handleVisibilityChange() {
    if (pageIsVisible()) {
      runIfVisible()
      startTimer()
    } else {
      stopTimer()
    }
  }

  onMounted(() => {
    mounted = true
    document.addEventListener('visibilitychange', handleVisibilityChange)
    startTimer()
  })

  onBeforeUnmount(() => {
    mounted = false
    stopTimer()
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  })

  return { refreshNow: runIfVisible }
}
