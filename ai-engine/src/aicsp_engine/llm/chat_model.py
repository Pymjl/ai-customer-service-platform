from collections.abc import AsyncIterator
from typing import Any, cast

from openai.types.chat.chat_completion_message_param import ChatCompletionMessageParam

from aicsp_engine.core.config import Settings
from aicsp_engine.core.prompts import (
    AGENT_LLM_DISABLED_STREAM_CHUNKS,
    DEFAULT_CHAT_SYSTEM_PROMPT,
    LLM_DISABLED_STREAM_CHUNKS,
)
from aicsp_engine.llm.clients import create_openai_client
from aicsp_engine.models.chat import EngineRequest


class ChatModelService:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def stream_chat(self, request: EngineRequest) -> AsyncIterator[str]:
        if not self._settings.llm_enabled:
            for placeholder_text in LLM_DISABLED_STREAM_CHUNKS:
                yield placeholder_text
            return

        client = create_openai_client(
            self._settings.llm_base_url,
            self._settings.llm_api_key,
            self._settings.llm_timeout_seconds,
        )
        try:
            stream = await client.chat.completions.create(
                model=self._settings.llm_model,
                messages=[
                    {"role": "system", "content": DEFAULT_CHAT_SYSTEM_PROMPT},
                    {"role": "user", "content": request.message},
                ],
                stream=True,
                temperature=self._settings.llm_temperature,
                max_tokens=self._settings.llm_max_tokens,
            )
            async for chunk in stream:
                delta = chunk.choices[0].delta.content if chunk.choices else None
                if delta:
                    yield delta
        finally:
            await client.close()

    async def stream_messages(self, messages: list[dict[str, Any]]) -> AsyncIterator[str]:
        if not self._settings.llm_enabled:
            for placeholder_text in AGENT_LLM_DISABLED_STREAM_CHUNKS:
                yield placeholder_text
            return

        client = create_openai_client(
            self._settings.llm_base_url,
            self._settings.llm_api_key,
            self._settings.llm_timeout_seconds,
        )
        typed_messages = cast(list[ChatCompletionMessageParam], messages)
        try:
            stream = await client.chat.completions.create(
                model=self._settings.llm_model,
                messages=typed_messages,
                stream=True,
                temperature=self._settings.llm_temperature,
                max_tokens=self._settings.llm_max_tokens,
            )
            async for chunk in stream:
                delta = chunk.choices[0].delta.content if chunk.choices else None
                if delta:
                    yield delta
        finally:
            await client.close()
