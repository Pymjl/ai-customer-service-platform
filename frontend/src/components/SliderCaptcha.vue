<template>
  <div class="slider-captcha" :class="{ verified }">
    <div
      class="captcha-image"
      ref="imageRef"
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
      <button class="refresh-button" type="button" :disabled="loading" @click="reload">
        <el-icon><Refresh /></el-icon>
      </button>
      <div v-if="loading" class="captcha-state">加载中</div>
    </div>

    <div class="slider-track" ref="trackRef">
      <div class="slider-progress" :style="{ width: `${progressWidth}px` }" />
      <div class="slider-text">{{ sliderText }}</div>
      <button
        class="slider-button"
        type="button"
        :disabled="loading || verifying || verified || !challenge"
        :style="{ transform: `translateX(${offset}px)` }"
        @mousedown="startDrag"
        @touchstart.prevent="startTouchDrag"
      >
        <el-icon><Select v-if="verified" /><Loading v-else-if="verifying" /><DArrowRight v-else /></el-icon>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
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

const buttonSize = 44

const progressWidth = computed(() => offset.value + buttonSize)

const sliderText = computed(() => {
  if (loading.value) return '正在加载验证码图片'
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
  } catch {
    await reload()
  } finally {
    verifying.value = false
  }
}

async function loadChallenge() {
  loading.value = true
  try {
    challenge.value = await fetchCaptcha()
    await nextTick()
    maxOffset.value = Math.max((trackRef.value?.clientWidth ?? 0) - buttonSize, 0)
    await drawCaptcha()
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
  void reload()
}

function removeDragListeners() {
  window.removeEventListener('mousemove', onMove)
  window.removeEventListener('mouseup', stopDrag)
  window.removeEventListener('touchmove', onTouchMove)
  window.removeEventListener('touchend', stopDrag)
}

onMounted(() => {
  void loadChallenge()
})

onBeforeUnmount(() => {
  removeDragListeners()
})

defineExpose({ reset })
</script>

<style scoped>
.slider-captcha {
  width: 100%;
  user-select: none;
}

.captcha-image {
  position: relative;
  width: 100%;
  overflow: hidden;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background: #eef2f7;
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
  filter: drop-shadow(0 8px 12px rgba(15, 23, 42, .24));
  will-change: transform;
}

.refresh-button {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, .7);
  border-radius: 999px;
  background: rgba(15, 23, 42, .48);
  color: #fff;
  cursor: pointer;
}

.captcha-state {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(248, 250, 252, .72);
  color: #334155;
  font-size: 14px;
}

.slider-track {
  position: relative;
  height: 44px;
  margin-top: 10px;
  overflow: hidden;
  border: 1px solid #dbe3ef;
  border-radius: 999px;
  background: #eef2f7;
}

.slider-progress {
  position: absolute;
  inset: 0 auto 0 0;
  width: 44px;
  background: linear-gradient(135deg, #60a5fa, #2563eb);
  transition: width .18s ease;
}

.slider-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
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
  border: none;
  border-radius: 999px;
  background: #fff;
  color: #2563eb;
  box-shadow: 0 8px 18px rgba(37, 99, 235, .24);
  cursor: grab;
  transition: transform .18s ease, color .18s ease;
}

.slider-button:disabled {
  cursor: default;
}

.slider-button:active {
  cursor: grabbing;
}

.verified .slider-track {
  border-color: rgba(16, 185, 129, .45);
  background: #ecfdf5;
}

.verified .slider-progress {
  background: linear-gradient(135deg, #34d399, #10b981);
}

.verified .slider-text,
.verified .slider-button {
  color: #059669;
}
</style>
