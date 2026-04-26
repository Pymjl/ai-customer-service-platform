from typing import Any, Literal

from pydantic import BaseModel, Field


KnowledgeSelectionMode = Literal["DEFAULT", "SELECTED", "NONE"]


class KnowledgeSelection(BaseModel):
    mode: KnowledgeSelectionMode = "DEFAULT"
    includePublic: bool | None = None
    includePersonal: bool | None = None
    documentIds: list[str] = Field(default_factory=list)
    categoryIds: list[str] = Field(default_factory=list)
    tagIds: list[str] = Field(default_factory=list)


class EngineRequest(BaseModel):
    messageId: str | None = None
    sessionId: str
    message: str
    traceId: str | None = None
    userId: str | None = None
    tenantId: str | None = None
    roles: list[str] = Field(default_factory=list)
    locale: str = "zh-CN"
    knowledgeSelection: KnowledgeSelection | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)
