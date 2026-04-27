<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h1 class="page-title">角色资源授权</h1>
        <p class="page-subtitle">按角色分配 API 访问权限，目录节点仅用于快速批量勾选</p>
      </div>
      <n-button type="primary" :disabled="!selectedRole || !isRoleEnabled(selectedRole)" :loading="saving" @click="handleSave">
        <template #icon><n-icon :component="Save24Regular" /></template>
        保存授权
      </n-button>
    </div>

    <div class="grant-layout">
      <section class="page-card role-card">
        <h2 class="card-title">角色</h2>
        <n-spin :show="roleLoading">
          <div class="role-list-panel">
            <button
              v-for="role in roles"
              :key="role.id"
              type="button"
              class="role-row"
              :class="{ active: selectedRole?.id === role.id, disabled: !isRoleEnabled(role) }"
              @click="selectRole(role)"
            >
              <span>
                <strong>{{ roleName(role) }}</strong>
                <small>{{ roleCode(role) }}</small>
              </span>
              <span v-if="!isRoleEnabled(role)" class="status-pill">停用</span>
            </button>
          </div>
        </n-spin>
        <n-alert v-if="selectedRole" class="role-hint" type="info" :bordered="false">
          {{ roleHint(selectedRole) }}
        </n-alert>
      </section>

      <section class="page-card permission-card">
        <div class="permission-header">
          <h2 class="card-title">资源权限</h2>
          <span v-if="selectedRole" class="status-pill primary">{{ roleName(selectedRole) }}</span>
        </div>

        <n-empty v-if="!selectedRole" description="请选择一个角色后配置权限" />
        <template v-else>
          <div class="permission-toolbar">
            <n-input v-model:value="filterText" clearable placeholder="搜索服务或接口路径">
              <template #prefix><n-icon :component="Search24Regular" /></template>
            </n-input>
            <n-button-group>
              <n-button @click="checkAllApis">
                <template #icon><n-icon :component="CheckmarkCircle24Regular" /></template>
                全选接口
              </n-button>
              <n-button @click="clearChecked">
                <template #icon><n-icon :component="Subtract24Regular" /></template>
                清空
              </n-button>
            </n-button-group>
          </div>
          <div class="permission-summary">
            <span>当前视图匹配 {{ matchedApiCount }} 个接口，已选 {{ checkedApiCount }} / {{ apiIds.length }} 个接口</span>
            <n-progress type="line" :percentage="checkedPercent" :show-indicator="false" />
          </div>
          <n-spin :show="resourceLoading">
            <n-tree
              block-line
              cascade
              checkable
              :data="resources"
              key-field="id"
              label-field="label"
              children-field="children"
              disabled-field="disabled"
              :pattern="filterText"
              :filter="resourceTreeFilter"
              :expanded-keys="expandedKeys"
              :checked-keys="checkedKeys"
              :render-label="renderResourceLabel"
              :override-default-node-click-behavior="overrideNodeClickBehavior"
              @update:checked-keys="handleCheckedKeys"
              @update:expanded-keys="handleExpandedKeys"
            />
          </n-spin>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, h, nextTick, onMounted, ref } from 'vue'
import type { Key } from 'treemate'
import type { TreeOption, TreeOverrideNodeClickBehavior } from 'naive-ui'
import { NIcon } from 'naive-ui'
import { CheckmarkCircle24Regular, Cloud24Regular, Folder24Regular, Link24Regular, Save24Regular, Search24Regular, Server24Regular, Subtract24Regular } from '@vicons/fluent'
import { fetchResources, fetchRoleResourceIds, fetchRoles, saveRoleResources } from '@/api/system'
import type { RoleItem } from '@/types/system'
import { message } from '@/utils/feedback'
import {
  apiDisplayMethod,
  apiDisplayPath,
  buildResourceDisplayTree,
  collectEnabledApiIds,
  countMatchedApis,
  markDisabledResourceNodes,
  resourceTreeFilter,
  type ResourceDisplayNode
} from '@/utils/resourceTree'

const roleLoading = ref(false)
const resourceLoading = ref(false)
const saving = ref(false)
const filterText = ref('')
const roles = ref<RoleItem[]>([])
const resources = ref<ResourceDisplayNode[]>([])
const selectedRole = ref<RoleItem | null>(null)
const checkedApiCount = ref(0)
const checkedKeys = ref<Key[]>([])
const expandedKeys = ref<Key[]>([])

const apiIds = computed(() => collectEnabledApiIds(resources.value))
const checkedPercent = computed(() => apiIds.value.length ? Math.round((checkedApiCount.value / apiIds.value.length) * 100) : 0)
const matchedApiCount = computed(() => countMatchedApis(resources.value, filterText.value))

function roleCode(role: RoleItem) {
  return role.code || role.roleCode || ''
}

function roleName(role: RoleItem) {
  return role.name || role.roleName || roleCode(role)
}

function isRoleEnabled(role?: RoleItem | null) {
  return role?.enabled !== false
}

function roleHint(role: RoleItem) {
  const code = roleCode(role)
  if (code === 'SUPER_ADMIN') return '超级管理员默认拥有全部权限，授权树用于审计和展示。'
  if (code === 'ADMIN') return '管理员拥有日常管理权限，不授予删除和权限分配等敏感操作。'
  if (code === 'USER') return '普通用户仅允许访问智能客服和自己的用户资料。'
  return '自定义角色按下方勾选的 API 资源进行鉴权。'
}

async function loadRoles() {
  roleLoading.value = true
  try {
    roles.value = (await fetchRoles({ current: 1, size: 200 })).records
  } finally {
    roleLoading.value = false
  }
}

async function loadResources() {
  resourceLoading.value = true
  try {
    resources.value = markDisabledResourceNodes(buildResourceDisplayTree(await fetchResources()))
    expandedKeys.value = []
  } finally {
    resourceLoading.value = false
  }
}

async function selectRole(row?: RoleItem) {
  if (!row) return
  if (!isRoleEnabled(row)) {
    selectedRole.value = null
    clearChecked()
    message.warning('停用角色不参与授权')
    return
  }
  selectedRole.value = row
  const ids = await fetchRoleResourceIds(row.id)
  await nextTick()
  const enabledApiIdSet = new Set(apiIds.value.map(String))
  checkedKeys.value = ids.map(String).filter((id) => enabledApiIdSet.has(id))
  updateCheckedCount()
}

function checkAllApis() {
  checkedKeys.value = [...apiIds.value]
  updateCheckedCount()
}

function clearChecked() {
  checkedKeys.value = []
  updateCheckedCount()
}

function handleCheckedKeys(keys: Key[]) {
  checkedKeys.value = keys
  updateCheckedCount()
}

function handleExpandedKeys(keys: Key[]) {
  expandedKeys.value = keys
}

const overrideNodeClickBehavior: TreeOverrideNodeClickBehavior = ({ option }) => {
  const node = option as unknown as ResourceDisplayNode
  return node.type.toString().toLowerCase() === 'api' ? 'none' : 'toggleExpand'
}

function updateCheckedCount() {
  const selected = new Set(checkedKeys.value.map(String))
  checkedApiCount.value = apiIds.value.filter((id) => selected.has(String(id))).length
}

async function handleSave() {
  if (!selectedRole.value) return
  saving.value = true
  try {
    const apiIdSet = new Set(apiIds.value.map(String))
    const resourceIds = checkedKeys.value.filter((id) => apiIdSet.has(String(id))).map((id) => Number(id))
    await saveRoleResources(selectedRole.value.id, resourceIds)
    message.success('角色资源授权已保存')
  } finally {
    saving.value = false
  }
}

function renderResourceLabel({ option }: { option: TreeOption }) {
  const node = option as unknown as ResourceDisplayNode
  const type = node.type.toString().toLowerCase()
  if (type === 'service') return renderServiceLabel(node)
  if (type === 'controller') return renderControllerLabel(node)
  if (type === 'api') return renderApiLabel(node)
  return h('span', { class: 'resource-node-label' }, node.label)
}

function renderServiceLabel(node: ResourceDisplayNode) {
  return h('span', { class: ['resource-node-label', 'resource-node-service'] }, [
    h('span', { class: ['resource-node-icon', 'service-icon', serviceTone(node.label)], title: '后端服务', 'aria-hidden': 'true' }, [
      h(NIcon, { component: node.label.includes('gateway') ? Cloud24Regular : Server24Regular })
    ]),
    h('span', { class: 'resource-node-main' }, [
      h('strong', node.label)
    ])
  ])
}

function renderControllerLabel(node: ResourceDisplayNode) {
  return h('span', { class: ['resource-node-label', 'resource-node-controller'] }, [
    h('span', { class: 'resource-node-icon controller-icon', title: 'Controller', 'aria-hidden': 'true' }, [
      h(NIcon, { component: Folder24Regular })
    ]),
    h('span', { class: 'resource-node-main' }, [
      h('strong', node.label)
    ])
  ])
}

function renderApiLabel(node: ResourceDisplayNode) {
  const method = apiDisplayMethod(node)
  return h('span', { class: ['resource-node-label', 'resource-node-api'] }, [
    h('span', { class: ['method-badge', methodClass(method)], title: `HTTP ${method}`, 'aria-label': `HTTP 方法 ${method}` }, method),
    h('span', { class: 'api-path' }, apiDisplayPath(node)),
    node.resource?.enabled === false ? h('span', { class: 'status-pill' }, '停用') : null
  ])
}

function methodClass(method?: string | null) {
  const key = (method || 'ANY').toLowerCase()
  if (key === 'get') return 'method-get'
  if (key === 'post') return 'method-post'
  if (key === 'put' || key === 'patch') return 'method-update'
  if (key === 'delete') return 'method-delete'
  return 'method-any'
}

function serviceTone(label?: string) {
  const tones = ['service-tone-blue', 'service-tone-green', 'service-tone-violet', 'service-tone-orange']
  const sum = String(label || '').split('').reduce((total, char) => total + char.charCodeAt(0), 0)
  return tones[sum % tones.length]
}

onMounted(async () => {
  await Promise.all([loadRoles(), loadResources()])
})
</script>

<style scoped>
.grant-layout {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.role-card,
.permission-card {
  min-height: calc(100vh - 156px);
}

.card-title {
  margin: 0 0 16px;
  font-size: 16px;
}

.role-list-panel {
  display: grid;
  gap: 8px;
}

.role-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  padding: 12px;
  text-align: left;
  border: 1px solid var(--border-subtle);
  border-radius: 14px;
  background: var(--bg-surface);
  cursor: pointer;
}

.role-row.active,
.role-row:hover {
  border-color: var(--accent-primary);
  background: var(--glass-surface-hover);
}

.role-row.disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.role-row strong,
.role-row small {
  display: block;
}

.role-row small {
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 12px;
}

.role-hint {
  margin-top: 16px;
}

.permission-header,
.permission-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.permission-toolbar {
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.permission-toolbar :deep(.n-input) {
  max-width: 460px;
}

.permission-summary {
  display: grid;
  grid-template-columns: auto minmax(120px, 240px);
  gap: 14px;
  align-items: center;
  margin-bottom: 14px;
  color: var(--text-muted);
  font-size: 13px;
}

.permission-card :deep(.n-tree-node-content) {
  min-height: 38px;
  cursor: pointer;
}

.permission-card :deep(.n-tree-node-switcher) {
  display: none;
}

.resource-node-label {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.resource-node-main {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.resource-node-main strong,
.api-path {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-node-main small {
  color: var(--text-muted);
  font-size: 12px;
}

.resource-node-service .resource-node-main strong {
  font-size: 15px;
  font-weight: 850;
}

.resource-node-controller .resource-node-main strong {
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 760;
}

.resource-node-api {
  gap: 8px;
  color: var(--text-secondary);
  font-size: 13px;
}

.resource-node-icon {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  border-radius: 9px;
}

.service-icon {
  color: var(--text-on-accent);
}

.service-tone-blue {
  background: #2563eb;
}

.service-tone-green {
  background: #059669;
}

.service-tone-violet {
  background: #7c3aed;
}

.service-tone-orange {
  background: #ea580c;
}

.controller-icon {
  color: #7c3aed;
  background: rgba(124, 58, 237, 0.1);
}

.method-badge {
  min-width: 54px;
  display: inline-flex;
  justify-content: center;
  padding: 3px 7px;
  color: #fff;
  border-radius: 7px;
  font-size: 11px;
  font-weight: 850;
  line-height: 1.2;
}

.method-get {
  background: #2563eb;
}

.method-post {
  background: #059669;
}

.method-update {
  background: #ea580c;
}

.method-delete {
  background: #dc2626;
}

.method-any {
  background: #64748b;
}

@media (max-width: 1000px) {
  .grant-layout {
    grid-template-columns: 1fr;
  }
}
</style>
