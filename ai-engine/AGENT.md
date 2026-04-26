# ai-engine 后续会话恢复上下文

本文档用于后续开启新会话时快速恢复 `ai-engine` 的当前状态、关键约定和待实现 Agent 功能。

## 当前项目状态

- 根目录：`D:\Projects\Java\ai-customer-service-platform\ai-engine`
- 技术栈：Python 3.14、uv、FastAPI、OpenAI Python SDK、LangGraph、PostgreSQL + pgvector、Ollama OpenAI 兼容接口。
- 已提交过一次基础实现：`359b2d7 实现 Python 引擎层并修复入库回调契约`。
- 当前工作区仍有未提交改动，主要集中在 `ai-engine`；另外存在若干 `biz-service` 未提交改动，后续不要误回退。

## 已实现能力

- FastAPI 路由：
  - `GET /healthz`
  - `POST /api/chat/stream`
  - `POST /internal/rag/ingest`
  - `POST /internal/rag/reindex`
  - `POST /internal/rag/delete`
  - `POST /internal/rag/retrieve`
  - `GET /internal/rag/tasks/{taskId}`
  - `POST /internal/rag/chunk/preview`
- RAG 分片：
  - 客服案例库：`CASE_PARENT`、`CASE_CHILD`、`CASE_SUMMARY`
  - 通用文档：FAQ、标题章节、语义段落 fallback
  - 稳定 chunk id、parent-child 关系、ACL metadata、质量分
- RAG 入库：
  - 支持 raw text、本地文件、MinIO 文本对象读取
  - chunking、embedding、PG upsert、旧 chunk 软删除
  - 入库任务状态同步到 PG
  - `biz-service /internal/knowledge/ingestion-callback` 回调字段已对齐 Java DTO：`progress`、`chunkCount`、`embeddingModel`、`errorMessage`
- RAG 检索：
  - 向量召回 child chunk
  - 命中 child 后展开 parent chunk 注入模型
  - 检索日志区分 `hit_chunk_ids` 和 `hit_parent_ids`
  - citation 中包含 `matchedChildIds`、`matchedFields`
- 模型调用：
  - embedding 模型：`qwen3-embedding:8b`
  - LLM：`gemma4:26b`
  - Ollama OpenAI 兼容地址：`http://localhost:11434/v1`
  - OpenAI SDK 客户端已统一使用 `httpx.AsyncClient(trust_env=False)`，避免本机代理影响 localhost 请求
- 提示词管理：
  - 所有智能客服全局提示词、RAG 模板、工具结果模板、兜底话术集中在 `src/aicsp_engine/core/prompts.py`
  - 智能客服全局提示词要求简体中文、语气诚恳耐心、知识库/工具优先、不输出推理过程、不编造信息、失败时建议人工客服
- SQL：
  - `sql/001_create_engine_database.sql` 可在 IDEA / DataGrip PG Console 中重复执行，使用 `dblink` 做数据库存在性判断
  - `sql/002_create_engine_schema.sql` 可重复执行
  - `qwen3-embedding:8b` 实测向量维度为 4096
  - pgvector HNSW 索引不支持超过 2000 维，因此当前不创建向量 ANN 索引，第一阶段使用精确相似度排序
- 工具脚本：
  - `scripts/check_embedding_dimension.py` 可检测 Ollama embedding 实际维度

## 当前验证结果

最近一次已通过：

```bash
uv run ruff check .
uv run mypy src tests scripts
uv run pytest
uv run python scripts/check_embedding_dimension.py
```

真实模型调用结果：

- `EmbeddingModel` 可直接调用 Ollama，返回 4096 维向量。
- `CustomerServiceAgent` 在 `knowledgeSelection=NONE` 时可直接调用 `gemma4:26b`，英文测试可返回 `OK`。
- `gemma4:26b` 对中文短提示仍可能误判为“无法正常显示”，但全局提示词已约束其使用中文、耐心语气澄清。该问题更像模型或 tokenizer 兼容风险，提示词只能缓解。

## 关键文件

- 配置：`src/aicsp_engine/core/config.py`
- 统一提示词：`src/aicsp_engine/core/prompts.py`
- OpenAI 客户端工厂：`src/aicsp_engine/llm/clients.py`
- Chat 模型调用：`src/aicsp_engine/llm/chat_model.py`
- Embedding 调用：`src/aicsp_engine/llm/embedding_model.py`
- Agent 编排：`src/aicsp_engine/agent/graph.py`
- Agent 节点：`src/aicsp_engine/agent/nodes.py`
- Agent 策略：`src/aicsp_engine/agent/policies.py`
- RAG prompt 组装：`src/aicsp_engine/rag/prompt_context.py`
- RAG 检索：`src/aicsp_engine/rag/retriever.py`
- PG 存储：`src/aicsp_engine/storage/postgres.py`
- 入库流水线：`src/aicsp_engine/rag/ingestion.py`
- SQL：`sql/001_create_engine_database.sql`、`sql/002_create_engine_schema.sql`

## 当前已知风险

- `gemma4:26b` 中文理解不稳定，需要继续对比本地其他模型，如 `deepseek-r1:14b` 等。
- 4096 维向量不能创建 pgvector HNSW 索引；数据量变大后精确向量排序可能变慢。
- `/api/chat/stream` 默认问答会先走 RAG filter；如果 `biz-service` 不可用，会按聊天兜底话术建议联系人工。
- Parent 展开当前只按 `parent_chunk_id` 回查 READY + enabled parent；大规模场景还需要更严格的 ACL 二次校验或保留原过滤条件回查 parent。
- `rerank.py`、`observability/metrics.py` 仍是占位模块。

## 后续待实现 Agent 功能

1. 多轮会话上下文
   - 从 Java 侧接收最近 N 轮对话摘要或历史消息。
   - 在 prompt 中区分“已确认事实”和“用户本轮新问题”。
   - 避免把未确认猜测写入后续上下文。

2. Query Rewrite 与指代消解
   - 将“这个订单”“刚才那个退款”等指代补全。
   - 失败时使用原始 query 继续检索，并记录降级原因。
   - 重写结果不得扩大权限或替换用户身份。

3. 工具调用策略增强
   - 当前仅用规则识别订单/物流类工具。
   - 后续需要工具 schema-first 选择、参数校验、幂等 key、超时控制、敏感字段脱敏。
   - 工具失败时仅聊天场景使用 `HUMAN_HANDOFF_MESSAGE`。

4. RAG 检索增强
   - 增加关键词检索或 PostgreSQL full-text 检索。
   - 实现 dense + sparse RRF 融合。
   - 增加 reranker，并与 `quality_score` 融合排序。
   - 检索日志记录 rewrite、RRF、rerank、ACL 过滤摘要。

5. Parent 注入策略细化
   - 对过大的 parent 按命中 child 附近 sibling 截断。
   - 表格、FAQ、工单场景采用不同注入模板。
   - 多个字段命中同一工单时合并来源和分数解释。

6. Agent 状态图实装
   - 当前 `graph.py` 有简化 LangGraph 示例，真实执行仍主要是顺序调用节点。
   - 后续应将 prepare、intent、filter、retrieve、tool、generate、fallback 做成完整状态图。
   - 增加可中断、可恢复和循环上限。

7. 安全与合规
   - 明确系统提示词注入防护。
   - 对工具结果和 RAG 内容做敏感字段脱敏。
   - 对用户不可访问的文档只记录 hash，不向模型暴露拒绝原因细节。

8. 可观测性
   - 补齐结构化事件和指标导出。
   - 记录每轮 Agent 阶段耗时、模型 token、工具耗时、检索命中、降级原因。
   - 增加 answer quality / feedback 入口的预留事件。

9. 模型适配
   - 对 `gemma4:26b` 中文异常做更多真实样例测试。
   - 对比 `deepseek-r1:14b` 等本地模型。
   - 如模型返回 reasoning/thinking 字段，保持只输出 `delta.content`，不得透出 reasoning。

10. 集成测试
   - 增加真实 Ollama 可选集成测试标记。
   - 增加 PG 向量库集成测试，覆盖入库、检索、parent 展开、日志写入。
   - 增加 `/api/chat/stream` NDJSON 端到端测试。

## 后续工作注意事项

- 默认中文回复用户。
- 不要回退用户或其他任务在 `biz-service`、文档、前端中的未提交改动。
- 手动编辑文件优先使用 `apply_patch`。
- 修改模型、RAG、Agent 后至少运行：

```bash
uv run ruff check .
uv run mypy src tests scripts
uv run pytest
```

- 如果涉及 Ollama 调用，直接跑真实调用脚本或 inline 测试，不要只假设可用。
