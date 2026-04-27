from __future__ import annotations

from typing import Any

import httpx

from aicsp_engine.core.config import Settings
from aicsp_engine.models.chat import EngineRequest, KnowledgeSelection


class BizServiceClient:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def resolve_retrieval_filter(
        self,
        request: EngineRequest,
        product_line: str | None = None,
    ) -> dict[str, Any]:
        payload = {
            "tenantId": request.tenantId,
            "userId": request.userId,
            "roles": request.roles,
            "knowledgeSelection": _selection_payload(request.knowledgeSelection),
            "productLine": product_line,
            "traceId": request.traceId,
        }
        result = await self._post("/internal/knowledge/retrieval-filter", payload, request.traceId)
        return _unwrap_result(result)

    async def call_tool(
        self,
        tool_name: str,
        payload: dict[str, Any],
        trace_id: str | None,
    ) -> dict[str, Any]:
        result = await self._post(f"/internal/tools/{tool_name}", payload, trace_id)
        return _unwrap_result(result)

    async def submit_message_completed(
        self,
        payload: dict[str, Any],
        trace_id: str | None,
    ) -> None:
        await self._post("/internal/chat/message-completed", payload, trace_id)

    async def submit_ingestion_callback(
        self,
        payload: dict[str, Any],
        trace_id: str | None,
    ) -> None:
        await self._post("/internal/knowledge/ingestion-callback", payload, trace_id)

    async def _post(
        self,
        path: str,
        payload: dict[str, Any],
        trace_id: str | None,
    ) -> dict[str, Any]:
        headers = {"X-Internal-Token": self._settings.biz_internal_token}
        if trace_id:
            headers["X-Trace-Id"] = trace_id
        async with httpx.AsyncClient(
            base_url=self._settings.biz_base_url,
            timeout=10.0,
        ) as client:
            response = await client.post(path, json=payload, headers=headers)
            response.raise_for_status()
            data = response.json()
            return data if isinstance(data, dict) else {"data": data}


def _selection_payload(selection: KnowledgeSelection | None) -> dict[str, Any]:
    if selection is None:
        return {
            "mode": "DEFAULT",
            "includePublic": True,
            "includePersonal": True,
            "personalKbIds": [],
            "kbIds": [],
            "documentIds": [],
            "categoryIds": [],
            "tagIds": [],
        }
    return selection.model_dump(mode="json")


def _unwrap_result(response: dict[str, Any]) -> dict[str, Any]:
    data = response.get("data")
    if isinstance(data, dict):
        return data
    if response.get("code") in (0, 200) and isinstance(data, dict):
        return data
    return response
