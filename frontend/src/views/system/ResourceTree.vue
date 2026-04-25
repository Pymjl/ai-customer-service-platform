<template>
  <div>
    <div class="page-header">
      <div>
        <h1 class="page-title">资源树管理</h1>
        <p class="page-subtitle">管理菜单、按钮、接口资源，支持从后端同步资源清单</p>
      </div>
      <div>
        <el-button icon="Refresh" :loading="syncing" @click="handleSync">同步资源</el-button>
        <el-button type="primary" icon="Plus" @click="openCreate()">新增资源</el-button>
      </div>
    </div>

    <el-card class="page-card">
      <el-table v-loading="loading" :data="resources" row-key="id" default-expand-all stripe>
        <el-table-column prop="name" label="资源名称" min-width="220" />
        <el-table-column prop="code" label="资源编码" min-width="180" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }"><el-tag :type="resourceTypeMeta(row.type).tag">{{ resourceTypeMeta(row.type).label }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="220" />
        <el-table-column prop="method" label="方法" width="100" />
        <el-table-column prop="sort" label="排序" width="90" />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="openCreate(row)">新增子级</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑资源' : '新增资源'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级资源"><el-tree-select v-model="form.parentId" :data="resources" node-key="id" check-strictly clearable :props="treeProps" /></el-form-item>
        <el-form-item label="资源名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="资源编码" prop="code"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="类型" prop="type"><el-select v-model="form.type"><el-option label="菜单" value="MENU" /><el-option label="按钮" value="BUTTON" /><el-option label="接口" value="API" /></el-select></el-form-item>
        <el-form-item label="路径"><el-input v-model="form.path" /></el-form-item>
        <el-form-item label="方法"><el-input v-model="form.method" placeholder="GET / POST / PUT / DELETE" /></el-form-item>
        <el-form-item label="图标"><el-input v-model="form.icon" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
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
import { createResource, deleteResource, fetchResources, syncResources, updateResource } from '@/api/system'
import type { ResourceItem, ResourceType } from '@/types/system'

const typeMap: Record<ResourceType, { label: string; tag: 'primary' | 'success' | 'warning' }> = {
  MENU: { label: '菜单', tag: 'primary' },
  BUTTON: { label: '按钮', tag: 'success' },
  API: { label: '接口', tag: 'warning' }
}

function resourceTypeMeta(type: ResourceType) {
  return typeMap[type]
}

const treeProps = { label: 'name', children: 'children' }
const loading = ref(false)
const saving = ref(false)
const syncing = ref(false)
const resources = ref<ResourceItem[]>([])
const dialogVisible = ref(false)
const editingId = ref<ResourceItem['id'] | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<Partial<ResourceItem>>({ parentId: null, name: '', code: '', type: 'MENU', path: '', method: '', icon: '', sort: 0 })
const rules: FormRules = {
  name: [{ required: true, message: '请输入资源名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入资源编码', trigger: 'blur' }],
  type: [{ required: true, message: '请选择资源类型', trigger: 'change' }]
}

async function loadData() {
  loading.value = true
  try { resources.value = await fetchResources() } finally { loading.value = false }
}

function openCreate(parent?: ResourceItem) {
  editingId.value = null
  Object.assign(form, { parentId: parent?.id ?? null, name: '', code: '', type: 'MENU', path: '', method: '', icon: '', sort: 0 })
  dialogVisible.value = true
}

function openEdit(row: ResourceItem) { editingId.value = row.id; Object.assign(form, row); dialogVisible.value = true }

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    editingId.value ? await updateResource(editingId.value, form) : await createResource(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally { saving.value = false }
}

async function handleDelete(row: ResourceItem) {
  await ElMessageBox.confirm(`确认删除资源 ${row.name}？`, '提示', { type: 'warning' })
  await deleteResource(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function handleSync() {
  syncing.value = true
  try {
    const result = await syncResources()
    ElMessage.success(`资源同步完成${typeof result?.count === 'number' ? `，新增/更新 ${result.count} 条` : ''}`)
    loadData()
  } finally { syncing.value = false }
}

onMounted(loadData)
</script>
