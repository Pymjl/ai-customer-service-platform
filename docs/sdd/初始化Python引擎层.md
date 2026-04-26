# ai-customer-service-platform — Python 引擎层软件设计文档（SDD）

**版本**：1.0.0  
**日期**：2026-04-26  
**引擎根目录**：`ai-engine`  
**技术基线**：Python 3.14.4 · uv · FastAPI 0.136.1 · LangChain 1.2.15 · LangGraph 1.1.9（规划版本，以 `uv.lock` 为准）

---

## 1. 目标与范围

Python 引擎层负责智能客服的模型调用、RAG 检索、工具编排和流式响应生成。Java 侧继续承担统一网关、认证授权、SSE 转发入口、会话与消息持久化、内部业务防腐层等职责。

本设计文档覆盖：

- `ai-engine` 工程目录与依赖管理方式。
- Python 引擎与 Java `stream-service` 的接口契约。
- OpenAI 兼容模型 API 的接入规范。
- 聊天模型与 embedding 模型的独立配置方式。
- 基于 `docs/sdd/agent设计哲学.md` 的 Agent/RAG 设计原则。
- RAG 知识库管理 API 与页面交互的职责边界。
- 与 Java 侧中间件版本保持一致的约束。
- 流式输出、工具调用、安全、观测和测试要求。

Python 引擎不直接写数据库。模型流式生成完成后，Python 引擎通过 `biz-service` 内部接口提交消息完成结果，由 `biz-service` 幂等持久化。知识库管理不新增独立微服务，统一归入现有 `biz-service`，Python 引擎只承担内网 RAG 处理能力。

---

## 2. 关键假设

1. Python 引擎目录固定为项目根目录下的 `ai-engine/`。
2. Java `stream-service` 仅负责 SSE 转发，调用 Python 引擎的基础地址仍使用 `python.engine.base-url`，dev 默认 `http://localhost:8000`。
3. Python 引擎对 `stream-service` 暴露 `POST /api/chat/stream`，响应固定为 `application/x-ndjson; charset=utf-8`；Java 当前 DTO 为 `EngineEvent { event, data }`，实现阶段必须逐行解析完整 JSON 对象。
4. 用户身份由网关注入到 Java 服务，Python 引擎需要用户身份时由 `stream-service` 透传 `X-User-Id`、`X-Tenant-Id`、`X-Trace-Id`、`X-User-Roles` 等头。
5. 本地开发模型服务使用 Ollama 的 OpenAI 兼容接口，聊天模型和 embedding 模型分别配置。
6. 需要使用到 PostgreSQL、Redis、RocketMQ、MinIO、Nacos 时，运行中间件版本必须与 Java/dev 文档保持一致。
7. 知识库管理、function call 防腐、消息完成持久化接口属于业务管理能力，放在 Java `biz-service`；文档解析、分块、embedding、向量入库、检索和重排属于 RAG 引擎能力，放在 Python `ai-engine`。

---

## 3. 技术栈版本矩阵

### 3.1 Python 栈

| 组件 | 版本策略 | 说明 |
|---|---|---|
| Python | `3.14.4`（规划版本，以 `uv.lock` 为准） | Python.org 当前最新稳定维护版；优先锁定 Python 3.14 小版本线，若兼容性验证失败，按下方 CI 规则回退到 Python 3.13.x |
| uv | 使用当前稳定版 | 作为唯一 Python 依赖与虚拟环境管理工具，不提交 `.venv` |
| FastAPI | `0.136.1`（规划版本，以 `uv.lock` 为准） | Python API 服务框架 |
| Uvicorn | `0.43.0`（规划版本，以 `uv.lock` 为准） | ASGI 服务运行时，dev 使用 `uv run uvicorn ... --reload` |
| Pydantic | `2.13.3`（规划版本，以 `uv.lock` 为准） | 配置、请求、响应、工具 schema 校验 |
| OpenAI Python SDK | `2.32.0`（规划版本，以 `uv.lock` 为准） | 直接使用 OpenAI 兼容 REST API；可配置 `base_url` |
| LangChain | `1.2.15`（规划版本，以 `uv.lock` 为准） | LLM/RAG 应用抽象 |
| LangChain OpenAI | `1.2.1`（规划版本，以 `uv.lock` 为准） | OpenAI 兼容聊天与 embedding 接入 |
| LangGraph | `1.1.9`（规划版本，以 `uv.lock` 为准） | Agent 状态图、可中断流程、工具编排 |
| LangChain Postgres | `0.0.17`（规划版本，以 `uv.lock` 为准） | 如使用 pgvector，优先使用 `PGVectorStore` |
| httpx | 最新稳定兼容版 | 异步 HTTP 客户端，调用 Java 内部接口与模型 API |
| redis-py | 最新稳定兼容版 | 如需要缓存检索、限流或短期状态 |
| minio / boto3 | 最新稳定兼容版 | 如 Python 侧需要读取知识库原始文件 |

> 版本核对日期：2026-04-26。落地实现时使用 `uv lock` 固化完整解析结果，禁止只提交未锁定的宽松依赖。

CI 版本验证规则：

- CI 必须在 Python `3.14.4` 上执行 `uv lock --locked`、`uv sync --frozen`、`uv run pytest`、`uv run ruff check .`、`uv run mypy src` 和契约测试。
- 若 `uv lock`、`uv sync` 或测试因某个直接/传递依赖缺少 Python 3.14 wheel、源码构建失败、上游元数据显式排除 Python 3.14，或出现可复现的 Python 3.14 运行时兼容缺陷，则触发回退评估。
- 回退版本优先选择当前受支持的 Python `3.13.x` 最新维护版；回退后必须重新生成 `uv.lock`，并在 `pyproject.toml`、`.python-version` 和本文档记录触发原因与恢复到 3.14 的条件。
- 不允许只因本地开发机未安装 Python 3.14 而回退；本地问题应通过 uv 自动安装解释器或补齐开发环境解决。

### 3.2 中间件版本一致性

| 中间件 | Java/dev 当前版本 | Python 引擎使用策略 |
|---|---|---|
| PostgreSQL + pgvector | `pgvector/pgvector:pg17`，PostgreSQL 17+ | 知识库向量存储使用同一 PostgreSQL 容器；单独创建 `aicsp_engine` 数据库与 `engine_service` schema |
| Redis | `redis:7.2` | 如需缓存、短期会话状态、检索结果缓存，使用 Redis 7.2；建议 DB `3` 留给引擎 |
| RocketMQ | `apache/rocketmq:5.3.2` | 第一阶段 Python 引擎不直接消费/生产 MQ；知识库异步入库若后续接入，必须对齐 5.3.2 |
| Nacos | `2.x` | 第一阶段由 Java 配置 Python 地址，不强制 Python 注册 Nacos；如后续接入服务发现，必须对齐 Nacos 2.x |
| MinIO | `minio/minio` 当前稳定镜像 | 原始文档、解析产物可复用 MinIO；bucket 独立于用户头像 bucket |
| Ollama | 本地部署版本 | 使用其 OpenAI 兼容 `/v1/chat/completions` 与 `/v1/embeddings` 接口 |

---

## 4. 工程目录设计

`ai-engine` 采用 uv 标准项目结构，根目录必须包含 `pyproject.toml` 与 `uv.lock`。

```text
ai-engine/
├── pyproject.toml
├── uv.lock
├── .python-version
├── .gitignore
├── .env.example
├── README.md
├── src/
│   └── aicsp_engine/
│       ├── __init__.py
│       ├── main.py
│       ├── api/
│       │   ├── __init__.py
│       │   ├── routes_chat.py
│       │   ├── routes_health.py
│       │   └── routes_rag.py
│       ├── core/
│       │   ├── __init__.py
│       │   ├── config.py
│       │   ├── logging.py
│       │   └── trace.py
│       ├── models/
│       │   ├── __init__.py
│       │   ├── engine.py
│       │   ├── chat.py
│       │   └── events.py
│       ├── llm/
│       │   ├── __init__.py
│       │   ├── clients.py
│       │   ├── chat_model.py
│       │   └── embedding_model.py
│       ├── rag/
│       │   ├── __init__.py
│       │   ├── retriever.py
│       │   ├── chunking.py
│       │   ├── rerank.py
│       │   └── prompt_context.py
│       ├── agent/
│       │   ├── __init__.py
│       │   ├── graph.py
│       │   ├── state.py
│       │   ├── nodes.py
│       │   └── policies.py
│       ├── tools/
│       │   ├── __init__.py
│       │   ├── registry.py
│       │   ├── schemas.py
│       │   └── stream_service.py
│       ├── storage/
│       │   ├── __init__.py
│       │   ├── postgres.py
│       │   ├── redis.py
│       │   └── minio.py
│       └── observability/
│           ├── __init__.py
│           ├── metrics.py
│           └── events.py
└── tests/
    ├── unit/
    ├── integration/
    └── contract/
```

目录职责：

| 目录 | 职责 |
|---|---|
| `api` | HTTP 路由、流式响应封装、健康检查 |
| `core` | 配置、日志、Trace ID、应用生命周期 |
| `models` | Pydantic 请求/响应/内部事件模型 |
| `llm` | OpenAI 兼容聊天模型与 embedding 模型客户端 |
| `rag` | 查询改写、混合检索、重排、上下文组装 |
| `agent` | LangGraph 状态图、节点、循环控制和降级策略 |
| `tools` | 工具 schema、工具注册、Java 内部接口调用 |
| `storage` | PostgreSQL/pgvector、Redis、MinIO 访问封装 |
| `observability` | 指标、结构化事件、质量评估扩展 |
| `tests/contract` | 与 Java `stream-service` 的请求/事件契约测试 |

`api/routes_rag.py` 承载第 19.5 节定义的 `/internal/rag/*` 内网接口；该路由只能由 Java 内部服务调用，不应暴露到前端网关。`.gitignore` 至少应排除 `.venv/`、`.env`、`__pycache__/`、`.pytest_cache/`、`.mypy_cache/`、`.ruff_cache/` 等本地生成文件。

---

## 5. uv 依赖管理规范

### 5.1 初始化方式

```bash
cd ai-engine
uv init --package --python 3.14
uv add fastapi==0.136.1 "uvicorn[standard]==0.43.0" pydantic==2.13.3 pydantic-settings
uv add openai==2.32.0 langchain==1.2.15 langchain-openai==1.2.1 langgraph==1.1.9 langchain-postgres==0.0.17
uv add httpx psycopg[binary,pool] redis minio python-json-logger
uv add --dev pytest pytest-asyncio ruff mypy
uv lock
```

`uv init --package --python 3.14` 中的 `--python 3.14` 是解释器版本选择器，初始化后 `pyproject.toml` 通常只会生成 `requires-python = ">=3.14"`。本项目要求固定 Python 3.14 小版本线，初始化后必须手动修正为 `requires-python = ">=3.14,<3.15"`，再执行 `uv lock`。

### 5.2 约束

- 只能通过 `uv add`、`uv remove`、`uv lock`、`uv sync` 管理依赖。
- `pyproject.toml` 记录直接依赖，`uv.lock` 固化解析后的完整依赖树。
- `.venv/` 不提交到仓库。
- `uv init --package` 生成的 `[build-system]` 必须保留；若 uv 版本生成的是 `uv_build`、`hatchling` 或其它后端，按生成结果保留并确保 `src/aicsp_engine` 能被发现为包。构建后端以 `uv init` 实际生成结果为准，示例仅供参考，不得手动替换。
- 依赖升级必须先执行 `uv lock --upgrade-package <package>`，再跑单元测试与契约测试。
- 不使用 `requirements.txt` 作为主依赖来源；如需导出给容器扫描，可由 `uv export` 生成。

### 5.3 pyproject 关键配置示例

构建后端以 `uv init` 实际生成结果为准，下面 `[build-system]` 只展示一种可能结果，不得为了贴合示例而手动替换已生成的构建后端。

```toml
[build-system]
requires = ["uv_build>=0.11.6,<0.12"]
build-backend = "uv_build"

[project]
name = "aicsp-engine"
version = "0.1.0"
requires-python = ">=3.14,<3.15"
dependencies = [
    "fastapi==0.136.1",
    "uvicorn[standard]==0.43.0",
    "pydantic==2.13.3",
    "pydantic-settings",
    "openai==2.32.0",
    "langchain==1.2.15",
    "langchain-openai==1.2.1",
    "langgraph==1.1.9",
    "langchain-postgres==0.0.17",
    "httpx",
    "psycopg[binary,pool]",
    "redis",
    "minio",
    "python-json-logger",
]

[dependency-groups]
dev = [
    "pytest",
    "pytest-asyncio",
    "ruff",
    "mypy",
]

[tool.uv]
package = true

[tool.ruff]
line-length = 100
target-version = "py314"

[tool.mypy]
python_version = "3.14"
strict = true
mypy_path = "src"

[tool.pytest.ini_options]
asyncio_mode = "auto"
testpaths = ["tests"]
```

`aicsp_engine.main:app` 这类启动模块路径依赖 `src/` 布局下的包安装或包发现配置。只要保留 `[build-system]` 并设置 `tool.uv.package = true`，`uv run` 会以当前项目包方式运行，`src/aicsp_engine` 可被正确加入导入路径。

---

## 6. 服务契约

### 6.1 Python 引擎入口

| 方法 | 路径 | Content-Type | 说明 |
|---|---|---|---|
| `POST` | `/api/chat/stream` | `application/json` | 接收 Java `stream-service` 请求，流式返回模型输出事件 |
| `GET` | `/healthz` | - | Python 引擎健康检查，便于 Java/部署层探活 |

Python 侧统一使用 `/healthz` 作为探活路径，避免与 Spring Boot Actuator 概念混淆。Java 或部署层的健康检查配置应指向该路径；如后续确需兼容旧探活配置，可额外提供 `/actuator/health` 适配路由，但不作为主契约。

### 6.2 请求模型

第一阶段兼容 Java 当前 `EngineRequest`：

```json
{
  "sessionId": "s_001",
  "message": "我的订单什么时候发货？",
  "traceId": "trace-001"
}
```

建议第二阶段扩展为：

```json
{
  "messageId": "m_001",
  "sessionId": "s_001",
  "message": "我的订单什么时候发货？",
  "traceId": "trace-001",
  "userId": "u_001",
  "tenantId": "default",
  "roles": ["USER"],
  "locale": "zh-CN",
  "knowledgeSelection": {
    "mode": "SELECTED",
    "includePublic": true,
    "includePersonal": true,
    "documentIds": ["doc_public_001", "doc_personal_001"],
    "categoryIds": ["cat_after_sales"],
    "tagIds": ["tag_refund"]
  },
  "metadata": {}
}
```

扩展字段必须保持向后兼容，Python 侧 Pydantic 模型对新增字段使用可选字段。`knowledgeSelection` 表示用户本次智能问答启用的知识库候选范围；为空时按 `DEFAULT` 处理。最终可检索范围以 Java `biz-service` 解析出的过滤条件为准，Python 不自行维护 `status=READY AND enabled=true` 等可检索规则。

`knowledgeSelection.mode` 取值：

| mode | 说明 |
|---|---|
| `DEFAULT` | 使用系统默认知识库范围 |
| `SELECTED` | 仅使用用户显式选择的知识库、分类、标签或文档 |
| `NONE` | 本次不使用知识库，只进行模型直接回答和必要工具调用 |

最终可检索范围必须取“用户选择范围”和“后端权限校验范围”的交集，前端传入的 `documentIds`、`categoryIds`、`tagIds` 只能作为候选条件，不能作为权限依据。该交集由 `biz-service` 统一计算并下发给 Python，避免 `selectable` 与 RAG 检索各自实现一套过滤逻辑。

### 6.3 流式响应事件

Python 引擎输出固定为 NDJSON，响应头必须为 `Content-Type: application/x-ndjson; charset=utf-8`。每行都是一个完整 `EngineEvent` JSON 对象，行尾使用 `\n`；第一阶段不使用 SSE `text/event-stream`，避免 Java `PythonEngineClient.bodyToFlux(EngineEvent.class)` 在内容协商或解码上出现二义性。

```json
{"event":"message","data":"您好"}
{"event":"message","data":"，请提供订单号。"}
{"event":"done","data":"{\"finishReason\":\"stop\"}"}
```

事件类型：

| event | data | 说明 |
|---|---|---|
| `message` | 文本片段 | 面向用户的增量输出，必须可直接拼接 |
| `tool_call` | JSON | 可选，工具调用开始事件，默认不透出给终端用户 |
| `tool_result` | JSON | 可选，工具调用结果摘要，敏感字段必须脱敏 |
| `citation` | JSON | 可选，知识库来源信息 |
| `error` | JSON | 流内错误，不抛断 TCP 连接 |
| `done` | JSON | 流结束，包含 `finishReason`、token 用量、检索摘要等可选字段 |

约束：

- 智能客服相关输出均为流式输出。
- 用户可见文本只通过 `message` 事件输出。
- 工具调用与检索事件默认作为内部观测事件；如果后续前端需要展示来源，可只透出 `citation`。
- 流内异常使用 `error` 事件返回，随后发送 `done` 或直接结束流；不得返回半截 JSON。
- 契约测试必须固定响应格式：断言 `Content-Type` 为 `application/x-ndjson`，断言每一行可独立反序列化为 `EngineEvent`，并覆盖 `message`、`error`、`done` 事件。

---

## 7. 模型接入设计

### 7.1 OpenAI 兼容 API

Python 引擎只依赖 OpenAI 兼容协议，不绑定具体厂商。

配置项：

```env
AICSP_LLM_BASE_URL=http://localhost:11434/v1
AICSP_LLM_API_KEY=ollama
AICSP_LLM_MODEL=qwen3:8b

AICSP_EMBEDDING_BASE_URL=http://localhost:11434/v1
AICSP_EMBEDDING_API_KEY=ollama
AICSP_EMBEDDING_MODEL=nomic-embed-text
AICSP_EMBEDDING_DIMENSIONS=768
```

说明：

- 聊天模型和 embedding 模型必须分开配置，不复用一个模型名。
- `base_url` 必须包含 OpenAI 兼容 `/v1` 前缀。
- 本地 Ollama `api_key` 可填任意非空值，例如 `ollama`。
- 生产环境只替换 `base_url`、`api_key`、`model` 即可迁移到其它 OpenAI 兼容供应商。

### 7.2 调用策略

- 聊天模型默认使用 `/v1/chat/completions`，开启 `stream=true`。
- embedding 使用 `/v1/embeddings`。
- 生成参数由配置控制：`temperature`、`top_p`、`max_tokens`、`timeout_seconds`。
- 模型超时需要与 Java `python.engine.response-timeout=310000` 保持协调，Python 单次流最长不超过 `300s`。
- 所有模型调用必须带 `trace_id` 日志字段。

---

## 8. Agent 与 RAG 设计

本设计遵循 `docs/sdd/agent设计哲学.md` 的核心原则：上下文工程优先、公共知识库与个人知识库双轨、Schema-First 工具、Fail-Closed 权限、分层漏斗、可中断可恢复、可观测性内建。

### 8.1 上下文三层组装

| 层级 | 来源 | 说明 |
|---|---|---|
| 系统级 | 固定系统提示词、租户策略、客服行为约束 | 不包含大段知识库内容 |
| 会话级 | 当前 session 的短期历史、用户身份、已确认事实 | 防止多轮追问丢失指代 |
| 检索级 | RAG 检索结果、工具实时查询结果 | 只注入与当前问题相关的内容 |

禁止把全量知识库直接塞入 prompt。

### 8.2 RAG 检索链路

```mermaid
flowchart TD
    Q[用户问题] --> Normalize[规范化与指代消解]
    Normalize --> Rewrite[Query Rewrite / Multi-Query]
    Rewrite -->|超时/失败| RewriteFallback[使用原始问题]
    RewriteFallback --> Select
    Rewrite --> Select[用户启用知识库范围]
    Select --> AuthScope[权限交集: scope/user/status]
    AuthScope --> Meta[元数据过滤: tenant/user/product_line/document/category/tag]
    Meta --> Dense[向量检索]
    Meta --> Sparse[关键词检索]
    Dense --> RRF[RRF 融合]
    Sparse --> RRF
    RRF --> Rerank[Reranker 精排]
    RRF -->|Reranker 不可用| RerankFallback[保留 RRF 排序]
    RerankFallback --> Context
    Rerank --> Context[上下文组装]
    Context -->|无可用片段| NoRag[无 RAG 上下文]
    NoRag --> LLM
    Context --> LLM[流式生成]
```

默认策略：

- 分块优先按 Markdown/HTML 标题、段落、语义边界切分。
- 检索 Chunk：128~256 token。
- 父级 Chunk：512~1024 token。
- Chunk 重叠：10%~20%。
- 公共知识库与个人知识库可分别召回，再融合排序；召回前必须使用 `biz-service` 注入的过滤条件缩小候选集合。
- 个人知识库权限、公共知识库启用状态、`READY + enabled` 等规则均由 `biz-service` 统一计算，Python 不重复编码这些业务规则。
- 用户问答时可以选择启用哪些知识库；检索前必须先调用或接收 Java 侧解析出的过滤条件，再与用户选择条件取交集后的结果执行向量检索。
- 当用户选择 `NONE` 或交集为空时，RAG 检索应跳过，并在可观测日志中记录 `knowledge_selection=none` 或 `knowledge_selection_empty=true`。
- Query Rewrite 超时或失败时，使用原始用户问题继续检索，并记录 `rewrite_status=fallback`、`fallback_reason`。
- Dense 或 Sparse 任一路召回失败时，允许使用另一可用召回结果继续；两路均失败时跳过 RAG，转为直接生成或工具链路。
- Reranker 不可用、超时或返回异常时，保留 RRF 融合排序继续上下文组装，并记录 `rerank_status=fallback`。
- 所有降级都不得扩大权限范围；权限交集与元数据过滤失败时必须 Fail-Closed，直接返回空检索结果。

### 8.3 LangGraph Agent 状态机

```mermaid
stateDiagram-v2
    [*] --> PrepareContext
    PrepareContext --> IntentFunnel
    IntentFunnel --> DirectAnswer: 高频规则命中
    IntentFunnel --> Retrieve: 需要知识库
    IntentFunnel --> ToolPlan: 需要实时业务数据
    Retrieve --> RagFallback: 改写/召回/重排降级或空结果
    RagFallback --> Generate
    Retrieve --> Generate
    ToolPlan --> ToolExecute
    ToolExecute --> Generate
    DirectAnswer --> Stream
    Generate --> Stream
    Stream --> QualityEvent
    QualityEvent --> [*]
```

设计约束：

- `max_turns` 默认不超过 `8`，避免 Agentic Loop 无限循环。
- 只读工具可自动执行；写入工具必须显式确认；高风险工具必须人工审批。
- 工具失败不得阻塞完整会话，优先降级为“无法查询到实时信息，请补充信息或转人工”。
- 已完成的工具步骤必须具备幂等键，重试时不得重复写入业务系统。
- RAG 降级统一进入 `RagFallback` 状态，状态数据必须保留 `fallback_reason`、`failed_stage` 和 `degraded=true`，便于日志、指标和回放分析。

---

## 9. 工具调用设计

### 9.1 工具来源

Python 引擎不直接绕过 Java 业务边界访问订单、物流、CRM 等系统，统一通过 Java `biz-service` 的 `/internal/tools/{toolName}` 防腐接口调用。`stream-service` 只负责 SSE 流式转发，不承载 function call 或数据防腐逻辑。

```mermaid
sequenceDiagram
    participant P as ai-engine
    participant B as biz-service
    participant E as 业务系统

    P->>B: POST /internal/tools/{toolName} + X-Internal-Token
    B->>B: 参数校验 / 权限判断 / 幂等 / 审计
    B->>E: 调用订单、物流、CRM 等服务
    E-->>B: 原始业务结果
    B-->>P: 脱敏后的标准化结果
```

工具接口统一使用 `POST`，便于携带结构化参数、幂等键、确认状态和审计字段。只读工具可自动执行；写入或有副作用的工具必须携带幂等键与确认状态。

### 9.2 Schema-First

每个工具必须同时定义：

- `name`：稳定工具名。
- `description`：面向模型的使用场景描述。
- `input_schema`：Pydantic 模型，执行前校验。
- `output_schema`：标准化输出模型。
- `permission_level`：`read`、`write_confirm`、`human_approval`。
- `timeout_seconds`：单工具超时。
- `idempotency_key`：写入类工具必填。

### 9.3 Fail-Closed 权限

| 工具类型 | 示例 | 默认策略 |
|---|---|---|
| 只读工具 | 查询订单、查询物流、查询售后政策 | 可自动执行 |
| 写入工具 | 创建工单、修改地址、发起退款 | 需要用户确认 |
| 高风险工具 | 批量退款、账号封禁、金额调整 | 人工审批 |

权限不明确时按更保守级别处理。

### 9.4 消息完成回调

Python 引擎完成流式生成后，调用 `biz-service` 内部接口提交最终结果：

```text
POST /internal/chat/message-completed
```

请求体：

```json
{
  "messageId": "m_001",
  "sessionId": "s_001",
  "userId": "u_001",
  "tenantId": "default",
  "userMessage": "退款多久到账？",
  "aiReply": "退款通常 1-3 个工作日到账。",
  "status": "COMPLETED",
  "traceId": "trace-001",
  "timestamp": 1777204800000
}
```

`messageId` 是消息完成回调的幂等键。`biz-service` 必须以 `messageId` 做唯一约束或等效幂等控制，重复提交同一 `messageId` 时只能写入一次；重复请求应返回已持久化结果或幂等成功，不得追加重复 AI 消息。

`stream-service` 不聚合回答、不发布 MQ、不持久化消息。

---

## 10. 数据存储设计

### 10.1 数据库规划

如 Python 引擎需要持久化知识库、检索索引、评估事件，使用独立数据库与 schema：

| 数据库 | Schema | 用途 |
|---|---|---|
| `aicsp_engine` | `engine_service` | 知识库文档、chunk、向量索引、检索事件、质量评估事件 |

PostgreSQL 扩展：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### 10.2 核心表建议

| 表 | 用途 |
|---|---|
| `engine_media` | RAG 处理侧文档镜像/处理记录，关联 Java `Media` 的 `document_id`、来源、处理状态、索引生命周期；不作为用户可见文档主数据 |
| `engine_chunk` | RAG chunk 元数据、父子关系、正文摘要 |
| `engine_embedding` | 向量索引表，可使用 `vector(dimensions)` |
| `engine_retrieval_log` | 检索命中、分数、来源、耗时；个人知识库越权候选必须记录 `access_denied` 安全审计事件 |
| `engine_tool_call_log` | 工具入参摘要、出参摘要、状态、耗时 |
| `engine_quality_event` | 回答有用性、幻觉评估、低置信度标记 |

### 10.3 Redis 规划

建议 Python 引擎使用 Redis DB `3`，避免与 Java 侧冲突：

| Redis DB | 当前用途 |
|---:|---|
| `0` | gateway-service 限流等 |
| `1` | user-service refresh token |
| `2` | biz-service |
| `3` | ai-engine 检索缓存、短期会话状态、幂等锁 |

Key 前缀统一使用 `aicsp:engine:`。

TTL 策略：

| Key 类型 | Key 示例 | TTL | 说明 |
|---|---|---:|---|
| 检索结果缓存 | `aicsp:engine:retrieval:{tenantId}:{hash}` | `10m` | 只缓存权限过滤后的候选与分数，Key 必须包含租户、用户或权限摘要，避免跨用户复用 |
| RAG 中间状态 | `aicsp:engine:rag_state:{traceId}` | `30m` | 用于调试和短期恢复，不保存完整敏感正文 |
| 消息完成幂等锁 | `aicsp:engine:idempotency:message_completed:{messageId}` | `24h` | 防止生成完成回调重试造成并发重复提交 |
| 工具调用幂等锁 | `aicsp:engine:idempotency:tool:{idempotencyKey}` | `24h` | 写入类工具调用使用，读工具不强制 |
| 临时限流计数 | `aicsp:engine:rate:{tenantId}:{window}` | `2m` | 覆盖当前限流窗口并留少量缓冲 |

所有 Redis Key 必须设置 TTL；确需长期保存的数据应进入 PostgreSQL，不得用 Redis 充当永久存储。

---

## 11. 配置项

`.env.example` 应至少包含：

```env
AICSP_ENGINE_HOST=0.0.0.0
AICSP_ENGINE_PORT=8000
AICSP_ENGINE_ENV=dev

AICSP_LLM_BASE_URL=http://localhost:11434/v1
AICSP_LLM_API_KEY=ollama
AICSP_LLM_MODEL=qwen3:8b
AICSP_LLM_TEMPERATURE=0.2
AICSP_LLM_TOP_P=0.9
AICSP_LLM_MAX_TOKENS=2048
AICSP_LLM_TIMEOUT_SECONDS=300

AICSP_EMBEDDING_BASE_URL=http://localhost:11434/v1
AICSP_EMBEDDING_API_KEY=ollama
AICSP_EMBEDDING_MODEL=nomic-embed-text
AICSP_EMBEDDING_DIMENSIONS=768

AICSP_STREAM_SERVICE_BASE_URL=http://localhost:18082
AICSP_STREAM_INTERNAL_TOKEN=change-me
AICSP_BIZ_SERVICE_BASE_URL=http://localhost:18083
AICSP_BIZ_INTERNAL_TOKEN=change-me-biz

AICSP_POSTGRES_DSN=postgresql://postgres:123456@localhost:5432/aicsp_engine
AICSP_POSTGRES_SCHEMA=engine_service
AICSP_REDIS_URL=redis://:123456@localhost:6379/3

AICSP_MINIO_ENDPOINT=http://localhost:9000
AICSP_MINIO_ACCESS_KEY=minioadmin
AICSP_MINIO_SECRET_KEY=minioadmin123
AICSP_MINIO_BUCKET=aicsp-engine

AICSP_MAX_AGENT_TURNS=8
AICSP_TRACE_HEADER=X-Trace-Id
```

生产环境禁止使用 dev 默认密钥、密码和 token。

---

## 12. 流式输出实现要求

Python 引擎 API 层使用异步生成器输出事件：

1. 接收请求后立即生成 `trace_id` 或透传 Java `traceId`。
2. 进行上下文组装、检索、工具调用时不得阻塞事件循环。
3. 模型侧使用 OpenAI 兼容 streaming 接口逐 token 读取。
4. 每个文本增量转换为 `{"event":"message","data":"..."}`。
5. 结束时输出 `done`，异常时输出 `error`。

响应格式固定使用 `application/x-ndjson; charset=utf-8`。由于 Java 当前 `PythonEngineClient.bodyToFlux(EngineEvent.class)` 直接解析 JSON 对象流，Python 引擎必须确保每行一个完整 JSON 对象；第一阶段不得同时支持 SSE 输出，后续如需 SSE 必须新增独立路径或通过明确的版本化契约切换。

---

## 13. 可观测性

每次请求至少记录：

| 字段 | 说明 |
|---|---|
| `trace_id` | 全链路追踪 ID，与 Java `X-Trace-Id` 对齐 |
| `session_id` | 会话 ID |
| `user_id` / `tenant_id` | 用户与租户，日志中可做 hash 脱敏 |
| `intent_route` | 规则、embedding、RAG、tool、direct |
| `retrieval_hits` | 命中文档 ID、分数、scope |
| `tool_calls` | 工具名、耗时、状态 |
| `model` | 聊天模型名称 |
| `embedding_model` | embedding 模型名称 |
| `latency_ms` | 端到端耗时 |
| `token_usage` | 如供应商返回则记录 |
| `finish_reason` | stop、length、error、cancelled |

可观测性数据默认写日志；后续可扩展到 PostgreSQL、Prometheus 或 OpenTelemetry。

---

## 14. 安全设计

- Python 引擎不直接暴露公网，只允许 Java `stream-service` 访问流式入口，允许 `biz-service` 访问 RAG 入库类内网接口。
- `/api/chat/stream` 第一阶段信任 `stream-service` 透传的网关身份头；生产部署需加网络隔离和内部 token。
- 调用 Java `biz-service` 的 `/internal/**` 必须携带 `X-Internal-Token`。
- Python 引擎调用 `biz-service /internal/tools/{toolName}` 时必须使用独立的 `AICSP_BIZ_INTERNAL_TOKEN`，不得复用 `AICSP_STREAM_INTERNAL_TOKEN`。`AICSP_STREAM_INTERNAL_TOKEN` 仅用于 Java `stream-service` 访问 Python 流式入口的内部鉴权，两个 token 在生产环境必须由不同密钥材料生成并独立轮换。
- 个人知识库检索必须强制注入 `user_id` 过滤条件，默认 Fail-Closed。
- 日志禁止输出完整用户问题中的敏感信息、完整工具出参、API Key、内部 token。
- 模型输出不得包含系统提示词、内部工具 token、原始检索上下文的隐藏字段。

---

## 15. 本地启动流程

依赖启动顺序：

1. PostgreSQL + pgvector
2. Redis
3. MinIO（如测试知识库文件）
4. Ollama，并拉取聊天模型与 embedding 模型
5. Java `user-service`、`biz-service`
6. `ai-engine`
7. Java `stream-service`、`gateway-service`
8. frontend

Python 引擎生成完成后需要回调 `biz-service /internal/chat/message-completed`，工具调用也依赖 `biz-service /internal/tools/{toolName}`。开发测试时需确保 Java 侧 `biz-service` 先启动并可访问；若只调试 Python 引擎，必须使用 mock `biz-service` 或关闭会触发回调的测试路径。

Python 引擎启动：

```bash
cd ai-engine
uv sync
uv run uvicorn aicsp_engine.main:app --host 0.0.0.0 --port 8000 --reload
```

`aicsp_engine.main:app` 是基于 `src/` 布局的包模块路径，不能改成 `src.aicsp_engine.main:app`。该命令依赖第 5.3 节的包安装/包发现配置；若删除 `[build-system]` 或关闭 `tool.uv.package`，`uv run` 可能无法导入 `aicsp_engine`。

本地模型准备示例：

```bash
ollama pull qwen3:8b
ollama pull nomic-embed-text
```

---

## 16. 测试与验证

| 测试类型 | 要求 |
|---|---|
| 单元测试 | RAG 查询改写、权限策略、工具 schema、事件序列 |
| 契约测试 | `EngineRequest` 输入与 `EngineEvent` 输出必须兼容 Java；固定 `/api/chat/stream` 响应为 `application/x-ndjson` |
| 集成测试 | Ollama OpenAI 兼容 chat/embedding、本地 pgvector、Redis |
| 流式测试 | 验证首 token 延迟、异常事件、done 事件、客户端断开 |
| 安全测试 | 个人知识库越权、工具权限、敏感日志过滤 |

基础命令：

```bash
cd ai-engine
uv run pytest
uv run ruff check .
uv run mypy src
```

---

## 17. 与 Java 侧的后续联动点

| 联动点 | 当前状态 | 建议 |
|---|---|---|
| `PythonEngineClient` | 已存在，但当前 `ChatStreamServiceImpl` 仍是占位回显 | 后续将 Java 流式服务切换为调用 Python 引擎 |
| `EngineRequest` | 只有 `sessionId/message/traceId` | 增加 `messageId/userId/tenantId/roles`，保持可选兼容 |
| `EngineEvent` | `event/data` | 保持稳定，复杂事件 data 使用 JSON 字符串 |
| `biz-service /internal/tools/{toolName}` | 规划 | Python function call 统一走该入口 |
| `biz-service /internal/chat/message-completed` | 规划 | Python 生成结束后提交消息完成结果 |
| `python.engine.response-timeout` | `310000ms` | Python 最大流式时长控制在 300s 内 |

---

## 18. 风险与边界

- Python 3.14.4 是当前最新稳定维护版，但部分第三方包可能存在 wheel 发布滞后；实现阶段必须以 `uv.lock` 和 CI 验证为准。
- LangChain、LangGraph、FastAPI、OpenAI SDK 等依赖版本均为规划版本；若实际锁定或测试出现兼容问题，按第 3.1 节 CI 规则短期回退 Python 3.13.x 或调整依赖补丁版本，并记录原因。
- Ollama 的 OpenAI 兼容接口覆盖常用端点，但不同模型对工具调用、JSON mode、reasoning 参数支持程度不同；工具调用能力必须按具体模型验证。
- 第一阶段 Python 引擎不直接写业务会话消息，只通过 `biz-service /internal/chat/message-completed` 提交结果，避免与 Java 持久化职责冲突。
- RAG 质量依赖知识库数据质量；没有经过解析、分块、检索评估的数据不得直接进入生产知识库。

---

## 19. 知识库管理职责与页面交互设计

### 19.1 职责边界

知识库管理采用“Java 管业务与权限，Python 管 RAG 处理”的边界。由于本项目是个人学习练手项目，不再拆分新的 `knowledge-service`，对外知识库管理 API 直接放入现有 `biz-service`。

| 能力 | 放置位置 | 说明 |
|---|---|---|
| 公共知识库列表、详情、搜索 | `biz-service` | 普通用户只读，管理员与超级管理员可管理 |
| 个人知识库列表、详情、搜索 | `biz-service` | 当前登录用户只能管理自己的个人知识 |
| 文件上传、重命名、分类、标签、删除 | `biz-service` | 负责鉴权、元数据、MinIO 原始文件路径、审计字段 |
| 公共知识库新增、编辑、删除、上下线 | `biz-service` | 默认仅 `ADMIN`、`SUPER_ADMIN` 可操作 |
| 文档解析、语义分块、embedding、向量入库 | `ai-engine` 内网接口 | Java 创建任务，Python 执行 RAG 处理 |
| 文档重建索引、向量删除、检索测试 | `ai-engine` 内网接口 | 只允许 Java 内部调用 |
| 客服对话时 RAG 检索与上下文组装 | `ai-engine` | 在模型流式生成前动态完成 |
| Function Call 业务防腐 | `biz-service` | Python 通过 `/internal/tools/{toolName}` 调用 |
| 最终消息持久化 | `biz-service` | Python 通过 `/internal/chat/message-completed` 提交结果 |

核心原则：

- Java 是知识库管理的唯一对外入口，前端不直接访问 Python 引擎。
- Python 不直接决定用户是否有权访问某份知识，权限过滤条件由 Java `biz-service` 注入。
- Python 查询向量库时必须执行 Java 注入的元数据预过滤条件，例如 `tenant_id`、`scope`、`owner_user_id`、`document_id`、`category_id`、`tag_id`、`status`、`enabled`。Python 不得自行拼装或改写 `status=READY AND enabled=true` 这类业务可检索规则。
- 公共知识库默认只读开放给普通用户；公共知识库增删改默认只允许管理员和超级管理员。
- 管理员和超级管理员可以启用/禁用普通公共知识库；客服案例库是公共知识库的一种，使用 `scope=PUBLIC`、`source_type=CASE_LIBRARY` 标识，默认启用，只有超级管理员可以禁用/启用。
- 个人知识库默认 Fail-Closed：除本人、管理员审计流程外，其他用户不可见。

### 19.2 总体链路

```mermaid
flowchart LR
    FE[Frontend 知识库管理页]
    G[gateway-service]
    B[biz-service 知识库管理 API]
    DB[(aicsp_biz / biz_service)]
    MinIO[(MinIO 原始文件)]
    MQ[[RocketMQ 可选]]
    P[ai-engine 内网 RAG API]
    VDB[(aicsp_engine / pgvector)]

    FE -->|/api/knowledge/**| G
    G -->|鉴权后转发身份头| B
    B -->|写 Media 元数据/状态| DB
    B -->|上传原始文件| MinIO
    B -->|同步 HTTP 或异步任务| P
    B -. 后续可选 .-> MQ
    MQ -. 入库任务 .-> P
    P -->|读取文件| MinIO
    P -->|chunk / embedding / index| VDB
    P -->|回写处理结果| B
    B -->|更新状态| DB
```

本项目第一阶段可先使用 `biz-service -> ai-engine` 的同步 HTTP 内网调用触发入库任务；当批量上传和大文件处理变多后，再切换为 RocketMQ 异步任务。

### 19.3 数据所有权

| 数据 | 所有者 | 存储建议 |
|---|---|---|
| 用户可见文档元数据 `Media` | Java `biz-service` | `aicsp_biz.biz_service` |
| 原始文件 | Java `biz-service` | MinIO bucket，例如 `aicsp-kb` |
| 解析后 chunk 文本与向量 | Python `ai-engine` | `aicsp_engine.engine_service` + pgvector |
| 入库任务状态 | Java 为主，Python 回写结果 | Java 保存用户可见状态，Python 保存处理日志 |
| 检索日志、命中片段、质量评估 | Python `ai-engine` | `aicsp_engine.engine_service` |

这样设计可以保证用户看到的“知识库文档”与权限、审计、文件管理保持在 Java 业务体系内；RAG 专用的 chunk、embedding、检索日志则留在 Python 引擎内，避免 Java 被 AI 处理细节污染。

### 19.4 Java `biz-service` API 设计

所有接口均通过 `gateway-service` 访问，路径前缀为 `/api/knowledge`。Controller 返回值继续遵循 Java 侧统一响应体 `R<T>`；SSE 或文件下载接口除外。

#### 19.4.1 公共知识库

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/api/knowledge/public/documents` | 登录用户 | 公共知识库列表，只返回已发布或当前用户可见状态 |
| `GET` | `/api/knowledge/public/documents/{documentId}` | 登录用户 | 查看公共文档详情、元数据、处理状态 |
| `GET` | `/api/knowledge/public/documents/{documentId}/preview` | 登录用户 | 预览原始文件或解析文本 |
| `POST` | `/api/knowledge/public/documents` | `ADMIN` / `SUPER_ADMIN` | 新增公共知识文档并触发入库 |
| `PUT` | `/api/knowledge/public/documents/{documentId}` | `ADMIN` / `SUPER_ADMIN` | 修改标题、分类、标签、产品线、有效期 |
| `DELETE` | `/api/knowledge/public/documents/{documentId}` | `ADMIN` / `SUPER_ADMIN` | 删除公共文档，并触发向量清理 |
| `POST` | `/api/knowledge/public/documents/{documentId}/publish` | `ADMIN` / `SUPER_ADMIN` | 发布或重新发布 |
| `POST` | `/api/knowledge/public/documents/{documentId}/disable` | `ADMIN` / `SUPER_ADMIN` | 下线文档，检索时不可命中 |
| `POST` | `/api/knowledge/public/documents/{documentId}/enable` | `ADMIN` / `SUPER_ADMIN` | 启用普通公共文档；若 `source_type=CASE_LIBRARY`，仅 `SUPER_ADMIN` 可操作 |
| `POST` | `/api/knowledge/public/documents/{documentId}/reindex` | `ADMIN` / `SUPER_ADMIN` | 重建 chunk 与向量索引 |

公共知识库启用/禁用规则：

- 普通公共知识库文档：管理员和超级管理员都可以启用/禁用。
- 客服案例库文档：默认启用，只能由超级管理员禁用/启用。
- 禁用文档不进入 `selectable`，也不参与 RAG 检索。
- `status=READY` 且 `enabled=true` 才能被问答侧选择和检索。

#### 19.4.2 个人知识库

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/api/knowledge/personal/documents` | 本人 | 当前用户个人知识库列表 |
| `GET` | `/api/knowledge/personal/documents/{documentId}` | 本人 | 查看个人文档详情 |
| `POST` | `/api/knowledge/personal/documents` | 本人 | 上传个人文档并触发入库 |
| `PUT` | `/api/knowledge/personal/documents/{documentId}` | 本人 | 修改标题、目录、标签 |
| `DELETE` | `/api/knowledge/personal/documents/{documentId}` | 本人 | 删除个人文档，并触发向量和原始文件清理 |
| `POST` | `/api/knowledge/personal/documents/{documentId}/reindex` | 本人 | 重新入库个人文档 |
| `POST` | `/api/knowledge/personal/documents/{documentId}/contribute` | 本人 | 申请贡献到公共知识库，进入管理员审核 |

贡献审批规则：

- 普通用户只能贡献自己的个人知识库文档。
- 贡献申请进入管理员审批流，状态为 `PENDING_APPROVAL`。
- 管理员或超级管理员审批通过后，系统复制生成公共知识库文档，原个人文档仍保留在个人知识库。
- 审批通过后的公共知识库文档默认 `enabled=false`，不进入 `selectable`，不参与 RAG 检索。
- 审批通过处理器创建公共副本时必须在应用层显式写入 `enabled=false`，不得省略 `enabled` 字段后依赖数据库默认值。
- 普通公共文档可由管理员手动启用。
- 如果贡献内容识别为客服案例库（`source_type=CASE_LIBRARY`），审批通过后仍默认禁用，且只有超级管理员可启用。
- 审批拒绝必须填写拒绝原因，用户可查看申请结果。

贡献审批接口：

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/api/knowledge/contributions/mine` | 本人 | 查看我的贡献申请 |
| `GET` | `/api/knowledge/contributions/pending` | `ADMIN` / `SUPER_ADMIN` | 查看待审批申请 |
| `POST` | `/api/knowledge/contributions/{applicationId}/approve` | `ADMIN` / `SUPER_ADMIN` | 审批通过，生成默认禁用的公共文档 |
| `POST` | `/api/knowledge/contributions/{applicationId}/reject` | `ADMIN` / `SUPER_ADMIN` | 审批拒绝 |

#### 19.4.3 分类、标签与管理辅助

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/api/knowledge/categories` | 登录用户 | 分类树，按公共/个人 scope 返回 |
| `POST` | `/api/knowledge/categories` | 公共需管理员，个人需本人 | 新增分类 |
| `PUT` | `/api/knowledge/categories/{categoryId}` | 公共需管理员，个人需本人 | 重命名、移动分类 |
| `DELETE` | `/api/knowledge/categories/{categoryId}` | 公共需管理员，个人需本人 | 删除空分类 |
| `GET` | `/api/knowledge/tags` | 登录用户 | 标签候选 |
| `GET` | `/api/knowledge/documents/{documentId}/ingestion` | 有文档访问权 | 查看入库进度、失败原因、chunk 数、向量维度 |
| `POST` | `/api/knowledge/documents/{documentId}/retry` | 有管理权 | 重试失败任务 |
| `GET` | `/api/knowledge/selectable` | 登录用户 | 获取智能问答可启用的知识库、分类、标签和文档范围 |

`/api/knowledge/selectable` 只返回当前用户有权访问且可检索的知识范围：

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
      "categoryId": "cat_after_sales"
    }
  ]
}
```

### 19.5 Python `ai-engine` 内网 RAG API

Python 引擎的知识库相关接口只允许 Java 内部调用，不经前端暴露。

| 方法 | 路径 | 调用方 | 说明 |
|---|---|---|---|
| `POST` | `/internal/rag/ingest` | `biz-service` | 提交解析、分块、embedding、入库任务，快速返回任务接收结果 |
| `POST` | `/internal/rag/reindex` | `biz-service` | 提交清理旧索引并重新入库任务，快速返回任务接收结果 |
| `POST` | `/internal/rag/delete` | `biz-service` | 提交删除指定文档对应 chunk 与向量的任务，快速返回任务接收结果 |
| `POST` | `/internal/rag/retrieve` | `biz-service` | 内部检索测试；聊天检索由 `ai-engine` 在 `/api/chat/stream` 链路内完成召回，但必须先执行 `biz-service` 注入的过滤条件 |
| `GET` | `/internal/rag/tasks/{taskId}` | `biz-service` | 查询入库任务进度 |

`ingest` 请求示例：

```json
{
  "taskId": "kb_task_001",
  "documentId": "doc_001",
  "scope": "PUBLIC",
  "tenantId": "default",
  "ownerUserId": null,
  "objectPath": "kb/public/doc_001/manual.pdf",
  "title": "售后政策手册",
  "sourceType": "PDF",
  "productLine": "default",
  "tags": ["售后", "退款"],
  "traceId": "trace-001"
}
```

`ownerUserId` 约定：公共文档使用 `null`，表示不归属于单个用户；个人文档必须填写实际用户 ID。不得使用 `"0"` 这类隐式哨兵值表达公共或系统归属，避免与真实用户 ID、审计规则和数据库约束混淆。

`ingest`、`reindex`、`delete` 均为异步任务提交接口。Python 引擎接收请求、完成基础参数校验并持久化/登记任务后必须快速返回，不得在 HTTP 请求内等待完整解析、分块、embedding 或索引写入完成。

成功提交响应示例：

```json
{
  "taskId": "kb_task_001",
  "documentId": "doc_001",
  "status": "SUBMITTED",
  "accepted": true,
  "traceId": "trace-001"
}
```

响应语义：

- `accepted=true` 仅表示任务已被 Python 引擎接收，不表示入库完成。
- `status=SUBMITTED` 是初始任务状态；后续状态通过 `GET /internal/rag/tasks/{taskId}` 或入库回调更新。
- 若参数校验、权限 token 或对象路径校验失败，应返回 4xx，不得创建后台任务。
- 若任务已按相同 `taskId` 接收，重复提交应返回同一 `taskId` 的当前状态，避免重复入库。

`retrieve` 请求必须携带权限过滤条件：

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

Python 侧处理规则：

- `tenantId`、`userId`、`allowedScopes` 与最终 `filters` 由 Java `biz-service` 生成或转发，优先级高于用户输入。
- 普通聊天链路中，Python 收到 `knowledgeSelection` 后必须先让 `biz-service` 解析为权威检索过滤条件；`knowledgeSelection` 只能作为用户意图候选，不能由 Python 直接解释为授权结果。
- `knowledgeSelection.mode=NONE` 时跳过向量检索，并记录 `knowledge_selection=none`。
- `knowledgeSelection.mode=DEFAULT/SELECTED` 时，Python 只执行 Java 注入的过滤条件和候选 ID 集合；不得在 Python 内部维护独立的 `PUBLIC`、`PERSONAL`、`status=READY`、`enabled=true`、`owner_user_id=userId` 判断逻辑。
- 任意 `documentId` 若被 Java 侧解析结果剔除，Python 不得再次放回候选集合，也不向模型暴露原因细节。
- 在 `SELECTED` 模式下，如果 Java 侧返回被拒绝候选摘要，Python 必须在 `engine_retrieval_log` 中记录 `access_denied` 安全审计事件，字段至少包含 `trace_id`、`tenant_id`、`user_id`、`document_id_hash`、`scope`、`reason`、`timestamp`；日志不得保存完整文档正文或泄露文档所有者敏感信息。

### 19.6 知识库文档状态机

```mermaid
stateDiagram-v2
    [*] --> UPLOADED
    UPLOADED --> PARSING
    PARSING --> CHUNKING
    CHUNKING --> EMBEDDING
    EMBEDDING --> INDEXING
    INDEXING --> READY
    PARSING --> FAILED
    CHUNKING --> FAILED
    EMBEDDING --> FAILED
    INDEXING --> FAILED
    FAILED --> PARSING: retry
    READY --> DELETED
```

状态说明：

| 状态 | 用户可见文案 | 是否可检索 | 说明 |
|---|---|---|---|
| `UPLOADED` | 已上传 | 否 | Java 已保存文件和元数据 |
| `PARSING` | 解析中 | 否 | Python 正在提取正文 |
| `CHUNKING` | 分块中 | 否 | 正在生成 chunk |
| `EMBEDDING` | 向量化中 | 否 | 正在调用 embedding 模型 |
| `INDEXING` | 入库中 | 否 | 正在写入 pgvector |
| `READY` | 已就绪 | 是 | 可用于 RAG 检索；若管理侧 `enabled=false`，则不可检索 |
| `FAILED` | 入库失败 | 否 | 显示失败原因，允许重试 |
| `DELETED` | 已删除 | 否 | 软删除记录，后台清理文件和向量 |

`status` 只描述文档入库生命周期与删除状态；管理员启用/禁用开关由 Java `biz-service` 的 `enabled` 字段表达。已就绪但被禁用统一表示为 `status=READY AND enabled=false`，不得使用 `DISABLED` 作为文档状态。

### 19.7 前端页面信息架构

知识库管理页作为实际工作台，不做营销式页面。推荐入口为左侧主导航的“知识库”。

```text
知识库管理
├── 顶部工具栏
│   ├── scope 切换：公共知识库 / 我的知识库
│   ├── 搜索框：标题、标签、正文关键词
│   ├── 筛选器：状态、类型、分类、产品线、更新时间
│   └── 主按钮：上传文档
├── 左侧分类树
│   ├── 全部
│   ├── 未分类
│   └── 自定义分类
├── 中间文档列表
│   ├── 表格视图：标题、类型、状态、分类、标签、更新时间、命中次数、操作
│   └── 空状态 / 加载态 / 失败态
└── 右侧详情抽屉
    ├── 基础信息
    ├── 解析状态
    ├── 文档预览
    ├── chunk 片段
    ├── 检索测试
    └── 操作历史
```

布局原则：

- 主界面以表格和筛选为主，适合重复管理和快速扫描。
- 分类树固定宽度，列表区域自适应；移动端分类树收起为抽屉。
- 操作按钮使用图标 + tooltip；危险操作使用二次确认。
- 不嵌套卡片，文档详情使用右侧抽屉而不是跳转，减少管理任务上下文切换。

### 19.8 公共知识库交互

普通用户视角：

- 可以浏览公共知识库列表。
- 可以搜索、筛选、查看详情、查看来源片段。
- 可以在客服回答中查看引用来源。
- 不显示新增、编辑、删除、发布、下线、重建索引按钮。

管理员 / 超级管理员视角：

- 可以上传公共文档。
- 可以编辑标题、分类、标签、产品线、有效期。
- 可以发布、下线、删除、重建索引、启用、禁用普通公共文档。
- 客服案例库默认启用；启用/禁用客服案例库的操作仅超级管理员可见。
- 可以查看入库失败原因、处理耗时、chunk 数、embedding 维度。
- 可以查看知识命中统计、用户反馈、低质量回答关联文档。

公共知识库上传流程：

```text
1. 点击“上传文档”
2. 选择 scope=公共知识库
3. 拖拽或选择文件，支持 PDF / Word / Markdown / HTML / TXT
4. 填写标题、分类、标签、产品线、有效期
5. 点击“上传并入库”
6. 列表新增记录，状态显示“解析中”
7. 状态依次流转：解析中 -> 分块中 -> 向量化中 -> 入库中 -> 已就绪
8. 失败时展示失败原因和“重试”按钮
```

### 19.9 个人知识库交互

个人知识库面向普通用户，重点是易用和隔离：

- 用户只能看到自己的文档。
- 用户可以上传、重命名、移动分类、删除、重建索引自己的文档。
- 用户可以查看自己的存储使用量和文档处理状态。
- 删除时明确提示“会从知识库检索中移除，并清理相关向量索引”。
- 用户可以对个人文档发起“贡献到公共知识库”申请，申请进入管理员审批流；审批通过后公共副本默认禁用，需要管理员手动启用后才可被普通用户检索。

个人知识库推荐交互：

```text
我的知识库
├── 顶部：已用容量 / 文档数 / 可检索文档数
├── 上传入口：拖拽上传，默认归入“未分类”
├── 文档列表：更强调状态、最近更新时间、是否可检索
└── 详情抽屉：预览、片段、检索测试、删除
```

### 19.10 智能问答知识库选择交互

智能问答页需要提供“知识库范围选择器”，让用户在发起问题前选择本次启用哪些知识库。该控件位于输入框上方或输入框左侧工具区，默认折叠展示，避免干扰高频对话。

推荐交互：

```text
智能问答输入区
├── 知识库选择器
│   ├── 默认：公共知识库 + 我的知识库
│   ├── 仅公共知识库
│   ├── 仅我的知识库
│   ├── 自定义选择
│   └── 不使用知识库
├── 已选范围摘要：公共 18 篇 · 个人 6 篇 · 售后政策
└── 输入框 + 发送按钮
```

自定义选择弹层：

| 区域 | 交互 |
|---|---|
| 顶部搜索 | 按文档标题、分类、标签搜索可选知识 |
| Scope 分组 | 公共知识库、我的知识库分组展示 |
| 分类树 | 勾选分类时自动包含分类下 READY 文档 |
| 标签筛选 | 可叠加标签过滤，适合按业务主题选择 |
| 文档列表 | 展示标题、scope、状态、更新时间，支持多选 |
| 底部摘要 | 展示最终将启用的文档数量，提供清空和确认 |

状态与规则：

- 默认模式为 `DEFAULT`，即公共知识库 + 我的知识库。
- 用户选择会话级生效；同一会话后续问题沿用上次选择，直到用户修改。
- 每次发送请求时，前端都随请求提交当前 `knowledgeSelection` 快照。
- 如果用户选择的文档后来被删除、下线或入库失败，后端自动剔除，并可在回答结束后通过提示说明“部分知识库不可用，已自动忽略”。
- 普通用户在选择器里只能看到公共 `READY + enabled` 文档和自己的个人 `READY + enabled` 文档。
- 管理员虽然可管理公共知识库，但普通问答时仍遵循同样的选择器逻辑；如需审计他人个人知识库，应走管理后台，不混入普通问答入口。

前端发送聊天请求示例：

```json
{
  "messageId": "m_001",
  "sessionId": "s_001",
  "message": "退款多久到账？",
  "knowledgeSelection": {
    "mode": "SELECTED",
    "includePublic": true,
    "includePersonal": false,
    "categoryIds": ["cat_after_sales"],
    "documentIds": ["doc_public_001"]
  }
}
```

Java `stream-service` 处理要求：

- `ChatRequest` 增加可选字段 `knowledgeSelection`，不影响旧客户端。
- `stream-service` 读取用户身份头后，将用户身份与 `knowledgeSelection` 一起转成 `EngineRequest`。
- `stream-service` 不信任前端传入的用户、租户、角色字段；这些字段只能来自网关注入头。
- 如果选择器为空，按 `DEFAULT` 处理。

### 19.11 文档详情抽屉

详情抽屉用于避免用户在列表和详情页之间频繁跳转。

| Tab | 内容 |
|---|---|
| 基础信息 | 标题、文件类型、大小、分类、标签、scope、创建者、更新时间 |
| 处理状态 | 当前状态、失败原因、耗时、chunk 数、embedding 模型、重试入口 |
| 文档预览 | 原始文件预览或解析文本预览 |
| 知识片段 | chunk 列表、父子 chunk、元数据、是否启用 |
| 检索测试 | 输入测试问题，展示召回片段、分数、来源 |
| 使用反馈 | 命中次数、有帮助率、低分回答关联 |
| 操作历史 | 上传、编辑、发布、下线、重建索引、删除记录 |

### 19.12 列表字段与筛选器

文档列表字段：

| 字段 | 说明 |
|---|---|
| 标题 | 主识别字段，点击打开详情抽屉 |
| Scope | 公共 / 个人 |
| 类型 | PDF、Word、Markdown、HTML、TXT |
| 状态 | 已就绪、处理中、失败、已下线 |
| 分类 | 分类树节点 |
| 标签 | 最多展示 3 个，剩余折叠 |
| 产品线 | 用于检索前过滤 |
| 更新时间 | 最近元数据或索引更新时间 |
| 命中次数 | 管理员可见，用于识别高价值文档 |
| 操作 | 预览、编辑、重建、下线、删除 |

筛选器：

- 关键词：标题、标签、摘要。
- 状态：处理中、已就绪、失败、已下线。
- 类型：PDF、Word、Markdown、HTML、TXT。
- Scope：公共、个人。
- 分类与产品线。
- 更新时间范围。

### 19.13 权限矩阵

| 操作 | 普通用户 | 管理员 | 超级管理员 |
|---|---|---|---|
| 查看公共知识库 | 允许 | 允许 | 允许 |
| 新增公共文档 | 禁止 | 允许 | 允许 |
| 编辑公共文档 | 禁止 | 允许 | 允许 |
| 删除公共文档 | 禁止 | 允许 | 允许 |
| 发布/下线公共文档 | 禁止 | 允许 | 允许 |
| 启用/禁用普通公共文档 | 禁止 | 允许 | 允许 |
| 启用/禁用客服案例库 | 禁止 | 禁止 | 允许 |
| 重建公共索引 | 禁止 | 允许 | 允许 |
| 查看自己的个人知识库 | 允许 | 允许 | 允许 |
| 管理自己的个人文档 | 允许 | 允许 | 允许 |
| 发起个人知识贡献申请 | 允许 | 允许 | 允许 |
| 审批知识贡献申请 | 禁止 | 允许 | 允许 |
| 问答时选择公共 READY 知识 | 允许 | 允许 | 允许 |
| 问答时选择自己的个人 READY 知识 | 允许 | 允许 | 允许 |
| 查看他人个人知识库 | 禁止 | 审计场景可选 | 允许 |
| 删除他人个人文档 | 禁止 | 禁止或审计授权 | 允许 |

后端必须做强校验，前端隐藏按钮只作为体验优化，不能作为权限依据。

### 19.14 成熟方案借鉴点

本设计吸收的成熟 RAG/企业搜索方案经验：

- 采用文档级访问控制思想，索引中保留 `scope`、`tenant_id`、`owner_user_id`、`status` 等权限元数据，查询时强制过滤。
- 向量检索使用预过滤优先，先缩小到用户有权访问的候选集，再做向量召回，减少越权和无关召回。
- 内容准备阶段拆成上传、解析、分块、向量化、索引、发布多个状态，所有状态用户可见。
- 列表管理参考云存储与企业知识库产品的“分类树 + 筛选表格 + 详情抽屉”模式，优先保证可扫描、可批量操作、可定位失败原因。
- RAG 检索结果需要能回溯到文档、chunk 和操作历史，便于管理员修正文档和持续优化知识库质量。

---

## 20. 参考资料

- `README.md`
- `docs/sdd/初始化Java服务.md`
- `docs/sdd/agent设计哲学.md`
- `docs/sdd/dev环境各组件连接信息.md`
- Python.org Downloads：`https://www.python.org/downloads/`
- Python Developer's Guide Status of Python versions：`https://devguide.python.org/versions/`
- uv Project Structure：`https://docs.astral.sh/uv/concepts/projects/layout/`
- uv Managing Dependencies：`https://docs.astral.sh/uv/concepts/projects/dependencies/`
- PyPI FastAPI：`https://pypi.org/project/fastapi/`
- PyPI Uvicorn：`https://pypi.org/project/uvicorn/`
- PyPI Pydantic：`https://pypi.org/project/pydantic/`
- PyPI LangChain：`https://pypi.org/project/langchain/`
- PyPI LangGraph：`https://pypi.org/project/langgraph/`
- PyPI LangChain OpenAI：`https://pypi.org/project/langchain-openai/`
- PyPI OpenAI：`https://pypi.org/project/openai/`
- Ollama OpenAI Compatibility：`https://docs.ollama.com/api/openai-compatibility`
- Azure AI Search RAG overview：`https://learn.microsoft.com/en-us/azure/search/retrieval-augmented-generation-overview`
- Azure AI Search document-level access control：`https://learn.microsoft.com/en-us/azure/search/search-document-level-access-overview`
- Azure AI Search vector query filters：`https://learn.microsoft.com/en-us/azure/search/vector-search-filters`
