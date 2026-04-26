from fastapi import FastAPI

from aicsp_engine.api.routes_chat import router as chat_router
from aicsp_engine.api.routes_health import router as health_router
from aicsp_engine.api.routes_rag import router as rag_router
from aicsp_engine.core.config import get_settings
from aicsp_engine.core.logging import configure_logging


def create_app() -> FastAPI:
    configure_logging()
    settings = get_settings()
    app = FastAPI(title=settings.app_name, version=settings.app_version)
    app.include_router(health_router)
    app.include_router(chat_router)
    app.include_router(rag_router)
    return app


app = create_app()
