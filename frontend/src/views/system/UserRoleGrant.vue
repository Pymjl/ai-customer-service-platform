<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h1 class="page-title">用户角色授权</h1>
        <p class="page-subtitle">为用户分配一个或多个角色，角色变更会在用户重新登录后刷新到访问令牌</p>
      </div>
      <div class="header-actions">
        <n-button :disabled="!selectedUser" @click="resetSelection">
          <template #icon><n-icon :component="ArrowSync24Regular" /></template>
          重置
        </n-button>
        <n-button type="primary" :disabled="!selectedUser || !hasChanges" :loading="saving" @click="handleSave">
          <template #icon><n-icon :component="Save24Regular" /></template>
          保存授权
        </n-button>
      </div>
    </div>

    <div class="grant-layout">
      <section class="page-card user-card">
        <div class="card-header">
          <span>用户</span>
          <span class="status-pill">{{ total }} 人</span>
        </div>

        <div class="user-toolbar">
          <n-input v-model:value="query.keyword" clearable placeholder="搜索账号、姓名、邮箱" @keyup.enter="loadUsers">
            <template #prefix><n-icon :component="Search24Regular" /></template>
          </n-input>
          <n-button @click="resetQuery">重置</n-button>
        </div>

        <n-spin :show="userLoading">
          <div class="user-list">
            <button
              v-for="user in users"
              :key="user.userId"
              type="button"
              class="user-row"
              :class="{ active: selectedUser?.userId === user.userId }"
              @click="selectUser(user)"
            >
              <n-avatar round :size="34" :src="resolveAvatarUrl(user.avatarPath)" :fallback-src="defaultAvatar">{{ avatarText(user) }}</n-avatar>
              <span>
                <strong>{{ user.realName || user.username }}</strong>
                <small>{{ user.username }}</small>
              </span>
              <span class="status-pill" :class="user.status === 1 ? 'success' : ''">{{ user.status === 1 ? '启用' : '停用' }}</span>
            </button>
          </div>
          <n-empty v-if="!users.length" description="暂无用户" />
        </n-spin>

        <n-pagination class="pagination" v-model:page="query.current" v-model:page-size="query.size" :item-count="total" @update:page="loadUsers" />
      </section>

      <section class="page-card role-card">
        <div class="card-header">
          <span>角色分配</span>
          <span v-if="selectedUser" class="status-pill primary">{{ selectedUser.realName || selectedUser.username }}</span>
        </div>

        <n-empty v-if="!selectedUser" description="请选择左侧用户后分配角色" />
        <template v-else>
          <div class="selected-user">
            <n-avatar round :size="44" :src="resolveAvatarUrl(selectedUser.avatarPath)" :fallback-src="defaultAvatar">{{ avatarText(selectedUser) }}</n-avatar>
            <div>
              <strong>{{ selectedUser.realName || selectedUser.username }}</strong>
              <span>{{ selectedUser.username }} · {{ selectedUser.email || '未填写邮箱' }}</span>
            </div>
          </div>

          <div class="assignment-summary">
            <span>已选 {{ checkedRoleIds.length }} / {{ roles.length }} 个角色</span>
            <span class="status-pill" :class="hasChanges ? 'warning' : 'success'">{{ hasChanges ? '有未保存更改' : '已同步' }}</span>
          </div>

          <n-input v-model:value="roleKeyword" clearable class="role-search" placeholder="搜索角色名称或编码">
            <template #prefix><n-icon :component="Search24Regular" /></template>
          </n-input>

          <n-spin :show="roleLoading">
            <n-checkbox-group v-model:value="checkedRoleIds" class="role-list">
              <n-checkbox
                v-for="role in filteredRoles"
                :key="roleId(role)"
                class="role-option"
                :value="roleId(role)"
                :disabled="!isRoleEnabled(role)"
              >
                <span class="role-option-content">
                  <span>
                    <strong>{{ roleName(role) }}</strong>
                    <small>{{ roleCode(role) }}</small>
                  </span>
                  <span class="status-pill" :class="isRoleEnabled(role) ? roleTag(role) : ''">
                    {{ isRoleEnabled(role) ? roleTypeLabel(role) : '停用' }}
                  </span>
                </span>
              </n-checkbox>
            </n-checkbox-group>
            <n-empty v-if="!filteredRoles.length" description="没有匹配的角色" />
          </n-spin>

          <div class="selected-tags">
            <span>当前角色</span>
            <div>
              <n-tag v-for="role in selectedRoles" :key="roleId(role)" closable @close="removeRole(role)">{{ roleName(role) }}</n-tag>
              <span v-if="!selectedRoles.length" class="status-pill">未分配角色</span>
            </div>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowSync24Regular, Save24Regular, Search24Regular } from '@vicons/fluent'
import { fetchRoles, fetchUserRoleIds, fetchUsers, saveUserRoles } from '@/api/system'
import type { RoleItem, UserItem } from '@/types/system'
import { defaultAvatar, resolveAvatarUrl } from '@/utils/avatar'
import { message } from '@/utils/feedback'

type RoleId = string
type TagType = 'primary' | 'success' | 'warning' | 'danger' | ''

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
    message.success('用户角色授权已保存')
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

.card-header {
  margin-bottom: 16px;
  font-weight: 750;
}

.user-toolbar {
  margin-bottom: 14px;
}

.user-list {
  display: grid;
  gap: 8px;
}

.user-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  width: 100%;
  padding: 12px;
  text-align: left;
  border: 1px solid var(--border-subtle);
  border-radius: 14px;
  background: var(--bg-surface);
  cursor: pointer;
}

.user-row.active,
.user-row:hover {
  border-color: var(--accent-primary);
  background: var(--glass-surface-hover);
}

.user-row strong,
.user-row small,
.selected-user strong,
.selected-user span,
.role-option-content strong,
.role-option-content small {
  display: block;
}

.user-row small,
.selected-user span,
.role-option-content small {
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 12px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.selected-user {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--border-subtle);
  border-radius: 14px;
  background: var(--bg-surface-muted);
}

.assignment-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 16px 0 12px;
  color: var(--text-muted);
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
  min-width: 0;
  margin: 0;
  padding: 12px 14px;
  border: 1px solid var(--border-subtle);
  border-radius: 14px;
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
  border-top: 1px solid var(--border-subtle);
  color: var(--text-muted);
  font-size: 13px;
}

.selected-tags div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 1100px) {
  .grant-layout {
    grid-template-columns: 1fr;
  }
}
</style>
