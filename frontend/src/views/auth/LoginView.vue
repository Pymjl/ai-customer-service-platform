<template>
  <main class="login-page">
    <section class="login-card" aria-labelledby="login-title">
      <div class="auth-brand">
        <span class="brand-mark">
          <n-icon :component="Bot24Regular" />
        </span>
        <div>
          <strong>智能客服工作站</strong>
          <span>AI Customer Service</span>
        </div>
      </div>

      <div class="auth-heading">
        <h1 id="login-title">欢迎回来</h1>
        <p>请登录以继续探索您的 AI 知识库</p>
      </div>

      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top" @keyup.enter="handleLogin">
        <n-form-item label="账户名" path="username">
          <n-input v-model:value="form.username" maxlength="100" show-count placeholder="请输入账户名">
            <template #prefix>
              <n-icon :component="Person24Regular" />
            </template>
          </n-input>
        </n-form-item>
        <n-form-item label="密码" path="password">
          <n-input v-model:value="form.password" type="password" show-password-on="click" placeholder="请输入密码">
            <template #prefix>
              <n-icon :component="LockClosed24Regular" />
            </template>
          </n-input>
        </n-form-item>
        <n-form-item label="安全验证" path="captchaToken">
          <SliderCaptcha ref="captchaRef" @verified="form.captchaToken = $event" @reset="form.captchaToken = ''" />
        </n-form-item>
        <n-button class="submit" type="primary" size="large" block :loading="loading" @click="handleLogin">
          登录
        </n-button>
      </n-form>

      <div class="auth-link">还没有账户？<RouterLink to="/register">立即注册</RouterLink></div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInst, FormRules } from 'naive-ui'
import { Bot24Regular, LockClosed24Regular, Person24Regular } from '@vicons/fluent'
import SliderCaptcha from '@/components/SliderCaptcha.vue'
import { useAuthStore } from '@/stores/auth'
import { message } from '@/utils/feedback'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const formRef = ref<FormInst | null>(null)
const captchaRef = ref<InstanceType<typeof SliderCaptcha>>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  captchaToken: ''
})

const usernamePattern = /^[A-Za-z0-9]{1,100}$/

const rules: FormRules = {
  username: [
    { required: true, message: '请输入账户名', trigger: 'blur' },
    { pattern: usernamePattern, message: '账户名仅支持字母和数字，长度不超过 100 位', trigger: 'blur' }
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaToken: [{ required: true, message: '请完成滑块验证', trigger: 'change' }]
}

async function handleLogin() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await authStore.login({ ...form, username: form.username.toLowerCase() })
    message.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch {
    captchaRef.value?.reset()
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

.login-card:hover {
  background: var(--glass-surface-hover);
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

@media (max-width: 767px) {
  .login-page {
    padding: 16px;
  }

  .login-card {
    padding: 28px 20px;
    border-radius: 18px;
  }
}
</style>
