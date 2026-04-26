-- ai-engine PostgreSQL 数据库初始化脚本
-- 执行方式示例：
--   psql -h localhost -U postgres -f ai-engine/sql/001_create_engine_database.sql
--
-- 说明：
-- 1. CREATE DATABASE 不能在事务中执行。
-- 2. 如果你的 PostgreSQL 已有同名用户或数据库，可以按实际情况跳过对应语句。
-- 3. 密码仅为开发默认值，生产环境必须替换。

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'engine_service') THEN
        CREATE ROLE engine_service LOGIN PASSWORD 'engine_service';
    END IF;
END
$$;

SELECT 'CREATE DATABASE aicsp_engine OWNER engine_service ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'aicsp_engine')\gexec
