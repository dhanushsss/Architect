-- Phase 3: AI Understanding Layer tables
CREATE TABLE ai_query_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    query_text TEXT NOT NULL,
    response_text TEXT,
    query_type VARCHAR(50) DEFAULT 'NL_QUERY',
    tokens_used INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ai_arch_docs (
    id BIGSERIAL PRIMARY KEY,
    repo_id BIGINT REFERENCES repos(id) ON DELETE CASCADE,
    doc_content TEXT NOT NULL,
    generated_by BIGINT REFERENCES users(id),
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(repo_id)
);

CREATE TABLE ai_anomalies (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    anomaly_type VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    affected_repo VARCHAR(500),
    severity VARCHAR(20) DEFAULT 'MEDIUM',
    resolved BOOLEAN DEFAULT false,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Phase 4: Enterprise & Compliance tables
CREATE TABLE organizations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL,
    plan VARCHAR(50) DEFAULT 'FREE',
    sso_enabled BOOLEAN DEFAULT false,
    saml_idp_url TEXT,
    saml_cert TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE org_members (
    id BIGSERIAL PRIMARY KEY,
    org_id BIGINT REFERENCES organizations(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) DEFAULT 'DEVELOPER',
    invited_by BIGINT REFERENCES users(id),
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(org_id, user_id)
);

CREATE TABLE dependency_snapshots (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    snapshot_label VARCHAR(255),
    snapshot_data JSONB NOT NULL,
    node_count INT,
    edge_count INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    org_id BIGINT REFERENCES organizations(id),
    action VARCHAR(255) NOT NULL,
    resource_type VARCHAR(100),
    resource_id BIGINT,
    details JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Phase 5: Platform & Ecosystem tables
CREATE TABLE api_keys (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    org_id BIGINT REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    key_hash VARCHAR(500) UNIQUE NOT NULL,
    key_prefix VARCHAR(20) NOT NULL,
    scopes TEXT DEFAULT 'read:graph',
    rate_limit_per_hour INT DEFAULT 100,
    last_used_at TIMESTAMP,
    expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Extend repos table for Phase 4 org support
ALTER TABLE repos ADD COLUMN IF NOT EXISTS org_id BIGINT REFERENCES organizations(id);

-- Indexes for performance
CREATE INDEX idx_ai_query_user ON ai_query_history(user_id);
CREATE INDEX idx_ai_anomalies_user ON ai_anomalies(user_id, resolved);
CREATE INDEX idx_org_members_org ON org_members(org_id);
CREATE INDEX idx_org_members_user ON org_members(user_id);
CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_org ON audit_logs(org_id);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at);
CREATE INDEX idx_api_keys_user ON api_keys(user_id);
CREATE INDEX idx_api_keys_hash ON api_keys(key_hash);
CREATE INDEX idx_dependency_snapshots_user ON dependency_snapshots(user_id);
