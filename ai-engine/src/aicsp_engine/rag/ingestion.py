from __future__ import annotations

from time import perf_counter

from aicsp_engine.core.config import Settings
from aicsp_engine.llm.embedding_model import EmbeddingModel
from aicsp_engine.models.rag import IngestRequest
from aicsp_engine.rag.chunking import Chunk, ChunkDocument, chunk_document
from aicsp_engine.rag.tasks import InMemoryTaskStore
from aicsp_engine.storage.document_loader import DocumentLoader
from aicsp_engine.storage.postgres import PostgresVectorStore
from aicsp_engine.tools.biz_client import BizServiceClient


class IngestionPipeline:
    def __init__(self, settings: Settings, task_store: InMemoryTaskStore) -> None:
        self._settings = settings
        self._task_store = task_store
        self._loader = DocumentLoader(settings)
        self._embedding = EmbeddingModel(settings)
        self._store = PostgresVectorStore(settings)

    async def ingest(self, request: IngestRequest, operation: str = "INGEST") -> None:
        started = perf_counter()
        metrics: dict[str, object] = {
            "document_id": request.documentId,
            "operation": operation,
            "embedding_model": self._settings.embedding_model,
        }
        try:
            self._task_store.update(request.taskId, "PARSING", "正在提取文本", 10, metrics)
            await self._persist_task(request, operation, "PARSING", "正在提取文本", metrics)
            text = await self._loader.load_text(request)

            self._task_store.update(request.taskId, "CHUNKING", "正在生成知识片段", 35, metrics)
            await self._persist_task(request, operation, "CHUNKING", "正在生成知识片段", metrics)
            chunks = chunk_document(_to_chunk_document(request, text))
            metrics.update(_chunk_metrics(chunks))
            if not chunks:
                raise ValueError("未生成可入库 chunk")

            self._task_store.update(request.taskId, "EMBEDDING", "正在生成向量", 60, metrics)
            await self._persist_task(request, operation, "EMBEDDING", "正在生成向量", metrics)
            embeddings = await self._embed_chunks(chunks)

            self._task_store.update(request.taskId, "INDEXING", "正在写入向量库", 85, metrics)
            await self._persist_task(request, operation, "INDEXING", "正在写入向量库", metrics)
            await self._store.upsert_chunks_with_embeddings(
                chunks=chunks,
                embeddings=embeddings,
                model=self._settings.embedding_model,
                dimensions=self._settings.embedding_dimensions,
            )
            await self._store.mark_document_missing_chunks_deleted(
                request.documentId,
                [chunk.chunk_id for chunk in chunks],
            )

            metrics["duration_ms"] = int((perf_counter() - started) * 1000)
            self._task_store.update(request.taskId, "READY", "入库完成", 100, metrics)
            await self._persist_task(request, operation, "READY", "入库完成", metrics)
            await self._callback(request, "READY", 100, None, metrics)
        except Exception as exc:
            metrics["duration_ms"] = int((perf_counter() - started) * 1000)
            metrics["error_type"] = exc.__class__.__name__
            message = str(exc)
            self._task_store.update(request.taskId, "FAILED", message, 100, metrics)
            await self._persist_task(request, operation, "FAILED", message, metrics)
            await self._callback(request, "FAILED", 100, message, metrics)

    async def delete(self, task_id: str, document_id: str, trace_id: str | None) -> None:
        try:
            self._task_store.update(task_id, "INDEXING", "正在删除向量索引", 80)
            await self._store.delete_document(document_id)
            self._task_store.update(task_id, "READY", "删除完成", 100)
        except Exception as exc:
            self._task_store.update(task_id, "FAILED", str(exc), 100)

    async def _embed_chunks(self, chunks: list[Chunk]) -> list[list[float]]:
        embeddings: list[list[float]] = []
        batch_size = max(1, self._settings.ingestion_embedding_batch_size)
        for index in range(0, len(chunks), batch_size):
            batch = chunks[index : index + batch_size]
            embeddings.extend(await self._embedding.embed_texts([chunk.content for chunk in batch]))
        for embedding in embeddings:
            if len(embedding) != self._settings.embedding_dimensions:
                raise ValueError(
                    f"embedding 维度不匹配：期望 {self._settings.embedding_dimensions}，实际 {len(embedding)}"
                )
        return embeddings

    async def _callback(
        self,
        request: IngestRequest,
        status: str,
        progress: int,
        message: str | None,
        metrics: dict[str, object],
    ) -> None:
        try:
            await BizServiceClient(self._settings).submit_ingestion_callback(
                {
                    "taskId": request.taskId,
                    "documentId": request.documentId,
                    "status": status,
                    "progress": progress,
                    "chunkCount": _metric_int(metrics, "chunk_count"),
                    "embeddingModel": self._settings.embedding_model,
                    "errorMessage": message if status in {"FAILED", "FAILED_RETRYABLE"} else None,
                    "traceId": request.traceId,
                },
                request.traceId,
            )
        except Exception:
            return

    async def _persist_task(
        self,
        request: IngestRequest,
        operation: str,
        status: str,
        message: str,
        metrics: dict[str, object],
    ) -> None:
        await self._store.upsert_ingestion_task(
            task_id=request.taskId,
            document_id=request.documentId,
            operation=operation,
            status=status,
            trace_id=request.traceId,
            message=message,
            metrics=metrics,
        )


def _to_chunk_document(request: IngestRequest, text: str) -> ChunkDocument:
    return ChunkDocument(
        document_id=request.documentId,
        title=request.title,
        text=text,
        tenant_id=request.tenantId,
        scope=request.scope,
        owner_user_id=request.ownerUserId,
        source_type=request.sourceType,
        category_id=request.categoryId,
        tag_ids=request.tagIds or request.tags,
        product_line=request.productLine,
        status="READY",
        enabled=request.enabled,
    )


def _chunk_metrics(chunks: list[Chunk]) -> dict[str, object]:
    parent_count = sum(1 for chunk in chunks if chunk.parent_chunk_id is None)
    child_count = len(chunks) - parent_count
    token_counts = [chunk.token_count for chunk in chunks if chunk.parent_chunk_id is not None]
    return {
        "chunk_count": len(chunks),
        "parent_chunk_count": parent_count,
        "child_chunk_count": child_count,
        "avg_child_token_count": int(sum(token_counts) / len(token_counts)) if token_counts else 0,
        "case_count": len({chunk.case_id for chunk in chunks if chunk.case_id}),
    }


def _metric_int(metrics: dict[str, object], key: str) -> int:
    value = metrics.get(key)
    return value if isinstance(value, int) else 0
