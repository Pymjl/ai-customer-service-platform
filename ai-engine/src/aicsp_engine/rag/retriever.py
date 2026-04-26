from time import perf_counter

from aicsp_engine.core.config import Settings
from aicsp_engine.llm.embedding_model import EmbeddingModel
from aicsp_engine.models.rag import RetrieveRequest, RetrieveResponse
from aicsp_engine.storage.postgres import PostgresVectorStore


class Retriever:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._store = PostgresVectorStore(settings)
        self._embedding_model = EmbeddingModel(settings)

    async def retrieve(self, request: RetrieveRequest) -> RetrieveResponse:
        started = perf_counter()
        if request.knowledgeSelection and request.knowledgeSelection.mode == "NONE":
            response = RetrieveResponse(
                query=request.query,
                hits=[],
                skipped=True,
                reason="knowledge_selection_none",
                traceId=request.traceId,
            )
            await self._store.write_retrieval_log(request, [], True, response.reason, _elapsed_ms(started))
            return response
        if not self._store.is_configured():
            return RetrieveResponse(
                query=request.query,
                hits=[],
                skipped=False,
                reason="vector_store_disabled",
                traceId=request.traceId,
            )
        query_embedding = (await self._embedding_model.embed_texts([request.query]))[0]
        hits = await self._store.similarity_search(request, query_embedding)
        response = RetrieveResponse(
            query=request.query,
            hits=hits,
            skipped=False,
            reason=None if hits else "no_hits",
            traceId=request.traceId,
        )
        await self._store.write_retrieval_log(request, hits, False, response.reason, _elapsed_ms(started))
        return response


def _elapsed_ms(started: float) -> int:
    return int((perf_counter() - started) * 1000)
