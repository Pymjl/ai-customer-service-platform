import pytest
from typing import cast

from aicsp_engine.core.config import Settings
from aicsp_engine.models.rag import RetrieveHit, RetrieveRequest
from aicsp_engine.rag.retriever import Retriever


@pytest.mark.asyncio
async def test_retriever_expands_child_hits_to_parent(monkeypatch: pytest.MonkeyPatch) -> None:
    stored: dict[str, object] = {}

    async def fake_embed_texts(self: object, texts: list[str]) -> list[list[float]]:
        assert texts == ["退款多久到账"]
        return [[0.1, 0.2, 0.3]]

    async def fake_similarity_search(
        self: object,
        request: RetrieveRequest,
        query_embedding: list[float],
    ) -> list[RetrieveHit]:
        stored["query_embedding"] = query_embedding
        return [
            RetrieveHit(
                chunkId="child_001",
                parentChunkId="parent_001",
                documentId="doc_001",
                content="退款审核通过后通常 1-3 个工作日到账。",
                score=0.88,
                metadata={
                    "chunk_type": "SECTION_CHILD",
                    "case_field_title": "退款规则",
                    "vector_score": 0.9,
                    "quality_score": 1.1,
                    "final_score": 0.88,
                },
            )
        ]

    async def fake_expand_parent_hits(
        self: object,
        hits: list[RetrieveHit],
    ) -> list[RetrieveHit]:
        assert hits[0].chunkId == "child_001"
        return [
            RetrieveHit(
                chunkId="parent_001",
                parentChunkId=None,
                documentId="doc_001",
                content="【退款规则】退款审核通过后通常 1-3 个工作日到账。",
                score=0.88,
                metadata={
                    "chunk_type": "DOCUMENT_PARENT",
                    "matched_child_ids": ["child_001"],
                    "matched_child_fields": ["退款规则"],
                    "vector_score": 0.9,
                    "quality_score": 1.1,
                    "final_score": 0.88,
                },
            )
        ]

    async def fake_write_retrieval_log(
        self: object,
        request: RetrieveRequest,
        hits: list[RetrieveHit],
        skipped: bool,
        reason: str | None,
        duration_ms: int,
    ) -> None:
        stored["logged_hits"] = hits
        stored["logged_reason"] = reason
        stored["duration_ms"] = duration_ms

    monkeypatch.setattr("aicsp_engine.llm.embedding_model.EmbeddingModel.embed_texts", fake_embed_texts)
    monkeypatch.setattr(
        "aicsp_engine.storage.postgres.PostgresVectorStore.similarity_search",
        fake_similarity_search,
    )
    monkeypatch.setattr(
        "aicsp_engine.storage.postgres.PostgresVectorStore.expand_parent_hits",
        fake_expand_parent_hits,
    )
    monkeypatch.setattr(
        "aicsp_engine.storage.postgres.PostgresVectorStore.write_retrieval_log",
        fake_write_retrieval_log,
    )

    response = await Retriever(
        Settings(
            vector_store_enabled=True,
            postgres_dsn="postgresql://example",
            embedding_dimensions=3,
        )
    ).retrieve(
        RetrieveRequest(
            query="退款多久到账",
            tenantId="default",
            userId="u_001",
            allowedScopes=["PUBLIC", "PERSONAL"],
            filters={"status": "READY", "enabled": True},
            traceId="trace_001",
        )
    )

    assert stored["query_embedding"] == [0.1, 0.2, 0.3]
    assert response.hits[0].chunkId == "parent_001"
    assert response.hits[0].content.startswith("【退款规则】")
    assert response.hits[0].metadata["matched_child_ids"] == ["child_001"]
    logged_hits = cast(list[RetrieveHit], stored["logged_hits"])
    assert logged_hits[0].chunkId == "parent_001"
    assert stored["logged_reason"] is None
