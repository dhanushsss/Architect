CREATE TABLE github_etag_cache (
    id BIGSERIAL PRIMARY KEY,
    repo_id BIGINT NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    resource_path VARCHAR(768) NOT NULL,
    etag VARCHAR(255),
    cached_body TEXT,
    last_fetched_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (repo_id, resource_path)
);

CREATE INDEX idx_github_etag_repo ON github_etag_cache(repo_id);
