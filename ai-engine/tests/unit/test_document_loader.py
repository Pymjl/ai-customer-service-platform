from pathlib import Path

import pytest

from aicsp_engine.core.config import Settings
from aicsp_engine.models.rag import IngestRequest
from aicsp_engine.storage.document_loader import DocumentLoader
from aicsp_engine.storage.minio import MinioStore


@pytest.mark.asyncio
async def test_document_loader_reads_local_text_file(tmp_path: Path) -> None:
    source = tmp_path / "case.md"
    source.write_text("# T2026042700000001\n## 问题描述\n本地文件读取。", encoding="utf-8")

    text = await DocumentLoader(Settings(allow_local_object_path=True)).load_text(
        IngestRequest(
            taskId="task_local",
            documentId="doc_local",
            scope="PUBLIC",
            tenantId="default",
            objectPath=str(source),
            title="本地文件",
        )
    )

    assert "本地文件读取" in text


def test_minio_object_path_parser() -> None:
    store = MinioStore(Settings(minio_bucket="default-bucket"))

    assert store._parse_object_path("minio://kb/docs/a.md") == ("kb", "docs/a.md")
    assert store._parse_object_path("docs/a.md") == ("default-bucket", "docs/a.md")

