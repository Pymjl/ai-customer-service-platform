from __future__ import annotations

import os
import sys

import httpx


def main() -> int:
    base_url = os.getenv("AICSP_EMBEDDING_BASE_URL", "http://localhost:11434/v1").rstrip("/")
    api_key = os.getenv("AICSP_EMBEDDING_API_KEY", "ollama")
    model = os.getenv("AICSP_EMBEDDING_MODEL", "qwen3-embedding:8b")
    timeout = float(os.getenv("AICSP_EMBEDDING_TIMEOUT_SECONDS", "120"))
    expected_dimensions = os.getenv("AICSP_EMBEDDING_DIMENSIONS")

    try:
        embedding, endpoint = _request_openai_embedding(base_url, api_key, model, timeout)
    except Exception as exc:
        print(f"OpenAI 兼容接口检测失败，尝试 Ollama 原生接口：{exc}", file=sys.stderr)
        try:
            embedding, endpoint = _request_ollama_embedding(base_url, model, timeout)
        except Exception as fallback_exc:
            print(f"检测失败：{fallback_exc}", file=sys.stderr)
            return 1

    if not isinstance(embedding, list):
        print("检测失败：返回结果中的 embedding 不是数组。", file=sys.stderr)
        return 1

    actual_dimensions = len(embedding)
    print(f"base_url={base_url}")
    print(f"endpoint={endpoint}")
    print(f"model={model}")
    print(f"actual_dimensions={actual_dimensions}")
    if expected_dimensions:
        print(f"configured_dimensions={expected_dimensions}")
    return 0


def _request_openai_embedding(
    base_url: str,
    api_key: str,
    model: str,
    timeout: float,
) -> tuple[list[object], str]:
    endpoint = f"{base_url}/embeddings"
    with httpx.Client(timeout=timeout, trust_env=False) as client:
        response = client.post(
            endpoint,
            json={
                "model": model,
                "input": "用于检测 embedding 模型返回向量维度的测试文本。",
            },
            headers={"Authorization": f"Bearer {api_key}"},
        )
    response.raise_for_status()
    body = response.json()
    return body["data"][0]["embedding"], endpoint


def _request_ollama_embedding(
    base_url: str,
    model: str,
    timeout: float,
) -> tuple[list[object], str]:
    ollama_base_url = base_url.removesuffix("/v1")
    endpoint = f"{ollama_base_url}/api/embed"
    with httpx.Client(timeout=timeout, trust_env=False) as client:
        response = client.post(
            endpoint,
            json={
                "model": model,
                "input": "用于检测 embedding 模型返回向量维度的测试文本。",
            },
        )
    response.raise_for_status()
    body = response.json()
    return body["embeddings"][0], endpoint


if __name__ == "__main__":
    raise SystemExit(main())
