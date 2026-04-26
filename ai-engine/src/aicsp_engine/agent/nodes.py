from __future__ import annotations

from uuid import uuid4

from aicsp_engine.agent.policies import classify_intent, extract_business_identifier
from aicsp_engine.agent.state import AgentState
from aicsp_engine.core.config import Settings
from aicsp_engine.core.prompts import HUMAN_HANDOFF_MESSAGE
from aicsp_engine.models.chat import KnowledgeSelection
from aicsp_engine.models.rag import RetrieveRequest
from aicsp_engine.rag.prompt_context import build_prompt_messages, format_rag_context
from aicsp_engine.rag.retriever import Retriever
from aicsp_engine.tools.biz_client import BizServiceClient


async def prepare_context(state: AgentState, _settings: Settings) -> AgentState:
    state.normalized_query = " ".join(state.user_message.split())
    return state


async def intent_funnel(state: AgentState, _settings: Settings) -> AgentState:
    state.intent_route = classify_intent(state)
    return state


async def resolve_retrieval_filter(state: AgentState, settings: Settings) -> AgentState:
    if state.request.knowledgeSelection and state.request.knowledgeSelection.mode == "NONE":
        state.degraded = True
        state.failed_stage = "retrieval_filter"
        state.fallback_reason = "knowledge_selection_none"
        return state
    try:
        state.retrieval_filter = await BizServiceClient(settings).resolve_retrieval_filter(state.request)
    except Exception as exc:
        state.degraded = True
        state.failed_stage = "retrieval_filter"
        state.fallback_reason = f"biz_filter_failed:{exc.__class__.__name__}"
        state.direct_answer = HUMAN_HANDOFF_MESSAGE
        state.retrieval_filter = {"skipRetrieval": True, "allowedScopes": [], "filters": {}}
    return state


async def retrieve(state: AgentState, settings: Settings) -> AgentState:
    if state.direct_answer:
        return state
    filter_result = state.retrieval_filter
    if filter_result.get("skipRetrieval"):
        state.degraded = True
        state.failed_stage = state.failed_stage or "retrieve"
        state.fallback_reason = state.fallback_reason or "skip_retrieval"
        return state

    raw_filters = filter_result.get("filters")
    filters: dict[str, object] = raw_filters if isinstance(raw_filters, dict) else {}
    selection = _selection_from_filters(filter_result.get("mode"), filters)
    request = RetrieveRequest(
        query=state.normalized_query,
        tenantId=str(filter_result.get("tenantId") or state.request.tenantId or "default"),
        userId=state.request.userId,
        allowedScopes=[str(scope) for scope in filter_result.get("allowedScopes", [])],
        knowledgeSelection=selection,
        filters=dict(filters),
        topK=settings.retrieval_top_k_default,
        traceId=state.trace_id,
    )
    try:
        response = await Retriever(settings).retrieve(request)
        state.rag_hits = response.hits
        state.retrieved_chunk_ids = [hit.chunkId for hit in response.hits]
        if not response.hits:
            state.degraded = True
            state.failed_stage = "retrieve"
            state.fallback_reason = response.reason or "no_hits"
    except Exception as exc:
        state.degraded = True
        state.failed_stage = "retrieve"
        state.fallback_reason = f"retrieve_failed:{exc.__class__.__name__}"
        state.direct_answer = HUMAN_HANDOFF_MESSAGE
    return state


async def assemble_rag_context(state: AgentState, _settings: Settings) -> AgentState:
    state.rag_context, state.citations = format_rag_context(state.rag_hits)
    return state


async def execute_tool(state: AgentState, settings: Settings) -> AgentState:
    if not state.tool_name:
        return state
    identifier = extract_business_identifier(state.normalized_query)
    payload = {
        "toolCallId": f"tool_{uuid4().hex}",
        "sessionId": state.session_id,
        "messageId": state.request.messageId,
        "userId": state.request.userId,
        "tenantId": state.request.tenantId,
        "arguments": {"query": state.normalized_query, "identifier": identifier},
        "idempotencyKey": f"{state.request.messageId or state.trace_id or uuid4().hex}:{state.tool_name}",
    }
    try:
        state.tool_result = await BizServiceClient(settings).call_tool(
            state.tool_name,
            payload,
            state.trace_id,
        )
        if state.tool_result.get("success") is False:
            state.degraded = True
            state.failed_stage = "tool_execute"
            state.fallback_reason = str(state.tool_result.get("errorCode") or "tool_returned_failure")
            state.direct_answer = HUMAN_HANDOFF_MESSAGE
    except Exception as exc:
        state.degraded = True
        state.failed_stage = "tool_execute"
        state.fallback_reason = f"tool_failed:{exc.__class__.__name__}"
        state.direct_answer = HUMAN_HANDOFF_MESSAGE
        state.tool_result = {
            "success": False,
            "message": HUMAN_HANDOFF_MESSAGE,
        }
    return state


async def build_generation_messages(state: AgentState, _settings: Settings) -> AgentState:
    if state.direct_answer:
        state.messages = []
        return state
    state.messages = build_prompt_messages(
        user_message=state.user_message,
        rag_context=state.rag_context,
        tool_name=state.tool_name,
        tool_result=state.tool_result,
        degraded=state.degraded,
        fallback_reason=state.fallback_reason,
    )
    return state


def _selection_from_filters(mode: object, filters: dict[str, object]) -> KnowledgeSelection:
    document_ids = _string_list(filters.get("documentIds"))
    category_ids = _string_list(filters.get("categoryIds"))
    tag_ids = _string_list(filters.get("tagIds"))
    return KnowledgeSelection(
        mode="SELECTED" if str(mode or "DEFAULT") != "NONE" else "NONE",
        includePublic=True,
        includePersonal=True,
        documentIds=document_ids,
        categoryIds=category_ids,
        tagIds=tag_ids,
    )


def _string_list(value: object) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item) for item in value if item is not None]
