<template>
  <main class="login-page register-page">
    <section class="login-card register-card" aria-labelledby="register-title">
      <div class="auth-brand">
        <span class="brand-mark">
          <n-icon :component="Sparkle24Regular" />
        </span>
        <div>
          <strong>加入智能客服平台</strong>
          <span>AI Customer Service</span>
        </div>
      </div>

      <div class="auth-heading">
        <h1 id="register-title">用户注册</h1>
        <p>默认头像可在登录后修改</p>
      </div>

      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
        <n-form-item label="账户名" path="username">
          <n-input v-model:value="form.username" maxlength="100" show-count placeholder="仅支持字母和数字" />
        </n-form-item>
        <n-form-item label="昵称" path="realName">
          <n-input v-model:value="form.realName" maxlength="50" show-count placeholder="请输入昵称" />
        </n-form-item>
        <div class="form-row">
          <n-form-item label="性别" path="gender">
            <n-select v-model:value="form.gender" :options="genderOptions" />
          </n-form-item>
          <n-form-item label="年龄" path="age">
            <n-input-number v-model:value="form.age" :min="1" :max="120" :show-button="false" placeholder="请输入年龄" />
          </n-form-item>
        </div>
        <n-form-item label="邮箱" path="email">
          <n-input v-model:value="form.email" maxlength="128" placeholder="请输入邮箱，例如 user@example.com" />
        </n-form-item>
        <n-form-item label="电话" path="phone">
          <n-input v-model:value="form.phone" maxlength="32" placeholder="请输入电话，例如 138 0000 0000" />
        </n-form-item>
        <n-form-item label="住址" path="address">
          <n-input v-model:value="form.address" maxlength="256" show-count placeholder="请输入住址" />
        </n-form-item>
        <n-form-item label="密码" path="password">
          <n-input v-model:value="form.password" type="password" show-password-on="click" maxlength="100" placeholder="6-100 位" />
        </n-form-item>
        <n-form-item label="安全验证" path="captchaToken">
          <SliderCaptcha ref="captchaRef" @verified="form.captchaToken = $event" @reset="form.captchaToken = ''" />
        </n-form-item>
        <n-button class="submit" type="primary" size="large" block :loading="loading" @click="handleRegister">
          注册
        </n-button>
      </n-form>

      <div class="auth-link">已有账户？<RouterLink to="/login">返回登录</RouterLink></div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInst, FormRules } from 'naive-ui'
import { Sparkle24Regular } from '@vicons/fluent'
import { registerWithAvatar } from '@/api/auth'
import SliderCaptcha from '@/components/SliderCaptcha.vue'
import defaultAvatarUrl from '@/assets/default-avatar.webp'
import { useAuthStore } from '@/stores/auth'
import { message } from '@/utils/feedback'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInst | null>(null)
const captchaRef = ref<InstanceType<typeof SliderCaptcha>>()
const loading = ref(false)

const form = reactive({
  username: '',
  realName: '',
  gender: 0,
  age: 18,
  email: '',
  phone: '',
  address: '',
  password: '',
  captchaToken: ''
})

const genderOptions = [
  { label: '保密', value: 0 },
  { label: '男', value: 1 },
  { label: '女', value: 2 }
]

const usernamePattern = /^[A-Za-z0-9]{1,100}$/
const nicknamePattern = /^[\p{L}\p{N}_\-\s]{1,50}$/u
const phonePattern = /^\+?[0-9][0-9\-\s]{5,30}$/

const rules: FormRules = {
  username: [
    { required: true, message: '请输入账户名', trigger: 'blur' },
    { pattern: usernamePattern, message: '账户名仅支持字母和数字，长度不超过 100 位', trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { pattern: nicknamePattern, message: '昵称仅支持中英文、数字、空格、下划线和短横线，长度不超过 50 位', trigger: 'blur' }
  ],
  gender: [{ required: true, type: 'number', message: '请选择性别', trigger: 'change' }],
  age: [
    { required: true, type: 'number', message: '请输入年龄', trigger: 'change' },
    { type: 'number', min: 1, max: 120, message: '年龄范围为 1-120', trigger: 'change' }
  ],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }],
  phone: [{ pattern: phonePattern, message: '请输入正确的电话号码', trigger: 'blur' }],
  address: [{ max: 256, message: '住址不能超过 256 个字符', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度为 6-100 位', trigger: 'blur' }
  ],
  captchaToken: [{ required: true, message: '请完成滑块验证', trigger: 'change' }]
}

async function defaultAvatarFile() {
  const response = await fetch(defaultAvatarUrl)
  const blob = await response.blob()
  return new File([blob], 'default-avatar.webp', { type: 'image/webp' })
}

async function handleRegister() {
  await formRef.value?.validate()
  loading.value = true
  try {
    const result = await registerWithAvatar({ ...form, username: form.username.toLowerCase() }, await defaultAvatarFile())
    message.success('注册成功，请登录')
    const loggedIn = await authStore.applyRegisterResult(result)
    router.replace(loggedIn ? '/' : '/login')
  } catch (error) {
    captchaRef.value?.reset()
    throw error
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  overflow: hidden;
  background-color: var(--bg-app);
}

.login-page::before {
  content: "";
  position: absolute;
  inset: -6%;
  background-image:
    radial-gradient(at 0% 0%, var(--mesh-color-1) 0, transparent 50%),
    radial-gradient(at 100% 0%, var(--mesh-color-2) 0, transparent 50%),
    radial-gradient(at 100% 100%, var(--mesh-color-3) 0, transparent 50%);
  animation: meshBreathe 15s ease-in-out infinite alternate;
  will-change: transform;
}

.login-card {
  position: relative;
  z-index: 1;
  width: min(100%, 460px);
  padding: 48px;
  color: var(--text-primary);
  background: var(--glass-surface);
  backdrop-filter: blur(var(--glass-blur-card));
  -webkit-backdrop-filter: blur(var(--glass-blur-card));
  border: 1px solid var(--glass-border);
  border-radius: 24px;
  box-shadow: var(--shadow-lg), var(--glass-highlight);
  animation: spatialFadeIn var(--duration-spring) var(--ease-spring) both;
}

.auth-brand {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 34px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  color: var(--text-on-accent);
  border-radius: 16px;
  background: linear-gradient(135deg, var(--accent-cyan), var(--accent-primary));
  box-shadow: 0 12px 24px var(--accent-primary-muted);
}

.auth-brand strong,
.auth-brand span,
.auth-heading h1,
.auth-heading p {
  display: block;
  margin: 0;
}

.auth-brand strong {
  font-size: 17px;
}

.auth-brand span {
  color: var(--text-muted);
  font-size: 12px;
}

.auth-heading {
  margin-bottom: 28px;
}

.auth-heading h1 {
  font-size: 32px;
  line-height: var(--leading-tight);
}

.auth-heading p {
  margin-top: 8px;
  color: var(--text-muted);
}

.submit {
  height: 46px;
  margin-top: 4px;
}

.auth-link {
  margin-top: 22px;
  color: var(--text-muted);
  text-align: center;
}

.auth-link a {
  color: var(--accent-primary);
  font-weight: 750;
}

.register-card {
  width: min(100%, 560px);
  max-height: calc(100vh - 32px);
  overflow: auto;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

:deep(.n-input-number) {
  width: 100%;
}

@media (max-width: 560px) {
  .login-page {
    padding: 16px;
  }

  .login-card {
    padding: 28px 20px;
    border-radius: 18px;
  }

  .form-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
