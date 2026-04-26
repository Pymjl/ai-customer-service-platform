from typing import Any, Literal

from pydantic import BaseModel, Field


PermissionLevel = Literal["read", "write_confirm", "human_approval"]


class ToolSpec(BaseModel):
    name: str
    description: str
    permission_level: PermissionLevel = "read"
    timeout_seconds: float = 10.0


class ToolCallInput(BaseModel):
    toolCallId: str
    sessionId: str
    messageId: str | None = None
    userId: str | None = None
    tenantId: str | None = None
    arguments: dict[str, Any] = Field(default_factory=dict)
    idempotencyKey: str | None = None


class ToolCallOutput(BaseModel):
    toolCallId: str
    success: bool
    data: dict[str, Any] = Field(default_factory=dict)
    errorCode: str | None = None
    message: str | None = None
