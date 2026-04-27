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
                chunk_id, parent_chunk_id, document_id, kb_id, kb_version, kb_type, tenant_id, scope, owner_user_id,
                chunk_type, source_type, case_id, case_field, section_path, content,
                content_hash, metadata, token_count, quality_score, status, enabled,
                deleted, updated_at
            )
            VALUES (
                %(chunk_id)s, %(parent_chunk_id)s, %(document_id)s, %(kb_id)s,
                %(kb_version)s, %(kb_type)s, %(tenant_id)s, %(scope)s,
                %(owner_user_id)s, %(chunk_type)s, %(source_type)s,
                %(case_id)s, %(case_field)s, %(section_path)s, %(content)s,
                %(content_hash)s, %(metadata)s, %(token_count)s, %(quality_score)s,
                %(status)s, %(enabled)s, FALSE, CURRENT_TIMESTAMP
            )
            ON CONFLICT (chunk_id) WHERE deleted = FALSE
            DO UPDATE SET
                parent_chunk_id = EXCLUDED.parent_chunk_id,
                kb_id = EXCLUDED.kb_id,
                kb_version = EXCLUDED.kb_version,
                kb_type = EXCLUDED.kb_type,
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
                c.kb_id,
                c.kb_version,
                c.kb_type,
                c.section_path,
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
            _row_to_hit(
                row,
                score=float(row["final_score"]),
                extra_metadata={
                    "vector_score": float(row["vector_score"]),
                    "quality_score": float(row["quality_score"]),
                    "final_score": float(row["final_score"]),
                },
            )
            for row in rows
        ]

    async def expand_parent_hits(self, hits: list[RetrieveHit]) -> list[RetrieveHit]:
        if not hits:
            return []
        dsn = self._settings.postgres_dsn
        if not self.is_configured() or dsn is None:
            return hits

        grouped: dict[str, list[RetrieveHit]] = {}
        for hit in hits:
            group_id = hit.parentChunkId or hit.chunkId
            grouped.setdefault(group_id, []).append(hit)

        parent_ids = [
            group_id
            for group_id, group_hits in grouped.items()
            if any(hit.parentChunkId for hit in group_hits)
        ]
        if not parent_ids:
            return [_with_matched_child_metadata(hit, [hit]) for hit in hits]

        query = SQL(
            """
            SELECT
                chunk_id,
                parent_chunk_id,
                document_id,
                kb_id,
                kb_version,
                kb_type,
                section_path,
                content,
                metadata,
                quality_score
            FROM {schema}.{chunk_table}
            WHERE deleted = FALSE
              AND status = 'READY'
              AND enabled = TRUE
              AND chunk_id = ANY(%s)
            """
        ).format(
            schema=Identifier(self._settings.postgres_schema),
            chunk_table=Identifier(self._settings.postgres_chunk_table),
        )
        async with await AsyncConnection.connect(dsn, row_factory=dict_row) as conn:
            rows = await (await conn.execute(query, (parent_ids,))).fetchall()

        parents = {str(row["chunk_id"]): row for row in rows}
        expanded: list[RetrieveHit] = []
        for group_id, group_hits in grouped.items():
            child_hits = [hit for hit in group_hits if hit.parentChunkId]
            direct_parent = next((hit for hit in group_hits if not hit.parentChunkId), None)
            if not child_hits:
                expanded.append(_with_matched_child_metadata(group_hits[0], [group_hits[0]]))
                continue
            parent_row = parents.get(group_id)
            if parent_row is None and direct_parent is not None:
                expanded.append(_with_matched_child_metadata(direct_parent, child_hits))
                continue
            if parent_row is None:
                continue
            best_hit = max(child_hits, key=lambda candidate: candidate.score)
            expanded.append(
                _row_to_hit(
                    parent_row,
                    score=best_hit.score,
                    extra_metadata=_parent_match_metadata(parent_row, child_hits, best_hit),
                )
            )
        return expanded

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
                    Jsonb(_hit_chunk_ids(hits)),
                    Jsonb(_hit_parent_ids(hits)),
                    Jsonb(list({hit.metadata.get("case_id") for hit in hits if hit.metadata.get("case_id")})),
                    Jsonb(
                        [
                            {
                                "chunkId": hit.chunkId,
                                "matchedChildIds": _matched_child_ids(hit),
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
        clauses: list[Any] = [SQL("AND c.tenant_id = %s")]
        params: list[Any] = [request.tenantId]

        status = request.filters.get("status", "READY")
        enabled = request.filters.get("docEnabled", request.filters.get("enabled", True))
        if status is not None:
            clauses.append(SQL("AND c.status = %s"))
            params.append(status)
        if enabled is not None:
            clauses.append(SQL("AND c.enabled = %s"))
            params.append(enabled)

        allowed_kb_ids = request.allowedKbIds or _string_list(request.filters.get("allowedKbIds"))
        if allowed_kb_ids:
            clauses.append(SQL("AND c.kb_id = ANY(%s)"))
            params.append(allowed_kb_ids)

        kb_version_map = request.filters.get("kbVersionMap")
        if isinstance(kb_version_map, dict) and kb_version_map:
            version_clauses: list[SQL] = []
            for kb_id, version in kb_version_map.items():
                version_clauses.append(SQL("(c.kb_id = %s AND c.kb_version = %s)"))
                params.extend([str(kb_id), int(version)])
            clauses.append(SQL("AND (") + SQL(" OR ").join(version_clauses) + SQL(")"))

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
        "kb_id": chunk.kb_id,
        "kb_version": chunk.kb_version,
        "kb_type": chunk.kb_type,
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


def _row_to_hit(
    row: dict[str, Any],
    score: float,
    extra_metadata: dict[str, Any] | None = None,
) -> RetrieveHit:
    metadata = dict(row["metadata"] or {})
    if extra_metadata:
        metadata.update(extra_metadata)
    return RetrieveHit(
        chunkId=str(row["chunk_id"]),
        parentChunkId=str(row["parent_chunk_id"]) if row["parent_chunk_id"] else None,
        kbId=str(row["kb_id"]) if row.get("kb_id") else metadata.get("kb_id"),
        kbName=str(metadata.get("kb_name") or ""),
        kbType=str(row["kb_type"]) if row.get("kb_type") else metadata.get("kb_type"),
        kbVersion=int(row["kb_version"]) if row.get("kb_version") is not None else metadata.get("kb_version"),
        documentId=str(row["document_id"]),
        documentTitle=str(metadata.get("document_title") or metadata.get("title") or row["document_id"]),
        sectionPath=_json_list(row.get("section_path") or metadata.get("section_path")),
        content=str(row["content"]),
        score=score,
        position=_position_from_metadata(metadata),
        metadata=metadata,
    )


def _string_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item) for item in value if item is not None]


def _json_list(value: Any) -> list[str]:
    if isinstance(value, list):
        return [str(item) for item in value if item is not None]
    return []


def _position_from_metadata(metadata: dict[str, Any]) -> dict[str, Any]:
    position = metadata.get("position")
    if isinstance(position, dict):
        return position
    anchor = {
        key: metadata.get(key)
        for key in ("page", "charStart", "charEnd")
        if metadata.get(key) is not None
    }
    return anchor


def _parent_match_metadata(
    parent_row: dict[str, Any],
    children: list[RetrieveHit],
    best_hit: RetrieveHit,
) -> dict[str, Any]:
    return {
        "matched_child_ids": [child.chunkId for child in children],
        "matched_child_chunk_types": _unique_metadata_values(children, "chunk_type"),
        "matched_child_fields": _unique_metadata_values(children, "case_field_title", "case_field"),
        "matched_child_scores": [
            {
                "chunkId": child.chunkId,
                "score": child.score,
                "vectorScore": child.metadata.get("vector_score"),
                "qualityScore": child.metadata.get("quality_score"),
                "finalScore": child.metadata.get("final_score"),
            }
            for child in children
        ],
        "vector_score": best_hit.metadata.get("vector_score"),
        "quality_score": float(parent_row["quality_score"]),
        "final_score": best_hit.score,
        "retrieval_expanded_from_child": True,
    }


def _with_matched_child_metadata(hit: RetrieveHit, children: list[RetrieveHit]) -> RetrieveHit:
    metadata = {
        **hit.metadata,
        "matched_child_ids": [child.chunkId for child in children],
    }
    return hit.model_copy(update={"metadata": metadata})


def _unique_metadata_values(hits: list[RetrieveHit], *keys: str) -> list[str]:
    values: list[str] = []
    for hit in hits:
        for key in keys:
            value = hit.metadata.get(key)
            if value and str(value) not in values:
                values.append(str(value))
                break
    return values


def _matched_child_ids(hit: RetrieveHit) -> list[str]:
    value = hit.metadata.get("matched_child_ids")
    if isinstance(value, list):
        return [str(item) for item in value if item is not None]
    return [hit.chunkId]


def _hit_chunk_ids(hits: list[RetrieveHit]) -> list[str]:
    ids: list[str] = []
    for hit in hits:
        for chunk_id in _matched_child_ids(hit):
            if chunk_id not in ids:
                ids.append(chunk_id)
    return ids


def _hit_parent_ids(hits: list[RetrieveHit]) -> list[str]:
    ids: list[str] = []
    for hit in hits:
        parent_id = hit.parentChunkId or hit.chunkId
        if parent_id not in ids:
            ids.append(parent_id)
    return ids
