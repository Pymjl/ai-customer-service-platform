# biz-service 软件设计文档（SDD）

**版本**：1.0.0  
**日期**：2026-04-26  
**服务端口**：`18083`  
**技术基线**：Java 21 · Spring Boot 3.5.13 · Spring MVC · MyBatis · PostgreSQL · Redis · RocketMQ（可选）

---

## 1. 目标与范围

`biz-service` 是业务管理服务，负责客服会话、消息历史、知识库管理、知识库入库任务编排、AI function call 业务防腐接口和知识库页面所需的业务查询接口。

本项目不再额外拆分 `knowledge-service`。知识库管理能力统一放入 `biz-service`，Python `ai-engine` 只提供内网 RAG 处理接口。

职责边界：

| 能力 | 归属 |
|---|---|
| 会话列表、创建、后续会话管理 | `biz-service` |
| 消息列表、消息完成内部接口、消息持久化 | `biz-service` |
| 公共知识库/个人知识库管理 API | `biz-service` |
| 知识库权限、元数据、分类、标签、状态、审计 | `biz-service` |
| 原始文件上传与 MinIO 对象路径管理 | `biz-service` |
| 调用 Python 入库、重建索引、删除向量索引 | `biz-service -> ai-engine` |
| AI Function Call 防腐层 | `ai-engine -> biz-service -> 其它业务服务` |
| 文档解析、分块、embedding、向量检索、rerank | `ai-engine` |
| SSE 流式对话入口 | `stream-service` |

---

## 2. 外部依赖

| 依赖 | 用途 | dev 配置 |
|---|---|---|
| PostgreSQL | 会话、消息、知识库元数据 | `localhost:5432/aicsp_biz?currentSchema=biz_service` |
| Redis | 后续缓存、幂等锁、临时状态 | `localhost:6379/2` |
| RocketMQ | 后续可用于知识库异步入库任务；不作为对话完成主链路 | `localhost:9876` |
| MinIO | 知识库原始文件存储 | `http://localhost:9000` |
| ai-engine | 内网 RAG 处理接口 | `http://localhost:8000` |
| gateway-service | 统一入口、鉴权、身份头注入 | `http://localhost:18080` |
| 其它业务服务 | 订单、物流、CRM 等 function call 目标服务 | 内网 HTTP |

Redis Key 必须设置 TTL；幂等锁、临时状态和短期缓存不得无期限保留，长期业务数据必须写入 PostgreSQL。

---

## 3. 统一接口规范

### 3.1 访问入口

所有业务接口通过 `gateway-service` 暴露：

```text
http://localhost:18080/api/sessions/**
http://localhost:18080/api/messages/**
http://localhost:18080/api/knowledge/**
```

`biz-service` 不直接面向公网暴露。

### 3.2 身份头

`biz-service` 只信任网关注入后的身份头：

| Header | 必填 | 说明 |
|---|---|---|
| `X-User-Id` | 是 | 当前用户 ID |
| `X-Tenant-Id` | 是 | 当前租户 ID |
| `X-User-Roles` | 是 | 角色列表，逗号分隔 |
| `X-Trace-Id` | 建议 | 全链路追踪 ID |
| `X-Internal-Token` | 内部调用时必填 | HTTP Header；`ai-engine` 调用 `/internal/chat/message-completed`、`/internal/tools/{toolName}`、知识库回调等内部接口时使用；必须校验独立的 biz internal token |

前端请求体中的 `userId`、`tenantId`、`roles` 不能作为权限依据。后续会话创建接口也应逐步改为从身份头读取用户与租户，避免客户端伪造。

内部 token 约束：

- `biz-service` 对 `/internal/**` 校验的 token 对应 Python 引擎侧 `AICSP_BIZ_INTERNAL_TOKEN`，不得与 `stream-service -> ai-engine` 使用的 `AICSP_STREAM_INTERNAL_TOKEN` 共用。
- 生产环境中 biz internal token 必须使用独立密钥材料生成，并支持独立轮换；轮换时不得影响 `stream-service` 调用 Python 流式入口。
- token 校验失败必须返回 `401/403`，并记录安全审计日志，日志不得输出 token 原文。
- 所有内部 token 只允许放在 HTTP Header `X-Internal-Token` 中，不允许放在 query string、请求体或日志字段中。

内部调用相关配置建议：

| 配置项 | 说明 |
|---|---|
| `internal.biz-token` | `biz-service` 校验 `/internal/chat/message-completed`、`/internal/tools/{toolName}`、知识库回调使用；对应 Python `AICSP_BIZ_INTERNAL_TOKEN` |
| `python.engine.base-url` | `biz-service` 调用 `ai-engine /internal/rag/**` 的基础地址 |
| `python.engine.internal-token` | `biz-service` 调用 `ai-engine /internal/rag/**` 时发送的 `X-Internal-Token`；不得复用 `internal.biz-token` |

### 3.3 统一响应体

除文件下载、SSE 等特殊接口外，统一返回：

```json
{
  "success": true,
  "message": "success",
  "data": {}
}
```

错误响应：

```json
{
  "success": false,
  "message": "权限不足",
  "data": null
}
```

### 3.4 分页规范

列表接口统一使用：

| 参数 | 默认 | 说明 |
|---|---:|---|
| `pageNo` | `1` | 页码，从 1 开始 |
| `pageSize` | `20` | 每页数量，最大 100 |
| `keyword` | 空 | 搜索关键词 |
| `sort` | `updatedAt_desc` | 排序字段 |

分页响应：

```json
{
  "records": [],
  "pageNo": 1,
  "pageSize": 20,
  "total": 0
}
```

---

## 4. 当前会话与消息接口

### 4.1 会话接口

| 方法 | 路径 | 权限 | 当前状态 | 说明 |
|---|---|---|---|---|
| `GET` | `/api/sessions` | 登录用户 | 已实现 | 查询当前用户可见会话列表 |
| `POST` | `/api/sessions` | 登录用户 | 已实现 | 创建会话 |
| `PUT` | `/api/sessions/{sessionId}` | 会话拥有者 | 规划 | 修改标题、状态 |
| `DELETE` | `/api/sessions/{sessionId}` | 会话拥有者 | 规划 | 软删除会话 |

当前创建请求：

```json
{
  "sessionId": "s_001",
  "userId": "u_001",
  "tenantId": "default",
  "title": "售后咨询"
}
```

安全待修复项（P0）：当前创建请求仍包含 `userId`、`tenantId`，实现必须改为由身份头覆盖，不信任请求体。完成前该接口存在客户端伪造身份风险，不能作为普通后续演进处理。

会话响应：

```json
{
  "sessionId": "s_001",
  "title": "售后咨询",
  "status": 1,
  "createdAt": "2026-04-26T20:00:00+08:00"
}
```

### 4.2 消息接口

| 方法 | 路径 | 权限 | 当前状态 | 说明 |
|---|---|---|---|---|
| `GET` | `/api/messages?sessionId=s_001` | 会话拥有者 | 已实现 | 查询消息列表 |
| `GET` | `/api/messages/{messageId}` | 会话拥有者 | 规划 | 查询消息详情、引用来源 |
| `DELETE` | `/api/messages/{messageId}` | 会话拥有者 | 规划 | 软删除消息 |

消息响应：

```json
{
  "messageId": "m_001",
  "sessionId": "s_001",
  "userMsg": "退款多久到账？",
  "aiReply": "一般 1-3 个工作日到账。",
  "status": "COMPLETED",
  "traceId": "trace-001",
  "createdAt": "2026-04-26T20:00:00+08:00"
}
```

### 4.3 消息完成内部接口

`ai-engine` 在模型流式生成结束后调用 `biz-service` 内部接口，`biz-service` 幂等持久化会话和消息。`stream-service` 不发布消息完成事件，不参与消息落库。

接口：

```text
POST /internal/chat/message-completed
```

请求头：

| Header | 必填 | 说明 |
|---|---|---|
| `X-Internal-Token` | 是 | biz internal token，对应 Python `AICSP_BIZ_INTERNAL_TOKEN` |
| `X-Trace-Id` | 建议 | 链路追踪 |

请求体：

```json
{
  "messageId": "m_001",
  "sessionId": "s_001",
  "userId": "u_001",
  "tenantId": "default",
  "userMessage": "退款多久到账？",
  "aiReply": "一般 1-3 个工作日到账。",
  "status": "COMPLETED",
  "traceId": "trace-001",
  "timestamp": 1777204800000
}
```

幂等规则：

- `messageId` 是消息完成回调的幂等键，不能为空。
- `cs_session.session_id` 唯一。
- `cs_message.message_id` 唯一。
- 消费重复消息时使用 `INSERT ... ON CONFLICT DO NOTHING` 或等价逻辑。
- 重复提交同一 `messageId` 时只能写入一次；重复请求应返回已持久化结果或幂等成功，不得追加重复 AI 消息。

状态：

| status | 说明 |
|---|---|
| `COMPLETED` | 正常完成 |
| `FAILED` | 模型、工具或生成链路失败 |
| `INTERRUPTED` | 超时、长度截断、用户中断等 |

---

## 4.4 Function Call 防腐接口

Python `ai-engine` 不直接调用订单、物流、CRM 等业务服务，统一调用 `biz-service` 的内部工具接口。`biz-service` 负责参数校验、权限判断、脱敏、幂等、审计和对其它服务的 HTTP 调用。

统一路径：

```text
POST /internal/tools/{toolName}
```

请求头：

| Header | 必填 | 说明 |
|---|---|---|
| `X-Internal-Token` | 是 | biz internal token，对应 Python `AICSP_BIZ_INTERNAL_TOKEN` |
| `X-Trace-Id` | 建议 | 链路追踪 |

工具请求：

```json
{
  "toolCallId": "tool_call_001",
  "sessionId": "s_001",
  "messageId": "m_001",
  "userId": "u_001",
  "tenantId": "default",
  "arguments": {
    "orderId": "O001"
  },
  "idempotencyKey": "tool_call_001"
}
```

工具响应：

```json
{
  "toolCallId": "tool_call_001",
  "success": true,
  "data": {
    "orderId": "O001",
    "status": "PAID"
  },
  "errorCode": null,
  "message": "success"
}
```

第一阶段工具：

| toolName | 方法 | 说明 |
|---|---|---|
| `order.query` | `POST` | 查询订单信息 |
| `logistics.query` | `POST` | 查询物流信息 |
| `crm.query` | `POST` | 查询客户信息 |

工具约束：

- 只读工具可自动执行。
- 写入工具必须带 `idempotencyKey`，且需要用户确认状态。
- 高风险工具只返回“需要人工处理”或进入人工审批，不直接执行。
- 所有工具出参必须脱敏后返回给 `ai-engine`。
- 工具调用日志写入 `biz-service`，用于审计和排障。
- `/internal/tools/{toolName}` 必须使用独立的 biz internal token 校验，不得接受 `stream-service` 调用 Python 引擎的 token。

---

## 5. 知识库管理设计

### 5.1 Scope 与权限

知识库分为公共知识库和个人知识库：

| Scope | 可见性 | 管理权限 |
|---|---|---|
| `PUBLIC` | 所有登录用户只读 | `ADMIN`、`SUPER_ADMIN` 可增删改、发布、下线、重建索引、启用、禁用 |
| `PERSONAL` | 仅本人可见 | 本人可增删改自己的个人文档 |

客服案例库是公共知识库的一种，使用 `scope=PUBLIC`、`source_type=CASE_LIBRARY` 表示。它比较特殊：默认启用；普通用户只能查看、检索和在智能问答时选择启用；管理员和超级管理员可维护内容；但客服案例库的全局启用/禁用只能由超级管理员操作，管理员不能禁用或重新启用客服案例库。

普通用户在智能问答时可以选择公共 `READY + enabled` 文档和自己的个人 `READY + enabled` 文档。后端必须用身份头重新计算可访问集合，前端选择只作为候选条件。

### 5.2 文档状态

| 状态 | 说明 | 是否可检索 |
|---|---|---|
| `UPLOADED` | 已上传原始文件和元数据 | 否 |
| `PARSING` | Python 正在解析 | 否 |
| `CHUNKING` | Python 正在分块 | 否 |
| `EMBEDDING` | Python 正在向量化 | 否 |
| `INDEXING` | Python 正在入库 | 否 |
| `READY` | 已就绪 | 是 |
| `FAILED` | 入库失败 | 否 |
| `DELETED` | 已删除 | 否 |

`status` 与 `enabled` 的职责边界：

- `status` 只描述文档入库生命周期与删除状态：`UPLOADED -> PARSING -> CHUNKING -> EMBEDDING -> INDEXING -> READY -> FAILED/DELETED`。
- 管理员启用/禁用开关只使用 `enabled` 表达，不再使用 `status=DISABLED`。
- “已就绪但被禁用/下线”统一表示为 `status=READY AND enabled=false`。
- 可检索条件必须同时满足 `status=READY AND enabled=true`。

### 5.3 推荐表结构

在 `aicsp_biz.biz_service` 下新增业务元数据表：

| 表 | 说明 |
|---|---|
| `kb_document` | 文档元数据、scope、owner、状态、MinIO 路径 |
| `kb_category` | 分类树，公共和个人共用表，通过 scope 区分 |
| `kb_tag` | 标签字典 |
| `kb_document_tag` | 文档标签关联 |
| `kb_ingestion_task` | 入库任务状态、失败原因、重试次数；保存 `task_id` 与 `document_id` 映射 |
| `kb_operation_log` | 上传、编辑、发布、下线、删除、重建索引操作记录 |
| `kb_contribution_application` | 个人知识贡献到公共知识库的审批流 |

核心字段建议：

```sql
document_id      VARCHAR(64)  NOT NULL,
tenant_id        VARCHAR(64)  NOT NULL,
scope            VARCHAR(16)  NOT NULL,
owner_user_id    VARCHAR(64),
title            VARCHAR(255) NOT NULL,
source_type      VARCHAR(32)  NOT NULL,
object_path      VARCHAR(512) NOT NULL,
category_id      VARCHAR(64),
product_line     VARCHAR(64),
status           VARCHAR(32)  NOT NULL,
enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
created_by       BIGINT       NOT NULL DEFAULT 0,
created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_by       BIGINT       NOT NULL DEFAULT 0,
updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
deleted          BOOLEAN      NOT NULL DEFAULT FALSE
```

字段约束：

- `owner_user_id` 必须允许 `NULL`：公共文档使用 `NULL`，个人文档必须填写实际用户 ID，不得使用 `"0"` 等哨兵值。
- 建议增加约束：`scope='PUBLIC'` 时 `owner_user_id IS NULL`，`scope='PERSONAL'` 时 `owner_user_id IS NOT NULL`。
- `enabled` 默认值为 `TRUE` 只适用于普通上传文档；贡献审批通过生成的公共副本必须由审批通过处理器在应用层显式写入 `enabled=false`，不能省略字段后依赖数据库默认值。
- 贡献审批通过处理器的插入语句、Mapper 或 Repository 方法必须把 `enabled` 作为必填入参；缺少该字段应视为实现错误并由单元测试覆盖，避免公共副本直接进入可检索状态。

`kb_ingestion_task` 关键字段建议：

```sql
task_id        VARCHAR(64)  NOT NULL PRIMARY KEY,
document_id    VARCHAR(64)  NOT NULL,
task_type      VARCHAR(32)  NOT NULL, -- INGEST / REINDEX / DELETE
status         VARCHAR(32)  NOT NULL,
retry_count    INTEGER      NOT NULL DEFAULT 0,
error_message  TEXT,
created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
```

`document_id` 与 `task_id` 是一对多关系：首次入库、重建索引、删除向量都生成新的 `task_id`。`GET /api/knowledge/documents/{documentId}/ingestion` 查询该 `documentId` 最新一条非终态任务；如无非终态任务，则返回最近一次任务结果。

`kb_ingestion_task.status` 合法值：

| 状态 | 说明 |
|---|---|
| `SUBMITTED` | `biz-service` 已生成任务并准备提交给 `ai-engine` |
| `SUBMITTING_UNKNOWN` | 提交调用超时或连接中断，无法确认 Python 是否已接收任务 |
| `PARSING` | Python 正在解析 |
| `CHUNKING` | Python 正在分块 |
| `EMBEDDING` | Python 正在向量化 |
| `INDEXING` | Python 正在写入向量索引 |
| `READY` | 入库完成 |
| `FAILED_RETRYABLE` | 可重试失败，例如提交超时后确认未接收或临时依赖不可用 |
| `FAILED` | 不可自动重试失败，需要用户或管理员处理 |
| `DELETED` | 对应索引已删除 |

---

## 6. 知识库 API 规范

### 6.1 公共知识库

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/api/knowledge/public/documents` | 登录用户 | 公共文档列表 |
| `GET` | `/api/knowledge/public/documents/{documentId}` | 登录用户 | 公共文档详情 |
| `GET` | `/api/knowledge/public/documents/{documentId}/preview` | 登录用户 | 文档预览 |
| `POST` | `/api/knowledge/public/documents` | `ADMIN` / `SUPER_ADMIN` | 上传公共文档 |
| `PUT` | `/api/knowledge/public/documents/{documentId}` | `ADMIN` / `SUPER_ADMIN` | 编辑公共文档元数据 |
| `DELETE` | `/api/knowledge/public/documents/{documentId}` | `ADMIN` / `SUPER_ADMIN` | 删除公共文档 |
| `POST` | `/api/knowledge/public/documents/{documentId}/publish` | `ADMIN` / `SUPER_ADMIN` | 发布文档 |
| `POST` | `/api/knowledge/public/documents/{documentId}/disable` | `ADMIN` / `SUPER_ADMIN` | 禁用文档，设置 `enabled=false` |
| `POST` | `/api/knowledge/public/documents/{documentId}/enable` | `ADMIN` / `SUPER_ADMIN` | 启用普通公共文档；若 `source_type=CASE_LIBRARY`，仅 `SUPER_ADMIN` 可操作 |
| `POST` | `/api/knowledge/public/documents/{documentId}/reindex` | `ADMIN` / `SUPER_ADMIN` | 重建索引 |

公共知识库启用/禁用规则：

- 普通公共文档：管理员和超级管理员均可启用/禁用。
- 客服案例库文档：默认启用，只有超级管理员可禁用/启用。
- 禁用后的文档状态保留，但不进入 `/api/knowledge/selectable`，也不参与 RAG 检索。
- 禁用不改变 `status`，只设置 `enabled=false`；重新启用设置 `enabled=true`。`status=READY AND enabled=false` 表示“已就绪但被禁用/下线”，不得进入检索。

上传使用 `multipart/form-data`：

| 字段 | 必填 | 说明 |
|---|---|---|
| `file` | 是 | 原始文件 |
| `title` | 是 | 文档标题 |
| `categoryId` | 否 | 分类 |
| `tagIds` | 否 | 标签 ID 列表，逗号分隔或 JSON 数组 |
| `productLine` | 否 | 产品线 |
| `effectiveUntil` | 否 | 有效期 |

### 6.2 个人知识库

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/api/knowledge/personal/documents` | 本人 | 我的文档列表 |
| `GET` | `/api/knowledge/personal/documents/{documentId}` | 本人 | 我的文档详情 |
| `POST` | `/api/knowledge/personal/documents` | 本人 | 上传个人文档 |
| `PUT` | `/api/knowledge/personal/documents/{documentId}` | 本人 | 编辑个人文档 |
| `DELETE` | `/api/knowledge/personal/documents/{documentId}` | 本人 | 删除个人文档 |
| `POST` | `/api/knowledge/personal/documents/{documentId}/reindex` | 本人 | 重建个人文档索引 |
| `POST` | `/api/knowledge/personal/documents/{documentId}/contribute` | 本人 | 申请贡献到公共知识库，进入管理员审批流 |

贡献申请请求：

```json
{
  "targetCategoryId": "cat_after_sales",
  "targetTagIds": ["tag_refund"],
  "reason": "该文档整理了退款到账时间的常见处理口径，建议补充到公共知识库。"
}
```

贡献审批规则：

- 普通用户只能对自己的个人知识库文档发起贡献申请。
- 发起申请后生成审批流记录，状态为 `PENDING_APPROVAL`。
- 管理员或超级管理员审批通过后，系统复制一份公共知识库文档，不直接把原个人文档改成公共文档。
- 审批通过后的公共文档默认 `enabled=false`，即默认禁用，不进入 `/api/knowledge/selectable`，也不参与 RAG 检索。
- 审批通过处理器生成公共副本时必须显式设置 `enabled=false`；`kb_document.enabled DEFAULT TRUE` 不适用于贡献审批副本。
- 审批通过处理器不得复用普通公共文档上传的默认启用路径；若底层持久化方法没有显式 `enabled` 参数，必须新增专用方法或强制参数化，不能通过数据库默认值补齐。
- 管理员可手动启用普通公共文档。
- 如果贡献内容被识别为客服案例库（`source_type=CASE_LIBRARY`），审批通过后仍默认禁用，且只有超级管理员可手动启用。
- 审批拒绝时必须填写拒绝原因，普通用户可在个人知识库中查看申请结果。

审批相关接口：

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/api/knowledge/contributions/mine` | 本人 | 查看我的贡献申请 |
| `GET` | `/api/knowledge/contributions/pending` | `ADMIN` / `SUPER_ADMIN` | 查看待审批申请 |
| `POST` | `/api/knowledge/contributions/{applicationId}/approve` | `ADMIN` / `SUPER_ADMIN` | 审批通过，生成默认禁用的公共文档 |
| `POST` | `/api/knowledge/contributions/{applicationId}/reject` | `ADMIN` / `SUPER_ADMIN` | 审批拒绝 |

### 6.3 分类、标签和可选择范围

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/api/knowledge/categories?scope=PUBLIC` | 登录用户 | 分类树 |
| `POST` | `/api/knowledge/categories` | 公共需管理员，个人需本人 | 新增分类 |
| `PUT` | `/api/knowledge/categories/{categoryId}` | 公共需管理员，个人需本人 | 编辑分类 |
| `DELETE` | `/api/knowledge/categories/{categoryId}` | 公共需管理员，个人需本人 | 删除空分类 |
| `GET` | `/api/knowledge/tags` | 登录用户 | 标签候选 |
| `GET` | `/api/knowledge/selectable` | 登录用户 | 智能问答可启用知识范围 |

`GET /api/knowledge/selectable` 响应：

```json
{
  "defaultMode": "DEFAULT",
  "scopes": [
    {"scope": "PUBLIC", "enabled": true, "editable": false, "documentCount": 18},
    {"scope": "PERSONAL", "enabled": true, "editable": true, "documentCount": 6}
  ],
  "categories": [
    {"categoryId": "cat_after_sales", "scope": "PUBLIC", "name": "售后政策", "documentCount": 8}
  ],
  "tags": [
    {"tagId": "tag_refund", "name": "退款", "documentCount": 5}
  ],
  "documents": [
    {
      "documentId": "doc_public_001",
      "scope": "PUBLIC",
      "title": "售后政策手册",
      "status": "READY",
      "categoryId": "cat_after_sales",
      "updatedAt": "2026-04-26T20:00:00+08:00"
    }
  ]
}
```

`selectable` 过滤规则：

- 只返回当前用户有权访问的知识。
- 只返回 `status=READY` 且 `enabled=true` 的知识。
- 审批通过但默认禁用的公共知识副本不得返回。
- 禁用的普通公共文档和禁用的客服案例库均不得返回。
- 个人知识库只返回当前用户自己的 `READY + enabled` 文档。
- 上述规则是智能问答知识范围的权威规则。RAG 检索侧不得另行维护一套等价判断，必须复用 `biz-service` 解析出的过滤条件。

### 6.4 问答检索过滤条件解析

`biz-service` 必须提供内网能力，把前端选择器结果和可信身份头解析成 RAG 检索可执行的权威过滤条件。该能力供普通聊天链路中的 `ai-engine` 调用，也供管理侧检索测试复用，确保 `/api/knowledge/selectable` 与实际 RAG 命中的候选范围一致。

```text
POST /internal/knowledge/retrieval-filter
```

请求头：

| Header | 必填 | 说明 |
|---|---|---|
| `X-Internal-Token` | 是 | biz internal token，对应 Python `AICSP_BIZ_INTERNAL_TOKEN` |
| `X-Trace-Id` | 建议 | 链路追踪 |

请求体：

```json
{
  "tenantId": "default",
  "userId": "u_001",
  "roles": ["USER"],
  "knowledgeSelection": {
    "mode": "SELECTED",
    "includePublic": true,
    "includePersonal": true,
    "documentIds": ["doc_public_001"],
    "categoryIds": ["cat_after_sales"],
    "tagIds": ["tag_refund"]
  },
  "productLine": "default",
  "traceId": "trace-001"
}
```

响应体：

```json
{
  "mode": "SELECTED",
  "skipRetrieval": false,
  "tenantId": "default",
  "allowedScopes": ["PUBLIC", "PERSONAL"],
  "filters": {
    "status": "READY",
    "enabled": true,
    "tenantId": "default",
    "publicOwnerIsNull": true,
    "personalOwnerUserId": "u_001",
    "documentIds": ["doc_public_001"],
    "categoryIds": ["cat_after_sales"],
    "tagIds": ["tag_refund"],
    "productLine": "default"
  },
  "deniedCandidates": [
    {"documentIdHash": "sha256:...", "scope": "PERSONAL", "reason": "OWNER_MISMATCH"}
  ]
}
```

解析规则：

- `biz-service` 是 `status=READY AND enabled=true`、公共/个人范围、贡献副本禁用、客服案例库启停和 owner 过滤的唯一权威实现。
- `knowledgeSelection.mode=NONE` 时返回 `skipRetrieval=true`，Python 必须跳过 RAG。
- `knowledgeSelection.mode=DEFAULT` 时按当前用户可访问的公共知识库和本人个人知识库计算默认范围。
- `knowledgeSelection.mode=SELECTED` 时先按用户选择缩小候选，再与权限、状态、启用规则取交集。
- 被剔除的候选只返回脱敏摘要，不返回完整文档标题、正文或真实 owner 信息。
- Python 侧只能把 `filters` 转换为向量库元数据过滤表达式执行，不得自行补充、放宽或重写业务权限规则。

### 6.5 入库任务状态

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/api/knowledge/documents/{documentId}/ingestion` | 有文档访问权 | 查询入库状态 |
| `POST` | `/api/knowledge/documents/{documentId}/retry` | 有管理权 | 重试失败任务 |

入库状态响应：

```json
{
  "documentId": "doc_public_001",
  "taskId": "kb_task_001",
  "status": "EMBEDDING",
  "progress": 70,
  "chunkCount": 42,
  "embeddingModel": "nomic-embed-text",
  "errorMessage": null,
  "updatedAt": "2026-04-26T20:00:00+08:00"
}
```

---

## 7. 与 ai-engine 的内网交互

### 7.1 调用方式

`biz-service` 通过内网 HTTP 调用 Python：

```text
POST /internal/rag/ingest
POST /internal/rag/reindex
POST /internal/rag/delete
POST /internal/rag/retrieve
GET  /internal/rag/tasks/{taskId}
```

请求头：

| Header | 说明 |
|---|---|
| `X-Internal-Token` | HTTP Header；调用 `ai-engine` 的内部 token，由 Python 引擎侧配置校验；不得复用 `AICSP_BIZ_INTERNAL_TOKEN` |
| `X-Trace-Id` | 透传链路 ID |

`X-Internal-Token` 只能通过 HTTP Header 传输，不允许放入 query string 或请求体。

客户端治理：

- `connect-timeout` 建议 `5s`，普通提交类请求 `request-timeout` 建议 `10s`；`/internal/rag/retrieve` 管理侧检索测试建议 `30s`。
- `ingest/reindex/delete` 必须设计为“提交任务后快速返回”，不得在同步 HTTP 请求内等待完整解析、分块、embedding 和入库完成。
- 调用超时但 `biz-service` 已生成并保存 `taskId` 时，任务状态标记为 `SUBMITTING_UNKNOWN`，随后按 `taskId` 轮询 `/internal/rag/tasks/{taskId}`；若连续查询 `404` 或超过重试窗口，再标记为 `FAILED_RETRYABLE`。
- 对 `5xx`、连接失败、超时可按同一 `taskId` 做有限重试，避免重复创建任务；对 `4xx` 不重试，直接记录失败原因。
- ai-engine 连续失败时应打开熔断，短时间内拒绝新的入库提交并返回可重试错误；已有任务保持可查询状态，不删除业务元数据。

### 7.2 入库请求

```json
{
  "taskId": "kb_task_001",
  "documentId": "doc_public_001",
  "scope": "PUBLIC",
  "tenantId": "default",
  "ownerUserId": null,
  "objectPath": "kb/public/doc_public_001/manual.pdf",
  "title": "售后政策手册",
  "sourceType": "PDF",
  "productLine": "default",
  "tags": ["售后", "退款"],
  "traceId": "trace-001"
}
```

`ownerUserId` 约定：公共文档使用 `null`，个人文档填写实际用户 ID；不得使用 `"0"` 等哨兵值表示公共归属，避免权限过滤和审计歧义。

`ingest/reindex/delete` 响应必须表示“任务已接收”，不表示完整入库已经完成：

```json
{
  "taskId": "kb_task_001",
  "documentId": "doc_public_001",
  "status": "SUBMITTED",
  "accepted": true,
  "traceId": "trace-001"
}
```

收到该响应后，`biz-service` 将 `kb_ingestion_task.status` 置为 `SUBMITTED`，并通过 `/internal/rag/tasks/{taskId}` 或回调更新后续进度。

### 7.3 检索测试请求

`/internal/rag/retrieve` 仅用于管理侧检索测试、知识库质量评估和排障，不参与普通聊天链路；普通聊天检索由 `ai-engine` 在 `/api/chat/stream` 内完成向量召回，但召回前必须先调用 `biz-service /internal/knowledge/retrieval-filter` 获取权威过滤条件。

`biz-service` 构造请求体时必须由 Java 侧计算权限范围：

- `tenantId`、`userId` 来自网关注入身份，不信任前端请求体。
- `allowedScopes` 根据当前用户角色和文档权限计算；普通用户最多为 `["PUBLIC","PERSONAL"]`，其中 `PERSONAL` 只代表当前用户自己的个人知识库。
- `knowledgeSelection` 可来自管理页测试条件，但只能作为候选过滤条件，不能作为授权依据。
- `filters.status` 固定叠加 `READY`，并且只允许 `enabled=true` 的文档进入候选；该规则必须复用 `POST /internal/knowledge/retrieval-filter` 的解析逻辑，不得在管理侧检索测试中复制实现。
- `topK` 由后端限制最大值，默认 `8`，最大不超过 `20`。

请求示例：

```json
{
  "query": "如何申请退款？",
  "tenantId": "default",
  "userId": "u_001",
  "allowedScopes": ["PUBLIC", "PERSONAL"],
  "knowledgeSelection": {
    "mode": "SELECTED",
    "includePublic": true,
    "includePersonal": true,
    "documentIds": ["doc_public_001"],
    "categoryIds": ["cat_after_sales"],
    "tagIds": ["tag_refund"]
  },
  "filters": {
    "status": "READY",
    "enabled": true,
    "productLine": "default"
  },
  "topK": 8,
  "traceId": "trace-001"
}
```

### 7.4 入库结果回写

第一阶段推荐 `biz-service` 主动轮询 `/internal/rag/tasks/{taskId}`。后续如需要实时进度，可增加 `biz-service` 内部回调：

```text
POST /internal/knowledge/ingestion-callback
```

回调请求必须携带 HTTP Header：

| Header | 必填 | 说明 |
|---|---|---|
| `X-Internal-Token` | 是 | biz internal token，对应 Python `AICSP_BIZ_INTERNAL_TOKEN` |
| `X-Trace-Id` | 建议 | 链路追踪 |

该接口必须纳入 `/internal/**` 统一鉴权。token 校验失败返回 `401/403`，不得更新入库任务状态。

回调请求：

```json
{
  "taskId": "kb_task_001",
  "documentId": "doc_public_001",
  "status": "READY",
  "progress": 100,
  "chunkCount": 42,
  "embeddingModel": "nomic-embed-text",
  "errorMessage": null,
  "traceId": "trace-001"
}
```

---

## 8. 智能问答知识库选择契约

前端在发起智能问答前通过 `GET /api/knowledge/selectable` 获取用户可选择范围。发送聊天请求时，选择结果进入 `stream-service` 的 `ChatRequest.knowledgeSelection`。

`biz-service` 的职责：

- 只返回用户有权访问且状态为 `READY`、`enabled=true` 的知识项。
- 公共知识库对普通用户只读可选。
- 普通公共知识库可由管理员或超级管理员启用/禁用；客服案例库默认启用，只有超级管理员能禁用/启用。
- 个人知识库只返回当前用户自己的文档。
- 当文档 `enabled=false`，或 `status` 变为 `DELETED`、`FAILED` 时，从 selectable 结果中移除。
- 如果聊天请求中传入他人个人文档 ID 或其它无权访问文档 ID，`biz-service` 的检索过滤条件解析必须剔除并返回脱敏拒绝摘要；`ai-engine` 只按该摘要记录 `engine_retrieval_log.access_denied`，不得自行重新判权。

选择结构：

```json
{
  "mode": "SELECTED",
  "includePublic": true,
  "includePersonal": false,
  "documentIds": ["doc_public_001"],
  "categoryIds": ["cat_after_sales"],
  "tagIds": ["tag_refund"]
}
```

`mode`：

| mode | 说明 |
|---|---|
| `DEFAULT` | 公共知识库 + 当前用户个人知识库 |
| `SELECTED` | 仅使用显式选择范围 |
| `NONE` | 本次问答不使用知识库 |

---

## 9. 页面交互要求

`biz-service` 需要支撑以下前端页面：

| 页面/组件 | 后端接口 |
|---|---|
| 知识库管理页 | `/api/knowledge/public/**`、`/api/knowledge/personal/**` |
| 分类树 | `/api/knowledge/categories` |
| 标签筛选 | `/api/knowledge/tags` |
| 文档详情抽屉 | 文档详情、预览、入库状态 |
| 上传弹窗 | 文档上传接口 |
| 入库进度条 | `/api/knowledge/documents/{documentId}/ingestion` |
| 智能问答知识库选择器 | `/api/knowledge/selectable` |

页面交互原则：

- 公共知识库普通用户只读，不展示管理按钮。
- 管理员和超级管理员可见公共知识库新增、编辑、删除、发布、下线、重建索引、启用、禁用；但客服案例库的启用/禁用按钮仅超级管理员可见。
- 个人知识库只展示当前用户自己的内容。
- 普通用户可在个人知识库详情中发起“贡献到公共知识库”，申请进入管理员审批流；审批通过后的公共副本默认禁用，需要管理员手动启用。
- 文档状态必须可见，失败状态必须展示失败原因和重试入口。
- 智能问答选择器显示已选范围摘要，例如“公共 18 篇 · 个人 6 篇 · 售后政策”。

---

## 10. 验证要求

| 测试 | 重点 |
|---|---|
| Controller 单元测试 | 权限、参数校验、统一响应 |
| Mapper 测试 | schema、软删除、唯一索引、分页 |
| 消息完成接口测试 | 重复回调幂等、状态落库、异常处理 |
| Function Call 防腐测试 | 参数校验、权限判断、脱敏、幂等、审计 |
| 知识库权限测试 | 普通用户不能增删改公共知识库，不能访问他人个人知识库 |
| selectable 测试 | 只返回 READY、enabled 且有权访问的知识 |
| 贡献审批测试 | 普通用户可发起申请；审批通过生成默认禁用公共副本；拒绝必须记录原因 |
| 默认禁用测试 | 审批通过处理器必须显式写入 `enabled=false`；未启用的公共副本不得进入 selectable，不参与 RAG |
| 检索过滤条件解析测试 | `/api/knowledge/selectable` 与 `/internal/knowledge/retrieval-filter` 对 READY、enabled、owner、贡献副本和客服案例库启停规则保持一致 |
| ai-engine 契约测试 | ingest/reindex/delete/retrieve/task 查询请求和响应字段稳定；公共文档 `ownerUserId=null`；`documentId` 与 `taskId` 一对多映射正确 |
| 内部 token 测试 | `/internal/chat/message-completed`、`/internal/tools/{toolName}`、`/internal/knowledge/ingestion-callback` 只接受 HTTP Header `X-Internal-Token` 中的 biz internal token，不接受 stream internal token |
| ai-engine 客户端治理测试 | 连接失败、超时、5xx 重试、4xx 不重试、熔断和 `SUBMITTING_UNKNOWN` 状态处理 |
| 会话创建安全测试 | 请求体伪造 `userId/tenantId` 时必须被身份头覆盖 |

建议命令：

```bash
mvn -pl biz-service -am test
```

---

## 11. 后续演进

- P0：将会话创建接口改为从身份头读取 `userId`、`tenantId`，请求体同名字段不得作为权限依据。
- 增加知识库 Flyway 表结构。
- 增加 MinIO 文件上传与对象路径规范。
- 增加 `ai-engine` 内网客户端。
- 增加 `/internal/chat/message-completed` 与 `/internal/tools/{toolName}` 内部接口，并固化 `messageId` 幂等键与独立 biz internal token 校验。
- 批量上传和大文件场景再引入 RocketMQ 异步入库任务。
