<template>
  <div class="knowledge-page">
    <section class="toolbar">
      <div class="scope-block">
        <el-segmented v-model="scope" :options="scopeOptions" @change="reloadDocuments" />
        <span class="scope-hint">{{ scope === 'public' ? '公共知识库面向团队检索' : '我的知识库仅本人可见' }}</span>
      </div>
      <div class="filter-row">
        <el-input v-model="query.keyword" class="search-input" clearable placeholder="搜索标题、标签、摘要" :prefix-icon="Search" @keyup.enter="reloadDocuments" />
        <el-select v-model="query.status" clearable placeholder="状态" @change="reloadDocuments">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.sourceType" clearable placeholder="类型" @change="reloadDocuments">
          <el-option v-for="item in sourceTypeOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="query.productLine" clearable placeholder="产品线" @change="reloadDocuments">
          <el-option v-for="item in productLineOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-date-picker v-model="updatedRange" type="daterange" start-placeholder="开始时间" end-placeholder="结束时间" value-format="YYYY-MM-DD" @change="reloadDocuments" />
        <el-button :icon="Refresh" @click="reloadDocuments">刷新</el-button>
        <el-button type="primary" :icon="Upload" @click="uploadDialogVisible = true">上传文档</el-button>
      </div>
    </section>

    <section v-if="scope === 'personal'" class="personal-summary">
      <div><strong>{{ personalStats.used }}</strong><span>已用容量</span></div>
      <div><strong>{{ personalStats.total }}</strong><span>文档数</span></div>
      <div><strong>{{ personalStats.ready }}</strong><span>可检索</span></div>
    </section>

    <section class="workspace">
      <aside class="category-panel">
        <div class="panel-heading">分类</div>
        <el-tree
          :data="categoryTree"
          node-key="id"
          default-expand-all
          highlight-current
          :props="{ label: 'name', children: 'children' }"
          @node-click="handleCategoryClick"
        />
      </aside>

      <main class="document-panel">
        <el-alert
          v-if="usingFallback"
          class="fallback-alert"
          type="info"
          show-icon
          :closable="false"
          title="后端知识库接口暂不可用，当前展示本地示例数据。"
        />
        <el-table
          v-loading="loading"
          :data="visibleDocuments"
          class="document-table"
          empty-text="暂无知识文档，请调整筛选条件或上传文档"
          @row-click="openDetail"
        >
          <el-table-column label="标题" min-width="220">
            <template #default="{ row }">
              <button class="title-link" type="button" @click.stop="openDetail(row)">{{ row.title }}</button>
              <div class="sub-text">{{ row.filename || row.originalFilename || '知识文档' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="Scope" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.scope === 'personal' ? 'success' : 'primary'">{{ row.scope === 'personal' ? '个人' : '公共' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sourceType" label="类型" width="100" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="categoryName" label="分类" min-width="120" />
          <el-table-column label="标签" min-width="190">
            <template #default="{ row }">
              <el-tag v-for="tag in normalizeTags(row.tags).slice(0, 3)" :key="tag" class="tag-item" size="small" effect="plain">{{ tag }}</el-tag>
              <el-tag v-if="normalizeTags(row.tags).length > 3" size="small" effect="plain">+{{ normalizeTags(row.tags).length - 3 }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="productLine" label="产品线" width="120" />
          <el-table-column prop="updatedAt" label="更新时间" min-width="150" />
          <el-table-column prop="hitCount" label="命中次数" width="100" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-tooltip content="预览">
                <el-button :icon="View" circle text @click.stop="openDetail(row)" />
              </el-tooltip>
              <el-tooltip content="重建索引">
                <el-button :icon="RefreshRight" circle text @click.stop="submitReindex(row)" />
              </el-tooltip>
              <el-tooltip content="删除">
                <el-button :icon="Delete" circle text type="danger" @click.stop="confirmDelete(row)" />
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <span>共 {{ total }} 条</span>
          <el-pagination v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" layout="prev, pager, next, sizes" :total="total" @change="reloadDocuments" />
        </div>
      </main>
    </section>

    <el-drawer v-model="detailVisible" size="48%" :title="selectedDocument?.title || '文档详情'">
      <el-tabs v-if="selectedDocument" v-model="activeTab">
        <el-tab-pane label="基础信息" name="base">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="标题">{{ selectedDocument.title }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ selectedDocument.sourceType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="大小">{{ formatSize(selectedDocument.fileSize) }}</el-descriptions-item>
            <el-descriptions-item label="分类">{{ selectedDocument.categoryName || '未分类' }}</el-descriptions-item>
            <el-descriptions-item label="标签">{{ normalizeTags(selectedDocument.tags).join('、') || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Scope">{{ selectedDocument.scope === 'personal' ? '个人' : '公共' }}</el-descriptions-item>
            <el-descriptions-item label="创建者">{{ selectedDocument.creatorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ selectedDocument.updatedAt || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="处理状态" name="status">
          <el-steps :active="stepActive(selectedDocument.status)" finish-status="success" simple>
            <el-step title="解析中" />
            <el-step title="分块中" />
            <el-step title="向量化" />
            <el-step title="已就绪" />
          </el-steps>
          <div class="status-grid">
            <span>当前状态：{{ statusLabel(selectedDocument.status) }}</span>
            <span>失败原因：{{ selectedDocument.failureReason || '无' }}</span>
            <span>耗时：{{ selectedDocument.parseDurationMs ? `${selectedDocument.parseDurationMs} ms` : '-' }}</span>
            <span>Chunk 数：{{ selectedDocument.chunkCount ?? '-' }}</span>
            <span>Embedding 模型：{{ selectedDocument.embeddingModel || '-' }}</span>
          </div>
        </el-tab-pane>
        <el-tab-pane label="文档预览" name="preview">
          <pre class="preview-text">{{ selectedDocument.previewText || '暂无预览内容，后端解析完成后将在此展示文本摘要。' }}</pre>
        </el-tab-pane>
        <el-tab-pane label="知识片段" name="chunks">
          <el-timeline>
            <el-timeline-item v-for="chunk in sampleChunks" :key="chunk.id" :timestamp="chunk.meta">
              {{ chunk.content }}
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>
        <el-tab-pane label="检索测试" name="retrieval">
          <div class="retrieval-box">
            <el-input v-model="retrievalQuestion" placeholder="输入测试问题" />
            <el-button type="primary" @click="runRetrievalTest">测试召回</el-button>
          </div>
          <el-empty v-if="retrievalResults.length === 0" description="暂无召回结果" />
          <div v-for="item in retrievalResults" :key="item.content" class="retrieval-result">
            <strong>Score {{ item.score ?? '-' }}</strong>
            <p>{{ item.content }}</p>
            <small>{{ item.source || selectedDocument.title }}</small>
          </div>
        </el-tab-pane>
        <el-tab-pane label="使用反馈" name="feedback">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="命中次数">{{ selectedDocument.hitCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="有帮助率">示例数据 92%</el-descriptions-item>
            <el-descriptions-item label="低分回答关联">暂无</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="操作历史" name="history">
          <el-timeline>
            <el-timeline-item timestamp="刚刚">查看文档详情</el-timeline-item>
            <el-timeline-item timestamp="2026-04-26">文档入库并启用检索</el-timeline-item>
          </el-timeline>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <el-dialog v-model="uploadDialogVisible" title="上传文档" width="560px">
      <el-form label-position="top">
        <el-form-item label="Scope">
          <el-segmented v-model="uploadForm.scope" :options="scopeOptions" />
        </el-form-item>
        <el-form-item label="文件">
          <el-upload drag :auto-upload="false" :limit="1" :on-change="handleUploadChange">
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div>拖拽或选择 PDF / Word / Markdown / HTML / TXT</div>
          </el-upload>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="uploadForm.title" /></el-form-item>
        <el-form-item label="分类"><el-select v-model="uploadForm.categoryId" clearable><el-option v-for="item in flatCategories" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="标签"><el-input v-model="uploadForm.tags" placeholder="多个标签用逗号分隔" /></el-form-item>
        <el-form-item label="产品线"><el-input v-model="uploadForm.productLine" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitUpload">上传并入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { UploadFile } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Refresh, RefreshRight, Search, Upload, UploadFilled, View } from '@element-plus/icons-vue'
import {
  deleteKnowledgeDocument,
  fetchKnowledgeCategories,
  fetchKnowledgeDocuments,
  fetchKnowledgeSelectable,
  reindexKnowledgeDocument,
  testKnowledgeRetrieval,
  uploadKnowledgeDocument
} from '@/api/biz'
import type { KnowledgeCategory, KnowledgeDocument, KnowledgeScope } from '@/types/biz'

type CategoryNode = KnowledgeCategory & { id: string | number; name: string; children?: CategoryNode[] }

const scope = ref<KnowledgeScope>('public')
const loading = ref(false)
const usingFallback = ref(false)
const documents = ref<KnowledgeDocument[]>([])
const categories = ref<KnowledgeCategory[]>([])
const total = ref(0)
const updatedRange = ref<[string, string] | null>(null)
const detailVisible = ref(false)
const selectedDocument = ref<KnowledgeDocument | null>(null)
const activeTab = ref('base')
const uploadDialogVisible = ref(false)
const selectedCategoryId = ref<string | number | undefined>()
const retrievalQuestion = ref('')
const retrievalResults = ref<Array<{ content: string; score?: number; source?: string }>>([])

const query = reactive({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  status: '',
  sourceType: '',
  productLine: ''
})

const uploadForm = reactive({
  scope: 'public' as KnowledgeScope,
  file: null as File | null,
  title: '',
  categoryId: '' as string | number,
  tags: '',
  productLine: ''
})

const scopeOptions = [
  { label: '公共知识库', value: 'public' },
  { label: '我的知识库', value: 'personal' }
]

const statusOptions = [
  { label: '处理中', value: 'PROCESSING' },
  { label: '已就绪', value: 'READY' },
  { label: '失败', value: 'FAILED' },
  { label: '已下线', value: 'OFFLINE' }
]

const fallbackDocuments: KnowledgeDocument[] = [
  {
    id: 'doc_public_refund',
    documentId: 'doc_public_refund',
    title: '售后退款政策 FAQ',
    filename: 'refund-policy.md',
    sourceType: 'Markdown',
    categoryId: 'cat_after_sales',
    categoryName: '售后政策',
    productLine: '商城',
    tags: ['退款', '售后', '时效'],
    status: 'READY',
    enabled: true,
    scope: 'public',
    hitCount: 128,
    chunkCount: 24,
    fileSize: 18200,
    creatorName: '运营管理员',
    embeddingModel: 'bge-m3',
    parseDurationMs: 2360,
    updatedAt: '2026-04-26 10:20',
    previewText: '退款申请审核通过后，原路退回通常需要 1-5 个工作日，具体到账时间以支付渠道为准。'
  },
  {
    id: 'doc_public_delivery',
    documentId: 'doc_public_delivery',
    title: '物流异常处理手册',
    filename: 'delivery.html',
    sourceType: 'HTML',
    categoryId: 'cat_delivery',
    categoryName: '物流配送',
    productLine: '商城',
    tags: ['物流', '异常件', '补发'],
    status: 'PROCESSING',
    enabled: true,
    scope: 'public',
    hitCount: 46,
    chunkCount: 12,
    fileSize: 42000,
    creatorName: '客服主管',
    updatedAt: '2026-04-25 17:12'
  },
  {
    id: 'doc_personal_notes',
    documentId: 'doc_personal_notes',
    title: '个人常用回复话术',
    filename: 'personal-replies.txt',
    sourceType: 'TXT',
    categoryId: 'cat_personal',
    categoryName: '个人资料',
    productLine: '客服',
    tags: ['话术', '跟进'],
    status: 'READY',
    enabled: true,
    scope: 'personal',
    hitCount: 9,
    chunkCount: 8,
    fileSize: 6800,
    creatorName: '当前用户',
    updatedAt: '2026-04-24 09:00',
    previewText: '针对订单延迟，先确认订单号和物流节点，再给出预计处理时间。'
  }
]

const fallbackCategories: CategoryNode[] = [
  { id: 'cat_after_sales', name: '售后政策' },
  { id: 'cat_delivery', name: '物流配送' },
  { id: 'cat_personal', name: '个人资料' }
]

const sourceTypeOptions = computed(() => ['PDF', 'Word', 'Markdown', 'HTML', 'TXT'])
const productLineOptions = computed(() => Array.from(new Set(documents.value.map((item) => item.productLine).filter(Boolean))) as string[])
const flatCategories = computed(() => flattenCategories(categories.value.length ? categories.value : fallbackCategories))
const categoryTree = computed<CategoryNode[]>(() => [
  { id: 'all', name: '全部' },
  { id: 'uncategorized', name: '未分类' },
  ...flatCategories.value
])
const visibleDocuments = computed(() => documents.value)
const personalStats = computed(() => {
  const personalDocs = fallbackDocuments.filter((item) => item.scope === 'personal')
  const used = personalDocs.reduce((sum, item) => sum + (item.fileSize || 0), 0)
  return {
    used: formatSize(used),
    total: String(personalDocs.length),
    ready: String(personalDocs.filter((item) => item.status === 'READY').length)
  }
})
const sampleChunks = computed(() => [
  { id: 1, meta: 'chunk #1', content: selectedDocument.value?.previewText || '解析文本片段将在入库完成后展示。' },
  { id: 2, meta: 'chunk #2', content: '包含父子 chunk、来源页码、标签等元数据。' }
])

onMounted(async () => {
  await Promise.all([loadSelectable(), reloadDocuments()])
})

async function loadSelectable() {
  try {
    const [selectable, categoryResult] = await Promise.all([fetchKnowledgeSelectable(), fetchKnowledgeCategories(scope.value)])
    categories.value = categoryResult?.length ? categoryResult : selectable.categories || []
  } catch {
    usingFallback.value = true
    categories.value = fallbackCategories
  }
}

async function reloadDocuments() {
  loading.value = true
  try {
    const result = await fetchKnowledgeDocuments(scope.value, {
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword,
      categoryId: selectedCategoryId.value,
      status: query.status,
      sourceType: query.sourceType,
      productLine: query.productLine,
      updatedFrom: updatedRange.value?.[0],
      updatedTo: updatedRange.value?.[1]
    })
    documents.value = result.records
    total.value = result.total
    usingFallback.value = false
  } catch {
    const filtered = fallbackDocuments.filter((item) => {
      const text = `${item.title}${normalizeTags(item.tags).join('')}${item.previewText || ''}`.toLowerCase()
      return item.scope === scope.value
        && (!query.keyword || text.includes(query.keyword.toLowerCase()))
        && (!query.status || item.status === query.status)
        && (!query.sourceType || item.sourceType === query.sourceType)
        && (!query.productLine || item.productLine === query.productLine)
        && (!selectedCategoryId.value || item.categoryId === selectedCategoryId.value)
    })
    documents.value = filtered
    total.value = filtered.length
    usingFallback.value = true
  } finally {
    loading.value = false
  }
}

function handleCategoryClick(node: CategoryNode) {
  selectedCategoryId.value = node.id === 'all' ? undefined : node.id === 'uncategorized' ? 'uncategorized' : node.id
  query.pageNo = 1
  reloadDocuments()
}

function openDetail(row: KnowledgeDocument) {
  selectedDocument.value = row
  activeTab.value = 'base'
  retrievalResults.value = []
  detailVisible.value = true
}

function normalizeTags(tags?: string[] | string) {
  if (!tags) return []
  return Array.isArray(tags) ? tags : tags.split(',').map((item) => item.trim()).filter(Boolean)
}

function flattenCategories(items: KnowledgeCategory[]): CategoryNode[] {
  return items.flatMap((item) => {
    const id = item.id ?? item.categoryId ?? item.code ?? item.name
    const node: CategoryNode = { ...item, id, name: item.name, children: item.children ? flattenCategories(item.children) : undefined }
    return [node, ...flattenCategories(item.children || [])]
  })
}

function statusLabel(status?: string) {
  const found = statusOptions.find((item) => item.value === status)
  return found?.label || status || '未知'
}

function statusTagType(status?: string) {
  if (status === 'READY') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'OFFLINE') return 'info'
  return 'warning'
}

function stepActive(status?: string) {
  if (status === 'READY') return 4
  if (status === 'FAILED') return 1
  if (status === 'PROCESSING') return 2
  return 1
}

function formatSize(size?: number) {
  if (!size) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

async function submitReindex(row: KnowledgeDocument) {
  try {
    await reindexKnowledgeDocument(scope.value, row.documentId || row.id || '')
    ElMessage.success('已提交重建索引任务')
  } catch {
    ElMessage.info('后端重建接口暂不可用，已保留当前页面状态')
  }
}

async function confirmDelete(row: KnowledgeDocument) {
  await ElMessageBox.confirm(`确认删除“${row.title}”？删除后会从知识库检索中移除，并清理相关向量索引。`, '删除确认', {
    type: 'warning'
  })
  try {
    await deleteKnowledgeDocument(scope.value, row.documentId || row.id || '')
    ElMessage.success('已删除文档')
    await reloadDocuments()
  } catch {
    documents.value = documents.value.filter((item) => item !== row)
    total.value = Math.max(0, total.value - 1)
    ElMessage.info('后端删除接口暂不可用，已从当前列表移除')
  }
}

function handleUploadChange(file: UploadFile) {
  uploadForm.file = file.raw || null
  if (!uploadForm.title) uploadForm.title = file.name.replace(/\.[^.]+$/, '')
}

async function submitUpload() {
  if (!uploadForm.file || !uploadForm.title) {
    ElMessage.warning('请先选择文件并填写标题')
    return
  }
  try {
    await uploadKnowledgeDocument(uploadForm.scope, {
      file: uploadForm.file,
      title: uploadForm.title,
      sourceType: uploadForm.file.name.split('.').pop()?.toUpperCase() || 'TXT',
      categoryId: uploadForm.categoryId,
      productLine: uploadForm.productLine,
      tags: uploadForm.tags
    })
    ElMessage.success('已上传，状态将进入解析中')
  } catch {
    ElMessage.info('后端上传接口暂不可用，已保留本地交互流程')
  }
  uploadDialogVisible.value = false
  await reloadDocuments()
}

async function runRetrievalTest() {
  if (!selectedDocument.value || !retrievalQuestion.value.trim()) return
  try {
    retrievalResults.value = await testKnowledgeRetrieval(selectedDocument.value.documentId || selectedDocument.value.id || '', retrievalQuestion.value.trim())
  } catch {
    retrievalResults.value = [
      { score: 0.86, content: selectedDocument.value.previewText || '本地示例召回片段。', source: selectedDocument.value.title }
    ]
  }
}
</script>

<style scoped>
.knowledge-page {
  display: grid;
  gap: 16px;
  min-height: calc(100vh - 124px);
}

.toolbar,
.workspace,
.personal-summary {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
}

.toolbar {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.scope-block,
.filter-row,
.pager,
.retrieval-box {
  display: flex;
  align-items: center;
  gap: 12px;
}

.scope-hint,
.sub-text {
  color: #64748b;
  font-size: 13px;
}

.search-input {
  width: 260px;
}

.personal-summary {
  display: flex;
  gap: 32px;
  padding: 14px 18px;
}

.personal-summary div {
  display: grid;
  gap: 3px;
}

.personal-summary strong {
  color: #0f172a;
  font-size: 20px;
}

.personal-summary span {
  color: #64748b;
  font-size: 13px;
}

.workspace {
  display: grid;
  grid-template-columns: 236px minmax(0, 1fr);
  min-height: 0;
}

.category-panel {
  padding: 16px;
  border-right: 1px solid #e5e7eb;
}

.panel-heading {
  margin-bottom: 12px;
  color: #0f172a;
  font-weight: 700;
}

.document-panel {
  min-width: 0;
  padding: 16px;
}

.fallback-alert {
  margin-bottom: 12px;
}

.document-table {
  width: 100%;
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

.tag-item {
  margin-right: 4px;
}

.pager {
  justify-content: space-between;
  padding-top: 14px;
  color: #64748b;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.preview-text {
  white-space: pre-wrap;
  min-height: 260px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
  color: #334155;
  line-height: 1.7;
}

.retrieval-box {
  margin-bottom: 14px;
}

.retrieval-result {
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.retrieval-result p {
  margin: 6px 0;
  line-height: 1.7;
}

.upload-icon {
  color: #2563eb;
  font-size: 32px;
}

@media (max-width: 960px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .category-panel {
    border-right: 0;
    border-bottom: 1px solid #e5e7eb;
  }

  .filter-row {
    flex-wrap: wrap;
  }
}
</style>
