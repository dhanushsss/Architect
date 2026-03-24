-- Phase 3: persist PR analysis for "risky PRs this week" + audit trail
CREATE TABLE pr_analysis_runs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    repo_id BIGINT NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    github_pr_number INT NOT NULL,
    pr_url TEXT,
    head_sha VARCHAR(64),
    scenario VARCHAR(64),
    verdict VARCHAR(32),
    numeric_score DOUBLE PRECISION,
    dependents_count INT DEFAULT 0,
    confidence_score DOUBLE PRECISION,
    affected_repo_names TEXT,
    risk_factors_json JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pr_runs_user_created ON pr_analysis_runs(user_id, created_at DESC);
CREATE INDEX idx_pr_runs_repo_pr ON pr_analysis_runs(repo_id, github_pr_number);
