<template>
  <div>
    <div class="page-header">
      <div>
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">维护用户账户名、头像、昵称和联系方式</p>
      </div>
      <el-button type="primary" icon="Plus" @click="openCreate">新增用户</el-button>
    </div>

    <el-card class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" clearable placeholder="搜索账户名 / 昵称 / 邮箱" style="width: 280px" @keyup.enter="loadData" />
        <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </div>
      <el-table v-loading="loading" :data="users" stripe>
        <el-table-column label="头像" width="80">
          <template #default="{ row }">
            <el-avatar :src="row.avatarPath || defaultAvatar" />
          </template>
        </el-table-column>
        <el-table-column prop="username" label="账户名" min-width="120" />
        <el-table-column prop="realName" label="昵称" min-width="120" />
        <el-table-column label="性别" width="90">
          <template #default="{ row }">{{ genderText(row.gender) }}</template>
        </el-table-column>
        <el-table-column prop="age" label="年龄" width="80" />
        <el-table-column prop="email" label="邮箱" min-width="170" />
        <el-table-column prop="phone" label="电话" min-width="130" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pagination" layout="total, prev, pager, next, sizes" :total="total" v-model:current-page="query.current" v-model:page-size="query.size" @change="loadData" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新增用户'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="账户名" prop="username"><el-input v-model.trim="form.username" :disabled="Boolean(editingId)" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item v-if="!editingId" label="密码" prop="password"><el-input v-model="form.password" type="password" show-password maxlength="100" /></el-form-item>
        <el-form-item label="昵称" prop="realName"><el-input v-model.trim="form.realName" maxlength="50" show-word-limit /></el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-select v-model="form.gender" style="width: 100%">
            <el-option label="保密" :value="0" />
            <el-option label="男" :value="1" />
            <el-option label="女" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="年龄" prop="age"><el-input-number v-model="form.age" :min="1" :max="120" style="width: 100%" /></el-form-item>
        <el-form-item label="邮箱" prop="email"><el-input v-model.trim="form.email" maxlength="128" /></el-form-item>
        <el-form-item label="电话" prop="phone"><el-input v-model.trim="form.phone" maxlength="32" /></el-form-item>
        <el-form-item label="住址" prop="address"><el-input v-model.trim="form.address" type="textarea" :rows="2" maxlength="256" show-word-limit /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="enabled" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { createUser, fetchUsers, updateUser } from '@/api/system'
import type { UserItem } from '@/types/system'
import defaultAvatar from '@/assets/default-avatar.webp'

const loading = ref(false)
const saving = ref(false)
const users = ref<UserItem[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const editingId = ref<UserItem['userId'] | null>(null)
const formRef = ref<FormInstance>()

const query = reactive({ keyword: '', current: 1, size: 10 })
const form = reactive({ username: '', password: '', tenantId: 'default', realName: '', gender: 0, age: 18, email: '', phone: '', address: '', status: 1 })
const enabled = computed({ get: () => form.status === 1, set: (value: boolean) => { form.status = value ? 1 : 0 } })
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
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  age: [
    { required: true, message: '请输入年龄', trigger: 'change' },
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
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.pagination { margin-top: 18px; justify-content: flex-end; }
.muted { color: #94a3b8; }
</style>
