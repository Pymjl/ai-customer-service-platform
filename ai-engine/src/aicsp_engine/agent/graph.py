from __future__ import annotations

from collections.abc import AsyncIterator
from time import time
from typing import Any, TypedDict

from langgraph.graph import END, StateGraph

from aicsp_engine.agent.nodes import (
    assemble_rag_context,
    build_generation_messages,
    execute_tool,
    intent_funnel,
    prepare_context,
    resolve_retrieval_filter,
    retrieve,
)
from aicsp_engine.agent.state import AgentState
from aicsp_engine.core.config import Settings
from aicsp_engine.llm.chat_model import ChatModelService
from aicsp_engine.models.chat import EngineRequest
from aicsp_engine.models.events import EngineEvent
from aicsp_engine.tools.biz_client import BizServiceClient


class AgentGraphState(TypedDict, total=False):
    route: str
    user_message: str
    degraded: bool
    failed_stage: str
    fallback_reason: str


def build_state_graph() -> Any:
    graph = StateGraph(AgentGraphState)

    def prepare_node(state: AgentGraphState) -> AgentGraphState:
        state["user_message"] = " ".join(str(state.get("user_message", "")).split())
        return state

    def intent_node(state: AgentGraphState) -> AgentGraphState:
        message = str(state.get("user_message", ""))
        state["route"] = "direct" if not message else "generate"
        return state

    graph.add_node("PrepareContext", prepare_node)
    graph.add_node("IntentFunnel", intent_node)
    graph.set_entry_point("PrepareContext")
    graph.add_edge("PrepareContext", "IntentFunnel")
    graph.add_edge("IntentFunnel", END)
    return graph.compile()


class CustomerServiceAgent:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._chat_model = ChatModelService(settings)
        self._compiled_graph = build_state_graph()

    async def run_until_generation(self, request: EngineRequest) -> AgentState:
        state = AgentState.from_request(request)
        state = await prepare_context(state, self._settings)
        state = await intent_funnel(state, self._settings)

        if state.intent_route == "direct":
            return await build_generation_messages(state, self._settings)

        if state.intent_route == "tool":
            state = await execute_tool(state, self._settings)
        elif state.intent_route == "retrieve":
            state = await resolve_retrieval_filter(state, self._settings)
            state = await retrieve(state, self._settings)
            state = await assemble_rag_context(state, self._settings)

        return await build_generation_messages(state, self._settings)

    async def stream(self, request: EngineRequest) -> AsyncIterator[EngineEvent]:
        state = await self.run_until_generation(request)
        started_at = time()

        for citation in state.citations:
            yield EngineEvent.citation(citation)

        if state.direct_answer:
            state.answer_text = state.direct_answer
            yield EngineEvent(event="message", data=state.direct_answer)
            await self._submit_completed_message(state, started_at, "COMPLETED")
            return

        async for delta in self._chat_model.stream_messages(state.messages):
            state.answer_text += delta
            yield EngineEvent(event="message", data=delta)
        await self._submit_completed_message(state, started_at, "COMPLETED")

    async def _submit_completed_message(
        self,
        state: AgentState,
        started_at: float,
        status: str,
    ) -> None:
        if not self._settings.message_completed_callback_enabled:
            return
        if not state.request.messageId:
            return
        payload = {
            "messageId": state.request.messageId,
            "sessionId": state.session_id,
            "userId": state.request.userId,
            "tenantId": state.request.tenantId,
            "userMessage": state.user_message,
            "aiReply": state.answer_text,
            "status": status,
            "traceId": state.trace_id,
            "timestamp": int(started_at * 1000),
        }
        try:
            await BizServiceClient(self._settings).submit_message_completed(payload, state.trace_id)
        except Exception:
            # 聊天消息已输出，持久化回调失败不再打扰用户。
            return
