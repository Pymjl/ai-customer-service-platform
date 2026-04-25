<template>
  <div>
    <div class="page-header">
      <div>
        <h1 class="page-title">个人信息</h1>
        <p class="page-subtitle">维护头像、昵称、联系方式和基础资料</p>
      </div>
    </div>

    <el-card class="page-card profile-card" v-loading="loading">
      <div class="avatar-section">
        <el-avatar :size="96" :src="avatarSrc" />
        <el-upload :show-file-list="false" accept="image/*" :auto-upload="false" :on-change="handleAvatarChange">
          <el-button type="primary" :loading="uploading">上传头像</el-button>
        </el-upload>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" class="profile-form">
        <el-form-item label="账户名">
          <el-input v-model="form.username" disabled />
        </el-form-item>
        <el-form-item label="昵称" prop="realName">
          <el-input v-model.trim="form.realName" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-select v-model="form.gender" style="width: 100%">
            <el-option label="保密" :value="0" />
            <el-option label="男" :value="1" />
            <el-option label="女" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="年龄" prop="age">
          <el-input-number v-model="form.age" :min="1" :max="120" style="width: 100%" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model.trim="form.email" maxlength="128" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model.trim="form.phone" maxlength="32" />
        </el-form-item>
        <el-form-item label="住址" prop="address">
          <el-input v-model.trim="form.address" type="textarea" :rows="3" maxlength="256" show-word-limit />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存资料</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { ElMessage } from 'element-plus'
import { updateUser, updateUserAvatar } from '@/api/system'
import { useAuthStore } from '@/stores/auth'
import defaultAvatar from '@/assets/default-avatar.webp'

const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)

const form = reactive({
  userId: '',
  username: '',
  avatarPath: '',
  gender: 0,
  realName: '',
  age: 18,
  email: '',
  phone: '',
  address: '',
  status: 1
})

const nicknamePattern = /^[\p{L}\p{N}_\-\s]{1,50}$/u
const phonePattern = /^\+?[0-9][0-9\-\s]{5,30}$/

const rules: FormRules = {
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
  address: [{ max: 256, message: '住址不能超过 256 个字符', trigger: 'blur' }]
}

const avatarSrc = computed(() => form.avatarPath || defaultAvatar)

async function loadProfile() {
  loading.value = true
  try {
    if (!authStore.user) {
      await authStore.loadCurrentUser()
    }
    const user = authStore.user
    if (!user?.userId) return
    Object.assign(form, {
      userId: user.userId,
      username: user.username,
      avatarPath: user.avatarPath || '',
      gender: user.gender ?? 0,
      realName: user.realName || '',
      age: user.age ?? 18,
      email: user.email || '',
      phone: user.phone || '',
      address: user.address || '',
      status: 1
    })
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    await updateUser(form.userId, form)
    await authStore.loadCurrentUser()
    ElMessage.success('资料已更新')
  } finally {
    saving.value = false
  }
}

async function handleAvatarChange(uploadFile: UploadFile) {
  if (!form.userId || !uploadFile.raw) return
  uploading.value = true
  try {
    const avatarPath = await updateUserAvatar(form.userId, uploadFile.raw)
    form.avatarPath = avatarPath
    await authStore.loadCurrentUser()
    ElMessage.success('头像已更新')
  } finally {
    uploading.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-card {
  max-width: 760px;
  padding: 8px;
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 24px;
}

.profile-form {
  max-width: 560px;
}
</style>
