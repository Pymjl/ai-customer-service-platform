from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any


CASE_ID_RE = re.compile(r"^\s*(?:#{1,6}\s*)?(T\d{16})\s*$", re.IGNORECASE)
HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
FIELD_HEADING_RE = re.compile(r"^\s*(?:#{1,6}\s*)?(.+?)\s*$")
FAQ_Q_RE = re.compile(r"^\s*(?:Q|Question|问|问题)\s*[:：]\s*(.+?)\s*$", re.IGNORECASE)
FAQ_A_RE = re.compile(r"^\s*(?:A|Answer|答|答案)\s*[:：]\s*(.+?)\s*$", re.IGNORECASE)
STEP_RE = re.compile(r"^\s*(?:\d+[.)、]|[-*]\s+)(.+)")

FIELD_ALIASES = {
    "QUESTION": {"问题描述", "问题", "question", "客户问题", "用户问题", "故障现象", "现象描述"},
    "REASON": {"问题根因", "根因", "原因", "reason", "root cause", "原因分析"},
    "SOLUTION": {"解决方案与措施", "解决方案", "处理措施", "解决措施", "solution", "action", "处理步骤"},
    "REMARK": {"备注", "补充说明", "remark", "note", "注意事项"},
}

FIELD_TITLES = {
    "QUESTION": "问题描述",
    "REASON": "问题根因",
    "SOLUTION": "解决方案与措施",
    "REMARK": "备注",
    "UNKNOWN": "未识别字段",
}

MEANINGLESS_REMARKS = {"无", "暂无", "n/a", "na", "none", "空", "-"}


@dataclass(slots=True)
class ChunkDocument:
    document_id: str
    title: str
    text: str
    kb_id: str = "kb_default"
    kb_version: int = 1
    kb_type: str = "GENERIC_PUBLIC"
    tenant_id: str = "default"
    scope: str = "PUBLIC"
    owner_user_id: str | None = None
    source_type: str = "UNKNOWN"
    category_id: str | None = None
    tag_ids: list[str] = field(default_factory=list)
    product_line: str | None = None
    status: str = "READY"
    enabled: bool = True
    language: str = "zh-CN"
    kb_name: str | None = None


@dataclass(slots=True)
class Chunk:
    chunk_id: str
    parent_chunk_id: str | None
    document_id: str
    kb_id: str
    kb_version: int
    kb_type: str
    content: str
    chunk_type: str
    source_type: str
    tenant_id: str
    scope: str
    owner_user_id: str | None
    section_path: list[str]
    token_count: int
    content_hash: str
    quality_score: float
    status: str
    enabled: bool
    case_id: str | None = None
    case_field: str | None = None
    metadata: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "chunk_id": self.chunk_id,
            "parent_chunk_id": self.parent_chunk_id,
            "document_id": self.document_id,
            "kb_id": self.kb_id,
            "kb_version": self.kb_version,
            "kb_type": self.kb_type,
            "chunk_type": self.chunk_type,
            "source_type": self.source_type,
            "tenant_id": self.tenant_id,
            "scope": self.scope,
            "owner_user_id": self.owner_user_id,
            "case_id": self.case_id,
            "case_field": self.case_field,
            "section_path": self.section_path,
            "content": self.content,
            "content_hash": self.content_hash,
            "token_count": self.token_count,
            "quality_score": self.quality_score,
            "status": self.status,
            "enabled": self.enabled,
            "metadata": self.metadata,
        }


@dataclass(slots=True)
class CaseBlock:
    case_id: str
    lines: list[str]
    duplicate: bool = False


def chunk_document(document: ChunkDocument) -> list[Chunk]:
    _validate_document_acl(document)
    text = _normalize_text(document.text)
    if _looks_like_case_library(text):
        return _chunk_case_library(document, text)
    if _has_faq_pairs(text):
        return _chunk_faq(document, text)
    if _has_markdown_headings(text):
        return _chunk_by_headings(document, text)
    return _chunk_semantic(document, text)


def detect_document_type(text: str) -> str:
    normalized = _normalize_text(text)
    if _looks_like_case_library(normalized):
        return "CASE_LIBRARY"
    if _has_faq_pairs(normalized):
        return "FAQ"
    if _has_markdown_headings(normalized):
        return "MANUAL"
    return "NOTE" if normalized.strip() else "UNKNOWN"


def _validate_document_acl(document: ChunkDocument) -> None:
    if document.scope == "PUBLIC" and document.owner_user_id is not None:
        raise ValueError("PUBLIC document owner_user_id must be None")
    if document.scope == "PERSONAL" and not document.owner_user_id:
        raise ValueError("PERSONAL document owner_user_id is required")


def _normalize_text(text: str) -> str:
    return text.replace("\r\n", "\n").replace("\r", "\n").strip()


def _looks_like_case_library(text: str) -> bool:
    lines = text.splitlines()
    has_case = any(CASE_ID_RE.match(line) for line in lines)
    has_field = any(_normalize_field_title(_strip_heading(line)) is not None for line in lines)
    return has_case and has_field


def _has_markdown_headings(text: str) -> bool:
    return any(HEADING_RE.match(line) and not CASE_ID_RE.match(line) for line in text.splitlines())


def _has_faq_pairs(text: str) -> bool:
    seen_question = False
    for line in text.splitlines():
        if FAQ_Q_RE.match(line):
            seen_question = True
        if seen_question and FAQ_A_RE.match(line):
            return True
    return False


def _chunk_case_library(document: ChunkDocument, text: str) -> list[Chunk]:
    cases = _split_cases(text)
    chunks: list[Chunk] = []
    for case_index, case in enumerate(cases):
        fields, inferred_question = _extract_case_fields(case)
        quality = _assess_case_quality(fields, duplicate=case.duplicate)
        quality_score = _case_quality_score(fields, duplicate=case.duplicate)
        parent = _build_case_parent(document, case, fields, quality, quality_score, case_index)
        chunks.append(parent)
        chunks.append(_build_case_summary(document, case, fields, parent, quality, quality_score))
        for order, (field_name, values) in enumerate(fields.items(), start=1):
            for value_index, value in enumerate(values, start=1):
                if _is_meaningless_field(field_name, value):
                    continue
                for split_index, part in enumerate(_split_case_field(field_name, value), start=1):
                    chunks.append(
                        _build_case_child(
                            document=document,
                            case=case,
                            field_name=field_name,
                            field_title=FIELD_TITLES.get(field_name, field_name),
                            content=part,
                            parent=parent,
                            quality=quality,
                            quality_score=quality_score,
                            field_order=order,
                            ordinal=value_index * 100 + split_index,
                            inferred_question=inferred_question and field_name == "QUESTION",
                        )
                    )
    return chunks


def _split_cases(text: str) -> list[CaseBlock]:
    cases: list[CaseBlock] = []
    current_id: str | None = None
    current_lines: list[str] = []
    seen: set[str] = set()
    duplicate = False

    def flush() -> None:
        if current_id is not None:
            cases.append(CaseBlock(case_id=current_id, lines=current_lines.copy(), duplicate=duplicate))

    for line in text.splitlines():
        match = CASE_ID_RE.match(line)
        if match:
            flush()
            current_id = match.group(1).upper()
            current_lines = []
            duplicate = current_id in seen
            seen.add(current_id)
            continue
        if current_id is not None:
            current_lines.append(line)

    flush()
    return cases


def _extract_case_fields(case: CaseBlock) -> tuple[dict[str, list[str]], bool]:
    fields: dict[str, list[str]] = {}
    current_field: str | None = None
    buffer: list[str] = []
    inferred_question = False

    def flush() -> None:
        nonlocal buffer
        content = "\n".join(line for line in buffer).strip()
        if content:
            fields.setdefault(current_field or "UNKNOWN", []).append(content)
        buffer = []

    for raw_line in case.lines:
        stripped = raw_line.strip()
        if not stripped:
            if buffer:
                buffer.append("")
            continue
        field_name = _normalize_field_title(_strip_heading(stripped))
        if field_name:
            flush()
            current_field = field_name
            continue
        buffer.append(raw_line)
    flush()

    if "QUESTION" not in fields:
        unknown_values = fields.get("UNKNOWN", [])
        if unknown_values:
            fields.setdefault("QUESTION", [unknown_values[0]])
            inferred_question = True
    return fields, inferred_question


def _strip_heading(line: str) -> str:
    match = FIELD_HEADING_RE.match(line)
    title = match.group(1) if match else line
    return title.strip().strip("#").strip()


def _normalize_field_title(title: str) -> str | None:
    normalized = re.sub(r"\s+", " ", title).strip().lower()
    for field_name, aliases in FIELD_ALIASES.items():
        if normalized in {alias.lower() for alias in aliases}:
            return field_name
    return None


def _assess_case_quality(fields: dict[str, list[str]], duplicate: bool) -> str:
    if duplicate:
        return "DUPLICATE"
    if "QUESTION" in fields and "SOLUTION" in fields:
        return "COMPLETE"
    return "PARTIAL" if fields else "MALFORMED"


def _case_quality_score(fields: dict[str, list[str]], duplicate: bool) -> float:
    score = 0.6
    if "QUESTION" in fields:
        score += 0.2
    if "SOLUTION" in fields:
        score += 0.3
    else:
        score -= 0.4
    if "REASON" in fields:
        score += 0.1
    if any(STEP_RE.match(line) for value in fields.get("SOLUTION", []) for line in value.splitlines()):
        score += 0.1
    if duplicate:
        score -= 0.2
    return max(0.0, min(1.2, score))


def _build_case_parent(
    document: ChunkDocument,
    case: CaseBlock,
    fields: dict[str, list[str]],
    quality: str,
    quality_score: float,
    ordinal: int,
) -> Chunk:
    parts = [f"工单号：{case.case_id}"]
    for field_name in ("QUESTION", "REASON", "SOLUTION", "REMARK"):
        title = FIELD_TITLES[field_name]
        values = fields.get(field_name)
        content = "\n\n".join(values) if values else "未提供"
        parts.append(f"【{title}】\n{content}")
    content = "\n\n".join(parts).strip()
    chunk_id = _stable_id(document.document_id, case.case_id, "CASE_PARENT", [case.case_id], ordinal)
    return _make_chunk(
        document=document,
        chunk_id=chunk_id,
        parent_chunk_id=None,
        content=content,
        chunk_type="CASE_PARENT",
        source_type="CASE_LIBRARY",
        section_path=[case.case_id],
        quality_score=quality_score,
        case_id=case.case_id,
        case_field=None,
        metadata={
            "case_quality": quality,
            "has_reason": "REASON" in fields,
            "has_solution": "SOLUTION" in fields,
            "split_strategy": "CASE_LIBRARY_MARKDOWN_TXT",
        },
    )


def _build_case_summary(
    document: ChunkDocument,
    case: CaseBlock,
    fields: dict[str, list[str]],
    parent: Chunk,
    quality: str,
    quality_score: float,
) -> Chunk:
    question = _first_text(fields.get("QUESTION"), "未提供")
    reason = _first_text(fields.get("REASON"), "未提供")
    solution = _first_text(fields.get("SOLUTION"), "未提供")
    content = f"工单 {case.case_id}：用户问题是“{_single_line(question)}”；根因为“{_single_line(reason)}”；解决措施包括“{_single_line(solution)}”。"
    chunk_id = _stable_id(document.document_id, case.case_id, "CASE_SUMMARY", [case.case_id, "CASE_SUMMARY"], 0)
    return _make_chunk(
        document=document,
        chunk_id=chunk_id,
        parent_chunk_id=parent.chunk_id,
        content=content,
        chunk_type="CASE_SUMMARY",
        source_type="CASE_LIBRARY",
        section_path=[case.case_id, "CASE_SUMMARY"],
        quality_score=quality_score,
        case_id=case.case_id,
        case_field="CASE_SUMMARY",
        metadata={"case_quality": quality, "split_strategy": "CASE_SUMMARY_RULE"},
    )


def _build_case_child(
    document: ChunkDocument,
    case: CaseBlock,
    field_name: str,
    field_title: str,
    content: str,
    parent: Chunk,
    quality: str,
    quality_score: float,
    field_order: int,
    ordinal: int,
    inferred_question: bool,
) -> Chunk:
    child_content = f"工单号：{case.case_id}\n字段：{field_title}\n内容：{content.strip()}"
    chunk_id = _stable_id(document.document_id, case.case_id, "CASE_CHILD", [case.case_id, field_title], ordinal)
    return _make_chunk(
        document=document,
        chunk_id=chunk_id,
        parent_chunk_id=parent.chunk_id,
        content=child_content,
        chunk_type="CASE_CHILD",
        source_type="CASE_LIBRARY",
        section_path=[case.case_id, field_title],
        quality_score=quality_score,
        case_id=case.case_id,
        case_field=field_name,
        metadata={
            "case_quality": quality,
            "case_field_title": field_title,
            "case_field_order": field_order,
            "inferred_question": inferred_question,
            "contextual_prefix": f"以下内容来自客服工单 {case.case_id} 的“{field_title}”字段。",
            "contextual_prefix_generated_by": "rule",
            "contextual_prefix_in_embedding": True,
            "split_strategy": "CASE_FIELD",
        },
    )


def _split_case_field(field_name: str, content: str) -> list[str]:
    if field_name != "SOLUTION":
        return _split_long_text(content, max_tokens=380)
    steps = _extract_steps(content)
    if not steps:
        return _split_long_text(content, max_tokens=380)
    grouped: list[str] = []
    current: list[str] = []
    for step in steps:
        candidate = "\n".join([*current, step]).strip()
        if current and approximate_token_count(candidate) > 320:
            grouped.append("\n".join(current).strip())
            current = [step]
        else:
            current.append(step)
    if current:
        grouped.append("\n".join(current).strip())
    return grouped


def _extract_steps(content: str) -> list[str]:
    steps: list[str] = []
    for line in content.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        if STEP_RE.match(stripped):
            steps.append(stripped)
        elif steps:
            steps[-1] = f"{steps[-1]}\n{stripped}"
    return steps


def _is_meaningless_field(field_name: str, content: str) -> bool:
    return field_name == "REMARK" and content.strip().lower() in MEANINGLESS_REMARKS


def _chunk_faq(document: ChunkDocument, text: str) -> list[Chunk]:
    chunks: list[Chunk] = []
    pairs: list[tuple[str, str]] = []
    current_question: str | None = None
    answer_lines: list[str] = []
    for line in text.splitlines():
        q_match = FAQ_Q_RE.match(line)
        a_match = FAQ_A_RE.match(line)
        if q_match:
            if current_question and answer_lines:
                pairs.append((current_question, "\n".join(answer_lines).strip()))
            current_question = q_match.group(1).strip()
            answer_lines = []
        elif a_match and current_question:
            answer_lines = [a_match.group(1).strip()]
        elif current_question and answer_lines:
            answer_lines.append(line)
    if current_question and answer_lines:
        pairs.append((current_question, "\n".join(answer_lines).strip()))

    for index, (question, answer) in enumerate(pairs):
        content = f"问题：{question}\n答案：{answer}"
        parent_id = _stable_id(document.document_id, "", "FAQ_PAIR", [question], index)
        parent = _make_chunk(
            document=document,
            chunk_id=parent_id,
            parent_chunk_id=None,
            content=content,
            chunk_type="FAQ_PAIR",
            source_type="FAQ",
            section_path=[question],
            quality_score=1.2,
            case_id=None,
            case_field=None,
            metadata={"question": question, "answer": answer, "split_strategy": "FAQ_PAIR"},
        )
        chunks.append(parent)
        if approximate_token_count(content) > 380:
            chunks.append(
                _make_chunk(
                    document=document,
                    chunk_id=_stable_id(document.document_id, "", "FAQ_QUESTION_CHILD", [question], index),
                    parent_chunk_id=parent_id,
                    content=f"问题：{question}",
                    chunk_type="FAQ_QUESTION_CHILD",
                    source_type="FAQ",
                    section_path=[question],
                    quality_score=1.1,
                    case_id=None,
                    case_field=None,
                    metadata={"split_strategy": "FAQ_QUESTION_CHILD"},
                )
            )
    return chunks


def _chunk_by_headings(document: ChunkDocument, text: str) -> list[Chunk]:
    sections: list[tuple[list[str], list[str]]] = []
    path_stack: list[str] = []
    current_path: list[str] = []
    current_lines: list[str] = []

    def flush() -> None:
        if current_path and any(line.strip() for line in current_lines):
            sections.append((current_path.copy(), current_lines.copy()))

    for line in text.splitlines():
        match = HEADING_RE.match(line)
        if match and not CASE_ID_RE.match(line):
            flush()
            level = len(match.group(1))
            title = match.group(2).strip()
            path_stack[:] = path_stack[: level - 1]
            path_stack.append(title)
            current_path = path_stack.copy()
            current_lines = []
        else:
            current_lines.append(line)
    flush()

    chunks: list[Chunk] = []
    for index, (path, lines) in enumerate(sections):
        body = "\n".join(lines).strip()
        if not body:
            continue
        parent_content = f"标题路径：{' > '.join(path)}\n内容：{body}"
        parent_id = _stable_id(document.document_id, "", "DOCUMENT_PARENT", path, index)
        parent = _make_chunk(
            document=document,
            chunk_id=parent_id,
            parent_chunk_id=None,
            content=parent_content,
            chunk_type="DOCUMENT_PARENT",
            source_type=detect_document_type(text),
            section_path=path,
            quality_score=1.1,
            case_id=None,
            case_field=None,
            metadata={"split_strategy": "HEADING_TREE"},
        )
        chunks.append(parent)
        for child_index, part in enumerate(_split_long_text(body, max_tokens=320), start=1):
            chunks.append(
                _make_chunk(
                    document=document,
                    chunk_id=_stable_id(document.document_id, "", "SECTION_CHILD", path, child_index),
                    parent_chunk_id=parent_id,
                    content=f"标题路径：{' > '.join(path)}\n内容：{part}",
                    chunk_type="SECTION_CHILD",
                    source_type=parent.source_type,
                    section_path=path,
                    quality_score=1.1,
                    case_id=None,
                    case_field=None,
                    metadata={
                        "contextual_prefix": f"以下内容来自文档《{document.title}》的章节“{' > '.join(path)}”。",
                        "contextual_prefix_generated_by": "rule",
                        "contextual_prefix_in_embedding": True,
                        "split_strategy": "SECTION_PARAGRAPH",
                    },
                )
            )
    return chunks or _chunk_semantic(document, text)


def _chunk_semantic(document: ChunkDocument, text: str) -> list[Chunk]:
    paragraphs = [part.strip() for part in re.split(r"\n\s*\n", text) if part.strip()]
    if not paragraphs and text.strip():
        paragraphs = [text.strip()]
    if not paragraphs:
        return []

    parent_id = _stable_id(document.document_id, "", "DOCUMENT_PARENT", [document.title], 0)
    parent = _make_chunk(
        document=document,
        chunk_id=parent_id,
        parent_chunk_id=None,
        content=text.strip(),
        chunk_type="DOCUMENT_PARENT",
        source_type=detect_document_type(text),
        section_path=[document.title],
        quality_score=1.0,
        case_id=None,
        case_field=None,
        metadata={"split_strategy": "SEMANTIC_PARAGRAPH_PARENT"},
    )
    chunks = [parent]
    current: list[str] = []
    ordinal = 1
    for paragraph in paragraphs:
        candidate = "\n\n".join([*current, paragraph]).strip()
        if current and approximate_token_count(candidate) > 320:
            chunks.append(_semantic_child(document, parent_id, current, ordinal))
            ordinal += 1
            current = [paragraph]
        else:
            current.append(paragraph)
    if current:
        chunks.append(_semantic_child(document, parent_id, current, ordinal))
    return chunks


def _semantic_child(document: ChunkDocument, parent_id: str, paragraphs: list[str], ordinal: int) -> Chunk:
    content = "\n\n".join(paragraphs).strip()
    return _make_chunk(
        document=document,
        chunk_id=_stable_id(document.document_id, "", "SEMANTIC_CHILD", [document.title], ordinal),
        parent_chunk_id=parent_id,
        content=content,
        chunk_type="SEMANTIC_CHILD",
        source_type="NOTE",
        section_path=[document.title],
        quality_score=0.9 if approximate_token_count(content) < 40 else 1.0,
        case_id=None,
        case_field=None,
        metadata={"split_strategy": "SEMANTIC_PARAGRAPH"},
    )


def _split_long_text(content: str, max_tokens: int) -> list[str]:
    if approximate_token_count(content) <= max_tokens:
        return [content.strip()]
    sentences = [part.strip() for part in re.split(r"(?<=[。！？；.!?;])", content) if part.strip()]
    if not sentences:
        return _hard_split(content, max_chars=max_tokens * 2)
    result: list[str] = []
    current: list[str] = []
    for sentence in sentences:
        candidate = "".join([*current, sentence])
        if current and approximate_token_count(candidate) > max_tokens:
            result.append("".join(current).strip())
            current = [sentence]
        else:
            current.append(sentence)
    if current:
        result.append("".join(current).strip())
    return result


def _hard_split(content: str, max_chars: int) -> list[str]:
    return [content[index : index + max_chars].strip() for index in range(0, len(content), max_chars)]


def _make_chunk(
    document: ChunkDocument,
    chunk_id: str,
    parent_chunk_id: str | None,
    content: str,
    chunk_type: str,
    source_type: str,
    section_path: list[str],
    quality_score: float,
    case_id: str | None,
    case_field: str | None,
    metadata: dict[str, Any],
) -> Chunk:
    now = datetime.now(timezone.utc).isoformat()
    token_count = approximate_token_count(content)
    final_chunk_id = _versioned_id(document, chunk_id)
    base_metadata = {
        "chunk_id": final_chunk_id,
        "parent_chunk_id": parent_chunk_id,
        "document_id": document.document_id,
        "kb_id": document.kb_id,
        "kb_name": document.kb_name,
        "kb_version": document.kb_version,
        "kb_type": document.kb_type,
        "tenant_id": document.tenant_id,
        "scope": document.scope,
        "owner_user_id": document.owner_user_id,
        "status": document.status,
        "enabled": document.enabled,
        "chunk_type": chunk_type,
        "source_type": source_type,
        "title": document.title,
        "document_title": document.title,
        "section_path": section_path,
        "content_hash": _content_hash(content),
        "quality_score": quality_score,
        "token_count": token_count,
        "language": document.language,
        "category_id": document.category_id,
        "tag_ids": document.tag_ids,
        "product_line": document.product_line,
        "created_at": now,
        "updated_at": now,
    }
    base_metadata.update(metadata)
    return Chunk(
        chunk_id=final_chunk_id,
        parent_chunk_id=parent_chunk_id,
        document_id=document.document_id,
        kb_id=document.kb_id,
        kb_version=document.kb_version,
        kb_type=document.kb_type,
        content=content,
        chunk_type=chunk_type,
        source_type=source_type,
        tenant_id=document.tenant_id,
        scope=document.scope,
        owner_user_id=document.owner_user_id,
        section_path=section_path,
        token_count=token_count,
        content_hash=_content_hash(content),
        quality_score=quality_score,
        status=document.status,
        enabled=document.enabled,
        case_id=case_id,
        case_field=case_field,
        metadata=base_metadata,
    )


def _stable_id(document_id: str, case_id: str, chunk_type: str, section_path: list[str], ordinal: int) -> str:
    raw = "|".join([document_id, case_id, chunk_type, "/".join(section_path), str(ordinal)])
    return "sha256:" + hashlib.sha256(raw.encode("utf-8")).hexdigest()[:48]


def _versioned_id(document: ChunkDocument, stable_id: str) -> str:
    raw = f"{document.kb_id}|{document.kb_version}|{stable_id}"
    return "sha256:" + hashlib.sha256(raw.encode("utf-8")).hexdigest()[:48]


def _content_hash(content: str) -> str:
    return "sha256:" + hashlib.sha256(content.strip().encode("utf-8")).hexdigest()


def approximate_token_count(text: str) -> int:
    cjk_count = len(re.findall(r"[\u4e00-\u9fff]", text))
    word_count = len(re.findall(r"[A-Za-z0-9_]+", text))
    punctuation = len(re.findall(r"[^\s\w\u4e00-\u9fff]", text)) // 2
    return max(1, cjk_count + word_count + punctuation)


def _first_text(values: list[str] | None, fallback: str) -> str:
    return values[0] if values else fallback


def _single_line(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()
