<template>
  <div :class="['profile-view', { embedded }]">
    <div v-if="!embedded" class="page-header">
      <div>
        <h1 class="page-title">个人信息</h1>
        <p class="page-subtitle">维护头像、昵称、联系方式和基础资料</p>
      </div>
    </div>

    <section :class="['profile-surface', { 'page-card profile-card': !embedded }]">
      <n-spin :show="loading">
        <div class="avatar-section">
          <n-upload :show-file-list="false" accept="image/*" :default-upload="false" @change="handleAvatarChange">
            <button class="avatar-uploader" type="button" :disabled="uploading" aria-label="更换头像">
              <n-avatar round :size="84" :src="avatarSrc" :fallback-src="defaultAvatar" />
              <span class="avatar-overlay">更换头像</span>
              <span class="camera-badge" aria-hidden="true">
                <n-icon :component="Camera24Regular" />
              </span>
            </button>
          </n-upload>
          <div>
            <strong>{{ form.realName || form.username || '用户' }}</strong>
            <span>{{ authStore.user?.roles?.join(' / ') || '普通用户' }}</span>
          </div>
        </div>

        <n-form ref="formRef" :model="form" :rules="rules" label-placement="top" class="profile-form">
          <n-form-item label="账户名">
            <n-input class="readonly-input" v-model:value="form.username" readonly :bordered="false">
              <template #suffix>
                <span class="readonly-hint"><n-icon :component="LockClosed24Regular" />不可修改</span>
              </template>
            </n-input>
          </n-form-item>
          <n-form-item label="昵称" path="realName">
            <n-input v-model:value="form.realName" maxlength="50" show-count placeholder="请输入昵称" />
          </n-form-item>
          <n-form-item label="性别" path="gender">
            <n-select v-model:value="form.gender" :options="genderOptions" />
          </n-form-item>
          <n-form-item label="年龄" path="age">
            <n-input-number v-model:value="form.age" :min="1" :max="120" :show-button="false" placeholder="请输入年龄" />
          </n-form-item>
          <n-form-item label="邮箱" path="email">
            <n-input v-model:value="form.email" maxlength="128" placeholder="请输入邮箱，例如 user@example.com" />
          </n-form-item>
          <n-form-item label="电话" path="phone">
            <n-input v-model:value="form.phone" maxlength="32" placeholder="请输入电话，例如 138 0000 0000" />
          </n-form-item>
          <n-form-item label="住址" path="address">
            <n-input v-model:value="form.address" type="textarea" :rows="3" maxlength="256" show-count placeholder="请输入住址" />
          </n-form-item>
        </n-form>
      </n-spin>
    </section>

    <footer :class="['profile-actions', { embedded }]">
      <n-button @click="handleCancel">取消</n-button>
      <n-button type="primary" :loading="saving" @click="handleSave">保存资料</n-button>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInst, FormRules, UploadFileInfo } from 'naive-ui'
import { Camera24Regular, LockClosed24Regular } from '@vicons/fluent'
import { updateUser, updateUserAvatar } from '@/api/system'
import { useAuthStore } from '@/stores/auth'
import { defaultAvatar, resolveAvatarUrl } from '@/utils/avatar'
import { message } from '@/utils/feedback'

const props = defineProps<{
  embedded?: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInst | null>(null)
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

const genderOptions = [
  { label: '保密', value: 0 },
  { label: '男', value: 1 },
  { label: '女', value: 2 }
]
const nicknamePattern = /^[\p{L}\p{N}_\-\s]{1,50}$/u
const phonePattern = /^\+?[0-9][0-9\-\s]{5,30}$/

const rules: FormRules = {
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
  address: [{ max: 256, message: '住址不能超过 256 个字符', trigger: 'blur' }]
}

const avatarSrc = computed(() => resolveAvatarUrl(form.avatarPath))

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
    message.success('资料已更新')
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  if (props.embedded) {
    emit('close')
    return
  }
  if (!history.state?.back) {
    router.push('/chat')
    return
  }
  router.back()
}

async function handleAvatarChange(options: { file: UploadFileInfo }) {
  if (!form.userId || !options.file.file) return
  uploading.value = true
  try {
    const avatarPath = await updateUserAvatar(form.userId, options.file.file)
    form.avatarPath = avatarPath
    await authStore.loadCurrentUser()
    message.success('头像已更新')
  } finally {
    uploading.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-view {
  display: grid;
  gap: 16px;
}

.profile-view.embedded {
  min-height: 100%;
  grid-template-rows: minmax(0, 1fr) auto;
}

.profile-card {
  max-width: 780px;
}

.profile-surface {
  min-height: 0;
}

.profile-view.embedded .profile-surface {
  padding: 20px 22px;
  overflow: auto;
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 24px;
}

.avatar-section strong,
.avatar-section span {
  display: block;
}

.avatar-section span {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 13px;
}

.avatar-uploader {
  position: relative;
  display: block;
  width: 88px;
  height: 88px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
}

.avatar-uploader:disabled {
  cursor: wait;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #fff;
  border-radius: 50%;
  background: rgba(17, 24, 39, 0.48);
  opacity: 0;
  font-size: 12px;
  font-weight: 750;
  transition: opacity var(--duration-base) var(--ease-out-quint);
}

.avatar-uploader:hover .avatar-overlay,
.avatar-uploader:focus-visible .avatar-overlay {
  opacity: 1;
}

.camera-badge {
  position: absolute;
  right: 0;
  bottom: 2px;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  color: var(--text-on-accent);
  border: 2px solid var(--bg-surface);
  border-radius: 50%;
  background: rgba(0, 102, 204, 0.92);
}

.profile-form {
  max-width: 580px;
}

.profile-view.embedded .profile-form {
  max-width: none;
}

.readonly-input {
  background: var(--bg-surface-muted);
  border-radius: 10px;
}

.readonly-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.profile-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.profile-actions.embedded {
  position: sticky;
  bottom: 0;
  padding: 14px 22px;
  border-top: 1px solid var(--border-subtle);
  background: var(--glass-surface);
  backdrop-filter: blur(var(--glass-blur-floating));
  box-shadow: var(--glass-highlight);
}

:deep(.n-input-number) {
  width: 100%;
}
</style>
