<template>
  <div>
    <div class="page-header">
      <div>
        <h1 class="page-title">用户角色授权</h1>
        <p class="page-subtitle">选择用户后勾选其拥有的角色</p>
      </div>
      <el-button type="primary" :disabled="!selectedUser" :loading="saving" @click="handleSave">保存授权</el-button>
    </div>
    <div class="grant-grid">
      <el-card class="page-card">
        <template #header>用户列表</template>
        <el-input v-model="keyword" clearable placeholder="搜索用户" @keyup.enter="loadUsers" />
        <el-table v-loading="userLoading" :data="users" class="grant-table" highlight-current-row @current-change="selectUser">
          <el-table-column prop="username" label="账户名" />
          <el-table-column prop="realName" label="昵称" />
        </el-table>
      </el-card>
      <el-card class="page-card">
        <template #header>角色授权</template>
        <el-alert v-if="!selectedUser" title="请先从左侧选择用户" type="info" show-icon :closable="false" />
        <div v-else>
          <div class="selected-title">当前用户：{{ selectedUser.realName || selectedUser.username }}</div>
          <el-checkbox-group v-model="checkedRoleIds" class="role-list">
            <el-checkbox v-for="role in roles" :key="role.id" :label="role.id" border>{{ role.name }}（{{ role.code }}）</el-checkbox>
          </el-checkbox-group>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchRoles, fetchUserRoleIds, fetchUsers, saveUserRoles } from '@/api/system'
import type { RoleItem, UserItem } from '@/types/system'

const keyword = ref('')
const userLoading = ref(false)
const saving = ref(false)
const users = ref<UserItem[]>([])
const roles = ref<RoleItem[]>([])
const selectedUser = ref<UserItem | null>(null)
const checkedRoleIds = ref<Array<RoleItem['id']>>([])

async function loadUsers() {
  userLoading.value = true
  try { users.value = (await fetchUsers({ keyword: keyword.value, current: 1, size: 50 })).records } finally { userLoading.value = false }
}

async function loadRoles() { roles.value = (await fetchRoles({ current: 1, size: 200 })).records }

async function selectUser(row?: UserItem) {
  if (!row) return
  selectedUser.value = row
  checkedRoleIds.value = await fetchUserRoleIds(row.userId)
}

async function handleSave() {
  if (!selectedUser.value) return
  saving.value = true
  try {
    await saveUserRoles(selectedUser.value.userId, checkedRoleIds.value)
    ElMessage.success('用户角色授权已保存')
  } finally { saving.value = false }
}

onMounted(() => { loadUsers(); loadRoles() })
</script>

<style scoped>
.grant-grid { display: grid; grid-template-columns: 1fr 1.35fr; gap: 20px; }
.grant-table { margin-top: 14px; }
.selected-title { margin-bottom: 16px; font-weight: 700; color: #0f172a; }
.role-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
:deep(.el-checkbox.is-bordered) { margin-right: 0; height: auto; padding: 12px 14px; }
</style>
