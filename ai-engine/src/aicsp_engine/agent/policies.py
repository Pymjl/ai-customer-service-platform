import re

from aicsp_engine.agent.state import AgentState

GREETING_RE = re.compile(r"^\s*(你好|您好|hi|hello|在吗)[!！。.\s]*$", re.IGNORECASE)
ORDER_RE = re.compile(r"(订单|支付|发货|退款|售后).*?([A-Za-z0-9_-]{6,})")
LOGISTICS_RE = re.compile(r"(物流|快递|运单|配送).*?([A-Za-z0-9_-]{6,})")

HUMAN_HANDOFF_MESSAGE = (
    "非常抱歉，这个问题我暂时没能顺利查询到可靠结果。"
    "为了不耽误您处理，我建议为您联系人工客服继续核实。"
    "请您稍等一下，或补充订单号、手机号后四位、物流单号等信息，人工同事会更耐心地帮您确认。"
)


def classify_intent(state: AgentState) -> str:
    message = state.normalized_query or state.user_message
    if GREETING_RE.match(message):
        state.direct_answer = "您好，我是智能客服助手。请描述您遇到的问题，或提供订单号/物流单号以便查询。"
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
