import json

from fastapi.testclient import TestClient

from aicsp_engine.main import app


def test_chat_stream_returns_ndjson_events() -> None:
    client = TestClient(app)

    response = client.post(
        "/api/chat/stream",
        headers={"X-Internal-Token": "dev-stream-internal-token"},
        json={"sessionId": "s_001", "message": "你好", "traceId": "trace_001"},
    )

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("application/x-ndjson")
    events = [json.loads(line) for line in response.text.splitlines() if line]
    assert events
    assert events[-1]["event"] == "done"
    assert all(set(event.keys()) == {"event", "data"} for event in events)


def test_rag_preview_validates_public_owner() -> None:
    client = TestClient(app)

    response = client.post(
        "/internal/rag/chunk/preview",
        headers={"X-Internal-Token": "dev-biz-internal-token"},
        json={
            "documentId": "doc_001",
            "title": "公共文档",
            "text": "内容",
            "scope": "PUBLIC",
            "ownerUserId": "u_001",
        },
    )

    assert response.status_code == 422
