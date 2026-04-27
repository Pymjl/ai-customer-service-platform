from typing import Any, Literal

from pydantic import BaseModel, Field, model_validator

from aicsp_engine.models.chat import KnowledgeSelection
from aicsp_engine.rag.chunking import ChunkDocument


Scope = Literal["PUBLIC", "PERSONAL"]
TaskStatus = Literal["SUBMITTED", "PARSING", "CHUNKING", "EMBEDDING", "INDEXING", "READY", "FAILED"]


class IngestRequest(BaseModel):
    taskId: str
    documentId: str
    kbId: str = "kb_default"
    kbVersion: int = 1
    kbType: str = "GENERIC_PUBLIC"
    kbName: str | None = None
    scope: Scope
    tenantId: str
    ownerUserId: str | None = None
    objectPath: str | None = None
    rawText: str | None = None
    title: str
    documentTitle: str | None = None
    sourceType: str = "UNKNOWN"
    categoryId: str | None = None
    productLine: str | None = None
    tags: list[str] = Field(default_factory=list)
    tagIds: list[str] = Field(default_factory=list)
    enabled: bool = True
    traceId: str | None = None

    @model_validator(mode="after")
    def validate_owner(self) -> "IngestRequest":
        if self.scope == "PUBLIC" and self.ownerUserId is not None:
            raise ValueError("PUBLIC document ownerUserId must be null")
        if self.scope == "PERSONAL" and not self.ownerUserId:
            raise ValueError("PERSONAL document ownerUserId is required")
        return self


class DeleteRequest(BaseModel):
    taskId: str
    documentId: str
    tenantId: str
    kbId: str | None = None
    kbVersion: int | None = None
    traceId: str | None = None


class RagTaskResponse(BaseModel):
    taskId: str
    documentId: str
    status: str
    accepted: bool
    traceId: str | None = None


class TaskStatusResponse(RagTaskResponse):
    operation: str
    message: str | None = None
    progress: int = 0
    metrics: dict[str, Any] = Field(default_factory=dict)


class RetrieveRequest(BaseModel):
    query: str
    tenantId: str
    userId: str | None = None
    allowedKbIds: list[str] = Field(default_factory=list)
    allowedScopes: list[str] = Field(default_factory=list)
    knowledgeSelection: KnowledgeSelection | None = None
    filters: dict[str, Any] = Field(default_factory=dict)
    topK: int = 8
    traceId: str | None = None


class RetrieveHit(BaseModel):
    chunkId: str
    parentChunkId: str | None = None
    kbId: str | None = None
    kbName: str | None = None
    kbType: str | None = None
    kbVersion: int | None = None
    documentId: str
    documentTitle: str | None = None
    sectionPath: list[str] = Field(default_factory=list)
    content: str
    score: float
    position: dict[str, Any] = Field(default_factory=dict)
    metadata: dict[str, Any] = Field(default_factory=dict)


class RetrieveResponse(BaseModel):
    query: str
    hits: list[RetrieveHit]
    skipped: bool = False
    reason: str | None = None
    traceId: str | None = None


class ChunkDocumentRequest(BaseModel):
    documentId: str
    kbId: str = "kb_preview"
    kbVersion: int = 1
    kbType: str = "GENERIC_PUBLIC"
    kbName: str | None = None
    title: str
    text: str
    tenantId: str = "default"
    scope: Scope = "PUBLIC"
    ownerUserId: str | None = None
    sourceType: str = "UNKNOWN"
    categoryId: str | None = None
    tagIds: list[str] = Field(default_factory=list)
    productLine: str | None = None
    status: str = "READY"
    enabled: bool = True

    @model_validator(mode="after")
    def validate_owner(self) -> "ChunkDocumentRequest":
        if self.scope == "PUBLIC" and self.ownerUserId is not None:
            raise ValueError("PUBLIC document ownerUserId must be null")
        if self.scope == "PERSONAL" and not self.ownerUserId:
            raise ValueError("PERSONAL document ownerUserId is required")
        return self

    def to_chunk_document(self) -> ChunkDocument:
        return ChunkDocument(
            document_id=self.documentId,
            kb_id=self.kbId,
            kb_version=self.kbVersion,
            kb_type=self.kbType,
            kb_name=self.kbName,
            title=self.title,
            text=self.text,
            tenant_id=self.tenantId,
            scope=self.scope,
            owner_user_id=self.ownerUserId,
            source_type=self.sourceType,
            category_id=self.categoryId,
            tag_ids=self.tagIds,
            product_line=self.productLine,
            status=self.status,
            enabled=self.enabled,
        )
