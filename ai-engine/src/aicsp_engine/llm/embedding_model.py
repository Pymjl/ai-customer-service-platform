from aicsp_engine.core.config import Settings
from aicsp_engine.llm.clients import create_openai_client


class EmbeddingModel:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def embed_texts(self, texts: list[str]) -> list[list[float]]:
        client = create_openai_client(
            self._settings.embedding_base_url,
            self._settings.embedding_api_key,
            self._settings.embedding_timeout_seconds,
        )
        try:
            response = await client.embeddings.create(model=self._settings.embedding_model, input=texts)
            return [item.embedding for item in response.data]
        finally:
            await client.close()
