from uuid import uuid4


def resolve_trace_id(trace_id: str | None) -> str:
    return trace_id if trace_id else uuid4().hex
