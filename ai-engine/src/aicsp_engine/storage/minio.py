from __future__ import annotations

from io import BytesIO

from minio import Minio

from aicsp_engine.core.config import Settings


class MinioStore:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    def is_configured(self) -> bool:
        return bool(self._settings.minio_enabled)

    async def read_text_object(self, object_path: str) -> str:
        if not self.is_configured():
            raise RuntimeError("MinIO is not configured")
        bucket, object_name = self._parse_object_path(object_path)
        client = Minio(
            endpoint=self._settings.minio_endpoint,
            access_key=self._settings.minio_access_key,
            secret_key=self._settings.minio_secret_key,
            secure=self._settings.minio_secure,
        )
        response = client.get_object(bucket, object_name)
        try:
            data = response.read()
        finally:
            response.close()
            response.release_conn()
        return _decode_bytes(data)

    def _parse_object_path(self, object_path: str) -> tuple[str, str]:
        if object_path.startswith("minio://"):
            normalized_uri = object_path.removeprefix("minio://").lstrip("/")
            bucket, object_name = normalized_uri.split("/", 1)
            return bucket, object_name
        normalized = object_path.lstrip("/")
        if "/" not in normalized:
            return self._settings.minio_bucket, normalized
        first, rest = normalized.split("/", 1)
        if "." in first or first == self._settings.minio_bucket:
            return first, rest
        return self._settings.minio_bucket, normalized


def _decode_bytes(data: bytes) -> str:
    for encoding in ("utf-8", "utf-8-sig", "gb18030"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    return BytesIO(data).read().decode("utf-8", errors="ignore")
