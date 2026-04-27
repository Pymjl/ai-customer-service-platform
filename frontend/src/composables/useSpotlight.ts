import { onBeforeUnmount, onMounted, ref } from 'vue'

export function useSpotlight<T extends HTMLElement>() {
  const target = ref<T | null>(null)
  let frame = 0

  const update = (event: PointerEvent) => {
    const element = target.value
    if (!element) return

    cancelAnimationFrame(frame)
    frame = requestAnimationFrame(() => {
      const rect = element.getBoundingClientRect()
      const x = event.clientX - rect.left
      const y = event.clientY - rect.top
      element.style.setProperty('--spotlight-x', `${x}px`)
      element.style.setProperty('--spotlight-y', `${y}px`)
    })
  }

  onMounted(() => {
    target.value?.addEventListener('pointermove', update)
  })

  onBeforeUnmount(() => {
    cancelAnimationFrame(frame)
    target.value?.removeEventListener('pointermove', update)
  })

  return { target }
}
