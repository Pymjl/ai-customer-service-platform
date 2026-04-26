<template>
  <div class="chat-page">
    <aside class="conversation-panel">
      <div class="panel-title">
        <h3>会话</h3>
        <el-button type="primary" :icon="Plus" circle @click="startNewConversation" />
      </div>
      <el-scrollbar class="conversation-list">
        <button
          v-for="conversation in conversations"
          :key="conversation.id"
          class="conversation-item"
          :class="{ active: conversation.id === activeConversationId }"
          type="button"
          @click="activeConversationId = conversation.id"
        >
          <span>{{ conversation.title }}</span>
          <small>{{ conversation.time }}</small>
        </button>
      </el-scrollbar>
    </aside>

    <section class="chat-workspace">
      <header class="chat-header">
        <div>
          <h1>智能客服</h1>
          <p>发送问题时会携带当前会话的知识库范围快照。</p>
        </div>
        <el-tag :type="usingFallback ? 'info' : 'success'" effect="light">{{ usingFallback ? '示例知识库' : '知识库已加载' }}</el-tag>
      </header>

      <el-scrollbar ref="messageScrollRef" class="message-scroll">
        <div class="message-list">
          <div v-for="message in activeMessages" :key="message.id" class="message-row" :class="message.role">
            <el-avatar :size="34" :icon="message.role === 'assistant' ? Service : User" />
            <div class="message-bubble">
              <div class="message-meta">{{ message.role === 'assistant' ? '智能客服' : '我' }}</div>
              <p>{{ message.content }}</p>
            </div>
          </div>
        </div>
      </el-scrollbar>

      <div class="quick-actions">
        <el-button v-for="item in quickPrompts" :key="item" @click="usePrompt(item)">{{ item }}</el-button>
      </div>

      <footer class="composer">
        <div class="knowledge-selector">
          <el-popover placement="top-start" trigger="click" width="360">
            <template #reference>
              <el-button :icon="Collection" plain>{{ selectionTitle }}</el-button>
            </template>
            <div class="selection-popover">
              <el-radio-group v-model="activeSelection.mode" @change="handleModeChange">
                <el-radio-button label="DEFAULT">默认</el-radio-button>
                <el-radio-button label="PUBLIC_ONLY">仅公共</el-radio-button>
                <el-radio-button label="PERSONAL_ONLY">仅我的</el-radio-button>
                <el-radio-button label="DISABLED">不使用</el-radio-button>
              </el-radio-group>
              <el-button class="custom-button" :icon="Setting" @click="customDialogVisible = true">自定义选择</el-button>
              <div class="selection-summary">{{ selectionSummary }}</div>
            </div>
          </el-popover>
          <span class="selection-summary inline">{{ selectionSummary }}</span>
        </div>
        <div class="input-row">
          <el-input
            v-model="draft"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 4 }"
            resize="none"
            placeholder="请输入客户问题或业务咨询内容"
            @keydown.enter.exact.prevent="sendMessage"
          />
          <el-button type="primary" :icon="Promotion" :disabled="!draft.trim() || sending" :loading="sending" @click="sendMessage">发送</el-button>
        </div>
      </footer>

      <el-dialog v-model="customDialogVisible" title="自定义知识库范围" width="820px">
        <div class="custom-selection">
          <div class="custom-toolbar">
            <el-input v-model="knowledgeKeyword" clearable placeholder="搜索标题、分类、标签" :prefix-icon="Search" />
            <el-select v-model="selectedTagIds" multiple collapse-tags clearable placeholder="标签筛选">
              <el-option v-for="tag in selectableTags" :key="tagValue(tag)" :label="tag.name" :value="tagValue(tag)" />
            </el-select>
          </div>
          <div class="custom-body">
            <aside class="custom-tree">
              <div class="custom-title">分类</div>
              <el-tree
                ref="categoryTreeRef"
                :data="categoryTree"
                node-key="categoryId"
                show-checkbox
                default-expand-all
                :props="{ label: 'name', children: 'children' }"
              />
            </aside>
            <el-table
              ref="documentTableRef"
              :data="filteredKnowledgeDocuments"
              row-key="documentId"
              height="360"
              @selection-change="handleDocumentSelection"
            >
              <el-table-column type="selection" width="48" :selectable="isReadyDocument" />
              <el-table-column label="标题" min-width="220">
                <template #default="{ row }">
                  <strong>{{ row.title }}</strong>
                  <div class="document-meta">{{ row.categoryName || '未分类' }} · {{ normalizeTags(row.tags).join('、') || '无标签' }}</div>
                </template>
              </el-table-column>
              <el-table-column label="Scope" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.scope === 'personal' ? 'success' : 'primary'">{{ row.scope === 'personal' ? '我的' : '公共' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 'READY' ? 'success' : 'warning'">{{ row.status === 'READY' ? '已就绪' : '不可选' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="updatedAt" label="更新时间" width="150" />
            </el-table>
          </div>
        </div>
        <template #footer>
          <span class="dialog-summary">将启用 {{ selectedDocumentIds.length }} 篇文档，{{ selectedCategoryIds.length }} 个分类，{{ selectedTagIds.length }} 个标签</span>
          <el-button @click="clearCustomSelection">清空</el-button>
          <el-button type="primary" @click="applyCustomSelection">确认</el-button>
        </template>
      </el-dialog>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import type { ElTable, ElTree, ScrollbarInstance } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Collection, Plus, Promotion, Search, Service, Setting, User } from '@element-plus/icons-vue'
import { createSession, fetchAllKnowledgeDocuments, fetchKnowledgeSelectable, fetchMessages, fetchSessions, postChatStream } from '@/api/biz'
import { useAuthStore } from '@/stores/auth'
import type { KnowledgeCategory, KnowledgeDocument, KnowledgeSelection, KnowledgeSelectionMode, KnowledgeTag, MessageDTO, SessionDTO } from '@/types/biz'

interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
}

interface Conversation {
  id: string
  title: string
  time: string
  knowledgeSelection: KnowledgeSelection
  messages: ChatMessage[]
}

const quickPrompts = ['查询订单状态', '售后退款咨询', '物流异常怎么处理']
const authStore = useAuthStore()
const messageScrollRef = ref<ScrollbarInstance>()
const documentTableRef = ref<InstanceType<typeof ElTable>>()
const categoryTreeRef = ref<InstanceType<typeof ElTree>>()
const activeConversationId = ref('session-default')
const draft = ref('')
const sending = ref(false)
const sessionsLoading = ref(false)
const customDialogVisible = ref(false)
const knowledgeKeyword = ref('')
const knowledgeDocuments = ref<KnowledgeDocument[]>([])
const selectableCategories = ref<KnowledgeCategory[]>([])
const selectableTags = ref<KnowledgeTag[]>([])
const usingFallback = ref(false)
const selectedDocumentIds = ref<Array<string | number>>([])
const selectedCategoryIds = ref<Array<string | number>>([])
const selectedTagIds = ref<Array<string | number>>([])
let nextMessageId = 4

const fallbackDocuments: KnowledgeDocument[] = [
  {
    id: 'doc_public_refund',
    documentId: 'doc_public_refund',
    title: '售后退款政策 FAQ',
    sourceType: 'Markdown',
    categoryId: 'cat_after_sales',
    categoryName: '售后政策',
    tags: ['退款', '售后'],
    status: 'READY',
    enabled: true,
    scope: 'public',
    updatedAt: '2026-04-26'
  },
  {
    id: 'doc_public_delivery',
    documentId: 'doc_public_delivery',
    title: '物流异常处理手册',
    sourceType: 'HTML',
    categoryId: 'cat_delivery',
    categoryName: '物流配送',
    tags: ['物流', '补发'],
    status: 'READY',
    enabled: true,
    scope: 'public',
    updatedAt: '2026-04-25'
  },
  {
    id: 'doc_personal_notes',
    documentId: 'doc_personal_notes',
    title: '个人常用回复话术',
    sourceType: 'TXT',
    categoryId: 'cat_personal',
    categoryName: '个人资料',
    tags: ['话术'],
    status: 'READY',
    enabled: true,
    scope: 'personal',
    updatedAt: '2026-04-24'
  }
]

const fallbackCategories: KnowledgeCategory[] = [
  { id: 'cat_after_sales', categoryId: 'cat_after_sales', name: '售后政策' },
  { id: 'cat_delivery', categoryId: 'cat_delivery', name: '物流配送' },
  { id: 'cat_personal', categoryId: 'cat_personal', name: '个人资料' }
]

const conversations = ref<Conversation[]>([
  {
    id: 'session-default',
    title: '默认会话',
    time: '刚刚',
    knowledgeSelection: createDefaultSelection(),
    messages: [
      { id: 1, role: 'assistant', content: '您好，我是智能客服助手。您可以先选择本次问答使用的知识库范围，再发送问题。' },
      { id: 2, role: 'assistant', content: '后端接口未就绪时，页面会用本地示例数据保留完整交互流程。' }
    ]
  }
])

const activeConversation = computed(() => conversations.value.find((item) => item.id === activeConversationId.value) || conversations.value[0])
const activeMessages = computed(() => activeConversation.value?.messages || [])
const activeSelection = computed(() => activeConversation.value.knowledgeSelection)
const categoryTree = computed(() => (selectableCategories.value.length ? selectableCategories.value : fallbackCategories).map(normalizeCategoryOption))
const selectedTagNames = computed(() => selectedTagIds.value.map((id) => {
  const tag = selectableTags.value.find((item) => String(tagValue(item)) === String(id))
  return tag?.name || String(id)
}))
const readyPublicCount = computed(() => knowledgeDocuments.value.filter((item) => item.scope !== 'personal' && isReadyDocument(item)).length)
const readyPersonalCount = computed(() => knowledgeDocuments.value.filter((item) => item.scope === 'personal' && isReadyDocument(item)).length)
const selectionTitle = computed(() => {
  const map: Record<KnowledgeSelectionMode, string> = {
    DEFAULT: '默认知识库',
    PUBLIC_ONLY: '仅公共知识库',
    PERSONAL_ONLY: '仅我的知识库',
    SELECTED: '自定义知识库',
    DISABLED: '不使用知识库'
  }
  return map[activeSelection.value.mode]
})
const selectionSummary = computed(() => {
  const selection = activeSelection.value
  if (selection.mode === 'DISABLED') return '本次不检索知识库'
  if (selection.mode === 'SELECTED') {
    const labels = [
      selection.includePublic ? `公共 ${selectedCount('public')} 篇` : '',
      selection.includePersonal ? `个人 ${selectedCount('personal')} 篇` : '',
      selection.categoryIds.length ? `${selection.categoryIds.length} 个分类` : '',
      selection.tagIds.length ? `${selection.tagIds.length} 个标签` : ''
    ].filter(Boolean)
    return labels.join(' · ') || '自定义范围未选择'
  }
  if (selection.mode === 'PUBLIC_ONLY') return `公共 ${readyPublicCount.value} 篇`
  if (selection.mode === 'PERSONAL_ONLY') return `个人 ${readyPersonalCount.value} 篇`
  return `公共 ${readyPublicCount.value} 篇 · 个人 ${readyPersonalCount.value} 篇`
})
const filteredKnowledgeDocuments = computed(() => {
  const keyword = knowledgeKeyword.value.trim().toLowerCase()
  return knowledgeDocuments.value.filter((item) => {
    const text = `${item.title}${item.categoryName || ''}${normalizeTags(item.tags).join('')}`.toLowerCase()
    const tagMatched = selectedTagIds.value.length === 0 || normalizeTags(item.tags).some((tag) => selectedTagNames.value.includes(tag))
    return (!keyword || text.includes(keyword)) && tagMatched
  })
})

onMounted(async () => {
  await Promise.all([loadKnowledgeOptions(), loadSessions()])
})

watch(activeConversationId, (sessionId) => {
  loadMessages(sessionId)
})

async function loadSessions() {
  sessionsLoading.value = true
  try {
    const sessions = await fetchSessions()
    if (sessions.length > 0) {
      conversations.value = sessions.map(toConversation)
      activeConversationId.value = conversations.value[0].id
      await loadMessages(activeConversationId.value)
    }
  } catch {
    usingFallback.value = true
  } finally {
    sessionsLoading.value = false
  }
}

async function loadMessages(sessionId: string) {
  const conversation = conversations.value.find((item) => item.id === sessionId)
  if (!conversation || sessionId === 'session-default') return
  try {
    const messages = await fetchMessages(sessionId)
    const mapped = messages.flatMap(toChatMessages)
    if (mapped.length > 0) {
      conversation.messages = mapped
      await scrollToBottom()
    }
  } catch {
    // 保留当前本地消息，避免接口异常时切换会话出现空白。
  }
}

async function loadKnowledgeOptions() {
  try {
    const [selectable, docs] = await Promise.all([
      fetchKnowledgeSelectable(),
      fetchAllKnowledgeDocuments({ pageNo: 1, pageSize: 200, status: 'READY', enabled: true })
    ])
    selectableCategories.value = selectable.categories || []
    selectableTags.value = selectable.tags || []
    knowledgeDocuments.value = docs.records.length ? docs.records : fallbackDocuments
    usingFallback.value = docs.records.length === 0
  } catch {
    usingFallback.value = true
    knowledgeDocuments.value = fallbackDocuments
    selectableCategories.value = fallbackCategories
    selectableTags.value = [
      { id: '退款', tagId: '退款', name: '退款' },
      { id: '物流', tagId: '物流', name: '物流' },
      { id: '话术', tagId: '话术', name: '话术' }
    ]
  }
}

function createDefaultSelection(): KnowledgeSelection {
  return {
    mode: 'DEFAULT',
    includePublic: true,
    includePersonal: true,
    documentIds: [],
    categoryIds: [],
    tagIds: []
  }
}

async function startNewConversation() {
  const id = `session-${Date.now()}`
  const title = `新会话 ${conversations.value.length + 1}`
  conversations.value.unshift({
    id,
    title,
    time: '刚刚',
    knowledgeSelection: createDefaultSelection(),
    messages: [
      { id: nextMessageId++, role: 'assistant', content: '新的客服会话已创建，知识库范围默认包含公共知识库和我的知识库。' }
    ]
  })
  activeConversationId.value = id
  try {
    await createSession({ sessionId: id, title })
  } catch {
    ElMessage.info('会话已在本地创建，后端会话接口暂不可用')
  }
}

function usePrompt(prompt: string) {
  draft.value = prompt
}

function handleModeChange(mode: KnowledgeSelectionMode | string | number | boolean) {
  const nextMode = mode as KnowledgeSelectionMode
  const selection = activeSelection.value
  selection.mode = nextMode
  selection.includePublic = nextMode === 'DEFAULT' || nextMode === 'PUBLIC_ONLY' || nextMode === 'SELECTED'
  selection.includePersonal = nextMode === 'DEFAULT' || nextMode === 'PERSONAL_ONLY' || nextMode === 'SELECTED'
  if (nextMode !== 'SELECTED') {
    selection.documentIds = []
    selection.categoryIds = []
    selection.tagIds = []
  }
}

function handleDocumentSelection(rows: KnowledgeDocument[]) {
  selectedDocumentIds.value = rows.map((item) => item.documentId || item.id).filter((id): id is string | number => id !== undefined)
}

function clearCustomSelection() {
  selectedDocumentIds.value = []
  selectedCategoryIds.value = []
  selectedTagIds.value = []
  documentTableRef.value?.clearSelection()
  categoryTreeRef.value?.setCheckedKeys([])
}

function applyCustomSelection() {
  const checkedKeys = categoryTreeRef.value?.getCheckedKeys(false) || []
  selectedCategoryIds.value = checkedKeys as Array<string | number>
  const selection = activeSelection.value
  selection.mode = 'SELECTED'
  selection.includePublic = true
  selection.includePersonal = true
  selection.documentIds = [...selectedDocumentIds.value]
  selection.categoryIds = [...selectedCategoryIds.value]
  selection.tagIds = [...selectedTagIds.value]
  customDialogVisible.value = false
}

function selectedCount(scope: 'public' | 'personal') {
  const selection = activeSelection.value
  if (selection.documentIds.length === 0) return knowledgeDocuments.value.filter((item) => item.scope === scope && isReadyDocument(item)).length
  return knowledgeDocuments.value.filter((item) => item.scope === scope && selection.documentIds.includes(item.documentId || item.id || '')).length
}

function isReadyDocument(row: KnowledgeDocument) {
  return row.status === 'READY' && row.enabled !== false
}

function toConversation(session: SessionDTO): Conversation {
  return {
    id: session.sessionId,
    title: session.title || '未命名会话',
    time: session.updatedAt || session.createdAt || '刚刚',
    knowledgeSelection: createDefaultSelection(),
    messages: [
      { id: nextMessageId++, role: 'assistant', content: '历史会话已加载，请继续输入需要咨询的问题。' }
    ]
  }
}

function toChatMessages(message: MessageDTO): ChatMessage[] {
  const rows: ChatMessage[] = []
  if (message.userMsg || (message.role === 'user' && message.content)) {
    rows.push({
      id: nextMessageId++,
      role: 'user',
      content: message.userMsg || message.content || ''
    })
  }
  if (message.aiReply || (message.role === 'assistant' && message.content)) {
    rows.push({
      id: nextMessageId++,
      role: 'assistant',
      content: message.aiReply || message.content || ''
    })
  }
  return rows
}

function normalizeTags(tags?: string[] | string) {
  if (!tags) return []
  return Array.isArray(tags) ? tags : tags.split(',').map((item) => item.trim()).filter(Boolean)
}

function normalizeCategoryOption(category: KnowledgeCategory): KnowledgeCategory {
  return {
    ...category,
    categoryId: category.categoryId ?? category.id,
    children: category.children?.map(normalizeCategoryOption)
  }
}

function tagValue(tag: KnowledgeTag) {
  return tag.tagId ?? tag.id ?? tag.name
}

function selectionSnapshot(): KnowledgeSelection {
  const selection = activeSelection.value
  return {
    mode: selection.mode,
    includePublic: selection.includePublic,
    includePersonal: selection.includePersonal,
    documentIds: [...selection.documentIds],
    categoryIds: [...selection.categoryIds],
    tagIds: [...selection.tagIds]
  }
}

async function sendMessage() {
  const content = draft.value.trim()
  const conversation = activeConversation.value
  if (!content || !conversation || sending.value) return

  const assistantMessage: ChatMessage = { id: nextMessageId++, role: 'assistant', content: '' }
  conversation.messages.push({ id: nextMessageId++, role: 'user', content })
  conversation.messages.push(assistantMessage)
  conversation.time = '刚刚'
  draft.value = ''
  sending.value = true
  await scrollToBottom()

  try {
    await postChatStream(
      {
        sessionId: conversation.id,
        messageId: `m-${Date.now()}`,
        message: content,
        knowledgeSelection: selectionSnapshot()
      },
      authStore.token || '',
      {
        onMessage(chunk) {
          assistantMessage.content += chunk
        },
        onError(message) {
          throw new Error(message)
        }
      }
    )
    if (!assistantMessage.content) assistantMessage.content = '已收到请求，后端暂未返回内容。'
  } catch {
    assistantMessage.content = `已收到您的问题。当前后端聊天接口尚未确认，页面已按约定请求格式携带 knowledgeSelection：${selectionTitle.value}。`
    ElMessage.info('聊天接口暂不可用，已展示本地示例回复')
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

async function scrollToBottom() {
  await nextTick()
  messageScrollRef.value?.setScrollTop(999999)
}
</script>

<style scoped>
.chat-page {
  height: calc(100vh - 124px);
  min-height: 640px;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 18px;
}

.conversation-panel,
.chat-workspace {
  min-height: 0;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 16px 45px rgba(15, 23, 42, 0.08);
}

.conversation-panel {
  padding: 16px;
}

.panel-title,
.chat-header,
.input-row,
.knowledge-selector,
.custom-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
}

.panel-title,
.chat-header {
  justify-content: space-between;
}

.panel-title h3,
.chat-header h1 {
  margin: 0;
  color: #0f172a;
}

.chat-header p,
.selection-summary,
.document-meta,
.dialog-summary {
  color: #64748b;
  font-size: 13px;
}

.conversation-list {
  height: calc(100% - 52px);
  margin-top: 14px;
}

.conversation-item {
  width: 100%;
  display: block;
  padding: 12px;
  margin-bottom: 8px;
  text-align: left;
  border: 1px solid transparent;
  border-radius: 8px;
  background: #f8fafc;
  cursor: pointer;
}

.conversation-item.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.conversation-item span,
.conversation-item small {
  display: block;
}

.conversation-item span {
  color: #0f172a;
  font-weight: 700;
}

.conversation-item small {
  margin-top: 5px;
  color: #64748b;
}

.chat-workspace {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto auto;
}

.chat-header {
  padding: 18px 22px;
  border-bottom: 1px solid #e5e7eb;
}

.message-scroll {
  min-height: 0;
}

.message-list {
  padding: 22px;
}

.message-row {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.message-bubble {
  max-width: 68%;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f8fafc;
  color: #1f2937;
}

.message-row.user .message-bubble {
  background: #2563eb;
  color: #fff;
}

.message-meta {
  margin-bottom: 6px;
  font-size: 12px;
  color: #64748b;
}

.message-row.user .message-meta {
  color: rgba(255, 255, 255, .72);
}

.message-bubble p {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
}

.quick-actions {
  display: flex;
  gap: 10px;
  padding: 0 22px 12px;
}

.composer {
  display: grid;
  gap: 10px;
  padding: 14px 22px 20px;
  border-top: 1px solid #e5e7eb;
}

.selection-popover {
  display: grid;
  gap: 12px;
}

.custom-button {
  width: fit-content;
}

.input-row :deep(.el-textarea) {
  flex: 1;
}

.input-row .el-button {
  height: 48px;
}

.custom-selection {
  display: grid;
  gap: 14px;
}

.custom-toolbar .el-input {
  width: 320px;
}

.custom-toolbar .el-select {
  width: 240px;
}

.custom-body {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 14px;
}

.custom-tree {
  min-height: 360px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.custom-title {
  margin-bottom: 10px;
  font-weight: 700;
}

@media (max-width: 960px) {
  .chat-page {
    grid-template-columns: 1fr;
  }

  .conversation-panel {
    min-height: 220px;
  }

  .custom-body {
    grid-template-columns: 1fr;
  }

  .selection-summary.inline {
    display: none;
  }
}
</style>
