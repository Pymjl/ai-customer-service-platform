from fastapi import APIRouter

from aicsp_engine.core.config import get_settings

router = APIRouter(tags=["health"])


@router.get("/healthz")
async def healthz() -> dict[str, str]:
    settings = get_settings()
    return {"status": "UP", "service": settings.app_name, "version": settings.app_version}


@router.get("/actuator/health")
async def actuator_health() -> dict[str, str]:
    return {"status": "UP"}
