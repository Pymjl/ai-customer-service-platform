from aicsp_engine.tools.schemas import ToolSpec


DEFAULT_TOOL_SPECS = {
    "order.query": ToolSpec(
        name="order.query",
        description="查询订单状态、支付状态、发货状态等只读信息。",
        permission_level="read",
    ),
    "logistics.query": ToolSpec(
        name="logistics.query",
        description="查询物流轨迹、异常件和配送状态等只读信息。",
        permission_level="read",
    ),
    "crm.query": ToolSpec(
        name="crm.query",
        description="查询脱敏客户资料和服务记录等只读信息。",
        permission_level="read",
    ),
}


class ToolRegistry:
    def __init__(self) -> None:
        self._tools: dict[str, ToolSpec] = dict(DEFAULT_TOOL_SPECS)

    def register(self, name: str, tool: ToolSpec) -> None:
        self._tools[name] = tool

    def get(self, name: str) -> ToolSpec | None:
        return self._tools.get(name)

    def all(self) -> list[ToolSpec]:
        return list(self._tools.values())
