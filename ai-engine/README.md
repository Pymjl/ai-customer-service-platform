# AICSP Python Engine

`ai-engine` 是智能客服平台的 Python 引擎层，负责模型调用、RAG 分片/检索、工具编排和 NDJSON 流式响应。

## 本阶段能力

- `GET /healthz`：健康检查。
- `POST /api/chat/stream`：接收 `stream-service` 的 `EngineRequest`，返回 `application/x-ndjson` 事件流。
- `POST /internal/rag/ingest|reindex|delete|retrieve`：按设计文档预留的内网 RAG 接口。
- Markdown/TXT 第一阶段分片器：客服案例库 `CASE_PARENT`、`CASE_CHILD`、`CASE_SUMMARY`，以及通用标题/FAQ/段落 fallback。

## 启动

```bash
uv sync
uv run uvicorn aicsp_engine.main:app --reload --host 0.0.0.0 --port 8000
```

默认 `AICSP_LLM_ENABLED=false`，聊天接口会返回占位流式结果。接入 OpenAI 兼容模型后，将 `.env` 中模型配置补齐并启用即可。

## 验证

```bash
uv run pytest
uv run ruff check .
```
