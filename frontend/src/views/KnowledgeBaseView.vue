<template>
  <div class="knowledge-page">
    <n-tabs v-model:value="activeTab" type="segment" animated @update:value="handleTabChange">
      <n-tab-pane name="PUBLIC" tab="公共知识库" />
      <n-tab-pane name="PERSONAL" tab="我的知识库" />
      <n-tab-pane name="MINE" tab="我的申请" />
      <n-tab-pane name="REVIEW" tab="审批中心" />
    </n-tabs>

    <section v-if="activeTab === 'PUBLIC' || activeTab === 'PERSONAL'" class="kb-shell">
      <Transition name="kb-view" mode="out-in">
        <section v-if="viewMode === 'list'" key="list" class="kb-list-page page-card">
          <header class="kb-page-header">
            <div>
              <h2>{{ activeTab === 'PUBLIC' ? '公共知识库' : '我的知识库' }}</h2>
              <p>点击卡片进入知识库详情后管理文档、版本和启用状态。</p>
            </div>
            <span class="status-pill">{{ knowledgeBases.length }} 个知识库</span>
          </header>

          <div class="toolbar">
            <n-input v-model:value="kbQuery.keyword" clearable placeholder="搜索知识库" @keyup.enter="loadKnowledgeBases">
              <template #prefix><n-icon :component="Search24Regular" /></template>
            </n-input>
            <n-button @click="loadKnowledgeBases">
              <template #icon><n-icon :component="ArrowSync24Regular" /></template>
              刷新
            </n-button>
            <n-button type="primary" @click="openCreateKb">
              <template #icon><n-icon :component="Add24Regular" /></template>
              新建
            </n-button>
          </div>

          <n-alert v-if="usingFallback" class="fallback-alert" type="info" :bordered="false">
            后端知识库接口暂不可用，当前展示本地示例数据。
          </n-alert>

          <div class="kb-card-list">
            <SpotlightKbCard
              v-for="kb in knowledgeBases"
              :key="kb.kbId"
              :kb="kb"
              :active="selectedKb?.kbId === kb.kbId"
              @select="openKbDetail(kb)"
            />
          </div>
          <n-empty v-if="!knowledgeBases.length" description="暂无知识库" />
        </section>

        <main v-else key="detail" class="kb-detail-panel page-card">
          <n-empty v-if="!selectedKb" description="请选择一个知识库" />
          <template v-else>
            <header class="detail-header">
              <div class="detail-title-block">
                <n-button class="back-button" text @click="backToList">
                  <template #icon><n-icon :component="ArrowLeft24Regular" /></template>
                  返回知识库列表
                </n-button>
                <div>
                  <h2>{{ selectedKb.name }}</h2>
                  <p>{{ selectedKb.description || '暂无描述' }}</p>
                </div>
              </div>
              <div class="detail-actions">
                <n-switch
                  v-model:value="selectedKb.enabled"
                  :disabled="selectedKb.locked && selectedKb.kbType === 'CASE_LIBRARY'"
                  @update:value="toggleKbEnabled"
                >
                  <template #checked>启用</template>
                  <template #unchecked>禁用</template>
                </n-switch>
                <n-button v-if="selectedKb.scope === 'PERSONAL'" @click="submitContribution">
                  {{ selectedKb.hasPublicSnapshot ? '提交同步申请' : '贡献到公共知识库' }}
                </n-button>
                <n-button type="primary" @click="uploadDialogVisible = true">
                  <template #icon><n-icon :component="CloudArrowUp24Regular" /></template>
                  上传文档
                </n-button>
              </div>
            </header>

            <n-tabs v-model:value="detailTab" type="line" animated>
              <n-tab-pane name="documents" tab="文档管理">
                <div class="document-toolbar">
                  <n-input v-model:value="docQuery.keyword" clearable placeholder="搜索文档" @keyup.enter="loadDocuments">
                    <template #prefix><n-icon :component="Search24Regular" /></template>
                  </n-input>
                  <n-select v-model:value="docQuery.status" clearable :options="docStatusOptions" placeholder="状态" @update:value="loadDocuments" />
                  <n-button @click="loadDocuments">
                    <template #icon><n-icon :component="ArrowSync24Regular" /></template>
                    刷新
                  </n-button>
                </div>
                <n-spin :show="loadingDocuments">
                  <table class="data-table">
                    <thead>
                      <tr>
                        <th>文档</th>
                        <th>类型</th>
                        <th>状态</th>
                        <th>版本</th>
                        <th>更新时间</th>
                        <th>操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="row in documents" :key="row.documentId">
                        <td>
                          <button class="title-link" type="button" @click="openDocument(row)">{{ row.title }}</button>
                          <div class="sub-text">{{ row.documentId }}</div>
                        </td>
                        <td>{{ row.sourceType }}</td>
                        <td><span class="status-pill" :class="row.status === 'READY' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'">{{ row.status || '-' }}</span></td>
                        <td>v{{ row.kbVersion || '-' }}</td>
                        <td>{{ row.updatedAt || '-' }}</td>
                        <td class="row-actions">
                          <n-button text @click="submitReindex(row)">
                            <template #icon><n-icon :component="ArrowSync24Regular" /></template>
                          </n-button>
                          <n-button text @click="openDocument(row)">
                            <template #icon><n-icon :component="Eye24Regular" /></template>
                          </n-button>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                  <n-empty v-if="!documents.length" description="暂无文档" />
                </n-spin>
                <div class="pager">
                  <span>共 {{ docTotal }} 条</span>
                  <n-pagination v-model:page="docQuery.pageNo" v-model:page-size="docQuery.pageSize" show-size-picker :item-count="docTotal" @update:page="loadDocuments" @update:page-size="loadDocuments" />
                </div>
              </n-tab-pane>
              <n-tab-pane name="versions" tab="版本管理">
                <n-empty description="版本差异预览将在贡献/同步审批落库后展示" />
              </n-tab-pane>
              <n-tab-pane name="settings" tab="设置">
                <dl class="detail-list">
                  <div><dt>知识库 ID</dt><dd>{{ selectedKb.kbId }}</dd></div>
                  <div><dt>范围</dt><dd>{{ selectedKb.scope === 'PERSONAL' ? '个人' : '公共' }}</dd></div>
                  <div><dt>类型</dt><dd>{{ kbTypeLabel(selectedKb.kbType) }}</dd></div>
                  <div><dt>当前版本</dt><dd>v{{ selectedKb.currentVersion || 1 }}</dd></div>
                  <div><dt>启用状态</dt><dd>{{ selectedKb.enabled === false ? '已禁用' : '已启用' }}</dd></div>
                </dl>
              </n-tab-pane>
            </n-tabs>
          </template>
        </main>
      </Transition>
    </section>

    <section v-else class="application-panel page-card">
      <table class="data-table">
        <thead>
          <tr>
            <th>申请 ID</th>
            <th>类型</th>
            <th>来源 KB</th>
            <th>目标 KB</th>
            <th>状态</th>
            <th>提交时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in applications" :key="row.applicationId">
            <td>{{ row.applicationId }}</td>
            <td>{{ row.applicationType === 'SYNC' ? '同步' : '贡献' }}</td>
            <td>{{ row.sourceKbId }}</td>
            <td>{{ row.targetKbId }}</td>
            <td><span class="status-pill">{{ row.status }}</span></td>
            <td>{{ row.createdAt || '-' }}</td>
            <td class="row-actions">
              <n-button text type="primary" @click="openDiff(row.applicationId)">差异</n-button>
              <template v-if="activeTab === 'REVIEW' && row.status === 'PENDING'">
                <n-button text type="success" @click="reviewApplication(row.applicationId, true)">通过</n-button>
                <n-button text type="error" @click="reviewApplication(row.applicationId, false)">拒绝</n-button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
      <n-empty v-if="!applications.length" description="暂无申请记录" />
    </section>

    <n-modal v-model:show="kbDialogVisible" preset="card" title="新建知识库" class="form-modal">
      <n-form label-placement="top">
        <n-form-item label="名称"><n-input v-model:value="kbForm.name" /></n-form-item>
        <n-form-item label="描述"><n-input v-model:value="kbForm.description" type="textarea" /></n-form-item>
        <n-form-item label="类型"><n-select v-model:value="kbForm.kbType" :options="kbTypeOptions" /></n-form-item>
      </n-form>
      <template #footer>
        <div class="modal-actions">
          <n-button @click="kbDialogVisible = false">取消</n-button>
          <n-button type="primary" @click="submitCreateKb">创建</n-button>
        </div>
      </template>
    </n-modal>

    <n-modal v-model:show="uploadDialogVisible" preset="card" title="上传文档" class="form-modal">
      <n-form label-placement="top">
        <n-form-item label="文件">
          <n-upload :default-upload="false" :max="1" @change="handleUploadChange">
            <n-upload-dragger>
              <n-icon class="upload-icon" :component="CloudArrowUp24Regular" />
              <div>拖拽或选择 PDF / Word / Markdown / HTML / TXT</div>
            </n-upload-dragger>
          </n-upload>
        </n-form-item>
        <n-form-item label="标题"><n-input v-model:value="uploadForm.title" /></n-form-item>
        <n-form-item label="标签"><n-input v-model:value="uploadForm.tags" placeholder="多个标签用逗号分隔" /></n-form-item>
        <n-form-item label="产品线"><n-input v-model:value="uploadForm.productLine" /></n-form-item>
      </n-form>
      <template #footer>
        <div class="modal-actions">
          <n-button @click="uploadDialogVisible = false">取消</n-button>
          <n-button type="primary" @click="submitUpload">上传并入库</n-button>
        </div>
      </template>
    </n-modal>

    <n-drawer v-model:show="documentDrawerVisible" :width="520">
      <n-drawer-content :title="selectedDocument?.title || '文档详情'" closable>
        <dl v-if="selectedDocument" class="detail-list">
          <div><dt>文档 ID</dt><dd>{{ selectedDocument.documentId }}</dd></div>
          <div><dt>知识库</dt><dd>{{ selectedDocument.kbName || selectedKb?.name }}</dd></div>
          <div><dt>版本</dt><dd>v{{ selectedDocument.kbVersion || selectedKb?.currentVersion || 1 }}</dd></div>
          <div><dt>状态</dt><dd>{{ selectedDocument.status || '-' }}</dd></div>
          <div><dt>标签</dt><dd>{{ normalizeTags(selectedDocument.tags).join('、') || '-' }}</dd></div>
        </dl>
        <pre class="preview-text">{{ selectedDocument?.previewText || '原文预览和片段审计接口接入后将在此展示。' }}</pre>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="diffDrawerVisible" :width="560">
      <n-drawer-content title="申请差异预览" closable>
        <n-tabs v-if="activeDiff" value="added">
          <n-tab-pane :tab="`新增 ${activeDiff.added?.length || 0}`" name="added"><DiffList :items="activeDiff.added || []" /></n-tab-pane>
          <n-tab-pane :tab="`修改 ${activeDiff.modified?.length || 0}`" name="modified"><DiffList :items="activeDiff.modified || []" /></n-tab-pane>
          <n-tab-pane :tab="`删除 ${activeDiff.deleted?.length || 0}`" name="deleted"><DiffList :items="activeDiff.deleted || []" /></n-tab-pane>
        </n-tabs>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { NEmpty, type UploadFileInfo } from 'naive-ui'
import { Add24Regular, ArrowLeft24Regular, ArrowSync24Regular, CloudArrowUp24Regular, Eye24Regular, Search24Regular } from '@vicons/fluent'
import SpotlightKbCard from '@/components/SpotlightKbCard.vue'
import { message } from '@/utils/feedback'
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
const viewMode = ref<'list' | 'detail'>('list')
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
const docQuery = reactive({ pageNo: 1, pageSize: 10, keyword: '', status: null as string | null })
const kbForm = reactive({ name: '', description: '', kbType: 'GENERIC_PUBLIC' })
const uploadForm = reactive({ file: null as File | null, title: '', tags: '', productLine: '' })

const docStatusOptions = [
  { label: '已就绪', value: 'READY' },
  { label: '处理中', value: 'UPLOADED' },
  { label: '失败', value: 'FAILED' }
]
const kbTypeOptions = [
  { label: '普通公共知识库', value: 'GENERIC_PUBLIC' },
  { label: '个人知识库', value: 'PERSONAL' }
]

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
  docTotal.value = 0
  selectedDocument.value = null
  documentDrawerVisible.value = false
  uploadDialogVisible.value = false
  viewMode.value = 'list'
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
}

async function openKbDetail(kb: KnowledgeBase) {
  selectedKb.value = kb
  viewMode.value = 'detail'
  docQuery.pageNo = 1
  detailTab.value = 'documents'
  await loadDocuments()
}

function backToList() {
  viewMode.value = 'list'
  selectedKb.value = null
  documents.value = []
  docTotal.value = 0
  selectedDocument.value = null
  documentDrawerVisible.value = false
  uploadDialogVisible.value = false
}

async function loadDocuments() {
  if (!selectedKb.value) return
  loadingDocuments.value = true
  try {
    const result = await fetchKnowledgeBaseDocuments(selectedKb.value.kbId, {
      pageNo: docQuery.pageNo,
      pageSize: docQuery.pageSize,
      keyword: docQuery.keyword,
      status: docQuery.status || ''
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
    message.warning('请填写知识库名称')
    return
  }
  try {
    await createKnowledgeBase({
      scope: activeTab.value,
      name: kbForm.name,
      description: kbForm.description,
      kbType: kbForm.kbType
    })
    message.success('知识库已创建')
    kbDialogVisible.value = false
    await loadKnowledgeBases()
  } catch {
    message.info('后端创建接口暂不可用')
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
    message.success('启用状态已更新')
  } catch {
    message.info('启停接口暂不可用，已保留当前页面状态')
  }
}

function handleUploadChange(options: { file: UploadFileInfo }) {
  uploadForm.file = options.file.file || null
  if (!uploadForm.title && options.file.name) uploadForm.title = options.file.name.replace(/\.[^.]+$/, '')
}

async function submitUpload() {
  if (!selectedKb.value || !uploadForm.file || !uploadForm.title) {
    message.warning('请选择文件并填写标题')
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
    message.success('已上传，状态将进入解析中')
    uploadDialogVisible.value = false
    await loadDocuments()
  } catch {
    message.info('后端上传接口暂不可用')
  }
}

async function submitReindex(row: KnowledgeDocument) {
  if (!selectedKb.value) return
  try {
    await reindexKnowledgeBaseDocument(selectedKb.value.kbId, row.documentId || '')
    message.success('已提交重建索引任务')
  } catch {
    message.info('后端重建接口暂不可用')
  }
}

async function submitContribution() {
  if (!selectedKb.value) return
  try {
    await contributeKnowledgeBase(selectedKb.value.kbId, '申请贡献个人知识库快照')
    message.success('申请已提交')
  } catch {
    message.info('申请接口暂不可用')
  }
}

async function loadApplications() {
  try {
    applications.value = activeTab.value === 'MINE'
      ? await fetchMyContributions()
      : await fetchPendingContributions()
  } catch {
    applications.value = []
    message.info('申请列表接口暂不可用')
  }
}

async function openDiff(applicationId: string) {
  try {
    activeDiff.value = await fetchContributionDiff(applicationId)
    diffDrawerVisible.value = true
  } catch {
    message.info('差异预览接口暂不可用')
  }
}

async function reviewApplication(applicationId: string, approved: boolean) {
  try {
    if (approved) {
      await approveContribution(applicationId, '审批通过，公共快照默认按知识库启用状态控制', false)
    } else {
      await rejectContribution(applicationId, '内容暂不适合进入公共知识库')
    }
    message.success('审批结果已提交')
    await loadApplications()
  } catch {
    message.info('审批接口暂不可用')
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

const DiffList = defineComponent({
  props: {
    items: { type: Array as () => ContributionDiffItem[], required: true }
  },
  setup(props) {
    return () => props.items.length
      ? h('div', { class: 'diff-list' }, props.items.map((item) => h('div', { class: 'diff-item', key: item.sourceDocumentId || item.targetDocumentId }, [
        h('strong', item.title || item.sourceDocumentId || item.targetDocumentId),
        h('span', item.sourceType || '-'),
        h('small', item.fingerprint || item.previousFingerprint)
      ])))
      : h(NEmpty, { description: '暂无差异' })
  }
})
</script>

<style scoped>
.knowledge-page {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 16px;
  min-height: calc(100vh - 124px);
}

.kb-shell {
  min-height: 0;
}

.kb-list-page {
  min-height: 0;
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  overflow: hidden;
}

.kb-detail-panel {
  min-height: 0;
}

.kb-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.kb-page-header h2,
.kb-page-header p {
  margin: 0;
}

.kb-page-header h2 {
  font-size: 22px;
  line-height: var(--leading-tight);
}

.kb-page-header p {
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 14px;
}

.kb-card-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  align-content: start;
  gap: 16px;
  min-height: 0;
  overflow: auto;
  padding: 4px;
}

.fallback-alert {
  margin-bottom: 12px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.detail-title-block {
  display: grid;
  gap: 12px;
}

.back-button {
  justify-self: start;
}

.detail-header h2,
.detail-header p {
  margin: 0;
}

.detail-header p,
.sub-text {
  color: var(--text-muted);
  font-size: 13px;
}

.detail-actions,
.document-toolbar,
.pager,
.row-actions,
.modal-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.document-toolbar {
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.document-toolbar :deep(.n-input) {
  flex: 1 1 260px;
}

.document-toolbar :deep(.n-select) {
  flex: 0 0 150px;
}

.title-link {
  padding: 0;
  color: var(--accent-primary);
  font-weight: 750;
  text-align: left;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.pager {
  justify-content: space-between;
  padding-top: 14px;
  color: var(--text-muted);
}

.detail-list {
  display: grid;
  gap: 10px;
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

.form-modal {
  width: min(92vw, 560px);
}

.modal-actions {
  justify-content: flex-end;
}

.upload-icon {
  color: var(--accent-primary);
  font-size: 34px;
}

.preview-text {
  min-height: 240px;
  margin-top: 14px;
  padding: 14px;
  white-space: pre-wrap;
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  background: var(--bg-surface-muted);
  line-height: var(--leading-relaxed);
}

.diff-list {
  display: grid;
  gap: 10px;
}

.diff-item {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  background: var(--bg-surface-muted);
}

.diff-item span,
.diff-item small {
  color: var(--text-muted);
}

.kb-view-enter-active,
.kb-view-leave-active {
  transition:
    opacity var(--duration-base) var(--ease-out-quint),
    transform var(--duration-spring) var(--ease-spring),
    filter var(--duration-base) var(--ease-out-quint);
}

.kb-view-enter-from,
.kb-view-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.99);
  filter: blur(3px);
}

.kb-view-enter-to,
.kb-view-leave-from {
  opacity: 1;
  transform: translateY(0) scale(1);
  filter: blur(0);
}

@media (max-width: 767px) {
  .knowledge-page {
    min-height: calc(100vh - 60px);
    padding: 12px;
  }

  .detail-header,
  .detail-actions,
  .kb-page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .kb-card-list {
    grid-template-columns: 1fr;
  }
}
</style>
