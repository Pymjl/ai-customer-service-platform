from collections.abc import AsyncIterator

from fastapi import APIRouter, Header, HTTPException
from fastapi.responses import StreamingResponse

from aicsp_engine.agent.graph import CustomerServiceAgent
from aicsp_engine.agent.policies import HUMAN_HANDOFF_MESSAGE
from aicsp_engine.core.config import get_settings
from aicsp_engine.models.chat import EngineRequest
from aicsp_engine.models.events import EngineEvent, ndjson_line

router = APIRouter(tags=["chat"])


def _check_stream_token(token: str | None) -> None:
    settings = get_settings()
    if settings.stream_internal_token and token != settings.stream_internal_token:
        raise HTTPException(status_code=401, detail="invalid internal token")


async def _stream_events(request: EngineRequest) -> AsyncIterator[str]:
    agent = CustomerServiceAgent(get_settings())
    try:
        async for event in agent.stream(request):
            yield ndjson_line(event)
        yield ndjson_line(EngineEvent.done(trace_id=request.traceId))
    except Exception as exc:  # pragma: no cover - defensive stream boundary
        yield ndjson_line(EngineEvent(event="message", data=HUMAN_HANDOFF_MESSAGE))
        yield ndjson_line(EngineEvent.error(str(exc), trace_id=request.traceId))
        yield ndjson_line(EngineEvent.done(finish_reason="error", trace_id=request.traceId))


@router.post("/api/chat/stream")
async def chat_stream(
    request: EngineRequest,
    x_internal_token: str | None = Header(default=None, alias="X-Internal-Token"),
) -> StreamingResponse:
    _check_stream_token(x_internal_token)
    return StreamingResponse(
        _stream_events(request),
        media_type="application/x-ndjson; charset=utf-8",
    )
