from __future__ import annotations

import json
from typing import Any

from aicsp_engine.models.rag import RetrieveHit


SYSTEM_PROMPT = """你是智能客服助手。请遵守以下要求：
1. 优先基于已提供的知识库片段和工具结果回答。
2. 不要编造订单、物流、客户资料或政策条款。
3. 信息不足时，明确说明需要用户补充哪些信息。
4. 涉及退款、支付、账号、安全和投诉升级时保持谨慎，并建议转人工或按流程处理。
5. 回答要简洁、可执行，必要时列出步骤。"""


def format_rag_context(hits: list[RetrieveHit], max_items: int = 5) -> tuple[str, list[dict[str, Any]]]:
    if not hits:
        return "", []
    context_parts: list[str] = []
    citations: list[dict[str, Any]] = []
    for index, hit in enumerate(hits[:max_items], start=1):
        metadata = hit.metadata
        title = str(metadata.get("title") or hit.documentId)
        case_id = metadata.get("case_id")
        field = metadata.get("case_field_title") or metadata.get("case_field")
        source = f"{title}"
        if case_id:
            source += f" / 工单 {case_id}"
        if field:
            source += f" / {field}"
        context_parts.append(
            f"[知识片段 {index}]\n来源：{source}\n相关分数：{hit.score:.4f}\n内容：{hit.content}"
        )
        citations.append(
            {
                "documentId": hit.documentId,
                "chunkId": hit.chunkId,
                "parentChunkId": hit.parentChunkId,
                "title": title,
                "caseId": case_id,
                "field": field,
                "score": hit.score,
            }
        )
    return "\n\n".join(context_parts), citations


def build_prompt_messages(
    user_message: str,
    rag_context: str,
    tool_name: str | None,
    tool_result: dict[str, Any] | None,
    degraded: bool,
    fallback_reason: str | None,
) -> list[dict[str, Any]]:
    sections: list[str] = []
    if rag_context:
        sections.append("【检索级上下文】\n" + rag_context)
    if tool_result:
        sections.append(
            "【工具结果】\n"
            + json.dumps(
                {"tool": tool_name, "result": tool_result},
                ensure_ascii=False,
                separators=(",", ":"),
            )
        )
    if degraded:
        sections.append(f"【降级状态】{fallback_reason or 'unknown'}。不得扩大权限或编造未取得的信息。")
    context = "\n\n".join(sections) if sections else "本轮没有可用的知识库片段或实时工具结果。"
    return [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": f"{context}\n\n【用户问题】\n{user_message}"},
    ]
