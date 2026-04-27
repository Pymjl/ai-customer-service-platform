<template>
  <div class="slider-captcha" :class="{ verified }">
    <button class="captcha-trigger" type="button" :class="{ verified }" @click="openCaptcha">
      <span class="trigger-icon">
        <n-icon :component="verified ? Checkmark24Regular : Shield24Regular" />
      </span>
      <span class="trigger-copy">
        <strong>{{ verified ? '安全验证已完成' : '点击完成安全验证' }}</strong>
        <small>{{ verified ? '需要时可点击重新验证' : '弹窗内拖动滑块补齐缺口' }}</small>
      </span>
      <n-icon class="trigger-arrow" :component="verified ? ArrowSync24Regular : ArrowRight24Regular" />
    </button>

    <Transition name="captcha-popover">
      <div v-if="panelVisible" ref="panelRef" class="captcha-popover" role="dialog" aria-label="安全验证">
        <div class="popover-header">
          <div class="modal-title">
            <span class="trigger-icon">
              <n-icon :component="Shield24Regular" />
            </span>
            <span>
              <strong>安全验证</strong>
              <small>拖动拼图滑块完成验证</small>
            </span>
          </div>
          <button class="close-button" type="button" :disabled="verifying" aria-label="关闭验证码" @click="closePanel">
            ×
          </button>
        </div>

        <div class="captcha-panel">
          <div
            ref="imageRef"
            class="captcha-image"
            :style="{ aspectRatio: challenge ? `${challenge.width} / ${challenge.height}` : '2 / 1' }"
          >
            <canvas
              v-if="challenge"
              ref="backgroundCanvasRef"
              class="captcha-bg"
              :width="challenge.width"
              :height="challenge.height"
            />
            <canvas
              v-if="challenge"
              ref="pieceCanvasRef"
              class="captcha-piece"
              :width="challenge.sliderWidth"
              :height="challenge.sliderWidth"
              :style="pieceStyle"
            />
            <button class="refresh-button" type="button" :disabled="loading || verifying" aria-label="刷新验证码" @click="reload">
              <n-icon :component="ArrowSync24Regular" />
            </button>
            <div v-if="loading || loadError" class="captcha-state">
              {{ loadError || '加载中' }}
            </div>
          </div>

          <div ref="trackRef" class="slider-track">
            <div class="slider-progress" :style="{ width: `${progressWidth}px` }" />
            <div class="slider-text">{{ sliderText }}</div>
            <button
              class="slider-button"
              type="button"
              :disabled="loading || verifying || verified || !challenge"
              :style="{ transform: `translateX(${offset}px)` }"
              :aria-label="sliderText"
              @mousedown="startDrag"
              @touchstart.prevent="startTouchDrag"
            >
              <n-icon :component="verified ? Checkmark24Regular : verifying ? ArrowSync24Regular : ArrowRight24Regular" />
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowRight24Regular, ArrowSync24Regular, Checkmark24Regular, Shield24Regular } from '@vicons/fluent'
import { fetchCaptcha, verifyCaptcha } from '@/api/auth'
import type { CaptchaChallenge } from '@/types/system'

const emit = defineEmits<{
  verified: [token: string]
  reset: []
}>()

const trackRef = ref<HTMLElement>()
const imageRef = ref<HTMLElement>()
const backgroundCanvasRef = ref<HTMLCanvasElement>()
const pieceCanvasRef = ref<HTMLCanvasElement>()
const challenge = ref<CaptchaChallenge>()
const offset = ref(0)
const maxOffset = ref(0)
const dragging = ref(false)
const startX = ref(0)
const startOffset = ref(0)
const verified = ref(false)
const loading = ref(false)
const verifying = ref(false)
const panelVisible = ref(false)
const loadError = ref('')
const panelRef = ref<HTMLElement>()
let closeTimer = 0

const buttonSize = 44

const progressWidth = computed(() => offset.value + buttonSize)

const sliderText = computed(() => {
  if (loading.value) return '正在加载验证码图片'
  if (loadError.value) return '请刷新验证码后重试'
  if (verifying.value) return '正在验证'
  if (verified.value) return '验证通过'
  return '拖动滑块补齐缺口'
})

const imageScale = computed(() => {
  if (!challenge.value || !imageRef.value) return 1
  return imageRef.value.clientWidth / challenge.value.width
})

const pieceOffset = computed(() => {
  if (!challenge.value || !imageRef.value || !trackRef.value || maxOffset.value <= 0) return 0
  const maxPieceOffset = imageRef.value.clientWidth - challenge.value.sliderWidth * imageScale.value
  return (offset.value / maxOffset.value) * maxPieceOffset
})

const pieceStyle = computed(() => {
  if (!challenge.value) return {}
  const size = challenge.value.sliderWidth * imageScale.value
  const y = challenge.value.sliderY * imageScale.value
  return {
    width: `${size}px`,
    height: `${size}px`,
    transform: `translate(${pieceOffset.value}px, ${y}px)`
  }
})

function prepareDrag(clientX: number) {
  if (verified.value || verifying.value || loading.value || !challenge.value || !trackRef.value) return
  const rect = trackRef.value.getBoundingClientRect()
  maxOffset.value = Math.max(rect.width - buttonSize, 0)
  dragging.value = true
  startX.value = clientX
  startOffset.value = offset.value
}

function startDrag(event: MouseEvent) {
  prepareDrag(event.clientX)
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', stopDrag)
}

function startTouchDrag(event: TouchEvent) {
  prepareDrag(event.touches[0].clientX)
  window.addEventListener('touchmove', onTouchMove, { passive: false })
  window.addEventListener('touchend', stopDrag)
}

function onMove(event: MouseEvent) {
  moveTo(event.clientX)
}

function onTouchMove(event: TouchEvent) {
  event.preventDefault()
  moveTo(event.touches[0].clientX)
}

function moveTo(clientX: number) {
  if (!dragging.value) return
  const nextOffset = startOffset.value + clientX - startX.value
  offset.value = Math.min(Math.max(nextOffset, 0), maxOffset.value)
}

async function stopDrag() {
  if (!dragging.value) return
  dragging.value = false
  removeDragListeners()
  await submitVerify()
}

async function submitVerify() {
  if (!challenge.value || !imageRef.value) return
  verifying.value = true
  try {
    const scale = imageRef.value.clientWidth / challenge.value.width
    const x = Math.round(pieceOffset.value / scale)
    const result = await verifyCaptcha({ challengeId: challenge.value.challengeId, x })
    verified.value = true
    emit('verified', result.captchaToken)
    closeTimer = window.setTimeout(() => {
      panelVisible.value = false
    }, 360)
  } catch {
    await reload()
  } finally {
    verifying.value = false
  }
}

async function loadChallenge() {
  loading.value = true
  loadError.value = ''
  try {
    challenge.value = await fetchCaptcha()
    await nextTick()
    maxOffset.value = Math.max((trackRef.value?.clientWidth ?? 0) - buttonSize, 0)
    await drawCaptcha()
  } catch {
    challenge.value = undefined
    loadError.value = '验证码加载失败，请刷新重试'
  } finally {
    loading.value = false
  }
}

async function drawCaptcha() {
  if (!challenge.value) return
  await Promise.all([
    drawImageToCanvas(backgroundCanvasRef.value, challenge.value.backgroundImage, challenge.value.width, challenge.value.height),
    drawImageToCanvas(pieceCanvasRef.value, challenge.value.sliderImage, challenge.value.sliderWidth, challenge.value.sliderWidth)
  ])
}

function drawImageToCanvas(canvas: HTMLCanvasElement | undefined, src: string, width: number, height: number) {
  return new Promise<void>((resolve, reject) => {
    if (!canvas) {
      resolve()
      return
    }
    const context = canvas.getContext('2d')
    if (!context) {
      resolve()
      return
    }
    const image = new Image()
    image.onload = () => {
      canvas.width = width
      canvas.height = height
      context.clearRect(0, 0, width, height)
      context.drawImage(image, 0, 0, width, height)
      resolve()
    }
    image.onerror = () => reject(new Error('验证码图片加载失败'))
    image.src = src
  })
}

async function reload() {
  offset.value = 0
  verified.value = false
  emit('reset')
  await loadChallenge()
}

function reset() {
  offset.value = 0
  verified.value = false
  challenge.value = undefined
  loadError.value = ''
  emit('reset')
}

function closePanel() {
  if (verifying.value) return
  panelVisible.value = false
}

async function openCaptcha() {
  if (verified.value) {
    reset()
  }
  panelVisible.value = true
  await nextTick()
  if (!challenge.value) {
    await loadChallenge()
  } else {
    maxOffset.value = Math.max((trackRef.value?.clientWidth ?? 0) - buttonSize, 0)
  }
}

function handleOutsidePointer(event: PointerEvent) {
  if (!panelVisible.value || verifying.value) return
  const target = event.target as Node | null
  if (!target) return
  if (panelRef.value?.contains(target)) return
  if ((target as HTMLElement).closest?.('.captcha-trigger')) return
  closePanel()
}

function removeDragListeners() {
  window.removeEventListener('mousemove', onMove)
  window.removeEventListener('mouseup', stopDrag)
  window.removeEventListener('touchmove', onTouchMove)
  window.removeEventListener('touchend', stopDrag)
}

onMounted(() => {
  // 弹窗打开时再加载验证码，减少登录页首屏负担。
  document.addEventListener('pointerdown', handleOutsidePointer)
})

onBeforeUnmount(() => {
  window.clearTimeout(closeTimer)
  document.removeEventListener('pointerdown', handleOutsidePointer)
  removeDragListeners()
})

defineExpose({ reset })
</script>

<style scoped>
.slider-captcha {
  position: relative;
  width: 100%;
  user-select: none;
}

.captcha-trigger {
  width: 100%;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
  color: var(--text-primary);
  text-align: left;
  border: 1px solid var(--glass-border);
  border-radius: 16px;
  background: var(--glass-surface);
  cursor: pointer;
  box-shadow: var(--shadow-sm), var(--glass-highlight);
  transition:
    transform var(--duration-spring) var(--ease-spring),
    background-color var(--duration-base) var(--ease-out-quint),
    border-color var(--duration-base) var(--ease-out-quint),
    box-shadow var(--duration-base) var(--ease-out-quint);
}

.captcha-trigger:hover {
  transform: translateY(-1px);
  background: var(--glass-surface-hover);
  border-color: var(--accent-primary);
  box-shadow: var(--shadow-md);
}

.captcha-trigger.verified {
  border-color: color-mix(in srgb, var(--accent-success) 64%, var(--glass-border));
  background: color-mix(in srgb, var(--accent-success) 9%, var(--glass-surface-solid));
}

.trigger-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  color: var(--text-on-accent);
  border-radius: 14px;
  background: linear-gradient(135deg, var(--accent-cyan), var(--accent-primary));
  box-shadow: 0 10px 20px var(--accent-primary-muted);
}

.verified .trigger-icon {
  background: linear-gradient(135deg, #34d399, var(--accent-success));
}

.trigger-copy {
  min-width: 0;
}

.trigger-copy strong,
.trigger-copy small,
.modal-title strong,
.modal-title small {
  display: block;
}

.trigger-copy strong,
.modal-title strong {
  line-height: 1.3;
}

.trigger-copy small,
.modal-title small {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
}

.trigger-arrow {
  color: var(--text-muted);
}

.captcha-popover {
  position: absolute;
  right: 0;
  bottom: calc(100% + 12px);
  z-index: 50;
  width: min(92vw, 460px);
  color: var(--text-primary);
  background: var(--glass-surface);
  backdrop-filter: blur(var(--glass-blur-card));
  -webkit-backdrop-filter: blur(var(--glass-blur-card));
  border: 1px solid var(--glass-border);
  border-radius: 22px;
  box-shadow: var(--shadow-lg), var(--glass-highlight);
  transform-origin: right bottom;
}

.captcha-popover::after {
  content: "";
  position: absolute;
  right: 22px;
  bottom: -7px;
  width: 14px;
  height: 14px;
  background: var(--glass-surface);
  border-right: 1px solid var(--glass-border);
  border-bottom: 1px solid var(--glass-border);
  transform: rotate(45deg);
}

.popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 18px 12px;
}

.close-button {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  color: var(--text-muted);
  border: 1px solid var(--border-subtle);
  border-radius: 999px;
  background: var(--bg-surface);
  cursor: pointer;
  font-size: 20px;
  line-height: 1;
}

.close-button:hover {
  color: var(--text-primary);
  background: var(--bg-surface-muted);
}

.modal-title {
  display: flex;
  gap: 12px;
  align-items: center;
}

.captcha-panel {
  display: grid;
  gap: 14px;
  padding: 0 18px 18px;
}

.captcha-image {
  position: relative;
  width: 100%;
  overflow: hidden;
  border: 1px solid var(--glass-border);
  border-radius: 16px;
  background: var(--bg-surface-muted);
}

.captcha-bg,
.captcha-piece {
  position: absolute;
  inset: 0 auto auto 0;
  display: block;
  pointer-events: none;
}

.captcha-bg {
  width: 100%;
  height: 100%;
}

.captcha-piece {
  filter: drop-shadow(0 8px 12px rgba(15, 23, 42, 0.24));
  will-change: transform;
}

.refresh-button {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  color: var(--text-on-accent);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.56);
  cursor: pointer;
}

.captcha-state {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  background: rgba(248, 250, 252, 0.72);
  font-size: 14px;
}

.slider-track {
  position: relative;
  height: 44px;
  margin-top: 10px;
  overflow: hidden;
  border: 1px solid var(--glass-border);
  border-radius: 999px;
  background: var(--bg-surface-muted);
}

.slider-progress {
  position: absolute;
  inset: 0 auto 0 0;
  width: 44px;
  background: linear-gradient(135deg, var(--accent-cyan), var(--accent-primary));
  transition: width var(--duration-fast) var(--ease-out-quint);
}

.slider-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  font-size: 14px;
}

.slider-button {
  position: absolute;
  top: 0;
  left: 0;
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  color: var(--accent-primary);
  border: none;
  border-radius: 999px;
  background: var(--bg-surface);
  box-shadow: 0 8px 18px var(--accent-primary-muted);
  cursor: grab;
  transition:
    transform var(--duration-fast) var(--ease-out-quint),
    color var(--duration-fast) var(--ease-out-quint);
}

.slider-button:disabled {
  cursor: default;
}

.slider-button:active {
  cursor: grabbing;
}

.verified .slider-track {
  border-color: color-mix(in srgb, var(--accent-success) 55%, transparent);
  background: color-mix(in srgb, var(--accent-success) 10%, var(--bg-surface));
}

.verified .slider-progress {
  background: linear-gradient(135deg, #34d399, var(--accent-success));
}

.verified .slider-text,
.verified .slider-button {
  color: var(--accent-success);
}

.captcha-popover-enter-active,
.captcha-popover-leave-active {
  transition:
    opacity var(--duration-base) var(--ease-out-quint),
    transform var(--duration-spring) var(--ease-spring),
    filter var(--duration-base) var(--ease-out-quint);
}

.captcha-popover-enter-from,
.captcha-popover-leave-to {
  opacity: 0;
  transform: translate(10px, 14px) scale(0.96);
  filter: blur(4px);
}

.captcha-popover-enter-to,
.captcha-popover-leave-from {
  opacity: 1;
  transform: translate(0, 0) scale(1);
  filter: blur(0);
}

@media (max-width: 520px) {
  .captcha-popover {
    right: 50%;
    width: calc(100vw - 32px);
    transform-origin: center bottom;
  }

  .captcha-popover-enter-from,
  .captcha-popover-leave-to {
    transform: translate(50%, 14px) scale(0.96);
  }

  .captcha-popover-enter-to,
  .captcha-popover-leave-from {
    transform: translate(50%, 0) scale(1);
  }

  .captcha-popover::after {
    right: 50%;
    margin-right: -7px;
  }
}
</style>
