from openai import AsyncOpenAI

from aicsp_engine.core.config import Settings


class EmbeddingModel:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def embed_texts(self, texts: list[str]) -> list[list[float]]:
        client = AsyncOpenAI(
            base_url=self._settings.embedding_base_url,
            api_key=self._settings.embedding_api_key,
            timeout=self._settings.embedding_timeout_seconds,
        )
        response = await client.embeddings.create(model=self._settings.embedding_model, input=texts)
        return [item.embedding for item in response.data]
