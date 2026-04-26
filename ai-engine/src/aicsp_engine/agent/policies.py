import re

from aicsp_engine.agent.state import AgentState
from aicsp_engine.core.prompts import GREETING_RESPONSE

GREETING_RE = re.compile(r"^\s*(你好|您好|hi|hello|在吗)[!！。.\s]*$", re.IGNORECASE)
ORDER_RE = re.compile(r"(订单|支付|发货|退款|售后).*?([A-Za-z0-9_-]{6,})")
LOGISTICS_RE = re.compile(r"(物流|快递|运单|配送).*?([A-Za-z0-9_-]{6,})")


def classify_intent(state: AgentState) -> str:
    message = state.normalized_query or state.user_message
    if GREETING_RE.match(message):
        state.direct_answer = GREETING_RESPONSE
        return "direct"
    if LOGISTICS_RE.search(message):
        state.tool_name = "logistics.query"
        return "tool"
    if ORDER_RE.search(message):
        state.tool_name = "order.query"
        return "tool"
    if state.request.knowledgeSelection and state.request.knowledgeSelection.mode == "NONE":
        return "generate"
    return "retrieve"


def extract_business_identifier(text: str) -> str | None:
    for pattern in (LOGISTICS_RE, ORDER_RE):
        match = pattern.search(text)
        if match:
            return match.group(2)
    return None
