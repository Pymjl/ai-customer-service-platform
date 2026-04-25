ALTER TABLE IF EXISTS cs_user DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE IF EXISTS cs_role DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE IF EXISTS cs_permission DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE IF EXISTS cs_user_role DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE IF EXISTS cs_role_permission DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE IF EXISTS cs_api_resource DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE IF EXISTS cs_role_resource DROP COLUMN IF EXISTS deleted_at;

DO $$
BEGIN
    IF to_regclass('cs_user') IS NOT NULL THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'cs_user' AND column_name = 'avatar_path') THEN
            ALTER TABLE cs_user ADD COLUMN avatar_path VARCHAR(512);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'cs_user' AND column_name = 'gender') THEN
            ALTER TABLE cs_user ADD COLUMN gender SMALLINT;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'cs_user' AND column_name = 'real_name') THEN
            ALTER TABLE cs_user ADD COLUMN real_name VARCHAR(64);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'cs_user' AND column_name = 'age') THEN
            ALTER TABLE cs_user ADD COLUMN age SMALLINT;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'cs_user' AND column_name = 'email') THEN
            ALTER TABLE cs_user ADD COLUMN email VARCHAR(128);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'cs_user' AND column_name = 'phone') THEN
            ALTER TABLE cs_user ADD COLUMN phone VARCHAR(32);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'cs_user' AND column_name = 'address') THEN
            ALTER TABLE cs_user ADD COLUMN address VARCHAR(256);
        END IF;
    END IF;
END $$;

UPDATE cs_user
SET gender = COALESCE(gender, 0),
    real_name = COALESCE(real_name, 'Administrator'),
    age = COALESCE(age, 18),
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'default' AND username = 'admin' AND deleted = FALSE;
