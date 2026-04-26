import pytest

from aicsp_engine.agent.graph import CustomerServiceAgent
from aicsp_engine.core.config import Settings
from aicsp_engine.core.prompts import HUMAN_HANDOFF_MESSAGE
from aicsp_engine.models.chat import EngineRequest


@pytest.mark.asyncio
async def test_tool_failure_uses_human_handoff_message(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_call_tool(*args: object, **kwargs: object) -> dict[str, object]:
        return {"success": False, "errorCode": "TOOL_NOT_AVAILABLE", "message": "failed"}

    monkeypatch.setattr("aicsp_engine.tools.biz_client.BizServiceClient.call_tool", fake_call_tool)

    agent = CustomerServiceAgent(Settings(llm_enabled=False))
    events = [
        event
        async for event in agent.stream(
            EngineRequest(
                messageId="m_001",
                sessionId="s_001",
                message="请帮我查询订单 ABC123456 的状态",
                traceId="trace_001",
                userId="u_001",
                tenantId="default",
            )
        )
    ]

    assert events[-1].event == "message"
    assert events[-1].data == HUMAN_HANDOFF_MESSAGE
    assert "抱歉" in events[-1].data
    assert "人工客服" in events[-1].data
