import pytest

from aicsp_engine.core.config import Settings
from aicsp_engine.models.rag import IngestRequest
from aicsp_engine.rag.ingestion import IngestionPipeline
from aicsp_engine.rag.tasks import InMemoryTaskStore


@pytest.mark.asyncio
async def test_ingestion_pipeline_chunks_embeds_and_marks_ready(monkeypatch: pytest.MonkeyPatch) -> None:
    stored = {}

    async def fake_embed_texts(self: object, texts: list[str]) -> list[list[float]]:
        return [[0.1, 0.2, 0.3] for _ in texts]

    async def fake_upsert(
        self: object,
        chunks: object,
        embeddings: object,
        model: str,
        dimensions: int,
    ) -> None:
        stored["chunks"] = chunks
        stored["embeddings"] = embeddings
        stored["model"] = model
        stored["dimensions"] = dimensions

    async def fake_mark_deleted(self: object, document_id: str, active_chunk_ids: list[str]) -> None:
        stored["document_id"] = document_id
        stored["active_chunk_ids"] = active_chunk_ids

    async def fake_task_upsert(
        self: object,
        task_id: str,
        document_id: str,
        operation: str,
        status: str,
        trace_id: str | None,
        message: str | None,
        metrics: dict[str, object] | None = None,
    ) -> None:
        stored["last_task_status"] = status

    async def fake_callback(self: object, payload: dict[str, object], trace_id: str | None) -> None:
        stored["callback"] = payload

    monkeypatch.setattr("aicsp_engine.llm.embedding_model.EmbeddingModel.embed_texts", fake_embed_texts)
    monkeypatch.setattr(
        "aicsp_engine.storage.postgres.PostgresVectorStore.upsert_chunks_with_embeddings",
        fake_upsert,
    )
    monkeypatch.setattr(
        "aicsp_engine.storage.postgres.PostgresVectorStore.mark_document_missing_chunks_deleted",
        fake_mark_deleted,
    )
    monkeypatch.setattr(
        "aicsp_engine.storage.postgres.PostgresVectorStore.upsert_ingestion_task",
        fake_task_upsert,
    )
    monkeypatch.setattr(
        "aicsp_engine.tools.biz_client.BizServiceClient.submit_ingestion_callback",
        fake_callback,
    )

    task_store = InMemoryTaskStore()
    request = IngestRequest(
        taskId="task_001",
        documentId="doc_001",
        scope="PUBLIC",
        tenantId="default",
        title="售后案例库",
        sourceType="CASE_LIBRARY",
        rawText="""# T2026042600000001
## 问题描述
支付成功后订单仍显示待支付。
## 解决方案与措施
1. 查询支付流水。
2. 触发订单状态同步。
""",
    )
    task_store.submit(request.taskId, request.documentId, request.traceId, "INGEST")

    pipeline = IngestionPipeline(
        Settings(
            vector_store_enabled=True,
            postgres_dsn="postgresql://example",
            embedding_dimensions=3,
            embedding_model="test-embedding",
        ),
        task_store,
    )
    await pipeline.ingest(request)

    task = task_store.get("task_001")
    assert task is not None
    assert task.status == "READY"
    assert task.progress == 100
    assert task.metrics["chunk_count"] >= 3
    assert stored["model"] == "test-embedding"
    assert stored["dimensions"] == 3
    assert stored["document_id"] == "doc_001"
    assert stored["callback"] == {
        "taskId": "task_001",
        "documentId": "doc_001",
        "status": "READY",
        "progress": 100,
        "chunkCount": task.metrics["chunk_count"],
        "embeddingModel": "test-embedding",
        "errorMessage": None,
        "traceId": None,
    }
