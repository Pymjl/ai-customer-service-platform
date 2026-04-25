<template>
  <div class="chat-page">
    <aside class="conversation-panel">
      <div class="panel-title">
        <h3>会话</h3>
        <el-button type="primary" icon="Plus" circle @click="startNewConversation" />
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
          <p>客服问答页面已预置，后端对话接口接入后可替换当前本地消息流。</p>
        </div>
        <el-tag type="warning" effect="light">接口待接入</el-tag>
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
        <el-input
          v-model="draft"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          resize="none"
          placeholder="请输入客户问题或业务咨询内容"
          @keydown.enter.exact.prevent="sendMessage"
        />
        <el-button type="primary" icon="Promotion" :disabled="!draft.trim()" @click="sendMessage">发送</el-button>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import type { ScrollbarInstance } from 'element-plus'
import { Service, User } from '@element-plus/icons-vue'

interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
}

interface Conversation {
  id: number
  title: string
  time: string
  messages: ChatMessage[]
}

const quickPrompts = ['查询订单状态', '售后退款咨询', '转人工客服']
const messageScrollRef = ref<ScrollbarInstance>()
const activeConversationId = ref(1)
const draft = ref('')
let nextConversationId = 2
let nextMessageId = 4

const conversations = ref<Conversation[]>([
  {
    id: 1,
    title: '默认会话',
    time: '刚刚',
    messages: [
      { id: 1, role: 'assistant', content: '您好，我是智能客服助手。当前对话接口尚未接入，您可以先体验前端交互流程。' },
      { id: 2, role: 'assistant', content: '后续接入接口时，可将发送逻辑替换为真实的问答、知识库检索或工单创建服务。' }
    ]
  }
])

const activeConversation = computed(() => conversations.value.find((item) => item.id === activeConversationId.value) || conversations.value[0])
const activeMessages = computed(() => activeConversation.value?.messages || [])

function startNewConversation() {
  const id = nextConversationId++
  conversations.value.unshift({
    id,
    title: `新会话 ${id}`,
    time: '刚刚',
    messages: [
      { id: nextMessageId++, role: 'assistant', content: '新的客服会话已创建，请输入需要咨询的问题。' }
    ]
  })
  activeConversationId.value = id
}

function usePrompt(prompt: string) {
  draft.value = prompt
}

async function sendMessage() {
  const content = draft.value.trim()
  if (!content || !activeConversation.value) return
  activeConversation.value.messages.push({ id: nextMessageId++, role: 'user', content })
  activeConversation.value.messages.push({
    id: nextMessageId++,
    role: 'assistant',
    content: '已收到您的问题。当前后端智能客服服务尚未实现，接口接入后这里将展示真实回复。'
  })
  activeConversation.value.time = '刚刚'
  draft.value = ''
  await nextTick()
  messageScrollRef.value?.setScrollTop(999999)
}
</script>

<style scoped>
.chat-page {
  height: calc(100vh - 124px);
  min-height: 620px;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 18px;
}

.conversation-panel,
.chat-workspace {
  min-height: 0;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 16px 45px rgba(15, 23, 42, 0.08);
}

.conversation-panel {
  padding: 16px;
}

.panel-title,
.chat-header,
.composer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.panel-title h3,
.chat-header h1 {
  margin: 0;
  color: #0f172a;
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

.chat-header p {
  margin: 6px 0 0;
  color: #64748b;
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
  border-radius: 12px;
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
  line-height: 1.7;
}

.quick-actions {
  display: flex;
  gap: 10px;
  padding: 0 22px 14px;
}

.composer {
  padding: 16px 22px 20px;
  border-top: 1px solid #e5e7eb;
}

.composer :deep(.el-textarea) {
  flex: 1;
}

.composer .el-button {
  height: 48px;
}
</style>
