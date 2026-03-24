-- HTTP method, normalized path for matching, EXTERNAL vs resolved internal
ALTER TABLE api_calls ADD COLUMN IF NOT EXISTS http_method VARCHAR(20);
ALTER TABLE api_calls ADD COLUMN IF NOT EXISTS normalized_pattern TEXT;
ALTER TABLE api_calls ADD COLUMN IF NOT EXISTS target_kind VARCHAR(30);
ALTER TABLE api_calls ADD COLUMN IF NOT EXISTS external_host VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_api_calls_target_kind ON api_calls(target_kind);
