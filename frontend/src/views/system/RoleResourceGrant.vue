<template>
  <div>
    <div class="page-header">
      <div>
        <h1 class="page-title">角色资源授权</h1>
        <p class="page-subtitle">为角色分配菜单、按钮、接口资源权限</p>
      </div>
      <el-button type="primary" :disabled="!selectedRole" :loading="saving" @click="handleSave">保存授权</el-button>
    </div>
    <div class="grant-grid">
      <el-card class="page-card">
        <template #header>角色列表</template>
        <el-table v-loading="roleLoading" :data="roles" highlight-current-row @current-change="selectRole">
          <el-table-column prop="code" label="编码" />
          <el-table-column prop="name" label="名称" />
        </el-table>
      </el-card>
      <el-card class="page-card">
        <template #header>资源权限</template>
        <el-alert v-if="!selectedRole" title="请先从左侧选择角色" type="info" show-icon :closable="false" />
        <div v-else>
          <div class="selected-title">当前角色：{{ selectedRole.name }}</div>
          <el-tree
            ref="treeRef"
            :data="resources"
            node-key="id"
            show-checkbox
            default-expand-all
            :props="treeProps"
          >
            <template #default="{ data }">
              <span class="resource-node">
                <span>{{ data.name }}</span>
                <el-tag size="small" effect="plain">{{ resourceTypeLabel(data.type) }}</el-tag>
                <small>{{ data.code }}</small>
              </span>
            </template>
          </el-tree>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import type { ElTree } from 'element-plus'
import { ElMessage } from 'element-plus'
import { fetchResources, fetchRoleResourceIds, fetchRoles, saveRoleResources } from '@/api/system'
import type { ResourceItem, ResourceType, RoleItem } from '@/types/system'

const treeProps = { label: 'name', children: 'children' }
const typeLabel: Record<ResourceType, string> = { MENU: '菜单', BUTTON: '按钮', API: '接口' }
function resourceTypeLabel(type: ResourceType) {
  return typeLabel[type]
}

const roleLoading = ref(false)
const saving = ref(false)
const roles = ref<RoleItem[]>([])
const resources = ref<ResourceItem[]>([])
const selectedRole = ref<RoleItem | null>(null)
const treeRef = ref<InstanceType<typeof ElTree>>()

async function loadRoles() {
  roleLoading.value = true
  try { roles.value = (await fetchRoles({ current: 1, size: 200 })).records } finally { roleLoading.value = false }
}

async function loadResources() { resources.value = await fetchResources() }

async function selectRole(row?: RoleItem) {
  if (!row) return
  selectedRole.value = row
  const ids = await fetchRoleResourceIds(row.id)
  await nextTick()
  treeRef.value?.setCheckedKeys(ids, false)
}

async function handleSave() {
  if (!selectedRole.value) return
  saving.value = true
  try {
    const checked = treeRef.value?.getCheckedKeys(false) || []
    const halfChecked = treeRef.value?.getHalfCheckedKeys() || []
    await saveRoleResources(selectedRole.value.id, [...checked, ...halfChecked])
    ElMessage.success('角色资源授权已保存')
  } finally { saving.value = false }
}

onMounted(() => { loadRoles(); loadResources() })
</script>

<style scoped>
.grant-grid { display: grid; grid-template-columns: 0.9fr 1.4fr; gap: 20px; }
.selected-title { margin-bottom: 16px; font-weight: 700; color: #0f172a; }
.resource-node { display: inline-flex; align-items: center; gap: 8px; }
.resource-node small { color: #94a3b8; }
</style>
