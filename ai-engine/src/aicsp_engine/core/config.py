from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AICSP_", env_file=".env", extra="ignore")

    app_name: str = "aicsp-engine"
    app_version: str = "0.1.0"
    app_env: str = "dev"

    stream_internal_token: str = "dev-stream-internal-token"
    biz_internal_token: str = "dev-biz-internal-token"
    biz_base_url: str = "http://localhost:8082"
    message_completed_callback_enabled: bool = False

    llm_enabled: bool = False
    llm_base_url: str = "http://localhost:11434/v1"
    llm_api_key: str = "ollama"
    llm_model: str = "gemma4:26b"
    llm_temperature: float = 0.2
    llm_max_tokens: int = 2048
    llm_timeout_seconds: float = 300.0

    embedding_base_url: str = "http://localhost:11434/v1"
    embedding_api_key: str = "ollama"
    embedding_model: str = "qwen3-embedding:8b"
    embedding_dimensions: int = 4096
    embedding_timeout_seconds: float = 120.0

    postgres_dsn: str | None = Field(default=None)
    vector_store_enabled: bool = False
    postgres_schema: str = "engine_service"
    postgres_chunk_table: str = "engine_chunk"
    postgres_embedding_table: str = "engine_embedding"
    retrieval_top_k_default: int = 8
    retrieval_top_k_max: int = 50
    ingestion_embedding_batch_size: int = 16
    ingestion_background_enabled: bool = True
    allow_local_object_path: bool = True
    redis_url: str | None = Field(default=None)

    minio_enabled: bool = False
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin"
    minio_bucket: str = "aicsp-knowledge"
    minio_secure: bool = False


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
