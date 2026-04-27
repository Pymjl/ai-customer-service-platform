<template>
  <button ref="target" class="kb-card" :class="{ active }" type="button" @click="$emit('select')">
    <div class="kb-card-content">
      <div class="kb-icon-wrapper">
        <n-icon :component="icon" />
      </div>
      <div class="kb-title-row">
        <strong>{{ kb.name }}</strong>
        <span class="status-pill" :class="typeClass">{{ typeLabel }}</span>
      </div>
      <p>{{ kb.description || '暂无描述' }}</p>
      <div class="kb-meta">
        <span>{{ kb.documentCount || 0 }} 个文档</span>
        <span>v{{ kb.currentVersion || 1 }}</span>
        <span class="status-pill" :class="kb.enabled === false ? '' : 'success'">{{ kb.enabled === false ? '已禁用' : '已启用' }}</span>
        <span v-if="kb.locked" class="status-pill">已锁定</span>
      </div>
    </div>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Database24Regular, Document24Regular, Person24Regular } from '@vicons/fluent'
import type { KnowledgeBase } from '@/types/biz'
import { useSpotlight } from '@/composables/useSpotlight'

const props = defineProps<{
  kb: KnowledgeBase
  active: boolean
}>()

defineEmits<{
  select: []
}>()

const { target } = useSpotlight<HTMLButtonElement>()

const icon = computed(() => {
  if (props.kb.kbType === 'CASE_LIBRARY') return Document24Regular
  if (props.kb.scope === 'PERSONAL') return Person24Regular
  return Database24Regular
})

const typeLabel = computed(() => {
  if (props.kb.kbType === 'CASE_LIBRARY') return '客服案例库'
  if (props.kb.kbType === 'PERSONAL') return '个人'
  return '公共'
})

const typeClass = computed(() => {
  if (props.kb.kbType === 'CASE_LIBRARY') return 'warning'
  if (props.kb.scope === 'PERSONAL') return 'success'
  return 'primary'
})
</script>

<style scoped>
.kb-card {
  position: relative;
  width: 100%;
  padding: 0;
  overflow: hidden;
  color: var(--text-primary);
  text-align: left;
  background: var(--bg-surface);
  border: 1px solid var(--glass-border);
  border-radius: 20px;
  cursor: pointer;
  transition:
    transform var(--duration-spring) var(--ease-spring),
    box-shadow var(--duration-spring) var(--ease-spring),
    border-color var(--duration-base) var(--ease-out-quint),
    background-color var(--duration-base) var(--ease-out-quint);
}

.kb-card::before {
  content: "";
  position: absolute;
  inset: 0;
  background: radial-gradient(
    800px circle at var(--spotlight-x, 50%) var(--spotlight-y, 50%),
    rgba(0, 102, 204, 0.1),
    transparent 40%
  );
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--duration-base) var(--ease-out-quint);
}

.kb-card:hover,
.kb-card:focus-visible,
.kb-card.active {
  transform: translateY(-4px) scale(1.01);
  background: var(--glass-surface-hover);
  border-color: var(--accent-primary);
  box-shadow: var(--shadow-md);
}

.kb-card:hover::before,
.kb-card:focus-visible::before,
.kb-card.active::before {
  opacity: 1;
}

.kb-card-content {
  position: relative;
  display: grid;
  gap: 10px;
  padding: 18px;
}

.kb-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  color: var(--accent-primary);
  font-size: 24px;
  background: linear-gradient(135deg, var(--bg-app), var(--bg-surface));
  border-radius: 14px;
  box-shadow: var(--shadow-sm), inset 0 1px 1px rgba(255, 255, 255, 0.7);
}

.kb-title-row,
.kb-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.kb-title-row strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 16px;
}

.kb-card p {
  min-height: 42px;
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.kb-meta {
  justify-content: flex-start;
  flex-wrap: wrap;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
