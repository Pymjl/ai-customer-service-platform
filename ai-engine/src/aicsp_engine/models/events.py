import json
from typing import Any

from pydantic import BaseModel


class EngineEvent(BaseModel):
    event: str
    data: str

    @classmethod
    def done(
        cls,
        finish_reason: str = "stop",
        trace_id: str | None = None,
        rag: dict[str, Any] | None = None,
    ) -> "EngineEvent":
        payload: dict[str, Any] = {"finishReason": finish_reason, "traceId": trace_id}
        if rag is not None:
            payload["rag"] = rag
        return cls(
            event="done",
            data=json.dumps(
                payload,
                ensure_ascii=False,
                separators=(",", ":"),
            ),
        )

    @classmethod
    def error(cls, message: str, trace_id: str | None = None) -> "EngineEvent":
        return cls(
            event="error",
            data=json.dumps(
                {"message": message, "traceId": trace_id},
                ensure_ascii=False,
                separators=(",", ":"),
            ),
        )

    @classmethod
    def citation(cls, payload: dict[str, Any]) -> "EngineEvent":
        return cls(event="citation", data=json.dumps(payload, ensure_ascii=False, separators=(",", ":")))


def ndjson_line(event: EngineEvent) -> str:
    return event.model_dump_json(by_alias=True) + "\n"
