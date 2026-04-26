<template>
  <div>
    <div class="page-header">
      <div>
        <h1 class="page-title">资源管理</h1>
        <p class="page-subtitle">按服务、包目录、Controller 和 API 路径浏览后端资源清单</p>
      </div>
      <el-button icon="Refresh" :loading="syncing" @click="handleSync">同步资源</el-button>
    </div>

    <div class="resource-layout">
      <el-card class="page-card resource-tree-card">
        <div class="resource-toolbar">
          <el-input v-model="filterText" clearable placeholder="搜索服务、包名、Controller 或 API" prefix-icon="Search" />
          <div class="resource-stats">
            <el-statistic title="服务" :value="stats.services" />
            <el-statistic title="接口" :value="stats.apis" />
          </div>
        </div>

        <el-tree
          ref="treeRef"
          v-loading="loading"
          class="resource-tree"
          :data="resources"
          node-key="id"
          default-expand-all
          highlight-current
          :props="treeProps"
          :filter-node-method="filterNode"
          @current-change="selectedNode = $event"
        >
          <template #default="{ data }">
            <div class="resource-node" :class="{ api: isApiNode(data), disabled: isDisabledApiNode(data) }">
              <div class="resource-node-main">
                <el-icon class="resource-node-icon">
                  <component :is="nodeIcon(data)" />
                </el-icon>
                <span class="resource-node-label">{{ data.label }}</span>
                <el-tag v-if="isApiNode(data)" size="small" :type="methodMeta(data.resource?.httpMethod).tag">
                  {{ data.resource?.httpMethod || 'ANY' }}
                </el-tag>
                <el-tag v-if="isDisabledApiNode(data)" size="small" type="info" effect="plain">停用</el-tag>
              </div>

              <el-dropdown
                v-if="isApiNode(data)"
                trigger="click"
                placement="bottom-end"
                @command="handleResourceCommand(data, $event as ResourceCommand)"
              >
                <el-button class="node-action" text icon="MoreFilled" @click.stop />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :command="isResourceEnabled(data.resource) ? 'disable' : 'enable'">
                      {{ isResourceEnabled(data.resource) ? '停用资源' : '启用资源' }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-tree>
      </el-card>

      <el-card class="page-card detail-card">
        <template #header>
          <span>资源详情</span>
        </template>
        <el-empty v-if="!selectedNode" description="请选择左侧资源节点" />
        <div v-else class="detail-content">
          <div class="detail-title">
            <el-icon><component :is="nodeIcon(selectedNode)" /></el-icon>
            <strong>{{ selectedNode.label }}</strong>
            <el-tag :type="nodeTypeMeta(selectedNode.type).tag">{{ nodeTypeMeta(selectedNode.type).label }}</el-tag>
            <el-tag v-if="isApiNode(selectedNode)" :type="isResourceEnabled(selectedNode.resource) ? 'success' : 'info'">
              {{ isResourceEnabled(selectedNode.resource) ? '启用' : '停用' }}
            </el-tag>
          </div>

          <template v-if="isApiNode(selectedNode)">
            <div class="method-line">
              <el-tag :type="methodMeta(selectedNode.resource?.httpMethod).tag">
                {{ selectedNode.resource?.httpMethod || 'ANY' }}
              </el-tag>
              <code>{{ selectedNode.resource?.path }}</code>
            </div>

            <el-descriptions :column="1" border>
              <el-descriptions-item label="资源编码">{{ selectedNode.resource?.resourceCode || '-' }}</el-descriptions-item>
              <el-descriptions-item label="服务">{{ selectedNode.resource?.serviceName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="Controller">{{ selectedNode.resource?.controllerName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="处理方法">{{ selectedNode.resource?.methodName || '-' }}</el-descriptions-item>
            </el-descriptions>

            <section class="detail-section">
              <h2>接口描述</h2>
              <p>{{ parsedDescription.summary || '暂无接口描述，请同步资源或补充 Controller Javadoc。' }}</p>
            </section>

            <section class="detail-section">
              <h2>请求参数</h2>
              <pre class="json-view"><code v-html="requestExampleHtml" /></pre>
            </section>

            <section class="detail-section">
              <h2>返回结果</h2>
              <pre class="json-view"><code v-html="responseExampleHtml" /></pre>
            </section>
          </template>

          <el-alert v-else type="info" :closable="false" show-icon title="目录节点用于组织资源，不直接参与接口鉴权。" />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import type { ElTree } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchResources, syncResources, updateResource } from '@/api/system'
import type { ResourceItem, ResourceTreeNode } from '@/types/system'

type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'
type ResourceCommand = 'enable' | 'disable'

interface DescriptionParam {
  name: string
  description: string
}

interface ParsedDescription {
  summary: string
  params: DescriptionParam[]
  returns: string
}

const treeProps = { label: 'label', children: 'children' }
const loading = ref(false)
const syncing = ref(false)
const filterText = ref('')
const resources = ref<ResourceTreeNode[]>([])
const selectedNode = ref<ResourceTreeNode | null>(null)
const treeRef = ref<InstanceType<typeof ElTree>>()

const stats = computed(() => countStats(resources.value))
const parsedDescription = computed(() => parseDescription(selectedNode.value?.resource?.description))
const requestExample = computed(() => normalizeJson(selectedNode.value?.resource?.requestExample, '{}'))
const responseExample = computed(() => normalizeJson(selectedNode.value?.resource?.responseExample, '{}'))
const requestExampleHtml = computed(() => highlightJson(requestExample.value))
const responseExampleHtml = computed(() => highlightJson(responseExample.value))

watch(filterText, (value) => {
  treeRef.value?.filter(value)
})

function isApiNode(node?: ResourceTreeNode | null) {
  return node?.type?.toString().toLowerCase() === 'api'
}

function isResourceEnabled(resource?: ResourceItem | null) {
  return resource?.enabled !== false
}

function isDisabledApiNode(node?: ResourceTreeNode | null) {
  return isApiNode(node) && !isResourceEnabled(node?.resource)
}

function nodeIcon(node: ResourceTreeNode) {
  const type = node.type.toString().toLowerCase()
  if (type === 'api') return 'Link'
  if (type === 'controller') return 'Document'
  if (type === 'service') return 'Box'
  return 'Folder'
}

function nodeTypeMeta(type: ResourceTreeNode['type']): { label: string; tag: TagType } {
  const key = type.toString().toLowerCase()
  const map: Record<string, { label: string; tag: TagType }> = {
    service: { label: '服务', tag: 'primary' },
    package: { label: '包目录', tag: 'info' },
    controller: { label: 'Controller', tag: 'success' },
    api: { label: 'API', tag: 'warning' }
  }
  return map[key] || { label: type.toString(), tag: 'info' }
}

function methodMeta(method?: string | null): { tag: TagType } {
  const map: Record<string, TagType> = {
    GET: 'success',
    POST: 'primary',
    PUT: 'warning',
    PATCH: 'warning',
    DELETE: 'danger'
  }
  return { tag: map[(method || '').toUpperCase()] || 'info' }
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
    data.resource?.methodName,
    data.resource?.description
  ].some((item) => item?.toLowerCase().includes(keyword))
}

function countStats(nodes: ResourceTreeNode[]) {
  const result = { services: 0, apis: 0 }
  const walk = (items: ResourceTreeNode[]) => {
    items.forEach((item) => {
      const type = item.type.toString().toLowerCase()
      if (type === 'service') result.services += 1
      if (type === 'api') result.apis += 1
      if (item.children?.length) walk(item.children)
    })
  }
  walk(nodes)
  return result
}

function parseDescription(description?: string | null): ParsedDescription {
  const parsed: ParsedDescription = { summary: '', params: [], returns: '' }
  if (!description) return parsed

  const summary: string[] = []
  description.split(/\r?\n/).forEach((rawLine) => {
    const line = rawLine.trim()
    if (!line) return
    if (line.startsWith('用途：')) {
      summary.push(line.slice('用途：'.length).trim())
    } else if (line.startsWith('参数：')) {
      parsed.params.push(...parseParams(line.slice('参数：'.length)))
    } else if (line.startsWith('返回：')) {
      parsed.returns = line.slice('返回：'.length).trim()
    } else {
      summary.push(line)
    }
  })
  parsed.summary = summary.join(' ')
  return parsed
}

function parseParams(value: string) {
  return value
    .split(/[；;]/)
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => {
      const separatorIndex = item.indexOf('：') >= 0 ? item.indexOf('：') : item.indexOf(':')
      if (separatorIndex < 0) return { name: item, description: '' }
      return {
        name: item.slice(0, separatorIndex).trim(),
        description: item.slice(separatorIndex + 1).trim()
      }
    })
}

function normalizeJson(value?: string | null, fallback = '{}') {
  if (!value) return fallback
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function highlightJson(value: string) {
  const escaped = escapeHtml(value)
  return escaped.replace(
    /(&quot;(?:\\u[\da-fA-F]{4}|\\[^u]|[^\\&])*&quot;)(\s*:)?|\b(true|false)\b|\b(null)\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g,
    (match, stringToken, keySuffix, booleanToken, nullToken) => {
      if (stringToken) {
        return keySuffix ? `<span class="json-key">${stringToken}</span>${keySuffix}` : `<span class="json-string">${stringToken}</span>`
      }
      if (booleanToken) return `<span class="json-boolean">${booleanToken}</span>`
      if (nullToken) return `<span class="json-null">${nullToken}</span>`
      return `<span class="json-number">${match}</span>`
    }
  )
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

async function loadData() {
  loading.value = true
  try {
    resources.value = await fetchResources()
    selectedNode.value = firstApiNode(resources.value) || resources.value[0] || null
    await nextTick()
    if (selectedNode.value) {
      treeRef.value?.setCurrentKey(selectedNode.value.id)
    }
  } finally {
    loading.value = false
  }
}

function firstApiNode(nodes: ResourceTreeNode[]): ResourceTreeNode | null {
  for (const node of nodes) {
    if (isApiNode(node)) return node
    const child = firstApiNode(node.children || [])
    if (child) return child
  }
  return null
}

async function handleSync() {
  syncing.value = true
  try {
    const result = await syncResources()
    ElMessage.success(`资源同步完成${typeof result?.count === 'number' ? `，扫描 ${result.count} 条` : ''}`)
    await loadData()
  } finally {
    syncing.value = false
  }
}

async function handleResourceCommand(node: ResourceTreeNode, command: ResourceCommand) {
  if (!node.resource) return
  const enabled = command === 'enable'
  const actionLabel = enabled ? '启用' : '停用'
  await ElMessageBox.confirm(`确认${actionLabel}资源 ${node.resource.path || node.label}？`, '状态变更确认', {
    type: enabled ? 'success' : 'warning',
    confirmButtonText: actionLabel,
    cancelButtonText: '取消'
  })
  await updateResource(node.resource.id, { enabled })
  ElMessage.success(`资源已${actionLabel}`)
  await loadData()
}

onMounted(loadData)
</script>

<style scoped>
.resource-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(460px, 0.9fr);
  gap: 18px;
  align-items: start;
}

.resource-tree-card,
.detail-card {
  min-height: calc(100vh - 156px);
}

.resource-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  margin-bottom: 16px;
}

.resource-stats {
  display: flex;
  gap: 22px;
  padding: 0 4px;
}

.resource-tree {
  --el-tree-node-hover-bg-color: #f1f5f9;
}

.resource-tree :deep(.el-tree-node__content) {
  height: 38px;
  border-radius: 8px;
}

.resource-node {
  min-width: 0;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.resource-node-main {
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

.node-action {
  width: 26px;
  height: 26px;
  opacity: 0;
}

.resource-tree :deep(.el-tree-node__content:hover) .node-action {
  opacity: 1;
}

.detail-content {
  display: grid;
  gap: 18px;
}

.detail-title,
.method-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-title strong {
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 16px;
  color: #0f172a;
}

.method-line code {
  min-width: 0;
  padding: 6px 8px;
  border-radius: 6px;
  background: #f8fafc;
  color: #334155;
  overflow-wrap: anywhere;
}

.detail-section {
  display: grid;
  gap: 8px;
}

.detail-section h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.detail-section p {
  margin: 0;
  line-height: 1.7;
  color: #475569;
}

.json-view {
  margin: 0;
  max-height: 260px;
  overflow: auto;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.6;
}

.json-view :deep(.json-key) {
  color: #7dd3fc;
}

.json-view :deep(.json-string) {
  color: #86efac;
}

.json-view :deep(.json-number) {
  color: #fbbf24;
}

.json-view :deep(.json-boolean) {
  color: #c4b5fd;
}

.json-view :deep(.json-null) {
  color: #fca5a5;
}

@media (max-width: 1100px) {
  .resource-layout {
    grid-template-columns: 1fr;
  }
}
</style>
