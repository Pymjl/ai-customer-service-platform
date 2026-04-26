from fastapi import APIRouter, BackgroundTasks, Header, HTTPException

from aicsp_engine.core.config import get_settings
from aicsp_engine.models.rag import (
    ChunkDocumentRequest,
    DeleteRequest,
    IngestRequest,
    RagTaskResponse,
    RetrieveRequest,
    RetrieveResponse,
    TaskStatusResponse,
)
from aicsp_engine.rag.chunking import chunk_document
from aicsp_engine.rag.ingestion import IngestionPipeline
from aicsp_engine.rag.retriever import Retriever
from aicsp_engine.rag.tasks import get_task_store

router = APIRouter(prefix="/internal/rag", tags=["rag"])


def _check_biz_token(token: str | None) -> None:
    settings = get_settings()
    if settings.biz_internal_token and token != settings.biz_internal_token:
        raise HTTPException(status_code=401, detail="invalid internal token")


@router.post("/ingest", response_model=RagTaskResponse)
async def submit_ingest(
    request: IngestRequest,
    background_tasks: BackgroundTasks,
    x_internal_token: str | None = Header(default=None, alias="X-Internal-Token"),
) -> RagTaskResponse:
    _check_biz_token(x_internal_token)
    existing = get_task_store().get(request.taskId)
    if existing is not None:
        return RagTaskResponse(
            taskId=existing.taskId,
            documentId=existing.documentId,
            status=existing.status,
            accepted=existing.accepted,
            traceId=existing.traceId,
        )
    response = get_task_store().submit(request.taskId, request.documentId, request.traceId, "INGEST")
    pipeline = IngestionPipeline(get_settings(), get_task_store())
    if get_settings().ingestion_background_enabled:
        background_tasks.add_task(pipeline.ingest, request, "INGEST")
    else:
        await pipeline.ingest(request, "INGEST")
    return response


@router.post("/reindex", response_model=RagTaskResponse)
async def submit_reindex(
    request: IngestRequest,
    background_tasks: BackgroundTasks,
    x_internal_token: str | None = Header(default=None, alias="X-Internal-Token"),
) -> RagTaskResponse:
    _check_biz_token(x_internal_token)
    existing = get_task_store().get(request.taskId)
    if existing is not None:
        return RagTaskResponse(
            taskId=existing.taskId,
            documentId=existing.documentId,
            status=existing.status,
            accepted=existing.accepted,
            traceId=existing.traceId,
        )
    response = get_task_store().submit(request.taskId, request.documentId, request.traceId, "REINDEX")
    pipeline = IngestionPipeline(get_settings(), get_task_store())
    if get_settings().ingestion_background_enabled:
        background_tasks.add_task(pipeline.ingest, request, "REINDEX")
    else:
        await pipeline.ingest(request, "REINDEX")
    return response


@router.post("/delete", response_model=RagTaskResponse)
async def submit_delete(
    request: DeleteRequest,
    background_tasks: BackgroundTasks,
    x_internal_token: str | None = Header(default=None, alias="X-Internal-Token"),
) -> RagTaskResponse:
    _check_biz_token(x_internal_token)
    existing = get_task_store().get(request.taskId)
    if existing is not None:
        return RagTaskResponse(
            taskId=existing.taskId,
            documentId=existing.documentId,
            status=existing.status,
            accepted=existing.accepted,
            traceId=existing.traceId,
        )
    response = get_task_store().submit(request.taskId, request.documentId, request.traceId, "DELETE")
    pipeline = IngestionPipeline(get_settings(), get_task_store())
    if get_settings().ingestion_background_enabled:
        background_tasks.add_task(pipeline.delete, request.taskId, request.documentId, request.traceId)
    else:
        await pipeline.delete(request.taskId, request.documentId, request.traceId)
    return response


@router.get("/tasks/{task_id}", response_model=TaskStatusResponse)
async def get_task(
    task_id: str,
    x_internal_token: str | None = Header(default=None, alias="X-Internal-Token"),
) -> TaskStatusResponse:
    _check_biz_token(x_internal_token)
    task = get_task_store().get(task_id)
    if task is None:
        raise HTTPException(status_code=404, detail="task not found")
    return task


@router.post("/retrieve", response_model=RetrieveResponse)
async def retrieve(
    request: RetrieveRequest,
    x_internal_token: str | None = Header(default=None, alias="X-Internal-Token"),
) -> RetrieveResponse:
    _check_biz_token(x_internal_token)
    if request.knowledgeSelection and request.knowledgeSelection.mode == "NONE":
        return RetrieveResponse(
            query=request.query,
            hits=[],
            skipped=True,
            reason="knowledge_selection_none",
            traceId=request.traceId,
        )
    return await Retriever(get_settings()).retrieve(request)


@router.post("/chunk/preview")
async def preview_chunks(
    request: ChunkDocumentRequest,
    x_internal_token: str | None = Header(default=None, alias="X-Internal-Token"),
) -> dict[str, object]:
    _check_biz_token(x_internal_token)
    chunks = chunk_document(request.to_chunk_document())
    return {
        "documentId": request.documentId,
        "chunkCount": len(chunks),
        "chunks": [chunk.to_dict() for chunk in chunks],
    }
