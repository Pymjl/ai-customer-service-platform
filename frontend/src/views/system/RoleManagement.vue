<template>
  <div>
    <div class="page-header">
      <div>
        <h1 class="page-title">角色管理</h1>
        <p class="page-subtitle">维护角色编码、名称和启用状态</p>
      </div>
      <el-button type="primary" icon="Plus" @click="openCreate">新增角色</el-button>
    </div>
    <el-card class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" clearable placeholder="搜索角色编码 / 名称" style="width: 260px" @keyup.enter="loadData" />
        <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </div>
      <el-table v-loading="loading" :data="roles" stripe>
        <el-table-column prop="code" label="角色编码" min-width="160" />
        <el-table-column prop="name" label="角色名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="260" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pagination" layout="total, prev, pager, next, sizes" :total="total" v-model:current-page="query.current" v-model:page-size="query.size" @change="loadData" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑角色' : '新增角色'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色编码" prop="code"><el-input v-model="form.code" placeholder="如 ADMIN" /></el-form-item>
        <el-form-item label="角色名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createRole, deleteRole, fetchRoles, updateRole } from '@/api/system'
import type { RoleItem } from '@/types/system'

const loading = ref(false)
const saving = ref(false)
const roles = ref<RoleItem[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const editingId = ref<RoleItem['id'] | null>(null)
const formRef = ref<FormInstance>()
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
function openEdit(row: RoleItem) { editingId.value = row.id; Object.assign(form, row); dialogVisible.value = true }

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    editingId.value ? await updateRole(editingId.value, form) : await createRole(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally { saving.value = false }
}

async function handleDelete(row: RoleItem) {
  await ElMessageBox.confirm(`确认删除角色 ${row.name}？`, '提示', { type: 'warning' })
  await deleteRole(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.pagination { margin-top: 18px; justify-content: flex-end; }
</style>
