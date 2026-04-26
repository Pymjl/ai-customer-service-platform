from dataclasses import dataclass, field
from typing import Any

from aicsp_engine.models.chat import EngineRequest
from aicsp_engine.models.rag import RetrieveHit


@dataclass(slots=True)
class AgentState:
    request: EngineRequest
    session_id: str
    user_message: str
    trace_id: str | None = None
    intent_route: str = "generate"
    normalized_query: str = ""
    retrieval_filter: dict[str, Any] = field(default_factory=dict)
    rag_hits: list[RetrieveHit] = field(default_factory=list)
    rag_context: str = ""
    citations: list[dict[str, Any]] = field(default_factory=list)
    tool_name: str | None = None
    tool_result: dict[str, Any] | None = None
    degraded: bool = False
    failed_stage: str | None = None
    fallback_reason: str | None = None
    direct_answer: str | None = None
    messages: list[dict[str, Any]] = field(default_factory=list)
    answer_text: str = ""
    retrieved_chunk_ids: list[str] = field(default_factory=list)

    @classmethod
    def from_request(cls, request: EngineRequest) -> "AgentState":
        return cls(
            request=request,
            session_id=request.sessionId,
            user_message=request.message,
            trace_id=request.traceId,
            normalized_query=request.message.strip(),
        )
