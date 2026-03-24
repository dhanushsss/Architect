CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    github_id BIGINT UNIQUE NOT NULL,
    login VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255),
    avatar_url TEXT,
    access_token TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE repos (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    github_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    full_name VARCHAR(500) NOT NULL,
    description TEXT,
    default_branch VARCHAR(100) DEFAULT 'main',
    primary_language VARCHAR(100),
    html_url TEXT,
    is_private BOOLEAN DEFAULT false,
    scan_status VARCHAR(50) DEFAULT 'PENDING',
    last_scanned_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, github_id)
);

CREATE TABLE api_endpoints (
    id BIGSERIAL PRIMARY KEY,
    repo_id BIGINT REFERENCES repos(id) ON DELETE CASCADE,
    path VARCHAR(1000) NOT NULL,
    http_method VARCHAR(20),
    file_path TEXT NOT NULL,
    line_number INT,
    framework VARCHAR(100),
    language VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE api_calls (
    id BIGSERIAL PRIMARY KEY,
    caller_repo_id BIGINT REFERENCES repos(id) ON DELETE CASCADE,
    endpoint_id BIGINT REFERENCES api_endpoints(id) ON DELETE SET NULL,
    url_pattern TEXT NOT NULL,
    file_path TEXT NOT NULL,
    line_number INT,
    call_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE component_imports (
    id BIGSERIAL PRIMARY KEY,
    source_repo_id BIGINT REFERENCES repos(id) ON DELETE CASCADE,
    target_repo_id BIGINT REFERENCES repos(id) ON DELETE CASCADE,
    component_name VARCHAR(500),
    import_path TEXT NOT NULL,
    file_path TEXT NOT NULL,
    line_number INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE config_dependencies (
    id BIGSERIAL PRIMARY KEY,
    repo_id BIGINT REFERENCES repos(id) ON DELETE CASCADE,
    config_repo_id BIGINT REFERENCES repos(id) ON DELETE CASCADE,
    config_file TEXT NOT NULL,
    referencing_file TEXT NOT NULL,
    line_number INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE dependency_edges (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    target_id BIGINT NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    edge_type VARCHAR(100) NOT NULL,
    label TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pr_analyses (
    id BIGSERIAL PRIMARY KEY,
    repo_id BIGINT REFERENCES repos(id) ON DELETE CASCADE,
    pr_number INT NOT NULL,
    pr_title TEXT,
    affected_repos JSONB,
    risk_score VARCHAR(20),
    analysis_json JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_api_endpoints_repo ON api_endpoints(repo_id);
CREATE INDEX idx_api_calls_caller_repo ON api_calls(caller_repo_id);
CREATE INDEX idx_component_imports_source ON component_imports(source_repo_id);
CREATE INDEX idx_dependency_edges_source ON dependency_edges(source_id, source_type);
CREATE INDEX idx_dependency_edges_target ON dependency_edges(target_id, target_type);
