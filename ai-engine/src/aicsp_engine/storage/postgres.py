from __future__ import annotations

import hashlib
from typing import Any

from psycopg import AsyncConnection
from psycopg.types.json import Jsonb
from psycopg.rows import dict_row
from psycopg.sql import SQL, Identifier

from aicsp_engine.core.config import Settings
from aicsp_engine.models.rag import RetrieveHit, RetrieveRequest
from aicsp_engine.rag.chunking import Chunk


class PostgresVectorStore:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    def is_configured(self) -> bool:
        return bool(self._settings.vector_store_enabled and self._settings.postgres_dsn)

    async def upsert_chunks_with_embeddings(
        self,
        chunks: list[Chunk],
        embeddings: list[list[float]],
        model: str,
        dimensions: int,
    ) -> None:
        dsn = self._settings.postgres_dsn
        if not self.is_configured() or dsn is None:
            raise RuntimeError("PostgreSQL vector store is not configured")
        if len(chunks) != len(embeddings):
            raise ValueError("chunks and embeddings length mismatch")

        chunk_query = SQL(
            """
            INSERT INTO {schema}.{chunk_table} (
                chunk_id, parent_chunk_id, document_id, tenant_id, scope, owner_user_id,
                chunk_type, source_type, case_id, case_field, section_path, content,
                content_hash, metadata, token_count, quality_score, status, enabled,
                deleted, updated_at
            )
            VALUES (
                %(chunk_id)s, %(parent_chunk_id)s, %(document_id)s, %(tenant_id)s,
                %(scope)s, %(owner_user_id)s, %(chunk_type)s, %(source_type)s,
                %(case_id)s, %(case_field)s, %(section_path)s, %(content)s,
                %(content_hash)s, %(metadata)s, %(token_count)s, %(quality_score)s,
                %(status)s, %(enabled)s, FALSE, CURRENT_TIMESTAMP
            )
            ON CONFLICT (chunk_id) WHERE deleted = FALSE
            DO UPDATE SET
                parent_chunk_id = EXCLUDED.parent_chunk_id,
                tenant_id = EXCLUDED.tenant_id,
                scope = EXCLUDED.scope,
                owner_user_id = EXCLUDED.owner_user_id,
                chunk_type = EXCLUDED.chunk_type,
                source_type = EXCLUDED.source_type,
                case_id = EXCLUDED.case_id,
                case_field = EXCLUDED.case_field,
                section_path = EXCLUDED.section_path,
                content = EXCLUDED.content,
                content_hash = EXCLUDED.content_hash,
                metadata = EXCLUDED.metadata,
                token_count = EXCLUDED.token_count,
                quality_score = EXCLUDED.quality_score,
                status = EXCLUDED.status,
                enabled = EXCLUDED.enabled,
                deleted = FALSE,
                updated_at = CURRENT_TIMESTAMP
            """
        ).format(
            schema=Identifier(self._settings.postgres_schema),
            chunk_table=Identifier(self._settings.postgres_chunk_table),
        )
        embedding_query = SQL(
            """
            INSERT INTO {schema}.{embedding_table} (
                chunk_id, model, dimensions, embedding, deleted, created_at
            )
            VALUES (%s, %s, %s, %s::vector, FALSE, CURRENT_TIMESTAMP)
            ON CONFLICT (chunk_id, model) WHERE deleted = FALSE
            DO UPDATE SET
                dimensions = EXCLUDED.dimensions,
                embedding = EXCLUDED.embedding,
                deleted = FALSE,
                created_at = CURRENT_TIMESTAMP
            """
        ).format(
            schema=Identifier(self._settings.postgres_schema),
            embedding_table=Identifier(self._settings.postgres_embedding_table),
        )

        async with await AsyncConnection.connect(dsn) as conn:
            async with conn.transaction():
                for chunk, embedding in zip(chunks, embeddings, strict=True):
                    await conn.execute(chunk_query, _chunk_params(chunk))
                    await conn.execute(
                        embedding_query,
                        (
                            chunk.chunk_id,
                            model,
                            dimensions,
                            _vector_literal(embedding),
                        ),
                    )

    async def mark_document_missing_chunks_deleted(
        self,
        document_id: str,
        active_chunk_ids: list[str],
    ) -> None:
        dsn = self._settings.postgres_dsn
        if not self.is_configured() or dsn is None:
            return
        query = SQL(
            """
            UPDATE {schema}.{chunk_table}
               SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP
             WHERE document_id = %s
               AND deleted = FALSE
               AND NOT (chunk_id = ANY(%s))
            """
        ).format(
            schema=Identifier(self._settings.postgres_schema),
            chunk_table=Identifier(self._settings.postgres_chunk_table),
        )
        async with await AsyncConnection.connect(dsn) as conn:
            await conn.execute(query, (document_id, active_chunk_ids))
            await conn.commit()

    async def delete_document(self, document_id: str) -> None:
        dsn = self._settings.postgres_dsn
        if not self.is_configured() or dsn is None:
            return
        chunk_query = SQL(
            """
            UPDATE {schema}.{chunk_table}
               SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP
             WHERE document_id = %s
               AND deleted = FALSE
            RETURNING chunk_id
            """
        ).format(
            schema=Identifier(self._settings.postgres_schema),
            chunk_table=Identifier(self._settings.postgres_chunk_table),
        )
        embedding_query = SQL(
            """
            UPDATE {schema}.{embedding_table}
               SET deleted = TRUE
             WHERE chunk_id = ANY(%s)
               AND deleted = FALSE
            """
        ).format(
            schema=Identifier(self._settings.postgres_schema),
            embedding_table=Identifier(self._settings.postgres_embedding_table),
        )
        async with await AsyncConnection.connect(dsn, row_factory=dict_row) as conn:
            async with conn.transaction():
                rows = await (await conn.execute(chunk_query, (document_id,))).fetchall()
                chunk_ids = [str(row["chunk_id"]) for row in rows]
                if chunk_ids:
                    await conn.execute(embedding_query, (chunk_ids,))

    async def similarity_search(
        self,
        request: RetrieveRequest,
        query_embedding: list[float],
    ) -> list[RetrieveHit]:
        dsn = self._settings.postgres_dsn
        if not self.is_configured() or dsn is None:
            return []

        top_k = self._resolve_top_k(request.topK)
        where_sql, params = self._build_filters(request)
        vector_literal = "[" + ",".join(str(value) for value in query_embedding) + "]"

        query = SQL(
            """
            SELECT
                c.chunk_id,
                c.parent_chunk_id,
                c.document_id,
                c.content,
                c.metadata,
                c.quality_score,
                1 - (e.embedding <=> %s::vector) AS vector_score,
                (1 - (e.embedding <=> %s::vector)) * c.quality_score AS final_score
            FROM {schema}.{chunk_table} c
            JOIN {schema}.{embedding_table} e
              ON e.chunk_id = c.chunk_id
             AND e.deleted = FALSE
             AND e.model = %s
            WHERE c.deleted = FALSE
              {where_sql}
            ORDER BY final_score DESC
            LIMIT %s
            """
        ).format(
            schema=Identifier(self._settings.postgres_schema),
            chunk_table=Identifier(self._settings.postgres_chunk_table),
            embedding_table=Identifier(self._settings.postgres_embedding_table),
            where_sql=where_sql,
        )
        sql_params: list[Any] = [
            vector_literal,
            vector_literal,
            self._settings.embedding_model,
            *params,
            top_k,
        ]

        async with await AsyncConnection.connect(
            dsn,
            row_factory=dict_row,
        ) as conn:
            rows = await (await conn.execute(query, sql_params)).fetchall()

        return [
            RetrieveHit(
                chunkId=str(row["chunk_id"]),
                parentChunkId=str(row["parent_chunk_id"]) if row["parent_chunk_id"] else None,
                documentId=str(row["document_id"]),
                content=str(row["content"]),
                score=float(row["final_score"]),
                metadata={
                    **dict(row["metadata"] or {}),
                    "vector_score": float(row["vector_score"]),
                    "quality_score": float(row["quality_score"]),
                    "final_score": float(row["final_score"]),
                },
            )
            for row in rows
        ]

    async def upsert_ingestion_task(
        self,
        task_id: str,
        document_id: str,
        operation: str,
        status: str,
        trace_id: str | None,
        message: str | None,
        metrics: dict[str, Any] | None = None,
    ) -> None:
        dsn = self._settings.postgres_dsn
        if not self.is_configured() or dsn is None:
            return
        query = SQL(
            """
            INSERT INTO {schema}.engine_ingestion_task (
                task_id, document_id, operation, status, trace_id, message, metrics, updated_at
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, CURRENT_TIMESTAMP)
            ON CONFLICT (task_id)
            DO UPDATE SET
                document_id = EXCLUDED.document_id,
                operation = EXCLUDED.operation,
                status = EXCLUDED.status,
                trace_id = EXCLUDED.trace_id,
                message = EXCLUDED.message,
                metrics = EXCLUDED.metrics,
                updated_at = CURRENT_TIMESTAMP
            """
        ).format(schema=Identifier(self._settings.postgres_schema))
        async with await AsyncConnection.connect(dsn) as conn:
            await conn.execute(
                query,
                (
                    task_id,
                    document_id,
                    operation,
                    status,
                    trace_id,
                    message,
                    Jsonb(metrics or {}),
                ),
            )
            await conn.commit()

    async def write_retrieval_log(
        self,
        request: RetrieveRequest,
        hits: list[RetrieveHit],
        skipped: bool,
        reason: str | None,
        duration_ms: int,
    ) -> None:
        dsn = self._settings.postgres_dsn
        if not self.is_configured() or dsn is None:
            return
        query = SQL(
            """
            INSERT INTO {schema}.engine_retrieval_log (
                trace_id, tenant_id, user_id, query_hash, knowledge_selection, filters,
                hit_chunk_ids, hit_parent_ids, case_ids, scores, skipped, reason, duration_ms
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """
        ).format(schema=Identifier(self._settings.postgres_schema))
        async with await AsyncConnection.connect(dsn) as conn:
            await conn.execute(
                query,
                (
                    request.traceId,
                    request.tenantId,
                    request.userId,
                    _hash_query(request.query),
                    Jsonb(request.knowledgeSelection.model_dump(mode="json") if request.knowledgeSelection else {}),
                    Jsonb(request.filters),
                    Jsonb([hit.chunkId for hit in hits]),
                    Jsonb([hit.parentChunkId for hit in hits if hit.parentChunkId]),
                    Jsonb(list({hit.metadata.get("case_id") for hit in hits if hit.metadata.get("case_id")})),
                    Jsonb(
                        [
                            {
                                "chunkId": hit.chunkId,
                                "score": hit.score,
                                "vectorScore": hit.metadata.get("vector_score"),
                                "qualityScore": hit.metadata.get("quality_score"),
                                "finalScore": hit.metadata.get("final_score"),
                            }
                            for hit in hits
                        ]
                    ),
                    skipped,
                    reason,
                    duration_ms,
                ),
            )
            await conn.commit()

    def _resolve_top_k(self, top_k: int) -> int:
        requested = top_k or self._settings.retrieval_top_k_default
        return max(1, min(requested, self._settings.retrieval_top_k_max))

    def _build_filters(self, request: RetrieveRequest) -> tuple[Any, list[Any]]:
        clauses: list[SQL] = [SQL("AND c.tenant_id = %s")]
        params: list[Any] = [request.tenantId]

        status = request.filters.get("status", "READY")
        enabled = request.filters.get("enabled", True)
        if status is not None:
            clauses.append(SQL("AND c.status = %s"))
            params.append(status)
        if enabled is not None:
            clauses.append(SQL("AND c.enabled = %s"))
            params.append(enabled)

        product_line = request.filters.get("productLine")
        if product_line:
            clauses.append(SQL("AND c.metadata->>'product_line' = %s"))
            params.append(product_line)

        allowed_scopes = set(request.allowedScopes)
        if "PUBLIC" in allowed_scopes and "PERSONAL" in allowed_scopes and request.userId:
            clauses.append(
                SQL(
                    """
                    AND (
                        (c.scope = 'PUBLIC' AND c.owner_user_id IS NULL)
                        OR (c.scope = 'PERSONAL' AND c.owner_user_id = %s)
                    )
                    """
                )
            )
            params.append(request.userId)
        elif "PUBLIC" in allowed_scopes:
            clauses.append(SQL("AND c.scope = 'PUBLIC' AND c.owner_user_id IS NULL"))
        elif "PERSONAL" in allowed_scopes and request.userId:
            clauses.append(SQL("AND c.scope = 'PERSONAL' AND c.owner_user_id = %s"))
            params.append(request.userId)
        else:
            clauses.append(SQL("AND FALSE"))

        selection = request.knowledgeSelection
        if selection:
            if selection.documentIds:
                clauses.append(SQL("AND c.document_id = ANY(%s)"))
                params.append(selection.documentIds)
            if selection.categoryIds:
                clauses.append(SQL("AND c.metadata->>'category_id' = ANY(%s)"))
                params.append(selection.categoryIds)
            if selection.tagIds:
                clauses.append(SQL("AND c.metadata->'tag_ids' ?| %s"))
                params.append(selection.tagIds)

        return SQL(" ").join(clauses), params


class PostgresStore(PostgresVectorStore):
    """Backward-compatible alias for earlier placeholder imports."""


def _hash_query(query: str) -> str:
    return "sha256:" + hashlib.sha256(query.encode("utf-8")).hexdigest()


def _chunk_params(chunk: Chunk) -> dict[str, Any]:
    return {
        "chunk_id": chunk.chunk_id,
        "parent_chunk_id": chunk.parent_chunk_id,
        "document_id": chunk.document_id,
        "tenant_id": chunk.tenant_id,
        "scope": chunk.scope,
        "owner_user_id": chunk.owner_user_id,
        "chunk_type": chunk.chunk_type,
        "source_type": chunk.source_type,
        "case_id": chunk.case_id,
        "case_field": chunk.case_field,
        "section_path": Jsonb(chunk.section_path),
        "content": chunk.content,
        "content_hash": chunk.content_hash,
        "metadata": Jsonb(chunk.metadata),
        "token_count": chunk.token_count,
        "quality_score": chunk.quality_score,
        "status": chunk.status,
        "enabled": chunk.enabled,
    }


def _vector_literal(embedding: list[float]) -> str:
    return "[" + ",".join(str(value) for value in embedding) + "]"
