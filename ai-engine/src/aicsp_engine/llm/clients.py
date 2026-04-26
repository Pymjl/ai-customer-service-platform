from openai import AsyncOpenAI
import httpx


def create_openai_client(base_url: str, api_key: str, timeout: float) -> AsyncOpenAI:
    return AsyncOpenAI(
        base_url=base_url,
        api_key=api_key,
        timeout=timeout,
        http_client=httpx.AsyncClient(timeout=timeout, trust_env=False),
    )
