-- ai-engine PostgreSQL 数据库初始化脚本（IDEA / DataGrip PG Console 可直接执行）
--
-- 说明：
-- 1. 请先连接到默认维护库 postgres，再执行本脚本。
-- 2. PostgreSQL 原生不支持 CREATE DATABASE IF NOT EXISTS，因此使用 dblink 在存在性判断后创建数据库。
-- 3. 本脚本可重复执行；如果角色或数据库已存在，会自动跳过。
-- 4. 密码仅为开发默认值，生产环境必须替换。

CREATE EXTENSION IF NOT EXISTS dblink;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'engine_service') THEN
        CREATE ROLE engine_service LOGIN PASSWORD 'engine_service';
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'aicsp_engine') THEN
        PERFORM dblink_exec(
            'dbname=postgres',
            'CREATE DATABASE aicsp_engine OWNER engine_service ENCODING ''UTF8'''
        );
    END IF;
END
$$;
