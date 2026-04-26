from __future__ import annotations

import json
from typing import Any

from aicsp_engine.core.prompts import (
    CUSTOMER_SERVICE_GLOBAL_PROMPT,
    DEGRADED_SECTION_TEMPLATE,
    KNOWLEDGE_SNIPPET_TEMPLATE,
    NO_CONTEXT_MESSAGE,
    RAG_CONTEXT_SECTION_TEMPLATE,
    TOOL_RESULT_SECTION_TEMPLATE,
    USER_PROMPT_TEMPLATE,
)
from aicsp_engine.models.rag import RetrieveHit


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
            KNOWLEDGE_SNIPPET_TEMPLATE.format(
                index=index,
                source=source,
                score=hit.score,
                content=hit.content,
            )
        )
        citations.append(
            {
                "documentId": hit.documentId,
                "chunkId": hit.chunkId,
                "parentChunkId": hit.parentChunkId,
                "matchedChildIds": _string_list(metadata.get("matched_child_ids")),
                "matchedFields": _string_list(metadata.get("matched_child_fields")),
                "title": title,
                "caseId": case_id,
                "field": field,
                "score": hit.score,
            }
        )
    return "\n\n".join(context_parts), citations


def _string_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item) for item in value if item is not None]


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
        sections.append(RAG_CONTEXT_SECTION_TEMPLATE.format(rag_context=rag_context))
    if tool_result:
        sections.append(
            TOOL_RESULT_SECTION_TEMPLATE.format(
                tool_result=json.dumps(
                    {"tool": tool_name, "result": tool_result},
                    ensure_ascii=False,
                    separators=(",", ":"),
                )
            )
        )
    if degraded:
        sections.append(
            DEGRADED_SECTION_TEMPLATE.format(fallback_reason=fallback_reason or "unknown")
        )
    context = "\n\n".join(sections) if sections else NO_CONTEXT_MESSAGE
    return [
        {"role": "system", "content": CUSTOMER_SERVICE_GLOBAL_PROMPT},
        {"role": "user", "content": USER_PROMPT_TEMPLATE.format(context=context, user_message=user_message)},
    ]
