ALTER TABLE cs_api_resource ADD COLUMN IF NOT EXISTS request_example TEXT NOT NULL DEFAULT '{}';
ALTER TABLE cs_api_resource ADD COLUMN IF NOT EXISTS response_example TEXT NOT NULL DEFAULT '{}';
