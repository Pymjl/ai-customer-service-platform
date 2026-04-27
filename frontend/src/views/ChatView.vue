<template>
  <div class="chat-page">
    <aside class="chat-sidebar">
      <div class="sidebar-search">
        <n-input v-model:value="conversationKeyword" clearable placeholder="搜索会话">
          <template #prefix>
            <n-icon :component="Search24Regular" />
          </template>
        </n-input>
        <n-button type="primary" circle aria-label="新建对话" @click="startNewConversation">
          <template #icon>
            <n-icon :component="Add24Regular" />
          </template>
        </n-button>
      </div>

      <div class="conversation-list">
        <span class="conversation-group">今天</span>
        <button
          v-for="conversation in filteredConversations"
          :key="conversation.id"
          class="conversation-item"
          :class="{ active: conversation.id === activeConversationId }"
          type="button"
          @click="activeConversationId = conversation.id"
        >
          <n-icon :component="Chat24Regular" />
          <span>
            <strong>{{ conversation.title }}</strong>
            <small>{{ conversation.time }}</small>
          </span>
        </button>
      </div>
    </aside>

    <section class="chat-workspace">
      <header class="chat-header">
        <button ref="drawerTrigger" class="sidebar-toggle" type="button" aria-label="打开会话列表" @click="drawerVisible = true">
          <n-icon :component="PanelLeft24Regular" />
        </button>
        <div>
          <h1>智能客服 · 知识库</h1>
          <p>{{ selectionSummary }}</p>
        </div>
        <span class="status-pill" :class="usingFallback ? '' : 'success'">
          {{ usingFallback ? '示例知识库' : '知识库已加载' }}
        </span>
      </header>

      <div ref="messageScrollRef" class="message-scroll">
        <div class="message-list">
          <div v-for="message in activeMessages" :key="message.id" class="message-wrapper" :class="message.role === 'assistant' ? 'ai' : 'user'">
            <div class="message-avatar">
              <n-icon :component="message.role === 'assistant' ? Bot24Regular : Person24Regular" />
            </div>
            <article class="message-content">
              <div class="message-meta">{{ message.role === 'assistant' ? '智能客服' : '我' }}</div>
              <span v-if="message.role === 'assistant' && sending && !message.content" class="sr-only" aria-live="polite">AI 正在生成回答...</span>
              <span v-if="message.role === 'assistant' && sending && !message.content" class="ai-generating-text" aria-hidden="true">AI 正在生成回答...</span>
              <p v-else class="ai-answer" v-html="renderMessage(message.content)" />
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
            </article>
          </div>
        </div>
      </div>

      <div class="quick-actions" aria-label="快捷问题">
        <n-button v-for="item in quickPrompts" :key="item" secondary @click="draft = item">{{ item }}</n-button>
      </div>

      <footer class="floating-input-container">
        <div class="knowledge-selector">
          <n-radio-group v-model:value="activeSelection.mode" name="knowledge-mode">
            <n-radio-button value="DEFAULT">默认</n-radio-button>
            <n-radio-button value="PUBLIC_ONLY">仅公共</n-radio-button>
            <n-radio-button value="PERSONAL_ONLY">仅我的</n-radio-button>
            <n-radio-button value="NONE">不使用</n-radio-button>
          </n-radio-group>
          <n-select
            v-model:value="activeSelection.personalKbIds"
            multiple
            :disabled="activeSelection.mode === 'PUBLIC_ONLY' || activeSelection.mode === 'NONE'"
            :options="personalKbOptions"
            placeholder="追加我的知识库"
          />
        </div>
        <div class="input-row">
          <n-input
            v-model:value="draft"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入您的问题..."
            @keydown.enter.exact.prevent="sendMessage"
          />
          <n-button type="primary" circle :disabled="!draft.trim() || sending" :loading="sending" aria-label="发送" @click="sendMessage">
            <template #icon>
              <n-icon :component="Send24Regular" />
            </template>
          </n-button>
        </div>
      </footer>

      <n-drawer v-model:show="drawerVisible" placement="left" :width="320" @after-leave="drawerTrigger?.focus()">
        <n-drawer-content title="会话列表" closable>
          <div class="drawer-conversations">
            <button
              v-for="conversation in filteredConversations"
              :key="conversation.id"
              class="conversation-item"
              :class="{ active: conversation.id === activeConversationId }"
              type="button"
              @click="activeConversationId = conversation.id; drawerVisible = false"
            >
              <n-icon :component="Chat24Regular" />
              <span>
                <strong>{{ conversation.title }}</strong>
                <small>{{ conversation.time }}</small>
              </span>
            </button>
          </div>
        </n-drawer-content>
      </n-drawer>

      <n-drawer v-model:show="citationDrawerVisible" :width="420" title="引用来源">
        <n-drawer-content title="引用来源" closable>
          <dl v-if="activeCitation" class="detail-list">
            <div><dt>知识库</dt><dd>{{ activeCitation.kbName || activeCitation.kbId }}</dd></div>
            <div><dt>版本</dt><dd>v{{ activeCitation.kbVersion || '-' }}</dd></div>
            <div><dt>文档</dt><dd>{{ activeCitation.documentTitle || activeCitation.documentId }}</dd></div>
            <div><dt>片段</dt><dd>{{ activeCitation.chunkId }}</dd></div>
            <div><dt>章节</dt><dd>{{ Array.isArray(activeCitation.sectionPath) ? activeCitation.sectionPath.join(' / ') : '-' }}</dd></div>
            <div><dt>相似度</dt><dd>{{ activeCitation.score }}</dd></div>
          </dl>
          <pre class="snippet">{{ activeCitation?.snippet || '暂无片段内容' }}</pre>
        </n-drawer-content>
      </n-drawer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import {
  Add24Regular,
  Bot24Regular,
  Chat24Regular,
  PanelLeft24Regular,
  Person24Regular,
  Search24Regular,
  Send24Regular
} from '@vicons/fluent'
import { createSession, fetchKnowledgeSelectable, fetchMessages, fetchSessions, postChatStream } from '@/api/biz'
import { useAuthStore } from '@/stores/auth'
import type { KnowledgeBase, KnowledgeSelection, MessageDTO, SessionDTO } from '@/types/biz'
import { message } from '@/utils/feedback'

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
const messageScrollRef = ref<HTMLElement | null>(null)
const drawerTrigger = ref<HTMLButtonElement | null>(null)
const drawerVisible = ref(false)
const conversationKeyword = ref('')
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

const filteredConversations = computed(() => {
  const keyword = conversationKeyword.value.trim().toLowerCase()
  if (!keyword) return conversations.value
  return conversations.value.filter((item) => item.title.toLowerCase().includes(keyword))
})
const activeConversation = computed(() => conversations.value.find((item) => item.id === activeConversationId.value) || conversations.value[0])
const activeMessages = computed(() => activeConversation.value?.messages || [])
const activeSelection = computed(() => activeConversation.value.knowledgeSelection)
const personalKbOptions = computed(() => personalKbs.value.map((kb) => ({ label: kb.name, value: kb.kbId })))
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
    message.info('会话已在本地创建，后端会话接口暂不可用')
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

function toChatMessages(item: MessageDTO): ChatMessage[] {
  const rows: ChatMessage[] = []
  if (item.userMsg || (item.role === 'user' && item.content)) {
    rows.push({ id: nextMessageId++, role: 'user', content: item.userMsg || item.content || '' })
  }
  if (item.aiReply || (item.role === 'assistant' && item.content)) {
    rows.push({ id: nextMessageId++, role: 'assistant', content: item.aiReply || item.content || '' })
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
        onError(errorMessage) {
          throw new Error(errorMessage)
        }
      }
    )
    if (!assistantMessage.content) assistantMessage.content = '已收到请求，后端暂未返回内容。'
  } catch {
    assistantMessage.content = `已收到您的问题。当前后端聊天接口尚未确认，页面已按新契约携带 knowledgeSelection：${selectionSummary.value}。`
    message.info('聊天接口暂不可用，已展示本地示例回复')
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
  if (messageScrollRef.value) {
    messageScrollRef.value.scrollTop = messageScrollRef.value.scrollHeight
  }
}
</script>

<style scoped>
.chat-page {
  height: calc(100vh - 124px);
  min-height: 640px;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
}

.chat-sidebar {
  min-height: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 16px;
  padding: 16px;
  overflow: hidden;
  background: var(--glass-surface);
  backdrop-filter: blur(var(--glass-blur-card));
  border: 1px solid var(--glass-border);
  border-radius: 24px;
  box-shadow: var(--shadow-sm), var(--glass-highlight);
}

.sidebar-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.conversation-list,
.drawer-conversations {
  min-height: 0;
  display: grid;
  align-content: start;
  gap: 8px;
  overflow: auto;
}

.conversation-group {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 750;
}

.conversation-item {
  width: 100%;
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 12px;
  text-align: left;
  color: var(--text-secondary);
  border: 1px solid transparent;
  border-radius: 16px;
  background: transparent;
  cursor: pointer;
  transition:
    transform var(--duration-spring) var(--ease-spring),
    background-color var(--duration-base) var(--ease-out-quint),
    border-color var(--duration-base) var(--ease-out-quint);
}

.conversation-item:hover,
.conversation-item.active {
  transform: translateY(-1px);
  color: var(--accent-primary);
  border-color: var(--glass-border);
  background: var(--glass-surface-hover);
}

.conversation-item span,
.conversation-item strong,
.conversation-item small {
  display: block;
  min-width: 0;
}

.conversation-item strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-item small {
  color: var(--text-muted);
  font-size: 12px;
}

.chat-workspace {
  position: relative;
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
  color: var(--text-primary);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 24px;
  box-shadow: var(--shadow-sm);
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 18px 22px;
  border-bottom: 1px solid var(--border-subtle);
}

.chat-header h1,
.chat-header p {
  margin: 0;
}

.chat-header h1 {
  font-size: 20px;
}

.chat-header p {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 13px;
}

.sidebar-toggle {
  display: none;
}

.message-scroll {
  min-height: 0;
  overflow: auto;
  padding-bottom: 168px;
}

.message-list {
  display: grid;
  gap: 18px;
  padding: 24px;
}

.message-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  animation: spatialFadeIn var(--duration-spring) var(--ease-spring) both;
}

.message-wrapper.user {
  flex-direction: row-reverse;
}

.message-avatar {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  color: var(--accent-primary);
  border: 1px solid var(--border-subtle);
  border-radius: 14px;
  background: var(--bg-surface-muted);
}

.message-content {
  max-width: min(72%, 760px);
  padding: 16px 20px;
  color: var(--text-primary);
  border-radius: 20px;
  font-size: 15px;
  line-height: var(--leading-base);
  letter-spacing: var(--tracking-normal);
}

.message-wrapper.ai .message-content {
  background: var(--bg-app);
  border: 1px solid var(--glass-border);
  border-top-left-radius: 4px;
}

.message-wrapper.user .message-content {
  color: var(--text-on-accent);
  background: var(--accent-primary);
  border-top-right-radius: 4px;
  box-shadow: 0 8px 16px var(--accent-primary-muted);
}

.message-wrapper.user .message-avatar {
  color: var(--text-on-accent);
  background: var(--accent-primary);
}

.message-meta {
  margin-bottom: 6px;
  color: var(--text-muted);
  font-size: 12px;
}

.message-wrapper.user .message-meta {
  color: color-mix(in srgb, var(--text-on-accent) 76%, transparent);
}

.message-content p {
  margin: 0;
  white-space: pre-wrap;
}

.citation-list {
  display: grid;
  gap: 6px;
  margin-top: 10px;
}

.citation-list button {
  padding: 7px 9px;
  color: var(--accent-primary);
  text-align: left;
  border: 1px solid var(--glass-border);
  border-radius: 10px;
  background: var(--bg-surface);
  cursor: pointer;
}

.quick-actions {
  position: absolute;
  right: 28px;
  bottom: 108px;
  left: 28px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  pointer-events: none;
}

.quick-actions :deep(.n-button) {
  pointer-events: auto;
}

.floating-input-container {
  position: absolute;
  right: 50%;
  bottom: 28px;
  width: min(84%, 820px);
  display: grid;
  gap: 10px;
  padding: 10px 14px;
  transform: translateX(50%);
  background: var(--glass-surface);
  backdrop-filter: blur(var(--glass-blur-floating));
  border: 1px solid var(--glass-border);
  border-radius: 24px;
  box-shadow: var(--shadow-lg), var(--glass-highlight);
  transition:
    transform var(--duration-spring) var(--ease-spring),
    box-shadow var(--duration-spring) var(--ease-spring),
    background-color var(--duration-base) var(--ease-out-quint);
}

.floating-input-container:focus-within {
  transform: translateX(50%) translateY(-2px);
  background: var(--glass-surface-hover);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.08), var(--glass-highlight);
}

.knowledge-selector,
.input-row {
  display: grid;
  grid-template-columns: auto minmax(180px, 1fr);
  gap: 10px;
  align-items: center;
}

.input-row {
  grid-template-columns: minmax(0, 1fr) auto;
}

.detail-list {
  display: grid;
  gap: 10px;
  margin: 0;
}

.detail-list div {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  gap: 10px;
}

.detail-list dt {
  color: var(--text-muted);
}

.detail-list dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.snippet {
  margin-top: 14px;
  padding: 12px;
  white-space: pre-wrap;
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  background: var(--bg-surface-muted);
  line-height: var(--leading-relaxed);
}

@media (min-width: 768px) and (max-width: 1023px) {
  .chat-page {
    grid-template-columns: 1fr;
  }

  .chat-sidebar {
    display: none;
  }

  .sidebar-toggle {
    display: grid;
    place-items: center;
    width: 40px;
    height: 40px;
    border: 1px solid var(--glass-border);
    border-radius: 12px;
    background: var(--bg-surface);
  }
}

@media (max-width: 767px) {
  .chat-page {
    height: calc(100vh - 60px);
    min-height: 560px;
    grid-template-columns: 1fr;
  }

  .chat-sidebar {
    display: none;
  }

  .chat-workspace {
    border-radius: 0;
  }

  .sidebar-toggle {
    display: grid;
    place-items: center;
    width: 40px;
    height: 40px;
    border: 1px solid var(--glass-border);
    border-radius: 12px;
    background: var(--bg-surface);
  }

  .chat-header {
    padding: 12px;
  }

  .chat-header h1 {
    font-size: 17px;
  }

  .status-pill {
    display: none;
  }

  .message-list {
    padding: 16px 12px;
  }

  .message-content {
    max-width: calc(100vw - 84px);
  }

  .quick-actions {
    display: none;
  }

  .floating-input-container {
    position: fixed;
    bottom: max(12px, env(safe-area-inset-bottom));
    width: calc(100vw - 24px);
    border-radius: 18px;
  }

  .knowledge-selector {
    grid-template-columns: 1fr;
  }
}
</style>
