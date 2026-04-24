# Dev 环境各组件连接信息

> 文档更新时间：2026-04-24
> 用途：汇总所有 Docker 容器的基本信息、端口、挂载目录、连接方式，供后续开发和其他 Agent 使用
> 运行环境：所有容器均运行在本机 **Docker Desktop** 中（Windows + WSL2）

---

## 零、Java 服务本地端口

为避免与 RocketMQ Proxy 的 `8080`、`8081` 宿主机端口冲突，Java 服务 dev 环境统一使用 `18080` 起始端口。

| 服务 | Dev 端口 | 说明 |
|---|---:|---|
| `gateway-service` | `18080` | 统一网关入口 |
| `user-service` | `18081` | 用户、角色、权限服务 |
| `stream-service` | `18082` | SSE 对话流服务 |
| `biz-service` | `18083` | 会话、消息业务服务 |

---

## 一、PostgreSQL + pgvector

### 基本信息

| 项目 | 值 |
|---|---|
| 容器名称 | `pgvector` |
| 镜像 | `pgvector/pgvector:pg17` |
| 状态 | 运行中 |
| 重启策略 | `always` |

### 端口映射

| 宿主机端口 | 容器端口 | 用途 |
|---|---|---|
| 5432 | 5432 | PostgreSQL 数据库连接 |

### 挂载目录

| 本地路径 | 容器内路径 | 用途 |
|---|---|---|
| `~/docker/pgvector/data` | `/var/lib/postgresql/data` | 数据库数据文件持久化 |
| `~/docker/pgvector/init` | `/docker-entrypoint-initdb.d` | 初始化 SQL（首次启动自动执行） |

> ⚠️ 数据目录挂载在 WSL Linux 本地文件系统，不可挂载到 Windows 盘符（NTFS 不支持 chmod）

### 连接信息

| 参数 | 值 |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `vector_db`（默认库）、`aicsp_user`（user-service）、`aicsp_biz`（biz-service） |
| Username | `postgres` |
| Password | `123456` |

### 使用说明

1. **微服务独立数据库**：`user-service` 使用 `aicsp_user`，`biz-service` 使用 `aicsp_biz`，均连接同一 PostgreSQL 容器。`gateway-service`、`stream-service` 当前不直连关系型数据库。
2. **创建业务数据库**：可在 Navicat 或 psql 中执行 `CREATE DATABASE aicsp_user;` 与 `CREATE DATABASE aicsp_biz;`。
3. **创建向量数据库**：如需向量能力，在目标数据库中执行 `CREATE EXTENSION IF NOT EXISTS vector;`
4. **验证扩展**：`SELECT * FROM pg_extension WHERE extname = 'vector';`
5. **向量字段示例**：`embedding vector(1536)`

---

## 二、Redis

### 基本信息

| 项目 | 值 |
|---|---|
| 容器名称 | `redis` |
| 镜像 | `redis:7.2` |
| 状态 | 运行中 |
| 重启策略 | `always` |

### 端口映射

| 宿主机端口 | 容器端口 | 用途 |
|---|---|---|
| 6379 | 6379 | Redis 数据连接 |

### 挂载目录

| 本地路径 | 容器内路径 | 用途 |
|---|---|---|
| `~/docker/redis/conf/redis.conf` | `/etc/redis/redis.conf` | Redis 配置文件 |
| `~/docker/redis/data` | `/data` | RDB/AOF 持久化数据 |

### 连接信息

| 参数 | 值 |
|---|---|
| Host | `localhost` |
| Port | `6379` |
| Password | `123456` |
| 最大内存 | `256mb` |
| 淘汰策略 | `allkeys-lru` |

### Java 服务配置

以下 dev 服务需要配置 `spring.data.redis.password: 123456`：

| 服务 | Redis DB | 密码 |
|---|---:|---|
| `gateway-service` | `0` | `123456` |
| `user-service` | `1` | `123456` |
| `biz-service` | `2` | `123456` |

### 持久化配置

- **AOF**：已开启（`appendonly yes`）
- **RDB**：已开启（`save 900 1`、`save 300 10`、`save 60 10000`）

---

## 三、MinIO

### 基本信息

| 项目 | 值 |
|---|---|
| 容器名称 | `minio` |
| 镜像 | `minio/minio`（最新版） |
| 状态 | 运行中 |
| 重启策略 | `always` |

### 端口映射

| 宿主机端口 | 容器端口 | 用途 |
|---|---|---|
| 9000 | 9000 | S3 兼容 API 访问 |
| 9001 | 9001 | Web 控制台访问 |

### 挂载目录

| 本地路径 | 容器内路径 | 用途 |
|---|---|---|
| `~/docker/minio/data` | `/data` | 对象存储文件数据 |
| `~/docker/minio/config` | `/root/.minio` | MinIO 配置文件 |

### 连接信息

| 服务 | 地址 | 用户名 | 密码 |
|---|---|---|---|
| API 端点 | `http://localhost:9000` | `minioadmin` | `minioadmin123` |
| Web 控制台 | `http://localhost:9001` | `minioadmin` | `minioadmin123` |

### 使用说明

1. 浏览器访问 `http://localhost:9001` 登录控制台
2. 创建 Bucket 后，可通过 S3 兼容 SDK 访问
3. Java/Python/Node.js 等主流 SDK 均支持

---

## 四、RocketMQ

### 基本信息

| 项目 | 值 |
|---|---|
| 容器名称（NameServer） | `rmqnamesrv` |
| 容器名称（Broker） | `rmqbroker` |
| 镜像 | `apache/rocketmq:5.3.2` |
| 状态 | 运行中 |
| 重启策略 | `always` |
| Docker 网络 | `rocketmq`（容器间内部通信） |

### 端口映射

| 宿主机端口 | 容器端口 | 用途 |
|---|---|---|
| 9876 | 9876 | NameServer 注册端口（微服务连接地址） |
| 10911 | 10911 | Broker 消息收发主端口 |
| 10909 | 10909 | Broker VIP 通道端口 |
| 10912 | 10912 | Broker 高可用端口 |
| 8080 | 8080 | Proxy gRPC 接入端口 |
| 8081 | 8081 | Proxy gRPC 接入端口（备用） |

### 挂载目录

| 本地路径 | 容器内路径 | 用途 |
|---|---|---|
| `D:\ubuntu\rocketmq\namesrv\logs` | `/home/rocketmq/logs` | NameServer 日志 |
| `D:\ubuntu\rocketmq\broker\logs` | `/home/rocketmq/logs` | Broker 运行日志 |
| `D:\ubuntu\rocketmq\broker\data` | `/home/rocketmq/store` | 消息存储数据 |
| `D:\ubuntu\rocketmq\broker\conf\broker.conf` | `/home/rocketmq/rocketmq-5.3.2/conf/broker.conf` | Broker 配置文件 |

### 连接信息

| 参数 | 值 |
|---|---|
| NameServer 地址 | `localhost:9876` |
| Broker 地址 | `localhost:10911` |
| Proxy gRPC 地址 | `localhost:8081` |

### Broker 配置（broker.conf）

```properties
brokerClusterName = DefaultCluster
brokerName = broker-a
brokerId = 0
brokerIP1 = 127.0.0.1
deleteWhen = 04
fileReservedTime = 48
brokerRole = ASYNC_MASTER
flushDiskType = ASYNC_FLUSH
autoCreateTopicEnable = true
