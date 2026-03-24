-- Facts extracted from config (no Git coupling): app names, gateway routes, proxy targets, registry URLs
CREATE TABLE runtime_wiring_facts (
    id              BIGSERIAL PRIMARY KEY,
    repo_id         BIGINT NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    fact_type       VARCHAR(40) NOT NULL,
    fact_key        VARCHAR(500),
    fact_value      TEXT,
    source_file     TEXT NOT NULL,
    line_number     INT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rwf_repo ON runtime_wiring_facts(repo_id);
CREATE INDEX idx_rwf_type ON runtime_wiring_facts(fact_type);
