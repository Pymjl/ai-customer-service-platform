<template>
  <div class="auth-shell">
    <section class="auth-hero">
      <div class="auth-brand">
        <el-icon><Service /></el-icon>
        <span>AI Customer Service</span>
      </div>
      <h1>AI 智能客服平台</h1>
      <p>为用户提供智能问答、业务咨询和服务协同入口，让常见问题处理更及时、更清晰。</p>
    </section>
    <section class="auth-panel-wrap">
      <div class="auth-panel">
        <h2>欢迎登录</h2>
        <p class="hint">请输入账户名和密码进入系统</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleLogin">
          <el-form-item label="账户名" prop="username">
            <el-input v-model.trim="form.username" size="large" maxlength="100" show-word-limit placeholder="请输入账户名" prefix-icon="User" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" size="large" type="password" show-password placeholder="请输入密码" prefix-icon="Lock" />
          </el-form-item>
          <el-form-item label="安全验证" prop="captchaToken">
            <SliderCaptcha ref="captchaRef" @verified="form.captchaToken = $event" @reset="form.captchaToken = ''" />
          </el-form-item>
          <el-button class="submit" type="primary" size="large" :loading="loading" @click="handleLogin">登录</el-button>
        </el-form>
        <div class="auth-link">还没有账户？<RouterLink to="/register">立即注册</RouterLink></div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import SliderCaptcha from '@/components/SliderCaptcha.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
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
    ElMessage.success('登录成功')
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
.submit {
  width: 100%;
  height: 44px;
  margin-top: 8px;
  border-radius: 12px;
  font-weight: 700;
}

.auth-link {
  margin-top: 20px;
  text-align: center;
  color: #64748b;
}

.auth-link a {
  color: #2563eb;
  font-weight: 700;
}
</style>
