# ai-engine 数据库脚本

按顺序在 IDEA / DataGrip 的 PG Console 中手动执行：

1. 连接默认维护库 `postgres`，执行 `001_create_engine_database.sql`。
2. 切换连接到 `aicsp_engine` 数据库，执行 `002_create_engine_schema.sql`。

注意：两个脚本都按可重复执行设计。`001_create_engine_database.sql` 使用 `dblink` 实现“数据库不存在才创建”，因此需要使用有权创建扩展、角色和数据库的账号执行，开发环境通常使用 `postgres` 用户。

默认数据库连接：

```env
AICSP_POSTGRES_DSN=postgresql://engine_service:engine_service@localhost:5432/aicsp_engine
```

当前 `qwen3-embedding:8b` 已通过本地 Ollama 实测为 `4096` 维，因此按 `vector(4096)` 建表。pgvector 的 HNSW 索引不支持超过 `2000` 维的 `vector` 列，所以初始化脚本不会创建向量 ANN 索引；第一阶段使用精确相似度排序。如果后续更换为 `<= 2000` 维 embedding 模型，可以重新检测维度、同步修改 `.env` 与 `engine_embedding.embedding vector(N)`，再启用 HNSW 索引。
