<template>
  <div>
    <div class="page-header">
      <div>
        <h1 class="page-title">角色资源授权</h1>
        <p class="page-subtitle">按角色分配 API 访问权限，目录节点仅用于快速批量勾选</p>
      </div>
      <el-button type="primary" icon="Check" :disabled="!selectedRole || !isRoleEnabled(selectedRole)" :loading="saving" @click="handleSave">保存授权</el-button>
    </div>

    <div class="grant-layout">
      <el-card class="page-card role-card">
        <template #header>角色</template>
        <el-table
          v-loading="roleLoading"
          :data="roles"
          class="role-table"
          highlight-current-row
          :row-class-name="roleRowClassName"
          @current-change="selectRole"
        >
          <el-table-column label="角色" min-width="180">
            <template #default="{ row }">
              <div class="role-cell">
                <strong>{{ roleName(row) }}</strong>
                <small>{{ roleCode(row) }}</small>
                <el-tag v-if="!isRoleEnabled(row)" size="small" type="info" effect="plain">停用</el-tag>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <el-alert
          v-if="selectedRole"
          class="role-hint"
          type="info"
          :closable="false"
          show-icon
          :title="roleHint(selectedRole)"
        />
      </el-card>

      <el-card class="page-card permission-card">
        <template #header>
          <div class="permission-header">
            <span>资源权限</span>
            <el-tag v-if="selectedRole" type="primary" effect="plain">{{ roleName(selectedRole) }}</el-tag>
          </div>
        </template>

        <el-empty v-if="!selectedRole" description="请选择一个角色后配置权限" />
        <template v-else>
          <div class="permission-toolbar">
            <el-input v-model="filterText" clearable placeholder="搜索服务、包名、Controller 或 API" prefix-icon="Search" />
            <el-button-group>
              <el-button icon="CircleCheck" @click="checkAllApis">全选接口</el-button>
              <el-button icon="Remove" @click="clearChecked">清空</el-button>
            </el-button-group>
          </div>
          <div class="permission-summary">
            <span>已选 {{ checkedApiCount }} / {{ apiIds.length }} 个接口</span>
            <el-progress :percentage="checkedPercent" :show-text="false" />
          </div>
          <el-tree
            :key="treeRenderKey"
            ref="treeRef"
            v-loading="resourceLoading"
            class="permission-tree"
            :data="resources"
            node-key="id"
            show-checkbox
            default-expand-all
            :props="treeProps"
            :filter-node-method="filterNode"
            @check="updateCheckedCount"
          >
            <template #default="{ data }">
              <div class="resource-node" :class="{ api: isApiNode(data), disabled: isDisabledApiNode(data) }">
                <el-icon class="resource-node-icon">
                  <component :is="nodeIcon(data)" />
                </el-icon>
                <span class="resource-node-label">{{ data.label }}</span>
                <el-tag v-if="isApiNode(data)" size="small" :type="methodMeta(data.resource?.httpMethod)">
                  {{ data.resource?.httpMethod || 'ANY' }}
                </el-tag>
                <el-tag v-if="isDisabledApiNode(data)" size="small" type="info" effect="plain">停用</el-tag>
                <small v-if="isApiNode(data)">{{ data.resource?.methodName }}</small>
              </div>
            </template>
          </el-tree>
        </template>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import type { ElTree } from 'element-plus'
import { ElMessage } from 'element-plus'
import { fetchResources, fetchRoleResourceIds, fetchRoles, saveRoleResources } from '@/api/system'
import type { ResourceTreeNode, RoleItem } from '@/types/system'

type TreeKey = string | number
type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

const treeProps = { label: 'label', children: 'children', disabled: 'disabled' }
const roleLoading = ref(false)
const resourceLoading = ref(false)
const saving = ref(false)
const filterText = ref('')
const roles = ref<RoleItem[]>([])
const resources = ref<ResourceTreeNode[]>([])
const selectedRole = ref<RoleItem | null>(null)
const checkedApiCount = ref(0)
const treeRenderKey = ref(0)
const treeRef = ref<InstanceType<typeof ElTree>>()

const apiIds = computed(() => collectEnabledApiIds(resources.value))
const checkedPercent = computed(() => apiIds.value.length ? Math.round((checkedApiCount.value / apiIds.value.length) * 100) : 0)

watch(filterText, (value) => {
  treeRef.value?.filter(value)
})

function roleCode(role: RoleItem) {
  return role.code || role.roleCode || ''
}

function roleName(role: RoleItem) {
  return role.name || role.roleName || roleCode(role)
}

function isRoleEnabled(role?: RoleItem | null) {
  return role?.enabled !== false
}

function roleRowClassName({ row }: { row: RoleItem }) {
  return isRoleEnabled(row) ? '' : 'disabled-row'
}

function roleHint(role: RoleItem) {
  const code = roleCode(role)
  if (code === 'SUPER_ADMIN') return '超级管理员默认拥有全部权限，授权树用于审计和展示。'
  if (code === 'ADMIN') return '管理员拥有日常管理权限，不授予删除和权限分配等敏感操作。'
  if (code === 'USER') return '普通用户仅允许访问智能客服和自己的用户资料。'
  return '自定义角色按下方勾选的 API 资源进行鉴权。'
}

function isApiNode(node?: ResourceTreeNode | null) {
  return node?.type?.toString().toLowerCase() === 'api'
}

function isDisabledApiNode(node?: ResourceTreeNode | null) {
  return isApiNode(node) && node?.resource?.enabled === false
}

function nodeIcon(node: ResourceTreeNode) {
  const type = node.type.toString().toLowerCase()
  if (type === 'api') return 'Link'
  if (type === 'controller') return 'Document'
  if (type === 'service') return 'Box'
  return 'Folder'
}

function methodMeta(method?: string | null): TagType {
  const map: Record<string, TagType> = {
    GET: 'success',
    POST: 'primary',
    PUT: 'warning',
    PATCH: 'warning',
    DELETE: 'danger'
  }
  return map[(method || '').toUpperCase()] || 'info'
}

function filterNode(value: string, data: ResourceTreeNode) {
  if (!value) return true
  const keyword = value.toLowerCase()
  return [
    data.label,
    data.resource?.path,
    data.resource?.httpMethod,
    data.resource?.resourceCode,
    data.resource?.controllerName,
    data.resource?.methodName
  ].some((item) => item?.toLowerCase().includes(keyword))
}

function collectEnabledApiIds(nodes: ResourceTreeNode[]) {
  const ids: TreeKey[] = []
  const walk = (items: ResourceTreeNode[]) => {
    items.forEach((item) => {
      if (isApiNode(item) && !isDisabledApiNode(item)) ids.push(item.id)
      if (item.children?.length) walk(item.children)
    })
  }
  walk(nodes)
  return ids
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
    resources.value = markDisabledNodes(await fetchResources())
  } finally {
    resourceLoading.value = false
  }
}

async function selectRole(row?: RoleItem) {
  if (!row) return
  if (!isRoleEnabled(row)) {
    selectedRole.value = null
    clearChecked()
    ElMessage.warning('停用角色不参与授权')
    return
  }
  selectedRole.value = row
  const ids = await fetchRoleResourceIds(row.id)
  await nextTick()
  const enabledApiIdSet = new Set(apiIds.value.map(String))
  treeRef.value?.setCheckedKeys(ids.map(String).filter((id) => enabledApiIdSet.has(id)), false)
  updateCheckedCount()
}

function checkAllApis() {
  treeRef.value?.setCheckedKeys(apiIds.value, false)
  updateCheckedCount()
}

function clearChecked() {
  treeRef.value?.setCheckedKeys([], false)
  updateCheckedCount()
}

function updateCheckedCount() {
  const selected = new Set(treeRef.value?.getCheckedKeys(false) || [])
  checkedApiCount.value = apiIds.value.filter((id) => selected.has(id)).length
}

async function handleSave() {
  if (!selectedRole.value) return
  saving.value = true
  try {
    const apiIdSet = new Set(apiIds.value)
    const checked = treeRef.value?.getCheckedKeys(false) || []
    const resourceIds = checked.filter((id) => apiIdSet.has(id)).map((id) => Number(id))
    await saveRoleResources(selectedRole.value.id, resourceIds)
    ElMessage.success('角色资源授权已保存')
  } finally {
    saving.value = false
  }
}

function markDisabledNodes(nodes: ResourceTreeNode[]): ResourceTreeNode[] {
  return nodes.map((node) => {
    const children = node.children ? markDisabledNodes(node.children) : undefined
    return {
      ...node,
      children,
      disabled: isApiNode(node) && node.resource?.enabled === false
    }
  })
}

onMounted(async () => {
  await Promise.all([loadRoles(), loadResources()])
  treeRenderKey.value += 1
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

.role-table :deep(.el-table__row) {
  cursor: pointer;
}

.role-table :deep(.disabled-row) {
  color: #94a3b8;
  cursor: not-allowed;
}

.role-cell {
  display: grid;
  gap: 4px;
}

.role-cell strong {
  color: #0f172a;
}

.role-cell small,
.resource-node small {
  color: #94a3b8;
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
  margin-bottom: 14px;
}

.permission-toolbar .el-input {
  max-width: 460px;
}

.permission-summary {
  display: grid;
  grid-template-columns: auto minmax(120px, 240px);
  gap: 14px;
  align-items: center;
  margin-bottom: 14px;
  color: #64748b;
  font-size: 13px;
}

.permission-tree {
  --el-tree-node-hover-bg-color: #f1f5f9;
}

.permission-tree :deep(.el-tree-node__content) {
  height: 38px;
  border-radius: 8px;
}

.resource-node {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.resource-node.api .resource-node-label {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}

.resource-node.disabled {
  color: #94a3b8;
}

.resource-node-icon {
  color: #64748b;
}

.resource-node.disabled .resource-node-icon {
  color: #cbd5e1;
}

.resource-node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
