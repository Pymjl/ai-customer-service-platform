<template>
  <div class="auth-shell">
    <section class="auth-hero">
      <div class="auth-brand">
        <el-icon><Promotion /></el-icon>
        <span>AI Customer Service</span>
      </div>
      <h1>加入 AI 智能客服平台</h1>
      <p>注册后即可登录系统，使用智能客服能力，并在个人信息页面维护头像和基础资料。</p>
    </section>
    <section class="auth-panel-wrap">
      <div class="auth-panel">
        <h2>用户注册</h2>
        <p class="hint">默认头像可在登录后修改</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="账户名" prop="username">
            <el-input v-model.trim="form.username" size="large" maxlength="100" show-word-limit placeholder="仅支持字母和数字" />
          </el-form-item>
          <el-form-item label="昵称" prop="realName">
            <el-input v-model.trim="form.realName" size="large" maxlength="50" show-word-limit placeholder="请输入昵称" />
          </el-form-item>
          <div class="form-row">
            <el-form-item label="性别" prop="gender">
              <el-select v-model="form.gender" size="large" style="width: 100%">
                <el-option label="保密" :value="0" />
                <el-option label="男" :value="1" />
                <el-option label="女" :value="2" />
              </el-select>
            </el-form-item>
            <el-form-item label="年龄" prop="age">
              <el-input-number v-model="form.age" :min="1" :max="120" size="large" style="width: 100%" />
            </el-form-item>
          </div>
          <el-form-item label="邮箱">
            <el-input v-model.trim="form.email" size="large" maxlength="128" placeholder="可选" />
          </el-form-item>
          <el-form-item label="电话">
            <el-input v-model.trim="form.phone" size="large" maxlength="32" placeholder="可选" />
          </el-form-item>
          <el-form-item label="住址">
            <el-input v-model.trim="form.address" size="large" maxlength="256" show-word-limit placeholder="可选" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" size="large" type="password" show-password maxlength="100" placeholder="6-100 位" />
          </el-form-item>
          <el-form-item label="安全验证" prop="captchaToken">
            <SliderCaptcha ref="captchaRef" @verified="form.captchaToken = $event" @reset="form.captchaToken = ''" />
          </el-form-item>
          <el-button class="submit" type="primary" size="large" :loading="loading" @click="handleRegister">注册</el-button>
        </el-form>
        <div class="auth-link">已有账户？<RouterLink to="/login">返回登录</RouterLink></div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { registerWithAvatar } from '@/api/auth'
import SliderCaptcha from '@/components/SliderCaptcha.vue'
import defaultAvatarUrl from '@/assets/default-avatar.webp'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
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
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  age: [
    { required: true, message: '请输入年龄', trigger: 'change' },
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
    ElMessage.success('注册成功，请登录')
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
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

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
