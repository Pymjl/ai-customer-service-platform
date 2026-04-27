import request from '@/utils/request'
import type { AxiosRequestConfig } from 'axios'
import type {
  ChatStreamHandlers,
  ChatStreamPayload,
  ContributionApplication,
  ContributionDiff,
  IngestionStatus,
  KnowledgeBase,
  KnowledgeBasePageResult,
  KnowledgeBasePayload,
  KnowledgeBaseQuery,
  KnowledgeCategory,
  KnowledgeDocument,
  KnowledgeDocumentQuery,
  KnowledgePageResult,
  KnowledgeScope,
  KnowledgeSelectable,
  KnowledgeTag,
  KnowledgeUpdatePayload,
  KnowledgeUploadPayload,
  MessageDTO,
  SessionDTO,
  SessionPayload
} from '@/types/biz'

const silentRequestConfig = { _suppressError: true } as AxiosRequestConfig

function normalizePage<T>(result: any): { records: T[]; total: number } {
  if (Array.isArray(result)) {
    return { records: result, total: result.length }
  }
  const records = result?.records || result?.list || result?.rows || result?.content || []
  const total = Number(result?.total ?? result?.totalCount ?? records.length ?? 0)
  return { records, total }
}

function cleanParams(params: Record<string, unknown>) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== '')
  )
}

export function fetchSessions() {
  return request.get<SessionDTO[], SessionDTO[]>('/sessions')
}

export function createSession(data: SessionPayload) {
  return request.post<SessionDTO | void, SessionDTO | void>('/sessions', data)
}

export function updateSession(id: string | number, data: Partial<SessionPayload>) {
  return request.put<SessionDTO, SessionDTO>(`/sessions/${id}`, data)
}

export function deleteSession(id: string | number) {
  return request.delete<void, void>(`/sessions/${id}`)
}

export function fetchMessages(sessionId: string) {
  return request.get<MessageDTO[], MessageDTO[]>('/messages', { params: { sessionId } })
}

export function fetchKnowledgeDocuments(scope: KnowledgeScope, params: KnowledgeDocumentQuery) {
  return request
    .get<KnowledgePageResult | KnowledgeDocument[], KnowledgePageResult | KnowledgeDocument[]>(
      `/knowledge/${scope}/documents`,
      { ...silentRequestConfig, params: cleanParams(params as unknown as Record<string, unknown>) }
    )
    .then(normalizePage<KnowledgeDocument>)
}

export function fetchKnowledgeBases(scope: 'PUBLIC' | 'PERSONAL' | string, params: KnowledgeBaseQuery) {
  return request
    .get<KnowledgeBasePageResult | KnowledgeBase[], KnowledgeBasePageResult | KnowledgeBase[]>('/knowledge/kbs', {
      ...silentRequestConfig,
      params: cleanParams({ ...params, scope })
    })
    .then(normalizePage<KnowledgeBase>)
}

export function createKnowledgeBase(data: KnowledgeBasePayload) {
  return request.post<KnowledgeBase, KnowledgeBase>('/knowledge/kbs', data)
}

export function updateKnowledgeBase(kbId: string, data: Partial<KnowledgeBasePayload>) {
  return request.put<KnowledgeBase, KnowledgeBase>(`/knowledge/kbs/${kbId}`, data)
}

export function deleteKnowledgeBase(kbId: string) {
  return request.delete<void, void>(`/knowledge/kbs/${kbId}`)
}

export function enableKnowledgeBase(kbId: string) {
  return request.post<void, void>(`/knowledge/kbs/${kbId}/enable`)
}

export function disableKnowledgeBase(kbId: string) {
  return request.post<void, void>(`/knowledge/kbs/${kbId}/disable`)
}

export function fetchKnowledgeBaseDocuments(kbId: string, params: KnowledgeDocumentQuery) {
  return request
    .get<KnowledgePageResult | KnowledgeDocument[], KnowledgePageResult | KnowledgeDocument[]>(
      `/knowledge/kbs/${kbId}/documents`,
      { ...silentRequestConfig, params: cleanParams(params as unknown as Record<string, unknown>) }
    )
    .then(normalizePage<KnowledgeDocument>)
}

export function uploadKnowledgeBaseDocument(kbId: string, payload: KnowledgeUploadPayload) {
  const formData = new FormData()
  formData.append('file', payload.file)
  formData.append('title', payload.title)
  formData.append('sourceType', payload.sourceType)
  if (payload.categoryId !== undefined && payload.categoryId !== '') {
    formData.append('categoryId', String(payload.categoryId))
  }
  if (payload.productLine) formData.append('productLine', payload.productLine)
  if (payload.tags) formData.append('tags', payload.tags)
  return request.post<KnowledgeDocument, KnowledgeDocument>(`/knowledge/kbs/${kbId}/documents`, formData)
}

export function reindexKnowledgeBaseDocument(kbId: string, documentId: string | number) {
  return request.post<void, void>(`/knowledge/kbs/${kbId}/documents/${documentId}/reindex`)
}

export function contributeKnowledgeBase(kbId: string, reason?: string) {
  return request.post('/knowledge/kbs/' + kbId + '/contribute', { reason }, silentRequestConfig)
}

export function fetchMyContributions() {
  return request.get<ContributionApplication[], ContributionApplication[]>('/knowledge/contributions/mine', silentRequestConfig)
}

export function fetchPendingContributions() {
  return request.get<ContributionApplication[], ContributionApplication[]>('/knowledge/contributions/pending', silentRequestConfig)
}

export function fetchContributionDiff(applicationId: string) {
  return request.get<ContributionDiff, ContributionDiff>(`/knowledge/contributions/${applicationId}/diff`, silentRequestConfig)
}

export function approveContribution(applicationId: string, comment?: string, activateVersion = false) {
  return request.post<ContributionApplication, ContributionApplication>(
    `/knowledge/contributions/${applicationId}/approve`,
    { comment, activateVersion },
    silentRequestConfig
  )
}

export function rejectContribution(applicationId: string, comment: string) {
  return request.post<ContributionApplication, ContributionApplication>(
    `/knowledge/contributions/${applicationId}/reject`,
    { comment },
    silentRequestConfig
  )
}

export function fetchAllKnowledgeDocuments(params: KnowledgeDocumentQuery) {
  return Promise.all([
    fetchKnowledgeDocuments('public', params),
    fetchKnowledgeDocuments('personal', params)
  ]).then(([publicResult, personalResult]) => ({
    records: [...publicResult.records, ...personalResult.records],
    total: publicResult.total + personalResult.total
  }))
}

export function uploadKnowledgeDocument(scope: KnowledgeScope, payload: KnowledgeUploadPayload) {
  const formData = new FormData()
  formData.append('file', payload.file)
  formData.append('title', payload.title)
  formData.append('sourceType', payload.sourceType)
  if (payload.categoryId !== undefined && payload.categoryId !== '') {
    formData.append('categoryId', String(payload.categoryId))
  }
  if (payload.productLine) formData.append('productLine', payload.productLine)
  if (payload.tags) formData.append('tags', payload.tags)
  return request.post<KnowledgeDocument, KnowledgeDocument>(`/knowledge/${scope}/documents`, formData)
}

export function updateKnowledgeDocument(scope: KnowledgeScope, documentId: string | number, data: KnowledgeUpdatePayload) {
  return request.put<KnowledgeDocument, KnowledgeDocument>(`/knowledge/${scope}/documents/${documentId}`, data)
}

export function deleteKnowledgeDocument(scope: KnowledgeScope, documentId: string | number) {
  return request.delete<void, void>(`/knowledge/${scope}/documents/${documentId}`)
}

export function publishKnowledgeDocument(scope: KnowledgeScope, documentId: string | number) {
  return request.post<void, void>(`/knowledge/${scope}/documents/${documentId}/publish`)
}

export function enableKnowledgeDocument(scope: KnowledgeScope, documentId: string | number) {
  return request.post<void, void>(`/knowledge/${scope}/documents/${documentId}/enable`)
}

export function disableKnowledgeDocument(scope: KnowledgeScope, documentId: string | number) {
  return request.post<void, void>(`/knowledge/${scope}/documents/${documentId}/disable`)
}

export function reindexKnowledgeDocument(scope: KnowledgeScope, documentId: string | number) {
  return request.post<void, void>(`/knowledge/${scope}/documents/${documentId}/reindex`)
}

export function fetchDocumentIngestion(documentId: string | number) {
  return request.get<IngestionStatus, IngestionStatus>(`/knowledge/documents/${documentId}/ingestion`)
}

export function fetchKnowledgeCategories(scope?: KnowledgeScope) {
  return request.get<KnowledgeCategory[], KnowledgeCategory[]>('/knowledge/categories', {
    ...silentRequestConfig,
    ...(scope ? { params: { scope: scope.toUpperCase() } } : {})
  })
}

export function createKnowledgeCategory(data: Partial<KnowledgeCategory>) {
  return request.post<KnowledgeCategory, KnowledgeCategory>('/knowledge/categories', data)
}

export function updateKnowledgeCategory(id: string | number, data: Partial<KnowledgeCategory>) {
  return request.put<KnowledgeCategory, KnowledgeCategory>(`/knowledge/categories/${id}`, data)
}

export function deleteKnowledgeCategory(id: string | number) {
  return request.delete<void, void>(`/knowledge/categories/${id}`)
}

export function fetchKnowledgeTags() {
  return request.get<KnowledgeTag[] | string[], KnowledgeTag[] | string[]>('/knowledge/tags', silentRequestConfig).then((tags) => {
    return tags.map((tag) => (typeof tag === 'string' ? { name: tag } : tag))
  })
}

export function fetchKnowledgeSelectable() {
  return request.get<KnowledgeSelectable, KnowledgeSelectable>('/knowledge/selectable', silentRequestConfig)
}

export function testKnowledgeRetrieval(documentId: string | number, question: string) {
  return request.post<Array<{ content: string; score?: number; source?: string }>, Array<{ content: string; score?: number; source?: string }>>(
    `/knowledge/documents/${documentId}/retrieval-test`,
    { question },
    silentRequestConfig
  )
}

export async function postChatStream(payload: ChatStreamPayload, token: string, handlers: ChatStreamHandlers) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  const response = await fetch(`${baseUrl}/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(toStreamPayload(payload))
  })

  if (!response.ok || !response.body) {
    throw new Error(`会话请求失败（${response.status}）`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const dispatchEvent = (raw: string) => {
    const lines = raw.split(/\r?\n/)
    const event = lines
      .find((line) => line.startsWith('event:'))
      ?.slice(6)
      .trim() || 'message'
    const data = lines
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trimStart())
      .join('\n')
    if (event === 'heartbeat') return
    if (event === 'done' || data === '[DONE]') {
      handlers.onDone?.()
      return
    }
    if (event === 'citation') {
      try {
        handlers.onCitation?.(JSON.parse(data))
      } catch {
        handlers.onCitation?.({ raw: data })
      }
      return
    }
    if (event === 'error') {
      handlers.onError?.(parseStreamError(data))
      return
    }
    if (event !== 'message') return
    if (!data) return
    try {
      const parsed = JSON.parse(data)
      if (parsed.event === 'done' || parsed.type === 'done') {
        handlers.onDone?.()
      } else if (parsed.error || parsed.messageType === 'error') {
        handlers.onError?.(parsed.error || parsed.message || '智能客服回复失败')
      } else {
        handlers.onMessage(parsed.content || parsed.delta || parsed.message || data)
      }
    } catch {
      handlers.onMessage(data)
    }
  }

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const events = buffer.split(/\r?\n\r?\n/)
    buffer = events.pop() || ''
    events.forEach(dispatchEvent)
  }
  if (buffer.trim()) dispatchEvent(buffer)
}

function parseStreamError(data: string) {
  if (!data) return '智能客服回复失败'
  try {
    const parsed = JSON.parse(data)
    return parsed.message || parsed.error || '智能客服回复失败'
  } catch {
    return data
  }
}

function toStreamPayload(payload: ChatStreamPayload) {
  if (!payload.knowledgeSelection) return payload
  const selection = payload.knowledgeSelection
  return {
    ...payload,
    locale: 'zh-CN',
    knowledgeSelection: {
      mode: selection.mode,
      personalKbIds: selection.personalKbIds.map(String)
    }
  }
}
