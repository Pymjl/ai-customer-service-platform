import type { PageResult } from '@/types/system'

declare module 'axios' {
  interface AxiosRequestConfig {
    _suppressError?: boolean
  }
}

export type KnowledgeScope = 'public' | 'personal'

export interface SessionDTO {
  id?: number | string
  sessionId: string
  title: string
  createdAt?: string
  updatedAt?: string
}

export interface SessionPayload {
  sessionId: string
  title: string
}

export type MessageRole = 'user' | 'assistant' | 'system'

export interface MessageDTO {
  id?: number | string
  messageId?: string
  sessionId: string
  role?: MessageRole | string
  content?: string
  userMsg?: string
  aiReply?: string
  status?: string
  traceId?: string
  createdAt?: string
}

export interface KnowledgeDocument {
  id?: number | string
  documentId?: number | string
  kbId?: string
  kbVersion?: number
  kbName?: string
  kbType?: string
  title: string
  filename?: string
  originalFilename?: string
  sourceType?: string
  categoryId?: number | string | null
  categoryName?: string
  productLine?: string
  tags?: string[] | string
  status?: string
  enabled?: boolean
  scope?: KnowledgeScope | string
  fileSize?: number
  creatorName?: string
  hitCount?: number
  chunkCount?: number
  embeddingModel?: string
  failureReason?: string
  parseDurationMs?: number
  previewText?: string
  createdAt?: string
  updatedAt?: string
}

export interface KnowledgeBase {
  kbId: string
  scope: 'PUBLIC' | 'PERSONAL' | string
  name: string
  description?: string
  kbType: 'CASE_LIBRARY' | 'GENERIC_PUBLIC' | 'PERSONAL' | string
  currentVersion?: number
  enabled?: boolean
  status?: string
  locked?: boolean
  documentCount?: number
  manageable?: boolean
  hasPublicSnapshot?: boolean
  publicSnapshotKbId?: string
  createdAt?: string
  updatedAt?: string
}

export interface KnowledgeBaseQuery {
  pageNo: number
  pageSize: number
  keyword?: string
}

export interface KnowledgeBasePayload {
  scope: 'PUBLIC' | 'PERSONAL' | string
  name: string
  description?: string
  kbType?: string
  enabled?: boolean
}

export interface ContributionApplication {
  applicationId: string
  applicationType?: string
  sourceKbId?: string
  targetKbId?: string
  sourceSnapshotId?: string
  status?: string
  reason?: string
  reviewComment?: string
  createdAt?: string
  reviewedAt?: string
}

export interface ContributionDiff {
  applicationId: string
  sourceSnapshotId?: string
  targetKbId?: string
  targetVersionNo?: number
  added?: ContributionDiffItem[]
  modified?: ContributionDiffItem[]
  deleted?: ContributionDiffItem[]
}

export interface ContributionDiffItem {
  sourceDocumentId?: string
  targetDocumentId?: string
  title?: string
  sourceType?: string
  fingerprint?: string
  previousFingerprint?: string
}

export interface KnowledgeDocumentQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  categoryId?: number | string
  status?: string
  enabled?: boolean | string
  sourceType?: string
  productLine?: string
  updatedFrom?: string
  updatedTo?: string
}

export interface KnowledgeUploadPayload {
  file: File
  title: string
  sourceType: string
  categoryId?: number | string
  productLine?: string
  tags?: string
}

export interface KnowledgeUpdatePayload {
  title?: string
  sourceType?: string
  categoryId?: number | string | null
  productLine?: string
  tags?: string[] | string
}

export interface KnowledgeCategory {
  id?: number | string
  categoryId?: number | string
  parentId?: number | string | null
  name: string
  code?: string
  description?: string
  enabled?: boolean
  children?: KnowledgeCategory[]
}

export interface KnowledgeTag {
  id?: number | string
  tagId?: number | string
  name: string
  count?: number
}

export interface KnowledgeSelectable {
  publicKbs?: KnowledgeBase[]
  personalKbs?: KnowledgeBase[]
  policy?: {
    publicAlwaysOn?: boolean
    personalSelectable?: boolean
  }
  categories?: KnowledgeCategory[]
  tags?: KnowledgeTag[]
  productLines?: string[]
  sourceTypes?: string[]
  statuses?: string[]
}

export interface IngestionStatus {
  documentId: number | string
  status?: string
  message?: string
  progress?: number
  updatedAt?: string
}

export type KnowledgeSelectionMode = 'DEFAULT' | 'PUBLIC_ONLY' | 'PERSONAL_ONLY' | 'NONE'

export interface KnowledgeSelection {
  mode: KnowledgeSelectionMode
  personalKbIds: string[]
}

export interface ChatStreamPayload {
  sessionId: string
  message: string
  messageId: string
  knowledgeSelection?: KnowledgeSelection
}

export interface ChatStreamHandlers {
  onMessage: (chunk: string) => void
  onCitation?: (citation: Record<string, unknown>) => void
  onDone?: () => void
  onError?: (message: string) => void
}

export type KnowledgePageResult = PageResult<KnowledgeDocument>
export type KnowledgeBasePageResult = PageResult<KnowledgeBase>
