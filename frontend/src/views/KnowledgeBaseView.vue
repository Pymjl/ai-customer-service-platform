<template>
  <div class="knowledge-page">
    <el-tabs v-model="activeTab" class="kb-tabs" stretch @tab-change="handleTabChange">
      <el-tab-pane label="公共知识库" name="PUBLIC" />
      <el-tab-pane label="我的知识库" name="PERSONAL" />
      <el-tab-pane label="我的申请" name="MINE" />
      <el-tab-pane label="审批中心" name="REVIEW" />
    </el-tabs>

    <section v-if="activeTab === 'PUBLIC' || activeTab === 'PERSONAL'" class="kb-layout">
      <aside class="kb-list-panel">
        <div class="toolbar">
          <el-input v-model="kbQuery.keyword" clearable placeholder="搜索知识库" :prefix-icon="Search" @keyup.enter="loadKnowledgeBases" />
          <el-button :icon="Refresh" @click="loadKnowledgeBases">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreateKb">新建</el-button>
        </div>

        <el-alert
          v-if="usingFallback"
          class="fallback-alert"
          type="info"
          show-icon
          :closable="false"
          title="后端知识库接口暂不可用，当前展示本地示例数据。"
        />

        <div class="kb-card-list">
          <button
            v-for="kb in knowledgeBases"
            :key="kb.kbId"
            class="kb-card"
            :class="{ active: selectedKb?.kbId === kb.kbId }"
            type="button"
            @click="selectKb(kb)"
          >
            <div class="kb-title-row">
              <strong>{{ kb.name }}</strong>
              <el-tag size="small" :type="kb.kbType === 'CASE_LIBRARY' ? 'warning' : kb.scope === 'PERSONAL' ? 'success' : 'primary'">
                {{ kbTypeLabel(kb.kbType) }}
              </el-tag>
            </div>
            <p>{{ kb.description || '暂无描述' }}</p>
            <div class="kb-meta">
              <span>{{ kb.documentCount || 0 }} 个文档</span>
              <span>v{{ kb.currentVersion || 1 }}</span>
              <el-tag size="small" :type="kb.enabled === false ? 'info' : 'success'">{{ kb.enabled === false ? '已禁用' : '已启用' }}</el-tag>
              <el-tag v-if="kb.locked" size="small" type="info">已锁定</el-tag>
            </div>
          </button>
        </div>
      </aside>

      <main class="kb-detail-panel">
        <el-empty v-if="!selectedKb" description="请选择一个知识库" />
        <template v-else>
          <header class="detail-header">
            <div>
              <h2>{{ selectedKb.name }}</h2>
              <p>{{ selectedKb.description || '暂无描述' }}</p>
            </div>
            <div class="detail-actions">
              <el-switch
                v-model="selectedKb.enabled"
                :disabled="selectedKb.locked && selectedKb.kbType === 'CASE_LIBRARY'"
                active-text="启用"
                inactive-text="禁用"
                @change="toggleKbEnabled"
              />
              <el-button v-if="selectedKb.scope === 'PERSONAL'" @click="submitContribution">
                {{ selectedKb.hasPublicSnapshot ? '提交同步申请' : '贡献到公共知识库' }}
              </el-button>
              <el-button type="primary" :icon="Upload" @click="uploadDialogVisible = true">上传文档</el-button>
            </div>
          </header>

          <el-tabs v-model="detailTab" class="detail-tabs">
            <el-tab-pane label="文档管理" name="documents">
              <div class="document-toolbar">
                <el-input v-model="docQuery.keyword" clearable placeholder="搜索文档" :prefix-icon="Search" @keyup.enter="loadDocuments" />
                <el-select v-model="docQuery.status" clearable placeholder="状态" @change="loadDocuments">
                  <el-option label="已就绪" value="READY" />
                  <el-option label="处理中" value="UPLOADED" />
                  <el-option label="失败" value="FAILED" />
                </el-select>
                <el-button :icon="Refresh" @click="loadDocuments">刷新</el-button>
              </div>
              <el-table v-loading="loadingDocuments" :data="documents" empty-text="暂无文档">
                <el-table-column prop="title" label="文档" min-width="220">
                  <template #default="{ row }">
                    <button class="title-link" type="button" @click="openDocument(row)">{{ row.title }}</button>
                    <div class="sub-text">{{ row.documentId }}</div>
                  </template>
                </el-table-column>
                <el-table-column prop="sourceType" label="类型" width="120" />
                <el-table-column label="状态" width="110">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'READY' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'">{{ row.status || '-' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="kbVersion" label="版本" width="90" />
                <el-table-column prop="updatedAt" label="更新时间" min-width="160" />
                <el-table-column label="操作" width="130" fixed="right">
                  <template #default="{ row }">
                    <el-tooltip content="重建索引">
                      <el-button :icon="RefreshRight" circle text @click="submitReindex(row)" />
                    </el-tooltip>
                    <el-tooltip content="预览">
                      <el-button :icon="View" circle text @click="openDocument(row)" />
                    </el-tooltip>
                  </template>
                </el-table-column>
              </el-table>
              <div class="pager">
                <span>共 {{ docTotal }} 条</span>
                <el-pagination v-model:current-page="docQuery.pageNo" v-model:page-size="docQuery.pageSize" layout="prev, pager, next, sizes" :total="docTotal" @change="loadDocuments" />
              </div>
            </el-tab-pane>
            <el-tab-pane label="版本管理" name="versions">
              <el-empty description="版本差异预览将在贡献/同步审批落库后展示" />
            </el-tab-pane>
            <el-tab-pane label="设置" name="settings">
              <el-descriptions :column="1" border>
                <el-descriptions-item label="知识库 ID">{{ selectedKb.kbId }}</el-descriptions-item>
                <el-descriptions-item label="范围">{{ selectedKb.scope === 'PERSONAL' ? '个人' : '公共' }}</el-descriptions-item>
                <el-descriptions-item label="类型">{{ kbTypeLabel(selectedKb.kbType) }}</el-descriptions-item>
                <el-descriptions-item label="当前版本">v{{ selectedKb.currentVersion || 1 }}</el-descriptions-item>
                <el-descriptions-item label="启用状态">{{ selectedKb.enabled === false ? '已禁用' : '已启用' }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
          </el-tabs>
        </template>
      </main>
    </section>

    <section v-else class="application-panel">
      <el-table :data="applications" empty-text="暂无申请记录">
        <el-table-column prop="applicationId" label="申请 ID" min-width="190" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ row.applicationType === 'SYNC' ? '同步' : '贡献' }}</template>
        </el-table-column>
        <el-table-column prop="sourceKbId" label="来源 KB" min-width="180" />
        <el-table-column prop="targetKbId" label="目标 KB" min-width="180" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="createdAt" label="提交时间" min-width="160" />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openDiff(row.applicationId)">差异</el-button>
            <template v-if="activeTab === 'REVIEW' && row.status === 'PENDING'">
              <el-button text type="success" @click="reviewApplication(row.applicationId, true)">通过</el-button>
              <el-button text type="danger" @click="reviewApplication(row.applicationId, false)">拒绝</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="kbDialogVisible" title="新建知识库" width="520px">
      <el-form label-position="top">
        <el-form-item label="名称"><el-input v-model="kbForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="kbForm.description" type="textarea" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="kbForm.kbType">
            <el-option label="普通公共知识库" value="GENERIC_PUBLIC" />
            <el-option label="个人知识库" value="PERSONAL" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kbDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreateKb">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="uploadDialogVisible" title="上传文档" width="560px">
      <el-form label-position="top">
        <el-form-item label="文件">
          <el-upload drag :auto-upload="false" :limit="1" :on-change="handleUploadChange">
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div>拖拽或选择 PDF / Word / Markdown / HTML / TXT</div>
          </el-upload>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="uploadForm.title" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="uploadForm.tags" placeholder="多个标签用逗号分隔" /></el-form-item>
        <el-form-item label="产品线"><el-input v-model="uploadForm.productLine" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitUpload">上传并入库</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="documentDrawerVisible" size="46%" :title="selectedDocument?.title || '文档详情'">
      <el-descriptions v-if="selectedDocument" :column="1" border>
        <el-descriptions-item label="文档 ID">{{ selectedDocument.documentId }}</el-descriptions-item>
        <el-descriptions-item label="知识库">{{ selectedDocument.kbName || selectedKb?.name }}</el-descriptions-item>
        <el-descriptions-item label="版本">v{{ selectedDocument.kbVersion || selectedKb?.currentVersion || 1 }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ selectedDocument.status || '-' }}</el-descriptions-item>
        <el-descriptions-item label="标签">{{ normalizeTags(selectedDocument.tags).join('、') || '-' }}</el-descriptions-item>
      </el-descriptions>
      <pre class="preview-text">{{ selectedDocument?.previewText || '原文预览和片段审计接口接入后将在此展示。' }}</pre>
    </el-drawer>

    <el-drawer v-model="diffDrawerVisible" size="48%" title="申请差异预览">
      <el-tabs v-if="activeDiff" model-value="added">
        <el-tab-pane :label="`新增 ${activeDiff.added?.length || 0}`" name="added">
          <diff-list :items="activeDiff.added || []" />
        </el-tab-pane>
        <el-tab-pane :label="`修改 ${activeDiff.modified?.length || 0}`" name="modified">
          <diff-list :items="activeDiff.modified || []" />
        </el-tab-pane>
        <el-tab-pane :label="`删除 ${activeDiff.deleted?.length || 0}`" name="deleted">
          <diff-list :items="activeDiff.deleted || []" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { UploadFile } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, RefreshRight, Search, Upload, UploadFilled, View } from '@element-plus/icons-vue'
import {
  approveContribution,
  createKnowledgeBase,
  fetchContributionDiff,
  disableKnowledgeBase,
  enableKnowledgeBase,
  fetchKnowledgeBaseDocuments,
  fetchKnowledgeBases,
  fetchMyContributions,
  fetchPendingContributions,
  rejectContribution,
  reindexKnowledgeBaseDocument,
  uploadKnowledgeBaseDocument,
  contributeKnowledgeBase
} from '@/api/biz'
import type { ContributionApplication, ContributionDiff, ContributionDiffItem, KnowledgeBase, KnowledgeDocument } from '@/types/biz'

const activeTab = ref<'PUBLIC' | 'PERSONAL' | 'MINE' | 'REVIEW'>('PUBLIC')
const detailTab = ref('documents')
const loadingDocuments = ref(false)
const usingFallback = ref(false)
const knowledgeBases = ref<KnowledgeBase[]>([])
const selectedKb = ref<KnowledgeBase | null>(null)
const documents = ref<KnowledgeDocument[]>([])
const docTotal = ref(0)
const kbDialogVisible = ref(false)
const uploadDialogVisible = ref(false)
const documentDrawerVisible = ref(false)
const selectedDocument = ref<KnowledgeDocument | null>(null)
const applications = ref<ContributionApplication[]>([])
const activeDiff = ref<ContributionDiff | null>(null)
const diffDrawerVisible = ref(false)

const kbQuery = reactive({ pageNo: 1, pageSize: 20, keyword: '' })
const docQuery = reactive({ pageNo: 1, pageSize: 10, keyword: '', status: '' })
const kbForm = reactive({ name: '', description: '', kbType: 'GENERIC_PUBLIC' })
const uploadForm = reactive({ file: null as File | null, title: '', tags: '', productLine: '' })

const fallbackKbs = computed<KnowledgeBase[]>(() => activeTab.value === 'PUBLIC'
  ? [
      { kbId: 'kb_case_default', scope: 'PUBLIC', name: '客服案例库', description: '系统默认公共案例库', kbType: 'CASE_LIBRARY', enabled: true, locked: true, documentCount: 2, currentVersion: 1 },
      { kbId: 'kb_after_sales', scope: 'PUBLIC', name: '售后政策库', description: '退款、换货、维修政策', kbType: 'GENERIC_PUBLIC', enabled: true, documentCount: 1, currentVersion: 3 }
    ]
  : [
      { kbId: 'kb_personal_notes', scope: 'PERSONAL', name: '我的售后笔记', description: '个人沉淀的话术和处理记录', kbType: 'PERSONAL', enabled: true, documentCount: 1, currentVersion: 1 }
    ])

const fallbackDocuments: KnowledgeDocument[] = [
  { documentId: 'doc_refund', kbId: 'kb_after_sales', kbVersion: 3, title: '售后退款政策 FAQ', sourceType: 'Markdown', status: 'READY', enabled: true, updatedAt: '2026-04-26', tags: ['退款', '售后'] },
  { documentId: 'doc_case', kbId: 'kb_case_default', kbVersion: 1, title: '客服案例样例', sourceType: 'CASE_LIBRARY', status: 'READY', enabled: true, updatedAt: '2026-04-26', tags: ['案例'] }
]

onMounted(loadKnowledgeBases)

async function handleTabChange() {
  selectedKb.value = null
  documents.value = []
  if (activeTab.value === 'PUBLIC' || activeTab.value === 'PERSONAL') {
    await loadKnowledgeBases()
  } else {
    await loadApplications()
  }
}

async function loadKnowledgeBases() {
  if (activeTab.value !== 'PUBLIC' && activeTab.value !== 'PERSONAL') return
  try {
    const result = await fetchKnowledgeBases(activeTab.value, kbQuery)
    knowledgeBases.value = result.records
    usingFallback.value = false
  } catch {
    knowledgeBases.value = fallbackKbs.value
    usingFallback.value = true
  }
  if (!selectedKb.value && knowledgeBases.value.length) {
    await selectKb(knowledgeBases.value[0])
  }
}

async function selectKb(kb: KnowledgeBase) {
  selectedKb.value = kb
  docQuery.pageNo = 1
  await loadDocuments()
}

async function loadDocuments() {
  if (!selectedKb.value) return
  loadingDocuments.value = true
  try {
    const result = await fetchKnowledgeBaseDocuments(selectedKb.value.kbId, {
      pageNo: docQuery.pageNo,
      pageSize: docQuery.pageSize,
      keyword: docQuery.keyword,
      status: docQuery.status
    })
    documents.value = result.records
    docTotal.value = result.total
    usingFallback.value = false
  } catch {
    const rows = fallbackDocuments.filter((item) => item.kbId === selectedKb.value?.kbId)
    documents.value = rows
    docTotal.value = rows.length
    usingFallback.value = true
  } finally {
    loadingDocuments.value = false
  }
}

function openCreateKb() {
  kbForm.name = ''
  kbForm.description = ''
  kbForm.kbType = activeTab.value === 'PERSONAL' ? 'PERSONAL' : 'GENERIC_PUBLIC'
  kbDialogVisible.value = true
}

async function submitCreateKb() {
  if (!kbForm.name.trim()) {
    ElMessage.warning('请填写知识库名称')
    return
  }
  try {
    await createKnowledgeBase({
      scope: activeTab.value,
      name: kbForm.name,
      description: kbForm.description,
      kbType: kbForm.kbType
    })
    ElMessage.success('知识库已创建')
    kbDialogVisible.value = false
    await loadKnowledgeBases()
  } catch {
    ElMessage.info('后端创建接口暂不可用')
  }
}

async function toggleKbEnabled() {
  if (!selectedKb.value) return
  try {
    if (selectedKb.value.enabled === false) {
      await disableKnowledgeBase(selectedKb.value.kbId)
    } else {
      await enableKnowledgeBase(selectedKb.value.kbId)
    }
    ElMessage.success('启用状态已更新')
  } catch {
    ElMessage.info('启停接口暂不可用，已保留当前页面状态')
  }
}

function handleUploadChange(file: UploadFile) {
  uploadForm.file = file.raw || null
  if (!uploadForm.title) uploadForm.title = file.name.replace(/\.[^.]+$/, '')
}

async function submitUpload() {
  if (!selectedKb.value || !uploadForm.file || !uploadForm.title) {
    ElMessage.warning('请选择文件并填写标题')
    return
  }
  try {
    await uploadKnowledgeBaseDocument(selectedKb.value.kbId, {
      file: uploadForm.file,
      title: uploadForm.title,
      sourceType: uploadForm.file.name.split('.').pop()?.toUpperCase() || 'TXT',
      productLine: uploadForm.productLine,
      tags: uploadForm.tags
    })
    ElMessage.success('已上传，状态将进入解析中')
    uploadDialogVisible.value = false
    await loadDocuments()
  } catch {
    ElMessage.info('后端上传接口暂不可用')
  }
}

async function submitReindex(row: KnowledgeDocument) {
  if (!selectedKb.value) return
  try {
    await reindexKnowledgeBaseDocument(selectedKb.value.kbId, row.documentId || '')
    ElMessage.success('已提交重建索引任务')
  } catch {
    ElMessage.info('后端重建接口暂不可用')
  }
}

async function submitContribution() {
  if (!selectedKb.value) return
  try {
    await contributeKnowledgeBase(selectedKb.value.kbId, '申请贡献个人知识库快照')
    ElMessage.success('申请已提交')
  } catch {
    ElMessage.info('申请接口暂不可用')
  }
}

async function loadApplications() {
  try {
    applications.value = activeTab.value === 'MINE'
      ? await fetchMyContributions()
      : await fetchPendingContributions()
  } catch {
    applications.value = []
    ElMessage.info('申请列表接口暂不可用')
  }
}

async function openDiff(applicationId: string) {
  try {
    activeDiff.value = await fetchContributionDiff(applicationId)
    diffDrawerVisible.value = true
  } catch {
    ElMessage.info('差异预览接口暂不可用')
  }
}

async function reviewApplication(applicationId: string, approved: boolean) {
  try {
    if (approved) {
      await approveContribution(applicationId, '审批通过，公共快照默认按知识库启用状态控制', false)
    } else {
      await rejectContribution(applicationId, '内容暂不适合进入公共知识库')
    }
    ElMessage.success('审批结果已提交')
    await loadApplications()
  } catch {
    ElMessage.info('审批接口暂不可用')
  }
}

function openDocument(row: KnowledgeDocument) {
  selectedDocument.value = row
  documentDrawerVisible.value = true
}

function normalizeTags(tags?: string[] | string) {
  if (!tags) return []
  return Array.isArray(tags) ? tags : tags.split(',').map((item) => item.trim()).filter(Boolean)
}

function kbTypeLabel(type?: string) {
  if (type === 'CASE_LIBRARY') return '客服案例库'
  if (type === 'PERSONAL') return '个人'
  return '公共'
}

const DiffList = {
  props: {
    items: { type: Array as () => ContributionDiffItem[], required: true }
  },
  template: `
    <div class="diff-list">
      <el-empty v-if="items.length === 0" description="暂无差异" />
      <div v-for="item in items" :key="item.sourceDocumentId || item.targetDocumentId" class="diff-item">
        <strong>{{ item.title || item.sourceDocumentId || item.targetDocumentId }}</strong>
        <span>{{ item.sourceType || '-' }}</span>
        <small>{{ item.fingerprint || item.previousFingerprint }}</small>
      </div>
    </div>
  `
}
</script>

<style scoped>
.knowledge-page {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 16px;
  width: 100%;
  min-height: calc(100vh - 124px);
}

.kb-tabs {
  min-width: 0;
  padding: 0 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.kb-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.kb-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.kb-tabs :deep(.el-tabs__item) {
  height: 48px;
}

.kb-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.36fr) minmax(660px, 1fr);
  gap: 16px;
  width: 100%;
  min-height: 0;
}

.kb-list-panel,
.kb-detail-panel,
.application-panel {
  min-height: 0;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.kb-list-panel {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  overflow: hidden;
}

.kb-detail-panel {
  display: grid;
  grid-template-rows: min-content minmax(0, 1fr);
}

.application-panel {
  width: 100%;
  overflow: hidden;
}

.toolbar,
.document-toolbar,
.detail-header,
.detail-actions,
.pager,
.kb-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar,
.document-toolbar {
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.fallback-alert {
  margin-bottom: 12px;
}

.kb-card-list {
  display: grid;
  align-content: start;
  gap: 10px;
  min-height: 0;
  overflow: auto;
}

.kb-card {
  display: grid;
  gap: 8px;
  width: 100%;
  padding: 12px;
  text-align: left;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.kb-card.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.kb-title-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.kb-card p,
.detail-header p,
.sub-text {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.detail-header {
  justify-content: space-between;
  margin-bottom: 14px;
}

.detail-header > div:first-child {
  min-width: 0;
}

.detail-header h2 {
  margin: 0 0 6px;
}

.detail-tabs {
  min-width: 0;
}

.detail-tabs :deep(.el-tabs__content),
.detail-tabs :deep(.el-tab-pane) {
  min-width: 0;
}

.detail-tabs :deep(.el-table) {
  width: 100%;
}

.document-toolbar :deep(.el-input) {
  flex: 1 1 260px;
  min-width: 220px;
}

.document-toolbar :deep(.el-select) {
  flex: 0 0 150px;
}

.title-link {
  padding: 0;
  color: #2563eb;
  font-weight: 700;
  text-align: left;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.pager {
  justify-content: space-between;
  padding-top: 14px;
  color: #64748b;
}

.upload-icon {
  color: #2563eb;
  font-size: 32px;
}

.preview-text {
  min-height: 240px;
  margin-top: 14px;
  padding: 14px;
  white-space: pre-wrap;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
  line-height: 1.7;
}

.diff-list {
  display: grid;
  gap: 10px;
}

.diff-item {
  display: grid;
  gap: 4px;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.diff-item span,
.diff-item small {
  color: #64748b;
}

@media (max-width: 960px) {
  .kb-layout {
    grid-template-columns: 1fr;
  }

  .kb-tabs {
    padding: 0 10px;
  }

  .detail-header,
  .detail-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
