# stream-service 软件设计文档（SDD）

**版本**：2.0.0  
**日期**：2026-04-26  
**服务端口**：`18082`  
**技术基线**：Java 21 · Spring Boot 3.5.13 · WebFlux · Reactor Netty

---

## 1. 目标与范围

`stream-service` 是智能客服的 **SSE 流式转发服务**。它的职责始终保持单一：接收网关转发的 `/api/chat/stream` 请求，透传用户身份头和请求体到 Python `ai-engine`，再将 Python 引擎返回的 NDJSON 流式事件转换为前端 SSE 事件。

`stream-service` 只处理流式连接本身，不做业务防腐、不做 function call、不发布消息完成事件、不写数据库、不消费或生产 RocketMQ。

核心职责：

| 能力 | 说明 |
|---|---|
| SSE 对话入口 | 对外提供 `POST /api/chat/stream` |
| Python 引擎转发 | 调用 `ai-engine /api/chat/stream` |
| 流式事件透传 | 将 Python `EngineEvent` 转换为前端 SSE 事件 |
| 连接治理 | 超时、心跳、客户端断开、流内错误转 SSE error |
| 身份头透传 | 透传网关注入的用户、租户、角色、traceId |

明确不负责：

| 不负责能力 | 归属 |
|---|---|
| Function Call 业务工具接口 | `biz-service` |
| 订单、物流、CRM 等业务防腐 | `biz-service` |
| 消息完成持久化 | `biz-service`，由 `ai-engine` 回调或内部接口写入 |
| RocketMQ 消息发布 | 不属于 `stream-service` |
| 知识库权限、selectable、贡献审批 | `biz-service` |
| RAG 检索、Agent、模型调用 | `ai-engine` |

---

## 2. 外部依赖

| 依赖 | 用途 | dev 配置 |
|---|---|---|
| `gateway-service` | 统一入口、鉴权、身份头注入 | `http://localhost:18080` |
| `ai-engine` | 模型、RAG、Agent、流式生成 | `http://localhost:8000` |

`stream-service` 不直接依赖 PostgreSQL、Redis、RocketMQ、MinIO 或其它业务服务。

---

## 3. 接口规范

### 3.1 `POST /api/chat/stream`

| 项 | 值 |
|---|---|
| Method | `POST` |
| Path | `/api/chat/stream` |
| Content-Type | `application/json` |
| Produces | `text/event-stream` |
| 权限 | 登录用户，经 `gateway-service` 鉴权 |

请求体：

```json
{
  "messageId": "m_001",
  "sessionId": "s_001",
  "message": "退款多久到账？",
  "locale": "zh-CN",
  "knowledgeSelection": {
    "mode": "SELECTED",
    "includePublic": true,
    "includePersonal": false,
    "documentIds": ["doc_public_001"],
    "categoryIds": ["cat_after_sales"],
    "tagIds": ["tag_refund"]
  }
}
```

字段说明：

| 字段 | 必填 | 说明 |
|---|---|---|
| `messageId` | 是 | 消息幂等 ID，后续 `ai-engine -> biz-service /internal/chat/message-completed` 以该字段作为幂等键 |
| `sessionId` | 是 | 会话 ID |
| `message` | 是 | 用户输入 |
| `locale` | 否 | 用户语言区域，默认 `zh-CN`，透传给 Python |
| `knowledgeSelection` | 否 | 本次问答启用的知识库候选范围 |

`stream-service` 不解释 `knowledgeSelection` 的业务含义，只转发。可选范围由前端从 `biz-service /api/knowledge/selectable` 获取；实际 RAG 检索前，`ai-engine` 必须调用 `biz-service` 的内网过滤条件解析能力获取权威过滤条件，并只执行该过滤条件。

### 3.2 可信身份头

`stream-service` 只信任 `gateway-service` 注入的身份头，并将它们转发给 `ai-engine`：

| Header | 必填 | 说明 |
|---|---|---|
| `X-User-Id` | 是 | 当前用户 ID |
| `X-Tenant-Id` | 是 | 当前租户 ID |
| `X-User-Roles` | 是 | 当前用户角色 |
| `X-Trace-Id` | 建议 | 链路追踪 ID，不存在时可生成 |

约束：

- 不信任请求体中的 `userId`、`tenantId`、`roles`。
- 不做 RBAC 判断。
- 不做知识库 selectable 校验。
- 不做 function call 工具权限判断。

---

## 4. 与 ai-engine 的契约

### 4.1 请求

```text
POST {python.engine.base-url}/api/chat/stream
```

转发给 Python 的请求体：

```json
{
  "messageId": "m_001",
  "sessionId": "s_001",
  "message": "退款多久到账？",
  "traceId": "trace-001",
  "userId": "u_001",
  "tenantId": "default",
  "roles": ["USER"],
  "locale": "zh-CN",
  "knowledgeSelection": {
    "mode": "SELECTED",
    "includePublic": true,
    "includePersonal": false,
    "documentIds": ["doc_public_001"],
    "categoryIds": ["cat_after_sales"],
    "tagIds": ["tag_refund"]
  },
  "metadata": {}
}
```

字段来源：

| 字段 | 来源 |
|---|---|
| `messageId` | 前端请求 |
| `sessionId` | 前端请求 |
| `message` | 前端请求 |
| `traceId` | `X-Trace-Id` 或本服务生成 |
| `userId` | `X-User-Id` |
| `tenantId` | `X-Tenant-Id` |
| `roles` | `X-User-Roles` |
| `locale` | 前端请求体；为空时默认 `zh-CN` |
| `knowledgeSelection` | 前端请求，空则原样为空或补 `DEFAULT` |

`messageId` 是必填字段。目标阶段前端必须显式传入，并在用户点击“重试/重新生成”时复用同一个 `messageId` 或显式创建新的 `messageId` 表示新一轮消息。兼容旧客户端时，如果请求体缺少 `messageId`，`stream-service` 可为当前 HTTP 请求生成 UUIDv7/ULID 并转发；由于 `stream-service` 无状态，无法跨 HTTP 请求自动识别“同一次重试”，因此缺少 `messageId` 的旧客户端不保证重试幂等。

### 4.2 响应

Python 上游响应固定为 NDJSON，响应头必须为 `Content-Type: application/x-ndjson; charset=utf-8`。`stream-service` 使用 WebClient 按行解析，每行反序列化为一个完整 `EngineEvent`；不得按 SSE 格式解析 Python 上游响应。

```json
{"event":"message","data":"退款通常"}
{"event":"message","data":" 1-3 个工作日到账。"}
{"event":"citation","data":"{\"documentId\":\"doc_public_001\",\"title\":\"售后政策手册\"}"}
{"event":"done","data":"{\"finishReason\":\"stop\"}"}
```

Java 侧 `EngineEvent`：

```json
{
  "event": "message",
  "data": "退款通常"
}
```

### 4.3 事件转发

| Python event | 前端 SSE event | 处理 |
|---|---|---|
| `message` | `message` | 透传 |
| `citation` | `citation` | 透传 |
| `tool_call` | `tool_call` | 可透传或由前端忽略，不做业务处理 |
| `tool_result` | `tool_result` | 可透传或由前端忽略，不做业务处理 |
| `error` | `error` | 透传 |
| `done` | `done` | 透传 |
| `heartbeat` | `heartbeat` | 透传或由本服务补充 |

`stream-service` 不聚合最终回答，不发布完成事件。最终消息持久化固定由 `ai-engine` 在生成完成后调用 `biz-service /internal/chat/message-completed` 完成。

`done.data` 由 Python 引擎生成，`stream-service` 不向其中注入 `messageId`。前端刷新消息列表时使用本次请求的 `messageId` 或 `sessionId` 调用 `biz-service` 查询；如后续需要在 `done.data` 携带 `messageId`，必须先更新 Python 引擎契约。

---

## 5. 前端交互约定

前端聊天页：

1. 从 `biz-service /api/knowledge/selectable` 获取可选择知识范围。
2. 用户选择知识库范围后，将 `knowledgeSelection` 放入 `/api/chat/stream` 请求体。
3. 连接 `stream-service /api/chat/stream`。
4. 按 SSE `message` 增量渲染回答。
5. 按 SSE `citation` 展示引用来源。
6. 收到 `error` 展示错误。
7. 收到 `done` 结束本轮生成，并刷新 `biz-service /api/messages?sessionId=...`。

注意：

- 审批通过但默认禁用的公共知识副本不会出现在 `/api/knowledge/selectable`。
- 禁用公共知识库、禁用客服案例库、未就绪文档不应展示。
- 即使客户端伪造提交了不可用文档 ID，`stream-service` 也只转发，不授权；最终由 `biz-service` 的权威检索过滤条件解析剔除，`ai-engine` 只执行解析结果。

---

## 6. 配置项

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | `18082` | 服务端口 |
| `python.engine.base-url` | `http://localhost:8000` | Python 引擎地址 |
| `python.engine.internal-token` | dev 使用独立默认值 | 调用 Python `/api/chat/stream` 时写入 HTTP Header `X-Internal-Token`，对应 Python 侧 `AICSP_STREAM_INTERNAL_TOKEN` |
| `python.engine.max-connections` | `200` | WebClient 最大连接数 |
| `python.engine.connect-timeout` | `5000` | 连接超时 ms |
| `python.engine.response-timeout` | `310000` | 响应超时 ms |
| `stream.sse.heartbeat-interval` | `15s` | SSE 心跳 |
| `stream.sse.max-duration` | `300s` | 单次流最大时长 |

---

## 7. WebFlux 实现约束

- 不在响应式链路中执行阻塞 IO。
- 只使用 WebClient 调用 `ai-engine`。
- 调用 Python `/api/chat/stream` 时必须发送 HTTP Header `Accept: application/x-ndjson` 和 `X-Internal-Token`；token 不得放入 query string 或请求体。
- Python 上游只按 NDJSON 解析，每行一个完整 `EngineEvent`，再转换为前端 SSE。
- 如果 Python 因 `X-Internal-Token` 缺失或校验失败返回 `401/403`，`stream-service` 必须转换为前端 SSE `error` 事件并结束本次流，不得重试，也不得向前端暴露 token 细节。
- 如果 Python 返回其它非 2xx 状态，`stream-service` 必须先生成 SSE `error` 事件，`data` 至少包含标准错误码、traceId 和上游 HTTP 状态；随后必须发送 SSE `done` 事件，`data={"finishReason":"error"}`，最后关闭流。前端始终以 `done` 事件执行收尾与消息列表刷新。
- SSE 流内异常使用 `onErrorResume` 生成 `error` 事件。
- 心跳只用于连接保活，不参与业务。
- 客户端断开时取消上游 Python 请求。
- 不聚合回答正文。
- 不发布 RocketMQ。
- 不暴露 `/internal/**`。
- 不调用订单、物流、CRM 等业务系统。

---

## 8. 安全设计

- `/api/chat/stream` 必须通过网关访问。
- 网关负责鉴权、限流、身份头注入。
- 本服务不接受外部伪造身份头作为最终可信来源，部署上只允许网关访问。
- 本服务不保存 API Key 或模型凭据；仅配置访问 Python 引擎所需的 `python.engine.internal-token`，不得复用 biz internal token。
- 日志中不输出完整用户问题、token、API Key。
- `knowledgeSelection` 只是用户意图候选，不是授权结果。

---

## 9. 验证要求

| 测试 | 重点 |
|---|---|
| `ChatStreamController` 测试 | SSE 事件、异常转 error 事件 |
| `PythonEngineClient` 契约测试 | `EngineRequest`、`EngineEvent` 字段稳定；上游固定 `application/x-ndjson`，每行可反序列化 |
| `knowledgeSelection` 转发测试 | 候选范围原样转发；身份字段只来自请求头 |
| `locale` 转发测试 | 请求体存在时原样转发，缺失时默认 `zh-CN` |
| `messageId` 测试 | 存在时原样转发；缺失时仅为当前 HTTP 请求生成，不承诺跨请求重试复用 |
| 上游鉴权失败测试 | Python 返回 `401/403` 时转换为 SSE `error`，不重试且不泄露 token |
| 客户端断开测试 | 下游断开后取消上游 Python 请求 |
| 纯职责测试 | 不发布 MQ、不暴露 `/internal/**`、不调用业务服务 |
| 端到端测试 | 前端 -> 网关 -> stream -> ai-engine -> biz-service 内部完成落库 |

建议命令：

```bash
mvn -pl stream-service -am test
```

---

## 10. 后续演进

- 保持 stream-service 无内部工具接口、无 InternalToken 过滤器、无 RocketMQ 发布器。
- 将当前占位回显逻辑切换为真实调用 `PythonEngineClient`。
- 扩展 `ChatRequest`、`EngineRequest`，加入 `messageId`、`locale`、`knowledgeSelection`、`userId`、`tenantId`、`roles`。
- 增加 SSE 心跳和最大时长控制。
- 增加客户端断开时取消上游请求的处理。
