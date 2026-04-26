from threading import Lock
from typing import Any

from aicsp_engine.models.rag import RagTaskResponse, TaskStatusResponse


class InMemoryTaskStore:
    def __init__(self) -> None:
        self._lock = Lock()
        self._tasks: dict[str, TaskStatusResponse] = {}

    def submit(
        self,
        task_id: str,
        document_id: str,
        trace_id: str | None,
        operation: str,
    ) -> RagTaskResponse:
        with self._lock:
            task = self._tasks.get(task_id)
            if task is None:
                task = TaskStatusResponse(
                    taskId=task_id,
                    documentId=document_id,
                    status="SUBMITTED",
                    accepted=True,
                    traceId=trace_id,
                    operation=operation,
                    message="task accepted; background pipeline is reserved for later implementation",
                    progress=0,
                    metrics={},
                )
                self._tasks[task_id] = task
            return RagTaskResponse(
                taskId=task.taskId,
                documentId=task.documentId,
                status=task.status,
                accepted=task.accepted,
                traceId=task.traceId,
            )

    def get(self, task_id: str) -> TaskStatusResponse | None:
        with self._lock:
            return self._tasks.get(task_id)

    def update(
        self,
        task_id: str,
        status: str,
        message: str | None = None,
        progress: int | None = None,
        metrics: dict[str, Any] | None = None,
    ) -> None:
        with self._lock:
            task = self._tasks.get(task_id)
            if task is None:
                return
            task.status = status
            if message is not None:
                task.message = message
            if progress is not None:
                task.progress = progress
            if metrics is not None:
                task.metrics = metrics


_STORE = InMemoryTaskStore()


def get_task_store() -> InMemoryTaskStore:
    return _STORE
