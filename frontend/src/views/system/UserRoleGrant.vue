<template>
  <div>
    <div class="page-header">
      <div>
        <h1 class="page-title">用户角色授权</h1>
        <p class="page-subtitle">为用户分配一个或多个角色，角色变更会在用户重新登录后刷新到访问令牌</p>
      </div>
      <div class="header-actions">
        <el-button icon="Refresh" :disabled="!selectedUser" @click="resetSelection">重置</el-button>
        <el-button type="primary" icon="Check" :disabled="!selectedUser || !hasChanges" :loading="saving" @click="handleSave">
          保存授权
        </el-button>
      </div>
    </div>

    <div class="grant-layout">
      <el-card class="page-card user-card">
        <template #header>
          <div class="card-header">
            <span>用户</span>
            <el-tag type="info" effect="plain">{{ total }} 人</el-tag>
          </div>
        </template>

        <div class="user-toolbar">
          <el-input v-model="query.keyword" clearable placeholder="搜索账号、姓名、邮箱" prefix-icon="Search" @keyup.enter="loadUsers" />
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </div>

        <el-table
          v-loading="userLoading"
          :data="users"
          class="user-table"
          highlight-current-row
          row-key="userId"
          @current-change="selectUser"
        >
          <el-table-column label="用户" min-width="210">
            <template #default="{ row }">
              <div class="user-cell">
                <el-avatar :size="34" :src="row.avatarPath || defaultAvatar">{{ avatarText(row) }}</el-avatar>
                <div>
                  <strong>{{ row.realName || row.username }}</strong>
                  <small>{{ row.username }}</small>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="82">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="pagination"
          layout="prev, pager, next"
          :total="total"
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          @change="loadUsers"
        />
      </el-card>

      <el-card class="page-card role-card">
        <template #header>
          <div class="card-header">
            <span>角色分配</span>
            <el-tag v-if="selectedUser" type="primary" effect="plain">{{ selectedUser.realName || selectedUser.username }}</el-tag>
          </div>
        </template>

        <el-empty v-if="!selectedUser" description="请选择左侧用户后分配角色" />
        <template v-else>
          <div class="selected-user">
            <el-avatar :size="44" :src="selectedUser.avatarPath || defaultAvatar">{{ avatarText(selectedUser) }}</el-avatar>
            <div>
              <strong>{{ selectedUser.realName || selectedUser.username }}</strong>
              <span>{{ selectedUser.username }} · {{ selectedUser.email || '未填写邮箱' }}</span>
            </div>
          </div>

          <div class="assignment-summary">
            <span>已选 {{ checkedRoleIds.length }} / {{ roles.length }} 个角色</span>
            <el-tag v-if="hasChanges" type="warning" effect="plain">有未保存更改</el-tag>
            <el-tag v-else type="success" effect="plain">已同步</el-tag>
          </div>

          <el-input v-model="roleKeyword" clearable class="role-search" placeholder="搜索角色名称或编码" prefix-icon="Search" />

          <el-checkbox-group v-model="checkedRoleIds" class="role-list">
            <el-checkbox
              v-for="role in filteredRoles"
              :key="roleId(role)"
              class="role-option"
              :class="{ disabled: !isRoleEnabled(role) }"
              :label="roleId(role)"
              :disabled="!isRoleEnabled(role)"
              border
            >
              <div class="role-option-content">
                <div>
                  <strong>{{ roleName(role) }}</strong>
                  <small>{{ roleCode(role) }}</small>
                </div>
                <el-tag size="small" :type="isRoleEnabled(role) ? roleTag(role) : 'info'" effect="plain">
                  {{ isRoleEnabled(role) ? roleTypeLabel(role) : '停用' }}
                </el-tag>
              </div>
            </el-checkbox>
          </el-checkbox-group>

          <el-empty v-if="!filteredRoles.length" description="没有匹配的角色" />

          <div class="selected-tags">
            <span>当前角色</span>
            <div>
              <el-tag
                v-for="role in selectedRoles"
                :key="roleId(role)"
                closable
                :type="roleTag(role)"
                @close="removeRole(role)"
              >
                {{ roleName(role) }}
              </el-tag>
              <el-tag v-if="!selectedRoles.length" type="info" effect="plain">未分配角色</el-tag>
            </div>
          </div>
        </template>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchRoles, fetchUserRoleIds, fetchUsers, saveUserRoles } from '@/api/system'
import type { RoleItem, UserItem } from '@/types/system'
import defaultAvatar from '@/assets/default-avatar.webp'

type RoleId = string
type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

const query = reactive({ keyword: '', current: 1, size: 10 })
const roleKeyword = ref('')
const userLoading = ref(false)
const roleLoading = ref(false)
const saving = ref(false)
const users = ref<UserItem[]>([])
const roles = ref<RoleItem[]>([])
const total = ref(0)
const selectedUser = ref<UserItem | null>(null)
const checkedRoleIds = ref<RoleId[]>([])
const originalRoleIds = ref<RoleId[]>([])

const filteredRoles = computed(() => {
  const keyword = roleKeyword.value.trim().toLowerCase()
  if (!keyword) return roles.value
  return roles.value.filter((role) => [roleName(role), roleCode(role)].some((item) => item.toLowerCase().includes(keyword)))
})

const selectedRoles = computed(() => {
  const selected = new Set(checkedRoleIds.value)
  return roles.value.filter((role) => selected.has(roleId(role)))
})

const hasChanges = computed(() => {
  if (!selectedUser.value) return false
  return normalizeIds(checkedRoleIds.value).join(',') !== normalizeIds(originalRoleIds.value).join(',')
})

function roleId(role: RoleItem): RoleId {
  return String(role.id)
}

function roleCode(role: RoleItem) {
  return role.code || role.roleCode || ''
}

function roleName(role: RoleItem) {
  return role.name || role.roleName || roleCode(role)
}

function isRoleEnabled(role: RoleItem) {
  return role.enabled !== false
}

function roleTag(role: RoleItem): TagType {
  const code = roleCode(role)
  if (code === 'SUPER_ADMIN') return 'danger'
  if (code === 'ADMIN') return 'warning'
  if (code === 'USER') return 'success'
  return 'primary'
}

function roleTypeLabel(role: RoleItem) {
  const code = roleCode(role)
  if (code === 'SUPER_ADMIN') return '全局'
  if (code === 'ADMIN') return '管理'
  if (code === 'USER') return '基础'
  return '自定义'
}

function avatarText(user: UserItem) {
  return (user.realName || user.username || 'U').slice(0, 1).toUpperCase()
}

function normalizeIds(ids: RoleId[]) {
  return [...new Set(ids.map(String))].sort()
}

function toApiIds(ids: RoleId[]) {
  return normalizeIds(ids).map((id) => (/^\d+$/.test(id) ? Number(id) : id))
}

async function loadUsers() {
  userLoading.value = true
  try {
    const result = await fetchUsers(query)
    users.value = result.records
    total.value = result.total
  } finally {
    userLoading.value = false
  }
}

async function loadRoles() {
  roleLoading.value = true
  try {
    roles.value = (await fetchRoles({ current: 1, size: 200 })).records
  } finally {
    roleLoading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.current = 1
  loadUsers()
}

async function selectUser(row?: UserItem) {
  if (!row) return
  selectedUser.value = row
  roleKeyword.value = ''
  const ids = (await fetchUserRoleIds(row.userId)).map(String)
  checkedRoleIds.value = normalizeIds(ids)
  originalRoleIds.value = normalizeIds(ids)
}

function resetSelection() {
  checkedRoleIds.value = normalizeIds(originalRoleIds.value).filter(isEnabledRoleId)
}

function removeRole(role: RoleItem) {
  if (!isRoleEnabled(role)) return
  const target = roleId(role)
  checkedRoleIds.value = checkedRoleIds.value.filter((id) => id !== target)
}

async function handleSave() {
  if (!selectedUser.value) return
  saving.value = true
  try {
    const enabledIds = normalizeIds(checkedRoleIds.value).filter(isEnabledRoleId)
    await saveUserRoles(selectedUser.value.userId, toApiIds(enabledIds))
    checkedRoleIds.value = enabledIds
    originalRoleIds.value = enabledIds
    ElMessage.success('用户角色授权已保存')
  } finally {
    saving.value = false
  }
}

function isEnabledRoleId(id: RoleId) {
  const role = roles.value.find((item) => roleId(item) === id)
  return !role || isRoleEnabled(role)
}

onMounted(() => {
  loadUsers()
  loadRoles()
})
</script>

<style scoped>
.header-actions,
.card-header,
.user-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.grant-layout {
  display: grid;
  grid-template-columns: 420px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.user-card,
.role-card {
  min-height: calc(100vh - 156px);
}

.user-toolbar {
  margin-bottom: 14px;
}

.user-table :deep(.el-table__row) {
  cursor: pointer;
}

.user-cell,
.selected-user {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-cell strong,
.user-cell small,
.selected-user strong,
.selected-user span,
.role-option-content strong,
.role-option-content small {
  display: block;
}

.user-cell strong,
.selected-user strong,
.role-option-content strong {
  color: #0f172a;
}

.user-cell small,
.selected-user span,
.role-option-content small {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.selected-user {
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.assignment-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 16px 0 12px;
  color: #64748b;
  font-size: 13px;
}

.role-search {
  max-width: 420px;
  margin-bottom: 14px;
}

.role-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.role-option {
  margin-right: 0;
  height: auto;
  padding: 12px 14px;
}

.role-option.disabled {
  opacity: 0.55;
}

.role-option :deep(.el-checkbox__label) {
  min-width: 0;
  flex: 1;
}

.role-option-content {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.selected-tags {
  display: grid;
  gap: 10px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
  color: #64748b;
  font-size: 13px;
}

.selected-tags div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
