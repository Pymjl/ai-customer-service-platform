from pathlib import Path

from aicsp_engine.core.config import Settings
from aicsp_engine.models.rag import IngestRequest
from aicsp_engine.storage.minio import MinioStore


class DocumentLoader:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._minio = MinioStore(settings)

    async def load_text(self, request: IngestRequest) -> str:
        if request.rawText and request.rawText.strip():
            return request.rawText
        if request.objectPath and self._settings.allow_local_object_path:
            path = Path(request.objectPath)
            if path.exists() and path.is_file():
                return path.read_text(encoding="utf-8")
        if request.objectPath and self._minio.is_configured():
            return await self._minio.read_text_object(request.objectPath)
        raise ValueError("未提取到可检索文本；对象存储解析器尚未接入")
