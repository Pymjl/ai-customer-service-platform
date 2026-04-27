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
          <p>公共启用知识库默认参与 RAG，可额外选择我的知识库。</p>
        </div>
        <el-tag :type="usingFallback ? 'info' : 'success'" effect="light">{{ usingFallback ? '示例知识库' : '知识库已加载' }}</el-tag>
      </header>

      <el-scrollbar ref="messageScrollRef" class="message-scroll">
        <div class="message-list">
          <div v-for="message in activeMessages" :key="message.id" class="message-row" :class="message.role">
            <el-avatar :size="34" :icon="message.role === 'assistant' ? Service : User" />
            <div class="message-bubble">
              <div class="message-meta">{{ message.role === 'assistant' ? '智能客服' : '我' }}</div>
              <p v-html="renderMessage(message.content)"></p>
              <div v-if="message.citations?.length" class="citation-list">
                <button
                  v-for="citation in message.citations"
                  :key="String(citation.citationId)"
                  type="button"
                  @click="openCitation(citation)"
                >
                  {{ citation.citationId }} · {{ citation.kbName }} / {{ citation.documentTitle }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </el-scrollbar>

      <div class="quick-actions">
        <el-button v-for="item in quickPrompts" :key="item" @click="draft = item">{{ item }}</el-button>
      </div>

      <footer class="composer">
        <div class="knowledge-selector">
          <el-radio-group v-model="activeSelection.mode">
            <el-radio-button label="DEFAULT">默认</el-radio-button>
            <el-radio-button label="PUBLIC_ONLY">仅公共</el-radio-button>
            <el-radio-button label="PERSONAL_ONLY">仅我的</el-radio-button>
            <el-radio-button label="NONE">不使用</el-radio-button>
          </el-radio-group>
          <el-select
            v-model="activeSelection.personalKbIds"
            multiple
            collapse-tags
            :disabled="activeSelection.mode === 'PUBLIC_ONLY' || activeSelection.mode === 'NONE'"
            placeholder="追加我的知识库"
          >
            <el-option v-for="kb in personalKbs" :key="kb.kbId" :label="kb.name" :value="kb.kbId" />
          </el-select>
          <span class="selection-summary">{{ selectionSummary }}</span>
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

      <el-drawer v-model="citationDrawerVisible" size="420px" title="引用来源">
        <el-descriptions v-if="activeCitation" :column="1" border>
          <el-descriptions-item label="知识库">{{ activeCitation.kbName || activeCitation.kbId }}</el-descriptions-item>
          <el-descriptions-item label="版本">v{{ activeCitation.kbVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文档">{{ activeCitation.documentTitle || activeCitation.documentId }}</el-descriptions-item>
          <el-descriptions-item label="片段">{{ activeCitation.chunkId }}</el-descriptions-item>
          <el-descriptions-item label="章节">{{ Array.isArray(activeCitation.sectionPath) ? activeCitation.sectionPath.join(' / ') : '-' }}</el-descriptions-item>
          <el-descriptions-item label="相似度">{{ activeCitation.score }}</el-descriptions-item>
        </el-descriptions>
        <pre class="snippet">{{ activeCitation?.snippet || '暂无片段内容' }}</pre>
      </el-drawer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import type { ScrollbarInstance } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Plus, Promotion, Service, User } from '@element-plus/icons-vue'
import { createSession, fetchKnowledgeSelectable, fetchMessages, fetchSessions, postChatStream } from '@/api/biz'
import { useAuthStore } from '@/stores/auth'
import type { KnowledgeBase, KnowledgeSelection, MessageDTO, SessionDTO } from '@/types/biz'

interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  citations?: Array<Record<string, unknown>>
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
const activeConversationId = ref('session-default')
const draft = ref('')
const sending = ref(false)
const usingFallback = ref(false)
const publicKbs = ref<KnowledgeBase[]>([])
const personalKbs = ref<KnowledgeBase[]>([])
const citationDrawerVisible = ref(false)
const activeCitation = ref<Record<string, unknown> | null>(null)
let nextMessageId = 4

const fallbackPublicKbs: KnowledgeBase[] = [
  { kbId: 'kb_case_default', scope: 'PUBLIC', name: '客服案例库', kbType: 'CASE_LIBRARY', enabled: true, locked: true, documentCount: 12, currentVersion: 1 },
  { kbId: 'kb_after_sales', scope: 'PUBLIC', name: '售后政策库', kbType: 'GENERIC_PUBLIC', enabled: true, documentCount: 8, currentVersion: 2 }
]
const fallbackPersonalKbs: KnowledgeBase[] = [
  { kbId: 'kb_personal_notes', scope: 'PERSONAL', name: '我的售后笔记', kbType: 'PERSONAL', enabled: true, documentCount: 3, currentVersion: 1 }
]

const conversations = ref<Conversation[]>([
  {
    id: 'session-default',
    title: '默认会话',
    time: '刚刚',
    knowledgeSelection: createDefaultSelection(),
    messages: [
      { id: 1, role: 'assistant', content: '您好，我是智能客服助手。公共启用知识库会默认参与回答，您也可以勾选个人知识库作为补充。' }
    ]
  }
])

const activeConversation = computed(() => conversations.value.find((item) => item.id === activeConversationId.value) || conversations.value[0])
const activeMessages = computed(() => activeConversation.value?.messages || [])
const activeSelection = computed(() => activeConversation.value.knowledgeSelection)
const selectionSummary = computed(() => {
  const mode = activeSelection.value.mode
  if (mode === 'NONE') return '本次不检索知识库'
  const personalCount = activeSelection.value.personalKbIds.length
  if (mode === 'PUBLIC_ONLY') return `公共 ${publicKbs.value.length} 个`
  if (mode === 'PERSONAL_ONLY') return `个人 ${personalCount} 个`
  return `公共 ${publicKbs.value.length} 个 · 个人 ${personalCount} 个`
})

onMounted(async () => {
  await Promise.all([loadKnowledgeOptions(), loadSessions()])
})

watch(activeConversationId, (sessionId) => {
  loadMessages(sessionId)
})

async function loadKnowledgeOptions() {
  try {
    const selectable = await fetchKnowledgeSelectable()
    publicKbs.value = selectable.publicKbs?.length ? selectable.publicKbs : fallbackPublicKbs
    personalKbs.value = selectable.personalKbs?.length ? selectable.personalKbs : fallbackPersonalKbs
    usingFallback.value = !selectable.publicKbs?.length && !selectable.personalKbs?.length
  } catch {
    usingFallback.value = true
    publicKbs.value = fallbackPublicKbs
    personalKbs.value = fallbackPersonalKbs
  }
}

async function loadSessions() {
  try {
    const sessions = await fetchSessions()
    if (sessions.length > 0) {
      conversations.value = sessions.map(toConversation)
      activeConversationId.value = conversations.value[0].id
      await loadMessages(activeConversationId.value)
    }
  } catch {
    usingFallback.value = true
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
    // 保留本地消息。
  }
}

function createDefaultSelection(): KnowledgeSelection {
  return { mode: 'DEFAULT', personalKbIds: [] }
}

async function startNewConversation() {
  const id = `session-${Date.now()}`
  const title = `新会话 ${conversations.value.length + 1}`
  conversations.value.unshift({
    id,
    title,
    time: '刚刚',
    knowledgeSelection: createDefaultSelection(),
    messages: [{ id: nextMessageId++, role: 'assistant', content: '新的客服会话已创建。' }]
  })
  activeConversationId.value = id
  try {
    await createSession({ sessionId: id, title })
  } catch {
    ElMessage.info('会话已在本地创建，后端会话接口暂不可用')
  }
}

function toConversation(session: SessionDTO): Conversation {
  return {
    id: session.sessionId,
    title: session.title || '未命名会话',
    time: session.updatedAt || session.createdAt || '刚刚',
    knowledgeSelection: createDefaultSelection(),
    messages: [{ id: nextMessageId++, role: 'assistant', content: '历史会话已加载，请继续输入需要咨询的问题。' }]
  }
}

function toChatMessages(message: MessageDTO): ChatMessage[] {
  const rows: ChatMessage[] = []
  if (message.userMsg || (message.role === 'user' && message.content)) {
    rows.push({ id: nextMessageId++, role: 'user', content: message.userMsg || message.content || '' })
  }
  if (message.aiReply || (message.role === 'assistant' && message.content)) {
    rows.push({ id: nextMessageId++, role: 'assistant', content: message.aiReply || message.content || '' })
  }
  return rows
}

function selectionSnapshot(): KnowledgeSelection {
  const selection = activeSelection.value
  return {
    mode: selection.mode,
    personalKbIds: selection.mode === 'PUBLIC_ONLY' || selection.mode === 'NONE' ? [] : [...selection.personalKbIds]
  }
}

async function sendMessage() {
  const content = draft.value.trim()
  const conversation = activeConversation.value
  if (!content || !conversation || sending.value) return

  const assistantMessage: ChatMessage = { id: nextMessageId++, role: 'assistant', content: '', citations: [] }
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
        onCitation(citation) {
          assistantMessage.citations?.push(citation)
        },
        onError(message) {
          throw new Error(message)
        }
      }
    )
    if (!assistantMessage.content) assistantMessage.content = '已收到请求，后端暂未返回内容。'
  } catch {
    assistantMessage.content = `已收到您的问题。当前后端聊天接口尚未确认，页面已按新契约携带 knowledgeSelection：${selectionSummary.value}。`
    ElMessage.info('聊天接口暂不可用，已展示本地示例回复')
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

function openCitation(citation: Record<string, unknown>) {
  activeCitation.value = citation
  citationDrawerVisible.value = true
}

function renderMessage(content: string) {
  return escapeHtml(content).replace(/\[\^(c_\d+)]/g, '<sup class="inline-citation">[$1]</sup>')
}

function escapeHtml(content: string) {
  return content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
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
  border-radius: 8px;
  background: #fff;
}

.conversation-panel {
  padding: 16px;
}

.panel-title,
.chat-header,
.input-row,
.knowledge-selector {
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
.selection-summary {
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

.chat-workspace {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto auto;
}

.chat-header {
  padding: 18px 22px;
  border-bottom: 1px solid #e5e7eb;
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
  border-radius: 8px;
  background: #f8fafc;
  color: #1f2937;
}

.message-row.user .message-bubble {
  background: #2563eb;
  color: #fff;
}

.message-meta {
  margin-bottom: 6px;
  color: #64748b;
  font-size: 12px;
}

.message-bubble p {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
}

.citation-list {
  display: grid;
  gap: 6px;
  margin-top: 10px;
}

.citation-list button {
  padding: 6px 8px;
  text-align: left;
  border: 1px solid #dbe3ef;
  border-radius: 6px;
  background: #fff;
  color: #2563eb;
  cursor: pointer;
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

.knowledge-selector {
  flex-wrap: wrap;
}

.knowledge-selector .el-select {
  width: 260px;
}

.input-row :deep(.el-textarea) {
  flex: 1;
}

.input-row .el-button {
  height: 48px;
}

.snippet {
  margin-top: 14px;
  padding: 12px;
  white-space: pre-wrap;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
  line-height: 1.7;
}

@media (max-width: 960px) {
  .chat-page {
    grid-template-columns: 1fr;
  }
}
</style>
