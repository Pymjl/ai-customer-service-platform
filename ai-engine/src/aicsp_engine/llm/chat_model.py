from collections.abc import AsyncIterator
from typing import Any, cast

from openai import AsyncOpenAI
from openai.types.chat.chat_completion_message_param import ChatCompletionMessageParam

from aicsp_engine.core.config import Settings
from aicsp_engine.models.chat import EngineRequest


class ChatModelService:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def stream_chat(self, request: EngineRequest) -> AsyncIterator[str]:
        if not self._settings.llm_enabled:
            yield "Python 引擎已收到请求。"
            yield "当前模型调用处于占位模式，后续会接入 OpenAI 兼容模型服务。"
            return

        client = AsyncOpenAI(
            base_url=self._settings.llm_base_url,
            api_key=self._settings.llm_api_key,
            timeout=self._settings.llm_timeout_seconds,
        )
        stream = await client.chat.completions.create(
            model=self._settings.llm_model,
            messages=[
                {"role": "system", "content": "你是智能客服助手，回答要准确、简洁、可追溯。"},
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

    async def stream_messages(self, messages: list[dict[str, Any]]) -> AsyncIterator[str]:
        if not self._settings.llm_enabled:
            yield "Python 智能客服 Agent 已完成上下文准备。"
            yield "当前大模型调用处于占位模式，请在 .env 中启用 AICSP_LLM_ENABLED=true。"
            return

        client = AsyncOpenAI(
            base_url=self._settings.llm_base_url,
            api_key=self._settings.llm_api_key,
            timeout=self._settings.llm_timeout_seconds,
        )
        typed_messages = cast(list[ChatCompletionMessageParam], messages)
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
