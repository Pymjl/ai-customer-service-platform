<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h1 class="page-title">资源管理</h1>
        <p class="page-subtitle">按服务、Controller 和 API 路径浏览后端资源清单</p>
      </div>
      <n-button :loading="syncing" @click="handleSync">
        <template #icon><n-icon :component="ArrowSync24Regular" /></template>
        同步资源
      </n-button>
    </div>

    <div class="resource-layout">
      <section class="page-card resource-tree-card">
        <div class="resource-toolbar">
          <n-input v-model:value="filterText" clearable placeholder="搜索服务或接口路径">
            <template #prefix><n-icon :component="Search24Regular" /></template>
          </n-input>
          <div class="resource-stats">
            <span><strong>{{ stats.services }}</strong><small>服务</small></span>
            <span><strong>{{ matchedApiCount }}</strong><small>{{ filterText ? '匹配接口' : '接口' }}</small></span>
          </div>
        </div>

        <n-spin :show="loading">
          <n-tree
            block-line
            :data="treeData"
            key-field="id"
            label-field="label"
            children-field="children"
            :pattern="filterText"
            :filter="resourceTreeFilter"
            :expanded-keys="expandedKeys"
            :selected-keys="selectedKeys"
            :render-label="renderResourceLabel"
            :node-props="resourceNodeProps"
            :override-default-node-click-behavior="overrideNodeClickBehavior"
            selectable
            @update:selected-keys="handleSelect"
            @update:expanded-keys="handleExpandedKeys"
          />
        </n-spin>
      </section>

      <section class="page-card detail-card">
        <h2 class="detail-card-title">资源详情</h2>
        <n-empty v-if="!selectedNode" description="请选择左侧资源节点" />
        <div v-else class="detail-content">
          <div class="detail-title">
            <n-icon :component="nodeIcon(selectedNode)" />
            <strong>{{ selectedNode.label }}</strong>
            <span class="status-pill" :class="nodeTypeMeta(selectedNode.type).tag">{{ nodeTypeMeta(selectedNode.type).label }}</span>
            <span v-if="isApiNode(selectedNode)" class="status-pill" :class="isResourceEnabled(selectedNode.resource) ? 'success' : ''">
              {{ isResourceEnabled(selectedNode.resource) ? '启用' : '停用' }}
            </span>
            <n-popconfirm
              v-if="isApiNode(selectedNode)"
              :positive-text="isResourceEnabled(selectedNode.resource) ? '停用' : '启用'"
              negative-text="取消"
              :positive-button-props="{ type: isResourceEnabled(selectedNode.resource) ? 'error' : 'primary' }"
              @positive-click="toggleResource(selectedNode)"
            >
              <template #trigger>
                <n-button size="small">
                  {{ isResourceEnabled(selectedNode.resource) ? '停用资源' : '启用资源' }}
                </n-button>
              </template>
              确认{{ isResourceEnabled(selectedNode.resource) ? '停用' : '启用' }}资源 {{ selectedNode.resource?.path || selectedNode.label }}？
            </n-popconfirm>
          </div>

          <template v-if="isApiNode(selectedNode)">
            <div class="method-line">
              <span class="status-pill" :class="methodMeta(selectedNode.resource?.httpMethod).tag">
                {{ selectedNode.resource?.httpMethod || 'ANY' }}
              </span>
              <code>{{ selectedNode.resource?.path }}</code>
            </div>

            <dl class="detail-list">
              <div><dt>资源编码</dt><dd>{{ selectedNode.resource?.resourceCode || '-' }}</dd></div>
              <div><dt>服务</dt><dd>{{ selectedNode.resource?.serviceName || '-' }}</dd></div>
              <div><dt>Controller</dt><dd>{{ selectedNode.resource?.controllerName || '-' }}</dd></div>
              <div><dt>Java 包</dt><dd>{{ packagePathFromControllerPath(selectedNode.resource?.controllerPath) || '-' }}</dd></div>
              <div><dt>Controller 文件</dt><dd>{{ selectedNode.resource?.controllerPath || '-' }}</dd></div>
              <div><dt>处理方法</dt><dd>{{ selectedNode.resource?.methodName || '-' }}</dd></div>
            </dl>

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

          <template v-else>
            <n-alert type="info" :bordered="false">目录节点用于组织资源，不直接参与接口鉴权。</n-alert>
            <dl class="detail-list">
              <div><dt>节点类型</dt><dd>{{ nodeTypeMeta(selectedNode.type).label }}</dd></div>
              <div><dt>接口数量</dt><dd>{{ directoryApiCount(selectedNode) }} 个</dd></div>
              <div v-if="displayNode(selectedNode).serviceName"><dt>服务</dt><dd>{{ displayNode(selectedNode).serviceName }}</dd></div>
              <div v-if="displayNode(selectedNode).controllerName"><dt>Controller</dt><dd>{{ displayNode(selectedNode).controllerName }}</dd></div>
              <div v-if="displayNode(selectedNode).controllerPath"><dt>Java 包</dt><dd>{{ packagePathFromControllerPath(displayNode(selectedNode).controllerPath) || '-' }}</dd></div>
              <div v-if="displayNode(selectedNode).controllerPath"><dt>Controller 文件</dt><dd>{{ displayNode(selectedNode).controllerPath }}</dd></div>
            </dl>
          </template>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, h, nextTick, onMounted, ref } from 'vue'
import type { Key } from 'treemate'
import type { TreeOption, TreeOverrideNodeClickBehavior } from 'naive-ui'
import { NIcon } from 'naive-ui'
import { ArrowSync24Regular, Cloud24Regular, Folder24Regular, Link24Regular, Search24Regular, Server24Regular } from '@vicons/fluent'
import { fetchResources, syncResources, updateResource } from '@/api/system'
import type { ResourceItem, ResourceTreeNode } from '@/types/system'
import { message } from '@/utils/feedback'
import {
  apiDisplayMethod,
  apiDisplayPath,
  buildResourceDisplayTree,
  countMatchedApis,
  countResourceTree,
  findResourceNode,
  isDisplayApiNode,
  packagePathFromControllerPath,
  resourceTreeFilter,
  type ResourceDisplayNode
} from '@/utils/resourceTree'

type TagType = 'primary' | 'success' | 'warning' | 'danger' | ''

interface DescriptionParam {
  name: string
  description: string
}

interface ParsedDescription {
  summary: string
  params: DescriptionParam[]
  returns: string
}

const loading = ref(false)
const syncing = ref(false)
const filterText = ref('')
const resources = ref<ResourceDisplayNode[]>([])
const selectedNode = ref<ResourceTreeNode | null>(null)
const selectedKeys = ref<Key[]>([])
const expandedKeys = ref<Key[]>([])

const treeData = computed(() => resources.value)
const stats = computed(() => countStats(resources.value))
const matchedApiCount = computed(() => countMatchedApis(resources.value, filterText.value))
const parsedDescription = computed(() => parseDescription(selectedNode.value?.resource?.description))
const requestExample = computed(() => normalizeJson(selectedNode.value?.resource?.requestExample, '{}'))
const responseExample = computed(() => normalizeJson(selectedNode.value?.resource?.responseExample, '{}'))
const requestExampleHtml = computed(() => highlightJson(requestExample.value))
const responseExampleHtml = computed(() => highlightJson(responseExample.value))

function isApiNode(node?: ResourceTreeNode | null) {
  return isDisplayApiNode(node)
}

function isResourceEnabled(resource?: ResourceItem | null) {
  return resource?.enabled !== false
}

function nodeIcon(node: ResourceTreeNode) {
  const type = node.type.toString().toLowerCase()
  if (type === 'api') return Link24Regular
  if (type === 'controller') return Folder24Regular
  if (type === 'service') return Server24Regular
  return Folder24Regular
}

function nodeTypeMeta(type: ResourceTreeNode['type']): { label: string; tag: TagType } {
  const key = type.toString().toLowerCase()
  const map: Record<string, { label: string; tag: TagType }> = {
    service: { label: '服务', tag: 'primary' },
    controller: { label: 'Controller', tag: 'success' },
    api: { label: 'API', tag: 'warning' }
  }
  return map[key] || { label: type.toString(), tag: '' }
}

function methodMeta(method?: string | null): { tag: TagType } {
  const map: Record<string, TagType> = {
    GET: 'success',
    POST: 'warning',
    PUT: 'primary',
    PATCH: 'primary',
    DELETE: 'danger'
  }
  return { tag: map[(method || '').toUpperCase()] || '' }
}

function countStats(nodes: ResourceTreeNode[]) {
  return countResourceTree(nodes)
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
    resources.value = buildResourceDisplayTree(await fetchResources())
    expandedKeys.value = []
    selectedNode.value = resources.value[0] || null
    selectedKeys.value = selectedNode.value ? [selectedNode.value.id] : []
    await nextTick()
  } finally {
    loading.value = false
  }
}

function handleSelect(keys: Key[]) {
  selectedKeys.value = keys
  selectedNode.value = keys.length ? findResourceNode(resources.value, keys[0]) : null
}

function handleExpandedKeys(keys: Key[]) {
  expandedKeys.value = keys
}

function resourceNodeProps({ option }: { option: TreeOption }) {
  const node = option as unknown as ResourceDisplayNode
  if (isApiNode(node)) return {}
  return {
    onClick: () => {
      selectedNode.value = node
      selectedKeys.value = [node.id]
    }
  }
}

const overrideNodeClickBehavior: TreeOverrideNodeClickBehavior = ({ option }) => {
  const node = option as unknown as ResourceDisplayNode
  return isApiNode(node) ? 'toggleSelect' : 'toggleExpand'
}

async function handleSync() {
  syncing.value = true
  try {
    const result = await syncResources()
    message.success(`资源同步完成${typeof result?.count === 'number' ? `，扫描 ${result.count} 条` : ''}`)
    await loadData()
  } finally {
    syncing.value = false
  }
}

function toggleResource(node: ResourceTreeNode) {
  if (!node.resource) return
  const enabled = !isResourceEnabled(node.resource)
  const actionLabel = enabled ? '启用' : '停用'
  return updateResource(node.resource.id, { enabled }).then(async () => {
    message.success(`资源已${actionLabel}`)
    await loadData()
  })
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

function displayNode(node: ResourceTreeNode) {
  return node as ResourceDisplayNode
}

function directoryApiCount(node: ResourceTreeNode) {
  const current = node as ResourceDisplayNode
  return current.apiCount || countStats(current.children || []).apis
}

onMounted(loadData)
</script>

<style scoped>
.resource-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(420px, 0.9fr);
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
  gap: 12px;
}

.resource-stats span {
  display: grid;
  min-width: 76px;
  padding: 8px 10px;
  border: 1px solid var(--border-subtle);
  border-radius: 14px;
  background: var(--bg-surface);
}

.resource-stats strong {
  font-size: 18px;
}

.resource-stats small {
  color: var(--text-muted);
}

.resource-tree-card :deep(.n-tree-node-content) {
  min-height: 38px;
  cursor: pointer;
}

.resource-tree-card :deep(.n-tree-node-switcher) {
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

.detail-card-title {
  margin: 0 0 16px;
  font-size: 16px;
}

.detail-content,
.detail-section {
  display: grid;
  gap: 16px;
}

.detail-title,
.method-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.detail-title strong {
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 16px;
}

.method-line code {
  min-width: 0;
  padding: 7px 9px;
  border-radius: 10px;
  background: var(--bg-surface-muted);
  color: var(--text-secondary);
  overflow-wrap: anywhere;
}

.detail-list {
  display: grid;
  gap: 0;
  margin: 0;
}

.detail-list div {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-subtle);
}

.detail-list dt {
  color: var(--text-muted);
}

.detail-list dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.detail-section h2,
.detail-section p {
  margin: 0;
}

.detail-section h2 {
  font-size: 14px;
}

.detail-section p {
  color: var(--text-secondary);
  line-height: var(--leading-relaxed);
}

.json-view {
  margin: 0;
  max-height: 260px;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.6;
}

.json-view :deep(.json-key) { color: #7dd3fc; }
.json-view :deep(.json-string) { color: #86efac; }
.json-view :deep(.json-number) { color: #fbbf24; }
.json-view :deep(.json-boolean) { color: #c4b5fd; }
.json-view :deep(.json-null) { color: #fca5a5; }

@media (max-width: 1100px) {
  .resource-layout {
    grid-template-columns: 1fr;
  }
}
</style>
