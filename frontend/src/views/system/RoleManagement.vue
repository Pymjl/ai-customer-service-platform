<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h1 class="page-title">角色管理</h1>
        <p class="page-subtitle">维护角色编码、名称和启用状态</p>
      </div>
      <n-button type="primary" @click="openCreate">
        <template #icon><n-icon :component="Add24Regular" /></template>
        新增角色
      </n-button>
    </div>
    <section class="page-card">
      <div class="toolbar">
        <n-input v-model:value="query.keyword" clearable placeholder="搜索角色编码 / 名称" @keyup.enter="loadData">
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
            <tr><th>角色编码</th><th>角色名称</th><th>描述</th><th>状态</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="row in roles" :key="row.id">
              <td>{{ roleCode(row) || '-' }}</td>
              <td>{{ roleName(row) || '-' }}</td>
              <td>{{ row.description || '-' }}</td>
              <td><span class="status-pill" :class="isRoleEnabled(row) ? 'success' : ''">{{ isRoleEnabled(row) ? '启用' : '停用' }}</span></td>
              <td class="row-actions">
                <n-button text type="primary" @click="openEdit(row)">编辑</n-button>
                <n-button text type="error" @click="handleDelete(row)">删除</n-button>
              </td>
            </tr>
          </tbody>
        </table>
        <n-empty v-if="!roles.length" description="暂无角色" />
      </n-spin>
      <n-pagination class="pagination" v-model:page="query.current" v-model:page-size="query.size" show-size-picker :item-count="total" @update:page="loadData" @update:page-size="loadData" />
    </section>

    <n-modal v-model:show="dialogVisible" preset="card" :title="editingId ? '编辑角色' : '新增角色'" class="form-modal">
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="90">
        <n-form-item label="角色编码" path="code"><n-input v-model:value="form.code" placeholder="如 ADMIN" /></n-form-item>
        <n-form-item label="角色名称" path="name"><n-input v-model:value="form.name" /></n-form-item>
        <n-form-item label="描述"><n-input v-model:value="form.description" type="textarea" :rows="3" /></n-form-item>
        <n-form-item label="状态"><n-switch v-model:value="form.enabled"><template #checked>启用</template><template #unchecked>停用</template></n-switch></n-form-item>
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
import { onMounted, reactive, ref } from 'vue'
import type { FormInst, FormRules } from 'naive-ui'
import { Add24Regular, ArrowSync24Regular, Search24Regular } from '@vicons/fluent'
import { createRole, deleteRole, fetchRoles, updateRole } from '@/api/system'
import type { RoleItem } from '@/types/system'
import { dialog, message } from '@/utils/feedback'

const loading = ref(false)
const saving = ref(false)
const roles = ref<RoleItem[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const editingId = ref<RoleItem['id'] | null>(null)
const formRef = ref<FormInst | null>(null)
const query = reactive({ keyword: '', current: 1, size: 10 })
const form = reactive({ code: '', name: '', description: '', enabled: true })
const rules: FormRules = {
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const result = await fetchRoles(query)
    roles.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function resetQuery() { query.keyword = ''; query.current = 1; loadData() }
function openCreate() { editingId.value = null; Object.assign(form, { code: '', name: '', description: '', enabled: true }); dialogVisible.value = true }
function roleCode(role: RoleItem) {
  return role.code || role.roleCode || ''
}

function roleName(role: RoleItem) {
  return role.name || role.roleName || roleCode(role)
}

function isRoleEnabled(role: RoleItem) {
  return role.enabled !== false
}

function toRolePayload() {
  return { roleCode: form.code, roleName: form.name, description: form.description, enabled: form.enabled }
}

function openEdit(row: RoleItem) {
  editingId.value = row.id
  Object.assign(form, { code: roleCode(row), name: roleName(row), description: row.description || '', enabled: isRoleEnabled(row) })
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    editingId.value ? await updateRole(editingId.value, toRolePayload()) : await createRole(toRolePayload())
    message.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally { saving.value = false }
}

function handleDelete(row: RoleItem) {
  dialog.warning({
    title: '删除角色',
    content: `确认删除角色 ${roleName(row)}？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await deleteRole(row.id)
      message.success('删除成功')
      loadData()
    }
  })
}

onMounted(loadData)
</script>

<style scoped>
.toolbar :deep(.n-input) {
  max-width: 300px;
}

.row-actions,
.modal-actions {
  display: flex;
  gap: 10px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 18px;
}

.form-modal {
  width: min(92vw, 540px);
}

.modal-actions {
  justify-content: flex-end;
}
</style>
