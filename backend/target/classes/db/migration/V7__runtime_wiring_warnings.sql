CREATE TABLE runtime_wiring_warnings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    repo_id BIGINT REFERENCES repos(id) ON DELETE CASCADE,
    fact_type VARCHAR(64) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rt_wiring_warn_user ON runtime_wiring_warnings(user_id);
