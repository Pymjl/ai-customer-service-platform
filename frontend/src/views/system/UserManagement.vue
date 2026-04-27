<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">维护用户账户名、头像、昵称和联系方式</p>
      </div>
      <n-button type="primary" @click="openCreate">
        <template #icon><n-icon :component="Add24Regular" /></template>
        新增用户
      </n-button>
    </div>

    <section class="page-card">
      <div class="toolbar">
        <n-input v-model:value="query.keyword" clearable placeholder="搜索账户名 / 昵称 / 邮箱" @keyup.enter="loadData">
          <template #prefix><n-icon :component="Search24Regular" /></template>
        </n-input>
        <n-button type="primary" @click="loadData">查询</n-button>
        <n-button @click="resetQuery">
          <template #icon><n-icon :component="ArrowSync24Regular" /></template>
          重置
        </n-button>
      </div>
      <n-spin :show="loading">
        <table class="data-table">
          <thead>
            <tr>
              <th>头像</th>
              <th>账户名</th>
              <th>昵称</th>
              <th>性别</th>
              <th>年龄</th>
              <th>邮箱</th>
              <th>电话</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in users" :key="row.userId">
              <td><n-avatar round :src="resolveAvatarUrl(row.avatarPath)" :fallback-src="defaultAvatar" /></td>
              <td>{{ row.username }}</td>
              <td>{{ row.realName || '-' }}</td>
              <td>{{ genderText(row.gender) }}</td>
              <td>{{ row.age || '-' }}</td>
              <td>{{ row.email || '-' }}</td>
              <td>{{ row.phone || '-' }}</td>
              <td><span class="status-pill" :class="row.status === 1 ? 'success' : ''">{{ row.status === 1 ? '启用' : '停用' }}</span></td>
              <td><n-button text type="primary" @click="openEdit(row)">编辑</n-button></td>
            </tr>
          </tbody>
        </table>
        <n-empty v-if="!users.length" description="暂无用户" />
      </n-spin>
      <n-pagination class="pagination" v-model:page="query.current" v-model:page-size="query.size" show-size-picker :item-count="total" @update:page="loadData" @update:page-size="loadData" />
    </section>

    <n-modal v-model:show="dialogVisible" preset="card" :title="editingId ? '编辑用户' : '新增用户'" class="form-modal">
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="90">
        <n-form-item label="账户名" path="username"><n-input v-model:value="form.username" :disabled="Boolean(editingId)" maxlength="100" show-count /></n-form-item>
        <n-form-item v-if="!editingId" label="密码" path="password"><n-input v-model:value="form.password" type="password" show-password-on="click" maxlength="100" /></n-form-item>
        <n-form-item label="昵称" path="realName"><n-input v-model:value="form.realName" maxlength="50" show-count /></n-form-item>
        <n-form-item label="性别" path="gender"><n-select v-model:value="form.gender" :options="genderOptions" /></n-form-item>
        <n-form-item label="年龄" path="age"><n-input-number v-model:value="form.age" :min="1" :max="120" /></n-form-item>
        <n-form-item label="邮箱" path="email"><n-input v-model:value="form.email" maxlength="128" /></n-form-item>
        <n-form-item label="电话" path="phone"><n-input v-model:value="form.phone" maxlength="32" /></n-form-item>
        <n-form-item label="住址" path="address"><n-input v-model:value="form.address" type="textarea" :rows="2" maxlength="256" show-count /></n-form-item>
        <n-form-item label="状态"><n-switch v-model:value="enabled"><template #checked>启用</template><template #unchecked>停用</template></n-switch></n-form-item>
      </n-form>
      <template #footer>
        <div class="modal-actions">
          <n-button @click="dialogVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInst, FormRules } from 'naive-ui'
import { Add24Regular, ArrowSync24Regular, Search24Regular } from '@vicons/fluent'
import { createUser, fetchUsers, updateUser } from '@/api/system'
import type { UserItem } from '@/types/system'
import { defaultAvatar, resolveAvatarUrl } from '@/utils/avatar'
import { message } from '@/utils/feedback'

const loading = ref(false)
const saving = ref(false)
const users = ref<UserItem[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const editingId = ref<UserItem['userId'] | null>(null)
const formRef = ref<FormInst | null>(null)

const query = reactive({ keyword: '', current: 1, size: 10 })
const form = reactive({ username: '', password: '', tenantId: 'default', realName: '', gender: 0, age: 18, email: '', phone: '', address: '', status: 1 })
const enabled = computed({ get: () => form.status === 1, set: (value: boolean) => { form.status = value ? 1 : 0 } })
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
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度为 6-100 位', trigger: 'blur' }
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
  address: [{ max: 256, message: '住址不能超过 256 个字符', trigger: 'blur' }]
}

function genderText(value?: number) {
  return value === 1 ? '男' : value === 2 ? '女' : '保密'
}

async function loadData() {
  loading.value = true
  try {
    const result = await fetchUsers(query)
    users.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.current = 1
  loadData()
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { username: '', password: '', tenantId: 'default', realName: '', gender: 0, age: 18, email: '', phone: '', address: '', status: 1 })
  dialogVisible.value = true
}

function openEdit(row: UserItem) {
  editingId.value = row.userId
  Object.assign(form, { username: row.username, password: '', tenantId: row.tenantId, realName: row.realName || '', gender: row.gender ?? 0, age: row.age ?? 18, email: row.email || '', phone: row.phone || '', address: row.address || '', status: row.status ?? 1 })
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = { ...form, username: form.username.toLowerCase() }
    if (editingId.value) {
      await updateUser(editingId.value, payload)
    } else {
      await createUser(payload)
    }
    message.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.toolbar :deep(.n-input) {
  max-width: 320px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 18px;
}

.form-modal {
  width: min(92vw, 580px);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

:deep(.n-input-number) {
  width: 100%;
}
</style>
