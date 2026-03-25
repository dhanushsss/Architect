CREATE TABLE scan_tasks (
    id BIGSERIAL PRIMARY KEY,
    repo_id BIGINT NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(16) NOT NULL CHECK (type IN ('PR', 'MANUAL')),
    scan_mode VARCHAR(16) NOT NULL DEFAULT 'DEEP' CHECK (scan_mode IN ('QUICK', 'DEEP', 'INCREMENTAL')),
    status VARCHAR(16) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    picked_by VARCHAR(64),
    picked_at TIMESTAMP,
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE INDEX idx_scan_tasks_status_priority ON scan_tasks (status, type, created_at);
