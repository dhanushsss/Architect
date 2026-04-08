CREATE TABLE pr_predictions (
    id BIGSERIAL PRIMARY KEY,
    pr_number INTEGER NOT NULL,
    repo_full_name VARCHAR(255) NOT NULL,
    predicted_risk VARCHAR(20) NOT NULL,
    confidence_pct INTEGER NOT NULL,
    direct_match_count INTEGER NOT NULL DEFAULT 0,
    inferred_match_count INTEGER NOT NULL DEFAULT 0,
    unresolved_call_count INTEGER NOT NULL DEFAULT 0,
    stale_repo_count INTEGER NOT NULL DEFAULT 0,
    affected_repo_names TEXT[],
    signal_breakdown JSONB,
    pr_head_sha VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pr_predictions_repo ON pr_predictions(repo_full_name);
CREATE INDEX idx_pr_predictions_risk ON pr_predictions(predicted_risk);
CREATE INDEX idx_pr_predictions_created ON pr_predictions(created_at);
