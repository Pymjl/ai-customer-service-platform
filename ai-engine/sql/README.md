# ai-engine 数据库脚本

按顺序手动执行：

```powershell
psql -h localhost -U postgres -f ai-engine/sql/001_create_engine_database.sql
psql -h localhost -U postgres -d aicsp_engine -f ai-engine/sql/002_create_engine_schema.sql
```

默认数据库连接：

```env
AICSP_POSTGRES_DSN=postgresql://engine_service:engine_service@localhost:5432/aicsp_engine
```

当前 `qwen3-embedding:8b` 默认按 `4096` 维建表。如果本地 Ollama 实际返回维度不同，需要同步修改 `.env` 与 `engine_embedding.embedding vector(N)`。
