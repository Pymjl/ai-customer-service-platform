from aicsp_engine.rag.chunking import ChunkDocument, chunk_document, detect_document_type


def test_case_library_generates_parent_summary_and_children() -> None:
    document = ChunkDocument(
        document_id="doc_001",
        title="售后案例库",
        text="""# T2026042600000001

## 问题描述
支付成功后订单仍显示待支付。

## 问题根因
支付回调延迟。

## 解决方案与措施
1. 查询支付流水。
2. 触发订单状态同步。

## 备注
超过 30 分钟转人工。
""",
    )

    chunks = chunk_document(document)

    assert detect_document_type(document.text) == "CASE_LIBRARY"
    assert [chunk.chunk_type for chunk in chunks].count("CASE_PARENT") == 1
    assert [chunk.chunk_type for chunk in chunks].count("CASE_SUMMARY") == 1
    assert any(chunk.case_field == "QUESTION" for chunk in chunks)
    assert any(chunk.case_field == "SOLUTION" for chunk in chunks)
    assert all(chunk.scope == "PUBLIC" and chunk.owner_user_id is None for chunk in chunks)


def test_multi_case_document_never_crosses_case_parent() -> None:
    document = ChunkDocument(
        document_id="doc_002",
        title="多工单",
        text="""# T2026042600000001
## 问题描述
问题一。
## 解决方案与措施
方案一。

# T2026042600000002
## 问题描述
问题二。
## 解决方案与措施
方案二。
""",
    )

    parents = [chunk for chunk in chunk_document(document) if chunk.chunk_type == "CASE_PARENT"]

    assert len(parents) == 2
    assert parents[0].case_id == "T2026042600000001"
    assert parents[1].case_id == "T2026042600000002"
    assert "问题二" not in parents[0].content
    assert "问题一" not in parents[1].content


def test_personal_document_requires_owner() -> None:
    document = ChunkDocument(document_id="doc_003", title="个人", text="内容", scope="PERSONAL")

    try:
        chunk_document(document)
    except ValueError as exc:
        assert "owner_user_id" in str(exc)
    else:
        raise AssertionError("expected owner validation error")


def test_heading_document_generates_parent_child() -> None:
    document = ChunkDocument(
        document_id="doc_004",
        title="售后政策",
        text="""# 退款规则

退款审核通过后通常 1-3 个工作日到账。

## 节假日

节假日可能顺延。
""",
        scope="PERSONAL",
        owner_user_id="u_001",
    )

    chunks = chunk_document(document)

    assert detect_document_type(document.text) == "MANUAL"
    assert any(chunk.chunk_type == "DOCUMENT_PARENT" for chunk in chunks)
    assert any(chunk.chunk_type == "SECTION_CHILD" for chunk in chunks)
    assert all(chunk.scope == "PERSONAL" and chunk.owner_user_id == "u_001" for chunk in chunks)
